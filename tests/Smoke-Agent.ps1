$ErrorActionPreference = 'Stop'
foreach ($file in @('scripts/Install.ps1', 'scripts/Uninstall.ps1')) {
    $tokens = $null; $errors = $null
    [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PWD $file), [ref]$tokens, [ref]$errors) | Out-Null
    if ($errors.Count) { throw ($errors | Out-String) }
}
$qrTest = Start-Process (Join-Path $PWD 'package/agent/LibreHwAgent.exe') -ArgumentList '--pair-test' -PassThru -Wait
if ($qrTest.ExitCode -ne 0) { throw 'QR generation self-test failed' }
$process = Start-Process (Join-Path $PWD 'package/agent/LibreHwAgent.exe') -PassThru
try {
    $configPath = Join-Path $env:LOCALAPPDATA 'LibreHwTab/connection.json'
    $ready = $false
    for ($i = 0; $i -lt 40; $i++) {
        Start-Sleep -Milliseconds 500
        if ($process.HasExited) { throw "Agent exited: $($process.ExitCode)" }
        if (!(Test-Path $configPath)) { continue }
        $config = Get-Content $configPath -Raw | ConvertFrom-Json
        $url = "http://127.0.0.1:$($config.Port)/api/v1/telemetry"
        $response = Invoke-WebRequest $url -Headers @{ 'X-Libre-Token' = $config.Token } -SkipHttpErrorCheck
        if ($response.StatusCode -eq 200) { $ready = $true; break }
    }
    if (!$ready) { throw 'Agent did not return a sample' }
    $sample = $response.Content | ConvertFrom-Json
    if ($sample.version -ne 1 -or !$sample.timestamp -or !$sample.host) { throw 'Invalid telemetry schema' }
    if ($null -eq $sample.game -or $null -eq $sample.game.active -or !$sample.game.status) { throw 'Missing game status schema' }
    $help = Start-Process (Join-Path $PWD 'package/agent/PresentMon.exe') -ArgumentList '--help' -Wait -PassThru -NoNewWindow
    if ($help.ExitCode -ne 0) { throw 'Bundled PresentMon does not run' }
    $noKey = Invoke-WebRequest $url -SkipHttpErrorCheck
    $badKey = Invoke-WebRequest $url -Headers @{ 'X-Libre-Token' = 'wrong' } -SkipHttpErrorCheck
    if ($noKey.StatusCode -ne 401 -or $badKey.StatusCode -ne 401) { throw 'Authentication failed' }
    if ($response.Headers['Cache-Control'] -notcontains 'no-store') { throw 'Telemetry must not be cached' }
    Write-Host 'PASS: QR payload/PNG generation, API schema, no-key/wrong-key rejection, valid-key response, no-store, installer syntax.'
} finally {
    if (!$process.HasExited) { Stop-Process -Id $process.Id -Force }
}
