import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { resolve } from 'node:path';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { Client } from '@modelcontextprotocol/client';
import { StdioClientTransport } from '@modelcontextprotocol/client/stdio';

const repo = resolve(import.meta.dirname, '../../..');
const target = '26.3-fabric';
const instance = resolve(repo, 'validations/feature-runtime_probe/instance', target, 'client');
assert.equal(resolve(process.env.MCP_MINECRAFT_TOKEN_FILE ?? ''), resolve(instance, 'minecraft-protocol/token'));
const webUrl = 'http://127.0.0.1:4317';
const client = new Client({ name:'vanilla-ui-consumer-conformance', version:'1.0.0' });
const transport = new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],
  env:{...process.env},stderr:'pipe'});
const checks = [];
const nodes = tree => (tree.children ?? []).flatMap(node => [node,...nodes(node)]);
const pause = () => new Promise(resolve => setTimeout(resolve,150));
let browserToken, webOwned = false;
async function tool(name,args={}) {
  const response = await client.callTool({name,arguments:args});
  if(response.isError) throw new Error(JSON.stringify(response.structuredContent));
  return response.structuredContent.data;
}
const ui = (selector,action='click',extra={}) => tool('minecraft_interact_ui',{selector,action,...extra});
const tree = () => tool('minecraft_get_ui',{view:'tree'});
async function waitScreen(pattern) {
  const end=Date.now()+30000;
  do {const value=await tree();if(pattern.test(value.screenClass)&&!value.overlayIdentity)return value;await pause();}while(Date.now()<end);
  throw new Error('Screen timeout '+pattern);
}
async function acquire() {
  await tool('minecraft_control',{action:'begin_task',ttlMs:60000,budgetMs:180000});
  await tool('minecraft_control',{action:'acquire',ttlMs:60000});
}
async function release() {
  await tool('minecraft_control',{action:'release'});
  await tool('minecraft_control',{action:'end_task'});
}
async function web(path,body) {
  const response=await fetch(webUrl+'/api/'+path,{method:body===undefined?'GET':'POST',
    headers:{'x-web-ui-token':browserToken,...(body===undefined?{}:{'content-type':'application/json'})},
    ...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(30000)});
  const value=await response.json();
  if(!response.ok)throw Object.assign(new Error(value.message),{code:value.error});
  return value;
}
async function webAction(key,action='click',extra={}) {
  const value=await web('snapshot');
  const matches=nodes(value.tree).filter(n=>n.controlKey===key&&n.active!==false&&n.visible!==false);
  assert.equal(matches.length,1,key);
  return web('action',{nodeId:matches[0].nodeId,frameKey:value.frameKey,action,...extra});
}
async function command(command) {
  await tool('minecraft_execute_player_command',{command});
}
async function use() {
  const result=await tool('minecraft_run_input_pipeline',{steps:[{type:'delay',durationMs:150},
    {type:'mouse.button',button:1,action:1},{type:'delay',durationMs:150},{type:'mouse.button',button:1,action:0}],timeoutMs:10000});
  assert.equal(result.state,'completed');
}
let failure;
try {
  await client.connect(transport);
  assert.equal((await tool('minecraft_get_session')).target,target);
  await waitScreen(/TitleScreen$/);
  await acquire();
  await ui({translationKey:'menu.options'});await ui({translationKey:'options.resourcepack'});
  await waitScreen(/PackSelectionScreen$/);
  const before=nodes(await tree()).filter(n=>n.packId&&n.role==='container'&&n.selected).map(n=>n.packId);
  await ui({packId:'file/ui-semantic-a',controlKey:'pack.select'});
  await ui({packId:'file/ui-semantic-b',controlKey:'pack.select'});
  const up=nodes(await tree()).find(n=>n.packId?.startsWith('file/ui-semantic-')&&n.controlKey==='pack.move_up'&&n.active);
  assert(up);await ui({packId:up.packId,controlKey:'pack.move_up'});
  for(const id of ['file/ui-semantic-b','file/ui-semantic-a'])await ui({packId:id,controlKey:'pack.unselect'});
  assert.deepEqual(nodes(await tree()).filter(n=>n.packId&&n.role==='container'&&n.selected).map(n=>n.packId),before);
  await ui({translationKey:'gui.done'});await waitScreen(/OptionsScreen$/);
  await ui({translationKey:'gui.done'});await waitScreen(/TitleScreen$/);
  checks.push('mcp_typed_pack_select_reorder_restore');
  const owner=JSON.parse(await readFile(resolve(instance,'rendered-ui-world-owner.json'),'utf8'));
  assert.equal(owner.purpose,'VANILLA_RENDERED_UI');
  await ui({translationKey:'menu.singleplayer'});await waitScreen(/SelectWorldScreen$/);
  await ui({worldId:owner.worldId});await ui({translationKey:'selectWorld.select'});
  await waitScreen(/^$/);
  await command('fill -2 80 -2 2 83 3 minecraft:air');
  await command('tp @s 0.5 80 0.5 0 0');
  await tool('minecraft_wait',{condition:{type:'player',perspective:'client',expected:{pitch:0}},timeoutMs:10000});
  await command('clear @s');
  await command('item replace entity @s weapon.mainhand with minecraft:writable_book');
  await use();await waitScreen(/BookEditScreen$/);
  await release();
  const bootstrap=await (await fetch(webUrl+'/api/bootstrap')).json();browserToken=bootstrap.browserToken;
  assert.equal(bootstrap.runtimeUrl,new URL(process.env.MCP_MINECRAFT_BASE_URL).origin);
  await web('acquire',{});webOwned=true;
  const book='Web line one\nWeb line two';
  await webAction('book.page','type_text',{text:book,replace:true});
  let snapshot=await web('snapshot');
  assert.equal(nodes(snapshot.tree).find(n=>n.controlKey==='book.page').value,book);
  const stale={nodeId:nodes(snapshot.tree).find(n=>n.controlKey==='book.page').nodeId,frameKey:snapshot.frameKey,action:'type_text',text:'STALE'};
  await webAction('book.next_page');
  await assert.rejects(web('action',stale),{code:'WEB_UI_STALE_FRAME'});
  await webAction('book.page','type_text',{text:'Second web page',replace:true});
  checks.push('web_book_multiline_and_stale_page_rejection');
  await web('key',{key:'Escape'});await web('release',{});webOwned=false;
  await acquire();
  await command('item replace entity @s weapon.mainhand with minecraft:oak_sign 4');
  await command('tp @s 0.5 80 0.5 0 60');
  await tool('minecraft_wait',{condition:{type:'player',perspective:'client',expected:{pitch:60}},timeoutMs:10000});
  await use();await waitScreen(/SignEditScreen$/);await release();
  await web('acquire',{});webOwned=true;
  await webAction('sign.line.2','type_text',{text:'Web sign line',replace:true});
  snapshot=await web('snapshot');
  const sign=nodes(snapshot.tree).find(n=>n.controlKey==='sign.line.2');
  assert.equal(sign.boundsAvailable,false);assert.equal(sign.inputRoute,'screen_keyboard');
  assert.equal(sign.value,'Web sign line');assert.equal(sign.focused,true);
  checks.push('web_sign_keyboard_route_without_coordinates');
  await web('key',{key:'Escape'});await web('release',{});webOwned=false;
  const input=await tool('minecraft_get_state',{kind:'input'});
  assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);
  assert.equal((await tool('minecraft_control',{action:'status'})).mode,'READ');
} catch(error) {failure=error;} finally {
  if(webOwned)await web('release',{}).catch(()=>{});
  await tool('minecraft_control',{action:'release'}).catch(()=>{});
  await tool('minecraft_control',{action:'end_task'}).catch(()=>{});
  await client.close();
}
const paths=['components/companion/src/server.ts','components/companion/src/ui-console.ts',
  'components/companion/web/app.mjs','components/companion/scripts/live-vanilla-ui.mjs'];
const sourceHashes=await Promise.all(paths.map(async path=>({path,sha256:createHash('sha256').update(await readFile(resolve(repo,path))).digest('hex')})));
const result={result:failure?'FAIL':'PASS',checks,target,sourceHashes,date:new Date().toISOString(),
  testedCommit:execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).trim(),dirty:true,
  transports:['mcp_stdio','web_http'],arrange:'diagnostic_commands_in_owned_fixture_world',
  coordinateFallbacks:0,persistentWriteCalls:0,fullVanillaUiAcceptance:false,...(failure?{failure:String(failure)}:{})};
const output=resolve(repo,'validations/compatibility-standard_gui/result');
await mkdir(output,{recursive:true});
await writeFile(resolve(output,'ui-consumers-26.3-fabric.json'),JSON.stringify(result,null,2)+'\n');
console.log(JSON.stringify(result,null,2));
if(failure)process.exitCode=1;
