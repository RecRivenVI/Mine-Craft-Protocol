[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$EvidencePath,
    [Parameter(Mandatory)][string]$LocalGateEvidencePath
)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
function Require([bool]$ok,[string]$reason){if(-not$ok){throw "Control Round 1 final gate: $reason"}}
$static=& (Join-Path $PSScriptRoot 'Invoke-ControlRound1StaticGate.ps1')
$core=& (Join-Path $PSScriptRoot '../core/Invoke-CoreDemoCorrectnessStaticGate.ps1')
Require ($static.Result-eq'PASS'-and$core.Result-eq'PASS') 'static/Hook contract regression'
$local=Get-Content -LiteralPath $LocalGateEvidencePath -Raw|ConvertFrom-Json
Require ($local.Result-eq'PASS'-and$local.JavaTestFailures-eq0-and$local.HardeningStatic-eq'PASS') 'Phase 8 Local Gate must pass'
Require ($local.DependencyAuditStatus-eq'PASS_NO_THRESHOLD_VULNERABILITIES'-and$local.DependencyAuditResponseValid-and$local.DependencyAuditServiceAvailable-and$local.DependencyVulnerabilitiesHigh-eq0-and$local.DependencyVulnerabilitiesCritical-eq0) 'trusted dependency audit required'
$evidence=Get-Content -LiteralPath $EvidencePath -Raw|ConvertFrom-Json
$expected=@('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.2-neoforge','26.2-fabric')
Require ($evidence.result-eq'PASS'-and$evidence.matrix.Count-eq5) 'complete five-Target live evidence required'
Require ($evidence.persistentWriteInvocations-eq0-and-not$evidence.computerUseUsed) 'scope / native acceptance boundary'
Require ($evidence.blockingIssues.Count-eq0) 'unresolved blockers'
foreach($target in $expected){
    $rows=@($evidence.matrix|Where-Object target -eq $target)
    Require ($rows.Count-eq1) "$target missing or duplicated"
    $row=$rows[0]
    foreach($field in @('read','operate','takeover','leaseLoss','manualRevoke','reacquire','concurrency','shutdown','firstUseContactSheetShutdown')){
        Require ($row.$field-eq'PASS') "$target / $field not passed"
    }
    Require ($row.humanProof.result-eq'PASS'-and$row.humanProof.operationState-eq'cancelled'-and$row.humanProof.heldKeys-eq0-and$row.humanProof.heldButtons-eq0-and$row.humanProof.postRevokeInput-eq0) "$target physical Esc / cleanup evidence incomplete"
    Require ($row.shutdownProof.ProcessExitCode-eq0-and$row.shutdownProof.RecordingStatus-eq'completed'-and$row.shutdownProof.WriterErrors-eq0) "$target finalization / process shutdown failed"
    $jar=Join-Path $root "versions/$target/build/libs/minecraft_protocol_probe-0.0.1-phase8.jar"
    Require ((Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()-eq$row.artifactSha256) "$target artifact changed after evidence capture"
}
foreach($file in $evidence.sourceHashes.PSObject.Properties){
    Require ((Get-FileHash -LiteralPath (Join-Path $root $file.Name) -Algorithm SHA256).Hash.ToLowerInvariant()-eq$file.Value) "source changed: $($file.Name)"
}
Push-Location $root
try{git diff --check;if($LASTEXITCODE-ne0){throw 'git diff --check failed'}}finally{Pop-Location}
[pscustomobject]@{Result='PASS';Targets=5;HttpOperations=$static.ClassifiedHttpOperations;McpTools=$static.ClassifiedMcpTools;Phase8Local='PASS';HumanEsc='PASS';ColdFinalization='PASS';PersistentWriteInvocations=0;Round2='NOT_STARTED';WireProtocolV1='NOT_FROZEN';Evidence=$EvidencePath}
