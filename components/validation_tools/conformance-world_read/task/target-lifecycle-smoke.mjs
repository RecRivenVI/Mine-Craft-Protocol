import assert from 'node:assert/strict';
import {readFile,readdir,writeFile,mkdir} from 'node:fs/promises';
import {resolve,relative} from 'node:path';
import {createHash} from 'node:crypto';
import {randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {connectRuntime} from '../../runtime-connection.mjs';

const repo=resolve(import.meta.dirname,'../../../..');
const args=Object.fromEntries(process.argv.slice(2).map(arg=>arg.replace(/^--/,'').split('=')));
const target=args.target,validation='conformance-world_read';
assert(['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric'].includes(target));
const instance=resolve(repo,'validations',validation,'instance',target,'client');
const fixtureInput=JSON.parse(await readFile(resolve(instance,'fixture-input.json'),'utf8'));
assert.equal(fixtureInput.policy,'immutable_baseline_restored');assert.equal(fixtureInput.target,target);
assert.equal(fixtureInput.baselineSha256,args['fixture-hash']);assert.equal(fixtureInput.restoredSha256,args['fixture-hash']);assert.equal(fixtureInput.credentialsCopied,false);
const connection=await connectRuntime(instance,target),checks=[],actionSamples=[];
let lease,failure,shutdownResponse='not_requested';
async function sources(){
  const files=[];
  for(const root of [`versions/${target}/src`,'components/runtime_core/src/main','components/runtime_safety/src/main'])
    for(const entry of await readdir(resolve(repo,root),{recursive:true,withFileTypes:true}))if(entry.isFile())files.push(relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/'));
  for(const entry of await readdir(import.meta.dirname,{withFileTypes:true}))if(entry.isFile()&&/\.(mjs|ps1)$/.test(entry.name))files.push(relative(repo,resolve(import.meta.dirname,entry.name)).replaceAll('\\','/'));
  files.push('components/protocol_schema/src/main/openapi/minecraft-control-v0.json','components/validation_tools/InstanceProfiles.ps1','components/validation_tools/Prepare-InstanceProfile.ps1','components/validation_tools/instance-profiles.gradle','build.gradle.kts','components/validation_tools/RuntimeConnection.ps1','components/validation_tools/runtime-connection.mjs',`validations/${validation}/validation.toml`,'gradle.properties','settings.gradle.kts','gradle/libs.versions.toml',`versions/${target}/build.gradle.kts`,`versions/${target}/target.properties`);
  const hashes={};for(const file of [...new Set(files)].sort())hashes[file]=createHash('sha256').update(await readFile(resolve(repo,file))).digest('hex');return hashes;
}
const before=await sources();
async function api(method,path,body,expected){
  const response=await connection.fetch(path,{method,headers:{...(lease?{'x-mcp-control-lease':lease}:{}),...(body?{'content-type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(25000)});
  const value=await response.json();
  if(expected){assert(!response.ok,JSON.stringify(value));assert.equal(value.error,expected,JSON.stringify(value));}
  else assert(response.ok,JSON.stringify(value));
  return value;
}
const nodes=t=>(t.children??[]).flatMap(n=>[n,...nodes(n)]),pause=()=>new Promise(r=>setTimeout(r,50));
async function poll(read,accept,label,budget=10000){const end=Date.now()+budget;let value;do{value=await read();if(accept(value))return value;await pause();}while(Date.now()<end);throw new Error(label+': '+JSON.stringify(value));}
async function treeAt(accept,label,budget){return poll(()=>api('GET','/v0/ui/tree'),t=>!t.overlayIdentity&&accept(t),label,budget);}
async function click(key){const tree=await api('GET','/v0/ui/tree'),found=nodes(tree).filter(n=>n.translationKey===key&&n.visible!==false&&n.active!==false);assert.equal(found.length,1,key);return api('POST','/v0/ui/action',{selector:{nodeId:found[0].nodeId},action:'click'});}
async function key(code){const op=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:code,holdMs:40}],timeoutMs:10000});const out=await api('POST',`/v0/operations/${op.operationId}/wait`,{timeoutMs:10000});assert.equal(out.state,'completed',JSON.stringify(out));}
async function acquire(){assert.equal((await api('GET','/v0/control/mode')).reconsentRequired,false,'Do not clear human override');lease=(await api('POST','/v0/control/acquire',{ttlMs:60000})).leaseId;}
async function mode(value){const current=await api('GET','/v0/control/mode');await api('POST','/v0/control/mode',{mode:value,expectedModeVersion:current.modeVersion});lease=undefined;}
async function clean(){const state=await api('GET','/v0/input/state');assert.equal(state.pressedKeyCount,0);assert.equal(state.pressedButtonCount,0);return state;}
async function command(text){await api('POST','/v0/control/renew',{ttlMs:60000});const receipt=await api('POST','/v0/command/player',{command:text});assert.equal(receipt.mechanism,'GAME_ROUTED_RAW');assert.equal(receipt.effectClassification,'STATE_MUTATION');assert.equal(receipt.effectConfirmed,false);assert.equal(receipt.gameplayAcceptanceEligible,false);}
async function block(x=2,y=80,z=0){return api('GET',`/v0/world/block?x=${x}&y=${y}&z=${z}`);}
async function setTarget(id){await command(`setblock 2 80 0 ${id}`);return poll(()=>block(),b=>b.available&&b.block===id,'target reaches client');}
async function act(ref,action='aim',extra={}){const op=await api('POST','/v0/pipelines',{steps:[{type:'world.action',targetRef:ref,action,...extra}],timeoutMs:12000});return api('POST',`/v0/operations/${op.operationId}/wait`,{timeoutMs:14000});}
async function check(name,run){await run();checks.push({name,status:'PASS'});console.log('PASS: '+name);}
try{
  await acquire();
  await check('semantic GUI enters the restored immutable world',async()=>{
    await click('menu.singleplayer');const tree=await treeAt(t=>t.screenClass.endsWith('SelectWorldScreen'),'world list');
    const world=nodes(tree).filter(n=>n.worldName==='World-Read-Alpha');assert.equal(world.length,1);
    await api('POST','/v0/ui/action',{selector:{nodeId:world[0].nodeId},action:'click'});await click('selectWorld.select');await treeAt(t=>!t.screenClass,'fixed world',150000);
  });
  await check('bounded diagnostic fixture uses native commands, not gameplay evidence',async()=>{
    await command('gamemode creative @s');await command('fill -1 79 -1 7 79 3 minecraft:stone');await command('fill -1 80 -1 7 83 3 minecraft:air');await command('tp @s 0.5 80 0.5');
    let ticks;
    await poll(()=>api('GET','/v0/player'),p=>{if(Math.abs(p.x-.5)>.01||Math.abs(p.y-80)>.01||Math.abs(p.z-.5)>.01){ticks=undefined;return false;}ticks??=p.clientTick;return p.clientTick>=ticks+5;},'stationary actor');await clean();
  });
  await check('unrelated update in the same native Chunk preserves the original target reference',async()=>{
    await setTarget('minecraft:stone');await command('setblock 4 80 0 minecraft:dirt');await poll(()=>block(4),b=>b.block==='minecraft:dirt','neighbor dirt');
    const original=await block(),issued=Date.now();assert(original.targetRef);
    await command('setblock 4 80 0 minecraft:cobblestone');const neighbor=await poll(()=>block(4),b=>b.block==='minecraft:cobblestone','neighbor update');
    const after=await block();assert.equal(after.block,original.block);assert.equal(original.x,2);assert.equal(neighbor.x,4);assert.equal(Math.floor(original.x/16),Math.floor(neighbor.x/16));
    const outcome=await act(original.targetRef);actionSamples.push({case:'same_chunk_unrelated_update',original,after,neighbor,outcome,elapsedMs:Date.now()-issued,actAuthority:'PLAYTEST',arrangeAuthority:'DEBUG_PRIVILEGED_native_commands'});
    assert(Date.now()-issued<12000,'Must not confuse reference TTL with lifecycle');assert.equal(outcome.state,'completed',JSON.stringify(outcome));await clean();
  });
  await check('same-position A-B-A still rejects the old reference with zero input',async()=>{
    const original=await block(),issued=Date.now();await setTarget('minecraft:air');await setTarget('minecraft:stone');
    const input=await clean(),pose=await api('GET','/v0/player'),outcome=await act(original.targetRef);assert.equal(outcome.state,'failed');assert.equal(outcome.errorCode,'WORLD_TARGET_STALE',JSON.stringify(outcome));assert(Date.now()-issued<12000);
    const after=await clean(),afterPose=await api('GET','/v0/player');
    actionSamples.push({case:'same_position_aba',outcome,before:input,after,pose,afterPose,elapsedMs:Date.now()-issued});
    // The production counter includes one unconditional release_all record, even with no held input.
    // Every actual dispatched key/mouse/look would add another increment; require exactly cleanup.
    assert.equal(after.inputDispatchSequence-input.inputDispatchSequence,1,'only the empty cleanup bookkeeping, no action input');
    for(const field of ['x','y','z','yaw','pitch'])assert.equal(afterPose[field],pose[field],'stale action changed '+field);
    Object.assign(actionSamples.at(-1),{cleanupBookkeeping:1,postRejectionActionInput:0});
  });
  await check('fresh replacement reference works but READ and OPERATE cannot use it',async()=>{
    const targetBlock=await block();await mode('READ');let input=await clean();await api('POST','/v0/pipelines',{steps:[{type:'world.action',targetRef:targetBlock.targetRef,action:'aim'}]},'TAKEOVER_REQUIRED');
    assert.equal((await clean()).inputDispatchSequence,input.inputDispatchSequence);await mode('OPERATE');input=await clean();await api('POST','/v0/pipelines',{steps:[{type:'world.action',targetRef:targetBlock.targetRef,action:'aim'}]},'TAKEOVER_REQUIRED');
    assert.equal((await clean()).inputDispatchSequence,input.inputDispatchSequence);await mode('READ');await acquire();assert.equal((await act((await block()).targetRef)).state,'completed');await clean();
  });
  await check('owned Survival mining still follows native destruction and releases input',async()=>{
    await command('clear @s');await setTarget('minecraft:oak_planks');await command('gamemode survival @s');
    await key(69);await treeAt(t=>/(^|\.)InventoryScreen$/.test(t.screenClass),'actual Survival inventory, not only command acceptance');await key(256);await treeAt(t=>!t.screenClass,'return from Survival inventory');
    const outcome=await act((await block()).targetRef,'mine',{holdMs:8000});assert.equal(outcome.state,'completed',JSON.stringify(outcome));const result=outcome.result.steps[0].result;
    assert.equal(result.clientBreakPredicted,true);assert(result.holdDurationMillis>400,'Must complete real held Survival mining');assert.equal(result.evidenceAuthority,'PLAYTEST');assert.equal(result.effectConfirmed,false);
    await poll(()=>block(),b=>b.block==='minecraft:air','native mining effect');await clean();actionSamples.push({case:'owned_mining',result});
  });
  await check('native bulk snapshot reuses the cached Chunk but invalidates its old reference',async()=>{
    await setTarget('minecraft:stone');const original=await block(),issued=Date.now(),attempt=randomUUID().replaceAll('-','');
    const receipt=await api('POST','/v0/command/player',{command:'say Mine-Craft-Protocol_chunk_snapshot_'+attempt});
    assert.equal(receipt.mechanism,'GAME_ROUTED_RAW');assert.equal(receipt.effectClassification,'COMMUNICATION');assert.equal(receipt.effectConfirmed,false);
    const replay=await poll(async()=>{try{return JSON.parse(await readFile(resolve(repo,'validations',validation,'result',target+'-chunk-snapshot.json'),'utf8'));}catch(error){if(error.code==='ENOENT'||error instanceof SyntaxError)return {};throw error;}},r=>r.attemptId===attempt,'current native packet replay',6000);
    assert.equal(replay.probeOnly,true);assert.equal(replay.sourceChunkAlreadyLoaded,true);assert.equal(replay.sameNativeCachedChunk,true);assert.equal(replay.sameBlockState,true);assert.equal(replay.nativePositionUpdates,0);assert.equal(replay.nativeSnapshotCompleted,true);assert.equal(replay.worldMutationUsed,false);assert.equal(replay.chunkLoadRequested,false);
    const input=await clean(),pose=await api('GET','/v0/player'),outcome=await act(original.targetRef),after=await clean(),afterPose=await api('GET','/v0/player');
    assert.equal(outcome.state,'failed');assert.equal(outcome.errorCode,'WORLD_TARGET_STALE',JSON.stringify(outcome));assert(Date.now()-issued<12000,'Snapshot invalidation, not token TTL');
    assert.equal(after.inputDispatchSequence-input.inputDispatchSequence,1,'only empty failure cleanup, not an input');
    for(const field of ['x','y','z','yaw','pitch'])assert.equal(afterPose[field],pose[field]);
    assert.equal((await act((await block()).targetRef)).state,'completed');await clean();
    actionSamples.push({case:'bulk_snapshot_replay',replay,outcome,elapsedMs:Date.now()-issued,arrangeAuthority:'FIXTURE',arrangeMechanism:'validation_probe_native_packet_replay',gameplayAcceptanceEligible:false,postRejectionActionInput:0});
  });
  await check('real Save and Quit invalidates the old world reference without loading a Chunk',async()=>{
    await setTarget('minecraft:stone');const original=await block(),issued=Date.now();
    await key(256);await treeAt(t=>t.screenClass.endsWith('PauseScreen'),'pause');await click('menu.returnToMenu');await treeAt(t=>t.screenClass.endsWith('TitleScreen'),'world unloaded',60000);
    const current=await api('GET','/v0/control/mode');if(current.mode!=='TAKEOVER'){lease=undefined;await acquire();}
    assert.equal((await api('GET','/v0/player')).available,false);
    const input=await clean(),outcome=await act(original.targetRef);assert.equal(outcome.state,'failed');assert.equal(outcome.errorCode,'WORLD_TARGET_STALE',JSON.stringify(outcome));assert(Date.now()-issued<12000,'World identity, not token TTL, must reject');
    const after=await clean();assert.equal(after.inputDispatchSequence-input.inputDispatchSequence,1,'only empty failure cleanup at title, no action dispatch');
    actionSamples.push({case:'world_unload',outcome,elapsedMs:Date.now()-issued,postRejectionActionInput:0,worldAvailable:false});
  });
}catch(error){failure=error;console.error(error);}
finally{
  try{
    if(!lease)await acquire();
    for(let i=0;i<8;i++){
      const tree=await api('GET','/v0/ui/tree');
      if(tree.screenClass.endsWith('TitleScreen')){shutdownResponse='requested';try{await click('menu.quit');shutdownResponse='acknowledged';}catch(error){shutdownResponse='transport_closed_requires_runner_attestation';}break;}
      const quit=nodes(tree).find(n=>['menu.returnToMenu','menu.disconnect'].includes(n.translationKey));
      if(quit){await api('POST','/v0/ui/action',{selector:{nodeId:quit.nodeId},action:'click'});await treeAt(t=>t.screenClass.endsWith('TitleScreen'),'Save & Quit',60000);}else await key(256);
    }
    assert.notEqual(shutdownResponse,'not_requested');
  }catch(error){failure??=error;console.error(error);}
  const after=await sources(),sourceChangedDuringRun=JSON.stringify(before)!==JSON.stringify(after);if(sourceChangedDuringRun)failure??=new Error('Sources changed during test');
  const directory=resolve(repo,'validations',validation,'result');await mkdir(directory,{recursive:true});
  await writeFile(resolve(directory,target+'-target-lifecycle.json'),JSON.stringify({status:failure?'FAIL':'PASS',target,scenario:'target-lifecycle',fullSmoke:false,fullWorldMilestone:false,testedAt:new Date().toISOString(),testedCommit:execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).trim(),dirty:true,evidence:'development_runtime_native',fixturePolicy:fixtureInput.policy,fixtureInput,checks,actionSamples,consumerChecks:[],consumerScope:'not_executed',shutdownResponse,sourceHashes:after,sourceChangedDuringRun,persistentWriteCalls:0,...(failure?{error:String(failure)}:{})},null,2)+'\n');
}
if(failure)process.exitCode=1;
