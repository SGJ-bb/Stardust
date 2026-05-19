# 星尘 AI 桌宠 — 项目代码索引

> 版本: 2026-05-18 | 语言: Kotlin | 平台: Android | 包名: com.aicompanion
> 编译 SDK: 34 | 最低 SDK: 23 | 目标 SDK: 34 | AGP: 8.5.0 | Kotlin: 1.9.24

---

## 1. 项目概述

星尘 AI 桌宠是一款 Android 平台的 AI 伴侣应用，核心功能包括：多角色人设聊天、群聊系统、Live2D 虚拟形象、好感度系统、记忆系统、日记系统、朋友圈动态、虚拟世界推演、表情包系统、插件化工具调用、AI生图、语音交互、屏幕识别等。应用以 `HomeActivity` 为启动入口，用户可创建和管理多个 AI 角色（Persona），每个角色拥有独立的聊天历史和人设配置。支持群聊模式下多AI角色互动，每个群聊搭配独立的虚拟世界。

---

## 2. 架构概览

### 启动流程

```
Launcher → HomeActivity (LAUNCHER)
  ├→ 检查激活码 → ActivationActivity (未激活时)
  ├→ 选择已有角色 → MainActivity (persona_id)
  ├→ 新建角色 → BottomSheet + PersonaManager.addPersona()
  ├→ 群聊 → GroupChatListActivity → GroupChatActivity
  ├→ 朋友圈 → MomentsActivity
  ├→ 日记 → DiaryActivity
  └→ 设置 → SettingsActivity
```

### 核心调度

- **HomeActivity**: 应用主入口，展示角色列表，管理角色 CRUD，导航至各功能模块
- **MainActivity**: 单聊核心界面，集成 Live2D 渲染、消息收发、工具调用、好感度/成就/日记触发
- **GroupChatActivity**: 群聊核心界面，多AI角色互动、@提及链式回复、记忆池/日记触发、虚拟世界入口
- **AppContainer**: 全局单例依赖容器，初始化所有 Manager 并注册内置插件

### 人设系统

- **PersonaManager**: 管理多个 Persona 角色，每个角色拥有独立聊天历史（`chat_history_{id}` SharedPreferences）
- **Persona**: 数据类，包含 id / name / prompt / personality / speechStyle / avatarPath / description / isDefault

### 聊天系统

- **ApiClient** → LLM API 调用（OpenAI/DeepSeek 兼容），支持工具调用循环
- **PluginRegistry** → 插件化工具调用注册与分发
- **ContextManager** + **MemoryPool** → 上下文与记忆管理（记忆池每10轮consolidate）
- **Humanizer** → 回复拟人化处理

### 群聊系统

- **GroupChatManager** → 群聊 CRUD / 成员管理 / 消息持久化
- **GroupChat** → 数据类：id / name / memberPersonaIds / speakMode / lastMessagePreview
- **发言模式** → auto（全员）/ ai_judge（AI判定）/ manual（手动选择）
- **@互动** → AI自主@其他角色触发链式回复，最大深度3轮
- **好感度集成** → 群聊提示词中包含角色对其他成员的好感度和态度

### 虚拟世界系统

- **VirtualWorldManager** → 每群聊独立世界（worldId = groupId），SharedPreferences隔离存储
- **WorldConfig** → 世界背景/规则/角色关系/初始场景/叙事风格 + AI自动生成
- **WorldState** → 时间/天气/季节/场景/角色位置/状态
- **StoryEvent** → 剧情事件记录，支持AI生图

---

## 3. 模块说明

### ui/ — 用户界面层

| 文件 | 功能 |
|------|------|
| `HomeActivity` | **启动入口** (LAUNCHER)：角色列表展示 / 新建编辑删除角色（BottomSheet）/ 导航至群聊/朋友圈/日记/设置 / 主题应用 / 激活码检查 |
| `MainActivity` | **单聊核心**：消息收发 / Live2D 控制 / 工具调用集成 / 好感度/成就/日记触发 / 系统感知注入 / 主动搭话 / 图片上传 / 虚拟世界定时推演 |
| `SettingsActivity` | 综合设置：API / 功能开关 / 偏好 / 日记触发 / 搜索提供商 / 主动搭话频率 / 图片生成API配置 |
| `VirtualWorldActivity` | 虚拟世界界面：世界观编辑（BottomSheet + AI自动生成）/ 推演控制 / 剧情事件流 / 图片上传 / 入场动画 |
| `ActivationActivity` | 暗号激活页 |
| `SplashActivity` | Logo 启动页 |
| `ChatAdapter` | 聊天消息 RecyclerView 适配器：3 种 ViewType / 渐变背景 / 长按菜单 / Emoji 反应 |
| `FavoriteManager` | 消息收藏管理（SharedPreferences + JSON） |
| `NicknameManager` | AI 昵称管理：手动设置 / LLM 自动发现 / 多称呼存储 |
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

### groupchat/ — 群聊系统

| 文件 | 功能 |
|------|------|
| `GroupChatListActivity` | 群聊列表：创建群聊（3步BottomSheet：名称→选成员→确认）/ 删除群聊 / 空状态提示 / 入场动画 |
| `GroupChatActivity` | 群聊核心：多AI角色互动 / @提及链式回复（depth≤3）/ 发言模式切换 / 记忆池查看🧠 / 拉新人 / 虚拟世界入口 / 图片上传 / 好感度集成 / 记忆池+日记触发 |
| `GroupChatManager` | 群聊数据持久化：CRUD / 成员管理 / 消息追加（SharedPreferences + JSON） |
| `GroupChat` | 数据类：id / name / memberPersonaIds / speakMode / lastMessagePreview / createdAt |
| `GroupMessage` | 数据类：senderPersonaId / senderName / text / time / isUser / emotion |

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
| `BuiltinPlugins` | 内置插件实现（8 个）：AlarmPlugin / AlarmAtTimePlugin / SchedulePlugin / WebSearchPlugin / SearchMemoryPlugin / CurrentTimePlugin / NicknamePlugin / SendStickerPlugin / GenerateImagePlugin |

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
| `generate_image` | AI 自主决定生图（prompt + 可选style），调用 VirtualWorldManager |

### virtualworld/ — 虚拟世界系统

| 文件 | 功能 |
|------|------|
| `VirtualWorldManager` | 世界管理：每群聊独立世界（worldId隔离）/ 世界观配置 / 状态推演 / 图片生成（全局API配置）/ AI自动生成世界观 / 故事事件管理 |
| `WorldConfig` | 世界配置：worldBackground / worldRules / worldRelations / worldScene / worldStyle / memberPersonaIds / imageApiUrl / imageApiKey / imageModel |
| `WorldState` | 世界状态：time / weather / season / scene / characterLocations / characterStatuses |
| `StoryEvent` | 剧情事件：id / tick / speaker / text / type / imageUrl / timestamp |
| `VirtualWorldModels` | 辅助模型：ImageGenerationRequest 等 |

### theme/ — 主题与皮肤系统

| 文件 | 功能 |
|------|------|
| `ThemeManager` | 5 套配色方案（星尘紫 / 海洋蓝 / 樱花粉 / 森林绿 / 日落橙）+ 深色/浅色/跟随系统 |
| `BubbleSkinManager` | 气泡皮肤管理：5 套内置气泡皮肤 + 5 套头像边框 + 图片气泡/图片头像框覆盖层；SharedPreferences 持久化选择 |

### sticker/ — 表情包系统

| 文件 | 功能 |
|------|------|
| `StickerManager` | 表情包管理：内置表情包 / 用户自定义表情包 / 向量语义搜索 / 关键词搜索 / CRUD / VectorStore 集成 |
| `StickerModel` | 数据类：id / filePath / description / emotion / tags / owner / createdAt / embedding |
| `StickerActivity` | 表情包选择界面：GridView / Tab切换 / 添加自定义 / 长按删除 |

### moments/ — 朋友圈动态系统

| 文件 | 功能 |
|------|------|
| `MomentsManager` | 动态管理：CRUD / 评论管理 / AI 自动发动态触发 / AI 生成动态内容 / AI 回复评论 |
| `MomentModel` | 数据类：Moment + Comment；支持 JSON 序列化 |
| `MomentsActivity` | 朋友圈界面：动态流 / 发动态 / 评论交互 / AI 自动回复 |

### voice/ — 语音系统

| 文件 | 功能 |
|------|------|
| `VoiceManager` | Android TTS + SpeechRecognizer 语音输入 + MediaPlayer |
| `OfflineASREngine` | 离线语音识别（空壳预留） |

### screen/ — 屏幕识别层

| 文件 | 功能 |
|------|------|
| `ScreenRecognitionService` | AccessibilityService 截屏 + OCR 识别 |
| `AppCategoryClassifier` | 包名分类（游戏 / 浏览器 / 视频 / 社交 / 工作） |
| `AutoOperator` | 辅助功能自动操作（点击 / 滑动 / 输入） |

### network/ — 网络通信层

| 文件 | 功能 |
|------|------|
| `ApiClient` | OpenAI/DeepSeek 兼容 HTTP API 封装（OkHttp）：sendChat / sendChatWithToolLoop / generateImage / generateDiary / sendSimplePrompt / 日志脱敏 / Response .use() 安全关闭 |

### action/ — AI 工具调用层

| 文件 | 功能 |
|------|------|
| `AIActionManager` | 工具调用执行器：闹钟设置 / 日程管理 / ReminderReceiver 广播接收 |

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
| `MemoryPool` | 运行时记忆池：新会话检测 / 定时保存 / 10,000 token 自动压缩 / 每10轮consolidate |
| `ContextManager` | 对话上下文：最近 8 轮 ConversationTurn / 记忆池上下文构建(getContextBlock) / evaluateAndUpdateMemory（提取+consolidate） |
| `SessionManager` | 会话生命周期：最多 20 个历史会话 / 跨会话记忆继承 |
| `MemorableMomentsManager` | 难忘时刻评分：对话情感强度 0-100 评分 / 标记高光时刻 |

### rag/ — RAG 检索增强生成

| 文件 | 功能 |
|------|------|
| `PersonaRagManager` | 角色人设文本分块 → 向量化 → 索引；根据 query 检索 TopK 最相关人设片段注入 prompt |
| `RagConfig` | 配置：PersonaRAG 开关 / 嵌入模型 / TopK 值 / 分块大小 / 最小相似度阈值 |
| `RagEmbedder` | TF-IDF 本地向量化 |
| `TextChunker` | 滑动窗口分块 |
| `VectorStore` | 本地向量索引存储（SharedPreferences） |

### diary/ — 日记系统

| 文件 | 功能 |
|------|------|
| `DiaryManager` | AI 自动生成每日总结：手动 / 每小时 / 每 2 小时 / 每 50 条消息 / 每日 22 点触发 |
| `DiaryEntry` | 日记数据类：日期 / 标题 / 内容 / 心情 / 好感度 / 标签 |

### settings/ — 配置管理层

| 文件 | 功能 |
|------|------|
| `SettingsManager` | SharedPreferences 统一封装：API 配置（EncryptedSharedPreferences加密存储）/ 功能开关 / 日记触发模式 / 搜索提供商 / 语言风格 / 主动搭话频率 |

### prompt/ — 提示词构建层

| 文件 | 功能 |
|------|------|
| `PromptBuilder` | 统一提示词构建：buildIdentity / buildGroupChatPrompt（含好感度+@互动规则）/ buildAutoWorldLorePrompt / buildMemoryConsolidatePrompt / buildDiaryPrompt 等 |

### affection/ — 好感度系统

| 文件 | 功能 |
|------|------|
| `AffectionManager` | 好感度计算 (0-100) / 行为评估 / 累计互动天数 / 阈值触发 |

### models/ — 数据模型层

| 文件 | 功能 |
|------|------|
| `Models.kt` | 核心数据类：ChatMessage / ChatResponse / ToolDefinition / ToolCall / Emotion / Action / CharacterCard / WorldInfo / UserPersona / Achievement / AchievementProgress / DailyCardData / CheckInRecord 等 |
| `EmotionActionMapper.kt` | 情绪/动作映射：LLM 文本标签 → 枚举值 |

### anim/ — 动画工具层

| 文件 | 功能 |
|------|------|
| `AnimeUtils` | 统一动画工具：pulse / springScale / staggerSlideIn / slideInFromBottom / fadeInScale / bounceIn / setupTouchScale |

### 其他模块

| 目录 | 文件 | 功能 |
|------|------|------|
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
| `migration/` | `DataMigrationManager` | 数据迁移：API密钥迁移到加密存储 |
| `util/` | `AppConstants` / `AppLogger` | 全局常量 / 统一日志（内存缓冲脱敏 + Logcat 双写） |

### 全局组件

| 文件 | 功能 |
|------|------|
| `CompanionApp.kt` | Application 入口 & 全局异常捕获（Toast不泄漏内部细节） |
| `AppContainer.kt` | 全局依赖容器：初始化所有 Manager / 构建 ApiClient / 注册内置插件 / 设置回调 |

---

## 4. 数据存储说明

### SharedPreferences 键值

| SharedPreferences 名 | 键 | 说明 | 加密 |
|----------------------|-----|------|------|
| `app_prefs` | `active_persona_id` | 当前活跃角色 ID | 否 |
| `app_prefs` | `is_activated` | 是否已激活 | 否 |
| `app_prefs` | `chat_background` | 聊天背景图路径 | 否 |
| `companion_secure_prefs` | `chat_api_url` / `chat_api_key` / `chat_model` / `search_api_key` | API配置 | ✅ EncryptedSharedPreferences |
| `companion_secure_prefs` | `image_api_url` / `image_api_key` / `image_model` | 图片生成API配置 | ✅ EncryptedSharedPreferences |
| `companion_prefs_fallback` | (同上) | 加密不可用时的降级存储 | ❌ 明文 |
| `chat_history_{personaId}` | `messages` | 各角色独立聊天历史（JSON 数组） | 否 |
| `persona_data` | `persona_name` / `persona_desc` / `persona_personality` / `persona_speech_style` | 角色人设信息 | 否 |
| `bubble_skin_prefs` | `active_bubble_skin` / `active_ai_frame` / `active_user_frame` | 皮肤选择 | 否 |
| `virtual_world_{groupId}` | `world_config` / `world_state` / `story_events` | 虚拟世界数据 | 否 |

### 文件存储

| 路径 | 说明 |
|------|------|
| `filesDir/personas/personas_index.json` | 角色索引文件 |
| `filesDir/personas/{personaId}/` | 各角色独立目录 |
| `filesDir/personas/avatars/` | 角色头像文件 |
| `filesDir/stickers/sticker_index.json` | 用户自定义表情包索引 |
| `filesDir/stickers/builtin_stickers/` | 内置表情包缓存 |
| `filesDir/moments/moments_index.json` | 朋友圈动态索引 |
| `filesDir/moments/images/` | 动态图片存储 |
| `filesDir/chat_images/` | 聊天图片存储 |
| `filesDir/diaries/{personaId}/` | 日记JSON文件 |
| `filesDir/virtual_world_images/` | 虚拟世界生成图片 |

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
| `FOREGROUND_SERVICE_MEDIA_PROJECTION` | 屏幕截图（敏感权限） |
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

## 6. 安全设计说明

### 已实施的安全措施

| 措施 | 说明 |
|------|------|
| EncryptedSharedPreferences | API密钥加密存储（chatApiKey / searchApiKey / imageApiKey） |
| 网络安全配置 | `network_security_config.xml` 禁止明文流量（localhost除外） |
| allowBackup=false | 防止adb备份提取应用数据 |
| 日志脱敏 | AppLogger内存日志过滤API密钥模式；ApiClient日志URL脱敏 |
| 异常信息保护 | 全局异常Toast不泄漏内部类名和消息 |
| Intent输入验证 | persona_id 仅允许字母数字下划线 |
| 图片下载安全 | 使用OkHttpClient替代URL.openStream()，遵循网络安全配置 |
| Response安全关闭 | OkHttp Response使用.use{}确保关闭 |
| 生命周期清理 | GroupChatActivity/HomeActivity onDestroy清理缓存和引用 |
| Image资源保护 | ScreenCaptureManager try-finally确保image.close() |
| 降级文件名诚实 | 加密不可用时降级到"companion_prefs_fallback"而非误导性名称 |

### 已知安全风险（待优化）

| 风险 | 严重程度 | 说明 |
|------|----------|------|
| 辅助功能服务权限过大 | 高 | 可读取所有屏幕内容和执行手势，需添加应用黑名单 |
| AutoOperator无安全边界 | 高 | LLM返回的操作直接执行，需添加频率限制和敏感操作确认 |
| 聊天记录/日记明文存储 | 中 | SharedPreferences/文件中敏感数据未加密 |
| WebView路径遍历 | 中 | Live2DWebView的shouldInterceptRequest需严格验证路径范围 |
| 过度存储权限 | 中 | READ/WRITE_EXTERNAL_STORAGE应迁移到分区存储 |

---

## 7. 构建说明

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
| Activity | 19 | HomeActivity (LAUNCHER) + 18 个功能页 |
| Service | 3 | OverlayService / BackgroundService / ScreenRecognitionService |
| BroadcastReceiver | 3 | WakeUpReceiver / BootReceiver / ReminderReceiver |
| ContentProvider | 1 | FileProvider（文件分享） |

---

## 8. 代码质量待优化项

| 类别 | 问题 | 严重程度 | 建议 |
|------|------|----------|------|
| 内存泄漏 | GroupChatActivity协程未绑定lifecycleScope | 高 | 改用lifecycleScope |
| 内存泄漏 | Manager类持有Activity Context | 高 | 统一使用applicationContext |
| 资源浪费 | ApiClient每次创建新OkHttpClient | 高 | OkHttpClient单例化 |
| 代码重复 | calculateSampleSize重复3次 | 高 | 抽取BitmapUtils工具类 |
| 代码重复 | Bitmap加载逻辑重复5处 | 高 | 抽取BitmapUtils.decodeSampledBitmap |
| 性能 | HomeActivity onBindViewHolder每次解码Bitmap | 高 | 添加LruCache或使用Glide |
| 性能 | notifyDataSetChanged全量刷新 | 高 | 改用DiffUtil或ListAdapter |
| 性能 | VirtualWorldManager config/state每次反序列化 | 中 | 内存缓存 |
| 架构 | MainActivity 2293行违反单一职责 | 高 | 拆分到ViewModel+UseCase |
| 数据 | SharedPreferences被当数据库用 | 中 | 迁移到Room数据库 |
| 错误处理 | 大量空catch块吞没异常 | 中 | 至少添加Log.w记录 |

---

## 📌 关键文件速查

| 要找什么 | 看哪个文件 |
|----------|-----------|
| 修改启动入口/角色列表 | `ui/HomeActivity.kt` |
| 修改单聊逻辑 | `ui/MainActivity.kt` — `sendMessage()` / `sendToLLM()` |
| 修改群聊逻辑 | `groupchat/GroupChatActivity.kt` — `triggerAiResponses()` / `callPersonaLLM()` |
| 修改群聊列表/创建流程 | `groupchat/GroupChatListActivity.kt` |
| 修改AI回复格式 | `network/ApiClient.kt` — `sendChat()` |
| 修改系统Prompt | `prompt/PromptBuilder.kt` |
| 修改@互动规则 | `prompt/PromptBuilder.kt` — `buildGroupChatPrompt()` |
| 修改好感度规则 | `affection/AffectionManager.kt` |
| 修改记忆系统 | `memory/MemoryManager.kt` / `MemoryPool.kt` / `ContextManager.kt` |
| 修改记忆池consolidate | `memory/ContextManager.kt` — `evaluateAndUpdateMemory()` |
| 修改虚拟世界 | `virtualworld/VirtualWorldManager.kt` / `ui/VirtualWorldActivity.kt` |
| 修改世界观编辑UI | `layout/dialog_world_lore_editor.xml` |
| 修改AI自动生成世界观 | `prompt/PromptBuilder.kt` — `buildAutoWorldLorePrompt()` |
| 添加/修改工具插件 | `plugin/BuiltinPlugins.kt` + `plugin/ToolPlugin.kt` |
| 修改插件注册逻辑 | `plugin/PluginRegistry.kt` |
| 修改角色管理 | `persona/PersonaManager.kt` |
| 修改昵称逻辑 | `ui/NicknameManager.kt` |
| 修改收藏逻辑 | `ui/FavoriteManager.kt` |
| 修改表情包 | `sticker/StickerManager.kt` + `sticker/StickerActivity.kt` |
| 修改朋友圈 | `moments/MomentsManager.kt` + `moments/MomentsActivity.kt` |
| 修改气泡皮肤 | `theme/BubbleSkinManager.kt` |
| 修改主题配色 | `theme/ThemeManager.kt` |
| 修改全局依赖初始化 | `AppContainer.kt` |
| 修改聊天气泡 | `layout/item_message_user.xml` / `item_message_pet.xml` / `item_group_message.xml` |
| 修改签到规则 | `gamify/CheckInManager.kt` |
| 修改成就列表 | `gamify/AchievementManager.kt` |
| 修改日记生成 | `diary/DiaryManager.kt` |
| 修改主动搭话 | `interaction/ProactiveInteractionEngine.kt` |
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
| 修改动画效果 | `anim/AnimeUtils.kt` |
| 查看运行日志 | `util/AppLogger.kt` — `getRecentLogs()` |
| 修改创建群聊UI | `layout/dialog_create_group.xml` + `groupchat/GroupChatListActivity.kt` |
| 修改新建角色UI | `layout/dialog_add_persona.xml` + `ui/HomeActivity.kt` |
| 修改群聊列表UI | `layout/activity_group_chat_list.xml` + `layout/item_group_chat.xml` |
| 修改角色卡片UI | `layout/item_persona.xml` |
