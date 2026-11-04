$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskDist = Join-Path $taskRoot '.gradle-dist'
New-Item -ItemType Directory -Force $taskDist | Out-Null
$url = 'https://downloads.gradle.org/distributions/gradle-8.11.1-bin.zip'
$zip = Join-Path $taskDist 'gradle.zip'
Invoke-WebRequest $url -OutFile $zip
$expected = (Invoke-WebRequest "$url.sha256").Content.Trim().ToLowerInvariant()
$actual = (Get-FileHash $zip -Algorithm SHA256).Hash.ToLowerInvariant()
if ($actual -ne $expected) { throw 'Gradle checksum mismatch; download rejected.' }
Expand-Archive $zip -DestinationPath $taskDist -Force
Remove-Item $zip
