import assert from 'node:assert/strict';
import { isDeepStrictEqual } from 'node:util';
import { auditAppliedUiMutation, auditUiDebugCapabilities } from './ui-debug-capability-audit.mjs';

const timesOperation = 'ui.hud.title.times.set';
const clearOperation = 'ui.hud.title.clear';
const selectionOperation = 'ui.list.selection.set';
const timing = node => structuredClone(node.debugTitleTimes);
const titles = view => view.presentation?.children?.find(node => node.nodeId === 'hud.titles');
const spectator = (view, nodes) => nodes(view).find(node => node.nodeId === 'spectator.menu');

export function titleTimesRequest(node, value = timing(node)) {
  assert.equal(node.nodeId, 'hud.titles');
  assert.equal(node.debugTitleGuardAvailable, true);
  assert(node.resourceVersion && node.debugMutation?.supportedOperations.includes(timesOperation));
  for (const key of ['fadeInTicks', 'stayTicks', 'fadeOutTicks'])
    assert(Number.isSafeInteger(node.debugTitleTimes[key]) && node.debugTitleTimes[key] >= 0);
  return { operation: timesOperation, nodeId: node.nodeId, value,
    expectedValue: timing(node), expectedResourceVersion: structuredClone(node.resourceVersion) };
}

export function spectatorSelectionRequest(node, value = node.debugSelectionIndex) {
  assert.equal(node.nodeId, 'spectator.menu');
  assert.equal(node.debugSelectionGuardAvailable, true);
  assert.equal(node.debugSelectionUnit, 'native_entry_index');
  assert(node.resourceVersion && node.debugMutation?.supportedOperations.includes(selectionOperation));
  assert(Number.isInteger(node.debugSelectionIndex) && node.debugSelectionIndex >= -1 && node.debugSelectionIndex <= 8);
  return { operation: selectionOperation, nodeId: node.nodeId, value,
    expectedValue: node.debugSelectionIndex, expectedResourceVersion: structuredClone(node.resourceVersion) };
}

export async function restoreTitleTiming({ snapshot, applyUiMutation, original, confirmed }) {
  const current = titles(await snapshot());
  assert(current?.resourceVersion, 'Original native title owner is required for timing recovery');
  if (isDeepStrictEqual(current.debugTitleTimes, original)) return { written: false };
  assert(confirmed?.resourceVersion && confirmed.value, 'Unconfirmed title mutation must be preserved, not overwritten');
  assert.deepEqual(current.debugTitleTimes, confirmed.value, 'Title timing changed externally');
  assert.deepEqual(current.resourceVersion, confirmed.resourceVersion, 'Title lifecycle or content changed externally');
  const result = await applyUiMutation(titleTimesRequest(current, original));
  auditAppliedUiMutation(result);
  assert.deepEqual(result.after?.debugTitleTimes, original);
  assert(result.afterResourceVersion);
  return { written: true };
}

export async function restoreSpectatorSelection({ snapshot, nodes, applyUiMutation, original, confirmed }) {
  const current = spectator(await snapshot(), nodes);
  assert(current?.resourceVersion, 'Original native spectator menu is required for recovery');
  if (current.debugSelectionIndex === original) return { written: false };
  assert(Number.isInteger(original) && original >= 0, 'Initial -1 is not restorable by pure native highlighting');
  assert(confirmed?.resourceVersion && Number.isInteger(confirmed.value), 'Unconfirmed spectator mutation must be preserved');
  assert.equal(current.debugSelectionIndex, confirmed.value, 'Spectator selection changed externally');
  assert.deepEqual(current.resourceVersion, confirmed.resourceVersion, 'Spectator owner, page or content changed externally');
  const result = await applyUiMutation(spectatorSelectionRequest(current, original));
  auditAppliedUiMutation(result);
  assert.equal(result.after?.debugSelectionIndex, original);
  assert(result.afterResourceVersion);
  return { written: true };
}

async function noInput(api, before) {
  const after = await api('GET', '/v0/input/state');
  assert.equal(after.inputDispatchSequence, before.inputDispatchSequence);
  assert.equal(after.pressedKeyCount, 0);
  assert.equal(after.pressedButtonCount, 0);
}

// The caller owns a fixed-world title/subtitle fixture with a long native lifetime, and an active
// session-bound UI Arm in OPERATE. No command, mode transition or player input is issued here.
export async function titleLifecycleAssertions({ target, api, snapshot, applyUiMutation, samples, pause }) {
  let view = await snapshot();
  auditUiDebugCapabilities(view, await api('GET', '/v0/ui/tree?category=USER'), target);
  let node = titles(view);
  titleTimesRequest(node);
  assert.equal(node.debugTitleCleared, false);
  const original = timing(node), before = await api('GET', '/v0/input/state');
  for (const value of Object.values(original)) assert(value <= 12000, 'Owned fixture timing must be safely restorable');
  const firstRemaining = node.debugTitleRemainingTicks, stableVersion = structuredClone(node.resourceVersion);
  assert(Number.isInteger(firstRemaining) && firstRemaining > 200, 'A live long-lived native title fixture is required');
  let previousTick = view.clientTick;
  const observedNativeTicks = [];
  for (let attempt = 0; attempt < 40 && observedNativeTicks.length < 6; attempt++) {
    await pause();
    view = await snapshot();
    node = titles(view);
    assert(Number.isSafeInteger(view.clientTick) && view.clientTick >= previousTick);
    assert(node?.resourceVersion);
    assert.deepEqual(node.resourceVersion, stableVersion, 'Ordinary native countdown must not invalidate a title receipt');
    if (view.clientTick > previousTick) {
      observedNativeTicks.push(view.clientTick);
      previousTick = view.clientTick;
    }
  }
  assert.equal(observedNativeTicks.length, 6);
  assert(node.debugTitleRemainingTicks < firstRemaining, 'Actual native lifetime must advance, not just repeated reads');
  const value = { fadeInTicks: 5, stayTicks: 400, fadeOutTicks: 5 };
  const accepted = titleTimesRequest(node, value);
  let confirmed, failure;
  try {
    const changed = await applyUiMutation(accepted);
    auditAppliedUiMutation(changed);
    assert.equal(changed.sideEffects, 'native_hud_title_timing_and_remaining_reset');
    assert.deepEqual(changed.after?.debugTitleTimes, value);
    assert(changed.afterResourceVersion);
    assert(changed.after.debugTitleRemainingTicks > 0 && changed.after.debugTitleRemainingTicks <= 410);
    confirmed = { value, resourceVersion: structuredClone(changed.afterResourceVersion) };
    assert.notDeepEqual(confirmed.resourceVersion, accepted.expectedResourceVersion);
    await assert.rejects(api('POST', '/v0/debug/mutations', accepted), error => error.code === 'STALE_RESOURCE_REVISION');
    node = titles(await snapshot());
    assert.deepEqual(node.resourceVersion, confirmed.resourceVersion);
    await assert.rejects(api('POST', '/v0/debug/mutations', {
      ...titleTimesRequest(node), value: { fadeInTicks: 0, stayTicks: 1 },
    }), error => error.code === 'INVALID_UI_DEBUG_VALUE');
    assert.deepEqual(titles(await snapshot()).resourceVersion, confirmed.resourceVersion);
  } catch (error) {
    failure = error;
    throw error;
  } finally {
    try {
      await restoreTitleTiming({ snapshot, applyUiMutation, original, confirmed });
    } catch (error) {
      if (failure) failure.message += `; timing cleanup: ${error.message}`;
      else throw error;
    }
  }
  await noInput(api, before);
  samples.push({ case: 'native_title_lifecycle_timing', observedNativeTicks,
    countdownReceiptStable: true, nativeTimingChanged: true, timingRestored: true,
    staleRejected: true, malformedRejected: true, inputDelta: 0, elapsedLifetimeRestored: false });

  node = titles(await snapshot());
  assert.equal(node.debugTitleCleared, false);
  const clear = { operation: clearOperation, nodeId: node.nodeId, value: true,
    expectedValue: false, expectedResourceVersion: node.resourceVersion };
  await assert.rejects(api('POST', '/v0/debug/mutations', { ...clear, value: false }), error => error.code === 'INVALID_UI_DEBUG_VALUE');
  const cleared = await applyUiMutation(clear);
  auditAppliedUiMutation(cleared);
  assert.equal(cleared.sideEffects, 'native_hud_title_and_subtitle_clear');
  assert.equal(cleared.after?.debugTitleCleared, true);
  assert.equal(cleared.after?.debugTitleRemainingTicks, 0);
  assert(cleared.afterResourceVersion);
  assert.equal(titles(await snapshot()).debugTitleCleared, true);
  const publicView = await api('GET', '/v0/ui/tree?category=USER');
  assert(!publicView.presentation?.children?.some(item => ['hud.title', 'hud.subtitle'].includes(item.controlKey)));
  await noInput(api, before);
  samples.push({ case: 'native_title_lifecycle_clear', wholeGroupCleared: true,
    remainingTicks: 0, userTitleAndSubtitleAbsent: true, oneWayOwnedFixture: true,
    inputDelta: 0, textOrLifetimeRestored: false });
}

// Caller opens the real native menu through USER input and first selects a nonnegative disabled
// category. Finish within its native lifetime; never alter the timeout or invoke an item callback.
export async function spectatorSelectionAssertions({ target, api, snapshot, nodes, applyUiMutation, samples }) {
  let view = await snapshot();
  auditUiDebugCapabilities(view, await api('GET', '/v0/ui/tree?category=USER'), target);
  let node = spectator(view, nodes);
  spectatorSelectionRequest(node);
  const original = node.debugSelectionIndex;
  assert(original >= 0, 'USER must establish a restorable native selection before DEBUG');
  const rows = nodes(view).filter(item => item.role === 'spectator_item');
  const alternative = rows.find(item => item.entryIndex !== original && item.activationEnabled === true);
  assert(alternative && Number.isInteger(alternative.entryIndex), 'An enabled item is needed to prove same-index does not activate');
  const owners = rows.map(item => [item.entryIndex, item.elementIdentity]);
  const before = await api('GET', '/v0/input/state');
  const player = await api('GET', '/v0/player?category=USER');
  assert.equal(player.available, true);
  assert.equal(player.dataCategory, 'USER');
  assert.equal(player.gameMode, 'spectator');
  assert.equal(typeof player.dimension, 'string');
  for (const field of ['x', 'y', 'z']) assert(Number.isFinite(player[field]));
  const accepted = spectatorSelectionRequest(node, alternative.entryIndex);
  let confirmed, failure;
  try {
    const changed = await applyUiMutation(accepted);
    auditAppliedUiMutation(changed);
    assert.equal(changed.sideEffects, 'native_local_spectator_highlight_not_activation');
    assert.equal(changed.after?.debugSelectionIndex, alternative.entryIndex);
    assert(changed.afterResourceVersion);
    confirmed = { value: alternative.entryIndex, resourceVersion: structuredClone(changed.afterResourceVersion) };
    view = await snapshot();
    node = spectator(view, nodes);
    assert.deepEqual(node.resourceVersion, confirmed.resourceVersion);
    assert.deepEqual(nodes(view).filter(item => item.role === 'spectator_item').map(item => [item.entryIndex, item.elementIdentity]), owners);
    assert(nodes(view).some(item => item.role === 'spectator_item' && item.entryIndex === alternative.entryIndex && item.selected));
    const repeated = await applyUiMutation(spectatorSelectionRequest(node));
    auditAppliedUiMutation(repeated);
    assert.equal(repeated.after?.debugSelectionIndex, alternative.entryIndex);
    assert.deepEqual(repeated.afterResourceVersion, confirmed.resourceVersion, 'Same-index highlight must be a strict native no-op');
  } catch (error) {
    failure = error;
    throw error;
  } finally {
    try {
      await restoreSpectatorSelection({ snapshot, nodes, applyUiMutation, original, confirmed });
    } catch (error) {
      if (failure) failure.message += `; spectator cleanup: ${error.message}`;
      else throw error;
    }
  }
  node = spectator(await snapshot(), nodes);
  assert.equal(node.debugSelectionIndex, original);
  await assert.rejects(api('POST', '/v0/debug/mutations', accepted), error => error.code === 'STALE_RESOURCE_REVISION');
  const current = spectatorSelectionRequest(node);
  await assert.rejects(api('POST', '/v0/debug/mutations', { ...current, value: -1 }), error => error.code === 'INVALID_UI_DEBUG_VALUE');
  await assert.rejects(api('POST', '/v0/debug/mutations', { ...current, expectedValue: original + 1 }), error => error.code === 'VALUE_PRECONDITION_FAILED');
  assert.deepEqual(spectator(await snapshot(), nodes).resourceVersion, node.resourceVersion);
  const afterPlayer = await api('GET', '/v0/player?category=USER');
  assert.equal(afterPlayer.available, true);
  assert.equal(afterPlayer.dataCategory, 'USER');
  for (const field of ['dimension', 'gameMode', 'x', 'y', 'z']) assert.equal(afterPlayer[field], player[field], 'Pure highlight must not teleport or change player state');
  await noInput(api, before);
  samples.push({ case: 'native_spectator_debug_highlight', original, value: alternative.entryIndex,
    actualItemOwnersStable: true, sameIndexNoOp: true, sameTokenAfterNoOp: true,
    restored: true, staleRejected: true, invalidMinusOneRejected: true, expectedValueRejected: true,
    userPositionUnchanged: true, inputDelta: 0, itemActivationUsed: false, initialMinusOneRestored: false });
}
