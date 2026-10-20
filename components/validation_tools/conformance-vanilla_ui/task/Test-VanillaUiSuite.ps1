$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$tokens = $null
$errors = $null
$ast=[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Invoke-VanillaUiSuite.ps1'), [ref]$tokens, [ref]$errors)
if($errors.Count){throw 'UI runner syntax error'}
$definition=$ast.Find({param($node) $node-is[System.Management.Automation.Language.FunctionDefinitionAst]-and$node.Name-eq'Test-UiReceiptOwner'},$true)
if(-not$definition){throw 'Missing production receipt owner check'}
. ([scriptblock]::Create($definition.Extent.Text))
$started=[DateTime]::Parse('2026-10-16T16:41:00Z',[Globalization.CultureInfo]::InvariantCulture,[Globalization.DateTimeStyles]::RoundtripKind)
$receipt='{"runtimePort":5785,"launchAttestation":{"processId":44384},"testedAt":"2026-10-16T16:49:36.033Z"}'|ConvertFrom-Json
if(-not(Test-UiReceiptOwner $receipt 5785 44384 $started)){throw 'Current JSON UTC receipt lost its offset'}
$receipt.testedAt=[DateTimeOffset]::Parse('2026-10-17T00:49:36.033+08:00')
if(-not(Test-UiReceiptOwner $receipt 5785 44384 $started)){throw 'Current offset receipt lost its offset'}
$receipt.testedAt='2026-10-16T16:49:36.033Z'
if(-not(Test-UiReceiptOwner $receipt 5785 44384 $started)){throw 'Current string receipt rejected'}
if((Test-UiReceiptOwner $receipt 5786 44384 $started)-or(Test-UiReceiptOwner $receipt 5785 44385 $started)){throw 'Another run receipt accepted'}
$receipt.testedAt='2026-10-16T16:40:59Z'
if(Test-UiReceiptOwner $receipt 5785 44384 $started){throw 'Historical receipt accepted'}
$runner=Join-Path $PSScriptRoot 'Invoke-VanillaUiSuite.ps1'
$nativeRunner=Get-Content (Join-Path $PSScriptRoot 'run-vanilla-ui.mjs') -Raw
if($nativeRunner.IndexOf('await advancementPanActions(')-lt0-or$nativeRunner.IndexOf('await advancementPanActions(')-gt$nativeRunner.IndexOf("await check('pause: Return to Game")){throw 'Advancement canvas diagnostics must execute before Save and Quit removes the player context'}
if(-not$nativeRunner.Contains("path==='/v0/control/acquire')body=automaticTakeoverRequest(await api('GET','/v0/control/mode'),body)")){throw 'Every automatic acquisition, including Debug cleanup, must reject manual override and pin the checked native mode generation'}
foreach($name in @('ui','key','command')) {
    if($nativeRunner-notmatch("async function "+$name+'\([^\n]*?\)\{\s*await heartbeatControl\(\);')){throw "Explicit $name work must maintain its existing task and Lease"}
}
if(-not$nativeRunner.Contains("action:'heartbeat',taskId,ttlMs:60000")-or-not$nativeRunner.Contains('budgetMs:300000')-or-not$nativeRunner.Contains('if(Date.now()-taskStarted>180000)')){throw 'UI task TTL maintenance must preserve the bounded budget and idle case rotation'}
if(-not$nativeRunner.Contains("await title();await click('menu.quit','Quit Game')")-or-not$nativeRunner.Contains('finally{if(lease)')){throw 'Failed UI work must release its Lease and navigate before Quit'}
try { & $runner -Target '26.3-fabric' -RuntimePort 65536; throw 'Invalid Runtime port accepted' }
catch { if ($_.FullyQualifiedErrorId -notlike 'ParameterArgumentValidationError*') { throw } }
try { & $runner -All -KeepClient; throw 'Batch KeepClient accepted' }
catch { if ($_.Exception.Message -ne 'Batch testing requires automatic shutdown and existing immutable baselines') { throw } }
try { & $runner -Target '26.3-neoforge' -CheckPrefix 'UI special world menus:'; throw 'Specific checks accepted as complete scenario evidence' }
catch { if ($_.Exception.Message -ne 'Specific checks cannot stand in for complete scenario evidence') { throw } }
foreach($scenario in @('editors','hud','hud-vitals','spectator','pointer','special-menus','ui-debug','ui-debug-editors','ui-debug-consumers','ui-debug-toast','ui-debug-advancement','ui-completion','ui-local','ui-navigation','ui-native-pages','ui-world-pages','ui-breadth','hud-camera','hud-camera-variants','hud-toast','hud-variants','hud-late','hud-saving','hud-player-list','hud-hardcore','hud-demo')) {
    try { & $runner -All -Scenario $scenario -DebugUi; throw 'Partial scenarios accepted as full batch evidence' }
    catch { if ($_.Exception.Message -ne 'Batch evidence requires the complete scenario matrix') { throw } }
}
foreach($required in @('async function rejectUiMutation','after.resourceVersion,before.resourceVersion','clean.inputDispatchSequence,input.inputDispatchSequence','async function applyUiMutation',"mode.mode,'OPERATE'",'result.uiDebugSamples=uiDebugSamples')){
    if(-not$nativeRunner.Contains($required)){throw "UI Debug proof must bind actual state, native input counts and explicit mode: $required"}
}
$configuration=Get-Content (Join-Path $repo 'validations/conformance-vanilla_ui/validation.toml') -Raw
if($configuration-notmatch 'memory-max = "4G"'-or$configuration-match 'minecraft.protocol.probe.port'){throw 'UI launch memory/port isolation contract is inconsistent'}
[System.Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Read-VanillaUiCatalog.ps1'), [ref]$tokens, [ref]$errors) | Out-Null
if ($errors.Count) { throw ($errors | Out-String) }
& node --check (Join-Path $PSScriptRoot 'run-vanilla-ui.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Native UI runner syntax failed' }
& node --check (Join-Path $PSScriptRoot 'special-menu-actions.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Special menu actions syntax failed' }
& node --check (Join-Path $PSScriptRoot 'hud-vital-actions.mjs')
if ($LASTEXITCODE -ne 0) { throw 'HUD vital actions syntax failed' }
& node --check (Join-Path $PSScriptRoot 'run-web-input.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Web input runner syntax failed' }
& node --check (Join-Path $PSScriptRoot 'run-ui-category-consumers.mjs')
if ($LASTEXITCODE -ne 0) { throw 'MCP category runner syntax failed' }
& node --check (Join-Path $PSScriptRoot 'toast-consumers.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Toast consumers syntax failed' }
& node --check (Join-Path $PSScriptRoot 'editor-consumers.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Editor consumers syntax failed' }
& node --check (Join-Path $PSScriptRoot 'spectator-consumers.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Spectator consumers syntax failed' }
& node --test (Join-Path $PSScriptRoot 'runner-handoff.test.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Production editor handoff recovery failed' }
& node --test (Join-Path $PSScriptRoot 'ui-debug-capability-audit.test.mjs')
if ($LASTEXITCODE -ne 0) { throw 'UI typed applicability audit failed' }
& node --test (Join-Path $PSScriptRoot 'hud-completion-actions.test.mjs') (Join-Path $PSScriptRoot 'suggestion-debug-actions.test.mjs') (Join-Path $PSScriptRoot 'visibility-toast-actions.test.mjs')
if ($LASTEXITCODE -ne 0) { throw 'UI completion proof contracts failed' }
& node --test (Join-Path $PSScriptRoot 'ui-local-owner-actions.test.mjs') (Join-Path $PSScriptRoot 'hud-camera-actions.test.mjs') (Join-Path $PSScriptRoot 'hud-camera-variant-actions.test.mjs') (Join-Path $PSScriptRoot 'hud-toast-user-actions.test.mjs') (Join-Path $PSScriptRoot 'ui-navigation-completion-actions.test.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Native local-owner, HUD and navigation proof contracts failed' }
& node --test (Join-Path $PSScriptRoot 'ui-native-confirmation-actions.test.mjs') (Join-Path $PSScriptRoot 'ui-recovery-actions.test.mjs') (Join-Path $PSScriptRoot 'player-list-actions.test.mjs') (Join-Path $PSScriptRoot 'ui-owned-connection-actions.test.mjs') (Join-Path $PSScriptRoot 'ui-world-recovery-actions.test.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Native confirmation, cancellation and private experimental draft contracts failed' }
& node --test (Join-Path $PSScriptRoot 'hud-vital-variant-actions.test.mjs') (Join-Path $PSScriptRoot 'hud-boss-score-actions.test.mjs') (Join-Path $PSScriptRoot 'hud-late-text-actions.test.mjs') (Join-Path $PSScriptRoot 'hud-native-message-actions.test.mjs') (Join-Path $PSScriptRoot 'ui-special-world-menu-actions.test.mjs') (Join-Path $PSScriptRoot 'ui-recipe-hotbar-actions.test.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Finite native UI breadth and actual HUD draw contracts failed' }
& node --test (Join-Path $PSScriptRoot 'ui-hardcore-actions.test.mjs') (Join-Path $PSScriptRoot 'hud-demo-actions.test.mjs') (Join-Path $PSScriptRoot 'hud-saving-actions.test.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Actual native hardcore and Demo launch contracts failed' }
& node --test (Join-Path $PSScriptRoot 'ui-reconfiguration-actions.test.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Native bounded reconfiguration disconnect contracts failed' }
& node --test (Join-Path $PSScriptRoot 'ui-toast-lifecycle-actions.test.mjs') (Join-Path $PSScriptRoot 'ui-command-management-actions.test.mjs')
if ($LASTEXITCODE -ne 0) { throw 'Native Toast lifecycle and management-page boundary contracts failed' }
& (Join-Path $PSScriptRoot 'Test-VanillaUiCatalog.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Concrete native Screen catalogue validation failed' }
& node --check (Join-Path $PSScriptRoot 'visibility-toast-actions.mjs')
if ($LASTEXITCODE -ne 0) { throw 'UI visibility Toast syntax failed' }
& node --check (Join-Path $PSScriptRoot 'ui-debug-advancement-actions.mjs')
if ($LASTEXITCODE -ne 0) { throw 'UI advancement canvas syntax failed' }
$catalog = Get-Content (Join-Path $repo 'validations/conformance-vanilla_ui/task/screens.json') -Raw | ConvertFrom-Json
$names = @($catalog.families | ForEach-Object { $_.name })
if ($names.Count -lt 10 -or @($names | Sort-Object -Unique).Count -ne $names.Count) { throw 'UI coverage families must be complete and unique' }
foreach ($family in $catalog.families) {
    if (-not $family.screens.Count -or -not $family.actions.Count) { throw "Missing coverage contract: $($family.name)" }
    if (@($family.screens | Sort-Object -Unique).Count -ne $family.screens.Count) { throw "Duplicate Screen: $($family.name)" }
}
Write-Output "PASS: isolated UI runner syntax and $($names.Count) coverage families"
