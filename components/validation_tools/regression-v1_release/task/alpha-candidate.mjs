import {readFile,readdir} from 'node:fs/promises';
import {resolve,relative} from 'node:path';
import {createHash} from 'node:crypto';

export const sha256=bytes=>createHash('sha256').update(bytes).digest('hex');
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
