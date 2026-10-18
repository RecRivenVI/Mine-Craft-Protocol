import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

// The runner owns the restored world; commands use the real current player, never console.
export async function commandDiscoveryBatchActions({api,repo,instance,target,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples}) {
  const path='/v0/command/player',samples=[];
  async function clean(){const value=await api('GET','/v0/input/state');assert.equal(value.pressedKeyCount,0);assert.equal(value.pressedButtonCount,0);return value;}
  await check('command discovery at title reports no connected player without input',async()=>{
    const before=await clean();
    await api('POST',path,{action:'discover',input:'',maxResults:8},'PLAYER_COMMAND_CONTEXT_UNAVAILABLE');
    assert.equal((await clean()).inputDispatchSequence,before.inputDispatchSequence);
  });
  await acquire();await click('menu.singleplayer','Singleplayer');
  let tree=await wait(value=>value.screenClass.endsWith('SelectWorldScreen'),'command discovery world list');
  const row=nodes(tree).find(value=>value.worldName==='World-Read-Alpha');assert(row);
  await clickNode(row);await click('selectWorld.select','Play Selected World');
  await wait(value=>!value.screenClass,'command discovery world entered',150000);await release();
  await check('READ discovers current native roots and contextual completions without dispatch',async()=>{
    const before=await clean();
    const roots=await api('POST',path,{action:'discover',input:'',maxResults:128});
    assert.equal(roots.dataCategory,'USER');assert.equal(roots.source,'current_player_native_command_tree');
    assert(roots.roots.includes('help'),JSON.stringify(roots));assert.equal(roots.commandExecuted,false);
    assert.equal(roots.inputDispatched,false);assert.equal(roots.requiresControlLease,false);
    assert.equal(roots.serverAcceptanceConfirmed,false);
    const completed=await api('POST',path,{action:'discover',input:'/he',cursor:3,maxResults:16});
    const help=completed.suggestions.find(value=>value.text==='help');assert(help,JSON.stringify(completed));
    assert.equal(help.start,1);assert.equal(help.end,3);assert.equal(completed.parsedExecutable,false);
    assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
    assert.equal((await clean()).inputDispatchSequence,before.inputDispatchSequence);
    samples.push({case:'native_command_discovery',roots,completed});
  });
  await check('READ rejects a native batch with no input or Lease side effect',async()=>{
    const before=await clean();
    await api('POST',path,{action:'batch',commands:['help']},'OPERATE_REQUIRED');
    assert.equal((await clean()).inputDispatchSequence,before.inputDispatchSequence);
    assert.equal((await api('GET','/v0/control/mode')).takeoverActive,false);
  });
  await mode('OPERATE');
  await check('OPERATE submits indexed native batch without Lease, UI focus or player input',async()=>{
    const before=await clean(),screen=await api('GET','/v0/ui/tree');
    const batch=await api('POST',path,{action:'batch',commands:['help','help','help']});
    assert.equal(batch.submissionStatus,'submitted_unconfirmed');assert.equal(batch.submittedCount,3);
    assert.equal(batch.dataCategory,'USER');assert.equal(batch.route,'NATIVE');assert.equal(batch.mechanism,'NORMAL_NETWORK');
    assert.equal(batch.effectConfirmed,false);assert.equal(batch.serverFeedbackCorrelated,false);assert.equal(batch.atomic,false);assert.equal(batch.retrySafe,false);
    assert.equal(batch.requiresControlLease,false);assert.equal(batch.inputDispatched,false);
    assert.equal(batch.commands.length,3);
    batch.commands.forEach((item,index)=>{assert.equal(item.commandIndex,index);assert.equal(item.status,'submitted_unconfirmed');assert.equal(item.receipt.route,'NATIVE');assert.equal(item.receipt.dataCategory,'USER');});
    assert.equal((await api('GET','/v0/ui/tree')).screenIdentity,screen.screenIdentity);
    assert.equal((await api('GET','/v0/control/mode')).takeoverActive,false);
    assert.equal((await clean()).inputDispatchSequence,before.inputDispatchSequence);
    samples.push({case:'native_command_batch',batch,inputBefore:before,inputAfter:await clean()});
  });
  await check('first rejected native item stops remaining commands and preserves prior receipt',async()=>{
    const result=await api('POST',path,{action:'batch',commands:['help','stop','help']});
    assert.equal(result.submittedCount,1);assert.equal(result.stoppedAtCommandIndex,1);
    assert.equal(result.commands[0].status,'submitted_unconfirmed');
    assert.equal(result.commands[1].status,'submission_unconfirmed');
    assert(['PLAYER_COMMAND_OUT_OF_SCOPE','PLAYER_COMMAND_NOT_AVAILABLE','PLAYER_COMMAND_UNCLASSIFIED'].includes(result.commands[1].error.code),JSON.stringify(result));
    assert.equal(result.commands[2].status,'not_attempted');assert.equal(result.rollbackAttempted,false);assert.equal(result.retrySafe,false);
    samples.push({case:'native_batch_stops_on_first_error',result});
  });
  await check('native batch accepts 32 items and rejects 33 or invalid text before dispatch',async()=>{
    const before=await clean();
    const boundary=await api('POST',path,{action:'batch',commands:Array(32).fill('help')});
    assert.equal(boundary.submittedCount,32);assert.equal(boundary.commands.length,32);
    await api('POST',path,{action:'batch',commands:Array(33).fill('help')},'PLAYER_COMMAND_BATCH_BUDGET_EXCEEDED');
    await api('POST',path,{action:'batch',commands:['help','help\nstop']},'INVALID_PLAYER_COMMAND');
    await api('POST',path,{action:'discover',input:'help\nstop'},'INVALID_COMMAND_DISCOVERY');
    await api('POST',path,{action:'discover',input:'',maxResults:129},'PLAYER_COMMAND_BUDGET_EXCEEDED');
    assert.equal((await clean()).inputDispatchSequence,before.inputDispatchSequence);
    samples.push({case:'native_batch_boundary',boundary,invalidBeforeSubmission:true});
  });
  await check('native tree availability stays distinct from effect policy and server acceptance',async()=>{
    const before=await clean();
    const found=await api('POST',path,{action:'discover',input:'stop',maxResults:16});
    assert.equal(found.effectPolicyAllowsSubmission,false);assert.equal(found.serverAcceptanceConfirmed,false);assert.equal(found.commandExecuted,false);
    const help=await api('POST',path,{action:'discover',input:'help',maxResults:16});
    assert.equal(help.parsedExecutable,true);assert.equal(help.effectPolicyAllowsSubmission,true);assert.equal(help.effectClassification,'COMMUNICATION');
    assert.equal((await clean()).inputDispatchSequence,before.inputDispatchSequence);
    samples.push({case:'command_availability_policy',found,help});
  });
  await check('actual MCP forwards typed command discovery and batch without another tool or Lease',async()=>{
    const require=createRequire(resolve(repo,'components/companion/package.json'));
    const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
    const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
    const env=Object.fromEntries(Object.entries(process.env).filter(([,value])=>typeof value==='string'));
    for(const name of ['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE'])delete env[name];
    env.MCP_MINECRAFT_INSTANCE_DIR=instance;
    const client=new Client({name:'command-discovery-batch-regression',version:'1.0.0'});
    const transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
    try {
      await client.connect(transport);
      const discovery=await client.callTool({name:'minecraft_execute_player_command',arguments:{action:'discover',input:'he',maxResults:8}});
      assert(!discovery.isError,JSON.stringify(discovery));assert.equal(discovery.structuredContent.data.type,'command.player.discovery');
      assert(discovery.structuredContent.data.suggestions.some(value=>value.text==='help'));
      const batch=await client.callTool({name:'minecraft_execute_player_command',arguments:{action:'batch',commands:['help','list']}});
      assert(!batch.isError,JSON.stringify(batch));assert.equal(batch.structuredContent.data.submittedCount,2);assert.equal(batch.structuredContent.data.dataCategory,'USER');
      samples.push({case:'mcp_command_discovery_batch',target,discovery:discovery.structuredContent.data,batch:batch.structuredContent.data});
    } finally {await client.close();}
  });
  await mode('READ');await clean();actionSamples.push(...samples);
}
