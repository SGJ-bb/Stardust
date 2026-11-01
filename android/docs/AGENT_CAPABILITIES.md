# 星尘 · Agent 能力与机型矩阵

> 更新时间：2026-10-24 19:40
> 本文回答三件事：AI 现在能替你做哪些事、哪些手机能用、还差什么。

---

## 一、AI 现在能做什么

| 能力 | 工具 | 状态 |
|---|---|---|
| 看/建/删手机日历 | `calendar`（系统日历，日历App里那份） | ✅ 真机验证 |
| 记 App 内日程 | `add_calendar_event` | ✅ 原有 |
| 设提醒 | `set_alarm` / `set_alarm_at_time` | ✅ 原有 |
| 联网搜索 | `search_web` | ✅ 已修并实测（8 条 / 2.3s） |
| 文件增删查改 | `file`（读/写/追加/搜索/列举/删除/移动/信息） | ✅ 真机验证 |
| 执行 shell 命令 | `run_shell` | ✅ 有 shell 身份时可用 |
| 改系统设置 | `device` → `settings_put` | ✅ 写后回读比对 |
| 点屏幕 / 输入 / 滑动 | `device` → `tap` / `text` / `swipe` | ✅ 点完比对界面是否真的变了 |
| 截图 | `device` → `screenshot` | ✅ |
| 应用管理 | `device` → `app_list` / `app_start` / `app_stop` / `app_clear` | ✅ |
| 多步任务计划 | `plan`（set/update/get） | ✅ 原有 |
| **系统功能（20+ 项）** | `system`（命令目录） | ✅ 编入，待红米上跑实际效果 |

### 过程可见 + 危险动作要你点头（本轮新增）

聊天页输入框上方多了一条**活动条**：

- **执行中**：转圈 + 一句人话，例如「点按屏幕 (540, 1200)」「写进手机日历「周会」」，右侧显示耗时
- **需要确认**：变色卡片，写明要做什么、为什么危险，两个按钮「允许 / 不允许」
  - 60 秒不点按**拒绝**处理 —— 宁可少做，不背着你做不可逆的事
- **点一下"详情"**：展开最近 12 条，看到每一步做了什么、成没成、花了多久

闸门分三档：

| 档位 | 例子 | 行为 |
|---|---|---|
| 直接拒绝 | 改 `adb_enabled`（会切断自己的通道）、`rm -rf`、`pm uninstall`、`reboot` | 不做，并告诉模型换法子 |
| 需要确认 | 删文件、清除应用数据、强制停止应用、改系统设置、发消息 | 弹卡片等你点 |
| 直接执行 | 截图、查设置、点屏幕、读文件、搜索 | 直接做，只记状态 |

### `system` 工具：不用为每个功能写插件

这是本轮回答"闹钟那类功能能不能直接 shell 调"的产物。

做法是**命令目录**（`system/SystemOps.kt`）：一个 op 一条命令 + 一条回读命令，
不用为每个系统功能写 Kotlin 插件。当前目录：

| 分类 | 操作 id |
|---|---|
| 时间 | `alarm.set`（系统闹钟）、`timer.set` |
| 显示 | `brightness.set`、`brightness.auto`、`rotate.set`、`timeout.set`、`stayon.set` |
| 声音 | `dnd.set`（免打扰）、`media.key`（播放/暂停/切歌/音量键） |
| 网络 | `wifi.set`、`data.set`、`bluetooth.set`、`airplane.set`、`gps.set`、`net.info` |
| 采集 | `screenshot`、`record.set` |
| 状态 | `battery.info`、`storage.info`、`screen.off`、`vibrate.set` |
| 应用 | `app.install`、`app.uninstall`、`cache.clear` |

**为什么不能"直接让 AI 自由拼 shell"** —— 实测这台 Android 10：

```
cmd media_session volume --get   → No shell command implementation
cmd wifi set-wifi-enabled        → Security exception: Uid 2000 does not have access
cmd notification set_dnd on      → 无输出，也没生效
cmd connectivity airplane-mode   → 无输出，也没生效
```

三条是静默失败。模型看到 exit=0 会以为成功了，然后基于错误前提继续往下做。

所以每条 op 都强制带**回读验证**：

```
已执行：设置屏幕亮度（0-255）
命令：settings put system screen_brightness 120
回读：120
✓ 已确认生效（期望 120）
```

不对就明说：

```
⚠️ 回读值与期望不符（期望 120，实际 51）：系统可能没接受，别当成功
```

参数全部走类型 + 范围校验（`hour` 必须 0-23、`on` 必须 true/false），不接受任意字符串拼进命令。

**扩展一个新功能的成本 = 在目录里加一条 Op**，不用碰 UI、不用重新设计工具协议。

---

## 二、哪些手机能用（重要）

| 机型 / 系统 | 免电脑配对 | 常驻服务（免 WiFi） | 结论 |
|---|---|---|---|
| **红米 / Android 13–14**（已实测） | ✅ | ✅ | **推荐做主力** |
| 其他 Android 11+ 国产 ROM | ✅ 大概率 | ✅ | 可用，个别 ROM 需试 |
| **华为 Mate 10 / Android 10**（备用机） | ❌ 系统没有「配对码」功能 | ❌ | **不适合**；root 也走不通（见下） |

为什么华为不行：**「无线调试 → 使用配对码配对设备」是 Android 11 才有的系统功能**。Android 10 上没有这个入口，App 再怎么改也变不出来。唯一的路是电脑 USB 执行一次 `adb tcpip 5555`，但华为/荣耀的 OEM 层之前实测会拦掉 TCP 授权，所以这台机基本走不通。

红米那台是唯一跑通过完整链路（配对 → TLS 1.3 → shell(2000) → 常驻守护进程 → 关 WiFi 仍可操作）的机器。

### 华为这台刷 root 行不行

实测结论：**基本不可行**，三道门全关着。

| 门 | 状态 | 说明 |
|---|---|---|
| Bootloader 解锁 | `ro.boot.flash.locked = 1`（锁定） | 必须解锁才能刷 |
| 解锁码 | **官方 2018-07 起停止发放** | 华为/荣耀不再提供任何官方解锁渠道，第三方付费码来源不明、有变砖和植入风险 |
| fastboot 通路 | 电脑侧无华为 fastboot 驱动 | 实测重启进 bootloader 后，`adb devices` 和 `fastboot devices` 都是空，Windows 只认到「USB Composite Device」 |

而且**即使 root 了，对这个方案的提升也很有限**：我们已经拿到 `shell(2000)`，
改设置、截屏、点屏幕、装应用、读日历全都能做；root 只多出"改系统分区/装模块"这类边缘能力，
代价是变砖风险 + 每次 OTA 要重刷。

**结论：这台继续当备用机就好，不值得为 root 折腾。** 真正需要提升的是红米那条链路的稳定性。

---

## 三、UI 密度

- **首页**（我的伙伴）：大片留白，不紧凑，反而偏空
- **设置页**：真正密的地方，几十个开关排成长列表，没有分组折叠
- **聊天页**：正常；新增的活动条只在执行/待确认时出现，空闲不占高度，不会加剧拥挤

如果要做，建议动设置页：按「常用 / 权限 / 高级」折叠分组，或把开关密度降到每屏 6–8 项。

---

## 四、还差什么

1. **`system` 工具实跑**：目录和回读逻辑已编入，但这台华为没有 shell 通道，只能验到"降级提示正确"。真实的命令执行要在红米上跑（`brightness.set`、`dnd.set`、`alarm.set` 各验一次）。
2. **红米上回归**：agent 活动条、确认卡片、日历、搜索、system 目录都要在红米上完整验一遍。
3. **其他系统接口**：通讯录、短信、通知、剪贴板还没做。按 `SystemOps` 的模式补（加一条 Op 即可），不用写新插件。

---

## 附：调试入口

```bash
# 全链路自检（搜索 / 日历 / 闸门 / 活动记录）
adb shell am start -n com.aicompanion/.ui.MainActivity --es agent_test 1
adb logcat -s AgentTest

# 触发一张「需要你确认」卡片（看 UI 效果）
adb shell am start -n com.aicompanion/.ui.MainActivity --es agent_demo 1

# 清理测试写入的日历事件
adb shell am start -n com.aicompanion/.ui.MainActivity --es cal_clean 1
```

---

## 真机回归（Redmi / Android 14，2026-10-25）

| 能力 | 结果 |
|---|---|
| 联网搜索 | ✅ 786ms，返回真实结果（原：DDG 超时 5s / Bing 只出 2 条） |
| 系统日历 | ✅ agenda / add / 回读 / delete 四步闭环，事件 id 递增 |
| 危险命令闸门 | ✅ `rm -rf` 被 DENIED，未执行 |
| system 目录 | ✅ 按 Android API 34 生成可用清单 |
| system 执行 | ✅ 亮度设为 120，回读 `settings get system screen_brightness` = 120 |
| 人格锚点/演化 | ✅ 见 docs/PERSONALITY_SYSTEM.md |
| 确认卡片 | ✅ 出现「清除「测试应用」的全部数据」+ 详情；超时 90s 自动按拒绝（`allow=false`） |

### 本轮修掉的两个逻辑缺陷

1. **通道未连时直接放弃**：`system` 工具原先 `if (!AdbSelf.isReady()) return "没有 shell 通道"`，
   即"AI 想做系统操作，但这次恰好没连上"就报失败。现在改为**主动 `autoConnect()` 一次**，
   失败才报错。用户不该知道"通道"这个概念。
2. **失败被记成 OK**：工具用字符串表达失败（"没有 shell 通道"/"回读值与期望不符"），
   `runToolInternal` 却一律 markStatus(true)。活动条因此显示假成功。
   现在统一识别失败前缀，改为 FAIL。

---

## 功能板块去重（2026-10-25）

判断标准：**这件事 AI 用 shell / 系统 Provider 能不能做？能，就不该在 App 里再写一遍。**

| 板块 | 原实现 | 判定 | 处理 |
|---|---|---|---|
| 闹钟提醒 | `AlarmPlugin` / `AlarmAtTimePlugin` → AIActionManager + AlarmManager（App 私有） | 重复：`system.alarm.set` 直接调系统闹钟，且系统闹钟更可靠（App 被杀也响） | **取消注册**，工具下架 |
| 日程记录 | `SchedulePlugin` → App 私有日程表 | 重复：`calendar.add` 写系统日历，用户在系统日历里能看见 | **取消注册** |
| 日程页入口 | 侧边栏「日程」+ 快捷入口 | 与系统日历两套数据，AI 写不进用户看得见的地方 | **入口移除** |
| 系统日历 | `calendar` 工具（SystemCalendarBridge） | 不是重复，是唯一正确实现 | 保留，成为唯一数据源 |
| 定时唤醒 | WakeUpTask（语音唤醒任务） | 不是重复：系统没有"到点用某个声音唤醒我"的能力 | 保留 |
| 签到 / 成就 / 日记 | App 内游戏化数据 | 不是重复 | 保留 |

移除后 AI 的行为映射：

```
「X 点叫我」「10 分钟后提醒我」 → system → alarm.set / timer.set
「记一下这个安排」「我周三有个会」 → calendar → add（写系统日历）
「我明天有什么安排」           → calendar → agenda（读系统日历）
```

> 旧插件类定义保留在 `BuiltinPlugins.kt` 里但不再注册：历史已设置的闹钟仍然会响
> （`AlarmActivity` 还在），不会因为这次改动把用户已有的提醒弄丢。

## UI 树 → skill（真机验证通过）

```
$ am startservice -n com.aicompanion/.screen.UiProbeService \
    --es tool_call '{"name":"skill","args":"{\"action\":\"learn_ui\"}"}'

TOOL[skill] 已为「系统设置」创建技能：记录页面 设置（18 个元素）
TOOL[skill] 已更新技能「系统设置 操作指南」：本次记录 设置（共 18 个元素），该技能现含 1 个页面
```

生成的技能片段（真实输出）：

```markdown
## 页面：设置（账号、云服务、会员权益等）

### 可点元素
| 文字/描述 | 类型 | 坐标 |
| 新版本 | 可点 | (903, 870) |

### 可见文本（带坐标，可按文字定位后点击）
| 文字 | 坐标 |
| WLAN | (274, 1117) |
| 蓝牙 | (251, 1274) |
| 移动网络 | (299, 1431) |
| 个人热点 | (299, 1588) |
```

三个真机问题已修：

1. **页名取成了搜索框提示**（"搜索系统设置项"）→ 改成优先取 Activity 名；
   拿不到时用窗口 title，最后才退回文本启发式，并排除"搜索/请输入"这类提示语。
2. **可点元素只有 1 条**：MIUI/微信的列表项在无障碍树里不标 `clickable`，
   只列 clickable 的话模型知道有"WLAN"却不知道点哪 → 增加"文字 → 坐标"表（最多 30 行）。
3. **同一页反复学变成多页**（页名猜两次就两条）：`mergePage` 增加内容相似度匹配
   （文字集合 Jaccard ≥ 0.5 视为同页并替换）。连学 3 次页面数稳定为 1，单测锁住。

---

## 2026-10-25 第二轮修复

### 记忆检索：「明明记得却说没找到」

三个病根，都实测确认过：

| # | 病根 | 证据 | 修法 |
|---|---|---|---|
| 1 | 只查**当前场景**记忆池 | 日志 `Index built for memory_pool: 1 entries`，而跨场景记忆、已巩固的要点/长期印象都不在里面 | 候选集合 = 当前池 + 跨场景共享 + 非归档的要点/语义层 |
| 2 | 向量阈值 0.1 太高 | 用的是 TF-IDF（不是真 embedding），中文短句余弦普遍 < 0.1 → 全被滤掉 | 阈值降到 0.03 |
| 3 | 没有兜底 | 检索为空就直接回"未找到" | 加 2-gram 关键词兜底；仍为空则返回最近 N 条并标注"供参考" |

实测：

```
修前：TOOL[search_memory] 未找到与「主人喜欢什么」相关的记忆。
修后：TOOL[search_memory] [最近的记忆]（没有直接命中，供参考）
      - 哥哥下周二上午10点在302会议室参加项目会议，需带季度报表…
      - 用户家里养了狗"乌龙"和鹦鹉"话梅"。
```

> 原则：**宁可多给几条让模型自己判断，也不要一句"没找到"**——后者会让 AI 显得失忆。

### UI 树 skill：不再记坐标，改用文字定位

用户的判断是对的：**列表顺序会变**（QQ 联系人、微信会话、搜索结果），记第几个或记坐标必然点错人。

- 新增 `device` 动作 **`tap_text`**：在无障碍树上按文字查（精确 → 包含），自己不可点就向上找可点的祖先，最后才退化成一次手势点击
- 技能正文改版：`| 文字 | 操作 |` → `device tap_text "WLAN"`；坐标只留给"完全没有文字的图标按钮"
- 操作要点里明确写：列表顺序会变、找不到就 swipe 再找、不猜坐标

实测（系统设置页）：

```
DevicePlugin: tap_text「WLAN」命中=1 部分=0 clicked=true
TOOL[device] 已点击「WLAN」
→ 页面确实切到 WLAN 设置页
```

### 技能管理入口与去重

- 设置 → 功能入口 → **技能库**（新增入口，之前只能从「无障碍设置」里进去，基本等于藏起来了）
- 新增 `skill` 动作 **`dedupe`**：按规范化后的 App 名合并（同一个拨号应用历史上留下 4 份：电话 / 系统拨号 / 电话（联系人/拨号）…）
- 学习后自动更新描述，带上"已采集 N 页"

现状：微信（LauncherUI，45 元素）、系统设置（2 页）都已收录；`learn_ui` 会按包名合并进已有技能，不再产生第二份。

### AI 主动消息：三个 bug 叠加，等于从不触发

| # | bug | 后果 |
|---|---|---|
| 1 | `onPause()` 里移除定时器 | 切到后台（正是该主动说话的时候）就永久停摆；而 `trigger()` 又要求"非前台"，两个条件互斥 → **永远不发** |
| 2 | `shouldTriggerInteraction(sm.nagFrequency.name.lowercase())` 传 `low/medium/high`，函数只认 `frequent/normal/infrequent` | 频率设置全部落到 else 分支 → 3/10/30 分钟设置无效 |
| 3 | 双重随机门控：先按频率随机 10/30/60%，再过间隔判断 | 两道门相乘，实际触发率低到像坏了 |

修法：`onPause` 不再取消（`onResume` 重排）；改用 `getNagFrequency()`；门控改为"到间隔 + 85% 命中"；新增 `markNagSent()` 在真正发出后记录时间。

> **注意：这一项只做了逻辑修正，没有做真机长时验证**（需要把 App 放后台等 3~10 分钟）。
