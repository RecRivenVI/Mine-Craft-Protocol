[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget)
$ErrorActionPreference='Stop'
$base=$BaseUri.TrimEnd('/')
$token=(Get-Content $TokenFile -Raw).Trim()
$auth=@{Authorization="Bearer $token"}
function Assert-True([bool]$Condition,[string]$Message) {
    if(-not $Condition) { throw "Phase 9B.2 executor bound failed: $Message" }
}
$caps=Invoke-RestMethod "$base/v0/observe/deep/capabilities" -Headers $auth
Assert-True ($caps.revisionRuntime.queueCapacity -eq 8) 'revision queue capacity'
Assert-True ($caps.providerRuntime.worker.queueCapacity -eq 16) 'provider queue capacity'
$body=@{
    perspective='server_authoritative'
    domains=@('player','entities','chunks','providers')
    selector=@{chunkRadius=2;entityRadius=64}
    includeProviderData=$true
    providerIds=@('minecraft_protocol_probe:safe')
    providerQuery=@{probe='concurrent'}
    budgets=@{maxEntities=128;maxProviders=1;maxResponseBytes=524288}
}|ConvertTo-Json -Depth 20 -Compress
$client=[System.Net.Http.HttpClient]::new()
$client.DefaultRequestHeaders.Authorization=[System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$token)
$client.Timeout=[TimeSpan]::FromSeconds(15)
$tasks=@()
$messages=@()
$watch=[Diagnostics.Stopwatch]::StartNew()
for($index=0;$index-lt16;$index++) {
    $message=[System.Net.Http.HttpRequestMessage]::new(
        [System.Net.Http.HttpMethod]::Post,"$base/v0/observe/deep")
    $message.Headers.TryAddWithoutValidation(
        'X-MCP-Request-Id',[guid]::NewGuid().ToString())|Out-Null
    $message.Content=[System.Net.Http.StringContent]::new(
        $body,[Text.Encoding]::UTF8,'application/json')
    $messages+=$message
    $tasks+=$client.SendAsync($message)
}
Assert-True ([Threading.Tasks.Task]::WaitAll(
    [Threading.Tasks.Task[]]$tasks,15000)) 'concurrent requests timed out'
$watch.Stop()
$statuses=@($tasks|ForEach-Object{[int]$_.Result.StatusCode})
Assert-True (@($statuses|Where-Object{$_ -notin @(200,429)}).Count -eq 0) 'controlled status'
foreach($task in $tasks) { $task.Result.Dispose() }
foreach($message in $messages) { $message.Dispose() }
$client.Dispose()
$sessionWatch=[Diagnostics.Stopwatch]::StartNew()
$session=Invoke-RestMethod "$base/v0/session" -Headers $auth
$sessionWatch.Stop()
Assert-True ($session.target -eq $ExpectedTarget) 'session responsive'
Assert-True ($sessionWatch.ElapsedMilliseconds -lt 5000) 'owner thread responsiveness'
$after=Invoke-RestMethod "$base/v0/observe/deep/capabilities" -Headers $auth
Assert-True ($after.revisionRuntime.queueDepth -le
    $after.revisionRuntime.queueCapacity) 'revision queue bounded'
Assert-True ($after.providerRuntime.worker.queueDepth -le
    $after.providerRuntime.worker.queueCapacity) 'provider queue bounded'
[pscustomobject]@{
    Result='PASS'
    Target=$ExpectedTarget
    Concurrent=16
    Success=@($statuses|Where-Object{$_ -eq 200}).Count
    ControlledRejection=@($statuses|Where-Object{$_ -eq 429}).Count
    ElapsedMs=$watch.ElapsedMilliseconds
    SessionLatencyMs=$sessionWatch.ElapsedMilliseconds
    RevisionQueueDepth=$after.revisionRuntime.queueDepth
    RevisionQueueCapacity=$after.revisionRuntime.queueCapacity
    ProviderQueueDepth=$after.providerRuntime.worker.queueDepth
    ProviderQueueCapacity=$after.providerRuntime.worker.queueCapacity
    ForcedOverload='JAVA_TEST_PASS'
}

