import assert from 'node:assert/strict';
import {collectPlayerStateEvents} from './region-stream-actions.mjs';

// Explicit existing serialization harness: same-process host ownership, not cross-process proof.
export async function integratedPeerActions({api,connection,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples,setArm}){
  await acquire();await click('menu.singleplayer','Singleplayer');
  const tree=await wait(value=>value.screenClass.endsWith('SelectWorldScreen'),'owned world list');
  const row=nodes(tree).find(node=>node.worldName==='World-Read-Alpha');assert(row);
  await clickNode(row);await click('selectWorld.select','Play Selected World');
  await wait(value=>!value.screenClass,'owned integrated world',150000);await release();
  let grant,attached,arm,changed,original,mutation;
  const samples={kind:'integrated_owner_serialized_self_peer',independentPrincipalsProved:false,secondEndpointCreated:false};
  const query={category:'DEBUG',perspective:'server_authoritative',domains:['blocks'],budgets:{maxResponseBytes:30000}};
  const deep=()=>api('POST','/v0/observe/deep',query);
  const reference=snapshot=>{const value=snapshot.resourceRevisionRefs.find(item=>item.resourceType==='block'&&item.mutationPreconditionEligible);assert(value);return value;};
  const detach=async()=>{if(attached){await api('POST','/v0/server/peer/delegation',{action:'detach',delegationId:attached});attached=null;}};
  const revoke=async()=>{if(grant){await api('DELETE','/v0/server/peer/delegations/'+grant.delegationId);grant=null;}};
  try{
    await check('integrated host reuses its live Runtime authority without a second endpoint',async()=>{
      const deadline=Date.now()+15000;let peer;
      do{peer=await api('GET','/v0/server/peer');if(peer.connected)break;await new Promise(done=>setTimeout(done,100));}while(Date.now()<deadline);
      assert(peer?.connected&&peer.connectionId&&peer.playerUuid);
      const state=await api('GET','/v0/input/state');samples.beforeInput=state;
      samples.unattached=await api('GET','/v0/world/fingerprint?category=DEBUG',undefined,'PEER_DELEGATION_REQUIRED');
      grant=await api('POST','/v0/server/peer/delegations',{playerUuid:peer.playerUuid,connectionId:peer.connectionId,scopes:['read','debug','debug.world.read'],ttlMs:60000});
      samples.hostFingerprint=grant.worldFingerprint;
      await api('POST','/v0/server/peer/delegation',{delegationId:grant.delegationId,connectionId:peer.connectionId});attached=grant.delegationId;
      samples.player=await api('POST','/v0/observe/deep',{category:'DEBUG',perspective:'server_authoritative',domains:['player'],projection:{playerFields:['identity','transform','vitals']},budgets:{maxResponseBytes:30000}});
      assert.equal(samples.player.peerPlayerUuid,peer.playerUuid);
      samples.peer=peer;
    });
    await check('integrated delegated mutation uses current native revisions and independent OPERATE',async()=>{
      await mode('OPERATE');
      const player=await api('GET','/v0/player?category=USER');
      const p={x:Math.floor(Number(player.x??player.position?.x)/16)*16+8,y:Math.floor(Number(player.y??player.position?.y))+8,z:Math.floor(Number(player.z??player.position?.z)/16)*16+8};
      assert(Object.values(p).every(Number.isSafeInteger));query.selector={blocks:[p]};
      original=await deep();assert(original.server.blocks[0].available);
      arm=await api('POST','/v0/debug/arm',{worldFingerprint:grant.worldFingerprint,namespaces:['world'],ttlMs:60000});setArm(arm.debugArmId);
      const fp=grant.worldFingerprint;await revoke();await detach();
      grant=await api('POST','/v0/server/peer/delegations',{playerUuid:samples.peer.playerUuid,connectionId:samples.peer.connectionId,scopes:['read','debug','debug.world.read','debug.write','debug.world'],debugArmId:arm.debugArmId,ttlMs:60000});
      await api('POST','/v0/server/peer/delegation',{delegationId:grant.delegationId,connectionId:samples.peer.connectionId});attached=grant.delegationId;
      mutation={operation:'world.block.set',...p,worldFingerprint:fp,blockId:original.server.blocks[0].blockId==='minecraft:stone'?'minecraft:glass':'minecraft:stone',expectedResourceVersion:reference(original)};
      changed=await api('POST','/v0/debug/mutations',mutation);samples.mutation=changed;
      assert.equal(changed.evidenceAuthority,'DEBUG_PRIVILEGED');assert.equal(changed.gameplayEvidence,false);
      samples.stale=await api('POST','/v0/debug/mutations',mutation,'STALE_RESOURCE_REVISION');
      const after=await deep();assert.equal(after.server.blocks[0].blockId,mutation.blockId);
      samples.restored=await api('POST','/v0/debug/mutations',{...mutation,blockId:original.server.blocks[0].blockId,properties:original.server.blocks[0].properties,expectedResourceVersion:reference(after)});changed=null;
    });
    await check('integrated owner revoke is persistent and leaves player input untouched',async()=>{
      await revoke();samples.revoked=await api('POST','/v0/observe/deep',query,'PEER_AUTHORITY_REVOKED');await detach();
      samples.afterInput=await api('GET','/v0/input/state');
      assert.equal(samples.afterInput.inputDispatchSequence,samples.beforeInput.inputDispatchSequence);
      assert.equal(samples.afterInput.pressedKeyCount,0);assert.equal(samples.afterInput.pressedButtonCount,0);
      assert.equal((await api('GET','/v0/control/mode')).takeoverActive,false);
      actionSamples.push(samples);
    });
  }finally{
    if(changed&&mutation&&original){const now=await deep();await api('POST','/v0/debug/mutations',{...mutation,blockId:original.server.blocks[0].blockId,properties:original.server.blocks[0].properties,expectedResourceVersion:reference(now)});}
    try{await revoke();}finally{await detach();}
    if(arm){await api('POST','/v0/debug/disarm');setArm(undefined);}
    await mode('READ');
  }
  await check('native USER target stream and ordered GUI action/readback retain distinct results',async()=>{
    const stream=await collectPlayerStateEvents(connection,{eventType:'world.target.block',collectMillis:1250});
    const events=stream.messages.filter(item=>item.event.type==='world.target.block');assert(events.length>0);
    for(const {event} of events){assert.equal(event.dataCategory,'USER');assert.equal(event.source,'native_client_target_loaded_block');assert.equal(event.chunkLoadRequested,false);assert.equal(event.storageAccessed,false);assert(Number.isSafeInteger(event.sourceTick));}
    await acquire();
    try{
      const started=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:69,holdMs:80},{type:'state.read',read:{providerId:'minecraft:client/player',category:'USER'}}]});
      const done=await api('POST','/v0/operations/'+started.operationId+'/wait',{timeoutMs:10000});assert.equal(done.state,'completed',JSON.stringify(done));
      const readback=done.result.steps[1].result;assert.equal(readback.type,'provider.read');assert.equal(readback.dataCategory,'USER');
      await wait(value=>value.screenClass.endsWith('InventoryScreen'),'native inventory after readback');
      const closed=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:256,holdMs:80}]});
      assert.equal((await api('POST','/v0/operations/'+closed.operationId+'/wait',{timeoutMs:10000})).state,'completed');
      assert.equal((await api('GET','/v0/control/mode')).reconsentRequired,false);
      actionSamples.push({kind:'native_target_stream_and_user_readback',stream,operation:done});
    }finally{await release();}
  });
}
