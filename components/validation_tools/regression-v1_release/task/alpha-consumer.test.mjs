import nodeTest from 'node:test';
import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir} from 'node:fs/promises';
import {createHash,randomUUID} from 'node:crypto';
import {resolve,relative,dirname,basename} from 'node:path';
import {fileURLToPath} from 'node:url';
import {tmpdir} from 'node:os';
import {consumerPriorityProof,consumerFeedbackProof,uiRegressionProof,collaborationIncrementProof,continuousInputProof,canonicalSourceHashes,CONTINUOUS_PROFILE,collectContinuousGameplayProof,verifyContinuousPublication,inputContextProof,regionStreamProof,placementReceiptsProof,continuousPerformanceProof,interactionBaselineProof} from './alpha-evidence.mjs';
import {baselineStatistics} from '../../conformance-world_read/task/interaction-baseline-actions.mjs';
import {validatePlayerStream,decodeRegionPage} from '../../conformance-world_read/task/region-stream-actions.mjs';
import {placementReceiptCases} from '../../conformance-world_read/task/world-placement-receipts-actions.mjs';
import {performanceAttackSteps,performanceAttackComparison} from '../../conformance-world_read/task/continuous-performance-actions.mjs';
import {verifyEvidenceSources,candidateBindingRequired} from './alpha-evidence.mjs';
import {DEPENDENCY_PROFILE,DEPENDENCY_SCOPE,readDependencyBaseline,dependencySourceProof,dependencyPerformance} from './dependency-proof.mjs';
import {E_UI_PROFILE,E_UI_REQUIRED_CASE_CONTRACTS,eUiRegressionProof,eUiDemoProof,eUiKnownCompletionProof,eUiFiniteOperationProof,blockEntitySelectorProof,firstCameraProof,commandCollaborationProof,eUiProofSlots,eUiScenarioSources,verifyEUiEvidenceSources,verifyEUiCandidateBindings,collectEUiProof} from './alpha-evidence.mjs';
import {historicalTrialBaseline} from './dependency-proof.mjs';

const publicationFixtureMode=process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)&&process.argv[2]==='--write-publication-fixture';
// The explicit fixture CLI does not register tests or emit TAP. Production never imports this file.
const test=publicationFixtureMode?()=>{}:nodeTest;

async function dependencyFixture(){
 const root=resolve(import.meta.dirname,'../../build/test/dependency-proof-'+randomUUID()),repo=resolve(root,'repository');
 const digest=value=>createHash('sha256').update(value).digest('hex'),write=async(path,bytes)=>{await mkdir(dirname(path),{recursive:true});await writeFile(path,bytes);};
 const targets=['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-fabric','26.3-neoforge'];
 const originals={
  'versions/1.20.1-forge/target.properties':'loader_version=47.4.28\njava_version=17\n',
  'versions/1.21.1-neoforge/target.properties':'loader_version=21.1.259\nloader_minimum=21.1.248\njava_version=21\n',
  'versions/26.1.2-neoforge/target.properties':'loader_version=26.1.2.117\nloader_minimum=26.1.2.97\njava_version=25\n',
  'versions/26.3-neoforge/target.properties':'loader_version=26.3.0.77\njava_version=25\n',
  'versions/26.3-fabric/target.properties':'loader_version=0.19.5\nloader_minimum=0.19.3\njava_version=25\nfabric_api_version=0.162.0+26.3\n',
  'gradle/libs.versions.toml':'[versions]\nloom = "1.18.3"\nmoddev = "2.0.148"\n',
  'components/runtime_core/src/main/java/Example.java':'class Example {}\n'
 };
 const replacements={'21.1.259':'21.1.264','26.1.2.117':'26.1.2.122','26.3.0.77':'26.3.0.94','0.162.0+26.3':'0.164.0+26.3','1.18.3':'1.19.1'},sourceHashes={},currentSources={};
 for(const path of Object.keys(originals).sort()){const before=originals[path];let after=before;for(const[from,to]of Object.entries(replacements))after=after.replace(from,to);sourceHashes[path]=digest(before);currentSources[path]=digest(after);await write(resolve(repo,path),after);}
 const packageBefore={name:'synthetic-companion',version:'test',dependencies:{zod:'4.6.5',unchanged:'1.0.0'}},packageAfter=structuredClone(packageBefore);packageAfter.dependencies.zod='4.6.7';
 const lockBefore={lockfileVersion:3,packages:{'':{dependencies:{zod:'4.6.5'}},'node_modules/zod':{version:'4.6.5',resolved:'https://registry.npmjs.org/zod/-/zod-4.6.5.tgz',integrity:'sha512-b2xk',license:'MIT'}}},lockAfter=structuredClone(lockBefore);lockAfter.packages[''].dependencies.zod='4.6.7';Object.assign(lockAfter.packages['node_modules/zod'],{version:'4.6.7',resolved:'https://registry.npmjs.org/zod/-/zod-4.6.7.tgz',integrity:'sha512-bmV3'});
 for(const[name,before,after]of [['package.json',packageBefore,packageAfter],['package-lock.json',lockBefore,lockAfter]]){const path='components/companion/'+name;originals[path]=JSON.stringify(before);await write(resolve(repo,path),JSON.stringify(after));}
 const fingerprint=digest(JSON.stringify(sourceHashes)),name='20261020-0337-'+fingerprint.slice(0,8),directory=resolve(root,name),proof=target=>({target,resultSha256:'a'.repeat(64)});
 const artifacts=targets.map(target=>({target,fileName:'product-'+target+'.jar',sha256:digest('synthetic Jar '+target)}));
 const report={type:'internal_alpha_build',publicationProfile:'continuous_gameplay_trial_v1',sourceFingerprint:fingerprint,sourceHashes,commit:'synthetic-baseline',nativeSmokeScope:'full',fullNativeSmoke:true,fullMilestonesComplete:false,
  artifacts,nativeSmoke:targets.map(proof),uiRegression:targets.map(proof),continuousInput:targets.map(proof),packagedRuntime:artifacts.map(row=>({...proof(row.target),artifactSha256:row.sha256,sourceFingerprint:fingerprint})),
  controlCollaboration:targets.flatMap(target=>['mode-collaboration','command-discovery-batch','resource-version','server-completion'].map(scenario=>({...proof(target),scenario}))),
  continuousGameplay:{readyFor:'trial_not_goal_acceptance',proofs:targets.flatMap(target=>['input-context','region-stream','world-placement-receipts','continuous-performance','interaction-baseline'].map(scenario=>({...proof(target),scenario})))},
  performanceAcceptance:{status:'NOT_MET',goalPerformancePass:false,targets:targets.map(target=>({target,targetResult:'NOT_MET'}))},agentGuide:{fileName:'AGENT_GUIDE.md',sha256:digest('synthetic guide')}};
 for(const row of artifacts)await write(resolve(directory,row.fileName),'synthetic Jar '+row.target);await write(resolve(directory,'AGENT_GUIDE.md'),'synthetic guide');
 const bytes=JSON.stringify(report);await write(resolve(directory,'alpha-manifest.json'),bytes);const identity={name,sourceFingerprint:fingerprint,manifestSha256:digest(bytes)},baseline=await readDependencyBaseline(directory,identity);
 return{repo,directory,identity,baseline,currentSources,originals,write,digest,readOriginal:async path=>Buffer.from(originals[path])};
}

test('dependency profile binds an immutable seven-file baseline and only five declared facts plus the Zod pair',async()=>{
 const f=await dependencyFixture(),original=await readFile(resolve(f.directory,'alpha-manifest.json'));
 const proof=await dependencySourceProof(f.repo,f.baseline,f.currentSources,f.readOriginal);
 assert.equal(DEPENDENCY_PROFILE,'dependency_update_trial_v1');assert.equal(DEPENDENCY_SCOPE,'dependency_update_installed_world_smoke');assert.equal(proof.changes.length,5);assert.equal(proof.historicalLogicalSlots,65);assert.equal(proof.historicalOnly,true);assert.equal(proof.currentFullMatrixClaimed,false);assert.equal(proof.companion.files.length,2);assert.equal(proof.companion.liveMcpAcceptanceClaimed,false);
 const performance=dependencyPerformance(f.baseline);assert.equal(performance.status,'NOT_MET');assert.equal(performance.newMeasurements,false);assert.equal(performance.goalPerformancePass,false);assert.deepEqual(performance.historical,f.baseline.report.performanceAcceptance);assert.deepEqual(await readFile(resolve(f.directory,'alpha-manifest.json')),original);
 await assert.rejects(readDependencyBaseline(f.directory),/Unexpected dependency baseline/,'Production must not accept the synthetic baseline identity');
});

test('dependency baseline refuses changed manifest, guide, every Jar or an extra file without rewriting any source',async()=>{
 for(const kind of ['alpha-manifest.json','AGENT_GUIDE.md','product-1.20.1-forge.jar','product-1.21.1-neoforge.jar','product-26.1.2-neoforge.jar','product-26.3-fabric.jar','product-26.3-neoforge.jar','unexpected.txt']){
  const f=await dependencyFixture();await f.write(resolve(f.directory,kind),'tampered');await assert.rejects(readDependencyBaseline(f.directory,f.identity),undefined,kind);
 }
});

test('dependency-only acceptance rejects product edits, changed identity, Java or minimum Loader and unrelated catalog changes',async()=>{
 for(const[path,edit]of [
  ['components/runtime_core/src/main/java/Example.java',value=>value+'class Changed {}\n'],
  ['versions/26.3-fabric/target.properties',value=>value.replace('java_version=25','java_version=26')],
  ['versions/26.3-fabric/target.properties',value=>value.replace('loader_minimum=0.19.3','loader_minimum=0.19.4')],
  ['versions/1.20.1-forge/target.properties',value=>value.replace('47.4.28','47.4.29')],
  ['gradle/libs.versions.toml',value=>value.replace('moddev = "2.0.148"','moddev = "2.0.149"')]
 ]){const f=await dependencyFixture(),bytes=edit(await readFile(resolve(f.repo,path),'utf8'));await f.write(resolve(f.repo,path),bytes);f.currentSources[path]=f.digest(bytes);await assert.rejects(dependencySourceProof(f.repo,f.baseline,f.currentSources,f.readOriginal),undefined,path);}
 const f=await dependencyFixture();f.currentSources['versions/new-target/src/main/Changed.java']='a'.repeat(64);await assert.rejects(dependencySourceProof(f.repo,f.baseline,f.currentSources,f.readOriginal),/Product source set changed/);
 const wrong=await dependencyFixture();await assert.rejects(dependencySourceProof(wrong.repo,wrong.baseline,wrong.currentSources,async path=>Buffer.from(wrong.originals[path]+'\n')),/Baseline Git facts differ/);
});

test('Companion dependency proof refuses unrelated package or lockfile edits and records actual current digests',async()=>{
 for(const path of ['components/companion/package.json','components/companion/package-lock.json']){
  const f=await dependencyFixture(),value=JSON.parse(await readFile(resolve(f.repo,path)));value.unrelated='not a Zod update';await f.write(resolve(f.repo,path),JSON.stringify(value));await assert.rejects(dependencySourceProof(f.repo,f.baseline,f.currentSources,f.readOriginal),/limited to Zod/);
 }
 const f=await dependencyFixture(),proof=await dependencySourceProof(f.repo,f.baseline,f.currentSources,f.readOriginal);for(const file of proof.companion.files)assert.equal(file.sha256,f.digest(await readFile(resolve(f.repo,file.source))));
});

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

test('continuous-profile UI candidate minimum binds native sources and facts while full-world requires metadata too',async()=>{
 const repo=resolve(import.meta.dirname,'../../../..'),target='1.20.1-forge';
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

function eUiFixture(target='fixture-target'){
 const r=validUiFixture().r;r.target=target;
 for(const row of r.uiDebugSamples)if(['native_hud_content_guard_probe','native_toast_content_guard_probe'].includes(row.case))row.target=target;
 if(target.startsWith('1.')){r.uiDebugSamples.find(row=>row.case==='native_toast_content_guard_probe').resetConsumption='native_render_on_probe_owned_toast_no_pixel_canvas';r.editorSamples.find(row=>row.editor==='book_title'&&row.transport==='mcp_stdio').textReplaceStrategy='backspace_from_end';}
 if(target==='1.20.1-forge'){r.uiDebugSamples=r.uiDebugSamples.filter(row=>!(row.case==='native_toast_hide'&&row.kind==='system'));r.uiDebugSamples.push({case:'native_system_toast_hide_absent',nativeMethodAbsent:true,inputDelta:0},{case:'legacy_system_toast_fixture_cleanup',probeOnly:true,notAProductHideCapability:true});}
 r.uiCorrectnessSamples=[{case:'declared_slot_action_aliases',feedback:'CF-071',rightClickSplit:true,shiftClickTransferred:true,semanticTarget:true,handwrittenCoordinates:false,act:'GAME_ROUTED',heldKeys:0,heldButtons:0},{case:'hud_page_readiness',feedback:['CF-019','CF-023'],hudReady:true,realMcpStdio:true,hiddenTargetProbed:false,inputDelta:0,nativeFailure:{readiness:{failures:['SCREEN_NOT_READY']}},readiness:{failures:['TARGET_NOT_FOUND_OR_NOT_VISIBLE']}}];
 r.suggestionSamples=[{case:'native_suggestion_selection',original:0,value:1,restored:true,contentGuardAvailable:true,staleRejected:true,draftUnchanged:true,nativeOffsetUnchanged:true,nativeSetter:'SuggestionsList.select(int)',inputDelta:0,playerLeaseRequired:false,suggestionUseTested:false,commandExecutionTested:false},{case:'native_suggestion_consumer_handoff',actualTransports:['mcp_stdio_debug','web_user_isolation'],draftUnchanged:true,privateSelection:true,typedInputDelta:0},{case:'native_suggestion_owner_replaced',rejected:'UI_CONTEXT_UNAVAILABLE',inputDelta:0}];
 r.editorSamples.push({editor:'debug_suggestion_selection',transport:'mcp_stdio_debug',status:'PASS',inputDelta:0,playerLeaseAcquired:false,typedRecovery:true},{editor:'debug_suggestion_selection',transport:'web_user_isolation',status:'PASS',debugMetadataLeaked:false,automaticLeaseAcquired:false});
 const draw=(controlKey,more={})=>({controlKey,visible:true,active:false,actions:[],iconSource:'last_completed_native_hud_draw',boundsAvailable:true,x:1,y:2,width:13,height:2,...more});
 r.hudCompletionSamples=['durability','cooldown'].map(kind=>({case:'hud_item_draw',target,kind,category:'USER',authority:'native_completed_hud_draw',rawItemStateExposed:false,input:goalInput(),selected:{selected:true,x:1,y:2,item:kind==='durability'?'minecraft:iron_sword':'minecraft:ender_pearl'},bar:draw('hud.item.'+kind+'.1.2',{role:'progress_bar',valueMinimum:0,value:2,valueMaximum:13,valueSemantics:kind==='durability'?'drawn_fill_pixels_not_raw_item_state':'drawn_fill_pixels_not_remaining_ticks'}),...(kind==='durability'?{name:{label:'Iron Sword',labelSource:'last_completed_native_hud_draw'}}:{})}));
 r.hudCompletionSamples.push(...['hud_item_occlusion','hud_context_occlusion'].map(name=>({case:name,target,f1UserNodes:0,inventoryUserNodes:0,input:goalInput()})),{case:'hud_item_cooldown_expired',target,remainingTicksExposed:false,input:goalInput()},...['experience','jump'].map(family=>({case:'hud_context_draw',target,family,category:'USER',rawPlayerStatisticsExposed:false,input:goalInput(),nodes:[draw('hud.context.'+family+'.background',{resourceId:'hud/'+family+'_bar_background'}),draw('hud.context.'+family+'.progress',{role:'progress_bar',value:2}),...(family==='experience'?[draw('hud.context.experience.level',{label:'7'})]:[])],...(family==='jump'?{released:goalReleased()}:{})})));
 return r;
}

// Controlled receipts exercise production proof consumers; none is a Live Minecraft result.
function eUiDemoFixture(target='26.3-neoforge'){
 const before={...goalInput(0,1,'title'),target,clientTick:0,inputContextAvailable:false},after={...goalInput(1,2,'world'),target,clientTick:10};
 const entry={before,after,requestOutcome:'response_received',requestReplayed:false,independentlyObserved:true,response:{directMutationUsed:false}};
 const receipt={outcome:'world_exit_independently_observed',requestOutcome:'response_received',requestReplayed:false,before:{...after,clientTick:20},after:{...goalInput(2,3,'returned-title'),target,clientTick:40,inputContextAvailable:false},screenClass:'net.minecraft.client.gui.screens.TitleScreen',overlayIdentity:0,released:goalReleased()};
 const exit={type:'ui.tree',domain:'UI',dataCategory:'USER',target,clientTick:30,screenClass:'net.minecraft.client.gui.screens.TitleScreen',overlayIdentity:0,screenIdentity:100,screenRevision:10,treeTruncated:false};
 const launch={target,processId:42,validationOwned:true,maximumHeapBytes:4294967296,argumentCoverage:'actual_java_command_line_demo_flag_only',gameArguments:['--demo'],instanceDirectory:'D:/owned/validations/conformance-vanilla_ui/instance/'+target+'/client'};
 const row={case:'hud_demo_native_launch',target,category:'USER',launch,titleEntry:{translationKey:'menu.playdemo',visible:true,active:true,actions:['click']},entry,player:{...goalPlayer(),gameMode:'survival'},nativeDemoNodes:[eUiLateNode('demo','Remaining time: 99:59')],exit,returnedTitle:'net.minecraft.client.gui.screens.TitleScreen',evidenceAuthority:'PLAYTEST',resetClicked:false,externalLinkClicked:false,handwrittenCoordinates:false,demoModeMutationUsed:false,expiredDemoTested:false,nativeRemainingTimeDrawObserved:true};
 return{target,status:'PASS',scenario:'hud-demo',phase:'test',runtime:'development_native',cleanShutdown:true,processExitCode:0,endpointRemoved:true,sourceChangedDuringRun:false,persistentWriteCalls:0,noMultimodal:true,screenshotAssertions:0,fixtureSha256:'a'.repeat(64),launchAttestation:structuredClone(launch),checks:[{name:'HUD: Demo: real --demo Title entry enters the native world and draws its remaining-time HUD',status:'PASS'}],hudCompletionSamples:[row],worldExitSamples:[receipt]};
}

function eUiLateNode(family,label='actual native text'){
 return{renderPhase:family,role:'hud_text',visible:true,active:false,actions:[],boundsAvailable:true,labelSource:'last_completed_native_gui_text_draw',iconSource:'last_completed_native_gui_draw',boundsSemantics:'native_text_draw_viewport_scissor_intersection',x:0,y:0,width:100,height:9,opacity:1,textPhase:'drawn',label};
}

function eUiSavingFixture(target){
 const common={target,category:'USER',commandUsed:false,requestReplayed:false,physicalEscTested:false,agentEscOnly:true,naturalSaveDurationValidated:false,performanceValidated:false,persistentWriteApiCalls:0,inputBefore:goalInput(),inputAfter:goalInput()};
 const base={probeOnly:true,arrangeAuthority:'DIAGNOSTIC_bounded_native_pause_save_timing',target,processId:42,fixtureId:'controlled-pause-save-fixture',failure:'',barrierMaximumMillis:3000,barrierTimedOut:false,barrierElapsedMillis:0,savingStateWrittenByProbe:false,indicatorAlphaWrittenByProbe:false,additionalSaveInvokedByProbe:false,naturalSaveDurationValidated:false,performanceValidated:false,worldFilesTouched:'owned_validation_world_only',persistentWriteApiCalls:0,armed:false,readyForArm:true,resumeObserved:false,nativeSaveStarted:false,nativeSaveReturned:false,nativeSaveResult:false,nativeSavingFlagCleared:false,nativeSaveSuppressedLog:false,nativeSaveFlush:false,nativeSaveForced:false,barrierActive:false,nativeTextDrawObserved:false,drawWhileActualSaving:false,selectedNativePauseSaveCalls:0,nativeScreenClass:'',nativeTextLabel:'',nativeTextOpacity:0};
 if(target==='1.20.1-forge')return{...common,case:'hud_native_saving_indicator_not_applicable',disposition:'NOT_RENDERED_BY_DEFAULT_FORGE_GUI',userDrawValidated:false,draw:[],selectedNativePauseSaveCalls:0,pauseRequests:0,diagnosticTimingArranged:false,applicability:{...base,nativeIndicatorDisposition:'NOT_RENDERED_BY_DEFAULT_FORGE_GUI',nativeIndicatorSupportSource:'actual_hud_renderer_and_registered_overlay_ids',actualHudRenderer:'net.minecraftforge.client.gui.overlay.ForgeGui',actualRegisteredOverlayIds:['minecraft:hotbar','minecraft:player_list'],nativeVanillaOverlayIds:['minecraft:hotbar','minecraft:player_list']}};
 const drawn={...base,armed:true,resumeObserved:true,selectedNativePauseSaveCalls:1,nativeSaveStarted:true,nativeTextDrawObserved:true,drawWhileActualSaving:true,nativeScreenClass:'net.minecraft.client.gui.screens.PauseScreen',nativeTextLabel:'Saving world',nativeTextOpacity:1};
 return{...common,case:'hud_native_saving_indicator',draw:[eUiLateNode('saving_indicator','Saving world')],pauseKey:256,pauseRequests:1,selectedNativePauseSaveCalls:1,diagnosticTimingArranged:true,prepared:{...base},armed:{...base,armed:true},resumed:{...base,armed:true,resumeObserved:true},nativeDraw:drawn,nativeCompleted:{...drawn,nativeSaveReturned:true,nativeSavingFlagCleared:true}};
}

function eUiLegacyToastSamples(target){
 const owner=index=>'toast:00000000-0000-0000-0000-'+index.toString(16).padStart(12,'0'),lifecycle=(kind,index)=>{const contextKey=owner(index),node=(renderPhase,textPhase,label)=>({contextKey,class:'net.minecraft.client.gui.components.toasts.'+kind+'Toast',role:'toast',visible:true,active:false,actions:[],labelSource:'last_native_toast_render',boundsAvailable:true,boundsSemantics:'native_toast_draw_viewport_intersection',x:0,y:0,width:160,height:32,renderPhase,textPhase,label});return{kind,contextKey,entering:node('entering','withheld_partial_or_occluded',''),full:node('fully_shown',kind==='Advancement'?'header_and_title':'static','Actual '+kind+' Native text'),exiting:node('exiting','withheld_partial_or_occluded',''),absent:{clientTick:1000,remaining:0},tutorialHideRequestedByFixture:kind==='Tutorial',naturalGameplayCompletionClaimed:false};};
 const scope={target,category:'USER',source:'probe_only_native_toast_construction',arrangeAuthority:'DIAGNOSTIC_native_display_fixture',assertAuthority:'USER_NATIVE_DRAW',before:goalInput(),input:goalInput(1),fixtureReset:true,gameplayTutorialCompletionClaimed:false,gameplayUnlockChanged:false,volumeWritten:false,worldFilesWritten:false};
 return[...['System','Tutorial','Advancement','Recipe'].map((kind,index)=>({case:'native_toast_lifecycle',...scope,...lifecycle(kind,index+1)})),{case:'native_toast_queue_boundary',...scope,blockers:Array.from({length:5},(_,index)=>({contextKey:owner(index+20),label:'Queue blocker '+(index+1),renderPhase:'fully_shown'})),observations:[100,101,102].map(clientTick=>({clientTick,recipeNodes:0,visibleBlockers:5,forbiddenTextPresent:false})),released:true,queuedRecipe:lifecycle('Recipe',10),queuedTextExposed:false}];
}

function eUiManagementSamples(target){
 const modern=!target.startsWith('1.'),hash=text=>createHash('sha256').update(text).digest('hex'),witness=(index,count,extra)=>({probeOnly:true,validationName:'conformance-vanilla_ui',target,processId:42,fixtureAuthority:'DIAGNOSTIC_native_server_receipt_observation',worldName:'UI-Conformance',singlePrivatePlayerBound:true,chunkLoadRequested:false,worldMutationUsedByProbe:false,attemptId:index.toString(16).padStart(32,'0'),nativePlayerCommands:count,commandBlockPackets:0,commandMinecartPackets:0,jigsawConfigurationPackets:0,jigsawGenerationPackets:0,structureUpdatePackets:0,structureScanPackets:0,structureSavePackets:0,structureLoadPackets:0,...(modern?{testBlockPackets:0,testInstanceActionPackets:0}:{}),...extra});
 const failed={state:'failed',errorCode:'PLAYER_COMMAND_OUT_OF_SCOPE',result:{commandSubmissions:[]}},scope=(caseName,className)=>({case:caseName,target,screenClass:'net.minecraft.client.gui.screens.inventory.'+className,nativeScreens:['net.minecraft.client.gui.screens.inventory.'+className],screenIdentity:100,screenRevision:1,openOutcome:{state:'completed'},category:'USER',arrangeAuthority:'DIAGNOSTIC_current_player_commands_owned_copy_only',actAuthority:'PLAYTEST',readbackAuthority:'DIAGNOSTIC_native_server_receipt_observation',managementExecutionValidated:false,semanticTargets:true,handwrittenCoordinates:false,directCallbackUsed:false,persistentWriteCalls:0,inputAfter:goalInput(3)}),cart={uiFixtureMinecartCount:1,uiFixtureMinecartCommandHash:hash('')};
 const rows=[{...scope('ui_command_minecart_configuration_denied','MinecartCommandBlockEditScreen'),before:witness(1,10,cart),after:witness(2,11,cart),editedScreenRevision:2,result:structuredClone(failed),inputBefore:goalInput(),semanticActions:['world.use','draft.type_text','gui.done.denied']}];
 if(modern){const block={storedTestMode:'LOG',storedTestMessageHash:hash('')},instance={storedTestInstanceHash:hash('controlled_native_test_instance')};rows.push({...scope('ui_test_block_configuration_denied','TestBlockEditScreen'),before:witness(3,20,block),after:witness(4,21,block),result:structuredClone(failed),semanticActions:['world.use','draft.type_text','gui.done.denied']},{...scope('ui_test_instance_management_denied','TestInstanceBlockEditScreen'),openOutcome:structuredClone(failed),before:witness(5,30,instance),afterCancel:witness(6,31,instance),after:witness(7,32,instance),query:structuredClone(failed),reset:structuredClone(failed),resetOpenOutcome:structuredClone(failed),localDraftRollbackClaimed:false,semanticActions:['world.use','query.draft.denied','native.cancel','world.use','reset.denied']});}
 else rows.push({case:'ui_test_management_native_absent',target,nativeScreens:[],screens:['TestBlockEditScreen','TestInstanceBlockEditScreen'],disposition:'NATIVE_CLASS_ABSENT',operationCoverage:false});
 return rows;
}

function eUiKnownFixture(target='26.3-neoforge'){
 const demoResult=eUiDemoFixture(target),demo=demoResult.hudCompletionSamples[0],draw=(extra={})=>({visible:true,active:false,actions:[],boundsAvailable:true,x:0,y:0,width:9,height:9,...extra}),hud=[],vitals=[],local=[],nav=[];
 const result={target,scenario:'all',status:'PASS',launchAttestation:{processId:42},hudCompletionSamples:hud,vitalSamples:vitals,localOwnerSamples:local,navigationCompletionSamples:nav,worldExitSamples:demoResult.worldExitSamples};
 hud.push(...eUiLegacyToastSamples(target));
 hud.push({case:'hud_hardcore_native_world',target,category:'USER',nativeWorldCreation:true,selectedMode:{label:'Game Mode: Hardcore'},worldName:'UI-Hardcore-Validation',world:{worldName:'UI-Hardcore-Validation',worldId:'owned-created-hardcore'},entry:demo.entry,exit:demo.exit,player:demo.player,heartNodes:[draw({controlKey:'hud.vitals.health.0',label:'hardcore_full',iconSource:'last_completed_native_hud_draw',valueSemantics:'ordered_native_glyph_composition_not_raw_statistics'})],hardcoreFlagMutationUsed:false,handwrittenCoordinates:false,deathTested:false,worldDeletionUsed:false});
 for(const variant of ['blinking','frozen'])vitals.push({case:'hud_vital_variant',variant,target,category:'USER',mode:{mode:'READ'},input:goalInput(),rawPlayerStatisticsExposed:false,nodes:[{label:variant==='blinking'?'full_blinking':'frozen_full'}]});vitals.push({case:'hud_vital_blink_expired',nativeClockObserved:true,firstClientTick:10,afterClientTick:35},{case:'hud_vital_thaw',nativeGlyphObserved:true});
 for(const phase of ['crosshair','attack_indicator','pumpkin','spyglass'])hud.push({case:'hud_camera_draw',target,category:'USER',input:goalInput(),rawPlayerStatisticsExposed:false,finalPixelAssertion:false,nodes:[draw({renderPhase:phase,resourceId:'minecraft:'+phase})]});hud.push({case:'hud_camera_occlusion',f1Rows:0,thirdPersonCrosshairs:0,inventoryRows:0});
 for(const kind of ['portal','sleep','nausea','vignette'])hud.push({case:'hud_camera_variant_'+kind,target,dataCategory:'USER',assertSource:'USER_native_completed_hud_draw',directRendererSetterUsed:false,rawRendererValuesRead:false,finalPixelAssertion:false,handwrittenCoordinates:false,nodes:[draw()],input:goalInput()});
 const boss={case:'hud_boss_native_variants',target,category:'USER',commandReceiptIsNotDrawProof:true,assertAuthority:'USER_NATIVE_DRAW'};for(const[key,rows,names,widths]of [['red',[20,90,25,95],['red_background','notched_10_background','red_progress','notched_10_progress'],[182,182,91,91]],['blue',[10,15],['blue_background','blue_progress'],[182,91]]])boss[key]={sprites:rows.map((v,index)=>draw({renderPhase:'boss_bar',iconSource:'last_completed_native_hud_draw',width:widths[index],height:5,...(target==='1.20.1-forge'?{textureId:'minecraft:textures/gui/bars.png',textureU:0,textureV:v,atlasWidth:256,atlasHeight:256}:{resourceId:'minecraft:boss_bar/'+names[index]})}))};hud.push(boss,{case:'hud_boss_hidden_reflow',hiddenNameAbsent:true});if(target!=='1.20.1-forge')hud.push({case:'hud_sidebar_fixed_format',rawScoreExposed:false,rows:[{label:'VisibleName: DISPLAY'}]});
 for(const family of ['inventory_effect','tooltip','chat_background','chat_focused','debug_text','overlay_message'])hud.push({case:'hud_late_native_text',target,category:'USER',family,authority:'completed_native_gui_draw',hiddenSourceTextAsserted:false,nodes:[eUiLateNode(family)],input:goalInput()});
 for(const[kind,family]of [['subtitle','subtitle'],['record','overlay_message']])hud.push({case:'hud_native_'+kind,target,category:'USER',authority:'completed_native_gui_draw',audiblePlaybackAsserted:false,draw:kind==='subtitle'?{text:[eUiLateNode(family,'Cow moos')],directions:[eUiLateNode(family,'>')]}:[eUiLateNode(family)],input:goalInput()},{case:'hud_native_'+kind+'_expired',[kind==='subtitle'?'remainingNativeSubtitleDraws':'remainingNativeOverlayDraws']:0,[kind==='subtitle'?'sourceCacheClearedByTool':'hudValueClearedByTool']:false});
 hud.push(eUiSavingFixture(target));
 const formats=target!=='1.20.1-forge';for(const kind of ['roster',...(formats?['fixed','blank']:['numeric']),'hearts','fallback'])hud.push({case:'player_list_'+kind,target,source:'last_completed_native_player_list_draw',actualMultiplayerNetworkValidated:false,serverPlayersCreated:0,rows:80,fixture:{expectedVisibleRows:Array.from({length:80},(_,i)=>({displayName:'visible '+i}))},displayedNames:Array.from({length:80},(_,i)=>'visible '+i),nativeFeatures:{numberFormats:formats,pingFormat:formats?'resource_sprite':'gui_icons_atlas'},pingSprites:formats?Array(6).fill('reviewed_ping'):[],pingAtlasRegions:formats?[]:Array(6).fill({textureId:'minecraft:textures/gui/icons.png'})});hud.push({case:'player_list_visibility',tabReleaseHidden:true,f1Hidden:true,screenHidden:true},{case:'player_list_cleanup',ownedRosterRemoved:true,fixture:{cleaned:true},input:goalInput()});
 if(target.startsWith('26.')){const settings={masterVolume:0,masterVolumeAfter:0};hud.push({case:'hud_music_toast_option_prepared',target,originalLabel:'Music Toast: Never',settings:{...settings,after:'Music Toast: Pause Menu and Toast'},masterWritten:false,debugSetterUsed:false,modeChanged:false,leaseAcquired:false},{case:'hud_music_toast_option_restored',target,preferenceRestored:true,settings:{...settings,after:'Music Toast: Never'},masterWritten:false,debugSetterUsed:false,modeChanged:false,leaseAcquired:false});for(const kind of target==='26.1.2-neoforge'?['NowPlaying']:['Friend','NowPlaying']){const row={case:'hud_resident_toast_user',target,kind,category:'USER',partialTextWithheld:true,queuedTextExposed:false,input:goalInput()};for(const[phase,key]of [['entering','entering'],['fully_shown','full'],['exiting','exiting']])row[key]=draw({role:'toast',renderPhase:phase,labelSource:'last_native_toast_render',textPhase:phase==='fully_shown'?'drawn':'withheld_partial_or_occluded',label:phase==='fully_shown'?'actual native song':''});hud.push(row);}}
 local.push({case:'native_title_lifecycle_timing',countdownReceiptStable:true,nativeTimingChanged:true,timingRestored:true,staleRejected:true,malformedRejected:true,inputDelta:0},{case:'native_title_lifecycle_clear',wholeGroupCleared:true,userTitleAndSubtitleAbsent:true,inputDelta:0},{case:'native_spectator_debug_highlight',actualItemOwnersStable:true,sameIndexNoOp:true,sameTokenAfterNoOp:true,restored:true,staleRejected:true,userPositionUnchanged:true,inputDelta:0,itemActivationUsed:false},...['hud.playerlist.header','hud.playerlist.footer'].map(field=>({case:'native_player_list_text',field,restoredText:true,inputDelta:0,playerLeaseRequired:false})));
 for(const name of ['settings','credits','world_creation','native_game_mode'])nav.push({case:'navigation_'+name+'_completion',semanticTargets:true,handwrittenCoordinates:false,nativeScreens:['net.minecraft.client.gui.screens.TitleScreen']});
 const absent=target==='1.20.1-forge'?['privacy','conduct','restrictions','notice_link']:target==='1.21.1-neoforge'?['privacy','conduct','restrictions']:target==='26.1.2-neoforge'?['privacy']:[];
 const classes={popup:'components.PopupScreen',onboarding:'screens.AccessibilityOnboardingScreen',alert:'screens.AlertScreen',backup:'screens.BackupConfirmScreen',death_confirm:'screens.DeathScreen$TitleConfirmScreen',waiting:'screens.GenericWaitingScreen',telemetry:'screens.telemetry.TelemetryInfoScreen',error:'screens.ErrorScreen',oom:'screens.OutOfMemoryScreen',experiments:'screens.worldselection.ExperimentsScreen',experimental_confirm:'screens.worldselection.ConfirmExperimentalFeaturesScreen',experimental_details:'screens.worldselection.ConfirmExperimentalFeaturesScreen$DetailsScreen',privacy:'screens.PrivacyConfirmLinkScreen',conduct:'screens.multiplayer.CodeOfConductScreen',restrictions:'screens.multiplayer.RestrictionsScreen',confirm_link:'screens.ConfirmLinkScreen',notice_link:'screens.NoticeWithLinkScreen'};
 if(target==='1.20.1-forge')classes.popup='screens.PopupScreen';
 for(const name of ['popup','onboarding','alert','backup','death_confirm','waiting','telemetry','error','oom','experiments','experimental_confirm','experimental_details',...['privacy','conduct','restrictions'].filter(value=>!absent.includes(value)),...(target.startsWith('1.')?['confirm_link']:[]),...(target==='1.21.1-neoforge'?['notice_link']:[])]){const nativeClass='net.minecraft.client.gui.'+classes[name];nav.push({case:'native_confirmation_'+name,semanticTargets:true,handwrittenCoordinates:false,actualFaultInduced:false,semanticActions:[{node:{nodeId:'real-semantic-button'},result:{}}],nativeScreens:[nativeClass],screenClass:nativeClass,...(name==='experimental_details'?{}:{arranged:{probeOnly:true,target,processId:42,case:name,nativeClass}})});}nav.push(...absent.map(nativeCase=>({case:'native_confirmation_unavailable',nativeCase,target,disposition:'NATIVE_CLASS_ABSENT',operationCoverage:false})));
 return result;
}

// Controlled receipts exercise the real publication consumers; none are Live evidence.
function eUiFiniteFixture(target='26.3-neoforge'){
 const result=eUiKnownFixture(target),rows=result.navigationCompletionSamples,modern=!target.startsWith('1.'),pid=42;
 const native=suffix=>'net.minecraft.client.gui.screens.'+suffix,fixtureId='controlled-native-fixture',semantic={semanticTargets:true,handwrittenCoordinates:false},inputs=()=>({before:goalInput(),after:goalInput(2)}),action={node:{nodeId:'controlled-native-button'},result:{}};
 const record=(name,screens,extra={})=>({case:name,...semantic,nativeScreens:screens.map(native),...extra});
 Object.assign(rows.find(row=>row.case==='navigation_settings_completion'),{nativeScreens:[target==='1.20.1-forge'?'LanguageSelectScreen':'FontOptionsScreen','OnlineOptionsScreen','TelemetryInfoScreen'].map(native),settingsChanged:false,telemetryOptInChanged:false,externalNavigationUsed:false,telemetryNativeEntryDisabled:false,telemetryCoverage:'actual_native_entry',fontOperationCoverage:target!=='1.20.1-forge',fontDisposition:target==='1.20.1-forge'?'NATIVE_CLASS_ABSENT':'actual_native_screen_visited'});
 Object.assign(rows.find(row=>row.case==='navigation_credits_completion'),{nativeScreens:[native('CreditsAndAttributionScreen'),native('WinScreen')],nativeWinScreenOpened:true,externalNavigationUsed:false});
 Object.assign(rows.find(row=>row.case==='navigation_world_creation_completion'),{nativeScreens:[native('CreateBuffetWorldScreen'),native('ExperimentsScreen')],worldCreated:false,worldListUnchanged:true,experimentsChanged:false,experimentsDisposition:'actual_native_screen_visited'});
 Object.assign(rows.find(row=>row.case==='navigation_native_game_mode_completion'),{nativeScreens:Array(3).fill(native('GameModeSwitcherScreen')),initialMode:'creative',restored:true,activation:'native_debug_modifier_release',directModeSetterUsed:false,operations:['survival','creative'].map((mode,index)=>({mode,choice:{actions:['hover'],effect:'hover_then_release_debug_modifier'},before:{gameMode:index?'survival':'creative'},uncommitted:{gameMode:index?'survival':'creative'},player:{gameMode:mode},released:{state:'completed'},after:goalInput(2)})),cancelledSelection:{choice:{nodeId:'controlled-alternate-mode',label:'Survival',translationKey:'gameMode.survival',actions:['hover'],effect:'hover_then_release_debug_modifier'},before:{...goalPlayer(),gameMode:'creative',clientTick:100},uncommitted:{...goalPlayer(),gameMode:'creative',clientTick:102},stopped:{type:'input.release_all',inputGeneration:2},later:{...goalPlayer(),gameMode:'creative',clientTick:106},after:goalInput(8),cancelledBy:'explicit_input_release_all',privateSubmitCalled:false}});
 for(const group of [...(target==='1.20.1-forge'?['font_settings']:[]),...(!modern?['world_options_rules_debug','native_dialogs']:[])])rows.push({case:'navigation_native_unavailable',target,group,nativeClasses:[native('AbsentScreen')],disposition:'NATIVE_CLASS_ABSENT',operationCoverage:false,semanticActionsPerformed:false});
 if(modern)rows.push(record('navigation_world_settings_completion',['WorldOptionsScreen','InWorldGameRulesScreen','DebugOptionsScreen'],{gamerulesChanged:false,debugSettingsChanged:false,debugSearchDraftOnly:true}),record('navigation_native_dialog_style_hit',['NoticeDialogScreen','DialogScreen'],{actualScreenChange:true,externalNavigationUsed:false,nativeStyleTarget:{label:'semantic dialog link',clickEventType:'show_dialog',actions:['click']},screens:[{screenIdentity:1},{screenIdentity:2}]}),{case:'navigation_dialog_callback_guards',directCallbackUsed:false,callbackOutcome:'production_guard_rejected',serverSideEffectIndependentlyMeasured:false,rejected:[{label:'blocked command link',target:{label:'blocked command link'},failure:{code:'PLAYER_COMMAND_OUT_OF_SCOPE'}},{label:'blocked custom link',target:{label:'blocked custom link'},failure:{code:'PLAYER_COMMAND_UNCLASSIFIED'}}]},record('navigation_dialog_variants',['MultiButtonDialogScreen','DialogListDialogScreen','ServerLinksDialogScreen','DialogScreen$WarningScreen','WaitingForResponseScreen','ConfirmLinkScreen','NoticeWithLinkScreen'],{externalNavigationUsed:false,realExternalServiceAvailabilityTested:false,worldMutationUsed:false}));
 const callbacks={popup:'popup_closed',onboarding:target==='1.20.1-forge'?'none':'onboarding_closed',alert:'alert_closed',backup:target==='1.20.1-forge'?'none':'backup_cancelled',death_confirm:'death_cancelled',waiting:'waiting_closed',privacy:'privacy_cancelled',conduct:'conduct_declined',restrictions:'none',telemetry:'none',experiments:'none',experimental_confirm:'experimental_cancelled',confirm_link:'confirm_link_cancelled',notice_link:'notice_link_closed'};
 for(const row of rows.filter(row=>row.case.startsWith('native_confirmation_')&&!['native_confirmation_unavailable','native_confirmation_experimental_details'].includes(row.case))){const caseId=row.case.slice('native_confirmation_'.length);Object.assign(row,inputs());row.arranged={...row.arranged,fixtureId,sequence:1,arrangeAuthority:'DIAGNOSTIC_native_screen_fixture',returnedToFixture:false,callbackAction:'none',actualFaultInduced:false,externalServiceUsed:false,worldFilesTouched:false,globalPackRepositoryTouched:false,serverListEntrySupplied:false,experimentOutputCalls:0,selectedPacksBefore:[],selectedPacksAfter:[]};const title=['error','oom'].includes(caseId)||(target==='1.20.1-forge'&&caseId==='onboarding');row.returnedScreen=native(title?'TitleScreen':'UiNativeConfirmationFixtureScreen');if(['error','oom'].includes(caseId))row.quitClientSelected=false;else row.returned={...row.arranged,returnedToFixture:!title,callbackAction:callbacks[caseId]};if(['backup','experiments'].includes(caseId))row.draft={before:{nodeId:'native-draft',label:'off'},after:{nodeId:'native-draft',label:'on'},applied:false};}
 const connections=[0,1].map(index=>({index,acceptedAtMillis:index*100,remoteAddress:'127.0.0.1',receivedBytes:32,closed:true,closeError:false,closedAtMillis:index*100+50,serverInitiatedClose:index?'intentional_owned_disconnect':false,...(index?{serverCloseRequestedAtMillis:index*100+25}:{})}));
 for(const[index,caseName,state]of [[0,'owned_connection_native_cancel','native_cancel_and_transport_close_observed'],[1,'owned_connection_native_disconnect','native_disconnected_page_and_return_observed']])rows.push(record(caseName,['ConnectScreen',...(index?['DisconnectedScreen']:[])],{target,state,nativeAddress:'127.0.0.1',port:12345,minecraftProtocolRepliesSent:0,loginSucceeded:false,externalServerTargetUsed:false,directCallbackUsed:false,semanticActions:[action,action,action],connection:connections[index],peerCloseObservedAfterCancel:!index,...inputs()}));
 rows.push({case:'owned_connection_cleanup',target,listenerHost:'127.0.0.1',maximumMillis:30000,observedLifetimeMillis:500,maximumConnections:2,acceptedConnections:2,connections,openSockets:0,listening:false,serverClosed:true,cleanupCompleted:true,failed:false,externalServerTargetUsed:false,minecraftProtocolRepliesSent:0,maximumOpenSockets:1});
 const recoveries={disconnected:['DisconnectedScreen','none'],datapack_failure:['DatapackLoadFailureScreen','datapack_cancelled'],file_fixer_aborted:['FileFixerAbortedScreen','file_fixer_back'],file_fixer_progress:['worldselection.FileFixerProgressScreen','native_progress_cancel_observed'],graphics_warning:['options.UnsupportedGraphicsWarningScreen','graphics_back']},applicable=target==='1.20.1-forge'?['disconnected','datapack_failure']:target==='1.21.1-neoforge'?['disconnected','datapack_failure','graphics_warning']:Object.keys(recoveries),support=Object.fromEntries(Object.keys(recoveries).map(name=>[name,applicable.includes(name)?'AVAILABLE':'NATIVE_CLASS_NOT_PRESENT']));
 for(const[caseId,[className,callbackAction]]of Object.entries(recoveries)){if(!applicable.includes(caseId)){rows.push({case:'recovery_ui_'+caseId,target,applicable:false,verified:false,outcome:'not_applicable_native_class_absent',nativeAvailability:'NATIVE_CLASS_NOT_PRESENT',caseSupport:support,nativeScreenObserved:false,nativeScreens:[],semanticActions:[],fixtureId,processId:pid});continue;}const progress=caseId==='file_fixer_progress',toTitle=target==='1.20.1-forge'&&caseId==='datapack_failure',arranged={probeOnly:true,arrangeAuthority:'DIAGNOSTIC_native_screen_fixture',target,fixtureId,processId:pid,sequence:1,case:caseId,nativeClass:native(className),returnedToFixture:false,callbackAction:'none',actualFaultInduced:false,networkConnectionCreated:false,externalServiceUsed:false,worldFilesTouched:false,repairOrConversionStarted:false,graphicsSettingsChanged:false,openedNativeTick:10,nativeProgressAvailable:progress,nativeProgressCancelled:false,nativeCancelButtonDisabledObserved:false,cancelObservedNativeTick:-1,returnedNativeTick:-1,caseSupport:support,datapackCancelDestination:target==='1.20.1-forge'?'native_title_screen':'fixture_callback'};rows.push(record('recovery_ui_'+caseId,[className],{target,applicable:true,verified:true,directCallbackUsed:false,semanticActions:[action],arranged,returned:{...arranged,returnedToFixture:!toTitle,callbackAction:toTitle?'none':callbackAction,...(progress?{nativeProgressCancelled:true,nativeCancelButtonDisabledObserved:true,cancelObservedNativeTick:11,returnedNativeTick:12}:{})},returnedScreen:native(toTitle?'TitleScreen':'UiRecoveryFixtureScreen'),...inputs()}));}
 const manifestSha256='a'.repeat(64),limits={files:1024,entries:4096,bytes:256*1024*1024,fileBytes:64*1024*1024,milliseconds:120000};
 rows.push({case:'world_recovery_copy',target,worldFilesTouched:'auxonly',sourceWorldWritten:false,immutableBaselineTouched:false,dailyInstancesTouched:false,copyManifestSha256:manifestSha256,copies:['recover','optimize'],excludedFiles:['session.lock'],sourceHashesVerified:true,bothCopyHashesVerified:true,fileCount:2,totalBytes:100,limits,sourceLevelId:'UI-Conformance'});
 for(const caseId of ['recover','optimize']){if(caseId==='recover'&&target==='1.20.1-forge'){rows.push({case:'world_recovery_recover',target,copyManifestSha256:manifestSha256,applicable:false,recoverVerified:false,nativeAvailability:'NATIVE_CLASS_ABSENT',nativeScreenObserved:false,nativeScreens:[],semanticActions:[],worldFilesTouched:'none',processId:pid});continue;}const arranged={probeOnly:true,arrangeAuthority:'DIAGNOSTIC_owned_aux_world',target,fixtureId,processId:pid,sourceLevelId:'UI-Conformance',sourceWorldName:'UI-Conformance',integratedServerStopped:true,playerAbsent:true,connectionAbsent:true,sourceSaveCompleted:true,sourceWorldWritten:false,immutableBaselineTouched:false,dailyInstancesTouched:false,networkConnectionCreated:false,actualCorruptionInduced:false,recoveryNativeAvailability:target==='1.20.1-forge'?'NATIVE_CLASS_ABSENT':'PRESENT',sequence:1,case:caseId,nativeClass:native(caseId==='recover'?'RecoverWorldDataScreen':'worldselection.OptimizeWorldScreen'),auxRootRelative:'world-recovery-aux',copyManifestSha256:manifestSha256,auxLevelId:caseId,worldFilesTouched:'auxonly',returnedToFixture:false,storageAccessOpened:true,nativeBusinessRepairPerformed:false,realWorldUpgraderCreated:caseId==='optimize',callbackAction:'none',...(caseId==='recover'?{healthyPrimary:true,healthyFallback:true,restoreButtonActive:false}:{})};const returned={...arranged,returnedToFixture:true,storageAccessClosed:true,directoryLockReacquiredAndClosed:true,callbackAction:caseId==='recover'?'recover_back':'optimize_cancelled',...(caseId==='optimize'?{workerTerminated:true,nativeRemovedCompleted:true,nativeCancelReturned:true,workerJoined:true,savedDataStorageCloseApplicable:modern,savedDataStorageClosed:modern,savedDataSaveReturned:true,savedDataCleanupSemantics:'native_work_save_return_and_worker_join_no_native_close_api',nativeFinishedBeforeCancel:false}:{})};rows.push(record('world_recovery_'+caseId,[caseId==='recover'?'RecoverWorldDataScreen':'worldselection.OptimizeWorldScreen'],{target,copyManifestSha256:manifestSha256,arranged,returned,sourceWorldWritten:false,immutableBaselineTouched:false,dailyInstancesTouched:false,directCallbackUsed:false,after:goalInput(2),semanticActions:[action],...(caseId==='recover'?{outcome:'healthy_restore_disabled_and_native_back',restoreDenied:{code:'UI_NODE_NOT_ACTIONABLE',mandatoryCleanupDispatches:1,before:goalInput(),after:goalInput(1)}}:{nativeScreenObserved:true,cancelInputSubmitted:true,optimizeCancelVerified:true,outcome:modern?'native_cancel_worker_joined_storage_closed':'native_cancel_worker_joined_saved_data_flushed_storage_lock_closed'})}));}
 rows.push({case:'world_recovery_cleanup',target,auxRootRelative:'world-recovery-aux',ownerId:'00000000-0000-0000-0000-000000000001',worldFilesTouched:'auxonly',sourceWorldWritten:false,sourceWorldWrittenByTool:false,immutableBaselineTouched:false,dailyInstancesTouched:false,failed:false,sourceHashesStillMatch:true,nativeResourcesReleased:true,cleanupCompleted:true,persistentWriteCalls:0,recoveryVerified:target!=='1.20.1-forge',optimizeCancelVerified:true});
 if(target==='1.20.1-forge')rows.push({case:'native_reconfiguration_unavailable',target,nativeClass:'ServerReconfigScreen',disposition:'NATIVE_CLASS_ABSENT',operationCoverage:false});
 else{const proof={probeOnly:true,arrangeAuthority:'DIAGNOSTIC_native_reconfiguration',target,fixtureId,processId:pid,sourceWorldName:'UI-Conformance',immutableBaselineTouched:false,dailyInstancesTouched:false,externalServerUsed:false,serverConfigStartInvokedByProbe:false,configurationTaskInjected:false,customProtocolPacketsSent:false,configurationSuccessClaimed:false,worldFilesTouched:'owned_validation_working_world',sequence:1,switchRequested:true,switchReturned:true,serverOwnerThread:true,singlePlayerOnly:true,unpublished:true,configurationAcknowledged:true,nativeClass:native('multiplayer.ServerReconfigScreen'),nativeScreenObserved:true,connectionIdentityBound:true,disconnectInitiallyDisabled:true,firstNativeDelayTicker:0};const ready={...proof,disconnectReadyObserved:true,readyNativeDelayTicker:600},closed={...ready,nativeDisconnectHandlerReturned:true,nativeClientConnectionClosed:true,nativeServerConnectionClosed:true,integratedServerStopped:true,sourceDirectoryLockReleased:true,playerAbsent:true,connectionAbsent:true},shared={target,...semantic,directCallbackUsed:false,configurationSuccessClaimed:false,semanticActions:[{requestReplayed:false,requestOutcome:'response_received',outcome:{}}]};rows.push({case:'reconfiguration_native_entry',...shared,proof},{case:'reconfiguration_native_disconnect',...shared,nativeReady:ready,proof:closed,nativeDelayTicksRequired:600,nativeDelayWasModified:false,connectionClosureIndependentlyObserved:true,sourceDirectoryLockReleased:true},{case:'reconfiguration_cleanup',target,startedNative:true,nativeClosed:true,cleanupCompleted:true,failed:false,persistentWriteCalls:0});}
 const overlayDismissal={targeting:'computed_native_overlay_background_coordinates',semanticNodeClick:false,source:{dataCategory:'USER',screenClass:native('inventory.InventoryScreen'),screenIdentity:123,overlayNodeId:'native-recipe-popup',popup:{nodeId:'native-recipe-popup',controlKey:'recipe.overlay',x:0,y:0,width:30,height:20},options:[{nodeId:'native-recipe-option-1',controlKey:'recipe.option.1',x:2,y:2,width:12,height:12},{nodeId:'native-recipe-option-2',controlKey:'recipe.option.2',x:16,y:2,width:12,height:12}]},point:{x:.5,y:.5},operationId:'controlled-native-background-click',stepType:'mouse.click',state:'completed',overlayAbsent:true,after:{screenClass:native('inventory.InventoryScreen'),screenIdentity:123,pressedKeyCount:0,pressedButtonCount:0,inputCleanupPending:false,inputCleanupFailed:false}};
 const recipe={target,...semantic,actAuthority:'PLAYTEST',arrangeAuthority:'DIAGNOSTIC_player_commands_on_disposable_copy',recipeUnlockCommandsSent:true,searchAndFilterRestored:true,recipeVisibilityRestored:true,originalGameModeRestored:true,originalRecipeUnlockSetRestored:false,recipeStateResetBy:'outer_immutable_fixture_restore',actions:[action],input:goalInput()};rows.push({...recipe,case:'recipe_controls',originalItems:['minecraft:oak_planks'],category:{nodeId:'native-recipe-category',label:modern?'minecraft:crafting_building_blocks':'CRAFTING_BUILDING_BLOCKS',...(modern?{resourceId:'minecraft:crafting_building_blocks'}:{}),selected:true,items:['minecraft:oak_door']},restoredCategory:{nodeId:'native-recipe-search',label:modern?'CRAFTING':'CRAFTING_SEARCH',selected:true},selectionSource:'native_book_owner_selectedTab_identity',filterBefore:'all',filterAfter:'craftable',searchValue:'planks',searchItems:['minecraft:oak_planks']},{...recipe,case:'recipe_overlay_fill',nativeControlFreezeUsed:true,overlayDismissal,overlayOptionCount:2,realMenuContentsConfirmed:true,ghostRecipeIsNotSufficient:true,result:{slot:0,count:4,item:'minecraft:oak_planks'},ingredients:[{slot:1,count:1,item:'minecraft:oak_log'}]});
 const empty=()=>Array.from({length:9},(_,slot)=>({slot,item:slot===0?'minecraft:paper':'minecraft:air',count:slot===0?1:0})),saved=Array.from({length:9},(_,slot)=>({slot,item:['minecraft:stone','minecraft:oak_planks','minecraft:glass'][slot]??'minecraft:air',count:[2,3,4][slot]??0}));rows.push({case:'creative_saved_hotbars',target,...semantic,before:empty(),restored:empty(),saved,loaded:structuredClone(saved),firstRowOriginallyEmpty:true,firstRowRestoredEmpty:true,nativeLoadServerContentsConfirmed:true,fixedBindingsConfirmedByNativeText:true,originalCreativeTabRestored:true,originalGameModeRestored:true,otherSavedRowsTouched:false,exactHotbarsFileBytesRestored:false,actions:[67,88,67].map(activator=>({action:'native_hotbar_chord',activator,key:49,holdMs:150,state:'completed'})),input:goalInput()});
 const initialBedHotbar=Array.from({length:9},(_,index)=>({controlKey:'hud.hotbar.'+index,item:index===0?'minecraft:red_bed':index===2?'minecraft:diamond':'minecraft:air',count:index===0?1:index===2?3:0,selected:index===1})),bedCommand={type:'command.player.execute',target,accepted:true,dataCategory:'USER',effectConfirmed:false,serverValidationObserved:false},bedReadback=()=>({dataCategory:'USER',authority:'CLIENT_KNOWN',chunkLoadRequested:false,entitiesTruncated:false,remainingBedDrops:[]});
 rows.push(record('special_world_in_bed_chat',['InBedChatScreen'],{target,...inputs(),chatSubmitted:false,directCallbackUsed:false,automaticWakePreventedByRule:101,originalRule:{value:100},restoredRule:{value:100},removedPriorDrops:{...bedCommand},priorDropReadback:bedReadback(),preparationDropReadback:bedReadback(),entered:{state:'completed'},placed:{state:'completed'},bedCleanup:{item:'minecraft:red_bed',pickupLimit:1,initialBedCount:1,addedBeds:1,initialHotbar:initialBedHotbar,beforeHotbar:initialBedHotbar.map((node,index)=>({...node,...(index===3?{item:'minecraft:red_bed',count:1}:{})})),afterHotbar:structuredClone(initialBedHotbar),removedDrop:{...bedCommand},dropReadback:bedReadback(),removedPickup:{...bedCommand},observedTick:100,afterTick:101,stableTick:102,source:'USER_native_hotbar_readback',unrelatedItemsCleared:false}}));
 if(!modern)rows.push({case:'special_world_nautilus_unavailable',target,disposition:'NATIVE_CLASS_ABSENT',operationCoverage:false});else rows.push(record('special_world_nautilus_inventory',['NautilusInventoryScreen'],{target,...inputs(),saddleEquippedAndReturned:true,ownerUsedOnlyForDiagnosticArrange:true,nativeTamingRandomnessTested:false,source:{slotOwner:'player_inventory'},destination:{slotOwner:'menu'},entered:{state:'completed'}}));
 rows.push({case:'special_world_social_environment_limit',target,...inputs(),pauseScreenClass:native('PauseScreen'),externalServiceAcceptance:false,fakeAccountUsed:false,...(target.startsWith('1.')||target==='26.1.2-neoforge'?{disposition:'NATIVE_SINGLEPLAYER_ENTRY_ABSENT',entry:null,absentEntryProof:{dataCategory:'USER',screenClass:native('PauseScreen'),screenIdentity:123,screenRevision:4,socialEntries:0,alternateEntry:{nodeId:'native-share-to-lan',translationKey:'menu.shareToLan',label:'Open to LAN',visible:true,active:true,actions:['click']}}}:{disposition:'NATIVE_SINGLEPLAYER_ENTRY_DISABLED',entry:{active:false}}),screenOperationCoverage:false,screenConstructedByProbe:false,refusalPresentation:{children:[{controlKey:'hud.actionbar',visible:true,label:'Social Interactions are only available in Multiplayer worlds'}]}});
 rows.push(...eUiManagementSamples(target));
 for(const row of rows.filter(row=>row.case.startsWith('navigation_')&&row.case!=='navigation_native_unavailable'&&row.case!=='navigation_dialog_callback_guards')){row.screenConstructionIsNotFullOperationCoverage=true;row.screens=row.nativeScreens.map((screenClass,index)=>({screenClass,screenIdentity:index+1,screenRevision:1,interactionNodeCount:1}));}
 return result;
}

function eUiBlockEntities(){
 const target='26.3-neoforge',r=goalHeader(target,'block-entity-selector',4),a={x:4,y:82,z:4},b={...a,x:5},far={...b,x:b.x+1048576,z:b.z+1048576},entry=(p,type)=>({key:`${p.x},${p.y},${p.z}`,type,loaded:true,readEffects:'none'}),value=entries=>({dataCategory:'DEBUG',server:{chunkLoadRequested:false,storageAccessed:false,blockEntities:entries}}),body=(perspective,position=b)=>({perspective,selector:{blocks:[position]}});
 r.actionSamples=[{case:'block_entity_selector_fixture',a,b,copyOnlySideEffects:true,beforeArrange:goalInput(),inputBeforeReads:goalInput()},{case:'block_entity_selector_exact',body:body('server_authoritative'),value:value([entry(b,'minecraft:barrel')]),broad:value([entry(a,'minecraft:chest'),entry(b,'minecraft:barrel')]),expected:b,excluded:a},{case:'block_entity_selector_both',body:body('both'),value:{...value([entry(b,'minecraft:barrel')]),client:{}}},{case:'block_entity_selector_client_rejected',body:body('client_known'),httpStatus:400,denied:{error:'UNSUPPORTED_DEEP_SELECTOR'}},{case:'block_entity_selector_unloaded',far,body:body('server_authoritative',far),value:value([]),before:{server:{chunkLoadRequested:false,blocks:[{available:false,loadRequested:false}]}},after:{server:{chunkLoadRequested:false,blocks:[{available:false,loadRequested:false}]}},chunkLoadRequested:false},{case:'block_entity_selector_cleanup',cleanup:[a,b].map(position=>({position,receipt:{route:'NATIVE',inputDispatched:false},restored:{block:'minecraft:air'}}))},{case:'block_entity_selector_complete',target,checks:4,evidenceKind:'runtime_functional',liveEvidence:true,scope:'CF-021_coordinate_selection_not_CF-022_pagination',inputBeforeReads:goalInput(),inputAfterReads:goalInput(),finalInput:goalInput(),finalMode:goalReadMode(),chunkLoadRequested:false,persistentWriteCalls:0}];return r;
}

function eUiCamera(){
 const target='26.3-neoforge',r=goalHeader(target,'first-camera',3),player=goalPlayer();
 r.actionSamples=[{case:'first_background_world_camera',target,inputReplayed:false,hostFocusChangedByTest:false,pauseResumeBeforeCamera:false,directCameraMutation:false,performanceMeasured:false,titleInput:{hostFocused:false},titleControl:{mode:'TAKEOVER'},beforeInput:{...goalInput(),hostFocused:false},beforePlayer:player,steps:[{type:'key.tap',key:87,holdMs:700},{type:'mouse.delta',dx:36,dy:-12}],operation:{state:'completed'},observations:Array.from({length:5},()=>({...player,x:player.x+1,yaw:player.yaw+1})),afterInput:{...goalInput(10),hostCursorCaptured:false},cameraChanged:true,playerMoved:true}];return r;
}

function eUiCommand(){
 const target='26.3-neoforge',r=goalHeader(target,'command-collaboration',6),receipt={route:'NATIVE',inputDispatched:false,requiresControlLease:false,dataCategory:'USER',serverValidationObserved:false},before={nativePlayerCommands:10,permissionRevoked:false,gamemasterAllowed:true,nativePermissionTreeSends:0},pair=(n=2)=>({before:structuredClone(before),after:{nativePlayerCommands:10+n},inputBefore:goalInput(),inputAfter:goalInput()}),tree={roots:['time'],parsedExecutable:true};
 r.actionSamples=[{case:'command_collaboration_initial_authority',...pair(),receipt,tree},{case:'command_collaboration_permission_revoked',...pair(1),before:{...before,permissionRevoked:true,gamemasterAllowed:false,nativePermissionTreeSends:1},denied:{error:'PLAYER_COMMAND_NOT_AVAILABLE'},tree:{roots:[],parsedExecutable:false},clientCommandTreeForged:false},{case:'command_collaboration_revoked_batch',...pair(),batch:{submittedCount:1,stoppedAtCommandIndex:1,commands:[{status:'submitted_unconfirmed'},{error:{code:'PLAYER_COMMAND_NOT_AVAILABLE'}},{status:'not_attempted'}],rollbackAttempted:false,retrySafe:false}},{case:'command_collaboration_permission_restored',...pair(),before:{...before,nativePermissionTreeSends:2},receipt,tree},{case:'command_collaboration_mcp',...pair(),receipt,transport:'real_mcp_stdio',initializeServerInfo:{name:'minecraft-protocol-companion',version:'fixture-version'},expectedPackageVersion:'fixture-version',uiContextPreserved:true},{case:'command_collaboration_complete',target,serverPermissionFixtureTested:true,operatorListChanged:false,worldMetadataChanged:false,humanCooperationTested:false,humanPermissionChangeTested:false,physicalEscTested:false,runtimeScopeHotRevocationTested:false,clientShutdownOwnedByOuterRunner:true,persistentWriteCalls:0,finalInput:goalInput(),finalMode:goalReadMode()}];return r;
}

test('E UI requires 32 current development slots including five real Demo launches plus five entered-world installations',async()=>{
 const targets=['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric'];
 assert.equal(E_UI_PROFILE,'e_ui_trial_v1');const slots=eUiProofSlots(targets);assert.equal(slots.length,32);assert.equal(new Set(slots.map(row=>row.target+':'+row.scenario)).size,32);assert.equal(slots.length+targets.length,37);assert.deepEqual(slots.filter(row=>row.scenario==='hud-demo').map(row=>row.target).sort(),[...targets].sort());assert.deepEqual(slots.filter(row=>row.scenario==='first-camera').map(row=>row.target).sort(),['1.21.1-neoforge','26.3-neoforge']);assert.throws(()=>eUiProofSlots(targets.slice(1)));assert.throws(()=>eUiProofSlots([...targets.slice(1),targets[1]]));
 const f=await dependencyFixture(),historical=historicalTrialBaseline(f.baseline);assert.equal(historical.historicalOnly,true);assert.equal(historical.historicalLogicalSlots,65);assert.equal(historical.currentFullMatrixClaimed,false);assert.equal(historical.manifestSha256,f.identity.manifestSha256);
 assert.deepEqual(E_UI_REQUIRED_CASE_CONTRACTS,['M-03_final_UI_case_contract','M-02_final_navigation_case_contract','M-04_final_HUD_Toasts_case_contract']);await assert.rejects(collectEUiProof(f.repo,targets,{}),{code:'ENOENT'});
});

test('E UI proof requires actual slot aliases, read-only readiness, typed suggestion recovery and completed native item/context draws',()=>{
 const r=eUiFixture();assert.equal(eUiRegressionProof(r,r.target).suggestionConsumers,2);assert.equal(eUiRegressionProof(r,r.target).fullUiMilestoneClaimed,false);
 for(const field of ['uiCorrectnessSamples','suggestionSamples','hudCompletionSamples']){const changed=structuredClone(r);changed[field]=[];assert.throws(()=>eUiRegressionProof(changed,changed.target));}
 for(const mutate of [r=>r.uiCorrectnessSamples[0].shiftClickTransferred=false,r=>r.uiCorrectnessSamples[0].handwrittenCoordinates=true,r=>r.uiCorrectnessSamples[1].realMcpStdio=false,r=>r.uiCorrectnessSamples[1].inputDelta=1,r=>r.suggestionSamples[0].restored=false,r=>r.suggestionSamples[0].staleRejected=false,r=>r.suggestionSamples[0].value=r.suggestionSamples[0].original,r=>r.suggestionSamples[1].actualTransports=['mcp_stdio_debug'],r=>r.suggestionSamples[2].rejected='STALE_RESOURCE_REVISION',r=>r.hudCompletionSamples[0].bar.damage=100,r=>r.hudCompletionSamples[1].bar.valueSemantics='remaining_ticks',r=>r.hudCompletionSamples.find(s=>s.case==='hud_context_draw').nodes=[]]){const changed=structuredClone(r);mutate(changed);assert.throws(()=>eUiRegressionProof(changed,changed.target));}
 const duplicate=structuredClone(r);duplicate.suggestionSamples.push(duplicate.suggestionSamples[0]);assert.throws(()=>eUiRegressionProof(duplicate,duplicate.target));
});

test('E UI contextual HUD accepts actual Forge icons atlas coordinates, never a fabricated modern sprite',()=>{
 const fixture=()=>{const result=eUiFixture('1.20.1-forge');for(const row of result.hudCompletionSamples.filter(row=>row.case==='hud_context_draw'))for(const node of row.nodes){if(node.controlKey.endsWith('.level'))continue;const background=node.controlKey.endsWith('.background');delete node.resourceId;Object.assign(node,{textureId:'minecraft:textures/gui/icons.png',textureU:0,textureV:(row.family==='experience'?64:84)+(background?0:5),atlasWidth:256,atlasHeight:256,spriteSemantics:'native_gui_icons_atlas_region',spriteRegion:'hud/'+row.family+'_bar_'+(background?'background':'progress')});}return result;};
 const result=fixture();assert.deepEqual(eUiRegressionProof(result,result.target).hudFamilies,['experience','jump']);
 const background=value=>value.hudCompletionSamples.find(row=>row.case==='hud_context_draw').nodes[0];
 for(const mutate of [r=>background(r).textureId='minecraft:textures/gui/bars.png',r=>background(r).textureU=1,r=>background(r).textureV=84,r=>background(r).atlasWidth=512,r=>background(r).spriteSemantics='estimated',r=>background(r).spriteRegion='hud/jump_bar_background',r=>background(r).resourceId='minecraft:hud/experience_bar_background',r=>{delete background(r).textureId;background(r).resourceId='minecraft:hud/experience_bar_background';}]){const changed=fixture();mutate(changed);assert.throws(()=>eUiRegressionProof(changed,changed.target));}
 const modern=eUiFixture('1.21.1-neoforge');assert.deepEqual(eUiRegressionProof(modern,modern.target).hudFamilies,['experience','jump']);Object.assign(background(modern),background(result));assert.throws(()=>eUiRegressionProof(modern,modern.target));
});

test('E UI requires an independently launched real Demo result for each target, not an ordinary all receipt',()=>{
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric'])assert.equal(eUiDemoProof(eUiDemoFixture(target),target).actualDemoLaunch,true);
 assert.throws(()=>eUiDemoProof(undefined,'26.3-neoforge'));
 assert.throws(()=>eUiDemoProof({...eUiDemoFixture(),scenario:'all'},'26.3-neoforge'));
 for(const mutate of [
  r=>r.hudCompletionSamples=[],r=>r.hudCompletionSamples.push(structuredClone(r.hudCompletionSamples[0])),
  r=>r.launchAttestation.processId++,r=>r.hudCompletionSamples[0].launch.gameArguments=[],
  r=>r.hudCompletionSamples[0].launch.gameArguments.push('--demo'),
  r=>{r.hudCompletionSamples[0].launch.instanceDirectory='D:/daily/instances/26.3-neoforge/client';r.launchAttestation=structuredClone(r.hudCompletionSamples[0].launch);},
  r=>r.hudCompletionSamples[0].titleEntry.translationKey='menu.singleplayer',
  r=>r.hudCompletionSamples[0].nativeDemoNodes=[],r=>r.hudCompletionSamples[0].nativeDemoNodes[0].label='Requested Demo countdown',
  r=>r.hudCompletionSamples[0].nativeDemoNodes[0].labelSource='requested_command',
  r=>r.hudCompletionSamples[0].nativeDemoNodes[0].uuid='private',
  r=>r.hudCompletionSamples[0].entry.requestReplayed=true,
  r=>r.hudCompletionSamples[0].entry.after.inputContextGeneration=r.hudCompletionSamples[0].entry.before.inputContextGeneration,
  r=>r.worldExitSamples[0].after.pressedButtonCount=1,
  r=>r.worldExitSamples[0].outcome='click_completed',r=>r.hudCompletionSamples[0].demoModeMutationUsed=true,
  r=>r.hudCompletionSamples[0].resetClicked=true,r=>r.cleanShutdown=false,r=>r.endpointRemoved=false,
 ]){const r=eUiDemoFixture();mutate(r);assert.throws(()=>eUiDemoProof(r,r.target));}
});

test('Demo entry and exit retain exact not-confirmed errors without replay or an invented successful action',()=>{
 const r=eUiDemoFixture();for(const row of [r.hudCompletionSamples[0].entry,r.worldExitSamples[0]]){row.requestOutcome='not_confirmed';row.error={status:409,body:{error:'REQUEST_CANCELLED',executionOutcome:'not_confirmed',retrySafe:false}};}
 assert.equal(eUiDemoProof(r,r.target).normalWorldExit,true);
 for(const mutate of [r=>r.hudCompletionSamples[0].entry.error.body.retrySafe=true,r=>r.worldExitSamples[0].error.body.executionOutcome='completed',r=>r.worldExitSamples[0].error.status=500,r=>r.worldExitSamples[0].error.body.error='UNRELATED_CONFLICT']){const value=structuredClone(r);mutate(value);assert.throws(()=>eUiDemoProof(value,value.target));}
});

test('E UI Hardcore and Demo associate their returned Title tree with exactly one independent same-world exit receipt',()=>{
 const variants=[{make:eUiKnownFixture,verify:eUiKnownCompletionProof,caseName:'hud_hardcore_native_world'},{make:eUiDemoFixture,verify:eUiDemoProof,caseName:'hud_demo_native_launch'}];
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric'])for(const{make,verify,caseName}of variants){
  const sample=value=>value.hudCompletionSamples.find(row=>row.case===caseName),result=make(target);assert.equal(sample(result).exit.type,'ui.tree');assert.equal(sample(result).exit.before,undefined);verify(result,target);
  const unrelated=structuredClone(result.worldExitSamples[0]);unrelated.before.inputContextId='another-world-context';result.worldExitSamples.push(unrelated);verify(result,target);
  for(const mutate of [
   (value,row)=>value.worldExitSamples=[],(value,row)=>value.worldExitSamples.push(structuredClone(value.worldExitSamples[0])),
   (value,row)=>value.worldExitSamples[0].before.inputContextId='foreign-world',
   (value,row)=>value.worldExitSamples[0].before.target='foreign-target',(value,row)=>value.worldExitSamples[0].after.target='foreign-target',
   (value,row)=>row.entry.after.target='foreign-target',(value,row)=>value.worldExitSamples[0].before.inputContextGeneration++,
   (value,row)=>value.worldExitSamples[0].before.inputGeneration=row.entry.after.inputGeneration-1,
   (value,row)=>value.worldExitSamples[0].before.clientTick=row.entry.after.clientTick-1,
   (value,row)=>row.exit.clientTick=value.worldExitSamples[0].before.clientTick-1,
   (value,row)=>value.worldExitSamples[0].after.clientTick=row.exit.clientTick-1,
   (value,row)=>row.entry.after.clientTick=-1,(value,row)=>row.exit.type='operation',
   (value,row)=>row.exit.dataCategory='DEBUG',(value,row)=>row.exit.overlayIdentity=1,
   (value,row)=>row.exit.target='foreign-target',(value,row)=>row.exit.screenClass='net.minecraft.client.gui.screens.SelectWorldScreen',
   (value,row)=>row.exit=structuredClone(value.worldExitSamples[0]),
   (value,row)=>Object.assign(row.exit,structuredClone(value.worldExitSamples[0])),
   (value,row)=>value.worldExitSamples[0].requestReplayed=true,
   (value,row)=>value.worldExitSamples[0].released.passed=false,
   (value,row)=>value.worldExitSamples[0].after.pressedKeyCount=1,
  ]){const changed=make(target);mutate(changed,sample(changed));assert.throws(()=>verify(changed,target));}
 }
});

test('known current HUD and native finite samples are hard prerequisites on all five actual target branches',()=>{
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
  const proof=eUiKnownCompletionProof(eUiKnownFixture(target),target);assert.equal(proof.hardcore,true);assert.equal(proof.saving,target==='1.20.1-forge'?'NOT_RENDERED_BY_DEFAULT_FORGE_GUI':'native_pause_save_draw');assert.equal(proof.fullUiMilestoneClaimed,false);
  assert.equal(proof.scope,'finite_current_UI_HUD_local_and_confirmation_samples');
 }
 assert.equal(E_UI_REQUIRED_CASE_CONTRACTS.length,3);
 for(const caseName of ['hud_hardcore_native_world','hud_camera_variant_nausea','hud_boss_native_variants','hud_late_native_text','player_list_roster','hud_native_saving_indicator','hud_resident_toast_user']){
  const value=eUiKnownFixture();value.hudCompletionSamples=value.hudCompletionSamples.filter(row=>row.case!==caseName);assert.throws(()=>eUiKnownCompletionProof(value,value.target),undefined,caseName);
 }
 for(const mutate of [
  r=>r.hudCompletionSamples.find(row=>row.case==='hud_hardcore_native_world').hardcoreFlagMutationUsed=true,
  r=>r.hudCompletionSamples.find(row=>row.case==='hud_camera_variant_nausea').directRendererSetterUsed=true,
  r=>r.hudCompletionSamples.find(row=>row.case==='hud_native_saving_indicator').draw=[],
  r=>r.hudCompletionSamples.find(row=>row.case==='hud_native_subtitle').draw.directions=[],
  r=>r.hudCompletionSamples.find(row=>row.case==='player_list_roster').displayedNames.reverse(),
  r=>r.hudCompletionSamples.find(row=>row.case==='hud_resident_toast_user').exiting.label='hidden queued text',
  r=>r.localOwnerSamples.find(row=>row.case==='native_title_lifecycle_timing').inputDelta=1,
  r=>r.localOwnerSamples.find(row=>row.case==='native_spectator_debug_highlight').itemActivationUsed=true,
  r=>r.navigationCompletionSamples.find(row=>row.case==='native_confirmation_popup').arranged.processId++,
  r=>r.navigationCompletionSamples.find(row=>row.case==='native_confirmation_popup').screenClass='net.minecraft.client.gui.screens.TitleScreen',
  r=>r.navigationCompletionSamples.find(row=>row.case==='native_confirmation_backup').semanticActions=[],
 ]){const value=eUiKnownFixture();mutate(value);assert.throws(()=>eUiKnownCompletionProof(value,value.target));}
 const legacy=eUiKnownFixture('1.20.1-forge');legacy.navigationCompletionSamples.find(row=>row.case==='native_confirmation_unavailable').operationCoverage=true;assert.throws(()=>eUiKnownCompletionProof(legacy,legacy.target));
});

test('E UI requires all four legacy Toast classes on every Target with their actual full text phases and same opaque owner',()=>{
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){const result=eUiKnownFixture(target),proof=eUiKnownCompletionProof(result,target);assert.deepEqual(proof.legacyToasts.nativeKinds,['System','Tutorial','Advancement','Recipe']);assert.equal(proof.legacyToasts.queueBlockedNativeTicks,3);assert.equal(proof.legacyToasts.gameplayTutorialCompletionClaimed,false);for(const kind of ['System','Tutorial','Advancement','Recipe']){const missing=eUiKnownFixture(target);missing.hudCompletionSamples=missing.hudCompletionSamples.filter(row=>row.case!=='native_toast_lifecycle'||row.kind!==kind);assert.throws(()=>eUiKnownCompletionProof(missing,target));}}
 for(const textPhase of ['header','title','header_and_title']){const result=eUiKnownFixture();result.hudCompletionSamples.find(row=>row.case==='native_toast_lifecycle'&&row.kind==='Advancement').full.textPhase=textPhase;eUiKnownCompletionProof(result,result.target);}
 const sample=result=>result.hudCompletionSamples.find(row=>row.case==='native_toast_lifecycle'&&row.kind==='Recipe');
 for(const mutate of [row=>row.full.textPhase='drawn',row=>row.full.textPhase='header',row=>row.full.label='',row=>row.entering.label='queued hidden text',row=>row.exiting.textPhase='static',row=>row.full.contextKey='toast:00000000-0000-0000-0000-000000000099',row=>row.contextKey='toast:0',row=>row.full.class='net.minecraft.client.gui.components.toasts.SystemToast',row=>row.full.resourceVersion={},row=>row.fixtureReset=false,row=>row.gameplayTutorialCompletionClaimed=true,row=>row.naturalGameplayCompletionClaimed=true,row=>row.arrangeAuthority='PLAYTEST',row=>row.assertAuthority='DEBUG',row=>row.worldFilesWritten=true,row=>row.absent.remaining=1,row=>row.input.pressedKeyCount=1]){const result=eUiKnownFixture();mutate(sample(result));assert.throws(()=>eUiKnownCompletionProof(result,result.target));}
});

test('E UI Toast queue proof requires five real blockers and at least three distinct forward Native ticks before release',()=>{
 const queue=result=>result.hudCompletionSamples.find(row=>row.case==='native_toast_queue_boundary');
 for(const mutate of [row=>row.blockers.pop(),row=>row.blockers[1].contextKey=row.blockers[0].contextKey,row=>row.blockers[1].label=row.blockers[0].label,row=>row.blockers[0].renderPhase='entering',row=>row.observations.pop(),row=>row.observations[1].clientTick=row.observations[0].clientTick,row=>row.observations[2].clientTick=0,row=>row.observations[0].recipeNodes=1,row=>row.observations[0].visibleBlockers=4,row=>row.observations[0].forbiddenTextPresent=true,row=>row.released=false,row=>row.queuedTextExposed=true,row=>row.fixtureReset=false,row=>row.queuedRecipe.full.contextKey=row.blockers[0].contextKey,row=>row.queuedRecipe.full.textPhase='drawn',row=>row.queuedRecipe.exiting.label='not actually withheld',row=>row.queuedRecipe.absent.clientTick=100,row=>row.input.inputCleanupFailed=true]){const result=eUiKnownFixture();mutate(queue(result));assert.throws(()=>eUiKnownCompletionProof(result,result.target));}
});

test('E UI management proof requires current-process FIFO refusal with unchanged Native packet counters and stored hashes',()=>{
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){const result=eUiFiniteFixture(target),proof=eUiFiniteOperationProof(result,target).commandManagement;assert.equal(proof.commandMinecart,true);assert.equal(proof.testManagementApplicable,!target.startsWith('1.'));assert.equal(proof.managementExecutionValidated,false);const missing=eUiFiniteFixture(target);missing.navigationCompletionSamples=missing.navigationCompletionSamples.filter(row=>row.case!=='ui_command_minecart_configuration_denied');assert.throws(()=>eUiFiniteOperationProof(missing,target));}
 const cart=result=>result.navigationCompletionSamples.find(row=>row.case==='ui_command_minecart_configuration_denied');
 for(const mutate of [row=>row.before.processId++,row=>row.after.target='other-target',row=>row.before.probeOnly=false,row=>row.before.validationName='other-validation',row=>row.before.worldName='daily_world',row=>row.before.singlePrivatePlayerBound=false,row=>row.after.attemptId=row.before.attemptId,row=>row.after.nativePlayerCommands++,row=>delete row.before.commandMinecartPackets,row=>row.after.commandMinecartPackets++,row=>row.after.structureSavePackets++,row=>row.after.uiFixtureMinecartCommandHash='a'.repeat(64),row=>row.before.uiFixtureMinecartCount=0,row=>row.result.state='completed',row=>row.result.errorCode='UI_TARGET_INVALIDATED',row=>row.result.result.commandSubmissions.push({accepted:true}),row=>row.inputAfter.pressedButtonCount=1,row=>row.managementExecutionValidated=true,row=>row.readbackAuthority='USER',row=>row.semanticActions.pop()]){const result=eUiFiniteFixture();mutate(cart(result));assert.throws(()=>eUiFiniteOperationProof(result,result.target));}
 for(const mutate of [rows=>rows.find(row=>row.case==='ui_test_block_configuration_denied').after.testBlockPackets++,rows=>rows.find(row=>row.case==='ui_test_block_configuration_denied').after.storedTestMessageHash='b'.repeat(64),rows=>rows.find(row=>row.case==='ui_test_instance_management_denied').afterCancel.testInstanceActionPackets++,rows=>rows.find(row=>row.case==='ui_test_instance_management_denied').after.storedTestInstanceHash='c'.repeat(64),rows=>rows.find(row=>row.case==='ui_test_instance_management_denied').localDraftRollbackClaimed=true,rows=>rows.find(row=>row.case==='ui_test_instance_management_denied').query.state='queued']){const result=eUiFiniteFixture();mutate(result.navigationCompletionSamples);assert.throws(()=>eUiFiniteOperationProof(result,result.target));}
 for(const target of ['1.20.1-forge','1.21.1-neoforge']){const result=eUiFiniteFixture(target);result.navigationCompletionSamples.find(row=>row.case==='ui_test_management_native_absent').operationCoverage=true;assert.throws(()=>eUiFiniteOperationProof(result,target));}
 const modern=eUiFiniteFixture();modern.navigationCompletionSamples.push({case:'ui_test_management_native_absent',target:modern.target,nativeScreens:[],screens:['TestBlockEditScreen','TestInstanceBlockEditScreen'],disposition:'NATIVE_CLASS_ABSENT',operationCoverage:false});assert.throws(()=>eUiFiniteOperationProof(modern,modern.target));
});

test('E UI Boss preserves Native background background progress progress order and rejects the old interleaved expectation',()=>{
 const boss=result=>result.hudCompletionSamples.find(row=>row.case==='hud_boss_native_variants');
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
  const result=eUiKnownFixture(target);eUiKnownCompletionProof(result,target);assert.deepEqual(boss(result).red.sprites.map(row=>row.width),[182,182,91,91]);assert.deepEqual(boss(result).blue.sprites.map(row=>row.width),[182,91]);
  if(target==='1.20.1-forge')assert.deepEqual(boss(result).red.sprites.map(row=>row.textureV),[20,90,25,95]);else assert.deepEqual(boss(result).red.sprites.map(row=>row.resourceId),['red_background','notched_10_background','red_progress','notched_10_progress'].map(name=>'minecraft:boss_bar/'+name));
  for(const mutate of [value=>value.red.sprites=[value.red.sprites[0],value.red.sprites[2],value.red.sprites[1],value.red.sprites[3]],value=>value.red.sprites[1].width=91,value=>value.red.sprites[2].width=182,value=>value.red.sprites.pop(),value=>value.red.sprites.push(structuredClone(value.red.sprites[0])),value=>value.blue.sprites.reverse(),value=>{if(target==='1.20.1-forge')value.red.sprites[1].textureV=25;else value.red.sprites[1].resourceId='minecraft:boss_bar/red_progress';}]){const changed=eUiKnownFixture(target);mutate(boss(changed));assert.throws(()=>eUiKnownCompletionProof(changed,target));}
 }
});

test('E UI Popup exact Native class follows each Target actual probe binding and rejects the other version path',async()=>{
 const repo=resolve(import.meta.dirname,'../../../..');
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
  const forge=target==='1.20.1-forge',expected='net.minecraft.client.gui.'+(forge?'screens':'components')+'.PopupScreen';
  const path=forge?'mixin/UiNativePopupProbeAccessor.java':'UiNativeConfirmationFixtureScreen.java',source=await readFile(resolve(repo,`versions/${target}/src/probe/java/io/github/recrivenvi/minecraftprotocol/probe/validation/`+path),'utf8');assert(source.includes('import '+expected+';'));
  const result=eUiFiniteFixture(target),row=result.navigationCompletionSamples.find(row=>row.case==='native_confirmation_popup');assert.equal(row.screenClass,expected);eUiKnownCompletionProof(result,target);eUiFiniteOperationProof(result,target);
  const wrong='net.minecraft.client.gui.'+(forge?'components':'screens')+'.PopupScreen';row.screenClass=wrong;row.nativeScreens=[wrong];row.arranged.nativeClass=wrong;row.returned.nativeClass=wrong;assert.throws(()=>eUiKnownCompletionProof(result,target));
 }
});

test('E UI finite operation contracts consume every applicable native group and retain explicit Target limitations',()=>{
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
  const result=eUiFiniteFixture(target),proof=eUiFiniteOperationProof(result,target);eUiKnownCompletionProof(result,target);
  assert.deepEqual(proof.contracts,E_UI_REQUIRED_CASE_CONTRACTS);assert.equal(proof.ownedTcpConnections,2);assert.equal(proof.nativeDialogApplicable,!target.startsWith('1.'));assert.equal(proof.reconfigurationApplicable,target!=='1.20.1-forge');assert.equal(proof.worldRecoveryApplicable,target!=='1.20.1-forge');assert.equal(proof.socialScreenOperationCoverage,false);assert.equal(proof.fullUiMilestoneClaimed,false);
 }
 for(const caseName of ['navigation_settings_completion','navigation_native_game_mode_completion','navigation_native_dialog_style_hit','navigation_dialog_callback_guards','navigation_dialog_variants','native_confirmation_popup','owned_connection_native_cancel','owned_connection_native_disconnect','owned_connection_cleanup','recovery_ui_file_fixer_progress','world_recovery_copy','world_recovery_recover','world_recovery_optimize','world_recovery_cleanup','reconfiguration_native_entry','reconfiguration_native_disconnect','reconfiguration_cleanup','recipe_controls','recipe_overlay_fill','creative_saved_hotbars','special_world_in_bed_chat','special_world_nautilus_inventory','special_world_social_environment_limit']){
  const result=eUiFiniteFixture();result.navigationCompletionSamples=result.navigationCompletionSamples.filter(row=>row.case!==caseName);assert.throws(()=>eUiFiniteOperationProof(result,result.target),undefined,caseName);
 }
});

test('E UI accepts zero bytes only for the intentional second owned TCP disconnect with monotonic actual close evidence',()=>{
 const row=(result,index)=>result.navigationCompletionSamples.find(value=>value.case===(index?'owned_connection_native_disconnect':'owned_connection_native_cancel'));
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
  const zero=eUiFiniteFixture(target);row(zero,1).connection.receivedBytes=0;eUiFiniteOperationProof(zero,target);
  const limit=eUiFiniteFixture(target);row(limit,0).connection.receivedBytes=65536;row(limit,1).connection.receivedBytes=65536;eUiFiniteOperationProof(limit,target);
  for(const mutate of [
   result=>row(result,0).connection.receivedBytes=0,
   result=>row(result,0).connection.receivedBytes=-1,
   result=>row(result,0).connection.receivedBytes=.5,
   result=>row(result,0).connection.receivedBytes=65537,
   result=>row(result,1).connection.receivedBytes=-1,
   result=>row(result,1).connection.receivedBytes=.5,
   result=>row(result,1).connection.receivedBytes=65537,
   result=>delete row(result,1).connection.serverCloseRequestedAtMillis,
   result=>row(result,1).connection.serverCloseRequestedAtMillis=NaN,
   result=>row(result,1).connection.serverCloseRequestedAtMillis=Infinity,
   result=>row(result,1).connection.serverCloseRequestedAtMillis=row(result,1).connection.acceptedAtMillis-1,
   result=>row(result,1).connection.serverCloseRequestedAtMillis=row(result,1).connection.closedAtMillis+1,
   result=>delete row(result,1).connection.acceptedAtMillis,
   result=>delete row(result,1).connection.closedAtMillis,
   result=>row(result,1).connection.closedAtMillis=row(result,1).connection.acceptedAtMillis-1,
   result=>row(result,1).connection.closedAtMillis=501,
   result=>row(result,1).connection.closed=false,
   result=>row(result,1).connection.closeError=true,
   result=>row(result,1).connection.serverInitiatedClose='final_cleanup',
   result=>row(result,1).connection.serverInitiatedClose='deadline',
   result=>row(result,1).connection.serverInitiatedClose=false,
   result=>row(result,1).nativeScreens=['net.minecraft.client.gui.screens.ConnectScreen'],
   result=>row(result,1).nativeScreens=['net.minecraft.client.gui.screens.DisconnectedScreen'],
   result=>row(result,1).semanticActions=[],
   result=>row(result,1).loginSucceeded=true,
   result=>row(result,1).minecraftProtocolRepliesSent=1,
   result=>{const cleanup=result.navigationCompletionSamples.find(value=>value.case==='owned_connection_cleanup');cleanup.connections[1]={...cleanup.connections[1],receivedBytes:1};}
  ]){const changed=eUiFiniteFixture(target);row(changed,1).connection.receivedBytes=0;mutate(changed);assert.throws(()=>eUiFiniteOperationProof(changed,target));}
 }
});

test('E UI game-mode cancellation requires USER mode stability across native ticks and an explicit held-clean safety release',()=>{
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
  const row=result=>result.navigationCompletionSamples.find(value=>value.case==='navigation_native_game_mode_completion');
  eUiFiniteOperationProof(eUiFiniteFixture(target),target);
  for(const mutate of [
   value=>delete value.cancelledSelection,
   value=>value.cancelledSelection.choice.actions=['click'],
   value=>value.cancelledSelection.choice.effect='direct_mode_setter',
   value=>value.cancelledSelection.before.gameMode='survival',
   value=>value.cancelledSelection.uncommitted.gameMode='survival',
   value=>value.cancelledSelection.later.gameMode='survival',
   value=>value.cancelledSelection.before.clientTick=-1,
   value=>value.cancelledSelection.uncommitted.clientTick=value.cancelledSelection.before.clientTick-1,
   value=>value.cancelledSelection.later.clientTick=value.cancelledSelection.uncommitted.clientTick+3,
   value=>value.cancelledSelection.later.clientTick=NaN,
   value=>value.cancelledSelection.later.dataCategory='DEBUG',
   value=>value.cancelledSelection.stopped.type='key.release',
   value=>value.cancelledSelection.cancelledBy='native_modifier_release',
   value=>value.cancelledSelection.privateSubmitCalled=true,
   value=>value.cancelledSelection.after.pressedKeyCount=1,
   value=>value.cancelledSelection.after.pressedButtonCount=1,
   value=>value.cancelledSelection.after.inputCleanupPending=true,
   value=>value.cancelledSelection.after.inputCleanupFailed=true,
   value=>value.nativeScreens.pop()
  ]){const result=eUiFiniteFixture(target);mutate(row(result));assert.throws(()=>eUiFiniteOperationProof(result,target));}
 }
});

test('E UI social limits distinguish the reviewed pre-26.3 absent LAN branch from a disabled Native entry and require actual visible refusal',()=>{
 const social=result=>result.navigationCompletionSamples.find(row=>row.case==='special_world_social_environment_limit');
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
  const result=eUiFiniteFixture(target);eUiFiniteOperationProof(result,target);assert.equal(social(result).disposition,target.startsWith('26.3-')?'NATIVE_SINGLEPLAYER_ENTRY_DISABLED':'NATIVE_SINGLEPLAYER_ENTRY_ABSENT');
  const actualPage=eUiFiniteFixture(target),page=social(actualPage);delete page.target;Object.assign(page,{case:'special_world_social_navigation',semanticTargets:true,handwrittenCoordinates:false,nativeScreens:['net.minecraft.client.gui.screens.social.SocialInteractionsScreen'],localNavigationOnly:true,reportSubmitted:false,visits:[{},{},{}]});assert.equal(eUiFiniteOperationProof(actualPage,target).socialScreenOperationCoverage,true);
  for(const mutate of [row=>row.target='foreign-target',row=>row.pauseScreenClass='net.minecraft.client.gui.screens.TitleScreen',row=>row.refusalPresentation.children=[],row=>row.refusalPresentation.children.push({...row.refusalPresentation.children[0]}),row=>row.refusalPresentation.children[0].label='Saving world',row=>row.refusalPresentation.children[0].visible=false,row=>row.refusalPresentation.children[0].controlKey='hidden.debug.actionbar',row=>row.after.pressedKeyCount=1,row=>row.after.inputCleanupFailed=true]){const changed=eUiFiniteFixture(target);mutate(social(changed));assert.throws(()=>eUiFiniteOperationProof(changed,target));}
  const duplicate=eUiFiniteFixture(target);duplicate.navigationCompletionSamples.push({...social(duplicate)});assert.throws(()=>eUiFiniteOperationProof(duplicate,target));
  if(target.startsWith('1.')||target==='26.1.2-neoforge')for(const mutate of [row=>row.entry={active:false},row=>delete row.absentEntryProof,row=>row.absentEntryProof.dataCategory='DEBUG',row=>row.absentEntryProof.screenClass='net.minecraft.client.gui.screens.TitleScreen',row=>row.absentEntryProof.screenIdentity=0,row=>row.absentEntryProof.screenRevision=-1,row=>row.absentEntryProof.socialEntries=1,row=>row.absentEntryProof.alternateEntry.nodeId='',row=>row.absentEntryProof.alternateEntry.translationKey='menu.playerReporting',row=>row.absentEntryProof.alternateEntry.visible=false,row=>row.absentEntryProof.alternateEntry.active=false,row=>row.absentEntryProof.alternateEntry.actions=[],row=>row.disabledEntryProof={}]){const changed=eUiFiniteFixture(target);mutate(social(changed));assert.throws(()=>eUiFiniteOperationProof(changed,target));}
  else{
   for(const mutate of [row=>row.entry.active=true,row=>row.entry=null,row=>row.disposition='NATIVE_SINGLEPLAYER_ENTRY_ABSENT']){const changed=eUiFiniteFixture(target);mutate(social(changed));assert.throws(()=>eUiFiniteOperationProof(changed,target));}
   const forged=eUiFiniteFixture(target),legacy=social(eUiFiniteFixture('1.20.1-forge'));Object.assign(social(forged),{disposition:legacy.disposition,entry:null,absentEntryProof:legacy.absentEntryProof});assert.throws(()=>eUiFiniteOperationProof(forged,target));
  }
 }
});

test('E UI bed cleanup preserves all initial hotbar contents and proves only its own optional single pickup was cleared',()=>{
 const cleanup=result=>result.navigationCompletionSamples.find(row=>row.case==='special_world_in_bed_chat').bedCleanup;
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
  const original=eUiFiniteFixture(target);eUiFiniteOperationProof(original,target);
  const noPickup=eUiFiniteFixture(target),none=cleanup(noPickup);none.addedBeds=0;none.beforeHotbar=structuredClone(none.initialHotbar);delete none.removedPickup;eUiFiniteOperationProof(noPickup,target);
  const initiallyEmpty=eUiFiniteFixture(target),empty=cleanup(initiallyEmpty);empty.initialBedCount=0;empty.initialHotbar[0]={...empty.initialHotbar[0],item:'minecraft:air',count:0};empty.beforeHotbar[0]={...empty.initialHotbar[0]};empty.afterHotbar=structuredClone(empty.initialHotbar);eUiFiniteOperationProof(initiallyEmpty,target);
  for(const mutate of [
   row=>delete row.bedCleanup,
   row=>row.bedCleanup.initialBedCount++,
   row=>row.bedCleanup.initialHotbar.pop(),
   row=>row.bedCleanup.initialHotbar[1].controlKey='hud.hotbar.0',
   row=>row.bedCleanup.initialHotbar[1].selected=false,
   row=>row.bedCleanup.beforeHotbar[0].selected=true,
   row=>row.bedCleanup.beforeHotbar[0].item='unqualified_item',
   row=>row.bedCleanup.beforeHotbar[0].count=-1,
   row=>row.bedCleanup.beforeHotbar[0].count=1.5,
   row=>row.bedCleanup.beforeHotbar[0].selected='false',
   row=>row.bedCleanup.beforeHotbar[1].count=1,
   row=>row.bedCleanup.beforeHotbar[0].count=0,
   row=>row.bedCleanup.beforeHotbar[0].count=3,
   row=>row.bedCleanup.addedBeds=-1,
   row=>row.bedCleanup.addedBeds=2,
   row=>row.bedCleanup.addedBeds=0,
   row=>row.bedCleanup.afterHotbar[0]={...row.bedCleanup.afterHotbar[0],item:'minecraft:air',count:0},
   row=>row.bedCleanup.afterHotbar[0].count=2,
   row=>row.bedCleanup.afterHotbar[2].count=2,
   row=>row.bedCleanup.afterTick=row.bedCleanup.observedTick,
   row=>row.bedCleanup.stableTick=row.bedCleanup.afterTick,
   row=>row.bedCleanup.observedTick=NaN,
   row=>row.bedCleanup.dropReadback.dataCategory='DEBUG',
   row=>row.bedCleanup.dropReadback.authority='SERVER_AUTHORITATIVE',
   row=>row.bedCleanup.dropReadback.chunkLoadRequested=true,
   row=>row.bedCleanup.dropReadback.entitiesTruncated=true,
   row=>row.bedCleanup.dropReadback.remainingBedDrops=[{itemId:'minecraft:red_bed',itemCount:1}],
   row=>delete row.bedCleanup.removedDrop,
   row=>delete row.bedCleanup.removedPickup,
   row=>row.bedCleanup.removedPickup.effectConfirmed=true,
   row=>row.bedCleanup.unrelatedItemsCleared=true,
   row=>row.bedCleanup.source='DEBUG_inventory_snapshot',
   row=>row.bedCleanup.pickupLimit=2
  ]){const changed=eUiFiniteFixture(target),row=changed.navigationCompletionSamples.find(value=>value.case==='special_world_in_bed_chat');mutate(row);assert.throws(()=>eUiFiniteOperationProof(changed,target));}
  const unnecessaryClear=structuredClone(noPickup);cleanup(unnecessaryClear).removedPickup={...cleanup(original).removedPickup};assert.throws(()=>eUiFiniteOperationProof(unnecessaryClear,target));
 }
});

test('E UI bed preparation binds prior bounded USER drops, an unconfirmed removal receipt and an independently empty owned area',()=>{
 const bed=result=>result.navigationCompletionSamples.find(row=>row.case==='special_world_in_bed_chat'),ownedDrops=[{itemId:'minecraft:red_bed',itemCount:1,x:-1,y:79,z:1},{itemId:'minecraft:red_bed',itemCount:1,x:3,y:84,z:6}];
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
  const empty=eUiFiniteFixture(target);eUiFiniteOperationProof(empty,target);
  const prior=eUiFiniteFixture(target);bed(prior).priorDropReadback.remainingBedDrops=structuredClone(ownedDrops);eUiFiniteOperationProof(prior,target);
  for(const mutate of [
   row=>delete row.removedPriorDrops,
   row=>row.removedPriorDrops.accepted=false,
   row=>row.removedPriorDrops.effectConfirmed=true,
   row=>delete row.priorDropReadback,
   row=>delete row.preparationDropReadback,
   row=>row.priorDropReadback.dataCategory='DEBUG',
   row=>row.priorDropReadback.authority='SERVER_AUTHORITATIVE',
   row=>row.priorDropReadback.chunkLoadRequested=true,
   row=>row.priorDropReadback.entitiesTruncated=true,
   row=>row.preparationDropReadback.remainingBedDrops=structuredClone(ownedDrops),
   row=>row.preparationDropReadback.dataCategory='DEBUG',
   row=>row.preparationDropReadback.chunkLoadRequested=true,
   row=>row.priorDropReadback.remainingBedDrops[0].itemId='minecraft:diamond',
   row=>row.priorDropReadback.remainingBedDrops[0].itemCount=0,
   row=>row.priorDropReadback.remainingBedDrops[0].itemCount=1.5,
   row=>row.priorDropReadback.remainingBedDrops[0].x=-1.01,
   row=>row.priorDropReadback.remainingBedDrops[1].x=3.01,
   row=>row.priorDropReadback.remainingBedDrops[0].y=78.99,
   row=>row.priorDropReadback.remainingBedDrops[1].y=84.01,
   row=>row.priorDropReadback.remainingBedDrops[0].z=.99,
   row=>row.priorDropReadback.remainingBedDrops[1].z=6.01,
   row=>row.priorDropReadback.remainingBedDrops[0].x=NaN,
   row=>row.priorDropReadback.remainingBedDrops[0].uuid='hidden-native-identity',
   row=>row.priorDropReadback.remainingBedDrops=Array.from({length:129},()=>({...ownedDrops[0]}))
  ]){const changed=eUiFiniteFixture(target),row=bed(changed);row.priorDropReadback.remainingBedDrops=structuredClone(ownedDrops);mutate(row);assert.throws(()=>eUiFiniteOperationProof(changed,target));}
 }
});

test('E UI recovery respects actual Disconnected parent ownership and only Forge datapack cancellation returns to Title',()=>{
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
  const result=eUiFiniteFixture(target),disconnected=result.navigationCompletionSamples.find(row=>row.case==='recovery_ui_disconnected'),datapack=result.navigationCompletionSamples.find(row=>row.case==='recovery_ui_datapack_failure');assert.equal(disconnected.returned.returnedToFixture,true);assert.equal(disconnected.returned.callbackAction,'none');assert(disconnected.returnedScreen.endsWith('UiRecoveryFixtureScreen'));assert.equal(datapack.returned.returnedToFixture,target!=='1.20.1-forge');eUiFiniteOperationProof(result,target);
  for(const mutate of [value=>{const row=value.navigationCompletionSamples.find(row=>row.case==='recovery_ui_disconnected');row.returned.returnedToFixture=false;row.returnedScreen='net.minecraft.client.gui.screens.TitleScreen';},value=>{const row=value.navigationCompletionSamples.find(row=>row.case==='recovery_ui_datapack_failure');row.returned.returnedToFixture=!row.returned.returnedToFixture;}]){const changed=eUiFiniteFixture(target);mutate(changed);assert.throws(()=>eUiFiniteOperationProof(changed,target));}
 }
});

test('E UI finite native proofs reject wrong process, fake callbacks, TCP cleanup failure, missing ACK and premature cancellation',()=>{
 const find=(r,name)=>r.navigationCompletionSamples.find(row=>row.case===name);
 for(const mutate of [
  r=>find(r,'navigation_native_game_mode_completion').operations[0].uncommitted.gameMode='survival',
  r=>find(r,'navigation_settings_completion').screens[0].screenClass='net.minecraft.client.gui.screens.TitleScreen',
  r=>find(r,'navigation_world_creation_completion').nativeScreens=[],
  r=>find(r,'navigation_native_dialog_style_hit').nativeStyleTarget.clickEventType='run_command',
  r=>find(r,'navigation_dialog_callback_guards').rejected[1].failure.code='success',
  r=>find(r,'navigation_dialog_variants').realExternalServiceAvailabilityTested=true,
  r=>find(r,'native_confirmation_popup').arranged.processId++,
  r=>find(r,'native_confirmation_popup').returned.callbackAction='none',
  r=>find(r,'native_confirmation_backup').draft.after=structuredClone(find(r,'native_confirmation_backup').draft.before),
  r=>find(r,'owned_connection_cleanup').openSockets=1,
  r=>find(r,'owned_connection_cleanup').observedLifetimeMillis=find(r,'owned_connection_cleanup').maximumMillis+1,
  r=>find(r,'owned_connection_cleanup').maximumMillis=60000,
  r=>find(r,'owned_connection_native_cancel').connection.serverInitiatedClose='final_cleanup',
  r=>find(r,'recovery_ui_file_fixer_progress').returned.returnedNativeTick=find(r,'recovery_ui_file_fixer_progress').returned.cancelObservedNativeTick,
  r=>find(r,'recovery_ui_graphics_warning').returned.graphicsSettingsChanged=true,
  r=>find(r,'world_recovery_copy').sourceWorldWritten=true,
  r=>find(r,'world_recovery_copy').limits.files=100000,
  r=>find(r,'world_recovery_optimize').returned.workerJoined=false,
  r=>find(r,'world_recovery_cleanup').nativeResourcesReleased=false,
  r=>find(r,'reconfiguration_native_entry').proof.configurationAcknowledged=false,
  r=>find(r,'reconfiguration_native_disconnect').nativeReady.readyNativeDelayTicker=599,
  r=>find(r,'reconfiguration_native_disconnect').proof.sourceDirectoryLockReleased=false,
  r=>find(r,'reconfiguration_native_entry').semanticActions[0].requestReplayed=true,
  r=>find(r,'recipe_overlay_fill').realMenuContentsConfirmed=false,
  r=>find(r,'recipe_overlay_fill').ingredients[0].count=0,
  r=>find(r,'creative_saved_hotbars').loaded[0].count=1,
  r=>find(r,'creative_saved_hotbars').actions[1].activator=67,
  r=>find(r,'special_world_in_bed_chat').restoredRule.value=101,
  r=>find(r,'special_world_nautilus_inventory').source.slotOwner='menu',
  r=>find(r,'special_world_social_environment_limit').screenOperationCoverage=true,
  r=>find(r,'special_world_social_environment_limit').externalServiceAcceptance=true,
 ]){const result=eUiFiniteFixture();mutate(result);assert.throws(()=>eUiFiniteOperationProof(result,result.target));}
 for(const target of ['1.20.1-forge','1.21.1-neoforge']){
  const result=eUiFiniteFixture(target);find(result,'navigation_native_unavailable').operationCoverage=true;assert.throws(()=>eUiFiniteOperationProof(result,target));
  const recovery=eUiFiniteFixture(target);find(recovery,'recovery_ui_file_fixer_progress').verified=true;assert.throws(()=>eUiFiniteOperationProof(recovery,target));
 }
 const old=eUiFiniteFixture('1.20.1-forge');find(old,'native_reconfiguration_unavailable').operationCoverage=true;assert.throws(()=>eUiFiniteOperationProof(old,old.target));
});

test('E UI recipe selection requires Native owner selectedTab and a real selected Search restoration, not page rotation',()=>{
 const recipe=result=>result.navigationCompletionSamples.find(row=>row.case==='recipe_controls');
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
  const result=eUiFiniteFixture(target);assert.equal(eUiFiniteOperationProof(result,target).fullUiMilestoneClaimed,false);
  // A displayed page can naturally rotate even while its real selected tab stays unchanged.
  recipe(result).category.items=[...recipe(result).originalItems];eUiFiniteOperationProof(result,target);
  for(const mutate of [row=>delete row.category.selected,row=>row.category.selected=false,row=>delete row.restoredCategory,row=>row.restoredCategory.selected=false,row=>row.restoredCategory.label='CRAFTING_EQUIPMENT',row=>row.restoredCategory.label=target.startsWith('1.')?'CRAFTING':'CRAFTING_SEARCH',row=>row.restoredCategory.nodeId=row.category.nodeId,row=>delete row.selectionSource,row=>row.selectionSource='different_recipe_items',row=>row.category.resourceId='RecipeBookCategory@70ab12']){const changed=eUiFiniteFixture(target);mutate(recipe(changed));assert.throws(()=>eUiFiniteOperationProof(changed,target));}
  if(!target.startsWith('1.'))for(const mutate of [row=>delete row.category.resourceId,row=>row.category.label='RecipeBookCategory@70ab12']){const changed=eUiFiniteFixture(target);mutate(recipe(changed));assert.throws(()=>eUiFiniteOperationProof(changed,target));}
 }
});

test('E UI recipe overlay requires the Native Ctrl freeze route and still verifies actual slot fill and released input',()=>{
 const overlay=result=>result.navigationCompletionSamples.find(row=>row.case==='recipe_overlay_fill');
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
  const result=eUiFiniteFixture(target);assert.equal(overlay(result).nativeControlFreezeUsed,true);eUiFiniteOperationProof(result,target);
  for(const mutate of [row=>delete row.nativeControlFreezeUsed,row=>row.nativeControlFreezeUsed=false,row=>row.realMenuContentsConfirmed=false,row=>row.ghostRecipeIsNotSufficient=false,row=>row.input.pressedKeyCount=1,row=>row.input.inputCleanupFailed=true]){const changed=eUiFiniteFixture(target);mutate(overlay(changed));assert.throws(()=>eUiFiniteOperationProof(changed,target));}
 }
});

test('E UI recipe dismissal recomputes a standard click inside its Native popup and outside all options on the same owner',()=>{
 const fill=result=>result.navigationCompletionSamples.find(row=>row.case==='recipe_overlay_fill');
 for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
  const result=eUiFiniteFixture(target);eUiFiniteOperationProof(result,target);assert.equal(fill(result).overlayDismissal.semanticNodeClick,false);
  for(const mutate of [
   row=>delete row.overlayDismissal,row=>row.overlayDismissal.targeting='semantic_node_click',row=>row.overlayDismissal.semanticNodeClick=true,
   row=>row.overlayDismissal.source.dataCategory='DEBUG',row=>row.overlayDismissal.source.overlayNodeId='replacement-owner',
   row=>row.overlayDismissal.source.popup.controlKey='recipe.option',row=>row.overlayDismissal.source.popup.width=0,
   row=>row.overlayDismissal.source.popup.x=NaN,row=>row.overlayDismissal.point.x=30,
   row=>row.overlayDismissal.point={x:3,y:3},row=>row.overlayDismissal.point={x:17,y:3},
   row=>row.overlayDismissal.source.options.pop(),row=>row.overlayDismissal.source.options[1].nodeId=row.overlayDismissal.source.options[0].nodeId,
   row=>row.overlayDismissal.after.screenIdentity++,row=>row.overlayDismissal.after.screenClass='net.minecraft.client.gui.screens.TitleScreen',
   row=>row.overlayDismissal.operationId='',row=>row.overlayDismissal.stepType='ui.click',row=>row.overlayDismissal.state='queued',
   row=>row.overlayDismissal.overlayAbsent=false,row=>row.overlayDismissal.after.pressedButtonCount=1,
   row=>row.overlayDismissal.after.inputCleanupPending=true,row=>row.overlayDismissal.after.inputCleanupFailed=true,
  ]){const changed=eUiFiniteFixture(target);mutate(fill(changed));assert.throws(()=>eUiFiniteOperationProof(changed,target));}
 }
});

test('E UI distinguishes native optimizer completion before Cancel from actual cancellation without replay',()=>{
 const result=eUiFiniteFixture(),rows=result.navigationCompletionSamples,optimize=rows.find(row=>row.case==='world_recovery_optimize'),cleanup=rows.find(row=>row.case==='world_recovery_cleanup');
 optimize.nativeScreenObserved=false;optimize.nativeScreens=[];optimize.cancelInputSubmitted=false;optimize.semanticActions=[];optimize.returned.callbackAction='optimize_finished';optimize.returned.nativeFinishedBeforeCancel=true;optimize.optimizeCancelVerified=false;optimize.outcome='finished_before_cancel_not_cancel_coverage';cleanup.optimizeCancelVerified=false;
 assert.equal(eUiFiniteOperationProof(result,result.target).optimizeCancelVerified,false);
 for(const mutate of [r=>r.navigationCompletionSamples.find(row=>row.case==='world_recovery_optimize').optimizeCancelVerified=true,r=>r.navigationCompletionSamples.find(row=>row.case==='world_recovery_cleanup').optimizeCancelVerified=true,r=>r.navigationCompletionSamples.find(row=>row.case==='world_recovery_optimize').returned.workerTerminated=false]){const changed=structuredClone(result);mutate(changed);assert.throws(()=>eUiFiniteOperationProof(changed,changed.target));}
 const boundary=eUiFiniteFixture(),entry=boundary.navigationCompletionSamples.find(row=>row.case==='reconfiguration_native_entry');entry.semanticActions=[{requestOutcome:'not_confirmed',requestReplayed:false,runtimeError:{error:'REQUEST_CANCELLED',executionOutcome:'not_confirmed',retrySafe:false}}];assert.equal(eUiFiniteOperationProof(boundary,boundary.target).reconfigurationApplicable,true);entry.semanticActions[0].runtimeError.retrySafe=true;assert.throws(()=>eUiFiniteOperationProof(boundary,boundary.target));
});

test('E UI saving proof requires native pause-save stages or the exact default Forge non-rendering boundary',()=>{
 for(const mutate of [r=>r.hudCompletionSamples.find(row=>row.case==='hud_native_saving_indicator').nativeDraw.processId++,r=>r.hudCompletionSamples.find(row=>row.case==='hud_native_saving_indicator').nativeCompleted.nativeSaveReturned=false,r=>r.hudCompletionSamples.find(row=>row.case==='hud_native_saving_indicator').prepared.readyForArm=false,r=>r.hudCompletionSamples.find(row=>row.case==='hud_native_saving_indicator').commandUsed=true]){const result=eUiKnownFixture();mutate(result);assert.throws(()=>eUiKnownCompletionProof(result,result.target));}
 for(const mutate of [s=>s.userDrawValidated=true,s=>s.pauseRequests=1,s=>s.applicability.selectedNativePauseSaveCalls=1,s=>s.applicability.processId++,s=>s.applicability.actualHudRenderer='net.minecraft.client.gui.Gui',s=>s.applicability.actualRegisteredOverlayIds.push('minecraft:saving'),s=>s.applicability.nativeVanillaOverlayIds.reverse()]){const result=eUiKnownFixture('1.20.1-forge');mutate(result.hudCompletionSamples.find(row=>row.case==='hud_native_saving_indicator_not_applicable'));assert.throws(()=>eUiKnownCompletionProof(result,result.target));}
});

test('Demo proof binds its actual launch draw and SaveQuit tools but ignores imported unexecuted UI branches',async()=>{
 const repo=resolve(import.meta.dirname,'../../build/test/e-ui-demo-'+randomUUID()),target='1.20.1-forge',ui='components/validation_tools/conformance-vanilla_ui/task/',world='components/validation_tools/conformance-world_read/task/',product=`versions/${target}/src/main/java/Product.java`,unusedProbe=`versions/${target}/src/probe/java/io/github/recrivenvi/minecraftprotocol/probe/validation/UiPlayerListFixtureScreen.java`,probeMixin=unusedProbe.replace('UiPlayerListFixtureScreen.java','mixin/UiPlayerListProbeMixin.java');
 const files=[product,unusedProbe,probeMixin,`versions/${target}/target.properties`,`versions/${target}/build.gradle.kts`,'gradle.properties','settings.gradle.kts','gradle/libs.versions.toml','components/protocol_schema/src/main/openapi/minecraft-control-v0.json','components/companion/src/server.ts','components/companion/package.json','components/companion/package-lock.json',...['run-vanilla-ui.mjs','Invoke-VanillaUiSuite.ps1','vanilla-ui.init.gradle','hud-demo-actions.mjs','hud-late-text-actions.mjs'].map(name=>ui+name),...['interaction-baseline-actions.mjs','context-exit-evidence.mjs'].map(name=>world+name)],hashes={};
 for(const path of files){await mkdir(dirname(resolve(repo,path)),{recursive:true});await writeFile(resolve(repo,path),path);hashes[path]=createHash('sha256').update(path).digest('hex');}
 const unused=ui+'ui-native-confirmation-actions.mjs',raw={target,scenario:'hud-demo',sourceHashes:{...hashes,[unused]:'0'.repeat(64),[world+'continuous-performance-actions.mjs']:'0'.repeat(64)}};
 await writeFile(resolve(repo,unusedProbe),'Changed unused local PlayerList fixture\n');
 const bindings=await verifyEUiCandidateBindings(repo,raw,target,{[product]:hashes[product]});assert(!Object.hasOwn(bindings,unused));assert(!Object.hasOwn(bindings,world+'continuous-performance-actions.mjs'));assert(!Object.hasOwn(bindings,unusedProbe));assert(Object.hasOwn(bindings,probeMixin));
 assert(Object.hasOwn(eUiScenarioSources(raw.sourceHashes,'all'),unusedProbe));
 await assert.rejects(verifyEUiEvidenceSources(repo,{target,scenario:'all',sourceHashes:{[unusedProbe]:hashes[unusedProbe]}}),/Stale executed E UI source/);
 for(const path of [product,ui+'run-vanilla-ui.mjs',ui+'hud-demo-actions.mjs',ui+'vanilla-ui.init.gradle',world+'context-exit-evidence.mjs']){const absent={...raw.sourceHashes};delete absent[path];await assert.rejects(verifyEUiCandidateBindings(repo,{...raw,sourceHashes:absent},target,{[product]:hashes[product]}),/Missing E UI/);await assert.rejects(verifyEUiCandidateBindings(repo,{...raw,sourceHashes:{...raw.sourceHashes,[path]:'0'.repeat(64)}},target,{[product]:hashes[product]}),/Stale executed E UI source/);}
 for(const path of [probeMixin,ui+'hud-demo-actions.mjs']){await writeFile(resolve(repo,path),'Changed bound Demo source\n');await assert.rejects(verifyEUiCandidateBindings(repo,raw,target,{[product]:hashes[product]}),/Stale executed E UI source/);await writeFile(resolve(repo,path),path);}
});

test('E UI CF021 proof rejects the original ignored-coordinate broad result, hidden client data, chunk loading and synthetic completion',()=>{
 const r=eUiBlockEntities();assert.equal(blockEntitySelectorProof(r,r.target).exactCachedCoordinates,true);
 for(const mutate of [r=>{const exact=r.actionSamples.find(row=>row.case==='block_entity_selector_exact');exact.value=exact.broad;},r=>r.actionSamples.find(row=>row.case==='block_entity_selector_both').value.client.blockEntities=[],r=>r.actionSamples.find(row=>row.case==='block_entity_selector_client_rejected').httpStatus=200,r=>r.actionSamples.find(row=>row.case==='block_entity_selector_unloaded').after.server.blocks[0].available=true,r=>r.actionSamples.at(-1).liveEvidence=false,r=>r.actionSamples.at(-1).inputAfterReads.inputDispatchSequence++,r=>r.actionSamples.find(row=>row.case==='block_entity_selector_cleanup').cleanup.pop()]){const changed=structuredClone(r);mutate(changed);assert.throws(()=>blockEntitySelectorProof(changed,changed.target));}
});

test('E UI camera proof recomputes first background movement and turn without focus, replay, yaw mutation or a performance claim',()=>{
 const r=eUiCamera();assert.equal(firstCameraProof(r,r.target).firstBackgroundCamera,true);
 for(const mutate of [r=>r.actionSamples[0].beforeInput.hostFocused=true,r=>r.actionSamples[0].inputReplayed=true,r=>r.actionSamples[0].directCameraMutation=true,r=>r.actionSamples[0].observations.forEach(p=>p.yaw=r.actionSamples[0].beforePlayer.yaw),r=>r.actionSamples[0].observations.forEach(p=>p.x=r.actionSamples[0].beforePlayer.x),r=>r.actionSamples[0].afterInput.hostCursorCaptured=true,r=>r.actionSamples[0].afterInput.pressedKeyCount=1]){const changed=structuredClone(r);mutate(changed);assert.throws(()=>firstCameraProof(changed,changed.target));}
});

test('E UI command collaboration requires actual native server counters, current permission trees, partial batch and real MCP',()=>{
 const r=eUiCommand();assert.equal(commandCollaborationProof(r,r.target).actualServerPermissionFixture,true);
 for(const mutate of [r=>r.actionSamples[0].receipt.requiresControlLease=true,r=>r.actionSamples[1].tree.roots=['time'],r=>r.actionSamples[1].after.nativePlayerCommands++,r=>r.actionSamples[2].batch.commands[2].status='submitted_unconfirmed',r=>r.actionSamples[3].before.nativePermissionTreeSends=1,r=>r.actionSamples[4].transport='injected_unit_test',r=>r.actionSamples.at(-1).humanCooperationTested=true,r=>r.actionSamples.at(-1).physicalEscTested=true]){const changed=structuredClone(r);mutate(changed);assert.throws(()=>commandCollaborationProof(changed,changed.target));}
});

test('E UI current evidence binds real executed bytes but excludes test files and imported unexecuted world branches',async()=>{
 const repo=resolve(import.meta.dirname,'../../build/test/e-ui-proof-'+randomUUID()),product='versions/26.3-neoforge/src/main/java/Product.java',tool='components/validation_tools/conformance-world_read/task/input-context-actions.mjs',runner='components/validation_tools/conformance-world_read/task/world-read-smoke.mjs',unused='components/validation_tools/conformance-world_read/task/continuous-performance-actions.mjs',testFile='versions/26.3-neoforge/src/test/java/OnlyTest.java',bindings={};
 for(const path of [product,tool,runner]){await mkdir(dirname(resolve(repo,path)),{recursive:true});await writeFile(resolve(repo,path),path);bindings[path]=createHash('sha256').update(path).digest('hex');}
 const raw={scenario:'input-context',sourceHashes:{...bindings,[unused]:'0'.repeat(64),[testFile]:'0'.repeat(64)}};const checked=await verifyEUiEvidenceSources(repo,raw);assert.deepEqual(Object.keys(checked).sort(),Object.keys(bindings).sort());assert(Object.hasOwn(raw.sourceHashes,unused));
 for(const path of [product,tool,runner])await assert.rejects(verifyEUiEvidenceSources(repo,{...raw,sourceHashes:{...raw.sourceHashes,[path]:'0'.repeat(64)}}),/Stale executed E UI source/);
 const all=eUiScenarioSources(raw.sourceHashes,'all');assert(Object.hasOwn(all,unused));assert(!Object.hasOwn(all,testFile));
});

test('Controlled source fixtures permit only the reverse-hashed unexecuted Forge count update, not Live proof',async()=>{
 const repo=resolve(import.meta.dirname,'../../build/test/e-ui-forge-count-'+randomUUID()),runner='components/validation_tools/conformance-vanilla_ui/task/run-vanilla-ui.mjs',path=resolve(repo,runner),digest=bytes=>createHash('sha256').update(bytes).digest('hex');
 const current=await readFile(resolve(import.meta.dirname,'../../../..',runner),'utf8'),updated="'1.20.1-forge':[34,6]",previous="'1.20.1-forge':[35,5]";
 assert.equal(current.split(updated).length-1,1);assert.equal(current.includes(previous),false);
 assert(current.includes("if(args.phase!=='quit'&&args.scenario==='hud-demo'){"));assert(current.includes("}else if(args.phase!=='quit'){"));assert(current.includes("}[target];"));
 const before=current.replace(updated,previous),oldDigest=digest(before),raw=(target,scenario,source=runner)=>({target,scenario,sourceHashes:{[source]:oldDigest}});
 await mkdir(dirname(path),{recursive:true});await writeFile(path,current);
 for(const[target,scenario]of [['26.3-neoforge','hud-demo'],['26.3-neoforge','all'],['1.21.1-neoforge','hud-demo'],['26.3-fabric','all'],['1.20.1-forge','hud-demo']]){
  const result=raw(target,scenario),checked=await verifyEUiEvidenceSources(repo,result);assert.equal(checked[runner],oldDigest);assert.equal(result.sourceHashes[runner],oldDigest);
 }
 await verifyEUiEvidenceSources(repo,{target:'1.20.1-forge',scenario:'all',sourceHashes:{[runner]:digest(current)}});
 for(const[target,scenario]of [['1.20.1-forge','all'],['1.21.1-neoforge','all'],['unregistered-target','all'],['26.3-neoforge','input-context']])await assert.rejects(verifyEUiEvidenceSources(repo,raw(target,scenario)),/Stale executed E UI source/);
 for(const changed of [current+'\n',current.replace("args.scenario==='hud-demo'","args.scenario!=='hud-demo'"),current.replace("'1.21.1-neoforge':[37,6]","'1.21.1-neoforge':[38,6]"),current+'\n// '+updated+'\n',current+'\n// '+previous+'\n']){
  await writeFile(path,changed);await assert.rejects(verifyEUiEvidenceSources(repo,raw('26.3-neoforge','hud-demo')),/Stale executed E UI source/);
 }
 for(const changed of [current+'\n// '+updated+'\n',current+'\n// '+previous+'\n']){
  await writeFile(path,changed);const ambiguous=raw('26.3-neoforge','hud-demo');ambiguous.sourceHashes[runner]=digest(changed.replace(updated,previous));
  await assert.rejects(verifyEUiEvidenceSources(repo,ambiguous),/Stale executed E UI source/);
 }
 await writeFile(path,current);const differentSource=runner.replace('run-vanilla-ui.mjs','copied-runner.mjs');await writeFile(resolve(repo,differentSource),current);
 await assert.rejects(verifyEUiEvidenceSources(repo,raw('26.3-neoforge','all',differentSource)),/Stale executed E UI source/);
});

test('Known legacy operator-count changes require exact unexecuted bytes and never bypass either legacy all or Runtime bindings',async()=>{
 const repo=resolve(import.meta.dirname,'../../build/test/e-ui-legacy-counts-'+randomUUID()),runner='components/validation_tools/conformance-vanilla_ui/task/run-vanilla-ui.mjs',path=resolve(repo,runner),digest=bytes=>createHash('sha256').update(bytes).digest('hex'),current=await readFile(resolve(import.meta.dirname,'../../../..',runner),'utf8');
 const forge="'1.20.1-forge':[34,6]",neo="'1.21.1-neoforge':[37,6]";
 for(const value of [forge,neo])assert.equal(current.split(value).length-1,1);
 await mkdir(dirname(path),{recursive:true});await writeFile(path,current);
 for(const[oldForge,oldNeo]of [[forge,"'1.21.1-neoforge':[37,5]"],["'1.20.1-forge':[34,5]",neo],["'1.20.1-forge':[34,5]","'1.21.1-neoforge':[37,5]"],["'1.20.1-forge':[35,5]","'1.21.1-neoforge':[37,5]"]]){
  const bound=digest(current.replace(forge,oldForge).replace(neo,oldNeo));
  for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
   const result={target,scenario:'hud-demo',sourceHashes:{[runner]:bound}};assert.equal((await verifyEUiEvidenceSources(repo,result))[runner],bound);
   if(target.startsWith('26.'))await verifyEUiEvidenceSources(repo,{...result,scenario:'all'});
   else await assert.rejects(verifyEUiEvidenceSources(repo,{...result,scenario:'all'}),/Stale executed E UI source/);
  }
 }
 for(const bytes of [current.replace(forge,"'1.20.1-forge':[35,6]"),current.replace(neo,"'1.21.1-neoforge':[37,7]"),current.replace(neo,"'1.21.1-neoforge':[36,5]")])await assert.rejects(verifyEUiEvidenceSources(repo,{target:'26.3-neoforge',scenario:'all',sourceHashes:{[runner]:digest(bytes)}}),/Stale executed E UI source/);
 const repeated=current+'\n// '+neo+'\n';await writeFile(path,repeated);
 await assert.rejects(verifyEUiEvidenceSources(repo,{target:'26.3-neoforge',scenario:'hud-demo',sourceHashes:{[runner]:digest(repeated.replace(neo,"'1.21.1-neoforge':[37,5]"))}}),/Stale executed E UI source/);
 await writeFile(path,current);
 for(const target of ['1.20.1-forge','1.21.1-neoforge']){
  const product=`versions/${target}/src/main/java/Runtime.java`;await mkdir(dirname(resolve(repo,product)),{recursive:true});await writeFile(resolve(repo,product),'Actual Runtime changed\n');
  await assert.rejects(verifyEUiEvidenceSources(repo,{target,scenario:'hud-demo',sourceHashes:{[runner]:digest(current.replace(forge,"'1.20.1-forge':[34,5]").replace(neo,"'1.21.1-neoforge':[37,5]")),[product]:digest('Previous Runtime\n')}}),/Stale executed E UI source/);
 }
});

test('Controlled source proofs permit only the three exact unexecuted 26.1.2 Options detours without normalizing other bytes',async()=>{
 const repo=resolve(import.meta.dirname,'../../build/test/e-ui-world-options-'+randomUUID()),ui='components/validation_tools/conformance-vanilla_ui/task/',digest=bytes=>createHash('sha256').update(bytes).digest('hex');
 const changes=[
  ['hud-camera-variant-actions.mjs',[["      await tap(256); await page('PauseScreen');\n      if (target === '26.1.2-neoforge') {await button('menu.options'); await page('OptionsScreen');}\n      await button('options.worldOptions.button'); await page('WorldOptionsScreen');\n","      await tap(256); await page('PauseScreen'); await button('options.worldOptions.button'); await page('WorldOptionsScreen');\n"]]],
  ['ui-special-world-menu-actions.mjs',[["    await key(256); await page('PauseScreen');\n    if (target === '26.1.2-neoforge') {await click('menu.options', 'Options...'); await page('OptionsScreen');}\n    await click('options.worldOptions.button', 'World Options...'); await page('WorldOptionsScreen');\n","    await key(256); await page('PauseScreen'); await click('options.worldOptions.button', 'World Options...'); await page('WorldOptionsScreen');\n"],["      await button('gui.cancel'); await page('WorldOptionsScreen');\n      if (target === '26.1.2-neoforge') {await button('gui.done'); await page('OptionsScreen'); await button('gui.done');}\n      else await button('gui.cancel');\n      await page('PauseScreen'); await key(256); await gameplay();\n","      await button('gui.cancel'); await page('WorldOptionsScreen'); await button('gui.cancel'); await page('PauseScreen'); await key(256); await gameplay();\n"],["    const absent = (target.startsWith('1.') || target === '26.1.2-neoforge') && entries.length === 0;\n","    const absent = target.startsWith('1.') && entries.length === 0;\n"]]],
  ['ui-navigation-completion-actions.mjs',[["    if(target==='26.1.2-neoforge'){await click('menu.options','Options...');await page('OptionsScreen');}\n",''],["    await button(['gui.cancel'],'Cancel');await page('WorldOptionsScreen');\n    if(target==='26.1.2-neoforge'){await button(['gui.done'],'Done');await page('OptionsScreen');await button(['gui.done'],'Done');}\n    else await button(['gui.cancel'],'Cancel');\n    await page('PauseScreen');await key(256);\n","    await button(['gui.cancel'],'Cancel');await page('WorldOptionsScreen');await button(['gui.cancel'],'Cancel');await page('PauseScreen');await key(256);\n"]]]
 ];
 for(const[name,replacements]of changes){
  const source=ui+name,path=resolve(repo,source),current=await readFile(resolve(import.meta.dirname,'../../../..',source),'utf8');let previous=current;
  for(const[from,to]of replacements){assert.equal(current.split(from).length,2);previous=previous.replace(from,to);}
  const bound=digest(previous),raw=(target='1.21.1-neoforge',scenario='all',key=source)=>({target,scenario,sourceHashes:{[key]:bound}});
  await mkdir(dirname(path),{recursive:true});await writeFile(path,current);
  for(const target of ['1.20.1-forge','1.21.1-neoforge','26.3-neoforge','26.3-fabric']){const result=raw(target);assert.equal((await verifyEUiEvidenceSources(repo,result))[source],bound);assert.equal(result.sourceHashes[source],bound);}
  for(const[target,scenario]of [['26.1.2-neoforge','all'],['unregistered-target','all'],['1.21.1-neoforge','hud-camera'],['1.21.1-neoforge','ui-navigation'],['1.21.1-neoforge','input-context']])await assert.rejects(verifyEUiEvidenceSources(repo,raw(target,scenario)),/Stale executed E UI source/);
  await verifyEUiEvidenceSources(repo,{target:'26.1.2-neoforge',scenario:'all',sourceHashes:{[source]:digest(current)}});
  for(const changed of [current+'\n// Unrelated byte change\n',current.replace(/target\s*===\s*'26\.1\.2-neoforge'/,"target !== '26.1.2-neoforge'"),current.replace('WorldOptionsScreen','UnrelatedScreen')]){await writeFile(path,changed);await assert.rejects(verifyEUiEvidenceSources(repo,raw()),/Stale executed E UI source/);}
  const extraGuard='\nif (target === "26.1.2-neoforge") {}\n';await writeFile(path,current+extraGuard);await assert.rejects(verifyEUiEvidenceSources(repo,{...raw(),sourceHashes:{[source]:digest(previous+extraGuard)}}),/Stale executed E UI source/);
  const repeated=current+replacements[0][0];let repeatedBefore=repeated;for(const[from,to]of replacements)repeatedBefore=repeatedBefore.replace(from,to);await writeFile(path,repeated);await assert.rejects(verifyEUiEvidenceSources(repo,{...raw(),sourceHashes:{[source]:digest(repeatedBefore)}}),/Stale executed E UI source/);
  await writeFile(path,current);const copy=ui+'copied-'+name;await writeFile(resolve(repo,copy),current);await assert.rejects(verifyEUiEvidenceSources(repo,raw('1.21.1-neoforge','all',copy)),/Stale executed E UI source/);
  const product='versions/1.21.1-neoforge/src/main/java/Production.java';await mkdir(dirname(resolve(repo,product)),{recursive:true});await writeFile(resolve(repo,product),current);await assert.rejects(verifyEUiEvidenceSources(repo,{...raw(),sourceHashes:{[source]:bound,[product]:bound}}),/Stale executed E UI source/);
 }
});

test('E UI candidate binding refuses omitted native code, old current-candidate digests, missing executed tools and Companion packages',async()=>{
 const repo=resolve(import.meta.dirname,'../../build/test/e-ui-candidate-'+randomUUID()),target='26.3-neoforge',world='components/validation_tools/conformance-world_read/task/',product=`versions/${target}/src/main/java/Product.java`,files=[product,`versions/${target}/target.properties`,`versions/${target}/build.gradle.kts`,'gradle.properties','settings.gradle.kts','gradle/libs.versions.toml','components/protocol_schema/src/main/openapi/minecraft-control-v0.json','components/companion/src/server.ts','components/companion/package.json','components/companion/package-lock.json',...['world-read-smoke.mjs','Invoke-WorldReadSmoke.ps1','WorldReadFixture.ps1','Test-WorldReadArtifact.ps1','context-exit-evidence.mjs','input-context-actions.mjs'].map(name=>world+name)],sourceHashes={};
 for(const path of files){await mkdir(dirname(resolve(repo,path)),{recursive:true});await writeFile(resolve(repo,path),path);sourceHashes[path]=createHash('sha256').update(path).digest('hex');}
 const result={scenario:'input-context',sourceHashes};await verifyEUiCandidateBindings(repo,result,target,{[product]:sourceHashes[product]});
 for(const path of [product,world+'input-context-actions.mjs','components/companion/package.json','components/companion/package-lock.json']){const changed={...sourceHashes};delete changed[path];await assert.rejects(verifyEUiCandidateBindings(repo,{...result,sourceHashes:changed},target,{[product]:sourceHashes[product]}),/Missing E UI/);}
 await assert.rejects(verifyEUiCandidateBindings(repo,result,target,{[product]:'0'.repeat(64)}),/Different E UI candidate/);
});

test('E UI all binds the actual finite navigation recovery recipe and TCP consumers, not merely check names',async()=>{
 const repo=resolve(import.meta.dirname,'../../build/test/e-ui-finite-binding-'+randomUUID()),target='26.3-neoforge',ui='components/validation_tools/conformance-vanilla_ui/task/',product=`versions/${target}/src/main/java/Product.java`,tools=['run-vanilla-ui.mjs','Invoke-VanillaUiSuite.ps1','vanilla-ui.init.gradle','ui-correctness-actions.mjs','suggestion-debug-actions.mjs','hud-completion-actions.mjs','ui-hardcore-actions.mjs','hud-camera-actions.mjs','hud-camera-variant-actions.mjs','hud-vital-variant-actions.mjs','hud-boss-score-actions.mjs','hud-late-text-actions.mjs','hud-native-message-actions.mjs','hud-saving-actions.mjs','player-list-actions.mjs','ui-local-owner-actions.mjs','ui-navigation-completion-actions.mjs','ui-native-confirmation-actions.mjs','ui-owned-connection-actions.mjs','ui-recovery-actions.mjs','ui-world-recovery-actions.mjs','ui-recipe-hotbar-actions.mjs','ui-special-world-menu-actions.mjs','ui-reconfiguration-actions.mjs','hud-toast-user-actions.mjs'];
 tools.push('ui-toast-lifecycle-actions.mjs','ui-command-management-actions.mjs');
 const files=[product,`versions/${target}/target.properties`,`versions/${target}/build.gradle.kts`,'gradle.properties','settings.gradle.kts','gradle/libs.versions.toml','components/protocol_schema/src/main/openapi/minecraft-control-v0.json','components/companion/src/server.ts','components/companion/package.json','components/companion/package-lock.json',...tools.map(name=>ui+name)],sourceHashes={};
 for(const path of files){await mkdir(dirname(resolve(repo,path)),{recursive:true});await writeFile(resolve(repo,path),path);sourceHashes[path]=createHash('sha256').update(path).digest('hex');}
 const result={scenario:'all',sourceHashes};await verifyEUiCandidateBindings(repo,result,target,{[product]:sourceHashes[product]});
 for(const name of ['ui-owned-connection-actions.mjs','ui-recovery-actions.mjs','ui-world-recovery-actions.mjs','ui-reconfiguration-actions.mjs','ui-recipe-hotbar-actions.mjs','ui-special-world-menu-actions.mjs','hud-camera-variant-actions.mjs','ui-navigation-completion-actions.mjs','hud-saving-actions.mjs','ui-toast-lifecycle-actions.mjs','ui-command-management-actions.mjs']){const path=ui+name,missing={...sourceHashes};delete missing[path];await assert.rejects(verifyEUiCandidateBindings(repo,{...result,sourceHashes:missing},target,{[product]:sourceHashes[product]}),/Missing E UI/);await assert.rejects(verifyEUiCandidateBindings(repo,{...result,sourceHashes:{...sourceHashes,[path]:'0'.repeat(64)}},target,{[product]:sourceHashes[product]}),/Stale executed E UI source/);}
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
