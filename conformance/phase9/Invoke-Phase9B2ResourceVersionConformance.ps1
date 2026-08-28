[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget)
$ErrorActionPreference='Stop'
$base=$BaseUri.TrimEnd('/')
$token=(Get-Content $TokenFile -Raw).Trim()
$auth=@{Authorization="Bearer $token"}
function Assert-True([bool]$Condition,[string]$Message) {
    if(-not $Condition) { throw "Phase 9B.2 resource version failed: $Message" }
}
$request=@{
    perspective='both'
    domains=@('player','entities','blocks','block_entities','chunks','menu')
    selector=@{chunkRadius=0;entityRadius=16}
    includeSerializedBlockEntities=$true
    includeProviderData=$false
    budgets=@{maxEntities=32;maxBlockEntities=32;maxResponseBytes=524288}
}
$response=Invoke-RestMethod "$base/v0/observe/deep" -Method Post -Headers $auth -ContentType 'application/json' -Body (
    $request|ConvertTo-Json -Depth 20 -Compress)
Assert-True ($response.sessionEpoch -eq $response.metadata.sessionEpoch) 'response epoch'
Assert-True (@($response.resourceRevisionRefs).Count -gt 0) 'revision refs'
foreach($ref in @($response.resourceRevisionRefs)) {
    foreach($field in @(
            'sessionEpoch','resourceType','resourceKey','lifecycleId',
            'revision','revisionSource','revisionScope','mutationPreconditionEligible')) {
        Assert-True ($null-ne$ref.$field) "missing $field"
    }
    Assert-True ($ref.sessionEpoch -eq $response.sessionEpoch) 'ref epoch'
    if($ref.revisionScope -eq 'resource') {
        Assert-True ([bool]$ref.mutationPreconditionEligible) 'resource precondition eligibility'
    }
}
$client=@($response.resourceRevisionRefs|
    Where-Object -Property resourceType -eq 'player'|
    Where-Object -Property resourceKey -match '@client_known$')[0]
$server=@($response.resourceRevisionRefs|
    Where-Object -Property resourceType -eq 'player'|
    Where-Object -Property resourceKey -match '@server_authoritative$')[0]
Assert-True ($client.resourceKey -ne $server.resourceKey) 'client/server player identity'
Assert-True ($client.sessionEpoch -eq $server.sessionEpoch) 'player epoch'
$serialized=@($response.resourceRevisionRefs|
    Where-Object -Property resourceType -eq 'block_entity_serialized')
foreach($ref in $serialized) {
    $baseRef=@($response.resourceRevisionRefs|
        Where-Object -Property resourceType -eq 'block_entity'|
        Where-Object -Property resourceKey -eq $ref.resourceKey)[0]
    Assert-True ($baseRef.lifecycleId -eq $ref.lifecycleId) 'serialized BE lifecycle'
}
[pscustomobject]@{
    Result='PASS'
    Target=$ExpectedTarget
    Epoch='PASS'
    ClientServerPlayer='PASS'
    LifecycleFields='PASS'
    SerializedBlockEntity='PASS'
    RuntimeRestart='JAVA_TEST'
    MenuReuse='JAVA_TEST'
    EntityRecreate='JAVA_TEST'
    ChunkReload='JAVA_TEST'
    PreconditionVerifier='JAVA_TEST'
}

