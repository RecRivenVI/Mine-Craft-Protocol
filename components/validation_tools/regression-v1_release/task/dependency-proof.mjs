import assert from 'node:assert/strict';
import {readFile,readdir,lstat} from 'node:fs/promises';
import {resolve,basename} from 'node:path';
import {execFileSync} from 'node:child_process';
import {candidateSource,sha256} from './alpha-candidate.mjs';
import {collectInstalledProof} from '../../integration-packaged_runtime/task/installed-evidence.mjs';

export const DEPENDENCY_PROFILE='dependency_update_trial_v1';
export const DEPENDENCY_SCOPE='dependency_update_installed_world_smoke';
export const DEPENDENCY_BASELINE=Object.freeze({
 name:'20261020-0337-f2dc15c9',
 sourceFingerprint:'f2dc15c927ca7bf77dd7a0ffee52e605718366d4e16b1433319a24101fdcadf3',
 manifestSha256:'dea8f2285d2f0d05ff1f92c656c1b09ec6e81f1247094767e56a6e06fc0313a8'
});
const TARGETS=['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-fabric','26.3-neoforge'];
const CHANGES={
 'versions/1.21.1-neoforge/target.properties':['loader_version=21.1.259','loader_version=21.1.264'],
 'versions/26.1.2-neoforge/target.properties':['loader_version=26.1.2.117','loader_version=26.1.2.122'],
 'versions/26.3-neoforge/target.properties':['loader_version=26.3.0.77','loader_version=26.3.0.94'],
 'versions/26.3-fabric/target.properties':['fabric_api_version=0.162.0+26.3','fabric_api_version=0.164.0+26.3'],
 'gradle/libs.versions.toml':['loom = "1.18.3"','loom = "1.19.1"']
};
const targetSet=rows=>assert.deepEqual(rows.map(row=>row.target).sort(),TARGETS,'Dependency trial requires the unchanged five Targets');
function sourceFingerprint(hashes){
 assert(hashes&&typeof hashes==='object'&&!Array.isArray(hashes));
 for(const[path,digest]of Object.entries(hashes)){
  assert(!path.includes('\\')&&!path.includes(':')&&!path.split('/').some(part=>!part||part==='.'||part==='..'));
  assert.match(digest,/^[0-9a-f]{64}$/);
 }
 return sha256(JSON.stringify(hashes));
}
function group(rows,scenarios){
 assert.equal(rows?.length,TARGETS.length*scenarios.length);
 assert.deepEqual(rows.map(row=>row.target+':'+row.scenario).sort(),TARGETS.flatMap(target=>scenarios.map(scenario=>target+':'+scenario)).sort());
 for(const row of rows)assert.match(row.resultSha256,/^[0-9a-f]{64}$/);
}

// Production pins the already published batch. The identity parameter permits an isolated unit fixture.
export async function readDependencyBaseline(directory,identity=DEPENDENCY_BASELINE){
 directory=resolve(directory);assert.equal(basename(directory),identity.name,'Unexpected dependency baseline');
 const manifestBytes=await readFile(resolve(directory,'alpha-manifest.json'));
 assert.equal(sha256(manifestBytes),identity.manifestSha256,'Published baseline manifest changed');
 const report=JSON.parse(manifestBytes);
 assert.equal(report.type,'internal_alpha_build');assert.equal(report.publicationProfile,'continuous_gameplay_trial_v1');
 assert.equal(report.sourceFingerprint,identity.sourceFingerprint);assert.equal(sourceFingerprint(report.sourceHashes),identity.sourceFingerprint);
 assert.equal(report.nativeSmokeScope,'full');assert.equal(report.fullNativeSmoke,true);assert.equal(report.fullMilestonesComplete,false);
 assert.equal(report.continuousGameplay.readyFor,'trial_not_goal_acceptance');assert.equal(report.performanceAcceptance.status,'NOT_MET');assert.equal(report.performanceAcceptance.goalPerformancePass,false);
 targetSet(report.artifacts);for(const key of ['nativeSmoke','uiRegression','continuousInput','packagedRuntime']){targetSet(report[key]);for(const row of report[key])assert.match(row.resultSha256,/^[0-9a-f]{64}$/);}
 group(report.controlCollaboration,['mode-collaboration','command-discovery-batch','resource-version','server-completion']);
 group(report.continuousGameplay.proofs,['input-context','region-stream','world-placement-receipts','continuous-performance','interaction-baseline']);
 const files=[{fileName:'alpha-manifest.json',sha256:identity.manifestSha256}];
 for(const artifact of report.artifacts){
  assert.match(artifact.fileName,/^[A-Za-z0-9_.-]+\.jar$/);assert.match(artifact.sha256,/^[0-9a-f]{64}$/);
  const installed=report.packagedRuntime.find(row=>row.target===artifact.target);assert.equal(installed.artifactSha256,artifact.sha256);assert.equal(installed.sourceFingerprint,identity.sourceFingerprint);
  files.push({fileName:artifact.fileName,sha256:artifact.sha256});
 }
 assert.equal(report.agentGuide.fileName,'AGENT_GUIDE.md');assert.match(report.agentGuide.sha256,/^[0-9a-f]{64}$/);files.push({fileName:'AGENT_GUIDE.md',sha256:report.agentGuide.sha256});
 assert.equal(new Set(files.map(row=>row.fileName)).size,7);assert.deepEqual((await readdir(directory)).sort(),files.map(row=>row.fileName).sort());
 assert(!(await lstat(directory)).isSymbolicLink());
 for(const file of files){const path=resolve(directory,file.fileName);assert((await lstat(path)).isFile()&&!(await lstat(path)).isSymbolicLink());assert.equal(sha256(await readFile(path)),file.sha256,'Published baseline file changed: '+file.fileName);}
 return{directory,report,files};
}

export async function dependencySourceProof(repo,baseline,currentSources,readOriginal){
 const old=baseline.report,changes=[];assert.equal(sourceFingerprint(old.sourceHashes),old.sourceFingerprint);sourceFingerprint(currentSources);
 assert.deepEqual(Object.keys(currentSources).sort(),Object.keys(old.sourceHashes).sort(),'Product source set changed');
 const original=readOriginal??(path=>execFileSync('git',['show',old.commit+':'+path],{cwd:repo}));
 for(const[path,beforeSha256]of Object.entries(old.sourceHashes)){
  const bytes=await readFile(resolve(repo,path)),afterSha256=sha256(bytes);assert.equal(currentSources[path],afterSha256,'Current candidate source changed: '+path);
  if(beforeSha256===afterSha256)continue;
  assert(Object.hasOwn(CHANGES,path),'Product/source change is outside the dependency update: '+path);
  const beforeBytes=await original(path);assert.equal(sha256(beforeBytes),beforeSha256,'Baseline Git facts differ from the published source map: '+path);
  const before=beforeBytes.toString('utf8'),after=bytes.toString('utf8'),[from,to]=CHANGES[path],lines=before.split('\n');
  assert.equal(lines.filter(line=>line===from).length,1,'Unexpected old dependency fact: '+path);
  assert.equal(after,lines.map(line=>line===from?to:line).join('\n'),'Only the declared dependency value may change: '+path);
  changes.push({source:path,before:from,after:to,beforeSha256,afterSha256});
 }
 const companion=[];
 for(const path of ['components/companion/package.json','components/companion/package-lock.json']){
  const beforeBytes=await original(path),afterBytes=await readFile(resolve(repo,path)),before=JSON.parse(beforeBytes),after=JSON.parse(afterBytes),restored=structuredClone(after);
  if(path.endsWith('/package.json')){assert.equal(before.dependencies.zod,'4.6.5');assert.equal(after.dependencies.zod,'4.6.7');restored.dependencies.zod=before.dependencies.zod;}
  else{
   assert.equal(before.packages[''].dependencies.zod,'4.6.5');assert.equal(after.packages[''].dependencies.zod,'4.6.7');restored.packages[''].dependencies.zod='4.6.5';
   const node=after.packages['node_modules/zod'],oldNode=before.packages['node_modules/zod'];assert.equal(oldNode.version,'4.6.5');assert.equal(node.version,'4.6.7');assert.equal(node.resolved,'https://registry.npmjs.org/zod/-/zod-4.6.7.tgz');assert.match(node.integrity,/^sha512-[A-Za-z0-9+/]+={0,2}$/);
   for(const key of ['version','resolved','integrity'])restored.packages['node_modules/zod'][key]=oldNode[key];
  }
  assert.deepEqual(restored,before,'Companion changes must be limited to Zod 4.6.5 to 4.6.7');
  companion.push({source:path,beforeSha256:sha256(beforeBytes),sha256:sha256(afterBytes)});
 }
 return{directory:baseline.directory,manifestSha256:sha256(await readFile(resolve(baseline.directory,'alpha-manifest.json'))),sourceFingerprint:old.sourceFingerprint,commit:old.commit,files:baseline.files,
  historicalOnly:true,historicalLogicalSlots:65,currentFullMatrixClaimed:false,changes,companion:{dependency:'zod',before:'4.6.5',after:'4.6.7',files:companion,verificationEvidence:'repository_check_build_and_unit_test_logs_not_embedded_here',liveMcpAcceptanceClaimed:false}};
}

export function dependencyPerformance(baseline){return{status:'NOT_MET',origin:'immutable_baseline_historical_only',newMeasurements:false,currentPolicy:'DEFERRED_BY_USER_2026_10_20',goalPerformancePass:false,historical:baseline.report.performanceAcceptance};}

export function historicalTrialBaseline(baseline){return{directory:baseline.directory,manifestSha256:baseline.files.find(file=>file.fileName==='alpha-manifest.json').sha256,sourceFingerprint:baseline.report.sourceFingerprint,historicalOnly:true,historicalLogicalSlots:65,currentFullMatrixClaimed:false,files:baseline.files};}

export async function verifyDependencyPublication(report,repo){
 assert.equal(report.publicationProfile,DEPENDENCY_PROFILE);assert.equal(report.nativeSmokeScope,'packaged-runtime');assert.equal(report.runtimeAcceptance,DEPENDENCY_SCOPE);assert.equal(report.fullNativeSmoke,false);assert.equal(report.fullMilestonesComplete,false);assert.equal(report.delivery,'dependency_update_trial');
 assert(!report.continuousGameplay&&!report.uiRegression&&!report.controlCollaboration&&!report.continuousInput,'Historical matrix must not appear as current-candidate acceptance');
 targetSet(report.artifacts);assert.equal(report.packagedRuntimeVerified,true);
 const actualSources=await candidateSource(repo,report.artifacts.map(row=>row.target));assert.deepEqual(report.sourceHashes,actualSources);assert.equal(report.sourceFingerprint,sourceFingerprint(actualSources));
 const baseline=await readDependencyBaseline(report.dependencyBaseline?.directory);
 assert.deepEqual(report.dependencyBaseline,await dependencySourceProof(repo,baseline,actualSources));assert.deepEqual(report.performanceAcceptance,dependencyPerformance(baseline));
 const proofs=await collectInstalledProof(repo,report.artifacts,report.sourceFingerprint,{requireEnteredWorld:true});targetSet(report.packagedRuntime);targetSet(report.nativeSmoke);
 for(const proof of proofs){assert.equal(proof.enteredWorld,true);assert.equal(proof.worldInput.currentTargetFactsSha256,actualSources[`versions/${proof.target}/target.properties`]);const stored=report.packagedRuntime.find(row=>row.target===proof.target);for(const[key,value]of Object.entries(proof))assert.deepEqual(stored[key],value,'Changed installed-world proof: '+key);const native=report.nativeSmoke.find(row=>row.target===proof.target);assert.equal(native.result,proof.result);assert.equal(native.resultSha256,proof.resultSha256);assert.equal(native.fullSmoke,false);assert.equal(native.enteredWorld,true);assert.equal(native.sourceMatchesCurrent,true);assert.equal(native.cleanShutdown,true);}
 return{scope:DEPENDENCY_SCOPE,performanceAcceptance:report.performanceAcceptance,baselineHistoricalOnly:true,currentInstalledWorldTargets:5};
}
