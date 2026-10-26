import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

export function relationState(snapshot,id){const value=snapshot.server.player?.uuid===id?snapshot.server.player:snapshot.server.entities.find(e=>e.uuid===id);assert(value?.relationships);const state=structuredClone(value.relationships);state.vehicle??=null;if(state.leash.supported)state.leash.holderUuid??=null;if(state.owner.supported)state.owner.ownerUuid??=null;return state;}
export function relationRef(snapshot,id){const ref=snapshot.resourceRevisionRefs.find(r=>r.resourceType==='entity_relationship'&&r.resourceKey===id);assert(ref?.mutationPreconditionEligible);assert.equal(ref.lifecycleId,relationState(snapshot,id).lifecycleId);return ref;}

export async function worldDebugRelationsActions({api,target,repo,instance,fixtureInput,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples,setArm}) {
  assert.equal(fixtureInput?.policy,'immutable_baseline_restored');assert.equal(fixtureInput.target,target);
  const owned=new Set(),cleanupErrors=[],pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));let fingerprint,playerId,held=false,lastWrite=0,failure;
  const deep=(selector={})=>api('POST','/v0/observe/deep',{category:'DEBUG',perspective:'server_authoritative',domains:['player','entities'],selector:{entityRadius:16,...selector}});
  const arm=async(namespaces=['player','entity'])=>setArm((await api('POST','/v0/debug/arm',{worldFingerprint:fingerprint,namespaces,ttlMs:60000})).debugArmId);
  async function mutate(body,error){const delay=650-(Date.now()-lastWrite);if(delay>0)await pause(delay);lastWrite=Date.now();return api('POST','/v0/debug/mutations',body,error);}
  function body(snapshot,id,kind,value){const state=relationState(snapshot,id),before=kind==='vehicle'?state.vehicle:kind==='leash'?state.leash.holderUuid:{ownerUuid:state.owner.ownerUuid,tamed:state.owner.tamed};
    const related=new Set();if(kind==='owner'){if(value.ownerUuid)related.add(value.ownerUuid);}else{if(value)related.add(value);if((kind==='vehicle'||(kind==='leash'&&value))&&state.vehicle)related.add(state.vehicle);if((kind==='leash'||(kind==='vehicle'&&value))&&state.leash.holderUuid)related.add(state.leash.holderUuid);}
    return {operation:`${id===playerId?'player':'entity'}.${kind}.set`,...(id===playerId?{}:{entityUuid:id}),worldFingerprint:fingerprint,expectedResourceVersion:relationRef(snapshot,id),value,...(kind==='owner'&&!state.owner.supported?{}:{expectedValue:before}),relatedResourceVersions:[...related].map(uuid=>relationRef(snapshot,uuid))};
  }
  async function input(){const s=await api('GET','/v0/input/state');assert.equal(s.pressedKeyCount,0);assert.equal(s.pressedButtonCount,0);assert.equal(s.inputCleanupPending,false);assert.equal(s.inputCleanupFailed,false);assert.equal((await api('GET','/v0/control/status')).status,'available');return s;}
  async function spawn(type,offset){const p=(await deep()).server.player.position,at={x:Math.floor(p.x)+2.5+offset%4,y:Math.floor(p.y)+1,z:Math.floor(p.z)+0.5+Math.floor(offset/4)};
    const s=await deep({entityChunk:{x:Math.floor(at.x/16),z:Math.floor(at.z/16)}}),registry=s.server.entityRegistry;assert(registry.available);
    const ref=s.resourceRevisionRefs.find(r=>r.resourceType==='entity_registry'&&r.resourceKey===registry.key);assert(ref);
    const created=await mutate({operation:'entity.spawn',worldFingerprint:fingerprint,expectedResourceVersion:ref,value:{entityType:type,position:at}});owned.add(created.createdEntity.uuid);return created.createdEntity.uuid;
  }
  async function chain(id,kind,value){await arm();const before=await deep(),initial=body(before,id,kind,value),inputBefore=await input(),applied=await mutate(initial);assert.equal(applied.gameplayEvidence,false);assert.equal(applied.evidenceContaminated,true);assert.equal(applied.storageAccessed,false);
    const after=await deep();assert.deepEqual(relationRef(after,id),applied.afterResourceVersion);const stale=await mutate(initial,'STALE_RESOURCE_REVISION');assert.equal(stale.actualRevision,applied.afterResourceVersion.revision);
    const restored=await mutate(body(after,id,kind,initial.expectedValue));const end=await deep();assert.deepEqual(relationState(end,id),relationState(before,id));
    assert(relationRef(end,id).revision>initial.expectedResourceVersion.revision);assert.equal((await input()).inputDispatchSequence,inputBefore.inputDispatchSequence);
    actionSamples.push({case:'native_relation_chain',operation:initial.operation,applied,stale,restored,before:relationState(before,id),after:relationState(after,id),actInputDelta:0});return {applied,restored};
  }
  try {
    await acquire();held=true;await click('menu.singleplayer','Singleplayer');const list=await wait(t=>t.screenClass.endsWith('SelectWorldScreen')&&nodes(t).some(n=>n.worldName==='World-Read-Alpha'),'relation fixed world list populated');const worlds=nodes(list).filter(n=>n.worldName==='World-Read-Alpha');assert.equal(worlds.length,1);await clickNode(worlds[0]);await click('selectWorld.select','Play Selected World');await wait(t=>!t.screenClass,'relation world running');
    const op=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:256,holdMs:40}],timeoutMs:12000});assert.equal((await api('POST',`/v0/operations/${op.operationId}/wait`,{timeoutMs:12000})).state,'completed');await wait(t=>t.screenClass.endsWith('PauseScreen'),'native relation fixture paused');await release();held=false;await mode('OPERATE');
    fingerprint=(await api('GET','/v0/world/fingerprint?category=DEBUG')).worldFingerprint;playerId=(await deep()).server.player.uuid;await arm();
    const a=await spawn('minecraft:pig',0),b=await spawn('minecraft:pig',1),boat=await spawn(target.startsWith('1.')?'minecraft:boat':'minecraft:oak_boat',2),cart=await spawn('minecraft:minecart',3),wolf=await spawn('minecraft:wolf',4),horse=await spawn('minecraft:horse',5);
    // Each independent riding case gets a fresh actor: native dismount starts a 60-tick
    // boarding cooldown, which must not be reset or bypassed in this paused fixture.
    const seatRider=await spawn('minecraft:pig',6),seatChallenger=await spawn('minecraft:pig',7),cycleRider=await spawn('minecraft:pig',8),mcpRider=await spawn('minecraft:pig',9),leashRider=await spawn('minecraft:pig',10),leashedMount=await spawn('minecraft:pig',11);
    await check('relationship reads use dedicated versions and mutations preserve Arm mode and participant admission',async()=>{
      const initial=await deep(),request=body(initial,a,'vehicle',boat),inputBefore=await input();await api('POST','/v0/debug/disarm');setArm(undefined);await mutate(request,'DEBUG_NOT_ARMED');await arm();await mode('READ');await mutate(request,'OPERATE_REQUIRED');await mode('OPERATE');
      await mutate({...request,relatedResourceVersions:[]},'INVALID_DEBUG_RELATION');await mutate({...request,value:a,relatedResourceVersions:[relationRef(initial,a)]},'DEBUG_RELATION_REJECTED');
      await mutate({...request,value:'not-a-uuid'},'INVALID_DEBUG_RELATION');await mutate({...body(initial,a,'owner',{ownerUuid:playerId,tamed:true})},'DEBUG_RELATION_UNSUPPORTED');
      await mutate({...request,entityUuid:playerId,expectedResourceVersion:relationRef(initial,playerId)},'INVALID_DEBUG_RELATION');
      const after=await deep();assert.deepEqual(relationState(after,a),relationState(initial,a));assert.equal((await input()).inputDispatchSequence,inputBefore.inputDispatchSequence);
      actionSamples.push({case:'relation_admission',before:relationState(initial,a),after:relationState(after,a),actInputDelta:0});
    });
    await check('native nonforced riding changes both participants and refuses stale vehicle or full seats',async()=>{
      await arm();const initial=await deep(),old=body(initial,a,'vehicle',boat);await mutate(body(initial,b,'vehicle',boat));
      await mutate(old,'STALE_RESOURCE_REVISION');assert.equal(relationState(await deep(),a).vehicle,null);
      await mutate(body(await deep(),b,'vehicle',null));await chain(a,'vehicle',boat);
      await mutate(body(await deep(),seatRider,'vehicle',cart));const full=await deep();await mutate(body(full,seatChallenger,'vehicle',cart),'DEBUG_RELATION_REJECTED');assert.equal(relationState(await deep(),seatChallenger).vehicle,null);
      await mutate(body(await deep(),seatRider,'vehicle',null));
      await mutate(body(await deep(),cycleRider,'vehicle',boat));const cycle=await deep();await mutate(body(cycle,boat,'vehicle',cycleRider),'DEBUG_RELATION_REJECTED');await mutate(body(await deep(),cycleRider,'vehicle',null));
    });
    await check('leash attachment uses native eligibility rejects cycles and explicitly clears without dropping lead',async()=>{
      await arm();await chain(a,'leash',b);await chain(a,'leash',playerId);await mutate(body(await deep(),a,'leash',b));
      const cycle=await deep();await mutate(body(cycle,b,'leash',a),'DEBUG_RELATION_REJECTED');assert.equal(relationState(await deep(),b).leash.holderUuid,null);await mutate(body(await deep(),a,'leash',null));
      await mutate(body(await deep(),leashRider,'vehicle',boat));const mounted=await deep(),attachment=body(mounted,leashRider,'leash',playerId);assert(attachment.relatedResourceVersions.some(r=>r.resourceKey===boat));
      const applied=await mutate(attachment),after=await deep();assert.equal(relationState(after,leashRider).vehicle,null);assert.equal(relationState(after,leashRider).leash.holderUuid,playerId);assert(!relationState(after,boat).passengers.includes(leashRider));
      assert(applied.relatedAfter.some(r=>r.uuid===boat));await mutate(body(after,leashRider,'leash',null));actionSamples.push({case:'native_leash_automatic_dismount',applied});
      await mutate(body(await deep(),leashedMount,'leash',playerId));const tied=await deep(),mount=body(tied,leashedMount,'vehicle',boat);assert(mount.relatedResourceVersions.some(r=>r.resourceKey===playerId));await mutate({...mount,relatedResourceVersions:mount.relatedResourceVersions.filter(r=>r.resourceKey!==playerId)},'INVALID_DEBUG_RELATION');
      const ridden=await mutate(mount),detached=await deep(),newDrops=detached.server.entities.filter(e=>e.type==='minecraft:item'&&!tied.server.entities.some(old=>old.uuid===e.uuid));for(const item of newDrops)owned.add(item.uuid);assert.equal(newDrops.length,1,'native riding must report its actual lead drop, not suppress it');assert.equal(relationState(detached,leashedMount).leash.holderUuid,null);assert.equal(relationState(detached,leashedMount).vehicle,boat);await mutate(body(detached,leashedMount,'vehicle',null));actionSamples.push({case:'native_riding_leash_drop',ridden,createdDropUuids:newDrops.map(e=>e.uuid)});
    });
    await check('native tame ownership preserves before after state and restores owned wolf and horse',async()=>{
      for(const id of [wolf,horse]){const result=await chain(id,'owner',{ownerUuid:playerId,tamed:true});assert.equal(result.applied.after.owner.ownerUuid,playerId);assert.equal(result.applied.after.owner.tamed,true);
        assert.deepEqual(result.restored.afterEntity.attributes,result.applied.beforeEntity.attributes);
        if(result.restored.afterEntity.health!==result.applied.beforeEntity.health){const current=await deep(),entity=current.server.entities.find(e=>e.uuid===id),ref=current.resourceRevisionRefs.find(r=>r.resourceType==='entity'&&r.resourceKey===id);
          const cleanup=await mutate({operation:'entity.state.set',worldFingerprint:fingerprint,entityUuid:id,expectedResourceVersion:ref,value:{health:result.applied.beforeEntity.health},expectedValue:{health:entity.health}});assert.equal(cleanup.after.health,result.applied.beforeEntity.health);actionSamples.push({case:'native_tame_health_side_effect_explicit_restore',before:result.applied.beforeEntity.health,afterNativeUntame:entity.health,cleanup});}}
    });
    await check('current player riding independently requires player and entity namespaces without an input Lease',async()=>{
      const initial=await deep(),request=body(initial,playerId,'vehicle',boat);await arm(['player']);await mutate(request,'DEBUG_SCOPE_DENIED');assert.equal(relationState(await deep(),playerId).vehicle,null);
      await arm(['entity']);await mutate(request,'DEBUG_SCOPE_DENIED');await arm();
      const batch=await api('POST','/v0/debug/batches',{items:[body(await deep(),playerId,'vehicle',boat)],maxTotalDurationMs:15000});
      const done=await api('POST',`/v0/operations/${batch.operationId}/wait?category=DEBUG`,{timeoutMs:15000});assert.equal(done.state,'completed',JSON.stringify(done));assert.equal(relationState(await deep(),playerId).vehicle,boat);await mutate(body(await deep(),playerId,'vehicle',null));await input();
      actionSamples.push({case:'combined_namespace_native_batch',done,inputLeaseAcquired:false});
    });
    await check('actual MCP vehicle mutation preserves related identity versions and clears normally',async()=>{
      const require=createRequire(resolve(repo,'components/companion/package.json')),{Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href),{StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href),env=Object.fromEntries(Object.entries(process.env).filter(([k,v])=>typeof v==='string'&&!/^MCP_MINECRAFT_/i.test(k)));env.MCP_MINECRAFT_INSTANCE_DIR=instance;env.MCP_MINECRAFT_TARGET=target;
      const client=new Client({name:'world-relations-consumer',version:'1.0.0'},{versionNegotiation:{mode:'legacy'}}),transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'}),call=async(name,args)=>{const r=await client.callTool({name,arguments:args},{timeout:10000});assert(!r.isError,JSON.stringify(r));return r.structuredContent.data;};const inputBefore=await input();
      try{await client.connect(transport);await call('minecraft_debug_arm',{action:'arm',worldFingerprint:fingerprint,namespaces:['player','entity'],ttlMs:60000});await pause(650);
        const applied=await call('minecraft_debug',{action:'mutate',mutation:body(await deep(),mcpRider,'vehicle',boat)});assert.equal(relationState(await deep(),mcpRider).vehicle,boat);await pause(650);
        const restored=await call('minecraft_debug',{action:'mutate',mutation:body(await deep(),mcpRider,'vehicle',null)});assert.equal(relationState(await deep(),mcpRider).vehicle,null);actionSamples.push({case:'relation_actual_mcp',applied,restored,consumer:'mcp_stdio'});
      }finally{await client.close();await transport.close();}assert.equal((await input()).inputDispatchSequence,inputBefore.inputDispatchSequence);
    });
  }catch(error){failure=error;}finally{
    try{if(held){await release();held=false;}await mode('OPERATE');if(fingerprint){await arm();let current=await deep();if(playerId&&owned.has(relationState(current,playerId).vehicle))await mutate(body(current,playerId,'vehicle',null));
      for(const id of owned){current=await deep();if(!current.server.entities.some(e=>e.uuid===id))continue;const state=relationState(current,id);if(state.vehicle)await mutate(body(current,id,'vehicle',null));current=await deep();const leash=relationState(current,id).leash;if(leash.supported&&leash.resolution==='resolved'&&leash.holderUuid)await mutate(body(current,id,'leash',null));}
      for(const id of [...owned].reverse()){current=await deep();const entity=current.server.entities.find(e=>e.uuid===id);if(!entity){owned.delete(id);continue;}const version=current.resourceRevisionRefs.find(r=>r.resourceType==='entity'&&r.resourceKey===id);await mutate({operation:'entity.remove',worldFingerprint:fingerprint,expectedResourceVersion:version,entityUuid:id,expectedEntityType:entity.type});owned.delete(id);}}}catch(error){cleanupErrors.push(error.message);}
    try{await api('POST','/v0/debug/disarm');setArm(undefined);await mode('READ');await input();}catch(error){cleanupErrors.push(error.message);}actionSamples.push({case:'world_debug_relations_cleanup',cleanupErrors,remainingOwnedEntities:[...owned],persistentWriteCalls:0});
  }
  if(failure)throw failure;assert.deepEqual(cleanupErrors,[]);assert.equal(owned.size,0);
}
