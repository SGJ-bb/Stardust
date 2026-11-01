@echo off
rem 用包含「帧动画层」的源码重新打 debug 包并装进模拟器
rem 注意：debug 包签名和已装的 release 包不同，必须先卸载
setlocal
set "ADB=F:\android-dev\android-sdk\platform-tools\adb.exe"
cd /d "%~dp0"

echo [1/3] building debug apk...
call gradlew.bat assembleDebug
if errorlevel 1 (
  echo [ERROR] build failed
  pause & exit /b 1
)

echo [2/3] uninstall old package (signature differs) ...
"%ADB%" uninstall com.aicompanion >nul 2>&1

echo [3/3] install + launch ...
"%ADB%" install -r "app\build\outputs\apk\debug\app-debug.apk"
"%ADB%" shell am start -n com.aicompanion/com.aicompanion.ui.MainActivity
echo.
echo 装好后在模拟器里点：首页 -> 和角色对话，或找设置里的动作/表情，帧动画会跟着播。
pause
