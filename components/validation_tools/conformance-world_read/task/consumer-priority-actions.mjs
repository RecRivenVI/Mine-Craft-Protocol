import assert from 'node:assert/strict';
import {createHash,randomUUID} from 'node:crypto';
import {createRequire} from 'node:module';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {finishBaselineWorldExit} from './interaction-baseline-actions.mjs';

// Native consumer flows on the runner's immutable copy, never another project's instance.
// Commands are diagnostic Arrange. Captures prove delivery, not third-party scene convergence.
export async function consumerPriorityActions({api,connection,repo,instance,target,mode,acquire,release,key,click,wait,nodes,check,actionSamples,setArm,enterWorld=false}) {
 const nap=()=>new Promise(r=>setTimeout(r,70));
 async function poll(read,accept,label,budget=30000){const end=Date.now()+budget;let value;do{value=await read();if(accept(value))return value;await nap();}while(Date.now()<end);throw new Error(label+': '+JSON.stringify(value));}
 const player=()=>api('GET','/v0/player');
 async function clean(){const input=await api('GET','/v0/input/state');assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);return input;}
 async function command(text){await api('POST','/v0/control/renew',{ttlMs:60000});const r=await api('POST','/v0/command/player',{command:text});assert.equal(r.mechanism,'GAME_ROUTED_RAW');assert.equal(r.effectConfirmed,false);assert.equal(r.gameplayAcceptanceEligible,false);return r;}
 async function pipeline(steps){const op=await api('POST','/v0/pipelines',{steps,timeoutMs:15000});const result=await api('POST',`/v0/operations/${op.operationId}/wait`,{timeoutMs:16000});assert.equal(result.state,'completed',JSON.stringify(result));await clean();return result;}
 async function enter(){await click('menu.singleplayer','Singleplayer');const t=await wait(t=>t.screenClass.endsWith('SelectWorldScreen'),'consumer world list');const row=nodes(t).find(n=>n.worldName==='World-Read-Alpha');assert(row);await api('POST','/v0/ui/action',{selector:{nodeId:row.nodeId},action:'double_click'});await wait(t=>!t.screenClass,'consumer fixed world',150000);await poll(player,p=>p.available,'USER world ready');}
 if(enterWorld){await acquire();await enter();await release();}
 const samples=[];
 let lastMutation=0,originalTemperature;
 const facts=await readFile(resolve(repo,'gradle.properties'),'utf8'),namespace=facts.match(/^mod_id=(.+)$/m)?.[1]?.trim();assert(namespace,'Project mod_id fact');
 const safe=namespace+':safe';
 const query=ids=>({category:'DEBUG',perspective:'both',domains:['providers'],includeProviderData:true,providerIds:ids,providerQuery:{probe:'consumer-priority'},budgets:{maxProviders:8,providerTimeoutMs:1000,maxProviderBytes:16384,maxTotalProviderBytes:65536,maxResponseBytes:262144}});
 const observe=ids=>api('POST','/v0/observe/deep',query(ids));
 async function safeState(){const value=await observe([safe]);const entry=value.providers.find(p=>p.providerId===safe);assert.equal(entry?.status,'completed',JSON.stringify(value));const ref=value.resourceRevisionRefs.find(r=>r.resourceType==='provider'&&r.resourceKey===safe);assert(ref?.mutationPreconditionEligible);return {value,entry,ref};}
 const fingerprint=async()=>(await api('GET','/v0/world/fingerprint?category=DEBUG')).worldFingerprint;
 async function mutation(body,expected,headers={}){const remaining=550-(Date.now()-lastMutation);if(remaining>0)await new Promise(r=>setTimeout(r,remaining));lastMutation=Date.now();return api('POST','/v0/debug/mutations',body,expected,headers);}
 async function arm(){const fp=await fingerprint();const a=await api('POST','/v0/debug/arm',{worldFingerprint:fp,namespaces:['provider'],ttlMs:60000});setArm(a.debugArmId);return fp;}
 function request(ref,fp,value,extra={}){return {operation:'provider.mutate',providerId:safe,worldFingerprint:fp,expectedResourceVersion:ref,mutation:{operation:'temperature.set',value,...extra}};}
 async function capture(stage){
  const before=await wait(t=>t.windowWidth===1920&&t.windowHeight===1080,'capture viewport without loading overlay');
  const response=await connection.fetch('/v0/capture',{signal:AbortSignal.timeout(25000)});assert(response.ok);const bytes=Buffer.from(await response.arrayBuffer());
  assert.deepEqual([...bytes.subarray(0,8)],[137,80,78,71,13,10,26,10]);assert.equal(bytes.readUInt32BE(16),1920);assert.equal(bytes.readUInt32BE(20),1080);
  const after=await api('GET','/v0/ui/tree');assert.equal(after.screenIdentity,before.screenIdentity);assert.equal(after.overlayIdentity,0);assert.equal(after.windowWidth,1920);assert.equal(after.windowHeight,1080);
  samples.push({case:'consumer_frame',stage,bytes:bytes.length,sha256:createHash('sha256').update(bytes).digest('hex'),screenClass:after.screenClass,overlayIdentity:0,dimensions:[1920,1080],thirdPartyConvergenceClaimed:false});
 }
 try{
  await check('consumer native player motion and relative look preserve USER input provenance',async()=>{
   await acquire();await command('gamemode creative @s');await command('fill -4 79 -4 4 79 4 minecraft:stone');await command('tp @s 0.5 80 0.5 0 0');
   const before=await poll(player,p=>p.available&&Math.abs(p.x-.5)<.05&&Math.abs(p.z-.5)<.05&&Math.abs(p.yaw)<.05,'consumer camera Arrange');
   const result=await pipeline([{type:'key.tap',key:87,holdMs:160},{type:'mouse.delta',dx:12,dy:3}]);
   const after=await poll(player,p=>p.available&&Math.hypot(p.x-before.x,p.z-before.z)>.02&&Math.abs(p.yaw-before.yaw)>.01,'actual player moved and turned');
   assert.equal(after.authority,'CLIENT_KNOWN');assert.equal(after.dataCategory,'USER');assert.equal(after.chunkLoadRequested,false);
   samples.push({case:'consumer_motion',before,after,result,arrangeAuthority:'diagnostic_native_commands',actAuthority:'PLAYTEST',heldInput:0});
   await command('tp @s 0.5 80 0.5 0 0');await command('time set day');await command('setblock 2 80 2 minecraft:lime_concrete');
   await poll(()=>api('GET','/v0/world/block?x=2&y=80&z=2'),b=>b.available&&b.block==='minecraft:lime_concrete','client marker');await release();
  });
  await check('consumer Capture and recording deliver current content without an input Lease',async()=>{
   const input=await clean();assert.equal((await api('GET','/v0/control/mode')).mode,'READ');await capture('initial_world');
   const recording=await api('POST','/v0/recordings',{category:'USER',captureFrames:true,intervalMs:250,durationMs:1200,maxSamples:4,stateReads:[{providerId:'minecraft:client/player'}],contactSheet:{enabled:false}});
   const status=await poll(()=>api('GET',`/v0/recordings/${recording.recordingId}`),s=>s.artifactReady,'recording finalized',30000);assert(status.writtenFrames>0);assert(status.writtenStates>0);
   const response=await connection.fetch(`/v0/recordings/${recording.recordingId}/artifact`,{signal:AbortSignal.timeout(15000)});assert(response.ok);const bytes=Buffer.from(await response.arrayBuffer());assert.equal(bytes.readUInt16BE(0),0x504b);
   assert.equal((await clean()).inputDispatchSequence,input.inputDispatchSequence);samples.push({case:'consumer_recording',recordingId:recording.recordingId,status,artifactBytes:bytes.length,readWithoutLease:true});
  });
  await check('consumer resource reload waits for actual UI overlay completion before sampling',async()=>{
   await acquire();await pipeline([{type:'key.chord',keys:[{key:292},{key:84}],holdMs:60}]);
   await wait(t=>!t.screenClass,'resource reload overlay finished',90000);await poll(player,p=>p.available,'world after reload');await release();await capture('after_resource_reload');
  });
  await check('consumer dimension travel checks client identity and never force-loads by reading',async()=>{
   await acquire();
   for(const dimension of ['minecraft:the_nether','minecraft:overworld']){
    const before=await clean();assert.equal(before.inputContextAvailable,true);assert.equal(typeof before.inputContextId,'string');assert(before.inputContextId.length>0);
    await api('POST','/v0/control/renew',{ttlMs:60000});
    const receipt=await api('POST','/v0/command/player',{command:`execute in ${dimension} run tp @s 0.5 80 0.5 0 0`,route:'NATIVE'});
    assert.equal(receipt.route,'NATIVE');assert.equal(receipt.mechanism,'NORMAL_NETWORK');assert.equal(receipt.dataCategory,'USER');assert.equal(receipt.inputDispatched,false);assert.equal(receipt.requiresControlLease,false);assert.equal(receipt.effectConfirmed,false);assert.equal(receipt.serverValidationObserved,false);assert.equal(receipt.gameplayAcceptanceEligible,false);assert.equal(receipt.feedbackAvailable,false);assert(!('commandFeedback'in receipt));
    const p=await poll(player,p=>p.available&&p.dimension===dimension,'native dimension transfer independently observed by USER',90000);assert.equal(p.dataCategory,'USER');assert.equal(p.authority,'CLIENT_KNOWN');
    const readyUi=await wait(t=>!t.screenClass&&t.overlayIdentity===0,'dimension Screen and overlay cleared',90000);
    const released=await api('POST','/v0/wait/until',{condition:{type:'input.released'},timeoutMs:5000});assert.equal(released.passed,true);assert.deepEqual(released.evidence,{pressedKeyCount:0,pressedButtonCount:0});
    const after=await poll(clean,value=>value.inputContextAvailable===true&&value.inputCleanupPending===false&&value.inputCleanupFailed===false&&value.inputContextGeneration>before.inputContextGeneration,'native dimension epoch and cleanup independently observed',5000);
    for(const field of ['inputGeneration','inputContextGeneration']){assert(Number.isSafeInteger(before[field])&&before[field]>=0);assert(Number.isSafeInteger(after[field])&&after[field]>before[field]);}assert.equal(typeof after.inputContextId,'string');assert(after.inputContextId.length>0);assert.notEqual(after.inputContextId,before.inputContextId);
    samples.push({case:'consumer_dimension',dimension,before,receipt,player:p,after,released,readyUi,authority:'diagnostic_native_command',effectConfirmedBy:'independent_USER_dimension_and_native_epoch_not_submission_receipt'});
   }
   await release();const unloaded=await api('GET','/v0/world/block?x=1000000&y=80&z=1000000');assert.equal(unloaded.available,false);assert.equal(unloaded.chunkLoadRequested,false);
   await capture('after_dimension_roundtrip');
  });
  await check('consumer Save Quit reentry persists the marker through semantic world selection',async()=>{
   await acquire();await key(256);await wait(t=>t.screenClass.endsWith('PauseScreen'),'pause');
   const t=await api('GET','/v0/ui/tree'),quit=nodes(t).find(n=>['menu.disconnect','menu.returnToMenu'].includes(n.translationKey));assert(quit);
   const worldExit=await finishBaselineWorldExit({api,quit,wait,clickNode:node=>api('POST','/v0/ui/action',{selector:{nodeId:node.nodeId},action:'click'})});
   samples.push({case:'consumer_world_exit',...worldExit});await release();await capture('saved_title');
   await acquire();await enter();await poll(()=>api('GET','/v0/world/block?x=2&y=80&z=2'),b=>b.available&&b.block==='minecraft:lime_concrete','saved marker client readback',90000);await release();await capture('saved_world_reentry');
  });
  await check('consumer Provider V2 reads validate owner thread schema category and independent scope',async()=>{
   const value=await observe(['safe','client-thread','render-thread','server-thread'].map(id=>namespace+':'+id));
   for(const id of ['safe','client-thread','render-thread','server-thread']){const p=value.providers.find(p=>p.providerId===namespace+':'+id);assert.equal(p?.status,'completed',JSON.stringify(value));assert.match(p.data.threadName,/thread/i);assert.match(p.data.threadName,id==='client-thread'||id==='render-thread'?/Render thread/:/Server thread/);}
   await api('POST','/v0/observe/deep',{...query([safe]),category:'USER'},'WORLD_USER_PERCEPTION_UNAVAILABLE');
   const bad=await api('POST','/v0/observe/deep',{...query([safe]),providerQuery:{enable:true}});assert.equal(bad.providers[0].reason,'query_schema_violation');
   const denied=await observe([namespace+':scope']);assert.equal(denied.providers[0].reason,'provider_scope_denied');
   originalTemperature=(await safeState()).entry.data.temperature;samples.push({case:'consumer_provider_owner_reads',providers:value.providers,readWithoutArm:true,readWithoutLease:true});
  });
  await check('consumer typed Provider mutation requires independent Arm mode schema and revision',async()=>{
   let state=await safeState();let fp=await fingerprint();await mutation(request(state.ref,fp,21.25),'DEBUG_NOT_ARMED');
   fp=await arm();await mutation(request(state.ref,fp,21.25),'OPERATE_REQUIRED');await mode('OPERATE');
   await mutation(request(state.ref,fp,21.25,{operation:'not-approved'}),'PROVIDER_DEBUG_SCHEMA_VIOLATION');
   const changed=await mutation(request(state.ref,fp,21.25));assert.equal(changed.mechanism,'registered_provider_typed_mutation');assert.equal(changed.gameplayEvidence,false);assert.equal(changed.after.temperature,21.25);assert(changed.afterResourceVersion.revision>state.ref.revision);
   await mutation(request(state.ref,fp,22),'STALE_RESOURCE_REVISION');state=await safeState();assert.equal(state.entry.data.temperature,21.25);
   await mutation(request(state.ref,fp,originalTemperature));samples.push({case:'consumer_provider_mutation',changed,authority:'DEBUG_PRIVILEGED',inputLease:false});
  });
  await check('consumer Provider deadline cancels its delayed future with no late mutation',async()=>{
   const state=await safeState(),fp=await fingerprint();
   const denied=await mutation(request(state.ref,fp,23,{operation:'temperature.delayed_set',delayMs:500}),'REQUEST_DEADLINE_EXCEEDED',{'X-MCP-Deadline-Ms':'80'});
   const start=Date.now();await poll(safeState,s=>{assert.equal(s.entry.data.temperature,originalTemperature);assert.equal(s.ref.revision,state.ref.revision);return Date.now()-start>=650;},'cancelled provider never applies late',5000);
   samples.push({case:'consumer_provider_deadline',error:denied.error,noLateEffect:true,windowMs:Date.now()-start});
   await api('POST','/v0/debug/disarm');setArm(undefined);await mode('READ');
  });
  await check('consumer actual MCP reads the registered Provider without input ownership',async()=>{
   const require=createRequire(resolve(repo,'components/companion/package.json'));
   const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
   const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
   const env=Object.fromEntries(Object.entries(process.env).filter(([,v])=>typeof v==='string'));
   for(const name of ['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE'])delete env[name];env.MCP_MINECRAFT_INSTANCE_DIR=instance;
   const client=new Client({name:'consumer-provider-regression',version:'1.0.0'}),transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
   try{await client.connect(transport);const result=await client.callTool({name:'minecraft_deep_observe',arguments:query([safe])});assert(!result.isError,JSON.stringify(result));assert.equal(result.structuredContent.data.providers[0].data.temperature,originalTemperature);const state=await client.callTool({name:'minecraft_control',arguments:{action:'status'}});assert.equal(state.structuredContent.data.mode,'READ');assert.equal(state.structuredContent.data.takeoverActive,false);samples.push({case:'consumer_mcp_provider',transport:'real_mcp_stdio',readWithoutArm:true,readWithoutLease:true});}finally{await client.close();}
  });
 }finally{
  if(originalTemperature!==undefined){await mode('OPERATE');const fp=await arm(),state=await safeState();if(state.entry.data.temperature!==originalTemperature)await mutation(request(state.ref,fp,originalTemperature));await api('POST','/v0/debug/disarm');setArm(undefined);await mode('READ');}
  await clean();actionSamples.push(...samples);
 }
}
