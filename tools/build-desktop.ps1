param([string]$Jdk='D:\Java\MicrosoftJDK17')
$ErrorActionPreference='Stop'
$repoRoot=Split-Path $PSScriptRoot -Parent
& "$repoRoot\app\test\run.ps1" -Jdk $Jdk
if($LASTEXITCODE -ne 0){throw 'Room tests failed'}
if(!(Test-Path -LiteralPath "$repoRoot\app\assets\game.swf")){throw 'Run tools/prepare.py first'}
$desktopPath=Join-Path $repoRoot 'build\desktop'
New-Item -ItemType Directory -Force -Path $desktopPath | Out-Null
Copy-Item -LiteralPath "$repoRoot\app\assets","$repoRoot\app\lib" -Destination $desktopPath -Recurse -Force
& "$Jdk\bin\jar.exe" cf "$desktopPath\server.jar" -C "$repoRoot\app\build\test-classes" com
if($LASTEXITCODE -ne 0){throw 'JAR creation failed'}
$launcher=@'
param([string]$Jdk='D:\Java\MicrosoftJDK17')
$javaPath=Join-Path $Jdk 'bin\java.exe'
if(!(Test-Path -LiteralPath $javaPath)){$javaPath=(Get-Command java.exe -ErrorAction Stop).Source}
& $javaPath -cp "$PSScriptRoot\server.jar;$PSScriptRoot\lib\json.jar" com.party.classic.DesktopServer "$PSScriptRoot\assets" --open
'@
$launcher | Set-Content -Encoding UTF8 -LiteralPath "$desktopPath\start.ps1"
'@echo off','powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1"','pause' | Set-Content -Encoding ASCII -LiteralPath "$desktopPath\Start.cmd"
Write-Output "Desktop package: $desktopPath"
