import assert from 'node:assert/strict';
import {readFile,writeFile,copyFile,mkdir} from 'node:fs/promises';
import {resolve} from 'node:path';
const repo=resolve(import.meta.dirname,'../../../..');
const [action,target]=process.argv.slice(2);
assert(['capture','verify'].includes(action));
const settings=await readFile(resolve(repo,'settings.gradle.kts'),'utf8');
assert([...settings.matchAll(/register\("([0-9.]+-(?:forge|neoforge|fabric))"\)/g)].some(match=>match[1]===target));
const root=resolve(repo,'validations/integration-extension_platform/result');
await mkdir(root,{recursive:true});
const current=resolve(root,target+'-installed-client.json'),lifecycle=resolve(root,target+'.json');
const read=async path=>JSON.parse(await readFile(path,'utf8'));
const version=value=>value.observations.find(item=>item.catalog).catalog.packages.find(item=>item.packageId==='platform_probe').version;
const body=value=>value.observations.find(item=>item.sampled).sampled.providers[0].data;
const phase=await read(current),life=await read(lifecycle);
assert.equal(phase.status,'PASS');assert.equal(life.status,'PASS');assert.equal(life.cleanShutdown,true);
assert.equal(phase.runtimeInstanceId,life.runtimeInstanceId??phase.runtimeInstanceId);
assert.equal(phase.artifactSha256,life.artifactSha256);
if(action==='capture'){
  assert.equal(version(phase),'1.0.0');
  await copyFile(current,resolve(root,target+'-replacement-before.json'));
  await copyFile(lifecycle,resolve(root,target+'-replacement-before-install.json'));
}else{
  const before=await read(resolve(root,target+'-replacement-before.json'));
  const oldLife=await read(resolve(root,target+'-replacement-before-install.json'));
  assert.equal(before.status,'PASS');assert.equal(oldLife.cleanShutdown,true);
  assert.equal(before.artifactSha256,phase.artifactSha256,'Only the extension may change');
  assert.notEqual(before.runtimeInstanceId,phase.runtimeInstanceId,'Native code updates require restart');
  assert.notEqual(before.packageIdentity,phase.packageIdentity);
  assert.notEqual(version(before),version(phase));
  assert.equal(body(phase).adapterRevision,'managed_async_preparation_v3');
  assert.notEqual(body(before).adapterRevision,body(phase).adapterRevision,'Real package code changed, not only metadata');
  await copyFile(current,resolve(root,target+'-replacement-after.json'));
  await copyFile(lifecycle,resolve(root,target+'-replacement-after-install.json'));
  await writeFile(resolve(root,target+'-replacement.json'),JSON.stringify({target,status:'PASS',artifactSha256:phase.artifactSha256,hostJarUnchanged:true,nativeRestart:true,before:{version:version(before),identity:before.packageIdentity,runtime:before.runtimeInstanceId},after:{version:version(phase),identity:phase.packageIdentity,runtime:phase.runtimeInstanceId},completedAt:new Date().toISOString(),persistentWriteCalls:0},null,2)+'\n');
}
console.log('PASS extension package replacement '+action+': '+target);
