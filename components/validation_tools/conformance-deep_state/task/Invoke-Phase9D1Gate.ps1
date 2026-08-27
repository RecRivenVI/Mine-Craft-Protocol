[CmdletBinding()]
param(
    [switch]$Offline,
    [switch]$SkipBuild,
    [switch]$SkipRegression
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 9D-1 gate failed: $Message" }
}

Push-Location $root
try {
    $static = & '.\components\validation_tools\conformance-deep_state\task\Invoke-Phase9D1StaticGate.ps1'
    Assert-True ($static.Result -eq 'PASS' -and $static.Targets -eq 5) 'five-target static foundation gate failed'

    $javaArgs = @(
        ':version:26.3-neoforge:test',
        '--tests', 'io.github.recrivenvi.minecraftprotocol.probe.runtime.PersistentWriteSafetyFoundationTest',
        '--no-daemon'
    )
    if ($Offline) { $javaArgs += '--offline' }
    & '.\gradlew.bat' @javaArgs | ForEach-Object { Write-Host $_ }
    Assert-True ($LASTEXITCODE -eq 0) 'synthetic identity/ownership/precondition/atomic tests failed'

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
        Assert-True ($LASTEXITCODE -eq 0) 'OpenAPI or five-target build failed'
        $build = 'PASS'
    }

    $regression = 'SKIPPED'
    if (-not $SkipRegression) {
        $phase8 = & '.\components\validation_tools\regression-v1_release\task\Invoke-Phase8LocalGate.ps1' -Offline
        Assert-True ($phase8.Result -eq 'PASS') 'Phase 8 regression gate failed'
        $phase9d0 = & '.\components\validation_tools\conformance-deep_state\task\Invoke-Phase9D0StaticGate.ps1'
        Assert-True ($phase9d0.Result -eq 'PASS') 'Phase 9D-0 read static regression failed'
        $phase9c = & '.\components\validation_tools\conformance-deep_state\task\Invoke-Phase9CStaticGate.ps1'
        Assert-True ($phase9c.Result -eq 'PASS') 'Phase 9C static regression failed'
        $regression = 'PASS'
    }

    [pscustomobject]@{
        Result = 'PASS'
        Phase = '9D-1'
        Static = 'PASS'
        SyntheticSafetyTests = 'PASS'
        OpenAPIAndBuild = $build
        Regression = $regression
        PersistentWriteRoute = 'NOT_IMPLEMENTED'
        RealMinecraftStorageWrites = 0
        EntryReview = 'READY'
    }
}
finally { Pop-Location }
