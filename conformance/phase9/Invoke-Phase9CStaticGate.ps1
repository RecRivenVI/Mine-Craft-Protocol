[CmdletBinding()]param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
function Assert-True([bool]$Condition,[string]$Message) {
    if(-not $Condition) { throw "Phase 9C static gate failed: $Message" }
}
$schema=Get-Content (Join-Path $root 'protocol-schema\src\main\openapi\minecraft-control-v0.json') -Raw|ConvertFrom-Json
Assert-True ($schema.info.version -eq '0.0.1-control-r1') 'OpenAPI version'
foreach($path in @(
    '/v0/debug/capabilities','/v0/debug/mutations','/v0/debug/batches',
    '/v0/debug/evidence','/v0/debug/evidence/act/start','/v0/debug/evidence/act/finish')) {
    Assert-True ($null-ne$schema.paths.$path) "missing route $path"
}
foreach($name in @(
    'DebugCapabilitiesResponse','DebugMutationRequest','DebugMutationResult',
    'DebugBatchRequest','DebugBatchResult','GameplayActEvidence','ResourceVersionToken')) {
    Assert-True ($null-ne$schema.components.schemas.$name) "missing schema $name"
}
$targets=@(
    @{Id='1.20.1-forge';Src='src\main\java';Mixin='src\main\resources\minecraft_protocol_probe.mixins.json'},
    @{Id='1.21.1-neoforge';Src='src\main\java';Mixin='src\main\resources\minecraft_protocol_probe.mixins.json'},
    @{Id='26.1.2-neoforge';Src='src\main\java';Mixin='src\main\resources\minecraft_protocol_probe.mixins.json'},
    @{Id='26.2-neoforge';Src='src\main\java';Mixin='src\main\resources\minecraft_protocol_probe.mixins.json'},
    @{Id='26.2-fabric';Src='src\client\java';Mixin='src\client\resources\minecraft_protocol_probe.client.mixins.json'})
foreach($target in $targets) {
    $base=Join-Path $root "versions\$($target.Id)\$($target.Src)\io\github\recrivenvi\minecraftprotocol\probe"
    $engine=Get-Content (Join-Path $base 'runtime\Phase9ASpikeEngine.java') -Raw
    $transport=Get-Content (Join-Path $base 'runtime\ProbeTransport.java') -Raw
    $batch=Get-Content (Join-Path $base 'runtime\DebugBatchEngine.java') -Raw
    $state=Get-Content (Join-Path $base 'runtime\ProtocolState.java') -Raw
    $provider=Get-Content (Join-Path $base 'runtime\ProviderExecutionEngine.java') -Raw
    $providerApi=Get-Content (Join-Path $base 'api\AgentDataProviderV2.java') -Raw
    $mixin=Get-Content (Join-Path $root "versions\$($target.Id)\$($target.Mixin)") -Raw
    foreach($marker in @(
        'player.health.set','player.attribute.set','entity.no_gravity.set','world.block.set',
        'block_entity.custom_name.set','menu.slot.set','provider.mutate',
        'ResourceVersionVerifier.verify','TARGET_NOT_LOADED','DEBUG_REVISION_DID_NOT_ADVANCE',
        'normal_network_sync','gameplayEvidence')) {
        Assert-True ($engine.Contains($marker)) "$($target.Id) Debug marker $marker"
    }
    foreach($route in @(
        '/v0/debug/capabilities','/v0/debug/mutations','/v0/debug/batches',
        '/v0/debug/evidence/act/start','/v0/debug/evidence/act/finish')) {
        Assert-True ($transport.Contains($route)) "$($target.Id) route $route"
    }
    foreach($marker in @(
        'MAX_BATCH_ITEMS = 64','MAX_BATCH_BYTES = 256 * 1024',
        'MAX_PER_TICK_MUTATIONS = 4','cancellationBarrier','postCancelMutations')) {
        Assert-True ($batch.Contains($marker)) "$($target.Id) batch $marker"
    }
    foreach($marker in @(
        'requireDebugAuthorization','debug.write','DEBUG_SCOPE_DENIED',
        'STALE_SESSION_EPOCH','noteDebugMutation','startGameplayAct','CancellableOperationFuture')) {
        Assert-True ($state.Contains($marker)) "$($target.Id) authority $marker"
    }
    foreach($marker in @(
        'debugMutate','PROVIDER_DEBUG_SCHEMA_VIOLATION','PROVIDER_DEBUG_REVISION_DID_NOT_ADVANCE',
        'pendingDebugMutations','underlying().cancel')) {
        Assert-True ($provider.Contains($marker)) "$($target.Id) Provider Debug $marker"
    }
    foreach($marker in @(
        'mutate(DebugContext','mutationSchema','resultSchema','supportsResourceVersionPrecondition')) {
        Assert-True ($providerApi.Contains($marker)) "$($target.Id) Provider API $marker"
    }
    Assert-True ($mixin.Contains('BaseContainerBlockEntityAccessor')) "$($target.Id) typed BE Accessor"
    Assert-True ($engine -notmatch 'Class\.forName|java\.lang\.reflect|setAccessible') "$($target.Id) reflection backdoor"
    Assert-True ($transport -notmatch 'arbitraryPacket|ByteBuf.*debug') "$($target.Id) raw packet backdoor"
    Assert-True ($engine -notmatch 'storage\.world\.write|RegionFile.*write|writeCompressed') "$($target.Id) Phase 9D write leakage"
}
$companion=Get-Content (Join-Path $root 'companion\src\server.ts') -Raw
Assert-True ($companion.Contains("z.discriminatedUnion('operation'") `
    -and $companion.Contains("'/v0/debug/mutations'") `
    -and $companion.Contains("'/v0/debug/batches'")) 'typed aggregated MCP Debug surface'
[pscustomobject]@{
    Result='PASS'
    Targets=5
    OpenAPI='PASS'
    TypedDebug='PASS'
    ReflectionBackdoor='ABSENT'
    RawPacketBackdoor='ABSENT'
    PersistentWrite='NOT_STARTED'
    WireProtocolV1='NOT_FROZEN'
}
