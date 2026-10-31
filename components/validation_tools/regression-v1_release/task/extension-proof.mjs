import assert from 'node:assert/strict';
import {readFile,readdir} from 'node:fs/promises';
import {resolve,relative} from 'node:path';
import {candidateSource,extensionPackageSource,sha256} from './alpha-candidate.mjs';

export const EXTENSION_PROFILE='extension_platform_trial_v1';
const base='validations/integration-extension_platform/result/';
export async function currentHashes(repo,hashes){
  assert(hashes&&Object.keys(hashes).length>0,'Missing source/tool identity');
  for(const [path,hash] of Object.entries(hashes)){
    assert(!path.includes('\\')&&!path.includes(':')&&!path.startsWith('/')&&!path.split('/').includes('..'));
    assert.match(hash,/^[0-9a-f]{64}$/);
    assert.equal(sha256(await readFile(resolve(repo,path))),hash,'Stale executed source/tool: '+path);
  }
}
export async function currentPackageIdentity(repo,id){
  assert.match(id,/^[a-z][a-z0-9_]*$/);
  const root=resolve(repo,'components/extension_packages/build/packages',id);
  const bytes=await readFile(resolve(root,'extension.json')),manifest=JSON.parse(bytes);
  assert.equal(manifest.id,id);
  const entries=await readdir(root,{recursive:true,withFileTypes:true});
  assert(entries.every(item=>!item.isSymbolicLink()),'Distributed package cannot contain links');
  const files=entries.filter(item=>item.isFile()).map(item=>relative(root,resolve(item.parentPath,item.name)).replaceAll('\\','/')).sort();
  assert.deepEqual(files,['extension.json',...Object.keys(manifest.files)].sort(),'Distributed package file index must be complete');
  await currentHashes(root,manifest.files);
  return sha256(bytes);
}
export async function collectExtensionProof(repo,artifacts){
  const rows=[];
  async function read(path){const bytes=await readFile(resolve(repo,path));return {value:JSON.parse(bytes),proof:{result:path,resultSha256:sha256(bytes)}};}
  async function platform(target,suffix,ids,installed=false){
    const {value,proof}=await read(base+target+suffix+'.json');
    assert.equal(value.target,target);assert.equal(value.status,'PASS');assert.equal(value.persistentWriteCalls,0);
    assert.deepEqual(value.sourceFiles,await candidateSource(repo,[target]));
    assert.deepEqual(value.packageSourceFiles,await extensionPackageSource(repo,ids));
    await currentHashes(repo,value.toolFiles);
    const catalog=value.observations.find(item=>item.catalog)?.catalog;assert(catalog,'Missing actual installed package catalog');
    for(const id of ids)assert.equal(catalog.packages.find(item=>item.packageId===id)?.contentIdentity,await currentPackageIdentity(repo,id),'Published package differs from actual installed code: '+target+'/'+id);
    if(suffix.startsWith('-installed-server-')){
      const tool=value.observations.find(item=>item.serverAdapterTool)?.serverAdapterTool;assert(tool,'Missing dedicated adapter tool provenance');
      await currentHashes(repo,{[tool.path]:tool.sha256});
    }
    assert(value.checks.length>=3&&value.checks.every(item=>item.status==='PASS'));
    if(installed){assert.equal(value.developmentClasspath,false);assert.equal(value.finalJarInstall,true);assert.equal(value.artifactSha256,artifacts.find(item=>item.target===target).sha256);}
    else assert.equal(value.cleanShutdown,true);
    rows.push({...proof,target,checks:value.checks.length,scope:'extension_platform',runtimeInstanceId:value.runtimeInstanceId});
    return value;
  }
  async function installedExit(target,suffix,server=false){
    const {value,proof}=await read(base+target+suffix+'.json');
    assert.equal(value.target,target);assert.equal(value.status,'PASS');assert.equal(value.persistentWriteCalls,0);
    assert.equal(server?value.jarSha256:value.artifactSha256,artifacts.find(item=>item.target===target).sha256);
    assert.equal(value.exitCode,0);
    if(server){assert.equal(value.checks.normalShutdownRemovesEndpoint,true);assert.deepEqual(value.sourceHashes,await candidateSource(repo,[target]));await currentHashes(repo,value.toolHashes);}
    else {assert.equal(value.cleanShutdown,true);assert.equal(value.noCrashReports,true);assert.equal(value.sourceChangedDuringRun,false);await currentHashes(repo,value.sourceHashes);await currentHashes(repo,value.toolHashes);}
    rows.push({...proof,target,scope:'installed_lifecycle'});
  }
  for(const artifact of artifacts){
    const target=artifact.target;
    await platform(target,'-client',['platform_probe']);
    await platform(target,'-installed-client',['platform_probe'],true);
    await installedExit(target,'');
    await platform(target,'-installed-server',['platform_probe'],true);
    await installedExit(target,'-installed-server-lifecycle',true);
    for(const [folder,suffix,scenario] of [['conformance-world_read','','full'],['conformance-vanilla_ui','','all'],['conformance-world_read','-input-context','input-context'],['conformance-world_read','-continuous-correctness','continuous-correctness']]){
      const {value,proof}=await read(`validations/${folder}/result/${target}${suffix}.json`);
      assert.equal(value.target,target);assert.equal(value.status,'PASS');assert.equal(value.scenario,scenario);assert.equal(value.cleanShutdown,true);assert.equal(value.sourceChangedDuringRun,false);assert.equal(value.persistentWriteCalls,0);
      assert(value.checks.length>0&&value.checks.every(item=>item.status==='PASS'));
      await currentHashes(repo,value.sourceHashes);
      const product=await candidateSource(repo,[target]);
      for(const [path,hash] of Object.entries(product))if(path.endsWith('.java')||path.startsWith(`versions/${target}/src/`)||path.endsWith('/target.properties'))assert.equal(value.sourceHashes[path],hash,'Regression does not bind current production: '+path);
      if(scenario==='continuous-correctness'){
        const {continuousCorrectnessProof}=await import('./dp-freeze-proof.mjs');continuousCorrectnessProof(value);
      }
      rows.push({...proof,target,scope:scenario,checks:value.checks.length});
    }
  }
  const lifetime=await platform('26.3-neoforge','-client-lifetimes',['platform_probe','lifetime_left','lifetime_right']);
  for(const required of ['independent_input_failure','independent_grant_failure'])assert.equal(lifetime.observations.filter(item=>item.case===required).length,2);
  assert(lifetime.observations.some(item=>item.nativeWorldExitResources));
  const replacement=await read(base+'26.3-neoforge-replacement.json');
  assert.equal(replacement.value.status,'PASS');assert.equal(replacement.value.hostJarUnchanged,true);assert.equal(replacement.value.nativeRestart,true);
  assert.equal(replacement.value.artifactSha256,artifacts.find(item=>item.target==='26.3-neoforge').sha256);
  rows.push({...replacement.proof,scope:'same_final_host_package_replacement'});
  const adapters=[],limitations=[];
  const ids=JSON.parse(await readFile(resolve(repo,'components/extension_packages/packages/index.json'),'utf8'));
  for(const id of ids){
    const spec=JSON.parse(await readFile(resolve(repo,'components/extension_packages/packages',id,'package.json'),'utf8'));
    for(const target of spec.targets){
      if(id==='computercraft'&&target==='26.3-neoforge'){
        limitations.push({packageId:id,target,status:'UPSTREAM_BLOCKED',reason:'Pinned CC 1.120.3 references removed ModConfig.Type.SERVER before Runtime startup; no platform business PASS.'});continue;
      }
      const installed=target==='1.20.1-forge';
      const value=await platform(target,(installed?'-installed':'')+'-client-'+id,['platform_probe',id],installed);
      if(installed)await installedExit(target,'-'+id);
      adapters.push({packageId:id,target,status:'PASS',scope:'finite_named_business_scenario'});
      if(id==='littletiles'){
        const input=value.observations.find(item=>item.case==='littletiles_normal_door_input');
        limitations.push({packageId:id,target,status:input?.doorBusinessStatus??'NOT_CLOSED',closeStatus:value.observations.find(item=>item.case==='littletiles_axis_return')?.status??'SEE_RESULT',reason:'Static native micro geometry and callback diagnosis; moving-animation parent pick/complete open-close remains preview.'});
      }
      if(['ae2','create','littletiles'].includes(id)){
        await platform(target,'-installed-server-'+id,['platform_probe',id],true);
        await installedExit(target,'-installed-server-lifecycle-'+id,true);
      }
    }
    if(!['ae2','create','littletiles'].includes(id)){
      await platform('1.21.1-neoforge','-installed-server-'+id,['platform_probe',id],true);
      await installedExit('1.21.1-neoforge','-installed-server-lifecycle-'+id,true);
    }
  }
  return {scope:'extension_platform_current_candidate_five_targets_and_available_pinned_adapters',proofs:rows,adapters,limitations,humanAcceptance:'NOT_PERFORMED',performanceMeasured:false,persistentWriteCalls:0};
}

export async function verifyExtensionPublication(report,repo){
  assert.equal(report.publicationProfile,EXTENSION_PROFILE);assert.equal(report.fullMilestonesComplete,false);
  await currentHashes(repo,report.extensionSourceHashes);await currentHashes(repo,report.consumerSourceHashes);
  assert.deepEqual(report.extensionPlatform,await collectExtensionProof(repo,report.artifacts));
  for(const file of report.additionalArtifacts??[]){assert.match(file.fileName,/^(?:extensions|companion)\/[A-Za-z0-9_./-]+$/);assert(!file.fileName.split('/').includes('..'));assert.equal(sha256(await readFile(resolve(repo,file.path))),file.sha256);}
  assert(report.additionalArtifacts.some(file=>file.fileName.endsWith('-extension-sdk.jar')));
  for(const id of ['computercraft','create','ae2','emi','exposure','modern_ui','littletiles'])assert(report.additionalArtifacts.some(file=>file.fileName===`extensions/packages/${id}/extension.json`));
  assert(report.additionalArtifacts.some(file=>file.fileName==='companion/dist/index.js'));
  return {performanceAcceptance:{status:'NOT_PERFORMED'}};
}
