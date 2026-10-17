import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import {resolve} from 'node:path';

export async function consumerHitFeedback({api,repo,instance,target,acquire,release,leaseId,check,actionSamples}) {
 const original=await api('GET','/v0/player');assert.equal(original.available,true);await acquire();
 const command=text=>api('POST','/v0/command/player',{command:text});
 const verify=operation=>{
  assert.equal(operation.state,'failed',JSON.stringify(operation));assert.equal(operation.errorCode,'WORLD_TARGET_NOT_HIT');
  const hit=operation.errorDetails?.actualHit;assert(hit,JSON.stringify(operation));assert.equal(hit.kind,'block');assert.equal(hit.dataCategory,'USER');assert.equal(hit.authority,'CLIENT_KNOWN');assert(['native_interaction_pick','candidate_outline_ray'].includes(hit.source));
  assert.equal(hit.block.x,0);assert.equal(hit.block.y,81);assert.equal(hit.block.z,2);assert.equal(hit.face,'north');assert(hit.distance>0&&hit.distance<=32);assert(Number.isFinite(hit.position.x)&&Number.isFinite(hit.position.y)&&Number.isFinite(hit.position.z));for(const field of ['guard','nbt','inventory','uuid'])assert(!(field in hit));return hit;
 };
 try {
  await command('gamemode creative @s');await command('fill -2 79 -2 2 79 6 minecraft:stone');await command('tp @s 0.5 80 0.5 0 0');await command('setblock 0 81 2 minecraft:stone');await command('setblock 0 81 4 minecraft:stone');
  const available=await api('POST','/v0/wait/until',{condition:{type:'block',x:0,y:81,z:4,available:true,blockId:'minecraft:stone'},timeoutMs:5000});const targetRef=available.evidence.targetRef;assert(targetRef);
  const steps=[{type:'world.action',targetRef,action:'use'}];
  await check('feedback hit: occluded USER target reports the actual bounded native obstruction without dispatching a press',async()=>{
   const before=await api('GET','/v0/input/state');const accepted=await api('POST','/v0/pipelines',{steps,timeoutMs:12000});const operation=await api('POST',`/v0/operations/${accepted.operationId}/wait`,{timeoutMs:12000});const hit=verify(operation);
   const after=await api('GET','/v0/input/state');assert.equal(after.pressedKeyCount,0);assert.equal(after.pressedButtonCount,0);
   // The production failure path always calls releaseAllInput once, even with no held input;
   // that cleanup increments the same sequence. Any extra camera/key/button dispatch fails.
   assert.equal(after.inputDispatchSequence-before.inputDispatchSequence,1);actionSamples.push({case:'feedback_native_hit',operation,hit,beforeInput:before,afterInput:after,inputDispatchDelta:1,cleanupDispatches:1,playerInputDispatches:0});
  });
  await check('feedback hit: actual MCP preserves failed Operation diagnostics under the existing explicit Lease',async()=>{
   const require=createRequire(resolve(repo,'components/companion/package.json'));const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
   const env=Object.fromEntries(Object.entries(process.env).filter(([,v])=>typeof v==='string'));for(const key of ['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE'])delete env[key];env.MCP_MINECRAFT_INSTANCE_DIR=instance;env.MCP_MINECRAFT_TARGET=target;
   const client=new Client({name:'hit-feedback-consumer',version:'0.0.1'}),transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
   try {await client.connect(transport);const before=await api('GET','/v0/input/state');const value=await client.callTool({name:'minecraft_run_input_pipeline',arguments:{steps,leaseId:leaseId(),waitForCompletion:true}});assert(!value.isError,JSON.stringify(value));const operation=value.structuredContent.data;const hit=verify(operation);const after=await api('GET','/v0/input/state');assert.equal(after.inputDispatchSequence-before.inputDispatchSequence,1);assert.equal(after.pressedKeyCount,0);assert.equal(after.pressedButtonCount,0);actionSamples.push({case:'feedback_mcp_hit',operation,hit,beforeInput:before,afterInput:after,realMcp:true,cleanupDispatches:1,playerInputDispatches:0});}
   finally {try{await client.close();}finally{await transport.close();}}
  });
 } finally {
  try {await command('setblock 0 81 2 minecraft:air');await command('setblock 0 81 4 minecraft:air');await command(`tp @s ${original.x} ${original.y} ${original.z} ${original.yaw} ${original.pitch}`);await command(`gamemode ${original.gameMode} @s`);}
  finally {await release();}
 }
 const input=await api('GET','/v0/input/state');assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);
}
