import test from 'node:test';
import assert from 'node:assert/strict';
import {consumerPriorityProof,consumerFeedbackProof,uiRegressionProof,collaborationIncrementProof} from './alpha-evidence.mjs';

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
test('full alpha refuses targeted UI, stale sources or unverified process cleanup',()=>{
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
