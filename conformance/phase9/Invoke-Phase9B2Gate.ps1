[CmdletBinding()]param(
    [string]$BaseUri,
    [string]$TokenFile,
    [string]$ExpectedTarget,
    [switch]$SkipBuild,
    [switch]$SkipLive,
    [switch]$Offline)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$static=& (Join-Path $PSScriptRoot 'Invoke-Phase9B2StaticGate.ps1')
if($static.Result-ne'PASS') { throw '9B.2 static failed' }
$java='SKIPPED'
if(-not $SkipBuild) {
    Push-Location $root
    try {
        $arguments=@(':versions:26.2-neoforge:test','--no-daemon')
        if($Offline) { $arguments+='--offline' }
        & '.\gradlew.bat' @arguments
        if($LASTEXITCODE-ne0) { throw '9B.2 Java tests failed' }
        $java='PASS'
    } finally {
        Pop-Location
    }
}
$details=$null
if(-not $SkipLive) {
    if(-not $BaseUri -or -not $TokenFile -or -not $ExpectedTarget) {
        throw 'live parameters required'
    }
    $parameters=@{
        BaseUri=$BaseUri
        TokenFile=$TokenFile
        ExpectedTarget=$ExpectedTarget
    }
    $details=[ordered]@{
        Canonical=& (Join-Path $PSScriptRoot 'Invoke-Phase9B2CanonicalizationConformance.ps1')
        Provider=& (Join-Path $PSScriptRoot 'Invoke-Phase9B2ProviderRevisionConformance.ps1') @parameters
        Resource=& (Join-Path $PSScriptRoot 'Invoke-Phase9B2ResourceVersionConformance.ps1') @parameters
        Executors=& (Join-Path $PSScriptRoot 'Invoke-Phase9B2ExecutorBoundConformance.ps1') @parameters
    }
    foreach($entry in $details.GetEnumerator()) {
        if($entry.Value.Result-ne'PASS') { throw "$($entry.Key) failed" }
    }
}
[pscustomobject]@{
    Result='PASS'
    Phase='9B.2'
    Static='PASS'
    Java=$java
    Live=$(if($SkipLive){'SKIPPED'}else{'PASS'})
    Details=$details
    Phase9C='DOWNSTREAM_PHASE_COMPLETE'
    WireProtocolV1='NOT_FROZEN'
}
