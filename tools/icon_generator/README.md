# 图标批量生成工具

## 文件结构

```
icon_generator/
├── icon_generator.py      # 主脚本
├── icon_prompts.json       # 78个图标提示词数据（已内置）
├── .env.example            # 配置模板
└── generated_icons/        # 输出目录（运行后自动创建）
    ├── png/                # PNG 图片 (1024x1024)
    ├── svg/                # SVG 矢量图（可选转换）
    └── _generation_report.json  # 生成报告
```

## 快速开始

### 1. 配置 API

```bash
cd tools/icon_generator
copy .env.example .env
# 编辑 .env 填入你的 API Key 和地址
```

### 2. 安装依赖（可选）

```bash
# SVG 转换需要（可选）
pip install vtracer        # 推荐：高质量矢量化
# 或
# 系统安装 potrace + ImageMagick（convert命令）
```

### 3. 运行

```bash
# 生成全部 78 个图标
python icon_generator.py

# 只生成核心入口图标（3个）
python icon_generator.py --category core

# 只生成指定 ID
python icon_generator.py --id 1 2 3 4 5

# 列出所有图标
python icon_generator.py --list

# 预览不执行
python icon_generator.py --dry-run

# 强制重新生成已有图标
python icon_generator.py --force
```

## 支持的 API 格式

本工具兼容 **OpenAI Images API** 格式，以下平台均可使用：

| 平台 | model 参数示例 | 说明 |
|------|---------------|------|
| OpenAI 官方 | `dall-e-3` | 质量最高 |
| 智谱 AI | `cogview-4` | 兼容 OpenAI 格式 |
| 硅基流动 | `stable-diffusion-xl` | 性价比高 |
| DeepSeek | `deepseek-vl` | 如支持图片生成 |
| OneAPI / NewAPI 中转 | 视后端而定 | 统一管理多模型 |

## .env 配置说明

```
IMAGE_API_BASE=https://api.openai.com/v1   # API 地址
IMAGE_API_KEY=sk-xxx                        # 你的密钥
IMAGE_MODEL=dall-e-3                        # 模型名
IMAGE_SIZE=1024x1024                        # 尺寸
IMAGE_QUALITY=standard                      # 质量 (dall-e-3)
IMAGE_STYLE=vivid                           # 风格 (dall-e-3)
CONCURRENCY=2                               # 并发数
MAX_RETRIES=3                               # 重试次数
```

## 图标分类

| 分类 ID | 名称 | 数量 | 优先级 |
|---------|------|------|--------|
| core | 核心功能入口 | 3 | 最高 |
| chat | 聊天交互 | 13 | 高 |
| profile | 个人资料统计 | 6 | 高 |
| toolbar | 页面标题栏 | 7 | 中 |
| virtual_world | 虚拟世界模块 | 10 | 中 |
| achievement | 成就系统徽章 | 18 | 低 |
| functional | 其他功能性图标 | 21 | 低 |
