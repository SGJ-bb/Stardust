@echo off
chcp 936 >nul
setlocal
rem =============================================================================
rem  一次性 ADB 授权（免 root、免装 Shizuku，把能力直接"塞"给应用）
rem
rem  为什么需要电脑/一次 adb：
rem    Android 里 WRITE_SECURE_SETTINGS、使用情况访问、无障碍开关、静默授权这些
rem    都属于 shell(UID 2000) 才有的特权，应用自己无法自我提权（SELinux 直接拒绝）。
rem    所以只有三条路：装 Shizuku、连电脑跑一次 adb、或者只靠无障碍。
rem    本脚本走第二条：跑一次之后，这些权限就常驻在应用上（卸载前一直有效）。
rem
rem  用法：手机开 USB 调试 → 连线 → 双击运行本脚本（或：adb devices 能看到设备后运行）
rem =============================================================================

set PKG=com.aicompanion
set A11Y=%PKG%/com.google.android.accessibility.selecttospeak.SelectToSpeakService
set NLS=%PKG%/%PKG%.notification.NotificationReaderService
set ADB=adb

where %ADB% >nul 2>nul
if errorlevel 1 (
  if exist "F:\android-dev\android-sdk\platform-tools\adb.exe" (
    set ADB=F:\android-dev\android-sdk\platform-tools\adb.exe
  ) else (
    echo [ERROR] 找不到 adb，请把 adb 加入 PATH 或改本脚本里的 ADB 变量。
    if not "%STRA_NO_PAUSE%"=="1" pause & exit /b 1
  )
)

echo == 检查设备 ==
%ADB% devices
rem 只认状态为 device 的行（offline / unauthorized 都不行）
set DEV=
for /f "tokens=1,2" %%A in ('%ADB% devices ^| findstr /r /c:"device$"') do set DEV=%%A
if "%DEV%"=="" (
  echo [ERROR] 没有可用设备：请确认已开 USB 调试，并在手机上点"允许 USB 调试"。
  if not "%STRA_NO_PAUSE%"=="1" pause
  exit /b 1
)
echo    device = %DEV%
%ADB% wait-for-device

echo.
echo == 1/6 运行时权限 ==
for %%P in (
  android.permission.RECORD_AUDIO
  android.permission.CALL_PHONE
  android.permission.READ_CONTACTS
  android.permission.POST_NOTIFICATIONS
  android.permission.READ_EXTERNAL_STORAGE
  android.permission.WRITE_EXTERNAL_STORAGE
  android.permission.SEND_SMS
  android.permission.READ_SMS
  android.permission.READ_PHONE_STATE
) do (
  %ADB% shell pm grant %PKG% %%P >nul 2>nul
)
echo    done（未声明的权限会被跳过，正常）

echo.
echo == 2/6 写系统设置（关键：等价 Shizuku 的 settings put 能力）==
%ADB% shell pm grant %PKG% android.permission.WRITE_SECURE_SETTINGS >nul 2>nul
%ADB% shell pm grant %PKG% android.permission.PACKAGE_USAGE_STATS >nul 2>nul
echo    done

echo.
echo == 3/6 特殊应用权限（AppOps）==
%ADB% shell appops set %PKG% MANAGE_EXTERNAL_STORAGE allow >nul 2>nul
%ADB% shell appops set %PKG% GET_USAGE_STATS allow >nul 2>nul
%ADB% shell appops set %PKG% SYSTEM_ALERT_WINDOW allow >nul 2>nul
%ADB% shell appops set %PKG% PROJECT_MEDIA allow >nul 2>nul
%ADB% shell appops set %PKG% READ_CLIPBOARD_IN_BACKGROUND allow >nul 2>nul
echo    done

echo.
echo == 4/6 无障碍服务（自动打开，省得手动点）==
rem 拼接在设备端完成：避开 Windows 换行符污染设置值
%ADB% shell "CUR=$(settings get secure enabled_accessibility_services); case "$CUR" in *%A11Y%*) echo '   already enabled' ;; *) if [ -z "$CUR" ] || [ "$CUR" = null ]; then settings put secure enabled_accessibility_services %A11Y%; else settings put secure enabled_accessibility_services "$CUR:%A11Y%"; fi; echo '   appended' ;; esac"
%ADB% shell settings put secure accessibility_enabled 1 >nul 2>nul
echo    done

echo.
echo == 5/6 通知使用权 ==
%ADB% shell "CUR=$(settings get secure enabled_notification_listeners); case "$CUR" in *%NLS%*) echo '   already enabled' ;; *) if [ -z "$CUR" ] || [ "$CUR" = null ]; then settings put secure enabled_notification_listeners %NLS%; else settings put secure enabled_notification_listeners "$CUR:%NLS%"; fi; echo '   appended' ;; esac"

echo.
echo == 6/6 电池优化白名单 ==
%ADB% shell dumpsys deviceidle whitelist +%PKG% >nul 2>nul
%ADB% shell cmd deviceidle whitelist +%PKG% >nul 2>nul
echo    done

echo.
echo == 7/7 让 adbd 监听 5555（应用「自建 ADB 通道」要用；重启后需重跑本脚本）==
%ADB% tcpip 5555
echo    提示：非 root 设备无需手动授权公钥 --
echo    在应用里点「连接并测试」，手机上会弹「允许 USB 调试」，点允许即可。

echo.
echo ================= 结果核对 =================
echo [WRITE_SECURE_SETTINGS]
%ADB% shell pm grant %PKG% android.permission.WRITE_SECURE_SETTINGS 2>&1 | findstr /i "Exception" >nul
if errorlevel 1 (echo    granted) else (echo    FAILED: 清单里没声明 WRITE_SECURE_SETTINGS?)
echo [无障碍]
%ADB% shell settings get secure enabled_accessibility_services
echo [通知使用权]
%ADB% shell settings get secure enabled_notification_listeners
echo [所有文件访问]
%ADB% shell appops get %PKG% MANAGE_EXTERNAL_STORAGE
echo [使用情况访问]
%ADB% shell appops get %PKG% GET_USAGE_STATS
echo [电池白名单]
%ADB% shell dumpsys deviceidle whitelist | findstr %PKG%
echo ===========================================

echo.
echo 完成。以后不需要再连电脑；卸载重装后需要重跑一次。
if not "%STRA_NO_PAUSE%"=="1" pause
