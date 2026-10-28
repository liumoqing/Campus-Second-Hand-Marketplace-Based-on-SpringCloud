# 看端口 / 进程 / 健康状态；端口被占时直接显示是谁占的
. (Join-Path $PSScriptRoot '_common.ps1')

$rows = @()
foreach ($s in $script:Services) {
    $owner = Get-PortOwner -port $s.Port
    $health = '-'
    if ($owner) { if (Test-Health -port $s.Port) { $health = 'UP' } else { $health = 'DOWN' } }
    $who = '-'
    if ($owner) { $who = "{0}({1})" -f $owner.Name, $owner.Pid }
    $state = '空闲'
    if ($owner) { $state = '占用' }
    $rows += [pscustomobject]@{ 服务 = $s.Name; 端口 = $s.Port; 状态 = $state; 健康 = $health; 占用者 = $who }
}
$fe = Get-PortOwner -port $script:FrontendPort
$feState = '空闲'
$feHealth = '-'
$feWho = '-'
if ($fe) { $feState = '占用'; $feHealth = 'UP'; $feWho = "{0}({1})" -f $fe.Name, $fe.Pid }
$rows += [pscustomobject]@{ 服务 = 'frontend(vite)'; 端口 = $script:FrontendPort; 状态 = $feState; 健康 = $feHealth; 占用者 = $feWho }
$rows | Format-Table -AutoSize | Out-String -Width 110 | Write-Host