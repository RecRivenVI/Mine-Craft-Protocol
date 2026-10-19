$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '../../RuntimeConnection.ps1')
. (Join-Path $PSScriptRoot 'InstalledInstanceCleanup.ps1')
if($IsWindows){Add-Type -AssemblyName Microsoft.VisualBasic}
$fixtureRoot=Join-Path ([IO.Path]::GetTempPath()) ('installed-cleanup-'+[guid]::NewGuid().ToString('N'))
$cleanupRoot=Join-Path ([IO.Path]::GetTempPath()) ('.ic-'+[guid]::NewGuid().ToString('N').Substring(0,8))
[IO.Directory]::CreateDirectory($cleanupRoot)|Out-Null
$roots=[Collections.Generic.List[string]]::new()
$retained=[Collections.Generic.List[string]]::new()
function Assert-True([bool]$Value,[string]$Message){if(-not$Value){throw $Message}}
function Clear-TestDirectory {
    param([string]$Path,[bool]$Windows=$IsWindows,
        [scriptblock]$Recycle={param($p)[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($p,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)},
        [scriptblock]$Temporary={param($p)[IO.Directory]::Delete($p,$true)})
    $resolved=[IO.Path]::GetFullPath($Path)
    $short=[IO.Path]::GetFullPath($cleanupRoot)
    if($resolved-ine[IO.Path]::GetFullPath($fixtureRoot)-and$resolved-ine$short-and-not$resolved.StartsWith($short+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Test cleanup escaped its own temporary directories'}
    if($Windows){& $Recycle $resolved}else{& $Temporary $resolved}
}
$platformCleanup={param($path)Clear-TestDirectory $path}
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
    Retire-InstalledRuntimeInstance -Root $Fixture.Root -ExpectedParent $Fixture.Parent -Target '26.3-neoforge' -ShortParent $cleanupRoot -RecycleAction $RecycleAction
}
try{
    $branches=[Collections.Generic.List[string]]::new()
    foreach($windows in @($true,$false)){
        Clear-TestDirectory $cleanupRoot -Windows $windows -Recycle {param($p)$branches.Add('windows_recycle')} -Temporary {param($p)$branches.Add('nonwindows_temporary')}
    }
    Assert-True (($branches-join',')-ceq'windows_recycle,nonwindows_temporary') 'Both platform branches must execute their selected cleanup, not skip the test'
    $escaped=$false
    try{Clear-TestDirectory ([IO.Path]::GetTempPath()) -Windows $false -Temporary {throw 'must not reach deletion'}}catch{$escaped=$true}
    Assert-True $escaped 'Test-only removal must never accept the broad temporary parent'
    $long=New-Fixture 'long'
    Assert-True ($long.Payload.Length-gt260) 'Synthetic source must actually exceed MAX_PATH'
    $result=Retire-Fixture $long {
        param($path)
        $max=@(Get-ChildItem -LiteralPath $path -Recurse -Force|ForEach-Object{$_.FullName.Length}|Sort-Object -Descending)[0]
        Assert-True ($max-lt248) 'Shell staging must shorten the actual descendant paths'
        Clear-TestDirectory $path
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
        try{Retire-InstalledRuntimeInstance -Root $mismatch.Root -ExpectedParent $(if($kind-eq'parent'){$fixtureRoot}else{$mismatch.Parent}) -Target $(if($kind-eq'target'){'1.20.1-forge'}else{'26.3-neoforge'}) -ShortParent $cleanupRoot -RecycleAction $platformCleanup}catch{$denied=$true}
        Assert-True ($denied-and(Test-Path -LiteralPath $mismatch.Payload)) 'Wrong Target or parent must not move data'
    }

    $live=New-Fixture 'live'
    $held=[IO.File]::Open((Join-Path $live.Root 'client/minecraft-protocol/instance.lock'),[IO.FileMode]::Create,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try{
        $denied=$false
        try{Retire-Fixture $live {throw 'must not reach recycling'}}catch{$denied=$true}
        Assert-True ($denied-and(Test-Path -LiteralPath $live.Payload)) 'Open Runtime ownership lock must reject retirement'
    }finally{$held.Dispose()}
    $policy=if($IsWindows){'Windows Recycle Bin; no permanent deletion'}else{'non-Windows owned temporary test directories only'}
    Write-Output "PASS installed cleanup: both platform branches, actual long-path cleanup, retained failure bytes, owner, Target, parent and live-lock rejection; $policy"
}finally{
    foreach($root in $roots){
        if(-not(Test-Path -LiteralPath $root)){continue}
        [IO.File]::WriteAllText((Join-Path $root 'owner.json'),'{"owner":"packaged-runtime-clean-install","target":"26.3-neoforge"}')
        $path=Retire-InstalledRuntimeInstance -Root $root -ExpectedParent ([IO.Path]::GetDirectoryName($root)) -Target '26.3-neoforge' -ShortParent $cleanupRoot -RecycleAction $platformCleanup
        if($path){$retained.Add($path)}
    }
    foreach($path in $retained){
        if(Test-Path -LiteralPath $path){Clear-TestDirectory $path}
    }
    if(Test-Path -LiteralPath $fixtureRoot){Clear-TestDirectory $fixtureRoot}
    if(Test-Path -LiteralPath $cleanupRoot){Clear-TestDirectory $cleanupRoot}
}
