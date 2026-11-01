@echo off
chcp 65001 >nul
cd /d F:\stradust
echo ============================================
echo  Open Xianyu login window. Click [Login],
echo  scan QR with the Xianyu app, confirm.
echo  Cookie is saved automatically on success.
echo  This window closes after 15 min timeout.
echo ============================================
echo.
"C:\Users\SGJ\AppData\Roaming\TRAE SOLO CN\ModularData\ai-agent\vm\tools\python\python.exe" -u _final_login.py
echo.
echo ---- finished, press any key ----
pause >nul
