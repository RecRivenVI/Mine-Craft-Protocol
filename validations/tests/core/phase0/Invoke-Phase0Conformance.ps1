[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUri,

    [Parameter(Mandatory = $true)]
    [string]$Token,

    [Parameter(Mandatory = $true)]
    [string]$ExpectedTarget,

    [switch]$RequireWorld
)

$ErrorActionPreference = 'Stop'
$headers = @{ Authorization = "Bearer $Token" }
$base = $BaseUri.TrimEnd('/')

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw "Conformance assertion failed: $Message"
    }
}

$unauthorized = Invoke-WebRequest -Uri "$base/v0/session" -SkipHttpErrorCheck
Assert-True ($unauthorized.StatusCode -eq 401) 'unauthenticated request must return 401'

$session = Invoke-RestMethod -Uri "$base/v0/session" -Headers $headers
Assert-True ($session.target -eq $ExpectedTarget) "target must be $ExpectedTarget"
Assert-True (-not [string]::IsNullOrWhiteSpace($session.thread)) 'session must report client thread'

$capabilities = Invoke-RestMethod -Uri "$base/v0/capabilities" -Headers $headers
Assert-True ($null -ne $capabilities.capabilities) 'capabilities object must exist'

$tree = Invoke-RestMethod -Uri "$base/v0/ui/tree" -Headers $headers
Assert-True ($null -ne $tree.children) 'UI tree must contain children array'
Assert-True ($null -ne $tree.screenRevision) 'UI tree must contain screenRevision'

$capture = Invoke-WebRequest -Uri "$base/v0/capture" -Headers $headers
$bytes = [byte[]]$capture.Content
Assert-True ($bytes.Length -gt 8) 'capture must contain PNG data'
$signature = [BitConverter]::ToString($bytes[0..7])
Assert-True ($signature -eq '89-50-4E-47-0D-0A-1A-0A') 'capture must have PNG signature'

$readiness = Invoke-RestMethod -Uri "$base/v0/readiness" -Headers $headers
Assert-True ($readiness.overall -eq 'ready') 'runtime readiness must be ready'
Assert-True ($readiness.hooks.compositeCapture -eq 'runtime_verified') 'capture hook must become runtime verified'

$trace = Invoke-RestMethod -Uri "$base/v0/trace" -Headers $headers
Assert-True ($null -ne $trace.screenRevision) 'trace must contain screenRevision'

$wsUri = [Uri](($base -replace '^http', 'ws') + '/v0/events')
$webSocket = [System.Net.WebSockets.ClientWebSocket]::new()
$webSocket.Options.SetRequestHeader('Authorization', "Bearer $Token")
$cancellation = [System.Threading.CancellationTokenSource]::new(5000)
try {
    [void]$webSocket.ConnectAsync($wsUri, $cancellation.Token).GetAwaiter().GetResult()
    $buffer = New-Object byte[] 4096
    $segment = [ArraySegment[byte]]::new($buffer)
    $received = $webSocket.ReceiveAsync($segment, $cancellation.Token).GetAwaiter().GetResult()
    $hello = [Text.Encoding]::UTF8.GetString($buffer, 0, $received.Count) | ConvertFrom-Json
    Assert-True ($hello.type -eq 'event.hello') 'WebSocket must emit event.hello'
    Assert-True ($hello.target -eq $ExpectedTarget) 'WebSocket hello target must match'
}
finally {
    $webSocket.Dispose()
    $cancellation.Dispose()
}

if ($RequireWorld) {
    Assert-True ([bool]$session.inWorld) 'RequireWorld needs an active world'
    $player = Invoke-RestMethod -Uri "$base/v0/player" -Headers $headers
    Assert-True ([bool]$player.available) 'player state must be available'
    $x = [math]::Floor($player.x)
    $y = [math]::Floor($player.y) - 1
    $z = [math]::Floor($player.z)
    $block = Invoke-RestMethod -Uri "$base/v0/world/block?x=$x&y=$y&z=$z" -Headers $headers
    Assert-True ([bool]$block.available) 'block below player must be available'
    $entities = Invoke-RestMethod -Uri "$base/v0/world/entities?radius=32" -Headers $headers
    Assert-True ($null -ne $entities.entities) 'entity query must return an array'
}

[pscustomobject]@{
    Target = $session.target
    InWorld = [bool]$session.inWorld
    Screen = $session.screenClass
    ScreenRevision = $session.screenRevision
    MenuRevision = $session.menuRevision
    CaptureBytes = $bytes.Length
    WebSocket = 'PASS'
    Readiness = $readiness.overall
    Result = 'PASS'
}
