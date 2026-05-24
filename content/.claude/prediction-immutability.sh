$filePath = $env:FILE_PATH
$content = $env:NEW_CONTENT

if ($filePath -match "predictions[\\/]") {
    if ($content -match "##\s*预测" -and (Test-Path $filePath)) {
        $existing = Get-Content $filePath -Raw
        if ($existing -match "##\s*预测") {
            Write-Host "BLOCKED: 预测段不可修改。只能往 ## 复盘 段追加。"
            exit 1
        }
    }
}

exit 0
