import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir,readdir} from 'node:fs/promises';
import {resolve,relative} from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {toastConsumers} from './toast-consumers.mjs';
import {editorConsumers} from './editor-consumers.mjs';
import {spectatorConsumers} from './spectator-consumers.mjs';
import {specialMenuActions} from './special-menu-actions.mjs';
import {hudVitalActions} from './hud-vital-actions.mjs';
import {connectRuntime,automaticTakeoverRequest} from '../../runtime-connection.mjs';
import {villagerFixtureCommands} from '../../vanilla-command-fixtures.mjs';
import {createServer} from 'node:net';
import {once} from 'node:events';

const repo=resolve(import.meta.dirname,'../../../..');
const args=Object.fromEntries(process.argv.slice(2).map(arg=>arg.replace(/^--/,'').split('=')));
const target=args.target;
assert(['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric'].includes(target));
const instance=resolve(repo,'validations/conformance-vanilla_ui/instance',target,'client');
const token=(await readFile(resolve(instance,'minecraft-protocol/token'),'utf8')).trim();
const connection=await connectRuntime(instance,target);
const uiDebugAuthorized=(await (await connection.fetch('/v0/security/context')).json()).grantedScopes?.includes('debug.ui')===true;
if(['ui-debug-scroll','ui-debug-hud','ui-debug-toast'].includes(args.scenario))assert(uiDebugAuthorized,args.scenario+' requires actual Runtime DebugUi authorization');
const port=connection.port,base=connection.base;
if(args.port)assert.equal(Number(args.port),port,'Runner supplied port does not match owned instance');
// Keep the failed-server fixture reserved; never ping an unrelated service at RuntimePort+100.
const fixtureServer=createServer(socket=>socket.destroy());
fixtureServer.listen(0,'127.0.0.1');await once(fixtureServer,'listening');fixtureServer.unref();
const fixturePort=fixtureServer.address().port;
process.on('exit',()=>fixtureServer.close());
async function sourceHashes(){
 const paths=['components/protocol_schema/src/main/openapi/minecraft-control-v0.json',relative(repo,import.meta.filename),'components/validation_tools/conformance-vanilla_ui/task/Invoke-VanillaUiSuite.ps1','components/validation_tools/conformance-vanilla_ui/task/vanilla-ui.init.gradle','components/validation_tools/InstanceProfiles.ps1','components/validation_tools/Prepare-InstanceProfile.ps1','components/validation_tools/instance-profiles.gradle','build.gradle.kts','components/validation_tools/RuntimeConnection.ps1','components/validation_tools/runtime-connection.mjs','validations/conformance-vanilla_ui/validation.toml'];
 paths.push('gradle.properties','settings.gradle.kts','gradle/libs.versions.toml',`versions/${target}/target.properties`,`versions/${target}/build.gradle.kts`,'components/runtime_core/build.gradle.kts','components/runtime_safety/build.gradle.kts','components/validation_tools/vanilla-command-fixtures.mjs');
 const root=resolve(repo,'versions',target,'src');
 for(const entry of await readdir(root,{recursive:true,withFileTypes:true}))if(entry.isFile()&&(/\.java$/.test(entry.name)||/mixins\.json$/.test(entry.name)))paths.push(relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/'));
 const safety=resolve(repo,'components/runtime_safety/src/main');
 for(const entry of await readdir(safety,{recursive:true,withFileTypes:true}))if(entry.isFile()&&/\.java$/.test(entry.name))paths.push(relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/'));
 const core=resolve(repo,'components/runtime_core/src/main');
 for(const entry of await readdir(core,{recursive:true,withFileTypes:true}))if(entry.isFile()&&/\.java$/.test(entry.name))paths.push(relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/'));
 paths.push('components/validation_tools/conformance-vanilla_ui/task/toast-consumers.mjs','components/validation_tools/conformance-vanilla_ui/task/editor-consumers.mjs','components/validation_tools/conformance-vanilla_ui/task/spectator-consumers.mjs','components/validation_tools/conformance-vanilla_ui/task/special-menu-actions.mjs','components/validation_tools/conformance-vanilla_ui/task/hud-vital-actions.mjs','validations/conformance-vanilla_ui/task/screens.json');
 for(const dir of ['src','dist'])for(const entry of await readdir(resolve(repo,'components/companion',dir),{recursive:true,withFileTypes:true}))if(entry.isFile()&&/\.(?:ts|js)$/.test(entry.name))paths.push(relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/'));
 const hashes={};for(const path of paths.sort())hashes[path]=createHash('sha256').update(await readFile(resolve(repo,path))).digest('hex');return hashes;
}
const startedSources=await sourceHashes();
let lease, taskId, failure, failureTree, failureControl, failureInput, debugArm, lastDebugRequest=0, identityChurn;
const validationStarted=Date.now();
let taskStarted=validationStarted;
const checks=[],toastSamples=[],editorSamples=[],effectSamples=[],spectatorSamples=[],specialMenuSamples=[],vitalSamples=[],fixtureReadSamples=[],chatCloseSamples=[],screens=new Set(),pause=()=>new Promise(r=>setTimeout(r,120));
const worldSelectionSamples=[],uiDebugSamples=[];
const nodes=t=>(t.children??[]).flatMap(n=>[n,...nodes(n)]);
async function api(method,path,body,extraHeaders={}){
 if(method==='POST'&&path==='/v0/control/acquire')body=automaticTakeoverRequest(await api('GET','/v0/control/mode'),body);
 if(path.startsWith('/v0/debug/')){const delay=550-(Date.now()-lastDebugRequest);if(delay>0)await new Promise(r=>setTimeout(r,delay));lastDebugRequest=Date.now();}
 const r=await connection.fetch(path,{method,headers:{...(lease?{'x-mcp-control-lease':lease}:{}),...(debugArm?{'x-mcp-debug-arm':debugArm}:{}),...(body?{'content-type':'application/json'}:{}),...extraHeaders},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(45000)});
 const value=await r.json();if(!r.ok)throw Object.assign(new Error(value.error+': '+value.message),{code:value.error,nativeError:value,httpStatus:r.status});return value;
}
async function tree(){const t=await api('GET','/v0/ui/tree');screens.add(t.screenClass);return t;}
async function wait(predicate,description){const end=Date.now()+45000;let last;do{const t=await tree();last=t;if(!t.overlayIdentity&&predicate(t))return t;await pause();}while(Date.now()<end);failureTree=last;throw new Error('Timeout: '+description);}
async function heartbeatControl(){
 await api('POST','/v0/control/task',{action:'heartbeat',taskId,ttlMs:60000});
 await api('POST','/v0/control/renew',{ttlMs:60000});
}
async function ui(selector,action='click',extra={}){
 await heartbeatControl();
 const r=await api('POST','/v0/ui/action',{selector,action,...extra});assert.notEqual(r.directMutationUsed,true);assert.equal(r.targetingSource,'interaction_tree');return r;
}
async function click(key,label){const keys=Array.isArray(key)?key:[key];const candidates=nodes(await tree()).filter(n=>keys.includes(n.translationKey)&&n.visible!==false&&n.active!==false);if(candidates.length===1)return ui({nodeId:candidates[0].nodeId});return ui({label});}
async function key(key){await heartbeatControl();const op=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key,holdMs:40}],timeoutMs:12000});const r=await api('POST','/v0/operations/'+op.operationId+'/wait',{timeoutMs:12000});assert.equal(r.state,'completed',JSON.stringify(r));}
async function closeOwnedChat(prefix){
 let escapes=0;
 for(let i=0;i<4;i++){
  const t=await tree();
  if(!t.screenClass){
   const state=await api('GET','/v0/control/mode'),input=await api('GET','/v0/input/state');
   assert.equal(state.mode,'TAKEOVER');assert.equal(state.reconsentRequired,false);assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);
   chatCloseSamples.push({escapes,screenClosed:true,agentEscDidNotRevoke:true});return;
  }
  assert(t.screenClass.endsWith('ChatScreen'),'Do not dismiss an unrelated Screen');
  assert(nodes(t).some(n=>n.role==='text_field'&&n.value?.startsWith(prefix)),'Do not discard an unrelated draft');
  // Vanilla may use the first Esc only to dismiss its suggestions, not Chat itself.
  await key(256);escapes++;
 }
 throw new Error('Owned Chat did not close through bounded native Esc input');
}
async function title(){for(let i=0;i<12;i++){const t=await wait(t=>!t.overlayIdentity,'ready for navigation');if(t.screenClass?.endsWith('TitleScreen'))return t;if(!t.screenClass){await key(256);continue;}const quit=nodes(t).find(n=>['menu.disconnect','menu.returnToMenu'].includes(n.translationKey));if(quit){await ui({nodeId:quit.nodeId});return await wait(t=>t.screenClass?.endsWith('TitleScreen'),'save & quit');}await key(256);}throw new Error('Could not return to title');}
async function check(name,fn){
 if(args.scenario==='ui-debug-toast'&&!/^(title:|world fixture:|Toast: native movement tutorial|typed Toast DEBUG:|pause:)/.test(name))return;
 if(args.scenario==='ui-debug-hud'&&!/^(title:|world fixture:|typed HUD DEBUG:|pause:)/.test(name))return;
 if(args.scenario==='ui-debug-scroll'&&!/^(title:|world fixture:|creative: rendered scrollbar|workstations: semantic loom|merchant: native scrollbar|pause:)/.test(name))return;
 if(args.scenario==='ui-debug-editors'&&!/^(title:|world fixture:|book editor:|sign:|hanging sign:|pause:)/.test(name))return;
 if(args.scenario==='ui-debug-consumers'&&!/^(title:|typed UI DEBUG|UI DEBUG text:|language: scrollbar thumb drag|world fixture:|book editor:|sign:|hanging sign:|pause:)/.test(name))return;
 if(args.scenario==='ui-debug'&&!/^(title:|typed UI DEBUG|UI DEBUG text:|language: scrollbar thumb drag)/.test(name))return;
 if(args.scenario==='world-selection'&&!/^(title:|world selection:|pause:)/.test(name))return;
 if(args.scenario==='editors'&&!/^(title:|world fixture:|book editor:|sign:|hanging sign:|survival inventory:|pause:)/.test(name))return;
 if(args.scenario==='hud'&&!/^(title:|world fixture:|HUD:|Toast:|pause:)/.test(name))return;
 if(args.scenario==='spectator'&&!/^(title:|world fixture:|spectator:|pause:)/.test(name))return;
 if(args.scenario==='pointer'&&!/^(title:|window:|world fixture:|language: scrollbar thumb drag|creative:|item gesture:|pause:)/.test(name))return;
 if(args.scenario==='special-menus'&&!/^(title:|world fixture:|special menus:|pause:)/.test(name))return;
 if(args.scenario==='hud-vitals'&&!/^(title:|world fixture:|HUD: (?:vital|fractional|armor|poisoned|submerged|mount|F1 Screen|MCP and isolated web preserve READ-only vital)|pause:)/.test(name))return;
 const started=Date.now();try{
 if(Date.now()-taskStarted>180000){
  // Separate bounded test tasks only at an idle case boundary, never bypass a manual revoke.
  const mode=await api('GET','/v0/control/mode'),input=await api('GET','/v0/input/state');
  assert.equal(mode.mode,'TAKEOVER');assert.equal(mode.reconsentRequired,false);assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);
  await api('POST','/v0/control/release');lease=undefined;await api('POST','/v0/control/task',{action:'end',taskId});
  taskId=(await api('POST','/v0/control/task',{action:'begin',ttlMs:60000,budgetMs:300000})).taskId;
  lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;taskStarted=Date.now();
 }
 await api('POST','/v0/control/task',{action:'heartbeat',taskId,ttlMs:60000});
 await fn();checks.push({name,status:'PASS',milliseconds:Date.now()-started});console.log('PASS '+name);}catch(e){failureTree??=await tree().catch(()=>undefined);checks.push({name,status:'FAIL',reason:e.message});throw e;}}
async function command(command){await heartbeatControl();return api('POST','/v0/command/player',{command});}
async function serverTicksAfter(minimumTicks=3){
 // This is a diagnostic fixture barrier, never a USER observation or a player input.
 const input=await api('GET','/v0/input/state');assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);
 await api('POST','/v0/control/release');lease=undefined;
 try{
  assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
  const snapshot=async()=>{const value=await api('GET','/v0/server/player?category=DEBUG');assert.equal(value.dataCategory,'DEBUG');return value;};
  const tick=(await snapshot()).serverTick,end=Date.now()+10000;
  do{const current=await snapshot();if(current.serverTick>=tick+minimumTicks){fixtureReadSamples.push({source:'server_player',mode:'READ',dataCategory:current.dataCategory,inputLease:false,startTick:tick,endTick:current.serverTick});return;}await pause();}while(Date.now()<end);
  throw new Error('Server did not flush the diagnostic fixture before a pausing Screen');
 }finally{
  const mode=await api('GET','/v0/control/mode');assert.equal(mode.mode,'READ');assert.equal(mode.reconsentRequired,false,'Manual revocation cannot be cleared by a fixture barrier');
  lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;
 }
}
async function fixtureVisible(block,pitch=0){const end=Date.now()+10000;let readyTick,last;do{const [b,p]=await Promise.all([api('GET','/v0/world/block?x=0&y=81&z=2'),api('GET','/v0/player')]);last={block:b.block,available:b.available,player:{x:p.x,y:p.y,z:p.z,yaw:p.yaw,pitch:p.pitch,clientTick:p.clientTick},expectedPitch:pitch};if(b.block==='minecraft:'+block&&Math.abs(p.x-0.5)<0.05&&Math.abs(p.y-80)<0.05&&Math.abs(p.z-0.5)<0.05&&Math.abs(p.yaw%360)<0.05&&Math.abs(p.pitch-pitch)<0.05){readyTick??=p.clientTick;if(p.clientTick>=readyTick+3)return;}else readyTick=undefined;await pause();}while(Date.now()<end);throw new Error('Fixture block/camera has not remained stable across client ticks: '+block+' '+JSON.stringify(last));}
async function enterWorld(){
 await title();await click('menu.singleplayer','Singleplayer');
 let t=await wait(t=>/SelectWorldScreen$|CreateWorldScreen$/.test(t.screenClass),'world selection');
 if(t.screenClass.endsWith('SelectWorldScreen')){
   const world=nodes(t).find(n=>n.worldName==='UI-Conformance');
   if(world){await ui({nodeId:world.nodeId});await click('selectWorld.select','Play Selected World');await wait(t=>!t.screenClass,'enter fixture');return;}
   await click('selectWorld.create','Create New World');
 }
 await wait(t=>t.screenClass.endsWith('CreateWorldScreen'),'create');
 await ui({role:'text_field',nth:0},'type_text',{text:'UI-Conformance'});
 for(let i=0;i<4;i++){
   const mode=nodes(await tree()).find(n=>n.label?.startsWith('Game Mode:'));assert(mode);
   if(mode.label.includes('Creative'))break;await ui({nodeId:mode.nodeId});
 }
 t=await tree();const tabs=nodes(t).filter(n=>n.role==='tab');
 const worldTab=tabs.find(n=>n.label==='World');if(worldTab)await ui({nodeId:worldTab.nodeId});
 const seed=nodes(await tree()).find(n=>n.role==='text_field'&&n.label?.includes('Seed'));if(seed)await ui({nodeId:seed.nodeId},'type_text',{text:'20261012'});
 const type=nodes(await tree()).find(n=>n.label?.startsWith('World Type:'));
 if(type){for(let i=0;i<12;i++){const n=nodes(await tree()).find(n=>n.label?.startsWith('World Type:'));if(/Superflat|Flat/.test(n.label))break;await ui({nodeId:n.nodeId});}}
 const game=nodes(await tree()).find(n=>n.role==='tab'&&n.label==='Game');if(game)await ui({nodeId:game.nodeId});
 await click('selectWorld.create','Create New World');await wait(t=>!t.screenClass,'created fixture');
}
async function use(){const op=await api('POST','/v0/pipelines',{steps:[{type:'mouse.button',button:1,action:1},{type:'delay',durationMs:120},{type:'mouse.button',button:1,action:0}],timeoutMs:12000});assert.equal((await api('POST','/v0/operations/'+op.operationId+'/wait',{timeoutMs:12000})).state,'completed');}
async function equip(item,count=1){
 await command('item replace entity @s weapon.mainhand with minecraft:'+item+' '+count);
 await wait(t=>!t.screenClass&&t.presentation.children.some(n=>n.selected&&n.item==='minecraft:'+item&&n.count===count),'selected client item received: '+item);
}
async function dragScroller(prefix,y){
 const t=await tree(),thumb=nodes(t).find(n=>n.controlKey===prefix+'.thumb'),track=nodes(t).find(n=>n.controlKey===prefix+'.track');
 assert(thumb?.active&&track?.active,prefix+' native scrollbar available');
 const op=await api('POST','/v0/pipelines',{steps:[{type:'ui.drag',fromSelector:{nodeId:thumb.nodeId},toSelector:{nodeId:track.nodeId},toOffset:{x:0.5,y},segments:12,durationMs:600}],timeoutMs:12000});
 const result=await api('POST','/v0/operations/'+op.operationId+'/wait',{timeoutMs:12000});assert.equal(result.state,'completed',JSON.stringify(result));
 return wait(t=>nodes(t).some(n=>n.controlKey===prefix+'.track'&&(y>0.5?n.scrollAmount>0.7:n.scrollAmount===0)),prefix+' native scroll changed');
}
async function shiftItem(item){
 let stable=0,last;await wait(t=>{const stamp=t.screenRevision+':'+t.menuRevision;stable=stamp===last?stable+1:0;last=stamp;return stable>=3&&nodes(t).some(n=>n.role==='slot'&&n.item===item&&n.count>0);},'fixture inventory settled');
 const slot=nodes(await tree()).find(n=>n.role==='slot'&&n.item===item&&n.count>0);assert(slot);
 await api('POST','/v0/input/key',{key:340,scanCode:0,action:1,modifiers:1});
 try{await ui({nodeId:slot.nodeId},'click',{modifiers:1});}finally{await api('POST','/v0/input/key',{key:340,scanCode:0,action:0,modifiers:0});}
}
async function debugUi(fn){
 const mode=await api('GET','/v0/control/mode');
 await api('POST','/v0/control/mode',{mode:'OPERATE',expectedModeVersion:mode.modeVersion});lease=undefined;
 const snapshot=()=>api('GET','/v0/ui/tree?category=DEBUG');
 const t=await snapshot();
 debugArm=(await api('POST','/v0/debug/arm',{uiContextId:t.uiContextId,namespaces:['ui'],ttlMs:60000})).debugArmId;
 try{await fn(snapshot);}finally{await api('POST','/v0/debug/disarm');debugArm=undefined;lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;}
}
async function rejectUiMutation(request,code,snapshot,headers={}){
 const before=nodes(await snapshot()).find(n=>n.nodeId===request.nodeId);assert(before?.resourceVersion);
 const input=await api('GET','/v0/input/state');
 await assert.rejects(api('POST','/v0/debug/mutations',request,headers),e=>e.code===code);
 const after=nodes(await snapshot()).find(n=>n.nodeId===request.nodeId);assert(after);
 assert.deepEqual(after.resourceVersion,before.resourceVersion,'Rejected typed mutation must not change the native target');
 const clean=await api('GET','/v0/input/state');assert.equal(clean.inputDispatchSequence,input.inputDispatchSequence);assert.equal(clean.pressedKeyCount,0);assert.equal(clean.pressedButtonCount,0);
 uiDebugSamples.push({operation:request.operation,result:code,resourceUnchanged:true,inputDelta:0});
}
async function applyUiMutation(request){
 const before=await api('GET','/v0/input/state');
 const result=await api('POST','/v0/debug/mutations',request);
 assert.equal(result.inputDispatchCount,0);assert.equal(result.evidenceAuthority,'DEBUG_PRIVILEGED');assert.equal(result.dataCategory,'DEBUG');
 const mode=await api('GET','/v0/control/mode');assert.equal(mode.mode,'OPERATE');assert.equal(mode.reconsentRequired,false);
 const after=await api('GET','/v0/input/state');assert.equal(after.inputDispatchSequence,before.inputDispatchSequence);assert.equal(after.pressedKeyCount,0);assert.equal(after.pressedButtonCount,0);
 uiDebugSamples.push({operation:request.operation,result:'APPLIED',inputDelta:0,mode:mode.mode,diagnostic:true,before:result.beforeResourceVersion,after:result.afterResourceVersion});
 return result;
}
async function debugScreenScroller(prefix){
 if(!uiDebugAuthorized)return;
 let enabled=false;
 await debugUi(async snapshot=>{
  let t=await snapshot(),n=nodes(t).find(n=>n.controlKey===prefix+'.track');
  assert(n?.resourceVersion,'Native Screen scrollbar revision: '+prefix);
  const original=n.scrollAmount,id=n.nodeId;
  enabled=n.active;
  const request={operation:'ui.scroll.set',nodeId:id,expectedResourceVersion:n.resourceVersion,expectedValue:original,value:1};
  for(const value of [-0.1,1.1,false,{},null])await rejectUiMutation({...request,value},'INVALID_UI_DEBUG_VALUE',snapshot);
  await rejectUiMutation({...request,expectedValue:original+0.125},'VALUE_PRECONDITION_FAILED',snapshot);
  if(!n.active){
   await rejectUiMutation(request,'INVALID_UI_DEBUG_VALUE',snapshot);
   const result=await applyUiMutation({...request,value:0});assert.equal(result.after.scrollAmount,0);
   uiDebugSamples.push({case:'native_screen_scroll',controlKey:prefix,enabled:false,original,restored:true,inputDelta:0});return;
  }
  const selected=nodes(t).filter(n=>n.selected===true&&n.entryIndex!==undefined).map(n=>[n.controlKey,n.entryIndex]);
  try{
   const result=await applyUiMutation(request);assert(Math.abs(result.after.scrollAmount-1)<1e-6);
   await rejectUiMutation(request,'STALE_RESOURCE_REVISION',snapshot);
  }finally{
   n=nodes(await snapshot()).find(n=>n.nodeId===id);assert(n?.resourceVersion);
   const restored=await applyUiMutation({operation:'ui.scroll.set',nodeId:id,expectedResourceVersion:n.resourceVersion,expectedValue:n.scrollAmount,value:original});
   assert(Math.abs(restored.after.scrollAmount-original)<1e-6);
  }
  t=await snapshot();
  assert.deepEqual(nodes(t).filter(n=>n.selected===true&&n.entryIndex!==undefined).map(n=>[n.controlKey,n.entryIndex]),selected);
  assert(t.carriedStack.empty,'Display scrolling must not pick up an item');
  uiDebugSamples.push({case:'native_screen_scroll',controlKey:prefix,enabled:true,original,restored:true,inputDelta:0,selectionUnchanged:true});
 });
 if(enabled)await editorHandoff('debug_screen_scroll_'+prefix.split('.')[0],undefined,{selector:{controlKey:prefix+'.track'},field:'scrollAmount',operation:'ui.scroll.set',changedValue:1});
}
async function debugEditorTextFields(keys){
 await debugUi(async snapshot=>{
  const original=await snapshot(),focus=nodes(original).filter(n=>n.focused).map(n=>n.nodeId);
  for(const key of keys){
   let n=nodes(await snapshot()).find(n=>n.controlKey===key);assert(n?.resourceVersion&&n.debugTextGuardAvailable,'Complete native editor guard: '+key);
   const value=n.value,id=n.nodeId,request={operation:'ui.text.set',nodeId:id,expectedResourceVersion:n.resourceVersion,expectedValue:value,value:'Debug'};
   for(const bad of [false,{},'x'.repeat(4097)])await rejectUiMutation({...request,value:bad},'INVALID_UI_DEBUG_VALUE',snapshot);
   if(key.startsWith('sign.'))await rejectUiMutation({...request,value:'W'.repeat(64)},'INVALID_UI_DEBUG_VALUE',snapshot);
   await rejectUiMutation({...request,expectedValue:value+' wrong'},'VALUE_PRECONDITION_FAILED',snapshot);
   try{
    const result=await applyUiMutation(request);assert.equal(result.after.value,'Debug');
    if(key==='book.page'||key.startsWith('sign.'))assert.equal(result.sideEffects,'native_editor_draft_may_submit_on_close');
    await rejectUiMutation(request,'STALE_RESOURCE_REVISION',snapshot);
   }finally{
    n=nodes(await snapshot()).find(n=>n.nodeId===id);
    if(n.value!==value){const restored=await applyUiMutation({...request,expectedResourceVersion:n.resourceVersion,expectedValue:n.value,value});assert.equal(restored.after.value,value);}
   }
   assert.equal(nodes(await snapshot()).find(n=>n.nodeId===id).value,value);
  }
  assert.deepEqual(nodes(await snapshot()).filter(n=>n.focused).map(n=>n.nodeId),focus,'DEBUG edits must preserve the active editor or sign line');
 });
}
async function editorHandoff(kind,expected,control){
 const debugEnabled=['ui-debug-consumers','ui-debug-scroll','ui-debug-hud','ui-debug-toast','all'].includes(args.scenario);
 if(target!=='26.3-fabric'&&!debugEnabled)return;
 await api('POST','/v0/control/release');lease=undefined;
 await api('POST','/v0/control/task',{action:'end',taskId});taskId=undefined;
 let consumerFailure;
 try{
  editorSamples.push(...await editorConsumers(repo,base,token,resolve(instance,'minecraft-protocol/token'),kind,expected,target,debugEnabled,control));
 }catch(error){
  consumerFailure=error;failure??=error;console.error('Editor consumer failed: '+kind,error);throw error;
 }finally{
  try{
   const mode=await api('GET','/v0/control/mode');assert.equal(mode.mode,'READ');assert.equal(mode.reconsentRequired,false,'A handoff must not clear human override');
   taskId=(await api('POST','/v0/control/task',{action:'begin',ttlMs:60000,budgetMs:300000})).taskId;
   lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;taskStarted=Date.now();
  }catch(cleanupError){
   if(!consumerFailure)throw cleanupError;
   console.error('Editor controller recovery failed: '+kind,cleanupError);
  }
 }
}
try{
 assert.equal((await api('GET','/v0/session')).target,target);
 taskId=(await api('POST','/v0/control/task',{action:'begin',ttlMs:60000,budgetMs:300000})).taskId;
 lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;
 if(args.phase!=='quit'){
 await check('title: Native graph and bounded node metadata',async()=>{const t=await title();assert(nodes(t).some(n=>n.translationKey==='menu.singleplayer'));assert(!t.treeTruncated);});
 await check('window: final-output title and original icon badge across modes',async()=>{
   const hooks=await api('GET','/v0/diagnostics/hooks');
   // A Hook descriptor may aggregate several injected callbacks, not one row per site.
   assert.equal(hooks.cancellableInjectionCount,target.startsWith('26.3-')?25:target.startsWith('26.')?22:20);
   for(const id of ['player_command_entry_guard','player_command_packet_guard'])assert(hooks.hooks.some(hook=>hook.id===id&&hook.cancellable===true));
   assert.equal(hooks.replacementInjectionCount,target.startsWith('26.3-')?9:10);
   assert.equal(hooks.passiveDrawDelegationCount,5);
   assert.equal(hooks.controlFlowPolicy,'operator_control_and_exact_hud_draw_delegation');
   async function presentation(mode,icon,suffix){
     const end=Date.now()+10000;let s;
     do{s=await api('GET','/v0/session');if(s.mode===mode&&s.windowIconState===icon&&s.windowTitle===s.windowBaseTitle+suffix)break;await pause();}while(Date.now()<end);
     assert.equal(s.mode,mode);assert.equal(s.windowIconState,icon);
     assert(s.originalWindowIconCount>0);assert.equal(s.windowDecorationError,'');
     assert.equal(s.windowTitle,s.windowBaseTitle+suffix);return s;
   }
   const first=await presentation('TAKEOVER','original_with_agent_pointer',' - 由智能体接管');
   for(let repeat=0;repeat<2;repeat++){
     await api('POST','/v0/control/release');lease=undefined;
     await presentation('READ','original_minecraft',' - 智能体读取中');
     const m=await api('GET','/v0/control/mode');
     await api('POST','/v0/control/mode',{mode:'OPERATE',expectedModeVersion:m.modeVersion});
     await presentation('OPERATE','original_minecraft',' - 智能体操控中');
     lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;
     const again=await presentation('TAKEOVER','original_with_agent_pointer',' - 由智能体接管');
     assert.equal(again.originalWindowIconCount,first.originalWindowIconCount);
   }
 });
 await check('navigation: active node identities survive more than 2048 historical elements',async()=>{
   const highest=t=>Math.max(t.screenIdentity??0,...nodes(t).map(n=>Number(n.elementIdentity??0)));
   const start=highest(await title());let high=start,visits=0;
   while(high-start<2200&&visits<32){
     await click('menu.options','Options...');await wait(t=>t.screenClass.endsWith('OptionsScreen'),'options identity churn');
     await click('options.language','Language...');let t=await wait(t=>t.screenClass.endsWith('LanguageSelectScreen'),'language identity churn');
     const done=nodes(t).find(n=>n.translationKey==='gui.done');assert(done);
     high=Math.max(high,highest(t));
     for(let sample=0;sample<3;sample++){
       t=await tree();assert.equal(nodes(t).find(n=>n.translationKey==='gui.done')?.nodeId,done.nodeId,'unchanged native button retains identity');
       const resolved=await api('POST','/v0/ui/resolve',{nodeId:done.nodeId});assert.equal(resolved.node.nodeId,done.nodeId);
     }
     await ui({nodeId:done.nodeId});await title();visits++;
   }
   assert(high-start>=2200,'navigation must exercise the old identity table boundary');
   identityChurn={visits,allocatedIdentityRange:high-start,unchangedNodeStable:true};
 });
 await check('UI categories: USER default and DEBUG needs an independent read scope',async()=>{
   const t=await tree();assert.equal(t.domain,'UI');assert.equal(t.dataCategory,'USER');
   assert(nodes(t).every(n=>n.visible===true&&n.occluded!==true));
   assert(nodes(t).every(n=>!('nativeSlotIndex'in n)&&!('entryCount'in n)&&!('clickEvent'in n)));
   const caps=await api('GET','/v0/capabilities');assert.equal(caps.uiContract.defaultReadCategory,'USER');assert.equal(caps.uiContract.milestoneComplete,false);
   const context=await api('GET','/v0/security/context');
   const debugAllowed=context.grantedScopes.includes('debug')&&context.grantedScopes.includes('debug.ui.read');
   const expected=debugAllowed?'UI_CATEGORY_MODE_MISMATCH':'SCOPE_DENIED';
   await assert.rejects(api('GET','/v0/ui/tree?category=DEBUG'),e=>e.code===expected);
   await assert.rejects(api('GET','/v0/ui/tree?category=unexpected'),e=>e.code==='INVALID_UI_CATEGORY');
   for(const category of [null,[],42])await assert.rejects(api('POST','/v0/ui/resolve',{category,translationKey:'menu.singleplayer'}),e=>e.code==='INVALID_UI_CATEGORY');
   await assert.rejects(api('POST','/v0/ui/resolve',{category:'DEBUG',translationKey:'menu.singleplayer'}),e=>e.code===expected);
   await assert.rejects(api('POST','/v0/ui/action',{category:'DEBUG',action:'click',selector:{translationKey:'menu.singleplayer'}}),e=>e.code==='USER_UI_OPERATION_REQUIRED');
   assert((await tree()).screenClass.endsWith('TitleScreen'));
   const before=await api('GET','/v0/control/mode');
   await api('POST','/v0/control/mode',{mode:'OPERATE',expectedModeVersion:before.modeVersion});lease=undefined;
   await assert.rejects(api('GET','/v0/ui/tree'),e=>e.code==='UI_CATEGORY_MODE_MISMATCH');
   if(debugAllowed){const debug=await api('GET','/v0/ui/tree?category=DEBUG');assert.equal(debug.dataCategory,'DEBUG');assert.equal(debug.debugFacts.completeGameData,false);}
   const operate=await api('GET','/v0/control/mode');
   await api('POST','/v0/control/mode',{mode:'READ',expectedModeVersion:operate.modeVersion});
   assert.equal((await tree()).dataCategory,'USER');
   if(debugAllowed){
     const debug=await api('GET','/v0/ui/tree?category=DEBUG');assert.equal(debug.dataCategory,'DEBUG');
     const resolved=await api('POST','/v0/ui/resolve',{category:'DEBUG',translationKey:'menu.singleplayer'});assert.equal(resolved.dataCategory,'DEBUG');
     assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
   }
   lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;
 });
 await check('typed UI DEBUG: scoped session Arm, revisions, hidden widgets, cleanup and no input Lease',async()=>{
   if(!(await api('GET','/v0/security/context')).grantedScopes.includes('debug.ui')) throw new Error('Run the milestone matrix with -DebugUi to authorize typed UI Debug checks');
   let mode=await api('GET','/v0/control/mode');
   await api('POST','/v0/control/mode',{mode:'OPERATE',expectedModeVersion:mode.modeVersion});lease=undefined;
   let t=await api('GET','/v0/ui/tree?category=DEBUG');
   let n=nodes(t).find(n=>n.translationKey==='menu.singleplayer');assert(n?.resourceVersion);
   const mutation=(node,value)=>({operation:'ui.widget.visible.set',nodeId:node.nodeId,expectedResourceVersion:node.resourceVersion,value});
   const snapshot=()=>api('GET','/v0/ui/tree?category=DEBUG');
   await rejectUiMutation(mutation(n,false),'DEBUG_NOT_ARMED',snapshot);
   const arm=await api('POST','/v0/debug/arm',{uiContextId:t.uiContextId,namespaces:['ui'],ttlMs:60000});debugArm=arm.debugArmId;
   assert.equal(arm.authority,'CLIENT_UI');assert.equal(arm.worldFingerprint,undefined);
   await rejectUiMutation(mutation(n,false),'DEBUG_NOT_ARMED',snapshot,{'x-mcp-debug-arm':'invalid-ui-arm'});
   for(const operation of ['ui.widget.active.set','ui.widget.visible.set']){
     for(const value of ['false',0,null,{},[]])await rejectUiMutation({operation,nodeId:n.nodeId,expectedResourceVersion:n.resourceVersion,value},'INVALID_UI_DEBUG_VALUE',snapshot);
     await rejectUiMutation({operation,nodeId:n.nodeId,expectedResourceVersion:n.resourceVersion,value:false,expectedValue:false},'VALUE_PRECONDITION_FAILED',snapshot);
   }
   for(const [operation,value] of [['ui.text.set','text'],['ui.scroll.set',0],['ui.slider.set',0.5]])await rejectUiMutation({operation,nodeId:n.nodeId,expectedResourceVersion:n.resourceVersion,value},'INVALID_UI_DEBUG_VALUE',snapshot);
   const stale=mutation(n,false);const changed=await applyUiMutation(stale);
   assert.equal(changed.evidenceAuthority,'DEBUG_PRIVILEGED');assert.equal(changed.inputDispatchCount,0);assert.equal(changed.after.visible,false);
   await rejectUiMutation(stale,'STALE_RESOURCE_REVISION',snapshot);
   t=await api('GET','/v0/ui/tree?category=DEBUG');n=nodes(t).find(x=>x.nodeId===n.nodeId);assert.equal(n.visible,false);
   mode=await api('GET','/v0/control/mode');await api('POST','/v0/control/mode',{mode:'READ',expectedModeVersion:mode.modeVersion});
   assert(!nodes(await tree()).some(x=>x.nodeId===n.nodeId));
   await assert.rejects(api('POST','/v0/debug/mutations',mutation(n,true)),e=>e.code==='OPERATE_REQUIRED');
   mode=await api('GET','/v0/control/mode');await api('POST','/v0/control/mode',{mode:'OPERATE',expectedModeVersion:mode.modeVersion});
   const restored=await applyUiMutation(mutation(n,true));assert.equal(restored.after.visible,true);
   n=nodes(await api('GET','/v0/ui/tree?category=DEBUG')).find(x=>x.nodeId===n.nodeId);
   const active={operation:'ui.widget.active.set',nodeId:n.nodeId,expectedResourceVersion:n.resourceVersion,value:false,expectedValue:true};
   await assert.rejects(api('POST','/v0/debug/mutations',{...active,expectedValue:false}),e=>e.code==='VALUE_PRECONDITION_FAILED');
   await assert.rejects(api('POST','/v0/debug/mutations',{...active,value:'false'}),e=>e.code==='INVALID_UI_DEBUG_VALUE');
   assert.equal((await applyUiMutation(active)).after.active,false);
   await rejectUiMutation(active,'STALE_RESOURCE_REVISION',snapshot);
   n=nodes(await api('GET','/v0/ui/tree?category=DEBUG')).find(x=>x.nodeId===n.nodeId);
   assert.equal((await applyUiMutation({...active,expectedResourceVersion:n.resourceVersion,value:true,expectedValue:false})).after.active,true);
   assert.equal((await api('GET','/v0/input/state')).pressedKeyCount,0);
   await api('POST','/v0/debug/disarm');debugArm=undefined;
   lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;
   if(args.scenario==='ui-debug-consumers'||args.scenario==='all')for(const field of ['active','visible'])await editorHandoff('widget_'+field,undefined,{selector:{translationKey:'menu.singleplayer'},field,operation:'ui.widget.'+field+'.set'});
 });
 await check('language: scrollbar thumb drag through native callbacks',async()=>{
   await title();await click('menu.options','Options...');
   await click(['options.language','narrator.button.language'],'Language...');let t=await wait(t=>/LanguageSelectScreen$/.test(t.screenClass),'language');
   const thumb=nodes(t).find(n=>n.role==='scrollbar_thumb'),track=nodes(t).find(n=>n.role==='scrollbar');assert(thumb);assert(track);
   const before=track.scrollAmount;
   const op=await api('POST','/v0/pipelines',{steps:[{type:'ui.drag',fromSelector:{nodeId:thumb.nodeId},toSelector:{nodeId:track.nodeId},segments:12,durationMs:600}],timeoutMs:12000});
   assert.equal((await api('POST','/v0/operations/'+op.operationId+'/wait',{timeoutMs:12000})).state,'completed');
   assert(nodes(await tree()).find(n=>n.role==='scrollbar').scrollAmount>before);
   await debugUi(async snapshot=>{
     let n=nodes(await snapshot()).find(n=>n.role==='list'&&n.resourceVersion);assert(n,'native list Debug resource');
     const original=n.scrollAmount,id=n.nodeId,request={operation:'ui.scroll.set',nodeId:id,expectedResourceVersion:n.resourceVersion,expectedValue:original,value:0};
     for(const value of [-1,1e100,'0',null,false,{}])await rejectUiMutation({...request,value},'INVALID_UI_DEBUG_VALUE',snapshot);
     await rejectUiMutation({...request,expectedValue:original+0.5},'VALUE_PRECONDITION_FAILED',snapshot);
     try{
       const result=await applyUiMutation(request);assert.equal(result.after.scrollAmount,0);
       await rejectUiMutation(request,'STALE_RESOURCE_REVISION',snapshot);
     }finally{n=nodes(await snapshot()).find(n=>n.nodeId===id);await applyUiMutation({...request,expectedResourceVersion:n.resourceVersion,expectedValue:n.scrollAmount,value:original});}
     assert.equal(nodes(await snapshot()).find(n=>n.nodeId===id).scrollAmount,original);
   });
   if(args.scenario==='ui-debug-consumers'||args.scenario==='all')await editorHandoff('native_list',undefined,{selector:{role:'list'},field:'scrollAmount',operation:'ui.scroll.set'});
   await click('gui.done','Done');await title();
 });
 for(const [keyName,label] of [['options.sounds','Music & Sounds...'],['options.video','Video Settings...'],['options.controls','Controls...'],['options.accessibility','Accessibility Settings...'],['options.chat.title','Chat Settings...'],['options.skinCustomisation','Skin Customization...'],['options.resourcepack','Resource Packs...']]){
   await check('options: '+keyName,async()=>{await title();await click('menu.options','Options...');const t=await tree();const found=nodes(t).find(n=>n.translationKey===keyName||n.label===label);assert(found,'Expected vanilla options entry '+keyName);await ui({nodeId:found.nodeId});const page=await tree();assert(page.screenClass!==t.screenClass);assert(nodes(page).length>0);if(keyName==='options.sounds'){const master=nodes(page).find(n=>n.role==='slider'&&/Master Volume/.test(n.label));if(master)await ui({nodeId:master.nodeId},'set_value',{value:0});}await title();});
 }
 await check('typed UI DEBUG slider: native setting/label, bounds, stale revision and restoration without input',async()=>{
   await title();await click('menu.options','Options...');await click('options.controls','Controls...');await click('options.mouse_settings','Mouse Settings...');
   await debugUi(async snapshot=>{
     let n=nodes(await snapshot()).find(n=>n.role==='slider'&&/Sensitivity/.test(n.label));assert(n?.resourceVersion,'sensitivity slider');
     const original=n.value,originalLabel=n.label,id=n.nodeId;
     const req={operation:'ui.slider.set',nodeId:id,expectedResourceVersion:n.resourceVersion,expectedValue:original,value:original<0.5?0.75:0.25};
     for(const value of [-0.1,1.1,'0.5',null,false,{}])await rejectUiMutation({...req,value},'INVALID_UI_DEBUG_VALUE',snapshot);
     await rejectUiMutation({...req,expectedValue:original+0.01},'VALUE_PRECONDITION_FAILED',snapshot);
     assert.equal(nodes(await snapshot()).find(n=>n.nodeId===id).value,original);
     try{
       const r=await applyUiMutation(req);assert.equal(r.after.value,req.value);assert.notEqual(r.after.label,originalLabel);
       assert.equal(r.inputDispatchCount,0);assert.equal(r.evidenceAuthority,'DEBUG_PRIVILEGED');assert.equal(r.sideEffects,'native_slider_setting_callback');
       assert.equal((await api('GET','/v0/control/mode')).mode,'OPERATE');
       await rejectUiMutation(req,'STALE_RESOURCE_REVISION',snapshot);
       for(const value of [0,1]){n=nodes(await snapshot()).find(n=>n.nodeId===id);assert.equal((await applyUiMutation({...req,expectedResourceVersion:n.resourceVersion,expectedValue:n.value,value})).after.value,value);}
     }finally{n=nodes(await snapshot()).find(n=>n.nodeId===id);await applyUiMutation({...req,expectedResourceVersion:n.resourceVersion,expectedValue:n.value,value:original});}
     n=nodes(await snapshot()).find(n=>n.nodeId===id);assert.equal(n.value,original);assert.equal(n.label,originalLabel);
   });
   if(args.scenario==='ui-debug-consumers'||args.scenario==='all')await editorHandoff('native_slider',undefined,{selector:{role:'slider',labelContains:'Sensitivity'},field:'value',operation:'ui.slider.set'});
   await title();
 });
 await check('nested controls: key bindings and mouse settings',async()=>{
  for(const [id,label,screen] of [['options.keybinds','Key Binds...','KeyBindsScreen'],['options.mouse_settings','Mouse Settings...','MouseSettingsScreen']]) {
   await title();await click('menu.options','Options...');await click('options.controls','Controls...');await click(id,label);
   const t=await wait(t=>t.screenClass.endsWith(screen),screen);assert(nodes(t).some(n=>n.actions?.includes('click')));await title();
  }
 });
 if(args.phase!=='prepare')await check('multiplayer: semantic saved rows, hover icons, reorder, edit and confirmed cleanup',async()=>{
   await title();await click('menu.multiplayer','Multiplayer');
   let t=await wait(t=>/SafetyScreen$|JoinMultiplayerScreen$/.test(t.screenClass),'multiplayer entry');
   if(t.screenClass.endsWith('SafetyScreen')){
     await click('multiplayerWarning.proceed','Proceed');
     t=await wait(t=>t.screenClass.endsWith('JoinMultiplayerScreen'),'multiplayer warning accepted in fixture');
   }
   const rows=t=>nodes(t).filter(n=>n.controlKey==='server.entry');
   assert.equal(rows(t).length,0,'Disposable baseline must not contain user servers');
   async function add(name){
     await click('selectServer.add','Add Server');
     await wait(t=>/EditServerScreen$|ManageServerScreen$/.test(t.screenClass),'add server');
     await ui({role:'text_field',nth:0},'type_text',{text:name});
     await ui({role:'text_field',nth:1},'type_text',{text:'127.0.0.1:'+fixturePort});
     await click(['addServer.add','gui.done'],'Done');
     await wait(t=>t.screenClass.endsWith('JoinMultiplayerScreen')&&rows(t).some(n=>n.label===name),'server added');
   }
   await add('UI Local A');await add('UI Local B');
   await ui({translationKey:'selectServer.add'},'hover');
   t=await wait(t=>!nodes(t).some(n=>n.controlKey==='server.move_down'),'non-hovered server controls are absent in USER');
   assert(rows(t).every(n=>n.label==='UI Local A'||n.label==='UI Local B'));
   assert(!JSON.stringify(rows(t)).includes('127.0.0.1'),'Saved address is not visible in the list');
   let a=rows(t).find(n=>n.label==='UI Local A');await ui({nodeId:a.nodeId},'hover');
   t=await wait(t=>nodes(t).some(n=>n.controlKey==='server.move_down'),'native row hover');
   let down=nodes(t).find(n=>n.controlKey==='server.move_down');
   assert(!nodes(t).some(n=>n.controlKey==='server.move_up'));
   assert(nodes(t).some(n=>n.controlKey==='server.join'));
   const stale=down.nodeId;await ui({nodeId:down.nodeId});
   t=await wait(t=>rows(t)[0]?.label==='UI Local B','server moved down');
   await assert.rejects(ui({nodeId:stale}),e=>['UI_NODE_NOT_FOUND','UI_TARGET_INVALIDATED'].includes(e.code));
   assert.deepEqual(rows(await tree()).map(n=>n.label),['UI Local B','UI Local A']);
   a=rows(t).find(n=>n.label==='UI Local A');await ui({nodeId:a.nodeId},'hover');
   t=await wait(t=>nodes(t).some(n=>n.controlKey==='server.move_up'),'move-up hover');
   await ui({nodeId:nodes(t).find(n=>n.controlKey==='server.move_up').nodeId});
   t=await wait(t=>rows(t)[0]?.label==='UI Local A','server order restored');
   await ui({nodeId:rows(t)[1].nodeId});await click('selectServer.edit','Edit');
   await wait(t=>/EditServerScreen$|ManageServerScreen$/.test(t.screenClass),'server edit');
   await ui({role:'text_field',nth:0},'type_text',{text:'UI Local B edited'});
   await click(['addServer.add','gui.done'],'Done');
   t=await wait(t=>rows(t).some(n=>n.label==='UI Local B edited'),'saved edit');
   await ui({nodeId:rows(t)[0].nodeId});await click('selectServer.delete','Delete');
   await wait(t=>t.screenClass.endsWith('ConfirmScreen'),'delete confirmation');
   await click('gui.cancel','Cancel');assert.equal(rows(await tree()).length,2);
   for(const name of ['UI Local A','UI Local B edited']){
     t=await tree();await ui({nodeId:rows(t).find(n=>n.label===name).nodeId});
     await click('selectServer.delete','Delete');await wait(t=>t.screenClass.endsWith('ConfirmScreen'),'confirm owned row deletion');
     await click('selectServer.deleteButton','Delete');
     await wait(t=>t.screenClass.endsWith('JoinMultiplayerScreen')&&!rows(t).some(n=>n.label===name),'row removed');
   }
   await click('selectServer.direct','Direct Connection');
   await wait(t=>t.screenClass.endsWith('DirectJoinServerScreen'),'direct connection form');
   await ui({role:'text_field'},'type_text',{text:'127.0.0.1:'+fixturePort});
   await click('gui.cancel','Cancel');assert.equal(rows(await tree()).length,0);
   await title();
 });
 if(args.phase!=='prepare')await check('world creation: flat preset selection, rules and data-pack pages, then discard without creating a world',async()=>{
  await title();await click('menu.singleplayer','Singleplayer');await wait(t=>/SelectWorldScreen$/.test(t.screenClass),'world list');
  const before=nodes(await tree()).filter(n=>n.worldId).map(n=>n.worldId).sort();
  await click('selectWorld.create','Create New World');await wait(t=>/CreateWorldScreen$/.test(t.screenClass),'world creation');
  await ui({role:'tab',label:'World'});
  for(let i=0;i<12;i++){const type=nodes(await tree()).find(n=>n.label?.startsWith('World Type:'));assert(type);if(/Superflat|Flat/.test(type.label))break;await ui({nodeId:type.nodeId});}
  await click('selectWorld.customizeType','Customize...');await wait(t=>/CreateFlatWorldScreen$/.test(t.screenClass),'flat customization');
  await click('createWorld.customize.presets','Presets');
  let t=await wait(t=>/PresetFlatWorldScreen$/.test(t.screenClass),'flat presets');
  const presets=nodes(t).filter(n=>n.role==='list_item'&&n.actions.includes('click'));assert(presets.length>=2,'native preset rows');
  const previous=nodes(t).find(n=>n.role==='text_field')?.value;
  await ui({nodeId:presets[1].nodeId});t=await tree();assert.notEqual(nodes(t).find(n=>n.role==='text_field')?.value,previous,'preset selection changes native serialized text');
  await click('createWorld.customize.presets.select','Use Preset');await wait(t=>/CreateFlatWorldScreen$/.test(t.screenClass),'preset applied in draft');
  assert(nodes(await tree()).some(n=>n.role==='list_item'&&n.label));await click('gui.done','Done');
  await wait(t=>/CreateWorldScreen$/.test(t.screenClass),'draft creation restored');await ui({role:'tab',label:'More'});
  await click('selectWorld.gameRules','Game Rules');await wait(t=>/GameRulesScreen$/.test(t.screenClass),'game rules');
  assert(nodes(await tree()).some(n=>n.actions?.includes('click')&&n.role!=='list_item'));await click('gui.cancel','Cancel');
  await wait(t=>/CreateWorldScreen$/.test(t.screenClass),'rules canceled');await click('selectWorld.dataPacks','Data Packs');
  await wait(t=>/PackSelectionScreen$/.test(t.screenClass),'world data packs');assert(nodes(await tree()).some(n=>n.packId));await key(256);
  await wait(t=>/CreateWorldScreen$/.test(t.screenClass),'data packs closed');await click('gui.cancel','Cancel');
  await wait(t=>/SelectWorldScreen$/.test(t.screenClass),'draft discarded');
  assert.deepEqual(nodes(await tree()).filter(n=>n.worldId).map(n=>n.worldId).sort(),before,'no world created by navigation');await title();
 });
 await check('world selection: unselected semantic row double click enters the exact fixture',async()=>{
  await title();await click('menu.singleplayer','Singleplayer');
  const before=await wait(t=>t.screenClass.endsWith('SelectWorldScreen'),'fresh world list');
  const row=nodes(before).find(n=>n.worldName==='UI-Conformance');assert(row,'Immutable baseline world must exist');
  assert.equal(row.selected,false,'Exercise an unselected row, not a preselected workaround');assert(row.actions.includes('double_click'));
  const sample={worldId:row.worldId,nodeId:row.nodeId,selectedBefore:row.selected,action:'double_click',targetingSource:'interaction_tree',status:'IN_PROGRESS'};worldSelectionSamples.push(sample);
  await ui({nodeId:row.nodeId},'double_click');
  await wait(t=>!t.screenClass,'world entered by semantic double click');
  const player=await api('GET','/v0/player');assert.equal(player.available,true);assert.equal(player.dataCategory,'USER');
  const input=await api('GET','/v0/input/state');assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);
  sample.status='PASS';sample.worldEntered=true;sample.noHeldInput=true;
 });
 await check('world fixture: GUI entry and deterministic Arrange',async()=>{await enterWorld();const window=await api('GET','/v0/session');assert.equal(window.windowTitle,window.windowBaseTitle+' - 由智能体接管');assert.equal(window.windowIconState,'original_with_agent_pointer');for(const [old,current,value] of [['doDaylightCycle','advance_time','false'],['doWeatherCycle','advance_weather','false'],['doMobSpawning','spawn_mobs','false'],['randomTickSpeed','random_tick_speed','0']])await command('gamerule '+(target.startsWith('1.')?old:'minecraft:'+current)+' '+value);await command('time set day');await command('weather clear');await command('kill @e[type=!minecraft:player]');await command('tp @s 0.5 80 0.5 0 0');});
 if(args.phase!=='prepare'){
  if(!args.scenario||['all','hud','hud-vitals'].includes(args.scenario))await hudVitalActions({target,api,command,wait,tree,key,check,samples:vitalSamples,serverTicksAfter,readConsumers:()=>toastConsumers(repo,base,token,resolve(instance,'minecraft-protocol/token'),target),readOnly:async fn=>{
   const before=await api('GET','/v0/input/state');assert.equal(before.pressedKeyCount,0);assert.equal(before.pressedButtonCount,0);
   await api('POST','/v0/control/release');lease=undefined;const input=await api('GET','/v0/input/state');
   try{assert.equal((await api('GET','/v0/control/mode')).mode,'READ');await fn();const after=await api('GET','/v0/input/state');assert.equal(after.inputDispatchSequence,input.inputDispatchSequence);assert.equal(after.pressedKeyCount,0);assert.equal(after.pressedButtonCount,0);}
   finally{const mode=await api('GET','/v0/control/mode');assert.equal(mode.mode,'READ');assert.equal(mode.reconsentRequired,false);lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;}
  }});
  if(!args.scenario||['all','special-menus'].includes(args.scenario))await specialMenuActions({target,api,command,wait,tree,nodes,ui,key,use,fixtureVisible,shiftItem,check,samples:specialMenuSamples,readConsumers:()=>toastConsumers(repo,base,token,resolve(instance,'minecraft-protocol/token'),target)});
  await check('Toast: native movement tutorial completes through routed jump and look',async()=>{
   const visible=await wait(t=>t.presentation.children.some(n=>n.class?.endsWith('TutorialToast')),'native movement tutorial shown');
   const toast=visible.presentation.children.find(n=>n.class?.endsWith('TutorialToast'));assert.equal(toast.textSupported,true);assert.equal(toast.labelSource,'last_native_toast_render');assert.deepEqual(toast.actions,[]);
   const before=await api('GET','/v0/input/state'),start=await api('GET','/v0/player');
   const steps=[{type:'key',key:32,action:1}];for(let i=0;i<60;i++)steps.push({type:'mouse.delta',dx:i%2? -2:2,dy:0},{type:'delay',durationMs:80});steps.push({type:'key',key:32,action:0});
   const op=await api('POST','/v0/pipelines',{steps,timeoutMs:18000});const done=await api('POST','/v0/operations/'+op.operationId+'/wait',{timeoutMs:20000});assert.equal(done.state,'completed',JSON.stringify(done));
   await wait(t=>!t.presentation.children.some(n=>n.class?.endsWith('TutorialToast')),'native tutorial completed and faded out');
   const end=await api('GET','/v0/player'),input=await api('GET','/v0/input/state');assert(end.clientTick>start.clientTick+40);assert(input.inputDispatchSequence>before.inputDispatchSequence);assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);
   toastSamples.push({kind:'tutorial',phase:toast.textPhase,label:toast.label,source:toast.labelSource,completedBy:'GAME_ROUTED_NATIVE_JUMP_AND_RELATIVE_LOOK',arrangeAuthority:'diagnostic_admin_command_creative',observedClientTicks:end.clientTick-start.clientTick,heldKeys:0,heldButtons:0});
   await command('tp @s 0.5 80 0.5 0 0');
  });
  await check('HUD: visible title, subtitle and actionbar; no queued Toast content in USER',async()=>{
   await command('title @s times 0 200 0');await command('title @s subtitle {"text":"UI subtitle fixture"}');
   await command('title @s title {"text":"UI title fixture"}');await command('title @s actionbar {"text":"UI actionbar fixture"}');
   const t=await wait(t=>t.presentation?.children?.some(n=>n.controlKey==='hud.title'&&n.label==='UI title fixture')&&t.presentation.children.some(n=>n.controlKey==='hud.actionbar'),'HUD text received');
   assert(t.presentation.children.some(n=>n.controlKey==='hud.subtitle'&&n.label==='UI subtitle fixture'));
   assert.equal(t.presentation.queuedToastCount,undefined);assert(t.presentation.children.every(n=>n.visible===true&&n.actions.length===0));
   await command('title @s clear');
   await wait(t=>!t.presentation.children.some(n=>n.controlKey==='hud.title'),'HUD title cleared');
  });
  await check('typed HUD DEBUG: native message fields, stale guards and independent consumers',async()=>{
   assert(uiDebugAuthorized,'HUD DEBUG must be explicitly authorized; no skipped authorization may count as PASS');
   const proof=JSON.parse(await readFile(resolve(repo,'validations/conformance-vanilla_ui/result',target+'-hud-content-probe.json'),'utf8'));
   assert.equal(proof.status,'PASS',JSON.stringify(proof));assert.equal(proof.probeOnly,true);assert.equal(proof.target,target);assert.equal(proof.processId,connection.pid);
   assert.equal(proof.checks,10);assert.equal(proof.playerInputUsed,false);assert.equal(proof.worldMutationUsed,false);
   assert(Date.parse(proof.testedAt)>=validationStarted&&Date.parse(proof.testedAt)<=Date.now()+1000,'Native HUD guard proof must come from this actual Loader run');
   uiDebugSamples.push({case:'native_hud_content_guard_probe',...proof});
   await command('title @s times 0 6000 0');
   for(const [field,text]of [['subtitle','HUD subtitle fixture'],['title','HUD title fixture'],['actionbar','HUD actionbar fixture']])
    await command('title @s '+field+' '+JSON.stringify({text}));
   try{
    await debugUi(async snapshot=>{
     const find=async key=>(await snapshot()).presentation.children.find(n=>n.controlKey===key);
     for(const key of ['hud.title','hud.subtitle','hud.actionbar']){
      let n=await find(key);assert(n?.resourceVersion&&n.debugTextGuardAvailable,key+' complete native HUD content guard');
      const original=n.value,request={operation:'ui.text.set',nodeId:n.nodeId,expectedResourceVersion:n.resourceVersion,expectedValue:original,value:'HUD Debug'};
      const rejected=async(body,code)=>{
       const before=await find(key),input=await api('GET','/v0/input/state');
       await assert.rejects(api('POST','/v0/debug/mutations',body),e=>e.code===code);
       assert.deepEqual((await find(key)).resourceVersion,before.resourceVersion);
       const clean=await api('GET','/v0/input/state');assert.equal(clean.inputDispatchSequence,input.inputDispatchSequence);assert.equal(clean.pressedKeyCount,0);assert.equal(clean.pressedButtonCount,0);
       uiDebugSamples.push({case:'hud_message_rejected',field:key,code,inputDelta:0});
      };
      for(const value of [false,{},null,'x'.repeat(4097)])await rejected({...request,value},'INVALID_UI_DEBUG_VALUE');
      await rejected({...request,expectedValue:original+' changed'},'VALUE_PRECONDITION_FAILED');
      try{
       const result=await applyUiMutation(request);assert.equal(result.after.value,'HUD Debug');assert.equal(result.sideEffects,'native_hud_plain_text_and_lifetime');
       await rejected(request,'STALE_RESOURCE_REVISION');
      }finally{
       n=await find(key);assert(n?.resourceVersion);
       const restored=await applyUiMutation({...request,expectedResourceVersion:n.resourceVersion,expectedValue:n.value,value:original});assert.equal(restored.after.value,original);
      }
      uiDebugSamples.push({case:'native_hud_text',field:key,restoredText:true,inputDelta:0,worldMutation:false,lifetimeRestored:false,richFormattingRestored:false});
     }
    });
    for(const key of ['hud.title','hud.subtitle','hud.actionbar'])
     await editorHandoff('debug_'+key.replace('.','_'),undefined,{selector:{controlKey:key},presentation:true,field:'value',operation:'ui.text.set',expiringPresentation:key==='hud.actionbar'});
    const rows=uiDebugSamples.filter(s=>s.case==='native_hud_text');assert.equal(rows.length,3);
    for(const key of ['hud.title','hud.subtitle','hud.actionbar'])for(const channel of ['mcp_stdio_debug','web_user_isolation'])
     assert.equal(editorSamples.filter(s=>s.editor==='debug_'+key.replace('.','_')&&s.transport===channel&&s.status==='PASS').length,1,'Missing actual HUD consumer: '+key+' '+channel);
   }finally{await command('title @s reset');}
  });
  await check('typed Toast DEBUG: resident lifecycle, native reset progress and one-way dismissal',async()=>{
   assert(uiDebugAuthorized,'Toast DEBUG must be explicitly authorized');
   const capabilities=await api('GET','/v0/debug/capabilities');
   for(const operation of ['ui.slider.set','ui.toast.reset','ui.toast.hide']){
    const entry=capabilities.operations.find(row=>row.operation===operation);assert(entry,operation+' must be in the actual Runtime capability response');
    assert.equal(entry.authority,'CLIENT_UI');assert.equal(entry.requiredScope,'debug.ui');assert.equal(entry.requiresArm,true);assert.equal(entry.resourceVersionPrecondition,true);
   }
   const proof=JSON.parse(await readFile(resolve(repo,'validations/conformance-vanilla_ui/result',target+'-toast-content-probe.json'),'utf8'));
   assert.equal(proof.status,'PASS',JSON.stringify(proof));assert.equal(proof.probeOnly,true);assert.equal(proof.target,target);assert.equal(proof.processId,connection.pid);assert.equal(proof.checks,7);
   const endpoint=JSON.parse(await readFile(resolve(instance,'minecraft-protocol/endpoint.json'),'utf8'));
   assert.equal(endpoint.pid,connection.pid);assert.equal(proof.playerInputUsed,false);assert.equal(proof.worldMutationUsed,false);
   assert(Date.parse(proof.testedAt)>=Date.parse(endpoint.processStartedAt)&&Date.parse(proof.testedAt)<=Date.now()+1000,'Toast guard proof must belong to this Runtime');
   uiDebugSamples.push({case:'native_toast_content_guard_probe',...proof,processStartedAt:endpoint.processStartedAt});
   const mode=await api('GET','/v0/control/mode');
   await api('POST','/v0/control/mode',{mode:'OPERATE',expectedModeVersion:mode.modeVersion});lease=undefined;
   await api('POST','/v0/diagnostics/ui/test-screen',{});
   lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;
   await ui({label:'Toast DEBUG Fixtures'});await ui({label:'Close Probe'});
   await wait(t=>t.presentation.children.filter(n=>n.role==='toast'&&n.label.startsWith('DEBUG ')).length===3,'three native Toast fixtures fully rendered');
   const pick=(t,kind)=>t.presentation.children.find(n=>n.debugLabel?.startsWith('DEBUG ')&&(kind==='system'?n.debugToastKind==='system':n.debugToastKind==='tutorial'&&n.debugProgressable===(kind==='progress')));
   await debugUi(async snapshot=>{
    for(const kind of ['system','progress','static'])assert(pick(await snapshot(),kind)?.resourceVersion,kind+' native owner/version');
    const rejected=async(req,code)=>{
     const before=pick(await snapshot(),req.operation==='ui.toast.reset'?'system':req.nodeId===pick(await snapshot(),'progress')?.nodeId?'progress':'static');
     const input=await api('GET','/v0/input/state');await assert.rejects(api('POST','/v0/debug/mutations',req),e=>e.code===code);
     const after=UiFind(await snapshot(),req.nodeId);assert.deepEqual(after.resourceVersion,before.resourceVersion);
     assert.equal((await api('GET','/v0/input/state')).inputDispatchSequence,input.inputDispatchSequence);
     uiDebugSamples.push({case:'native_toast_rejected',operation:req.operation,code,inputDelta:0});
    };
    function UiFind(t,id){return t.presentation.children.find(n=>n.nodeId===id);}
    let n=pick(await snapshot(),'progress');
    const req={operation:'ui.slider.set',nodeId:n.nodeId,expectedResourceVersion:n.resourceVersion,expectedValue:n.debugProgress,value:0.75};
    for(const value of [false,null,{},-0.01,1.01])await rejected({...req,value},'INVALID_UI_DEBUG_VALUE');
    await rejected({...req,expectedValue:0.123},'VALUE_PRECONDITION_FAILED');
    const changed=await applyUiMutation(req);assert.equal(changed.after.debugProgress,0.75);assert.equal(changed.sideEffects,'native_tutorial_display_progress_only');
    await rejected(req,'STALE_RESOURCE_REVISION');
    n=pick(await snapshot(),'progress');await applyUiMutation({...req,expectedResourceVersion:n.resourceVersion,expectedValue:n.debugProgress,value:0});
    n=pick(await snapshot(),'static');await rejected({...req,nodeId:n.nodeId,expectedResourceVersion:n.resourceVersion},'UI_DEBUG_UNSUPPORTED');
    uiDebugSamples.push({case:'native_tutorial_progress',restored:true,inputDelta:0,worldMutation:false});
    n=pick(await snapshot(),'system');const original=n.debugToastValue;
    const reset={operation:'ui.toast.reset',nodeId:n.nodeId,expectedResourceVersion:n.resourceVersion,expectedValue:original,value:{title:'DEBUG reset title',message:'DEBUG reset message'}};
    for(const value of [false,null,{}, {title:'missing message'}, {title:'title',message:null}, {title:'x'.repeat(4097),message:'message'}])await rejected({...reset,value},'INVALID_UI_DEBUG_VALUE');
    await rejected({...reset,expectedValue:{...original,title:'wrong'}},'VALUE_PRECONDITION_FAILED');
    const resetResult=await applyUiMutation(reset);assert.deepEqual(resetResult.after.debugToastValue,reset.value);assert.equal(resetResult.sideEffects,'native_toast_plain_text_reflow_and_lifetime');
    await rejected(reset,'STALE_RESOURCE_REVISION');
    n=pick(await snapshot(),'system');await applyUiMutation({...reset,expectedResourceVersion:n.resourceVersion,expectedValue:n.debugToastValue,value:original});
    uiDebugSamples.push({case:'native_system_toast_reset',restoredText:true,richFormattingRestored:false,lifetimeRestored:false,inputDelta:0,worldMutation:false});
   });
   await editorHandoff('debug_tutorial_progress',undefined,{selector:{debugToastKind:'tutorial',debugProgressable:true},presentation:true,field:'debugProgress',operation:'ui.slider.set',privateField:true,changedValue:0.25});
   await editorHandoff('debug_system_toast',undefined,{selector:{debugToastKind:'system'},presentation:true,field:'debugToastValue',operation:'ui.toast.reset',privateField:true,changedValue:{title:'MCP Toast',message:'MCP local display'}});
   await debugUi(async snapshot=>{
    for(const kind of ['progress','static','system']){
     let n=pick(await snapshot(),kind);assert(n?.resourceVersion);
     const req={operation:'ui.toast.hide',nodeId:n.nodeId,expectedResourceVersion:n.resourceVersion,expectedValue:false,value:true};
     if(kind==='system'&&target==='1.20.1-forge'){
      await assert.rejects(api('POST','/v0/debug/mutations',req),e=>e.code==='UI_DEBUG_UNSUPPORTED');
      assert.deepEqual(pick(await snapshot(),kind).resourceVersion,n.resourceVersion);uiDebugSamples.push({case:'native_system_toast_hide_absent',nativeMethodAbsent:true,inputDelta:0});continue;
     }
     for(const value of [false,null,'true'])await assert.rejects(api('POST','/v0/debug/mutations',{...req,value}),e=>e.code==='INVALID_UI_DEBUG_VALUE');
     const result=await applyUiMutation(req);assert.equal(result.sideEffects,'native_toast_hide_requested_not_reversible');
     assert(result.after===null||result.after.debugHideRequested===true);
     await assert.rejects(api('POST','/v0/debug/mutations',req),e=>['UI_NODE_NOT_FOUND','STALE_RESOURCE_REVISION','RESOURCE_MISMATCH'].includes(e.code));
     uiDebugSamples.push({case:'native_toast_hide',kind,oneWay:true,inputDelta:0,worldMutation:false});
     const deadline=Date.now()+10000;
     let removed=false;
     do{
      removed=!(await snapshot()).presentation.children.some(row=>row.resourceVersion?.resourceKey===n.resourceVersion.resourceKey);
      if(!removed)await pause();
     }while(!removed&&Date.now()<deadline);
     assert(removed,'Wait for the actual dismissed owner to leave before targeting the next indexed Toast');
    }
   });
   await wait(t=>!t.presentation.children.some(n=>n.class?.endsWith('TutorialToast')),'native tutorial dismissal completed');
   if(target==='1.20.1-forge'){
    const mode=await api('GET','/v0/control/mode');await api('POST','/v0/control/mode',{mode:'OPERATE',expectedModeVersion:mode.modeVersion});lease=undefined;
    await api('POST','/v0/diagnostics/ui/test-screen',{});lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;
    await ui({label:'End Toast DEBUG Fixture'});await ui({label:'Close Probe'});
    uiDebugSamples.push({case:'legacy_system_toast_fixture_cleanup',probeOnly:true,notAProductHideCapability:true});
   }
   await wait(t=>!t.presentation.children.some(n=>n.class?.endsWith('SystemToast')),'native system dismissal completed');
  });
  await check('Toast: native multiline advancement render phases never disclose the pending title',async()=>{
   await wait(t=>!t.presentation.children.some(n=>n.role==='toast'),'existing notifications finished');
   await command('advancement revoke @s only minecraft:end/find_end_city');
   await command('advancement grant @s only minecraft:end/find_end_city');
   const pick=t=>t.presentation.children.find(n=>n.class?.endsWith('AdvancementToast'));
   let t=await wait(t=>pick(t)?.textPhase==='header','multiline advancement header frame');
   let n=pick(t);assert.equal(n.labelSource,'last_native_toast_render');assert.equal(n.textSupported,true);
   assert(!n.label.includes('City'));assert.equal(n.debugLabel,undefined);assert.deepEqual(n.actions,[]);
   toastSamples.push({kind:'multiline',phase:n.textPhase,label:n.label});
   t=await wait(t=>pick(t)?.textPhase==='title','multiline advancement title frame');n=pick(t);
   assert.match(n.label.replaceAll('\n',' '),/City at the End of the Game/);assert(!n.label.includes('Advancement Made'));
   toastSamples.push({kind:'multiline',phase:n.textPhase,label:n.label});
   await key(290);assert(!(await tree()).presentation.children.some(n=>n.role==='toast'));
   await key(290);await wait(t=>!pick(t),'advancement notification expires');
   await command('advancement revoke @s only minecraft:end/find_end_city');
  });
  await check('Toast: recipe unlock text comes from native render, not an invented clickable control',async()=>{
   const consumers=target==='26.3-fabric'?await toastConsumers(repo,base,token,resolve(instance,'minecraft-protocol/token'),target):null;
   try{
   await command('recipe take @s minecraft:oak_planks');await command('recipe give @s minecraft:oak_planks');
   const t=await wait(t=>t.presentation.children.some(n=>n.class?.endsWith('RecipeToast')&&n.textPhase==='static'),'recipe notification rendered');
   const n=t.presentation.children.find(n=>n.class?.endsWith('RecipeToast'));
   assert.match(n.label,/New Recipe(?:s|\(s\)) Unlocked/);assert.match(n.label,/Check your recipe book/);
   assert.equal(n.labelSource,'last_native_toast_render');assert.equal(n.textSupported,true);assert.equal(n.active,false);assert.deepEqual(n.actions,[]);assert.equal(n.debugLabel,undefined);
   toastSamples.push({kind:'recipe',phase:n.textPhase,label:n.label});
   if(consumers)toastSamples.push(...await consumers.verify());
   await wait(t=>!t.presentation.children.some(n=>n.class?.endsWith('RecipeToast')),'recipe notification expires');
   await command('recipe take @s minecraft:oak_planks');
   }finally{await consumers?.close();}
  });
  await check('HUD: player list uses native visibility and decorated names, not hidden profile or score facts',async()=>{
   const list=t=>t.presentation.children.filter(n=>n.controlKey.startsWith('hud.playerlist.'));
   const rawKey=(key,action)=>api('POST','/v0/input/key',{key,action});
   let hidden=false;
   assert.equal(list(await tree()).length,0,'closed player list is not USER-visible');
   await rawKey(258,1);
   const tick=await api('GET','/v0/player');
   await wait(t=>t.clientTick>tick.clientTick+3,'Tab processed without a singleplayer list objective');
   assert.equal(list(await tree()).length,0,'singleplayer Tab does not invent an invisible list');
   await rawKey(258,0);
   await command('scoreboard objectives add ui_tab dummy');await command('scoreboard objectives setdisplay list ui_tab');
   await command('scoreboard players set @s ui_tab 37');
   await command('team add ui_tab_team');await command('team modify ui_tab_team prefix {"text":"[UI] "}');await command('team join ui_tab_team @s');
   try{
    await rawKey(258,1);
    let t=await wait(t=>list(t).some(n=>n.controlKey==='hud.playerlist.row.0'&&n.label.startsWith('[UI] ')),'native player list shown');
    const row=list(t).find(n=>n.controlKey==='hud.playerlist.row.0');
    assert.equal(list(t).length,1);assert.deepEqual(row.actions,[]);assert.equal(row.active,false);assert.equal(row.boundsAvailable,false);
    for(const field of ['uuid','profile','latency','gameMode','score','value'])assert.equal(row[field],undefined,field+' is not visible player-list text');
    await rawKey(258,0);await wait(t=>list(t).length===0,'Tab release hides list');
    await rawKey(290,1);await rawKey(290,0);hidden=true;await rawKey(258,1);
    await wait(t=>!t.presentation.children.some(n=>n.controlKey.startsWith('hud.')),'F1 hides player list too');
    await rawKey(258,0);await rawKey(290,1);await rawKey(290,0);hidden=false;
    await rawKey(258,1);await wait(t=>list(t).length===1,'Tab after F1 restores visible names');
    await key(69);await wait(t=>/InventoryScreen$/.test(t.screenClass),'inventory hides player list');assert.equal(list(await tree()).length,0);await key(256);
    const mode=await api('GET','/v0/control/mode');await api('POST','/v0/control/mode',{mode:'READ',expectedModeVersion:mode.modeVersion});lease=undefined;
    await wait(t=>list(t).length===0,'READ has no held Tab input');
    const debug=await api('GET','/v0/ui/tree?category=DEBUG');assert(list(debug).some(n=>n.label.startsWith('[UI] ')&&!n.visible),'DEBUG retains the captured hidden list');
    assert.equal((await api('GET','/v0/input/state')).pressedKeyCount,0);
    lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;
   }finally{
    if(lease){await rawKey(258,0);if(hidden){await rawKey(290,1);await rawKey(290,0);}await command('team leave @s');await command('team remove ui_tab_team');await command('scoreboard objectives remove ui_tab');}
   }
  });
  await check('spectator: native shortcuts separate selection from activation and expire safely',async()=>{
   await command('gamemode spectator @s');
   try{
    await wait(t=>t.uiInputContext==='spectator','spectator context');
    await ui({controlKey:'spectator.open'},'open');
    let t=await wait(t=>t.uiInputContext==='spectator_menu'&&nodes(t).some(n=>n.role==='spectator_item'),'native menu opened');
    const disabled=nodes(t).find(n=>n.role==='spectator_item'&&!n.activationEnabled);assert(disabled,'single-player category has no eligible players');
    assert.equal(disabled.label,'');assert(!('debugLabel'in disabled));assert.equal(disabled.boundsSemantics,'display_only');
    await ui({nodeId:disabled.nodeId},'select');
    t=await wait(t=>nodes(t).some(n=>n.entryIndex===disabled.entryIndex&&n.selected),'native hotbar highlight');
    const selected=nodes(t).find(n=>n.entryIndex===disabled.entryIndex);assert(selected.label);assert(!selected.actions.includes('activate'));
    const unchanged=await ui({nodeId:selected.nodeId},'click');assert.equal(unchanged.inputDispatchCount,0);
    assert.equal((await tree()).spectatorContextKey,t.spectatorContextKey);
    await assert.rejects(()=>ui({nodeId:disabled.nodeId},'select'),/UI_NODE_NOT_FOUND|UI_TARGET_INVALIDATED/);
    await key(290);assert.equal(nodes(await tree()).filter(n=>n.role==='spectator_item').length,0);
    await api('POST','/v0/control/release');lease=undefined;
    const debug=await api('GET','/v0/ui/tree?category=DEBUG');assert(nodes(debug).some(n=>n.role==='spectator_item'&&!n.visible&&n.debugLabel));
    await assert.rejects(()=>api('POST','/v0/ui/action',{action:'select',selector:{controlKey:'spectator.slot.8'}}),/TAKEOVER_REQUIRED/);
    lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;
    await key(290);
    // Selection may expire while F1 is toggled; reopen via the native shortcut when needed.
    t=await tree();if(t.uiInputContext==='spectator')await ui({controlKey:'spectator.open'},'open');
    await ui({controlKey:'spectator.slot.8'},'select');
    await wait(t=>nodes(t).some(n=>n.controlKey==='spectator.slot.8'&&n.selected),'Close selected');
    await ui({controlKey:'spectator.slot.8'},'activate');
    await wait(t=>t.uiInputContext==='spectator','native Close activation');
    await ui({controlKey:'spectator.open'},'open');
    await wait(t=>t.uiInputContext==='spectator','native inactivity expiry');
    if(target==='26.3-fabric'){
     await api('POST','/v0/control/release');lease=undefined;
     await api('POST','/v0/control/task',{action:'end',taskId});taskId=undefined;
     spectatorSamples.push(...await spectatorConsumers(repo,base,token,resolve(instance,'minecraft-protocol/token')));
     taskId=(await api('POST','/v0/control/task',{action:'begin',ttlMs:60000,budgetMs:300000})).taskId;
     lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;taskStarted=Date.now();
    }
   }finally{await command('gamemode creative @s');}
   await wait(t=>!t.uiInputContext,'spectator cleanup');
  });
  await check('HUD: effect icons respect visibility, beneficial/harmful rows, F1 and no hidden effect details',async()=>{
   await command('effect clear @s');
   try{
    await command('effect give @s minecraft:speed 600 4 false');await command('effect give @s minecraft:slowness 600 0 false');
    await command('effect give @s minecraft:invisibility 600 0 true');
    const icons=t=>t.presentation.children.filter(n=>n.role==='status_icon');
    let t=await wait(t=>icons(t).some(n=>n.effect==='minecraft:speed')&&icons(t).some(n=>n.effect==='minecraft:slowness'),'effect icon packets received');
    assert.equal(icons(t).length,2);assert(!JSON.stringify(icons(t)).includes('invisibility'));
    for(const n of icons(t)){assert.deepEqual(n.actions,[]);assert.equal(n.label,'');assert(!('duration'in n));assert(!('amplifier'in n));assert.equal(n.width,18);}
    assert.equal(icons(t).find(n=>n.effect==='minecraft:slowness').y-icons(t).find(n=>n.effect==='minecraft:speed').y,26);
    await key(290);await wait(t=>icons(t).length===0,'F1 hides effect icons');
    let mode=await api('GET','/v0/control/mode');await api('POST','/v0/control/mode',{mode:'READ',expectedModeVersion:mode.modeVersion});lease=undefined;
    const debug=await api('GET','/v0/ui/tree?category=DEBUG');assert(icons(debug).some(n=>n.effect==='minecraft:invisibility'&&!n.visible));
    lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;await key(290);
    await wait(t=>icons(t).length===2,'F1 restores effect icons');
    await command('effect clear @s minecraft:speed');await wait(t=>!icons(t).some(n=>n.effect==='minecraft:speed'),'removed icon disappears');
    await command('effect give @s minecraft:strength 5 0 false');
    mode=await api('GET','/v0/control/mode');await api('POST','/v0/control/mode',{mode:'READ',expectedModeVersion:mode.modeVersion});lease=undefined;
    const end=Date.now()+8000;let received=false,finished=false;const opacities=[];
    do{
     const d=await api('GET','/v0/ui/tree?category=DEBUG');const icon=icons(d).find(n=>n.effect==='minecraft:strength');
     if(icon){received=true;if(icon.opacity!==undefined){assert.equal(icon.iconSource,'last_native_effect_icon_draw');assert(icon.opacity>=0&&icon.opacity<=1);opacities.push(icon.opacity);if(icon.renderPhase==='transparent'){assert.equal(icon.opacity,0);assert.equal(icon.visible,false);}}}
     else if(received){finished=true;break;}
     const user=icons(await tree());for(const n of user){assert.equal(n.renderPhase,'drawn');assert.equal(n.iconSource,'last_native_effect_icon_draw');assert(n.opacity>0&&n.opacity<=1);assert(!('duration'in n));assert(!('amplifier'in n));}
     await pause();
    }while(Date.now()<end);
    assert(received&&finished,'short-lived icon arrived and expired');
    assert(opacities.some(v=>v===0)&&opacities.some(v=>v>0),'native transparent and visible blink frames both observed');
    effectSamples.push({effect:'minecraft:strength',source:'last_native_effect_icon_draw',samples:opacities.length,minimum:Math.min(...opacities),maximum:Math.max(...opacities),expired:true,status:'PASS'});
    await wait(t=>icons(t).every(n=>n.effect!=='minecraft:strength'),'expired effect icon removed');
    lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;
    if(target==='26.3-fabric'){
     await command('effect give @s minecraft:strength 600 0 false');
     await wait(t=>icons(t).some(n=>n.effect==='minecraft:strength'&&n.opacity===1),'steady icon for read-only consumers');
     const m=await api('GET','/v0/control/mode');await api('POST','/v0/control/mode',{mode:'READ',expectedModeVersion:m.modeVersion});lease=undefined;
     const consumers=await toastConsumers(repo,base,token,resolve(instance,'minecraft-protocol/token'),target);
     try{effectSamples.push(...await consumers.verifyEffect('minecraft:strength'));}finally{await consumers.close();}
     assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
     lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;
    }
   }finally{await command('effect clear @s');}
   await wait(t=>!t.presentation.children.some(n=>n.role==='status_icon'),'effects cleanup');
  });
  await check('HUD: hotbar icons, offhand and native selection; hidden HUD and menus do not leak',async()=>{
   await command('clear @s');await command('item replace entity @s hotbar.0 with minecraft:stone 12');
   await command('item replace entity @s weapon.offhand with minecraft:torch 3');await key(49);
   let t=await wait(t=>t.presentation.children.some(n=>n.controlKey==='hud.hotbar.0'&&n.count===12)&&t.presentation.children.some(n=>n.controlKey==='hud.offhand'&&n.count===3),'hotbar received');
   let bar=t.presentation.children.filter(n=>n.controlKey.startsWith('hud.hotbar.'));assert.equal(bar.length,9);
   assert.equal(bar[0].item,'minecraft:stone');assert.equal(bar[0].selected,true);assert.equal(bar[0].label,'');assert(bar.every(n=>n.actions.length===0));
   await key(50);t=await wait(t=>t.presentation.children.some(n=>n.controlKey==='hud.hotbar.1'&&n.selected),'hotbar native key selection');
   await key(290);await wait(t=>!t.presentation.children.some(n=>n.controlKey.startsWith('hud.')),'F1 hides HUD');
   await api('POST','/v0/control/release');lease=undefined;
   const hidden=await api('GET','/v0/ui/tree?category=DEBUG');assert(hidden.presentation.children.some(n=>n.controlKey==='hud.hotbar.0'&&!n.visible&&n.count===12));
   lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;
   await key(290);await wait(t=>t.presentation.children.some(n=>n.controlKey==='hud.hotbar.0'),'F1 restores HUD');
   await key(69);await wait(t=>/InventoryScreen$/.test(t.screenClass),'inventory obscures HUD');
   assert(!(await tree()).presentation.children.some(n=>n.controlKey.startsWith('hud.')));await key(256);await command('clear @s');
  });
  await check('HUD: bossbar displayed progress and removal',async()=>{
   await command('bossbar add ui_fixture:bar {"text":"UI Boss fixture"}');
   try{
    await command('bossbar set ui_fixture:bar max 100');await command('bossbar set ui_fixture:bar value 25');await command('bossbar set ui_fixture:bar players @s');
    const t=await wait(t=>t.presentation.children.some(n=>n.controlKey.startsWith('hud.bossbar.')&&n.label==='UI Boss fixture'&&Math.abs(n.value-0.25)<0.001),'bossbar visible progress');
    const bar=t.presentation.children.find(n=>n.label==='UI Boss fixture');assert.deepEqual(bar.actions,[]);assert.equal(bar.role,'progress_bar');
    await command('bossbar set ui_fixture:bar name {"text":""}');await wait(t=>t.presentation.children.some(n=>n.role==='progress_bar'&&n.label===''&&n.value===0.25),'empty boss name still displays bar');
    await command('bossbar set ui_fixture:bar visible false');await wait(t=>!t.presentation.children.some(n=>n.controlKey.startsWith('hud.bossbar.')),'bossbar hidden');
   }finally{await command('bossbar remove ui_fixture:bar');}
  });
  await check('HUD: sidebar display order, hidden rows, cap, team sidebar and number formatting',async()=>{
   await command('scoreboard objectives add ui_fixture dummy {"text":"UI Sidebar fixture"}');
   await command('scoreboard objectives add ui_team dummy {"text":"UI Team fixture"}');
   await command('team add ui_fixture');
   try{
    for(let i=0;i<17;i++)await command('scoreboard players set Entry'+String(i).padStart(2,'0')+' ui_fixture '+i);
    await command('scoreboard players set #Hidden ui_fixture 999');await command('scoreboard objectives setdisplay sidebar ui_fixture');
    let t=await wait(t=>t.presentation.children.some(n=>n.controlKey==='hud.sidebar.title'&&n.label==='UI Sidebar fixture')&&t.presentation.children.filter(n=>n.controlKey.startsWith('hud.sidebar.row.')).length===(target.startsWith('1.20')?14:15)&&t.presentation.children.some(n=>n.label==='Entry16: 16'),'sidebar client rows fully received');
    let rows=t.presentation.children.filter(n=>n.controlKey.startsWith('hud.sidebar.row.'));assert.equal(rows.length,target.startsWith('1.20')?14:15);assert(!JSON.stringify(rows).includes('#Hidden'));assert(!JSON.stringify(rows).includes('Entry00'));assert(!JSON.stringify(rows).includes('Entry01'));
    assert(rows.some(n=>n.label==='Entry16: 16'));assert(rows.every(n=>n.actions.length===0&&!('value'in n)));
    if(!target.startsWith('1.20')){await command('scoreboard objectives modify ui_fixture numberformat blank');await wait(t=>t.presentation.children.filter(n=>n.controlKey.startsWith('hud.sidebar.row.')).every(n=>!n.label.includes(':')),'blank format hides raw score');}
    await command('scoreboard players set TeamEntry ui_team 7');
    await command('team modify ui_fixture color red');await command('team join ui_fixture @s');await command('scoreboard objectives setdisplay sidebar.team.red ui_team');
    t=await wait(t=>t.presentation.children.some(n=>n.controlKey==='hud.sidebar.title'&&n.label==='UI Team fixture'),'team sidebar replaces generic');assert(!t.presentation.children.some(n=>n.label==='UI Sidebar fixture'));
    await command('scoreboard players reset TeamEntry ui_team');
    if(target.startsWith('1.20'))await wait(t=>!t.presentation.children.some(n=>n.controlKey==='hud.sidebar.title'),'1.20 empty sidebar has no rendered header');
    else await wait(t=>t.presentation.children.some(n=>n.controlKey==='hud.sidebar.title')&&!t.presentation.children.some(n=>n.controlKey.startsWith('hud.sidebar.row.')),'modern empty sidebar retains header');
   }finally{await command('team remove ui_fixture');await command('scoreboard objectives remove ui_fixture');await command('scoreboard objectives remove ui_team');}
   await wait(t=>!t.presentation.children.some(n=>n.controlKey.startsWith('hud.sidebar.')),'sidebar removed');
  });
  await check('container family: native slots across standard block menus',async()=>{
   await command('fill -2 79 -2 2 79 4 minecraft:stone');
   await command('setblock 0 80 2 minecraft:stone');
   for(const [block,screen] of [['chest','ContainerScreen'],['furnace','FurnaceScreen'],['blast_furnace','BlastFurnaceScreen'],['smoker','SmokerScreen'],['anvil','AnvilScreen'],['hopper','HopperScreen'],['dispenser','DispenserScreen'],['brewing_stand','BrewingStandScreen'],['shulker_box','ShulkerBoxScreen'],['grindstone','GrindstoneScreen'],['smithing_table','SmithingScreen'],['cartography_table','CartographyTableScreen'],['crafting_table','CraftingScreen'],['enchanting_table','EnchantmentScreen'],['loom','LoomScreen'],['stonecutter','StonecutterScreen'],['beacon','BeaconScreen']]){
    const pitch=block==='stonecutter'?10:0;
    await command('setblock 0 81 2 minecraft:'+block);await command('clear @s');await command('tp @s 0.5 80 0.5 0 '+pitch);await fixtureVisible(block,pitch);await use();
    const t=await wait(t=>t.screenClass?.endsWith(screen),block);const slots=nodes(t).filter(n=>n.role==='slot');assert(slots.length>0,block);
    assert.equal(new Set(slots.map(n=>n.slot)).size,slots.length,block+' unique slots');assert.equal(typeof t.carriedStack.empty,'boolean');assert.equal(typeof t.carriedStack.id,'string');assert(Number.isInteger(t.carriedStack.count));await ui({nodeId:slots.at(-1).nodeId},'hover');await key(256);
   }
   await command('setblock 0 81 2 minecraft:air');
  });
  await check('creative: rendered scrollbar drag, slot content refresh and tab visibility',async()=>{
   await key(69);let t=await wait(t=>/CreativeModeInventoryScreen$/.test(t.screenClass),'creative scroll');
   await ui({nodeId:nodes(t).find(n=>n.resourceId==='minecraft:search').nodeId});await ui({role:'text_field'},'type_text',{text:''});
   t=await tree();const before=nodes(t).filter(n=>n.role==='slot').slice(0,45).map(n=>n.item);assert(t.carriedStack.empty);
   await debugScreenScroller('creative.scrollbar');
   t=await dragScroller('creative.scrollbar',0.95);assert.notDeepEqual(nodes(t).filter(n=>n.role==='slot').slice(0,45).map(n=>n.item),before);assert(t.carriedStack.empty);
   t=await dragScroller('creative.scrollbar',0.02);assert.deepEqual(nodes(t).filter(n=>n.role==='slot').slice(0,45).map(n=>n.item),before);
   await ui({role:'text_field'},'type_text',{text:'barrier'});t=await tree();const disabled=nodes(t).find(n=>n.controlKey==='creative.scrollbar.track');assert(disabled&&!disabled.active);assert.deepEqual(disabled.actions,[]);
   await ui({nodeId:nodes(t).find(n=>n.resourceId==='minecraft:inventory').nodeId});assert(!nodes(await tree()).some(n=>n.controlKey?.startsWith('creative.scrollbar')));await key(256);
  });
  await check('item gesture: native semantic drag distributes one stack across nine slots',async()=>{
   await command('clear @s');await command('item replace entity @s inventory.0 with minecraft:stone 64');
   await key(69);let t=await wait(t=>/CreativeModeInventoryScreen$/.test(t.screenClass),'creative inventory');
   const tab=nodes(t).find(n=>n.resourceId==='minecraft:inventory');assert(tab);await ui({nodeId:tab.nodeId});
   t=await wait(t=>nodes(t).some(n=>n.role==='slot'&&n.visible!==false&&n.item==='minecraft:stone'&&n.count===64),'native full stack received');
   const source=nodes(t).find(n=>n.role==='slot'&&n.visible!==false&&n.item==='minecraft:stone'&&n.count===64);
   await ui({nodeId:source.nodeId});t=await wait(t=>t.carriedStack?.id==='minecraft:stone'&&t.carriedStack.count===64,'full stack picked up');
   const groups=Map.groupBy(nodes(t).filter(n=>n.role==='slot'&&n.visible!==false&&n.active!==false&&n.count===0),n=>n.y);
   const rows=[...groups.entries()].filter(([,slots])=>slots.length===9).sort((a,b)=>a[0]-b[0]).map(([,slots])=>slots.sort((a,b)=>a.x-b.x));assert(rows.length>=2);
   const row=rows[0],rest=rows[1][0];
   const op=await api('POST','/v0/pipelines',{steps:[{type:'ui.drag',fromSelector:{nodeId:row[0].nodeId},toSelector:{nodeId:row.at(-1).nodeId},button:0,segments:32,durationMs:900}],timeoutMs:15000});
   const done=await api('POST','/v0/operations/'+op.operationId+'/wait',{timeoutMs:18000});assert.equal(done.state,'completed',JSON.stringify(done));
   t=await wait(t=>row.every(slot=>nodes(t).some(n=>n.nodeId===slot.nodeId&&n.item==='minecraft:stone'&&n.count===7))&&t.carriedStack?.count===1,'native nine-slot distribution and carried remainder');
   await ui({nodeId:rest.nodeId});await wait(t=>t.carriedStack?.empty&&nodes(t).some(n=>n.nodeId===rest.nodeId&&n.item==='minecraft:stone'&&n.count===1),'remainder safely deposited');
   const input=await api('GET','/v0/input/state');assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);
   effectSamples.push({kind:'item_drag',arrangeAuthority:'diagnostic_admin_command_creative',actAuthority:'PLAYTEST',semanticSlots:true,dragRoute:'ui.drag_native_callbacks',distributedSlots:9,countPerSlot:7,remainder:1,heldKeys:0,heldButtons:0});
   await key(256);await command('clear @s');
  });
  await check('workstations: semantic loom and stonecutter scrollbar input',async()=>{
   // This scenario also runs independently of the general menu catalog's floor preparation.
   await command('fill -2 79 -2 2 79 4 minecraft:stone');await command('setblock 0 80 2 minecraft:stone');
   for(const [block,screen,items] of [['loom','LoomScreen',['white_banner','blue_dye']],['stonecutter','StonecutterScreen',[target.startsWith('1.20')?'stone':'tuff']]]){
    await command('setblock 0 81 2 minecraft:'+block);await command('clear @s');
    for(let i=0;i<items.length;i++)await command('item replace entity @s hotbar.'+i+' with minecraft:'+items[i]);
    const pitch=block==='stonecutter'?10:0;await command('tp @s 0.5 80 0.5 0 '+pitch);await fixtureVisible(block,pitch);await use();
    await wait(t=>t.screenClass?.endsWith(screen),'workstation scroll fixture');
    const initial=nodes(await tree()).find(n=>n.controlKey===block+'.scrollbar.track');assert(initial&&!initial.active);
    for(const item of items)await shiftItem('minecraft:'+item);
    const prefix=block==='loom'?'loom.pattern.':'stonecutter.recipe.';
    let t=await wait(t=>nodes(t).some(n=>n.controlKey?.startsWith(prefix)),'workstation choices received');
    const first=nodes(t).find(n=>n.controlKey?.startsWith(prefix));
    await debugScreenScroller(block+'.scrollbar');
    if(block==='stonecutter'&&target.startsWith('1.20')){
     const bar=nodes(t).find(n=>n.controlKey===block+'.scrollbar.track');assert(!bar.active);assert.deepEqual(bar.actions,[]);
    }else{
     t=await dragScroller(block+'.scrollbar',0.98);assert(!nodes(t).some(n=>n.nodeId===first.nodeId));
     await assert.rejects(ui({nodeId:first.nodeId}),e=>['UI_NODE_NOT_FOUND','UI_TARGET_INVALIDATED'].includes(e.code));
     const choice=nodes(await tree()).find(n=>n.controlKey?.startsWith(prefix));assert(choice);
     await ui({nodeId:choice.nodeId});await wait(t=>nodes(t).some(n=>n.controlKey===choice.controlKey&&n.selected),'scrolled native choice selected');
     await dragScroller(block+'.scrollbar',0.02);
    }
    assert((await tree()).carriedStack.empty);await key(256);
   }await command('setblock 0 81 2 minecraft:air');await command('clear @s');
  });
  await check('merchant: native scrollbar, recycled rows and stale offer rejection',async()=>{
   await command('clear @s');await command('kill @e[type=minecraft:villager,distance=..6]');
   for(const text of villagerFixtureCommands({target,tag:'ui_scroll_fixture',position:[0.5,80,2.5],invulnerable:true,sells:['stone','dirt','sand','cobblestone','oak_log','glass','gravel','clay','brick','iron_ingot','gold_ingot','diamond']}))await command(text);
   try{
    await command('tp @s 0.5 80 0.5 0 0');
    const end=Date.now()+10000;while(!(await api('GET','/v0/world/entities?radius=8&limit=20')).entities.some(n=>n.type==='minecraft:villager'&&Math.abs(n.z-2.5)<0.1)){assert(Date.now()<end);await pause();}
    await use();let t=await wait(t=>/MerchantScreen$/.test(t.screenClass)&&nodes(t).some(n=>n.controlKey==='merchant.scrollbar.track'&&n.active),'merchant fixture');
    const old=nodes(t).find(n=>n.controlKey==='merchant.trade.0');assert(old);
    await debugScreenScroller('merchant.scrollbar');
    t=await dragScroller('merchant.scrollbar',0.98);assert(!nodes(t).some(n=>n.nodeId===old.nodeId));
    await assert.rejects(ui({nodeId:old.nodeId}),e=>['UI_NODE_NOT_FOUND','UI_TARGET_INVALIDATED'].includes(e.code));
    const last=nodes(await tree()).find(n=>n.controlKey==='merchant.trade.11');assert(last);await ui({nodeId:last.nodeId});
    await wait(t=>nodes(t).some(n=>n.controlKey==='merchant.trade.11'&&n.selected),'scrolled offer selected');
    await dragScroller('merchant.scrollbar',0.02);assert((await tree()).carriedStack.empty);await key(256);
   }finally{await command('kill @e[tag=ui_scroll_fixture]');}
  });
  await check('creative inventory: unique semantic slots and real carried item',async()=>{await key(69);let t=await wait(t=>/CreativeModeInventoryScreen$/.test(t.screenClass),'creative');const tab=nodes(t).find(n=>n.resourceId==='minecraft:search');assert(tab);await ui({nodeId:tab.nodeId});await ui({role:'text_field'},'type_text',{text:'stone'});t=await tree();const slot=nodes(t).find(n=>n.role==='slot'&&n.item==='minecraft:stone');assert(slot);await ui({nodeId:slot.nodeId});const carried=(await tree()).carriedStack;assert.equal(carried.id,'minecraft:stone');assert(carried.count>0);await key(256);});
  await check('special editors: native command, structure and jigsaw fields',async()=>{
   for(const [block,screen] of [['command_block','CommandBlockEditScreen'],['structure_block','StructureBlockEditScreen'],['jigsaw','JigsawBlockEditScreen']]) {
    await command('setblock 0 81 2 minecraft:'+block);await command('tp @s 0.5 80 0.5 0 0');await fixtureVisible(block);await use();
    const t=await wait(t=>t.screenClass?.endsWith(screen),block);const field=nodes(t).find(n=>n.role==='text_field'&&n.active!==false&&n.visible!==false);
    assert(field,block+' semantic text field');await ui({nodeId:field.nodeId},'type_text',{text:block==='command_block'?'say ui-fixture':'ui_fixture'});
    assert(nodes(await tree()).some(n=>n.role==='text_field'&&n.value?.includes('ui_fixture')||n.value==='say ui-fixture'));await key(256);
   }await command('setblock 0 81 2 minecraft:air');
  });
  await check('chat: suggestions are selectable, not submitted',async()=>{await key(84);await wait(t=>/ChatScreen$/.test(t.screenClass),'chat');await ui({role:'text_field'},'type_text',{text:'/gam'});const t=await wait(t=>nodes(t).some(n=>n.role==='suggestion'),'suggestions');const n=nodes(t).find(n=>n.role==='suggestion'&&n.label==='gamemode');assert(n);await ui({nodeId:n.nodeId});assert(nodes(await tree()).find(n=>n.role==='text_field').value.includes('gamemode'));await closeOwnedChat('/gamemode');});
  await check('chat: rich text suggest-command link uses actual hit region',async()=>{const event=target.startsWith('1.')?{clickEvent:{action:'suggest_command',value:'/say semantic-link'}}:{click_event:{action:'suggest_command',command:'/say semantic-link'}};await command('tellraw @s '+JSON.stringify({text:'UI Link',...event}));await key(84);let t=await wait(t=>nodes(t).some(n=>n.role==='link'&&n.label.includes('UI Link')),'chat link');const n=nodes(t).find(n=>n.role==='link'&&n.label.includes('UI Link'));assert.equal(n.clickEventType,'suggest_command');await ui({nodeId:n.nodeId});assert(nodes(await tree()).find(n=>n.role==='text_field').value.includes('semantic-link'));await closeOwnedChat('/say semantic-link');});
  await check('book: style-derived page link and stale page identity',async()=>{
   await command('clear @s');
   const page={text:'Go to page two',...(target.startsWith('1.')?{clickEvent:{action:'change_page',value:'2'}}:{click_event:{action:'change_page',page:2}})};
   const pages=[page,{text:'Page two'}];
   const item=target.startsWith('1.20')?'minecraft:written_book{title:"UI Link Book",author:"Mine-Craft-Protocol",pages:['+pages.map(p=>"'"+JSON.stringify(p)+"'").join(',')+']}':
    'minecraft:written_book[written_book_content={title:"UI Link Book",author:"Mine-Craft-Protocol",pages:['+pages.map(p=>target.startsWith('1.')?"'"+JSON.stringify(p)+"'":JSON.stringify(p)).join(',')+']}]';
   await command('item replace entity @s weapon.mainhand with '+item);
   // A server command response is not proof that the client has received the held item.
   await wait(t=>!t.screenClass&&t.presentation.children.some(n=>n.selected&&n.item==='minecraft:written_book'&&n.count===1),'written book received in the selected client slot');
   await use();
   let t=await wait(t=>/BookViewScreen$/.test(t.screenClass)&&nodes(t).some(n=>n.role==='link'),'book page link');
   assert.equal(t.screenData.pageIndex,0);const link=nodes(t).find(n=>n.role==='link');assert.equal(link.clickEventType,'change_page');
   const context=t.editorContextKey;await ui({nodeId:link.nodeId});t=await tree();assert.equal(t.screenData.pageIndex,1);assert.equal(t.screenData.pageText,'Page two');assert.notEqual(t.editorContextKey,context);
   await assert.rejects(ui({nodeId:link.nodeId}),e=>['UI_NODE_NOT_FOUND','UI_TARGET_INVALIDATED'].includes(e.code));await key(256);
  });
  await check('book editor: multiline input, append, page identity, signing and actual saved content',async()=>{
   await command('clear @s');await command('fill -2 79 -2 2 79 4 minecraft:stone');await command('tp @s 0.5 80 0.5 0 0');await equip('writable_book');await use();
   let t=await wait(t=>/BookEditScreen$/.test(t.screenClass),'writable book');
   const first='First page\n第二行';
   await ui({controlKey:'book.page'},'type_text',{text:first,replace:true});
   const old=nodes(await tree()).find(n=>n.controlKey==='book.page');assert.equal(old.value,first);
   if(args.scenario==='ui-debug-editors'||args.scenario==='all')await debugEditorTextFields(['book.page']);
   await ui({controlKey:'book.next_page'});
   await ui({controlKey:'book.page'},'type_text',{text:'Page two',replace:true});
   await assert.rejects(ui({nodeId:old.nodeId},'type_text',{text:'MUST NOT ARRIVE'}),e=>['UI_NODE_NOT_FOUND','UI_TARGET_INVALIDATED'].includes(e.code));
   assert.equal(nodes(await tree()).find(n=>n.controlKey==='book.page').value,'Page two');
   await ui({controlKey:'book.page'},'type_text',{text:' appended',replace:false,focusOnly:true});
   assert.equal(nodes(await tree()).find(n=>n.controlKey==='book.page').value,'Page two appended');
   await ui({controlKey:'book.previous_page'});assert.equal(nodes(await tree()).find(n=>n.controlKey==='book.page').value,first);
   await editorHandoff('book',first);
   await click('book.signButton','Sign');
   await wait(t=>nodes(t).some(n=>n.controlKey==='book.title'),'book title');
   if(args.scenario==='ui-debug-editors'||args.scenario==='all')await debugEditorTextFields(['book.title']);
   if(args.scenario==='ui-debug-consumers'||args.scenario==='all')await editorHandoff('book_title','');
   await ui({controlKey:'book.title'},'type_text',{text:'UI Signed',replace:true});
   await click('book.finalizeButton','Sign and Close');
   await wait(t=>!t.screenClass&&t.presentation.children.some(n=>n.selected&&n.item==='minecraft:written_book'),'signed book returned from server');
   await use();t=await wait(t=>/BookViewScreen$/.test(t.screenClass),'signed book reader');
   assert.equal(t.screenData.pageCount,2);assert.equal(t.screenData.pageText,first);
   assert(!nodes(t).some(n=>n.actions?.includes('type_text')),'signed book is not editable');
   await ui({controlKey:'book.next_page'});assert.equal((await tree()).screenData.pageText,'Page two appended');await key(256);
  });
  for(const hanging of [false,true])await check((hanging?'hanging sign':'sign')+': four semantic lines, native save and reopen, stale editor rejection',async()=>{
   await command('clear @s');await command('fill -1 80 -1 1 82 1 minecraft:air');
   await command('setblock 0 79 1 minecraft:stone');
   if(hanging)await command('setblock 0 82 0 minecraft:stone');
   const pitch=hanging?-90:60;
   await command('tp @s 0.5 80 0.5 0 '+pitch);await equip(hanging?'oak_hanging_sign':'oak_sign',8);
   // The item acknowledgement above and camera condition are client facts, not a fixed delay.
   const end=Date.now()+10000;while(Math.abs((await api('GET','/v0/player')).pitch-pitch)>0.05){assert(Date.now()<end);await pause();}
   await use();let t=await wait(t=>hanging?/HangingSignEditScreen$/.test(t.screenClass):/\.SignEditScreen$/.test(t.screenClass),'placed sign editor');
   const messages=['Line one','第二行','Third','Last line'];
   for(const line of [3,0,2,1]){
    await ui({controlKey:'sign.line.'+line},'type_text',{text:messages[line],replace:true});
    assert.equal(nodes(await tree()).find(n=>n.controlKey==='sign.line.'+line).value,messages[line]);
   }
   t=await tree();const old=nodes(t).find(n=>n.controlKey==='sign.line.0');
   assert.equal(nodes(t).filter(n=>n.controlKey?.startsWith('sign.line.')).length,4);
   if(args.scenario==='ui-debug-editors'||args.scenario==='all')await debugEditorTextFields([0,1,2,3].map(line=>'sign.line.'+line));
   await editorHandoff(hanging?'hanging_sign':'sign',messages[2]);
   await click('gui.done','Done');await wait(t=>!t.screenClass,'save sign');
   // The upward placement ray hits the support from inside the hanging sign's block. Reopen
   // from the front of the actual panel, not by invoking the editor or clicking its support.
   await command('item replace entity @s weapon.mainhand with minecraft:air');
   await wait(t=>!t.screenClass&&t.presentation.children.some(n=>n.selected&&n.count===0),'empty hand');
   if(hanging){
    // Rotation=8 faces north. Keep clear of the workstation support left at z=2.
    await command('tp @s 0.5 80 -1.5 0 0');
    const end=Date.now()+10000;let readyTick;
    while(true){const p=await api('GET','/v0/player');if(Math.abs(p.x-0.5)<0.05&&Math.abs(p.y-80)<0.05&&Math.abs(p.z+1.5)<0.05&&Math.abs(p.yaw%360)<0.05&&Math.abs(p.pitch)<0.05){readyTick??=p.clientTick;if(p.clientTick>=readyTick+3)break;}else readyTick=undefined;assert(Date.now()<end,'reopen viewpoint must remain clear and stable');await pause();}
   }
   await serverTicksAfter();await use();
   t=await wait(t=>hanging?/HangingSignEditScreen$/.test(t.screenClass):/\.SignEditScreen$/.test(t.screenClass),'reopened sign');
   for(let line=0;line<4;line++)assert.equal(nodes(t).find(n=>n.controlKey==='sign.line.'+line).value,messages[line],'saved sign line '+line);
   await assert.rejects(ui({nodeId:old.nodeId},'type_text',{text:'MUST NOT ARRIVE'}),e=>['UI_NODE_NOT_FOUND','UI_TARGET_INVALIDATED'].includes(e.code));
   await click('gui.done','Done');await wait(t=>!t.screenClass,'close reopened sign');
   await command('fill -1 80 -1 1 82 1 minecraft:air');await command('clear @s');await command('tp @s 0.5 80 0.5 0 0');
  });
  await check('survival inventory: semantic 2x2 crafting and result transfer through real slots',async()=>{
   await command('clear @s');await command('gamemode survival @s');await equip('oak_log');await key(69);
   let t=await wait(t=>/\.InventoryScreen$/.test(t.screenClass)&&nodes(t).some(n=>n.role==='slot'&&n.item==='minecraft:oak_log'&&n.count===1),'survival inventory');
   const slots=nodes(t).filter(n=>n.role==='slot');assert.equal(new Set(slots.map(n=>n.slot)).size,slots.length);
   const log=slots.find(n=>n.item==='minecraft:oak_log');await ui({nodeId:log.nodeId});
   assert.equal((await tree()).carriedStack.id,'minecraft:oak_log');
   await ui({role:'slot',slot:1});
   t=await wait(t=>nodes(t).some(n=>n.role==='slot'&&n.slot===0&&n.item==='minecraft:oak_planks'&&n.count===4),'native crafting result');
   assert((await tree()).carriedStack.empty);await ui({role:'slot',slot:0});
   assert.equal((await tree()).carriedStack.count,4);await ui({role:'slot',slot:9});
   t=await wait(t=>nodes(t).some(n=>n.role==='slot'&&n.slot===9&&n.item==='minecraft:oak_planks'&&n.count===4),'result placed in first storage slot');
   assert(t.carriedStack.empty);assert.equal(nodes(t).find(n=>n.role==='slot'&&n.slot===1).count,0);
   await key(256);await command('gamemode creative @s');await command('clear @s');
  });
  await check('advancements: graph tabs, hover nodes and native panning',async()=>{
   await command('advancement revoke @s everything');await command('advancement grant @s only minecraft:story/mine_stone');
   const toast=await wait(t=>t.presentation.children.some(n=>n.role==='toast'&&n.class?.endsWith('AdvancementToast')&&n.label),'visible advancement Toast text');
   assert(toast.presentation.children.filter(n=>n.role==='toast').every(n=>n.actions.length===0));
   await command('advancement grant @s everything');await serverTicksAfter();await key(76);
   let t=await wait(t=>/AdvancementsScreen$/.test(t.screenClass)&&nodes(t).some(n=>n.controlKey==='advancement.tab'),'advancements received from server');
   let canvas;
   for(const tab of nodes(t).filter(n=>n.controlKey==='advancement.tab')){
    await ui({nodeId:tab.nodeId});t=await tree();canvas=nodes(t).find(n=>n.controlKey==='advancement.canvas');
    if(canvas?.canPanX)break;
   }
   assert(canvas?.canPanX,'Fixture must contain a horizontally pannable advancement tree');
   const n=nodes(t).find(n=>n.role==='advancement');assert(n);assert.deepEqual(n.actions,['hover']);await ui({nodeId:n.nodeId},'hover');
   const old=canvas.scrollX;const op=await api('POST','/v0/pipelines',{steps:[{type:'ui.drag',fromSelector:{nodeId:canvas.nodeId},toSelector:{nodeId:canvas.nodeId},
    fromOffset:{x:0.6,y:0.5},toOffset:{x:0.3,y:0.5},segments:12,durationMs:600}],timeoutMs:12000});
   assert.equal((await api('POST','/v0/operations/'+op.operationId+'/wait',{timeoutMs:12000})).state,'completed');
   assert.notEqual(nodes(await tree()).find(n=>n.controlKey==='advancement.canvas').scrollX,old);await key(256);
  });
  await check('statistics: native tabs and return without screenshots',async()=>{
   await key(256);await wait(t=>/PauseScreen$/.test(t.screenClass),'pause statistics');await click('gui.stats','Statistics');
   let t=await wait(t=>/StatsScreen$/.test(t.screenClass)&&nodes(t).some(n=>n.actions?.includes('click')),'statistics loaded');
   for(const name of ['stat.generalButton','stat.itemsButton','stat.mobsButton']){
    const n=nodes(t).find(n=>n.translationKey===name&&n.active!==false);if(n){await ui({nodeId:n.nodeId});t=await tree();assert(/StatsScreen$/.test(t.screenClass));}
   }
   await click('gui.done','Done');await wait(t=>/PauseScreen$/.test(t.screenClass),'statistics return');await click('menu.returnToGame','Back to Game');
  });
  await check('death: native respawn button and live world restored',async()=>{
   await command('gamemode survival @s');await command('kill @s');
   await wait(t=>/DeathScreen$/.test(t.screenClass)&&nodes(t).some(n=>n.translationKey==='deathScreen.respawn'&&n.active!==false),'death respawn enabled');
   await click('deathScreen.respawn','Respawn');await wait(t=>!t.screenClass,'native respawn');await command('gamemode creative @s');
  });
  await check('pause: Return to Game is routed, then Save & Quit',async()=>{await command('clear @s');await key(256);await wait(t=>/PauseScreen$/.test(t.screenClass),'pause');await click('menu.returnToGame','Back to Game');await wait(t=>!t.screenClass,'resume');await title();});
  if(args.scenario==='ui-debug-scroll'){
   const scrolls=uiDebugSamples.filter(s=>s.case==='native_screen_scroll');
   assert.deepEqual(scrolls.map(s=>s.controlKey),['creative.scrollbar','loom.scrollbar','stonecutter.scrollbar','merchant.scrollbar'],'All four native Screen scrollbar branches must actually run');
   for(const sample of scrolls.filter(s=>s.enabled))for(const transport of ['mcp_stdio_debug','web_user_isolation']){
    const rows=editorSamples.filter(s=>s.editor==='debug_screen_scroll_'+sample.controlKey.split('.')[0]&&s.transport===transport);
    assert.equal(rows.length,1,'Missing actual scrollbar consumer: '+sample.controlKey+' '+transport);assert.equal(rows[0].status,'PASS');
   }
  }
  await check('owned world management: select, edit and cancel without mutation',async()=>{
   await click('menu.singleplayer','Singleplayer');const t=await wait(t=>t.screenClass.endsWith('SelectWorldScreen'),'worlds');
   const row=nodes(t).find(n=>n.worldName==='UI-Conformance');assert(row);await ui({nodeId:row.nodeId});await click('selectWorld.edit','Edit');
   const edit=await wait(t=>t.screenClass.endsWith('EditWorldScreen'),'world edit');assert(nodes(edit).some(n=>n.role==='text_field'));await key(256);await title();
  });
  await check('UI DEBUG text: bounded EditBox state, cancel without saving',async()=>{
   await click('menu.singleplayer','Singleplayer');let t=await wait(t=>t.screenClass.endsWith('SelectWorldScreen'),'world selection');
   const row=nodes(t).find(n=>n.worldName==='UI-Conformance');assert(row);await ui({nodeId:row.nodeId});await click('selectWorld.edit','Edit');
   await wait(t=>t.screenClass.endsWith('EditWorldScreen'),'world editor');
   await debugUi(async snapshot=>{
     let n=nodes(await snapshot()).find(n=>n.role==='text_field'&&n.resourceVersion);assert(n);
     assert.equal(n.debugTextGuardAvailable,true,'Typed text token must bind the complete native value, not just its published prefix');
     const id=n.nodeId,original=n.value,req={operation:'ui.text.set',nodeId:id,expectedResourceVersion:n.resourceVersion,value:'UI DEBUG temporary',expectedValue:original};
     for(const value of ['x'.repeat(4097),null,false,0,{},[]])await rejectUiMutation({...req,value},'INVALID_UI_DEBUG_VALUE',snapshot);
     await rejectUiMutation({...req,expectedValue:original+'changed'},'VALUE_PRECONDITION_FAILED',snapshot);
     try{
       const result=await applyUiMutation(req);assert.equal(result.after.value,req.value);
       await rejectUiMutation(req,'STALE_RESOURCE_REVISION',snapshot);
       n=nodes(await snapshot()).find(n=>n.nodeId===id);assert.equal((await applyUiMutation({...req,expectedResourceVersion:n.resourceVersion,expectedValue:n.value,value:''})).after.value,'');
     }finally{n=nodes(await snapshot()).find(n=>n.nodeId===id);await applyUiMutation({...req,expectedResourceVersion:n.resourceVersion,expectedValue:n.value,value:original});}
     assert.equal(nodes(await snapshot()).find(n=>n.nodeId===id).value,original);
   });
   if(args.scenario==='ui-debug-consumers'||args.scenario==='all')await editorHandoff('native_editbox',undefined,{selector:{role:'text_field'},field:'value',operation:'ui.text.set'});
   await key(256);await title();
  });
 }
 await title();
 }
}catch(e){failure??=e;console.error(e.stack);failureControl=await api('GET','/v0/control/mode').catch(()=>undefined);failureInput=await api('GET','/v0/input/state').catch(()=>undefined);}
finally{
 try{if(!lease){const mode=await api('GET','/v0/control/mode');if(!mode.reconsentRequired){taskId=(await api('POST','/v0/control/task',{action:'begin',ttlMs:60000,budgetMs:60000})).taskId;lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;}}}catch(e){failure??=e;}
 try{if(lease){await title();const input=await api('GET','/v0/input/state');assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);}}catch(e){failure??=e;}
 finally{if(lease){try{await api('POST','/v0/control/release');}catch(e){failure??=e;}lease=undefined;}}
 if(taskId)await api('POST','/v0/control/task',{action:'end',taskId}).catch(e=>{failure??=e;});
 const hashes=startedSources;const sourceChangedDuringRun=JSON.stringify(hashes)!==JSON.stringify(await sourceHashes());
 if(sourceChangedDuringRun)failure??=new Error('Source changed during the run; this result cannot attest the current candidate');
 const manifest=await readFile(resolve(instance,'../baseline/fixture-manifest.json')).catch(()=>null);
 const catalog=JSON.parse(await readFile(resolve(repo,'validations/conformance-vanilla_ui/task/screens.json'),'utf8'));
 const coverage=catalog.families.map(f=>({family:f.name,encountered:f.screens.filter(s=>[...screens].some(cls=>cls.endsWith('.'+s))),notEncountered:f.screens.filter(s=>![...screens].some(cls=>cls.endsWith('.'+s))),...(f.limitation?{limitation:f.limitation}:{})}));
 const launchAttestation=JSON.parse(await readFile(resolve(instance,'../launch-attestation.json'),'utf8').catch(()=>'null'));
 const result={status:failure?'FAIL':'PASS',target,runtime:'development_native',runtimePort:port,launchAttestation,phase:args.phase??'test',scenario:args.scenario??'all',testedAt:new Date().toISOString(),testedCommit:execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).trim(),dirty:true,screens:[...screens],coverage,checks,worldSelectionSamples,sourceHashes:hashes,sourceChangedDuringRun,fixtureSha256:manifest?createHash('sha256').update(manifest).digest('hex'):null,noMultimodal:true,screenshotAssertions:0,persistentWriteCalls:0,
   notExecuted:['external server authentication','Realms/account-gated UI','world conversion/repair','all settings combinations','all recipe combinations','all world presets/rules/data-pack combinations','player-list header/footer, multiplayer sorting and 80-player boundary','spectator multiplayer category paging and teleport execution','spectator mouse/scancode/Loader-modified hotbar bindings','third-party UI'],...(failure?{error:failure.message,nativeError:failure.nativeError,failureTree,failureControl,failureInput}:{})};
 result.toastSamples=toastSamples;
 result.editorSamples=editorSamples;
 result.uiDebugSamples=uiDebugSamples;
 result.effectSamples=effectSamples;
 result.spectatorSamples=spectatorSamples;
 result.specialMenuSamples=specialMenuSamples;
 result.vitalSamples=vitalSamples;
 result.fixtureReadSamples=fixtureReadSamples;
 result.chatCloseSamples=chatCloseSamples;
 result.identityChurn=identityChurn;
 const resultDir=resolve(repo,'validations/conformance-vanilla_ui/result');await mkdir(resultDir,{recursive:true});const suffix=args.phase&&args.phase!=='test'?'-'+args.phase:args.scenario&&args.scenario!=='all'?'-'+args.scenario:'';await writeFile(resolve(resultDir,target+suffix+'.json'),JSON.stringify(result,null,2)+'\n');
 if(!args['keep-client'])try{const mode=await api('GET','/v0/control/mode');if(mode.reconsentRequired)throw new Error('Human override: client remains READ; reacquisition requires user consent');taskId=(await api('POST','/v0/control/task',{action:'begin',ttlMs:60000,budgetMs:60000})).taskId;lease=(await api('POST','/v0/control/acquire',{ttlMs:60000,taskId})).leaseId;await title();await click('menu.quit','Quit Game');}catch(e){let stopped=false;for(let i=0;i<30;i++){try{await api('GET','/v0/session');}catch{stopped=true;break;}await pause();}if(!stopped){if(lease)await api('POST','/v0/control/release').catch(()=>{});if(taskId)await api('POST','/v0/control/task',{action:'end',taskId}).catch(()=>{});console.error('Shutdown: '+e.message);process.exitCode=1;}}
}
if(failure)process.exitCode=1;
