import assert from 'node:assert/strict';
import test from 'node:test';
import {hudDemoActions, validateDemoLaunch} from './hud-demo-actions.mjs';

const targets = ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric'];
const launch = (target = '26.3-neoforge') => ({target, processId: 123, validationOwned: true,
  gameArguments: ['--username', 'Validation', '--demo'],
  instanceDirectory: 'D:/repository/validations/conformance-vanilla_ui/instance/' + target + '/client'});

function nativeContext({target = '26.3-neoforge', normalTitle = false, fakeDraw = false, clippedDraw = false, expired = false,
  entryError, introError, enterWorld = true, changedLease = false, saveFails = false,
  noIntro = false, introWithoutLater = false, duplicateLater = false, foreignIntro = false, stuckIntro = false,
  disabledLater = false, hiddenLater = false} = {}) {
  let screen = 'TitleScreen', inWorld = false, worldExists = false, context = 0;
  let pendingIntro = false, transientFrames = 2, introDismissed = false, introFrames = 0;
  const calls = [], semantic = [], checks = [], samples = [], observations = [];
  const button = (nodeId, translationKey, label, active = true) => ({nodeId, translationKey, label,
    role: 'button', visible: true, active, actions: active ? ['click'] : []});
  const tree = async () => {
    if (pendingIntro && transientFrames-- <= 0) {pendingIntro = false; screen = target.startsWith('1.') ? 'DemoIntroScreen' : 'PopupScreen';}
    const intro = ['DemoIntroScreen', 'PopupScreen'].includes(screen);
    if (intro) introFrames++;
    const value = {dataCategory: 'USER', overlayIdentity: 0, treeTruncated: false, width: 320, height: 240,
    clientTick: observations.length + 1, title: intro ? 'Minecraft Demo Mode' : screen,
    screenClass: screen ? screen === 'PopupScreen' ? 'net.minecraft.client.gui.components.PopupScreen' : 'net.minecraft.client.gui.screens.' + screen : '',
    children: screen === 'TitleScreen' ? normalTitle ? [button('normal', 'menu.singleplayer', 'Singleplayer')]
      : [button('play', 'menu.playdemo', 'Play Demo World'), button('reset', 'menu.resetdemo', 'Reset Demo World', worldExists)]
      : screen === 'PauseScreen' ? [button('save', 'menu.returnToMenu', 'Save and Quit to Title')]
        : intro ? foreignIntro ? [button('foreign', 'gui.ok', 'An unrelated popup')]
          : [button('buy', 'demo.help.buy', 'Buy now'),
            ...(introWithoutLater ? [] : [{...button('later', 'demo.help.later', 'Continue playing', !disabledLater), visible: !hiddenLater}]),
            ...(duplicateLater ? [button('duplicate', 'demo.help.later', 'Continue playing')] : [])] : [],
    presentation: {children: !screen && inWorld ? [{renderPhase: 'demo', role: 'hud_text', visible: true, active: false,
      actions: [], boundsAvailable: true, x: 200, y: 5, width: 110, height: 9, opacity: 1,
      label: clippedDraw ? '' : expired ? "Demo time's up!" : 'Remaining time: 01:40:24',
      labelSource: fakeDraw ? 'cached_demo_text' : 'last_completed_native_gui_text_draw',
      iconSource: 'last_completed_native_gui_draw', textPhase: clippedDraw ? 'withheld_partial_or_occluded' : 'drawn',
      boundsSemantics: 'native_text_draw_viewport_scissor_intersection'}] : []}};
    observations.push({screenClass: value.screenClass, inWorld, introDismissed}); return value;
  };
  const input = () => ({pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false,
    inputGeneration: context, inputContextGeneration: context, inputContextId: 'demo-native-' + context, inputContextAvailable: inWorld});
  const api = async (method, path, body) => {
    calls.push({method, path, body}); assert.equal(method, 'GET');
    if (path === '/v0/input/state') return input();
    if (path === '/v0/control/status') return {mode: 'TAKEOVER', takeoverActive: true,
      leaseId: changedLease && inWorld ? 'changed' : 'same', modeVersion: {controlSessionId: 'control', generation: 1}};
    if (path === '/v0/player?category=USER') return {available: inWorld, dataCategory: 'USER', gameMode: 'survival'};
    if (path === '/v0/session') return {target, inWorld};
    assert.fail('Unexpected API ' + path);
  };
  const finish = () => {if (inWorld) context++; inWorld = false; screen = 'TitleScreen'; pendingIntro = false;};
  const ctx = {target, api, tree, nodes: value => value.children ?? [], samples, launchAttestation: launch(target),
    wait: async (predicate, label) => {for (let poll = 0; poll < 12; poll++) {const value = await tree(); if (predicate(value)) return value;}
      throw new Error('Timeout: ' + label);},
    ui: async ({nodeId}, action = 'click', extra = {}) => {
      assert.equal(action, 'click'); semantic.push({nodeId, action, extra});
      if (nodeId === 'play') {
        if (enterWorld) {inWorld = true; worldExists = true; screen = ''; context++; pendingIntro = !noIntro;}
        if (entryError) throw entryError;
      } else if (nodeId === 'later') {
        assert(['DemoIntroScreen', 'PopupScreen'].includes(screen)); assert(introFrames > 0);
        if (!stuckIntro) {screen = ''; introDismissed = true;}
        if (introError) throw introError;
      } else assert.fail('Do not reset a world, open purchase links or dismiss unrelated pages');
      return {targetingSource: 'interaction_tree', directMutationUsed: false};
    },
    key: async code => {assert.equal(code, 256); assert(inWorld); assert.equal(screen, '');
      assert.equal(introDismissed, true, 'Esc cannot bypass the actual native intro'); screen = 'PauseScreen';},
    title: async () => {finish(); return tree();},
    saveQuit: async quit => {assert.equal(quit.nodeId, 'save'); assert.equal(screen, 'PauseScreen');
      if (saveFails) throw new Error('native save failed'); finish(); return {outcome: 'world_exit_independently_observed', requestReplayed: false};},
    check: async (name, run) => {checks.push(name); await run();}};
  return {ctx, calls, semantic, checks, samples, observations, state: () => ({screen, inWorld, worldExists})};
}

const cancelledEntry = () => Object.assign(new Error('Demo world entry epoch changed'), {httpStatus: 409,
  nativeError: {error: 'CONTROL_INPUT_GENERATION_CHANGED', executionOutcome: 'not_confirmed', retrySafe: false}});

test('each real --demo Target uses its actual intro and unique Later action before native HUD and normal Save and Quit', async () => {
  for (const target of targets) {
  const value = nativeContext({target}); await hudDemoActions(value.ctx);
  assert.equal(value.checks.length, 1); assert.equal(value.samples.length, 1);
  const sample = value.samples[0]; assert.equal(sample.case, 'hud_demo_native_launch'); assert.equal(sample.titleEntry.translationKey, 'menu.playdemo');
  assert.equal(sample.nativeDemoNodes[0].label, 'Remaining time: 01:40:24'); assert.equal(sample.resetClicked, false);
  assert.equal(sample.externalLinkClicked, false); assert.equal(sample.demoModeMutationUsed, false); assert.equal(sample.expiredDemoTested, false);
  assert.deepEqual(value.semantic.map(call => call.nodeId), ['play', 'later']); assert(value.calls.every(call => call.method === 'GET'));
  assert.equal(sample.intro.observed, true); assert.equal(sample.intro.dismissedIndependently, true);
  assert.equal(sample.intro.safeButton.translationKey, 'demo.help.later');
  assert.equal(sample.intro.screenClass, target.startsWith('1.') ? 'net.minecraft.client.gui.screens.DemoIntroScreen'
    : 'net.minecraft.client.gui.components.PopupScreen');
  assert(value.observations.some(frame => frame.inWorld && !frame.screenClass && !frame.introDismissed), 'The mock exposes a transient HUD before native intro');
  assert.equal(sample.demoSaveDirectory, 'Demo_World'); assert.equal(sample.crossRunPersistenceAsserted, false);
  assert.deepEqual(value.state(), {screen: 'TitleScreen', inWorld: false, worldExists: true});
  }
});

test('rejects missing, fake, duplicate or external launch attestation before any Runtime or UI call', async () => {
  for (const change of [value => delete value.gameArguments, value => value.gameArguments = ['--demo=true'],
    value => value.gameArguments.push('--demo'), value => value.validationOwned = false,
    value => value.instanceDirectory = 'D:/repository/instances/26.3-neoforge/client', value => value.target = '26.3-fabric',
    value => value.processId = 0]) {
    const value = nativeContext(); change(value.ctx.launchAttestation);
    await assert.rejects(hudDemoActions(value.ctx), assert.AssertionError);
    assert.equal(value.calls.length, 0); assert.equal(value.semantic.length, 0); assert.equal(value.samples.length, 0);
  }
  assert.throws(() => validateDemoLaunch({isDemo: true}, '26.3-neoforge'));
});

test('an attested launch without the actual Demo Title cannot be accepted or mutated', async () => {
  const value = nativeContext({normalTitle: true}); await assert.rejects(hudDemoActions(value.ctx), /Actual Demo Title entry/);
  assert.equal(value.semantic.length, 0); assert.equal(value.samples.length, 0);
});

test('only a matching epoch cancellation can use independently observed entry without replay or RPC success claims', async () => {
  const value = nativeContext({entryError: cancelledEntry()}); await hudDemoActions(value.ctx);
  assert.equal(value.samples[0].entry.requestOutcome, 'not_confirmed'); assert.equal(value.samples[0].entry.requestReplayed, false);
  assert.equal(value.samples[0].entry.response, undefined); assert.equal(value.semantic.length, 2);
  const missing = nativeContext({enterWorld: false, entryError: cancelledEntry()});
  await assert.rejects(hudDemoActions(missing.ctx), /actual native Demo intro/);
  assert.equal(missing.samples.length, 0); assert.equal(missing.semantic.length, 1);
});

test('private cached text, clipped glyphs, expired worlds and changed Lease cannot prove fresh native Demo coverage', async () => {
  for (const [options, failure] of [[{fakeDraw: true}, assert.AssertionError],
    [{clippedDraw: true}, /actual Demo remaining-time draw/], [{expired: true}, /actual Demo remaining-time draw/],
    [{changedLease: true}, assert.AssertionError]]) {
    const value = nativeContext(options); await assert.rejects(hudDemoActions(value.ctx), failure);
    assert.equal(value.samples.length, 0); assert.equal(value.semantic.length, 2); assert.equal(value.state().screen, 'TitleScreen');
  }
});

test('uncertain submission and a failed native save are never published as success', async () => {
  const failure = new Error('HTTP disconnected after Demo submission');
  const value = nativeContext({entryError: failure}); await assert.rejects(hudDemoActions(value.ctx), error => error === failure);
  assert.equal(value.semantic.length, 1); assert.equal(value.samples.length, 0);
  const save = nativeContext({saveFails: true}); await assert.rejects(hudDemoActions(save.ctx), /native save failed/);
  assert.equal(save.samples.length, 0); assert.equal(save.state().screen, 'TitleScreen');
});

test('no intro, a foreign popup or a missing, duplicate, disabled or hidden Later cannot pass or choose another action', async () => {
  for (const options of [{noIntro: true}, {foreignIntro: true}, {introWithoutLater: true}, {duplicateLater: true},
    {disabledLater: true}, {hiddenLater: true}]) {
    const value = nativeContext(options); await assert.rejects(hudDemoActions(value.ctx),
      options.noIntro || options.foreignIntro ? /actual native Demo intro/ : /One actual safe native Demo Later/);
    assert.deepEqual(value.semantic.map(call => call.nodeId), ['play']); assert.equal(value.samples.length, 0);
  }
});

test('Later precise not-confirmed cancellation is recorded without replay only when the intro independently disappears', async () => {
  const value = nativeContext({introError: cancelledEntry()}); await hudDemoActions(value.ctx);
  assert.equal(value.samples[0].intro.requestOutcome, 'not_confirmed'); assert.equal(value.samples[0].intro.requestReplayed, false);
  assert.equal(value.samples[0].intro.response, undefined); assert.deepEqual(value.semantic.map(call => call.nodeId), ['play', 'later']);
  for (const options of [{stuckIntro: true}, {stuckIntro: true, introError: cancelledEntry()}]) {
    const stuck = nativeContext(options); await assert.rejects(hudDemoActions(stuck.ctx), /actual Demo remaining-time draw/);
    assert.deepEqual(stuck.semantic.map(call => call.nodeId), ['play', 'later']); assert.equal(stuck.samples.length, 0);
  }
});

test('unexpected Later failure or wrong cancellation metadata remains a failure without replay or success evidence', async () => {
  for (const failure of [new Error('HTTP disconnected after Later submission'),
    Object.assign(new Error('unsafe retry metadata'), {httpStatus: 409, nativeError: {error: 'REQUEST_CANCELLED', executionOutcome: 'not_confirmed', retrySafe: true}}),
    Object.assign(new Error('unrelated cancellation'), {httpStatus: 409, nativeError: {error: 'UI_TARGET_INVALIDATED', executionOutcome: 'not_confirmed', retrySafe: false}})]) {
    const value = nativeContext({introError: failure}); await assert.rejects(hudDemoActions(value.ctx), error => error === failure);
    assert.deepEqual(value.semantic.map(call => call.nodeId), ['play', 'later']); assert.equal(value.samples.length, 0);
  }
});
