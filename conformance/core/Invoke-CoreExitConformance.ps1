[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget,
    [Parameter(Mandatory)][string]$InstanceDirectory,
    [ValidateRange(0,5000)][int]$UiHoldMs=100
)
# Run only against a dedicated test instance. Save & Quit is ordinary Minecraft
# lifecycle behavior, not a Persistent Write API or direct save-file mutation.
$ErrorActionPreference='Stop'
$base=$BaseUri.TrimEnd('/')
$uri=[uri]$base
if(-not$uri.IsLoopback){throw 'Process exit attestation requires a local test instance'}
$instance=(Resolve-Path -LiteralPath $InstanceDirectory).Path
$auth=@{Authorization='Bearer '+(Get-Content -LiteralPath $TokenFile -Raw).Trim()}
function Require([bool]$ok,[string]$reason){if(-not$ok){throw "Core exit conformance: $reason"}}
function Json([string]$method,[string]$path,[object]$body=$null,[hashtable]$headers=$auth){
    $parameters=@{Uri="$base$path";Method=$method;Headers=$headers;TimeoutSec=40}
    if($null-ne$body){$parameters.ContentType='application/json';$parameters.Body=$body|ConvertTo-Json -Depth 20 -Compress}
    Invoke-RestMethod @parameters
}
function Pipeline([object[]]$steps){
    $operation=Json POST '/v0/pipelines' @{timeoutMs=35000;steps=$steps} $control
    $done=Json POST "/v0/operations/$($operation.operationId)/wait" @{timeoutMs=36000}
    Require ($done.state-eq'completed') "pipeline $($done.operationId): $($done.state) / $($done.error)"
    return $done
}
$session=Json GET '/v0/session'
Require ($session.target-eq$ExpectedTarget-and$session.inWorld-and[string]::IsNullOrEmpty($session.screenClass)) 'test world must be open without a GUI'
$before=Json GET '/v0/input/state'
Require ($before.controlState-ne'MANUALLY_REVOKED') 'explicit conversation consent and reacquire are required first'
$connection=Get-NetTCPConnection -LocalPort $uri.Port -State Listen|Select-Object -First 1
$process=Get-Process -Id $connection.OwningProcess
$process.EnableRaisingEvents=$true
$lease=Json POST '/v0/control/acquire' @{ttlMs=60000}
$control=$auth.Clone();$control['X-MCP-Control-Lease']=$lease.leaseId
$recording=$null
try{
    $recording=Json POST '/v0/recordings' @{
        intervalMs=150;durationMs=60000;maxSamples=128;captureFrames=$true
        stateReads=@(@{providerId='minecraft:client/player'})
        contactSheet=@{enabled=$true;columns=4;cellWidth=160;cellHeight=90}
    }
    $operation=Pipeline @(
        @{type='key.tap';key=256;holdMs=40},
        @{type='wait.until';condition=@{type='screen';classContains='PauseScreen'};timeoutMs=5000},
        @{type='ui.action';selector=@{label='Save and Quit to Title'};holdMs=$UiHoldMs},
        @{type='wait.until';condition=@{type='screen';classContains='TitleScreen'};timeoutMs=25000}
    )
    $offlineDeadline=[DateTime]::UtcNow.AddSeconds(8)
    do{
        $inventory=Json GET '/v0/diagnostics/phase9a/inventory'
        $offline=$inventory.persistentWriteSafety.lifecycleState-eq'STOPPED_OFFLINE'
        if(-not$offline){Start-Sleep -Milliseconds 100}
    }while(-not$offline-and[DateTime]::UtcNow-lt$offlineDeadline)
    Require $offline 'LIVE ownership must be gone before offline reads'
    $reads=foreach($domain in @('world','player','chunk')){
        $read=Json POST '/v0/diagnostics/phase9a/storage/read' @{domain=$domain}
        Require ($read.dataSource-eq'PERSISTED'-and$read.consistency-eq'last_saved_state'-and$read.lifecycleState-eq'offline_file_snapshot'-and-not$read.liveWorldExists-and-not$read.targetLoaded) "offline $domain authority"
        $read|Select-Object domain,readStatus,dataSource,consistency,lifecycleState
    }
    $afterEsc=Json GET '/v0/input/state'
    Require ($afterEsc.nativeRevocations-eq$before.nativeRevocations-and$afterEsc.controlState-eq'AGENT_CONTROLLED') 'Agent Esc must not revoke control'
    Json POST '/v0/control/release' $null $control|Out-Null
    $fadeDeadline=[DateTime]::UtcNow.AddSeconds(2)
    do{$released=Json GET '/v0/input/state';if($released.controlChromeAlpha-ne0){Start-Sleep -Milliseconds 50}}while($released.controlChromeAlpha-ne0-and[DateTime]::UtcNow-lt$fadeDeadline)
    Require ($released.controlState-eq'IDLE'-and$released.controlChromeAlpha-eq0-and$released.windowIconState-eq'original_minecraft'-and$released.pressedKeyCount-eq0-and$released.pressedButtonCount-eq0) 'normal release presentation/input cleanup'
    $lease=Json POST '/v0/control/acquire' @{ttlMs=15000}
    $control['X-MCP-Control-Lease']=$lease.leaseId
    try{Json POST '/v0/ui/action' @{selector=@{label='Quit Game'};holdMs=100} $control|Out-Null}catch{$quitDiagnostic=$_.Exception.Message}
    $exitDeadline=[DateTime]::UtcNow.AddSeconds(30)
    while(-not$process.HasExited-and[DateTime]::UtcNow-lt$exitDeadline){Start-Sleep -Milliseconds 100;$process.Refresh()}
    Require ($process.HasExited-and$process.ExitCode-eq0) 'client must terminate normally, not only close its HTTP port'
    Require (-not(Test-Path -LiteralPath (Join-Path $instance "hs_err_pid$($process.Id).log"))) 'native JVM crash report present'
    $directory=Join-Path $instance "minecraft-protocol/artifacts/$($recording.recordingId)"
    $manifest=Get-Content -LiteralPath (Join-Path $directory 'manifest.json') -Raw|ConvertFrom-Json
    Require ($manifest.status-eq'completed'-and$manifest.stopReason-eq'transport_close'-and$manifest.writerErrors-eq0) 'active recording must finalize on transport close'
    foreach($name in @('bundle.zip','checksums.json','frame-index.json')){Require (Test-Path -LiteralPath (Join-Path $directory $name)) "$name missing"}
    [pscustomobject]@{
        Result='PASS';Target=$ExpectedTarget;AgentEsc='PASS';SaveAndQuit='PASS';Lifecycle='STOPPED_OFFLINE'
        OfflineReads=$reads;NormalRelease='PASS';CleanShutdown='PASS';ProcessExitCode=$process.ExitCode
        RecordingId=$recording.recordingId;RecordingStatus=$manifest.status;StopReason=$manifest.stopReason
        WrittenFrames=$manifest.writtenFrames;Gaps=$manifest.gaps;WriterErrors=$manifest.writerErrors
        Manifest=(Join-Path $directory 'manifest.json');PersistentWriteInvocations=0
    }
}finally{
    if(-not$process.HasExited){
        if($recording){try{Json DELETE "/v0/recordings/$($recording.recordingId)"|Out-Null}catch{}}
        try{Json POST '/v0/control/release' $null $control|Out-Null}catch{}
    }
}
