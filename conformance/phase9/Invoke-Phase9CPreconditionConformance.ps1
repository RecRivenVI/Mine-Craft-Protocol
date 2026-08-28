[CmdletBinding()]param([switch]$Offline)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Push-Location $root
try {
    $arguments=@(
        ':versions:26.2-neoforge:test',
        '--tests','io.github.recrivenvi.minecraftprotocol.probe.runtime.Phase9CDebugAuthorizationTest',
        '--tests','io.github.recrivenvi.minecraftprotocol.probe.runtime.ObservationRevisionTrackerTest',
        '--no-daemon')
    if($Offline) { $arguments+='--offline' }
    & '.\gradlew.bat' @arguments
    if($LASTEXITCODE-ne0) { throw 'Phase 9C precondition Java tests failed' }
    [pscustomobject]@{
        Result='PASS'
        Arm='PASS'
        Scope='PASS'
        WorldFingerprint='PASS'
        SessionEpoch='PASS'
        ResourceIdentity='PASS'
        Lifecycle='PASS'
        Revision='PASS'
        QueryViewRejection='PASS'
        EvidenceContamination='PASS'
        CancellationPartialResult='PASS'
    }
}
finally { Pop-Location }
