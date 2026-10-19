import assert from 'node:assert/strict';
import test from 'node:test';
import {placementReceiptCases, stateFromUserPoint, validatePlacementReceipt, validatePlacementCompanion, worldPlacementReceiptsActions} from './world-placement-receipts-actions.mjs';

const AIR = {block: 'minecraft:air', properties: {}};
const typed = (block, properties = {}) => ({block, properties});
const point = state => ({available: true, dataCategory: 'USER', authority: 'CLIENT_KNOWN', chunkLoadRequested: false, storageAccessed: false,
  block: state.block, state: `Block{${state.block}}` + (Object.keys(state.properties).length ? '[' + Object.entries(state.properties).map(([key, value]) => key + '=' + value).join(',') + ']' : '')});

function receipt(specification, before, after) {
  const consumed = !specification.blocked, predicted = consumed && specification.kind !== 'note_block';
  return {operationId: 'test-op', state: 'completed', status: 'completed', result: {steps: [{stepType: 'world.action', result: {
    type: 'world.action_result', action: 'place', domain: 'WORLD', dataCategory: 'USER', evidenceAuthority: 'PLAYTEST', entryLayer: 'GAME_ROUTED_RAW',
    directMutationUsed: false, effectConfirmed: false, serverValidationObserved: false, nativeInvocationObserved: true, nativeUseOnResultObserved: true,
    inputReleased: true, nativeUseOnResult: specification.blocked ? 'fail' : 'success', nativeConsumesAction: consumed,
    clientDestinationChanged: predicted, clientPlacementPredicted: predicted,
    placementOutcome: specification.blocked ? 'native_rejected' : predicted ? 'client_placement_predicted' : 'native_consumed_not_confirmed',
    reason: specification.blocked ? 'native_use_on_failed' : predicted ? 'native_use_on_consumed_and_expected_destination_changed' : 'native_use_on_consumed_without_expected_destination_change',
    placementState: {before: structuredClone(before), after: structuredClone(after)}
  }}]}};
}

test('existing USER native state text yields only block/properties, without requesting DEBUG', () => {
  assert.deepEqual(stateFromUserPoint(point(typed('minecraft:oak_slab', {type: 'bottom', waterlogged: 'false'}))), typed('minecraft:oak_slab', {type: 'bottom', waterlogged: 'false'}));
  for (const edit of [value => {value.available = false;}, value => {value.dataCategory = 'DEBUG';}, value => {value.chunkLoadRequested = true;},
    value => {value.storageAccessed = true;}, value => {value.state = 'not-a-native-state';}, value => {value.block = 'minecraft:dirt';},
    value => {value.state = 'Block{minecraft:air}[type=top,type=bottom]';}]) {
    const value = point(AIR); edit(value); assert.throws(() => stateFromUserPoint(value));
  }
});

test('completed input plus consumed action is not itself successful placement', () => {
  const blocked = placementReceiptCases.find(value => value.id === 'door_blocked'), interactive = placementReceiptCases.at(-1);
  const rejected = validatePlacementReceipt(receipt(blocked, AIR, AIR), blocked, AIR, AIR);
  assert.equal(rejected.placementOutcome, 'native_rejected'); assert.equal(rejected.clientPlacementPredicted, false);
  const consumed = validatePlacementReceipt(receipt(interactive, AIR, AIR), interactive, AIR, AIR);
  assert.equal(consumed.nativeConsumesAction, true); assert.equal(consumed.clientPlacementPredicted, false);
  assert.equal(consumed.placementOutcome, 'native_consumed_not_confirmed');
});

test('same block ID with changed native properties can be a real client placement prediction', () => {
  const specification = placementReceiptCases.find(value => value.kind === 'slab'), before = typed(specification.item, {type: 'bottom', waterlogged: 'false'}),
    after = typed(specification.item, {type: 'double', waterlogged: 'false'});
  const result = validatePlacementReceipt(receipt(specification, before, after), specification, before, after);
  assert.equal(result.clientDestinationChanged, true); assert.equal(result.clientPlacementPredicted, true);
  assert.equal(result.placementState.before.block, result.placementState.after.block);
});

test('immediate prediction and later complete USER state are separate observation boundaries', () => {
  const specification = placementReceiptCases.find(value => value.id === 'door_unblocked');
  const predicted = typed(specification.item, {facing: 'east', half: 'lower', hinge: 'right', open: 'false', powered: 'false'});
  const observed = typed(specification.item, {...predicted.properties, hinge: 'left'});
  const operation = receipt(specification, AIR, predicted), original = structuredClone(operation);
  const result = validatePlacementReceipt(operation, specification, AIR, observed);
  assert.deepEqual(operation, original); assert.deepEqual(result.placementState.after, predicted);
  assert.equal(result.clientDestinationChanged, true); assert.equal(result.effectConfirmed, false);
  assert.equal(result.serverValidationObserved, false); assert.equal(observed.properties.hinge, 'left');
});

test('reconciliation never excuses missing properties, wrong native state or invented confirmation', () => {
  const specification = placementReceiptCases.find(value => value.id === 'door_unblocked');
  const state = typed(specification.item, {facing: 'east', half: 'lower', hinge: 'left', open: 'false', powered: 'false'});
  for (const edit of [value => {delete value.properties.hinge;}, value => {value.properties.extra = 'false';},
    value => {value.properties.half = 'upper';}, value => {value.properties.facing = 'west';}, value => {value.properties.hinge = 'middle';},
    value => {value.properties.open = 'true';}, value => {value.properties.powered = 'true';}, value => {value.block = 'minecraft:air';}]) {
    const invalid = structuredClone(state); edit(invalid);
    assert.throws(() => validatePlacementReceipt(receipt(specification, AIR, invalid), specification, AIR, state));
    assert.throws(() => validatePlacementReceipt(receipt(specification, AIR, state), specification, AIR, invalid));
  }
  validatePlacementCompanion(specification, state, {...state, properties: {...state.properties, half: 'upper'}});
  assert.throws(() => validatePlacementCompanion(specification, state, {...state, properties: {...state.properties, half: 'upper', hinge: 'right'}}));
  const bed = placementReceiptCases.find(value => value.id === 'bed_unblocked');
  const foot = typed(bed.item, {facing: 'east', occupied: 'false', part: 'foot'});
  validatePlacementCompanion(bed, foot, {...foot, properties: {...foot.properties, part: 'head'}});
  assert.throws(() => validatePlacementCompanion(bed, foot, {...foot, properties: {...foot.properties, part: 'head', occupied: 'true'}}));
});

test('missing native result, false-positive consumption/placement and invented server success are rejected', () => {
  const specification = placementReceiptCases.find(value => value.id === 'door_unblocked'), after = typed(specification.item, {facing: 'east', half: 'lower', hinge: 'left', open: 'false', powered: 'false'});
  for (const edit of [value => {value.state = 'executing';}, value => {value.result.steps[0].result.nativeUseOnResultObserved = false;},
    value => {value.result.steps[0].result.nativeInvocationObserved = false;}, value => {value.result.steps[0].result.serverValidationObserved = true;},
    value => {value.result.steps[0].result.effectConfirmed = true;}, value => {value.result.steps[0].result.directMutationUsed = true;},
    value => {value.result.steps[0].result.nativeConsumesAction = false;}, value => {value.result.steps[0].result.clientDestinationChanged = false;},
    value => {value.result.steps[0].result.nativeUseOnResult = 'fail';}, value => {value.result.steps[0].result.placementState.after = AIR;},
    value => {value.result.steps[0].result.placementOutcome = 'server_placement_success';}, value => {value.result.steps[0].result.evidenceAuthority = 'FIXTURE';}]) {
    const value = receipt(specification, AIR, after); edit(value); assert.throws(() => validatePlacementReceipt(value, specification, AIR, after));
  }
  const blocked = placementReceiptCases[0], lying = receipt(blocked, AIR, AIR); lying.result.steps[0].result.clientPlacementPredicted = true;
  assert.throws(() => validatePlacementReceipt(lying, blocked, AIR, AIR));
});

function simulation(options = {}) {
  let milliseconds = 0, mode = 'READ', lease, localLease, sequence = 0, heldItem = '', heldCount = 0, screen = 'TitleScreen', operationNumber = 0, targetNumber = 0;
  let releases = 0, stops = 0; const world = new Map(), targets = new Map(), operations = new Map(), calls = [], samples = [], checks = [];
  const player = {available: true, dataCategory: 'USER', authority: 'CLIENT_KNOWN', x: .5, y: 65, z: .5, yaw: -90, pitch: 0, onGround: true};
  const keyOf = at => `${at.x}:${at.y}:${at.z}`, read = at => structuredClone(world.get(keyOf(at)) ?? AIR), write = (at, state) => world.set(keyOf(at), structuredClone(state));
  function parseState(text) {
    const match = /^([^[]+)(?:\[(.*)\])?$/.exec(text); assert(match);
    return typed(match[1], Object.fromEntries((match[2] ?? '').split(',').filter(Boolean).map(value => value.split('='))));
  }
  const input = () => ({pressedKeyCount: 0, pressedButtonCount: 0, inputDispatchSequence: sequence, inputGeneration: stops,
    inputCleanupPending: false, inputCleanupFailed: false});
  const status = () => ({mode, status: lease ? 'active' : 'available', ...(lease ? {leaseId: lease} : {}), reconsentRequired: false});
  function execute(operation) {
    const step = operation.body.steps[0], target = targets.get(step.targetRef); assert(target);
    const destination = step.face === 'west' ? {...target, x: target.x - 1} : {...target, y: target.y + 1};
    const before = read(destination); assert.equal(step.expectedDestinationBlockId, before.block);
    let kind, blocked = false;
    if (heldItem === 'minecraft:oak_door') {
      kind = 'door'; const upper = {...destination, y: destination.y + 1}; blocked = read(upper).block !== 'minecraft:air';
      if (!blocked) {
        write(destination, typed(heldItem, {half: 'lower', facing: 'east', open: 'false', hinge: 'left', powered: 'false'}));
        if (!options.missingSecondHalf) write(upper, typed(heldItem, {half: 'upper', facing: 'east', open: 'false', hinge: 'left', powered: 'false'}));
      }
    } else if (heldItem === 'minecraft:red_bed') {
      kind = 'bed'; const head = {...destination, x: destination.x + 1}; blocked = read(head).block !== 'minecraft:air';
      if (!blocked) {write(destination, typed(heldItem, {part: 'foot', facing: 'east', occupied: 'false'})); write(head, typed(heldItem, {part: 'head', facing: 'east', occupied: 'false'}));}
    } else if (heldItem === 'minecraft:rose_bush') {
      kind = 'double_plant'; const upper = {...destination, y: destination.y + 1}; blocked = read(upper).block !== 'minecraft:air';
      assert.equal(read(target).block, 'minecraft:grass_block');
      if (!blocked) {write(destination, typed(heldItem, {half: 'lower'})); write(upper, typed(heldItem, {half: 'upper'}));}
    } else if (heldItem === 'minecraft:oak_slab') {
      kind = 'slab'; assert.equal(step.face, 'west'); assert.equal(step.hit.v, .75); assert.equal(before.properties.type, 'bottom');
      write(destination, typed(heldItem, {type: 'double', waterlogged: 'false'}));
    } else {
      kind = 'note_block'; assert.equal(heldItem, 'minecraft:stone'); assert.equal(read(target).block, 'minecraft:note_block');
      const old = read(target); write(target, {...old, properties: {...old.properties, note: '1'}});
    }
    const specification = {kind, item: heldItem, blocked}, outcome = receipt(specification, before, read(destination));
    if (options.reconciledDoorHinge && kind === 'door' && !blocked) outcome.result.steps[0].result.placementState.after.properties.hinge = 'right';
    if (options.mismatchedDoorHalf && kind === 'door' && !blocked) {
      const upper = {...destination, y: destination.y + 1}, old = read(upper);
      write(upper, {...old, properties: {...old.properties, hinge: 'right'}});
    }
    Object.assign(outcome, {operationId: operation.id});
    Object.assign(outcome.result.steps[0].result, {targetRef: step.targetRef, onTarget: true, actualHit: {kind: 'block', face: step.face, block: target}});
    sequence += 3;
    if (options.failOperation) return operation.outcome = {operationId: operation.id, state: 'failed', status: 'failed', errorCode: 'WORLD_TARGET_NOT_HIT'};
    if (options.falsePlacementForBlocked && blocked) outcome.result.steps[0].result.clientPlacementPredicted = true;
    if (options.forgeServerSuccess) outcome.result.steps[0].result.serverValidationObserved = true;
    return operation.outcome = outcome;
  }
  const api = async (method, path, body) => {
    calls.push({method, path, body: body && structuredClone(body), at: milliseconds}); milliseconds += 5;
    if (method === 'GET' && path === '/v0/input/state') return input();
    if (method === 'GET' && ['/v0/control/status', '/v0/control/mode'].includes(path)) return status();
    if (method === 'GET' && path === '/v0/player?category=USER') return {...player};
    if (method === 'GET' && path === '/v0/ui/tree') return {screenClass: screen, overlayIdentity: 0};
    if (method === 'GET' && path.startsWith('/v0/world/block?')) {
      const query = new URL('http://owned' + path).searchParams, at = {x: Number(query.get('x')), y: Number(query.get('y')), z: Number(query.get('z'))};
      assert.equal(query.get('category'), 'USER'); const targetRef = 'world:point-' + ++targetNumber; targets.set(targetRef, at);
      return {...point(read(at)), ...at, targetRef};
    }
    if (method === 'POST' && path === '/v0/command/player') {
      assert.equal(mode, 'OPERATE'); assert(!lease); assert.equal(body.route, 'NATIVE'); const parts = body.command.split(' ');
      if (parts[0] === 'fill') {
        const [x0, y0, z0, x1, y1, z1] = parts.slice(1, 7).map(Number);
        for (let x = x0; x <= x1; x++) for (let y = y0; y <= y1; y++) for (let z = z0; z <= z1; z++) write({x, y, z}, parseState(parts[7]));
      } else if (parts[0] === 'setblock') {const [x, y, z] = parts.slice(1, 4).map(Number); write({x, y, z}, parseState(parts[4]));}
      else if (parts[0] === 'item') {
        const count = Number(parts[7]); assert.equal(count, parts[6] === 'minecraft:red_bed' ? 1 : 16);
        if (!(options.staleDoorHudForBed && parts[6] === 'minecraft:red_bed')) {heldItem = parts[6]; heldCount = count;}
      }
      else if (parts[0] === 'tp') {const [x, y, z, yaw, pitch] = parts.slice(2).map(Number); Object.assign(player, {x, y, z, yaw, pitch});}
      else assert.equal(parts[0], 'gamemode');
      return {route: 'NATIVE', inputDispatched: false, effectConfirmed: false};
    }
    if (method === 'POST' && path === '/v0/pipelines') {
      assert.equal(mode, 'TAKEOVER'); assert(lease); assert.equal(body.steps.length, 1); assert.equal(body.steps[0].type, 'world.action');
      assert.equal(body.steps[0].action, 'place'); assert(targets.has(body.steps[0].targetRef)); const id = 'place-op-' + ++operationNumber;
      operations.set(id, {id, body: structuredClone(body), waits: 0}); return {operationId: id, state: 'executing', status: 'running'};
    }
    if (method === 'POST' && path.startsWith('/v0/operations/') && path.endsWith('/wait')) {
      const operation = operations.get(path.split('/')[3]); assert(operation); operation.waits++;
      if (operation.outcome) return operation.outcome;
      if (options.nonterminalFirst && operation.waits === 1) return {operationId: operation.id, state: 'executing', status: 'running'};
      return execute(operation);
    }
    if (method === 'POST' && path === '/v0/wait/until') return {passed: true, condition: body.condition, evidence: {pressedKeyCount: 0, pressedButtonCount: 0}};
    if (method === 'POST' && path === '/v0/input/release-all') {assert.equal(lease, localLease); sequence++; stops++; return {type: 'input.release_all', inputGeneration: stops};}
    assert.fail('Unexpected fake route ' + method + ' ' + path);
  };
  const env = {api, target: '26.3-neoforge', fixtureInput: {policy: 'immutable_baseline_restored', target: '26.3-neoforge', role: 'client', credentialsCopied: false,
    baselineSha256: 'a'.repeat(64), restoredSha256: 'a'.repeat(64)}, mode: async value => {assert(!lease); mode = value;},
    acquire: async () => {assert(!lease); mode = 'TAKEOVER'; lease = localLease = 'own-lease-' + releases;},
    release: async () => {assert.equal(lease, localLease); releases++; mode = 'READ'; lease = localLease = undefined;},
    click: async name => {if (name === 'menu.singleplayer') screen = 'SelectWorldScreen'; else if (name === 'selectWorld.select') screen = ''; else assert.fail('Unknown semantic button');},
    clickNode: async node => assert.equal(node.worldName, 'World-Read-Alpha'), nodes: tree => (tree.children ?? []).flatMap(value => [value, ...(value.children ?? [])]),
    wait: async (predicate, description) => {
      const tree = {screenClass: screen, children: [{worldName: 'World-Read-Alpha', nodeId: 'owned-world'}],
        presentation: {children: [{selected: true, item: heldItem, count: heldCount}]}}; assert(predicate(tree), description); return tree;
    }, check: async (name, callback) => {await callback(); checks.push(name);}, actionSamples: samples,
    forgetExpiredLease: () => {assert(!lease); assert.equal(mode, 'READ');}};
  return {env, services: {now: () => milliseconds, sleep: async duration => {milliseconds += duration;}}, calls, samples, checks, operations,
    counters: () => ({releases, stops, lease, mode})};
}

test('all eight synthetic native receipt cases use semantic USER Act, independent points and clean input', async () => {
  const fixture = simulation({nonterminalFirst: true}), complete = await worldPlacementReceiptsActions(fixture.env, fixture.services);
  assert.equal(complete.liveEvidence, false); assert.equal(complete.evidenceKind, 'synthetic_test_not_runtime'); assert.equal(complete.checks, 8);
  assert.deepEqual(complete.tested, placementReceiptCases.map(value => value.id)); assert.equal(fixture.checks.length, 8);
  const cases = fixture.samples.filter(value => value.case === 'world_placement_receipt'); assert.equal(cases.length, 8);
  assert.equal(cases.filter(value => value.specification.blocked).length, 3);
  const slab = cases.find(value => value.specification.kind === 'slab'); assert.equal(slab.result.clientDestinationChanged, true);
  assert.equal(slab.result.placementState.before.block, slab.result.placementState.after.block); assert.equal(slab.step.face, 'west');
  const note = cases.find(value => value.specification.kind === 'note_block'); assert.equal(note.result.nativeConsumesAction, true);
  assert.equal(note.result.clientPlacementPredicted, false); assert.equal(stateFromUserPoint(note.interactionAfter).properties.note, '1');
  for (const value of cases) {assert.equal(value.pixelInputUsed, false); assert.equal(value.actUsesSemanticTarget, true); assert.equal(value.serverValidationObserved, false);}
  const posts = fixture.calls.filter(value => value.path === '/v0/pipelines'); assert.equal(posts.length, 8);
  for (let index = 1; index < posts.length; index++) assert(posts[index].at - posts[index - 1].at >= 550);
  assert([...fixture.operations.values()].every(value => value.waits >= 2));
  assert.equal(fixture.counters().releases, 9); assert.equal(fixture.counters().stops, 9); assert.equal(fixture.counters().lease, undefined);
  assert.equal(complete.finalInput.pressedKeyCount, 0); assert.equal(complete.finalInput.pressedButtonCount, 0); assert.equal(complete.persistentWriteCalls, 0);
  assert(!fixture.calls.some(value => value.path.startsWith('/v0/debug')));
  const items = fixture.calls.filter(value => value.path === '/v0/command/player' && value.body.command.startsWith('item replace'));
  assert.equal(items.length, 8);
  for (let index = 0; index < items.length; index++) {
    const specification = placementReceiptCases[index];
    assert.equal(items[index].body.command, `item replace entity @s weapon.mainhand with ${specification.item} ${specification.count}`);
    assert.equal(specification.count, specification.kind === 'bed' ? 1 : 16);
  }
});

test('a rejected or unreceived bed fixture cannot use stale door HUD as confirmation or dispatch a bed action', async () => {
  const fixture = simulation({staleDoorHudForBed: true});
  await assert.rejects(worldPlacementReceiptsActions(fixture.env, fixture.services), /Native held item HUD update for bed_blocked/);
  assert.equal(fixture.checks.length, 2); assert.equal(fixture.calls.filter(value => value.path === '/v0/pipelines').length, 2);
  assert(fixture.calls.some(value => value.body?.command === 'item replace entity @s weapon.mainhand with minecraft:red_bed 1'));
  assert.equal(fixture.counters().lease, undefined); assert.equal(fixture.counters().mode, 'READ');
});

test('complete prediction and reconciled readback survive all eight cases without replay or rewriting', async () => {
  const fixture = simulation({reconciledDoorHinge: true}), complete = await worldPlacementReceiptsActions(fixture.env, fixture.services);
  assert.equal(complete.checks, 8); assert.equal(fixture.calls.filter(value => value.path === '/v0/pipelines').length, 8);
  const door = fixture.samples.find(value => value.id === 'door_unblocked');
  assert.equal(door.operation.result.steps[0].result.placementState.after.properties.hinge, 'right');
  assert.equal(stateFromUserPoint(door.afterDestination).properties.hinge, 'left');
  assert.equal(stateFromUserPoint(door.companionAfter).properties.hinge, 'left');
  assert.equal(door.serverValidationObserved, false); assert.equal(fixture.counters().lease, undefined);
});

test('independent paired-half inconsistency cannot hide behind prediction reconciliation', async () => {
  const fixture = simulation({mismatchedDoorHalf: true});
  await assert.rejects(worldPlacementReceiptsActions(fixture.env, fixture.services));
  assert.equal(fixture.checks.length, 1); assert.equal(fixture.calls.filter(value => value.path === '/v0/pipelines').length, 2);
  assert.equal(fixture.counters().lease, undefined);
});

test('missing fixed-copy identity never starts the instance or changes its state', async () => {
  const fixture = simulation(); fixture.env.fixtureInput.credentialsCopied = true;
  await assert.rejects(worldPlacementReceiptsActions(fixture.env, fixture.services)); assert.equal(fixture.calls.length, 0);
});

test('blocked placement false-positive receipt cannot pass merely because the operation completed', async () => {
  const fixture = simulation({falsePlacementForBlocked: true}); await assert.rejects(worldPlacementReceiptsActions(fixture.env, fixture.services));
  assert.equal(fixture.calls.filter(value => value.path === '/v0/pipelines').length, 1); assert.equal(fixture.checks.length, 0);
  assert.equal(fixture.counters().lease, undefined);
});

test('a missing paired upper block fails independent observation even when the lower prediction is correct', async () => {
  const fixture = simulation({missingSecondHalf: true}); await assert.rejects(worldPlacementReceiptsActions(fixture.env, fixture.services), /Arranged USER client block/);
  assert.equal(fixture.checks.length, 1); assert.equal(fixture.calls.filter(value => value.path === '/v0/pipelines').length, 2);
  assert.equal(fixture.counters().lease, undefined);
});

test('failed input operation is propagated and never replayed as a second placement', async () => {
  const fixture = simulation({failOperation: true}); await assert.rejects(worldPlacementReceiptsActions(fixture.env, fixture.services), /WORLD_TARGET_NOT_HIT/);
  assert.equal(fixture.calls.filter(value => value.path === '/v0/pipelines').length, 1); assert.equal(fixture.counters().lease, undefined);
});

test('invented server confirmation is rejected even with correct native blocks', async () => {
  const fixture = simulation({forgeServerSuccess: true}); await assert.rejects(worldPlacementReceiptsActions(fixture.env, fixture.services));
  assert.equal(fixture.checks.length, 0); assert.equal(fixture.counters().lease, undefined);
});
