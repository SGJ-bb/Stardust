#!/bin/bash
# 验证「一次性 adb 授权」脚本：起机 → 跑 bat → 核对每一项是否真的生效
set -u
export MSYS_NO_PATHCONV=1
SDK=/f/android-dev/android-sdk
ADB=$SDK/platform-tools/adb.exe

echo "== 起模拟器 =="
cd $SDK/emulator || exit 1
./emulator.exe -avd stradust -gpu host -memory 4096 -cores 4 -no-snapshot -no-audio \
  -netdelay none -netspeed full > /tmp/emu_elev.log 2>&1 &
$ADB start-server >/dev/null 2>&1
BOOT=""
for i in $(seq 1 40); do
  BOOT=$($ADB -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$BOOT" = "1" ] && { echo "== 开机完成 =="; break; }
  sleep 5
done
[ "$BOOT" = "1" ] || { echo "[FAIL] 没起来"; exit 1; }

echo
echo "== 运行 adb_elevate.bat =="
cd /f/stradust/android/tools
cmd //c "F:\\stradust\\android\\tools\\adb_elevate.bat" < /dev/null 2>&1 | tr -d '\r' | tail -32

echo
echo "== 独立复核 =="
echo -n "无障碍: "; $ADB -s emulator-5554 shell settings get secure enabled_accessibility_services | tr -d '\r'
echo -n "通知使用权: "; $ADB -s emulator-5554 shell settings get secure enabled_notification_listeners | tr -d '\r'
echo -n "所有文件: "; $ADB -s emulator-5554 shell appops get com.aicompanion MANAGE_EXTERNAL_STORAGE | tr -d '\r'
echo -n "使用情况: "; $ADB -s emulator-5554 shell appops get com.aicompanion GET_USAGE_STATS | tr -d '\r'
echo -n "WRITE_SECURE_SETTINGS: "; $ADB -s emulator-5554 shell dumpsys package com.aicompanion | grep -i "WRITE_SECURE_SETTINGS" | head -2 | tr -d '\r'
echo -n "电池白名单: "; $ADB -s emulator-5554 shell dumpsys deviceidle whitelist 2>/dev/null | grep aicompanion | tr -d '\r'
echo -n "无障碍服务是否在跑: "; $ADB -s emulator-5554 shell dumpsys accessibility 2>/dev/null | grep -i "aicompanion" | head -2 | tr -d '\r'
echo "== 结束 =="
