package com.aicompanion.ui

import android.app.Activity
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.aicompanion.R

class AlarmActivity : Activity() {

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var isVibrating = false
    private val vibratePattern = longArrayOf(0, 500, 500)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContentView(R.layout.activity_alarm)

        val label = intent.getStringExtra("alarm_label") ?: "闹钟"
        val message = intent.getStringExtra("alarm_message") ?: "时间到了！"

        findViewById<TextView>(R.id.tv_alarm_label)?.text = label
        findViewById<TextView>(R.id.tv_alarm_time)?.text = message

        findViewById<View>(R.id.btn_dismiss)?.setOnClickListener { dismissAlarm() }

        startAlarmRinging()
    }

    private fun startAlarmRinging() {
        try {
            val alarmUri: Uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmActivity, alarmUri)
                isLooping = true
                setVolume(1f, 1f)
                prepare()
                start()
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                vibrator = getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }
            vibrator?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    it.vibrate(VibrationEffect.createWaveform(vibratePattern, 0))
                } else {
                    @Suppress("DEPRECATION")
                    it.vibrate(vibratePattern, 0)
                }
                isVibrating = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun dismissAlarm() {
        try {
            mediaPlayer?.apply {
                stop()
                release()
            }
            mediaPlayer = null
            if (isVibrating) {
                vibrator?.cancel()
                isVibrating = false
            }
        } catch (_: Exception) {}

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask()
        } else {
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        dismissAlarm()
    }
}