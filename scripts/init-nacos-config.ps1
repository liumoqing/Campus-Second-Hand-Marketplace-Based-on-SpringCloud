param(
    [string]$NacosBaseUrl = $(if ($env:NACOS_ADDR) { "http://$env:NACOS_ADDR" } else { 'http://127.0.0.1:8848' }),
    [string]$Group = $(if ($env:NACOS_CONFIG_GROUP) { $env:NACOS_CONFIG_GROUP } else { 'DEFAULT_GROUP' })
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$configDir = Join-Path $root 'nacos-config'
$url = $NacosBaseUrl.TrimEnd('/') + '/nacos/v1/cs/configs'

$files = @(Get-ChildItem -LiteralPath $configDir -File -Filter '*.yaml' | Sort-Object Name)
if ($files.Count -eq 0) {
    throw "No Nacos config files found in $configDir"
}

Write-Host ("Syncing {0} Nacos config files -> {1} / {2}" -f $files.Count, $url, $Group) -ForegroundColor Cyan
foreach ($file in $files) {
    $content = [System.IO.File]::ReadAllText($file.FullName, [System.Text.Encoding]::UTF8)
    $form = @{
        dataId = $file.Name
        group = $Group
        type = 'yaml'
        content = $content
    }
    $result = Invoke-RestMethod -Method Post -Uri $url -Body $form -TimeoutSec 15
    if ([string]$result -ne 'true') {
        throw "Failed to publish Nacos config: $($file.Name) -> $result"
    }
    Write-Host ("  [OK] {0}" -f $file.Name) -ForegroundColor Green
}
