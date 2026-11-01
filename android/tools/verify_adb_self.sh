#!/bin/bash
# 验证自建 ADB 通道：起机 → 装包 → adb tcpip → 授权应用公钥 → 应用自测（应拿到 uid=2000(shell)）
set -u
export MSYS_NO_PATHCONV=1
SDK=/f/android-dev/android-sdk
ADB=$SDK/platform-tools/adb.exe
APK="F:/stradust/android/app/build_v3/outputs/apk/debug/app-debug.apk"

echo "== 起模拟器 =="
cd $SDK/emulator || exit 1
./emulator.exe -avd stradust -gpu host -memory 4096 -cores 4 -no-snapshot -no-audio \
  -netdelay none -netspeed full > /tmp/emu_adb.log 2>&1 &
$ADB start-server >/dev/null 2>&1
BOOT=""
for i in $(seq 1 40); do
  BOOT=$($ADB -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
  [ "$BOOT" = "1" ] && { echo "== 开机完成 =="; break; }
  sleep 5
done
[ "$BOOT" = "1" ] || { echo "[FAIL] 模���器没起来"; exit 1; }

echo "== 安装 =="
$ADB -s emulator-5554 install -r "$APK" 2>&1 | tail -1

echo "== adbd 切 TCP =="
$ADB -s emulator-5554 tcpip 5555 2>&1 | tail -1
sleep 3
$ADB -s emulator-5554 shell "netstat -ln 2>/dev/null | grep 5555" | head -3
$ADB -s emulator-5554 wait-for-device

echo "== 第一次启动应用：导出公钥 =="
$ADB -s emulator-5554 logcat -c
$ADB -s emulator-5554 shell am force-stop com.aicompanion
$ADB -s emulator-5554 shell am start -n com.aicompanion/.ui.MainActivity --es adb_test 5555 >/dev/null 2>&1
sleep 12
KEY=$($ADB -s emulator-5554 logcat -d -s AdbSelfTest 2>/dev/null | grep -o "keyLine=.*" | tail -1 | sed 's/keyLine=//' | tr -d '\r')
echo "公钥长度: ${#KEY}"
echo "公钥前 60 字符: ${KEY:0:60}"

if [ ${#KEY} -lt 100 ]; then
  echo "[FAIL] 没拿到公钥，打印日志："
  $ADB -s emulator-5554 logcat -d -s AdbSelfTest AdbKey AdbConnection 2>/dev/null | tail -20
  exit 1
fi

echo "== 授权：写入 /data/misc/adb/adb_keys =="
$ADB -s emulator-5554 root >/dev/null 2>&1
sleep 3
$ADB -s emulator-5554 wait-for-device
printf '%s\n' "$KEY" > /f/stradust/android/tools/_adb_pub.txt
$ADB -s emulator-5554 push "F:/stradust/android/tools/_adb_pub.txt" /data/local/tmp/k.txt 2>&1 | tail -1
$ADB -s emulator-5554 shell "mkdir -p /data/misc/adb; touch /data/misc/adb/adb_keys; grep -qF \"$KEY\" /data/misc/adb/adb_keys || cat /data/local/tmp/k.txt >> /data/misc/adb/adb_keys; chmod 640 /data/misc/adb/adb_keys; chown system:shell /data/misc/adb/adb_keys 2>/dev/null; wc -l /data/misc/adb/adb_keys"
$ADB -s emulator-5554 shell "setprop ctl.restart adbd" >/dev/null 2>&1
sleep 6
$ADB -s emulator-5554 wait-for-device
$ADB -s emulator-5554 tcpip 5555 >/dev/null 2>&1
sleep 2

echo "== 第二次启动应用：连接 + 执行 id =="
$ADB -s emulator-5554 logcat -c
$ADB -s emulator-5554 shell am force-stop com.aicompanion
$ADB -s emulator-5554 shell am start -n com.aicompanion/.ui.MainActivity --es adb_test 5555 >/dev/null 2>&1
sleep 15
echo "---- AdbSelfTest 日志 ----"
$ADB -s emulator-5554 logcat -d -s AdbSelfTest AdbConnection AdbSelf 2>/dev/null | tail -20
echo "== 结束 =="
