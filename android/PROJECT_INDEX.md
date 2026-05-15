# 星尘 AI 桌宠 — 项目代码索引

> 版本: 2026-05-15 | 语言: Kotlin | 平台: Android | 包名: com.aicompanion
> 编译 SDK: 34 | 最低 SDK: 23 | 目标 SDK: 34 | AGP: 8.5.0 | Kotlin: 1.9.24

---

## 📁 项目目录结构总览

```
com.aicompanion/
├── CompanionApp.kt              # Application 入口 & 全局异常捕获
│
├── models/                      # 【数据模型层】
│   ├── Models.kt                # 核心数据类定义 (30+ data class / enum)
│   └── EmotionActionMapper.kt   # 情绪/动作映射
│
├── network/                     # 【网络通信层】
│   └── ApiClient.kt             # 后端 API 客户端 (OpenAI/DeepSeek 兼容)
│
├── api/                         # 【API 预设配置】
│   └── ApiProviderPreset.kt     # 各厂商 API 预设模板
│
├── rag/                         # 【RAG 检索增强生成】
│   ├── PersonaRagManager.kt     # 角色人设向量检索
│   ├── RagConfig.kt             # RAG 配置参数
│   ├── RagEmbedder.kt           # TF-IDF 本地嵌入器
│   ├── TextChunker.kt           # 文本分块器
│   └── VectorStore.kt           # 本地向量存储 & 余弦相似度检索
│
├── humanizer/                   # 【人性化输出】
│   └── Humanizer.kt             # AI 回复拟人化处理
│
├── memory/                      # 【记忆系统层】
│   ├── MemoryManager.kt         # 长期记忆管理
│   ├── MemoryPool.kt            # 运行时记忆池 (定时保存 / 自动压缩)
│   ├── ContextManager.kt        # 对话上下文管理 (8 轮窗口)
│   ├── SessionManager.kt        # 会话生命周期 & 跨会话记忆继承
│   └── MemorableMomentsManager.kt  # 难忘时刻情感评分
│
├── action/                      # 【AI 工具调用层】
│   └── AIActionManager.kt       # Tool Calling: 闹钟 / 日程 / 搜索 / 昵称总结
│
├── search/                      # 【网络搜索】
│   └── WebSearchEngine.kt       # Bing / DuckDuckGo 网页搜索
│
├── ui/                          # 【用户界面层】(15 个 Activity + 2 个 Adapter/Manager)
│   ├── ActivationActivity.kt    # 暗号激活页 (启动入口)
│   ├── SplashActivity.kt        # Logo 启动页
│   ├── MainActivity.kt          # 主界面 (聊天 + Live2D + 全业务调度)
│   ├── ChatAdapter.kt           # 聊天消息列表适配器
│   ├── FavoriteManager.kt       # 消息收藏管理器
│   ├── NicknameManager.kt       # AI 昵称管理器 (手动 + 自动发现)
│   ├── SettingsActivity.kt      # 综合设置页
│   ├── ProfileActivity.kt       # 角色信息页 (好感度 + 收藏消息展示)
│   ├── PersonaEditorActivity.kt # 人设编辑器
│   ├── MemoryActivity.kt        # 长期记忆管理页
│   ├── MemoryPoolActivity.kt    # 运行时记忆池展示页
│   ├── DiaryActivity.kt         # 日记页
│   ├── AchievementActivity.kt   # 成就页
│   ├── AlarmActivity.kt         # 闹钟/提醒唤醒页 (全屏锁屏)
│   ├── ModelManagerActivity.kt  # Live2D 模型管理页
│   ├── ModelSettingsActivity.kt # 模型显示参数设置页
│   ├── ModelAdjustActivity.kt   # 模型实时调整页
│   └── WebTestActivity.kt       # WebView 测试页
│
├── overlay/                     # 【悬浮窗层】
│   ├── OverlayWindow.kt         # 悬浮窗创建 & 布局 (集成 Live2DWebView)
│   └── OverlayTouchHandler.kt   # 悬浮窗手势处理
│
├── services/                    # 【后台服务层】
│   ├── OverlayService.kt        # 悬浮窗前台服务
│   ├── BackgroundService.kt     # 后台保活 & 日记检查
│   └── SystemMonitor.kt         # 系统监控 (电量 / 应用 / 网络)
│
├── live2d/                      # 【Live2D 渲染层】
│   ├── Live2DWebView.kt         # WebView 容器 (Cubism SDK for Web)
│   ├── Live2DRenderer.kt        # OpenGL ES 备用渲染器
│   ├── Live2DModel.kt           # 模型数据类
│   └── ModelManager.kt          # 模型下载 & 切换
│
├── interaction/                 # 【主动交互层】
│   └── ProactiveInteractionEngine.kt  # 主动搭话引擎
│
├── character/                   # 【角色管理层】
│   └── CharacterCardManager.kt  # 角色卡片管理 (CRUD / 导入导出)
│
├── affection/                   # 【好感度系统】
│   └── AffectionManager.kt      # 好感度计算 & 触发
│
├── gamify/                      # 【游戏化系统】
│   ├── CheckInManager.kt        # 每日签到
│   ├── AchievementManager.kt    # 成就系统 (20+ 内置成就)
│   └── GrowthManager.kt         # 成长值系统 (5 阶段进化)
│
├── diary/                       # 【日记系统】
│   ├── DiaryManager.kt          # 日记 AI 生成 & 存储
│   └── DiaryEntry.kt            # 日记数据类
│
├── screen/                      # 【屏幕识别层】
│   ├── ScreenRecognitionService.kt  # 无障碍截屏 + OCR
│   ├── AppCategoryClassifier.kt     # 应用分类器
│   └── AutoOperator.kt          # 自动操作 (点击 / 滑动 / 输入)
│
├── wakeup/                      # 【唤醒保活层】
│   ├── WakeUpScheduler.kt       # AlarmManager 定时唤醒
│   └── BootReceiver.kt          # 开机自启
│
├── nlp/                         # 【离线 NLP】
│   └── OfflineNLP.kt            # 离线意图识别 & 兜底回复
│
├── voice/                       # 【语音系统】
│   ├── VoiceManager.kt          # TTS 合成 & 语音输入 (Android Speech)
│   └── OfflineASREngine.kt      # 离线语音识别 (空壳)
│
├── theme/                       # 【主题系统】
│   └── ThemeManager.kt          # 5 套配色方案 + 深色/浅色主题
│
├── settings/                    # 【配置管理层】
│   └── SettingsManager.kt       # SharedPreferences 统一封装
│
└── util/                        # 【工具类】
    └── AppConstants.kt          # 全局常量定义
```

---

## 📦 各模块详细介绍

### 🏗️ 核心入口 — `CompanionApp.kt`

- 全局未捕获异常拦截，Toast 提示
- Application 生命周期入口

---

### 💬 主界面 — `ui/MainActivity.kt`

**全项目最核心的文件**，负责几乎所有业务逻辑的调度：

| 功能 | 方法 / 说明 |
|------|-------------|
| 聊天消息收发 | `sendMessage()` → `sendToLLM()` → 显示回复 |
| 人设信息构建 | `getPersonaInfo()` / `buildPersonaFields()` |
| 工具调用集成 | `AIActionManager`：闹钟 / 日程 / 搜索 / `summarize_nicknames` |
| 系统感知 | 时间 / 电量 / 前台应用关键词检测 → 注入 prompt |
| 上下文管理 | `ContextManager` + `MemoryPool`：最近 8 轮对话 + 记忆池 |
| 好感度系统 | 消息行为评估 → `AffectionManager` 更新 |
| 日记自动触发 | 按配置间隔调用 `DiaryManager.generateDiary()` |
| 主动搭话调度 | `ProactiveInteractionEngine` 定时触发 |
| 电量低提醒 | 前台 / 后台双路径通知 |
| 签到 / 成就 / 难忘时刻 | 消息流中触发评分和解锁 |
| 昵称自动发现 | `NicknameManager` — LLM 通过 `summarize_nicknames` 工具生成 |
| 消息收藏 | `FavoriteManager` — 收藏 / 取消 / 查询 |
| Live2D 初始化 | 表情 / 动作控制，WebView 回调监听 |
| 聊天历史保存/加载 | `saveChatHistory()` / `loadChatHistory()` |
| 引用回复 | `showQuoteBar()` / `hideQuoteBar()` |

---

### 🤖 AI API 客户端 — `network/ApiClient.kt`

OpenAI / DeepSeek 兼容的 HTTP API 封装（OkHttp），**递归 JSON 序列化**确保工具参数正确编码：

| 功能 | 说明 |
|------|------|
| `sendChat()` | 核心聊天接口：注入 persona / 记忆 / 历史 / 系统信息 / 工具定义 |
| `sendChatWithToolLoop()` | 带 Tool Calling 循环的聊天：最多 3 轮工具交互 |
| `getWeather()` | 天气查询 |
| `generatePersona()` | AI 自动创建角色卡 |
| `generateImage()` | AI 图片生成 |
| `generateDiary()` | AI 日记生成 |
| `generateTTS()` | TTS 语音合成 |
| `mapToJson()` / `listToJson()` | **递归嵌套 Map / List 安全序列化为 JSONObject** |

**数据模型** (`Models.kt`):

| 类 | 说明 |
|----|------|
| `ChatMessage` | 聊天消息：id / text / time / isUser / emotion / feedback / isFavorited / reactionEmoji / 引用信息 |
| `ChatResponse` | LLM 响应：content / emotion / action / ttsUrl / toolCalls / errorMessage |
| `ToolDefinition` | 工具定义：name / description / parameters(Map) |
| `ToolCall` | LLM 工具调用：id / name / arguments |
| `Emotion` | 情绪枚举：HAPPY / SAD / ANGRY / SURPRISED / NEUTRAL / TSUNFERE |
| `Action` | 动作枚举：IDLE / TAIL_FLICK / EAR_TWITCH / EYE_BLINK / JUMP / HEAD_TILT |
| `CharacterCard` / `WorldInfo` / `UserPersona` | 角色卡 / 世界观 / 用户档案数据类 |
| `Achievement` / `AchievementProgress` | 成就定义 & 进度 |
| `DailyCardData` / `CheckInRecord` | 每日卡片 & 签到记录 |

`EmotionActionMapper` 负责 LLM 返回文本标签 (`happy/sad/angry/...`) → 枚举值映射。

---

### 🧠 RAG 检索增强生成 — `rag/`

| 文件 | 说明 |
|------|------|
| `PersonaRagManager` | 角色人设文本分块 → 向量化 → 索引；根据用户 query 检索 TopK 最相关人设片段注入 prompt |
| `RagConfig` | 配置：PersonaRAG 开关 / 嵌入模型 / TopK 值 / 分块大小 / 最小相似度阈值 |
| `RagEmbedder` | TF-IDF 本地向量化实现：构建词汇表 → 计算 IDF 权重 → 稀疏向量表示 |
| `TextChunker` | 长文本滑动窗口分块：默认 300 字符 / 重叠 60 字符 |
| `VectorStore` | 本地向量索引存储 (SharedPreferences)：添加 / 搜索 / 余弦相似度 / 持久化 |

---

### 💬 人性化输出 — `humanizer/Humanizer.kt`

- 为 AI 回复添加思考前缀 ("嗯…" / "让我想想…")
- 模拟打字修正 (删除 + 重写)
- 插入颜文字 / 表情符号 (`^_^` / `(>▽<)` / `QwQ`)
- 随机句尾变化 (`呢` / `呀` / `哦`)
- 分批输出 (`HumanizedChunk`) 模拟真实打字节奏

---

### 🧠 记忆系统 — `memory/`

| 文件 | 说明 |
|------|------|
| `MemoryManager` | 长期记忆：自动从对话中提取事实 (AI 辅助) / 手动 CRUD / 分类检索 |
| `MemoryPool` | 运行时记忆池：新会话检测 / 定时保存 / 10,000 token 自动压缩 / 上下文补充 |
| `ContextManager` | 对话上下文：管理最近 8 轮 `ConversationTurn` / 过长文本压缩 |
| `SessionManager` | 会话生命周期：最多保留 20 个历史会话 / 跨会话记忆继承 |
| `MemorableMomentsManager` | 难忘时刻评分：对话情感强度 0-100 评分 / 标记高光时刻 |

---

### 🔧 AI 工具调用 — `action/AIActionManager.kt`

通过 Tool Calling (OpenAI 兼容) 让 LLM 自主调用工具：

| 工具名 | 功能 |
|--------|------|
| `set_alarm` | 设置相对时间闹钟 (N 分钟后) |
| `set_alarm_at_time` | 设置绝对时间闹钟 (HH:MM) |
| `add_schedule` | 添加日程提醒 (日期 + 时间 + 内容) |
| `search_web` | 网页搜索 (`WebSearchEngine`) |
| `get_current_time` | 获取当前系统时间 |
| `summarize_nicknames` | AI 从对话中总结用户昵称 (多称呼) |

所有工具定义通过 `getToolDefinitions()` 返回 `List<ToolDefinition>`，在执行器 `executeTool()` 中分发。工具调用结果通过 `sendChatWithToolLoop()` 的多轮交互返回 LLM。

---

### 🎨 UI 界面 — `ui/`

| 文件 | 功能 |
|------|------|
| `ActivationActivity` | 暗号激活 ("时光机大人宇宙无敌超级厉害") → 已激活则跳转 MainActivity |
| `SplashActivity` | 全屏 Logo 展示 → 延迟跳转 |
| `MainActivity` | **核心**：聊天 + Live2D + 全业务调度 (见上方详述) |
| `ChatAdapter` | RecyclerView：3 种 ViewType (用户 / AI / 输入中) / 渐变背景 / 长按弹出菜单 (收藏 / 点缀 / 删除 / 引用) / Emoji 反应弹窗 |
| `FavoriteManager` | 消息收藏的添加 / 移除 / 查询 (SharedPreferences + JSON) |
| `NicknameManager` | AI 昵称：手动设置 / LLM 自动发现 / 多称呼存储 / Chip 展示 |
| `SettingsActivity` | 综合设置：API / 功能开关 / 偏好 / 日记触发 / 搜索提供商 / 主动搭话频率 |
| `ProfileActivity` | 角色信息：好感度进度 / 难忘时刻 / **收藏消息展示** / 导入导出 |
| `PersonaEditorActivity` | 人设编辑器：名称 / 描述 / 性格 / 说话风格 / 外貌 / 世界观 / AI 发现昵称 Chip |
| `MemoryActivity` | 记忆查看 / 添加 / 删除 / 搜索 |
| `MemoryPoolActivity` | 运行时记忆池：活跃条目 / token 统计 |
| `DiaryActivity` | 日记列表 / 详情弹窗 / 导出 / 导入 |
| `AchievementActivity` | 成就列表：已解锁 / 未解锁 / 分类筛选 |
| `AlarmActivity` | 闹钟/日程唤醒：全屏 + 锁屏可显示 + 响铃 + 振动 |
| `ModelManagerActivity` | 模型下载 / 切换 / 删除 / 导入 ZIP |
| `ModelSettingsActivity` | 纹理质量 / FPS 设置 |
| `ModelAdjustActivity` | SeekBar 缩放 + 触摸拖动偏移 |
| `WebTestActivity` | WebView 功能调试 |

---

### 🪟 悬浮窗 — `overlay/`

- **OverlayWindow**: 系统级悬浮窗 (TYPE_APPLICATION_OVERLAY)
  - Live2D 渲染区 (集成 Live2DWebView)
  - 迷你聊天输入框 / 展开折叠按钮 / 状态指示器
- **OverlayTouchHandler**: 拖拽移动 / 点击 / 双击 / 长按手势

---

### 🔧 后台服务 — `services/`

| 文件 | 说明 |
|------|------|
| `OverlayService` | 前台服务：悬浮窗生命周期管理 + 保活 (集成 VoiceManager) |
| `BackgroundService` | 后台保活：SystemMonitor 启动 / 每日日记检查 / 前台通知 "我在后台陪着你呢~" |
| `SystemMonitor` | 系统监控：电量监听 / 前台应用切换 / 网络状态 |

---

### 🎮 游戏化系统 — `gamify/`

| 文件 | 说明 |
|------|------|
| `CheckInManager` | 每日签到 / 连续签到天数 / 防作弊 |
| `AchievementManager` | 20+ 成就：首次对话 / 话痨达人 / 连续签到 / 好感度里程碑 / 分类筛选 |
| `GrowthManager` | 5 阶段成长：萌芽之种 → 破土新芽 → 翠绿藤蔓 → 含苞待放 → 盛放之花 |

---

### 💕 好感度 — `affection/AffectionManager.kt`

- 消息数量 / 心情 / 互动频率 → 好感度 (0-100)
- 积极 / 消极 / 中性行为评估
- 累计互动天数追踪
- 阈值触发：好感度阶段变化通知

---

### 📔 日记系统 — `diary/`

- **DiaryManager**: AI 自动生成每日总结
  - 触发模式: 手动 / 每小时 / 每 2 小时 / 每 50 条消息 / 每日 22 点
  - 基于聊天记录 AI 生成
- **DiaryEntry**: 数据类 (日期 / 标题 / 内容 / 心情 / 好感度 / 标签)

---

### 🔍 屏幕识别 — `screen/`

- **ScreenRecognitionService**: AccessibilityService 截屏 + OCR 识别
- **AppCategoryClassifier**: 包名分类 (游戏 / 浏览器 / 视频 / 社交 / 工作)
- **AutoOperator**: 辅助功能自动操作 (点击 / 滑动 / 输入)

---

### ⚡ 唤醒保活 — `wakeup/`

- **WakeUpScheduler**: AlarmManager 定时唤醒 + AI 主动问候通知
- **BootReceiver**: BOOT_COMPLETED 广播 → 自动启动后台服务

---

### 🗣️ 语音 — `voice/`

- **VoiceManager**: Android TTS (根据情绪调语调语速) + SpeechRecognizer 语音输入 + MediaPlayer
- **OfflineASREngine**: 离线语音识别 (当前为空壳)

---

### 🌐 搜索 — `search/WebSearchEngine.kt`

- Bing API / DuckDuckGo 网页搜索
- 百度百科词条查询
- 结果摘要提取

---

### 🎭 Live2D — `live2d/`

- **Live2DWebView**: WebView 加载 Cubism SDK for Web + NanoHTTPD 本地服务器
  - JS Bridge 控制表情 / 动作 / 截图
  - 模型下载 / 解压 / 缓存
- **Live2DRenderer**: OpenGL ES 2.0 备用渲染器
- **Live2DModel**: 模型元数据 (id / 名称 / 模型路径 / 纹理路径 / 版本)
- **ModelManager**: 模型 CRUD + 导入自定义模型

---

### 🎨 主题 — `theme/ThemeManager.kt`

- 5 套配色：星尘紫 / 海洋蓝 / 樱花粉 / 森林绿 / 日落橙
- 深色 / 浅色 / 跟随系统

---

### ⚙️ 配置 — `settings/SettingsManager.kt`

SharedPreferences 统一封装：API 配置 / 功能开关 (离线模式 / 搜索 / 屏幕识别 / 签到提醒) / 日记触发模式 / 搜索提供商 / 语言风格 / 主动搭话频率

---

### 📝 常量 — `util/AppConstants.kt`

全局常量：API 默认值 / 超时 / 重试 / 存储路径 / Intent Extra 键 / 通知渠道 ID / 权限请求码

---

### 📡 API 预设 — `api/ApiProviderPreset.kt`

预配置 OpenAI / DeepSeek 等厂商的 Chat URL、模型名、TTS/ASR URL，一键切换。

---

## 🔄 核心数据流

```
用户输入
  ↓
MainActivity.sendMessage()
  ├→ 系统感知注入 (时间 / 电量 / 前台应用)
  ↓
sendToLLM()
  ├→ getPersonaInfo(): 人设 + PersonaRAG 检索 + 昵称 (手动 or 自动发现)
  ├→ buildPersonaFields(): 世界设定 / 关系 / 规则
  ├→ ContextManager: 最近 8 轮对话
  ├→ MemoryPool: 运行时记忆
  ├→ AIActionManager.getToolDefinitions(): 所有工具定义
  ↓
ApiClient.sendChatWithToolLoop()
  ├→ mapToJson(): 递归序列化工具参数 (兼容 DeepSeek)
  ├→ POST → LLM 返回 {content, tool_calls?}
  │
  ├─ 有 tool_calls → executeTool() → 结果回传 LLM (最多 3 轮)
  │   ├→ set_alarm / set_alarm_at_time → AlarmManager
  │   ├→ add_schedule → 日程提醒
  │   ├→ search_web → WebSearchEngine
  │   ├→ get_current_time → 系统时间
  │   └→ summarize_nicknames → NicknameManager 存储多称呼
  │
  └─ 纯文本回复 → Humanizer 人性化处理 → 显示 + TTS + Live2D 动作
  ↓
好感度更新 / 成就检查 / 难忘时刻评分 / 日记触发检查
```

---

## 📊 统计概要

| 类别 | 数量 |
|------|------|
| Kotlin 源文件 | 52 |
| XML 布局文件 | 23 |
| XML Drawable (Shape/Vector) | 36 |
| PNG 图片资源 | 18 |
| Gradle 构建文件 | 8 |
| Assets (JS / 模型 / 纹理) | ~42 |
| Activity | 15 |
| Service / Receiver | 5 |

---

## 📌 关键文件速查

| 要找什么 | 看哪个文件 |
|----------|-----------|
| 修改聊天逻辑 | `ui/MainActivity.kt` — `sendMessage()` / `sendToLLM()` |
| 修改 AI 回复格式 | `network/ApiClient.kt` — `sendChat()` |
| 修改系统 Prompt | `ui/MainActivity.kt` — `getPersonaInfo()` / `buildPersonaFields()` |
| 添加/修改工具 | `action/AIActionManager.kt` |
| 修复工具 HTTP 400 | `network/ApiClient.kt` — `mapToJson()` / `listToJson()` (递归序列化) |
| 修改昵称逻辑 | `ui/NicknameManager.kt` |
| 修改收藏逻辑 | `ui/FavoriteManager.kt` |
| 修改聊天气泡 | `layout/item_message_user.xml` / `item_message_pet.xml` |
| 修改长按菜单 | `layout/popup_chat_action.xml` / `popup_emoji_reaction.xml` |
| 修改人设编辑 | `ui/PersonaEditorActivity.kt` + `layout/activity_persona_editor.xml` |
| 修改好感度规则 | `affection/AffectionManager.kt` |
| 修改签到规则 | `gamify/CheckInManager.kt` |
| 修改成就列表 | `gamify/AchievementManager.kt` |
| 修改日记生成 | `diary/DiaryManager.kt` |
| 修改主动搭话 | `interaction/ProactiveInteractionEngine.kt` |
| 修改记忆系统 | `memory/MemoryManager.kt` / `MemoryPool.kt` / `ContextManager.kt` |
| 修改会话管理 | `memory/SessionManager.kt` |
| 修改 PersonaRAG | `rag/PersonaRagManager.kt` + `rag/VectorStore.kt` |
| 修改人性化输出 | `humanizer/Humanizer.kt` |
| 修改数据模型 | `models/Models.kt` |
| 修改情绪映射 | `models/EmotionActionMapper.kt` |
| 修改悬浮窗 UI | `overlay/OverlayWindow.kt` |
| 修改手势 | `overlay/OverlayTouchHandler.kt` |
| 修改后台行为 | `services/BackgroundService.kt` + `services/SystemMonitor.kt` |
| 修改模型加载 | `live2d/Live2DWebView.kt` |
| 修改模型管理 | `live2d/ModelManager.kt` |
| 修改设置页面 | `ui/SettingsActivity.kt` + `settings/SettingsManager.kt` |
| 修改主题配色 | `theme/ThemeManager.kt` |
| 修改闹钟唤醒 UI | `ui/AlarmActivity.kt` + `layout/activity_alarm.xml` |
| 修改 API 预设 | `api/ApiProviderPreset.kt` |
| 修改全局常量 | `util/AppConstants.kt` |