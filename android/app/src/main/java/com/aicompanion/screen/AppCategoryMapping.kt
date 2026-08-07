/** 内置 App 包名→类别映射表，覆盖 80+ 主流国产 App */
package com.aicompanion.screen

object AppCategoryMapping {

    // ===== 类别常量（15 类）=====
    const val SOCIAL = "social"           // 社交
    const val VIDEO = "video"             // 视频
    const val SHOPPING = "shopping"       // 购物
    const val FINANCE = "finance"         // 理财
    const val NEWS = "news"               // 新闻
    const val MUSIC = "music"             // 音乐
    const val WORK = "work"               // 办公
    const val READING = "reading"         // 阅读
    const val MAP = "map"                 // 地图
    const val LIFE = "life"               // 生活服务
    const val TOOL = "tool"               // 工具
    const val BROWSER = "browser"         // 浏览器
    const val GAME = "game"               // 游戏
    const val HEALTH = "health"           // 健康
    const val SYSTEM = "system"           // 系统
    const val UNKNOWN = "unknown"         // 未知

    /** 全部类别（供 UI 使用） */
    val ALL_CATEGORIES = listOf(
        SOCIAL, VIDEO, SHOPPING, FINANCE, NEWS, MUSIC, WORK,
        READING, MAP, LIFE, TOOL, BROWSER, GAME, HEALTH, SYSTEM, UNKNOWN,
    )

    /** 类别中文显示名 */
    val DISPLAY_NAMES: Map<String, String> = mapOf(
        SOCIAL to "社交", VIDEO to "视频", SHOPPING to "购物",
        FINANCE to "理财", NEWS to "新闻", MUSIC to "音乐",
        WORK to "办公", READING to "阅读", MAP to "地图",
        LIFE to "生活", TOOL to "工具", BROWSER to "浏览器",
        GAME to "游戏", HEALTH to "健康", SYSTEM to "系统",
        UNKNOWN to "未知",
    )

    /**
     * 内置包名→类别映射（80+ 主流国产 App）
     * key 为完整包名，精确匹配
     */
    val BUILTIN: Map<String, String> = mapOf(
        // ===== 社交 =====
        "com.tencent.mm" to SOCIAL,              // 微信
        "com.tencent.mobileqq" to SOCIAL,        // QQ
        "com.sina.weibo" to SOCIAL,              // 微博
        "com.xingin.xhs" to SOCIAL,              // 小红书
        "com.zhihu.android" to SOCIAL,           // 知乎
        "com.douban.frodo" to SOCIAL,            // 豆瓣
        "com.smile.gifmaker" to SOCIAL,          // 快手（偏社交）
        "com.soulapp.android" to SOCIAL,         // Soul
        "com.pp.assistant" to SOCIAL,            // 探探
        "com.immomo.momo" to SOCIAL,             // 陌陌

        // ===== 视频 =====
        "com.ss.android.ugc.aweme" to VIDEO,     // 抖音
        "com.ss.android.article.video" to VIDEO, // 西瓜视频
        "tv.danmaku.bili" to VIDEO,              // 哔哩哔哩
        "com.qiyi.video" to VIDEO,               // 爱奇艺
        "com.tencent.qqlive" to VIDEO,           // 腾讯视频
        "com.youku.phone" to VIDEO,              // 优酷
        "com.hunantv.imgo.activity" to VIDEO,    // 芒果TV

        // ===== 购物 =====
        "com.taobao.taobao" to SHOPPING,         // 淘宝
        "com.tmall.wireless" to SHOPPING,        // 天猫
        "com.jingdong.app.mall" to SHOPPING,     // 京东
        "com.xunmeng.pinduoduo" to SHOPPING,     // 拼多多
        "com.achievo.vipshop" to SHOPPING,       // 唯品会
        "com.taobao.idlefish" to SHOPPING,       // 闲鱼
        "com.shizhuang.duapp" to SHOPPING,       // 得物
        "com.wuba" to SHOPPING,                  // 58同城
        "com.suning.mobile.ebuy" to SHOPPING,    // 苏宁易购
        "com.jingdong.app.mall.lite" to SHOPPING, // 京东极速版
        "com.tmall.wireless.lite" to SHOPPING,    // 淘宝特卖

        // ===== 理财 =====
        "com.eg.android.AlipayGphone" to FINANCE, // 支付宝
        "com.tencent.mobileqqi" to FINANCE,       // QQ钱包相关
        "com.unionpay" to FINANCE,                // 银联
        "com.icbc" to FINANCE,                    // 工商银行
        "com.chinamworld.main" to FINANCE,        // 建设银行
        "com.bankcomm.Bankcomm" to FINANCE,       // 交通银行
        "com.ccb.ccbnetpay" to FINANCE,           // 建行支付

        // ===== 生活服务 =====
        "com.sankuai.meituan" to LIFE,           // 美团
        "me.ele" to LIFE,                         // 饿了么
        "com.dianping.v1" to LIFE,               // 大众点评
        "com.sdu.didi.psnger" to LIFE,           // 滴滴出行
        "com.ctrip.ibet" to LIFE,                 // 携程
        "ctrip.android.view" to LIFE,             // 携程(备用)
        "com.Qunar" to LIFE,                      // 去哪儿
        "com.tongcheng.android" to LIFE,         // 同程
        "com.taobao.trip" to LIFE,                // 飞猪
        "com.MobileTicket" to LIFE,               // 12306
        "com.lianjia.beike" to LIFE,              // 贝壳找房
        "com.lianjia.house" to LIFE,              // 链家

        // ===== 地图 =====
        "com.autonavi.minimap" to MAP,           // 高德地图
        "com.baidu.BaiduMap" to MAP,             // 百度地图
        "com.tencent.map" to MAP,                // 腾讯地图

        // ===== 新闻 =====
        "com.ss.android.article.news" to NEWS,   // 今日头条
        "com.netease.newsreader.activity" to NEWS, // 网易新闻
        "com.tencent.news" to NEWS,              // 腾讯新闻
        "com.sohu.newsclient" to NEWS,           // 搜狐新闻

        // ===== 音乐 =====
        "com.netease.cloudmusic" to MUSIC,       // 网易云音乐
        "com.tencent.qqmusic" to MUSIC,          // QQ音乐
        "com.kugou.android" to MUSIC,            // 酷狗音乐
        "cn.kuwo.player" to MUSIC,               // 酷我音乐
        "com.meizu.media.music" to MUSIC,        // 魅族音乐

        // ===== 办公 =====
        "com.alibaba.android.rimet" to WORK,     // 钉钉
        "com.tencent.wework" to WORK,            // 企业微信
        "com.ss.android.lark" to WORK,           // 飞书
        "cn.wps.moffice_eng" to WORK,            // WPS
        "com.tencent.androidqqmail" to WORK,     // QQ邮箱
        "com.netease.mail" to WORK,              // 网易邮箱大师
        "com.youdao.note" to WORK,               // 有道云笔记
        "com.tencent.weread" to READING,         // 微信读书(归阅读)

        // ===== 阅读 =====
        "com.qidian.QDReader" to READING,        // 起点读书
        "com.dragon.read" to READING,            // 番茄小说
        "com.chaozh.iReaderFree" to READING,     // 沧海小说
        "com.htread.android" to READING,         // 备用

        // ===== 浏览器 =====
        "com.android.browser" to BROWSER,        // 小米浏览器
        "com.android.chrome" to BROWSER,         // Chrome
        "com.UCMobile" to BROWSER,               // UC浏览器
        "com.tencent.mtt" to BROWSER,            // QQ浏览器
        "com.qihoo.browser" to BROWSER,          // 360浏览器
        "com.quark.browser" to BROWSER,          // 夸克浏览器
        "org.mozilla.firefox" to BROWSER,        // Firefox

        // ===== 工具 =====
        "com.baidu.netdisk" to TOOL,             // 百度网盘
        "com.alicloud.databox" to TOOL,          // 阿里云盘
        "com.coolapk.market" to TOOL,            // 酷安
        "com.tencent.android.app" to TOOL,       // 应用宝
        "com.miui.player" to MUSIC,              // 小米音乐(归音乐)
        "com.android.calendar" to TOOL,          // 日历
        "com.android.contacts" to TOOL,          // 通讯录
        "com.android.mms" to SOCIAL,             // 短信(归社交)
        "com.android.phone" to SOCIAL,           // 电话(归社交)
        "com.android.settings" to SYSTEM,        // 设置
        "com.miui.securitycenter" to SYSTEM,     // 安全中心

        // ===== 健康 =====
        "com.xiaomi.hm.health" to HEALTH,        // 小米运动健康
        "com.codoon.gps" to HEALTH,              // 咕咚
        "com.keep.android" to HEALTH,            // Keep
    )
}
