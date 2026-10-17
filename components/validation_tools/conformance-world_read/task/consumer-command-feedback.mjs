import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import {resolve} from 'node:path';

export async function consumerCommandFeedback({api,repo,instance,target,acquire,release,click,wait,nodes,check,actionSamples,enterWorld=false}) {
 const verify=receipt=>{
  assert.equal(receipt.accepted,true);assert.equal(receipt.effectConfirmed,false);assert.equal(receipt.serverValidationObserved,false);assert.equal(receipt.gameplayAcceptanceEligible,false);
  const f=receipt.commandFeedback;assert(f,JSON.stringify(receipt));assert.equal(f.association,'temporal_not_guaranteed');assert.equal(f.windowMillis,500);assert.equal(f.observedMillis,500);assert.equal(f.complete,true);assert.equal(f.readable,true);assert.equal(typeof f.truncated,'boolean');assert(f.messages.length<=16);
  for(const m of f.messages){assert.equal(m.source,'client_chat');assert.equal(typeof m.text,'string');assert([...m.text].length<=512);assert(m.offsetMillis>=0&&m.offsetMillis<=500);}
  return f;
 };
 if(enterWorld){await acquire();await click('menu.singleplayer','Singleplayer');const t=await wait(t=>t.screenClass.endsWith('SelectWorldScreen'),'feedback fixed world list');const row=nodes(t).find(n=>n.worldName==='World-Read-Alpha');assert(row);await api('POST','/v0/ui/action',{selector:{nodeId:row.nodeId},action:'double_click'});await wait(t=>!t.screenClass,'feedback fixed world loaded',150000);await release();}
 await check('feedback command: Native chat samples success and server error without claiming execution proof',async()=>{
  await acquire();
  try {
   const success=await api('POST','/v0/command/player',{command:'time query gametime'});verify(success);assert(success.commandFeedback.messages.some(m=>m.text.match(/\d/)),JSON.stringify(success));
   const error=await api('POST','/v0/command/player',{command:'data get entity @s mcp_feedback_missing_path'});verify(error);assert(error.commandFeedback.messages.length>0,JSON.stringify(error));assert(!error.commandFeedback.messages.some(m=>success.commandFeedback.messages.some(s=>s.text===m.text)),'Previous command chat must not be copied into the next response');
   actionSamples.push({case:'feedback_native_command',success,error,correlationGuaranteed:false,normalPlayerPath:true});
  } finally {await release();}
 });
 await check('feedback command: unavailable commands still reject before any input',async()=>{
  await acquire();
  try {
   const before=await api('GET','/v0/input/state');const denied=await api('POST','/v0/command/player',{command:'mcp_definitely_missing_command'},'PLAYER_COMMAND_NOT_AVAILABLE');const after=await api('GET','/v0/input/state');assert.equal(after.inputDispatchSequence,before.inputDispatchSequence);assert(!denied.commandFeedback);
   actionSamples.push({case:'feedback_command_preflight',error:denied.error,inputDispatchDelta:0});
  } finally {await release();}
 });
 await check('feedback command: actual MCP preserves bounded temporal feedback and releases its own Lease',async()=>{
  const require=createRequire(resolve(repo,'components/companion/package.json'));const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
  const env=Object.fromEntries(Object.entries(process.env).filter(([,v])=>typeof v==='string'));for(const key of ['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE'])delete env[key];env.MCP_MINECRAFT_INSTANCE_DIR=instance;env.MCP_MINECRAFT_TARGET=target;
  const client=new Client({name:'command-feedback-consumer',version:'0.0.1'});const transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});let lease;
  const call=async(name,args)=>{const value=await client.callTool({name,arguments:args});assert(!value.isError,JSON.stringify(value));return value.structuredContent.data;};
  try {await client.connect(transport);lease=(await call('minecraft_control',{action:'acquire',ttlMs:30000})).leaseId;const result=await call('minecraft_execute_player_command',{command:'time query gametime',leaseId:lease});verify(result);assert(result.commandFeedback.messages.length>0,JSON.stringify(result));actionSamples.push({case:'feedback_mcp_command',result,realMcp:true});}
  finally {try{if(lease)await call('minecraft_control',{action:'release',leaseId:lease});}finally{try{await client.close();}finally{await transport.close();}}}
 });
 const input=await api('GET','/v0/input/state');assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
}
