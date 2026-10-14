[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidateSet('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')][string]$Target,
    [Parameter(Mandatory)][uri]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile
)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$expectedInstance = [IO.Path]::GetFullPath((Join-Path $repo "validations/feature-runtime_probe/instance/$Target/client"))
if (-not [IO.Path]::GetFullPath($TokenFile).StartsWith($expectedInstance + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Only the isolated runtime-probe validation instance is allowed' }
if ($BaseUri.Host -notin @('localhost','127.0.0.1','::1')) { throw 'Loopback only' }
$headers = @{Authorization = 'Bearer ' + (Get-Content -LiteralPath $TokenFile -Raw).Trim()}
$checks = [Collections.Generic.List[string]]::new()
$lease = $null
function Native($method, $path, $body = $null) {
    $args = @{Uri = ([uri]::new($BaseUri,$path)); Method = $method; Headers = $headers; TimeoutSec = 30}
    if ($null -ne $body) { $args.ContentType = 'application/json'; $args.Body = $body | ConvertTo-Json -Depth 32 -Compress }
    Invoke-RestMethod @args
}
function Nodes($tree) {
    foreach ($node in $tree.children) { $node; if ($node.children) { Nodes $node } }
}
function Ensure($condition, $message) { if (-not $condition) { throw $message } }
function Tree { Native GET '/v0/ui/tree' }
function Ui($action,$selector,$extra=@{}) {
    Native POST '/v0/control/renew' @{ttlMs=60000} | Out-Null
    $body=@{action=$action;selector=$selector}
    foreach($key in $extra.Keys){$body[$key]=$extra[$key]}
    $result=Native POST '/v0/ui/action' $body
    Ensure ($result.targetingSource -eq 'interaction_tree') 'Semantic input must not fall back to coordinates'
    Ensure (-not $result.directMutationUsed) 'Native UI input must not directly mutate business state'
    $result
}
function Click($label){ Ui click @{label=$label} | Out-Null }
function WaitScreen($pattern) {
    $end=[DateTime]::UtcNow.AddSeconds(25)
    do {
        $tree=Tree
        if($tree.screenClass -match $pattern){return $tree}
        Start-Sleep -Milliseconds 100
    }while([DateTime]::UtcNow -lt $end)
    throw "Screen did not reach $pattern"
}
try {
    $session=Native GET '/v0/session'
    Ensure ($session.target -eq $Target) 'Wrong Target'
    Ensure ((Tree).screenClass -match 'TitleScreen$') 'Start at title; do not operate an existing world'
    $mode=Native GET '/v0/control/mode'
    Ensure ($mode.mode -eq 'READ' -and -not $mode.reconsentRequired) 'Explicit available READ session required'
    $lease=Native POST '/v0/control/acquire' @{ttlMs=60000;expectedModeVersion=$mode.modeVersion}
    $headers['x-mcp-control-lease']=$lease.leaseId
    Click 'Singleplayer'
    $tree=WaitScreen 'SelectWorldScreen$|CreateWorldScreen$'
    if($tree.screenClass -match 'SelectWorldScreen$'){
        $worlds=@(Nodes $tree | Where-Object { $_.worldId -and $_.visible -and $_.active })
        Ui type_text @{role='text_field'} @{text='界面测试';replace=$true} | Out-Null
        Ensure ((@(Nodes (Tree)|Where-Object role -eq 'text_field')[0]).value -eq '界面测试') 'Unicode native character input failed'
        Ui type_text @{role='text_field'} @{text='';replace=$true} | Out-Null
        if($worlds.Count -gt 0){
            Ui click @{worldId=$worlds[0].worldId} | Out-Null
            Ensure (@(Nodes (Tree)|Where-Object { $_.label -eq 'Play Selected World' -and $_.active }).Count -eq 1) 'World selection did not enable Play'
            $checks.Add('world_row_selection')
        }
        Click 'Create New World'
        $tree=WaitScreen 'CreateWorldScreen$'
    }
    Ui type_text @{role='text_field';nth=0} @{text='UI 语义测试';replace=$true}|Out-Null
    Ensure (@(Nodes (Tree)|Where-Object {$_.role -eq 'text_field' -and $_.value -eq 'UI 语义测试'}).Count -eq 1) 'Create world text field did not receive Unicode'
    $checks.Add('unicode_text')
    $tabs=@(Nodes (Tree)|Where-Object {$_.role -eq 'tab' -and $_.visible})
    Ensure ($tabs.Count -ge 2) 'Create world tabs were not expanded'
    Ui click @{nodeId=$tabs[1].nodeId}|Out-Null
    Ensure (@(Nodes (Tree)|Where-Object {$_.nodeId -eq $tabs[1].nodeId -and $_.selected}).Count -eq 1) 'Native tab click did not select'
    $checks.Add('native_tabs')
    Click 'Cancel'
    $tree=Tree
    if($tree.screenClass -match 'SelectWorldScreen$'){
        $back=@(Nodes $tree|Where-Object {$_.role -eq 'button' -and $_.label -in @('Back','Cancel')})[0]
        Ensure ($null -ne $back) 'World list back button missing'
        Ui click @{nodeId=$back.nodeId}|Out-Null
    }
    WaitScreen 'TitleScreen$'|Out-Null
    Click 'Options...'
    Click 'Music & Sounds...'
    $tree=Tree
    $slider=@(Nodes $tree|Where-Object {$_.role -eq 'slider' -and $_.visible -and $_.active -and $_.label -notlike 'Master Volume:*'})[0]
    Ensure ($null -ne $slider) 'Native slider missing'
    $initial=$slider.value
    try {
        Ui set_value @{nodeId=$slider.nodeId} @{value=0.25}|Out-Null
        $value=@(Nodes (Tree)|Where-Object nodeId -eq $slider.nodeId)[0].value
        Ensure ([Math]::Abs($value-0.25) -lt 0.03) 'Native slider value did not update'
        $checks.Add('native_slider')
    }finally{Ui set_value @{nodeId=$slider.nodeId} @{value=$initial}|Out-Null}
    Click 'Done'
    Click 'Language...'
    $tree=Tree
    $rows=@(Nodes $tree|Where-Object role -eq 'list_item')
    Ensure ($rows.Count -gt 0) 'Visible native list rows missing'
    Ensure (@($rows|Where-Object {$_.visible-eq$false}).Count-eq0) 'USER view leaked hidden rows'
    $known=$rows[0]
    $scroll=@(Nodes $tree|Where-Object {$_.actions-contains'scroll'})[0]
    Ui scroll @{nodeId=$scroll.nodeId} @{yOffset=-20}|Out-Null
    Ui scroll_into_view @{nodeId=$known.nodeId;visibleOnly=$false}|Out-Null
    Ui hover @{nodeId=$known.nodeId}|Out-Null
    $checks.Add('bounded_scroll_into_view')
    Click 'Done'
    Click 'Video Settings...'
    $tree=Tree
    $lists=@(Nodes $tree|Where-Object role -eq 'list')
    Ensure ($lists.Count -gt 0) 'Options list missing'
    Ensure (@(Nodes $tree|Where-Object { $_.role -in @('slider','button') -and $_.nodeId -ne $lists[0].nodeId }).Count -gt 5) 'Nested option controls missing'
    $checks.Add('nested_options')
    Click 'Done'
    Click 'Done'
    WaitScreen 'TitleScreen$'|Out-Null
}
finally {
    if($lease){ Native POST '/v0/control/release' | Out-Null }
}
$input=Native GET '/v0/input/state'
Ensure ($input.pressedKeyCount -eq 0 -and $input.pressedButtonCount -eq 0) 'Held input leaked'
Ensure ((Native GET '/v0/control/mode').mode -eq 'READ') 'Did not return to READ'
$sourceRoot=Join-Path $repo "versions/$Target/src"
$sourceHashes=@(Get-ChildItem $sourceRoot,(Join-Path $repo 'components/runtime_core/src/main') -Recurse -File|Where-Object {$_.Name -in @('VanillaUiTree.java','AutomationEngine.java','ConditionEngine.java','ProbeService.java','ForgeProbeRuntime.java','NeoForgeProbeRuntime.java','FabricProbeRuntime.java') }|Sort-Object FullName|ForEach-Object { @{path=[IO.Path]::GetRelativePath($repo,$_.FullName).Replace('\','/');sha256=(Get-FileHash $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()} })
$result=@{result='PASS';target=$Target;checks=@($checks);testedCommit=(& git -C $repo rev-parse HEAD);dirty=(@(& git -C $repo status --porcelain).Count -gt 0);runtime='development_native';sourceHashes=$sourceHashes;heldKeys=$input.pressedKeyCount;heldButtons=$input.pressedButtonCount;coordinateFallbacks=0;persistentWriteCalls=0;scope='native_widgets_lists_text_sliders_tabs';fullVanillaUiAcceptance=$false;date=[DateTime]::UtcNow.ToString('o')}
$resultDirectory=Join-Path $repo 'validations/compatibility-standard_gui/result'
[IO.Directory]::CreateDirectory($resultDirectory)|Out-Null
[IO.File]::WriteAllText((Join-Path $resultDirectory "vanilla-ui-$Target.json"),($result|ConvertTo-Json -Depth 16).Replace("`r`n","`n")+"`n",[Text.UTF8Encoding]::new($false))
$result|ConvertTo-Json -Depth 4
