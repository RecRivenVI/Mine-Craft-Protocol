import assert from 'node:assert/strict';
import {readFile,readdir,writeFile,mkdir} from 'node:fs/promises';
import {resolve,relative} from 'node:path';
import {createHash,randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import {connectRuntime} from '../../runtime-connection.mjs';

const repo=resolve(import.meta.dirname,'../../../..');
const args=Object.fromEntries(process.argv.slice(2).map(arg=>arg.replace(/^--/,'').split('=')));
const target=args.target,validation='conformance-world_read';
assert(['26.1.2-neoforge','26.3-neoforge','26.3-fabric'].includes(target));
const instance=resolve(repo,'validations',validation,'instance',target,'client');
const fixtureInput=JSON.parse(await readFile(resolve(instance,'fixture-input.json'),'utf8'));
assert.equal(fixtureInput.policy,'immutable_baseline_restored');assert.equal(fixtureInput.target,target);
assert.equal(fixtureInput.baselineSha256,args['fixture-hash']);assert.equal(fixtureInput.restoredSha256,args['fixture-hash']);
const connection=await connectRuntime(instance,target),checks=[],actionSamples=[];
let lease,debugArm,failure,shutdownResponse='not_requested',lastDebugRequest=0;
async function debugBudget(){await new Promise(r=>setTimeout(r,Math.max(0,550-(Date.now()-lastDebugRequest))));lastDebugRequest=Date.now();}
async function sources(){
 const files=[];
 for(const root of [`versions/${target}/src`,'components/runtime_core/src/main','components/runtime_safety/src/main','components/companion/src'])
  for(const entry of await readdir(resolve(repo,root),{recursive:true,withFileTypes:true}))if(entry.isFile())files.push(relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/'));
 for(const entry of await readdir(import.meta.dirname,{withFileTypes:true}))if(entry.isFile()&&/\.(mjs|ps1)$/.test(entry.name))files.push(relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/'));
 files.push('components/protocol_schema/src/main/openapi/minecraft-control-v0.json','components/validation_tools/InstanceProfiles.ps1','components/validation_tools/Prepare-InstanceProfile.ps1','components/validation_tools/instance-profiles.gradle','build.gradle.kts','components/validation_tools/RuntimeConnection.ps1','components/validation_tools/runtime-connection.mjs',`validations/${validation}/validation.toml`,'gradle.properties','settings.gradle.kts','gradle/libs.versions.toml',`versions/${target}/build.gradle.kts`,`versions/${target}/target.properties`);
 const hashes={};for(const file of [...new Set(files)].sort())hashes[file]=createHash('sha256').update(await readFile(resolve(repo,file))).digest('hex');return hashes;
}
const before=await sources();
async function request(method,path,body){
 if(path.startsWith('/v0/debug/'))await debugBudget();
 const response=await connection.fetch(path,{method,headers:{...(lease?{'x-mcp-control-lease':lease}:{}),...(debugArm?{'x-mcp-debug-arm':debugArm}:{}),...(body?{'content-type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(30000)});
 return {ok:response.ok,status:response.status,value:await response.json()};
}
async function api(method,path,body){const out=await request(method,path,body);assert(out.ok,JSON.stringify(out));return out.value;}
const nodes=t=>(t.children??[]).flatMap(n=>[n,...nodes(n)]),pause=()=>new Promise(r=>setTimeout(r,50));
async function poll(read,accept,label,budget=10000){const end=Date.now()+budget;let value;do{value=await read();if(accept(value))return value;await pause();}while(Date.now()<end);throw new Error(label+': '+JSON.stringify(value));}
async function screen(accept,label,budget){return poll(()=>api('GET','/v0/ui/tree'),t=>!t.overlayIdentity&&accept(t),label,budget);}
async function click(key){const tree=await api('GET','/v0/ui/tree'),found=nodes(tree).filter(n=>n.translationKey===key&&n.visible!==false&&n.active!==false);assert.equal(found.length,1,key+': '+JSON.stringify(tree));return api('POST','/v0/ui/action',{selector:{nodeId:found[0].nodeId},action:'click'});}
async function acquire(){assert.equal((await api('GET','/v0/control/mode')).reconsentRequired,false,'Never clear human override');lease=(await api('POST','/v0/control/acquire',{ttlMs:60000})).leaseId;}
async function clean(){const state=await api('GET','/v0/input/state');assert.equal(state.pressedKeyCount,0);assert.equal(state.pressedButtonCount,0);return state;}
async function operation(steps){const op=await api('POST','/v0/pipelines',{steps,timeoutMs:10000});return api('POST',`/v0/operations/${op.operationId}/wait`,{timeoutMs:12000});}
async function key(code){const out=await operation([{type:'key.tap',key:code,holdMs:40}]);assert.equal(out.state,'completed',JSON.stringify(out));}
async function command(text){await api('POST','/v0/control/renew',{ttlMs:60000});const out=await api('POST','/v0/command/player',{command:text});assert.equal(out.mechanism,'GAME_ROUTED_RAW');return out;}
async function barrier(){
 const attempt=randomUUID().replaceAll('-','');await command('say Mine-Craft-Protocol_command_configuration_'+attempt);
 return poll(async()=>{try{return JSON.parse(await readFile(resolve(repo,'validations',validation,'result',target+'-command-configuration-probe.json'),'utf8'));}catch(error){if(error.code==='ENOENT'||error instanceof SyntaxError)return {};throw error;}},r=>r.attemptId===attempt,'native server FIFO barrier');
}
function unchanged(initial,after,stored){
 for(const field of ['testBlockPackets','testInstanceActionPackets',...stored])assert.equal(after[field],initial[field],field+' changed');
 assert.equal(after.testBlockPackets,0);assert.equal(after.testInstanceActionPackets,0);
 assert.equal(after.worldMutationUsedByProbe,false);assert.equal(after.chunkLoadRequested,false);
}
async function arrange(block){
 await screen(t=>!t.screenClass,'gameplay before fixture');await command('setblock 2 80 0 minecraft:air');await command('setblock 2 80 0 '+block);
 await poll(()=>api('GET','/v0/world/block?x=2&y=80&z=0'),b=>b.block===block.split('[')[0],'fixture client sync');
}
async function open(className){
 const block=await api('GET','/v0/world/block?x=2&y=80&z=0');
 const result=await operation([{type:'world.action',action:'use',targetRef:block.targetRef}]);
 // Native Test-instance initialization may attempt INIT while opening the Screen.
 // Rejection is acceptable only when the real Screen and zero server packets are observed.
 assert(['completed','failed'].includes(result.state));
 if(result.state==='failed')assert.equal(result.errorCode,'PLAYER_COMMAND_OUT_OF_SCOPE',JSON.stringify(result));
 actionSamples.push({case:'open_'+className,result});return screen(t=>t.screenClass.endsWith(className),'native Test editor');
}
async function deniedClick(keyName){const t=await api('GET','/v0/ui/tree'),n=nodes(t).filter(n=>n.translationKey===keyName&&n.active!==false&&n.visible!==false);assert.equal(n.length,1,keyName+': '+JSON.stringify(t));const result=await operation([{type:'ui.action',selector:{nodeId:n[0].nodeId},action:'click'}]);assert.equal(result.state,'failed',JSON.stringify(result));assert.equal(result.errorCode,'PLAYER_COMMAND_OUT_OF_SCOPE');assert.equal(result.result?.commandSubmissions?.length??0,0);return result;}
async function check(name,run){await run();checks.push({name,status:'PASS'});console.log('PASS: '+name);}
async function mcpTextDenied(){
 const require=createRequire(resolve(repo,'components/companion/package.json'));
 const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
 const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
 const env=Object.fromEntries(Object.entries(process.env).filter(([,value])=>typeof value==='string'));
 for(const key of ['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE'])delete env[key];
 env.MCP_MINECRAFT_INSTANCE_DIR=instance;env.MCP_MINECRAFT_TARGET=target;
 const client=new Client({name:'test-management-consumer',version:'0.0.1'});
 const transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
 const tool=async(name,args)=>{if(name.startsWith('minecraft_debug'))await debugBudget();const result=await client.callTool({name,arguments:args});assert(!result.isError,JSON.stringify(result));return result.structuredContent.data;};
 try{
  await client.connect(transport);const t=await tool('minecraft_get_ui',{category:'DEBUG'});
  const n=nodes(t).find(n=>n.role==='text_field'&&n.translationKey==='test_instance_block.test_id');assert(n?.resourceVersion);
  await tool('minecraft_debug_arm',{action:'arm',uiContextId:t.uiContextId,namespaces:['ui']});
  await debugBudget();
  const result=await client.callTool({name:'minecraft_debug',arguments:{action:'mutate',mutation:{operation:'ui.text.set',nodeId:n.nodeId,expectedResourceVersion:n.resourceVersion,expectedValue:n.value,value:'minecraft:must_not_query'}}});
  assert.equal(result.isError,true,JSON.stringify(result));assert.equal(result.structuredContent.error.code,'UI_DEBUG_UNSUPPORTED');
  const after=nodes(await tool('minecraft_get_ui',{category:'DEBUG'})).find(x=>x.nodeId===n.nodeId);assert(after);assert.equal(after.value,n.value);assert.deepEqual(after.resourceVersion,n.resourceVersion);
  const control=await tool('minecraft_control',{action:'status'});assert.equal(control.mode,'OPERATE');assert.equal(control.takeoverActive,false);
  actionSamples.push({case:'mcp_debug_responder_refusal',error:result.structuredContent.error,before:n,after});
 }finally{try{await tool('minecraft_debug_arm',{action:'disarm'});}finally{try{await client.close();}finally{await transport.close();}}}
}
try{
 await acquire();
 await check('semantic GUI enters the immutable diagnostic fixture',async()=>{
  await click('menu.singleplayer');const t=await screen(t=>t.screenClass.endsWith('SelectWorldScreen'),'world list');const world=nodes(t).filter(n=>n.worldName==='World-Read-Alpha');assert.equal(world.length,1);await api('POST','/v0/ui/action',{selector:{nodeId:world[0].nodeId},action:'click'});await click('selectWorld.select');await screen(t=>!t.screenClass,'fixed world',150000);
  await command('gamemode creative @s');await command('clear @s');await command('fill -1 79 -1 4 79 2 minecraft:stone');await command('fill -1 80 -1 4 83 2 minecraft:air');await command('tp @s 0.5 80 0.5');await clean();
 });
 await check('Test-block Done is refused before any server configuration or stored-message change',async()=>{
  await arrange('minecraft:test_block[mode=log]');const initial=await barrier();assert.equal(initial.storedTestMode,'LOG');assert(initial.storedTestMessageHash);
  await open('TestBlockEditScreen');const t=await api('GET','/v0/ui/tree'),n=nodes(t).filter(n=>n.role==='text_field'&&n.active!==false&&n.visible!==false);assert.equal(n.length,1,JSON.stringify(t));
  await api('POST','/v0/ui/action',{selector:{nodeId:n[0].nodeId},action:'type_text',text:'must_not_reach_server'});
  const result=await deniedClick('gui.done');await screen(t=>!t.screenClass,'denied Done closes native editor');const after=await barrier();unchanged(initial,after,['storedTestMode','storedTestMessageHash']);await clean();actionSamples.push({case:'test_block_denied',initial,after,result});
 });
 await check('Test-instance QUERY responder and RESET are refused before the server sees a management packet',async()=>{
  await arrange('minecraft:test_instance_block');const initial=await barrier();assert(initial.storedTestInstanceHash);
  let t=await open('TestInstanceBlockEditScreen');const n=nodes(t).find(n=>n.role==='text_field'&&n.translationKey==='test_instance_block.test_id'&&n.active!==false&&n.visible!==false);assert(n,JSON.stringify(t));
  const result=await operation([{type:'ui.action',selector:{nodeId:n.nodeId},action:'type_text',text:'minecraft:must_not_query'}]);assert.equal(result.state,'failed',JSON.stringify(result));assert.equal(result.errorCode,'PLAYER_COMMAND_OUT_OF_SCOPE');assert.equal(result.result?.commandSubmissions?.length??0,0);
  // A native responder can change a client draft before its packet is refused; no rollback is claimed.
  actionSamples.push({case:'test_instance_query_denied',result});await key(256);await screen(t=>!t.screenClass,'close query draft');let after=await barrier();unchanged(initial,after,['storedTestInstanceHash']);
  await open('TestInstanceBlockEditScreen');const reset=await deniedClick('test_instance.action.reset');await screen(t=>!t.screenClass,'native Reset closes its editor even when its packet is refused');after=await barrier();unchanged(initial,after,['storedTestInstanceHash']);actionSamples.push({case:'test_instance_reset_denied',initial,after,result:reset});await clean();
 });
 await check('OPERATE Native text setter refuses the Test-instance responder before client mutation without a Lease',async()=>{
  await open('TestInstanceBlockEditScreen');const mode=await api('GET','/v0/control/mode');await api('POST','/v0/control/mode',{mode:'OPERATE',expectedModeVersion:mode.modeVersion});lease=undefined;
  const start=await clean(),t=await api('GET','/v0/ui/tree?category=DEBUG'),n=nodes(t).find(n=>n.role==='text_field'&&n.translationKey==='test_instance_block.test_id');assert(n?.resourceVersion);
  debugArm=(await api('POST','/v0/debug/arm',{uiContextId:t.uiContextId,namespaces:['ui'],ttlMs:60000})).debugArmId;
  const denied=await request('POST','/v0/debug/mutations',{operation:'ui.text.set',nodeId:n.nodeId,expectedResourceVersion:n.resourceVersion,expectedValue:n.value,value:'minecraft:must_not_query'});
  actionSamples.push({case:'native_debug_refusal_response',denied});assert.equal(denied.ok,false,JSON.stringify(denied));assert.equal(denied.value.error,'UI_DEBUG_UNSUPPORTED',JSON.stringify(denied));
  const after=nodes(await api('GET','/v0/ui/tree?category=DEBUG')).find(x=>x.nodeId===n.nodeId);assert(after);assert.equal(after.value,n.value);assert.deepEqual(after.resourceVersion,n.resourceVersion);
  await api('POST','/v0/debug/disarm');debugArm=undefined;assert.equal((await clean()).inputDispatchSequence,start.inputDispatchSequence);actionSamples.push({case:'native_debug_responder_refusal',before:n,after,error:denied.value});
 });
 await check('real MCP preserves the same typed refusal and unchanged resource revision without acquiring TAKEOVER',async()=>{
  const start=await clean();await mcpTextDenied();assert.equal((await clean()).inputDispatchSequence,start.inputDispatchSequence);
  await acquire();await key(256);await screen(t=>!t.screenClass,'close Debug editor');const after=await barrier();assert.equal(after.testBlockPackets,0);assert.equal(after.testInstanceActionPackets,0);actionSamples.push({case:'final_server_barrier',after});await clean();
 });
 await command('setblock 2 80 0 minecraft:air');await clean();
}catch(error){failure=error;console.error(error);}
finally{
 try{
  if(debugArm){await api('POST','/v0/debug/disarm');debugArm=undefined;}
  if(!lease)await acquire();
  for(let i=0;i<10;i++){const t=await api('GET','/v0/ui/tree');if(t.screenClass.endsWith('TitleScreen')){shutdownResponse='requested';try{await click('menu.quit');shutdownResponse='acknowledged';}catch{shutdownResponse='transport_closed_requires_runner_attestation';}break;}const quit=nodes(t).find(n=>['menu.returnToMenu','menu.disconnect'].includes(n.translationKey));if(quit){await api('POST','/v0/ui/action',{selector:{nodeId:quit.nodeId},action:'click'});await screen(t=>t.screenClass.endsWith('TitleScreen'),'Save and Quit',60000);}else await key(256);}
  assert.notEqual(shutdownResponse,'not_requested');
 }catch(error){failure??=error;console.error(error);}
 const after=await sources(),sourceChangedDuringRun=JSON.stringify(before)!==JSON.stringify(after);if(sourceChangedDuringRun)failure??=new Error('Source changed during Runtime test');
 const directory=resolve(repo,'validations',validation,'result');await mkdir(directory,{recursive:true});
 await writeFile(resolve(directory,target+'-test-management.json'),JSON.stringify({status:failure?'FAIL':'PASS',target,scenario:'test-management',fullSmoke:false,fullWorldMilestone:false,testedAt:new Date().toISOString(),testedCommit:execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).trim(),dirty:true,evidence:'development_runtime_native_and_mcp',runtimeConnection:{runtimeInstanceId:connection.instanceId,port:connection.port,pid:connection.pid},fixturePolicy:fixtureInput.policy,fixtureInput,checks,actionSamples,shutdownResponse,sourceHashes:after,sourceChangedDuringRun,persistentWriteCalls:0,notExecuted:['test_management_execution','full_world_smoke','packaged_artifact_runtime','human_acceptance'],...(failure?{error:String(failure)}:{})},null,2)+'\n');
}
if(failure)process.exitCode=1;
