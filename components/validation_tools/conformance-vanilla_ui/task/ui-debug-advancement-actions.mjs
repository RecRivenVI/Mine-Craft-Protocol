import assert from 'node:assert/strict';
import { isDeepStrictEqual } from 'node:util';
import { auditAppliedUiMutation, auditUiDebugCapabilities } from './ui-debug-capability-audit.mjs';

const operation = 'ui.canvas.pan.set';
const controlKey = 'advancement.canvas';

export async function advancementPanActions({
  target, api, command, wait, tree, nodes, ui, key, check, debugUi,
  applyUiMutation, editorHandoff, samples,
}) {
  const pause = () => new Promise(resolve => setTimeout(resolve, 100));
  const canvas = view => nodes(view).find(node => node.controlKey === controlKey);
  const point = node => ({ ...node.debugPanValue });
  const unchanged = async before => {
    const after = await api('GET', '/v0/input/state');
    assert.equal(after.inputDispatchSequence, before.inputDispatchSequence);
    assert.equal(after.pressedKeyCount, 0);
    assert.equal(after.pressedButtonCount, 0);
  };
  const ready = async snapshot => {
    const deadline = Date.now() + 10000;
    let captured;
    do {
      captured = await snapshot();
      const node = canvas(captured);
      if (node?.debugPanReady && node.resourceVersion) {
        assert.equal(node.debugPanRange.unit, 'native_gui_pixels');
        assert(node.debugMutation.supportedOperations.includes(operation));
        auditUiDebugCapabilities(captured, await api('GET', '/v0/ui/tree?category=USER'), target);
        return node;
      }
      await pause();
    } while (Date.now() < deadline);
    throw new Error('Native advancement renderer did not publish a centered canvas: ' + JSON.stringify(captured));
  };
  const request = (node, value = point(node)) => ({
    operation, nodeId: node.nodeId, expectedResourceVersion: node.resourceVersion,
    expectedValue: point(node), value,
  });
  const comparePoint = (actual, expected) => {
    for (const axis of ['x', 'y'])
      assert(Math.abs(actual[axis] - expected[axis]) <= 1e-8, `${axis}: actual ${actual[axis]}, requested ${expected[axis]}`);
  };
  const differentPoint = node => {
    const result = point(node);
    for (const axis of ['x', 'y']) {
      const range = node.debugPanRange[axis];
      if (range.enabled) {
        const quarter = range.minimum + (range.maximum - range.minimum) / 4;
        result[axis] = quarter === result[axis] ? range.maximum : quarter;
      }
    }
    assert(!isDeepStrictEqual(result, point(node)), 'Ranged fixture must actually change at least one axis');
    return result;
  };
  const open = async () => {
    await key(76);
    return wait(view => /AdvancementsScreen$/.test(view.screenClass) && canvas(view), 'native advancement canvas');
  };
  const close = async () => {
    await key(256);
    await wait(view => !view.screenClass, 'native advancement Screen closes');
  };
  const selectWithRange = async (view, wanted) => {
    if (wanted(canvas(view))) return true;
    const tabs = nodes(view).filter(node => node.controlKey === 'advancement.tab');
    for (const tab of tabs) {
      await ui({ nodeId: tab.nodeId });
      view = await tree();
      if (wanted(canvas(view))) return true;
    }
    return false;
  };

  await check('typed advancement DEBUG: native fixed axes are not silently reset to zero', async () => {
    let found = false;
    const attemptedRoots = [];
    for (const family of ['story', 'end', 'nether', 'husbandry', 'adventure']) {
      await command('advancement revoke @s everything');
      await command(`advancement grant @s only minecraft:${family}/root`);
      const view = await open();
      attemptedRoots.push(family);
      found = await selectWithRange(view, node => node && !node.canPanX && !node.canPanY);
      if (found) break;
      await close();
    }
    assert(found, 'No native fixed-axis canvas in the bounded five-root fixture; do not mark this branch skipped');
    await debugUi(async snapshot => {
      const node = await ready(snapshot), original = point(node);
      for (const axis of ['x', 'y']) {
        assert.equal(node.debugPanRange[axis].enabled, false);
        assert.equal(node.debugPanRange[axis].minimum, original[axis]);
        assert.equal(node.debugPanRange[axis].maximum, original[axis]);
      }
      const input = await api('GET', '/v0/input/state');
      for (const axis of ['x', 'y']) {
        await assert.rejects(api('POST', '/v0/debug/mutations', request(node, { ...original, [axis]: original[axis] + 1 })), error => error.code === 'INVALID_UI_DEBUG_VALUE');
        assert.deepEqual((await ready(snapshot)).resourceVersion, node.resourceVersion);
      }
      const result = await applyUiMutation(request(node));
      auditAppliedUiMutation(result);
      assert.equal(result.sideEffects, 'native_advancement_display_pan_only');
      comparePoint(result.after.debugPanValue, original);
      await unchanged(input);
      samples.push({ case: 'native_advancement_pan_fixed_axes', attemptedRoots,
        centeredByNativeRenderer: true, original, bothFixed: true, unrequestedZeroing: false, inputDelta: 0 });
    });
    await close();
  });

  let previousRequest;
  await check('typed advancement DEBUG: native two-axis pan, stale rejection and guarded restoration', async () => {
    await command('advancement grant @s everything');
    const view = await open();
    assert(await selectWithRange(view, node => node && (node.canPanX || node.canPanY)), 'A ranged native advancement fixture is required');
    let consumerValue;
    await debugUi(async snapshot => {
      let node = await ready(snapshot);
      const original = point(node), id = node.nodeId;
      const value = differentPoint(node);
      consumerValue = value;
      const accepted = request(node, value);
      const input = await api('GET', '/v0/input/state');
      let confirmedChange, failure;
      try {
        for (const invalid of [null, false, [], 0.5, {}, { x: value.x },
          { ...value, z: 0 }, { ...value, x: '0' }, { ...value, y: null },
          { ...value, x: node.debugPanRange.x.maximum + 1 },
          { ...value, y: node.debugPanRange.y.minimum - 1 }]) {
          await assert.rejects(api('POST', '/v0/debug/mutations', { ...accepted, value: invalid }), error => error.code === 'INVALID_UI_DEBUG_VALUE');
          assert.deepEqual((await ready(snapshot)).resourceVersion, node.resourceVersion);
        }
        await assert.rejects(api('POST', '/v0/debug/mutations', { ...accepted, expectedValue: { ...original, x: original.x + 0.125 } }), error => error.code === 'VALUE_PRECONDITION_FAILED');
        const result = await applyUiMutation(accepted);
        confirmedChange = {
          value: structuredClone(result.after?.debugPanValue),
          resourceVersion: structuredClone(result.afterResourceVersion),
        };
        auditAppliedUiMutation(result);
        assert.equal(result.sideEffects, 'native_advancement_display_pan_only');
        comparePoint(result.after.debugPanValue, value);
        assert.notDeepEqual(result.afterResourceVersion, node.resourceVersion);
        await assert.rejects(api('POST', '/v0/debug/mutations', accepted), error => error.code === 'STALE_RESOURCE_REVISION');
      } catch (error) {
        failure = error;
        throw error;
      } finally {
        try {
          node = nodes(await snapshot()).find(value => value.nodeId === id);
          assert(node?.debugPanReady && node.resourceVersion, 'The original selected native owner is needed for recovery');
          if (!isDeepStrictEqual(point(node), original)) {
            assert(confirmedChange?.value && confirmedChange.resourceVersion, 'No confirmed owned canvas mutation; preserve instead of cleanup');
            assert.deepEqual(point(node), confirmedChange.value, 'Canvas value changed externally; preserve instead of cleanup');
            assert.deepEqual(node.resourceVersion, confirmedChange.resourceVersion, 'Canvas token changed externally; preserve instead of cleanup');
            const restored = await applyUiMutation(request(node, original));
            auditAppliedUiMutation(restored);
            comparePoint(restored.after.debugPanValue, original);
          }
        } catch (error) {
          if (failure) failure.message += `; cleanup: ${error.message}`;
          else throw error;
        }
      }
      node = await ready(snapshot);
      comparePoint(point(node), original);
      previousRequest = request(node, value);
      await unchanged(input);
      const mode = await api('GET', '/v0/control/mode');
      assert.equal(mode.mode, 'OPERATE');
      assert.equal(mode.takeoverActive, false);
      samples.push({ case: 'native_advancement_pan_ranged', original, value, restored: true,
        centeredByNativeRenderer: true, staleRejected: true, inputDelta: 0, playerLeaseRequired: false });
    });
    await editorHandoff('debug_advancement_canvas', undefined, {
      selector: { controlKey }, field: 'debugPanValue', operation,
      changedValue: consumerValue, privateField: true,
    });
    await close();
  });

  await check('typed advancement DEBUG: a replaced Screen cannot reuse the old canvas token', async () => {
    assert(previousRequest);
    await debugUi(async snapshot => {
      const input = await api('GET', '/v0/input/state');
      assert(!canvas(await snapshot()));
      await assert.rejects(api('POST', '/v0/debug/mutations', previousRequest), error => ['UI_CONTEXT_UNAVAILABLE', 'UI_NODE_NOT_FOUND'].includes(error.code));
      await unchanged(input);
      samples.push({ case: 'native_advancement_pan_screen_replaced', rejected: true, inputDelta: 0 });
    });
  });
}
