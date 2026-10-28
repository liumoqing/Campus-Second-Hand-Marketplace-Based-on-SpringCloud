# 改完代码用这个：停 → 构建 → 起。彻底避免"僵尸进程占端口 / jar 被占用导致 install 失败"
param([switch]$SkipBuild, [switch]$SkipFrontend)
$ErrorActionPreference = 'Continue'
. (Join-Path $PSScriptRoot '_common.ps1')

Write-Host '== 1/3 停止旧进程 ==' -ForegroundColor Cyan
Stop-ProjectServices -IncludeFrontend:(!$SkipFrontend) | ForEach-Object { "  已结束 $_" }

if (-not $SkipBuild) {
    Write-Host '== 2/3 Maven 构建 ==' -ForegroundColor Cyan
    Push-Location $script:Root
    & .\mvnw.cmd -B -q -DskipTests install
    $code = $LASTEXITCODE
    Pop-Location
    if ($code -ne 0) { Write-Host "  构建失败（exit $code），已中止启动" -ForegroundColor Red; exit $code }
    Write-Host '  构建成功' -ForegroundColor Green
} else {
    Write-Host '== 2/3 跳过构建 ==' -ForegroundColor DarkGray
}

Write-Host '== 3/3 启动 ==' -ForegroundColor Cyan
& (Join-Path $PSScriptRoot 'start-all.ps1') -NoStop -SkipFrontend:$SkipFrontend