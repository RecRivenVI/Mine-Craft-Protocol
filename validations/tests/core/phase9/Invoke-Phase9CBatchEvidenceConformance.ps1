[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget)
$ErrorActionPreference='Stop'
$base=$BaseUri.TrimEnd('/');$token=(Get-Content -LiteralPath $TokenFile -Raw).Trim();$auth=@{Authorization="Bearer $token"}
function Assert-True([bool]$Condition,[string]$Message){if(-not$Condition){throw "Phase 9C Batch/Evidence failed: $Message"}}
function Invoke-Json([string]$Method,[string]$Path,[hashtable]$Headers,[object]$Body){$p=@{Uri=$base+$Path;Method=$Method;Headers=$Headers;TimeoutSec=20};if($null-ne$Body){$p.ContentType='application/json';$p.Body=$Body|ConvertTo-Json -Depth 80 -Compress};Invoke-RestMethod @p}
function ObserveBlocks($Positions){Invoke-Json POST '/v0/observe/deep' $auth @{perspective='server_authoritative';domains=@('blocks');selector=@{chunkRadius=0;entityRadius=0;blocks=$Positions};includeProviderData=$false;budgets=@{maxResponseBytes=524288}}}
function Build-Items($Snapshot,$OriginalSnapshot,$Fingerprint,[string]$TargetBlock){$items=@();foreach($block in @($Snapshot.server.blocks)){$key="$($Snapshot.server.dimension)@$($block.key)";$ref=@($Snapshot.resourceRevisionRefs|Where-Object{$_.resourceType-eq'block'-and$_.resourceKey-eq$key})[0];$parts=$block.key-split',';$originalBlock=@($OriginalSnapshot.server.blocks|Where-Object -Property key -eq $block.key)[0];$destination=if($TargetBlock){$TargetBlock}else{$originalBlock.blockId};if($destination-eq$block.blockId){continue};$items+=@{operation='world.block.set';worldFingerprint=$Fingerprint;expectedResourceVersion=$ref;x=[int]$parts[0];y=[int]$parts[1];z=[int]$parts[2];blockId=$destination;expectedBlockId=$block.blockId}};return ,$items}
function Start-Batch($Headers,$Items,[int]$PerTick=4,[string]$Policy='CONTINUE_ON_FAILURE'){Invoke-Json POST '/v0/debug/batches' $Headers @{items=$Items;failurePolicy=$Policy;maxPerTickMutations=$PerTick;maxTotalDurationMs=30000}}
function Wait-Operation([string]$Id){Invoke-Json POST "/v0/operations/$Id/wait" $auth @{timeoutMs=30000}}
$session=Invoke-Json GET '/v0/session' $auth $null;Assert-True($session.target-eq$ExpectedTarget-and$session.inWorld)'Integrated world required'
$player=Invoke-Json GET '/v0/player' $auth $null;$baseX=[math]::Floor($player.x)-8;$y=[math]::Floor($player.y)-1;$baseZ=[math]::Floor($player.z)+7
$positions=@();0..29|ForEach-Object{$positions+=@{x=$baseX+($_%10);y=$y;z=$baseZ+[math]::Floor($_/10)}}
$fingerprint=Invoke-Json GET '/v0/world/fingerprint' $auth $null
$arm=Invoke-Json POST '/v0/debug/arm' $auth @{worldFingerprint=$fingerprint.worldFingerprint;namespaces=@('world','provider');ttlMs=60000}
$debug=@{Authorization="Bearer $token";'X-MCP-Debug-Arm'=$arm.debugArmId}
$original=ObserveBlocks $positions
try {
    $tenPositions=$positions[0..9];$tenOriginal=ObserveBlocks $tenPositions
    $tenItems=Build-Items $tenOriginal $tenOriginal $fingerprint.worldFingerprint 'minecraft:gold_block'
    foreach($index in 0..($tenItems.Count-1)){if($tenOriginal.server.blocks[$index].blockId-eq'minecraft:gold_block'){$tenItems[$index].blockId='minecraft:diamond_block'}}
    $watch=[Diagnostics.Stopwatch]::StartNew();$tenStart=Start-Batch $debug $tenItems 4 'STOP_ON_FAILURE';$tenTerminal=Wait-Operation $tenStart.operationId;$watch.Stop()
    Assert-True($tenTerminal.state-eq'completed'-and$tenTerminal.result.succeededItems-eq10-and$tenTerminal.result.failedItems-eq0)'10-item batch'
    Start-Sleep -Milliseconds 250;$tenCurrent=ObserveBlocks $tenPositions;$restore=Build-Items $tenCurrent $tenOriginal $fingerprint.worldFingerprint '';$restoreTerminal=Wait-Operation (Start-Batch $debug $restore 4).operationId;Assert-True($restoreTerminal.result.failedItems-eq0)'10-item cleanup'

    $cancelOriginal=ObserveBlocks $positions;$cancelItems=Build-Items $cancelOriginal $cancelOriginal $fingerprint.worldFingerprint 'minecraft:diamond_block'
    foreach($index in 0..($cancelItems.Count-1)){if($cancelOriginal.server.blocks[$index].blockId-eq'minecraft:diamond_block'){$cancelItems[$index].blockId='minecraft:gold_block'}}
    $cancelStart=Start-Batch $debug $cancelItems 1;Start-Sleep -Milliseconds 120;$cancelled=Invoke-Json DELETE "/v0/operations/$($cancelStart.operationId)" $auth $null
    $sequenceAtCancel=(Invoke-Json GET '/v0/debug/evidence' $auth $null).lastDebugMutationSequence;Start-Sleep -Seconds 2;$sequenceAfter=(Invoke-Json GET '/v0/debug/evidence' $auth $null).lastDebugMutationSequence
    Assert-True($cancelled.state-eq'cancelled'-and$sequenceAfter-eq$sequenceAtCancel-and$cancelled.result.postCancelMutations-eq0)'post-cancel mutation barrier'
    $cancelCurrent=ObserveBlocks $positions;$cancelRestore=Build-Items $cancelCurrent $cancelOriginal $fingerprint.worldFingerprint '';if($cancelRestore.Count){$cleanup=Wait-Operation (Start-Batch $debug $cancelRestore 4).operationId;Assert-True($cleanup.result.failedItems-eq0)'cancel cleanup'}

    Start-Sleep -Seconds 3
    $provider=Invoke-Json POST '/v0/observe/deep' $auth @{perspective='server_authoritative';domains=@('providers');includeProviderData=$true;providerIds=@('minecraft_protocol_probe:safe');providerQuery=@{probe='phase9c-cancel'};budgets=@{maxProviders=1;maxResponseBytes=131072}}
    $providerRef=@($provider.resourceRevisionRefs|Where-Object{$_.resourceType-eq'provider'-and$_.resourceKey-eq'minecraft_protocol_probe:safe'})[0]
    $providerStart=Start-Batch $debug @(@{operation='provider.mutate';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$providerRef;providerId='minecraft_protocol_probe:safe';mutation=@{operation='temperature.delayed_set';value=99.0;delayMs=500}}) 1
    Start-Sleep -Milliseconds 50;[void](Invoke-Json DELETE "/v0/operations/$($providerStart.operationId)" $auth $null);Start-Sleep -Milliseconds 700
    $providerAfter=Invoke-Json POST '/v0/observe/deep' $auth @{perspective='server_authoritative';domains=@('providers');includeProviderData=$true;providerIds=@('minecraft_protocol_probe:safe');providerQuery=@{probe='phase9c-cancel-after'};budgets=@{maxProviders=1;maxResponseBytes=131072}}
    $providerAfterRef=@($providerAfter.resourceRevisionRefs|Where-Object{$_.resourceType-eq'provider'-and$_.resourceKey-eq'minecraft_protocol_probe:safe'})[0]
    Assert-True ([long]$providerAfterRef.revision -eq [long]$providerRef.revision) 'cancelled Provider mutated after cancellation'

    $cleanStart=Invoke-Json POST '/v0/debug/evidence/act/start' $auth $null;$cleanAct=Invoke-Json POST '/v0/debug/evidence/act/finish' $auth @{actId=$cleanStart.actId};Assert-True (-not $cleanAct.contaminated -and $cleanAct.gameplayEvidence -eq 'gameplay') 'clean Act evidence'
    $onePosition=@($positions[0]);$oneBefore=ObserveBlocks $onePosition;$oneBlock=@($oneBefore.server.blocks)[0];$oneKey="$($oneBefore.server.dimension)@$($oneBlock.key)";$oneRef=@($oneBefore.resourceRevisionRefs|Where-Object{$_.resourceType-eq'block'-and$_.resourceKey-eq$oneKey})[0];$oneTarget=if($oneBlock.blockId-eq'minecraft:emerald_block'){'minecraft:stone'}else{'minecraft:emerald_block'}
    $actStart=Invoke-Json POST '/v0/debug/evidence/act/start' $auth $null;$oneMutation=Invoke-Json POST '/v0/debug/mutations' $debug @{operation='world.block.set';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$oneRef;x=$onePosition.x;y=$onePosition.y;z=$onePosition.z;blockId=$oneTarget;expectedBlockId=$oneBlock.blockId};$dirtyAct=Invoke-Json POST '/v0/debug/evidence/act/finish' $auth @{actId=$actStart.actId};Assert-True ($dirtyAct.contaminated -and $dirtyAct.gameplayEvidence -eq 'invalid_for_acceptance') 'Debug contamination window'
    [void](Invoke-Json POST '/v0/debug/mutations' $debug @{operation='world.block.set';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$oneMutation.afterResourceVersion;x=$onePosition.x;y=$onePosition.y;z=$onePosition.z;blockId=$oneBlock.blockId;expectedBlockId=$oneTarget})

    Start-Sleep -Seconds 5
    [void](Invoke-Json POST '/v0/debug/disarm' $auth $null)
    $shortArm=Invoke-Json POST '/v0/debug/arm' $auth @{worldFingerprint=$fingerprint.worldFingerprint;namespaces=@('world');ttlMs=1000}
    $shortHeaders=@{Authorization="Bearer $token";'X-MCP-Debug-Arm'=$shortArm.debugArmId}
    $expiryOriginal=ObserveBlocks $positions;$expiryItems=Build-Items $expiryOriginal $expiryOriginal $fingerprint.worldFingerprint 'minecraft:lapis_block'
    foreach($index in 0..($expiryItems.Count-1)){if($expiryOriginal.server.blocks[$index].blockId-eq'minecraft:lapis_block'){$expiryItems[$index].blockId='minecraft:iron_block'}}
    $expiryStart=Start-Batch $shortHeaders $expiryItems 1;$expiryTerminal=Wait-Operation $expiryStart.operationId
    Assert-True ($expiryTerminal.state -eq 'completed' -and $expiryTerminal.result.status -eq 'partial' -and $expiryTerminal.result.reason -eq 'DEBUG_NOT_ARMED') 'Arm expiry did not stop the batch'
    $arm=Invoke-Json POST '/v0/debug/arm' $auth @{worldFingerprint=$fingerprint.worldFingerprint;namespaces=@('world','provider');ttlMs=60000}
    $debug=@{Authorization="Bearer $token";'X-MCP-Debug-Arm'=$arm.debugArmId}
    $expiryCurrent=ObserveBlocks $positions;$expiryRestore=Build-Items $expiryCurrent $expiryOriginal $fingerprint.worldFingerprint '';if($expiryRestore.Count){$expiryCleanup=Wait-Operation (Start-Batch $debug $expiryRestore 4).operationId;Assert-True ($expiryCleanup.result.failedItems -eq 0) 'Arm-expiry cleanup'}

    [pscustomobject]@{Result='PASS';Target=$ExpectedTarget;TenItems='PASS';TenItemDurationMs=$watch.ElapsedMilliseconds;MaxPerTick=4;Cancellation='PASS';PostCancelMutations=0;CancelledCompletedItems=$cancelled.result.completedItems;ProviderCancellation='PASS';ArmExpiry='PASS';ArmExpiryCompletedItems=$expiryTerminal.result.completedItems;EvidenceClean='PASS';EvidenceContaminated='PASS';FailurePolicies='STOP_ON_FAILURE,CONTINUE_ON_FAILURE';Cleanup='PASS'}
}
finally {
    try {$current=ObserveBlocks $positions;$remaining=Build-Items $current $original $fingerprint.worldFingerprint '';if($remaining.Count){[void](Wait-Operation (Start-Batch $debug $remaining 4).operationId)}}catch{}
}
