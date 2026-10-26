import assert from 'node:assert/strict';
import {createHash, randomUUID} from 'node:crypto';
import {execFile} from 'node:child_process';
import {constants, createReadStream} from 'node:fs';
import {copyFile, lstat, mkdir, readFile, readdir, realpath, writeFile} from 'node:fs/promises';
import {dirname, isAbsolute, relative, resolve, sep} from 'node:path';
import {promisify} from 'node:util';

const VALIDATION = 'conformance-vanilla_ui';
const AUX_DIRECTORY = 'world-recovery-aux';
const LIMITS = {files: 1024, entries: 4096, bytes: 256 * 1024 * 1024, fileBytes: 64 * 1024 * 1024, milliseconds: 120000};
const CLASSES = {recover: 'net.minecraft.client.gui.screens.RecoverWorldDataScreen',
  optimize: 'net.minecraft.client.gui.screens.worldselection.OptimizeWorldScreen'};
const MENU = 'UiWorldRecoveryFixtureScreen';
const TARGETS = ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric'];
const REFERENCE = '26.3-neoforge';
const digest = value => createHash('sha256').update(value).digest('hex');

function within(parent, child) {
  const value = relative(parent, child);
  assert(value && !isAbsolute(value) && value !== '..' && !value.startsWith('..' + sep), 'Owned world path must stay inside its validation instance');
  return value;
}

async function optionalJson(path) {
  try {return JSON.parse(await readFile(path, 'utf8'));} catch (error) {if (error.code === 'ENOENT') return undefined; throw error;}
}

async function recycleOwnedDirectory(path) {
  assert.equal(process.platform, 'win32', 'No permanent deletion fallback: retain the owned auxiliary directory on this platform');
  const script = "$ErrorActionPreference='Stop'; Add-Type -AssemblyName Microsoft.VisualBasic; "
    + "$recoveryTarget=[IO.Path]::GetFullPath($env:MINECRAFT_UI_RECOVERY_RECYCLE_PATH); "
    + '[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($recoveryTarget,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin); '
    + "if(Test-Path -LiteralPath $recoveryTarget){throw 'Auxiliary directory survived recycling'}";
  await promisify(execFile)('powershell.exe', ['-NoProfile', '-NonInteractive', '-EncodedCommand', Buffer.from(script, 'utf16le').toString('base64')],
    {windowsHide: true, env: {...process.env, MINECRAFT_UI_RECOVERY_RECYCLE_PATH: path}, timeout: 30000});
}

async function inventory(root, healthy) {
  const files = []; let bytes = 0, entryCount = 0;
  const canonical = await realpath(root); assert.equal(canonical, root, 'World roots and ancestors must not be links');
  assert.equal((await lstat(root)).isSymbolicLink(), false, 'World roots must not be links');
  async function visit(directory) {
    healthy(); const entries = await readdir(directory, {withFileTypes: true}); entries.sort((a, b) => a.name.localeCompare(b.name, 'en'));
    for (const entry of entries) {
      healthy(); assert(++entryCount <= LIMITS.entries, 'World copy directory-entry budget exceeded');
      const path = resolve(directory, entry.name), key = within(canonical, path).split(sep).join('/');
      const info = await lstat(path); assert.equal(info.isSymbolicLink(), false, 'World copies reject links and junctions');
      assert.equal(await realpath(path), path, 'World files must not escape through an aliased directory');
      if (info.isDirectory()) {await visit(path); continue;}
      assert(info.isFile(), 'Only regular world files are copied');
      if (key === 'session.lock') continue;
      assert(files.length < LIMITS.files, 'World copy file-count budget exceeded');
      assert(info.size <= LIMITS.fileBytes, 'World copy per-file budget exceeded');
      bytes += info.size; assert(bytes <= LIMITS.bytes, 'World copy byte budget exceeded');
      const hash = createHash('sha256'); let readBytes = 0;
      for await (const chunk of createReadStream(path)) {
        healthy(); readBytes += chunk.length; assert(readBytes <= info.size, 'Source file grew while being copied'); hash.update(chunk);
      }
      assert.equal(readBytes, info.size, 'Source file changed while being hashed');
      files.push({path: key, bytes: info.size, sha256: hash.digest('hex')});
    }
  }
  await visit(canonical); files.sort((a, b) => a.path.localeCompare(b.path, 'en'));
  assert(files.some(file => file.path === 'level.dat'), 'The closed working world must contain real primary level data');
  assert(files.some(file => file.path === 'level.dat_old'), 'The healthy-backup case needs existing fallback level data; no backup is fabricated');
  return {files, bytes};
}

function closedClient(proof) {
  for (const field of ['integratedServerStopped', 'playerAbsent', 'connectionAbsent', 'sourceSaveCompleted']) assert.equal(proof[field], true, field);
}

function ownership(proof, {target, fixtureId, processId, sourceLevelId}) {
  assert.equal(proof.probeOnly, true); assert.equal(proof.arrangeAuthority, 'DIAGNOSTIC_owned_aux_world');
  assert.equal(proof.target, target); assert.equal(proof.fixtureId, fixtureId); assert.equal(proof.processId, processId);
  assert.equal(proof.sourceLevelId, sourceLevelId); assert.equal(proof.sourceWorldName, 'UI-Conformance'); closedClient(proof);
  for (const field of ['sourceWorldWritten', 'immutableBaselineTouched', 'dailyInstancesTouched', 'networkConnectionCreated', 'actualCorruptionInduced']) assert.equal(proof[field], false, field);
}

function recoveryAvailability(proof, target) {
  assert(TARGETS.includes(target), 'Only the five maintained native Targets are accepted');
  const expected = target === '1.20.1-forge' ? 'NATIVE_CLASS_ABSENT' : 'PRESENT';
  if (target !== REFERENCE || proof.recoveryNativeAvailability !== undefined)
    assert.equal(proof.recoveryNativeAvailability, expected, 'Native recovery availability must match the actual Target');
  return expected;
}

export function verifyWorldRecoveryProof(proof, expected) {
  ownership(proof, expected); assert.equal(proof.sequence, expected.sequence); assert.equal(proof.case, expected.caseId);
  const availability = recoveryAvailability(proof, expected.target);
  assert(['recover', 'optimize'].includes(expected.caseId));
  if (expected.caseId === 'recover') assert.equal(availability, 'PRESENT', 'An absent native recovery class is not a successful recovery proof');
  assert.equal(proof.nativeClass, CLASSES[expected.caseId]); assert.equal(proof.auxRootRelative, AUX_DIRECTORY);
  assert.equal(proof.copyManifestSha256, expected.manifestSha256); assert.equal(proof.auxLevelId, expected.caseId);
  assert.equal(proof.worldFilesTouched, 'auxonly'); assert.equal(proof.returnedToFixture, expected.returned);
  assert.equal(proof.storageAccessOpened, true); assert.equal(proof.nativeBusinessRepairPerformed, false);
  if (expected.returned) {
    for (const field of ['storageAccessClosed', 'directoryLockReacquiredAndClosed']) assert.equal(proof[field], true, field);
  }
  if (expected.caseId === 'recover') {
    assert.equal(proof.healthyPrimary, true); assert.equal(proof.healthyFallback, true); assert.equal(proof.restoreButtonActive, false);
    assert.equal(proof.realWorldUpgraderCreated, false); assert.equal(proof.callbackAction, expected.returned ? 'recover_back' : 'none');
  } else {
    assert.equal(proof.realWorldUpgraderCreated, true);
    if (expected.returned) {
      for (const field of ['workerTerminated', 'nativeRemovedCompleted', 'nativeCancelReturned', 'workerJoined']) assert.equal(proof[field], true, field);
      if (expected.target.startsWith('1.')) {
        assert.equal(proof.savedDataStorageCloseApplicable, false);
        assert.equal(proof.savedDataStorageClosed, false, 'The old native saved-data type has no close API');
        assert.equal(proof.savedDataSaveReturned, true, 'The real native worker must have returned from its saved-data save');
        assert.equal(proof.savedDataCleanupSemantics, 'native_work_save_return_and_worker_join_no_native_close_api');
      } else {
        if (expected.target !== REFERENCE || proof.savedDataStorageCloseApplicable !== undefined)
          assert.equal(proof.savedDataStorageCloseApplicable, true);
        assert.equal(proof.savedDataStorageClosed, true);
      }
      assert(['optimize_cancelled', 'optimize_finished'].includes(proof.callbackAction));
      if (proof.callbackAction === 'optimize_cancelled') {
        assert.equal(proof.nativeCancelReturned, true); assert.equal(proof.workerJoined, true); assert.equal(typeof proof.nativeFinishedBeforeCancel, 'boolean');
      } else assert.equal(proof.nativeFinishedBeforeCancel, true, 'Finishing early is not a native Cancel result');
    } else assert.equal(proof.callbackAction, 'none');
  }
  return proof;
}

// Only the current validation's closed working world is read. Native repair/conversion
// can write to the two exclusive auxiliary copies, never to that source or its baseline.
export async function worldRecoveryUiActions({api, repo, instance, target, tree, wait, nodes, ui, title, openFixture, check, samples,
  sourceLevelId = 'UI-Conformance', readFixtureProof, recycleAux = recycleOwnedDirectory}) {
  assert(TARGETS.includes(target), 'Only the five maintained native Targets are accepted');
  assert(typeof sourceLevelId === 'string' && sourceLevelId.length > 0 && sourceLevelId.length <= 100 && !/[\\/\x00-\x1f:]/.test(sourceLevelId)
    && !['.', '..'].includes(sourceLevelId), 'Source level ID must be one owned save-directory name');
  const root = resolve(repo, 'validations', VALIDATION, 'instance', target), client = resolve(root, 'client');
  assert.equal(resolve(instance), client, 'No daily instance, baseline or other validation can be a source');
  const canonicalRoot = await realpath(root); assert.equal(canonicalRoot, root, 'Validation roots must not be linked');
  assert.equal(await realpath(client), client); const source = resolve(client, 'saves', sourceLevelId); within(client, source);
  const aux = resolve(root, AUX_DIRECTORY), ownerPath = resolve(aux, 'owner.json'), proofPath = resolve(repo, 'validations', VALIDATION, 'result', target + '-world-recovery-ui-probe.json');
  within(root, aux); let owner, expected, request, primary, sourceInventory, copied = false, nativeMayBeOpen = false, cleanupFailure;
  let recoverVerified = false, optimizeCancelVerified = false, recoveryNativeAvailability;
  const started = performance.now(), healthy = () => assert(performance.now() - started < LIMITS.milliseconds, 'Owned world recovery helper exceeded its bounded time budget');
  async function readProof() {
    healthy(); const value = readFixtureProof ? await readFixtureProof() : JSON.parse(await readFile(proofPath, 'utf8'));
    assert(typeof value.fixtureId === 'string' && value.fixtureId.length >= 16); assert(Number.isSafeInteger(value.processId) && value.processId > 0);
    assert(Number.isSafeInteger(value.sequence) && value.sequence >= 0); return value;
  }
  async function clean() {
    healthy(); const input = await api('GET', '/v0/input/state'); assert.equal(input.pressedKeyCount, 0); assert.equal(input.pressedButtonCount, 0);
    assert.equal(input.inputCleanupPending, false); assert.equal(input.inputCleanupFailed, false); return input;
  }
  async function page(name) {
    const value = await wait(value => value.screenClass.endsWith(name), 'Actual native world recovery ' + name); healthy();
    assert.equal(value.dataCategory, 'USER'); assert.equal(value.treeTruncated, false); return value;
  }
  async function button(key, label) {
    const value = await tree(), found = nodes(value).filter(node => node.visible && node.active && node.actions?.includes('click')
      && (key ? node.translationKey === key : node.label === label));
    assert.equal(found.length, 1, 'One safe semantic native button: ' + (key || label));
    const outcome = await ui({nodeId: found[0].nodeId}); healthy(); assert.notEqual(outcome.directMutationUsed, true);
    return {node: found[0], outcome};
  }
  async function writeOwner(released) {owner.resourcesReleased = released; await writeFile(ownerPath, JSON.stringify(owner) + '\n', 'utf8');}
  async function returned() {
    await page(MENU); const value = verifyWorldRecoveryProof(await readProof(), {...expected, returned: true}); nativeMayBeOpen = false; return value;
  }
  function result(caseId, nativePage, before, extra = {}) {
    const value = {case: 'world_recovery_' + caseId, target, screenClass: nativePage?.screenClass, expectedNativeClass: CLASSES[caseId], nativeScreens: [],
      semanticActions: [], before, arrangeAuthority: 'DIAGNOSTIC_owned_aux_world', actAuthority: 'PLAYTEST',
      semanticTargets: true, handwrittenCoordinates: false, directCallbackUsed: false, nativeScreenObserved: false,
      worldFilesTouched: 'auxonly', sourceWorldWritten: false, immutableBaselineTouched: false, dailyInstancesTouched: false,
      copyManifestSha256: request.copyManifestSha256, worldRepairTested: false, worldConversionCorrectnessTested: false, ...extra};
    if (nativePage?.screenClass === CLASSES[caseId]) {
      value.nativeScreenObserved = true; value.nativeScreens.push(nativePage.screenClass);
      value.screenIdentity = nativePage.screenIdentity; value.screenRevision = nativePage.screenRevision;
    }
    samples.push(value); return value;
  }
  async function launch(caseId, label) {
    const baseline = await readProof(), before = await clean(); await writeOwner(false); nativeMayBeOpen = true;
    expected = {...expected, sequence: baseline.sequence + 1, caseId};
    await button(null, label);
    const nativePage = await wait(value => value.screenClass === CLASSES[caseId] || value.screenClass.endsWith(MENU), 'Native page or explicitly observed early optimizer completion');
    healthy(); const proof = await readProof(); verifyWorldRecoveryProof(proof, {...expected, returned: proof.returnedToFixture});
    if (caseId === 'recover') assert.equal(nativePage.screenClass, CLASSES.recover, 'Recovery must be observed, not just constructed');
    return {sample: result(caseId, nativePage, before, {arranged: proof}), nativePage, proof};
  }
  try {
    await title(); await page('TitleScreen'); assert.equal((await api('GET', '/v0/player?category=USER')).available, false);
    await openFixture();
    if (target === REFERENCE) {await button(null, 'UI Navigation Fixtures'); await page('UiNavigationFixtureScreen');}
    await button(null, 'World Recovery (Fixture)'); await page(MENU);
    const initial = await readProof(); expected = {target, sourceLevelId, fixtureId: initial.fixtureId, processId: initial.processId}; ownership(initial, expected);
    recoveryNativeAvailability = recoveryAvailability(initial, target);
    const priorAux = await lstat(aux).catch(error => {if (error.code !== 'ENOENT') throw error; return undefined;});
    if (priorAux) {assert(priorAux.isDirectory() && !priorAux.isSymbolicLink(), 'Auxiliary ownership must not traverse a link'); assert.equal(await realpath(aux), aux);}
    const previous = await optionalJson(ownerPath);
    if (previous) {
      assert.equal((await lstat(aux)).isSymbolicLink(), false); assert.equal(await realpath(aux), aux);
      assert.equal(previous.validation, VALIDATION); assert.equal(previous.target, target); assert.equal(previous.auxRootRelative, AUX_DIRECTORY);
      assert.equal(previous.runActive, false, 'Another or interrupted copy attempt retains exclusive ownership');
      assert.equal(previous.resourcesReleased, true, 'A prior unreleased auxiliary world must be retained, not replaced');
      assert.match(previous.ownerId, /^[0-9a-f-]{36}$/); await recycleAux(aux);
    }
    await mkdir(aux); owner = {version: 1, validation: VALIDATION, target, auxRootRelative: AUX_DIRECTORY, ownerId: randomUUID(),
      sourceLevelId, nativeProcessId: initial.processId, fixtureId: initial.fixtureId, runActive: true, resourcesReleased: true};
    await writeFile(ownerPath, JSON.stringify(owner) + '\n', {encoding: 'utf8', flag: 'wx'});
    sourceInventory = await inventory(source, healthy);
    const manifest = {version: 1, validation: VALIDATION, target, sourceLevelId, sourceWorldName: 'UI-Conformance',
      excludedFiles: ['session.lock'], fileCount: sourceInventory.files.length, totalBytes: sourceInventory.bytes, files: sourceInventory.files};
    const text = JSON.stringify(manifest) + '\n', manifestSha256 = digest(text);
    for (const id of ['recover', 'optimize']) {
      const destination = resolve(aux, 'worlds', id); await mkdir(destination, {recursive: true});
      for (const file of manifest.files) {
        healthy(); const path = resolve(destination, file.path); within(destination, path); await mkdir(dirname(path), {recursive: true});
        await copyFile(resolve(source, file.path), path, constants.COPYFILE_EXCL);
      }
      assert.deepEqual(await inventory(destination, healthy), sourceInventory, 'Auxiliary copy hashes must match the real closed source world');
    }
    assert.deepEqual(await inventory(source, healthy), sourceInventory, 'The closed source world changed during copying');
    await writeFile(resolve(aux, 'copy-manifest.json'), text, {encoding: 'utf8', flag: 'wx'});
    request = {version: 1, target, fixtureId: initial.fixtureId, processId: initial.processId, sourceLevelId, auxRootRelative: AUX_DIRECTORY,
      worldContainerRelative: AUX_DIRECTORY + '/worlds', recoverLevelId: 'recover', optimizeLevelId: 'optimize',
      copyManifestSha256: manifestSha256, fileCount: manifest.fileCount, totalBytes: manifest.totalBytes, eraseCache: false, maximumOptimizeAttempts: 1};
    await writeFile(resolve(aux, 'request.json'), JSON.stringify(request) + '\n', {encoding: 'utf8', flag: 'wx'});
    copied = true; expected.manifestSha256 = manifestSha256;
    samples.push({case: 'world_recovery_copy', target, worldFilesTouched: 'auxonly', sourceWorldWritten: false, immutableBaselineTouched: false,
      dailyInstancesTouched: false, copyManifestSha256: manifestSha256, limits: LIMITS, fileCount: manifest.fileCount, totalBytes: manifest.totalBytes,
      sourceLevelId, copies: ['recover', 'optimize'], sourceHashesVerified: true, bothCopyHashesVerified: true, excludedFiles: ['session.lock']});
    await check('UI world recovery: healthy primary and fallback disable Restore; semantic rejection and Back leave only the owned copy touched', async () => {
      if (recoveryNativeAvailability === 'NATIVE_CLASS_ABSENT') {
        samples.push({case: 'world_recovery_recover', target, applicable: false, recoverVerified: false,
          outcome: 'not_applicable_native_class_absent', nativeAvailability: recoveryNativeAvailability,
          nativeScreens: [], semanticActions: [], nativeScreenObserved: false,
          arrangeAuthority: 'DIAGNOSTIC_owned_aux_world', worldFilesTouched: 'none',
          sourceWorldWritten: false, immutableBaselineTouched: false, dailyInstancesTouched: false,
          worldRepairTested: false, worldConversionCorrectnessTested: false,
          fixtureId: expected.fixtureId, processId: expected.processId, copyManifestSha256: request.copyManifestSha256});
        return;
      }
      const context = await launch('recover', 'Healthy Recovery (Fixture)'), value = context.sample;
      const restore = nodes(context.nativePage).filter(node => node.visible && node.translationKey === 'recover_world.restore');
      assert.equal(restore.length, 1); assert.equal(restore[0].active, false);
      const beforeDenial = await clean(); assert(Number.isFinite(beforeDenial.agentPointer?.x) && Number.isFinite(beforeDenial.agentPointer?.y));
      await assert.rejects(ui({nodeId: restore[0].nodeId}), error => error.code === 'UI_NODE_NOT_ACTIONABLE');
      const afterDenial = await clean(), afterTree = await tree(); assert.equal(afterDenial.inputDispatchSequence, beforeDenial.inputDispatchSequence + 1);
      for (const field of ['x', 'y']) assert.equal(afterDenial.agentPointer[field], beforeDenial.agentPointer[field]);
      for (const field of ['screenIdentity', 'screenRevision']) assert.equal(afterTree[field], context.nativePage[field]);
      value.restoreDenied = {nodeId: restore[0].nodeId, code: 'UI_NODE_NOT_ACTIONABLE', before: beforeDenial, after: afterDenial, mandatoryCleanupDispatches: 1};
      value.semanticActions.push(await button('gui.back')); value.returned = await returned(); value.after = await clean();
      value.outcome = 'healthy_restore_disabled_and_native_back'; recoverVerified = true;
    });
    await check('UI world recovery: one real optimizer attempt distinguishes native Cancel and joined cleanup from finishing before Cancel', async () => {
      const context = await launch('optimize', 'Optimize Aux World (Fixture)'), value = context.sample;
      if (!context.proof.returnedToFixture) {
        assert.equal(context.nativePage.screenClass, CLASSES.optimize); const cancel = nodes(context.nativePage).filter(node => node.visible && node.active && node.translationKey === 'gui.cancel');
        assert.equal(cancel.length, 1, 'The actual running optimizer must expose its native Cancel button');
        try {value.semanticActions.push(await button('gui.cancel')); value.cancelInputSubmitted = true;}
        catch (error) {
          // The worker may finish after the USER tree read but before click dispatch.
          // Never replay the click, extend the world, or count this race as Cancel.
          value.cancelInputError = {code: error.code, message: error.message,
            ...(error.httpStatus ? {httpStatus: error.httpStatus} : {}), ...(error.nativeError ? {nativeError: error.nativeError} : {})};
          if (!['UI_TARGET_INVALIDATED', 'UI_NODE_NOT_FOUND'].includes(error.code)) throw error;
          const observed = await returned(); assert.equal(observed.callbackAction, 'optimize_finished'); value.returned = observed;
        }
      }
      value.returned ??= await returned(); value.after = await clean();
      optimizeCancelVerified = value.nativeScreenObserved && value.cancelInputSubmitted === true && value.returned.callbackAction === 'optimize_cancelled'
        && value.returned.nativeFinishedBeforeCancel === false;
      value.optimizeCancelVerified = optimizeCancelVerified;
      value.outcome = optimizeCancelVerified
        ? target.startsWith('1.') ? 'native_cancel_worker_joined_saved_data_flushed_storage_lock_closed' : 'native_cancel_worker_joined_storage_closed'
        : 'finished_before_cancel_not_cancel_coverage';
      if (!optimizeCancelVerified) assert.equal(value.returned.nativeFinishedBeforeCancel, true, 'A worker already finished at Cancel entry is not cancellation coverage');
    });
    await title(); await page('TitleScreen'); await clean();
  } catch (error) {primary = error;}
  finally {
    if (nativeMayBeOpen) {
      try {
        const current = await tree();
        if (current.screenClass === CLASSES.recover) await button('gui.back');
        else if (current.screenClass === CLASSES.optimize) await button('gui.cancel');
        assert(expected?.caseId, 'Unknown auxiliary native ownership cannot be treated as closed');
        const proof = await returned(); assert.equal(proof.storageAccessClosed, true); assert.equal(proof.directoryLockReacquiredAndClosed, true);
      } catch (error) {cleanupFailure = error;}
    }
    if (owner) {
      let sourceStillMatches = false;
      try {
        if (copied) {assert.deepEqual(await inventory(source, () => {}), sourceInventory, 'Original working-world content was modified'); sourceStillMatches = true;}
        owner.runActive = false; await writeOwner(!nativeMayBeOpen && !cleanupFailure);
      } catch (error) {cleanupFailure = cleanupFailure ? new AggregateError([cleanupFailure, error], 'Auxiliary native and source-integrity cleanup failed') : error;}
      samples.push({case: 'world_recovery_cleanup', target, auxRootRelative: AUX_DIRECTORY, ownerId: owner.ownerId, worldFilesTouched: 'auxonly',
        sourceWorldWritten: sourceStillMatches ? false : 'not_confirmed', sourceWorldWrittenByTool: false,
        immutableBaselineTouched: false, dailyInstancesTouched: false, retainedForInspection: true,
        sourceHashesStillMatch: sourceStillMatches, nativeResourcesReleased: !nativeMayBeOpen && !cleanupFailure,
        recoveryVerified: recoverVerified, recoveryNativeAvailability, optimizeCancelVerified, cleanupCompleted: !cleanupFailure, failed: Boolean(primary || cleanupFailure),
        ...(primary ? {failure: primary.message} : {}), ...(cleanupFailure ? {cleanupFailure: cleanupFailure.message} : {}),
        elapsedMillis: performance.now() - started, persistentWriteCalls: 0, liveEvidence: 'requires_actual_native_runner'});
    }
    if (cleanupFailure) {
      if (primary) throw new AggregateError([primary, cleanupFailure], 'World recovery UI failed and auxiliary cleanup was not proved');
      throw cleanupFailure;
    }
    if (primary) throw primary;
  }
  return {recoverVerified, recoveryNativeAvailability, optimizeCancelVerified, worldFilesTouched: 'auxonly', auxiliaryFilesRetained: true};
}
