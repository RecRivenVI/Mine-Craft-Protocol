import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import test from 'node:test';
import {mcpWorldBaseline} from './mcp-world-baseline.mjs';

const repo = resolve(import.meta.dirname, '../../../..'), target = '26.3-neoforge';
const pkg = JSON.parse(await readFile(resolve(repo, 'components/companion/package.json'), 'utf8'));
const contextId = 'opaque-world-player-connection';
const metadata = {available: true, domain: 'WORLD', dataCategory: 'USER', authority: 'CLIENT_KNOWN', source: 'bounded_client_world_model',
  dataSource: 'LIVE', storageAccessed: false, chunkLoadRequested: false, completeGameData: false};

function fixture(options = {}) {
  let milliseconds = 0, playerCalls = 0, inputReads = 0, clientClosed = 0, transportClosed = 0;
  const calls = [], nativeCalls = [], samples = [], checks = [];
  const status = () => ({mode: 'READ', status: 'available', reconsentRequired: true, modeVersion: {controlSessionId: 'same-instance', generation: 1}});
  const mcpMode = () => ({type: 'control.mode', mode: 'READ', takeoverActive: false, authorizationIndependent: true,
    authorization: {sessionOpen: true, inputLease: {held: false, remainingTtlMs: 0}},
    reconsentRequired: true, modeVersion: {controlSessionId: 'same-instance', generation: 1}});
  const player = () => ({...metadata, type: 'player.state', x: -1.25, y: 65, z: .5, yaw: 90, pitch: 0, onGround: true,
    velocityX: 0, velocityY: 0, velocityZ: 0, dimension: 'minecraft:overworld'});
  const input = () => ({pressedKeyCount: 0, pressedButtonCount: 0, inputDispatchSequence: 42, inputCleanupPending: false, inputCleanupFailed: false,
    inputContextAvailable: true, inputContextGeneration: 1, inputContextId: contextId});
  function region(query) {
    const min = query.min;
    return {...metadata, type: 'world.region', dimension: 'minecraft:overworld', contextId, sourceTick: 100, sourceTickEnd: 100,
      captureDurationNanos: 1000, ownerBudgetNanos: 8000000, stateSemantics: 'per_page_live_client_cache_non_atomic',
      order: 'section_y_z_x_then_local_y_z_x', unloadedPaletteIndex: -1, crossPageAtomic: false, region: {min, max: query.max},
      section: {x: Math.floor(min.x / 16), y: Math.floor(min.y / 16), z: Math.floor(min.z / 16), min, size: {x: 1, y: 1, z: 1}, offset: 0},
      palette: [{block: 'minecraft:grass_block', properties: {snowy: 'false'}}], indices: [0], pageStartOffset: 0, pageCellCount: 1,
      pageUnloadedCellCount: 0, requestedCellCount: 1, coveredCellCount: 1, remainingCellCount: 0, unloadedCellCount: 0,
      complete: true, allLoaded: true, truncated: false, maxBlocks: query.maxBlocks, maxBytes: query.maxBytes};
  }
  const client = {
    async connect() {milliseconds += 47; if (options.connectFailure) throw new Error('connect failed');},
    getServerVersion() {return {name: 'minecraft-protocol-companion', version: options.version ?? pkg.version};},
    async callTool(request) {
      calls.push(structuredClone(request)); const {name, arguments: args} = request; let result;
      if (name === 'minecraft_get_session') result = {target: options.target ?? target, inWorld: true};
      else if (name === 'minecraft_control') {assert.deepEqual(args, {action: 'status'}); result = mcpMode(); options.editMcpMode?.(result);}
      else if (name === 'minecraft_get_state') {
        assert.deepEqual(args, {kind: 'player', category: 'USER'}); playerCalls++; milliseconds += playerCalls;
        if (options.playerError) return {isError: true, structuredContent: {error: {code: 'WORLD_UNAVAILABLE'}}};
        result = player(); options.editPlayer?.(result);
      } else if (name === 'minecraft_query_world') {
        assert.equal(args.kind, 'region'); assert.equal(args.category, 'USER'); assert.equal(args.perspective, 'client'); assert.equal(args.action, 'read');
        result = region(args.query); options.editRegion?.(result);
      } else assert.fail('Unexpected tool ' + name);
      if (name !== 'minecraft_get_state') milliseconds += 5;
      return {structuredContent: {data: result}};
    },
    async close() {clientClosed++; if (options.closeFailure) throw new Error('owned close failed');}
  };
  const ctx = {
    repo, instance: resolve(repo, 'validations/conformance-world_read/instance', target, 'client'), target, actionSamples: samples,
    async check(name, run) {await run(); checks.push(name);},
    async api(method, path) {
      nativeCalls.push({method, path}); assert.equal(method, 'GET'); milliseconds += 2;
      if (path === '/v0/control/status') {const value = status(); options.editStatus?.(value, playerCalls); return value;}
      if (path === '/v0/input/state') {const value = input(); options.editInput?.(value, ++inputReads); return value;}
      assert.equal(path, '/v0/world/block?category=USER&x=-2&y=64&z=0');
      const value = {...metadata, type: 'world.block', block: 'minecraft:grass_block', state: 'Block{minecraft:grass_block}[snowy=false]'};
      options.editPoint?.(value); return value;
    }
  };
  let environment;
  const services = {
    now: () => milliseconds, async sleep(millis) {milliseconds += millis;},
    environment: {PATH: process.env.PATH, MCP_MINECRAFT_BASE_URL: 'http://wrong:1', MCP_MINECRAFT_TOKEN: 'do-not-log',
      MCP_MINECRAFT_TOKEN_FILE: 'other-instance-token', MCP_MINECRAFT_INSTANCE_DIR: 'other-instance', MCP_MINECRAFT_TARGET: 'other-target',
      mcp_minecraft_base_url: 'http://wrong-case:2', mcp_minecraft_token: 'do-not-log-case', mcp_minecraft_instance_dir: 'wrong-case-instance'},
    async createMcp(config) {
      environment = config.env; milliseconds += 23;
      return {client, transport: {async close() {transportClosed++;}}};
    }
  };
  return {ctx, services, calls, nativeCalls, samples, checks, environment: () => environment,
    closed: () => ({clientClosed, transportClosed}), playerCalls: () => playerCalls};
}

test('MCP baseline measures exactly20 monotonic calls, excludes startup/pacing and compares a one-cell USER page to Native', async () => {
  const value = fixture(), evidence = await mcpWorldBaseline(value.ctx, value.services);
  assert.equal(value.checks.length, 1); assert.equal(value.samples.length, 1); assert.equal(value.playerCalls(), 20);
  assert.deepEqual(evidence.samples.map(sample => sample.callRttMillis), Array.from({length: 20}, (_, index) => index + 1));
  assert.equal(evidence.startupMillis, 70); assert.equal(evidence.startupExcludedFromPlayerStatistics, true);
  assert.deepEqual(evidence.statistics, {sampleCount: 20, method: 'nearest_rank', min: 1, p50: 10, p95: 19, max: 20, slowest: {index: 19, callRttMillis: 20}});
  for (let index = 1; index < evidence.samples.length; index++) assert(evidence.samples[index].startedOffsetMillis - evidence.samples[index - 1].startedOffsetMillis >= 35);
  assert(evidence.samplingElapsedMillis > evidence.samples.reduce((sum, sample) => sum + sample.callRttMillis, 0));
  assert.deepEqual(evidence.query, {min: {x: -2, y: 64, z: 0}, max: {x: -2, y: 64, z: 0}, maxBlocks: 4096, maxBytes: 131072});
  assert.equal(evidence.decodedCell.block, evidence.point.block); assert.equal(evidence.transport, 'injected_unit_test'); assert.equal(evidence.liveEvidence, false);
  assert.equal(evidence.crossMachineLatencySla, false); assert.equal(evidence.automaticRetries, 0); assert.equal(evidence.inputDispatched, false);
  assert.equal(evidence.ownedCompanionClosed, true); assert.deepEqual(value.closed(), {clientClosed: 1, transportClosed: 1});
  assert.equal(value.environment().MCP_MINECRAFT_INSTANCE_DIR, value.ctx.instance); assert.equal(value.environment().MCP_MINECRAFT_TARGET, target);
  for (const name of ['MCP_MINECRAFT_BASE_URL', 'MCP_MINECRAFT_TOKEN', 'MCP_MINECRAFT_TOKEN_FILE']) assert(!(name in value.environment()));
  for (const name of ['mcp_minecraft_base_url', 'mcp_minecraft_token', 'mcp_minecraft_instance_dir']) assert(!(name in value.environment()));
  assert(!JSON.stringify(evidence).includes('do-not-log'));
  assert(value.nativeCalls.every(call => call.method === 'GET'));
  assert(value.calls.every(call => ['minecraft_get_session', 'minecraft_control', 'minecraft_get_state', 'minecraft_query_world'].includes(call.name)));
});

test('initialize version and actual session target mismatches fail before any player sample and close only the owned Companion', async () => {
  for (const options of [{version: 'wrong-package'}, {target: 'wrong-target'}, {connectFailure: true}]) {
    const value = fixture(options); await assert.rejects(mcpWorldBaseline(value.ctx, value.services));
    assert.equal(value.playerCalls(), 0); assert.equal(value.samples.length, 0); assert.equal(value.checks.length, 0);
    assert.deepEqual(value.closed(), {clientClosed: 1, transportClosed: 1});
  }
});

test('MCP control status uses the actual mode summary, never a fabricated lease-status shape', async () => {
  for (const editMcpMode of [value => {delete value.authorization;}, value => {value.authorization.inputLease.held = true;},
    value => {value.authorization.inputLease.remainingTtlMs = 1;}, value => {value.takeoverActive = true;},
    value => {value.authorizationIndependent = false;}, value => {value.authorization.sessionOpen = false;}]) {
    const value = fixture({editMcpMode}); await assert.rejects(mcpWorldBaseline(value.ctx, value.services));
    assert.equal(value.playerCalls(), 0); assert.deepEqual(value.closed(), {clientClosed: 1, transportClosed: 1});
  }
});

test('MCP error or privileged/disk/chunk-loading player payload is not a successful RTT sample', async () => {
  for (const options of [{playerError: true}, ...['storageAccessed', 'chunkLoadRequested'].map(field => ({editPlayer: value => {value[field] = true;}})),
    {editPlayer: value => {value.dataCategory = 'DEBUG';}}, {editPlayer: value => {value.velocityX = NaN;}}]) {
    const value = fixture(options); await assert.rejects(mcpWorldBaseline(value.ctx, value.services));
    assert.equal(value.playerCalls(), 1); assert.equal(value.samples.length, 0); assert.deepEqual(value.closed(), {clientClosed: 1, transportClosed: 1});
  }
});

test('palette corruption, point mismatch and server/disk/loaded evidence fail closed without retries', async () => {
  for (const options of [{editRegion: value => {value.indices[0] = 2;}}, {editRegion: value => {value.palette[0].nbt = {};}},
    {editRegion: value => {value.chunkLoadRequested = true;}}, {editRegion: value => {value.authority = 'SERVER_AUTHORITATIVE';}},
    {editPoint: value => {value.block = 'minecraft:stone';}}, {editPoint: value => {value.state = 'Block{minecraft:grass_block}[snowy=true]';}},
    {editPoint: value => {value.storageAccessed = true;}}]) {
    const value = fixture(options); await assert.rejects(mcpWorldBaseline(value.ctx, value.services));
    assert.equal(value.playerCalls(), 20); assert.equal(value.calls.filter(call => call.name === 'minecraft_query_world').length, 1);
    assert.equal(value.checks.length, 0); assert.equal(value.samples.length, 0); assert.deepEqual(value.closed(), {clientClosed: 1, transportClosed: 1});
  }
});

test('READ/noLease, real input/context stability and successful owned-process cleanup are required', async () => {
  for (const options of [{editStatus: value => {value.mode = 'OPERATE';}}, {editStatus: value => {value.leaseId = 'unexpected';}},
    {editStatus: (value, sampled) => {if (sampled) value.modeVersion.generation++;}},
    {editStatus: (value, sampled) => {if (sampled) value.reconsentRequired = false;}},
    {editInput: (value, read) => {if (read === 2) value.inputDispatchSequence++;}},
    {editInput: (value, read) => {if (read === 2) value.inputContextId = 'replacement';}},
    {editInput: value => {value.pressedKeyCount = 1;}}, {editInput: value => {value.inputCleanupPending = true;}}, {closeFailure: true}]) {
    const value = fixture(options); await assert.rejects(mcpWorldBaseline(value.ctx, value.services)); assert.equal(value.checks.length, 0);
    assert(!value.samples.some(sample => sample.case === 'mcp_world_baseline'));
    if (value.calls.length || options.closeFailure) assert.deepEqual(value.closed(), {clientClosed: 1, transportClosed: 1});
  }
});
