[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget
)
# Bounded title-only Native checks. No native human events, no persistent writes.
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$auth=@{Authorization='Bearer '+(Get-Content -LiteralPath $TokenFile -Raw).Trim();Connection='close'}
$task=$null;$lease=$null;$recording=$null
$checks=[Collections.Generic.List[string]]::new()
function Check([bool]$Ok,[string]$Name){if(-not$Ok){throw "Task continuity: $Name"};$checks.Add($Name)}
function Json([string]$Method,[string]$Path,[object]$Body=$null,[hashtable]$Headers=$auth){
    $requestParameters=@{Uri=$BaseUri.TrimEnd('/')+$Path;Method=$Method;Headers=$Headers;TimeoutSec=20}
    if($null-ne$Body){$requestParameters.ContentType='application/json';$requestParameters.Body=$Body|ConvertTo-Json -Depth 30 -Compress}
    Invoke-RestMethod @requestParameters
}
function SelectMode([string]$Mode){
    $current=Json GET '/v0/control/mode'
    Json POST '/v0/control/mode' @{mode=$Mode;expectedModeVersion=$current.modeVersion} $auth|Out-Null
}
function Action([string]$Label,[string]$Action='click'){
    Json POST '/v0/ui/action' @{action=$Action;selector=@{label=$Label};holdMs=80} $control
}
$session=Json GET '/v0/session'
Check ($session.target-eq$ExpectedTarget-and$session.screenClass-match'TitleScreen') 'isolated title instance'
$initial=Json GET '/v0/control/mode'
Check ($initial.mode-eq'READ'-and-not$initial.reconsentRequired-and-not$initial.agentTask.active) 'no foreign control/task/override'
try {
    $task=Json POST '/v0/control/task' @{action='begin';ttlMs=60000;budgetMs=120000}
    Check (-not$task.grantsAuthority-and$task.active) 'task grants no authority'
    Start-Sleep -Milliseconds 750
    $alphas=[Collections.Generic.List[double]]::new();$widths=[Collections.Generic.List[double]]::new()
    for($i=0;$i-lt18;$i++){
        $state=Json GET '/v0/input/state'
        $alphas.Add([double]$state.controlChromeAlpha);$widths.Add([double]$state.controlChromeEdgeWidth)
        Check ($state.presenceActive-and$state.mode-eq'READ') 'READ survives closed HTTP connection'
        Start-Sleep -Milliseconds 180
    }
    Check (($alphas|Measure-Object -Minimum).Minimum-ge0.99) 'no presence Fade restart across requests'
    Check ((($widths|Measure-Object -Maximum).Maximum-($widths|Measure-Object -Minimum).Minimum)-gt0.06) 'three-second breathing actually progresses'
    Start-Sleep -Milliseconds 3500
    Check ((Json GET '/v0/input/state').controlChromeAlpha-ge0.99) 'task waiting without network keeps presence'
    SelectMode OPERATE;Start-Sleep -Milliseconds 600
    Check ((Json GET '/v0/input/state').controlChromeAlpha-ge0.99) 'mode transition preserves presence'
    SelectMode READ
    $mode=Json GET '/v0/control/mode'
    $lease=Json POST '/v0/control/acquire' @{ttlMs=60000;taskId=$task.taskId;expectedModeVersion=$mode.modeVersion}
    $control=$auth.Clone();$control['X-MCP-Control-Lease']=$lease.leaseId
    Action 'Options...' hover|Out-Null
    $before=(Json GET '/v0/input/state').agentPointer
    Action 'Options...'|Out-Null
    Json POST '/v0/wait/until' @{condition=@{type='screen';classContains='OptionsScreen'};timeoutMs=10000}|Out-Null
    $after=(Json GET '/v0/input/state').agentPointer
    Check ([Math]::Abs($before.x-$after.x)-lt1e-9-and[Math]::Abs($before.y-$after.y)-lt1e-9) 'page replacement preserves GUI coordinates'
    Action 'Done' hover|Out-Null
    $before=(Json GET '/v0/input/state').agentPointer
    Action 'Done'|Out-Null
    Json POST '/v0/wait/until' @{condition=@{type='screen';classContains='TitleScreen'};timeoutMs=10000}|Out-Null
    $after=(Json GET '/v0/input/state').agentPointer
    Check ([Math]::Abs($before.x-$after.x)-lt1e-9-and[Math]::Abs($before.y-$after.y)-lt1e-9) 'return page preserves GUI coordinates'
    $recording=Json POST '/v0/recordings' @{intervalMs=100;durationMs=1500;maxSamples=20;captureFrames=$true;stateReads=@();contactSheet=@{enabled=$false}}
    for($i=0;$i-lt4;$i++){
        $capture=Invoke-WebRequest ($BaseUri.TrimEnd('/')+'/v0/capture') -Headers $auth -TimeoutSec 20
        Check ($capture.StatusCode-eq200-and$capture.RawContentLength-gt100) 'concurrent content capture succeeds'
    }
    Json DELETE "/v0/recordings/$($recording.recordingId)"|Out-Null
    # Independent observation work keeps Presence while finalizing; wait for its real terminal state.
    $recordingDeadline=[DateTime]::UtcNow.AddSeconds(20)
    do {
        $recorded=Json GET "/v0/recordings/$($recording.recordingId)"
        if($recorded.lifecycle-eq'CLOSED'){break}
        Start-Sleep -Milliseconds 100
    }while([DateTime]::UtcNow-lt$recordingDeadline)
    Check ($recorded.lifecycle-eq'CLOSED'-and$recorded.artifactReady) 'independent recording fully finalizes before testing task-only Fade'
    $recording=$null
    $pending=Json POST '/v0/pipelines' @{steps=@(@{type='delay';durationMs=5000},@{type='key.tap';key=65});timeoutMs=10000} $control
    $ended=Json POST '/v0/control/task' @{action='end';taskId=$task.taskId}
    Check ($ended.status-eq'ENDED') 'task end reaches terminal state'
    $state=Json GET '/v0/input/state';$sequence=$state.inputDispatchSequence
    Check ($state.mode-eq'READ'-and$state.pressedKeyCount-eq0-and$state.pressedButtonCount-eq0-and-not$state.inputSequenceActive-and-not$state.activeGestureId) 'end drains attached input and returns READ'
    Check ((Json GET "/v0/operations/$($pending.operationId)").state-eq'cancelled') 'task end cancels pending player operation'
    $inputDeadline=[DateTime]::UtcNow.AddMilliseconds(5500)
    do {
        Start-Sleep -Milliseconds 200
        $state=Json GET '/v0/input/state'
        Check ($state.inputDispatchSequence-eq$sequence) 'no late input past cancelled pipeline due time'
        Check (-not$state.presenceActive) 'closed task does not implicitly revive'
    }while([DateTime]::UtcNow-lt$inputDeadline)
    Check ($state.controlChromeAlpha-lt0.02) 'task-only visual Fade reaches transparent'
    $expired=Json POST '/v0/control/task' @{action='begin';ttlMs=1000;budgetMs=1000};$task=$expired
    $lease=Json POST '/v0/control/acquire' @{ttlMs=60000;taskId=$task.taskId};$control=$auth.Clone();$control['X-MCP-Control-Lease']=$lease.leaseId
    Start-Sleep -Milliseconds 1500
    $state=Json GET '/v0/input/state'
    Check ((Json GET '/v0/control/task').status-eq'EXPIRED'-and$state.mode-eq'READ'-and$state.pressedKeyCount-eq0-and$state.pressedButtonCount-eq0) 'TTL expiry drains longer input Lease'
} finally {
    try {if($recording){Json DELETE "/v0/recordings/$($recording.recordingId)"|Out-Null}}
    finally {if($task){Json POST '/v0/control/task' @{action='end';taskId=$task.taskId}|Out-Null}}
}
$result=[ordered]@{result='PASS';target=$ExpectedTarget;testedCommit=(& git -C $repo rev-parse HEAD).Trim();workingTreeDirty=[bool](& git -C $repo status --porcelain);runtime='loader_development_source_sets';checks=$checks;alphaMinimum=($alphas|Measure-Object -Minimum).Minimum;breathingWidthMinimum=($widths|Measure-Object -Minimum).Minimum;breathingWidthMaximum=($widths|Measure-Object -Maximum).Maximum;capturePixelExclusion='UNIT_BOUNDARY_TESTS_NOT_VISUAL_COMPARISON';humanAcceptance='NOT_PERFORMED';persistentWriteInvocations=0}
$path=Join-Path $repo "validations/conformance-agent_control/result/task-continuity-$ExpectedTarget.json"
$json=($result|ConvertTo-Json -Depth 20)-replace"`r`n","`n"
[IO.File]::WriteAllText($path,$json+"`n",[Text.UTF8Encoding]::new($false))
[pscustomobject]$result
