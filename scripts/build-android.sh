#!/bin/bash
echo "========================================"
echo " AI桌宠 - Android APK构建脚本"
echo "========================================"
echo

cd "$(dirname "$0")/../android"

echo "[1/5] 检查Java环境..."
if ! command -v java &> /dev/null; then
    echo "[错误] 未找到Java，请安装JDK 17+"
    exit 1
fi

echo "[2/5] 检查Android SDK..."
if [ -z "$ANDROID_HOME" ]; then
    echo "[错误] ANDROID_HOME环境变量未设置"
    echo "请设置ANDROID_HOME指向Android SDK目录"
    exit 1
fi

echo "[3/5] 检查Gradle..."
if [ -f "./gradlew" ]; then
    GRADLE_CMD="./gradlew"
else
    GRADLE_CMD="gradle"
fi

echo "[4/5] 开始构建Debug APK..."
$GRADLE_CMD assembleDebug

if [ $? -ne 0 ]; then
    echo
    echo "[错误] 构建失败！"
    exit 1
fi

echo
echo "[5/5] 构建成功！"
echo "APK路径: app/build/outputs/apk/debug/app-debug.apk"
echo

echo "检查设备连接..."
if adb devices | grep -q "device$"; then
    echo "检测到设备，正在安装..."
    adb install -r app/build/outputs/apk/debug/app-debug.apk
    if [ $? -eq 0 ]; then
        echo
        echo "安装成功！请在手机上打开应用"
    else
        echo
        echo "安装失败，请手动安装APK"
    fi
else
    echo "未检测到连接的设备"
    echo "请手动安装: app/build/outputs/apk/debug/app-debug.apk"
fi

echo
read -p "按回车键继续..."
