#!/bin/bash
echo "========================================"
echo " AI Companion Desktop Pet - 启动脚本"
echo "========================================"
echo

cd "$(dirname "$0")/../backend" || exit 1

echo "检查Python环境..."
if ! command -v python3 &> /dev/null; then
    echo "[错误] 未找到Python3，请先安装Python 3.9+"
    exit 1
fi

echo "创建虚拟环境..."
if [ ! -d "venv" ]; then
    python3 -m venv venv
fi

source venv/bin/activate

echo "安装依赖..."
pip install -r requirements.txt -q

echo
echo "检查配置文件..."
if [ ! -f "../config/.env" ]; then
    echo "[提示] 未找到.env配置文件，从.env.example复制..."
    cp ../config/.env.example ../config/.env
    echo "[提示] 请编辑 config/.env 文件填入你的API密钥"
fi

echo
echo "启动后端服务..."
echo "API地址: http://localhost:8000"
echo "API文档: http://localhost:8000/docs"
echo
echo "按 Ctrl+C 停止服务"
echo

python main.py
