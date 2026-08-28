[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget)
$ErrorActionPreference='Stop'
$base=$BaseUri.TrimEnd('/')
$token=(Get-Content $TokenFile -Raw).Trim()
$auth=@{Authorization="Bearer $token"}
function Assert-True([bool]$Condition,[string]$Message) {
    if(-not $Condition) { throw "Phase 9B.2 provider revision failed: $Message" }
}
function Invoke-Provider([string]$Id,[string]$Probe) {
    Invoke-RestMethod "$base/v0/observe/deep" -Method Post -Headers $auth -ContentType 'application/json' -Body (@{
        perspective='server_authoritative'
        domains=@('providers')
        includeProviderData=$true
        providerIds=@("minecraft_protocol_probe:$Id")
        providerQuery=@{probe=$Probe}
        budgets=@{
            maxProviders=1
            providerTimeoutMs=250
            maxProviderBytes=16384
            maxTotalProviderBytes=16384
            maxResponseBytes=131072
        }
    }|ConvertTo-Json -Depth 20 -Compress)
}
function Get-Provider($Response,[string]$Id) {
    @($Response.providers|Where-Object -Property providerId -eq "minecraft_protocol_probe:$Id")[0]
}
function Get-Revision($Response,[string]$Id) {
    @($Response.resourceRevisionRefs|
        Where-Object -Property resourceType -eq 'provider'|
        Where-Object {$_.resourceKey.StartsWith("minecraft_protocol_probe:$Id")})[0]
}
$fallbackA=Invoke-Provider 'fallback-query' 'health-only'
$fallbackB=Invoke-Provider 'fallback-query' 'inventory-only'
$fallbackC=Invoke-Provider 'fallback-query' 'all'
$fallbackProviderA=Get-Provider $fallbackA 'fallback-query'
$fallbackProviderB=Get-Provider $fallbackB 'fallback-query'
$fallbackRefA=Get-Revision $fallbackA 'fallback-query'
$fallbackRefB=Get-Revision $fallbackB 'fallback-query'
$fallbackRefC=Get-Revision $fallbackC 'fallback-query'
Assert-True ($fallbackProviderA.data.view -ne $fallbackProviderB.data.view) 'fallback payload must be query shaped'
Assert-True (
    [long]$fallbackRefA.revision -eq [long]$fallbackRefB.revision -and
    [long]$fallbackRefA.revision -eq [long]$fallbackRefC.revision) 'fallback resource revision query invariance'
Assert-True (
    $fallbackRefA.revisionScope -eq 'resource' -and
    [bool]$fallbackRefA.mutationPreconditionEligible) 'fallback resource scope'
$viewA=Invoke-Provider 'query-view' 'a'
$viewB=Invoke-Provider 'query-view' 'b'
$viewRefA=Get-Revision $viewA 'query-view'
$viewRefB=Get-Revision $viewB 'query-view'
Assert-True (
    $viewRefA.revisionScope -eq 'query_view' -and
    -not [bool]$viewRefA.mutationPreconditionEligible) 'query view scope'
Assert-True ($viewRefA.resourceKey -ne $viewRefB.resourceKey) 'query view identity'
$nativeFirst=Get-Provider (Invoke-Provider 'native-regression' 'first') 'native-regression'
Assert-True ($nativeFirst.status -eq 'completed') 'native first'
$nativeRegression=Get-Provider (Invoke-Provider 'native-regression' 'second') 'native-regression'
Assert-True ($nativeRegression.reason -eq 'provider_revision_regressed') 'native regression'
$nativeQuarantined=Get-Provider (Invoke-Provider 'native-regression' 'third') 'native-regression'
Assert-True ($nativeQuarantined.reason -eq 'provider_quarantined') 'native regression quarantine'
$consistentFirst=Get-Provider (Invoke-Provider 'native-inconsistent' 'first') 'native-inconsistent'
Assert-True ($consistentFirst.status -eq 'completed') 'native consistency first'
$inconsistent=Get-Provider (Invoke-Provider 'native-inconsistent' 'second') 'native-inconsistent'
Assert-True ($inconsistent.reason -eq 'provider_revision_inconsistent') 'native same revision inconsistency'
$inconsistentQuarantine=Get-Provider (Invoke-Provider 'native-inconsistent' 'third') 'native-inconsistent'
Assert-True ($inconsistentQuarantine.reason -eq 'provider_quarantined') 'native inconsistency quarantine'
[pscustomobject]@{
    Result='PASS'
    Target=$ExpectedTarget
    FallbackResource='PASS'
    QueryIndependent='PASS'
    QueryView='PASS'
    NativeMonotonicity='PASS'
    NativeConsistency='PASS'
    Quarantine='PASS'
}

