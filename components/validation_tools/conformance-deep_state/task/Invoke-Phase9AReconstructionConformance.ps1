[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BaseUri,
    [Parameter(Mandatory = $true)][string]$TokenFile,
    [Parameter(Mandatory = $true)][string]$ExpectedTarget
)

$ErrorActionPreference = 'Stop'
$base = $BaseUri.TrimEnd('/')
$token = (Get-Content -LiteralPath $TokenFile -Raw).Trim()
$auth = @{ Authorization = "Bearer $token" }

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 9A reconstruction failed: $Message" }
}

function Invoke-Json([string]$Method, [string]$Path, [hashtable]$Headers, [object]$Body) {
    $parameters = @{ Uri="$base$Path"; Method=$Method; Headers=$Headers }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 50 -Compress
    }
    Invoke-RestMethod @parameters
}

function Capture-Delta([string]$BaseSnapshotId) {
    Start-Sleep -Milliseconds 150
    Invoke-Json POST '/v0/diagnostics/phase9a/delta' $auth @{ baseSnapshotId=$BaseSnapshotId }
}

$player = Invoke-Json GET '/v0/player' $auth $null
Assert-True ([bool]$player.available) 'player required'
$x = [math]::Floor([double]$player.x) + 4
$y = [math]::Floor([double]$player.y) - 1
$z = [math]::Floor([double]$player.z)
$originalBlock = Invoke-Json GET "/v0/server/world/block?x=$x&y=$y&z=$z" $auth $null
Assert-True ([bool]$originalBlock.available) 'loaded block required'

[void](Invoke-Json POST '/v0/control/emergency-release' $auth $null)
$lease = Invoke-Json POST '/v0/control/acquire' $auth @{ ttlMs=60000 }
$leaseHeaders = @{ Authorization="Bearer $token"; 'X-MCP-Control-Lease'=$lease.leaseId }
$fingerprint = Invoke-Json GET '/v0/world/fingerprint' $auth $null
$arm = Invoke-Json POST '/v0/debug/arm' $leaseHeaders @{ worldFingerprint=$fingerprint.worldFingerprint; ttlMs=60000 }
$debugHeaders = @{
    Authorization="Bearer $token"
    'X-MCP-Control-Lease'=$lease.leaseId
    'X-MCP-Debug-Arm'=$arm.debugArmId
}

$blockChanged = $false
$stoneGiven = $false
$pigUuid = $null
$deltas = @()
$start = [DateTime]::UtcNow
try {
    $keyframe = Invoke-Json POST '/v0/diagnostics/phase9a/keyframe' $auth @{
        radiusChunks=1; entityRadius=32; selectedBlocks=@(@{x=$x;y=$y;z=$z})
    }
    $current = $keyframe.snapshotId

    # T1: PLAYTEST movement.
    $pipeline = Invoke-Json POST '/v0/pipelines' $leaseHeaders @{
        timeoutMs=5000; steps=@(
            @{type='key';key=87;scanCode=17;action=1},
            @{type='delay';durationMs=400},
            @{type='key';key=87;scanCode=17;action=0}
        )
    }
    [void](Invoke-Json POST "/v0/operations/$($pipeline.operationId)/wait" $auth @{timeoutMs=5000})
    $delta = Capture-Delta $current; $deltas += $delta; $current = $delta.snapshotId

    # T2: deterministic typed DEBUG Arrange; current-player command permissions remain unmodified.
    [void](Invoke-Json POST '/v0/debug/phase9a/scenario' $debugHeaders @{action='inventory_add_stone'})
    $stoneGiven = $true
    $delta = Capture-Delta $current; $deltas += $delta; $current = $delta.snapshotId

    # T3: typed DEBUG block Arrange, restored after evidence capture.
    [void](Invoke-Json POST '/v0/debug/world/block' $debugHeaders @{
        x=$x;y=$y;z=$z;blockId='minecraft:gold_block';expectedBlockId=$originalBlock.block
    })
    $blockChanged = $true
    $delta = Capture-Delta $current; $deltas += $delta; $current = $delta.snapshotId

    # T4-T6: typed DEBUG entity spawn, state change and removal.
    $spawn = Invoke-Json POST '/v0/debug/phase9a/scenario' $debugHeaders @{action='entity_spawn_pig'}
    $pigUuid = $spawn.spawnedEntityUuid
    $delta = Capture-Delta $current; $deltas += $delta; $current = $delta.snapshotId
    [void](Invoke-Json POST '/v0/debug/entity/state' $debugHeaders @{
        entityUuid=$pigUuid; state='no_gravity'; value=$true
    })
    $delta = Capture-Delta $current; $deltas += $delta; $current = $delta.snapshotId
    [void](Invoke-Json POST '/v0/debug/phase9a/scenario' $debugHeaders @{
        action='entity_remove'; entityUuid=$pigUuid
    })
    $pigUuid = $null
    $delta = Capture-Delta $current; $deltas += $delta; $current = $delta.snapshotId

    $reconstruction = Invoke-Json POST '/v0/diagnostics/phase9a/reconstruct' $auth @{
        keyframeId=$keyframe.snapshotId; deltaIds=@($deltas.deltaId)
    }
    Assert-True ($reconstruction.classification -eq 'EXACT' -and @($reconstruction.differences).Count -eq 0) `
        'bounded reconstruction must be exact'

    $types = @($deltas.operations.type | Select-Object -Unique)
    Assert-True ($types -contains 'player.state_change') 'player/inventory delta missing'
    Assert-True ($types -contains 'block.change') 'block delta missing'
    Assert-True ($types -contains 'entity.spawn') 'entity spawn delta missing'
    Assert-True ($types -contains 'entity.state_change') 'entity state delta missing'
    Assert-True ($types -contains 'entity.remove') 'entity removal delta missing'

    $elapsed = [Math]::Max(0.001, ([DateTime]::UtcNow - $start).TotalSeconds)
    $deltaBytes = [long](($deltas | Measure-Object encodedBytes -Sum).Sum)
    $bytesPerSecond = [long]($deltaBytes / $elapsed)
    [pscustomobject]@{
        Target = $ExpectedTarget
        KeyframeId = $keyframe.snapshotId
        KeyframeBytes = [long]$keyframe.encodedBytes
        DeltaCount = $deltas.Count
        DeltaBytes = $deltaBytes
        DeltaBytesPerSecond = $bytesPerSecond
        Estimate1Minute = $bytesPerSecond * 60
        Estimate20Minutes = $bytesPerSecond * 1200
        Estimate1Hour = $bytesPerSecond * 3600
        Acquisition = 'snapshot_diff'
        Perspective = 'server_authoritative'
        Reconstruction = $reconstruction.classification
        MissingInstrumentation = 'native inventory/entity/block-entity/chunk/scheduled-tick event hooks'
        Result = 'PASS'
    }
}
finally {
    if ($blockChanged) {
        try { [void](Invoke-Json POST '/v0/debug/world/block' $debugHeaders @{
            x=$x;y=$y;z=$z;blockId=$originalBlock.block;expectedBlockId='minecraft:gold_block'
        }) } catch { }
    }
    if ($stoneGiven) {
        try { [void](Invoke-Json POST '/v0/debug/phase9a/scenario' $debugHeaders @{action='inventory_remove_stone'}) } catch { }
    }
    if ($null -ne $pigUuid) {
        try { [void](Invoke-Json POST '/v0/debug/phase9a/scenario' $debugHeaders @{
            action='entity_remove'; entityUuid=$pigUuid
        }) } catch { }
    }
    try { [void](Invoke-Json POST '/v0/debug/disarm' $leaseHeaders $null) } catch { }
    try { [void](Invoke-Json POST '/v0/control/release' $leaseHeaders $null) } catch { }
}
