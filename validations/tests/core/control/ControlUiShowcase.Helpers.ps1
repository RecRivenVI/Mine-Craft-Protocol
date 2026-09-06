# Native Runtime API only. No host pointer/window/keyboard automation.
function Assert-Showcase([bool]$Condition,[string]$Message) {
    if(-not $Condition){throw "Showcase: $Message"}
}
function Invoke-ShowcaseApi([string]$Method,[string]$Path,[object]$Body=$null,[hashtable]$Headers=$script:auth) {
    $request=@{Uri=$script:base+$Path;Method=$Method;Headers=$Headers;TimeoutSec=30}
    if($null-ne$Body){$request.ContentType='application/json';$request.Body=$Body|ConvertTo-Json -Depth 40 -Compress}
    try {Invoke-RestMethod @request}
    catch {
        $detail=$_.ErrorDetails.Message
        if($detail){
            $failure=[InvalidOperationException]::new("${Method} ${Path}: $detail",$_.Exception)
            try {$failure.Data['RuntimeError']=($detail|ConvertFrom-Json).error}catch{}
            throw $failure
        }
        throw
    }
}
function Get-ShowcaseMode { Invoke-ShowcaseApi GET '/v0/control/mode' }
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
    $lease=Invoke-ShowcaseApi POST '/v0/control/acquire' @{ttlMs=60000;expectedModeVersion=$mode.modeVersion}
    $script:leaseId=$lease.leaseId
    $script:lastRenew=[DateTime]::UtcNow
}
function Get-ShowcaseControlHeaders {
    Assert-Showcase ([bool]$script:leaseId) 'TAKEOVER Lease required'
    $headers=$script:auth.Clone();$headers['X-MCP-Control-Lease']=$script:leaseId
    return $headers
}
function Update-ShowcaseLease {
    if($script:leaseId-and([DateTime]::UtcNow-$script:lastRenew).TotalSeconds-gt15) {
        Invoke-ShowcaseApi POST '/v0/control/renew' @{ttlMs=60000} (Get-ShowcaseControlHeaders)|Out-Null
        $script:lastRenew=[DateTime]::UtcNow
    }
}
function Wait-ShowcaseHold([double]$Seconds=$HoldSeconds) {
    $until=[DateTime]::UtcNow.AddSeconds($Seconds)
    while([DateTime]::UtcNow-lt$until) {
        $mode=Get-ShowcaseMode # READ presence heartbeat, not player input.
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
    $found=@(Get-ShowcaseNodes (Get-ShowcaseTree)|Where-Object {$_.label-eq$Label-and$_.active-ne$false})
    Assert-Showcase ($found.Count-eq1) "GUI target '$Label' missing or ambiguous (English showcase instance required)"
    $found[0]
}
function Invoke-ShowcaseUi([hashtable]$Selector,[string]$Action='click') {
    Update-ShowcaseLease
    Invoke-ShowcaseApi POST '/v0/ui/action' @{selector=$Selector;action=$Action;holdMs=80} (Get-ShowcaseControlHeaders)
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
        # Vanilla world-list rows have no semantic children in the current Interaction Tree.
        # Explicit, documented first-row coordinate fallback is confined to this one-world instance.
        Invoke-ShowcaseApi POST '/v0/ui/action' @{action='click';coordinates=@{x=([int]$tree.width/2);y=75};source='explicit_coordinate'} (Get-ShowcaseControlHeaders)|Out-Null
        Click-ShowcaseLabel 'Play Selected World'
    } elseif($session.screenClass-match'CreateWorldScreen') {
        Write-Host '  首次创建独立 Survival / Peaceful 演示世界（仅 Vanilla 正常保存，无 Persistent Write）。'
        $difficulty=@(Get-ShowcaseNodes (Get-ShowcaseTree)|Where-Object label -Like 'Difficulty:*')
        for($i=0;$i-lt4-and$difficulty.Count-eq1-and$difficulty[0].label-ne'Difficulty: Peaceful';$i++) {
            Click-ShowcaseLabel $difficulty[0].label
            $difficulty=@(Get-ShowcaseNodes (Get-ShowcaseTree)|Where-Object label -Like 'Difficulty:*')
        }
        Assert-Showcase ($difficulty.Count-eq1-and$difficulty[0].label-eq'Difficulty: Peaceful') 'cannot establish Peaceful demo world through the current GUI'
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
function Exit-ShowcaseWorld {
    $session=Invoke-ShowcaseApi GET '/v0/session'
    if(-not$session.inWorld){return}
    Write-Host '  [收尾/TAKEOVER] 正常 Save & Quit，回到标题页。'
    Start-ShowcaseTakeover;Close-ShowcaseScreen
    Send-ShowcaseKey 256;Wait-ShowcaseScreen 'PauseScreen'
    Click-ShowcaseLabel 'Save and Quit to Title'
    $until=[DateTime]::UtcNow.AddSeconds(60)
    do{$session=Invoke-ShowcaseApi GET '/v0/session';if($session.screenClass-match'TitleScreen'){break};Update-ShowcaseLease;Start-Sleep -Milliseconds 250}while([DateTime]::UtcNow-lt$until)
    Assert-Showcase ($session.screenClass-match'TitleScreen') 'Save & Quit did not reach title'
    Set-ShowcaseMode READ|Out-Null
}
function Open-ShowcaseInventory {
    Start-ShowcaseTakeover;Close-ShowcaseScreen;Send-ShowcaseKey 69
    Wait-ShowcaseScreen 'InventoryScreen'
    Assert-Showcase ((Invoke-ShowcaseApi GET '/v0/session').screenClass-notmatch'Creative') 'Showcase uses Survival inventory, not Creative catalog slots'
}
function Get-ShowcaseMenu {
    $until=[DateTime]::UtcNow.AddSeconds(5)
    do {
        $snapshot=Invoke-ShowcaseApi POST '/v0/observe/deep' @{perspective='server_authoritative';domains=@('menu');includeProviderData=$false}
        if($snapshot.server.menu.slots-and$snapshot.resourceRevisionRefs){return $snapshot}
        Start-Sleep -Milliseconds 200
    }while([DateTime]::UtcNow-lt$until)
    throw 'Authoritative menu remains unavailable; not substituting empty inventory'
}
function Set-ShowcaseSlot([int]$Slot,[int]$Count) {
    for($attempt=0;$attempt-lt3;$attempt++) {
        $snapshot=Get-ShowcaseMenu
        $current=@($snapshot.server.menu.slots|Where-Object slot -eq $Slot)[0]
        Assert-Showcase ($snapshot.server.menu.menuId-eq0) 'Only dedicated player inventory is allowed'
        $ref=@($snapshot.resourceRevisionRefs|Where-Object resourceType -eq 'menu')[0]
        $fp=Invoke-ShowcaseApi GET '/v0/world/fingerprint'
        $arm=Invoke-ShowcaseApi POST '/v0/debug/arm' @{worldFingerprint=$fp.worldFingerprint;namespaces=@('menu');ttlMs=10000}
        $headers=$script:auth.Clone();$headers['X-MCP-Debug-Arm']=$arm.debugArmId
        try {
            return Invoke-ShowcaseApi POST '/v0/debug/mutations' @{operation='menu.slot.set';worldFingerprint=$fp.worldFingerprint;expectedResourceVersion=$ref;slot=$Slot;itemId='minecraft:stone';count=$Count;expectedMenuId=0;expectedItemId=$current.id;expectedCount=$current.count} $headers
        } catch {if($attempt-eq2-or$_.Exception.Data['RuntimeError']-ne'STALE_RESOURCE_REVISION'){throw}}
        finally {Invoke-ShowcaseApi POST '/v0/debug/disarm'|Out-Null}
    }
}
function Initialize-ShowcaseItems {
    Enter-ShowcaseWorld
    Set-ShowcaseMode OPERATE|Out-Null
    $snapshot=Get-ShowcaseMenu
    foreach($slot in 9..12) {
        $current=@($snapshot.server.menu.slots|Where-Object slot -eq $slot)[0]
        Assert-Showcase ($null-ne$current-and$current.empty) "请保持专用存档的背包槽 $slot 为空；不会覆盖已有物品。"
    }
    Assert-Showcase $snapshot.server.menu.carriedStack.empty 'carried stack must be empty'
    # Claim only these four empty slots, before mutation, so any subsequent error is recoverable.
    $script:itemsOwned=$true
    $result=Set-ShowcaseSlot 9 8
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
    foreach($slot in 9..12) {
        $snapshot=Get-ShowcaseMenu
        $current=@($snapshot.server.menu.slots|Where-Object slot -eq $slot)[0]
        if(-not$current.empty) {
            Assert-Showcase ($current.id-eq'minecraft:stone'-and$current.count-le8) 'unexpected item; refusing cleanup overwrite'
            Set-ShowcaseSlot $slot 0|Out-Null
        }
    }
    $verified=Get-ShowcaseMenu
    $reserved=@($verified.server.menu.slots|Where-Object {$_.slot-in(9..12)})
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
    Invoke-ShowcaseUi @{nodeId=$node.nodeId} $Action|Out-Null
}
