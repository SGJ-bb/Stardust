@echo off
rem 安装最新一份 ai-companion-desktop-pet-v*.apk 并启动
setlocal enabledelayedexpansion
set "ADB=F:\android-dev\android-sdk\platform-tools\adb.exe"
set "APK="
for /f "delims=" %%f in ('dir /b /o-d "%~dp0..\ai-companion-desktop-pet-v*.apk"') do (
  if not defined APK set "APK=%~dp0..\%%f"
)
if not defined APK (
  echo [ERROR] 没找到 apk
  pause & exit /b 1
)
echo Installing: !APK!
"%ADB%" start-server >nul 2>&1
"%ADB%" wait-for-device
"%ADB%" install -r "!APK!"
"%ADB%" shell am start -n com.aicompanion/com.aicompanion.ui.MainActivity
echo.
echo Done. 模拟器里应该已经打开 app。
