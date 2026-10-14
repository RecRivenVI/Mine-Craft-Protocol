import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

// Explicit handoff of an idle, owned fixture; never reuses a user's website or lease.
export async function spectatorConsumers(repo, base, token, tokenFile) {
 const require=createRequire(resolve(repo,'components/companion/package.json'));
 const load=path=>import(pathToFileURL(resolve(repo,path)).href);
 const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
 const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
 const {RuntimeClient}=await load('components/companion/dist/runtime-client.js');
 const {UiConsole}=await load('components/companion/dist/ui-console.js');
 const {startUiConsole}=await load('components/companion/dist/ui-console-http.js');
 const env=Object.fromEntries(Object.entries(process.env).filter(([,v])=>typeof v==='string'));
 delete env.MCP_MINECRAFT_TOKEN;env.MCP_MINECRAFT_BASE_URL=base;env.MCP_MINECRAFT_TOKEN_FILE=tokenFile;
 const client=new Client({name:'spectator-consumer-conformance',version:'1.0.0'});
 const transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
 const nodes=t=>(t.children??[]).flatMap(n=>[n,...nodes(n)]);
 const tool=async(name,args={})=>{const response=await client.callTool({name,arguments:args});assert(!response.isError,JSON.stringify(response.structuredContent));return response.structuredContent.data;};
 async function wait(read,predicate){const end=Date.now()+10000;do{const t=await read();if(predicate(t))return t;await new Promise(r=>setTimeout(r,80));}while(Date.now()<end);throw new Error('Spectator consumer state timeout');}
 let web,browserToken,mcpOwned=false,webOwned=false;
 const request=async(path,body)=>{const r=await fetch(web.url+'/api/'+path,{method:body===undefined?'GET':'POST',headers:{'x-web-ui-token':browserToken,...(body===undefined?{}:{'content-type':'application/json'})},...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(30000)});const value=await r.json();if(!r.ok)throw Object.assign(new Error(value.message),{code:value.error});return value;};
 const results=[];
 try{
  await client.connect(transport);
  assert.equal((await tool('minecraft_control',{action:'status'})).mode,'READ');
  await tool('minecraft_control',{action:'begin_task',ttlMs:60000,budgetMs:120000});mcpOwned=true;
  await tool('minecraft_control',{action:'acquire',ttlMs:60000});
  const read=()=>tool('minecraft_get_ui');
  await tool('minecraft_interact_ui',{selector:{controlKey:'spectator.open'},action:'open'});
  await wait(read,t=>t.uiInputContext==='spectator_menu');
  await tool('minecraft_interact_ui',{selector:{controlKey:'spectator.slot.8'},action:'select'});
  await wait(read,t=>nodes(t).some(n=>n.controlKey==='spectator.slot.8'&&n.selected));
  const repeated=await tool('minecraft_interact_ui',{selector:{controlKey:'spectator.slot.8'},action:'click'});assert.equal(repeated.inputDispatchCount,0);
  await tool('minecraft_interact_ui',{selector:{controlKey:'spectator.slot.8'},action:'activate'});
  await wait(read,t=>t.uiInputContext==='spectator');
  await tool('minecraft_control',{action:'release'});await tool('minecraft_control',{action:'end_task'});mcpOwned=false;
  results.push({transport:'mcp_stdio',operation:'spectator_open_select_activate',status:'PASS'});
  web=await startUiConsole(new UiConsole(new RuntimeClient({baseUrl:new URL(base),token,timeoutMs:30000,maxJsonBytes:2097152,maxArtifactBytes:2097152})),0);
  ({browserToken}=await(await fetch(web.url+'/api/bootstrap')).json());
  let s=await request('acquire',{});webOwned=true;
  const action=async(key,action)=>{s=await request('snapshot');const n=nodes(s.tree).find(n=>n.controlKey===key);assert(n);const r=await request('action',{nodeId:n.nodeId,frameKey:s.frameKey,action});assert.notEqual(r.result.directMutationUsed,true);return r.result;};
  await action('spectator.open','open');
  await wait(()=>request('snapshot'),s=>s.tree.uiInputContext==='spectator_menu');
  s=await request('snapshot');const stale={nodeId:nodes(s.tree).find(n=>n.controlKey==='spectator.slot.8').nodeId,frameKey:s.frameKey,action:'select'};
  await action('spectator.slot.8','select');
  await wait(()=>request('snapshot'),s=>nodes(s.tree).some(n=>n.controlKey==='spectator.slot.8'&&n.selected));
  await assert.rejects(()=>request('action',stale),{code:'WEB_UI_STALE_FRAME'});
  assert.equal((await action('spectator.slot.8','click')).inputDispatchCount,0);
  await action('spectator.slot.8','activate');
  await wait(()=>request('snapshot'),s=>s.tree.uiInputContext==='spectator');
  s=await request('release',{});webOwned=false;
  assert.equal(s.control.mode,'READ');assert.equal(s.input.pressedKeyCount,0);assert.equal(s.input.pressedButtonCount,0);
  results.push({transport:'web_http',operation:'spectator_open_select_activate',status:'PASS'});
  return results;
 }finally{
  if(webOwned)await request('release',{}).catch(()=>{});
  if(mcpOwned){await tool('minecraft_control',{action:'release'}).catch(()=>{});await tool('minecraft_control',{action:'end_task'}).catch(()=>{});}
  try{await web?.close();}finally{await client.close();}
 }
}
