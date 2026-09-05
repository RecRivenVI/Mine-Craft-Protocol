[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)] [string]$BaseUri,
    [Parameter(Mandatory = $true)] [string]$TokenFile,
    [Parameter(Mandatory = $true)] [string]$ExpectedTarget,
    [switch]$ModernRenderFacts
)

$ErrorActionPreference = 'Stop'
$base = $BaseUri.TrimEnd('/')
$token = (Get-Content -LiteralPath $TokenFile -Raw).Trim()
$auth = @{ Authorization = "Bearer $token" }

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Phase 7 alignment assertion failed: $Message" }
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
    @{ Authorization = "Bearer $token"; 'X-MCP-Control-Lease' = $lease.leaseId }
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
Assert-True ($session.screenClass -match 'TitleScreen') 'Phase 7 conformance must start at title'

$capabilities = Invoke-Json -Method GET -Path '/v0/capabilities'
Assert-True ($capabilities.capabilities.'ui.standard_mod_gui_extended' -eq 'runtime_verified_fixture') 'extended standard GUI capability must be declared'
Assert-True ($capabilities.capabilities.'diagnostics.hook_manifest' -eq 'runtime_self_test') 'Hook manifest capability must be declared'

$operations = Invoke-Json -Method GET -Path '/v0/operations'
$hookOperation = $operations.operations | Where-Object id -eq 'diagnostics.hooks'
Assert-True ($null -ne $hookOperation) 'Hook diagnostics operation must be declared'
Assert-True (-not [bool]$hookOperation.requiresControlLease) 'Hook diagnostics must remain read-only'

$initialHooks = Invoke-Json -Method GET -Path '/v0/diagnostics/hooks'
Assert-True ($initialHooks.policy -eq 'capability_fidelity_first') 'Hook policy must be Capability/Fidelity First'
Assert-True ($initialHooks.overwriteCount -eq 0) 'Hook manifest must declare zero Overwrite hooks'
Assert-True ($initialHooks.cancellableInjectionCount -eq 7 -and $initialHooks.replacementInjectionCount -eq 2) 'Hook manifest must report the reviewed operator-control Hooks honestly'
Assert-True ($initialHooks.thirdPartyTargetCount -eq 0) 'Hook manifest must declare zero third-party targets'
Assert-True ([bool]$initialHooks.runtimeSelfTest -and $initialHooks.overall -eq 'ready') 'core Hook self-test must be ready'

$leaseHeaders = Acquire-Lease
$fixture = Invoke-Json -Method POST -Path '/v0/diagnostics/ui/test-screen' -Headers $leaseHeaders
Assert-True ([bool]$fixture.evidenceContaminated -and $fixture.mechanism -eq 'DIRECT') 'compatibility fixture must be contaminated Arrange evidence'

$tree = Invoke-Json -Method GET -Path '/v0/ui/tree'
Assert-True ($tree.screenClass -match 'AutomationProbeScreen') 'compatibility Screen must be active'
$textField = @($tree.children | Where-Object label -eq 'Compatibility Text')
Assert-True ($textField.Count -eq 1 -and $textField[0].role -eq 'text_field') 'standard EditBox must be semantic text_field'
Assert-True ($textField[0].actions -contains 'click') 'standard EditBox must expose routed click'
$disabled = @($tree.children | Where-Object label -eq 'Disabled Action')
Assert-True ($disabled.Count -eq 1 -and -not [bool]$disabled[0].active) 'disabled Widget state must be preserved'
Assert-True ($disabled[0].actions.Count -eq 0) 'disabled Widget must expose no actions'
$duplicates = @($tree.children | Where-Object label -eq 'Duplicate Action')
Assert-True ($duplicates.Count -eq 2) 'duplicate semantic controls must both be represented'

$ambiguous = Invoke-Error -Method POST -Path '/v0/ui/resolve' -Body @{ role = 'button'; label = 'Duplicate Action' }
Assert-True ($ambiguous.Status -eq 409 -and $ambiguous.Json.error -eq 'UI_SELECTOR_AMBIGUOUS') 'duplicate selector must fail closed without nth'
$second = Invoke-Json -Method POST -Path '/v0/ui/resolve' -Body @{ role = 'button'; label = 'Duplicate Action'; nth = 1 }
Assert-True ($second.matchCount -eq 2 -and $second.selectedIndex -eq 1) 'nth must deterministically select the second duplicate'
Assert-True ($second.node.x -gt $duplicates[0].x) 'nth-selected duplicate must preserve distinct bounds'

$disabledAction = Invoke-Error -Method POST -Path '/v0/ui/action' -Headers $leaseHeaders -Body @{
    action = 'click'; selector = @{ role = 'button'; label = 'Disabled Action' }
}
Assert-True ($disabledAction.Status -eq 409 -and $disabledAction.Json.error -eq 'UI_NODE_NOT_ACTIONABLE') 'disabled Widget must reject interaction'

Invoke-Json -Method POST -Path '/v0/ui/action' -Headers $leaseHeaders -Body @{
    action = 'click'; selector = @{ role = 'button'; label = 'Duplicate Action'; nth = 1 }
} | Out-Null
Invoke-Json -Method POST -Path '/v0/wait/until' -Body @{
    condition = @{ type = 'ui.exists'; selector = @{ role = 'button'; label = 'Duplicate Action Second Complete' } }; timeoutMs = 5000
} | Out-Null

Invoke-Json -Method POST -Path '/v0/ui/action' -Headers $leaseHeaders -Body @{
    action = 'click'; selector = @{ role = 'button'; label = 'Add Dynamic Control' }
} | Out-Null
$dynamicWait = Invoke-Json -Method POST -Path '/v0/wait/until' -Body @{
    condition = @{ type = 'ui.exists'; selector = @{ role = 'button'; label = 'Dynamic Control' } }; timeoutMs = 5000
}
Assert-True ([bool]$dynamicWait.passed) 'runtime-added standard Widget must enter the Interaction Tree'
Invoke-Json -Method POST -Path '/v0/ui/action' -Headers $leaseHeaders -Body @{
    action = 'click'; selector = @{ role = 'button'; label = 'Dynamic Control' }
} | Out-Null
Invoke-Json -Method POST -Path '/v0/wait/until' -Body @{
    condition = @{ type = 'ui.exists'; selector = @{ role = 'button'; label = 'Dynamic Control Complete' } }; timeoutMs = 5000
} | Out-Null

$textClick = Invoke-Json -Method POST -Path '/v0/ui/action' -Headers $leaseHeaders -Body @{
    action = 'click'; selector = @{ role = 'text_field'; label = 'Compatibility Text' }
}
Assert-True ($textClick.entryLayer -eq 'GAME_ROUTED_RAW' -and [bool]$textClick.screenObserved) 'EditBox click must use routed Screen input'

$capture = Invoke-WebRequest -Uri "$base/v0/capture" -Headers $auth
$bytes = [byte[]]$capture.Content
Assert-True ($bytes.Length -gt 8 -and [BitConverter]::ToString($bytes[0..7]) -eq '89-50-4E-47-0D-0A-1A-0A') 'compatibility Screen capture must be PNG'
$facts = Invoke-Json -Method GET -Path '/v0/render/facts'
if ($ModernRenderFacts) {
    Assert-True ($facts.coverage -eq 'render_primitives' -and $facts.factCount -gt 0) 'modern Target must expose render facts for compatibility Screen'
    Assert-True (-not [bool]$facts.semanticInference) 'Render Facts must not infer business semantics'
} else {
    Assert-True ($facts.coverage -eq 'unsupported') 'legacy Target must honestly report Render Facts unavailable'
}

$hooks = Invoke-Json -Method GET -Path '/v0/diagnostics/hooks'
foreach ($hook in $hooks.hooks) {
    Assert-True (-not [bool]$hook.overwrite -and (-not [bool]$hook.cancellable -or $hook.plane -eq 'OPERATOR_CONTROL') -and -not [bool]$hook.thirdPartyTarget) "Hook $($hook.id) must preserve compatibility policy"
    Assert-True (-not [string]::IsNullOrWhiteSpace($hook.failureCapability)) "Hook $($hook.id) must declare its degraded capability"
}
Assert-True (($hooks.hooks | Where-Object id -eq 'composite_capture').runtimeStatus -eq 'runtime_verified') 'Capture callback must become runtime verified'
if ($ModernRenderFacts) {
    Assert-True (($hooks.hooks | Where-Object id -eq 'render_facts').runtimeStatus -eq 'runtime_verified') 'modern Render hook must become runtime verified'
} else {
    Assert-True (($hooks.hooks | Where-Object id -eq 'render_facts').runtimeStatus -eq 'capability_unavailable') 'legacy Render hook must remain honestly unavailable'
}

Invoke-Json -Method POST -Path '/v0/ui/action' -Headers $leaseHeaders -Body @{
    action = 'click'; selector = @{ role = 'button'; label = 'Close Probe' }
} | Out-Null
$closed = Invoke-Json -Method POST -Path '/v0/wait/until' -Body @{
    condition = @{ type = 'screen'; classContains = 'TitleScreen' }; timeoutMs = 5000
}
Assert-True ([bool]$closed.passed) 'compatibility Screen must close through routed input'

[pscustomobject]@{
    Result = 'PASS'
    Target = $ExpectedTarget
    ExtendedWidgetTree = 'PASS'
    DisabledActionGuard = 'PASS'
    SelectorAmbiguity = 'PASS'
    DynamicControl = 'PASS'
    EditBoxRoutedInput = 'PASS'
    HookManifest = 'PASS'
    RenderFacts = $(if ($ModernRenderFacts) { 'PASS' } else { 'HONESTLY_UNAVAILABLE' })
}
