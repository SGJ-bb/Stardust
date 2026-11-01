#!/bin/bash
# 验证 Shizuku 接入后：Provider 已注册、应用能正常启动、Shizuku 状态优雅降级
set -u
export MSYS_NO_PATHCONV=1
SDK=/f/android-dev/android-sdk
ADB=$SDK/platform-tools/adb.exe

echo "== 起模拟器 =="
cd $SDK/emulator || exit 1
./emulator.exe -avd stradust -gpu host -memory 4096 -cores 4 -no-snapshot -no-audio \
  -netdelay none -netspeed full > /tmp/emu_shz.log 2>&1 &
$ADB start-server >/dev/null 2>&1
BOOT=""
for i in $(seq 1 40); do
  BOOT=$($ADB -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$BOOT" = "1" ] && { echo "== 开机完成 =="; break; }
  sleep 5
done
[ "$BOOT" = "1" ] || { echo "[FAIL] 模拟器没起来"; exit 1; }

echo "== 安装 =="
$ADB -s emulator-5554 install -r "F:/stradust/android/app/build_v3/outputs/apk/debug/app-debug.apk" 2>&1 | tail -1

echo "== Provider 是否注册 =="
$ADB -s emulator-5554 shell "dumpsys package com.aicompanion | grep -i shizuku" | head -5

echo "== 启动应用（会走 onResume → VoiceWake.sync + ShizukuBridge 探测）=="
$ADB -s emulator-5554 logcat -c
$ADB -s emulator-5554 shell am force-stop com.aicompanion
$ADB -s emulator-5554 shell am start -n com.aicompanion/.ui.MainActivity 2>&1 | tail -1
sleep 20

echo "== 崩溃检查（应为空）=="
$ADB -s emulator-5554 logcat -d -s AndroidRuntime ShizukuBridge 2>/dev/null | grep -E "FATAL|ShizukuBridge" | head -10
echo "== 进程还在吗 =="
$ADB -s emulator-5554 shell pidof com.aicompanion
echo "== 结束 =="
