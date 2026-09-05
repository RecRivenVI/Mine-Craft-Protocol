[CmdletBinding()]param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
function Require([bool]$ok,[string]$reason){if(-not$ok){throw "Control Round 1: $reason"}}
$schema=Get-Content (Join-Path $root 'protocol-schema/src/main/openapi/minecraft-control-v0.json') -Raw|ConvertFrom-Json
Require ($schema.info.version-eq'0.0.1-control-r1') 'wrong V0 schema version'
$policies=@('READ_COMPATIBLE','OPERATE_REQUIRED','TAKEOVER_REQUIRED','MODE_INDEPENDENT')
$count=0
foreach($path in $schema.paths.PSObject.Properties){foreach($operation in $path.Value.PSObject.Properties){
    if(-not$operation.Value.operationId){continue}
    Require ($operation.Value.'x-agent-mode'-in$policies) "unclassified $($operation.Name) $($path.Name)"
    $count++
}}
Require ($null-ne$schema.paths.'/v0/control/mode'.post) 'explicit mode route missing'
Require ($schema.components.schemas.AgentMode.enum.Count-eq3) 'intent enum must remain small'
Require ($schema.components.schemas.ControlModeRequest.properties.PSObject.Properties.Name-notcontains'userConsent') 'fake consent field'
$shared=Get-Content (Join-Path $root 'runtime-safety/src/main/java/io/github/recrivenvi/minecraftprotocol/safety/AgentControlSession.java') -Raw
Require ($shared-match'READ, OPERATE, TAKEOVER'-and$shared-match'withTakeover'-and$shared-match'beginOperate') 'shared intent/owner guards missing'
$targets=@('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.2-neoforge','26.2-fabric')
foreach($target in $targets){
    $source=if($target-eq'26.2-fabric'){'client'}else{'main'}
    $runtime=if($target-eq'1.20.1-forge'){'ForgeProbeRuntime'}elseif($target-eq'26.2-fabric'){'FabricProbeRuntime'}else{'NeoForgeProbeRuntime'}
    $directory=Join-Path $root "versions/$target/src/$source/java/io/github/recrivenvi/minecraftprotocol/probe/runtime"
    $transport=Get-Content (Join-Path $directory 'ProbeTransport.java') -Raw
    $state=Get-Content (Join-Path $directory 'ProtocolState.java') -Raw
    $adapter=Get-Content (Join-Path $directory "$runtime.java") -Raw
    Require ($transport-match'attachControlSession'-and$transport-match'/v0/control/mode') "$target mode session is not connected"
    Require ($transport-match'protocolState.requireTakeover'-and$transport-match'protocolState.admitInput') "$target input admission missing"
    Require ($transport-match'protocolState.operate\(service::openAutomationProbeScreen\)') "$target GUI Fixture must use OPERATE, not a Lease"
    Require ($transport-match'singleDebugAuthorization\(metadata, work\)'-and$transport-match'debugBatches.start\(operationId, body, metadata, work\)') "$target Debug admission missing"
    Require ($state-match'inputCleanupBarrier'-and$state-match'STALE_MODE_REVISION'-and$state-match'TAKEOVER_ONLY') "$target transition/latch contract missing"
    $minecraftMixin=Get-Content (Join-Path $directory '../mixin/MinecraftMixin.java') -Raw
    Require ($minecraftMixin-match'method = "close"'-and$minecraftMixin-match'beforeClientClose'-and$adapter-match'runtime_pre_loader_shutdown'-and$adapter-match'shutdownStarted.compareAndSet') "$target must drain before Loader teardown with an idempotent JVM fallback"
    Require ($adapter-match'controlSession.withTakeover\(accepted, supplier\)'-and$adapter-match'onOperatingServer\(') "$target owner-thread enforcement missing"
    Require ($transport-notmatch'storage/write|/v0/storage.write') "$target must not implement Persistent Write"
}
$server=Get-Content (Join-Path $root 'companion/src/server.ts') -Raw
$meta=[regex]::Matches($server,"minecraft/modePolicy").Count
Require ($meta-eq24) 'all 24 MCP tools must declare mode policy'
Require ($server-match'controlToolSchema'-and$server-match"action: z.literal\('set_mode'\)") 'MCP typed mode discriminator missing'
Require ($server-notmatch'state.debugArmId = undefined;[\s\S]{0,60}return result;[\s\S]{0,60}const active') 'Lease release must not impersonate Debug disarm'
[pscustomobject]@{Result='PASS';Targets=5;ClassifiedHttpOperations=$count;ClassifiedMcpTools=$meta;WireProtocolV1='NOT_FROZEN';NewCursorModel='NOT_IMPLEMENTED'}
