[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget,
    [switch]$SkipBatch)
$ErrorActionPreference='Stop'
$parameters=@{BaseUri=$BaseUri;TokenFile=$TokenFile;ExpectedTarget=$ExpectedTarget}
$authority=& (Join-Path $PSScriptRoot 'Invoke-Phase9CDebugAuthorityConformance.ps1') @parameters
Start-Sleep -Seconds 3
$playerEntity=& (Join-Path $PSScriptRoot 'Invoke-Phase9CPlayerEntityConformance.ps1') @parameters
Start-Sleep -Seconds 3
$worldBlockEntity=& (Join-Path $PSScriptRoot 'Invoke-Phase9CWorldBlockEntityConformance.ps1') @parameters
Start-Sleep -Seconds 3
$menuProvider=& (Join-Path $PSScriptRoot 'Invoke-Phase9CMenuProviderConformance.ps1') @parameters
$batch=$null
if(-not$SkipBatch){Start-Sleep -Seconds 5;$batch=& (Join-Path $PSScriptRoot 'Invoke-Phase9CBatchEvidenceConformance.ps1') @parameters}
foreach($value in @($authority,$playerEntity,$worldBlockEntity,$menuProvider,$batch|Where-Object{$null-ne$_})){
    if($value.Result-ne'PASS'){throw "Phase 9C target conformance failed: $($value|ConvertTo-Json -Depth 8 -Compress)"}
}
[pscustomobject]@{Result='PASS';Target=$ExpectedTarget;Authority=$authority;PlayerEntity=$playerEntity;WorldBlockEntity=$worldBlockEntity;MenuProvider=$menuProvider;Batch=$(if($SkipBatch){'SKIPPED'}else{$batch});Chunk='PARTIAL';Client='PARTIAL';Network='PARTIAL_NO_RAW_PACKET_INJECTION'}
