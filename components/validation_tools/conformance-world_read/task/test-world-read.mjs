import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { automaticTakeoverRequest, guardAutomaticTakeover } from '../../runtime-connection.mjs';
import { baselineStatistics } from './interaction-baseline-actions.mjs';
import { collectReadQuota } from './consumer-rate-actions.mjs';
import { contextExitEvidence } from './context-exit-evidence.mjs';

const exitBefore={inputGeneration:2,inputContextGeneration:1,inputContextId:'old',inputContextAvailable:true};
const exitAfter={inputGeneration:3,inputContextGeneration:2,inputContextId:'new',inputContextAvailable:false,pressedKeyCount:0,pressedButtonCount:0,inputCleanupPending:false,inputCleanupFailed:false};
const exitTitle={screenClass:'net.minecraft.client.gui.screens.TitleScreen',overlayIdentity:0};
const exitError={status:409,body:{error:'REQUEST_CANCELLED',executionOutcome:'not_confirmed',retrySafe:false}};
assert.equal(contextExitEvidence(exitBefore,exitAfter,exitTitle,exitError).requestOutcome,'not_confirmed');
for(const override of [{inputGeneration:2},{inputContextGeneration:1},{inputContextId:'old'},{inputContextAvailable:true},{pressedKeyCount:1},{pressedButtonCount:1},{inputCleanupPending:true},{inputCleanupFailed:true}])assert.throws(()=>contextExitEvidence(exitBefore,{...exitAfter,...override},exitTitle,exitError));
for(const override of [{screenClass:'PauseScreen'},{overlayIdentity:1}])assert.throws(()=>contextExitEvidence(exitBefore,exitAfter,{...exitTitle,...override},exitError));
for(const error of [{status:500,body:exitError.body},{status:409,body:{...exitError.body,error:'UI_TARGET_INVALIDATED'}},{status:409,body:{...exitError.body,executionOutcome:'completed'}},{status:409,body:{...exitError.body,retrySafe:true}}])assert.throws(()=>contextExitEvidence(exitBefore,exitAfter,exitTitle,error));

for(const first of ['native','mcp']){
 let nativeCalls=0,mcpCalls=0;
 const proof=await collectReadQuota({
  async nativeRead(){const round=Math.floor(nativeCalls++/12);return round>0||first==='native'?{status:429,body:{channel:'native'}}:{status:200};},
  async mcpRead(){const round=mcpCalls++;return round>0||first==='mcp'?{isError:true,structuredContent:{error:{status:429,channel:'mcp'}}}:{isError:false};},
  validate(value,operation){assert.equal(operation,'world');return {channel:value.channel};}
 });
 assert.equal(nativeCalls,24);assert.equal(mcpCalls,2);assert.equal(proof.requests,24);
 assert.deepEqual(proof.nativeQuota,{channel:'native'});assert.deepEqual(proof.mcpQuota,{channel:'mcp'});
}
let quotaNativeCalls=0,quotaMcpCalls=0;
await assert.rejects(collectReadQuota({async nativeRead(){quotaNativeCalls++;return {status:200};},async mcpRead(){quotaMcpCalls++;return {isError:false};},validate(){assert.fail('No rejection was received');}}),/bounded native read burst/);
assert.equal(quotaNativeCalls,192);assert.equal(quotaMcpCalls,16);
await assert.rejects(collectReadQuota({async nativeRead(){return {status:500};},async mcpRead(){assert.fail('Native error must not be ignored');},validate(){assert.fail();}}),{code:'ERR_ASSERTION'});
await assert.rejects(collectReadQuota({async nativeRead(){return {status:200};},async mcpRead(){return {isError:true,structuredContent:{error:{status:403}}};},validate(){assert.fail();}}),{code:'ERR_ASSERTION'});

assert.deepEqual(baselineStatistics([3, 1, 2, 4]), {sampleCount:4,method:'nearest_rank',min:1,p50:2,p95:4,max:4});
assert.equal(baselineStatistics(Array.from({length:20},(_,index)=>index+1)).p95,19);
for(const invalid of [[],[-1],[NaN],[Infinity]])assert.throws(()=>baselineStatistics(invalid));

const controlVersion={controlSessionId:'owned-test-runtime',generation:4};
const automaticRequest={ttlMs:60000,taskId:'own-task'};
const prepared=automaticTakeoverRequest({mode:'READ',reconsentRequired:false,modeVersion:controlVersion},automaticRequest);
assert.deepEqual(prepared,{...automaticRequest,expectedModeVersion:controlVersion});
assert.notEqual(prepared.expectedModeVersion,controlVersion);assert(!('expectedModeVersion'in automaticRequest));
const stale={controlSessionId:'owned-test-runtime',generation:3};
assert.deepEqual(automaticTakeoverRequest({reconsentRequired:false,modeVersion:controlVersion},{expectedModeVersion:stale}).expectedModeVersion,stale);
for(const mode of ['READ','OPERATE','TAKEOVER'])assert.throws(()=>automaticTakeoverRequest({mode,reconsentRequired:true,modeVersion:controlVersion}),error=>error.code==='USER_MANUALLY_ENDED_CONTROL'&&error.reconsentRequired===true&&error.controlState==='MANUALLY_REVOKED');
for(const state of [null,{}, {reconsentRequired:false}, {reconsentRequired:false,modeVersion:{...controlVersion,generation:NaN}}, {modeVersion:controlVersion}])assert.throws(()=>automaticTakeoverRequest(state),error=>error.code==='CONTROL_STATE_UNAVAILABLE');
let automaticState={reconsentRequired:false,modeVersion:controlVersion};const sent=[];
const guardedClient=guardAutomaticTakeover({async json(method,path,body,...options){if(method==='GET'&&path==='/v0/control/mode')return automaticState;sent.push({method,path,body,options});return {ok:true};}});
await guardedClient.json('POST','/v0/control/acquire',{ttlMs:15000},{headers:{test:'preserved'}});
assert.deepEqual(sent[0].body,{ttlMs:15000,expectedModeVersion:controlVersion});assert.deepEqual(sent[0].options,[{headers:{test:'preserved'}}]);
automaticState={reconsentRequired:true,modeVersion:controlVersion};
await assert.rejects(guardedClient.json('POST','/v0/control/acquire',{}),error=>error.code==='USER_MANUALLY_ENDED_CONTROL');assert.equal(sent.length,1,'No acquire is sent after override');
await guardedClient.json('GET','/v0/ui/tree');assert.equal(sent[1].path,'/v0/ui/tree','Observation is not revoked');
for(const file of ['editor-consumers.mjs','spectator-consumers.mjs']){
  const source=await readFile(resolve(import.meta.dirname,'../../conformance-vanilla_ui/task',file),'utf8');
  assert(source.includes('automaticTakeoverRequest(await tool(')&&source.includes('guardAutomaticTakeover(new RuntimeClient(loadConfig(env)))'),'Both MCP and isolated web automatic acquisition must be guarded');
}
const smokeSource=await readFile(resolve(import.meta.dirname,'world-read-smoke.mjs'),'utf8');
assert(smokeSource.includes("body=automaticTakeoverRequest(await api('GET','/v0/control/mode'),body)"));
const consumerSource=await readFile(resolve(import.meta.dirname,'world-read-consumers.mjs'),'utf8');
assert(consumerSource.includes("args=automaticTakeoverRequest(await call('minecraft_control',{action:'status'}),args)"));
assert(consumerSource.includes("automaticTakeoverRequest(mode,{action:'acquire',ttlMs:30000})"));
for(const[file,exitCase]of [['consumer-priority-actions.mjs','consumer_world_exit'],['server-completion-actions.mjs','server_completion_world_exit']]){
 const source=await readFile(resolve(import.meta.dirname,file),'utf8');
 assert(source.includes("import {finishBaselineWorldExit} from './interaction-baseline-actions.mjs'"),file+' must reuse the tested native context exit observer');
 assert(source.includes('const worldExit=await finishBaselineWorldExit({api,quit,'),file+' must observe the one real Save & Quit dispatch');
 assert(source.includes("case:'"+exitCase+"',...worldExit"),file+' must retain the original request outcome with independent exit evidence');
 assert(!source.includes('await clickNode(quit);await wait('),file+' must not require the epoch-invalidated RPC to claim success');
}
const prioritySource=await readFile(resolve(import.meta.dirname,'consumer-priority-actions.mjs'),'utf8');
assert(prioritySource.includes("command:`execute in ${dimension} run tp @s 0.5 80 0.5 0 0`,route:'NATIVE'"),'Dimension Arrange must use the explicit existing native route rather than a cancelled CHAT pipeline');
assert(prioritySource.includes("assert.equal(r.mechanism,'GAME_ROUTED_RAW')"),'The other command consumer flows must keep their actual CHAT path');
assert(prioritySource.includes("case:'consumer_dimension',dimension,before,receipt,player:p,after,released,readyUi"),'Native submission and independent USER/context/cleanup evidence remain separate');
const installedSource=await readFile(resolve(import.meta.dirname,'../../integration-packaged_runtime/task/installed-launcher.mjs'),'utf8');
assert(installedSource.includes('async function acquire(ttlMs=15000)'),'Title-only acquisition keeps its existing TTL default');
assert(installedSource.includes('automaticTakeoverRequest(await status.json(),{ttlMs})'),'Every requested TTL still passes the manual-revocation policy check');
assert(installedSource.includes('const acquired=await acquire(60000)'),'Installed world entry explicitly requests the existing longer TTL rather than reacquiring around expiry');
assert.equal((installedSource.match(/connection\.fetch\('\/v0\/control\/acquire'/g)??[]).length,1,'Every installed-loader acquisition uses the checked helper');

const repo=resolve(import.meta.dirname,'../../../..');
const schema=JSON.parse(await readFile(resolve(repo,'components/protocol_schema/src/main/openapi/minecraft-control-v0.json'),'utf8'));
for(const path of ['/v0/player','/v0/server/player','/v0/world/block','/v0/server/world/block','/v0/world/entities','/v0/server/world/entities']) assert(schema.paths[path].get.parameters.some(value=>value.$ref==='#/components/parameters/WorldReadCategory'),path+' missing actual category contract');
for(const model of ['WorldDataCategory','WorldUserPlayerResponse','WorldReadContract','WorldSceneResponse']) assert(schema.components.schemas[model]);
assert(schema.paths['/v0/world/scene']);
assert.equal(schema.components.schemas.OperationDescriptor.properties.additionalScopesByStep.type,'object');
assert.equal(schema['x-minecraft-websocket'].path,'/v0/events');
assert.equal(schema['x-minecraft-websocket'].continuousWorldState,true);
assert.equal(schema['x-minecraft-websocket'].stateScope,'USER_client_player_pose_only');
assert.deepEqual(schema['x-minecraft-websocket'].stateTypes,['world.player.state']);
assert.equal(schema.components.schemas.OperationResponse.properties.result.properties.timing.$ref,'#/components/schemas/PipelineTiming');
assert.equal(schema.components.schemas.UiNode.properties.slotOwner.enum.join(','),'player_inventory,menu,unknown');
for(const field of ['itemId','itemCount'])assert.deepEqual(schema.paths['/v0/world/entities'].get.responses['200'].content['application/json'].schema.oneOf[0].properties.entities.items.properties[field],schema.components.schemas.WorldSceneResponse.properties.entities.items.properties[field]);
for(const parameter of ['ExpectedScreenRevision','ExpectedMenuRevision'])assert(schema.paths['/v0/pipelines'].post.parameters.some(value=>value.$ref==='#/components/parameters/'+parameter),'Pipeline queued precondition missing from the real OpenAPI surface');
assert.throws(()=>execFileSync(process.execPath,[resolve(import.meta.dirname,'../../regression-v1_release/task/write-alpha-manifest.mjs'),'--scenario=unknown'],{stdio:'pipe'}),'Unknown alpha verification scope must fail before generating output');
assert(schema.components.schemas.PipelineStep.properties.type.enum.includes('world.action'));
assert(schema.components.schemas.PipelineStep.properties.targetRef);
assert.deepEqual(schema.components.schemas.PipelineStep.properties.hand.enum,['main_hand','off_hand']);
assert.deepEqual(schema.components.schemas.WorldUserPlayerResponse.properties.usingHand.enum,['none','main_hand','off_hand']);
assert.equal(schema.components.schemas.WorldUserPlayerResponse.properties.riding.type,'boolean');
for(const entity of [schema.paths['/v0/world/entities'].get.responses['200'].content['application/json'].schema.oneOf[0].properties.entities.items,schema.components.schemas.WorldSceneResponse.properties.entities.items])
  for(const field of ['baby','tamed','sitting','leashed','leashHolderKnown','leashHeldByPlayer'])assert.equal(entity.properties[field].type,'boolean',field+' missing USER entity feedback');
for(const model of ['DeepObservationRequest','ProviderReadRequest','StateFrameRequest','RecordingRequest','AutomationCondition'])assert(schema.components.schemas[model].properties.category,model+' missing read classification');
for(const model of ['PlayerStateResponse','BlockStateResponse','EntityQueryResponse']) {
  const fields=schema.components.schemas[model].allOf[1];assert(fields.required.includes('dataCategory'));assert.equal(fields.properties.dataCategory.enum[0],'DEBUG');
}
assert.equal(schema.components.schemas.WorldReadContract.properties.consumerIsolationComplete.enum[0],false);
for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
  const main=target.endsWith('fabric')?'client':'main';
  const base=resolve(repo,'versions',target,'src',main,'java/io/github/recrivenvi/minecraftprotocol/probe/runtime');
  const transport=await readFile(resolve(repo,'components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core/ProbeTransport.java'),'utf8');
  assert(transport.includes('new WorldReadEngine(service, this.protocolState)'),target+' missing production boundary');
  assert.equal((transport.match(/\n\s*worldRead\(/g)??[]).length,6,target+' basic route bypass');
  const adapter=await readFile(resolve(base,target.endsWith('fabric')?'FabricProbeRuntime.java':target.startsWith('1.20.1')?'ForgeProbeRuntime.java':'NeoForgeProbeRuntime.java'),'utf8');
  assert(adapter.includes('attachPlayerCommandGate')&&adapter.includes('InputDispatchContext.capture()')&&adapter.includes('commandGate.enter(accepted, permit)')&&adapter.includes('dispatch.receipt()'),target+' missing active input-origin and guarded submission receipt');
  assert(adapter.includes('permit.beforeInput(this::checkInputPreconditions)')&&adapter.includes('this.onControlledClient(supplier, true)')&&adapter.includes('worldTargeting.progress(ref, guard, released), false'),target+' missing first-event original revision binding or a read consumes it early');
  const ownerCheck=adapter.match(/private void checkInputPreconditions\(Long expectedScreenRevision, Long expectedMenuRevision\)\s*\{([\s\S]*?)\n    \}/)?.[1];
  assert(ownerCheck?.includes('this.refreshScreen(client)')&&ownerCheck.includes('this.refreshMenu(client)')&&ownerCheck.includes('expectedScreenRevision != this.screenRevision')&&ownerCheck.includes('expectedMenuRevision != this.menuRevision'),target+' final check must refresh and compare the exact caller values on the native owner thread');
  assert(adapter.replace(/\s/g,'').includes('ProbeTransport.forInstance(this,minecraft.gameDirectory.toPath(),"'+target+'")'),target+' shared transport instance identity not bound');
  assert(adapter.includes('WorldClientSnapshot.capture(kind, detached, this.worldTargeting)'),target+' missing client world adapter');
  const targeting=await readFile(resolve(base,'WorldTargeting.java'),'utf8');
  assert(targeting.includes('WorldTargetRegistry<Target>'),target+' missing bounded target registry');
  assert(targeting.includes('WorldAim.delta')&&targeting.includes('minecraftProtocolProbe$pick'),target+' missing native aim/pick');
  assert(targeting.includes('target.state.getShape')&&targeting.includes('ClipContext.Block.OUTLINE')&&targeting.includes('shape.forAllBoxes')&&targeting.includes('points.size() < 64'),target+' missing bounded native shape targeting');
  assert(targeting.includes('chunk != target.chunk.get()')&&targeting.includes('chunk.getBlockState(target.block) != target.state'),target+' missing chunk-instance or current block-state check');
  assert(targeting.includes('BLOCK_CHANGES.snapshot()')&&targeting.includes('BLOCK_CHANGES.changed(chunk, pos.asLong())')&&targeting.includes('BLOCK_CHANGES.unchanged(chunk, target.block.asLong(), target.revision)'),target+' missing position-specific bounded native change history');
  assert(!targeting.includes('revision(chunk) != target.revision'),target+' unrelated block changes still invalidate all targets in a Chunk');
  assert(targeting.includes('CHANGES.get(chunk) + 1')&&targeting.includes('revision(current.target.chunk.get())')&&targeting.includes('chunk.getBlockState(position), revision(chunk)'),target+' native destroy trace lost whole-chunk change accounting');
  assert(targeting.includes('mapping')&&targeting.includes('keyAttack')&&targeting.includes('keyUse'),target+' hardcoded input binding');
  assert(!/setYRot|setXRot|setPos|SingleplayerServer|ServerLevel|readNbt/.test(targeting),target+' semantic action bypasses USER/native boundary');
  assert(adapter.includes('worldAimDelta')&&adapter.includes('worldActionFrame'),target+' missing guarded owner-thread path');
  assert(adapter.includes('worldActionProgress')&&adapter.includes('worldActionEnd')&&adapter.includes('worldTargeting.end()'),target+' missing held gesture lifecycle');
  assert(targeting.includes('BlockPlaceContext')&&targeting.includes('expectedDestinationBlockId')&&targeting.includes('WORLD_PLACE_NOT_DISPATCHED'),target+' missing native placement validation');
  assert(targeting.includes('beforeDestroy')&&targeting.includes('afterDestroy')&&targeting.includes('WorldBreakTrace')&&targeting.includes('DESTROY.remove()')&&targeting.includes('getFluidState(position).createLegacyBlock()'),target+' missing bounded fluid/self-break provenance');
  assert(targeting.includes('WORLD_USE_HAND_CONFLICT')&&targeting.includes('getItemInHand(useHand(options))')&&targeting.includes('getUsedItemHand() != useHand(current.options)'),target+' missing native offhand precondition/feedback');
  const hooks=resolve(base,'../mixin');
  const commandHook=await readFile(resolve(hooks,'PlayerCommandMixin.java'),'utf8');
  const packetHook=await readFile(resolve(hooks,'ConnectionMixin.java'),'utf8');
  assert(commandHook.includes('sendCommand')&&commandHook.includes('allowPlayerCommand')&&commandHook.includes('cancellable = true'),target+' native command entry missing');
  assert(packetHook.includes('allowPlayerCommandPacket')&&packetHook.includes('cancellable = true'),target+' final command packet guard missing');
  assert(adapter.includes('ServerboundChatCommandPacket')&&adapter.includes('NativePlayerCommands.configuration(packet)')&&adapter.includes('gate.allowConfiguration('),target+' native chat/configuration classification missing');
  const parser=await readFile(resolve(base,'NativePlayerCommands.java'),'utf8');
  for(const packet of ['ServerboundSetCommandBlockPacket','ServerboundSetCommandMinecartPacket','ServerboundSetJigsawBlockPacket','ServerboundJigsawGeneratePacket','ServerboundSetStructureBlockPacket'])assert(parser.includes(packet),target+' missing typed native configuration packet '+packet);
  for(const [native,kind] of [['UPDATE_DATA','STRUCTURE_UPDATE'],['SCAN_AREA','STRUCTURE_SCAN'],['SAVE_AREA','STRUCTURE_SAVE'],['LOAD_AREA','STRUCTURE_LOAD']])assert(parser.replace(/\s/g,'').includes('case'+native+'->PlayerCommandGate.ConfigurationKind.'+kind),target+' structure actions must remain typed and distinct');
  assert(adapter.includes('"command.player.structure_configuration"')&&adapter.includes('"native_parameter_update_and_scan"')&&adapter.includes('"command.player.structure_template"')&&adapter.includes('"unavailable_storage_or_unresolved_template_effects"'),target+' missing production structure capability policy');
  assert(adapter.replace(/\s/g,'').includes('NativePlayerCommands.parseConfiguration(client,configuration,command)')&&parser.includes('jigsaw.getFinalState()'),target+' native Jigsaw packet lost its typed block-state boundary');
  assert(parser.includes('var dispatcher = connection.getCommands()')&&parser.includes('dispatcher.parse(command, connection.getSuggestionsProvider())')&&parser.includes('context.getRootNode()')&&parser.includes('context.getChild()')&&parser.includes('getRedirect()'),target+' actual native alias/nested command-tree classification missing');
  const payload=await readFile(resolve(base,'NativeCommandPayloads.java'),'utf8');
  assert(parser.includes('payloads.capture(context, root.getName())')&&parser.includes('dispatcher.parse(command, parsed.getContext().getSource())')&&payload.includes('context.getArguments()')&&payload.includes('instanceof Tag')&&payload.includes('instanceof BlockInput')&&payload.includes('instanceof ItemInput'),target+' actual native decoded payload/current-tree inspection missing');
  const guard=await readFile(resolve(hooks,'WorldGestureMixin.java'),'utf8');
  assert(guard.includes('MultiPlayerGameMode.class')&&guard.includes('allowSemanticWorldInput'),target+' missing production held guard');
  const revision=await readFile(resolve(hooks,'WorldChunkRevisionMixin.java'),'utf8');
  assert(revision.includes('@At("RETURN")')&&revision.includes('cir.getReturnValue()'),target+' unchanged Vanilla block echo must not invalidate target');
  assert(revision.includes('method = "replaceWithPacketData", at = @At("HEAD")')&&revision.includes('WorldTargeting.chunkReplaced(this)'),target+' full native snapshots must invalidate before decoding');
  assert(targeting.includes('public static synchronized void chunkReplaced(Object chunk)')&&targeting.includes('BLOCK_CHANGES.replaced(chunk)'),target+' missing production bulk-snapshot adapter');
  const probe=await readFile(resolve(repo,'versions',target,'src/probe/java/io/github/recrivenvi/minecraftprotocol/probe/validation/ChunkPacketReplay.java'),'utf8');
  assert(probe.includes('getChunkNow(0, 0)')&&probe.includes('ChunkStatus.FULL, false')&&probe.includes('new ClientboundLevelChunkWithLightPacket')&&probe.includes('sameNativeCachedChunk')&&probe.includes('nativePositionUpdates'),target+' actual cached-Chunk replay proof missing');
  assert(!probe.includes('WorldTargeting.')&&!/\.setBlock\(/.test(probe),target+' diagnostic packet replay must not mutate the world or invoke the target guard directly');
  assert(!/mouseDelta\\([^;]*\\)\\.join\\(\\)/.test(adapter),target+' client-thread self-wait');
  const clientModel=await readFile(resolve(base,'WorldClientSnapshot.java'),'utf8');
  assert(clientModel.includes('WorldClientModel.capture'),target+' missing shared budgets');
  assert(clientModel.includes('"usingHand"')&&clientModel.includes('player.isPassenger()'),target+' missing USER hand/ride client feedback');
  assert(clientModel.includes('living.isBaby()')&&clientModel.includes('animal.isTame()')&&clientModel.includes('animal.isInSittingPose()')&&clientModel.includes('leashable.isLeashed()')&&clientModel.includes('leashable.getLeashHolder()')&&clientModel.includes('holder == client.player'),target+' missing client-only entity interaction feedback');
  assert(!/getOwner|isInLove|getInLove|breedCooldown/.test(clientModel),target+' entity feedback must not inspect owner or breeding internals');
  assert(clientModel.indexOf('leashable.getLeashHolder()')<clientModel.indexOf('"leashed"'),target+' resolve delayed cached holder before sampling native leash state');
  assert(clientModel.replace(/\s/g,'').includes('ChunkStatus.FULL,false'),target+' cached chunk read must not load');
  assert(!/serverLevel|SingleplayerServer|ServerLevel|readNbt|playerdata/.test(clientModel),target+' USER adapter reaches server or storage');
  assert((await readFile(resolve(base,'WorldPresentationSnapshot.java'),'utf8')).includes('WorldPresentation.Context'),target+' missing bound native frame');
  for(const test of ['WorldReadEngineTest','WorldReadTransportTest']) assert((await readFile(resolve(repo,'components/runtime_core/src/test/java/io/github/recrivenvi/minecraftprotocol/core',test+'.java'),'utf8')).includes('class '+test));
}
for(const file of ['world-read-smoke.mjs','mode-collaboration-actions.mjs','resource-version-actions.mjs','command-discovery-batch-actions.mjs','server-completion-actions.mjs','world-movement-actions.mjs','world-block-state-actions.mjs','input-precondition-smoke.mjs','player-command-smoke.mjs','target-lifecycle-smoke.mjs','command-configuration-smoke.mjs','test-management-smoke.mjs','world-read-consumers.mjs','world-held-actions.mjs','world-entity-actions.mjs','world-ui-actions.mjs','../../regression-v1_release/task/write-alpha-manifest.mjs'])execFileSync(process.execPath,['--check',resolve(import.meta.dirname,file)]);
execFileSync(process.execPath,['--test',resolve(import.meta.dirname,'world-ui-fixture.test.mjs')],{stdio:'inherit'});
for(const name of ['Invoke-WorldReadSmoke.ps1','Test-WorldReadArtifact.ps1','WorldReadFixture.ps1','Test-WorldReadFixture.ps1'])execFileSync('pwsh',['-NoProfile','-Command',`$errors=$null;[void][System.Management.Automation.Language.Parser]::ParseFile('components/validation_tools/conformance-world_read/task/${name}',[ref]$null,[ref]$errors);if($errors.Count){throw ($errors|Out-String)}`],{cwd:repo,stdio:'pipe'});
execFileSync('pwsh',['-NoProfile','-File',resolve(import.meta.dirname,'Test-RuntimeConnection.ps1')],{cwd:repo,stdio:'inherit'});
execFileSync('pwsh',['-NoProfile','-File',resolve(import.meta.dirname,'Test-WorldReadFixture.ps1')],{cwd:repo,stdio:'inherit'});
console.log('PASS: categorized world read consumers, five native scene adapters and executable smoke syntax; actual HTTP permission/lifecycle tests run in runtime_core check');
