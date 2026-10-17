import assert from 'node:assert/strict';
import {villagerFixtureCommands} from '../../vanilla-command-fixtures.mjs';

// Fixtures use diagnostic administrative commands only inside the runner-owned disposable
// world. Acceptance Act uses existing USER world.action and semantic UI, never entity setters.
export async function worldEntityActions({api,command,poll,start,finish,key,wait,check,actionSamples,zero,nativeBlock,arrange,p,origin}) {
  const nodes=t=>(t.children??[]).flatMap(n=>[n,...nodes(n)]);
  const pause=ms=>new Promise(r=>setTimeout(r,ms));
  const at={x:origin.x+2.5,y:origin.y,z:origin.z+.5};
  async function entities(){const s=await api('GET','/v0/world/entities?radius=8&limit=128');assert.equal(s.dataCategory,'USER');assert.equal(s.authority,'CLIENT_KNOWN');assert.equal(s.entitiesTruncated,false);assert.equal(s.chunkLoadRequested,false);return s.entities;}
  async function one(type){return (await entities()).find(e=>e.type==='minecraft:'+type);}
  async function reset(){await command(`tp @s ${origin.x+.5} ${origin.y} ${origin.z+.5}`);await command('tag @e[type=minecraft:cow,distance=..8] add world_gesture_fixture');await command('kill @e[tag=world_gesture_fixture]');await command('clear @s');await command('gamemode survival @s');await poll(entities,e=>!e.some(n=>['minecraft:wolf','minecraft:cow','minecraft:villager'].includes(n.type)&&n.targetRef),'previous fixture gone');}
  async function equip(item,count=1){await command(`item replace entity @s weapon.mainhand with minecraft:${item} ${count}`);await wait(t=>t.presentation.children.some(n=>n.selected&&n.item==='minecraft:'+item&&n.count===count),'fixture item received: '+item);}
  async function use(entity){assert(entity?.targetRef,'Live entity target reference');const outcome=await finish(await start(entity.targetRef,'use'));assert.equal(outcome.state,'completed',JSON.stringify(outcome));const r=outcome.result.steps[0].result;assert.equal(r.evidenceAuthority,'PLAYTEST');assert.equal(r.directMutationUsed,false);assert.equal(r.effectConfirmed,false);await zero();return r;}
  async function ui(selector){const result=await api('POST','/v0/ui/action',{selector,action:'click'});if(result.operationId)assert.equal((await finish(result)).state,'completed');}
  await check('USER wolf taming uses bounded native bone attempts and client tame feedback',async()=>{
    await reset();await command(`summon minecraft:wolf ${at.x} ${at.y} ${at.z} {NoAI:1b,Silent:1b,PersistenceRequired:1b,Tags:["world_gesture_fixture"]}`);
    let wolf=await poll(()=>one('wolf'),e=>e?.targetRef&&e.tamed===false&&e.baby===false,'wild wolf client flags');
    await equip('stick');await use(wolf);wolf=await one('wolf');assert.equal(wolf.tamed,false,'An accepted use is not a successful tame');
    await equip('bone',32);
    let attempts=0;
    // Vanilla taming is random: do not seed/force success, and never retry without a budget.
    while(!wolf.tamed&&attempts<32){await use(wolf);attempts++;await wait(t=>t.presentation.children.some(n=>n.selected&&n.item==='minecraft:bone'&&n.count===32-attempts)||attempts===32&&t.presentation.children.some(n=>n.selected&&n.count===0),'native bone consumption');await pause(120);wolf=await one('wolf');assert(wolf);}
    assert.equal(wolf.tamed,true,'Native random taming did not succeed within 32 attempts');
    assert.equal((await api('POST','/v0/assert',{condition:{type:'entity',entityType:'minecraft:wolf',radius:8,expected:{tamed:true}}})).passed,true);
    assert(!('ownerUUID' in wolf)&&!('inLove' in wolf)&&!('health' in wolf));
    actionSamples.push({case:'entity_taming',arrangeAuthority:'diagnostic_admin_command',actAuthority:'PLAYTEST',observationAuthority:'CLIENT_KNOWN',species:'minecraft:wolf',tamedBefore:false,tamedAfter:wolf.tamed,wrongItemDidNotTame:true,attempts,maxAttempts:32,forcedTame:false});
  });
  await check('USER breeding feeds two adults and observes a new client-known baby',async()=>{
    await reset();
    for(const z of [-1,1])await command(`summon minecraft:cow ${at.x} ${at.y} ${at.z+z} {Silent:1b,PersistenceRequired:1b,Tags:["world_gesture_fixture"]}`);
    await equip('wheat',2);
    const adults=await poll(entities,e=>e.filter(n=>n.type==='minecraft:cow'&&n.baby===false&&n.targetRef).length===2&&!e.some(n=>n.type==='minecraft:cow'&&n.baby),'two adults and no calf before Act');
    const cows=adults.filter(n=>n.type==='minecraft:cow'&&n.baby===false);
    await use(cows[0]);await wait(t=>t.presentation.children.some(n=>n.selected&&n.item==='minecraft:wheat'&&n.count===1),'first native feeding');
    await use(cows[1]);await wait(t=>t.presentation.children.some(n=>n.selected&&n.count===0),'second native feeding');
    assert.equal((await api('POST','/v0/wait/until',{condition:{type:'entity',entityType:'minecraft:cow',radius:8,expected:{baby:true}},timeoutMs:20000})).passed,true,'Natural breeding must produce a client-known baby');
    const calf=await poll(()=>oneBaby(),e=>!!e,'natural calf received by client',20000);
    // Offspring do not inherit the parents' tags. Tag only this owned isolated fixture's
    // nearby cows for cleanup, after observing the native birth, never to create the birth.
    await command('tag @e[type=minecraft:cow,distance=..8] add world_gesture_fixture');
    async function oneBaby(){return (await entities()).find(n=>n.type==='minecraft:cow'&&n.baby===true);}
    actionSamples.push({case:'entity_breeding',arrangeAuthority:'diagnostic_admin_command',actAuthority:'PLAYTEST',observationAuthority:'CLIENT_KNOWN',adultCountBefore:2,babyCountBefore:0,babyObserved:calf.baby,consumedWheat:2,serverLoveStateRead:false,forcedOffspring:false});
  });
  await check('USER lead attachment, native fence binding and release are client-observed',async()=>{
    await reset();await command(`summon minecraft:cow ${at.x} ${at.y} ${at.z} {NoAI:1b,Silent:1b,Tags:["world_gesture_fixture"]}`);await equip('lead',2);
    let cow=await poll(()=>one('cow'),e=>e?.targetRef&&e.leashed===false,'unleashed cow');await use(cow);
    cow=await poll(()=>one('cow'),e=>e?.leashed&&e.leashHolderKnown&&e.leashHeldByPlayer,'native lead attached to player');
    const fence={x:origin.x+1,y:origin.y,z:origin.z+1};await nativeBlock(fence,'minecraft:oak_fence');
    const ref=await api('GET','/v0/world/block?'+new URLSearchParams(fence));await use(ref);
    await poll(()=>one('cow'),e=>e?.leashed&&e.leashHolderKnown&&e.leashHeldByPlayer===false,'native leash transferred to fence');
    const knot=await poll(()=>one('leash_knot'),e=>!!e,'client leash knot');assert(knot);
    // The knot occludes the fence's center. Target that real pickable entity instead of
    // bypassing the block's fresh native hit check or deleting the leash via Debug.
    assert(knot.targetRef);const broken=await finish(await start(knot.targetRef,'attack'));assert.equal(broken.state,'completed',JSON.stringify(broken));
    await poll(()=>one('cow'),e=>e?.leashed===false,'leash releases after native knot attack',7000);
    actionSamples.push({case:'entity_leash',arrangeAuthority:'diagnostic_admin_command_and_fixture_block',actAuthority:'PLAYTEST',observationAuthority:'CLIENT_KNOWN',attachedToPlayer:true,transferredToFence:true,knotObserved:true,releasedAfterKnotAttack:true});
    await arrange(fence,'minecraft:air');
  });
  await check('USER villager trade opens from world target and exchanges real menu items',async()=>{
    await reset();
    for(const text of villagerFixtureCommands({target:p.target,tag:'world_gesture_fixture',position:[at.x,at.y,at.z],sells:['stone'],zeroPriceMultiplier:true}))await command(text);
    await equip('emerald',4);await use(await poll(()=>one('villager'),e=>!!e?.targetRef,'villager discovery'));
    await wait(t=>/MerchantScreen$/.test(t.screenClass)&&nodes(t).some(n=>n.controlKey==='merchant.trade.0'),'normal merchant GUI');
    await ui({controlKey:'merchant.trade.0'});
    await wait(t=>nodes(t).some(n=>n.role==='slot'&&n.slot===2&&n.item==='minecraft:stone'&&n.count===1),'merchant result ready');
    await ui({role:'slot',slot:2});await wait(t=>t.carriedStack.id==='minecraft:stone'&&t.carriedStack.count===1,'real traded stone carried');
    const tree=await api('GET','/v0/ui/tree');const destination=nodes(tree).find(n=>n.role==='slot'&&n.slot>=3&&n.count===0);assert(destination);await ui({nodeId:destination.nodeId});
    const after=await wait(t=>t.carriedStack.empty&&nodes(t).some(n=>n.role==='slot'&&n.slot===destination.slot&&n.item==='minecraft:stone'&&n.count===1),'trade output deposited');
    assert.equal(nodes(after).filter(n=>n.role==='slot'&&n.slot!==2&&n.item==='minecraft:emerald').reduce((sum,n)=>sum+n.count,0),3,'One actual payment, including any refilled input');
    await key(256);await wait(t=>!t.screenClass,'normal merchant close');assert.equal((await api('GET','/v0/control/mode')).mode,'TAKEOVER');await zero();
    actionSamples.push({case:'entity_trade',arrangeAuthority:'diagnostic_admin_command_custom_offer',actAuthority:'PLAYTEST',worldUseOpenedMerchant:true,semanticOfferAndSlots:true,emeraldBefore:4,emeraldAfter:3,stoneReceived:1,directTradeMutation:false});
  });
  await reset();await command('gamemode creative @s');
}
