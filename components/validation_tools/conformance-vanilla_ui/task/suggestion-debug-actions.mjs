import assert from 'node:assert/strict';
import { auditAppliedUiMutation } from './ui-debug-capability-audit.mjs';

const operation = 'ui.list.selection.set';
const controlKey = 'chat.suggestions';
const privateFields = ['debugSelectionIndex', 'debugSelectionRange', 'debugSelectionUnit', 'debugSelectionGuardAvailable', 'resourceVersion', 'debugMutation'];

export function suggestionSelectionRequest(node, value = node.debugSelectionIndex) {
  assert.equal(node.controlKey, controlKey);
  assert.equal(node.role, 'list');
  assert.equal(node.debugSelectionGuardAvailable, true);
  assert.equal(node.debugSelectionUnit, 'native_entry_index');
  assert(node.resourceVersion);
  assert(node.debugMutation?.supportedOperations.includes(operation));
  const { minimum, maximum } = node.debugSelectionRange;
  assert.equal(minimum, 0);
  assert(Number.isInteger(maximum) && maximum >= minimum);
  assert(Number.isInteger(node.debugSelectionIndex) && node.debugSelectionIndex >= minimum && node.debugSelectionIndex <= maximum);
  return { operation, nodeId: node.nodeId, expectedResourceVersion: node.resourceVersion,
    expectedValue: node.debugSelectionIndex, value };
}

export async function restoreSuggestionSelection({ snapshot, nodes, applyUiMutation, nodeId, original, confirmed }) {
  const current = nodes(await snapshot()).find(node => node.nodeId === nodeId);
  assert(current?.resourceVersion, 'Original native suggestion owner is required for recovery');
  if (current.debugSelectionIndex === original) return { written: false, index: original };
  assert(confirmed?.resourceVersion && Number.isInteger(confirmed.value), 'No confirmed owned suggestion mutation; preserve instead of cleanup');
  assert.equal(current.debugSelectionIndex, confirmed.value, 'Selection changed externally; preserve instead of cleanup');
  assert.deepEqual(current.resourceVersion, confirmed.resourceVersion, 'Suggestion token changed externally; preserve instead of cleanup');
  const result = await applyUiMutation(suggestionSelectionRequest(current, original));
  auditAppliedUiMutation(result);
  assert.equal(result.after?.debugSelectionIndex, original);
  assert(result.afterResourceVersion);
  return { written: true, index: original };
}

// Caller owns the fixed world, task and Lease; these checks never acquire control on their own.
export async function suggestionDebugActions({
  api, wait, tree, nodes, ui, key, check, debugUi, applyUiMutation,
  editorHandoff, editorSamples, closeOwnedChat, samples,
}) {
  const prefix = '/gam';
  const pick = view => nodes(view).find(node => node.controlKey === controlKey && node.role === 'list');
  const draft = view => {
    const editors = nodes(view).filter(node => node.role === 'text_field');
    assert.equal(editors.length, 1, 'The owned native Chat input must be unambiguous');
    return editors[0].value;
  };
  const noInput = async before => {
    const after = await api('GET', '/v0/input/state');
    assert.equal(after.inputDispatchSequence, before.inputDispatchSequence);
    assert.equal(after.pressedKeyCount, 0);
    assert.equal(after.pressedButtonCount, 0);
  };
  const ready = async snapshot => {
    const deadline = Date.now() + 10000;
    let view;
    do {
      view = await snapshot();
      const node = pick(view);
      if (node) {
        assert.equal(draft(view), prefix);
        suggestionSelectionRequest(node);
        assert(node.debugSelectionRange.maximum >= 1, 'Fixture needs at least two real native suggestions');
        const user = await api('GET', '/v0/ui/tree?category=USER');
        for (const publicNode of nodes(user))
          for (const field of privateFields) assert(!Object.hasOwn(publicNode, field), 'USER leaked ' + field);
        return node;
      }
      await new Promise(resolve => setTimeout(resolve, 100));
    } while (Date.now() < deadline);
    throw new Error('Native Chat suggestions were not ready: ' + JSON.stringify(view));
  };
  let previousRequest, changedValue, ownedChat = false, failure;
  try {
    await check('typed suggestions DEBUG: native index, guards, stale rejection and owned restoration', async () => {
      assert(!(await tree()).screenClass, 'Arrange requires the caller-owned loaded world without a Screen');
      await key(84);
      ownedChat = true;
      await wait(view => /ChatScreen$/.test(view.screenClass), 'native Chat Screen');
      await ui({ role: 'text_field' }, 'type_text', { text: prefix });
      await wait(view => nodes(view).filter(node => node.role === 'suggestion').length >= 2, 'two real command suggestions');
      await debugUi(async snapshot => {
        let node = await ready(snapshot);
        const original = node.debugSelectionIndex, id = node.nodeId;
        const alternative = nodes(await snapshot()).find(row => row.role === 'suggestion' && row.entryIndex !== original);
        assert(alternative && Number.isInteger(alternative.entryIndex), 'Fixture needs another visible native suggestion');
        const value = alternative.entryIndex;
        const originalOffset = node.scrollAmount;
        assert(Number.isInteger(originalOffset) && originalOffset >= 0);
        changedValue = value;
        const accepted = suggestionSelectionRequest(node, value);
        const input = await api('GET', '/v0/input/state');
        let confirmed, mutationFailure;
        try {
          for (const invalid of [null, false, [], {}, '0', -1, node.debugSelectionRange.maximum + 1, 0.25]) {
            await assert.rejects(api('POST', '/v0/debug/mutations', { ...accepted, value: invalid }), error => error.code === 'INVALID_UI_DEBUG_VALUE');
            assert.deepEqual((await ready(snapshot)).resourceVersion, node.resourceVersion);
          }
          await assert.rejects(api('POST', '/v0/debug/mutations', { ...accepted, expectedValue: original + 0.25 }), error => error.code === 'VALUE_PRECONDITION_FAILED');
          const result = await applyUiMutation(accepted);
          auditAppliedUiMutation(result);
          assert.equal(result.sideEffects, 'native_suggestion_highlight_suffix_and_narration_only');
          assert.equal(result.after?.debugSelectionIndex, value);
          assert.equal(result.after.scrollAmount, originalOffset);
          assert(result.afterResourceVersion);
          confirmed = { value: result.after.debugSelectionIndex, resourceVersion: structuredClone(result.afterResourceVersion) };
          assert.notDeepEqual(result.afterResourceVersion, node.resourceVersion);
          const independent = await snapshot();
          assert.equal(draft(independent), prefix, 'Selection must not use or submit the suggestion');
          assert.equal(pick(independent).debugSelectionIndex, value);
          assert.deepEqual(pick(independent).resourceVersion, confirmed.resourceVersion);
          assert(nodes(independent).some(row => row.role === 'suggestion' && row.entryIndex === value && row.selected === true), 'Fresh semantic row must reflect actual native selection');
          await assert.rejects(api('POST', '/v0/debug/mutations', accepted), error => error.code === 'STALE_RESOURCE_REVISION');
        } catch (error) {
          mutationFailure = error;
          throw error;
        } finally {
          try {
            await restoreSuggestionSelection({ snapshot, nodes, applyUiMutation, nodeId: id, original, confirmed });
          } catch (error) {
            if (mutationFailure) mutationFailure.message += `; cleanup: ${error.message}`;
            else throw error;
          }
        }
        node = await ready(snapshot);
        assert.equal(node.debugSelectionIndex, original);
        assert.equal(node.scrollAmount, originalOffset);
        previousRequest = suggestionSelectionRequest(node, value);
        await noInput(input);
        const mode = await api('GET', '/v0/control/mode');
        assert.equal(mode.mode, 'OPERATE');
        assert.equal(mode.takeoverActive, false);
        samples.push({ case: 'native_suggestion_selection', original, value, restored: true,
          contentGuardAvailable: true, staleRejected: true, draftUnchanged: true,
          nativeSetter: 'SuggestionsList.select(int)', inputDelta: 0, playerLeaseRequired: false,
          suggestionUseTested: false, commandExecutionTested: false, nativeOffsetUnchanged: true });
      });
    });

    await check('typed suggestions DEBUG: actual MCP and web keep selection private and restore it', async () => {
      assert(Array.isArray(editorSamples));
      await editorHandoff('debug_suggestion_selection', undefined, {
        selector: { controlKey }, field: 'debugSelectionIndex', operation,
        changedValue, privateField: true,
      });
      const actual = editorSamples.filter(sample => sample.editor === 'debug_suggestion_selection');
      for (const transport of ['mcp_stdio_debug', 'web_user_isolation'])
        assert.equal(actual.filter(sample => sample.transport === transport && sample.status === 'PASS').length, 1, transport + ' must actually execute');
      assert.equal(draft(await tree()), prefix);
      samples.push({ case: 'native_suggestion_consumer_handoff', actualTransports: actual.map(sample => sample.transport),
        draftUnchanged: true, privateSelection: true,
        typedInputDelta: actual.find(sample => sample.transport === 'mcp_stdio_debug').inputDelta });
    });

    await check('typed suggestions DEBUG: closed native owner rejects its old selection token', async () => {
      assert(previousRequest);
      await closeOwnedChat(prefix);
      ownedChat = false;
      await debugUi(async snapshot => {
        assert(!pick(await snapshot()));
        const before = await api('GET', '/v0/input/state');
        await assert.rejects(api('POST', '/v0/debug/mutations', previousRequest), error => error.code === 'UI_CONTEXT_UNAVAILABLE');
        await noInput(before);
        samples.push({ case: 'native_suggestion_owner_replaced', rejected: 'UI_CONTEXT_UNAVAILABLE', inputDelta: 0 });
      });
    });
  } catch (error) {
    failure = error;
    throw error;
  } finally {
    if (ownedChat) {
      try {
        const view = await tree();
        if (view.screenClass) {
          const mode = await api('GET', '/v0/control/mode');
          assert.equal(mode.mode, 'TAKEOVER', 'Do not reacquire control during cleanup');
          assert.equal(mode.reconsentRequired, false, 'Human override is not cleanup permission');
          assert(/ChatScreen$/.test(view.screenClass) && draft(view) === prefix, 'Preserve an unrelated Screen or externally changed draft');
          await closeOwnedChat(prefix);
        }
      } catch (error) {
        if (failure) failure.message += `; cleanup: ${error.message}`;
        else throw error;
      }
    }
  }
}
