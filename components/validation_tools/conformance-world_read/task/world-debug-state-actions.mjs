import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

export function stateView(state, fields, player) {
  return Object.fromEntries(fields.map(field => {
    if(field === 'velocity') {
      const source = player ? state.position : state;
      return [field, Object.fromEntries(['x','y','z'].map(axis => [axis,source[`velocity${axis.toUpperCase()}`]]))];
    }
    assert(Object.hasOwn(state,field),`Native state lacks ${field}`); return [field,state[field]];
  }));
}

export async function worldDebugStateActions({api, target, repo, instance, fixtureInput, mode, acquire, release,
  click, clickNode, wait, nodes, check, actionSamples, setArm}) {
  assert.equal(fixtureInput?.policy,'immutable_baseline_restored'); assert.equal(fixtureInput.target,target);
  const pause = ms => new Promise(done => setTimeout(done,ms)), recovery = [], cleanupErrors = [];
  let held = false, paused = false, entityUuid, fingerprint, lastWrite = 0, failure;
  const tag = `mcp_state_${randomUUID().replaceAll('-','')}`;
  async function tapEscape() {
    const op = await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:256,holdMs:40}],timeoutMs:12000});
    assert.equal((await api('POST',`/v0/operations/${op.operationId}/wait`,{timeoutMs:12000})).state,'completed');
  }
  async function input() {
    const v = await api('GET','/v0/input/state'); assert.equal(v.pressedKeyCount,0); assert.equal(v.pressedButtonCount,0);
    assert.equal(v.inputCleanupPending,false); assert.equal(v.inputCleanupFailed,false);
    assert.equal((await api('GET','/v0/control/status')).status,'available'); return v;
  }
  const native = command => api('POST','/v0/command/player',{command,route:'NATIVE'});
  async function deep(domains) {
    const v = await api('POST','/v0/observe/deep',{category:'DEBUG',perspective:'server_authoritative',domains,selector:{entityRadius:16}});
    assert.equal(v.server.chunkLoadRequested,false); assert.equal(v.server.storageAccessed,false); return v;
  }
  async function read(player) {
    const v = await deep([player?'player':'entities']);
    const state = player ? v.server.player : v.server.entities.find(e => e.uuid===entityUuid); assert(state);
    const key = player ? `${state.uuid}@server_authoritative` : entityUuid;
    const ref = v.resourceRevisionRefs.find(r => r.resourceType===(player?'player':'entity') && r.resourceKey===key);
    assert(ref?.mutationPreconditionEligible); return {state,ref};
  }
  async function arm() {
    setArm((await api('POST','/v0/debug/arm',{worldFingerprint:fingerprint,namespaces:['player','entity'],ttlMs:60000})).debugArmId);
  }
  const body = (player,observed,value,expectedValue) => ({operation:player?'player.state.set':'entity.state.set',
    worldFingerprint:fingerprint,expectedResourceVersion:observed.ref,value,expectedValue,
    ...(!player ? {entityUuid,expectedEntityType:observed.state.type} : {})});
  async function mutate(value,error) {
    const delay = 650-(Date.now()-lastWrite); if(delay>0)await pause(delay); lastWrite=Date.now();
    return api('POST','/v0/debug/mutations',value,error);
  }
  async function chain(player,changed) {
    await arm(); const original = await read(player), fields=Object.keys(changed), old=stateView(original.state,fields,player), beforeInput=await input();
    let receipt;
    const undo = async () => {
      const observed=await read(player), current=stateView(observed.state,fields,player); if(JSON.stringify(current)===JSON.stringify(old))return;
      assert(receipt,'Unknown outcome is preserved'); assert.deepEqual(observed.ref,receipt.afterResourceVersion,'External resource change is preserved');
      assert.deepEqual(current,changed); await mutate(body(player,observed,old,current)); assert.deepEqual(stateView((await read(player)).state,fields,player),old);
    }; recovery.push(undo);
    receipt=await mutate(body(player,original,changed,old));
    assert.equal(receipt.gameplayEvidence,false);assert.equal(receipt.evidenceContaminated,true);assert.equal(receipt.storageAccessed,false);
    const after=await read(player);assert.deepEqual(stateView(after.state,fields,player),changed);
    const stale=await mutate(body(player,original,old,changed),'STALE_RESOURCE_REVISION');assert.equal(stale.actualRevision,after.ref.revision);assert.equal(stale.retrySafe,false);
    const wrong=structuredClone(changed), first=fields[0];wrong[first]=typeof wrong[first]==='boolean'?!wrong[first]:typeof wrong[first]==='number'?wrong[first]+1:{x:0,y:0,z:1};
    await mutate(body(player,after,old,wrong),'VALUE_PRECONDITION_FAILED');assert.deepEqual((await read(player)).ref,after.ref);
    await undo();const afterInput=await input();assert.equal(afterInput.inputDispatchSequence,beforeInput.inputDispatchSequence);
    actionSamples.push({case:'world_debug_state_chain',operation:player?'player.state.set':'entity.state.set',fields,original,receipt,after,stale,restored:await read(player),beforeInput,afterInput,actInputDelta:0});
  }
  async function effectChain(player) {
    await arm();const original=await read(player),effectId='minecraft:speed';assert(!original.state.effects.some(e=>e.id===effectId),'Owned effect starts absent');
    const value={duration:200,amplifier:1,ambient:false,showParticles:false,showIcon:true,hidden:[{duration:600,amplifier:0,ambient:true,showParticles:false,showIcon:false}]};
    const get=v=>{const effect=v.state.effects.find(e=>e.id===effectId);if(!effect)return null;assert.equal(effect.hiddenChainComplete,true);const {id,hiddenChainComplete,...fields}=effect;return fields;};
    const make=(observed,next,expected)=>({operation:player?'player.effect.set':'entity.effect.set',worldFingerprint:fingerprint,expectedResourceVersion:observed.ref,effectId,value:next,expectedValue:expected,...(!player?{entityUuid,expectedEntityType:observed.state.type}:{})});
    let applied;const beforeInput=await input();
    const undo=async()=>{const v=await read(player),current=get(v);if(current===null)return;assert(applied);assert.deepEqual(v.ref,applied.afterResourceVersion);assert.deepEqual(current,value);await mutate(make(v,null,current));assert.equal(get(await read(player)),null);};recovery.push(undo);
    applied=await mutate(make(original,value,null));assert.equal(applied.evidenceContaminated,true);const after=await read(player);assert.deepEqual(get(after),value);
    const stale=await mutate(make(original,null,value),'STALE_RESOURCE_REVISION');assert.equal(stale.actualRevision,after.ref.revision);
    await mutate(make(after,null,{...value,duration:201}),'VALUE_PRECONDITION_FAILED');assert.deepEqual((await read(player)).ref,after.ref);
    await undo();const afterInput=await input();assert.equal(afterInput.inputDispatchSequence,beforeInput.inputDispatchSequence);
    actionSamples.push({case:'world_debug_native_effect_chain',operation:player?'player.effect.set':'entity.effect.set',original,applied,after,stale,restored:await read(player),beforeInput,afterInput,actInputDelta:0});
  }
  async function attributeChain(player) {
    await arm();const original=await read(player),attributes=original.state.attributes;
    assert(Array.isArray(attributes));assert(attributes.every(a=>Array.isArray(a.modifiers)));
    const attribute=attributes.find(a=>a.typedBaseWritable&&a.id.includes(player?'movement_speed':'max_health'));assert(attribute,'Native registered attribute must exist');
    const requested=attribute.base+0.125;assert(requested<=attribute.maximum&&requested>=attribute.minimum);
    const operation=player?'player.attribute.set':'entity.attribute.set',get=v=>v.state.attributes.find(a=>a.id===attribute.id),beforeInput=await input();let applied;
    const make=(observed,value,expectedValue)=>({operation,worldFingerprint:fingerprint,expectedResourceVersion:observed.ref,attributeId:attribute.id,value,expectedValue,...(!player?{entityUuid,expectedEntityType:observed.state.type}:{})});
    const undo=async()=>{const v=await read(player);if(get(v).base===attribute.base)return;
      assert(applied);assert.deepEqual(v.ref,applied.afterResourceVersion);assert.equal(get(v).base,requested);
      await mutate(make(v,attribute.base,requested));assert.equal(get(await read(player)).base,attribute.base);};recovery.push(undo);
    applied=await mutate(make(original,requested,attribute.base));const after=await read(player);assert.equal(get(after).base,requested);
    const stale=await mutate(make(original,attribute.base,requested),'STALE_RESOURCE_REVISION');assert.equal(stale.actualRevision,after.ref.revision);
    await mutate(make(after,attribute.base,requested+1),'VALUE_PRECONDITION_FAILED');
    await mutate(make(after,attribute.maximum+1,requested),'INVALID_DEBUG_STATE');assert.deepEqual((await read(player)).ref,after.ref);
    await undo();const afterInput=await input();assert.equal(afterInput.inputDispatchSequence,beforeInput.inputDispatchSequence);
    actionSamples.push({case:'world_debug_native_attribute_chain',operation,attribute,applied,after,stale,restored:await read(player),beforeInput,afterInput,actInputDelta:0});
  }
  try {
    await acquire();held=true;await click('menu.singleplayer','Singleplayer');
    const list=await wait(t=>t.screenClass.endsWith('SelectWorldScreen'),'state fixed world list');
    const worlds=nodes(list).filter(n=>n.worldName==='World-Read-Alpha');assert.equal(worlds.length,1);await clickNode(worlds[0]);await click('selectWorld.select','Play Selected World');
    await wait(t=>!t.screenClass,'state world running');await release();held=false;await mode('OPERATE');
    fingerprint=(await api('GET','/v0/world/fingerprint?category=DEBUG')).worldFingerprint;
    const p=(await read(true)).state, at={x:Math.floor(p.position.x)+3.5,y:Math.floor(p.position.y)+2,z:Math.floor(p.position.z)+0.5};
    const oldIds=new Set((await deep(['entities'])).server.entities.map(e=>e.uuid));
    await native(`summon minecraft:armor_stand ${at.x} ${at.y} ${at.z} {NoGravity:1b,Invulnerable:1b,Invisible:1b,Tags:["${tag}"]}`);
    const deadline=Date.now()+5000;
    while(!entityUuid){const candidates=(await deep(['entities'])).server.entities.filter(e=>!oldIds.has(e.uuid)&&e.type==='minecraft:armor_stand'&&Math.abs(e.x-at.x)<0.1&&Math.abs(e.z-at.z)<0.1);
      assert(candidates.length<=1);entityUuid=candidates[0]?.uuid;if(!entityUuid){assert(Date.now()<deadline);await pause(50);}}
    await acquire();held=true;await tapEscape();await wait(t=>t.screenClass.endsWith('PauseScreen'),'native Pause stabilizes state');paused=true;await release();held=false;await mode('OPERATE');
    await check('state operations retain independent Arm mode and actual native value checks',async()=>{
      const v=await read(true), request=body(true,v,{food:v.state.food===17?18:17},{food:v.state.food});
      await mutate(request,'DEBUG_NOT_ARMED');await arm();await mode('READ');await mutate(request,'OPERATE_REQUIRED');await mode('OPERATE');
      await mutate({...request,value:{rawNbt:{}}},'INVALID_DEBUG_STATE');await mutate({...request,value:{air:v.state.maxAirSupply+1}},'INVALID_DEBUG_STATE');
      await mutate({...request,value:{experiencePoints:100000}},'INVALID_DEBUG_STATE');assert.deepEqual(await read(true),v);
      const e=await read(false);await mutate({...body(false,e,{silent:true},{silent:e.state.silent}),entityUuid:v.state.uuid,expectedResourceVersion:v.ref},'INVALID_DEBUG_STATE');
    });
    await check('explicit native field groups expose the same approved vitals and common state',async()=>{
      const fullPlayer=await read(true),fullEntity=await read(false);
      const projected=await api('POST','/v0/observe/deep',{category:'DEBUG',perspective:'server_authoritative',domains:['player','entities'],selector:{entityRadius:16},projection:{playerFields:['vitals'],entityFields:['living','common_state']}});
      for(const field of ['food','saturation','air','maxAirSupply','experiencePoints'])assert.equal(projected.server.player[field],fullPlayer.state[field]);
      const entity=projected.server.entities.find(e=>e.uuid===entityUuid);assert(entity);
      for(const field of ['health','absorption','air','maxAirSupply','silent','noGravity','glowing'])assert.equal(entity[field],fullEntity.state[field]);
      assert.equal(projected.server.player.inventory,undefined);assert.equal(entity.equipment,undefined);
      actionSamples.push({case:'world_debug_state_projection',projected,comparison:'native_full_capture_of_same_paused_fixture'});
    });
    await check('native player food saturation and air setters restore without player input',async()=>{
      const v=(await read(true)).state;await chain(true,{food:v.food===17?18:17,saturation:5,air:Math.min(200,v.maxAirSupply)});
    });
    await check('native player experience level and points use native level domains and restore',async()=>{
      const v=(await read(true)).state;await chain(true,{experienceLevel:v.experienceLevel+1});
      const next=(await read(true)).state;await chain(true,{experiencePoints:next.experiencePoints===1?2:1});
    });
    await check('native logical player velocity mutation is diagnostic and reversible while paused',async()=>{await chain(true,{velocity:{x:0.125,y:0,z:0}});});
    await check('flight state never grants native flight permission and restores the original game mode',async()=>{
      const initial=await read(true);assert.equal(initial.state.gameMode,'creative');assert.equal(initial.state.mayFly,true);assert.equal(initial.state.flying,false);
      await chain(true,{flying:true});
      try {await native('gamemode survival');const denied=await read(true);assert.equal(denied.state.mayFly,false);
        await mutate(body(true,denied,{flying:true},{flying:false}),'DEBUG_PLAYER_ABILITY_DENIED');assert.deepEqual(await read(true),denied);
        actionSamples.push({case:'world_debug_flight_permission',denied,permissionGranted:false});
      } finally {await native('gamemode creative');assert.equal((await read(true)).state.mayFly,true);}
    });
    await check('native loaded LivingEntity state flags health and motion have honest before after revisions',async()=>{
      const before=await read(false),v=before.state;assert(v.health>2);
      await mutate(body(false,before,{health:v.health-1,noAi:true},{health:v.health}),'INVALID_DEBUG_STATE');assert.deepEqual(await read(false),before);
      await chain(false,{health:v.health-1,silent:!v.silent,noGravity:!v.noGravity,glowing:!v.glowing,air:Math.min(200,v.maxAirSupply),velocity:{x:0,y:0.125,z:0}});
    });
    await check('native registered player and entity attribute domains modifiers and versions restore',async()=>{await attributeChain(true);await attributeChain(false);});
    await check('native player and LivingEntity effects preserve flags hidden chains and explicit removal',async()=>{await effectChain(true);await effectChain(false);});
    await check('actual MCP state operation preserves typed values and independent authorization',async()=>{
      const mcpInputBefore=await input();
      const require=createRequire(resolve(repo,'components/companion/package.json'));
      const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
      const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
      const env=Object.fromEntries(Object.entries(process.env).filter(([k,v])=>typeof v==='string'&&!/^MCP_MINECRAFT_/i.test(k)));env.MCP_MINECRAFT_INSTANCE_DIR=instance;env.MCP_MINECRAFT_TARGET=target;
      const client=new Client({name:'world-state-consumer',version:'1.0.0'},{versionNegotiation:{mode:'legacy'}}), transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
      let applied,original;
      const call=async(name,args)=>{const v=await client.callTool({name,arguments:args},{timeout:10000});assert(!v.isError,JSON.stringify(v));return v.structuredContent.data;};
      try {await client.connect(transport);await call('minecraft_debug_arm',{action:'arm',worldFingerprint:fingerprint,namespaces:['player','entity'],ttlMs:60000});
        original=await read(true);const changed=original.state.food===16?17:16;
        await pause(650);applied=await call('minecraft_debug',{action:'mutate',mutation:body(true,original,{food:changed},{food:original.state.food})});
        const after=await read(true);assert.equal(after.state.food,changed);assert.deepEqual(after.ref,applied.afterResourceVersion);
        await pause(650);const restored=await call('minecraft_debug',{action:'mutate',mutation:body(true,after,{food:original.state.food},{food:changed})});
        assert.equal((await read(true)).state.food,original.state.food);applied=undefined;
        actionSamples.push({case:'world_debug_state_actual_mcp',operation:'player.state.set',restored,consumer:'mcp_stdio'});
        const e=await read(false);assert.equal(e.state.equipment.head.empty,true,'Owned armor stand head must be empty');assert(e.state.nativeEquipmentSlots.includes('head'));
        const equip=(observed,count)=>({operation:'entity.equipment.set',worldFingerprint:fingerprint,expectedResourceVersion:observed.ref,entityUuid,expectedEntityType:observed.state.type,equipmentSlot:'head',itemId:'minecraft:iron_helmet',count,expectedItemId:observed.state.equipment.head.id,expectedCount:observed.state.equipment.head.count,...(count?{itemData:{customName:'Native Equipment Test',damage:2}}:{})});
        let equipment;
        try {await pause(650);equipment=await call('minecraft_debug',{action:'mutate',mutation:equip(e,1)});
          const changed=await read(false);assert.equal(changed.state.equipment.head.id,'minecraft:iron_helmet');assert.equal(changed.state.equipment.head.customName,'Native Equipment Test');assert.equal(changed.state.equipment.head.damage,2);
          assert.deepEqual(changed.ref,equipment.afterResourceVersion);await pause(650);
          const denied=await client.callTool({name:'minecraft_debug',arguments:{action:'mutate',mutation:equip(e,0)}},{timeout:10000});assert(denied.isError);assert.equal(denied.structuredContent.error.code,'STALE_RESOURCE_REVISION');assert.equal(denied.structuredContent.error.actualRevision,changed.ref.revision);
          await pause(650);const cleared=await call('minecraft_debug',{action:'mutate',mutation:equip(changed,0)});assert.equal((await read(false)).state.equipment.head.empty,true);equipment=undefined;
          actionSamples.push({case:'world_debug_entity_equipment_actual_mcp',operation:'entity.equipment.set',applied:cleared,consumer:'mcp_stdio'});
        } finally {if(equipment){await arm();const current=await read(false);assert.deepEqual(current.ref,equipment.afterResourceVersion);await mutate(equip(current,0));}}
        const effectOriginal=await read(true),effectId='minecraft:speed';assert(!effectOriginal.state.effects.some(e=>e.id===effectId));
        const effectValue={duration:300,amplifier:0,ambient:false,showParticles:false,showIcon:false,hidden:[]};let effectApplied;
        const effectRequest=(observed,value,expectedValue)=>({operation:'player.effect.set',worldFingerprint:fingerprint,expectedResourceVersion:observed.ref,effectId,value,expectedValue});
        try {await pause(650);effectApplied=await call('minecraft_debug',{action:'mutate',mutation:effectRequest(effectOriginal,effectValue,null)});
          const changed=await read(true), observed=changed.state.effects.find(e=>e.id===effectId);assert(observed);assert.equal(observed.hiddenChainComplete,true);
          const {id,hiddenChainComplete,...actual}=observed;assert.deepEqual(actual,effectValue);
          await pause(650);const cleared=await call('minecraft_debug',{action:'mutate',mutation:effectRequest(changed,null,effectValue)});
          assert(!(await read(true)).state.effects.some(e=>e.id===effectId));effectApplied=undefined;
          actionSamples.push({case:'world_debug_effect_actual_mcp',operation:'player.effect.set',cleared,consumer:'mcp_stdio'});
        } finally {if(effectApplied){await arm();const current=await read(true);assert.deepEqual(current.ref,effectApplied.afterResourceVersion);await mutate(effectRequest(current,null,effectValue));}}
        const mcpInputAfter=await input();assert.equal(mcpInputAfter.inputDispatchSequence,mcpInputBefore.inputDispatchSequence);
        actionSamples.push({case:'world_debug_state_mcp_zero_input',mcpInputBefore,mcpInputAfter,actInputDelta:0});
      } finally {if(applied){await arm();const current=await read(true);assert.deepEqual(current.ref,applied.afterResourceVersion);await mutate(body(true,current,{food:original.state.food},{food:current.state.food}));}await client.close();}
    });
    await check('native Mob AI flag is typed and reversible without arbitrary brain mutation',async()=>{
      for(const undo of recovery.reverse())await undo();recovery.length=0;
      await arm();const armor=entityUuid;let pig;
      try {const created=await api('POST','/v0/debug/phase9a/scenario',{action:'entity_spawn_pig'});pig=created.spawnedEntityUuid;assert(pig);entityUuid=pig;
        const before=await read(false);assert.equal(before.state.type,'minecraft:pig');assert.equal(typeof before.state.noAi,'boolean');
        await chain(false,{noAi:!before.state.noAi});
        actionSamples.push({case:'world_debug_mob_fixture',arrange:'legacy_debug_scenario_native_spawn',created,before,after:await read(false)});
      } finally {try {if(pig){for(const undo of recovery.reverse())await undo();recovery.length=0;await arm();await api('POST','/v0/debug/phase9a/scenario',{action:'entity_remove',entityUuid:pig});assert(!(await deep(['entities'])).server.entities.some(e=>e.uuid===pig));}}finally{entityUuid=armor;}}
    });
  } catch(error){failure=error;}
  finally {
    try {if(held){await release();held=false;}await mode('OPERATE');if(fingerprint){await arm();for(const undo of recovery.reverse())await undo();}}catch(error){cleanupErrors.push(String(error));}
    try {await api('POST','/v0/debug/disarm');setArm(undefined);}catch(error){cleanupErrors.push(String(error));}
    if(paused)try{await acquire();held=true;await tapEscape();await wait(t=>!t.screenClass,'state pause closed');await release();held=false;}catch(error){cleanupErrors.push(String(error));}
    if(entityUuid)try{await mode('OPERATE');await native(`kill @e[tag=${tag},limit=1]`);const end=Date.now()+5000;
      while((await deep(['entities'])).server.entities.some(e=>e.uuid===entityUuid)){assert(Date.now()<end);await pause(50);}}catch(error){cleanupErrors.push(String(error));}
    try{if(held)await release();await mode('READ');await input();}catch(error){cleanupErrors.push(String(error));}
    actionSamples.push({case:'world_debug_state_cleanup',cleanupErrors,persistentWriteCalls:0});
  }
  if(failure)throw failure;assert.deepEqual(cleanupErrors,[],'World state fixture must be restored');
}
