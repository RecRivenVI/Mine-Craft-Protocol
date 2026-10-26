import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {candidateSource,sha256} from './alpha-candidate.mjs';
import {collectInstalledProof} from '../../integration-packaged_runtime/task/installed-evidence.mjs';
import {readDependencyBaseline,historicalTrialBaseline,dependencyPerformance} from './dependency-proof.mjs';
import {eUiRegressionProof,inputContextProof,continuousInputProof,commandConfigurationProof,placementReceiptsProof} from './alpha-evidence.mjs';

export const E_WORLD_PROFILE='e_world_trial_v1';
export const E_WORLD_SCOPE='E_WORLD_affected_groups_and_installed_world_smoke';
export const E_WORLD_GROUPS=Object.freeze({
 '05a':['movement','input-context'],
 '05b':['special-movement','world-seats'],
 '05c':['item-utility','placement-branches','world-placement-receipts','full'],
 '05d':['block-bridge','station-use','lifecycle'],
 '05e':['combat-use','range-branches','world-seats','full'],
 '05f':['entity-branches','entity-social','entity-parts','full'],
 '05g':['lifecycle','player-command','command-configuration','command-collaboration','input-context'],
 '05h':['world-region-functional'],
 '06a':['world-debug-state'],
 '06b':['world-debug-items','block-entity-pages'],
 '06c':['world-debug-entities'],
 '06d':['world-debug-environment'],
 '06e':['world-debug-relations'],
 '06f':['world-debug-loading','world-debug-boundary'],
 '06g':['world-debug-transfer'],
 '06h':['world-debug-providers'],
 affectedContinuous:['continuous-input'],
 affectedUi:['all'],
});

export function eWorldProofSlots(targets){
 assert.deepEqual([...targets].sort(),['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-fabric','26.3-neoforge']);
 return targets.flatMap(target=>[...new Set(Object.values(E_WORLD_GROUPS).flat())].map(scenario=>({target,scenario})));
}

export function eWorldRunProof(result,target,scenario){
 assert.equal(result.target,target);assert.equal(result.scenario,scenario);assert.equal(result.sourceChangedDuringRun,false);assert.equal(result.cleanShutdown,true);assert.equal(result.persistentWriteCalls,0);
 assert.equal(result.status??result.result,'PASS');assert(result.checks.length>0);assert(result.checks.every(check=>check.status==='PASS'));
 if(scenario!=='all'){
  assert.equal(result.evidence,'development_runtime_native');assert.equal(result.fixturePolicy,'immutable_baseline_restored');assert.equal(result.fixtureInput.target,target);assert.equal(result.fixtureInput.role,'client');assert.equal(result.fixtureInput.credentialsCopied,false);assert.match(result.fixtureInput.baselineSha256,/^[0-9a-f]{64}$/);assert.equal(result.fixtureInput.baselineSha256,result.fixtureInput.restoredSha256);
  assert(!(result.actionSamples??[]).some(row=>row.case?.endsWith('_cleanup_failure')));
 }
 return{checks:result.checks.length,cleanShutdown:true,persistentWriteCalls:0};
}

export function eWorldCandidateSourceProof(result,target,scenario,required,current,full){
 const bindings=result.sourceHashes??{},supplemental=[];
 const metadata=new Set(['META-INF/mods.toml','META-INF/neoforge.mods.toml','META-INF/accesstransformer.cfg','pack.mcmeta'].map(path=>`versions/${target}/src/main/resources/${path}`));
 metadata.add(`versions/${target}/src/main/resources/minecraft_protocol_probe.accesswidener`);
 for(const path of required){
  if(bindings[path]===undefined&&scenario==='player-command'&&metadata.has(path)){
   // This recorder hashes Java/JSON/TS only. The required full-world run records
   // the remaining Loader resources; this does not claim the command run did so.
   eWorldRunProof(full,target,'full');
   assert.equal(full.sourceHashes?.[path],current[path],'Missing current full-world Loader resource: '+path);
   supplemental.push(path);
  }else assert.equal(bindings[path],current[path],'Different or missing E world candidate: '+target+' '+scenario+' '+path);
 }
 return supplemental.length?{resourceBindingsFrom:{result:`validations/conformance-world_read/result/${target}.json`,sources:supplemental,reason:'loader_resources_recorded_by_required_same_target_full_world_run_not_command_recorder'}}:{};
}

const worldListReadinessUpdates=new Map([
 ...['relations','boundary','transfer','providers'].map(kind=>[`components/validation_tools/conformance-world_read/task/world-debug-${kind}-actions.mjs`,{
  scenario:`world-debug-${kind}`,
  current:`t=>t.screenClass.endsWith('SelectWorldScreen')&&nodes(t).some(n=>n.worldName==='World-Read-Alpha'),'${kind==='providers'?'provider':kind==='relations'?'relation':kind} fixed world list populated'`,
  previous:`t=>t.screenClass.endsWith('SelectWorldScreen'),'${kind==='providers'?'provider':kind==='relations'?'relation':kind} fixed world list'`,
 }]),
 ['components/validation_tools/conformance-world_read/task/continuous-input-actions.mjs',{
  scenario:'continuous-input',
  current:"value => value.screenClass.endsWith('SelectWorldScreen') && nodes(value).some(row => row.worldName === 'World-Read-Alpha'), 'continuous world list populated'",
  previous:"value => value.screenClass.endsWith('SelectWorldScreen'), 'continuous world list'",
 }],
]);

export function eWorldUnexecutedReadinessUpdate(source,current,recordedDigest,scenario,result){
 if(source==='components/validation_tools/conformance-world_read/task/command-configuration-smoke.mjs'&&scenario==='player-command'){
  const identity='runtimeConnection:{runtimeInstanceId:connection.instanceId,port:connection.port,pid:connection.pid},',text=current.toString('utf8');
  // The standalone player-command recorder lists this file but never imports it.
  return current.equals(Buffer.from(text))&&text.split(identity).length===2&&sha256(Buffer.from(text.replace(identity,'')))===recordedDigest;
 }
 if(source==='components/validation_tools/conformance-vanilla_ui/task/ui-world-recovery-actions.mjs'&&scenario==='all'&&result?.status==='PASS'){
  const sample=result.navigationCompletionSamples?.find(row=>row.case==='world_recovery_optimize');
  const former="          assert(['UI_TARGET_INVALIDATED', 'UI_NODE_NOT_FOUND'].includes(error.code),\n            'Only native page replacement, not permission or other failures, can be an early-finish race');\n          value.cancelInputError = {code: error.code, message: error.message};";
  const diagnostic="          value.cancelInputError = {code: error.code, message: error.message,\n            ...(error.httpStatus ? {httpStatus: error.httpStatus} : {}), ...(error.nativeError ? {nativeError: error.nativeError} : {})};\n          if (!['UI_TARGET_INVALIDATED', 'UI_NODE_NOT_FOUND'].includes(error.code)) throw error;";
  const text=current.toString('utf8');
  // These passed races exercised only the same two accepted error codes. New error
  // diagnostics are not claimed for old results; native requests/admission are unchanged.
  return ['UI_TARGET_INVALIDATED','UI_NODE_NOT_FOUND'].includes(sample?.cancelInputError?.code)
   &&sample.returned?.callbackAction==='optimize_finished'&&sample.returned.storageAccessClosed===true&&sample.returned.directoryLockReacquiredAndClosed===true
   &&current.equals(Buffer.from(text))&&text.split(diagnostic).length===2&&sha256(Buffer.from(text.replace(diagnostic,former)))===recordedDigest;
 }
 const update=worldListReadinessUpdates.get(source);
 // The standalone command tools record all scenario files without importing them.
 // The shared runner imports continuous input, but calls it only in its named scenario.
 if(!update||scenario===update.scenario||!(['player-command','command-configuration'].includes(scenario)||update.scenario==='continuous-input'))return false;
 const text=current.toString('utf8');
 return current.equals(Buffer.from(text))&&text.split(update.current).length===2&&sha256(Buffer.from(text.replace(update.current,update.previous)))===recordedDigest;
}

export async function collectEWorldProof(repo,targets,sourceHashes){
 const proofs=[];
 for(const{target,scenario}of eWorldProofSlots(targets)){
  const ui=scenario==='all',path=ui?`validations/conformance-vanilla_ui/result/${target}.json`:`validations/conformance-world_read/result/${target}${scenario==='full'?'':'-'+scenario}.json`,bytes=await readFile(resolve(repo,path)),result=JSON.parse(bytes),proof=eWorldRunProof(result,target,scenario),bindings=result.sourceHashes??{};
  // Only current product facts, production and actually recorded tools prove this frozen candidate.
  const relevant=Object.keys(sourceHashes).filter(path=>path.startsWith('components/runtime_core/src/main/')||path.startsWith('components/runtime_safety/src/main/')||path==='components/protocol_schema/src/main/openapi/minecraft-control-v0.json'||['gradle.properties','settings.gradle.kts','gradle/libs.versions.toml',`versions/${target}/build.gradle.kts`,`versions/${target}/target.properties`].includes(path)||path.startsWith(`versions/${target}/src/main/`)||path.startsWith(`versions/${target}/src/client/`)).filter(path=>!ui||path.endsWith('.java')||path.endsWith('mixins.json')||path==='components/protocol_schema/src/main/openapi/minecraft-control-v0.json'||!path.includes('/src/main/')&&!path.includes('/src/client/'));
  const full=scenario==='player-command'?JSON.parse(await readFile(resolve(repo,`validations/conformance-world_read/result/${target}.json`))):undefined;
  const sourceProof=eWorldCandidateSourceProof(result,target,scenario,relevant,sourceHashes,full);
  const recordedToolUpdates=[];
  for(const[path,digest]of Object.entries(bindings)){
   if(path.includes('/src/test/'))continue;
   const current=await readFile(resolve(repo,path));
   if(sha256(current)!==digest){
    assert(eWorldUnexecutedReadinessUpdate(path,current,digest,scenario,result),'Changed E world executed binding: '+target+' '+scenario+' '+path);
    const update=worldListReadinessUpdates.get(path);
    recordedToolUpdates.push(path.endsWith('/command-configuration-smoke.mjs')?{source:path,unexecutedScenario:'command-configuration',reason:'exact_reverse_hash_of_unexecuted_configuration_receipt_identity_only'}:update?{source:path,unexecutedScenario:update.scenario,reason:'exact_reverse_hash_of_unexecuted_world_list_readiness_only'}
     :{source:path,reason:'exact_reverse_hash_of_optimizer_error_diagnostics_only',newDiagnosticsCovered:false,nativeRequestsAndSuccessAdmissionUnchanged:true});
   }
  }
  // Static function bindings avoid awaiting the still-evaluating CLI entry module.
  const validate={all:eUiRegressionProof,'input-context':inputContextProof,'continuous-input':continuousInputProof,'command-configuration':commandConfigurationProof,'world-placement-receipts':placementReceiptsProof}[scenario];if(validate)validate(result,target);
  proofs.push({target,scenario,result:path,resultSha256:sha256(bytes),sourceMatchesCurrent:true,...proof,...sourceProof,...(recordedToolUpdates.length?{recordedToolUpdates}:{})});
 }
 return{profile:E_WORLD_PROFILE,scope:E_WORLD_SCOPE,groups:E_WORLD_GROUPS,proofs,currentDevelopmentRuns:proofs.length,currentInstalledWorldRuns:targets.length,humanAcceptance:'NOT_PERFORMED',fullMilestonesComplete:false,persistentWriteCalls:0};
}

export async function verifyEWorldPublication(report,repo){
 assert.equal(report.publicationProfile,E_WORLD_PROFILE);assert.equal(report.nativeSmokeScope,'e-world-affected');assert.equal(report.fullNativeSmoke,false);assert.equal(report.fullMilestonesComplete,false);assert.equal(report.runtimeAcceptance,E_WORLD_SCOPE);assert.equal(report.delivery,'e_world_trial_with_known_performance_limit');assert.equal(report.performancePolicy,'DEFERRED_BY_USER_2026_10_20');
 const targets=report.artifacts.map(row=>row.target),sources=await candidateSource(repo,targets);assert.deepEqual(report.sourceHashes,sources);assert.equal(report.sourceFingerprint,sha256(JSON.stringify(sources)));assert.deepEqual(report.eWorldTrial,await collectEWorldProof(repo,targets,sources));
 const baseline=await readDependencyBaseline(report.historicalBaseline?.directory);assert.deepEqual(report.historicalBaseline,historicalTrialBaseline(baseline));assert.deepEqual(report.performanceAcceptance,dependencyPerformance(baseline));
 const installed=await collectInstalledProof(repo,report.artifacts,report.sourceFingerprint,{requireEnteredWorld:true});assert.equal(report.packagedRuntimeVerified,true);assert.equal(report.packagedRuntime.length,5);assert.equal(report.nativeSmoke.length,5);
 for(const actual of installed){const stored=report.packagedRuntime.filter(row=>row.target===actual.target),native=report.nativeSmoke.filter(row=>row.target===actual.target);assert.equal(stored.length,1);assert.equal(native.length,1);for(const[key,value]of Object.entries(actual))assert.deepEqual(stored[0][key],value);assert.equal(native[0].resultSha256,actual.resultSha256);assert.equal(native[0].enteredWorld,true);assert.equal(native[0].cleanShutdown,true);}
 return{scope:E_WORLD_SCOPE,currentDevelopmentRuns:report.eWorldTrial.currentDevelopmentRuns,currentInstalledWorldTargets:5,performanceAcceptance:report.performanceAcceptance,humanAcceptance:'NOT_PERFORMED',fullMilestonesComplete:false};
}
