import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import vm from 'node:vm';
import test from 'node:test';
import { finishBaselineWorldExit } from '../../conformance-world_read/task/interaction-baseline-actions.mjs';

const source = await readFile(new URL('./run-vanilla-ui.mjs', import.meta.url), 'utf8');
const start = source.indexOf('async function editorHandoff(');
const end = source.indexOf('\n}\ntry{', start);
assert(start >= 0 && end > start, 'Production handoff function boundary');
const handoff = new vm.Script('(' + source.slice(start, end + 2) + ')');

function controller({ consumerError, recoveryError, humanOverride = false } = {}) {
  const calls = [], logs = [];
  const context = {
    assert, resolve, args: { scenario: 'all' }, target: '26.3-fabric',
    repo: 'owned-repo', base: 'owned-runtime', token: 'test-token', instance: 'owned-instance',
    lease: 'old-lease', taskId: 'old-task', taskStarted: 0, failure: undefined, editorSamples: [],
    console: { error: (...values) => logs.push(values) },
    editorConsumers: async () => {
      calls.push('consumer');
      if (consumerError) throw consumerError;
      return [{ status: 'PASS', editor: 'debug_hud_title' }];
    },
    api: async (method, path, body) => {
      calls.push([method, path, body]);
      if (method === 'GET') return { mode: 'READ', reconsentRequired: humanOverride };
      if (path === '/v0/control/task' && body.action === 'begin') {
        if (recoveryError) throw recoveryError;
        return { taskId: 'new-task' };
      }
      if (path === '/v0/control/acquire') {
        assert.equal(body.taskId, 'new-task');
        return { leaseId: 'new-lease' };
      }
      return {};
    }
  };
  const run = handoff.runInContext(vm.createContext(context));
  return { context, calls, logs, run: () => run('debug_hud_title') };
}

test('production editor handoff replaces the ended task before controller work resumes', async () => {
  const fixture = controller();
  await fixture.run();
  assert.equal(fixture.context.lease, 'new-lease');
  assert.equal(fixture.context.taskId, 'new-task');
  assert.equal(fixture.context.editorSamples.length, 1);
  assert.equal(fixture.context.failure, undefined);
});

test('a consumer failure survives recovery and never becomes a passing sample', async () => {
  const primary = new Error('original consumer rejection');
  const fixture = controller({ consumerError: primary });
  await assert.rejects(fixture.run(), error => error === primary);
  assert.equal(fixture.context.failure, primary);
  assert.equal(fixture.context.taskId, 'new-task');
  assert.equal(fixture.context.lease, 'new-lease');
  assert.equal(fixture.context.editorSamples.length, 0);
});

test('a recovery failure is reported without replacing the original consumer failure', async () => {
  const primary = new Error('original consumer rejection');
  const secondary = new Error('controller recovery rejected');
  const fixture = controller({ consumerError: primary, recoveryError: secondary });
  await assert.rejects(fixture.run(), error => error === primary);
  assert.equal(fixture.context.failure, primary);
  assert.equal(fixture.context.taskId, undefined);
  assert.equal(fixture.context.lease, undefined);
  assert(fixture.logs.some(values => values.includes(secondary)));
});

test('handoff recovery never begins a task or acquires input after human override', async () => {
  for (const consumerError of [undefined, new Error('interrupted consumer')]) {
    const fixture = controller({ consumerError, humanOverride: true });
    await assert.rejects(fixture.run(), error => consumerError ? error === consumerError : /human override/.test(error.message));
    assert(!fixture.calls.some(call => Array.isArray(call) && (call[1] === '/v0/control/acquire' || call[2]?.action === 'begin')));
    assert.equal(fixture.context.taskId, undefined);
    assert.equal(fixture.context.lease, undefined);
  }
});

const exitStart = source.indexOf('async function saveQuit(');
const exitEnd = source.indexOf('\nasync function title(', exitStart);
assert(exitStart >= 0 && exitEnd > exitStart, 'Production Save and Quit function boundary');
const saveQuit = new vm.Script('(' + source.slice(exitStart, exitEnd) + ')');

function worldExit({ error, failUi = false, after = {}, title = {}, released = {} } = {}) {
  const calls = [], samples = [], response = { type: 'ui.action_result', targetingSource: 'interaction_tree', directMutationUsed: false };
  const before = { inputGeneration: 7, inputContextGeneration: 3, inputContextId: 'owned-world', inputContextAvailable: true,
    pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false };
  const final = { ...before, inputGeneration: 8, inputContextGeneration: 4, inputContextId: 'owned-title', inputContextAvailable: false, ...after };
  const titleTree = { screenClass: 'net.minecraft.client.gui.screens.TitleScreen', overlayIdentity: 0, children: [{ nodeId: 'singleplayer' }], ...title };
  let reads = 0;
  const context = {
    finishBaselineWorldExit, worldExitSamples: samples,
    ui: async selector => { calls.push(['ui', selector]); if (failUi) throw error; return response; },
    wait: async predicate => { calls.push(['title']); assert(predicate(titleTree), 'Title must have no overlay'); return titleTree; },
    api: async (method, path, body) => {
      calls.push([method, path, body]);
      if (method === 'GET' && path === '/v0/input/state') return reads++ === 0 ? before : final;
      assert.equal(method, 'POST'); assert.equal(path, '/v0/wait/until');
      assert.equal(body.condition.type, 'input.released');
      return { passed: true, evidence: { pressedKeyCount: 0, pressedButtonCount: 0 }, ...released };
    }
  };
  const run = saveQuit.runInContext(vm.createContext(context));
  return { calls, samples, response, titleTree, run: () => run({ nodeId: 'owned-save-quit' }) };
}

test('production Save and Quit bridges only structured epoch cancellation and independently records cleanup without replay', async () => {
  for (const code of ['REQUEST_CANCELLED', 'CONTROL_INPUT_GENERATION_CHANGED']) {
    const body = { error: code, executionOutcome: 'not_confirmed', retrySafe: false, requestId: 'cancelled-click' };
    const error = Object.assign(new Error('actual Runtime cancellation'), { httpStatus: 409, nativeError: body });
    const fixture = worldExit({ error, failUi: true });
    assert.equal(await fixture.run(), fixture.titleTree);
    assert.equal(fixture.calls.filter(call => call[0] === 'ui').length, 1);
    assert.equal(fixture.samples.length, 1);
    const evidence = fixture.samples[0];
    assert.equal(evidence.requestOutcome, 'not_confirmed'); assert.equal(evidence.requestReplayed, false);
    assert.equal(evidence.outcome, 'world_exit_independently_observed');
    assert.equal(evidence.error.status, 409); assert.equal(evidence.error.body, body);
    assert.equal(evidence.after.pressedKeyCount, 0); assert.equal(evidence.after.pressedButtonCount, 0);
    assert(!('response' in evidence));
  }
});

test('production Save and Quit preserves an actual response separately from independently observed exit', async () => {
  const fixture = worldExit();
  assert.equal(await fixture.run(), fixture.titleTree);
  assert.equal(fixture.samples.length, 1);
  assert.equal(fixture.samples[0].requestOutcome, 'response_received');
  assert.equal(fixture.samples[0].requestReplayed, false); assert.equal(fixture.samples[0].response, fixture.response);
  assert.equal(fixture.response.type, 'ui.action_result'); assert(!('executionOutcome' in fixture.response));
  assert.equal(fixture.calls.filter(call => call[0] === 'ui').length, 1);
});

test('Save and Quit leaves unrelated 409, server failure, unstructured errors and unsafe retry outcomes unchanged', async () => {
  const expected = { error: 'REQUEST_CANCELLED', executionOutcome: 'not_confirmed', retrySafe: false };
  for (const error of [new Error(JSON.stringify(expected)),
    Object.assign(new Error('server failed'), { httpStatus: 500, nativeError: expected }),
    Object.assign(new Error('target invalid'), { httpStatus: 409, nativeError: { ...expected, error: 'UI_TARGET_INVALIDATED' } }),
    Object.assign(new Error('retry is safe'), { httpStatus: 409, nativeError: { ...expected, retrySafe: true } }),
    Object.assign(new Error('confirmed'), { httpStatus: 409, nativeError: { ...expected, executionOutcome: 'completed' } })]) {
    const fixture = worldExit({ error, failUi: true });
    await assert.rejects(fixture.run(), actual => actual === error);
    assert.equal(fixture.samples.length, 0); assert.equal(fixture.calls.filter(call => call[0] === 'ui').length, 1);
    assert(!fixture.calls.some(call => call[0] === 'title'));
    if (error && typeof error === 'object') { assert(!('status' in error)); assert(!('runtimeError' in error)); }
  }
});

test('an interrupted Save and Quit cannot become successful evidence with obstructed title, stale context or held input', async () => {
  for (const invalid of [{ title: { overlayIdentity: 1 } }, { title: { screenClass: 'PauseScreen' } },
    { after: { inputContextId: 'owned-world' } }, { after: { inputGeneration: 7 } },
    { after: { pressedKeyCount: 1 } }, { after: { pressedButtonCount: 1 } }, { after: { inputCleanupFailed: true } },
    { released: { passed: false } }, { released: { evidence: { pressedKeyCount: 1, pressedButtonCount: 0 } } }]) {
    const error = Object.assign(new Error('cancelled'), { httpStatus: 409,
      nativeError: { error: 'REQUEST_CANCELLED', executionOutcome: 'not_confirmed', retrySafe: false } });
    const fixture = worldExit({ ...invalid, error, failUi: true });
    await assert.rejects(fixture.run()); assert.equal(fixture.samples.length, 0);
    assert.equal(fixture.calls.filter(call => call[0] === 'ui').length, 1);
  }
});

test('the real title path delegates Save and Quit and hashes both imported exit helpers in result provenance', () => {
  const titleStart = source.indexOf('async function title('), titleEnd = source.indexOf('\nasync function check(', titleStart);
  assert.match(source.slice(titleStart, titleEnd), /if\(quit\)return saveQuit\(quit\)/);
  assert.match(source, /import \{finishBaselineWorldExit\} from '\.\.\/\.\.\/conformance-world_read\/task\/interaction-baseline-actions\.mjs'/);
  const hashesStart = source.indexOf('async function sourceHashes('), hashesEnd = source.indexOf('\nconst startedSources=', hashesStart);
  const hashes = source.slice(hashesStart, hashesEnd);
  for (const name of ['interaction-baseline-actions.mjs', 'context-exit-evidence.mjs']) assert(hashes.includes(name));
  assert.match(source, /result\.worldExitSamples=worldExitSamples;/);
});

const respawnStart = source.indexOf('async function respawnFromDeath(');
const respawnEnd = source.indexOf('\nasync function check(', respawnStart);
assert(respawnStart >= 0 && respawnEnd > respawnStart, 'Production native Respawn boundary');
const respawn = new vm.Script('(' + source.slice(respawnStart, respawnEnd) + ')');

function respawnFixture(options = {}) {
  let milliseconds = 0, clicked = false, frameIndex = 0;
  const calls = [], samples = [], response = { type: 'ui.action_result', targetingSource: 'interaction_tree', directMutationUsed: false };
  const before = { inputGeneration: 7, inputContextGeneration: 3, inputContextId: 'dead-player-context', inputContextAvailable: true,
    pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false, clientTick: 10, inputDispatchSequence: 41, ...options.before };
  const controlBefore = { mode: 'TAKEOVER', takeoverActive: true, status: 'active', reconsentRequired: false, leaseId: 'owned-lease',
    modeVersion: { controlSessionId: 'owned-control', generation: 5 } };
  const button = { nodeId: 'native-respawn', translationKey: 'deathScreen.respawn', active: true, visible: true };
  const death = { screenClass: 'net.minecraft.client.gui.screens.DeathScreen', overlayIdentity: 0, children: [button], ...options.death };
  const frames = options.frames ?? [{ tick: 11 }, { tick: 12 }];
  const current = () => frames[Math.min(frameIndex, frames.length - 1)];
  const state = () => ({ ...before, inputGeneration: 8, inputContextGeneration: 4, inputContextId: 'respawned-player-context',
    inputDispatchSequence: options.lateInput && frameIndex >= 2 ? 45 : 44, clientTick: current().tick, ...options.after, ...current().input });
  const context = {
    assert, lease: 'owned-lease', respawnSamples: samples,
    nodes: value => value.children ?? [],
    tree: async () => {
      calls.push('tree'); if (!clicked) return death;
      return { clientTick: current().tick, screenClass: '', screenIdentity: 0, screenRevision: 8, overlayIdentity: 0, ...current().ui };
    },
    ui: async selector => { calls.push(['ui', selector]); clicked = true; if (options.error) throw options.error; return response; },
    api: async (method, path) => {
      calls.push([method, path]); assert.equal(method, 'GET');
      if (path === '/v0/input/state') return clicked ? state() : before;
      if (path === '/v0/control/status') {
        if (!clicked) return controlBefore;
        const control = { ...controlBefore, ...options.control, ...current().control }; frameIndex++; return control;
      }
      assert.equal(path, '/v0/player?category=USER');
      return { available: true, dataCategory: 'USER', health: 20, clientTick: current().tick, ...options.player, ...current().player };
    }
  };
  const run = respawn.runInContext(vm.createContext(context));
  return { calls, samples, response, run: () => run(button, { now: () => milliseconds, sleep: async millis => { milliseconds += millis; } }) };
}

test('production Respawn retains the original epoch-cancelled RPC and independently proves new player readiness without replay', async () => {
  for (const code of ['REQUEST_CANCELLED', 'CONTROL_INPUT_GENERATION_CHANGED']) {
    const body = { error: code, executionOutcome: 'not_confirmed', retrySafe: false, requestId: 'native-respawn-click' };
    const fixture = respawnFixture({ error: Object.assign(new Error('old player replaced'), { httpStatus: 409, nativeError: body }) });
    const result = await fixture.run(); assert.equal(fixture.samples.length, 1); assert.equal(result, fixture.samples[0]);
    assert.equal(result.requestOutcome, 'not_confirmed'); assert.equal(result.requestReplayed, false); assert.equal(result.requestEffectConfirmed, false);
    assert.equal(result.error.httpStatus, 409); assert.equal(result.error.body, body); assert(!('response' in result));
    assert.equal(result.outcome, 'respawn_independently_observed'); assert.equal(result.stableNativeFrames.length, 2);
    assert(result.stableNativeFrames[1].ui.clientTick > result.stableNativeFrames[0].ui.clientTick);
    assert.notEqual(result.after.inputContextId, result.before.inputContextId);
    assert(result.after.inputGeneration > result.before.inputGeneration); assert(result.after.inputContextGeneration > result.before.inputContextGeneration);
    assert(result.negativeObservationMillis >= 500); assert.equal(result.postRespawnInputDispatch, 0);
    assert.equal(result.sameLeasePreserved, true); assert.equal(result.sameModeVersionPreserved, true);
    assert.equal(fixture.calls.filter(call => Array.isArray(call) && call[0] === 'ui').length, 1);
    assert(!fixture.calls.some(call => Array.isArray(call) && call[0] === 'POST'));
  }
});

test('successful Respawn response remains a separate receipt, never invented server confirmation', async () => {
  const fixture = respawnFixture(), result = await fixture.run();
  assert.equal(result.requestOutcome, 'response_received'); assert.equal(result.response, fixture.response);
  assert.equal(result.requestEffectConfirmed, false); assert.equal(result.requestReplayed, false);
  assert(!('effectConfirmed' in fixture.response)); assert.equal(result.later.pressedKeyCount, 0); assert.equal(result.later.pressedButtonCount, 0);
});

test('Respawn waits through a real loading Screen and pending cleanup until two distinct unobstructed native frames', async () => {
  const fixture = respawnFixture({ frames: [
    { tick: 11, ui: { screenClass: 'ReceivingLevelScreen', screenIdentity: 88 }, input: { inputCleanupPending: true, pressedButtonCount: 1 } },
    { tick: 12, ui: { overlayIdentity: 8 } }, { tick: 13 }, { tick: 14 }
  ] });
  const result = await fixture.run(); assert.equal(result.observations.length, 4);
  assert.deepEqual(Array.from(result.stableNativeFrames, value => value.ui.clientTick), [13, 14]);
  assert.equal(fixture.calls.filter(call => Array.isArray(call) && call[0] === 'ui').length, 1);
});

test('ordinary UI errors and nonmatching cancellation outcomes are not made Respawn tolerant', async () => {
  const expected = { error: 'REQUEST_CANCELLED', executionOutcome: 'not_confirmed', retrySafe: false };
  for (const error of [new Error(JSON.stringify(expected)),
    Object.assign(new Error('server failed'), { httpStatus: 500, nativeError: expected }),
    Object.assign(new Error('target invalid'), { httpStatus: 409, nativeError: { ...expected, error: 'UI_TARGET_INVALIDATED' } }),
    Object.assign(new Error('retry'), { httpStatus: 409, nativeError: { ...expected, retrySafe: true } }),
    Object.assign(new Error('confirmed'), { httpStatus: 409, nativeError: { ...expected, executionOutcome: 'completed' } })]) {
    const fixture = respawnFixture({ error }); await assert.rejects(fixture.run(), actual => actual === error);
    assert.equal(fixture.samples.length, 0); assert.equal(fixture.calls.filter(call => Array.isArray(call) && call[0] === 'ui').length, 1);
    assert(!fixture.calls.some(call => Array.isArray(call) && call[1] === '/v0/player?category=USER'));
  }
});

test('only an actual enabled DeathScreen Respawn target and available native context may issue the single request', async () => {
  for (const options of [{ death: { screenClass: 'PauseScreen' } }, { death: { overlayIdentity: 1 } },
    { death: { children: [] } }, { before: { inputContextAvailable: false } }, { before: { pressedButtonCount: 1 } }]) {
    const fixture = respawnFixture(options); await assert.rejects(fixture.run());
    assert(!fixture.calls.some(call => Array.isArray(call) && call[0] === 'ui')); assert.equal(fixture.samples.length, 0);
  }
});

test('stale or unready player, duplicate native frames, held input and changed control cannot become passing Respawn evidence', async () => {
  for (const options of [{ after: { inputContextId: 'dead-player-context' } }, { after: { inputGeneration: 7 } },
    { after: { inputContextGeneration: 3 } }, { after: { inputContextAvailable: false } }, { after: { inputCleanupPending: true } },
    { after: { inputCleanupFailed: true } }, { after: { pressedKeyCount: 1 } }, { after: { pressedButtonCount: 1 } },
    { player: { available: false } }, { player: { health: 0 } }, { player: { health: NaN } }, { player: { dataCategory: 'DEBUG' } },
    { frames: [{ tick: 11 }, { tick: 11 }] }, { control: { leaseId: 'other-lease' } },
    { control: { modeVersion: { controlSessionId: 'owned-control', generation: 6 } } },
    { control: { mode: 'READ', takeoverActive: false, status: 'available', reconsentRequired: true } }]) {
    const fixture = respawnFixture(options); await assert.rejects(fixture.run()); assert.equal(fixture.samples.length, 0);
    assert.equal(fixture.calls.filter(call => Array.isArray(call) && call[0] === 'ui').length, 1);
  }
});

test('late input after ready respawn frames fails the complete 500ms observation instead of producing a sample', async () => {
  const fixture = respawnFixture({ lateInput: true }); await assert.rejects(fixture.run(), /Late Agent input/);
  assert.equal(fixture.samples.length, 0); assert.equal(fixture.calls.filter(call => Array.isArray(call) && call[0] === 'ui').length, 1);
});

test('only the existing death check invokes the explicit boundary and result records independent Respawn samples', () => {
  assert.equal((source.match(/await respawnFromDeath\(buttons\[0\]\)/g) ?? []).length, 1);
  assert.match(source, /result\.respawnSamples=respawnSamples;/);
  assert.match(source, /check\('death: native respawn button and live world restored'/);
});
