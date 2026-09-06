[CmdletBinding()]param(
    [Parameter(Mandatory)][ValidateSet('1.20.1-forge','26.2-fabric')][string]$Target,
    [Parameter(Mandatory)][string]$InstanceDirectory,
    [Parameter(Mandatory)][int]$Port
)
# Internal child process. No desktop automation or host input injection.
$ErrorActionPreference='Stop'
$repo=Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$expected=[IO.Path]::GetFullPath((Join-Path $repo "runs/$Target/control-ui-showcase"))
if([IO.Path]::GetFullPath($InstanceDirectory)-ne$expected){throw 'Only the dedicated Showcase instance is allowed'}
if($Port-ne$(if($Target-eq'1.20.1-forge'){25601}else{25602})){throw 'Wrong Showcase port'}
Set-Location -LiteralPath $repo
& ./gradlew.bat "-Dshowcase.target=$Target" "-Dshowcase.directory=$InstanceDirectory" "-Dshowcase.port=$Port" `
    --init-script (Join-Path $PSScriptRoot 'showcase-runs.init.gradle') ":versions:${Target}:runClient" --no-daemon
exit $LASTEXITCODE
