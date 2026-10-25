/**
 * 功能引导的目录：每个功能一套分步教程，步骤里带「动画类型」和「可跳转的系统入口」。
 *
 * 写这些内容的判断标准：用户看完能自己判断"我现在缺哪一步"，
 * 而不是把文档原样搬进 App。
 */
package com.aicompanion.ui.guide

/** 每步的动画类型（实现在 GuideAnimations.kt） */
enum class GuideAnim {
    WAKE_WAVE,          // 语音波形 + 命中
    TAP_RIPPLE,         // 手指点按 + 涟漪
    TOGGLE_ON,          // 开关打开 + 勾
    PERMISSION_LADDER,  // 权限阶梯逐级点亮
    RADAR_SCAN,         // 扫描端口
    CHECK_STEPS,        // 排查清单逐条打勾
    TERMINAL,           // 命令行逐行输出
}

/** 可以直达的系统设置页 */
enum class GuideAction(val label: String) {
    A11Y_SETTINGS("打开无障碍设置"),
    NOTIFICATION_ACCESS("打开通知使用权"),
    USAGE_ACCESS("打开使用情况访问"),
    OVERLAY_PERMISSION("打开悬浮窗权限"),
    BATTERY("打开电池优化"),
    ALL_FILES("打开所有文件访问"),
    WAKE_SETTINGS("去语音设置"),
    ADB_PANEL("去自建 ADB 通道"),
    WIRELESS_DEBUG("打开开发者选项"),
    NONE(""),
}

data class GuideStep(
    val title: String,
    val body: String,
    val anim: GuideAnim,
    val action: GuideAction = GuideAction.NONE,
    val tip: String = "",
)

data class GuideTopic(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    /** 适用范围一句话（空=所有机型通用），显示在步骤上方 */
    val compat: String = "",
    val steps: List<GuideStep>,
)

object GuideCatalog {

    val topics: List<GuideTopic> = listOf(

        GuideTopic(
            id = "wake",
            emoji = "🎙",
            title = "语音唤醒",
            subtitle = "不动手，说一句就能叫醒它",
            steps = listOf(
                GuideStep(
                    title = "它听的是「词」，不是「你」",
                    body = "唤醒是离线关键词识别：听到「星尘星尘」这种词就触发，" +
                        "不看是谁在说。所以别人说、视频里说，也会叫醒它。",
                    anim = GuideAnim.WAKE_WAVE,
                    tip = "模型只有 5MB，全程不联网，音频不会离开手机。",
                ),
                GuideStep(
                    title = "换成你自己想叫的名字",
                    body = "设置 → 语音设置 → 语音唤醒，把唤醒词改成 4 个字以上的中文。" +
                        "保存后下面会显示它实际使用的拼音，比如「x īng ch én x īng ch én」。",
                    anim = GuideAnim.TOGGLE_ON,
                    action = GuideAction.WAKE_SETTINGS,
                    tip = "2 个字的词（如「星尘」）误唤醒会明显变多，建议 4 字。",
                ),
                GuideStep(
                    title = "叫不醒时，按这个顺序查",
                    body = "① 录音权限有没有给；② 通知栏那条「语音唤醒」还在不在（不在=服务被杀了）；" +
                        "③ 是不是刚重启过，国产 ROM 需要把「自启动/后台运行」打开。",
                    anim = GuideAnim.CHECK_STEPS,
                    tip = "设置页里有「录 3 秒试一下」，当场就能看出是词不对还是麦克风被占。",
                ),
            ),
        ),

        GuideTopic(
            id = "a11y",
            emoji = "🖐",
            title = "无障碍（AI 的眼睛和手）",
            subtitle = "让它能看懂界面、点得动按钮",
            steps = listOf(
                GuideStep(
                    title = "它靠这个「看见」和「点击」",
                    body = "无障碍服务能把当前屏幕的控件树读出来（文字、按钮、输入框），" +
                        "也能反过来派发点击、滑动、输入。没有它，AI 只能聊天，不能替你操作。",
                    anim = GuideAnim.TAP_RIPPLE,
                ),
                GuideStep(
                    title = "开启，以及 Android 13+ 的坑",
                    body = "设置 → 无障碍 → 已下载的服务 → 打开本应用。" +
                        "如果开关是灰的，去 应用信息 → 右上角 ⋮ → 允许「受限设置」，再回来开。",
                    anim = GuideAnim.TOGGLE_ON,
                    action = GuideAction.A11Y_SETTINGS,
                    tip = "部分 ROM 重启后会关掉无障碍，建议同时打开自启动。",
                ),
            ),
        ),

        GuideTopic(
            id = "perms",
            emoji = "🔑",
            title = "其他关键权限",
            subtitle = "四个开关，决定它能不能「全自动」",
            steps = listOf(
                GuideStep(
                    title = "这四项各管一件事",
                    body = "通知使用权＝读/回消息；使用情况访问＝知道当前在前台的是哪个 App；" +
                        "悬浮窗＝能浮在别的应用上显示；电池无限制＝息屏后不被杀。",
                    anim = GuideAnim.PERMISSION_LADDER,
                    action = GuideAction.NOTIFICATION_ACCESS,
                ),
                GuideStep(
                    title = "一次性全开",
                    body = "懒得一个个点，可以连一次电脑跑 tools/adb_elevate.bat，" +
                        "它会把运行时权限、无障碍、通知、文件、电量白名单全部设好，之后不用再连。",
                    anim = GuideAnim.TERMINAL,
                    tip = "卸载重装后需要重跑一次。",
                ),
            ),
        ),

        GuideTopic(
            id = "pair",
            emoji = "🔗",
            title = "免电脑开通命令权限",
            subtitle = "填一次 6 位配对码，之后不再连电脑",
            compat = "适用：Android 11 及以上（2020 年后的大部分手机）。Android 10 及更早没有这个机制；" +
                "部分华为/荣耀机型即使配对成功也会被系统拦住 TCP 授权。",
            steps = listOf(
                GuideStep(
                    title = "这一步到底在换什么",
                    body = "手机默认只认「电脑」能管它。这个流程是让手机相信「本应用」也是合法的管理者——" +
                        "认过之后，改系统设置、截屏、停止别的应用都能自己做，不用连电脑、不用装 Shizuku。",
                    anim = GuideAnim.PERMISSION_LADDER,
                    tip = "只做一次。钥匙存在系统里，重启也不会掉。",
                ),
                GuideStep(
                    title = "先确认三个前提",
                    body = "① 系统是 Android 11 或更新；② Wi-Fi 处于打开状态（不联网也行）；" +
                        "③ 开发者选项里能看到「无线调试」这一项。少一个就只能用「连一次电脑」的老办法。",
                    anim = GuideAnim.CHECK_STEPS,
                    action = GuideAction.WIRELESS_DEBUG,
                    tip = "Android 10 及更早没有「配对码」机制，这是系统限制，不是应用的问题。",
                ),
                GuideStep(
                    title = "打开配对窗口",
                    body = "点下面的按钮 →「无线调试」→「使用配对码配对设备」。屏幕上会出现 6 位数字，" +
                        "下面还有一行 IP:端口。这个窗口一关就失效，所以要紧接着做下一步。",
                    anim = GuideAnim.TOGGLE_ON,
                    action = GuideAction.WIRELESS_DEBUG,
                    tip = "窗口里那行 IP:端口是给电脑看的；本应用自己会去找，不用你抄。",
                ),
                GuideStep(
                    title = "把 6 位数字填回来",
                    body = "回到本应用 → 设置 → 无障碍 → 命令执行 →「配对码配对」，照着屏幕敲那 6 位数字，" +
                        "点「开始配对」。通常一两秒就出结果。",
                    anim = GuideAnim.TAP_RIPPLE,
                    action = GuideAction.ADB_PANEL,
                    tip = "万一提示找不到端口，把窗口那行的端口一起填进去，格式「6 位码:端口」。",
                ),
                GuideStep(
                    title = "成功之后就不用再管",
                    body = "看到「配对成功」就结束了。以后只要无线调试开着就能直连；" +
                        "重启手机后重新打开一次「无线调试」即可，不需要再配一次。",
                    anim = GuideAnim.RADAR_SCAN,
                    tip = "想撤销这份信任：开发者选项 →「撤销 USB 调试授权」。",
                ),
                GuideStep(
                    title = "卡住了先看这里",
                    body = "① 提示配对失败 → 窗口可能已关闭，重开会换新码；" +
                        "② 一直找不到端口 → 用「6 位码:端口」的写法；" +
                        "③ 华为/荣耀个别机型连不上 → 系统改写 adb 授权，改用 Shizuku 或连一次电脑。",
                    anim = GuideAnim.TERMINAL,
                ),
            ),
        ),

        GuideTopic(
            id = "shell",
            emoji = "🧰",
            title = "命令通道（Shizuku / 自建 ADB）",
            subtitle = "改系统设置、截屏、强制停止靠它",
            steps = listOf(
                GuideStep(
                    title = "为什么要「shell 身份」",
                    body = "「改系统设置」「截屏」「强制停止别的 App」这些不属于普通应用，" +
                        "属于系统里的 shell 用户。拿到它有三条路：Shizuku、连一次电脑授权、或者自建 adb 客户端。",
                    anim = GuideAnim.PERMISSION_LADDER,
                ),
                GuideStep(
                    title = "Shizuku：装一次，点授权",
                    body = "装 Shizuku → 用「无线调试」启动它的服务 → 回本应用 设置 → 无障碍 → 命令执行 → 授权。 " +
                        "之后所有 ADB 级命令都能跑，不需要 root。",
                    anim = GuideAnim.TOGGLE_ON,
                    action = GuideAction.ADB_PANEL,
                    tip = "Shizuku 是开源项目（Apache-2.0），不是官方应用，但用的是系统自己的 adb 通道。",
                ),
                GuideStep(
                    title = "自建 ADB 通道：不装任何东西",
                    body = "应用自己当 adb 客户端。Android 11+ 直接用「6 位配对码」，全程不需要电脑（见上一章）；" +
                        "更老的系统才需要连一次电脑跑脚本，让应用先拿到授权。",
                    anim = GuideAnim.RADAR_SCAN,
                    action = GuideAction.ADB_PANEL,
                    tip = "配对码方式已可用：配对成功后钥匙写进系统，重启也不用再配。",
                ),
            ),
        ),

        GuideTopic(
            id = "operate",
            emoji = "🤖",
            title = "让 AI 替你操作手机",
            subtitle = "点外卖、回消息、改设置",
            steps = listOf(
                GuideStep(
                    title = "怎么交代任务",
                    body = "直接说人话：「帮我打开美团，点上次那家麻辣烫，送到公司」。 " +
                        "它会先读屏看清界面，再一步步点，每一步都能在屏幕上看到。",
                    anim = GuideAnim.TAP_RIPPLE,
                ),
                GuideStep(
                    title = "它怎么保证不点错",
                    body = "每个点击都有「执行前快照 → 执行后比对」；页面没变就重试或换定位方式" +
                        "（资源 ID → 文字 → 坐标）。失败会停下告诉你卡在哪，而不是乱点一气。",
                    anim = GuideAnim.CHECK_STEPS,
                    tip = "涉及付款、发消息这类动作，建议把「AI 操作模式」设为需确认。",
                ),
            ),
        ),
    )

    fun topic(id: String?): GuideTopic? = topics.firstOrNull { it.id == id }
}
