import assert from 'node:assert/strict';

// Only used by this owned disposable fixture, never as automatic recovery of a USER action.
export async function clearWorldUiFixture({api,command,poll}) {
  await command('kill @e[type=minecraft:item,distance=..8]');
  await command('clear @s');
  let lastTick=-1,stable=0;
  await poll(async()=>({tree:await api('GET','/v0/ui/tree'),nearby:await api('GET','/v0/world/entities?radius=8&limit=128')}),({tree,nearby})=>{
    const selected=tree.presentation?.children?.find(n=>n.role==='hud_item'&&n.selected);
    const empty=!tree.screenClass&&selected?.item==='minecraft:air'&&selected.count===0;
    const noDrops=nearby.entitiesTruncated===false&&Array.isArray(nearby.entities)&&!nearby.entities.some(n=>n.type==='minecraft:item');
    if(!empty||!noDrops){stable=0;lastTick=-1;return false;}
    if(Number.isInteger(tree.clientTick)&&tree.clientTick!==lastTick){lastTick=tree.clientTick;stable++;}
    return stable>=3;
  },'owned fixture drops gone and empty selected hand received across client ticks',8000);
}

// One USER input path bridges world and UI. Commands only arrange the owned disposable
// fixture; no screenshots, hand-written screen coordinates or menu business calls are used.
export async function worldUiActions({api,command,poll,start,finish,key,wait,check,actionSamples,zero,nativeBlock,arrange,origin}) {
  const nodes=t=>(t.children??[]).flatMap(n=>[n,...nodes(n)]);
  const at={x:origin.x+2,y:origin.y,z:origin.z};
  async function block(){return api('GET','/v0/world/block?'+new URLSearchParams(at));}
  async function act(action='use'){
    const ref=(await block()).targetRef;assert(ref);
    const outcome=await finish(await start(ref,action));assert.equal(outcome.state,'completed',JSON.stringify(outcome));
    const result=outcome.result.steps[0].result;assert.equal(result.evidenceAuthority,'PLAYTEST');assert.equal(result.directMutationUsed,false);await zero();return result;
  }
  async function settled(){
    let stamp,stable=0;
    await wait(t=>{const now=t.screenRevision+':'+t.menuRevision;stable=now===stamp?stable+1:0;stamp=now;return stable>=3;},'native menu acknowledgements settled before next gesture');
  }
  async function ui(selector,action='click',extra={}){
    await settled();
    const outcome=await api('POST','/v0/ui/action',{selector,action,...extra});
    if(outcome.operationId)assert.equal((await finish(outcome)).state,'completed');
  }
  async function slot(index,extra={}){await ui({role:'slot',slot:index},'click',extra);}
  async function shift(selector){
    await api('POST','/v0/input/key',{key:340,scanCode:0,action:1,modifiers:1});
    try{await ui(selector,'click',{modifiers:1});}
    finally{await api('POST','/v0/input/key',{key:340,scanCode:0,action:0,modifiers:0});}
  }
  async function open(state,screen){
    await command('clear @s');await command('gamemode creative @s');
    await command(`tp @s ${origin.x+.5} ${origin.y} ${origin.z+.5}`);
    await nativeBlock(at,state);await clearWorldUiFixture({api,command,poll});await act();
    return wait(t=>t.screenClass.endsWith(screen),'native '+screen);
  }
  async function equip(item,count=1){
    await command(`item replace entity @s weapon.mainhand with minecraft:${item} ${count}`);
    await wait(t=>t.presentation.children.some(n=>n.selected&&n.item==='minecraft:'+item&&n.count===count),'fixture selected item received: '+item);
  }
  async function close(){await settled();await key(256);await wait(t=>!t.screenClass,'normal Screen close');await zero();}
  await check('USER native outline targeting handles thin floor lever and button without ID dispatch',async()=>{
    await command('clear @s');await command('gamemode creative @s');
    await command(`tp @s ${origin.x+.5} ${origin.y} ${origin.z+.5}`);
    const samples=[];
    for(const id of ['lever','stone_button']){
      await nativeBlock(at,`minecraft:${id}[face=floor,facing=north,powered=false]`);
      await act();const changed=await poll(block,b=>b.state.includes('powered=true'),'native thin '+id+' activated');
      samples.push({block:changed.block,powered:true});
      await arrange(at,'minecraft:air');
    }
    actionSamples.push({case:'native_outline_targeting',arrangeAuthority:'diagnostic_admin_command',actAuthority:'PLAYTEST',samples,handWrittenAimCoordinates:false,blockIdDispatch:false});
  });
  await check('USER world workbench opens and semantic 3x3 slots craft a real output',async()=>{
    await open('minecraft:crafting_table','CraftingScreen');await close();
    await equip('iron_ingot',9);await act();
    let tree=await wait(t=>t.screenClass.endsWith('CraftingScreen')&&nodes(t).some(n=>n.slot>=10&&n.item==='minecraft:iron_ingot'&&n.count===9),'crafting ingredients received');
    const source=nodes(tree).find(n=>n.role==='slot'&&n.slot>=10&&n.item==='minecraft:iron_ingot');assert(source);
    await ui({nodeId:source.nodeId});
    for(let i=1;i<=9;i++)await slot(i,{button:1});
    await wait(t=>t.carriedStack.empty&&nodes(t).filter(n=>n.role==='slot'&&n.slot>=1&&n.slot<=9&&n.item==='minecraft:iron_ingot'&&n.count===1).length===9&&nodes(t).some(n=>n.slot===0&&n.item==='minecraft:iron_block'&&n.count===1),'native recipe output');
    await slot(0);tree=await wait(t=>t.carriedStack.id==='minecraft:iron_block','crafted item carried');
    const destination=nodes(tree).find(n=>n.role==='slot'&&n.slot>=10&&n.count===0);assert(destination);await ui({nodeId:destination.nodeId});
    await wait(t=>t.carriedStack.empty&&nodes(t).some(n=>n.slot===destination.slot&&n.item==='minecraft:iron_block'&&n.count===1)&&nodes(t).filter(n=>n.slot>=1&&n.slot<=9).every(n=>n.count===0),'ingredients consumed and actual output deposited');
    const old={nodeId:destination.nodeId};await close();
    const rejected=await api('POST','/v0/ui/action',{selector:old,action:'click'},'UI_NODE_NOT_FOUND');assert(rejected.error);
    actionSamples.push({case:'world_ui_crafting',arrangeAuthority:'diagnostic_admin_command',actAuthority:'PLAYTEST',openedByWorldUse:true,semanticSlots:true,ironIngotsConsumed:9,ironBlockReceived:1,oldScreenTargetRejected:true});
    await arrange(at,'minecraft:air');
  });
  await check('USER anvil text entry takes output and reopens to verify the actual saved item name',async()=>{
    await open('minecraft:anvil','AnvilScreen');await close();
    await equip('iron_sword');await act();
    let tree=await wait(t=>t.screenClass.endsWith('AnvilScreen')&&nodes(t).some(n=>n.role==='slot'&&n.slot>=3&&n.item==='minecraft:iron_sword'),'anvil source');
    let source=nodes(tree).find(n=>n.role==='slot'&&n.slot>=3&&n.item==='minecraft:iron_sword');await shift({nodeId:source.nodeId});
    tree=await wait(t=>nodes(t).some(n=>n.slot===0&&n.item==='minecraft:iron_sword')&&nodes(t).some(n=>n.role==='text_field'&&n.value),'anvil real input and name field');
    const field=nodes(tree).find(n=>n.role==='text_field');assert(field);
    await ui({nodeId:field.nodeId},'type_text',{text:'Agent UI Before'});
    const beforeName=await wait(t=>nodes(t).some(n=>n.role==='text_field'&&n.value==='Agent UI Before')&&nodes(t).some(n=>n.slot===2&&n.item==='minecraft:iron_sword'),'first native renamed output');
    const beforeItem=nodes(beforeName).find(n=>n.role==='slot'&&n.slot===2);assert(beforeItem?.contentGuardAvailable);assert.match(beforeItem.contentRevision,/^[0-9a-f]{64}$/);
    assert(!JSON.stringify(beforeName).includes('contentFingerprint'),'USER must not expose private item fingerprints');
    await ui({nodeId:field.nodeId},'type_text',{text:'Agent UI Bridge'});
    const afterName=await wait(t=>nodes(t).some(n=>n.role==='text_field'&&n.value==='Agent UI Bridge')&&nodes(t).some(n=>n.slot===2&&n.item==='minecraft:iron_sword'&&n.contentRevision!==beforeItem.contentRevision),'same item/count output gets a new content revision');
    const afterItem=nodes(afterName).find(n=>n.role==='slot'&&n.slot===2);
    assert.equal(afterName.menuIdentity,beforeName.menuIdentity);assert.equal(afterItem.elementIdentity,beforeItem.elementIdentity);assert.equal(afterItem.item,beforeItem.item);assert.equal(afterItem.count,beforeItem.count);
    assert(afterName.menuRevision>beforeName.menuRevision,'Global menu revision must include native item metadata, not just ID/count');
    actionSamples.push({case:'same_item_count_metadata_revision',arrangeAuthority:'diagnostic_admin_command_creative',actAuthority:'PLAYTEST',changedBy:'native_anvil_text_input',beforeRevision:beforeItem.contentRevision,afterRevision:afterItem.contentRevision,sameMenuAndSlot:true,item:afterItem.item,count:afterItem.count,menuRevisionChanged:true,userPrivateFingerprintLeaked:false});
    await slot(2);tree=await wait(t=>t.carriedStack.id==='minecraft:iron_sword','rename result carried');
    const destination=nodes(tree).find(n=>n.role==='slot'&&n.slot>=3&&n.count===0);assert(destination);await ui({nodeId:destination.nodeId});await close();await act();
    tree=await wait(t=>t.screenClass.endsWith('AnvilScreen'),'anvil reopened');source=nodes(tree).find(n=>n.role==='slot'&&n.slot>=3&&n.item==='minecraft:iron_sword');assert(source);await shift({nodeId:source.nodeId});
    await wait(t=>nodes(t).some(n=>n.role==='text_field'&&n.value==='Agent UI Bridge'),'native name restored from actual renamed stack, not only the draft field');
    await shift({role:'slot',slot:0});await close();await arrange(at,'minecraft:air');
    actionSamples.push({case:'world_ui_anvil',arrangeAuthority:'diagnostic_admin_command_creative',actAuthority:'PLAYTEST',openedByWorldUse:true,semanticTextAndSlots:true,rename:'Agent UI Bridge',actualItemReopened:true,directItemDataMutation:false});
  });
  await check('USER ordinary use applies native axe shovel and hoe rules without typed tool dispatch',async()=>{
    await command('clear @s');await command('gamemode survival @s');
    await command(`tp @s ${origin.x+.5} ${origin.y} ${origin.z+.5}`);
    const samples=[];
    for(const [before,item,after] of [
      ['oak_log','diamond_axe','stripped_oak_log'],
      ['grass_block','diamond_shovel','dirt_path'],
      ['dirt','diamond_hoe','farmland']]){
      await nativeBlock(at,'minecraft:'+before);
      await equip('stick');
      await act();assert.equal((await block()).block,'minecraft:'+before,'Wrong item must not synthesize a tool effect');
      await equip(item);
      await act();const changed=await poll(block,b=>b.block==='minecraft:'+after,'native '+item+' effect');
      samples.push({before:'minecraft:'+before,item:'minecraft:'+item,after:changed.block,wrongItemUnchanged:true});
      await arrange(at,'minecraft:air');
    }
    actionSamples.push({case:'native_tool_rules',arrangeAuthority:'diagnostic_admin_command',actAuthority:'PLAYTEST',samples,toolIdDispatch:false,directBlockMutation:false});
  });
  await check('USER bucket fills and drains a cauldron through ordinary native use',async()=>{
    await command('clear @s');await command('gamemode survival @s');
    await nativeBlock(at,'minecraft:cauldron');
    await equip('water_bucket');
    await act();await poll(block,b=>b.block==='minecraft:water_cauldron'&&b.state.includes('level=3'),'native full water cauldron');
    await equip('bucket');
    await act();await poll(block,b=>b.block==='minecraft:cauldron','native bucket drains cauldron');
    actionSamples.push({case:'native_cauldron_bucket',arrangeAuthority:'diagnostic_admin_command',actAuthority:'PLAYTEST',fullWaterObserved:true,emptyCauldronObserved:true,fluidStorageWrite:false});
    await arrange(at,'minecraft:air');
  });
  await check('USER furnace uses semantic material and fuel slots then takes a real smelted output',async()=>{
    await open('minecraft:furnace','FurnaceScreen');await close();
    await command('item replace entity @s hotbar.0 with minecraft:raw_iron');
    await command('item replace entity @s hotbar.1 with minecraft:coal');
    await wait(t=>t.presentation.children.some(n=>n.selected&&n.item==='minecraft:raw_iron'&&n.count===1)&&t.presentation.children.some(n=>n.controlKey==='hud.hotbar.1'&&n.item==='minecraft:coal'&&n.count===1),'furnace material and fuel actually received');await act();
    let tree=await wait(t=>t.screenClass.endsWith('FurnaceScreen')&&['minecraft:raw_iron','minecraft:coal'].every(id=>nodes(t).some(n=>n.slot>=3&&n.item===id&&n.count===1)),'furnace prepared materials');
    let source=nodes(tree).find(n=>n.role==='slot'&&n.slot>=3&&n.item==='minecraft:raw_iron');assert(source);
    await ui({nodeId:source.nodeId});await slot(0);
    tree=await wait(t=>t.carriedStack.empty&&nodes(t).some(n=>n.slot===0&&n.item==='minecraft:raw_iron'),'real furnace input');
    source=nodes(tree).find(n=>n.role==='slot'&&n.slot>=3&&n.item==='minecraft:coal');assert(source);
    await ui({nodeId:source.nodeId});await slot(1);
    await wait(t=>t.carriedStack.empty&&nodes(t).some(n=>n.slot===2&&n.item==='minecraft:iron_ingot'&&n.count===1)&&nodes(t).some(n=>n.slot===0&&n.count===0),'native smelting, not a fixed delay',30000);
    await slot(2);tree=await wait(t=>t.carriedStack.id==='minecraft:iron_ingot','smelted output carried');
    const destination=nodes(tree).find(n=>n.role==='slot'&&n.slot>=3&&n.count===0);assert(destination);await ui({nodeId:destination.nodeId});
    await wait(t=>t.carriedStack.empty&&nodes(t).some(n=>n.slot===destination.slot&&n.item==='minecraft:iron_ingot'&&n.count===1)&&nodes(t).some(n=>n.slot===2&&n.count===0),'real smelted item deposited');
    await close();await arrange(at,'minecraft:air');
    actionSamples.push({case:'world_ui_furnace',arrangeAuthority:'diagnostic_admin_command_creative',actAuthority:'PLAYTEST',openedByWorldUse:true,semanticSlots:true,rawIronConsumed:1,ironIngotReceived:1,nativeProcessing:true,pollActualOutput:true,directMenuMutation:false});
  });
  await check('USER semantic menu actions tolerate unrelated hopper updates but reject changed targets and explicit global pins',async()=>{
    await open('minecraft:barrel[facing=west,open=false]','ContainerScreen');await close();
    const feeder={...at,y:at.y+1};
    await nativeBlock(feeder,'minecraft:hopper[facing=down,enabled=true]');
    await equip('dirt',8);
    await command(`item replace block ${feeder.x} ${feeder.y} ${feeder.z} container.0 with minecraft:stone 64`);
    await act();
    let tree=await wait(t=>t.screenClass.endsWith('ContainerScreen')&&nodes(t).some(n=>n.slot===0&&n.item==='minecraft:stone'&&n.count>=2)&&nodes(t).some(n=>n.slot>=27&&n.item==='minecraft:dirt'&&n.count===8),'native hopper transfer and player item received');
    const source=nodes(tree).find(n=>n.role==='slot'&&n.slot>=27&&n.item==='minecraft:dirt');assert(source);
    async function stageAway(target){
      const current=await api('GET','/v0/ui/tree');
      const distance=n=>Math.hypot(n.x-target.x,n.y-target.y);
      const candidate=nodes(current).filter(n=>n.role==='slot'&&n.slot!==0&&n.nodeId!==target.nodeId&&n.count===0&&n.active!==false&&n.visible!==false&&Number.isFinite(n.x)&&Number.isFinite(n.y)).sort((a,b)=>distance(b)-distance(a))[0];
      assert(candidate,'Need an actual unchanged semantic staging slot');
      await api('POST','/v0/ui/action',{selector:{nodeId:candidate.nodeId},action:'hover'});
    }
    await stageAway(source);
    const samples=[];let sampling=true,sampleError;
    const monitor=(async()=>{
      while(sampling&&samples.length<160){
        const current=await api('GET','/v0/ui/tree'),input=await api('GET','/v0/input/state');
        const other=nodes(current).find(n=>n.role==='slot'&&n.slot===0);
        samples.push({clientTick:current.clientTick,menuRevision:current.menuRevision,otherCount:other?.count,x:input.agentPointer?.x,y:input.agentPointer?.y});
        await new Promise(done=>setTimeout(done,25));
      }
    })().catch(error=>{sampleError=error;});
    try{await api('POST','/v0/ui/action',{selector:{nodeId:source.nodeId},action:'hover'});}
    finally{sampling=false;await monitor;}
    if(sampleError)throw sampleError;
    assert(samples.length>2,'Need actual concurrent native samples');
    assert(samples.some((sample,index)=>index>0&&sample.otherCount>samples[index-1].otherCount&&(sample.x!==samples[index-1].x||sample.y!==samples[index-1].y)),'Actual background slot transfer must overlap pointer motion: '+JSON.stringify(samples));
    tree=await api('GET','/v0/ui/tree');assert(tree.carriedStack.empty);
    await api('POST','/v0/ui/action',{selector:{nodeId:source.nodeId},action:'click'});
    await wait(t=>t.carriedStack.id==='minecraft:dirt'&&t.carriedStack.count===8,'native stable source pickup while another slot updates');
    await api('POST','/v0/ui/action',{selector:{role:'slot',slot:1},action:'click'});
    tree=await wait(t=>t.carriedStack.empty&&nodes(t).some(n=>n.slot===1&&n.item==='minecraft:dirt'&&n.count===8),'actual transfer into unchanged target slot');
    const background=nodes(tree).find(n=>n.role==='slot'&&n.slot===0);assert(background.count<64,'Hopper must still be running for rejection tests');
    await stageAway(background);
    const changed=nodes(await api('GET','/v0/ui/tree')).find(n=>n.role==='slot'&&n.slot===0);
    const rejected=await api('POST','/v0/ui/action',{selector:{nodeId:changed.nodeId},action:'click'},'UI_TARGET_INVALIDATED');
    assert((await api('GET','/v0/ui/tree')).carriedStack.empty,'Changed slot must not be picked up');await zero();
    await stageAway(nodes(tree).find(n=>n.role==='slot'&&n.slot===1));tree=await api('GET','/v0/ui/tree');
    const strict=await api('POST','/v0/ui/action',{selector:{role:'slot',slot:1},action:'hover'},['STALE_MENU_REVISION','UI_TARGET_INVALIDATED'],{'X-MCP-Expected-Menu-Revision':String(tree.menuRevision)});
    await zero();
    actionSamples.push({case:'dynamic_menu_independent_slots',arrangeAuthority:'diagnostic_admin_commands',actAuthority:'PLAYTEST',nativeHopper:true,semanticTargets:true,backgroundAndMotionOverlap:true,samples,actualDirtMoved:8,changedTargetRejected:rejected.error,explicitGlobalRevisionRejected:strict.error,semanticPointerStaging:true,handWrittenTargetCoordinates:false});
    await close();await arrange(feeder,'minecraft:air');await arrange(at,'minecraft:air');
  });
  await command('clear @s');
}
