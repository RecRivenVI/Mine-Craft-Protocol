import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

// Only the outer runner's restored world and authenticated instance are used.
// A released snapshot observes held counts, not a permanent queue-idle or cleanup-barrier promise.
export async function continuousInputActions({api, connection, repo, instance, target, mode, acquire, release, click, clickNode, wait, nodes, check, actionSamples, stopRenewal, forgetExpiredLease}) {
  const startedAt = performance.now(), deadline = startedAt + 180000;
  const sleep = millis => new Promise(done => setTimeout(done, millis));
  const releasedCondition = {type: 'input.released'};
  const terminalStates = new Set(['completed', 'failed', 'cancelled']);
  let ownsLease = false, ownedLeaseId, lastPipelinePost = -Infinity, primaryFailure;
  function remaining(maximum = 5000) {
    const left = Math.floor(deadline - performance.now());
    assert(left > 0, 'Continuous input exceeded its 180-second budget');
    return Math.min(maximum, left);
  }
  async function input(zero = false) {
    remaining();
    const state = await api('GET', '/v0/input/state');
    for (const field of ['pressedKeyCount', 'pressedButtonCount', 'inputDispatchSequence', 'inputGeneration'])
      assert(Number.isSafeInteger(state[field]) && state[field] >= 0, 'Invalid actual input state: ' + field);
    if (zero) {
      assert.equal(state.pressedKeyCount, 0, JSON.stringify(state));
      assert.equal(state.pressedButtonCount, 0, JSON.stringify(state));
    }
    return state;
  }
  function releaseEvidence(value) {
    assert.equal(value.passed, true, JSON.stringify(value));
    assert.deepEqual(value.condition, releasedCondition);
    assert.deepEqual(value.evidence, {pressedKeyCount: 0, pressedButtonCount: 0});
    return value;
  }
  async function released() {
    return releaseEvidence(await api('POST', '/v0/wait/until', {condition: releasedCondition, timeoutMs: remaining(5000)}));
  }
  async function poll(read, predicate, label, maximum = 5000) {
    const end = performance.now() + remaining(maximum);
    let value;
    do {
      value = await read();
      if (predicate(value)) return value;
      await sleep(Math.min(75, remaining()));
    } while (performance.now() < end);
    assert.fail(label + ': ' + JSON.stringify(value));
  }
  async function take() {
    remaining(); await acquire(); ownsLease = true;
    const state = await api('GET', '/v0/control/status');
    assert.equal(state.mode, 'TAKEOVER'); assert.equal(state.status, 'active');
    assert.equal(typeof state.leaseId, 'string'); ownedLeaseId = state.leaseId;
    return state;
  }
  async function handBack() {
    if (!ownsLease) return;
    const state = await api('GET', '/v0/control/status');
    async function forgetEnded(current) {
      assert.equal(current.status, 'available', 'Do not release a different owner\'s Lease');
      assert.equal(current.mode, 'READ', 'Only forget an actually ended input Lease');
      releaseEvidence(await api('POST', '/v0/wait/until', {condition: releasedCondition, timeoutMs: 5000}));
      forgetExpiredLease();
    }
    if (state.leaseId === ownedLeaseId) {
      try { await release(); }
      catch (error) {
        const current = await api('GET', '/v0/control/status');
        if (current.status !== 'available' || current.mode !== 'READ') throw error;
        await forgetEnded(current);
      }
    } else await forgetEnded(state);
    ownsLease = false; ownedLeaseId = undefined;
  }
  async function sameLease(expectedId, expectedVersion) {
    const state = await api('GET', '/v0/control/status');
    assert.equal(state.status, 'active'); assert.equal(state.mode, 'TAKEOVER');
    assert.equal(state.leaseId, expectedId); assert.equal(state.reconsentRequired, false);
    if (expectedVersion) assert.deepEqual(state.modeVersion, expectedVersion);
    return state;
  }
  async function pipelineSlot() {
    let pause = 550 - (performance.now() - lastPipelinePost);
    while (pause > 0) {
      assert(pause < remaining(), 'Pipeline spacing exceeds remaining scenario budget');
      await sleep(Math.ceil(pause));
      pause = 550 - (performance.now() - lastPipelinePost);
    }
    remaining();
  }
  async function pacePipeline() {
    await pipelineSlot(); lastPipelinePost = performance.now();
  }
  async function startPipeline(steps, options = {}) {
    await pacePipeline();
    const start = performance.now();
    const operation = await api('POST', '/v0/pipelines', {steps, timeoutMs: 8000, ...options});
    assert.equal(typeof operation.operationId, 'string');
    return {...operation, postRttMillis: performance.now() - start};
  }
  async function terminal(operation, expected = 'completed', maximum = 8000) {
    const end = performance.now() + remaining(maximum);
    let result;
    do {
      result = await api('POST', `/v0/operations/${operation.operationId}/wait`, {timeoutMs: Math.max(1, Math.min(remaining(3000), Math.floor(end - performance.now())))});
      if (terminalStates.has(result.state)) {
        assert.equal(result.state, expected, JSON.stringify(result));
        return result;
      }
      assert.equal(result.status, 'running', 'An incomplete operation is not a PASS: ' + JSON.stringify(result));
    } while (performance.now() < end);
    assert.fail('Operation did not reach ' + expected + ': ' + JSON.stringify(result));
  }
  async function pipeline(steps, options) {
    const operation = await startPipeline(steps, options);
    return terminal(operation);
  }
  function routed(outcome) {
    for (const step of outcome.result.steps) {
      if (!['key', 'mouse.delta', 'mouse.button'].includes(step.stepType)) continue;
      assert.equal(step.result.inputOrigin, 'AGENT_ROUTED');
      assert.equal(step.result.entryLayer, 'GAME_ROUTED_RAW');
      assert.equal(step.result.hostCursorUsed, false); assert.equal(step.result.directMutationUsed, false);
    }
  }
  async function heldKey(keyCode) {
    return poll(() => input(), state => state.pressedKeyCount === 1 && state.pressedButtonCount === 0 && state.pressedKeys?.includes(keyCode), 'Actual Runtime-owned key was not held', 3000);
  }
  const keyStep = (keyCode, action) => ({type: 'key', key: keyCode, action, scanCode: 0, modifiers: 0});
  try {
    await check('continuous READ rejects release-all without input effects and independently waits/asserts held zero', async () => {
      await mode('READ');
      const before = await input(true);
      const denied = await api('POST', '/v0/input/release-all', {}, 'TAKEOVER_REQUIRED');
      const waited = await released();
      const asserted = releaseEvidence(await api('POST', '/v0/assert', {condition: releasedCondition}));
      const after = await input(true);
      assert.equal(after.inputDispatchSequence, before.inputDispatchSequence);
      assert.equal(after.inputGeneration, before.inputGeneration);
      actionSamples.push({case: 'continuous_read_denied', denied, waited, asserted, before, after, readDoesNotGrantDiagnostics: true});
    });
    await check('continuous enters the restored world by USER semantic UI without coordinate fallback', async () => {
      await take(); await click('menu.singleplayer', 'Singleplayer');
      const tree = await wait(value => value.screenClass.endsWith('SelectWorldScreen'), 'continuous world list', remaining(20000));
      const row = nodes(tree).find(value => value.worldName === 'World-Read-Alpha'); assert(row);
      await clickNode(row); await click('selectWorld.select', 'Play Selected World');
      await wait(value => !value.screenClass, 'continuous restored world entered', remaining(60000));
      await released(); await input(true);
      actionSamples.push({case: 'continuous_semantic_world_entry', worldName: row.worldName, nodeId: row.nodeId, coordinateFallbacks: 0});
    });
    await check('continuous W survives three separate relative-look pipelines and causes actual client displacement', async () => {
      const before = await api('GET', '/v0/player?category=USER');
      const down = await pipeline([keyStep(87, 1)], {cleanupOnComplete: false}); routed(down);
      assert.equal(down.result.cleanupOnComplete, false);
      const held = await heldKey(87), looks = [];
      for (const dx of [3, -6, 3]) {
        const outcome = await pipeline([{type: 'mouse.delta', dx, dy: 0}], {cleanupOnComplete: false});
        routed(outcome); assert.equal(outcome.result.cleanupOnComplete, false);
        looks.push({outcome, held: await heldKey(87)});
      }
      const after = await api('GET', '/v0/player?category=USER');
      const distance = Math.hypot(after.x - before.x, after.z - before.z);
      assert(Number.isFinite(distance) && distance > .01, 'Real client player movement was not observed');
      assert.equal((await api('GET', '/v0/ui/tree')).screenClass, '');
      actionSamples.push({case: 'continuous_preserved_w', down, held, looks, before, after, observedHorizontalDistance: distance, serverAcceptanceConfirmed: false, actAuthority: 'PLAYTEST'});
    });
    await check('continuous explicit W release reaches the formal input.released condition', async () => {
      await heldKey(87);
      const up = await pipeline([keyStep(87, 0)], {cleanupOnComplete: false}); routed(up);
      const waited = await released(), after = await input(true);
      assert.equal(up.result.cleanupOnComplete, false);
      actionSamples.push({case: 'continuous_explicit_up', up, waited, after});
    });
    await check('continuous default completion cleanup releases a key preserved by an earlier pipeline', async () => {
      const down = await pipeline([keyStep(87, 1)], {cleanupOnComplete: false});
      const held = await heldKey(87);
      const completed = await pipeline([{type: 'delay', durationMs: 50}]);
      assert.equal(completed.result.cleanupOnComplete, true);
      assert.equal(completed.result.cleanup.releasedKeys, 1);
      assert.equal(completed.result.timing.stages.cleanup.calls, 1);
      const waited = await released(), after = await input(true);
      actionSamples.push({case: 'continuous_default_cleanup', down, held, completed, waited, after});
    });
    await check('continuous release-all cancels active and queued input, drains held state and preserves its Lease', async () => {
      const before = await sameLease(ownedLeaseId);
      const active = await startPipeline([keyStep(340, 1), {type: 'delay', durationMs: 15000}, {type: 'key.tap', key: 65, holdMs: 40}], {timeoutMs: 20000, cleanupOnComplete: false});
      const held = await heldKey(340);
      const queued = [];
      for (const code of [65, 68]) queued.push(await startPipeline([{type: 'key.tap', key: code, holdMs: 40}]));
      const queuedState = await heldKey(340);
      assert.equal(queuedState.inputDispatchSequence, held.inputDispatchSequence, 'Queued future keys ran before the stop fixture');
      const activeBefore = await api('GET', `/v0/operations/${active.operationId}`);
      assert.equal(activeBefore.status, 'running'); assert(!terminalStates.has(activeBefore.state));
      const stopStarted = performance.now();
      const stopped = await api('POST', '/v0/input/release-all', {});
      const stopRttMillis = performance.now() - stopStarted;
      assert.equal(stopped.type, 'input.release_all');
      const outcomes = [];
      for (const operation of [active, ...queued]) outcomes.push(await terminal(operation, 'cancelled'));
      const waited = await released(), after = await input(true);
      assert.equal(after.inputCleanupPending, false); assert.equal(after.inputCleanupFailed, false);
      assert.equal(after.inputGeneration, stopped.inputGeneration); assert(after.inputGeneration > queuedState.inputGeneration);
      await sameLease(before.leaseId, before.modeVersion);
      // A bounded negative-observation window, never a substitute for a success condition.
      assert(remaining() >= 500, 'No budget remains for the complete negative-observation window');
      await sleep(500); const later = await input(true);
      assert.equal(later.inputDispatchSequence, after.inputDispatchSequence, 'Cancelled input arrived after the completed stop barrier');
      actionSamples.push({case: 'continuous_release_all', active, queued, held, queuedState, activeBefore, stopped, stopRttMillis, outcomes, waited, after, later,
        cleanupBarrierConfirmedBy: 'release_all_response', negativeObservationMillis: 500, leasePreserved: true, modeVersionPreserved: true, physicalEscTested: false});
    });
    await check('continuous the same Lease accepts a fresh input after explicit stop', async () => {
      const before = await sameLease(ownedLeaseId), sequence = (await input(true)).inputDispatchSequence;
      const completed = await pipeline([{type: 'key.tap', key: 340, holdMs: 40}]);
      await released(); const after = await input(true);
      assert(after.inputDispatchSequence >= sequence + 3, 'A fresh native down/up plus cleanup was not observed');
      await sameLease(before.leaseId, before.modeVersion);
      actionSamples.push({case: 'continuous_after_stop', leaseId: before.leaseId, completed, after});
    });
    await check('continuous three attack button down/up pairs share one pipeline cleanup and report actual timing', async () => {
      const before = await input(true), steps = [];
      for (let index = 0; index < 3; index++) {
        steps.push({type: 'mouse.button', button: 0, action: 1, modifiers: 0}, {type: 'delay', durationMs: 40}, {type: 'mouse.button', button: 0, action: 0, modifiers: 0});
        if (index < 2) steps.push({type: 'delay', durationMs: 60});
      }
      const requestStarted = performance.now(), completed = await pipeline(steps); routed(completed);
      const observedTerminalMillis = performance.now() - requestStarted;
      const timing = completed.result.timing;
      assert.equal(timing.scope, 'automation_pipeline_not_http_or_server_effect');
      assert.equal(timing.timeSource, 'monotonic_elapsed');
      assert.equal(timing.stages.cleanup.calls, 1);
      assert.equal(completed.result.steps.filter(step => step.stepType === 'mouse.button').length, 6);
      await released(); const after = await input(true);
      assert.equal(after.inputDispatchSequence - before.inputDispatchSequence, 7, 'Six actual button callbacks and one cleanup must be distinguished');
      actionSamples.push({case: 'continuous_attack_pipeline', completed, timing, observedTerminalMillis, after,
        ordinaryButtonPairs: 3, cleanupCalls: 1, attackHitConfirmed: false, serverAcceptanceConfirmed: false, latencyRatioClaimed: false, actAuthority: 'PLAYTEST'});
    });
    await check('continuous real Lease expiration clears preserved Shift and returns to READ', async () => {
      await pipelineSlot(); stopRenewal();
      const renewed = await api('POST', '/v0/control/renew', {ttlMs: 1000});
      assert.equal(renewed.leaseId, ownedLeaseId);
      const heldOperation = await startPipeline([keyStep(340, 1)], {cleanupOnComplete: false});
      const held = await heldKey(340); await terminal(heldOperation);
      const expired = await poll(() => api('GET', '/v0/control/mode'), value => value.mode === 'READ', 'Short Lease did not actually expire', 5000);
      assert.equal(expired.takeoverActive, false); assert.equal(expired.reconsentRequired, false);
      assert.equal(expired.modeTransitionReason, 'lease_expired');
      const waited = await released(), after = await input(true);
      const status = await api('GET', '/v0/control/status'); assert.equal(status.status, 'available');
      assert.equal(after.inputCleanupPending, false); assert.equal(after.inputCleanupFailed, false);
      forgetExpiredLease(); ownsLease = false; ownedLeaseId = undefined;
      actionSamples.push({case: 'continuous_lease_expiry', renewed, heldOperation, held, expired, waited, after, status, localLeaseForgottenOnlyAfterConfirmedExpiry: true});
    });
    await check('continuous actual MCP initializes with the package version and preserves its Lease through release_inputs', async () => {
      const acquired = await take();
      const require = createRequire(resolve(repo, 'components/companion/package.json'));
      const {Client} = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
      const {StdioClientTransport} = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
      const pkg = JSON.parse(await readFile(resolve(repo, 'components/companion/package.json'), 'utf8'));
      const env = Object.fromEntries(Object.entries(process.env).filter(([, value]) => typeof value === 'string'));
      for (const name of ['MCP_MINECRAFT_TOKEN', 'MCP_MINECRAFT_BASE_URL', 'MCP_MINECRAFT_TOKEN_FILE']) delete env[name];
      env.MCP_MINECRAFT_INSTANCE_DIR = instance; env.MCP_MINECRAFT_TARGET = target;
      const client = new Client({name: 'continuous-input-regression', version: '1.0.0'}, {versionNegotiation: {mode: 'legacy'}});
      const transport = new StdioClientTransport({command: process.execPath, args: [resolve(repo, 'components/companion/dist/index.js')], env, stderr: 'pipe'});
      let mcpFailure;
      const call = async (name, args) => {
        remaining();
        const result = await client.callTool({name, arguments: args}, {timeout: remaining(12000)});
        assert(!result.isError, JSON.stringify(result)); assert(result.structuredContent?.data, JSON.stringify(result));
        return result.structuredContent.data;
      };
      try {
        await client.connect(transport, {timeout: remaining(12000)});
        const serverInfo = client.getServerVersion();
        assert.equal(serverInfo?.name, 'minecraft-protocol-companion'); assert.equal(serverInfo.version, pkg.version);
        const cached = await call('minecraft_control', {action: 'renew', leaseId: acquired.leaseId, ttlMs: 60000});
        assert.equal(cached.leaseId, acquired.leaseId);
        await pacePipeline();
        const down = await call('minecraft_run_input_pipeline', {steps: [keyStep(340, 1)], timeoutMs: 8000, cleanupOnComplete: false, waitForCompletion: true});
        assert.equal(down.state, 'completed', JSON.stringify(down)); const held = await heldKey(340);
        const before = await call('minecraft_control', {action: 'status'});
        const stopped = await call('minecraft_control', {action: 'release_inputs'});
        assert.equal(stopped.type, 'input.release_all');
        const waited = releaseEvidence(await call('minecraft_wait', {condition: releasedCondition, timeoutMs: remaining(5000)}));
        const asserted = releaseEvidence(await call('minecraft_assert', {condition: releasedCondition}));
        const after = await call('minecraft_control', {action: 'status'});
        assert.equal(after.mode, 'TAKEOVER'); assert.equal(after.reconsentRequired, false); assert.deepEqual(after.modeVersion, before.modeVersion);
        await sameLease(acquired.leaseId, before.modeVersion);
        await pacePipeline();
        const fresh = await call('minecraft_run_input_pipeline', {steps: [{type: 'key.tap', key: 340, holdMs: 40}], timeoutMs: 8000, waitForCompletion: true});
        assert.equal(fresh.state, 'completed', JSON.stringify(fresh)); await input(true);
        await handBack(); const returned = await api('GET', '/v0/control/mode');
        assert.equal(returned.mode, 'READ'); await released();
        actionSamples.push({case: 'continuous_mcp_release_inputs', transport: 'real_mcp_stdio', initializeServerInfo: serverInfo, expectedPackageVersion: pkg.version,
          acquired, cached, down, held, stopped, waited, asserted, before, after, fresh, returned, cachedLeasePreserved: true, leaseReturnedBy: 'owning_native_runner'});
      } catch (error) { mcpFailure = error; throw error; }
      finally {
        let cleanupFailure;
        try {
          await handBack();
        } catch (error) { cleanupFailure = error; }
        try { await client.close(); } catch (error) { cleanupFailure ??= error; }
        try { await transport.close(); } catch (error) { cleanupFailure ??= error; }
        if (cleanupFailure) {
          actionSamples.push({case: 'continuous_mcp_cleanup_failure', error: String(cleanupFailure)});
          if (!mcpFailure) throw cleanupFailure;
          console.error(cleanupFailure);
        }
      }
    });
    await mode('READ'); const finalInput = await input(true);
    actionSamples.push({case: 'continuous_input_complete', target, elapsedMillis: performance.now() - startedAt, maximumMillis: 180000, checks: 10,
      minimumPipelinePostIntervalMillis: 550, finalInput, physicalEscTested: false, networkDisconnectTested: false, stateStreamTested: false,
      nativeWorldEpochCrossServerGuarantee: false, cleanupBarrierNotInferredFromHeldCounts: true,
      clientShutdownOwnedByOuterRunner: true, persistentWriteCalls: 0});
  } catch (error) { primaryFailure = error; throw error; }
  finally {
    try { await handBack(); }
    catch (error) {
      actionSamples.push({case: 'continuous_cleanup_failure', error: String(error)});
      if (!primaryFailure) throw error;
      console.error(error);
    }
  }
}
