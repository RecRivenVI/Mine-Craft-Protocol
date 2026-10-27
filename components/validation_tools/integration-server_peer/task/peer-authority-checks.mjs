import assert from 'node:assert/strict';

// Uses the two explicitly owned, single-JAR instances; console commands are fixture authority.
export async function checkPeerAuthority({client, server, nativeConsole, until}) {
  let previous=0;
  const paced=call=>async(...args)=>{const remaining=550-(Date.now()-previous);if(remaining>0)await new Promise(done=>setTimeout(done,remaining));previous=Date.now();return call(...args);};
  client=paced(client);server=paced(server);
  const mode=async(call,value)=>{const state=await call('GET','/v0/control/mode');return call('POST','/v0/control/mode',{mode:value,expectedModeVersion:state.modeVersion});};
  const peer=await client('GET','/v0/server/peer');assert(peer.connected&&peer.connectionId&&peer.playerUuid);
  const readBody={category:'DEBUG',perspective:'server_authoritative',domains:['player'],projection:{playerFields:['identity','transform','vitals']},selector:{chunkRadius:0},budgets:{maxResponseBytes:30000}};
  const read=()=>client('POST','/v0/observe/deep',readBody);
  const denied=await client('POST','/v0/observe/deep',readBody,403);assert.equal(denied.error,'PEER_DELEGATION_REQUIRED');
  let attached, grant, serverArm, clientArm, original, latest;
  const report={beforeDelegation:denied,fixtureAuthority:'owned_native_server_console',persistentWriteCalls:0};
  report.localCapabilitiesBeforeDelegation=await client('GET','/v0/debug/capabilities');
  assert.equal(report.localCapabilitiesBeforeDelegation.worldDiscovery.available,false);
  assert.equal(report.localCapabilitiesBeforeDelegation.worldDiscovery.errorCode,'PEER_DELEGATION_REQUIRED');
  assert(report.localCapabilitiesBeforeDelegation.operations.length>0);
  assert(report.localCapabilitiesBeforeDelegation.operations.every(operation=>operation.authority==='CLIENT_UI'));
  const issue=async(scopes,arm)=>{grant=await server('POST','/v0/server/peer/delegations',{playerUuid:peer.playerUuid,connectionId:peer.connectionId,scopes,ttlMs:60000,...(arm?{debugArmId:arm.debugArmId}:{})});return grant;};
  const attach=async()=>{await client('POST','/v0/server/peer/delegation',{delegationId:grant.delegationId,connectionId:peer.connectionId});attached=grant.delegationId;};
  const detach=async()=>{if(attached){await client('POST','/v0/server/peer/delegation',{action:'detach',delegationId:attached});attached=null;}};
  const revoke=async()=>{if(grant){await server('DELETE','/v0/server/peer/delegations/'+grant.delegationId);grant=null;}};
  const reads=['read','debug','debug.world.read'];
  try {
    await issue(reads);await attach();
    report.nonOperator=await client('POST','/v0/observe/deep',readBody,403);assert.equal(report.nonOperator.error,'PEER_AUTHORITY_REVOKED');
    nativeConsole('op OptionalTest');
    report.player=await until(async()=>{try{return await read();}catch(error){return {waiting:String(error)};}},value=>value.peerPlayerUuid===peer.playerUuid,'real server OP and delegated read');
    assert.equal(report.player.peerConnectionId,peer.connectionId);assert(report.player.serverPrincipalId);
    assert.equal(report.player.dataCategory,'DEBUG');
    report.authorizedCapabilities=await client('GET','/v0/debug/capabilities');
    assert.equal(report.authorizedCapabilities.worldDiscovery.available,true);
    assert(report.authorizedCapabilities.operations.some(operation=>operation.authority==='CLIENT_UI'));
    assert(report.authorizedCapabilities.operations.some(operation=>operation.operation==='world.block.set'));
    report.anchor=await client('POST','/v0/observe/deep',{category:'DEBUG',perspective:'server_authoritative',domains:['chunks'],selector:{anchor:{x:20000000,y:100,z:20000000},chunkRadius:0},budgets:{maxResponseBytes:30000}});
    assert.equal(report.anchor.server.chunks[0].chunkX,1250000);assert.equal(report.anchor.server.chunks[0].loaded,false);assert.equal(report.anchor.server.chunkLoadRequested,false);
    report.otherDimension=await client('POST','/v0/observe/deep',{category:'DEBUG',perspective:'server_authoritative',domains:['chunks'],selector:{dimension:'minecraft:the_nether',chunkRadius:0},budgets:{maxResponseBytes:30000}});
    assert.equal(report.otherDimension.server.dimension,'minecraft:the_nether');
    const staleConnection=await client('POST','/v0/server/peer/delegation',{delegationId:grant.delegationId,connectionId:'00000000-0000-0000-0000-000000000000'},409);
    assert.equal(staleConnection.error,'PEER_CONNECTION_MISMATCH');report.wrongConnection=staleConnection;
    await revoke();report.revoked=await client('POST','/v0/observe/deep',readBody,403);assert.equal(report.revoked.error,'PEER_AUTHORITY_REVOKED');await detach();
    await mode(server,'OPERATE');await mode(client,'OPERATE');
    const fingerprint=await server('GET','/v0/world/fingerprint?category=DEBUG');
    serverArm=await server('POST','/v0/debug/arm',{namespaces:['world'],ttlMs:60000,worldFingerprint:fingerprint.worldFingerprint});
    await issue([...reads,'debug.write','debug.world'],serverArm);await attach();
    clientArm=await client('POST','/v0/debug/arm',{namespaces:['world'],ttlMs:60000,worldFingerprint:fingerprint.worldFingerprint});
    nativeConsole('forceload add 0 0');
    const query={category:'DEBUG',perspective:'server_authoritative',domains:['blocks'],selector:{blocks:[{x:8,y:100,z:8}]},budgets:{maxResponseBytes:30000}};
    original=await until(()=>client('POST','/v0/observe/deep',query),value=>value.server?.blocks?.[0]?.available,'owned fixture chunk');
    const ref=value=>{const found=value.resourceRevisionRefs.find(item=>item.resourceType==='block'&&item.resourceKey.endsWith('@8,100,8'));assert(found?.mutationPreconditionEligible);return found;};
    const body={operation:'world.block.set',x:8,y:100,z:8,worldFingerprint:fingerprint.worldFingerprint,blockId:original.server.blocks[0].blockId==='minecraft:stone'?'minecraft:glass':'minecraft:stone',expectedResourceVersion:ref(original)};
    const headers={'x-mcp-debug-arm':clientArm.debugArmId};
    report.mutation=await client('POST','/v0/debug/mutations',body,200,headers);latest=report.mutation;
    assert.equal(latest.evidenceAuthority,'DEBUG_PRIVILEGED');assert.equal(latest.gameplayEvidence,false);
    const after=await client('POST','/v0/observe/deep',query);assert.equal(after.server.blocks[0].blockId,body.blockId);
    report.stale=await client('POST','/v0/debug/mutations',body,409,headers);assert.equal(report.stale.error,'STALE_RESOURCE_REVISION');
    report.restore=await client('POST','/v0/debug/mutations',{...body,blockId:original.server.blocks[0].blockId,properties:original.server.blocks[0].properties,expectedResourceVersion:ref(after)},200,headers);latest=null;
    nativeConsole('deop OptionalTest');
    report.deop=await until(async()=>{try{return await client('POST','/v0/observe/deep',readBody,403);}catch(error){return {waiting:String(error)};}},value=>value.error==='PEER_AUTHORITY_REVOKED','native OP revoked at receiver');
    assert.equal((await client('GET','/v0/control/status')).takeoverActive,false);
    assert.equal((await server('GET','/v0/control/status')).takeoverActive,false);
    report.clientInput=await client('GET','/v0/input/state');assert.equal(report.clientInput.pressedKeyCount,0);assert.equal(report.clientInput.pressedButtonCount,0);
    report.noInputLease=true;
    return report;
  } finally {
    // Last known committed block is restored through the owner if a subsequent peer check failed.
    if(latest&&original&&serverArm){
      const readback=await server('POST','/v0/observe/deep',{category:'DEBUG',perspective:'server_authoritative',domains:['blocks'],selector:{blocks:[{x:8,y:100,z:8}]}});
      const fp=await server('GET','/v0/world/fingerprint?category=DEBUG');
      await server('POST','/v0/debug/mutations',{operation:'world.block.set',x:8,y:100,z:8,worldFingerprint:fp.worldFingerprint,blockId:original.server.blocks[0].blockId,properties:original.server.blocks[0].properties,expectedResourceVersion:readback.resourceRevisionRefs.find(item=>item.resourceType==='block'&&item.resourceKey.endsWith('@8,100,8'))},200,{'x-mcp-debug-arm':serverArm.debugArmId});
    }
    try {await revoke();} finally {await detach();}
    if(clientArm)await client('POST','/v0/debug/disarm');
    if(serverArm)await server('POST','/v0/debug/disarm');
    await mode(client,'READ');await mode(server,'READ');
    nativeConsole('deop OptionalTest');nativeConsole('forceload remove 0 0');
  }
}
