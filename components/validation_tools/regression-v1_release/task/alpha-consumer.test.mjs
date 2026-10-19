import nodeTest from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {resolve,relative,dirname,basename} from 'node:path';
import {fileURLToPath} from 'node:url';
import {tmpdir} from 'node:os';
import {consumerPriorityProof,consumerFeedbackProof,uiRegressionProof,collaborationIncrementProof,continuousInputProof,canonicalSourceHashes,CONTINUOUS_PROFILE,collectContinuousGameplayProof,verifyContinuousPublication,inputContextProof,regionStreamProof,placementReceiptsProof,continuousPerformanceProof,interactionBaselineProof} from './alpha-evidence.mjs';
import {baselineStatistics} from '../../conformance-world_read/task/interaction-baseline-actions.mjs';
import {validatePlayerStream,decodeRegionPage} from '../../conformance-world_read/task/region-stream-actions.mjs';
import {placementReceiptCases} from '../../conformance-world_read/task/world-placement-receipts-actions.mjs';
import {performanceAttackSteps,performanceAttackComparison} from '../../conformance-world_read/task/continuous-performance-actions.mjs';
import {verifyEvidenceSources,candidateBindingRequired} from './alpha-evidence.mjs';

const publicationFixtureMode=process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)&&process.argv[2]==='--write-publication-fixture';
// The explicit fixture CLI does not register tests or emit TAP. Production never imports this file.
const test=publicationFixtureMode?()=>{}:nodeTest;

test('publication checks actual production and shared runner bytes, excluding only an unexecuted placement branch',async()=>{
 const repo=resolve(import.meta.dirname,'../../../..');
 const product='components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core/ProtocolState.java';
 const runner='components/validation_tools/conformance-world_read/task/world-read-smoke.mjs';
 const placement='components/validation_tools/conformance-world_read/task/world-placement-receipts-actions.mjs';
 const sourceHashes={};for(const path of [product,runner,placement])sourceHashes[path]=createHash('sha256').update(await readFile(resolve(repo,path))).digest('hex');
 const stale='0'.repeat(64);
 for(const scenario of ['full','region-stream','continuous-performance','interaction-baseline','mode-collaboration']){
  const result={scenario,sourceHashes:{...sourceHashes,[placement]:stale}};
  const checked=await verifyEvidenceSources(repo,result);assert.equal(checked[product],sourceHashes[product]);assert(!Object.hasOwn(checked,placement));assert.equal(result.sourceHashes[placement],stale);
  for(const changed of [product,runner])await assert.rejects(verifyEvidenceSources(repo,{scenario,sourceHashes:{...sourceHashes,[changed]:stale}}),/Stale evidence source/);
 }
 await assert.rejects(verifyEvidenceSources(repo,{scenario:'world-placement-receipts',sourceHashes:{...sourceHashes,[placement]:stale}}),/Stale evidence source/);
 await verifyEvidenceSources(repo,{scenario:'world-placement-receipts',sourceHashes});
});

test('UI proof requires its actual producer domain while full-world proof still requires release metadata',async()=>{
 const repo=resolve(import.meta.dirname,'../../../..'),target='1.20.1-forge';
 const producer=await readFile(resolve(repo,'components/validation_tools/conformance-vanilla_ui/task/run-vanilla-ui.mjs'),'utf8');
 assert(producer.includes('/\\.java$/.test(entry.name)||/mixins\\.json$/.test(entry.name)'));
 for(const path of ['components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core/ProtocolState.java',`versions/${target}/target.properties`]){await readFile(resolve(repo,path));assert(candidateBindingRequired(path,target,'uiRegression'));}
 const metadata=`versions/${target}/src/main/resources/META-INF/mods.toml`;await readFile(resolve(repo,metadata));
 assert.equal(candidateBindingRequired(metadata,target,'uiRegression'),false);assert.equal(candidateBindingRequired(metadata,target,'nativeSmoke'),true);assert.equal(candidateBindingRequired(metadata,target,'continuousInput'),true);
});

test('alpha canonicalizes an actual producer file binding from either Windows or portable paths without changing its digest',async()=>{
 const repo=resolve(import.meta.dirname,'../../../..'),file=resolve(repo,'components/validation_tools/conformance-world_read/task/world-read-smoke.mjs');
 const portable=relative(repo,file).replaceAll('\\','/'),windows=portable.replaceAll('/','\\');
 const hash=createHash('sha256').update(await readFile(file)).digest('hex');
 for(const path of [windows,portable]){const bindings=Object.freeze({[path]:hash}),normalized=canonicalSourceHashes(bindings);assert.deepEqual(Object.keys(normalized),[portable]);assert.equal(normalized[portable],hash);assert.deepEqual(Object.keys(bindings),[path]);}
});
test('alpha path canonicalization never admits duplicate ambiguous paths unsafe traversal or an invalid digest',()=>{
 const hash='a'.repeat(64);
 for(const path of ['../file','./file','/file','C:\\file','dir//file','dir\\..\\file'])assert.throws(()=>canonicalSourceHashes({[path]:hash}),/Unsafe source binding/);
 for(const other of [hash,'b'.repeat(64)])assert.throws(()=>canonicalSourceHashes({'dir\\file':hash,'dir/file':other}),/Duplicate canonical/);
 for(const invalid of ['','A'.repeat(64),'a'.repeat(63),{},null])assert.throws(()=>canonicalSourceHashes({'dir/file':invalid}),/Invalid source binding hash/);
 for(const invalid of [null,[],'not an object'])assert.throws(()=>canonicalSourceHashes(invalid),/must be an object/);
});

function validContinuousInput(){
 const input=(sequence=0,generation=1,key)=>({pressedKeyCount:key===undefined?0:1,pressedButtonCount:0,pressedKeys:key===undefined?[]:[key],inputDispatchSequence:sequence,inputGeneration:generation,inputCleanupPending:false,inputCleanupFailed:false});
 const released=()=>({passed:true,condition:{type:'input.released'},evidence:{pressedKeyCount:0,pressedButtonCount:0}});
 const timing=()=>({scope:'automation_pipeline_not_http_or_server_effect',timeSource:'monotonic_elapsed',stages:{cleanup:{calls:1,elapsedMillis:1}}});
 const operation=(id,types=['key'],cleanupOnComplete=false)=>({operationId:id,state:'completed',result:{cleanupOnComplete,cleanup:{releasedKeys:1},timing:timing(),steps:types.map(stepType=>({stepType,result:{inputOrigin:'AGENT_ROUTED',entryLayer:'GAME_ROUTED_RAW',hostCursorUsed:false,directMutationUsed:false}}))}});
 const stop=()=>({type:'input.release_all',inputGeneration:2,releasedKeys:0,releasedButtons:0});
 const active={operationId:'active'},queued=[{operationId:'queued-1'},{operationId:'queued-2'}],attack=operation('attack',Array(6).fill('mouse.button'),true);
 const player=x=>({x,z:0,dataCategory:'USER',authority:'CLIENT_KNOWN'}),modeVersion={generation:1};
 return{target:'fixture-target',scenario:'continuous-input',status:'PASS',evidence:'development_runtime_native',fullSmoke:false,fullWorldMilestone:false,cleanShutdown:true,sourceChangedDuringRun:false,persistentWriteCalls:0,fixturePolicy:'immutable_baseline_restored',checks:Array.from({length:10},(_,i)=>({name:'continuous check '+i,status:'PASS'})),actionSamples:[
  {case:'continuous_read_denied',denied:{error:'TAKEOVER_REQUIRED'},waited:released(),asserted:released(),before:input(),after:input()},
  {case:'continuous_semantic_world_entry',worldName:'World-Read-Alpha',nodeId:'fresh-world-row',coordinateFallbacks:0},
  {case:'continuous_preserved_w',down:operation('w-down'),held:input(1,1,87),looks:[3,-6,3].map((dx,i)=>({outcome:operation('look-'+i,['mouse.delta']),held:input(i+2,1,87)})),before:player(0),after:player(1),observedHorizontalDistance:1,serverAcceptanceConfirmed:false,actAuthority:'PLAYTEST'},
  {case:'continuous_explicit_up',up:operation('w-up'),waited:released(),after:input(5)},
  {case:'continuous_default_cleanup',down:operation('prior-w'),held:input(6,1,87),completed:operation('default-cleanup',['delay'],true),waited:released(),after:input(7)},
  {case:'continuous_release_all',active,queued,held:input(8,1,340),queuedState:input(8,1,340),activeBefore:{status:'running',state:'executing'},stopped:stop(),stopRttMillis:20,outcomes:[active,...queued].map(row=>({...row,state:'cancelled'})),waited:released(),after:input(10,2),later:input(10,2),cleanupBarrierConfirmedBy:'release_all_response',negativeObservationMillis:500,leasePreserved:true,modeVersionPreserved:true,physicalEscTested:false},
  {case:'continuous_after_stop',leaseId:'same-lease',completed:operation('fresh',['key'],true),after:input(13,2)},
  {case:'continuous_attack_pipeline',completed:attack,timing:timing(),observedTerminalMillis:300,after:input(20,2),ordinaryButtonPairs:3,cleanupCalls:1,attackHitConfirmed:false,serverAcceptanceConfirmed:false,latencyRatioClaimed:false,actAuthority:'PLAYTEST'},
  {case:'continuous_lease_expiry',renewed:{leaseId:'same-lease'},heldOperation:{operationId:'expiring'},held:input(21,2,340),expired:{mode:'READ',takeoverActive:false,reconsentRequired:false,modeTransitionReason:'lease_expired'},waited:released(),after:input(22,3),status:{status:'available'},localLeaseForgottenOnlyAfterConfirmedExpiry:true},
  {case:'continuous_mcp_release_inputs',transport:'real_mcp_stdio',initializeServerInfo:{name:'minecraft-protocol-companion',version:'fixture-version'},expectedPackageVersion:'fixture-version',acquired:{leaseId:'mcp-lease'},cached:{leaseId:'mcp-lease'},down:operation('mcp-down'),held:input(23,3,340),stopped:stop(),waited:released(),asserted:released(),before:{mode:'TAKEOVER',modeVersion},after:{mode:'TAKEOVER',modeVersion,reconsentRequired:false},fresh:operation('mcp-fresh',['key'],true),returned:{mode:'READ'},cachedLeasePreserved:true,leaseReturnedBy:'owning_native_runner'},
  {case:'continuous_input_complete',target:'fixture-target',elapsedMillis:20000,maximumMillis:180000,checks:10,minimumPipelinePostIntervalMillis:550,finalInput:input(30,3),physicalEscTested:false,networkDisconnectTested:false,stateStreamTested:false,nativeWorldEpochCrossServerGuarantee:false,cleanupBarrierNotInferredFromHeldCounts:true,clientShutdownOwnedByOuterRunner:true,persistentWriteCalls:0}
 ]};
}

test('continuous input alpha requires actual cross-request held, strong-stop, TTL and MCP samples without claiming broader delivery',()=>{
 const proof=continuousInputProof(validContinuousInput(),'fixture-target');
 assert.equal(proof.checks,10);assert.equal(proof.strongStopActiveAndQueued,true);assert.equal(proof.realMcpReleaseInputs,true);assert.equal(proof.leaseExpiryCleanup,true);
 for(const field of ['physicalEscTested','networkDisconnectTested','stateStreamTested','nativeWorldEpochCrossServerGuarantee','serverAcceptanceConfirmed','attackHitConfirmed','latencyRatioClaimed'])assert.equal(proof[field],false);
 for(const row of validContinuousInput().actionSamples){const result=validContinuousInput();result.actionSamples=result.actionSamples.filter(value=>value.case!==row.case);assert.throws(()=>continuousInputProof(result,'fixture-target'),row.case);}
 for(const patch of [{target:'other'},{scenario:'full'},{status:'FAIL'},{evidence:'historical'},{fullSmoke:true},{fullWorldMilestone:true},{cleanShutdown:false},{sourceChangedDuringRun:true},{persistentWriteCalls:1},{fixturePolicy:'unrestored'},{checks:[]},{checks:Array.from({length:9},()=>({name:'continuous partial',status:'PASS'}))}])assert.throws(()=>continuousInputProof({...validContinuousInput(),...patch},'fixture-target'));
 const duplicate=validContinuousInput();duplicate.actionSamples.push(structuredClone(duplicate.actionSamples[0]));assert.throws(()=>continuousInputProof(duplicate,'fixture-target'));
 const dirty=validContinuousInput();dirty.actionSamples.push({case:'continuous_cleanup_failure',error:'cleanup unavailable'});assert.throws(()=>continuousInputProof(dirty,'fixture-target'));
});

test('continuous alpha rejects late input, false cleanup, lost Lease, incomplete operations and broad authority claims',()=>{
 for(const [name,change]of [
  ['continuous_read_denied',row=>{row.after.inputDispatchSequence++;}],['continuous_read_denied',row=>{row.after.inputGeneration++;}],['continuous_read_denied',row=>{row.denied.error='SCOPE_DENIED';}],
  ['continuous_semantic_world_entry',row=>{row.coordinateFallbacks=1;}],
  ['continuous_preserved_w',row=>{row.looks.pop();}],['continuous_preserved_w',row=>{row.looks[0].held.pressedKeys=[];}],['continuous_preserved_w',row=>{row.down.result.cleanupOnComplete=true;}],
  ['continuous_preserved_w',row=>{row.looks[1].outcome.result.steps[0].result.hostCursorUsed=true;}],['continuous_preserved_w',row=>{row.after.x=0;}],['continuous_preserved_w',row=>{row.serverAcceptanceConfirmed=true;}],
  ['continuous_explicit_up',row=>{row.after.pressedKeyCount=1;}],['continuous_explicit_up',row=>{row.waited.condition.category='USER';}],
  ['continuous_default_cleanup',row=>{row.completed.result.cleanupOnComplete=false;}],['continuous_default_cleanup',row=>{row.completed.result.cleanup.releasedKeys=0;}],['continuous_default_cleanup',row=>{row.completed.result.timing.stages.cleanup.calls=2;}],
  ['continuous_release_all',row=>{row.queued.pop();}],['continuous_release_all',row=>{row.outcomes[1].state='executing';}],['continuous_release_all',row=>{row.outcomes[1].operationId='different-operation';}],
  ['continuous_release_all',row=>{row.queuedState.inputDispatchSequence++;}],['continuous_release_all',row=>{row.stopped.inputGeneration=row.queuedState.inputGeneration;row.after.inputGeneration=row.stopped.inputGeneration;}],
  ['continuous_release_all',row=>{row.stopped.releasedKeys=-1;}],['continuous_release_all',row=>{row.after.inputCleanupPending=true;}],['continuous_release_all',row=>{row.after.inputCleanupFailed=true;}],
  ['continuous_release_all',row=>{row.later.inputDispatchSequence++;}],['continuous_release_all',row=>{row.leasePreserved=false;}],['continuous_release_all',row=>{row.modeVersionPreserved=false;}],
  ['continuous_release_all',row=>{row.cleanupBarrierConfirmedBy='held_counts_only';}],['continuous_release_all',row=>{row.negativeObservationMillis=100;}],['continuous_release_all',row=>{row.physicalEscTested=true;}],
  ['continuous_after_stop',row=>{row.completed.state='executing';}],['continuous_after_stop',row=>{row.after.inputDispatchSequence=10;}],
  ['continuous_attack_pipeline',row=>{row.completed.result.steps.pop();}],['continuous_attack_pipeline',row=>{row.timing.stages.cleanup.calls=2;}],['continuous_attack_pipeline',row=>{row.attackHitConfirmed=true;}],['continuous_attack_pipeline',row=>{row.latencyRatioClaimed=true;}],
  ['continuous_lease_expiry',row=>{row.expired.mode='TAKEOVER';}],['continuous_lease_expiry',row=>{row.expired.modeTransitionReason='manual_revocation';}],['continuous_lease_expiry',row=>{row.expired.reconsentRequired=true;}],['continuous_lease_expiry',row=>{row.after.inputCleanupPending=true;}],['continuous_lease_expiry',row=>{row.status.status='held';}],
  ['continuous_mcp_release_inputs',row=>{row.transport='mock';}],['continuous_mcp_release_inputs',row=>{row.initializeServerInfo.version='old-phase9c';}],['continuous_mcp_release_inputs',row=>{row.cached.leaseId='replacement';}],
  ['continuous_mcp_release_inputs',row=>{row.after.modeVersion={generation:2};}],['continuous_mcp_release_inputs',row=>{row.after.reconsentRequired=true;}],['continuous_mcp_release_inputs',row=>{row.fresh.state='running';}],
  ['continuous_input_complete',row=>{row.elapsedMillis=180001;}],['continuous_input_complete',row=>{row.stateStreamTested=true;}],['continuous_input_complete',row=>{row.networkDisconnectTested=true;}],
  ['continuous_input_complete',row=>{row.nativeWorldEpochCrossServerGuarantee=true;}],['continuous_input_complete',row=>{row.cleanupBarrierNotInferredFromHeldCounts=false;}],['continuous_input_complete',row=>{row.finalInput.inputCleanupFailed=true;}]
 ]){const result=validContinuousInput();change(result.actionSamples.find(row=>row.case===name));assert.throws(()=>continuousInputProof(result,'fixture-target'),name);}
});

test('alpha collaboration proof requires actual per-item/MCP samples and rejects partial or dirty runs',()=>{
 const result={target:'fixture',scenario:'command-discovery-batch',status:'PASS',cleanShutdown:true,sourceChangedDuringRun:false,persistentWriteCalls:0,fixturePolicy:'immutable_baseline_restored',checks:Array.from({length:8},()=>({status:'PASS'})),actionSamples:[
  {case:'native_command_batch',batch:{submittedCount:3,requiresControlLease:false,inputDispatched:false,effectConfirmed:false,serverFeedbackCorrelated:false}},
  {case:'native_batch_boundary',boundary:{commandCount:32,submittedCount:32}},
  {case:'native_batch_stops_on_first_error',result:{commands:[{}, {}, {status:'not_attempted'}]}},
  {case:'mcp_command_discovery_batch'}
 ]};
 assert.equal(collaborationIncrementProof(result,'fixture','command-discovery-batch').checks,8);
 for(const change of [{status:'FAIL'},{sourceChangedDuringRun:true},{cleanShutdown:false},{actionSamples:result.actionSamples.slice(0,-1)}])
  assert.throws(()=>collaborationIncrementProof({...result,...change},'fixture','command-discovery-batch'));
 const boundary=structuredClone(result);boundary.actionSamples[1].boundary.submittedCount=31;
 assert.throws(()=>collaborationIncrementProof(boundary,'fixture','command-discovery-batch'));
});

function validServerCompletion(){
 const server=()=>({source:'current_player_server_command_suggestions',completionSource:'SERVER',completionScope:'server_current_player',dataCategory:'USER',serverCompletionRequested:true,serverCompletionSupported:true,serverAcceptanceConfirmed:false,commandExecuted:false,inputDispatched:false,requiresControlLease:false,humanFutureModified:false,humanRequestsHavePriority:true,serverBudgetShared:true,maxAgentCompletionsInFlight:1,suggestions:[{text:'help',start:0,end:2}]});
 return{target:'fixture-target',scenario:'server-completion',status:'PASS',cleanShutdown:true,sourceChangedDuringRun:false,persistentWriteCalls:0,fixturePolicy:'immutable_baseline_restored',checks:Array.from({length:8},()=>({status:'PASS'})),actionSamples:[
  {case:'server_completion_local_default',local:{source:'current_player_native_command_tree',completionScope:'client_local_only',serverCompletionRequested:false,commandExecuted:false,inputDispatched:false,requiresControlLease:false,suggestions:[{text:'help',start:0,end:2}]}},
  {case:'server_completion_actual_reply',result:server()},
  {case:'server_completion_bounded',result:{...server(),suggestionCount:12,suggestions:[{text:'advancement',start:0,end:0}],suggestionsTruncated:true,completionComplete:false,nativeServerMayLimitSuggestions:true}},
  {case:'server_completion_concurrency',values:[{status:200,data:server()},{status:409,data:{error:'PLAYER_COMMAND_COMPLETION_BUSY'}}]},
  {case:'server_completion_preserves_chat',result:server(),before:[{value:'server completion unsent draft',focused:true}],after:[{value:'server completion unsent draft',focused:true}]},
  {case:'mcp_server_completion',target:'fixture-target',result:server()}
 ]};
}

test('server completion alpha requires all six Native and actual MCP samples and retains shared-budget limitations',()=>{
 const proof=collaborationIncrementProof(validServerCompletion(),'fixture-target','server-completion');
 assert.equal(proof.checks,8);assert.equal(proof.serverCompletion,true);assert.equal(proof.serverBudgetShared,true);assert.equal(proof.humanCompletionConcurrentLiveTested,false);assert.equal(proof.physicalEscTested,false);
 for(const sample of validServerCompletion().actionSamples){const result=validServerCompletion();result.actionSamples=result.actionSamples.filter(row=>row.case!==sample.case);assert.throws(()=>collaborationIncrementProof(result,'fixture-target','server-completion'),sample.case);}
 const limited=validServerCompletion();limited.actionSamples.find(row=>row.case==='server_completion_concurrency').values[1]={status:429,data:{error:'PLAYER_COMMAND_COMPLETION_RATE_LIMITED'}};assert.equal(collaborationIncrementProof(limited,'fixture-target','server-completion').checks,8);
 for(const patch of [{checks:[]},{checks:Array.from({length:7},()=>({status:'PASS'}))},{cleanShutdown:false},{sourceChangedDuringRun:true},{persistentWriteCalls:1},{fixturePolicy:'unrestored'}])assert.throws(()=>collaborationIncrementProof({...validServerCompletion(),...patch},'fixture-target','server-completion'));
});

test('server completion alpha refuses false source authority no-input budgets and Chat-preservation claims',()=>{
 for(const name of ['server_completion_actual_reply','server_completion_bounded','server_completion_preserves_chat','mcp_server_completion'])for(const [field,value]of [
  ['source','current_player_native_command_tree'],['completionSource','CLIENT_LOCAL'],['completionScope','server_internal'],['dataCategory','DEBUG'],
  ['serverCompletionRequested',false],['serverCompletionSupported',false],['serverAcceptanceConfirmed',true],['commandExecuted',true],['inputDispatched',true],['requiresControlLease',true],
  ['humanFutureModified',true],['humanRequestsHavePriority',false],['serverBudgetShared',false],['maxAgentCompletionsInFlight',2]
 ]){const result=validServerCompletion();result.actionSamples.find(row=>row.case===name).result[field]=value;assert.throws(()=>collaborationIncrementProof(result,'fixture-target','server-completion'),name+' '+field);}
 for(const [name,change]of [
  ['server_completion_local_default',row=>{row.local.completionScope='server_current_player';}],['server_completion_local_default',row=>{row.local.serverCompletionRequested=true;}],
  ['server_completion_local_default',row=>{row.local.inputDispatched=true;}],['server_completion_local_default',row=>{row.local.suggestions=[];}],
  ['server_completion_actual_reply',row=>{row.result.suggestions[0].end=3;}],
  ['server_completion_bounded',row=>{row.result.suggestionCount=1;}],['server_completion_bounded',row=>{row.result.suggestions.push({text:'help'});}],
  ['server_completion_bounded',row=>{row.result.suggestionsTruncated=false;}],['server_completion_bounded',row=>{row.result.completionComplete=true;}],['server_completion_bounded',row=>{row.result.nativeServerMayLimitSuggestions=false;}],
  ['server_completion_concurrency',row=>{row.values[1]=structuredClone(row.values[0]);}],['server_completion_concurrency',row=>{row.values[1].data.error='RATE_LIMITED';}],
  ['server_completion_concurrency',row=>{row.values[1].status=429;}],['server_completion_concurrency',row=>{row.values[0].data.requiresControlLease=true;}],
  ['server_completion_preserves_chat',row=>{row.after[0].value='lost draft';}],['server_completion_preserves_chat',row=>{row.after[0].focused=false;}],
  ['server_completion_preserves_chat',row=>{row.before=[];row.after=[];}],['server_completion_preserves_chat',row=>{row.before[0].value='';row.after[0].value='';}],
  ['mcp_server_completion',row=>{row.target='other-target';}],['mcp_server_completion',row=>{row.result.suggestions=[];}]
 ]){const result=validServerCompletion();change(result.actionSamples.find(row=>row.case===name));assert.throws(()=>collaborationIncrementProof(result,'fixture-target','server-completion'),name);}
 const duplicate=validServerCompletion();duplicate.actionSamples.push(structuredClone(duplicate.actionSamples[0]));assert.throws(()=>collaborationIncrementProof(duplicate,'fixture-target','server-completion'));
});

function validResourceVersion(){
 const operations=['player.health.set','player.attribute.set','entity.no_gravity.set','block_entity.custom_name.set','menu.slot.set'];
 const input={inputDispatchSequence:10,pressedKeyCount:0,pressedButtonCount:0};
 const chain=(operation,consumer='native_http')=>({case:'resource_version_typed_chain',operation,consumer,authority:'DEBUG_PRIVILEGED',inspectionSource:'formal_deep_observation',original:{revision:1},applied:{beforeResourceVersion:{revision:1},afterResourceVersion:{revision:2},gameplayEvidence:false,evidenceContaminated:true,storageAccessed:false},stale:{[consumer==='native_http'?'error':'code']:'STALE_RESOURCE_REVISION',expectedRevision:1,actualRevision:2,reinspectRequired:true,retrySafe:false},reinspected:{revision:2},restored:{beforeResourceVersion:{revision:2},afterResourceVersion:{revision:3}},inputBefore:{...input},inputAfter:{...input},actInputDelta:0});
 return{target:'fixture-target',scenario:'resource-version',status:'PASS',cleanShutdown:true,sourceChangedDuringRun:false,persistentWriteCalls:0,fixturePolicy:'immutable_baseline_restored',checks:Array.from({length:14},()=>({status:'PASS'})),actionSamples:[
  {case:'resource_inspection_contract',contract:{operations:[...operations,'world.block.set'].map(operation=>({operation})),grantsMutationPermission:false}},
  {case:'resource_version_actual_mcp',error:{code:'STALE_RESOURCE_REVISION',actualRevision:2,expectedRevision:1,reinspectRequired:true,retrySafe:false}},
  {case:'safe_stale_reinspect'},
  {case:'resource_version_fixture_arrange',nativePauseObserved:true,ownerThreadReadsWhilePaused:true},
  ...operations.map(operation=>chain(operation)),
  {...chain('menu.slot.set','mcp_stdio'),case:'resource_version_menu_actual_mcp'},
  {case:'resource_version_extended_cleanup',entityRemoved:true,restoredReadMode:true,persistentWriteCalls:0,cleanupErrors:[]}
 ]};
}

test('resource alpha requires fourteen checks and all five formal typed chains plus the actual MCP Menu consumer',()=>{
 assert.equal(collaborationIncrementProof(validResourceVersion(),'fixture-target','resource-version').checks,14);
 for(const sample of validResourceVersion().actionSamples){
  const result=validResourceVersion();result.actionSamples=result.actionSamples.filter(row=>row.case!==sample.case||(row.case==='resource_version_typed_chain'&&row.operation!==sample.operation));
  assert.throws(()=>collaborationIncrementProof(result,'fixture-target','resource-version'),sample.operation??sample.case);
 }
 for(const patch of [{checks:Array.from({length:8},()=>({status:'PASS'}))},{checks:[]},{cleanShutdown:false},{sourceChangedDuringRun:true},{persistentWriteCalls:1}])assert.throws(()=>collaborationIncrementProof({...validResourceVersion(),...patch},'fixture-target','resource-version'));
});

test('resource alpha rejects forged recovery, stale counters, authority and input-cleanup summaries',()=>{
 for(const name of ['resource_version_typed_chain','resource_version_menu_actual_mcp'])for(const change of [
  row=>{row.consumer='invented_consumer';},row=>{row.authority='PLAYTEST';},row=>{row.actInputDelta=1;},
  row=>{row.inputBefore.pressedKeyCount=1;},row=>{row.inputAfter.pressedButtonCount=1;},row=>{row.inputAfter.inputDispatchSequence++;},
  row=>{row.applied.beforeResourceVersion.revision=0;},row=>{row.applied.afterResourceVersion.revision=1;},
  row=>{row.applied.gameplayEvidence=true;},row=>{row.applied.evidenceContaminated=false;},row=>{row.applied.storageAccessed=true;},
  row=>{row.stale.expectedRevision=0;},row=>{row.stale.actualRevision=3;},row=>{row.stale.reinspectRequired=false;},row=>{row.stale.retrySafe=true;},
  row=>{row.reinspected.revision='2';},row=>{row.restored.beforeResourceVersion.revision=0;},row=>{row.restored.afterResourceVersion.revision=2;},
  row=>{delete row.stale.error;delete row.stale.code;},row=>{delete row.inputAfter;},row=>{delete row.restored;}
 ]){const result=validResourceVersion();change(result.actionSamples.find(row=>row.case===name));assert.throws(()=>collaborationIncrementProof(result,'fixture-target','resource-version'),name);}
 for(const [name,change]of [
  ['resource_version_typed_chain',row=>{row.inspectionSource='legacy_direct_read';}],
  ['resource_version_typed_chain',row=>{row.operation='world.block.set';}],
  ['resource_version_menu_actual_mcp',row=>{row.operation='player.health.set';}],
  ['resource_version_fixture_arrange',row=>{row.nativePauseObserved=false;}],
  ['resource_version_extended_cleanup',row=>{row.entityRemoved=false;}],
  ['resource_version_extended_cleanup',row=>{row.restoredReadMode=false;}],
  ['resource_version_extended_cleanup',row=>{row.cleanupErrors=['unknown external state'];}],
  ['resource_version_extended_cleanup',row=>{row.persistentWriteCalls=1;}]
 ]){const result=validResourceVersion();change(result.actionSamples.find(row=>row.case===name));assert.throws(()=>collaborationIncrementProof(result,'fixture-target','resource-version'),name);}
});

function valid(){
 const player={dataCategory:'USER',authority:'CLIENT_KNOWN',x:0,y:80,z:0,yaw:0};
 return{target:'fixture-target',status:'PASS',scenario:'full',cleanShutdown:true,sourceChangedDuringRun:false,persistentWriteCalls:0,
  checks:Array.from({length:9},(_,i)=>({name:'consumer '+i,status:'PASS'})),actionSamples:[
   {case:'consumer_motion',before:player,after:{...player,z:1,yaw:1},heldInput:0,result:{state:'completed'}},
   ...['initial_world','after_resource_reload','after_dimension_roundtrip','saved_title','saved_world_reentry'].map(stage=>({case:'consumer_frame',stage,sha256:'a'.repeat(64),bytes:1024,dimensions:[1920,1080],overlayIdentity:0,thirdPartyConvergenceClaimed:false})),
   {case:'consumer_recording',status:{artifactReady:true,writtenFrames:2,writtenStates:2},artifactBytes:2048,readWithoutLease:true},
   ...['minecraft:the_nether','minecraft:overworld'].map(dimension=>({case:'consumer_dimension',dimension,player:{...player,dimension}})),
   {case:'consumer_provider_owner_reads',readWithoutArm:true,readWithoutLease:true,providers:Array.from({length:4},()=>({status:'completed',data:{threadName:'Server thread'}}))},
   {case:'consumer_provider_mutation',authority:'DEBUG_PRIVILEGED',inputLease:false,changed:{gameplayEvidence:false,mechanism:'registered_provider_typed_mutation',beforeResourceVersion:{revision:1},afterResourceVersion:{revision:2}}},
   {case:'consumer_provider_deadline',error:'REQUEST_DEADLINE_EXCEEDED',noLateEffect:true,windowMs:700},
   {case:'consumer_mcp_provider',transport:'real_mcp_stdio',readWithoutArm:true,readWithoutLease:true}
  ]};
}

function validFeedback(){
 const quota=operationCategory=>({retryAfterMillis:20,rateLimit:{scope:'principal',category:operationCategory,operationCategory,capacity:60,remaining:0,refillPerSecond:30,cost:1}});
 const receipt=()=>({accepted:true,effectConfirmed:false,serverValidationObserved:false,gameplayAcceptanceEligible:false,commandFeedback:{association:'temporal_not_guaranteed',windowMillis:500,observedMillis:500,complete:true,readable:true,messages:[{source:'client_chat',text:'untrusted response',offsetMillis:30}]}});
 const player={available:true,authority:'CLIENT_KNOWN',dataCategory:'USER',gameMode:'creative',onGround:true,flying:false};
 const hit={kind:'block',source:'candidate_outline_ray',dataCategory:'USER',authority:'CLIENT_KNOWN',face:'north',block:{x:0,y:81,z:2},position:{x:.5,y:81.5,z:2},distance:1.5};
 return{target:'fixture-target',status:'PASS',scenario:'full',cleanShutdown:true,sourceChangedDuringRun:false,persistentWriteCalls:0,
  checks:Array.from({length:14},(_,i)=>({name:'feedback '+i,status:'PASS'})),actionSamples:[
   {case:'feedback_read_quota',native:quota('world'),mcp:quota('world'),realMcp:true,inputLease:false,inputDispatchDelta:0},
   {case:'feedback_pipeline_quota',...quota('pipeline'),inputRetried:false,inputDispatchDelta:0,heldKeys:0,heldButtons:0},
   {case:'feedback_native_command',success:receipt(),error:receipt(),correlationGuaranteed:false,normalPlayerPath:true},
   {case:'feedback_command_preflight',error:'PLAYER_COMMAND_NOT_AVAILABLE',inputDispatchDelta:0},
   {case:'feedback_mcp_command',result:receipt(),realMcp:true},
   {case:'feedback_title_ready',result:{passed:true,evidence:{pageReady:true,overlayIdentity:0}},inputDispatchDelta:0,unknownWorld:{available:false}},
   {case:'feedback_block_readiness',air:{evidence:{available:true,block:'minecraft:air'}},loadingRequested:false,unloaded:{available:false,chunkLoadRequested:false},stillUnloaded:{available:false,chunkLoadRequested:false}},
   {case:'feedback_destination_ready',ready:{passed:true,evidence:{authority:'CLIENT_KNOWN'}},readDoesNotLoad:true},
   {case:'feedback_player_unknown',player:{available:false,gameMode:'unknown',onGround:'unknown',flying:'unknown'}},
   {case:'feedback_user_player',player,provider:{data:player},frame:{reads:[{data:player}]},condition:{passed:true},inputDispatchDelta:0},
   {case:'feedback_mcp_player',player,frame:{reads:[{data:player}]},realMcp:true,inputLease:false},
   {case:'feedback_player_mode',before:player,changed:{...player,gameMode:'survival'}},
   ...['feedback_native_hit','feedback_mcp_hit'].map(name=>({case:name,operation:{state:'failed',errorCode:'WORLD_TARGET_NOT_HIT',errorDetails:{actualHit:hit}},hit,cleanupDispatches:1,playerInputDispatches:0,beforeInput:{inputDispatchSequence:5},afterInput:{inputDispatchSequence:6,pressedKeyCount:0,pressedButtonCount:0},realMcp:true}))
  ]};
}

test('feedback alpha proof requires all new Native and real MCP contracts rather than a summary flag',()=>{
 assert.equal(consumerFeedbackProof(validFeedback(),'fixture-target').realMcpChecks,4);
 for(const sample of validFeedback().actionSamples){const r=validFeedback();r.actionSamples=r.actionSamples.filter(s=>s.case!==sample.case);assert.throws(()=>consumerFeedbackProof(r,'fixture-target'),sample.case);}
});

test('feedback alpha proof refuses invented air flags, authority, execution acknowledgement and late input',()=>{
 for(const [name,change]of[
  ['feedback_read_quota',s=>{s.mcp.rateLimit.remaining=1;}],
  ['feedback_pipeline_quota',s=>{s.inputRetried=true;}],
  ['feedback_native_command',s=>{s.success.effectConfirmed=true;}],
  ['feedback_mcp_command',s=>{s.realMcp=false;}],
  ['feedback_title_ready',s=>{s.result.evidence.overlayIdentity=4;}],
  ['feedback_block_readiness',s=>{s.unloaded.available=true;}],
  ['feedback_player_unknown',s=>{s.player.flying=false;}],
  ['feedback_mcp_player',s=>{s.player.flying='unknown';}],
  ['feedback_native_hit',s=>{s.afterInput.inputDispatchSequence++;}],
  ['feedback_mcp_hit',s=>{s.operation.errorDetails.actualHit={...s.hit,kind:'miss'};}]
 ]){const r=structuredClone(validFeedback());change(r.actionSamples.find(s=>s.case===name));assert.throws(()=>consumerFeedbackProof(r,'fixture-target'),name);}
 for(const patch of[{status:'FAIL'},{scenario:'command-feedback'},{cleanShutdown:false},{sourceChangedDuringRun:true},{persistentWriteCalls:1},{checks:[]}])assert.throws(()=>consumerFeedbackProof({...validFeedback(),...patch},'fixture-target'));
});
test('consumer alpha proof requires the native lifecycle, current frames and independent typed Provider/MCP evidence',()=>{
 assert.equal(consumerPriorityProof(valid(),'fixture-target').checks,9);
});
test('consumer alpha proof refuses missing samples, authority laundering, late effects and false convergence',()=>{
 for(const name of ['consumer_motion','consumer_frame','consumer_recording','consumer_dimension','consumer_provider_owner_reads','consumer_provider_mutation','consumer_provider_deadline','consumer_mcp_provider']){const r=valid();r.actionSamples=r.actionSamples.filter(s=>s.case!==name);assert.throws(()=>consumerPriorityProof(r,'fixture-target'));}
 for(const patch of [{status:'FAIL'},{scenario:'movement'},{cleanShutdown:false},{sourceChangedDuringRun:true},{persistentWriteCalls:1},{checks:[]}])assert.throws(()=>consumerPriorityProof({...valid(),...patch},'fixture-target'));
 for(const [name,patch]of [['consumer_motion',{heldInput:1}],['consumer_frame',{thirdPartyConvergenceClaimed:true}],['consumer_provider_mutation',{authority:'PLAYTEST'}],['consumer_provider_deadline',{noLateEffect:false}],['consumer_mcp_provider',{readWithoutLease:false}]]){const r=valid();Object.assign(r.actionSamples.find(s=>s.case===name),patch);assert.throws(()=>consumerPriorityProof(r,'fixture-target'));}
});
function validUiFixture(){
 const r={target:'fixture-target',status:'PASS',scenario:'all',phase:'test',runtime:'development_native',cleanShutdown:true,processExitCode:0,endpointRemoved:true,sourceChangedDuringRun:false,persistentWriteCalls:0,noMultimodal:true,screenshotAssertions:0,fixtureSha256:'a'.repeat(64),checks:Array.from({length:60},()=>({status:'PASS'})),worldSelectionSamples:[{selectedBefore:false,status:'PASS',worldEntered:true,noHeldInput:true}],vitalSamples:['mcp_stdio','web_http'].map(transport=>({kind:'vital_consumer',transport,status:'PASS',readWithoutLease:true}))};
 r.launchAttestation={processId:42,titleReadyAt:'2026-10-17T00:00:00Z'};r.testedAt='2026-10-17T00:02:00Z';
 const scrolls=['creative.scrollbar','loom.scrollbar','stonecutter.scrollbar','merchant.scrollbar'],hud=['hud.title','hud.subtitle','hud.actionbar'];
 r.uiDebugSamples=[...Array.from({length:134},()=>({result:'APPLIED'})),
  ...scrolls.map(controlKey=>({case:'native_screen_scroll',controlKey,enabled:true,original:0,restored:true,inputDelta:0,selectionUnchanged:true})),
  ...hud.flatMap(field=>[
   {case:'native_hud_text',field,restoredText:true,inputDelta:0,worldMutation:false,lifetimeRestored:false,richFormattingRestored:false},
   ...['INVALID_UI_DEBUG_VALUE','INVALID_UI_DEBUG_VALUE','INVALID_UI_DEBUG_VALUE','INVALID_UI_DEBUG_VALUE','VALUE_PRECONDITION_FAILED','STALE_RESOURCE_REVISION'].map(code=>({case:'hud_message_rejected',field,code,inputDelta:0}))
  ]),{case:'native_hud_content_guard_probe',probeOnly:true,processId:42,target:r.target,testedAt:'2026-10-17T00:01:00Z',playerInputUsed:false,worldMutationUsed:false,status:'PASS',checks:10}];
 r.uiDebugSamples.push(
  {case:'native_toast_content_guard_probe',probeOnly:true,processId:42,target:r.target,processStartedAt:'2026-10-16T23:59:55Z',testedAt:'2026-10-17T00:00:01Z',playerInputUsed:false,worldMutationUsed:false,status:'PASS',checks:9,nativeTickVersionStable:true,sameValueResetInvalidates:true,resetGenerationChecks:2,resetConsumption:'native_update_on_probe_owned_toast'},
  {case:'native_tutorial_progress',restored:true,inputDelta:0,worldMutation:false},
  {case:'native_system_toast_reset',restoredText:true,inputDelta:0,worldMutation:false,lifetimeRestored:false,richFormattingRestored:false},
  ...['progress','static','system'].map(kind=>({case:'native_toast_hide',kind,oneWay:true,inputDelta:0,worldMutation:false})),
  ...['ui.slider.set','ui.toast.reset'].flatMap(operation=>['INVALID_UI_DEBUG_VALUE','VALUE_PRECONDITION_FAILED','STALE_RESOURCE_REVISION'].map(code=>({case:'native_toast_rejected',operation,code,inputDelta:0})))
 );
 r.advancementPanSamples=[
  {case:'native_advancement_pan_fixed_axes',attemptedRoots:['story'],centeredByNativeRenderer:true,original:{x:103,y:42},bothFixed:true,unrequestedZeroing:false,inputDelta:0},
  {case:'native_advancement_pan_ranged',original:{x:-20,y:42},value:{x:-60,y:42},restored:true,centeredByNativeRenderer:true,staleRejected:true,inputDelta:0,playerLeaseRequired:false},
  {case:'native_advancement_pan_screen_replaced',rejected:true,inputDelta:0}
 ];
 r.editorSamples=['widget_active','widget_visible','native_list','native_slider','native_editbox','book','book_title','sign','hanging_sign',...scrolls.map(key=>'debug_screen_scroll_'+key.split('.')[0]),...hud.map(key=>'debug_'+key.replace('.','_')),'debug_tutorial_progress','debug_system_toast','debug_advancement_canvas'].flatMap(editor=>[
  {editor,transport:'mcp_stdio_debug',status:'PASS',inputDelta:0,playerLeaseAcquired:false,typedRecovery:true,userOperationGetWaitAssertRejected:true,diagnosticCancelRedacted:true},
  {editor,transport:'web_user_isolation',status:'PASS',operateUserSnapshotAvailable:true,debugMetadataLeaked:false,automaticLeaseAcquired:false}
 ]);
 r.editorSamples.push(...['book','book_title','sign','hanging_sign'].flatMap(editor=>['mcp_stdio','web_http'].map(transport=>({editor,transport,status:'PASS',emptyRestoreVerified:editor==='book_title',textReplaceStrategy:'select_all'}))));
 return {r,scrolls,hud};
}

test('full alpha refuses targeted UI, stale sources or unverified process cleanup',()=>{
 const {r,scrolls,hud}=validUiFixture();
 assert.equal(uiRegressionProof(r,'fixture-target').checks,60);
 for(const patch of [{scenario:'hud-vitals'},{cleanShutdown:false},{processExitCode:1},{endpointRemoved:false},{sourceChangedDuringRun:true},{worldSelectionSamples:[]},{vitalSamples:[]},{editorSamples:[]},{uiDebugSamples:Array(56).fill({})}])assert.throws(()=>uiRegressionProof({...r,...patch},'fixture-target'));
 for(const [transport,field,value]of [['mcp_stdio_debug','userOperationGetWaitAssertRejected',false],['mcp_stdio_debug','inputDelta',1],['mcp_stdio_debug','typedRecovery',false],['web_user_isolation','operateUserSnapshotAvailable',false],['web_user_isolation','debugMetadataLeaked',true],['web_user_isolation','automaticLeaseAcquired',true]]){
  const changed=structuredClone(r);changed.editorSamples.find(s=>s.transport===transport)[field]=value;assert.throws(()=>uiRegressionProof(changed,'fixture-target'));
 }
 for(const target of ['1.20.1-forge','1.21.1-neoforge']){
  const old=structuredClone(r);old.target=target;old.uiDebugSamples.find(s=>s.case==='native_hud_content_guard_probe').target=target;old.uiDebugSamples.find(s=>s.case==='native_toast_content_guard_probe').target=target;
  old.uiDebugSamples.find(s=>s.case==='native_toast_content_guard_probe').resetConsumption='native_render_on_probe_owned_toast_no_pixel_canvas';
  if(target==='1.20.1-forge'){
   old.uiDebugSamples=old.uiDebugSamples.filter(s=>!(s.case==='native_toast_hide'&&s.kind==='system'));
   old.uiDebugSamples.push({case:'native_system_toast_hide_absent',nativeMethodAbsent:true,inputDelta:0},{case:'legacy_system_toast_fixture_cleanup',probeOnly:true,notAProductHideCapability:true});
  }
  assert.throws(()=>uiRegressionProof(old,target));
  old.editorSamples.find(s=>s.editor==='book_title'&&s.transport==='mcp_stdio').textReplaceStrategy='backspace_from_end';assert.equal(uiRegressionProof(old,target).status,'PASS');
 }
 for(const sample of r.uiDebugSamples.filter(s=>s.case)){
  const changed=structuredClone(r);changed.uiDebugSamples.splice(r.uiDebugSamples.indexOf(sample),1);assert.throws(()=>uiRegressionProof(changed,r.target),sample.case);
 }
 for(const [name,patch]of[
  ['native_screen_scroll',{restored:false}],['native_screen_scroll',{inputDelta:1}],['native_screen_scroll',{selectionUnchanged:false}],['native_screen_scroll',{enabled:false}],
  ['native_hud_text',{worldMutation:true}],['native_hud_text',{lifetimeRestored:true}],['native_hud_text',{richFormattingRestored:true}],
  ['native_hud_content_guard_probe',{processId:43}],['native_hud_content_guard_probe',{target:'other-target'}],['native_hud_content_guard_probe',{testedAt:'2026-10-16T00:00:00Z'}],
  ['native_hud_content_guard_probe',{testedAt:'2026-10-18T00:00:00Z'}],['native_hud_content_guard_probe',{checks:9}],['native_hud_content_guard_probe',{probeOnly:false}],
  ['native_toast_content_guard_probe',{processId:43}],['native_toast_content_guard_probe',{target:'other-target'}],['native_toast_content_guard_probe',{checks:6}],['native_toast_content_guard_probe',{processStartedAt:'invalid'}],['native_toast_content_guard_probe',{testedAt:'2026-10-18T00:00:00Z'}],['native_toast_content_guard_probe',{probeOnly:false}],['native_toast_content_guard_probe',{nativeTickVersionStable:false}],['native_toast_content_guard_probe',{sameValueResetInvalidates:false}],['native_toast_content_guard_probe',{resetGenerationChecks:0}],
  ['native_tutorial_progress',{restored:false}],['native_system_toast_reset',{richFormattingRestored:true}],['native_system_toast_reset',{lifetimeRestored:true}],['native_toast_hide',{oneWay:false}],['native_toast_hide',{inputDelta:1}]
 ]){const changed=structuredClone(r);Object.assign(changed.uiDebugSamples.find(s=>s.case===name),patch);assert.throws(()=>uiRegressionProof(changed,r.target),name);}
 for(const editor of [...scrolls.map(key=>'debug_screen_scroll_'+key.split('.')[0]),...hud.map(key=>'debug_'+key.replace('.','_')),'debug_tutorial_progress','debug_system_toast','debug_advancement_canvas'])for(const transport of ['mcp_stdio_debug','web_user_isolation']){
  const changed=structuredClone(r);changed.editorSamples=changed.editorSamples.filter(s=>s.editor!==editor||s.transport!==transport);assert.throws(()=>uiRegressionProof(changed,r.target),editor+' '+transport);
 }
 for(const sample of r.advancementPanSamples){const changed=structuredClone(r);changed.advancementPanSamples=changed.advancementPanSamples.filter(row=>row.case!==sample.case);assert.throws(()=>uiRegressionProof(changed,r.target),sample.case);}
 for(const [name,change]of [
  ['native_advancement_pan_fixed_axes',row=>{row.centeredByNativeRenderer=false;}],['native_advancement_pan_fixed_axes',row=>{row.bothFixed=false;}],['native_advancement_pan_fixed_axes',row=>{row.unrequestedZeroing=true;}],
  ['native_advancement_pan_fixed_axes',row=>{row.original.x=NaN;}],['native_advancement_pan_fixed_axes',row=>{row.inputDelta=1;}],
  ['native_advancement_pan_ranged',row=>{row.centeredByNativeRenderer=false;}],['native_advancement_pan_ranged',row=>{row.restored=false;}],['native_advancement_pan_ranged',row=>{row.staleRejected=false;}],
  ['native_advancement_pan_ranged',row=>{row.playerLeaseRequired=true;}],['native_advancement_pan_ranged',row=>{row.value={...row.original};}],['native_advancement_pan_ranged',row=>{row.value.y=Infinity;}],
  ['native_advancement_pan_ranged',row=>{row.original.z=0;}],['native_advancement_pan_ranged',row=>{row.inputDelta=1;}],
  ['native_advancement_pan_screen_replaced',row=>{row.rejected=false;}],['native_advancement_pan_screen_replaced',row=>{row.inputDelta=1;}]
 ]){const changed=structuredClone(r);change(changed.advancementPanSamples.find(row=>row.case===name));assert.throws(()=>uiRegressionProof(changed,r.target),name);}
 const duplicate=structuredClone(r);duplicate.advancementPanSamples[2]=structuredClone(duplicate.advancementPanSamples[0]);assert.throws(()=>uiRegressionProof(duplicate,r.target));
 const forge=structuredClone(r);forge.target='1.20.1-forge';forge.uiDebugSamples.find(s=>s.case==='native_hud_content_guard_probe').target=forge.target;
 forge.uiDebugSamples.find(s=>s.case==='native_toast_content_guard_probe').target=forge.target;
 forge.uiDebugSamples.find(s=>s.case==='native_toast_content_guard_probe').resetConsumption='native_render_on_probe_owned_toast_no_pixel_canvas';
 forge.uiDebugSamples=forge.uiDebugSamples.filter(s=>!(s.case==='native_toast_hide'&&s.kind==='system'));
 forge.uiDebugSamples.push({case:'native_system_toast_hide_absent',nativeMethodAbsent:true,inputDelta:0},{case:'legacy_system_toast_fixture_cleanup',probeOnly:true,notAProductHideCapability:true});
 forge.editorSamples.find(s=>s.editor==='book_title'&&s.transport==='mcp_stdio').textReplaceStrategy='backspace_from_end';
 forge.uiDebugSamples.find(s=>s.controlKey==='stonecutter.scrollbar').enabled=false;
 forge.editorSamples=forge.editorSamples.filter(s=>s.editor!=='debug_screen_scroll_stonecutter');
 assert.equal(uiRegressionProof(forge,forge.target).uiDiagnosticConsumers,18);
 const invented=structuredClone(forge);invented.uiDebugSamples.push({case:'native_toast_hide',kind:'system',oneWay:true,inputDelta:0,worldMutation:false});assert.throws(()=>uiRegressionProof(invented,forge.target));
});

const goalInput=(sequence=0,generation=1,id='world-1')=>({pressedKeyCount:0,pressedButtonCount:0,inputCleanupPending:false,inputCleanupFailed:false,inputDispatchSequence:sequence,inputGeneration:generation,inputContextGeneration:generation,inputContextId:id,inputContextAvailable:true});
const goalReleased=()=>({passed:true,evidence:{pressedKeyCount:0,pressedButtonCount:0}});
const goalPlayer=()=>({type:'player.state',available:true,domain:'WORLD',dataCategory:'USER',authority:'CLIENT_KNOWN',source:'bounded_client_world_model',dataSource:'LIVE',storageAccessed:false,chunkLoadRequested:false,completeGameData:false,x:.5,y:.5,z:.5,yaw:0,pitch:0,velocityX:0,velocityY:0,velocityZ:0,onGround:true,dimension:'minecraft:overworld'});
const goalReadMode=()=>({mode:'READ',status:'available',takeoverActive:false,reconsentRequired:true,modeVersion:{controlSessionId:'test-session',generation:1}});
function goalHeader(target,scenario,checks){return{target,scenario,status:'PASS',evidence:'development_runtime_native',fullSmoke:false,fullWorldMilestone:false,cleanShutdown:true,sourceChangedDuringRun:false,persistentWriteCalls:0,fixturePolicy:'immutable_baseline_restored',fixtureInput:{policy:'immutable_baseline_restored',target,role:'client',credentialsCopied:false,baselineSha256:'a'.repeat(64),restoredSha256:'a'.repeat(64)},checks:Array.from({length:checks},()=>({status:'PASS'})),actionSamples:[]};}
function goalEnd(target,name,checks,maximum){return{case:name,target,checks,maximumMillis:maximum,elapsedMillis:1000,liveEvidence:true,evidenceKind:'runtime_monotonic',finalInput:goalInput(),finalMode:goalReadMode(),physicalEscTested:false,clientShutdownOwnedByOuterRunner:true,persistentWriteCalls:0};}
function goalContext(target){
 const result=goalHeader(target,'input-context',3),lease={leaseId:'test-lease',modeVersion:{generation:1}};
 const edge=n=>{const before=goalInput(1,n,'world-'+n),after=goalInput(3,n+1,'world-'+(n+1));return{before,held:{...before,pressedKeyCount:1},queuedState:{...before},active:{operationId:'active-'+n},queued:{operationId:'queued-'+n},activeBefore:{status:'running'},outcomes:[{operationId:'active-'+n,state:'cancelled'},{operationId:'queued-'+n,state:'cancelled'}],after,later:{...after},waited:goalReleased(),negativeObservationMillis:500,sameLeasePreserved:true,oldInputWasCancelledNotReplayed:true,leaseBefore:lease,leaseAfter:lease,freshInput:{outcome:{state:'completed'},after:{...after,inputDispatchSequence:5},readiness:{condition:'two_distinct_native_frames_no_screen_no_overlay_cleanup_complete',stableNativeFrames:[{},{}]}}};};
 result.actionSamples=[{case:'input_context_screens',coordinateFallbacks:0,ordinaryScreensDoNotAdvanceNativeContext:true,agentEscDoesNotRevoke:true,inWorld:goalInput(),screens:Array.from({length:4},()=>({outcome:{state:'completed'},input:goalInput()}))},{case:'input_context_dimensions',arrangeIsGameplayAcceptance:false,destinationConfirmedBy:'independent_USER_client_player',nativeCommandEffectReceiptNotProof:true,outward:edge(1),returned:edge(2)},{case:'input_context_control_ws',transport:'.NET_ClientWebSocket',leaseHeaderBound:true,credentialsExcluded:true,closed:{normalClose:true,exitCode:0},hello:{target},readyInput:goalInput(),held:{...goalInput(),pressedKeyCount:1},ended:{...goalReadMode(),reconsentRequired:false,modeTransitionReason:'control_channel_disconnected'},waited:goalReleased(),after:goalInput(),later:goalInput(),negativeObservationMillis:500,ordinaryHttpCloseNotTestedAsDisconnect:true,localLeaseForgottenOnlyAfterConfirmedEnd:true},{...goalEnd(target,'input_context_complete',3,240000),sameUuidRespawnTested:false,ordinaryHttpCloseImmediateReleaseClaimed:false}];return result;
}
function goalPage(query,offset,count){
 const volume=['x','y','z'].reduce((n,key)=>n*(query.max[key]-query.min[key]+1),1),complete=offset+count===volume;
 return{type:'world.region',available:true,domain:'WORLD',dataCategory:'USER',authority:'CLIENT_KNOWN',source:'bounded_client_world_model',dataSource:'LIVE',storageAccessed:false,chunkLoadRequested:false,crossPageAtomic:false,completeGameData:false,stateSemantics:'per_page_live_client_cache_non_atomic',order:'section_y_z_x_then_local_y_z_x',unloadedPaletteIndex:-1,contextId:'world-1',dimension:'minecraft:overworld',sourceTick:1,sourceTickEnd:1,ownerBudgetNanos:8000000,captureDurationNanos:1,region:{min:query.min,max:query.max},requestedCellCount:volume,pageStartOffset:offset,pageCellCount:count,indices:Array(count).fill(0),palette:[{block:'minecraft:air',properties:{}}],coveredCellCount:offset+count,remainingCellCount:volume-offset-count,complete,truncated:!complete,maxBlocks:query.maxBlocks,maxBytes:query.maxBytes,section:{x:Math.floor(query.min.x/16),y:Math.floor(query.min.y/16),z:Math.floor(query.min.z/16),min:query.min,size:Object.fromEntries(['x','y','z'].map(key=>[key,query.max[key]-query.min[key]+1])),offset},pageUnloadedCellCount:0,unloadedCellCount:0,allLoaded:complete,...(!complete?{nextCursor:'test-cursor-'+offset}:{})};
}
function goalStream(target,start=1,replay){const event=index=>({type:'world.player.state',dataCategory:'USER',authority:'CLIENT_KNOWN',domain:'WORLD',source:'client_tick_state',stateStream:'client_player_pose',storageAccessed:false,serverAuthoritative:false,streamContext:{opaqueId:'world-1',generation:1},sequence:100+index,sourceTick:index,sampledAtMillis:1000+index,x:.5,y:.5,z:.5,yaw:0,pitch:0,velocityX:0,velocityY:0,velocityZ:0,onGround:true});return{normalClose:true,exitCode:0,controlLeaseHeaderSent:false,credentialsExcluded:true,hello:{type:'event.hello',target},subscription:{type:'event.subscription.accepted',filter:{types:['world.player.state']},sequence:99+start},collectMillis:1250,messages:[...(replay?[{event:replay,afterSubscription:false,elapsedMillis:0}]:[]),...Array.from({length:16},(_,index)=>({event:event(start+index),afterSubscription:true,elapsedMillis:index*50}))]};}
function goalMcp(target){const mode=goalReadMode(),mcpMode={...mode,type:'control.mode',authorizationIndependent:true,authorization:{sessionOpen:true,inputLease:{held:false,remainingTtlMs:0}}};delete mcpMode.status;const query={min:{x:0,y:-1,z:0},max:{x:0,y:-1,z:0},maxBlocks:4096,maxBytes:131072},region=goalPage(query,0,1),samples=Array.from({length:20},(_,index)=>({index,callRttMillis:10+index,startedOffsetMillis:35*index,player:goalPlayer()}));return{case:'mcp_world_baseline',target,transport:'real_mcp_stdio',liveEvidence:true,initializeServerInfo:{name:'minecraft-protocol-companion',version:'fixture-version'},expectedPackageVersion:'fixture-version',samples,statistics:{...baselineStatistics(samples.map(row=>row.callRttMillis)),slowest:{index:19,callRttMillis:29}},nativeBefore:mode,mcpBefore:mcpMode,nativeAfter:mode,mcpAfter:mcpMode,before:goalInput(),after:goalInput(),session:{target,inWorld:true},finalSession:{target,inWorld:true},query,region,decodedCell:decodeRegionPage(region,query,0,'world-1')[0],point:{...goalPlayer(),block:'minecraft:air',state:'Block{minecraft:air}'},startupMillis:1,samplingElapsedMillis:700,regionCallRttMillis:1,nativePointRttMillis:1,elapsedMillis:1000,maximumMillis:90000,startupExcludedFromPlayerStatistics:true,timeSource:'monotonic_elapsed',measurementScope:'mcp_stdio_call_round_trip_not_http_or_server_effect',minimumCallStartIntervalMillis:35,activeAdmissionExcludedFromCallRtt:true,automaticRetries:0,crossMachineLatencySla:false,controlLeaseAcquired:false,permissionsGrantedByHelper:false,chunkLoadRequested:false,inputDispatched:false,screenshotsUsed:false,stateStreamTested:false,physicalEscTested:false,serverAuthoritativeReadsRequested:0,worldDiskReadsRequested:0,persistentWriteCalls:0,ownedCompanionClosed:true,environment:{credentialsExcluded:true,connectionAuthority:'instance_discovery_only'}};}
function goalRegionBase(target){
 const result=goalHeader(target,'region-stream',5),query={min:{x:0,y:0,z:0},max:{x:15,y:15,z:15},maxBlocks:4096,maxBytes:131072},pages=[goalPage(query,0,4096)],baseline={...query,maxBlocks:64},receipt=goalStream(target),context={opaqueId:'world-1',generation:1},summary=validatePlayerStream(receipt,target,context),resumeBase=receipt.messages.at(-2).event,replay=receipt.messages.at(-1).event,resumed=goalStream(target,17,replay),resumeSummary=validatePlayerStream(resumed,target,context,resumeBase);
 result.actionSamples=[{case:'region_stream_fixture',context:goalInput()},{case:'region_stream_region',request:query,pages,covered:4096,unloaded:0,uniqueCoordinates:4096,inputBefore:goalInput(),inputAfter:goalInput(),crossPageAtomic:false,readLoadRequested:false,debugReadUsed:false,historicalApiMeasured:false,comparisonBasis:'current_candidate_same_region_region_api_64_vs_4096_page_budget',userRadius:32,regionMaximumCellCenterDistance:26,baseline:{request:baseline,pages:Array.from({length:64},(_,index)=>goalPage(baseline,index*64,64))},baselineRegionCalls:64,actualRegionCalls:1,callReductionRatio:64,points:[{cell:{x:0,y:0,z:0,loaded:true,block:'minecraft:air',properties:{}},point:{...goalPlayer(),block:'minecraft:air',state:'Block{minecraft:air}'}}],consumedRejected:{error:'WORLD_REGION_CURSOR_INVALID'},rotatedRejected:{error:'WORLD_REGION_CURSOR_INVALID'},cancelledRejected:{error:'WORLD_REGION_CURSOR_INVALID'},cancelled:{cancelled:true},twice:{cancelled:false}},{case:'region_stream_state',receipt,summary,noControlLeaseUsed:true,serverEffectConfirmed:false,before:goalPlayer(),after:goalPlayer(),stoppedGetAbsoluteErrors:Object.fromEntries(['x','y','z','yaw','pitch','velocityX','velocityY','velocityZ'].map(key=>[key,0]))},{case:'region_stream_resume',receipt:resumed,summary:resumeSummary,resumeBase,knownReplayMatched:true,replayedKnownSequence:replay.sequence,replayedKnownEvent:replay,before:goalInput(),after:goalInput(),noAutoAcquire:true,sameContext:true,replayGapResyncExercised:false},{...goalEnd(target,'region_stream_complete',4,180000),continuousWorldRegionSubscriptionClaimed:false},goalMcp(target)];return result;
}
function goalReadRequests(request,pages){return{request,pages,actualRequests:pages.length,requestAttempts:pages.map((page,index)=>({body:{category:'USER',action:'read',query:index?{cursor:pages[index-1].nextCursor}:request},attemptInPage:1,outcome:'success',status:200,elapsedMillis:1,startedOffsetMillis:index*35,pageIndex:index,responseSha256:createHash('sha256').update(JSON.stringify(page)).digest('hex'),retryScheduled:false}))};}
function recordGoalRegionTrace(result){const region=result.actionSamples.find(row=>row.case==='region_stream_region'),requestAttempts=[];for(const key of ['baseline','optimized']){const read=region[key];read.firstRequestIndex=requestAttempts.length;for(const row of read.requestAttempts){row.requestIndex=requestAttempts.length;requestAttempts.push(row);}read.lastRequestIndex=requestAttempts.length-1;}let trace=result.actionSamples.find(row=>row.case==='region_stream_requests');if(!trace){trace={case:'region_stream_requests'};result.actionSamples.push(trace);}Object.assign(trace,{scope:'page_helper_calls_excludes_expected_cursor_errors_and_final_cleanup',requestAttempts});}
function goalRegion(target){const result=goalRegionBase(target),region=result.actionSamples.find(row=>row.case==='region_stream_region');region.baseline=goalReadRequests(region.baseline.request,region.baseline.pages);region.optimized=goalReadRequests(region.request,region.pages);region.path={actualPointHttpRequests:165,baselineRegionHttpRequests:region.baseline.actualRequests,optimizedRegionHttpRequests:region.optimized.actualRequests,additionalRegionRequestsForPath:0,pointToOptimizedRequestRatio:165/region.optimized.actualRequests};recordGoalRegionTrace(result);return result;}
function goalPlacement(target){
 const result=goalHeader(target,'world-placement-receipts',8),point=(block,properties={})=>({...goalPlayer(),block,state:'Block{'+block+'}'+(Object.keys(properties).length?'['+Object.entries(properties).map(([key,value])=>key+'='+value).join(',')+']':'')});
 result.actionSamples=placementReceiptCases.map(spec=>{
  const before={block:spec.kind==='slab'?spec.item:'minecraft:air',properties:spec.kind==='slab'?{type:'bottom',waterlogged:'false'}:{}},after={block:spec.blocked||spec.kind==='note_block'?before.block:spec.item,properties:spec.blocked||spec.kind==='note_block'?before.properties:spec.kind==='slab'?{type:'double',waterlogged:'false'}:spec.kind==='bed'?{part:'foot',facing:'east',occupied:'false'}:spec.kind==='door'?{facing:'east',half:'lower',hinge:'left',open:'false',powered:'false'}:{half:'lower'}},changed=!spec.blocked&&spec.kind!=='note_block';
  const value={type:'world.action_result',action:'place',domain:'WORLD',dataCategory:'USER',evidenceAuthority:'PLAYTEST',entryLayer:'GAME_ROUTED_RAW',directMutationUsed:false,effectConfirmed:false,serverValidationObserved:false,nativeInvocationObserved:true,nativeUseOnResultObserved:true,inputReleased:true,nativeUseOnResult:spec.blocked?'fail':'success',nativeConsumesAction:!spec.blocked,clientDestinationChanged:changed,clientPlacementPredicted:changed,placementState:{before,after},placementOutcome:spec.blocked?'native_rejected':changed?'client_placement_predicted':'native_consumed_not_confirmed',reason:spec.blocked?'native_use_on_failed':changed?'native_use_on_consumed_and_expected_destination_changed':'native_use_on_consumed_without_expected_destination_change',targetRef:'semantic-'+spec.id,onTarget:true,actualHit:{kind:'block',block:{x:0,y:0,z:0},face:'up'}};
  return{case:'world_placement_receipt',id:spec.id,specification:spec,fixture:{target:{x:0,y:0,z:0},face:'up',destinationBefore:point(before.block,before.properties)},operation:{state:'completed',result:{steps:[{stepType:'world.action',result:value}]}},result:value,discovered:{targetRef:value.targetRef},afterDestination:point(after.block,after.properties),companionAfter:point(spec.item,spec.kind==='bed'?{...after.properties,part:'head'}:{...after.properties,half:'upper'}),interactionAfter:point('minecraft:note_block',{note:'1'}),beforeInput:goalInput(),afterInput:goalInput(2),waited:goalReleased(),arrangeAuthority:'diagnostic_current_player_NATIVE_commands',actAuthority:'PLAYTEST',assertAuthority:'CLIENT_KNOWN_USER',actUsesSemanticTarget:true,pixelInputUsed:false,directMutationUsedByAct:false,operationCompletedMeans:'input_lifecycle_only',serverValidationObserved:false};
 });result.actionSamples.push({...goalEnd(target,'world_placement_complete',8,240000),tested:placementReceiptCases.map(row=>row.id),serverValidationObserved:false});return result;
}
function goalAttack(count,index=0){
 const steps=performanceAttackSteps(count),before={...goalInput(),nativeEntityAttackCount:index*10,nativeEntityAttackScope:'native_multiplayer_game_mode_attack_return',nativeEntityAttackCounterUnits:'native_entity_attack_invocations_not_damage_or_swing',nativeEntityAttackContextId:'world-1',nativeEntityAttackContextGeneration:1,nativeEntityAttackControlGeneration:1,nativeEntityAttackLeaseActive:true,nativeEntityAttackServerEffectConfirmed:false},after={...before,inputDispatchSequence:count*2+1,nativeEntityAttackCount:index*10+count};let dispatch=0;
 return{steps,inputBefore:before,inputAfter:after,postRttMillis:1,waitRttMillis:1,observedTerminalMillis:count===1?110:150,outcome:{state:'completed',result:{cleanupOnComplete:true,steps:steps.map(step=>({stepType:step.type,elapsedMillis:1,result:step.type==='mouse.button'?{inputOrigin:'AGENT_ROUTED',entryLayer:'GAME_ROUTED_RAW',hostCursorUsed:false,directMutationUsed:false,clientTick:++dispatch,inputDispatchSequence:dispatch}:{}})),timing:{scope:'automation_pipeline_not_http_or_server_effect',timeSource:'monotonic_elapsed',queueMillis:0,totalMillis:count===1?100:140,requestedDelayMillis:count*40,inputCallbackCount:count*2,stages:{cleanup:{calls:1,elapsedMillis:1}}}}}};
}
function goalPerformance(target){const result=goalHeader(target,'continuous-performance',5),singles=[0,1,2].map(index=>({case:'continuous_performance_attack_single',index,...goalAttack(1,index)})),batch=goalAttack(3,3),comparison=performanceAttackComparison(singles,batch);result.actionSamples=[{case:'continuous_performance_movement',observedMillis:30000,clientObservedTravelDistance:10,lastClientTick:601,firstClientTick:1,observations:Array(30).fill({}),originalSensitivityUnchanged:true,serverEffectConfirmed:false},...singles,{case:'continuous_performance_attack_comparison',batch,comparison,targetNotMetBlocksPerformanceCompletion:true,measurementValidityDoesNotMeanTargetMet:true},{case:'continuous_performance_attack_fixture',entity:{type:'minecraft:cow',targetRef:'cow-ref'},aim:{outcome:{state:'completed',result:{steps:[{result:{onTarget:true,actualHit:{kind:'entity',entityType:'minecraft:cow',distance:2.5}}}]}}},elapsedMillis:1,scope:'diagnostic_fixture_and_real_USER_semantic_aim_excluded_from_attack_timing',actAuthority:'PLAYTEST',nativeCooldownChanged:false,serverEffectConfirmed:false,damageConfirmed:false},{...goalEnd(target,'continuous_performance_complete',5,180000),comparisonTargetResult:'MET',attackConsumptionConfirmed:true,performanceComplete:true,nativeCooldownChanged:false,serverEffectConfirmed:false,networkDisconnectTested:false}];return result;}
function goalGui(target){const result=goalHeader(target,'interaction-baseline',8),rows=Array.from({length:20},(_,index)=>{const nodeId='slot-'+index%2,timing={timeSource:'monotonic_elapsed',scope:'automation_pipeline_not_http_or_server_effect'};return{case:'interaction_baseline_sample',group:'gui_semantic_hover',index,operationId:'hover-'+index,state:'completed',timing,outcome:{state:'completed',result:{timing,steps:[{result:{resolvedNode:{nodeId},action:'hover'}}]}},semanticTarget:{freshTreePerSample:true,nodeId},inputBefore:goalInput(index*2),inputAfter:goalInput(index*2+2),postRttMillis:1,waitRttMillis:1,observedTerminalMillis:20+index};});const statistics=Object.fromEntries(['postRttMillis','waitRttMillis','observedTerminalMillis'].map(field=>[field,baselineStatistics(rows.map(row=>row[field]))]));result.actionSamples=[...rows,{case:'interaction_baseline_gui_pacing',sampleCount:20,guiSamples:20,guiP95Available:true,intentionalPacingUnchanged:true,hoverSamples:rows,statistics,alternatingSlots:[{slot:0},{slot:1}]},{case:'interaction_baseline_complete',guiSamples:20,guiP95Available:true,inputCallbackCounterIsDispatchProof:false,persistentWriteCalls:0,elapsedMillis:10000}];return result;}
function withTrialPerformanceLimit(result){const measured=result.actionSamples.find(row=>row.case==='continuous_performance_attack_comparison'),end=result.actionSamples.find(row=>row.case==='continuous_performance_complete');measured.batch.outcome.result.timing.totalMillis=300;measured.comparison=performanceAttackComparison(result.actionSamples.filter(row=>row.case==='continuous_performance_attack_single'),measured.batch);end.comparisonTargetResult=measured.comparison.targetResult;end.performanceComplete=measured.comparison.targetMet&&measured.comparison.actualAttackCountConfirmed;return result;}
function goalFixtures(target='fixture-target'){return{'input-context':goalContext(target),'region-stream':goalRegion(target),'world-placement-receipts':goalPlacement(target),'continuous-performance':withTrialPerformanceLimit(goalPerformance(target)),'interaction-baseline':goalGui(target)};}
const goalProofFunctions={'input-context':inputContextProof,'region-stream':regionStreamProof,'world-placement-receipts':placementReceiptsProof,'continuous-performance':continuousPerformanceProof,'interaction-baseline':interactionBaselineProof};

test('continuous profile validates actual sample structures, GUI20, MCP20 and counter-bound performance independently',()=>{for(const[scenario,result]of Object.entries(goalFixtures()))assert.equal(goalProofFunctions[scenario](result,'fixture-target').scenario,scenario);});
test('publisher preserves complete native prediction separately from reconciled USER readback',()=>{
 const value=goalPlacement('fixture-target'),row=value.actionSamples.find(sample=>sample.id==='door_unblocked');row.result.placementState.after.properties.hinge='right';
 const original=structuredClone(value);assert.equal(placementReceiptsProof(value,'fixture-target').checks,8);assert.deepEqual(value,original);
 for(const change of [r=>delete r.result.placementState.after.properties.powered,r=>r.result.placementState.after.properties.facing='west',r=>r.afterDestination.state=r.afterDestination.state.replace('half=lower','half=upper'),r=>r.companionAfter.state=r.companionAfter.state.replace('hinge=left','hinge=right'),r=>r.result.serverValidationObserved=true,r=>r.result.effectConfirmed=true]){const invalid=structuredClone(value);change(invalid.actionSamples.find(sample=>sample.id==='door_unblocked'));assert.throws(()=>placementReceiptsProof(invalid,'fixture-target'));}
});
test('continuous profile refuses missing/duplicate, stale, unclean, synthetic or write-contaminated evidence',()=>{
 for(const[scenario,original]of Object.entries(goalFixtures())){
  for(const change of [r=>r.target='wrong',r=>r.cleanShutdown=false,r=>r.sourceChangedDuringRun=true,r=>r.persistentWriteCalls=1,r=>r.fixtureInput.restoredSha256='b'.repeat(64),r=>r.checks.pop(),r=>r.actionSamples=[]]){const value=structuredClone(original);change(value);assert.throws(()=>goalProofFunctions[scenario](value,'fixture-target'),scenario);}
 }
});
test('trial preserves measured NOT_MET and incomplete performance without claiming Goal acceptance',()=>{
 const result=withTrialPerformanceLimit(goalPerformance('fixture-target')),before=structuredClone(result),proof=continuousPerformanceProof(result,'fixture-target');
 assert.equal(CONTINUOUS_PROFILE,'continuous_gameplay_trial_v1');assert.equal(proof.batchToSingleP50Ratio,3);assert.equal(proof.targetMet,false);assert.equal(proof.targetResult,'NOT_MET');assert.equal(proof.performanceComplete,false);assert.equal(proof.delivery,'trial_with_known_performance_limit');assert.equal(proof.performanceAcceptance,'NOT_MET');assert.equal(proof.goalPerformancePass,false);assert.equal(proof.actualAttackCountConfirmed,true);assert.deepEqual(result,before);
 for(const change of [r=>r.actionSamples.find(row=>row.case==='continuous_performance_complete').performanceComplete=true,r=>r.actionSamples.find(row=>row.case==='continuous_performance_complete').comparisonTargetResult='MET',r=>r.actionSamples.find(row=>row.case==='continuous_performance_attack_comparison').comparison.targetMet=true]){const changed=structuredClone(result);change(changed);assert.throws(()=>continuousPerformanceProof(changed,'fixture-target'));}
});
test('trial still rejects inconsistent limits or falsely claimed counts without changing hold duration or replacing statistics',()=>{
 for(const change of [r=>{const measured=r.actionSamples.find(row=>row.case==='continuous_performance_attack_comparison');measured.batch.outcome.result.timing.totalMillis=300;measured.comparison=performanceAttackComparison(r.actionSamples.filter(row=>row.case==='continuous_performance_attack_single'),measured.batch);},r=>r.actionSamples.find(row=>row.case==='continuous_performance_attack_single').inputAfter.nativeEntityAttackCount=0,r=>r.actionSamples.find(row=>row.case==='continuous_performance_attack_single').inputAfter.nativeEntityAttackContextId='other',r=>r.actionSamples.find(row=>row.case==='continuous_performance_attack_comparison').comparison.batchToSingleP50Ratio=.5,r=>r.actionSamples.find(row=>row.case==='continuous_performance_attack_comparison').batch.steps[1].durationMs=0]){const value=goalPerformance('fixture-target');change(value);assert.throws(()=>continuousPerformanceProof(value,'fixture-target'));}
});
test('GUI1 is not GUI20 and injected or truncated MCP baselines are not actual twenty-call evidence',()=>{
 const gui=goalGui('fixture-target');gui.actionSamples=gui.actionSamples.filter(row=>row.case!=='interaction_baseline_sample'||row.index===0);assert.throws(()=>interactionBaselineProof(gui,'fixture-target'));
 for(const change of [row=>row.samples.pop(),row=>row.transport='injected_unit_test',row=>row.liveEvidence=false,row=>row.statistics.p95=0,row=>row.after.inputDispatchSequence++,row=>row.ownedCompanionClosed=false,row=>row.samples[1].startedOffsetMillis=0]){const value=goalRegion('fixture-target');change(value.actionSamples.find(row=>row.case==='mcp_world_baseline'));assert.throws(()=>regionStreamProof(value,'fixture-target'));}
});
test('region source and stream replay cannot be replaced by air, replay bursts, stale contexts or a paper baseline',()=>{
 for(const change of [row=>row.pages[0].indices[0]=-1,row=>row.pages[0].contextId='old',row=>row.baselineRegionCalls=64*64,row=>row.userRadius=33,row=>row.readLoadRequested=true]){const value=goalRegion('fixture-target');change(value.actionSamples.find(row=>row.case==='region_stream_region'));assert.throws(()=>regionStreamProof(value,'fixture-target'));}
 const value=goalRegion('fixture-target');for(const row of value.actionSamples.find(row=>row.case==='region_stream_state').receipt.messages)row.afterSubscription=false;assert.throws(()=>regionStreamProof(value,'fixture-target'));
});

function goalReadRetry(result,key,pageIndex=0,failures=1){
 const region=result.actionSamples.find(row=>row.case==='region_stream_region'),read=region[key],successes=read.requestAttempts.filter(row=>row.outcome==='success'),rows=[];let started=0;
 for(const success of successes){const count=success.pageIndex===pageIndex?failures:0;for(let index=0;index<count;index++){rows.push({body:structuredClone(success.body),attemptInPage:index+1,outcome:'typed_no_progress',status:429,error:{error:'WORLD_REGION_BUDGET_NO_PROGRESS',retryAfterMillis:50},retryAfterMillis:50,startedOffsetMillis:started,elapsedMillis:1,retryScheduled:true,waitMillis:50});started+=51;}rows.push({...success,attemptInPage:count+1,startedOffsetMillis:started});started+=35;}
 read.requestAttempts=rows;read.actualRequests=rows.length;region.baselineRegionCalls=region.baseline.actualRequests;region.actualRegionCalls=region.optimized.actualRequests;region.callReductionRatio=region.baselineRegionCalls/region.actualRegionCalls;region.path.baselineRegionHttpRequests=region.baselineRegionCalls;region.path.optimizedRegionHttpRequests=region.actualRegionCalls;region.path.pointToOptimizedRequestRatio=region.path.actualPointHttpRequests/region.actualRegionCalls;recordGoalRegionTrace(result);
}
test('region trial counts typed no-progress attempts, including a failed continuation, without replaying a successful cursor',()=>{
 const value=goalRegion('fixture-target'),region=value.actionSamples.find(row=>row.case==='region_stream_region');
 goalReadRetry(value,'baseline',0,1);goalReadRetry(value,'optimized',0,2);
 let proof=regionStreamProof(value,'fixture-target');assert.equal(proof.baselineCalls,65);assert.equal(proof.regionCalls,3);assert.equal(proof.callReductionRatio,65/3);assert.deepEqual(proof.failedRegionAttempts,{baseline:1,optimized:2});
 region.pages=[goalPage(region.request,0,2048),goalPage(region.request,2048,2048)];region.optimized=goalReadRequests(region.request,region.pages);goalReadRetry(value,'optimized',1,1);proof=regionStreamProof(value,'fixture-target');assert.equal(proof.regionCalls,3);assert.equal(region.optimized.requestAttempts[1].body.query.cursor,region.pages[0].nextCursor);assert.deepEqual(region.optimized.requestAttempts[1].body,region.optimized.requestAttempts[2].body);
});
test('region trial rejects missing attempts, unknown/transport retries, unsafe hints, query drift, late waits and omitted failures',()=>{
 for(const key of ['baseline','optimized'])for(const change of [
  read=>{delete read.requestAttempts;},read=>read.actualRequests--,
  read=>read.requestAttempts[0].status=503,read=>read.requestAttempts[0].outcome='transport_error',read=>read.requestAttempts[0].error.error='WORLD_REGION_BUSY',
  read=>read.requestAttempts[0].error.retryAfterMillis=0,read=>read.requestAttempts[0].error.retryAfterMillis=1001,read=>read.requestAttempts[0].error.retryAfterMillis=1.5,read=>read.requestAttempts[0].error.retryAfterMillis='50',
  read=>read.requestAttempts[0].retryAfterMillis=49,read=>read.requestAttempts[0].waitMillis=49,read=>read.requestAttempts[0].retryScheduled=false,
  read=>read.requestAttempts[0].pageIndex=0,read=>read.requestAttempts[0].responseSha256='a'.repeat(64),
  read=>read.requestAttempts[0].body.action='cancel',read=>read.requestAttempts[0].body.category='DEBUG',
  read=>read.requestAttempts[1].attemptInPage=1,read=>read.requestAttempts[1].body.query={cursor:'other'},read=>read.requestAttempts[1].startedOffsetMillis=1,
  read=>read.requestAttempts[1].responseSha256='a'.repeat(64),read=>read.requestAttempts[1].error={error:'ignored'},
  read=>{read.requestAttempts.shift();},read=>{read.actualRequests=read.pages.length;}
 ]){const result=goalRegion('fixture-target'),region=result.actionSamples.find(row=>row.case==='region_stream_region');goalReadRetry(result,key);change(region[key]);assert.throws(()=>regionStreamProof(result,'fixture-target'),key);}
 const fourth=goalRegion('fixture-target');goalReadRetry(fourth,'optimized',0,3);assert.throws(()=>regionStreamProof(fourth,'fixture-target'));
 const replay=goalRegion('fixture-target'),region=replay.actionSamples.find(row=>row.case==='region_stream_region');region.pages=[goalPage(region.request,0,1024),goalPage(region.request,1024,1024),goalPage(region.request,2048,2048)];region.pages[1].nextCursor=region.pages[0].nextCursor;region.optimized=goalReadRequests(region.request,region.pages);goalReadRetry(replay,'optimized');assert.throws(()=>regionStreamProof(replay,'fixture-target'));
});
test('region request summaries cannot omit a failed call from the independent scenario trace or borrow another read range',()=>{
 for(const change of [
  (r,trace)=>{trace.scope='all_http_calls';},(r,trace)=>{trace.requestAttempts[0].requestIndex=1;},
  r=>{r.optimized.firstRequestIndex=r.baseline.firstRequestIndex;},r=>{r.baseline.lastRequestIndex--;},
  r=>{const read=r.optimized;read.requestAttempts.shift();read.requestAttempts[0].attemptInPage=1;read.requestAttempts[0].requestIndex=read.firstRequestIndex;read.lastRequestIndex=read.firstRequestIndex;read.actualRequests--;r.actualRegionCalls--;r.callReductionRatio=r.baselineRegionCalls/r.actualRegionCalls;r.path.optimizedRegionHttpRequests=r.actualRegionCalls;r.path.pointToOptimizedRequestRatio=165/r.actualRegionCalls;}
 ]){let result=goalRegion('fixture-target');goalReadRetry(result,'optimized');result=JSON.parse(JSON.stringify(result));change(result.actionSamples.find(row=>row.case==='region_stream_region'),result.actionSamples.find(row=>row.case==='region_stream_requests'));assert.throws(()=>regionStreamProof(result,'fixture-target'));}
});

async function writePublicationFixture(repository){
 const repo=resolve(repository),owner=dirname(repo);
 assert.equal(basename(repo),'repository');assert.match(basename(owner),/^minecraft-alpha-snapshot-[0-9a-f]{32}$/);
 const withinTemp=relative(resolve(tmpdir()),owner);assert(withinTemp&&!withinTemp.startsWith('..')&&!resolve(tmpdir(),withinTemp).includes('..'));
 const manifest=resolve(repo,'build/alpha-manifest.json'),report=JSON.parse(await readFile(manifest,'utf8'));
 assert.equal(report.type,'internal_alpha_build');assert.equal(report.artifacts.length,5);
 const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
 const write=async(path,content)=>{const file=resolve(repo,path);assert(!relative(repo,file).startsWith('..'));await mkdir(dirname(file),{recursive:true});await writeFile(file,content);return digest(content);};
 const put=async(path,value)=>{const bytes=JSON.stringify(value)+'\n';return{result:path,resultSha256:await write(path,bytes)};};
 const worldTools='components/validation_tools/conformance-world_read/task/';
 const sourcePaths=['settings.gradle.kts','source.txt','gradle.properties','gradle/libs.versions.toml','components/protocol_schema/src/main/openapi/minecraft-control-v0.json','components/runtime_core/src/main/java/Fixture.java','components/runtime_safety/src/main/java/Fixture.java','components/companion/src/server.ts','components/companion/package.json','components/companion/package-lock.json',...['world-read-smoke.mjs','Invoke-WorldReadSmoke.ps1','WorldReadFixture.ps1','input-context-actions.mjs','region-stream-actions.mjs','world-placement-receipts-actions.mjs','continuous-performance-actions.mjs','interaction-baseline-actions.mjs','mcp-world-baseline.mjs'].map(name=>worldTools+name),...report.artifacts.flatMap(row=>[`versions/${row.target}/target.properties`,`versions/${row.target}/build.gradle.kts`,`versions/${row.target}/src/main/java/Fixture.java`])];
 const bindings={};for(const path of sourcePaths){let bytes;if(['settings.gradle.kts','source.txt'].includes(path))bytes=await readFile(resolve(repo,path));else bytes=path==='components/companion/package.json'?JSON.stringify({version:'fixture-version'}):'Synthetic alpha contract source '+path+'\n';bindings[path]=await write(path,bytes);}
 report.sourceHashes=Object.fromEntries(Object.entries(bindings).sort(([a],[b])=>a<b?-1:1));report.sourceFingerprint=digest(JSON.stringify(report.sourceHashes));report.distribution.directory=report.distribution.directory.replace(/[0-9a-f]{8}$/,report.sourceFingerprint.slice(0,8));
 report.publicationProfile=CONTINUOUS_PROFILE;report.nativeSmokeScope='full';report.fullNativeSmoke=true;
 report.nativeSmoke=[];report.uiRegression=[];report.continuousInput=[];report.controlCollaboration=[];
 const attach=result=>Object.assign(result,{sourceHashes:report.sourceHashes});
 for(const{target}of report.artifacts){
  const fixtures=goalFixtures(target);for(const[scenario,result]of Object.entries(fixtures)){attach(result);result.fixtureInput.targetFactsSha256=bindings[`versions/${target}/target.properties`];await put(`validations/conformance-world_read/result/${target}-${scenario}.json`,result);}
  const full={...valid(),target,scenario:'full',status:'PASS',evidence:'development_runtime_native',fullSmoke:true,fullWorldMilestone:false,checks:[...valid().checks,...validFeedback().checks],cleanShutdown:true,sourceChangedDuringRun:false,persistentWriteCalls:0};full.actionSamples.push(...validFeedback().actionSamples);attach(full);const fullRecord=await put(`validations/conformance-world_read/result/${target}.json`,full);report.nativeSmoke.push({target,fullSmoke:true,sourceMatchesCurrent:true,cleanShutdown:true,...fullRecord,consumerPriority:consumerPriorityProof(full,target),consumerFeedback:consumerFeedbackProof(full,target)});
  const ui=validUiFixture().r;ui.target=target;for(const row of ui.uiDebugSamples)if(['native_hud_content_guard_probe','native_toast_content_guard_probe'].includes(row.case))row.target=target;
  if(target.startsWith('1.')){ui.uiDebugSamples.find(row=>row.case==='native_toast_content_guard_probe').resetConsumption='native_render_on_probe_owned_toast_no_pixel_canvas';ui.editorSamples.find(row=>row.editor==='book_title'&&row.transport==='mcp_stdio').textReplaceStrategy='backspace_from_end';}
  if(target==='1.20.1-forge'){ui.uiDebugSamples=ui.uiDebugSamples.filter(row=>!(row.case==='native_toast_hide'&&row.kind==='system'));ui.uiDebugSamples.push({case:'native_system_toast_hide_absent',nativeMethodAbsent:true,inputDelta:0},{case:'legacy_system_toast_fixture_cleanup',probeOnly:true,notAProductHideCapability:true});}
  attach(ui);report.uiRegression.push({target,...await put(`validations/conformance-vanilla_ui/result/${target}.json`,ui),...uiRegressionProof(ui,target)});
  const continuous=validContinuousInput();continuous.target=target;continuous.actionSamples.find(row=>row.case==='continuous_input_complete').target=target;attach(continuous);report.continuousInput.push({target,...await put(`validations/conformance-world_read/result/${target}-continuous-input.json`,continuous),...continuousInputProof(continuous,target)});
  const modes={...goalHeader(target,'mode-collaboration',8),actionSamples:[{case:'native_operate',receipt:{route:'NATIVE'}},{case:'takeover_chunk_fields',read:{loaded:true,loadRequested:false,scheduledBlockTicks:[]},takeover:{loaded:true,loadRequested:false,scheduledBlockTicks:[]},remote:{available:false}},{case:'input_handback',physicalEscTested:false}]};
  const batch={...goalHeader(target,'command-discovery-batch',8),actionSamples:[{case:'native_command_batch',batch:{submittedCount:3,requiresControlLease:false,inputDispatched:false,effectConfirmed:false,serverFeedbackCorrelated:false}},{case:'native_batch_boundary',boundary:{commandCount:32,submittedCount:32}},{case:'native_batch_stops_on_first_error',result:{commands:[{},{},{status:'not_attempted'}]}},{case:'mcp_command_discovery_batch'}]};
  const resource=validResourceVersion();resource.target=target;const completion=validServerCompletion();completion.target=target;completion.actionSamples.find(row=>row.case==='mcp_server_completion').target=target;
  for(const result of [modes,batch,resource,completion]){attach(result);report.controlCollaboration.push({target,scenario:result.scenario,...await put(`validations/conformance-world_read/result/${target}-${result.scenario}.json`,result),...collaborationIncrementProof(result,target,result.scenario)});}
 }
 for(const proof of report.packagedRuntime){const live=JSON.parse(await readFile(resolve(repo,proof.result),'utf8'));live.sourceFingerprint=report.sourceFingerprint;Object.assign(proof,await put(proof.result,live),{sourceFingerprint:report.sourceFingerprint});}
 report.continuousGameplay=await collectContinuousGameplayProof(repo,report.artifacts.map(row=>row.target),report.sourceHashes);report.delivery=report.continuousGameplay.delivery;report.fullMilestonesComplete=false;report.performanceAcceptance=report.continuousGameplay.performanceAcceptance;await verifyContinuousPublication(report,repo);await write(manifest,JSON.stringify(report)+'\n');
}

if(publicationFixtureMode){
 try{assert.equal(process.argv.length,4);await writePublicationFixture(process.argv[3]);console.log('Synthetic publication fixture: READY');}
 catch(error){console.error(error.stack??error);process.exitCode=1;}
}
