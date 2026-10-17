# Connection discovery is instance-scoped. Never scan ports, choose the first Runtime, or infer consent.
function Get-RuntimeConnection {
    [CmdletBinding()]param([Parameter(Mandatory)][string]$InstanceDirectory,[Parameter(Mandatory)][string]$ExpectedTarget,[DateTime]$NotBefore=[DateTime]::MinValue)
    $game=(Resolve-Path -LiteralPath $InstanceDirectory).Path.TrimEnd('\','/')
    $file=Join-Path $game 'minecraft-protocol/endpoint.json'
    if((Get-Item -LiteralPath $file).Length-gt16384){throw 'Runtime discovery exceeds 16 KiB'}
    $record=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json
    if($record.schemaVersion-ne1-or$record.state-ne'RUNNING'-or$record.target-ne$ExpectedTarget-or$record.runtimeInstanceId-notmatch'^[0-9a-f-]{36}$'-or$record.tokenFile-ne'token'-or$record.port-lt1-or$record.port-gt65535-or$record.baseUrl-ne"http://127.0.0.1:$($record.port)"-or[IO.Path]::GetFullPath($record.gameDirectory).TrimEnd('\','/')-ne$game){throw 'Invalid Runtime instance identity, Target or endpoint'}
    if(([DateTimeOffset]$record.startedAt).UtcDateTime-lt$NotBefore.ToUniversalTime()){throw 'Stale endpoint predates this launch'}
    $process=Get-Process -Id $record.pid -ErrorAction Stop
    if([Math]::Abs(($process.StartTime.ToUniversalTime()-([DateTimeOffset]$record.processStartedAt).UtcDateTime).TotalSeconds)-gt1){throw 'Stale Runtime process identity'}
    $tokenFile=Join-Path $game 'minecraft-protocol/token'
    if((Get-Item -LiteralPath $tokenFile).Length-gt16384){throw 'Runtime token exceeds 16 KiB'}
    $token=(Get-Content -LiteralPath $tokenFile -Raw).Trim()
    if(-not$token){throw 'Runtime token is empty'}
    $headers=@{Authorization='Bearer '+$token;'X-MCP-Runtime-Instance'=$record.runtimeInstanceId}
    $session=Invoke-RestMethod "$($record.baseUrl)/v0/session" -Headers $headers -TimeoutSec 3 -MaximumRedirection 0 -ResponseHeadersVariable responseHeaders
    if($session.target-ne$ExpectedTarget-or$session.runtimeConnection.runtimeInstanceId-ne$record.runtimeInstanceId-or$responseHeaders['X-MCP-Runtime-Instance']-ne$record.runtimeInstanceId){throw 'Runtime identity handshake failed'}
    [pscustomobject]@{BaseUri=$record.baseUrl;Port=[int]$record.port;TokenFile=$tokenFile;Headers=$headers;RuntimeInstanceId=$record.runtimeInstanceId;ProcessId=[int]$record.pid;StartedAt=$record.startedAt;InstanceDirectory=$game}
}

function Assert-RuntimeInstanceStopped {
    param([Parameter(Mandatory)][string]$InstanceDirectory)
    $lock=Join-Path $InstanceDirectory 'minecraft-protocol/instance.lock'
    if(Test-Path -LiteralPath $lock){
        # Windows sharing denial also protects a fixture restore from an open Java lock handle.
        $handle=[IO.File]::Open($lock,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
        $handle.Dispose()
    }
}

function Wait-RuntimeConnection {
    param([string]$InstanceDirectory,[string]$ExpectedTarget,[DateTime]$NotBefore,[Diagnostics.Process]$Launcher,[int]$TimeoutSeconds=360)
    $until=[DateTime]::UtcNow.AddSeconds($TimeoutSeconds)
    do {
        if($Launcher.HasExited){throw 'Owned Runtime launcher exited before discovery'}
        try{return Get-RuntimeConnection $InstanceDirectory $ExpectedTarget -NotBefore $NotBefore}catch{}
        Start-Sleep -Milliseconds 300
    }while([DateTime]::UtcNow-lt$until)
    throw 'Owned Runtime discovery timeout'
}

function Enter-RuntimeRunner {
    param([string]$InstanceDirectory)
    $lock=Join-Path (Split-Path $InstanceDirectory) 'runner.lock'
    $handle=[IO.File]::Open($lock,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try{Assert-RuntimeInstanceStopped $InstanceDirectory;return $handle}catch{$handle.Dispose();throw}
}
