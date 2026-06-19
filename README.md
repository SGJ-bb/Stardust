# 星尘 Stradust - AI 智能桌宠伴侣

> 一只名为"星尘"的异色瞳黑猫 AI 桌宠，基于 Live2D 渲染，支持 **Android / PC (Tauri) / iOS (Tauri)** 三端。

**核心定位**: 云端重智能 + 本地轻表现与感知 的混合架构 AI 伴侣应用。

---

## 项目概览

| 平台 | 技术栈 | 状态 | 目录 |
|------|--------|------|------|
| **Android** | Kotlin + Material Design 3 + Live2D WebView | 主力端 | `android/` |
| **PC** | Tauri 2 + Rust + React 19 + PixiJS Live2D | 开发中 | `stradust-pc/` |
| **iOS** | Tauri 2 + React 19 + AnimeJS | 开发中 | `stradust-ios/` |
| **后端** | Python FastAPI + OpenAI + Mem0 | 基础设施 | `backend/` |

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
- **多模型支持**: OpenAI / Anthropic / Ollama / 自定义兼容 API
- **流式响应**: SSE 实时流式输出，打字机效果
- **工具调用循环**: LLM 自动调用插件工具（搜索/绘图/代码执行等），最多 3 轮
- **分层提示词**: 人格设定 + 长期记忆 + 短期上下文 + 世界书 + RAG 检索
- **成本优化**: 日常闲聊用 mini 模型，复杂推理用高端模型

### 记忆系统
- **三层记忆架构**:
  - **ContextManager** - 短期对话上下文窗口管理
  - **MemoryPool** - 中期记忆池（可配置 token 上限）
  - **GlobalMemoryPool** - 长期全局持久化记忆
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

### 角色系统 (Persona)
- **多角色切换**: 支持 Character Card (Tavern格式) 导入导出
- **人格进化**: 基于交互历史动态演化性格参数
- **人格档案**: ProfileActivity 查看/编辑完整角色卡

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
- **虚拟世界 (StraCloud)**: 像素风格场景渲染引擎，场景提示词→像素图生成

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
│   ├── app/src/main/java/com/aicompanion/
│   │   ├── action/                 # AI 行为管理
│   │   ├── album/                  # 纪念相册
│   │   ├── diary/                  # 日记系统
│   │   ├── emotion/                # 情绪分析引擎
│   │   ├── gamify/                 # 游戏化(成就/签到/成长)
│   │   ├── groupchat/              # 群聊系统
│   │   ├── humanizer/              # 人性化处理
│   │   ├── ilink/                  # 微信连接(iLink)
│   │   ├── live2d/                 # Live2D 渲染(PixiJS WebView)
│   │   ├── localmodel/             # 本地模型(TFLite/ONNX)
│   │   ├── memory/                 # 三层记忆系统
│   │   ├── moments/                # 朋友圈
│   │   ├── network/                # 网络层(APIClient/ProviderAdapter)
│   │   ├── overlay/                # 全局悬浮窗
│   │   ├── persona/                # 角色人格系统
│   │   ├── pixelpet/               # 像素宠物(Android版)
│   │   ├── plugin/                 # 插件系统(10+内置插件)
│   │   ├── predict/                # 聊天预测
│   │   ├── prompt/                 # 分层提示词构建
│   │   ├── rag/                    # RAG向量检索(ONNX+云)
│   │   ├── screen/                 # 屏幕感知/自动操作
│   │   ├── search/                 # 网络搜索
│   │   ├── settings/               # 设置管理
│   │   ├── sticker/                # 表情包系统
│   │   ├── theme/                  # 主题/气泡皮肤
│   │   ├── ui/                     # 32个Activity界面
│   │   ├── voice/                  # 语音(ASR/TTS/EdgeTTS)
│   │   ├── virtualworld/           # 虚拟世界(StraCloud)
│   │   ├── wakeup/                 # 唤醒/定时任务
│   │   ├── AppContainer.kt         # 全局依赖容器
│   │   └── CompanionApp.kt         # Application类
│   └── app/src/main/res/           # 资源(布局/动画/图标)
│
├── stradust-pc/                     # PC 客户端 (Tauri 2 + Rust + React)
│   ├── src/                        # React 19 前端
│   │   ├── components/
│   │   │   ├── agent/              # Agent 工作台
│   │   │   ├── chat/               # 聊天界面
│   │   │   ├── live2d/             # Live2D Canvas (PixiJS)
│   │   │   ├── pixelpet/           # 像素宠物(创建/设置/管理)
│   │   │   ├── effects/            # 环境特效(雨/入场动画)
│   │   │   └── layout/             # 布局(侧栏/标题栏)
│   │   ├── hooks/                  # React Hooks
│   │   ├── lib/
│   │   │   ├── agent/              # Agent 类型
│   │   │   └── pixelpet/           # 像素宠物引擎
│   │   └── pages/                  # 页面组件
│   └── src-tauri/src/              # Rust 后端
│       ├── agents/                 # Agent 引擎(CLI执行器)
│       ├── commands/               # Tauri命令(18个模块)
│       ├── db/                     # SQLite 数据库(15张表)
│       ├── models/                 # 数据模型
│       ├── plugins/                # 插件系统(含builtin_skills)
│       ├── services/               # 业务服务(LLM/记忆/语音/RAG等)
│       └── utils/                  # 工具(加密/日志)
│
├── stradust-ios/                    # iOS 客户端 (Tauri 2 + React)
│   ├── src/pages/                  # 14个页面
│   └── src-tauri/                  # Tauri 配置
│
├── backend/                         # Python 后端 (FastAPI)
│   └── app/
│       ├── api/                    # RESTful API
│       ├── services/               # LLM/记忆/语音/提示词
│       └── core/config.py          # 配置管理
│
├── config/                          # 配置文件
│   ├── .env.example                # 环境变量模板
│   └── settings.json               # 应用默认配置
│
├── scripts/                         # 构建与测试脚本
│   ├── build-android.bat/sh        # Android APK 构建
│   ├── start-backend.bat/sh        # 启动后端服务
│   ├── run-desktop.bat             # 启动 PC 版
│   └── test-*.html                 # Web 测试页面
│
├── emotion/                         # 表情包素材
└── content/                         # 内容创作工作流
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
| UI | Material Design 3 |
| 网络 | OkHttp 4.12.0 |
| ML | TFLite 2.14 + ONNX Runtime 1.17 + ML Kit |
| Live2D | PixiJS + pixi-live2d-display (WebView) |
| 加密 | androidx-security-crypto |
| 二维码 | ZXing 3.5.2 |

### PC (Tauri)
| 类别 | 技术 |
|------|------|
| 框架 | Tauri 2 + Rust 2021 edition |
| 前端 | React 19 + TypeScript + Vite 6 + TailwindCSS 4 |
| UI 组件 | Radix UI + Framer Motion + Zustand |
| Live2D | PixiJS 8 + pixi-live2d-display 0.4 |
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
pnpm tauri dev
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

---

## 安全 & 隐私

- **API Key 安全**: 所有 API Key 存储在本地 SQLite 数据库中，`.gitignore` 已排除所有 `.db` 文件和敏感配置
- **数据最小化**: 屏幕文字不离机，仅传抽象类别标签
- **用户控制**: 所有功能可开关，记忆可删除
- **录音安全**: 显式长按触发，松开即停，不静默录音
- **内容安全**: 多级内容过滤（Off/Low/Medium/High）

---

## 开发路线

### 已完成
- [x] Android 完整功能（32个 Activity / 50+ 子模块）
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
- [ ] PC 端功能对齐 Android
- [ ] iOS 端原生能力适配
- [ ] 虚拟世界 StraCloud 场景引擎完善

### 计划中
- [ ] 多语言国际化 (i18n)
- [ ] 云端同步服务
- [ ] 性能优化与功耗调优
- [ ] 用户账号体系

---

## 许可证

- **项目代码**: MIT License
- **Live2D SDK**: Live2D Proprietary Software License
- **第三方库**: 遵循各自开源协议

---

## 相关链接

- **远程仓库**: https://github.com/SGJ-bb/Stardust
- **Obsidian 知识库**: `E:\obsidian\study\stradust\`
- **项目文档**: 详见 `PROJECT_SUMMARY.md` (Android 详细文档)

---

*最后更新: 2026-06-19*  *版本: v0.2.0*
