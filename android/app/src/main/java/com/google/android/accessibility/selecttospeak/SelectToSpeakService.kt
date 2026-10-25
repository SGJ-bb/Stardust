package com.google.android.accessibility.selecttospeak

/**
 * 伪装成系统内置无障碍服务的**类全名**。
 *
 * ## 为什么要这么干（实测 + 社区验证）
 *
 * 微信从 **8.0.52** 起，对"普通第三方无障碍服务"隐藏节点树：
 * 拿到的是一棵被混淆的空树（`nodes=1 text=0`），`uiautomator dump` 对微信任何界面
 * 也只有一个空根节点（本项目在微信 8.0.76 / Android 14 上复现过）。
 *
 * 但微信的判定方式是**比对无障碍服务的组件类名**，并不校验包名和签名 ——
 * 也就是说它认的是"你叫不叫这个名字"，而不是"你从哪来"。
 * 把服务的类全名注册成系统内置的 `com.google.android.accessibility.selecttospeak.SelectToSpeakService`，
 * 微信就会把它当成系统无障碍工具放行（社区在 8.0.78 上验证过仍有效）。
 *
 * ## 这不是提权
 *
 * - 不改系统分区、不改签名、不伪造 `FLAG_SYSTEM`；
 * - 拿到的权限和普通无障碍服务完全一样；
 * - 唯一变化是**组件的类名**：`com.aicompanion/com.google.android.accessibility.selecttospeak.SelectToSpeakService`。
 *
 * ## 小坑（社区经验）
 *
 * 早期有人用 `TalkBackService` 这个名字，在小米机型上会触发屏幕常驻朗读提示。
 * `SelectToSpeakService` 更"安静"，所以选它。
 *
 * 逻辑全部继承自真正的采集服务，这里一行都不重写。
 */
class SelectToSpeakService : com.aicompanion.screen.ScreenRecognitionService()
