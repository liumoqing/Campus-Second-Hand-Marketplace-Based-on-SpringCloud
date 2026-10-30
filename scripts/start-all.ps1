# 启动本项目全部服务：先幂等停一次（清掉僵尸进程），再按依赖顺序拉起，最后起前端
param([switch]$SkipFrontend, [switch]$NoStop, [switch]$SkipNacosConfig, [switch]$SkipObservability)
$ErrorActionPreference = 'Continue'
. (Join-Path $PSScriptRoot '_common.ps1')

if (-not $NoStop) { Stop-ProjectServices -IncludeFrontend:(!$SkipFrontend) | Out-Null }

if (-not $SkipNacosConfig) {
    Write-Host '== 同步 Nacos 配置 ==' -ForegroundColor Cyan
    try {
        & (Join-Path $PSScriptRoot 'init-nacos-config.ps1')
    } catch {
        Write-Host ("  [WARN] Nacos 配置同步失败，使用本地兜底继续启动：{0}" -f $_.Exception.Message) -ForegroundColor Yellow
    }
}

if (-not $SkipObservability) {
    try {
        & (Join-Path $PSScriptRoot 'start-observability.ps1')
    } catch {
        Write-Host ("  [WARN] Observability startup skipped: {0}" -f $_.Exception.Message) -ForegroundColor Yellow
    }
}

Write-Host '== 启动后端服务 ==' -ForegroundColor Cyan
$pids = Start-ProjectServices

if (-not $SkipFrontend) {
    Write-Host '== 启动前端 Vite ==' -ForegroundColor Cyan
    Start-Frontend | Out-Null
}

Write-Host ''
Write-Host '== 当前状态 ==' -ForegroundColor Cyan
Get-ProjectStatus | Format-Table -AutoSize | Out-String -Width 100 | Write-Host
Write-Host '后端入口 http://127.0.0.1:4399    前端 http://127.0.0.1:5173' -ForegroundColor Gray
