import assert from 'node:assert/strict';
import test from 'node:test';
import {continuousFunctionalActions, validateFunctionalAttack} from './continuous-functional-actions.mjs';
import {continuousFunctionalProof} from '../../regression-v1_release/task/alpha-evidence.mjs';

function simulation(options = {}) {
  let milliseconds = 0, mode = 'READ', lease, leaseIndex = 0, sequence = 0, generation = 1, operationIndex = 0;
  let screen = 'TitleScreen', cow, nativeCount = 4, floor = 64, screenIdentity = 1, attacks = 0, stopped = 0, readDenied = 0;
  const keys = new Set(), buttons = new Set(), calls = [], actionSamples = [], checks = [], operations = new Map();
  const player = {x: .5, y: 64, z: .5, yaw: 0, pitch: 0, dimension: 'minecraft:overworld', onGround: true, flying: false, gameMode: 'creative'};
  const status = () => ({mode, status: lease ? 'active' : 'available', ...(lease ? {leaseId: lease} : {})});
  const input = () => ({pressedKeyCount: keys.size, pressedButtonCount: buttons.size, pressedKeys: [...keys], pressedButtons: [...buttons],
    inputDispatchSequence: sequence, inputGeneration: generation, inputCleanupPending: false, inputCleanupFailed: false,
    clientTick: Math.floor(milliseconds / 50), nativeEntityAttackCount: nativeCount,
    nativeEntityAttackScope: 'native_multiplayer_game_mode_attack_return',
    nativeEntityAttackCounterUnits: 'native_entity_attack_invocations_not_damage_or_swing',
    nativeEntityAttackContextId: 'own-world-context', nativeEntityAttackContextGeneration: 1,
    nativeEntityAttackControlGeneration: leaseIndex, nativeEntityAttackLeaseActive: !!lease,
    nativeEntityAttackServerEffectConfirmed: false});
  function tree() {
    return {screenClass: screen, overlayIdentity: 0, screenIdentity, menuIdentity: 17, screenRevision: 1,
      children: screen === 'SelectWorldScreen' ? [{worldName: 'World-Read-Alpha', nodeId: 'own-restored-world'}]
        : screen.includes('InventoryScreen') ? [0, 1].map(slot => ({role: 'slot', slotOwner: 'player_inventory', slot, nodeId: 'slot-' + slot,
          visible: true, active: true, x: slot * 18, y: 0, width: 16, height: 16})) : [],
      presentation: {children: [{role: 'hud_item', selected: true, count: 0, item: 'minecraft:air'}]}};
  }
  function execute(operation) {
    if (operation.outcome) return operation.outcome;
    const results = [];
    for (const step of operation.body.steps) {
      let result = {inputOrigin: 'AGENT_ROUTED', entryLayer: 'GAME_ROUTED_RAW', hostCursorUsed: false, directMutationUsed: false,
        clientTick: Math.floor(milliseconds / 50)};
      if (step.type === 'mouse.button') {
        sequence++; if (step.action === 1) buttons.add(step.button); else buttons.delete(step.button);
      } else if (step.type === 'delay') {
        milliseconds += step.durationMs;
        if (buttons.has(0)) {attacks++; if (!options.missingConsumption) nativeCount++;}
        result = {type: 'delay'};
      } else if (step.type === 'world.action') {
        sequence++; assert.equal(step.category, 'USER'); assert.equal(step.targetRef, cow.targetRef);
        result = {onTarget: true, actualHit: {kind: 'entity', entityType: 'minecraft:cow', distance: 2.5}};
      } else if (step.type === 'mouse.delta') {sequence++; player.yaw += step.dx;}
      else if (step.type === 'key.tap') {
        sequence += 2;
        if (step.key === 69) {screen = 'InventoryScreen'; screenIdentity++;}
        if (step.key === 256) {screen = ''; screenIdentity++;}
      } else if (step.type === 'ui.action') {
        sequence++; const slot = Number(step.selector.nodeId.slice(5));
        result = {action: 'hover', targetingSource: 'interaction_tree', resolvedNode: {nodeId: step.selector.nodeId, slot}};
        if (options.badHover) result.resolvedNode.slot = 0;
      } else assert.fail('Unexpected functional mock step ' + step.type);
      result.inputDispatchSequence = sequence;
      results.push({stepType: step.type, elapsedMillis: 1, result});
    }
    keys.clear(); buttons.clear(); sequence++;
    const result = {operationId: operation.id, state: 'completed', result: {cleanupOnComplete: true, steps: results,
      timing: {scope: 'automation_pipeline_not_http_or_server_effect', stages: {cleanup: {calls: options.doubleCleanup ? 2 : 1}},
        totalMillis: 999999, requestedDelayMillis: operation.body.steps.filter(value => value.type === 'delay').reduce((sum, value) => sum + value.durationMs, 0)}}};
    return operation.outcome = result;
  }
  const api = async (method, path, body, expectedError) => {
    milliseconds += 5; calls.push({method, path, body: structuredClone(body)});
    if (method === 'GET' && path === '/v0/input/state') {
      const value = input();
      if (options.changedContext && attacks > 0) value.nativeEntityAttackContextId = 'new-context';
      if (options.pendingAfterCancel && [...operations.values()].some(value => value.outcome?.state === 'cancelled')) value.inputCleanupPending = true;
      return value;
    }
    if (method === 'GET' && ['/v0/control/status', '/v0/control/mode'].includes(path)) return status();
    if (method === 'GET' && path.startsWith('/v0/ui/tree')) return tree();
    if (method === 'GET' && path === '/v0/player?category=USER') return {...player, dataCategory: 'USER', available: true};
    if (method === 'GET' && path.startsWith('/v0/world/entities?')) return {dataCategory: 'USER', authority: 'CLIENT_KNOWN',
      chunkLoadRequested: false, entitiesTruncated: false, entities: cow ? [structuredClone(cow)] : []};
    if (method === 'POST' && path === '/v0/pipelines') {
      if (mode === 'READ') {assert.equal(expectedError, 'TAKEOVER_REQUIRED'); readDenied++; return {error: 'TAKEOVER_REQUIRED'};}
      assert.equal(mode, 'TAKEOVER'); assert(lease);
      const id = 'operation-' + ++operationIndex, operation = {id, body: structuredClone(body)}; operations.set(id, operation);
      if (body.steps[0].type === 'key') {keys.add(body.steps[0].key); sequence++; operation.active = true;}
      return {operationId: id};
    }
    if (method === 'POST' && /\/v0\/operations\/[^/]+\/wait$/.test(path)) {
      const operation = operations.get(path.split('/')[3]); assert(operation);
      assert(!operation.active || operation.outcome, 'Cancel fixture must not execute its queued delta');
      return execute(operation);
    }
    if (method === 'DELETE' && path.startsWith('/v0/operations/')) {
      const operation = operations.get(path.split('/')[3]); assert(operation.active); keys.clear(); sequence++; generation++;
      operation.outcome = {operationId: operation.id, state: 'cancelled'}; return {operationId: operation.id, cancellationRequested: true};
    }
    if (method === 'POST' && path === '/v0/input/release-all') {
      stopped++; keys.clear(); buttons.clear(); sequence++; generation++; return {type: 'input.release_all', inputGeneration: generation};
    }
    if (method === 'POST' && path === '/v0/wait/until') {
      assert.deepEqual(body.condition, {type: 'input.released'}); assert.equal(keys.size + buttons.size, 0);
      return {passed: true, evidence: {pressedKeyCount: 0, pressedButtonCount: 0}};
    }
    if (method === 'POST' && path === '/v0/command/player') {
      assert.equal(mode, 'OPERATE'); assert(!lease); assert.equal(body.route, 'NATIVE');
      const parts = body.command.split(' ');
      if (parts[0] === 'gamemode') player.gameMode = parts[1];
      else if (parts[0] === 'tp') {const [x, y, z, yaw, pitch] = parts.slice(2).map(Number); Object.assign(player, {x, y, z, yaw, pitch});}
      else if (parts[0] === 'fill') floor = Number(parts[2]);
      else if (parts[0] === 'clear') assert.equal(body.command, 'clear @s');
      else if (parts[0] === 'summon') {
        const tag = /Tags:\["([a-z0-9_]+)"\]/.exec(body.command)?.[1]; assert.match(tag, /^mcp_cf_[a-f0-9]{24}$/);
        for (const token of parts.slice(2, 5)) assert.match(token, /^-?\d+\.\d{3}$/);
        const [x, y, z] = parts.slice(2, 5).map(Number); cow = {type: 'minecraft:cow', x, y, z, targetRef: 'own-cow', tag};
      } else if (parts[0] === 'kill') {
        assert(cow); assert(body.command.includes('tag=' + cow.tag + ','));
        assert(body.command.includes(`x=${cow.x},y=${cow.y},z=${cow.z},distance=..0.1,limit=1]`)); cow = undefined;
      } else assert.fail('Unexpected native fixture command ' + body.command);
      return {route: 'NATIVE', inputDispatched: false, effectConfirmed: false};
    }
    assert.fail('Unexpected functional API ' + method + ' ' + path);
  };
  const ctx = {api, target: '26.3-fabric', fixtureInput: {policy: 'immutable_baseline_restored', target: '26.3-fabric', role: 'client',
    credentialsCopied: false, baselineSha256: 'a'.repeat(64), restoredSha256: 'a'.repeat(64)}, actionSamples,
    async mode(value) {assert(!lease); mode = value;},
    async acquire() {assert(!lease); lease = 'own-' + ++leaseIndex; mode = 'TAKEOVER';},
    async release() {assert(lease?.startsWith('own-')); lease = undefined; mode = 'READ'; keys.clear(); buttons.clear(); generation++; sequence++;},
    forgetExpiredLease() {assert(!lease);},
    async click(translation) {if (translation === 'menu.singleplayer') screen = 'SelectWorldScreen'; else if (translation === 'selectWorld.select') screen = ''; else assert.fail(translation);},
    async clickNode(node) {assert.equal(node.worldName, 'World-Read-Alpha');},
    async wait(predicate) {const value = tree(); assert(predicate(value), JSON.stringify(value)); return value;},
    nodes(value) {return [value, ...(value.children ?? []).flatMap(child => ctx.nodes(child))];},
    async check(name, run) {await run(); checks.push(name);}};
  return {ctx, services: {now: () => milliseconds, sleep: async duration => {milliseconds += duration;}}, calls, actionSamples, checks, operations,
    state: () => ({mode, lease, sequence, keys: [...keys], buttons: [...buttons], attacks, stopped, readDenied, cow, floor})};
}

test('functional-only flow dispatches exactly one single and one triple, three single inputs, two semantic hovers and cancellation', async () => {
  const fixture = simulation(), result = await continuousFunctionalActions(fixture.ctx, fixture.services);
  assert.equal(result.scope, 'functional_only'); assert.equal(result.performance.status, 'DEFERRED_BY_USER_2026_10_20');
  assert.equal(result.performance.newMeasurementsPerformed, false); assert.equal(result.liveEvidence, false);
  assert.equal(fixture.checks.length, 9); assert.deepEqual(result.attackPairs, [1, 3]);
  const attacks = fixture.actionSamples.filter(value => value.case === 'continuous_functional_attack');
  assert.deepEqual(attacks.map(value => value.sample.validation.nativeEntityAttackDelta), [1, 3]);
  assert(attacks.every(value => value.sample.outcome.result.timing.totalMillis === 999999), 'Runtime timing remains raw and cannot affect functional validity');
  assert.deepEqual(fixture.actionSamples.filter(value => value.case === 'continuous_functional_single_input').map(value => value.name), ['mouse_delta', 'delay_100', 'shift_40']);
  const hover = fixture.actionSamples.find(value => value.case === 'continuous_functional_gui_hover');
  assert.deepEqual(hover.anchors, [0, 1]); assert.deepEqual(hover.samples.map(value => value.sample.steps[0].selector.nodeId), ['slot-0', 'slot-1']);
  assert(fixture.actionSamples.find(value => value.case === 'continuous_functional_cancel').noLateInput);
  assert.equal(fixture.state().attacks, 4); assert.equal(fixture.state().readDenied, 1); assert.equal(fixture.state().stopped, 1);
  assert.equal(fixture.state().cow, undefined); assert.equal(fixture.state().mode, 'READ'); assert.equal(fixture.state().lease, undefined);
  assert.deepEqual(fixture.state().keys, []); assert.deepEqual(fixture.state().buttons, []);
  assert.equal(result.persistentWriteCalls, 0); assert.equal(result.serverEffectConfirmed, false);
  const forbidden = /^(?:ratio|p50|p95|postRttMillis|waitRttMillis|observedTerminalMillis|statistics|comparison)$/;
  const inspect = value => {for (const [key, child] of Object.entries(value ?? {})) {assert(!forbidden.test(key), key); if (child && typeof child === 'object') inspect(child);}};
  inspect(fixture.actionSamples);
});

test('missing consumption, replaced native context or double cleanup cannot become functional PASS', async () => {
  for (const options of [{missingConsumption: true}, {changedContext: true}, {doubleCleanup: true}]) {
    const fixture = simulation(options); await assert.rejects(continuousFunctionalActions(fixture.ctx, fixture.services));
    assert(!fixture.actionSamples.some(value => value.case === 'continuous_functional_complete'));
    assert.equal(fixture.state().mode, 'READ'); assert.equal(fixture.state().lease, undefined); assert.equal(fixture.state().cow, undefined);
    assert.deepEqual(fixture.state().keys, []); assert.deepEqual(fixture.state().buttons, []);
  }
});

test('functional publication refuses synthetic, missing, stale-context and falsely measured samples', async () => {
  const fixture=simulation();await continuousFunctionalActions(fixture.ctx,fixture.services);
  const result={target:'26.3-fabric',scenario:'continuous-functional',status:'PASS',evidence:'development_runtime_native',fullSmoke:false,fullWorldMilestone:false,cleanShutdown:true,sourceChangedDuringRun:false,persistentWriteCalls:0,fixturePolicy:'immutable_baseline_restored',fixtureInput:fixture.ctx.fixtureInput,checks:fixture.checks.map(name=>({name,status:'PASS'})),actionSamples:fixture.actionSamples,acceptanceScope:'functional_only',performance:{status:'DEFERRED_BY_USER_2026_10_20',newMeasurements:false}};
  assert.throws(()=>continuousFunctionalProof(result,'26.3-fabric'));
  const end=result.actionSamples.find(row=>row.case==='continuous_functional_complete');end.liveEvidence=true;end.evidenceKind='runtime_functional';
  const proof=continuousFunctionalProof(result,'26.3-fabric');assert.equal(proof.performanceAcceptance,'DEFERRED_BY_USER_2026_10_20');assert.equal(proof.guiSamples,2);assert(!Object.hasOwn(proof,'batchToSingleP50Ratio'));
  for(const change of [r=>{r.target='26.3-neoforge';},r=>{r.performance.newMeasurements=true;},r=>{r.cleanShutdown=false;},r=>{r.sourceChangedDuringRun=true;},r=>{r.persistentWriteCalls=1;},r=>{r.actionSamples=r.actionSamples.filter(row=>row.case!=='continuous_functional_cancel');},r=>{r.actionSamples.find(row=>row.case==='continuous_functional_attack').sample.inputAfter.nativeEntityAttackControlGeneration++;},r=>{r.actionSamples.find(row=>row.case==='continuous_functional_gui_hover').samples[1].slot=0;}]){
    const changed=structuredClone(result);change(changed);assert.throws(()=>continuousFunctionalProof(changed,'26.3-fabric'));
  }
});

test('two different semantic targets and a completed cleanup barrier are required', async () => {
  for (const options of [{badHover: true}, {pendingAfterCancel: true}]) {
    const fixture = simulation(options); await assert.rejects(continuousFunctionalActions(fixture.ctx, fixture.services));
    assert(!fixture.actionSamples.some(value => value.case === 'continuous_functional_complete'));
    assert.equal(fixture.state().lease, undefined); assert.equal(fixture.state().cow, undefined);
  }
});

test('unsupported Target and unsafe copy receipts are refused before any instance request', async () => {
  for (const edit of [ctx => {ctx.target = '26.3-neoforge';}, ctx => {ctx.fixtureInput.credentialsCopied = true;},
    ctx => {ctx.fixtureInput.restoredSha256 = 'b'.repeat(64);}, ctx => {ctx.fixtureInput = undefined;}]) {
    const fixture = simulation(); edit(fixture.ctx); await assert.rejects(continuousFunctionalActions(fixture.ctx, fixture.services)); assert.equal(fixture.calls.length, 0);
  }
});

test('attack validator rejects altered holds, attribution, native count and release state without measuring duration', async () => {
  const fixture = simulation(); await continuousFunctionalActions(fixture.ctx, fixture.services);
  const original = fixture.actionSamples.find(value => value.case === 'continuous_functional_attack' && value.pairs === 3).sample;
  for (const edit of [value => {value.steps[1].durationMs = 0;}, value => {value.inputAfter.nativeEntityAttackCount--;},
    value => {value.inputAfter.nativeEntityAttackControlGeneration++;}, value => {value.inputAfter.nativeEntityAttackLeaseActive = false;},
    value => {value.inputAfter.pressedButtonCount = 1;}, value => {value.outcome.result.steps[0].result.hostCursorUsed = true;},
    value => {value.outcome.result.steps[0].result.inputOrigin = 'NATIVE_HUMAN';}, value => {value.outcome.result.timing.stages.cleanup.calls = 2;}]) {
    const value = structuredClone(original); edit(value); assert.throws(() => validateFunctionalAttack(value, 3));
  }
  const unrelatedDuration = structuredClone(original); delete unrelatedDuration.outcome.result.timing.totalMillis;
  assert.equal(validateFunctionalAttack(unrelatedDuration, 3).actualAttackCountConfirmed, true);
});

test('each run owns a different bounded cow selector and does not kill a pre-existing fixture by a shared tag', async () => {
  const tags = [];
  for (let index = 0; index < 2; index++) {
    const fixture = simulation(); await continuousFunctionalActions(fixture.ctx, fixture.services);
    const cleanup = fixture.calls.filter(value => value.body?.command?.startsWith('kill ')); assert.equal(cleanup.length, 1);
    const tag = /tag=([a-z0-9_]+),/.exec(cleanup[0].body.command)?.[1]; assert.match(tag, /^mcp_cf_[a-f0-9]{24}$/); tags.push(tag);
    assert(cleanup[0].body.command.includes('distance=..0.1,limit=1]'));
  }
  assert.notEqual(tags[0], tags[1]);
});
