#requires -Version 7.0
[CmdletBinding()]param()
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'ControlUiShowcase.Helpers.ps1')
. (Join-Path $PSScriptRoot 'ControlUiShowcase.Cases.ps1')
$checks=[Collections.Generic.List[string]]::new()
function Check([bool]$Condition,[string]$Name){if(-not$Condition){throw "Showcase test: $Name"};$checks.Add($Name)}
function MustReject([scriptblock]$Action,[string]$Name){$rejected=$false;try{& $Action|Out-Null}catch{$rejected=$true};Check $rejected $Name}

foreach($file in Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*Showcase*.ps1') {
    $tokens=$null;$parseErrors=$null
    [void][Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$parseErrors)
    Check ($parseErrors.Count-eq0) ("parse: "+$file.Name)
}
Check ((Get-ShowcaseCaseOrder)-join','-eq'read,operate,takeover-gameplay,pointer-move,takeover-gui,mode-transition,ui-overlay,gui-scenes') 'fixed All order excludes physical Esc'
Check ($showcaseCases.Count-eq9) 'eight automatic cases and one explicit human case'
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
        '/v0/control/mode' {$script:fakeMode.mode=$Body.mode;return $script:fakeMode}
        '/v0/input/state' {return @{pressedKeyCount=0;pressedButtonCount=0;inputSequenceActive=$false}}
        '/v0/world/fingerprint' {return @{worldFingerprint='test-world'}}
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
Start-ShowcaseTakeover
Check ($leaseId-eq'test-lease'-and$calls[-1].body.expectedModeVersion.generation-eq2) 'explicit acquire carries current version'
$script:fakeMode.mode='TAKEOVER';$script:leaseId=$null
MustReject {Start-ShowcaseTakeover} 'do not steal another controller Lease'

function Get-ShowcaseMenu {
    @{server=@{menu=@{menuId=0;carriedStack=@{empty=$true};slots=@(9..12|ForEach-Object {@{slot=$_;id='minecraft:air';empty=$true;count=0}})}};resourceRevisionRefs=@(@{resourceType='menu';resourceKey='test-player';revision=4;sessionEpoch='test-epoch'})}
}
$calls.Clear()
Set-ShowcaseSlot 9 8|Out-Null
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
$cases=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ControlUiShowcase.Cases.ps1') -Raw
$helpers=Get-Content -LiteralPath (Join-Path $PSScriptRoot 'ControlUiShowcase.Helpers.ps1') -Raw
Check ($main.Contains('[IO.FileShare]::None')-and$main.Contains('Get-ShowcaseListener $otherPort')) 'single Runner and single target instance guards'
Check ($main.Contains('if($instanceOwned)')) 'rejected unknown instance receives no report file'
Check ($main.IndexOf("Assert-Showcase (`$initial.mode-ne'TAKEOVER')")-lt$main.IndexOf('$connected=$true')) 'foreign control is not cleaned up on failed admission'
Check ($main.Contains("runtime='loader_development_source_sets'")-and$main.Contains("visualAcceptance='NOT_PERFORMED'")) 'evidence does not masquerade as packaged or visual attestation'
Check ($helpers.Contains('$worlds.Count-eq1')-and$helpers.Contains('$current.empty')) 'unknown worlds/items are not overwritten'
Check ($helpers-notmatch '/storage/.*/write|/debug/storage|userConsent\s*=|SendKeys|SendInput|glfwSetCursorPos') 'no storage write, fake consent or desktop input path'
Check ($cases.Contains("type='ui.drag'")-and$cases.Contains("Invoke-ShowcaseSlot 9 hover")) 'real semantic hover/drag API retained'
Check ($cases.Contains('nativeRevocations-gt')-and$cases.Contains('post-revoke input detected')) 'manual case requires Runtime native evidence and no late dispatch'
$smoke=(Get-Command Invoke-ShowcaseSmoke).ScriptBlock.ToString()
Check (-not$smoke.Contains('Get-ShowcaseCaseOrder')-and-not$smoke.Contains('Show-ManualEsc')) 'smoke never starts full All or human acceptance'
Check ($cases.Contains('DEBUG_PRIVILEGED')-and$cases.Contains('Fixture Tutorial/System Toast')) 'diagnostic evidence labelled explicitly'
[pscustomobject]@{Result='PASS';Checks=$checks.Count;Cases=9;Tests=$checks;RuntimeSmoke='SEPARATE';VisualAcceptance='NOT_PERFORMED'}
