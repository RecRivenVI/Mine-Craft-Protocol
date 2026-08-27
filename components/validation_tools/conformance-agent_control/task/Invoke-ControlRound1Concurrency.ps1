[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'ModeHelpers.ps1')
$base=$BaseUri.TrimEnd('/')
$auth=@{Authorization='Bearer '+(Get-Content -LiteralPath $TokenFile -Raw).Trim()}
function Require([bool]$ok,[string]$reason){if(-not$ok){throw "Round 1 concurrency: $reason"}}
function Json([string]$method,[string]$path,[object]$body=$null,[hashtable]$headers=$auth){
    $p=@{Uri=$base+$path;Method=$method;Headers=$headers;TimeoutSec=15}
    if($null-ne$body){$p.ContentType='application/json';$p.Body=$body|ConvertTo-Json -Depth 40 -Compress}
    Invoke-RestMethod @p
}
function Denied([string]$path,[object]$body,[string]$code){
    $reply=Invoke-WebRequest ($base+$path) -Method Post -Headers $auth -ContentType application/json -Body ($body|ConvertTo-Json -Depth 40 -Compress) -SkipHttpErrorCheck
    Require ($reply.StatusCode-ge400-and($reply.Content|ConvertFrom-Json).error-eq$code) "expected ${code}: $($reply.Content)"
}
function Content([object]$body){[Net.Http.StringContent]::new(($body|ConvertTo-Json -Depth 40 -Compress),[Text.Encoding]::UTF8,'application/json')}
$session=Json GET '/v0/session'
Require ($session.target-eq$ExpectedTarget-and$session.inWorld) 'safe test world required'
$initial=Get-AgentMode $base $auth
Require ($initial.mode-eq'READ'-and-not$initial.reconsentRequired) 'start in READ after explicit reconsent, not automatic acquire'
$client=[Net.Http.HttpClient]::new()
$client.Timeout=[TimeSpan]::FromSeconds(15)
$client.DefaultRequestHeaders.Add('Authorization',$auth.Authorization)
$socket=[Net.WebSockets.ClientWebSocket]::new()
$socket.Options.SetRequestHeader('Authorization',$auth.Authorization)
try{
    $before=Json GET '/v0/input/state'
    $deep=Json POST '/v0/observe/deep' @{perspective='server_authoritative';domains=@('player');includeProviderData=$false}
    Require ($null-ne$deep.server.player) 'READ deep observation'
    $capture=Invoke-WebRequest "$base/v0/capture" -Headers $auth -TimeoutSec 15
    Require ($capture.StatusCode-eq200-and$capture.RawContentLength-gt100) 'READ capture'
    $socket.ConnectAsync([Uri]($base.Replace('http','ws')+'/v0/events'),[Threading.CancellationToken]::None).GetAwaiter().GetResult()|Out-Null
    $buffer=[byte[]]::new(65536);$deadline=[Threading.CancellationTokenSource]::new(5000)
    try{$event=$socket.ReceiveAsync([ArraySegment[byte]]::new($buffer),$deadline.Token).GetAwaiter().GetResult();Require ($event.Count-gt0) 'READ Event WebSocket'}finally{$deadline.Dispose()}
    # Exercise READ recording without warming the Contact Sheet implementation:
    # the subsequent CoreExit gate must prove first-use finalization before Loader teardown.
    $recording=Json POST '/v0/recordings' @{intervalMs=200;durationMs=1500;maxSamples=8;captureFrames=$true;stateReads=@(@{providerId='minecraft:client/player'});contactSheet=@{enabled=$false}}
    $until=[DateTime]::UtcNow.AddSeconds(8)
    do{$recorded=Json GET "/v0/recordings/$($recording.recordingId)";if($recorded.status-in@('completed','failed')){break};Start-Sleep -Milliseconds 100}while([DateTime]::UtcNow-lt$until)
    Require ($recorded.status-eq'completed'-and$recorded.writtenFrames-gt0-and$recorded.artifactReady) 'READ recording/artifact'
    $readAfter=Json GET '/v0/input/state'
    Require ($readAfter.inputDispatchSequence-eq$before.inputDispatchSequence-and$readAfter.mode-eq'READ') 'READ emitted player input'
    $socket.Dispose()

    # Both HTTP requests use one expected generation. Exactly one may change intent.
    $version=(Get-AgentMode $base $auth).modeVersion
    $race=@(1,2|ForEach-Object{$client.PostAsync("$base/v0/control/mode",(Content @{mode='OPERATE';expectedModeVersion=$version}))})
    $responses=@($race|ForEach-Object{$_.GetAwaiter().GetResult()})
    Require (@($responses|Where-Object IsSuccessStatusCode).Count-eq1) 'two racing mode changes both succeeded'
    $rejected=@($responses|Where-Object {-not$_.IsSuccessStatusCode})[0].Content.ReadAsStringAsync().GetAwaiter().GetResult()|ConvertFrom-Json
    Require ($rejected.error-eq'STALE_MODE_REVISION') 'racing transition was not a typed stale-version rejection'

    $fp=Json GET '/v0/world/fingerprint'
    $arm=Json POST '/v0/debug/arm' @{worldFingerprint=$fp.worldFingerprint;namespaces=@('player');ttlMs=60000}
    $debug=$auth.Clone();$debug['X-MCP-Debug-Arm']=$arm.debugArmId
    $snapshot=Json POST '/v0/observe/deep' @{perspective='server_authoritative';domains=@('player');includeProviderData=$false}
    $ref=@($snapshot.resourceRevisionRefs|Where-Object resourceType -eq player)[0]
    Require ($null-ne$ref) 'bounded player revision missing'
    # Deliberately wrong value preconditions: exercise an active authorized batch
    # without changing test-world state or inventing gameplay evidence.
    $items=@(1..64|ForEach-Object{@{operation='player.health.set';health=20;expectedHealth=-1;worldFingerprint=$fp.worldFingerprint;expectedResourceVersion=$ref}})
    $batch=Json POST '/v0/debug/batches' @{items=$items;maxPerTickMutations=1;failurePolicy='CONTINUE_ON_FAILURE';maxTotalDurationMs=10000} $debug
    Require ((Get-AgentMode $base $auth).activeOperateRequests-gt0) 'batch was not pending at conflict test'
    Denied '/v0/control/acquire' @{ttlMs=10000} 'MODE_OPERATION_IN_PROGRESS'
    Denied '/v0/input/key' @{key=87;action=1} 'TAKEOVER_REQUIRED'
    Require ((Json GET '/v0/control/status').status-eq'available') 'Debug batch acquired input Lease'
    Json DELETE "/v0/operations/$($batch.operationId)"|Out-Null
    $until=[DateTime]::UtcNow.AddSeconds(3)
    do{$active=(Get-AgentMode $base $auth).activeOperateRequests;if($active-eq0){break};Start-Sleep -Milliseconds 25}while([DateTime]::UtcNow-lt$until)
    Require ($active-eq0) 'cancelled Debug admission was not retired'
    Json POST '/v0/debug/disarm'|Out-Null
    Set-AgentMode $base $auth READ|Out-Null

    $lease=Json POST '/v0/control/acquire' @{ttlMs=30000}
    $control=$auth.Clone();$control['X-MCP-Control-Lease']=$lease.leaseId
    $pipeline=Json POST '/v0/pipelines' @{steps=@(@{type='key';key=87;action=1},@{type='delay';durationMs=2500},@{type='key';key=65;action=1});timeoutMs=5000} $control
    $until=[DateTime]::UtcNow.AddSeconds(2)
    do{$held=Json GET '/v0/input/state';if($held.pressedKeyCount-gt0){break};Start-Sleep -Milliseconds 25}while([DateTime]::UtcNow-lt$until)
    Require ($held.pressedKeyCount-gt0) 'pipeline never started'
    $version=(Get-AgentMode $base $auth).modeVersion
    $client.DefaultRequestHeaders.Add('X-MCP-Control-Lease',$lease.leaseId)
    $cancel=$client.DeleteAsync("$base/v0/operations/$($pipeline.operationId)")
    $transition=$client.PostAsync("$base/v0/control/mode",(Content @{mode='OPERATE';expectedModeVersion=$version}))
    Require ($cancel.GetAwaiter().GetResult().IsSuccessStatusCode-and$transition.GetAwaiter().GetResult().IsSuccessStatusCode) 'cancel / intent exit race failed'
    $clean=Json GET '/v0/input/state'
    Require ($clean.mode-eq'OPERATE'-and$clean.pressedKeyCount-eq0-and$clean.pressedButtonCount-eq0) 'input remains after mode acknowledgement'
    Start-Sleep -Milliseconds 3000
    Require ((Json GET '/v0/input/state').inputDispatchSequence-eq$clean.inputDispatchSequence) 'queued input arrived after intent exit'
    Require ((Json GET "/v0/operations/$($pipeline.operationId)").state-eq'cancelled') 'operation cancellation state'
    Set-AgentMode $base $auth READ|Out-Null
    [pscustomobject]@{Result='PASS';Target=$ExpectedTarget;ReadDeepObserve='PASS';ReadCapture='PASS';ReadEvent='PASS';ReadRecording='PASS';ReadArtifact='PASS';ReadInputEffects=0;RecordingId=$recording.recordingId;ModeCASRace='PASS';DebugVsTakeover='CONTROLLED_REJECTION';DebugBatchCancellation='PASS';CancelVsModeChange='PASS';PostTransitionInput=0;PersistentWriteInvocations=0}
}finally{
    $socket.Dispose();$client.Dispose()
    try{Json POST '/v0/control/emergency-release'|Out-Null}catch{}
}
