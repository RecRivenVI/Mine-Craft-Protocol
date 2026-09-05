[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget,
    [Parameter(Mandatory)][string]$InstanceDirectory
)
$ErrorActionPreference='Stop'
$base=$BaseUri.TrimEnd('/')
$auth=@{Authorization='Bearer '+(Get-Content -LiteralPath $TokenFile -Raw).Trim()}
function Json([string]$method,[string]$path,[object]$body=$null,[hashtable]$headers=$auth){
    $p=@{Uri=$base+$path;Method=$method;Headers=$headers;TimeoutSec=15}
    if($null-ne$body){$p.ContentType='application/json';$p.Body=$body|ConvertTo-Json -Depth 25 -Compress}
    Invoke-RestMethod @p
}
$session=Json GET '/v0/session'
if($session.target-ne$ExpectedTarget-or-not$session.inWorld-or$session.screenClass){throw 'Safe test world without GUI required'}
if((Json GET '/v0/control/mode').reconsentRequired){throw 'Ask the user for reconsent before reacquire'}
$lease=Json POST '/v0/control/acquire' @{ttlMs=60000}
$control=$auth.Clone();$control['X-MCP-Control-Lease']=$lease.leaseId
$cycles=@()
try{
    for($i=0;$i-lt3;$i++){
        $open=Json POST '/v0/pipelines' @{timeoutMs=8000;steps=@(@{type='key.tap';key=256;holdMs=40},@{type='wait.until';condition=@{type='screen';classContains='PauseScreen'};timeoutMs=5000})} $control
        $opened=Json POST "/v0/operations/$($open.operationId)/wait" @{timeoutMs=8000}
        if($opened.state-ne'completed'){throw 'Pause open failed'}
        $tree=Json GET '/v0/ui/tree'
        $target=@($tree.children|Where-Object label -eq 'Save and Quit to Title')
        if($target.Count-ne1-or-not$target[0].active){throw 'Save & Quit target missing/ambiguous/disabled'}
        $close=Json POST '/v0/pipelines' @{timeoutMs=8000;steps=@(@{type='key.tap';key=256;holdMs=40},@{type='wait.until';condition=@{type='screen';open=$false};timeoutMs=5000})} $control
        $closed=Json POST "/v0/operations/$($close.operationId)/wait" @{timeoutMs=8000}
        if($closed.state-ne'completed'){throw 'Pause close failed'}
        $cycles+=@{openOperation=$open.operationId;closeOperation=$close.operationId;saveTarget=$target[0].nodeId;screenRevision=$tree.screenRevision;result='PASS'}
    }
}finally{Json POST '/v0/control/release' $null $control|Out-Null}
$exit=& (Join-Path $PSScriptRoot '../core/Invoke-CoreExitConformance.ps1') -BaseUri $base -TokenFile $TokenFile -ExpectedTarget $ExpectedTarget -InstanceDirectory $InstanceDirectory
if($exit.Result-ne'PASS'){throw 'Save & Quit/close regression failed'}
[pscustomobject]@{Result='PASS';Target=$ExpectedTarget;PauseCycles=$cycles;SaveAndQuit=$exit;HistoricalTimeout='historical single non-reproduced timeout';PersistentWriteInvocations=0}
