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
    if (-not $Condition) { throw "Phase 8 cancellation conformance failed: $Message" }
}

function Invoke-Json {
    param([ValidateSet('GET','POST','DELETE')][string]$Method, [string]$Path,
        [hashtable]$Headers = $auth, [object]$Body)
    $parameters = @{ Uri = "$base$Path"; Method = $Method; Headers = $Headers }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 30 -Compress
    }
    Invoke-RestMethod @parameters
}

function Acquire-Lease([int]$TtlMs = 60000) {
    Invoke-Json POST '/v0/control/emergency-release' | Out-Null
    $lease = Invoke-Json POST '/v0/control/acquire' -Body @{ ttlMs = $TtlMs }
    @{ Authorization = "Bearer $token"; 'X-MCP-Control-Lease' = $lease.leaseId }
}

function Start-Pipeline([hashtable]$Headers, [array]$Steps, [int]$TimeoutMs = 60000) {
    Invoke-Json POST '/v0/pipelines' -Headers $Headers -Body @{
        timeoutMs = $TimeoutMs; cleanupOnComplete = $true; steps = $Steps
    }
}

function Wait-Input([int]$Keys, [int]$Buttons, [int]$TimeoutMs = 3000) {
    $deadline = [DateTime]::UtcNow.AddMilliseconds($TimeoutMs)
    do {
        $state = Invoke-Json GET '/v0/input/state'
        if ($state.pressedKeyCount -ge $Keys -and $state.pressedButtonCount -ge $Buttons) { return $state }
        Start-Sleep -Milliseconds 25
    } while ([DateTime]::UtcNow -lt $deadline)
    throw "Input did not reach keys=$Keys buttons=$Buttons"
}

function Assert-CancelledAndStable([string]$OperationId, [int]$StabilityMs = 650) {
    $cancelled = Invoke-Json DELETE "/v0/operations/$OperationId"
    Assert-True ($cancelled.status -eq 'cancelled') "operation $OperationId must report cancelled"
    $deadline = [DateTime]::UtcNow.AddSeconds(3)
    do {
        $cleared = Invoke-Json GET '/v0/input/state'
        if ($cleared.pressedKeyCount -eq 0 -and $cleared.pressedButtonCount -eq 0) { break }
        Start-Sleep -Milliseconds 25
    } while ([DateTime]::UtcNow -lt $deadline)
    Assert-True ($cleared.pressedKeyCount -eq 0 -and $cleared.pressedButtonCount -eq 0) 'runtime-owned input must be released'
    $sequence = [long]$cleared.inputDispatchSequence
    Start-Sleep -Milliseconds $StabilityMs
    $stable = Invoke-Json GET '/v0/input/state'
    Assert-True ([long]$stable.inputDispatchSequence -eq $sequence) "operation $OperationId emitted post-cancel input"
    $terminal = Invoke-Json POST "/v0/operations/$OperationId/wait" -Body @{ timeoutMs = 1000 }
    Assert-True ([bool]$terminal.cancelled -and $terminal.state -eq 'cancelled') 'typed cancellation lifecycle must be terminal'
}

$session = Invoke-Json GET '/v0/session'
Assert-True ($session.target -eq $ExpectedTarget) "expected $ExpectedTarget"
$leaseHeaders = Acquire-Lease

$delay = Start-Pipeline $leaseHeaders @(
    @{ type = 'delay'; durationMs = 2000 },
    @{ type = 'key'; key = 87; scanCode = 17; action = 1 }
)
Start-Sleep -Milliseconds 100
Assert-CancelledAndStable $delay.operationId 2200

$held = Start-Pipeline $leaseHeaders @(
    @{ type = 'key'; key = 87; scanCode = 17; action = 1 },
    @{ type = 'delay'; durationMs = 2000 },
    @{ type = 'key'; key = 87; scanCode = 17; action = 0 }
)
Wait-Input 1 0 | Out-Null
Assert-CancelledAndStable $held.operationId

$multi = Start-Pipeline $leaseHeaders @(
    @{ type = 'key'; key = 87; scanCode = 17; action = 1 },
    @{ type = 'key'; key = 65; scanCode = 30; action = 1 },
    @{ type = 'delay'; durationMs = 2000 }
)
Wait-Input 2 0 | Out-Null
Assert-CancelledAndStable $multi.operationId

$mouse = Start-Pipeline $leaseHeaders @(
    @{ type = 'mouse.button'; button = 0; action = 1 },
    @{ type = 'delay'; durationMs = 2000 },
    @{ type = 'mouse.button'; button = 0; action = 0 }
)
Wait-Input 0 1 | Out-Null
Assert-CancelledAndStable $mouse.operationId

$drag = Start-Pipeline $leaseHeaders @(
    @{ type = 'mouse.drag'; fromX = 2; fromY = 2; toX = 100; toY = 100; button = 0; durationMs = 2000; segments = 40 }
)
Start-Sleep -Milliseconds 300
Assert-CancelledAndStable $drag.operationId 2200

$multiStep = Start-Pipeline $leaseHeaders @(
    @{ type = 'key.tap'; key = 32; scanCode = 57; holdMs = 25 },
    @{ type = 'delay'; durationMs = 2000 },
    @{ type = 'mouse.move'; x = 140; y = 80 }
)
Start-Sleep -Milliseconds 150
Assert-CancelledAndStable $multiStep.operationId 2200

$wait = Start-Pipeline $leaseHeaders @(
    @{ type = 'wait.until'; timeoutMs = 30000; condition = @{ type = 'event'; eventType = "never.$([guid]::NewGuid())" } },
    @{ type = 'key'; key = 87; scanCode = 17; action = 1 }
)
Start-Sleep -Milliseconds 100
Assert-CancelledAndStable $wait.operationId

$uiHold = Start-Pipeline $leaseHeaders @(
    @{ type = 'ui.action'; action = 'click'; source = 'explicit_coordinate'; coordinates = @{ x = 2; y = 2 }; holdMs = 2000 }
)
Wait-Input 0 1 | Out-Null
Assert-CancelledAndStable $uiHold.operationId 2200

$immediate = Start-Pipeline $leaseHeaders @(
    @{ type = 'delay'; durationMs = 1000 },
    @{ type = 'key'; key = 87; scanCode = 17; action = 1 }
)
Assert-CancelledAndStable $immediate.operationId 1200

$near = Start-Pipeline $leaseHeaders @(
    @{ type = 'delay'; durationMs = 100 },
    @{ type = 'key.tap'; key = 32; scanCode = 57; holdMs = 25 }
)
Start-Sleep -Milliseconds 80
$nearResult = Invoke-Json DELETE "/v0/operations/$($near.operationId)"
Assert-True ($nearResult.status -in @('cancelled','completed')) 'near-completion race must reach one valid terminal state'
Wait-Input 0 0 | Out-Null
$nearSequence = [long](Invoke-Json GET '/v0/input/state').inputDispatchSequence
Start-Sleep -Milliseconds 500
Assert-True ([long](Invoke-Json GET '/v0/input/state').inputDispatchSequence -eq $nearSequence) 'near-completion race emitted later input'

$disconnectHeaders = Acquire-Lease
$disconnectLease = $disconnectHeaders.'X-MCP-Control-Lease'
$webSocket = [System.Net.WebSockets.ClientWebSocket]::new()
$webSocket.Options.SetRequestHeader('Authorization', "Bearer $token")
$webSocket.Options.SetRequestHeader('X-MCP-Control-Lease', $disconnectLease)
$wsUri = [Uri]($base -replace '^http','ws')
$wsUri = [Uri]::new($wsUri, '/v0/events')
[void]$webSocket.ConnectAsync($wsUri, [Threading.CancellationToken]::None).GetAwaiter().GetResult()
$disconnectPipeline = Start-Pipeline $disconnectHeaders @(
    @{ type = 'key'; key = 87; scanCode = 17; action = 1 },
    @{ type = 'delay'; durationMs = 30000 }
)
Wait-Input 1 0 | Out-Null
$webSocket.Dispose()
$disconnectDeadline = [DateTime]::UtcNow.AddSeconds(3)
do {
    $disconnectOperation = Invoke-Json GET "/v0/operations/$($disconnectPipeline.operationId)"
    if ($disconnectOperation.status -eq 'cancelled') { break }
    Start-Sleep -Milliseconds 50
} while ([DateTime]::UtcNow -lt $disconnectDeadline)
Assert-True ($disconnectOperation.status -eq 'cancelled') 'control WebSocket disconnect must cancel lease-bound pipeline'
Wait-Input 0 0 | Out-Null

$expiringHeaders = Acquire-Lease 1000
$expiring = Start-Pipeline $expiringHeaders @(
    @{ type = 'key'; key = 87; scanCode = 17; action = 1 },
    @{ type = 'delay'; durationMs = 30000 }
)
Wait-Input 1 0 | Out-Null
Start-Sleep -Milliseconds 1300
$expired = Invoke-Json GET "/v0/operations/$($expiring.operationId)"
Assert-True ($expired.status -eq 'cancelled') 'lease expiry must cancel lease-bound pipeline'
Wait-Input 0 0 | Out-Null
Invoke-Json POST '/v0/control/emergency-release' | Out-Null

[pscustomobject]@{
    Target = $ExpectedTarget
    Scenarios = 12
    PostCancelInputSideEffects = 0
    DisconnectCleanup = 'PASS'
    LeaseExpiryCleanup = 'PASS'
    Result = 'PASS'
}
