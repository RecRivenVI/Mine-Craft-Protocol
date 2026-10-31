import {readFile,readdir} from 'node:fs/promises';
import {resolve,relative} from 'node:path';
import {createHash} from 'node:crypto';

export const sha256=bytes=>createHash('sha256').update(bytes).digest('hex');
export async function extensionInputArtifacts(instance){
  const result={};
  const entries=await readdir(resolve(instance,'mods'),{withFileTypes:true});
  for(const entry of entries.sort((a,b)=>a.name.localeCompare(b.name))){
    if(!entry.name.startsWith('extension-input-')||!entry.name.endsWith('.jar'))continue;
    if(!entry.isFile()||entry.isSymbolicLink())throw new Error('Declared extension input must be a regular JAR');
    result[entry.name]=sha256(await readFile(resolve(instance,'mods',entry.name)));
  }
  return result;
}
export async function extensionPackageSource(repo,packages){
  const ids=[...new Set(packages)].sort();
  if(ids.some(id=>!/^[a-z][a-z0-9_]*$/.test(id)))throw new Error('Registered package source ID required');
  const base='components/extension_packages';
  const files=[`${base}/build.gradle.kts`,`${base}/settings.gradle.kts`,`${base}/native-classpaths.gradle`];
  for(const directory of ['src/main','scripts',...ids.map(id=>'packages/'+id)]){
    for(const entry of await readdir(resolve(repo,base,directory),{recursive:true,withFileTypes:true})){
      if(entry.isSymbolicLink())throw new Error('Package sources cannot be symbolic links');
      if(entry.isFile())files.push(relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/'));
    }
  }
  const hashes={};for(const path of files.sort())hashes[path]=sha256(await readFile(resolve(repo,path)));return hashes;
}
export async function candidateSource(repo,targets){
  const files=['gradle.properties','settings.gradle.kts','gradle/libs.versions.toml','components/protocol_schema/src/main/openapi/minecraft-control-v0.json'];
  for(const component of ['runtime_safety','runtime_core']){
    files.push(`components/${component}/settings.gradle.kts`,`components/${component}/build.gradle.kts`);
    for(const entry of await readdir(resolve(repo,'components',component,'src/main'),{recursive:true,withFileTypes:true}))if(entry.isFile())files.push(relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/'));
  }
  for(const target of targets){
    files.push(`versions/${target}/build.gradle.kts`,`versions/${target}/target.properties`);
    for(const sourceSet of ['main','client'])for(const entry of await readdir(resolve(repo,'versions',target,'src',sourceSet),{recursive:true,withFileTypes:true}).catch(()=>[]))if(entry.isFile())files.push(relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/'));
  }
  const hashes={};for(const path of files.sort())hashes[path]=sha256(await readFile(resolve(repo,path)));return hashes;
}
