import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';

const NATIVE = 'net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen';
const MAXIMUM_MILLIS = 90000;
const SUPPORTED_TARGETS = new Set(['1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric']);

function owned(proof, expected) {
  assert.equal(proof.probeOnly, true); assert.equal(proof.arrangeAuthority, 'DIAGNOSTIC_native_reconfiguration');
  assert.equal(proof.target, expected.target); assert.equal(proof.fixtureId, expected.fixtureId); assert.equal(proof.processId, expected.processId);
  assert.equal(proof.sourceWorldName, 'UI-Conformance');
  for (const field of ['immutableBaselineTouched', 'dailyInstancesTouched', 'externalServerUsed', 'serverConfigStartInvokedByProbe',
    'configurationTaskInjected', 'customProtocolPacketsSent', 'configurationSuccessClaimed']) assert.equal(proof[field], false, field);
  assert.equal(proof.worldFilesTouched, 'owned_validation_working_world');
  assert.equal(proof.failure, undefined, 'The native fixture must not report a preparation or cleanup failure');
}

export function verifyReconfigurationProof(proof, expected, phase) {
  owned(proof, expected); assert.equal(proof.sequence, expected.sequence);
  assert.equal(proof.switchRequested, true); assert.equal(proof.switchReturned, true); assert.equal(proof.serverOwnerThread, true);
  assert.equal(proof.singlePlayerOnly, true); assert.equal(proof.unpublished, true); assert.equal(proof.configurationAcknowledged, true);
  assert.equal(proof.nativeClass, NATIVE); assert.equal(proof.nativeScreenObserved, true); assert.equal(proof.connectionIdentityBound, true);
  assert.equal(proof.disconnectInitiallyDisabled, true);
  assert(Number.isSafeInteger(proof.firstNativeDelayTicker) && proof.firstNativeDelayTicker >= 0 && proof.firstNativeDelayTicker < 600);
  if (phase === 'ready' || phase === 'closed') {
    assert.equal(proof.disconnectReadyObserved, true); assert(Number.isSafeInteger(proof.readyNativeDelayTicker) && proof.readyNativeDelayTicker >= 600);
  }
  if (phase === 'closed') {
    for (const field of ['nativeDisconnectHandlerReturned', 'nativeClientConnectionClosed', 'nativeServerConnectionClosed',
      'integratedServerStopped', 'sourceDirectoryLockReleased', 'playerAbsent', 'connectionAbsent']) assert.equal(proof[field], true, field);
  }
  return proof;
}

// Exercises the real stalled reconfiguration UI only: the probe requests native
// switchToConfig, never starts configuration itself and never fabricates a server.
export async function reconfigurationUiActions({api, repo, target, tree, wait, nodes, ui, title, enterWorld, openFixture, check, samples, readFixtureProof},
  {now = () => performance.now(), sleep = milliseconds => new Promise(done => setTimeout(done, milliseconds))} = {}) {
  assert(SUPPORTED_TARGETS.has(target), 'The target must have the reviewed native configuration protocol; Forge 1.20.1 is not applicable');
  const proofPath = resolve(repo, 'validations/conformance-vanilla_ui/result', target + '-reconfiguration-ui-probe.json');
  const started = now(); let expected, primary, cleanupFailure, startedNative = false, disconnectAttempted = false, closed = false;
  const healthy = () => assert(now() - started < MAXIMUM_MILLIS, 'Native UI reconfiguration exceeded its bounded 90-second scenario');
  async function proof() {healthy(); return readFixtureProof ? readFixtureProof() : JSON.parse(await readFile(proofPath, 'utf8'));}
  async function clean() {
    const value = await api('GET', '/v0/input/state'); healthy(); assert.equal(value.pressedKeyCount, 0); assert.equal(value.pressedButtonCount, 0);
    assert.equal(value.inputCleanupPending, false); assert.equal(value.inputCleanupFailed, false); return value;
  }
  async function page(suffix) {
    const value = await wait(value => value.screenClass.endsWith(suffix), 'Actual native UI reconfiguration ' + suffix); healthy();
    assert.equal(value.dataCategory, 'USER'); assert.equal(value.treeTruncated, false); return value;
  }
  function select(value, key, label) {
    const found = nodes(value).filter(node => node.visible && (key ? node.translationKey === key : node.label === label));
    assert.equal(found.length, 1, 'One actual semantic native target: ' + (key || label)); return found[0];
  }
  async function button(key, label) {
    const node = select(await tree(), key, label); assert.equal(node.active, true); assert(node.actions?.includes('click'));
    const outcome = await ui({nodeId: node.nodeId}); assert.notEqual(outcome.directMutationUsed, true); healthy(); return {node, outcome};
  }
  async function boundaryClick(node) {
    assert.equal(node.active, true); assert(node.actions?.includes('click'));
    try {
      const outcome = await ui({nodeId: node.nodeId}); assert.notEqual(outcome.directMutationUsed, true);
      return {nodeId: node.nodeId, outcome, requestOutcome: 'response_received', requestReplayed: false};
    }
    catch (error) {
      const body = error.nativeError;
      assert.equal(error.httpStatus, 409);
      assert(['REQUEST_CANCELLED', 'CONTROL_INPUT_GENERATION_CHANGED'].includes(body?.error));
      assert.equal(body.executionOutcome, 'not_confirmed'); assert.equal(body.retrySafe, false);
      return {nodeId: node.nodeId, requestOutcome: 'not_confirmed', runtimeError: body, requestReplayed: false};
    }
  }
  async function waitClosed() {
    const deadline = Math.min(started + MAXIMUM_MILLIS, now() + 15000); let last;
    do {
      last = await proof(); owned(last, expected);
      if (last.nativeClientConnectionClosed && last.nativeServerConnectionClosed && last.integratedServerStopped && last.sourceDirectoryLockReleased) {
        verifyReconfigurationProof(last, expected, 'closed'); closed = true; return last;
      }
      await sleep(100);
    } while (now() < deadline);
    assert.fail('Native disconnect did not prove owned connection/server/lock cleanup: ' + JSON.stringify(last));
  }
  async function observedProof(phase) {
    const deadline = Math.min(started + MAXIMUM_MILLIS, now() + 5000); let last;
    do {
      last = await proof(); owned(last, expected); assert.equal(last.sequence, expected.sequence);
      if (last.switchReturned && last.configurationAcknowledged && last.nativeScreenObserved
        && (phase !== 'ready' || last.disconnectReadyObserved)) return verifyReconfigurationProof(last, expected, phase);
      await sleep(25);
    } while (now() < deadline);
    assert.fail('Native configuration ACK or UI observer did not reach its publication point: ' + JSON.stringify(last));
  }
  try {
    await enterWorld(); await openFixture();
    if (target === '26.3-neoforge') {
      await button(null, 'UI Navigation Fixtures'); await page('UiNavigationFixtureScreen');
    } else {
      await page('AutomationProbeScreen');
    }
    const initial = await proof();
    assert(typeof initial.fixtureId === 'string' && initial.fixtureId.length >= 16); assert(Number.isSafeInteger(initial.processId) && initial.processId > 0);
    assert(Number.isSafeInteger(initial.sequence) && initial.sequence >= 0);
    expected = {target, fixtureId: initial.fixtureId, processId: initial.processId, sequence: initial.sequence + 1}; owned(initial, expected);
    await check('UI reconfiguration: real owned switchToConfig and ACK reach the native page with Disconnect initially disabled', async () => {
      const before = await clean(), node = select(await tree(), null, 'Native Reconfigure (Fixture)'); assert.equal(node.active, true);
      startedNative = true; const action = await boundaryClick(node), value = await page('ServerReconfigScreen');
      assert.equal(value.screenClass, NATIVE); const disconnect = select(value, 'menu.disconnect'); assert.equal(disconnect.active, false);
      const actual = await observedProof('opened');
      samples.push({case: 'reconfiguration_native_entry', target, screenClass: value.screenClass, nativeScreens: [value.screenClass],
        screenIdentity: value.screenIdentity, screenRevision: value.screenRevision, semanticActions: [action], before, after: await clean(), proof: actual,
        arrangeAuthority: 'DIAGNOSTIC_native_reconfiguration', actAuthority: 'PLAYTEST', semanticTargets: true, handwrittenCoordinates: false,
        directCallbackUsed: false, configurationSuccessClaimed: false, businessOperationCoverage: 'native_stalled_configuration_ui_only'});
    });
    await check('UI reconfiguration: native 600-tick Disconnect becomes available and closes its actual connection, integrated server and world lock', async () => {
      const began = now(), ready = await wait(value => value.screenClass === NATIVE && nodes(value).some(node => node.translationKey === 'menu.disconnect' && node.active),
        'The unmodified native Disconnect button after 600 client ticks'); healthy();
      const readyWaitMillis = now() - began;
      const nativeReady = await observedProof('ready');
      disconnectAttempted = true; const action = await boundaryClick(select(ready, 'menu.disconnect'));
      const destination = await wait(value => /DisconnectedScreen$|TitleScreen$/.test(value.screenClass), 'Native disconnect destination'); healthy();
      const actual = await waitClosed(), after = await clean();
      samples.push({case: 'reconfiguration_native_disconnect', target, screenClass: NATIVE, nativeScreens: [NATIVE, destination.screenClass],
        semanticActions: [action], nativeReady, proof: actual, after, observedReadyWaitMillis: readyWaitMillis,
        nativeDelayTicksRequired: 600, nativeDelayWasModified: false, connectionClosureIndependentlyObserved: true,
        sourceDirectoryLockReleased: true, arrangeAuthority: 'DIAGNOSTIC_native_reconfiguration', actAuthority: 'PLAYTEST',
        semanticTargets: true, handwrittenCoordinates: false, directCallbackUsed: false, configurationSuccessClaimed: false});
      if (destination.screenClass.endsWith('DisconnectedScreen')) {
        const choices = nodes(destination).filter(node => node.visible && node.active && ['gui.toMenu', 'gui.toTitle', 'gui.back'].includes(node.translationKey));
        assert.equal(choices.length, 1); await ui({nodeId: choices[0].nodeId});
      }
      await title(); await page('TitleScreen'); await clean();
    });
  } catch (error) {primary = error;}
  finally {
    if (startedNative && !closed) {
      try {
        const value = await tree();
        if (value.screenClass === NATIVE && !disconnectAttempted) {
          const disconnect = select(value, 'menu.disconnect'); assert.equal(disconnect.active, true, 'Native Disconnect is not yet available; outer Runner must clean its own client');
          disconnectAttempted = true; await boundaryClick(disconnect);
        }
        await waitClosed();
      } catch (error) {cleanupFailure = error;}
    }
    samples.push({case: 'reconfiguration_cleanup', target, startedNative, nativeClosed: closed, cleanupCompleted: !cleanupFailure,
      outerRunnerOwnsFinalClientShutdown: true, failed: Boolean(primary || cleanupFailure), ...(primary ? {failure: primary.message} : {}),
      ...(cleanupFailure ? {cleanupFailure: cleanupFailure.message} : {}), elapsedMillis: now() - started, maximumMillis: MAXIMUM_MILLIS,
      configurationSuccessClaimed: false, serverConfigStartInvokedByProbe: false, configurationTaskInjected: false, customProtocolPacketsSent: false,
      persistentWriteCalls: 0});
    if (cleanupFailure) {if (primary) throw new AggregateError([primary, cleanupFailure], 'Native reconfiguration UI and cleanup failed'); throw cleanupFailure;}
    if (primary) throw primary;
  }
}
