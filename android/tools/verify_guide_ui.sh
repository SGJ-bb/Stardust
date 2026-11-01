#!/bin/bash
# 在模拟器里真的点进「功能引导」并截图（验证 UI 渲染，而不是只看编译通过）
set -u
export MSYS_NO_PATHCONV=1
SDK=/f/android-dev/android-sdk
ADB=$SDK/platform-tools/adb.exe
APK="F:/stradust/android/app/build_v3/outputs/apk/debug/app-debug.apk"

echo "== 起模拟器 =="
cd $SDK/emulator || exit 1
./emulator.exe -avd stradust -gpu host -memory 4096 -cores 4 -no-snapshot -no-audio \
  -netdelay none -netspeed full > /tmp/emu_guide.log 2>&1 &
$ADB start-server >/dev/null 2>&1
BOOT=""
for i in $(seq 1 40); do
  BOOT=$($ADB -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$BOOT" = "1" ] && { echo "== 开机完成 =="; break; }
  sleep 5
done
[ "$BOOT" = "1" ] || { echo "[FAIL] 没起来"; exit 1; }

echo "== 安装 =="
$ADB -s emulator-5554 install -r "$APK" 2>&1 | tail -1
$ADB -s emulator-5554 shell am force-stop com.aicompanion
$ADB -s emulator-5554 shell am start -n com.aicompanion/.ui.MainActivity >/dev/null 2>&1
sleep 8

tap_text() {
  local target="$1"
  $ADB -s emulator-5554 shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  $ADB -s emulator-5554 shell cat /sdcard/ui.xml > /tmp/ui.xml 2>/dev/null
  local xy
  xy=$(/f/see-through/venv/Scripts/python.exe - "$target" <<'PY'
import re, sys
target = sys.argv[1]
try:
    xml = open('/tmp/ui.xml', encoding='utf-8', errors='ignore').read()
except Exception:
    print(""); raise SystemExit
best = None
for m in re.finditer(r'<node[^>]*>', xml):
    tag = m.group(0)
    text = re.search(r'text="([^"]*)"', tag)
    desc = re.search(r'content-desc="([^"]*)"', tag)
    label = (text.group(1) if text else "") + "|" + (desc.group(1) if desc else "")
    if target in label:
        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        if b:
            x1, y1, x2, y2 = map(int, b.groups())
            best = ((x1 + x2) // 2, (y1 + y2) // 2)
            break
print(f"{best[0]} {best[1]}" if best else "")
PY
)
  if [ -z "$xy" ]; then
    echo "    [miss] 没找到「$target」"
    return 1
  fi
  echo "    tap「$target」→ $xy"
  $ADB -s emulator-5554 shell input tap $xy
  sleep 2
  return 0
}

echo "== 进设置 =="
tap_text "设置" || tap_text "Settings" || echo "  （用坐标兜底）" && $ADB -s emulator-5554 shell input tap 1030 120
sleep 2

echo "== 点「功能引导」=="
tap_text "功能引导"
sleep 2

echo "== 当前页面文字（应能看到引导条目）=="
$ADB -s emulator-5554 shell uiautomator dump /sdcard/ui2.xml >/dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/ui2.xml > /tmp/ui2.xml 2>/dev/null
/f/see-through/venv/Scripts/python.exe - <<'PY'
import re
xml = open('/tmp/ui2.xml', encoding='utf-8', errors='ignore').read()
labels = [t for t in re.findall(r'text="([^"]+)"', xml)]
print("  页面文字:", " / ".join(labels[:14]))
hit = [x for x in labels if any(k in x for k in ("语音唤醒", "无障碍", "命令通道", "操作手机", "功能引导"))]
print("  命中引导条目:", hit)
PY

echo "== 截图 =="
$ADB -s emulator-5554 shell screencap -p /sdcard/guide.png
$ADB -s emulator-5554 pull /sdcard/guide.png "F:/stradust/android/tools/guide_list.png" 2>&1 | tail -1

echo "== 点第一条「语音唤醒」看详情 =="
tap_text "语音唤醒"
sleep 2
$ADB -s emulator-5554 shell screencap -p /sdcard/guide2.png
$ADB -s emulator-5554 pull /sdcard/guide2.png "F:/stradust/android/tools/guide_step.png" 2>&1 | tail -1
$ADB -s emulator-5554 shell uiautomator dump /sdcard/ui3.xml >/dev/null 2>&1
$ADB -s emulator-5554 shell cat /sdcard/ui3.xml > /tmp/ui3.xml 2>/dev/null
/f/see-through/venv/Scripts/python.exe - <<'PY'
import re
xml = open('/tmp/ui3.xml', encoding='utf-8', errors='ignore').read()
labels = [t for t in re.findall(r'text="([^"]+)"', xml)]
print("  详情页文字:", " / ".join(labels[:12]))
PY
echo "== 结束 =="
