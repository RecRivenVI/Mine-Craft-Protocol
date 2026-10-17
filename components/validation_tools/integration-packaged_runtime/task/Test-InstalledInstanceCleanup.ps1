$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '../../RuntimeConnection.ps1')
. (Join-Path $PSScriptRoot 'InstalledInstanceCleanup.ps1')
Add-Type -AssemblyName Microsoft.VisualBasic
$fixtureRoot=Join-Path ([IO.Path]::GetTempPath()) ('installed-cleanup-'+[guid]::NewGuid().ToString('N'))
$roots=[Collections.Generic.List[string]]::new()
$retained=[Collections.Generic.List[string]]::new()
function Assert-True([bool]$Value,[string]$Message){if(-not$Value){throw $Message}}
function New-Fixture([string]$Name){
    $parent=Join-Path $fixtureRoot ($Name+'/'+('p'*70))
    $root=Join-Path $parent '26.3-neoforge'
    [IO.Directory]::CreateDirectory((Join-Path $root 'client/minecraft-protocol'))|Out-Null
    [IO.File]::WriteAllText((Join-Path $root 'owner.json'),'{"owner":"packaged-runtime-clean-install","target":"26.3-neoforge"}')
    $payload=Join-Path $root ('installation/libraries/'+('n'*50)+'/'+('v'*50)+'/fixture.txt')
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($payload))|Out-Null
    [IO.File]::WriteAllText($payload,'recoverable fixture payload')
    $roots.Add($root)
    return [pscustomobject]@{Root=$root;Parent=$parent;Payload=$payload}
}
function Retire-Fixture($Fixture,[scriptblock]$RecycleAction){
    Retire-InstalledRuntimeInstance -Root $Fixture.Root -ExpectedParent $Fixture.Parent -Target '26.3-neoforge' -ShortParent ([IO.Path]::GetTempPath()) -RecycleAction $RecycleAction
}
try{
    $long=New-Fixture 'long'
    Assert-True ($long.Payload.Length-gt260) 'Synthetic source must actually exceed MAX_PATH'
    $result=Retire-Fixture $long {
        param($path)
        $max=@(Get-ChildItem -LiteralPath $path -Recurse -Force|ForEach-Object{$_.FullName.Length}|Sort-Object -Descending)[0]
        Assert-True ($max-lt248) 'Shell staging must shorten the actual descendant paths'
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($path,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)
    }
    Assert-True ($null-eq$result-and-not(Test-Path -LiteralPath $long.Root)) 'Long fixture must be recycled, not reused'

    $failed=New-Fixture 'failure'
    $saved=Retire-Fixture $failed {throw 'injected recycle failure'}
    $retained.Add($saved)
    Assert-True (-not(Test-Path -LiteralPath $failed.Root)-and(Test-Path -LiteralPath $saved)) 'Failed recycling must retain the entire staged copy'
    $copy=Get-ChildItem -LiteralPath $saved -Recurse -Filter 'fixture.txt'|Select-Object -First 1
    Assert-True ([IO.File]::ReadAllText($copy.FullName)-ceq'recoverable fixture payload') 'Retained bytes must remain intact'

    $foreign=New-Fixture 'foreign'
    [IO.File]::WriteAllText((Join-Path $foreign.Root 'owner.json'),'{"owner":"someone-else","target":"26.3-neoforge"}')
    $denied=$false
    try{Retire-Fixture $foreign {throw 'must not reach recycling'}}catch{$denied=$true}
    Assert-True ($denied-and(Test-Path -LiteralPath $foreign.Payload)) 'Foreign data must remain unmoved'

    $mismatch=New-Fixture 'mismatch'
    foreach($kind in @('target','parent')){
        $denied=$false
        try{Retire-InstalledRuntimeInstance -Root $mismatch.Root -ExpectedParent $(if($kind-eq'parent'){$fixtureRoot}else{$mismatch.Parent}) -Target $(if($kind-eq'target'){'1.20.1-forge'}else{'26.3-neoforge'}) -ShortParent ([IO.Path]::GetTempPath())}catch{$denied=$true}
        Assert-True ($denied-and(Test-Path -LiteralPath $mismatch.Payload)) 'Wrong Target or parent must not move data'
    }

    $live=New-Fixture 'live'
    $held=[IO.File]::Open((Join-Path $live.Root 'client/minecraft-protocol/instance.lock'),[IO.FileMode]::Create,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try{
        $denied=$false
        try{Retire-Fixture $live {throw 'must not reach recycling'}}catch{$denied=$true}
        Assert-True ($denied-and(Test-Path -LiteralPath $live.Payload)) 'Open Runtime ownership lock must reject retirement'
    }finally{$held.Dispose()}
    Write-Output 'PASS installed cleanup: actual long-path recycle, retained failure bytes, owner, Target, parent and live-lock rejection; no permanent deletion'
}finally{
    foreach($root in $roots){
        if(-not(Test-Path -LiteralPath $root)){continue}
        [IO.File]::WriteAllText((Join-Path $root 'owner.json'),'{"owner":"packaged-runtime-clean-install","target":"26.3-neoforge"}')
        $path=Retire-InstalledRuntimeInstance -Root $root -ExpectedParent ([IO.Path]::GetDirectoryName($root)) -Target '26.3-neoforge' -ShortParent ([IO.Path]::GetTempPath())
        if($path){$retained.Add($path)}
    }
    foreach($path in $retained){
        if(Test-Path -LiteralPath $path){[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($path,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)}
    }
    if(Test-Path -LiteralPath $fixtureRoot){[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($fixtureRoot,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)}
}
