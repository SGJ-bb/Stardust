#!/bin/bash
# 抓 ANR：启动应用 → 检查是否 ANR → 取主线程栈
set -u
export MSYS_NO_PATHCONV=1
SDK=/f/android-dev/android-sdk
ADB=$SDK/platform-tools/adb.exe

echo "== 起模拟器 =="
cd $SDK/emulator || exit 1
./emulator.exe -avd stradust -gpu host -memory 4096 -cores 4 -no-snapshot -no-audio \
  -netdelay none -netspeed full > /tmp/emu_anr.log 2>&1 &
$ADB start-server >/dev/null 2>&1
for i in $(seq 1 40); do
  S=$($ADB -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$S" = "1" ] && break
  sleep 5
done
echo "== 开机完成 =="

$ADB -s emulator-5554 logcat -c
$ADB -s emulator-5554 shell am force-stop com.aicompanion
$ADB -s emulator-5554 shell am start -n com.aicompanion/.ui.MainActivity >/dev/null 2>&1
echo "== 等 25 秒（不做任何点击）=="
sleep 25

echo "== ANR 关键字 =="
$ADB -s emulator-5554 logcat -d 2>/dev/null | grep -aE "ANR in|Reason:|Input dispatching timed out" | tail -8

echo "== 进程 CPU/状态 =="
$ADB -s emulator-5554 shell "dumpsys activity processes 2>/dev/null | grep -A3 'aicompanion' | head -12"

echo "== 取主线程栈 =="
$ADB -s emulator-5554 root >/dev/null 2>&1
sleep 3
$ADB -s emulator-5554 wait-for-device
TRACE=$($ADB -s emulator-5554 shell "ls -t /data/anr/ 2>/dev/null | head -1" | tr -d '\r')
echo "trace file: $TRACE"
if [ -n "$TRACE" ]; then
  $ADB -s emulator-5554 shell "cat /data/anr/$TRACE" > /tmp/anr.txt 2>/dev/null
  /f/see-through/venv/Scripts/python.exe - <<'PY'
import re
try:
    txt = open('/tmp/anr.txt', encoding='utf-8', errors='ignore').read()
except Exception as e:
    print("读不到:", e); raise SystemExit
i = txt.find('com.aicompanion')
print("文件里提到 aicompanion 的位置:", i)
if i >= 0:
    seg = txt[i:i+3000]
    # 打印 "main" 线程段
    m = re.search(r'"main".*?(?=\n"|\Z)', seg, re.S)
    print((m.group(0)[:1800] if m else seg[:1200]))
PY
fi
echo "== 结束 =="
