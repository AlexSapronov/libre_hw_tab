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
    if ('media' -notin $sample.PSObject.Properties.Name) { throw 'Missing optional media schema' }
    $mediaNoKey = Invoke-WebRequest ("http://127.0.0.1:$($config.Port)/api/v1/media/control") -Method Post -ContentType 'application/json' -Body '{"id":"stale","action":"next"}' -SkipHttpErrorCheck
    if ($mediaNoKey.StatusCode -ne 401) { throw 'Media control must require pairing token' }
    $mediaStale = Invoke-WebRequest ("http://127.0.0.1:$($config.Port)/api/v1/media/control") -Method Post -Headers @{ 'X-Libre-Token' = $config.Token } -ContentType 'application/json' -Body '{"id":"stale","action":"next"}' -SkipHttpErrorCheck
    if ($mediaStale.StatusCode -ne 409) { throw 'Stale media commands must be rejected' }
    $thumbNoKey = Invoke-WebRequest ("http://127.0.0.1:$($config.Port)/api/v1/media/stale/thumbnail") -SkipHttpErrorCheck
    if ($thumbNoKey.StatusCode -ne 401) { throw 'Media thumbnails must require pairing token' }
    $capture = Start-Process (Join-Path $PWD 'package/agent/PresentMon.exe') -ArgumentList @('--no_csv','--no_console_stats','--v1_metrics','--no_track_gpu','--no_track_input','--session_name',"LibreHwTab-Smoke-$PID",'--timed','2','--terminate_after_timed') -PassThru -NoNewWindow
    try { $capture | Wait-Process -Timeout 15 } catch { Stop-Process -Id $capture.Id -Force; throw }
    $capture.Refresh()
    if ($capture.ExitCode -ne 0) { throw "PresentMon ETW capture failed: $($capture.ExitCode)" }
    $latest = Invoke-RestMethod $url -Headers @{ 'X-Libre-Token' = $config.Token }
    if ($latest.game.status -notin @('idle','ready','starting')) { throw "Agent capture is unhealthy: $($latest.game.status)" }
    $collectorPid = @(Get-CimInstance Win32_Process -Filter "ParentProcessId=$($process.Id)" | Where-Object { $_.Name -eq 'PresentMon.exe' } | Select-Object -ExpandProperty ProcessId)
    if ($collectorPid.Count -ne 1) { throw 'Expected exactly one bundled PresentMon child' }
    $noKey = Invoke-WebRequest $url -SkipHttpErrorCheck
    $badKey = Invoke-WebRequest $url -Headers @{ 'X-Libre-Token' = 'wrong' } -SkipHttpErrorCheck
    if ($noKey.StatusCode -ne 401 -or $badKey.StatusCode -ne 401) { throw 'Authentication failed' }
    if ($response.Headers['Cache-Control'] -notcontains 'no-store') { throw 'Telemetry must not be cached' }
    Write-Host 'PASS: PresentMon ETW capture/child lifecycle, QR payload/PNG generation, API schema, no-key/wrong-key rejection, valid-key response, no-store, installer syntax.'
} finally {
    if (!$process.HasExited) { Stop-Process -Id $process.Id -Force; $process | Wait-Process -Timeout 5 }
    if ($collectorPid) {
        Start-Sleep -Milliseconds 300
        if (Get-Process -Id $collectorPid -ErrorAction SilentlyContinue) { throw 'PresentMon child survived agent termination' }
    }
}
