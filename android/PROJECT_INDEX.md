# 星尘 AI 桌宠 - 项目代码索引

> 版本: 2026-05-14 | 语言: Kotlin | 平台: Android
> 包名: com.aicompanion

---

## 📁 项目目录结构总览

```
com.aicompanion/
├── CompanionApp.kt              # Application 入口
├── CompanionApp.kt              # 全局应用初始化 & 异常捕获
│
├── models/                      # 【数据模型层】
│   ├── Models.kt                # 核心数据类定义
│   └── EmotionActionMapper.kt   # 情绪/动作映射
│
├── network/                     # 【网络通信层】
│   ├── ApiClient.kt             # 后端 API 客户端 (聊天/画像/天气)
│
├── api/                         # 【API 预设配置】
│   └── ApiProviderPreset.kt     # 各厂商 API 预设模板
│
├── search/                      # 【网络搜索】
│   └── WebSearchEngine.kt       # 网页搜索 & 百度百科
│
├── ui/                          # 【用户界面层】(10个Activity + Adapter)
│   ├── SplashActivity.kt        # 启动页
│   ├── MainActivity.kt          # 主界面 (聊天+Live2D+状态管理)
│   ├── ChatAdapter.kt           # 聊天消息列表适配器
│   ├── SettingsActivity.kt      # 设置页
│   ├── ProfileActivity.kt       # 角色信息页
│   ├── PersonaEditorActivity.kt # 人设编辑器
│   ├── MemoryActivity.kt        # 记忆管理页
│   ├── DiaryActivity.kt         # 日记页
│   ├── AchievementActivity.kt   # 成就页
│   ├── ModelManagerActivity.kt  # 模型管理页
│   ├── ModelSettingsActivity.kt # 模型设置页
│   ├── ModelAdjustActivity.kt   # 模型调整页
│   ├── ActivationActivity.kt    # 激活页
│   └── WebTestActivity.kt       # Web 测试页
│
├── overlay/                     # 【悬浮窗层】
│   ├── OverlayWindow.kt         # 悬浮窗创建 & 布局
│   └── OverlayTouchHandler.kt   # 悬浮窗手势处理
│
├── services/                    # 【后台服务层】
│   ├── OverlayService.kt        # 悬浮窗前台服务
│   ├── BackgroundService.kt     # 后台保活 & 日记检查
│   └── SystemMonitor.kt         # 系统监控 (电量/应用/网络)
│
├── live2d/                      # 【Live2D 渲染层】
│   ├── Live2DWebView.kt         # WebView 容器 (核心交互枢纽)
│   ├── Live2DRenderer.kt        # OpenGL ES 渲染器
│   ├── Live2DModel.kt           # 模型数据类
│   └── ModelManager.kt          # 模型下载 & 切换
│
├── action/                      # 【AI 动作执行层】
│   └── AIActionManager.kt       # 闹钟 / 日程 / 搜索 动作管理
│
├── interaction/                 # 【主动交互层】
│   └── ProactiveInteractionEngine.kt  # 主动搭话引擎
│
├── memory/                      # 【记忆系统层】
│   ├── MemoryManager.kt         # 长期记忆管理
│   └── MemorableMomentsManager.kt     # 难忘时刻评分
│
├── character/                   # 【角色管理层】
│   └── CharacterCardManager.kt  # 角色卡片管理
│
├── affection/                   # 【好感度系统】
│   └── AffectionManager.kt      # 好感度计算 & 触发
│
├── gamify/                      # 【游戏化系统】
│   ├── CheckInManager.kt        # 每日签到
│   ├── AchievementManager.kt    # 成就系统
│   └── GrowthManager.kt         # 成长值系统
│
├── diary/                       # 【日记系统】
│   ├── DiaryManager.kt          # 日记生成 & 存储
│   └── DiaryEntry.kt            # 日记数据类
│
├── screen/                      # 【屏幕识别层】
│   ├── ScreenRecognitionService.kt    # 屏幕内容识别 (OCR)
│   ├── AppCategoryClassifier.kt       # 应用分类器
│   └── AutoOperator.kt          # 自动操作 (点击/滑动/输入)
│
├── wakeup/                      # 【唤醒保活层】
│   ├── WakeUpScheduler.kt       # 定时唤醒调度
│   └── BootReceiver.kt          # 开机自启
│
├── nlp/                         # 【离线 NLP】
│   └── OfflineNLP.kt            # 离线意图识别
│
├── voice/                       # 【语音系统】
│   ├── VoiceManager.kt          # TTS 语音合成
│   └── OfflineASREngine.kt      # 离线语音识别
│
├── theme/                       # 【主题系统】
│   └── ThemeManager.kt          # 主题 & 配色管理
│
├── settings/                    # 【配置管理层】
│   └── SettingsManager.kt       # SharedPreferences 封装
│
└── util/                        # 【工具类】
    └── AppConstants.kt          # 全局常量
```

---

## 📦 各模块详细介绍

### 🏗️ 核心入口 (`CompanionApp.kt`)
- **全局异常捕获**: 拦截未处理的崩溃, Toast 提示用户
- **Application 生命周期**: 作为整个 App 的入口点

### 💬 主界面 (`ui/MainActivity.kt`)
**最核心的文件**, 负责几乎所有业务逻辑的调度:
- 聊天消息发送/接收 (sendMessage → sendToLLM → 显示)
- 系统感知 (时间/电量关键词检测 → 注入系统信息)
- 上下文记忆 (最近 10 条消息作为历史发送给 LLM)
- 闹钟/日程设置 (LLM 回复后本地执行)
- 搜索功能 (关键词检测 → 网页搜索)
- 好感度系统 (消息行为评估 → 好感度更新)
- 日记定时触发 (按配置的间隔自动生成日记)
- 主动搭话调度 (ProactiveEngine 定时触发)
- 电量低提醒 (前台/后台双路径)
- 签到 / 成就 / 难忘时刻评分触发
- 用户心情选择 & 表情映射
- Live2D 初始化 & 状态监听
- 欢迎消息 & 新手引导

### 🤖 AI API 客户端 (`network/ApiClient.kt`)
- **sendChat()**: 核心聊天接口, 构建 OpenAI 兼容的请求
  - 注入 persona / 记忆 / 历史消息 / 系统信息 / 用户心情
  - 解析 LLM 返回的 emotion / action 标签
  - 支持 TTS 语音 URL 返回
- **getWeather()**: 天气查询
- **generatePersona()**: AI 自动创建角色卡
- **generateImage()**: AI 图片生成

### 🎭 情绪 & 动作 (`models/`)
- **Models.kt**: 定义所有核心数据类
  - `ChatMessage` - 聊天消息
  - `PersonaInfo` - 角色信息
  - `AffectionData` - 好感度数据
  - `Achievement` / `AchievementProgress` - 成就
  - `DiaryTriggerMode` - 日记触发模式枚举
  - `Emotion` / `Action` - 情绪和动作枚举
- **EmotionActionMapper.kt**: LLM 返回的 emotion/action 文本映射为枚举值

### 🎨 UI 界面 (`ui/`)
| 文件 | 功能 |
|------|------|
| `SplashActivity` | 启动页, 显示 Logo 后跳转 |
| `ChatAdapter` | 聊天列表适配器, 用户/AI 消息不同布局, 支持心情头像 |
| `SettingsActivity` | API 配置 / 功能开关 / 偏好设置 |
| `ProfileActivity` | 查看/编辑角色信息, 好感度展示 |
| `PersonaEditorActivity` | 人设 Prompt 编辑器 |
| `MemoryActivity` | 长期记忆列表查看/删除 |
| `DiaryActivity` | 日记列表, 按日期展示 |
| `AchievementActivity` | 成就列表, 进度展示 |
| `ModelManagerActivity` | Live2D 模型下载/切换/删除 |
| `ModelSettingsActivity` | 模型显示参数设置 |
| `ModelAdjustActivity` | 模型位置/大小实时调整 |
| `ActivationActivity` | 激活/验证页面 |
| `WebTestActivity` | WebView 测试页面 |

### 🪟 悬浮窗 (`overlay/`)
- **OverlayWindow.kt**: 创建系统级悬浮窗 (TYPE_APPLICATION_OVERLAY), 包含:
  - Live2D 渲染区域
  - 迷你聊天输入框
  - 展开/折叠按钮
  - 状态指示器
- **OverlayTouchHandler.kt**: 处理悬浮窗拖拽、点击、双击等手势

### 🔧 后台服务 (`services/`)
- **OverlayService.kt**: 前台服务, 管理悬浮窗的创建/销毁/保活
- **BackgroundService.kt**: 后台服务, 负责:
  - 系统监控启动 (电量低通知)
  - 每日日记自动生成检查
  - 前台通知 ("我在后台陪着你呢~")
- **SystemMonitor.kt**: 持续监控系统状态:
  - 电量变化监听 (低电量回调)
  - 前台应用变化监听
  - 网络连接状态监听

### 🎮 游戏化系统 (`gamify/`)
- **CheckInManager.kt**: 每日签到, 连续签到奖励
- **AchievementManager.kt**: 成就进度追踪 & 解锁
- **GrowthManager.kt**: 成长值 / 等级计算

### 💕 好感度系统 (`affection/AffectionManager.kt`)
- 根据用户消息数量、心情、互动频率计算好感度
- 行为评估: 积极/消极/中性消息对好感度的影响
- 阈值触发: 不同好感度阶段的 AI 行为变化

### 🧠 记忆系统 (`memory/`)
- **MemoryManager.kt**: 长期记忆的存储/检索/管理
  - 自动从对话中提取事实
  - 支持手动添加/删除记忆
- **MemorableMomentsManager.kt**: 难忘时刻评分
  - 每 20 条消息触发一次评分
  - 标记特别有意义的对话时刻

### ⏰ AI 动作执行 (`action/AIActionManager.kt`)
- **闹钟**: 关键词检测 → 解析时间 → 设置 Android AlarmManager
- **日程**: 关键词检测 → 解析日期时间 → 设置日程提醒
- **搜索**: 关键词检测 → 提取搜索词 → 返回搜索结果
- **通知**: 通过 NotificationManager 发送系统通知

### 🤖 主动交互 (`interaction/ProactiveInteractionEngine.kt`)
- 根据时间、用户活跃度、好感度决定何时主动搭话
- 不同频率策略 (低/中/高)
- 上下文感知的主动消息生成

### 🎭 角色管理 (`character/CharacterCardManager.kt`)
- 角色卡的创建/加载/保存
- 角色信息的序列化/反序列化

### 📔 日记系统 (`diary/`)
- **DiaryManager.kt**: 自动生成日记
  - 根据聊天记录 AI 生成每日总结
  - 支持多种触发模式 (手动/每小时/每2小时/每50条消息/每日22点)
- **DiaryEntry.kt**: 日记数据类 (日期/内容/心情)

### 🔍 屏幕识别 (`screen/`)
- **ScreenRecognitionService.kt**: 截屏 + OCR 识别屏幕内容
- **AppCategoryClassifier.kt**: 根据应用包名分类 (社交/游戏/学习等)
- **AutoOperator.kt**: 辅助功能自动操作 (点击/滑动/输入文本)

### ⚡ 唤醒保活 (`wakeup/`)
- **WakeUpScheduler.kt**: WorkManager 定时唤醒后台服务
- **BootReceiver.kt**: 开机广播, 自动启动服务

### 🗣️ 语音系统 (`voice/`)
- **VoiceManager.kt**: TTS 语音合成播放, 支持情绪语调变化
- **OfflineASREngine.kt**: 离线语音识别 (语音输入)

### 🌐 网络搜索 (`search/WebSearchEngine.kt`)
- 网页搜索 (百度/Google 等)
- 百度百科词条查询
- 结果摘要提取

### 📡 API 预设 (`api/ApiProviderPreset.kt`)
- 预配置各厂商 API 地址/模型
- 一键切换不同 AI 提供商

### 🎭 Live2D 渲染 (`live2d/`)
- **Live2DWebView.kt**: 核心组件, 基于 WebView 的 Live2D 容器
  - 加载 Cubism SDK for Web
  - JS 接口: 表情控制/动作触发/日志获取
  - 模型下载/解压/缓存
  - 屏幕截图 & 压缩
- **Live2DRenderer.kt**: OpenGL ES 2.0 渲染器 (备用方案)
- **Live2DModel.kt**: 模型元数据 (名称/版本/文件路径)
- **ModelManager.kt**: 模型下载/切换/删除/列表管理

### 🎨 主题系统 (`theme/ThemeManager.kt`)
- 深色/浅色/跟随系统主题
- 自定义配色方案

### ⚙️ 配置管理 (`settings/SettingsManager.kt`)
- SharedPreferences 统一封装
- API 配置 / 功能开关 / 偏好设置读写

### 📝 工具类 (`util/AppConstants.kt`)
- 全局常量定义 (API 默认值/阈值/配置键名等)

---

## 🔄 核心数据流

```
用户输入消息
  ↓
MainActivity.sendMessage()
  ↓
关键词检测 (闹钟/日程/搜索)
  ├→ 搜索 → 直接返回结果 (不走LLM)
  └→ 其他 → sendToLLM()
       ↓
    buildSystemContext()  ← 注入时间/电量/闹钟意图
       ↓
    ApiClient.sendChat()  ← 附带历史消息 + 记忆 + 心情
       ↓
    LLM 返回 {text, emotion, action, audioUrl}
       ↓
    显示回复 → TTS 播放 → Live2D 动作
       ↓
    如果是闹钟请求 → 本地设置 AlarmManager
    如果是日程请求 → 本地设置日程提醒
```

---

## 📌 关键文件速查

| 要找什么 | 看哪个文件 |
|---------|-----------|
| 修改聊天逻辑 | `ui/MainActivity.kt` 的 sendMessage() 和 sendToLLM() |
| 修改 AI 回复格式 | `network/ApiClient.kt` 的 sendChat() |
| 添加新功能关键词 | `action/AIActionManager.kt` |
| 修改好感度规则 | `affection/AffectionManager.kt` |
| 修改签到规则 | `gamify/CheckInManager.kt` |
| 修改成就列表 | `gamify/AchievementManager.kt` |
| 修改日记生成 | `diary/DiaryManager.kt` |
| 修改主动搭话 | `interaction/ProactiveInteractionEngine.kt` |
| 修改悬浮窗 UI | `overlay/OverlayWindow.kt` |
| 修改模型加载 | `live2d/Live2DWebView.kt` |
| 修改设置页面 | `ui/SettingsActivity.kt` + `settings/SettingsManager.kt` |
| 修改数据模型 | `models/Models.kt` |
| 修改后台行为 | `services/BackgroundService.kt` + `services/SystemMonitor.kt` |
