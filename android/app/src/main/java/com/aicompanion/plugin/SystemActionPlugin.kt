package com.aicompanion.plugin

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.aicompanion.models.ToolDefinition
import com.aicompanion.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 系统动作插件：走「官方 Intent / 系统 API」而不是无障碍模拟点击。
 *
 * 为什么单独做这一条通道：
 *   无障碍自动化是「看见界面 → 模拟手指」，天然脆弱（控件找不到、UI 改版、页面没加载完）。
 *   而拨号、设闹钟、开应用、开设置页这些事，Android 本来就提供了官方入口，
 *   一步 Intent 直达，不依赖任何界面元素，也不会点错。
 *   两条通道是互补关系：官方入口优先走这里，没有官方入口的才交给 operate_phone 去模拟。
 *
 * 安全取舍（重要）：
 *   默认**不做不可逆动作**。dial 默认只打开拨号盘并把号码填好（ACTION_DIAL，零权限），
 *   由用户自己按拨号键；send_sms 同理只预填不发送。要真拨出去必须显式传 direct=true
 *   且用户已授予 CALL_PHONE 权限，否则自动降级为「填好待拨」并说明原因。
 */
class SystemActionPlugin(private val context: Context) : ToolPlugin {

    companion object {
        private const val TAG = "SystemAction"

        /** 供设置页展示：当前已授予的相关权限 */
        fun grantedSummary(context: Context): String {
            val contacts = ContextCompat.checkSelfPermission(
                context, Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED
            val call = ContextCompat.checkSelfPermission(
                context, Manifest.permission.CALL_PHONE
            ) == PackageManager.PERMISSION_GRANTED
            return "通讯录：${if (contacts) "已授权" else "未授权"}　通话：${if (call) "已授权" else "未授权"}"
        }
    }

    private data class ContactHit(val name: String, val number: String)

    override val name = "system_action"
    override val description = "执行系统级的确定性动作（拨号、开应用、系统设置、闹钟、音量等）"

    override fun getDefinition() = ToolDefinition(
        name = "system_action",
        description = """执行手机系统级的确定性动作。这条路走官方接口，比模拟点击可靠得多，凡是下面列出的动作都应优先用它，不要用 operate_phone 去模拟点击。

可用 action 及其参数：
- dial            拨打电话。参数 number（号码）或 name（通讯录姓名，二选一）；direct=true 直接拨出（需通话权限），默认只打开拨号盘填好号码由用户按拨号键
- contacts_search 搜索通讯录。参数 query（姓名关键字）
- open_app        打开应用。参数 query（应用名，如「计算器」「设置」，或直接给包名）
- list_apps       列出已安装应用。参数 query 可选（按关键字过滤）
- open_settings   打开系统设置页。参数 page（wifi/bluetooth/sound/display/battery/apps/accessibility/location/storage/airplane/nfc/security/privacy/notification/language/date/about/developer，默认总设置页）
- send_sms        发短信。参数 number、text；默认打开短信应用预填内容，由用户点发送
- set_alarm       设系统闹钟。参数 hour（0-23）、minute（0-59）、label 可选
- set_timer       设倒计时。参数 seconds、label 可选
- open_url        用浏览器打开网址。参数 url
- navigate        地图导航。参数 destination（地址或地点名）
- share_text      分享一段文本给其它应用。参数 text
- volume          音量控制。参数 mode（up/down/mute/max/set/get）、stream（music/ring/alarm/notification，默认 music）、level（mode=set 时的目标值）
- torch           手电筒开关。参数 on（true/false）
- battery         读取电量与充电状态
- clipboard       剪贴板。参数 mode（read/write）、text（mode=write 时）

说明：dial / send_sms 默认是「填好让用户确认」，返回结果里会说明当前状态，不要误以为已经拨出或发送。""",
        parameters = mapOf(
            "type" to "object",
            "properties" to mapOf(
                "action" to mapOf(
                    "type" to "string",
                    "description" to "要执行的动作，见工具描述中的 action 列表"
                ),
                "number" to mapOf("type" to "string", "description" to "电话号码（dial / send_sms）"),
                "name" to mapOf("type" to "string", "description" to "通讯录姓名（dial）"),
                "query" to mapOf("type" to "string", "description" to "搜索关键字（contacts_search / open_app / list_apps）"),
                "text" to mapOf("type" to "string", "description" to "文本内容（send_sms / share_text / clipboard）"),
                "url" to mapOf("type" to "string", "description" to "网址（open_url）"),
                "destination" to mapOf("type" to "string", "description" to "目的地（navigate）"),
                "page" to mapOf("type" to "string", "description" to "设置页标识（open_settings）"),
                "hour" to mapOf("type" to "integer", "description" to "小时 0-23（set_alarm）"),
                "minute" to mapOf("type" to "integer", "description" to "分钟 0-59（set_alarm）"),
                "seconds" to mapOf("type" to "integer", "description" to "秒数（set_timer）"),
                "label" to mapOf("type" to "string", "description" to "备注文字（set_alarm / set_timer）"),
                "mode" to mapOf("type" to "string", "description" to "子模式（volume: up/down/mute/max/set/get；clipboard: read/write）"),
                "stream" to mapOf("type" to "string", "description" to "音量通道（volume，默认 music）"),
                "level" to mapOf("type" to "integer", "description" to "目标音量值（volume，mode=set）"),
                "direct" to mapOf("type" to "boolean", "description" to "dial 是否直接拨出，默认 false（只填号不拨出）"),
                "on" to mapOf("type" to "boolean", "description" to "开关状态（torch）")
            ),
            "required" to listOf("action")
        )
    )

    override fun isEnabled(): Boolean = true

    override suspend fun execute(arguments: String): String = withContext(Dispatchers.IO) {
        try {
            val args = JSONObject(arguments)
            val action = args.optString("action", "").trim()
            if (action.isBlank()) return@withContext "错误：缺少 action 参数"

            val result = when (action) {
                "dial" -> doDial(args)
                "contacts_search" -> doContactsSearch(args)
                "open_app" -> doOpenApp(args)
                "list_apps" -> doListApps(args)
                "open_settings" -> doOpenSettings(args)
                "send_sms" -> doSendSms(args)
                "set_alarm" -> doSetAlarm(args)
                "set_timer" -> doSetTimer(args)
                "open_url" -> doOpenUrl(args)
                "navigate" -> doNavigate(args)
                "share_text" -> doShareText(args)
                "volume" -> doVolume(args)
                "torch" -> doTorch(args)
                "battery" -> doBattery()
                "clipboard" -> doClipboard(args)
                else -> "错误：未知 action「${action}」，请使用工具描述中列出的动作"
            }
            AppLogger.i(TAG, "action=${action} → ${result.take(120)}")
            result
        } catch (e: Exception) {
            AppLogger.e(TAG, "execute failed: ${e.message}")
            "系统动作执行失败：${e.message}"
        }
    }

    // ---------------- 权限与启动辅助 ----------------

    private fun hasPermission(perm: String): Boolean =
        ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED

    private fun safeStart(intent: Intent, okMsg: String, failMsg: String): String {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            okMsg
        } catch (e: ActivityNotFoundException) {
            "${failMsg}（设备上没有能处理该动作的应用）"
        } catch (e: Exception) {
            "${failMsg}（${e.message}）"
        }
    }

    /** 归一化号码：去掉空格、横线、括号，保留数字与 + * # , ; */
    private fun normalizeNumber(raw: String): String =
        raw.replace(Regex("[^0-9+*#,;]"), "")

    // ---------------- 拨号 ----------------

    private fun doDial(args: JSONObject): String {
        val direct = args.optBoolean("direct", false)
        var number = normalizeNumber(args.optString("number", "").trim())
        val name = args.optString("name", "").trim()

        // 只给了姓名：查通讯录解析号码
        if (number.isBlank() && name.isNotBlank()) {
            if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
                return "无法通过姓名查找号码：尚未授予通讯录权限。\n" +
                    "请到「系统设置 → 应用 → 星尘 → 权限」中开启通讯录权限后重试，" +
                    "或直接告诉我号码。"
            }
            val hits = searchContacts(name, limit = 6)
            when {
                hits.isEmpty() -> return "通讯录中没有找到与「${name}」匹配的联系人。请确认姓名，或直接告诉我号码。"
                hits.size > 1 -> {
                    val list = hits.joinToString("\n") { "· ${it.name}  ${it.number}" }
                    return "「${name}」匹配到多个联系人，请指定更完整的姓名或直接给号码：\n${list}"
                }
                else -> number = normalizeNumber(hits.first().number)
            }
        }

        if (number.isBlank()) return "错误：请提供 number（号码）或 name（通讯录姓名）"

        val contactLabel = if (name.isNotBlank()) "（${name}）" else ""
        val uri = Uri.parse("tel:" + Uri.encode(number))

        if (direct) {
            if (!hasPermission(Manifest.permission.CALL_PHONE)) {
                return "未授予通话权限，已改为打开拨号盘并填好号码，请手动点击拨号键。\n" +
                    safeStart(
                        Intent(Intent.ACTION_DIAL, uri),
                        "已打开拨号盘，号码 ${number}${contactLabel} 已填好。",
                        "打开拨号盘失败"
                    )
            }
            val intent = Intent(Intent.ACTION_CALL, uri)
            return safeStart(
                intent,
                "正在拨出 ${number}${contactLabel}。",
                "拨号失败"
            )
        }

        return safeStart(
            Intent(Intent.ACTION_DIAL, uri),
            "已打开拨号盘，号码 ${number}${contactLabel} 已填好，请点击拨号键拨出。",
            "打开拨号盘失败"
        )
    }

    private fun searchContacts(query: String, limit: Int): List<ContactHit> {
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) return emptyList()
        val out = mutableListOf<ContactHit>()
        try {
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
            val args = arrayOf("%${query}%")
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection, selection, args, null
            )?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(projection[0])
                val numIdx = cursor.getColumnIndex(projection[1])
                while (cursor.moveToNext() && out.size < limit) {
                    val n = cursor.getString(nameIdx) ?: continue
                    val p = cursor.getString(numIdx) ?: continue
                    val normalized = normalizeNumber(p)
                    if (normalized.isBlank()) continue
                    if (out.any { it.number == normalized }) continue
                    out.add(ContactHit(n, normalized))
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "searchContacts failed: ${e.message}")
        }
        return out
    }

    private fun doContactsSearch(args: JSONObject): String {
        val query = args.optString("query", "").trim()
        if (query.isBlank()) return "错误：请提供 query（姓名关键字）"
        if (!hasPermission(Manifest.permission.READ_CONTACTS)) {
            return "尚未授予通讯录权限，无法搜索联系人。\n" +
                "请到「系统设置 → 应用 → 星尘 → 权限」中开启通讯录权限。"
        }
        val hits = searchContacts(query, limit = 20)
        if (hits.isEmpty()) return "通讯录中没有找到与「${query}」匹配的联系人。"
        val list = hits.joinToString("\n") { "· ${it.name}  ${it.number}" }
        return "找到 ${hits.size} 个匹配「${query}」的联系人：\n${list}"
    }

    // ---------------- 应用 ----------------

    private fun doOpenApp(args: JSONObject): String {
        val query = args.optString("query", "").trim()
        if (query.isBlank()) return "错误：请提供 query（应用名或包名）"
        val pm = context.packageManager

        // 1) 看起来像包名时先直接尝试
        if (query.contains(".")) {
            pm.getLaunchIntentForPackage(query)?.let { intent ->
                return safeStart(intent, "已打开应用 ${query}。", "打开应用失败")
            }
        }

        // 2) 按应用显示名匹配（精确优先，其次包含）
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = try {
            pm.queryIntentActivities(mainIntent, 0)
        } catch (e: Exception) {
            emptyList()
        }
        if (apps.isEmpty()) return "无法枚举已安装应用（可能缺少包可见性权限）。"

        val labeled = apps.mapNotNull { info ->
            val label = try { info.loadLabel(pm).toString() } catch (_: Exception) { "" }
            if (label.isBlank()) null else label to info
        }
        val lower = query.lowercase()
        val hit = labeled.firstOrNull { it.first.equals(query, ignoreCase = true) }
            ?: labeled.firstOrNull { it.first.lowercase() == lower }
            ?: labeled.firstOrNull { it.first.lowercase().contains(lower) }

        if (hit == null) {
            val similar = labeled
                .filter { it.first.lowercase().contains(lower.take(2)) }
                .take(8)
                .joinToString("、") { it.first }
            val hint = if (similar.isNotBlank()) "\n名称相近的应用：${similar}" else ""
            return "没有找到名为「${query}」的应用。${hint}\n可以用 list_apps 查看已安装应用。"
        }

        val launch = pm.getLaunchIntentForPackage(hit.second.activityInfo.packageName)
            ?: return "找到了「${hit.first}」但没有可启动的入口（可能是系统组件）。"
        return safeStart(launch, "已打开「${hit.first}」。", "打开「${hit.first}」失败")
    }

    private fun doListApps(args: JSONObject): String {
        val query = args.optString("query", "").trim()
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = try {
            pm.queryIntentActivities(mainIntent, 0)
        } catch (e: Exception) {
            emptyList()
        }
        if (apps.isEmpty()) return "无法枚举已安装应用（可能缺少包可见性权限）。"

        val names = apps.mapNotNull { info ->
            try { info.loadLabel(pm).toString() } catch (_: Exception) { null }
        }.distinct().sorted()

        val filtered = if (query.isBlank()) names else names.filter { it.contains(query, ignoreCase = true) }
        if (filtered.isEmpty()) return "没有匹配「${query}」的应用。"

        val shown = filtered.take(60)
        val tail = if (filtered.size > shown.size) "\n（共 ${filtered.size} 个，仅列出前 ${shown.size} 个）" else ""
        return "已安装应用共 ${names.size} 个" +
            (if (query.isBlank()) "" else "，匹配「${query}」${filtered.size} 个") + "：\n" +
            shown.joinToString("、") + tail
    }

    // ---------------- 系统设置 ----------------

    private fun doOpenSettings(args: JSONObject): String {
        val page = args.optString("page", "").trim().lowercase()
        val action = when (page) {
            "", "root", "main", "settings" -> Settings.ACTION_SETTINGS
            "wifi", "wlan" -> Settings.ACTION_WIFI_SETTINGS
            "bluetooth", "bt" -> Settings.ACTION_BLUETOOTH_SETTINGS
            "sound", "volume" -> Settings.ACTION_SOUND_SETTINGS
            "display", "screen" -> Settings.ACTION_DISPLAY_SETTINGS
            "battery", "power" -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            "apps", "application" -> Settings.ACTION_APPLICATION_SETTINGS
            "accessibility" -> Settings.ACTION_ACCESSIBILITY_SETTINGS
            "location", "gps" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
            "storage" -> Settings.ACTION_INTERNAL_STORAGE_SETTINGS
            "airplane", "flight" -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
            "nfc" -> Settings.ACTION_NFC_SETTINGS
            "security" -> Settings.ACTION_SECURITY_SETTINGS
            "privacy" -> Settings.ACTION_PRIVACY_SETTINGS
            "notification", "notifications" -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
            "language", "locale" -> Settings.ACTION_LOCALE_SETTINGS
            "date", "time" -> Settings.ACTION_DATE_SETTINGS
            "about", "device" -> Settings.ACTION_DEVICE_INFO_SETTINGS
            "developer" -> Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS
            "home", "launcher" -> Settings.ACTION_HOME_SETTINGS
            "data", "mobile" -> Settings.ACTION_DATA_ROAMING_SETTINGS
            else -> null
        }

        if (action == null) {
            return "未知的设置页「${page}」。可用值：wifi / bluetooth / sound / display / battery / apps / " +
                "accessibility / location / storage / airplane / nfc / security / privacy / notification / " +
                "language / date / about / developer / home / data，留空则打开设置首页。"
        }

        // 部分页面的 action 在低版本系统上不存在，失败则退回设置首页
        val primary = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(primary)
            "已打开系统设置的「${if (page.isBlank()) "首页" else page}」页面。"
        } catch (e: Exception) {
            safeStart(
                Intent(Settings.ACTION_SETTINGS),
                "该设置页在当前系统上不可用，已改为打开设置首页。",
                "打开设置失败"
            )
        }
    }

    // ---------------- 短信 / 闹钟 / 计时器 ----------------

    private fun doSendSms(args: JSONObject): String {
        val number = normalizeNumber(args.optString("number", "").trim())
        val text = args.optString("text", "")
        if (number.isBlank()) return "错误：请提供 number（收信人号码）"

        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(number))).apply {
            if (text.isNotBlank()) putExtra("sms_body", text)
        }
        val tail = if (text.isNotBlank()) "，内容已填好" else ""
        return safeStart(
            intent,
            "已打开短信应用，收信人 ${number} 已填好${tail}，请点击发送键发送。",
            "打开短信应用失败"
        )
    }

    private fun doSetAlarm(args: JSONObject): String {
        val hour = args.optInt("hour", -1)
        val minute = args.optInt("minute", -1)
        if (hour !in 0..23) return "错误：hour 需要在 0-23 之间"
        if (minute !in 0..59) return "错误：minute 需要在 0-59 之间"
        val label = args.optString("label", "")

        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
        }
        val timeText = String.format("%02d:%02d", hour, minute)
        return safeStart(
            intent,
            "已打开闹钟应用，闹钟时间 ${timeText} 已填好，请确认保存。",
            "设置闹钟失败"
        )
    }

    private fun doSetTimer(args: JSONObject): String {
        val seconds = args.optInt("seconds", -1)
        if (seconds <= 0) return "错误：seconds 需要为正整数"
        val label = args.optString("label", "")

        val intent = Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
        }
        val readable = if (seconds >= 60) {
            "${seconds / 60} 分 ${seconds % 60} 秒"
        } else {
            "${seconds} 秒"
        }
        return safeStart(
            intent,
            "已打开计时器，倒计时 ${readable} 已填好，请确认开始。",
            "设置计时器失败"
        )
    }

    // ---------------- 网址 / 导航 / 分享 ----------------

    private fun doOpenUrl(args: JSONObject): String {
        var url = args.optString("url", "").trim()
        if (url.isBlank()) return "错误：请提供 url"
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "https://$url"
        }
        return safeStart(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)),
            "已用浏览器打开 ${url}。",
            "打开网址失败"
        )
    }

    private fun doNavigate(args: JSONObject): String {
        val dest = args.optString("destination", "").trim()
        if (dest.isBlank()) return "错误：请提供 destination（目的地）"
        // 用 geo: 通用格式，交给系统弹出的地图应用选择器处理，兼容高德/百度/腾讯地图
        val uri = Uri.parse("geo:0,0?q=" + Uri.encode(dest))
        return safeStart(
            Intent(Intent.ACTION_VIEW, uri),
            "已发起导航到「${dest}」，请在弹出的地图应用中选择。",
            "发起导航失败"
        )
    }

    private fun doShareText(args: JSONObject): String {
        val text = args.optString("text", "")
        if (text.isBlank()) return "错误：请提供 text"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        val chooser = Intent.createChooser(intent, "分享到")
        return safeStart(chooser, "已弹出分享面板，请选择要分享到的应用。", "分享失败")
    }

    // ---------------- 音量 / 手电筒 / 电量 / 剪贴板 ----------------

    private fun doVolume(args: JSONObject): String {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return "无法获取音频服务。"
        val streamName = args.optString("stream", "music").lowercase()
        val stream = when (streamName) {
            "ring", "ringtone" -> AudioManager.STREAM_RING
            "alarm" -> AudioManager.STREAM_ALARM
            "notification" -> AudioManager.STREAM_NOTIFICATION
            "call", "voice" -> AudioManager.STREAM_VOICE_CALL
            else -> AudioManager.STREAM_MUSIC
        }
        val mode = args.optString("mode", "get").lowercase()

        return try {
            when (mode) {
                "up" -> {
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    "已调高${streamLabel(streamName)}音量，当前 ${am.getStreamVolume(stream)}/${am.getStreamMaxVolume(stream)}。"
                }
                "down" -> {
                    am.adjustStreamVolume(stream, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    "已调低${streamLabel(streamName)}音量，当前 ${am.getStreamVolume(stream)}/${am.getStreamMaxVolume(stream)}。"
                }
                "mute" -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        am.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, AudioManager.FLAG_SHOW_UI)
                    } else {
                        am.setStreamVolume(stream, 0, AudioManager.FLAG_SHOW_UI)
                    }
                    "已静音${streamLabel(streamName)}音量。"
                }
                "max" -> {
                    val max = am.getStreamMaxVolume(stream)
                    am.setStreamVolume(stream, max, AudioManager.FLAG_SHOW_UI)
                    "已将${streamLabel(streamName)}音量调到最大（${max}）。"
                }
                "set" -> {
                    val max = am.getStreamMaxVolume(stream)
                    val level = args.optInt("level", -1)
                    if (level < 0) return "错误：mode=set 需要提供 level"
                    val safe = level.coerceIn(0, max)
                    am.setStreamVolume(stream, safe, AudioManager.FLAG_SHOW_UI)
                    "已将${streamLabel(streamName)}音量设为 ${safe}/${max}。"
                }
                else -> {
                    val cur = am.getStreamVolume(stream)
                    val max = am.getStreamMaxVolume(stream)
                    val muted = cur == 0
                    "当前${streamLabel(streamName)}音量 ${cur}/${max}${if (muted) "（静音）" else ""}。"
                }
            }
        } catch (e: SecurityException) {
            "调整音量被系统拒绝（${e.message}）。可改为提示用户手动调整。"
        }
    }

    private fun streamLabel(name: String): String = when (name) {
        "ring", "ringtone" -> "铃声"
        "alarm" -> "闹钟"
        "notification" -> "通知"
        "call", "voice" -> "通话音"
        else -> "媒体"
    }

    private fun doTorch(args: JSONObject): String {
        val on = args.optBoolean("on", false)
        return try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return "无法获取相机服务。"
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return "这台设备没有可用的闪光灯。"
            cm.setTorchMode(cameraId, on)
            if (on) "手电筒已打开。" else "手电筒已关闭。"
        } catch (e: Exception) {
            "手电筒操作失败：${e.message}"
        }
    }

    private fun doBattery(): String {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                ?: return "无法获取电池服务。"
            val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            // 充电状态走 sticky broadcast 读，兼容所有版本
            // （BatteryManager.BATTERY_PROPERTY_STATUS 是 API 26 才有的常量，低版本会崩）
            val sticky = context.registerReceiver(
                null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val status = sticky?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
            "当前电量 ${level}%${if (charging) "，正在充电" else "，未在充电"}。"
        } catch (e: Exception) {
            "读取电量失败：${e.message}"
        }
    }

    private fun doClipboard(args: JSONObject): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return "无法获取剪贴板服务。"
        val mode = args.optString("mode", "read").lowercase()
        return try {
            if (mode == "write") {
                val text = args.optString("text", "")
                if (text.isBlank()) return "错误：mode=write 需要提供 text"
                cm.setPrimaryClip(ClipData.newPlainText("stardust", text))
                "已写入剪贴板（${text.length} 个字符）。"
            } else {
                val clip = cm.primaryClip
                if (clip == null || clip.itemCount == 0) {
                    "剪贴板为空，或当前系统限制后台读取剪贴板（需要应用处于前台）。"
                } else {
                    val text = clip.getItemAt(0).coerceToText(context)?.toString().orEmpty()
                    if (text.isBlank()) {
                        "剪贴板内容不是文本，无法读取。"
                    } else {
                        "剪贴板内容：\n${text.take(2000)}"
                    }
                }
            }
        } catch (e: Exception) {
            "剪贴板操作失败：${e.message}"
        }
    }

}
