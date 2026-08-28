[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget,
    [switch]$HasPhase9ADiagnostics)
$ErrorActionPreference='Stop'
$base=$BaseUri.TrimEnd('/');$token=(Get-Content $TokenFile -Raw).Trim();$auth=@{Authorization="Bearer $token"}
function A([bool]$c,[string]$m){if(-not$c){throw "Phase 9B.1 revision failed: $m"}}
function J([string]$m,[string]$p,$h,$b){$q=@{Uri=$base+$p;Method=$m;Headers=$h};if($null-ne$b){$q.ContentType='application/json';$q.Body=$b|ConvertTo-Json -Depth 50 -Compress};Invoke-RestMethod @q}
function Ref($response,[string]$type,[string]$key=''){$values=@($response.resourceRevisionRefs|Where-Object -Property resourceType -eq $type);if($key){$values=@($values|Where-Object -Property resourceKey -eq $key)};if($values.Count){return $values[0]};return $null}
function Observe($projection,[bool]$serialized){J POST '/v0/observe/deep' $auth @{perspective='server_authoritative';domains=@('player','entities','blocks','block_entities','chunks','menu');selector=@{chunkRadius=0;entityRadius=16;blocks=@(@{x=$script:x;y=$script:y;z=$script:z})};projection=$projection;includeSerializedBlockEntities=$serialized;includeProviderData=$false;budgets=@{maxEntities=32;maxBlockEntities=32;maxResponseBytes=524288}}}
$session=J GET '/v0/session' $auth $null;A($session.target-eq$ExpectedTarget-and$session.inWorld)'target/world'
$player=J GET '/v0/player' $auth $null;$script:x=[math]::Floor($player.x)+2;$script:y=[math]::Floor($player.y)-1;$script:z=[math]::Floor($player.z)
$lease=$null;$debugHeaders=$null;$originalBlock=$null;$currentFixtureBlock=$null;$pigs=@()
J POST '/v0/control/emergency-release' $auth $null|Out-Null
$lease=J POST '/v0/control/acquire' $auth @{ttlMs=60000}
$leaseHeaders=@{Authorization="Bearer $token";'X-MCP-Control-Lease'=$lease.leaseId}
if($HasPhase9ADiagnostics){
 $fingerprint=J GET '/v0/world/fingerprint' $auth $null
 $arm=J POST '/v0/debug/arm' $leaseHeaders @{worldFingerprint=$fingerprint.worldFingerprint;ttlMs=60000}
 $debugHeaders=@{Authorization="Bearer $token";'X-MCP-Control-Lease'=$lease.leaseId;'X-MCP-Debug-Arm'=$arm.debugArmId}
 $originalBlock=(J GET "/v0/world/block?x=$script:x&y=$script:y&z=$script:z" $auth $null).blockId
 J POST '/v0/debug/world/block' $debugHeaders @{x=$script:x;y=$script:y;z=$script:z;blockId='minecraft:chest';expectedBlockId=$originalBlock}|Out-Null
 $currentFixtureBlock='minecraft:chest'
 1..2|ForEach-Object{$pigs+=@(J POST '/v0/debug/phase9a/scenario' $debugHeaders @{action='entity_spawn_pig'}).spawnedEntityUuid}
}
J POST '/v0/input/key' $leaseHeaders @{key=256;scanCode=1;action=1;modifiers=0}|Out-Null
J POST '/v0/input/key' $leaseHeaders @{key=256;scanCode=1;action=0;modifiers=0}|Out-Null
Start-Sleep -Milliseconds 250
try{
 $minimal=Observe @{playerFields=@('identity');entityFields=@('identity')} $false
 $different=Observe @{playerFields=@('inventory');entityFields=@('living')} $false
 $maximum=Observe @{playerFields=@('identity','transform','environment','vitals','authority','inventory','attributes','effects','relationships','menu','dimension','respawn');entityFields=@('identity','transform','living','equipment','effects','attributes','relationships','common_state')} $false
 $same=Observe @{playerFields=@('identity');entityFields=@('identity')} $false
 $checked=0
 foreach($type in @('player','menu','entity','chunk','block_entity')){foreach($ref in @($minimal.resourceRevisionRefs|Where-Object -Property resourceType -eq $type)){$b=Ref $different $type $ref.resourceKey;$c=Ref $maximum $type $ref.resourceKey;$d=Ref $same $type $ref.resourceKey;if($b-and$c-and$d){A(([long]$ref.revision-eq[long]$b.revision)-and([long]$ref.revision-eq[long]$c.revision)-and([long]$ref.revision-eq[long]$d.revision))"$type projection invariance $($ref.resourceKey)";$checked++}}}
 A($null-ne(Ref $minimal 'player'))'player revision absent';A($null-ne(Ref $minimal 'menu'))'menu revision absent';A($null-ne(Ref $minimal 'chunk'))'chunk revision absent'
 if($HasPhase9ADiagnostics){A($null-ne(Ref $minimal 'entity' $pigs[0]))'entity revision absent';A($null-ne(Ref $minimal 'block_entity' "$script:x,$script:y,$script:z"))'block entity revision absent'}
 $serialized=Observe @{playerFields=@('identity');entityFields=@('identity')} $true;$afterSerialized=Observe @{playerFields=@('identity');entityFields=@('identity')} $false
 if($HasPhase9ADiagnostics){$beKey="$script:x,$script:y,$script:z";$baseBefore=Ref $minimal 'block_entity' $beKey;$baseDuring=Ref $serialized 'block_entity' $beKey;$baseAfter=Ref $afterSerialized 'block_entity' $beKey;A(([long]$baseBefore.revision-eq[long]$baseDuring.revision)-and([long]$baseBefore.revision-eq[long]$baseAfter.revision))'serialized option changed base BE revision';A($null-ne(Ref $serialized 'block_entity_serialized' $beKey))'serialized-state revision absent'}
 $mutation='NOT_APPLICABLE'
 if($HasPhase9ADiagnostics){
  $beforePlayer=Observe @{playerFields=@('inventory');entityFields=@('common_state')} $true;J POST '/v0/debug/phase9a/scenario' $debugHeaders @{action='inventory_add_stone'}|Out-Null;$afterPlayer=Observe @{playerFields=@('inventory');entityFields=@('common_state')} $true;A([long](Ref $beforePlayer 'player').revision-ne[long](Ref $afterPlayer 'player').revision)'player state revision did not change';A([long](Ref $beforePlayer 'menu').revision-ne[long](Ref $afterPlayer 'menu').revision)'menu revision did not change';J POST '/v0/debug/phase9a/scenario' $debugHeaders @{action='inventory_remove_stone'}|Out-Null
  $entityBefore=Observe @{playerFields=@('identity');entityFields=@('common_state')} $true;J POST '/v0/debug/entity/state' $debugHeaders @{entityUuid=$pigs[0];state='no_gravity';value=$true}|Out-Null;$entityAfter=Observe @{playerFields=@('identity');entityFields=@('common_state')} $true;A([long](Ref $entityBefore 'entity' $pigs[0]).revision-ne[long](Ref $entityAfter 'entity' $pigs[0]).revision)'changed entity revision';A([long](Ref $entityBefore 'entity' $pigs[1]).revision-eq[long](Ref $entityAfter 'entity' $pigs[1]).revision)'unrelated entity revision changed';A([long](Ref $entityBefore 'player').revision-eq[long](Ref $entityAfter 'player').revision)'unrelated player revision changed'
  $beKey="$script:x,$script:y,$script:z";$beforeBe=Observe @{playerFields=@('identity');entityFields=@('identity')} $true;J POST '/v0/debug/world/block' $debugHeaders @{x=$script:x;y=$script:y;z=$script:z;blockId='minecraft:trapped_chest';expectedBlockId='minecraft:chest'}|Out-Null;$currentFixtureBlock='minecraft:trapped_chest';$afterBe=Observe @{playerFields=@('identity');entityFields=@('identity')} $true;A([long](Ref $beforeBe 'block_entity' $beKey).revision-ne[long](Ref $afterBe 'block_entity' $beKey).revision)'block entity base revision';A([long](Ref $beforeBe 'block_entity_serialized' $beKey).revision-ne[long](Ref $afterBe 'block_entity_serialized' $beKey).revision)'block entity serialized revision'
  $chunkKey=(Ref $afterBe 'chunk').resourceKey;$chunkBefore=Ref $afterBe 'chunk' $chunkKey;J POST '/v0/debug/world/block' $debugHeaders @{x=$script:x;y=$script:y;z=$script:z;blockId='minecraft:stone';expectedBlockId='minecraft:trapped_chest'}|Out-Null;$currentFixtureBlock='minecraft:stone';$afterChunk=Observe @{playerFields=@('identity');entityFields=@('identity')} $false;A([long]$chunkBefore.revision-ne[long](Ref $afterChunk 'chunk' $chunkKey).revision)'chunk semantic revision';$mutation='PASS'
 }
 [pscustomobject]@{Result='PASS';Target=$ExpectedTarget;ProjectionInvariantResources=$checked;SerializationInvariant='PASS';StateMutation=$mutation;TrackerBound=$minimal.metadata.revisionTracker.entryBound;SessionEpoch=$minimal.sessionEpoch}
}finally{
 if($HasPhase9ADiagnostics-and$debugHeaders){
  foreach($pig in $pigs){try{J POST '/v0/debug/phase9a/scenario' $debugHeaders @{action='entity_remove';entityUuid=$pig}|Out-Null}catch{}}
  if($currentFixtureBlock){try{J POST '/v0/debug/world/block' $debugHeaders @{x=$script:x;y=$script:y;z=$script:z;blockId=$originalBlock;expectedBlockId=$currentFixtureBlock}|Out-Null}catch{}}
 }
 if($leaseHeaders){
  try{J POST '/v0/input/key' $leaseHeaders @{key=256;scanCode=1;action=1;modifiers=0}|Out-Null;J POST '/v0/input/key' $leaseHeaders @{key=256;scanCode=1;action=0;modifiers=0}|Out-Null}catch{}
  try{J POST '/v0/control/release' $leaseHeaders $null|Out-Null}catch{J POST '/v0/control/emergency-release' $auth $null|Out-Null}
 }
}
