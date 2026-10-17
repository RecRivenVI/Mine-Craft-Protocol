function Add-ShowcaseEvidence([object]$Value) {
    $script:caseEvidence.Add($Value)
}
function Wait-ShowcaseCondition([scriptblock]$Read,[scriptblock]$Accept,[string]$Name,[int]$TimeoutMs=10000) {
    $until=[DateTime]::UtcNow.AddMilliseconds($TimeoutMs)
    do {
        Update-ShowcaseLease
        Assert-Showcase (-not(Get-ShowcaseMode).reconsentRequired) '用户手动结束控制；停止案例，不重新接管。'
        $value=& $Read
        if(& $Accept $value){return $value}
        Start-Sleep -Milliseconds 100
    }while([DateTime]::UtcNow-lt$until)
    throw "Showcase condition timed out: $Name"
}
function Show-WorldObservation {
    Enter-ShowcaseWorld;Set-ShowcaseMode READ|Out-Null
    Write-Host '  READ / USER：有界客户端模型，不把墙后缓存伪称画面可见，不加载区块。'
    $scene=Invoke-ShowcaseApi GET '/v0/world/scene?radius=2&blockLimit=64&entityLimit=8'
    Assert-Showcase ($scene.available-and$scene.dataCategory-eq'USER'-and$scene.authority-eq'CLIENT_KNOWN'-and$scene.chunkLoadRequested-eq$false) 'bounded USER scene unavailable'
    $player=Invoke-ShowcaseApi GET '/v0/player'
    Wait-ShowcaseHold
    Write-Host '  READ / DEBUG：独立读取权限；服务端数据不混进 USER。没有输入 Lease 或写 Arm。'
    $server=Invoke-ShowcaseApi GET '/v0/server/player?category=DEBUG'
    Assert-Showcase ($server.available-and$server.dataCategory-eq'DEBUG') 'explicit DEBUG server read unavailable'
    Add-ShowcaseEvidence @{case='world-observe';userAuthority=$scene.authority;userBlocks=@($scene.blocks).Count;userEntities=@($scene.entities).Count;debugCategory=$server.dataCategory;chunkLoadRequested=$scene.chunkLoadRequested;readMode='READ';inputLease=$false;act='observation_only';playerPosition=@($player.x,$player.y,$player.z)}
    Wait-ShowcaseHold
}
function Get-ShowcaseBlockSnapshot($Position) {
    $snapshot=Invoke-ShowcaseApi POST '/v0/observe/deep' @{category='DEBUG';perspective='server_authoritative';domains=@('blocks');selector=@{chunkRadius=0;entityRadius=0;blocks=@($Position)};includeSerializedBlockEntities=$false;includeProviderData=$false}
    $key="$($Position.x),$($Position.y),$($Position.z)"
    $blocks=@($snapshot.server.blocks|Where-Object key -eq $key)
    $refs=@($snapshot.resourceRevisionRefs|Where-Object {$_.resourceType-eq'block'-and$_.resourceKey-eq"$($snapshot.server.dimension)@$key"})
    Assert-Showcase ($blocks.Count-eq1-and$refs.Count-eq1) 'typed block snapshot/version unavailable'
    @{block=$blocks[0];version=$refs[0]}
}
function Set-ShowcaseBlock($Position,[string]$Expected,[string]$Destination,[string]$Fingerprint,$ExpectedVersion=$null) {
    Set-ShowcaseMode OPERATE|Out-Null
    $current=Invoke-ShowcaseApi GET '/v0/world/fingerprint?category=DEBUG'
    Assert-Showcase ($current.worldFingerprint-eq$Fingerprint) 'world changed; no fixture mutation'
    $snapshot=Get-ShowcaseBlockSnapshot $Position
    Assert-Showcase ($snapshot.block.blockId-eq$Expected) 'fixture changed; refusing overwrite'
    $arm=Invoke-ShowcaseApi POST '/v0/debug/arm' @{worldFingerprint=$Fingerprint;namespaces=@('world');ttlMs=10000}
    $headers=$script:auth.Clone();$headers['X-MCP-Debug-Arm']=$arm.debugArmId
    try {
        $version=if($ExpectedVersion){$ExpectedVersion}else{$snapshot.version}
        Invoke-ShowcaseApi POST '/v0/debug/mutations' @{operation='world.block.set';worldFingerprint=$Fingerprint;expectedResourceVersion=$version;x=$Position.x;y=$Position.y;z=$Position.z;blockId=$Destination;expectedBlockId=$Expected} $headers
    }finally{Invoke-ShowcaseApi POST '/v0/debug/disarm'|Out-Null}
}
function Restore-ShowcaseWorldFixture {
    if(-not$script:worldFixture){return}
    Assert-Showcase (-not(Get-ShowcaseMode).reconsentRequired) '人工退出后不重新接管；专用世界夹具保留，等待明确同意后清理。'
    Set-ShowcaseMode OPERATE|Out-Null
    $fingerprint=Invoke-ShowcaseApi GET '/v0/world/fingerprint?category=DEBUG'
    Assert-Showcase ($fingerprint.worldFingerprint-eq$script:worldFixture.fingerprint) 'cleanup world changed; retain fixture ownership without mutation'
    $session=Invoke-ShowcaseApi GET '/v0/session'
    if($session.screenClass-and$session.screenClass-ne'none'){
        Assert-Showcase ($session.screenClass-match'CraftingScreen$') 'unknown fixture cleanup Screen; preserve it without input'
        Start-ShowcaseTakeover;Close-ShowcaseScreen;Set-ShowcaseMode OPERATE|Out-Null
    }
    $current=Get-ShowcaseBlockSnapshot $script:worldFixture.position
    if($current.block.blockId-ne'minecraft:air'){
        Assert-Showcase ([bool]$script:worldFixture.afterVersion) 'fixture mutation result is unknown; preserve it for explicit recovery'
        Set-ShowcaseBlock $script:worldFixture.position 'minecraft:crafting_table' 'minecraft:air' $script:worldFixture.fingerprint $script:worldFixture.afterVersion|Out-Null
    }
    $script:worldFixture=$null
    Set-ShowcaseMode READ|Out-Null
}
function Show-WorldInteraction {
    Enter-ShowcaseWorld;Start-ShowcaseTakeover;Close-ShowcaseScreen
    $player=Invoke-ShowcaseApi GET '/v0/player'
    $position=$null
    foreach($offset in @(@(1,0),@(-1,0),@(0,1),@(0,-1))){
        $candidate=@{x=[int][Math]::Floor($player.x)+$offset[0];y=[int][Math]::Floor($player.y);z=[int][Math]::Floor($player.z)+$offset[1]}
        $block=Invoke-ShowcaseApi GET "/v0/world/block?x=$($candidate.x)&y=$($candidate.y)&z=$($candidate.z)"
        if($block.available-and$block.block-eq'minecraft:air'){$position=$candidate;break}
    }
    Assert-Showcase ($null-ne$position) 'no nearby known air cell; do not replace terrain or unknown blocks'
    Set-ShowcaseMode OPERATE|Out-Null
    $fingerprint=(Invoke-ShowcaseApi GET '/v0/world/fingerprint?category=DEBUG').worldFingerprint
    Write-Host '  Arrange / DEBUG_PRIVILEGED：只在已观察的空气位置布置一个工作台，使用 Arm 与资源/值前置条件。'
    $script:worldFixture=@{position=$position;fingerprint=$fingerprint}
    try {
        $mutation=Set-ShowcaseBlock $position 'minecraft:air' 'minecraft:crafting_table' $fingerprint
        $script:worldFixture.afterVersion=$mutation.afterResourceVersion
        Start-ShowcaseTakeover
        $target=Wait-ShowcaseCondition {Invoke-ShowcaseApi GET "/v0/world/block?x=$($position.x)&y=$($position.y)&z=$($position.z)"} {$args[0].block-eq'minecraft:crafting_table'-and$args[0].targetRef} 'client receives fixture'
        Write-Host '  Act / PLAYTEST：用 USER targetRef 发现、瞄准并普通使用；不传鼠标坐标，不写 yaw/pitch。'
        $operation=Invoke-ShowcasePipeline @(@{type='world.action';targetRef=$target.targetRef;action='use'})
        $input=$operation.result.steps[0].result
        Assert-Showcase ($input.evidenceAuthority-eq'PLAYTEST'-and$input.directMutationUsed-eq$false-and$input.effectConfirmed-eq$false) 'world Act provenance mismatch'
        $assertion=Invoke-ShowcaseApi POST '/v0/assert' @{condition=@{type='screen';category='USER';classContains='CraftingScreen'}}
        Assert-Showcase $assertion.passed 'normal use did not open a crafting menu'
        Add-ShowcaseEvidence @{case='world-interact';arrange='DEBUG_PRIVILEGED';act='PLAYTEST';targetSource='USER_CLIENT_MODEL';targetRef=$target.targetRef;operationId=$operation.operationId;assertion=$assertion;noDirectWorldAct=$true;handWrittenAimCoordinates=$false}
        Wait-ShowcaseHold
    }finally{Restore-ShowcaseWorldFixture}
}
function Show-WaitEvidence {
    Enter-ShowcaseWorld;Set-ShowcaseMode READ|Out-Null
    $recording=Invoke-ShowcaseApi POST '/v0/recordings' @{category='USER';captureFrames=$true;intervalMs=1000;durationMs=60000;maxSamples=6;stateReads=@(@{providerId='minecraft:client/player'});contactSheet=@{enabled=$true;columns=3}}
    $stopped=$false
    try {
        Write-Host '  Recording start → 真实背包操作 → 条件 Wait / Assert；默认帧不包含 Operator UI。'
        Open-ShowcaseInventory
        $condition=@{type='ui.exists';category='USER';selector=@{role='slot';nth=0}}
        $waited=Invoke-ShowcaseApi POST '/v0/wait/until' @{condition=$condition;timeoutMs=10000}
        $asserted=Invoke-ShowcaseApi POST '/v0/assert' @{condition=$condition}
        Assert-Showcase $asserted.passed 'inventory Slot assertion failed'
        $frame=Join-Path $script:instance 'core-feature-frame.png'
        Invoke-WebRequest ($script:base+'/v0/capture') -Headers $script:auth -TimeoutSec 30 -OutFile $frame|Out-Null
        Wait-ShowcaseHold
        Close-ShowcaseScreen
        Show-ShowcaseTextDraft
        Set-ShowcaseMode READ|Out-Null
        Invoke-ShowcaseApi DELETE "/v0/recordings/$($recording.recordingId)"|Out-Null;$stopped=$true
        $finished=Wait-ShowcaseCondition {Invoke-ShowcaseApi GET "/v0/recordings/$($recording.recordingId)"} {$args[0].artifactReady} 'Recording artifact finalization' 20000
        $bundle=Join-Path $script:instance 'core-feature-evidence.zip'
        Invoke-WebRequest ($script:base+"/v0/recordings/$($recording.recordingId)/artifact") -Headers $script:auth -TimeoutSec 30 -OutFile $bundle|Out-Null
        $archive=[IO.Compression.ZipFile]::OpenRead($bundle)
        try{$contents=Test-ShowcaseArtifact $archive}finally{$archive.Dispose()}
        Add-ShowcaseEvidence @{case='wait-evidence';waitResult=$waited;assertResult=$asserted;recordingId=$recording.recordingId;recordingStatus=$finished.status;frame=[IO.Path]::GetRelativePath($repo,$frame).Replace('\','/');artifact=[IO.Path]::GetRelativePath($repo,$bundle).Replace('\','/');sha256=(Get-FileHash $bundle -Algorithm SHA256).Hash.ToLowerInvariant();contents=$contents;operatorUiPolicy='excluded_by_product_default';operatorUiPixelAssertion='NOT_PERFORMED'}
        Write-Host "  Recording stop / Artifact：$bundle"
    }finally{if(-not$stopped){Invoke-ShowcaseApi DELETE "/v0/recordings/$($recording.recordingId)"|Out-Null}}
}
function Show-ShowcaseTextDraft {
    Write-Host '  TAKEOVER / USER：语义文本框输入一段聊天草稿，然后取消，不发送消息或命令。'
    Send-ShowcaseKey 84;Wait-ShowcaseScreen 'ChatScreen'
    $fields=@(Get-ShowcaseNodes (Get-ShowcaseTree)|Where-Object role -eq 'text_field')
    Assert-Showcase ($fields.Count-eq1-and$fields[0].value-eq'') 'existing or ambiguous Chat draft; do not overwrite it'
    $draft='Showcase draft - not sent'
    Invoke-ShowcaseApi POST '/v0/ui/action' @{selector=@{nodeId=$fields[0].nodeId};action='type_text';text=$draft;replace=$false} (Get-ShowcaseControlHeaders)|Out-Null
    $tree=Get-ShowcaseTree
    $typed=@(Get-ShowcaseNodes $tree|Where-Object role -eq 'text_field')
    Assert-Showcase ($tree.screenClass-match'ChatScreen$'-and$typed.Count-eq1-and$typed[0].value-eq$draft) 'native text entry did not match the owned draft'
    Wait-ShowcaseHold
    for($i=0;$i-lt3;$i++){
        $tree=Get-ShowcaseTree
        if(-not$tree.screenClass-or$tree.screenClass-eq'none'){
            Add-ShowcaseEvidence @{case='wait-evidence';input='semantic_type_text';draftCancelled=$true;messageSubmitted=$false;commandSubmitted=$false}
            return
        }
        $fields=@(Get-ShowcaseNodes $tree|Where-Object role -eq 'text_field')
        Assert-Showcase ($tree.screenClass-match'ChatScreen$'-and$fields.Count-eq1-and$fields[0].value-eq$draft) 'Chat draft or Screen changed; refusing automatic cancellation'
        Send-ShowcaseKey 256
    }
    throw 'owned Showcase draft did not close within the native Esc budget'
}
function Test-ShowcaseArtifact([IO.Compression.ZipArchive]$Archive) {
    $entries=@($Archive.Entries)
    Assert-Showcase ($entries.Count-le1024-and($entries|Measure-Object Length -Sum).Sum-le256MB) 'Artifact exceeds inspection budget'
    $names=@($entries.FullName)
    Assert-Showcase (@($names|Sort-Object -Unique).Count-eq$names.Count) 'duplicate Artifact members'
    foreach($entry in $entries){Assert-Showcase ($entry.Length-le32MB-and$entry.FullName-notmatch'(^/|\\|(^|/)\.\.(/|$))') 'unsafe or oversized Artifact member'}
    foreach($required in @('manifest.json','checksums.json','frame-index.json','timeline/timeline.ndjson','derivatives/contact-sheet.png')){Assert-Showcase ($required-in$names) "Artifact missing $required"}
    $documents=@{}
    foreach($name in @('manifest.json','checksums.json')){
        $reader=[IO.StreamReader]::new($Archive.GetEntry($name).Open())
        try{$documents[$name]=$reader.ReadToEnd()|ConvertFrom-Json -AsHashtable}finally{$reader.Dispose()}
    }
    $manifest=$documents['manifest.json'];$checksums=$documents['checksums.json']
    Assert-Showcase ($manifest.dataCategory-eq'USER'-and$manifest.writtenFrames-gt0-and$manifest.writtenStates-gt0) 'Artifact lacks USER frames or structured states'
    foreach($entry in $entries|Where-Object FullName -ne 'checksums.json'){
        Assert-Showcase ($checksums.ContainsKey($entry.FullName)) "Artifact checksum missing: $($entry.FullName)"
        $stream=$entry.Open()
        try{$actual=(Get-FileHash -InputStream $stream -Algorithm SHA256).Hash.ToLowerInvariant()}finally{$stream.Dispose()}
        Assert-Showcase ($actual-eq$checksums[$entry.FullName]) "Artifact checksum mismatch: $($entry.FullName)"
    }
    Assert-Showcase ($checksums.Count-eq$entries.Count-1) 'Artifact checksum catalog has missing or extra members'
    @{manifest='manifest.json';timeline='timeline/timeline.ndjson';contactSheet='derivatives/contact-sheet.png';checksumsVerified=$checksums.Count;writtenFrames=$manifest.writtenFrames;writtenStates=$manifest.writtenStates;dataCategory=$manifest.dataCategory;evidenceContaminated=[bool]$manifest.evidenceContaminated}
}
function Show-PersistentRead {
    Enter-ShowcaseWorld;Set-ShowcaseMode READ|Out-Null
    $live=Invoke-ShowcaseApi GET '/v0/player'
    $chunkX=[int][Math]::Floor($live.x/16);$chunkZ=[int][Math]::Floor($live.z/16)
    Exit-ShowcaseWorld
    Write-Host '  Save & Quit 后通过保留的安全上下文读 world/player/chunk：PERSISTED / last_saved_state，不回退 LIVE。'
    foreach($domain in @('world','player','chunk')) {
        $body=@{domain=$domain};if($domain-eq'chunk'){$body.chunkX=$chunkX;$body.chunkZ=$chunkZ}
        $saved=Invoke-ShowcaseApi POST '/v0/diagnostics/phase9a/storage/read' $body
        Assert-Showcase ($saved.available-and$saved.dataSource-eq'PERSISTED'-and$saved.consistency-eq'last_saved_state'-and$saved.writeImplemented-eq$false) "persisted $domain read unavailable; no LIVE fallback"
        Add-ShowcaseEvidence @{case='persistent-read';domain=$domain;dataSource=$saved.dataSource;consistency=$saved.consistency;stalePossibility=$saved.stalePossibility;readStatus=$saved.readStatus;contextSource=$saved.contextSource;writeImplemented=$saved.writeImplemented}
        Wait-ShowcaseHold
    }
}
function Show-CancelCleanup {
    Enter-ShowcaseWorld;Start-ShowcaseTakeover;Close-ShowcaseScreen
    Write-Host '  TAKEOVER：等待 W 实际按住后取消 Operation；后续视角步骤不得晚到。不是模拟真人 Esc。'
    $operation=Invoke-ShowcaseApi POST '/v0/pipelines' @{steps=@(@{type='key';key=87;action=1},@{type='delay';durationMs=10000},@{type='mouse.delta';dx=32;dy=0});timeoutMs=15000} (Get-ShowcaseControlHeaders)
    $script:activeOperation=$operation.operationId
    try {
        Wait-ShowcaseCondition {Invoke-ShowcaseApi GET '/v0/input/state'} {$args[0].pressedKeyCount-gt0} 'held W observed'|Out-Null
        Invoke-ShowcaseApi DELETE "/v0/operations/$($operation.operationId)"|Out-Null
        $terminal=Wait-ShowcaseCondition {Invoke-ShowcaseApi GET "/v0/operations/$($operation.operationId)"} {$args[0].state-eq'cancelled'} 'operation cancelled'
        Set-ShowcaseMode READ|Out-Null
        $before=Assert-ShowcaseCleanInput;Wait-ShowcaseHold;$after=Assert-ShowcaseCleanInput
        Assert-Showcase ($before.inputDispatchSequence-eq$after.inputDispatchSequence) 'late input after cleanup'
        Add-ShowcaseEvidence @{case='cancel-cleanup';operationId=$operation.operationId;terminal=$terminal.state;mode='READ';heldKeys=$after.pressedKeyCount;heldButtons=$after.pressedButtonCount;postCleanupInputDispatch=0;humanEscSimulated=$false}
    }finally{
        Invoke-ShowcaseApi DELETE "/v0/operations/$($operation.operationId)"|Out-Null
        $script:activeOperation=$null
    }
}
