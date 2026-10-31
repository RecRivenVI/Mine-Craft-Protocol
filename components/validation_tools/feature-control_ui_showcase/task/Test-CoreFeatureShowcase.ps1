#requires -Version 7.0
[CmdletBinding()]param()
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'ControlUiShowcase.Helpers.ps1')
. (Join-Path $PSScriptRoot 'CoreFeatureShowcase.Cases.ps1')
$checks=[Collections.Generic.List[string]]::new()
function Check([bool]$Condition,[string]$Name){if(-not$Condition){throw "Core Showcase test: $Name"};$checks.Add($Name)}
function MustReject([scriptblock]$Action,[string]$Name){$failed=$false;try{& $Action|Out-Null}catch{$failed=$true};Check $failed $Name}
$exitBefore=@{inputGeneration=2;inputContextGeneration=3;inputContextAvailable=$true;inputContextId='world-a'}
$exitAfter=@{inputGeneration=3;inputContextGeneration=4;inputContextAvailable=$false;inputContextId='title-b';pressedKeyCount=0;pressedButtonCount=0;inputCleanupPending=$false;inputCleanupFailed=$false}
$exitTree=@{screenClass='net.minecraft.client.gui.screens.TitleScreen';overlayIdentity=0}
$exitFailure=@{status=409;body=@{error='REQUEST_CANCELLED';executionOutcome='not_confirmed';retrySafe=$false}}
$exitProof=Confirm-ShowcaseWorldExit $exitBefore $exitAfter $exitTree $exitFailure
Check ($exitProof.requestOutcome-eq'not_confirmed'-and$exitProof.requestReplayed-eq$false) 'normal world exit never promotes an unconfirmed click into confirmed execution'
$exitAfter.pressedKeyCount=1;MustReject {Confirm-ShowcaseWorldExit $exitBefore $exitAfter $exitTree $exitFailure} 'unconfirmed exit rejects held input';$exitAfter.pressedKeyCount=0
$exitAfter.inputContextGeneration=3;MustReject {Confirm-ShowcaseWorldExit $exitBefore $exitAfter $exitTree $exitFailure} 'unconfirmed exit rejects unchanged native context';$exitAfter.inputContextGeneration=4
$exitFailure.body.error='USER_MANUALLY_ENDED_CONTROL';MustReject {Confirm-ShowcaseWorldExit $exitBefore $exitAfter $exitTree $exitFailure} 'manual revoke is never swallowed as ordinary exit'
function Reset-TestState {
    $script:calls=[Collections.Generic.List[object]]::new()
    $script:caseEvidence=[Collections.Generic.List[object]]::new()
    $script:fakeMode=@{mode='READ';modeVersion=@{generation=1};reconsentRequired=$false}
    $script:auth=@{Authorization='fixture-only'};$script:leaseId=$null;$script:taskId=$null
    $script:worldFixture=$null;$script:live=$true;$script:world='world-a';$script:block='minecraft:air'
    $script:storedSource='PERSISTED';$script:storedAvailable=$true;$script:mutationError=$false
    $script:authority='CLIENT_KNOWN';$script:serverCategory='DEBUG'
    $script:held=0;$script:dispatch=10;$script:injectLate=$false
    $script:draft='';$script:screen='none';$script:changeDraftDuringHold=$false
    $script:terrain='Default';$script:missingWorldType=$false;$script:terrainStuck=$false
}
function Enter-ShowcaseWorld {$script:live=$true}
function Exit-ShowcaseWorld {$script:live=$false}
function Wait-ShowcaseHold {if($script:injectLate){$script:dispatch++};if($script:changeDraftDuringHold){$script:draft='external replacement'}}
function Get-ShowcaseTree {
    if($script:screen-eq'CreateWorldScreen'){
        $children=@(@{role='tab';label='World';nodeId='world-tab'},@{role='tab';label='Game';nodeId='game-tab'})
        if(-not$script:missingWorldType){$children+=@{role='button';label="World Type: $script:terrain";nodeId='terrain'}}
        return @{screenClass=$script:screen;children=$children}
    }
    @{screenClass=$script:screen;children=@(@{role='text_field';nodeId='chat-field';value=$script:draft})}
}
function Send-ShowcaseKey([int]$Key){$script:calls.Add(@{path='native-key';key=$Key});$script:screen=if($Key-eq84){'ChatScreen'}else{'none'}}
function Wait-ShowcaseScreen([string]$Class){Assert-Showcase ($script:screen-eq$Class) 'synthetic native screen mismatch'}
function Invoke-ShowcaseApi($Method,$Path,$Body=$null,$Headers=$null) {
    $script:calls.Add(@{method=$Method;path=$Path;body=$Body;headers=$Headers})
    switch($Path){
        '/v0/control/mode' {if($Method-eq'POST'){$script:fakeMode.mode=$Body.mode};return $script:fakeMode}
        '/v0/input/state' {return @{pressedKeyCount=$script:held;pressedButtonCount=0;inputSequenceActive=($script:held-gt0);inputDispatchSequence=$script:dispatch}}
        '/v0/control/acquire' {$script:fakeMode.mode='TAKEOVER';return @{leaseId='case-lease'}}
        '/v0/pipelines' {$script:held=1;return @{operationId='case-operation'}}
        '/v0/ui/action' {if($Body.selector.nodeId-eq'terrain'-and-not$script:terrainStuck){$script:terrain='Superflat'}else{$script:draft+=$Body.text};return @{inputRoute='GAME_ROUTED'}}
        '/v0/operations/case-operation' {if($Method-eq'DELETE'){$script:held=0};return @{state=$(if($script:held){'running'}else{'cancelled'})}}
        '/v0/world/fingerprint?category=DEBUG' {return @{worldFingerprint=$script:world}}
        '/v0/session' {return @{inWorld=$script:live;screenClass='none'}}
        '/v0/player' {return @{available=$true;x=1.5;y=80;z=-1.5}}
        '/v0/world/scene?radius=2&blockLimit=64&entityLimit=8' {return @{available=$true;dataCategory='USER';authority=$script:authority;chunkLoadRequested=$false;blocks=@(1);entities=@()}}
        '/v0/server/player?category=DEBUG' {return @{available=$true;dataCategory=$script:serverCategory}}
        '/v0/observe/deep' {return @{server=@{dimension='minecraft:overworld';blocks=@(@{key='1,80,2';blockId=$script:block})};resourceRevisionRefs=@(@{resourceType='block';resourceKey='minecraft:overworld@1,80,2';revision=7;sessionEpoch='epoch-a'})}}
        '/v0/debug/arm' {return @{debugArmId='world-arm'}}
        '/v0/debug/disarm' {return @{disarmed=$true}}
        '/v0/debug/mutations' {if($script:mutationError){throw 'STALE_RESOURCE_REVISION'};return @{afterResourceVersion=@{revision=8};evidence='diagnostic'}}
        '/v0/diagnostics/phase9a/storage/read' {Check (-not$script:live) 'production Persistent Read case waits for Save & Quit';return @{available=$script:storedAvailable;dataSource=$script:storedSource;consistency='last_saved_state';writeImplemented=$false;stalePossibility=$true}}
    }
    throw "unexpected synthetic request: $Method $Path"
}
Reset-TestState
Show-WorldObservation
Check ($caseEvidence[0].userAuthority-eq'CLIENT_KNOWN'-and$caseEvidence[0].debugCategory-eq'DEBUG') 'production observation case keeps USER and DEBUG separate'
Check (@($calls|Where-Object {$_.method-ne'GET'-and$_.path-match'acquire|/input/|mutations'}).Count-eq0) 'world observation neither acquires input nor mutates'
$script:authority='SERVER_AUTHORITATIVE'
MustReject {Show-WorldObservation} 'world observation refuses mislabeled server authority'
$script:authority='CLIENT_KNOWN';$script:serverCategory='USER'
MustReject {Show-WorldObservation} 'internal read cannot masquerade as USER'

Reset-TestState
Show-PersistentRead
$reads=@($calls|Where-Object path -eq '/v0/diagnostics/phase9a/storage/read')
Check (($reads.body.domain-join',')-eq'world,player,chunk') 'production Persisted case reads all three saved domains'
Check ($reads[2].body.chunkX-eq0-and$reads[2].body.chunkZ-eq-1) 'negative world positions use floor division for saved chunk'
Check ($caseEvidence.Count-eq3-and@($caseEvidence|Where-Object dataSource -ne 'PERSISTED').Count-eq0) 'all saved results retain last-saved authority'
$script:storedSource='LIVE'
MustReject {Show-PersistentRead} 'Persisted case refuses LIVE fallback'
$script:storedSource='PERSISTED';$script:storedAvailable=$false
MustReject {Show-PersistentRead} 'busy or unavailable Persisted data fails honestly'

Reset-TestState
$pos=@{x=1;y=80;z=2};$pinned=@{revision=7;sessionEpoch='epoch-a'}
Set-ShowcaseBlock $pos 'minecraft:air' 'minecraft:crafting_table' 'world-a' $pinned|Out-Null
$mutation=@($calls|Where-Object path -eq '/v0/debug/mutations')[0]
Check ($mutation.body.expectedResourceVersion-eq$pinned-and$mutation.body.expectedBlockId-eq'minecraft:air') 'typed block fixture preserves pinned revision and value guard'
Check ($mutation.headers['X-MCP-Debug-Arm']-eq'world-arm'-and$calls[-1].path-eq'/v0/debug/disarm') 'typed fixture uses and releases an independent Arm'
Check (@($calls|Where-Object path -eq '/v0/control/acquire').Count-eq0) 'typed block fixture never obtains an input Lease'
$calls.Clear();$script:world='world-b'
MustReject {Set-ShowcaseBlock $pos 'minecraft:air' 'minecraft:crafting_table' 'world-a'} 'changed world rejected before block mutation'
Check (@($calls|Where-Object {$_.path-match'/debug/'}).Count-eq0) 'identity failure performs zero Debug writes'
$script:world='world-a';$script:block='minecraft:chest'
MustReject {Set-ShowcaseBlock $pos 'minecraft:air' 'minecraft:crafting_table' 'world-a'} 'unexpected terrain not overwritten'
$script:block='minecraft:air';$script:mutationError=$true;$calls.Clear()
MustReject {Set-ShowcaseBlock $pos 'minecraft:air' 'minecraft:crafting_table' 'world-a' $pinned} 'stale fixture revision not retried as a fresh overwrite'
Check ($calls[-1].path-eq'/v0/debug/disarm'-and@($calls|Where-Object path -eq '/v0/debug/mutations').Count-eq1) 'failed fixture disarms and submits only once'

Reset-TestState
$script:worldFixture=@{position=$pos;fingerprint='world-a'};$script:world='world-b'
MustReject {Restore-ShowcaseWorldFixture} 'cleanup rejects world replacement even when current block is air'
Check ($null-ne$worldFixture-and@($calls|Where-Object path -eq '/v0/control/acquire').Count-eq0) 'changed world preserves recovery ownership without player input'
$script:world='world-a';$script:block='minecraft:crafting_table'
MustReject {Restore-ShowcaseWorldFixture} 'unknown mutation result cannot authorize restoration'
Check (@($calls|Where-Object path -eq '/v0/debug/mutations').Count-eq0) 'ambiguous fixture cleanup writes zero blocks'
$script:fakeMode.reconsentRequired=$true;$calls.Clear()
MustReject {Restore-ShowcaseWorldFixture} 'manual override blocks automatic fixture cleanup takeover'
Check (@($calls|Where-Object path -eq '/v0/control/acquire').Count-eq0) 'manual latch is not bypassed during recovery'
Reset-TestState
$script:readCount=0
$actual=Wait-ShowcaseCondition {$script:readCount++;@{ready=$true}} {$args[0].ready} 'ready test' 1
Check ($actual.ready-and$readCount-eq1) 'condition Wait returns actual state, not a fixed delay'
$script:fakeMode.reconsentRequired=$true;$script:readCount=0
MustReject {Wait-ShowcaseCondition {$script:readCount++;$true} {$args[0]} 'manual test' 1} 'condition Wait stops on real manual latch'
Check ($readCount-eq0) 'manual latch rejects before reading or continuing the action'
$script:fakeMode.reconsentRequired=$false
MustReject {Wait-ShowcaseCondition {$false} {$args[0]} 'deadline test' 1} 'condition Wait has a finite deadline'

Reset-TestState
Show-CancelCleanup
$pipeline=@($calls|Where-Object path -eq '/v0/pipelines')[0]
Check ($pipeline.headers['X-MCP-Control-Lease']-eq'case-lease'-and$pipeline.body.steps[0].type-eq'key') 'production cancel case uses the acquired Lease and a real held-input step'
Check ($caseEvidence[0].terminal-eq'cancelled'-and$caseEvidence[0].heldKeys-eq0-and$caseEvidence[0].postCleanupInputDispatch-eq0-and$fakeMode.mode-eq'READ') 'production cancel case confirms terminal state, READ and held-input cleanup'
Reset-TestState
$script:injectLate=$true
MustReject {Show-CancelCleanup} 'cancel case catches late dispatch rather than reporting a false cleanup PASS'

Reset-TestState
$script:leaseId='case-lease';$script:fakeMode.mode='TAKEOVER'
Show-ShowcaseTextDraft
Check ($caseEvidence[0].draftCancelled-and-not$caseEvidence[0].messageSubmitted-and$screen-eq'none'-and@($calls|Where-Object path -eq '/v0/ui/action')[0].body.selector.nodeId-eq'chat-field') 'production text case targets the actual field and cancels without submitting'
Reset-TestState
$script:leaseId='case-lease';$script:draft='existing user text'
MustReject {Show-ShowcaseTextDraft} 'text showcase refuses an existing Chat draft'
Check (@($calls|Where-Object path -eq '/v0/ui/action').Count-eq0-and$draft-eq'existing user text') 'existing draft receives zero replacement or text events'
Reset-TestState
$script:leaseId='case-lease';$script:changeDraftDuringHold=$true
MustReject {Show-ShowcaseTextDraft} 'text showcase does not discard an externally changed draft during cleanup'

Reset-TestState
$script:leaseId='case-lease';$script:screen='CreateWorldScreen'
Set-ShowcaseFlatWorldDraft
$actions=@($calls|Where-Object path -eq '/v0/ui/action')
Check (($actions.body.selector.nodeId-join',')-eq'world-tab,terrain,game-tab'-and$terrain-eq'Superflat') 'production fixture selects default Superflat through real semantic tabs and the native world-type cycle'
$calls.Clear();Set-ShowcaseFlatWorldDraft
Check (@($calls|Where-Object {$_.path-eq'/v0/ui/action'-and$_.body.selector.nodeId-eq'terrain'}).Count-eq0) 'an already flat draft is not cycled or customized'
$script:missingWorldType=$true
MustReject {Set-ShowcaseFlatWorldDraft} 'missing World Type refuses uncontrolled fixture creation'
$script:missingWorldType=$false;$script:terrainStuck=$true;$script:terrain='Default';$calls.Clear()
MustReject {Set-ShowcaseFlatWorldDraft} 'unresponsive World Type selection has a finite budget'
Check (@($calls|Where-Object {$_.path-eq'/v0/ui/action'-and$_.body.selector.nodeId-eq'terrain'}).Count-eq12) 'fixture cycle stops at twelve native clicks instead of retrying indefinitely'

function New-TestArchive([string]$Scenario='valid') {
    $files=[ordered]@{
        'manifest.json'='{"dataCategory":"USER","writtenFrames":1,"writtenStates":1}'
        'frame-index.json'='[]'
        'timeline/timeline.ndjson'='{"category":"input"}'
        'frames/frame.png'='synthetic frame bytes, not a pixel-acceptance image'
        'state/state.json'='{"dataCategory":"USER"}'
        'derivatives/contact-sheet.png'='synthetic derivative'
    }
    if($Scenario-eq'missing'){$files.Remove('derivatives/contact-sheet.png')}
    if($Scenario-eq'unsafe'){$files['../outside']='bad path'}
    if($Scenario-eq'debug'){$files['manifest.json']='{"dataCategory":"DEBUG","writtenFrames":1,"writtenStates":1}'}
    $hashes=[ordered]@{}
    foreach($name in $files.Keys){$hashes[$name]=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($files[$name]))).ToLowerInvariant()}
    if($Scenario-eq'corrupt'){$hashes['frame-index.json']='0'*64}
    if($Scenario-eq'extra'){$hashes['missing.json']='0'*64}
    $files['checksums.json']=$hashes|ConvertTo-Json -Compress
    $stream=[IO.MemoryStream]::new()
    $zip=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Create,$true)
    foreach($name in $files.Keys){
        $writer=[IO.StreamWriter]::new($zip.CreateEntry($name).Open(),[Text.UTF8Encoding]::new($false))
        try{$writer.Write($files[$name])}finally{$writer.Dispose()}
    }
    if($Scenario-eq'duplicate'){$zip.CreateEntry('frame-index.json')|Out-Null}
    $zip.Dispose();$stream.Position=0
    @{archive=[IO.Compression.ZipArchive]::new($stream,[IO.Compression.ZipArchiveMode]::Read,$true);stream=$stream}
}
$fixture=New-TestArchive
try{
    $report=Test-ShowcaseArtifact $fixture.archive
    Check ($report.checksumsVerified-eq6-and$report.dataCategory-eq'USER'-and$report.writtenFrames-eq1) 'production Artifact inspector verifies actual ZIP entries and content hashes'
}finally{$fixture.archive.Dispose();$fixture.stream.Dispose()}
foreach($scenario in @('corrupt','missing','unsafe','debug','extra','duplicate')){
    $fixture=New-TestArchive $scenario
    try{MustReject {Test-ShowcaseArtifact $fixture.archive} "Artifact rejects $scenario evidence"}finally{$fixture.archive.Dispose();$fixture.stream.Dispose()}
}
[pscustomobject]@{Result='PASS';CoreChecks=$checks.Count;Tests=$checks;RuntimeSmoke='SEPARATE';VisualAcceptance='NOT_PERFORMED'}
