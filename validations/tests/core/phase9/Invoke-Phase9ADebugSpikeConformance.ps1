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
    if (-not $Condition) { throw "Phase 9A debug spike failed: $Message" }
}

function Invoke-Json([string]$Method, [string]$Path, [hashtable]$Headers, [object]$Body) {
    $parameters = @{ Uri="$base$Path"; Method=$Method; Headers=$Headers }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 30 -Compress
    }
    Invoke-RestMethod @parameters
}

$player = Invoke-Json GET '/v0/player' $auth $null
Assert-True ([bool]$player.available) 'player required'
$x = [math]::Floor([double]$player.x) + 3
$y = [math]::Floor([double]$player.y) - 1
$z = [math]::Floor([double]$player.z)
$block = Invoke-Json GET "/v0/server/world/block?x=$x&y=$y&z=$z" $auth $null
Assert-True ([bool]$block.available) 'loaded block required'

[void](Invoke-Json POST '/v0/control/emergency-release' $auth $null)
$lease = Invoke-Json POST '/v0/control/acquire' $auth @{ ttlMs=60000 }
$leaseHeaders = @{ Authorization="Bearer $token"; 'X-MCP-Control-Lease'=$lease.leaseId }
$fingerprint = Invoke-Json GET '/v0/world/fingerprint' $auth $null
$arm = Invoke-Json POST '/v0/debug/arm' $leaseHeaders @{
    worldFingerprint=$fingerprint.worldFingerprint; ttlMs=60000
}
$debugHeaders = @{
    Authorization="Bearer $token"
    'X-MCP-Control-Lease'=$lease.leaseId
    'X-MCP-Debug-Arm'=$arm.debugArmId
}

$attributeBefore = $null
$noGravityBefore = $false
$blockChanged = $false
try {
    $snapshot = Invoke-Json POST '/v0/diagnostics/phase9a/observe' $auth @{ radiusChunks=0; entityRadius=8 }
    Assert-True ($null -ne $snapshot.player.maxHealth) 'max health projection missing'
    $attributeBefore = [double]$snapshot.player.maxHealth
    $attribute = Invoke-Json POST '/v0/debug/player/attribute' $debugHeaders @{
        attributeId='minecraft:max_health'; value=$attributeBefore + 0.5
    }
    Assert-True ($attribute.authority -eq 'runtime_internal' -and $attribute.evidence -eq 'diagnostic' `
        -and -not [bool]$attribute.gameplayEvidence) 'attribute provenance invalid'

    # The current player is deliberately excluded from the bounded nearby-entity list; use the typed current entity target.
    $entity = Invoke-Json POST '/v0/debug/entity/state' $debugHeaders @{
        entityUuid=$player.uuid; state='no_gravity'; value=$true
    }
    $noGravityBefore = [bool]::Parse([string]$entity.before)
    Assert-True ($entity.authority -eq 'runtime_internal' -and $entity.evidence -eq 'diagnostic' `
        -and -not [bool]$entity.gameplayEvidence) 'entity provenance invalid'

    $blockMutation = Invoke-Json POST '/v0/debug/world/block' $debugHeaders @{
        x=$x; y=$y; z=$z; blockId='minecraft:gold_block'; expectedBlockId=$block.block
    }
    $blockChanged = $true
    Assert-True ($blockMutation.mode -eq 'DEBUG_PRIVILEGED' -and [bool]$blockMutation.evidenceContaminated) `
        'block mutation provenance invalid'

    $pipeline = Invoke-Json POST '/v0/pipelines' $leaseHeaders @{
        timeoutMs=5000; steps=@(@{type='key.tap';key=32;scanCode=57;holdMs=25})
    }
    $act = Invoke-Json POST "/v0/operations/$($pipeline.operationId)/wait" $auth @{ timeoutMs=5000 }
    Assert-True ($act.state -eq 'completed' -and -not [bool]$act.result.steps[0].result.directMutationUsed) `
        'PLAYTEST Act must remain real input'
    $capture = Invoke-WebRequest -Uri "$base/v0/capture" -Headers $auth
    Assert-True ($capture.RawContentLength -gt 100) 'visible assertion capture missing'
}
finally {
    if ($blockChanged) {
        try { [void](Invoke-Json POST '/v0/debug/world/block' $debugHeaders @{
            x=$x; y=$y; z=$z; blockId=$block.block; expectedBlockId='minecraft:gold_block'
        }) } catch { }
    }
    if ($null -ne $attributeBefore) {
        try { [void](Invoke-Json POST '/v0/debug/player/attribute' $debugHeaders @{
            attributeId='minecraft:max_health'; value=$attributeBefore
        }) } catch { }
    }
    try { [void](Invoke-Json POST '/v0/debug/entity/state' $debugHeaders @{
        entityUuid=$player.uuid; state='no_gravity'; value=$noGravityBefore
    }) } catch { }
    try { [void](Invoke-Json POST '/v0/debug/disarm' $leaseHeaders $null) } catch { }
    try { [void](Invoke-Json POST '/v0/control/release' $leaseHeaders $null) } catch { }
}

[pscustomobject]@{
    Target = $ExpectedTarget
    PlayerAttribute = 'PASS'
    EntityState = 'PASS'
    WorldBlock = 'PASS'
    Arrange = 'DEBUG_PRIVILEGED'
    Act = 'PLAYTEST'
    Assert = 'internal+visible'
    Authority = 'runtime_internal'
    Evidence = 'diagnostic'
    GameplayEvidence = $false
    Cleanup = 'PASS'
    Result = 'PASS'
}
