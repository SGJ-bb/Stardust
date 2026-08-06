# Stradust - 高度可自定义的 AI 伴侣框架

> 一个把"自定义"做到极致的跨端 AI 伴侣应用 —— 角色、人格、外观、语音、模型、世界设定几乎全部可由用户自行定义。支持 **Android / PC (Tauri) / iOS (Tauri)** 三端。

**核心定位**: 云端重智能 + 本地轻表现与感知 的混合架构 AI 伴侣框架。内置角色与模型仅作示例，真正的卖点是一套覆盖角色、外观、语音、模型、世界、宠物的完整自定义体系。

---

## 项目概览

| 平台 | 技术栈 | 状态 | 目录 |
|------|--------|------|------|
| **Android** | Kotlin + Material Design 3 + Live2D WebView | 主力端 | `android/` |
| **PC** | Tauri 2 + Rust + React 19 + PixiJS Live2D | 开发中 | `stradust-pc/` |
| **iOS** | Tauri 2 + React 19 + AnimeJS | 开发中 | `stradust-ios/` |
| **后端** | Python FastAPI + OpenAI + Mem0 | 基础设施 | `backend/` |

---

## 自定义能力（项目核心）

Stradust 的设计哲学是"框架先行，内容用户填"。以下每一项都可在应用内由用户自行配置，无需改代码。

### 1. 角色与人格自定义

| 维度 | 可自定义内容 | 实现位置 |
|------|------------|---------|
| 基础信息 | 名称、头像、描述、性格、说话风格、口头禅、外貌、喜好 | `persona/PersonaManager.kt`、`character/CharacterCardManager.kt` |
| 提示词 | 完整 system prompt 自由编辑 | `CharacterCard.systemPrompt` |
| 世界书 | 世界设定、人物关系、世界规则（WorldInfo 多条目） | `models/WorldInfo.kt` |
| 用户人设 | 用户自身的昵称与角色设定（UserPersona） | `models/UserPersona.kt` |
| 角色卡导入 | **Tavern 角色卡格式**导入导出，兼容 SillyTavern 生态 | `CharacterCardManager.parseTavernCard()` |
| 语音绑定 | 每个角色可单独绑定 TTS 音色、音调、语速 | `Persona.ttsVoice/ttsPitch/ttsRate` |
| 旧数据迁移 | 旧版 Persona 数据自动迁移为 CharacterCard | `migrateFromLegacyPersonas()` |

### 2. LLM 模型自定义

内置 **12+ 预置 Provider 配置**，每个都可深度调参，也支持完全自定义接入：

| Provider | 默认端点 | 视觉 | 特性 |
|----------|---------|------|------|
| 自定义 | 用户填写 | ✓ | 任意 OpenAI 兼容 API |
| OpenAI | 官方 | ✓ | 完整参数支持 |
| DeepSeek | 官方 | ✓ | 最大 384K 输出 |
| 通义千问 | 阿里云 | ✓ | — |
| 智谱 AI | 官方 | ✓ | 温度 0~1 |
| MiniMax | 官方 | ✓ | 温度 0~1 |
| 月之暗面 | 官方 | ✓ | 温度 0~1 |
| 阿里云百炼 | 阿里云 | ✓ | — |
| 硅基流动 | 官方 | ✓ | 多模型聚合 |
| OpenRouter | 官方 | ✓ | 任意模型路由 |
| NVIDIA NIM | NVIDIA | ✓ | Llama 系列等 |
| n1n | 用户填写 | ✓ | — |

每个 Provider 可调参数：`temperature` / `topP` / `frequency_penalty` / `presence_penalty` / `max_tokens` / `model`，并按 Provider 能力自动屏蔽不支持的参数。详见 `settings/ProviderProfile.kt`。

### 3. 主题与外观自定义

**12 套精心调配的主题**，每套提供 Light/Dark 双模式（暗夜、赛博朋克仅暗色）：

```
樱粉 · 桃粉 · 紫罗兰 · 海蓝 · 翡翠 · 日落 · 玫瑰金 · 薄荷 · 暗夜 · 茶香 · 赛博朋克 · 华夏风韵
```

每套主题包含 30+ 设计令牌（`theme/ThemeTokens.kt`）：背景层、表面层、主色/次色/强调色、错误色、轮廓、文字、用户气泡渐变、AI 气泡、辉光、工具栏、侧栏等，与 PC 端 `themes.css` 1:1 对应。

### 4. 聊天气泡皮肤自定义

`theme/BubbleSkinManager.kt` 提供细粒度气泡自定义：

- **用户气泡**：背景色 / 渐变色 / 圆角 / 描边颜色与宽度 / 文字颜色 / 图片背景（9-patch）
- **AI 气泡**：上述全部 + 透明度
- **头像框**：描边色 / 圆角 / 辉光色 / 辉光半径 / 图片素材
- **图片气泡皮肤**：支持整张图片作为气泡背景

### 5. 像素宠物自定义

`pixelpet/` 模块支持用户从零创建专属像素宠物：

- **AI 绘图生成**：支持 OpenAI DALL-E / Stable Diffusion WebUI / ComfyUI / 通用 SD API（`pixelpet/ImageGenClient.kt`）
- **绘图后端自定义**：apiUrl / model / apiKey 全部可配
- **动作编辑器**：可视化创建宠物动画序列
- **精灵表渲染**：Canvas 2D 像素画渲染管线

### 6. Live2D 模型自定义

- **导入自有模型**：支持 `.model3.json` 格式，兼容 Live2D Cubism 生态
- **实时调整**：缩放、位置、表情、动作可调（`ui/ModelAdjustActivity.kt`）
- **多模型管理**：`ui/ModelManagerActivity.kt` 管理模型导入/切换/删除
- **内置示例模型**：Haru、PurpleBird、小恶魔（仅作演示，可替换）

### 7. 虚拟世界自定义

`virtualworld/VirtualWorldManager.kt` 让用户构建专属世界：

- **世界设定编辑**：lore / scenario / state / story 自由编辑（WorldLoreEditor）
- **关系图**：`RelationshipGraphView` 可视化人物关系
- **场景图生成**：自定义图片生成 API（URL/key/model）
- **多世界隔离**：每个世界独立 SharedPreferences，互不干扰
- **世界书联动**：与角色世界书协同

### 8. 语音自定义

`voice/TtsManager.kt` 提供 4 种 TTS 引擎模式：

| 模式 | 说明 | 可调项 |
|------|------|--------|
| `edge` | Edge TTS（免费，推荐） | voice / rate / pitch |
| `cloud` | 云端 TTS API | apiUrl / apiKey |
| `local` | 本地 TTS | — |
| `auto` | 自动选择 | — |

- **per-persona 音色**：每个角色可绑定独立音色
- **情感变调**：根据情感标签调整音高、语速
- **ASR 引擎**：云端 Whisper / 离线 sherpa-onnx 可切换

### 9. 贴纸与表情自定义

`sticker/StickerManager.kt`：

- **内置贴纸库**：预生成向量嵌入的表情贴纸，支持语义搜索
- **用户导入**：支持添加自有贴纸图片
- **语义搜索**：基于向量检索，输入文字自动匹配贴纸（`VectorStore`）

### 10. 其他可自定义项

| 模块 | 可自定义内容 |
|------|------------|
| 插件系统 | 10+ 内置插件可独立开关 |
| 群聊 | 群名 / 成员 / 群世界书 |
| 记忆池 | 容量上限 / 记忆分类 |
| 唤醒任务 | 自定义唤醒话语 / 定时任务 / 闹钟 |
| 日记 | 日记内容 / 心情标签 |
| 相册 | 纪念相册条目自定义 |
| 时间胶囊 | 自定义开启时间 |
| 内容安全 | 多级过滤（Off/Low/Medium/High） |
| 主动互动 | 基于时间/App类别/电量的规则可配 |

---

## 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                    云端后端 (Python/FastAPI)                  │
│  ┌───────────┐ ┌───────────┐ ┌───────────┐ ┌────────────┐  │
│  │ LLM 服务   │ │ 记忆引擎   │ │ 语音服务   │ │ RAG 向量检索│  │
│  │ GPT-4o    │ │ Mem0      │ │ Azure TTS │ │ ONNX/BERT  │  │
│  └───────────┘ └───────────┘ └───────────┘ └────────────┘  │
└────────────────────────────┬────────────────────────────────┘
                             │ HTTPS / WebSocket
         ┌───────────────────┼───────────────────┐
         ▼                   ▼                   ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│  Android 客户端  │ │   PC 客户端      │ │  iOS 客户端      │
│  Kotlin + MVVM  │ │ Tauri + React   │ │ Tauri + React   │
│                 │ │ Rust 后端       │ │                 │
│ • Live2D WebView│ │ • PixiJS Live2D │ │ • AnimeJS 动画   │
│ • 悬浮窗 Overlay│ │ • 悬浮窗 Pet     │ │ • 原生 iOS UI   │
│ • 无障碍屏幕感知│ │ • Agent 工作台   │ │                 │
│ • 微信 iLink   │ │ • PixelPet 像素宠│ │                 │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

---

## 核心功能

### 智能对话系统
- **流式响应**: SSE 实时流式输出，打字机效果
- **工具调用循环**: LLM 自动调用插件工具（搜索/绘图/代码执行等），最多 3 轮
- **分层提示词**: 人格设定 + 长期记忆 + 短期上下文 + 世界书 + RAG 检索
- **成本优化**: 日常闲聊用 mini 模型，复杂推理用高端模型

### 记忆系统
- **三层记忆架构**: ContextManager（短期）/ MemoryPool（中期）/ GlobalMemoryPool（长期）
- **云端同步**: Mem0 跨会话记忆持久化
- **RAG 检索**: 本地 ONNX 嵌入 + 云端 Embedding API 双模式
- **记忆卡片**: 每日自动生成"今日观察日记"

### 情感 & 表现系统
- **6 维情感映射**: happy / angry / sad / surprised / tsundere / neutral
- **Live2D 参数驱动**: LLM 输出 JSON → 解析 → 实时更新表情/动作参数
- **情感变调**: 根据情感标签调整 TTS 音高、语速
- **情绪分析引擎**: SubjectivityEngine 多维度情绪识别

### 语音交互
- **ASR 语音识别**: 支持云端 Whisper / 离线 sherpa-onnx
- **TTS 语音合成**: Edge TTS (免费) / ChatTTS / Azure Speech / GPT-SoVITS
- **隐私设计**: 显式长按触发，松开即停，不静默录音

### 屏幕感知 (Android)
- **本地处理**: 仅获取 App 包名，离线分类
- **脱敏传输**: 只传抽象类别标签（社交/视频/办公等）
- **隐私保护**: 屏幕文字不离机

### 插件系统
- **10+ 内置插件**: 网络搜索、图片生成、语音通话、昵称生成、日程提醒、闹钟、贴纸、记忆搜索等
- **ToolPlugin 接口**: 统一的工具调用协议
- **AI Action Manager**: LLM 决策驱动的自主行为调度
- **PC 端 Skill 系统**: OCR 识别、文本摘要、翻译、代码运行、JSON 格式化、PDF 合并、GIF 制作、视频裁剪、字幕生成

### 社交 & 互动
- **群聊系统**: 多人群组聊天，群内记忆隔离
- **朋友圈 Moments**: 发布动态、评论互动
- **微信 iLink**: 微信消息桥接（轮询模式）
- **游戏化**: 成就系统、签到、成长值、等级
- **主动互动引擎**: 基于时间/App类别/电量规则驱动

### PixelPet 像素宠物 (PC)
- **AI 绘图生成**: 支持 OpenAI DALL-E / Stability AI / ComfyUI / SD WebUI
- **自定义动作编辑器**: 可视化创建宠物动画序列
- **精灵表渲染**: Canvas 2D 像素画渲染管线

### Agent 工作台 (PC)
- **CLI 执行器**: 安全沙箱中执行终端命令
- **技能面板**: 可视化管理 AI 技能
- **工具调用展示**: 实时显示 LLM 的推理和工具调用过程

---

## 项目目录结构

```
stradust/
├── android/                        # Android 客户端 (Kotlin)
│   └── app/src/main/java/com/aicompanion/
│       ├── character/              # 角色卡(Tavern格式导入导出)
│       ├── persona/                # 人格管理
│       ├── live2d/                 # Live2D 渲染(PixiJS WebView)
│       ├── pixelpet/               # 像素宠物(AI绘图+动作编辑)
│       ├── theme/                  # 12套主题+气泡皮肤+头像框
│       ├── sticker/                # 贴纸(语义搜索)
│       ├── virtualworld/           # 虚拟世界+关系图
│       ├── voice/                  # 语音(EdgeTTS/ASR)
│       ├── settings/               # 设置(ProviderProfile 12+ LLM)
│       ├── memory/                 # 三层记忆系统
│       ├── rag/                    # RAG向量检索(ONNX+云)
│       ├── plugin/                 # 插件系统(10+内置)
│       ├── groupchat/              # 群聊系统
│       ├── moments/                # 朋友圈
│       ├── ilink/                  # 微信连接(iLink)
│       ├── overlay/                # 全局悬浮窗
│       ├── emotion/                # 情绪分析引擎
│       ├── gamify/                 # 游戏化(成就/签到/成长)
│       ├── prompt/                 # 分层提示词构建
│       ├── network/                # 网络层(APIClient/ProviderAdapter)
│       └── ui/                     # Activity 界面
│
├── stradust-pc/                     # PC 客户端 (Tauri 2 + Rust + React)
│   ├── src/                        # React 19 前端
│   │   ├── components/             # agent/chat/live2d/pixelpet/effects/layout
│   │   ├── pages/                  # 页面组件
│   │   ├── stores/                 # Zustand 状态管理
│   │   └── lib/                    # 工具库(含 pixelpet 引擎)
│   └── src-tauri/src/              # Rust 后端
│       ├── agents/                 # Agent 引擎(CLI执行器)
│       ├── commands/               # Tauri命令(18个模块)
│       ├── db/                     # SQLite 数据库(15张表)
│       ├── plugins/                # 插件系统(含builtin_skills)
│       └── services/               # 业务服务(LLM/记忆/语音/RAG等)
│
├── stradust-ios/                    # iOS 客户端 (Tauri 2 + React)
├── backend/                         # Python 后端 (FastAPI)
├── config/                          # 配置文件(.env.example)
└── scripts/                         # 构建与测试脚本
```

---

## 技术栈

### Android
| 类别 | 技术 |
|------|------|
| 语言 | Kotlin 1.9.24 |
| 最低 SDK | 23 (Android 6.0) |
| 目标 SDK | 34 (Android 14) |
| 构建 | Gradle 8.14 + AGP 8.5.0 |
| UI | Material Design 3 (Compose + View 混合) |
| 网络 | OkHttp 4.12.0 |
| ML | TFLite 2.14 + ONNX Runtime 1.17 + ML Kit |
| Live2D | PixiJS + pixi-live2d-display (WebView) |
| 加密 | androidx-security-crypto (EncryptedSharedPreferences) |
| 二维码 | ZXing 3.5.2 |

### PC (Tauri)
| 类别 | 技术 |
|------|------|
| 框架 | Tauri 2 + Rust 2021 edition |
| 前端 | React 19 + TypeScript + Vite 6 + TailwindCSS 4 |
| UI 组件 | Radix UI + Framer Motion + Zustand |
| Live2D | PixiJS 8 + @jannchie/pixi-live2d-display |
| 数据库 | SQLite (rusqlite, bundled) |
| HTTP | reqwest 0.12 |
| 加密 | AES-GCM + SHA2 |

### 后端
| 类别 | 技术 |
|------|------|
| 框架 | FastAPI + Uvicorn |
| LLM | OpenAI GPT-4o / GPT-4o-mini |
| 记忆 | Mem0 |
| 语音 | Azure Speech Services / pyttsx3 |

---

## 快速开始

### 环境要求
- **Android**: JDK 17 + Android SDK + Android Studio
- **PC**: Rust 1.70+ + Node.js 18+ + pnpm
- **iOS**: Xcode 15+ (macOS only)
- **后端**: Python 3.9+

### 启动后端
```bash
cd backend
pip install -r requirements.txt
cp ../config/.env.example ../config/.env
# 编辑 .env 填入你的 API Key
python main.py
# API 文档: http://localhost:8000/docs
```

### 启动 PC 版
```bash
cd stradust-pc
pnpm install
pnpm tauri dev      # 桌面应用
pnpm dev            # 仅 Web 预览
```

### 构建 Android APK
```bash
cd scripts
build-android.bat          # Windows
./build-android.sh         # Linux/Mac
```

### 配置说明
1. 复制 `config/.env.example` 为 `config/.env`，填入 API Key
2. PC 版本: 首次运行后在设置界面配置 LLM Provider（API Key 存储在本地 SQLite，不会提交到仓库）
3. Android 版本: 在设置界面配置服务器地址和 API Key
4. **所有自定义项均可在应用内配置**，无需改代码：角色、主题、气泡、模型、语音、宠物、世界等

---

## 安全 & 隐私

- **API Key 安全**: 所有 API Key 存储在本地 SQLite / EncryptedSharedPreferences，`.gitignore` 已排除所有 `.db` 文件和敏感配置
- **数据最小化**: 屏幕文字不离机，仅传抽象类别标签
- **用户控制**: 所有功能可开关，记忆可删除
- **录音安全**: 显式长按触发，松开即停，不静默录音
- **内容安全**: 多级内容过滤（Off/Low/Medium/High）
- **模型文件安全**: ONNX/大模型文件已加入 `.gitignore`，不会推送到仓库

---

## 开发路线

### 已完成
- [x] Android 完整功能（自定义体系全覆盖）
- [x] 三层记忆系统 + RAG 向量检索
- [x] Live2D WebView 渲染 + 模型管理
- [x] 插件系统（10+ 内置插件）
- [x] 群聊 / 朋友圈 / 微信 iLink
- [x] PC 端 Tauri 基础框架 + Live2D + 聊天
- [x] PC 端 Agent 工作台 + CLI 执行器
- [x] PC 端 PixelPet 像素宠物 + AI 绘图
- [x] iOS 端基础框架 + 14 个页面
- [x] GitHub Actions CI/CD (iOS 构建)

### 进行中
- [ ] PC 端自定义能力对齐 Android（主题/气泡/贴纸/虚拟世界）
- [ ] iOS 端原生能力适配
- [ ] 虚拟世界场景引擎完善

### 计划中
- [ ] 多语言国际化 (i18n)
- [ ] 云端同步服务
- [ ] 性能优化与功耗调优
- [ ] 用户账号体系
- [ ] 自定义插件 SDK（用户开发自己的插件）

---

## 许可证

- **项目代码**: MIT License
- **Live2D SDK**: Live2D Proprietary Software License
- **第三方库**: 遵循各自开源协议

---

## 相关链接

- **远程仓库**: https://github.com/SGJ-bb/Stardust
- **项目文档**: 详见 `PROJECT_SUMMARY.md` (Android 详细文档)

---

*最后更新: 2026-08-06*  *版本: v0.3.0*
