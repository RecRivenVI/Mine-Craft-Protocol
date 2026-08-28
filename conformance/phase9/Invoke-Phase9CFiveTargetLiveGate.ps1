[CmdletBinding()]param([switch]$Offline,[string[]]$OnlyTargets=@())
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
function Assert-True([bool]$Condition,[string]$Message){if(-not$Condition){throw "Phase 9C five-target live failed: $Message"}}
function Invoke-Json($Base,[string]$Method,[string]$Path,[hashtable]$Headers,[object]$Body){$p=@{Uri=$Base+$Path;Method=$Method;Headers=$Headers;TimeoutSec=10};if($null-ne$Body){$p.ContentType='application/json';$p.Body=$Body|ConvertTo-Json -Depth 40 -Compress};Invoke-RestMethod @p}
$runs=@(
 @{Target='1.20.1-forge';Task=':versions:1.20.1-forge:runClient';Dir='runs\1.20.1-forge\client';Port=25581;Has9A=$true},
 @{Target='1.21.1-neoforge';Task=':versions:1.21.1-neoforge:runClient';Dir='runs\1.21.1-neoforge\client';Port=25581;Has9A=$false},
 @{Target='26.1.2-neoforge';Task=':versions:26.1.2-neoforge:runClient';Dir='runs\26.1.2-neoforge\client';Port=25582;Has9A=$false},
 @{Target='26.2-neoforge';Task=':versions:26.2-neoforge:runClient';Dir='runs\26.2-neoforge\client';Port=25582;Has9A=$true;WorldExit=$true},
 @{Target='26.2-fabric';Task=':versions:26.2-fabric:runClient';Dir='runs\26.2-fabric\client';Port=25583;Has9A=$true})
if($OnlyTargets.Count){$runs=@($runs|Where-Object{$_.Target-in$OnlyTargets})}
Push-Location $root
try {
 $env:MCP_RUNTIME_SCOPES='read,ui,input,capture,event,diagnostics,control,command,fixture,debug,debug.write,debug.player,debug.entity,debug.world,debug.block_entity,debug.menu,debug.provider,debug.chunk,debug.client,debug.network'
 $results=foreach($run in $runs){
  Write-Host "[Phase9C] starting $($run.Target)"
  Assert-True(-not[bool](Get-NetTCPConnection -LocalPort $run.Port -State Listen -ErrorAction SilentlyContinue))"port $($run.Port) occupied"
  $directory=(Resolve-Path $run.Dir).Path
  $arguments=@($run.Task,'--no-daemon');if($Offline){$arguments+='--offline'}
  $process=Start-Process '.\gradlew.bat' -ArgumentList $arguments -WorkingDirectory $root -RedirectStandardOutput(Join-Path $directory 'phase9c-five-target-stdout.log') -RedirectStandardError(Join-Path $directory 'phase9c-five-target-stderr.log') -PassThru -WindowStyle Hidden
  $tokenFile=Join-Path $directory 'minecraft-protocol\token';$deadline=(Get-Date).AddMinutes(6);$session=$null
  do{if(Test-Path -LiteralPath $tokenFile){$token=(Get-Content -LiteralPath $tokenFile -Raw).Trim();$auth=@{Authorization="Bearer $token"};try{$session=Invoke-RestMethod "http://127.0.0.1:$($run.Port)/v0/session" -Headers $auth -TimeoutSec 2}catch{$session=$null}};if($session-and$session.target-eq$run.Target-and($session.inWorld-or$session.screenClass-match'TitleScreen')){break};Start-Sleep -Seconds 2}while((Get-Date)-lt$deadline)
  Assert-True($session-and$session.target-eq$run.Target)"$($run.Target) not ready"
  $base="http://127.0.0.1:$($run.Port)"
  [void](Invoke-Json $base POST '/v0/control/emergency-release' $auth $null);$lease=Invoke-Json $base POST '/v0/control/acquire' $auth @{ttlMs=60000};$headers=@{Authorization="Bearer $token";'X-MCP-Control-Lease'=$lease.leaseId}
  try {
   if(-not$session.inWorld){
    $deadline=(Get-Date).AddSeconds(20);do{$tree=Invoke-Json $base GET '/v0/ui/tree' $auth $null;$single=@($tree.children|Where-Object -Property label -eq 'Singleplayer')[0];if($null-ne$single-and$single.active){break};Start-Sleep -Milliseconds 250}while((Get-Date)-lt$deadline)
    Assert-True($null-ne$single-and$single.active)"$($run.Target) title UI"
    for($attempt=0;$attempt-lt10;$attempt++){
     $session=Invoke-Json $base GET '/v0/session' $auth $null
     if($session.screenClass-match'SelectWorldScreen'){break}
     [void](Invoke-Json $base POST '/v0/ui/action' $headers @{action='click';holdMs=100;selector=@{role='button';label='Singleplayer'}})
     Start-Sleep -Milliseconds 500
    }
    Assert-True($session.screenClass-match'SelectWorldScreen')"$($run.Target) Select World"
    for($index=0;$index-lt20;$index++){$tree=Invoke-Json $base GET '/v0/ui/tree' $auth $null;$play=@($tree.children|Where-Object -Property label -eq 'Play Selected World')[0];if($null-ne$play-and$play.active){break};[void](Invoke-Json $base POST '/v0/ui/action' $headers @{action='click';holdMs=100;source='explicit_coordinate';coordinates=@{x=200;y=75}});Start-Sleep -Milliseconds 300}
    Assert-True($null-ne$play-and$play.active)"$($run.Target) world selection"
    [void](Invoke-Json $base POST '/v0/ui/action' $headers @{action='click';holdMs=100;selector=@{role='button';label='Play Selected World'}})
    $deadline=(Get-Date).AddSeconds(45);do{Start-Sleep -Milliseconds 250;$session=Invoke-Json $base GET '/v0/session' $auth $null}while(((-not$session.inWorld)-or$session.screenClass)-and(Get-Date)-lt$deadline)
   }
  } finally {try{[void](Invoke-Json $base POST '/v0/control/release' $headers $null)}catch{}}
  Assert-True($session.inWorld-and-not$session.screenClass)"$($run.Target) world"
  $phase9c=& '.\conformance\phase9\Invoke-Phase9CTargetConformance.ps1' -BaseUri $base -TokenFile $tokenFile -ExpectedTarget $run.Target
  Assert-True($phase9c.Result-eq'PASS')"$($run.Target) Phase 9C"
  $worldExit='NOT_REPRESENTATIVE'
  $maximumBatch='NOT_REPRESENTATIVE'
  if($run.WorldExit){Start-Sleep -Seconds 5;$maximum=& '.\conformance\phase9\Invoke-Phase9CMaximumBatchConformance.ps1' -BaseUri $base -TokenFile $tokenFile -ExpectedTarget $run.Target;Assert-True($maximum.Result-eq'PASS')'maximum batch representative';$maximumBatch='PASS';Start-Sleep -Seconds 5;$world=& '.\conformance\phase9\Invoke-Phase9CWorldExitConformance.ps1' -BaseUri $base -TokenFile $tokenFile -ExpectedTarget $run.Target;Assert-True($world.Result-eq'PASS')'world exit representative';$worldExit='PASS'}
  Start-Sleep -Seconds 5
  $phase9b=& '.\conformance\phase9\Invoke-Phase9BGate.ps1' -BaseUri $base -TokenFile $tokenFile -ExpectedTarget $run.Target -HasPhase9ADiagnostics:$run.Has9A -SkipBuild
  Assert-True($phase9b.Result-eq'PASS')"$($run.Target) Phase 9B regression"
  $v1=& '.\conformance\phase8\Invoke-Phase8TargetSmoke.ps1' -BaseUri $base -TokenFile $tokenFile -ExpectedTarget $run.Target -ExpectedBackend any -EnterWorld -RequireAuthoritative
  Assert-True($v1.Result-eq'PASS')"$($run.Target) V1 regression"
  [void](Invoke-Json $base POST '/v0/control/emergency-release' $auth $null);$quitLease=Invoke-Json $base POST '/v0/control/acquire' $auth @{ttlMs=15000};$quitHeaders=@{Authorization="Bearer $token";'X-MCP-Control-Lease'=$quitLease.leaseId}
  try {
   [void](Invoke-Json $base POST '/v0/input/key' $quitHeaders @{key=256;scanCode=1;action=1;modifiers=0})
   [void](Invoke-Json $base POST '/v0/input/key' $quitHeaders @{key=256;scanCode=1;action=0;modifiers=0})
   Start-Sleep -Milliseconds 400
   $tree=Invoke-Json $base GET '/v0/ui/tree' $auth $null
   $save=@($tree.children|Where-Object -Property label -eq 'Save and Quit to Title')[0]
   if($save){
    [void](Invoke-Json $base POST '/v0/ui/action' $quitHeaders @{action='click';holdMs=100;selector=@{role='button';label='Save and Quit to Title'}})
    Start-Sleep -Seconds 3
   }
   $tree=Invoke-Json $base GET '/v0/ui/tree' $auth $null
   $quit=@($tree.children|Where-Object -Property label -eq 'Quit Game')[0]
   if($quit){try{[void](Invoke-Json $base POST '/v0/ui/action' $quitHeaders @{action='click';holdMs=100;selector=@{role='button';label='Quit Game'}})}catch{}}
  } catch { }
  $deadline=(Get-Date).AddSeconds(30);do{Start-Sleep -Milliseconds 250;$listening=[bool](Get-NetTCPConnection -LocalPort $run.Port -State Listen -ErrorAction SilentlyContinue)}while($listening-and(Get-Date)-lt$deadline);Assert-True(-not$listening)"$($run.Target) shutdown"
  Write-Host "[Phase9C] completed $($run.Target)"
  [pscustomobject]@{Target=$run.Target;Player='PASS';Entity='PASS';Block='PASS';BlockEntity='PASS';Menu='PASS';Provider='PASS';Batch='PASS';Preconditions='PASS';Cancellation='PASS';Evidence='PASS';Chunk='PARTIAL';Client='PARTIAL';Network='PARTIAL';MaximumBatch=$maximumBatch;WorldExit=$worldExit;Phase9B='PASS';V1='PASS';Shutdown='PASS';Performance=$phase9c.Batch}
 }
 [pscustomobject]@{Result='PASS';Targets=$results.Count;Results=$results}
}
finally{Pop-Location}
