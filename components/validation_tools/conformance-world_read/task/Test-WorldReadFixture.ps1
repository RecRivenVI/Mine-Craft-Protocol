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
$baselineFacts="loader_version=test`nloader_minimum=minimum`njava_version=25`nnetty_http_version=retained`n"
$originCommit='1111111111111111111111111111111111111111'
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
function Refuses-UnchangedFixture([scriptblock]$Action,[string]$Message){
    $before=Get-WorldReadFixtureHash @(Get-WorldReadFixtureFiles $client)
    Refuses $Action $Message
    Require ((Get-WorldReadFixtureHash @(Get-WorldReadFixtureFiles $client))-ceq$before) ($Message+'; working copy must not be retired or changed')
}
try{
    Write-FixtureText (Join-Path $client 'world-read-owner.json') ('{"target":"'+$target+'","owner":"world-read-classification"}')
    Write-FixtureText (Join-Path $client 'saves/World-Read-Alpha/level.dat') 'synthetic-fixture-not-a-Minecraft-save'
    Write-FixtureText (Join-Path $client 'saves/World-Read-Alpha/session.lock') 'synthetic-lock'
    Write-FixtureText (Join-Path $client 'options.txt') "soundCategory_master:0.0`n"
    Write-FixtureText (Join-Path $client 'minecraft-protocol/token') 'must-not-copy'
    Write-FixtureText (Join-Path $client 'logs/latest.log') 'must-not-copy'
    Write-FixtureText $facts $baselineFacts
    Write-FixtureText $report ('{"target":"'+$target+'","status":"FAIL","cleanShutdown":true,"sourceChangedDuringRun":false}')
    Refuses {Save-WorldReadFixture $root $target $facts $report} 'Failed runs cannot become baselines'
    Write-FixtureText $report ('{"target":"'+$target+'","status":"PASS","cleanShutdown":true,"sourceChangedDuringRun":false,"testedCommit":"'+$originCommit+'"}')
    $manifest=Save-WorldReadFixture $root $target $facts $report
    Require ($manifest.hash-match'^[0-9a-f]{64}$') 'Baseline hash required'
    Require ($manifest.files.Count-eq4) 'Only world inputs, settings and owner are captured'
    Require (-not(Test-Path (Join-Path $root 'baseline/minecraft-protocol'))) 'Credentials must not enter the baseline'
    Refuses {Save-WorldReadFixture $root $target $facts $report} 'Baseline is immutable'
    Write-FixtureText (Join-Path $client 'saves/World-Read-Alpha/level.dat') 'changed by previous run'
    Write-FixtureText (Join-Path $client 'saves/unexpected-world/level.dat') 'extra previous world'
    $receipt=Restore-WorldReadFixture $root $target $facts
    Require ($receipt.baselineSha256-eq$manifest.hash-and$receipt.restoredSha256-eq$manifest.hash) 'Restoration must have exact input hash'
    Require ($receipt.compatibilityBasis-ceq'target_facts_exact_match'-and$receipt.targetFactsSha256-ceq$manifest.targetFactsSha256-and$receipt.currentTargetFactsSha256-ceq$receipt.baselineTargetFactsSha256-and$receipt.targetFactsChanges.Count-eq0) 'Exact facts retain the default strict restoration contract'
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
    Write-FixtureText $facts $baselineFacts
    $originReader=(Get-Item Function:Read-WorldReadFixtureOriginTargetFacts).ScriptBlock
    $script:originFacts=[Text.UTF8Encoding]::new($false).GetBytes($baselineFacts)
    $script:originUnavailable=$false
    $script:originReads=0
    function Read-WorldReadFixtureOriginTargetFacts {
        param([string]$Commit,[string]$Target)
        Require ($Commit-ceq$originCommit) 'Compatibility must read exactly the manifest origin commit'
        Require ($Target-cin@('26.3-neoforge','26.3-fabric')) 'Compatibility must read the requested Target'
        $script:originReads++
        if($script:originUnavailable){throw 'Synthetic missing Git blob'}
        return ,$script:originFacts
    }
    try{
        $beforeReads=$script:originReads
        [void](Get-WorldReadFixture $root $target $facts -AllowLoaderUpdate)
        Require ($script:originReads-eq$beforeReads) 'Identical facts must not require Git history'
        $checks++
        $updatedFacts=$baselineFacts.Replace('loader_version=test','loader_version=updated')
        Write-FixtureText $facts $updatedFacts
        Write-FixtureText (Join-Path $client 'preserve-on-rejection.txt') 'must survive every rejected restore'
        Refuses-UnchangedFixture {Restore-WorldReadFixture $root $target $facts} 'Loader compatibility must be explicitly requested'
        $baselineManifest=Join-Path $root 'baseline/fixture-manifest.json'
        $beforeManifest=(Get-FileHash -LiteralPath $baselineManifest -Algorithm SHA256).Hash
        $compatible=Restore-WorldReadFixture $root $target $facts -AllowLoaderUpdate
        Require ($compatible.targetFactsSha256-ceq(Get-FileHash -LiteralPath $facts -Algorithm SHA256).Hash.ToLowerInvariant()) 'Restoration must bind the current Target facts'
        Require ($compatible.currentTargetFactsSha256-ceq$compatible.targetFactsSha256-and$compatible.baselineTargetFactsSha256-ceq$manifest.targetFactsSha256-and$compatible.originTestedCommit-ceq$originCommit) 'Original and current facts must remain distinct'
        Require ($compatible.compatibilityBasis-ceq'same_target_and_minecraft_loader_update_only'-and$compatible.targetFactsChanges.Count-eq1-and$compatible.targetFactsChanges[0].key-ceq'loader_version'-and$compatible.targetFactsChanges[0].before-ceq'test'-and$compatible.targetFactsChanges[0].after-ceq'updated') 'Compatibility receipt must describe only the actual Loader change'
        Require ($compatible.baselineSha256-ceq$manifest.hash-and$compatible.restoredSha256-ceq$manifest.hash-and(Get-FileHash -LiteralPath $baselineManifest -Algorithm SHA256).Hash-ceq$beforeManifest) 'Loader update must not rewrite the baseline, its manifest or world hashes'
        $persisted=Get-Content -Raw (Join-Path $client 'fixture-input.json')|ConvertFrom-Json
        Require ($persisted.currentTargetFactsSha256-ceq$compatible.targetFactsSha256-and$persisted.targetFactsChanges[0].key-ceq'loader_version') 'The persisted restoration receipt must retain the compatibility proof'
        $checks++
        Write-FixtureText (Join-Path $client 'preserve-on-rejection.txt') 'must survive every rejected restore'
        $script:originUnavailable=$true
        Refuses-UnchangedFixture {Restore-WorldReadFixture $root $target $facts -AllowLoaderUpdate} 'Missing origin Git blob must fail before restore'
        $script:originUnavailable=$false
        $script:originFacts=[Text.UTF8Encoding]::new($false).GetBytes($baselineFacts.Replace("`n","`r`n"))
        Refuses-UnchangedFixture {Restore-WorldReadFixture $root $target $facts -AllowLoaderUpdate} 'Origin Git bytes must match the original SHA without newline normalization'
        $script:originFacts=[Text.UTF8Encoding]::new($false).GetBytes($baselineFacts)
        foreach($invalid in @(
            @{name='Java';text=$updatedFacts.Replace('java_version=25','java_version=26')},
            @{name='minimum Loader';text=$updatedFacts.Replace('loader_minimum=minimum','loader_minimum=changed')},
            @{name='Netty dependency';text=$updatedFacts.Replace('netty_http_version=retained','netty_http_version=changed')},
            @{name='new fact';text=$updatedFacts+"new_fact=value`n"},
            @{name='missing fact';text=$updatedFacts.Replace("loader_minimum=minimum`n",'')},
            @{name='duplicate fact';text=$updatedFacts+"loader_version=duplicate`n"}
        )){
            Write-FixtureText $facts $invalid.text
            Refuses-UnchangedFixture {Restore-WorldReadFixture $root $target $facts -AllowLoaderUpdate} ($invalid.name+' changes must not be accepted as a Loader update')
        }
        Write-FixtureText $facts $updatedFacts
        Refuses-UnchangedFixture {Restore-WorldReadFixture $root '1.20.1-forge' $facts -AllowLoaderUpdate} 'Compatibility must not change the baseline Target'
        $originalManifest=Get-Content -Raw $baselineManifest
        $badOrigin=$originalManifest|ConvertFrom-Json
        $badOrigin.originTestedCommit='HEAD'
        Write-FixtureText $baselineManifest ($badOrigin|ConvertTo-Json -Depth 8)
        try{Refuses-UnchangedFixture {Restore-WorldReadFixture $root $target $facts -AllowLoaderUpdate} 'A mutable or abbreviated origin identity must not replace the recorded commit'}finally{Write-FixtureText $baselineManifest $originalManifest}
        $fabricBefore="loader_version=old`njava_version=25`nfabric_api_version=old_api`n"
        $script:originFacts=[Text.UTF8Encoding]::new($false).GetBytes($fabricBefore)
        $fabricManifest=[pscustomobject]@{originTestedCommit=$originCommit;targetFactsSha256=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($script:originFacts)).ToLowerInvariant()}
        Write-FixtureText $facts ($fabricBefore.Replace('loader_version=old','loader_version=new').Replace('fabric_api_version=old_api','fabric_api_version=new_api'))
        $fabric=Get-WorldReadFixtureFactsCompatibility $fabricManifest '26.3-fabric' $facts -AllowLoaderUpdate
        Require ($fabric.targetFactsChanges.Count-eq2-and($fabric.targetFactsChanges.key-join',')-ceq'fabric_api_version,loader_version') 'Fabric permits only its Loader and Fabric API fact changes'
        $checks++
        Refuses {Get-WorldReadFixtureFactsCompatibility $fabricManifest '26.3-neoforge' $facts -AllowLoaderUpdate} 'Fabric API changes must not be allowed for a non-Fabric Target'
    }finally{
        Set-Item Function:Read-WorldReadFixtureOriginTargetFacts $originReader
        Write-FixtureText $facts $baselineFacts
    }
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
