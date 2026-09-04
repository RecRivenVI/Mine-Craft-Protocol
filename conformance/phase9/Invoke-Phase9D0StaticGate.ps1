[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 9D-0 static gate failed: $Message" }
}

$targets = @(
    @{ Id='1.20.1-forge'; Source='src\main\java'; Runtime='ForgeProbeRuntime.java' },
    @{ Id='1.21.1-neoforge'; Source='src\main\java'; Runtime='NeoForgeProbeRuntime.java' },
    @{ Id='26.1.2-neoforge'; Source='src\main\java'; Runtime='NeoForgeProbeRuntime.java' },
    @{ Id='26.2-neoforge'; Source='src\main\java'; Runtime='NeoForgeProbeRuntime.java' },
    @{ Id='26.2-fabric'; Source='src\client\java'; Runtime='FabricProbeRuntime.java' }
)

$openApi = Get-Content -LiteralPath (Join-Path $root 'protocol-schema\src\main\openapi\minecraft-control-v0.json') -Raw
Assert-True ($openApi.Contains('/v0/diagnostics/phase9a/storage/read') `
    -and $openApi.Contains('PersistentStorageReadRequest') `
    -and $openApi.Contains('PersistentStorageReadResponse') `
    -and $openApi.Contains('"x-required-scope": "storage.read"')) `
    'OpenAPI must describe the bounded storage read contract'

foreach ($target in $targets) {
    $targetRoot = Join-Path $root "versions\$($target.Id)"
    $runtimeRoot = Join-Path $targetRoot "$($target.Source)\io\github\recrivenvi\minecraftprotocol\probe\runtime"
    $engine = Get-Content -LiteralPath (Join-Path $runtimeRoot 'Phase9ASpikeEngine.java') -Raw
    $adapter = Get-Content -LiteralPath (Join-Path $runtimeRoot 'PersistentStorageAdapter.java') -Raw
    $transport = Get-Content -LiteralPath (Join-Path $runtimeRoot 'ProbeTransport.java') -Raw
    $runtime = Get-Content -LiteralPath (Join-Path $runtimeRoot $target.Runtime) -Raw

    foreach ($marker in @(
        'PersistentStorageAdapter', 'playerDataRoot', 'server.isCurrentlySaving()',
        'observeWorldLifecycle', 'storageAdapter.lifecycleEpoch()',
        'boolean saveInProgress', 'String sessionEpoch', 'long lifecycleEpoch')) {
        Assert-True ($engine.Contains($marker)) "$($target.Id) engine missing $marker"
    }
    foreach ($marker in @(
        'MAX_SOURCE_BYTES', 'MAX_NBT_BYTES', 'ArrayBlockingQueue', 'QUEUE_CAPACITY',
        'PERSISTED_STORAGE_QUEUE_FULL', 'PERSISTED_STORAGE_CHANGED_DURING_READ',
        'PERSISTED_STORAGE_WORLD_IDENTITY_CHANGED', 'PERSISTED_STORAGE_CLOSED',
        'NbtAccounter', 'StandardOpenOption.READ', 'read_only_region_channel',
        'read_only_file_channel', 'lifecycleEpoch', 'cancelPending',
        'awaitTermination')) {
        Assert-True ($adapter.Contains($marker)) "$($target.Id) adapter missing $marker"
    }
    Assert-True ($adapter -notmatch 'NbtAccounter\.unlimitedHeap|new\s+RegionFile\(|RegionStorageInfo') `
        "$($target.Id) adapter must not use unlimited NBT or write-capable RegionFile"
    Assert-True ($engine -notmatch 'NbtAccounter\.unlimitedHeap|Files\.readAllBytes|writeCompressed\(') `
        "$($target.Id) engine must not expose unbounded persisted reads or writes"
    Assert-True ($transport.Contains('/v0/diagnostics/phase9a/storage/read') `
        -and $transport.Contains('requireScope("storage.read")')) `
        "$($target.Id) storage read route/scope missing"
    Assert-True ($runtime.Contains('phase9aStorageRead') `
        -and ($runtime.Contains('observeWorldLifecycle') -or $runtime.Contains('observeStorageLifecycle'))) `
        "$($target.Id) Runtime lifecycle/storage wiring missing"
}

[pscustomobject]@{
    Result = 'PASS'
    Targets = $targets.Count
    Adapter = 'PASS'
    BoundedNbt = 'PASS'
    ReadOnlyRegion = 'PASS'
    Lifecycle = 'PASS'
    Scope = 'storage.read'
    PersistentWrite = 'NOT_IMPLEMENTED'
    WireProtocolV1 = 'NOT_FROZEN'
}
