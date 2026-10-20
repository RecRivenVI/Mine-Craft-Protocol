import assert from 'node:assert/strict';

const key = position => `${position.x},${position.y},${position.z}`;

export async function blockEntitySelectorActions({api, connection, target, fixtureInput, mode, acquire, release, click, clickNode,
  wait, nodes, check, actionSamples, enterWorld = false}, services) {
  assert.equal(fixtureInput?.policy, 'immutable_baseline_restored'); assert.equal(fixtureInput.target, target);
  assert.equal(fixtureInput.role, 'client'); assert.equal(fixtureInput.credentialsCopied, false);
  assert.match(fixtureInput.baselineSha256, /^[0-9a-f]{64}$/); assert.equal(fixtureInput.restoredSha256, fixtureInput.baselineSha256);
  const now = services?.now ?? (() => performance.now()), sleep = services?.sleep ?? (millis => new Promise(done => setTimeout(done, millis)));
  const deadline = now() + 90000, owned = []; let ownsLease = false, primaryFailure, cleanupFailure, inputBeforeReads, inputAfterReads;
  const remaining = (maximum = 5000) => {const left = Math.floor(deadline - now()); assert(left > 0, 'CF-021 exceeded 90 seconds'); return Math.min(maximum, left);};
  async function until(read, predicate, label) {
    const end = now() + remaining(); let value;
    do {value = await read(); if (predicate(value)) return value; await sleep(150);} while (now() < end);
    assert.fail(label + ': ' + JSON.stringify(value));
  }
  async function input() {
    const value = await api('GET', '/v0/input/state');
    assert.equal(value.pressedKeyCount, 0); assert.equal(value.pressedButtonCount, 0);
    assert.equal(value.inputCleanupPending, false); assert.equal(value.inputCleanupFailed, false);
    assert(Number.isSafeInteger(value.inputDispatchSequence)); return value;
  }
  const point = position => api('GET', '/v0/world/block?category=USER&' + new URLSearchParams(position));
  async function command(command) {
    const receipt = await api('POST', '/v0/command/player', {command, route: 'NATIVE'});
    assert.equal(receipt.route, 'NATIVE'); assert.equal(receipt.dataCategory, 'USER');
    assert.equal(receipt.inputDispatched, false); assert.equal(receipt.effectConfirmed, false); return receipt;
  }
  const request = (perspective, blocks, domains = ['block_entities']) => ({category: 'DEBUG', perspective, domains,
    selector: {chunkRadius: 0, ...(blocks === undefined ? {} : {blocks})}, includeSerializedBlockEntities: false,
    budgets: {maxBlockEntities: 128}});
  async function deep(body) {
    const value = await api('POST', '/v0/observe/deep', body);
    assert.equal(value.dataCategory, 'DEBUG'); assert.equal(value.perspective, body.perspective);
    assert.equal(value.server.chunkLoadRequested, false); assert.equal(value.server.storageAccessed, false);
    return value;
  }
  function exact(value, positions) {
    const entries = value.server.blockEntities; assert(Array.isArray(entries));
    assert.deepEqual(entries.map(entry => entry.key).sort(), positions.map(key).sort());
    for (const entry of entries) {assert.equal(entry.loaded, true); assert.equal(entry.readEffects, 'none'); assert(!('serializedState' in entry));}
    return entries;
  }
  try {
    if (enterWorld) {
      await acquire(); ownsLease = true; await click('menu.singleplayer', 'Singleplayer');
      const list = await wait(value => value.screenClass.endsWith('SelectWorldScreen') && nodes(value).some(node => node.worldName === 'World-Read-Alpha'), 'CF-021 restored world list', remaining(20000));
      const worlds = nodes(list).filter(value => value.worldName === 'World-Read-Alpha'); assert.equal(worlds.length, 1);
      const world = worlds[0];
      await clickNode(world); await click('selectWorld.select', 'Play Selected World');
      await wait(value => !value.screenClass, 'CF-021 restored world entered', remaining(60000)); await release(); ownsLease = false;
    }
    await mode('OPERATE'); const beforeArrange = await input();
    const status = await api('GET', '/v0/control/status'); assert.equal(status.mode, 'OPERATE'); assert(!status.leaseId);
    const player = await api('GET', '/v0/player?category=USER'); assert.equal(player.available, true); assert.equal(player.dataCategory, 'USER');
    const chunkX = Math.floor(player.x) >> 4, chunkZ = Math.floor(player.z) >> 4;
    const a = {x: chunkX * 16 + 4, y: Math.floor(player.y) + 2, z: chunkZ * 16 + 4};
    const b = {...a, x: a.x + 1};
    const originals = await Promise.all([point(a), point(b)]);
    for (const original of originals) {assert.equal(original.available, true); assert.equal(original.block, 'minecraft:air', 'Only baseline air is used'); assert.equal(original.chunkLoadRequested, false);}
    const receipts = [];
    for (const [position, block] of [[a, 'minecraft:chest'], [b, 'minecraft:barrel']]) {
      owned.push({position, block}); receipts.push(await command(`setblock ${position.x} ${position.y} ${position.z} ${block}`));
      await until(() => point(position), value => value.available && value.block === block, 'Diagnostic block entity did not reach the client');
    }
    inputBeforeReads = await input(); assert.equal(inputBeforeReads.inputDispatchSequence, beforeArrange.inputDispatchSequence);
    actionSamples.push({case: 'block_entity_selector_fixture', fixtureInput, player, a, b, originals, receipts, beforeArrange,
      copyOnlySideEffects: true, arrangeAuthority: 'diagnostic_current_player_NATIVE_commands', inputBeforeReads});
    await check('CF-021 explicit coordinates return only the selected cached block entity', async () => {
      const broad = await until(() => deep(request('server_authoritative')), value => {
        const found = value.server.blockEntities.map(entry => entry.key); return found.includes(key(a)) && found.includes(key(b));
      }, 'Both diagnostic entities must exist before selecting one');
      const body = request('server_authoritative', [b]), value = await deep(body), entries = exact(value, [b]);
      assert.equal(entries[0].type, 'minecraft:barrel');
      actionSamples.push({case: 'block_entity_selector_exact', body, broad, value, expected: b, excluded: a});
    });
    await check('CF-021 both perspective selects only that server entity and opens no client internal entity data', async () => {
      const body = request('both', [b]), value = await deep(body); exact(value, [b]);
      assert(value.client && typeof value.client === 'object'); assert(!('blockEntities' in value.client));
      actionSamples.push({case: 'block_entity_selector_both', body, value});
    });
    await check('CF-021 client-known coordinate block-entity selector is explicitly rejected', async () => {
      const body = request('client_known', [b]);
      const response = await connection.fetch('/v0/observe/deep', {method: 'POST', headers: {'content-type': 'application/json'},
        body: JSON.stringify(body), signal: AbortSignal.timeout(remaining())});
      const denied = await response.json(); assert.equal(response.status, 400); assert.equal(denied.error, 'UNSUPPORTED_DEEP_SELECTOR');
      assert(!('client' in denied)); assert(!('server' in denied));
      actionSamples.push({case: 'block_entity_selector_client_rejected', body, httpStatus: response.status, denied});
    });
    await check('CF-021 unloaded coordinates stay unavailable and never return nearby block entities', async () => {
      const far = {...b, x: b.x + 1048576, z: b.z + 1048576}, pointBody = request('server_authoritative', [far], ['blocks']);
      const before = await deep(pointBody); assert.equal(before.server.blocks.length, 1);
      assert.equal(before.server.blocks[0].available, false); assert.equal(before.server.blocks[0].loadRequested, false);
      const body = request('server_authoritative', [far]), value = await deep(body); exact(value, []);
      const after = await deep(pointBody); assert.equal(after.server.blocks.length, 1);
      assert.equal(after.server.blocks[0].available, false); assert.equal(after.server.blocks[0].loadRequested, false);
      actionSamples.push({case: 'block_entity_selector_unloaded', far, pointBody, before, body, value, after, chunkLoadRequested: false});
    });
    inputAfterReads = await input(); assert.equal(inputAfterReads.inputDispatchSequence, inputBeforeReads.inputDispatchSequence);
  } catch (error) {primaryFailure = error;}
  finally {
    try {
      if (ownsLease) {await release(); ownsLease = false;}
      await mode('OPERATE'); const cleanup = [];
      for (const entry of owned) {
        const current = await point(entry.position);
        assert.equal(current.available, true);
        if (current.block === entry.block) {
          const position = entry.position, receipt = await command(`setblock ${position.x} ${position.y} ${position.z} minecraft:air`);
          const restored = await until(() => point(position), value => value.available && value.block === 'minecraft:air', 'CF-021 cleanup not received');
          cleanup.push({position, current, receipt, restored});
        } else {assert.equal(current.block, 'minecraft:air', 'Unknown replacement is preserved, not cleared'); cleanup.push({position: entry.position, current, alreadyAir: true});}
      }
      await mode('READ'); actionSamples.push({case: 'block_entity_selector_cleanup', cleanup});
    } catch (error) {cleanupFailure = error; actionSamples.push({case: 'block_entity_selector_cleanup_failure', error: String(error)});}
  }
  if (primaryFailure) throw primaryFailure;
  if (cleanupFailure) throw cleanupFailure;
  const finalInput = await input(), finalMode = await api('GET', '/v0/control/status'); assert.equal(finalMode.mode, 'READ'); assert(!finalMode.leaseId);
  const result = {case: 'block_entity_selector_complete', target, checks: 4, evidenceKind: services ? 'synthetic_test_not_runtime' : 'runtime_functional',
    liveEvidence: !services, scope: 'CF-021_coordinate_selection_not_CF-022_pagination', inputBeforeReads, inputAfterReads,
    finalInput, finalMode, chunkLoadRequested: false, persistentWriteCalls: 0};
  actionSamples.push(result); return result;
}
