# Run from an extracted companion ZIP. Elevates once to register a login task and a LAN-only firewall rule.
$ErrorActionPreference = 'Stop'
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Start-Process powershell.exe -Verb RunAs -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
    exit
}
$destination = Join-Path $env:LOCALAPPDATA 'LibreHwTab\bin'
$source = Join-Path $PSScriptRoot 'agent'
Stop-ScheduledTask -TaskName 'LibreHwTab' -ErrorAction SilentlyContinue
Get-Process LibreHwAgent -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq (Join-Path $destination 'LibreHwAgent.exe') } | Stop-Process -Force
New-Item -ItemType Directory -Force $destination | Out-Null
Copy-Item "$source\*" $destination -Recurse -Force
$configPath = Join-Path $env:LOCALAPPDATA 'LibreHwTab\connection.json'
if (!(Test-Path $configPath)) {
    $bytes = New-Object byte[] 16
    $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($bytes)
    $rng.Dispose()
    @{ Port = 8750; Token = ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant() } | ConvertTo-Json | ForEach-Object { [IO.File]::WriteAllText($configPath, $_, (New-Object Text.UTF8Encoding $false)) }
}
$config = Get-Content $configPath -Raw | ConvertFrom-Json
$exe = Join-Path $destination 'LibreHwAgent.exe'
$action = New-ScheduledTaskAction -Execute $exe -WorkingDirectory $destination
$user = [Security.Principal.WindowsIdentity]::GetCurrent().Name
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $user
$principal = New-ScheduledTaskPrincipal -UserId $user -LogonType Interactive -RunLevel Highest
$settings = New-ScheduledTaskSettingsSet -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 3 -RestartInterval (New-TimeSpan -Minutes 1)
Register-ScheduledTask -TaskName 'LibreHwTab' -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
Get-NetFirewallRule -DisplayName 'Libre HW Tab LAN' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
New-NetFirewallRule -DisplayName 'Libre HW Tab LAN' -Direction Inbound -Action Allow -Program $exe -Protocol TCP -LocalPort $config.Port -RemoteAddress LocalSubnet -Profile Private | Out-Null
Start-ScheduledTask -TaskName 'LibreHwTab'
Start-Process $exe -ArgumentList '--pair'
Write-Host "`nInstalled. Set your home network profile to Private. Tablet and PC must be on the same LAN."
Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -ne '127.0.0.1' -and $_.IPAddress -notlike '169.254.*' } | ForEach-Object { Write-Host "Address: http://$($_.IPAddress):$($config.Port)" }
Write-Host "Pairing key: $($config.Token)"
Write-Host 'If CPU temperature is missing, install PawnIO from https://pawnio.eu/ and reboot.'
Read-Host 'Press Enter to close'
