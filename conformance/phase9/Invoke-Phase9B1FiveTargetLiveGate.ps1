[CmdletBinding()]param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
function A([bool]$condition,[string]$message){if(-not $condition){throw "Phase 9B.1 five-target live failed: $message"}}
function J($base,$method,$path,$headers,$body){$parameters=@{Uri=$base+$path;Method=$method;Headers=$headers};if($null-ne$body){$parameters.ContentType='application/json';$parameters.Body=$body|ConvertTo-Json -Depth 30 -Compress};Invoke-RestMethod @parameters}
$runs=@(
 @{Target='1.20.1-forge';Task=':versions:1.20.1-forge:runClient';Dir='runs\1.20.1-forge\client';Port=25581;Has9A=$true},
 @{Target='1.21.1-neoforge';Task=':versions:1.21.1-neoforge:runClient';Dir='runs\1.21.1-neoforge\client';Port=25581;Has9A=$false},
 @{Target='26.1.2-neoforge';Task=':versions:26.1.2-neoforge:runClient';Dir='runs\26.1.2-neoforge\client';Port=25582;Has9A=$false},
 @{Target='26.2-neoforge';Task=':versions:26.2-neoforge:runClient';Dir='runs\26.2-neoforge\client';Port=25582;Has9A=$true},
 @{Target='26.2-fabric';Task=':versions:26.2-fabric:runClient';Dir='runs\26.2-fabric\client';Port=25583;Has9A=$true})
Push-Location $root
try{
 $env:MCP_RUNTIME_SCOPES='read,ui,input,capture,event,diagnostics,control,command,fixture,debug'
 $results=foreach($run in $runs){
  Write-Host "[Phase9B.1] starting $($run.Target)"
  A(-not[bool](Get-NetTCPConnection -LocalPort $run.Port -State Listen -ErrorAction SilentlyContinue))"port $($run.Port) occupied"
  $dir=(Resolve-Path $run.Dir).Path
  $process=Start-Process '.\gradlew.bat' -ArgumentList $run.Task,'--no-daemon','--offline' -WorkingDirectory $root -RedirectStandardOutput(Join-Path $dir 'phase9b1-live-stdout.log') -RedirectStandardError(Join-Path $dir 'phase9b1-live-stderr.log') -PassThru -WindowStyle Hidden
  $tokenFile=Join-Path $dir 'minecraft-protocol\token';$deadline=[DateTime]::UtcNow.AddMinutes(6);$session=$null
  do{if(Test-Path $tokenFile){$token=(Get-Content $tokenFile -Raw).Trim();$auth=@{Authorization="Bearer $token"};try{$session=Invoke-RestMethod "http://127.0.0.1:$($run.Port)/v0/session" -Headers $auth -TimeoutSec 2}catch{$session=$null}};if($session.target-eq$run.Target){break};Start-Sleep -Seconds 2}while([DateTime]::UtcNow-lt$deadline)
  A($session.target-eq$run.Target)"$($run.Target) not ready";$base="http://127.0.0.1:$($run.Port)"
  J $base POST '/v0/control/emergency-release' $auth $null|Out-Null;$lease=J $base POST '/v0/control/acquire' $auth @{ttlMs=60000};$leaseHeaders=@{Authorization="Bearer $token";'X-MCP-Control-Lease'=$lease.leaseId}
  try{
   if(-not $session.inWorld){
    $titleDeadline=[DateTime]::UtcNow.AddSeconds(20)
    do{$titleTree=J $base GET '/v0/ui/tree' $auth $null;$single=@($titleTree.children|Where-Object -Property label -eq 'Singleplayer')[0];if($single.active){break};Start-Sleep -Milliseconds 250}while([DateTime]::UtcNow-lt$titleDeadline)
    A([bool]$single.active)"$($run.Target) title UI stable"
    for($attempt=0;$attempt-lt10;$attempt++){$session=J $base GET '/v0/session' $auth $null;if($session.screenClass-match'SelectWorldScreen'){break};J $base POST '/v0/ui/action' $leaseHeaders @{action='click';holdMs=100;selector=@{role='button';label='Singleplayer'}}|Out-Null;Start-Sleep -Milliseconds 500}
    A($session.screenClass-match'SelectWorldScreen')'Select World'
    for($attempt=0;$attempt-lt10;$attempt++){$tree=J $base GET '/v0/ui/tree' $auth $null;$play=@($tree.children|Where-Object -Property label -eq 'Play Selected World')[0];if($play.active){break};J $base POST '/v0/ui/action' $leaseHeaders @{action='click';holdMs=100;source='explicit_coordinate';coordinates=@{x=200;y=75}}|Out-Null;Start-Sleep -Milliseconds 500}
    A([bool]$play.active)'world selection'
    J $base POST '/v0/ui/action' $leaseHeaders @{action='click';holdMs=100;selector=@{role='button';label='Play Selected World'}}|Out-Null
    $deadline=[DateTime]::UtcNow.AddSeconds(45)
    do{Start-Sleep -Milliseconds 150;$session=J $base GET '/v0/session' $auth $null}while(((-not$session.inWorld)-or$session.screenClass)-and[DateTime]::UtcNow-lt$deadline)
   }
  }finally{J $base POST '/v0/control/release' $leaseHeaders $null|Out-Null}
  A($session.inWorld-and-not$session.screenClass)"$($run.Target) world"
  $b1=& '.\conformance\phase9\Invoke-Phase9B1Gate.ps1' -BaseUri $base -TokenFile $tokenFile -ExpectedTarget $run.Target -HasPhase9ADiagnostics:$run.Has9A -SkipBuild
  A($b1.Result-eq'PASS')"$($run.Target) 9B.1"
  $p=@{BaseUri=$base;TokenFile=$tokenFile;ExpectedTarget=$run.Target}
  $formal=[ordered]@{Deep=& '.\conformance\phase9\Invoke-Phase9BDeepObservationConformance.ps1' @p -HasPhase9ADiagnostics:$run.Has9A;ClientServer=& '.\conformance\phase9\Invoke-Phase9BClientServerConformance.ps1' @p;Provider=& '.\conformance\phase9\Invoke-Phase9BProviderConformance.ps1' @p;Tickets=& '.\conformance\phase9\Invoke-Phase9BTicketConformance.ps1' @p;Scheduled=& '.\conformance\phase9\Invoke-Phase9BScheduledTickConformance.ps1' @p;Budget=& '.\conformance\phase9\Invoke-Phase9BBudgetConformance.ps1' @p}
  foreach($entry in $formal.GetEnumerator()){A($entry.Value.Result-eq'PASS')"$($run.Target) $($entry.Key)"}
  $v1=& '.\conformance\phase8\Invoke-Phase8TargetSmoke.ps1' -BaseUri $base -TokenFile $tokenFile -ExpectedTarget $run.Target -ExpectedBackend any -EnterWorld -RequireAuthoritative;A($v1.Result-eq'PASS')"$($run.Target) V1"
  J $base POST '/v0/control/emergency-release' $auth $null|Out-Null;$quitLease=J $base POST '/v0/control/acquire' $auth @{ttlMs=10000};$quitHeaders=@{Authorization="Bearer $token";'X-MCP-Control-Lease'=$quitLease.leaseId}
  $quitDeadline=[DateTime]::UtcNow.AddSeconds(15)
  do{$quitTree=J $base GET '/v0/ui/tree' $auth $null;$quit=@($quitTree.children|Where-Object -Property label -eq 'Quit Game')[0];if($quit.active){break};Start-Sleep -Milliseconds 250}while([DateTime]::UtcNow-lt$quitDeadline)
  A([bool]$quit.active)"$($run.Target) quit UI stable"
  try{J $base POST '/v0/ui/action' $quitHeaders @{action='click';holdMs=100;selector=@{role='button';label='Quit Game'}}|Out-Null}catch{}
  $deadline=[DateTime]::UtcNow.AddSeconds(30);do{Start-Sleep -Milliseconds 250;$listening=[bool](Get-NetTCPConnection -LocalPort $run.Port -State Listen -ErrorAction SilentlyContinue)}while($listening-and[DateTime]::UtcNow-lt$deadline);A(-not$listening)"$($run.Target) shutdown"
  Write-Host "[Phase9B.1] completed $($run.Target)"
  [pscustomobject]@{Target=$run.Target;Revision=$b1.Details.Revision.Result;Scope=$b1.Details.Policy.Scope;Perspective=$b1.Details.Policy.Perspective;Affinity='PASS';Policy='PASS';Timeout=$b1.Details.Lifecycle.Timeout;Schema=$b1.Details.Schema.Result;Formal='PASS';V1='PASS';Shutdown='PASS';Metrics=$formal.Budget}
 }
 [pscustomobject]@{Result='PASS';Targets=$results.Count;Results=$results}
}finally{Pop-Location}
