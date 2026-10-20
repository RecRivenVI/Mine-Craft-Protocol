import assert from 'node:assert/strict';
import test from 'node:test';
import {recoveryUiActions, verifyRecoveryUiProof} from './ui-recovery-actions.mjs';

const expected = {target: '26.3-neoforge', fixtureId: 'current-recovery-fixture', processId: 71, sequence: 2,
  caseId: 'file_fixer_progress', returned: true, callbackAction: 'native_progress_cancel_observed'};
const proof = () => ({probeOnly: true, arrangeAuthority: 'DIAGNOSTIC_native_screen_fixture', target: expected.target,
  fixtureId: expected.fixtureId, processId: expected.processId, sequence: expected.sequence, case: expected.caseId,
  nativeClass: 'net.minecraft.client.gui.screens.worldselection.FileFixerProgressScreen', returnedToFixture: true,
  callbackAction: expected.callbackAction, actualFaultInduced: false, networkConnectionCreated: false, externalServiceUsed: false,
  worldFilesTouched: false, repairOrConversionStarted: false, graphicsSettingsChanged: false, nativeProgressAvailable: true,
  nativeProgressCancelled: true, nativeCancelButtonDisabledObserved: true, openedNativeTick: 8, cancelObservedNativeTick: 10, returnedNativeTick: 11});

test('accepts the actual native cancellation flag and later-tick return, not a successful conversion', () => {
  const value = proof(); assert.equal(verifyRecoveryUiProof(value, expected), value);
});

test('rejects stale ownership, fake screen classes, direct same-tick return or unproven native cancellation', () => {
  for (const change of [{probeOnly: false}, {target: '26.3-fabric'}, {fixtureId: 'previous-fixture'}, {processId: 72}, {sequence: 1},
    {case: 'unknown'}, {nativeClass: 'io.github.recrivenvi.minecraftprotocol.probe.validation.FakeProgressScreen'},
    {returnedToFixture: false}, {callbackAction: 'cancel_button_clicked'}, {actualFaultInduced: true}, {networkConnectionCreated: true},
    {externalServiceUsed: true}, {worldFilesTouched: true}, {repairOrConversionStarted: true}, {graphicsSettingsChanged: true},
    {nativeProgressAvailable: false}, {nativeProgressCancelled: false}, {nativeCancelButtonDisabledObserved: false},
    {cancelObservedNativeTick: 8}, {returnedNativeTick: 10}, {cancelObservedNativeTick: 10.5}, {returnedNativeTick: Infinity}]) assert.throws(() => verifyRecoveryUiProof({...proof(), ...change}, expected), JSON.stringify(change));
});

async function controlledRecovery(options = {}) {
  const target = options.target ?? '26.3-neoforge';
  const variants = [
    ['disconnected', 'Disconnected Page (Fixture)', 'DisconnectedScreen', 'gui.toMenu', 'none'],
    ['datapack_failure', 'Datapack Failure (Fixture)', 'DatapackLoadFailureScreen', target === '1.20.1-forge' ? 'gui.toTitle' : 'gui.back', 'datapack_cancelled'],
    ['file_fixer_aborted', 'File Fixer Aborted (Fixture)', 'FileFixerAbortedScreen', 'gui.back', 'file_fixer_back'],
    ['file_fixer_progress', 'File Fixer Progress (Fixture)', 'worldselection.FileFixerProgressScreen', 'gui.cancel', 'native_progress_cancel_observed'],
    ['graphics_warning', 'Graphics Warning (Fixture)', 'options.UnsupportedGraphicsWarningScreen', 'gui.back', 'graphics_back'],
  ];
  const available = target === '1.20.1-forge' ? ['disconnected', 'datapack_failure']
    : target === '1.21.1-neoforge' ? ['disconnected', 'datapack_failure', 'graphics_warning'] : variants.map(value => value[0]);
  const caseSupport = Object.fromEntries(variants.map(value => [value[0], available.includes(value[0]) ? 'AVAILABLE' : 'NATIVE_CLASS_NOT_PRESENT']));
  const nativeSupport = {target, caseSupport, datapackCancelDestination: target === '1.20.1-forge' ? 'native_title_screen' : 'fixture_callback'};
  let screen = 'net.minecraft.client.gui.screens.TitleScreen', current, sequence = 0, dispatch = 0, closeProbe = false;
  let actual = {...proof(), ...nativeSupport, sequence: 0, case: '', nativeClass: '', nativeProgressAvailable: false, nativeProgressCancelled: false,
    nativeCancelButtonDisabledObserved: false, openedNativeTick: 0, cancelObservedNativeTick: -1, returnedNativeTick: -1, returnedToFixture: false, callbackAction: 'none'};
  const samples = [], clicks = [], checks = [];
  const button = (nodeId, label, translationKey) => ({nodeId, label, translationKey, role: 'button', visible: true, active: true, actions: ['click']});
  const tree = async () => {
    let children;
    if (screen.endsWith('AutomationProbeScreen')) children = [target === '26.3-neoforge'
      ? button('navigation', 'UI Navigation Fixtures') : button('recovery', 'Recovery UI (Fixture)'), button('close-probe', 'Close Probe')];
    else if (screen.endsWith('UiNavigationFixtureScreen')) children = [button('recovery', 'Recovery UI (Fixture)'), button('navigation-done', 'Done', 'gui.done')];
    else if (screen.endsWith('UiRecoveryFixtureScreen')) children = [...variants.filter(value => available.includes(value[0]) || options.exposeAbsentButtons)
      .map(value => button(value[0], value[1])), button('recovery-done', 'Done', 'gui.done')];
    else children = current ? [button('safe-native-return', 'Native safe return', current[3])] : [];
    return {screenClass: screen, screenIdentity: sequence + 1, screenRevision: dispatch + 1, dataCategory: 'USER', treeTruncated: false, children};
  };
  await recoveryUiActions({repo: '.', target, samples, tree, nodes: value => value.children,
    title: async () => {screen = 'net.minecraft.client.gui.screens.TitleScreen';},
    openFixture: async () => {screen = 'io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen';},
    readFixtureProof: async () => ({...structuredClone(actual), ...options.proofChange}),
    api: async (method, path) => {
      assert.equal(method, 'GET'); if (path === '/v0/player?category=USER') return {available: false};
      assert.equal(path, '/v0/input/state'); return {pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false, inputDispatchSequence: dispatch};
    },
    wait: async (predicate, label) => {const value = await tree(); assert(predicate(value), label); return value;},
    ui: async selector => {
      assert.deepEqual(Object.keys(selector), ['nodeId']); clicks.push(selector.nodeId); dispatch++;
      if (selector.nodeId === 'navigation') screen = 'io.github.recrivenvi.minecraftprotocol.probe.validation.UiNavigationFixtureScreen';
      else if (selector.nodeId === 'recovery') screen = 'io.github.recrivenvi.minecraftprotocol.probe.validation.UiRecoveryFixtureScreen';
      else if (selector.nodeId === 'recovery-done') screen = target === '26.3-neoforge'
        ? 'io.github.recrivenvi.minecraftprotocol.probe.validation.UiNavigationFixtureScreen'
        : 'io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen';
      else if (selector.nodeId === 'navigation-done') screen = 'io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen';
      else if (selector.nodeId === 'close-probe') {screen = 'net.minecraft.client.gui.screens.TitleScreen'; closeProbe = true;}
      else if (selector.nodeId === 'safe-native-return') {
        const nativeTitle = (current[0] === 'disconnected' && options.disconnectedToTitle)
          || (current[0] === 'datapack_failure' && target === '1.20.1-forge');
        actual.returnedToFixture = !nativeTitle; actual.callbackAction = nativeTitle ? 'none' : current[4];
        if (nativeTitle && options.titleCallback) actual.callbackAction = options.titleCallback;
        if (current[0] === 'file_fixer_progress') {
          actual.nativeProgressCancelled = true; actual.nativeCancelButtonDisabledObserved = true;
          actual.cancelObservedNativeTick = actual.openedNativeTick + 2; actual.returnedNativeTick = actual.cancelObservedNativeTick + 1;
        }
        screen = nativeTitle ? 'net.minecraft.client.gui.screens.TitleScreen'
          : 'io.github.recrivenvi.minecraftprotocol.probe.validation.UiRecoveryFixtureScreen';
      } else {
        current = variants.find(value => value[0] === selector.nodeId); assert(current); sequence++;
        screen = 'net.minecraft.client.gui.screens.' + current[2];
        assert(available.includes(current[0]), 'An absent native class must never be arranged');
        actual = {...proof(), ...nativeSupport, sequence, case: current[0], nativeClass: screen, openedNativeTick: sequence + 10, returnedToFixture: false,
          callbackAction: 'none', nativeProgressAvailable: current[0] === 'file_fixer_progress', nativeProgressCancelled: false,
          nativeCancelButtonDisabledObserved: false, cancelObservedNativeTick: -1, returnedNativeTick: -1};
      }
      return {directMutationUsed: false, targetingSource: 'interaction_tree'};
    }, check: async (name, run) => {await run(); checks.push(name);},
  });
  return {samples, clicks, checks, closeProbe, screen, variants, available};
}

test('the actual helper uses five real native classes and safe semantic returns, keeping repair and network claims false', async () => {
  const {checks, samples, clicks, closeProbe, variants} = await controlledRecovery();
  assert.equal(checks.length, 3); assert.equal(samples.length, 5); assert.equal(closeProbe, true);
  assert.equal(clicks.filter(value => value === 'safe-native-return').length, 5);
  assert.deepEqual(samples.map(value => value.screenClass), variants.map(value => 'net.minecraft.client.gui.screens.' + value[2]));
  assert(samples.every(value => value.semanticTargets && !value.directCallbackUsed && !value.worldRepairTested && !value.fileConversionTested && !value.connectionAttempted));
  const nativeProgress = samples.find(value => value.case === 'recovery_ui_file_fixer_progress');
  assert.equal(nativeProgress.arranged.nativeProgressCancelled, false); assert.equal(nativeProgress.returned.nativeProgressCancelled, true);
  assert.equal(nativeProgress.returned.nativeCancelButtonDisabledObserved, true);
});

test('the actual helper exercises all four non-reference native fixtures through independent entries without claiming absent pages', async () => {
  for (const target of ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-fabric']) {
    const result = await controlledRecovery({target, exposeAbsentButtons: true});
    assert.equal(result.checks.length, 3); assert.equal(result.samples.length, 5);
    assert.equal(result.clicks.includes('navigation'), false);
    const verified = result.samples.filter(value => value.verified === true);
    assert.equal(verified.length, result.available.length);
    assert.deepEqual(verified.map(value => value.case), result.available.map(value => 'recovery_ui_' + value));
    for (const sample of result.samples.filter(value => !value.verified)) {
      assert.equal(sample.applicable, false); assert.equal(sample.nativeScreenObserved, false);
      assert.equal(sample.outcome, 'not_applicable_native_class_absent'); assert.deepEqual(sample.nativeScreens, []);
      assert.equal(result.clicks.includes(sample.case.slice('recovery_ui_'.length)), false);
    }
    if (target === '1.20.1-forge') {
      const datapack = result.samples.find(value => value.case === 'recovery_ui_datapack_failure');
      assert.equal(datapack.returnedScreen, 'net.minecraft.client.gui.screens.TitleScreen');
      assert.equal(datapack.returned.returnedToFixture, false); assert.equal(datapack.returned.callbackAction, 'none');
    }
    assert(result.screen.endsWith('TitleScreen'));
  }
});

test('native disconnected title returns are independently observed and never claimed to have returned to the fixture', async () => {
  for (const target of ['26.3-neoforge', '1.21.1-neoforge']) {
    const {samples} = await controlledRecovery({target, disconnectedToTitle: true});
    const disconnected = samples.find(value => value.case === 'recovery_ui_disconnected');
    assert(disconnected.returnedScreen.endsWith('TitleScreen'));
    assert.equal(disconnected.returned.returnedToFixture, false); assert.equal(disconnected.returned.callbackAction, 'none');
  }
});

test('the actual cross-target helper rejects missing, invented or mismatched support instead of silently skipping a required page', async () => {
  for (const proofChange of [{caseSupport: undefined}, {caseSupport: {}},
    {caseSupport: {disconnected: 'AVAILABLE', datapack_failure: 'AVAILABLE', file_fixer_aborted: 'AVAILABLE', file_fixer_progress: 'AVAILABLE', graphics_warning: 'AVAILABLE'}},
    {datapackCancelDestination: 'fixture_callback'}]) {
    await assert.rejects(controlledRecovery({target: '1.20.1-forge', proofChange}));
  }
  await assert.rejects(controlledRecovery({target: '1.20.1-forge', titleCallback: 'datapack_cancelled'}));
  await assert.rejects(controlledRecovery({target: 'unknown-target'}));
});
