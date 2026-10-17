$ErrorActionPreference='Stop'
. (Join-Path $PSScriptRoot '../../RuntimeConnection.ps1')
. (Join-Path $PSScriptRoot 'WorldReadFixture.ps1')
$temporary=Join-Path ([IO.Path]::GetTempPath()) ('world-read-fixture-'+[guid]::NewGuid().ToString('N'))
$target='26.3-neoforge'
$root=Join-Path $temporary $target
$client=Join-Path $root 'client'
$facts=Join-Path $temporary 'target.properties'
$report=Join-Path $temporary 'result.json'
$checks=0
function Write-FixtureText([string]$Path,[string]$Text){
    [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($Path))|Out-Null
    [IO.File]::WriteAllText($Path,$Text,[Text.UTF8Encoding]::new($false))
}
function Require([bool]$Condition,[string]$Message){if(-not$Condition){throw $Message}}
function Refuses([scriptblock]$Action,[string]$Message){
    $refused=$false
    try{& $Action|Out-Null}catch{$refused=$true}
    Require $refused $Message
    $script:checks++
}
try{
    Write-FixtureText (Join-Path $client 'world-read-owner.json') ('{"target":"'+$target+'","owner":"world-read-classification"}')
    Write-FixtureText (Join-Path $client 'saves/World-Read-Alpha/level.dat') 'synthetic-fixture-not-a-Minecraft-save'
    Write-FixtureText (Join-Path $client 'saves/World-Read-Alpha/session.lock') 'synthetic-lock'
    Write-FixtureText (Join-Path $client 'options.txt') "soundCategory_master:0.0`n"
    Write-FixtureText (Join-Path $client 'minecraft-protocol/token') 'must-not-copy'
    Write-FixtureText (Join-Path $client 'logs/latest.log') 'must-not-copy'
    Write-FixtureText $facts "loader_version=test`njava_version=25`n"
    Write-FixtureText $report ('{"target":"'+$target+'","status":"FAIL","cleanShutdown":true,"sourceChangedDuringRun":false}')
    Refuses {Save-WorldReadFixture $root $target $facts $report} 'Failed runs cannot become baselines'
    Write-FixtureText $report ('{"target":"'+$target+'","status":"PASS","cleanShutdown":true,"sourceChangedDuringRun":false,"testedCommit":"synthetic"}')
    $manifest=Save-WorldReadFixture $root $target $facts $report
    Require ($manifest.hash-match'^[0-9a-f]{64}$') 'Baseline hash required'
    Require ($manifest.files.Count-eq4) 'Only world inputs, settings and owner are captured'
    Require (-not(Test-Path (Join-Path $root 'baseline/minecraft-protocol'))) 'Credentials must not enter the baseline'
    Refuses {Save-WorldReadFixture $root $target $facts $report} 'Baseline is immutable'
    Write-FixtureText (Join-Path $client 'saves/World-Read-Alpha/level.dat') 'changed by previous run'
    Write-FixtureText (Join-Path $client 'saves/unexpected-world/level.dat') 'extra previous world'
    $receipt=Restore-WorldReadFixture $root $target $facts
    Require ($receipt.baselineSha256-eq$manifest.hash-and$receipt.restoredSha256-eq$manifest.hash) 'Restoration must have exact input hash'
    Require (-not(Test-Path (Join-Path $client 'saves/unexpected-world'))) 'Extra previous worlds cannot survive restore'
    Require (-not(Test-Path (Join-Path $client 'minecraft-protocol/token'))) 'Old authentication cannot survive restore'
    $again=Restore-WorldReadFixture $root $target $facts
    Require ($again.baselineSha256-eq$receipt.baselineSha256) 'Repeated runs use identical baseline'
    $checks++
    $lock=Join-Path $client 'minecraft-protocol/instance.lock'
    Write-FixtureText $lock 'owned-lock'
    $handle=[IO.File]::Open($lock,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try{Refuses {Restore-WorldReadFixture $root $target $facts} 'Runtime ownership must block restore'}finally{$handle.Dispose()}
    $session=Join-Path $client 'saves/World-Read-Alpha/session.lock'
    $handle=[IO.File]::Open($session,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try{Refuses {Restore-WorldReadFixture $root $target $facts} 'External Minecraft session must block restore'}finally{$handle.Dispose()}
    Refuses {Get-WorldReadFixture $root '1.20.1-forge' $facts} 'Wrong Target must fail'
    Write-FixtureText $facts 'changed-facts'
    Refuses {Restore-WorldReadFixture $root $target $facts} 'Changed Target facts must fail before touching the client'
    Write-FixtureText $facts "loader_version=test`njava_version=25`n"
    $baseline=Join-Path $root 'baseline'
    $level=Join-Path $baseline 'saves/World-Read-Alpha/level.dat'
    Write-FixtureText $level 'baseline-tampered'
    Refuses {Restore-WorldReadFixture $root $target $facts} 'Tampered input must fail'
    Write-FixtureText $level 'synthetic-fixture-not-a-Minecraft-save'
    $manifestFile=Join-Path $baseline 'fixture-manifest.json'
    $original=Get-Content $manifestFile -Raw
    $modified=$original|ConvertFrom-Json
    $modified.files[0].path='../escape'
    Write-FixtureText $manifestFile ($modified|ConvertTo-Json -Depth 8)
    Refuses {Restore-WorldReadFixture $root $target $facts} 'Path traversal must fail'
    Write-FixtureText $manifestFile $original
    Write-FixtureText (Join-Path $baseline 'extra.txt') 'unlisted-file'
    Refuses {Get-WorldReadFixture $root $target $facts} 'Unlisted baseline content must fail'
    Write-Output "PASS: $checks world fixture checks, immutable hashes, repeated restore, ownership/session locks, no copied credentials"
}finally{
    if(Test-Path -LiteralPath $temporary){
        $resolved=[IO.Path]::GetFullPath($temporary)
        if(-not$resolved.StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath()),[StringComparison]::OrdinalIgnoreCase)-or(Split-Path $resolved -Leaf)-notlike'world-read-fixture-*'){throw 'Unsafe synthetic fixture cleanup'}
        Add-Type -AssemblyName Microsoft.VisualBasic
        try{[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($resolved,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)}catch{Write-Warning "Synthetic fixture retained because Recycle Bin is unavailable: $resolved"}
    }
}
