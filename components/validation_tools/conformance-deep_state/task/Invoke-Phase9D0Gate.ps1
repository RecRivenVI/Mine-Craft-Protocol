[CmdletBinding()]
param(
    [switch]$Offline,
    [switch]$SkipBuild,
    [switch]$SkipLive
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 9D-0 gate failed: $Message" }
}

Push-Location $root
try {
    $static = & '.\components\validation_tools\conformance-deep_state\task\Invoke-Phase9D0StaticGate.ps1'
    Assert-True ($static.Result -eq 'PASS') 'static gate failed'

    $build = 'SKIPPED'
    if (-not $SkipBuild) {
        $tasks = @(':protocol_schema:openApiValidate') + @(
            ':version:1.20.1-forge:build',
            ':version:1.21.1-neoforge:build',
            ':version:26.1.2-neoforge:build',
            ':version:26.3-neoforge:build',
            ':version:26.3-fabric:build'
        ) + @('--no-daemon')
        if ($Offline) { $tasks += '--offline' }
        & '.\gradlew.bat' @tasks | ForEach-Object { Write-Host $_ }
        Assert-True ($LASTEXITCODE -eq 0) 'five-target build/OpenAPI validation failed'
        $build = 'PASS'
    }

    $javaArgs = @(':version:26.3-neoforge:test', '--tests', 'io.github.recrivenvi.minecraftprotocol.probe.runtime.PersistentStorageAdapterTest', '--no-daemon')
    if ($Offline) { $javaArgs += '--offline' }
    & '.\gradlew.bat' @javaArgs | ForEach-Object { Write-Host $_ }
    Assert-True ($LASTEXITCODE -eq 0) 'PersistentStorageAdapter deterministic tests failed'

    Push-Location (Join-Path $root 'components/companion')
    try {
        & npm test | ForEach-Object { Write-Host $_ }
        Assert-True ($LASTEXITCODE -eq 0) 'Companion tests failed'
    }
    finally { Pop-Location }

    $phase8 = & '.\components\validation_tools\regression-v1_release\task\Invoke-Phase8LocalGate.ps1' -Offline
    Assert-True ($phase8.Result -eq 'PASS') 'Phase 8 regression gate failed'
    $phase9bStatic = @(
        & '.\components\validation_tools\conformance-deep_state\task\Invoke-Phase9BStaticGate.ps1'
        & '.\components\validation_tools\conformance-deep_state\task\Invoke-Phase9B1StaticGate.ps1'
        & '.\components\validation_tools\conformance-deep_state\task\Invoke-Phase9B2StaticGate.ps1'
    )
    Assert-True ($phase9bStatic.Result -notcontains 'FAIL') 'Phase 9B static regression failed'
    $phase9cStatic = & '.\components\validation_tools\conformance-deep_state\task\Invoke-Phase9CStaticGate.ps1'
    Assert-True ($phase9cStatic.Result -eq 'PASS') 'Phase 9C static regression failed'

    $live = 'SKIPPED'
    $liveResult = $null
    if (-not $SkipLive) {
        $liveResult = & '.\components\validation_tools\conformance-deep_state\task\Invoke-Phase9D0FiveTargetLiveGate.ps1' -Offline:$Offline
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
