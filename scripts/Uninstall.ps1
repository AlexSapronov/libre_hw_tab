$ErrorActionPreference = 'Stop'
if (-not ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Start-Process powershell.exe -Verb RunAs -ArgumentList "-NoProfile -ExecutionPolicy Bypass -File `"$PSCommandPath`""
    exit
}
Stop-ScheduledTask -TaskName 'LibreHwTab' -ErrorAction SilentlyContinue
Unregister-ScheduledTask -TaskName 'LibreHwTab' -Confirm:$false -ErrorAction SilentlyContinue
Get-NetFirewallRule -DisplayName 'Libre HW Tab LAN' -ErrorAction SilentlyContinue | Remove-NetFirewallRule
$destination = Join-Path $env:LOCALAPPDATA 'LibreHwTab\bin'
Get-Process LibreHwAgent -ErrorAction SilentlyContinue | Where-Object { $_.Path -eq (Join-Path $destination 'LibreHwAgent.exe') } | Stop-Process -Force
Remove-Item $destination -Recurse -Force -ErrorAction SilentlyContinue
Write-Host 'Removed. Pairing settings retained in LocalAppData\LibreHwTab.'
