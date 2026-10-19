import assert from 'node:assert/strict';
import { readFile, writeFile, readdir, mkdir } from 'node:fs/promises';
import { resolve, relative } from 'node:path';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { worldReadConsumers, commandOperationConsumer } from './world-read-consumers.mjs';
import { worldHeldActions } from './world-held-actions.mjs';
import { worldMovementActions } from './world-movement-actions.mjs';
import { worldBlockStateActions } from './world-block-state-actions.mjs';
import { consumerPriorityActions } from './consumer-priority-actions.mjs';
import { consumerRateActions } from './consumer-rate-actions.mjs';
import { consumerCommandFeedback } from './consumer-command-feedback.mjs';
import { consumerTitleReadiness, consumerWorldReadiness } from './consumer-readiness-actions.mjs';
import { consumerPlayerTitle, consumerPlayerFlags } from './consumer-player-feedback.mjs';
import { consumerHitFeedback } from './consumer-hit-feedback.mjs';
import { modeCollaborationActions } from './mode-collaboration-actions.mjs';
import { resourceVersionActions } from './resource-version-actions.mjs';
import { commandDiscoveryBatchActions } from './command-discovery-batch-actions.mjs';
import { serverCompletionActions } from './server-completion-actions.mjs';
import { interactionBaselineActions } from './interaction-baseline-actions.mjs';
import { continuousInputActions } from './continuous-input-actions.mjs';
import { continuousPerformanceActions } from './continuous-performance-actions.mjs';
import { contextExitEvidence } from './context-exit-evidence.mjs';
import { inputContextActions } from './input-context-actions.mjs';
import { regionStreamActions } from './region-stream-actions.mjs';
import { worldPlacementReceiptsActions } from './world-placement-receipts-actions.mjs';
import { mcpWorldBaseline } from './mcp-world-baseline.mjs';
import { connectRuntime, automaticTakeoverRequest } from '../../runtime-connection.mjs';

const repo = resolve(import.meta.dirname, '../../../..');
const openapi = JSON.parse(await readFile(resolve(repo,'components/protocol_schema/src/main/openapi/minecraft-control-v0.json'),'utf8'));
function validateModel(value, kind) {
  const shape = kind==='operation' ? openapi.components.schemas.OperationResponse : kind==='command' ? openapi.components.schemas.PlayerCommandResponse : kind==='player' ? openapi.components.schemas.WorldUserPlayerResponse : kind==='scene' ? openapi.components.schemas.WorldSceneResponse : openapi.paths['/v0/world/'+kind].get.responses['200'].content['application/json'].schema.oneOf[0];
  function verify(data, schema, path) {
    if (schema.$ref) return verify(data,openapi.components.schemas[schema.$ref.split('/').at(-1)],path);
    for (const part of schema.allOf ?? []) verify(data,part,path);
    if (schema.enum) assert(schema.enum.some(item=>item===data),path+' invalid enum');
    if (schema.type==='object') {
      assert(data&&typeof data==='object'&&!Array.isArray(data),path);
      for(const name of schema.required??[])assert(name in data,path+' missing '+name);
      for(const [name,child] of Object.entries(data)) {
        if(schema.additionalProperties===false)assert(name in (schema.properties??{}),path+' undeclared '+name);
        if(schema.properties?.[name])verify(child,schema.properties[name],path+'.'+name);
      }
    } else if (schema.type==='array') {
      assert(Array.isArray(data),path);if(schema.maxItems!==undefined)assert(data.length<=schema.maxItems,path);
      for(const child of data)verify(child,schema.items??{},path+'[]');
    } else if (schema.type==='number'||schema.type==='integer') {
      assert.equal(typeof data,'number',path);assert(Number.isFinite(data),path);
      if(schema.type==='integer')assert(Number.isInteger(data),path);
      if(schema.minimum!==undefined)assert(data>=schema.minimum,path);if(schema.maximum!==undefined)assert(data<=schema.maximum,path);
    } else if (schema.type==='string'||schema.type==='boolean') {
      assert.equal(typeof data,schema.type,path);
      if(schema.maxLength!==undefined)assert([...data].length<=schema.maxLength,path);
    }
  }
  verify(value,shape,kind);
}
const args = Object.fromEntries(process.argv.slice(2).map(arg => arg.replace(/^--/, '').split('=')));
const target = args.target;
const scenario=args.scenario??'full';assert(['full','movement','block-state','consumer','rate-limit','command-feedback','mode-collaboration','resource-version','command-discovery-batch','server-completion','interaction-baseline','continuous-input','continuous-performance','input-context','region-stream','world-placement-receipts'].includes(scenario));
assert(['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric'].includes(target));
const validation = args.validation ?? 'conformance-world_read';
assert(['conformance-world_read','migration-runtime_core'].includes(validation));
const instance = resolve(repo, 'validations',validation,'instance', target, 'client');
const fixtureInput=JSON.parse(await readFile(resolve(instance,'fixture-input.json'),'utf8').catch(error=>{if(error.code==='ENOENT')return 'null';throw error;}));
if(args['shutdown-only']!=='true'){
  if(args['fixture-hash']){
    assert.equal(fixtureInput?.policy,'immutable_baseline_restored');assert.equal(fixtureInput.target,target);assert.equal(fixtureInput.role,'client');
    assert.match(args['fixture-hash'],/^[0-9a-f]{64}$/);assert.equal(fixtureInput.baselineSha256,args['fixture-hash']);assert.equal(fixtureInput.restoredSha256,args['fixture-hash']);assert.equal(fixtureInput.credentialsCopied,false);
  }else assert.equal(fixtureInput,null,'Stale fixture receipt cannot be used as bootstrap preparation');
}
const token = (await readFile(resolve(instance, 'minecraft-protocol/token'), 'utf8')).trim();
const connection = await connectRuntime(instance, target);
const base = connection.base;
let lease, renew, failure, debugArm;
const checks = [];
const sceneSamples = [];
const actionSamples = [];
let consumerChecks = [];
const commandConsumerChecks = [];
async function api(method, path, body, expectedError, extraHeaders={}) {
  if(method==='POST'&&path==='/v0/control/acquire')body=automaticTakeoverRequest(await api('GET','/v0/control/mode'),body);
  const response = await connection.fetch(path, { method, headers: { ...(lease ? {'x-mcp-control-lease':lease}:{}), ...(debugArm ? {'x-mcp-debug-arm':debugArm}:{}), ...(body ? {'content-type':'application/json'}:{}), ...extraHeaders }, ...(body ? {body:JSON.stringify(body)}:{}), signal:AbortSignal.timeout(25000) });
  const value = await response.json();
  if (expectedError) { assert((Array.isArray(expectedError)?expectedError:[expectedError]).includes(value.error), JSON.stringify(value)); assert(!response.ok); return value; }
  if(!response.ok) {
    const error=new Error(JSON.stringify(value));error.status=response.status;error.runtimeError=value;throw error;
  }
  if(method==='GET'&&path.startsWith('/v0/world/entities?')&&!path.includes('category=DEBUG'))validateModel(value,'entities');
  return value;
}
const delay = () => new Promise(resolveWait => setTimeout(resolveWait, 180));
const nodes = tree => (tree.children ?? []).flatMap(node => [node, ...nodes(node)]);
async function wait(predicate, description, budget=90000) {
  const end=Date.now()+budget;
  do { const tree=await api('GET','/v0/ui/tree'); if (!tree.overlayIdentity && predicate(tree)) return tree; await delay(); } while(Date.now()<end);
  throw new Error('UI timeout: '+description);
}
async function clickNode(node, modifiers=0) { return api('POST','/v0/ui/action',{selector:{nodeId:node.nodeId},action:'click',modifiers}); }
async function click(key, label) {
  const tree=await api('GET','/v0/ui/tree');
  const candidates=nodes(tree).filter(node=>node.translationKey===key&&node.active!==false&&node.visible!==false);
  const node=candidates.length===1?candidates[0]:nodes(tree).find(node=>node.label===label&&node.active!==false&&node.visible!==false);
  assert(node,'Missing semantic button: '+key); return clickNode(node);
}
async function key(code) {
  const operation=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:code,holdMs:40}],timeoutMs:12000});
  const outcome=await api('POST','/v0/operations/'+operation.operationId+'/wait',{timeoutMs:12000});
  assert.equal(outcome.state,'completed',JSON.stringify(outcome));
}
async function acquire() {
  const current=await api('GET','/v0/control/mode');
  assert.equal(current.reconsentRequired,false,'User manually ended control; runner must not reacquire without conversation consent');
  lease=(await api('POST','/v0/control/acquire',{ttlMs:60000})).leaseId;
  renew=setInterval(()=>{void api('POST','/v0/control/renew',{ttlMs:60000}).catch(()=>{});},20000);
}
async function release() { clearInterval(renew); await api('POST','/v0/control/release'); lease=undefined; }
async function mode(value) { const current=await api('GET','/v0/control/mode'); await api('POST','/v0/control/mode',{mode:value,expectedModeVersion:current.modeVersion}); }
async function shutdown() {
  if (!lease) await acquire();
  for(let i=0;i<10;i++) {
    const tree=await wait(()=>true,'shutdown navigation');
    if(tree.screenClass.endsWith('TitleScreen')) {
      await click('menu.quit','Quit Game').catch(()=>{}); clearInterval(renew); lease=undefined; return;
    }
    const quit=nodes(tree).find(node=>['menu.disconnect','menu.returnToMenu'].includes(node.translationKey));
    if(quit) {
      const before=await api('GET','/v0/input/state');let unconfirmed;
      try{await clickNode(quit);}catch(error){
        if(error.status!==409||!['REQUEST_CANCELLED','CONTROL_INPUT_GENERATION_CHANGED'].includes(error.runtimeError?.error))throw error;
        unconfirmed={status:error.status,body:error.runtimeError};
      }
      const title=await wait(t=>t.screenClass.endsWith('TitleScreen'),'Save and Quit finishes before another navigation input');
      if(unconfirmed){
        const released=await api('POST','/v0/wait/until',{condition:{type:'input.released'},timeoutMs:5000});
        assert.equal(released.passed,true);
        const after=await api('GET','/v0/input/state');
        actionSamples.push({case:'shutdown_context_exit',...contextExitEvidence(before,after,title,unconfirmed),released});
        console.log('Save and Quit request unconfirmed; new native context, title and released input independently verified; no replay');
      }
    } else await key(256);
  }
  throw new Error('Unable to request normal shutdown');
}
if(args['shutdown-only']==='true'){await shutdown();process.exit(0);}
async function sources() {
  const hashes={};
  for(const dir of [resolve(repo,'versions',target,'src'),resolve(repo,'components/runtime_safety/src/main'),resolve(repo,'components/runtime_core/src/main')]) {
    for(const entry of await readdir(dir,{recursive:true,withFileTypes:true})) if(entry.isFile()) {
      const path=resolve(entry.parentPath,entry.name);hashes[relative(repo,path).replaceAll('\\','/')]=createHash('sha256').update(await readFile(path)).digest('hex');
    }
  }
for(const path of ['components/protocol_schema/src/main/openapi/minecraft-control-v0.json',relative(repo,import.meta.filename),'components/validation_tools/conformance-world_read/task/Invoke-WorldReadSmoke.ps1','components/validation_tools/conformance-world_read/task/Test-WorldReadArtifact.ps1','components/validation_tools/conformance-world_read/task/world-read-consumers.mjs','components/validation_tools/conformance-world_read/task/world-held-actions.mjs','components/validation_tools/conformance-world_read/task/world-entity-actions.mjs','validations/conformance-world_read/validation.toml','gradle.properties','settings.gradle.kts','gradle/libs.versions.toml',`versions/${target}/build.gradle.kts`,`versions/${target}/target.properties`]) hashes[path]=createHash('sha256').update(await readFile(resolve(repo,path))).digest('hex');
  for(const entry of await readdir(resolve(repo,'components/companion/src'),{recursive:true,withFileTypes:true}))if(entry.isFile()&&entry.name.endsWith('.ts')){const path=resolve(entry.parentPath,entry.name);hashes[relative(repo,path).replaceAll('\\','/')]=createHash('sha256').update(await readFile(path)).digest('hex');}
  for(const path of ['components/companion/package.json','components/companion/package-lock.json'])hashes[path]=createHash('sha256').update(await readFile(resolve(repo,path))).digest('hex');
  for(const path of ['components/validation_tools/InstanceProfiles.ps1','components/validation_tools/Prepare-InstanceProfile.ps1','components/validation_tools/instance-profiles.gradle','build.gradle.kts','components/validation_tools/RuntimeConnection.ps1','components/validation_tools/runtime-connection.mjs','components/validation_tools/vanilla-command-fixtures.mjs'])hashes[path]=createHash('sha256').update(await readFile(resolve(repo,path))).digest('hex');
  const fixtureSource='components/validation_tools/conformance-world_read/task/WorldReadFixture.ps1';hashes[fixtureSource]=createHash('sha256').update(await readFile(resolve(repo,fixtureSource))).digest('hex');
  const joint='components/validation_tools/conformance-world_read/task/world-ui-actions.mjs';
  hashes[joint]=createHash('sha256').update(await readFile(resolve(repo,joint))).digest('hex');
  const movement='components/validation_tools/conformance-world_read/task/world-movement-actions.mjs';
  hashes[movement]=createHash('sha256').update(await readFile(resolve(repo,movement))).digest('hex');
  const blockState='components/validation_tools/conformance-world_read/task/world-block-state-actions.mjs';
  hashes[blockState]=createHash('sha256').update(await readFile(resolve(repo,blockState))).digest('hex');
  const consumers='components/validation_tools/conformance-world_read/task/consumer-priority-actions.mjs';
  hashes[consumers]=createHash('sha256').update(await readFile(resolve(repo,consumers))).digest('hex');
  const rates='components/validation_tools/conformance-world_read/task/consumer-rate-actions.mjs';
  hashes[rates]=createHash('sha256').update(await readFile(resolve(repo,rates))).digest('hex');
  const feedback='components/validation_tools/conformance-world_read/task/consumer-command-feedback.mjs';
  hashes[feedback]=createHash('sha256').update(await readFile(resolve(repo,feedback))).digest('hex');
  const readiness='components/validation_tools/conformance-world_read/task/consumer-readiness-actions.mjs';
  hashes[readiness]=createHash('sha256').update(await readFile(resolve(repo,readiness))).digest('hex');
  const playerFeedback='components/validation_tools/conformance-world_read/task/consumer-player-feedback.mjs';
  hashes[playerFeedback]=createHash('sha256').update(await readFile(resolve(repo,playerFeedback))).digest('hex');
  const hitFeedback='components/validation_tools/conformance-world_read/task/consumer-hit-feedback.mjs';
  hashes[hitFeedback]=createHash('sha256').update(await readFile(resolve(repo,hitFeedback))).digest('hex');
  const collaboration='components/validation_tools/conformance-world_read/task/mode-collaboration-actions.mjs';
  hashes[collaboration]=createHash('sha256').update(await readFile(resolve(repo,collaboration))).digest('hex');
  const baseline='components/validation_tools/conformance-world_read/task/interaction-baseline-actions.mjs';
  hashes[baseline]=createHash('sha256').update(await readFile(resolve(repo,baseline))).digest('hex');
  const continuous='components/validation_tools/conformance-world_read/task/continuous-input-actions.mjs';
  hashes[continuous]=createHash('sha256').update(await readFile(resolve(repo,continuous))).digest('hex');
  const performanceTool='components/validation_tools/conformance-world_read/task/continuous-performance-actions.mjs';
  hashes[performanceTool]=createHash('sha256').update(await readFile(resolve(repo,performanceTool))).digest('hex');
  const exitTool='components/validation_tools/conformance-world_read/task/context-exit-evidence.mjs';
  hashes[exitTool]=createHash('sha256').update(await readFile(resolve(repo,exitTool))).digest('hex');
  const contextTool='components/validation_tools/conformance-world_read/task/input-context-actions.mjs';
  hashes[contextTool]=createHash('sha256').update(await readFile(resolve(repo,contextTool))).digest('hex');
  const regionTool='components/validation_tools/conformance-world_read/task/region-stream-actions.mjs';
  hashes[regionTool]=createHash('sha256').update(await readFile(resolve(repo,regionTool))).digest('hex');
  const placementTool='components/validation_tools/conformance-world_read/task/world-placement-receipts-actions.mjs';
  hashes[placementTool]=createHash('sha256').update(await readFile(resolve(repo,placementTool))).digest('hex');
  const mcpBaselineTool='components/validation_tools/conformance-world_read/task/mcp-world-baseline.mjs';
  hashes[mcpBaselineTool]=createHash('sha256').update(await readFile(resolve(repo,mcpBaselineTool))).digest('hex');
  for(const path of ['components/validation_tools/conformance-world_read/task/resource-version-actions.mjs','components/validation_tools/conformance-world_read/task/command-discovery-batch-actions.mjs','components/validation_tools/conformance-world_read/task/server-completion-actions.mjs'])hashes[path]=createHash('sha256').update(await readFile(resolve(repo,path))).digest('hex');
  return hashes;
}
const before=await sources();
async function check(name, run) {await run();checks.push({name,status:'PASS'});console.log('PASS: '+name);}
try {
  if(scenario==='world-placement-receipts') {
    await worldPlacementReceiptsActions({api,target,fixtureInput,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples,forgetExpiredLease:()=>{clearInterval(renew);lease=undefined;}});
  } else if(scenario==='region-stream') {
    await regionStreamActions({api,target,fixtureInput,connection,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples,forgetExpiredLease:()=>{clearInterval(renew);lease=undefined;}});
    await mcpWorldBaseline({api,repo,instance,target,check,actionSamples});
  } else if(scenario==='input-context') {
    await inputContextActions({api,target,fixtureInput,connection,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples,forgetExpiredLease:()=>{clearInterval(renew);lease=undefined;}});
  } else if(scenario==='continuous-performance') {
    await continuousPerformanceActions({api,target,fixtureInput,connection,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples,forgetExpiredLease:()=>{clearInterval(renew);lease=undefined;}});
  } else if(scenario==='continuous-input') {
    await continuousInputActions({api,connection,repo,instance,target,mode,acquire,release,key,click,clickNode,wait,nodes,check,actionSamples,stopRenewal:()=>clearInterval(renew),forgetExpiredLease:()=>{clearInterval(renew);lease=undefined;}});
  } else if(scenario==='interaction-baseline') {
    await interactionBaselineActions({api,connection,target,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples});
  } else if(scenario==='server-completion') {
    await serverCompletionActions({api,connection,repo,instance,target,mode,acquire,release,key,click,clickNode,wait,nodes,check,actionSamples});
  } else if(scenario==='resource-version') {
    await resourceVersionActions({api,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples,setArm:value=>{debugArm=value;},repo,instance,target,enterWorld:true});
  } else if(scenario==='command-discovery-batch') {
    await commandDiscoveryBatchActions({api,repo,instance,target,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples});
  } else if(scenario==='mode-collaboration') {
    await modeCollaborationActions({api,connection,repo,instance,target,mode,acquire,release,key,click,clickNode,wait,nodes,check,actionSamples,setArm:value=>{debugArm=value;}});
  } else if(scenario==='rate-limit') {
    await consumerRateActions({api,connection,repo,instance,acquire,release,leaseHeaders:()=>({'x-mcp-control-lease':lease}),check,actionSamples});
  } else if(scenario==='command-feedback') {
    await consumerTitleReadiness({api,check,actionSamples});
    await consumerPlayerTitle({api,check,actionSamples});
    await consumerCommandFeedback({api,repo,instance,target,acquire,release,click,wait,nodes,check,actionSamples,enterWorld:true});
    await consumerWorldReadiness({api,acquire,release,check,actionSamples});
    await consumerPlayerFlags({api,repo,instance,target,acquire,release,check,actionSamples});
    await consumerHitFeedback({api,repo,instance,target,acquire,release,leaseId:()=>lease,check,actionSamples});
  } else if(scenario==='movement') {
    await worldMovementActions({api,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples,fixtureInput});
  } else if(scenario==='block-state') {
    await worldBlockStateActions({api,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples,setArm:value=>{debugArm=value;},repo,instance,target,enterWorld:true});
  } else if(scenario==='consumer') {
    await consumerPriorityActions({api,connection,repo,instance,target,mode,acquire,release,key,click,wait,nodes,check,actionSamples,setArm:value=>{debugArm=value;},enterWorld:true});
  } else {
  await consumerTitleReadiness({api,check,actionSamples});
  await consumerPlayerTitle({api,check,actionSamples});
  await check('instance discovery and pinned transport identity',async()=>{
    const session=await api('GET','/v0/session');assert.equal(session.runtimeConnection.runtimeInstanceId,connection.instanceId);assert.equal(session.runtimeConnection.port,connection.port);
    const refused=await fetch(base+'/v0/control/acquire',{method:'POST',headers:{authorization:'Bearer '+token,'x-mcp-runtime-instance':'stale-instance'},signal:AbortSignal.timeout(3000)});
    assert.equal(refused.status,409);assert.equal((await refused.json()).error,'RUNTIME_INSTANCE_MISMATCH');
    assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
  });
  assert.equal((await api('GET','/v0/session')).target,target);
  await check('capability truth and unavailable client world at title',async()=>{
    const capability=await api('GET','/v0/capabilities');assert.equal(capability.worldContract.implementationStage,'bounded_client_world_model');assert.deepEqual(capability.worldContract.userSemanticActions,['aim','attack','use','mine','use_hold','place']);assert.equal(capability.worldContract.userMaxHoldMs,10000);assert.equal(capability.worldContract.userHeldHands,'main_hand_or_off_hand_empty_main');assert.equal(capability.worldContract.userMiningCompletion,'bounded_synchronous_native_destroy_including_fluid');assert.equal(capability.worldContract.readConsumerIsolationComplete,true);assert.equal(capability.worldContract.consumerIsolationComplete,false);assert.equal(capability.worldContract.milestoneComplete,false);
    const hooks=await api('GET','/v0/diagnostics/hooks');assert.equal(hooks.cancellableInjectionCount,target.startsWith('26.3-')?26:target.startsWith('26.')?23:21);
    for(const id of ['player_command_entry_guard','player_command_packet_guard','owned_server_command_completion'])assert(hooks.hooks.some(hook=>hook.id===id&&hook.cancellable===true),'Actual Hook manifest must include '+id);
    assert(hooks.hooks.some(hook=>hook.id==='system_toast_reset_revision'&&hook.cancellable===false&&hook.behavior==='passive_native_reset_revision'),'Native reset generation is passive observation, not an input cancellation Hook');
    const player=await api('GET','/v0/player');validateModel(player,'player');assert.equal(player.dataCategory,'USER');assert.equal(player.domain,'WORLD');for(const field of ['uuid','serverTick','nbt'])assert(!(field in player));assert.equal(player.available,false);
  });
  await check('server USER and invalid category fail before internal capture',async()=>{
    for(const path of ['/v0/server/player','/v0/server/world/block?x=0&y=80&z=0','/v0/server/world/entities'])await api('GET',path,undefined,'WORLD_USER_PERCEPTION_UNAVAILABLE');
    await api('GET','/v0/player?category=CLIENT_KNOWN',undefined,'INVALID_WORLD_CATEGORY');
  });
  await check('READ debug independent of write Arm and Lease',async()=>{
    const player=await api('GET','/v0/player?category=DEBUG');assert.equal(player.dataCategory,'DEBUG');assert.equal(player.perceptionFiltered,false);assert.equal(player.available,false);assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
  });
  await check('OPERATE accepts both independently authorized categories without Lease',async()=>{
    await mode('OPERATE');assert.equal((await api('GET','/v0/player')).dataCategory,'USER');assert.equal((await api('GET','/v0/player?category=DEBUG')).dataCategory,'DEBUG');await mode('READ');
  });
  await acquire();
  await check('TAKEOVER retains authorized diagnostic read',async()=>assert.equal((await api('GET','/v0/player?category=DEBUG')).dataCategory,'DEBUG'));
  await check('existing semantic GUI enters dedicated creative flat world',async()=>{
    await click('menu.singleplayer','Singleplayer');
    let tree=await wait(tree=>/SelectWorldScreen$|CreateWorldScreen$/.test(tree.screenClass),'select world');
    if(tree.screenClass.endsWith('SelectWorldScreen')) {
      const world=nodes(tree).find(node=>node.worldName==='World-Read-Alpha');
      if(world){await clickNode(world);await click('selectWorld.select','Play Selected World');await wait(tree=>!tree.screenClass,'enter world',150000);return;}
      await click('selectWorld.create','Create New World');
    }
    await wait(tree=>tree.screenClass.endsWith('CreateWorldScreen'),'create world');
    await api('POST','/v0/ui/action',{selector:{role:'text_field',nth:0},action:'type_text',text:'World-Read-Alpha'});
    for(let i=0;i<4;i++){tree=await api('GET','/v0/ui/tree');const button=nodes(tree).find(node=>node.label?.startsWith('Game Mode:'));assert(button);if(button.label.includes('Creative'))break;await clickNode(button);}
    tree=await api('GET','/v0/ui/tree');const worldTab=nodes(tree).find(node=>node.role==='tab'&&node.label==='World');if(worldTab)await clickNode(worldTab);
    for(let i=0;i<12;i++){tree=await api('GET','/v0/ui/tree');const type=nodes(tree).find(node=>node.label?.startsWith('World Type:'));if(!type||/Superflat|Flat/.test(type.label))break;await clickNode(type);}
    tree=await api('GET','/v0/ui/tree');const game=nodes(tree).find(node=>node.role==='tab'&&node.label==='Game');if(game)await clickNode(game);
    await click('selectWorld.create','Create New World');await wait(tree=>!tree.screenClass,'generate world',150000);
  });
  await check('bounded USER player/block/entities/scene independent of GUI, no direct mutation',async()=>{
    await key(69);await wait(tree=>tree.screenClass.includes('Creative'),'creative inventory');
    assert.equal((await api('GET','/v0/player')).available,true);
    await key(69);await wait(tree=>!tree.screenClass,'return from Inventory without changing test items');
    const player=await api('GET','/v0/player');validateModel(player,'player');assert.equal(player.authority,'CLIENT_KNOWN');assert.equal(player.storageAccessed,false);assert.equal(typeof player.health,'number');assert.equal(typeof player.x,'number');
    const scene=await api('GET','/v0/world/scene?radius=2&blockLimit=128&entityLimit=16');
    validateModel(scene,'scene');
    assert.equal(scene.dataCategory,'USER');assert.equal(scene.available,true);assert.equal(scene.coverage,'bounded_client_cache');
    assert(scene.blocks.length>0);assert(scene.blocks.length<=128);assert.equal(scene.visibility,'not_evaluated');assert.equal(scene.chunkLoadRequested,false);sceneSamples.push(scene);
    const x=Math.floor(player.x), y=Math.floor(player.y)-1, z=Math.floor(player.z);
    const block=await api('GET',`/v0/world/block?x=${x}&y=${y}&z=${z}`);validateModel(block,'block');assert.equal(block.available,true);assert.equal(typeof block.block,'string');
    assert.equal((await api('GET','/v0/world/block?x=1000000&y=64&z=1000000')).reason,'outside_user_radius');
    const entities=await api('GET','/v0/world/entities?radius=16&limit=8');validateModel(entities,'entities');assert(Array.isArray(entities.entities));assert(entities.entities.length<=8);
    for(const entity of entities.entities)for(const field of ['uuid','health','goals','nbt','inventory'])assert(!(field in entity),field);
    for(const path of ['/v0/world/entities?radius=33','/v0/world/scene?radius=9','/v0/world/block?x=0.5&y=64&z=0'])
      await api('GET',path,undefined,'INVALID_WORLD_USER_QUERY');
    await key(69);await wait(tree=>tree.screenClass.includes('Creative'),'client model remains available in Inventory');
    const guiScene=await api('GET','/v0/world/scene?radius=1');assert.equal(guiScene.available,true);sceneSamples.push(guiScene);
    await key(69);await wait(tree=>!tree.screenClass,'return gameplay');
    await release();
  });
  await check('player command submission is unconfirmed and cannot produce clean gameplay evidence',async()=>{
    await api('POST','/v0/command/player',{command:'help'},'TAKEOVER_REQUIRED');
    let recording;
    await acquire();
    try {
      recording=await api('POST','/v0/recordings',{captureFrames:false,intervalMs:250,durationMs:60000,maxSamples:100,stateReads:[{providerId:'minecraft:client/player'}],contactSheet:{enabled:false}});
      let sampled=false;const sampleDeadline=Date.now()+10000;
      do{
        const current=await api('GET','/v0/recordings/'+recording.recordingId);
        if(current.writtenStates>0){sampled=true;break;}
        assert.equal(current.status,'recording',JSON.stringify(current));await delay();
      }while(Date.now()<sampleDeadline);
      assert(sampled,'Recording must have an actual structured state before the command, not just a pending sample');
      await api('POST','/v0/command/player',{command:'help\nhelp'},'INVALID_PLAYER_COMMAND');
      assert.equal((await api('GET','/v0/recordings/'+recording.recordingId)).evidenceContaminated,false,'rejected command has no submission attempt');
      const receipt=await api('POST','/v0/command/player',{command:'time add 0'});
      validateModel(receipt,'command');
      assert.equal(receipt.effectClassification,'STATE_MUTATION');assert.equal(receipt.mechanism,'GAME_ROUTED_RAW');
      assert.equal(receipt.gameplayAcceptanceEligible,false);
      assert.equal(receipt.effectConfirmed,false);
      assert.equal(receipt.serverValidationObserved,false);
      assert.equal(receipt.evidenceContaminated,true);
      assert.equal((await api('GET','/v0/recordings/'+recording.recordingId)).evidenceContaminated,true);
      actionSamples.push({case:'classified_native_player_command',receipt,scope:'native_chat_classified_submission_not_execution_proof'});
    } finally {
      try {if(recording)await api('DELETE','/v0/recordings/'+recording.recordingId);}
      finally {if(lease)await release();}
    }
    let status;const end=Date.now()+15000;
    do{status=await api('GET','/v0/recordings/'+recording.recordingId);if(status.artifactReady)break;assert.notEqual(status.status,'failed',JSON.stringify(status));await delay();}while(Date.now()<end);
    assert(status.artifactReady,JSON.stringify(status));
    const response=await connection.fetch('/v0/recordings/'+recording.recordingId+'/artifact',{signal:AbortSignal.timeout(25000)});assert(response.ok);
    const artifact=resolve(instance,'world-command-evidence.zip');await writeFile(artifact,new Uint8Array(await response.arrayBuffer()));
    const checked=JSON.parse(execFileSync('pwsh',['-NoProfile','-File',resolve(import.meta.dirname,'Test-WorldReadArtifact.ps1'),'-Artifact',artifact,'-RequireCommandContamination'],{cwd:repo,encoding:'utf8'}));
    assert.equal(checked.status,'PASS');assert.equal(checked.commandAttemptEvents,1);assert.equal(checked.evidenceContaminated,true);
  });
  await check('native command tree classifies queries and mutations and refuses indirect/host effects',async()=>{
    await acquire();
    try {
      const query=await api('POST','/v0/command/player',{command:'execute run time query gametime'});
      assert.equal(query.effectClassification,'INFORMATION');assert.equal(query.classificationSource,'native_player_command_tree');
      const aliasQuery=await api('POST','/v0/command/player',{command:'xp query @s levels'});
      assert.equal(aliasQuery.effectClassification,'INFORMATION','Native alias continuation must retain the complete query path');
      const mutation=await api('POST','/v0/command/player',{command:'time add 0'});
      assert.equal(mutation.effectClassification,'STATE_MUTATION');assert.equal(mutation.mode,'DEBUG_PRIVILEGED');
      assert.equal(mutation.evidenceContaminated,true);assert.equal(mutation.permissionEscalated,false);assert.equal(mutation.effectConfirmed,false);
      await api('POST','/v0/command/player',{command:'execute run reload'},'PLAYER_COMMAND_OUT_OF_SCOPE');
      await api('POST','/v0/command/player',{command:'function minecraft:missing_milestone_fixture'},'PLAYER_COMMAND_UNCLASSIFIED');
      await api('POST','/v0/command/player',{command:'missing_milestone_command'},'PLAYER_COMMAND_NOT_AVAILABLE');
      actionSamples.push({case:'native_command_effects',query,aliasQuery,mutation,denied:['host_control','unresolved_function','unknown_native_command'],executionProof:false});
    } finally {await release();}
  });
  await check('raw Chat Enter shares the final guard and carries command receipts through key release',async()=>{
    await acquire();
    try {
      for(const [command,effect] of [['time query gametime','INFORMATION'],['xp query @s levels','INFORMATION'],['time add 0','STATE_MUTATION'],['reload','PLAYER_COMMAND_OUT_OF_SCOPE']]) {
        await key(84);await wait(tree=>tree.screenClass.endsWith('ChatScreen'),'actual native Chat Screen');
        await api('POST','/v0/ui/action',{selector:{role:'text_field',nth:0},action:'type_text',text:'/'+command});
        const started=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:257,holdMs:40}],timeoutMs:12000});
        const outcome=await api('POST','/v0/operations/'+started.operationId+'/wait',{timeoutMs:12000});
        if(effect==='PLAYER_COMMAND_OUT_OF_SCOPE') {assert.equal(outcome.state,'failed',JSON.stringify(outcome));assert.equal(outcome.errorCode,effect,JSON.stringify(outcome));}
        else {
          assert.equal(outcome.state,'completed',JSON.stringify(outcome));
          const receipts=outcome.result.commandSubmissions;assert.equal(receipts.length,1,JSON.stringify(outcome));
          const receipt=receipts[0];assert.equal(receipt.effectClassification,effect);assert.equal(receipt.mechanism,'GAME_ROUTED_RAW');
          assert.equal(receipt.effectConfirmed,false);assert.equal(receipt.evidenceContaminated,effect==='STATE_MUTATION');
          assert.equal(outcome.result.evidenceContaminated,effect==='STATE_MUTATION');
          assert.equal(outcome.result.steps[0].result.commandSubmissions.length,1,'key up must not erase key-down command provenance');
        }
        actionSamples.push({case:'raw_chat_final_guard',command,outcome});
        const tree=await api('GET','/v0/ui/tree');if(tree.screenClass.endsWith('ChatScreen'))await key(256);
        await wait(tree=>!tree.screenClass,'return gameplay after raw command');
        const held=await api('GET','/v0/input/state');assert.equal(held.pressedKeyCount,0);assert.equal(held.pressedButtonCount,0);
      }
    } finally {await release();}
  });
  await check('failed cancelled timed-out and lease-lost raw commands retain provenance without late input',async()=>{
    for(const scenario of ['failed_query','cancelled_mutation','timed_out_mutation','lease_lost_mutation']) {
      await acquire();
      try {
        await key(84);await wait(tree=>tree.screenClass.endsWith('ChatScreen'),'native chat for terminal provenance');
        const query=scenario==='failed_query',effect=query?'INFORMATION':'STATE_MUTATION',command=query?'time query gametime':'time add 0';
        await api('POST','/v0/ui/action',{selector:{role:'text_field',nth:0},action:'type_text',text:'/'+command});
        const tail=query?[{type:'ui.action',selector:{nodeId:'missing_milestone_command_target'},action:'click'}]:[{type:'delay',durationMs:10000},{type:'key.tap',key:87,holdMs:40}];
        const started=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:257,holdMs:40},...tail],timeoutMs:scenario==='timed_out_mutation'?3000:15000});
        const path='/v0/operations/'+started.operationId;
        let observed;const deadline=Date.now()+10000;
        do{observed=await api('GET',path);if(observed.result?.commandSubmissions?.length===1)break;assert(Date.now()<deadline,'native command provenance was not observed');await delay();}while(true);
        if(scenario==='cancelled_mutation')await api('DELETE',path);
        if(scenario==='lease_lost_mutation')await release();
        const expectedState=query?'failed':scenario==='timed_out_mutation'?'timed_out':'cancelled';
        let terminal;const end=Date.now()+10000;
        do{terminal=await api('GET',path);if(terminal.state===expectedState&&terminal.commandSubmissionsComplete)break;assert(Date.now()<end,JSON.stringify(terminal));await delay();}while(true);
        validateModel(terminal,'operation');
        assert.equal(terminal.result.type,'input.command.provenance');assert.equal(terminal.result.partial,true);
        assert.equal(terminal.result.commandSubmissions.length,1);assert.equal(terminal.result.evidenceContaminated,!query);
        assert.equal(terminal.result.evidenceAuthority,query?'PLAYTEST':'DEBUG_PRIVILEGED');assert.equal(terminal.result.commandEffectsConfirmed,false);
        const receipt=terminal.result.commandSubmissions[0];assert.equal(receipt.mechanism,'GAME_ROUTED_RAW');assert.equal(receipt.effectClassification,effect);assert.equal(receipt.effectConfirmed,false);assert.equal(receipt.gameplayAcceptanceEligible,false);
        if(query)assert.equal(terminal.errorCode,'UI_NODE_NOT_FOUND');
        if(scenario==='timed_out_mutation')assert.equal(terminal.errorCode,'PIPELINE_TIMEOUT');
        if(lease)await release();
        const input=await api('GET','/v0/input/state');assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);
        // A negative observation interval, not a fixed delay substituted for a success condition.
        await new Promise(done=>setTimeout(done,250));
        const after=await api('GET','/v0/input/state');assert.equal(after.inputDispatchSequence,input.inputDispatchSequence,'no late W after terminal cleanup');
        assert.equal((await api('GET',path)).result.commandSubmissions.length,1,'polling must not duplicate receipts');
        commandConsumerChecks.push(await commandOperationConsumer(repo,instance,target,started.operationId,{state:expectedState,effect,contaminated:!query,scenario}));
        actionSamples.push({case:'raw_command_terminal_provenance',scenario,terminal,heldKeys:0,heldButtons:0,noLateInput:true,normalPacket:true,directMutation:false});
      } finally {if(lease)await release();}
    }
  });
  await check('USER semantic target aim/use/attack, stale rejection and cancellation',async()=>{
    const step=targetRef=>({type:'world.action',targetRef,action:'aim'});
    await api('POST','/v0/pipelines',{steps:[step('world:unknown')]},'TAKEOVER_REQUIRED');
    // Reset only the explicitly owned disposable fixture area, including water left by an
    // interrupted fluid case. Administrative commands are diagnostic Arrange, never USER Act.
    await acquire();
    const initial=await api('GET','/v0/player');
    await api('POST','/v0/command/player',{command:'fill ~-16 ~ ~-16 ~16 ~3 ~16 minecraft:air replace minecraft:water'});
    await api('POST','/v0/command/player',{command:`tp @s ${Math.floor(initial.x)+.5} ${Math.floor(initial.y)} ${Math.floor(initial.z)+.5}`});
    await new Promise(r=>setTimeout(r,2000));
    await release();
    await mode('OPERATE');
    await api('POST','/v0/pipelines',{steps:[step('world:unknown')]},'TAKEOVER_REQUIRED');
    const p=await api('GET','/v0/player?category=DEBUG');
    const pos={x:Math.floor(p.x)+2,y:Math.floor(p.y),z:Math.floor(p.z)};
    const wall={...pos,x:pos.x-1,y:pos.y+1};
    const query=Object.entries(pos).map(([key,value])=>key+'='+value).join('&');
    const old=await api('GET','/v0/world/block?category=DEBUG&'+query);
    assert(['minecraft:air','minecraft:note_block'].includes(old.block),'Only empty or this owned smoke fixture may be prepared');
    const fingerprint=await api('GET','/v0/world/fingerprint?category=DEBUG');
    debugArm=(await api('POST','/v0/debug/arm',{worldFingerprint:fingerprint.worldFingerprint,namespaces:['world'],ttlMs:60000})).debugArmId;
    async function arrange(blockId,at=pos) {await api('POST','/v0/debug/world/block',{...at,blockId});await new Promise(r=>setTimeout(r,200));}
    async function operation(steps) {
      const started=await api('POST','/v0/pipelines',{steps,timeoutMs:15000});
      return api('POST','/v0/operations/'+started.operationId+'/wait',{timeoutMs:15000});
    }
    async function targetBlock() {return api('GET','/v0/world/block?'+query);}
    try {
      await arrange('minecraft:note_block');
      await mode('READ');await acquire();
      const beforeReplacement=await targetBlock();const issuedAt=Date.now();
      await release();await mode('OPERATE');await arrange('minecraft:air');await arrange('minecraft:note_block');
      await mode('READ');await acquire();
      const replaced=await operation([step(beforeReplacement.targetRef)]);
      assert.equal(replaced.state,'failed');assert.equal(replaced.errorCode,'WORLD_TARGET_STALE',JSON.stringify(replaced));
      assert(Date.now()-issuedAt<15000,'A-B-A refusal must be lifecycle based, not just TTL');
      await release();await mode('OPERATE');await arrange('minecraft:stone',wall);
      await mode('READ');await acquire();
      const blocked=await operation([step((await targetBlock()).targetRef)]);
      assert.equal(blocked.state,'failed');assert.equal(blocked.errorCode,'WORLD_TARGET_NOT_HIT',JSON.stringify(blocked));
      await release();await mode('OPERATE');await arrange('minecraft:air',wall);await mode('READ');await acquire();
      const spatial=await api('GET','/v0/world/scene?radius=3&blockLimit=512&entityLimit=16');
      const candidate=spatial.blocks.find(block=>block.block==='minecraft:note_block'&&block.x===pos.x&&block.y===pos.y&&block.z===pos.z);
      assert(candidate?.targetRef,'Discover fixture from bounded scene, not a coordinate input');
      const discovered=await targetBlock();discovered.targetRef=candidate.targetRef;assert(discovered.targetRef?.startsWith('world:'));validateModel(discovered,'block');
      const aimed=await operation([step(discovered.targetRef)]);assert.equal(aimed.state,'completed',JSON.stringify(aimed));
      const used=await operation([{...step(discovered.targetRef),action:'use'}]);assert.equal(used.state,'completed',JSON.stringify(used));
      const usedResult=used.result.steps[0].result;assert.equal(usedResult.directMutationUsed,false);assert.equal(usedResult.evidenceAuthority,'PLAYTEST');assert.equal(usedResult.effectConfirmed,false);assert.equal(usedResult.inputReleased,true);
      const end=Date.now()+4000;let after;
      do{after=await targetBlock();if(after.state!==discovered.state)break;await delay();}while(Date.now()<end);
      assert.notEqual(after.state,discovered.state,'Native use must change note block state');
      const afterUse=after.state;
      const stale=await operation([step(discovered.targetRef)]);assert.equal(stale.state,'failed');assert.equal(stale.errorCode,'WORLD_TARGET_STALE',JSON.stringify(stale));
      const active=await api('POST','/v0/pipelines',{steps:[{type:'delay',durationMs:5000},{...step(after.targetRef),action:'attack'}],timeoutMs:10000});
      await api('DELETE','/v0/operations/'+active.operationId);
      assert.equal((await targetBlock()).block,'minecraft:note_block');
      const attacked=await operation([{...step((await targetBlock()).targetRef),action:'attack'}]);
      assert.equal(attacked.state,'completed',JSON.stringify(attacked));
      const finish=Date.now()+4000;
      do{after=await targetBlock();if(after.block==='minecraft:air')break;await delay();}while(Date.now()<finish);
      assert.equal(after.block,'minecraft:air','Native creative attack must remove fixture');
      assert.equal((await api('POST','/v0/wait/until',{condition:{type:'block',...pos,blockId:'minecraft:air'},timeoutMs:3000})).passed,true);
      actionSamples.push({arrangeAuthority:'diagnostic_admin_command_and_DEBUG_PRIVILEGED',actAuthority:'PLAYTEST',pos,beforeUse:discovered.state,afterUse,afterAttack:after.block,useResult:usedResult,attackResult:attacked.result.steps[0].result,staleAfterUse:stale.errorCode,sameStateReplacement:replaced.errorCode,occluded:blocked.errorCode,cancelledOperation:active.operationId,effectConfirmedBy:'USER client block read and Wait, not input response'});
      const input=await api('GET','/v0/input/state');assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);
    } finally {
      if(lease)await release();
      await mode('OPERATE');await arrange('minecraft:air');await arrange('minecraft:air',wall);
      await api('POST','/v0/debug/disarm').catch(()=>{});debugArm=undefined;await mode('READ');
    }
  });
  await worldHeldActions({api,mode,acquire,release,key,wait,check,actionSamples,setArm:value=>{debugArm=value;}});
  await worldBlockStateActions({api,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples,setArm:value=>{debugArm=value;},repo,instance,target});
  await check('DEBUG full precision and loaded/unloaded semantics remain separate',async()=>{
    const player=await api('GET','/v0/player?category=DEBUG');assert.equal(player.available,true);assert.equal(typeof player.x,'number');
    const block=await api('GET',`/v0/world/block?category=DEBUG&x=${Math.floor(player.x)}&y=${Math.floor(player.y)-1}&z=${Math.floor(player.z)}`);assert.equal(block.dataCategory,'DEBUG');
    const remote=await api('GET','/v0/world/block?category=DEBUG&x=1000000&y=64&z=1000000');assert.equal(remote.available,false);assert.equal(remote.chunkLoadRequested,false);
    for(const path of ['/v0/server/player?category=DEBUG','/v0/world/entities?category=DEBUG','/v0/server/world/entities?category=DEBUG'])assert.equal((await api('GET',path)).dataCategory,'DEBUG');
  });
  await check('READ input denied and held input zero',async()=>{
    await api('POST','/v0/input/key',{key:87,scanCode:0,action:1,modifiers:0},'TAKEOVER_REQUIRED');const state=await api('GET','/v0/input/state');assert.equal(state.pressedKeyCount,0);assert.equal(state.pressedButtonCount,0);
  });
  await check('Provider and State Frame apply category before internal lookup or capture',async()=>{
    await api('POST','/v0/providers/read',{providerId:'minecraft:server/player'},'WORLD_USER_PERCEPTION_UNAVAILABLE');
    await api('POST','/v0/providers/read',{providerId:'unregistered:hidden'},'WORLD_USER_PERCEPTION_UNAVAILABLE');
    await api('POST','/v0/state/frames',{reads:[{providerId:'minecraft:client/player'},{providerId:'minecraft:server/player'}]},'WORLD_USER_PERCEPTION_UNAVAILABLE');
    const frame=await api('POST','/v0/state/frames',{reads:[{providerId:'minecraft:client/player'}]});
    assert.equal(frame.dataCategory,'USER');assert.equal(frame.reads[0].data.dataCategory,'USER');assert.equal(typeof frame.reads[0].data.x,'number');
    const diagnostic=await api('POST','/v0/state/frames',{category:'DEBUG',reads:[{providerId:'minecraft:client/player'},{providerId:'minecraft:server/player'}]});
    assert.equal(diagnostic.dataCategory,'DEBUG');assert.equal(typeof diagnostic.reads[0].data.x,'number');
  });
  await check('USER conditions accept public client fields but reject server/internal sources',async()=>{
    await api('POST','/v0/assert',{condition:{type:'block',x:0.5,y:64,z:0}},'INVALID_WORLD_USER_QUERY');
    await api('POST','/v0/wait/until',{condition:{type:'entity',radius:-1},timeoutMs:300},'INVALID_WORLD_USER_QUERY');
    await api('POST','/v0/assert',{condition:{type:'entity',uuid:'private-entity-reference'}},'WORLD_USER_FIELD_UNAVAILABLE');
    assert.equal((await api('POST','/v0/assert',{condition:{type:'player',healthMin:1}})).passed,true);
    assert.equal((await api('POST','/v0/assert',{condition:{type:'entity',radius:0,minCount:0}})).passed,true);
    await api('POST','/v0/assert',{condition:{type:'provider',providerId:'minecraft:server/player'}},'WORLD_USER_PERCEPTION_UNAVAILABLE');
    const result=await api('POST','/v0/assert',{condition:{type:'player',category:'DEBUG',expected:{available:true}}});
    assert.equal(result.dataCategory,'DEBUG');assert.equal(result.passed,true);
    const scene=await api('POST','/v0/wait/until',{condition:{type:'scene',expected:{available:true}},timeoutMs:3000});
    assert.equal(scene.dataCategory,'USER');assert.equal(scene.passed,true);
  });
  await check('Deep internal reads require explicit DEBUG without a write Arm or Lease',async()=>{
    await api('POST','/v0/observe/deep',{perspective:'client_known',domains:['player']},'WORLD_USER_PERCEPTION_UNAVAILABLE');
    const internal=await api('POST','/v0/observe/deep',{category:'DEBUG',perspective:'client_known',domains:['player']});
    assert.equal(internal.dataCategory,'DEBUG');assert.equal(internal.domain,'WORLD');
  });
  await check('USER resync and recording never launder DEBUG snapshots',async()=>{
    const user=await api('GET','/v0/events/resync');assert.equal(user.dataCategory,'USER');assert.equal(typeof user.player.x,'number');assert(!('operations' in user));
    const debug=await api('GET','/v0/events/resync?category=DEBUG');assert.equal(debug.dataCategory,'DEBUG');assert.equal(typeof debug.player.x,'number');
    await api('POST','/v0/recordings',{captureFrames:false,stateReads:[{providerId:'minecraft:server/player'}]},'WORLD_USER_PERCEPTION_UNAVAILABLE');
    const recording=await api('POST','/v0/recordings',{captureFrames:false,intervalMs:100,maxSamples:2,stateReads:[{providerId:'minecraft:client/player'}],contactSheet:{enabled:false}});
    let status;const deadline=Date.now()+15000;
    do{status=await api('GET','/v0/recordings/'+recording.recordingId);if(status.artifactReady)break;assert.notEqual(status.status,'failed');await delay();}while(Date.now()<deadline);
    assert(status.artifactReady,JSON.stringify(status));assert.equal(status.dataCategory,'USER');
    const response=await connection.fetch('/v0/recordings/'+recording.recordingId+'/artifact',{signal:AbortSignal.timeout(25000)});assert(response.ok);
    const artifact=resolve(instance,'world-user-evidence.zip');await writeFile(artifact,new Uint8Array(await response.arrayBuffer()));
    const checked=JSON.parse(execFileSync('pwsh',['-NoProfile','-File',resolve(import.meta.dirname,'Test-WorldReadArtifact.ps1'),'-Artifact',artifact],{cwd:repo,encoding:'utf8'}));
    assert.equal(checked.status,'PASS');assert(checked.structuredStates>0);
  });
  if(validation==='migration-runtime_core') await check('shared Provider Capture Recording and Event bridge',async()=>{
    const providers=await api('GET','/v0/providers');
    const echo=providers.providers.find(value=>value.id?.endsWith(':echo'));
    assert(echo,'registered product echo Provider');
    const reply=await api('POST','/v0/providers/read',{providerId:echo.id,category:'DEBUG',query:{marker:'runtime_core'}});
    assert.equal(reply.data.echo.marker,'runtime_core');
    const capture=await connection.fetch('/v0/capture',{signal:AbortSignal.timeout(25000)});
    assert(capture.ok);const bytes=new Uint8Array(await capture.arrayBuffer());
    assert(bytes.length<16*1024*1024);assert.deepEqual([...bytes.slice(0,8)],[137,80,78,71,13,10,26,10]);
    await api('GET','/v0/events/resync');
    const recording=await api('POST','/v0/recordings',{intervalMs:250,durationMs:1000,maxSamples:4,stateReads:[],contactSheet:{enabled:true}});
    let status;const end=Date.now()+30000;
    do{status=await api('GET','/v0/recordings/'+recording.recordingId);if(status.artifactReady)break;assert.notEqual(status.status,'failed',JSON.stringify(status));await delay();}while(Date.now()<end);
    assert(status.artifactReady,JSON.stringify(status));assert(status.writtenFrames>0,JSON.stringify(status));
    const artifact=await connection.fetch('/v0/recordings/'+recording.recordingId+'/artifact',{signal:AbortSignal.timeout(25000)});
    assert(artifact.ok);const bundle=new Uint8Array(await artifact.arrayBuffer());assert(bundle.length<16*1024*1024);assert.equal(bundle[0],80);assert.equal(bundle[1],75);
  });
  await consumerPriorityActions({api,connection,repo,instance,target,mode,acquire,release,key,click,wait,nodes,check,actionSamples,setArm:value=>{debugArm=value;}});
  await consumerCommandFeedback({api,repo,instance,target,acquire,release,click,wait,nodes,check,actionSamples});
  await consumerWorldReadiness({api,acquire,release,check,actionSamples});
  await consumerPlayerFlags({api,repo,instance,target,acquire,release,check,actionSamples});
  await consumerHitFeedback({api,repo,instance,target,acquire,release,leaseId:()=>lease,check,actionSamples});
  await consumerRateActions({api,connection,repo,instance,acquire,release,leaseHeaders:()=>({'x-mcp-control-lease':lease}),check,actionSamples});
  await check('Actual MCP stdio world/UI consumers and correctly classified Debug batch waits',async()=>{
    const worldConsumers=await worldReadConsumers(repo,base,resolve(instance,'minecraft-protocol/token'),target);
    assert.equal(worldConsumers.length,9);assert.equal(commandConsumerChecks.length,4);
    consumerChecks=[...commandConsumerChecks,...worldConsumers];
  });
  }
} catch(error) { failure=error;console.error(error);try{const tree=await api('GET','/v0/ui/tree');checks.push({name:'failure context',screen:tree.screenClass,presentation:tree.presentation,width:tree.width,height:tree.height,windowWidth:tree.windowWidth,windowHeight:tree.windowHeight,visibleNodes:nodes(tree).filter(node=>node.visible!==false).map(node=>({role:node.role,label:node.label,item:node.item,controlKey:node.controlKey,translationKey:node.translationKey}))});}catch{} }
finally {
  clearInterval(renew);
  try { await shutdown(); } catch(error) {failure??=error;console.error(error);} finally { clearInterval(renew); }
  const after=await sources();const sourceChangedDuringRun=JSON.stringify(before)!==JSON.stringify(after);if(sourceChangedDuringRun)failure??=new Error('Sources changed during Runtime test');
  const directory=resolve(repo,'validations',validation,'result');await mkdir(directory,{recursive:true});
  await writeFile(resolve(directory,target+(scenario==='full'?'':'-'+scenario)+'.json'),JSON.stringify({status:failure?'FAIL':'PASS',target,scenario,fullSmoke:scenario==='full',runtimeConnection:{runtimeInstanceId:connection.instanceId,port:connection.port,pid:connection.pid,discovery:'instance_directory'},testedAt:new Date().toISOString(),testedCommit:execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).trim(),dirty:true,evidence:'development_runtime_native',fixturePolicy:fixtureInput?.policy??'bootstrap_preparation_not_fixed_copy',fixtureInput,arrange:scenario==='rate-limit'?'readonly_queries_and_delay_only_pipelines_at_title':'native_gui_and_diagnostic_admin_commands_and_authorized_debug',checks,sceneSamples,actionSamples,consumerChecks,sourceHashes:after,sourceChangedDuringRun,persistentWriteCalls:0,fullWorldMilestone:false,...(failure?{error:String(failure)}:{})},null,2)+'\n');
}
if(failure)process.exitCode=1;
