[CmdletBinding()]param([Parameter(Mandatory)][string]$BaseUri,[Parameter(Mandatory)][string]$TokenFile,[Parameter(Mandatory)][string]$ExpectedTarget)
$ErrorActionPreference='Stop';$base=$BaseUri.TrimEnd('/');$token=(Get-Content $TokenFile -Raw).Trim();$auth=@{Authorization="Bearer $token"}
function A([bool]$c,[string]$m){if(-not$c){throw "Phase 9B.1 provider schema failed: $m"}}
function O($ids,$query=@{probe='phase9b1'}){Invoke-RestMethod "$base/v0/observe/deep" -Method Post -Headers $auth -ContentType 'application/json' -Body (@{perspective='server_authoritative';domains=@('providers');includeProviderData=$true;providerIds=$ids;providerQuery=$query;budgets=@{maxProviders=8;providerTimeoutMs=100;maxProviderBytes=1024;maxTotalProviderBytes=8192;maxResponseBytes=262144}}|ConvertTo-Json -Depth 20 -Compress)}
function P($response,[string]$id){@($response.providers|Where-Object -Property providerId -eq "minecraft_protocol_probe:$id")[0]}
$safe=O @('minecraft_protocol_probe:safe');A((P $safe 'safe').status-eq'completed')'valid payload'
$cases=@{'schema-missing'='schema_violation';'schema-type'='schema_violation';'schema-nested'='schema_violation';'oversized'='provider_byte_budget_exceeded';'invalid'='schema_version_mismatch'}
foreach($entry in $cases.GetEnumerator()){$r=O @("minecraft_protocol_probe:$($entry.Key)");A((P $r $entry.Key).reason-eq$entry.Value)"$($entry.Key) classification"}
$query=O @('minecraft_protocol_probe:safe') @{unexpected=@{nested='shape'}};A((P $query 'safe').reason-eq'query_schema_violation')'query schema'
$caps=Invoke-RestMethod "$base/v0/observe/deep/capabilities" -Headers $auth;$descriptor=@($caps.providers|Where-Object -Property providerId -eq 'minecraft_protocol_probe:safe')[0];A($descriptor.snapshotSchema-and$descriptor.querySchema-and$descriptor.requiredScopes-and$descriptor.perspectives)'descriptor schema'
[pscustomobject]@{Result='PASS';Target=$ExpectedTarget;Valid='PASS';Missing='PASS';WrongType='PASS';Nested='PASS';Oversized='PASS';Version='PASS';Query='PASS';Registration='JAVA_TEST';Duplicate='JAVA_TEST'}
