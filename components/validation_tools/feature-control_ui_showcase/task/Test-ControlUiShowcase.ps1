#requires -Version 7.0
[CmdletBinding()]param()
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'ControlUiShowcase.Helpers.ps1')
. (Join-Path $PSScriptRoot 'ControlUiShowcase.Cases.ps1')
$checks=[Collections.Generic.List[string]]::new()
function Check([bool]$Condition,[string]$Name){if(-not$Condition){throw "Showcase test: $Name"};$checks.Add($Name)}
function MustReject([scriptblock]$Action,[string]$Name){$rejected=$false;try{& $Action|Out-Null}catch{$rejected=$true};Check $rejected $Name}

foreach($file in Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1') {
    $tokens=$null;$parseErrors=$null
    [void][Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$parseErrors)
    Check ($parseErrors.Count-eq0) ("parse: "+$file.Name)
}
Check ((Get-ShowcaseCaseOrder)-join','-eq'read,operate,takeover-gameplay,pointer-move,takeover-gui,mode-transition,ui-overlay,gui-scenes,chrome-spring,cursor-rule,pause-pointer,inventory-pointer,world-observe,world-interact,wait-evidence,persistent-read,cancel-cleanup') 'fixed All order excludes physical Esc'
Check ($showcaseCases.Count-eq18) 'seventeen automatic cases and one explicit human case'
$tutorialToast=@{role='toast';visible=$true;labelSource='last_native_toast_render';actions=@();label="Showcase Fixture`nTutorial Toast / not gameplay"}
$systemToast=@{role='toast';visible=$true;labelSource='last_native_toast_render';actions=@();label="Showcase Fixture`nSystem Toast / not an advancement"}
Check (Test-ShowcaseFixtureToasts @{presentation=@{children=@($tutorialToast,$systemToast)}}) 'both fixture Toasts require actually rendered readonly native text'
Check (-not(Test-ShowcaseFixtureToasts @{presentation=@{children=@($tutorialToast)}})) 'one Toast cannot stand in for the other fixture'
$systemToast.visible=$false
Check (-not(Test-ShowcaseFixtureToasts @{presentation=@{children=@($tutorialToast,$systemToast)}})) 'hidden fixture Toast is not a presentation PASS'
$systemToast.visible=$true;$systemToast.labelSource='invented'
Check (-not(Test-ShowcaseFixtureToasts @{presentation=@{children=@($tutorialToast,$systemToast)}})) 'invented Toast text is not native rendering evidence'
$listed=@(& (Join-Path $PSScriptRoot 'Invoke-ControlUiPreview.ps1') -ListCases)
Check ($listed.Count-eq18-and$listed[0].Case-eq'read') 'Preview lists real Runner cases without launching a game'
Assert-ShowcaseTitle @{inWorld=$false;screenClass='net.minecraft.client.gui.screens.TitleScreen'}
Check $true 'Title Screen is admitted for repeatable viewing'
MustReject {Assert-ShowcaseTitle @{inWorld=$true;screenClass='net.minecraft.client.gui.screens.TitleScreen'}} 'running world cannot masquerade as title'
MustReject {Assert-ShowcaseTitle @{inWorld=$false;screenClass='net.minecraft.client.gui.screens.OptionsScreen'}} 'non-title menu is not auto-navigated'
MustReject {Assert-ShowcaseTitle @{inWorld=$false;screenClass='UnknownTitleScreen'}} 'unknown Screen with similar name is rejected'
Check (-not(Test-ShowcaseClientCommand 'java -Dfabric.dli.config=launch.cfg -Dfabric.dli.env=server -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotServer net.fabricmc.devlaunchinjector.Main nogui')) 'Fabric server does not block a single Showcase client'
Check (Test-ShowcaseClientCommand 'java -Dfabric.dli.config=launch.cfg -Dfabric.dli.env=client net.fabricmc.devlaunchinjector.Main') 'Fabric development client is detected'
Check (Test-ShowcaseClientCommand 'java net.fabricmc.loader.impl.launch.knot.KnotClient') 'packaged Fabric client is detected'
Check (Test-ShowcaseClientCommand 'java --launchTarget forgeclient') 'Forge client is detected'
Check (-not(Test-ShowcaseClientCommand 'java --launchTarget forgeserver')) 'Forge server is not a client'
Check (Test-ShowcaseClientCommand 'java @build/moddev/clientRunVmArgs.txt') 'ModDev client is detected'
$nodes=@(Get-ShowcaseNodes @{children=@(@{label='root';children=@(@{label='nested'})},@{label='leaf'})})
Check ($nodes.Count-eq3-and$nodes[1].label-eq'nested') 'recursive Interaction Tree traversal'

$script:auth=@{Authorization='test-only'};$script:leaseId=$null
$script:calls=[Collections.Generic.List[object]]::new()
$script:fakeMode=@{mode='READ';modeVersion=@{generation=1};reconsentRequired=$true}
function Get-ShowcaseMode {$script:fakeMode}
function Invoke-ShowcaseApi($Method,$Path,$Body=$null,$Headers=$null){
    $script:calls.Add(@{method=$Method;path=$Path;body=$Body;headers=$Headers})
    switch($Path){
        '/v0/control/acquire' {return @{leaseId='test-lease'}}
        '/v0/control/task' {return @{taskId='test-task';status=$(if($Body.action-eq'end'){'ENDED'}else{'ACTIVE'})}}
        '/v0/control/mode' {$script:fakeMode.mode=$Body.mode;return $script:fakeMode}
        '/v0/input/state' {return @{pressedKeyCount=0;pressedButtonCount=0;inputSequenceActive=$false}}
        '/v0/world/fingerprint?category=DEBUG' {return @{worldFingerprint='test-world'}}
        '/v0/observe/deep' {
            Assert-Showcase ($Body.category-eq'DEBUG') 'Internal inventory reads require explicit independently authorized DEBUG in every mode'
            return @{server=@{menu=@{menuId=0;carriedStack=@{empty=$true};slots=@(9..12|ForEach-Object {@{slot=$_;id='minecraft:air';empty=$true;count=0}})}};resourceRevisionRefs=@(@{resourceType='menu';resourceKey='test-player';revision=4;sessionEpoch='test-epoch'})}
        }
        '/v0/debug/arm' {return @{debugArmId='test-arm'}}
        '/v0/debug/mutations' {
            if($script:injectedErrors-gt0){
                $script:injectedErrors--
                $failure=[InvalidOperationException]::new('synthetic protocol rejection')
                $failure.Data['RuntimeError']=$script:injectedCode
                throw $failure
            }
            return @{evidence='diagnostic'}
        }
    }
}
MustReject {Start-ShowcaseTakeover} 'manual latch prevents auto reacquire'
Check ($calls.Count-eq0) 'rejected reacquire issues zero API writes'
Set-ShowcaseMode OPERATE|Out-Null
Check ($fakeMode.mode-eq'OPERATE'-and$fakeMode.reconsentRequired-and-not$leaseId) 'OPERATE does not clear human latch or acquire Lease'
$script:fakeMode=@{mode='READ';modeVersion=@{generation=2};reconsentRequired=$false}
Start-ShowcaseTask
Start-ShowcaseTakeover
Check ($leaseId-eq'test-lease'-and$calls[-1].body.expectedModeVersion.generation-eq2) 'explicit acquire carries current version'
Check ($calls[-1].body.taskId-eq'test-task') 'Showcase acquire attaches only the Runner task'
Stop-ShowcaseTask
Check (-not$script:taskId-and-not$script:leaseId-and$calls[-1].body.action-eq'end') 'explicit task completion clears local identity and awaits Runtime cleanup'
$script:fakeMode.mode='TAKEOVER';$script:leaseId=$null
MustReject {Start-ShowcaseTakeover} 'do not steal another controller Lease'
$script:leaseId='test-lease';$script:lastRenew=[DateTime]::UtcNow
$selector=@{label='Singleplayer'}
Invoke-ShowcaseUi $selector hover|Out-Null
Check ($calls[-1].body.selector.translationKey-eq'menu.singleplayer'-and-not$calls[-1].body.selector.ContainsKey('label')) 'actual UI request uses language-independent vanilla translation key'
Check ($selector.label-eq'Singleplayer'-and-not$selector.ContainsKey('translationKey')) 'semantic conversion never mutates the caller selector'
Check ((Get-ShowcaseSelector @{label='Showcase Toasts (Fixture)'}).label-eq'Showcase Toasts (Fixture)') 'fixture labels are not guessed as vanilla keys'
$script:leaseId=$null
Check ((Get-ShowcaseMutedOptions '').Contains("lang:en_us`n")-and(Get-ShowcaseMutedOptions '').Contains('soundCategory_master:0.0')) 'new owned Showcase instance starts muted'
$muted=Get-ShowcaseMutedOptions "lang:zh_cn`r`nsoundCategory_master:1.0`r`notherSetting:preserved`r`n"
Check ($muted.Contains("lang:zh_cn`n")-and$muted.Contains("soundCategory_master:0.0`n")-and$muted.Contains('otherSetting:preserved')) 'muting preserves existing language and unknown settings'

$script:fakeMode.mode='OPERATE'
$script:reservedSlots=@(Select-ShowcaseEmptySlots (Get-ShowcaseMenu).server.menu)
Check ($calls[-1].path-eq'/v0/observe/deep'-and$calls[-1].body.category-eq'DEBUG') 'production Showcase helper explicitly classifies internal menu reads'
Check (($reservedSlots-join',')-eq'9,10,11,12') 'empty demo inventory uses the first four normal slots'
$mixedMenu=@{slots=@(@{slot=0;empty=$true},@{slot=9;empty=$false},@{slot=45;empty=$true})+@(10..13|ForEach-Object {@{slot=$_;empty=$true}})}
Check (((Select-ShowcaseEmptySlots $mixedMenu)-join',')-eq'10,11,12,13') 'existing items and equipment are never claimed or overwritten'
MustReject {Select-ShowcaseEmptySlots @{slots=@(@{slot=9;empty=$true})}} 'insufficient empty slots reject fixture preparation'
$calls.Clear()
Set-ShowcaseSlot 9 8|Out-Null
Check (@($calls|Where-Object path -eq '/v0/world/fingerprint?category=DEBUG').Count-eq1) 'production Showcase Arm fingerprint uses DEBUG, not default USER'
$mutation=@($calls|Where-Object path -eq '/v0/debug/mutations')[0]
Check ($mutation.body.expectedItemId-eq'minecraft:air'-and$mutation.body.expectedCount-eq0) 'typed item value precondition uses authoritative id/count'
Check ($mutation.body.expectedResourceVersion.sessionEpoch-eq'test-epoch') 'typed fixture keeps epoch-bound resource version'
Check ($mutation.headers['X-MCP-Debug-Arm']-eq'test-arm') 'dedicated Arm header retained'
Check ($calls[-1].path-eq'/v0/debug/disarm') 'Arm always disarmed after mutation'
$script:injectedErrors=2;$script:injectedCode='STALE_RESOURCE_REVISION';$calls.Clear()
Set-ShowcaseSlot 9 8|Out-Null
Check (@($calls|Where-Object path -eq '/v0/debug/mutations').Count-eq3) 'only stale revisions receive bounded fresh-observation retries'
Check (@($calls|Where-Object path -eq '/v0/debug/disarm').Count-eq3) 'every retry disarms its own authorization'
$script:injectedErrors=1;$script:injectedCode='VALUE_PRECONDITION_FAILED';$calls.Clear()
MustReject {Set-ShowcaseSlot 9 8} 'value mismatch is never retried or overwritten'
Check (@($calls|Where-Object path -eq '/v0/debug/mutations').Count-eq1) 'non-stale failure stops immediately'
$script:fakeMode=@{mode='OPERATE';modeVersion=@{generation=3};reconsentRequired=$false};$script:leaseId=$null;$script:itemsOwned=$true;$calls.Clear()
Restore-ShowcaseItems
Check (-not$itemsOwned-and@($calls|Where-Object path -eq '/v0/control/acquire').Count-eq0) 'OPERATE-only cleanup does not flash TAKEOVER or acquire input Lease'

$main=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'Invoke-ControlUiShowcase.ps1') -Raw
$preview=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'Invoke-ControlUiPreview.ps1') -Raw
$cases=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ControlUiShowcase.Cases.ps1') -Raw
$helpers=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ControlUiShowcase.Helpers.ps1') -Raw
$launcher=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'Start-ControlUiShowcaseClient.ps1') -Raw
Check ($main.Contains("if(`$Offline){`$launchArguments+='-Offline'}")-and$preview.Contains('if($Offline){$arguments.Offline=$true}')-and$launcher.Contains('Get-ShowcaseGradleArguments')-and$launcher.Contains('& ./gradlew.bat @arguments')) 'explicit offline option reaches Gradle without changing the default online behavior'
$offlineLaunch=Get-ShowcaseGradleArguments '26.3-neoforge' 'C:/fixture with spaces/client' 'showcase-runs.init.gradle' $true
Check ($offlineLaunch.Count-eq8-and$offlineLaunch[-1]-eq'--offline'-and$offlineLaunch[1]-eq'-Dshowcase.directory=C:/fixture with spaces/client') 'production launch arguments preserve offline as a complete token and a spaced directory as one argument'
Check ((Get-ShowcaseGradleArguments '1.20.1-forge' 'fixture/client' 'showcase-runs.init.gradle' $false).Count-eq7) 'ordinary launch does not add offline or empty argument tokens'
Check ((Get-ShowcaseGradleArguments '26.3-fabric' 'fixture/client-multiplayer' 'showcase-runs.init.gradle' $false 'client-multiplayer')-contains':version:26.3-fabric:runValidationFeatureControlUiShowcaseClientMultiplayer') 'automatic smoke has its own native role and no manual client reuse'
Check ($main.Contains('[IO.FileShare]::None')-and$main.Contains('Assert-RuntimeInstanceStopped')-and$main.Contains('Get-RuntimeConnection')) 'single Runner and instance-scoped discovery guards'
Check ($main.Contains('if($instanceOwned)')) 'rejected unknown instance receives no report file'
Check ($main.Contains('if($FreshFixture)')-and$main.Contains('retiredInstance')-and$main.Contains('Unsafe fresh fixture archive path')-and$main.Contains('Move-Item -LiteralPath $instance -Destination $retired')) 'fresh fixtures explicitly archive stopped owned data without deleting or clearing inventory'
Check ($main.Contains('Join-Path $fixtureRoot $role')-and$main.Contains("'retired/'+`$role+'-'")) 'fresh fixture archive follows the selected manual or automatic role'
Check ($main.Contains('VM.flags')-and$main.Contains('MaxHeapSize=4294967296')) 'actual Showcase client heap is attested before running cases'
Check ($main.Contains("`$instanceProfile=if(`$Smoke-or`$CoreSmoke-or`$Automatic){'automatic'}")-and$main.Contains('[switch]$Automatic')) 'All can use the standard muted automatic role without changing manual defaults'
Check ($main.Contains('Get-ShowcaseSourceHashes')-and$main.Contains('sourceChangedDuringRun')-and$main.Contains('Sources changed during Showcase execution')) 'Showcase report binds native product, inputs and tools before and after execution'
Check ($main.IndexOf("Assert-Showcase (`$initial.mode-ne'TAKEOVER')")-lt$main.IndexOf('$connected=$true')) 'foreign control is not cleaned up on failed admission'
Check ($main.IndexOf('Assert-ShowcaseTitle (Invoke-ShowcaseApi')-lt$main.IndexOf('$connected=$true')) 'title-only rejection occurs before input or cleanup ownership'
Check ($main.Contains('if($PrepareClient){$KeepClient=$true}')-and$main.Contains('$selected=if($PrepareClient){@()}')) 'PrepareClient leaves one client open and runs zero cases'
Check ($preview.Contains('FromTitle=$true;KeepClient=$true')-and$preview.Contains("'Invoke-ControlUiShowcase.ps1'")) 'Preview delegates to the existing Runner with title-only admission and client reuse'
Check ($main.Contains("runtime='loader_development_source_sets'")-and$main.Contains("visualAcceptance='NOT_PERFORMED'")) 'evidence does not masquerade as packaged or visual attestation'
Check ($helpers.Contains('$worlds.Count-eq1')-and$helpers.Contains('$current.empty')) 'unknown worlds/items are not overwritten'
Check ($helpers-notmatch '/storage/.*/write|/debug/storage|userConsent\s*=|SendKeys|SendInput|glfwSetCursorPos') 'no storage write, fake consent or desktop input path'
Check ($cases.Contains("type='ui.drag'")-and$cases.Contains("Invoke-ShowcaseSlot 9 hover")) 'real semantic hover/drag API retained'
Check ((Get-Command Show-PointerInteraction).ScriptBlock.ToString().Contains('$stoneCount-eq8')-and(Get-Command Show-PointerInteraction).ScriptBlock.ToString().Contains("kind='semantic_slot_drag'")) 'drag execution checks actual USER slot/carried conservation before cleanup rather than only accepting the operation'
Check ($cases.Contains('nativeRevocations-gt')-and$cases.Contains('post-revoke input detected')) 'manual case requires Runtime native evidence and no late dispatch'
$smoke=(Get-Command Invoke-ShowcaseSmoke).ScriptBlock.ToString()
Check (-not$smoke.Contains('Get-ShowcaseCaseOrder')-and-not$smoke.Contains('Show-ManualEsc')) 'smoke never starts full All or human acceptance'
Check ($cases.Contains('DEBUG_PRIVILEGED')-and$cases.Contains('Fixture Tutorial/System Toast')) 'diagnostic evidence labelled explicitly'
Check (-not$cases.Contains('Get-ShowcaseMenu')) 'TAKEOVER showcase assertions use USER tree and carried stack, not internal menu data'
Check ((Get-Command Show-ChromeSpring).ScriptBlock.ToString().Contains("Assert-ShowcaseTitle")-and-not(Get-Command Show-ChromeSpring).ScriptBlock.ToString().Contains('Enter-ShowcaseWorld')) 'spring preview is title-only and never opens a save'
function Get-ShowcasePointer { $script:fakePointer }
$script:fakePointer=@{x=91.5;y=77.25;gameCursorRevision=4}
Assert-ShowcasePointerRetained @{x=91.5;y=77.25;gameCursorRevision=4} 'synthetic stable pointer'
Check $true 'cursor rule compares fractional positions and game generation'
MustReject {Assert-ShowcasePointerRetained @{x=91;y=77.25;gameCursorRevision=4} 'wrong position'} 'cursor rule rejects shifted position'
MustReject {Assert-ShowcasePointerRetained @{x=91.5;y=77.25;gameCursorRevision=3} 'wrong game generation'} 'cursor rule detects effective game request despite unchanged coordinates'
Check ((Get-Command Show-CursorRule).ScriptBlock.ToString().Contains('Assert-ShowcasePointerCentered')-and(Get-Command Show-CursorRule).ScriptBlock.ToString().Contains('Assert-ShowcasePointerRetained')) 'cursor preview asserts both ordinary page retention and effective release centering'
Check ($main.Contains('[IO.File]::WriteAllText')-and$main.Contains('[Text.UTF8Encoding]::new($false)')) 'generated execution report uses explicit UTF8 and normalized LF'
Check ((Get-Command Show-PausePointer).ScriptBlock.ToString().Contains('Assert-ShowcasePointerCentered')-and(Get-Command Show-PausePointer).ScriptBlock.ToString().Contains("@{label='Options...'} hover")-and-not(Get-Command Show-PausePointer).ScriptBlock.ToString().Contains('mouseMove')) 'pause example observes real centered input and semantic hover without coordinate compensation'
Check ((Get-Command Show-InventoryPointer).ScriptBlock.ToString().Contains('Assert-ShowcasePointerCentered')-and(Get-Command Show-InventoryPointer).ScriptBlock.ToString().Contains('Invoke-ShowcaseSlot $script:reservedSlots[0] hover')-and(Get-Command Show-InventoryPointer).ScriptBlock.ToString().Contains('Restore-ShowcaseItems')) 'inventory example observes a real item hotspot and cleans only its reserved fixture'
& (Join-Path $PSScriptRoot 'Test-CoreFeatureShowcase.ps1')
if(-not$?){throw 'Core feature Showcase tests failed'}
[pscustomobject]@{Result='PASS';Checks=$checks.Count;Cases=18;Tests=$checks;RuntimeSmoke='SEPARATE';VisualAcceptance='NOT_PERFORMED'}
