import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {execFile} from 'node:child_process';
import {promisify} from 'node:util';
import {access,cp,lstat,mkdir,readFile,readdir,realpath,writeFile} from 'node:fs/promises';
import {basename,join,resolve,sep} from 'node:path';

const exists=path=>access(path).then(()=>true,()=>false);
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
async function inventory(root,relative=''){
  const result=[];
  for(const entry of (await readdir(join(root,relative))).sort()){
    const name=relative?relative+'/'+entry:entry, path=join(root,name),info=await lstat(path);
    assert(!info.isSymbolicLink(),'Fixture links are forbidden');
    if(info.isDirectory())result.push(...await inventory(root,name));
    else {assert(info.isFile(),'Only ordinary fixture files');result.push({path:name,sha256:hash(await readFile(path))});}
  }
  return result;
}

// Caller holds its owned instance lock and has stopped the native server before every copy.
export async function prepareServerWorld({repo,game,world,target,facts,start,stop,validation='integration-server_peer'}){
  assert(['integration-server_peer','integration-extension_platform'].includes(validation),'Registered fixture validation required');
  const root=resolve(repo,'validations',validation,'instance');
  game=resolve(game);
  assert(game.startsWith(root+sep)&&basename(world)===world&&world!=='.'&&world!=='..','Explicit owned world required');
  const actualGame=await realpath(game), actualRoot=await realpath(root);
  assert(actualGame.startsWith(actualRoot+sep)&&actualGame.toLowerCase()===game.toLowerCase(),'Fixture parent must not redirect outside the owned directory');
  const baseline=join(game,'fixture-baseline'), manifest=join(baseline,'manifest.json'), source=join(game,world);
  const factsHash=hash(await readFile(facts));
  if(!await exists(manifest)){
    assert(!await exists(baseline),'Incomplete baseline retained for inspection');
    const preparation=await start();await stop(preparation);
    assert(await exists(join(source,'level.dat')),'Native preparation must save the owned world');
    const before=await inventory(source);await mkdir(baseline);
    await cp(source,join(baseline,'world'),{recursive:true,errorOnExist:true,force:false});
    const files=await inventory(join(baseline,'world'));assert.deepEqual(files,before);
    await writeFile(manifest,JSON.stringify({target,world,factsHash,files,hash:hash(JSON.stringify(files)),credentialsCopied:false},null,2)+'\n');
  }
  const value=JSON.parse(await readFile(manifest,'utf8'));
  assert.equal(value.target,target);assert.equal(value.world,world);assert.equal(value.factsHash,factsHash,'Target facts changed: retain baseline, explicitly prepare a new fixture');
  const files=await inventory(join(baseline,'world'));assert.deepEqual(files,value.files);assert.equal(hash(JSON.stringify(files)),value.hash);
  if(await exists(source)){
    assert(!(await lstat(source)).isSymbolicLink(),'World root must not be a link');
    await inventory(source); // Reject links before handing the exact, checked path to the Recycle Bin.
    const script="$ErrorActionPreference='Stop'; Add-Type -AssemblyName Microsoft.VisualBasic; [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($env:MINECRAFT_SERVER_FIXTURE_RECYCLE,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)";
    await promisify(execFile)('pwsh',['-NoProfile','-Command',script],{env:{...process.env,MINECRAFT_SERVER_FIXTURE_RECYCLE:source},windowsHide:true});
  }
  await cp(join(baseline,'world'),source,{recursive:true,errorOnExist:true,force:false});
  const restored=await inventory(source);assert.deepEqual(restored,value.files);
  return {policy:'immutable_baseline_restored',target,world,baselineSha256:value.hash,restoredSha256:hash(JSON.stringify(restored)),targetFactsSha256:factsHash,credentialsCopied:false};
}
