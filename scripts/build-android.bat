@echo off
chcp 65001 >nul
echo ========================================
echo  AI桌宠 - Android APK构建脚本
echo ========================================
echo.

cd /d "%~dp0..\android"

REM 检查Java环境
echo [1/5] 检查Java环境...
java -version >nul 2>&1
if errorlevel 1 (
    echo [错误] 未找到Java，请安装JDK 17+
    pause
    exit /b 1
)

REM 检查Android SDK
echo [2/5] 检查Android SDK...
if "%ANDROID_HOME%"=="" (
    echo [错误] ANDROID_HOME环境变量未设置
    echo 请设置ANDROID_HOME指向Android SDK目录
    pause
    exit /b 1
)

REM 检查Gradle
echo [3/5] 检查Gradle...
if not exist "gradlew.bat" (
    echo [提示] 未找到gradlew，使用系统Gradle...
    gradle --version >nul 2>&1
    if errorlevel 1 (
        echo [错误] 未找到Gradle，请安装或下载wrapper
        pause
        exit /b 1
    )
    set GRADLE_CMD=gradle
) else (
    set GRADLE_CMD=gradlew.bat
)

echo [4/5] 开始构建Debug APK...
%GRADLE_CMD% assembleDebug

if errorlevel 1 (
    echo.
    echo [错误] 构建失败！
    echo 请检查错误信息并修复问题
    pause
    exit /b 1
)

echo.
echo [5/5] 构建成功！
echo APK路径: app\build\outputs\apk\debug\app-debug.apk
echo.

REM 检查是否连接了设备
echo 检查设备连接...
adb devices | findstr /V "List" | findstr /V "^$" >nul
if %errorlevel% == 0 (
    echo 检测到设备，正在安装...
    adb install -r app\build\outputs\apk\debug\app-debug.apk
    if %errorlevel% == 0 (
        echo.
        echo 安装成功！请在手机上打开应用
    ) else (
        echo.
        echo 安装失败，请手动安装APK
    )
) else (
    echo 未检测到连接的设备
    echo 请手动安装: app\build\outputs\apk\debug\app-debug.apk
)

echo.
pause
