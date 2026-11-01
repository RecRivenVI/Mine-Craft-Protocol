import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {connectRuntime,automaticTakeoverRequest} from '../../runtime-connection.mjs';
import {candidateSource,sha256} from '../../regression-v1_release/task/alpha-candidate.mjs';
const repo=resolve(import.meta.dirname,'../../../..'),target=process.argv.find(a=>a.startsWith('--target='))?.slice(9);
const instance=resolve(repo,'validations/integration-extension_platform/instance',target,'client');
const connection=await connectRuntime(instance,target),sourceHashes=await candidateSource(repo,[target]);
const checks=[],report={target,scope:'extensionpack_settings',sourceHashes,runtimeInstanceId:connection.instanceId,checks,persistentWriteCalls:0};
const phase=process.argv.find(a=>a.startsWith('--phase='))?.slice(8)??'initial';
assert(['initial','disabled-restart','enabled-restart'].includes(phase));report.phase=phase;
let lease;
const nodes=t=>(t.children??[]).flatMap(n=>[n,...nodes(n)]);
async function api(method,path,body,expectedError){
 const response=await connection.fetch(path,{method,headers:{...(lease?{'x-mcp-control-lease':lease}:{}),...(body?{'content-type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{})});
 const value=await response.json();if(expectedError){assert(!response.ok);assert(expectedError.includes(value.error),JSON.stringify(value));return value;}
 assert(response.ok,JSON.stringify(value));return value;
}
const tree=()=>api('GET','/v0/ui/tree');
async function preview(stage){
 const response=await connection.fetch('/v0/capture');assert(response.ok,'Extension settings screenshot');
 const root=resolve(process.env.MCP_EXTENSION_PREVIEW_ROOT??'D:/Workspaces/Artifacts/MineCraftProtocol/Previews/2026-11-01-extensionpacks');
 await mkdir(root,{recursive:true});const png=Buffer.from(await response.arrayBuffer());
 await writeFile(resolve(root,target+'-'+phase+'-'+stage+'.png'),png);
 if(stage==='overview')await writeFile(resolve(root,target+'.png'),png);
}
async function click(predicate,error){const ui=await tree(),found=nodes(ui).find(predicate);assert(found,'Expected semantic target: '+JSON.stringify(nodes(ui).map(n=>[n.label,n.controlKey])));
 const response=await api('POST','/v0/ui/action',{selector:{nodeId:found.nodeId},action:'click'},error);
 if(response.operationId){const operation=await api('POST',`/v0/operations/${response.operationId}/wait`,{timeoutMs:15000});assert.equal(operation.state,'completed',JSON.stringify(operation));}
 return response;
}
async function check(name,run){await run();checks.push({name,status:'PASS'});console.log('PASS: '+name);}
try{
 const readyUntil=Date.now()+120000;while(true){const view=await tree();if(view.screenClass.endsWith('TitleScreen')&&!view.overlayIdentity&&nodes(view).some(n=>n.label==='Mods'||n.translationKey==='fml.menu.mods'))break;assert(Date.now()<readyUntil,'Title readiness');await new Promise(done=>setTimeout(done,200));}
 lease=(await api('POST','/v0/control/acquire',automaticTakeoverRequest(await api('GET','/v0/control/mode'),{ttlMs:60000}))).leaseId;
 await check('native Loader configuration opens the in-game extensionpack page',async()=>{
  await click(n=>n.translationKey==='fml.menu.mods'||n.translationKey==='modmenu.title'||n.label==='Mods');
  await click(n=>n.role==='list_item'&&n.label?.includes('Mine Craft Protocol')&&!n.label.includes('Validation'));
  await click(n=>n.active!==false&&n.role==='button'&&/config|配置/i.test(n.translationKey??n.label??''));
  assert((await tree()).screenClass.endsWith('ProtocolConfigScreen'));
  await click(n=>n.label==='扩展包…');assert((await tree()).screenClass.endsWith('ExtensionPacksScreen'));
 });
 await check('Agent cannot first-approve content or enable an invalid dependency',async()=>{
  const t=await tree();report.settingsTree=t;
  for(const id of ['lifetime_left','lifetime_right']){
   const selector=nodes(t).find(n=>n.packId===id&&n.controlKey==='pack.select');assert(selector,JSON.stringify(t));
   assert.equal(selector.agentOperable,false);assert.equal(selector.active,false);
   const row=nodes(t).find(n=>n.packId===id&&n.role==='container');assert(row);
   if(id==='lifetime_left')assert.equal(row.approvalRequired,true);else assert.equal(row.dependencyCompatible,false);
   await click(n=>n.nodeId===selector.nodeId,['UI_NODE_NOT_ACTIONABLE']);
  }
  const first=nodes(await tree()).find(n=>n.packId==='lifetime_left'&&n.controlKey==='pack.select');
  const gesture=await api('POST','/v0/pipelines',{steps:[{type:'mouse.move',x:first.interactionX,y:first.interactionY},{type:'mouse.button',button:0,action:1},{type:'mouse.button',button:0,action:0}],timeoutMs:15000});
  const terminal=await api('POST',`/v0/operations/${gesture.operationId}/wait`,{timeoutMs:15000});assert.equal(terminal.state,'completed',JSON.stringify(terminal));
  assert(nodes(await tree()).some(n=>n.packId==='lifetime_left'&&n.controlKey==='pack.select'),'Raw Agent input cannot approve either');
  const list=await api('GET','/v0/extensions');report.catalog=list;
  assert.equal(list.packages.find(p=>p.packageId==='lifetime_left')?.status,'EXTENSION_APPROVAL_REQUIRED');
  assert.notEqual(list.packages.find(p=>p.packageId==='lifetime_right')?.status,'active');
 });
 await check('natural ordering and no reordering or Agent folder access',async()=>{
  const t=await tree();const all=nodes(t);
  const groups=all.map(n=>(n.children??[]).filter(row=>row.packId&&row.role==='container')).filter(rows=>rows.length);assert.equal(groups.length,phase==='disabled-restart'?1:2);
  for(const group of groups){const labels=group.map(n=>n.label);assert.deepEqual(labels,[...labels].sort((a,b)=>a.localeCompare(b,'en',{sensitivity:'base'})));}
  assert(all.filter(n=>n.controlKey==='pack.move_up'||n.controlKey==='pack.move_down').every(n=>n.active===false));
  const folder=all.find(n=>n.translationKey==='pack.openFolder');assert(folder&&folder.agentOperable===false&&folder.active===false);
  await preview('overview');
 });
 await check('approved selection persists across real Runtime restarts without hot-loading',async()=>{
  const catalog=await api('GET','/v0/extensions'),entry=catalog.packages.find(p=>p.packageId==='platform_probe');
  assert.equal(entry.status,phase==='disabled-restart'?'disabled':'active',JSON.stringify(entry));
  if(phase==='initial'){
   await click(n=>n.packId==='platform_probe'&&n.controlKey==='pack.unselect');
   let t=await tree();assert(nodes(t).some(n=>n.packId==='platform_probe'&&n.controlKey==='pack.select'&&n.active!==false));
   assert.equal((await api('GET','/v0/extensions')).packages.find(p=>p.packageId==='platform_probe').status,'active');
   await click(n=>n.packId==='platform_probe'&&n.controlKey==='pack.select');
   await click(n=>n.packId==='platform_probe'&&n.controlKey==='pack.unselect');
  }else if(phase==='disabled-restart'){
   await click(n=>n.packId==='platform_probe'&&n.controlKey==='pack.select');
   assert.equal((await api('GET','/v0/extensions')).packages.find(p=>p.packageId==='platform_probe').status,'disabled');
  }
  report.startupSelection=entry;report.afterSelection=await tree();
  const row=nodes(report.afterSelection).find(n=>n.packId==='platform_probe'&&n.role==='container');assert(row);assert.equal(row.restartRequired,phase!=='enabled-restart');
  await preview('selection');
 });
 await check('Done returns normally without held input or manual revocation',async()=>{
  await click(n=>n.translationKey==='gui.done');assert((await tree()).screenClass.endsWith('ProtocolConfigScreen'));
  await click(n=>n.translationKey==='gui.done');
  await click(n=>['gui.back','gui.done'].includes(n.translationKey)&&n.active!==false);assert((await tree()).screenClass.endsWith('TitleScreen'));
  await api('POST','/v0/control/release');lease=undefined;
  assert.equal((await api('GET','/v0/control/mode')).reconsentRequired,false);
  const input=await api('GET','/v0/input/state');report.finalInput=input;
  assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);
 });
 report.sourceChangedDuringRun=JSON.stringify(sourceHashes)!==JSON.stringify(await candidateSource(repo,[target]));assert.equal(report.sourceChangedDuringRun,false);
 report.status='PASS';
}catch(error){report.status='FAIL';report.error=String(error);try{report.failureTree=await tree();}catch{}throw error;}
finally{
 if(lease)try{await api('POST','/v0/control/release');}catch{}
 const path=resolve(repo,'validations/integration-extension_platform/result',target+'-client-settings'+(phase==='initial'?'':'-'+phase)+'.json');await mkdir(resolve(path,'..'),{recursive:true});
 report.testedAt=new Date().toISOString();report.toolSha256=sha256(await readFile(new URL(import.meta.url)));await writeFile(path,JSON.stringify(report,null,2)+'\n');
}
