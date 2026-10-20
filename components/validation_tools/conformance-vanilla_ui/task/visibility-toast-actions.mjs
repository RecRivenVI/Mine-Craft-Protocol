import assert from 'node:assert/strict';
import { auditAppliedUiMutation, auditUiDebugCapabilities } from './ui-debug-capability-audit.mjs';

// The caller explicitly prepares the probe-only native Toast owners and enters debugUi.
// These assertions do not claim music playback, friends-service, USER text or pixel coverage.
export async function visibilityToastAssertions({ target, api, snapshot, applyUiMutation, samples, pause }) {
  const kinds = target.startsWith('26.3') ? ['now_playing', 'friend'] : target.startsWith('26.1.2-') ? ['now_playing'] : [];
  for (const kind of kinds) {
    const deadline = Date.now() + 10000;
    let node, capturedTick;
    do {
      const captured = await snapshot();
      const publicView = await api('GET', '/v0/ui/tree?category=USER');
      auditUiDebugCapabilities(captured, publicView, target);
      node = captured.presentation.children.find(n => n.debugToastKind === kind && !n.debugHideRequested);
      capturedTick = captured.clientTick;
      if (node) break;
      await pause();
    } while (Date.now() < deadline);
    assert(node?.resourceVersion, `${kind}: actual resident native owner/version`);
    assert.equal(node.debugToastGuardScope, 'native_visibility');
    assert.deepEqual(node.debugMutation.supportedOperations, ['ui.toast.hide']);
    assert.equal(node.debugToastValue, undefined, 'Visibility token must not pretend to guard private text');
    const request = { operation: 'ui.toast.hide', nodeId: node.nodeId,
      expectedResourceVersion: node.resourceVersion, expectedValue: false, value: true };
    const before = await api('GET', '/v0/input/state');
    const negativeChecks = async (current, hidden) => {
      const accepted = { ...request, expectedResourceVersion: current.resourceVersion, expectedValue: hidden };
      for (const value of [false, null, 'true'])
        await assert.rejects(api('POST', '/v0/debug/mutations', { ...accepted, value }), e => e.code === 'INVALID_UI_DEBUG_VALUE');
      await assert.rejects(api('POST', '/v0/debug/mutations', { ...accepted, expectedValue: !hidden }), e => e.code === 'VALUE_PRECONDITION_FAILED');
      await assert.rejects(api('POST', '/v0/debug/mutations', { ...accepted, operation: 'ui.toast.reset', value: { title: 'not allowed', message: 'not allowed' } }), e => e.code === 'UI_DEBUG_UNSUPPORTED');
    };
    // Its native five-second display must be actively dismissed, not naturally expired while
    // repeated rate-limited negative requests run. The hidden owner remains independently readable.
    if (kind !== 'now_playing') await negativeChecks(node, false);
    const result = await applyUiMutation(request);
    auditAppliedUiMutation(result);
    assert(result.after === null || result.after.debugHideRequested === true);
    if (kind === 'now_playing') {
      assert.equal(result.before.debugHideRequested, false);
      assert(result.after?.resourceVersion && result.afterResourceVersion);
      assert.deepEqual(result.after.resourceVersion, result.afterResourceVersion);
      await negativeChecks(result.after, true);
    }
    await assert.rejects(api('POST', '/v0/debug/mutations', request), e => ['STALE_RESOURCE_REVISION', 'UI_NODE_NOT_FOUND', 'RESOURCE_MISMATCH'].includes(e.code));
    assert(Number.isInteger(capturedTick), 'Native client tick is required, not elapsed wall time');
    let previousTick = capturedTick;
    const observedNativeTicks = [];
    // Count distinct actual native tick observations; six HTTP reads of one paused tick do not pass.
    for (let attempt = 0; attempt < 40 && observedNativeTicks.length < 6; attempt++) {
      await pause();
      const view = await snapshot();
      assert(Number.isInteger(view.clientTick) && view.clientTick >= previousTick, 'Native clock regressed or was unavailable');
      const current = view.presentation.children.find(n => n.resourceVersion?.resourceKey === node.resourceVersion.resourceKey);
      if (kind === 'now_playing') assert(current, 'Independent native NowPlaying owner must remain present while hidden');
      if (current) assert.equal(current.debugHideRequested, true, kind + ': no later SHOW after native dismissal');
      if (view.clientTick > previousTick) {
        observedNativeTicks.push(view.clientTick);
        previousTick = view.clientTick;
      }
    }
    assert.equal(observedNativeTicks.length, 6, 'Six distinct native tick observations are required');
    const after = await api('GET', '/v0/input/state');
    assert.equal(after.inputDispatchSequence, before.inputDispatchSequence);
    assert.equal(after.pressedKeyCount, 0);
    assert.equal(after.pressedButtonCount, 0);
    samples.push({ case: 'native_visibility_toast_hide', kind, guardScope: 'native_visibility',
      actualOwner: true, oneWay: true, lateRevival: false, inputDelta: 0,
      ...(kind === 'now_playing' ? { displayUpdateTerminated: true, legalHideBeforeNegativeChecks: true } : {}),
      observedNativeTicks,
      worldMutation: false, musicPlaybackTested: false, friendsServiceTested: false });
  }
}
