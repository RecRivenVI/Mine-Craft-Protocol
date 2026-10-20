import assert from 'node:assert/strict';
import test from 'node:test';
import { suggestionSelectionRequest, restoreSuggestionSelection } from './suggestion-debug-actions.mjs';

const token = revision => ({ sessionEpoch: 'owned', resourceType: 'ui_node', resourceKey: 'chat:suggestions', lifecycleId: 'original-list', revision, revisionSource: 'runtime', revisionScope: 'resource', mutationPreconditionEligible: true });
const node = (value = 1, revision = 2) => ({
  nodeId: 'native-list', controlKey: 'chat.suggestions', role: 'list',
  debugSelectionIndex: value, debugSelectionRange: { minimum: 0, maximum: 2 },
  debugSelectionUnit: 'native_entry_index', debugSelectionGuardAvailable: true,
  resourceVersion: token(revision),
  debugMutation: { supportedOperations: ['ui.list.selection.set'] },
});

test('typed selection requires the real parent-owner descriptor and complete content guard', () => {
  assert.deepEqual(suggestionSelectionRequest(node(), 2), {
    operation: 'ui.list.selection.set', nodeId: 'native-list', expectedResourceVersion: token(2), expectedValue: 1, value: 2,
  });
  for (const change of [
    { role: 'suggestion' }, { controlKey: 'different.list' }, { debugSelectionGuardAvailable: false },
    { resourceVersion: undefined }, { debugSelectionUnit: 'normalized' },
    { debugMutation: { supportedOperations: [] } }, { debugSelectionIndex: 3 },
  ]) assert.throws(() => suggestionSelectionRequest({ ...node(), ...change }, 0));
});

test('owned restoration uses the exact confirmed value and complete token', async () => {
  const calls = [];
  const originalNode = node();
  const result = await restoreSuggestionSelection({
    snapshot: async () => ({ children: [originalNode] }), nodes: tree => tree.children,
    applyUiMutation: async request => {
      calls.push(request);
      return { dataCategory: 'DEBUG', evidenceAuthority: 'DEBUG_PRIVILEGED', inputDispatchCount: 0,
        operation: request.operation, before: originalNode, after: node(0, 3), afterResourceVersion: token(3) };
    },
    nodeId: originalNode.nodeId, original: 0, confirmed: { value: 1, resourceVersion: token(2) },
  });
  assert.deepEqual(result, { written: true, index: 0 });
  assert.equal(calls.length, 1);
  assert.deepEqual(calls[0], suggestionSelectionRequest(originalNode, 0));
});

test('response loss, external changes and same-value ABA never authorize restoration', async () => {
  for (const [current, confirmed] of [
    [node(), undefined],
    [node(2), { value: 1, resourceVersion: token(2) }],
    [node(1, 3), { value: 1, resourceVersion: token(2) }],
    [{ ...node(), resourceVersion: { ...token(2), lifecycleId: 'replacement-list' } }, { value: 1, resourceVersion: token(2) }],
  ]) {
    let writes = 0;
    await assert.rejects(restoreSuggestionSelection({
      snapshot: async () => ({ children: [current] }), nodes: tree => tree.children,
      applyUiMutation: async () => { writes++; }, nodeId: current.nodeId, original: 0, confirmed,
    }));
    assert.equal(writes, 0);
  }
});

test('an already restored original selection performs no further write', async () => {
  let writes = 0;
  const result = await restoreSuggestionSelection({
    snapshot: async () => ({ children: [node(0, 99)] }), nodes: tree => tree.children,
    applyUiMutation: async () => { writes++; }, nodeId: 'native-list', original: 0,
  });
  assert.deepEqual(result, { written: false, index: 0 });
  assert.equal(writes, 0);
});
