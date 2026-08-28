[CmdletBinding()]param(
    [Parameter(Mandatory)][string]$BaseUri,
    [Parameter(Mandatory)][string]$TokenFile,
    [Parameter(Mandatory)][string]$ExpectedTarget)
$ErrorActionPreference='Stop'
$base=$BaseUri.TrimEnd('/');$token=(Get-Content -LiteralPath $TokenFile -Raw).Trim();$auth=@{Authorization="Bearer $token"}
function Assert-True([bool]$Condition,[string]$Message){if(-not$Condition){throw "Phase 9C Debug authority failed: $Message"}}
function Invoke-Json([string]$Method,[string]$Path,[hashtable]$Headers,[object]$Body){$p=@{Uri=$base+$Path;Method=$Method;Headers=$Headers;TimeoutSec=15};if($null-ne$Body){$p.ContentType='application/json';$p.Body=$Body|ConvertTo-Json -Depth 60 -Compress};Invoke-RestMethod @p}
function Invoke-Expected([object]$Body,[hashtable]$Headers,[string]$Code){$r=Invoke-WebRequest -Uri "$base/v0/debug/mutations" -Method Post -Headers $Headers -ContentType 'application/json' -Body ($Body|ConvertTo-Json -Depth 60 -Compress) -SkipHttpErrorCheck -TimeoutSec 15;$e=$r.Content|ConvertFrom-Json;Assert-True($e.error-eq$Code)"expected $Code got $($e.error)"}
$session=Invoke-Json GET '/v0/session' $auth $null;Assert-True($session.target-eq$ExpectedTarget-and$session.inWorld)'Integrated world required'
$fingerprint=Invoke-Json GET '/v0/world/fingerprint' $auth $null
$deadline=(Get-Date).AddSeconds(5);do{$observation=Invoke-Json POST '/v0/observe/deep' $auth @{perspective='server_authoritative';domains=@('player');includeProviderData=$false};$reference=@($observation.resourceRevisionRefs|Where-Object -Property resourceType -eq 'player')[0];$attribute=@($observation.server.player.attributes|Where-Object { $_.id -in @('minecraft:max_health','minecraft:generic.max_health') })[0];if($null-ne$reference-and$null-ne$attribute){break};Start-Sleep -Milliseconds 250}while((Get-Date)-lt$deadline)
Assert-True ($null-ne$reference-and$null-ne$attribute) 'Player resource/attribute did not become ready'
$mutation=@{operation='player.attribute.set';worldFingerprint=$fingerprint.worldFingerprint;expectedResourceVersion=$reference;attributeId='minecraft:max_health';value=([double]$attribute.base+0.25);expectedValue=[double]$attribute.base}
[void](Invoke-Json POST '/v0/debug/disarm' $auth $null)
Invoke-Expected $mutation $auth 'DEBUG_NOT_ARMED'
$afterMissing=Invoke-Json POST '/v0/observe/deep' $auth @{perspective='server_authoritative';domains=@('player');includeProviderData=$false}
$afterAttribute=@($afterMissing.server.player.attributes|Where-Object { $_.id -in @('minecraft:max_health','minecraft:generic.max_health') })[0]
Assert-True ([double]$afterAttribute.base -eq [double]$attribute.base) "missing Arm mutated Player: before=$($attribute.base) after=$($afterAttribute.base)"
$wrongArm=Invoke-WebRequest -Uri "$base/v0/debug/arm" -Method Post -Headers $auth -ContentType 'application/json' -Body (@{worldFingerprint=('0'*64);ttlMs=10000}|ConvertTo-Json -Compress) -SkipHttpErrorCheck
Assert-True(($wrongArm.Content|ConvertFrom-Json).error-eq'WORLD_FINGERPRINT_MISMATCH')'wrong fingerprint Arm'
$arm=Invoke-Json POST '/v0/debug/arm' $auth @{worldFingerprint=$fingerprint.worldFingerprint;namespaces=@('entity');ttlMs=30000}
$armHeaders=@{Authorization="Bearer $token";'X-MCP-Debug-Arm'=$arm.debugArmId}
Invoke-Expected $mutation $armHeaders 'DEBUG_SCOPE_DENIED'
[void](Invoke-Json POST '/v0/debug/disarm' $auth $null)
$arm=Invoke-Json POST '/v0/debug/arm' $auth @{worldFingerprint=$fingerprint.worldFingerprint;namespaces=@('player');ttlMs=30000}
$armHeaders=@{Authorization="Bearer $token";'X-MCP-Debug-Arm'=$arm.debugArmId}
$wrongEpoch=$reference.PSObject.Copy();$wrongEpoch.sessionEpoch=[guid]::NewGuid().ToString()
$epochMutation=$mutation.Clone();$epochMutation.expectedResourceVersion=$wrongEpoch
Invoke-Expected $epochMutation $armHeaders 'STALE_SESSION_EPOCH'
$wrongLifecycle=$reference.PSObject.Copy();$wrongLifecycle.lifecycleId=$reference.lifecycleId+'-stale'
$lifecycleMutation=$mutation.Clone();$lifecycleMutation.expectedResourceVersion=$wrongLifecycle
Invoke-Expected $lifecycleMutation $armHeaders 'RESOURCE_MISMATCH'
$final=Invoke-Json POST '/v0/observe/deep' $auth @{perspective='server_authoritative';domains=@('player');includeProviderData=$false}
$finalAttribute=@($final.server.player.attributes|Where-Object { $_.id -in @('minecraft:max_health','minecraft:generic.max_health') })[0]
Assert-True ([double]$finalAttribute.base -eq [double]$attribute.base) "failed precondition mutated Player: before=$($attribute.base) after=$($finalAttribute.base)"
[pscustomobject]@{Result='PASS';Target=$ExpectedTarget;MissingArm='PASS';WrongFingerprint='PASS';Namespace='PASS';WrongEpoch='PASS';WrongLifecycle='PASS';InvocationOnFailure=0;WorldChangesOnFailure=0}
