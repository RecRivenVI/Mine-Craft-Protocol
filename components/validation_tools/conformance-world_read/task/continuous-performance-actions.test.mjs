import assert from 'node:assert/strict';
import test from 'node:test';
import {continuousPerformanceActions, observePerformanceAttackConsumption, performanceAttackComparison, performanceAttackConsumption, performanceAttackSteps, performanceAttackTiming} from './continuous-performance-actions.mjs';

function timingSample(count, totalMillis = count * 60 + 20) {
  const steps = performanceAttackSteps(count); let sequence = 0;
  const results = steps.map(step => ({stepType: step.type, elapsedMillis: step.type === 'delay' ? step.durationMs : 5,
    result: step.type === 'delay' ? {type: 'delay'} : {inputOrigin: 'AGENT_ROUTED', entryLayer: 'GAME_ROUTED_RAW', hostCursorUsed: false,
      directMutationUsed: false, clientTick: 100, inputDispatchSequence: ++sequence}}));
  return {steps, postRttMillis: 5, waitRttMillis: totalMillis + 5, observedTerminalMillis: totalMillis + 10,
    inputBefore: {inputDispatchSequence: 0, pressedKeyCount: 0, pressedButtonCount: 0},
    inputAfter: {inputDispatchSequence: sequence + 1, pressedKeyCount: 0, pressedButtonCount: 0},
    outcome: {state: 'completed', result: {cleanupOnComplete: true, steps: results, timing: {
      scope: 'automation_pipeline_not_http_or_server_effect', timeSource: 'monotonic_elapsed', queueMillis: 0, totalMillis,
      requestedDelayMillis: count * 40, inputCallbackCount: count * 2, stages: {cleanup: {calls: 1, elapsedMillis: 2}}
    }}}};
}

function nativeCounterSample(count, totalMillis) {
  const sample = timingSample(count, totalMillis);
  const context = {nativeEntityAttackCount: 7, nativeEntityAttackScope: 'native_multiplayer_game_mode_attack_return',
    nativeEntityAttackCounterUnits: 'native_entity_attack_invocations_not_damage_or_swing', nativeEntityAttackContextId: 'context-one',
    nativeEntityAttackContextGeneration: 2, nativeEntityAttackControlGeneration: 5, nativeEntityAttackLeaseActive: true,
    nativeEntityAttackServerEffectConfirmed: false};
  Object.assign(sample.inputBefore, context); Object.assign(sample.inputAfter, context, {nativeEntityAttackCount: 7 + count});
  sample.inputBefore.clientTick = 99; sample.inputAfter.clientTick = 100;
  return sample;
}

function observationFixture(options = {}) {
  const sample = nativeCounterSample(3); sample.inputAfter.nativeEntityAttackCount = 9;
  const terminal = structuredClone(sample.inputAfter), states = [], statuses = []; let millis = 0, polls = 0;
  const lease = {mode: 'TAKEOVER', status: 'active', leaseId: 'own-lease'};
  const services = {leaseId: lease.leaseId, now: () => millis, sleep: async delay => {millis += delay;},
    async readInput() {
      polls++; millis += options.readMillis ?? 1;
      const value = {...terminal, clientTick: terminal.clientTick + (options.frozenTick ? 0 : polls), nativeEntityAttackCount: options.insufficient ? 9 : 10};
      options.editInput?.(value, polls); states.push(structuredClone(value)); return value;
    },
    async readLease() {const value = {...lease}; options.editLease?.(value, polls); statuses.push(value); return value;}
  };
  return {sample, terminal, services, states, statuses, polls: () => polls};
}

test('bounded passive observation sees late native consumption without changing pipeline/HTTP terminal timing or replaying input', async () => {
  const value = observationFixture(), original = structuredClone(value.sample);
  const observed = await observePerformanceAttackConsumption(value.sample, 3, value.services);
  assert.equal(observed.reason, 'native_attack_delta_observed_after_terminal'); assert.equal(observed.invalidated, false);
  assert.equal(observed.newNativeTicks, 1); assert.equal(observed.samples.length, 2); assert.equal(value.polls(), 1);
  assert.equal(observed.elapsedMillis, 21); assert.equal(observed.newAttacksRequested, 0); assert.equal(observed.inputResubmitted, false);
  assert.equal(observed.includedInPipelineOrObservedTerminalTiming, false);
  assert.deepEqual(value.sample.terminalInputSnapshot, original.inputAfter); assert.deepEqual(value.sample.outcome, original.outcome);
  for (const field of ['postRttMillis', 'waitRttMillis', 'observedTerminalMillis', 'steps']) assert.deepEqual(value.sample[field], original[field]);
  const result = performanceAttackTiming(value.sample, 3); assert.equal(result.actualAttackCountConfirmed, true);
  assert.equal(result.postTerminalObservationMillis, 21); assert.equal(result.postTerminalObservationIncludedInTimings, false);
  assert.equal(result.consumptionReason, 'passive_routed_native_entity_attack_return_not_damage_or_server_ack');
  assert.equal(result.damageConfirmed, false); assert.equal(result.serverEffectConfirmed, false);
});

test('passive consumption window accepts at most two new native ticks and rejects later arrivals or insufficient deltas', async () => {
  const second = observationFixture({editInput: (value, poll) => {value.nativeEntityAttackCount = poll === 1 ? 9 : 10;}});
  await observePerformanceAttackConsumption(second.sample, 3, second.services);
  assert.equal(second.polls(), 2); assert.equal(second.sample.nativeConsumptionObservation.newNativeTicks, 2);
  assert.equal(performanceAttackConsumption(second.sample, 3).actualAttackCountConfirmed, true);
  for (const options of [{insufficient: true}, {editInput: value => {value.clientTick += 2;}}, {editInput: value => {value.clientTick = 99;}},
    {readMillis: 480}, {frozenTick: true, insufficient: true}]) {
    const value = observationFixture(options); const observed = await observePerformanceAttackConsumption(value.sample, 3, value.services);
    assert.equal(performanceAttackConsumption(value.sample, 3).actualAttackCountConfirmed, false);
    assert.equal(performanceAttackConsumption(value.sample, 3).gameConsumption, 'not_confirmed');
    assert(observed.samples.length > 0); assert(value.polls() <= 25); assert.equal(observed.inputResubmitted, false);
    if (options.insufficient && !options.frozenTick) {assert.equal(value.polls(), 2); assert.equal(observed.newNativeTicks, 2);}
    if (options.readMillis) {assert.equal(observed.elapsedMillis, 500); assert.equal(observed.invalidated, true);}
    if (options.frozenTick) {assert.equal(observed.elapsedMillis, 500); assert.equal(observed.invalidated, true);}
  }
});

test('Lease expiry/replacement and native epoch/control-generation changes cannot confirm a late attack delta', async () => {
  for (const options of [
    {editLease: (value, poll) => {if (poll) value.leaseId = 'another-owner';}},
    {editLease: (value, poll) => {if (poll) {value.mode = 'READ'; value.status = 'available'; delete value.leaseId;}}},
    {editInput: value => {value.nativeEntityAttackLeaseActive = false;}},
    {editInput: value => {value.nativeEntityAttackContextId = 'new-context';}},
    {editInput: value => {value.nativeEntityAttackContextGeneration++;}},
    {editInput: value => {value.nativeEntityAttackControlGeneration++;}}
  ]) {
    const value = observationFixture(options); await observePerformanceAttackConsumption(value.sample, 3, value.services);
    const result = performanceAttackConsumption(value.sample, 3);
    assert.equal(result.actualAttackCountConfirmed, false); assert.equal(result.serverEffectConfirmed, false);
    assert.equal(value.polls(), 1); assert.equal(value.sample.nativeConsumptionObservation.newAttacksRequested, 0);
  }
});

test('attack plans use identical down/40ms hold/up pairs, not a faster batch substitute', () => {
  assert.deepEqual(performanceAttackSteps(3), Array.from({length: 3}, () => performanceAttackSteps(1)).flat());
  assert.equal(performanceAttackSteps(3).filter(step => step.type === 'delay').reduce((sum, step) => sum + step.durationMs, 0), 120);
  for (const count of [0, 2, 4, '3', NaN]) assert.throws(() => performanceAttackSteps(count));
});

test('actual dispatch counts and client ticks never pretend to prove three consumed attacks', () => {
  const result = performanceAttackTiming(timingSample(3), 3);
  assert.equal(result.dispatchedButtonCallbacks, 6); assert.equal(result.cleanupCalls, 1);
  assert.equal(result.nativeCallbackTickSpan, 0); assert.equal(result.gameConsumption, 'not_confirmed');
  assert.equal(result.swingCount, null); assert.equal(result.hit, 'not_confirmed'); assert.equal(result.serverEffect, 'not_confirmed');
  assert.equal(result.vanillaCooldown, 'unchanged');
});

test('only a matching active native context and exact counter delta confirms entity branch consumption, never damage', () => {
  for (const count of [1, 3]) {
    const result = performanceAttackTiming(nativeCounterSample(count), count);
    assert.equal(result.actualAttackCountConfirmed, true); assert.equal(result.nativeEntityAttackDelta, count);
    assert.equal(result.gameConsumption, 'confirmed_native_entity_attack_invocations');
    assert.equal(result.counterIsConservativeLowerBound, true); assert.equal(result.missingDeltaDoesNotProveNoAttack, true);
    assert.equal(result.damageConfirmed, false); assert.equal(result.serverEffectConfirmed, false);
    assert.equal(result.swingCount, null); assert.equal(result.serverEffect, 'not_confirmed');
  }
});

test('missing, reset, stale, inactive or ambiguous native counters remain not confirmed', () => {
  const edits = [
    sample => {delete sample.inputBefore.nativeEntityAttackCount;},
    sample => {sample.inputAfter.nativeEntityAttackCount = NaN;},
    sample => {sample.inputAfter.nativeEntityAttackCount = 6;},
    sample => {sample.inputAfter.nativeEntityAttackCount = 7;},
    sample => {sample.inputAfter.nativeEntityAttackCount = 9;},
    sample => {sample.inputAfter.nativeEntityAttackCount = 11;},
    sample => {sample.inputAfter.nativeEntityAttackContextId = 'new-world';},
    sample => {sample.inputAfter.nativeEntityAttackContextGeneration++;},
    sample => {sample.inputAfter.nativeEntityAttackControlGeneration++;},
    sample => {sample.inputBefore.nativeEntityAttackLeaseActive = false;},
    sample => {sample.inputAfter.nativeEntityAttackLeaseActive = false;},
    sample => {sample.inputAfter.nativeEntityAttackScope = 'input_callbacks';},
    sample => {sample.inputAfter.nativeEntityAttackCounterUnits = 'damage';},
    sample => {sample.inputAfter.nativeEntityAttackServerEffectConfirmed = true;},
    sample => {sample.inputAfter.nativeEntityAttackContextId = '';},
    sample => {delete sample.inputAfter.nativeEntityAttackControlGeneration;}
  ];
  for (const edit of edits) {
    const sample = nativeCounterSample(3); edit(sample); const result = performanceAttackConsumption(sample, 3);
    assert.equal(result.actualAttackCountConfirmed, false); assert.equal(result.gameConsumption, 'not_confirmed');
    assert.equal(result.damageConfirmed, false); assert.equal(result.missingDeltaDoesNotProveNoAttack, true);
  }
});

test('timing rejects incomplete, uncaptured, mismatched-hold or double-cleanup claims', () => {
  const edits = [
    sample => {sample.outcome.state = 'executing';},
    sample => {sample.outcome.state = 'failed';},
    sample => {sample.outcome.result.cleanupOnComplete = false;},
    sample => {sample.outcome.result.timing.stages.cleanup.calls = 2;},
    sample => {sample.outcome.result.timing.requestedDelayMillis = 0;},
    sample => {sample.steps[1].durationMs = 0;},
    sample => {sample.inputAfter.inputDispatchSequence++;},
    sample => {sample.inputAfter.pressedButtonCount = 1;},
    sample => {sample.outcome.result.steps[0].result.inputOrigin = 'NATIVE_HUMAN';},
    sample => {sample.outcome.result.steps[0].result.directMutationUsed = true;},
    sample => {sample.outcome.result.steps[0].result.hostCursorUsed = true;},
    sample => {sample.outcome.result.steps[0].result.clientTick = null;},
    sample => {sample.outcome.result.steps[2].result.inputDispatchSequence = 0;},
    sample => {sample.outcome.result.steps[0].elapsedMillis = -1;},
    sample => {sample.outcome.result.timing.totalMillis = NaN;},
    sample => {sample.observedTerminalMillis = Infinity;}
  ];
  for (const edit of edits) { const sample = timingSample(3); edit(sample); assert.throws(() => performanceAttackTiming(sample, 3)); }
});

test('1.5 target stays batch total/single p50 even when dividing by three would pass', () => {
  const singles = [timingSample(1, 80), timingSample(1, 100), timingSample(1, 120)];
  const missed = performanceAttackComparison(singles, timingSample(3, 180));
  assert.equal(missed.singleInternalMillis.p50, 100); assert.equal(missed.batchToSingleP50Ratio, 1.8);
  assert.equal(missed.diagnosticRatios.batchToThreeSinglesTotal, .6);
  assert.equal(missed.targetRatio, 1.5); assert.equal(missed.targetMet, false); assert.equal(missed.targetResult, 'NOT_MET');
  assert.equal(missed.diagnosticRatiosCannotReplaceTarget, true);
  assert.equal(missed.actualAttackCountConfirmed, false); assert.equal(missed.serverEffectConfirmed, false);
  const met = performanceAttackComparison(singles, timingSample(3, 150));
  assert.equal(met.targetMet, true); assert.equal(met.targetResult, 'MET');
  const consumed = performanceAttackComparison([nativeCounterSample(1, 80), nativeCounterSample(1, 100), nativeCounterSample(1, 120)], nativeCounterSample(3, 180));
  assert.equal(consumed.actualAttackCountConfirmed, true); assert.equal(consumed.targetMet, false);
  const missingSingle = nativeCounterSample(1, 100); missingSingle.inputAfter.nativeEntityAttackCount = 7;
  assert.equal(performanceAttackComparison([nativeCounterSample(1, 80), missingSingle, nativeCounterSample(1, 120)], nativeCounterSample(3, 150)).actualAttackCountConfirmed, false);
  assert.throws(() => performanceAttackComparison(singles.slice(1), timingSample(3)));
});

function nativeSummonPosition(tokens) {
  assert.equal(tokens.length, 3);
  return tokens.map((token, axis) => {
    assert.match(token, /^-?\d+(?:\.\d+)?$/);
    return Number(token) + (axis !== 1 && !token.includes('.') ? .5 : 0);
  });
}

test('native summon integer X/Z literals are centered, while explicit decimal coordinates remain exact', () => {
  assert.deepEqual(nativeSummonPosition(['0', '65', '-5']), [.5, 65, -4.5]);
  assert.deepEqual(nativeSummonPosition(['0.000', '65.000', '-5.000']), [0, 65, -5]);
  assert.deepEqual(nativeSummonPosition(['0.500', '65.000', '3.000']), [.5, 65, 3]);
});

function simulation(options = {}) {
  let milliseconds = 0, mode = 'READ', lease, sequence = 0, generation = 0, operationNumber = 0, acquires = 0, releases = 0, stops = 0, sceneReads = 0;
  let cow, nativeAttackCount = 0, nativeConsumedAttacks = 0, nativeAimCount = 0;
  let screen = 'TitleScreen'; const keys = new Set(), buttons = new Set(), operations = new Map(), fills = [], calls = [], samples = [], checks = [];
  const player = {x: .5, y: 64, z: .5, yaw: 0, pitch: 0, dimension: 'minecraft:overworld', gameMode: 'creative', onGround: true, flying: false};
  const advance = duration => {
    if (keys.has(87)) player.z += duration * .0043;
    if (keys.has(83)) player.z -= duration * .0043;
    milliseconds += duration;
  };
  const clock = {now: () => milliseconds, sleep: async duration => advance(duration)};
  const input = () => ({pressedKeyCount: keys.size, pressedButtonCount: buttons.size, pressedKeys: [...keys], pressedButtons: [...buttons],
    inputDispatchSequence: sequence, inputGeneration: generation, inputCleanupPending: false, inputCleanupFailed: false, clientTick: Math.floor(milliseconds / 50),
    ...(options.nativeCounter ? {nativeEntityAttackCount: nativeAttackCount, nativeEntityAttackScope: 'native_multiplayer_game_mode_attack_return',
      nativeEntityAttackCounterUnits: 'native_entity_attack_invocations_not_damage_or_swing', nativeEntityAttackContextId: 'native-context',
      nativeEntityAttackContextGeneration: 1, nativeEntityAttackControlGeneration: acquires,
      nativeEntityAttackLeaseActive: Boolean(lease), nativeEntityAttackServerEffectConfirmed: false} : {})});
  const status = () => ({mode, status: lease ? 'active' : 'available', leaseId: lease, reconsentRequired: false, modeVersion: {controlSessionId: 'owned', generation: 1}});
  function dispatch() {
    sequence++; advance(5);
    return {inputOrigin: 'AGENT_ROUTED', entryLayer: 'GAME_ROUTED_RAW', hostCursorUsed: false, directMutationUsed: false,
      inputDispatchSequence: sequence, clientTick: Math.floor(milliseconds / 50)};
  }
  function execute(operation) {
    const begin = milliseconds, results = [];
    for (const step of operation.body.steps) {
      const start = milliseconds; let result;
      if (step.type === 'key') { if (step.action) keys.add(step.key); else keys.delete(step.key); result = dispatch(); }
      else if (step.type === 'mouse.delta') { player.yaw += step.dx * .15; result = dispatch(); }
      else if (step.type === 'mouse.button') { if (step.action) buttons.add(step.button); else buttons.delete(step.button); result = dispatch(); }
      else if (step.type === 'delay') {
        advance(step.durationMs); result = {type: 'delay'};
        if (buttons.has(0) && cow && nativeAimCount) {nativeConsumedAttacks++; if (options.nativeCounter && !options.lateConsumption) nativeAttackCount++;}
      }
      else if (step.type === 'world.action') {
        assert.equal(step.action, 'aim'); assert.equal(step.category, 'USER'); assert.equal(step.targetRef, cow?.targetRef);
        nativeAimCount++; result = {onTarget: true, actualHit: {kind: 'entity', entityType: 'minecraft:cow', distance: 2.5}};
      }
      else assert.fail('Unexpected mock pipeline step: ' + step.type);
      results.push({stepType: step.type, elapsedMillis: milliseconds - start, result});
    }
    const cleanup = operation.body.cleanupOnComplete;
    if (cleanup) { keys.clear(); buttons.clear(); sequence++; advance(5); }
    if (options.syntheticSlowSingles && operation.body.steps.filter(step => step.type === 'mouse.button').length === 2) advance(100);
    return {operationId: operation.id, state: 'completed', status: 'completed', result: {cleanupOnComplete: cleanup, steps: results,
      timing: {scope: 'automation_pipeline_not_http_or_server_effect', timeSource: 'monotonic_elapsed', queueMillis: 0,
        totalMillis: milliseconds - begin, requestedDelayMillis: operation.body.steps.reduce((sum, step) => sum + (step.type === 'delay' ? step.durationMs : 0), 0),
        inputCallbackCount: results.filter(step => step.stepType !== 'delay').length,
        stages: {...(cleanup ? {cleanup: {calls: 1, elapsedMillis: 5}} : {})}}}};
  }
  const fixtureInput = {policy: 'immutable_baseline_restored', target: '26.3-neoforge', role: 'client', credentialsCopied: false,
    baselineSha256: 'a'.repeat(64), restoredSha256: 'a'.repeat(64)};
  const api = async (method, path, body) => {
    calls.push({method, path, body: body && structuredClone(body), at: milliseconds}); advance(5);
    if (method === 'GET' && path === '/v0/input/state') return input();
    if (method === 'GET' && ['/v0/control/mode', '/v0/control/status'].includes(path)) return status();
    if (method === 'GET' && path === '/v0/ui/tree') return {screenClass: screen, overlayIdentity: 0, presentation: {children: [{selected: true, count: 0}]}};
    if (method === 'GET' && path === '/v0/player?category=USER') return {...player, dataCategory: 'USER', clientTick: Math.floor(milliseconds / 50)};
    if (method === 'GET' && path.startsWith('/v0/world/scene?')) {
      sceneReads++;
      if (options.failScene) {
        if (options.foreignOwner) lease = 'another-owner';
        throw new Error('owned observation failure');
      }
      return {dataCategory: 'USER', authority: 'CLIENT_KNOWN', chunkLoadRequested: false, clientTick: Math.floor(milliseconds / 50), blocks: [], entities: []};
    }
    if (method === 'GET' && path.startsWith('/v0/world/entities?')) {
      assert.equal(new URL('http://test' + path).searchParams.get('category'), 'USER');
      return {dataCategory: 'USER', authority: 'CLIENT_KNOWN', chunkLoadRequested: false, entitiesTruncated: false, entities: cow ? [{...cow}] : []};
    }
    if (method === 'GET' && path.startsWith('/v0/world/block?')) {
      const query = new URL('http://test' + path).searchParams, x = Number(query.get('x')), y = Number(query.get('y')), z = Number(query.get('z'));
      const fill = fills.findLast(value => x >= value.x1 && x <= value.x2 && y >= value.y1 && y <= value.y2 && z >= value.z1 && z <= value.z2);
      return {available: true, block: fill?.block ?? 'minecraft:air', x, y, z};
    }
    if (method === 'POST' && path === '/v0/command/player') {
      assert.equal(mode, 'OPERATE'); assert.equal(lease, undefined); assert.equal(body.route, 'NATIVE');
      const parts = body.command.split(' ');
      if (parts[0] === 'fill') { const [x1, y1, z1, x2, y2, z2] = parts.slice(1, 7).map(Number); fills.push({x1, y1, z1, x2, y2, z2, block: parts[7]}); }
      else if (parts[0] === 'gamemode') player.gameMode = parts[1];
      else if (parts[0] === 'tp') { const [x, y, z, yaw, pitch] = parts.slice(2).map(Number); Object.assign(player, {x, y, z, yaw, pitch}); }
      else if (parts[0] === 'clear') assert.equal(body.command, 'clear @s');
      else if (parts[0] === 'kill') {assert.equal(body.command, 'kill @e[type=minecraft:cow,tag=mcp_continuous_performance_attack]'); cow = undefined;}
      else if (parts[0] === 'summon') {
        assert.equal(parts[1], 'minecraft:cow'); assert(body.command.includes('NoAI:1b,NoGravity:1b,Invulnerable:1b'));
        assert(body.command.includes('Tags:["mcp_continuous_performance_attack"]'));
        const [x, y, z] = nativeSummonPosition(parts.slice(2, 5)); cow = {type: 'minecraft:cow', targetRef: 'world:attack-cow', x, y, z};
      }
      else assert.fail('Unexpected fixture command');
      return {route: 'NATIVE', inputDispatched: false, effectConfirmed: false};
    }
    if (method === 'POST' && path === '/v0/pipelines') {
      assert.equal(mode, 'TAKEOVER'); assert(lease); const id = 'operation-' + ++operationNumber;
      operations.set(id, {id, body: structuredClone(body), waits: 0}); return {operationId: id, state: 'executing', status: 'running'};
    }
    if (method === 'POST' && path.startsWith('/v0/operations/') && path.endsWith('/wait')) {
      const operation = operations.get(path.split('/')[3]); assert(operation); operation.waits++;
      if (options.nonterminalFirst && operation.waits === 1) return {state: 'executing', status: 'running'};
      return operation.result ??= execute(operation);
    }
    if (method === 'POST' && path === '/v0/input/release-all') {
      assert.equal(mode, 'TAKEOVER'); assert(lease); stops++; generation++; sequence++; keys.clear(); buttons.clear();
      if (options.failStop) throw new Error('stop response lost');
      return {type: 'input.release_all', inputGeneration: generation};
    }
    if (method === 'POST' && path === '/v0/wait/until') {
      assert.deepEqual(body.condition, {type: 'input.released'}); assert.equal(keys.size + buttons.size, 0);
      return {passed: true, condition: body.condition, evidence: {pressedKeyCount: 0, pressedButtonCount: 0}};
    }
    assert.fail('Unexpected mock API: ' + method + ' ' + path);
  };
  const environment = {api, target: '26.3-neoforge', fixtureInput, actionSamples: samples,
    mode: async value => {assert(!lease); mode = value;},
    acquire: async () => {assert(!lease); acquires++; lease = 'own-' + acquires; mode = 'TAKEOVER';},
    release: async () => {assert(lease?.startsWith('own-')); releases++; lease = undefined; mode = 'READ'; keys.clear(); buttons.clear(); sequence++; generation++;},
    forgetExpiredLease: () => {assert(!lease); assert.equal(mode, 'READ');},
    click: async translation => {if (translation === 'menu.singleplayer') screen = 'SelectWorldScreen'; else if (translation === 'selectWorld.select') screen = ''; else assert.fail('Unexpected GUI button');},
    clickNode: async node => assert.equal(node.worldName, 'World-Read-Alpha'),
    wait: async predicate => {const tree = {screenClass: screen}; assert(predicate(tree)); return tree;},
    nodes: () => [{worldName: 'World-Read-Alpha', nodeId: 'fixed-copy'}],
    check: async (name, run) => {await run(); checks.push(name);}
  };
  return {environment, clock, calls, samples, checks, operations,
    state: () => ({milliseconds, mode, lease, acquires, releases, stops, keys: [...keys], buttons: [...buttons], sceneReads,
      nativeConsumedAttacks, nativeAttackCount, nativeAimCount, cow})};
}

test('synthetic full flow preserves 30 seconds of real-shaped calls, strict counts and a final strong stop', async () => {
  const fixture = simulation({nonterminalFirst: true});
  const result = await continuousPerformanceActions(fixture.environment, fixture.clock);
  assert.equal(result.evidenceKind, 'synthetic_test_not_runtime'); assert.equal(result.liveEvidence, false);
  assert.equal(result.performanceComplete, false); assert.equal(result.comparisonTargetResult, 'NOT_MET');
  assert.equal(fixture.checks.length, 5); assert.equal(result.holdMillis, 30000);
  const movement = fixture.samples.find(sample => sample.case === 'continuous_performance_movement');
  assert(movement.observedMillis >= 30000); assert(movement.observations.length >= 15); assert(movement.clientObservedTravelDistance > 1);
  assert(movement.observations.some(sample => sample.direction === 87)); assert(movement.observations.some(sample => sample.direction === 83));
  assert.equal(fixture.samples.find(sample => sample.case === 'continuous_performance_fixture').verifiedWalls.length, 8);
  const posts = fixture.calls.filter(call => call.path === '/v0/pipelines');
  for (let index = 1; index < posts.length; index++) assert(posts[index].at - posts[index - 1].at >= 550);
  assert([...fixture.operations.values()].every(operation => operation.waits >= 2), 'A nonterminal wait must not become a PASS or input replay');
  assert.equal(posts.length, fixture.operations.size);
  assert.equal(fixture.state().mode, 'READ'); assert.equal(fixture.state().lease, undefined);
  assert.deepEqual(fixture.state().keys, []); assert.deepEqual(fixture.state().buttons, []);
  assert.equal(fixture.state().stops, 2); assert.equal(fixture.state().releases, 3);
  const attack = fixture.samples.find(sample => sample.case === 'continuous_performance_attack_fixture');
  assert.equal(attack.entity.type, 'minecraft:cow'); assert.equal(attack.aim.outcome.result.steps[0].result.onTarget, true);
  const summon = fixture.calls.find(call => call.body?.command?.startsWith('summon minecraft:cow ')).body.command.split(' ');
  for (const token of summon.slice(2, 5)) assert.match(token, /^-?\d+\.\d{3}$/);
  const arena = fixture.samples.find(sample => sample.case === 'continuous_performance_fixture').arena;
  assert.equal(attack.entity.x, arena.x); assert.equal(attack.entity.y, arena.y); assert.equal(attack.entity.z, arena.z + 2.5);
  assert(attack.aim.outcome.result.steps[0].result.actualHit.distance <= 3);
  assert(attack.elapsedMillis > 0); assert.equal(attack.nativeCooldownChanged, false); assert.equal(attack.invulnerableIsFixtureOnly, true);
  assert.equal(fixture.state().nativeAimCount, 1); assert.equal(fixture.state().nativeConsumedAttacks, 6); assert.equal(fixture.state().cow, undefined);
  assert(fixture.samples.some(sample => sample.case === 'continuous_performance_attack_fixture_cleanup'));
});

test('a regression to integer summon tokens fails exact fixture discovery instead of widening reach or skipping aim', async () => {
  const fixture = simulation({nativeCounter: true}), request = fixture.environment.api;
  fixture.environment.api = (method, path, body) => {
    if (body?.command?.startsWith('summon minecraft:cow ')) {
      const parts = body.command.split(' ');
      for (let axis = 2; axis < 5; axis++) parts[axis] = String(Number(parts[axis]));
      body = {...body, command: parts.join(' ')};
    }
    return request(method, path, body);
  };
  await assert.rejects(continuousPerformanceActions(fixture.environment, fixture.clock), /Unique client-known attack fixture was not received/);
  assert.equal(fixture.state().nativeAimCount, 0); assert.equal(fixture.state().nativeConsumedAttacks, 0);
  assert.equal(fixture.state().cow, undefined); assert.equal(fixture.state().mode, 'READ'); assert.equal(fixture.state().lease, undefined);
  assert(!fixture.samples.some(sample => sample.case === 'continuous_performance_complete'));
});

test('mock native counters confirm all six entity attack branch invocations without claiming damage or Live proof', async () => {
  const fixture = simulation({nativeCounter: true, syntheticSlowSingles: true});
  const result = await continuousPerformanceActions(fixture.environment, fixture.clock);
  assert.equal(result.attackConsumptionConfirmed, true); assert.equal(result.performanceComplete, true);
  assert.equal(result.liveEvidence, false); assert.equal(result.damageConfirmed, false); assert.equal(result.serverEffectConfirmed, false);
  const singles = fixture.samples.filter(sample => sample.case === 'continuous_performance_attack_single');
  assert.deepEqual(singles.map(sample => sample.details.nativeEntityAttackDelta), [1, 1, 1]);
  const comparison = fixture.samples.find(sample => sample.case === 'continuous_performance_attack_comparison');
  assert.equal(comparison.comparison.batchDetails.nativeEntityAttackDelta, 3);
  for (const sample of [...singles, comparison.batch]) {
    assert(sample.terminalInputSnapshot); assert.equal(sample.nativeConsumptionObservation.inputResubmitted, false);
    assert.equal(sample.nativeConsumptionObservation.newAttacksRequested, 0);
    assert.equal(sample.nativeConsumptionObservation.includedInPipelineOrObservedTerminalTiming, false);
    assert.equal(sample.nativeConsumptionObservation.newNativeTicks, 0);
    assert.equal(sample.outcome.result.timing.requestedDelayMillis, sample.steps.length === 3 ? 40 : 120);
  }
  const movement = fixture.samples.find(sample => sample.case === 'continuous_performance_movement');
  assert(movement.observations.every(value => !value.inputSample.nativeConsumptionObservation && !value.inputSample.terminalInputSnapshot),
    'Pure movement pipelines do not acquire the attack observer or its waits');
  assert.equal(fixture.state().nativeConsumedAttacks, 6); assert.equal(fixture.state().nativeAttackCount, 6);
});

test('missing native deltas after the bounded observer remain unconfirmed rather than reported as no attacks', async () => {
  const fixture = simulation({nativeCounter: true, lateConsumption: true, syntheticSlowSingles: true});
  const result = await continuousPerformanceActions(fixture.environment, fixture.clock);
  assert.equal(result.comparisonTargetResult, 'MET'); assert.equal(result.attackConsumptionConfirmed, false); assert.equal(result.performanceComplete, false);
  assert.equal(fixture.state().nativeConsumedAttacks, 6); assert.equal(fixture.state().nativeAttackCount, 0);
  const sample = fixture.samples.find(sample => sample.case === 'continuous_performance_attack_single');
  assert.equal(sample.details.nativeEntityAttackDelta, 0); assert.equal(sample.details.gameConsumption, 'not_confirmed');
  assert.equal(sample.details.missingDeltaDoesNotProveNoAttack, true);
  assert.equal(sample.details.consumptionReason, 'native_attack_count_not_confirmed_possible_late_consumption');
  assert.equal(sample.nativeConsumptionObservation.reason, 'native_attack_count_not_confirmed_within_two_native_ticks');
  assert(sample.nativeConsumptionObservation.newNativeTicks <= 2); assert(sample.nativeConsumptionObservation.elapsedMillis <= 500);
  for (const operation of fixture.operations.values()) for (const step of operation.body.steps.filter(value => value.type === 'delay')) assert.equal(step.durationMs, 40);
});

test('a synthetic timing target alone cannot certify three actual Minecraft attacks', async () => {
  const fixture = simulation({syntheticSlowSingles: true});
  const result = await continuousPerformanceActions(fixture.environment, fixture.clock);
  assert.equal(result.comparisonTargetResult, 'MET');
  assert.equal(result.attackConsumptionConfirmed, false);
  assert.equal(result.performanceComplete, false);
  assert.equal(result.liveEvidence, false);
});

test('missing, copied-credential or mismatched fixture receipts cause no input or world mutation', async () => {
  for (const edit of [env => {env.fixtureInput = undefined;}, env => {env.fixtureInput.credentialsCopied = true;},
    env => {env.fixtureInput.target = 'other-target';}, env => {env.fixtureInput.restoredSha256 = 'b'.repeat(64);}]) {
    const fixture = simulation(); edit(fixture.environment);
    await assert.rejects(continuousPerformanceActions(fixture.environment, fixture.clock));
    assert.equal(fixture.state().acquires, 0); assert.equal(fixture.calls.length, 0);
  }
});

test('invalid hold duration is rejected before any instance operation', async () => {
  for (const holdMillis of [0, 29999, 60001, NaN, '30000']) {
    const fixture = simulation(); fixture.environment.holdMillis = holdMillis;
    await assert.rejects(continuousPerformanceActions(fixture.environment, fixture.clock)); assert.equal(fixture.calls.length, 0);
  }
});

test('observation failure stops and releases only the owned Lease, preserving the original failure', async () => {
  const fixture = simulation({failScene: true});
  await assert.rejects(continuousPerformanceActions(fixture.environment, fixture.clock), /owned observation failure/);
  assert.equal(fixture.state().sceneReads, 1); assert.equal(fixture.state().stops, 1);
  assert.equal(fixture.state().lease, undefined); assert.equal(fixture.state().mode, 'READ');
  assert.deepEqual(fixture.state().keys, []); assert.deepEqual(fixture.state().buttons, []);
});

test('uncertain strong-stop response is not blindly replayed and Lease cleanup is still attempted', async () => {
  const fixture = simulation({failStop: true});
  await assert.rejects(continuousPerformanceActions(fixture.environment, fixture.clock), /stop response lost/);
  assert.equal(fixture.state().stops, 1); assert.equal(fixture.state().releases, 2);
  assert.equal(fixture.state().lease, undefined); assert.deepEqual(fixture.state().keys, []);
});

test('cleanup never stops or releases another owner after an external Lease replacement', async () => {
  const fixture = simulation({failScene: true, foreignOwner: true});
  const priorError = console.error; const reported = []; console.error = error => reported.push(error);
  try { await assert.rejects(continuousPerformanceActions(fixture.environment, fixture.clock), /owned observation failure/); }
  finally { console.error = priorError; }
  assert.equal(fixture.state().lease, 'another-owner'); assert.equal(fixture.state().stops, 0);
  assert.equal(fixture.state().releases, 1); assert.equal(reported.length, 1);
  assert(fixture.samples.some(sample => sample.case === 'continuous_performance_lease_cleanup_failure'));
});
