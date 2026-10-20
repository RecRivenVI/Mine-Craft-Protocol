import assert from 'node:assert/strict';
import test from 'node:test';
import { visibilityToastAssertions } from './visibility-toast-actions.mjs';

function runtime({ freezeClock = false, reviveNowPlaying = false } = {}) {
  const samples = [], mutations = [];
  let tick = 10, nowPlayingHiddenAt;
  const descriptor = { grantsPermission: false, requiresInputLease: false, requiresUiArm: true,
    requiresResourceVersion: true, inputDispatchCount: 0, supportedOperations: ['ui.toast.hide'],
    operations: { 'ui.toast.hide': { valueType: 'boolean', onlyValue: true } }, blockedOperations: {} };
  const owners = ['now_playing', 'friend'].map(kind => ({
    nodeId: kind === 'now_playing' ? 'toast.now_playing' : 'toast.0',
    class: kind === 'now_playing' ? 'native.NowPlayingToast' : 'native.FriendToast',
    debugToastKind: kind, debugToastGuardScope: 'native_visibility', debugToastGuardAvailable: true,
    debugHideRequested: false, debugMutation: structuredClone(descriptor),
    resourceVersion: { resourceKey: 'actual-instance-' + kind, revision: 1 },
  }));
  const snapshot = async () => {
    if (reviveNowPlaying && nowPlayingHiddenAt !== undefined && tick >= nowPlayingHiddenAt + 3)
      owners[0].debugHideRequested = false;
    return structuredClone({ dataCategory: 'DEBUG', clientTick: tick, children: [], presentation: { children: owners } });
  };
  const api = async (method, path, request) => {
    if (method === 'GET' && path === '/v0/ui/tree?category=USER')
      return { dataCategory: 'USER', children: [], presentation: { children: [] } };
    if (method === 'GET' && path === '/v0/input/state')
      return { inputDispatchSequence: 7, pressedKeyCount: 0, pressedButtonCount: 0 };
    assert.equal(method, 'POST');
    assert.equal(path, '/v0/debug/mutations');
    mutations.push(structuredClone(request));
    const owner = owners.find(node => node.nodeId === request.nodeId);
    assert(owner);
    const failure = code => Object.assign(new Error(code), { code });
    if (request.expectedResourceVersion.revision !== owner.resourceVersion.revision) throw failure('STALE_RESOURCE_REVISION');
    if (request.operation !== 'ui.toast.hide') throw failure('UI_DEBUG_UNSUPPORTED');
    if (request.value !== true) throw failure('INVALID_UI_DEBUG_VALUE');
    if (request.expectedValue !== owner.debugHideRequested) throw failure('VALUE_PRECONDITION_FAILED');
    const before = structuredClone(owner);
    owner.debugHideRequested = true;
    owner.resourceVersion.revision++;
    if (owner.debugToastKind === 'now_playing') nowPlayingHiddenAt = tick;
    return { operation: request.operation, dataCategory: 'DEBUG', evidenceAuthority: 'DEBUG_PRIVILEGED',
      inputDispatchCount: 0, before, after: structuredClone(owner), afterResourceVersion: structuredClone(owner.resourceVersion) };
  };
  return { samples, mutations, args: {
    target: '26.3-neoforge', api, snapshot,
    applyUiMutation: request => api('POST', '/v0/debug/mutations', request), samples,
    pause: async () => { if (!freezeClock) tick++; },
  } };
}

test('NowPlaying is legally hidden before rate-limited negatives, then six actual ticks and Friend run', async () => {
  const live = runtime();
  await visibilityToastAssertions(live.args);
  assert.deepEqual(live.mutations[0], {
    operation: 'ui.toast.hide', nodeId: 'toast.now_playing',
    expectedResourceVersion: { resourceKey: 'actual-instance-now_playing', revision: 1 },
    expectedValue: false, value: true,
  });
  assert.deepEqual(live.samples.map(sample => sample.kind), ['now_playing', 'friend']);
  assert.equal(live.samples[0].legalHideBeforeNegativeChecks, true);
  for (const sample of live.samples) {
    assert.equal(new Set(sample.observedNativeTicks).size, 6);
    assert.equal(sample.inputDelta, 0);
    assert.equal(sample.musicPlaybackTested, false);
  }
});

test('six repeated reads of the same native tick cannot pass no-revival evidence', async () => {
  const live = runtime({ freezeClock: true });
  await assert.rejects(visibilityToastAssertions(live.args), /Six distinct native tick/);
  assert.equal(live.samples.length, 0);
});

test('26.1.2 checks native NowPlaying only, while legacy Targets do not fabricate either type', async () => {
  const modern = runtime();
  await visibilityToastAssertions({...modern.args, target:'26.1.2-neoforge'});
  assert.deepEqual(modern.samples.map(sample=>sample.kind), ['now_playing']);
  assert(modern.mutations.every(request=>request.nodeId==='toast.now_playing'));
  for(const target of ['1.20.1-forge','1.21.1-neoforge']){
    const legacy = runtime();
    await visibilityToastAssertions({...legacy.args,target});
    assert.equal(legacy.samples.length,0);assert.equal(legacy.mutations.length,0);
  }
});

test('a later native SHOW on the same owner is a failure, not a natural expiry or replacement', async () => {
  const live = runtime({ reviveNowPlaying: true });
  await assert.rejects(visibilityToastAssertions(live.args), /no later SHOW/);
  assert.equal(live.samples.length, 0);
});
