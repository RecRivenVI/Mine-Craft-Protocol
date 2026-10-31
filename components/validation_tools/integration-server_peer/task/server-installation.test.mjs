import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdtemp,mkdir,writeFile,rename} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {join} from 'node:path';
import {serverInstallationReady,recordServerInstallation} from './server-installation.mjs';

test('partial installer arguments cannot stand in for complete official server outputs',async()=>{
  const game=await mkdtemp(join(tmpdir(),'minecraft-server-installation-'));
  await mkdir(join(game,'libraries/loader'),{recursive:true});
  const args='libraries/loader/win_args.txt',installer='a'.repeat(64);
  await writeFile(join(game,args),'-cp libraries/base.jar;libraries/server-extra.jar\nMain\n');
  await writeFile(join(game,'libraries/base.jar'),'official fixture library bytes');
  assert.equal(await serverInstallationReady(game,args,installer),false);
  await assert.rejects(recordServerInstallation(game,args,installer),/ENOENT/);
  await writeFile(join(game,'libraries/server-extra.jar'),'generated server resource bytes');
  await writeFile(join(game,'libraries/server-mapped.jar'),'indirect generated server classes');
  assert.equal(await serverInstallationReady(game,args,installer),false,'Existing args still lack successful installer attestation');
  await recordServerInstallation(game,args,installer);
  assert.equal(await serverInstallationReady(game,args,installer),true);
  await rename(join(game,'libraries/server-extra.jar'),join(game,'libraries/server-extra.withheld'));
  assert.equal(await serverInstallationReady(game,args,installer),false);
  await rename(join(game,'libraries/server-extra.withheld'),join(game,'libraries/server-extra.jar'));
  await writeFile(join(game,'libraries/server-mapped.jar'),'changed indirect output');
  assert.equal(await serverInstallationReady(game,args,installer),false);
  await recordServerInstallation(game,args,installer);
  assert.equal(await serverInstallationReady(game,args,'b'.repeat(64)),false);
  assert.equal(await serverInstallationReady(game,'../other/args.txt',installer),false);
});
