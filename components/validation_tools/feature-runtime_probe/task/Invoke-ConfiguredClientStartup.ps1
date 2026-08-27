[CmdletBinding()]param([ValidateRange(60,900)][int]$StartupTimeoutSeconds=480,[ValidateRange(30,2400)][int]$MutexTimeoutSeconds=1200)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$targets=@([regex]::Matches((Get-Content (Join-Path $repo 'settings.gradle.kts') -Raw),'(?m)^\s*register\("([^"\r\n]+)"\)')|ForEach-Object {$_.Groups[1].Value})
if($targets.Count-ne5-or@($targets|Sort-Object -Unique).Count-ne5){throw 'Expected the five registered MCP Targets'}
$stamp=[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ')
$output=Join-Path $repo "validations/feature-runtime_probe/result"
[void][IO.Directory]::CreateDirectory($output)
$results=[Collections.Generic.List[object]]::new()
$mutex=[Threading.Mutex]::new($false,'Local\RavenTemplateClientSmoke')
try {
 foreach($target in $targets){
  $held=$false;$launcher=$null;$client=$null;$readyAt=$null;$start=$null
  $row=[ordered]@{target=$target;result='UNVERIFIED';command=":version:${target}:runValidationFeatureRuntimeProbeClient --offline --no-daemon";modId='minecraft_protocol_probe';mutex='Local\RavenTemplateClientSmoke';uiAutomation=$false;worldEntered=$false;shutdown='NOT_STARTED'}
  try {
   Write-Host "WAIT_MUTEX $target"
   try{$held=$mutex.WaitOne([TimeSpan]::FromSeconds($MutexTimeoutSeconds))}catch [Threading.AbandonedMutexException]{$held=$true}
   if(-not$held){throw 'GPU mutex timeout'}
   $start=[DateTime]::UtcNow;$row.startedUtc=$start.ToString('o')
   $stdout=Join-Path $output "$target.stdout.log";$stderr=Join-Path $output "$target.stderr.log"
   $latest=Join-Path $repo "validations/feature-runtime_probe/instance/$target/client/logs/latest.log";$row.log=$latest;$row.stdout=$stdout;$row.stderr=$stderr
   $startScript=Join-Path $PSScriptRoot 'Start-ConfiguredClient.ps1'
   $launcher=Start-Process -FilePath (Get-Process -Id $PID).Path -ArgumentList @('-NoProfile','-File',('"'+$startScript+'"'),'-Target',$target) -WorkingDirectory $repo -RedirectStandardOutput $stdout -RedirectStandardError $stderr -PassThru -WindowStyle Hidden
   $row.launcherPid=$launcher.Id;$owned=[Collections.Generic.HashSet[int]]::new();[void]$owned.Add($launcher.Id)
   Write-Host "START $target launcher=$($launcher.Id)"
   while(([DateTime]::UtcNow-$start).TotalSeconds-lt$StartupTimeoutSeconds){
    $processes=@(Get-CimInstance Win32_Process)
    do{$added=$false;foreach($p in $processes){if($owned.Contains([int]$p.ParentProcessId)-and$p.CreationDate.ToUniversalTime()-ge$start.AddSeconds(-1)){if($owned.Add([int]$p.ProcessId)){$added=$true}}}}while($added)
    if($null-eq$client){
     $candidates=@($processes|Where-Object {$owned.Contains([int]$_.ProcessId)-and$_.Name-match'^java(w)?\.exe$'-and$_.CommandLine-match'(?i)BootstrapLauncher|devlaunchinjector\.Main|neoforged\.devlaunch|KnotClient|net\.minecraft\.client\.main\.Main'-and$_.CommandLine-notmatch'GradleDaemon|GradleWorkerMain'})
     if($candidates.Count-gt1){throw 'Ambiguous client process identity'}
     if($candidates.Count-eq1){$client=$candidates[0];$row.clientPid=$client.ProcessId;$row.clientParentPid=$client.ParentProcessId;$row.clientCreatedUtc=$client.CreationDate.ToUniversalTime().ToString('o');$row.javaExecutable=$client.ExecutablePath;$row.commandHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($client.CommandLine)));$release=Join-Path (Split-Path (Split-Path $client.ExecutablePath)) 'release';$row.javaVersion=((Get-Content $release|Where-Object {$_-match'^JAVA_VERSION='})-split'=',2)[1].Trim('"');Write-Host "CLIENT $target pid=$($client.ProcessId) java=$($row.javaVersion)"}
    }
    $alive=$null-ne$client-and@($processes|Where-Object ProcessId -EQ $client.ProcessId).Count-eq1
    if(Test-Path -LiteralPath $latest){
     $info=Get-Item -LiteralPath $latest
     if($info.LastWriteTimeUtc-ge$start){
      $text=Get-Content -LiteralPath $latest -Raw
      $mod=[regex]::Match($text,'(?m)^.*Mine-Craft-Protocol Phase 8 .*V1 Runtime loaded.*$')
      $render=[regex]::Match($text,'(?m)^.*Created:.*textures/atlas/shulker_boxes\.png.*$')
      if($mod.Success){$row.modLoadedEvidence=$mod.Value.Trim()}
      if($render.Success){$row.renderReadyEvidence=$render.Value.Trim()}
      if($alive-and$mod.Success-and$render.Success){if($null-eq$readyAt){$readyAt=[DateTime]::UtcNow};if(([DateTime]::UtcNow-$readyAt).TotalSeconds-ge10){$row.result='PASS';$row.evidenceBoundary='MOD_INITIALIZER_AND_RENDER_RESOURCE_READY_PLUS_10S_ALIVE_NOT_VISUAL_MENU_ACCEPTANCE';break}}
     }
    }
    $launcher.Refresh()
    if($launcher.HasExited){$row.result='FAIL';throw "Native client launcher exited before verified readiness: $($launcher.ExitCode)"}
    Start-Sleep -Seconds 1
   }
   if($row.result-ne'PASS'){throw 'Bounded startup verification timed out'}
  } catch {$row.error=$_.Exception.Message;if($row.result-ne'PASS'){$row.result='FAIL'};Write-Host "STARTUP_RESULT $target $($row.result): $($row.error)"}
  finally {
   try {
    if($null-ne$client){
     $current=Get-CimInstance Win32_Process -Filter ("ProcessId="+$client.ProcessId)
     if($current){
      if($current.CreationDate-ne$client.CreationDate-or$current.ExecutablePath-ne$client.ExecutablePath-or$current.CommandLine-ne$client.CommandLine){throw 'PID identity changed; refuse termination'}
      # No supported non-UI quit command exists for this native client. End only
      # the exact fresh descendant we started, never another Java/Gradle process.
      Stop-Process -Id $client.ProcessId -Force
      $deadline=[DateTime]::UtcNow.AddSeconds(30)
      while((Get-Process -Id $client.ProcessId -ErrorAction SilentlyContinue)-and[DateTime]::UtcNow-lt$deadline){Start-Sleep -Milliseconds 250}
      if(Get-Process -Id $client.ProcessId -ErrorAction SilentlyContinue){throw 'Client failed to exit'}
      $row.shutdown='CONTROLLED_TERMINATION_EXACT_CLIENT_PID'
     }else{$row.shutdown='CLIENT_ALREADY_EXITED'}
     $row.clientExitConfirmed=$true
    }
    if($launcher){$exited=$launcher.WaitForExit(60000);$row.launcherExitConfirmed=$exited;if($exited){$row.launcherExitCode=$launcher.ExitCode};if(-not$exited){$row.cleanupIssue='Launcher not yet exited; not forcibly terminated';$row.result='FAIL'}}
   }catch{$row.cleanupIssue=$_.Exception.Message;$row.result='FAIL'}
   finally{if($held){$mutex.ReleaseMutex()}}
   $row.finishedUtc=[DateTime]::UtcNow.ToString('o');$results.Add([pscustomobject]$row)
   $results|ConvertTo-Json -Depth 8|Set-Content (Join-Path $output 'matrix.json') -Encoding utf8
   Write-Host "DONE $target $($row.result) shutdown=$($row.shutdown)"
  }
  if($row.Contains('cleanupIssue')){Write-Warning 'Cleanup not proven; do not launch another client';break}
 }
}finally{$mutex.Dispose()}
[pscustomobject]@{Result=if($results.Count-eq$targets.Count-and@($results|Where-Object {$_.result-ne'PASS'-or-not$_.clientExitConfirmed-or-not$_.launcherExitConfirmed}).Count-eq0){'PASS'}else{'PARTIAL'};Targets=$targets;Evidence=(Join-Path $output 'matrix.json');RuntimeKind='NATIVE_CLIENT_STARTUP_NO_UI_OR_WORLD';Rows=$results}
