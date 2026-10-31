# Native Runtime API only. No host pointer/window/keyboard automation.
. (Join-Path $PSScriptRoot '../../InstanceProfiles.ps1')
function Assert-Showcase([bool]$Condition,[string]$Message) {
    if(-not $Condition){throw "Showcase: $Message"}
}
function Assert-ShowcaseTitle($Session) {
    Assert-Showcase (-not$Session.inWorld-and$Session.screenClass-match'(^|\.)TitleScreen$') '请先手动退回主菜单，再运行本案例；不会自动退出当前世界或操作其他界面。'
}
function Test-ShowcaseFixtureToasts($Tree) {
    $toasts=@($Tree.presentation.children|Where-Object {$_.role-eq'toast'-and$_.visible-ne$false-and$_.labelSource-eq'last_native_toast_render'-and$null-ne$_.actions-and@($_.actions).Count-eq0})
    $tutorial=@($toasts|Where-Object {$_.label-match'(?s)Showcase Fixture.*Tutorial\s+Toast\s*/\s*not\s+gameplay'})
    $system=@($toasts|Where-Object {$_.label-match'(?s)Showcase Fixture.*System\s+Toast\s*/\s*not\s+an\s+advancement'})
    return $tutorial.Count-gt0-and$system.Count-gt0
}
function Test-ShowcaseClientCommand([string]$CommandLine) {
    return $CommandLine-match'clientRunVmArgs|fabric\.dli\.env=client(?:\s|$)|net\.fabricmc\.loader\.[^\s]*KnotClient|--launchTarget(?:=|\s+)[^\s]*client[^\s]*|net\.minecraft\.client\.main\.Main'
}
function Get-ShowcaseGradleArguments([string]$TargetId,[string]$Directory,[string]$InitScript,[bool]$Offline,[string]$Role='client') {
    if($Role-notin@('client','client-multiplayer')){throw 'Unknown Showcase client role'}
    $suffix=if($Role-eq'client'){'Client'}else{'ClientMultiplayer'}
    $arguments=@("-Dshowcase.target=$TargetId","-Dshowcase.directory=$Directory","-Dshowcase.role=$Role",'--init-script',$InitScript,":version:${TargetId}:runValidationFeatureControlUiShowcase$suffix",'--no-daemon')
    if($Offline){$arguments+='--offline'}
    return $arguments
}
function Invoke-ShowcaseApi([string]$Method,[string]$Path,[object]$Body=$null,[hashtable]$Headers=$script:auth) {
    $request=@{Uri=$script:base+$Path;Method=$Method;Headers=$Headers;TimeoutSec=30}
    if($null-ne$Body){$request.ContentType='application/json';$request.Body=$Body|ConvertTo-Json -Depth 40 -Compress}
    try {Invoke-RestMethod @request}
    catch {
        $detail=$_.ErrorDetails.Message
        if($detail){
            $failure=[InvalidOperationException]::new("${Method} ${Path}: $detail",$_.Exception)
            try {$native=$detail|ConvertFrom-Json;$failure.Data['RuntimeError']=$native.error;$failure.Data['NativeError']=$native;$failure.Data['HttpStatus']=[int]$_.Exception.Response.StatusCode}catch{}
            throw $failure
        }
        throw
    }
}
function Get-ShowcaseMode { Invoke-ShowcaseApi GET '/v0/control/mode' }
function Start-ShowcaseTask {
    Assert-Showcase (-not$script:taskId) '当前 Runner 已拥有任务，不重复开始。'
    $task=Invoke-ShowcaseApi POST '/v0/control/task' @{action='begin';ttlMs=60000;budgetMs=300000}
    $script:taskId=$task.taskId
    $script:lastTaskHeartbeat=[DateTime]::UtcNow
}
function Stop-ShowcaseTask {
    if($script:taskId) {
        Invoke-ShowcaseApi POST '/v0/control/task' @{action='end';taskId=$script:taskId}|Out-Null
        $script:taskId=$null;$script:leaseId=$null
    }
}
function Set-ShowcaseMode([ValidateSet('READ','OPERATE')][string]$Mode) {
    $current=Get-ShowcaseMode
    $headers=$script:auth.Clone()
    if($script:leaseId){$headers['X-MCP-Control-Lease']=$script:leaseId}
    Invoke-ShowcaseApi POST '/v0/control/mode' @{mode=$Mode;expectedModeVersion=$current.modeVersion} $headers|Out-Null
    $script:leaseId=$null
    Assert-ShowcaseCleanInput
}
function Start-ShowcaseTakeover {
    $mode=Get-ShowcaseMode
    # Never manufacture chat consent, clear a latch implicitly, or take another client's Lease.
    Assert-Showcase (-not $mode.reconsentRequired) '用户手动结束控制。请先在对话中重新取得明确同意，再通过正式 acquire 路径恢复；Runner 不会自动清除提示。'
    if($mode.mode-eq'TAKEOVER') {
        Assert-Showcase ([bool]$script:leaseId) '已有其他控制会话；不会抢占。'
        return
    }
    $body=@{ttlMs=60000;expectedModeVersion=$mode.modeVersion}
    if($script:taskId){$body.taskId=$script:taskId}
    $lease=Invoke-ShowcaseApi POST '/v0/control/acquire' $body
    $script:leaseId=$lease.leaseId
    $script:lastRenew=[DateTime]::UtcNow
}
function Get-ShowcaseControlHeaders {
    Assert-Showcase ([bool]$script:leaseId) 'TAKEOVER Lease required'
    $headers=$script:auth.Clone();$headers['X-MCP-Control-Lease']=$script:leaseId
    return $headers
}
function Update-ShowcaseLease {
    if($script:taskId-and([DateTime]::UtcNow-$script:lastTaskHeartbeat).TotalSeconds-gt15) {
        Invoke-ShowcaseApi POST '/v0/control/task' @{action='heartbeat';taskId=$script:taskId;ttlMs=60000}|Out-Null
        $script:lastTaskHeartbeat=[DateTime]::UtcNow
    }
    if($script:leaseId-and([DateTime]::UtcNow-$script:lastRenew).TotalSeconds-gt15) {
        Invoke-ShowcaseApi POST '/v0/control/renew' @{ttlMs=60000} (Get-ShowcaseControlHeaders)|Out-Null
        $script:lastRenew=[DateTime]::UtcNow
    }
}
function Wait-ShowcaseHold([double]$Seconds=$HoldSeconds) {
    $until=[DateTime]::UtcNow.AddSeconds($Seconds)
    while([DateTime]::UtcNow-lt$until) {
        $mode=Get-ShowcaseMode # Observe override; explicit task keeps presence even between requests.
        Assert-Showcase (-not $mode.reconsentRequired) '用户手动结束控制；停止自动展示，不重新接管。'
        Update-ShowcaseLease
        Start-Sleep -Milliseconds ([Math]::Min(500,[Math]::Max(1,($until-[DateTime]::UtcNow).TotalMilliseconds)))
    }
}
function Assert-ShowcaseCleanInput {
    $until=[DateTime]::UtcNow.AddSeconds(5)
    do {
        $state=Invoke-ShowcaseApi GET '/v0/input/state'
        if($state.pressedKeyCount-eq0-and$state.pressedButtonCount-eq0-and-not$state.inputSequenceActive-and-not$state.activeGestureId){return $state}
        Start-Sleep -Milliseconds 50
    } while([DateTime]::UtcNow-lt$until)
    throw 'Showcase cleanup failed: held input or gesture remains active'
}
function Invoke-ShowcasePipeline([array]$Steps,[int]$TimeoutMs=20000) {
    Update-ShowcaseLease
    $started=Invoke-ShowcaseApi POST '/v0/pipelines' @{steps=$Steps;timeoutMs=$TimeoutMs} (Get-ShowcaseControlHeaders)
    $script:activeOperation=$started.operationId
    $until=[DateTime]::UtcNow.AddMilliseconds($TimeoutMs+5000)
    try {
        do {
            $operation=Invoke-ShowcaseApi GET "/v0/operations/$($started.operationId)"
            if($operation.state-in@('completed','failed','cancelled')){break}
            Update-ShowcaseLease
            Start-Sleep -Milliseconds 150
        } while([DateTime]::UtcNow-lt$until)
        Assert-Showcase ($operation.state-eq'completed') "operation $($operation.state): $($operation.error|ConvertTo-Json -Compress -Depth 8)"
        $operation
    } finally {
        if($operation.state-ne'completed'){Invoke-ShowcaseApi DELETE "/v0/operations/$($started.operationId)"|Out-Null}
        $script:activeOperation=$null
    }
}
function Send-ShowcaseKey([int]$Key,[int]$HoldMs=60) {
    Invoke-ShowcasePipeline @(@{type='key.tap';key=$Key;holdMs=$HoldMs})|Out-Null
}
function Wait-ShowcaseScreen([string]$Class,[bool]$Open=$true,[int]$TimeoutMs=10000) {
    $condition=@{type='screen';open=$Open}
    if($Class){$condition.classContains=$Class}
    Invoke-ShowcaseApi POST '/v0/wait/until' @{condition=$condition;timeoutMs=$TimeoutMs}|Out-Null
}
function Get-ShowcaseTree { Invoke-ShowcaseApi GET '/v0/ui/tree' }
function Wait-ShowcaseStableScreen {
    $until=[DateTime]::UtcNow.AddSeconds(20)
    $previous=$null;$stableSince=[DateTime]::UtcNow
    do {
        $tree=Get-ShowcaseTree
        $key="$($tree.screenIdentity)/$($tree.screenRevision)/$($tree.width)/$($tree.height)/$($tree.guiScale)"
        if($tree.overlayIdentity-ne0-or$key-ne$previous){$stableSince=[DateTime]::UtcNow}
        if($tree.overlayIdentity-eq0-and([DateTime]::UtcNow-$stableSince).TotalSeconds-ge1){return}
        $previous=$key;Start-Sleep -Milliseconds 150
    }while([DateTime]::UtcNow-lt$until)
    throw 'Vanilla Screen/reload overlay did not stabilize; not clicking stale coordinates'
}
function Get-ShowcaseNodes($Tree) {
    foreach($node in $Tree.children){$node;if($node.children){Get-ShowcaseNodes $node}}
}
function Find-ShowcaseNode([string]$Label) {
    $selector=Get-ShowcaseSelector @{label=$Label}
    $found=@(Get-ShowcaseNodes (Get-ShowcaseTree)|Where-Object {$_.active-ne$false-and$(if($selector.ContainsKey('translationKey')){$_.translationKey-eq$selector.translationKey}else{$_.label-eq$Label})})
    Assert-Showcase ($found.Count-eq1) "Semantic GUI target '$Label' missing or ambiguous"
    $found[0]
}
function Get-ShowcaseMutedOptions([string]$Text) {
    return Get-InstanceProfileOptions $Text automatic
}
function Get-ShowcaseSelector([hashtable]$Selector) {
    $keys=@{'Singleplayer'='menu.singleplayer';'Play Selected World'='selectWorld.select';'Create New World'='selectWorld.create';'Save and Quit to Title'='menu.returnToMenu';'Quit Game'='menu.quit';'Options...'='menu.options';'Language...'='options.language';'Done'='gui.done';'Continue'='gui.continue'}
    $result=$Selector.Clone()
    if($result.ContainsKey('label')-and-not$result.ContainsKey('translationKey')-and$keys.ContainsKey($result.label)){
        $result.translationKey=$keys[$result.label]
        $result.Remove('label')
    }
    return $result
}
function Invoke-ShowcaseUi([hashtable]$Selector,[string]$Action='click') {
    Update-ShowcaseLease
    Invoke-ShowcaseApi POST '/v0/ui/action' @{selector=(Get-ShowcaseSelector $Selector);action=$Action;holdMs=80} (Get-ShowcaseControlHeaders)
}
function Click-ShowcaseLabel([string]$Label) { Invoke-ShowcaseUi @{label=$Label}|Out-Null }
function Close-ShowcaseScreen {
    for($i=0;$i-lt5;$i++) {
        $session=Invoke-ShowcaseApi GET '/v0/session'
        if(-not$session.screenClass-or$session.screenClass-eq'none'){return}
        if($session.screenClass-match'TitleScreen'){return}
        Send-ShowcaseKey 256
    }
    Wait-ShowcaseScreen '' $false
}
function Set-ShowcaseFlatWorldDraft {
    $tabs=@(Get-ShowcaseNodes (Get-ShowcaseTree)|Where-Object {$_.role-eq'tab'-and$_.label-eq'World'})
    Assert-Showcase ($tabs.Count-eq1) 'World creation tab unavailable; do not generate an uncontrolled terrain fixture'
    Invoke-ShowcaseUi @{nodeId=$tabs[0].nodeId}|Out-Null
    for($i=0;$i-lt12;$i++){
        $types=@(Get-ShowcaseNodes (Get-ShowcaseTree)|Where-Object {$_.label-like'World Type:*'})
        Assert-Showcase ($types.Count-eq1) 'World Type missing or ambiguous'
        if($types[0].label-match'Superflat|Flat'){
            $tabs=@(Get-ShowcaseNodes (Get-ShowcaseTree)|Where-Object {$_.role-eq'tab'-and$_.label-eq'Game'})
            Assert-Showcase ($tabs.Count-eq1) 'Game creation tab unavailable'
            Invoke-ShowcaseUi @{nodeId=$tabs[0].nodeId}|Out-Null
            return
        }
        Invoke-ShowcaseUi @{nodeId=$types[0].nodeId}|Out-Null
    }
    throw 'Vanilla flat World Type not reached within the bounded draft cycle'
}
function Enter-ShowcaseWorld {
    $session=Invoke-ShowcaseApi GET '/v0/session'
    if($session.inWorld){return}
    Write-Host '  [布置/TAKEOVER] 通过 Vanilla GUI 进入专用 Showcase 世界。'
    Start-ShowcaseTakeover
    Assert-Showcase ($session.screenClass-match'TitleScreen') '从标题页开始；不会操作未知 Screen'
    Click-ShowcaseLabel 'Singleplayer'
    $until=[DateTime]::UtcNow.AddSeconds(20)
    do {$session=Invoke-ShowcaseApi GET '/v0/session';if($session.screenClass-match'SelectWorldScreen|CreateWorldScreen'){break};Start-Sleep -Milliseconds 200}while([DateTime]::UtcNow-lt$until)
    if($session.screenClass-match'SelectWorldScreen') {
        $worlds=@(Get-ChildItem -LiteralPath (Join-Path $script:instance 'saves') -Directory -ErrorAction SilentlyContinue)
        Assert-Showcase ($worlds.Count-eq1) '专用实例必须仅有一个 Showcase 世界；不会选择未知存档。'
        $tree=Get-ShowcaseTree
        $row=@(Get-ShowcaseNodes $tree|Where-Object {$_.worldId-eq$worlds[0].Name})
        Assert-Showcase ($row.Count-eq1) '专用世界必须有唯一语义行；不退回坐标。'
        if(-not$row[0].visible){Invoke-ShowcaseUi @{worldId=$worlds[0].Name;visibleOnly=$false} scroll_into_view|Out-Null}
        Invoke-ShowcaseUi @{worldId=$worlds[0].Name}|Out-Null
        Click-ShowcaseLabel 'Play Selected World'
    } elseif($session.screenClass-match'CreateWorldScreen') {
        Write-Host '  首次创建独立 Survival / Peaceful / Superflat 演示世界（默认超平坦模板，仅 Vanilla 正常保存，无 Persistent Write）。'
        $difficulty=@(Get-ShowcaseNodes (Get-ShowcaseTree)|Where-Object label -Like 'Difficulty:*')
        for($i=0;$i-lt4-and$difficulty.Count-eq1-and$difficulty[0].label-ne'Difficulty: Peaceful';$i++) {
            Click-ShowcaseLabel $difficulty[0].label
            $difficulty=@(Get-ShowcaseNodes (Get-ShowcaseTree)|Where-Object label -Like 'Difficulty:*')
        }
        Assert-Showcase ($difficulty.Count-eq1-and$difficulty[0].label-eq'Difficulty: Peaceful') 'cannot establish Peaceful demo world through the current GUI'
        Set-ShowcaseFlatWorldDraft
        Click-ShowcaseLabel 'Create New World'
    } else {throw 'World selection/creation did not become ready'}
    $until=[DateTime]::UtcNow.AddSeconds(120)
    do {
        $session=Invoke-ShowcaseApi GET '/v0/session'
        if($session.inWorld-and-not$session.screenClass){break}
        # Some versions represent the absent Screen by an empty/null class, some by "none".
        if($session.inWorld-and$session.screenClass-eq'none'){break}
        Update-ShowcaseLease;Start-Sleep -Milliseconds 300
    } while([DateTime]::UtcNow-lt$until)
    Assert-Showcase $session.inWorld 'world did not become ready'
    Close-ShowcaseScreen
    Set-ShowcaseMode READ|Out-Null
}
function Confirm-ShowcaseWorldExit($Before,$After,$Tree,$Failure) {
    Assert-Showcase ($Failure.status-eq409-and$Failure.body.error-in@('REQUEST_CANCELLED','CONTROL_INPUT_GENERATION_CHANGED')) 'unknown exit error is not an independently confirmed transition'
    Assert-Showcase ($Failure.body.executionOutcome-eq'not_confirmed'-and$Failure.body.retrySafe-eq$false) 'exit request must retain its unconfirmed/no-retry outcome'
    Assert-Showcase ($Tree.screenClass-match'TitleScreen$'-and$Tree.overlayIdentity-eq0) 'unconfirmed exit needs an unobscured actual title'
    foreach($field in @('inputGeneration','inputContextGeneration')){Assert-Showcase ($null-ne$Before.$field-and$After.$field-gt$Before.$field) 'exit needs new input and native context generations'}
    Assert-Showcase ($Before.inputContextAvailable-eq$true-and$After.inputContextAvailable-eq$false-and$Before.inputContextId-and$After.inputContextId-and$Before.inputContextId-ne$After.inputContextId) 'exit needs a replaced native world context'
    Assert-Showcase ($After.pressedKeyCount-eq0-and$After.pressedButtonCount-eq0-and$After.inputCleanupPending-eq$false-and$After.inputCleanupFailed-eq$false) 'unconfirmed exit needs clean released input'
    return @{outcome='world_exit_independently_observed';requestOutcome='not_confirmed';requestReplayed=$false;before=$Before;after=$After;screenClass=$Tree.screenClass;error=$Failure}
}
function Exit-ShowcaseWorld {
    $session=Invoke-ShowcaseApi GET '/v0/session'
    if(-not$session.inWorld){return}
    Write-Host '  [收尾/TAKEOVER] 正常 Save & Quit，回到标题页。'
    Start-ShowcaseTakeover;Close-ShowcaseScreen
    Send-ShowcaseKey 256;Wait-ShowcaseScreen 'PauseScreen'
    $before=Invoke-ShowcaseApi GET '/v0/input/state'
    $unconfirmed=$null
    try{Click-ShowcaseLabel 'Save and Quit to Title'}catch{
        if($_.Exception.Data['RuntimeError']-notin@('REQUEST_CANCELLED','CONTROL_INPUT_GENERATION_CHANGED')){throw}
        $unconfirmed=@{status=$_.Exception.Data['HttpStatus'];body=$_.Exception.Data['NativeError']}
    }
    $until=[DateTime]::UtcNow.AddSeconds(60)
    do{$tree=Get-ShowcaseTree;if($tree.screenClass-match'TitleScreen$'-and-not$tree.overlayIdentity){break};Update-ShowcaseLease;Start-Sleep -Milliseconds 250}while([DateTime]::UtcNow-lt$until)
    Assert-Showcase ($tree.screenClass-match'TitleScreen$'-and-not$tree.overlayIdentity) 'Save & Quit did not reach an unobscured title'
    if($unconfirmed){
        Invoke-ShowcaseApi POST '/v0/wait/until' @{condition=@{type='input.released'};timeoutMs=5000}|Out-Null
        $receipt=Confirm-ShowcaseWorldExit $before (Invoke-ShowcaseApi GET '/v0/input/state') $tree $unconfirmed
        if($null-eq$script:contextExitReceipts){$script:contextExitReceipts=[Collections.Generic.List[object]]::new()}
        $script:contextExitReceipts.Add($receipt)
        Write-Host '  Save & Quit回执未确认；新原生上下文、标题页和输入清零已独立确认，没有重放点击。'
    }
    Set-ShowcaseMode READ|Out-Null
}
function Open-ShowcaseInventory {
    Start-ShowcaseTakeover;Close-ShowcaseScreen;Send-ShowcaseKey 69
    Wait-ShowcaseScreen 'InventoryScreen'
    Assert-Showcase ((Invoke-ShowcaseApi GET '/v0/session').screenClass-notmatch'Creative') 'Showcase uses Survival inventory, not Creative catalog slots'
}
function Get-ShowcaseSourceHashes([string]$Repository,[string]$TargetId) {
    $paths=[Collections.Generic.List[string]]::new()
    foreach($folder in @("versions/$TargetId/src",'components/runtime_core/src/main','components/runtime_safety/src/main','components/validation_tools/feature-control_ui_showcase/task')) {
        foreach($file in Get-ChildItem -LiteralPath (Join-Path $Repository $folder) -File -Recurse){$paths.Add($file.FullName)}
    }
    foreach($file in @('settings.gradle.kts','build.gradle.kts','gradle.properties','gradle/libs.versions.toml',"versions/$TargetId/target.properties", "versions/$TargetId/build.gradle.kts",'components/protocol_schema/src/main/openapi/minecraft-control-v0.json','validations/feature-control_ui_showcase/validation.toml','components/validation_tools/InstanceProfiles.ps1','components/validation_tools/Prepare-InstanceProfile.ps1','components/validation_tools/instance-profiles.gradle','build.gradle.kts','components/validation_tools/RuntimeConnection.ps1')){$paths.Add((Join-Path $Repository $file))}
    $hashes=[ordered]@{}
    foreach($file in $paths|Sort-Object -Unique){$hashes[[IO.Path]::GetRelativePath($Repository,$file).Replace('\','/')]=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant()}
    return $hashes
}
function Get-ShowcaseMenu {
    $until=[DateTime]::UtcNow.AddSeconds(5)
    do {
        $snapshot=Invoke-ShowcaseApi POST '/v0/observe/deep' @{category='DEBUG';perspective='server_authoritative';domains=@('menu');includeProviderData=$false}
        if($snapshot.server.menu.slots-and$snapshot.resourceRevisionRefs){return $snapshot}
        Start-Sleep -Milliseconds 200
    }while([DateTime]::UtcNow-lt$until)
    throw 'Authoritative menu remains unavailable; not substituting empty inventory'
}
function Set-ShowcaseSlot([int]$Slot,[int]$Count) {
    for($attempt=0;$attempt-lt3;$attempt++) {
        $snapshot=Get-ShowcaseMenu
        $current=@($snapshot.server.menu.slots|Where-Object slot -eq $Slot)[0]
        Assert-Showcase ($null-ne$current-and$(if($Count-gt0){$current.empty}else{$current.empty-or($current.id-eq'minecraft:stone'-and$current.count-le8)})) 'reserved slot changed; refusing to overwrite an unexpected item'
        Assert-Showcase ($snapshot.server.menu.menuId-eq0) 'Only dedicated player inventory is allowed'
        $ref=@($snapshot.resourceRevisionRefs|Where-Object resourceType -eq 'menu')[0]
        $fp=Invoke-ShowcaseApi GET '/v0/world/fingerprint?category=DEBUG'
        $arm=Invoke-ShowcaseApi POST '/v0/debug/arm' @{worldFingerprint=$fp.worldFingerprint;namespaces=@('menu');ttlMs=10000}
        $headers=$script:auth.Clone();$headers['X-MCP-Debug-Arm']=$arm.debugArmId
        try {
            return Invoke-ShowcaseApi POST '/v0/debug/mutations' @{operation='menu.slot.set';worldFingerprint=$fp.worldFingerprint;expectedResourceVersion=$ref;slot=$Slot;itemId='minecraft:stone';count=$Count;expectedMenuId=0;expectedItemId=$current.id;expectedCount=$current.count} $headers
        } catch {if($attempt-eq2-or$_.Exception.Data['RuntimeError']-ne'STALE_RESOURCE_REVISION'){throw}}
        finally {Invoke-ShowcaseApi POST '/v0/debug/disarm'|Out-Null}
    }
}
function Select-ShowcaseEmptySlots($Menu) {
    $available=@($Menu.slots|Where-Object {$_.slot-ge9-and$_.slot-le35-and$_.empty}|Sort-Object slot|Select-Object -First 4)
    Assert-Showcase ($available.Count-eq4) '演示需要四个空背包槽；不会覆盖、清空或移动原有物品。'
    @($available|ForEach-Object {[int]$_.slot})
}
function Initialize-ShowcaseItems {
    Enter-ShowcaseWorld
    Set-ShowcaseMode OPERATE|Out-Null
    $snapshot=Get-ShowcaseMenu
    Assert-Showcase $snapshot.server.menu.carriedStack.empty 'carried stack must be empty'
    $script:reservedSlots=@(Select-ShowcaseEmptySlots $snapshot.server.menu)
    Write-Host "  仅预留空槽：$($script:reservedSlots-join', ')；原有物品保持不变。"
    # Claim only the four observed-empty slots before mutation; value/version guards still apply.
    $script:itemsOwned=$true
    $result=Set-ShowcaseSlot $script:reservedSlots[0] 8
    Assert-Showcase ($result.evidence-eq'diagnostic') 'Fixture setup must not become gameplay evidence'
}
function Restore-ShowcaseItems {
    if(-not$script:itemsOwned){return}
    $mode=Get-ShowcaseMode
    if($mode.reconsentRequired){throw '人工退出后不自动重新接管。物品仍保留在专用世界；请重新同意后再完成 cleanup。'}
    # OPERATE-only cases never need an input Lease for cleanup. Only an actual open GUI
    # needs a separate TAKEOVER step to return carried contents through Vanilla Escape.
    $session=Invoke-ShowcaseApi GET '/v0/session'
    if($session.screenClass-and$session.screenClass-ne'none') {
        Start-ShowcaseTakeover;Close-ShowcaseScreen
    }
    Set-ShowcaseMode OPERATE|Out-Null
    $snapshot=Get-ShowcaseMenu
    Assert-Showcase $snapshot.server.menu.carriedStack.empty 'carried stack not returned; cannot safely clean inventory'
    foreach($slot in $script:reservedSlots) {
        $snapshot=Get-ShowcaseMenu
        $current=@($snapshot.server.menu.slots|Where-Object slot -eq $slot)[0]
        if(-not$current.empty) {
            Assert-Showcase ($current.id-eq'minecraft:stone'-and$current.count-le8) 'unexpected item; refusing cleanup overwrite'
            Set-ShowcaseSlot $slot 0|Out-Null
        }
    }
    $verified=Get-ShowcaseMenu
    $reserved=@($verified.server.menu.slots|Where-Object {$_.slot-in$script:reservedSlots})
    Assert-Showcase ($reserved.Count-eq4-and@($reserved|Where-Object {-not$_.empty}).Count-eq0-and$verified.server.menu.carriedStack.empty) 'authoritative inventory cleanup not confirmed'
    $script:itemsOwned=$false
    Set-ShowcaseMode READ|Out-Null
}
function Get-ShowcaseSlotNode([int]$Slot) {
    $nodes=@(Get-ShowcaseNodes (Get-ShowcaseTree)|Where-Object {$_.role-eq'slot'-and$_.slot-eq$Slot-and$_.active})
    Assert-Showcase ($nodes.Count-eq1) "active Inventory slot $Slot missing"
    $nodes[0]
}
function Invoke-ShowcaseSlot([int]$Slot,[string]$Action='click') {
    $node=Get-ShowcaseSlotNode $Slot
    $selector=@{nodeId=$node.nodeId}
    $resolved=Invoke-ShowcaseApi POST '/v0/ui/resolve' $selector
    $x=[double]$node.x+[double]$node.width/2.0;$y=[double]$node.y+[double]$node.height/2.0
    Assert-Showcase ($node.width-eq18-and$node.height-eq18-and[Math]::Abs($node.interactionX-$x)-lt1e-9-and[Math]::Abs($node.interactionY-$y)-lt1e-9) 'native Slot hit box / metadata center mismatch'
    Assert-Showcase ([Math]::Abs($resolved.interactionPoint.x-$x)-lt1e-9-and[Math]::Abs($resolved.interactionPoint.y-$y)-lt1e-9) 'Slot resolve differs from tree metadata'
    $result=Invoke-ShowcaseUi $selector $Action
    $pointer=(Invoke-ShowcaseApi GET '/v0/input/state').agentPointer
    Assert-Showcase ([Math]::Abs($result.x-$x)-lt1e-9-and[Math]::Abs($result.y-$y)-lt1e-9-and[Math]::Abs($pointer.x-$x)-lt1e-9-and[Math]::Abs($pointer.y-$y)-lt1e-9) 'Slot action / delivered pointer differs from resolved center'
}
