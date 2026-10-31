[CmdletBinding()]param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$runtimeCore=Join-Path (Resolve-Path (Join-Path $PSScriptRoot '../../../..')) 'components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core'
function Require([bool]$ok,[string]$reason){if(-not$ok){throw "Control Round 1: $reason"}}
$schema=Get-Content (Join-Path $root 'components/protocol_schema/src/main/openapi/minecraft-control-v0.json') -Raw|ConvertFrom-Json
Require ($schema.info.version-eq'0.0.1-ui-m1') 'wrong V0 schema version'
$policies=@('READ_COMPATIBLE','OPERATE_OR_TAKEOVER','TAKEOVER_REQUIRED','MODE_INDEPENDENT','COMMAND_ROUTE_DEPENDENT')
$count=0
foreach($path in $schema.paths.PSObject.Properties){foreach($operation in $path.Value.PSObject.Properties){
    if($operation.Name-notin@('get','post','put','patch','delete','options','head','trace')-or-not$operation.Value.operationId){continue}
    Require ($operation.Value.'x-agent-mode'-in$policies) "unclassified $($operation.Name) $($path.Name)"
    $count++
}}
Require ($null-ne$schema.paths.'/v0/control/mode'.post) 'explicit mode route missing'
Require ($schema.components.schemas.AgentMode.enum.Count-eq3) 'intent enum must remain small'
Require ($schema.components.schemas.ControlModeRequest.properties.PSObject.Properties.Name-notcontains'userConsent') 'fake consent field'
$shared=Get-Content (Join-Path $root 'components/runtime_safety/src/main/java/io/github/recrivenvi/minecraftprotocol/safety/AgentControlSession.java') -Raw
Require ($shared-match'READ,\s*OPERATE,\s*TAKEOVER'-and$shared-match'withTakeover'-and$shared-match'beginOperate') 'shared intent/owner guards missing'
$targets=@('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')
$settings=Get-Content (Join-Path $root 'settings.gradle.kts') -Raw
Require ($settings-match'product\("runtime_core"\)') 'shared protocol component must be a product'
foreach($target in $targets){
    $source=if($target-eq'26.3-fabric'){'client'}else{'main'}
    $runtime=if($target-eq'1.20.1-forge'){'ForgeProbeRuntime'}elseif($target-eq'26.3-fabric'){'FabricProbeRuntime'}else{'NeoForgeProbeRuntime'}
    $directory=Join-Path $root "versions/$target/src/$source/java/io/github/recrivenvi/minecraftprotocol/probe/runtime"
    $transport=Get-Content (Join-Path $runtimeCore 'ProbeTransport.java') -Raw
    $state=Get-Content (Join-Path $runtimeCore 'ProtocolState.java') -Raw
    $adapter=Get-Content (Join-Path $directory "$runtime.java") -Raw
    Require (-not(Test-Path (Join-Path $directory 'ProbeTransport.java'))-and-not(Test-Path (Join-Path $directory 'ProtocolState.java'))) "$target must not retain duplicate protocol implementations"
    Require (($adapter-replace'\s','').Contains('ProbeTransport.forInstance(this,minecraft.gameDirectory.toPath(),"'+$target+'")')-and$adapter.Contains('import io.github.recrivenvi.minecraftprotocol.core.ProbeTransport;')) "$target shared transport instance identity/adapter binding missing"
    $experimental=$target-in@('1.20.1-forge','26.3-neoforge','26.3-fabric')
    Require (($adapter-match'implements Phase9AExperimentalService')-eq$experimental) "$target experimental route surface changed"
    Require ($transport-match'attachControlSession'-and$transport-match'/v0/control/mode') "$target mode session is not connected"
    Require (([regex]::Matches($transport,'protocolState.captureInputGuard\(metadata.leaseId\(\)\)')).Count-eq4-and$transport-match'protocolState.admitInput\(\s*inputGuard,') "$target generation-fenced input admission missing"
    Require ($state-match'captureInputGuard\(String leaseId\)'-and$state-match'requireTakeover\(guard.leaseId\(\)\)'-and$state-match'guard.run\(\)') "$target TAKEOVER/Lease/generation admission checks missing"
    Require ($transport-match'protocolState.operate\(service::openAutomationProbeScreen\)') "$target GUI Fixture must use OPERATE, not a Lease"
    Require ($transport-match'singleDebugAuthorization\(\s*metadata,\s*work\s*\)'-and$transport-match'debugBatches.start\(\s*operationId,\s*body,\s*metadata,\s*work\s*\)') "$target Debug admission missing"
    Require ($state-match'inputCleanupBarrier'-and$state-match'STALE_MODE_REVISION'-and$state-match'TAKEOVER_ONLY') "$target transition/latch contract missing"
    $minecraftMixin=Get-Content (Join-Path $directory '../mixin/MinecraftMixin.java') -Raw
    Require ($minecraftMixin-match'method = "close"'-and$minecraftMixin-match'beforeClientClose'-and$adapter-match'runtime_pre_loader_shutdown'-and$adapter-match'shutdownStarted.compareAndSet') "$target must drain before Loader teardown with an idempotent JVM fallback"
    $compact=$adapter-replace'\s',''
    Require ($compact.Contains('controlSession.withTakeover(accepted,')-and$compact.Contains('commandGate.enter(accepted,permit)')-and$compact.Contains('dispatch.check()')-and$adapter-match'onOperatingServer\('-and$compact.Contains('extensionInputs.collect(')-and$compact.Contains('permit::check')) "$target owner-thread enforcement and guarded framework/native callback missing"
    Require ($transport-notmatch'storage/write|/v0/storage.write') "$target must not implement Persistent Write"
}
$server=Get-Content (Join-Path $root 'components/companion/src/server.ts') -Raw
$meta=[regex]::Matches($server,"minecraft/modePolicy").Count
Require ($meta-eq26) 'all 26 MCP tools must declare mode policy'
Require ($server-match'controlToolSchema'-and$server-match"action: z.literal\('set_mode'\)") 'MCP typed mode discriminator missing'
Require ($server-notmatch'state.debugArmId = undefined;[\s\S]{0,60}return result;[\s\S]{0,60}const active') 'Lease release must not impersonate Debug disarm'
[pscustomobject]@{Result='PASS';Targets=5;ClassifiedHttpOperations=$count;ClassifiedMcpTools=$meta;WireProtocolV1='NOT_FROZEN';NewCursorModel='IMPLEMENTED_PENDING_UNIFIED_ACCEPTANCE'}
