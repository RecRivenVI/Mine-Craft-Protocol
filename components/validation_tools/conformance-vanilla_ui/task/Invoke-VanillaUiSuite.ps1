[CmdletBinding()]
param(
    [Parameter(Mandatory,ParameterSetName='Single')][ValidateSet('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')][string]$Target,
    [Parameter(Mandatory,ParameterSetName='Batch')][switch]$All,
    [ValidateRange(1024,65435)][int]$RuntimePort=28591,
    [string]$BatchId,
    [ValidateSet('all','editors','hud','spectator')][string]$Scenario='all',
    [switch]$KeepClient,
    [switch]$DebugRead,
    [switch]$DebugUi,
    [switch]$CaptureBaseline
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$batchSignal=Join-Path $repo 'validations/conformance-vanilla_ui/instance/batch-start.json'
if($All){
    if($Scenario-ne'all'){throw 'Batch evidence requires the complete scenario matrix'}
    if($KeepClient-or$CaptureBaseline){throw 'Batch testing requires automatic shutdown and existing immutable baselines'}
    $targets=@('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')
    if($RuntimePort+104-gt65535){throw 'Batch Runtime and loopback fixture port range exceeds 65535'}
    $batch=[guid]::NewGuid().ToString('N')
    foreach($t in $targets){if(-not(Test-Path (Join-Path $repo "validations/conformance-vanilla_ui/instance/$t/baseline/fixture-manifest.json"))){throw "Prepare a serial immutable baseline first: $t"}}
    foreach($port in $RuntimePort..($RuntimePort+4)){if(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue){throw "Test port occupied: $port"}}
    $jobs=@()
    $batchFailure=$null
    $ready=@()
    try{
        for($i=0;$i-lt$targets.Count;$i++){
            Write-Output "Starting $($targets[$i]): isolated fixture, port $($RuntimePort+$i), maximum heap 4 GiB"
            $jobs+=Start-Job -Name $targets[$i] -ScriptBlock {
                param($script,$target,$port,$batch,$debugRead,$debugUi)
                & $script -Target $target -RuntimePort $port -BatchId $batch -DebugRead:$debugRead -DebugUi:$debugUi
            } -ArgumentList $PSCommandPath,$targets[$i],($RuntimePort+$i),$batch,$DebugRead.IsPresent,$DebugUi.IsPresent
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
function Port-Free { -not(Get-NetTCPConnection -State Listen -LocalPort $RuntimePort -ErrorAction SilentlyContinue) }
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
    if(Get-NetTCPConnection -State Listen -LocalPort ($RuntimePort+100) -ErrorAction SilentlyContinue){throw 'Loopback server-list fixture port is occupied; refusing to ping an existing service'}
    Write-Owner $client
    $options=Join-Path $client 'options.txt'
    if(-not(Test-Path -LiteralPath $options)){[IO.File]::WriteAllText($options,"soundCategory_master:0.0`n",[Text.UTF8Encoding]::new($false))}
    $command="Set-Location -LiteralPath '$($repo.Replace("'","''"))'; & './gradlew.bat' ':version:$($Target):runValidationConformanceVanillaUiClient' '--offline' '--console=plain' '--init-script' 'components/validation_tools/conformance-vanilla_ui/task/vanilla-ui.init.gradle' '-PvanillaUiPort=$RuntimePort'; exit `$LASTEXITCODE"
    if($DebugUi){$command="`$env:MCP_RUNTIME_SCOPES='read,ui,input,capture,event,diagnostics,control,command,debug,debug.ui.read,debug.write,debug.ui'; "+$command}
    elseif($DebugRead){$command="`$env:MCP_RUNTIME_SCOPES='read,ui,input,capture,event,diagnostics,control,command,debug,debug.ui.read'; "+$command}
    $p=Start-Process -FilePath 'pwsh' -ArgumentList @('-NoProfile','-Command',$command) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'launch-out.log') -RedirectStandardError (Join-Path $root 'launch-error.log')
    $deadline=[DateTime]::UtcNow.AddMinutes(9)
    do{
        if($p.HasExited){throw "Client launcher exited: $($p.ExitCode)"}
        $token=Join-Path $client 'minecraft-protocol/token'
        if(Test-Path -LiteralPath $token){try{$headers=@{Authorization='Bearer '+(Get-Content -LiteralPath $token -Raw).Trim()};$tree=Invoke-RestMethod "http://127.0.0.1:$RuntimePort/v0/ui/tree" -Headers $headers -TimeoutSec 2;if($tree.target-eq$Target-and$tree.screenClass-like'*TitleScreen'-and-not$tree.overlayIdentity){return $p}}catch{}}
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
        if(-not$process.WaitForExit(10000)){Run-Scenario quit;if(-not$process.WaitForExit(60000)){throw 'Client shutdown incomplete'}}
        if($process.ExitCode-ne0){throw 'Client launcher failed'}
        if(-not(Port-Free)){throw 'Test Runtime port was not released'}
    }
}
Write-Output "UI conformance completed: $Target; same fixture restored; KeepClient=$KeepClient"
