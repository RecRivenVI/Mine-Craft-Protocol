[CmdletBinding()] param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$runtimeCore=Join-Path (Resolve-Path (Join-Path $PSScriptRoot '../../../..')) 'components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core'
function A([bool]$c,[string]$m){if(-not$c){throw "Phase 9B static gate failed: $m"}}
$schema=Get-Content (Join-Path $root 'components\protocol_schema\src\main\openapi\minecraft-control-v0.json') -Raw|ConvertFrom-Json
A ($schema.info.'x-stability' -eq 'unstable') 'Native contract stability must remain explicit; documentary completion is not test evidence'
A ($schema.info.version -eq '0.0.1-ui-m1') 'OpenAPI version after explicit Control Round 1 evolution'
foreach($p in @('/v0/observe/deep','/v0/observe/deep/capabilities')){A($null-ne$schema.paths.$p) "missing $p"}
foreach($s in @('DeepObservationRequest','DeepObservationResponse','ObservationMetadata','ResourceRevisionRef','PlayerSnapshot','MenuSnapshot','EntitySnapshot','BlockSnapshot','BlockEntitySnapshot','ChunkSnapshot','ChunkLoadingSummary','ScheduledTickSnapshot','ProviderV2Result')){A($null-ne$schema.components.schemas.$s) "missing schema $s"}
$targets=@(
 @{Id='1.20.1-forge';Src='src\main\java';Mixin='src\main\resources\minecraft_protocol_probe.mixins.json';Has9A=$true},
 @{Id='1.21.1-neoforge';Src='src\main\java';Mixin='src\main\resources\minecraft_protocol_probe.mixins.json';Has9A=$false},
 @{Id='26.1.2-neoforge';Src='src\main\java';Mixin='src\main\resources\minecraft_protocol_probe.mixins.json';Has9A=$false},
 @{Id='26.3-neoforge';Src='src\main\java';Mixin='src\main\resources\minecraft_protocol_probe.mixins.json';Has9A=$true},
 @{Id='26.3-fabric';Src='src\client\java';Mixin='src\client\resources\minecraft_protocol_probe.client.mixins.json';Has9A=$true})
foreach($t in $targets){
 $base=Join-Path $root "versions\$($t.Id)\$($t.Src)\io\github\recrivenvi\minecraftprotocol\probe"
 $engine=Get-Content (Join-Path $root "versions/$($target.Id)/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/Phase9ASpikeEngine.java") -Raw
 $revisions=Get-Content (Join-Path $runtimeCore 'ObservationRevisionTracker.java') -Raw
 $transport=Get-Content (Join-Path $runtimeCore 'ProbeTransport.java') -Raw
 $provider=Get-Content (Join-Path $root 'components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/api/AgentDataProviderV2.java') -Raw
 $mixin=Get-Content (Join-Path $root "versions\$($t.Id)\$($t.Mixin)") -Raw
 foreach($m in @('formalCapabilities','captureFormal','formalize','maxResponseBytes','projectionApplied','includeSerializedState','serialization_hooks_invoked','loadingSummary','scheduledBlockTicks','scheduledFluidTicks','ticketHookVerified','scheduledTickHookVerified')){A($engine.Contains($m)) "$($t.Id) missing $m"}
 A($revisions.Contains('snapshot_change_sequence')-and$revisions.Contains('sessionEpoch')) "$($t.Id) revision tracker"
 foreach($m in @('snapshotSafe','mayInitialize','mayLoadData','mayAccessStorage','mayMutate','deltaCapability','debugDeclaration','requiredScopes')){A($provider.Contains($m)) "$($t.Id) provider missing $m"}
 A($transport.Contains('/v0/observe/deep')) "$($t.Id) formal route"
 if($t.Has9A){A($transport.Contains('/v0/diagnostics/phase9a/observe')) "$($t.Id) diagnostic coexistence"}
 A($mixin.Contains('DistanceManagerAccessor')-and$mixin.Contains('LevelTicksAccessor')) "$($t.Id) read-only hook config"
 A($engine-notmatch'Class\.forName|setAccessible|java\.lang\.reflect') "$($t.Id) reflection"
}
[pscustomobject]@{Result='PASS';Targets=5;OpenApi='PASS';ProviderV2='PASS';TicketHooks='PASS';ScheduledTickHooks='PASS';WireProtocolV1='NOT_FROZEN'}
