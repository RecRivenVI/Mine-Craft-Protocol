import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {createHash} from 'node:crypto';
import {candidateSource} from '../../regression-v1_release/task/alpha-candidate.mjs';
const repo=resolve(import.meta.dirname,'../../../..'),root=resolve(repo,'validations/integration-extension_platform/result');
const settings=await readFile(resolve(repo,'settings.gradle.kts'),'utf8');
const targets=[...settings.matchAll(/register\("([0-9.]+-(?:forge|neoforge|fabric))"\)/g)].map(match=>match[1]);
const facts=Object.fromEntries((await readFile(resolve(repo,'gradle.properties'),'utf8')).split(/\r?\n/).filter(line=>line.includes('=')).map(line=>{const at=line.indexOf('=');return[line.slice(0,at),line.slice(at+1)];}));
const digest=value=>createHash('sha256').update(value).digest('hex'),read=async name=>JSON.parse(await readFile(resolve(root,name),'utf8'));
const allSource=await candidateSource(repo,targets),rows=[];
for(const target of targets){
  const jar=await readFile(resolve(repo,'build/release',facts.mod_version,`${facts.mod_id}-${target}-${facts.mod_version}.jar`));
  const artifactSha256=digest(jar),source=await candidateSource(repo,[target]);
  const client=await read(target+'-installed-client.json'),clientExit=await read(target+'.json');
  const server=await read(target+'-installed-server.json'),serverExit=await read(target+'-installed-server-lifecycle.json');
  for(const [proof,side]of [[client,'CLIENT'],[server,'SERVER']]){
    assert.equal(proof.status,'PASS',target+' '+side);assert.equal(proof.finalJarInstall,true);assert.equal(proof.developmentClasspath,false);
    assert.equal(proof.artifactSha256,artifactSha256);assert.deepEqual(proof.sourceFiles,source,'Current product source must match: '+target+' '+side);
    assert.equal(proof.persistentWriteCalls,0);
    const catalog=proof.observations.find(item=>item.catalog).catalog;
    assert.equal(catalog.side,side);assert.equal(catalog.target,target);
    const hooks=proof.observations.find(item=>item.earlyHooks).earlyHooks;
    assert.equal(hooks.state,'validated');assert(hooks.configs.some(config=>config.registered&&config.appliedTargets.some(value=>value.includes('MinecraftServer'))));
    const sampled=proof.observations.find(item=>item.sampled).sampled.providers[0].data;
    assert.equal(sampled.adapterRevision,'managed_async_preparation_v3');assert(sampled.samples>0);assert(sampled.passiveNativeHookCalls>0);
    assert(!sampled.nativeHelperClassLoader.includes('ExtensionClassSpace'));
    assert(proof.observations.some(item=>item.cleanup?.cleanup.some(scope=>scope.complete&&scope.released&&scope.resources.length>0)));
  }
  assert.equal(clientExit.status,'PASS');assert.equal(clientExit.cleanShutdown,true);assert.equal(clientExit.noCrashReports,true);
  assert.equal(clientExit.artifactSha256,artifactSha256);assert.deepEqual(clientExit.sourceHashes,allSource);assert.equal(clientExit.sourceChangedDuringRun,false);
  assert.equal(serverExit.status,'PASS');assert.equal(serverExit.jarSha256,artifactSha256);assert.equal(serverExit.exitCode,0);assert.equal(serverExit.checks.normalShutdownRemovesEndpoint,true);
  assert(client.checks.some(item=>item.name.includes('input Lease lifetime')));assert(server.checks.some(item=>item.name.includes('requires no input Lease')));
  rows.push({target,status:'PASS',artifactSha256,clientRuntime:client.runtimeInstanceId,serverRuntime:server.runtimeInstanceId,packageIdentity:client.packageIdentity,serverPackageIdentity:server.packageIdentity});
}
const replacement=await read('26.3-neoforge-replacement.json');assert.equal(replacement.status,'PASS');assert.equal(replacement.hostJarUnchanged,true);
await writeFile(resolve(root,'bridge-matrix.json'),JSON.stringify({status:'PASS',scope:'EP2_current_final_jars_client_and_dedicated_server_not_seven_mod_acceptance',rows,sourceFingerprint:digest(JSON.stringify(allSource)),sourceHashes:allSource,
  replacement:{proof:'26.3-neoforge-replacement.json',artifactSha256:replacement.artifactSha256,currentMatrixArtifact:rows.find(row=>row.target==='26.3-neoforge').artifactSha256,basis:'independent_earlier_ep2_host_binary_not_current_frozen_alpha_proof'},
  completedAt:new Date().toISOString(),humanAcceptance:'PENDING',persistentWriteCalls:0},null,2)+'\n');
console.log('PASS: five current final JARs, real early hooks, client/server, MCP/grant/cleanup; no Alpha published');
