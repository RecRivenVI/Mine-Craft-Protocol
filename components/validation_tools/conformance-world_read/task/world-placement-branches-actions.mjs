import assert from 'node:assert/strict';

// Finite Vanilla fixtures: diagnostic Arrange, semantic/native USER Act, separate client readback.
export async function worldPlacementBranchesActions({api,command,reset,poll,player,zero,check,actionSamples}) {
  const at={x:0,y:80,z:2}, floor={x:0,y:79,z:2};let failure;
  const point=p=>api('GET','/v0/world/block?'+new URLSearchParams(p));
  async function block(p,state){await command(`setblock ${p.x} ${p.y} ${p.z} minecraft:${state}`);return poll(()=>point(p),b=>b.available&&b.block==='minecraft:'+state.split('[')[0],'diagnostic block received: '+state);}
  async function prepare(gameMode='creative',item='stone') {await command('clear @s');await command('kill @e[type=minecraft:item,distance=..24]');await command('gamemode '+gameMode+' @s');await command('fill -2 80 1 2 84 4 minecraft:air');await command('fill -2 79 1 2 79 4 minecraft:stone');await reset();await command('item replace entity @s weapon.mainhand with minecraft:'+item+' 16');await poll(()=>api('GET','/v0/ui/tree'),t=>t.presentation?.children?.some(n=>n.selected&&n.item==='minecraft:'+item),'native held block received');}
  async function pipeline(steps){const op=await api('POST','/v0/pipelines',{steps,timeoutMs:15000}),done=await api('POST','/v0/operations/'+op.operationId+'/wait',{timeoutMs:18000});await zero();return done;}
  async function place(p,{face='up',hit={u:.5,v:.5},expected='minecraft:air',sneak=false}={}){
    const known=await point(p);assert(known.targetRef);const step={type:'world.action',targetRef:known.targetRef,action:'place',face,hit,expectedDestinationBlockId:expected};
    const done=await pipeline(sneak?[{type:'key',key:340,action:1},step,{type:'key',key:340,action:0}]:[step]);assert.equal(done.state,'completed',JSON.stringify(done));const r=done.result.steps[sneak?1:0].result;assert.equal(r.evidenceAuthority,'PLAYTEST');assert.equal(r.directMutationUsed,false);assert.equal(r.effectConfirmed,false);return r;
  }
  try{
    await check('WU-08 precise placement follows the actual fractional native outline',async()=>{
      await prepare();await block(at,'oak_slab[type=bottom]');const r=await place(at);await poll(()=>point({...at,y:81}),p=>p.block==='minecraft:stone','native placement over a bottom slab');assert.equal(r.actualHit.face,'up');assert(Math.abs(r.actualHit.position.y-80.5)<.025,JSON.stringify(r));actionSamples.push({case:'placement_fractional_outline',nativeExpectedTop:80.5,result:r,actAuthority:'PLAYTEST',inputCoordinateCompensation:false});
      await prepare();await block(at,'chest[facing=north]');const chest=await place(at,{sneak:true});await poll(()=>point({...at,y:81}),p=>p.block==='minecraft:stone','native sneak placement above chest');assert(Math.abs(chest.actualHit.position.y-80.875)<.025);assert.equal((await api('GET','/v0/ui/tree')).screenClass,'');actionSamples.push({case:'placement_sneak_interactive_outline',result:chest,nativeExpectedTop:80.875,actAuthority:'PLAYTEST'});
    });
    await check('WU-08 native collision and unsupported attachment reject without a false placement',async()=>{
      await prepare();const feet={x:0,y:79,z:0},collision=await place(feet);assert.equal(collision.placementOutcome,'native_rejected',JSON.stringify(collision));assert.equal((await point({x:0,y:80,z:0})).block,'minecraft:air');
      await prepare('creative','torch');const ceiling={x:0,y:82,z:2};await block(ceiling,'stone');const unsupported=await place(ceiling,{face:'down'});assert.equal(unsupported.placementOutcome,'native_rejected',JSON.stringify(unsupported));assert.equal((await point({...ceiling,y:81})).block,'minecraft:air');actionSamples.push({case:'placement_native_refusal',collision,unsupported,actAuthority:'PLAYTEST',serverValidationObserved:false});
    });
    await check('WU-08 placement replaces actual fluid and preserves native orientation',async()=>{
      await prepare();for(const p of [{x:-1,y:80,z:2},{x:1,y:80,z:2},{x:0,y:80,z:1},{x:0,y:80,z:3}])await block(p,'stone');await block(at,'water');await reset(.5,81,1.5);const water=await place(floor,{expected:'minecraft:water'});await poll(()=>point(at),p=>p.block==='minecraft:stone','native fluid replacement');assert.equal(water.clientPlacementPredicted,true);actionSamples.push({case:'placement_replaceable_fluid',result:water,containedFixture:true,actAuthority:'PLAYTEST'});
      await prepare('creative','oak_stairs');const stairs=await place(floor,{hit:{u:.25,v:.75}}),after=await poll(()=>point(at),p=>p.block==='minecraft:oak_stairs','native directed stairs');assert(after.state.includes('facing=south'),after.state);assert(after.state.includes('half=bottom'));actionSamples.push({case:'placement_native_direction',result:stairs,after,actAuthority:'PLAYTEST'});
    });
    await check('WU-07 Adventure and unbreakable native refusal time out honestly; Creative breaks',async()=>{
      const refusals=[];for(const [gameMode,id] of [['adventure','stone'],['survival','bedrock']]) {await prepare(gameMode);await block(at,id);const b=await point(at),done=await pipeline([{type:'world.action',targetRef:b.targetRef,action:'mine',holdMs:350}]);assert.equal(done.state,'failed',JSON.stringify(done));assert.equal(done.errorCode,'WORLD_MINE_TIMEOUT',JSON.stringify(done));assert.equal((await point(at)).block,'minecraft:'+id);refusals.push({gameMode,id,error:done.errorCode,held:await zero()});}
      await prepare();await block(at,'stone');const b=await point(at),done=await pipeline([{type:'world.action',targetRef:b.targetRef,action:'mine',holdMs:1500}]);assert.equal(done.state,'completed',JSON.stringify(done));assert.equal(done.result.steps[0].result.clientBreakPredicted,true);await poll(()=>point(at),p=>p.block==='minecraft:air','native Creative instant break');actionSamples.push({case:'mining_native_refusals',refusals,creative:done,actAuthority:'PLAYTEST',protectionPluginClaimed:false});
    });
  }catch(error){failure=error;throw error;}finally{
    try{await command('gamemode creative @s');await command('clear @s');await command('kill @e[type=minecraft:item,distance=..24]');await command('fill -2 80 1 2 84 4 minecraft:air');await command('fill -2 79 1 2 79 4 minecraft:stone');await reset();await zero();}catch(error){actionSamples.push({case:'placement_branch_cleanup_failure',error:String(error)});if(!failure)throw error;}
  }
}
