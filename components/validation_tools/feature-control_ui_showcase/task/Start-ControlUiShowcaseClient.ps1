[CmdletBinding()]param(
    [Parameter(Mandatory)][ValidateSet('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')][string]$Target,
    [Parameter(Mandatory)][string]$InstanceDirectory,
    [ValidateSet('client','client-multiplayer')][string]$Role='client',
    [switch]$Offline
)
# Internal child process. No desktop automation or host input injection.
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$expected=[IO.Path]::GetFullPath((Join-Path $repo "validations/feature-control_ui_showcase/instance/$Target/$Role"))
if([IO.Path]::GetFullPath($InstanceDirectory)-ne$expected){throw 'Only the dedicated Showcase instance is allowed'}
Set-Location -LiteralPath $repo
. (Join-Path $PSScriptRoot 'ControlUiShowcase.Helpers.ps1')
$arguments=Get-ShowcaseGradleArguments $Target $InstanceDirectory (Join-Path $PSScriptRoot 'showcase-runs.init.gradle') $Offline.IsPresent $Role
& ./gradlew.bat @arguments
exit $LASTEXITCODE
