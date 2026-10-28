# 停止本项目全部服务（java 后端 7 个 + 前端 Vite），不影响 Nacos/Sentinel/Seata/MySQL/Redis
param([switch]$KeepFrontend)
$ErrorActionPreference = 'Continue'
. (Join-Path $PSScriptRoot '_common.ps1')

Write-Host '== 停止项目服务 ==' -ForegroundColor Cyan
$killed = Stop-ProjectServices -IncludeFrontend:(-not $KeepFrontend)
if ($killed.Count -eq 0) { Write-Host '  没有需要停止的进程' -ForegroundColor Gray }
else { Write-Host ("  已结束进程：{0}" -f (($killed | Sort-Object -Unique) -join ', ')) }

$remain = @()
foreach ($port in $script:ServicePorts) {
    $c = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    if ($c) { $remain += $port }
}
if (-not $KeepFrontend) {
    $c = Get-NetTCPConnection -LocalPort $script:FrontendPort -State Listen -ErrorAction SilentlyContinue
    if ($c) { $remain += $script:FrontendPort }
}
if ($remain.Count -eq 0) { Write-Host '  端口已全部释放' -ForegroundColor Green }
else { Write-Host ("  [WARN] 仍被占用：{0}" -f ($remain -join ', ')) -ForegroundColor Yellow }
