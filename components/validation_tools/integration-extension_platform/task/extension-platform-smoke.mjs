import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir,readdir} from 'node:fs/promises';
import {resolve,relative} from 'node:path';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';
import {connectRuntime,automaticTakeoverRequest} from '../../runtime-connection.mjs';
import {finishBaselineWorldExit} from '../../conformance-world_read/task/interaction-baseline-actions.mjs';
import {candidateSource,extensionPackageSource,extensionInputArtifacts} from '../../regression-v1_release/task/alpha-candidate.mjs';
import {adapterUiActions} from './adapter-ui-actions.mjs';
import {adapterWorldActions} from './adapter-world-actions.mjs';
import {lifecycleFaultActions,restoreLifecycleFaults} from './lifecycle-fault-actions.mjs';
import {serverAdapterActions} from './server-adapter-actions.mjs';
const repo=resolve(import.meta.dirname,'../../../..');
const args=Object.fromEntries(process.argv.slice(2).map(arg=>arg.replace(/^--/,'').split('=')));
const target=args.target,server=args.server==='true',installed=args.installed==='true',role=server?'server':'client';
const worldName='Extension-Platform-'+(args.adapter??'platform_probe');
const instance=resolve(repo,`validations/integration-extension_platform/instance/${installed?'installed/':''}${target}/${role}`);
const connection=await connectRuntime(instance,target);
let lease,arm,writeCalls=0;
const checks=[],observations=[],admissionRetries=[];
const pause=ms=>new Promise(done=>setTimeout(done,ms));
async function api(method,path,body,error){
  assert(!/storage[./]write|persistent.*write/i.test(path),'Persistent Write forbidden');
  for(let attempt=0;attempt<9;attempt++){
  const response=await connection.fetch(path,{method,headers:{...(lease?{'x-mcp-control-lease':lease}:{}),...(arm?{'x-mcp-debug-arm':arm}:{}),...(body?{'content-type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(20000)});
  const value=await response.json();
  if(!error&&response.status===429&&value.error==='EXPENSIVE_OPERATION_RATE_LIMITED'&&attempt<8){
    const waitMs=Math.max(500,Math.min(2000,value.retryAfterMillis??500));
    admissionRetries.push({path,attempt:attempt+1,waitMs,reason:value.error});
    await pause(waitMs);continue; // Admission rejection only; never retry a dispatched mutation or stale precondition.
  }
  if(response.ok&&path==='/v0/debug/arm'&&value.debugArmId)arm=value.debugArmId;
  if(response.ok&&path==='/v0/debug/disarm')arm=undefined;
  if(error){assert.equal(value.error,error,JSON.stringify(value));assert(!response.ok);return value;}
  if(!response.ok)throw Object.assign(new Error(JSON.stringify(value)),{code:value.error,status:response.status,runtimeError:value});return value;
  }
}
const nodes=tree=>(tree.children??[]).flatMap(node=>[node,...nodes(node)]);
async function until(test,description,timeout=120000){const end=Date.now()+timeout;do{const value=await test();if(value)return value;await pause(150);}while(Date.now()<end);throw new Error(description);}
const tree=()=>api('GET','/v0/ui/tree');
const screen=async(predicate,timeout=120000)=>{let last;try{return await until(async()=>{last=await tree();return !last.overlayIdentity&&predicate(last)?last:false;},'Screen transition timeout',timeout);}catch(error){observations.push({screenTimeout:last});throw error;}};
async function acquire(){if(lease&&(await api('GET','/v0/control/status')).leaseId===lease&&(await api('GET','/v0/control/mode')).mode==='TAKEOVER')return;lease=(await api('POST','/v0/control/acquire',automaticTakeoverRequest(await api('GET','/v0/control/mode'),{ttlMs:60000}))).leaseId;}
async function mode(value){const state=await api('GET','/v0/control/mode');await api('POST','/v0/control/mode',{mode:value,expectedModeVersion:state.modeVersion});lease=undefined;}
async function clickNode(node){
  assert(node?.nodeId,'Semantic target required');
  const started=await api('POST','/v0/ui/action',{selector:{nodeId:node.nodeId},action:'click'});
  if(!started.operationId)return started;
  const result=await api('POST',`/v0/operations/${started.operationId}/wait`,{timeoutMs:15000});
  assert.equal(result.state,'completed',JSON.stringify(result));return result;
}
async function click(key,label){const value=await tree(),found=nodes(value).filter(node=>(node.translationKey===key||node.label===label)&&node.active!==false&&node.visible!==false);assert.equal(found.length,1,JSON.stringify({key,candidates:found}));return clickNode(found[0]);}
async function key(key){const started=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key,holdMs:40}],timeoutMs:10000});assert.equal((await api('POST',`/v0/operations/${started.operationId}/wait`,{timeoutMs:10000})).state,'completed');}
async function shutdown(stopAtTitle=false){
  if(server)return;
  for(let i=0;i<8;i++){
    const state=await api('GET','/v0/control/mode');if(state.mode!=='TAKEOVER')await acquire();
    const value=await screen(()=>true);
    if(value.screenClass.endsWith('TitleScreen')){
      if(stopAtTitle){await api('POST','/v0/control/release');lease=undefined;return;}
      try{await click('menu.quit','Quit Game');}catch(error){if(error.code!=='RUNTIME_SHUTTING_DOWN'&&!(error instanceof TypeError))throw error;console.log('Quit response not confirmed; verifying normal process exit:',String(error));}
      return;
    }
    const exit=nodes(value).find(node=>['menu.disconnect','menu.returnToMenu'].includes(node.translationKey)&&node.active!==false);
    if(exit){observations.push({worldExit:await finishBaselineWorldExit({api,clickNode,wait:screen,quit:exit})});if((await api('GET','/v0/control/mode')).mode!=='TAKEOVER')lease=undefined;}
    else {const cancel=nodes(value).find(node=>node.translationKey==='gui.cancel'&&node.active!==false);if(cancel)await clickNode(cancel);else await key(256);}
  }
  throw new Error('Could not navigate normal shutdown');
}
if(args['shutdown-only']==='true'){await shutdown();process.exit(0);}
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const before=await candidateSource(repo,[target]);
const packageIds=['platform_probe',...(args.adapter&&args.adapter!=='platform_probe'?[args.adapter]:[]),...(args['lifetime-faults']==='true'?['lifetime_left','lifetime_right']:[])];
const packageSourceFiles=await extensionPackageSource(repo,packageIds);
const upstreamArtifacts=await extensionInputArtifacts(instance);
const toolFiles={};
for(const file of ['Invoke-ExtensionPlatformSmoke.ps1','extension-platform-smoke.mjs','install-platform-probe.mjs','adapter-ui-actions.mjs','adapter-world-actions.mjs','lifecycle-fault-actions.mjs','install-adapter.py']) {
  const path='components/validation_tools/integration-extension_platform/task/'+file;
  toolFiles[path]=hash(await readFile(resolve(repo,path)));
}
if(server)toolFiles['components/validation_tools/integration-extension_platform/task/server-adapter-actions.mjs']=hash(await readFile(resolve(import.meta.dirname,'server-adapter-actions.mjs')));
for(const path of ['components/companion/package.json','components/companion/package-lock.json'])toolFiles[path]=hash(await readFile(resolve(repo,path)));
for(const entry of await readdir(resolve(repo,'components/companion/dist'),{recursive:true,withFileTypes:true}))if(entry.isFile()){
  const path=relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/');
  if(!path.startsWith('components/companion/dist/test/'))toolFiles[path]=hash(await readFile(resolve(repo,path)));
}
const record={target,role,adapter:args.adapter??'platform_probe',runtimeInstanceId:connection.instanceId,developmentClasspath:!installed,finalJarInstall:installed,toolFiles,packageSourceFiles,upstreamArtifacts,checks,observations,admissionRetries,persistentWriteCalls:0};
if(installed){const product=Object.fromEntries((await readFile(resolve(repo,'gradle.properties'),'utf8')).split(/\r?\n/).filter(line=>line.includes('=')).map(line=>{const at=line.indexOf('=');return[line.slice(0,at),line.slice(at+1)];}));const name=`${product.mod_id}-${target}-${product.mod_version}.jar`;record.artifactSha256=hash(await readFile(resolve(instance,'mods',name)));assert.equal(record.artifactSha256,hash(await readFile(resolve(repo,'build/release',product.mod_version,name))));}
const check=async(name,run)=>{try{await run();checks.push({name,status:'PASS'});console.log('PASS: '+name);}catch(error){if(!server)try{observations.push({failedCheck:name,failureUiTree:await tree()});}catch(readError){observations.push({failedCheck:name,diagnosticReadError:String(readError)});}throw error;}};
const request={category:'DEBUG',perspective:'server_authoritative',domains:['providers'],includeProviderData:true,providerIds:['platform_probe:sampling'],providerQuery:{},budgets:{maxProviders:1,providerTimeoutMs:1000}};
async function read(){const value=await api('POST','/v0/observe/deep',request);assert.equal(value.providers[0].status,'completed',JSON.stringify(value.providers));return value;}
const data=value=>value.providers[0].data;
const version=value=>{const found=value.resourceRevisionRefs.find(ref=>ref.resourceType==='provider'&&ref.resourceKey==='platform_probe:sampling');assert(found?.mutationPreconditionEligible);return found;};
let fingerprint;
const mutation=(observed,action)=>({operation:'provider.mutate',providerId:'platform_probe:sampling',worldFingerprint:fingerprint,expectedResourceVersion:version(observed),mutation:{action}});
const startArm=async ttlMs=>{arm=(await api('POST','/v0/debug/arm',{worldFingerprint:fingerprint,namespaces:['provider'],ttlMs})).debugArmId;};
async function write(body,error){const state=server?null:await api('GET','/v0/input/state');writeCalls++;const value=await api('POST','/v0/debug/mutations',body,error);if(!server){const after=await api('GET','/v0/input/state');assert.equal(after.inputDispatchSequence,state.inputDispatchSequence);assert.equal(after.pressedKeyCount,0);assert.equal(after.pressedButtonCount,0);}return value;}
try {
  if(!server)await screen(value=>value.screenClass.endsWith('TitleScreen'));
  await check(server?'approved independent SDK package and static catalog/documents work without client classes':'approved independent SDK package loads and static catalog/documents work at title',async()=>{
    const catalog=await api('GET','/v0/extensions');const installed=catalog.packages.find(item=>item.packageId==='platform_probe');assert.equal(installed?.status,'active',JSON.stringify(catalog));assert(installed.registeredCapabilities.includes('platform_probe:sampling'));
    const bytes=await readFile(resolve(instance,'extensionpacks/platform_probe/extensionpack.json'));assert.equal(installed.contentIdentity,hash(bytes));record.packageIdentity=installed.contentIdentity;
    assert.equal(catalog.earlyHooks.state,'validated',JSON.stringify(catalog.earlyHooks));
    assert.equal(catalog.earlyHooks.globalFallbackReplaced,false);
    assert(catalog.earlyHooks.configs.some(config=>config.packageId==='platform_probe'&&config.registered),JSON.stringify(catalog.earlyHooks));
    const document=await api('GET','/v0/extensions/document?packageId=platform_probe&documentId=overview');assert.equal(hash(Buffer.from(document.text)),document.sha256);assert.equal(document.contentIsData,true);observations.push({catalog,document});
  });
  if(!server){
  await check('SDK UI enrichment retains native identity and supports normal menu navigation',async()=>{
    const before=await screen(value=>nodes(value).some(node=>node.capabilitySource==='platform_probe:native_ui'));
    const enriched=nodes(before).find(node=>node.capabilitySource==='platform_probe:native_ui');
    assert(enriched.elementIdentity>0&&!enriched.nodeId.startsWith('extension:'));
    await acquire();const hovered=await api('POST','/v0/ui/action',{selector:{nodeId:enriched.nodeId},action:'hover'});
    if(hovered.operationId)assert.equal((await api('POST',`/v0/operations/${hovered.operationId}/wait`,{timeoutMs:10000})).state,'completed');
    await click('menu.options','Options...');await screen(value=>value.screenClass.endsWith('OptionsScreen'));
    await key(256);await screen(value=>value.screenClass.endsWith('TitleScreen'));observations.push({nativeUiEnrichment:enriched});await api('POST','/v0/control/release');lease=undefined;
  });
  await acquire();await click('menu.singleplayer','Singleplayer');
  let value=await screen(t=>/SelectWorldScreen$|CreateWorldScreen$/.test(t.screenClass));
  if(value.screenClass.endsWith('SelectWorldScreen')){
    const world=nodes(value).find(node=>node.worldName===worldName);
    if(world){await clickNode(world);await click('selectWorld.select','Play Selected World');}
    else await click('selectWorld.create','Create New World');
  }
  value=await screen(t=>!t.screenClass||t.screenClass.endsWith('CreateWorldScreen'));
  if(value.screenClass){
    await api('POST','/v0/ui/action',{selector:{role:'text_field',nth:0},action:'type_text',text:worldName});
    for(let i=0;i<4;i++){value=await tree();const button=nodes(value).find(node=>node.label?.startsWith('Game Mode:'));assert(button);if(button.label.includes('Creative'))break;await clickNode(button);}
    const worldTab=nodes(await tree()).find(node=>node.role==='tab'&&node.label==='World');if(worldTab)await clickNode(worldTab);
    for(let i=0;i<12;i++){value=await tree();const type=nodes(value).find(node=>node.label?.startsWith('World Type:'));if(!type||/Superflat|Flat/.test(type.label))break;await clickNode(type);}
    const gameTab=nodes(await tree()).find(node=>node.role==='tab'&&node.label==='Game');if(gameTab)await clickNode(gameTab);
    await click('selectWorld.create','Create New World');
  }
  await screen(t=>!t.screenClass);await api('POST','/v0/control/release');lease=undefined;
  }
  fingerprint=(await api('GET','/v0/world/fingerprint?category=DEBUG')).worldFingerprint;
  if(!server&&target==='1.20.1-forge'&&(!args.adapter||args.adapter==='platform_probe')){
    await check('vanilla Forge experience-level draw remains observable after optional passive hook',async()=>{
      await mode('OPERATE');
      for(const command of ['gamemode survival @s','experience set @s 7 levels','experience set @s 9 points']){
        await api('POST','/v0/command/player',{command,route:'NATIVE'});await pause(550);
      }
      try {
        const view=await screen(value=>(value.presentation?.children??[]).some(node=>node.controlKey==='hud.context.experience.level'&&node.label?.includes('7')),10000);
        observations.push({case:'forge_vanilla_experience_draw',nodes:view.presentation.children.filter(node=>node.controlKey?.startsWith('hud.context.experience.'))});
      } finally {await api('POST','/v0/command/player',{command:'gamemode creative @s',route:'NATIVE'});}
    });
  }
  await check('registered Provider uses real server owner and DEBUG writes still require Arm and mode',async()=>{
    const observed=await read();assert.equal(data(observed).running,false);assert.equal(data(observed).samples,0);assert.match(data(observed).nativeOwnerType,server?/^net\.minecraft\.server\.dedicated\.DedicatedServer$/:/^net\.minecraft\.client\.server\.IntegratedServer$/);
    const early=(await api('GET','/v0/extensions')).earlyHooks;assert(early.configs.some(config=>config.appliedTargets.some(target=>target.includes('MinecraftServer'))),JSON.stringify(early));observations.push({earlyHooks:early});
    assert(data(observed).passiveNativeHookCalls>0,JSON.stringify(data(observed)));
    assert(!data(observed).nativeHelperClassLoader.includes('ExtensionClassSpace'),JSON.stringify(data(observed)));
    assert.equal(data(observed).passiveNativeHookScope,'physical_jvm');const body=mutation(observed,'start');
    await mode('OPERATE');await write(body,'DEBUG_NOT_ARMED');await startArm(60000);await mode('READ');await write(body,'OPERATE_REQUIRED');await mode('OPERATE');
    const started=await write(body);assert.equal(started.gameplayEvidence,false);assert.equal(started.evidenceContaminated,true);
    const sampled=await until(async()=>{const result=await read();return data(result).samples>0?result:false;},'Owner sample did not arrive',6000);assert.match(data(sampled).ownerThread,/Server thread/);assert(data(sampled).sampledAtNanos>0);observations.push({started,sampled});
  });
  await check(server?'headless Debug sampling requires no input Lease':'input Lease lifetime remains independent of the Debug sampling grant',async()=>{
    if(!server){await acquire();await api('POST','/v0/control/release');lease=undefined;assert.equal((await api('GET','/v0/control/mode')).mode,'READ');}
    const result=await read();assert.equal(data(result).running,true);assert(!(await api('GET','/v0/control/status')).leaseId);
    await mode('OPERATE');await pause(650);const stopped=await write(mutation(await read(),'stop'));assert.equal(stopped.after.running,false);observations.push({stopped});await api('POST','/v0/debug/disarm');arm=undefined;
  });
  await check('MCP lookup and typed start then Arm expiry clean real resources without late publication',async()=>{
    const require=createRequire(resolve(repo,'components/companion/package.json'));
    const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
    const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
    const env=Object.fromEntries(Object.entries(process.env).filter(([key,value])=>typeof value==='string'&&!/^MCP_MINECRAFT_|^MCP_COMPANION_AUTHORING_ONLY$/i.test(key)));Object.assign(env,{MCP_MINECRAFT_INSTANCE_DIR:instance,MCP_MINECRAFT_TARGET:target});
    const transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'}),client=new Client({name:'extension-platform-consumer',version:'1'});
    try {
      await client.connect(transport);const installed=await client.readResource({uri:'minecraft://extensions/installed'});assert(JSON.stringify(installed).includes(record.packageIdentity));
      const scenario=await client.readResource({uri:'minecraft://extensions/platform_probe/tests/smoke'});
      assert(JSON.stringify(scenario).includes('extension.test'));assert(JSON.stringify(scenario).includes(record.packageIdentity));
      const call=async(name,args)=>{const result=await client.callTool({name,arguments:args});assert(!result.isError,JSON.stringify(result));return result.structuredContent.data;};
      await call('minecraft_debug_arm',{action:'arm',worldFingerprint:fingerprint,namespaces:['provider'],ttlMs:1000});await pause(650);
      const observed=await call('minecraft_deep_observe',request);const started=await call('minecraft_debug',{action:'mutate',mutation:mutation(observed,'start')});assert.equal(started.after.running,true);
      const stopped=await until(async()=>{const result=await read();return !data(result).running?result:false;},'Expired grant did not clean sampling',6000);
      const samples=data(stopped).samples;await pause(400);assert.equal(data(await read()).samples,samples);
      const cleanup=await until(async()=>{const value=await api('GET','/v0/extensions/resources');return value.cleanup.some(item=>item.lifetime==='DEBUG_GRANT'&&item.complete&&item.resources.length>0)?value:false;},'No completed cleanup report',6000);
      assert(cleanup.cleanup.filter(item=>item.lifetime==='DEBUG_GRANT').every(item=>item.released),JSON.stringify(cleanup));observations.push({started,stopped,cleanup});
    } finally {await client.close();await transport.close();}
  });
  await api('POST','/v0/debug/disarm');arm=undefined;await mode('READ');
  if(!server)await check('world PartRef uses native parent pick and real player use, then rejects parent replacement',async()=>{
    await mode('OPERATE');
    for(const command of ['gamemode creative @s','clear @s','fill -2 80 0 2 84 4 minecraft:air','fill -2 79 0 2 79 4 minecraft:stone','tp @s .5 80 .5 0 0','setblock 0 80 2 minecraft:chest[facing=north]']){
      await api('POST','/v0/command/player',{command,route:'NATIVE'});await pause(550);
    }
    await acquire();
    let lastPartRead;
    const block=await until(async()=>{const value=lastPartRead=await api('GET','/v0/world/block?x=0&y=80&z=2&category=USER');return value.extensionParts?.some(part=>part.capabilitySource==='platform_probe:native_world')?value:false;},'No current native world part',10000).catch(error=>{observations.push({missingPartRead:lastPartRead});throw new Error(error.message+': '+JSON.stringify(lastPartRead));});
    const part=block.extensionParts.find(part=>part.capabilitySource==='platform_probe:native_world');
    assert.notEqual(part.targetRef,block.targetRef);assert.equal(part.category,'USER');assert.equal(part.perspective,'CLIENT_KNOWN');
    const started=await api('POST','/v0/pipelines',{steps:[{type:'world.action',targetRef:part.targetRef,action:'use'}],timeoutMs:12000});
    const result=await api('POST',`/v0/operations/${started.operationId}/wait`,{timeoutMs:15000});assert.equal(result.state,'completed',JSON.stringify(result));
    const opened=await screen(value=>value.screenClass.endsWith('ContainerScreen'));assert(nodes(opened).some(node=>node.role==='slot'));
    observations.push({worldPart:part,result,actualNativeScreen:opened.screenClass,act:'GAME_ROUTED',directBusinessCall:false});
    await key(256);await screen(value=>!value.screenClass);
    const stale=(await api('GET','/v0/world/block?x=0&y=80&z=2&category=USER')).extensionParts[0];
    await mode('OPERATE');await api('POST','/v0/command/player',{command:'setblock 0 80 2 minecraft:stone',route:'NATIVE'});
    await until(async()=>(await api('GET','/v0/world/block?x=0&y=80&z=2&category=USER')).block==='minecraft:stone','Replacement did not synchronize',10000);
    await acquire();const before=await api('GET','/v0/input/state');
    const rejected=await api('POST','/v0/pipelines',{steps:[{type:'world.action',targetRef:stale.targetRef,action:'use'}],timeoutMs:12000});
    const stopped=await api('POST',`/v0/operations/${rejected.operationId}/wait`,{timeoutMs:15000});assert.equal(stopped.state,'failed',JSON.stringify(stopped));
    assert.match(JSON.stringify(stopped),/WORLD_TARGET_STALE|WORLD_PART_STALE/);
    const after=await api('GET','/v0/input/state');assert.equal(after.inputDispatchSequence,before.inputDispatchSequence+1,'Only the mandatory release-all bookkeeping counter may advance after rejected input');assert.equal(after.pressedKeyCount,0);assert.equal(after.pressedButtonCount,0);
    observations.push({stalePartRejected:stopped,cleanupCounterAdvanced:1,noHeldInput:true});await api('POST','/v0/control/release');lease=undefined;
  });
  if(args['lifetime-faults']==='true'){
    assert(!server);
    await lifecycleFaultActions({api,mode,acquire,key,screen,until,check,observations});
    await shutdown(true);
    await check('leaving the actual world retires package WORLD and CONNECTION scopes without a client disconnect',async()=>{
      const value=await until(async()=>{const item=await api('GET','/v0/extensions/resources');const rows=item.cleanup.filter(row=>['lifetime_left','lifetime_right'].includes(row.packageId)&&['WORLD','CONNECTION'].includes(row.lifetime));return rows.length>=4&&rows.every(row=>row.complete)?item:false;},'World contexts did not retire',6000);
      assert(value.cleanup.filter(row=>['lifetime_left','lifetime_right'].includes(row.packageId)&&['WORLD','CONNECTION'].includes(row.lifetime)).every(row=>row.released),JSON.stringify(value));observations.push({nativeWorldExitResources:value});
    });
  }
  if(args.adapter&&args.adapter!=='platform_probe'){
    if(server)await serverAdapterActions({adapter:args.adapter,api,observations,check,until:async(read,valid,label)=>until(async()=>{const value=await read();return valid(value)?value:false;},label)});
    else await (['ae2','create','exposure','littletiles'].includes(args.adapter)?adapterWorldActions:adapterUiActions)({adapter:args.adapter,api,tree,screen,nodes,mode,acquire,key,check,observations,until});
  }
  if(!server){const input=await api('GET','/v0/input/state');assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);}
  assert.deepEqual(await candidateSource(repo,[target]),before,'Source changed while running');
  assert.deepEqual(await extensionPackageSource(repo,packageIds),packageSourceFiles,'Adapter sources changed while running');
  assert.deepEqual(await extensionInputArtifacts(instance),upstreamArtifacts,'Upstream input JARs changed while running');
  record.sourceFiles=before;record.status='PASS';record.debugWriteRequests=writeCalls;
} catch(error) {record.status='FAIL';record.error=String(error);throw error;}
finally {
  let cleanupFailure;
  try {
    if(args['lifetime-faults']==='true'&&!(await tree()).screenClass.endsWith('TitleScreen')){
      await restoreLifecycleFaults({api,mode});record.faultFixturesRestored=true;
    }
    await api('POST','/v0/debug/disarm');arm=undefined;
    const expected=observations.filter(item=>item.case==='independent_grant_failure').flatMap(item=>item.reports.filter(row=>row.packageId===item.failed));
    record.expectedCleanupFailures=expected;
    const cleanup=await until(async()=>{const value=await api('GET','/v0/extensions/resources');return value.cleanup.filter(item=>item.lifetime==='DEBUG_GRANT').every(item=>item.complete&&(item.released||expected.some(known=>known.packageId===item.packageId&&known.contextId===item.contextId&&JSON.stringify(known.resources)===JSON.stringify(item.resources))))?value:false;},'Debug grant cleanup did not finish before shutdown',6000);
    record.debugCleanupBeforeShutdown=cleanup;
  } catch(error){record.status='FAIL';record.cleanupError=String(error);cleanupFailure=error;}
  try {
    if(args['host-shutdown']==='true'&&!server){
      await shutdown(true);
    }else await shutdown();
  } catch(error){record.status='FAIL';record.shutdownError=String(error);cleanupFailure??=error;}
  finally {
    const result=resolve(repo,'validations/integration-extension_platform/result');await mkdir(result,{recursive:true});
    record.completedAt=new Date().toISOString();await writeFile(resolve(result,`${target}-${installed?'installed-':''}${role}${args.adapter&&args.adapter!=='platform_probe'?'-'+args.adapter:''}${args['lifetime-faults']==='true'?'-lifetimes':''}.json`),JSON.stringify(record,null,2)+'\n');
  }
  if(cleanupFailure)throw cleanupFailure;
}
