# 权限阶梯：不 root，把操作能力做到最高

> 结论先说：**不 root 也能做到“几乎全自动操作手机”**，关键是走对四层阶梯。
> root 只在“改系统分区 / 绕过应用沙箱 / 伪装指纹”这类场景才有不可替代性，日常自动化用不到。

## 1. 四层阶梯

| 层 | 拿到什么 | 怎么开 | 能力上限 |
|---|---|---|---|
| **L1 应用内权限**（普通权限） | 录音、相机、通讯录、短信、通知、精确闹钟、所有文件访问、电池优化白名单 | 应用内弹窗 / 设置引导 | 调 API、发短信、读写 sdcard |
| **L2 系统特权服务**（免 root，但要用户手动开） | **无障碍**、通知使用权、使用情况访问、悬浮窗、默认输入法 | 系统设置里逐项打开 | **读写任意 App 的 UI 树 + 注入手势/文字 + 全局动作**；这就是“操作手机”的本体 |
| **L3 Shizuku**（ADB 级，UID 2000） | `input`、`settings put`、`screencap`、`pm grant/revoke/force-stop`、`am`、`cmd`、`dumpsys` | 装 Shizuku → 无线调试启动服务 → 应用内点授权（一次性） | **等同电脑上 `adb shell`**，能改系统设置、静默授权、强制停止、截屏，且**不需要 root** |
| **L4 Device Owner**（设备所有者，一次性 adb） | 静默安装/卸载、禁用应用、锁定任务、隐藏状态栏、权限策略 | 电脑执行一次 `adb shell dpm set-device-owner` | 接近 MDM；**要求设备无账号、会限制还原出厂** |

**推荐组合：L2 + L3。** L2 负责“看懂界面并点它”，L3 负责“L2 干不了的系统级动作”。L4 只在做专用设备（车机、展示机、老人机）时才值得。

## 2. 本应用现在的状态（2026-10-22 审计）

| 能力 | 状态 |
|---|---|
| 无障碍服务（读 UI 树 / 点击 / 手势 / 文字输入 / 滚动 / 勾选） | ✅ 已实现（`screen/ScreenRecognitionService.kt`，config 里 `canPerformGestures` / `canTakeScreenshot` / `flagRetrieveInteractiveWindows` 都开了） |
| 全局动作（返回 / 主页 / 最近任务 / 通知栏） | ✅ 已实现（`AutoOperator`） |
| 多窗口枚举（对话框、输入法弹窗） | ✅ 已实现（`service.windows`） |
| 屏幕理解（截图 + OCR + 应用分类 + 每应用技能） | ✅ 已实现（MediaProjection + ML Kit + `AppSkillManager`） |
| 应用启动 / 设置页跳转 / 短信 / 电话 / 音量 / 手电 / 剪贴板 | ✅ 已实现（`plugin/SystemActionPlugin.kt`） |
| 所有文件访问（MANAGE_EXTERNAL_STORAGE） | ✅ 已声明 + 引导 |
| **Shizuku（ADB 级）** | ⚠️ **此前是“空转”**：只有反射探测，没有依赖、没有 Provider，永远返回未授权 → **本次已补全**（见下） |
| Device Owner | ❌ 未做（一次性 adb，按需再加） |

## 3. 本次补全：Shizuku 真接入

之前 `ShellExecutor` 用 `Class.forName("rikka.shizuku.Shizuku")` 反射，但：

- 工程里没有 `dev.rikka.shizuku:api` 依赖 → 类根本不存在；
- Manifest 里没有 `ShizukuProvider` → 即使有类也无法完成权限握手。

现在补齐了三件事：

1. 依赖：`dev.rikka.shizuku:api:12.2.0` + `:provider:12.2.0`
   （**必须用 12.2.0**：13.x 声明 minSdk 24，而本应用保底 23，直接拉 13.1.5 会在
   `processDebugMainManifest` 报 `uses-sdk:minSdkVersion 23 cannot be smaller than version 24`；
   `tools:overrideLibrary` 对这条不起作用。12.2.0 的 API 已包含 `newProcess` / 权限请求 / 监听，
   且**客户端版本低于服务端不会有问题**——用户装最新版 Shizuku 也能连）；
2. Manifest：`rikka.shizuku.ShizukuProvider`（authorities `${applicationId}.shizuku`）；
3. 代码：新增 `shell/ShizukuBridge.kt`（`pingBinder` / `checkSelfPermission` / `requestPermission` / `newProcess`），
   `ShellExecutor` 的 shizuku 通道改为走它，并在 **设置 → 无障碍 → 命令执行** 里加了状态与「授权 Shizuku」按钮。

### 用户侧一次性操作

1. 安装 Shizuku（应用商店搜「Shizuku」，或 https://shizuku.rikka.app/ ）；
2. 打开 Shizuku → 「通过无线调试启动」（Android 11+，开发者选项里开无线调试，配对一次即可）；
3. 回到本应用：设置 → 无障碍 → 命令执行 → **授权 Shizuku** → 在弹窗里允许。

之后 AI 就能直接跑这些（以前要么失败、要么必须 root）：

```bash
input tap 540 1200            # 坐标点击（手势兜不住时的兜底）
input text "hello"            # 输入文字
settings put system font_scale 1.15
screencap -p /sdcard/s.png    # 截屏（不用每次弹投屏授权）
pm grant com.xxx android.permission.CAMERA
am force-stop com.xxx
dumpsys activity top
cmd notification list
```

## 3.5 不想装 Shizuku：一次性 adb 授权（已实测通过）

**先解释一个绕不过去的事实**：`shell`（UID 2000）不是一种“权限”，而是**进程身份**。它由 adb/init 分配，
SELinux 明确禁止普通应用自我提权 —— 所以“把 Shizuku 的功能自己写进应用”在原理上不成立：
任何想拿到 shell 身份的实现，都必须让 adb 帮它起一个进程，这正是 Shizuku 干的事（它只是把这套流程做成了 App）。

但 **“大部分能力”并不需要 shell 身份**，只需要**权限**；而权限可以让 adb 一次性授予，然后常驻。
所以有第二条路：`tools/adb_elevate.bat`（一次性，2 分钟，之后不用再连电脑）。

### 它做了什么（2026-10-22 在模拟器上逐项验证，6/6 通过）

| 项 | 结果 |
|---|---|
| `WRITE_SECURE_SETTINGS`（**关键**：应用从此可自行 `settings put`） | granted |
| 无障碍服务（自动打开，免手动点） | `com.aicompanion/…ScreenRecognitionService` |
| 通知使用权 | `com.aicompanion/…NotificationReaderService` |
| 所有文件访问（MANAGE_EXTERNAL_STORAGE） | allow |
| 使用情况访问（GET_USAGE_STATS） | allow |
| 电池优化白名单 | `user,com.aicompanion,10141` |
| 运行时权限（录音/电话/通讯录/通知/存储/SMS 等） | 逐条 `pm grant` |

### 用法

1. 手机开「开发者选项 → USB 调试」，连线，手机上点允许；
2. 双击 `F://stradust//android//tools//adb_elevate.bat`（它会自动找 SDK 里的 adb）；
3. 看到 6/6 和结果核对即可，拔线。

### 它和 Shizuku 的差别（诚实版）

| 能力 | adb 脚本 | Shizuku |
|---|---|---|
| 改系统设置（`settings put`） | ✅ 应用自己就能做 | ✅ |
| 打开/修复无障碍、通知使用权 | ✅ | ✅ |
| 静默授权其他权限 | ⚠️ 只能脚本里做，应用不能给自己授权 | ✅ 运行期随时 |
| 任意 shell 命令（`input` / `pm force-stop` / `dumpsys`） | ❌ | ✅ |
| 每次重装要重跑 | ✅ 要 | ✅ 要重新授权 |
| 装第三方 App | ❌ 不用 | ✅ 要 |

结论：**日常自动化（看界面、点按、输入、改设置、截屏）用 adb 脚本就够了**；
只有真的需要“随时跑任意 adb 命令”时才值得装 Shizuku。两条路可以并存，代码里通道顺序是 `shizuku → root → app`。

### 这个脚本踩过的坑（复现时注意）

1. **`.bat` 必须存成 GBK**：cmd 按系统代码页解析，UTF-8 的中文注释会把行拆碎，报 “xxx is not recognized as a command”。
2. **`WRITE_SECURE_SETTINGS` 要先进清单**：没写 `<uses-permission>` 时 `pm grant` 直接抛
   `Package has not requested permission`（本次已补进 `AndroidManifest.xml`）。
3. **拼接设置值要在设备端做**：Windows 的 `\r` 会污染 `settings put` 的值，所以无障碍/通知监听都是
   `adb shell "CUR=$(settings get …); …"` 在手机上拼。
4. **管道别转义**：`^|` 会被原样送到设备的 shell，设备上没有 `findstr`。

## 4. L2 逐项开启（漏一项就会“看着能用其实不行”）

| 开关 | 路径 | 少了会怎样 |
|---|---|---|
| 无障碍服务 | 设置 → 无障碍 → 已下载的服务 → 「AI 伙伴」 | 读不到 UI 树、点不了任何东西 |
| **受限设置**（Android 13+ 侧载包必看） | 应用信息 → 右上角 ⋮ → 允许受限设置 | 无障碍开关**灰掉点不动** |
| 通知使用权 | 设置 → 通知 → 通知使用权 | 读不到/回不了消息通知 |
| 使用情况访问 | 设置 → 特殊应用权限 → 使用情况访问 | 判断不了当前前台是谁 |
| 悬浮窗 | 设置 → 应用 → 特殊权限 → 显示在其他应用上层 | 桌宠/悬浮按钮出不来，后台拉界面被拦 |
| 电池优化白名单 | 设置 → 电池 → 无限制 | 息屏一段时间服务被杀（语音唤醒也会断） |
| 所有文件访问 | 应用内引导 → 系统页 | 读写 /sdcard 失败 |
| 精确闹钟 | 系统会自动引导 | 定时任务漂移 |

## 5. 国产 ROM 额外三步（不设就一定反复掉线）

1. **自启动**：手机管家/应用管理里允许「自启动」「关联启动」；
2. **后台运行**：省电策略设为「无限制」，加锁后台（多任务卡片下拉锁定）；
3. **后台弹界面**：小米「后台弹出界面」、OPPO「允许后台弹出界面」、vivo「后台高耗电」都要开，
   否则唤醒/定时任务拉起 Activity 会被静默拦掉。

## 6. 诚实的边界（不 root 就做不到的事）

- 读别的 App 的**私有数据**（聊天数据库、cookie）——沙箱隔离，无障碍只能看到屏幕上渲染出来的内容；
- 改**系统分区**、删预装应用、改 build.prop；
- 部分机型**移动数据开关**（Android 10+ 应用无权直接切，只能开设置页让用户点）；
- **模拟指纹/人脸/支付确认**（安全硬件隔离）；
- 静默**安装/卸载**（除非 L4 Device Owner）；
- 绕过「无障碍服务被系统杀死后需用户重新开启」——这是系统行为，应用只能检测并提醒。

## 7. 想再往上：Device Owner（可选，一次性）

前提：设备已恢复出厂 / 无任何账号登录。

```bash
adb shell dpm set-device-owner com.aicompanion/.admin.CompanionDeviceAdminReceiver
```

拿到后面能做的事：静默安装卸载、禁用应用、锁定单一任务（kiosk）、隐藏状态栏/锁屏、强制权限策略。
**代价**：设备被“托管”，部分操作要还原出厂才能撤销。日常自用手机不建议开。

## 8. 建议的落地顺序

1. 先把 L2 的 8 个开关全部打开（应用内已有检测，缺哪个补哪个）；
2. 装 Shizuku 并授权（本次已支持），把「命令执行」通道设为 `auto`，优先走 Shizuku；
3. 真遇到 L2/L3 都做不到的场景（静默装应用、kiosk），再考虑 Device Owner。
