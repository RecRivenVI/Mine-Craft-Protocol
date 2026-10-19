import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {finishBaselineWorldExit} from './interaction-baseline-actions.mjs';

export async function serverCompletionActions({api,connection,repo,instance,target,mode,acquire,release,key,click,clickNode,wait,nodes,check,actionSamples}) {
  const path='/v0/command/player',samples=[];
  const body=(input='he',maxResults=16)=>({action:'discover',completionSource:'SERVER',input,maxResults});
  const spacing=()=>new Promise(done=>setTimeout(done,300));
  async function clean(){const state=await api('GET','/v0/input/state');assert.equal(state.pressedKeyCount,0);assert.equal(state.pressedButtonCount,0);return state;}
  await check('server completion at title refuses missing player without input',async()=>{
    const before=await clean();
    await api('POST',path,body(),'PLAYER_COMMAND_CONTEXT_UNAVAILABLE');
    assert.equal((await clean()).inputDispatchSequence,before.inputDispatchSequence);
  });
  await acquire();await click('menu.singleplayer','Singleplayer');
  await wait(tree=>tree.screenClass.endsWith('SelectWorldScreen'),'server completion world list');
  const tree=await api('GET','/v0/ui/tree'),row=nodes(tree).find(value=>value.worldName==='World-Read-Alpha');assert(row);
  await clickNode(row);await click('selectWorld.select','Play Selected World');
  await wait(value=>!value.screenClass,'server completion world entered',150000);await release();
  await check('default command discovery retains honest client-local behavior',async()=>{
    const before=await clean();
    const local=await api('POST',path,{action:'discover',input:'he',maxResults:16});
    assert.equal(local.completionScope,'client_local_only');assert.equal(local.serverCompletionRequested,false);
    assert(local.suggestions.some(value=>value.text==='help'));assert.equal(local.commandExecuted,false);
    assert.equal((await clean()).inputDispatchSequence,before.inputDispatchSequence);
    samples.push({case:'server_completion_local_default',local});
  });
  await check('READ obtains actual server suggestions without Lease or command execution',async()=>{
    const before=await clean();
    const result=await api('POST',path,body());
    assert.equal(result.source,'current_player_server_command_suggestions');
    assert.equal(result.completionSource,'SERVER');assert.equal(result.completionScope,'server_current_player');
    assert.equal(result.dataCategory,'USER');assert.equal(result.serverCompletionRequested,true);
    assert.equal(result.serverCompletionSupported,true);assert.equal(result.serverAcceptanceConfirmed,false);
    assert.equal(result.commandExecuted,false);assert.equal(result.inputDispatched,false);assert.equal(result.requiresControlLease,false);
    assert.equal(result.humanFutureModified,false);assert.equal(result.serverBudgetShared,true);assert.equal(result.humanRequestsHavePriority,true);
    const help=result.suggestions.find(value=>value.text==='help');assert(help,JSON.stringify(result));
    assert.equal(help.start,0);assert.equal(help.end,2);
    assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
    assert.equal((await clean()).inputDispatchSequence,before.inputDispatchSequence);
    samples.push({case:'server_completion_actual_reply',result});
  });
  await check('server suggestions respect requested result budget and never claim global completeness',async()=>{
    await spacing();
    const result=await api('POST',path,body('',1));
    assert(result.suggestionCount>1,JSON.stringify(result));assert.equal(result.suggestions.length,1);
    assert.equal(result.suggestionsTruncated,true);assert.equal(result.completionComplete,false);
    assert.equal(result.nativeServerMayLimitSuggestions,true);
    samples.push({case:'server_completion_bounded',result});
  });
  await check('concurrent Agent server queries are single-flight or rate limited, not unbounded',async()=>{
    await spacing();const before=await clean();
    const responses=await Promise.all([0,1].map(()=>connection.fetch(path,{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(body()),signal:AbortSignal.timeout(5000)})));
    const values=await Promise.all(responses.map(async response=>({status:response.status,data:await response.json()})));
    assert.equal(values.filter(value=>value.status===200).length,1,JSON.stringify(values));
    const denied=values.find(value=>value.status!==200);assert(denied);
    assert(['PLAYER_COMMAND_COMPLETION_BUSY','PLAYER_COMMAND_COMPLETION_RATE_LIMITED'].includes(denied.data.error),JSON.stringify(values));
    assert.equal((await clean()).inputDispatchSequence,before.inputDispatchSequence);
    samples.push({case:'server_completion_concurrency',values});
  });
  await check('OPERATE server query preserves native Chat Screen, draft and focus without input Lease',async()=>{
    await acquire();await key(84);await wait(value=>value.screenClass.endsWith('ChatScreen'),'server completion existing Chat');
    const current=await api('GET','/v0/ui/tree'),editor=nodes(current).find(value=>value.role==='text_field');assert(editor);
    await api('POST','/v0/ui/action',{selector:{nodeId:editor.nodeId},action:'type_text',text:'server completion unsent draft'});
    await release();await mode('OPERATE');await spacing();
    const before=await api('GET','/v0/ui/tree'),inputBefore=await clean();
    const result=await api('POST',path,body());
    const after=await api('GET','/v0/ui/tree');assert.equal(after.screenIdentity,before.screenIdentity);
    const editorState=tree=>nodes(tree).filter(value=>value.role==='text_field').map(value=>({value:value.value,focused:value.focused}));
    assert.deepEqual(editorState(after),editorState(before));
    assert.equal((await clean()).inputDispatchSequence,inputBefore.inputDispatchSequence);
    assert.equal((await api('GET','/v0/control/mode')).takeoverActive,false);
    samples.push({case:'server_completion_preserves_chat',result,before:editorState(before),after:editorState(after)});
    await acquire();await key(256);await release();await mode('READ');
  });
  await check('actual MCP exposes explicit SERVER completion as USER read without takeover',async()=>{
    await spacing();
    const require=createRequire(resolve(repo,'components/companion/package.json'));
    const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
    const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
    const env=Object.fromEntries(Object.entries(process.env).filter(([,value])=>typeof value==='string'));
    for(const name of ['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE'])delete env[name];
    env.MCP_MINECRAFT_INSTANCE_DIR=instance;
    const client=new Client({name:'server-completion-regression',version:'1.0.0'});
    const transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
    try {
      await client.connect(transport);
      const reply=await client.callTool({name:'minecraft_execute_player_command',arguments:body()});
      assert(!reply.isError,JSON.stringify(reply));const result=reply.structuredContent.data;
      assert.equal(result.completionSource,'SERVER');assert.equal(result.dataCategory,'USER');assert.equal(result.commandExecuted,false);
      assert(result.suggestions.some(value=>value.text==='help'));
      assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
      samples.push({case:'mcp_server_completion',target,result});
    } finally {await client.close();}
  });
  await check('leaving world invalidates its completion context and cleanly returns to READ',async()=>{
    await acquire();await key(256);await wait(value=>value.screenClass.endsWith('PauseScreen'),'server completion Save and Quit');
    const pause=await api('GET','/v0/ui/tree'),quit=nodes(pause).find(value=>['menu.returnToMenu','menu.disconnect'].includes(value.translationKey));assert(quit);
    const worldExit=await finishBaselineWorldExit({api,quit,clickNode,wait});
    actionSamples.push({case:'server_completion_world_exit',...worldExit});await release();
    await api('POST',path,body(),'PLAYER_COMMAND_CONTEXT_UNAVAILABLE');
    assert.equal((await api('GET','/v0/control/mode')).mode,'READ');await clean();
  });
  actionSamples.push(...samples);
}
