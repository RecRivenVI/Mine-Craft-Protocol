import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir,readdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import {createHash} from 'node:crypto';
import {connectRuntime,automaticTakeoverRequest} from '../../runtime-connection.mjs';
import {candidateSource} from '../../regression-v1_release/task/alpha-candidate.mjs';
const repo=resolve(import.meta.dirname,'../../../..');
const args=Object.fromEntries(process.argv.slice(2).map(arg=>arg.replace(/^--/,'').split('=')));
const target=args.target,instance=resolve(repo,`validations/integration-extension_platform/instance/${target}/client`);
const connection=await connectRuntime(instance,target);
let lease,arm,writeCalls=0;
const checks=[],observations=[];
const pause=ms=>new Promise(done=>setTimeout(done,ms));
async function api(method,path,body,error){
  assert(!/storage[./]write|persistent.*write/i.test(path),'Persistent Write forbidden');
  const response=await connection.fetch(path,{method,headers:{...(lease?{'x-mcp-control-lease':lease}:{}),...(arm?{'x-mcp-debug-arm':arm}:{}),...(body?{'content-type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(20000)});
  const value=await response.json();
  if(error){assert.equal(value.error,error,JSON.stringify(value));assert(!response.ok);return value;}
  if(!response.ok)throw Object.assign(new Error(JSON.stringify(value)),{code:value.error,status:response.status});return value;
}
const nodes=tree=>(tree.children??[]).flatMap(node=>[node,...nodes(node)]);
async function until(test,description,timeout=120000){const end=Date.now()+timeout;do{const value=await test();if(value)return value;await pause(150);}while(Date.now()<end);throw new Error(description);}
const tree=()=>api('GET','/v0/ui/tree');
const screen=async predicate=>until(async()=>{const value=await tree();return !value.overlayIdentity&&predicate(value)?value:false;},'Screen transition timeout');
async function acquire(){lease=(await api('POST','/v0/control/acquire',automaticTakeoverRequest(await api('GET','/v0/control/mode'),{ttlMs:60000}))).leaseId;}
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
async function shutdown(){
  for(let i=0;i<8;i++){
    const state=await api('GET','/v0/control/mode');if(state.mode!=='TAKEOVER')await acquire();
    const value=await screen(()=>true);
    if(value.screenClass.endsWith('TitleScreen')){
      try{await click('menu.quit','Quit Game');}catch(error){if(error.code!=='RUNTIME_SHUTTING_DOWN'&&!(error instanceof TypeError))throw error;console.log('Quit response not confirmed; verifying normal process exit:',String(error));}
      return;
    }
    const exit=nodes(value).find(node=>['menu.disconnect','menu.returnToMenu'].includes(node.translationKey)&&node.active!==false);
    if(exit){try{await clickNode(exit);}catch(error){observations.push({shutdownRequestUnconfirmed:String(error)});}await screen(t=>t.screenClass.endsWith('TitleScreen'));if((await api('GET','/v0/control/mode')).mode!=='TAKEOVER')lease=undefined;}
    else await key(256);
  }
  throw new Error('Could not navigate normal shutdown');
}
if(args['shutdown-only']==='true'){await shutdown();process.exit(0);}
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const before=await candidateSource(repo,[target]);
const toolFiles={};
for(const file of ['Invoke-ExtensionPlatformSmoke.ps1','extension-platform-smoke.mjs','install-platform-probe.mjs']) {
  const path='components/validation_tools/integration-extension_platform/task/'+file;
  toolFiles[path]=hash(await readFile(resolve(repo,path)));
}
const record={target,runtimeInstanceId:connection.instanceId,developmentClasspath:true,finalJarInstall:false,toolFiles,checks,observations,persistentWriteCalls:0};
const check=async(name,run)=>{await run();checks.push({name,status:'PASS'});console.log('PASS: '+name);};
const request={category:'DEBUG',perspective:'server_authoritative',domains:['providers'],includeProviderData:true,providerIds:['platform_probe:sampling'],providerQuery:{},budgets:{maxProviders:1,providerTimeoutMs:1000}};
async function read(){const value=await api('POST','/v0/observe/deep',request);assert.equal(value.providers[0].status,'completed',JSON.stringify(value.providers));return value;}
const data=value=>value.providers[0].data;
const version=value=>{const found=value.resourceRevisionRefs.find(ref=>ref.resourceType==='provider'&&ref.resourceKey==='platform_probe:sampling');assert(found?.mutationPreconditionEligible);return found;};
let fingerprint;
const mutation=(observed,action)=>({operation:'provider.mutate',providerId:'platform_probe:sampling',worldFingerprint:fingerprint,expectedResourceVersion:version(observed),mutation:{action}});
const startArm=async ttlMs=>{arm=(await api('POST','/v0/debug/arm',{worldFingerprint:fingerprint,namespaces:['provider'],ttlMs})).debugArmId;};
async function write(body,error){const state=await api('GET','/v0/input/state');writeCalls++;const value=await api('POST','/v0/debug/mutations',body,error);const after=await api('GET','/v0/input/state');assert.equal(after.inputDispatchSequence,state.inputDispatchSequence);assert.equal(after.pressedKeyCount,0);assert.equal(after.pressedButtonCount,0);return value;}
try {
  await screen(value=>value.screenClass.endsWith('TitleScreen'));
  await check('approved independent SDK package loads and static catalog/documents work at title',async()=>{
    const catalog=await api('GET','/v0/extensions');const installed=catalog.packages.find(item=>item.packageId==='platform_probe');assert.equal(installed?.status,'active',JSON.stringify(catalog));assert(installed.registeredCapabilities.includes('platform_probe:sampling'));
    const bytes=await readFile(resolve(instance,'minecraft-protocol/extensions/packages/platform_probe/extension.json'));assert.equal(installed.contentIdentity,hash(bytes));record.packageIdentity=installed.contentIdentity;
    const document=await api('GET','/v0/extensions/document?packageId=platform_probe&documentId=overview');assert.equal(hash(Buffer.from(document.text)),document.sha256);assert.equal(document.contentIsData,true);observations.push({catalog,document});
  });
  await acquire();await click('menu.singleplayer','Singleplayer');
  let value=await screen(t=>/SelectWorldScreen$|CreateWorldScreen$/.test(t.screenClass));
  if(value.screenClass.endsWith('SelectWorldScreen')){
    const world=nodes(value).find(node=>node.worldName==='Extension-Platform');
    if(world){await clickNode(world);await click('selectWorld.select','Play Selected World');}
    else await click('selectWorld.create','Create New World');
  }
  value=await screen(t=>!t.screenClass||t.screenClass.endsWith('CreateWorldScreen'));
  if(value.screenClass){
    await api('POST','/v0/ui/action',{selector:{role:'text_field',nth:0},action:'type_text',text:'Extension-Platform'});
    for(let i=0;i<4;i++){value=await tree();const button=nodes(value).find(node=>node.label?.startsWith('Game Mode:'));assert(button);if(button.label.includes('Creative'))break;await clickNode(button);}
    const worldTab=nodes(await tree()).find(node=>node.role==='tab'&&node.label==='World');if(worldTab)await clickNode(worldTab);
    for(let i=0;i<12;i++){value=await tree();const type=nodes(value).find(node=>node.label?.startsWith('World Type:'));if(!type||/Superflat|Flat/.test(type.label))break;await clickNode(type);}
    const gameTab=nodes(await tree()).find(node=>node.role==='tab'&&node.label==='Game');if(gameTab)await clickNode(gameTab);
    await click('selectWorld.create','Create New World');
  }
  await screen(t=>!t.screenClass);await api('POST','/v0/control/release');lease=undefined;
  fingerprint=(await api('GET','/v0/world/fingerprint?category=DEBUG')).worldFingerprint;
  await check('registered Provider uses real server owner and DEBUG writes still require Arm and mode',async()=>{
    const observed=await read();assert.equal(data(observed).running,false);assert.equal(data(observed).samples,0);const body=mutation(observed,'start');
    await mode('OPERATE');await write(body,'DEBUG_NOT_ARMED');await startArm(60000);await mode('READ');await write(body,'OPERATE_REQUIRED');await mode('OPERATE');
    const started=await write(body);assert.equal(started.gameplayEvidence,false);assert.equal(started.evidenceContaminated,true);
    const sampled=await until(async()=>{const result=await read();return data(result).samples>0?result:false;},'Owner sample did not arrive',6000);assert.match(data(sampled).ownerThread,/Server thread/);assert(data(sampled).sampledAtNanos>0);observations.push({started,sampled});
  });
  await check('input Lease lifetime remains independent of the Debug sampling grant',async()=>{
    await acquire();await api('POST','/v0/control/release');lease=undefined;const result=await read();assert.equal(data(result).running,true);assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
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
  const input=await api('GET','/v0/input/state');assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);
  assert.deepEqual(await candidateSource(repo,[target]),before,'Source changed while running');
  record.sourceFiles=before;record.status='PASS';record.debugWriteRequests=writeCalls;
} catch(error) {record.status='FAIL';record.error=String(error);throw error;}
finally {
  const result=resolve(repo,'validations/integration-extension_platform/result');await mkdir(result,{recursive:true});
  record.completedAt=new Date().toISOString();await writeFile(resolve(result,`${target}-client.json`),JSON.stringify(record,null,2)+'\n');
  await shutdown().catch(error=>{console.error('Owned shutdown request failed',error);});
}
