[CmdletBinding()]
param(
    [Parameter(Mandatory,ParameterSetName='Single')][ValidateSet('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')][string]$Target,
    [Parameter(Mandatory,ParameterSetName='Batch')][switch]$All,
    [ValidateRange(0,65535)][int]$RuntimePort=0,
    [string]$BatchId,
    [ValidateSet('all','editors','hud','hud-vitals','spectator','pointer','special-menus','world-selection','ui-debug','ui-debug-editors','ui-debug-consumers','ui-debug-scroll','ui-debug-hud','ui-debug-toast')][string]$Scenario='all',
    [switch]$KeepClient,
    [switch]$DebugRead,
    [switch]$DebugUi,
    [switch]$CaptureBaseline
)
$ErrorActionPreference='Stop'
if($Scenario-in@('ui-debug-scroll','ui-debug-hud','ui-debug-toast')-and-not$DebugUi){throw "$Scenario requires explicit -DebugUi authorization"}
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
. (Join-Path $PSScriptRoot '../../RuntimeConnection.ps1')
. (Join-Path $PSScriptRoot '../../InstanceProfiles.ps1')
$requestedRuntimePort=$RuntimePort
$batchSignal=Join-Path $repo 'validations/conformance-vanilla_ui/instance/batch-start.json'
function Test-UiReceiptOwner($Report,[int]$PortNumber,[int]$ClientProcessId,[DateTime]$LaunchStarted) {
    if($Report.runtimePort-ne$PortNumber-or$Report.launchAttestation.processId-ne$ClientProcessId){return $false}
    # ConvertFrom-Json may produce a UTC DateTime; reparsing its display text loses the offset.
    $time=if($Report.testedAt-is[DateTimeOffset]){$Report.testedAt}
        elseif($Report.testedAt-is[DateTime]){[DateTimeOffset]::new($Report.testedAt)}
        else{[DateTimeOffset]::Parse($Report.testedAt,[Globalization.CultureInfo]::InvariantCulture)}
    return $time.UtcDateTime-ge$LaunchStarted.ToUniversalTime()
}
if($All){
    if($Scenario-ne'all'){throw 'Batch evidence requires the complete scenario matrix'}
    if($KeepClient-or$CaptureBaseline){throw 'Batch testing requires automatic shutdown and existing immutable baselines'}
    $targets=@('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')
    if($RuntimePort-ne0-and$RuntimePort+4-gt65535){throw 'Batch Runtime port range exceeds 65535'}
    $batch=[guid]::NewGuid().ToString('N')
    foreach($t in $targets){if(-not(Test-Path (Join-Path $repo "validations/conformance-vanilla_ui/instance/$t/baseline/fixture-manifest.json"))){throw "Prepare a serial immutable baseline first: $t"}}
    if($RuntimePort-ne0){foreach($port in $RuntimePort..($RuntimePort+4)){if(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue){throw "Test port occupied: $port"}}}
    $jobs=@()
    $batchFailure=$null
    $ready=@()
    try{
        for($i=0;$i-lt$targets.Count;$i++){
            $workerPort=if($RuntimePort-eq0){0}else{$RuntimePort+$i}
            Write-Output "Starting $($targets[$i]): isolated fixture, requested port $workerPort (0=automatic), maximum heap 4 GiB"
            $jobs+=Start-Job -Name $targets[$i] -ScriptBlock {
                param($script,$target,$port,$batch,$debugRead,$debugUi)
                & $script -Target $target -RuntimePort $port -BatchId $batch -DebugRead:$debugRead -DebugUi:$debugUi
            } -ArgumentList $PSCommandPath,$targets[$i],$workerPort,$batch,$DebugRead.IsPresent,$DebugUi.IsPresent
        }
        $deadline=[DateTime]::UtcNow.AddMinutes(10)
        do{
            if(@($jobs|Where-Object State -In @('Failed','Stopped','Completed')).Count){throw 'A batch worker exited before the simultaneous-ready barrier'}
            $ready=@(foreach($t in $targets){$file=Join-Path $repo "validations/conformance-vanilla_ui/instance/$t/launch-attestation.json";if(Test-Path $file){try{$value=Get-Content $file -Raw|ConvertFrom-Json;if($value.batchId-eq$batch){$value}}catch{}}})
            if($ready.Count-eq5){break}
            Start-Sleep -Milliseconds 500
        }while([DateTime]::UtcNow-lt$deadline)
        if($ready.Count-ne5){throw 'Five clients did not reach the simultaneous-ready barrier'}
        foreach($r in $ready){if(-not(Get-Process -Id $r.processId -ErrorAction SilentlyContinue)){throw 'A ready client exited before batch release'}}
        [IO.File]::WriteAllText($batchSignal,(@{batchId=$batch;action='start'}|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
        Write-Output 'Five clients are simultaneously at the title screen; verified 4 GiB heaps; starting interface-only scenarios.'
        $deadline=[DateTime]::UtcNow.AddMinutes(12)
        while(@($jobs|Where-Object State -EQ 'Running').Count-and[DateTime]::UtcNow-lt$deadline){
            foreach($job in $jobs|Where-Object HasMoreData){Receive-Job $job -ErrorAction Continue|ForEach-Object{Write-Output "[$($job.Name)] $_"}}
            Start-Sleep -Milliseconds 500
        }
        foreach($job in $jobs){Receive-Job $job -ErrorAction Continue|ForEach-Object{Write-Output "[$($job.Name)] $_"}}
        if(@($jobs|Where-Object State -NE 'Completed').Count){throw 'One or more batch workers failed or did not cleanly finish'}
    }catch{$batchFailure=$_;Write-Warning $_}
    finally{
        [IO.File]::WriteAllText($batchSignal,(@{batchId=$batch;action='abort'}|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
        $result=Join-Path $repo 'validations/conformance-vanilla_ui/result/batch.json'
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($result))|Out-Null
        $outcome=@{status=$(if($batchFailure){'FAIL'}else{'PASS'});batchId=$batch;testedAt=[DateTime]::UtcNow.ToString('o');clients=$ready;workers=@($jobs|ForEach-Object{@{target=$_.Name;state=[string]$_.State}});persistentWriteCalls=0}
        [IO.File]::WriteAllText($result,($outcome|ConvertTo-Json -Depth 8)+"`n",[Text.UTF8Encoding]::new($false))
        # Allow blocked launch workers to see abort and close only their owned clients.
        $jobs|Wait-Job -Timeout 60|Out-Null
        $jobs|Where-Object State -NE 'Running'|Remove-Job
    }
    if($batchFailure){throw $batchFailure}
    exit
}
$root=[IO.Path]::GetFullPath((Join-Path $repo "validations/conformance-vanilla_ui/instance/$Target"))
$client=Join-Path $root 'client'
$baseline=Join-Path $root 'baseline'
[IO.Directory]::CreateDirectory($root)|Out-Null
$runnerLock=[IO.File]::Open((Join-Path $root 'runner.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try {
$nodeScript=Join-Path $PSScriptRoot 'run-vanilla-ui.mjs'
$owner='Mine-Craft-Protocol vanilla UI conformance'
function Assert-Within([string]$Path) {
    $full=[IO.Path]::GetFullPath($Path)
    if(-not$full.StartsWith($root+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw "Unsafe fixture path: $full"}
}
function Assert-Owned([string]$Path) {
    Assert-Within $Path
    $marker=Join-Path $Path 'fixture-owner.json'
    if(-not(Test-Path -LiteralPath $marker)){throw "Not an owned fixture: $Path"}
    $value=Get-Content -LiteralPath $marker -Raw|ConvertFrom-Json
    if($value.owner-ne$owner-or$value.target-ne$Target){throw 'Fixture ownership mismatch'}
}
function Write-Owner([string]$Path) {
    Assert-Within $Path
    if ((Test-Path -LiteralPath $Path) -and @(Get-ChildItem -LiteralPath $Path -Force).Count -gt 0) { Assert-Owned $Path }
    [IO.Directory]::CreateDirectory($Path)|Out-Null
    [IO.File]::WriteAllText((Join-Path $Path 'fixture-owner.json'),(@{owner=$owner;target=$Target}|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
}
function Port-Free { try{Assert-RuntimeInstanceStopped $client;return $true}catch{return $false} }
function Capture-Fixture {
    if(-not(Port-Free)){throw 'Client must be cleanly stopped before copying its fixture'}
    if(Test-Path -LiteralPath $baseline){throw 'Immutable baseline already exists; it will not be overwritten'}
    Assert-Within $baseline
    Write-Owner $client
    [IO.Directory]::CreateDirectory($baseline)|Out-Null
    foreach($entry in @('saves','resourcepacks','options.txt','servers.dat','fixture-owner.json')) {
        $source=Join-Path $client $entry
        if(Test-Path -LiteralPath $source){Copy-Item -LiteralPath $source -Destination (Join-Path $baseline $entry) -Recurse}
    }
    $files=@(Get-ChildItem -LiteralPath $baseline -File -Recurse|Sort-Object FullName|ForEach-Object {@{path=[IO.Path]::GetRelativePath($baseline,$_.FullName).Replace('\','/');sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}})
    [IO.File]::WriteAllText((Join-Path $baseline 'fixture-manifest.json'),(@{target=$Target;files=$files}|ConvertTo-Json -Depth 8)+"`n",[Text.UTF8Encoding]::new($false))
}
function Restore-Fixture {
    if(-not(Port-Free)){throw 'A Runtime already owns the test port; no second client will be started'}
    Assert-Owned $baseline
    $manifest=Get-Content (Join-Path $baseline 'fixture-manifest.json') -Raw|ConvertFrom-Json
    if ($manifest.target -ne $Target) { throw 'Baseline Target mismatch' }
    foreach ($file in $manifest.files) {
        if ([IO.Path]::IsPathRooted($file.path) -or $file.path.Split('/') -contains '..' -or $file.path.Contains('\') -or $file.sha256 -notmatch '^[0-9a-f]{64}$') { throw 'Invalid fixture manifest entry' }
    }
    foreach($file in $manifest.files){$path=Join-Path $baseline $file.path;Assert-Within $path;if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()-ne$file.sha256){throw 'Immutable fixture hash mismatch'}}
    if(Test-Path -LiteralPath $client){
        Assert-Owned $client
        Add-Type -AssemblyName Microsoft.VisualBasic
        try { [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($client,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin) }
        catch {
            # Retain data if Windows refuses the Recycle Bin; never fall back to permanent deletion.
            $retired = [IO.Path]::GetFullPath((Join-Path $root ('retired/client-' + [guid]::NewGuid().ToString('N'))))
            Assert-Within $retired
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($retired)) | Out-Null
            Move-Item -LiteralPath $client -Destination $retired
            Write-Warning "Recycle Bin unavailable; retained owned test copy at $retired"
        }
    }
    [IO.Directory]::CreateDirectory($client)|Out-Null
    foreach($file in $manifest.files){$source=Join-Path $baseline $file.path;$dest=Join-Path $client $file.path;Assert-Within $dest;[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest))|Out-Null;Copy-Item -LiteralPath $source -Destination $dest}
    foreach($file in $manifest.files){if((Get-FileHash -LiteralPath (Join-Path $client $file.path) -Algorithm SHA256).Hash.ToLowerInvariant()-ne$file.sha256){throw 'Restored fixture differs from baseline'}}
}
function Start-Client {
    if(-not(Port-Free)){throw 'A Runtime is already listening; refusing parallel launch'}
    Write-Owner $client
    $options=Join-Path $client 'options.txt'
    $optionsText=if(Test-Path -LiteralPath $options){[IO.File]::ReadAllText($options)}else{''}
    [IO.File]::WriteAllText($options,(Get-InstanceProfileOptions $optionsText automatic),[Text.UTF8Encoding]::new($false))
    $command="Set-Location -LiteralPath '$($repo.Replace("'","''"))'; & './gradlew.bat' ':version:$($Target):runValidationConformanceVanillaUiClient' '--offline' '--console=plain' '--init-script' 'components/validation_tools/conformance-vanilla_ui/task/vanilla-ui.init.gradle' '-PvanillaUiPort=$requestedRuntimePort'; exit `$LASTEXITCODE"
    if($DebugUi){$command="`$env:MCP_RUNTIME_SCOPES='read,ui,input,capture,event,diagnostics,control,command,fixture,debug,debug.ui.read,debug.world.read,debug.write,debug.ui'; "+$command}
    elseif($DebugRead){$command="`$env:MCP_RUNTIME_SCOPES='read,ui,input,capture,event,diagnostics,control,command,debug,debug.ui.read'; "+$command}
    $p=Start-Process -FilePath 'pwsh' -ArgumentList @('-NoProfile','-Command',$command) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'launch-out.log') -RedirectStandardError (Join-Path $root 'launch-error.log')
    $deadline=[DateTime]::UtcNow.AddMinutes(9)
    do{
        if($p.HasExited){throw "Client launcher exited: $($p.ExitCode)"}
        $token=Join-Path $client 'minecraft-protocol/token'
        if(Test-Path -LiteralPath $token){try{$connection=Get-RuntimeConnection $client $Target -NotBefore $p.StartTime.ToUniversalTime();$headers=$connection.Headers;$tree=Invoke-RestMethod "$($connection.BaseUri)/v0/ui/tree" -Headers $headers -TimeoutSec 2;if($tree.target-eq$Target-and$tree.screenClass-like'*TitleScreen'-and-not$tree.overlayIdentity){$script:RuntimePort=$connection.Port;return $p}}catch{}}
        Start-Sleep -Milliseconds 250
    }while([DateTime]::UtcNow-lt$deadline)
    throw 'Client did not reach a ready title screen'
}
function Run-Scenario($phase,[switch]$Keep) {
    $args=@($nodeScript,"--target=$Target","--phase=$phase","--port=$RuntimePort")
    if($phase-eq'test'){$args+="--scenario=$Scenario"}
    if($Keep){$args+='--keep-client=true'}
    & node @args
    if($LASTEXITCODE-ne0){throw "UI scenario failed: $phase"}
}
if($CaptureBaseline){Capture-Fixture;Write-Output 'Immutable baseline captured';exit}
if(-not(Test-Path -LiteralPath $baseline)){
    $process=Start-Client
    Run-Scenario prepare
    if(-not$process.WaitForExit(60000)){throw 'Client shutdown incomplete; baseline will not be captured'}
    if($process.ExitCode-ne0){throw 'Client did not shut down cleanly'}
    Capture-Fixture
}
Restore-Fixture
$process=Start-Client
try{
    $listener=Get-NetTCPConnection -State Listen -LocalPort $RuntimePort|Select-Object -First 1
    $java=Get-CimInstance Win32_Process -Filter "ProcessId=$($listener.OwningProcess)"
    $jcmd=Join-Path (Split-Path $java.ExecutablePath) 'jcmd.exe'
    $flags=& $jcmd $java.ProcessId VM.flags 2>&1|Out-String
    if($LASTEXITCODE-ne0-or$flags-notmatch '(?:^|\s)-XX:MaxHeapSize=4294967296(?:\s|$)'){throw 'Actual client maximum heap is not 4 GiB'}
    $attestation=@{target=$Target;port=$RuntimePort;processId=$java.ProcessId;maximumHeapBytes=4294967296;batchId=$BatchId;titleReadyAt=[DateTime]::UtcNow.ToString('o')}
    [IO.File]::WriteAllText((Join-Path $root 'launch-attestation.json'),($attestation|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
    if($BatchId){
        $deadline=[DateTime]::UtcNow.AddMinutes(10)
        do{
            if($process.HasExited){throw 'Client exited at the batch barrier'}
            $signal=$null
            if(Test-Path $batchSignal){try{$signal=Get-Content $batchSignal -Raw|ConvertFrom-Json}catch{}}
            if($signal.batchId-eq$BatchId){if($signal.action-eq'abort'){throw 'Batch aborted'};break}
            Start-Sleep -Milliseconds 500
        }while([DateTime]::UtcNow-lt$deadline)
        if($signal.batchId-ne$BatchId){throw 'Batch start barrier timed out'}
    }
    Run-Scenario test -Keep:$KeepClient
}finally{
    if(-not$KeepClient){
        $cleanupError=$null
        try{
            if(-not$process.WaitForExit(10000)){Run-Scenario quit;if(-not$process.WaitForExit(60000)){throw 'Client shutdown incomplete'}}
            if($process.ExitCode-ne0){throw 'Client launcher failed'}
            if(-not(Port-Free)){throw 'Test Runtime port was not released'}
        }catch{$cleanupError=$_;throw}
        finally{
            $suffix=if($Scenario-eq'all'){''}else{"-$Scenario"}
            $receipt=Join-Path $repo "validations/conformance-vanilla_ui/result/$Target$suffix.json"
            if(Test-Path -LiteralPath $receipt){
                $report=Get-Content -LiteralPath $receipt -Raw|ConvertFrom-Json
                # Never annotate a previous run after a failure before the Node runner wrote a receipt.
                if(Test-UiReceiptOwner $report $RuntimePort $java.ProcessId $process.StartTime){
                    $report|Add-Member -NotePropertyName cleanShutdown -NotePropertyValue (-not$cleanupError) -Force
                    $report|Add-Member -NotePropertyName processExitCode -NotePropertyValue $process.ExitCode -Force
                    $report|Add-Member -NotePropertyName endpointRemoved -NotePropertyValue (-not(Test-Path (Join-Path $client 'minecraft-protocol/endpoint.json'))) -Force
                    if($cleanupError){$report.status='FAIL';$report|Add-Member -NotePropertyName shutdownError -NotePropertyValue ([string]$cleanupError) -Force}
                    [IO.File]::WriteAllText($receipt,($report|ConvertTo-Json -Depth 40)+"`n",[Text.UTF8Encoding]::new($false))
                }
            }
        }
    }
}
Write-Output "UI conformance completed: $Target; same fixture restored; KeepClient=$KeepClient"
}finally{$runnerLock.Dispose()}
