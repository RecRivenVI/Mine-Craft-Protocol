[CmdletBinding()]param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
function A([bool]$condition,[string]$message) {
    if(-not $condition) { throw "Phase 9B.1 static gate failed: $message" }
}
$targets=@(
    @{Id='1.20.1-forge';Src='src\main\java'},
    @{Id='1.21.1-neoforge';Src='src\main\java'},
    @{Id='26.1.2-neoforge';Src='src\main\java'},
    @{Id='26.2-neoforge';Src='src\main\java'},
    @{Id='26.2-fabric';Src='src\client\java'})
$companion=Get-Content (Join-Path $root 'companion\src\server.ts') -Raw
A ($companion.Contains("minecraft_deep_observe") -and
        $companion.Contains("/v0/requests/") -and
        $companion.Contains("signal?.addEventListener('abort', cancelNative")) 'MCP Deep Observation cancellation propagation'
foreach($target in $targets) {
    $base=Join-Path $root "versions\$($target.Id)\$($target.Src)\io\github\recrivenvi\minecraftprotocol\probe"
    $tracker=Get-Content (Join-Path $base 'runtime\ObservationRevisionTracker.java') -Raw
    $provider=Get-Content (Join-Path $base 'runtime\ProviderExecutionEngine.java') -Raw
    $registry=Get-Content (Join-Path $base 'api\MinecraftProtocolProvidersV2.java') -Raw
    $schema=Get-Content (Join-Path $base 'api\ProviderSchemaRegistry.java') -Raw
    $engine=Get-Content (Join-Path $base 'runtime\Phase9ASpikeEngine.java') -Raw
    foreach($marker in @('DEFAULT_MAX_ENTRIES','LinkedHashMap','nextGeneration','canonicalize','evictedResourceReobservation')) {
        A ($tracker.Contains($marker)) "$($target.Id) tracker $marker"
    }
    foreach($marker in @(
            'provider_scope_denied','unsupported_perspective','mutation_not_allowed_in_observation',
            'storage_access_not_allowed_in_observation','data_loading_not_allowed_in_observation',
            'synchronous_entry_budget_exceeded','provider_quarantined','query_schema_violation',
            'schema_version_mismatch','provider_byte_budget_exceeded','underlying().cancel','pending.remove')) {
        A ($provider.Contains($marker)) "$($target.Id) provider $marker"
    }
    foreach($marker in @(
            'snapshotSchema','querySchema','threadAffinity','perspectives','requiredScopes',
            'revisionSource','deltaCapability','Duplicate Provider V2')) {
        A ($registry.Contains($marker)) "$($target.Id) registry $marker"
    }
    A ($schema.Contains('ValidationResult') -and $schema.Contains('unknown_schema')) "$($target.Id) executable schema registry"
    A ($engine.Contains('canonicalResourceRevisions(canonicalSnapshot)') -and
            $engine.IndexOf('canonicalResourceRevisions(canonicalSnapshot)') -lt
            $engine.IndexOf('applyProjection(visible, request)') -and
            $engine.Contains('minecraft-protocol-observation-revisions')) "$($target.Id) canonical before projection on detached revision worker"
    A ($engine.Contains('block_entity_serialized') -and $engine.Contains('providerExecution.execute')) "$($target.Id) formal hardening route"
}
[pscustomobject]@{
    Result='PASS'
    Targets=5
    Revision='PASS'
    ProviderPolicy='PASS'
    ProviderLifecycle='PASS'
    ProviderSchema='PASS'
}
