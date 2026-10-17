[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$validation=Join-Path $repo 'validations/migration-runtime_core'
$compliance=Get-Content (Join-Path $repo 'build/reports/compliance/report.json') -Raw|ConvertFrom-Json
if(-not$compliance.strict-or-not$compliance.passed-or$compliance.failures-or$compliance.warnings){throw 'Current strict compliance report is not PASS'}
$packaging=& (Join-Path $PSScriptRoot '../../integration-packaged_runtime/task/Invoke-Phase9D21PackagingGate.ps1') -SkipBuild
if($packaging.Result-ne'PASS'){throw 'Current product artifacts failed verification'}
$targets=@('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')
$matrix=foreach($target in $targets){
    $result=Get-Content (Join-Path $validation "result/$target.json") -Raw|ConvertFrom-Json
    if($result.status-ne'PASS'-or-not$result.cleanShutdown-or$result.sourceChangedDuringRun-or$result.persistentWriteCalls-ne0-or$result.checks.Count-lt10){throw "Incomplete current Native smoke: $target"}
    foreach($source in $result.sourceHashes.PSObject.Properties){
        if((Get-FileHash (Join-Path $repo $source.Name) -Algorithm SHA256).Hash.ToLowerInvariant()-ne$source.Value){throw "Native smoke source became stale: $($source.Name)"}
    }
    $loaded=Get-Content (Join-Path $validation "instance/$target/runtime-core-class-loader.log") -Raw
    if($loaded-notmatch'io.github.recrivenvi.minecraftprotocol.core.ProbeTransport/'){throw "Actual shared transport loading was not observed: $target"}
    $client=Join-Path $validation "instance/$target/client"
    $launch=Get-Content (Join-Path $validation "instance/$target/launch-out.log") -Raw
    if($launch-notmatch'BUILD SUCCESSFUL'-or$launch-match'NoClassDefFoundError|LinkageError'){throw "Runtime launcher/class linking failed: $target"}
    if(@(Get-ChildItem (Join-Path $client 'crash-reports') -File -ErrorAction SilentlyContinue).Count){throw "Crash report exists: $target"}
    [ordered]@{target=$target;checks=$result.checks.Count;nativeRuntime='PASS';sharedTransportLoaded=$true;currentSourceVerified=$true;cleanShutdown=$true;artifactSha256=$packaging.ArtifactSha256[$target]}
}
$server=Get-Content (Join-Path $validation 'instance/26.3-neoforge/server/logs/latest.log') -Raw
if($server-notmatch'Done \('-or$server-notmatch'Runtime loaded'-or$server-notmatch'Stopping server'-or$server-notmatch'Closing FML Loader'-or$server-match'NoClassDefFoundError|LinkageError'){throw 'Reference dedicated server startup/shutdown not confirmed'}
foreach($target in $targets){if(Test-Path (Join-Path $validation "instance/$target/client/minecraft-protocol/endpoint.json")){throw "Owned discovery record remains: $target"}}
$games=@(Get-CimInstance Win32_Process -Filter "Name='java.exe'"|Where-Object {$_.CommandLine-like('*'+$repo+'*')-and$_.CommandLine-match'devlaunch.Main|devlaunchinjector.Main'})
if($games.Count){throw 'Repository game process still running'}
$suites=foreach($suite in @('components/runtime_core','components/runtime_safety')+@($targets|ForEach-Object {"versions/$_"})){
    $xmls=@(Get-ChildItem (Join-Path $repo "$suite/build/test-results/test") -Filter 'TEST-*.xml'|ForEach-Object {[xml]$xml=Get-Content $_.FullName -Raw;$xml.testsuite})
    $failures=($xmls|Measure-Object failures -Sum).Sum+($xmls|Measure-Object errors -Sum).Sum
    if($failures){throw "Java test failure: $suite"}
    [ordered]@{suite=$suite;tests=($xmls|Measure-Object tests -Sum).Sum;failures=$failures}
}
$report=[ordered]@{status='PASS';testedAt=[DateTime]::UtcNow.ToString('o');testedCommit=(& git -C $repo rev-parse HEAD).Trim();dirty=$true;specification=$compliance.specification;strictCompliance='PASS';warnings=0;javaTests=$suites;targets=$matrix;referenceDedicatedServer='PASS';runtimeEvidence='development_runtime_native_not_final_jar_install_attestation';humanAcceptance='NOT_RUN';persistentWriteCalls=0}
[IO.File]::WriteAllText((Join-Path $validation 'result/summary.json'),($report|ConvertTo-Json -Depth 12).Replace("`r`n","`n")+"`n",[Text.UTF8Encoding]::new($false))
Write-Output 'PASS: five current Native runtimes, actual shared class loading, unique release classes, reference server shutdown, strict compliance and Java tests'
