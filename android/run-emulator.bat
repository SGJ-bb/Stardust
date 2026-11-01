@echo off
rem 启动安卓模拟器（stradust AVD，硬件 GPU + WHPX）
setlocal
set "ANDROID_SDK_ROOT=F:\android-dev\android-sdk"
set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
if not exist "%ANDROID_SDK_ROOT%\emulator\emulator.exe" (
  echo [ERROR] emulator not found: %ANDROID_SDK_ROOT%\emulator\emulator.exe
  pause & exit /b 1
)
echo Starting emulator (AVD: stradust)...
rem 不写 -no-snapshot：优先从上次快照快速启动（第一次约 70s，之后十几秒）
start "" "%ANDROID_SDK_ROOT%\emulator\emulator.exe" -avd stradust -gpu host -memory 4096 -cores 4 -no-audio
echo.
echo 等窗口出现后再跑 install-latest-apk.bat 装包。
