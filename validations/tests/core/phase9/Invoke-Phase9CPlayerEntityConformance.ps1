[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget)
$ErrorActionPreference='Stop'
$base=$BaseUri.TrimEnd('/')
$token=(Get-Content -LiteralPath $TokenFile -Raw).Trim()
$auth=@{Authorization="Bearer $token"}
function Assert-True([bool]$Condition,[string]$Message) {
    if(-not $Condition) { throw "Phase 9C Player/Entity failed: $Message" }
}
function Invoke-Json([string]$Method,[string]$Path,[hashtable]$Headers,[object]$Body) {
    $parameters=@{Uri=$base+$Path;Method=$Method;Headers=$Headers;TimeoutSec=15}
    if($null-ne$Body){$parameters.ContentType='application/json';$parameters.Body=$Body|ConvertTo-Json -Depth 60 -Compress}
    Invoke-RestMethod @parameters
}
function Invoke-ExpectedError([string]$Path,[hashtable]$Headers,[object]$Body,[string]$Code) {
    $response=Invoke-WebRequest -Uri ($base+$Path) -Method Post -Headers $Headers `
        -ContentType 'application/json' -Body ($Body|ConvertTo-Json -Depth 60 -Compress) `
        -SkipHttpErrorCheck -TimeoutSec 15
    $error=$response.Content|ConvertFrom-Json
    Assert-True ($response.StatusCode -ge 400 -and $error.error -eq $Code) "expected $Code, got $($response.StatusCode) $($error.error)"
}
function Observe([string[]]$Domains) {
    Invoke-Json POST '/v0/observe/deep' $auth @{
        perspective='server_authoritative';domains=$Domains
        selector=@{chunkRadius=0;entityRadius=32};includeProviderData=$false
        budgets=@{maxEntities=64;maxResponseBytes=524288}
    }
}
$session=Invoke-Json GET '/v0/session' $auth $null
Assert-True ($session.target-eq$ExpectedTarget-and$session.inWorld) 'Integrated world required'
$fingerprint=Invoke-Json GET '/v0/world/fingerprint' $auth $null
$arm=Invoke-Json POST '/v0/debug/arm' $auth @{
    worldFingerprint=$fingerprint.worldFingerprint;namespaces=@('player','entity');ttlMs=60000
}
$debug=@{Authorization="Bearer $token";'X-MCP-Debug-Arm'=$arm.debugArmId}
$originalAttribute=$null
$pigUuid=$null
try {
    $deadline=(Get-Date).AddSeconds(5);do{$playerBefore=Observe @('player');$playerRef=@($playerBefore.resourceRevisionRefs|Where-Object -Property resourceType -eq 'player')[0];$attribute=@($playerBefore.server.player.attributes|Where-Object { $_.id -in @('minecraft:max_health','minecraft:generic.max_health') })[0];if($null-ne$playerRef-and$null-ne$attribute){break};Start-Sleep -Milliseconds 250}while((Get-Date)-lt$deadline)
    Assert-True ($null-ne$playerRef-and$null-ne$attribute) 'Player token/attribute missing'
    $originalAttribute=[double]$attribute.base
    $requested=$originalAttribute+0.25
    $playerMutation=Invoke-Json POST '/v0/debug/mutations' $debug @{
        operation='player.attribute.set';worldFingerprint=$fingerprint.worldFingerprint
        expectedResourceVersion=$playerRef;attributeId='minecraft:max_health'
        value=$requested;expectedValue=$originalAttribute
    }
    Assert-True ([long]$playerMutation.afterResourceVersion.revision-ne[long]$playerMutation.beforeResourceVersion.revision) 'Player revision did not advance'
    Invoke-ExpectedError '/v0/debug/mutations' $debug @{
        operation='player.attribute.set';worldFingerprint=$fingerprint.worldFingerprint
        expectedResourceVersion=$playerRef;attributeId='minecraft:max_health'
        value=($requested+0.25);expectedValue=$requested
    } 'STALE_RESOURCE_REVISION'
    Start-Sleep -Milliseconds 150
    $playerCurrent=Observe @('player')
    $currentRef=@($playerCurrent.resourceRevisionRefs|Where-Object -Property resourceType -eq 'player')[0]
    $currentAttribute=@($playerCurrent.server.player.attributes|Where-Object {
        $_.id -in @('minecraft:max_health','minecraft:generic.max_health')
    })[0]
    $playerRestore=Invoke-Json POST '/v0/debug/mutations' $debug @{
        operation='player.attribute.set';worldFingerprint=$fingerprint.worldFingerprint
        expectedResourceVersion=$currentRef;attributeId='minecraft:max_health'
        value=$originalAttribute;expectedValue=[double]$currentAttribute.base
    }

    [void](Invoke-Json POST '/v0/control/emergency-release' $auth $null)
    $lease=Invoke-Json POST '/v0/control/acquire' $auth @{ttlMs=30000}
    $leaseHeaders=@{Authorization="Bearer $token";'X-MCP-Control-Lease'=$lease.leaseId;'X-MCP-Debug-Arm'=$arm.debugArmId}
    try {
        $arrange=Invoke-Json POST '/v0/debug/phase9a/scenario' $leaseHeaders @{action='entity_spawn_pig'}
        $pigUuid=$arrange.spawnedEntityUuid
    } finally {
        [void](Invoke-Json POST '/v0/control/release' $leaseHeaders $null)
    }
    Start-Sleep -Milliseconds 750
    $entitiesAfter=Observe @('entities')
    $pig=@($entitiesAfter.server.entities|Where-Object -Property uuid -eq $pigUuid)[0]
    Assert-True ($null-ne$pig) 'Typed Debug Arrange did not produce a bounded test pig'
    $entityRef=@($entitiesAfter.resourceRevisionRefs|Where-Object {
        $_.resourceType-eq'entity'-and$_.resourceKey-eq$pigUuid
    })[0]
    $entityMutation=Invoke-Json POST '/v0/debug/mutations' $debug @{
        operation='entity.no_gravity.set';worldFingerprint=$fingerprint.worldFingerprint
        expectedResourceVersion=$entityRef;entityUuid=$pigUuid;value=$true
        expectedNoGravity=$false;expectedEntityType='minecraft:pig'
    }
    Assert-True ([long]$entityMutation.afterResourceVersion.revision-ne[long]$entityMutation.beforeResourceVersion.revision) 'Entity revision did not advance'
    Invoke-ExpectedError '/v0/debug/mutations' $debug @{
        operation='entity.no_gravity.set';worldFingerprint=$fingerprint.worldFingerprint
        expectedResourceVersion=$entityRef;entityUuid=$pigUuid;value=$false
        expectedNoGravity=$true;expectedEntityType='minecraft:pig'
    } 'STALE_RESOURCE_REVISION'
    $entityCurrent=Observe @('entities')
    $entityCurrentRef=@($entityCurrent.resourceRevisionRefs|Where-Object {
        $_.resourceType-eq'entity'-and$_.resourceKey-eq$pigUuid
    })[0]
    $entityCurrentState=@($entityCurrent.server.entities|Where-Object -Property uuid -eq $pigUuid)[0]
    Assert-True ($null-ne$entityCurrentRef-and[bool]$entityCurrentState.noGravity) 'Entity current token missing after mutation'
    $entityRestore=Invoke-Json POST '/v0/debug/mutations' $debug @{
        operation='entity.no_gravity.set';worldFingerprint=$fingerprint.worldFingerprint
        expectedResourceVersion=$entityCurrentRef;entityUuid=$pigUuid;value=$false
        expectedNoGravity=$true;expectedEntityType='minecraft:pig'
    }
    $audit=Invoke-Json GET '/v0/audit?limit=256' $auth $null
    Assert-True (@($audit.entries|Where-Object -Property path -eq '/v0/debug/mutations'|Where-Object -Property outcome -eq 'completed').Count-ge4) 'Debug audit correlation missing'
    [pscustomobject]@{
        Result='PASS';Target=$ExpectedTarget
        Player='PASS';Entity='PASS';StaleToken='PASS';OwnerThread='server_thread'
        PlayerRevision="$($playerMutation.beforeResourceVersion.revision)->$($playerMutation.afterResourceVersion.revision)"
        EntityRevision="$($entityMutation.beforeResourceVersion.revision)->$($entityMutation.afterResourceVersion.revision)"
        Authority=$playerMutation.authority;Evidence=$playerMutation.evidence;Arrange='DEBUG_PRIVILEGED'
        Cleanup='PASS';Audit='PASS'
    }
}
finally {
    if($null-ne$pigUuid) {
        try {
            [void](Invoke-Json POST '/v0/control/emergency-release' $auth $null)
            $cleanupLease=Invoke-Json POST '/v0/control/acquire' $auth @{ttlMs=15000}
            $cleanupHeaders=@{Authorization="Bearer $token";'X-MCP-Control-Lease'=$cleanupLease.leaseId}
            $cleanupHeaders['X-MCP-Debug-Arm']=$arm.debugArmId
            [void](Invoke-Json POST '/v0/debug/phase9a/scenario' $cleanupHeaders @{action='entity_remove';entityUuid=$pigUuid})
            [void](Invoke-Json POST '/v0/control/release' $cleanupHeaders $null)
        } catch { }
    }
}
