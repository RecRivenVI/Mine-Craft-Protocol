[CmdletBinding()]param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$runtimeCore=Join-Path (Resolve-Path (Join-Path $PSScriptRoot '../../../..')) 'components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core'
function Require([bool]$Ok,[string]$Why){if(-not$Ok){throw "Diagnostic boundary: $Why"}}
# Formal adapters supersede the original three-target write-capable read spike.
# Retain no-load/no-write/no-reflection and diagnostic evidence assertions;
# exercise the current five-target read/debug contracts instead of old plan text.
foreach($gate in @('Invoke-Phase9BStaticGate.ps1','Invoke-Phase9CStaticGate.ps1','Invoke-Phase9D0StaticGate.ps1')){
 $result=& (Join-Path $PSScriptRoot $gate)
 Require ($result.Result-eq'PASS') "$gate failed"
}
foreach($target in @('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')){
 $source=if($target-eq'26.3-fabric'){'client'}else{'main'}
 $dir=Join-Path $root "versions/$target/src/$source/java/io/github/recrivenvi/minecraftprotocol/probe/runtime"
 $engine=Get-Content (Join-Path $root "versions/$target/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/Phase9ASpikeEngine.java") -Raw
 $transport=Get-Content (Join-Path $runtimeCore 'ProbeTransport.java') -Raw
 Require ($engine-notmatch 'getChunk\([^,]+,[^,]+\)|setChunk|writeCompressed|\.save\(|java\.lang\.reflect|Class\.forName|setAccessible') "$target no-load/no-write/no-reflection boundary"
 foreach($marker in @('getChunkNow','runtime_internal','diagnostic','gameplayEvidence')){Require ($engine.Contains($marker)) "$target missing $marker"}
 if($target-in@('1.20.1-forge','26.3-neoforge','26.3-fabric')){
  foreach($marker in @('phase9a.capability_inventory','phase9a.deep_observation','phase9a.experimental_keyframe','phase9a.experimental_delta','phase9a.reconstruction','snapshot_diff')){Require ($engine.Contains($marker)) "$target retained diagnostic $marker"}
  foreach($route in @('/v0/diagnostics/phase9a/inventory','/v0/diagnostics/phase9a/observe','/v0/diagnostics/phase9a/keyframe','/v0/diagnostics/phase9a/delta','/v0/diagnostics/phase9a/reconstruct')){Require ($transport.Contains($route)) "$target route missing $route"}
 }
}
[pscustomobject]@{Result='PASS';Targets=5;ForceLoad='ABSENT';PersistentWrite='NOT_IMPLEMENTED';Reflection='ABSENT';Runtime='NOT_RUN'}
