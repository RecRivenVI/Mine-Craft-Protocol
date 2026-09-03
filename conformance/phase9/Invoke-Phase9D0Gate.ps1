[CmdletBinding()]
param(
    [switch]$Offline,
    [switch]$SkipBuild,
    [switch]$SkipLive
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 9D-0 gate failed: $Message" }
}

Push-Location $root
try {
    $static = & '.\conformance\phase9\Invoke-Phase9D0StaticGate.ps1'
    Assert-True ($static.Result -eq 'PASS') 'static gate failed'

    $build = 'SKIPPED'
    if (-not $SkipBuild) {
        $tasks = @(':protocol-schema:openApiValidate') + @(
            ':versions:1.20.1-forge:build',
            ':versions:1.21.1-neoforge:build',
            ':versions:26.1.2-neoforge:build',
            ':versions:26.2-neoforge:build',
            ':versions:26.2-fabric:build'
        ) + @('--no-daemon')
        if ($Offline) { $tasks += '--offline' }
        & '.\gradlew.bat' @tasks | ForEach-Object { Write-Host $_ }
        Assert-True ($LASTEXITCODE -eq 0) 'five-target build/OpenAPI validation failed'
        $build = 'PASS'
    }

    $javaArgs = @(':versions:26.2-neoforge:test', '--tests', 'io.github.recrivenvi.minecraftprotocol.probe.runtime.PersistentStorageAdapterTest', '--no-daemon')
    if ($Offline) { $javaArgs += '--offline' }
    & '.\gradlew.bat' @javaArgs | ForEach-Object { Write-Host $_ }
    Assert-True ($LASTEXITCODE -eq 0) 'PersistentStorageAdapter deterministic tests failed'

    Push-Location (Join-Path $root 'companion')
    try {
        & npm test | ForEach-Object { Write-Host $_ }
        Assert-True ($LASTEXITCODE -eq 0) 'Companion tests failed'
    }
    finally { Pop-Location }

    $phase8 = & '.\conformance\phase8\Invoke-Phase8LocalGate.ps1' -Offline
    Assert-True ($phase8.Result -eq 'PASS') 'Phase 8 regression gate failed'
    $phase9bStatic = @(
        & '.\conformance\phase9\Invoke-Phase9BStaticGate.ps1'
        & '.\conformance\phase9\Invoke-Phase9B1StaticGate.ps1'
        & '.\conformance\phase9\Invoke-Phase9B2StaticGate.ps1'
    )
    Assert-True ($phase9bStatic.Result -notcontains 'FAIL') 'Phase 9B static regression failed'
    $phase9cStatic = & '.\conformance\phase9\Invoke-Phase9CStaticGate.ps1'
    Assert-True ($phase9cStatic.Result -eq 'PASS') 'Phase 9C static regression failed'

    $live = 'SKIPPED'
    $liveResult = $null
    if (-not $SkipLive) {
        $liveResult = & '.\conformance\phase9\Invoke-Phase9D0FiveTargetLiveGate.ps1' -Offline:$Offline
        Assert-True ($liveResult.Result -eq 'PASS' -and $liveResult.Targets -eq 5) 'five-target live storage gate failed'
        $live = 'PASS'
    }

    [pscustomobject]@{
        Result = 'PASS'
        Phase = '9D-0'
        Static = 'PASS'
        OpenAPI = $(if ($SkipBuild) { 'SKIPPED' } else { 'PASS' })
        Build = $build
        JavaStorageTests = 'PASS'
        Companion = 'PASS'
        Phase8Regression = 'PASS'
        Phase9BRegression = 'PASS'
        Phase9CRegression = 'PASS'
        Live = $live
        PersistentWrite = 'NOT_IMPLEMENTED'
        WireProtocolV1 = 'NOT_FROZEN'
        Details = $liveResult
    }
}
finally { Pop-Location }
