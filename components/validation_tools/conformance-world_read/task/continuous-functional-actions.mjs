import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {performanceAttackConsumption, performanceAttackSteps} from './continuous-performance-actions.mjs';

const RELEASED = {type: 'input.released'};
const TERMINAL = new Set(['completed', 'failed', 'cancelled']);
const PERFORMANCE = Object.freeze({status: 'DEFERRED_BY_USER_2026_10_20', newMeasurementsPerformed: false});

export function validateFunctionalAttack(sample, pairs) {
  assert.deepEqual(sample.steps, performanceAttackSteps(pairs));
  assert.equal(sample.outcome.state, 'completed');
  assert.equal(sample.outcome.result.cleanupOnComplete, true);
  assert.equal(sample.outcome.result.steps.length, sample.steps.length);
  assert.equal(sample.outcome.result.timing?.stages?.cleanup?.calls, 1);
  for (const [index, step] of sample.outcome.result.steps.entries()) {
    assert.equal(step.stepType, sample.steps[index].type);
    if (step.stepType === 'mouse.button') {
      assert.equal(step.result.inputOrigin, 'AGENT_ROUTED');
      assert.equal(step.result.entryLayer, 'GAME_ROUTED_RAW');
      assert.equal(step.result.hostCursorUsed, false);
      assert.equal(step.result.directMutationUsed, false);
    }
  }
  assert.equal(sample.inputAfter.pressedKeyCount, 0);
  assert.equal(sample.inputAfter.pressedButtonCount, 0);
  assert.equal(sample.inputAfter.inputDispatchSequence - sample.inputBefore.inputDispatchSequence, pairs * 2 + 1);
  const consumption = performanceAttackConsumption(sample, pairs);
  assert.equal(consumption.actualAttackCountConfirmed, true, JSON.stringify(consumption));
  return {...consumption, requestedPairs: pairs, holdMillisPerPair: 40, cleanupCalls: 1, scope: 'functional_only'};
}

// The clock bounds admission, cleanup and passive observation only; no durations are measured or compared.
export async function continuousFunctionalActions({api, target, fixtureInput, mode, acquire, release, click, clickNode,
  wait, nodes, check, actionSamples, forgetExpiredLease}, services) {
  assert.equal(target, '26.3-fabric');
  assert.equal(fixtureInput?.policy, 'immutable_baseline_restored');
  assert.equal(fixtureInput.target, target); assert.equal(fixtureInput.role, 'client');
  assert.equal(fixtureInput.credentialsCopied, false);
  assert.match(fixtureInput.baselineSha256, /^[0-9a-f]{64}$/);
  assert.equal(fixtureInput.restoredSha256, fixtureInput.baselineSha256);
  const now = services?.now ?? (() => performance.now());
  const sleep = services?.sleep ?? (millis => new Promise(done => setTimeout(done, millis)));
  const deadline = now() + 180000;
  const tag = 'mcp_cf_' + randomUUID().replaceAll('-', '').slice(0, 24);
  let ownsLease = false, leaseId, touchedFixture = false, arena, entityPosition, primaryFailure, cleanupFailure, finalStop;
  let pipelinePosted = false;
  function remaining(maximum = 5000) {
    const left = Math.floor(deadline - now()); assert(left > 0, 'Functional scenario exceeded 180 seconds');
    return Math.min(maximum, left);
  }
  async function until(read, predicate, label, maximum = 5000) {
    const end = now() + remaining(maximum); let value;
    do {value = await read(); if (predicate(value)) return value; await sleep(50);} while (now() < end);
    assert.fail(label + ': ' + JSON.stringify(value));
  }
  async function input(zero = false) {
    const value = await api('GET', '/v0/input/state');
    for (const key of ['pressedKeyCount', 'pressedButtonCount', 'inputDispatchSequence'])
      assert(Number.isSafeInteger(value[key]) && value[key] >= 0);
    if (zero) {assert.equal(value.pressedKeyCount, 0); assert.equal(value.pressedButtonCount, 0); assert.equal(value.inputCleanupPending, false);}
    assert.equal(value.inputCleanupFailed, false);
    return value;
  }
  async function released() {
    const value = await api('POST', '/v0/wait/until', {condition: RELEASED, timeoutMs: remaining()});
    assert.equal(value.passed, true); assert.deepEqual(value.evidence, {pressedKeyCount: 0, pressedButtonCount: 0}); return value;
  }
  async function take() {
    await acquire(); ownsLease = true;
    const value = await api('GET', '/v0/control/status');
    assert.equal(value.mode, 'TAKEOVER'); assert.equal(value.status, 'active');
    assert.equal(typeof value.leaseId, 'string'); assert(value.leaseId.length > 0); leaseId = value.leaseId;
  }
  async function handBack() {
    if (!ownsLease) return;
    const value = await api('GET', '/v0/control/status');
    if (value.leaseId === leaseId) await release();
    else {
      assert.equal(value.mode, 'READ'); assert.equal(value.status, 'available'); assert(!value.leaseId);
      await released(); forgetExpiredLease();
    }
    ownsLease = false; leaseId = undefined;
  }
  async function nativeCommand(command) {
    const value = await api('POST', '/v0/command/player', {command, route: 'NATIVE'});
    assert.equal(value.route, 'NATIVE'); assert.equal(value.inputDispatched, false); assert.equal(value.effectConfirmed, false);
    return value;
  }
  async function post(steps) {
    if (pipelinePosted) await sleep(550);
    remaining(); pipelinePosted = true;
    const value = await api('POST', '/v0/pipelines', {steps, timeoutMs: 15000, cleanupOnComplete: true});
    assert.equal(typeof value.operationId, 'string'); return value;
  }
  async function terminal(id, expected = 'completed') {
    const value = await until(() => api('POST', `/v0/operations/${id}/wait`, {timeoutMs: remaining(1000)}),
      value => TERMINAL.has(value.state), 'Operation did not terminate', 20000);
    assert.equal(value.state, expected, JSON.stringify(value)); return value;
  }
  async function pipeline(steps) {
    const inputBefore = await input(true), accepted = await post(steps), outcome = await terminal(accepted.operationId);
    const inputAfter = await input(true);
    assert.equal(outcome.result.cleanupOnComplete, true); assert.equal(outcome.result.steps.length, steps.length);
    return {operationId: accepted.operationId, steps, inputBefore, inputAfter, outcome};
  }
  async function tap(key) {return pipeline([{type: 'key.tap', key, holdMs: 40}]);}
  async function stop() {
    const before = await input(), response = await api('POST', '/v0/input/release-all', {});
    assert.equal(response.type, 'input.release_all');
    const waited = await released(), after = await input(true);
    assert.equal(after.inputGeneration, response.inputGeneration);
    return {before, response, waited, after};
  }
  async function entities() {
    const value = await api('GET', '/v0/world/entities?category=USER&radius=4&limit=32');
    assert.equal(value.dataCategory, 'USER'); assert.equal(value.authority, 'CLIENT_KNOWN');
    assert.equal(value.chunkLoadRequested, false); assert.equal(value.entitiesTruncated, false); return value.entities;
  }
  const matchesCow = value => entityPosition && value.type === 'minecraft:cow'
    && ['x', 'y', 'z'].every(key => Math.abs(value[key] - entityPosition[key]) < .05);
  async function observeAttack(sample, pairs) {
    const terminalInput = sample.terminalInputSnapshot = structuredClone(sample.inputAfter);
    assert(Number.isSafeInteger(terminalInput.clientTick));
    const end = now() + remaining(500), observations = [];
    while (true) {
      const lease = await api('GET', '/v0/control/status');
      assert.equal(lease.mode, 'TAKEOVER'); assert.equal(lease.status, 'active'); assert.equal(lease.leaseId, leaseId);
      const current = sample.inputAfter;
      const ticks = current.clientTick - terminalInput.clientTick;
      assert(Number.isSafeInteger(ticks) && ticks >= 0 && ticks <= 2, 'Native consumption observation exceeded two ticks');
      assert(now() < end, 'Native consumption was not confirmed within the passive window');
      const consumption = performanceAttackConsumption(sample, pairs); observations.push({input: structuredClone(current), lease, consumption});
      if (consumption.actualAttackCountConfirmed) break;
      assert.equal(consumption.consumptionReason, 'native_attack_count_not_confirmed_possible_late_consumption');
      assert(consumption.nativeEntityAttackDelta < pairs); assert(ticks < 2);
      await sleep(20); sample.inputAfter = await input(true);
    }
    sample.passiveConsumptionObservation = {scope: 'functional_native_consumption_only', maximumMillis: 500,
      maximumNewNativeTicks: 2, observations, inputResubmitted: false, newAttacksRequested: 0};
  }
  try {
    await check('functional READ rejects player input without dispatch', async () => {
      await mode('READ'); const before = await input(true);
      const refused = await api('POST', '/v0/pipelines', {steps: [{type: 'mouse.delta', dx: 2, dy: 0}], timeoutMs: 5000}, 'TAKEOVER_REQUIRED');
      assert.equal(refused.error, 'TAKEOVER_REQUIRED'); const after = await input(true);
      assert.equal(after.inputDispatchSequence, before.inputDispatchSequence);
      actionSamples.push({case: 'continuous_functional_read_denied', before, refused, after});
    });
    await check('functional enters only the restored diagnostic world and aims at its own client-known cow', async () => {
      const title = await api('GET', '/v0/ui/tree'); assert(title.screenClass.endsWith('TitleScreen')); assert(!title.overlayIdentity);
      await take(); await click('menu.singleplayer', 'Singleplayer');
      const list = await wait(value => value.screenClass.endsWith('SelectWorldScreen'), 'functional world list', remaining(20000));
      const row = nodes(list).find(value => value.worldName === 'World-Read-Alpha'); assert(row);
      await clickNode(row); await click('selectWorld.select', 'Play Selected World');
      await wait(value => !value.screenClass && !value.overlayIdentity, 'functional restored world', remaining(60000));
      await handBack(); await mode('OPERATE');
      const original = await api('GET', '/v0/player?category=USER'); assert.equal(original.dimension, 'minecraft:overworld');
      for (const axis of ['x', 'y', 'z']) assert(Number.isFinite(original[axis]));
      const x = Math.floor(original.x), z = Math.floor(original.z), floor = Math.min(200, Math.max(64, Math.floor(original.y) + 8));
      arena = {x: x + .5, y: floor + 1, z: z + .5, floor};
      entityPosition = {x: arena.x, y: arena.y, z: arena.z + 2.5};
      touchedFixture = true; const receipts = [];
      for (const command of [
        `fill ${x - 3} ${floor} ${z - 3} ${x + 3} ${floor} ${z + 4} minecraft:stone`,
        `fill ${x - 3} ${floor + 1} ${z - 3} ${x + 3} ${floor + 4} ${z + 4} minecraft:air`,
        'clear @s', 'gamemode survival @s', `tp @s ${arena.x} ${arena.y} ${arena.z} 0 0`,
        `summon minecraft:cow ${entityPosition.x.toFixed(3)} ${entityPosition.y.toFixed(3)} ${entityPosition.z.toFixed(3)} {NoAI:1b,NoGravity:1b,Invulnerable:1b,Silent:1b,Tags:["${tag}"]}`
      ]) receipts.push(await nativeCommand(command));
      const actor = await until(() => api('GET', '/v0/player?category=USER'), value => value.dimension === original.dimension
        && ['x', 'y', 'z'].every(key => Math.abs(value[key] - arena[key]) < .05)
        && value.onGround === true && value.flying === false && value.gameMode === 'survival', 'Diagnostic actor was not received');
      const emptyHand = await wait(value => !value.screenClass && nodes(value.presentation ?? {}).some(node => node.selected && node.count === 0),
        'functional empty hand', remaining());
      const known = await until(entities, value => value.filter(matchesCow).length === 1, 'Unique fixture cow was not received');
      const entity = known.find(matchesCow); assert.equal(typeof entity.targetRef, 'string');
      await take(); const aim = await pipeline([{type: 'world.action', category: 'USER', targetRef: entity.targetRef, action: 'aim'}]);
      const result = aim.outcome.result.steps[0].result;
      assert.equal(result.onTarget, true); assert.equal(result.actualHit.kind, 'entity');
      assert.equal(result.actualHit.entityType, 'minecraft:cow'); assert(result.actualHit.distance > 0 && result.actualHit.distance <= 3);
      actionSamples.push({case: 'continuous_functional_fixture', fixtureInput, original, arena, tag, entity, actor, emptyHand, receipts, aim,
        arrangeAuthority: 'diagnostic_current_player_NATIVE_commands', actAuthority: 'PLAYTEST', copyOnlySideEffects: true,
        nativeCooldownChanged: false, damageConfirmed: false, serverEffectConfirmed: false});
    });
    for (const pairs of [1, 3]) await check('functional native entity attack consumes exactly ' + pairs + ' routed pair(s)', async () => {
      const sample = await pipeline(performanceAttackSteps(pairs));
      actionSamples.push({case: 'continuous_functional_attack', pairs, sample});
      await observeAttack(sample, pairs); sample.validation = validateFunctionalAttack(sample, pairs);
    });
    for (const [name, steps] of [
      ['mouse_delta', [{type: 'mouse.delta', dx: 4, dy: 0}]],
      ['delay_100', [{type: 'delay', durationMs: 100}]],
      ['shift_40', [{type: 'key.tap', key: 340, holdMs: 40}]]
    ]) await check('functional single ' + name, async () => {
      const before = name === 'mouse_delta' ? await api('GET', '/v0/player?category=USER') : undefined;
      const sample = await pipeline(steps);
      if (name !== 'delay_100') {
        const actual = sample.outcome.result.steps[0].result;
        assert.equal(actual.inputOrigin, 'AGENT_ROUTED'); assert.equal(actual.entryLayer, 'GAME_ROUTED_RAW'); assert.equal(actual.directMutationUsed, false);
        assert(sample.inputAfter.inputDispatchSequence - sample.inputBefore.inputDispatchSequence >= (name === 'shift_40' ? 3 : 2));
      }
      const after = before ? await until(() => api('GET', '/v0/player?category=USER'),
        value => Math.abs(((value.yaw - before.yaw + 540) % 360) - 180) > 0,
        'Native relative mouse input must reach the camera') : undefined;
      actionSamples.push({case: 'continuous_functional_single_input', name, sample, playerBefore: before, playerAfter: after});
    });
    await check('functional hovers two different fresh semantic Inventory slots', async () => {
      await tap(69); const first = await wait(value => value.screenClass.includes('InventoryScreen'), 'functional Inventory', remaining());
      const eligible = value => value.role === 'slot' && value.slotOwner === 'player_inventory' && Number.isSafeInteger(value.slot)
        && value.visible !== false && value.active !== false;
      const anchors = [...new Set(nodes(first).filter(eligible).map(value => value.slot))].slice(0, 2); assert.equal(anchors.length, 2);
      const samples = [];
      for (const slot of anchors) {
        const tree = await api('GET', '/v0/ui/tree?category=USER'); assert.equal(tree.screenIdentity, first.screenIdentity); assert.equal(tree.menuIdentity, first.menuIdentity);
        const matches = nodes(tree).filter(value => eligible(value) && value.slot === slot); assert.equal(matches.length, 1);
        const node = matches[0], sample = await pipeline([{type: 'ui.action', selector: {nodeId: node.nodeId}, action: 'hover'}]);
        const result = sample.outcome.result.steps[0].result;
        assert.equal(result.action, 'hover'); assert.equal(result.targetingSource, 'interaction_tree');
        assert.equal(result.resolvedNode.nodeId, node.nodeId); assert.equal(result.resolvedNode.slot, slot);
        samples.push({slot, nodeId: node.nodeId, tree, sample});
      }
      actionSamples.push({case: 'continuous_functional_gui_hover', anchors, samples, intentionalGuiPacingUnchanged: true});
      await tap(256); await wait(value => !value.screenClass && !value.overlayIdentity, 'functional gameplay after Inventory', remaining());
    });
    await check('functional cancellation releases held Shift and prevents its queued delta', async () => {
      const accepted = await post([{type: 'key', key: 340, action: 1, scanCode: 0, modifiers: 0},
        {type: 'delay', durationMs: 10000}, {type: 'mouse.delta', dx: 8, dy: 0}]);
      const held = await until(() => input(), value => value.pressedKeyCount === 1, 'Shift was not actually held');
      assert(held.pressedKeys?.includes(340));
      const cancel = await api('DELETE', `/v0/operations/${accepted.operationId}`), outcome = await terminal(accepted.operationId, 'cancelled');
      const waited = await released(), stopped = await until(() => input(), value => value.pressedKeyCount === 0
        && value.pressedButtonCount === 0 && value.inputCleanupPending === false, 'Cancelled input cleanup did not complete');
      await sleep(300); const later = await input(true);
      assert.equal(later.inputDispatchSequence, stopped.inputDispatchSequence, 'Cancelled queued input arrived late');
      actionSamples.push({case: 'continuous_functional_cancel', accepted, held, cancel, outcome, waited, stopped, later,
        negativeObservationMillis: 300, noLateInput: true, physicalEscTested: false});
      finalStop = await stop(); actionSamples.push({case: 'continuous_functional_stop', ...finalStop});
    });
  } catch (error) {primaryFailure = error;}
  finally {
    if (ownsLease) {
      try {const current = await api('GET', '/v0/control/status'); if (current.leaseId === leaseId && !finalStop) finalStop = await stop();}
      catch (error) {cleanupFailure = error; actionSamples.push({case: 'continuous_functional_cleanup_failure', error: String(error)});}
      try {await handBack();} catch (error) {cleanupFailure ??= error;}
    }
    if (touchedFixture && !ownsLease) {
      try {
        await mode('OPERATE');
        const at = entityPosition;
        const receipt = await nativeCommand(`kill @e[type=minecraft:cow,tag=${tag},x=${at.x},y=${at.y},z=${at.z},distance=..0.1,limit=1]`);
        const after = await until(entities, values => !values.some(matchesCow), 'Own fixture cleanup was not received');
        await mode('READ'); actionSamples.push({case: 'continuous_functional_fixture_cleanup', tag, entityPosition, receipt, after});
      } catch (error) {cleanupFailure ??= error; actionSamples.push({case: 'continuous_functional_fixture_cleanup_failure', error: String(error)});}
    }
  }
  if (primaryFailure) throw primaryFailure;
  if (cleanupFailure) throw cleanupFailure;
  const finalInput = await input(true), finalMode = await api('GET', '/v0/control/status');
  assert.equal(finalMode.mode, 'READ'); assert.equal(finalMode.status, 'available'); assert(!finalMode.leaseId); assert(finalStop);
  const result = {case: 'continuous_functional_complete', target, scope: 'functional_only', performance: {...PERFORMANCE},
    evidenceKind: services ? 'synthetic_test_not_runtime' : 'runtime_functional', liveEvidence: !services,
    checks: 9, attackPairs: [1, 3], actualNativeAttackConsumptionConfirmed: true, guiHoverCount: 2,
    finalStop, finalInput, finalMode, physicalEscTested: false, damageConfirmed: false, serverEffectConfirmed: false,
    clientShutdownOwnedByOuterRunner: true, persistentWriteCalls: 0};
  actionSamples.push(result); return result;
}
