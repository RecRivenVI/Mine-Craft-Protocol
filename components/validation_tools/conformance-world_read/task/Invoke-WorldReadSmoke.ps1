[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')][string]$Target,
    [ValidateSet('conformance-world_read','migration-runtime_core')][string]$Validation='conformance-world_read',
    [ValidateSet('full','input-preconditions','player-command','target-lifecycle','command-configuration','test-management','movement','movement-branches','special-movement','modern-mounts','world-seats','item-utility','placement-branches','block-bridge','surface-use','station-use','combat-use','entity-branches','entity-parts','entity-tail','entity-social','range-branches','range-tail','lifecycle','block-state','consumer','rate-limit','command-feedback','mode-collaboration','resource-version','command-discovery-batch','server-completion','interaction-baseline','continuous-input','continuous-performance','input-context','region-stream','world-placement-receipts','first-camera','block-entity-selector','block-entity-pages','world-debug-items','world-debug-state','world-debug-entities','world-debug-environment','world-debug-relations','world-debug-loading','world-debug-boundary','world-debug-transfer','world-debug-providers','world-region-functional','world-user-catalog','command-collaboration')][string]$Scenario='full')
$ErrorActionPreference='Stop'
if($Scenario-ne'full'-and$Validation-ne'conformance-world_read'){throw 'Targeted input checks use only the world-read validation instance'}
if($Scenario-eq'test-management'-and$Target-in@('1.20.1-forge','1.21.1-neoforge')){throw 'These pinned native versions do not provide Test management blocks or packets'}
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
. (Join-Path $PSScriptRoot '../../RuntimeConnection.ps1')
. (Join-Path $PSScriptRoot '../../InstanceProfiles.ps1')
. (Join-Path $PSScriptRoot 'WorldReadFixture.ps1')
function Test-WorldReceiptOwner($Report,$Connection) {
    return $null-ne$Connection-and$Report.runtimeConnection.runtimeInstanceId-eq$Connection.RuntimeInstanceId-and$Report.runtimeConnection.pid-eq$Connection.ProcessId-and$Report.runtimeConnection.port-eq$Connection.Port
}
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
    $fixture=Restore-WorldReadFixture $root $Target $facts -AllowLoaderUpdate
    $fixtureArgs+="--fixture-hash=$($fixture.baselineSha256)"
    Write-Output "Restored immutable $Target world fixture: $($fixture.baselineSha256)"
}else{Write-Output 'Bootstrap preparation only; the next run captures the clean passing world before restoring it'}
$runName=if($Validation-eq'migration-runtime_core'){'runValidationMigrationRuntimeCoreClient'}else{'runValidationConformanceWorldReadClient'}
$optionsText=if(Test-Path -LiteralPath $options){[IO.File]::ReadAllText($options)}else{''}
[IO.File]::WriteAllText($options,(Get-InstanceProfileOptions $optionsText automatic),[Text.UTF8Encoding]::new($false))
$resourceScopes=if($Scenario-in@('resource-version','world-debug-transfer','world-debug-items','world-debug-state','world-debug-entities','world-debug-environment','world-debug-relations')){',debug.entity,debug.block_entity,debug.menu'}else{''}
if($Scenario-in@('command-collaboration','world-debug-items','world-debug-boundary','world-debug-transfer','world-debug-providers','world-user-catalog','world-seats')){$resourceScopes+=',fixture'}
$backgroundEnvironment=if($Scenario-eq'first-camera'){"`$env:MCP_VALIDATION_BACKGROUND_WINDOW='true'; "}else{''}
$backgroundFmlPath=$null
if($Scenario-eq'first-camera'-and$Target-eq'1.21.1-neoforge'){
    # The Loader splash normally hands over an already-focused window. This test
    # creates its own ordinary GLFW window without requesting focus instead.
    $backgroundFmlPath=Join-Path $client 'config/fml.toml'
    $backgroundFmlExisted=Test-Path -LiteralPath $backgroundFmlPath
    $backgroundFmlOriginal=if($backgroundFmlExisted){[IO.File]::ReadAllText($backgroundFmlPath)}else{''}
    $backgroundFmlText=if($backgroundFmlOriginal-match'(?m)^earlyWindowControl\s*='){
        [regex]::Replace($backgroundFmlOriginal,'(?m)^earlyWindowControl\s*=.*$','earlyWindowControl = false')
    }else{$backgroundFmlOriginal+"`nearlyWindowControl = false`n"}
    [IO.Directory]::CreateDirectory((Split-Path $backgroundFmlPath))|Out-Null
    [IO.File]::WriteAllText($backgroundFmlPath,$backgroundFmlText,[Text.UTF8Encoding]::new($false))
}
$command="Set-Location -LiteralPath '$($repo.Replace("'","''"))'; $backgroundEnvironment`$env:MCP_RUNTIME_SCOPES='read,ui,input,capture,event,diagnostics,control,command,debug,debug.world.read,debug.write,debug.world,debug.player,debug.ui,debug.ui.read,debug.provider$resourceScopes'; & './gradlew.bat' ':version:$($Target):$runName' '--offline' '--console=plain'; exit `$LASTEXITCODE"
if($Scenario-eq'command-collaboration'){$command=$command.Replace("'--console=plain';", "'--console=plain' '--init-script' 'components/validation_tools/conformance-world_read/task/command-collaboration.init.gradle';")}
if($Scenario-eq'world-debug-items'){$command=$command.Replace("'--console=plain';", "'--console=plain' '--init-script' 'components/validation_tools/conformance-world_read/task/world-debug-items.init.gradle';")}
if($Scenario-eq'world-debug-boundary'){$command=$command.Replace("'--console=plain';", "'--console=plain' '--init-script' 'components/validation_tools/conformance-world_read/task/world-debug-boundary.init.gradle';")}
if($Scenario-eq'world-debug-transfer'){$command=$command.Replace("'--console=plain';", "'--console=plain' '--init-script' 'components/validation_tools/conformance-world_read/task/world-debug-transfer.init.gradle';")}
if($Scenario-eq'world-user-catalog'){$command=$command.Replace("'--console=plain';", "'--console=plain' '--init-script' 'components/validation_tools/conformance-world_read/task/world-user-catalog.init.gradle';")}
if($Scenario-eq'world-seats'){$command=$command.Replace("'--console=plain';", "'--console=plain' '--init-script' 'components/validation_tools/conformance-world_read/task/world-seats.init.gradle';")}
Write-Output "World read smoke: $Target, single owned muted client, 4 GiB maximum heap"
$process=Start-Process pwsh -ArgumentList @('-NoProfile','-Command',$command) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'launch-out.log') -RedirectStandardError (Join-Path $root 'launch-error.log')
$launchStarted=$process.StartTime.ToUniversalTime()
$headers=$null
$connection=$null
$failure=$null
$shutdownError=$null
$partnerProcess=$null
$partnerConnection=$null
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
    if($Scenario-eq'world-seats'){
        $partner=Join-Path $root 'client-multiplayer'
        Assert-RuntimeInstanceStopped $partner
        $partnerOwner=Join-Path $partner 'world-seat-owner.json'
        if(Test-Path $partnerOwner){$marker=Get-Content $partnerOwner -Raw|ConvertFrom-Json;if($marker.target-ne$Target-or$marker.owner-ne'world-seat-comparison'){throw 'Partner fixture ownership mismatch'}}
        elseif((Test-Path $partner)-and@(Get-ChildItem -LiteralPath $partner -Force).Count){throw 'Unowned partner instance will not be modified'}
        [IO.Directory]::CreateDirectory($partner)|Out-Null
        [IO.File]::WriteAllText($partnerOwner,(@{target=$Target;owner='world-seat-comparison'}|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
        $partnerOptions=Join-Path $partner 'options.txt'
        $partnerText=if(Test-Path $partnerOptions){[IO.File]::ReadAllText($partnerOptions)}else{''}
        [IO.File]::WriteAllText($partnerOptions,(Get-InstanceProfileOptions $partnerText automatic),[Text.UTF8Encoding]::new($false))
        $partnerCommand="Set-Location -LiteralPath '$($repo.Replace("'","''"))'; `$env:MCP_RUNTIME_SCOPES='read,ui,input,control,event,diagnostics'; & './gradlew.bat' ':version:$($Target):runValidationConformanceWorldReadClientMultiplayer' '--offline' '--console=plain'; exit `$LASTEXITCODE"
        Write-Output 'Seat comparison: two real owned clients of this Target, each muted, 1920x1080 and 4 GiB'
        $partnerProcess=Start-Process pwsh -ArgumentList @('-NoProfile','-Command',$partnerCommand) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'seat-partner-out.log') -RedirectStandardError (Join-Path $root 'seat-partner-error.log')
        $partnerConnection=Wait-RuntimeConnection $partner $Target -NotBefore $partnerProcess.StartTime.ToUniversalTime() -Launcher $partnerProcess
        $partnerJava=Get-CimInstance Win32_Process -Filter "ProcessId=$($partnerConnection.ProcessId)"
        if($partnerJava.CommandLine-notmatch[regex]::Escape($repo)){throw 'Partner is not this repository Runtime'}
        $partnerFlags=& (Join-Path (Split-Path $partnerJava.ExecutablePath) 'jcmd.exe') $partnerConnection.ProcessId VM.flags 2>&1|Out-String
        if($LASTEXITCODE-ne0-or$partnerFlags-notmatch '(?:^|\s)-XX:MaxHeapSize=4294967296(?:\s|$)'){throw 'Partner maximum heap is not 4 GiB'}
    }
    $entry=switch($Scenario){'full'{'world-read-smoke.mjs'}'movement'{'world-read-smoke.mjs'}'block-state'{'world-read-smoke.mjs'}'consumer'{'world-read-smoke.mjs'}'rate-limit'{'world-read-smoke.mjs'}'command-feedback'{'world-read-smoke.mjs'}'mode-collaboration'{'world-read-smoke.mjs'}'resource-version'{'world-read-smoke.mjs'}'command-discovery-batch'{'world-read-smoke.mjs'}'server-completion'{'world-read-smoke.mjs'}'interaction-baseline'{'world-read-smoke.mjs'}'input-preconditions'{'input-precondition-smoke.mjs'}'player-command'{'player-command-smoke.mjs'}'target-lifecycle'{'target-lifecycle-smoke.mjs'}'command-configuration'{'command-configuration-smoke.mjs'}'test-management'{'test-management-smoke.mjs'}}
    if($Scenario-in@('movement-branches','special-movement','modern-mounts','world-seats','item-utility','placement-branches','block-bridge','surface-use','station-use','combat-use','entity-branches','entity-parts','entity-tail','entity-social','range-branches','range-tail','lifecycle','continuous-input','continuous-performance','input-context','region-stream','world-placement-receipts','first-camera','block-entity-selector','block-entity-pages','world-debug-items','world-debug-state','world-debug-entities','world-debug-environment','world-debug-relations','world-debug-loading','world-debug-boundary','world-debug-transfer','world-debug-providers','world-region-functional','world-user-catalog','command-collaboration')){$entry='world-read-smoke.mjs'}
    if($Scenario-in@('movement','movement-branches','special-movement','modern-mounts','world-seats','item-utility','placement-branches','block-bridge','surface-use','station-use','combat-use','entity-branches','entity-parts','entity-tail','entity-social','range-branches','range-tail','lifecycle','block-state','consumer','rate-limit','command-feedback','mode-collaboration','resource-version','command-discovery-batch','server-completion','interaction-baseline','continuous-input','continuous-performance','input-context','region-stream','world-placement-receipts','first-camera','block-entity-selector','block-entity-pages','world-debug-items','world-debug-state','world-debug-entities','world-debug-environment','world-debug-relations','world-debug-loading','world-debug-boundary','world-debug-transfer','world-debug-providers','world-region-functional','world-user-catalog','command-collaboration')){$fixtureArgs+="--scenario=$Scenario"}
    & node (Join-Path $PSScriptRoot $entry) "--target=$Target" "--validation=$Validation" @fixtureArgs
    if($LASTEXITCODE-ne0){throw 'World read Native smoke failed'}
}catch{$failure=$_}
finally{
    if($partnerProcess){
        if(-not$partnerProcess.WaitForExit(1000)){
            try{& node (Join-Path $PSScriptRoot 'world-read-smoke.mjs') "--target=$Target" '--role=client-multiplayer' '--shutdown-only=true'}catch{}
        }
        if(-not$partnerProcess.WaitForExit(60000)){$shutdownError='Owned seat partner still running; retained for diagnosis'}
        elseif($partnerProcess.ExitCode-ne0){$shutdownError="Seat partner launcher exit code $($partnerProcess.ExitCode)"}
        elseif($partnerConnection-and(Get-NetTCPConnection -State Listen -LocalPort $partnerConnection.Port -ErrorAction SilentlyContinue)){$shutdownError='Seat partner Runtime port remains'}
        elseif(Test-Path (Join-Path $root 'client-multiplayer/minecraft-protocol/endpoint.json')){$shutdownError='Seat partner endpoint remains'}
    }
    if(-not$process.WaitForExit(60000)){
        # Ask the project Runtime to exit; do not kill another Java process or automate the desktop.
        if($headers){try{& node (Join-Path $PSScriptRoot 'world-read-smoke.mjs') "--target=$Target" "--validation=$Validation" '--shutdown-only=true'}catch{}}
    }
    if(-not$process.WaitForExit(30000)){$shutdownError="Owned launcher still running: $($process.Id); retained for safe diagnosis"}
    elseif($process.ExitCode-ne0){$shutdownError="Owned launcher exit code $($process.ExitCode)"}
    elseif($port-and(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue)){$shutdownError='Owned Runtime port not released'}
    elseif(Test-Path (Join-Path $client 'minecraft-protocol/endpoint.json')){$shutdownError='Owned discovery record remains after shutdown'}
    if($shutdownError){$failure=$shutdownError}
    if($backgroundFmlPath-and-not$shutdownError){
        if($backgroundFmlExisted){[IO.File]::WriteAllText($backgroundFmlPath,$backgroundFmlOriginal,[Text.UTF8Encoding]::new($false))}
        else{Add-Type -AssemblyName Microsoft.VisualBasic;[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($backgroundFmlPath,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)}
    }
    $suffix=if($Scenario-eq'full'){''}else{"-$Scenario"}
    $result=Join-Path $repo "validations/$Validation/result/$Target$suffix.json"
    if(Test-Path $result){
        $report=Get-Content $result -Raw|ConvertFrom-Json
        if(Test-WorldReceiptOwner $report $connection){
            $report|Add-Member -NotePropertyName cleanShutdown -NotePropertyValue (-not$shutdownError) -Force
            $report|Add-Member -NotePropertyName maximumHeapBytes -NotePropertyValue 4294967296 -Force
            if($partnerConnection){$report|Add-Member -NotePropertyName seatPartner -NotePropertyValue @{runtimeInstanceId=$partnerConnection.RuntimeInstanceId;pid=$partnerConnection.ProcessId;port=$partnerConnection.Port;maximumHeapBytes=4294967296;cleanShutdown=(-not$shutdownError)} -Force}
            if($failure){$report.status='FAIL'}
            if($shutdownError){$report|Add-Member -NotePropertyName shutdownError -NotePropertyValue $shutdownError -Force}
            [IO.File]::WriteAllText($result,($report|ConvertTo-Json -Depth 20)+"`n",[Text.UTF8Encoding]::new($false))
        }
    }
}
if($failure){throw $failure}
Write-Output "PASS: $Target $Scenario Native smoke, clean shutdown, port released"
}finally{$runnerLock.Dispose()}
