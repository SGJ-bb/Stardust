# StarDust 项目索引

## 项目概述
AI伴侣Android应用，支持多模态交互（文字/语音/图片），虚拟世界推演，Live2D模型展示。
包名：`com.aicompanion`，源码路径：`android/app/src/main/java/com/aicompanion/`

## 目录结构

### 核心模块

| 目录/文件 | 用途 | 关键类 |
|-----------|------|--------|
| `CompanionApp.kt` | Application入口，全局异常捕获 | `CompanionApp` |
| `AppContainer.kt` | 全局单例容器，懒初始化各Manager | `AppContainer` |
| `models/Models.kt` | 核心数据模型定义 | `Emotion`, `Action`, `TextureQuality`, `CharacterCard`, `WorldInfoEntry`, `WorldInfo`, `UserPersona`, `ChatResponse`, `ToolDefinition`, `ToolCall`, `MemoryFact`, `Live2DModel`, `AppCategory`, `DailyCardData`, `CheckInRecord`, `Achievement`, `GrowthNode` |
| `models/EmotionActionMapper.kt` | 情绪/动作映射 | `EmotionActionMapper` → `getDefaultAction()`, `getEmotionFromText()` |

### 网络层 (network/)

| 文件 | 用途 | 关键方法 |
|------|------|---------|
| `ApiClient.kt` | AI后端API客户端，聊天/工具调用/图片/TTS | `sendChat()`, `sendSimplePrompt()`, `sendChatWithToolLoop()`, `buildToolsJson()` |
| `ProviderAdapter.kt` | 多厂商API适配，统一请求/响应格式 | `buildImageRequest()`, `buildImageHeaders()`, `parseImageResponse()`, `buildTtsRequest()`, `parseTtsResponse()`, `callCloudAsr()`, `pollAliyunTask()` |

### 设置 (settings/)

| 文件 | 用途 | 关键类/方法 |
|------|------|------------|
| `SettingsManager.kt` | SharedPreferences统一封装，所有配置读写 | `chatApiUrl`, `chatApiKey`, `chatModel`, `apiProvider`, `ttsEngineMode`, `asrMode`, `llmTemperature`, `llmTopP`, `llmMaxTokens`, `contextTurns`, `screenRecognitionEnabled`, `voiceRecognitionEnabled`, `offlineModeEnabled`, `nagFrequency`, `diaryTriggerMode`, `wakeEnabled`, `searchEnabled`, `live2dEnabled`, `autoStart`, `backgroundRunning` |
| `ServicePreset.kt` | 各服务预设列表（LLM/TTS/ASR/图片生成/图片识别） | `ServicePresets.llmPresets`, `ServicePresets.ttsPresets`, `ServicePresets.asrPresets`, `ServicePresets.imageGenPresets`, `ServicePresets.imageRecogPresets` |
| `ProviderProfile.kt` | 各厂商参数配置（温度范围/频率惩罚/视觉支持等） | `ProviderProfile.getProfile()`, `ProviderProfile.shouldSendFreqPenalty()`, `ProviderProfile.supportsVision()`, `ProviderProfile.getMaxTokensLimit()` |

### UI层 (ui/)

| 文件 | 用途 | 关键类 |
|------|------|--------|
| `MainActivity.kt` | 主聊天界面Activity | `MainActivity` |
| `ChatViewModel.kt` | 聊天ViewModel，消息管理/LLM调用/事件分发 | `ChatViewModel`, `UiEvent`(密封类) |
| `ChatAdapter.kt` | 聊天消息RecyclerView适配器 | `ChatAdapter` |
| `ChatBubblePopup.kt` | 消息气泡弹出菜单（点赞/收藏/复制等） | `ChatBubblePopup` |
| `HomeActivity.kt` | 首页Activity | `HomeActivity` |
| `SettingsActivity.kt` | 设置页面Activity | `SettingsActivity` |
| `SettingsAdapter.kt` | 设置列表适配器 | `SettingsAdapter` |
| `SplashActivity.kt` | 启动页Activity | `SplashActivity` |
| `ProfileActivity.kt` | 个人资料页Activity | `ProfileActivity` |
| `PersonaEditorActivity.kt` | 角色编辑器Activity | `PersonaEditorActivity` |
| `ChatHistoryActivity.kt` | 聊天历史Activity | `ChatHistoryActivity` |
| `VirtualWorldActivity.kt` | 虚拟世界Activity | `VirtualWorldActivity` |
| `MemoryActivity.kt` | 记忆管理Activity | `MemoryActivity` |
| `MemoryPoolActivity.kt` | 记忆池Activity | `MemoryPoolActivity` |
| `DiaryActivity.kt` | 日记Activity | `DiaryActivity` |
| `AchievementActivity.kt` | 成就Activity | `AchievementActivity` |
| `SkinShopActivity.kt` | 皮肤商店Activity | `SkinShopActivity` |
| `ModelManagerActivity.kt` | Live2D模型管理Activity | `ModelManagerActivity` |
| `ModelSettingsActivity.kt` | 模型设置Activity | `ModelSettingsActivity` |
| `ModelAdjustActivity.kt` | 模型调整Activity | `ModelAdjustActivity` |
| `LocalModelActivity.kt` | 本地模型Activity | `LocalModelActivity` |
| `AlarmActivity.kt` | 闹钟Activity | `AlarmActivity` |
| `PhoneCallActivity.kt` | 电话Activity | `PhoneCallActivity` |
| `BedtimeRadioActivity.kt` | 睡前电台Activity | `BedtimeRadioActivity` |
| `TimeCapsuleActivity.kt` | 时间胶囊Activity | `TimeCapsuleActivity` |
| `WebTestActivity.kt` | Web测试Activity | `WebTestActivity` |
| `ActivationActivity.kt` | 激活页Activity | `ActivationActivity` |
| `FavoriteManager.kt` | 收藏管理器 | `FavoriteManager` |
| `NicknameManager.kt` | 昵称管理器 | `NicknameManager` |

### 语音 (voice/)

| 文件 | 用途 | 关键方法 |
|------|------|---------|
| `TtsManager.kt` | TTS语音合成管理，支持Edge/云端/本地 | `synthesize()`, `synthesizeWithVoice()`, `cloudSynthesize()`, `playAudio()`, `stopPlayback()` |
| `VoiceManager.kt` | 语音管理器（Android原生TTS+语音识别） | `startListening()`, `speak()`, `stopListening()`, `shutdown()` |
| `LocalAsrManager.kt` | 本地/云端ASR语音识别 | `startListening()`, `stopListening()`, `callCloudAsr()` |
| `EdgeTtsEngine.kt` | Edge TTS引擎（WebSocket连接微软服务） | `EdgeTtsEngine.synthesize()`, `VOICES`(语音列表) |
| `OfflineASREngine.kt` | 离线ASR引擎（占位实现） | `initialize()`, `startListening()`, `stopListening()`, `release()` |

### 虚拟世界 (virtualworld/)

| 文件 | 用途 | 关键方法 |
|------|------|---------|
| `VirtualWorldManager.kt` | 虚拟世界推演核心，世界tick/图片生成/状态管理 | `generateImageForEvent()`, `tick()`, `loadConfig()`, `saveConfig()`, `loadState()`, `saveState()`, `getStory()`, `hasImageModelConfigured()` |
| `VirtualWorldModels.kt` | 虚拟世界数据模型 | `WorldConfig`, `WorldState`, `StoryEvent` |
| `RelationshipGraphView.kt` | 角色关系图自定义View | `RelationshipGraphView`, `GraphNode`, `GraphEdge` |

### 插件 (plugin/)

| 文件 | 用途 | 关键类 |
|------|------|--------|
| `ToolPlugin.kt` | 插件接口定义 | `ToolPlugin` → `name`, `description`, `getDefinition()`, `execute()`, `isEnabled()` |
| `PluginRegistry.kt` | 插件注册中心，管理插件生命周期 | `PluginRegistry` → `register()`, `unregister()`, `executePlugin()`, `getEnabledDefinitions()` |
| `BuiltinPlugins.kt` | 内置插件实现 | `AlarmPlugin`(set_alarm), `AlarmAtTimePlugin`(set_alarm_at_time), `SchedulePlugin`(add_schedule), `WebSearchPlugin`(search_web), `SearchMemoryPlugin`(search_memory), `SearchDiaryPlugin`(search_diary), `CurrentTimePlugin`(get_current_time), `NicknamePlugin`(summarize_nicknames), `SendStickerPlugin`(send_sticker), `GenerateImagePlugin`(generate_image) |

### 情感 (emotion/)

| 文件 | 用途 | 关键方法 |
|------|------|---------|
| `EmotionAnalyzer.kt` | 情感分析器，调用LLM分析情绪并调整参数 | `analyzeEmotion()` → 返回 `EmotionParams` |
| `EmotionGuardian.kt` | 情感守护，追踪情绪趋势，防止长期负面情绪 | `recordEmotion()`, `getRecentTrend()`, `shouldShowCare()`, `getCareMessage()` |

### 记忆 (memory/)

| 文件 | 用途 | 关键类/方法 |
|------|------|------------|
| `MemoryManager.kt` | 长期记忆管理，自动提取事实 | `addMemoryFact()`, `getLocalMemories()`, `removeMemoryFact()`, `searchMemories()` |
| `ContextManager.kt` | 上下文管理，记忆池+会话+全局记忆 | `memoryPool`, `globalMemoryPool`, `sessionManager`, `addTurn()`, `getContextBlock()` |
| `MemoryPool.kt` | 记忆池，短期记忆条目管理 | `MemoryEntry`, `add()`, `getPoolCharCount()`, `search()`, `consolidate()` |
| `GlobalMemoryPool.kt` | 全局记忆池，跨会话持久记忆 | `addFromScene()`, `getGlobalEntries()`, `consolidate()` |
| `SessionManager.kt` | 会话管理，会话切换/记忆继承 | `SessionInfo`, `incrementTurn()`, `checkMemoryLimit()`, `startNewSession()`, `getInheritedMemory()` |
| `MemorableMomentsManager.kt` | 难忘时刻评分器 | `ScoredMemory`, `evaluateAndScore()`, `getTopMoments()` |

### 存储 (storage/)

| 文件 | 用途 | 关键类 |
|------|------|--------|
| `ChatHistoryStorage.kt` | 聊天历史持久化存储 | `StoredMessage`, `ChatHistoryStorage` → `saveMessages()`, `loadMessages()`, `clearHistory()` |

### 主题 (theme/)

| 文件 | 用途 | 关键类 |
|------|------|--------|
| `ThemeManager.kt` | 主题管理，配色方案切换 | `ThemeManager`, `ColorScheme` |
| `BubbleSkinManager.kt` | 气泡皮肤管理 | `BubbleSkinManager`, `BubbleSkin` |

### RAG (rag/)

| 文件 | 用途 | 关键类/方法 |
|------|------|------------|
| `PersonaRagManager.kt` | Persona RAG检索，基于向量相似度 | `buildIndex()`, `search()`, `currentHash()` |
| `RagEmbedder.kt` | 文本嵌入接口+TF-IDF实现 | `RagEmbedder`(接口), `TfidfEmbedder` → `embed()`, `embedSingle()`, `buildVocabulary()` |
| `VectorStore.kt` | 向量存储，JSON文件持久化 | `VectorStore` → `add()`, `addAll()`, `search()`, `load()`, `save()` |
| `VectorMath.kt` | 向量数学工具 | `VectorMath.cosineSimilarity()` |
| `TextChunker.kt` | 文本分块器 | `TextChunker` → `chunkPersona()`, `chunkText()`, `Chunk` |
| `RagConfig.kt` | RAG配置常量 | `RagConfig` → `personaRagEnabled`, `personaTopK`, `chunkMaxChars`, `minSimilarity` |

### 人设 (persona/)

| 文件 | 用途 | 关键类 |
|------|------|--------|
| `PersonaManager.kt` | 角色人设管理，创建/切换/删除 | `Persona`, `PersonaManager` → `load()`, `getPersona()`, `savePersona()`, `deletePersona()`, `getAllPersonas()` |

### 提示词 (prompt/)

| 文件 | 用途 | 关键方法 |
|------|------|---------|
| `PromptBuilder.kt` | 系统提示词构建，身份/规则/记忆注入 | `buildIdentity()`, `getCoreRules()`, `IdentityBlock` |

### 动作 (action/)

| 文件 | 用途 | 关键类/方法 |
|------|------|------------|
| `AIActionManager.kt` | AI动作管理，闹钟/日程/通知 | `AIActionManager` → `setAlarm()`, `addSchedule()`, `AlarmInfo`, `ScheduleInfo` |

### 好感度 (affection/)

| 文件 | 用途 | 关键类/方法 |
|------|------|------------|
| `AffectionManager.kt` | 好感度计算与管理 | `AffectionManager` → `affectionLevel`, `processMessage()`, `getAffectionTitle()`, `DailyStats` |

### 日记 (diary/)

| 文件 | 用途 | 关键类 |
|------|------|--------|
| `DiaryManager.kt` | 日记生成与管理 | `DiaryManager` → `generateDiary()`, `getDiary()`, `getDiaryList()`, `updateDiary()` |
| `DiaryEntry.kt` | 日记数据类 | `DiaryEntry` |

### 游戏化 (gamify/)

| 文件 | 用途 | 关键类/方法 |
|------|------|------------|
| `AchievementManager.kt` | 成就系统 | `AchievementManager` → `checkAndUnlock()`, `getAllAchievements()`, `getUnlockedCount()` |
| `CheckInManager.kt` | 签到打卡 | `CheckInManager` → `checkIn()`, `currentStreak`, `totalCheckIns` |
| `GrowthManager.kt` | 成长等级系统 | `GrowthManager` → `getCurrentStage()`, `stages`, `GrowthStage` |

### Live2D (live2d/)

| 文件 | 用途 | 关键类 |
|------|------|--------|
| `Live2DWebView.kt` | Live2D WebView渲染容器，Cubism SDK for Web | `Live2DWebView` → `loadModel()`, `setEmotion()`, `triggerAction()`, `captureScreenshot()` |
| `Live2DModel.kt` | Live2D模型数据类 | `Live2DModelInfo` |
| `Live2DRenderer.kt` | OpenGL ES备用渲染器（占位） | `Live2DRenderer` |
| `ModelManager.kt` | Live2D模型下载/切换/删除 | `ModelManager` → `getAllModels()`, `downloadModel()`, `deleteModel()` |

### 悬浮窗 (overlay/)

| 文件 | 用途 | 关键类 |
|------|------|--------|
| `OverlayWindow.kt` | 系统级悬浮窗，Live2D+迷你聊天 | `OverlayWindow` → `show()`, `hide()`, `updateSize()` |
| `OverlayTouchHandler.kt` | 悬浮窗触摸事件处理 | `OverlayTouchHandler` |

### 服务 (services/)

| 文件 | 用途 | 关键类 |
|------|------|--------|
| `BackgroundService.kt` | 后台保活服务，日记自动生成 | `BackgroundService` |
| `OverlayService.kt` | 悬浮窗前台服务 | `OverlayService` |
| `SystemMonitor.kt` | 系统监控（电量/应用切换/网络） | `SystemMonitor` → `startMonitoring()`, `stopMonitoring()` |

### 屏幕 (screen/)

| 文件 | 用途 | 关键类/方法 |
|------|------|------------|
| `ScreenRecognitionService.kt` | 无障碍服务，屏幕内容识别 | `ScreenRecognitionService` → `getLastScreenText()`, `getClickableData()`, `performClick()`, `performScroll()` |
| `AutoOperator.kt` | 自动操作器（点击/滑动/输入） | `AutoOperator` → `readScreenText()`, `executeAction()`, `AutoAction` |
| `AppCategoryClassifier.kt` | 应用分类器 | `AppCategoryClassifier.classify()` |

### 本地模型 (localmodel/)

| 文件 | 用途 | 关键类 |
|------|------|--------|
| `LocalModelManager.kt` | 本地模型管理（ML Kit OCR等） | `LocalModelManager` → `analyzeScreen()`, `ScreenAnalysisResult` |
| `DeviceProfiler.kt` | 设备性能分析 | `DeviceProfiler.profile()` → `DeviceProfile`, `ModelTier` |
| `TFLiteRunner.kt` | TFLite推理引擎 | `TFLiteRunner` → `loadModel()`, `classify()`, `ClassificationResult` |
| `ModelConfig.kt` | 模型配置与注册表 | `ModelInfo`, `ModelRegistry`, `ModelTier` |
| `ModelDownloader.kt` | 模型下载器 | `ModelDownloader` |
| `ScreenCaptureManager.kt` | 屏幕截图管理 | `ScreenCaptureManager` |
| `ScreenCaptureService.kt` | 屏幕截图服务 | `ScreenCaptureService` |

### 搜索 (search/)

| 文件 | 用途 | 关键方法 |
|------|------|---------|
| `WebSearchEngine.kt` | 网页搜索引擎（DuckDuckGo/Bing/Baidu） | `search()`, `searchAndSummarize()`, `SearchResult` |

### 安全 (safety/)

| 文件 | 用途 | 关键方法 |
|------|------|---------|
| `ContentSafetyFilter.kt` | 内容安全过滤（色情/暴力/违法） | `ContentSafetyFilter.filter()`, `isEnabled()`, `setEnabled()` |

### 群聊 (groupchat/)

| 文件 | 用途 | 关键类 |
|------|------|--------|
| `GroupChatManager.kt` | 群聊管理，多角色对话 | `GroupChat`, `GroupMessage`, `GroupChatManager` |
| `GroupChatActivity.kt` | 群聊界面Activity | `GroupChatActivity` |
| `GroupChatListActivity.kt` | 群聊列表Activity | `GroupChatListActivity` |

### iLink微信 (ilink/)

| 文件 | 用途 | 关键类 |
|------|------|--------|
| `IlinkApi.kt` | 微信iLink API接口 | `IlinkApi` → `sendMessage()`, `generateQRCode()` |
| `IlinkAuthManager.kt` | iLink认证管理 | `IlinkAuthManager` → `isBound`, `botToken`, `bindBot()`, `unbindBot()` |
| `IlinkPollingService.kt` | iLink消息轮询服务 | `IlinkPollingService` |
| `WechatBindActivity.kt` | 微信绑定Activity | `WechatBindActivity` |

### 唤醒 (wakeup/)

| 文件 | 用途 | 关键类 |
|------|------|--------|
| `WakeUpScheduler.kt` | 唤醒调度器（AlarmManager） | `WakeUpScheduler` → `scheduleWakeup()`, `cancelWakeup()`, `WakeUpReceiver` |
| `WakeUpTaskManager.kt` | 唤醒任务管理 | `WakeUpTaskManager`, `WakeUpTask` |
| `WakeUpActivity.kt` | 唤醒设置Activity | `WakeUpActivity` |
| `BootReceiver.kt` | 开机自启广播接收器 | `BootReceiver` |

### 其他模块

| 文件 | 用途 |
|------|------|
| `interaction/ProactiveInteractionEngine.kt` | 主动交互引擎，根据时间/活跃度/好感度主动搭话 |
| `humanizer/Humanizer.kt` | 人性化处理器，添加思考前缀/打字错误/停顿标签 |
| `predict/ChatPredictor.kt` | 聊天预测器，预测用户可能的回复 |
| `nlp/OfflineNLP.kt` | 离线NLP，意图识别+兜底回复 |
| `calendar/CalendarManager.kt` | 节日/日历管理，公历+农历节日识别 |
| `calendar/CalendarActivity.kt` | 日历Activity |
| `capsule/TimeCapsuleManager.kt` | 时间胶囊管理 |
| `character/CharacterCardManager.kt` | 角色卡片管理（CharacterCard V2格式） |
| `album/MemorialAlbumManager.kt` | 纪念相册管理 |
| `album/MemorialAlbumActivity.kt` | 纪念相册Activity |
| `milestone/MilestoneManager.kt` | 里程碑记录 |
| `moments/MomentsManager.kt` | 朋友圈/动态管理 |
| `moments/MomentsActivity.kt` | 动态Activity |
| `moments/MomentModel.kt` | 动态数据模型 |
| `sticker/StickerManager.kt` | 表情包管理，向量搜索匹配 |
| `sticker/StickerActivity.kt` | 表情包Activity |
| `sticker/StickerModel.kt` | 表情包数据模型 |
| `stats/PersonaStatsManager.kt` | 角色统计管理（消息数/聊天天数/情绪分布） |
| `migration/DataMigrationManager.kt` | 数据迁移管理 |
| `anim/AnimeUtils.kt` | 动画工具类 |
| `anim/AnimeInterpolators.kt` | 动画插值器 |
| `api/ApiProviderPreset.kt` | API预设配置（旧版，含TTS语音列表） |
| `util/AppConstants.kt` | 全局常量定义 |
| `util/AppLogger.kt` | 日志管理器，文件持久化 |

## API适配层

### ProviderAdapter 格式类型

| formatType | 厂商 | 请求差异 | 响应差异 |
|------------|------|---------|---------|
| `openai` | OpenAI兼容 | 标准`{model, prompt, n, size}` | `data[0].url` 或 `data[0].b64_json` |
| `siliconflow` | 硅基流动 | `image_size`替代`size`, `batch_size`替代`n`, `num_inference_steps` | `images[0].url` 或 `images[0].b64_json` |
| `aliyun_async` | 阿里云百炼 | `input.messages`嵌套结构, `parameters.size`用`*`替代`x` | `output.task_id`需轮询, 或`output.results[0].url` |
| `fish_audio` | Fish Audio | TTS专用格式 | 音频流响应 |

### ServicePreset 预设列表

#### LLM预设

| 服务 | 厂商 | URL | 默认模型 |
|------|------|-----|---------|
| openai | OpenAI | `https://api.openai.com/v1/chat/completions` | gpt-4o-mini |
| aliyun | 阿里云百炼 | `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` | qwen-plus |
| zhipu | 智谱AI | `https://open.bigmodel.cn/api/paas/v4/chat/completions` | glm-4-flash |
| minimax | MiniMax | `https://api.minimax.chat/v1/text/chatcompletion_v2` | MiniMax-Text-01 |
| moonshot | 月之暗面 | `https://api.moonshot.cn/v1/chat/completions` | moonshot-v1-8k |
| deepseek | DeepSeek | `https://api.deepseek.com/v1/chat/completions` | deepseek-v4-flash |
| siliconflow | 硅基流动 | `https://api.siliconflow.cn/v1/chat/completions` | Qwen/Qwen2.5-7B-Instruct |
| openrouter | OpenRouter | `https://openrouter.ai/api/v1/chat/completions` | google/gemini-2.0-flash-001 |
| qwen | 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` | qwen-max |
| n1n | n1n | `https://api.n1n.ai/v1/chat/completions` | gpt-4o-mini |

#### TTS预设

| 服务 | 厂商 | URL | 默认模型 | 默认语音 |
|------|------|-----|---------|---------|
| openai | OpenAI | `https://api.openai.com/v1/audio/speech` | tts-1 | alloy |
| siliconflow | 硅基流动 | `https://api.siliconflow.cn/v1/audio/speech` | FunAudioLLM/CosyVoice2-0.5B | alex |
| fish_audio | Fish Audio | `https://api.fish.audio/v1/tts` | s2-pro | default |
| aliyun | 阿里云百炼 | `https://dashscope.aliyuncs.com/compatible-mode/v1/audio/speech` | cosyvoice-v1 | longxiaochun |

#### ASR预设

| 服务 | 厂商 | URL | 默认模型 |
|------|------|-----|---------|
| openai | OpenAI | `https://api.openai.com/v1/audio/transcriptions` | whisper-1 |
| siliconflow | 硅基流动 | `https://api.siliconflow.cn/v1/audio/transcriptions` | FunAudioLLM/SenseVoiceSmall |
| aliyun | 阿里云百炼 | `https://dashscope.aliyuncs.com/compatible-mode/v1/audio/transcriptions` | sensevoice-v1 |

#### 图片生成预设

| 服务 | 厂商 | URL | 默认模型 | formatType |
|------|------|-----|---------|-----------|
| openai | OpenAI (DALL-E) | `https://api.openai.com/v1/images/generations` | dall-e-3 | openai |
| siliconflow | 硅基流动 | `https://api.siliconflow.cn/v1/images/generations` | Kwai-Kolors/Kolors | siliconflow |
| aliyun_kling | 阿里云百炼(可灵) | `https://dashscope.aliyuncs.com/api/v1/services/aigc/image-generation/generation` | kling-v3-image-generation | aliyun_async |
| zhipu | 智谱AI(CogView) | `https://open.bigmodel.cn/api/paas/v4/images/generations` | cogview-4-250304 | openai |

#### 图片识别预设

| 服务 | 厂商 | URL | 默认模型 |
|------|------|-----|---------|
| openai | OpenAI (GPT-4o) | `https://api.openai.com/v1/chat/completions` | gpt-4o |
| aliyun | 阿里云百炼 (Qwen-VL) | `https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions` | qwen-vl-max |
| zhipu | 智谱AI (GLM-4V) | `https://open.bigmodel.cn/api/paas/v4/chat/completions` | glm-4v-flash |
| siliconflow | 硅基流动 (Qwen-VL) | `https://api.siliconflow.cn/v1/chat/completions` | Qwen/Qwen2-VL-7B-Instruct |

### ProviderProfile 参数差异

| 厂商 | 温度范围 | frequency_penalty | presence_penalty | 视觉支持 | maxTokensLimit |
|------|---------|-------------------|-----------------|---------|---------------|
| custom | 0~2 | ✅(-2~2) | ✅(-2~2) | ✅ | 131072 |
| openai | 0~2 | ✅(-2~2) | ✅(-2~2) | ✅ | 16384 |
| deepseek | 0~2 | ✅(0~2) | ✅(0~2) | ✅ | 393216 |
| aliyun | 0~2 | ❌ | ❌ | ✅ | 8192 |
| qwen | 0~2 | ❌ | ❌ | ✅ | 8192 |
| zhipu | 0~1 | ❌ | ❌ | ✅ | 8192 |
| minimax | 0~1 | ❌ | ❌ | ✅ | 8192 |
| moonshot | 0~1 | ❌ | ❌ | ✅ | 8192 |
| siliconflow | 0~2 | ❌ | ❌ | ✅ | 8192 |
| openrouter | 0~2 | ✅(-2~2) | ✅(-2~2) | ✅ | 131072 |
| n1n | 0~2 | ✅ | ✅ | ✅ | 32768 |

## 数据流

### 聊天流程
```
用户输入 → ChatViewModel.sendToLLM()
  → PromptBuilder.buildIdentity() 构建系统提示词
  → ContextManager.getContextBlock() 注入记忆上下文
  → PersonaRagManager.search() RAG检索相关片段
  → ApiClient.sendChat() 发送请求
  → 解析ChatResponse (text/emotion/action/tool_calls)
  → 如果有tool_calls → PluginRegistry.executePlugin() 执行工具
  → 工具结果 → ApiClient.sendChat() 再次请求(循环)
  → EmotionAnalyzer.analyzeEmotion() 情感分析(可选)
  → Humanizer.humanize() 人性化处理
  → ChatViewModel.addPetMessageInternal() 添加消息
  → TtsManager.synthesize() 语音合成(可选)
  → UI显示
```

### 图片生成流程
```
用户"画一张猫" → LLM返回tool_calls: generate_image
  → GenerateImagePlugin.execute()
  → VirtualWorldManager.generateImageForEvent()
  → ProviderAdapter.buildImageRequest() 构建请求
  → ProviderAdapter.buildImageHeaders() 构建请求头
  → HTTP请求 → ProviderAdapter.parseImageResponse() 解析响应
  → (阿里云异步) pollAliyunTask() 轮询任务
  → 下载图片 → 保存到本地
  → UI显示
```

### TTS流程
```
AI回复 → TtsManager.synthesize()
  → 判断引擎模式(edge/cloud/local/auto)
  → Edge模式: EdgeTtsEngine.synthesize() → WebSocket连接微软 → 保存音频文件
  → Cloud模式: cloudSynthesize() → ProviderAdapter → API → 保存音频
  → TtsManager.playAudio() → MediaPlayer播放
```

### ASR流程
```
用户说话 → LocalAsrManager.startListening()
  → 判断模式(system/sherpa/cloud)
  → System模式: Android SpeechRecognizer
  → Cloud模式: callCloudAsr() → ProviderAdapter → API → 文字结果
  → AsrListener.onFinalResult() 回调
```

### 虚拟世界推演流程
```
VirtualWorldManager.tick()
  → 构建世界上下文(WorldConfig+WorldState+StoryEvent)
  → ApiClient.sendChat() 请求LLM推演
  → 解析推演结果 → 更新WorldState
  → 如果需要配图 → generateImageForEvent()
  → 保存状态和故事
```

## 关键配置

### SettingsManager 属性

| 属性 | 类型 | 默认值 | 用途 |
|------|------|--------|------|
| `chatApiUrl` | String | "" | 聊天API地址 |
| `chatApiKey` | String(加密) | "" | 聊天API密钥 |
| `chatModel` | String | "gpt-4o-mini" | 聊天模型名 |
| `apiProvider` | String | "custom" | API提供商ID |
| `ttsProvider` | String | "custom" | TTS提供商ID |
| `asrProvider` | String | "custom" | ASR提供商ID |
| `imageGenProvider` | String | "custom" | 图片生成提供商ID |
| `imageRecogProvider` | String | "custom" | 图片识别提供商ID |
| `ttsEngineMode` | String | "edge" | TTS引擎模式(edge/cloud/local/auto) |
| `ttsApiUrl` | String | "" | TTS API地址 |
| `ttsModel` | String | "tts-1" | TTS模型名 |
| `ttsVoice` | String | "alloy" | TTS语音名 |
| `ttsPitch` | Float | 1.0 | TTS音调(0.5~2.0) |
| `ttsRate` | Float | 1.0 | TTS语速(0.5~2.0) |
| `asrMode` | String | "cloud" | ASR模式(system/sherpa/cloud) |
| `asrApiUrl` | String | "" | ASR API地址 |
| `screenApiUrl` | String | "" | 屏幕识别API地址 |
| `screenModel` | String | "gpt-4o" | 屏幕识别模型 |
| `llmTemperature` | Float | 1.05 | LLM温度(0~2) |
| `llmTopP` | Float | 0.92 | LLM Top-P(0~1) |
| `llmFrequencyPenalty` | Float | 0.35 | 频率惩罚(-2~2) |
| `llmPresencePenalty` | Float | 0.5 | 存在惩罚(-2~2) |
| `llmMaxTokens` | Int | 500 | 最大输出token数 |
| `contextTurns` | Int | 10 | 上下文轮数(5~50) |
| `screenRecognitionEnabled` | Boolean | false | 屏幕识别开关 |
| `voiceRecognitionEnabled` | Boolean | false | 语音识别开关 |
| `offlineModeEnabled` | Boolean | false | 离线模式开关 |
| `isTTSEnabled` | Boolean | true | TTS开关 |
| `emotionAnalysisEnabled` | Boolean | false | 情感分析开关 |
| `llmEmotionAnalysisEnabled` | Boolean | true | LLM情感标签开关 |
| `searchEnabled` | Boolean | true | 搜索功能开关 |
| `searchProvider` | String | "duckduckgo" | 搜索引擎(duckduckgo/bing/baidu) |
| `live2dEnabled` | Boolean | true | Live2D开关 |
| `autoStart` | Boolean | true | 开机自启 |
| `backgroundRunning` | Boolean | true | 后台运行 |
| `nagFrequency` | NagFrequency | MEDIUM | 主动搭话频率(LOW/MEDIUM/HIGH/OFF) |
| `diaryTriggerMode` | DiaryTriggerMode | DAILY_10PM | 日记触发模式(MANUAL/MSG_50/HOURLY/EVERY_2H/DAILY_10PM) |
| `languageStyle` | LanguageStyle | NORMAL | 语言风格(NORMAL/TSUNDERE/CUTE) |
| `wakeEnabled` | Boolean | false | 定时唤醒开关 |
| `wakeHour` | Int | 8 | 唤醒小时 |
| `wakeMinute` | Int | 0 | 唤醒分钟 |
| `useLocalOcr` | Boolean | true | 使用本地OCR |
| `useChatModelForVision` | Boolean | true | 用聊天模型做视觉 |
| `simpleScreenMode` | Boolean | false | 简易屏幕模式 |
| `onboardingCompleted` | Boolean | false | 引导完成标记 |
| `userGender` | String | "" | 用户性别 |
| `userBirthday` | String | "" | 用户生日 |
| `userAppearance` | String | "" | 用户外貌描述 |

## 布局文件索引

| 文件 | 用途 | 关键控件 |
|------|------|---------|
| `activity_main.xml` | 主聊天界面 | RecyclerView, EditText, Live2D WebView |
| `activity_home.xml` | 首页 | 角色信息, 快捷入口 |
| `activity_settings.xml` | 设置页面 | RecyclerView(设置列表) |
| `activity_profile.xml` | 个人资料 | 用户信息表单 |
| `activity_persona_editor.xml` | 角色编辑器 | 角色属性编辑表单 |
| `activity_virtual_world.xml` | 虚拟世界 | 故事列表, 关系图, 配置面板 |
| `activity_memory.xml` | 记忆管理 | 记忆列表 |
| `activity_memory_pool.xml` | 记忆池 | 记忆条目列表 |
| `activity_diary.xml` | 日记 | 日记列表 |
| `activity_achievement.xml` | 成就 | 成就网格 |
| `activity_skin_shop.xml` | 皮肤商店 | 皮肤卡片列表 |
| `activity_model_manager.xml` | 模型管理 | 模型卡片列表 |
| `activity_model_settings.xml` | 模型设置 | 参数调节 |
| `activity_model_adjust.xml` | 模型调整 | 缩放/偏移调节 |
| `activity_local_model.xml` | 本地模型 | 模型下载/管理 |
| `activity_alarm.xml` | 闹钟 | 闹钟列表 |
| `activity_wakeup.xml` | 唤醒设置 | 唤醒任务列表 |
| `activity_group_chat.xml` | 群聊 | 群聊消息列表 |
| `activity_group_chat_list.xml` | 群聊列表 | 群聊卡片列表 |
| `activity_moments.xml` | 动态 | 动态流列表 |
| `activity_sticker.xml` | 表情包 | 表情包网格 |
| `activity_activation.xml` | 激活页 | 激活表单 |
| `activity_web_test.xml` | Web测试 | WebView |
| `item_message_user.xml` | 用户消息气泡 | TextView, 头像 |
| `item_message_pet.xml` | AI消息气泡 | TextView, 头像, 情绪指示器 |
| `item_group_message.xml` | 群聊消息 | 角色名, 消息内容 |
| `item_settings_llm.xml` | LLM设置项 | Spinner, SeekBar |
| `item_settings_tts.xml` | TTS设置项 | Spinner, SeekBar |
| `item_settings_asr.xml` | ASR设置项 | Spinner |
| `item_settings_user.xml` | 用户设置项 | EditText |
| `item_settings_style.xml` | 风格设置项 | Spinner |
| `item_settings_memory.xml` | 记忆设置项 | Switch, SeekBar |
| `item_settings_diary.xml` | 日记设置项 | Spinner |
| `item_settings_appearance.xml` | 外观设置项 | Switch |
| `item_settings_ai_features.xml` | AI功能设置项 | Switch |
| `item_settings_screen.xml` | 屏幕设置项 | Switch |
| `item_settings_safety.xml` | 安全设置项 | Switch |
| `item_settings_search.xml` | 搜索设置项 | Switch, EditText |
| `item_model.xml` | 模型卡片 | 模型信息, 下载按钮 |
| `item_model_card.xml` | 模型卡片(皮肤商店) | 预览图, 名称 |
| `item_skin_shop.xml` | 皮肤商品 | 预览, 名称, 价格 |
| `item_frame_shop.xml` | 边框商品 | 预览, 名称 |
| `item_achievement.xml` | 成就项 | 图标, 标题, 进度 |
| `item_diary.xml` | 日记项 | 日期, 心情, 摘要 |
| `item_moment.xml` | 动态项 | 作者, 内容, 图片 |
| `item_comment.xml` | 评论项 | 评论者, 内容 |
| `item_sticker.xml` | 表情包项 | 图片, 描述 |
| `item_favorite_message.xml` | 收藏消息 | 消息内容, 时间 |
| `item_wakeup_task.xml` | 唤醒任务 | 时间, 名称, 开关 |
| `item_story_event.xml` | 故事事件 | 事件内容, 时间 |
| `item_persona.xml` | 角色卡片 | 头像, 名称, 描述 |
| `item_persona_picker.xml` | 角色选择项 | 头像, 名称 |
| `item_member_select.xml` | 成员选择项 | 头像, 名称, 复选框 |
| `item_group_chat.xml` | 群聊项 | 名称, 预览, 时间 |
| `item_typing_indicator.xml` | 打字指示器 | 动画点 |
| `stub_live2d.xml` | Live2D占位 | Live2DWebView |
| `popup_chat_action.xml` | 聊天操作弹出 | 操作按钮列表 |
| `popup_emoji_reaction.xml` | 表情反应弹出 | Emoji网格 |
| `dialog_persona_picker.xml` | 角色选择对话框 | 角色列表 |
| `dialog_add_persona.xml` | 添加角色对话框 | 输入表单 |
| `dialog_world_lore_editor.xml` | 世界观编辑对话框 | 文本编辑 |
| `dialog_diary_detail.xml` | 日记详情对话框 | 日记内容 |
| `dialog_create_group.xml` | 创建群聊对话框 | 输入表单 |
| `dialog_new_moment.xml` | 发动态对话框 | 输入框, 图片选择 |
| `dialog_add_wakeup_task.xml` | 添加唤醒任务对话框 | 时间选择, 输入 |
| `dialog_add_sticker.xml` | 添加表情包对话框 | 图片选择, 描述输入 |
| `settings_footer_stub.xml` | 设置页脚 | 版本信息 |
| `spinner_item_dark.xml` | 深色下拉项 | TextView |
| `spinner_dropdown_item_dark.xml` | 深色下拉菜单项 | TextView |
