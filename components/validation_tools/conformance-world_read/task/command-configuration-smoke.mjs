import assert from 'node:assert/strict';
import {readFile,readdir,writeFile,mkdir} from 'node:fs/promises';
import {resolve,relative} from 'node:path';
import {createHash,randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {connectRuntime} from '../../runtime-connection.mjs';
import {commandOperationConsumer} from './world-read-consumers.mjs';
import {contextExitEvidence} from './context-exit-evidence.mjs';

const repo=resolve(import.meta.dirname,'../../../..');
const args=Object.fromEntries(process.argv.slice(2).map(arg=>arg.replace(/^--/,'').split('=')));
const target=args.target,validation='conformance-world_read';
assert(['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric'].includes(target));
const instance=resolve(repo,'validations',validation,'instance',target,'client');
const fixtureInput=JSON.parse(await readFile(resolve(instance,'fixture-input.json'),'utf8'));
assert.equal(fixtureInput.policy,'immutable_baseline_restored');assert.equal(fixtureInput.target,target);
assert.equal(fixtureInput.baselineSha256,args['fixture-hash']);assert.equal(fixtureInput.restoredSha256,args['fixture-hash']);
const connection=await connectRuntime(instance,target),checks=[],actionSamples=[],consumerChecks=[];
let lease,recording,failure,shutdownResponse='not_requested';
async function sources(){
 const files=[];
 for(const root of [`versions/${target}/src`,'components/runtime_core/src/main','components/runtime_safety/src/main','components/companion/src'])
  for(const entry of await readdir(resolve(repo,root),{recursive:true,withFileTypes:true}))if(entry.isFile())files.push(relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/'));
 for(const entry of await readdir(import.meta.dirname,{withFileTypes:true}))if(entry.isFile()&&/\.(mjs|ps1)$/.test(entry.name))files.push(relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/'));
 files.push('components/protocol_schema/src/main/openapi/minecraft-control-v0.json','components/validation_tools/InstanceProfiles.ps1','components/validation_tools/Prepare-InstanceProfile.ps1','components/validation_tools/instance-profiles.gradle','build.gradle.kts','components/validation_tools/RuntimeConnection.ps1','components/validation_tools/runtime-connection.mjs',`validations/${validation}/validation.toml`,'gradle.properties','settings.gradle.kts','gradle/libs.versions.toml',`versions/${target}/build.gradle.kts`,`versions/${target}/target.properties`);
 const hashes={};for(const file of [...new Set(files)].sort())hashes[file]=createHash('sha256').update(await readFile(resolve(repo,file))).digest('hex');return hashes;
}
const before=await sources(),digest=text=>createHash('sha256').update(text).digest('hex');
async function api(method,path,body){
 const response=await connection.fetch(path,{method,headers:{...(lease?{'x-mcp-control-lease':lease}:{}),...(body?{'content-type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(30000)});
 const value=await response.json();if(!response.ok)throw Object.assign(new Error(JSON.stringify(value)),{code:value.error,status:response.status,runtimeError:value});return value;
}
const nodes=t=>(t.children??[]).flatMap(n=>[n,...nodes(n)]),pause=()=>new Promise(r=>setTimeout(r,50));
async function poll(read,accept,label,budget=10000){const end=Date.now()+budget;let value;do{value=await read();if(accept(value))return value;await pause();}while(Date.now()<end);throw new Error(label+': '+JSON.stringify(value));}
async function screen(accept,label,budget){return poll(()=>api('GET','/v0/ui/tree'),t=>!t.overlayIdentity&&accept(t),label,budget);}
async function click(key){const tree=await api('GET','/v0/ui/tree'),found=nodes(tree).filter(n=>n.translationKey===key&&n.visible!==false&&n.active!==false);assert.equal(found.length,1,key);return api('POST','/v0/ui/action',{selector:{nodeId:found[0].nodeId},action:'click'});}
async function key(code){const op=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:code,holdMs:40}],timeoutMs:10000});const out=await api('POST',`/v0/operations/${op.operationId}/wait`,{timeoutMs:12000});assert.equal(out.state,'completed',JSON.stringify(out));}
async function acquire(){assert.equal((await api('GET','/v0/control/mode')).reconsentRequired,false,'Never clear human override');lease=(await api('POST','/v0/control/acquire',{ttlMs:60000})).leaseId;}
async function clean(){const state=await api('GET','/v0/input/state');assert.equal(state.pressedKeyCount,0);assert.equal(state.pressedButtonCount,0);return state;}
async function command(text){await api('POST','/v0/control/renew',{ttlMs:60000});const out=await api('POST','/v0/command/player',{command:text});assert.equal(out.mechanism,'GAME_ROUTED_RAW');assert.equal(out.effectConfirmed,false);return out;}
async function barrier(){
 const attempt=randomUUID().replaceAll('-','');await command('say Mine-Craft-Protocol_command_configuration_'+attempt);
 return poll(async()=>{try{return JSON.parse(await readFile(resolve(repo,'validations',validation,'result',target+'-command-configuration-probe.json'),'utf8'));}catch(error){if(error.code==='ENOENT'||error instanceof SyntaxError)return {};throw error;}},r=>r.attemptId===attempt,'native server FIFO barrier');
}
async function worldAct(ref){const op=await api('POST','/v0/pipelines',{steps:[{type:'world.action',action:'use',targetRef:ref}],timeoutMs:10000});const out=await api('POST',`/v0/operations/${op.operationId}/wait`,{timeoutMs:12000});assert.equal(out.state,'completed',JSON.stringify(out));}
async function open(kind){
 await screen(t=>!t.screenClass,'gameplay before opening editor');
 if(kind==='COMMAND_BLOCK'||kind==='JIGSAW_BLOCK'||kind.startsWith('STRUCTURE_')){const block=await api('GET','/v0/world/block?x=2&y=80&z=0');assert.equal(block.block,kind==='COMMAND_BLOCK'?'minecraft:command_block':kind==='JIGSAW_BLOCK'?'minecraft:jigsaw':'minecraft:structure_block');await worldAct(block.targetRef);}
 else {const snapshot=await api('GET','/v0/world/entities?radius=8&limit=32');const entities=snapshot.entities??[];const found=entities.filter(e=>e.type==='minecraft:command_block_minecart'||e.entityType==='minecraft:command_block_minecart');assert.equal(found.length,1,JSON.stringify(snapshot));await worldAct(found[0].targetRef);}
 await screen(t=>t.screenClass.endsWith(kind==='COMMAND_BLOCK'?'CommandBlockEditScreen':kind==='JIGSAW_BLOCK'?'JigsawBlockEditScreen':kind.startsWith('STRUCTURE_')?'StructureBlockEditScreen':'MinecartCommandBlockEditScreen'),'native command editor');
}
async function field(text,translationKey='advMode.command'){const tree=await api('GET','/v0/ui/tree');const fields=nodes(tree).filter(n=>n.role==='text_field'&&n.translationKey===translationKey&&n.visible!==false&&n.active!==false&&n.actions?.includes('type_text'));assert.equal(fields.length,1,JSON.stringify(fields));await api('POST','/v0/ui/action',{selector:{nodeId:fields[0].nodeId},action:'type_text',text});await screen(t=>nodes(t).some(n=>n.translationKey===translationKey&&n.value===text),'native command draft equals requested text');}
async function done(translationKey='gui.done'){const tree=await api('GET','/v0/ui/tree');const button=nodes(tree).filter(n=>n.translationKey===translationKey&&n.active!==false&&n.visible!==false);assert.equal(button.length,1,translationKey);const op=await api('POST','/v0/pipelines',{steps:[{type:'ui.action',selector:{nodeId:button[0].nodeId},action:'click'}],timeoutMs:10000});return api('POST',`/v0/operations/${op.operationId}/wait`,{timeoutMs:12000});}
async function check(name,run){await run();checks.push({name,status:'PASS'});console.log('PASS: '+name);}
async function consume(sample){
 // Terminal operations are bounded, not durable IDs. Read them before subsequent GUI operations can evict them.
 await api('POST','/v0/control/release',{});lease=undefined;
 assert.equal((await api('GET','/v0/control/mode')).mode,'READ');const before=await clean();
 const partial=sample.case==='configuration_partial_failure';
 const parameter=sample.kind.startsWith('STRUCTURE_');
 const checked=await commandOperationConsumer(repo,instance,target,sample.result.operationId,{state:partial?'failed':'completed',partial,contaminated:true,effect:'STATE_MUTATION',scenario:'configuration_'+sample.kind+(partial?'_partial':''),configurationKind:sample.kind,configurationPayloadType:parameter?'STRUCTURE_PARAMETERS':sample.kind==='JIGSAW_BLOCK'?'BLOCK_STATE':'COMMAND',configuredCommandEffect:parameter||sample.kind==='JIGSAW_BLOCK'?'STATE_MUTATION':'INFORMATION'});
 const after=await clean();assert.equal(after.inputDispatchSequence,before.inputDispatchSequence);assert.equal((await api('GET','/v0/control/mode')).mode,'READ');consumerChecks.push({...checked,operationId:sample.result.operationId,inputUnchanged:true});await acquire();
}
try{
 await acquire();
 await check('semantic GUI enters immutable world and bounded native fixture remains diagnostic',async()=>{
  const capability=await api('GET','/v0/capabilities');assert.equal(capability.capabilities['command.player.configuration'],'native_command_blocks_and_jigsaw_final_state');assert.equal(capability.capabilities['command.player.jigsaw_generation'],'unavailable_unresolved_template_effects');assert.equal(capability.capabilities['command.player.structure_configuration'],'native_parameter_update_and_scan');assert.equal(capability.capabilities['command.player.structure_template'],'unavailable_storage_or_unresolved_template_effects');
  await click('menu.singleplayer');const tree=await screen(t=>t.screenClass.endsWith('SelectWorldScreen')&&nodes(t).some(n=>n.worldName==='World-Read-Alpha'),'world list populated');const world=nodes(tree).filter(n=>n.worldName==='World-Read-Alpha');assert.equal(world.length,1);await api('POST','/v0/ui/action',{selector:{nodeId:world[0].nodeId},action:'click'});await click('selectWorld.select');await screen(t=>!t.screenClass,'fixed world',150000);
  await command('gamemode creative @s');await command('clear @s');await command('fill -1 79 -1 4 79 2 minecraft:stone');await command('fill -1 80 -1 4 83 2 minecraft:air');await command('tp @s 0.5 80 0.5');await command('setblock 2 80 0 minecraft:command_block');
  await poll(()=>api('GET','/v0/world/block?x=2&y=80&z=0'),b=>b.block==='minecraft:command_block','fixture client sync');await clean();
 });
 recording=await api('POST','/v0/recordings',{captureFrames:false,intervalMs:750,durationMs:300000,maxSamples:500,stateReads:[{providerId:'minecraft:client/player'}],contactSheet:{enabled:false}});
 await poll(()=>api('GET','/v0/recordings/'+recording.recordingId),r=>r.writtenStates>0,'real USER recording sample');
 for(const kind of ['COMMAND_BLOCK','COMMAND_MINECART']){
  if(kind==='COMMAND_MINECART'){
   await command('setblock 2 80 0 minecraft:air');await command('kill @e[type=minecraft:command_block_minecart,distance=..8]');await command('summon minecraft:command_block_minecart 2.5 80 0.5');
   await poll(()=>api('GET','/v0/world/entities?radius=8&limit=32'),r=>(r.entities??[]).some(e=>e.type==='minecraft:command_block_minecart'||e.entityType==='minecraft:command_block_minecart'),'minecart reaches client');
  }
  await check(kind+' rejects host and unresolved programs before the server sees a configuration',async()=>{
   const initial=await barrier();
   for(const [text,code] of [['reload','PLAYER_COMMAND_OUT_OF_SCOPE'],['function minecraft:missing','PLAYER_COMMAND_UNCLASSIFIED']]){
    await open(kind);await field(text);const result=await done();assert.equal(result.state,'failed');assert.equal(result.errorCode,code,JSON.stringify(result));await screen(t=>!t.screenClass,'native Done closes even when packet is denied');await clean();
    const after=await barrier();assert.equal(after.commandBlockPackets,initial.commandBlockPackets);assert.equal(after.commandMinecartPackets,initial.commandMinecartPackets);assert.equal(after.worldMutationUsedByProbe,false);assert.equal(after.chunkLoadRequested,false);actionSamples.push({case:'configuration_rejected',kind,code,result,initial,after});
   }
   if(kind==='COMMAND_BLOCK')assert.equal((await api('GET','/v0/recordings/'+recording.recordingId)).evidenceContaminated,false,'denials are not submissions');
  });
  await check(kind+' submits query configuration with diagnostic unconfirmed provenance and native stored value',async()=>{
   const initial=await barrier();await open(kind);const text='/time query gametime';await field(text);const result=await done();assert.equal(result.state,'completed',JSON.stringify(result));
   const receipts=result.result.commandSubmissions;assert.equal(receipts.length,1,JSON.stringify(result));const receipt=receipts[0];assert.equal(receipt.configurationKind,kind);assert.equal(receipt.configuredCommandEffect,'INFORMATION');assert.equal(receipt.effectClassification,'STATE_MUTATION');assert.equal(receipt.classificationSource,'native_player_command_configuration');assert.equal(receipt.contaminationReason,'player_command_configuration');assert.equal(receipt.effectConfirmed,false);assert.equal(receipt.gameplayAcceptanceEligible,false);assert.equal(result.result.evidenceAuthority,'DEBUG_PRIVILEGED');assert.equal(result.result.evidenceContaminated,true);
   await screen(t=>!t.screenClass,'configured screen closed');const after=await barrier();const counter=kind==='COMMAND_BLOCK'?'commandBlockPackets':'commandMinecartPackets',stored=kind==='COMMAND_BLOCK'?'storedBlockCommandHash':'storedMinecartCommandHash';assert.equal(after[counter],initial[counter]+1);assert.equal(after[stored],digest(text));await clean();const sample={case:'configuration_submitted',kind,result,initial,after,assertAuthority:'DEBUG_probe_native_server_readback'};actionSamples.push(sample);await consume(sample);
  });
  await check(kind+' supports native clearing without interpreting it as an executed empty command',async()=>{
   const initial=await barrier();await open(kind);await field('');const result=await done();assert.equal(result.state,'completed',JSON.stringify(result));assert.equal(result.result.commandSubmissions[0].configuredCommandEffect,'NONE');await screen(t=>!t.screenClass,'cleared editor closed');const after=await barrier();const counter=kind==='COMMAND_BLOCK'?'commandBlockPackets':'commandMinecartPackets',stored=kind==='COMMAND_BLOCK'?'storedBlockCommandHash':'storedMinecartCommandHash';assert.equal(after[counter],initial[counter]+1);assert.equal(after[stored],digest(''));await clean();
  });
 }
 await command('setblock 2 80 0 minecraft:jigsaw');
 await poll(()=>api('GET','/v0/world/block?x=2&y=80&z=0'),b=>b.block==='minecraft:jigsaw','Jigsaw client sync');
 await check('Jigsaw final state rejects nested host and unresolved programs before the server receives configuration',async()=>{
  const initial=await barrier();
  for(const [text,code]of [['minecraft:command_block{Command:"reload"}','PLAYER_COMMAND_OUT_OF_SCOPE'],['minecraft:command_block{Command:"unknown_inner_program"}','PLAYER_COMMAND_UNCLASSIFIED']]){
   await open('JIGSAW_BLOCK');await field(text,'jigsaw_block.final_state');const result=await done();assert.equal(result.state,'failed');assert.equal(result.errorCode,code,JSON.stringify(result));await screen(t=>!t.screenClass,'Jigsaw denied Done closes native editor');await clean();const after=await barrier();assert.equal(after.jigsawConfigurationPackets,initial.jigsawConfigurationPackets);assert.equal(after.jigsawGenerationPackets,initial.jigsawGenerationPackets);actionSamples.push({case:'jigsaw_rejected',code,result,initial,after});
  }
 });
 await check('Jigsaw safe final-state configuration is diagnostic and independently stored without generation',async()=>{
  const initial=await barrier(),text='minecraft:stone';await open('JIGSAW_BLOCK');await field(text,'jigsaw_block.final_state');const result=await done();assert.equal(result.state,'completed',JSON.stringify(result));const receipt=result.result.commandSubmissions[0];assert.equal(receipt.configurationKind,'JIGSAW_BLOCK');assert.equal(receipt.configurationPayloadType,'BLOCK_STATE');assert.equal(receipt.configuredCommandEffect,'STATE_MUTATION');assert.equal(receipt.evidenceContaminated,true);assert.equal(receipt.effectConfirmed,false);await screen(t=>!t.screenClass,'Jigsaw saved screen closed');const after=await barrier();assert.equal(after.jigsawConfigurationPackets,initial.jigsawConfigurationPackets+1);assert.equal(after.jigsawGenerationPackets,initial.jigsawGenerationPackets);assert.equal(after.storedJigsawFinalStateHash,digest(text));await clean();const sample={case:'configuration_submitted',kind:'JIGSAW_BLOCK',result,initial,after,assertAuthority:'DEBUG_probe_native_server_readback'};actionSamples.push(sample);await consume(sample);
 });
 await check('Jigsaw Generate retains its submitted configuration but refuses opaque generation with partial provenance',async()=>{
  const initial=await barrier(),text='minecraft:air';await open('JIGSAW_BLOCK');await field(text,'jigsaw_block.final_state');const result=await done('jigsaw_block.generate');assert.equal(result.state,'failed');assert.equal(result.errorCode,'PLAYER_COMMAND_UNCLASSIFIED',JSON.stringify(result));assert.equal(result.result.partial,true);assert.equal(result.result.evidenceContaminated,true);assert.equal(result.result.commandSubmissions.length,1);assert.equal(result.result.commandSubmissions[0].configurationKind,'JIGSAW_BLOCK');assert.equal(result.result.commandSubmissions[0].configurationPayloadType,'BLOCK_STATE');await screen(t=>!t.screenClass,'native Generate closes editor');const after=await barrier();assert.equal(after.jigsawConfigurationPackets,initial.jigsawConfigurationPackets+1);assert.equal(after.jigsawGenerationPackets,initial.jigsawGenerationPackets);assert.equal(after.storedJigsawFinalStateHash,digest(text));await clean();const sample={case:'configuration_partial_failure',kind:'JIGSAW_BLOCK',result,initial,after,assertAuthority:'DEBUG_probe_native_server_readback'};actionSamples.push(sample);await consume(sample);
 });
 await command('setblock 2 80 0 minecraft:structure_block[mode=save]{mode:"SAVE",name:"minecraft:before",posY:1,sizeX:1,sizeY:1,sizeZ:1}');
 await poll(()=>api('GET','/v0/world/block?x=2&y=80&z=0'),b=>b.block==='minecraft:structure_block','Structure client sync');
 await check('Structure Done updates parameters with diagnostic provenance and independent native readback',async()=>{
  const initial=await barrier();await open('STRUCTURE_UPDATE');await field('minecraft:validation_structure','structure_block.structure_name');const result=await done();assert.equal(result.state,'completed',JSON.stringify(result));await screen(t=>!t.screenClass,'Structure Done closes editor');const after=await barrier();assert.equal(after.structureUpdatePackets,initial.structureUpdatePackets+1);assert.equal(after.structureScanPackets,initial.structureScanPackets);assert.equal(after.structureSavePackets,initial.structureSavePackets);assert.equal(after.structureLoadPackets,initial.structureLoadPackets);assert.equal(after.storedStructureNameHash,digest('minecraft:validation_structure'));assert.equal(after.storedStructureMode,'SAVE');const receipt=result.result.commandSubmissions[0];assert.equal(receipt.configurationKind,'STRUCTURE_UPDATE');assert.equal(receipt.configurationPayloadType,'STRUCTURE_PARAMETERS');assert.equal(receipt.configuredCommandEffect,'STATE_MUTATION');await clean();const sample={case:'configuration_submitted',kind:'STRUCTURE_UPDATE',result,initial,after,assertAuthority:'DEBUG_probe_native_server_readback'};actionSamples.push(sample);await consume(sample);
 });
 await command('setblock 1 79 1 minecraft:structure_block[mode=corner]{mode:"CORNER",name:"minecraft:validation_structure"}');
 await command('setblock 5 83 5 minecraft:structure_block[mode=corner]{mode:"CORNER",name:"minecraft:validation_structure"}');
 await check('Structure native Detect Size scans corners and stores actual offset/size without template saving',async()=>{
  const initial=await barrier();await open('STRUCTURE_SCAN');const result=await done('structure_block.button.detect_size');assert.equal(result.state,'completed',JSON.stringify(result));await screen(t=>!t.screenClass,'Structure Detect closes editor');const after=await barrier();assert.equal(after.structureScanPackets,initial.structureScanPackets+1);assert.equal(after.structureUpdatePackets,initial.structureUpdatePackets);assert.equal(after.structureSavePackets,initial.structureSavePackets);assert.equal(after.structureLoadPackets,initial.structureLoadPackets);assert.equal(after.storedStructureOffset,'0,0,2');assert.equal(after.storedStructureSize,'3,3,3');await clean();const sample={case:'configuration_submitted',kind:'STRUCTURE_SCAN',result,initial,after,assertAuthority:'DEBUG_probe_native_server_readback'};actionSamples.push(sample);await consume(sample);
 });
 for(const [kind,mode,button,code] of [['STRUCTURE_SAVE','SAVE','structure_block.button.save','PLAYER_COMMAND_OUT_OF_SCOPE'],['STRUCTURE_LOAD','LOAD','structure_block.button.load','PLAYER_COMMAND_UNCLASSIFIED']]){
  if(mode==='LOAD'){await command('setblock 2 80 0 minecraft:air');await command('setblock 2 80 0 minecraft:structure_block[mode=load]{mode:"LOAD",name:"minecraft:validation_structure"}');await poll(()=>api('GET','/v0/world/block?x=2&y=80&z=0'),b=>b.block==='minecraft:structure_block','Structure load fixture client sync');}
  await check(kind+' fails before the native server handler or any parameter change',async()=>{
   const initial=await barrier();await open(kind);await field('minecraft:must_not_be_saved','structure_block.structure_name');const result=await done(button);assert.equal(result.state,'failed');assert.equal(result.errorCode,code,JSON.stringify(result));assert.equal(result.result?.commandSubmissions?.length??0,0);await screen(t=>!t.screenClass,'Structure rejected button closes editor');const after=await barrier();for(const field of ['structureUpdatePackets','structureScanPackets','structureSavePackets','structureLoadPackets','storedStructureNameHash','storedStructureMode','storedStructureOffset','storedStructureSize'])assert.equal(after[field],initial[field],field+' changed on rejected action');await clean();actionSamples.push({case:'structure_rejected',kind,code,result,initial,after});
  });
 }
 await check('USER Artifact preserves eight configuration attempts and submissions without claiming execution',async()=>{
  await api('DELETE','/v0/recordings/'+recording.recordingId);const final=await poll(()=>api('GET','/v0/recordings/'+recording.recordingId),r=>r.artifactReady,'configuration Artifact',30000);assert.equal(final.evidenceContaminated,true);
  const response=await connection.fetch('/v0/recordings/'+recording.recordingId+'/artifact',{signal:AbortSignal.timeout(25000)});assert(response.ok);const artifact=resolve(instance,'command-configuration-evidence.zip');await writeFile(artifact,new Uint8Array(await response.arrayBuffer()));
  const checked=JSON.parse(execFileSync('pwsh',['-NoProfile','-File',resolve(import.meta.dirname,'Test-WorldReadArtifact.ps1'),'-Artifact',artifact,'-RequireConfigurationContamination','-ExpectedConfigurations','8'],{cwd:repo,encoding:'utf8'}));assert.equal(checked.status,'PASS');assert.equal(checked.configurationSubmissionEvents,8);actionSamples.push({case:'configuration_artifact',artifact:relative(repo,artifact),checked});recording=undefined;
 });
 await check('real MCP preserves five configuration kinds and partial failure under READ without acquiring a Lease',async()=>{
  assert.equal(consumerChecks.length,6);assert(consumerChecks.every(c=>c.status==='PASS'&&c.inputUnchanged));assert.equal(new Set(consumerChecks.map(c=>c.operationId)).size,6);await clean();
 });
 await command('kill @e[type=minecraft:command_block_minecart,distance=..8]');await command('setblock 2 80 0 minecraft:air');await command('setblock 1 79 1 minecraft:air');await command('setblock 5 83 5 minecraft:air');await clean();
}catch(error){failure=error;console.error(error);}
finally{
 try{
  if(recording)await api('DELETE','/v0/recordings/'+recording.recordingId);
  if(!lease)await acquire();
  for(let i=0;i<10;i++){
   const tree=await api('GET','/v0/ui/tree');
   if(tree.screenClass.endsWith('TitleScreen')){shutdownResponse='requested';try{await click('menu.quit');shutdownResponse='acknowledged';}catch{shutdownResponse='transport_closed_requires_runner_attestation';}break;}
   const quit=nodes(tree).find(n=>['menu.returnToMenu','menu.disconnect'].includes(n.translationKey));
   if(quit){
    const beforeExit=await clean();let unconfirmed;
    try{await api('POST','/v0/ui/action',{selector:{nodeId:quit.nodeId},action:'click'});}catch(error){if(error.status!==409||!['REQUEST_CANCELLED','CONTROL_INPUT_GENERATION_CHANGED'].includes(error.code))throw error;unconfirmed={status:error.status,body:error.runtimeError};}
    const title=await screen(t=>t.screenClass.endsWith('TitleScreen'),'Save and Quit',60000);
    if(unconfirmed){const released=await api('POST','/v0/wait/until',{condition:{type:'input.released'},timeoutMs:5000});assert.equal(released.passed,true);actionSamples.push({case:'shutdown_context_exit',...contextExitEvidence(beforeExit,await clean(),title,unconfirmed),released});}
   }else await key(256);
  }
  assert.notEqual(shutdownResponse,'not_requested');
 }catch(error){failure??=error;console.error(error);}
 const after=await sources(),sourceChangedDuringRun=JSON.stringify(before)!==JSON.stringify(after);if(sourceChangedDuringRun)failure??=new Error('Source changed during Runtime test');
 const directory=resolve(repo,'validations',validation,'result');await mkdir(directory,{recursive:true});
 await writeFile(resolve(directory,target+'-command-configuration.json'),JSON.stringify({status:failure?'FAIL':'PASS',target,scenario:'command-configuration',fullSmoke:false,fullWorldMilestone:false,testedAt:new Date().toISOString(),testedCommit:execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).trim(),dirty:true,evidence:'development_runtime_native',runtimeConnection:{runtimeInstanceId:connection.instanceId,port:connection.port,pid:connection.pid},fixturePolicy:fixtureInput.policy,fixtureInput,checks,actionSamples,consumerChecks,consumerScope:'native_command_configuration',shutdownResponse,sourceHashes:after,sourceChangedDuringRun,persistentWriteCalls:0,notExecuted:['remaining_opaque_payloads','structure_template_save_load_policy','jigsaw_generation_effect_resolution','peer','packaged_artifact_runtime','human_acceptance'],...(failure?{error:String(failure)}:{})},null,2)+'\n');
}
if(failure)process.exitCode=1;
