# Build and run EkmProbe: reproduces BC TLS1.3 EKM behaviour locally (no device needed).
# Usage:  powershell -ExecutionPolicy Bypass -File run.ps1
$ErrorActionPreference = "Stop"
$bc = Join-Path $env:USERPROFILE ".gradle\caches\modules-2\files-2.1\org.bouncycastle"
$cp = ((Get-ChildItem -Path $bc -Recurse -Filter "*1.78.1.jar" | ForEach-Object { $_.FullName }) -join ';')
Set-Location $PSScriptRoot
javac -encoding UTF-8 -cp $cp -d . EkmProbe.java
java -cp "$cp;." EkmProbe
