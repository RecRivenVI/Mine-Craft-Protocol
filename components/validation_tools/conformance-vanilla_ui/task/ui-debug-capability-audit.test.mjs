import assert from 'node:assert/strict';
import test from 'node:test';
import { auditUiDebugCapabilities, auditAppliedUiMutation } from './ui-debug-capability-audit.mjs';

function captured() {
  return {
    dataCategory: 'DEBUG', children: [{
      nodeId: 'native', class: 'native.Widget', resourceVersion: { revision: 1 },
      debugMutation: {
        supportedOperations: ['ui.widget.active.set'], operations: { 'ui.widget.active.set': { valueType: 'boolean' } },
        blockedOperations: {}, grantsPermission: false, requiresInputLease: false,
        requiresUiArm: true, requiresResourceVersion: true, inputDispatchCount: 0,
      },
    }],
  };
}
const user = { dataCategory: 'USER', children: [] };

test('audit accepts typed applicability, not a declaration of a completed catalogue', () => {
  const samples = auditUiDebugCapabilities(captured(), user, '26.3-neoforge');
  assert.equal(samples.length, 1);
  assert(!Object.hasOwn(samples[0], 'milestoneComplete'));
});

test('audit catches missing owner descriptors, USER leaks and missing native revisions', () => {
  const tree = captured();
  assert.throws(() => auditUiDebugCapabilities(tree, { dataCategory: 'USER', children: tree.children }, '26.3-neoforge'));
  delete tree.children[0].resourceVersion;
  assert.throws(() => auditUiDebugCapabilities(tree, user, '26.3-neoforge'));
  delete tree.children[0].debugMutation;
  assert.throws(() => auditUiDebugCapabilities(tree, user, '26.3-neoforge'));
});

test('audit rejects unavailable guards and contradictory operation declarations', () => {
  const tree = captured();
  tree.children[0].debugTextGuardAvailable = false;
  assert.throws(() => auditUiDebugCapabilities(tree, user, '26.3-neoforge'));
  delete tree.children[0].debugTextGuardAvailable;
  tree.children[0].debugMutation.blockedOperations['ui.widget.active.set'] = 'UI_CONTENT_GUARD_UNAVAILABLE';
  assert.throws(() => auditUiDebugCapabilities(tree, user, '26.3-neoforge'));
});

test('actual mutation must match the descriptor returned in its native before state', () => {
  const before = captured().children[0];
  const result = { dataCategory: 'DEBUG', evidenceAuthority: 'DEBUG_PRIVILEGED', inputDispatchCount: 0, operation: 'ui.widget.active.set', before };
  assert.equal(auditAppliedUiMutation(result).nativeDescriptorMatchedActualSetter, true);
  assert.throws(() => auditAppliedUiMutation({ ...result, operation: 'ui.toast.hide' }));
  assert.throws(() => auditAppliedUiMutation({ ...result, inputDispatchCount: 1 }));
});
