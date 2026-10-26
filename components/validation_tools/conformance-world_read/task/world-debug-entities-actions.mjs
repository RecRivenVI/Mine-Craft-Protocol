import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

export function registryRef(snapshot) {
  const value=snapshot.server.entityRegistry;assert(value?.available);assert.equal(value.chunkLoadRequested,false);
  assert.equal(value.count,value.members.length);assert.deepEqual(value.members,[...new Set(value.members)].sort());
  const ref=snapshot.resourceRevisionRefs.find(r=>r.resourceType==='entity_registry'&&r.resourceKey===value.key);
  assert(ref?.mutationPreconditionEligible);return ref;
}

export async function worldDebugEntitiesActions({api,target,repo,instance,fixtureInput,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples,setArm}) {
  assert.equal(fixtureInput?.policy,'immutable_baseline_restored');assert.equal(fixtureInput.target,target);
  let fingerprint,chunk,position,held=false,lastWrite=0,failure;
  const owned=new Set(),cleanupErrors=[],pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const deep=selector=>api('POST','/v0/observe/deep',{category:'DEBUG',perspective:'server_authoritative',domains:['player','entities'],selector:{entityRadius:16,...selector}});
  const arm=async()=>setArm((await api('POST','/v0/debug/arm',{worldFingerprint:fingerprint,namespaces:['entity'],ttlMs:60000})).debugArmId);
  async function mutate(body,error){const delay=650-(Date.now()-lastWrite);if(delay>0)await pause(delay);lastWrite=Date.now();return api('POST','/v0/debug/mutations',body,error);}
  const spawn=(ref,type='minecraft:pig',at=position)=>({operation:'entity.spawn',worldFingerprint:fingerprint,expectedResourceVersion:ref,value:{entityType:type,position:at,yaw:20.5,pitch:0}});
  function remove(snapshot,uuid){const state=snapshot.server.entities.find(e=>e.uuid===uuid);assert(state);
    const ref=snapshot.resourceRevisionRefs.find(r=>r.resourceType==='entity'&&r.resourceKey===uuid);assert(ref);
    return {operation:'entity.remove',worldFingerprint:fingerprint,expectedResourceVersion:ref,entityUuid:uuid,expectedEntityType:state.type};}
  async function input(){const v=await api('GET','/v0/input/state');assert.equal(v.pressedKeyCount,0);assert.equal(v.pressedButtonCount,0);assert.equal(v.inputCleanupPending,false);assert.equal(v.inputCleanupFailed,false);assert.equal((await api('GET','/v0/control/status')).status,'available');return v;}
  try {
    await acquire();held=true;await click('menu.singleplayer','Singleplayer');const list=await wait(t=>t.screenClass.endsWith('SelectWorldScreen'),'entity fixed world list');
    const worlds=nodes(list).filter(n=>n.worldName==='World-Read-Alpha');assert.equal(worlds.length,1);await clickNode(worlds[0]);await click('selectWorld.select','Play Selected World');await wait(t=>!t.screenClass,'entity world running');
    const op=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:256,holdMs:40}],timeoutMs:12000});assert.equal((await api('POST',`/v0/operations/${op.operationId}/wait`,{timeoutMs:12000})).state,'completed');
    await wait(t=>t.screenClass.endsWith('PauseScreen'),'paused native entity fixture');await release();held=false;await mode('OPERATE');
    fingerprint=(await api('GET','/v0/world/fingerprint?category=DEBUG')).worldFingerprint;
    const p=(await deep({})).server.player.position;position={x:Math.floor(p.x)+2.5,y:Math.floor(p.y)+1.125,z:Math.floor(p.z)+0.5};chunk={x:Math.floor(position.x/16),z:Math.floor(position.z/16)};
    await check('entity factories require Arm world identity mode and a complete loaded membership version',async()=>{
      const before=await deep({entityChunk:chunk}),ref=registryRef(before),inputBefore=await input();
      await mutate(spawn(ref),'DEBUG_NOT_ARMED');await arm();await mode('READ');await mutate(spawn(ref),'OPERATE_REQUIRED');await mode('OPERATE');
      await mutate({...spawn(ref),worldFingerprint:'0'.repeat(64)},'WORLD_FINGERPRINT_MISMATCH');
      await mutate({...spawn(ref),value:{...spawn(ref).value,nbt:'{}'}},'INVALID_DEBUG_ENTITY_LIFECYCLE');
      await mutate(spawn(ref,'minecraft:not_a_native_entity'),'INVALID_DEBUG_ENTITY_LIFECYCLE');
      await mutate(spawn(ref,'minecraft:player'),'DEBUG_ENTITY_FACTORY_UNAVAILABLE');
      await mutate({...spawn(ref),expectedEntityCount:before.server.entityRegistry.count+1},'VALUE_PRECONDITION_FAILED');
      const after=await deep({entityChunk:chunk});assert.deepEqual(after.server.entityRegistry,before.server.entityRegistry);assert.deepEqual(registryRef(after),ref);
      assert.equal((await input()).inputDispatchSequence,inputBefore.inputDispatchSequence);actionSamples.push({case:'entity_factory_guards',before,after,inputBefore,actInputDelta:0});
    });
    await check('unloaded destination returns unavailable and cannot create or force load a chunk',async()=>{
      const far={x:position.x+160000,z:position.z+160000,y:position.y},farChunk={x:Math.floor(far.x/16),z:Math.floor(far.z/16)};
      const before=await deep({entityChunk:farChunk});assert.equal(before.server.entityRegistry.available,false);assert.equal(before.server.entityRegistry.reason,'chunk_not_loaded');
      assert(!before.resourceRevisionRefs.some(r=>r.resourceType==='entity_registry'));
      await mutate(spawn(registryRef(await deep({entityChunk:chunk})),'minecraft:pig',far),'TARGET_NOT_LOADED');
      const after=await deep({entityChunk:farChunk});assert.deepEqual(after.server.entityRegistry,before.server.entityRegistry);assert.equal(after.server.chunkLoadRequested,false);
      actionSamples.push({case:'entity_factory_unloaded',before,after});
    });
    await check('native factory insertion stale retry and discard expose actual identity and tombstone',async()=>{
      await arm();const before=await deep({entityChunk:chunk}),ref=registryRef(before),inputBefore=await input();
      const created=await mutate({...spawn(ref),expectedEntityCount:before.server.entityRegistry.count});const entity=created.createdEntity;assert(entity?.uuid);owned.add(entity.uuid);
      assert.equal(entity.type,'minecraft:pig');assert.equal(entity.x,position.x);assert.equal(entity.y,position.y);assert.equal(entity.z,position.z);assert.equal(entity.yaw,20.5);assert.equal(entity.removed,false);
      assert.equal(created.factorySemantics,'native_factory_defaults_no_nbt_no_finalize_spawn');assert.equal(created.gameplayEvidence,false);assert.equal(created.evidenceContaminated,true);assert.equal(created.storageAccessed,false);
      const after=await deep({entityChunk:chunk});assert(after.server.entityRegistry.members.includes(entity.uuid));assert.deepEqual(registryRef(after),created.afterResourceVersion);
      const stale=await mutate(spawn(ref),'STALE_RESOURCE_REVISION');assert.equal(stale.actualRevision,created.afterResourceVersion.revision);assert.deepEqual((await deep({entityChunk:chunk})).server.entityRegistry,after.server.entityRegistry);
      const request=remove(after,entity.uuid);await mutate({...request,expectedEntityType:'minecraft:cow'},'VALUE_PRECONDITION_FAILED');
      const removed=await mutate(request);owned.delete(entity.uuid);assert.equal(removed.after.uuid,entity.uuid);assert.equal(removed.after.removed,true);assert.equal(removed.after.removalReason,'DISCARDED');assert.equal(removed.afterResourceVersion.mutationPreconditionEligible,false);
      await mutate(request,'TARGET_NOT_LOADED');const restored=await deep({entityChunk:chunk});assert.deepEqual(restored.server.entityRegistry.members,before.server.entityRegistry.members);assert(registryRef(restored).revision>ref.revision);
      await mutate(spawn(ref),'STALE_RESOURCE_REVISION');const player=restored.server.player;await mutate({operation:'entity.remove',worldFingerprint:fingerprint,entityUuid:player.uuid,expectedResourceVersion:ref},'INVALID_DEBUG_ENTITY_LIFECYCLE');
      const inputAfter=await input();assert.equal(inputAfter.inputDispatchSequence,inputBefore.inputDispatchSequence);actionSamples.push({case:'native_entity_factory_discard',before,created,after,stale,removed,restored,inputBefore,inputAfter,actInputDelta:0});
    });
    await check('actual MCP preserves typed factory coordinates membership version and explicit discard',async()=>{
      const require=createRequire(resolve(repo,'components/companion/package.json')), {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href),{StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
      const env=Object.fromEntries(Object.entries(process.env).filter(([k,v])=>typeof v==='string'&&!/^MCP_MINECRAFT_/i.test(k)));env.MCP_MINECRAFT_INSTANCE_DIR=instance;env.MCP_MINECRAFT_TARGET=target;
      const client=new Client({name:'world-entity-consumer',version:'1.0.0'},{versionNegotiation:{mode:'legacy'}}),transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
      const call=async(name,args)=>{const r=await client.callTool({name,arguments:args},{timeout:10000});assert(!r.isError,JSON.stringify(r));return r.structuredContent.data;};
      const inputBefore=await input();try{await client.connect(transport);await call('minecraft_debug_arm',{action:'arm',worldFingerprint:fingerprint,namespaces:['entity'],ttlMs:60000});
        const before=await call('minecraft_deep_observe',{category:'DEBUG',perspective:'server_authoritative',domains:['entities'],selector:{entityChunk:chunk,entityRadius:16}});registryRef(before);await pause(650);
        const created=await call('minecraft_debug',{action:'mutate',mutation:spawn(registryRef(before),'minecraft:armor_stand')});owned.add(created.createdEntity.uuid);
        const after=await deep({entityChunk:chunk});assert(after.server.entities.some(e=>e.uuid===created.createdEntity.uuid));await pause(650);
        const removed=await call('minecraft_debug',{action:'mutate',mutation:remove(after,created.createdEntity.uuid)});owned.delete(created.createdEntity.uuid);assert.equal(removed.after.removed,true);
        const restored=await deep({entityChunk:chunk});assert.deepEqual(restored.server.entityRegistry.members,before.server.entityRegistry.members);actionSamples.push({case:'entity_factory_actual_mcp',created,removed,consumer:'mcp_stdio'});
      }finally{await client.close();await transport.close();}assert.equal((await input()).inputDispatchSequence,inputBefore.inputDispatchSequence);
    });
  }catch(error){failure=error;}finally{
    if(held){try{await release();}catch(error){cleanupErrors.push(error.message);}}
    if(fingerprint){try{await mode('OPERATE');await arm();for(const uuid of owned){const current=await deep({entityChunk:chunk});await mutate(remove(current,uuid));owned.delete(uuid);}}catch(error){cleanupErrors.push(error.message);}}
    try{await api('POST','/v0/debug/disarm');setArm(undefined);await mode('READ');}catch(error){cleanupErrors.push(error.message);}
    try{await input();}catch(error){cleanupErrors.push(error.message);}actionSamples.push({case:'world_debug_entities_cleanup',remainingOwnedEntities:[...owned],cleanupErrors,persistentWriteCalls:0});
  }
  if(failure)throw failure;assert.deepEqual(cleanupErrors,[]);assert.equal(owned.size,0);
}
