[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$OperationId,
    [Parameter(Mandatory)][string]$OutputPath,
    [ValidateRange(15,360)][int]$TimeoutSeconds=150
)
$ErrorActionPreference='Stop'
$base=$BaseUri.TrimEnd('/')
$auth=@{Authorization='Bearer '+(Get-Content -LiteralPath $TokenFile -Raw).Trim()}
$clock=[Diagnostics.Stopwatch]::StartNew();$manualAt=$null;$postEffects=0;$sequence=$null
$samples=[Collections.Generic.List[object]]::new();$last=''
$initial=Invoke-RestMethod "$base/v0/input/state" -Headers $auth -TimeoutSec 5
while($clock.Elapsed.TotalSeconds-lt$TimeoutSeconds){
    $state=Invoke-RestMethod "$base/v0/input/state" -Headers $auth -TimeoutSec 5
    $sample=[pscustomobject]@{
        ms=$clock.ElapsedMilliseconds;mode=$state.mode;controlState=$state.controlState
        reconsentRequired=$state.reconsentRequired;generation=$state.modeVersion.generation
        keys=$state.pressedKeyCount;buttons=$state.pressedButtonCount;dispatch=$state.inputDispatchSequence
        nativeRevocations=$state.nativeRevocations;icon=$state.windowIconState;alpha=$state.controlChromeAlpha
    }
    $signature=($sample.PSObject.Properties|Where-Object Name -ne ms|ForEach-Object Value)-join'|'
    if($signature-ne$last){$samples.Add($sample);$last=$signature}
    if($state.mode-eq'READ'-and$state.reconsentRequired-and$state.pressedKeyCount-eq0-and$state.pressedButtonCount-eq0){
        if($null-eq$manualAt){$manualAt=$clock.Elapsed.TotalSeconds;$sequence=$state.inputDispatchSequence}
        elseif($state.inputDispatchSequence-ne$sequence){$postEffects++;$sequence=$state.inputDispatchSequence}
        if($clock.Elapsed.TotalSeconds-$manualAt-ge8){break}
    }
    Start-Sleep -Milliseconds 100
}
$operation=Invoke-RestMethod "$base/v0/operations/$OperationId" -Headers $auth -TimeoutSec 5
$read=Invoke-RestMethod "$base/v0/player" -Headers $auth -TimeoutSec 5
$result=[ordered]@{
    result=$(if($null-ne$manualAt-and$postEffects-eq0-and$operation.state-eq'cancelled'-and$state.nativeRevocations-gt$initial.nativeRevocations-and$read.available){'PASS'}else{'PARTIAL'})
    nativeEventSource='USER_PHYSICAL_ESC_ONLY';mode=$state.mode;reconsentRequired=$state.reconsentRequired
    operationId=$OperationId;operationState=$operation.state;heldKeys=$state.pressedKeyCount;heldButtons=$state.pressedButtonCount
    postRevokeInput=$postEffects;readStillAvailable=$read.available;elapsedSeconds=$clock.Elapsed.TotalSeconds;samples=$samples
}
$destination=[IO.Path]::GetFullPath($OutputPath)
New-Item -ItemType Directory -Force -Path (Split-Path $destination -Parent)|Out-Null
[IO.File]::WriteAllText($destination,($result|ConvertTo-Json -Depth 10))
[pscustomobject]$result|Select-Object -ExcludeProperty samples
