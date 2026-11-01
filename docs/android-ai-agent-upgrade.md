# AI 操作手机能力升级说明（2026-09-11）

本次针对四个问题做了根因定位与修复：

1. AI 说「看不见 UI 树 / 只能看见语义树」
2. 进入某个 App 后拿不到部分板块内容、「未找到可点击元素」（页面明明满是可点元素）
3. 工具调用轮次疑似被写死为 3
4. 需要「技能（Skill）+ Skill Creator」能力，以及让 AI 在安卓上执行命令

---

## 一、根因诊断

### 1.1 「看不见 UI 树」——三处叠加

| # | 根因 | 位置 | 后果 |
|---|---|---|---|
| A | 无障碍服务配置缺少 `flagReportViewIds` | `res/xml/accessibility_service_config.xml` | 拿不到资源 ID，只能靠文字匹配，元素一改名就失效 |
| B | 缺少 `flagIncludeNotImportantViews` | 同上 | 被标记 `importantForAccessibility=no` 的视图会让**整棵子树被系统剪掉**。自绘控件 / Compose / 混合 WebView 大量使用，这就是「板块内容拿不到」的系统级原因 |
| C | 缺少 `flagRetrieveInteractiveWindows` | 同上 | 只能读活跃窗口，**对话框 / 弹窗 / 悬浮窗 / 输入法窗口里的内容全部不可见** |
| D | 采集时只输出有文字的节点 | `ScreenRecognitionService.buildStructured` | 输出的是「语义文本列表」，不含层级结构、类名、可点性，所以 AI 说「只能看见语义树」 |

### 1.2 「拿不到板块内容 / 未找到可点击元素」——四个硬伤

1. **深度写死 3 层、元素写死 50 个**
   真实 App 的层级普遍 10 层以上，列表项内容在 5～10 层。
   更严重的是元素计数到 50 后 `return`，**整棵剩余子树（含纯文本）被静默丢弃**，既不提示也不截断标记。

2. **可点击容器被丢弃**
   Android 里 `clickable=true` 常常挂在**没有文字**的容器上（ConstraintLayout / CardView / RecyclerView item），
   文字在子 TextView 里。旧代码只取节点自身的 `text/contentDescription`，标签为空 → 整个可点区域被跳过。
   → 这正是「页面明明全是可点击元素，却报未找到可点击元素」。

3. **点击解析太脆**
   `findNodeByBounds` 用 `Rect` 精确相等匹配节点，父子节点 bounds 相同时会打错目标；
   树一变化（异步刷新、动画未结束）就找不到；`ACTION_CLICK` 返回 false 时**没有任何兜底**。

4. **规划前读的是旧页面（最隐蔽、影响最大）**
   `AutoOperator.formatScreenForLLM()` 调用的是**异步**刷新 `refreshScreenData()`，然后立刻读缓存
   `getLastScreenText()`。缓存非空时直接返回——于是 LLM 看到的往往是**上一个页面**的内容。
   表现就是「刚点进 App 就瞎了」。

### 1.3 架构层面的根因：一次规划、盲执行

`PhoneOperationPlugin` 旧流程是：

```
读一次屏幕 → LLM 规划整段动作序列 → 顺序执行 N 步
```

LLM 只在开局看过一次屏幕（而且还是旧缓存），之后每一步都是盲走。
即使 UI 树采集完全正确，进入新页面后仍然无从判断。
**这是「点进一个 App 就拿不到某些板块」的结构性原因，必须改成闭环。**

### 1.4 工具调用轮次

`ApiClient.sendChatWithToolLoop()` 里写死：

```kotlin
val maxIterations = 3
```

而设置页里的「最大操作轮次」(`aiOperationMaxRounds`，默认 15) 控制的是**手机操作的动作步数**，
和这个「AI 连续调用工具的轮数」是两个完全不同的参数——所以设置里确实没有能改它的地方。

---

## 二、已实施的修改

### 2.1 UI 树采集（`ScreenRecognitionService.kt` 重写）

- **多窗口采集**：活跃窗口 + 其它窗口（系统对话框 / 输入法 / 别的包名），按 bounds 去重，输出带 `— 附加窗口[类型] 包名 —` 分隔。
- **点击容器标签继承**：可点节点自身无文字时，向下最多 4 层找第一个文字作为标签，并在输出里标注 `{类名 #资源ID}`。
- **无文字可点元素也会列出**：输出 `‹无文字可点元素› {ImageButton} @540,2100`，并分配 index，保证至少能用索引/坐标点。
- **输出结构化树**：缩进表示层级，`[n]` 可点元素、`[input]` 输入框、`(scroll)` 可滚动区、`×N` 同名折叠、`⚠ …已截断` 截断标记。
- **同步刷新**：新增 `refreshAndWait()`，提交到单线程队列并阻塞等待，保证读到的是**当前**页面。
- **点击五级兜底**：路径定位 → 精确 bounds → 中心点最近可点祖先 → 祖先回溯 → **坐标手势 tap**。
- **深度/元素上限可配置**：默认深度 8、元素 150，可在设置页调整（3–20 / 30–400）。
- 新增 `TYPE_WINDOW_CONTENT_CHANGED` 监听（部分 App 换板块只发这个事件）。
- 新增 `getCurrentPackageName()`，供闭环检测「是否切换了 App」。

### 2.2 无障碍服务配置

```xml
android:accessibilityFlags="flagDefault|flagReportViewIds|flagIncludeNotImportantViews|flagRetrieveInteractiveWindows"
android:accessibilityEventTypes="...|typeWindowContentChanged"
```

> 服务需要**关闭后重新开启**才会让新 flags 生效。

### 2.3 反应式闭环（`PhoneOperationPlugin.kt` 重写）

```
while 步数 < 上限:
    读当前屏幕（同步刷新）
    LLM 决策：{"actions":[...]} / {"done":true} / {"error":"..."}
    执行 1~4 个动作；一旦检测到切换 App 立即停下
    把执行结果写进历史，回到观察
```

- 每轮都带着「当前屏幕 + 已执行历史 + 进度」请求决策，LLM 真正看得见新页面。
- 全失败时自动降级用**完整树**重读，并提示先 scroll 翻页。
- 连续 4 轮全失败 / 连续 3 轮返回脏数据 → 停止并给出可操作的建议。
- 保留 `single_shot` 旧策略（设置里可切回，用于排障）。

### 2.4 工具调用轮次可配置

- `SettingsManager.aiToolLoopMaxRounds`（默认 8，范围 1–30）
- `ApiClient.defaultToolLoopMaxIterations` 静态默认值，`sendChatWithToolLoop(..., maxIterations=)` 显式覆盖
- 达到上限时的兜底话术改为可操作提示，而不是「让我想想怎么回答你」
- 设置页新增滑块：「AI 工具调用最大轮次」，并与「手机操作最大轮次」明确区分说明

### 2.5 技能库 + Skill Creator

新增 `com.aicompanion.skill` 包：

| 文件 | 职责 |
|---|---|
| `Skill.kt` | 技能模型（id/名称/描述/适用 App/触发词/正文 Markdown/来源/版本/使用统计），含 frontmatter 读写 |
| `SkillStore.kt` | `filesDir/skills.json` 持久化；清单渲染（渐进式披露）、按指令+包名打分匹配、Markdown 导入导出、旧 `app_ui_skills.json` 一次性迁移 |
| `SkillCreator.kt` | AI 生成技能 / 从成功操作轨迹归纳技能 / 手动模板 |
| `plugin/SkillPlugin.kt` | `skill` 工具：list / get / create / update / delete / search |
| `ui/skill/SkillScreen.kt` | 技能库界面：列表、启停、详情、手写编辑、AI 生成、AI 改进、导入导出 |

**渐进式披露**：系统提示里只注入「技能名 + 一句话描述 + 适用 App + 触发词」，
AI 判断相关时才用 `skill(action=get)` 读取正文——平时几乎不占 token。

**闭环学习**：一次操作成功且动作数 ≥ 3 时，自动调 LLM 归纳成技能（`source=auto`），下次直接复用。

### 2.6 安卓命令执行

新增 `com.aicompanion.shell` + `plugin/ShellPlugin.kt`，三级通道自动降级：

| 通道 | 前提 | 能力 |
|---|---|---|
| **APP**（默认可用） | 无 | 以本应用身份执行：`getprop`、`pm list packages`、`dumpsys`（部分）、`am start`、自身沙箱文件操作、`logcat`（仅本应用）。**不能** `input tap` / `settings put` / `screencap`（需要更高权限） |
| **SHIZUKU**（推荐） | 安装 Shizuku 并授权本应用 | ADB(shell UID) 级：`input tap/text/swipe`、`settings put`、`screencap`、`dumpsys`、`pm grant` 等 |
| **ROOT** | 设备已 root | root shell，能力最全 |

安全设计：
- 默认**关闭**（`shellEnabled=false`），需在设置里显式打开
- 高危命令（`rm -rf`、`pm uninstall/clear`、`settings put secure/global`、`reboot`、`dd`、`mkfs`、`wipe` 等 17 条规则）默认**拦截**，需另开「允许高危命令」
- 拒绝由模型自行申请放行——放行只认用户设置
- 命令超时（默认 20s，可调）+ 输出截断（8KB）+ 超时强制杀进程

---

## 三、使用与调参建议

1. **首次升级后**：到系统设置里把无障碍服务**关闭再开启**（让新 flags 生效）。
2. **看不到某板块 / 找不到元素时**，按顺序尝试：
   1. 设置 → 无障碍 → UI 树采集：调大「最大深度」（8 → 12）和「最多登记可点击元素」（150 → 250）
   2. 输入框里直接说「先向下滑一屏再找」——闭环模式下 AI 会真的 scroll + read
   3. 若该板块是 WebView 或自绘（游戏、部分金融 App），UI 树本身就没有语义，需要走坐标点击或截图视觉方案
3. **多步任务中断**：调高「AI 工具调用最大轮次」和「手机操作最大轮次」。
4. **要 ADB 级能力**：装 Shizuku → 在 Shizuku 里授权本应用 → 设置页会显示「Shizuku(ADB级)：已授权」。
5. **技能沉淀**：完成一个复杂流程后，到技能库看是否自动生成了技能；也可以手写，或让 AI 从一句话生成。

---

## 四、尚未覆盖 / 建议的下一步

1. **视觉兜底**：`canTakeScreenshot` 已在配置里开启，但尚未接入。
   对纯图像/自绘界面（游戏、部分银行 App），把截图喂给视觉模型是唯一可靠的方案。
   建议下一步：`AccessibilityService.takeScreenshot()`（API 30+）→ base64 → 作为 image 一并发给多模态模型。
2. **Shizuku 授权引导**：当前用反射调用，未授权时只能提示。建议后续接入 Shizuku 官方依赖，在 App 内做一键授权弹窗。
3. **技能与工具的打通**：技能正文里可以写 shell 命令，目前靠 LLM 自觉调 `run_shell`。可考虑在技能模型里加结构化字段（`steps[].tool`）。
4. **UI 树体积控制**：深度/元素调大后单次请求 token 明显增加，建议后续加「视口内元素优先 + 自动分页」策略。

---

# 追加：AI 回复的呈现方式（分段 / 一次性 / 流式）

> 2026-09-11 追加。起因：AI 回复总是被拆成一条一条的多条气泡，用户想要「一次性输出」或「流式输出」，并且在设置里找不到可调项。

## 五、根因：那个开关是个死配置

`ChatViewModel` 里确实读过一个开关：

```kotlin
val noSplit = getApplication<Application>()
    .getSharedPreferences("app_prefs", 0)
    .getBoolean("pref_ai_no_split", false)
```

但**全项目没有任何地方写过 `pref_ai_no_split`**（grep 只能命中这一处读取）。
也就是说它永远返回 `false`，永远走 `processSplitResponse` 分段路径 —— 设置里当然找不到，因为它根本没有 UI。

真实的分段逻辑在 `humanizer/Humanizer.kt`：按句拆成 `HumanizedChunk`，每条之间还有随机延迟（`800 + random(600)` ms），偶尔还会插一条"打错字重发"的拟人气泡。这就是"一段一段多条消息"的来源。

## 六、已实施的修改

### 6.1 设置项（`SettingsManager`）

```kotlin
const val REPLY_MODE_SPLIT  = 0   // 分段拟人（默认，保持原行为）
const val REPLY_MODE_SINGLE = 1   // 一次性整段
const val REPLY_MODE_STREAM = 2   // 流式逐字

var aiReplyMode: Int   // 存 companion_settings / ai_reply_mode
val isStreamReply: Boolean
```

读取时做了一次**旧配置迁移**：若旧的 `app_prefs.pref_ai_no_split == true`，首次读取自动落到「一次性」，不会让老用户的设置失效。

设置页位置：**设置 → AI 设置 → 回复呈现方式**（三档药丸选择器，与「深色模式」同款样式），带一句说明文字。

### 6.2 流式输出（`ApiClient`）

没有新开一条请求构造逻辑，而是给已有的 `sendChat` 加了两个可选参数，**复用全部现有的错误处理与 400 重试链**：

```kotlin
fun sendChat(..., stream: Boolean = false, onDelta: ((String) -> Unit)? = null): ChatResponse?
```

- 请求体上加 `"stream": true`，头加 `Accept: text/event-stream`。
- 新增 `private fun executeStream(...)`：用 OkHttp 的 `BufferedSource.readUtf8Line()` 逐行读 SSE，
  解析 `data: {...}` 帧，取 `choices[0].delta.content` 片段累加后回调。
- **tool_calls 分片拼接**：流式下 `function.arguments` 是按片段下发的，按 `index` 归并
  `id / name / arguments`，最后拼成完整的 `ToolCall`。这是流式最容易踩的坑。
- **情绪标记处理**：AI 输出末尾带 `[[emotion:xxx]]`。流式时必须边流边剥
  （`stripEmotionTagStreaming`），连"只收到一半"的中间态（`[[emot`）也要藏起来，
  否则用户会看着标记慢慢长出来。
- **三级降级**：401/402/429 直接报错；其它 4xx/5xx、零数据帧、IO 异常 → 返回 null →
  `sendChat` 继续往下走**非流式**逻辑（那里有完整的裁历史/去工具重试）。
  所以「中转站不支持流式」不会导致发不出消息，只会失去流式效果。

`sendChatWithToolLoop` 同样加了 `stream` / `onDelta` 参数并透传给每一轮 `sendChat`，
所以**工具调用轮次也是流式的**。

### 6.3 单气泡刷新（`ChatViewModel`）

```kotlin
private var streamBubbleIndex = -1   // 本轮流式气泡下标
private fun onStreamDelta(text: String)   // 参数是「本轮已累计的完整文本」
private suspend fun finalizeStreamResponse(...)
```

关键点（**踩坑记录**）：

1. `onDelta` 回调的是**累计完整文本**而不是增量片段 —— 调用方直接覆盖气泡即可，
   不需要自己拼接，也天然解决了"每一轮工具调用后文本要重置"的问题。
2. **必须替换成新实例**：`ChatMessage` 是 data class，如果原地改 `var` 字段，
   Compose 侧拿到的还是同一个对象引用、`equals` 判定相等 → LazyColumn 列表不重组 → **界面不刷新**。
   所以用 `_messages[idx] = _messages[idx].copy(text = text, isPartial = true)`。
3. **节流**：内容每次都写进列表（不丢字），但最多每 60 ms 通知一次 UI（`OnStreamDelta`），
   否则每个字都触发一次全列表快照 + 重组。同时 UI 事件缓冲区从 64 提到 256。
4. 首个增量到达时顺手 `SetTypingIndicator(false)`，避免"正在输入…"和流式气泡同时出现。
5. 最终回复为空时**删掉流式气泡**，不留空气泡。

### 6.4 已有的流式 UI 直接复用

项目里其实早就写好了流式/部分消息的 UI，只是从没被触发过：

- `ChatMessageItem.kt`：`message.isPartial` 时在气泡末尾渲染**闪烁光标** `|`。
- `ChatAdapter.kt`：`updateLastPetMessage(text, isPartial)`。
- `ChatDiffCallback.kt`：按 `text + isPartial` 判断变更。

另外给 `ChatScreen` 补了一个跟随滚动：消息条数不变时（流式），靠 `messageVersion`
变化触发，且只在用户已经贴在底部时才滚动，不打断往上翻阅历史。

## 七、三种模式对照

| 模式 | 气泡形态 | 首个字出现速度 | 依赖 |
|---|---|---|---|
| 分段拟人（默认） | 多条，按句拆 + 拟人延迟 | 等整段生成完 | 无 |
| 一次性 | 单条，整段 | 等整段生成完 | 无 |
| 流式输出 | 单条，逐字增长 + 闪烁光标 | **最快**（首 token 即显示） | 模型/服务端需支持 SSE |

## 八、边界与已知未覆盖

1. **群聊不跟随该设置**：群聊由 `GroupChatManager` 自己管理消息（多角色、多气泡），
   仍然走非流式，避免多角色并发写入同一条消息。
2. **不能中途停止生成**：流式进行中没有「停止」入口，需要后续在发送按钮上做「停止/发送」二态。
3. **重试期间的重复文本**：流式中途断流降级到非流式时，会先用断流时的部分文本渲染，
   请求成功后由最终文本覆盖 —— 表现为"重新长了一遍"，属可接受行为。

---

# 追加：AI 文件读写能力（2026-09-11 晚）

## 九、设计：把「文件操作」与「命令执行」分开

原先只有 `run_shell`，让 AI 读写文件必须绕 shell。这条路有三个问题：

1. **默认不可用** —— shell 是默认关闭的（有安全顾虑），文件能力就被一起关掉了；
2. **能力弱** —— APP 通道以应用身份跑，`cat`/`echo` 勉强能用，但列目录、判类型、限大小都得手拼命令；
3. **结果不可控** —— 输出是裸 stdout，AI 容易把乱码/截断当成正常结果。

所以新增独立的 `file` 工具：**不经过 shell、默认开启、结构化返回**。分工是
「`file` 管结构化文件操作，`run_shell` 管命令与二进制处理」。

## 十、沙箱边界（`file/AgentWorkspace.kt`）

三个根目录，按「零权限优先」排序：

| 前缀 | 真实路径 | 权限要求 |
|---|---|---|
| `workspace/` | `<filesDir>/agent_workspace` | 无（始终可用） |
| `external/` | `/sdcard/Android/data/<pkg>/files/agent_workspace` | 无（API 19+） |
| `public/` | `/sdcard/StardustAgent` | 需 MANAGE_EXTERNAL_STORAGE |

越界防护做了三层：
- 前缀匹配（`workspace`/`private`/`work`、`external`/`ext`/`app`、`public`/`sdcard`/`storage`）；
- 无前缀的相对路径默认落 `workspace/`；绝对路径必须落在某个根目录内；
- **`canonicalPath` 前缀校验** —— `..` 与符号链接都逃不出根目录；同时拒绝删除根目录本身。

设定上限：单次读 256KB、写 4MB、搜索 100 条/最多扫 2000 个文件。

> 注意 `SkillStore` 的文件在 `<filesDir>/app_ui_skills.json`，**不在** `agent_workspace` 内，
> 所以文件工具碰不到技能库 —— 沙箱是收敛的，不是「整个 filesDir」。

## 十一、工具动作（`plugin/FilePlugin.kt`）

单一工具 `file`，`action` 分派：`roots / list / read / write / append / mkdir / copy / move / delete / search / info`。

几个针对 LLM 的取舍：
- **`read` 带行号输出**（`12│ 内容`）—— 便于后续做精确定位与改写；
- **大文件必须先 `offset`/`limit`** —— 超过 256KB 且没给 offset 时直接拒绝并给出分段建议，
  避免一次性灌爆上下文；
- **`list` 输出大小+时间+类型**，条目上限 300，超出提示用 `glob` 收窄；
- **二进制识别**（扩展名表 + 探测前 8KB 的 NUL）—— 读到图片/压缩包时明确说"这是二进制"，
  而不是吐一堆乱码；
- **`delete` 非空目录必须 `recursive=true`**，且拒绝删除工作区根目录。

默认**开启**（`SettingsManager.fileOpsEnabled`），因为它的边界是应用自己的目录，零权限零风险；
公共存储是额外能力，需要用户主动授权。

## 十二、Live2D 图层遮挡输入框（bug 修复）

**现象**：皮套图层"看着透明却点不动输入框"。

**根因**（两层叠加）：
1. **层级错了，而且 `zIndex` 从来没生效过。** `ChatScreen` 里输入栏嵌在消息 `Column` 内部，
   Live2D 图层是外层 `Box` 的兄弟 —— **Compose 的 `Modifier.zIndex` 只在同一父节点的兄弟之间比较**，
   所以挂在输入栏上的 `zIndex` 与 Live2D 根本不在一个比较集合里，形同虚设。
2. **全屏 WebView 吞掉所有触摸。** `Live2DWebView` 是 `match_parent`，而
   `Live2DCoordinator.setupTouch()` 注册的 `touchHandler` 对 `ACTION_DOWN/MOVE/UP` 一律 `return true`，
   `onInterceptTouchEvent` 也恒返回 true —— 于是它不仅挡输入框，连聊天列表的滑动也一起吞了。

**修复**：
- **把输入栏提升为外层 `Box` 的直接子节点，并声明在 Live2D 之后**。声明顺序即层级：
  壁纸 → 消息列表 → Live2D 皮套 → 输入栏。
  （改动幅度较大，用脚本做块搬迁，避免手工复制出错。）
- **消息列表自己留出底部空间**：输入栏不再参与消息 `Column` 的垂直排布，所以给 `LazyColumn`
  加了 `bottom = 输入栏实测高度` 的 `contentPadding`（`onSizeChanged` 实测，回复条/预测条
  出现时高度变化会自动跟随）。
- **触摸穿透**：`Live2DWebView.onTouchEvent` 只在 `ACTION_DOWN` 命中皮套范围时消费，
  否则 `return false` 放行给下层。皮套范围由新增的 JS `window.getModelHitRect()`
  （`model.getBounds()`）提供，Kotlin 侧缓存为 `RectF`。

**穿透的三处保守设计**（避免"修复后皮套点不动"这种更糟的结果）：
- 范围**未知时为 `null`**，此时退回旧行为（全部消费）；
- JS 回传值解析后做**合理性校验**（宽高 > 0 且与视图有交集），不合理则视为取数失败 → 退回全部消费；
- 命中判定按皮套宽度的 6% 外扩（8~48px），留出容错。

**另一个坑**：`contentPadding = PaddingValues(horizontal = …, top = …, bottom = …)` **编译不过** ——
`horizontal/vertical` 与 `start/top/end/bottom` 属于不同重载，不能混用；必须统一写成
`start/top/end/bottom`。

## 十三、本次改动文件

新增：`file/AgentWorkspace.kt`、`plugin/FilePlugin.kt`
修改：`plugin/FilePlugin.kt`(注册)、`AppContainer.kt`、`settings/SettingsManager.kt`、
`ui/settings/AccessibilitySettingsScreen.kt`、`ui/chat/ChatScreen.kt`、
`live2d/Live2DWebView.kt`、`ui/coordinator/Live2DCoordinator.kt`、`AndroidManifest.xml`

## 十四、未覆盖 / 建议下一步

1. **SAF 方式访问任意用户目录**：现在 `public/` 只有一个固定目录（`/sdcard/StardustAgent`）。
   要读写用户自己挑的目录（下载、微信文件夹等），正路是 SAF（`ACTION_OPEN_DOCUMENT_TREE`
   + 持久化 URI 权限），而不是继续加权限。这也是 Google Play 更接受的做法。
2. **MANAGE_EXTERNAL_STORAGE 的上架影响**：该权限在 Google Play 属受限权限，需要申请豁免。
   如果只是自用/侧载，保留即可；若要去 Play，删掉 Manifest 那一行、
   并把设置页的「去授权」按钮一起去掉，功能自动退回两个零权限目录（不影响可用性）。
3. **`file` 与技能库的联动**：目前技能库走 `SkillStore`，文件工具走沙箱，两者不互通。
   可考虑让技能正文也支持 `.md` 文件导入导出。
4. **多模态读图**：`file` 现在明确拒绝读二进制。若要"看图"，应走已有的图片插件把
   图片作为 `imageUrls` 传给多模态模型，而不是用文件工具读字节。

