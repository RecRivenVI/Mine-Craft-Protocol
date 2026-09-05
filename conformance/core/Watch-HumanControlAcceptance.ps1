[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$OperationId,
    [Parameter(Mandatory)][string]$OutputPath,
    [int]$TimeoutSeconds=150
)
$ErrorActionPreference='Stop'
$token=(Get-Content -LiteralPath $TokenFile -Raw).Trim()
$auth=@{Authorization="Bearer $token"}
$base=$BaseUri.TrimEnd('/')
$samples=[Collections.Generic.List[object]]::new()
$timer=[Diagnostics.Stopwatch]::StartNew()
$manualAt=$null
$lastSequence=$null
$postRevokeEffects=0
$lastSignature=''
$manualAudit=@()
$initialGrants=$null
while($timer.Elapsed.TotalSeconds -lt $TimeoutSeconds){
    $state=Invoke-RestMethod "$base/v0/input/state" -Headers $auth -TimeoutSec 5
    if($null-eq$initialGrants){$initialGrants=[long]$state.nativeCaptureGrants}
    $sample=[ordered]@{
        ms=[long]$timer.ElapsedMilliseconds;state=$state.controlState;focused=[bool]$state.hostFocused
        captured=[bool]$state.hostCursorCaptured;grant=[bool]$state.hostCursorCaptureGranted
        grants=[long]$state.nativeCaptureGrants;revocations=[long]$state.nativeRevocations
        keys=[int]$state.pressedKeyCount;buttons=[int]$state.pressedButtonCount
        dispatch=[long]$state.inputDispatchSequence;alpha=$state.controlChromeAlpha;icon=$state.windowIconState
    }
    $signature=($sample.GetEnumerator()|Where-Object Key -ne 'ms'|ForEach-Object Value)-join '|'
    if($signature-ne$lastSignature){$samples.Add([pscustomobject]$sample);$lastSignature=$signature}
    if($state.controlState-eq'MANUALLY_REVOKED' -and $state.pressedKeyCount-eq0 -and $state.pressedButtonCount-eq0){
        if($null-eq$manualAt){
            $manualAt=$timer.Elapsed.TotalSeconds;$lastSequence=[long]$state.inputDispatchSequence
            $audit=Invoke-RestMethod "$base/v0/audit?limit=256" -Headers $auth -TimeoutSec 5
            $manualAudit=@($audit.entries|Where-Object {($_|ConvertTo-Json -Compress)-match 'human_manual_revocation'})
        }
        elseif([long]$state.inputDispatchSequence-ne$lastSequence){$postRevokeEffects++;$lastSequence=[long]$state.inputDispatchSequence}
        if($timer.Elapsed.TotalSeconds-$manualAt-ge50){break}
    }
    Start-Sleep -Milliseconds 100
}
$operation=Invoke-RestMethod "$base/v0/operations/$OperationId" -Headers $auth -TimeoutSec 5
$everFocusedFree=@($samples|Where-Object {$_.state-eq'AGENT_CONTROLLED'-and$_.focused-and-not$_.captured-and-not$_.grant}).Count-gt0
$captureSample=$samples|Where-Object {$_.state-eq'AGENT_CONTROLLED'-and$_.captured-and$_.grant-and$_.grants-gt$initialGrants}|Select-Object -First 1
$everCaptured=$null-ne$captureSample
$backgroundFree=@($samples|Where-Object {$_.state-eq'AGENT_CONTROLLED'-and-not$_.focused-and-not$_.captured-and-not$_.grant}).Count-gt0
# The host cursor restriction is a control-lease rule, not a replacement for Vanilla
# after handback. Keep post-revocation samples for input-quiescence evidence only.
$backgroundCapture=@($samples|Where-Object {$_.state-eq'AGENT_CONTROLLED'-and-not$_.focused-and$_.captured}).Count
$lossAfterClick=$samples|Where-Object {$null-ne$captureSample-and$_.ms-gt$captureSample.ms-and$_.state-eq'AGENT_CONTROLLED'-and-not$_.focused-and-not$_.captured-and-not$_.grant}|Select-Object -First 1
$returnWithoutClick=$samples|Where-Object {$null-ne$lossAfterClick-and$_.ms-gt$lossAfterClick.ms-and$_.state-eq'AGENT_CONTROLLED'-and$_.focused-and-not$_.captured-and-not$_.grant}|Select-Object -First 1
$result=[ordered]@{
    evidenceType='human_physical_input_runtime_attestation';nativeEventGenerator='HUMAN_ONLY'
    result=$(if($null-ne$manualAt-and$postRevokeEffects-eq0-and$operation.state-eq'cancelled'-and$everFocusedFree-and$everCaptured-and$null-ne$returnWithoutClick-and$backgroundCapture-eq0-and$manualAudit.Count-gt0){'PASS'}else{'PARTIAL'})
    manualObserved=$null-ne$manualAt;operationId=$OperationId;operationState=$operation.state
    postRevokeAgentInput=$postRevokeEffects;heldKeys=$state.pressedKeyCount;heldButtons=$state.pressedButtonCount
    focusWithoutGrab=$everFocusedFree;nativeClickCapture=$everCaptured;focusLossRelease=$backgroundFree
    focusLossAfterClick=$null-ne$lossAfterClick;returnWithoutRecapture=$null-ne$returnWithoutClick
    backgroundCaptureSamples=$backgroundCapture;manualAuditEntries=$manualAudit.Count
    elapsedSeconds=$timer.Elapsed.TotalSeconds;samples=$samples
}
$destination=[IO.Path]::GetFullPath($OutputPath)
New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent)|Out-Null
[IO.File]::WriteAllText($destination,($result|ConvertTo-Json -Depth 12))
[pscustomobject]$result|Select-Object -ExcludeProperty samples
