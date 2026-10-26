import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

export async function blockEntityPagesActions({api, connection, target, repo, instance, fixtureInput, mode, acquire, release,
  click, clickNode, wait, nodes, check, actionSamples}) {
  assert.equal(fixtureInput?.policy, 'immutable_baseline_restored');
  assert.equal(fixtureInput.target, target); assert.equal(fixtureInput.credentialsCopied, false);
  let ownsLease = false, arranged = false, positions, bounds, failure, cleanupFailure;
  const key = p => `${p.x},${p.y},${p.z}`;
  const deep = body => api('POST', '/v0/observe/deep', body);
  const request = (selector = {}, domains = ['block_entities']) => ({category: 'DEBUG',
    perspective: 'server_authoritative', domains, selector: {chunkRadius: 0, ...selector},
    includeSerializedBlockEntities: false, budgets: {maxBlockEntities: 128}});
  const command = async value => {
    const receipt = await api('POST', '/v0/command/player', {command: value, route: 'NATIVE'});
    assert.equal(receipt.inputDispatched, false); assert.equal(receipt.dataCategory, 'USER'); return receipt;
  };
  async function blocks() {
    const all = [];
    for (let at = 0; at < positions.length; at += 64) {
      const value = await deep(request({blocks: positions.slice(at, at + 64)}, ['blocks']));
      assert.equal(value.server.chunkLoadRequested, false); assert.equal(value.server.storageAccessed, false);
      all.push(...value.server.blocks);
    }
    assert.equal(all.length, positions.length);
    assert.deepEqual(all.map(p => p.key).sort(), positions.map(key).sort());
    for (const p of all) {assert.equal(p.available, true); assert.equal(p.loadRequested, false);}
    return all;
  }
  async function untilBlocks(id) {
    const deadline = Date.now() + 10000; let current;
    do {current = await blocks(); if (current.every(p => p.blockId === id)) return current;
      await new Promise(done => setTimeout(done, 150));} while (Date.now() < deadline);
    assert.fail('Native fill result not observed: ' + JSON.stringify(current));
  }
  async function input() {
    const value = await api('GET', '/v0/input/state');
    assert.equal(value.pressedKeyCount, 0); assert.equal(value.pressedButtonCount, 0);
    assert.equal(value.inputCleanupPending, false); assert.equal(value.inputCleanupFailed, false); return value;
  }
  try {
    await acquire(); ownsLease = true; await click('menu.singleplayer', 'Singleplayer');
    const tree = await wait(t => t.screenClass.endsWith('SelectWorldScreen') && nodes(t).some(n => n.worldName === 'World-Read-Alpha'), 'CF-022 fixed world list');
    const worlds = nodes(tree).filter(n => n.worldName === 'World-Read-Alpha'); assert.equal(worlds.length, 1);
    await clickNode(worlds[0]); await click('selectWorld.select', 'Play Selected World');
    await wait(t => !t.screenClass, 'CF-022 fixed world entered'); await release(); ownsLease = false;
    await mode('OPERATE'); const before = await input(), player = await api('GET', '/v0/player?category=USER');
    const x = Math.floor(player.x) >> 4 << 4, z = (Math.floor(player.z) >> 4 << 4) + 8, y = Math.floor(player.y) + 20;
    positions = Array.from({length: 160}, (_, i) => ({x: x + i % 16, y: y + Math.floor(i / 16), z}));
    bounds = `${x} ${y} ${z} ${x + 15} ${y + 9} ${z}`;
    const originals = await blocks(); for (const p of originals) assert.equal(p.blockId, 'minecraft:air', 'Only verified baseline air may be prepared');
    arranged = true; const receipt = await command(`fill ${bounds} minecraft:barrel`);
    const nativeBlocks = await untilBlocks('minecraft:barrel');
    actionSamples.push({case: 'block_entity_pages_arrange', fixtureInput, originals, nativeBlocks, receipt, arrangeAuthority: 'diagnostic_current_player_NATIVE_commands'});
    let firstCursor;
    await check('CF-022 all 160 native block entities are reachable with bounded DEBUG pages', async () => {
      const seen = new Map(); let cursor; let count = 0;
      do {
        const body = request(cursor ? {blockEntityCursor: cursor} : {}), value = await deep(body);
        actionSamples.push({case: 'block_entity_pages_page', body, value});
        assert.equal(value.dataCategory, 'DEBUG'); assert.equal(value.server.chunkLoadRequested, false);
        assert.equal(value.server.storageAccessed, false); assert(value.server.blockEntities.length <= 128);
        const page = value.server.blockEntitiesResult;
        assert(page, 'CF-022: bounded block entity result has no pagination metadata');
        assert.equal(page.returnedCount, value.server.blockEntities.length); assert.equal(page.consistency, 'per_page_live');
        for (const entry of value.server.blockEntities) {
          assert(!seen.has(entry.key), 'Pagination returned a duplicate key'); seen.set(entry.key, entry);
          assert.equal(entry.loaded, true); assert.equal(entry.readEffects, 'none'); assert(!('serializedState' in entry));
        }
        cursor = page.nextCursor; if (++count === 1) firstCursor = cursor;
        assert.equal(Boolean(cursor), page.hasMore); assert(count <= 8, 'Pagination must terminate for this finite native fixture');
      } while (cursor);
      for (const p of positions) assert.equal(seen.get(key(p))?.type, 'minecraft:barrel');
      assert(firstCursor, '160 native entities must exceed the 128-item page');
      actionSamples.push({case: 'block_entity_pages_complete', nativeExpected: positions.map(key), returnedKeys: [...seen.keys()], pages: count});
    });
    await check('CF-022 continuation cannot change selector or open client internal data', async () => {
      for (const body of [{...request({blockEntityCursor: firstCursor, chunkRadius: 1})},
        {...request({blockEntityCursor: firstCursor}), perspective: 'client_known'}]) {
        const response = await connection.fetch('/v0/observe/deep', {method: 'POST', headers: {'content-type': 'application/json'}, body: JSON.stringify(body), signal: AbortSignal.timeout(10000)});
        const denied = await response.json(); assert(!response.ok); assert(['DEEP_PAGE_CURSOR_STALE', 'UNSUPPORTED_DEEP_SELECTOR'].includes(denied.error), JSON.stringify(denied));
        actionSamples.push({case: 'block_entity_pages_denied', body, status: response.status, denied});
      }
    });
    await check('CF-022 actual MCP serialized pages include marked inventory beyond the first 128 entries', async () => {
      const firstPage = actionSamples.find(value => value.case === 'block_entity_pages_page').value.server.blockEntities;
      const firstKeys = new Set(firstPage.map(value => value.key));
      const tail = positions.find(position => !firstKeys.has(key(position))); assert(tail, 'Marker must be outside the original first page');
      const pointBody = {...request({blocks:[tail]}),includeSerializedBlockEntities:true};
      const original = await deep(pointBody); assert.equal(original.server.blockEntities.length,1);
      assert.deepEqual(original.server.blockEntities[0].serializedState.Items,[]);
      const receipt = await command(`item replace block ${tail.x} ${tail.y} ${tail.z} container.0 with minecraft:stone 7`);
      const countOf = item => Number(target.startsWith('1.20.1-') ? item.Count : item.count);
      const deadline = Date.now()+5000; let marked;
      do {
        marked = await deep(pointBody);
        if(marked.server.blockEntities[0].serializedState.Items.some(value => Number(value.Slot) === 0 && value.id === 'minecraft:stone' && countOf(value) === 7)) break;
        assert(Date.now() < deadline,'Native marked inventory was not observed'); await new Promise(done => setTimeout(done,150));
      } while(true);
      actionSamples.push({case:'block_entity_pages_marked_tail',tail,firstKeys:[...firstKeys],original,receipt,marked,arrangeAuthority:'diagnostic_current_player_NATIVE_command'});
      const require = createRequire(resolve(repo, 'components/companion/package.json'));
      const {Client} = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
      const {StdioClientTransport} = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
      const client = new Client({name:'cf022-pages',version:'1.0.0'}, {versionNegotiation:{mode:'legacy'}});
      const env = Object.fromEntries(Object.entries(process.env).filter(([name, value]) => typeof value === 'string' && !/^MCP_MINECRAFT_/i.test(name)));
      env.MCP_MINECRAFT_INSTANCE_DIR = instance; env.MCP_MINECRAFT_TARGET = target;
      const transport = new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
      try {
        await client.connect(transport, {timeout:10000});
        const replies = []; let cursor;
        do {
          const args = {...request(cursor ? {blockEntityCursor:cursor} : {}),includeSerializedBlockEntities:true,budgets:{maxBlockEntities:32}};
          const reply = await client.callTool({name:'minecraft_deep_observe',arguments:args}, {timeout:10000});
          assert(!reply.isError, JSON.stringify(reply)); const value = reply.structuredContent?.data; assert(value?.server);
          actionSamples.push({case:'block_entity_pages_serialized_page',args,value});
          assert.equal(value.metadata.readEffects,'serialization_hooks_invoked','Serialized owner capture must not claim effect-free metadata');
          replies.push(value); cursor = value.server.blockEntitiesResult.nextCursor;
          assert(replies.length <= 8);
        } while (cursor);
        const returned = replies.flatMap(value => value.server.blockEntities.map(entry => entry.key));
        assert.equal(returned.length, new Set(returned).size);
        for (const position of positions) assert(returned.includes(key(position)));
        const values = new Map(replies.flatMap(value => value.server.blockEntities).map(value => [value.key,value]));
        for(const position of positions) {
          const value = values.get(key(position)); assert.equal(value.serializedStateStatus,'complete');
          assert.equal(value.readEffects,'serialization_hooks_invoked'); assert.equal(value.serializedState.id,'minecraft:barrel');
          const items = value.serializedState.Items; assert(Array.isArray(items));
          if(key(position) === key(tail)) {
            assert.equal(items.length,1); assert.equal(items[0].id,'minecraft:stone'); assert.equal(countOf(items[0]),7); assert.equal(Number(items[0].Slot),0);
          } else assert.deepEqual(items,[]);
        }
        const status = await api('GET', '/v0/control/status'); assert.equal(status.mode, 'OPERATE'); assert(!status.leaseId);
        actionSamples.push({case:'block_entity_pages_mcp', transport:'real_stdio', replies, status,serializedContentsVerified:160,markedTail:key(tail)});
      } finally {await client.close(); await transport.close();}
    });
    const after = await input(); assert.equal(after.inputDispatchSequence, before.inputDispatchSequence);
  } catch (error) {failure = error;}
  finally {
    try {
      if (ownsLease) await release(); await mode('OPERATE');
      if (arranged) {
        const current = await blocks();
        for (const p of current) assert(['minecraft:air', 'minecraft:barrel'].includes(p.blockId), 'Unknown external replacements are preserved');
        const receipt = await command(`fill ${bounds} minecraft:air`), restored = await untilBlocks('minecraft:air');
        actionSamples.push({case: 'block_entity_pages_cleanup', receipt, restored});
      }
      await mode('READ'); await input();
    } catch (error) {cleanupFailure = error;}
  }
  if (failure) throw failure; if (cleanupFailure) throw cleanupFailure;
  return {case: 'block_entity_pages_finished', persistentWriteCalls: 0, finalMode: 'READ'};
}
