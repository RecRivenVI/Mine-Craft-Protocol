import {readFile, readdir, mkdir, cp, writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
const repo=resolve(import.meta.dirname,'../../../..');
const target=process.argv.find(arg=>arg.startsWith('--target='))?.slice(9);
const registered=[...(await readFile(resolve(repo,'settings.gradle.kts'),'utf8')).matchAll(/register\("([0-9.]+-(?:forge|neoforge|fabric))"\)/g)].map(match=>match[1]);
if(!registered.includes(target))throw new Error('Registered Target required');
const flags=Object.fromEntries(process.argv.slice(2).map(arg=>arg.replace(/^--/,'').split('=')));
const role=flags.role??'client';if(!['client','server'].includes(role))throw new Error('Invalid role');
const root=resolve(repo,`validations/integration-extension_platform/instance/${flags.installed==='true'?'installed/':''}${target}/${role}/minecraft-protocol/extensions`);
const source=resolve(repo,'components/extension_packages/build/packages/platform_probe');
const bytes=await readFile(resolve(source,'extension.json')), manifest=JSON.parse(bytes), hash=createHash('sha256').update(bytes).digest('hex');
const installed=resolve(root,'packages/platform_probe');
if(await readdir(installed).then(()=>true,error=>{if(error.code==='ENOENT')return false;throw error;})) {
  // Only this validation's fixed owned package is recycled; worlds and credentials are retained.
  const script=`Add-Type -AssemblyName Microsoft.VisualBasic; [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory('${installed.replaceAll("'","''")}', [Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs, [Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)`;
  execFileSync('pwsh',['-NoProfile','-Command',script],{stdio:'inherit'});
}
await mkdir(resolve(root,'packages'),{recursive:true});
await cp(source,installed,{recursive:true,errorOnExist:true,force:false});
await writeFile(resolve(root,'approvals.json'),JSON.stringify({formatVersion:1,packages:{platform_probe:{contentIdentity:hash,scopes:manifest.requestedScopes}}},null,2)+'\n');
console.log(`Installed approved test package platform_probe ${hash.slice(0,12)}`);
