import assert from 'node:assert/strict';
import test from 'node:test';
import {readFile} from 'node:fs/promises';
import {reconfigurationUiActions} from './ui-reconfiguration-actions.mjs';

test('native reconfiguration probes use the existing bounded atomic evidence publisher without duplicating file replacement', async () => {
  for (const target of ['1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric']) {
    const source = await readFile(new URL(`../../../../versions/${target}/src/probe/java/io/github/recrivenvi/minecraftprotocol/probe/validation/UiReconfigurationFixture.java`, import.meta.url), 'utf8');
    assert.match(source, /private synchronized void report\(\)/, target);
    assert.match(source, /UiSavingIndicatorFixture\.publish\(temporary, result\);/, target);
    assert(!source.includes('Files.move('), target);
  }
});

// Controlled Native API values exercise the actual helper; this is not a claim
// that Minecraft ticked, switched its protocol, or released a real world lock.
function controlledNative({target = '26.3-neoforge', epochCancelled = false, cancelCode = 'CONTROL_INPUT_GENERATION_CHANGED',
  readyTicks = 600, lockReleased = true, foreignConnection = false, skippedDisabled = false} = {}) {
  let screen = 'TitleScreen', ticks = 0, sequence = 0, dispatch = 0, identity = 1, clock = 0;
  const samples = [], checks = [], actions = [];
  const native = 'net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen';
  let actual = {probeOnly: true, arrangeAuthority: 'DIAGNOSTIC_native_reconfiguration', target, fixtureId: 'current-native-reconfiguration-fixture',
    processId: 71, sequence: 0, sourceWorldName: 'UI-Conformance', immutableBaselineTouched: false, dailyInstancesTouched: false, externalServerUsed: false,
    serverConfigStartInvokedByProbe: false, configurationTaskInjected: false, customProtocolPacketsSent: false, configurationSuccessClaimed: false,
    worldFilesTouched: 'owned_validation_working_world'};
  const button = (nodeId, label, translationKey, active = true) => ({nodeId, label, translationKey, visible: true, active, actions: active ? ['click'] : []});
  const change = value => {screen = value; identity++;};
  const tree = async () => ({screenClass: screen, dataCategory: 'USER', treeTruncated: false, screenIdentity: identity, screenRevision: identity,
    children: screen.endsWith('AutomationProbeScreen') ? (target === '26.3-neoforge'
      ? [button('nav', 'UI Navigation Fixtures')] : [button('reconfigure', 'Native Reconfigure (Fixture)')])
      : screen.endsWith('UiNavigationFixtureScreen') ? [button('reconfigure', 'Native Reconfigure (Fixture)')]
      : screen === native ? [button('disconnect', 'Disconnect', 'menu.disconnect', ticks >= readyTicks)]
      : screen.endsWith('DisconnectedScreen') ? [button('return', 'Back', 'gui.toTitle')] : []});
  const ctx = {repo: '.', target, samples, tree, nodes: value => value.children,
    readFixtureProof: async () => structuredClone(actual), title: async () => {change('net.minecraft.client.gui.screens.TitleScreen');},
    enterWorld: async () => {change('');}, openFixture: async () => {change('io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen');},
    api: async (method, path) => {assert.equal(method, 'GET'); assert.equal(path, '/v0/input/state'); return {pressedKeyCount: 0, pressedButtonCount: 0,
      inputCleanupPending: false, inputCleanupFailed: false, inputDispatchSequence: dispatch};},
    check: async (name, run) => {assert(name.startsWith('UI reconfiguration:')); await run(); checks.push(name);},
    wait: async (predicate, label) => {
      let value = await tree();
      if (!predicate(value) && screen === native) {
        ticks = readyTicks; clock += 30000; Object.assign(actual, {disconnectReadyObserved: true, readyNativeDelayTicker: readyTicks}); value = await tree();
      }
      assert(predicate(value), label); return value;
    },
    ui: async selector => {
      assert.deepEqual(Object.keys(selector), ['nodeId']); const node = (await tree()).children.find(node => node.nodeId === selector.nodeId); assert(node?.active);
      actions.push(selector.nodeId); dispatch++;
      if (node.nodeId === 'nav') change('io.github.recrivenvi.minecraftprotocol.probe.validation.UiNavigationFixtureScreen');
      else if (node.nodeId === 'reconfigure') {
        sequence++; ticks = skippedDisabled ? readyTicks : 1; change(native);
        Object.assign(actual, {sequence, switchRequested: true, switchReturned: true, serverOwnerThread: true, singlePlayerOnly: true, unpublished: true,
          configurationAcknowledged: true, nativeClass: native, nativeScreenObserved: true, connectionIdentityBound: !foreignConnection,
          disconnectInitiallyDisabled: !skippedDisabled, firstNativeDelayTicker: ticks, disconnectReadyObserved: skippedDisabled,
          readyNativeDelayTicker: skippedDisabled ? readyTicks : -1});
      } else if (node.nodeId === 'disconnect') {
        Object.assign(actual, {nativeDisconnectHandlerReturned: true, nativeClientConnectionClosed: true, nativeServerConnectionClosed: true,
          integratedServerStopped: true, sourceDirectoryLockReleased: lockReleased, playerAbsent: true, connectionAbsent: true});
        change('net.minecraft.client.gui.screens.DisconnectedScreen');
      } else if (node.nodeId === 'return') change('net.minecraft.client.gui.screens.TitleScreen');
      else assert.fail(node.nodeId);
      if (epochCancelled && ['reconfigure', 'disconnect'].includes(node.nodeId)) throw Object.assign(new Error('Native input context changed'),
        {httpStatus: 409, nativeError: {error: cancelCode, executionOutcome: 'not_confirmed', retrySafe: false}});
      return {targetingSource: 'interaction_tree', directMutationUsed: false};
    }};
  return {ctx, samples, checks, actions, state: () => screen, services: {now: () => clock, sleep: async milliseconds => {clock += milliseconds;}}};
}

test('the actual helper records native disabled-to-600tick readiness and independent connection/server/lock cleanup', async () => {
  const fixture = controlledNative(); await reconfigurationUiActions(fixture.ctx, fixture.services);
  assert.equal(fixture.checks.length, 2); assert(fixture.state().endsWith('TitleScreen'));
  assert.deepEqual(fixture.actions, ['nav', 'reconfigure', 'disconnect', 'return']);
  assert.equal(fixture.samples[0].proof.disconnectInitiallyDisabled, true); assert.equal(fixture.samples[1].nativeReady.readyNativeDelayTicker, 600);
  assert.equal(fixture.samples[1].proof.sourceDirectoryLockReleased, true); assert.equal(fixture.samples.at(-1).nativeClosed, true);
  assert(fixture.samples.every(sample => sample.configurationSuccessClaimed === false));
});

test('the other three actual configuration-protocol targets use their independent Automation button without Nav', async () => {
  for (const target of ['1.21.1-neoforge', '26.1.2-neoforge', '26.3-fabric']) {
    const fixture = controlledNative({target}); await reconfigurationUiActions(fixture.ctx, fixture.services);
    assert.equal(fixture.checks.length, 2); assert(fixture.state().endsWith('TitleScreen'));
    assert.deepEqual(fixture.actions, ['reconfigure', 'disconnect', 'return']);
    assert.equal(fixture.samples[0].proof.target, target); assert.equal(fixture.samples[0].proof.configurationAcknowledged, true);
    assert.equal(fixture.samples[1].nativeDelayTicksRequired, 600); assert.equal(fixture.samples[1].nativeDelayWasModified, false);
    assert.equal(fixture.samples[1].proof.nativeDisconnectHandlerReturned, true);
    assert.equal(fixture.samples[1].proof.integratedServerStopped, true);
    assert.equal(fixture.samples[1].proof.sourceDirectoryLockReleased, true);
    assert.equal(fixture.samples.at(-1).nativeClosed, true); assert.equal(fixture.samples.at(-1).persistentWriteCalls, 0);
  }
});

test('Forge without the configuration protocol and unreviewed targets are rejected before entry or dispatch', async () => {
  for (const target of ['1.20.1-forge', 'unreviewed-target']) {
    const fixture = controlledNative({target});
    await assert.rejects(reconfigurationUiActions(fixture.ctx, fixture.services), /reviewed native configuration protocol/);
    assert.equal(fixture.state(), 'TitleScreen'); assert.deepEqual(fixture.actions, []); assert.deepEqual(fixture.samples, []);
  }
});

test('an epoch-cancelled UI response stays not-confirmed while independent native results are verified, without replay', async () => {
  const fixture = controlledNative({epochCancelled: true}); await reconfigurationUiActions(fixture.ctx, fixture.services);
  assert.equal(fixture.actions.filter(value => value === 'reconfigure').length, 1); assert.equal(fixture.actions.filter(value => value === 'disconnect').length, 1);
  for (const sample of fixture.samples.slice(0, 2)) {
    assert.equal(sample.semanticActions[0].requestOutcome, 'not_confirmed'); assert.equal(sample.semanticActions[0].requestReplayed, false);
    assert.equal(sample.semanticActions[0].runtimeError.retrySafe, false);
  }
});

test('the independent Automation entry never replays REQUEST_CANCELLED or epoch cancellation on any supported port', async () => {
  for (const target of ['1.21.1-neoforge', '26.1.2-neoforge', '26.3-fabric']) for (const cancelCode of ['REQUEST_CANCELLED', 'CONTROL_INPUT_GENERATION_CHANGED']) {
    const fixture = controlledNative({target, epochCancelled: true, cancelCode});
    await reconfigurationUiActions(fixture.ctx, fixture.services);
    assert.deepEqual(fixture.actions, ['reconfigure', 'disconnect', 'return']);
    for (const sample of fixture.samples.slice(0, 2)) {
      const action = sample.semanticActions[0]; assert.equal(action.requestOutcome, 'not_confirmed');
      assert.equal(action.requestReplayed, false); assert.equal(action.runtimeError.error, cancelCode);
      assert.equal(action.runtimeError.executionOutcome, 'not_confirmed'); assert.equal(action.runtimeError.retrySafe, false);
    }
  }
});

test('unrelated or retryable cancellation cannot be promoted to native success and is never replayed', async () => {
  for (const retryable of [false, true]) {
    const fixture = controlledNative({target: '26.3-fabric', epochCancelled: true,
      cancelCode: retryable ? 'REQUEST_CANCELLED' : 'UNRELATED_CONFLICT'});
    if (retryable) {
      const ui = fixture.ctx.ui;
      fixture.ctx.ui = async selector => {try {return await ui(selector);} catch (error) {error.nativeError.retrySafe = true; throw error;}};
    }
    await assert.rejects(reconfigurationUiActions(fixture.ctx, fixture.services));
    assert.equal(fixture.actions.filter(action => action === 'reconfigure').length, 1);
    assert.equal(fixture.actions.filter(action => action === 'disconnect').length, 0);
    assert.equal(fixture.samples.at(-1).failed, true); assert(fixture.checks.length < 2);
  }
});

test('the real helper waits for the asynchronous native ACK publication after the page becomes readable', async () => {
  const fixture = controlledNative(); const read = fixture.ctx.readFixtureProof; let pending = 2;
  fixture.ctx.readFixtureProof = async () => {
    const value = await read(); if (value.switchRequested && !value.disconnectReadyObserved && pending-- > 0) value.configurationAcknowledged = false;
    return value;
  };
  await reconfigurationUiActions(fixture.ctx, fixture.services); assert.equal(fixture.checks.length, 2); assert(pending <= 0);
  assert.equal(fixture.actions.filter(value => value === 'reconfigure').length, 1);
});

test('native tick shortening, foreign connections and missing initial disabled state cannot pass as real native configuration UI', async () => {
  for (const options of [{readyTicks: 599}, {foreignConnection: true}, {skippedDisabled: true}]) {
    const fixture = controlledNative(options); await assert.rejects(reconfigurationUiActions(fixture.ctx, fixture.services));
    assert(fixture.checks.length < 2); assert.equal(fixture.samples.at(-1).failed, true);
  }
});

test('the actual direct-entry consumer rejects missing ACK and wrong owned proof instead of accepting a readable page', async () => {
  for (const mutate of [proof => proof.configurationAcknowledged = false, proof => proof.processId++,
    proof => proof.target = '26.3-neoforge', proof => proof.failure = 'Native preparation failed']) {
    const fixture = controlledNative({target: '26.3-fabric'}), read = fixture.ctx.readFixtureProof;
    fixture.ctx.readFixtureProof = async () => {const value = await read(); if (value.switchRequested) mutate(value); return value;};
    await assert.rejects(reconfigurationUiActions(fixture.ctx, fixture.services));
    assert.equal(fixture.actions.filter(action => action === 'reconfigure').length, 1);
    assert.equal(fixture.checks.length, 0); assert.equal(fixture.samples.at(-1).failed, true);
  }
});

test('each native disconnect connection server player and lock fact is independently required on the direct path', async () => {
  for (const field of ['nativeDisconnectHandlerReturned', 'nativeClientConnectionClosed', 'nativeServerConnectionClosed',
    'integratedServerStopped', 'sourceDirectoryLockReleased', 'playerAbsent', 'connectionAbsent']) {
    const fixture = controlledNative({target: '1.21.1-neoforge'}), read = fixture.ctx.readFixtureProof;
    fixture.ctx.readFixtureProof = async () => {const value = await read(); if (value.nativeDisconnectHandlerReturned) value[field] = false; return value;};
    await assert.rejects(reconfigurationUiActions(fixture.ctx, fixture.services));
    assert.equal(fixture.actions.filter(action => action === 'disconnect').length, 1);
    assert.equal(fixture.checks.length, 1); assert.equal(fixture.samples.at(-1).nativeClosed, false);
    assert.equal(fixture.samples.at(-1).cleanupCompleted, false);
  }
});

test('an unreleased real-world lock fails both the observation and cleanup rather than being hidden by a menu return', async () => {
  const fixture = controlledNative({lockReleased: false});
  await assert.rejects(reconfigurationUiActions(fixture.ctx, fixture.services), error => error instanceof AggregateError && error.errors.length === 2);
  assert.equal(fixture.checks.length, 1); assert.equal(fixture.samples.at(-1).cleanupCompleted, false); assert.equal(fixture.samples.at(-1).nativeClosed, false);
});
