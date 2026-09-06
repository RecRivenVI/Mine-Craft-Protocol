[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUri,

    [string]$Token,

    [string]$TokenFile,

    [Parameter(Mandatory = $true)]
    [string]$ExpectedTarget,

    [switch]$RequireIntegratedServer
)

$ErrorActionPreference = 'Stop'
$base = $BaseUri.TrimEnd('/')

if ([string]::IsNullOrWhiteSpace($Token)) {
    if ([string]::IsNullOrWhiteSpace($TokenFile)) {
        throw 'Provide either -Token or -TokenFile.'
    }
    $Token = (Get-Content -LiteralPath $TokenFile -Raw).Trim()
}
if ([string]::IsNullOrWhiteSpace($Token)) {
    throw 'Resolved runtime token is empty.'
}

$auth = @{ Authorization = "Bearer $Token" }
$jsonContentType = 'application/json'

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw "Phase 2 conformance assertion failed: $Message"
    }
}

function Invoke-Probe {
    param(
        [ValidateSet('GET', 'POST', 'DELETE')]
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers = $auth,
        [object]$Body
    )
    $parameters = @{
        Uri = "$base$Path"
        Method = $Method
        Headers = $Headers
        SkipHttpErrorCheck = $true
    }
    if ($null -ne $Body) {
        $parameters.ContentType = $jsonContentType
        $parameters.Body = $Body | ConvertTo-Json -Compress
    }
    $response = Invoke-WebRequest @parameters
    $parsed = $null
    if ($response.Content.Length -gt 0 -and $response.Headers.'Content-Type' -match 'application/json') {
        $parsed = $response.Content | ConvertFrom-Json
    }
    [pscustomobject]@{
        Status = [int]$response.StatusCode
        Headers = $response.Headers
        Json = $parsed
    }
}

function Wait-ForInputClear {
    param([int]$TimeoutMs = 4000)
    $deadline = [DateTime]::UtcNow.AddMilliseconds($TimeoutMs)
    do {
        $state = Invoke-Probe -Method GET -Path '/v0/input/state'
        if ($state.Status -eq 200 -and
            $state.Json.pressedKeyCount -eq 0 -and
            $state.Json.pressedButtonCount -eq 0) {
            return $state.Json
        }
        Start-Sleep -Milliseconds 50
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Input state did not clear before the conformance timeout.'
}

$unauthorized = Invoke-Probe -Method GET -Path '/v0/session' -Headers @{}
Assert-True ($unauthorized.Status -eq 401) 'unauthenticated request must return 401'
Assert-True ($unauthorized.Json.error -eq 'UNAUTHORIZED') 'unauthenticated error must be typed'

$badOriginHeaders = @{
    Authorization = "Bearer $Token"
    Origin = 'https://untrusted.example'
}
$badOrigin = Invoke-Probe -Method GET -Path '/v0/session' -Headers $badOriginHeaders
Assert-True ($badOrigin.Status -eq 403) 'non-loopback Origin must return 403'
Assert-True ($badOrigin.Json.error -eq 'ORIGIN_REJECTED') 'Origin rejection must be typed'

$wrongProtocolHeaders = @{
    Authorization = "Bearer $Token"
    'X-MCP-Protocol-Version' = 'v999'
}
$wrongProtocol = Invoke-Probe -Method GET -Path '/v0/session' -Headers $wrongProtocolHeaders
Assert-True ($wrongProtocol.Status -eq 426) 'unsupported protocol version must return 426'
Assert-True ($wrongProtocol.Json.error -eq 'PROTOCOL_VERSION_UNSUPPORTED') 'protocol error must be typed'

$correlationId = "phase2-$([guid]::NewGuid())"
$correlationHeaders = @{
    Authorization = "Bearer $Token"
    'X-MCP-Request-Id' = $correlationId
    'X-MCP-Protocol-Version' = 'v0'
}
$session = Invoke-Probe -Method GET -Path '/v0/session' -Headers $correlationHeaders
Assert-True ($session.Status -eq 200) 'session request must succeed'
Assert-True ($session.Json.target -eq $ExpectedTarget) "target must be $ExpectedTarget"
Assert-True ($session.Json.requestId -eq $correlationId) 'requestId must round-trip in JSON'
Assert-True ([string]$session.Headers.'X-MCP-Request-Id' -eq $correlationId) 'requestId must round-trip in headers'
Assert-True ($session.Json.protocolVersion -eq 'v0') 'negotiated protocol must be returned'

foreach ($affinity in @('client', 'render')) {
    $threadProbe = Invoke-Probe -Method GET -Path "/v0/diagnostics/thread?affinity=$affinity"
    Assert-True ($threadProbe.Status -eq 200) "$affinity thread probe must succeed"
    Assert-True ([bool]$threadProbe.Json.ownerThreadObserved) "$affinity thread ownership must be observed"
}
if ($RequireIntegratedServer) {
    $serverProbe = Invoke-Probe -Method GET -Path '/v0/diagnostics/thread?affinity=server'
    Assert-True ($serverProbe.Status -eq 200) 'server thread probe requires an active integrated server'
    Assert-True ([bool]$serverProbe.Json.ownerThreadObserved) 'server thread ownership must be observed'
}

$security = Invoke-Probe -Method GET -Path '/v0/security/context'
Assert-True ($security.Status -eq 200) 'security context must be readable'
Assert-True ($security.Json.authentication -eq 'bearer') 'Bearer authentication must be reported'
Assert-True ($security.Json.bindAddress -eq '127.0.0.1') 'loopback bind must be reported'
Assert-True ($security.Json.grantedScopes -contains 'input') 'input scope must be granted for the probe'

$descriptors = Invoke-Probe -Method GET -Path '/v0/operations'
$inputDescriptor = $descriptors.Json.operations | Where-Object id -eq 'input.key'
Assert-True ($null -ne $inputDescriptor) 'input.key operation descriptor must exist'
Assert-True ([bool]$inputDescriptor.requiresControlLease) 'input.key must declare Control Lease'
Assert-True ([bool]$inputDescriptor.supportsIdempotency) 'input.key must declare idempotency support'
Assert-True ($inputDescriptor.supportedPreconditions -contains 'screenRevision') 'input must declare screen revision precondition'

$expiredHeaders = @{
    Authorization = "Bearer $Token"
    'X-MCP-Deadline-Ms' = '0'
}
$expired = Invoke-Probe -Method GET -Path '/v0/session' -Headers $expiredHeaders
Assert-True ($expired.Status -eq 408) 'elapsed deadline must return 408'
Assert-True ($expired.Json.error -eq 'REQUEST_DEADLINE_EXCEEDED') 'deadline failure must be typed'

$inputWithoutLease = Invoke-Probe -Method POST -Path '/v0/input/mouse/move' -Body @{ x = 1; y = 1 }
Assert-True ($inputWithoutLease.Status -eq 409) 'input without lease must return 409'
Assert-True ($inputWithoutLease.Json.error -eq 'CONTROL_LEASE_REQUIRED') 'missing lease must be typed'

$lease = Invoke-Probe -Method POST -Path '/v0/control/acquire' -Body @{ ttlMs = 10000 }
Assert-True ($lease.Status -eq 200) 'lease acquisition must succeed'
Assert-True (-not [string]::IsNullOrWhiteSpace($lease.Json.leaseId)) 'lease ID must be returned'
$leaseId = $lease.Json.leaseId

$leaseConflict = Invoke-Probe -Method POST -Path '/v0/control/acquire' -Body @{ ttlMs = 10000 }
Assert-True ($leaseConflict.Status -eq 409) 'second writer lease must conflict'
Assert-True ($leaseConflict.Json.error -eq 'CONTROL_LEASE_CONFLICT') 'lease conflict must be typed'

$leaseHeaders = @{
    Authorization = "Bearer $Token"
    'X-MCP-Control-Lease' = $leaseId
}
$renewed = Invoke-Probe -Method POST -Path '/v0/control/renew' -Headers $leaseHeaders -Body @{ ttlMs = 10000 }
Assert-True ($renewed.Status -eq 200 -and $renewed.Json.status -eq 'renewed') 'active lease must renew'

$staleHeaders = @{
    Authorization = "Bearer $Token"
    'X-MCP-Control-Lease' = $leaseId
    'X-MCP-Expected-Screen-Revision' = [string]([long]$session.Json.screenRevision + 1000)
}
$stale = Invoke-Probe -Method POST -Path '/v0/input/mouse/move' -Headers $staleHeaders -Body @{ x = 2; y = 2 }
Assert-True ($stale.Status -eq 409) 'stale screen revision must return 409'
Assert-True ($stale.Json.error -eq 'STALE_SCREEN_REVISION') 'stale resource failure must be typed'

$keyDown = Invoke-Probe -Method POST -Path '/v0/input/key' -Headers $leaseHeaders -Body @{
    key = 87
    scanCode = 17
    action = 1
    modifiers = 0
}
Assert-True ($keyDown.Status -eq 200) 'leased key-down must succeed'
$held = Invoke-Probe -Method GET -Path '/v0/input/state'
Assert-True ($held.Json.pressedKeys -contains 87) 'runtime must expose held virtual key state'

$idempotencyKey = "phase2-$([guid]::NewGuid())"
$idempotentHeaders = @{
    Authorization = "Bearer $Token"
    'X-MCP-Control-Lease' = $leaseId
    'X-MCP-Idempotency-Key' = $idempotencyKey
}
$move1 = Invoke-Probe -Method POST -Path '/v0/input/mouse/move' -Headers $idempotentHeaders -Body @{ x = 4; y = 4 }
$move2 = Invoke-Probe -Method POST -Path '/v0/input/mouse/move' -Headers $idempotentHeaders -Body @{ x = 4; y = 4 }
Assert-True ($move1.Status -eq 200 -and $move2.Status -eq 200) 'idempotent input requests must succeed'
Assert-True ($move1.Json.clientTick -eq $move2.Json.clientTick) 'same idempotency key must reuse the operation result'

$released = Invoke-Probe -Method POST -Path '/v0/control/release' -Headers $leaseHeaders
Assert-True ($released.Status -eq 200 -and $released.Json.status -eq 'released') 'explicit lease release must succeed'
[void](Wait-ForInputClear)

$expiringLease = Invoke-Probe -Method POST -Path '/v0/control/acquire' -Body @{ ttlMs = 1000 }
$expiringHeaders = @{
    Authorization = "Bearer $Token"
    'X-MCP-Control-Lease' = $expiringLease.Json.leaseId
}
$expiringKey = Invoke-Probe -Method POST -Path '/v0/input/key' -Headers $expiringHeaders -Body @{
    key = 65
    scanCode = 30
    action = 1
    modifiers = 0
}
Assert-True ($expiringKey.Status -eq 200) 'key-down under expiring lease must succeed'
[void](Wait-ForInputClear -TimeoutMs 5000)
$expiredLeaseStatus = Invoke-Probe -Method GET -Path '/v0/control/status'
Assert-True ($expiredLeaseStatus.Json.status -eq 'available') 'expired lease must become available'

$disconnectLease = Invoke-Probe -Method POST -Path '/v0/control/acquire' -Body @{ ttlMs = 10000 }
$disconnectLeaseId = $disconnectLease.Json.leaseId
$disconnectHeaders = @{
    Authorization = "Bearer $Token"
    'X-MCP-Control-Lease' = $disconnectLeaseId
}
$disconnectKey = Invoke-Probe -Method POST -Path '/v0/input/key' -Headers $disconnectHeaders -Body @{
    key = 68
    scanCode = 32
    action = 1
    modifiers = 0
}
Assert-True ($disconnectKey.Status -eq 200) 'disconnect cleanup setup key-down must succeed'

$wsUri = [Uri](($base -replace '^http', 'ws') + '/v0/events')
$webSocket = [System.Net.WebSockets.ClientWebSocket]::new()
$webSocket.Options.SetRequestHeader('Authorization', "Bearer $Token")
$webSocket.Options.SetRequestHeader('X-MCP-Control-Lease', $disconnectLeaseId)
$wsCancellation = [System.Threading.CancellationTokenSource]::new(5000)
try {
    [void]$webSocket.ConnectAsync($wsUri, $wsCancellation.Token).GetAwaiter().GetResult()
    $buffer = New-Object byte[] 4096
    $segment = [ArraySegment[byte]]::new($buffer)
    $received = $webSocket.ReceiveAsync($segment, $wsCancellation.Token).GetAwaiter().GetResult()
    $hello = [Text.Encoding]::UTF8.GetString($buffer, 0, $received.Count) | ConvertFrom-Json
    Assert-True ($hello.type -eq 'event.hello') 'control WebSocket must receive hello'
}
finally {
    $webSocket.Dispose()
    $wsCancellation.Dispose()
}
[void](Wait-ForInputClear -TimeoutMs 5000)
$disconnectStatus = Invoke-Probe -Method GET -Path '/v0/control/status'
Assert-True ($disconnectStatus.Json.status -eq 'available') 'control WebSocket disconnect must release its lease'

$operation = Invoke-Probe -Method POST -Path '/v0/operations/wait/screen' -Body @{
    classContains = "phase2-never-$([guid]::NewGuid())"
    timeoutMs = 30000
}
Assert-True ($operation.Status -eq 200) 'long wait operation must start'
Assert-True ($operation.Json.status -eq 'running') 'long wait operation must initially run'
$operationId = $operation.Json.operationId
$cancelled = Invoke-Probe -Method DELETE -Path "/v0/operations/$operationId"
Assert-True ($cancelled.Status -eq 200 -and $cancelled.Json.status -eq 'cancelled') 'operation cancellation must succeed'
$cancelledStatus = Invoke-Probe -Method GET -Path "/v0/operations/$operationId"
Assert-True ($cancelledStatus.Json.status -eq 'cancelled') 'cancelled operation status must remain observable'

$audit = Invoke-Probe -Method GET -Path '/v0/audit?limit=256'
Assert-True ($audit.Status -eq 200) 'audit endpoint must succeed'
$correlatedAudit = $audit.Json.entries | Where-Object requestId -eq $correlationId
Assert-True ($null -ne $correlatedAudit) 'audit must retain the correlated request'
Assert-True (($audit.Json.entries.sequence | Measure-Object -Maximum).Maximum -gt 0) 'audit sequence must advance'

[pscustomobject]@{
    Target = $session.Json.target
    Protocol = $session.Json.protocolVersion
    Authentication = 'PASS'
    HostOrigin = 'PASS'
    Scopes = 'PASS'
    Deadline = 'PASS'
    Preconditions = 'PASS'
    LeaseSingleWriter = 'PASS'
    LeaseExpiryCleanup = 'PASS'
    DisconnectCleanup = 'PASS'
    Idempotency = 'PASS'
    Cancellation = 'PASS'
    ClientRenderThread = 'PASS'
    ServerThread = $(if ($RequireIntegratedServer) { 'PASS' } else { 'NOT_REQUESTED' })
    Audit = 'PASS'
    Result = 'PASS'
}
