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
  execFileSync('python',['-B',resolve(import.meta.dirname,'install-adapter.py'),'--target',target,'--adapter',adapter,'--role',role,'--installed'],{cwd:repo,stdio:'inherit',windowsHide:true});
  // The first ordinary startup installs the tiny discovery bridge; subsequent Loader discovery
  // builds approved native content itself. This host does not pre-stage a native library.
  return {automaticEarlyStage:true,role,adapter};
}
