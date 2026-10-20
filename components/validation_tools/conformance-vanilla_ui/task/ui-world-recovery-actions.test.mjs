import assert from 'node:assert/strict';
import {execFile} from 'node:child_process';
import {createHash} from 'node:crypto';
import {lstat, mkdir, mkdtemp, open, readFile, readdir, rename, rm, writeFile} from 'node:fs/promises';
import {tmpdir} from 'node:os';
import {basename, dirname, join, resolve} from 'node:path';
import test from 'node:test';
import {promisify} from 'node:util';
import {worldRecoveryUiActions} from './ui-world-recovery-actions.mjs';

const target = '26.3-neoforge', native = 'net.minecraft.client.gui.screens.', probe = 'io.github.recrivenvi.minecraftprotocol.probe.validation.';
const roots = new Set();
async function recycleTestRoot(root) {
    const script = "$ErrorActionPreference='Stop'; Add-Type -AssemblyName Microsoft.VisualBasic; "
      + '[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($env:MINECRAFT_UI_RECOVERY_TEST_ROOT,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)';
    await promisify(execFile)('powershell.exe', ['-NoProfile', '-NonInteractive', '-EncodedCommand', Buffer.from(script, 'utf16le').toString('base64')],
      {windowsHide: true, env: {...process.env, MINECRAFT_UI_RECOVERY_TEST_ROOT: root}, timeout: 30000});
}
async function cleanupTestRoot(root, {windows = process.platform === 'win32', recycle = recycleTestRoot, remove = rm} = {}) {
  const path = resolve(root);
  assert(roots.has(path), 'Cleanup requires a registered test-owned temporary directory');
  assert.equal(dirname(path), resolve(tmpdir())); assert(basename(path).startsWith('minecraft-ui-world-recovery-'));
  if (windows) await recycle(path);
  else await remove(path, {recursive: true, force: false});
  roots.delete(path);
}
test.after(async () => {
  for (const root of [...roots]) await cleanupTestRoot(root);
});

// Real files are copied and hashed. Native classes, workers and storage facts below
// are a controlled API adapter: these unit tests are not Minecraft Live evidence.
async function controlledNative(options = {}) {
  const target = options.target ?? '26.3-neoforge', legacy = target.startsWith('1.');
  const root = await mkdtemp(join(tmpdir(), 'minecraft-ui-world-recovery-')); roots.add(root);
  const repo = join(root, 'repository'), instanceRoot = join(repo, 'validations/conformance-vanilla_ui/instance', target);
  const instance = join(instanceRoot, 'client'), source = join(instance, 'saves/UI-Conformance'), aux = join(instanceRoot, 'world-recovery-aux');
  const original = {'level.dat': 'controlled-primary-data', 'level.dat_old': 'controlled-fallback-data', 'region/r.0.0.mca': 'controlled-region-data',
    'datapacks/fixture/pack.mcmeta': '{"controlledFixture":true}', 'session.lock': 'owned-source-lock-not-copied'};
  for (const [name, content] of Object.entries(original)) {const path = join(source, name); await mkdir(dirname(path), {recursive: true}); await writeFile(path, content);}
  const protectedFiles = [join(instanceRoot, 'baseline/saves/UI-Conformance/level.dat'), join(repo, 'instances', target, 'client/saves/Important/level.dat')];
  for (const path of protectedFiles) {await mkdir(dirname(path), {recursive: true}); await writeFile(path, 'protected-not-a-source');}
  let screen = native + 'TitleScreen', identity = 1, revision = 1, sequence = 0, dispatch = 0, currentCase, closed = true;
  let proof = {probeOnly: true, arrangeAuthority: 'DIAGNOSTIC_owned_aux_world', target, fixtureId: 'owned-world-recovery-current-fixture', processId: 71,
    sequence: 0, sourceLevelId: 'UI-Conformance', sourceWorldName: 'UI-Conformance', integratedServerStopped: true,
    playerAbsent: true, connectionAbsent: true, sourceSaveCompleted: true, sourceWorldWritten: false, immutableBaselineTouched: false,
    dailyInstancesTouched: false, networkConnectionCreated: false, actualCorruptionInduced: false,
    recoveryNativeAvailability: target === '1.20.1-forge' ? 'NATIVE_CLASS_ABSENT' : 'PRESENT'};
  if (options.serverStillRunning) proof.integratedServerStopped = false;
  const checks = [], samples = [], actions = [], recycled = [], nativeLaunches = [];
  const change = name => {screen = name; identity++; revision++;};
  const button = (nodeId, label, translationKey, active = true) => ({nodeId, label, translationKey, role: 'button', visible: true, active, actions: ['click']});
  const tree = async () => {
    let children = [];
    if (screen.endsWith('AutomationProbeScreen')) children = target === '26.3-neoforge'
      ? [button('navigation', 'UI Navigation Fixtures')] : [button('world-recovery', 'World Recovery (Fixture)')];
    else if (screen.endsWith('UiNavigationFixtureScreen')) children = [button('world-recovery', 'World Recovery (Fixture)')];
    else if (screen.endsWith('UiWorldRecoveryFixtureScreen')) children = [button('recover', 'Healthy Recovery (Fixture)', undefined, target !== '1.20.1-forge'), button('optimize', 'Optimize Aux World (Fixture)')];
    else if (screen === native + 'RecoverWorldDataScreen') children = [button('restore', 'Restore', 'recover_world.restore', false), button('back', 'Back', 'gui.back')];
    else if (screen === native + 'worldselection.OptimizeWorldScreen') children = [button('cancel', 'Cancel', 'gui.cancel')];
    return {screenClass: screen, screenIdentity: identity, screenRevision: revision, dataCategory: 'USER', treeTruncated: false, children};
  };
  function complete(caseId, action) {
    closed = !options.storageNotClosed;
    Object.assign(proof, {returnedToFixture: true, callbackAction: action, storageAccessClosed: closed, directoryLockReacquiredAndClosed: closed});
    if (caseId === 'optimize') Object.assign(proof, {workerTerminated: true, nativeRemovedCompleted: true,
      savedDataStorageCloseApplicable: !legacy, savedDataStorageClosed: !legacy,
      ...(legacy ? {savedDataSaveReturned: true, savedDataCleanupSemantics: 'native_work_save_return_and_worker_join_no_native_close_api'} : {}),
      nativeCancelReturned: true, workerJoined: !options.workerNotJoined,
      nativeFinishedBeforeCancel: action === 'optimize_finished' || options.finishedBeforeNativeCancel === true});
    change(probe + 'UiWorldRecoveryFixtureScreen');
  }
  const ctx = {api: async (method, path) => {
    assert.equal(method, 'GET');
    if (path === '/v0/player?category=USER') return {available: false};
    assert.equal(path, '/v0/input/state'); return {pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false,
      inputDispatchSequence: dispatch, agentPointer: {x: 42.5, y: 54.25}};
  }, repo, instance, target, tree, nodes: value => value.children, samples,
  title: async () => {change(native + 'TitleScreen');}, openFixture: async () => {change(probe + 'AutomationProbeScreen');},
  wait: async (predicate, label) => {const value = await tree(); assert(predicate(value), label); return value;},
  readFixtureProof: async () => ({...structuredClone(proof), ...options.proofChange}),
  check: async (name, run) => {assert(name.startsWith('UI world recovery:')); await run(); checks.push(name);},
  recycleAux: async path => {
    assert.equal(path, aux); const destination = join(root, 'synthetic-recycle-' + recycled.length);
    await rename(path, destination); recycled.push(destination);
  },
  ui: async selector => {
    assert.deepEqual(Object.keys(selector), ['nodeId']); const node = (await tree()).children.find(value => value.nodeId === selector.nodeId); assert(node);
    actions.push(selector.nodeId); dispatch++;
    if (!node.active) throw Object.assign(new Error('Disabled native Restore'), {code: 'UI_NODE_NOT_ACTIONABLE'});
    if (selector.nodeId === 'navigation') change(probe + 'UiNavigationFixtureScreen');
    else if (selector.nodeId === 'world-recovery') change(probe + 'UiWorldRecoveryFixtureScreen');
    else if (selector.nodeId === 'back') {
      complete('recover', 'recover_back'); if (options.sourceMutated) await writeFile(join(source, 'level.dat'), 'unexpected-source-mutation');
    } else if (selector.nodeId === 'cancel') {
      if (options.finishDuringClick) {complete('optimize', 'optimize_finished'); throw Object.assign(new Error('Screen replaced by natural completion'), {code: options.finishErrorCode ?? 'UI_TARGET_INVALIDATED'});}
      complete('optimize', 'optimize_cancelled');
    } else if (['recover', 'optimize'].includes(selector.nodeId)) {
      const request = JSON.parse(await readFile(join(aux, 'request.json'), 'utf8'));
      assert.equal(request.fixtureId, proof.fixtureId); assert.equal(request.processId, proof.processId);
      assert.equal(request.worldContainerRelative, 'world-recovery-aux/worlds'); assert.equal(request.maximumOptimizeAttempts, 1); assert.equal(request.eraseCache, false);
      currentCase = selector.nodeId; nativeLaunches.push(currentCase); sequence++; closed = false;
      proof = {...proof, sequence, case: currentCase, nativeClass: native + (currentCase === 'recover' ? 'RecoverWorldDataScreen' : 'worldselection.OptimizeWorldScreen'),
        auxRootRelative: 'world-recovery-aux', auxLevelId: currentCase, copyManifestSha256: request.copyManifestSha256,
        worldFilesTouched: 'auxonly', returnedToFixture: false, storageAccessOpened: true, storageAccessClosed: false, directoryLockReacquiredAndClosed: false,
        nativeBusinessRepairPerformed: false, healthyPrimary: true, healthyFallback: true, restoreButtonActive: false, callbackAction: 'none',
        realWorldUpgraderCreated: currentCase === 'optimize', nativeCancelReturned: false, workerJoined: false, workerTerminated: false,
        nativeRemovedCompleted: false, savedDataStorageClosed: false, nativeFinishedBeforeCancel: false};
      change(proof.nativeClass);
      if (currentCase === 'optimize') {
        await writeFile(join(aux, 'worlds/optimize/region/r.0.0.mca'), 'controlled-native-aux-only-write');
        if (options.finishedEarly) complete('optimize', 'optimize_finished');
      }
    } else assert.fail(selector.nodeId);
    return {directMutationUsed: false, targetingSource: 'interaction_tree'};
  }};
  return {ctx, repo, source, aux, instanceRoot, original, checks, samples, actions, nativeLaunches, protectedFiles, recycled,
    state: () => ({screen, closed, proof: structuredClone(proof)}),
    assertSourceUnchanged: async () => {
      for (const [path, content] of Object.entries(original)) assert.equal(await readFile(join(source, path), 'utf8'), content);
      for (const path of protectedFiles) assert.equal(await readFile(path, 'utf8'), 'protected-not-a-source');
    }};
}

test('actual helper copies and hashes two real bounded worlds; semantic Restore refusal, Back and native Cancel stay aux-only', async () => {
  const fixture = await controlledNative(); const result = await worldRecoveryUiActions(fixture.ctx);
  assert.deepEqual(result, {recoverVerified: true, recoveryNativeAvailability: 'PRESENT', optimizeCancelVerified: true, worldFilesTouched: 'auxonly', auxiliaryFilesRetained: true});
  assert.equal(fixture.checks.length, 2); assert.deepEqual(fixture.nativeLaunches, ['recover', 'optimize']);
  assert(fixture.state().screen.endsWith('TitleScreen')); assert.equal(fixture.state().closed, true); await fixture.assertSourceUnchanged();
  const manifestText = await readFile(join(fixture.aux, 'copy-manifest.json'), 'utf8'), manifest = JSON.parse(manifestText);
  assert.equal(manifest.fileCount, 4); assert.equal(manifest.files.length, 4); assert.deepEqual(manifest.excludedFiles, ['session.lock']);
  assert.equal(fixture.samples[0].copyManifestSha256, createHash('sha256').update(manifestText).digest('hex'));
  for (const file of manifest.files) {
    const copied = await readFile(join(fixture.aux, 'worlds/recover', file.path));
    assert.equal(file.sha256, createHash('sha256').update(copied).digest('hex')); assert.equal(file.bytes, copied.length);
  }
  await assert.rejects(lstat(join(fixture.aux, 'worlds/recover/session.lock')), {code: 'ENOENT'});
  assert.equal(await readFile(join(fixture.aux, 'worlds/optimize/region/r.0.0.mca'), 'utf8'), 'controlled-native-aux-only-write');
  const [copied, recovered, optimized, cleanup] = fixture.samples;
  assert.equal(copied.bothCopyHashesVerified, true); assert.equal(recovered.restoreDenied.mandatoryCleanupDispatches, 1);
  assert.equal(recovered.nativeScreenObserved, true); assert.equal(recovered.returned.healthyFallback, true);
  assert.equal(optimized.optimizeCancelVerified, true); assert.equal(optimized.returned.workerJoined, true);
  assert.equal(cleanup.cleanupCompleted, true); assert.equal(cleanup.nativeResourcesReleased, true); assert.equal(cleanup.sourceHashesStillMatch, true);
  const owner = JSON.parse(await readFile(join(fixture.aux, 'owner.json'), 'utf8')); assert.equal(owner.resourcesReleased, true); assert.equal(owner.runActive, false);
});

test('a worker finishing before the UI or during the click is never counted as successful Cancel and never replayed', async () => {
  for (const option of ['finishedEarly', 'finishDuringClick', 'finishedBeforeNativeCancel']) {
    const fixture = await controlledNative({[option]: true}); const result = await worldRecoveryUiActions(fixture.ctx);
    assert.equal(result.recoverVerified, true); assert.equal(result.optimizeCancelVerified, false);
    const sample = fixture.samples.find(value => value.case === 'world_recovery_optimize');
    assert.equal(sample.outcome, 'finished_before_cancel_not_cancel_coverage');
    assert.equal(sample.returned.callbackAction, option === 'finishedBeforeNativeCancel' ? 'optimize_cancelled' : 'optimize_finished');
    assert.equal(fixture.nativeLaunches.filter(value => value === 'optimize').length, 1);
    assert.equal(fixture.actions.filter(value => value === 'cancel').length, option === 'finishedEarly' ? 0 : 1);
    assert.equal(sample.nativeScreenObserved, option !== 'finishedEarly'); await fixture.assertSourceUnchanged();
    if (option === 'finishedEarly') {assert(sample.screenClass.endsWith('UiWorldRecoveryFixtureScreen')); assert.deepEqual(sample.nativeScreens, []);}
  }
});

test('a native page that finishes before request target resolution reports NODE_NOT_FOUND, not successful Cancel', async () => {
  const fixture = await controlledNative({finishDuringClick: true, finishErrorCode: 'UI_NODE_NOT_FOUND'});
  const result = await worldRecoveryUiActions(fixture.ctx);
  assert.equal(result.optimizeCancelVerified, false);
  const sample = fixture.samples.find(value => value.case === 'world_recovery_optimize');
  assert.equal(sample.cancelInputError.code, 'UI_NODE_NOT_FOUND');
  assert.equal(sample.returned.callbackAction, 'optimize_finished');
  assert.equal(sample.returned.nativeFinishedBeforeCancel, true);
  assert.equal(sample.outcome, 'finished_before_cancel_not_cancel_coverage');
  assert.equal(fixture.actions.filter(value => value === 'cancel').length, 1);
  await fixture.assertSourceUnchanged();
});

test('natural completion cannot reclassify an authorization failure as a page-replacement race', async () => {
  const fixture = await controlledNative({finishDuringClick: true, finishErrorCode: 'SCOPE_REQUIRED'});
  await assert.rejects(worldRecoveryUiActions(fixture.ctx), /Only native page replacement/);
  assert.equal(fixture.samples.some(value => value.case === 'world_recovery_cleanup' && value.failed), true);
  assert.equal(fixture.actions.filter(value => value === 'cancel').length, 1);
});

test('the actual helper uses independent modern/legacy entries, skips only the absent Forge recovery and verifies each real native cleanup', async () => {
  for (const testedTarget of ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-fabric']) {
    const fixture = await controlledNative({target: testedTarget});
    const result = await worldRecoveryUiActions(fixture.ctx);
    const absent = testedTarget === '1.20.1-forge', legacy = testedTarget.startsWith('1.');
    assert.equal(result.recoverVerified, !absent);
    assert.equal(result.recoveryNativeAvailability, absent ? 'NATIVE_CLASS_ABSENT' : 'PRESENT');
    assert.equal(result.optimizeCancelVerified, true);
    assert.equal(fixture.actions.includes('navigation'), false, 'Non-reference recovery must not depend on the navigation fixture');
    assert.deepEqual(fixture.nativeLaunches, absent ? ['optimize'] : ['recover', 'optimize']);
    assert.equal(fixture.nativeLaunches.filter(value => value === 'optimize').length, 1);
    const recovery = fixture.samples.find(value => value.case === 'world_recovery_recover');
    if (absent) {
      assert.equal(recovery.applicable, false); assert.equal(recovery.recoverVerified, false);
      assert.equal(recovery.outcome, 'not_applicable_native_class_absent');
      assert.equal(recovery.nativeScreenObserved, false); assert.deepEqual(recovery.nativeScreens, []);
      assert.equal(fixture.actions.includes('restore'), false);
    }
    const optimized = fixture.samples.find(value => value.case === 'world_recovery_optimize');
    assert.equal(optimized.returned.savedDataStorageCloseApplicable, !legacy);
    assert.equal(optimized.returned.savedDataStorageClosed, !legacy);
    if (legacy) assert.equal(optimized.returned.savedDataSaveReturned, true);
    assert.equal(optimized.returned.workerJoined, true); assert.equal(optimized.returned.storageAccessClosed, true);
    assert.equal(optimized.returned.directoryLockReacquiredAndClosed, true);
    await fixture.assertSourceUnchanged();
  }
});

test('cross-Target unknown/false native close, flush, ownership and directory-lock claims cannot become successful cleanup', async () => {
  for (const [testedTarget, proofChange] of [
    ['1.20.1-forge', {recoveryNativeAvailability: 'PRESENT'}],
    ['1.21.1-neoforge', {savedDataSaveReturned: false}],
    ['1.21.1-neoforge', {savedDataStorageClosed: true}],
    ['1.21.1-neoforge', {savedDataStorageCloseApplicable: true}],
    ['26.1.2-neoforge', {savedDataStorageClosed: false}],
    ['26.3-fabric', {savedDataStorageCloseApplicable: false}],
    ['26.3-fabric', {directoryLockReacquiredAndClosed: undefined}],
    ['26.1.2-neoforge', {nativeCancelReturned: false}],
  ]) {
    const fixture = await controlledNative({target: testedTarget, proofChange});
    await assert.rejects(worldRecoveryUiActions(fixture.ctx));
    assert.equal(fixture.samples.some(sample => sample.case === 'world_recovery_cleanup' && sample.nativeResourcesReleased === true), false);
    assert.equal(fixture.nativeLaunches.filter(value => value === 'optimize').length <= 1, true);
    await fixture.assertSourceUnchanged();
  }
});

test('old native workers finishing naturally retain honest no-close semantics but never claim cancellation coverage', async () => {
  for (const testedTarget of ['1.20.1-forge', '1.21.1-neoforge']) {
    const fixture = await controlledNative({target: testedTarget, finishedEarly: true});
    const result = await worldRecoveryUiActions(fixture.ctx);
    assert.equal(result.optimizeCancelVerified, false);
    const sample = fixture.samples.find(value => value.case === 'world_recovery_optimize');
    assert.equal(sample.outcome, 'finished_before_cancel_not_cancel_coverage');
    assert.equal(sample.returned.callbackAction, 'optimize_finished');
    assert.equal(sample.returned.nativeCancelReturned, true, 'Native removed still calls cancel/join after natural finish');
    assert.equal(sample.returned.savedDataStorageClosed, false);
    assert.equal(sample.returned.savedDataSaveReturned, true);
    assert.equal(fixture.actions.includes('cancel'), false);
    await fixture.assertSourceUnchanged();
  }
});

test('repeat execution recycles only its released owned auxiliary copy; unknown, active or unreleased directories are retained', async () => {
  const fixture = await controlledNative(); await worldRecoveryUiActions(fixture.ctx);
  const oldRequest = await readFile(join(fixture.aux, 'request.json'), 'utf8');
  await worldRecoveryUiActions(fixture.ctx); assert.equal(fixture.recycled.length, 1);
  assert.equal(await readFile(join(fixture.recycled[0], 'request.json'), 'utf8'), oldRequest); await fixture.assertSourceUnchanged();
  for (const reason of ['unknown', 'active', 'unreleased']) {
    const blocked = await controlledNative(); await mkdir(blocked.aux); await writeFile(join(blocked.aux, 'sentinel'), 'retain-this-unknown-or-busy-copy');
    if (reason !== 'unknown') await writeFile(join(blocked.aux, 'owner.json'), JSON.stringify({validation: 'conformance-vanilla_ui', target,
      auxRootRelative: 'world-recovery-aux', ownerId: '00000000-0000-0000-0000-000000000000', runActive: reason === 'active', resourcesReleased: reason !== 'unreleased'}));
    await assert.rejects(worldRecoveryUiActions(blocked.ctx)); assert.equal(blocked.recycled.length, 0); assert.equal(blocked.nativeLaunches.length, 0);
    assert.equal(await readFile(join(blocked.aux, 'sentinel'), 'utf8'), 'retain-this-unknown-or-busy-copy'); await blocked.assertSourceUnchanged();
  }
});

test('unclosed native storage or a worker not joined fails and retains unreleased ownership; cleanup never masks the primary failure', async () => {
  for (const option of ['storageNotClosed', 'workerNotJoined']) {
    const fixture = await controlledNative({[option]: true});
    await assert.rejects(worldRecoveryUiActions(fixture.ctx), error => error instanceof AggregateError && error.errors.length === 2);
    const cleanup = fixture.samples.at(-1); assert.equal(cleanup.case, 'world_recovery_cleanup'); assert.equal(cleanup.cleanupCompleted, false);
    assert.equal(cleanup.nativeResourcesReleased, false); assert.equal(cleanup.failed, true); assert(cleanup.failure); assert(cleanup.cleanupFailure);
    const owner = JSON.parse(await readFile(join(fixture.aux, 'owner.json'), 'utf8')); assert.equal(owner.resourcesReleased, false);
    assert.equal(fixture.recycled.length, 0); await fixture.assertSourceUnchanged();
  }
});

test('copy budgets, absent real fallback data and source links are refused before opening a native storage or worker', async () => {
  const oversized = await controlledNative(); const huge = await open(join(oversized.source, 'oversized-region.mca'), 'wx');
  try {await huge.truncate(64 * 1024 * 1024 + 1);} finally {await huge.close();}
  await assert.rejects(worldRecoveryUiActions(oversized.ctx), /per-file budget/); assert.equal(oversized.nativeLaunches.length, 0);
  const noFallback = await controlledNative(); await rename(join(noFallback.source, 'level.dat_old'), join(noFallback.source, 'not-a-fallback.dat'));
  await assert.rejects(worldRecoveryUiActions(noFallback.ctx), /existing fallback/); assert.equal(noFallback.nativeLaunches.length, 0);
  const linked = await controlledNative(); const moved = resolve(linked.source, '../owned-but-not-the-world'); await rename(linked.source, moved);
  const {symlink} = await import('node:fs/promises'); await symlink(moved, linked.source, process.platform === 'win32' ? 'junction' : 'dir');
  await assert.rejects(worldRecoveryUiActions(linked.ctx), /World roots.*must not be links/); assert.equal(linked.nativeLaunches.length, 0);
});

test('running or unsaved worlds, alternate instances and source traversal cannot create an auxiliary copy', async () => {
  const running = await controlledNative({serverStillRunning: true}); await assert.rejects(worldRecoveryUiActions(running.ctx), /integratedServerStopped/);
  await assert.rejects(lstat(running.aux), {code: 'ENOENT'});
  const fixture = await controlledNative();
  await assert.rejects(worldRecoveryUiActions({...fixture.ctx, instance: join(fixture.repo, 'instances', target, 'client')}), /No daily instance/);
  await assert.rejects(worldRecoveryUiActions({...fixture.ctx, sourceLevelId: '../baseline'}), /one owned save-directory/);
  await assert.rejects(lstat(fixture.aux), {code: 'ENOENT'}); await fixture.assertSourceUnchanged();
});

test('an unexpected source mutation after native UI is detected instead of publishing an unchanged-world claim', async () => {
  const fixture = await controlledNative({sourceMutated: true}); await assert.rejects(worldRecoveryUiActions(fixture.ctx), /Original working-world content was modified/);
  const cleanup = fixture.samples.at(-1); assert.equal(cleanup.cleanupCompleted, false); assert.equal(cleanup.sourceHashesStillMatch, false);
  assert.equal(cleanup.sourceWorldWritten, 'not_confirmed'); assert.equal(cleanup.sourceWorldWrittenByTool, false);
  assert.equal(cleanup.failed, true); assert(cleanup.cleanupFailure);
  for (const path of fixture.protectedFiles) assert.equal(await readFile(path, 'utf8'), 'protected-not-a-source');
  assert.equal((await readdir(fixture.instanceRoot)).includes('world-recovery-aux'), true);
});

test('temporary-test cleanup selects Windows recycling or actual non-Windows owned-directory removal, with no skip or swallowed error', async () => {
  const linux = await controlledNative(); let removed = false;
  const linuxRoot = dirname(linux.repo);
  await cleanupTestRoot(linuxRoot, {windows: false, recycle: async () => assert.fail('Linux cannot invoke a Windows interface'),
    remove: async (path, options) => {assert.equal(path, linuxRoot); assert.deepEqual(options, {recursive: true, force: false}); removed = true; await rm(path, options);}});
  assert.equal(removed, true); await assert.rejects(lstat(linuxRoot), {code: 'ENOENT'});
  const windows = await controlledNative(), windowsRoot = dirname(windows.repo), recycled = windowsRoot + '-recycled'; let sentToRecycle = false;
  await cleanupTestRoot(windowsRoot, {windows: true, remove: async () => assert.fail('Windows cannot permanently remove its test directory'),
    recycle: async path => {sentToRecycle = true; await rename(path, recycled); roots.add(recycled);}});
  assert.equal(sentToRecycle, true); await assert.rejects(lstat(windowsRoot), {code: 'ENOENT'}); assert((await lstat(recycled)).isDirectory());
  await assert.rejects(cleanupTestRoot(dirname(tmpdir()), {windows: false}), /registered test-owned/);
  const failed = await controlledNative(), failedRoot = dirname(failed.repo);
  await assert.rejects(cleanupTestRoot(failedRoot, {windows: false, remove: async () => {throw new Error('actual cleanup failed');}}), /actual cleanup failed/);
  assert(roots.has(failedRoot)); assert((await lstat(failedRoot)).isDirectory());
});
