@echo off
chcp 65001 >nul
echo ========================================
echo  AI桌宠 - 桌面版测试程序
echo ========================================
echo.

cd /d "%~dp0"

REM 检查Python
echo [1/3] 检查Python环境...
python --version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到Python，请安装Python 3.8+
    pause
    exit /b 1
)

REM 检查PyQt5
echo [2/3] 检查PyQt5...
python -c "import PyQt5" >nul 2>&1
if errorlevel 1 (
    echo [提示] 正在安装PyQt5...
    pip install PyQt5 requests -q
)

REM 检查后端服务
echo [3/3] 检查后端服务...
curl -s http://localhost:8001/api/v1/health/status >nul 2>&1
if errorlevel 1 (
    echo [提示] 后端服务未运行，将使用离线模式
    echo 如需启动后端，请运行: start-backend.bat
    echo.
)

echo.
echo 启动桌面版测试程序...
python desktop_test.py

pause
