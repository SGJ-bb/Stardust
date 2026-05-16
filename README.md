# AI 桌宠伴侣 - AI Companion Desktop Pet

一个基于Live2D的智能AI桌宠应用，采用"云端重智能、本地轻表现与感知"混合架构。

## 项目概述

**星尘** - 你的异色瞳黑猫AI桌宠伙伴。

### 核心特性

- **Live2D角色渲染** - 流畅的2D动画表现，支持情感与动作同步
- **云端AI大脑** - 大语言模型驱动的智能对话
- **长期记忆系统** - 跨会话记忆，记住你的喜好和习惯
- **屏幕感知** - 智能识别当前应用类别，适时互动
- **语音交互** - 支持云端/离线语音识别与合成
- **隐私保护** - 屏幕文字不离机，仅传输脱敏类别标签
- **多场景分层** - 日常闲聊与复杂推理使用不同模型，控制成本

## 技术架构

```
ai-companion-desktop-pet/
├── backend/              # 云端后端 (Python/FastAPI)
│   ├── app/
│   │   ├── api/         # REST API路由
│   │   ├── core/        # 核心配置
│   │   ├── models/      # 数据模型
│   │   ├── services/    # 业务服务 (LLM、记忆、语音)
│   │   └── utils/
│   ├── tests/
│   ├── requirements.txt
│   └── main.py
├── android/              # Android客户端 (Kotlin)
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── java/com/aicompanion/
│   │   │   │   ├── ui/          # 控制面板UI
│   │   │   │   ├── live2d/      # Live2D渲染
│   │   │   │   ├── overlay/     # 悬浮窗管理
│   │   │   │   ├── services/    # 后台服务
│   │   │   │   ├── voice/       # 语音模块
│   │   │   │   ├── screen/      # 屏幕感知
│   │   │   │   ├── memory/      # 记忆管理
│   │   │   │   ├── interaction/ # 主动互动
│   │   │   │   ├── nlp/         # 离线NLP
│   │   │   │   ├── models/      # 数据模型
│   │   │   │   ├── network/     # 网络通信
│   │   │   │   └── settings/    # 设置管理
│   │   │   └── res/             # 资源文件
│   │   └── build.gradle
│   └── build.gradle
├── config/               # 配置文件
├── docs/                 # 文档
└── scripts/              # 启动脚本
```

## 功能特性

### 手机端界面
- **主聊天界面** - 仿微信聊天风格，支持文字和语音输入
- **角色状态面板** - 实时显示角色情感状态和动作表现
- **Live2D模型调整** - 支持拖拽移动、双指缩放模型位置，位置配置自动保存
- **模型管理** - 多模型切换、纹理质量与帧率设置
- **设置界面** - 完整的功能开关和参数配置
- **记忆管理界面** - 查看、搜索、删除记忆
- **悬浮窗模式** - 全局悬浮窗，支持触摸穿透和长按交互

### 设置项
| 设置项 | 说明 |
|--------|------|
| 服务器地址 | 后端API服务器URL |
| 模型调整 | 拖拽/缩放调整Live2D角色位置和大小 |
| 模型管理 | 切换和配置Live2D角色模型 |
| 屏幕识别 | 开关无障碍服务识别App类别 |
| 语音识别 | 开关麦克风语音输入 |
| 语音合成 | 开关TTS语音回复播放 |
| 离线模式 | 切换到本地模型 |
| 唠叨频率 | 主动互动频率：关闭/低/中/高 |
| 语言风格 | 正常/毒舌/卖萌 |
| 记忆管理 | 查看、删除记忆 |

### 后端启动

```bash
cd scripts
start-backend.bat  # Windows
# 或
./start-backend.sh # Linux/macOS
```

### 配置API密钥

编辑 `config/.env` 文件，填入你的API密钥：

```env
LLM_API_KEY="your_openai_api_key_here"
MEM0_API_KEY="your_mem0_api_key_here"
AZURE_SPEECH_KEY="your_azure_speech_key_here"
```

### Android构建

```bash
cd android
./gradlew assembleDebug
```

## 技术选型

| 模块 | 技术 |
|------|------|
| 后端框架 | FastAPI + Python |
| LLM | OpenAI GPT-4o/mini |
| 记忆引擎 | Mem0 |
| 云端语音 | Azure Speech |
| 离线ASR | sherpa-onnx / whisper.cpp |
| 本地TTS | pyttsx3 / ChatTTS |
| Android | Kotlin + OpenGL |
| 角色渲染 | Live2D Cubism SDK |

## 性能目标

| 场景 | CPU | 内存 | 耗电 |
|------|-----|------|------|
| 待机 | 2-4% | ~180MB | ≤1.5%/h |
| 文字对话 | 5-8% | ~200MB | 3-4%/h |
| 离线语音 | 15-20% | ~230MB | 8-10%/h |
| 屏幕感知 | +1-2% | +≤5MB | - |

## 隐私保护

- 屏幕文字**绝不离开设备**，仅传输抽象类别标签
- 录音需**显式长按**触发，松开即停止
- 记忆数据支持**一键删除**
- 默认**匿名账户**，可升级为正式账号

## 开发路线

1. [x] 项目架构设计
2. [x] 后端API框架搭建
3. [x] Android客户端框架搭建
4. [x] Live2D渲染集成
5. [ ] 云端LLM对接
6. [ ] 记忆系统完善
7. [ ] 语音模块集成
8. [ ] 屏幕感知分类器
9. [ ] 主动互动引擎
10. [ ] 性能优化与测试

交流群qq：637205564
## 许可证

MIT License
