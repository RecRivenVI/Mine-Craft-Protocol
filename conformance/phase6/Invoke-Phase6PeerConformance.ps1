[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUri,

    [Parameter(Mandatory = $true)]
    [string]$TokenFile,

    [Parameter(Mandatory = $true)]
    [string]$ExpectedTarget
)

$ErrorActionPreference = 'Stop'
$base = $BaseUri.TrimEnd('/')
$token = (Get-Content -LiteralPath $TokenFile -Raw).Trim()
$auth = @{ Authorization = "Bearer $token" }

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Phase 6 Peer conformance assertion failed: $Message" }
}

function Invoke-Json {
    param(
        [ValidateSet('GET', 'POST', 'DELETE')]
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = $auth,
        [object]$Body
    )
    $parameters = @{ Uri = "$base$Path"; Method = $Method; Headers = $Headers }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    Invoke-RestMethod @parameters
}

function Invoke-Error {
    param([string]$Method, [string]$Path, [hashtable]$Headers = $auth, [object]$Body)
    $parameters = @{ Uri = "$base$Path"; Method = $Method; Headers = $Headers; SkipHttpErrorCheck = $true }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    $response = Invoke-WebRequest @parameters
    [pscustomobject]@{ Status = [int]$response.StatusCode; Json = $response.Content | ConvertFrom-Json }
}

function Acquire-Lease {
    Invoke-Json -Method POST -Path '/v0/control/emergency-release' | Out-Null
    $lease = Invoke-Json -Method POST -Path '/v0/control/acquire' -Body @{ ttlMs = 60000 }
    @{
        Authorization = "Bearer $token"
        'X-MCP-Control-Lease' = $lease.leaseId
    }
}

function Wait-Operation {
    param([string]$OperationId, [int]$TimeoutSeconds = 65)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        Start-Sleep -Milliseconds 100
        $operation = Invoke-Json -Method GET -Path "/v0/operations/$OperationId"
    } while ($operation.status -eq 'running' -and [DateTime]::UtcNow -lt $deadline)
    Assert-True ($operation.status -ne 'running') "operation $OperationId must finish"
    $operation
}

$session = Invoke-Json -Method GET -Path '/v0/session'
Assert-True ($session.target -eq $ExpectedTarget) "target must be $ExpectedTarget"
if ($session.screenClass -match 'AccessibilityOnboardingScreen') {
    $onboardingLease = Acquire-Lease
    Invoke-Json -Method POST -Path '/v0/ui/action' -Headers $onboardingLease -Body @{
        action = 'click'; selector = @{ role = 'button'; label = 'Continue' }
    } | Out-Null
    Invoke-Json -Method POST -Path '/v0/wait/until' -Body @{
        condition = @{ type = 'screen'; classContains = 'TitleScreen' }; timeoutMs = 5000
    } | Out-Null
    $session = Invoke-Json -Method GET -Path '/v0/session'
}
Assert-True ($session.screenClass -match 'TitleScreen') 'conformance must begin at title'

$titlePeer = Invoke-Json -Method GET -Path '/v0/server/peer'
Assert-True (-not [bool]$titlePeer.connected) 'Peer must be disconnected before a game connection exists'
Assert-True ($titlePeer.pendingRequests -eq 0) 'Peer must not retain requests at title'
$unavailable = Invoke-Error -Method POST -Path '/v0/server/peer/probe'
Assert-True ($unavailable.Status -eq 409 -and $unavailable.Json.error -eq 'SERVER_PEER_UNAVAILABLE') 'Peer probe must fail with a typed error before negotiation'

$capabilities = Invoke-Json -Method GET -Path '/v0/capabilities'
Assert-True ($capabilities.capabilities.'server.peer.transport' -eq 'optional_peer_v0') 'target must declare optional Peer transport'

$leaseHeaders = Acquire-Lease
$worldPipeline = Invoke-Json -Method POST -Path '/v0/pipelines' -Headers $leaseHeaders -Body @{
    timeoutMs = 55000
    steps = @(
        @{ type = 'ui.action'; action = 'click'; selector = @{ role = 'button'; label = 'Singleplayer' } },
        @{ type = 'wait.until'; timeoutMs = 5000; condition = @{ type = 'screen'; classContains = 'SelectWorldScreen' } },
        @{ type = 'mouse.click'; x = 200; y = 75 },
        @{ type = 'delay'; durationMs = 250 },
        @{ type = 'ui.action'; action = 'click'; selector = @{ role = 'button'; label = 'Play Selected World' } },
        @{ type = 'wait.until'; timeoutMs = 30000; condition = @{ type = 'screen'; open = $false } }
    )
}
$worldResult = Wait-Operation -OperationId $worldPipeline.operationId
Assert-True ($worldResult.status -eq 'completed') 'world Arrange pipeline must complete'

$peerDeadline = [DateTime]::UtcNow.AddSeconds(10)
do {
    Start-Sleep -Milliseconds 100
    $peer = Invoke-Json -Method GET -Path '/v0/server/peer'
} while (-not [bool]$peer.connected -and [DateTime]::UtcNow -lt $peerDeadline)
Assert-True ([bool]$peer.connected) 'peer-v0 handshake must complete'
Assert-True ($peer.protocol -eq 'peer-v0') 'negotiated protocol must be peer-v0'

$probe = Invoke-Json -Method POST -Path '/v0/server/peer/probe'
Assert-True ([bool]$probe.read -and [bool]$probe.peerAuthenticated) 'Peer probe must return authenticated read authority'
Assert-True ($probe.source -eq 'dedicated_server_peer') 'Peer probe must carry Peer provenance'
Assert-True ($probe.dataSource -eq 'LIVE' -and -not [bool]$probe.storageAccessed) 'Peer must remain LIVE-only and avoid storage'

# The client must be launched with MCP_PEER_FORCE=true for these requests. This turns the
# integrated server into an executable serialization harness without changing production routing.
$player = Invoke-Json -Method GET -Path '/v0/server/player'
Assert-True ($player.source -eq 'dedicated_server_peer') 'server player read must cross the forced Peer route'
Assert-True ([bool]$player.peerAuthenticated -and $player.authority -eq 'server_authoritative') 'player read must carry authoritative Peer evidence'
$x = [math]::Floor($player.x)
$y = [math]::Floor($player.y) - 1
$z = [math]::Floor($player.z)
$block = Invoke-Json -Method GET -Path "/v0/server/world/block?x=$x&y=$y&z=$z"
Assert-True ($block.source -eq 'dedicated_server_peer' -and -not [bool]$block.chunkLoadRequested) 'block read must cross Peer without loading a chunk'
$entities = Invoke-Json -Method GET -Path '/v0/server/world/entities?radius=16'
Assert-True ($entities.source -eq 'dedicated_server_peer') 'entity query must cross Peer'
$stateFrame = Invoke-Json -Method POST -Path '/v0/state/frames' -Body @{
    reads = @(
        @{ providerId = 'minecraft:server/player' },
        @{ providerId = 'minecraft:server/world/entities'; query = @{ radius = 16 } }
    )
}
Assert-True ($stateFrame.reads.Count -eq 2) 'Peer State Frame must preserve selected reads'
Assert-True ($stateFrame.reads[0].source -eq 'dedicated_server_peer') 'State Frame provider wrapper must preserve actual Peer source'
Assert-True ($stateFrame.reads[0].data.source -eq 'dedicated_server_peer') 'State Frame data must preserve actual Peer source'
Assert-True (-not [bool]$stateFrame.storageAccessed) 'Peer State Frame must not touch storage'

$peerRecording = Invoke-Json -Method POST -Path '/v0/recordings' -Body @{
    intervalMs = 100
    durationMs = 700
    maxSamples = 4
    captureFrames = $false
    stateReads = @(@{ providerId = 'minecraft:server/player' })
    contactSheet = @{ enabled = $false }
}
$recordingDeadline = [DateTime]::UtcNow.AddSeconds(10)
do {
    Start-Sleep -Milliseconds 100
    $peerRecordingStatus = Invoke-Json -Method GET -Path "/v0/recordings/$($peerRecording.recordingId)"
} while ($peerRecordingStatus.status -notin @('completed', 'failed') -and [DateTime]::UtcNow -lt $recordingDeadline)
Assert-True ($peerRecordingStatus.status -eq 'completed' -and $peerRecordingStatus.writtenStates -gt 0) 'basic State Recording must consume Peer-backed State Frames'

$security = Invoke-Json -Method GET -Path '/v0/security/context'
if ($security.grantedScopes -contains 'fixture' -and $security.grantedScopes -contains 'debug') {
    Assert-True ([bool]$probe.fixture -and [bool]$probe.debug) 'explicit server flags and operator authority must enable Peer Fixture/Debug'
    $fixture = Invoke-Json -Method POST -Path '/v0/fixture/player/teleport' -Headers $leaseHeaders -Body @{
        x = $player.x; y = $player.y; z = $player.z
    }
    Assert-True ($fixture.source -eq 'dedicated_server_peer' -and $fixture.mode -eq 'FIXTURE') 'Fixture must cross Peer and contaminate evidence'

    $fingerprint = Invoke-Json -Method GET -Path '/v0/world/fingerprint'
    $arm = Invoke-Json -Method POST -Path '/v0/debug/arm' -Headers $leaseHeaders -Body @{
        worldFingerprint = $fingerprint.worldFingerprint; ttlMs = 30000
    }
    $debugHeaders = @{
        Authorization = "Bearer $token"
        'X-MCP-Control-Lease' = $leaseHeaders.'X-MCP-Control-Lease'
        'X-MCP-Debug-Arm' = $arm.debugArmId
    }
    $health = Invoke-Json -Method POST -Path '/v0/debug/player/health' -Headers $debugHeaders -Body @{ health = $player.health }
    Assert-True ($health.source -eq 'dedicated_server_peer' -and $health.mechanism -eq 'DIRECT_MUTATION') 'typed Debug health must cross Peer only after Debug Arm validation'
    $blockMutation = Invoke-Json -Method POST -Path '/v0/debug/world/block' -Headers $debugHeaders -Body @{
        x = $x; y = $y; z = $z; blockId = $block.block; expectedBlockId = $block.block
    }
    Assert-True ($blockMutation.source -eq 'dedicated_server_peer' -and $blockMutation.before -eq $blockMutation.after) 'typed Debug block must cross Peer with a value precondition'
}

$disconnect = Invoke-Json -Method POST -Path '/v0/pipelines' -Headers $leaseHeaders -Body @{
    timeoutMs = 15000
    steps = @(
        @{ type = 'key'; key = 256; scanCode = 1; action = 1 },
        @{ type = 'key'; key = 256; scanCode = 1; action = 0 },
        @{ type = 'wait.until'; timeoutMs = 5000; condition = @{ type = 'screen'; classContains = 'PauseScreen' } },
        @{ type = 'ui.action'; action = 'click'; selector = @{ role = 'button'; label = 'Save and Quit to Title' } },
        @{ type = 'wait.until'; timeoutMs = 10000; condition = @{ type = 'screen'; classContains = 'TitleScreen' } }
    )
}
$disconnectResult = Wait-Operation -OperationId $disconnect.operationId -TimeoutSeconds 20
Assert-True ($disconnectResult.status -eq 'completed') 'disconnect pipeline must return to title'

$cleanupDeadline = [DateTime]::UtcNow.AddSeconds(5)
do {
    Start-Sleep -Milliseconds 100
    $cleaned = Invoke-Json -Method GET -Path '/v0/server/peer'
} while ([bool]$cleaned.connected -and [DateTime]::UtcNow -lt $cleanupDeadline)
Assert-True (-not [bool]$cleaned.connected) 'Peer must reset on disconnect'
Assert-True ($cleaned.pendingRequests -eq 0) 'Peer disconnect must clean pending requests'

[pscustomobject]@{
    Result = 'PASS'
    Target = $ExpectedTarget
    Protocol = $probe.protocolVersion
    PeerProtocol = $peer.protocol
    ServerTick = $probe.serverTick
    Fixture = [bool]$probe.fixture
    Debug = [bool]$probe.debug
    DisconnectCleanup = $true
}
