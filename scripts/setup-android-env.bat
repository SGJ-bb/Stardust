@echo off
chcp 65001 >nul
echo ========================================
echo  Android开发环境配置脚本
echo ========================================
echo.

set "ANDROID_HOME=F:\android-dev\android-sdk"
set "JAVA_HOME=F:\android-dev\jdk-17.0.2"
set "PATH=%JAVA_HOME%\bin;%ANDROID_HOME\cmdline-tools\latest\bin;%ANDROID_HOME%\platform-tools;%PATH%"

echo [1/5] 配置环境变量...
echo JAVA_HOME=%JAVA_HOME%
echo ANDROID_HOME=%ANDROID_HOME%

echo.
echo [2/5] 验证Java...
java -version

echo.
echo [3/5] 安装Android SDK组件...
echo 使用国内镜像加速...

set "ANDROID_SDK_ROOT=%ANDROID_HOME%"

REM 创建repositories.cfg
echo ### User Sources for Android SDK Manager > "%ANDROID_HOME%\repositories.cfg"
echo count=0 >> "%ANDROID_HOME%\repositories.cfg"

REM 安装必要组件
echo 正在安装 platform-tools...
sdkmanager "platform-tools" --sdk_root="%ANDROID_HOME%"

echo 正在安装 build-tools...
sdkmanager "build-tools;34.0.0" --sdk_root="%ANDROID_HOME%"

echo 正在安装 platforms...
sdkmanager "platforms;android-34" --sdk_root="%ANDROID_HOME%"

echo.
echo [4/5] 验证安装...
echo ADB版本:
adb --version

echo.
echo [5/5] 环境配置完成！
echo.
echo 请运行以下命令设置环境变量：
echo   setx JAVA_HOME "F:\android-dev\jdk-17.0.2"
echo   setx ANDROID_HOME "F:\android-dev\android-sdk"
echo   setx PATH "%%JAVA_HOME%%\bin;%%ANDROID_HOME%%\cmdline-tools\latest\bin;%%ANDROID_HOME%%\platform-tools;%%PATH%%"
echo.
pause
