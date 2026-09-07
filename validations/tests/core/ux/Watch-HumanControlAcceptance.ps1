[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$OperationId,
    [Parameter(Mandatory)][string]$OutputPath,
    [int]$TimeoutSeconds=150
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '../control/ControlCursorContract.ps1')
$token=(Get-Content -LiteralPath $TokenFile -Raw).Trim()
$auth=@{Authorization="Bearer $token"}
$base=$BaseUri.TrimEnd('/')
$capabilities=Invoke-RestMethod "$base/v0/capabilities" -Headers $auth -TimeoutSec 5
$samples=[Collections.Generic.List[object]]::new()
$timer=[Diagnostics.Stopwatch]::StartNew()
$manualAt=$null
$lastSequence=$null
$postRevokeEffects=0
$lastSignature=''
$manualAudit=@()
while($timer.Elapsed.TotalSeconds -lt $TimeoutSeconds){
    $state=Invoke-RestMethod "$base/v0/input/state" -Headers $auth -TimeoutSec 5
    Assert-ControlCursorResponse $capabilities $state
    $sample=[ordered]@{
        ms=[long]$timer.ElapsedMilliseconds;state=$state.controlState;focused=[bool]$state.hostFocused
        captured=[bool]$state.hostCursorCaptured;grant=[bool]$state.hostCursorCaptureGranted
        grants=[long]$state.nativeCaptureGrants;revocations=[long]$state.nativeRevocations
        mode=$state.mode;reconsent=$state.reconsentRequired;suppressedButtons=[long]$state.suppressedNativeInput.button
        keys=[int]$state.pressedKeyCount;buttons=[int]$state.pressedButtonCount
        dispatch=[long]$state.inputDispatchSequence;alpha=$state.controlChromeAlpha;icon=$state.windowIconState
    }
    $signature=($sample.GetEnumerator()|Where-Object Key -ne 'ms'|ForEach-Object Value)-join '|'
    if($signature-ne$lastSignature){$samples.Add([pscustomobject]$sample);$lastSignature=$signature}
    if($state.controlState-eq'MANUALLY_REVOKED' -and $state.mode-eq'READ' -and $state.reconsentRequired -and $state.pressedKeyCount-eq0 -and $state.pressedButtonCount-eq0){
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
$cursor=Measure-ControlCursorSamples $samples.ToArray()
$result=[ordered]@{
    evidenceType='human_physical_input_runtime_attestation';nativeEventGenerator='HUMAN_ONLY'
    result=$(if($cursor.violations-gt0){'FAIL'}elseif($null-ne$manualAt-and$postRevokeEffects-eq0-and$operation.state-eq'cancelled'-and$cursor.complete-and$manualAudit.Count-gt0){'PASS'}else{'PARTIAL'})
    manualObserved=$null-ne$manualAt;operationId=$OperationId;operationState=$operation.state
    postRevokeAgentInput=$postRevokeEffects;heldKeys=$state.pressedKeyCount;heldButtons=$state.pressedButtonCount
    contract='exclusive_takeover_no_host_capture';nativeClickCapture=($cursor.capturedSamples-gt0)
    nativeButtonSuppressed=$cursor.nativeButtonSuppressed;focusLossObserved=$cursor.focusLoss;returnWithoutRecapture=$cursor.returnWithoutCapture
    takeoverCaptureViolations=$cursor.violations;manualAuditEntries=$manualAudit.Count
    elapsedSeconds=$timer.Elapsed.TotalSeconds;samples=$samples
}
$destination=[IO.Path]::GetFullPath($OutputPath)
New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent)|Out-Null
[IO.File]::WriteAllText($destination,($result|ConvertTo-Json -Depth 12))
[pscustomobject]$result|Select-Object -ExcludeProperty samples
