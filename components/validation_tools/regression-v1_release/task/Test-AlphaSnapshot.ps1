[CmdletBinding()]param()
$ErrorActionPreference='Stop'
$publisher=Join-Path $PSScriptRoot 'Publish-AlphaSnapshot.ps1'
$tempRoot=Join-Path ([IO.Path]::GetTempPath()) ('minecraft-alpha-snapshot-'+[Guid]::NewGuid().ToString('N'))
[IO.Directory]::CreateDirectory($tempRoot)|Out-Null
$repo=Join-Path $tempRoot 'repository';$output=Join-Path $tempRoot 'Alpha';$trash=Join-Path $tempRoot 'recycled'
$targets=@('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')
$checks=0
function Require([bool]$Condition,[string]$Message){if(-not$Condition){throw "Alpha snapshot test: $Message"};$script:checks++}
function Write-Text([string]$Path,[string]$Text){[IO.Directory]::CreateDirectory((Split-Path $Path))|Out-Null;[IO.File]::WriteAllText($Path,$Text,[Text.UTF8Encoding]::new($false))}
function Digest([string]$Text){return [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.UTF8Encoding]::new($false).GetBytes($Text))).ToLowerInvariant()}
$recycle={param($Path)
    $resolved=[IO.Path]::GetFullPath($Path)
    if(-not$resolved.StartsWith($tempRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Synthetic recycling escaped its fixture'}
    [IO.Directory]::CreateDirectory($trash)|Out-Null
    $destination=Join-Path $trash ([Guid]::NewGuid().ToString('N'))
    if(-not[IO.Path]::GetFullPath($destination).StartsWith($tempRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Synthetic recycle destination escaped its fixture'}
    Move-Item -LiteralPath $resolved -Destination $destination
}.GetNewClosure()
function Run-Publisher([string]$Mode='Publish',[string]$Snapshot,[string]$Consumer){
    $args=@{OutputRoot=$output;RepositoryRoot=$repo;RecycleAction=$recycle;Mode=$Mode}
    if($Snapshot){$args.Snapshot=$Snapshot};if($Consumer){$args.Consumer=$Consumer}
    return (& $publisher @args)|ConvertFrom-Json
}
function Rejected([scriptblock]$Action,[string]$Message){$failed=$false;try{& $Action|Out-Null}catch{$failed=$true};Require $failed $Message}
function Make-Batch([int]$Minute,[string]$Guide='Synthetic Agent guide',[switch]$ZuluTime){
    Write-Text (Join-Path $repo 'settings.gradle.kts') (($targets|ForEach-Object {'register("'+$_+'")'})-join"`n")
    $source="source revision $Minute";Write-Text (Join-Path $repo 'source.txt') $source
    Write-Text (Join-Path $repo 'documents/development/workflow-alpha_agent.md') $Guide
    Write-Text (Join-Path $repo 'build/AGENT_GUIDE.md') $Guide
    $hashes=[ordered]@{'source.txt'=(Digest $source)}
    $fingerprint=Digest ($hashes|ConvertTo-Json -Compress)
    $time=[DateTimeOffset]::Parse('2026-10-16T10:00:00Z').AddMinutes($Minute)
    $name=$time.ToOffset([TimeSpan]::FromHours(8)).ToString('yyyyMMdd-HHmm')+'-'+$fingerprint.Substring(0,8)
    $artifacts=@(foreach($target in $targets){$file="product-$target.jar";$text="synthetic Jar $target revision $Minute";$path="build/release/test/$file";Write-Text (Join-Path $repo $path) $text;@{target=$target;path=$path;fileName=$file;sha256=(Digest $text)}})
    $prepared=if($ZuluTime){$time.UtcDateTime.ToString('o')}else{$time.ToString('o')}
    $report=@{type='internal_alpha_build';preparedAt=$prepared;sourceFingerprint=$fingerprint;sourceHashes=$hashes;artifacts=$artifacts;agentGuide=@{path='build/AGENT_GUIDE.md';sha256=(Digest $Guide);source='documents/development/workflow-alpha_agent.md';sourceSha256=(Digest $Guide)};nativeSmoke=@($targets|ForEach-Object {@{target=$_;sourceMatchesCurrent=$true;cleanShutdown=$true}});distribution=@{layout='flat_immutable_snapshot';directory=(Join-Path $output $name)}}
    $report.packagedRuntimeVerified=$true
    $report.packagedRuntime=@(foreach($artifact in $artifacts){
        $proof=@{target=$artifact.target;status='PASS';runtime='installed_loader_release_jar';artifactSha256=$artifact.sha256;sourceFingerprint=$fingerprint;sourceChangedDuringRun=$false;developmentClasspath=$false;externalModCount=1;exitCode=0;persistentWriteCalls=0;freshGameDirectory=$true;endpointVerified=$true;handshake=$true;readPassed=$true;titleReady=$true;cleanShutdown=$true;noCrashReports=$true}
        $path='validations/integration-packaged_runtime/result/'+$artifact.target+'.json';$bytes=($proof|ConvertTo-Json)+"`n"
        Write-Text (Join-Path $repo $path) $bytes
        @{target=$artifact.target;status='PASS';runtime='installed_loader_release_jar';artifactSha256=$artifact.sha256;sourceFingerprint=$fingerprint;result=$path;resultSha256=(Digest $bytes)}
    })
    Write-Text (Join-Path $repo 'build/alpha-manifest.json') (($report|ConvertTo-Json -Depth 9)+"`n")
    $fixtureOutput=& node (Join-Path $PSScriptRoot 'alpha-consumer.test.mjs') --write-publication-fixture $repo 2>&1
    if($LASTEXITCODE-ne0){throw ('Synthetic publication fixture failed: '+($fixtureOutput-join' '))}
    return Get-Content (Join-Path $repo 'build/alpha-manifest.json') -Raw|ConvertFrom-Json
}
try{
    $batch=Make-Batch 0 -ZuluTime
    $first=Run-Publisher
    Require ($batch.publicationProfile-ceq'continuous_gameplay_trial_v1'-and$batch.delivery-ceq'trial_with_known_performance_limit'-and$batch.fullMilestonesComplete-eq$false-and$batch.performanceAcceptance.status-ceq'NOT_MET'-and$batch.performanceAcceptance.goalPerformancePass-eq$false) 'a permitted trial preserves NOT_MET and never claims Goal completion'
    Require ([IO.Path]::GetFileName($first.directory)-match'^20261016-1800-') 'UTC Z timestamps publish at the same Shanghai time as explicit offsets'
    Require (@($first.files).Count-eq7) 'seven flat distribution files'
    Require ((Get-Content (Join-Path $output 'latest.txt') -Raw).Trim()-eq$first.directory) 'latest points only to a complete snapshot'
    foreach($artifact in $batch.artifacts){Require ((Get-FileHash (Join-Path $first.directory $artifact.fileName)).Hash.ToLowerInvariant()-eq$artifact.sha256) 'copied Jar hash'}
    $sealed=Get-FileHash (Join-Path $first.directory 'alpha-manifest.json')
    $modified=(Get-Item (Join-Path $first.directory 'alpha-manifest.json')).LastWriteTimeUtc
    Run-Publisher|Out-Null
    Require ((Get-Item (Join-Path $first.directory 'alpha-manifest.json')).LastWriteTimeUtc-eq$modified) 'idempotent publish never rewrites the snapshot'
    Write-Text (Join-Path $repo $batch.artifacts[0].path) 'new build overwrote the source'
    Rejected {Run-Publisher} 'overwritten build Jar must be refused'
    Require ((Get-FileHash $sealed.Path).Hash-eq$sealed.Hash) 'overwriting build cannot change a published snapshot'
    Make-Batch 0 'different guide same minute'|Out-Null
    Rejected {Run-Publisher} 'different bytes at the same snapshot identity cannot overwrite'
    Make-Batch 1|Out-Null
    Write-Text (Join-Path $repo 'build/AGENT_GUIDE.md') 'mismatched guide'
    Rejected {Run-Publisher} 'guide hash mismatch'
    Make-Batch 1|Out-Null
    Write-Text (Join-Path $repo 'source.txt') 'stale source'
    Rejected {Run-Publisher} 'stale source map'
    $missing=Make-Batch 1;$missing.packagedRuntimeVerified=$false
    Write-Text (Join-Path $repo 'build/alpha-manifest.json') ($missing|ConvertTo-Json -Depth 9)
    Rejected {Run-Publisher} 'missing five-Target installed Runtime gate'
    $stale=Make-Batch 1
    Write-Text (Join-Path $repo $stale.packagedRuntime[0].result) '{}'
    Rejected {Run-Publisher} 'modified installed Runtime proof'
    $continuous=Make-Batch 1
    $candidateJson=$continuous|ConvertTo-Json -Depth 100 -Compress
    $latestBefore=(Get-Content (Join-Path $output 'latest.txt') -Raw).Trim()
    foreach($change in @(
        {param($r)$r.publicationProfile='historical_scoped'},
        {param($r)$r.delivery='release'},
        {param($r)$r.fullMilestonesComplete=$true},
        {param($r)$r.performanceAcceptance.goalPerformancePass=$true},
        {param($r)$r.continuousGameplay.proofs=@($r.continuousGameplay.proofs|Select-Object -Skip 1)},
        {param($r)$r.continuousGameplay.proofs[1]=$r.continuousGameplay.proofs[0]},
        {param($r)$r.nativeSmoke=@()},
        {param($r)$r.uiRegression=@()},
        {param($r)$r.continuousInput=@()},
        {param($r)$r.controlCollaboration=@($r.controlCollaboration|Select-Object -Skip 1)}
    )){
        $candidate=$candidateJson|ConvertFrom-Json
        & $change $candidate
        Write-Text (Join-Path $repo 'build/alpha-manifest.json') ($candidate|ConvertTo-Json -Depth 100)
        Rejected {Run-Publisher} 'direct publication cannot bypass the new profile, a Target or prior core/UI/collaboration/input proof'
        Require ((Get-Content (Join-Path $output 'latest.txt') -Raw).Trim()-eq$latestBefore) 'rejected profile does not advance latest'
    }
    function Reject-ContinuousResult([string]$Scenario,[scriptblock]$Change,[string]$Message){
        $candidate=$candidateJson|ConvertFrom-Json
        $proof=@($candidate.continuousGameplay.proofs|Where-Object {$_.target-ceq$targets[0]-and$_.scenario-ceq$Scenario})[0]
        $path=Join-Path $repo $proof.result;$original=Get-Content $path -Raw
        try{
            $result=$original|ConvertFrom-Json;& $Change $result
            $bytes=($result|ConvertTo-Json -Depth 100 -Compress)+"`n";Write-Text $path $bytes;$proof.resultSha256=Digest $bytes
            Write-Text (Join-Path $repo 'build/alpha-manifest.json') ($candidate|ConvertTo-Json -Depth 100)
            Rejected {Run-Publisher} $Message
            Require ((Get-Content (Join-Path $output 'latest.txt') -Raw).Trim()-eq$latestBefore) 'rejected actual evidence does not advance latest'
        }finally{Write-Text $path $original;Write-Text (Join-Path $repo 'build/alpha-manifest.json') $candidateJson}
    }
    Reject-ContinuousResult 'continuous-performance' {param($r)($r.actionSamples|Where-Object case -CEQ 'continuous_performance_complete').performanceComplete=$true} 'trial cannot rewrite NOT_MET into completed performance'
    Reject-ContinuousResult 'continuous-performance' {param($r)$s=@($r.actionSamples|Where-Object case -CEQ 'continuous_performance_attack_single')[0];$s.inputAfter.nativeEntityAttackCount=$s.inputBefore.nativeEntityAttackCount} 'reported actual consumption cannot replace the native counter delta'
    Reject-ContinuousResult 'interaction-baseline' {param($r)$r.actionSamples=@($r.actionSamples|Where-Object {$_.case-cne'interaction_baseline_sample'-or$_.index-eq0})} 'old GUI1 cannot be relabelled GUI20'
    Reject-ContinuousResult 'region-stream' {param($r)($r.actionSamples|Where-Object case -CEQ 'mcp_world_baseline').transport='injected_unit_test'} 'injected services cannot become real MCP evidence'
    Reject-ContinuousResult 'region-stream' {param($r)$s=$r.actionSamples|Where-Object case -CEQ 'mcp_world_baseline';$s.samples=@($s.samples|Select-Object -Skip 1)} 'MCP twenty calls cannot be inferred from summary statistics'
    Reject-ContinuousResult 'input-context' {param($r)$r.cleanShutdown=$false} 'context transition evidence requires clean shutdown'
    Reject-ContinuousResult 'world-placement-receipts' {param($r)$r.persistentWriteCalls=1} 'placement receipts cannot hide write contamination'
    $tool=Join-Path $repo 'components/validation_tools/conformance-world_read/task/mcp-world-baseline.mjs';$originalTool=Get-Content $tool -Raw
    try{Write-Text $tool 'modified bound MCP consumer';Rejected {Run-Publisher} 'direct publisher rejects changed proof tools even when they are not candidate product files'}finally{Write-Text $tool $originalTool}
    Write-Text (Join-Path $repo 'build/alpha-manifest.json') $candidateJson
    $dependency=$candidateJson|ConvertFrom-Json
    $dependency.publicationProfile='dependency_update_trial_v1'
    $dependency.nativeSmokeScope='packaged-runtime'
    $dependency.fullNativeSmoke=$false
    $dependency|Add-Member -NotePropertyName runtimeAcceptance -NotePropertyValue 'dependency_update_installed_world_smoke' -Force
    Write-Text (Join-Path $repo 'build/alpha-manifest.json') ($dependency|ConvertTo-Json -Depth 100)
    $missingWorldRejected=$false
    try{Run-Publisher|Out-Null}catch{$missingWorldRejected=$_.Exception.Message.Contains('require new installed-world Runtime proof')}
    Require $missingWorldRejected 'a dependency profile cannot publish the title-only installed proof'
    Require ((Get-Content (Join-Path $output 'latest.txt') -Raw).Trim()-eq$latestBefore) 'missing dependency world proof must not change latest'
    Write-Text (Join-Path $repo 'build/alpha-manifest.json') $candidateJson
    $pin=Run-Publisher 'Pin' $first.directory 'voxy'
    Require $pin.inUse 'explicit use protects the old snapshot even after source changes'
    Run-Publisher 'Pin' $first.directory 'voxy'|Out-Null
    foreach($minute in 1..4){Make-Batch $minute|Out-Null;Run-Publisher|Out-Null}
    $directories=@(Get-ChildItem -LiteralPath $output -Directory|Where-Object Name -Match '^\d{8}-')
    Require ($directories.Count-eq4) 'newest three plus the pinned old snapshot'
    Require (Test-Path $first.directory) 'pinned oldest snapshot is retained'
    Require (@(Get-ChildItem $trash -Directory).Count-gt0) 'retired snapshots moved to the synthetic Recycle Bin'
    Require ((Get-FileHash $sealed.Path).Hash-eq$sealed.Hash) 'retention never edits a pinned snapshot'
    $released=Run-Publisher 'Unpin' $first.directory 'voxy'
    Require (-not$released.inUse) 'explicit release removes the use pin recoverably'
    Require (@($released.retained).Count-eq3) 'releasing the last pin immediately prunes older snapshots'
    $final=Run-Publisher
    Require (@($final.retained).Count-eq3) 'publication retains only the newest three unpinned snapshots'
    Require (-not(Test-Path $first.directory)) 'released old snapshot is recycled'
    $usageRoot=Join-Path $output '.usage'
    Write-Text (Join-Path $usageRoot 'unknown-record.txt') 'unrecognized usage state'
    Make-Batch 5|Out-Null
    $unknown=Run-Publisher
    Require (@($unknown.retained).Count-eq4-and$unknown.warnings.Count-gt0) 'unknown global usage state retains all old snapshots'
    & $recycle (Join-Path $usageRoot 'unknown-record.txt')
    $failRecycle={param($Path)throw 'injected Recycle Bin failure'}
    $failedRecycle=(& $publisher -OutputRoot $output -RepositoryRoot $repo -RecycleAction $failRecycle)|ConvertFrom-Json
    Require (@($failedRecycle.retained).Count-eq4-and$failedRecycle.warnings.Count-gt0) 'cleanup failure retains the old snapshot and reports its path'
    $final=Run-Publisher
    Require (@($final.retained).Count-eq3) 'successful retry prunes only known unpinned snapshots'
    & $recycle $usageRoot
    Write-Text $usageRoot 'damaged registry is a file'
    Make-Batch 6|Out-Null
    $damaged=Run-Publisher
    Require (@($damaged.retained).Count-eq4-and$damaged.warnings.Count-gt0) 'damaged global usage registry cannot permit pruning'
    & $recycle $usageRoot
    $final=Run-Publisher
    Require (@($final.retained).Count-eq3) 'repaired registry permits pruning without rewriting survivors'
    $lock=[IO.File]::Open((Join-Path $output '.alpha.lock'),[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
    try{Rejected {Run-Publisher} 'concurrent publication/use is excluded by an OS lock'}finally{$lock.Dispose()}
    Rejected {Run-Publisher 'Pin' '..' 'voxy'} 'pin path traversal'
    Rejected {Run-Publisher 'Pin' $final.directory '../bad'} 'consumer path traversal'
    Run-Publisher 'Pin' $final.directory 'voxy'|Out-Null
    $withdrawn=& $publisher -Mode Withdraw -Snapshot $final.directory -Reason SYNTHETIC_DEPENDENCY_FAILURE -OutputRoot $output -RepositoryRoot $repo -RecycleAction $recycle|ConvertFrom-Json
    Require (-not$withdrawn.latestAvailable-and(Test-Path $final.directory)) 'withdrawal removes latest without changing the published snapshot'
    Rejected {Run-Publisher 'Pin' $final.directory 'voxy'} 'withdrawn snapshots cannot be newly acquired'
    Rejected {Run-Publisher} 'withdrawn snapshot cannot be republished'
    $releaseWithdrawn=Run-Publisher 'Unpin' $final.directory 'voxy'
    Require (-not$releaseWithdrawn.inUse) 'a consumer can release a withdrawn snapshot without a latest pointer'
    Write-Output "PASS alpha fixed snapshots: $checks assertions; copy hashes, stale inputs, collision, lock, usage and retention; synthetic data only"
}finally{
    $resolved=[IO.Path]::GetFullPath($tempRoot)
    $parent=[IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd([IO.Path]::DirectorySeparatorChar)+[IO.Path]::DirectorySeparatorChar
    if(-not$resolved.StartsWith($parent,[StringComparison]::OrdinalIgnoreCase)-or[IO.Path]::GetFileName($resolved)-cnotmatch'^minecraft-alpha-snapshot-[0-9a-f]{32}$'){throw 'Refusing cleanup outside owned synthetic fixture'}
    if($IsWindows){try{Add-Type -AssemblyName Microsoft.VisualBasic;[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($resolved,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)}catch{Write-Warning "Synthetic fixture retained; Recycle Bin unavailable: $resolved"}}
    else{Write-Warning "Synthetic fixture retained without permanent deletion: $resolved"}
}
