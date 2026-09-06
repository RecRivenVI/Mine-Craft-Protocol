$script:showcaseCases=[ordered]@{
    'read'='READ：标题页与世界 HUD、弱蓝边、Presence 淡入淡出'
    'operate'='OPERATE：受控物品布置与恢复，不代替玩家输入'
    'takeover-gameplay'='TAKEOVER：第一人称移动、相对转向与释放'
    'pointer-move'='Virtual Pointer：短距离、长距离与真实 Slot hover'
    'takeover-gui'='Virtual Pointer：真实物品点击、Slot 移动、drag'
    'mode-transition'='三种模式连续转换，含 GUI Pointer 出现/隐藏'
    'ui-overlay'='Fixture Tutorial/System Toast、Inventory、Pause 的叠层'
    'gui-scenes'='Title / Inventory / Pause / Options 的不同背景'
    'manual-esc'='可选：等候真人物理 Esc（不属于 -All）'
}
function Get-ShowcaseCaseOrder { @($script:showcaseCases.Keys|Where-Object {$_-ne'manual-esc'}) }
function Show-Read {
    Exit-ShowcaseWorld;Set-ShowcaseMode READ|Out-Null
    Write-Host '  READ / Title。没有玩家输入；正在保持视觉状态。';Wait-ShowcaseHold
    Write-Host '  停止所有 API 心跳 17 秒，观察既有 15 秒 Presence 到期后的 Fade Out（不改动画）。'
    # No polling here: querying Session would itself keep READ presence alive.
    for($i=0;$i-lt17;$i++){Start-Sleep -Seconds 1}
    Wait-ShowcaseHold
    Enter-ShowcaseWorld;Set-ShowcaseMode READ|Out-Null
    Write-Host '  READ / 世界 HUD。';Wait-ShowcaseHold
}
function Show-Operate {
    Initialize-ShowcaseItems
    Write-Host '  OPERATE / DEBUG_PRIVILEGED：已在空槽放入 8 块石头，保持文案；没有玩家输入。'
    Wait-ShowcaseHold
    Restore-ShowcaseItems
    Wait-ShowcaseHold
}
function Show-TakeoverGameplay {
    Enter-ShowcaseWorld;Start-ShowcaseTakeover;Close-ShowcaseScreen
    Write-Host '  TAKEOVER / GAME_ROUTED：短按 W、S 与 Vanilla 相对视角输入。'
    Invoke-ShowcasePipeline @(@{type='key.tap';key=87;holdMs=200},@{type='key.tap';key=83;holdMs=200})|Out-Null
    Invoke-ShowcaseApi POST '/v0/input/mouse/delta' @{dx=32;dy=0} (Get-ShowcaseControlHeaders)|Out-Null
    Wait-ShowcaseHold
    Invoke-ShowcaseApi POST '/v0/input/mouse/delta' @{dx=-32;dy=0} (Get-ShowcaseControlHeaders)|Out-Null
    Set-ShowcaseMode READ|Out-Null;Wait-ShowcaseHold
}
function Show-PointerMovement {
    Initialize-ShowcaseItems;Open-ShowcaseInventory
    $tree=Get-ShowcaseTree
    foreach($x in @(20,([int]$tree.width-20))) {
        Write-Host '  屏幕两侧之间的长距离 hover（无点击）。'
        Invoke-ShowcaseApi POST '/v0/ui/action' @{action='hover';coordinates=@{x=$x;y=([int]$tree.height/2)};source='explicit_coordinate'} (Get-ShowcaseControlHeaders)|Out-Null
        Wait-ShowcaseHold
    }
    foreach($slot in @(9,10,17,36,9)) {
        Write-Host "  hover Slot $slot（现有 12-step easing，不修改速度）。"
        Invoke-ShowcaseSlot $slot hover
        Wait-ShowcaseHold
    }
    Restore-ShowcaseItems
}
function Show-PointerInteraction {
    Initialize-ShowcaseItems;Open-ShowcaseInventory
    Write-Host '  TAKEOVER：hover 石头 → 拿起 → 放到相邻槽。'
    Invoke-ShowcaseSlot 9 hover;Wait-ShowcaseHold
    Invoke-ShowcaseSlot 9;Wait-ShowcaseHold
    Invoke-ShowcaseSlot 10;Wait-ShowcaseHold
    $until=[DateTime]::UtcNow.AddSeconds(5)
    do {$menu=Get-ShowcaseMenu;$moved=@($menu.server.menu.slots|Where-Object slot -eq 10)[0];if($moved.id-eq'minecraft:stone'-and$moved.count-eq8){break};Start-Sleep -Milliseconds 100}while([DateTime]::UtcNow-lt$until)
    Assert-Showcase ($moved.id-eq'minecraft:stone'-and$moved.count-eq8) 'Vanilla slot-to-slot move not observed'
    $from=Get-ShowcaseSlotNode 10;$to=Get-ShowcaseSlotNode 11
    Write-Host '  TAKEOVER：真实 button hold / mouseMoved / drag / release。'
    Invoke-ShowcasePipeline @(@{type='ui.drag';fromSelector=@{nodeId=$from.nodeId};toSelector=@{nodeId=$to.nodeId};durationMs=900;segments=12;button=0})|Out-Null
    # Vanilla drag semantics can leave a carried stack. Explicitly place it in the reserved slot.
    $menu=Get-ShowcaseMenu
    if(-not$menu.server.menu.carriedStack.empty){Invoke-ShowcaseSlot 12}
    Wait-ShowcaseHold
    Assert-ShowcaseCleanInput|Out-Null
    Restore-ShowcaseItems
}
function Show-ModeTransitions {
    Enter-ShowcaseWorld;Open-ShowcaseInventory;Set-ShowcaseMode READ|Out-Null
    for($cycle=1;$cycle-le3;$cycle++) {
        Write-Host "  第 $cycle / 3 轮：READ → OPERATE → TAKEOVER → READ（不重新加载世界）。"
        Wait-ShowcaseHold;Set-ShowcaseMode OPERATE|Out-Null;Wait-ShowcaseHold
        Start-ShowcaseTakeover;Invoke-ShowcaseSlot 9 hover;Wait-ShowcaseHold
        Set-ShowcaseMode READ|Out-Null;Wait-ShowcaseHold
    }
    Start-ShowcaseTakeover;Close-ShowcaseScreen;Set-ShowcaseMode READ|Out-Null
}
function Start-ShowcaseToasts {
    Set-ShowcaseMode OPERATE|Out-Null
    Invoke-ShowcaseApi POST '/v0/diagnostics/ui/test-screen' @{}|Out-Null
    Start-ShowcaseTakeover;Click-ShowcaseLabel 'Showcase Toasts (Fixture)'
    # This is a pre-existing diagnostic Screen, not a new Runtime endpoint.
    Click-ShowcaseLabel 'Close Probe'
}
function Show-UiOverlay {
    Enter-ShowcaseWorld
    Write-Host '  明确标记的 Fixture Tutorial/System Toast；不是自然教学/成就事件。'
    Start-ShowcaseToasts
    Send-ShowcaseKey 69;Wait-ShowcaseScreen 'InventoryScreen';Wait-ShowcaseHold
    Close-ShowcaseScreen
    Start-ShowcaseToasts
    Send-ShowcaseKey 256;Wait-ShowcaseScreen 'PauseScreen';Wait-ShowcaseHold
    Close-ShowcaseScreen;Set-ShowcaseMode READ|Out-Null
}
function Show-GuiScenes {
    Enter-ShowcaseWorld;Open-ShowcaseInventory;Wait-ShowcaseHold
    Close-ShowcaseScreen;Send-ShowcaseKey 256;Wait-ShowcaseScreen 'PauseScreen';Wait-ShowcaseHold
    Visit-ShowcaseOptions
    Close-ShowcaseScreen;Exit-ShowcaseWorld
    Start-ShowcaseTakeover;Invoke-ShowcaseUi @{label='Singleplayer'} hover|Out-Null;Wait-ShowcaseHold
    Set-ShowcaseMode READ|Out-Null
}
function Visit-ShowcaseOptions {
    Click-ShowcaseLabel 'Options...';Wait-ShowcaseScreen 'OptionsScreen'
    Invoke-ShowcaseUi @{label='Done'} hover|Out-Null;Wait-ShowcaseHold
    # Mouse scroll is sent only to a real scrollable Vanilla list.
    Click-ShowcaseLabel 'Language...';Wait-ShowcaseScreen 'LanguageSelectScreen'
    $tree=Get-ShowcaseTree
    Invoke-ShowcaseApi POST '/v0/ui/action' @{action='scroll';coordinates=@{x=([int]$tree.width/2);y=([int]$tree.height/2)};source='explicit_coordinate';yOffset=-2} (Get-ShowcaseControlHeaders)|Out-Null
    Wait-ShowcaseHold
    # No language selection: scroll-only; original English remains unchanged.
    Send-ShowcaseKey 256
    Wait-ShowcaseScreen 'OptionsScreen'
    Click-ShowcaseLabel 'Done'
}
function Show-ManualEsc {
    Enter-ShowcaseWorld;Open-ShowcaseInventory
    $before=Invoke-ShowcaseApi GET '/v0/input/state'
    Write-Host '现在请按一次 Esc。Runner 等待真实物理 Esc；不会模拟，也不会随后自动重新接管。' -ForegroundColor Cyan
    $until=[DateTime]::UtcNow.AddSeconds($ManualTimeoutSeconds)
    do {
        $mode=Get-ShowcaseMode
        if($mode.reconsentRequired){break}
        Update-ShowcaseLease;Start-Sleep -Milliseconds 200
    }while([DateTime]::UtcNow-lt$until)
    Assert-Showcase ($mode.mode-eq'READ'-and$mode.reconsentRequired) '未在时限内收到真人撤销；不是人工验收 PASS。'
    $script:leaseId=$null
    $clean=Assert-ShowcaseCleanInput
    Assert-Showcase ($clean.nativeRevocations-gt$before.nativeRevocations) '没有 Runtime 原生人工撤销事件'
    $sequence=$clean.inputDispatchSequence;Start-Sleep -Milliseconds 350
    Assert-Showcase ((Invoke-ShowcaseApi GET '/v0/input/state').inputDispatchSequence-eq$sequence) 'post-revoke input detected'
    Write-Host 'Runtime 已确认 READ + reconsentRequired=true + held input=0。保留客户端；不会自动重新接管。'
}
function Invoke-ShowcaseCase([string]$Name) {
    switch($Name) {
        'read' {Show-Read}
        'operate' {Show-Operate}
        'takeover-gameplay' {Show-TakeoverGameplay}
        'pointer-move' {Show-PointerMovement}
        'takeover-gui' {Show-PointerInteraction}
        'mode-transition' {Show-ModeTransitions}
        'ui-overlay' {Show-UiOverlay}
        'gui-scenes' {Show-GuiScenes}
        'manual-esc' {Show-ManualEsc}
        default {throw "Unknown showcase case: $Name"}
    }
}
function Invoke-ShowcaseSmoke {
    # Intentionally not -All: only runner plumbing, real item path and test-Toast availability.
    Write-Host 'Runner smoke（非整套展示、非视觉验收）：READ → 布置 → GUI hover/click/drag → cleanup → title。'
    Set-ShowcaseMode READ|Out-Null;Wait-ShowcaseHold
    Show-PointerInteraction
    Start-ShowcaseToasts
    Wait-ShowcaseHold 0.75
    Invoke-WebRequest ($script:base+'/v0/capture') -Headers $script:auth -TimeoutSec 20 -OutFile (Join-Path $script:instance 'smoke-fixture-content.png')|Out-Null
    Set-ShowcaseMode READ|Out-Null
    Exit-ShowcaseWorld
    Start-ShowcaseTakeover;Visit-ShowcaseOptions
    Wait-ShowcaseScreen 'TitleScreen';Set-ShowcaseMode READ|Out-Null
}
