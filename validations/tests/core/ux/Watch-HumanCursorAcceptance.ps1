[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$OutputPath,
    [ValidateRange(10,180)][int]$TimeoutSeconds=120
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '../control/ControlCursorContract.ps1')
# Future human-only observation; do not run as automated/human acceptance interchangeably.
$auth=@{Authorization='Bearer '+(Get-Content -LiteralPath $TokenFile -Raw).Trim()}
$base=$BaseUri.TrimEnd('/')
$capabilities=Invoke-RestMethod "$base/v0/capabilities" -Headers $auth -TimeoutSec 5
$samples=[Collections.Generic.List[object]]::new()
$clock=[Diagnostics.Stopwatch]::StartNew()
$last=''
while($clock.Elapsed.TotalSeconds -lt $TimeoutSeconds){
    $state=Invoke-RestMethod "$base/v0/input/state" -Headers $auth -TimeoutSec 5
    Assert-ControlCursorResponse $capabilities $state
    $sample=[pscustomobject]@{
        ms=$clock.ElapsedMilliseconds;state=$state.controlState
        focused=[bool]$state.hostFocused;captured=[bool]$state.hostCursorCaptured
        grant=[bool]$state.hostCursorCaptureGranted;grants=[long]$state.nativeCaptureGrants
        suppressedButtons=[long]$state.suppressedNativeInput.button
    }
    $signature="$($sample.state)|$($sample.focused)|$($sample.captured)|$($sample.grant)|$($sample.grants)|$($sample.suppressedButtons)"
    if($signature-ne$last){$samples.Add($sample);$last=$signature}
    if($sample.state-ne'AGENT_CONTROLLED'){break}
    $measurement=Measure-ControlCursorSamples $samples.ToArray()
    if($measurement.complete-or$measurement.violations-gt0){break}
    Start-Sleep -Milliseconds 100
}
$measurement=Measure-ControlCursorSamples $samples.ToArray()
$result=[ordered]@{
    evidenceType='human_physical_cursor_runtime_attestation';nativeEventGenerator='HUMAN_ONLY'
    contract='exclusive_takeover_no_host_capture'
    result=$(if($measurement.violations-gt0){'FAIL'}elseif($measurement.complete){'PASS'}else{'PARTIAL'})
    nativeButtonSuppressed=$measurement.nativeButtonSuppressed
    nativeClickCapture=($measurement.capturedSamples-gt0) # compatibility name: observed capture is a failure, never a required success
    focusLossObserved=$measurement.focusLoss;returnWithoutRecapture=$measurement.returnWithoutCapture
    takeoverCaptureViolations=$measurement.violations;elapsedSeconds=$clock.Elapsed.TotalSeconds;samples=$samples
}
$destination=[IO.Path]::GetFullPath($OutputPath)
New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent)|Out-Null
[IO.File]::WriteAllText($destination,($result|ConvertTo-Json -Depth 10))
[pscustomobject]$result|Select-Object -ExcludeProperty samples
