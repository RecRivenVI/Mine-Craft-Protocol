import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';

const repo=resolve(import.meta.dirname,'../../../..'),target='26.3-fabric';
const instance=resolve(repo,'validations/conformance-vanilla_ui/instance',target,'client');
const owner=JSON.parse(await readFile(resolve(instance,'fixture-owner.json'),'utf8'));assert.equal(owner.owner,'Mine-Craft-Protocol vanilla UI conformance');assert.equal(owner.target,target);
const native='http://127.0.0.1:28591',site=process.env.MCP_WEB_UI_URL??'http://127.0.0.1:4317';
const siteUrl=new URL(site);assert.equal(siteUrl.protocol,'http:');assert.equal(siteUrl.hostname,'127.0.0.1');assert.equal(siteUrl.pathname,'/');
const bootstrap=await(await fetch(site+'/api/bootstrap')).json();
let snapshot,sequence=0,failed,failureTree;
const checks=[];
const nodes=t=>(t.children??[]).flatMap(n=>[n,...nodes(n)]);
async function web(path,body){const response=await fetch(site+'/api/'+path,{method:body===undefined?'GET':'POST',headers:{'x-web-ui-token':bootstrap.browserToken,...(body===undefined?{}:{'content-type':'application/json'})},...(body===undefined?{}:{body:JSON.stringify(body)}),signal:AbortSignal.timeout(30000)});const value=await response.json();if(!response.ok)throw Object.assign(new Error(value.error+': '+value.message),{code:value.error});return value;}
async function refresh(){snapshot=await web('snapshot');return snapshot;}
async function event(body,frame=snapshot.inputFrameKey){const r=await web('input',{...body,sequence:++sequence,inputFrameKey:frame});if(r.snapshot)snapshot=r.snapshot;return r;}
function point(n){assert(n.visible!==false&&n.active!==false&&n.boundsAvailable!==false);return {x:n.interactionX??n.x+n.width/2,y:n.interactionY??n.y+n.height/2};}
async function click(n,modifiers=0){const frame=snapshot.inputFrameKey,p=point(n);await event({type:'button',...p,button:0,action:1,modifiers},frame);await event({type:'button',...p,button:0,action:0},frame);await refresh();}
async function wait(predicate,description){const end=Date.now()+30000;do{const s=await refresh();if(!s.tree.overlayIdentity&&predicate(s))return s;await new Promise(r=>setTimeout(r,100));}while(Date.now()<end);throw new Error('Timeout: '+description);}
async function key(key){const r=await web('key',{key});snapshot=r.snapshot;}
async function check(name,fn){await fn();checks.push(name);console.log('PASS web '+name);}
async function title(){for(let i=0;i<10;i++){await refresh();if(snapshot.tree.screenClass?.endsWith('TitleScreen'))return;if(!snapshot.tree.screenClass){await key('Escape');continue;}const quit=nodes(snapshot.tree).find(n=>['menu.returnToMenu','menu.disconnect'].includes(n.translationKey));if(quit){await click(quit);await wait(s=>s.tree.screenClass.endsWith('TitleScreen'),'save & quit');return;}await key('Escape');}throw new Error('Title cleanup failed');}
try{
 const s=await refresh();assert.equal(s.runtimeUrl,native);assert.equal(s.session.target,target);assert.equal(s.control.mode,'READ');assert(!s.control.reconsentRequired);assert(s.tree.screenClass.endsWith('TitleScreen'));
 await check('READ never injects player input',()=>assert.rejects(web('input',{type:'key',key:69,action:1,sequence:1,inputFrameKey:s.inputFrameKey}),e=>e.code==='TAKEOVER_REQUIRED'));
 snapshot=await web('acquire',{});sequence=snapshot.inputSequence;
 await check('screen-changing click and release stay in their original gesture',async()=>{
  await click(nodes(snapshot.tree).find(n=>n.translationKey==='menu.singleplayer'));
  await wait(s=>s.tree.screenClass.endsWith('SelectWorldScreen'),'world list');
  const row=nodes(snapshot.tree).find(n=>n.worldName==='UI-Conformance');assert(row);await click(row);
  const play=nodes(snapshot.tree).find(n=>n.translationKey==='selectWorld.select');assert(play?.active);await click(play);
  await wait(s=>s.session.inWorld&&!s.tree.screenClass,'fixture world');
 });
 await check('keyboard text preserves native caret position',async()=>{
  await key('Inventory');await wait(s=>s.tree.screenClass.endsWith('CreativeModeInventoryScreen'),'inventory');
  const search=nodes(snapshot.tree).find(n=>n.resourceId==='minecraft:search');await click(search);
  const field=nodes(snapshot.tree).find(n=>n.role==='text_field');await click(field);
  await event({type:'key',key:341,action:1,modifiers:2});await event({type:'key',key:65,action:1,modifiers:2});await event({type:'key',key:65,action:0});await event({type:'key',key:341,action:0});
  await event({type:'text',text:'stne'});await event({type:'key',key:263,action:1});await event({type:'key',key:263,action:0});await event({type:'key',key:263,action:1});await event({type:'key',key:263,action:0});await event({type:'text',text:'o'});
  assert.equal(nodes((await refresh()).tree).find(n=>n.role==='text_field').value,'stone');
 });
 await check('Shift-click and nine-slot native drag distribution',async()=>{
  const stone=nodes(snapshot.tree).find(n=>n.role==='slot'&&n.item==='minecraft:stone');assert(stone);
  await event({type:'key',key:340,action:1,modifiers:1});await click(stone,1);await event({type:'key',key:340,action:0});
  assert.equal(snapshot.tree.carriedStack?.id,'minecraft:stone');assert.equal(snapshot.tree.carriedStack?.count,64);
  const tab=nodes(snapshot.tree).find(n=>n.resourceId==='minecraft:inventory');assert(tab);await click(tab);
  let slots=nodes(snapshot.tree).filter(n=>n.role==='slot'&&n.visible!==false);
  if(snapshot.tree.carriedStack?.empty){const stack=slots.find(n=>n.item==='minecraft:stone'&&n.count===64);assert(stack,'Native tab change must retain the full stack in a slot or on the cursor');await click(stack);}
  slots=nodes((await refresh()).tree).filter(n=>n.role==='slot'&&n.visible!==false&&n.item==='minecraft:air');
  const rows=Map.groupBy(slots,n=>n.y);const row=[...rows.entries()].filter(([,v])=>v.length===9).sort((a,b)=>a[0]-b[0])[0]?.[1].sort((a,b)=>a.x-b.x);assert.equal(row?.length,9);
  const frame=snapshot.inputFrameKey;await event({type:'button',...point(row[0]),button:0,action:1},frame);
  // Native quick-craft adds a slot during mouseDragged, not the initial mouse press.
  await event({type:'move',x:point(row[0]).x+row[0].width*0.05,y:point(row[0]).y},frame);
  for(const slot of row.slice(1)){await new Promise(r=>setTimeout(r,50));await event({type:'move',...point(slot)},frame);}
  await event({type:'button',...point(row.at(-1)),button:0,action:0},frame);
  const final=nodes((await refresh()).tree);assert(row.every(n=>final.some(m=>m.nodeId===n.nodeId&&m.item==='minecraft:stone'&&m.count>0)),'All nine destinations must receive items');await key('Escape');
 });
 await check('native scrollbar press, move and release',async()=>{
  await title();await click(nodes(snapshot.tree).find(n=>n.translationKey==='menu.options'));await click(nodes(snapshot.tree).find(n=>n.translationKey==='options.language'));await wait(s=>s.tree.screenClass.endsWith('LanguageSelectScreen'),'language');
  const thumb=nodes(snapshot.tree).find(n=>n.role==='scrollbar_thumb'),track=nodes(snapshot.tree).find(n=>n.role==='scrollbar');assert(thumb&&track);const before=track.scrollAmount,frame=snapshot.inputFrameKey;
  await event({type:'button',...point(thumb),button:0,action:1},frame);await event({type:'move',...point(track)},frame);await event({type:'button',...point(track),button:0,action:0},frame);
  assert(nodes((await refresh()).tree).find(n=>n.role==='scrollbar').scrollAmount>before);await title();
 });
 await check('PNG remains standard Capture; no image assertions',async()=>{
  const r=await fetch(site+'/api/capture',{method:'POST',headers:{'x-web-ui-token':bootstrap.browserToken,'content-type':'application/json'},body:'{}'});assert.equal(r.status,200);assert(r.headers.get('content-type').startsWith('image/png'));const bytes=new Uint8Array(await r.arrayBuffer());assert.deepEqual([...bytes.slice(0,8)],[137,80,78,71,13,10,26,10]);assert(bytes.length>1000);
 });
}catch(e){failed=e;failureTree=snapshot?.tree;console.error(e.stack);}
finally{
 try{if((await refresh()).ownsLease)await title();snapshot=await web('release',{});assert.equal(snapshot.control.mode,'READ');assert.equal(snapshot.input.pressedKeyCount,0);assert.equal(snapshot.input.pressedButtonCount,0);}catch(e){failed??=e;}
 const sources={};for(const path of ['components/companion/src/ui-console.ts','components/companion/src/ui-console-http.ts','components/companion/web/app.mjs','components/validation_tools/conformance-vanilla_ui/task/run-web-input.mjs'])sources[path]=createHash('sha256').update(await readFile(resolve(repo,path))).digest('hex');
 const result={status:failed?'FAIL':'PASS',target,testedAt:new Date().toISOString(),testedCommit:execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).trim(),dirty:true,checks,sourceHashes:sources,noMultimodal:true,screenshotAssertions:0,captureTransportChecks:1,persistentWriteCalls:0,...(failed?{error:failed.message,failureTree}:{})};
 const dir=resolve(repo,'validations/conformance-vanilla_ui/result');await mkdir(dir,{recursive:true});await writeFile(resolve(dir,'web-'+target+'.json'),JSON.stringify(result,null,2)+'\n');
}
if(failed)process.exitCode=1;
