#!/bin/bash
# 用比例坐标点进引导列表 + 首个教程详情，各截一张图
set -u
export MSYS_NO_PATHCONV=1
SDK=/f/android-dev/android-sdk
ADB=$SDK/platform-tools/adb.exe

echo "== 起模拟器 =="
cd $SDK/emulator || exit 1
./emulator.exe -avd stradust -gpu host -memory 4096 -cores 4 -no-snapshot -no-audio \
  -netdelay none -netspeed full > /tmp/emu_guide2.log 2>&1 &
$ADB start-server >/dev/null 2>&1
for i in $(seq 1 40); do
  S=$($ADB -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$S" = "1" ] && break
  sleep 5
done

SIZE=$($ADB -s emulator-5554 shell wm size | tr -d '\r' | sed 's/.*: //')
W=$(echo $SIZE | cut -dx -f1)
H=$(echo $SIZE | cut -dx -f2)
echo "屏幕 $W x $H"

tap_ratio() {  # x_ratio y_ratio
  local x=$(python -c "print(int($W*$1))" 2>/dev/null || echo 540)
  local y=$(python -c "print(int($H*$2))" 2>/dev/null || echo 300)
  $ADB -s emulator-5554 shell input tap $x $y
  sleep 2
}

$ADB -s emulator-5554 shell am force-stop com.aicompanion
$ADB -s emulator-5554 shell am start -n com.aicompanion/.ui.MainActivity >/dev/null 2>&1
sleep 8
tap_ratio 0.95 0.05      # 设置入口（右上角）
sleep 2
tap_ratio 0.50 0.12      # 功能引导卡片
sleep 2
$ADB -s emulator-5554 shell screencap -p /sdcard/g1.png
$ADB -s emulator-5554 pull /sdcard/g1.png "F:/stradust/android/tools/guide_list.png" 2>&1 | tail -1

tap_ratio 0.50 0.20      # 第一条：语音唤醒
sleep 3
$ADB -s emulator-5554 shell screencap -p /sdcard/g2.png
$ADB -s emulator-5554 pull /sdcard/g2.png "F:/stradust/android/tools/guide_step.png" 2>&1 | tail -1
echo "== 结束 =="
