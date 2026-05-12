# AI桌宠伴侣 (AI Companion Desktop Pet) - 项目总结

## 项目概述

**AI桌宠伴侣** 是一款基于Live2D的智能AI桌宠应用，采用"云端重智能、本地轻表现与感知"混合架构。用户可以在手机上养一只名为"星尘"的异色瞳黑猫AI伴侣，它具备智能对话、情感表达、屏幕感知、语音交互等丰富功能。

---

## 技术架构

### 总体架构
```
云端重智能 + 本地轻表现与感知
```

### 架构图
```
┌─────────────────────────────────────────────────────────────┐
│                        云端后端 (Python/FastAPI)              │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ LLM服务      │  │ 记忆引擎     │  │ 语音服务             │  │
│  │ (GPT-4o)    │  │ (Mem0)      │  │ (Azure TTS)         │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ 提示词构建   │  │ 健康检查     │  │ 记忆管理API          │  │
│  │ (分层注入)   │  │ (性能监控)   │  │ (CRUD操作)          │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                              ↑ HTTPS/WebSocket
┌─────────────────────────────────────────────────────────────┐
│                     Android客户端 (Kotlin)                    │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ Live2D渲染   │  │ 悬浮窗系统   │  │ 聊天界面             │  │
│  │ (Cubism SDK)│  │ (触摸穿透)   │  │ (Material Design)   │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ 语音模块     │  │ 屏幕感知     │  │ 记忆管理             │  │
│  │ (ASR/TTS)   │  │ (无障碍服务)  │  │ (本地缓存+云端同步)   │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ 主动互动引擎 │  │ 离线NLP     │  │ 设置面板             │  │
│  │ (规则引擎)   │  │ (TinyBERT)  │  │ (功能开关)           │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 后端服务 (Python/FastAPI)

### 核心模块

| 模块 | 文件 | 功能 |
|------|------|------|
| **LLM服务** | `app/services/llm_service.py` | 调用GPT-4o/mini，JSON结构化输出 |
| **记忆引擎** | `app/services/memory_service.py` | Mem0集成，跨会话记忆持久化 |
| **语音服务** | `app/services/voice_service.py` | Azure TTS + 本地TTS，情感变调 |
| **提示词构建** | `app/services/prompt_builder.py` | 分层提示词（人格+记忆+世界书） |
| **API路由** | `app/api/` | RESTful API（聊天、记忆、健康） |

### API接口

| 接口 | 方法 | 说明 |
|------|------|------|
| `/api/v1/chat/send` | POST | 发送消息获取AI回复 |
| `/api/v1/chat/voice` | POST | 发送消息获取语音回复 |
| `/api/v1/memory/list/{user_id}` | GET | 获取用户记忆列表 |
| `/api/v1/memory/delete` | DELETE | 删除记忆（单条/全部） |
| `/api/v1/memory/daily-card/{user_id}` | GET | 生成每日记忆卡片 |
| `/api/v1/health/status` | GET | 健康检查 |

### 技术选型
- **框架**: FastAPI + Uvicorn
- **LLM**: OpenAI GPT-4o / GPT-4o-mini
- **记忆**: Mem0 (开源记忆层)
- **语音**: Azure Speech Services / pyttsx3
- **配置**: Pydantic Settings + .env

---

## Android客户端 (Kotlin)

### 核心模块

| 模块 | 文件 | 功能 |
|------|------|------|
| **Live2D渲染** | `live2d/Live2DRenderer.kt` | Cubism SDK for Native集成 |
| **模型管理** | `live2d/ModelManager.kt` | 模型导入、切换、配置 |
| **悬浮窗** | `overlay/OverlayWindow.kt` | 全局悬浮窗，触摸穿透处理 |
| **聊天界面** | `ui/MainActivity.kt` | Material Design聊天UI |
| **设置面板** | `ui/SettingsActivity.kt` | 功能开关、参数配置 |
| **记忆管理** | `ui/MemoryActivity.kt` | 记忆查看、删除 |
| **语音模块** | `voice/VoiceManager.kt` | ASR/TTS集成 |
| **屏幕感知** | `screen/ScreenRecognitionService.kt` | 无障碍服务识别App类别 |
| **主动互动** | `interaction/ProactiveInteractionEngine.kt` | 规则引擎驱动主动对话 |
| **离线NLP** | `nlp/OfflineNLP.kt` | 本地意图识别 |

### 界面结构

```
MainActivity (聊天主界面)
├── 顶部状态栏（角色头像、情感、动作）
├── 聊天消息列表（RecyclerView）
├── 底部输入区（文字+语音）
└── 右上角设置按钮

SettingsActivity (设置界面)
├── 服务配置（服务器地址、用户ID）
├── 功能开关（屏幕识别、语音、TTS、离线模式）
├── 交互设置（唠叨频率、语言风格）
├── Live2D模型管理
├── 记忆管理
└── 关于信息

MemoryActivity (记忆管理)
├── 记忆列表（长按删除）
└── 一键清空

ModelManagerActivity (模型管理)
├── 当前模型展示
├── 导入模型（文件夹选择）
├── 扫描本地模型
└── 模型列表（切换/编辑/删除）
```

### 技术选型
- **语言**: Kotlin
- **UI**: Material Design 3 + Material Components
- **网络**: OkHttp + Kotlin Coroutines
- **渲染**: Live2D Cubism SDK for Native (OpenGL ES)
- **存储**: SharedPreferences + Room (可选)

---

## 项目目录结构

```
ai-companion-desktop-pet/
├── backend/                          # 云端后端
│   ├── app/
│   │   ├── api/                      # API路由
│   │   │   ├── chat.py               # 聊天接口
│   │   │   ├── memory.py             # 记忆接口
│   │   │   └── health.py             # 健康检查
│   │   ├── core/
│   │   │   └── config.py             # 核心配置
│   │   ├── models/
│   │   │   └── schemas.py            # 数据模型
│   │   ├── services/
│   │   │   ├── llm_service.py        # LLM服务
│   │   │   ├── memory_service.py     # 记忆服务
│   │   │   ├── voice_service.py      # 语音服务
│   │   │   └── prompt_builder.py     # 提示词构建
│   │   └── utils/
│   ├── tests/
│   │   └── test_api.py               # API测试
│   ├── main.py                       # 入口文件
│   └── requirements.txt              # 依赖列表
│
├── android/                          # Android客户端
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/aicompanion/
│   │   │   │   ├── ui/               # UI组件
│   │   │   │   │   ├── MainActivity.kt
│   │   │   │   │   ├── SettingsActivity.kt
│   │   │   │   │   ├── MemoryActivity.kt
│   │   │   │   │   ├── ModelManagerActivity.kt
│   │   │   │   │   ├── ModelSettingsActivity.kt
│   │   │   │   │   ├── WebTestActivity.kt
│   │   │   │   │   └── ChatAdapter.kt
│   │   │   │   ├── live2d/           # Live2D渲染
│   │   │   │   │   ├── Live2DRenderer.kt
│   │   │   │   │   ├── Live2DModel.kt
│   │   │   │   │   ├── ModelManager.kt
│   │   │   │   │   └── Live2DWebView.kt
│   │   │   │   ├── overlay/          # 悬浮窗
│   │   │   │   │   ├── OverlayWindow.kt
│   │   │   │   │   └── OverlayTouchHandler.kt
│   │   │   │   ├── services/         # 后台服务
│   │   │   │   │   └── OverlayService.kt
│   │   │   │   ├── voice/            # 语音模块
│   │   │   │   │   ├── VoiceManager.kt
│   │   │   │   │   └── OfflineASREngine.kt
│   │   │   │   ├── screen/           # 屏幕感知
│   │   │   │   │   ├── AppCategoryClassifier.kt
│   │   │   │   │   └── ScreenRecognitionService.kt
│   │   │   │   ├── memory/           # 记忆管理
│   │   │   │   │   └── MemoryManager.kt
│   │   │   │   ├── interaction/      # 主动互动
│   │   │   │   │   └── ProactiveInteractionEngine.kt
│   │   │   │   ├── nlp/              # 离线NLP
│   │   │   │   │   └── OfflineNLP.kt
│   │   │   │   ├── models/           # 数据模型
│   │   │   │   │   ├── Models.kt
│   │   │   │   │   └── EmotionActionMapper.kt
│   │   │   │   ├── network/          # 网络通信
│   │   │   │   │   └── ApiClient.kt
│   │   │   │   └── settings/         # 设置管理
│   │   │   │       └── SettingsManager.kt
│   │   │   ├── res/                  # 资源文件
│   │   │   │   ├── layout/           # 布局文件
│   │   │   │   ├── drawable/         # 图形资源
│   │   │   │   └── xml/              # XML配置
│   │   │   └── AndroidManifest.xml   # 应用清单
│   │   └── build.gradle              # 模块构建配置
│   ├── build.gradle                  # 项目构建配置
│   ├── settings.gradle               # 项目设置
│   ├── gradle.properties             # Gradle属性
│   └── gradle/wrapper/               # Gradle Wrapper
│
├── config/                           # 配置文件
│   ├── .env.example                  # 环境变量模板
│   └── settings.json                 # 应用配置
│
├── scripts/                          # 脚本工具
│   ├── start-backend.bat             # 启动后端(Windows)
│   ├── start-backend.sh              # 启动后端(Linux/Mac)
│   ├── build-android.bat             # 构建APK(Windows)
│   ├── build-android.sh              # 构建APK(Linux/Mac)
│   ├── setup-android-env.bat         # 配置Android环境
│   ├── test-ui.html                  # Web测试界面(基础)
│   ├── test-phone-ui.html            # Web测试界面(完整)
│   ├── test-model-manager.html       # 模型管理测试
│   └── test-live2d-preview.html      # Live2D预览测试
│
├── docs/                             # 文档
└── README.md                         # 项目说明
```

---

## 核心功能实现

### 1. 智能对话系统
- **云端LLM**: GPT-4o/mini，JSON结构化输出
- **动态提示词**: 基础人格 + 记忆注入 + 实时世界书
- **模型分层**: 日常闲聊用低成本模型，复杂推理用高端模型
- **成本优化**: 本地缓存高频回复，每日请求软上限

### 2. 情感-表现同步
- **情感映射**: happy/angry/sad/surprised/tsundere/neutral
- **动作映射**: tail_flick/ear_twitch/blush/stretch/yawn/idle
- **实时驱动**: LLM输出JSON → 客户端解析 → Live2D参数更新
- **语音变调**: 根据情感标签调整音高、语速

### 3. 长期记忆系统
- **云端存储**: Mem0按用户ID组织
- **记忆注入**: 自动提取事实，注入提示词
- **记忆管理**: 查看、逐条删除、一键清空
- **每日卡片**: 自动生成"今日观察日记"

### 4. 屏幕感知与隐私
- **本地处理**: 仅获取App包名，离线分类
- **脱敏传输**: 只传抽象类别标签（社交/视频/办公等）
- **隐私保护**: 屏幕文字不离机，不读取无障碍节点文字
- **开关控制**: 用户可随时关闭屏幕识别

### 5. 语音交互
- **语音识别**: 云端Azure Speech / 离线sherpa-onnx
- **语音合成**: 系统TTS / ChatTTS / Azure TTS
- **情感变调**: 根据情感标签调整语音参数
- **隐私设计**: 长按触发，松开即停，不静默录音

### 6. 主动互动引擎
- **规则驱动**: 基于时间、App类别、电量等触发
- **频率控制**: 每小时最多2-3次，用户可调节
- **离线支持**: 内置TinyBERT意图模型，断网也能互动
- **智能退避**: 电量低/过热时自动降低频率

### 7. Live2D模型管理
- **模型导入**: 支持Cubism 3/4格式，文件夹选择导入
- **模型切换**: 一键切换不同皮套
- **参数配置**: 纹理质量、帧率、动作映射
- **扫描发现**: 自动扫描手机存储中的Live2D模型

---

## 性能目标

| 场景 | CPU | 内存 | 耗电 |
|------|-----|------|------|
| 待机 | 2-4% | ~180MB | ≤1.5%/h |
| 文字对话 | 5-8% | ~200MB | 3-4%/h |
| 离线语音 | 15-20% | ~230MB | 8-10%/h |
| 屏幕感知 | +1-2% | +≤5MB | - |

**保障策略**:
- 电量<20%或过热：自动降频、关闭动画
- 内存>280MB：释放纹理缓存
- 网络容错：指数退避重试

---

## 隐私与合规

- **数据最小化**: 屏幕文字不离机，仅传类别标签
- **用户控制**: 所有功能可开关，记忆可删除
- **透明说明**: 无障碍服务用途明确说明
- **匿名默认**: 默认匿名用户，可升级正式账号
- **录音安全**: 显式长按触发，松开即停

---

## 开发环境配置

### 后端环境
```bash
# 安装依赖
cd backend
pip install -r requirements.txt

# 配置环境变量
cp config/.env.example config/.env
# 编辑 .env 填入API密钥

# 启动服务
python main.py
```

### Android环境
```bash
# 环境已配置在 F:\android-dev\
# JDK 17: F:\android-dev\jdk-17.0.2
# Android SDK: F:\android-dev\android-sdk

# 构建APK
cd scripts
build-android.bat
```

### 环境变量
```bash
setx JAVA_HOME "F:\android-dev\jdk-17.0.2"
setx ANDROID_HOME "F:\android-dev\android-sdk"
setx PATH "%JAVA_HOME%\bin;%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\platform-tools;%PATH%"
```

---

## 测试工具

| 工具 | 文件 | 用途 |
|------|------|------|
| **API测试** | `backend/tests/test_api.py` | pytest自动化测试 |
| **Web模拟器** | `scripts/test-phone-ui.html` | 完整UI交互测试 |
| **模型管理测试** | `scripts/test-model-manager.html` | Live2D模型管理测试 |
| **Live2D预览** | `scripts/test-live2d-preview.html` | PixiJS渲染测试 |
| **构建脚本** | `scripts/build-android.bat` | 一键构建APK |

---

## 开发路线

### 已完成
1. ✅ 项目架构设计
2. ✅ 后端API框架搭建
3. ✅ Android客户端框架搭建
4. ✅ 聊天UI界面
5. ✅ 设置面板
6. ✅ 记忆管理界面
7. ✅ Live2D模型管理
8. ✅ 屏幕感知分类器
9. ✅ 语音模块框架
10. ✅ 主动互动引擎
11. ✅ 离线NLP
12. ✅ 构建环境配置

### 待开发
1. 🔄 Live2D Native SDK集成（需要真实模型文件）
2. 🔄 语音ASR/TTS完整集成
3. 🔄 悬浮窗触摸穿透优化
4. 🔄 屏幕感知无障碍服务调试
5. 🔄 后端LLM API密钥配置
6. 🔄 Mem0记忆引擎对接
7. 🔄 性能优化与功耗调优
8. 🔄 多语言支持
9. 🔄 iOS版本适配

---

## 技术栈总结

| 层级 | 技术 |
|------|------|
| **后端框架** | FastAPI + Python 3.9+ |
| **LLM** | OpenAI GPT-4o/mini |
| **记忆引擎** | Mem0 |
| **语音服务** | Azure Speech / pyttsx3 |
| **Android** | Kotlin + Material Design 3 |
| **Live2D渲染** | Cubism SDK for Native (OpenGL ES) |
| **网络通信** | OkHttp + RESTful API |
| **本地存储** | SharedPreferences + SQLite |
| **构建工具** | Gradle 8.2 |
| **测试框架** | pytest + JUnit |

---

## 项目特色

1. **混合架构**: 云端重智能，本地轻表现，平衡性能与功能
2. **隐私优先**: 屏幕文字不离机，用户数据可控可删
3. **成本优化**: 模型分层调用，高频回复本地缓存
4. **情感同步**: LLM输出直接驱动Live2D表情动作
5. **离线可用**: 核心功能断网也能使用
6. **模型可换**: 支持导入自定义Live2D皮套
7. **开源友好**: 使用开源SDK，遵循各组件协议

---

## 许可证

- **项目代码**: MIT License
- **Live2D SDK**: Live2D Proprietary Software License
- **第三方库**: 遵循各自开源协议

---

## 联系方式

- **项目路径**: `f:\ai-companion-desktop-pet\`
- **后端服务**: `http://localhost:8000`
- **API文档**: `http://localhost:8000/docs`

---

*项目创建日期: 2026-05-06*
*版本: v1.0.0*
