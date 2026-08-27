[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget,
    [switch]$NoPrivilegedScopes
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'ModeHelpers.ps1')
$base=$BaseUri.TrimEnd('/')
$auth=@{Authorization='Bearer '+(Get-Content -LiteralPath $TokenFile -Raw).Trim()}
function Require([bool]$ok,[string]$reason){if(-not$ok){throw "Control Round 1 live: $reason"}}
function Json([string]$method,[string]$path,[object]$body=$null,[hashtable]$headers=$auth){
    $p=@{Uri=$base+$path;Method=$method;Headers=$headers;TimeoutSec=20}
    if($null-ne$body){$p.ContentType='application/json';$p.Body=$body|ConvertTo-Json -Depth 40 -Compress}
    Invoke-RestMethod @p
}
function Denied([string]$path,[object]$body,[string]$code,[hashtable]$headers=$auth){
    $r=Invoke-WebRequest ($base+$path) -Method Post -Headers $headers -ContentType application/json -Body ($body|ConvertTo-Json -Depth 40 -Compress) -SkipHttpErrorCheck -TimeoutSec 15
    $errorBody=$r.Content|ConvertFrom-Json
    Require ($r.StatusCode-ge400-and$errorBody.error-eq$code) "$path expected $code, got $($r.StatusCode) / $($errorBody.error)"
    return $errorBody
}
function Acquire([int]$ttl=60000){
    $m=Get-AgentMode $base $auth
    Require (-not$m.reconsentRequired) 'new conversation consent is required before this automatic test acquires'
    $script:lease=Json POST '/v0/control/acquire' @{ttlMs=$ttl;expectedModeVersion=$m.modeVersion}
    $script:control=$auth.Clone();$script:control['X-MCP-Control-Lease']=$lease.leaseId
}
function Select-Intent([string]$mode){
    $current=Json GET '/v0/control/status'
    Set-AgentMode $base $auth $mode $current.leaseId|Out-Null
}
function Pipeline([array]$steps,[int]$timeout=8000){
    $started=Json POST '/v0/pipelines' @{steps=$steps;timeoutMs=$timeout} $control
    $done=Json POST "/v0/operations/$($started.operationId)/wait" @{timeoutMs=$timeout}
    Require ($done.state-eq'completed') "pipeline $($done.operationId) $($done.state): $($done.error)"
    return $done
}
function PlayerReference {
    $readyDeadline=[DateTime]::UtcNow.AddSeconds(5)
    do{
        $snapshot=Json POST '/v0/observe/deep' @{perspective='server_authoritative';domains=@('player');includeProviderData=$false}
        $reference=@($snapshot.resourceRevisionRefs|Where-Object resourceType -eq player)
        if($reference.Count-gt0-and$null-ne$snapshot.server.player.health){return $snapshot}
        Start-Sleep -Milliseconds 250
    }while([DateTime]::UtcNow-lt$readyDeadline)
    throw 'Player snapshot remains partial; never substitute zero for unavailable state'
}
function Mutation([double]$value){
    $fp=Json GET '/v0/world/fingerprint'
    $snapshot=PlayerReference
    $ref=@($snapshot.resourceRevisionRefs|Where-Object resourceType -eq player)[0]
    @{operation='player.health.set';worldFingerprint=$fp.worldFingerprint;expectedResourceVersion=$ref;health=$value;expectedHealth=$snapshot.server.player.health}
}
function Apply-Health([double]$value,[hashtable]$headers){
    for($attempt=1;$attempt-le3;$attempt++){
        try{return Json POST '/v0/debug/mutations' (Mutation $value) $headers}
        catch{
            if($attempt-eq3-or$_.ErrorDetails.Message-notmatch'STALE_RESOURCE_REVISION'){throw}
            $script:revisionRetries++
            Start-Sleep -Milliseconds 200
        }
    }
}
function Restore-Health([double]$value,[hashtable]$headers){
    for($attempt=1;$attempt-le3;$attempt++){
        $current=PlayerReference
        if([double]$current.server.player.health-eq$value){
            $script:restoreAlreadySatisfied++
            return
        }
        try{Apply-Health $value $headers|Out-Null;return}
        catch{
            # Peaceful regeneration can satisfy cleanup between observation and dispatch.
            # Only accept that after a fresh authoritative value check on the next iteration.
            if(($_.ErrorDetails.Message|ConvertFrom-Json).error-ne'DEBUG_NO_STATE_CHANGE'){throw}
        }
    }
    throw 'Could not verify restored authoritative health'
}
$script:restoreAlreadySatisfied=0
$script:revisionRetries=0
$session=Json GET '/v0/session'
Require ($session.target-eq$ExpectedTarget-and$session.inWorld) 'dedicated test world required'
Select-Intent READ
$readMode=Get-AgentMode $base $auth
Require ($readMode.mode-eq'READ'-and-not$readMode.takeoverActive) 'READ state'
$before=Json GET '/v0/input/state'
$observation=Json GET '/v0/player'
Require $observation.available 'READ player observation'
foreach($request in @(
    @{path='/v0/input/key';body=@{key=87;action=1}},
    @{path='/v0/ui/action';body=@{coordinates=@{x=2;y=2}}},
    @{path='/v0/pipelines';body=@{steps=@(@{type='delay';durationMs=1})}},
    @{path='/v0/command/player';body=@{command='help'}}
)){ $errorBody=Denied $request.path $request.body 'TAKEOVER_REQUIRED';Require ($errorBody.control.mode-eq'READ') 'structured mode rejection' }
$after=Json GET '/v0/input/state'
Require ($before.inputDispatchSequence-eq$after.inputDispatchSequence) 'READ input rejection emitted an input side effect'
Denied '/v0/control/acquire' @{ttlMs=10000;userConsent=$true} 'INVALID_CONTROL_REQUEST'|Out-Null
$initialVersion=$readMode.modeVersion
Select-Intent OPERATE
$operate=Get-AgentMode $base $auth
Require ($operate.mode-eq'OPERATE'-and-not$operate.takeoverActive) 'OPERATE state'
Require ((Json GET '/v0/control/status').status-eq'available') 'OPERATE must not own an input Lease'
Denied '/v0/input/key' @{key=87;action=1} 'TAKEOVER_REQUIRED'|Out-Null
Denied '/v0/control/mode' @{mode='READ';expectedModeVersion=$initialVersion} 'STALE_MODE_REVISION'|Out-Null
if($NoPrivilegedScopes){
    Denied '/v0/diagnostics/ui/test-screen' @{} 'SCOPE_DENIED'|Out-Null
    Denied '/v0/debug/mutations' @{operation='player.health.set'} 'DEBUG_SCOPE_DENIED'|Out-Null
    $debug='SCOPES_DENIED_CORRECTLY';$fixture='SCOPES_DENIED_CORRECTLY'
}else{
    Json POST '/v0/debug/disarm'|Out-Null
    $snapshot=PlayerReference
    $original=$snapshot.server.player.health
    Require ($null-ne$original-and[double]$original-ge2-and[double]$original-le2048) 'valid restorable health baseline required'
    Denied '/v0/debug/mutations' (Mutation ([double]$original-0.25)) 'DEBUG_NOT_ARMED'|Out-Null
    $opened=Json POST '/v0/diagnostics/ui/test-screen'
    Require ($opened.evidenceContaminated-and$opened.mechanism-eq'DIRECT') 'Fixture provenance'
    Require ((Json GET '/v0/control/status').status-eq'available') 'Fixture acquired an input Lease'
    $fixture='PASS'
    Acquire
    Pipeline @(@{type='ui.action';selector=@{label='Close Probe'};holdMs=100},@{type='wait.until';condition=@{type='screen';open=$false};timeoutMs=5000})|Out-Null
    Select-Intent OPERATE
    $fp=Json GET '/v0/world/fingerprint'
    $arm=Json POST '/v0/debug/arm' @{worldFingerprint=$fp.worldFingerprint;namespaces=@('player');ttlMs=60000}
    $armed=$auth.Clone();$armed['X-MCP-Debug-Arm']=$arm.debugArmId
    $restoreNeeded=$false
    try{
        Select-Intent READ
        Denied '/v0/debug/mutations' (Mutation ([double]$original-0.25)) 'OPERATE_REQUIRED' $armed|Out-Null
        Select-Intent OPERATE
        $changed=Apply-Health ([double]$original-0.25) $armed
        $restoreNeeded=$true
        Require ($changed.evidence-eq'diagnostic'-and$changed.authority-eq'runtime_internal') 'Debug must not become gameplay evidence'
        Restore-Health ([double]$original) $armed
        $restoreNeeded=$false
        $debug='PASS'
    }finally{
        try{if($restoreNeeded){Apply-Health ([double]$original) $armed|Out-Null}}
        finally{Json POST '/v0/debug/disarm'|Out-Null}
    }
}
Acquire
Denied '/v0/input/key' @{key=87;action=1} 'CONTROL_LEASE_REQUIRED'|Out-Null
$input=Pipeline @(@{type='key.tap';key=87;holdMs=100})
Require ($input.result.steps[0].result.entryLayer-eq'GAME_ROUTED_RAW'-and-not$input.result.steps[0].result.directMutationUsed) 'TAKEOVER normal input provenance'
$nativeBefore=(Json GET '/v0/input/state').nativeRevocations
Pipeline @(@{type='key.tap';key=69;holdMs=40},@{type='wait.until';condition=@{type='screen';classContains='InventoryScreen'};timeoutMs=5000},@{type='key.tap';key=256;holdMs=40},@{type='wait.until';condition=@{type='screen';open=$false};timeoutMs=5000})|Out-Null
Require ((Get-AgentMode $base $auth).mode-eq'TAKEOVER'-and(Json GET '/v0/input/state').nativeRevocations-eq$nativeBefore) 'Agent Esc revoked control'
$pending=Json POST '/v0/pipelines' @{steps=@(@{type='key';key=87;action=1},@{type='delay';durationMs=5000},@{type='key';key=87;action=0});timeoutMs=8000} $control
$deadline=[DateTime]::UtcNow.AddSeconds(3)
do{$held=Json GET '/v0/input/state';if($held.pressedKeyCount-eq0){Start-Sleep -Milliseconds 25}}while($held.pressedKeyCount-eq0-and[DateTime]::UtcNow-lt$deadline)
Require ($held.pressedKeyCount-eq1) 'pending pipeline did not start'
Select-Intent OPERATE
$stopped=Json GET '/v0/input/state'
Require ($stopped.pressedKeyCount-eq0-and$stopped.pressedButtonCount-eq0) 'mode transition leaked held input'
Require ((Json GET "/v0/operations/$($pending.operationId)").state-eq'cancelled') 'mode transition failed to cancel pending pipeline'
$sequence=$stopped.inputDispatchSequence
Start-Sleep -Milliseconds 800
Require ((Json GET '/v0/input/state').inputDispatchSequence-eq$sequence) 'post-transition input'
Acquire 1000
Json POST '/v0/input/key' @{key=87;action=1} $control|Out-Null
$deadline=[DateTime]::UtcNow.AddSeconds(3)
do{$lost=Get-AgentMode $base $auth;if($lost.mode-eq'TAKEOVER'){Start-Sleep -Milliseconds 50}}while($lost.mode-eq'TAKEOVER'-and[DateTime]::UtcNow-lt$deadline)
Require ($lost.mode-eq'READ'-and-not$lost.reconsentRequired) 'Lease expiry must be normal READ handback'
$clean=Json GET '/v0/input/state'
Require ($clean.pressedKeyCount-eq0-and$clean.pressedButtonCount-eq0) 'expiry input cleanup'
[pscustomobject]@{
    Result='PASS';Target=$ExpectedTarget;Read='PASS';Operate='PASS';Takeover='PASS';Fixture=$fixture;TypedDebug=$debug
    ScopeSeparation='PASS';StaleModeRevision='PASS';AgentEsc='PASS';PipelineTransitionCancellation='PASS';PostTransitionInput=0
    RevisionPreconditionRetries=$script:revisionRetries;CleanupAlreadySatisfiedByLiveState=$script:restoreAlreadySatisfied
    LeaseExpiry='PASS';ManualEsc='SEPARATE_HUMAN_GATE';PersistentWriteInvocations=0
}
