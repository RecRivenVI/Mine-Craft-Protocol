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
    if (-not $Condition) { throw "Phase 8 condition conformance failed: $Message" }
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

$session = Invoke-Json GET '/v0/session'
Assert-True ($session.target -eq $ExpectedTarget) "expected $ExpectedTarget"
$player = Invoke-Json GET '/v0/player'
$playerCondition = @{ type = 'player'; expected = @{ available = [bool]$player.available } }
$playerAssert = Invoke-Json POST '/v0/assert' -Body @{
    condition = $playerCondition
}
Assert-True ([bool]$playerAssert.passed) 'player condition must match live state'

if ([bool]$player.available) {
    $x = [math]::Floor([double]$player.x)
    $y = [math]::Floor([double]$player.y) - 1
    $z = [math]::Floor([double]$player.z)
    $block = Invoke-Json GET "/v0/world/block?x=$x&y=$y&z=$z"
    $blockCondition = @{ type = 'block'; x = $x; y = $y; z = $z; blockId = $block.block }
    $blockAssert = Invoke-Json POST '/v0/assert' -Body @{
        condition = $blockCondition
    }
    Assert-True ([bool]$blockAssert.passed) 'block condition must match LIVE block'
}
else {
    $blockCondition = @{ type = 'block'; x = 0; y = 0; z = 0; available = $false }
    $blockAssert = Invoke-Json POST '/v0/assert' -Body @{
        condition = $blockCondition
    }
    Assert-True ([bool]$blockAssert.passed) 'block condition must represent unavailable world honestly'
}

$entities = Invoke-Json GET '/v0/world/entities?radius=0'
$entityCondition = @{ type = 'entity'; radius = 0; minCount = @($entities.entities).Count }
$entityAssert = Invoke-Json POST '/v0/assert' -Body @{
    condition = $entityCondition
}
Assert-True ([bool]$entityAssert.passed) 'entity condition must evaluate bounded live query'

$tree = Invoke-Json GET '/v0/ui/tree'
$menuOpen = $null -ne $tree.menuId
$menuCondition = @{ type = 'menu'; open = $menuOpen }
$menuAssert = Invoke-Json POST '/v0/assert' -Body @{
    condition = $menuCondition
}
$inventoryAssert = Invoke-Json POST '/v0/assert' -Body @{
    condition = @{ type = 'inventory'; open = $menuOpen }
}
Assert-True ([bool]$menuAssert.passed -and [bool]$inventoryAssert.passed) 'menu/inventory conditions must share current Menu truth'

$cursor = [long](Invoke-Json GET '/v0/events/resync').resumeCursor
[void](Invoke-Json POST '/v0/diagnostics/events/stress' -Body @{ count = 1; payloadBytes = 0 })
$eventWait = Invoke-Json POST '/v0/wait/until' -Body @{
    timeoutMs = 2000
    condition = @{ type = 'event'; eventType = 'diagnostics.event.self_test'; afterSequence = $cursor }
}
Assert-True ([bool]$eventWait.passed -and [bool]$eventWait.waited) 'event wait must use EventHub ring'

$lease = Invoke-Json POST '/v0/control/acquire' -Body @{ ttlMs = 10000 }
$leaseHeaders = @{ Authorization = "Bearer $token"; 'X-MCP-Control-Lease' = $lease.leaseId }
try {
    $typedPipeline = Invoke-Json POST '/v0/pipelines' -Headers $leaseHeaders -Body @{
        timeoutMs = 5000
        steps = @(
            @{ type = 'wait.until'; timeoutMs = 2000; condition = $playerCondition },
            @{ type = 'wait.until'; timeoutMs = 2000; condition = $blockCondition },
            @{ type = 'assert.that'; condition = $entityCondition },
            @{ type = 'assert.that'; condition = $menuCondition }
        )
    }
    $typedCompleted = Invoke-Json POST "/v0/operations/$($typedPipeline.operationId)/wait" -Body @{ timeoutMs = 5000 }
    Assert-True ($typedCompleted.state -eq 'completed' -and $typedCompleted.result.stepCount -eq 4) `
        'Pipeline player/block waits and entity/menu asserts must reuse typed conditions'

    $pipelineCursor = [long](Invoke-Json GET '/v0/events/resync').resumeCursor
    $eventPipeline = Invoke-Json POST '/v0/pipelines' -Headers $leaseHeaders -Body @{
        timeoutMs = 5000
        steps = @(@{
            type = 'wait.until'; timeoutMs = 3000
            condition = @{ type = 'event'; eventType = 'diagnostics.event.self_test'; afterSequence = $pipelineCursor }
        })
    }
    [void](Invoke-Json POST '/v0/diagnostics/events/stress' -Body @{ count = 1; payloadBytes = 0 })
    $eventCompleted = Invoke-Json POST "/v0/operations/$($eventPipeline.operationId)/wait" -Body @{ timeoutMs = 5000 }
    Assert-True ($eventCompleted.state -eq 'completed') 'Pipeline event wait must reuse typed conditions'

    $started = Invoke-Json POST '/v0/pipelines' -Headers $leaseHeaders -Body @{
        timeoutMs = 5000; steps = @(@{ type = 'delay'; durationMs = 25 })
    }
    $completed = Invoke-Json POST "/v0/operations/$($started.operationId)/wait" -Body @{ timeoutMs = 5000 }
    Assert-True ($completed.state -eq 'completed') 'operation setup must complete'
    $operationAssert = Invoke-Json POST '/v0/assert' -Body @{
        condition = @{ type = 'operation'; operationId = $started.operationId; expected = @{ state = 'completed' } }
    }
    Assert-True ([bool]$operationAssert.passed) 'operation condition must use native lifecycle'
}
finally { Invoke-Json POST '/v0/control/release' -Headers $leaseHeaders | Out-Null }

[pscustomobject]@{
    Target = $ExpectedTarget
    Player = 'PASS'
    Block = 'PASS'
    Entity = 'PASS'
    Menu = 'PASS'
    Inventory = 'PASS'
    Event = 'PASS'
    Operation = 'PASS'
    PipelineTypedConditions = 'PASS'
    Result = 'PASS'
}
