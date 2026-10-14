import { readFile, readdir, writeFile, stat } from 'node:fs/promises';
import { resolve, relative } from 'node:path';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';

const repo=resolve(import.meta.dirname,'../../../..');
const validation=process.argv.find(value=>value.startsWith('--validation='))?.slice('--validation='.length)??'conformance-world_read';
if(!['conformance-world_read','migration-runtime_core'].includes(validation))throw new Error('Unsupported Native smoke validation: '+validation);
const facts=Object.fromEntries((await readFile(resolve(repo,'gradle.properties'),'utf8')).split(/\r?\n/).filter(line=>line.startsWith('mod_')).map(line=>{const index=line.indexOf('=');return [line.slice(0,index),line.slice(index+1)];}));
const targets=[...(await readFile(resolve(repo,'settings.gradle.kts'),'utf8')).matchAll(/register\("([^"\r\n]+)"\)/g)].map(match=>match[1]);
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
const artifacts=[];
for(const target of targets){
  const name=`${facts.mod_id}-${target}-${facts.mod_version}.jar`;
  const collected=resolve(repo,'build/release',facts.mod_version,name);
  const native=resolve(repo,'versions',target,'build/libs',name);
  const bytes=await readFile(collected);
  if(digest(bytes)!==digest(await readFile(native)))throw new Error('Collected/native artifact mismatch: '+target);
  artifacts.push({target,path:relative(repo,collected).replaceAll('\\','/'),sha256:digest(bytes),bytes:bytes.length,builtAt:(await stat(native)).mtime.toISOString()});
}
const files=['gradle.properties','settings.gradle.kts','gradle/libs.versions.toml','components/protocol_schema/src/main/openapi/minecraft-control-v0.json'];
for(const component of ['runtime_safety','runtime_core'])files.push(`components/${component}/settings.gradle.kts`,`components/${component}/build.gradle.kts`);
for(const target of targets){
  files.push(`versions/${target}/build.gradle.kts`,`versions/${target}/target.properties`);
  for(const sourceSet of ['main','client']) {
    const root=resolve(repo,'versions',target,'src',sourceSet);
    for(const entry of await readdir(root,{recursive:true,withFileTypes:true}).catch(()=>[]))if(entry.isFile())files.push(relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/'));
  }
}
for(const component of ['runtime_safety','runtime_core'])for(const entry of await readdir(resolve(repo,'components',component,'src/main'),{recursive:true,withFileTypes:true}))if(entry.isFile())files.push(relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/'));
const sourceHashes={};for(const path of files.sort())sourceHashes[path]=digest(await readFile(resolve(repo,path)));
const head=execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).trim();
const dirty=execFileSync('git',['status','--porcelain=v1'],{cwd:repo,encoding:'utf8'}).trim().length>0;
const nativeSmoke=[];
for(const target of targets) {
  const path=`validations/${validation}/result/${target}.json`;
  const result=JSON.parse(await readFile(resolve(repo,path),'utf8'));
  let matches=true;
  for(const [source,hash] of Object.entries(result.sourceHashes??{}))if(digest(await readFile(resolve(repo,source)))!==hash)matches=false;
  if(result.status!=='PASS'||result.sourceChangedDuringRun||!result.cleanShutdown||!matches)throw new Error('Missing/current candidate Native smoke: '+target);
  nativeSmoke.push({target,result:path,checks:result.checks.length,evidence:result.evidence,sourceMatchesCurrent:true,cleanShutdown:result.cleanShutdown,fixturePolicy:result.fixturePolicy});
}
const contract=JSON.parse(await readFile(resolve(repo,'components/protocol_schema/src/main/openapi/minecraft-control-v0.json'),'utf8')).components.schemas.WorldReadContract.properties;
const report={type:'internal_alpha_build',preparedAt:new Date().toISOString(),commit:head,dirty,sourceFingerprint:digest(JSON.stringify(sourceHashes)),sourceHashes,modVersion:facts.mod_version,worldStage:contract.implementationStage.enum[0],fullWorldMilestone:contract.milestoneComplete.enum[0],consumerIsolationComplete:contract.consumerIsolationComplete.enum[0],artifactCheck:'collected_matches_native',runtimeAcceptance:'development Runtime only; not packaged artifact attestation',nativeSmoke,artifacts};
const output=resolve(repo,'build/alpha-manifest.json');
await writeFile(output,JSON.stringify(report,null,2)+'\n');
console.log('Prepared internal alpha manifest: '+output+' ('+artifacts.length+' JARs)');
