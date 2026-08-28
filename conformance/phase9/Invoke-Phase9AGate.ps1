[CmdletBinding()]
param(
    [string]$BaseUri,
    [string]$TokenFile,
    [string]$ExpectedTarget,
    [switch]$Offline,
    [switch]$SkipBuild,
    [switch]$SkipLive
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 9A gate failed: $Message" }
}

Push-Location $root
try {
    $static = & '.\conformance\phase9\Invoke-Phase9AStaticGate.ps1'
    Assert-True ($static.Result -eq 'PASS') 'Static Gate failed'
    if (-not $SkipBuild) {
        $arguments = @(
            ':versions:1.20.1-forge:build',
            ':versions:26.2-neoforge:build',
            ':versions:26.2-fabric:build',
            '--no-daemon'
        )
        if ($Offline) { $arguments += '--offline' }
        & '.\gradlew.bat' @arguments
        if ($LASTEXITCODE -ne 0) { throw 'Representative Target build failed' }
    }
    $live = $null
    if (-not $SkipLive) {
        Assert-True (-not [string]::IsNullOrWhiteSpace($BaseUri) `
            -and -not [string]::IsNullOrWhiteSpace($TokenFile) `
            -and -not [string]::IsNullOrWhiteSpace($ExpectedTarget)) 'Live parameters are required'
        $parameters = @{ BaseUri=$BaseUri; TokenFile=$TokenFile; ExpectedTarget=$ExpectedTarget }
        $live = [ordered]@{
            Observation = & '.\conformance\phase9\Invoke-Phase9ADeepObservationConformance.ps1' @parameters
            Debug = & '.\conformance\phase9\Invoke-Phase9ADebugSpikeConformance.ps1' @parameters
            Storage = & '.\conformance\phase9\Invoke-Phase9AStorageReadConformance.ps1' @parameters
        }
        # Debug mutations share the bounded expensive-operation bucket. Let it refill before the reconstruction scenario.
        Start-Sleep -Seconds 5
        $live['Reconstruction'] = & '.\conformance\phase9\Invoke-Phase9AReconstructionConformance.ps1' @parameters
        foreach ($entry in $live.GetEnumerator()) {
            Assert-True ($entry.Value.Result -eq 'PASS') "$($entry.Key) live conformance failed"
        }
    }
    [pscustomobject]@{
        Result = 'PASS'
        Phase = '9A'
        Static = 'PASS'
        RepresentativeBuild = $(if ($SkipBuild) { 'SKIPPED' } else { 'PASS' })
        Live = $(if ($SkipLive) { 'SKIPPED' } else { 'PASS' })
        PersistentWrite = 'NOT_IMPLEMENTED'
        WireProtocolV1 = 'NOT_FROZEN'
        Details = $live
    }
}
finally { Pop-Location }
