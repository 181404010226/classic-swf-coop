param([string]$Jdk='D:\Java\MicrosoftJDK17')
$ErrorActionPreference='Stop'
$taskRoot=Split-Path $PSScriptRoot -Parent
New-Item -ItemType Directory -Force -Path "$taskRoot\build\test-classes" | Out-Null
& "$Jdk\bin\javac.exe" -encoding UTF-8 -cp "$taskRoot\lib\json.jar" -d "$taskRoot\build\test-classes" "$taskRoot\src\com\party\classic\RoomWorld.java" "$taskRoot\src\com\party\classic\RoomServer.java" "$taskRoot\test\RoomTest.java" "$taskRoot\test\DesktopServer.java"
if($LASTEXITCODE -ne 0){throw 'Java compilation failed'}
& "$Jdk\bin\java.exe" -cp "$taskRoot\build\test-classes;$taskRoot\lib\json.jar" com.party.classic.RoomTest
if($LASTEXITCODE -ne 0){throw 'Room tests failed'}
