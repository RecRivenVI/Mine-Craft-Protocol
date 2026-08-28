[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 9A static gate failed: $Message" }
}

$targets = @(
    @{ Id='1.20.1-forge'; Source='src\main\java' },
    @{ Id='26.2-neoforge'; Source='src\main\java' },
    @{ Id='26.2-fabric'; Source='src\client\java' }
)

$plan = Get-Content -LiteralPath (Join-Path $root 'PHASE9_IMPLEMENTATION_PLAN.md') -Raw
$executionPlan = Get-Content -LiteralPath (Join-Path $root 'PROJECT_EXECUTION_PLAN.md') -Raw
Assert-True ($plan.Contains('Phase 9A PASS WITH IDENTIFIED IMPLEMENTATION GAPS') `
    -and $plan.Contains('Phase 9B Entry Gate: READY FOR INDEPENDENT REVIEW')) `
    'Phase 9 implementation plan is not reconciled'
Assert-True ($executionPlan.Contains('Phase 8 Remote Parity: PASS') `
    -and $executionPlan.Contains('Phase 9: STARTED — Phase 9A ONLY') `
    -and $executionPlan.Contains('Wire Protocol v1: NOT FROZEN')) `
    'Project execution status is stale'

foreach ($target in $targets) {
    $runtime = Join-Path $root "versions\$($target.Id)\$($target.Source)\io\github\recrivenvi\minecraftprotocol\probe\runtime"
    $engine = Get-Content -LiteralPath (Join-Path $runtime 'Phase9ASpikeEngine.java') -Raw
    $transport = Get-Content -LiteralPath (Join-Path $runtime 'ProbeTransport.java') -Raw
    foreach ($marker in @(
        'phase9a.capability_inventory', 'phase9a.deep_observation', 'phase9a.storage.read',
        'phase9a.experimental_keyframe', 'phase9a.experimental_delta', 'phase9a.reconstruction',
        'getChunkNow', 'chunkLoadRequested', 'storageAccessOccurred', 'snapshot_diff',
        'runtime_internal', 'diagnostic', 'gameplayEvidence', 'REQUIRES_NEW_HOOK',
        'region_file_api_uses_write_capable_handle; no_write_requested')) {
        Assert-True ($engine.Contains($marker)) "$($target.Id) missing $marker"
    }
    foreach ($route in @(
        '/v0/diagnostics/phase9a/inventory', '/v0/diagnostics/phase9a/observe',
        '/v0/diagnostics/phase9a/storage/read', '/v0/diagnostics/phase9a/keyframe',
        '/v0/diagnostics/phase9a/delta', '/v0/diagnostics/phase9a/reconstruct',
        '/v0/debug/player/attribute', '/v0/debug/entity/state', '/v0/debug/phase9a/scenario')) {
        Assert-True ($transport.Contains($route)) "$($target.Id) missing route $route"
    }
    Assert-True ($engine -notmatch 'getChunk\([^,]+,[^,]+\)' -and $engine -notmatch 'setChunk|writeCompressed|\.save\(') `
        "$($target.Id) Phase 9A storage/observation must not force-load or persist writes"
    Assert-True ($engine -notmatch 'java\.lang\.reflect|Class\.forName|setAccessible') `
        "$($target.Id) Phase 9A must not expose reflection"
}

[pscustomobject]@{
    Result = 'PASS'
    Targets = $targets.Count
    PersistentWrite = 'NOT_IMPLEMENTED'
    ForceLoad = 'ABSENT'
    Reflection = 'ABSENT'
    WireProtocolV1 = 'NOT_FROZEN'
}
