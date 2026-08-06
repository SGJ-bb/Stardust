@echo off
chcp 936 >nul 2>&1
title Stradust PC - Tauri Desktop

cd /d "%~dp0"

echo ============================================
echo   Stradust PC - Tauri Desktop Mode
echo ============================================
echo.

where node >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Node.js not found.
    pause
    exit /b 1
)
for /f "tokens=*" %%v in ('node -v') do echo [OK] Node.js %%v

where cargo >nul 2>&1
if %errorlevel% neq 0 (
    echo [WARN] Rust/Cargo not found.
    echo   Install from: https://rustup.rs
    echo.
)

set "PM_CMD=npm"
where pnpm >nul 2>&1
if %errorlevel% equ 0 set "PM_CMD=pnpm"

for /f "tokens=*" %%v in ('%PM_CMD% -v') do echo [OK] Package: %PM_CMD% %%v

if not exist "node_modules" (
    echo.
    echo [INFO] Installing dependencies...
    call %PM_CMD% install
    if %errorlevel% neq 0 (
        echo [ERROR] Install failed.
        pause
        exit /b 1
    )
)

echo.
echo [INFO] Starting Tauri desktop app...
echo [INFO] Press Ctrl+C to stop.
echo.

call %PM_CMD% tauri dev

if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Tauri failed to start.
    echo   Make sure Rust is installed: https://rustup.rs
    pause
)
