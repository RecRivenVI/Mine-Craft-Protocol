[CmdletBinding()]param([Parameter(Mandatory)][string]$Target)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$registered=@([regex]::Matches((Get-Content (Join-Path $repo 'settings.gradle.kts') -Raw),'(?m)^\s*register\("([^"\r\n]+)"\)')|ForEach-Object {$_.Groups[1].Value})
if($Target-notin$registered){throw 'Target is not registered'}
Set-Location -LiteralPath $repo
& ./gradlew.bat ":version:${Target}:runValidationFeatureRuntimeProbeClient" --offline --no-daemon
exit $LASTEXITCODE
