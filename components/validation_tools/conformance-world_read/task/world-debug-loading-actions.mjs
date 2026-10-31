import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

export function fluidFixturePositions(player){const x=Math.floor(player.x/16)*16,z=Math.floor(player.z/16)*16,y=Math.floor(player.y)+8;return Array.from({length:65},(_,i)=>({x:x+(i%8)*2,y:y+Math.floor(i/64),z:z+(Math.floor(i/8)%8)*2}));}
const key=p=>`${p.x},${p.y},${p.z}`;

export async function worldDebugLoadingActions({api,target,repo,instance,fixtureInput,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples,setArm}) {
  assert.equal(fixtureInput?.policy,'immutable_baseline_restored');assert.equal(fixtureInput.target,target);
  let held=false,paused=false,fingerprint,failure,positions=[],baselineFluidCount;const owned=new Map(),cleanupErrors=[],pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const deep=(domains=['world','chunks'],selector={})=>api('POST','/v0/observe/deep',{category:'DEBUG',perspective:'server_authoritative',domains,selector:{chunkRadius:0,...selector}});
  async function debugRequest(path,body){
    for(let attempt=1;attempt<=3;attempt++)try{return await api('POST',path,body);}catch(error){
      const hint=error.runtimeError?.retryAfterMillis;
      if(attempt===3||error.status!==429||error.runtimeError?.error!=='EXPENSIVE_OPERATION_RATE_LIMITED'||!Number.isSafeInteger(hint)||hint<1||hint>1000)throw error;
      actionSamples.push({case:'loading_debug_budget_wait',path,attempt,retryAfterMillis:hint});await pause(hint+25);
    }
  }
  const arm=async()=>setArm((await debugRequest('/v0/debug/arm',{worldFingerprint:fingerprint,namespaces:['world'],ttlMs:60000})).debugArmId);
  const input=async()=>{const value=await api('GET','/v0/input/state');assert.equal(value.pressedKeyCount,0);assert.equal(value.pressedButtonCount,0);assert.equal(value.inputCleanupPending,false);assert.equal(value.inputCleanupFailed,false);assert.equal((await api('GET','/v0/control/status')).status,'available');return value;};
  async function escape(){const op=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:256,holdMs:40}],timeoutMs:12000});assert.equal((await api('POST',`/v0/operations/${op.operationId}/wait`,{timeoutMs:12000})).state,'completed');}
  async function pauseWorld(value){await acquire();held=true;await escape();await wait(t=>value?t.screenClass.endsWith('PauseScreen'):!t.screenClass,value?'loading fixture paused':'loading fixture resumed');paused=value;await release();held=false;await mode('OPERATE');}
  async function cells(selected){const result=new Map();for(let i=0;i<selected.length;i+=64){const snapshot=await deep(['blocks'],{blocks:selected.slice(i,i+64)});for(const state of snapshot.server.blocks){assert(state.available);assert.equal(state.loadRequested,false);const ref=snapshot.resourceRevisionRefs.find(r=>r.resourceType==='block'&&r.resourceKey===`${snapshot.server.dimension}@${state.key}`);assert(ref?.mutationPreconditionEligible);result.set(state.key,{state,ref});}}return result;}
  const request=(cell,blockId)=>({operation:'world.block.set',worldFingerprint:fingerprint,x:cell.state.x,y:cell.state.y,z:cell.state.z,expectedResourceVersion:cell.ref,expectedBlockId:cell.state.blockId,expectedProperties:cell.state.properties,blockId});
  async function batch(items,expected='completed'){
    await arm();const op=await debugRequest('/v0/debug/batches',{items,maxPerTickMutations:4,maxTotalDurationMs:15000});
    const done=await api('POST',`/v0/operations/${op.operationId}/wait?category=DEBUG`,{timeoutMs:15000});assert.equal(done.state,'completed',JSON.stringify(done));assert(done.result);
    for(const item of done.result.items){if(item.status!=='completed')continue;const result=item.result;assert.equal(result.gameplayEvidence,false);assert.equal(result.evidenceContaminated,true);assert.equal(result.storageAccessed,false);const at=key(result.after);if(result.after.blockId==='minecraft:air')owned.delete(at);else owned.set(at,{position:{x:result.after.x,y:result.after.y,z:result.after.z},ref:result.afterResourceVersion,state:result.after});}
    assert.equal(done.result.status,expected,JSON.stringify(done));return done;
  }
  try {
    await acquire();held=true;await click('menu.singleplayer','Singleplayer');const list=await wait(t=>t.screenClass.endsWith('SelectWorldScreen'),'loading fixed world list');const worlds=nodes(list).filter(n=>n.worldName==='World-Read-Alpha');assert.equal(worlds.length,1);await clickNode(worlds[0]);await click('selectWorld.select','Play Selected World');await wait(t=>!t.screenClass,'loading world running');await release();held=false;await pauseWorld(true);
    fingerprint=(await api('GET','/v0/world/fingerprint?category=DEBUG')).worldFingerprint;const player=(await deep(['player'])).server.player;positions=fluidFixturePositions(player.position);const initial=await cells(positions);for(const cell of initial.values())assert.equal(cell.state.blockId,'minecraft:air','Only fixed owned air may be used');const original=await deep();baselineFluidCount=original.server.world.fluidScheduledTickCount;
    await check('loaded block batch retains mode Arm and native count admission with zero rejected effects',async()=>{
      const body=request(initial.get(key(positions[0])),'minecraft:water'),inputBefore=await input();await api('POST','/v0/debug/mutations',body,'DEBUG_NOT_ARMED');await arm();await mode('READ');await api('POST','/v0/debug/batches',{items:[body]},'OPERATE_REQUIRED');await mode('OPERATE');await api('POST','/v0/debug/batches',{items:Array.from({length:65},()=>body)},'BATCH_BUDGET_EXCEEDED');
      assert.equal((await cells([positions[0]])).get(key(positions[0])).state.blockId,'minecraft:air');assert.equal((await input()).inputDispatchSequence,inputBefore.inputDispatchSequence);actionSamples.push({case:'loaded_batch_admission',actInputDelta:0});
    });
    await check('native queued fluid ticks beyond the per chunk budget explicitly report truncation without read effects',async()=>{
      const inputBefore=await input();for(let i=0;i<positions.length;i+=64)await batch(positions.slice(i,i+64).map(p=>request(initial.get(key(p)),'minecraft:water')));
      const before=await deep(),chunk=before.server.chunks[0];assert.equal(before.server.chunks.length,1);assert.equal(chunk.scheduledFluidTicks.length,64);assert(before.server.world.fluidScheduledTickCount-baselineFluidCount>=65);
      actionSamples.push({case:'native_65_queued_ticks',before,ownedPositions:positions,actInputDelta:0});
      const info=chunk.scheduledFluidTicksResult;assert(info,'Bounded tick arrays must declare omitted native entries');assert.equal(info.limit,64);assert.equal(info.returnedCount,64);assert(info.queuedCount>=65);assert.equal(info.truncated,true);assert.equal(info.coverage,'loaded_container_queued_only');assert.equal(info.includesCollectedToRun,false);
      assert(before.server.world.simulation.readOnly);assert.equal(before.server.world.simulation.integratedPaused,true);
      for(let i=0;i<2;i++){const next=await deep();assert.deepEqual(next.server.chunks[0].scheduledFluidTicks,chunk.scheduledFluidTicks);assert.deepEqual(next.server.chunks[0].scheduledFluidTicksResult,info);assert.equal(next.server.world.fluidScheduledTickCount,before.server.world.fluidScheduledTickCount);assert.equal(next.server.chunkLoadRequested,false);assert.equal(next.server.storageAccessed,false);}
      assert.equal((await input()).inputDispatchSequence,inputBefore.inputDispatchSequence);
    });
    await check('loaded batch partial failure preserves the applied prefix and refuses an unloaded target',async()=>{
      const current=await cells([positions[0]]),body=request(current.get(key(positions[0])),'minecraft:stone'),inputBefore=await input(),done=await batch([body,{...body,blockId:'minecraft:dirt'}],'partial');assert.equal(done.result.succeededItems,1);assert.equal(done.result.failedItems,1);assert.equal(done.result.items[1].error,'STALE_RESOURCE_REVISION');
      assert.equal((await cells([positions[0]])).get(key(positions[0])).state.blockId,'minecraft:stone');const far={x:positions[0].x+1000000,y:positions[0].y,z:positions[0].z+1000000},unknown=await deep(['blocks'],{blocks:[far]});assert.equal(unknown.server.blocks[0].available,false);assert.equal(unknown.server.blocks[0].loadRequested,false);
      await api('POST','/v0/debug/mutations',{...body,...far},'TARGET_NOT_LOADED');assert.equal((await deep(['blocks'],{blocks:[far]})).server.blocks[0].available,false);assert.equal((await input()).inputDispatchSequence,inputBefore.inputDispatchSequence);actionSamples.push({case:'native_loaded_batch_partial',done,unknown,actInputDelta:0});
    });
    await check('actual MCP reads the same bounded native loading and readonly simulation diagnostics',async()=>{
      const require=createRequire(resolve(repo,'components/companion/package.json')),{Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href),{StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href),env=Object.fromEntries(Object.entries(process.env).filter(([k,v])=>typeof v==='string'&&!/^MCP_MINECRAFT_/i.test(k)));env.MCP_MINECRAFT_INSTANCE_DIR=instance;env.MCP_MINECRAFT_TARGET=target;
      const client=new Client({name:'world-loading-consumer',version:'1.0.0'},{versionNegotiation:{mode:'legacy'}}),transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'}),inputBefore=await input();
      try {await client.connect(transport);const result=await client.callTool({name:'minecraft_deep_observe',arguments:{category:'DEBUG',perspective:'server_authoritative',domains:['world','chunks'],selector:{chunkRadius:0}}},{timeout:10000});assert(!result.isError,JSON.stringify(result));const data=result.structuredContent.data;assert.equal(data.server.chunks[0].scheduledFluidTicksResult.truncated,true);assert.equal(data.server.world.simulation.readOnly,true);assert.equal(data.server.storageAccessed,false);actionSamples.push({case:'loading_actual_mcp',data});}finally{await client.close();await transport.close();}assert.equal((await input()).inputDispatchSequence,inputBefore.inputDispatchSequence);
    });
  }catch(error){failure=error;}finally{
    try{if(held){await release();held=false;}await mode('OPERATE');if(fingerprint&&owned.size){await arm();for(let i=0;i<positions.length;i+=64){const group=positions.slice(i,i+64).filter(p=>owned.has(key(p)));if(!group.length)continue;const current=await cells(group),items=group.map(p=>{const cell=current.get(key(p)),tracked=owned.get(key(p));assert.deepEqual(cell.ref,tracked.ref,'Unknown external block change must be preserved');return request(cell,'minecraft:air');});await batch(items);}}
      if(fingerprint&&paused&&baselineFluidCount!==undefined){await pauseWorld(false);const deadline=Date.now()+10000;let state;do{state=await deep();if(state.server.world.fluidScheduledTickCount<=baselineFluidCount)break;await pause(200);}while(Date.now()<deadline);assert(state.server.world.fluidScheduledTickCount<=baselineFluidCount,'Native scheduled fixture ticks must naturally drain after blocks are restored');actionSamples.push({case:'native_scheduler_drained',fluidScheduledTickCount:state.server.world.fluidScheduledTickCount});await pauseWorld(true);}
    }catch(error){cleanupErrors.push(error.message);}
    try{await debugRequest('/v0/debug/disarm');setArm(undefined);await mode('READ');await input();}catch(error){cleanupErrors.push(error.message);}actionSamples.push({case:'world_debug_loading_cleanup',cleanupErrors,remainingOwnedBlocks:[...owned.keys()],persistentWriteCalls:0});
  }
  if(failure)throw failure;assert.deepEqual(cleanupErrors,[]);assert.equal(owned.size,0);
}
