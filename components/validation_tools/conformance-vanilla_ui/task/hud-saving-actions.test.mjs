import assert from 'node:assert/strict';
import test from 'node:test';
import {readFile} from 'node:fs/promises';
import {hudSavingActions, verifyNativePauseSaveProof} from './hud-saving-actions.mjs';

const TARGET = '26.3-neoforge';
const PID = 7;
const PAUSE = 'net.minecraft.client.gui.screens.PauseScreen';
const PROBE = 'io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen';

test('every actual saving probe publishes complete same-directory records by atomic replacement without a non-atomic fallback', async () => {
  for (const target of ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric']) {
    const source = await readFile(new URL(`../../../../versions/${target}/src/probe/java/io/github/recrivenvi/minecraftprotocol/probe/validation/UiSavingIndicatorFixture.java`, import.meta.url), 'utf8');
    assert.match(source, /private synchronized void report\(\)/, target);
    assert.match(source, /Path destination = directory\.resolve\(TARGET \+ "-saving-indicator-probe\.json"\);/, target);
    assert.match(source, /Path temporary = directory\.resolve\(TARGET \+ "-saving-indicator-probe\.pending"\);/, target);
    const moves = [...source.matchAll(/Files\.move\(([^;]+)\);/gs)];
    assert.equal(moves.length, 1, target);
    assert.equal(moves[0][1].replace(/\s+/g, ''), 'temporary,destination,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING', target);
    assert.match(source, /publish\(temporary, destination\);/, target);
    assert.match(source, /catch \(AccessDeniedException busy\)/, target);
    assert.match(source, /if \(attempt == 7\) throw busy;/, target);
    assert.match(source, /Thread\.sleep\(10\);/, target);
    assert.match(source, /Thread\.currentThread\(\)\.interrupt\(\);/, target);
  }
});

function preparedProof(target = TARGET) {
  return {probeOnly: true, arrangeAuthority: 'DIAGNOSTIC_bounded_native_pause_save_timing',
    target, processId: PID, fixtureId: 'owned-native-pause-save-fixture',
    armed: false, readyForArm: true, resumeObserved: false, selectedNativePauseSaveCalls: 0,
    nativeSaveStarted: false, nativeSaveReturned: false, nativeSaveResult: false, nativeSavingFlagCleared: false,
    nativeSaveSuppressedLog: false, nativeSaveFlush: false, nativeSaveForced: false,
    barrierMaximumMillis: 3000, barrierActive: false, barrierTimedOut: false, barrierElapsedMillis: 0,
    nativeTextDrawObserved: false, drawWhileActualSaving: false, nativeTextLabel: '', nativeTextOpacity: 0,
    nativeScreenClass: '', failure: '', savingStateWrittenByProbe: false, indicatorAlphaWrittenByProbe: false,
    additionalSaveInvokedByProbe: false, naturalSaveDurationValidated: false, performanceValidated: false,
    worldFilesTouched: 'owned_validation_world_only', persistentWriteApiCalls: 0,
    ...(target === '1.20.1-forge' ? {actualHudRenderer: 'net.minecraftforge.client.gui.overlay.ForgeGui',
      actualRegisteredOverlayIds: ['minecraft:hotbar', 'minecraft:player_list'],
      nativeVanillaOverlayIds: ['minecraft:hotbar', 'minecraft:player_list'],
      nativeIndicatorDisposition: 'NOT_RENDERED_BY_DEFAULT_FORGE_GUI',
      nativeIndicatorSupportSource: 'actual_hud_renderer_and_registered_overlay_ids'} : {})};
}

function nativeDraw({hiddenSource = false} = {}) {
  return {nodeId: 'native-save', renderPhase: 'saving_indicator', role: 'hud_text', label: 'Saving world',
    visible: true, active: false, actions: [], boundsAvailable: true, x: 540, y: 346, width: 80, height: 9,
    opacity: .2, textPhase: 'drawn', labelSource: 'last_completed_native_gui_text_draw',
    iconSource: 'last_completed_native_gui_draw', boundsSemantics: 'native_text_draw_viewport_scissor_intersection',
    ...(hiddenSource ? {resourceVersion: {revision: 3}} : {})};
}

// Controlled consumers exercise the real helper, not a game or a substitute Live result.
function runtime({drawn = true, nativeTextObserved = true, hiddenSource = false, disabled = false,
  timedOut = false, saveFailure = '', completes = true, flagCleared = true, foreignPid = false,
  foreignOwner = false, inputError = false, cleanupKeyError = false, releaseError = false, publicationLag = false,
  target = TARGET, unexpectedDraw = false} = {}) {
  const calls = [], checks = [], samples = [], proof = preparedProof(target);
  let screen = '', clock = 0, fadeReads = 0, pauseRequests = 0, armRequests = 0, pausedProofReads = 0;
  const view = () => {
    const value = {dataCategory: 'USER', screenClass: screen, overlayIdentity: 0,
      screenIdentity: screen === PROBE ? 1 : screen === PAUSE ? 2 : 0, screenRevision: 4,
      width: 640, height: 360, treeTruncated: false, children: [], presentation: {children: []}};
    if (screen === PROBE) value.children = [
      {nodeId: 'arm', label: 'Pause Save Fixture', role: 'button', visible: true, active: !disabled && !proof.armed, actions: ['click']},
      {nodeId: 'close', label: 'Close Probe', role: 'button', visible: true, active: true, actions: ['click']},
    ];
    if (unexpectedDraw || (drawn && ((screen === PAUSE && pauseRequests > 0) || (!screen && fadeReads-- > 0))))
      value.presentation.children.push(nativeDraw({hiddenSource}));
    return value;
  };
  const ctx = {target, repo: '.', processId: PID, samples,
    tree: async () => {calls.push({kind: 'tree'}); return view();},
    nodes: value => value.children,
    openFixture: async () => {calls.push({kind: 'openFixture'}); screen = PROBE;},
    readFixtureProof: async () => {
      calls.push({kind: 'proof'});
      const value = structuredClone(proof);
      if (foreignPid) value.processId = PID + 1;
      if (foreignOwner && pauseRequests) value.fixtureId = 'another-native-pause-save-fixture';
      if (publicationLag && screen === PAUSE && pausedProofReads++ === 0) Object.assign(value,
        {nativeTextDrawObserved: false, nativeTextLabel: '', drawWhileActualSaving: false,
          nativeSaveReturned: false, nativeSavingFlagCleared: false, barrierActive: true});
      return value;
    },
    wait: async (predicate, description) => {
      const deadline = clock + 20000;
      do {const value = view(); if (predicate(value)) return value; clock += 25;} while (clock < deadline);
      throw new Error('Timeout: ' + description);
    },
    api: async (method, path, body) => {
      calls.push({kind: 'api', method, path, body});
      if (method === 'GET' && path === '/v0/input/state')
        return {pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false};
      if (method === 'POST' && path === '/v0/input/release-all') {
        assert.deepEqual(body, {}); if (releaseError) throw new Error('release cleanup failed'); return {type: 'input.release_all'};
      }
      assert.fail('Unexpected API ' + method + ' ' + path);
    },
    key: async code => {
      calls.push({kind: 'key', code}); assert.equal(code, 256);
      if (!screen) {
        pauseRequests++; screen = PAUSE;
        Object.assign(proof, {selectedNativePauseSaveCalls: 1, nativeSaveStarted: true,
          nativeSaveReturned: completes, nativeSaveResult: true, nativeSavingFlagCleared: flagCleared,
          nativeTextDrawObserved: nativeTextObserved, drawWhileActualSaving: nativeTextObserved,
          nativeTextLabel: nativeTextObserved ? 'Saving world' : '', nativeTextOpacity: nativeTextObserved ? .2 : 0,
          nativeScreenClass: nativeTextObserved ? PAUSE : '', barrierActive: false,
          barrierElapsedMillis: timedOut ? 3000 : 120, barrierTimedOut: timedOut, failure: saveFailure});
        if (inputError) throw new Error('pause input failed after submission');
      } else if (screen === PAUSE) {
        if (cleanupKeyError) throw new Error('resume cleanup failed');
        screen = ''; fadeReads = 2;
      } else assert.fail('Do not Esc an unrelated Screen');
    },
    ui: async selector => {
      calls.push({kind: 'ui', selector}); assert.deepEqual(Object.keys(selector), ['nodeId']); assert.equal(screen, PROBE);
      if (selector.nodeId === 'arm') {
        assert.equal(disabled, false); armRequests++; proof.armed = true; proof.readyForArm = false;
      } else if (selector.nodeId === 'close') {
        screen = ''; if (proof.armed) proof.resumeObserved = true;
      } else assert.fail('Unexpected semantic target');
      return {directMutationUsed: false, targetingSource: 'interaction_tree'};
    },
    check: async (name, run) => {assert.match(name, /^HUD: native saving indicator/); await run(); checks.push(name);},
  };
  return {ctx, calls, checks, samples, proof, clock: {now: () => clock, sleep: async delay => {clock += delay;}},
    state: () => ({screen, pauseRequests, armRequests, clock})};
}

test('one semantic arm, actual pause save and independent USER draw precede native completion, fade and clean input', async () => {
  const run = runtime(); await hudSavingActions(run.ctx, run.clock);
  assert.equal(run.checks.length, 1); assert.equal(run.samples.length, 1);
  assert.equal(run.state().armRequests, 1); assert.equal(run.state().pauseRequests, 1); assert.equal(run.state().screen, '');
  assert.deepEqual(run.calls.filter(call => call.kind === 'key').map(call => call.code), [256, 256]);
  assert.deepEqual(run.calls.filter(call => call.kind === 'ui').map(call => call.selector.nodeId), ['arm', 'close']);
  const sample = run.samples[0];
  assert.equal(sample.nativeCompleted.nativeSaveReturned, true); assert.equal(sample.draw[0].renderPhase, 'saving_indicator');
  assert.equal(sample.diagnosticTimingArranged, true); assert.equal(sample.naturalSaveDurationValidated, false);
  assert.equal(sample.performanceValidated, false); assert.equal(sample.commandUsed, false); assert.equal(sample.requestReplayed, false);
  assert.equal(sample.selectedNativePauseSaveCalls, 1); assert.equal(sample.physicalEscTested, false);
  assert.equal(sample.persistentWriteApiCalls, 0);
  assert(run.calls.some(call => call.path === '/v0/input/release-all'));
  assert(!run.calls.some(call => /command|debug|acquire|reconsent/.test(call.path ?? '')));
});

test('a native probe draw cannot replace a missing independent USER draw, and input is never replayed', async () => {
  const run = runtime({drawn: false});
  await assert.rejects(hudSavingActions(run.ctx, run.clock), /Actual USER native saving-indicator draw/);
  assert.equal(run.samples.length, 0); assert.equal(run.state().pauseRequests, 1); assert.equal(run.state().armRequests, 1);
  assert.equal(run.state().screen, ''); assert.equal(run.checks.length, 0);
});

test('USER text cannot replace a missing actual native saving callback', async () => {
  const run = runtime({nativeTextObserved: false});
  await assert.rejects(hudSavingActions(run.ctx, run.clock), /Actual USER native saving-indicator draw/);
  assert.equal(run.samples.length, 0); assert.equal(run.state().pauseRequests, 1); assert.equal(run.state().screen, '');
});

test('the independent HTTP frame may precede publication of its native probe record without inventing either proof', async () => {
  const run = runtime({publicationLag: true}); await hudSavingActions(run.ctx, run.clock);
  assert.equal(run.samples.length, 1); assert.equal(run.samples[0].nativeDraw.nativeTextDrawObserved, true);
  assert.equal(run.state().pauseRequests, 1);
});

test('a disabled semantic fixture is rejected without arming or pausing, then its owned menu closes', async () => {
  const run = runtime({disabled: true});
  await assert.rejects(hudSavingActions(run.ctx, run.clock), /fixture button is disabled/);
  assert.equal(run.state().armRequests, 0); assert.equal(run.state().pauseRequests, 0); assert.equal(run.state().screen, '');
  assert.equal(run.samples.length, 0);
});

test('a three-second native barrier timeout fails without rearming or replaying the save', async () => {
  const run = runtime({timedOut: true, drawn: false, nativeTextObserved: false});
  await assert.rejects(hudSavingActions(run.ctx, run.clock), /saving-draw barrier timed out/);
  assert.equal(run.state().armRequests, 1); assert.equal(run.state().pauseRequests, 1); assert.equal(run.state().screen, '');
  assert.equal(run.samples.length, 0);
});

test('native save failure and incomplete native completion are not successful saves', async () => {
  for (const options of [{saveFailure: 'actual_native_save_failure'}, {completes: false}, {flagCleared: false}]) {
    const run = runtime(options);
    await assert.rejects(hudSavingActions(run.ctx, run.clock), /actual_native_save_failure|did not reach completed/);
    assert.equal(run.samples.length, 0); assert.equal(run.state().pauseRequests, 1); assert.equal(run.state().screen, '');
  }
});

test('actual Runtime PID and fixture identity are bound, not merely an internally consistent report', async () => {
  const wrongPid = runtime({foreignPid: true});
  await assert.rejects(hudSavingActions(wrongPid.ctx, wrongPid.clock), /actual connected Runtime process/);
  assert.equal(wrongPid.state().armRequests, 0); assert.equal(wrongPid.state().pauseRequests, 0);
  const wrongOwner = runtime({foreignOwner: true});
  await assert.rejects(hudSavingActions(wrongOwner.ctx, wrongOwner.clock), /another pause-save fixture/);
  assert.equal(wrongOwner.state().pauseRequests, 1); assert.equal(wrongOwner.samples.length, 0);
});

test('hidden revision data is not accepted as USER saving-indicator evidence', async () => {
  const run = runtime({hiddenSource: true});
  await assert.rejects(hudSavingActions(run.ctx, run.clock), /resourceVersion/);
  assert.equal(run.samples.length, 0); assert.equal(run.state().pauseRequests, 1); assert.equal(run.state().screen, '');
});

test('a failed input request is preserved even after a possible native pause, with no second opening attempt', async () => {
  const run = runtime({inputError: true});
  await assert.rejects(hudSavingActions(run.ctx, run.clock), /pause input failed after submission/);
  assert.equal(run.samples.length, 0); assert.equal(run.state().pauseRequests, 1); assert.equal(run.state().screen, '');
});

test('primary observation and cleanup failures are both retained, never replaced by a successful sample', async () => {
  const run = runtime({drawn: false, cleanupKeyError: true, releaseError: true});
  await assert.rejects(hudSavingActions(run.ctx, run.clock), error => error instanceof AggregateError
    && error.errors.some(cause => /Actual USER native saving-indicator draw/.test(cause.message))
    && error.errors.some(cause => /resume cleanup failed/.test(cause.message))
    && error.errors.some(cause => /release cleanup failed/.test(cause.message)));
  assert.equal(run.samples.length, 0); assert.equal(run.state().pauseRequests, 1); assert.equal(run.checks.length, 0);
});

test('diagnostic arrangement must not claim player-natural time, alter saving state or invoke an extra save', () => {
  const expected = {target: TARGET, processId: PID, fixtureId: preparedProof().fixtureId};
  for (const field of ['naturalSaveDurationValidated', 'performanceValidated', 'savingStateWrittenByProbe',
    'indicatorAlphaWrittenByProbe', 'additionalSaveInvokedByProbe']) {
    assert.throws(() => verifyNativePauseSaveProof({...preparedProof(), [field]: true}, expected, 'ready'), new RegExp(field));
  }
  assert.throws(() => verifyNativePauseSaveProof({...preparedProof(), selectedNativePauseSaveCalls: 2}, expected), /Only one/);
  assert.throws(() => verifyNativePauseSaveProof({...preparedProof(), persistentWriteApiCalls: 1}, expected));
});

test('an unknown Target is rejected before any fixture or input request', async () => {
  const run = runtime(); run.ctx.target = 'unknown-target';
  await assert.rejects(hudSavingActions(run.ctx, run.clock), /maintained Target/);
  assert.equal(run.calls.length, 0); assert.equal(run.checks.length, 0); assert.equal(run.samples.length, 0);
});

test('the actual default Forge HUD limitation is a separate unarmed sample, never a USER draw or selected save PASS', async () => {
  const run = runtime({target: '1.20.1-forge'}); await hudSavingActions(run.ctx, run.clock);
  assert.equal(run.checks.length, 1); assert.match(run.checks[0], /not applicable, not USER draw PASS/);
  assert.equal(run.samples.length, 1); const sample = run.samples[0];
  assert.equal(sample.case, 'hud_native_saving_indicator_not_applicable');
  assert.equal(sample.disposition, 'NOT_RENDERED_BY_DEFAULT_FORGE_GUI');
  assert.equal(sample.userDrawValidated, false); assert.deepEqual(sample.draw, []);
  assert.equal(sample.selectedNativePauseSaveCalls, 0); assert.equal(sample.pauseRequests, 0);
  assert.equal(sample.diagnosticTimingArranged, false);
  assert.equal(run.state().armRequests, 0); assert.equal(run.state().pauseRequests, 0); assert.equal(run.state().screen, '');
  assert.equal(run.calls.filter(call => call.kind === 'key').length, 0);
  assert.deepEqual(run.calls.filter(call => call.kind === 'ui').map(call => call.selector.nodeId), ['close']);
});

test('Forge not-applicable requires the actual renderer and complete unchanged registry, not a Target-name skip', async () => {
  for (const mutate of [
    proof => {delete proof.actualHudRenderer;},
    proof => {proof.actualHudRenderer = 'net.minecraft.client.gui.Gui';},
    proof => {proof.actualRegisteredOverlayIds = [];},
    proof => {proof.actualRegisteredOverlayIds.push('minecraft:saving_indicator');},
    proof => {proof.actualRegisteredOverlayIds.push('another_mod:overlay');},
    proof => {proof.nativeVanillaOverlayIds.push('minecraft:title_text');},
    proof => {proof.nativeIndicatorDisposition = 'UNVERIFIED_RENDER_PATH';},
    proof => {proof.selectedNativePauseSaveCalls = 1; proof.nativeSaveStarted = true;},
    proof => {proof.barrierTimedOut = true;},
  ]) {
    const run = runtime({target: '1.20.1-forge'}); mutate(run.proof);
    await assert.rejects(hudSavingActions(run.ctx, run.clock));
    assert.equal(run.samples.length, 0); assert.equal(run.state().armRequests, 0); assert.equal(run.state().pauseRequests, 0);
    assert.equal(run.state().screen, '');
  }
});

test('an unexpected USER saving draw cannot be relabeled as the Forge native limitation', async () => {
  const run = runtime({target: '1.20.1-forge', unexpectedDraw: true});
  await assert.rejects(hudSavingActions(run.ctx, run.clock), /unexpected saving draw/);
  assert.equal(run.samples.length, 0); assert.equal(run.state().armRequests, 0); assert.equal(run.state().screen, '');
});

test('the other maintained Targets still require an actual USER draw and one native pause save', async () => {
  for (const target of ['1.21.1-neoforge', '26.1.2-neoforge', '26.3-fabric']) {
    const run = runtime({target}); await hudSavingActions(run.ctx, run.clock);
    assert.equal(run.samples[0].case, 'hud_native_saving_indicator');
    assert.equal(run.samples[0].selectedNativePauseSaveCalls, 1); assert.equal(run.state().pauseRequests, 1);
    const noDraw = runtime({target, drawn: false});
    await assert.rejects(hudSavingActions(noDraw.ctx, noDraw.clock), /Actual USER native saving-indicator draw/);
    assert.equal(noDraw.samples.length, 0);
  }
});
