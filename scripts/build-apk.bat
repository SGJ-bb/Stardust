@echo off
chcp 65001 >nul
echo ========================================
echo  AI桌宠 - Android APK构建脚本
echo ========================================
echo.

REM 设置环境变量
set "JAVA_HOME=F:\android-dev\jdk-17.0.2"
set "ANDROID_HOME=F:\android-dev\android-sdk"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME%\cmdline-tools\latest\bin;%ANDROID_HOME%\platform-tools;%PATH%"

cd /d "%~dp0..\android"

echo [1/5] 验证Java环境...
java -version
if errorlevel 1 (
    echo [错误] Java环境配置失败
    pause
    exit /b 1
)

echo.
echo [2/5] 验证Android SDK...
if not exist "%ANDROID_HOME%\platform-tools\adb.exe" (
    echo [错误] Android SDK未正确安装
    pause
    exit /b 1
)

echo.
echo [3/5] 下载Gradle（使用国内镜像）...
if not exist "gradle\wrapper\gradle-wrapper.jar" (
    echo 正在下载Gradle Wrapper...
    call gradlew.bat --version
)

echo.
echo [4/5] 开始构建Debug APK...
call gradlew.bat assembleDebug --init-script init.gradle --no-daemon

if errorlevel 1 (
    echo.
    echo [错误] 构建失败！
    echo 请检查错误信息
    pause
    exit /b 1
)

echo.
echo [5/5] 构建成功！
echo APK路径: app\build\outputs\apk\debug\app-debug.apk
echo.

REM 检查设备并安装
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
