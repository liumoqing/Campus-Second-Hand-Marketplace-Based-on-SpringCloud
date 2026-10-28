# 公共定义：端口、服务清单、启停与健康检查函数（被 start/stop/restart/status 复用）
$script:Root = Split-Path -Parent $PSScriptRoot
$script:RunDir = Join-Path $script:Root '.tmp\run'
$script:FrontendDir = Join-Path $script:Root 'frontend'
$script:JavaExe = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
$script:NpmCmd = 'D:\VueWeb\nodejs\npm.cmd'
if (-not (Test-Path $script:NpmCmd)) { $script:NpmCmd = 'npm.cmd' }

# 启动顺序：被调方先起，网关最后
$script:Services = @(
    @{ Name = 'mall-user';    Port = 4401; Jar = 'mall-user\target\mall-user-1.0.0-SNAPSHOT.jar';    Args = @() },
    @{ Name = 'mall-product'; Port = 4402; Jar = 'mall-product\target\mall-product-1.0.0-SNAPSHOT.jar'; Args = @() },
    @{ Name = 'mall-order';   Port = 4403; Jar = 'mall-order\target\mall-order-1.0.0-SNAPSHOT.jar';   Args = @() },
    @{ Name = 'mall-payment'; Port = 4404; Jar = 'mall-payment\target\mall-payment-1.0.0-SNAPSHOT.jar'; Args = @() },
    @{ Name = 'mall-message'; Port = 4405; Jar = 'mall-message\target\mall-message-1.0.0-SNAPSHOT.jar'; Args = @() },
    @{ Name = 'mall-auth';    Port = 4400; Jar = 'mall-auth\target\mall-auth-1.0.0-SNAPSHOT.jar';    Args = @('--mall.captcha.enabled=false') },
    @{ Name = 'mall-gateway'; Port = 4399; Jar = 'mall-gateway\target\mall-gateway-1.0.0-SNAPSHOT.jar'; Args = @() }
)
$script:ServicePorts = @(4399, 4400, 4401, 4402, 4403, 4404, 4405)
$script:FrontendPort = 5173

function Ensure-RunDir { if (-not (Test-Path $script:RunDir)) { New-Item -ItemType Directory -Force -Path $script:RunDir | Out-Null } }

function Get-PidFile { Join-Path $script:RunDir 'pids.json' }

function Read-Pids {
    $f = Get-PidFile
    if (-not (Test-Path $f)) { return @{} }
    try {
        $obj = Get-Content $f -Raw | ConvertFrom-Json
        $map = @{}
        foreach ($prop in $obj.PSObject.Properties) { $map[$prop.Name] = $prop.Value }
        return $map
    } catch { return @{} }
}

function Write-Pids($map) {
    Ensure-RunDir
    ($map | ConvertTo-Json -Depth 4) | Set-Content -Path (Get-PidFile) -Encoding UTF8
}

# ─────────────────────────────────────────────────────────────
# 端口/进程清理：解决"改完代码端口被旧进程占着，必须手动杀进程"的问题
# ─────────────────────────────────────────────────────────────

# 找出监听某端口的 PID。Get-NetTCPConnection 在受限终端/非管理员环境下可能直接报
# “拒绝访问”，此时回退到 netstat，避免把正在运行的服务误判为空闲。
function Get-ListeningPid([int]$port) {
    try {
        $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop | Select-Object -First 1
        if ($conn -and $conn.OwningProcess) { return [int]$conn.OwningProcess }
    } catch { }

    $pattern = "^\s*TCP\s+\S+:{0}\s+\S+\s+LISTENING\s+(\d+)\s*$" -f $port
    foreach ($line in (& netstat.exe -ano -p tcp 2>$null)) {
        if ($line -match $pattern) { return [int]$Matches[1] }
    }
    return $null
}

# 找出监听某端口的进程（返回进程信息，取不到就返回 $null）
function Get-PortOwner([int]$port) {
    $ownerPid = Get-ListeningPid $port
    if (-not $ownerPid) { return $null }

    $name = 'unknown'
    $cmd = ''
    try {
        $proc = Get-CimInstance Win32_Process -Filter ("ProcessId={0}" -f $ownerPid) -ErrorAction Stop
        if ($proc) {
            $name = $proc.Name
            $cmd = [string]$proc.CommandLine
        }
    } catch {
        $proc = Get-Process -Id $ownerPid -ErrorAction SilentlyContinue
        if ($proc) { $name = $proc.ProcessName + '.exe' }
    }
    return [pscustomobject]@{ Port = $port; Pid = $ownerPid; Name = $name; Cmd = $cmd }
}

function Test-PortFree([int]$port) { return ($null -eq (Get-ListeningPid $port)) }

# 强杀进程树（/T 连子进程一起杀，/F 强制；Stop-Process 杀不掉 jvm 的僵尸子进程）
function Kill-Tree([int]$procId) {
    & taskkill.exe /PID $procId /T /F 2>&1 | Out-Null
}

# 单端口清理：找到占用者并杀进程树，最多重试 5 次
function Clear-Port([int]$port, [switch]$Quiet) {
    for ($i = 0; $i -lt 5; $i++) {
        $owner = Get-PortOwner -port $port
        if (-not $owner) {
            if (-not $Quiet -and $i -gt 0) { Write-Host ("  {0} 已释放" -f $port) -ForegroundColor DarkGray }
            return $true
        }
        if (-not $Quiet) { Write-Host ("  端口 {0} 被 {1}(pid {2}) 占用 → 强杀进程树" -f $port, $owner.Name, $owner.Pid) -ForegroundColor Yellow }
        Kill-Tree $owner.Pid
        Start-Sleep -Milliseconds 700
    }
    $left = Get-PortOwner -port $port
    if ($left) { Write-Host ("  !! 端口 {0} 仍被 {1}(pid {2}) 占用，请以管理员身份运行" -f $port, $left.Name, $left.Pid) -ForegroundColor Red; return $false }
    return $true
}

# 杀掉本项目所有 java 服务进程：命令行命中 mall-* 的 target（jar 或 IDEA 的 classes），或占着业务端口
function Stop-ProjectServices([switch]$IncludeFrontend) {
    Ensure-RunDir
    $killed = @()

    # 1) 命令行匹配：jar 方式与 IDEA 方式（target\classes）都覆盖
    $patterns = @(
        'mall-(gateway|auth|user|product|order|payment|message)\\[^\s]*target\\',
        'mall-(gateway|auth|user|product|order|payment|message)\\\\target\\\\'
    )
    foreach ($p in (Get-CimInstance Win32_Process -Filter "Name='java.exe'" -ErrorAction SilentlyContinue)) {
        $cmd = [string]$p.CommandLine
        $hit = $false
        foreach ($pat in $patterns) { if ($cmd -match $pat) { $hit = $true; break } }
        if (-not $hit -and $cmd -match 'mall-(gateway|auth|user|product|order|payment|message)') { $hit = $true }
        if ($hit) {
            Kill-Tree $p.ProcessId
            if ($killed -notcontains $p.ProcessId) { $killed += $p.ProcessId }
        }
    }

    # 2) 端口兜底：命令行被截断也能清掉
    foreach ($port in $script:ServicePorts) { Clear-Port -port $port -Quiet | Out-Null }

    # 3) 前端（可选）
    if ($IncludeFrontend) {
        Clear-Port -port $script:FrontendPort -Quiet | Out-Null
        foreach ($p in (Get-CimInstance Win32_Process -Filter "Name='node.exe'" -ErrorAction SilentlyContinue)) {
            $cmd = [string]$p.CommandLine
            if ($cmd -match 'vite' -and $cmd -match 'frontend') {
                Kill-Tree $p.ProcessId
                if ($killed -notcontains $p.ProcessId) { $killed += $p.ProcessId }
            }
        }
    }

    # 4) 等端口真正释放（最多 6 秒）
    for ($i = 0; $i -lt 12; $i++) {
        $busy = @($script:ServicePorts | Where-Object { -not (Test-PortFree $_) })
        if ($IncludeFrontend -and -not (Test-PortFree $script:FrontendPort)) { $busy += $script:FrontendPort }
        if ($busy.Count -eq 0) { break }
        Start-Sleep -Milliseconds 500
        foreach ($port in $busy) { Clear-Port -port $port -Quiet | Out-Null }
    }

    $killed | Sort-Object -Unique
}

# 用 Invoke-RestMethod 判健康：Windows PowerShell 5.1 下 Invoke-WebRequest 的 .Content 在
# application/vnd.spring-boot.actuator+json 时是 byte[]，用 -match 永远不匹配（曾导致每个服务空等 120s）
function Test-Health([int]$port) {
    try {
        $r = Invoke-RestMethod -Uri ("http://127.0.0.1:{0}/actuator/health" -f $port) -TimeoutSec 3
        return ($r.status -eq 'UP')
    } catch { return $false }
}

function Wait-Health([int]$port, [int]$timeoutSec = 90, [int]$procId = 0) {
    $deadline = (Get-Date).AddSeconds($timeoutSec)
    while ((Get-Date) -lt $deadline) {
        if (Test-Health -port $port) { return $true }
        if ($procId -gt 0) {
            $alive = Get-Process -Id $procId -ErrorAction SilentlyContinue
            if (-not $alive) { return $false }
        }
        Start-Sleep -Milliseconds 700
    }
    return $false
}

function Start-ProjectServices {
    Ensure-RunDir
    $pids = @{}
    # 启动前先清端口：上次异常退出或在 IDEA 里没停干净的实例会把端口继续占着
    foreach ($s in $script:Services) {
        if (-not (Test-PortFree $s.Port)) {
            Write-Host ("  端口 {0} 被占用，先清理（{1}）" -f $s.Port, $s.Name) -ForegroundColor Yellow
            Clear-Port -port $s.Port | Out-Null
        }
    }
    foreach ($s in $script:Services) {
        $jar = Join-Path $script:Root $s.Jar
        if (-not (Test-Path $jar)) { Write-Host ("  [skip] {0} 未构建：{1}" -f $s.Name, $s.Jar) -ForegroundColor Yellow; continue }
        $args = @('-Xms96m', '-Xmx512m', '-jar', $jar) + $s.Args
        $out = Join-Path $script:RunDir ($s.Name + '.log')
        if (Test-Path $out) { Remove-Item $out -Force -ErrorAction SilentlyContinue }
        $p = Start-Process -FilePath $script:JavaExe -ArgumentList $args -WorkingDirectory $script:Root -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError ($out -replace '\.log$', '.err.log') -PassThru
        $pids[$s.Name] = $p.Id
        Write-Host ("  启动 {0} (pid {1}, 端口 {2})" -f $s.Name, $p.Id, $s.Port)
        $ok = Wait-Health -port $s.Port -timeoutSec 120 -procId $p.Id
        if ($ok) { Write-Host ("    [OK] {0} 健康" -f $s.Name) -ForegroundColor Green }
        else { Write-Host ("    [FAIL] {0} 启动超时，看 {1}" -f $s.Name, $out) -ForegroundColor Red }
    }
    Write-Pids $pids
    return $pids
}

function Start-Frontend {
    if (-not (Test-Path $script:FrontendDir)) { Write-Host '  [skip] frontend 目录不存在' -ForegroundColor Yellow; return $null }

    # 幂等：已经能访问 5173 就不重复启动；端口被别的进程占着且不健康时才清理。
    $existing = Get-PortOwner $script:FrontendPort
    if ($existing) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:$script:FrontendPort/" -TimeoutSec 3 -UseBasicParsing
            if ($r.StatusCode -eq 200) {
                Write-Host ("  [OK] Vite 已在运行 http://127.0.0.1:{0}/ (pid {1})" -f $script:FrontendPort, $existing.Pid) -ForegroundColor Green
                return [pscustomobject]@{ Id = $existing.Pid }
            }
        } catch { }
        Write-Host ("  端口 {0} 被 {1}(pid {2}) 占用但页面不可用，先清理" -f $script:FrontendPort, $existing.Name, $existing.Pid) -ForegroundColor Yellow
        Clear-Port -port $script:FrontendPort | Out-Null
    }

    $out = Join-Path $script:RunDir 'vite.log'
    if (Test-Path $out) { Remove-Item $out -Force -ErrorAction SilentlyContinue }
    $p = Start-Process -FilePath $script:NpmCmd -ArgumentList @('run', 'dev') -WorkingDirectory $script:FrontendDir -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError ($out -replace '\.log$', '.err.log') -PassThru
    $deadline = (Get-Date).AddSeconds(90)
    while ((Get-Date) -lt $deadline) {
        try {
            $r = Invoke-WebRequest -Uri "http://127.0.0.1:$script:FrontendPort/" -TimeoutSec 3 -UseBasicParsing
            if ($r.StatusCode -eq 200) { Write-Host ("    [OK] Vite http://127.0.0.1:{0}/" -f $script:FrontendPort) -ForegroundColor Green; return $p }
        } catch { }
        Start-Sleep -Milliseconds 800
    }
    Write-Host ("    [FAIL] Vite 启动超时，看 {0}" -f $out) -ForegroundColor Red
    return $p
}

function Get-ProjectStatus {
    $rows = @()
    foreach ($s in $script:Services) {
        $owner = Get-PortOwner $s.Port
        $pidText = if ($owner) { $owner.Pid } else { '-' }
        $health = '-'
        if ($owner) {
            if (Test-Health -port $s.Port) { $health = 'UP' } else { $health = 'DOWN' }
        }
        $rows += [pscustomobject]@{ 服务 = $s.Name; 端口 = $s.Port; PID = $pidText; 健康 = $health }
    }
    $fe = Get-PortOwner $script:FrontendPort
    $fePid = '-'
    $feHealth = '-'
    if ($fe) { $fePid = $fe.Pid; $feHealth = 'UP' }
    $rows += [pscustomobject]@{ '服务' = 'frontend(vite)'; '端口' = $script:FrontendPort; 'PID' = $fePid; '健康' = $feHealth }
    $rows
}
