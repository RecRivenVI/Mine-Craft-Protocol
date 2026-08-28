[CmdletBinding()]param([string]$BaseUri,[string]$TokenFile,[string]$ExpectedTarget,[switch]$HasPhase9ADiagnostics,[switch]$SkipBuild,[switch]$SkipLive,[switch]$Offline)
$ErrorActionPreference='Stop';$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$static=& (Join-Path $PSScriptRoot 'Invoke-Phase9BStaticGate.ps1');if($static.Result-ne'PASS'){throw'static gate failed'}
$hardeningStatic=& (Join-Path $PSScriptRoot 'Invoke-Phase9B1StaticGate.ps1');if($hardeningStatic.Result-ne'PASS'){throw'9B.1 static gate failed'}
Push-Location $root;try{if(-not$SkipBuild){$a=@(':versions:1.20.1-forge:build',':versions:1.21.1-neoforge:build',':versions:26.1.2-neoforge:build',':versions:26.2-neoforge:build',':versions:26.2-fabric:build','--no-daemon');if($Offline){$a+='--offline'};&'.\gradlew.bat' @a;if($LASTEXITCODE-ne0){throw'five-target build failed'}}}finally{Pop-Location}
$details=$null
if(-not $SkipLive){
    if(-not $BaseUri -or -not $TokenFile -or -not $ExpectedTarget){throw 'live parameters required'}
    $p=@{BaseUri=$BaseUri;TokenFile=$TokenFile;ExpectedTarget=$ExpectedTarget}
    $contractHardening=& (Join-Path $PSScriptRoot 'Invoke-Phase9B1Gate.ps1') @p -SkipBuild -HasPhase9ADiagnostics:$HasPhase9ADiagnostics
    Start-Sleep -Milliseconds 750
    $details=[ordered]@{
        ContractHardening=$contractHardening
        Deep=& (Join-Path $PSScriptRoot 'Invoke-Phase9BDeepObservationConformance.ps1') @p -HasPhase9ADiagnostics:$HasPhase9ADiagnostics
        ClientServer=& (Join-Path $PSScriptRoot 'Invoke-Phase9BClientServerConformance.ps1') @p
        Provider=& (Join-Path $PSScriptRoot 'Invoke-Phase9BProviderConformance.ps1') @p
        Tickets=& (Join-Path $PSScriptRoot 'Invoke-Phase9BTicketConformance.ps1') @p
        Scheduled=& (Join-Path $PSScriptRoot 'Invoke-Phase9BScheduledTickConformance.ps1') @p
        Budget=& (Join-Path $PSScriptRoot 'Invoke-Phase9BBudgetConformance.ps1') @p
    }
    foreach($e in $details.GetEnumerator()){
        if($e.Value.Result -ne 'PASS'){throw "$($e.Key) failed"}
    }
}
[pscustomobject]@{Result='PASS';Phase='9B';ContractHardening='9B.1';Static='PASS';Build=$(if($SkipBuild){'SKIPPED'}else{'PASS'});Live=$(if($SkipLive){'SKIPPED'}else{'PASS'});Details=$details;Phase9C='NOT_STARTED';WireProtocolV1='NOT_FROZEN'}
