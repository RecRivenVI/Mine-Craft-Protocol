#requires -Version 7.0
[CmdletBinding(DefaultParameterSetName='Case')]param(
    [string]$Target='26.3-fabric',
    [Parameter(ParameterSetName='Case')][string]$Case='pointer-move',
    [Parameter(Mandatory,ParameterSetName='All')][switch]$All,
    [Parameter(Mandatory,ParameterSetName='Prepare')][switch]$PrepareClient,
    [Parameter(Mandatory,ParameterSetName='List')][switch]$ListCases,
    [ValidateRange(0.25,60)][double]$HoldSeconds=4,
    [ValidateRange(15,600)][int]$ManualTimeoutSeconds=180
)
$ErrorActionPreference='Stop'
# Viewing entry point: reuse one isolated client, never navigate out of the user's current world.
$arguments=@{Target=$Target;FromTitle=$true;KeepClient=$true;HoldSeconds=$HoldSeconds;ManualTimeoutSeconds=$ManualTimeoutSeconds}
switch($PSCmdlet.ParameterSetName) {
    'Prepare' {$arguments.PrepareClient=$true}
    'List' {$arguments.ListCases=$true}
    'All' {$arguments.All=$true}
    default {$arguments.Case=$Case}
}
& (Join-Path $PSScriptRoot 'Invoke-ControlUiShowcase.ps1') @arguments
