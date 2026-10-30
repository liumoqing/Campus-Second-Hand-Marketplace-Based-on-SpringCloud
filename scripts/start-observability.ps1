param(
    [string]$ZipkinJar = 'D:\zipkin-server\zipkin-server-3.6.1-exec.jar',
    [string]$SentinelJar = 'D:\sentinel-dashboard\sentinel-dashboard-1.8.10.jar'
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$runDir = Join-Path $root '.tmp\run'
$obsDir = Join-Path $root '.tmp\observability'
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
New-Item -ItemType Directory -Force -Path $runDir, $obsDir | Out-Null

function Test-Port([int]$port) {
    try {
        $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction Stop | Select-Object -First 1
        if ($conn) { return $true }
    } catch { }
    $pattern = "^\s*TCP\s+\S+:{0}\s+\S+\s+LISTENING\s+\d+\s*$" -f $port
    foreach ($line in (& netstat.exe -ano -p tcp 2>$null)) {
        if ($line -match $pattern) { return $true }
    }
    return $false
}

function Start-Hidden([string]$file, [string[]]$arguments, [string]$workingDirectory, [string]$name) {
    $out = Join-Path $runDir ($name + '.log')
    $err = Join-Path $runDir ($name + '.err.log')
    $process = Start-Process -FilePath $file -ArgumentList $arguments -WorkingDirectory $workingDirectory `
        -WindowStyle Hidden -RedirectStandardOutput $out -RedirectStandardError $err -PassThru
    Write-Host ("  [START] {0} pid={1}" -f $name, $process.Id) -ForegroundColor Cyan
}

Write-Host '== Starting observability components ==' -ForegroundColor Cyan

if (Test-Port 9411) {
    Write-Host '  [OK] Zipkin already listening on 9411' -ForegroundColor Green
} elseif (Test-Path -LiteralPath $ZipkinJar) {
    Start-Hidden $java @('-Xms128m', '-Xmx512m', '-jar', $ZipkinJar) (Split-Path $ZipkinJar) 'zipkin'
} else {
    Write-Host '  [SKIP] Zipkin JAR not found' -ForegroundColor Yellow
}

if (Test-Port 8858) {
    Write-Host '  [OK] Sentinel Dashboard already listening on 8858' -ForegroundColor Green
} elseif (Test-Path -LiteralPath $SentinelJar) {
    Start-Hidden $java @('-Xms128m', '-Xmx512m', '-Dserver.port=8858', '-Dcsp.sentinel.dashboard.server=127.0.0.1:8858', '-jar', $SentinelJar) (Split-Path $SentinelJar) 'sentinel-dashboard'
} else {
    Write-Host '  [SKIP] Sentinel Dashboard JAR not found' -ForegroundColor Yellow
}

$promExe = Get-ChildItem -LiteralPath (Join-Path $obsDir 'prometheus') -Recurse -Filter 'prometheus.exe' -File -ErrorAction SilentlyContinue | Select-Object -First 1
if (Test-Port 9090) {
    Write-Host '  [OK] Prometheus already listening on 9090' -ForegroundColor Green
} elseif ($promExe) {
    $promData = Join-Path $obsDir 'prometheus-data'
    New-Item -ItemType Directory -Force -Path $promData | Out-Null
    $promConfig = Join-Path $root 'observability\prometheus\prometheus.yml'
    Start-Hidden $promExe.FullName @("--config.file=$promConfig", "--storage.tsdb.path=$promData", '--web.listen-address=127.0.0.1:9090') $promExe.DirectoryName 'prometheus'
} else {
    Write-Host '  [SKIP] Portable Prometheus not found under .tmp/observability/prometheus' -ForegroundColor Yellow
}

$grafanaExe = Get-ChildItem -LiteralPath (Join-Path $obsDir 'grafana') -Recurse -Filter 'grafana.exe' -File -ErrorAction SilentlyContinue | Select-Object -First 1
if (Test-Port 3000) {
    Write-Host '  [OK] Grafana already listening on 3000' -ForegroundColor Green
} elseif ($grafanaExe) {
    $grafanaHome = Split-Path $grafanaExe.DirectoryName -Parent
    $env:GF_PATHS_PROVISIONING = Join-Path $root 'observability\grafana\provisioning'
    $env:GF_PATHS_DATA = Join-Path $obsDir 'grafana-data'
    $env:GF_PATHS_LOGS = Join-Path $obsDir 'grafana-logs'
    $env:GF_PATHS_PLUGINS = Join-Path $obsDir 'grafana-plugins'
    $env:GF_SERVER_HTTP_ADDR = '127.0.0.1'
    $env:GF_SERVER_HTTP_PORT = '3000'
    $env:GF_SECURITY_ADMIN_USER = 'admin'
    $env:GF_SECURITY_ADMIN_PASSWORD = 'admin'
    $env:GF_AUTH_ANONYMOUS_ENABLED = 'true'
    $env:GF_AUTH_ANONYMOUS_ORG_ROLE = 'Viewer'
    New-Item -ItemType Directory -Force -Path $env:GF_PATHS_DATA, $env:GF_PATHS_LOGS, $env:GF_PATHS_PLUGINS | Out-Null
    Start-Hidden $grafanaExe.FullName @('server', '--homepath', $grafanaHome) $grafanaExe.DirectoryName 'grafana'
} else {
    Write-Host '  [SKIP] Portable Grafana not found under .tmp/observability/grafana' -ForegroundColor Yellow
}
