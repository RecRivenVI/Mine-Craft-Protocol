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
    if (-not $Condition) { throw "Phase 8 event conformance failed: $Message" }
}

function Invoke-Json([string]$Method, [string]$Path, [object]$Body) {
    $parameters = @{ Uri = "$base$Path"; Method = $Method; Headers = $auth }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    Invoke-RestMethod @parameters
}

function Open-Events([string]$Query = '') {
    $socket = [System.Net.WebSockets.ClientWebSocket]::new()
    $socket.Options.SetRequestHeader('Authorization', "Bearer $token")
    $uri = [Uri](($base -replace '^http','ws') + "/v0/events$Query")
    $timeout = [Threading.CancellationTokenSource]::new(5000)
    try { [void]$socket.ConnectAsync($uri, $timeout.Token).GetAwaiter().GetResult() }
    finally { $timeout.Dispose() }
    $socket
}

function Receive-Event([System.Net.WebSockets.ClientWebSocket]$Socket, [int]$TimeoutMs = 5000) {
    $timeout = [Threading.CancellationTokenSource]::new($TimeoutMs)
    $memory = [IO.MemoryStream]::new()
    try {
        do {
            $buffer = New-Object byte[] 8192
            $segment = [ArraySegment[byte]]::new($buffer)
            $received = $Socket.ReceiveAsync($segment, $timeout.Token).GetAwaiter().GetResult()
            if ($received.MessageType -eq [Net.WebSockets.WebSocketMessageType]::Close) { throw 'WebSocket closed unexpectedly' }
            $memory.Write($buffer, 0, $received.Count)
        } while (-not $received.EndOfMessage)
        [Text.Encoding]::UTF8.GetString($memory.ToArray()) | ConvertFrom-Json
    }
    finally {
        $memory.Dispose()
        $timeout.Dispose()
    }
}

function Send-EventCommand([System.Net.WebSockets.ClientWebSocket]$Socket, [object]$Command) {
    $bytes = [Text.Encoding]::UTF8.GetBytes(($Command | ConvertTo-Json -Depth 20 -Compress))
    $segment = [ArraySegment[byte]]::new($bytes)
    [void]$Socket.SendAsync($segment, [Net.WebSockets.WebSocketMessageType]::Text, $true,
        [Threading.CancellationToken]::None).GetAwaiter().GetResult()
}

$session = Invoke-Json GET '/v0/session' $null
Assert-True ($session.target -eq $ExpectedTarget) "expected $ExpectedTarget"

$fast = Open-Events '?type=diagnostics.event.self_test'
try {
    $hello = Receive-Event $fast
    Assert-True ($hello.type -eq 'event.hello' -and $hello.target -eq $ExpectedTarget) 'hello must precede replay'
    $published = Invoke-Json POST '/v0/diagnostics/events/stress' @{ count = 16; payloadBytes = 32 }
    $last = 0L
    for ($index = 0; $index -lt 16; $index++) {
        $event = Receive-Event $fast
        Assert-True ($event.type -eq 'diagnostics.event.self_test') 'typed subscription filter returned wrong event'
        Assert-True ([long]$event.sequence -gt $last) 'event sequence must be monotonic'
        $last = [long]$event.sequence
    }
    Send-EventCommand $fast @{ type = 'event.ack'; sequence = $last }
}
finally { $fast.Dispose() }

$filtered = Open-Events '?type=event.screen.changed'
try {
    [void](Receive-Event $filtered)
    [void](Invoke-Json POST '/v0/diagnostics/events/stress' @{ count = 4; payloadBytes = 0 })
    $timedOut = $false
    try { [void](Receive-Event $filtered 350) } catch { $timedOut = $true }
    Assert-True $timedOut 'subscription filter must suppress non-matching diagnostics events'
}
finally { $filtered.Dispose() }

[void](Invoke-Json POST '/v0/diagnostics/events/stress' @{ count = 10; payloadBytes = 8 })
$resume = Open-Events ("?type=diagnostics.event.self_test&resumeFromSequence=$last")
try {
    Assert-True ((Receive-Event $resume).type -eq 'event.hello') 'resume connection must receive hello first'
    $replayed = Receive-Event $resume
    Assert-True ($replayed.type -eq 'diagnostics.event.self_test' -and [long]$replayed.sequence -gt $last) 'resume within ring must replay missed events'
}
finally { $resume.Dispose() }

$stalled = Open-Events '?type=diagnostics.event.self_test'
try {
    [void](Receive-Event $stalled)
    [void](Invoke-Json POST '/v0/diagnostics/events/stress' @{ count = 8192; payloadBytes = 4096 })
    $gapSeen = $false
    for ($attempt = 0; $attempt -lt 256 -and -not $gapSeen; $attempt++) {
        $event = Receive-Event $stalled 10000
        $gapSeen = $event.type -eq 'event.gap'
    }
    Assert-True $gapSeen 'stalled consumer must receive an explicit bounded-queue gap'
    Assert-True ([bool]$event.fullResyncRequired) 'backpressure gap must require full resync'
}
finally { $stalled.Dispose() }

$expired = Open-Events '?resumeFromSequence=0&type=diagnostics.event.self_test'
try {
    Assert-True ((Receive-Event $expired).type -eq 'event.hello') 'expired resume must still start with hello'
    $gap = Receive-Event $expired
    Assert-True ($gap.type -eq 'event.gap' -and [bool]$gap.fullResyncRequired) 'resume before ring start must declare gap'
    Send-EventCommand $expired @{ type = 'event.resync' }
    $resync = Receive-Event $expired 10000
    Assert-True ($resync.type -eq 'event.resync.snapshot') 'WS resync command must return a snapshot'
    Assert-True ($null -ne $resync.snapshot.session -and $null -ne $resync.snapshot.capabilities) 'resync must contain minimum authority snapshot'
    Assert-True (-not [bool]$resync.fullResyncRequired) 'successful resync must clear gap state'
}
finally { $expired.Dispose() }

$httpResync = Invoke-Json GET '/v0/events/resync' $null
Assert-True ($null -ne $httpResync.session -and $null -ne $httpResync.operations) 'HTTP resync snapshot must be complete'

[pscustomobject]@{
    Target = $ExpectedTarget
    Filter = 'PASS'
    FastConsumer = 'PASS'
    StalledConsumer = 'PASS'
    ResumeWithinRing = 'PASS'
    ResumeExpiredGap = 'PASS'
    FullResync = 'PASS'
    Result = 'PASS'
}
