import assert from 'node:assert/strict';

export async function consumerTitleReadiness({api,check,actionSamples}) {
 await check('feedback readiness: matching Title Screen needs a fresh interactive target without an input Lease',async()=>{
  const before=await api('GET','/v0/input/state');assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
  const condition={type:'screen',category:'USER',classContains:'TitleScreen',ready:true,selector:{translationKey:'menu.singleplayer'}};
  const result=await api('POST','/v0/wait/until',{condition,timeoutMs:5000});assert.equal(result.passed,true);assert.equal(result.evidence.pageReady,true);assert.equal(result.evidence.overlayIdentity,0);
  await api('POST','/v0/wait/until',{condition:{...condition,selector:{nodeId:'stale:missing'}},timeoutMs:100},'WAIT_TIMEOUT');
  await api('POST','/v0/assert',{condition:{type:'block',x:0,y:80,z:0,available:true,blockId:'minecraft:air'}},'ASSERTION_FAILED');
  const unknown=await api('GET','/v0/world/block?x=0&y=80&z=0');assert.equal(unknown.available,false);assert.equal(unknown.reason,'client_world_unavailable');assert(!('block'in unknown));assert.equal(unknown.chunkLoadRequested,false);
  const after=await api('GET','/v0/input/state');assert.equal(after.inputDispatchSequence,before.inputDispatchSequence);actionSamples.push({case:'feedback_title_ready',result,unknownWorld:unknown,inputDispatchDelta:0});
 });
}

export async function consumerWorldReadiness({api,acquire,release,check,actionSamples}) {
 await check('feedback readiness: block Wait distinguishes real air from unknown and unloaded without force loading',async()=>{
  const original=await api('GET','/v0/player');assert.equal(original.available,true);
  const query={x:Math.floor(original.x),y:Math.floor(original.y)+1,z:Math.floor(original.z)};
  const air=await api('POST','/v0/wait/until',{condition:{type:'block',...query,available:true,blockId:'minecraft:air'},timeoutMs:5000});assert.equal(air.passed,true);assert.equal(air.evidence.available,true);assert.equal(air.evidence.chunkLoadRequested,false);
  const far={x:1000000,y:80,z:1000000};const unloaded=await api('GET',`/v0/world/block?category=DEBUG&x=${far.x}&y=${far.y}&z=${far.z}`);assert.equal(unloaded.available,false);assert.equal(unloaded.chunkLoadRequested,false);
  await api('POST','/v0/wait/until',{condition:{type:'block',category:'DEBUG',...far,available:true},timeoutMs:150},'WAIT_TIMEOUT');
  await api('POST','/v0/assert',{condition:{type:'block',category:'DEBUG',...far,available:true,blockId:'minecraft:air'}},'ASSERTION_FAILED');
  const after=await api('GET',`/v0/world/block?category=DEBUG&x=${far.x}&y=${far.y}&z=${far.z}`);assert.equal(after.available,false);assert.equal(after.chunkLoadRequested,false);
  actionSamples.push({case:'feedback_block_readiness',air,unloaded,stillUnloaded:after,loadingRequested:false});
 });
 await check('feedback readiness: explicit teleport Arrange waits for the destination client block instead of only player coordinates',async()=>{
  const original=await api('GET','/v0/player');const destination={x:Math.floor(original.x)+512,y:Math.floor(original.y)+1,z:Math.floor(original.z)+512};
  await acquire();
  try {
   const [receipt,ready]=await Promise.all([
    api('POST','/v0/command/player',{command:`tp @s ${destination.x+.5} ${destination.y} ${destination.z+.5}`}),
    api('POST','/v0/wait/until',{condition:{type:'block',...destination,available:true,blockId:'minecraft:air'},timeoutMs:15000})
   ]);
   assert.equal(receipt.effectConfirmed,false);assert.equal(ready.evidence.available,true);assert.equal(ready.evidence.dataCategory,'USER');assert.equal(ready.evidence.chunkLoadRequested,false);assert.equal(ready.evidence.authority,'CLIENT_KNOWN');
   actionSamples.push({case:'feedback_destination_ready',ready,arrangeAuthority:'diagnostic_native_command',readDoesNotLoad:true});
  } finally {
   try{await api('POST','/v0/command/player',{command:`tp @s ${original.x} ${original.y} ${original.z} ${original.yaw} ${original.pitch}`});}finally{await release();}
  }
 });
}
