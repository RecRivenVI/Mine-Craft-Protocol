[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$OutputPath,
    [ValidateRange(10,180)][int]$TimeoutSeconds=120
)
$ErrorActionPreference='Stop'
$auth=@{Authorization='Bearer '+(Get-Content -LiteralPath $TokenFile -Raw).Trim()}
$base=$BaseUri.TrimEnd('/')
$samples=[Collections.Generic.List[object]]::new()
$clock=[Diagnostics.Stopwatch]::StartNew()
$initialGrants=$null
$capture=$null
$loss=$null
$returned=$null
$last=''
while($clock.Elapsed.TotalSeconds -lt $TimeoutSeconds){
    $state=Invoke-RestMethod "$base/v0/input/state" -Headers $auth -TimeoutSec 5
    if($null-eq$initialGrants){$initialGrants=[long]$state.nativeCaptureGrants}
    $sample=[pscustomobject]@{
        ms=$clock.ElapsedMilliseconds;state=$state.controlState
        focused=[bool]$state.hostFocused;captured=[bool]$state.hostCursorCaptured
        grant=[bool]$state.hostCursorCaptureGranted;grants=[long]$state.nativeCaptureGrants
    }
    $signature="$($sample.state)|$($sample.focused)|$($sample.captured)|$($sample.grant)|$($sample.grants)"
    if($signature-ne$last){$samples.Add($sample);$last=$signature}
    if($sample.state-ne'AGENT_CONTROLLED'){break}
    if($null-eq$capture-and$sample.focused-and$sample.captured-and$sample.grant-and$sample.grants-gt$initialGrants){$capture=$sample}
    if($null-ne$capture-and$null-eq$loss-and-not$sample.focused-and-not$sample.captured-and-not$sample.grant){$loss=$sample}
    if($null-ne$loss-and$sample.focused-and-not$sample.captured-and-not$sample.grant){$returned=$sample;break}
    Start-Sleep -Milliseconds 100
}
$backgroundCapture=@($samples|Where-Object {$_.state-eq'AGENT_CONTROLLED'-and-not$_.focused-and$_.captured}).Count
$focusOnly=@($samples|Where-Object {$_.state-eq'AGENT_CONTROLLED'-and$_.focused-and-not$_.captured-and-not$_.grant-and($null-eq$capture-or$_.ms-lt$capture.ms)}).Count-gt0
$result=[ordered]@{
    evidenceType='human_physical_cursor_runtime_attestation';nativeEventGenerator='HUMAN_ONLY'
    result=$(if($focusOnly-and$null-ne$capture-and$null-ne$loss-and$null-ne$returned-and$backgroundCapture-eq0){'PASS'}else{'PARTIAL'})
    focusWithoutGrab=$focusOnly;nativeClickCapture=$null-ne$capture
    focusLossRelease=$null-ne$loss;returnWithoutRecapture=$null-ne$returned
    backgroundCaptureSamples=$backgroundCapture;elapsedSeconds=$clock.Elapsed.TotalSeconds;samples=$samples
}
$destination=[IO.Path]::GetFullPath($OutputPath)
New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent)|Out-Null
[IO.File]::WriteAllText($destination,($result|ConvertTo-Json -Depth 10))
[pscustomobject]$result|Select-Object -ExcludeProperty samples
