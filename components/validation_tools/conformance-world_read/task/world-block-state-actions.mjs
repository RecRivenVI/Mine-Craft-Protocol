import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

// Only the immutable runner-owned world is used. All mutations below are DEBUG_PRIVILEGED,
// not gameplay acceptance; no command, NBT, filesystem write or direct business setter is an Act.
export async function worldBlockStateActions({api,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples,setArm,repo,instance,target,enterWorld=false}) {
  if(enterWorld){
    await acquire();await click('menu.singleplayer','Singleplayer');
    const tree=await wait(t=>t.screenClass.endsWith('SelectWorldScreen'),'select immutable debug world');
    const world=nodes(tree).find(n=>n.worldName==='World-Read-Alpha');assert(world);
    await clickNode(world);await click('selectWorld.select','Play Selected World');await wait(t=>!t.screenClass,'world entered',150000);await release();
  }
  await mode('OPERATE');
  const player=await api('GET','/v0/player?category=DEBUG'),at={x:Math.floor(player.x)+4,y:Math.floor(player.y)+2,z:Math.floor(player.z)+4};
  const fingerprint=(await api('GET','/v0/world/fingerprint?category=DEBUG')).worldFingerprint;
  const beforeInput=await api('GET','/v0/input/state');
  async function read(position=at){
    const snapshot=await api('POST','/v0/observe/deep',{category:'DEBUG',perspective:'server_authoritative',domains:['blocks'],selector:{blocks:[position]}});
    const state=snapshot.server.blocks[0],ref=snapshot.resourceRevisionRefs.find(r=>r.resourceType==='block');
    assert.equal(state.loadRequested,false);return {state,ref};
  }
  async function arm(){setArm((await api('POST','/v0/debug/arm',{worldFingerprint:fingerprint,namespaces:['world'],ttlMs:60000})).debugArmId);}
  async function mutate(body,error,headers){
    // Request pacing only: never used to decide whether a game effect happened.
    await new Promise(r=>setTimeout(r,650));
    return api('POST','/v0/debug/mutations',{operation:'world.block.set',worldFingerprint:fingerprint,...at,...body},error,headers);
  }
  async function request(blockId,properties,extra={}){const current=await read();assert(current.ref);return {...at,operation:'world.block.set',worldFingerprint:fingerprint,expectedResourceVersion:current.ref,expectedBlockId:current.state.blockId,expectedProperties:current.state.properties,blockId,...(properties===undefined?{}:{properties}),...extra};}
  async function unchanged(before){const after=await read();assert.deepEqual(after.state,before.state);assert.deepEqual(after.ref,before.ref);return after;}
  const original=await read();assert.equal(original.state.blockId,'minecraft:air','Only owned empty air may become this fixture');
  try {
    await arm();
    await check('WD-05 native typed BlockState replacement and same-type property mutation',async()=>{
      const placed=await mutate(await request('minecraft:oak_log',{axis:'x'}));
      assert.equal(placed.after.properties.axis,'x');assert.equal(placed.gameplayEvidence,false);assert.equal(placed.evidenceContaminated,true);assert.equal(placed.storageAccessed,false);assert.match(placed.sideEffects,/native_neighbor_updates/);
      assert(placed.afterResourceVersion.revision>placed.beforeResourceVersion.revision);
      const changed=await mutate(await request('minecraft:oak_log',{axis:'z'}));assert.equal(changed.before.properties.axis,'x');assert.equal(changed.after.properties.axis,'z');
      actionSamples.push({case:'typed_block_state_same_type',evidenceAuthority:'DEBUG_PRIVILEGED',placed,changed});
    });
    await check('WD-05 invalid properties and stale value/version reject before any native change',async()=>{
      for(const [fields,error] of [
        [{properties:{missing:'true'}},'UNKNOWN_BLOCK_PROPERTY'],
        [{properties:{axis:'bad_value'}},'INVALID_BLOCK_PROPERTY_VALUE'],
        [{properties:{axis:'x',missing:'true'}},'UNKNOWN_BLOCK_PROPERTY'],
        [{properties:{axis:1}},'INVALID_DEBUG_BLOCK_STATE'],
        [{properties:{axis:'x]{Command:run}'}},'INVALID_DEBUG_BLOCK_STATE'],
        [{expectedProperties:{axis:'x'},properties:{axis:'x'}},'VALUE_PRECONDITION_FAILED'],
        [{expectedProperties:{missing:'true'},properties:{axis:'x'}},'VALUE_PRECONDITION_FAILED']
      ]){const before=await read();const rejected=await mutate(await request('minecraft:oak_log',{axis:'x'},fields),error);await unchanged(before);actionSamples.push({case:'typed_block_state_refusal',error:rejected.error});}
      const stale=await request('minecraft:oak_log',{axis:'x'});await mutate(stale);const before=await read();await mutate(stale,'STALE_RESOURCE_REVISION');await unchanged(before);
      await mutate(await request('minecraft:oak_log'), 'DEBUG_NO_STATE_CHANGE');await unchanged(before);
    });
    await check('WD-05 native integer boolean and enum properties preserve omitted same-type values',async()=>{
      const note=await mutate(await request('minecraft:note_block',{note:'7',powered:'false',instrument:'harp'}));assert.equal(note.after.properties.note,'7');assert.equal(note.after.properties.powered,'false');
      const adjusted=await mutate(await request('minecraft:note_block',{note:'8'}));assert.equal(adjusted.after.properties.instrument,'harp');assert.equal(adjusted.after.properties.powered,'false');
      const slab=await mutate(await request('minecraft:oak_slab',{type:'top',waterlogged:'false'}));assert.equal(slab.after.properties.type,'top');assert.equal(slab.after.properties.waterlogged,'false');
      const before=await read();await mutate(await request('minecraft:oak_slab',{type:'invalid'}),'INVALID_BLOCK_PROPERTY_VALUE');await unchanged(before);
      actionSamples.push({case:'typed_block_state_native_domains',note,adjusted,slab});
    });
    await check('WD-05 unchanged authority barriers: Arm mode world identity and unloaded resources',async()=>{
      const before=await read(),body=await request('minecraft:oak_log',{axis:'x'});
      await mutate(body,'DEBUG_NOT_ARMED',{'x-mcp-debug-arm':''});await unchanged(before);
      await mode('READ');await mutate(body,'OPERATE_REQUIRED');await mode('OPERATE');await unchanged(before);
      await mutate({...body,worldFingerprint:'0'.repeat(64)},'WORLD_FINGERPRINT_MISMATCH');await unchanged(before);
      const remote={x:30000000,y:80,z:30000000},unloaded=await read(remote);assert.equal(unloaded.state.available,false);
      await mutate({...body,...remote},'TARGET_NOT_LOADED');assert.equal((await read(remote)).state.available,false);await unchanged(before);
    });
    await check('WD-05 actual MCP retains property maps and stale rejection without a player Lease',async()=>{
      const require=createRequire(resolve(repo,'components/companion/package.json'));
      const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
      const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
      const env=Object.fromEntries(Object.entries(process.env).filter(([,v])=>typeof v==='string'));
      for(const name of ['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE'])delete env[name];
      env.MCP_MINECRAFT_INSTANCE_DIR=instance;env.MCP_MINECRAFT_TARGET=target;
      const client=new Client({name:'typed-block-state-consumer',version:'0.0.1'}),transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
      const call=async(name,args)=>{const r=await client.callTool({name,arguments:args});assert(!r.isError,JSON.stringify(r));return r.structuredContent.data;};
      try {
        await client.connect(transport);await call('minecraft_control',{action:'set_mode',mode:'OPERATE'});await call('minecraft_debug_arm',{action:'arm',worldFingerprint:fingerprint,namespaces:['world'],ttlMs:60000});
        const body=await request('minecraft:oak_log',{axis:'x'});const applied=await call('minecraft_debug',{action:'mutate',mutation:body});assert.equal(applied.after.properties.axis,'x');assert.equal(applied.gameplayEvidence,false);
        const before=await read();await new Promise(r=>setTimeout(r,650));
        const stale=await client.callTool({name:'minecraft_debug',arguments:{action:'mutate',mutation:body}});assert.equal(stale.structuredContent.error.code,'STALE_RESOURCE_REVISION');await unchanged(before);
        const mode=await call('minecraft_control',{action:'status'});assert.equal(mode.mode,'OPERATE');assert.equal(mode.takeoverActive,false);
        const lease=await api('GET','/v0/control/status');assert.equal(lease.status,'available');assert.equal(lease.leaseId,undefined);
        actionSamples.push({case:'typed_block_state_actual_mcp',status:'PASS',propertyMapsPreserved:true,noPlayerLease:true});
      }finally{try{await call('minecraft_debug_arm',{action:'disarm'});}finally{await client.close();await transport.close();}}
    });
  }finally{
    await mode('OPERATE');await arm();
    try{const current=await read();if(JSON.stringify(current.state)!==JSON.stringify(original.state)){await mutate(await request(original.state.blockId,original.state.properties));}assert.deepEqual((await read()).state,original.state);}
    finally{await api('POST','/v0/debug/disarm');setArm(undefined);await mode('READ');}
  }
  await check('WD-05 cleanup restores the original block and dispatches no player input',async()=>{
    const after=await api('GET','/v0/input/state');assert.equal(after.pressedKeyCount,0);assert.equal(after.pressedButtonCount,0);assert.equal(after.inputDispatchSequence,beforeInput.inputDispatchSequence);assert.equal((await api('GET','/v0/control/mode')).takeoverActive,false);assert.equal((await api('GET','/v0/control/status')).status,'available');
  });
}
