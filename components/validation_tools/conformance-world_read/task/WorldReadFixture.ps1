function Assert-WorldReadFixtureOwner {
    param([string]$Client,[string]$Target)
    $marker=Join-Path $Client 'world-read-owner.json'
    if(-not(Test-Path -LiteralPath $marker)){throw 'Unowned world fixture will not be changed'}
    $value=Get-Content -LiteralPath $marker -Raw|ConvertFrom-Json
    if($value.target-ne$Target-or$value.owner-ne'world-read-classification'){throw 'World fixture ownership mismatch'}
}

function Get-WorldReadFixtureFiles {
    param([string]$Directory)
    $root=[IO.Path]::GetFullPath($Directory).TrimEnd('\','/')
    if((Get-Item -LiteralPath $root -Force).Attributes-band[IO.FileAttributes]::ReparsePoint){throw 'Fixture root is a link'}
    $queue=[Collections.Generic.Queue[string]]::new()
    $queue.Enqueue($root)
    $files=[Collections.Generic.List[object]]::new()
    [long]$bytes=0
    while($queue.Count){
        foreach($entry in Get-ChildItem -LiteralPath $queue.Dequeue() -Force){
            if($entry.Attributes-band[IO.FileAttributes]::ReparsePoint){throw 'Fixture contains a link'}
            if($entry.PSIsContainer){$queue.Enqueue($entry.FullName);continue}
            if($files.Count-ge100000-or($bytes+=$entry.Length)-gt17179869184){throw 'Fixture exceeds 100000 files or 16 GiB'}
            $files.Add([pscustomobject]@{path=[IO.Path]::GetRelativePath($root,$entry.FullName).Replace('\','/');sha256=(Get-FileHash -LiteralPath $entry.FullName -Algorithm SHA256).Hash.ToLowerInvariant()})
        }
    }
    return @($files|Sort-Object path -CaseSensitive)
}

function Get-WorldReadFixtureHash {
    param([object[]]$Files)
    $content=($Files|ForEach-Object{$_.path+':'+$_.sha256})-join"`n"
    return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($content))).ToLowerInvariant()
}

function Assert-WorldReadFixtureStopped {
    param([string]$Client)
    Assert-RuntimeInstanceStopped $Client
    if(Test-Path -LiteralPath (Join-Path $Client 'saves')){
        # Keep an external Minecraft process holding session.lock from being mistaken for offline.
        [void](Get-WorldReadFixtureFiles (Join-Path $Client 'saves'))
        foreach($file in Get-ChildItem -LiteralPath (Join-Path $Client 'saves') -Filter session.lock -File -Recurse){
            $handle=[IO.File]::Open($file.FullName,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
            $handle.Dispose()
        }
    }
}

function Read-WorldReadFixtureOriginTargetFacts {
    param([string]$Commit,[string]$Target)
    $start=[Diagnostics.ProcessStartInfo]::new('git')
    $start.WorkingDirectory=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../..'))
    $start.UseShellExecute=$false
    $start.CreateNoWindow=$true
    $start.RedirectStandardOutput=$true
    $start.RedirectStandardError=$true
    $start.ArgumentList.Add('show')
    $start.ArgumentList.Add($Commit+':versions/'+$Target+'/target.properties')
    $process=[Diagnostics.Process]::new()
    $process.StartInfo=$start
    $bytes=[IO.MemoryStream]::new()
    try{
        [void]$process.Start()
        $errorText=$process.StandardError.ReadToEndAsync()
        # Hash the Git blob itself; PowerShell text output can change its encoding or newlines.
        $process.StandardOutput.BaseStream.CopyTo($bytes)
        $process.WaitForExit()
        if($process.ExitCode-ne0){throw ('World baseline origin Target facts are unavailable: '+$errorText.GetAwaiter().GetResult().Trim())}
        return ,$bytes.ToArray()
    }finally{$bytes.Dispose();$process.Dispose()}
}

function ConvertFrom-WorldReadTargetFacts {
    param([byte[]]$Bytes)
    $facts=[Collections.Generic.Dictionary[string,string]]::new([StringComparer]::Ordinal)
    foreach($line in [Text.UTF8Encoding]::new($false,$true).GetString($Bytes).Split("`n")){
        $value=$line.Trim()
        if(-not$value-or$value.StartsWith('#')){continue}
        if($value-cnotmatch'^([a-z][a-z0-9_]*)=(.+)$'-or-not$facts.TryAdd($Matches[1],$Matches[2])){throw 'Invalid or duplicate world baseline Target fact'}
    }
    return ,$facts
}

function Get-WorldReadFixtureFactsCompatibility {
    param($Manifest,[string]$Target,[string]$TargetFacts,[switch]$AllowLoaderUpdate)
    $currentBytes=[IO.File]::ReadAllBytes($TargetFacts)
    $currentHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($currentBytes)).ToLowerInvariant()
    $changes=[Collections.Generic.List[object]]::new()
    $basis='target_facts_exact_match'
    if($Manifest.targetFactsSha256-cne$currentHash){
        if(-not$AllowLoaderUpdate){throw 'World baseline Target facts changed; explicit fixture replacement required'}
        if($Manifest.originTestedCommit-cnotmatch'^[0-9a-f]{40}$'){throw 'World baseline origin commit is not a full Git commit identity'}
        $originalBytes=Read-WorldReadFixtureOriginTargetFacts $Manifest.originTestedCommit $Target
        $originalHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([byte[]]$originalBytes)).ToLowerInvariant()
        if($originalHash-cne$Manifest.targetFactsSha256){throw 'World baseline origin Target facts do not match the recorded hash'}
        $original=ConvertFrom-WorldReadTargetFacts $originalBytes
        $current=ConvertFrom-WorldReadTargetFacts $currentBytes
        if($original.Count-ne$current.Count){throw 'World baseline Target fact keys changed'}
        foreach($key in $original.Keys){
            if(-not$current.ContainsKey($key)){throw 'World baseline Target fact keys changed'}
            if($original[$key]-ceq$current[$key]){continue}
            if($key-cne'loader_version'-and-not($Target.EndsWith('-fabric',[StringComparison]::Ordinal)-and$key-ceq'fabric_api_version')){throw ('World baseline non-Loader Target fact changed: '+$key)}
            $changes.Add([pscustomobject]@{key=$key;before=$original[$key];after=$current[$key]})
        }
        $basis='same_target_and_minecraft_loader_update_only'
    }
    return [pscustomobject]@{targetFactsSha256=$currentHash;currentTargetFactsSha256=$currentHash;baselineTargetFactsSha256=$Manifest.targetFactsSha256;originTestedCommit=$Manifest.originTestedCommit;compatibilityBasis=$basis;targetFactsChanges=@($changes|Sort-Object key -CaseSensitive)}
}

function Get-WorldReadFixture {
    param([string]$Root,[string]$Target,[string]$TargetFacts,[switch]$AllowLoaderUpdate,[ref]$FactsCompatibility)
    $baseline=Join-Path $Root 'baseline'
    $manifestFile=Join-Path $baseline 'fixture-manifest.json'
    if(-not(Test-Path -LiteralPath $manifestFile)){throw 'World baseline is missing or incomplete'}
    if((Get-Item -LiteralPath $manifestFile).Length-gt16777216){throw 'World baseline manifest exceeds 16 MiB'}
    $manifest=Get-Content -LiteralPath $manifestFile -Raw|ConvertFrom-Json
    if($manifest.schemaVersion-ne1-or$manifest.target-ne$Target-or$manifest.role-ne'client'-or$manifest.owner-ne'world-read-classification'){throw 'World baseline identity mismatch'}
    $compatibility=Get-WorldReadFixtureFactsCompatibility $manifest $Target $TargetFacts -AllowLoaderUpdate:$AllowLoaderUpdate
    if(-not$manifest.files-or$manifest.files.Count-gt100000){throw 'Invalid world baseline file list'}
    $names=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach($file in $manifest.files){
        if($file.path-notmatch'^(saves/[^/].*|resourcepacks/[^/].*|options\.txt|servers\.dat|world-read-owner\.json)$'-or$file.path.Contains('\')-or$file.path.Split('/')-contains'..'-or$file.path.Split('/')-contains'.'-or$file.path.Contains(':')-or$file.sha256-notmatch'^[0-9a-f]{64}$'-or-not$names.Add($file.path)){throw 'Invalid world baseline manifest entry'}
    }
    $actual=@(Get-WorldReadFixtureFiles $baseline|Where-Object path -ne 'fixture-manifest.json')
    if($actual.Count-ne$manifest.files.Count-or(Get-WorldReadFixtureHash $actual)-ne$manifest.hash-or(Get-WorldReadFixtureHash $manifest.files)-ne$manifest.hash){throw 'World baseline content/hash mismatch'}
    Assert-WorldReadFixtureOwner $baseline $Target
    if($null-ne$FactsCompatibility){$FactsCompatibility.Value=$compatibility}
    return $manifest
}

function Save-WorldReadFixture {
    param([string]$Root,[string]$Target,[string]$TargetFacts,[string]$PreviousResult)
    $client=Join-Path $Root 'client'
    Assert-WorldReadFixtureOwner $client $Target
    Assert-WorldReadFixtureStopped $client
    if(-not(Test-Path -LiteralPath $PreviousResult)){throw 'A clean passing preparation run is required before capturing a world baseline'}
    $result=Get-Content -LiteralPath $PreviousResult -Raw|ConvertFrom-Json
    if($result.target-ne$Target-or$result.status-ne'PASS'-or-not$result.cleanShutdown-or$result.sourceChangedDuringRun){throw 'Only a clean passing world preparation can become a baseline'}
    if(-not(Test-Path -LiteralPath (Join-Path $client 'saves/World-Read-Alpha/level.dat'))){throw 'Expected dedicated test world is missing'}
    $baseline=Join-Path $Root 'baseline'
    $preparing=Join-Path $Root 'baseline.preparing'
    if((Test-Path -LiteralPath $baseline)-or(Test-Path -LiteralPath $preparing)){throw 'Immutable world baseline or incomplete capture already exists; it will not be overwritten'}
    $inputPattern='^(saves/|resourcepacks/|options\.txt$|servers\.dat$|world-read-owner\.json$)'
    $before=@(Get-WorldReadFixtureFiles $client|Where-Object path -match $inputPattern)
    [IO.Directory]::CreateDirectory($preparing)|Out-Null
    foreach($entry in @('saves','resourcepacks','options.txt','servers.dat','world-read-owner.json')){
        $source=Join-Path $client $entry
        if(Test-Path -LiteralPath $source){Copy-Item -LiteralPath $source -Destination (Join-Path $preparing $entry) -Recurse}
    }
    $files=@(Get-WorldReadFixtureFiles $preparing)
    $after=@(Get-WorldReadFixtureFiles $client|Where-Object path -match $inputPattern)
    if((Get-WorldReadFixtureHash $before)-ne(Get-WorldReadFixtureHash $after)-or(Get-WorldReadFixtureHash $files)-ne(Get-WorldReadFixtureHash $before)){throw 'World inputs changed during baseline capture; incomplete capture retained'}
    $manifest=@{schemaVersion=1;owner='world-read-classification';target=$Target;role='client';targetFactsSha256=(Get-FileHash -LiteralPath $TargetFacts -Algorithm SHA256).Hash.ToLowerInvariant();createdAt=[DateTime]::UtcNow.ToString('o');originTestedCommit=$result.testedCommit;files=$files;hash=Get-WorldReadFixtureHash $files}
    [IO.File]::WriteAllText((Join-Path $preparing 'fixture-manifest.json'),($manifest|ConvertTo-Json -Depth 8)+"`n",[Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $preparing -Destination $baseline
    return Get-WorldReadFixture $Root $Target $TargetFacts
}

function Restore-WorldReadFixture {
    param([string]$Root,[string]$Target,[string]$TargetFacts,[switch]$AllowLoaderUpdate)
    $rootPath=[IO.Path]::GetFullPath($Root).TrimEnd('\','/')
    $client=[IO.Path]::GetFullPath((Join-Path $rootPath 'client'))
    if(-not$client.StartsWith($rootPath+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)-or(Split-Path $rootPath -Leaf)-ne$Target){throw 'Unsafe world fixture restore path'}
    $compatibility=$null
    $manifest=Get-WorldReadFixture $rootPath $Target $TargetFacts -AllowLoaderUpdate:$AllowLoaderUpdate -FactsCompatibility ([ref]$compatibility)
    Assert-WorldReadFixtureOwner $client $Target
    Assert-WorldReadFixtureStopped $client
    # Validate everything before retiring the working copy. Never permanently delete test worlds.
    [void](Get-WorldReadFixtureFiles $client)
    Add-Type -AssemblyName Microsoft.VisualBasic
    try{[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($client,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)}
    catch{
        $retired=[IO.Path]::GetFullPath((Join-Path $rootPath ('retired/client-'+[guid]::NewGuid().ToString('N'))))
        if(-not$retired.StartsWith($rootPath+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Unsafe retired fixture path'}
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($retired))|Out-Null
        Move-Item -LiteralPath $client -Destination $retired
        Write-Warning "Recycle Bin unavailable; retained owned fixture at $retired"
    }
    [IO.Directory]::CreateDirectory($client)|Out-Null
    foreach($file in $manifest.files){
        $destination=Join-Path $client $file.path
        [IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination))|Out-Null
        Copy-Item -LiteralPath (Join-Path (Join-Path $rootPath 'baseline') $file.path) -Destination $destination
    }
    $restored=@(Get-WorldReadFixtureFiles $client)
    if($restored.Count-ne$manifest.files.Count-or(Get-WorldReadFixtureHash $restored)-ne$manifest.hash){throw 'Restored world fixture differs from its baseline'}
    $receipt=@{policy='immutable_baseline_restored';target=$Target;role='client';baselineSha256=$manifest.hash;restoredSha256=Get-WorldReadFixtureHash $restored;fileCount=$restored.Count;restoredAt=[DateTime]::UtcNow.ToString('o');credentialsCopied=$false}
    foreach($property in $compatibility.PSObject.Properties){$receipt[$property.Name]=$property.Value}
    [IO.File]::WriteAllText((Join-Path $client 'fixture-input.json'),($receipt|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
    return $receipt
}
