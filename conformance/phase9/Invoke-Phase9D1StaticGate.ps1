[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 9D-1 static gate failed: $Message" }
}

Push-Location $root
try {
    $targets = @('1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.2-neoforge', '26.2-fabric')
    $markers = @(
        'PersistentWriteSafetyFoundation', 'StorageIdentity', 'StorageVersion',
        'LifecycleBarrier', 'WritePermit', 'UNKNOWN', 'STOPPED_OFFLINE', 'WritePrecondition', 'WriteContext',
        'storage.write', 'debug.storage', 'ATOMIC_MOVE', 'CANCELLED_BEFORE_COMMIT',
        'COMMITTED_BUT_POSTVERIFY_FAILED', 'RECOVERY_REQUIRED', 'ATOMICITY_POLICY_REQUIRED',
        'FileLock', 'cleanupStaleArtifacts'
    )
    $file = Join-Path $root 'runtime-safety/src/main/java/io/github/recrivenvi/minecraftprotocol/safety/PersistentWriteSafetyFoundation.java'
    Assert-True (Test-Path -LiteralPath $file) 'shared safety foundation missing'
    $source = Get-Content -LiteralPath $file -Raw
    foreach ($marker in $markers) {
        Assert-True ($source.Contains($marker)) "shared foundation missing marker $marker"
    }
    Assert-True ($source -notmatch 'NbtIo\.write|new\s+RegionFile\(') 'shared safety foundation must remain format-agnostic and non-Minecraft-writing'
    $hash = (Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash
    $hashes = @{}
    foreach ($target in $targets) {
        $build = Get-Content -LiteralPath (Join-Path $root "versions/$target/build.gradle") -Raw
        Assert-True ($build.Contains("project(':runtime-safety')")) "$target must consume the shared safety foundation"
        $hashes[$target] = $hash
    }

    $routes = @(
        'versions/1.20.1-forge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/ProbeTransport.java',
        'versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/ProbeTransport.java',
        'versions/26.1.2-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/ProbeTransport.java',
        'versions/26.2-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/ProbeTransport.java',
        'versions/26.2-fabric/src/client/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/ProbeTransport.java'
    )
    foreach ($route in $routes) {
        $source = Get-Content -LiteralPath (Join-Path $root $route) -Raw
        Assert-True ($source -notmatch 'storage\.write|storage\.world\.write') "$route must not expose a Persistent Write route in Phase 9D-1"
    }
    $openApi = Get-Content -LiteralPath (Join-Path $root 'protocol-schema/src/main/openapi/minecraft-control-v0.json') -Raw
    Assert-True ($openApi -notmatch 'storage\.write|storage\.world\.write') 'OpenAPI must not expose storage.write in the safety-foundation phase'

    [pscustomobject]@{
        Result = 'PASS'
        Targets = $targets.Count
        Foundation = 'PASS'
        FiveTargetHashes = $hashes
        PersistentWriteRoute = 'NOT_IMPLEMENTED'
        RealMinecraftStorageWrites = 0
    }
}
finally { Pop-Location }
