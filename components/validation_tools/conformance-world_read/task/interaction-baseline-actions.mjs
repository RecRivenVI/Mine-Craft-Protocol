import assert from 'node:assert/strict';
import {contextExitEvidence} from './context-exit-evidence.mjs';

export function baselineStatistics(values) {
  assert(values.length > 0, 'A baseline needs actual samples');
  for (const value of values) assert(Number.isFinite(value) && value >= 0, 'Invalid elapsed sample');
  const sorted = [...values].sort((left, right) => left - right);
  const rank = percentile => sorted[Math.max(0, Math.ceil(sorted.length * percentile) - 1)];
  return {sampleCount: sorted.length, method: 'nearest_rank', min: sorted[0], p50: rank(.5), p95: rank(.95), max: sorted.at(-1)};
}

export async function collectInventoryHoverSamples({api, nodes, sample}) {
  const samples = []; let firstTree, captured, slots, anchors;
  const eligible = value => value.role === 'slot' && value.slotOwner === 'player_inventory' && Number.isSafeInteger(value.slot) && value.slot >= 0
    && value.visible !== false && value.active !== false && Number.isFinite(value.x) && Number.isFinite(value.y)
    && Number.isFinite(value.width) && Number.isFinite(value.height) && value.width > 0 && value.height > 0;
  for (let index = 0; index < 20; index++) {
    const tree = await api('GET', '/v0/ui/tree?category=USER'), current = nodes(tree);
    assert(tree.screenClass.includes('InventoryScreen')); assert.equal(tree.overlayIdentity, 0);
    assert.equal(tree.treeNodeCount, current.length); assert.equal(tree.interactionNodeCount, current.length);
    assert.equal(tree.presentationNodeCount, nodes(tree.presentation ?? {}).length);
    if (!anchors) {
      firstTree = tree; captured = current; slots = current.filter(value => value.role === 'slot');
      for (const slot of slots) assert(['player_inventory', 'menu', 'unknown'].includes(slot.slotOwner), JSON.stringify(slot));
      const candidates = current.filter(eligible).sort((left, right) => left.slot - right.slot);
      anchors = [...new Map(candidates.map(value => [value.slot, {slot: value.slot, slotOwner: value.slotOwner}])).values()].slice(0, 2);
      assert.equal(anchors.length, 2, 'Inventory needs two distinct visible native player-inventory slots for hover');
    }
    assert.equal(tree.screenIdentity, firstTree.screenIdentity, 'Inventory was replaced during GUI timing');
    assert.equal(tree.menuIdentity, firstTree.menuIdentity, 'Inventory owner changed during GUI timing');
    const anchor = anchors[index % 2], matches = current.filter(value => eligible(value) && value.slot === anchor.slot);
    assert.equal(matches.length, 1, 'Fresh semantic slot must remain uniquely identifiable'); const target = matches[0];
    const result = await sample('gui_semantic_hover', index, [{type: 'ui.action', selector: {nodeId: target.nodeId}, action: 'hover'}]);
    assert.equal(result.outcome.state, 'completed'); const actual = result.outcome.result.steps[0].result;
    assert.equal(actual.action, 'hover'); assert.equal(actual.targetingSource, 'interaction_tree');
    assert.equal(actual.resolvedNode.nodeId, target.nodeId); assert.equal(actual.resolvedNode.slot, anchor.slot);
    result.semanticTarget = {nodeId: target.nodeId, slot: anchor.slot, slotOwner: anchor.slotOwner, screenIdentity: tree.screenIdentity,
      screenRevision: tree.screenRevision, menuIdentity: tree.menuIdentity, freshTreePerSample: true}; samples.push(result);
  }
  return {samples, firstTree, captured, slots, anchors};
}

export async function finishBaselineWorldExit({api, clickNode, wait, quit, remaining = maximum => maximum}, testServices) {
  const now = testServices?.now ?? (() => performance.now()), sleep = testServices?.sleep ?? (millis => new Promise(done => setTimeout(done, millis)));
  const before = await api('GET', '/v0/input/state'); let unconfirmed, response;
  try {response = await clickNode(quit);} catch (error) {
    const body = error.runtimeError;
    if (error.status !== 409 || !['REQUEST_CANCELLED', 'CONTROL_INPUT_GENERATION_CHANGED'].includes(body?.error)
        || body.executionOutcome !== 'not_confirmed' || body.retrySafe !== false) throw error;
    unconfirmed = {status: error.status, body};
  }
  const title = await wait(value => value.screenClass.endsWith('TitleScreen') && value.overlayIdentity === 0,
    'baseline title after Save and Quit', remaining(20000));
  const released = await api('POST', '/v0/wait/until', {condition: {type: 'input.released'}, timeoutMs: remaining(5000)});
  assert.equal(released.passed, true); assert.deepEqual(released.evidence, {pressedKeyCount: 0, pressedButtonCount: 0});
  const end = now() + remaining(5000); let after;
  do {
    after = await api('GET', '/v0/input/state'); assert.equal(after.inputCleanupFailed, false);
    if (after.inputCleanupPending === false && after.inputContextAvailable === false && after.inputContextGeneration > before.inputContextGeneration) break;
    await sleep(75);
  } while (now() < end);
  if (unconfirmed) return {...contextExitEvidence(before, after, title, unconfirmed), released};
  for (const field of ['inputGeneration', 'inputContextGeneration']) {
    assert(Number.isSafeInteger(before[field]) && before[field] >= 0); assert(Number.isSafeInteger(after[field]) && after[field] > before[field]);
  }
  assert.equal(before.inputContextAvailable, true); assert.equal(after.inputContextAvailable, false);
  assert.equal(typeof before.inputContextId, 'string'); assert(before.inputContextId.length > 0);
  assert.equal(typeof after.inputContextId, 'string'); assert(after.inputContextId.length > 0); assert.notEqual(after.inputContextId, before.inputContextId);
  assert.equal(after.pressedKeyCount, 0); assert.equal(after.pressedButtonCount, 0); assert.equal(after.inputCleanupPending, false);
  return {outcome: 'world_exit_independently_observed', requestOutcome: 'response_received', requestReplayed: false,
    before, after, screenClass: title.screenClass, overlayIdentity: title.overlayIdentity, released, response};
}

// Timings describe this isolated client and request path, never a cross-machine SLA or server acknowledgement.
export async function interactionBaselineActions({api, connection, target, mode, acquire, release, click, clickNode, wait, nodes, check, actionSamples}) {
  const deadline = performance.now() + 210000;
  const stageNames = ['preflight', 'inputState', 'pointerState', 'nativeCall', 'delay', 'cleanup'];
  let ownsLease = false, lastPipelinePost = -Infinity;
  const sleep = millis => new Promise(done => setTimeout(done, millis));
  function remaining(maximum = 5000) {
    const left = Math.floor(deadline - performance.now());
    assert(left > 0, 'Interaction baseline exceeded its 210-second budget');
    return Math.min(maximum, left);
  }
  async function take() { remaining(); await acquire(); ownsLease = true; }
  async function handBack() { if (ownsLease) { await release(); ownsLease = false; } }
  async function clean() {
    const value = await api('GET', '/v0/input/state');
    assert.equal(value.pressedKeyCount, 0, JSON.stringify(value));
    assert.equal(value.pressedButtonCount, 0, JSON.stringify(value));
    assert(Number.isSafeInteger(value.inputDispatchSequence));
    return value;
  }
  async function postPipeline(body) {
    remaining();
    let pause = 550 - (performance.now() - lastPipelinePost);
    while (pause > 0) {
      await sleep(Math.ceil(pause));
      pause = 550 - (performance.now() - lastPipelinePost);
    }
    remaining();
    lastPipelinePost = performance.now();
    const startedAt = performance.now();
    const started = await api('POST', '/v0/pipelines', body);
    return {started, startedAt, postRttMillis: performance.now() - startedAt};
  }
  async function tap(code) {
    const {started, postRttMillis} = await postPipeline({steps: [{type: 'key.tap', key: code, holdMs: 40}], timeoutMs: 8000, cleanupOnComplete: true});
    const outcome = await api('POST', `/v0/operations/${started.operationId}/wait`, {timeoutMs: remaining(5000)});
    assert.equal(outcome.state, 'completed', JSON.stringify(outcome));
    actionSamples.push({case: 'interaction_baseline_navigation', key: code, postRttMillis, outcome, excludedFromGameplayStatistics: true});
    return outcome;
  }
  function timingOf(outcome) {
    const timing = outcome.result?.timing;
    assert(timing, 'Actual terminal pipeline result must expose timing');
    assert.equal(timing.scope, 'automation_pipeline_not_http_or_server_effect');
    assert.equal(timing.timeSource, 'monotonic_elapsed');
    for (const field of ['queueMillis', 'totalMillis', 'requestedDelayMillis', 'inputCallbackCount'])
      assert(Number.isFinite(timing[field]) && timing[field] >= 0, 'Invalid timing.' + field);
    assert(Number.isSafeInteger(timing.inputCallbackCount));
    assert(timing.stages && typeof timing.stages === 'object');
    for (const [name, stage] of Object.entries(timing.stages)) {
      assert(stageNames.includes(name), 'Unknown measured stage: ' + name);
      assert(Number.isFinite(stage.elapsedMillis) && stage.elapsedMillis >= 0);
      assert(Number.isSafeInteger(stage.calls) && stage.calls >= 0);
    }
    if (timing.inputCallbackCount === 0) {
      assert(!('firstInputCallbackOffsetMillis' in timing));
      assert(!('lastInputCallbackOffsetMillis' in timing));
    } else {
      assert(Number.isFinite(timing.firstInputCallbackOffsetMillis) && timing.firstInputCallbackOffsetMillis >= 0);
      assert(Number.isFinite(timing.lastInputCallbackOffsetMillis) && timing.lastInputCallbackOffsetMillis >= timing.firstInputCallbackOffsetMillis);
    }
    for (const step of outcome.result.steps ?? []) assert(Number.isFinite(step.elapsedMillis) && step.elapsedMillis >= 0);
    return timing;
  }
  async function sample(group, index, steps) {
    const inputBefore = await clean();
    const playerBefore = group === 'gameplay_mouse_delta' ? await api('GET', '/v0/player?category=USER') : undefined;
    const {started, startedAt, postRttMillis} = await postPipeline({steps, timeoutMs: 8000, cleanupOnComplete: true});
    assert.equal(typeof started.operationId, 'string');
    const waitStarted = performance.now();
    const outcome = await api('POST', `/v0/operations/${started.operationId}/wait`, {timeoutMs: remaining(5000)});
    const waitRttMillis = performance.now() - waitStarted;
    const result = {case: 'interaction_baseline_sample', group, index, operationId: started.operationId,
      postRttMillis, waitRttMillis, observedTerminalMillis: performance.now() - startedAt,
      status: outcome.status, state: outcome.state, outcome};
    actionSamples.push(result);
    assert.equal(outcome.state, 'completed', 'Wait expiry/nonterminal or failed operation is not a baseline PASS: ' + JSON.stringify(outcome));
    result.timing = timingOf(outcome);
    assert.equal(outcome.result.steps.length, steps.length);
    result.inputBefore = inputBefore;
    result.inputAfter = await clean();
    result.inputDispatchSequenceDelta = result.inputAfter.inputDispatchSequence - inputBefore.inputDispatchSequence;
    assert(result.inputDispatchSequenceDelta >= 0);
    if (group !== 'gameplay_delay_100') {
      const minimum = group === 'gameplay_shift_tap' ? 3 : 2;
      assert(result.inputDispatchSequenceDelta >= minimum, 'Actual input callbacks plus cleanup must advance the Runtime sequence; cleanup alone is not proof');
      const dispatched = outcome.result.steps[0].result;
      if (group.startsWith('gameplay_')) {
        assert.equal(dispatched.inputOrigin, 'AGENT_ROUTED');
        assert.equal(dispatched.entryLayer, 'GAME_ROUTED_RAW');
        assert.equal(dispatched.directMutationUsed, false);
        result.nativeInputClientTick = dispatched.clientTick;
      }
    }
    if (playerBefore) {
      const playerAfter = await api('GET', '/v0/player?category=USER');
      result.playerBefore = {yaw: playerBefore.yaw, pitch: playerBefore.pitch};
      result.playerAfter = {yaw: playerAfter.yaw, pitch: playerAfter.pitch};
      assert(Number.isFinite(playerBefore.yaw) && Number.isFinite(playerAfter.yaw));
      const yawChange = ((playerAfter.yaw - playerBefore.yaw + 540) % 360) - 180;
      assert(Math.abs(yawChange) > 0, 'No client camera change observed for actual relative input');
    }
    return result;
  }
  function summarize(group, samples) {
    const fields = ['postRttMillis', 'waitRttMillis', 'observedTerminalMillis'];
    const summary = {case: 'interaction_baseline_summary', target, group, sampleCount: samples.length,
      minimumPostIntervalMillis: 550, spacingExcludedFromMeasurements: true,
      evidenceScope: 'isolated_client_not_server_effect_or_cross_machine_sla',
      inputCallbackMeaning: 'owner_permit_and_precondition_callback_entry_not_input_dispatch_or_server_effect',
      inputDispatchSequenceIncludesCleanup: true};
    for (const field of fields) summary[field] = baselineStatistics(samples.map(value => value[field]));
    summary.timing = {};
    for (const field of ['queueMillis', 'totalMillis', 'requestedDelayMillis', 'inputCallbackCount'])
      summary.timing[field] = baselineStatistics(samples.map(value => value.timing[field]));
    summary.stages = Object.fromEntries(stageNames.map(name => [name, {
      elapsedMillis: baselineStatistics(samples.map(value => value.timing.stages[name]?.elapsedMillis ?? 0)),
      calls: baselineStatistics(samples.map(value => value.timing.stages[name]?.calls ?? 0))
    }]));
    summary.slowest = samples.reduce((slowest, value) => value.observedTerminalMillis > slowest.observedTerminalMillis ? value : slowest);
    actionSamples.push(summary);
    return summary;
  }
  try {
    await check('baseline READ rejects player pipelines without side effects', async () => {
      await mode('READ'); const before = await clean();
      const refused = await api('POST', '/v0/pipelines', {steps: [{type: 'mouse.delta', dx: 2, dy: 0}], timeoutMs: 5000}, 'TAKEOVER_REQUIRED');
      const after = await clean(); assert.equal(after.inputDispatchSequence, before.inputDispatchSequence);
      actionSamples.push({case: 'interaction_baseline_read_denied', refused, before, after});
    });
    await take(); await click('menu.singleplayer', 'Singleplayer');
    let tree = await wait(value => value.screenClass.endsWith('SelectWorldScreen'), 'baseline world list', remaining(20000));
    const row = nodes(tree).find(value => value.worldName === 'World-Read-Alpha'); assert(row);
    await clickNode(row); await click('selectWorld.select', 'Play Selected World');
    await wait(value => !value.screenClass, 'baseline restored world entered', remaining(60000));
    await handBack();
    await check('baseline USER HUD, entity and scene expose an actual player-dropped item without internal fields', async () => {
      const itemId = 'minecraft:echo_shard';
      const hudItems = value => nodes(value.presentation ?? {}).filter(node => node.role === 'hud_item');
      const selectedItem = value => hudItems(value).find(node => node.selected);
      const before = await api('GET', '/v0/ui/tree?category=USER');
      const originalSelected = selectedItem(before); assert(originalSelected, 'A visible current hotbar selection is required');
      const empty = originalSelected.count === 0 && originalSelected.item === 'minecraft:air' ? originalSelected
        : hudItems(before).find(node => /^hud\.hotbar\.[0-8]$/.test(node.controlKey ?? '') && node.count === 0 && node.item === 'minecraft:air');
      assert(empty, 'Safe fixture blocked: no known empty hotbar slot; unknown inventory is never replaced');
      const existing = await api('GET', '/v0/world/entities?category=USER&radius=16&limit=128');
      assert.equal(existing.dataCategory, 'USER'); assert.equal(existing.authority, 'CLIENT_KNOWN');
      assert(!existing.entities.some(entity => entity.itemId === itemId), 'Safe fixture blocked: existing echo-shard drops must not be removed or merged');
      if (!empty.selected) {
        const slot = Number(empty.controlKey.slice('hud.hotbar.'.length));
        await take(); await tap(49 + slot);
        await wait(value => selectedItem(value)?.controlKey === empty.controlKey, 'baseline empty hotbar slot selected', remaining(5000));
        await handBack();
      }
      await mode('OPERATE');
      const arrangeInput = await clean();
      const arrange = await api('POST', '/v0/command/player', {command: 'item replace entity @s weapon.mainhand with minecraft:echo_shard 3', route: 'NATIVE'});
      assert.equal(arrange.route, 'NATIVE'); assert.equal(arrange.dataCategory, 'USER'); assert.equal(arrange.inputDispatched, false);
      assert.equal((await clean()).inputDispatchSequence, arrangeInput.inputDispatchSequence);
      const arrangeSample = {case: 'interaction_baseline_item_arrange', path: 'NATIVE_current_player_command', arrange,
        diagnosticArrange: true, gameplayAcceptance: false, originalSelected, prepared: null,
        copyOnlySideEffects: true, unknownInventoryOrDropsCleared: false};
      actionSamples.push(arrangeSample);
      const prepared = await wait(value => selectedItem(value)?.item === itemId && selectedItem(value)?.count === 3, 'baseline actual USER hotbar item received', remaining(5000));
      arrangeSample.prepared = selectedItem(prepared);
      try {
        await take(); const act = await tap(81);
        const afterDrop = await wait(value => selectedItem(value)?.item === itemId && selectedItem(value)?.count === 2, 'baseline ordinary Q drops exactly one item', remaining(5000));
        function noInternalItemFields(value) {
          for (const [field, child] of Object.entries(value)) {
            assert(!/nbt|component|uuid|health/i.test(field), 'USER item leaked internal field: ' + field);
            if (child && typeof child === 'object') noInternalItemFields(child);
          }
        }
        const itemDeadline = performance.now() + remaining(5000);
        let entities, dropped;
        do {
          entities = await api('GET', '/v0/world/entities?category=USER&radius=16&limit=128');
          dropped = entities.entities.find(entity => entity.type === 'minecraft:item' && entity.itemId === itemId && entity.itemCount === 1);
          if (dropped) break;
          await sleep(150);
        } while (performance.now() < itemDeadline);
        assert(dropped, 'Actual USER ItemEntity itemId/count was not observed within the bounded window');
        assert.equal(entities.dataCategory, 'USER'); assert.equal(entities.authority, 'CLIENT_KNOWN');
        noInternalItemFields(dropped);
        const scene = await api('GET', '/v0/world/scene?category=USER&radius=8&blockLimit=1&entityLimit=128');
        assert.equal(scene.dataCategory, 'USER'); assert.equal(scene.authority, 'CLIENT_KNOWN');
        const sceneItem = scene.entities.find(entity => entity.type === 'minecraft:item' && entity.itemId === itemId && entity.itemCount === 1);
        assert(sceneItem, 'Actual USER scene must retain the same dropped-item summary');
        noInternalItemFields(sceneItem);
        actionSamples.push({case: 'interaction_baseline_item_user_read', actPath: 'TAKEOVER_GAME_ROUTED_Q', act,
          hud: selectedItem(afterDrop), entities, scene, dropped, sceneItem,
          userPositiveSource: 'actual_client_hud_and_cached_entities_not_server_probe', copyOnlySideEffects: true,
          remainingPreparedItems: 2, droppedPreparedItems: 1, originalUnknownItemsCleared: false});
      } finally { await handBack(); await mode('READ'); }
    });
    await check('baseline rejects invalid Deep chunk radius for both native perspectives', async () => {
      const before = await clean();
      for (const perspective of ['client_known', 'server_authoritative']) {
        const response = await connection.fetch('/v0/observe/deep', {method: 'POST', headers: {'content-type': 'application/json'},
          body: JSON.stringify({category: 'DEBUG', perspective, domains: ['chunks'], selector: {chunkRadius: 3}}), signal: AbortSignal.timeout(remaining(10000))});
        const denied = await response.json();
        assert.equal(response.status, 400, JSON.stringify(denied)); assert.equal(denied.error, 'INVALID_DEEP_SELECTOR');
        actionSamples.push({case: 'interaction_baseline_invalid_selector', perspective, httpStatus: response.status, denied});
      }
      assert.equal((await clean()).inputDispatchSequence, before.inputDispatchSequence);
    });
    await take();
    for (const group of ['gameplay_mouse_delta', 'gameplay_delay_100', 'gameplay_shift_tap']) {
      await check('baseline ' + group + ' collects 20 separate real pipeline samples', async () => {
        const state = await api('GET', '/v0/control/mode'); assert.equal(state.mode, 'TAKEOVER'); assert.equal(state.takeoverActive, true);
        assert.equal((await api('GET', '/v0/ui/tree')).screenClass, '');
        const samples = [];
        for (let index = 0; index < 20; index++) {
          const steps = group === 'gameplay_mouse_delta' ? [{type: 'mouse.delta', dx: index % 2 ? -4 : 4, dy: 0}]
            : group === 'gameplay_delay_100' ? [{type: 'delay', durationMs: 100}]
              : [{type: 'key.tap', key: 340, holdMs: 40}];
          samples.push(await sample(group, index, steps));
        }
        summarize(group, samples);
      });
    }
    await check('baseline GUI semantic hover collects 20 fresh alternating slot samples with intentional pacing intact', async () => {
      await tap(69);
      tree = await wait(value => value.screenClass.includes('InventoryScreen'), 'baseline Inventory', remaining(10000));
      const {samples, firstTree, captured, slots, anchors} = await collectInventoryHoverSamples({api, nodes, sample});
      const statistics = summarize('gui_semantic_hover', samples);
      actionSamples.push({case: 'interaction_baseline_gui_pacing', sampleCount: samples.length, guiSamples: samples.length,
        guiP95Available: samples.length === 20, intentionalPacingUnchanged: true, alternatingSlots: anchors, hoverSamples: samples, statistics,
        screenTitle: firstTree.title ?? null, treeNodeCount: firstTree.treeNodeCount, capturedNodeCount: captured.length,
        interactionNodeCount: firstTree.interactionNodeCount, presentationNodeCount: firstTree.presentationNodeCount,
        slotOwners: Object.fromEntries(['player_inventory', 'menu', 'unknown'].map(owner => [owner, slots.filter(value => value.slotOwner === owner).length]))});
      await tap(256); await wait(value => !value.screenClass, 'baseline gameplay after Inventory', remaining(10000));
    });
    await check('baseline cancellation clears held Shift and prevents late queued input', async () => {
      const {started, postRttMillis} = await postPipeline({steps: [{type: 'key', key: 340, action: 1}, {type: 'delay', durationMs: 10000}, {type: 'mouse.delta', dx: 8, dy: 0}], timeoutMs: 15000, cleanupOnComplete: true});
      const heldDeadline = performance.now() + remaining(3000);
      let held;
      do { held = await api('GET', '/v0/input/state'); if (held.pressedKeyCount > 0) break; await sleep(75); } while (performance.now() < heldDeadline);
      assert.equal(held.pressedKeyCount, 1, 'Cancellation fixture must actually hold its safe key');
      await api('DELETE', `/v0/operations/${started.operationId}`);
      const outcome = await api('POST', `/v0/operations/${started.operationId}/wait`, {timeoutMs: remaining(5000)});
      assert.equal(outcome.state, 'cancelled', JSON.stringify(outcome));
      const cleanupDeadline = performance.now() + remaining(3000);
      let stopped;
      do { stopped = await api('GET', '/v0/input/state'); if (!stopped.pressedKeyCount && !stopped.pressedButtonCount) break; await sleep(75); } while (performance.now() < cleanupDeadline);
      assert.equal(stopped.pressedKeyCount, 0); assert.equal(stopped.pressedButtonCount, 0);
      // This is an explicit negative observation window after cleanup, not a success-condition sleep.
      await sleep(300); const later = await clean();
      assert.equal(later.inputDispatchSequence, stopped.inputDispatchSequence, 'Cancelled queued mouse.delta must not arrive late');
      actionSamples.push({case: 'interaction_baseline_cancel', operationId: started.operationId, postRttMillis, held, outcome, stopped, later, noLateInput: true, negativeObservationMillis: 300, physicalEscTested: false});
    });
    await tap(256); tree = await wait(value => value.screenClass.endsWith('PauseScreen'), 'baseline Save and Quit', remaining(10000));
    const quit = nodes(tree).find(value => ['menu.returnToMenu', 'menu.disconnect'].includes(value.translationKey)); assert(quit);
    const exit = await finishBaselineWorldExit({api, clickNode, wait, quit, remaining});
    actionSamples.push({case: 'interaction_baseline_context_exit', ...exit});
    await handBack(); await mode('READ'); await clean();
    actionSamples.push({case: 'interaction_baseline_complete', elapsedMillis: 210000 - (deadline - performance.now()), maximumMillis: 210000,
      gameplaySamplesPerGroup: 20, guiSamples: 20, guiP95Available: true, inputCallbackCounterIsDispatchProof: false, persistentWriteCalls: 0});
  } finally {
    await handBack();
  }
}
