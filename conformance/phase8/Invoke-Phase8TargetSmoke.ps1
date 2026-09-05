[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$BaseUri,
    [Parameter(Mandatory = $true)][string]$TokenFile,
    [Parameter(Mandatory = $true)][string]$ExpectedTarget,
    [ValidateSet('opengl','vulkan','any')][string]$ExpectedBackend = 'any',
    [switch]$EnterWorld,
    [switch]$RequireAuthoritative,
    [switch]$StayInWorld
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot '../control/ModeHelpers.ps1')
$base = $BaseUri.TrimEnd('/')
$token = (Get-Content -LiteralPath $TokenFile -Raw).Trim()
$auth = @{ Authorization = "Bearer $token" }

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw "Phase 8 target smoke failed: $Message" }
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

$unauthorized = Invoke-WebRequest -Uri "$base/v0/session" -SkipHttpErrorCheck
Assert-True ($unauthorized.StatusCode -eq 401) 'auth rejection must be active'
$readiness = Invoke-Json GET '/v0/readiness'
Assert-True ($readiness.overall -in @('ready','degraded')) 'runtime readiness must be typed'
$capabilities = Invoke-Json GET '/v0/capabilities'
$session = Invoke-Json GET '/v0/session'
Assert-True ($session.target -eq $ExpectedTarget) "expected $ExpectedTarget"
if ($EnterWorld -and -not [bool]$session.inWorld) {
    $titleReadyDeadline = [DateTime]::UtcNow.AddSeconds(15)
    do {
        if ($session.screenClass -match 'TitleScreen') { break }
        Start-Sleep -Milliseconds 100
        $session = Invoke-Json GET '/v0/session'
    } while ([DateTime]::UtcNow -lt $titleReadyDeadline)
    Assert-True ($session.screenClass -match 'TitleScreen') 'title screen must stabilize before UI control'
}
$tree = Invoke-Json GET '/v0/ui/tree'
Assert-True ($null -ne $tree.children -and $null -ne $tree.screenRevision) 'UI tree contract must be live'

$lease = Invoke-Json POST '/v0/control/acquire' -Body @{ ttlMs = 60000 }
$leaseHeaders = @{ Authorization = "Bearer $token"; 'X-MCP-Control-Lease' = $lease.leaseId }
try {
    $pipeline = Invoke-Json POST '/v0/pipelines' -Headers $leaseHeaders -Body @{
        timeoutMs = 5000
        cleanupOnComplete = $true
        steps = @(@{ type = 'key.tap'; key = 340; scanCode = 42; holdMs = 25 })
    }
    $operation = Invoke-Json POST "/v0/operations/$($pipeline.operationId)/wait" -Body @{ timeoutMs = 5000 }
    Assert-True ($operation.status -eq 'completed') 'GAME_ROUTED smoke input must complete'
    $inputResult = $operation.result.steps[0].result
    Assert-True ($inputResult.entryLayer -eq 'GAME_ROUTED_RAW') 'input provenance must identify the real entry layer'
    Assert-True (-not [bool]$inputResult.directMutationUsed) 'input smoke must not use direct mutation'

    if ($EnterWorld -and -not [bool]$session.inWorld) {
        for ($attempt = 0; $attempt -lt 10; $attempt++) {
            $session = Invoke-Json GET '/v0/session'
            if ($session.screenClass -match 'SelectWorldScreen') { break }
            Invoke-Json POST '/v0/ui/action' -Headers $leaseHeaders -Body @{
                action = 'click'; holdMs = 100; selector = @{ role = 'button'; label = 'Singleplayer' }
            } | Out-Null
            Start-Sleep -Milliseconds 500
        }
        Assert-True ($session.screenClass -match 'SelectWorldScreen') 'Singleplayer must open Select World'
        for ($attempt = 0; $attempt -lt 10; $attempt++) {
            $worldTree = Invoke-Json GET '/v0/ui/tree'
            $play = $worldTree.children | Where-Object label -eq 'Play Selected World'
            if ([bool]$play.active) { break }
            Invoke-Json POST '/v0/ui/action' -Headers $leaseHeaders -Body @{
                action = 'click'; holdMs = 100; source = 'explicit_coordinate'; coordinates = @{ x = 200; y = 75 }
            } | Out-Null
            Start-Sleep -Milliseconds 500
        }
        Assert-True ([bool]$play.active) 'first test world must become selected'
        Invoke-Json POST '/v0/ui/action' -Headers $leaseHeaders -Body @{
            action = 'click'; holdMs = 100; selector = @{ role = 'button'; label = 'Play Selected World' }
        } | Out-Null
        $worldDeadline = [DateTime]::UtcNow.AddSeconds(45)
        do {
            Start-Sleep -Milliseconds 100
            $session = Invoke-Json GET '/v0/session'
        } while (-not [bool]$session.inWorld -and [DateTime]::UtcNow -lt $worldDeadline)
        Assert-True ([bool]$session.inWorld) 'selected test world must load'
        do {
            Start-Sleep -Milliseconds 100
            $session = Invoke-Json GET '/v0/session'
        } while (-not [string]::IsNullOrEmpty($session.screenClass) -and [DateTime]::UtcNow -lt $worldDeadline)
        Assert-True ([string]::IsNullOrEmpty($session.screenClass)) 'world loading screen must close before player control'
        $worldControl = Invoke-Json POST '/v0/pipelines' -Headers $leaseHeaders -Body @{
            timeoutMs = 5000; cleanupOnComplete = $true
            steps = @(
                @{ type = 'key.tap'; key = 87; scanCode = 17; holdMs = 150 },
                @{ type = 'mouse.click'; x = 0; y = 0; button = 1; holdMs = 25 }
            )
        }
        $worldOperation = Invoke-Json POST "/v0/operations/$($worldControl.operationId)/wait" -Body @{ timeoutMs = 5000 }
        Assert-True ($worldOperation.state -eq 'completed') 'world player control must complete'
        $session = Invoke-Json GET '/v0/session'
    }

    $player = Invoke-Json GET '/v0/player'
    Assert-True ($null -ne $player.available) 'player state must be typed even outside a world'
    if ([bool]$player.available) {
        $x = [math]::Floor([double]$player.x)
        $y = [math]::Floor([double]$player.y) - 1
        $z = [math]::Floor([double]$player.z)
        $block = Invoke-Json GET "/v0/world/block?x=$x&y=$y&z=$z"
        Assert-True ($block.source -eq 'client_live' -and -not [bool]$block.storageAccessed) 'world query must remain LIVE-only'
        $command = Invoke-Json POST '/v0/command/player' -Headers $leaseHeaders -Body @{ command = 'help' }
        Assert-True ($command.mechanism -eq 'NORMAL_NETWORK' -and -not [bool]$command.permissionEscalated) 'player command must use current permissions and normal packet path'
        if ($RequireAuthoritative) {
            $serverPlayer = Invoke-Json GET '/v0/server/player'
            Assert-True ($serverPlayer.perspective -eq 'server_authoritative_live') 'integrated authority must be available'
            $serverBlock = Invoke-Json GET "/v0/server/world/block?x=$x&y=$y&z=$z"
            Assert-True ($serverBlock.perspective -eq 'server_authoritative_live' -and -not [bool]$serverBlock.storageAccessed) 'server block query must be authoritative LIVE state'
        }
    }

    $captureInfo = Invoke-Json GET '/v0/capture/info'
    $capture = Invoke-WebRequest -Uri "$base/v0/capture" -Headers $auth
    $bytes = [byte[]]$capture.Content
    Assert-True ($bytes.Length -gt 8 -and [BitConverter]::ToString($bytes[0..7]) -eq '89-50-4E-47-0D-0A-1A-0A') 'Composite Capture must be PNG'
    $readyDeadline = [DateTime]::UtcNow.AddSeconds(3)
    do {
        $readiness = Invoke-Json GET '/v0/readiness'
        if ($readiness.overall -eq 'ready') { break }
        Start-Sleep -Milliseconds 50
    } while ([DateTime]::UtcNow -lt $readyDeadline)
    Assert-True ($readiness.overall -eq 'ready') 'runtime must be ready after capture self-test'
    if ($ExpectedBackend -ne 'any') {
        Assert-True ($captureInfo.backend -eq $ExpectedBackend) "capture backend must be $ExpectedBackend"
    }

    $socket = [Net.WebSockets.ClientWebSocket]::new()
    $socket.Options.SetRequestHeader('Authorization', "Bearer $token")
    $timeout = [Threading.CancellationTokenSource]::new(5000)
    try {
        [void]$socket.ConnectAsync([Uri](($base -replace '^http','ws') + '/v0/events?type=diagnostics.event.self_test'), $timeout.Token).GetAwaiter().GetResult()
        $buffer = New-Object byte[] 8192
        $segment = [ArraySegment[byte]]::new($buffer)
        $helloResult = $socket.ReceiveAsync($segment, $timeout.Token).GetAwaiter().GetResult()
        $hello = [Text.Encoding]::UTF8.GetString($buffer, 0, $helloResult.Count) | ConvertFrom-Json
        Assert-True ($hello.type -eq 'event.hello') 'WS hello must be available'
        Set-AgentMode $base $auth OPERATE $lease.leaseId | Out-Null
        [void](Invoke-Json POST '/v0/diagnostics/events/stress' -Body @{ count = 1; payloadBytes = 0 })
        $eventResult = $socket.ReceiveAsync($segment, $timeout.Token).GetAwaiter().GetResult()
        $event = [Text.Encoding]::UTF8.GetString($buffer, 0, $eventResult.Count) | ConvertFrom-Json
        Assert-True ($event.type -eq 'diagnostics.event.self_test' -and $null -ne $event.sequence) 'WS event delivery must be live'
    }
    finally {
        $socket.Dispose()
        $timeout.Dispose()
    }

    # Diagnostic event publishing is OPERATE; player UI must explicitly reacquire.
    $lease = Invoke-Json POST '/v0/control/acquire' -Body @{ ttlMs = 60000 }
    $leaseHeaders.'X-MCP-Control-Lease' = $lease.leaseId

    if ($EnterWorld -and [bool]$session.inWorld -and -not $StayInWorld) {
        $pause = Invoke-Json POST '/v0/pipelines' -Headers $leaseHeaders -Body @{
            timeoutMs = 5000; steps = @(@{ type = 'key.tap'; key = 256; scanCode = 1; holdMs = 25 })
        }
        $pauseOperation = Invoke-Json POST "/v0/operations/$($pause.operationId)/wait" -Body @{ timeoutMs = 5000 }
        Assert-True ($pauseOperation.state -eq 'completed') 'pause input must complete'
        $pauseDeadline = [DateTime]::UtcNow.AddSeconds(5)
        do {
            Start-Sleep -Milliseconds 50
            $session = Invoke-Json GET '/v0/session'
        } while ($session.screenClass -notmatch 'PauseScreen' -and [DateTime]::UtcNow -lt $pauseDeadline)
        Assert-True ($session.screenClass -match 'PauseScreen') 'Escape must open Pause Screen'
        Invoke-Json POST '/v0/ui/action' -Headers $leaseHeaders -Body @{
            action = 'click'; holdMs = 100; selector = @{ role = 'button'; label = 'Save and Quit to Title' }
        } | Out-Null
        $titleDeadline = [DateTime]::UtcNow.AddSeconds(15)
        do {
            Start-Sleep -Milliseconds 100
            $session = Invoke-Json GET '/v0/session'
        } while ($session.screenClass -notmatch 'TitleScreen' -and [DateTime]::UtcNow -lt $titleDeadline)
        Assert-True ($session.screenClass -match 'TitleScreen') 'Save and Quit must return to Title Screen'
    }
}
finally {
    $remaining = Invoke-Json GET '/v0/control/status'
    if ($remaining.leaseId -eq $lease.leaseId) {
        Invoke-Json POST '/v0/control/release' -Headers $leaseHeaders | Out-Null
    }
}

$control = Invoke-Json GET '/v0/control/status'
$input = Invoke-Json GET '/v0/input/state'
Assert-True ($control.status -eq 'available') 'lease must be released'
Assert-True ($input.pressedKeyCount -eq 0 -and $input.pressedButtonCount -eq 0) 'disconnect must leave no held input'

[pscustomobject]@{
    Target = $ExpectedTarget
    BuildArtifact = 'CURRENT_PHASE8_HARDENED'
    Launch = 'PASS'
    Readiness = 'PASS'
    UI = 'PASS'
    Input = 'PASS'
    World = $(if ([bool]$player.available) { 'PASS' } else { 'NO_WORLD_TYPED' })
    Capture = $captureInfo.backend
    WebSocket = 'PASS'
    ShutdownPreconditions = 'PASS'
    Result = 'PASS'
}
