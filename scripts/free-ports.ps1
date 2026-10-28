# 只做一件事：把 4399~4405（以及可选的 5173）从旧进程手里抢回来。
# 场景：在 IDEA 里点 Run 之前发现端口被占，双击 free-ports.cmd 即可，不用再手动找 java.exe。
param([switch]$KeepFrontend)
$ErrorActionPreference = 'Continue'
. (Join-Path $PSScriptRoot '_common.ps1')

Write-Host '== 清理项目端口 ==' -ForegroundColor Cyan
$fail = @()
foreach ($s in $script:Services) {
    $owner = Get-PortOwner -port $s.Port
    if ($owner) {
        Write-Host ("  {0}: {1}(pid {2}) 占着 {3} → 结束" -f $s.Name, $owner.Name, $owner.Pid, $s.Port) -ForegroundColor Yellow
        if (-not (Clear-Port -port $s.Port)) { $fail += $s.Port }
    }
}
if (-not $KeepFrontend) {
    $fe = Get-PortOwner -port $script:FrontendPort
    if ($fe) {
        Write-Host ("  frontend: {0}(pid {1}) 占着 {2} → 结束" -f $fe.Name, $fe.Pid, $script:FrontendPort) -ForegroundColor Yellow
        if (-not (Clear-Port -port $script:FrontendPort)) { $fail += $script:FrontendPort }
    }
}

$busy = @()
foreach ($port in $script:ServicePorts) { if (-not (Test-PortFree $port)) { $busy += $port } }
if (-not $KeepFrontend) { if (-not (Test-PortFree $script:FrontendPort)) { $busy += $script:FrontendPort } }

if ($busy.Count -eq 0) {
    Write-Host '  全部端口已空闲，可以直接在 IDEA 里 Run' -ForegroundColor Green
    exit 0
} else {
    Write-Host ("  [FAIL] 仍有端口被占用：{0}（可能需要管理员权限）" -f ($busy -join ', ')) -ForegroundColor Red
    exit 1
}