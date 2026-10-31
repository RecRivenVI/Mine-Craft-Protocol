import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,writeFile,rename,mkdir} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {sha256} from './alpha-candidate.mjs';
import {currentHashes,currentPackageIdentity,verifyExtensionPublication} from './extension-proof.mjs';

test('actual publication CLI dispatches the extension validator without an unsettled module cycle and rejects missing proof',async()=>{
  const repo=await mkdtemp(join(tmpdir(),'minecraft-extension-cli-proof-'));
  await mkdir(join(repo,'build'));
  const manifest=join(repo,'build/alpha-manifest.json');
  await writeFile(manifest,JSON.stringify({publicationProfile:'extension_platform_trial_v1',fullMilestonesComplete:false}));
  const child=spawnSync(process.execPath,[join(import.meta.dirname,'alpha-evidence.mjs'),'--verify-continuous-publication',manifest,'--repository-root',repo],{encoding:'utf8',timeout:15000});
  assert.ifError(child.error);
  assert.equal(child.status,1);
  assert.match(child.stderr,/Missing source\/tool identity/);
  assert.doesNotMatch(child.stderr,/unsettled top-level await/);
});

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
