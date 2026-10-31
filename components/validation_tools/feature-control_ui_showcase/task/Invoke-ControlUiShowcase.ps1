#requires -Version 7.0
[CmdletBinding(DefaultParameterSetName='Case')]param(
    [ValidateSet('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')][string]$Target='1.20.1-forge',
    [Parameter(ParameterSetName='Case')]
    [ValidateSet('read','operate','takeover-gameplay','pointer-move','takeover-gui','mode-transition','ui-overlay','gui-scenes','chrome-spring','cursor-rule','pause-pointer','inventory-pointer','world-observe','world-interact','wait-evidence','persistent-read','cancel-cleanup','manual-esc')]
    [string]$Case='read',
    [Parameter(Mandatory,ParameterSetName='All')][switch]$All,
    [Parameter(Mandatory,ParameterSetName='Smoke')][switch]$Smoke,
    [Parameter(Mandatory,ParameterSetName='CoreSmoke')][switch]$CoreSmoke,
    [switch]$Automatic,
    [Parameter(Mandatory,ParameterSetName='List')][switch]$ListCases,
    [Parameter(Mandatory,ParameterSetName='Prepare')][switch]$PrepareClient,
    [ValidateRange(0.25,60)][double]$HoldSeconds=4,
    [ValidateRange(15,600)][int]$ManualTimeoutSeconds=180,
    [switch]$FromTitle,
    [switch]$FreshFixture,
    [switch]$Offline,
    [switch]$KeepClient
)
$ErrorActionPreference='Stop'
$script:leaseId=$null;$script:activeOperation=$null;$script:itemsOwned=$false
$script:taskId=$null
$script:worldFixture=$null
$script:contextExitReceipts=[Collections.Generic.List[object]]::new()
$script:reservedSlots=@(9,10,11,12)
. (Join-Path $PSScriptRoot 'ControlUiShowcase.Helpers.ps1')
. (Join-Path $PSScriptRoot 'ControlUiShowcase.Cases.ps1')
if($ListCases){$showcaseCases.GetEnumerator()|ForEach-Object {[pscustomobject]@{Case=$_.Key;Showcase=$_.Value;InAll=$_.Key-ne'manual-esc'}};return}
if($Smoke-or$CoreSmoke){$HoldSeconds=0.35}
if($PrepareClient){$KeepClient=$true}
$instanceProfile=if($Smoke-or$CoreSmoke-or$Automatic){'automatic'}else{'acceptance'}
$role=if($instanceProfile-eq'automatic'){'client-multiplayer'}else{'client'}
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
. (Join-Path $PSScriptRoot '../../RuntimeConnection.ps1')
$port=$null
$script:instance=Join-Path $repo "validations/feature-control_ui_showcase/instance/$Target/$role"
$tokenFile=Join-Path $instance 'minecraft-protocol/token'
$runnerLock=$null;$clientProcess=$null;$launcher=$null;$connected=$false;$instanceOwned=$false
$results=[Collections.Generic.List[object]]::new()
$sourcesBefore=Get-ShowcaseSourceHashes $repo $Target
$report=[ordered]@{schema='control-ui-showcase-v1';target=$Target;testedCommit=(& git -C $repo rev-parse HEAD).Trim();workingTreeDirty=[bool](& git -C $repo status --porcelain);runtime='loader_development_source_sets';purpose=$(if($PrepareClient){'PREPARE_CLIENT_ONLY'}elseif($CoreSmoke){'CORE_SHOWCASE_SMOKE'}else{'UI_AND_CORE_SHOWCASE'});requireTitle=[bool]($FromTitle-or$PrepareClient);freshFixture=[bool]$FreshFixture;result='FAIL';visualAcceptance='NOT_PERFORMED';unifiedAcceptance='NOT_PERFORMED';persistentWriteInvocations=0;cases=$results;cleanup='NOT_RUN';timestamp=[DateTime]::UtcNow.ToString('o')}
$report.offlineLaunch=[bool]$Offline
$report.instanceProfile=$instanceProfile
$report.instanceRole=$role
function Get-ShowcaseListener([int]$PortNumber) {
    Get-NetTCPConnection -LocalPort $PortNumber -State Listen -ErrorAction SilentlyContinue|Select-Object -First 1
}
try {
    New-Item -ItemType Directory -Path (Join-Path $repo 'validations/feature-control_ui_showcase/instance') -Force|Out-Null
    # One Runner, one target at a time. A stale empty lock file grants no ownership.
    $runnerLock=[IO.File]::Open((Join-Path $repo 'validations/feature-control_ui_showcase/instance/runner.lock'),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    foreach($otherTarget in @('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')|Where-Object {$_-ne$Target}){
        Assert-RuntimeInstanceStopped (Join-Path $repo "validations/feature-control_ui_showcase/instance/$otherTarget/client")
    }
    $marker=Join-Path $instance 'showcase-instance.json'
    if(Test-Path -LiteralPath $instance) {
        Assert-Showcase (Test-Path -LiteralPath $marker) '已有目录不是 Runner 创建的专用实例；不会使用或覆盖。'
        $ownership=Get-Content -LiteralPath $marker -Raw|ConvertFrom-Json
        Assert-Showcase ($ownership.target-eq$Target-and$ownership.purpose-eq'CONTROL_UI_SHOWCASE') '实例所有权标记不匹配'
        if($FreshFixture){
            Assert-RuntimeInstanceStopped $instance
            foreach($lock in Get-ChildItem -LiteralPath (Join-Path $instance 'saves') -Filter session.lock -File -Recurse -ErrorAction SilentlyContinue){$handle=[IO.File]::Open($lock.FullName,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None);$handle.Dispose()}
            $fixtureRoot=[IO.Path]::GetFullPath((Join-Path $repo "validations/feature-control_ui_showcase/instance/$Target")).TrimEnd('\','/')
            $retired=[IO.Path]::GetFullPath((Join-Path $fixtureRoot ('retired/'+$role+'-'+[guid]::NewGuid().ToString('N'))))
            Assert-Showcase ([IO.Path]::GetFullPath($instance)-eq(Join-Path $fixtureRoot $role)-and$retired.StartsWith($fixtureRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) 'Unsafe fresh fixture archive path'
            Assert-Showcase (-not((Get-Item -LiteralPath $instance -Force).Attributes-band[IO.FileAttributes]::ReparsePoint)) 'Cannot archive a linked instance'
            [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($retired))|Out-Null
            Move-Item -LiteralPath $instance -Destination $retired
            [IO.Directory]::CreateDirectory($instance)|Out-Null
            [IO.File]::WriteAllText($marker,(@{target=$Target;purpose='CONTROL_UI_SHOWCASE';created=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
            $report.retiredInstance=[IO.Path]::GetRelativePath($repo,$retired).Replace('\','/')
            Write-Host "已原样保留旧专用实例：$($report.retiredInstance)；不清空其存档或物品。"
        }
    } else {
        New-Item -ItemType Directory -Path $instance|Out-Null
        @{target=$Target;purpose='CONTROL_UI_SHOWCASE';created=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json|Set-Content -LiteralPath $marker -Encoding utf8
    }
    $instanceOwned=$true
    $connection=$null
    try{$connection=Get-RuntimeConnection $instance $Target}catch{}
    if(-not$connection) {
        Assert-RuntimeInstanceStopped $instance
        # Read-only process inventory; refuse an unexplained project client instead of multiplying windows.
        $otherClients=@(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'"|Where-Object {
            $_.CommandLine-match[regex]::Escape($repo)-and(Test-ShowcaseClientCommand $_.CommandLine)
        })
        Assert-Showcase ($otherClients.Count-eq0) '检测到已有 Minecraft 客户端；请先正常关闭，不会自动终止它。'
        $optionsFile=Join-Path $instance 'options.txt'
        $optionsText=if(Test-Path -LiteralPath $optionsFile){[IO.File]::ReadAllText($optionsFile)}else{''}
        [IO.File]::WriteAllText($optionsFile,(Get-InstanceProfileOptions $optionsText $instanceProfile),[Text.UTF8Encoding]::new($false))
        Write-Host "当前 Target：$Target。启动一个专用 Showcase 客户端；本次仅执行 $(if($PrepareClient){'准备主菜单，不运行案例'}elseif($Smoke){'Runner smoke'}elseif($CoreSmoke){'五个 Core 展示案例的有界自动 smoke'}elseif($All){'-All 顺序案例'}else{$Case})。" -ForegroundColor Cyan
        $child=Join-Path $PSScriptRoot 'Start-ControlUiShowcaseClient.ps1'
        $launchArguments=@('-NoProfile','-File',('"'+$child+'"'),'-Target',$Target,'-Role',$role,'-InstanceDirectory',('"'+$instance+'"'))
        if($Offline){$launchArguments+='-Offline'}
        $launcher=Start-Process -FilePath (Join-Path $PSHOME 'pwsh.exe') -ArgumentList $launchArguments -WorkingDirectory $repo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $instance 'launcher.log') -RedirectStandardError (Join-Path $instance 'launcher-error.log')
        $until=[DateTime]::UtcNow.AddMinutes(8)
        $nextNotice=[DateTime]::UtcNow.AddSeconds(20)
        do {
            try{$connection=Get-RuntimeConnection $instance $Target -NotBefore $launcher.StartTime.ToUniversalTime()}catch{}
            if($connection){break}
            Assert-Showcase (-not$launcher.HasExited) "启动失败，见 $instance/launcher-error.log 和 launcher.log"
            if([DateTime]::UtcNow-gt$nextNotice){Write-Host '  等待 Gradle / Loader 启动当前唯一客户端…';$nextNotice=[DateTime]::UtcNow.AddSeconds(20)}
            Start-Sleep -Milliseconds 500
        }while([DateTime]::UtcNow-lt$until)
        Assert-Showcase ([bool]$connection) 'Runtime startup timeout; not starting a second client'
    }
    Assert-Showcase (Test-Path -LiteralPath $tokenFile) '专用 Runtime token 缺失'
    $script:base=$connection.BaseUri;$port=$connection.Port
    $script:auth=$connection.Headers
    $session=Invoke-ShowcaseApi GET '/v0/session'
    Assert-Showcase ($session.target-eq$Target) '端口上的 Target 与请求不匹配'
    $clientProcess=Get-Process -Id $connection.ProcessId
    $report.runtimeInstanceId=$connection.RuntimeInstanceId;$report.runtimePort=$port
    $clientProcess.EnableRaisingEvents=$true
    $initial=Get-ShowcaseMode
    Assert-Showcase (-not$initial.reconsentRequired) '用户手动结束控制；Runner 不会自动重新申请 TAKEOVER。'
    Assert-Showcase ($initial.mode-ne'TAKEOVER') '已有 TAKEOVER；先由所属控制会话正常释放。'
    $until=[DateTime]::UtcNow.AddSeconds(45)
    do{$session=Invoke-ShowcaseApi GET '/v0/session';if($session.screenClass-match'TitleScreen|AccessibilityOnboardingScreen'-or$session.inWorld){break};Start-Sleep -Milliseconds 300}while([DateTime]::UtcNow-lt$until)
    Wait-ShowcaseStableScreen
    if($FromTitle-or$PrepareClient){Assert-ShowcaseTitle (Invoke-ShowcaseApi GET '/v0/session')}
    $connected=$true
    $capabilities=Invoke-ShowcaseApi GET '/v0/capabilities'
    $report.capabilities=$capabilities.capabilities
    $report.initialControlMode=$initial
    $report.apiTransport='native_http_instance_discovery'
    $runtimeProcess=Get-CimInstance Win32_Process -Filter "ProcessId=$($connection.ProcessId)"
    $jcmd=Join-Path (Split-Path $runtimeProcess.ExecutablePath) 'jcmd.exe'
    $flags=& $jcmd $connection.ProcessId VM.flags 2>&1|Out-String
    Assert-Showcase ($LASTEXITCODE-eq0-and$flags-match'(?:^|\s)-XX:MaxHeapSize=4294967296(?:\s|$)') 'Showcase actual client maximum heap must be 4 GiB'
    $report.maximumHeapBytes=4294967296
    if($session.screenClass-match'AccessibilityOnboardingScreen') {
        Write-Host '  [首次启动/TAKEOVER] 通过 Vanilla Continue 完成欢迎页，不改无障碍或视觉设置。'
        Start-ShowcaseTakeover;Click-ShowcaseLabel 'Continue';Wait-ShowcaseScreen 'TitleScreen'
        Set-ShowcaseMode READ|Out-Null
    }
    Wait-ShowcaseStableScreen
    $selected=if($PrepareClient){@()}elseif($Smoke){@('smoke')}elseif($CoreSmoke){@('world-observe','world-interact','wait-evidence','persistent-read','cancel-cleanup')}elseif($All){Get-ShowcaseCaseOrder}else{@($Case)}
    if($PrepareClient){Write-Host '客户端已准备好并停在主菜单。未运行案例、未申请 TAKEOVER；可重复运行 -FromTitle -KeepClient 案例。' -ForegroundColor Cyan}
    foreach($name in $selected) {
        Write-Host "`n=== $Target / $name : $($showcaseCases[$name]) ===" -ForegroundColor Cyan
        $started=[DateTime]::UtcNow
        $script:caseEvidence=[Collections.Generic.List[object]]::new()
        try {
            Start-ShowcaseTask
            if($name-eq'smoke'){Invoke-ShowcaseSmoke}else{Invoke-ShowcaseCase $name}
            $clean=Assert-ShowcaseCleanInput
            Stop-ShowcaseTask
            $results.Add([pscustomobject]@{case=$name;result='PASS';meaning='EXECUTION_ONLY_NOT_VISUAL_VERDICT';heldKeys=$clean.pressedKeyCount;heldButtons=$clean.pressedButtonCount;evidence=@($script:caseEvidence);seconds=([DateTime]::UtcNow-$started).TotalSeconds})
        } catch {$results.Add([pscustomobject]@{case=$name;result='FAIL';message=$_.Exception.Message});throw}
    }
    $report.result='PASS'
} catch {
    $report.failure=$_.Exception.Message
    Write-Warning $_.Exception.Message
} finally {
    if($connected-and-not$clientProcess.HasExited) {
        try {
            if($script:activeOperation){Invoke-ShowcaseApi DELETE "/v0/operations/$script:activeOperation"|Out-Null}
            # Release only the Runner's Lease, never another principal's emergency-release.
            if($script:leaseId){
                try{if((Get-ShowcaseMode).mode-eq'TAKEOVER'){Invoke-ShowcaseApi POST '/v0/control/release' $null (Get-ShowcaseControlHeaders)|Out-Null}}finally{$script:leaseId=$null}
            }
            Assert-ShowcaseCleanInput|Out-Null
            Restore-ShowcaseWorldFixture
            Restore-ShowcaseItems
            $mode=Get-ShowcaseMode
            if($mode.reconsentRequired) {
                $report.cleanup='INPUT_CLEAN_MANUAL_LATCH_PRESERVED_CLIENT_LEFT_OPEN'
            } else {
                Exit-ShowcaseWorld
                Set-ShowcaseMode READ|Out-Null
                $report.cleanup='READ_HELD_ZERO_TITLE'
                if(-not$KeepClient) {
                    Assert-Showcase ((Invoke-ShowcaseApi GET '/v0/session').screenClass-match'TitleScreen') 'unknown Screen; not sending Quit to an unverified target'
                    Start-ShowcaseTakeover
                    # A real Quit closes the HTTP transport before the click response can finish.
                    # Judge it by process exit + port release, not that expected response race.
                    try{Click-ShowcaseLabel 'Quit Game'}catch{$report.quitResponse=$_.Exception.Message}
                    Assert-Showcase ($clientProcess.WaitForExit(30000)-and$clientProcess.ExitCode-eq0) 'client did not shut down cleanly'
                    $script:leaseId=$null
                    Assert-Showcase (-not(Get-ShowcaseListener $port)) 'Runtime port remains after shutdown'
                    $report.cleanup='CLEAN_SHUTDOWN_PORT_RELEASED'
                    if($launcher){[void]$launcher.WaitForExit(15000)}
                }
            }
        } catch {
            $report.result='FAIL';$report.cleanup='FAILED: '+$_.Exception.Message;Write-Warning $report.cleanup
            if($script:leaseId){try{Invoke-ShowcaseApi POST '/v0/control/release' $null (Get-ShowcaseControlHeaders)|Out-Null}catch{}finally{$script:leaseId=$null}}
        } finally {
            if(-not$clientProcess.HasExited){try{Stop-ShowcaseTask}catch{$report.result='FAIL';$report.taskCleanup=$_.Exception.Message}}
        }
    }
    if($runnerLock){$runnerLock.Dispose()}
    if($instanceOwned){
        if($script:worldFixture){$report.fixtureRecoveryRequired=$script:worldFixture}
        $sourcesAfter=Get-ShowcaseSourceHashes $repo $Target
        $report.sourceHashes=$sourcesAfter
        if($script:contextExitReceipts.Count){$report.worldExitReceipts=@($script:contextExitReceipts)}
        $report.sourceChangedDuringRun=($sourcesBefore|ConvertTo-Json -Compress)-ne($sourcesAfter|ConvertTo-Json -Compress)
        if($report.sourceChangedDuringRun){$report.result='FAIL';$report.failure='Sources changed during Showcase execution'}
        $resultDirectory=Join-Path $repo 'validations/feature-control_ui_showcase/result'
        [void](New-Item -ItemType Directory -Force -Path $resultDirectory)
        $output=Join-Path $resultDirectory ($Target+'.json')
        $json=($report|ConvertTo-Json -Depth 30)-replace"`r`n","`n"
        [IO.File]::WriteAllText($output,$json+"`n",[Text.UTF8Encoding]::new($false))
        Write-Host "执行记录（非视觉结论）：$output"
    }
}
[pscustomobject]$report
if($report.result-ne'PASS'){throw 'Showcase execution incomplete; see the case/cleanup result above'}
