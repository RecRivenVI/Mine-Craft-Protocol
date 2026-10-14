[CmdletBinding()]
param([ValidateSet('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')][string]$Target)
$ErrorActionPreference='Stop'
$targets=if($Target){@($Target)}else{@('26.3-neoforge','1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-fabric')}
foreach($item in $targets){
    Write-Output "Runtime core migration: $item, isolated single client, Native interface only"
    & (Join-Path $PSScriptRoot '../../conformance-world_read/task/Invoke-WorldReadSmoke.ps1') -Target $item -Validation migration-runtime_core
}
