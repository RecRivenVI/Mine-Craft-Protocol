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
    if (-not $Condition) { throw "Phase 9A storage read failed: $Message" }
}

function Invoke-Json([string]$Method, [string]$Path, [object]$Body) {
    $parameters = @{ Uri="$base$Path"; Method=$Method; Headers=$auth }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    Invoke-RestMethod @parameters
}

$player = Invoke-Json GET '/v0/player' $null
Assert-True ([bool]$player.available) 'player required'
$chunkX = [math]::Floor([double]$player.x / 16.0)
$chunkZ = [math]::Floor([double]$player.z / 16.0)
$world = Invoke-Json POST '/v0/diagnostics/phase9a/storage/read' @{ domain='world' }
$persistedPlayer = Invoke-Json POST '/v0/diagnostics/phase9a/storage/read' @{ domain='player' }
$chunk = Invoke-Json POST '/v0/diagnostics/phase9a/storage/read' @{ domain='chunk'; chunkX=$chunkX; chunkZ=$chunkZ }

foreach ($result in @($world,$persistedPlayer,$chunk)) {
    Assert-True ($result.dataSource -eq 'PERSISTED' -and [bool]$result.storageAccessOccurred) 'PERSISTED provenance missing'
    Assert-True (-not [string]::IsNullOrWhiteSpace($result.sideEffects) -and -not [bool]$result.writeImplemented) `
        'storage read must report effects and keep writes unimplemented'
    Assert-True ($result.consistency -eq 'last_saved_state' -and [bool]$result.stalePossibility) 'stale consistency marker missing'
    Assert-True ($result.worldFingerprint -match '^[0-9a-f]{64}$') 'world fingerprint missing'
}
Assert-True ([bool]$world.available -and [bool]$persistedPlayer.available -and [bool]$chunk.available) `
    'test world must have persisted world/player/chunk state'
Assert-True ([bool]$chunk.targetLoaded -and [bool]$chunk.liveWorldExists) 'loaded-vs-persisted comparison metadata missing'

[pscustomobject]@{
    Target = $ExpectedTarget
    WorldMetadata = 'PASS'
    PlayerRead = 'PASS'
    ChunkRead = 'PASS'
    ChunkTargetLoaded = [bool]$chunk.targetLoaded
    Consistency = $chunk.consistency
    StalePossible = [bool]$chunk.stalePossibility
    SideEffects = $chunk.sideEffects
    PersistentWrite = 'NOT_IMPLEMENTED'
    Result = 'PASS'
}
