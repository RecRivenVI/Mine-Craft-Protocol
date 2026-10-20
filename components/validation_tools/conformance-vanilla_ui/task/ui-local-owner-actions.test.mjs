import assert from 'node:assert/strict';
import test from 'node:test';
import { titleTimesRequest, spectatorSelectionRequest, restoreTitleTiming, restoreSpectatorSelection } from './ui-local-owner-actions.mjs';

const token = revision => ({ sessionEpoch: 'owned', resourceType: 'ui_node', resourceKey: 'native-owner', lifecycleId: 'same-owner', revision,
  revisionSource: 'runtime', revisionScope: 'resource', mutationPreconditionEligible: true });
const timing = stayTicks => ({ fadeInTicks: 10, stayTicks, fadeOutTicks: 20 });
const title = (stayTicks = 400, revision = 2) => ({ nodeId: 'hud.titles', debugTitleGuardAvailable: true,
  debugTitleTimes: timing(stayTicks), resourceVersion: token(revision),
  debugMutation: { supportedOperations: ['ui.hud.title.times.set', 'ui.hud.title.clear'] } });
const selection = (index = 8, revision = 2) => ({ nodeId: 'spectator.menu', debugSelectionGuardAvailable: true,
  debugSelectionIndex: index, debugSelectionUnit: 'native_entry_index', resourceVersion: token(revision),
  debugMutation: { supportedOperations: ['ui.list.selection.set'] } });
const nodes = view => view.children;
const titleView = current => ({ presentation: { children: [current] } });
const selectionView = current => ({ children: [current] });
const receipt = (before, after, operation) => ({ dataCategory: 'DEBUG', evidenceAuthority: 'DEBUG_PRIVILEGED', inputDispatchCount: 0,
  operation, before, after, afterResourceVersion: after.resourceVersion });

test('requests require their actual descriptor and permit observed old native values, not invented owners', () => {
  assert.deepEqual(titleTimesRequest(title(13000), timing(400)).expectedValue, timing(13000));
  assert.equal(spectatorSelectionRequest(selection(-1), 0).expectedValue, -1);
  for (const change of [{ nodeId: 'hud.title' }, { debugTitleGuardAvailable: false }, { resourceVersion: undefined }, { debugMutation: { supportedOperations: [] } }])
    assert.throws(() => titleTimesRequest({ ...title(), ...change }));
  for (const change of [{ nodeId: 'spectator.slot.0' }, { debugSelectionGuardAvailable: false }, { debugSelectionUnit: 'pixels' }, { resourceVersion: undefined }, { debugSelectionIndex: -2 }])
    assert.throws(() => spectatorSelectionRequest({ ...selection(), ...change }));
});

test('title timing restores only a confirmed value with its complete after token', async () => {
  const current = title(), calls = [];
  const result = await restoreTitleTiming({ snapshot: async () => titleView(current), original: timing(6000),
    confirmed: { value: timing(400), resourceVersion: token(2) },
    applyUiMutation: async request => { calls.push(request); return receipt(current, title(6000, 3), request.operation); } });
  assert.deepEqual(result, { written: true });
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0], titleTimesRequest(current, timing(6000)));
});

test('spectator recovery uses only a confirmed nonnegative selection and never replays activation', async () => {
  const current = selection(), calls = [];
  const result = await restoreSpectatorSelection({ snapshot: async () => selectionView(current), nodes, original: 0,
    confirmed: { value: 8, resourceVersion: token(2) },
    applyUiMutation: async request => { calls.push(request); return receipt(current, selection(0, 3), request.operation); } });
  assert.deepEqual(result, { written: true });
  assert.equal(calls.length, 1);
  assert.equal(calls[0].operation, 'ui.list.selection.set');
  assert.equal(calls[0].value, 0);
});

test('lost response, external same-value reset, replacement and external content changes preserve native state', async () => {
  const differentOwner = { ...token(2), lifecycleId: 'replacement' };
  for (const [current, confirmed] of [
    [title(), undefined],
    [title(401), { value: timing(400), resourceVersion: token(2) }],
    [title(400, 3), { value: timing(400), resourceVersion: token(2) }],
    [{ ...title(), resourceVersion: differentOwner }, { value: timing(400), resourceVersion: token(2) }],
  ]) {
    let writes = 0;
    await assert.rejects(restoreTitleTiming({ snapshot: async () => titleView(current), original: timing(6000), confirmed,
      applyUiMutation: async () => { writes++; } }));
    assert.equal(writes, 0);
  }
  for (const [current, confirmed, original] of [
    [selection(), undefined, 0],
    [selection(1), { value: 8, resourceVersion: token(2) }, 0],
    [selection(8, 3), { value: 8, resourceVersion: token(2) }, 0],
    [{ ...selection(), resourceVersion: differentOwner }, { value: 8, resourceVersion: token(2) }, 0],
    [selection(), { value: 8, resourceVersion: token(2) }, -1],
  ]) {
    let writes = 0;
    await assert.rejects(restoreSpectatorSelection({ snapshot: async () => selectionView(current), nodes, original, confirmed,
      applyUiMutation: async () => { writes++; } }));
    assert.equal(writes, 0);
  }
});

test('already original values require no write even if a receipt was lost', async () => {
  let writes = 0;
  assert.deepEqual(await restoreTitleTiming({ snapshot: async () => titleView(title(6000, 99)), original: timing(6000),
    applyUiMutation: async () => { writes++; } }), { written: false });
  assert.deepEqual(await restoreSpectatorSelection({ snapshot: async () => selectionView(selection(0, 99)), nodes, original: 0,
    applyUiMutation: async () => { writes++; } }), { written: false });
  assert.equal(writes, 0);
});
