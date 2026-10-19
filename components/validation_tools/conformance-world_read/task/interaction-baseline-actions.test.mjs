import assert from 'node:assert/strict';
import test from 'node:test';
import {baselineStatistics, collectInventoryHoverSamples, finishBaselineWorldExit} from './interaction-baseline-actions.mjs';

function hoverFixture(options = {}) {
  let reads = 0; const calls = [], samples = [];
  const nodes = tree => (tree.children ?? []).flatMap(value => [value, ...nodes(value)]);
  const api = async (method, path) => {
    assert.equal(method, 'GET'); assert.equal(path, '/v0/ui/tree?category=USER'); reads++;
    const children = [9, 10, 11].map(slot => ({role: 'slot', slot, nodeId: `fresh-${reads}-slot-${slot}`,
      slotOwner: slot === 11 || options.noPlayerSlots ? 'menu' : 'player_inventory', x: slot * 18, y: 20, width: 16, height: 16,
      visible: true, active: true}));
    if (options.duplicateAt && reads === options.duplicateAt) children.push({...children[0], nodeId: 'ambiguous-other-owner'});
    if (options.targetDisappears && reads > 4) children.shift();
    const tree = {screenClass: 'net.minecraft.client.gui.screens.inventory.InventoryScreen', title: 'Inventory', screenIdentity: options.replacedScreen && reads > 3 ? 20 : 10,
      screenRevision: reads, menuIdentity: options.replacedMenu && reads > 3 ? 31 : 30, overlayIdentity: 0, children,
      treeNodeCount: children.length, interactionNodeCount: children.length, presentationNodeCount: 1, presentation: {children: [{role: 'hud_title'}]}};
    if (options.overlay && reads > 1) tree.overlayIdentity = 90;
    calls.push(tree); return tree;
  };
  const sample = async (group, index, steps) => {
    assert.equal(group, 'gui_semantic_hover'); assert.equal(steps.length, 1); assert.equal(steps[0].type, 'ui.action'); assert.equal(steps[0].action, 'hover');
    assert(!('x' in steps[0])); assert(!('y' in steps[0]));
    assert.deepEqual(Object.keys(steps[0].selector), ['nodeId']);
    const target = calls.at(-1).children.find(value => value.nodeId === steps[0].selector.nodeId); assert(target, 'Only the fresh tree may supply the selected node');
    const result = {group, index, steps, postRttMillis: index + 1, waitRttMillis: index + 11, observedTerminalMillis: index + 21,
      timing: {totalMillis: index + 30}, outcome: {state: options.failedSampleAt === index ? 'failed' : 'completed', result: {steps: [{stepType: 'ui.action', result: {
        action: 'hover', targetingSource: 'interaction_tree', resolvedNode: options.wrongResolvedNode ? {...target, nodeId: 'old-node'} : target
      }}]}}};
    samples.push(result); return result;
  };
  return {env: {api, nodes, sample}, calls, samples, reads: () => reads};
}

test('twenty actual collection calls reacquire fresh semantic nodes and alternate two native player slots without clicking', async () => {
  const fixture = hoverFixture(), result = await collectInventoryHoverSamples(fixture.env);
  assert.equal(fixture.reads(), 20); assert.equal(fixture.samples.length, 20); assert.equal(result.samples.length, 20);
  assert.deepEqual(result.anchors, [{slot: 9, slotOwner: 'player_inventory'}, {slot: 10, slotOwner: 'player_inventory'}]);
  assert.equal(new Set(result.samples.map(value => value.semanticTarget.nodeId)).size, 20);
  for (let index = 0; index < result.samples.length; index++) {
    assert.equal(result.samples[index].semanticTarget.slot, index % 2 ? 10 : 9);
    assert.equal(result.samples[index].semanticTarget.nodeId, `fresh-${index + 1}-slot-${index % 2 ? 10 : 9}`);
    assert.equal(result.samples[index].semanticTarget.freshTreePerSample, true);
  }
  const statistics = baselineStatistics(result.samples.map(value => value.observedTerminalMillis));
  assert.equal(statistics.sampleCount, 20); assert.equal(statistics.p50, 30); assert.equal(statistics.p95, 39);
});

test('unsafe or missing native player slots reject before issuing a hover', async () => {
  const fixture = hoverFixture({noPlayerSlots: true}); await assert.rejects(collectInventoryHoverSamples(fixture.env), /two distinct/);
  assert.equal(fixture.samples.length, 0);
});

test('replacement, overlay, vanished or ambiguous target cannot be hidden inside the GUI sample count', async () => {
  for (const options of [{replacedScreen: true}, {replacedMenu: true}, {overlay: true}, {targetDisappears: true}, {duplicateAt: 3}]) {
    const fixture = hoverFixture(options); await assert.rejects(collectInventoryHoverSamples(fixture.env)); assert(fixture.samples.length < 20);
  }
});

test('nonterminal/failed hover and an unrelated resolved node do not count as successful samples', async () => {
  for (const options of [{failedSampleAt: 7}, {wrongResolvedNode: true}]) {
    const fixture = hoverFixture(options); await assert.rejects(collectInventoryHoverSamples(fixture.env)); assert(fixture.samples.length < 20);
  }
});

const beforeExit = {inputGeneration: 10, inputContextGeneration: 7, inputContextId: 'old-native-world', inputContextAvailable: true,
  pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false};
const afterExit = {...beforeExit, inputGeneration: 11, inputContextGeneration: 8, inputContextId: 'new-native-title', inputContextAvailable: false};
const cancelled = {status: 409, runtimeError: {error: 'REQUEST_CANCELLED', executionOutcome: 'not_confirmed', retrySafe: false}};

function exitFixture(options = {}) {
  let milliseconds = 0, inputs = 0, clicks = 0, titleReads = 0; const calls = [];
  const failure = Object.assign(new Error('Only structured Runtime fields are authoritative'), options.error ?? cancelled);
  const env = {quit: {nodeId: 'semantic-save-quit'},
    api: async (method, path, body) => {
      calls.push({method, path, body});
      if (method === 'GET' && path === '/v0/input/state') {
        inputs++; if (inputs === 1) return {...beforeExit};
        if (options.pendingOnce && inputs === 2) return {...beforeExit, inputCleanupPending: true};
        return {...afterExit, ...options.after};
      }
      assert.equal(method, 'POST'); assert.equal(path, '/v0/wait/until'); assert.deepEqual(body.condition, {type: 'input.released'});
      return {passed: true, condition: body.condition, evidence: {pressedKeyCount: 0, pressedButtonCount: 0}};
    },
    clickNode: async value => {clicks++; assert.equal(value.nodeId, 'semantic-save-quit'); if (!options.success) throw failure; return {type: 'ui.action_result'};},
    wait: async predicate => {titleReads++; const title = {screenClass: 'net.minecraft.client.gui.screens.TitleScreen', overlayIdentity: 0, ...options.title};
      assert(predicate(title), 'No independent unobstructed title was reached'); return title;}, remaining: maximum => maximum};
  return {env, services: {now: () => milliseconds, sleep: async duration => {milliseconds += duration;}}, calls, failure,
    counts: () => ({inputs, clicks, titleReads})};
}

test('structured cancelled Save and Quit is not replayed; new epoch/title/released input independently prove cleanup', async () => {
  const fixture = exitFixture({pendingOnce: true}), result = await finishBaselineWorldExit(fixture.env, fixture.services);
  assert.equal(result.outcome, 'world_exit_independently_observed'); assert.equal(result.requestOutcome, 'not_confirmed'); assert.equal(result.requestReplayed, false);
  assert.equal(result.error.status, 409); assert.equal(result.error.body.error, 'REQUEST_CANCELLED'); assert.equal(result.after.inputCleanupPending, false);
  assert.equal(fixture.counts().clicks, 1); assert.equal(fixture.counts().titleReads, 1); assert.equal(fixture.counts().inputs, 3);
  assert.equal(fixture.calls.filter(value => value.method === 'POST').length, 1, 'Only the read-only released condition may follow the original click');
});

test('an actual successful response is kept separate from independently observed world exit', async () => {
  const fixture = exitFixture({success: true}), result = await finishBaselineWorldExit(fixture.env, fixture.services);
  assert.equal(result.requestOutcome, 'response_received'); assert.equal(result.requestReplayed, false); assert.equal(result.outcome, 'world_exit_independently_observed');
  assert.equal(result.response.type, 'ui.action_result'); assert.equal(fixture.counts().clicks, 1);
});

test('unstructured errors and every other 409 remain failures without probing them into apparent click success', async () => {
  for (const error of [{status: 500, runtimeError: cancelled.runtimeError}, {status: 409, runtimeError: {...cancelled.runtimeError, error: 'UI_TARGET_INVALIDATED'}},
    {status: 409, runtimeError: {...cancelled.runtimeError, retrySafe: true}}, {status: 409, runtimeError: {...cancelled.runtimeError, executionOutcome: 'completed'}},
    {message: JSON.stringify(cancelled.runtimeError)}]) {
    const fixture = exitFixture({error}); await assert.rejects(finishBaselineWorldExit(fixture.env, fixture.services), value => value === fixture.failure);
    assert.equal(fixture.counts().clicks, 1); assert.equal(fixture.counts().titleReads, 0); assert.equal(fixture.counts().inputs, 1);
  }
});

test('blocked title, held input, failed cleanup or reused epoch cannot confirm an interrupted exit', async () => {
  for (const options of [{title: {overlayIdentity: 1}}, {title: {screenClass: 'PauseScreen'}}, {after: {pressedKeyCount: 1}},
    {after: {pressedButtonCount: 1}}, {after: {inputCleanupFailed: true}}, {after: {inputCleanupPending: true}},
    {after: {inputContextId: beforeExit.inputContextId}}, {after: {inputGeneration: beforeExit.inputGeneration}},
    {after: {inputContextGeneration: beforeExit.inputContextGeneration}}, {after: {inputContextAvailable: true}}]) {
    const fixture = exitFixture(options); await assert.rejects(finishBaselineWorldExit(fixture.env, fixture.services)); assert.equal(fixture.counts().clicks, 1);
  }
});
