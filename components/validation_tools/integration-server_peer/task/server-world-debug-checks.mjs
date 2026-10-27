import assert from 'node:assert/strict';

// Preparation uses only this runner's owned native console. It is fixture evidence, not a Runtime load request.
export async function checkServerWorldDebug({call,mcp,child,until}) {
  // Pace this dense negative-case sequence below the production 2/s DEBUG refill; do not disable it.
  let lastRequest=0;
  const pace=async()=>{const delay=550-(Date.now()-lastRequest);if(delay>0)await new Promise(done=>setTimeout(done,delay));lastRequest=Date.now();};
  const nativeCall=call;call=async(...args)=>{await pace();return nativeCall(...args);};
  const tool=async(name,args)=>{await pace();const reply=await mcp.callTool({name,arguments:args});assert(!reply.isError,JSON.stringify(reply));return reply.structuredContent.data;};
  const mode=async value=>{const current=await call('GET','/v0/control/mode');return call('POST','/v0/control/mode',{mode:value,expectedModeVersion:current.modeVersion});};
  const pos={x:8,y:100,z:8};
  const blockRef=snapshot=>{const value=snapshot.resourceRevisionRefs.find(ref=>ref.resourceType==='block'&&ref.resourceKey.endsWith('@8,100,8'));assert(value?.mutationPreconditionEligible);return value;};
  const query={category:'DEBUG',perspective:'server_authoritative',domains:['world','blocks'],selector:{blocks:[pos]}};
  await mode('READ');
  const caps=await tool('minecraft_debug',{action:'capabilities'});assert.equal(caps.onlinePlayerRequired,false);assert(caps.operations.some(item=>item.operation==='entity.spawn'));assert(caps.operations.some(item=>item.operation==='player.inventory.slot.set'));
  child.stdin.write('forceload add 0 0\n');
  let before=await until(()=>tool('minecraft_deep_observe',query),v=>v.server.blocks[0].available,'fixture loaded chunk');
  assert.equal(before.server.world.onlinePlayerCount,0);assert.equal(before.metadata.readEffects,'none');assert.equal(before.dataCategory,'DEBUG');
  const fp=await call('GET','/v0/world/fingerprint?category=DEBUG');
  const mutation={operation:'world.block.set',...pos,blockId:before.server.blocks[0].blockId==='minecraft:stone'?'minecraft:glass':'minecraft:stone',worldFingerprint:fp.worldFingerprint,expectedResourceVersion:blockRef(before)};
  assert.equal((await call('POST','/v0/observe/deep',{...query,category:'USER'},409)).error,'WORLD_USER_PERCEPTION_UNAVAILABLE');
  await call('POST','/v0/debug/mutations',mutation,409); // Missing Arm/mode cannot mutate.
  const arm=await tool('minecraft_debug_arm',{action:'arm',namespaces:['world'],ttlMs:60000});
  await call('POST','/v0/debug/mutations',mutation,409,{'x-mcp-debug-arm':arm.debugArmId}); // READ still cannot write.
  assert.equal((await tool('minecraft_deep_observe',query)).server.blocks[0].blockId,before.server.blocks[0].blockId);
  await mode('OPERATE');
  const result=await tool('minecraft_debug',{action:'mutate',mutation,debugArmId:arm.debugArmId});
  assert.equal(result.after.blockId,mutation.blockId);assert.equal(result.gameplayEvidence,false);assert.equal(result.evidenceAuthority,'DEBUG_PRIVILEGED');assert(result.afterResourceVersion.revision>result.beforeResourceVersion.revision);
  const condition={type:'provider',category:'DEBUG',providerId:'minecraft:server/world/deep',params:{category:'DEBUG',perspective:'server_authoritative',domains:['blocks'],selector:{blocks:[pos]}},expected:{data:{server:{blocks:[{...before.server.blocks[0],blockId:mutation.blockId,properties:{}}]}}}};
  const waited=await tool('minecraft_wait',{condition,timeoutMs:5000});assert.equal(waited.passed,true);
  const asserted=await tool('minecraft_assert',{condition});assert.equal(asserted.passed,true);
  assert.equal(asserted.evidence.data.server.blocks[0].blockId,mutation.blockId);
  const wrongCondition={...condition,expected:{data:{server:{blocks:[{...before.server.blocks[0],blockId:'minecraft:diamond_block',properties:{}}]}}}};
  await pace();
  const rejected=await mcp.callTool({name:'minecraft_assert',arguments:{condition:wrongCondition}});
  assert.equal(rejected.isError,true);assert.equal(rejected.structuredContent.error.code,'ASSERTION_FAILED');
  const stale=await call('POST','/v0/debug/mutations',mutation,409,{'x-mcp-debug-arm':arm.debugArmId});assert.equal(stale.error,'STALE_RESOURCE_REVISION');assert.equal(stale.actualRevision,result.afterResourceVersion.revision);
  const wrong={...mutation,expectedResourceVersion:result.afterResourceVersion,expectedBlockId:'minecraft:diamond_block'};
  assert.equal((await call('POST','/v0/debug/mutations',wrong,409,{'x-mcp-debug-arm':arm.debugArmId})).error,'VALUE_PRECONDITION_FAILED');
  const far={x:20000000,y:100,z:20000000};
  const unloaded=await tool('minecraft_deep_observe',{...query,selector:{blocks:[far]}});
  assert.equal(unloaded.server.blocks[0].available,false);assert.equal(unloaded.server.blocks[0].reason,'NOT_LOADED');assert.equal(unloaded.resourceRevisionRefs.filter(ref=>ref.resourceType==='block').length,0);
  await call('POST','/v0/debug/mutations',{...mutation,...far},409,{'x-mcp-debug-arm':arm.debugArmId});
  const unloadedAfter=await tool('minecraft_deep_observe',{...query,selector:{blocks:[far]}});assert.equal(unloadedAfter.server.blocks[0].available,false);assert.equal(unloadedAfter.server.blocks[0].reason,'NOT_LOADED');
  const after=await tool('minecraft_deep_observe',query);
  const restore={...mutation,blockId:before.server.blocks[0].blockId,properties:before.server.blocks[0].properties,expectedResourceVersion:blockRef(after)};
  const batch=await tool('minecraft_debug',{action:'batch',items:[restore],debugArmId:arm.debugArmId,waitForCompletion:true});
  assert.equal(batch.state,'completed',JSON.stringify(batch));assert(!Object.hasOwn(batch,'worldEffectConfirmed'),'DEBUG batch must not inherit console unconfirmed-command metadata');
  const restored=await tool('minecraft_deep_observe',query);assert.equal(restored.server.blocks[0].blockId,before.server.blocks[0].blockId);assert.deepEqual(restored.server.blocks[0].properties,before.server.blocks[0].properties);
  // Exercise the actual native property registry, not just whole-block replacement.
  const firstAxis=restored.server.blocks[0].blockId==='minecraft:oak_log'&&restored.server.blocks[0].properties.axis==='x'?'y':'x';
  const secondAxis=['x','y','z'].find(axis=>axis!==firstAxis&&!(restored.server.blocks[0].blockId==='minecraft:oak_log'&&restored.server.blocks[0].properties.axis===axis));
  const log=await tool('minecraft_debug',{action:'mutate',mutation:{...mutation,blockId:'minecraft:oak_log',properties:{axis:firstAxis},expectedResourceVersion:blockRef(restored)},debugArmId:arm.debugArmId});
  assert.equal(log.after.properties.axis,firstAxis);
  const propertyOnly={...mutation,blockId:'minecraft:oak_log',properties:{axis:secondAxis},expectedProperties:{axis:firstAxis},expectedResourceVersion:log.afterResourceVersion};
  assert.equal((await call('POST','/v0/debug/mutations',{...propertyOnly,properties:{axis:'invalid_axis'}},400,{'x-mcp-debug-arm':arm.debugArmId})).error,'INVALID_BLOCK_PROPERTY_VALUE');
  const oriented=await tool('minecraft_debug',{action:'mutate',mutation:propertyOnly,debugArmId:arm.debugArmId});assert.equal(oriented.after.properties.axis,secondAxis);
  const propertyRestore=await tool('minecraft_debug',{action:'mutate',mutation:{...restore,expectedResourceVersion:oriented.afterResourceVersion},debugArmId:arm.debugArmId});
  assert.equal(propertyRestore.after.blockId,before.server.blocks[0].blockId);assert.deepEqual(propertyRestore.after.properties,before.server.blocks[0].properties);
  const catalogue = await checkDedicatedCatalogue({call,tool,child,until,query,fp,mode});
  const evidence=await call('GET','/v0/debug/evidence');assert(evidence.lastDebugMutationSequence>0);
  await tool('minecraft_debug_arm',{action:'disarm'});
  await call('POST','/v0/debug/mutations',{...mutation,expectedResourceVersion:propertyRestore.afterResourceVersion},409,{'x-mcp-debug-arm':arm.debugArmId});
  await mode('READ');
  const lease=await call('GET','/v0/control/status');assert.equal(lease.takeoverActive,false);
  child.stdin.write('forceload remove 0 0\n');
  return {catalogue,before,result,providerConditions:{waited,asserted,rejected:rejected.structuredContent},stale,unloaded,unloadedAfter,restored,propertyRestore,evidence,lease,checks:{zeroPlayers:true,readNoArmOrLease:true,independentModeAndArm:true,typedMutationAndRevision:true,nestedMcpProviderConditions:true,staleAndValueDenied:true,noAutomaticLoad:true,debugBatchAndRestore:true,nativePropertiesAndInvalidValue:true,diagnosticEvidence:true,disarmDenied:true}};
}


async function checkDedicatedCatalogue({call,tool,child,until,query,fp,mode}) {
  await tool('minecraft_debug_arm',{action:'disarm'});
  const arm=await tool('minecraft_debug_arm',{action:'arm',namespaces:['world','entity','block_entity'],ttlMs:60000});
  const deep=extra=>tool('minecraft_deep_observe',{...query,...extra});
  const mutate=body=>tool('minecraft_debug',{action:'mutate',mutation:{worldFingerprint:fp.worldFingerprint,...body},debugArmId:arm.debugArmId});
  const full=await deep({domains:['world','entities','chunks','block_entities'],selector:{anchor:{x:8,y:100,z:8},chunkRadius:0,entityRadius:16,entityChunk:{x:0,z:0}}});
  assert.equal(full.server.world.onlinePlayerCount,0);assert(full.server.world.dimensions.length>=3);
  assert.equal(full.server.chunkLoadRequested,false);
  const rule=full.server.world.environment.rules.find(item=>item.typedWritable&&typeof item.value==='boolean'&&/keep.?inventory/i.test(item.ruleId));assert(rule);
  const ruleRef=full.resourceRevisionRefs.find(ref=>ref.resourceType==='world_rule'&&ref.resourceKey===rule.key);assert(ruleRef);
  const changed=await mutate({operation:'world.rule.set',ruleId:rule.ruleId,value:!rule.value,expectedValue:rule.value,expectedResourceVersion:ruleRef});
  const ruleRead=await deep({domains:['world']});
  assert.equal(ruleRead.server.world.environment.rules.find(item=>item.key===rule.key).value,!rule.value);
  const restoredRule=await mutate({operation:'world.rule.set',ruleId:rule.ruleId,value:rule.value,expectedValue:!rule.value,expectedResourceVersion:changed.afterResourceVersion});
  assert.equal(restoredRule.after.value,rule.value);
  const players=await call('POST','/v0/observe/deep',{...query,domains:['player']},400);assert.equal(players.error,'EXPLICIT_PLAYER_REQUIRED');
  const nether=await deep({domains:['world','chunks'],selector:{dimension:'minecraft:the_nether',anchor:{x:0,y:64,z:0},chunkRadius:0}});
  assert.equal(nether.server.dimension,'minecraft:the_nether');assert.equal(nether.server.chunkLoadRequested,false);
  const membership=await deep({domains:['entities'],selector:{anchor:{x:8,y:100,z:8},entityChunk:{x:0,z:0}}});
  const entityRef=membership.resourceRevisionRefs.find(ref=>ref.resourceType==='entity_registry');assert(entityRef);
  const spawned=await mutate({operation:'entity.spawn',value:{entityType:'minecraft:marker',position:{x:8,y:100,z:8}},expectedResourceVersion:entityRef});
  const uuid=spawned.createdEntity.uuid;assert(uuid);
  const observed=await deep({domains:['entities'],selector:{entityTargets:[{dimension:'minecraft:overworld',entityUuid:uuid}]}});
  const ref=observed.resourceRevisionRefs.find(ref=>ref.resourceType==='entity'&&ref.resourceKey===uuid);assert(ref);
  const removed=await mutate({operation:'entity.remove',entityUuid:uuid,sourceDimension:'minecraft:overworld',expectedResourceVersion:ref,expectedEntityType:'minecraft:marker'});
  assert.equal(removed.gameplayEvidence,false);
  const originalView=await deep({domains:['blocks','block_entities'],selector:{blocks:[{x:8,y:100,z:8}]}});
  const original=originalView.server.blocks[0];
  assert.equal(originalView.server.blockEntities.length,0,'Never replace a fixture containing unknown persistent block-entity data');
  child.stdin.write('setblock 8 100 8 minecraft:barrel\n');
  const containerQuery={domains:['block_entities'],selector:{blocks:[{x:8,y:100,z:8}]},includeSerializedBlockEntities:true};
  let container,slotChanged,slotRestored;
  try{
    container=await until(()=>deep(containerQuery),value=>value.server?.blockEntities?.length===1,'native barrel fixture');
    const containerRef=value=>value.resourceRevisionRefs.find(item=>item.resourceType==='block_entity_serialized'&&item.resourceKey.endsWith('@8,100,8'));
    assert(containerRef(container));
    slotChanged=await mutate({operation:'block_entity.slot.set',x:8,y:100,z:8,slot:0,itemId:'minecraft:stone',count:7,expectedCount:0,expectedBlockEntityType:'minecraft:barrel',expectedResourceVersion:containerRef(container)});
    const actual=await deep(containerQuery);assert.notDeepEqual(actual.server.blockEntities[0].serializedState,container.server.blockEntities[0].serializedState);
    slotRestored=await mutate({operation:'block_entity.slot.set',x:8,y:100,z:8,slot:0,count:0,expectedCount:7,expectedItemId:'minecraft:stone',expectedBlockEntityType:'minecraft:barrel',expectedResourceVersion:containerRef(actual)});
    assert.deepEqual((await deep(containerQuery)).server.blockEntities[0].serializedState,container.server.blockEntities[0].serializedState);
  }finally{
    const current=await deep({domains:['blocks'],selector:{blocks:[{x:8,y:100,z:8}]}});
    await mutate({operation:'world.block.set',x:8,y:100,z:8,blockId:original.blockId,properties:original.properties,expectedResourceVersion:current.resourceRevisionRefs.find(item=>item.resourceType==='block'&&item.resourceKey.endsWith('@8,100,8'))});
  }
  const provider=await tool('minecraft_get_state',{kind:'state_frame',category:'DEBUG',reads:[{providerId:'minecraft:server/world/deep',query:{category:'DEBUG',perspective:'server_authoritative',domains:['world']}}]});
  assert.equal(provider.dataCategory,'DEBUG');
  const persisted=await tool('minecraft_read_storage',{domain:'world'});
  assert.equal(persisted.dataSource,'PERSISTED');assert.equal(persisted.writeImplemented,false);
  await tool('minecraft_debug_arm',{action:'disarm'});await mode('READ');
  return {full,changed,restoredRule,players,nether,spawned,observed,removed,container,slotChanged,slotRestored,provider,persisted};
}
