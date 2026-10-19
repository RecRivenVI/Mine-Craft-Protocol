import assert from 'node:assert/strict';
import {baselineStatistics} from './interaction-baseline-actions.mjs';

const MINIMUM_PIPELINE_INTERVAL = 550;
const ATTACK_HOLD_MILLIS = 40;
const TARGET_BATCH_TO_SINGLE_RATIO = 1.5;
const ATTACK_FIXTURE_TAG = 'mcp_continuous_performance_attack';
const ATTACK_COUNTER_SCOPE = 'native_multiplayer_game_mode_attack_return';
const ATTACK_COUNTER_UNITS = 'native_entity_attack_invocations_not_damage_or_swing';
const ATTACK_OBSERVATION_MILLIS = 500;
const ATTACK_OBSERVATION_TICKS = 2;
const RELEASED = {type: 'input.released'};
const TERMINAL = new Set(['completed', 'failed', 'cancelled']);

export function performanceAttackSteps(count) {
  assert(count === 1 || count === 3, 'Only the declared single/three-pair comparison is supported');
  return Array.from({length: count}, () => [
    {type: 'mouse.button', button: 0, action: 1, modifiers: 0},
    {type: 'delay', durationMs: ATTACK_HOLD_MILLIS},
    {type: 'mouse.button', button: 0, action: 0, modifiers: 0}
  ]).flat();
}

export function performanceAttackConsumption(sample, count) {
  const before = sample.inputBefore, after = sample.inputAfter;
  const result = {actualAttackCountConfirmed: false, gameConsumption: 'not_confirmed', nativeEntityAttackDelta: null,
    counterIsConservativeLowerBound: true, missingDeltaDoesNotProveNoAttack: true,
    swingCount: null, hit: 'not_confirmed', serverEffect: 'not_confirmed', serverEffectConfirmed: false, damageConfirmed: false,
    consumptionReason: 'native_attack_counter_unavailable'};
  if (sample.nativeConsumptionObservation?.invalidated === true)
    return {...result, consumptionReason: sample.nativeConsumptionObservation.reason};
  const valid = value => value && Number.isSafeInteger(value.nativeEntityAttackCount) && value.nativeEntityAttackCount >= 0
    && value.nativeEntityAttackScope === ATTACK_COUNTER_SCOPE && value.nativeEntityAttackCounterUnits === ATTACK_COUNTER_UNITS
    && typeof value.nativeEntityAttackContextId === 'string' && value.nativeEntityAttackContextId.length > 0
    && Number.isSafeInteger(value.nativeEntityAttackContextGeneration) && value.nativeEntityAttackContextGeneration > 0
    && Number.isSafeInteger(value.nativeEntityAttackControlGeneration) && value.nativeEntityAttackControlGeneration >= 0
    && value.nativeEntityAttackServerEffectConfirmed === false;
  if (!valid(before) || !valid(after)) return result;
  if (before.nativeEntityAttackContextId !== after.nativeEntityAttackContextId
    || before.nativeEntityAttackContextGeneration !== after.nativeEntityAttackContextGeneration
    || before.nativeEntityAttackControlGeneration !== after.nativeEntityAttackControlGeneration)
    return {...result, consumptionReason: 'native_attack_counter_context_changed'};
  if (before.nativeEntityAttackLeaseActive !== true || after.nativeEntityAttackLeaseActive !== true)
    return {...result, consumptionReason: 'native_attack_counter_lease_not_active'};
  const delta = after.nativeEntityAttackCount - before.nativeEntityAttackCount;
  if (!Number.isSafeInteger(delta) || delta < 0) return {...result, consumptionReason: 'native_attack_counter_reset'};
  if (delta !== count) return {...result, nativeEntityAttackDelta: delta,
    consumptionReason: 'native_attack_count_not_confirmed_possible_late_consumption'};
  return {...result, actualAttackCountConfirmed: true, gameConsumption: 'confirmed_native_entity_attack_invocations',
    nativeEntityAttackDelta: delta, hit: 'native_entity_attack_branch',
    consumptionReason: 'passive_routed_native_entity_attack_return_not_damage_or_server_ack'};
}

// This passive window starts after the unchanged pipeline terminal measurement; it never submits input.
export async function observePerformanceAttackConsumption(sample, count, {readInput, readLease, leaseId, now, sleep}) {
  assert.equal(sample.outcome.state, 'completed'); assert.equal(typeof leaseId, 'string'); assert(leaseId.length > 0);
  assert(!sample.nativeConsumptionObservation, 'Each attack sample has only one passive observation window');
  const terminal = sample.terminalInputSnapshot = structuredClone(sample.inputAfter);
  const began = now(), deadline = began + ATTACK_OBSERVATION_MILLIS, terminalTick = terminal.clientTick;
  const observation = {scope: 'post_terminal_passive_native_entity_attack_observation', timeSource: 'monotonic_elapsed',
    maximumMillis: ATTACK_OBSERVATION_MILLIS, maximumNewNativeTicks: ATTACK_OBSERVATION_TICKS, terminalClientTick: terminalTick,
    samples: [], inputResubmitted: false, newAttacksRequested: 0, includedInPipelineOrObservedTerminalTiming: false,
    invalidated: false, reason: 'native_attack_counter_unavailable', leasePreserved: true};
  let current = terminal, lease;
  function finish(reason, invalidated = false) {
    observation.reason = reason; observation.invalidated = invalidated;
    observation.elapsedMillis = now() - began;
    observation.newNativeTicks = Number.isSafeInteger(current.clientTick) && Number.isSafeInteger(terminalTick) ? current.clientTick - terminalTick : null;
    sample.inputAfter = current; sample.nativeConsumptionObservation = observation; return observation;
  }
  function invalidLease(value) {return value?.mode !== 'TAKEOVER' || value.status !== 'active' || value.leaseId !== leaseId;}
  while (true) {
    lease = await readLease();
    const elapsedMillis = now() - began;
    if (invalidLease(lease)) {
      observation.samples.push({elapsedMillis, input: current, lease, acceptedWithinWindow: false}); observation.leasePreserved = false;
      return finish('native_attack_observation_lease_changed', true);
    }
    if (elapsedMillis >= ATTACK_OBSERVATION_MILLIS) {
      observation.samples.push({elapsedMillis, input: current, lease, acceptedWithinWindow: false});
      return finish('native_attack_observation_time_budget_exhausted', true);
    }
    if (!Number.isSafeInteger(terminalTick) || !Number.isSafeInteger(current.clientTick) || current.clientTick < terminalTick) {
      observation.samples.push({elapsedMillis, input: current, lease, acceptedWithinWindow: false});
      return finish('native_attack_observation_tick_unavailable_or_reversed', true);
    }
    const tickDelta = current.clientTick - terminalTick;
    if (tickDelta > ATTACK_OBSERVATION_TICKS) {
      observation.samples.push({elapsedMillis, input: current, lease, acceptedWithinWindow: false});
      return finish('native_attack_observation_tick_budget_exceeded', true);
    }
    const consumption = performanceAttackConsumption({...sample, inputAfter: current}, count);
    observation.samples.push({elapsedMillis, input: current, lease, acceptedWithinWindow: true, consumption});
    if (consumption.actualAttackCountConfirmed) return finish(tickDelta === 0 && observation.samples.length === 1
      ? 'terminal_native_attack_delta_confirmed' : 'native_attack_delta_observed_after_terminal');
    if (consumption.consumptionReason !== 'native_attack_count_not_confirmed_possible_late_consumption'
      || consumption.nativeEntityAttackDelta > count) return finish(consumption.consumptionReason);
    if (tickDelta === ATTACK_OBSERVATION_TICKS) return finish('native_attack_count_not_confirmed_within_two_native_ticks');
    const pause = Math.min(20, deadline - now());
    if (pause <= 0) return finish('native_attack_observation_time_budget_exhausted', true);
    await sleep(pause);
    if (now() >= deadline) return finish('native_attack_observation_time_budget_exhausted', true);
    current = await readInput();
  }
}

export function performanceAttackTiming(sample, count) {
  assert.equal(sample.outcome.state, 'completed', 'A running/failed operation is not a timing PASS');
  const result = sample.outcome.result, timing = result?.timing;
  assert.equal(result?.cleanupOnComplete, true);
  assert.deepEqual(sample.steps, performanceAttackSteps(count));
  assert.equal(result.steps.length, sample.steps.length);
  for (let index = 0; index < result.steps.length; index++) {
    assert.equal(result.steps[index].stepType, sample.steps[index].type);
    assert(Number.isFinite(result.steps[index].elapsedMillis) && result.steps[index].elapsedMillis >= 0);
  }
  assert.equal(timing?.scope, 'automation_pipeline_not_http_or_server_effect');
  assert.equal(timing.timeSource, 'monotonic_elapsed');
  for (const field of ['queueMillis', 'totalMillis', 'requestedDelayMillis', 'inputCallbackCount'])
    assert(Number.isFinite(timing[field]) && timing[field] >= 0, 'Invalid timing.' + field);
  assert(Number.isSafeInteger(timing.inputCallbackCount));
  assert(timing.totalMillis > 0);
  assert.equal(timing.requestedDelayMillis, count * ATTACK_HOLD_MILLIS, 'Do not omit batch holds to meet the ratio');
  assert.equal(timing.stages?.cleanup?.calls, 1);
  assert.equal(sample.inputAfter.pressedKeyCount, 0); assert.equal(sample.inputAfter.pressedButtonCount, 0);
  assert.equal(sample.inputAfter.inputDispatchSequence - sample.inputBefore.inputDispatchSequence, count * 2 + 1,
    'Actual input callbacks and the one unconditional cleanup must be distinguished');
  const events = result.steps.filter(step => step.stepType === 'mouse.button').map(step => step.result);
  assert.equal(events.length, count * 2);
  for (const event of events) {
    assert.equal(event.inputOrigin, 'AGENT_ROUTED'); assert.equal(event.entryLayer, 'GAME_ROUTED_RAW');
    assert.equal(event.hostCursorUsed, false); assert.equal(event.directMutationUsed, false);
    assert(Number.isSafeInteger(event.clientTick) && event.clientTick >= 0);
    assert(Number.isSafeInteger(event.inputDispatchSequence) && event.inputDispatchSequence >= 0);
  }
  for (let index = 1; index < events.length; index++) {
    assert(events[index].clientTick >= events[index - 1].clientTick);
    assert(events[index].inputDispatchSequence > events[index - 1].inputDispatchSequence);
  }
  for (const field of ['postRttMillis', 'waitRttMillis', 'observedTerminalMillis'])
    assert(Number.isFinite(sample[field]) && sample[field] >= 0, 'Invalid HTTP timing.' + field);
  return {
    timing, events, nativeCallbackTickSpan: events.at(-1).clientTick - events[0].clientTick,
    dispatchSequenceDelta: sample.inputAfter.inputDispatchSequence - sample.inputBefore.inputDispatchSequence,
    requestedPairs: count, dispatchedButtonCallbacks: events.length, cleanupCalls: 1,
    ...performanceAttackConsumption(sample, count), vanillaCooldown: 'unchanged',
    postTerminalObservationMillis: sample.nativeConsumptionObservation?.elapsedMillis ?? null,
    postTerminalObservationIncludedInTimings: false
  };
}

export function performanceAttackComparison(singles, batch) {
  assert.equal(singles.length, 3, 'Use three actual single-pair samples');
  const singleDetails = singles.map(sample => performanceAttackTiming(sample, 1));
  const batchDetails = performanceAttackTiming(batch, 3);
  const internal = baselineStatistics(singleDetails.map(value => value.timing.totalMillis));
  const http = baselineStatistics(singles.map(value => value.observedTerminalMillis));
  assert(internal.p50 > 0 && http.p50 > 0);
  const ratio = batchDetails.timing.totalMillis / internal.p50;
  const singleOverhead = baselineStatistics(singleDetails.map(value => Math.max(0, value.timing.totalMillis - value.timing.requestedDelayMillis)));
  return {
    scope: 'isolated_native_input_submission_with_passive_entity_attack_observation', singleSampleCount: 3, batchSampleCount: 1,
    holdMillisPerPair: ATTACK_HOLD_MILLIS, matchedPairParameters: true, nativeCooldown: 'unchanged',
    singleInternalMillis: internal, singleObservedTerminalMillis: http,
    batchInternalMillis: batchDetails.timing.totalMillis,
    batchToSingleP50Ratio: ratio, targetRatio: TARGET_BATCH_TO_SINGLE_RATIO,
    targetMet: ratio <= TARGET_BATCH_TO_SINGLE_RATIO, targetResult: ratio <= TARGET_BATCH_TO_SINGLE_RATIO ? 'MET' : 'NOT_MET',
    diagnosticRatios: {
      batchToThreeSinglesTotal: batchDetails.timing.totalMillis / singleDetails.reduce((sum, value) => sum + value.timing.totalMillis, 0),
      batchHttpToSingleP50: batch.observedTerminalMillis / http.p50,
      batchOverheadToSingleP50: singleOverhead.p50 > 0 ? Math.max(0, batchDetails.timing.totalMillis - batchDetails.timing.requestedDelayMillis) / singleOverhead.p50 : null
    },
    diagnosticRatiosCannotReplaceTarget: true,
    actualAttackCountConfirmed: singleDetails.every(value => value.actualAttackCountConfirmed) && batchDetails.actualAttackCountConfirmed,
    serverEffectConfirmed: false,
    statisticsAreCrossMachineSla: false, singleDetails, batchDetails
  };
}

// The optional clock is for deterministic tool tests. Its output is explicitly not Live evidence.
export async function continuousPerformanceActions({api, target, fixtureInput, mode, acquire, release, click, clickNode, wait, nodes, check, actionSamples, forgetExpiredLease, holdMillis = 30000}, clock) {
  assert(Number.isSafeInteger(holdMillis) && holdMillis >= 30000 && holdMillis <= 60000, 'Hold duration must be 30..60 seconds');
  const now = clock?.now ?? (() => performance.now());
  const pause = clock?.sleep ?? (millis => new Promise(done => setTimeout(done, millis)));
  const evidenceKind = clock ? 'synthetic_test_not_runtime' : 'runtime_monotonic';
  const began = now(), deadline = began + 180000;
  let ownsLease = false, leaseId, lastPipelinePost = -Infinity, primaryFailure, cleanupFailure, finalStop, comparison, stopUnconfirmed = false;
  let attackFixtureTouched = false, attackFixture, attackFixturePosition;
  function remaining(maximum = 5000) {
    const left = Math.floor(deadline - now()); assert(left > 0, 'Continuous performance exceeded 180 seconds');
    return Math.min(maximum, left);
  }
  async function readInput(zero = false) {
    const value = await api('GET', '/v0/input/state');
    for (const field of ['pressedKeyCount', 'pressedButtonCount', 'inputDispatchSequence'])
      assert(Number.isSafeInteger(value[field]) && value[field] >= 0, 'Invalid input snapshot.' + field);
    if (zero) { assert.equal(value.pressedKeyCount, 0); assert.equal(value.pressedButtonCount, 0); }
    return value;
  }
  async function until(read, predicate, label, budget = 5000) {
    const end = now() + remaining(budget); let value;
    do { value = await read(); if (predicate(value)) return value; await pause(50); } while (now() < end);
    assert.fail(label + ': ' + JSON.stringify(value));
  }
  async function take() {
    remaining(); await acquire(); ownsLease = true;
    const value = await api('GET', '/v0/control/status');
    assert.equal(value.mode, 'TAKEOVER'); assert.equal(value.status, 'active'); assert.equal(typeof value.leaseId, 'string');
    leaseId = value.leaseId; return value;
  }
  async function released() {
    const value = await api('POST', '/v0/wait/until', {condition: RELEASED, timeoutMs: 5000});
    assert.equal(value.passed, true); assert.deepEqual(value.condition, RELEASED);
    assert.deepEqual(value.evidence, {pressedKeyCount: 0, pressedButtonCount: 0}); return value;
  }
  async function stop() {
    const before = await readInput(), at = now();
    stopUnconfirmed = true;
    const response = await api('POST', '/v0/input/release-all', {});
    const responseRttMillis = now() - at; assert.equal(response.type, 'input.release_all');
    const waited = await released(), after = await readInput(true);
    assert.equal(after.inputCleanupPending, false); assert.equal(after.inputCleanupFailed, false);
    assert.equal(after.inputGeneration, response.inputGeneration);
    stopUnconfirmed = false;
    return {before, response, responseRttMillis, waited, after, cleanupBarrierConfirmedBy: 'release_all_response'};
  }
  async function giveBack() {
    if (!ownsLease) return;
    const current = await api('GET', '/v0/control/status');
    if (leaseId === undefined || current.leaseId === leaseId) await release();
    else {
      assert.equal(current.status, 'available', 'Never release an unrelated owner'); assert.equal(current.mode, 'READ');
      await released();
      assert.equal(typeof forgetExpiredLease, 'function', 'Outer runner must forget only an actually ended Lease');
      forgetExpiredLease();
    }
    ownsLease = false; leaseId = undefined;
  }
  async function nativeCommand(command) {
    const receipt = await api('POST', '/v0/command/player', {command, route: 'NATIVE'});
    assert.equal(receipt.route, 'NATIVE'); assert.equal(receipt.inputDispatched, false); assert.equal(receipt.effectConfirmed, false);
    return receipt;
  }
  async function attackEntities() {
    const value = await api('GET', '/v0/world/entities?category=USER&radius=4&limit=32');
    assert.equal(value.dataCategory, 'USER'); assert.equal(value.authority, 'CLIENT_KNOWN');
    assert.equal(value.chunkLoadRequested, false); assert.equal(value.entitiesTruncated, false);
    assert(Array.isArray(value.entities)); return value.entities;
  }
  function isAttackEntity(value) {
    return attackFixturePosition && value.type === 'minecraft:cow'
      && Math.abs(value.x - attackFixturePosition.x) < .05 && Math.abs(value.y - attackFixturePosition.y) < .05
      && Math.abs(value.z - attackFixturePosition.z) < .05;
  }
  function descendants(value) {return [value, ...(value.children ?? []).flatMap(descendants)];}
  async function prepareAttackFixture() {
    const began = now(); await giveBack(); await mode('OPERATE'); attackFixtureTouched = true;
    const position = attackFixturePosition = {x: arena.x, y: arena.y, z: arena.z + 2.5};
    const receipts = [];
    for (const command of [
      'clear @s', `kill @e[type=minecraft:cow,tag=${ATTACK_FIXTURE_TAG}]`,
      `tp @s ${arena.x} ${arena.y} ${arena.z} 0 0`,
      `summon minecraft:cow ${position.x.toFixed(3)} ${position.y.toFixed(3)} ${position.z.toFixed(3)} {NoAI:1b,NoGravity:1b,Invulnerable:1b,Silent:1b,PersistenceRequired:1b,Tags:["${ATTACK_FIXTURE_TAG}"]}`
    ]) receipts.push(await nativeCommand(command));
    const actor = await until(() => api('GET', '/v0/player?category=USER'), value => value.dimension === arena.dimension
      && Math.abs(value.x - arena.x) < .05 && Math.abs(value.y - arena.y) < .05 && Math.abs(value.z - arena.z) < .05
      && value.onGround === true && value.flying === false && value.gameMode === 'survival', 'Attack fixture player state was not received');
    const emptyHand = await until(() => api('GET', '/v0/ui/tree'), value => !value.screenClass
      && descendants(value.presentation ?? {}).some(node => node.selected === true && node.count === 0), 'Empty main-hand HUD was not received');
    await take(); await released();
    const entities = await until(attackEntities, values => values.filter(isAttackEntity).length === 1,
      'Unique client-known attack fixture was not received');
    const entity = entities.find(isAttackEntity); assert.equal(typeof entity.targetRef, 'string'); assert(entity.targetRef.length > 0);
    const aim = await sendPipeline([{type: 'world.action', category: 'USER', targetRef: entity.targetRef, action: 'aim'}]);
    const aimed = aim.outcome.result.steps[0].result;
    assert.equal(aimed.onTarget, true); assert.equal(aimed.actualHit?.kind, 'entity');
    assert.equal(aimed.actualHit.entityType, 'minecraft:cow'); assert(aimed.actualHit.distance > 0 && aimed.actualHit.distance <= 3);
    assert.equal((await api('GET', '/v0/ui/tree')).screenClass, '');
    attackFixture = {entity, actor, emptyHand, receipts, aim, elapsedMillis: now() - began,
      scope: 'diagnostic_fixture_and_real_USER_semantic_aim_excluded_from_attack_timing', tag: ATTACK_FIXTURE_TAG,
      arrangeAuthority: 'diagnostic_current_player_NATIVE_commands', actAuthority: 'PLAYTEST', nativeCooldownChanged: false,
      invulnerableIsFixtureOnly: true, serverEffectConfirmed: false, damageConfirmed: false};
    actionSamples.push({case: 'continuous_performance_attack_fixture', ...attackFixture});
  }
  async function sendPipeline(steps, cleanupOnComplete = true) {
    const waitSpacing = Math.max(0, MINIMUM_PIPELINE_INTERVAL - (now() - lastPipelinePost));
    if (waitSpacing) { assert(waitSpacing < remaining()); await pause(waitSpacing); }
    const inputBefore = await readInput();
    remaining(); const postAt = now(); lastPipelinePost = postAt;
    const started = await api('POST', '/v0/pipelines', {steps, timeoutMs: 12000, cleanupOnComplete});
    assert.equal(typeof started.operationId, 'string'); const postRttMillis = now() - postAt;
    const operationDeadline = now() + remaining(15000); let outcome, waitRttMillis = 0;
    do {
      const waitAt = now();
      outcome = await api('POST', `/v0/operations/${started.operationId}/wait`, {timeoutMs: Math.max(1, Math.min(remaining(3000), Math.floor(operationDeadline - now())))});
      waitRttMillis += now() - waitAt;
      if (TERMINAL.has(outcome.state)) break;
      assert.equal(outcome.status, 'running', 'Unknown operation status');
    } while (now() < operationDeadline);
    const observedTerminalMillis = now() - postAt;
    assert.equal(outcome.state, 'completed', 'Wait timeout/failure is not a completed input: ' + JSON.stringify(outcome));
    const inputAfter = await readInput();
    return {operationId: started.operationId, steps, cleanupOnComplete, postRttMillis, waitRttMillis, observedTerminalMillis,
      inputBefore, inputAfter, outcome, admissionSpacingMillis: waitSpacing};
  }
  const key = (keyCode, action) => ({type: 'key', key: keyCode, action, scanCode: 0, modifiers: 0});
  const scenePath = '/v0/world/scene?category=USER&radius=2&blockLimit=1&entityLimit=8';
  let arena, moveSummary;
  try {
    await check('performance admits only the verified restored world and prepares a bounded diagnostic flat arena', async () => {
      assert.equal(fixtureInput?.policy, 'immutable_baseline_restored'); assert.equal(fixtureInput.target, target);
      assert.equal(fixtureInput.role, 'client'); assert.equal(fixtureInput.credentialsCopied, false);
      assert.match(fixtureInput.baselineSha256, /^[0-9a-f]{64}$/); assert.equal(fixtureInput.restoredSha256, fixtureInput.baselineSha256);
      await mode('READ'); await readInput(true);
      const title = await api('GET', '/v0/ui/tree'); assert(title.screenClass.endsWith('TitleScreen')); assert(!title.overlayIdentity);
      await take(); await click('menu.singleplayer', 'Singleplayer');
      const list = await wait(value => value.screenClass.endsWith('SelectWorldScreen'), 'performance world list', remaining(20000));
      const row = nodes(list).find(value => value.worldName === 'World-Read-Alpha'); assert(row);
      await clickNode(row); await click('selectWorld.select', 'Play Selected World');
      await wait(value => !value.screenClass, 'performance fixed world entered', remaining(60000));
      await giveBack(); await mode('OPERATE');
      const original = await api('GET', '/v0/player?category=USER'); assert.equal(original.dimension, 'minecraft:overworld');
      assert(Number.isFinite(original.x) && Number.isFinite(original.y) && Number.isFinite(original.z));
      const x = Math.floor(original.x), z = Math.floor(original.z), floorY = Math.min(200, Math.max(64, Math.floor(original.y) + 8));
      arena = {x: x + .5, y: floorY + 1, z: z + .5, floorY, halfSize: 8, maximumHorizontalOffset: 7, dimension: original.dimension,
        physicalContainment: 'three_high_native_stone_perimeter'};
      const commands = [
        `fill ${x - 8} ${floorY} ${z - 8} ${x + 8} ${floorY} ${z + 8} minecraft:stone`,
        `fill ${x - 8} ${floorY + 1} ${z - 8} ${x + 8} ${floorY + 4} ${z + 8} minecraft:air`,
        `fill ${x - 8} ${floorY + 1} ${z - 8} ${x - 8} ${floorY + 3} ${z + 8} minecraft:stone`,
        `fill ${x + 8} ${floorY + 1} ${z - 8} ${x + 8} ${floorY + 3} ${z + 8} minecraft:stone`,
        `fill ${x - 8} ${floorY + 1} ${z - 8} ${x + 8} ${floorY + 3} ${z - 8} minecraft:stone`,
        `fill ${x - 8} ${floorY + 1} ${z + 8} ${x + 8} ${floorY + 3} ${z + 8} minecraft:stone`,
        'gamemode survival @s', `tp @s ${arena.x} ${arena.y} ${arena.z} 0 0`
      ];
      const receipts = [];
      for (const command of commands) {
        const receipt = await api('POST', '/v0/command/player', {command, route: 'NATIVE'});
        assert.equal(receipt.route, 'NATIVE'); assert.equal(receipt.inputDispatched, false); assert.equal(receipt.effectConfirmed, false);
        receipts.push(receipt);
      }
      const settled = await until(() => api('GET', '/v0/player?category=USER'), value => value.dimension === arena.dimension
        && Math.abs(value.x - arena.x) < .05 && Math.abs(value.y - arena.y) < .05 && Math.abs(value.z - arena.z) < .05
        && value.onGround === true && value.flying === false && value.gameMode === 'survival', 'Diagnostic arena player state was not received');
      for (const [dx, dz] of [[0, 0], [-7, 0], [7, 0], [0, -7], [0, 7]])
        await until(() => api('GET', `/v0/world/block?category=USER&x=${x + dx}&y=${floorY}&z=${z + dz}`), value => value.available === true && value.block === 'minecraft:stone', 'Arena cached floor unavailable');
      const verifiedWalls = [];
      for (const [dx, dz] of [[-8, 0], [8, 0], [0, -8], [0, 8]])
        for (const height of [1, 3]) verifiedWalls.push(await until(() => api('GET', `/v0/world/block?category=USER&x=${x + dx}&y=${floorY + height}&z=${z + dz}`), value => value.available === true && value.block === 'minecraft:stone', 'Arena native containment was not observed'));
      await take(); await released();
      actionSamples.push({case: 'continuous_performance_fixture', fixtureInput, worldName: row.worldName, arena, original, receipts, settled, verifiedWalls,
        arrangeAuthority: 'diagnostic_current_player_NATIVE_commands', arrangeIsGameplayAcceptance: false, copyOnlySideEffects: true});
    });
    await check('performance holds movement for 30..60 seconds with bounded W/S, relative look and interleaved USER observations', async () => {
      const initial = await sendPipeline([key(87, 1)], false);
      assert.equal(initial.inputAfter.pressedKeyCount, 1); assert(initial.inputAfter.pressedKeys?.includes(87));
      const start = now(), end = start + holdMillis, observations = [];
      let direction = 87, index = 0;
      while (now() < end) {
        const requestedDirection = index % 2 ? 83 : 87, steps = [];
        if (requestedDirection !== direction) steps.push(key(direction, 0), key(requestedDirection, 1));
        steps.push({type: 'mouse.delta', dx: index % 2 ? -3 : 3, dy: 0});
        const inputSample = await sendPipeline(steps, false); direction = requestedDirection;
        assert.equal(inputSample.inputAfter.pressedKeyCount, 1); assert.equal(inputSample.inputAfter.pressedButtonCount, 0);
        assert(inputSample.inputAfter.pressedKeys?.includes(direction));
        for (const step of inputSample.outcome.result.steps) {
          assert.equal(step.result.inputOrigin, 'AGENT_ROUTED'); assert.equal(step.result.entryLayer, 'GAME_ROUTED_RAW');
          assert.equal(step.result.hostCursorUsed, false); assert.equal(step.result.directMutationUsed, false);
          assert(Number.isSafeInteger(step.result.clientTick) && step.result.clientTick >= 0);
        }
        const readAt = now(), player = await api('GET', '/v0/player?category=USER');
        const playerRttMillis = now() - readAt, sceneAt = now(), scene = await api('GET', scenePath);
        const sceneRttMillis = now() - sceneAt;
        assert.equal(player.dataCategory, 'USER'); assert.equal(scene.dataCategory, 'USER'); assert.equal(scene.authority, 'CLIENT_KNOWN');
        assert.equal(scene.chunkLoadRequested, false);
        assert(Number.isSafeInteger(player.clientTick) && player.clientTick >= 0);
        assert.equal(player.dimension, arena.dimension); assert.equal(player.onGround, true); assert.equal(player.flying, false);
        assert(Math.abs(player.x - arena.x) <= arena.maximumHorizontalOffset && Math.abs(player.z - arena.z) <= arena.maximumHorizontalOffset
          && Math.abs(player.y - arena.y) < .15, 'Actor left the bounded flat arena; stop instead of walking onward');
        assert.equal((await api('GET', '/v0/ui/tree')).screenClass, '');
        observations.push({index, direction, elapsedMillis: now() - start, inputSample, player, scene, playerRttMillis, sceneRttMillis});
        index++;
        const next = Math.min(end, start + index * 1000);
        if (next > now()) await pause(next - now());
        remaining();
      }
      const finalPlayer = await api('GET', '/v0/player?category=USER'), observedMillis = now() - start;
      assert(observedMillis >= holdMillis); assert(observations.length >= Math.floor(holdMillis / 2000), 'Insufficient interleaved observations');
      const firstPlayer = observations[0].player;
      assert(finalPlayer.clientTick > firstPlayer.clientTick, 'No real client tick progress');
      const travelled = observations.slice(1).reduce((distance, observation, at) => distance + Math.hypot(observation.player.x - observations[at].player.x, observation.player.z - observations[at].player.z), 0);
      assert(travelled > 1, 'Actual client movement was not observed');
      moveSummary = {requestedMillis: holdMillis, observedMillis, segmentMillis: 1000, observations, finalPlayer, clientObservedTravelDistance: travelled,
        firstClientTick: firstPlayer.clientTick, lastClientTick: finalPlayer.clientTick, originalSensitivityUnchanged: true, serverEffectConfirmed: false};
      actionSamples.push({case: 'continuous_performance_movement', ...moveSummary});
    });
    await check('performance strong stop drains movement and prepares a separately timed native entity target', async () => {
      const stopped = await stop();
      assert.equal(stopped.before.pressedKeyCount, 1);
      const current = await api('GET', '/v0/control/status'); assert.equal(current.leaseId, leaseId); assert.equal(current.mode, 'TAKEOVER');
      actionSamples.push({case: 'continuous_performance_movement_stop', ...stopped, leasePreserved: true});
      await prepareAttackFixture();
    });
    const singles = [];
    await check('performance records three matched single-pair pipelines and distinguishes native consumption from dispatch', async () => {
      for (let index = 0; index < 3; index++) {
        const playerBefore = await api('GET', '/v0/player?category=USER');
        const sample = await sendPipeline(performanceAttackSteps(1));
        await observePerformanceAttackConsumption(sample, 1, {readInput, readLease: () => api('GET', '/v0/control/status'), leaseId, now, sleep: pause});
        sample.playerBefore = playerBefore; sample.playerAfter = await api('GET', '/v0/player?category=USER'); sample.attackTarget = attackFixture.entity;
        sample.details = performanceAttackTiming(sample, 1); singles.push(sample);
        actionSamples.push({case: 'continuous_performance_attack_single', index, ...sample});
      }
    });
    await check('performance measures a matched three-pair pipeline and reports the unmodified 1.5 target independently', async () => {
      const playerBefore = await api('GET', '/v0/player?category=USER');
      const batch = await sendPipeline(performanceAttackSteps(3));
      await observePerformanceAttackConsumption(batch, 3, {readInput, readLease: () => api('GET', '/v0/control/status'), leaseId, now, sleep: pause});
      batch.playerBefore = playerBefore; batch.playerAfter = await api('GET', '/v0/player?category=USER'); batch.attackTarget = attackFixture.entity;
      comparison = performanceAttackComparison(singles, batch);
      actionSamples.push({case: 'continuous_performance_attack_comparison', batch, comparison,
        targetNotMetBlocksPerformanceCompletion: true, measurementValidityDoesNotMeanTargetMet: true});
    });
  } catch (error) { primaryFailure = error; }
  finally {
    if (ownsLease) {
      try {
        const current = await api('GET', '/v0/control/status');
        if (current.leaseId === leaseId && !stopUnconfirmed) finalStop = await stop();
      } catch (error) { cleanupFailure = error; actionSamples.push({case: 'continuous_performance_cleanup_failure', error: String(error)}); }
      try { await giveBack(); }
      catch (error) { cleanupFailure ??= error; actionSamples.push({case: 'continuous_performance_lease_cleanup_failure', error: String(error)}); }
    }
    if (attackFixtureTouched && !ownsLease) {
      try {
        await mode('OPERATE');
        const receipt = await nativeCommand(`kill @e[type=minecraft:cow,tag=${ATTACK_FIXTURE_TAG}]`);
        const entities = await until(attackEntities, values => !values.some(isAttackEntity), 'Attack fixture cleanup was not received');
        await mode('READ');
        actionSamples.push({case: 'continuous_performance_attack_fixture_cleanup', receipt, entities, ownedFixtureTag: ATTACK_FIXTURE_TAG});
      } catch (error) {cleanupFailure ??= error; actionSamples.push({case: 'continuous_performance_attack_fixture_cleanup_failure', error: String(error)});}
    }
  }
  if (primaryFailure) { if (cleanupFailure) console.error(cleanupFailure); throw primaryFailure; }
  if (cleanupFailure) throw cleanupFailure;
  const finalInput = await readInput(true), finalMode = await api('GET', '/v0/control/mode');
  assert.equal(finalMode.mode, 'READ'); assert(finalStop, 'Final explicit strong stop was not observed');
  assert(now() - began <= 180000, 'Scenario exceeded its advertised total budget');
  const result = {case: 'continuous_performance_complete', target, evidenceKind, liveEvidence: !clock, checks: 5,
    holdMillis, elapsedMillis: now() - began, maximumMillis: 180000, finalStop, finalInput, finalMode,
    comparisonTargetResult: comparison.targetResult, performanceComplete: comparison.targetMet && comparison.actualAttackCountConfirmed,
    nativeCooldownChanged: false, physicalEscTested: false, networkDisconnectTested: false,
    attackConsumptionConfirmed: comparison.actualAttackCountConfirmed, serverEffectConfirmed: false,
    nativeCounterIsConservativeLowerBound: true, damageConfirmed: false, clientShutdownOwnedByOuterRunner: true, persistentWriteCalls: 0};
  actionSamples.push(result); return result;
}
