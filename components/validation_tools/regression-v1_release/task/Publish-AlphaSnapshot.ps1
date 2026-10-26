[CmdletBinding()]
param(
    [ValidateSet('Publish','Pin','Unpin','Withdraw')][string]$Mode='Publish',
    [string]$OutputRoot='D:/Workspaces/Artifacts/MineCraftProtocol/Alpha',
    [string]$RepositoryRoot=(Join-Path $PSScriptRoot '../../../..'),
    [string]$Snapshot,
    [string]$Consumer,
    [string]$Reason,
    [scriptblock]$RecycleAction
)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($OutputRoot).TrimEnd([IO.Path]::DirectorySeparatorChar)
$repo=[IO.Path]::GetFullPath($RepositoryRoot)
$warnings=[Collections.Generic.List[string]]::new()
$recycled=[Collections.Generic.List[string]]::new()
$namePattern='^\d{8}-\d{4}-[0-9a-f]{8}$'
function Assert-Regular([string]$Path){
    $item=Get-Item -LiteralPath $Path -Force
    if($item.Attributes-band[IO.FileAttributes]::ReparsePoint){throw "Alpha refuses a reparse point: $Path"}
    return $item
}
function Assert-Within([string]$Path,[string]$Parent){
    $full=[IO.Path]::GetFullPath($Path)
    if(-not$full.StartsWith($Parent.TrimEnd([IO.Path]::DirectorySeparatorChar)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw "Alpha path escapes its owner: $Path"}
    return $full
}
function Hash([string]$Path){return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()}
function Recycle([string]$Path){
    $full=Assert-Within $Path $root
    if(-not(Test-Path -LiteralPath $full)){return}
    try{
        $item=Assert-Regular $full
        if($RecycleAction){& $RecycleAction $full|Out-Null}
        else{
            if(-not$IsWindows){throw 'Recycle Bin is unavailable on this platform'}
            Add-Type -AssemblyName Microsoft.VisualBasic
            if($item.PSIsContainer){[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($full,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)}
            else{[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($full,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)}
        }
        if(Test-Path -LiteralPath $full){throw 'Recycle operation left the source in place'}
        $recycled.Add($full)
    }catch{$warnings.Add("Retained; recycling failed: $full : $($_.Exception.Message)")}
}
function Prepared-Time($Report){
    # ConvertFrom-Json can return a UTC DateTime rather than a string; parsing its display text loses the offset.
    if($Report.preparedAt-is[DateTimeOffset]){return $Report.preparedAt}
    if($Report.preparedAt-is[DateTime]){return [DateTimeOffset]::new($Report.preparedAt)}
    return [DateTimeOffset]::Parse($Report.preparedAt,[Globalization.CultureInfo]::InvariantCulture)
}
function Snapshot-Name($Report){
    if($Report.sourceFingerprint-cnotmatch'^[0-9a-f]{64}$'){throw 'Invalid alpha source fingerprint'}
    $time=(Prepared-Time $Report).ToOffset([TimeSpan]::FromHours(8))
    return $time.ToString('yyyyMMdd-HHmm')+'-'+$Report.sourceFingerprint.Substring(0,8)
}
function Bundle-Files($Report){
    $artifacts=@($Report.artifacts)
    if($Report.type-ne'internal_alpha_build'-or$artifacts.Count-ne5-or@($artifacts.target|Sort-Object -Unique).Count-ne5){throw 'Alpha requires one verified artifact for each of five Targets'}
    $files=[ordered]@{}
    foreach($artifact in $artifacts){
        $name=[IO.Path]::GetFileName($artifact.path)
        if($name-cnotmatch'^[A-Za-z0-9_.-]+\.jar$'-or$artifact.fileName-cne$name-or$files.Contains($name)-or$artifact.sha256-cnotmatch'^[0-9a-f]{64}$'){throw 'Invalid or duplicate alpha artifact entry'}
        $files[$name]=$artifact.sha256
    }
    if($Report.agentGuide.sha256-cnotmatch'^[0-9a-f]{64}$'){throw 'Alpha guide hash is missing'}
    $files['AGENT_GUIDE.md']=$Report.agentGuide.sha256
    return $files
}
function Assert-Snapshot([string]$Directory,[switch]$AllowWithdrawn){
    $directory=Assert-Within $Directory $root
    if([IO.Path]::GetDirectoryName($directory)-ine$root-or[IO.Path]::GetFileName($directory)-cnotmatch$namePattern){throw 'Invalid alpha snapshot directory'}
    if(-not(Assert-Regular $directory).PSIsContainer){throw 'Snapshot is not a directory'}
    if(-not$AllowWithdrawn-and(Test-Path -LiteralPath (Join-Path $root ('.withdrawn/'+[IO.Path]::GetFileName($directory)+'.json')))){throw "Alpha snapshot withdrawn; do not install: $directory"}
    $manifest=Join-Path $directory 'alpha-manifest.json'
    Assert-Regular $manifest|Out-Null
    $report=Get-Content -LiteralPath $manifest -Raw|ConvertFrom-Json
    if((Snapshot-Name $report)-cne[IO.Path]::GetFileName($directory)-or$report.distribution.layout-ne'flat_immutable_snapshot'-or[IO.Path]::GetFullPath($report.distribution.directory)-ine$directory){throw 'Snapshot identity/layout mismatch'}
    $files=Bundle-Files $report
    if(@(Get-ChildItem -LiteralPath $directory -Force).Count-ne7){throw 'Snapshot must contain exactly five JARs, one manifest and one guide'}
    foreach($name in $files.Keys){$path=Join-Path $directory $name;if((Assert-Regular $path).PSIsContainer-or(Hash $path)-cne$files[$name]){throw "Snapshot hash mismatch: $name"}}
    return $report
}
function Assert-Source($Report,[string]$Manifest){
    if($Report.publicationProfile-cnotin@('continuous_gameplay_trial_v1','dependency_update_trial_v1','e_ui_trial_v1','e_world_trial_v1')){throw 'New alpha publications require an explicit trial profile; historical snapshots are not rewritten'}
    if(-not$Report.sourceHashes-or-not$Report.agentGuide.sourceSha256){throw 'Source/guide provenance is missing'}
    $canonical=$Report.sourceHashes|ConvertTo-Json -Depth 3 -Compress
    $fingerprint=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.UTF8Encoding]::new($false).GetBytes($canonical))).ToLowerInvariant()
    if($fingerprint-cne$Report.sourceFingerprint){throw 'Alpha source fingerprint does not match its source map'}
    foreach($entry in $Report.sourceHashes.PSObject.Properties){
        $path=Assert-Within (Join-Path $repo $entry.Name) $repo
        Assert-Regular $path|Out-Null
        if((Hash $path)-cne$entry.Value){throw "Current alpha source changed: $($entry.Name)"}
    }
    $guideSource=Assert-Within (Join-Path $repo $Report.agentGuide.source) $repo
    if((Hash $guideSource)-cne$Report.agentGuide.sourceSha256){throw 'Alpha guide source changed'}
    $expected=@([regex]::Matches((Get-Content (Join-Path $repo 'settings.gradle.kts') -Raw),'register\("([^"\r\n]+)"\)')|ForEach-Object {$_.Groups[1].Value}|Sort-Object)
    if(($expected-join',')-cne(@($Report.artifacts.target|Sort-Object)-join',')){throw 'Alpha Target set differs from current repository'}
    if(@($Report.nativeSmoke).Count-ne5-or@($Report.nativeSmoke|Where-Object {-not$_.sourceMatchesCurrent-or-not$_.cleanShutdown}).Count){throw 'Alpha Native smoke is not current/clean for all five Targets'}
    if($Report.packagedRuntimeVerified-ne$true-or@($Report.packagedRuntime).Count-ne5-or@($Report.packagedRuntime.target|Sort-Object -Unique).Count-ne5){throw 'Five current clean-install packaged Runtime proofs are required before publication'}
    foreach($proof in $Report.packagedRuntime){
        $artifact=@($Report.artifacts|Where-Object target -CEQ $proof.target)
        if($artifact.Count-ne1-or$proof.status-ne'PASS'-or$proof.runtime-ne'installed_loader_release_jar'-or$proof.artifactSha256-cne$artifact[0].sha256-or$proof.sourceFingerprint-cne$Report.sourceFingerprint){throw 'Packaged Runtime proof does not match this alpha batch'}
        $evidence=Assert-Within (Join-Path $repo $proof.result) (Join-Path $repo 'validations/integration-packaged_runtime/result')
        Assert-Regular $evidence|Out-Null
        if((Hash $evidence)-cne$proof.resultSha256){throw 'Packaged Runtime evidence changed'}
        $live=Get-Content -LiteralPath $evidence -Raw|ConvertFrom-Json
        if($live.status-ne'PASS'-or$live.target-cne$proof.target-or$live.runtime-ne'installed_loader_release_jar'-or$live.artifactSha256-cne$proof.artifactSha256-or$live.sourceFingerprint-cne$Report.sourceFingerprint-or$live.sourceChangedDuringRun-ne$false-or$live.developmentClasspath-ne$false-or$live.externalModCount-ne1-or$live.exitCode-ne0-or$live.persistentWriteCalls-ne0){throw 'Invalid clean-install Runtime evidence'}
        foreach($field in @('freshGameDirectory','endpointVerified','handshake','readPassed','titleReady','cleanShutdown','noCrashReports')){if($live.$field-ne$true){throw "Incomplete packaged Runtime proof: $field"}}
        if($Report.publicationProfile-cin@('dependency_update_trial_v1','e_ui_trial_v1','e_world_trial_v1')-and($live.enterWorldRequested-ne$true-or$live.enteredWorld-ne$true)){throw 'Dependency updates and E UI/world trials require new installed-world Runtime proof'}
    }
    foreach($artifact in $Report.artifacts){
        $path=Assert-Within (Join-Path $repo $artifact.path) (Join-Path $repo 'build/release')
        Assert-Regular $path|Out-Null
        if((Hash $path)-cne$artifact.sha256){throw "Alpha artifact changed: $($artifact.target)"}
    }
    $guide=Assert-Within (Join-Path $repo $Report.agentGuide.path) (Join-Path $repo 'build')
    if((Hash $guide)-cne$Report.agentGuide.sha256){throw 'Generated alpha guide changed'}
    $gateOutput=& node (Join-Path $PSScriptRoot 'alpha-evidence.mjs') --verify-continuous-publication $Manifest --repository-root $repo 2>&1
    if($LASTEXITCODE-ne0){throw ('Continuous-gameplay alpha evidence is incomplete, stale or has an unmet criterion: '+($gateOutput-join' '))}
}
function Write-DurableText([string]$Path,[string]$Text){
    $stream=[IO.File]::Open($Path,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::None)
    try{$bytes=[Text.UTF8Encoding]::new($false).GetBytes($Text);$stream.Write($bytes);$stream.Flush($true)}finally{$stream.Dispose()}
}
function Prune-Snapshots([string]$Current){
    $retained=[Collections.Generic.List[string]]::new()
    $owned=[Collections.Generic.List[object]]::new()
    $usageRoot=Join-Path $root '.usage'
    $usageKnown=$true
    $usageEntries=@{}
    try{
        if(Test-Path -LiteralPath $usageRoot){
            if(-not(Assert-Regular $usageRoot).PSIsContainer){throw 'Usage registry is not a directory'}
            foreach($entry in Get-ChildItem -LiteralPath $usageRoot -Force){
                if($entry.Name-cnotmatch$namePattern){throw 'Unknown entry in the usage registry'}
                $usageEntries[$entry.Name]=$entry.FullName
            }
        }
    }catch{$usageKnown=$false;$warnings.Add("Unknown usage registry; all snapshots retained: $($_.Exception.Message)")}
    foreach($candidate in Get-ChildItem -LiteralPath $root -Directory -Force|Where-Object Name -CMatch $namePattern){
        try{$metadata=Assert-Snapshot $candidate.FullName -AllowWithdrawn;$owned.Add([pscustomobject]@{Name=$candidate.Name;FullName=$candidate.FullName;PreparedAt=(Prepared-Time $metadata)})}
        catch{$retained.Add($candidate.FullName);$warnings.Add("Unknown snapshot retained: $($candidate.FullName) : $($_.Exception.Message)")}
    }
    $candidates=@($owned|Sort-Object PreparedAt,Name -Descending)
    for($index=0;$index-lt$candidates.Count;$index++){
        $candidate=$candidates[$index].FullName
        $inUse=-not$usageKnown
        if($usageKnown-and$usageEntries.ContainsKey($candidates[$index].Name)){
            try{
                $usage=$usageEntries[$candidates[$index].Name]
                if(-not(Assert-Regular $usage).PSIsContainer){throw 'Usage record container is not a directory'}
                $inUse=@(Get-ChildItem -LiteralPath $usage -Force).Count-gt0
            }catch{$inUse=$true;$warnings.Add("Unknown usage state retained: $candidate")}
        }
        if($index-lt3-or$inUse-or$candidate-ieq$Current){$retained.Add($candidate);continue}
        try{Assert-Snapshot $candidate -AllowWithdrawn|Out-Null;Recycle $candidate}catch{$warnings.Add("Unknown snapshot retained: $candidate : $($_.Exception.Message)")}
        if(Test-Path -LiteralPath $candidate){$retained.Add($candidate)}
    }
    return @($retained)
}
[IO.Directory]::CreateDirectory($root)|Out-Null
Assert-Regular $root|Out-Null
$lockPath=Join-Path $root '.alpha.lock'
if(Test-Path -LiteralPath $lockPath){Assert-Regular $lockPath|Out-Null}
$lock=[IO.File]::Open($lockPath,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
$stage=$null;$temporaryLatest=$null;$latestBackup=$null
try{
    if($Mode-eq'Withdraw'){
        if(-not$Reason){throw 'Withdrawal requires an explicit reason'}
        if(-not$Snapshot){$Snapshot=(Get-Content -LiteralPath (Join-Path $root 'latest.txt') -Raw).Trim()}
        $directory=if([IO.Path]::IsPathRooted($Snapshot)){$Snapshot}else{Join-Path $root $Snapshot}
        Assert-Snapshot $directory -AllowWithdrawn|Out-Null
        $directory=[IO.Path]::GetFullPath($directory)
        $withdrawn=Join-Path $root '.withdrawn'
        if(Test-Path -LiteralPath $withdrawn){Assert-Regular $withdrawn|Out-Null}else{[IO.Directory]::CreateDirectory($withdrawn)|Out-Null}
        $record=Join-Path $withdrawn ([IO.Path]::GetFileName($directory)+'.json')
        if(-not(Test-Path -LiteralPath $record)){Write-DurableText $record ((@{snapshot=$directory;reason=$Reason;withdrawnAt=[DateTimeOffset]::UtcNow.ToString('o')}|ConvertTo-Json)+"`n")}
        $latest=Join-Path $root 'latest.txt'
        if((Test-Path -LiteralPath $latest)-and(Get-Content -LiteralPath $latest -Raw).Trim()-ieq$directory){Recycle $latest;if(Test-Path -LiteralPath $latest){throw 'Could not withdraw latest pointer'}}
        @{mode=$Mode;directory=$directory;reason=$Reason;latestAvailable=(Test-Path -LiteralPath $latest);snapshotModified=$false;recycled=@($recycled)}|ConvertTo-Json -Depth 5
        return
    }
    if($Mode-ne'Publish'){
        if($Consumer-cnotmatch'^[a-z0-9][a-z0-9_-]{0,63}$'){throw 'A stable lowercase Consumer identity is required'}
        if(-not$Snapshot){$Snapshot=(Get-Content -LiteralPath (Join-Path $root 'latest.txt') -Raw).Trim()}
        $directory=if([IO.Path]::IsPathRooted($Snapshot)){$Snapshot}else{Join-Path $root $Snapshot}
        $report=Assert-Snapshot $directory -AllowWithdrawn:($Mode-eq'Unpin')
        $directory=[IO.Path]::GetFullPath($directory)
        $usageRoot=Join-Path $root '.usage'
        if(Test-Path -LiteralPath $usageRoot){Assert-Regular $usageRoot|Out-Null}else{[IO.Directory]::CreateDirectory($usageRoot)|Out-Null}
        $usage=Join-Path $usageRoot ([IO.Path]::GetFileName($directory))
        if(Test-Path -LiteralPath $usage){Assert-Regular $usage|Out-Null}else{[IO.Directory]::CreateDirectory($usage)|Out-Null}
        Assert-Regular $usage|Out-Null
        $pin=Join-Path $usage ($Consumer+'.json')
        if(Test-Path -LiteralPath $pin){
            Assert-Regular $pin|Out-Null
            $record=Get-Content -LiteralPath $pin -Raw|ConvertFrom-Json
            if($record.consumer-cne$Consumer-or$record.snapshot-ine$directory){throw 'Unknown usage record; retained'}
        }elseif($Mode-eq'Pin'){
            Write-DurableText $pin ((@{consumer=$Consumer;snapshot=$directory;sourceFingerprint=$report.sourceFingerprint;createdAt=[DateTimeOffset]::UtcNow.ToString('o');automaticExpiry=$false}|ConvertTo-Json)+"`n")
        }
        $retained=@()
        if($Mode-eq'Unpin'){
            Recycle $pin
            $current=$null
            if(Test-Path -LiteralPath (Join-Path $root 'latest.txt')){
                $current=(Get-Content -LiteralPath (Join-Path $root 'latest.txt') -Raw).Trim()
                Assert-Snapshot $current|Out-Null
            }
            $retained=@(Prune-Snapshots $current)
        }
        @{mode=$Mode;directory=$directory;consumer=$Consumer;inUse=(Test-Path -LiteralPath $pin);retained=$retained;warnings=@($warnings);recycled=@($recycled)}|ConvertTo-Json -Depth 5
        return
    }
    $manifest=Join-Path $repo 'build/alpha-manifest.json'
    Assert-Regular $manifest|Out-Null
    $manifestHash=Hash $manifest
    $report=Get-Content -LiteralPath $manifest -Raw|ConvertFrom-Json
    $name=Snapshot-Name $report
    $directory=Join-Path $root $name
    if($report.distribution.layout-ne'flat_immutable_snapshot'-or[IO.Path]::GetFullPath($report.distribution.directory)-ine$directory){throw 'Manifest distribution directory does not match requested publication'}
    $files=Bundle-Files $report
    Assert-Source $report $manifest
    $files['alpha-manifest.json']=$manifestHash
    if(Test-Path -LiteralPath $directory){
        Assert-Snapshot $directory|Out-Null
        foreach($file in $files.Keys){if((Hash (Join-Path $directory $file))-cne$files[$file]){throw "Immutable snapshot name collision; retry in a later minute: $directory"}}
    }else{
        $stage=Join-Path $root ('.publish-'+[Guid]::NewGuid().ToString('N'))
        [IO.Directory]::CreateDirectory($stage)|Out-Null
        foreach($artifact in $report.artifacts){Copy-Item -LiteralPath (Join-Path $repo $artifact.path) -Destination (Join-Path $stage $artifact.fileName)}
        Copy-Item -LiteralPath (Join-Path $repo $report.agentGuide.path) -Destination (Join-Path $stage 'AGENT_GUIDE.md')
        Copy-Item -LiteralPath $manifest -Destination (Join-Path $stage 'alpha-manifest.json')
        foreach($file in $files.Keys){if((Hash (Join-Path $stage $file))-cne$files[$file]){throw "Alpha copy changed: $file"}}
        Assert-Source $report $manifest
        if((Hash $manifest)-cne$manifestHash){throw 'Alpha manifest changed during copy'}
        [IO.Directory]::Move($stage,$directory)
        $stage=$null
    }
    Assert-Snapshot $directory|Out-Null
    $latest=Join-Path $root 'latest.txt'
    $temporaryLatest=Join-Path $root ('.latest-'+[Guid]::NewGuid().ToString('N')+'.tmp')
    Write-DurableText $temporaryLatest ($directory+"`n")
    if(Test-Path -LiteralPath $latest){
        Assert-Regular $latest|Out-Null
        $latestBackup=Join-Path $root ('.latest-backup-'+[Guid]::NewGuid().ToString('N')+'.txt')
        [IO.File]::Replace($temporaryLatest,$latest,$latestBackup)
        Recycle $latestBackup
        $latestBackup=$null
    }else{[IO.File]::Move($temporaryLatest,$latest)}
    $temporaryLatest=$null
    $retained=@(Prune-Snapshots $directory)
    @{mode='Publish';directory=$directory;latest=$latest;files=@($files.Keys);retained=@($retained);recycled=@($recycled);warnings=@($warnings);persistentWriteCalls=0}|ConvertTo-Json -Depth 6
}finally{
    if($stage){Recycle $stage}
    if($temporaryLatest){Recycle $temporaryLatest}
    $lock.Dispose()
    foreach($warning in $warnings){Write-Warning $warning}
}
