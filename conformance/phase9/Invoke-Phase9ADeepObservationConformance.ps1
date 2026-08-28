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
    if (-not $Condition) { throw "Phase 9A deep observation failed: $Message" }
}

function Invoke-Json([string]$Method, [string]$Path, [object]$Body) {
    $parameters = @{ Uri="$base$Path"; Method=$Method; Headers=$auth }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 40 -Compress
    }
    Invoke-RestMethod @parameters
}

$session = Invoke-Json GET '/v0/session' $null
Assert-True ($session.target -eq $ExpectedTarget -and [bool]$session.inWorld) 'Integrated test world is required'
$inventory = Invoke-Json GET '/v0/diagnostics/phase9a/inventory' $null
Assert-True ($inventory.phase -eq '9A' -and -not [bool]$inventory.persistentWriteImplemented) 'inventory contract mismatch'
$client = Invoke-Json GET '/v0/player' $null
$x = [math]::Floor([double]$client.x)
$y = [math]::Floor([double]$client.y) - 1
$z = [math]::Floor([double]$client.z)
$snapshot = Invoke-Json POST '/v0/diagnostics/phase9a/observe' @{
    radiusChunks = 1
    entityRadius = 32
    selectedBlocks = @(@{ x=$x; y=$y; z=$z })
}
Assert-True ($snapshot.player.uuid -eq $client.uuid) 'client/server player identity mismatch'
Assert-True ($snapshot.perspective -eq 'server_authoritative' -and $snapshot.dataSource -eq 'LIVE') 'authority mismatch'
Assert-True (-not [bool]$snapshot.chunkLoadRequested -and -not [bool]$snapshot.storageAccessed) 'LIVE observation crossed storage/load boundary'
Assert-True (@($snapshot.chunks).Count -eq 9) 'radius=1 must return exactly nine chunk identities'
Assert-True ($null -ne $snapshot.player.inventory -and $null -ne $snapshot.player.attributes) 'deep player projection missing'
Assert-True ($null -ne $snapshot.entities -and $null -ne $snapshot.blocks -and $null -ne $snapshot.blockEntities) 'world projection missing'
Assert-True ($snapshot.tickets.status -eq 'REQUIRES_NEW_HOOK') 'ticket limitation must be honest'
Assert-True ($snapshot.world.scheduledTickDetail -eq 'REQUIRES_NEW_HOOK') 'scheduled tick detail limitation must be honest'

$far = Invoke-Json POST '/v0/diagnostics/phase9a/observe' @{
    radiusChunks = 0
    entityRadius = 0
    selectedBlocks = @(@{ x=$x + 100000; y=$y; z=$z + 100000 })
}
$farBlock = @($far.blocks)[0]
Assert-True (-not [bool]$farBlock.available -and -not [bool]$farBlock.loadRequested) 'unloaded block must remain NOT_LOADED'

[pscustomobject]@{
    Target = $ExpectedTarget
    DeepPlayer = 'PARTIAL'
    DeepEntity = 'PARTIAL'
    BlockEntity = 'PARTIAL'
    ChunkInternals = 'PARTIAL'
    Tickets = 'REQUIRES_NEW_HOOK'
    ScheduledTicks = 'PARTIAL'
    ChunkCount = @($snapshot.chunks).Count
    EntityCount = @($snapshot.entities).Count
    BlockEntityCount = @($snapshot.blockEntities).Count
    NoForceLoad = 'PASS'
    Result = 'PASS'
}
