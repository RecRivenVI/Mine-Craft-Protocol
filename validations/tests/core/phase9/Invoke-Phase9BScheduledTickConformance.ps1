[CmdletBinding()]param([Parameter(Mandatory)][string]$BaseUri,[Parameter(Mandatory)][string]$TokenFile,[Parameter(Mandatory)][string]$ExpectedTarget)
$r=& (Join-Path $PSScriptRoot 'Invoke-Phase9BTicketConformance.ps1') -BaseUri $BaseUri -TokenFile $TokenFile -ExpectedTarget $ExpectedTarget
if($r.Result -ne 'PASS'){throw 'scheduled tick source gate failed'}
$base=$BaseUri.TrimEnd('/');$token=(Get-Content $TokenFile -Raw).Trim();$auth=@{Authorization="Bearer $token"}
function J($m,$p,$h,$b){$q=@{Uri=$base+$p;Method=$m;Headers=$h};if($null-ne$b){$q.ContentType='application/json';$q.Body=$b|ConvertTo-Json -Depth 20 -Compress};Invoke-RestMethod @q}
$player=J GET '/v0/player' $auth $null;$x=[math]::Floor($player.x)+3;$y=[math]::Floor($player.y);$z=[math]::Floor($player.z);$old=J GET "/v0/server/world/block?x=$x&y=$y&z=$z" $auth $null
J POST '/v0/control/emergency-release' $auth $null|Out-Null;$lease=J POST '/v0/control/acquire' $auth @{ttlMs=20000};$leaseHeaders=@{Authorization="Bearer $token";'X-MCP-Control-Lease'=$lease.leaseId};$fp=J GET '/v0/world/fingerprint' $auth $null;$arm=J POST '/v0/debug/arm' $leaseHeaders @{worldFingerprint=$fp.worldFingerprint;ttlMs=20000};$debugHeaders=@{Authorization="Bearer $token";'X-MCP-Control-Lease'=$lease.leaseId;'X-MCP-Debug-Arm'=$arm.debugArmId}
try{
 J POST '/v0/debug/world/block' $debugHeaders @{x=$x;y=$y;z=$z;blockId='minecraft:water';expectedBlockId=$old.block}|Out-Null
 $snapshot=J POST '/v0/observe/deep' $auth @{perspective='server_authoritative';domains=@('chunks');selector=@{chunkRadius=1;entityRadius=0};includeProviderData=$false}
 $chunks=@($snapshot.server.chunks);$fluid=@($chunks.scheduledFluidTicks);if($fluid.Count-lt1){throw 'no scheduled fluid tick captured'}
 foreach($field in @('x','y','z','type','triggerTick','priority','subTickOrder','chunkX','chunkZ')){if($null-eq$fluid[0].$field){throw "missing $field"}}
 [pscustomobject]@{Target=$ExpectedTarget;BlockTicks=@($chunks.scheduledBlockTicks).Count;FluidTicks=$fluid.Count;Type=$fluid[0].type;TriggerTick=$fluid[0].triggerTick;Priority=$fluid[0].priority;Position="$($fluid[0].x),$($fluid[0].y),$($fluid[0].z)";Fields='position,type,triggerTick,priority,subTickOrder,chunk';ReadOnlyHook='PASS';Arrange='existing typed debug block';Result='PASS'}
}finally{
 try{J POST '/v0/debug/world/block' $debugHeaders @{x=$x;y=$y;z=$z;blockId=$old.block;expectedBlockId='minecraft:water'}|Out-Null}catch{}
 try{J POST '/v0/debug/disarm' $leaseHeaders $null|Out-Null}catch{};try{J POST '/v0/control/release' $leaseHeaders $null|Out-Null}catch{}
}
