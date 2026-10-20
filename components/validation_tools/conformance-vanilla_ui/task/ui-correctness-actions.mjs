import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

export async function uiCorrectnessActions({api,command,key,tree,wait,nodes,ui,check,samples,repo,instance,target}) {
  await check('UI completion: declared right and Shift clicks execute actual slot transfers',async()=>{
    // Only the immutable disposable copy is changed. Commands are diagnostic Arrange.
    await command('fill -2 79 -2 2 79 2 minecraft:stone');
    await command('tp @s 0.5 80 0.5 0 0');
    await command('gamemode survival');await command('clear @s');
    await command('item replace entity @s hotbar.0 with minecraft:stone 8');
    let failure;
    try {
      await key(69);
      const initial=await wait(value=>value.screenClass.endsWith('InventoryScreen')
        &&nodes(value).some(node=>node.role==='slot'&&node.item==='minecraft:stone'&&node.count===8),'owned eight-item inventory stack');
      const source=nodes(initial).filter(node=>node.role==='slot'&&node.visible&&node.item==='minecraft:stone'&&node.count===8);
      assert.equal(source.length,1);assert.equal(source[0].slotOwner,'player_inventory');
      for(const action of ['right_click','shift_click'])assert(source[0].actions.includes(action));
      const before=await api('GET','/v0/input/state');
      await ui({nodeId:source[0].nodeId},'right_click');
      await wait(value=>value.carriedStack?.id==='minecraft:stone'&&value.carriedStack.count===4
        &&nodes(value).some(node=>node.nodeId===source[0].nodeId&&node.count===4),'native right-click splits the stack');
      await ui({nodeId:source[0].nodeId},'click');
      await wait(value=>value.carriedStack?.empty&&nodes(value).some(node=>node.nodeId===source[0].nodeId&&node.count===8),'native click restores the split');
      await ui({nodeId:source[0].nodeId},'shift_click');
      const moved=await wait(value=>value.carriedStack?.empty
        &&nodes(value).some(node=>node.nodeId===source[0].nodeId&&node.count===0)
        &&nodes(value).some(node=>node.role==='slot'&&node.nodeId!==source[0].nodeId&&node.item==='minecraft:stone'&&node.count===8),'native Shift transfers to another semantic slot');
      const after=await api('GET','/v0/input/state');
      assert(after.inputDispatchSequence>before.inputDispatchSequence);
      assert.equal(after.pressedKeyCount,0);assert.equal(after.pressedButtonCount,0);
      assert.equal(nodes(moved).filter(node=>node.role==='slot'&&node.item==='minecraft:stone').reduce((sum,node)=>sum+node.count,0),8);
      samples.push({case:'declared_slot_action_aliases',feedback:'CF-071',rightClickSplit:true,shiftClickTransferred:true,
        semanticTarget:true,handwrittenCoordinates:false,act:'GAME_ROUTED',arrange:'diagnostic_player_commands',heldKeys:0,heldButtons:0});
    } catch(error) {failure=error;throw error;}
    finally {
      try {
        if((await tree()).screenClass.endsWith('InventoryScreen'))await key(256);
        await command('clear @s');await command('gamemode creative');
      } catch(error) {
        if(failure)failure.message+='; cleanup: '+error.message;
        else throw error;
      }
    }
  });
  await check('UI completion: HUD ready and Native MCP page failure explanation preserve read-only authority',async()=>{
    const condition={type:'screen',category:'USER',open:false,ready:true};
    const ready=await api('POST','/v0/assert',{condition});
    assert.equal(ready.evidence.readiness.kind,'hud');assert.equal(ready.evidence.pageReady,true);
    await key(69);await wait(value=>value.screenClass.endsWith('CreativeModeInventoryScreen'),'readiness inventory');
    const before=await api('GET','/v0/input/state');
    let nativeFailure;
    await assert.rejects(api('POST','/v0/assert',{condition}),error=>{
      nativeFailure=error.nativeError;
      return error.code==='ASSERTION_FAILED'&&nativeFailure.readiness?.failures.includes('SCREEN_NOT_READY');
    });
    const missing={type:'screen',category:'USER',ready:true,selector:{nodeId:'ui-conformance:absent'}};
    await assert.rejects(api('POST','/v0/wait/until',{condition:missing,timeoutMs:100}),error=>
      error.code==='WAIT_TIMEOUT'&&error.nativeError.readiness?.failures.includes('TARGET_NOT_FOUND_OR_NOT_VISIBLE'));
    const require=createRequire(resolve(repo,'components/companion/package.json'));
    const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
    const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
    const env=Object.fromEntries(Object.entries(process.env).filter(([name,value])=>typeof value==='string'
      &&!['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE','MCP_MINECRAFT_INSTANCE_DIR'].includes(name.toUpperCase())));
    env.MCP_MINECRAFT_INSTANCE_DIR=instance;
    const client=new Client({name:'ui-readiness-conformance',version:'1.0.0'});
    const transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
    try {
      await client.connect(transport);
      const session=await client.callTool({name:'minecraft_get_session',arguments:{}});
      assert.equal(session.structuredContent.data.target,target);
      const response=await client.callTool({name:'minecraft_assert',arguments:{condition:missing}});
      assert.equal(response.isError,true);
      const error=response.structuredContent.error;
      assert.equal(error.code,'ASSERTION_FAILED');
      assert(error.readiness.failures.includes('TARGET_NOT_FOUND_OR_NOT_VISIBLE'));
      assert(!JSON.stringify(error.readiness).includes('ui-conformance:absent'));
      const after=await api('GET','/v0/input/state');
      assert.equal(after.inputDispatchSequence,before.inputDispatchSequence);
      assert.equal(after.pressedKeyCount,0);assert.equal(after.pressedButtonCount,0);
      samples.push({case:'hud_page_readiness',feedback:['CF-019','CF-023'],nativeFailure,readiness:error.readiness,
        hudReady:true,realMcpStdio:true,hiddenTargetProbed:false,inputDelta:0});
    } finally {await client.close();await key(256);}
  });
}
