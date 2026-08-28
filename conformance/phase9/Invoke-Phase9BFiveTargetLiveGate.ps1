[CmdletBinding()]param()
$ErrorActionPreference='Stop';$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
function A([bool]$c,[string]$m){if(-not$c){throw "Phase 9B five-target live failed: $m"}}
function J($base,$token,$m,$p,$h,$b){$q=@{Uri=$base+$p;Method=$m;Headers=$h};if($null-ne$b){$q.ContentType='application/json';$q.Body=$b|ConvertTo-Json -Depth 30 -Compress};Invoke-RestMethod @q}
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
  A(-not[bool](Get-NetTCPConnection -LocalPort $run.Port -State Listen -ErrorAction SilentlyContinue))"port $($run.Port) occupied"
  $dir=(Resolve-Path $run.Dir).Path
  $process=Start-Process '.\gradlew.bat' -ArgumentList $run.Task,'--no-daemon','--offline' -WorkingDirectory $root -RedirectStandardOutput (Join-Path $dir 'phase9b-live-stdout.log') -RedirectStandardError (Join-Path $dir 'phase9b-live-stderr.log') -PassThru -WindowStyle Hidden
  $tokenFile=Join-Path $dir 'minecraft-protocol\token';$deadline=[DateTime]::UtcNow.AddMinutes(3);$session=$null
  do{if(Test-Path $tokenFile){$token=(Get-Content $tokenFile -Raw).Trim();$auth=@{Authorization="Bearer $token"};try{$session=Invoke-RestMethod "http://127.0.0.1:$($run.Port)/v0/session" -Headers $auth -TimeoutSec 2}catch{$session=$null}};if($session.target-eq$run.Target){break};Start-Sleep -Seconds 2}while([DateTime]::UtcNow-lt$deadline)
  A($session.target-eq$run.Target)"$($run.Target) not ready";$base="http://127.0.0.1:$($run.Port)"
  [void](J $base $token POST '/v0/control/emergency-release' $auth $null);$lease=J $base $token POST '/v0/control/acquire' $auth @{ttlMs=60000};$lh=@{Authorization="Bearer $token";'X-MCP-Control-Lease'=$lease.leaseId}
  try{
   if(-not$session.inWorld){
    $titleDeadline=[DateTime]::UtcNow.AddSeconds(15)
    do{$titleTree=J $base $token GET '/v0/ui/tree' $auth $null;$single=@($titleTree.children|Where-Object -Property label -eq 'Singleplayer')[0];if($single.active){break};Start-Sleep -Milliseconds 200}while([DateTime]::UtcNow-lt$titleDeadline)
    A([bool]$single.active)'title Interaction Tree not stable'
    for($openAttempt=0;$openAttempt-lt10;$openAttempt++){
     $session=J $base $token GET '/v0/session' $auth $null
     if($session.screenClass-match'SelectWorldScreen'){break}
     [void](J $base $token POST '/v0/ui/action' $lh @{action='click';holdMs=100;selector=@{role='button';label='Singleplayer'}});Start-Sleep -Milliseconds 500
    }
    A($session.screenClass-match'SelectWorldScreen')'Select World did not open'
    for($i=0;$i-lt10;$i++){$tree=J $base $token GET '/v0/ui/tree' $auth $null;$play=$tree.children|Where-Object -Property label -eq 'Play Selected World';if($play.active){break};[void](J $base $token POST '/v0/ui/action' $lh @{action='click';holdMs=100;source='explicit_coordinate';coordinates=@{x=200;y=75}});Start-Sleep -Milliseconds 500}
    A([bool]$play.active)'world selection';[void](J $base $token POST '/v0/ui/action' $lh @{action='click';holdMs=100;selector=@{role='button';label='Play Selected World'}})
    $deadline=[DateTime]::UtcNow.AddSeconds(45);do{Start-Sleep -Milliseconds 100;$session=J $base $token GET '/v0/session' $auth $null}while(((-not$session.inWorld)-or$session.screenClass)-and[DateTime]::UtcNow-lt$deadline)
   }
  }finally{[void](J $base $token POST '/v0/control/release' $lh $null)}
  A($session.inWorld-and-not$session.screenClass)'world not stable'
  $gate=& '.\conformance\phase9\Invoke-Phase9BGate.ps1' -BaseUri $base -TokenFile $tokenFile -ExpectedTarget $run.Target -SkipBuild -HasPhase9ADiagnostics:$run.Has9A
  A($gate.Result-eq'PASS')"$($run.Target) 9B gate"
  $v1=& '.\conformance\phase8\Invoke-Phase8TargetSmoke.ps1' -BaseUri $base -TokenFile $tokenFile -ExpectedTarget $run.Target -ExpectedBackend any -EnterWorld -RequireAuthoritative
  A($v1.Result-eq'PASS')"$($run.Target) V1 smoke"
  [void](J $base $token POST '/v0/control/emergency-release' $auth $null);$quitLease=J $base $token POST '/v0/control/acquire' $auth @{ttlMs=10000};$qh=@{Authorization="Bearer $token";'X-MCP-Control-Lease'=$quitLease.leaseId};try{[void](J $base $token POST '/v0/ui/action' $qh @{action='click';holdMs=100;selector=@{role='button';label='Quit Game'}})}catch{}
  $deadline=[DateTime]::UtcNow.AddSeconds(30);do{Start-Sleep -Milliseconds 250;$listening=[bool](Get-NetTCPConnection -LocalPort $run.Port -State Listen -ErrorAction SilentlyContinue)}while($listening-and[DateTime]::UtcNow-lt$deadline);A(-not$listening)"$($run.Target) shutdown"
  [pscustomobject]@{Target=$run.Target;Formal='PASS';ClientServer='PASS';ProviderV2='PASS';Tickets='PASS';ScheduledTicks='PASS';Budgets='PASS';NoLoad='PASS';V1='PASS';Shutdown='PASS';Metrics=$gate.Details.Budget}
 }
 [pscustomobject]@{Result='PASS';Targets=$results.Count;Results=$results}
}finally{Pop-Location}
