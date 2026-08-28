[CmdletBinding()]param([Parameter(Mandatory)][string]$BaseUri,[Parameter(Mandatory)][string]$TokenFile,[Parameter(Mandatory)][string]$ExpectedTarget)
$token=(Get-Content $TokenFile -Raw).Trim();$auth=@{Authorization="Bearer $token"}
$r=Invoke-RestMethod ($BaseUri.TrimEnd('/')+'/v0/observe/deep') -Method Post -Headers $auth -ContentType 'application/json' -Body (@{perspective='both';domains=@('player','menu');includeProviderData=$false}|ConvertTo-Json -Compress)
if(-not$r.comparison.uuid.agreement){throw'client/server UUID mismatch'}
foreach($field in @('uuid','dimension','health','selectedSlot','x','y','z','yaw','pitch','velocityX','velocityY','velocityZ')){if($null-eq$r.comparison.$field){throw"missing comparison $field"}}
[pscustomobject]@{Target=$ExpectedTarget;ClientKnown='PASS';ServerAuthoritative='PASS';Both='PASS';Alignment=$r.metadata.alignmentQuality;Result='PASS'}
