# 微信这类 App 拿不到 UI 树：实测诊断与绕过方案

> ## ⚠️ 2026-10-24 09:38 重要更正（请先读这段）
>
> 本文开头的结论「微信不上报视图树」**只对"普通类名"的无障碍服务成立**。
> 实测发现：把无障碍服务的**类全名**换成系统内置的
> `com.google.android.accessibility.selecttospeak.SelectToSpeakService`，
> 同一个微信（8.0.76 / Android 14 / HyperOS）立刻交出完整节点树：
>
> | 服务类名 | 微信窗口节点数 | 文本 | 可点 |
> |---|---|---|---|
> | `com.aicompanion.screen.ScreenRecognitionService` | **1** | 0 | 0 |
> | `…selecttospeak.SelectToSpeakService`（伪装） | **298** | 17 | 61 |
>
> 机制：**微信只比对无障碍服务的组件类名，不校验包名与签名**（社区在 8.0.78 上验证过）。
> 这也解释了"盲人为什么能用"——TalkBack / SelectToSpeak 属于"自己人"。
>
> 下方原有的 OCR 兜底方案依然保留：它适用于**自绘页面**（朋友圈、部分小程序/WebView）
> 以及"伪装失效"的极端情况。实测链路见 §7，伪装实现见 §11。


> 环境：Redmi 24094RAD4C（codename `beryl`）/ Android 14 / 微信 8.0.76（versionCode 3141，targetSdk 34）
> 无障碍服务：`com.aicompanion/com.aicompanion.screen.ScreenRecognitionService`
> 实测时间：2026-10-24 00:47~01:05

## 1. 结论先说

**微信主动不向无障碍上报视图树**，不是我们配置错、也不是授权没给。

微信首页（`com.tencent.mm/.ui.LauncherUI`）在屏幕上**渲染完全正常**（能看到「微信(1411)」、
十几个会话名、底部四个标签），但：

| 取树手段 | 结果 |
|---|---|
| 我们的无障碍服务（`rootInActiveWindow`） | `nodes=1 text=0 clickable=0` |
| `uiautomator dump`（UiAutomation，独立管道，shell 级权限） | 同样 **1 个节点**、`class=""`、`enabled="false"` |
| `dumpsys activity top`（原生 View 树，不经过无障碍） | **完整存在**：`HomeUI$FitSystemWindowLayoutView` → `FirstScreenFrameLayout` → `CustomViewPager`… |

三行放在一起就能定位：**View 在、画面在，只有"无障碍通道"被掐了**。

## 2. 排除掉的可能（都做了对照实验）

| 假设 | 怎么验的 | 结果 |
|---|---|---|
| 无障碍服务没开/没连上 | `settings get secure enabled_accessibility_services` + `dumpsys accessibility` | 已连接 |
| 我们服务配置缺能力 | 检查 `res/xml/accessibility_service_config.xml` | `canRetrieveWindowContent=true`、`canTakeScreenshot=true`、`flagIncludeNotImportantViews`、`flagRetrieveInteractiveWindows` **都已开** |
| 是"第三方 App 通病" | 同样手段测 QQ（`com.tencent.mobileqq`） | **260 节点 / 37 条文本**，正常 |
| 系统 App 也这样 | 测 `com.android.settings` | 29 节点，正常 |
| 微信窗口是 FLAG_SECURE | `dumpsys window` 看 `fl=` | 只有 `LAYOUT_IN_SCREEN / SPLIT_TOUCH / HARDWARE_ACCELERATED` 等，**没有 SECURE** |
| 窗口创建时无障碍是关的（连接没注册） | 关无障碍→杀微信→开无障碍→冷启动→再 dump | 仍然 1 个节点，**假设不成立** |
| 内容在"同包名的另一个窗口" | 逐窗口遍历（`service.windows`） | 微信只有 1 个窗口，且就是那 1 个空节点；另有 `com.miui.home`、`com.android.systemui`（那 9 条文本是通知栏） |

> 顺带修掉一个我们自己的真 bug：原来的多窗口合并逻辑只纳入"不同包名/系统窗口"，
> 导致"同一应用的内容在另一个窗口"的情况被漏掉。现在改为：**活跃窗口没内容时，同包名窗口也算数**，
> 并在输出里明确标注"活动窗口是空壳"。

## 3. 绕过方案：截图 → OCR（带坐标）→ 系统手势点击

关键点在于**读**和**点**是两条完全不同的通路：

- **读**：微信可以拒绝上报无障碍树 → 所以改读像素（截图 + OCR）；
- **点**：手理由**系统**注入（`dispatchGesture` / `input tap`），应用拦不住 → 同一套点击逻辑直接复用。

### 已实现（代码）

| 文件 | 作用 |
|---|---|
| `screen/OcrView.kt` | ML Kit **中文**文字识别（项目里本来就有 `ChineseTextRecognizerOptions`），按**行**输出文字 + 包围盒 + 中心坐标 |
| `screen/ScreenRecognitionService.kt` | `captureBitmap()`（优先无障碍 `takeScreenshot`，失败退自建 ADB `screencap`）、`refreshByOcr()`（把 OCR 结果写进和 UI 树同一套缓存）、`isSnapshotDegenerate()`（判定退化快照） |
| `screen/AutoOperator.kt` | `formatScreenForLLM` / `formatScreenTreeForLLM` 检测到退化快照时**自动切 OCR**，模型看到的是带坐标的文本列表 |
| `screen/UiProbeService.kt` | 诊断入口，`--ez ocr true` 只跑 OCR 兜底并打日志（不改变前台应用） |

OCR 结果登记为 `ClickableData`（`path` 为空），于是现有的点击兜底链
`路径 → 精确 bounds → 最近可点祖先 → 坐标手势` 会自然走到最后一档 —— **不用为 OCR 另写点击逻辑**。

### 模型看到的样子

```
屏幕 1080x2400（该页面不上报无障碍树，以下为 OCR 结果，坐标可直接点击）
1. 微信(1411)  @(240,116)
2. diaxxxrot.  @(150,300)
3. 重电服务站  @(180,700)
...
提示：点击用 tap(x,y)；纯图标按钮 OCR 认不出，请结合截图判断。
```

## 4. 验证方法（真机）

```bash
# 1) UI 树体检（会打印逐窗口明细）
adb shell am startservice -n com.aicompanion/.screen.UiProbeService
adb logcat -s UiProbe

# 2) 只测 OCR 兜底
adb shell am startservice -n com.aicompanion/.screen.UiProbeService --ez ocr true
adb logcat -s UiProbe OcrView ScreenRecognitionService

# 注意：必须先解锁，且确认前台是目标 App
adb shell "dumpsys window | grep mCurrentFocus"
```

**踩坑**：屏幕锁屏或通知栏占焦点时，`mCurrentFocus` 会是 `NotificationShade`，
截图截到的是锁屏 → OCR 必然 0 行（这一步曾误判为"OCR 不工作"）。
测试前先：

```bash
adb shell svc power stayon usb                      # 插电不息屏
adb shell settings put system screen_off_timeout 1800000
adb shell input keyevent KEYCODE_WAKEUP
adb shell cmd statusbar collapse
```

## 5. 已知限制（诚实版）

1. **纯图标按钮 OCR 认不出**（微信右上角「+」、语音图标等）→ 只能靠截图交给视觉模型判断位置，或按固定坐标点；
2. **OCR 有延迟**（~1 秒/帧），不适合高频连续操作；
3. **输入文字**：微信的 `EditText` 即使能取到节点，`ACTION_SET_TEXT` 也常被忽略 → 用自建 ADB 通道的 `input text` 兜底（红米已验证可拿到 `uid=2000(shell)`）；
4. 截图在 `FLAG_SECURE` 页面（支付、部分银行）会是空白 —— 这类页面属于"既读不到也截不到"，只能请用户手动操作。

## 6. 兜底优先级（最终形态）

```
① 无障碍 UI 树（最快、可精确定位、支持 ACTION_CLICK）
   ↓ 退化（无可点元素 + 文本几乎为空）
② 截图 + OCR（带坐标，支持手势点击）
   ↓ OCR 也认不出（纯图标/图形界面）
③ 截图 + 视觉模型（让模型给坐标）
   ↓ 被 FLAG_SECURE 挡住
④ 交还用户，或走 ADB `input` 系列（需要 shell 通道）
```

## 7. 真机验证结果（2026-10-24 01:17~01:28，微信 8.0.76 / Android 14）

### ① OCR 读得到（38~44 条文本，坐标正确）

```
12. 微信(1411)   @(537,168)
25. 通讯录       @(404,2317)
26. 发现         @(675,2316)
32. 我           @(942,2317)
 3. diaxxxrot.  @(301,303)
 7. 重电服务站    @(318,688)
...
```

### ② 点到正确位置（两条都验过）

| 指令 | 日志 | 结果 |
|---|---|---|
| `--es tap "通讯录"` | `候选=1 策略=文字精确` / `TAP-END ok=true` | 截图确认切到**通讯录**（新的朋友/群聊/标签/公众号） |
| `--es tap "发现"` | `候选=1 策略=文字精确` / `TAP-END ok=true` | 截图确认切到**发现**（朋友圈/视频号/扫一扫/听一听…） |

### ③ “不上报树的 App”记忆生效

```
I ScreenRecognitionService: 记录「不上报无障碍树」的包名：com.tencent.mm（下次直接用 OCR）
I ScreenRecognitionService: OCR 兜底完成：44 条文本
```

`shared_prefs/app_prefs.xml` 里出现 `a11y_hostile_pkgs`。下次进微信直接走 OCR，
省掉一次注定为空的树采集 + 2 秒超时等待。

## 8. 这一轮额外加固的五处（都是为了"别再踩一次"）

| # | 问题 | 修法 |
|---|---|---|
| 1 | OCR 项没有节点路径，点击会走"找中心点可点祖先"，空树里根节点覆盖整屏 ⇒ **点到屏幕中央** | `cls=="ocr"/"vision"` 直接走坐标手势 |
| 2 | 锁屏/`FLAG_SECURE` 截出全黑图，被误判成"OCR 不工作" | 先采样亮度，`blank=true` 时明确报"截图是黑屏" |
| 3 | 状态栏"1:02""100"被当成可点目标 | 只过滤顶部 70px 内的纯时间/纯数字 |
| 4 | "快照为空"在首次采集前必然为真 ⇒ 会把正常 App 误记成不上报树 | 改成看**根节点是否空壳**（`childCount==0` 且无文本/描述） |
| 5 | 纯图标按钮 OCR 永远认不出 | 第三级视觉定位：截图叠**坐标网格** → 模型返回 JSON 坐标；解析逻辑有 6 条 JVM 单测 |

### 视觉定位的验证状态（诚实说明）

- **解析逻辑**：`VisionGroundingTest` **6/6 通过**（比例还原、夹带散文的 JSON、`label` 兜底、
  缺坐标跳过、越界裁剪、无数组返回空）；
- **端到端**：**没验成**。这台机 `chat_api_url` 为空（没配模型），且手机走蜂窝网、
  到电脑的本地网关不通 ⇒ 无法在真机上跑一次真实请求。
  代码路径会在未配置时直接返回并给出可读提示（实测日志：
  `(视觉模型未返回可用目标：可能未配置对话模型…)`），不会卡住主流程。

## 9. 复现命令

```bash
S=<serial>
adb -s $S shell am startservice -n com.aicompanion/.screen.UiProbeService              # 树体检（含逐窗口）
adb -s $S shell am startservice -n com.aicompanion/.screen.UiProbeService --ez ocr true     # OCR
adb -s $S shell am startservice -n com.aicompanion/.screen.UiProbeService --ez vision true  # 视觉定位
adb -s $S shell am startservice -n com.aicompanion/.screen.UiProbeService --es tap "通讯录"  # 按文字点击
adb -s $S shell am startservice -n com.aicompanion/.screen.UiProbeService --ez reset_hostile true  # 清空记忆
adb -s $S logcat -s UiProbe OcrView VisionGrounding ScreenRecognitionService
```

## 10. 熄屏自唤醒（2026-10-24 01:35 实测）

自动化最常见的失败原因之一是**屏幕睡着了**：截图全黑、点击落空、事件流停摆。
所以补了一套自唤醒，按"不麻烦用户"的顺序尝试：

| 顺序 | 手段 | 需要授权 | 实测 |
|---|---|---|---|
| ⓪ | `WakeLock(SCREEN_BRIGHT_WAKE_LOCK \| ACQUIRE_CAUSES_WAKEUP)` | 无（`WAKE_LOCK` 是普通权限） | **✅ 有效** |
| ① | `input keyevent KEYCODE_WAKEUP`（走 Shizuku / 自建 ADB / root） | 命令通道 | 未装 Shizuku 时不可用，但代码路径已接好 |
| ② | `wm dismiss-keyguard` | 命令通道 + **无密码锁屏** | 有密码时按设计跳过 |
| ③ | `WakeActivity`（`turnScreenOn=true`） | 无，但后台启动可能被系统拦 | 兜底，未单独验证 |

**为什么不直接用 `PowerManager.wakeUp()`**：它是 `@SystemApi`，需要 `android.permission.DEVICE_POWER`，
普通应用调用只会抛 `SecurityException`。

### 实测记录（Redmi / Android 14 / HyperOS）

```
== 主动息屏 ==
  mWakefulness=Dozing
== 触发自唤醒 ==
I UiProbe    : WAKE-BEGIN on=false locked=true
I ScreenWaker: 检测到熄屏，尝试唤醒
W ScreenWaker: 设备有密码锁，无法自动解锁
I ScreenWaker: 唤醒结果 ready=false woke=true needsUnlock=true how=wakelock:ACQUIRE_CAUSES_WAKEUP
I UiProbe    : WAKE-END 屏幕已点亮，但设备设了密码锁，需要你手动解锁后重试
== 唤醒后电源状态 ==
  mWakefulness=Awake
```

结论：**点亮成功，且不谎报**——有密码锁时明确告知需要人工解锁，而不是拿锁屏画面去 OCR
（那会识别出"1:28 / 已连接 USB 调试"这种噪声，反而让模型误判页面）。

### 什么时候自动唤醒

| 触发点 | 行为 |
|---|---|
| `refreshAndWait()`（树采集） | 熄屏先唤醒，否则事件流是停的 |
| `captureBitmap()`（截图） | 熄屏先唤醒，避免截到黑屏 |
| `refreshByOcr()` | 熄屏先唤醒；锁屏挡住则**直接返回可读提示**，不做无效 OCR |
| `formatScreenForLLM / formatScreenTreeForLLM` | 续期常亮 180s，"看→想→点"期间不睡 |
| `refreshByVision()` | 续期常亮 120s，视觉请求可能十几秒 |

### 已知限制

1. **有密码锁时无法自动解锁**——没有合法途径（`requestDismissKeyguard` 也只对无密码/可信设备有效）；
2. 个别 ROM 会忽略已废弃的 `ACQUIRE_CAUSES_WAKEUP`，此时必须靠命令通道；
3. 常亮用的是 `SCREEN_DIM_WAKE_LOCK`（变暗但不熄），不是 `SCREEN_BRIGHT`，省电一些。

## 11. 伪装系统无障碍服务类名（2026-10-24 09:38 实测成功）

### 做法

```kotlin
// 文件名不重要，关键是 **package + 类名**
package com.google.android.accessibility.selecttospeak

class SelectToSpeakService : com.aicompanion.screen.ScreenRecognitionService()
```

Manifest 里额外声明一个服务（与真实服务并存，逻辑全继承，一行都不重写）：

```xml
<service
    android:name="com.google.android.accessibility.selecttospeak.SelectToSpeakService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false"
    android:label="@string/app_name">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data android:name="android.accessibilityservice"
               android:resource="@xml/accessibility_service_config" />
</service>
```

启用（一键脚本 `tools/adb_elevate.bat` 已改成这条）：

```bash
adb shell settings put secure enabled_accessibility_services \
  com.aicompanion/com.google.android.accessibility.selecttospeak.SelectToSpeakService
adb shell settings put secure accessibility_enabled 1
```

> 前提：基类要 `open`（Kotlin 默认 final，否则编译报 "This type is final"）。

### A/B 实测（同一台机、同一个微信、同一次会话前后）

```
# 未伪装
pkg=com.tencent.mm  win#22613* 应用 com.tencent.mm  nodes=1   text=0   click=0

# 伪装后（小程序面板页）
pkg=com.tencent.mm  win#22613* 应用 com.tencent.mm  nodes=298 text=17  click=61
# 伪装后（模型实际拿到的 JSON）
{"package":"com.tencent.mm","nodeCount":85,...,"chars=16250"}
```

### 为什么"改包名"没用，必须改"类名"

| 想法 | 结果 |
|---|---|
| 把 App 包名改成 `com.android.settings` | **没用**。系统认"系统应用"靠 `ApplicationInfo.FLAG_SYSTEM`（APK 位于 /system、/system_ext 或平台签名），改包名不会得到，反而会和真系统包冲突装不上 |
| 把**服务类全名**伪装成系统无障碍服务 | **有效**。微信比对的是组件类名字符串，不校验包名/签名 |

### 顺手修掉的两个连带问题

1. **深度不够**：默认采集 `depth=8 / 150 元素`，微信层级深，浅采集只出 **19** 个节点（还全是状态栏）。
   现在 `formatScreenForLLM` / `formatScreenTreeForLLM` 在"可点元素 < 5"时**自动用全量模式重采一次**
   （实测 19 → **85**，字符 16250）。
2. **历史误记**：之前把 `com.tencent.mm` 写进了"不上报树"名单，伪装服务下会导致仍然跳过树。
   现在：伪装服务连上时**自动清空该名单**，且伪装服务下不再写入。

### 风险与边界（必须知道）

1. **这是"伪装"，不是官方支持**：类名冒充系统服务，微信一旦改成校验签名/包名就会失效；
2. **商店政策**：若上架应用商店，这种"冒充系统组件"的做法可能被判违规（自用/侧载没问题）；
3. **两个服务并存**：建议只启用伪装的那个，避免同时跑两份采集；
4. **仍可能读不到的地方**：朋友圈、部分小程序/WebView 的自绘区域，正文依旧没有文字节点 →
   这时才轮到 §3 的 OCR 兜底（两者互补，不是二选一）。
