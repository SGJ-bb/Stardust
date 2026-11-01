#!/bin/bash
# 验证常驻监听：打开设置里的语音唤醒 → 启动服务 → 采集循环是否正常起跑（不依赖麦克风有声音）
set -u
export MSYS_NO_PATHCONV=1
export ANDROID_SDK_ROOT=F:/android-dev/android-sdk
export ANDROID_HOME=F:/android-dev/android-sdk
SDK=/f/android-dev/android-sdk
ADB=$SDK/platform-tools/adb.exe

echo "== 起模拟器 =="
cd $SDK/emulator || exit 1
./emulator.exe -avd stradust -gpu host -memory 4096 -cores 4 -no-snapshot -no-audio \
  -netdelay none -netspeed full > /tmp/emu_live.log 2>&1 &

$ADB start-server >/dev/null 2>&1
BOOT=""
for i in $(seq 1 40); do
  BOOT=$($ADB -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$BOOT" = "1" ] && { echo "== 开机完成 =="; break; }
  sleep 5
done
[ "$BOOT" = "1" ] || { echo "[FAIL] 没起来"; exit 1; }

echo "== 安装最新 APK（必须，否则验证的是旧包）=="
$ADB -s emulator-5554 install -r "F:/stradust/android/app/build_v3/outputs/apk/debug/app-debug.apk" 2>&1 | tail -1

echo "== 打开 voice_wake_enabled（直接写 prefs，避免 UI 点击）=="
printf '%s\n' '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>' \
  '<map>' \
  '    <boolean name="voice_wake_enabled" value="true" />' \
  '    <string name="voice_wake_words">星尘星尘,你好星尘</string>' \
  '    <float name="voice_wake_threshold" value="0.25" />' \
  '</map>' > /f/stradust/android/tools/_companion_settings.xml
# 注意：MSYS_NO_PATHCONV=1 时 adb 收不到 Unix 路径，本地路径必须写 Windows 风格
$ADB -s emulator-5554 push "F:/stradust/android/tools/_companion_settings.xml" /data/local/tmp/companion_settings.xml 2>&1 | tail -1
$ADB -s emulator-5554 shell ls -l /data/local/tmp/companion_settings.xml
$ADB -s emulator-5554 root >/dev/null 2>&1
sleep 4
$ADB -s emulator-5554 wait-for-device
AUID=$($ADB -s emulator-5554 shell stat -c %u /data/data/com.aicompanion | tr -d '\r')
$ADB -s emulator-5554 shell "cat /data/local/tmp/companion_settings.xml > /data/data/com.aicompanion/shared_prefs/companion_settings.xml; chown $AUID:$AUID /data/data/com.aicompanion/shared_prefs/companion_settings.xml; chmod 660 /data/data/com.aicompanion/shared_prefs/companion_settings.xml; restorecon /data/data/com.aicompanion/shared_prefs/companion_settings.xml"
echo "uid=$AUID"
$ADB -s emulator-5554 shell "cat /data/data/com.aicompanion/shared_prefs/companion_settings.xml" | head -3

echo "== 给录音权限 + 启动界面（onResume 会同步服务）=="
$ADB -s emulator-5554 shell pm grant com.aicompanion android.permission.RECORD_AUDIO
$ADB -s emulator-5554 logcat -c
$ADB -s emulator-5554 shell am force-stop com.aicompanion
$ADB -s emulator-5554 shell am start -n com.aicompanion/.ui.MainActivity 2>&1 | tail -1
sleep 25

echo "== 服务状态 =="
$ADB -s emulator-5554 shell dumpsys activity services com.aicompanion 2>/dev/null | grep -E "ServiceRecord|isForeground|foregroundId" | head -6

echo "== 通知 =="
$ADB -s emulator-5554 shell dumpsys notification --noredact 2>/dev/null | grep -iE "voice_wake|语音唤醒" | head -5

echo "== 日志 =="
$ADB -s emulator-5554 logcat -d -s WakeWordEngine VoiceWakeService PinyinConverter AndroidRuntime AudioRecord 2>/dev/null | tail -25
echo "== 结束 =="
