# 应用内「功能引导」（互动教程）

## 怎么进

- 设置（右上角齿轮）→ 最上方「功能引导」卡片；
- 或者 设置 → 无障碍设置 → 顶部「第一次用？看互动教程」。

## 有什么

| 教程 | 步数 | 讲了什么 | 用到的动画 |
|---|---|---|---|
| 语音唤醒 | 3 | 它听词不听人 / 怎么唤醒词 / 叫不醒怎么查 | 波形+命中、开关、排查清单 |
| 无障碍（AI 的眼睛和手） | 2 | 为什么需要 / 怎么开（含 Android 13 受限设置） | 点按涟漪、开关 |
| 其他关键权限 | 2 | 四项各管什么 / 一次性全开 | 权限阶梯、终端 |
| 命令通道（Shizuku / 自建 ADB） | 3 | 为什么要 shell / Shizuku 授权 / 自建通道 | 权限阶梯、开关、雷达扫描 |
| 让 AI 替你操作手机 | 2 | 怎么交代任务 / 怎么保证不点错 | 点按涟漪、排查清单 |

交互：分步进度条、上一步/下一步、每步可跳转对应系统设置页（无障碍、通知使用权、使用情况、悬浮窗、电池、所有文件），
读完的步骤会持久化（`SettingsManager.guideDone()`，key = `topic#step`）。

## 代码位置

```
ui/guide/GuideCatalog.kt    // 内容（数据驱动，加教程只改这里）
ui/guide/GuideAnimations.kt // 7 种动画：WAKE_WAVE / TAP_RIPPLE / TOGGLE_ON /
                            //          PERMISSION_LADDER / RADAR_SCAN / CHECK_STEPS / TERMINAL
ui/guide/GuideScreen.kt     // 列表 + 分步详情 + 系统设置跳转
```

### 加一条教程

在 `GuideCatalog.topics` 里加一个 `GuideTopic` 即可，不需要动 UI：

```kotlin
GuideTopic(
    id = "xxx", emoji = "🧩", title = "标题", subtitle = "一句话",
    steps = listOf(GuideStep("小标题", "正文", GuideAnim.TAP_RIPPLE,
                             GuideAction.A11Y_SETTINGS, tip = "可选提示")),
)
```

## 顺手修掉的一个 ANR（重要）

做 UI 验证时点了设置，弹出 **"星尘 isn't responding"**。抓日志确认不是引导代码，而是
**语音唤醒服务在 `onStartCommand` 里同步加载 int8 模型（模拟器 5~7s）**，主线程被堵住，
一旦有触摸输入就触发 `Input dispatching timed out`。

修法：把「解析拼音表 + 加载模型 + 起引擎」整体移到 `wake-prep` 后台线程，
主线程只做 `startForeground` 和状态通知（先用「正在准备唤醒模型…」，就绪后换成「听候「xxx」」）。

真机影响：中低端机首次启动服务时同样会卡 0.5~2s，这个修复对它们更明显。
