import assert from 'node:assert/strict';
import {readFile,writeFile,readdir,lstat} from 'node:fs/promises';
import {resolve,relative} from 'node:path';
import {createHash} from 'node:crypto';

const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
const marker='official-server-installation.json';
function pathIn(game,path){
  assert(!path.includes(':')&&!path.startsWith('/')&&!path.split(/[\\/]/).includes('..'));
  const normalized=path.replaceAll('\\','/');assert(normalized.startsWith('libraries/'));
  return resolve(game,normalized);
}
async function file(game,path){
  const value=pathIn(game,path),info=await lstat(value);
  assert(info.isFile()&&!info.isSymbolicLink()&&info.size>0,'Incomplete official server file: '+path);
  return readFile(value);
}
async function argumentsFile(game,argsFile){
  const bytes=await file(game,argsFile);
  const required=[...new Set([...bytes.toString('utf8').matchAll(/libraries[\\/][^\s;"']+\.jar/g)].map(item=>item[0].replaceAll('\\','/')))];
  assert(required.length>0,'Official server arguments contain no library paths');
  for(const path of required)await file(game,path);
  return {bytes,required};
}
export async function serverInstallationReady(game,argsFile,installerSha256){
  try{
    const stamp=JSON.parse(await readFile(resolve(game,marker),'utf8'));
    assert.equal(stamp.formatVersion,1);assert.equal(stamp.installerSha256,installerSha256);
    assert.equal(stamp.argsFile,argsFile.replaceAll('\\','/'));
    const args=await argumentsFile(game,argsFile);assert.equal(stamp.argsSha256,digest(args.bytes));
    for(const path of args.required)assert(Object.hasOwn(stamp.libraries,path));
    for(const [path,hash]of Object.entries(stamp.libraries))assert.equal(digest(await file(game,path)),hash);
    return true;
  }catch(error){if(error.code==='ENOENT'||error instanceof assert.AssertionError||error instanceof SyntaxError)return false;throw error;}
}
export async function recordServerInstallation(game,argsFile,installerSha256){
  assert.match(installerSha256,/^[0-9a-f]{64}$/);
  const args=await argumentsFile(game,argsFile),libraries={};
  for(const entry of await readdir(resolve(game,'libraries'),{recursive:true,withFileTypes:true})){
    assert(!entry.isSymbolicLink(),'Official installation cannot contain links');
    if(entry.isFile()&&entry.name.endsWith('.jar')){
      const path=relative(game,resolve(entry.parentPath,entry.name)).replaceAll('\\','/');
      libraries[path]=digest(await file(game,path));
    }
  }
  // Written only after the official installer exited successfully and all declared inputs exist.
  await writeFile(resolve(game,marker),JSON.stringify({formatVersion:1,installerSha256,argsFile:argsFile.replaceAll('\\','/'),argsSha256:digest(args.bytes),libraries},null,2)+'\n');
}
