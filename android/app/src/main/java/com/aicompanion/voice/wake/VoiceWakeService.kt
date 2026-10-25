/**
 * 语音唤醒前台服务：常驻 16kHz 单声道采集 + 离线 KWS，命中后把应用带到「正在听你说」的状态。
 *
 * 关键设计：
 *  - microphone 类型前台服务（Android 14 必须声明 FOREGROUND_SERVICE_MICROPHONE）；
 *  - TTS 朗读 / ASR 识别期间自动暂停监听（VoiceBus），避免自己唤醒自己；
 *  - 命中后先静默 800ms 再开麦，防止唤醒词尾巴被当成问题内容；
 *  - 应用在前台走进程内广播原地开麦；在后台则拉起 MainActivity 并带 extra。
 */
package com.aicompanion.voice.wake

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.aicompanion.R
import com.aicompanion.settings.SettingsManager
import com.aicompanion.ui.MainActivity
import com.aicompanion.util.AppLogger
import java.io.File

class VoiceWakeService : Service() {

    companion object {
        private const val TAG = "VoiceWakeService"
        private const val CHANNEL_ID = "voice_wake_channel"
        private const val NOTIFICATION_ID = 1201

        const val ACTION_START = "com.aicompanion.voice.wake.START"
        const val ACTION_STOP = "com.aicompanion.voice.wake.STOP"
        const val ACTION_RELOAD = "com.aicompanion.voice.wake.RELOAD"
        const val ACTION_TEST_ASSET = "com.aicompanion.voice.wake.TEST_ASSET"
        const val EXTRA_PATH = "path"
    }

    private var engine: WakeWordEngine? = null
    private var settings: SettingsManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())

    /**
     * 看门狗：采集线程可能因设备异常（AudioRecord 读失败）自己退出，
     * 而服务还挂着"监听中"的通知 —— 那是最糟的假状态。每 30s 核一次。
     */
    private val watchdog = object : Runnable {
        override fun run() {
            if (settings?.voiceWakeEnabled == true) {
                val eng = engine
                if (eng == null || !eng.isRunning) {
                    AppLogger.w(TAG, "监听已停止（引擎状态异常），自动重启")
                    startMonitoring(rebuild = true)
                }
            }
            handler.postDelayed(this, 30_000L)
        }
    }

    /** 当前唤醒词（展示用） */
    private var primaryWord: String = VoiceWake.DEFAULT_WORDS.split(",")[0]

    override fun onCreate() {
        super.onCreate()
        settings = SettingsManager(this)
        createChannel()
        acquireWakeLock()
        handler.postDelayed(watchdog, 30_000L)
        AppLogger.i(TAG, "服务创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 诊断入口：adb shell am startservice -n com.aicompanion/.voice.wake.VoiceWakeService -a com.aicompanion.PROBE_UI
        // 用服务触发是为了"不把前台应用挤掉"——探针要读的是别人（比如微信）的窗口。
        if (intent?.action == "com.aicompanion.PROBE_UI") {
            Thread {
                val svc = com.aicompanion.screen.ScreenRecognitionService.getInstance()
                if (svc == null) {
                    AppLogger.e(TAG, "PROBE_UI: 无障碍服务未运行")
                } else {
                    AppLogger.i(TAG, "PROBE_UI " + com.aicompanion.screen.UiTreeProbe.probe(svc).toText())
                }
            }.start()
            return START_NOT_STICKY
        }

        // 先抢在前 5 秒里进前台，避免 startForegroundService 超时被 ANR
        startForegroundCompat(buildNotification("正在准备…", false))

        when (intent?.action) {
            ACTION_STOP -> {
                AppLogger.i(TAG, "收到停止指令")
                settings?.voiceWakeEnabled = false
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TEST_ASSET -> {
                val path = intent.getStringExtra(EXTRA_PATH) ?: ""
                runSelfTest(path)
                return START_STICKY
            }
            ACTION_RELOAD -> {
                AppLogger.i(TAG, "收到重载指令，重建关键词")
                startMonitoring(rebuild = true)
                return START_STICKY
            }
            else -> {
                startMonitoring(rebuild = false)
                return START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppLogger.i(TAG, "服务销毁")
        handler.removeCallbacksAndMessages(null)
        runCatching { engine?.release() }
        engine = null
        releaseWakeLock()
        super.onDestroy()
    }

    // ------------------------------------------------------------- 监听

    private fun startMonitoring(rebuild: Boolean) {
        if (!hasAudioPermission()) {
            AppLogger.w(TAG, "缺少录音权限，无法唤醒")
            startForegroundCompat(buildNotification("缺少录音权限，点此授权后在设置里重开", true))
            return
        }
        val sm = settings ?: SettingsManager(this).also { settings = it }
        val words = sm.voiceWakeWords.ifBlank { VoiceWake.DEFAULT_WORDS }
        primaryWord = VoiceWake.primaryWord(words)

        // 已经在跑且关键词没变：只刷新通知，避免每次 onResume 都重建模型
        val eng0 = engine
        if (!rebuild && eng0 != null && eng0.isRunning) {
            startForegroundCompat(buildNotification("听候「$primaryWord」", false))
            return
        }

        // ⚠ 必须放到后台线程：解析拼音表（~400ms）+ 加载 int8 模型（模拟器 5~7s）
        // 放在 onStartCommand 里会直接堵死主线程 —— 用户一碰界面就 ANR。
        startForegroundCompat(buildNotification("正在准备唤醒模型…", false))
        Thread({
            try {
                val lines = VoiceWake.keywordLines(this, words, threshold = sm.voiceWakeThreshold)
                if (lines.isEmpty()) {
                    AppLogger.w(TAG, "唤醒词为空或无法转拼音：$words")
                    handler.post {
                        startForegroundCompat(buildNotification("唤醒词无效，请到设置里改成中文词", true))
                    }
                    return@Thread
                }
                val eng = engine ?: WakeWordEngine(
                    context = this,
                    onWake = { kw -> onWake(kw) },
                    onError = { msg -> AppLogger.e(TAG, msg) },
                ).also { engine = it }

                eng.setKeywords(lines)
                val ok = eng.start()
                if (!ok) {
                    AppLogger.e(TAG, "引擎启动失败：${eng.lastError}")
                    handler.post {
                        startForegroundCompat(
                            buildNotification("唤醒引擎启动失败：${eng.lastError ?: "未知原因"}", true)
                        )
                    }
                    return@Thread
                }
                AppLogger.i(TAG, "监听中 words=$words → ${lines.first()}")
                handler.post {
                    startForegroundCompat(buildNotification("听候「$primaryWord」", false))
                }
            } catch (t: Throwable) {
                AppLogger.e(TAG, "准备唤醒失败: ${t.message}", t)
                handler.post {
                    startForegroundCompat(buildNotification("唤醒准备失败：${t.message}", true))
                }
            }
        }, "wake-prep").start()
    }

    private fun onWake(keyword: String) {
        VoiceBus.wakeSettleUntil = System.currentTimeMillis() + 800
        vibrate()
        AppLogger.i(TAG, "唤醒：$keyword")

        if (AppState.mainVisible) {
            // 应用在前台：直接让界面开麦，不打断用户正在做的事
            runCatching {
                sendBroadcast(Intent(VoiceWake.ACTION_WAKE).setPackage(packageName))
            }
            startForegroundCompat(buildNotification("我在～ 正在听你说", false))
            return
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(VoiceWake.EXTRA_VOICE_WAKE, true)
        }
        val launched = runCatching { startActivity(intent) }.isSuccess
        if (launched) {
            startForegroundCompat(buildNotification("我在～ 已为你打开对话", false))
        } else {
            // Android 10+ 后台启动界面可能被拦：退化成可点通知
            AppLogger.w(TAG, "后台拉起界面失败，改为通知提示")
            startForegroundCompat(buildNotification("听到你叫我了，点一下开始说话", true))
        }
    }

    // ------------------------------------------------------------- 自测

    private fun runSelfTest(assetPath: String) {
        val sm = settings ?: SettingsManager(this)
        val words = sm.voiceWakeWords.ifBlank { VoiceWake.DEFAULT_WORDS }
        val lines = VoiceWake.keywordLines(this, words, sm.voiceWakeThreshold)
        Thread {
            val eng = engine ?: WakeWordEngine(this, {}, {}).also { engine = it }
            val t0 = System.currentTimeMillis()
            val hit = if (assetPath.isBlank()) {
                null
            } else if (assetPath.startsWith("file:")) {
                eng.detectWavFile(File(assetPath.removePrefix("file:")), lines)
            } else {
                eng.detectAsset(assetPath, lines)
            }
            val cost = System.currentTimeMillis() - t0
            val ok = !hit.isNullOrEmpty()
            val msg = "自测 ${if (ok) "命中" else "未命中"}：${hit ?: "-"}（${cost}ms，${lines.size} 条关键词）"
            AppLogger.i(TAG, msg)
            runCatching {
                sendBroadcast(
                    Intent(VoiceWake.ACTION_TEST_RESULT)
                        .setPackage(packageName)
                        .putExtra(VoiceWake.EXTRA_RESULT_OK, ok)
                        .putExtra(VoiceWake.EXTRA_RESULT_KEYWORD, hit ?: "")
                        .putExtra(VoiceWake.EXTRA_RESULT_MESSAGE, msg)
                )
            }
            startForegroundCompat(buildNotification(msg, false))
        }.start()
    }

    // ------------------------------------------------------------- 通知

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID, "语音唤醒", NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "常驻监听唤醒词，不显示横幅、不响铃"
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String, withAlert: Boolean): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), pendingFlags()
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, VoiceWakeService::class.java).setAction(ACTION_STOP),
            pendingFlags()
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("语音唤醒")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(if (withAlert) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_LOW)
            .setContentIntent(openIntent)
            .addAction(0, "停止唤醒", stopIntent)
            .build()
    }

    private fun pendingFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

    private fun startForegroundCompat(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "startForeground 失败: ${e.message}", e)
        }
    }

    // ------------------------------------------------------------- 杂项

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "aicompanion:voice_wake").apply {
                setReferenceCounted(false)
                acquire(12 * 60 * 60 * 1000L)   // 12 小时上限，服务重启时续期
            }
        } catch (e: Exception) {
            AppLogger.w(TAG, "唤醒锁申请失败: ${e.message}")
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun vibrate() {
        if (settings?.voiceWakeVibrate != true) return
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(40)
            }
        } catch (_: Exception) {
        }
    }
}
