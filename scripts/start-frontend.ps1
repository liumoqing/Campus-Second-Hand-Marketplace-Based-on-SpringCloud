$ErrorActionPreference = 'Continue'
. (Join-Path $PSScriptRoot '_common.ps1')

Write-Host '== 启动前端 Vite ==' -ForegroundColor Cyan
$p = Start-Frontend
if (-not $p) { exit 1 }

Write-Host ''
& (Join-Path $PSScriptRoot 'status.ps1')