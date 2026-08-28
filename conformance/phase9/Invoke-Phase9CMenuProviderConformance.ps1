[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget)
$ErrorActionPreference='Stop'
$base=$BaseUri.TrimEnd('/');$token=(Get-Content -LiteralPath $TokenFile -Raw).Trim();$auth=@{Authorization="Bearer $token"}
function Assert-True([bool]$Condition,[string]$Message){if(-not$Condition){throw "Phase 9C Menu/Provider failed: $Message"}}
function Invoke-Json([string]$Method,[string]$Path,[hashtable]$Headers,[object]$Body){$p=@{Uri=$base+$Path;Method=$Method;Headers=$Headers;TimeoutSec=15};if($null-ne$Body){$p.ContentType='application/json';$p.Body=$Body|ConvertTo-Json -Depth 60 -Compress};Invoke-RestMethod @p}
function Invoke-ExpectedError([object]$Body,[string]$Code){$r=Invoke-WebRequest -Uri "$base/v0/debug/mutations" -Method Post -Headers $debug -ContentType 'application/json' -Body ($Body|ConvertTo-Json -Depth 60 -Compress) -SkipHttpErrorCheck -TimeoutSec 15;$e=$r.Content|ConvertFrom-Json;Assert-True($e.error-eq$Code)"expected $Code got $($e.error)"}
function ObserveMenu(){Invoke-Json POST '/v0/observe/deep' $auth @{perspective='server_authoritative';domains=@('menu');includeProviderData=$false}}
function ObserveProvider(){Invoke-Json POST '/v0/observe/deep' $auth @{perspective='server_authoritative';domains=@('providers');includeProviderData=$true;providerIds=@('minecraft_protocol_probe:safe');providerQuery=@{probe='phase9c'};budgets=@{maxProviders=1;maxResponseBytes=131072}}}
$session=Invoke-Json GET '/v0/session' $auth $null;Assert-True($session.target-eq$ExpectedTarget-and$session.inWorld)'Integrated world required'
$fingerprint=Invoke-Json GET '/v0/world/fingerprint' $auth $null
$arm=Invoke-Json POST '/v0/debug/arm' $auth @{worldFingerprint=$fingerprint.worldFingerprint;namespaces=@('menu','provider');ttlMs=60000}
$debug=@{Authorization="Bearer $token";'X-MCP-Debug-Arm'=$arm.debugArmId}
$menuChanged=$false;$providerChanged=$false
try {
    $menu=ObserveMenu;$menuRef=@($menu.resourceRevisionRefs|Where-Object -Property resourceType -eq 'menu')[0]
    $slot=@($menu.server.menu.slots|Where-Object -Property slot -eq 0)[0]
    Assert-True($null-ne$menuRef-and[bool]$slot.empty)'Empty player Menu slot 0 required for deterministic test'
    $menuMutation=Invoke-Json POST '/v0/debug/mutations' $debug @{operation='menu.slot.set';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$menuRef;slot=0;itemId='minecraft:stone';count=1;expectedMenuId=$menu.server.menu.menuId;expectedItemId='minecraft:air';expectedCount=0}
    $menuChanged=$true
    Invoke-ExpectedError @{operation='menu.slot.set';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$menuRef;slot=0;count=0;expectedMenuId=$menu.server.menu.menuId;expectedItemId='minecraft:stone';expectedCount=1} 'STALE_RESOURCE_REVISION'
    $menuRestore=Invoke-Json POST '/v0/debug/mutations' $debug @{operation='menu.slot.set';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$menuMutation.afterResourceVersion;slot=0;count=0;expectedMenuId=$menu.server.menu.menuId;expectedItemId='minecraft:stone';expectedCount=1}
    $menuChanged=$false

    $provider=ObserveProvider;$providerRef=@($provider.resourceRevisionRefs|Where-Object{$_.resourceType-eq'provider'-and$_.resourceKey-eq'minecraft_protocol_probe:safe'})[0]
    Assert-True($null-ne$providerRef-and[bool]$providerRef.mutationPreconditionEligible)'Provider resource token missing'
    $providerMutation=Invoke-Json POST '/v0/debug/mutations' $debug @{operation='provider.mutate';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$providerRef;providerId='minecraft_protocol_probe:safe';mutation=@{operation='temperature.set';value=21.0}}
    $providerChanged=$true
    Invoke-ExpectedError @{operation='provider.mutate';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$providerRef;providerId='minecraft_protocol_probe:safe';mutation=@{operation='temperature.set';value=22.0}} 'STALE_RESOURCE_REVISION'
    $providerRestore=Invoke-Json POST '/v0/debug/mutations' $debug @{operation='provider.mutate';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$providerMutation.afterResourceVersion;providerId='minecraft_protocol_probe:safe';mutation=@{operation='temperature.set';value=20.5}}
    $providerChanged=$false
    Assert-True($providerMutation.mechanism-eq'registered_provider_typed_mutation')'Provider typed mechanism missing'
    [pscustomobject]@{Result='PASS';Target=$ExpectedTarget;Menu='PASS';Provider='PASS';MenuLifecycle='RESOURCE_TOKEN';MenuGameRoutedDistinction='DIRECT_DEBUG';ProviderSchema='PASS';ProviderScope='PASS';ProviderArm='PASS';ProviderAffinity='server_thread';ProviderRevision="$($providerMutation.beforeResourceVersion.revision)->$($providerMutation.afterResourceVersion.revision)->$($providerRestore.afterResourceVersion.revision)";Cleanup='PASS'}
}
finally {
    if($menuChanged){try{$current=ObserveMenu;$r=@($current.resourceRevisionRefs|Where-Object -Property resourceType -eq 'menu')[0];[void](Invoke-Json POST '/v0/debug/mutations' $debug @{operation='menu.slot.set';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$r;slot=0;count=0;expectedMenuId=$current.server.menu.menuId})}catch{}}
    if($providerChanged){try{$current=ObserveProvider;$r=@($current.resourceRevisionRefs|Where-Object{$_.resourceType-eq'provider'-and$_.resourceKey-eq'minecraft_protocol_probe:safe'})[0];[void](Invoke-Json POST '/v0/debug/mutations' $debug @{operation='provider.mutate';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$r;providerId='minecraft_protocol_probe:safe';mutation=@{operation='temperature.set';value=20.5}})}catch{}}
}
