[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$LiveMatrixFile,
    [switch]$Offline
)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 8 release gate failed: $Message" }
}

$matrixPath = (Resolve-Path -LiteralPath $LiveMatrixFile).Path
$matrix = @(Get-Content -LiteralPath $matrixPath -Raw | ConvertFrom-Json)
$requiredTargets = @('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.2-neoforge','26.2-fabric')
foreach ($target in $requiredTargets) {
    Assert-True ($matrix.target -contains $target) "live matrix missing $target"
}
foreach ($target in @('26.2-neoforge','26.2-fabric')) {
    $backends = @($matrix | Where-Object target -eq $target | ForEach-Object backend)
    Assert-True ($backends -contains 'opengl' -and $backends -contains 'vulkan') "$target must smoke OpenGL and Vulkan"
}

Push-Location $root
try {
    $localArguments = @{}
    if ($Offline) { $localArguments.Offline = $true }
    $local = & '.\conformance\phase8\Invoke-Phase8LocalGate.ps1' @localArguments
    Assert-True ($local.Result -eq 'PASS') 'local hardening gate must pass'

    $results = foreach ($entry in $matrix) {
        $parameters = @{
            BaseUri = [string]$entry.baseUri
            TokenFile = [string]$entry.tokenFile
            ExpectedTarget = [string]$entry.target
            ExpectedBackend = [string]$entry.backend
        }
        if ([bool]$entry.requireAuthoritative) { $parameters.RequireAuthoritative = $true }
        if ([bool]$entry.enterWorld) { $parameters.EnterWorld = $true }
        $smoke = & '.\conformance\phase8\Invoke-Phase8TargetSmoke.ps1' @parameters
        Assert-True ($smoke.Result -eq 'PASS') "$($entry.target)/$($entry.backend) smoke failed"
        $smoke
    }

    $representative = $matrix | Where-Object {
        $_.target -eq '26.2-neoforge' -and $_.backend -eq 'opengl'
    } | Select-Object -First 1
    Assert-True ($null -ne $representative) 'hardening representative is missing'
    $cancellation = & '.\conformance\phase8\Invoke-Phase8CancellationConformance.ps1' `
        -BaseUri $representative.baseUri -TokenFile $representative.tokenFile -ExpectedTarget $representative.target
    Assert-True ($cancellation.Result -eq 'PASS') 'cancellation conformance failed'
    $events = & '.\conformance\phase8\Invoke-Phase8EventConformance.ps1' `
        -BaseUri $representative.baseUri -TokenFile $representative.tokenFile -ExpectedTarget $representative.target
    Assert-True ($events.Result -eq 'PASS') 'WebSocket reliability conformance failed'
    $security = & '.\conformance\phase8\Invoke-Phase8SecurityConformance.ps1' `
        -BaseUri $representative.baseUri -TokenFile $representative.tokenFile -ExpectedTarget $representative.target
    Assert-True ($security.Result -eq 'PASS') 'security conformance failed'
    $conditions = & '.\conformance\phase8\Invoke-Phase8ConditionConformance.ps1' `
        -BaseUri $representative.baseUri -TokenFile $representative.tokenFile -ExpectedTarget $representative.target
    Assert-True ($conditions.Result -eq 'PASS') 'typed Wait/Assert conformance failed'
    $recording = & '.\conformance\phase8\Invoke-Phase8RecordingHardeningConformance.ps1' `
        -BaseUri $representative.baseUri -TokenFile $representative.tokenFile -ExpectedTarget $representative.target
    Assert-True ($recording.Result -eq 'PASS') 'Recording budget/streaming conformance failed'

    [pscustomobject]@{
        Result = 'PASS'
        LocalGate = $local.Result
        LiveRuns = $results.Count
        Targets = $requiredTargets.Count
        Backends26_2 = 4
        CancellationScenarios = $cancellation.Scenarios
        EventReliability = $events.Result
        Security = $security.Result
        WaitAssert = $conditions.Result
        Recording = $recording.Result
        Results = $results
    }
}
finally { Pop-Location }
