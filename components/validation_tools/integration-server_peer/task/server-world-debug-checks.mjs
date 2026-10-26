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
  const query={category:'DEBUG',perspective:'server_authoritative',domains:['world','blocks'],selector:{blocks:[pos]}};
  await mode('READ');
  const caps=await tool('minecraft_debug',{action:'capabilities'});assert.equal(caps.onlinePlayerRequired,false);assert.equal(caps.operations.length,1);
  child.stdin.write('forceload add 0 0\n');
  let before=await until(()=>tool('minecraft_deep_observe',query),v=>v.server.blocks[0].available,'fixture loaded chunk');
  assert.equal(before.server.world.onlinePlayerCount,0);assert.equal(before.metadata.readEffects,'none');assert.equal(before.dataCategory,'DEBUG');
  const fp=await call('GET','/v0/world/fingerprint?category=DEBUG');
  const mutation={operation:'world.block.set',...pos,blockId:before.server.blocks[0].blockId==='minecraft:stone'?'minecraft:glass':'minecraft:stone',worldFingerprint:fp.worldFingerprint,expectedResourceVersion:before.resourceRevisionRefs[0]};
  assert.equal((await call('POST','/v0/observe/deep',{...query,category:'USER'},409)).error,'WORLD_USER_PERCEPTION_UNAVAILABLE');
  await call('POST','/v0/debug/mutations',mutation,409); // Missing Arm/mode cannot mutate.
  const arm=await tool('minecraft_debug_arm',{action:'arm',namespaces:['world'],ttlMs:60000});
  await call('POST','/v0/debug/mutations',mutation,409,{'x-mcp-debug-arm':arm.debugArmId}); // READ still cannot write.
  assert.equal((await tool('minecraft_deep_observe',query)).server.blocks[0].blockId,before.server.blocks[0].blockId);
  await mode('OPERATE');
  const result=await tool('minecraft_debug',{action:'mutate',mutation,debugArmId:arm.debugArmId});
  assert.equal(result.after.blockId,mutation.blockId);assert.equal(result.gameplayEvidence,false);assert.equal(result.evidenceAuthority,'DEBUG_PRIVILEGED');assert(result.afterResourceVersion.revision>result.beforeResourceVersion.revision);
  const stale=await call('POST','/v0/debug/mutations',mutation,409,{'x-mcp-debug-arm':arm.debugArmId});assert.equal(stale.error,'STALE_RESOURCE_REVISION');assert.equal(stale.actualRevision,result.afterResourceVersion.revision);
  const wrong={...mutation,expectedResourceVersion:result.afterResourceVersion,expectedBlockId:'minecraft:diamond_block'};
  assert.equal((await call('POST','/v0/debug/mutations',wrong,409,{'x-mcp-debug-arm':arm.debugArmId})).error,'VALUE_PRECONDITION_FAILED');
  const far={x:20000000,y:100,z:20000000};
  const unloaded=await tool('minecraft_deep_observe',{...query,selector:{blocks:[far]}});
  assert.equal(unloaded.server.blocks[0].available,false);assert.equal(unloaded.server.blocks[0].reason,'NOT_LOADED');assert.equal(unloaded.resourceRevisionRefs.length,0);
  await call('POST','/v0/debug/mutations',{...mutation,...far},409,{'x-mcp-debug-arm':arm.debugArmId});
  const unloadedAfter=await tool('minecraft_deep_observe',{...query,selector:{blocks:[far]}});assert.equal(unloadedAfter.server.blocks[0].available,false);assert.equal(unloadedAfter.server.blocks[0].reason,'NOT_LOADED');
  const after=await tool('minecraft_deep_observe',query);
  const restore={...mutation,blockId:before.server.blocks[0].blockId,properties:before.server.blocks[0].properties,expectedResourceVersion:after.resourceRevisionRefs[0]};
  const batch=await tool('minecraft_debug',{action:'batch',items:[restore],debugArmId:arm.debugArmId,waitForCompletion:true});
  assert.equal(batch.state,'completed',JSON.stringify(batch));assert(!Object.hasOwn(batch,'worldEffectConfirmed'),'DEBUG batch must not inherit console unconfirmed-command metadata');
  const restored=await tool('minecraft_deep_observe',query);assert.equal(restored.server.blocks[0].blockId,before.server.blocks[0].blockId);assert.deepEqual(restored.server.blocks[0].properties,before.server.blocks[0].properties);
  // Exercise the actual native property registry, not just whole-block replacement.
  const firstAxis=restored.server.blocks[0].blockId==='minecraft:oak_log'&&restored.server.blocks[0].properties.axis==='x'?'y':'x';
  const secondAxis=['x','y','z'].find(axis=>axis!==firstAxis&&!(restored.server.blocks[0].blockId==='minecraft:oak_log'&&restored.server.blocks[0].properties.axis===axis));
  const log=await tool('minecraft_debug',{action:'mutate',mutation:{...mutation,blockId:'minecraft:oak_log',properties:{axis:firstAxis},expectedResourceVersion:restored.resourceRevisionRefs[0]},debugArmId:arm.debugArmId});
  assert.equal(log.after.properties.axis,firstAxis);
  const propertyOnly={...mutation,blockId:'minecraft:oak_log',properties:{axis:secondAxis},expectedProperties:{axis:firstAxis},expectedResourceVersion:log.afterResourceVersion};
  assert.equal((await call('POST','/v0/debug/mutations',{...propertyOnly,properties:{axis:'invalid_axis'}},400,{'x-mcp-debug-arm':arm.debugArmId})).error,'INVALID_BLOCK_PROPERTY_VALUE');
  const oriented=await tool('minecraft_debug',{action:'mutate',mutation:propertyOnly,debugArmId:arm.debugArmId});assert.equal(oriented.after.properties.axis,secondAxis);
  const propertyRestore=await tool('minecraft_debug',{action:'mutate',mutation:{...restore,expectedResourceVersion:oriented.afterResourceVersion},debugArmId:arm.debugArmId});
  assert.equal(propertyRestore.after.blockId,before.server.blocks[0].blockId);assert.deepEqual(propertyRestore.after.properties,before.server.blocks[0].properties);
  const evidence=await call('GET','/v0/debug/evidence');assert(evidence.lastDebugMutationSequence>0);
  await tool('minecraft_debug_arm',{action:'disarm'});
  await call('POST','/v0/debug/mutations',{...mutation,expectedResourceVersion:propertyRestore.afterResourceVersion},409,{'x-mcp-debug-arm':arm.debugArmId});
  await mode('READ');
  const lease=await call('GET','/v0/control/status');assert.equal(lease.takeoverActive,false);
  child.stdin.write('forceload remove 0 0\n');
  return {before,result,stale,unloaded,unloadedAfter,restored,propertyRestore,evidence,lease,checks:{zeroPlayers:true,readNoArmOrLease:true,independentModeAndArm:true,typedMutationAndRevision:true,staleAndValueDenied:true,noAutomaticLoad:true,debugBatchAndRestore:true,nativePropertiesAndInvalidValue:true,diagnosticEvidence:true,disarmDenied:true}};
}
