[CmdletBinding()]param()
$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '../../RuntimeConnection.ps1')
. (Join-Path $PSScriptRoot 'ExtensionHost.Helpers.ps1')
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$checks=0
function MustReject([scriptblock]$Body){$failed=$false;try{& $Body|Out-Null}catch{$failed=$true};if(-not$failed){throw 'Expected host rejection'};$script:checks++}
foreach($path in Get-ChildItem -LiteralPath $PSScriptRoot -Filter '*.ps1'){
    $tokens=$null;$errors=$null;[void][Management.Automation.Language.Parser]::ParseFile($path.FullName,[ref]$tokens,[ref]$errors)
    if($errors.Count){throw "Invalid host script: $($path.Name)"};$checks++
}
$plan=Get-ExtensionHostPlan $repo '26.3-fabric' 'platform_probe' 'native-client'
if($plan.Role-ne'client'-or-not$plan.Game.Replace('\','/').EndsWith('26.3-fabric/client')){throw 'Wrong registered native host path'};$checks++
$server=Get-ExtensionHostPlan $repo '1.21.1-neoforge' 'ae2' 'installed-server'
if($server.Role-ne'server'-or-not$server.World.Replace('\','/').EndsWith('server/Extension-Server-ae2')){throw 'Server adapter fixture must be independent'};$checks++
MustReject {Get-ExtensionHostPlan $repo '../escape' 'platform_probe' 'native-client'}
MustReject {Get-ExtensionHostPlan $repo '26.3-fabric' '../escape' 'native-client'}
MustReject {Get-ExtensionHostPlan $repo '26.3-fabric' 'create' 'native-client'}
MustReject {Get-ExtensionHostPlan $repo '26.3-fabric' 'platform_probe' 'arbitrary-shell'}
$temporary=[IO.Path]::Combine([IO.Path]::GetTempPath(),'mine_craft_protocol-extension-host-'+[Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($temporary)|Out-Null
try{
    MustReject {Assert-ExtensionHostPath $temporary (Join-Path $temporary '../outside')}
    $game=Join-Path $temporary 'client';$world=Join-Path $game 'saves/fixed'
    [IO.Directory]::CreateDirectory($world)|Out-Null
    [IO.File]::WriteAllText((Join-Path $world 'level.dat'),'controlled-fixture')
    $sample=[pscustomobject]@{Base=$temporary;Game=$game;World=$world}
    if($IsWindows){
    Assert-ExtensionHostStopped $sample;$checks++
    $protocol=Join-Path $game 'mine_craft_protocol';[IO.Directory]::CreateDirectory($protocol)|Out-Null
    $endpoint=Join-Path $protocol 'endpoint.json';[IO.File]::WriteAllText($endpoint,'{}')
    MustReject {Assert-ExtensionHostStopped $sample}
    [IO.File]::Move($endpoint,(Join-Path $protocol 'retired-test-endpoint.json'))
    $sessionLock=Join-Path $world 'session.lock'
    $handle=[IO.File]::Open($sessionLock,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try{MustReject {Assert-ExtensionHostStopped $sample}}finally{$handle.Dispose()}
    Assert-ExtensionHostStopped $sample;$checks++
    }else{MustReject {Assert-ExtensionHostStopped $sample}}
    $before=Get-ExtensionFixtureFiles $temporary $world
    [IO.File]::WriteAllText((Join-Path $world 'level.dat'),'changed-fixture')
    $after=Get-ExtensionFixtureFiles $temporary $world
    if(($before|ConvertTo-Json -Compress)-ceq($after|ConvertTo-Json -Compress)){throw 'Changed fixture hash was not detected'};$checks++
}finally{if($IsWindows){Send-ExtensionHostRecycle ([IO.Path]::GetTempPath()) $temporary}}
Write-Output "PASS: $checks extension host checks; native endpoint/process/lock/Recycle workflows tested=$IsWindows"
