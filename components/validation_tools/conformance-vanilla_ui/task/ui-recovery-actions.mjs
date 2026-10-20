import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';

const NATIVE = {
  disconnected: ['DisconnectedScreen', 'Disconnected Page (Fixture)', 'none'],
  datapack_failure: ['DatapackLoadFailureScreen', 'Datapack Failure (Fixture)', 'datapack_cancelled'],
  file_fixer_aborted: ['FileFixerAbortedScreen', 'File Fixer Aborted (Fixture)', 'file_fixer_back'],
  file_fixer_progress: ['worldselection.FileFixerProgressScreen', 'File Fixer Progress (Fixture)', 'native_progress_cancel_observed'],
  graphics_warning: ['options.UnsupportedGraphicsWarningScreen', 'Graphics Warning (Fixture)', 'graphics_back'],
};

const TARGETS = ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric'];
const REFERENCE = '26.3-neoforge';

function support(value, target) {
  assert(TARGETS.includes(target), 'Only the five maintained native Targets are accepted');
  const available = target === '1.20.1-forge' ? ['disconnected', 'datapack_failure']
    : target === '1.21.1-neoforge' ? ['disconnected', 'datapack_failure', 'graphics_warning'] : Object.keys(NATIVE);
  const expected = Object.fromEntries(Object.keys(NATIVE).map(caseId => [caseId, available.includes(caseId) ? 'AVAILABLE' : 'NATIVE_CLASS_NOT_PRESENT']));
  if (target === REFERENCE && value.caseSupport === undefined) return expected;
  assert.deepEqual(value.caseSupport, expected, 'Native case support must match the actual finite Target fixture');
  assert.equal(value.datapackCancelDestination, target === '1.20.1-forge' ? 'native_title_screen' : 'fixture_callback');
  return expected;
}

export function verifyRecoveryUiProof(value, {target, fixtureId, processId, sequence, caseId, returned, callbackAction}) {
  assert(NATIVE[caseId], 'Only the finite native UI fixture cases are accepted');
  assert.equal(support(value, target)[caseId], 'AVAILABLE', 'An absent native class cannot supply a successful UI proof');
  assert.equal(value.probeOnly, true); assert.equal(value.arrangeAuthority, 'DIAGNOSTIC_native_screen_fixture');
  assert.equal(value.target, target); assert.equal(value.fixtureId, fixtureId); assert.equal(value.processId, processId);
  assert.equal(value.sequence, sequence); assert.equal(value.case, caseId);
  assert.equal(value.nativeClass, 'net.minecraft.client.gui.screens.' + NATIVE[caseId][0]);
  assert.equal(value.returnedToFixture, returned); assert.equal(value.callbackAction, callbackAction);
  for (const field of ['actualFaultInduced', 'networkConnectionCreated', 'externalServiceUsed', 'worldFilesTouched', 'repairOrConversionStarted', 'graphicsSettingsChanged']) assert.equal(value[field], false, field);
  assert(Number.isSafeInteger(value.openedNativeTick) && value.openedNativeTick >= 0);
  const progress = caseId === 'file_fixer_progress'; assert.equal(value.nativeProgressAvailable, progress);
  assert.equal(value.nativeProgressCancelled, progress && returned);
  assert.equal(value.nativeCancelButtonDisabledObserved, progress && returned);
  if (progress && returned) {
    assert(Number.isSafeInteger(value.cancelObservedNativeTick) && Number.isSafeInteger(value.returnedNativeTick));
    assert(value.cancelObservedNativeTick > value.openedNativeTick);
    assert(value.returnedNativeTick > value.cancelObservedNativeTick, 'The probe must observe native cancellation, then wait for a later client tick before returning');
  } else {
    assert.equal(value.cancelObservedNativeTick, -1); assert.equal(value.returnedNativeTick, -1);
  }
  return value;
}

// Native UI-only proof: no fault, network connection, repair worker or world IO is arranged.
export async function recoveryUiActions({api, repo, target, tree, wait, nodes, ui, title, openFixture, check, samples, readFixtureProof}) {
  assert(TARGETS.includes(target), 'Only the five maintained native Targets are accepted');
  const proofPath = resolve(repo, 'validations/conformance-vanilla_ui/result', target + '-recovery-ui-probe.json');
  async function clean() {
    const input = await api('GET', '/v0/input/state'); assert.equal(input.pressedKeyCount, 0); assert.equal(input.pressedButtonCount, 0);
    assert.equal(input.inputCleanupPending, false); assert.equal(input.inputCleanupFailed, false); return input;
  }
  async function page(name) {
    const value = await wait(value => value.screenClass.endsWith(name), 'Actual native recovery UI ' + name);
    assert.equal(value.dataCategory, 'USER'); assert.equal(value.treeTruncated, false); return value;
  }
  async function button(keys, label) {
    const value = await tree(), candidates = nodes(value).filter(node => node.visible && node.active && node.actions?.includes('click')
      && (keys.includes(node.translationKey) || node.label === label));
    assert.equal(candidates.length, 1, 'One actual safe native button: ' + (label ?? keys.join('|')));
    const result = await ui({nodeId: candidates[0].nodeId}); assert.notEqual(result.directMutationUsed, true);
    return {node: candidates[0], result};
  }
  async function proof() {
    const value = readFixtureProof ? await readFixtureProof() : JSON.parse(await readFile(proofPath, 'utf8'));
    assert.equal(typeof value.fixtureId, 'string'); assert(value.fixtureId.length >= 16);
    assert(Number.isSafeInteger(value.sequence) && value.sequence >= 0); assert(Number.isSafeInteger(value.processId) && value.processId > 0);
    assert.equal(value.target, target); assert.equal(value.probeOnly, true);
    assert.equal(value.arrangeAuthority, 'DIAGNOSTIC_native_screen_fixture'); support(value, target);
    return value;
  }
  async function menu() {
    if ((await tree()).screenClass.endsWith('UiRecoveryFixtureScreen')) return page('UiRecoveryFixtureScreen');
    await title(); assert.equal((await api('GET', '/v0/player?category=USER')).available, false, 'Recovery fixtures run only after normal world shutdown');
    await openFixture();
    if (target === REFERENCE) {await button([], 'UI Navigation Fixtures'); await page('UiNavigationFixtureScreen');}
    await button([], 'Recovery UI (Fixture)'); return page('UiRecoveryFixtureScreen');
  }
  async function applicable(caseId) {
    const nativeProof = await proof();
    if (support(nativeProof, target)[caseId] === 'AVAILABLE') return true;
    samples.push({case: 'recovery_ui_' + caseId, target, applicable: false, verified: false,
      outcome: 'not_applicable_native_class_absent', nativeAvailability: 'NATIVE_CLASS_NOT_PRESENT',
      nativeScreens: [], semanticActions: [], nativeScreenObserved: false, caseSupport: nativeProof.caseSupport,
      fixtureId: nativeProof.fixtureId, processId: nativeProof.processId,
      actualFaultInduced: false, connectionAttempted: false, worldRepairTested: false,
      fileConversionTested: false, graphicsCompatibilityTested: false});
    return false;
  }
  async function open(caseId) {
    await menu(); const baseline = await proof(), before = await clean(); await button([], NATIVE[caseId][1]);
    const value = await page(NATIVE[caseId][0]);
    const expected = {target, fixtureId: baseline.fixtureId, processId: baseline.processId, sequence: baseline.sequence + 1, caseId};
    const arranged = verifyRecoveryUiProof(await proof(), {...expected, returned: false, callbackAction: 'none'});
    assert.equal(value.screenClass, arranged.nativeClass);
    return {caseId, before, value, expected, arranged};
  }
  async function finish(context, keys) {
    const action = await button(keys), current = await tree();
    const nativeTitleReturn = context.caseId === 'disconnected'
      || (context.caseId === 'datapack_failure' && target === '1.20.1-forge'
        && context.arranged.datapackCancelDestination === 'native_title_screen');
    if (nativeTitleReturn && current.screenClass.endsWith('TitleScreen')) {
      const after = await clean(), arranged = verifyRecoveryUiProof(await proof(), {...context.expected, returned: false, callbackAction: 'none'});
      assert(after.inputDispatchSequence > context.before.inputDispatchSequence);
      samples.push(sample(context, action, after, arranged, current.screenClass)); return;
    }
    const returnedPage = await page('UiRecoveryFixtureScreen'), after = await clean();
    const returned = verifyRecoveryUiProof(await proof(), {...context.expected, returned: true, callbackAction: NATIVE[context.caseId][2]});
    assert(after.inputDispatchSequence > context.before.inputDispatchSequence);
    samples.push(sample(context, action, after, returned, returnedPage.screenClass));
  }
  function sample(context, action, after, returned, returnedScreen) {
    return {case: 'recovery_ui_' + context.caseId, screenClass: context.value.screenClass, nativeScreens: [context.value.screenClass],
      screenIdentity: context.value.screenIdentity, screenRevision: context.value.screenRevision, semanticActions: [action],
      before: context.before, after, arranged: context.arranged, returned, returnedScreen,
      arrangeAuthority: 'DIAGNOSTIC_native_screen_fixture', actAuthority: 'PLAYTEST', semanticTargets: true,
      handwrittenCoordinates: false, directCallbackUsed: false, applicable: true, verified: true,
      businessOperationCoverage: 'native_ui_only_safe_branch',
      connectionAttempted: false, actualFaultInduced: false, worldRepairTested: false, fileConversionTested: false, graphicsCompatibilityTested: false};
  }
  await menu(); await proof();
  await check('UI recovery: available native disconnected, datapack-failure and aborted pages return through semantic safe buttons; absent classes remain not applicable', async () => {
    for (const [caseId, keys] of [['disconnected', ['gui.toMenu', 'gui.toTitle']], ['datapack_failure', [target === '1.20.1-forge' ? 'gui.toTitle' : 'gui.back']], ['file_fixer_aborted', ['gui.back']]]) {
      if (await applicable(caseId)) await finish(await open(caseId), keys);
    }
  });
  await check('UI recovery: available native progress Cancel sets its real cancellation flag before a later-tick return; absent classes remain not applicable', async () => {
    if (await applicable('file_fixer_progress')) await finish(await open('file_fixer_progress'), ['gui.cancel']);
  });
  await check('UI recovery: available native graphics warning returns without a fault or settings change; absent classes remain not applicable', async () => {
    if (await applicable('graphics_warning')) await finish(await open('graphics_warning'), ['gui.back']);
    if (!(await tree()).screenClass.endsWith('TitleScreen')) {
      await page('UiRecoveryFixtureScreen'); await button(['gui.done']);
      if (target === REFERENCE) {await page('UiNavigationFixtureScreen'); await button(['gui.done']);}
      await page('AutomationProbeScreen'); await button([], 'Close Probe'); await page('TitleScreen');
    }
    await clean();
  });
}
