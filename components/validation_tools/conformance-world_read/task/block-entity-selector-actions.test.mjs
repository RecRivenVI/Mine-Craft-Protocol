import assert from 'node:assert/strict';
import test from 'node:test';
import {blockEntitySelectorActions} from './block-entity-selector-actions.mjs';

function simulation(options = {}) {
  let mode = 'READ', milliseconds = 0, clientRejected = false;
  const blocks = new Map(), calls = [], checks = [], actionSamples = [];
  const api = async (method, path, body) => {
    calls.push({method, path, body: structuredClone(body)}); milliseconds++;
    if (path === '/v0/input/state') return {pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false, inputDispatchSequence: 7};
    if (path === '/v0/control/status') return {mode, status: 'available'};
    if (path === '/v0/player?category=USER') return {dataCategory: 'USER', available: true, x: .5, y: 64, z: .5};
    if (path.startsWith('/v0/world/block?')) {
      const query = new URL('http://fixture' + path).searchParams, key = ['x', 'y', 'z'].map(axis => query.get(axis)).join(',');
      return {available: true, block: options.dirtyAir && !blocks.size ? 'minecraft:stone' : blocks.get(key) ?? 'minecraft:air', chunkLoadRequested: false};
    }
    if (path === '/v0/command/player') {
      assert.equal(mode, 'OPERATE'); assert.equal(body.route, 'NATIVE');
      const [, x, y, z, block] = body.command.split(' '); blocks.set([x, y, z].join(','), block);
      return {route: 'NATIVE', dataCategory: 'USER', inputDispatched: false, effectConfirmed: false};
    }
    if (path === '/v0/observe/deep') {
      assert.equal(method, 'POST'); assert.equal(mode, 'OPERATE'); assert.equal(body.category, 'DEBUG'); assert.equal(body.includeSerializedBlockEntities, false);
      const selected = body.selector.blocks?.map(point => `${point.x},${point.y},${point.z}`);
      const server = {chunkLoadRequested: false, storageAccessed: false};
      if (body.domains.includes('blocks')) server.blocks = selected.map(key => ({key, available: options.farLoaded === true, loadRequested: false}));
      else server.blockEntities = [...blocks].filter(([key, block]) => block !== 'minecraft:air' && (!selected || options.ignoreSelector || selected.includes(key)))
        .map(([key, type]) => ({key, type, loaded: true, readEffects: 'none'}));
      return {dataCategory: 'DEBUG', perspective: body.perspective, server,
        ...(body.perspective === 'both' ? {client: options.clientInternal ? {blockEntities: []} : {available: true}} : {})};
    }
    assert.fail('Unexpected CF-021 API ' + method + ' ' + path);
  };
  const ctx = {api, target: '26.3-neoforge', fixtureInput: {policy: 'immutable_baseline_restored', target: '26.3-neoforge', role: 'client',
    credentialsCopied: false, baselineSha256: 'a'.repeat(64), restoredSha256: 'a'.repeat(64)}, actionSamples,
    connection: {async fetch(path, init) {
      assert.equal(path, '/v0/observe/deep'); const body = JSON.parse(init.body); assert.equal(body.perspective, 'client_known'); clientRejected = true;
      return Response.json({error: 'UNSUPPORTED_DEEP_SELECTOR'}, {status: options.badRejectionStatus ? 500 : 400});
    }},
    async mode(value) {mode = value;}, async check(name, run) {await run(); checks.push(name);}};
  return {ctx, services: {now: () => milliseconds, sleep: async delay => {milliseconds += delay;}}, calls, checks, actionSamples,
    state: () => ({mode, clientRejected, blocks: [...blocks.values()]})};
}

test('CF-021 live-shaped flow selects one of two arranged cached entities and keeps client and unloaded boundaries', async () => {
  const fixture = simulation(), result = await blockEntitySelectorActions(fixture.ctx, fixture.services);
  assert.equal(fixture.checks.length, 4); assert.equal(result.checks, 4); assert.equal(result.liveEvidence, false);
  assert.equal(result.scope, 'CF-021_coordinate_selection_not_CF-022_pagination'); assert.equal(result.persistentWriteCalls, 0);
  const exact = fixture.actionSamples.find(sample => sample.case === 'block_entity_selector_exact');
  assert.equal(exact.broad.server.blockEntities.length, 2); assert.equal(exact.value.server.blockEntities.length, 1);
  assert.equal(exact.value.server.blockEntities[0].type, 'minecraft:barrel');
  assert.equal(result.inputBeforeReads.inputDispatchSequence, result.inputAfterReads.inputDispatchSequence);
  assert.equal(fixture.state().mode, 'READ'); assert.equal(fixture.state().clientRejected, true);
  assert(fixture.state().blocks.every(block => block === 'minecraft:air'));
  assert.equal(fixture.calls.filter(call => call.body?.command?.endsWith('minecraft:air')).length, 2);
  assert(!fixture.calls.some(call => call.path === '/v0/pipelines' || call.path.startsWith('/v0/debug/')));
});

test('the ignored selector reproduces failure and is not hidden by a successful broad scan', async () => {
  const fixture = simulation({ignoreSelector: true}); await assert.rejects(blockEntitySelectorActions(fixture.ctx, fixture.services));
  assert(!fixture.actionSamples.some(sample => sample.case === 'block_entity_selector_complete'));
  assert(fixture.state().blocks.every(block => block === 'minecraft:air')); assert.equal(fixture.state().mode, 'READ');
});

test('CF-021 GUI entry waits for the asynchronously populated world row, not just its Screen', async () => {
  const fixture = simulation(), actions = [], row = {worldName: 'World-Read-Alpha', nodeId: 'world:owned'};
  fixture.ctx.enterWorld = true;
  fixture.ctx.nodes = tree => tree.children ?? [];
  fixture.ctx.acquire = async () => actions.push('acquire');
  fixture.ctx.release = async () => actions.push('release');
  fixture.ctx.click = async key => actions.push(key);
  fixture.ctx.clickNode = async node => {assert.equal(node, row); actions.push(node.nodeId);};
  fixture.ctx.wait = async (predicate, label) => {
    if (label.includes('world list')) {
      assert.equal(predicate({screenClass: 'SelectWorldScreen', children: []}), false);
      assert.equal(predicate({screenClass: 'SelectWorldScreen', children: [{worldName: 'Unrelated'}]}), false);
      const ready = {screenClass: 'SelectWorldScreen', children: [row]};
      assert.equal(predicate(ready), true); return ready;
    }
    const entered = {screenClass: ''}; assert.equal(predicate(entered), true); return entered;
  };
  await blockEntitySelectorActions(fixture.ctx, fixture.services);
  assert.deepEqual(actions, ['acquire', 'menu.singleplayer', 'world:owned', 'selectWorld.select', 'release']);
  assert.equal(fixture.checks.length, 4);
});

test('CF-021 refuses duplicate fixed-world rows before clicking and still releases its Lease', async () => {
  const fixture = simulation(), actions = [], row = {worldName: 'World-Read-Alpha'};
  Object.assign(fixture.ctx, {enterWorld: true, nodes: tree => tree.children ?? [],
    acquire: async () => actions.push('acquire'), release: async () => actions.push('release'),
    click: async key => actions.push(key), clickNode: async () => assert.fail('Duplicate row must not be clicked'),
    wait: async predicate => {const tree = {screenClass: 'SelectWorldScreen', children: [row, {...row}]}; assert.equal(predicate(tree), true); return tree;}});
  await assert.rejects(blockEntitySelectorActions(fixture.ctx, fixture.services), /2 !== 1/);
  assert.deepEqual(actions, ['acquire', 'menu.singleplayer', 'release']);
  assert.equal(fixture.checks.length, 0);
  assert.equal(fixture.state().mode, 'READ');
});

test('unavailable boundaries and unsafe fixture input cannot be reported as a passed selector repair', async () => {
  for (const options of [{farLoaded: true}, {clientInternal: true}, {badRejectionStatus: true}, {dirtyAir: true}]) {
    const fixture = simulation(options); await assert.rejects(blockEntitySelectorActions(fixture.ctx, fixture.services));
    assert(!fixture.actionSamples.some(sample => sample.case === 'block_entity_selector_complete'));
    assert.equal(fixture.state().mode, 'READ');
    if (options.dirtyAir) assert.equal(fixture.calls.filter(call => call.path === '/v0/command/player').length, 0);
    else assert(fixture.state().blocks.every(block => block === 'minecraft:air'));
  }
  const fixture = simulation(); fixture.ctx.fixtureInput.credentialsCopied = true;
  await assert.rejects(blockEntitySelectorActions(fixture.ctx, fixture.services)); assert.equal(fixture.calls.length, 0);
});
