[CmdletBinding()]param([Parameter(Mandatory)][string]$BaseUri,[Parameter(Mandatory)][string]$TokenFile,[Parameter(Mandatory)][string]$ExpectedTarget)
$ErrorActionPreference='Stop';$base=$BaseUri.TrimEnd('/');$token=(Get-Content $TokenFile -Raw).Trim();$auth=@{Authorization="Bearer $token"}
function A([bool]$c,[string]$m){if(-not$c){throw "Phase 9B.1 provider lifecycle failed: $m"}}
function O($ids,[int]$timeout=50,$headers=$auth){Invoke-RestMethod "$base/v0/observe/deep" -Method Post -Headers $headers -ContentType 'application/json' -Body (@{perspective='server_authoritative';domains=@('player','providers');includeProviderData=$true;allowReadEffects=$false;providerIds=$ids;providerQuery=@{probe='phase9b1'};budgets=@{maxProviders=8;providerTimeoutMs=$timeout;maxProviderBytes=16384;maxTotalProviderBytes=65536;maxResponseBytes=262144}}|ConvertTo-Json -Depth 20 -Compress)}
function P($response,[string]$id){@($response.providers|Where-Object -Property providerId -eq "minecraft_protocol_probe:$id")[0]}
$failure=O @('minecraft_protocol_probe:failure');A((P $failure 'failure').reason-eq'provider_exception'-and$failure.server.player)'throw isolation'
$timeout=O @('minecraft_protocol_probe:timeout') 25;A((P $timeout 'timeout').reason-eq'timeout'-and$timeout.server.player)'timeout isolation'
$late=O @('minecraft_protocol_probe:late-success') 25;A((P $late 'late-success').reason-eq'timeout')'late initial timeout';A(@($late.resourceRevisionRefs|Where-Object -Property resourceKey -eq 'minecraft_protocol_probe:late-success').Count-eq0)'late revision added early'
Start-Sleep -Milliseconds 400
$caps=Invoke-RestMethod "$base/v0/observe/deep/capabilities" -Headers $auth;A($caps.providerRuntime.pendingInvocations-eq0)'late pending leak'
$lateAgain=O @('minecraft_protocol_probe:late-success') 25;A((P $lateAgain 'late-success').reason-eq'timeout')'late contaminated next request';A(@($lateAgain.resourceRevisionRefs|Where-Object -Property resourceKey -eq 'minecraft_protocol_probe:late-success').Count-eq0)'late revision contamination'
$blocking=O @('minecraft_protocol_probe:blocking-before-future') 250;A((P $blocking 'blocking-before-future').reason-eq'synchronous_entry_budget_exceeded')'blocking entry detection'
$blockedAgain=O @('minecraft_protocol_probe:blocking-before-future') 250;A((P $blockedAgain 'blocking-before-future').reason-eq'provider_quarantined')'blocking quarantine'
$deadlineHeaders=@{Authorization="Bearer $token";'X-MCP-Deadline-Ms'='10'};$deadlineStatus=0
try{O @('minecraft_protocol_probe:timeout') 1000 $deadlineHeaders|Out-Null}catch{$deadlineStatus=[int]$_.Exception.Response.StatusCode}
A($deadlineStatus-eq408)'request deadline'
Start-Sleep -Milliseconds 100
$caps=Invoke-RestMethod "$base/v0/observe/deep/capabilities" -Headers $auth;A($caps.providerRuntime.pendingInvocations-eq0)'deadline pending leak'
$requestId=[guid]::NewGuid().ToString()
$cancelBody=@{perspective='server_authoritative';domains=@('providers');includeProviderData=$true;providerIds=@('minecraft_protocol_probe:timeout');providerQuery=@{probe='explicit-cancel'};budgets=@{providerTimeoutMs=1000}}|ConvertTo-Json -Depth 10 -Compress
$http=[System.Net.Http.HttpClient]::new()
$http.DefaultRequestHeaders.Authorization=[System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$token)
$message=[System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::Post,"$base/v0/observe/deep")
$message.Headers.TryAddWithoutValidation('X-MCP-Request-Id',$requestId)|Out-Null
$message.Content=[System.Net.Http.StringContent]::new($cancelBody,[Text.Encoding]::UTF8,'application/json')
$pendingRequest=$http.SendAsync($message)
Start-Sleep -Milliseconds 75
$cancelled=Invoke-RestMethod "$base/v0/requests/$requestId" -Method Delete -Headers $auth
A($cancelled.status-eq'cancelled')'explicit request cancellation'
try{$pendingRequest.GetAwaiter().GetResult()|Out-Null}catch{}
$message.Dispose();$http.Dispose()
Start-Sleep -Milliseconds 75
$caps=Invoke-RestMethod "$base/v0/observe/deep/capabilities" -Headers $auth;A($caps.providerRuntime.pendingInvocations-eq0)'explicit cancel pending leak'
$body=@{perspective='server_authoritative';domains=@('providers');includeProviderData=$true;providerIds=@('minecraft_protocol_probe:timeout');providerQuery=@{probe='disconnect'};budgets=@{providerTimeoutMs=1000}}|ConvertTo-Json -Depth 10 -Compress
$uri=[uri]$base;$client=[System.Net.Sockets.TcpClient]::new('127.0.0.1',$uri.Port)
try{$stream=$client.GetStream();$crlf=[string][char]13+[char]10;$request='POST /v0/observe/deep HTTP/1.1'+$crlf+'Host: 127.0.0.1:'+$uri.Port+$crlf+'Authorization: Bearer '+$token+$crlf+'Content-Type: application/json'+$crlf+'Content-Length: '+[Text.Encoding]::UTF8.GetByteCount($body)+$crlf+'Connection: close'+$crlf+$crlf+$body;$bytes=[Text.Encoding]::UTF8.GetBytes($request);$stream.Write($bytes,0,$bytes.Length)}finally{$client.Close()}
Start-Sleep -Milliseconds 150
$caps=Invoke-RestMethod "$base/v0/observe/deep/capabilities" -Headers $auth;A($caps.providerRuntime.pendingInvocations-eq0)'disconnect pending leak'
[pscustomobject]@{Result='PASS';Target=$ExpectedTarget;Throw='PASS';Timeout='PASS';LateCompletion='PASS';BlockingEntry='PASS';Quarantine='PASS';RequestDeadline='PASS';ExplicitRequestCancel='PASS';ClientDisconnect='PASS';Pending=$caps.providerRuntime.pendingInvocations}
