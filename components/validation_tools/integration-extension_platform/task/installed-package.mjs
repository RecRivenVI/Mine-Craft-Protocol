import assert from 'node:assert/strict';
import {readFile,writeFile,rename,access,lstat} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';

// Only the registered extension validation can request this installation; no Runtime token path.
export async function preparePlatformPackage({repo,target,game,role,adapter='platform_probe'}) {
  assert(['client','server'].includes(role));
  const expected=resolve(repo,'validations/integration-extension_platform/instance/installed',target,role);
  assert.equal(resolve(game),expected);
  const owner=join(game,'extension-platform-owner.json');
  if(await access(owner).then(()=>true,()=>false)){const value=JSON.parse(await readFile(owner,'utf8'));assert.equal(value.owner,'extension-platform');assert.equal(value.target,target);}
  else await writeFile(owner,JSON.stringify({owner:'extension-platform',target})+'\n',{flag:'wx'});
  execFileSync(process.execPath,[resolve(import.meta.dirname,'install-platform-probe.mjs'),'--target='+target,'--role='+role,'--installed=true'],{cwd:repo,stdio:'inherit'});
  if(adapter!=='platform_probe'){
    assert.equal(role,'client','UI adapter acceptance requires a client');
    execFileSync('python',['-B',resolve(import.meta.dirname,'install-adapter.py'),'--target',target,'--adapter',adapter,'--installed'],{cwd:repo,stdio:'inherit',windowsHide:true});
  }
  const output=execFileSync('pwsh',['-NoProfile','-Command',"& './gradlew.bat' ':extension_packages:stageValidationLibrary' '-PextensionLayout=installed' '-PextensionTarget="+target+"' '-PextensionRole="+role+"' '--offline' '--console=plain'; exit $LASTEXITCODE"],{cwd:repo,encoding:'utf8'});
  await writeFile(join(game,'extension-staging.log'),output);
  const library=join(game,'mods/minecraft-protocol-extension-libraries.jar');
  if(await access(library).then(()=>true,()=>false)){
    assert(!(await lstat(library)).isSymbolicLink());
    execFileSync('pwsh',['-NoProfile','-Command',"Add-Type -AssemblyName Microsoft.VisualBasic; [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($env:MCP_EXTENSION_RECYCLE,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)"],{cwd:repo,env:{...process.env,MCP_EXTENSION_RECYCLE:library},stdio:'inherit'});
  }
  await rename(library+'.pending',library);
  return {librarySha256:createHash('sha256').update(await readFile(library)).digest('hex'),role,adapter};
}
