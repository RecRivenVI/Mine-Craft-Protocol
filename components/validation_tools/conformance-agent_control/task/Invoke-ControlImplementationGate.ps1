[CmdletBinding()]param(
 [Parameter(Mandatory)][string]$LocalGateEvidence,
 [Parameter(Mandatory)][string[]]$SmokeEvidence
)
$ErrorActionPreference='Stop'
function Check([bool]$ok,[string]$reason){if(-not$ok){throw "Implementation readiness: $reason"}}
$static=& (Join-Path $PSScriptRoot 'Invoke-ControlImplementationStaticGate.ps1')
Check ($static.Result-eq'PASS') 'static contract'
$local=Get-Content -LiteralPath $LocalGateEvidence -Raw|ConvertFrom-Json
Check ($local.Result-eq'PASS'-and$local.JavaTestFailures-eq0-and$local.JavaTests-ge103) 'build/unit/local regression'
Check ($local.DependencyAuditStatus-eq'PASS_NO_THRESHOLD_VULNERABILITIES'-and$local.DependencyVulnerabilitiesHigh-eq0-and$local.DependencyVulnerabilitiesCritical-eq0) 'dependency audit'
$targets=@()
foreach($file in $SmokeEvidence){
 $result=Get-Content -LiteralPath $file -Raw|ConvertFrom-Json
 Check ($result.Result-eq'PASS'-and$result.Validation-eq'AUTOMATED_DEVELOPMENT_SMOKE_NOT_HUMAN_ACCEPTANCE') 'runtime development smoke'
 Check ($result.HumanAcceptance-eq'PENDING'-and$result.PersistentWriteInvocations-eq0) 'acceptance/scope boundary'
 Check ($result.PointerSteps-ge12-and$result.Hover-eq'VANILLA_WIDGET_VERIFIED'-and$result.PostCancelMouseEvents-eq0-and$result.RelativeCamera-eq'VANILLA'-and$result.Shutdown-eq'PASS') 'pointer/camera/cancellation/shutdown'
 $targets+=$result.Target
}
Check (@($targets|Sort-Object -Unique).Count-eq5) 'five concrete development runtimes required for this evidence set'
git diff --check
if($LASTEXITCODE-ne0){throw 'git diff --check failed'}
[pscustomobject]@{Implementation='COMPLETE';UnifiedAcceptance='READY';HumanVisualAcceptance='NOT_RUN';AutomatedTargetCount=5;JavaTests=$local.JavaTests;Companion='PASS';NativeContract='0.0.1-control-r24';PersistentWriteInvocations=0;NextAction='STOP_FOR_UNIFIED_ACCEPTANCE'}
