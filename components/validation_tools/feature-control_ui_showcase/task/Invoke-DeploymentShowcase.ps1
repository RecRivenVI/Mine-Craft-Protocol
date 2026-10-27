#requires -Version 7.0
[CmdletBinding()]param(
    [Parameter(Mandatory)][ValidateSet('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')][string]$Target,
    [Parameter(Mandatory)][ValidateSet('server-console','peer-authority')][string]$Case,
    [Parameter(Mandatory)][string]$ServerInstance,
    [string]$ClientInstance,
    [ValidateRange(0.25,30)][double]$HoldSeconds=4
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
. (Join-Path $PSScriptRoot '../../RuntimeConnection.ps1')
function Connect-DemoInstance([string]$Directory) {
    $resolved=(Resolve-Path -LiteralPath $Directory).Path
    $validationRoot=[IO.Path]::GetFullPath((Join-Path $repo 'validations')).TrimEnd('\')+'\'
    if(-not$resolved.StartsWith($validationRoot,[StringComparison]::OrdinalIgnoreCase)-or$resolved-notmatch'[\\/]instance[\\/]'){
        throw 'Only explicitly selected repository validation instances may be used'
    }
    Get-RuntimeConnection $resolved $Target
}
function Invoke-DemoApi($Connection,[string]$Method,[string]$Path,$Body=$null) {
    $parameters=@{Uri=$Connection.BaseUri+$Path;Method=$Method;Headers=$Connection.Headers;TimeoutSec=15;MaximumRedirection=0;NoProxy=$true}
    if($null-ne$Body){$parameters.ContentType='application/json';$parameters.Body=$Body|ConvertTo-Json -Depth 24 -Compress}
    Invoke-RestMethod @parameters
}
$server=Connect-DemoInstance $ServerInstance
$session=Invoke-DemoApi $server GET '/v0/session'
if($session.deploymentRole-ne'DEDICATED_SERVER'){throw 'ServerInstance is not a dedicated server Runtime'}
$grant=$null;$recording=$null;$changedMode=$null;$client=$null;$cleanupOriginal=$null
$initial=Invoke-DemoApi $server GET '/v0/control/mode'
if($Case-eq'server-console'-and$initial.mode-ne'READ'){throw 'server-console starts in READ; explicitly finish the existing owner operation first'}
try {
    if($Case-eq'server-console'){
        Write-Host 'READ：服务器实际发布的有界日志；不是宿主终端截图。'
        Invoke-DemoApi $server GET '/v0/server/console?after=0&limit=16'|ConvertTo-Json -Depth 8|Write-Host
        Invoke-DemoApi $server POST '/v0/server/console/suggestions' @{input='li';cursor=2}|ConvertTo-Json -Depth 8|Write-Host
        Start-Sleep -Milliseconds ([int]($HoldSeconds*1000))
        if($initial.mode-eq'READ'){
            $changedMode=Invoke-DemoApi $server POST '/v0/control/mode' @{mode='OPERATE';expectedModeVersion=$initial.modeVersion}
        }
        Write-Host 'OPERATE：原生 list 命令；不接管玩家键鼠、不修改世界。'
        $operation=Invoke-DemoApi $server POST '/v0/server/console/commands' @{command='list'}
        $terminal=Invoke-DemoApi $server POST "/v0/operations/$($operation.operationId)/wait" @{timeoutMs=5000}
        if($terminal.state-ne'completed'){throw ($terminal|ConvertTo-Json -Depth 12)}
        $terminal.result|ConvertTo-Json -Depth 12|Write-Host
        $recording=Invoke-DemoApi $server POST '/v0/recordings' @{category='USER';captureFrames=$false;durationMs=5000;maxSamples=12;contactSheet=@{enabled=$false}}
        Start-Sleep -Milliseconds ([int]($HoldSeconds*1000))
        Invoke-DemoApi $server DELETE "/v0/recordings/$($recording.recordingId)"|ConvertTo-Json -Depth 8|Write-Host
        Write-Host "结构化 Recording：$($recording.recordingId)。没有图像轨道，不把命令分发当成世界效果证明。"
    }else{
        if(-not$ClientInstance){throw 'peer-authority requires an explicit ClientInstance'}
        $client=Connect-DemoInstance $ClientInstance
        $peer=Invoke-DemoApi $client GET '/v0/server/peer'
        if(-not$peer.connected-or-not$peer.connectionId-or-not$peer.playerUuid){throw 'Client must already be connected to the selected product server'}
        if($peer.delegationAttached){throw 'Client has an existing attachment; it will not be replaced'}
        Write-Host '服务器所有者显式授权这个真实玩家连接的 DEBUG 读取；不发放修改、输入或 OP 权限。'
        $grant=Invoke-DemoApi $server POST '/v0/server/peer/delegations' @{playerUuid=$peer.playerUuid;connectionId=$peer.connectionId;scopes=@('read','debug','debug.world.read');ttlMs=15000}
        Invoke-DemoApi $client POST '/v0/server/peer/delegation' @{delegationId=$grant.delegationId;connectionId=$peer.connectionId}|ConvertTo-Json -Depth 8|Write-Host
        $read=Invoke-DemoApi $client POST '/v0/observe/deep' @{category='DEBUG';perspective='server_authoritative';domains=@('player');projection=@{playerFields=@('identity','transform','vitals')};selector=@{chunkRadius=0};budgets=@{maxResponseBytes=30000}}
        if($read.peerPlayerUuid-ne$peer.playerUuid){throw 'Server receipt does not identify the real delegated player'}
        $read|ConvertTo-Json -Depth 16|Write-Host
        Start-Sleep -Milliseconds ([int]($HoldSeconds*1000))
        Write-Host '结束只读委托；玩家输入、模式、Lease 和独立 Debug Arm 均不由本案例取得。'
    }
}catch{$cleanupOriginal=$_;throw}finally{
    $cleanupErrors=[Collections.Generic.List[Exception]]::new()
    if($grant){
        try{Invoke-DemoApi $server DELETE "/v0/server/peer/delegations/$($grant.delegationId)"|Out-Null}catch{$cleanupErrors.Add($_.Exception)}
        if($client){try{Invoke-DemoApi $client POST '/v0/server/peer/delegation' @{action='detach';delegationId=$grant.delegationId}|Out-Null}catch{$cleanupErrors.Add($_.Exception)}}
    }
    if($recording){try{Invoke-DemoApi $server DELETE "/v0/recordings/$($recording.recordingId)"|Out-Null}catch{$cleanupErrors.Add($_.Exception)}}
    if($changedMode){
        try{Invoke-DemoApi $server POST '/v0/control/mode' @{mode='READ';expectedModeVersion=$changedMode.modeVersion}|Out-Null}catch{$cleanupErrors.Add($_.Exception)}
    }
    if($cleanupErrors.Count){if($cleanupOriginal){$cleanupErrors.Add($cleanupOriginal.Exception)};throw [AggregateException]::new('Showcase cleanup did not complete',$cleanupErrors)}
}
