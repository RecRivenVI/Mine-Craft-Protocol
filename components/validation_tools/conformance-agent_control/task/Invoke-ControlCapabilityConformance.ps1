[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget,
    [switch]$ExerciseModes
)
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot 'ControlCursorContract.ps1')
. (Join-Path $PSScriptRoot 'ModeHelpers.ps1')
$base=$BaseUri.TrimEnd('/');$headers=@{Authorization='Bearer '+(Get-Content -LiteralPath $TokenFile -Raw).Trim()}
$lease=$null;$samples=[Collections.Generic.List[object]]::new()
function Read-Contract([string]$ExpectedMode) {
    $caps=Invoke-RestMethod "$base/v0/capabilities" -Headers $headers -TimeoutSec 10
    $session=Invoke-RestMethod "$base/v0/session" -Headers $headers -TimeoutSec 10
    $state=Invoke-RestMethod "$base/v0/input/state" -Headers $headers -TimeoutSec 10
    if($caps.target-ne$ExpectedTarget-or$session.target-ne$ExpectedTarget-or$state.target-ne$ExpectedTarget){throw 'Target mismatch'}
    foreach($response in @($caps,$session,$state)){
        Assert-ControlCursorResponse $caps $response
        if($ExpectedMode-and$response.mode-ne$ExpectedMode){throw 'Mode transition did not reach expected state'}
    }
    $samples.Add([pscustomobject]@{mode=$state.mode;capability=$caps.capabilities.'input.host_cursor_capture';policy=$state.hostCursorPolicy;granted=$state.hostCursorCaptureGranted;captured=$state.hostCursorCaptured;nativeGrants=$state.nativeCaptureGrants})
}
try {
    Read-Contract
    if($ExerciseModes){
        $initial=Get-AgentMode $base $headers
        if($initial.mode-ne'READ'-or$initial.reconsentRequired){throw 'Isolated READ session without manual latch required; no automatic reconsent'}
        Set-AgentMode $base $headers OPERATE|Out-Null;Read-Contract OPERATE
        Set-AgentMode $base $headers READ|Out-Null
        $lease=Invoke-RestMethod "$base/v0/control/acquire" -Method Post -Headers $headers -ContentType application/json -Body '{"ttlMs":10000}'
        Read-Contract TAKEOVER
    }
}finally{
    if($lease){$control=$headers.Clone();$control['X-MCP-Control-Lease']=$lease.leaseId;Invoke-RestMethod "$base/v0/control/release" -Method Post -Headers $control -TimeoutSec 10|Out-Null}
    elseif($ExerciseModes-and$initial-and$initial.mode-eq'READ'-and-not$initial.reconsentRequired){Set-AgentMode $base $headers READ|Out-Null}
}
[pscustomobject]@{Result='PASS';Target=$ExpectedTarget;Source='ACTUAL_NATIVE_CAPABILITIES_SESSION_INPUT_STATE';Samples=$samples;HumanAcceptance='NOT_RUN';PersistentWrite=0}
