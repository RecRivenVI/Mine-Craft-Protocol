import assert from 'node:assert/strict';

// Diagnostic Arrange in the restored disposable world; Assert reads only actual UI glyphs.
export async function hudVitalActions({target,api,command,wait,tree,key,check,samples,serverTicksAfter,readOnly,readConsumers}) {
 const icons=(t,family)=>t.presentation?.children?.filter(n=>n.controlKey?.startsWith('hud.vitals.'+family+'.'))??[];
 const glyphs=t=>t.presentation?.children?.filter(n=>n.controlKey?.startsWith('hud.vitals.'))??[];
 const current=t=>t.presentation?.hudIconSampleStatus==='CURRENT';
 const label=(t,family,pattern)=>icons(t,family).some(n=>pattern.test(n.label));
 const natural=target.startsWith('1.')?'naturalRegeneration':'minecraft:natural_health_regeneration';
 async function heal(){await command('effect clear @s');await command('effect give @s minecraft:instant_health 1 20 true');await command('effect give @s minecraft:saturation 1 20 true');await command('effect clear @s');}
 function validate(t){
  assert.equal(t.dataCategory,'USER');assert(current(t));
  for(const n of glyphs(t)){
   assert.equal(n.role,'status_icon');assert.equal(n.active,false);assert.equal(n.visible,true);assert.deepEqual(n.actions,[]);
   assert.equal(n.iconSource,'last_completed_native_hud_draw');assert.equal(n.valueSemantics,'ordered_native_glyph_composition_not_raw_statistics');
   assert.equal(typeof n.resourceId,'string');assert.equal(n.width,9);assert.equal(n.height,9);assert(n.x>=0&&n.y>=0&&n.x+9<=t.width&&n.y+9<=t.height);
   for(const field of ['health','maxHealth','absorption','foodLevel','saturation','airSupply','maxAirSupply','uuid','nbt','resourceVersion'])assert.equal(n[field],undefined,field);
  }
 }
 function remember(kind,t){validate(t);samples.push({kind,arrangeAuthority:'diagnostic_admin_command',assertAuthority:'USER_NATIVE_UI',mode:'READ',inputLease:false,status:t.presentation.hudIconSampleStatus,icons:glyphs(t)});}
 try{
  await check('HUD: vital normal glyphs are native layers, not raw player statistics',async()=>{
   await command('fill -3 79 -3 3 79 3 minecraft:stone');await command('fill -2 80 -2 2 83 3 minecraft:air');await command('tp @s 0.5 80 0.5 0 0');
   await command('clear @s');await command('gamemode survival @s');await command('difficulty normal');await command('gamerule '+natural+' false');await heal();
   // Native ServerPlayer protects a newly joined player for 60 ticks on older Targets.
   // Observe a diagnostic server-tick barrier before damage; never infer success from submission.
   await serverTicksAfter(65);
   await readOnly(async()=>{
    const t=await wait(t=>current(t)&&icons(t,'health').filter(n=>n.label==='hud/heart/full').length===10&&icons(t,'food').length>=10,'native full health/food glyph frame');
    remember('normal',t);assert.equal(icons(t,'armor').length,0);assert.equal(icons(t,'air').length,0);
   });
  });
  await check('HUD: fractional damage exposes only the native rounded glyph composition',async()=>{
   await command('damage @s 6.2 minecraft:generic');
   await readOnly(async()=>{
    const t=await wait(t=>current(t)&&icons(t,'health').filter(n=>n.label==='hud/heart/full').length===7,'ceil health draw, seven full hearts');
    remember('fractional_damage',t);assert(!label(t,'health',/^hud\/heart\/half$/));
   });
  });
  await check('HUD: armor and hunger observe actual full/empty native icons',async()=>{
   await command('item replace entity @s armor.chest with minecraft:iron_chestplate');await command('effect give @s minecraft:hunger 8 100 true');
   await readOnly(async()=>{
    const t=await wait(t=>current(t)&&label(t,'armor',/armor_full/)&&label(t,'food',/food_empty_hunger/),'armor and exhausted hunger icons');
    remember('armor_hunger',t);
   });await heal();
  });
  await check('HUD: poisoned withered and absorption heart themes are drawn before publication',async()=>{
   for(const [effect,pattern] of [['poison',/poisoned/],['wither',/withered/],['absorption',/absorbing/]]){
    await heal();await command('effect give @s minecraft:'+effect+' 10 0 true');
    await readOnly(async()=>remember(effect,await wait(t=>current(t)&&label(t,'health',pattern),'native '+effect+' heart theme')));
   }await heal();
  });
  await check('HUD: submerged air bubbles are native draw units, not precise air ticks',async()=>{
   await command('fill -2 79 -2 2 83 2 minecraft:glass');await command('fill -1 80 -1 1 82 1 minecraft:water');await command('tp @s 0.5 80 0.5 0 0');
   await readOnly(async()=>{
    remember('air_full',await wait(t=>current(t)&&icons(t,'air').length>=9,'submerged bubble row'));
    remember('air_depleted',await wait(t=>current(t)&&icons(t,'air').length>0&&(icons(t,'air').filter(n=>n.label==='hud/air').length<10||label(t,'air',/air_(?:popping|empty)/)),'native air bubble depletion'));
   });await command('fill -2 80 -2 2 83 2 minecraft:air');await command('tp @s 0.5 80 0.5 0 0');
  });
  await check('HUD: mount hearts replace food through actual riding state',async()=>{
   await heal();await command('kill @e[tag=hud_vital_fixture]');await command('summon minecraft:horse 0.5 80 2.5 {Tags:["hud_vital_fixture"],Tame:1b,NoAI:1b,Silent:1b}');
   await command('ride @s mount @e[tag=hud_vital_fixture,limit=1]');
   await readOnly(async()=>{
    const t=await wait(t=>current(t)&&icons(t,'mount_health').length>0&&icons(t,'food').length===0,'mount native hearts without food');remember('mount',t);
   });await command('ride @s dismount');await command('kill @e[tag=hud_vital_fixture]');
  });
  await check('HUD: F1 Screen and creative visibility never disclose cached vital glyphs',async()=>{
   await command('gamemode survival @s');await heal();await key(290);
   await readOnly(async()=>{const t=await tree();assert.equal(t.presentation.hudIconSampleStatus,'OCCLUDED');assert.equal(glyphs(t).length,0);});
   await key(290);await wait(t=>current(t)&&icons(t,'health').length>0,'HUD visible again');await key(256);
   await readOnly(async()=>{const t=await tree();assert(t.screenClass.endsWith('PauseScreen'));assert.equal(t.presentation.hudIconSampleStatus,'OCCLUDED');assert.equal(glyphs(t).length,0);});
   await key(256);await command('gamemode creative @s');await readOnly(async()=>{const t=await wait(t=>current(t)&&glyphs(t).length===0,'creative has no survival icons');remember('creative_hidden',t);});
  });
  await check('HUD: MCP and isolated web preserve READ-only vital composition without Lease',async()=>{
   await command('gamemode survival @s');await heal();
   await readOnly(async()=>{
    await wait(t=>current(t)&&icons(t,'health').length>0,'consumer health glyph frame');
    const consumer=await readConsumers();try{samples.push(...await consumer.verifyVitals());}finally{await consumer.close();}
   });
  });
 }finally{
  await command('gamemode creative @s');await command('effect clear @s');await command('kill @e[tag=hud_vital_fixture]');await command('fill -2 80 -2 2 83 2 minecraft:air');await command('clear @s');await command('tp @s 0.5 80 0.5 0 0');
 }
}
