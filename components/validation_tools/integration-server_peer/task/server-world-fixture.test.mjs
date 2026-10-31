import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdir,writeFile,readFile} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {createHash,randomUUID} from 'node:crypto';
import {prepareServerWorld} from './server-world-fixture.mjs';

test('adapter baseline restores exact bytes and rejects a changed upstream identity before touching the current world',async()=>{
  const repo=resolve(import.meta.dirname,'../../build/test/server-fixture',randomUUID());
  const game=join(repo,'validations/integration-extension_platform/instance/installed/1.21.1-neoforge/server');
  const baseline=join(game,'fixture-baseline-ae2'),world='Extension-Server-ae2',facts=join(repo,'target.properties');
  await mkdir(join(baseline,'world'),{recursive:true});await writeFile(facts,'loader_version=fixed\n');
  const hash=bytes=>createHash('sha256').update(bytes).digest('hex'),bytes=Buffer.from('saved-world-fixture');
  const files=[{path:'level.dat',sha256:hash(bytes)}],fixtureIdentity={adapter:'ae2',upstream:{'ae2.jar':hash('pinned upstream')}};
  await writeFile(join(baseline,'world/level.dat'),bytes);
  await writeFile(join(baseline,'manifest.json'),JSON.stringify({target:'1.21.1-neoforge',world,factsHash:hash(await readFile(facts)),files,hash:hash(JSON.stringify(files)),fixtureIdentity,credentialsCopied:false}));
  const request={repo,game,world,target:'1.21.1-neoforge',facts,validation:'integration-extension_platform',baselineId:'fixture-baseline-ae2',fixtureIdentity,start:()=>{throw Error('Baseline restore must not launch Minecraft');},stop:()=>{throw Error('Unexpected native process');}};
  const restored=await prepareServerWorld(request);assert.equal(restored.baselineSha256,restored.restoredSha256);assert.deepEqual(await readFile(join(game,world,'level.dat')),bytes);
  await assert.rejects(prepareServerWorld({...request,fixtureIdentity:{...fixtureIdentity,upstream:{'ae2.jar':hash('changed upstream')}}}),/Upstream adapter inputs changed/);
  assert.deepEqual(await readFile(join(game,world,'level.dat')),bytes);
  await assert.rejects(prepareServerWorld({...request,baselineId:'../escape'}),/Registered baseline/);
});
