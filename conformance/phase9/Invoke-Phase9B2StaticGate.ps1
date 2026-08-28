[CmdletBinding()]param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
function Assert-True([bool]$Condition,[string]$Message) {
    if(-not $Condition) { throw "Phase 9B.2 static gate failed: $Message" }
}
$schema=Get-Content (
    Join-Path $root 'protocol-schema\src\main\openapi\minecraft-control-v0.json') -Raw|ConvertFrom-Json
Assert-True ($schema.info.version -eq '0.0.1-phase9b2') 'OpenAPI version'
foreach($name in @('ResourceRevisionRef','ResourceVersionToken','ResourceVersionPrecondition')) {
    Assert-True ($null-ne$schema.components.schemas.$name) "schema $name"
}
$targets=@(
    @{Id='1.20.1-forge';Src='src\main\java'},
    @{Id='1.21.1-neoforge';Src='src\main\java'},
    @{Id='26.1.2-neoforge';Src='src\main\java'},
    @{Id='26.2-neoforge';Src='src\main\java'},
    @{Id='26.2-fabric';Src='src\client\java'})
foreach($target in $targets) {
    $base=Join-Path $root "versions\$($target.Id)\$($target.Src)\io\github\recrivenvi\minecraftprotocol\probe"
    $tracker=Get-Content (Join-Path $base 'runtime\ObservationRevisionTracker.java') -Raw
    $provider=Get-Content (Join-Path $base 'runtime\ProviderExecutionEngine.java') -Raw
    $engine=Get-Content (Join-Path $base 'runtime\Phase9ASpikeEngine.java') -Raw
    $api=Get-Content (Join-Path $base 'api\AgentDataProviderV2.java') -Raw
    $bounded=Get-Content (Join-Path $base 'runtime\BoundedTaskExecutor.java') -Raw
    $verifier=Get-Content (Join-Path $base 'runtime\ResourceVersionVerifier.java') -Raw
    $orderedArrays=$tracker.Contains('arraySemantics", "ordered_by_default')
    $genericArraySort=$tracker.Contains('values.sort(Comparator')
    Assert-True ($orderedArrays -and -not $genericArraySort) "$($target.Id) ordered canonical arrays"
    foreach($marker in @(
            'sessionEpoch','lifecycleId','revisionScope',
            'mutationPreconditionEligible','queryViewRevision')) {
        Assert-True ($tracker.Contains($marker)) "$($target.Id) tracker $marker"
    }
    foreach($marker in @('revisionScope','revisionSchema','revisionQueryInvariant')) {
        Assert-True ($api.Contains($marker)) "$($target.Id) provider API $marker"
    }
    foreach($marker in @(
            'provider_revision_regressed','provider_revision_inconsistent',
            'provider_worker_backpressure','revisionState')) {
        Assert-True ($provider.Contains($marker)) "$($target.Id) provider $marker"
    }
    $boundedQueue=$bounded.Contains('ArrayBlockingQueue')
    $rejectPolicy=$bounded.Contains('AbortPolicy')
    Assert-True ($boundedQueue -and $rejectPolicy) "$($target.Id) bounded executor"
    $revisionExecutor=$engine.Contains('new BoundedTaskExecutor')
    $lifecycleTracker=$engine.Contains('ObservationLifecycleTracker')
    Assert-True ($revisionExecutor -and $lifecycleTracker) "$($target.Id) revision/lifecycle"
    foreach($marker in @(
            'STALE_SESSION_EPOCH','RESOURCE_MISMATCH','STALE_RESOURCE_REVISION')) {
        Assert-True ($verifier.Contains($marker)) "$($target.Id) verifier $marker"
    }
}
[pscustomobject]@{
    Result='PASS'
    Targets=5
    Canonicalization='PASS'
    ProviderRevision='PASS'
    ResourceVersion='PASS'
    Executors='PASS'
    WireProtocolV1='NOT_FROZEN'
}
