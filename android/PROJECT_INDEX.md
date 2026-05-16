# 星尘 AI 桌宠 — 项目代码索引

> 版本: 2026-05-16 | 语言: Kotlin | 平台: Android | 包名: com.aicompanion
> 编译 SDK: 34 | 最低 SDK: 23 | 目标 SDK: 34 | AGP: 8.5.0 | Kotlin: 1.9.24

---

## 1. 项目概述

星尘 AI 桌宠是一款 Android 平台的 AI 伴侣应用，核心功能包括：多角色人设聊天、Live2D 虚拟形象、好感度系统、记忆系统、日记系统、朋友圈动态、表情包系统、插件化工具调用、语音交互、屏幕识别等。应用以 `HomeActivity` 为启动入口，用户可创建和管理多个 AI 角色（Persona），每个角色拥有独立的聊天历史和人设配置。

---

## 2. 架构概览

### 启动流程

```
Launcher → HomeActivity (LAUNCHER)
  ├→ 选择已有角色 → MainActivity (persona_id)
  ├→ 新建角色 → PersonaManager.addPersona()
  ├→ 朋友圈 → MomentsActivity
  ├→ 日记 → DiaryActivity
  └→ 设置 → SettingsActivity
```

### 核心调度

- **HomeActivity**: 应用主入口，展示角色列表，管理角色 CRUD，导航至各功能模块
- **MainActivity**: 聊天核心界面，集成 Live2D 渲染、消息收发、工具调用、好感度/成就/日记触发
- **AppContainer**: 全局单例依赖容器，初始化所有 Manager 并注册内置插件

### 人设系统

- **PersonaManager**: 管理多个 Persona 角色，每个角色拥有独立聊天历史（`chat_history_{id}` SharedPreferences）
- **Persona**: 数据类，包含 id / name / prompt / personality / speechStyle / avatarPath / description / isDefault

### 聊天系统

- **ApiClient** → LLM API 调用（OpenAI/DeepSeek 兼容）
- **PluginRegistry** → 插件化工具调用注册与分发
- **ContextManager** + **MemoryPool** → 上下文与记忆管理
- **Humanizer** → 回复拟人化处理

---

## 3. 模块说明

### ui/ — 用户界面层

| 文件 | 功能 |
|------|------|
| `HomeActivity` | **启动入口** (LAUNCHER)：角色列表展示 / 新建编辑删除角色 / 导航至朋友圈/日记/设置 / 主题应用 |
| `MainActivity` | **聊天核心**：消息收发 / Live2D 控制 / 工具调用集成 / 好感度/成就/日记触发 / 系统感知注入 / 主动搭话 |
| `ActivationActivity` | 暗号激活页 |
| `SplashActivity` | Logo 启动页 |
| `ChatAdapter` | 聊天消息 RecyclerView 适配器：3 种 ViewType / 渐变背景 / 长按菜单 / Emoji 反应 |
| `FavoriteManager` | 消息收藏管理（SharedPreferences + JSON） |
| `NicknameManager` | AI 昵称管理：手动设置 / LLM 自动发现 / 多称呼存储 |
| `SettingsActivity` | 综合设置：API / 功能开关 / 偏好 / 日记触发 / 搜索提供商 / 主动搭话频率 |
| `ProfileActivity` | 角色信息：好感度进度 / 难忘时刻 / 收藏消息 / 导入导出 |
| `PersonaEditorActivity` | 人设编辑器：名称 / 描述 / 性格 / 说话风格 / 外貌 / 世界观 |
| `MemoryActivity` | 长期记忆查看 / 添加 / 删除 / 搜索 |
| `MemoryPoolActivity` | 运行时记忆池展示 |
| `DiaryActivity` | 日记列表 / 详情弹窗 / 导出 / 导入 |
| `AchievementActivity` | 成就列表：已解锁 / 未解锁 / 分类筛选 |
| `AlarmActivity` | 闹钟/日程唤醒：全屏 + 锁屏可显示 + 响铃 + 振动 |
| `ModelManagerActivity` | Live2D 模型下载 / 切换 / 删除 / 导入 |
| `ModelSettingsActivity` | 模型显示参数设置 |
| `ModelAdjustActivity` | 模型实时调整（缩放 + 拖动偏移） |
| `WebTestActivity` | WebView 功能调试 |

### persona/ — 角色人设管理

| 文件 | 功能 |
|------|------|
| `PersonaManager` | 角色 CRUD / 活跃角色切换 / 角色索引持久化（JSON 文件） / 独立聊天历史管理 |
| `Persona` | 数据类：id / name / prompt / personality / speechStyle / avatarPath / description / isDefault / createdAt；支持 JSON 序列化 |

### plugin/ — 插件化工具调用

| 文件 | 功能 |
|------|------|
| `ToolPlugin` | 插件接口：name / description / getDefinition() / execute() / isEnabled() / 生命周期回调 |
| `PluginRegistry` | 插件注册中心：register / unregister / getEnabledDefinitions / executePlugin / 变更监听 |
| `BuiltinPlugins` | 内置插件实现（7 个）：AlarmPlugin / AlarmAtTimePlugin / SchedulePlugin / WebSearchPlugin / SearchMemoryPlugin / CurrentTimePlugin / NicknamePlugin / SendStickerPlugin |

**内置插件列表：**

| 插件名 | 功能 |
|--------|------|
| `set_alarm` | 设置相对时间闹钟（N 分钟后） |
| `set_alarm_at_time` | 设置绝对时间闹钟（HH:MM） |
| `add_schedule` | 添加日程提醒（日期 + 时间 + 内容） |
| `search_web` | 网页搜索（WebSearchEngine，受设置开关控制） |
| `search_memory` | 搜索用户记忆池和历史日记 |
| `get_current_time` | 获取当前系统时间 |
| `summarize_nicknames` | AI 从对话中总结用户昵称 |
| `send_sticker` | AI 发送表情包（基于情感关键词匹配） |

### safety/ — 内容安全

> 当前模块尚未实现，预留扩展。

### theme/ — 主题与皮肤系统

| 文件 | 功能 |
|------|------|
| `ThemeManager` | 5 套配色方案（星尘紫 / 海洋蓝 / 樱花粉 / 森林绿 / 日落橙）+ 深色/浅色/跟随系统 |
| `BubbleSkinManager` | 气泡皮肤管理：5 套内置气泡皮肤（默认/毛玻璃/霓虹/糖果/薄荷）+ 5 套头像边框（默认/粉光/金边/赛博/无框）；SharedPreferences 持久化选择；动态应用 GradientDrawable |

**BubbleSkin 数据类：** userBgColor / userGradientColors / userCornerRadius / userStrokeColor / userStrokeWidth / aiBgColor / aiCornerRadius / aiStrokeColor / aiStrokeWidth / aiAlpha

**AvatarFrame 数据类：** strokeColor / strokeWidth / cornerRadius / glowColor / glowRadius

### sticker/ — 表情包系统

| 文件 | 功能 |
|------|------|
| `StickerManager` | 表情包管理：内置表情包（assets 加载 + 预计算嵌入向量）/ 用户自定义表情包 / 向量语义搜索（余弦相似度）/ 关键词搜索 / CRUD / VectorStore 集成 |
| `StickerModel` | 数据类：id / filePath / description / emotion / tags / owner / createdAt / embedding；支持 JSON 序列化 |
| `StickerActivity` | 表情包选择界面：GridView 展示 / 全部/内置/我的 Tab 切换 / 添加自定义表情包（图片选择 + 描述 + 情感 + 标签 + 嵌入向量生成）/ 长按删除 / 选择结果回传 |

### moments/ — 朋友圈动态系统

| 文件 | 功能 |
|------|------|
| `MomentsManager` | 动态管理：CRUD / 评论管理 / AI 自动发动态触发（好感度阈值 70/80/90/100 + 5% 随机触发）/ AI 生成动态内容 / AI 回复评论 |
| `MomentModel` | 数据类：Moment（id / author / content / imagePath / stickerPath / createdAt / comments）+ Comment（id / author / content / createdAt）；支持 JSON 序列化 |
| `MomentsActivity` | 朋友圈界面：RecyclerView 动态流 / 发动态（文字 + 图片 + 表情包）/ 评论交互 / AI 自动回复 / 主题背景 / 删除动态 |

### voice/ — 语音系统

| 文件 | 功能 |
|------|------|
| `VoiceManager` | Android TTS（根据情绪调语调语速）+ SpeechRecognizer 语音输入 + MediaPlayer |
| `OfflineASREngine` | 离线语音识别（当前为空壳） |

### screen/ — 屏幕识别层

| 文件 | 功能 |
|------|------|
| `ScreenRecognitionService` | AccessibilityService 截屏 + OCR 识别 |
| `AppCategoryClassifier` | 包名分类（游戏 / 浏览器 / 视频 / 社交 / 工作） |
| `AutoOperator` | 辅助功能自动操作（点击 / 滑动 / 输入） |

### network/ — 网络通信层

| 文件 | 功能 |
|------|------|
| `ApiClient` | OpenAI/DeepSeek 兼容 HTTP API 封装（OkHttp）：sendChat / sendChatWithToolLoop（最多 3 轮工具交互）/ getWeather / generatePersona / generateImage / generateDiary / generateTTS / getEmbedding / sendSimplePrompt / mapToJson / listToJson（递归 JSON 序列化） |

### action/ — AI 工具调用层

| 文件 | 功能 |
|------|------|
| `AIActionManager` | 工具调用执行器：闹钟设置 / 日程管理 / ReminderReceiver 广播接收；与 PluginRegistry 协同工作 |

### services/ — 后台服务层

| 文件 | 功能 |
|------|------|
| `OverlayService` | 前台服务：悬浮窗生命周期管理 + 保活（集成 VoiceManager） |
| `BackgroundService` | 后台保活：SystemMonitor 启动 / 每日日记检查 / 前台通知 |
| `SystemMonitor` | 系统监控：电量监听 / 前台应用切换 / 网络状态 |

### memory/ — 记忆系统层

| 文件 | 功能 |
|------|------|
| `MemoryManager` | 长期记忆：AI 辅助提取事实 / 手动 CRUD / 分类检索 |
| `MemoryPool` | 运行时记忆池：新会话检测 / 定时保存 / 10,000 token 自动压缩 |
| `ContextManager` | 对话上下文：最近 8 轮 ConversationTurn / 过长文本压缩 |
| `SessionManager` | 会话生命周期：最多 20 个历史会话 / 跨会话记忆继承 |
| `MemorableMomentsManager` | 难忘时刻评分：对话情感强度 0-100 评分 / 标记高光时刻 |

### rag/ — RAG 检索增强生成

| 文件 | 功能 |
|------|------|
| `PersonaRagManager` | 角色人设文本分块 → 向量化 → 索引；根据 query 检索 TopK 最相关人设片段注入 prompt |
| `RagConfig` | 配置：PersonaRAG 开关 / 嵌入模型 / TopK 值 / 分块大小 / 最小相似度阈值 |
| `RagEmbedder` | TF-IDF 本地向量化：构建词汇表 → 计算 IDF 权重 → 稀疏向量表示 |
| `TextChunker` | 滑动窗口分块：默认 300 字符 / 重叠 60 字符 |
| `VectorStore` | 本地向量索引存储（SharedPreferences）：添加 / 搜索 / 余弦相似度 / 持久化 |

### diary/ — 日记系统

| 文件 | 功能 |
|------|------|
| `DiaryManager` | AI 自动生成每日总结：手动 / 每小时 / 每 2 小时 / 每 50 条消息 / 每日 22 点触发 |
| `DiaryEntry` | 日记数据类：日期 / 标题 / 内容 / 心情 / 好感度 / 标签 |

### settings/ — 配置管理层

| 文件 | 功能 |
|------|------|
| `SettingsManager` | SharedPreferences 统一封装：API 配置 / 功能开关 / 日记触发模式 / 搜索提供商 / 语言风格 / 主动搭话频率 |

### models/ — 数据模型层

| 文件 | 功能 |
|------|------|
| `Models.kt` | 核心数据类：ChatMessage / ChatResponse / ToolDefinition / ToolCall / Emotion / Action / CharacterCard / WorldInfo / UserPersona / Achievement / AchievementProgress / DailyCardData / CheckInRecord 等 |
| `EmotionActionMapper.kt` | 情绪/动作映射：LLM 文本标签 → 枚举值 |

### 其他模块

| 目录 | 文件 | 功能 |
|------|------|------|
| `affection/` | `AffectionManager` | 好感度计算 (0-100) / 行为评估 / 累计互动天数 / 阈值触发 |
| `gamify/` | `CheckInManager` / `AchievementManager` / `GrowthManager` | 每日签到 / 20+ 成就系统 / 5 阶段成长 |
| `character/` | `CharacterCardManager` | 角色卡片 CRUD / 导入导出 |
| `interaction/` | `ProactiveInteractionEngine` | 主动搭话引擎 |
| `humanizer/` | `Humanizer` | AI 回复拟人化：思考前缀 / 打字修正 / 颜文字 / 句尾变化 / 分批输出 |
| `live2d/` | `Live2DWebView` / `Live2DRenderer` / `Live2DModel` / `ModelManager` | WebView 渲染 (Cubism SDK for Web) / OpenGL ES 备用 / 模型管理 |
| `overlay/` | `OverlayWindow` / `OverlayTouchHandler` | 系统级悬浮窗 / 手势处理 |
| `search/` | `WebSearchEngine` | Bing API / DuckDuckGo 搜索 / 百度百科查询 |
| `nlp/` | `OfflineNLP` | 离线意图识别 & 兜底回复 |
| `wakeup/` | `WakeUpScheduler` / `BootReceiver` | AlarmManager 定时唤醒 / 开机自启 |
| `api/` | `ApiProviderPreset` | 各厂商 API 预设模板 |
| `util/` | `AppConstants` / `AppLogger` | 全局常量 / 统一日志（内存缓冲 + Logcat 双写） |

### 全局组件

| 文件 | 功能 |
|------|------|
| `CompanionApp.kt` | Application 入口 & 全局异常捕获 |
| `AppContainer.kt` | 全局依赖容器：初始化所有 Manager / 构建 ApiClient / 注册内置插件 / 设置回调 |

---

## 4. 数据存储说明

### SharedPreferences 键值

| SharedPreferences 名 | 键 | 说明 |
|----------------------|-----|------|
| `app_prefs` | `active_persona_id` | 当前活跃角色 ID |
| `app_prefs` | `ai_name` | AI 名称（兜底） |
| `app_prefs` | `chat_background` | 聊天背景图路径 |
| `chat_history_{personaId}` | `messages` | 各角色独立聊天历史（JSON 数组） |
| `persona_data` | `persona_name` / `persona_desc` / `persona_personality` / `persona_speech_style` | 角色人设信息 |
| `bubble_skin_prefs` | `active_bubble_skin` | 当前气泡皮肤 ID |
| `bubble_skin_prefs` | `active_ai_frame` | 当前 AI 头像边框 ID |
| `bubble_skin_prefs` | `active_user_frame` | 当前用户头像边框 ID |
| SettingsManager 相关 | API URL / Key / Model / 功能开关等 | 应用配置 |

### 文件存储

| 路径 | 说明 |
|------|------|
| `filesDir/personas/personas_index.json` | 角色索引文件 |
| `filesDir/personas/{personaId}/` | 各角色独立目录 |
| `filesDir/stickers/sticker_index.json` | 用户自定义表情包索引 |
| `filesDir/stickers/builtin_stickers/` | 内置表情包缓存 |
| `filesDir/moments/moments_index.json` | 朋友圈动态索引 |
| `filesDir/moments/images/` | 动态图片存储 |

---

## 5. 权限说明

| 权限 | 用途 |
|------|------|
| `INTERNET` | LLM API 调用 / 网页搜索 / 模型下载 |
| `SYSTEM_ALERT_WINDOW` | 悬浮窗显示 |
| `RECORD_AUDIO` | 语音输入 |
| `ACCESS_NETWORK_STATE` | 网络状态监控 |
| `FOREGROUND_SERVICE` | 前台服务（悬浮窗 / 后台保活） |
| `FOREGROUND_SERVICE_SPECIAL_USE` | 前台服务特殊用途声明 |
| `READ_EXTERNAL_STORAGE` | 读取外部存储（模型/图片导入） |
| `WRITE_EXTERNAL_STORAGE` | 写入外部存储 |
| `RECEIVE_BOOT_COMPLETED` | 开机自启 |
| `POST_NOTIFICATIONS` | 发送通知（Android 13+） |
| `SCHEDULE_EXACT_ALARM` | 精确闹钟调度 |
| `USE_EXACT_ALARM` | 精确闹钟权限声明 |
| `USE_FULL_SCREEN_INTENT` | 全屏闹钟唤醒 |
| `VIBRATE` | 闹钟振动 |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | 请求忽略电池优化（保活） |

---

## 6. 构建说明

### 环境要求

- Android Studio Hedgehog+
- JDK 17
- Android SDK 34
- Kotlin 1.9.24
- AGP 8.5.0

### 构建命令

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建
./gradlew assembleRelease

# 清理
./gradlew clean
```

### 关键构建配置

- `compileSdk`: 34
- `minSdk`: 23
- `targetSdk`: 34
- `namespace`: `com.aicompanion`
- `usesCleartextTraffic`: false（强制 HTTPS）
- `networkSecurityConfig`: `@xml/network_security_config`

### 组件注册

| 组件类型 | 数量 | 说明 |
|----------|------|------|
| Activity | 17 | HomeActivity (LAUNCHER) + 16 个功能页 |
| Service | 3 | OverlayService / BackgroundService / ScreenRecognitionService |
| BroadcastReceiver | 3 | WakeUpReceiver / BootReceiver / ReminderReceiver |
| ContentProvider | 1 | FileProvider（文件分享） |

---

## 📌 关键文件速查

| 要找什么 | 看哪个文件 |
|----------|-----------|
| 修改启动入口/角色列表 | `ui/HomeActivity.kt` |
| 修改聊天逻辑 | `ui/MainActivity.kt` — `sendMessage()` / `sendToLLM()` |
| 修改 AI 回复格式 | `network/ApiClient.kt` — `sendChat()` |
| 修改系统 Prompt | `ui/MainActivity.kt` — `getPersonaInfo()` / `buildPersonaFields()` |
| 添加/修改工具插件 | `plugin/BuiltinPlugins.kt` + `plugin/ToolPlugin.kt` |
| 修改插件注册逻辑 | `plugin/PluginRegistry.kt` |
| 修复工具 HTTP 400 | `network/ApiClient.kt` — `mapToJson()` / `listToJson()` |
| 修改角色管理 | `persona/PersonaManager.kt` |
| 修改昵称逻辑 | `ui/NicknameManager.kt` |
| 修改收藏逻辑 | `ui/FavoriteManager.kt` |
| 修改表情包 | `sticker/StickerManager.kt` + `sticker/StickerActivity.kt` |
| 修改朋友圈 | `moments/MomentsManager.kt` + `moments/MomentsActivity.kt` |
| 修改气泡皮肤 | `theme/BubbleSkinManager.kt` |
| 修改主题配色 | `theme/ThemeManager.kt` |
| 修改全局依赖初始化 | `AppContainer.kt` |
| 修改聊天气泡 | `layout/item_message_user.xml` / `item_message_pet.xml` |
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
| 修改闹钟唤醒 UI | `ui/AlarmActivity.kt` + `layout/activity_alarm.xml` |
| 修改 API 预设 | `api/ApiProviderPreset.kt` |
| 修改全局常量 | `util/AppConstants.kt` |
| 查看运行日志 | `util/AppLogger.kt` — `getRecentLogs()` |
