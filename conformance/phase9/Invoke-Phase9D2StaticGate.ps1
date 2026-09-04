[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 9D-2 static gate failed: $Message" }
}

Push-Location $root
try {
    $targets = @(
        @{ Id = '1.20.1-forge'; Runtime = 'versions/1.20.1-forge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/ForgeProbeRuntime.java'; Engine = 'versions/1.20.1-forge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/Phase9ASpikeEngine.java'; Transport = 'versions/1.20.1-forge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/ProbeTransport.java' },
        @{ Id = '1.21.1-neoforge'; Runtime = 'versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/NeoForgeProbeRuntime.java'; Engine = 'versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/Phase9ASpikeEngine.java'; Transport = 'versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/ProbeTransport.java' },
        @{ Id = '26.1.2-neoforge'; Runtime = 'versions/26.1.2-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/NeoForgeProbeRuntime.java'; Engine = 'versions/26.1.2-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/Phase9ASpikeEngine.java'; Transport = 'versions/26.1.2-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/ProbeTransport.java' },
        @{ Id = '26.2-neoforge'; Runtime = 'versions/26.2-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/NeoForgeProbeRuntime.java'; Engine = 'versions/26.2-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/Phase9ASpikeEngine.java'; Transport = 'versions/26.2-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/ProbeTransport.java' },
        @{ Id = '26.2-fabric'; Runtime = 'versions/26.2-fabric/src/client/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/FabricProbeRuntime.java'; Engine = 'versions/26.2-fabric/src/client/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/Phase9ASpikeEngine.java'; Transport = 'versions/26.2-fabric/src/client/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/ProbeTransport.java' }
    )
    $foundation = Get-Content -LiteralPath (Join-Path $root 'runtime-safety/src/main/java/io/github/recrivenvi/minecraftprotocol/safety/PersistentWriteSafetyFoundation.java') -Raw
    Assert-True ($foundation.Contains('identityBasis=root_directory_lineage')) 'Storage identity must use stable root lineage'
    Assert-True ($foundation -notmatch 'levelSha256|lockSha256') 'mutable file content must not be identity material'
    foreach ($marker in @('AFTER_BACKUP_RECHECK', 'FINAL_RECHECK', 'FileLock', 'requireOwnershipLock', 'ATOMIC_MOVE', 'COMMITTED_BUT_POSTVERIFY_FAILED')) {
        Assert-True ($foundation.Contains($marker)) "foundation missing $marker"
    }
    $openApi = Get-Content -LiteralPath (Join-Path $root 'protocol-schema/src/main/openapi/minecraft-control-v0.json') -Raw
    foreach ($target in $targets) {
        $engine = Get-Content -LiteralPath (Join-Path $root $target.Engine) -Raw
        $runtime = Get-Content -LiteralPath (Join-Path $root $target.Runtime) -Raw
        $transport = Get-Content -LiteralPath (Join-Path $root $target.Transport) -Raw
        $adapterPath = if ($target.Id -eq '26.2-fabric') {
            "versions/26.2-fabric/src/client/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/PersistentStorageAdapter.java"
        } else {
            "versions/$($target.Id)/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/PersistentStorageAdapter.java"
        }
        $adapter = Get-Content -LiteralPath (Join-Path $root $adapterPath) -Raw
        Assert-True ($adapter.Contains('PersistentWriteSafetyFoundation.StorageIdentity')) "$($target.Id) storage read must use the canonical Storage Identity"
        foreach ($marker in @('writeLifecycle', 'observeStorageLifecycle', 'observeStorageShutdown')) {
            Assert-True ($engine.Contains($marker)) "$($target.Id) engine missing $marker"
        }
        foreach ($marker in @('getSingleplayerServer', 'isCurrentlySaving', 'getConnection', 'observeStorageLifecycle', 'observeStorageShutdown')) {
            Assert-True ($runtime.Contains($marker)) "$($target.Id) runtime missing $marker"
        }
        Assert-True ($transport -notmatch 'storage\.write|storage\.world\.write') "$($target.Id) must not expose storage.write"
    }
    Assert-True ($openApi -notmatch 'storage\.write|storage\.world\.write') 'OpenAPI must not expose storage.write'
    $test = Get-Content -LiteralPath (Join-Path $root 'versions/26.2-neoforge/src/test/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/PersistentWriteSafetyFoundationTest.java') -Raw
    foreach ($marker in @('legal-level-update', 'BACKUP_COPY', 'PRECOMMIT', 'externalSessionLockCompetition', 'CANCELLED_BEFORE_COMMIT')) {
        Assert-True ($test.Contains($marker)) "deterministic test missing $marker"
    }
    [pscustomobject]@{
        Result = 'PASS'
        Targets = $targets.Count
        StableIdentity = 'PASS'
        FinalRechecks = 'PASS'
        LifecycleHooks = 'PASS'
        WriteRoute = 'NOT_IMPLEMENTED'
        RealMinecraftStorageWrites = 0
    }
}
finally { Pop-Location }
