import assert from 'node:assert/strict';
import {readFile,readdir,writeFile,mkdir} from 'node:fs/promises';
import {resolve,relative} from 'node:path';
import {createHash} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {connectRuntime} from '../../runtime-connection.mjs';
import {inputContractConsumers} from './world-read-consumers.mjs';

const repo=resolve(import.meta.dirname,'../../../..');
const args=Object.fromEntries(process.argv.slice(2).map(arg=>arg.replace(/^--/,'').split('=')));
const target=args.target,validation=args.validation??'conformance-world_read';
assert(['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric'].includes(target));
assert.equal(validation,'conformance-world_read');
const instance=resolve(repo,'validations',validation,'instance',target,'client');
const fixtureInput=JSON.parse(await readFile(resolve(instance,'fixture-input.json'),'utf8'));
assert.equal(fixtureInput.policy,'immutable_baseline_restored');assert.equal(fixtureInput.target,target);
assert.equal(fixtureInput.role,'client');assert.equal(fixtureInput.credentialsCopied,false);assert.match(fixtureInput.baselineSha256,/^[0-9a-f]{64}$/);
assert.equal(fixtureInput.baselineSha256,args['fixture-hash']);assert.equal(fixtureInput.restoredSha256,args['fixture-hash']);
const connection=await connectRuntime(instance,target);
const checks=[],actionSamples=[],consumerChecks=[];
let lease,failure,shutdownResponse='not_requested';
async function sources(){
  const files=[];
  for(const root of [`versions/${target}/src`,'components/runtime_core/src/main','components/runtime_safety/src/main','components/companion/src'])
    for(const entry of await readdir(resolve(repo,root),{recursive:true,withFileTypes:true}))if(entry.isFile()&&/\.(java|json|ts)$/.test(entry.name))files.push(relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/'));
  for(const entry of await readdir(import.meta.dirname,{withFileTypes:true}))if(entry.isFile()&&/\.(mjs|ps1)$/.test(entry.name))files.push(relative(repo,resolve(import.meta.dirname,entry.name)).replaceAll('\\','/'));
  files.push('components/protocol_schema/src/main/openapi/minecraft-control-v0.json','components/validation_tools/InstanceProfiles.ps1','components/validation_tools/Prepare-InstanceProfile.ps1','components/validation_tools/instance-profiles.gradle','build.gradle.kts','components/validation_tools/RuntimeConnection.ps1','components/validation_tools/runtime-connection.mjs',`validations/${validation}/validation.toml`,'gradle.properties','settings.gradle.kts','gradle/libs.versions.toml',`versions/${target}/build.gradle.kts`,`versions/${target}/target.properties`);
  const hashes={};for(const file of [...new Set(files)].sort())hashes[file]=createHash('sha256').update(await readFile(resolve(repo,file))).digest('hex');return hashes;
}
const before=await sources();
async function api(method,path,body,headers={},expected){
  const response=await connection.fetch(path,{method,headers:{...(lease?{'x-mcp-control-lease':lease}:{}),...(body?{'content-type':'application/json'}:{}),...headers},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(15000)});
  const value=await response.json();
  if(expected){assert(!response.ok,JSON.stringify(value));assert.equal(value.error,expected,JSON.stringify(value));}
  else if(!response.ok)throw Object.assign(new Error(JSON.stringify(value)),{code:value.error});
  return value;
}
const nodes=t=>(t.children??[]).flatMap(n=>[n,...nodes(n)]);
const delay=ms=>new Promise(done=>setTimeout(done,ms));
async function treeAt(name){
  const end=Date.now()+6000;
  do{const tree=await api('GET','/v0/ui/tree');if(tree.screenClass.endsWith(name)&&!tree.overlayIdentity)return tree;await delay(60);}while(Date.now()<end);
  throw new Error('Native Screen did not reach '+name);
}
function node(tree,key){const found=nodes(tree).filter(n=>n.translationKey===key&&n.active!==false&&n.visible!==false);assert.equal(found.length,1,key);return found[0];}
async function click(tree,key){return api('POST','/v0/ui/action',{selector:{nodeId:node(tree,key).nodeId},action:'click'});}
async function clean(){const state=await api('GET','/v0/input/state');assert.equal(state.pressedKeyCount,0);assert.equal(state.pressedButtonCount,0);return state;}
async function check(name,work){await work();checks.push({name,status:'PASS'});console.log('PASS: '+name);}
try{
  await check('READ rejects player input before the targeted queue tests',async()=>{
    assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
    const descriptor=(await api('GET','/v0/operations')).operations.find(value=>value.id==='pipeline.execute');
    assert(descriptor);for(const field of ['screenRevision','menuRevision'])assert(descriptor.supportedPreconditions.includes(field));
    const schema=JSON.parse(await readFile(resolve(repo,'components/protocol_schema/src/main/openapi/minecraft-control-v0.json'),'utf8'));
    assert.deepEqual(Object.keys(descriptor.additionalScopesByStep).sort(),schema.components.schemas.PipelineStep.properties.type.enum.toSorted());
    for(const kind of ['ui.action','ui.drag'])assert.deepEqual(descriptor.additionalScopesByStep[kind],['ui']);
    for(const kind of ['wait.until','assert.that'])assert.deepEqual(descriptor.additionalScopesByStep[kind],['read']);
    await api('POST','/v0/input/key',{key:87,action:1},{},'TAKEOVER_REQUIRED');await clean();
  });
  await check('READ and OPERATE UI conditions use their authorized native projection without a player Lease',async()=>{
    for(const category of ['USER','DEBUG']){
      const result=await api('POST','/v0/assert',{condition:{type:'screen',category,classContains:'TitleScreen'}});
      assert.equal(result.domain,'UI');assert.equal(result.dataCategory,category);assert.equal(result.evidence.dataCategory,category);
      assert.equal(Boolean(result.evidence.debugFacts),category==='DEBUG');
    }
    let current=await api('GET','/v0/control/mode');await api('POST','/v0/control/mode',{mode:'OPERATE',expectedModeVersion:current.modeVersion});
    assert.equal((await api('POST','/v0/assert',{condition:{type:'screen',category:'USER'}})).dataCategory,'USER');
    assert.equal((await api('POST','/v0/assert',{condition:{type:'screen',category:'DEBUG',classContains:'TitleScreen'}})).dataCategory,'DEBUG');
    current=await api('GET','/v0/control/mode');assert.equal(current.mode,'OPERATE');assert.equal(current.takeoverActive,false);
    await api('POST','/v0/control/mode',{mode:'READ',expectedModeVersion:current.modeVersion});await clean();
  });
  const control=await api('GET','/v0/control/mode');assert.equal(control.reconsentRequired,false,'No automatic reconsent');
  lease=(await api('POST','/v0/control/acquire',{ttlMs:60000})).leaseId;
  await check('TAKEOVER condition plans retain independently granted DEBUG reads without input effects',async()=>{
    const before=await clean();
    for(const condition of [{type:'screen',category:'DEBUG'},{type:'player',category:'DEBUG',expected:{available:false}}]){
      const operation=await api('POST','/v0/pipelines',{steps:[{type:'assert.that',condition}]});
      const outcome=await api('POST',`/v0/operations/${operation.operationId}/wait`,{timeoutMs:5000});assert.equal(outcome.state,'completed',JSON.stringify(outcome));
    }
    consumerChecks.push(...await inputContractConsumers(repo,instance,target,lease));
    const after=await clean();assert.equal(after.inputDispatchSequence,before.inputDispatchSequence);
    assert.equal((await api('GET','/v0/control/mode')).mode,'TAKEOVER');
  });
  for(const kind of ['semantic_ui','raw_key','pipeline'])await check(kind+' rejects the initial Screen revision after its queue turn changes',async()=>{
    const title=await treeAt('TitleScreen');
    const headers={'X-MCP-Expected-Screen-Revision':String(title.screenRevision),'X-MCP-Expected-Menu-Revision':String(title.menuRevision)};
    const first=await api('POST','/v0/pipelines',{steps:[{type:'delay',durationMs:1200},{type:'ui.action',selector:{nodeId:node(title,'menu.options').nodeId},action:'click'}],timeoutMs:6000,cleanupOnComplete:false});
    let pending,outcome,settled=false,error;
    if(kind==='pipeline'){
      const accepted=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:87,holdMs:40}],timeoutMs:6000},headers);
      pending=api('POST','/v0/operations/'+accepted.operationId+'/wait',{timeoutMs:6000}).then(value=>{settled=true;return value;},reason=>{settled=true;error=reason;});
    }else pending=api('POST',kind==='raw_key'?'/v0/input/key':'/v0/ui/action',kind==='raw_key'?{key:87,action:1}:{selector:{role:'button',nth:0},action:'click'},headers,'STALE_SCREEN_REVISION').then(value=>{settled=true;return value;},reason=>{settled=true;error=reason;});
    try{
      await delay(60);assert.equal(settled,false,'Request must really wait behind the first owned gesture');
      const prior=await api('POST','/v0/operations/'+first.operationId+'/wait',{timeoutMs:6000});assert.equal(prior.state,'completed',JSON.stringify(prior));
      const options=await treeAt('OptionsScreen');assert.notEqual(options.screenRevision,title.screenRevision);
      const baseline=await clean();outcome=await pending;if(error)throw error;
      if(kind==='pipeline'){assert.equal(outcome.state,'failed',JSON.stringify(outcome));assert.equal(outcome.errorCode,'STALE_SCREEN_REVISION');}
      const after=await clean();assert.equal(after.inputDispatchSequence,baseline.inputDispatchSequence);
      assert.equal(after.agentPointer.x,baseline.agentPointer.x);assert.equal(after.agentPointer.y,baseline.agentPointer.y);
      await delay(100);assert.equal((await clean()).inputDispatchSequence,after.inputDispatchSequence,'No late rejected input');
      actionSamples.push({case:kind,pendingBehindOwnedGesture:true,expectedScreen:title.screenRevision,actualScreen:options.screenRevision,rejected:outcome.error??outcome.errorCode,dispatchBaseline:baseline.inputDispatchSequence,dispatchAfter:after.inputDispatchSequence,heldKeys:after.pressedKeyCount,heldButtons:after.pressedButtonCount});
      await click(options,'gui.done');await treeAt('TitleScreen');
    }finally{await pending;}
  });
  await check('valid original revisions bind the first native event without pinning self-caused page changes forever',async()=>{
    const title=await treeAt('TitleScreen');
    const headers={'X-MCP-Expected-Screen-Revision':String(title.screenRevision),'X-MCP-Expected-Menu-Revision':String(title.menuRevision)};
    const accepted=await api('POST','/v0/pipelines',{steps:[{type:'assert.that',condition:{type:'screen',category:'USER',classContains:'TitleScreen'}},{type:'wait.until',condition:{type:'ui.exists',category:'USER',selector:{translationKey:'menu.options'}},timeoutMs:2000},{type:'ui.action',selector:{translationKey:'menu.options'},action:'click'},{type:'ui.action',selector:{translationKey:'gui.done'},action:'click'}],timeoutMs:10000},headers);
    const completed=await api('POST','/v0/operations/'+accepted.operationId+'/wait',{timeoutMs:10000});
    assert.equal(completed.state,'completed',JSON.stringify(completed));assert.equal(completed.result.steps.length,4);
    const after=await treeAt('TitleScreen');assert.notEqual(after.screenRevision,title.screenRevision);
    await clean();
  });
  await api('POST','/v0/control/release');lease=undefined;
  consumerChecks.push(...await inputContractConsumers(repo,instance,target));
  assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
}catch(error){failure=error;console.error(error);}
finally{
  try{
    if(lease){
      const current=await api('GET','/v0/ui/tree');
      if(current.screenClass.endsWith('OptionsScreen'))await click(current,'gui.done');
      await clean();await api('POST','/v0/control/release');lease=undefined;
    }
    const control=await api('GET','/v0/control/mode');assert.equal(control.reconsentRequired,false,'Never reacquire after manual revoke');
    lease=(await api('POST','/v0/control/acquire',{ttlMs:15000})).leaseId;
    shutdownResponse='requested';
    try{await click(await treeAt('TitleScreen'),'menu.quit');shutdownResponse='acknowledged';}
    catch(error){if(error.code!=='CancellationException')throw error;shutdownResponse='cancelled_at_runtime_close_requires_runner_exit_attestation';}
    lease=undefined;
  }catch(error){failure??=error;console.error(error);}
  const after=await sources(),sourceChangedDuringRun=JSON.stringify(before)!==JSON.stringify(after);
  if(sourceChangedDuringRun)failure??=new Error('Sources changed during targeted Runtime test');
  const directory=resolve(repo,'validations',validation,'result');await mkdir(directory,{recursive:true});
  await writeFile(resolve(directory,target+'-input-preconditions.json'),JSON.stringify({status:failure?'FAIL':'PASS',target,scenario:'input-preconditions',fullSmoke:false,fullWorldMilestone:false,runtimeConnection:{runtimeInstanceId:connection.instanceId,port:connection.port,pid:connection.pid,discovery:'instance_directory'},testedAt:new Date().toISOString(),testedCommit:execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).trim(),dirty:true,evidence:'development_runtime_native',fixturePolicy:fixtureInput.policy,fixtureInput,arrange:'owned_title_options_screen_transition',checks,actionSamples,consumerChecks,consumerScope:'ui_condition_contract',shutdownResponse,notExecuted:['full_world_smoke','full_MCP_runtime_matrix','packaged_artifact_runtime','human_acceptance'],sourceHashes:after,sourceChangedDuringRun,persistentWriteCalls:0,...(failure?{error:String(failure)}:{})},null,2)+'\n');
}
if(failure)process.exitCode=1;
