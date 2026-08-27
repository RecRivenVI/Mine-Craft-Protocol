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
    if (-not $Condition) { throw "Phase 9D-0 storage conformance failed: $Message" }
}

function Invoke-Json([string]$Method, [string]$Path, [hashtable]$Headers, [object]$Body) {
    $parameters = @{ Uri = "$base$Path"; Method = $Method; Headers = $Headers; TimeoutSec = 15 }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 30 -Compress
    }
    Invoke-RestMethod @parameters
}

function Invoke-StorageRead([object]$Body) {
    for ($attempt = 1; $attempt -le 4; $attempt++) {
        try {
            return Invoke-Json POST '/v0/diagnostics/phase9a/storage/read' $auth $Body
        } catch {
            $diagnostic = $_.ErrorDetails.Message
            if ($attempt -eq 4 -or $diagnostic -notmatch 'PERSISTED_STORAGE_CHANGED_DURING_READ') { throw }
            $script:storageRaceRetries++
            Start-Sleep -Milliseconds (150 * $attempt)
        }
    }
    throw 'unreachable'
}

$script:storageRaceRetries = 0

$session = Invoke-Json GET '/v0/session' $auth $null
Assert-True ($session.target -eq $ExpectedTarget -and [bool]$session.inWorld) 'an Integrated Server world is required'
$security = Invoke-Json GET '/v0/security/context' $auth $null
Assert-True (@($security.grantedScopes) -contains 'storage.read') 'storage.read must be explicitly granted'
$capabilities = Invoke-Json GET '/v0/capabilities' $auth $null
Assert-True ($capabilities.capabilities.'storage.persistent.read' -match 'runtime_verified_bounded') `
    'bounded persistent read capability is not reported'

$player = Invoke-Json GET '/v0/player' $auth $null
Assert-True ([bool]$player.available) 'player is required'
$chunkX = [int][math]::Floor([double]$player.x / 16.0)
$chunkZ = [int][math]::Floor([double]$player.z / 16.0)
$world = Invoke-StorageRead @{ domain = 'world' }
$persistedPlayer = Invoke-StorageRead @{ domain = 'player' }
$chunk = Invoke-StorageRead @{ domain = 'chunk'; chunkX = $chunkX; chunkZ = $chunkZ }

foreach ($result in @($world, $persistedPlayer, $chunk)) {
    Assert-True ($result.target -eq $ExpectedTarget -and $result.phase -eq '9D-0') 'Target/phase marker mismatch'
    Assert-True ([bool]$result.formalRead -and $result.persistentReadScope -eq 'storage.read') 'formal read scope missing'
    Assert-True ($result.dataSource -eq 'PERSISTED' -and $result.source -eq 'persistent_storage') 'PERSISTED provenance missing'
    Assert-True ($result.consistency -eq 'last_saved_state' -and [bool]$result.stalePossibility) 'last-saved consistency missing'
    Assert-True ([bool]$result.storageAccessOccurred -and -not [bool]$result.writeImplemented) 'storage/write boundary invalid'
    Assert-True ($result.storageWorldIdentity -match '^[0-9a-f]{64}$') 'storage identity missing'
    Assert-True ($result.lifecycleState -eq 'active_file_snapshot' -and $result.saveState -eq 'not_saving_at_capture_file_stable') 'lifecycle marker missing'
    Assert-True ($result.queueCapacity -eq 8 -and $result.maxInFlight -eq 1 -and $result.queueDepth -ge 0) 'bounded worker marker missing'
}
Assert-True ($chunk.readStatus -eq 'ok' -and [bool]$chunk.available) 'current chunk must have persisted data'

# A far request exercises the missing-file path and proves that the adapter does not
# turn an unloaded query into a Chunk load or a hidden LIVE fallback.
$far = Invoke-StorageRead @{
    domain = 'chunk'; chunkX = $chunkX + 100000; chunkZ = $chunkZ + 100000
}
Assert-True ($far.readStatus -eq 'not_found' -and -not [bool]$far.available -and -not [bool]$far.targetLoaded) `
    'missing/unloaded persisted chunk must be an explicit not_found result'
Assert-True ($far.dataSource -eq 'PERSISTED' -and [bool]$far.storageAccessOccurred -and -not [bool]$far.writeImplemented) `
    'missing persisted chunk provenance changed'

$live = Invoke-Json GET "/v0/server/world/block?x=$($player.x -as [int])&y=$($player.y -as [int])&z=$($player.z -as [int])" $auth $null
Assert-True ($live.dataSource -eq 'LIVE' -and -not [bool]$live.storageAccessed) 'LIVE query crossed storage boundary'

[pscustomobject]@{
    Result = 'PASS'
    Target = $ExpectedTarget
    WorldMetadata = $world.readStatus
    PlayerRead = $persistedPlayer.readStatus
    LoadedChunkRead = $chunk.readStatus
    MissingChunk = $far.readStatus
    SaveReadRaceRetries = $script:storageRaceRetries
    NoImplicitLoad = 'PASS'
    LivePersistedSeparation = 'PASS'
    StorageIdentity = 'PASS'
    BoundedIO = 'PASS'
    PersistentWrite = 'NOT_IMPLEMENTED'
}
