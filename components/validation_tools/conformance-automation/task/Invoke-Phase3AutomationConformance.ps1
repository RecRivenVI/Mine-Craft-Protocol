[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$BaseUri,

    [Parameter(Mandatory = $true)]
    [string]$TokenFile,

    [Parameter(Mandatory = $true)]
    [string]$ExpectedTarget,

    [switch]$ModernRenderFacts,

    [switch]$RequireWorldLoop
)

$ErrorActionPreference = 'Stop'
$base = $BaseUri.TrimEnd('/')
$token = (Get-Content -LiteralPath $TokenFile -Raw).Trim()
$auth = @{ Authorization = "Bearer $token" }

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) {
        throw "Phase 3 conformance assertion failed: $Message"
    }
}

function Invoke-Json {
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
    }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 20 -Compress
    }
    Invoke-RestMethod @parameters
}

function Invoke-ErrorResponse {
    param([string]$Path, [object]$Body)
    $response = Invoke-WebRequest -Uri "$base$Path" -Method Post -Headers $auth `
        -ContentType 'application/json' -Body ($Body | ConvertTo-Json -Depth 20 -Compress) `
        -SkipHttpErrorCheck
    [pscustomobject]@{
        Status = [int]$response.StatusCode
        Json = $response.Content | ConvertFrom-Json
    }
}

function Wait-Operation {
    param([string]$OperationId, [int]$TimeoutSeconds = 65)
    $deadline = [DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        Start-Sleep -Milliseconds 100
        $operation = Invoke-Json -Method GET -Path "/v0/operations/$OperationId"
    } while ($operation.status -eq 'running' -and [DateTime]::UtcNow -lt $deadline)
    if ($operation.status -eq 'running') {
        throw "Operation $OperationId did not finish before the conformance timeout."
    }
    return $operation
}

function Start-Pipeline {
    param([hashtable]$LeaseHeaders, [array]$Steps, [int]$TimeoutMs = 30000)
    $started = Invoke-Json -Method POST -Path '/v0/pipelines' -Headers $LeaseHeaders -Body @{
        timeoutMs = $TimeoutMs
        cleanupOnComplete = $true
        steps = $Steps
    }
    Assert-True ($started.status -eq 'running') 'pipeline must start as a cancellable operation'
    Wait-Operation -OperationId $started.operationId -TimeoutSeconds ([math]::Ceiling($TimeoutMs / 1000) + 10)
}

function Acquire-Lease {
    Invoke-Json -Method POST -Path '/v0/control/emergency-release' | Out-Null
    $lease = Invoke-Json -Method POST -Path '/v0/control/acquire' -Body @{ ttlMs = 60000 }
    return @{
        Authorization = "Bearer $token"
        'X-MCP-Control-Lease' = $lease.leaseId
    }
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
Assert-True ($session.screenClass -match 'TitleScreen') 'Phase 3 conformance must start at the title screen'

$tree = Invoke-Json -Method GET -Path '/v0/ui/tree'
$singleplayer = $tree.children | Where-Object label -eq 'Singleplayer'
Assert-True ($tree.coverage -eq 'semantic_native') 'title screen must report semantic_native coverage'
Assert-True ($singleplayer.role -eq 'button') 'standard button must have semantic button role'
Assert-True ($singleplayer.actions -contains 'click') 'actionable node must advertise click'
Assert-True ($null -ne $singleplayer.nodeRevision) 'node must carry a resource revision'

$resolved = Invoke-Json -Method POST -Path '/v0/ui/resolve' -Body @{
    role = 'button'
    label = 'Singleplayer'
}
Assert-True ($resolved.matchCount -eq 1) 'selector must resolve exactly one Singleplayer button'
Assert-True ($resolved.interactionPoint.source -eq 'bounds_center') 'selector must generate a bounds-center point'

$vision = Invoke-Json -Method GET -Path '/v0/ui/vision/context'
Assert-True ([bool]$vision.visionFallbackAvailable) 'vision fallback must be available'
Assert-True ($vision.coordinateSpace -eq 'gui_scaled') 'vision coordinate space must be explicit'

$descriptors = Invoke-Json -Method GET -Path '/v0/operations'
$pipelineDescriptor = $descriptors.operations | Where-Object id -eq 'pipeline.execute'
Assert-True ([bool]$pipelineDescriptor.requiresControlLease) 'pipeline must declare Lease requirement'
Assert-True ([bool]$pipelineDescriptor.supportsCancellation) 'pipeline must declare cancellation'
Assert-True ($pipelineDescriptor.supportedPreconditions -contains 'leasePerStep') 'pipeline must re-check Lease per step'

$leaseHeaders = Acquire-Lease

$fixture = Invoke-Json -Method POST -Path '/v0/diagnostics/ui/test-screen' -Headers $leaseHeaders
Assert-True ($fixture.mechanism -eq 'DIRECT') 'test screen Arrange mechanism must be DIRECT'
Assert-True ([bool]$fixture.evidenceContaminated) 'test screen Arrange must mark evidence contamination'
$fixtureTree = Invoke-Json -Method GET -Path '/v0/ui/tree'
Assert-True ($fixtureTree.screenClass -match 'AutomationProbeScreen') 'standard Mod test Screen must be active'
Assert-True (($fixtureTree.children | Where-Object label -eq 'Probe Action').role -eq 'button') 'Mod button must be semantic'

$fixturePipeline = Start-Pipeline -LeaseHeaders $leaseHeaders -Steps @(
    @{ type = 'assert.that'; condition = @{ type = 'ui.exists'; selector = @{ role = 'button'; label = 'Probe Action' } } },
    @{ type = 'ui.action'; action = 'click'; selector = @{ role = 'button'; label = 'Probe Action' } },
    @{ type = 'wait.until'; timeoutMs = 5000; condition = @{ type = 'ui.exists'; selector = @{ role = 'button'; label = 'Probe Action Complete' } } },
    @{ type = 'key.chord'; holdMs = 80; keys = @(@{ key = 340; scanCode = 42 }, @{ key = 341; scanCode = 29 }) },
    @{ type = 'mouse.drag'; fromX = 20; fromY = 70; toX = 20; toY = 100; durationMs = 120; segments = 4 },
    @{ type = 'ui.action'; action = 'click'; selector = @{ role = 'button'; label = 'Close Probe' } },
    @{ type = 'wait.until'; timeoutMs = 5000; condition = @{ type = 'screen'; classContains = 'TitleScreen' } }
)
Assert-True ($fixturePipeline.status -eq 'completed') 'standard Mod GUI pipeline must complete'
Assert-True ($fixturePipeline.result.stepCount -eq 7) 'standard Mod GUI pipeline must execute every step'

$options = Invoke-Json -Method POST -Path '/v0/ui/resolve' -Body @{ role = 'button'; label = 'Options...' }
$visionAction = Invoke-Json -Method POST -Path '/v0/ui/action' -Headers $leaseHeaders -Body @{
    action = 'click'
    source = 'vision'
    coordinates = @{
        x = $options.interactionPoint.x
        y = $options.interactionPoint.y
    }
}
Assert-True ($visionAction.targetingSource -eq 'vision') 'coordinate fallback must preserve vision provenance'
$optionsWait = Invoke-Json -Method POST -Path '/v0/wait/until' -Body @{
    condition = @{ type = 'screen'; classContains = 'OptionsScreen' }
    timeoutMs = 5000
}
Assert-True ([bool]$optionsWait.passed) 'vision coordinate click must open Options'
Invoke-Json -Method POST -Path '/v0/ui/action' -Headers $leaseHeaders -Body @{
    action = 'click'
    selector = @{ role = 'button'; label = 'Done' }
} | Out-Null

$roundTrip = Start-Pipeline -LeaseHeaders $leaseHeaders -Steps @(
    @{ type = 'assert.that'; condition = @{ type = 'ui.exists'; selector = @{ role = 'button'; label = 'Singleplayer' } } },
    @{ type = 'ui.action'; action = 'click'; selector = @{ role = 'button'; label = 'Singleplayer' } },
    @{ type = 'wait.until'; timeoutMs = 5000; condition = @{ type = 'screen'; classContains = 'SelectWorldScreen' } },
    @{ type = 'mouse.scroll'; xOffset = 0; yOffset = -1 },
    @{ type = 'mouse.drag'; fromX = 20; fromY = 70; toX = 20; toY = 100; durationMs = 120; segments = 4 },
    @{ type = 'mouse.click'; x = 331; y = 222 },
    @{ type = 'wait.until'; timeoutMs = 5000; condition = @{ type = 'screen'; classContains = 'TitleScreen' } }
)
Assert-True ($roundTrip.status -eq 'completed') 'Vanilla GUI round-trip pipeline must complete'

$failedAssert = Invoke-ErrorResponse -Path '/v0/assert' -Body @{
    condition = @{ type = 'ui.exists'; selector = @{ label = "phase3-never-$([guid]::NewGuid())" } }
}
Assert-True ($failedAssert.Status -eq 412) 'false assertion must return 412'
Assert-True ($failedAssert.Json.error -eq 'ASSERTION_FAILED') 'false assertion must be typed'

$cancelStarted = Invoke-Json -Method POST -Path '/v0/pipelines' -Headers $leaseHeaders -Body @{
    timeoutMs = 60000
    steps = @(
        @{ type = 'key'; key = 87; scanCode = 17; action = 1 },
        @{ type = 'delay'; durationMs = 30000 }
    )
}
$heldDeadline = [DateTime]::UtcNow.AddSeconds(3)
do {
    Start-Sleep -Milliseconds 50
    $held = Invoke-Json -Method GET -Path '/v0/input/state'
} while ($held.pressedKeyCount -lt 1 -and [DateTime]::UtcNow -lt $heldDeadline)
Assert-True ($held.pressedKeys -contains 87) 'pipeline must expose held W before cancellation'
$cancelled = Invoke-Json -Method DELETE -Path "/v0/operations/$($cancelStarted.operationId)"
Assert-True ($cancelled.status -eq 'cancelled') 'pipeline cancellation must be observable'
$clearDeadline = [DateTime]::UtcNow.AddSeconds(3)
do {
    Start-Sleep -Milliseconds 50
    $cleared = Invoke-Json -Method GET -Path '/v0/input/state'
} while ($cleared.pressedKeyCount -gt 0 -and [DateTime]::UtcNow -lt $clearDeadline)
Assert-True ($cleared.pressedKeyCount -eq 0 -and $cleared.pressedButtonCount -eq 0) 'cancelled pipeline must clean input'

$facts = Invoke-Json -Method GET -Path '/v0/render/facts'
if ($ModernRenderFacts) {
    Assert-True ($facts.coverage -eq 'render_primitives') 'modern target must expose render primitives'
    Assert-True ($facts.factCount -gt 0) 'modern target must expose bounded render facts'
    Assert-True (-not [bool]$facts.semanticInference) 'Render Facts must not claim business semantics'
    Assert-True ($null -ne $facts.facts[0].width) 'render fact must include structured bounds'
}
else {
    Assert-True ($facts.coverage -eq 'unsupported') 'Forge 1.20.1 must honestly report unsupported Render Facts'
}

$worldResult = 'NOT_REQUESTED'
if ($RequireWorldLoop) {
    $leaseHeaders = Acquire-Lease
    $worldPipeline = Start-Pipeline -LeaseHeaders $leaseHeaders -TimeoutMs 55000 -Steps @(
        @{ type = 'ui.action'; action = 'click'; selector = @{ role = 'button'; label = 'Singleplayer' } },
        @{ type = 'wait.until'; timeoutMs = 5000; condition = @{ type = 'screen'; classContains = 'SelectWorldScreen' } },
        @{ type = 'mouse.click'; x = 200; y = 75 },
        @{ type = 'delay'; durationMs = 250 },
        @{ type = 'ui.action'; action = 'click'; selector = @{ role = 'button'; label = 'Play Selected World' } },
        @{ type = 'wait.until'; timeoutMs = 30000; condition = @{ type = 'screen'; open = $false } },
        @{ type = 'key.tap'; key = 69; scanCode = 18; holdMs = 50 },
        @{ type = 'wait.until'; timeoutMs = 5000; condition = @{ type = 'screen'; classContains = 'InventoryScreen' } },
        @{ type = 'assert.that'; condition = @{ type = 'ui.exists'; selector = @{ role = 'slot'; slot = 0 } } },
        @{ type = 'ui.action'; action = 'click'; selector = @{ role = 'slot'; slot = 0 } },
        @{ type = 'key.tap'; key = 69; scanCode = 18; holdMs = 50 },
        @{ type = 'wait.until'; timeoutMs = 5000; condition = @{ type = 'screen'; open = $false } }
    )
    Assert-True ($worldPipeline.status -eq 'completed') 'world/container pipeline must complete'
    $worldSession = Invoke-Json -Method GET -Path '/v0/session'
    $trace = Invoke-Json -Method GET -Path '/v0/trace'
    Assert-True ([bool]$worldSession.inWorld) 'world pipeline must enter an integrated world'
    Assert-True ($trace.screenSlotClickSequence -gt 0) 'slot click must reach Screen'
    Assert-True ($trace.menuDispatchSequence -gt 0) 'slot click must reach Menu'
    Assert-True ($trace.containerPacketSequence -gt 0) 'slot click must create a normal packet'
    Assert-True ($trace.serverContainerPacketSequence -gt 0) 'slot click must reach Server validation'
    $worldResult = 'PASS'
}

[pscustomobject]@{
    Target = $ExpectedTarget
    InteractionTree = 'PASS'
    Selector = 'PASS'
    StandardModGui = 'PASS'
    VisionFallback = 'PASS'
    WaitAssert = 'PASS'
    Scroll = 'PASS'
    Drag = 'PASS'
    MultiKeyChord = 'PASS'
    PipelineCancellationCleanup = 'PASS'
    RenderFacts = $(if ($ModernRenderFacts) { 'PASS' } else { 'HONESTLY_UNAVAILABLE' })
    WorldContainerLoop = $worldResult
    Result = 'PASS'
}
