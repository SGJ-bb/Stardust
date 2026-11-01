#!/bin/bash
# 一次性验证：起模拟器 → 装 APK → 用 assets 里的 wav 跑唤醒自测 → 打日志
# 全流程在同一个后台任务里，避免模拟器被会话回收
set -u
export MSYS_NO_PATHCONV=1
export ANDROID_SDK_ROOT=F:/android-dev/android-sdk
export ANDROID_HOME=F:/android-dev/android-sdk

SDK=/f/android-dev/android-sdk
ADB=$SDK/platform-tools/adb.exe
APK="F:/stradust/android/app/build_v3/outputs/apk/debug/app-debug.apk"

echo "== 启动模拟器 =="
cd $SDK/emulator || exit 1
./emulator.exe -avd stradust -gpu host -memory 4096 -cores 4 -no-snapshot -no-audio \
  -netdelay none -netspeed full > /tmp/emu_verify.log 2>&1 &
ALIVE=1

$ADB start-server >/dev/null 2>&1
BOOT=""
for i in $(seq 1 40); do
  BOOT=$($ADB -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  if [ "$BOOT" = "1" ]; then echo "== 开机完成（第 $i 次探测）=="; break; fi
  sleep 5
done
if [ "$BOOT" != "1" ]; then echo "[FAIL] 模拟器没起来"; tail -5 /tmp/emu_verify.log; exit 1; fi

echo "== 安装 APK =="
$ADB -s emulator-5554 install -r "$APK" 2>&1 | tail -2

echo "== 检查服务是否声明 =="
$ADB -s emulator-5554 shell dumpsys package com.aicompanion 2>/dev/null | grep -i "VoiceWakeService" | head -2

echo "== 触发唤醒自测（assets/kws/selftest.wav = 「星尘星尘」）=="
$ADB -s emulator-5554 logcat -c
$ADB -s emulator-5554 shell am force-stop com.aicompanion
$ADB -s emulator-5554 shell am start -n com.aicompanion/.ui.MainActivity \
  --es voice_wake_test_asset kws/selftest.wav 2>&1 | tail -2
sleep 30

echo "== 日志 =="
$ADB -s emulator-5554 logcat -d -s VoiceWakeTest VoiceWakeService WakeWordEngine PinyinConverter AndroidRuntime 2>/dev/null | tail -40
echo "== 结束 =="
