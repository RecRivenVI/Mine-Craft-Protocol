import assert from 'node:assert/strict';
import { worldEntityActions } from './world-entity-actions.mjs';
import { worldUiActions } from './world-ui-actions.mjs';

// All arrangement is confined to the owned disposable world. Administrative player commands
// are diagnostic setup, not proof of USER input; only the world.action steps are acceptance Act.
export async function worldHeldActions({api,mode,acquire,release,key,wait,check,actionSamples,setArm}) {
  const pause=ms=>new Promise(r=>setTimeout(r,ms));
  const p=await api('GET','/v0/player');
  const origin={x:Math.floor(p.x),y:Math.floor(p.y),z:Math.floor(p.z)};
  const changed=new Map();let held=false,primary;
  async function takeover(){if(!held){await mode('READ');await acquire();held=true;}}
  async function operate(){if(held){await release();held=false;}await mode('OPERATE');}
  async function command(command){await takeover();await api('POST','/v0/command/player',{command});await pause(220);}
  async function block(at){return api('GET','/v0/world/block?'+new URLSearchParams(at));}
  async function arrange(at,id){
    // Three debug requests per arrangement: stay below the production 2 token/s budget.
    await pause(1800);
    await operate();
    const fingerprint=await api('GET','/v0/world/fingerprint?category=DEBUG');
    setArm((await api('POST','/v0/debug/arm',{worldFingerprint:fingerprint.worldFingerprint,namespaces:['world'],ttlMs:60000})).debugArmId);
    const key=JSON.stringify(at);
    if(!changed.has(key)){
      const old=await api('GET','/v0/world/block?category=DEBUG&'+new URLSearchParams(at));
      assert(['minecraft:air','minecraft:stone','minecraft:obsidian','minecraft:oak_planks','minecraft:oak_slab'].includes(old.block),'Do not overwrite unrelated fixture state '+key);changed.set(key,at);
    }
    await api('POST','/v0/debug/world/block',{...at,blockId:id});await pause(200);await takeover();
  }
  async function poll(read,predicate,label,ms=4000){let value;const end=Date.now()+ms;do{value=await read();if(predicate(value))return value;await pause(25);}while(Date.now()<end);assert.fail(label+': '+JSON.stringify(value));}
  async function start(ref,action,extra={}){return api('POST','/v0/pipelines',{steps:[{type:'world.action',targetRef:ref,action,...extra}],timeoutMs:20000});}
  async function finish(op){return api('POST','/v0/operations/'+op.operationId+'/wait',{timeoutMs:22000});}
  async function act(at,action,extra={}){const target=await block(at);assert(target.targetRef,JSON.stringify(target));const result=await finish(await start(target.targetRef,action,extra));assert.equal(result.state,'completed',JSON.stringify(result));const step=result.result.steps[0].result;assert.equal(step.evidenceAuthority,'PLAYTEST');assert.equal(step.directMutationUsed,false);assert.equal(step.effectConfirmed,false);return step;}
  async function zero(){const input=await api('GET','/v0/input/state');assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);}
  async function nativeBlock(at,state){
    if(!changed.has(JSON.stringify(at))){
      const old=await block(at);
      assert.equal(old.block,'minecraft:air','Only owned empty fixture positions may be prepared');
      changed.set(JSON.stringify(at),at);
    }
    await command(`setblock ${at.x} ${at.y} ${at.z} ${state}`);
    await poll(()=>block(at),b=>b.block===state.split('[')[0],'native fixture block received');
  }
  const support={...origin,x:origin.x+2};
  try {
    // Reused diagnostic worlds may have holes from interrupted earlier runs. Restore only
    // this runner-owned bounded floor before Act; do not repair it during an action.
    await command(`fill ${origin.x-1} ${origin.y-1} ${origin.z-2} ${origin.x+4} ${origin.y-1} ${origin.z+2} minecraft:grass_block`);
    await poll(()=>block({...support,y:support.y-1}),b=>b.block==='minecraft:grass_block','owned fixture floor initialized');
    await check('USER sustained mining, bounded timeout, active cancellation and Lease loss',async()=>{
      const modern=!p.target.startsWith('1.');
      await command('gamerule '+(modern?'minecraft:mob_griefing':'mobGriefing')+' false');
      await command('gamerule '+(modern?'minecraft:random_tick_speed':'randomTickSpeed')+' 0');
      await command('gamerule '+(modern?'minecraft:spawn_mobs':'doMobSpawning')+' false');
      await command('difficulty peaceful');
      await command('kill @e[type=!minecraft:player,distance=..16]');
      // Fixture positioning is diagnostic setup. Center the actor so its collision box cannot
      // occupy one of the six adjacent placement destinations; it is never a USER Act shortcut.
      await command(`tp @s ${origin.x+.5} ${origin.y} ${origin.z+.5}`);
      let stationary=0;
      await poll(()=>api('GET','/v0/player'),s=>{stationary=Math.abs(s.x-origin.x-.5)<.01&&Math.abs(s.z-origin.z-.5)<.01&&Math.abs(s.y-origin.y)<.01?stationary+1:0;return stationary>=12;},'stable centered diagnostic actor');
      Object.assign(p,await api('GET','/v0/player'));
      await command('clear @s');await command('gamemode survival @s');
      await arrange(support,'minecraft:oak_planks');
      const result=await act(support,'mine',{holdMs:8000});
      assert.equal(result.clientBreakPredicted,true);assert(result.holdDurationMillis>400,'Must hold through real Survival breaking');
      await poll(()=>block(support),b=>b.block==='minecraft:air','Survival break effect');await zero();
      await arrange(support,'minecraft:obsidian');
      let operation=await start((await block(support)).targetRef,'mine',{holdMs:300});
      let outcome=await finish(operation);assert.equal(outcome.state,'failed');assert.equal(outcome.errorCode,'WORLD_MINE_TIMEOUT',JSON.stringify(outcome));await zero();
      operation=await start((await block(support)).targetRef,'mine',{holdMs:10000});
      await poll(()=>api('GET','/v0/input/state'),s=>s.pressedButtonCount+s.pressedKeyCount>0,'active mining press');
      await api('DELETE','/v0/operations/'+operation.operationId);await pause(220);await zero();
      let stopped=await api('GET','/v0/input/state');await pause(120);assert.equal((await api('GET','/v0/input/state')).inputDispatchSequence,stopped.inputDispatchSequence,'No late Agent dispatch after cancellation cleanup');
      assert.equal((await block(support)).block,'minecraft:obsidian');
      const lost=await start((await block(support)).targetRef,'mine',{holdMs:10000});
      await poll(()=>api('GET','/v0/input/state'),s=>s.pressedButtonCount+s.pressedKeyCount>0,'active mining before Lease loss');
      await release();held=false;await pause(180);await zero();
      assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
      stopped=await api('GET','/v0/input/state');await pause(120);assert.equal((await api('GET','/v0/input/state')).inputDispatchSequence,stopped.inputDispatchSequence,'No late Agent dispatch after Lease loss');
      assert.equal((await api('GET','/v0/operations/'+lost.operationId)).state,'cancelled');await takeover();
      actionSamples.push({case:'sustained_mining',arrangeAuthority:'diagnostic_admin_command_and_DEBUG_PRIVILEGED',actAuthority:'PLAYTEST',result,timeout:outcome.errorCode,cancelledOperation:operation.operationId,leaseLossOperation:lost.operationId,postCancelHeldInput:0,postCancelLateDispatch:0});
      await arrange(support,'minecraft:air');await command('gamemode creative @s');
    });
    await check('USER waterlogged replacement, multiblock effects and stale mining target',async()=>{
      await command('clear @s');await command('gamemode survival @s');
      // A real water source flows and changes nearby native states. Contain it and stand one block
      // higher to retain an unobstructed native pick over the basin wall; diagnostic Arrange only.
      const walls=[[-1,0],[1,0],[0,-1],[0,1]].map(([dx,dz])=>({...support,x:support.x+dx,z:support.z+dz}));
      for(const at of walls)await nativeBlock(at,'minecraft:stone');
      const platform={...origin};await nativeBlock(platform,'minecraft:stone');
      await command(`tp @s ${origin.x+.5} ${origin.y+1} ${origin.z+.5}`);
      await nativeBlock(support,'minecraft:oak_slab[type=bottom,waterlogged=true]');
      await pause(1200);
      const water=await act(support,'mine',{holdMs:8000});
      assert.equal(water.clientBreakPredicted,true);
      await poll(()=>block(support),b=>b.block==='minecraft:water','native fluid replacement remains water');
      assert.equal((await block({...support,y:support.y-1})).block,'minecraft:grass_block','Mining must not also break the fixture floor');
      await arrange(support,'minecraft:air');
      for(const at of walls)await arrange(at,'minecraft:air');
      await arrange(platform,'minecraft:air');
      await command(`tp @s ${origin.x+.5} ${origin.y} ${origin.z+.5}`);
      await command('gamemode creative @s');
      const upper={...support,y:support.y+1};
      // Separate setblock commands can leave an invalid half-plant between server ticks.
      // Let the actual BlockItem place both halves using its native placement contract.
      assert.equal((await block(upper)).block,'minecraft:air');
      changed.set(JSON.stringify(upper),upper);
      await command('item replace entity @s weapon.mainhand with minecraft:sunflower');
      const plantPlacement=await act({...support,y:support.y-1},'place',{face:'up',expectedDestinationBlockId:'minecraft:air'});
      await poll(()=>block(support),b=>b.block==='minecraft:sunflower','native lower plant placement');
      await poll(()=>block(upper),b=>b.block==='minecraft:sunflower','native upper plant placement');
      const chain=await act(support,'mine',{holdMs:2000});
      // Vanilla may predict only the clicked half; neighbor removal then arrives separately.
      // Do not mislabel the later client observation as synchronous native provenance.
      assert(chain.nativeBreakChanges>=1,'Owned native destruction must be observed');
      await poll(()=>block(support),b=>b.block==='minecraft:air','lower half removed');
      await poll(()=>block(upper),b=>b.block==='minecraft:air','upper half removed');
      await command('gamemode survival @s');await arrange(support,'minecraft:obsidian');
      const staleRef=(await block(support)).targetRef;
      await command(`setblock ${support.x} ${support.y} ${support.z} minecraft:stone`);
      await poll(()=>block(support),b=>b.block==='minecraft:stone','external replacement received');
      const stale=await finish(await start(staleRef,'mine',{holdMs:10000}));assert.equal(stale.state,'failed');assert.equal(stale.errorCode,'WORLD_TARGET_STALE',JSON.stringify(stale));await zero();
      const sequence=(await api('GET','/v0/input/state')).inputDispatchSequence;await pause(120);
      assert.equal((await api('GET','/v0/input/state')).inputDispatchSequence,sequence,'No late dispatch after stale rejection cleanup');
      actionSamples.push({case:'fluid_and_chain_mining',arrangeAuthority:'diagnostic_admin_command_and_DEBUG_PRIVILEGED',actAuthority:'PLAYTEST',water,chain,plantPlacement,plantPlacedByNativeInput:true,observedReplacement:'minecraft:water',bothPlantHalvesRemoved:true,completionAuthority:'CLIENT_KNOWN_prediction_and_separate_observation',externalUpdate:stale.errorCode,postRejectionLateInput:0});
      await arrange(support,'minecraft:air');await command('gamemode creative @s');
    });
    await check('USER off-hand sustained shield use, cancellation and hand-priority refusal',async()=>{
      await command('gamemode survival @s');await command('clear @s');
      await arrange(support,'minecraft:stone');
      await command('item replace entity @s weapon.offhand with minecraft:shield');
      await wait(t=>t.presentation.children.some(n=>n.controlKey==='hud.offhand'&&n.item==='minecraft:shield')&&t.presentation.children.some(n=>n.selected&&n.count===0),'empty main hand and shield received');
      let op=await start((await block(support)).targetRef,'use_hold',{hand:'off_hand',holdMs:1200});
      await poll(()=>api('GET','/v0/player'),s=>s.usingItem&&s.usingHand==='off_hand','actual native off-hand shield');
      const done=await finish(op);assert.equal(done.state,'completed',JSON.stringify(done));
      const shield=done.result.steps[0].result;assert.equal(shield.hand,'off_hand');assert.equal(shield.nativeUseStarted,true);assert.equal(shield.released,true);await zero();
      op=await start((await block(support)).targetRef,'use_hold',{hand:'off_hand',holdMs:10000});
      await poll(()=>api('GET','/v0/player'),s=>s.usingItem&&s.usingHand==='off_hand','off-hand before cancellation');
      await api('DELETE','/v0/operations/'+op.operationId);await pause(220);await zero();
      await poll(()=>api('GET','/v0/player'),s=>!s.usingItem,'off-hand cancellation releases native use');
      const sequence=(await api('GET','/v0/input/state')).inputDispatchSequence;await pause(120);assert.equal((await api('GET','/v0/input/state')).inputDispatchSequence,sequence);
      await command('item replace entity @s weapon.mainhand with minecraft:bow');
      await wait(t=>t.presentation.children.some(n=>n.selected&&n.item==='minecraft:bow'),'conflicting main hand received');
      const conflict=await finish(await start((await block(support)).targetRef,'use_hold',{hand:'off_hand',holdMs:1000}));
      assert.equal(conflict.state,'failed');assert.equal(conflict.errorCode,'WORLD_USE_HAND_CONFLICT',JSON.stringify(conflict));await zero();
      await command('item replace entity @s weapon.mainhand with minecraft:air');
      await command('item replace entity @s weapon.offhand with minecraft:stone');
      await wait(t=>t.presentation.children.some(n=>n.controlKey==='hud.offhand'&&n.item==='minecraft:stone')&&t.presentation.children.some(n=>n.selected&&n.count===0),'unsupported off-hand item received');
      const unsupported=await finish(await start((await block(support)).targetRef,'use_hold',{hand:'off_hand',holdMs:1000}));
      assert.equal(unsupported.errorCode,'WORLD_USE_ITEM_REQUIRED',JSON.stringify(unsupported));await zero();
      actionSamples.push({case:'offhand_use',arrangeAuthority:'diagnostic_admin_command_and_DEBUG_PRIVILEGED',actAuthority:'PLAYTEST',shield,cancellationHeldInput:0,cancellationLateInput:0,handConflict:conflict.errorCode,unsupported:unsupported.errorCode});
      await arrange(support,'minecraft:air');await command('clear @s');await command('gamemode creative @s');
    });
    await check('USER animal feeding consumes held item and vehicle mount/dismount is native',async()=>{
      await command('kill @e[tag=world_gesture_fixture]');await command('gamemode survival @s');await command('clear @s');
      const at={x:p.x+2,y:origin.y,z:p.z};
      const find=async(type)=>(await api('GET','/v0/world/entities?radius=4&limit=128')).entities.find(e=>e.type===type&&Math.abs(e.x-at.x)<.8&&Math.abs(e.z-at.z)<.8);
      await command(`summon minecraft:cow ${at.x} ${at.y} ${at.z} {NoAI:1b,Silent:1b,Tags:["world_gesture_fixture"]}`);
      await command('item replace entity @s weapon.mainhand with minecraft:wheat 8');
      await wait(t=>t.presentation.children.some(n=>n.selected&&n.item==='minecraft:wheat'&&n.count===8),'fixture wheat received');
      const cow=await poll(()=>find('minecraft:cow'),e=>!!e?.targetRef,'cow target discovery');
      assert.equal((await finish(await start(cow.targetRef,'use'))).state,'completed');
      await wait(t=>t.presentation.children.some(n=>n.selected&&n.item==='minecraft:wheat'&&n.count===7),'USER HUD confirms one wheat consumed');await zero();
      await command('kill @e[tag=world_gesture_fixture]');await pause(300);await command('clear @s');
      await command(`summon minecraft:minecart ${at.x} ${at.y} ${at.z} {NoGravity:1b,Tags:["world_gesture_fixture"]}`);
      const cart=await poll(()=>find('minecraft:minecart'),e=>!!e?.targetRef,'rideable cart discovery');
      assert.equal((await finish(await start(cart.targetRef,'use'))).state,'completed');
      await poll(()=>api('GET','/v0/player'),s=>s.riding===true,'client native mounted state');
      const dismount=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:340,holdMs:200}],timeoutMs:5000});
      assert.equal((await finish(dismount)).state,'completed');
      await poll(()=>api('GET','/v0/player'),s=>s.riding===false,'native sneak dismount');await zero();
      actionSamples.push({case:'entity_feed_and_ride',arrangeAuthority:'diagnostic_admin_command',actAuthority:'PLAYTEST',feedBefore:8,feedAfter:7,mounted:true,dismounted:true});
      await command('kill @e[tag=world_gesture_fixture]');await command(`tp @s ${origin.x+.5} ${origin.y} ${origin.z+.5}`);
      await command('gamemode creative @s');await command('clear @s');
    });
    await check('USER precise placement on all six faces with noncentral UV and destination precondition',async()=>{
      await command('item replace entity @s weapon.mainhand with minecraft:oak_slab 64');
      const faces={up:[2,0,0,0,1,0],down:[2,2,0,0,-1,0],west:[2,0,0,-1,0,0],east:[-2,0,0,1,0,0],north:[0,0,2,0,0,-1],south:[0,0,-2,0,0,1]};
      const cases=[];
      for(const [face,[x,y,z,dx,dy,dz]] of Object.entries(faces)){
        const at={x:origin.x+x,y:origin.y+y,z:origin.z+z},destination={x:at.x+dx,y:at.y+dy,z:at.z+dz};
        await arrange(at,'minecraft:stone');await arrange(destination,'minecraft:air');
        const ref=(await block(at)).targetRef;
        const refused=await finish(await start(ref,'place',{face,hit:{u:.25,v:.75},expectedDestinationBlockId:'minecraft:obsidian'}));
        assert.equal(refused.state,'failed');assert.equal(refused.errorCode,'WORLD_PLACE_PRECONDITION_FAILED',JSON.stringify(refused));await zero();
        assert.equal((await block(destination)).block,'minecraft:air');
        const result=await act(at,'place',{face,hit:{u:.25,v:.75},expectedDestinationBlockId:'minecraft:air'});
        const after=await poll(()=>block(destination),b=>b.block==='minecraft:oak_slab','Placed slab '+face);
        assert.equal(result.nativeInvocationObserved,true);
        if(!['up','down'].includes(face))assert(after.state.includes('type=top'),after.state);
        cases.push({face,u:.25,v:.75,result,observedState:after.state});await zero();
        await arrange(destination,'minecraft:air');await arrange(at,'minecraft:air');
      }
      actionSamples.push({case:'precise_six_face_placement',arrangeAuthority:'diagnostic_admin_command_and_DEBUG_PRIVILEGED',actAuthority:'PLAYTEST',cases});
    });
    await check('USER charged bow release and natural item-use completion',async()=>{
      await command('gamemode survival @s');await command('clear @s');
      await command('kill @e[type=minecraft:arrow,distance=..16]');
      await poll(()=>api('GET','/v0/world/entities?radius=16&limit=128'),s=>!s.entitiesTruncated&&!s.entities.some(e=>e.type==='minecraft:arrow'),'empty projectile fixture before bow Act');
      await command('item replace entity @s weapon.mainhand with minecraft:bow');await command('give @s minecraft:arrow 16');
      await arrange(support,'minecraft:stone');
      const op=await start((await block(support)).targetRef,'use_hold',{holdMs:1200});
      const using=await api('POST','/v0/wait/until',{condition:{type:'player',expected:{usingItem:true}},timeoutMs:7000});assert.equal(using.passed,true);
      const outcome=await finish(op);assert.equal(outcome.state,'completed',JSON.stringify(outcome));
      const bow=outcome.result.steps[0].result;assert.equal(bow.nativeUseStarted,true);assert.equal(bow.nativeUseReleaseObserved,true);assert.equal(bow.released,true);await zero();
      await poll(()=>api('GET','/v0/player'),s=>!s.usingItem,'bow release');
      const arrows=await poll(()=>api('GET','/v0/world/entities?radius=16&limit=128'),s=>s.entities.some(e=>e.type==='minecraft:arrow'),'real projectile entity');
      const cancelledUse=await start((await block(support)).targetRef,'use_hold',{holdMs:10000});
      assert.equal((await api('POST','/v0/wait/until',{condition:{type:'player',expected:{usingItem:true}},timeoutMs:7000})).passed,true);
      await api('DELETE','/v0/operations/'+cancelledUse.operationId);await pause(220);await zero();
      await poll(()=>api('GET','/v0/player'),s=>!s.usingItem,'cancelled charged use releases native item');
      const sequence=(await api('GET','/v0/input/state')).inputDispatchSequence;await pause(120);assert.equal((await api('GET','/v0/input/state')).inputDispatchSequence,sequence,'No late input after held-use cancellation');
      await command('item replace entity @s weapon.mainhand with minecraft:golden_apple 2');
      const food=await act(support,'use_hold',{holdMs:5000});assert.equal(food.nativeUseStarted,true);assert.equal(food.nativeCompletionObserved,true);assert(food.holdDurationMillis<4500,'Natural completion should end hold early');await zero();
      actionSamples.push({case:'held_use',arrangeAuthority:'diagnostic_admin_command_and_DEBUG_PRIVILEGED',actAuthority:'PLAYTEST',bow,food,projectileObserved:arrows.entities.some(e=>e.type==='minecraft:arrow'),cancelledUseOperation:cancelledUse.operationId,postCancelHeldInput:0,postCancelLateDispatch:0,cancelSemantics:'release_can_trigger_vanilla_effect_not_rollback'});
      await arrange(support,'minecraft:air');await command('gamemode creative @s');await command('clear @s');
    });
    await check('USER entity attack feedback, container interaction and replaced entity refusal',async()=>{
      const at={x:p.x+2,y:origin.y,z:p.z};
      await command('kill @e[tag=world_gesture_fixture]');
      await command(`summon minecraft:pig ${at.x} ${at.y} ${at.z} {NoAI:1b,Silent:1b,Tags:["world_gesture_fixture"]}`);
      const find=async(type)=>{const data=await api('GET','/v0/world/entities?radius=4&limit=128');return data.entities.find(e=>e.type===type&&Math.abs(e.x-at.x)<.8&&Math.abs(e.z-at.z)<.8);};
      const pig=await poll(()=>find('minecraft:pig'),e=>!!e?.targetRef,'pig discovery');
      const attack=await start(pig.targetRef,'attack');
      const hurt=await poll(()=>find('minecraft:pig'),e=>e?.hurtAnimation===true,'client hurt animation',5000);
      assert.equal((await finish(attack)).state,'completed');await zero();
      await command('kill @e[tag=world_gesture_fixture]');await pause(250);
      await command(`summon minecraft:pig ${at.x} ${at.y} ${at.z} {NoAI:1b,Silent:1b,Tags:["world_gesture_fixture"]}`);
      const stale=await finish(await start(pig.targetRef,'attack'));assert.equal(stale.state,'failed');assert.equal(stale.errorCode,'WORLD_TARGET_STALE',JSON.stringify(stale));
      await command('kill @e[tag=world_gesture_fixture]');await pause(300);
      await command(`summon minecraft:chest_minecart ${at.x} ${at.y} ${at.z} {NoGravity:1b,Tags:["world_gesture_fixture"]}`);
      const cart=await poll(()=>find('minecraft:chest_minecart'),e=>!!e?.targetRef,'cart discovery');
      const used=await finish(await start(cart.targetRef,'use'));assert.equal(used.state,'completed',JSON.stringify(used));
      await wait(tree=>!!tree.screenClass,'entity container opened');await key(256);await wait(tree=>!tree.screenClass,'Agent Esc closes container');
      assert.equal((await api('GET','/v0/control/mode')).mode,'TAKEOVER');await zero();
      actionSamples.push({case:'entity_interaction',arrangeAuthority:'diagnostic_admin_command',actAuthority:'PLAYTEST',hurtAnimation:hurt.hurtAnimation,replacedEntity:stale.errorCode,containerOpened:true,agentEscDidNotRevoke:true});
    });
    await worldEntityActions({api,command,poll,start,finish,key,wait,check,actionSamples,zero,nativeBlock,arrange,p,origin});
    await worldUiActions({api,command,poll,start,finish,key,wait,check,actionSamples,zero,nativeBlock,arrange,origin});
  } catch(error) {primary=error;try{actionSamples.push({case:'held_fixture_failure',player:await api('GET','/v0/player'),input:await api('GET','/v0/input/state'),presentation:(await api('GET','/v0/ui/tree')).presentation,nearby:await api('GET','/v0/world/entities?radius=8&limit=128')});}catch{}throw error;} finally {
    const cleanupErrors=[];
    // Normal client GUI and routed commands only; no filesystem or persistent mutation API.
    try{const tree=await api('GET','/v0/ui/tree');if(tree.screenClass){await takeover();await key(256);}}catch(error){cleanupErrors.push(String(error));}
    try{await command('tag @e[type=minecraft:cow,distance=..8] add world_gesture_fixture');await command('kill @e[tag=world_gesture_fixture]');await command('kill @e[type=minecraft:arrow,distance=..16]');await command('gamemode creative @s');await command('clear @s');}catch(error){cleanupErrors.push(String(error));}
    for(const at of changed.values())try{await arrange(at,'minecraft:air');}catch(error){cleanupErrors.push(String(error));}
    try{if(held)await release();await mode('OPERATE');await api('POST','/v0/debug/disarm');setArm(undefined);await mode('READ');await zero();}catch(error){cleanupErrors.push(String(error));}
    if(cleanupErrors.length){if(primary)console.error('Fixture cleanup errors:',cleanupErrors);else throw new Error('Fixture cleanup failed: '+cleanupErrors.join('; '));}
  }
}
