import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

export async function closeItemFixtureUi({read, escape, wait}) {
  let tree = await read();
  if(tree.screenClass.endsWith('AutomationProbeScreen')) {
    await escape(); tree = await wait(t => !t.screenClass || t.screenClass.endsWith('PauseScreen'),'fixture native parent',5000);
  }
  if(tree.screenClass.endsWith('PauseScreen')) {
    await escape(); tree = await wait(t => !t.screenClass,'native item pause closed',5000);
  }
  assert.equal(tree.screenClass,'','Unexpected Screen is preserved instead of blind Escape');
}

export async function worldDebugItemsActions({api, target, repo, instance, fixtureInput, mode, acquire, release,
  click, clickNode, wait, nodes, check, actionSamples, setArm}) {
  assert.equal(fixtureInput?.policy, 'immutable_baseline_restored'); assert.equal(fixtureInput.target, target);
  let held = false, paused = false, placed = false, position, originalContainer, fingerprint, armId, lastWrite = 0, failure;
  const recovery = [], cleanupErrors = [], pause = ms => new Promise(done => setTimeout(done, ms));
  async function input() {
    const v = await api('GET', '/v0/input/state');
    assert.equal(v.pressedKeyCount, 0); assert.equal(v.pressedButtonCount, 0);
    assert.equal(v.inputCleanupPending, false); assert.equal(v.inputCleanupFailed, false); return v;
  }
  async function tapEscape() {
    const op = await api('POST', '/v0/pipelines', {steps:[{type:'key.tap',key:256,holdMs:40}],timeoutMs:12000});
    assert.equal((await api('POST', `/v0/operations/${op.operationId}/wait`, {timeoutMs:12000})).state, 'completed');
  }
  const command = text => api('POST', '/v0/command/player', {command:text,route:'NATIVE'});
  async function deep(domains, extra = {}) {
    const v = await api('POST', '/v0/observe/deep', {category:'DEBUG',perspective:'server_authoritative',domains,...extra});
    assert.equal(v.server.chunkLoadRequested, false); assert.equal(v.server.storageAccessed, false); return v;
  }
  function ref(v, type, key) {
    const r = v.resourceRevisionRefs.find(r => r.resourceType === type && r.resourceKey === key);
    assert(r?.mutationPreconditionEligible); return r;
  }
  async function player() {
    const v = await deep(['player']); return {state:v.server.player,ref:ref(v,'player',`${v.server.player.uuid}@server_authoritative`)};
  }
  async function block() {
    const v = await deep(['blocks'], {selector:{blocks:[position]}}); assert.equal(v.server.blocks[0].available,true); return v.server.blocks[0];
  }
  async function container() {
    const v = await deep(['block_entities'], {selector:{blocks:[position]},includeSerializedBlockEntities:true});
    assert.equal(v.server.blockEntities.length,1); const state = v.server.blockEntities[0];
    return {state,ref:ref(v,'block_entity_serialized',`${v.server.dimension}@${state.key}`)};
  }
  async function arm(namespaces = ['player','block_entity']) {
    armId = (await api('POST','/v0/debug/arm',{worldFingerprint:fingerprint,namespaces,ttlMs:60000})).debugArmId;
    setArm(armId);
  }
  const body = (op, observed, fields) => ({operation:op,worldFingerprint:fingerprint,expectedResourceVersion:observed.ref,...fields});
  async function mutate(value, error) {
    const delay = 650 - (Date.now() - lastWrite); if (delay > 0) await pause(delay); lastWrite = Date.now();
    return api('POST','/v0/debug/mutations',value,error);
  }
  async function chain({operation, read, item, fields, itemId, count, itemData}) {
    const original = await read(); assert.equal(item(original.state).count,0,'Only an empty fixture slot is used');
    const initialInput = await input(); let applied;
    const make = (value, replacement) => body(operation,value,{...fields,expectedItemId:item(value.state).id,expectedCount:item(value.state).count,...replacement});
    const undo = async () => {
      const value = await read(); if (item(value.state).count === 0) return;
      assert(applied,'Unknown mutation outcome must not be overwritten'); assert.deepEqual(value.ref,applied.afterResourceVersion,'External changes must be preserved');
      assert.equal(item(value.state).id,itemId); assert.equal(item(value.state).count,count);
      await mutate(make(value,{count:0})); assert.equal(item((await read()).state).count,0);
    };
    recovery.push(undo);
    applied = await mutate(make(original,{itemId,count,...(itemData ? {itemData} : {})}));
    assert.equal(applied.gameplayEvidence,false); assert.equal(applied.evidenceContaminated,true); assert.equal(applied.storageAccessed,false);
    const changed = await read(); assert.equal(item(changed.state).id,itemId); assert.equal(item(changed.state).count,count);
    const stale = await mutate(make(original,{count:0}),'STALE_RESOURCE_REVISION');
    assert.equal(stale.actualRevision,changed.ref.revision); assert.equal(stale.retrySafe,false);
    await mutate({...make(changed,{count:0}),expectedCount:count + 1},'VALUE_PRECONDITION_FAILED');
    assert.deepEqual((await read()).ref,changed.ref);
    await undo(); const finalInput = await input(); assert.equal(finalInput.inputDispatchSequence,initialInput.inputDispatchSequence);
    actionSamples.push({case:'world_debug_item_chain',operation,original,applied,changed,stale,restored:await read(),initialInput,finalInput,actInputDelta:0});
  }
  try {
    await acquire(); held = true; await click('menu.singleplayer','Singleplayer');
    const tree = await wait(t => t.screenClass.endsWith('SelectWorldScreen'),'world items fixed list');
    const worlds = nodes(tree).filter(n => n.worldName === 'World-Read-Alpha'); assert.equal(worlds.length,1);
    await clickNode(worlds[0]); await click('selectWorld.select','Play Selected World');
    await wait(t => !t.screenClass,'world items entered'); await release(); held = false; await mode('OPERATE');
    const p = await api('GET','/v0/player?category=USER'); position = {x:Math.floor(p.x)+4,y:Math.floor(p.y)+3,z:Math.floor(p.z)+4};
    const old = await block(); assert.equal(old.blockId,'minecraft:air');
    placed = true; await command(`setblock ${position.x} ${position.y} ${position.z} minecraft:barrel`);
    const until = Date.now()+5000; while ((await block()).blockId !== 'minecraft:barrel') {assert(Date.now()<until); await pause(100);}
    originalContainer = (await container()).state.serializedState;
    await acquire(); held = true; await tapEscape(); await wait(t => t.screenClass.endsWith('PauseScreen'),'native pause for exact item preconditions'); paused = true;
    await release(); held = false; await mode('OPERATE'); fingerprint = (await api('GET','/v0/world/fingerprint?category=DEBUG')).worldFingerprint;
    const settled = await player(); await pause(180); assert.deepEqual(await player(),settled,'Player resource must actually be stable, not stale-retried');
    const slot = settled.state.inventory.find(s => s.slot >= 9 && s.slot <= 35 && s.empty); assert(slot);
    const head = settled.state.equipment.head; assert(head?.empty);
    await check('new item mutations retain Arm and READ mode boundaries', async () => {
      const value = body('player.inventory.slot.set',await player(),{slot:slot.slot,itemId:'minecraft:stone',count:2});
      await mutate(value,'DEBUG_NOT_ARMED'); await arm(); await mode('READ');
      await mutate(value,'OPERATE_REQUIRED'); assert.equal((await player()).state.inventory.find(s => s.slot === slot.slot).count,0);
      await mode('OPERATE');
    });
    await check('player inventory typed item data and stale cleanup require no Menu or input Lease', async () => {
      await chain({operation:'player.inventory.slot.set',read:player,item:s => s.inventory.find(v => v.slot === slot.slot),fields:{slot:slot.slot},itemId:'minecraft:iron_pickaxe',count:1,itemData:{customName:'World Item Test',damage:2}});
      const applied = actionSamples.at(-1).applied.after.inventory.find(s => s.slot === slot.slot);
      assert.equal(applied.customName,'World Item Test'); assert.equal(applied.damage,2); assert.equal(applied.maxDamage,250);
    });
    await check('player equipment typed setter updates and restores the actual native head slot', async () => {
      await chain({operation:'player.equipment.set',read:player,item:s => s.equipment.head,fields:{equipmentSlot:'head'},itemId:'minecraft:iron_helmet',count:1});
    });
    await check('native Block Entity Container slot has independent version and no UI identity dependency', async () => {
      const item = state => {
        const values = state.serializedState.Items; assert(Array.isArray(values),'Native barrel Items serialization is the comparison standard');
        const value = values.find(v => Number(v.Slot) === 0);
        return value ? {id:value.id,count:Number(target.startsWith('1.20.1-') ? value.Count : value.count)} : {id:'minecraft:air',count:0};
      };
      await chain({operation:'block_entity.slot.set',read:container,item,fields:{...position,slot:0,expectedBlockEntityType:'minecraft:barrel'},itemId:'minecraft:stone',count:7});
    });
    await check('item payload cannot create an oversized native stack or raw metadata', async () => {
      const original = await player(), value = body('player.inventory.slot.set',original,{slot:slot.slot,itemId:'minecraft:iron_pickaxe',count:2});
      await mutate(value,'INVALID_DEBUG_ITEM'); await mutate({...value,count:1,itemData:{rawNbt:'{}'}},'INVALID_DEBUG_ITEM');
      assert.deepEqual(await player(),original);
    });
    await check('actual MCP typed inventory mutation preserves inspect token stale reason and zero input', async () => {
      const require = createRequire(resolve(repo,'components/companion/package.json'));
      const {Client} = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
      const {StdioClientTransport} = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
      const env = Object.fromEntries(Object.entries(process.env).filter(([name,v]) => typeof v === 'string' && !/^MCP_MINECRAFT_/i.test(name)));
      env.MCP_MINECRAFT_INSTANCE_DIR = instance; env.MCP_MINECRAFT_TARGET = target;
      const client = new Client({name:'typed-world-items',version:'1.0.0'},{versionNegotiation:{mode:'legacy'}});
      const transport = new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
      const before = await input(); let applied;
      const call = async (name,args,error) => {
        const value = await client.callTool({name,arguments:args},{timeout:10000});
        if(error){assert(value.isError); assert.equal(value.structuredContent?.error?.code,error); return value.structuredContent.error;}
        assert(!value.isError,JSON.stringify(value)); assert(value.structuredContent?.data); return value.structuredContent.data;
      };
      const read = async () => {
        const v = await call('minecraft_deep_observe',{category:'DEBUG',perspective:'server_authoritative',domains:['player']});
        return {state:v.server.player,ref:ref(v,'player',`${v.server.player.uuid}@server_authoritative`)};
      };
      const make = (v,count) => body('player.inventory.slot.set',v,{slot:slot.slot,itemId:'minecraft:stone',count,expectedCount:v.state.inventory.find(s => s.slot === slot.slot).count});
      const write = async (v,error) => {const delay=650-(Date.now()-lastWrite);if(delay>0)await pause(delay);lastWrite=Date.now();return call('minecraft_debug',{action:'mutate',debugArmId:armId,mutation:v},error);};
      try {
        await client.connect(transport,{timeout:10000}); const original = await read();
        assert.equal(original.state.inventory.find(s => s.slot === slot.slot).count,0);
        applied = await write(make(original,7)); const changed = await read();
        assert.equal(changed.state.inventory.find(s => s.slot === slot.slot).count,7);
        const stale = await write(make(original,0),'STALE_RESOURCE_REVISION'); assert.equal(stale.actualRevision,changed.ref.revision);
        const restored = await write(make(changed,0)); assert.equal((await read()).state.inventory.find(s => s.slot === slot.slot).count,0);
        const after = await input(); assert.equal(after.inputDispatchSequence,before.inputDispatchSequence);
        actionSamples.push({case:'world_debug_item_mcp',consumer:'real_stdio',applied,stale,restored,before,after});
      } finally {
        try {if(applied){const current = await player();const value = current.state.inventory.find(s => s.slot === slot.slot);
          if(value.count!==0){assert.deepEqual(current.ref,applied.afterResourceVersion);assert.equal(value.id,'minecraft:stone');assert.equal(value.count,7);await mutate(make(current,0));}}}
        finally {await client.close();await transport.close();}
      }
    });
    await check('unopened native loot container is rejected before getItem and remains unresolved', async () => {
      const opened = await api('POST','/v0/diagnostics/ui/test-screen',{});
      assert.equal(opened.evidenceContaminated,true);
      await wait(t => t.screenClass.endsWith('AutomationProbeScreen'),'explicit loot fixture Screen');
      async function fixtureButton(label, completed) {
        await acquire(); held = true;
        const tree = await api('GET','/v0/ui/tree'), controls = nodes(tree).filter(n => n.label === label);
        assert.equal(controls.length,1,'Explicit world-debug-items init script must provide its fixture');
        await clickNode(controls[0]);
        await wait(t => nodes(t).some(n => n.label === completed && n.active !== false),'native loot fixture callback',5000);
        await release(); held = false; await mode('OPERATE');
      }
      let primary;
      try {
        await fixtureButton('Prepare Pending Loot (Fixture)','Pending Loot Prepared (Fixture)');
        await arm(); const original = await container(), before = await input();
        assert.equal(original.state.serializedState.LootTable,'minecraft:chests/simple_dungeon');
        actionSamples.push({case:'world_debug_pending_loot_arrange',authority:'FIXTURE',mechanism:'opt_in_native_barrel_setLootTable',original});
        const denied = await mutate(body('block_entity.slot.set',original,{...position,slot:0,itemId:'minecraft:stone',count:1}),'DEBUG_CONTAINER_LOOT_UNRESOLVED');
        const after = await container(), finalInput = await input();
        assert.deepEqual(after,original,'Rejected native getter must not unpack or replace the loot table');
        assert.equal(finalInput.inputDispatchSequence,before.inputDispatchSequence);
        actionSamples.push({case:'world_debug_pending_loot_denied',original,denied,after,before,finalInput,actInputDelta:0});
      } catch(error) {primary=error;throw error;}
      finally {
        try {
          if(held){await release();held=false;} await mode('OPERATE');
          const current = await container();
          if(current.state.serializedState.LootTable === 'minecraft:chests/simple_dungeon')
            await fixtureButton('Clear Pending Loot (Fixture)','Pending Loot Cleared (Fixture)');
          assert.deepEqual((await container()).state.serializedState,originalContainer,'Loot fixture must restore the exact original empty barrel');
          actionSamples.push({case:'world_debug_pending_loot_restored',originalContainer});
        } catch(error){if(primary)cleanupErrors.push(String(error));else throw error;}
      }
    });
  } catch (error) {failure = error;}
  finally {
    try {if(held){await release();held=false;} await mode('OPERATE');
      if(fingerprint){await arm(); for(const restore of recovery.reverse()) await restore();}
    } catch(error){cleanupErrors.push(String(error));}
    try {await api('POST','/v0/debug/disarm');setArm(undefined);} catch(error){cleanupErrors.push(String(error));}
    if(paused) try {
      await acquire();held=true;
      try {await closeItemFixtureUi({read:() => api('GET','/v0/ui/tree'),escape:tapEscape,wait});}
      finally {try {await release();} finally {held=false;}}
    } catch(error){cleanupErrors.push(String(error));}
    if(placed) try {await mode('OPERATE');const current = await block();assert.equal(current.blockId,'minecraft:barrel');
      assert(originalContainer); assert.deepEqual((await container()).state.serializedState,originalContainer,'Unknown container data must be preserved');
      await command(`setblock ${position.x} ${position.y} ${position.z} minecraft:air`);
      const until = Date.now()+5000; while((await block()).blockId !== 'minecraft:air'){assert(Date.now()<until);await pause(100);}
    } catch(error){cleanupErrors.push(String(error));}
    try {if(held)await release();await mode('READ');await input();} catch(error){cleanupErrors.push(String(error));}
    actionSamples.push({case:'world_debug_items_cleanup',cleanupErrors,persistentWriteCalls:0});
  }
  if(failure)throw failure; assert.deepEqual(cleanupErrors,[],'World item cleanup must complete');
}
