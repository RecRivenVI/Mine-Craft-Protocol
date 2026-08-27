[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget)
$ErrorActionPreference='Stop'
$base=$BaseUri.TrimEnd('/');$token=(Get-Content -LiteralPath $TokenFile -Raw).Trim();$auth=@{Authorization="Bearer $token"}
function Assert-True([bool]$Condition,[string]$Message){if(-not$Condition){throw "Phase 9C World/BE failed: $Message"}}
function Invoke-Json([string]$Method,[string]$Path,[hashtable]$Headers,[object]$Body){$p=@{Uri=$base+$Path;Method=$Method;Headers=$Headers;TimeoutSec=15};if($null-ne$Body){$p.ContentType='application/json';$p.Body=$Body|ConvertTo-Json -Depth 60 -Compress};Invoke-RestMethod @p}
function Invoke-ExpectedError([object]$Body,[string]$Code){$r=Invoke-WebRequest -Uri "$base/v0/debug/mutations" -Method Post -Headers $debug -ContentType 'application/json' -Body ($Body|ConvertTo-Json -Depth 60 -Compress) -SkipHttpErrorCheck -TimeoutSec 15;$e=$r.Content|ConvertFrom-Json;Assert-True($e.error-eq$Code)"expected $Code got $($e.error)"}
function Observe([string[]]$Domains,[bool]$Serialized){Invoke-Json POST '/v0/observe/deep' $auth @{perspective='server_authoritative';domains=$Domains;selector=@{chunkRadius=1;entityRadius=0;blocks=@(@{x=$script:x;y=$script:y;z=$script:z})};includeSerializedBlockEntities=$Serialized;includeProviderData=$false;budgets=@{maxBlockEntities=64;maxResponseBytes=524288}}}
$session=Invoke-Json GET '/v0/session' $auth $null;Assert-True($session.target-eq$ExpectedTarget-and$session.inWorld)'Integrated world required'
$player=Invoke-Json GET '/v0/player' $auth $null;$script:x=[math]::Floor($player.x)+3;$script:y=[math]::Floor($player.y)-1;$script:z=[math]::Floor($player.z)
$fingerprint=Invoke-Json GET '/v0/world/fingerprint' $auth $null
$arm=Invoke-Json POST '/v0/debug/arm' $auth @{worldFingerprint=$fingerprint.worldFingerprint;namespaces=@('world','block_entity');ttlMs=60000}
$debug=@{Authorization="Bearer $token";'X-MCP-Debug-Arm'=$arm.debugArmId}
$original=$null;$current=$null
try {
    $before=Observe @('blocks') $false;$block=@($before.server.blocks)[0];$original=$block.blockId;$current=$original
    $blockRef=@($before.resourceRevisionRefs|Where-Object -Property resourceType -eq 'block')[0]
    Invoke-ExpectedError @{operation='world.block.set';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$blockRef;x=$script:x;y=$script:y;z=$script:z;blockId='minecraft:chest';expectedBlockId='minecraft:impossible'} 'VALUE_PRECONDITION_FAILED'
    $setChest=Invoke-Json POST '/v0/debug/mutations' $debug @{operation='world.block.set';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$blockRef;x=$script:x;y=$script:y;z=$script:z;blockId='minecraft:chest';expectedBlockId=$original}
    $current='minecraft:chest';Assert-True(-not[bool]$setChest.before.loadRequested)'Debug Block force-loaded a chunk'
    Invoke-ExpectedError @{operation='world.block.set';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$blockRef;x=$script:x;y=$script:y;z=$script:z;blockId='minecraft:gold_block';expectedBlockId='minecraft:chest'} 'STALE_RESOURCE_REVISION'
    Start-Sleep -Milliseconds 500
    $snapshot=Observe @('block_entities','chunks') $true
    $key="$($snapshot.server.dimension)@$script:x,$script:y,$script:z"
    $be=@($snapshot.server.blockEntities|Where-Object -Property key -eq "$script:x,$script:y,$script:z")[0]
    $beRef=@($snapshot.resourceRevisionRefs|Where-Object{$_.resourceType-eq'block_entity_serialized'-and$_.resourceKey-eq$key})[0]
    Assert-True($null-ne$be-and$null-ne$beRef)'Block Entity serialized token missing'
    $named=Invoke-Json POST '/v0/debug/mutations' $debug @{operation='block_entity.custom_name.set';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$beRef;x=$script:x;y=$script:y;z=$script:z;customName='Phase9C';expectedCustomName=$null;expectedBlockEntityType=$be.type}
    Assert-True([long]$named.afterResourceVersion.revision-ne[long]$named.beforeResourceVersion.revision)'BE revision did not advance'
    $cleared=Invoke-Json POST '/v0/debug/mutations' $debug @{operation='block_entity.custom_name.set';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$named.afterResourceVersion;x=$script:x;y=$script:y;z=$script:z;customName=$null;expectedCustomName='Phase9C';expectedBlockEntityType=$be.type}
    Start-Sleep -Milliseconds 500
    $currentBlock=Observe @('blocks') $false;$currentRef=@($currentBlock.resourceRevisionRefs|Where-Object -Property resourceType -eq 'block')[0]
    $restored=Invoke-Json POST '/v0/debug/mutations' $debug @{operation='world.block.set';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$currentRef;x=$script:x;y=$script:y;z=$script:z;blockId=$original;expectedBlockId='minecraft:chest'}
    $current=$original
    [pscustomobject]@{Result='PASS';Target=$ExpectedTarget;Block='PASS';BlockEntity='PASS';NoLoad='PASS';ValuePrecondition='PASS';StaleToken='PASS';BlockRevision="$($setChest.beforeResourceVersion.revision)->$($setChest.afterResourceVersion.revision)";BlockEntityRevision="$($named.beforeResourceVersion.revision)->$($named.afterResourceVersion.revision)->$($cleared.afterResourceVersion.revision)";Synchronization=$named.synchronization;Cleanup='PASS'}
}
finally {
    if($null-ne$original-and$current-ne$original){
        try {$o=Observe @('blocks') $false;$r=@($o.resourceRevisionRefs|Where-Object -Property resourceType -eq 'block')[0];$b=@($o.server.blocks)[0];[void](Invoke-Json POST '/v0/debug/mutations' $debug @{operation='world.block.set';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$r;x=$script:x;y=$script:y;z=$script:z;blockId=$original;expectedBlockId=$b.blockId})}catch{}
    }
}
