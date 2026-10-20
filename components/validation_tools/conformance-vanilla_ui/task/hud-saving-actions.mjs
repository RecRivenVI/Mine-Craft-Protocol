import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {validateLateTextDraw} from './hud-late-text-actions.mjs';

const NATIVE_PAUSE = 'net.minecraft.client.gui.screens.PauseScreen';
const ARRANGE = 'DIAGNOSTIC_bounded_native_pause_save_timing';
const MAXIMUM_MILLIS = 30000;
const FORGE_NATIVE_DISPOSITION = 'NOT_RENDERED_BY_DEFAULT_FORGE_GUI';

export function verifyNativePauseSaveProof(value, expected, phase) {
  assert.equal(value.probeOnly, true); assert.equal(value.arrangeAuthority, ARRANGE);
  assert.equal(value.target, expected.target); assert.equal(value.processId, expected.processId, 'Use the actual connected Runtime process');
  assert.equal(value.fixtureId, expected.fixtureId, 'Do not accept another pause-save fixture');
  assert.equal(value.failure, '', 'Native pause-save probe failure: ' + value.failure);
  assert.equal(value.barrierMaximumMillis, 3000); assert.equal(value.barrierTimedOut, false, 'The diagnostic saving-draw barrier timed out');
  assert(Number.isFinite(value.barrierElapsedMillis) && value.barrierElapsedMillis >= 0);
  for (const field of ['savingStateWrittenByProbe', 'indicatorAlphaWrittenByProbe', 'additionalSaveInvokedByProbe',
    'naturalSaveDurationValidated', 'performanceValidated']) assert.equal(value[field], false, field);
  assert.equal(value.worldFilesTouched, 'owned_validation_world_only'); assert.equal(value.persistentWriteApiCalls, 0);
  for (const field of ['armed', 'readyForArm', 'resumeObserved', 'nativeSaveStarted', 'nativeSaveReturned', 'nativeSaveResult',
    'nativeSavingFlagCleared', 'nativeSaveSuppressedLog', 'nativeSaveFlush', 'nativeSaveForced', 'barrierActive',
    'nativeTextDrawObserved', 'drawWhileActualSaving']) assert.equal(typeof value[field], 'boolean', field);
  for (const field of ['nativeSaveSuppressedLog', 'nativeSaveFlush', 'nativeSaveForced']) assert.equal(value[field], false);
  assert(Number.isSafeInteger(value.selectedNativePauseSaveCalls) && value.selectedNativePauseSaveCalls >= 0
    && value.selectedNativePauseSaveCalls <= 1, 'Only one selected native pause save is allowed');
  if (phase === 'prepared') assert.equal(value.armed, false);
  if (phase === 'ready') { assert.equal(value.armed, false); assert.equal(value.readyForArm, true); }
  if (['armed', 'resumed', 'drawn', 'completed'].includes(phase)) assert.equal(value.armed, true);
  if (['resumed', 'drawn', 'completed'].includes(phase)) assert.equal(value.resumeObserved, true);
  if (['prepared', 'ready', 'armed', 'resumed'].includes(phase)) {
    assert.equal(value.selectedNativePauseSaveCalls, 0);
    assert.equal(value.nativeSaveStarted, false); assert.equal(value.nativeSaveReturned, false); assert.equal(value.nativeTextDrawObserved, false);
  }
  if (phase === 'drawn' || phase === 'completed') {
    assert.equal(value.selectedNativePauseSaveCalls, 1); assert.equal(value.nativeSaveStarted, true);
    assert.equal(value.nativeTextDrawObserved, true); assert.equal(value.drawWhileActualSaving, true);
    assert.equal(value.nativeScreenClass, NATIVE_PAUSE);
    assert(typeof value.nativeTextLabel === 'string' && value.nativeTextLabel.includes('Saving world'));
    assert(value.nativeTextOpacity > 0 && value.nativeTextOpacity <= 1);
  }
  if (phase === 'completed') {
    assert.equal(value.nativeSaveReturned, true); assert.equal(value.nativeSavingFlagCleared, true); assert.equal(value.barrierActive, false);
  }
  if (phase === 'not_applicable') {
    assert.equal(expected.target, '1.20.1-forge');
    assert.equal(value.nativeIndicatorDisposition, FORGE_NATIVE_DISPOSITION);
    assert.equal(value.nativeIndicatorSupportSource, 'actual_hud_renderer_and_registered_overlay_ids');
    assert.equal(value.actualHudRenderer, 'net.minecraftforge.client.gui.overlay.ForgeGui');
    for (const field of ['actualRegisteredOverlayIds', 'nativeVanillaOverlayIds']) {
      assert(Array.isArray(value[field]) && value[field].length > 0, 'The actual native overlay registry is required: ' + field);
      assert.equal(new Set(value[field]).size, value[field].length);
      assert(value[field].every(id => typeof id === 'string' && id.startsWith('minecraft:') && !/saving|autosave/.test(id)),
        'A saving or non-default overlay cannot establish the default Forge renderer limitation');
      for (const id of ['minecraft:hotbar', 'minecraft:player_list']) assert(value[field].includes(id));
    }
    assert.deepEqual(value.actualRegisteredOverlayIds, value.nativeVanillaOverlayIds,
      'Only the actual unchanged default Forge overlay registry establishes this limitation');
    assert.equal(value.selectedNativePauseSaveCalls, 0);
    for (const field of ['armed', 'resumeObserved', 'nativeSaveStarted', 'nativeSaveReturned', 'nativeSaveResult',
      'nativeSavingFlagCleared', 'barrierActive', 'nativeTextDrawObserved', 'drawWhileActualSaving']) assert.equal(value[field], false, field);
    assert.equal(value.barrierElapsedMillis, 0); assert.equal(value.nativeScreenClass, '');
    assert.equal(value.nativeTextLabel, ''); assert.equal(value.nativeTextOpacity, 0);
  }
  return value;
}

// The probe only arranges up to three seconds inside one real ordinary pause save.
// Actual USER text, native completion and cleanup are separate proof obligations.
export async function hudSavingActions({target, repo, processId, api, key, tree, wait, nodes, ui, openFixture, check, samples, readFixtureProof},
  {now = () => performance.now(), sleep = milliseconds => new Promise(done => setTimeout(done, milliseconds))} = {}) {
  assert(['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric'].includes(target), 'The native pause-save fixture requires a maintained Target');
  assert(Number.isSafeInteger(processId) && processId > 0, 'The connected Runtime PID is required');
  const path = resolve(repo, 'validations/conformance-vanilla_ui/result', target + '-saving-indicator-probe.json');
  const started = now();
  const healthy = () => assert(now() - started < MAXIMUM_MILLIS, 'Native pause-save UI exceeded its bounded scenario');
  await check(target === '1.20.1-forge'
    ? 'HUD: native saving indicator: actual default Forge HUD registry does not render this native indicator (not applicable, not USER draw PASS)'
    : 'HUD: native saving indicator: one actual pause save draws and fades with explicit diagnostic timing only', async () => {
    let expected, primary, result, fixtureOpened = false, closeProbeAttempted = false, pauseAttempted = false, resumeAttempted = false;
    async function clean() {
      const input = await api('GET', '/v0/input/state');
      assert.equal(input.pressedKeyCount, 0); assert.equal(input.pressedButtonCount, 0);
      assert.equal(input.inputCleanupPending, false); assert.equal(input.inputCleanupFailed, false); return input;
    }
    async function proof() {
      healthy();
      const value = readFixtureProof ? await readFixtureProof() : JSON.parse(await readFile(path, 'utf8'));
      assert.equal(value.target, target); assert.equal(value.processId, processId, 'Use the actual connected Runtime process');
      assert(typeof value.fixtureId === 'string' && value.fixtureId.length >= 16);
      expected ??= {target, processId, fixtureId: value.fixtureId};
      return verifyNativePauseSaveProof(value, expected);
    }
    async function observedProof(phase, predicate, maximumMillis = 5000) {
      const deadline = Math.min(started + MAXIMUM_MILLIS, now() + maximumMillis); let last;
      do {
        last = await proof();
        if (predicate(last)) return verifyNativePauseSaveProof(last, expected, phase);
        await sleep(25);
      } while (now() < deadline);
      assert.fail('Native pause-save probe did not reach ' + phase + ': ' + JSON.stringify(last));
    }
    async function page(predicate, description) {
      const value = await wait(value => {healthy(); return predicate(value);}, description);
      assert.equal(value.dataCategory, 'USER'); assert.equal(value.overlayIdentity, 0); return value;
    }
    async function button(label) {
      const current = await tree(), choices = nodes(current).filter(node => node.label === label
        && node.visible !== false && node.actions?.includes('click'));
      assert.equal(choices.length, 1, 'One actual semantic fixture button: ' + label);
      assert.equal(choices[0].active, true, 'The actual fixture button is disabled: ' + label);
      const outcome = await ui({nodeId: choices[0].nodeId});
      assert.notEqual(outcome.directMutationUsed, true); assert.equal(outcome.targetingSource, 'interaction_tree');
      return {node: choices[0], outcome};
    }
    try {
      const before = await tree(); assert.equal(before.dataCategory, 'USER'); assert.equal(before.screenClass, ''); assert.equal(before.overlayIdentity, 0);
      const inputBefore = await clean();
      await openFixture(); fixtureOpened = true;
      await page(value => value.screenClass.endsWith('AutomationProbeScreen'), 'owned pause-save probe menu');
      const prepared = await observedProof('ready', value => value.readyForArm && !value.armed);
      if (target === '1.20.1-forge') {
        const applicability = verifyNativePauseSaveProof(prepared, expected, 'not_applicable');
        const menu = await tree();
        assert(!menu.presentation?.children?.some(node => node.renderPhase === 'saving_indicator'),
          'An unexpected saving draw must not be hidden as not applicable');
        closeProbeAttempted = true; const close = await button('Close Probe');
        const after = await page(value => !value.screenClass, 'unarmed default Forge HUD observation returns to gameplay');
        assert(!after.presentation?.children?.some(node => node.renderPhase === 'saving_indicator'),
          'An unexpected actual saving-indicator draw must not be hidden as not applicable');
        const inputAfter = await clean();
        result = {case: 'hud_native_saving_indicator_not_applicable', target, category: 'USER',
          disposition: FORGE_NATIVE_DISPOSITION, applicability, draw: [], userDrawValidated: false,
          semanticActions: [close], selectedNativePauseSaveCalls: 0, pauseRequests: 0,
          commandUsed: false, requestReplayed: false, agentEscOnly: true, physicalEscTested: false,
          arrangeAuthority: ARRANGE, actAuthority: 'PLAYTEST', authority: 'actual_native_loader_hud_applicability',
          diagnosticTimingArranged: false, naturalSaveDurationValidated: false, performanceValidated: false,
          persistentWriteApiCalls: 0, inputBefore, inputAfter};
      } else {
      const arranged = await button('Pause Save Fixture');
      const armed = verifyNativePauseSaveProof(await proof(), expected, 'armed');
      closeProbeAttempted = true; const close = await button('Close Probe');
      await page(value => !value.screenClass, 'owned probe returns to real gameplay before the tested pause');
      const resumed = await observedProof('resumed', value => value.resumeObserved);
      await clean();

      pauseAttempted = true;
      let inputFailure;
      const opening = key(256).catch(error => {inputFailure = error; throw error;});
      const observing = (async () => {
        const deadline = Math.min(started + MAXIMUM_MILLIS, now() + 6000); let last, userDraw;
        do {
          healthy(); if (inputFailure) throw inputFailure;
          const [value, native] = await Promise.all([tree(), proof()]); last = value;
          if (value.screenClass === NATIVE_PAUSE && value.overlayIdentity === 0
            && value.presentation?.children?.some(node => node.renderPhase === 'saving_indicator' && node.textPhase === 'drawn' && node.opacity > 0)) {
            const draw = validateLateTextDraw(value, 'saving_indicator');
            assert(draw.some(node => node.label.includes('Saving world')), 'The actual USER saving label must be drawn');
            userDraw = {value, draw};
          }
          if (userDraw && native.nativeTextDrawObserved) {
            assert(userDraw.draw.some(node => node.label === native.nativeTextLabel),
              'The USER draw must independently contain the actual native saving label');
            return {...userDraw, native: verifyNativePauseSaveProof(native, expected, 'drawn')};
          }
          await sleep(25);
        } while (now() < deadline);
        assert.fail('Actual USER native saving-indicator draw was not observed: ' + JSON.stringify(last));
      })();
      const [opened, observed] = await Promise.allSettled([opening, observing]);
      if (opened.status === 'rejected') throw opened.reason;
      if (observed.status === 'rejected') throw observed.reason;
      const saved = await observedProof('completed', value => value.nativeSaveReturned && value.nativeSavingFlagCleared && !value.barrierActive, 8000);
      resumeAttempted = true; await key(256);
      const after = await page(value => !value.screenClass
        && !value.presentation?.children?.some(node => node.renderPhase === 'saving_indicator'), 'actual native pause-save indicator fades out in gameplay');
      const inputAfter = await clean();
      result = {case: 'hud_native_saving_indicator', target, category: 'USER', draw: observed.value.draw,
        screenIdentity: observed.value.value.screenIdentity, screenRevision: observed.value.value.screenRevision,
        afterScreenIdentity: after.screenIdentity, afterScreenRevision: after.screenRevision,
        prepared, armed, resumed, nativeDraw: observed.value.native, nativeCompleted: saved,
        semanticActions: [arranged, close], pauseKey: 256, pauseRequests: 1, selectedNativePauseSaveCalls: 1,
        commandUsed: false, requestReplayed: false, agentEscOnly: true, physicalEscTested: false,
        arrangeAuthority: ARRANGE, actAuthority: 'PLAYTEST', authority: 'completed_native_gui_draw',
        diagnosticTimingArranged: true, naturalSaveDurationValidated: false, performanceValidated: false,
        persistentWriteApiCalls: 0, inputBefore, inputAfter};
      }
    } catch (error) {primary = error; throw error;}
    finally {
      const failures = [];
      for (const cleanup of [
        async () => {await api('POST', '/v0/input/release-all', {});},
        async () => {
          const current = await tree();
          if (current.screenClass === NATIVE_PAUSE && pauseAttempted && !resumeAttempted) {
            resumeAttempted = true; await key(256);
            await page(value => !value.screenClass, 'owned failed pause-save returns safely to gameplay');
          } else if (current.screenClass.endsWith('AutomationProbeScreen') && fixtureOpened && !closeProbeAttempted) {
            closeProbeAttempted = true; await button('Close Probe');
            await page(value => !value.screenClass, 'owned unarmed save fixture closes safely');
          } else assert.equal(current.screenClass, '', 'Do not dismiss an unrelated or unconfirmed Screen');
        }, clean,
      ]) {try {await cleanup();} catch (error) {failures.push(error);}}
      if (failures.length) throw new AggregateError(primary ? [primary, ...failures] : failures,
        (primary ? primary.message + '; ' : '') + 'The owned pause-save cleanup was not fully confirmed', {cause: primary});
    }
    samples.push(result);
  });
}
