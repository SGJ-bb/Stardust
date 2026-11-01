@echo off
chcp 65001 >nul
echo ========================================
echo   ToolBox Pro 打包脚本
echo ========================================
echo.

echo [1/4] 检查 Python 环境...
python --version
if errorlevel 1 (
    echo [错误] 未找到 Python，请先安装 Python 3.10+
    pause
    exit /b 1
)

echo [2/4] 安装依赖...
pip install -r requirements.txt pyinstaller --quiet
if errorlevel 1 (
    echo [错误] 依赖安装失败
    pause
    exit /b 1
)

echo [3/4] 清理旧文件...
if exist dist rmdir /s /q dist
if exist build rmdir /s /q build

echo [4/4] 开始打包（可能需要几分钟）...
python -m PyInstaller build.spec --noconfirm
if errorlevel 1 (
    echo [错误] 打包失败
    pause
    exit /b 1
)

echo.
echo ========================================
echo   打包完成!
echo   可执行文件: dist\ToolBoxPro.exe
echo ========================================
pause
