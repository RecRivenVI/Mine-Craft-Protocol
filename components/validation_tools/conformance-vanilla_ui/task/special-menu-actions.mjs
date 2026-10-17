import assert from 'node:assert/strict';

// Arrange commands belong only to the restored disposable fixture. All menu actions use
// the real USER interaction tree and native callbacks, never screen coordinates or Debug setters.
export async function specialMenuActions({target,api,command,wait,tree,nodes,ui,key,use,fixtureVisible,shiftItem,check,samples,readConsumers}) {
  const pause=()=>new Promise(r=>setTimeout(r,100));
  async function zero(){const s=await api('GET','/v0/input/state');assert.equal(s.pressedKeyCount,0);assert.equal(s.pressedButtonCount,0);return s;}
  async function closed(){await wait(t=>!t.screenClass,'native menu closes');await zero();}
  async function prepare(){await command('clear @s');await command('fill -3 79 -3 3 79 5 minecraft:stone');await command('fill -2 80 1 2 83 4 minecraft:air');await command('tp @s 0.5 80 0.5 0 0');}
  async function inventory(){await key(69);let t=await wait(t=>/CreativeModeInventoryScreen$/.test(t.screenClass),'creative inventory');const tab=nodes(t).find(n=>n.resourceId==='minecraft:inventory');assert(tab);await ui({nodeId:tab.nodeId});return tree();}
  async function openEntity(entity){
    assert(entity?.targetRef);const before=await api('GET','/v0/player');
    await api('POST','/v0/input/key',{key:340,scanCode:0,action:1,modifiers:1});
    try{
      // Player movement input is sampled on ticks. A key callback alone is not proof
      // that native secondary-use state has reached the player before an already-aimed use.
      const end=Date.now()+5000;let current;
      do{current=await api('GET','/v0/player');if(current.clientTick>=before.clientTick+2)break;await pause();}while(Date.now()<end);assert(current.clientTick>=before.clientTick+2,'native modifier ticks');
      const op=await api('POST','/v0/pipelines',{steps:[{type:'world.action',targetRef:entity.targetRef,action:'use'}],timeoutMs:12000});const done=await api('POST','/v0/operations/'+op.operationId+'/wait',{timeoutMs:14000});assert.equal(done.state,'completed',JSON.stringify(done));
    }finally{await api('POST','/v0/input/key',{key:340,scanCode:0,action:0,modifiers:0});}
    return wait(t=>/HorseInventoryScreen$/.test(t.screenClass),'native animal inventory');
  }
  async function entityMenu(type,extra=''){
    await prepare();await command('kill @e[tag=ui_special_fixture]');
    await command(`summon minecraft:${type} 0.5 80 2.5 {Tags:["ui_special_fixture"],Tame:1b,NoAI:1b,Silent:1b${extra}}`);
    const end=Date.now()+10000;let entity;
    do{entity=(await api('GET','/v0/world/entities?radius=8&limit=32')).entities.find(n=>n.type==='minecraft:'+type);if(entity?.targetRef)break;await pause();}while(Date.now()<end);assert(entity?.targetRef,'owned entity received');
    return openEntity(entity);
  }
  await check('special menus: lectern page navigation and real Take Book',async()=>{
    await prepare();await command('setblock 0 81 2 minecraft:lectern[facing=north]');
    const item=target.startsWith('1.20')?'minecraft:written_book{title:"UI",author:"Agent",pages:[\'{"text":"Page A"}\',\'{"text":"Page B"}\']}':target.startsWith('1.')?'minecraft:written_book[written_book_content={title:"UI",author:"Agent",pages:[\'{"text":"Page A"}\',\'{"text":"Page B"}\']}]':'minecraft:written_book[written_book_content={title:"UI",author:"Agent",pages:[{text:"Page A"},{text:"Page B"}]}]';
    await command('item replace entity @s weapon.mainhand with '+item);await wait(t=>t.presentation.children.some(n=>n.selected&&n.item==='minecraft:written_book'),'written book received');await fixtureVisible('lectern');await use();
    const end=Date.now()+10000;let block;do{block=await api('GET','/v0/world/block?x=0&y=81&z=2');if(block.state.includes('has_book=true'))break;await pause();}while(Date.now()<end);assert(block.state.includes('has_book=true'),'native book insertion');
    await command('clear @s');await wait(t=>t.presentation.children.some(n=>n.selected&&n.item==='minecraft:air'),'empty hand received');await use();
    let t=await wait(t=>/LecternScreen$/.test(t.screenClass)&&t.screenData?.pageText==='Page A','lectern visible first page');assert.equal(t.screenData.pageCount,2);
    const oldContext=t.editorContextKey,next=nodes(t).find(n=>n.controlKey==='book.next_page');assert(next?.active);await ui({nodeId:next.nodeId});
    t=await wait(t=>t.screenData?.pageText==='Page B'&&t.screenData.pageIndex===1,'native lectern page changed');assert.notEqual(t.editorContextKey,oldContext);
    const take=nodes(t).find(n=>n.translationKey==='lectern.take_book'||n.label==='Take Book');assert(take?.active);await ui({nodeId:take.nodeId});await closed();
    const inventoryTree=await inventory();assert(nodes(inventoryTree).some(n=>n.role==='slot'&&n.item==='minecraft:written_book'&&n.count===1));await key(256);await closed();
    block=await api('GET','/v0/world/block?x=0&y=81&z=2');assert(block.state.includes('has_book=false'));await command('setblock 0 81 2 minecraft:air');
    samples.push({kind:'lectern',arrangeAuthority:'diagnostic_admin_command',actAuthority:'PLAYTEST',pagesRead:2,takeBookIntoActualInventory:true,semanticTargets:true});
  });
  await check('special menus: tamed horse native inventory equips and returns saddle',async()=>{
    await entityMenu('horse');await key(256);await closed();await command('item replace entity @s weapon.mainhand with minecraft:saddle');
    await wait(t=>t.presentation.children.some(n=>n.selected&&n.item==='minecraft:saddle'),'saddle received');
    // The same tame animal is still present. Sneak-use opens its real inventory, without mounting.
    const entity=(await api('GET','/v0/world/entities?radius=8&limit=32')).entities.find(n=>n.type==='minecraft:horse');assert(entity?.targetRef);
    await openEntity(entity);
    let t=await wait(t=>/HorseInventoryScreen$/.test(t.screenClass)&&nodes(t).some(n=>n.item==='minecraft:saddle'),'horse saddle source');const slots=nodes(t).filter(n=>n.role==='slot');assert.equal(new Set(slots.map(n=>n.slot)).size,slots.length);
    const playerSlots=new Set(slots.slice(-36).map(n=>n.nodeId)),source=slots.find(n=>n.item==='minecraft:saddle');assert(playerSlots.has(source.nodeId));await shiftItem('minecraft:saddle');
    t=await wait(t=>nodes(t).some(n=>n.item==='minecraft:saddle'&&n.nodeId!==source.nodeId),'native saddle auto-equipped');const equipped=nodes(t).find(n=>n.item==='minecraft:saddle');assert(!playerSlots.has(equipped.nodeId));
    await shiftItem('minecraft:saddle');await wait(t=>nodes(t).some(n=>n.item==='minecraft:saddle'&&playerSlots.has(n.nodeId))&&nodes(t).find(n=>n.nodeId===equipped.nodeId)?.count===0,'native saddle returned to player');assert((await tree()).carriedStack.empty);await key(256);await closed();await command('kill @e[tag=ui_special_fixture]');
    samples.push({kind:'horse_inventory',arrangeAuthority:'diagnostic_admin_command_tamed_entity',actAuthority:'PLAYTEST',semanticSlots:true,equippedAndReturned:true,slotCount:slots.length});
  });
  await check('special menus: donkey chest uses native cargo slots',async()=>{
    await entityMenu('donkey',',ChestedHorse:1b');await key(256);await closed();await command('item replace entity @s weapon.mainhand with minecraft:stone 8');
    await wait(t=>t.presentation.children.some(n=>n.selected&&n.item==='minecraft:stone'&&n.count===8),'cargo received');
    const entity=(await api('GET','/v0/world/entities?radius=8&limit=32')).entities.find(n=>n.type==='minecraft:donkey');assert(entity?.targetRef);
    await openEntity(entity);
    let t=await wait(t=>/HorseInventoryScreen$/.test(t.screenClass)&&nodes(t).some(n=>n.item==='minecraft:stone'),'donkey source');const slots=nodes(t).filter(n=>n.role==='slot'),playerSlots=new Set(slots.slice(-36).map(n=>n.nodeId));assert(slots.length-36>=16);assert(playerSlots.has(slots.find(n=>n.item==='minecraft:stone').nodeId));
    await shiftItem('minecraft:stone');t=await wait(t=>nodes(t).some(n=>n.item==='minecraft:stone'&&!playerSlots.has(n.nodeId)&&n.count===8),'native cargo transferred');await shiftItem('minecraft:stone');await wait(t=>nodes(t).some(n=>n.item==='minecraft:stone'&&playerSlots.has(n.nodeId)&&n.count===8),'native cargo returned');assert((await tree()).carriedStack.empty);await key(256);await closed();await command('kill @e[tag=ui_special_fixture]');
    samples.push({kind:'donkey_inventory',arrangeAuthority:'diagnostic_admin_command_tamed_chested_entity',actAuthority:'PLAYTEST',semanticSlots:true,cargoTransferredAndReturned:true,slotCount:slots.length});
  });
  if(!target.startsWith('1.20'))await check('special menus: crafter disabled cells remain clickable and revisioned',async()=>{
    await prepare();await command('setblock 0 81 2 minecraft:crafter');await fixtureVisible('crafter');await use();let t=await wait(t=>/CrafterScreen$/.test(t.screenClass),'crafter');const consumers=await readConsumers();try{
    const cell=nodes(t).find(n=>n.role==='slot'&&n.slot===0);assert(cell);assert.equal(cell.slotDisabled,false);assert(cell.active&&cell.actions.includes('click'));const revision=t.menuRevision;
    samples.push(...await consumers.verifyCrafter(false));
    await ui({nodeId:cell.nodeId});t=await wait(t=>nodes(t).find(n=>n.nodeId===cell.nodeId)?.slotDisabled===true,'native disabled cell');assert(t.menuRevision>revision);const disabled=nodes(t).find(n=>n.nodeId===cell.nodeId);assert(disabled.active&&disabled.actions.includes('click'));
    samples.push(...await consumers.verifyCrafter(true));
    const before=await zero();await assert.rejects(api('POST','/v0/ui/action',{selector:{nodeId:cell.nodeId},action:'click'},{'X-MCP-Expected-Menu-Revision':String(revision)}),e=>e.code==='STALE_MENU_REVISION');assert.equal((await zero()).inputDispatchSequence,before.inputDispatchSequence);assert.equal(nodes(await tree()).find(n=>n.nodeId===cell.nodeId).slotDisabled,true);
    await ui({nodeId:cell.nodeId});await wait(t=>nodes(t).find(n=>n.nodeId===cell.nodeId)?.slotDisabled===false,'native enabled cell restored');assert((await tree()).carriedStack.empty);await key(256);await closed();await command('setblock 0 81 2 minecraft:air');
    samples.push({kind:'crafter',arrangeAuthority:'diagnostic_admin_command',actAuthority:'PLAYTEST',disabledThenEnabled:true,menuRevisionAdvanced:true,semanticSlots:true});
    }finally{await consumers.close();}
  });
  await command('clear @s');await zero();
}
