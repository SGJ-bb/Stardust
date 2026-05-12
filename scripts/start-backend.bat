@echo off
echo ========================================
echo  AI Companion Desktop Pet - 启动脚本
echo ========================================
echo.

cd /d %~dp0backend

echo 检查Python环境...
python --version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到Python，请先安装Python 3.9+
    pause
    exit /b 1
)

echo 安装依赖...
pip install -r requirements.txt -q

echo.
echo 检查配置文件...
if not exist ..\config\.env (
    echo [提示] 未找到.env配置文件，从.env.example复制...
    copy ..\config\.env.example ..\config\.env
    echo [提示] 请编辑 config\.env 文件填入你的API密钥
    pause
)

echo.
echo 启动后端服务...
echo API地址: http://localhost:8000
echo API文档: http://localhost:8000/docs
echo.
echo 按 Ctrl+C 停止服务
echo.

python main.py

pause
