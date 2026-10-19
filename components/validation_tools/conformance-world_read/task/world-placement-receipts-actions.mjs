import assert from 'node:assert/strict';
import {isDeepStrictEqual} from 'node:util';

const RELEASED = {type: 'input.released'};
const TERMINAL = new Set(['completed', 'failed', 'cancelled']);

// These are finite Vanilla test fixtures, not a product-side placement compatibility table.
export const placementReceiptCases = Object.freeze([
  {id: 'door_blocked', kind: 'door', item: 'minecraft:oak_door', count: 16, blocked: true},
  {id: 'door_unblocked', kind: 'door', item: 'minecraft:oak_door', count: 16, blocked: false},
  {id: 'bed_blocked', kind: 'bed', item: 'minecraft:red_bed', count: 1, blocked: true},
  {id: 'bed_unblocked', kind: 'bed', item: 'minecraft:red_bed', count: 1, blocked: false},
  {id: 'double_plant_blocked', kind: 'double_plant', item: 'minecraft:rose_bush', count: 16, blocked: true},
  {id: 'double_plant_unblocked', kind: 'double_plant', item: 'minecraft:rose_bush', count: 16, blocked: false},
  {id: 'slab_same_id_merge', kind: 'slab', item: 'minecraft:oak_slab', count: 16, blocked: false},
  {id: 'interactive_consumed_without_placement', kind: 'note_block', item: 'minecraft:stone', count: 16, blocked: false}
]);

export function stateFromUserPoint(point) {
  assert.equal(point.available, true); assert.equal(point.dataCategory, 'USER'); assert.equal(point.authority, 'CLIENT_KNOWN');
  assert.equal(point.chunkLoadRequested, false); assert.equal(point.storageAccessed, false);
  const match = /^Block\{([a-z0-9_.-]+:[a-z0-9/._-]+)\}(?:\[([^\]]*)\])?$/.exec(point.state);
  assert(match, 'Expected the existing native BlockState text, not invented DEBUG properties'); assert.equal(match[1], point.block);
  const properties = {};
  if (match[2]) for (const part of match[2].split(',')) {
    const pair = /^([a-z0-9_]+)=([a-z0-9_.:/-]+)$/.exec(part); assert(pair); assert(!(pair[1] in properties)); properties[pair[1]] = pair[2];
  }
  return {block: point.block, properties};
}

function expectedPlacementState(state, specification, companion = false) {
  assert.equal(state.block, specification.item);
  let properties;
  if (specification.kind === 'door') {
    assert(['left', 'right'].includes(state.properties.hinge));
    properties = {facing: 'east', half: companion ? 'upper' : 'lower', hinge: state.properties.hinge, open: 'false', powered: 'false'};
  } else if (specification.kind === 'bed') properties = {facing: 'east', occupied: 'false', part: companion ? 'head' : 'foot'};
  else if (specification.kind === 'double_plant') properties = {half: companion ? 'upper' : 'lower'};
  else {assert.equal(specification.kind, 'slab'); properties = {type: 'double', waterlogged: 'false'};}
  assert.deepEqual(state, {block: specification.item, properties}, 'Complete state must match the finite Vanilla fixture');
}

export function validatePlacementCompanion(specification, destination, companion) {
  assert(!specification.blocked); assert(['door', 'bed', 'double_plant'].includes(specification.kind));
  expectedPlacementState(destination, specification); expectedPlacementState(companion, specification, true);
  if (specification.kind === 'door') assert.deepEqual(companion, {...destination, properties: {...destination.properties, half: 'upper'}});
  if (specification.kind === 'bed') assert.deepEqual(companion, {...destination, properties: {...destination.properties, part: 'head'}});
}

export function validatePlacementReceipt(operation, specification, beforeDestination, afterDestination) {
  assert.equal(operation.state, 'completed', 'Only the input lifecycle may be called completed');
  assert.equal(operation.result.steps.length, 1); assert.equal(operation.result.steps[0].stepType, 'world.action');
  const result = operation.result.steps[0].result;
  assert.equal(result.type, 'world.action_result'); assert.equal(result.action, 'place'); assert.equal(result.domain, 'WORLD');
  assert.equal(result.dataCategory, 'USER'); assert.equal(result.evidenceAuthority, 'PLAYTEST'); assert.equal(result.entryLayer, 'GAME_ROUTED_RAW');
  assert.equal(result.directMutationUsed, false); assert.equal(result.effectConfirmed, false); assert.equal(result.serverValidationObserved, false);
  assert.equal(result.nativeInvocationObserved, true); assert.equal(result.nativeUseOnResultObserved, true); assert.equal(result.inputReleased, true);
  assert(['fail', 'pass', 'success', 'success_server', 'consume', 'other'].includes(result.nativeUseOnResult));
  for (const name of ['nativeConsumesAction', 'clientDestinationChanged', 'clientPlacementPredicted']) assert.equal(typeof result[name], 'boolean');
  const nativeBefore = result.placementState.before, nativeAfter = result.placementState.after;
  assert.deepEqual(nativeBefore, beforeDestination);
  assert.equal(result.clientDestinationChanged, !isDeepStrictEqual(nativeBefore, nativeAfter));
  if (specification.blocked) {
    assert.equal(result.nativeUseOnResult, 'fail'); assert.equal(result.nativeConsumesAction, false);
    assert.equal(result.clientDestinationChanged, false); assert.equal(result.clientPlacementPredicted, false);
    assert.equal(result.placementOutcome, 'native_rejected'); assert.equal(result.reason, 'native_use_on_failed');
    assert.deepEqual(nativeAfter, nativeBefore); assert.deepEqual(afterDestination, beforeDestination);
  } else if (specification.kind === 'note_block') {
    assert.notEqual(result.nativeUseOnResult, 'fail'); assert.equal(result.nativeConsumesAction, true);
    assert.equal(result.clientDestinationChanged, false); assert.equal(result.clientPlacementPredicted, false);
    assert.equal(result.placementOutcome, 'native_consumed_not_confirmed');
    assert.equal(result.reason, 'native_use_on_consumed_without_expected_destination_change');
    assert.deepEqual(nativeAfter, nativeBefore); assert.deepEqual(afterDestination, beforeDestination);
  } else {
    assert.notEqual(result.nativeUseOnResult, 'fail'); assert.equal(result.nativeConsumesAction, true);
    assert.equal(result.clientDestinationChanged, true); assert.equal(result.clientPlacementPredicted, true);
    assert.equal(result.placementOutcome, 'client_placement_predicted');
    assert.equal(result.reason, 'native_use_on_consumed_and_expected_destination_changed');
    // Native prediction and independent readback may straddle Vanilla server reconciliation.
    expectedPlacementState(nativeAfter, specification); expectedPlacementState(afterDestination, specification);
    if (specification.kind !== 'door') assert.deepEqual(nativeAfter, afterDestination);
  }
  return result;
}

export async function worldPlacementReceiptsActions({api, target, fixtureInput, mode, acquire, release, click, clickNode, wait, nodes,
  check, actionSamples, forgetExpiredLease}, testServices) {
  const now = testServices?.now ?? (() => performance.now()), sleep = testServices?.sleep ?? (millis => new Promise(done => setTimeout(done, millis)));
  const started = now(), deadline = started + 240000; let ownsLease = false, ownedLease, lastPost = -Infinity, primaryFailure, cleanupFailure, origin;
  const remaining = (max = 5000) => {const left = Math.floor(deadline - now()); assert(left > 0, 'Placement receipts exceeded 240 seconds'); return Math.min(max, left);};
  async function poll(read, predicate, label, max = 5000) {
    const end = now() + remaining(max); let value;
    do {value = await read(); if (predicate(value)) return value; await sleep(75);} while (now() < end);
    assert.fail(label + ': ' + JSON.stringify(value));
  }
  async function zero() {const value = await api('GET', '/v0/input/state'); assert.equal(value.pressedKeyCount, 0); assert.equal(value.pressedButtonCount, 0); return value;}
  async function released() {
    const value = await api('POST', '/v0/wait/until', {condition: RELEASED, timeoutMs: remaining()});
    assert.equal(value.passed, true); assert.deepEqual(value.evidence, {pressedKeyCount: 0, pressedButtonCount: 0}); return value;
  }
  async function take() {
    await acquire(); ownsLease = true; const state = await api('GET', '/v0/control/status');
    assert.equal(state.mode, 'TAKEOVER'); assert.equal(state.status, 'active'); assert.equal(typeof state.leaseId, 'string'); ownedLease = state.leaseId;
  }
  async function handBack() {
    if (!ownsLease) return;
    const state = await api('GET', '/v0/control/status');
    if (state.leaseId === ownedLease) {
      let failure;
      try {const response = await api('POST', '/v0/input/release-all', {}); assert.equal(response.type, 'input.release_all'); await released(); await zero();}
      catch (error) {failure = error;}
      try {await release();} catch (error) {failure ??= error;}
      if (failure) throw failure;
    } else {
      assert.equal(state.mode, 'READ'); assert.equal(state.status, 'available'); assert(!state.leaseId); await released(); await zero(); forgetExpiredLease();
    }
    ownsLease = false; ownedLease = undefined;
  }
  async function command(command) {
    const result = await api('POST', '/v0/command/player', {command, route: 'NATIVE'});
    assert.equal(result.route, 'NATIVE'); assert.equal(result.inputDispatched, false); assert.equal(result.effectConfirmed, false); return result;
  }
  const point = at => api('GET', '/v0/world/block?category=USER&' + new URLSearchParams(at));
  async function known(at, block) {
    return poll(() => point(at), value => value.available === true && value.block === block, 'Arranged USER client block was not received');
  }
  async function arrange(specification) {
    await handBack(); await mode('OPERATE'); const {x, y, z} = origin, receipts = [];
    const target = {x: x + 2, y, z}, destination = {x: x + 2, y: y + 1, z};
    const companion = specification.kind === 'bed' ? {x: x + 3, y: y + 1, z} : {x: x + 2, y: y + 2, z};
    const commands = [`fill ${x - 2} ${y + 1} ${z - 3} ${x + 5} ${y + 5} ${z + 3} minecraft:air`,
      `fill ${x - 2} ${y} ${z - 3} ${x + 5} ${y} ${z + 3} minecraft:stone`,
      `setblock ${x} ${y + 1} ${z} minecraft:stone`, 'gamemode creative @s'];
    if (specification.kind === 'double_plant') commands.push(`setblock ${target.x} ${target.y} ${target.z} minecraft:grass_block`);
    if (specification.blocked) commands.push(`setblock ${companion.x} ${companion.y} ${companion.z} minecraft:stone`);
    let face = 'up', hit = {u: .5, v: .5}, targetBlock = specification.kind === 'double_plant' ? 'minecraft:grass_block' : 'minecraft:stone';
    if (specification.kind === 'slab') {
      commands.push(`setblock ${destination.x} ${destination.y} ${destination.z} minecraft:oak_slab[type=bottom,waterlogged=false]`,
        `setblock ${x + 3} ${y + 1} ${z} minecraft:stone`);
      Object.assign(target, {x: x + 3, y: y + 1}); face = 'west'; hit = {u: .5, v: .75};
    }
    if (specification.kind === 'note_block') {
      commands.push(`setblock ${target.x} ${target.y} ${target.z} minecraft:note_block[note=0,powered=false]`); targetBlock = 'minecraft:note_block';
    }
    commands.push(`item replace entity @s weapon.mainhand with ${specification.item} ${specification.count}`, `tp @s ${x + .5} ${y + 2} ${z + .5} -90 0`);
    for (const text of commands) receipts.push(await command(text));
    const actor = await poll(() => api('GET', '/v0/player?category=USER'), value => value.available && value.onGround
      && Math.abs(value.x - x - .5) < .02 && Math.abs(value.y - y - 2) < .02 && Math.abs(value.z - z - .5) < .02
      && Math.abs(value.yaw + 90) < .1, 'Raised diagnostic player position was not received');
    const supportBefore = await known(target, targetBlock), destinationBefore = await known(destination, specification.kind === 'slab' ? specification.item : 'minecraft:air');
    let companionBefore;
    if (['door', 'bed', 'double_plant'].includes(specification.kind)) companionBefore = await known(companion, specification.blocked ? 'minecraft:stone' : 'minecraft:air');
    if (specification.kind === 'slab') assert.equal(stateFromUserPoint(destinationBefore).properties.type, 'bottom');
    if (specification.kind === 'note_block') assert.equal(stateFromUserPoint(supportBefore).properties.note, '0');
    await take();
    const heldItem = await wait(tree => !tree.screenClass && nodes(tree.presentation ?? {}).some(value => value.selected === true && value.item === specification.item && value.count === specification.count),
      'Native held item HUD update for ' + specification.id, remaining(10000));
    return {target, destination, companion, face, hit, receipts, actor, supportBefore, destinationBefore, companionBefore, heldItem};
  }
  async function act(specification, fixture) {
    const discovered = await poll(() => point(fixture.target), value => value.available && typeof value.targetRef === 'string', 'Fresh semantic block target unavailable');
    const beforeInput = await zero(); let pause = 550 - (now() - lastPost);
    while (pause > 0) {assert(pause < remaining()); await sleep(Math.ceil(pause)); pause = 550 - (now() - lastPost);}
    const step = {type: 'world.action', category: 'USER', targetRef: discovered.targetRef, action: 'place', face: fixture.face, hit: fixture.hit,
      expectedDestinationBlockId: fixture.destinationBefore.block};
    lastPost = now(); const accepted = await api('POST', '/v0/pipelines', {steps: [step], timeoutMs: 15000}); assert.equal(typeof accepted.operationId, 'string');
    const operation = await poll(() => api('POST', `/v0/operations/${accepted.operationId}/wait`, {timeoutMs: remaining(1000)}), value => {
      if (!TERMINAL.has(value.state)) {assert.equal(value.status, 'running'); return false;}
      assert.equal(value.state, 'completed', JSON.stringify(value)); return true;
    }, 'Placement input operation did not complete', 20000);
    const expectedDestination = specification.blocked || specification.kind === 'note_block' ? 'minecraft:air' : specification.item;
    const afterDestination = await known(fixture.destination, expectedDestination);
    const result = validatePlacementReceipt(operation, specification, stateFromUserPoint(fixture.destinationBefore), stateFromUserPoint(afterDestination));
    assert.equal(result.targetRef, discovered.targetRef); assert.equal(result.onTarget, true);
    assert.equal(result.actualHit.kind, 'block'); assert.equal(result.actualHit.face, fixture.face); assert.deepEqual(result.actualHit.block, fixture.target);
    const companionAfter = fixture.companionBefore ? await known(fixture.companion, specification.blocked ? 'minecraft:stone' : specification.item) : undefined;
    if (!specification.blocked && ['door', 'bed', 'double_plant'].includes(specification.kind))
      validatePlacementCompanion(specification, stateFromUserPoint(afterDestination), stateFromUserPoint(companionAfter));
    if (specification.kind === 'slab') {
      assert.equal(stateFromUserPoint(afterDestination).properties.type, 'double'); assert.equal(result.placementState.before.block, result.placementState.after.block);
    }
    let interactionAfter;
    if (specification.kind === 'note_block') {
      interactionAfter = await poll(() => point(fixture.target), value => value.available && stateFromUserPoint(value).properties.note === '1', 'Native note interaction was not received');
      assert.equal((await api('GET', '/v0/ui/tree')).screenClass, '', 'Interactive fixture must not open a GUI');
    }
    const waited = await released(), afterInput = await zero(); assert(afterInput.inputDispatchSequence > beforeInput.inputDispatchSequence);
    return {case: 'world_placement_receipt', id: specification.id, specification, fixture, discovered, step, accepted, operation, result,
      afterDestination, companionAfter, interactionAfter, beforeInput, afterInput, waited,
      arrangeAuthority: 'diagnostic_current_player_NATIVE_commands', actAuthority: 'PLAYTEST', assertAuthority: 'CLIENT_KNOWN_USER',
      actUsesSemanticTarget: true, pixelInputUsed: false, directMutationUsedByAct: false,
      operationCompletedMeans: 'input_lifecycle_only', serverValidationObserved: false};
  }
  try {
    assert.equal(fixtureInput?.policy, 'immutable_baseline_restored'); assert.equal(fixtureInput.target, target); assert.equal(fixtureInput.role, 'client');
    assert.equal(fixtureInput.credentialsCopied, false); assert.match(fixtureInput.baselineSha256, /^[0-9a-f]{64}$/); assert.equal(fixtureInput.restoredSha256, fixtureInput.baselineSha256);
    await mode('READ'); await zero(); const title = await api('GET', '/v0/ui/tree'); assert(title.screenClass.endsWith('TitleScreen')); assert(!title.overlayIdentity);
    await take(); await click('menu.singleplayer', 'Singleplayer'); const list = await wait(value => value.screenClass.endsWith('SelectWorldScreen'), 'placement owned world list', remaining(20000));
    const row = nodes(list).find(value => value.worldName === 'World-Read-Alpha'); assert(row); await clickNode(row); await click('selectWorld.select', 'Play Selected World');
    await wait(value => !value.screenClass, 'placement restored world loaded', remaining(60000));
    const actor = await api('GET', '/v0/player?category=USER'); assert.equal(actor.available, true);
    origin = {x: Math.floor(actor.x), y: Math.floor(actor.y), z: Math.floor(actor.z)};
    actionSamples.push({case: 'world_placement_fixture', fixtureInput, worldName: row.worldName, origin});
    for (const specification of placementReceiptCases) await check('native placement receipt ' + specification.id, async () => {
      const fixture = await arrange(specification), sample = await act(specification, fixture); actionSamples.push(sample);
    });
  } catch (error) {primaryFailure = error; throw error;}
  finally {
    try {await handBack();} catch (error) {cleanupFailure = error;}
    if (cleanupFailure) {actionSamples.push({case: 'world_placement_cleanup_failure', error: String(cleanupFailure)}); if (!primaryFailure) throw cleanupFailure;}
  }
  const finalInput = await zero(), finalMode = await api('GET', '/v0/control/status'); assert.equal(finalMode.mode, 'READ'); assert.equal(finalMode.status, 'available'); assert(!finalMode.leaseId);
  assert(now() - started <= 240000);
  const result = {case: 'world_placement_complete', target, checks: placementReceiptCases.length, tested: placementReceiptCases.map(value => value.id),
    evidenceKind: testServices ? 'synthetic_test_not_runtime' : 'runtime_monotonic', liveEvidence: !testServices,
    elapsedMillis: now() - started, maximumMillis: 240000, finalInput, finalMode, physicalEscTested: false, serverValidationObserved: false,
    clientShutdownOwnedByOuterRunner: true, persistentWriteCalls: 0}; actionSamples.push(result); return result;
}
