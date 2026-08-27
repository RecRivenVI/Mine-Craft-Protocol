[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string]$BaseUri,
    [Parameter(Mandatory = $true)] [string]$TokenFile,
    [Parameter(Mandatory = $true)] [string]$ExpectedTarget
)

$ErrorActionPreference = 'Stop'
$base = $BaseUri.TrimEnd('/')
$token = (Get-Content -LiteralPath $TokenFile -Raw).Trim()
$auth = @{ Authorization = "Bearer $token" }

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Phase 6 Dedicated Peer assertion failed: $Message" }
}

function Invoke-Json {
    param([string]$Method, [string]$Path, [hashtable]$Headers = $auth, [object]$Body)
    $parameters = @{ Uri = "$base$Path"; Method = $Method; Headers = $Headers }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    Invoke-RestMethod @parameters
}

function Wait-Operation {
    param([string]$OperationId, [int]$TimeoutSeconds = 20)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        Start-Sleep -Milliseconds 100
        $operation = Invoke-Json -Method GET -Path "/v0/operations/$OperationId"
    } while ($operation.status -eq 'running' -and [DateTime]::UtcNow -lt $deadline)
    Assert-True ($operation.status -eq 'completed') "operation $OperationId must complete"
    $operation
}

$session = Invoke-Json -Method GET -Path '/v0/session'
Assert-True ($session.target -eq $ExpectedTarget -and [bool]$session.inWorld) 'client must already be connected to a world'
$peer = Invoke-Json -Method GET -Path '/v0/server/peer'
Assert-True ([bool]$peer.connected -and $peer.protocol -eq 'peer-v0') 'dedicated Peer must be negotiated'
$probe = Invoke-Json -Method POST -Path '/v0/server/peer/probe'
Assert-True ($probe.source -eq 'dedicated_server_peer' -and [bool]$probe.peerAuthenticated) 'probe must return dedicated Peer evidence'

# This script must be launched without MCP_PEER_FORCE. A Peer result here therefore proves
# the production remote-server routing condition rather than the integrated serialization harness.
$player = Invoke-Json -Method GET -Path '/v0/server/player'
$entities = Invoke-Json -Method GET -Path '/v0/server/world/entities?radius=16'
Assert-True ($player.source -eq 'dedicated_server_peer' -and $player.authority -eq 'server_authoritative') 'player read must use the remote Peer'
Assert-True ([bool]$player.peerAuthenticated -and $player.dataSource -eq 'LIVE') 'player read must declare authenticated LIVE authority'
Assert-True (-not [bool]$player.storageAccessed) 'remote Peer read must not touch persistent storage'
Assert-True ($entities.source -eq 'dedicated_server_peer') 'entity query must use the remote Peer'
$stateFrame = Invoke-Json -Method POST -Path '/v0/state/frames' -Body @{
    reads = @(
        @{ providerId = 'minecraft:server/player' },
        @{ providerId = 'minecraft:server/world/entities'; query = @{ radius = 16 } }
    )
}
Assert-True ($stateFrame.reads[0].source -eq 'dedicated_server_peer') 'remote State Frame wrapper must preserve actual Peer source'
Assert-True ($stateFrame.reads[0].data.source -eq 'dedicated_server_peer') 'remote State Frame data must preserve actual Peer source'
Assert-True (-not [bool]$stateFrame.storageAccessed) 'remote State Frame must not touch storage'

Invoke-Json -Method POST -Path '/v0/control/emergency-release' | Out-Null
$lease = Invoke-Json -Method POST -Path '/v0/control/acquire' -Body @{ ttlMs = 30000 }
$leaseHeaders = @{ Authorization = "Bearer $token"; 'X-MCP-Control-Lease' = $lease.leaseId }
$disconnect = Invoke-Json -Method POST -Path '/v0/pipelines' -Headers $leaseHeaders -Body @{
    timeoutMs = 15000
    steps = @(
        @{ type = 'key'; key = 256; scanCode = 1; action = 1 },
        @{ type = 'key'; key = 256; scanCode = 1; action = 0 },
        @{ type = 'wait.until'; timeoutMs = 5000; condition = @{ type = 'screen'; classContains = 'PauseScreen' } },
        @{ type = 'ui.action'; action = 'click'; selector = @{ role = 'button'; label = 'Disconnect' } }
    )
}
Wait-Operation -OperationId $disconnect.operationId | Out-Null

$deadline = [DateTime]::UtcNow.AddSeconds(5)
do {
    Start-Sleep -Milliseconds 100
    $cleaned = Invoke-Json -Method GET -Path '/v0/server/peer'
} while ([bool]$cleaned.connected -and [DateTime]::UtcNow -lt $deadline)
Assert-True (-not [bool]$cleaned.connected -and $cleaned.pendingRequests -eq 0) 'remote disconnect must clear Peer state'
$afterDisconnect = Invoke-Json -Method GET -Path '/v0/session'
Assert-True (-not [bool]$afterDisconnect.inWorld) 'remote disconnect must leave the active world'

[pscustomobject]@{
    Result = 'PASS'
    Target = $ExpectedTarget
    Topology = 'dedicated_server_peer'
    PeerProtocol = $peer.protocol
    ServerTick = $player.serverTick
    Fixture = [bool]$probe.fixture
    Debug = [bool]$probe.debug
    Operator = [bool]$probe.operator
    DisconnectCleanup = $true
}
