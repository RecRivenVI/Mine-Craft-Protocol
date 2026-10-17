[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')][string]$Target,
    [ValidateSet('conformance-world_read','migration-runtime_core')][string]$Validation='conformance-world_read',
    [ValidateSet('full','input-preconditions','player-command','target-lifecycle','command-configuration','test-management','movement','block-state','consumer','rate-limit','command-feedback')][string]$Scenario='full')
$ErrorActionPreference='Stop'
if($Scenario-ne'full'-and$Validation-ne'conformance-world_read'){throw 'Targeted input checks use only the world-read validation instance'}
if($Scenario-eq'test-management'-and$Target-in@('1.20.1-forge','1.21.1-neoforge')){throw 'These pinned native versions do not provide Test management blocks or packets'}
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
. (Join-Path $PSScriptRoot '../../RuntimeConnection.ps1')
. (Join-Path $PSScriptRoot '../../InstanceProfiles.ps1')
. (Join-Path $PSScriptRoot 'WorldReadFixture.ps1')
$root=Join-Path $repo "validations/$Validation/instance/$Target"
$client=Join-Path $root 'client'
$port=$null
[IO.Directory]::CreateDirectory($client)|Out-Null
$runnerLock=[IO.File]::Open((Join-Path $root 'runner.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try {
Assert-RuntimeInstanceStopped $client
$owner=Join-Path $client 'world-read-owner.json'
if(Test-Path $owner){Assert-WorldReadFixtureOwner $client $Target}
elseif(@(Get-ChildItem -LiteralPath $client -Force).Count){throw 'Unowned test instance will not be overwritten'}
else{[IO.File]::WriteAllText($owner,(@{target=$Target;owner='world-read-classification'}|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))}
$options=Join-Path $client 'options.txt'
$facts=Join-Path $repo "versions/$Target/target.properties"
$previous=Join-Path $repo "validations/$Validation/result/$Target.json"
if(Test-Path (Join-Path $root 'baseline.preparing')){throw 'Incomplete world baseline capture retained; refusing to overwrite it'}
if(-not(Test-Path (Join-Path $root 'baseline'))-and(Test-Path $previous)){
    $prepared=Get-Content $previous -Raw|ConvertFrom-Json
    if($prepared.status-eq'PASS'-and$prepared.cleanShutdown-and-not$prepared.sourceChangedDuringRun){Save-WorldReadFixture $root $Target $facts $previous|Out-Null}
}
$fixtureArgs=@()
if($Scenario-ne'full'-and-not(Test-Path (Join-Path $root 'baseline'))){throw 'Targeted checks require an existing immutable baseline; run the full bootstrap preparation first'}
if(Test-Path (Join-Path $root 'baseline')){
    $fixture=Restore-WorldReadFixture $root $Target $facts
    $fixtureArgs+="--fixture-hash=$($fixture.baselineSha256)"
    Write-Output "Restored immutable $Target world fixture: $($fixture.baselineSha256)"
}else{Write-Output 'Bootstrap preparation only; the next run captures the clean passing world before restoring it'}
$runName=if($Validation-eq'migration-runtime_core'){'runValidationMigrationRuntimeCoreClient'}else{'runValidationConformanceWorldReadClient'}
$optionsText=if(Test-Path -LiteralPath $options){[IO.File]::ReadAllText($options)}else{''}
[IO.File]::WriteAllText($options,(Get-InstanceProfileOptions $optionsText automatic),[Text.UTF8Encoding]::new($false))
$command="Set-Location -LiteralPath '$($repo.Replace("'","''"))'; `$env:MCP_RUNTIME_SCOPES='read,ui,input,capture,event,diagnostics,control,command,debug,debug.world.read,debug.write,debug.world,debug.player,debug.ui,debug.ui.read,debug.provider'; & './gradlew.bat' ':version:$($Target):$runName' '--offline' '--console=plain'; exit `$LASTEXITCODE"
Write-Output "World read smoke: $Target, single owned muted client, 4 GiB maximum heap"
$process=Start-Process pwsh -ArgumentList @('-NoProfile','-Command',$command) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'launch-out.log') -RedirectStandardError (Join-Path $root 'launch-error.log')
$launchStarted=$process.StartTime.ToUniversalTime()
$headers=$null
$failure=$null
$shutdownError=$null
try{
    $deadline=[DateTime]::UtcNow.AddMinutes(8)
    do{
        if($process.HasExited){throw "Launcher exited before Runtime readiness: $($process.ExitCode)"}
        $token=Join-Path $client 'minecraft-protocol/token'
        if(Test-Path $token){try{
            $connection=Get-RuntimeConnection -InstanceDirectory $client -ExpectedTarget $Target -NotBefore $launchStarted
            $headers=$connection.Headers
            $port=$connection.Port
            $tree=Invoke-RestMethod "http://127.0.0.1:$port/v0/ui/tree" -Headers $headers -TimeoutSec 2
            if($tree.target-eq$Target-and$tree.screenClass-like'*TitleScreen'-and-not$tree.overlayIdentity){break}
            if($tree.target-eq$Target-and$tree.screenClass-like'*AccessibilityOnboardingScreen'){
                $continue=@($tree.children|Where-Object {$_.active-ne$false-and($_.translationKey-eq'gui.continue'-or$_.label-eq'Continue')})|Select-Object -First 1
                if($continue){
                    $control=Invoke-RestMethod "http://127.0.0.1:$port/v0/control/mode" -Headers $headers -TimeoutSec 3
                    if($control.reconsentRequired-ne$false-or-not$control.modeVersion){throw 'Automatic onboarding cannot bypass a human override or missing control state'}
                    $startupLease=Invoke-RestMethod "http://127.0.0.1:$port/v0/control/acquire" -Method Post -Headers $headers -ContentType 'application/json' -Body (@{ttlMs=15000;expectedModeVersion=$control.modeVersion}|ConvertTo-Json -Depth 5)
                    $startupHeaders=$headers.Clone();$startupHeaders['X-MCP-Control-Lease']=$startupLease.leaseId
                    try{Invoke-RestMethod "http://127.0.0.1:$port/v0/ui/action" -Method Post -Headers $startupHeaders -ContentType 'application/json' -Body (@{selector=@{nodeId=$continue.nodeId};action='click'}|ConvertTo-Json -Depth 5)|Out-Null}
                    finally{Invoke-RestMethod "http://127.0.0.1:$port/v0/control/release" -Method Post -Headers $startupHeaders|Out-Null}
                }
            }
        }catch{}}
        Start-Sleep -Milliseconds 300
    }while([DateTime]::UtcNow-lt$deadline)
    if(-not$tree-or$tree.target-ne$Target-or$tree.screenClass-notlike'*TitleScreen'){throw 'Runtime title readiness timeout'}
    if($tree.windowWidth-ne1920-or$tree.windowHeight-ne1080){throw 'Automatic client must actually report 1920x1080'}
    $listener=Get-NetTCPConnection -State Listen -LocalPort $port|Select-Object -First 1
    $java=Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    if($java.CommandLine-notmatch[regex]::Escape($repo)){throw 'Port owner is not this repository Runtime'}
    $jcmd=Join-Path (Split-Path $java.ExecutablePath) 'jcmd.exe'
    $flags=& $jcmd $java.ProcessId VM.flags 2>&1|Out-String
    if($LASTEXITCODE-ne0-or$flags-notmatch '(?:^|\s)-XX:MaxHeapSize=4294967296(?:\s|$)'){throw 'Actual client maximum heap is not 4 GiB'}
    if($Validation-eq'migration-runtime_core'){
        $classes=& $jcmd $java.ProcessId VM.class_hierarchy io.github.recrivenvi.minecraftprotocol.core.ProbeTransport 2>&1|Out-String
        if($LASTEXITCODE-ne0-or$classes-notmatch'io.github.recrivenvi.minecraftprotocol.core.ProbeTransport'){throw 'Shared product transport not loaded by actual game Runtime'}
        [IO.File]::WriteAllText((Join-Path $root 'runtime-core-class-loader.log'),$classes,[Text.UTF8Encoding]::new($false))
    }
    $entry=switch($Scenario){'full'{'world-read-smoke.mjs'}'movement'{'world-read-smoke.mjs'}'block-state'{'world-read-smoke.mjs'}'consumer'{'world-read-smoke.mjs'}'rate-limit'{'world-read-smoke.mjs'}'command-feedback'{'world-read-smoke.mjs'}'input-preconditions'{'input-precondition-smoke.mjs'}'player-command'{'player-command-smoke.mjs'}'target-lifecycle'{'target-lifecycle-smoke.mjs'}'command-configuration'{'command-configuration-smoke.mjs'}'test-management'{'test-management-smoke.mjs'}}
    if($Scenario-in@('movement','block-state','consumer','rate-limit','command-feedback')){$fixtureArgs+="--scenario=$Scenario"}
    & node (Join-Path $PSScriptRoot $entry) "--target=$Target" "--validation=$Validation" @fixtureArgs
    if($LASTEXITCODE-ne0){throw 'World read Native smoke failed'}
}catch{$failure=$_}
finally{
    if(-not$process.WaitForExit(60000)){
        # Ask the project Runtime to exit; do not kill another Java process or automate the desktop.
        if($headers){try{& node (Join-Path $PSScriptRoot 'world-read-smoke.mjs') "--target=$Target" "--validation=$Validation" '--shutdown-only=true'}catch{}}
    }
    if(-not$process.WaitForExit(30000)){$shutdownError="Owned launcher still running: $($process.Id); retained for safe diagnosis"}
    elseif($process.ExitCode-ne0){$shutdownError="Owned launcher exit code $($process.ExitCode)"}
    elseif($port-and(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)){$shutdownError='Owned Runtime port not released'}
    elseif(Test-Path (Join-Path $client 'minecraft-protocol/endpoint.json')){$shutdownError='Owned discovery record remains after shutdown'}
    if($shutdownError){$failure=$shutdownError}
    $suffix=if($Scenario-eq'full'){''}else{"-$Scenario"}
    $result=Join-Path $repo "validations/$Validation/result/$Target$suffix.json"
    if(Test-Path $result){$report=Get-Content $result -Raw|ConvertFrom-Json;$report|Add-Member -NotePropertyName cleanShutdown -NotePropertyValue (-not$shutdownError) -Force;$report|Add-Member -NotePropertyName maximumHeapBytes -NotePropertyValue 4294967296 -Force;if($failure){$report.status='FAIL'};if($shutdownError){$report|Add-Member -NotePropertyName shutdownError -NotePropertyValue $shutdownError -Force};[IO.File]::WriteAllText($result,($report|ConvertTo-Json -Depth 20)+"`n",[Text.UTF8Encoding]::new($false))}
}
if($failure){throw $failure}
Write-Output "PASS: $Target $Scenario Native smoke, clean shutdown, port released"
}finally{$runnerLock.Dispose()}
