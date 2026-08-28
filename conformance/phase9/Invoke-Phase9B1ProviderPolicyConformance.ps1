[CmdletBinding()]param([Parameter(Mandatory)][string]$BaseUri,[Parameter(Mandatory)][string]$TokenFile,[Parameter(Mandatory)][string]$ExpectedTarget)
$ErrorActionPreference='Stop';$base=$BaseUri.TrimEnd('/');$token=(Get-Content $TokenFile -Raw).Trim();$auth=@{Authorization="Bearer $token"}
function A([bool]$c,[string]$m){if(-not$c){throw "Phase 9B.1 provider policy failed: $m"}}
function O($ids,[string]$perspective,[bool]$allow){Invoke-RestMethod "$base/v0/observe/deep" -Method Post -Headers $auth -ContentType 'application/json' -Body (@{perspective=$perspective;domains=@('providers');includeProviderData=$true;allowReadEffects=$allow;providerIds=$ids;providerQuery=@{probe='phase9b1'};budgets=@{maxProviders=8;providerTimeoutMs=100;maxProviderBytes=16384;maxTotalProviderBytes=65536;maxResponseBytes=262144}}|ConvertTo-Json -Depth 20 -Compress)}
function P($response,[string]$id){@($response.providers|Where-Object -Property providerId -eq "minecraft_protocol_probe:$id")[0]}
$safe=O @('minecraft_protocol_probe:safe') 'server_authoritative' $false;A((P $safe 'safe').status-eq'completed')'safe'
$lazy=O @('minecraft_protocol_probe:lazy') 'server_authoritative' $false;A((P $lazy 'lazy').reason-eq'read_effects_not_allowed')'lazy default'
$lazyAllowed=O @('minecraft_protocol_probe:lazy') 'server_authoritative' $true;A((P $lazyAllowed 'lazy').status-eq'completed'-and(P $lazyAllowed 'lazy').readEffects-eq'lazy_initialization')'lazy opt-in'
$scope=O @('minecraft_protocol_probe:scope') 'server_authoritative' $true;A((P $scope 'scope').status-eq'permission_denied'-and(P $scope 'scope').reason-eq'provider_scope_denied')'scope'
$unsupported=O @('minecraft_protocol_probe:server-thread') 'client_known' $false;A((P $unsupported 'server-thread').reason-eq'unsupported_perspective')'unsupported perspective'
foreach($case in @(@('load-data','data_loading_not_allowed_in_observation'),@('storage','storage_access_not_allowed_in_observation'),@('mutate','mutation_not_allowed_in_observation'))){$r=O @("minecraft_protocol_probe:$($case[0])") 'server_authoritative' $true;$v=P $r $case[0];A($v.reason-eq$case[1])"$($case[0]) policy"}
$server=O @('minecraft_protocol_probe:server-thread') 'server_authoritative' $false;$sv=P $server 'server-thread';A($sv.status-eq'completed'-and$sv.data.threadName)'server affinity'
$client=O @('minecraft_protocol_probe:client-thread') 'client_known' $false;$cv=P $client 'client-thread';A($cv.status-eq'completed'-and$cv.data.threadName)'client affinity'
$render=O @('minecraft_protocol_probe:render-thread') 'client_known' $false;$rv=P $render 'render-thread';A(($rv.status-eq'completed')-or($rv.reason-eq'thread_affinity_unavailable'))'render affinity honesty'
$audit=Invoke-RestMethod "$base/v0/audit?limit=256" -Headers $auth;A(@($audit.providerInvocations|Where-Object -Property providerId -eq 'minecraft_protocol_probe:scope'|Where-Object -Property decision -eq 'provider_scope_denied').Count-ge1)'scope audit'
[pscustomobject]@{Result='PASS';Target=$ExpectedTarget;Safe='PASS';Lazy='PASS';Scope='PASS';Perspective='PASS';NoLoad='PASS';NoStorage='PASS';NoMutation='PASS';ServerThread=$sv.data.threadName;ClientThread=$cv.data.threadName;Render=$rv.status;Audit='PASS'}

