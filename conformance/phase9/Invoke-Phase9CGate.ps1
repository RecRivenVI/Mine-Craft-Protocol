[CmdletBinding()]param(
    [string]$BaseUri,[string]$TokenFile,[string]$ExpectedTarget,
    [switch]$SkipBuild,[switch]$SkipLive,[switch]$SkipBatch,[switch]$Offline)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$static=& (Join-Path $PSScriptRoot 'Invoke-Phase9CStaticGate.ps1');if($static.Result-ne'PASS'){throw'Phase 9C static failed'}
$preconditions=& (Join-Path $PSScriptRoot 'Invoke-Phase9CPreconditionConformance.ps1') -Offline:$Offline;if($preconditions.Result-ne'PASS'){throw'Phase 9C preconditions failed'}
$phase9bStatic=& (Join-Path $PSScriptRoot 'Invoke-Phase9BGate.ps1') -SkipBuild -SkipLive -Offline:$Offline;if($phase9bStatic.Result-ne'PASS'){throw'Phase 9B static regression failed'}
$build='SKIPPED'
if(-not$SkipBuild){Push-Location $root;try{$args=@(':versions:1.20.1-forge:build',':versions:1.21.1-neoforge:build',':versions:26.1.2-neoforge:build',':versions:26.2-neoforge:build',':versions:26.2-fabric:build','--no-daemon');if($Offline){$args+='--offline'};& '.\gradlew.bat' @args;if($LASTEXITCODE-ne0){throw'Phase 9C five-target build failed'};$build='PASS'}finally{Pop-Location}}
$live=$null;$phase9bLive='SKIPPED';$v1='SKIPPED'
if(-not$SkipLive){if(-not$BaseUri-or-not$TokenFile-or-not$ExpectedTarget){throw'Live parameters required'};$live=& (Join-Path $PSScriptRoot 'Invoke-Phase9CTargetConformance.ps1') -BaseUri $BaseUri -TokenFile $TokenFile -ExpectedTarget $ExpectedTarget -SkipBatch:$SkipBatch;if($live.Result-ne'PASS'){throw'Phase 9C live failed'};$phase9b=& (Join-Path $PSScriptRoot 'Invoke-Phase9BGate.ps1') -BaseUri $BaseUri -TokenFile $TokenFile -ExpectedTarget $ExpectedTarget -SkipBuild;if($phase9b.Result-ne'PASS'){throw'Phase 9B live regression failed'};$phase9bLive='PASS';$smoke=& (Join-Path $root 'conformance\phase8\Invoke-Phase8TargetSmoke.ps1') -BaseUri $BaseUri -TokenFile $TokenFile -ExpectedTarget $ExpectedTarget -ExpectedBackend any -EnterWorld -RequireAuthoritative;if($smoke.Result-ne'PASS'){throw'V1 live regression failed'};$v1='PASS'}
[pscustomobject]@{Result='PASS';Phase='9C';Static=$static;Preconditions=$preconditions;Build=$build;Live=$(if($SkipLive){'SKIPPED'}else{$live});Phase9BStatic='PASS';Phase9BLive=$phase9bLive;V1=$v1;Phase9D='NOT_STARTED';WireProtocolV1='NOT_FROZEN'}
