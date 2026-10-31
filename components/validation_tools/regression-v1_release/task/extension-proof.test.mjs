import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,writeFile,rename,mkdir} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {sha256} from './alpha-candidate.mjs';
import {currentHashes,currentPackageIdentity,verifyExtensionPublication} from './extension-proof.mjs';

test('publication reads the bound file bytes and rejects stale, missing and escaped sources',async()=>{
  const root=await mkdtemp(join(tmpdir(),'minecraft-extension-proof-'));
  const path=join(root,'source.java');await writeFile(path,'current production');
  await currentHashes(root,{'source.java':sha256('current production')});
  await writeFile(path,'changed production');
  await assert.rejects(currentHashes(root,{'source.java':sha256('current production')}),/Stale executed/);
  await assert.rejects(currentHashes(root,{'../source.java':sha256('current production')}));
  await assert.rejects(currentHashes(root,{'absent.java':sha256('current production')}));
  await assert.rejects(verifyExtensionPublication({publicationProfile:'extension_platform_trial_v1',fullMilestonesComplete:false},root),/Missing source/);
  // Leave only an explicitly named temporary test input, never a game/credential directory.
  await rename(path,join(root,'completed-test-input.java'));
});

test('published package identity checks the actual indexed code and rejects unindexed files',async()=>{
  const repo=await mkdtemp(join(tmpdir(),'minecraft-extension-package-proof-'));
  const root=join(repo,'components/extension_packages/build/packages/example');await mkdir(root,{recursive:true});
  await writeFile(join(root,'code.jar'),'compiled adapter bytes');
  const manifest=JSON.stringify({id:'example',files:{'code.jar':sha256('compiled adapter bytes')}})+'\n';
  await writeFile(join(root,'extension.json'),manifest);
  assert.equal(await currentPackageIdentity(repo,'example'),sha256(manifest));
  await writeFile(join(root,'code.jar'),'different untested adapter');
  await assert.rejects(currentPackageIdentity(repo,'example'),/Stale executed/);
  await writeFile(join(root,'code.jar'),'compiled adapter bytes');await writeFile(join(root,'unindexed.txt'),'not installed');
  await assert.rejects(currentPackageIdentity(repo,'example'),/file index must be complete/);
  await assert.rejects(currentPackageIdentity(repo,'../example'));
});
