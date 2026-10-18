import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { resolve, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';
import { automaticTakeoverRequest, guardAutomaticTakeover, connectRuntime } from '../../runtime-connection.mjs';
import { isDeepStrictEqual } from 'node:util';

// An idle test controller explicitly hands off its owned fixture. No user website is reused.
export async function editorConsumers(repo, base, token, tokenFile, kind, expected, target, debugEnabled=false, control) {
 const require = createRequire(resolve(repo, 'components/companion/package.json'));
 const load = path => import(pathToFileURL(resolve(repo, path)).href);
 const { Client } = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
 const { StdioClientTransport } = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
 const { RuntimeClient } = await load('components/companion/dist/runtime-client.js');
 const {loadConfig}=await load('components/companion/dist/config.js');
 const { UiConsole } = await load('components/companion/dist/ui-console.js');
 const { startUiConsole } = await load('components/companion/dist/ui-console-http.js');
 const env = Object.fromEntries(Object.entries(process.env).filter(([, value]) => typeof value === 'string'));
 delete env.MCP_MINECRAFT_TOKEN;delete env.MCP_MINECRAFT_BASE_URL;delete env.MCP_MINECRAFT_TOKEN_FILE;
 env.MCP_MINECRAFT_INSTANCE_DIR=dirname(dirname(tokenFile));
 env.MCP_MINECRAFT_TARGET=target;
 const client = new Client({ name: 'editor-consumer-conformance', version: '1.0.0' });
 const transport = new StdioClientTransport({ command: process.execPath, args: [resolve(repo, 'components/companion/dist/index.js')], env, stderr: 'pipe' });
 const nodes = t => [...(t.children ?? []),...(control?.presentation?t.presentation?.children??[]:[])].flatMap(n => [n, ...nodes(n)]);
 const controlKey = kind === 'book' ? 'book.page' : kind === 'book_title' ? 'book.title' : 'sign.line.2';
 const find = tree => nodes(tree).find(node => control
  ? Object.entries(control.selector).every(([key,value]) => key==='labelContains' ? node.label?.includes(value) : node[key]===value)
  : node.controlKey===controlKey);
 const field=control?.field??'value';
 const results = [];
 let web, browserToken, mcpOwned = false, webOwned = false, lastMutation=0;
 const tool = async (name, args = {}, error) => {
  if(name==='minecraft_control'&&args.action==='acquire')args=automaticTakeoverRequest(await tool('minecraft_control',{action:'status'}),args);
  if(name==='minecraft_debug'&&['mutate','batch'].includes(args.action)){const delay=600-(Date.now()-lastMutation);if(delay>0)await new Promise(done=>setTimeout(done,delay));lastMutation=Date.now();}
  const response = await client.callTool({ name, arguments: args });
  if(error){assert(response.isError,JSON.stringify(response));assert.equal(response.structuredContent.error.code,error,JSON.stringify(response.structuredContent));return response.structuredContent.error;}
  assert(!response.isError, JSON.stringify(response.structuredContent));
  return response.structuredContent.data;
 };
 const request = async (path, body) => {
  const response = await fetch(web.url + '/api/' + path, { method: body === undefined ? 'GET' : 'POST', headers: { 'x-web-ui-token': browserToken, ...(body === undefined ? {} : { 'content-type': 'application/json' }) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }), signal: AbortSignal.timeout(30000) });
  const value = await response.json();
  if (!response.ok) throw Object.assign(new Error(value.message), { code: value.error });
  return value;
 };
 try {
  await client.connect(transport);
  const state = await tool('minecraft_control', { action: 'status' });
  assert.equal(state.mode, 'READ'); assert.equal(state.reconsentRequired, false);
  web = await startUiConsole(new UiConsole(guardAutomaticTakeover(new RuntimeClient(loadConfig(env)))), 0);
  let bootstrap;
  try{bootstrap=await fetch(web.url+'/api/bootstrap',{signal:AbortSignal.timeout(5000)});}
  catch(error){throw new Error(`Owned editor web bootstrap failed on port ${new URL(web.url).port}: ${error.cause?.code??error.cause?.message??error.message}`,{cause:error});}
  assert.equal(bootstrap.status,200,'Owned editor web bootstrap');
  ({ browserToken } = await bootstrap.json());
  const publicView=await request('snapshot');assert.equal(publicView.tree.dataCategory,'USER');assert.equal(publicView.ownsLease,false);assert(!('uiContextId'in publicView.tree));
   for(const node of nodes(publicView.tree))for(const field of ['resourceVersion','debugTextGuardAvailable','nativeTextContentFingerprint','debugToastValue','debugToastGuardAvailable','debugProgress','debugProgressable','debugHideRequested','debugPanReady','debugPanValue','debugPanRange'])assert(!(field in node),'USER web leaked '+field);
  if(debugEnabled){
   assert.equal(publicView.session.target,target);
   const native=await connectRuntime(dirname(dirname(tokenFile)),target);
   const input=async()=>{const r=await native.fetch('/v0/input/state');assert(r.ok);return r.json();};
   await tool('minecraft_control',{action:'set_mode',mode:'OPERATE'});
   await tool('minecraft_debug_arm',{action:'disarm'});
   const snapshot=()=>tool('minecraft_get_ui',{category:'DEBUG'});
   let tree=await snapshot(),original=find(tree),debugOperationId;assert(original?.resourceVersion);
   if(!control)assert(original.debugTextGuardAvailable);
   if(control)expected=original[field];else assert.equal(original.value,expected);
   const changedValue=control&&Object.hasOwn(control,'changedValue') ? control.changedValue : control ? typeof expected==='boolean' ? !expected : control.operation==='ui.scroll.set' ? expected>0?0:20 : expected<0.5?0.75:0.25 : 'MCP';
   if(control?.operation==='ui.text.set')assert(original.debugTextGuardAvailable);
   const mutation={operation:control?.operation??'ui.text.set',nodeId:original.nodeId,expectedResourceVersion:original.resourceVersion,expectedValue:expected,value:control?.operation==='ui.text.set'?'MCP':changedValue};
   const before=await input();assert.equal(before.pressedKeyCount,0);assert.equal(before.pressedButtonCount,0);
   let confirmedChange,mutationFailure;
   try{
    await tool('minecraft_debug',{action:'mutate',mutation},'DEBUG_ARM_REQUIRED');
    await tool('minecraft_debug',{action:'mutate',mutation,debugArmId:'invalid-ui-arm'},'DEBUG_NOT_ARMED');
    const user=await tool('minecraft_get_ui',{category:'USER'});assert.equal(user.dataCategory,'USER');
    const publicResponse=await fetch(web.url+'/api/snapshot',{headers:{'x-web-ui-token':browserToken}});
    assert.equal(publicResponse.status,200);const publicBody=await publicResponse.json();assert.equal(publicBody.tree.dataCategory,'USER');assert.equal(publicBody.ownsLease,false);
    for(const n of nodes(publicBody.tree))assert(!('resourceVersion'in n)&&!('debugFacts'in n),'USER must not disclose DEBUG metadata');
    assert.deepEqual(nodes(await snapshot()).find(n=>n.nodeId===original.nodeId).resourceVersion,original.resourceVersion);
    await tool('minecraft_debug_arm',{action:'arm',uiContextId:tree.uiContextId,namespaces:['ui'],ttlMs:60000});
    const wrongValue=control?.operation==='ui.canvas.pan.set'?{...expected,x:expected.x+0.125}:typeof expected==='boolean'?!expected:typeof expected==='number'?expected+0.123:typeof expected==='object'?{...expected,title:expected.title+' wrong'}:expected+' wrong';
    await tool('minecraft_debug',{action:'mutate',mutation:{...mutation,expectedValue:wrongValue}},'VALUE_PRECONDITION_FAILED');
    const changed=await tool('minecraft_debug',{action:'mutate',mutation});
    if(changed.after&&Object.hasOwn(changed.after,field)&&changed.afterResourceVersion)confirmedChange={value:structuredClone(changed.after[field]),resourceVersion:structuredClone(changed.afterResourceVersion)};
    assert.deepEqual(changed.after[field],mutation.value);assert.equal(changed.dataCategory,'DEBUG');assert.equal(changed.evidenceAuthority,'DEBUG_PRIVILEGED');assert.equal(changed.inputDispatchCount,0);
    await tool('minecraft_debug',{action:'mutate',mutation},'STALE_RESOURCE_REVISION');
   }catch(error){mutationFailure=error;throw error;
   }finally{
    try{
     const current=nodes(await snapshot()).find(n=>n.nodeId===original.nodeId);assert(current,'Owned editor must remain present for typed recovery');
     if(!isDeepStrictEqual(current[field],expected)){
      assert(confirmedChange,'No confirmed owned editor mutation; preserve instead of cleanup');
      assert.deepEqual(current[field],confirmedChange.value,'Editor value changed externally; preserve instead of cleanup');
      assert.deepEqual(current.resourceVersion,confirmedChange.resourceVersion,'Editor token changed externally; preserve instead of cleanup');
      const restored=await tool('minecraft_debug',{action:'batch',items:[{...mutation,expectedResourceVersion:current.resourceVersion,expectedValue:current[field],value:expected}]});
      assert.equal(restored.state,'completed');assert.equal(restored.result.succeededItems,1,JSON.stringify(restored));assert.deepEqual(restored.result.items[0].result.after[field],expected);assert.equal(restored.result.items[0].result.evidenceAuthority,'DEBUG_PRIVILEGED');
      debugOperationId=restored.operationId;
     }
    }catch(error){if(mutationFailure)mutationFailure.message+=`; cleanup: ${error.message}`;else throw error;}
    await tool('minecraft_debug_arm',{action:'disarm'});
    await tool('minecraft_control',{action:'set_mode',mode:'READ'});
   }
   if(debugOperationId){
    for(let repeat=0;repeat<2;repeat++){
     await tool('minecraft_get_operation',{operationId:debugOperationId,category:'USER'},'OPERATION_NOT_FOUND');
     await tool('minecraft_wait_operation',{operationId:debugOperationId,category:'USER',timeoutMs:1000},'OPERATION_NOT_FOUND');
    }
    for(const name of ['minecraft_assert','minecraft_wait'])await tool(name,{condition:{type:'operation',category:'USER',operationId:debugOperationId,expected:{state:'completed'}}},'OPERATION_NOT_FOUND');
    const diagnostic=await tool('minecraft_get_operation',{operationId:debugOperationId,category:'DEBUG'});assert.equal(diagnostic.result.items[0].result.evidenceAuthority,'DEBUG_PRIVILEGED');
    const cancelled=await tool('minecraft_cancel_operation',{operationId:debugOperationId});for(const field of ['result','error','errorCode','errorDetails'])assert(!(field in cancelled));
   }
   const after=await input(),mode=await tool('minecraft_control',{action:'status'});
   assert.equal(after.inputDispatchSequence,before.inputDispatchSequence);assert.equal(after.pressedKeyCount,0);assert.equal(after.pressedButtonCount,0);assert.equal(mode.mode,'READ');assert.equal(mode.takeoverActive,false);
   results.push({transport:'mcp_stdio_debug',editor:kind,operation:mutation.operation,status:'PASS',inputDelta:0,playerLeaseAcquired:false,typedRecovery:true,userOperationGetWaitAssertRejected:true,diagnosticCancelRedacted:true,rejections:['DEBUG_ARM_REQUIRED','DEBUG_NOT_ARMED','VALUE_PRECONDITION_FAILED','STALE_RESOURCE_REVISION','OPERATION_NOT_FOUND']});
   const restoredPublic=await request('snapshot');assert.equal(restoredPublic.tree.dataCategory,'USER');assert.equal(restoredPublic.ownsLease,false);
   const publicField=find(restoredPublic.tree);
   if(control?.privateField){if(publicField)assert.equal(publicField[field],undefined,'Private native display values must not leak into USER');}
   else if(publicField)assert.deepEqual(publicField[field],expected);else assert(control?.expiringPresentation,'A non-expiring restored field disappeared from USER');
   results.push({transport:'web_user_isolation',editor:kind,status:'PASS',operateUserSnapshotAvailable:true,debugMetadataLeaked:false,automaticLeaseAcquired:false});
  }
  if(control)return results;
  await tool('minecraft_control', { action: 'begin_task', ttlMs: 60000, budgetMs: 120000 }); mcpOwned = true;
  await tool('minecraft_control', { action: 'acquire', ttlMs: 60000 });
  const original = nodes(await tool('minecraft_get_ui')).find(n => n.controlKey === controlKey);
  assert.equal(original.value, expected);
  for (const text of ['MCP 验证', expected]) {
   const response = await tool('minecraft_interact_ui', { selector: { controlKey }, action: 'type_text', text, replace: true });
   assert.notEqual(response.directMutationUsed, true);
   assert.equal(nodes(await tool('minecraft_get_ui')).find(n => n.controlKey === controlKey).value, text);
  }
  await tool('minecraft_control', { action: 'release' });
  await tool('minecraft_control', { action: 'end_task' }); mcpOwned = false;
  results.push({ transport: 'mcp_stdio', editor: kind, status: 'PASS',textReplaceStrategy:original.textReplaceStrategy??'select_all',emptyRestoreVerified:expected==='' });
  let s = await request('acquire', {}); webOwned = true;
  const action = async (key, action, extra = {}) => {
   s = await request('snapshot');
   const n = nodes(s.tree).find(n => n.controlKey === key); assert(n);
   const response = await request('action', { nodeId: n.nodeId, frameKey: s.frameKey, action, ...extra });
   assert.notEqual(response.result?.directMutationUsed, true);
   s = await request('snapshot');
  };
  await action(controlKey, 'type_text', { text: 'Web', replace: true });
  await request('input', { type: 'text', text: ' 输入', sequence: s.inputSequence + 1, inputFrameKey: s.inputFrameKey });
  s = await request('snapshot');
  assert.equal(nodes(s.tree).find(n => n.controlKey === controlKey).value, 'Web 输入');
  await action(controlKey, 'type_text', { text: expected, replace: true });
  assert.equal(nodes(s.tree).find(n => n.controlKey === controlKey).value, expected);
  if (kind === 'book') {
   const old = { nodeId: nodes(s.tree).find(n => n.controlKey === controlKey).nodeId, frameKey: s.frameKey, action: 'type_text', text: 'MUST NOT ARRIVE' };
   await action('book.next_page', 'click');
   await assert.rejects(request('action', old), { code: 'WEB_UI_STALE_FRAME' });
   await action('book.previous_page', 'click');
   assert.equal(nodes(s.tree).find(n => n.controlKey === controlKey).value, expected);
  }
  s = await request('release', {}); webOwned = false;
  assert.equal(s.control.mode, 'READ');
  assert.equal(s.input.pressedKeyCount, 0); assert.equal(s.input.pressedButtonCount, 0);
  results.push({ transport: 'web_http', editor: kind, status: 'PASS' });
  return results;
 } finally {
  if(debugEnabled){await tool('minecraft_debug_arm',{action:'disarm'}).catch(()=>{});await tool('minecraft_control',{action:'set_mode',mode:'READ'}).catch(()=>{});}
  if (webOwned) await request('release', {}).catch(() => {});
  if (mcpOwned) {
   await tool('minecraft_control', { action: 'release' }).catch(() => {});
   await tool('minecraft_control', { action: 'end_task' }).catch(() => {});
  }
  try { await web?.close(); } finally { await client.close(); }
 }
}
