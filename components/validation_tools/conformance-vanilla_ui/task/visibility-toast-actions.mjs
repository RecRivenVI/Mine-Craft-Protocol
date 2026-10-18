import assert from 'node:assert/strict';
import { auditAppliedUiMutation, auditUiDebugCapabilities } from './ui-debug-capability-audit.mjs';

// The caller explicitly prepares the probe-only native Toast owners and enters debugUi.
// These assertions do not claim music playback, friends-service, USER text or pixel coverage.
export async function visibilityToastAssertions({ target, api, snapshot, applyUiMutation, samples, pause }) {
  const kinds = target.startsWith('26.3') ? ['friend'] : [];
  for (const kind of kinds) {
    const deadline = Date.now() + 10000;
    let node;
    do {
      const captured = await snapshot();
      const publicView = await api('GET', '/v0/ui/tree?category=USER');
      auditUiDebugCapabilities(captured, publicView, target);
      node = captured.presentation.children.find(n => n.debugToastKind === kind && !n.debugHideRequested);
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
    for (const value of [false, null, 'true'])
      await assert.rejects(api('POST', '/v0/debug/mutations', { ...request, value }), e => e.code === 'INVALID_UI_DEBUG_VALUE');
    await assert.rejects(api('POST', '/v0/debug/mutations', { ...request, expectedValue: true }), e => e.code === 'VALUE_PRECONDITION_FAILED');
    await assert.rejects(api('POST', '/v0/debug/mutations', { ...request, operation: 'ui.toast.reset', value: { title: 'not allowed', message: 'not allowed' } }), e => e.code === 'UI_DEBUG_UNSUPPORTED');
    const result = await applyUiMutation(request);
    auditAppliedUiMutation(result);
    assert(result.after === null || result.after.debugHideRequested === true);
    await assert.rejects(api('POST', '/v0/debug/mutations', request), e => ['STALE_RESOURCE_REVISION', 'UI_NODE_NOT_FOUND', 'RESOURCE_MISMATCH'].includes(e.code));
    // Verify subsequent actual ticks do not revive the dismissed native owner.
    for (let tick = 0; tick < 6; tick++) {
      await pause();
      const current = (await snapshot()).presentation.children.find(n => n.resourceVersion?.resourceKey === node.resourceVersion.resourceKey);
      if (current) assert.equal(current.debugHideRequested, true, kind + ': no later SHOW after native dismissal');
    }
    const after = await api('GET', '/v0/input/state');
    assert.equal(after.inputDispatchSequence, before.inputDispatchSequence);
    assert.equal(after.pressedKeyCount, 0);
    assert.equal(after.pressedButtonCount, 0);
    samples.push({ case: 'native_visibility_toast_hide', kind, guardScope: 'native_visibility',
      actualOwner: true, oneWay: true, lateRevival: false, inputDelta: 0,
      worldMutation: false, musicPlaybackTested: false, friendsServiceTested: false });
  }
}
