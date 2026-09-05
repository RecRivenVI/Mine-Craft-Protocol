[CmdletBinding()]param(
 [Parameter(Mandatory)][string]$BaseUri,
 [Parameter(Mandatory)][string]$TokenFile,
 [Parameter(Mandatory)][string]$ExpectedTarget,
 [Parameter(Mandatory)][string]$InstanceDirectory
)
# Non-human development smoke only. It never injects host events or attests human UX.
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'ModeHelpers.ps1')
$base=$BaseUri.TrimEnd('/')
$auth=@{Authorization='Bearer '+(Get-Content -LiteralPath $TokenFile -Raw).Trim()}
function Check([bool]$ok,[string]$message){if(-not$ok){throw "Control implementation smoke: $message"}}
function Json([string]$method,[string]$path,[object]$body=$null,[hashtable]$headers=$auth){
 $p=@{Uri=$base+$path;Method=$method;Headers=$headers;TimeoutSec=25}
 if($null-ne$body){$p.ContentType='application/json';$p.Body=$body|ConvertTo-Json -Depth 40 -Compress}
 Invoke-RestMethod @p
}
function Action([object]$request){Json POST '/v0/ui/action' $request $control}
function Pipeline([array]$steps,[int]$timeout=20000){
 $start=Json POST '/v0/pipelines' @{steps=$steps;timeoutMs=$timeout} $control
 $done=Json POST "/v0/operations/$($start.operationId)/wait" @{timeoutMs=$timeout}
 Check ($done.state-eq'completed') "pipeline $($done.state) / $($done.error)"
 $done
}
$session=Json GET '/v0/session'
Check ($session.target-eq$ExpectedTarget-and$session.screenClass-match'TitleScreen') 'fresh title instance required'
$connection=Get-NetTCPConnection -LocalPort ([uri]$base).Port -State Listen|Select-Object -First 1
$clientProcess=Get-Process -Id $connection.OwningProcess
$clientProcess.EnableRaisingEvents=$true
$recording=$null
try{
 $mode=Get-AgentMode $base $auth;Check (-not$mode.reconsentRequired) 'ask the human before reacquire'
 Set-AgentMode $base $auth OPERATE|Out-Null
 Json POST '/v0/diagnostics/ui/test-screen' @{}|Out-Null
 $lease=Json POST '/v0/control/acquire' @{ttlMs=60000}
 $control=$auth.Clone();$control['X-MCP-Control-Lease']=$lease.leaseId
 $before=Json GET '/v0/input/state'
 Check ($before.hostCursorPolicy-eq'never_capture_or_warp_during_takeover'-and-not$before.hostCursorCaptured) 'host cursor separation'
 $hover=Action @{action='hover';selector=@{label='Add Dynamic Control'}}
 Check ($hover.action-eq'hover') 'real routed hover request'
 $afterHover=Json GET '/v0/input/state'
 Check ($afterHover.inputDispatchSequence-$before.inputDispatchSequence-ge12) 'deterministic pointer steps missing'
 $until=[DateTime]::UtcNow.AddSeconds(3)
 do{$tree=Json GET '/v0/ui/tree';$button=@($tree.children|Where-Object label -eq 'Add Dynamic Control')[0];if($button.hovered){break};Start-Sleep -Milliseconds 50}while([DateTime]::UtcNow-lt$until)
 Check $button.hovered 'Vanilla Widget hover was not driven by the virtual pointer'
 Action @{selector=@{label='Add Dynamic Control'}}|Out-Null
 Json POST '/v0/assert' @{condition=@{type='ui.exists';selector=@{label='Dynamic Control'}}}|Out-Null
 $capture=Invoke-WebRequest "$base/v0/capture" -Headers $auth -TimeoutSec 15
 Check ($capture.StatusCode-eq200-and$capture.RawContentLength-gt100) 'content capture'
 $recording=Json POST '/v0/recordings' @{intervalMs=100;durationMs=2500;maxSamples=32;captureFrames=$true;stateReads=@();contactSheet=@{enabled=$true;columns=4;cellWidth=100;cellHeight=60}}
 $drag=Json POST '/v0/pipelines' @{steps=@(@{type='mouse.drag';fromX=2;fromY=2;toX=100;toY=100;durationMs=2000;segments=80});timeoutMs=5000} $control
 $until=[DateTime]::UtcNow.AddSeconds(2)
 do{$holding=Json GET '/v0/input/state';if($holding.pressedButtonCount-gt0){break};Start-Sleep -Milliseconds 20}while([DateTime]::UtcNow-lt$until)
 Check ($holding.pressedButtonCount-gt0) 'drag never entered button ownership'
 Json DELETE "/v0/operations/$($drag.operationId)"|Out-Null
 $until=[DateTime]::UtcNow.AddSeconds(2)
 do{$clean=Json GET '/v0/input/state';if($clean.pressedButtonCount-eq0-and-not$clean.inputSequenceActive){break};Start-Sleep -Milliseconds 25}while([DateTime]::UtcNow-lt$until)
 Check ($clean.pressedButtonCount-eq0) 'cancelled drag leaked a held button'
 $sequence=$clean.inputDispatchSequence;Start-Sleep -Milliseconds 400
 Check ((Json GET '/v0/input/state').inputDispatchSequence-eq$sequence) 'late mouse event after cancellation cleanup'
 Action @{selector=@{label='Close Probe'}}|Out-Null
 Pipeline @(
  @{type='ui.action';selector=@{label='Singleplayer'};holdMs=80},
  @{type='wait.until';condition=@{type='screen';classContains='SelectWorldScreen'};timeoutMs=10000},
  @{type='mouse.click';x=200;y=75;holdMs=80},
  @{type='ui.action';selector=@{label='Play Selected World'};holdMs=80},
  @{type='wait.until';condition=@{type='screen';open=$false};timeoutMs=45000}
 ) 60000|Out-Null
 $player=Json GET '/v0/player'
 $delta=Json POST '/v0/input/mouse/delta' @{dx=20;dy=3} $control
 $moved=Json GET '/v0/player'
 Check ($delta.cameraProcessing-eq'vanilla_sensitivity_inversion'-and-not$delta.directMutationUsed-and($moved.yaw-ne$player.yaw-or$moved.pitch-ne$player.pitch)) 'Vanilla relative camera path'
 Check (-not(Json GET '/v0/input/state').hostCursorCaptured) 'gameplay captured host cursor'
 Pipeline @(@{type='key.tap';key=69;holdMs=50},@{type='wait.until';condition=@{type='screen';classContains='InventoryScreen'};timeoutMs=5000},@{type='key.tap';key=256;holdMs=50},@{type='wait.until';condition=@{type='screen';open=$false};timeoutMs=5000})|Out-Null
 Check ((Get-AgentMode $base $auth).mode-eq'TAKEOVER'-and(Json GET '/v0/input/state').nativeRevocations-eq$before.nativeRevocations) 'Agent Esc became a human event'
 $until=[DateTime]::UtcNow.AddSeconds(8)
 do{$artifact=Json GET "/v0/recordings/$($recording.recordingId)";if($artifact.status-in@('completed','failed')){break};Start-Sleep -Milliseconds 100}while([DateTime]::UtcNow-lt$until)
 Check ($artifact.status-eq'completed'-and$artifact.artifactReady) 'recording finalization'
 Pipeline @(@{type='key.tap';key=256;holdMs=50},@{type='wait.until';condition=@{type='screen';classContains='PauseScreen'};timeoutMs=5000},@{type='ui.action';selector=@{label='Save and Quit to Title'};holdMs=80},@{type='wait.until';condition=@{type='screen';classContains='TitleScreen'};timeoutMs=25000}) 30000|Out-Null
 Json POST '/v0/control/release' $null $control|Out-Null
 $read=Json GET '/v0/session';Check ($read.mode-eq'READ'-and$read.windowIconState-eq'original_minecraft') 'READ handback/icon'
 $lease=Json POST '/v0/control/acquire' @{ttlMs=10000};$control['X-MCP-Control-Lease']=$lease.leaseId
 try{Action @{selector=@{label='Quit Game'};holdMs=80}|Out-Null}catch{}
 Check ($clientProcess.WaitForExit(30000)-and$clientProcess.ExitCode-eq0) 'clean process shutdown'
 [pscustomobject]@{Result='PASS';Target=$ExpectedTarget;Validation='AUTOMATED_DEVELOPMENT_SMOKE_NOT_HUMAN_ACCEPTANCE';Hover='VANILLA_WIDGET_VERIFIED';PointerSteps=($afterHover.inputDispatchSequence-$before.inputDispatchSequence);GuiClick='PASS';DragCancellation='PASS';PostCancelMouseEvents=0;RelativeCamera='VANILLA';HostCursorCaptured=$false;AgentEsc='PASS';Recording=$recording.recordingId;RecordingStatus=$artifact.status;Shutdown='PASS';HumanAcceptance='PENDING';PersistentWriteInvocations=0}
}finally{
 if(-not$clientProcess.HasExited){
  if($recording){try{Json DELETE "/v0/recordings/$($recording.recordingId)"|Out-Null}catch{}}
  try{Json POST '/v0/control/emergency-release'|Out-Null}catch{}
 }
}
