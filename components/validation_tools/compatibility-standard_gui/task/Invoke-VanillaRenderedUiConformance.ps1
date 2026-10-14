[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')][string]$Target,
    [Parameter(Mandatory)][uri]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [ValidateSet('packs','all')][string]$Cases='packs'
)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$instance=[IO.Path]::GetFullPath((Join-Path $repo "validations/feature-runtime_probe/instance/$Target/client"))
if(-not[IO.Path]::GetFullPath($TokenFile).StartsWith($instance+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Only isolated validation instance is allowed'}
if($BaseUri.Host-notin@('localhost','127.0.0.1','::1')){throw 'Loopback only'}
$headers=@{Authorization='Bearer '+(Get-Content -LiteralPath $TokenFile -Raw).Trim()}
$checks=[Collections.Generic.List[string]]::new()
$lease=$null
$failure=$null
function Native($method,$path,$body=$null) {
    $args=@{Uri=[uri]::new($BaseUri,$path);Method=$method;Headers=$headers;TimeoutSec=40}
    if($null-ne$body){$args.ContentType='application/json';$args.Body=$body|ConvertTo-Json -Depth 32 -Compress}
    Invoke-RestMethod @args
}
function Tree {Native GET '/v0/ui/tree'}
function Nodes($tree) {foreach($node in $tree.children){$node;if($node.children){Nodes $node}}}
function Ensure($condition,$message) {if(-not$condition){throw $message}}
function Ui($selector,$action='click',$extra=@{}) {
    Native POST '/v0/control/renew' @{ttlMs=60000}|Out-Null
    $body=@{selector=$selector;action=$action}
    foreach($key in $extra.Keys){$body[$key]=$extra[$key]}
    $result=Native POST '/v0/ui/action' $body
    Ensure ($result.targetingSource-eq'interaction_tree') 'No coordinate fallback is allowed'
    Ensure (-not$result.directMutationUsed) 'UI must use native input, not direct mutation'
    $result
}
function Click($key,$label) {
    $nodes=@(Nodes (Tree)|Where-Object {$_.translationKey-eq$key-and$_.active-and$_.visible})
    if($nodes.Count-eq1){Ui @{nodeId=$nodes[0].nodeId}|Out-Null}
    else{Ui @{label=$label}|Out-Null}
}
function WaitScreen($pattern) {
    $until=[DateTime]::UtcNow.AddSeconds(40)
    do {
        $tree=Tree
        if($tree.screenClass-match$pattern-and($null-eq$tree.overlayIdentity-or$tree.overlayIdentity-eq0)){return $tree}
        Start-Sleep -Milliseconds 150
    }while([DateTime]::UtcNow-lt$until)
    throw "Did not reach $pattern"
}
function SetupPacks {
    $facts=Get-Content (Join-Path $repo "versions/$Target/target.properties") -Raw|ConvertFrom-StringData
    $mc=$Target.Substring(0,$Target.LastIndexOf('-'))
    $gameJar=if($Target-eq'1.20.1-forge'){"versions/$Target/build/moddev/artifacts/client-extra-$mc-$($facts.loader_version).jar"}
        elseif($Target-eq'1.21.1-neoforge'){"versions/$Target/build/moddev/artifacts/neoforge-$($facts.loader_version)-client-extra-aka-minecraft-resources.jar"}
        elseif($Target-eq'26.3-fabric'){
            $pairedFacts=Get-Content (Join-Path $repo "versions/$mc-neoforge/target.properties") -Raw|ConvertFrom-StringData
            "versions/$mc-neoforge/build/moddev/artifacts/minecraft-patched-$($pairedFacts.loader_version).jar"
        }
        else{"versions/$Target/build/moddev/artifacts/minecraft-patched-$($facts.loader_version).jar"}
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip=[IO.Compression.ZipFile]::OpenRead((Join-Path $repo $gameJar))
    try {
        $reader=[IO.StreamReader]::new($zip.GetEntry('version.json').Open())
        try{$version=$reader.ReadToEnd()|ConvertFrom-Json}finally{$reader.Dispose()}
    }finally{$zip.Dispose()}
    Ensure ($version.id-eq$mc) 'Fixture pack format must match actual Minecraft'
    $pack=@{description='Mine-Craft-Protocol semantic UI fixture'}
    if($null-ne$version.pack_version.resource_major){
        $format=@($version.pack_version.resource_major,$version.pack_version.resource_minor)
        $pack.min_format=$format;$pack.max_format=$format
    }else{$pack.pack_format=$version.pack_version.resource}
    foreach($name in @('ui-semantic-a','ui-semantic-b')){
        $path=Join-Path $instance "resourcepacks/$name"
        if(Test-Path -LiteralPath $path){
            Ensure (Test-Path -LiteralPath (Join-Path $path 'fixture-owner.json')) 'Refusing to overwrite an unowned pack'
        }
        [IO.Directory]::CreateDirectory($path)|Out-Null
        [IO.File]::WriteAllText((Join-Path $path 'fixture-owner.json'),'{"owner":"Mine-Craft-Protocol UI conformance"}'+ "`n",[Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText((Join-Path $path 'pack.mcmeta'),(@{pack=$pack}|ConvertTo-Json -Depth 6)+ "`n",[Text.UTF8Encoding]::new($false))
    }
}
function Key($key) {
    $operation=Native POST '/v0/pipelines' @{timeoutMs=12000;steps=@(@{type='key.tap';key=$key;holdMs=50})}
    $result=Native POST ("/v0/operations/"+$operation.operationId+"/wait") @{timeoutMs=12000}
    Ensure ($result.state-eq'completed') 'Native key operation failed'
}
function Command($command) {
    Native POST '/v0/control/renew' @{ttlMs=60000}|Out-Null
    Native POST '/v0/command/player' @{command=$command}|Out-Null
    if($command-like'tp @s 0.5 80 0.5 0 *'){
        $pitch=[double]($command.Split(' ')[-1])
        $until=[DateTime]::UtcNow.AddSeconds(10)
        do{
            $player=Native GET '/v0/player'
            if([Math]::Abs($player.pitch-$pitch)-lt0.05-and[Math]::Abs($player.x-0.5)-lt0.05-and[Math]::Abs($player.z-0.5)-lt0.05){break}
            Start-Sleep -Milliseconds 100
        }while([DateTime]::UtcNow-lt$until)
        Ensure ([Math]::Abs($player.pitch-$pitch)-lt0.05) 'Server camera setup did not reach client'
    }
}
function RightClick {
    $operation=Native POST '/v0/pipelines' @{timeoutMs=12000;steps=@(
        @{type='delay';durationMs=100},
        @{type='mouse.button';button=1;action=1;modifiers=0},
        @{type='delay';durationMs=150},
        @{type='mouse.button';button=1;action=0;modifiers=0})}
    $result=Native POST ("/v0/operations/"+$operation.operationId+"/wait") @{timeoutMs=12000}
    Ensure ($result.state-eq'completed') 'Native use operation failed'
}
function WaitNode($predicate,$message) {
    $until=[DateTime]::UtcNow.AddSeconds(15)
    do{
        $tree=Tree
        $found=@(Nodes $tree|Where-Object $predicate)
        if($found.Count){return $found[0]}
        Start-Sleep -Milliseconds 150
    }while([DateTime]::UtcNow-lt$until)
    throw $message
}
function EnterOwnedWorld {
    $worldName="UI-Rendered-$Target"
    $marker=Join-Path $instance 'rendered-ui-world-owner.json'
    $owned=Test-Path -LiteralPath $marker
    $beforeWorlds=@(Get-ChildItem (Join-Path $instance 'saves') -Directory -ErrorAction SilentlyContinue|ForEach-Object Name)
    if($owned){
        $owner=Get-Content -LiteralPath $marker -Raw|ConvertFrom-Json
        Ensure ($owner.purpose-eq'VANILLA_RENDERED_UI'-and$owner.worldId-match'^UI-Rendered-') 'World ownership mismatch'
        $worldId=$owner.worldId
        Ensure ($worldId-in$beforeWorlds) 'Owned fixture world is missing'
    }
    Click 'menu.singleplayer' 'Singleplayer'
    WaitScreen 'SelectWorldScreen$|CreateWorldScreen$'|Out-Null
    if($owned){
        Ui @{worldId=$worldId}|Out-Null
        Click 'selectWorld.select' 'Play Selected World'
    }else{
        if((Tree).screenClass-match'SelectWorldScreen$'){Click 'selectWorld.create' 'Create New World'}
        WaitScreen 'CreateWorldScreen$'|Out-Null
        Ui @{role='text_field';nth=0} type_text @{text=$worldName;replace=$true}|Out-Null
        for($i=0;$i-lt3;$i++){
            $mode=@(Nodes (Tree)|Where-Object label -like 'Game Mode:*')[0]
            Ensure ($null-ne$mode) 'Game mode control missing'
            if($mode.label-eq'Game Mode: Creative'){break}
            Ui @{nodeId=$mode.nodeId}|Out-Null
        }
        Ensure ($mode.label-eq'Game Mode: Creative') 'Safe Creative fixture required'
        Click 'selectWorld.create' 'Create New World'
    }
    $until=[DateTime]::UtcNow.AddSeconds(100)
    do{
        Native POST '/v0/control/renew' @{ttlMs=60000}|Out-Null
        $session=Native GET '/v0/session'
        if($session.inWorld-and-not$session.screenClass){break}
        Start-Sleep -Milliseconds 250
    }while([DateTime]::UtcNow-lt$until)
    Ensure ($session.inWorld-and-not$session.screenClass) 'World did not become ready'
    if(-not$owned){
        $created=@(Get-ChildItem (Join-Path $instance 'saves') -Directory|Where-Object Name -notin $beforeWorlds)
        Ensure ($created.Count-eq1-and$created[0].Name-like'UI-Rendered-*') 'Created world identity is ambiguous'
        $worldId=$created[0].Name
        [IO.File]::WriteAllText($marker,(@{worldId=$worldId;purpose='VANILLA_RENDERED_UI'}|ConvertTo-Json)+[char]10,[Text.UTF8Encoding]::new($false))
    }
    # Diagnostic Arrange only. Commands use the actual player network and server permission checks.
    Command 'gamemode creative'
    Command 'tp @s 0.5 80 0.5 0 0'
    Command 'fill -4 79 -4 4 79 4 minecraft:stone'
    Command 'fill -2 80 -2 2 83 3 minecraft:air'
    Command 'clear @s'
    Key 49
}
function OpenWorkstation($block,$items) {
    Command 'kill @e[type=minecraft:villager,distance=..6]'
    Command "setblock 0 81 2 minecraft:$block"
    Command 'clear @s'
    $slot=0
    foreach($item in $items){Command "item replace entity @s hotbar.$slot with $item";$slot++}
    # Stonecutter is shorter than one block; aim into its actual collision shape.
    Command 'tp @s 0.5 80 0.5 0 10'
    RightClick
}
function ShiftItem($itemId) {
    $until=[DateTime]::UtcNow.AddSeconds(8)
    $last='';$stable=0
    do{
        $frame=Tree
        $key="$($frame.screenRevision):$($frame.menuRevision)"
        if($key-eq$last){$stable++}else{$stable=0;$last=$key}
        if($stable-ge3){break}
        Start-Sleep -Milliseconds 200
    }while([DateTime]::UtcNow-lt$until)
    Ensure ($stable-ge3) 'Fixture inventory synchronization did not settle'
    $item=WaitNode { $_.role-eq'slot'-and$_.item-eq$itemId-and$_.count-gt0-and$_.visible } "Missing $itemId slot"
    # Older Vanilla polls the held key, rather than the mouse callback modifier bits.
    Native POST '/v0/input/key' @{key=340;scanCode=0;action=1;modifiers=1}|Out-Null
    try{Ui @{nodeId=$item.nodeId} click @{modifiers=1}|Out-Null}
    finally{Native POST '/v0/input/key' @{key=340;scanCode=0;action=0;modifiers=0}|Out-Null}
}
function CloseMenu { Key 256; WaitScreen '^$'|Out-Null }
function TestWorld {
    EnterOwnedWorld
    OpenWorkstation 'stonecutter' @('minecraft:stone 16')
    WaitScreen 'StonecutterScreen$'|Out-Null
    ShiftItem 'minecraft:stone'
    $recipe=WaitNode {$_.controlKey-like'stonecutter.recipe.*'-and$_.visible-and$_.active} 'Stonecutter semantic recipe missing'
    Ui @{nodeId=$recipe.nodeId}|Out-Null
    WaitNode {$_.controlKey-eq$recipe.controlKey-and$_.selected} 'Stonecutter recipe not selected'|Out-Null
    $checks.Add('stonecutter_native_recipe')
    CloseMenu
    OpenWorkstation 'loom' @('minecraft:white_banner 1','minecraft:blue_dye 8')
    WaitScreen 'LoomScreen$'|Out-Null
    ShiftItem 'minecraft:white_banner';ShiftItem 'minecraft:blue_dye'
    $pattern=WaitNode {$_.controlKey-like'loom.pattern.*'-and$_.visible-and$_.active} 'Loom semantic pattern missing'
    Ui @{nodeId=$pattern.nodeId}|Out-Null
    WaitNode {$_.controlKey-eq$pattern.controlKey-and$_.selected} 'Loom pattern not selected'|Out-Null
    $checks.Add('loom_native_pattern')
    CloseMenu
    OpenWorkstation 'enchanting_table' @('minecraft:iron_sword 1','minecraft:lapis_lazuli 16')
    WaitScreen 'EnchantmentScreen$'|Out-Null
    ShiftItem 'minecraft:iron_sword';ShiftItem 'minecraft:lapis_lazuli'
    $enchant=WaitNode {$_.controlKey-like'enchantment.option.*'-and$_.active} 'Enchantment option missing'
    Ui @{nodeId=$enchant.nodeId}|Out-Null
    $until=[DateTime]::UtcNow.AddSeconds(10)
    do{$changed=@(Nodes (Tree)|Where-Object {$_.controlKey-eq$enchant.controlKey-and$_.semanticState-ne$enchant.semanticState});if($changed.Count){break};Start-Sleep -Milliseconds 150}while([DateTime]::UtcNow-lt$until)
    Ensure ($changed.Count-gt0) 'Server enchantment state did not change'
    $checks.Add('enchantment_native_option')
    CloseMenu
    Command 'setblock 0 81 2 minecraft:air'
    Command 'clear @s'
    Command 'summon minecraft:villager 0.5 80 2.5 {NoAI:1b,Invulnerable:1b,VillagerData:{profession:"minecraft:farmer",level:2,type:"minecraft:plains"}}'
    Command 'tp @s 0.5 80 0.5 0 0'
    $until=[DateTime]::UtcNow.AddSeconds(10)
    do{
        $villagers=@((Native GET '/v0/world/entities?radius=8&limit=20').entities|Where-Object {$_.type-eq'minecraft:villager'-and[Math]::Abs($_.z-2.5)-lt0.1})
        if($villagers.Count){break}
        Start-Sleep -Milliseconds 100
    }while([DateTime]::UtcNow-lt$until)
    Ensure ($villagers.Count-eq1) 'Fixture villager did not reach client'
    RightClick
    WaitScreen 'MerchantScreen$'|Out-Null
    $trade=WaitNode {$_.controlKey-like'merchant.trade.*'-and$_.visible-and$_.active} 'Merchant semantic offer missing'
    Ui @{nodeId=$trade.nodeId}|Out-Null
    WaitNode {$_.controlKey-eq$trade.controlKey-and$_.selected} 'Merchant offer not selected'|Out-Null
    $checks.Add('merchant_native_offer')
    CloseMenu
    Command 'kill @e[type=minecraft:villager,distance=..6]'
    Command 'fill -1 80 1 1 80 3 minecraft:iron_block'
    Command 'fill 0 82 2 0 319 2 minecraft:air'
    OpenWorkstation 'beacon' @('minecraft:diamond 1')
    WaitScreen 'BeaconScreen$'|Out-Null
    ShiftItem 'minecraft:diamond'
    $power=WaitNode {$_.controlKey-like'beacon.primary.*'-and$_.active} 'Beacon power missing'
    Ui @{nodeId=$power.nodeId}|Out-Null
    WaitNode {$_.controlKey-eq$power.controlKey-and$_.selected} 'Beacon power not selected'|Out-Null
    Ui @{controlKey='beacon.confirm'}|Out-Null
    WaitScreen '^$'|Out-Null
    $checks.Add('beacon_native_power_confirm')
    Command 'setblock 0 81 2 minecraft:air'
    Command 'clear @s'
    Command 'item replace entity @s weapon.mainhand with minecraft:writable_book'
    RightClick
    WaitScreen 'BookEditScreen$'|Out-Null
    $bookText='First line'+[char]10+'Second line'
    Ui @{controlKey='book.page'} type_text @{text=$bookText;replace=$true}|Out-Null
    Ensure (@(Nodes (Tree)|Where-Object {$_.controlKey-eq'book.page'-and$_.value-eq$bookText}).Count-eq1) 'Book multiline input failed'
    $first=@(Nodes (Tree)|Where-Object controlKey -eq 'book.page')[0]
    Ui @{controlKey='book.next_page'}|Out-Null
    Ui @{controlKey='book.page'} type_text @{text='Page two';replace=$true}|Out-Null
    $stale=$false
    try{Ui @{nodeId=$first.nodeId} type_text @{text='STALE'}|Out-Null}catch{
        $errorBody=$_.ErrorDetails.Message|ConvertFrom-Json
        $stale=$errorBody.error-in@('UI_NODE_NOT_FOUND','UI_TARGET_INVALIDATED')
    }
    Ensure $stale 'Old book page token was accepted'
    Ui @{controlKey='book.previous_page'}|Out-Null
    Ensure (@(Nodes (Tree)|Where-Object {$_.controlKey-eq'book.page'-and$_.value-eq$bookText}).Count-eq1) 'Book page contents not preserved'
    $checks.Add('book_multiline_page_switch_stale_rejection')
    Click 'book.signButton' 'Sign'
    WaitScreen 'BookSignScreen$|BookEditScreen$'|Out-Null
    Ui @{controlKey='book.title'} type_text @{text='UI Test';replace=$true}|Out-Null
    Click 'book.finalizeButton' 'Sign and Close'
    WaitScreen '^$'|Out-Null
    RightClick
    $bookTree=WaitScreen 'BookViewScreen$'
    Ensure ($bookTree.screenData.pageCount-eq2-and$bookTree.screenData.pageText-eq$bookText) 'Signed book content not readable'
    Ui @{controlKey='book.next_page'}|Out-Null
    Ensure ((Tree).screenData.pageText-eq'Page two') 'Read-only book paging failed'
    $checks.Add('book_sign_read_and_page')
    Key 256
    WaitScreen '^$'|Out-Null
    Command 'item replace entity @s weapon.mainhand with minecraft:oak_sign 8'
    Command 'tp @s 0.5 80 0.5 0 60'
    RightClick
    WaitScreen 'SignEditScreen$'|Out-Null
    for($line=0;$line-lt4;$line++){
        $text="Line $($line+1)"
        Ui @{controlKey="sign.line.$line"} type_text @{text=$text;replace=$true}|Out-Null
        Ensure (@(Nodes (Tree)|Where-Object {$_.controlKey-eq"sign.line.$line"-and$_.value-eq$text}).Count-eq1) 'Sign native line editing failed'
    }
    Click 'gui.done' 'Done'
    WaitScreen '^$'|Out-Null
    $checks.Add('sign_four_native_keyboard_lines')
    Command 'item replace entity @s weapon.mainhand with minecraft:oak_hanging_sign 8'
    Command 'setblock 0 82 0 minecraft:stone'
    Command 'tp @s 0.5 80 0.5 0 -90'
    RightClick
    WaitScreen 'HangingSignEditScreen$'|Out-Null
    Ui @{controlKey='sign.line.3'} type_text @{text='Hanging line';replace=$true}|Out-Null
    Ensure (@(Nodes (Tree)|Where-Object {$_.controlKey-eq'sign.line.3'-and$_.value-eq'Hanging line'}).Count-eq1) 'Hanging sign line editing failed'
    Click 'gui.done' 'Done'
    WaitScreen '^$'|Out-Null
    $checks.Add('hanging_sign_native_keyboard_line')
    Command 'clear @s'
    Command 'tp @s 0.5 80 0.5 0 0'
    Key 256
    WaitScreen 'PauseScreen$'|Out-Null
    Click 'menu.returnToMenu' 'Save and Quit to Title'
    WaitScreen 'TitleScreen$'|Out-Null
}

try {
    $session=Native GET '/v0/session'
    Ensure ($session.target-eq$Target) 'Wrong Target'
    WaitScreen 'TitleScreen$'|Out-Null
    $mode=Native GET '/v0/control/mode'
    Ensure ($mode.mode-eq'READ'-and-not$mode.reconsentRequired) 'Explicit available READ required'
    SetupPacks
    $lease=Native POST '/v0/control/acquire' @{ttlMs=60000;expectedModeVersion=$mode.modeVersion}
    $headers['x-mcp-control-lease']=$lease.leaseId
    Click 'menu.options' 'Options...'
    Click 'options.resourcepack' 'Resource Packs...'
    WaitScreen 'PackSelectionScreen$'|Out-Null
    $before=@(Nodes (Tree)|Where-Object {$_.packId-and$_.role-eq'container'-and$_.selected}|ForEach-Object packId)
    foreach($id in @('file/ui-semantic-a','file/ui-semantic-b')){
        Ui @{packId=$id;controlKey='pack.select'}|Out-Null
        Ensure (@(Nodes (Tree)|Where-Object {$_.packId-eq$id-and$_.role-eq'container'-and$_.selected}).Count-eq1) 'Pack did not select'
    }
    $checks.Add('pack_select')
    $up=@(Nodes (Tree)|Where-Object {$_.packId-like'file/ui-semantic-*'-and$_.controlKey-eq'pack.move_up'-and$_.active})[0]
    Ensure ($null-ne$up) 'Native pack move up action missing'
    $oldOrder=@(Nodes (Tree)|Where-Object {$_.packId-and$_.role-eq'container'-and$_.selected}|ForEach-Object packId)
    Ui @{nodeId=$up.nodeId}|Out-Null
    $newOrder=@(Nodes (Tree)|Where-Object {$_.packId-and$_.role-eq'container'-and$_.selected}|ForEach-Object packId)
    Ensure (($oldOrder-join'|')-ne($newOrder-join'|')) 'Move up did not reorder'
    $down=@(Nodes (Tree)|Where-Object {$_.packId-eq$up.packId-and$_.controlKey-eq'pack.move_down'-and$_.active})[0]
    Ensure ($null-ne$down) 'Native pack move down action missing'
    Ui @{nodeId=$down.nodeId}|Out-Null
    Ensure (($oldOrder-join'|')-eq(@(Nodes (Tree)|Where-Object {$_.packId-and$_.role-eq'container'-and$_.selected}|ForEach-Object packId)-join'|')) 'Move down did not restore ordering'
    $checks.Add('pack_reorder_up_down')
    foreach($id in @('file/ui-semantic-b','file/ui-semantic-a')){Ui @{packId=$id;controlKey='pack.unselect'}|Out-Null}
    Ensure (($before-join'|')-eq(@(Nodes (Tree)|Where-Object {$_.packId-and$_.role-eq'container'-and$_.selected}|ForEach-Object packId)-join'|')) 'Initial selection not restored'
    $checks.Add('pack_unselect_restore')
    Click 'gui.done' 'Done'
    WaitScreen 'OptionsScreen$'|Out-Null
    Click 'gui.done' 'Done'
    WaitScreen 'TitleScreen$'|Out-Null
    if($Cases-eq'all'){TestWorld}
}catch{$failure=$_}finally{if($lease){try{Native POST '/v0/control/release'|Out-Null}catch{if(-not$failure){$failure=$_}}}}
$input=Native GET '/v0/input/state'
Ensure ($input.pressedKeyCount-eq0-and$input.pressedButtonCount-eq0) 'Input leaked'
$hashes=@(Get-ChildItem (Join-Path $repo "versions/$Target/src"),(Join-Path $repo 'components/runtime_core/src/main') -Recurse -File|Where-Object {$_.Name-in@('RenderedUiAdapter.java','VanillaUiTree.java','AutomationEngine.java','ConditionEngine.java')}|ForEach-Object{@{path=[IO.Path]::GetRelativePath($repo,$_.FullName).Replace('\','/');sha256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}})
$result=@{result=$(if($failure){'FAIL'}else{'PASS'});target=$Target;checks=@($checks);sourceHashes=$hashes;toolSha256=(Get-FileHash $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant();testedCommit=(& git -C $repo rev-parse HEAD);dirty=$true;coordinateFallbacks=0;persistentWriteCalls=0;runtime='development_native';arrange='diagnostic_player_commands_in_owned_creative_world';uiAct='GAME_ROUTED';fullVanillaUiAcceptance=$false;heldKeys=$input.pressedKeyCount;heldButtons=$input.pressedButtonCount;date=[DateTime]::UtcNow.ToString('o')}
if($failure){$result.failure=$failure.Exception.Message}
$directory=Join-Path $repo 'validations/compatibility-standard_gui/result'
[IO.Directory]::CreateDirectory($directory)|Out-Null
[IO.File]::WriteAllText((Join-Path $directory "rendered-ui-$Target.json"),($result|ConvertTo-Json -Depth 10).Replace("`r`n","`n")+ "`n",[Text.UTF8Encoding]::new($false))
$result|ConvertTo-Json -Depth 4
if($failure){throw $failure}
