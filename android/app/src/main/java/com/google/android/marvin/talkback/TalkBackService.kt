package com.google.android.marvin.talkback

/**
 * 伪装成 Google TalkBack（读屏软件）的**类全名**。
 *
 * ## 为什么还要再伪装一个
 *
 * 已有的 `SelectToSpeakService` 能让微信放行一部分节点（标题栏、tab、可点容器），
 * 但会话列表那 14 行仍然是空的：`LinearLayout clickable=true` 却 text/desc 全空。
 * 实测微信把会话标题画在 Canvas 上，它只在"读屏模式"下才额外补 contentDescription。
 *
 * 微信的无障碍适配历史上主要针对 TalkBack（它的读屏播报逻辑就是按 TalkBack 调的），
 * 所以再挂一个 TalkBack 类名试试：如果微信是按"服务类名是否属于已知读屏"分支的，
 * 这个身份能拿到更多。
 *
 * ## 与 SelectToSpeakService 的关系
 *
 * 两个类都继承自 `ScreenRecognitionService`，一行逻辑都不重写。
 * 只在 `enabled_accessibility_services` 里启用其中一个即可（不要同时启用，
 * 两个服务同时跑会重复采集、互相抢事件）。
 *
 * ## 已知副作用
 *
 * 早期社区反馈：用 TalkBack 类名时，小米系机型可能弹出"屏幕朗读已开启"之类的提示，
 * 或者在无障碍设置里显示成读屏软件。功能不受影响，只是观感。所以默认仍推荐
 * SelectToSpeakService，TalkBack 这个作为"读不到内容时的加强档"。
 */
class TalkBackService : com.aicompanion.screen.ScreenRecognitionService()
