import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';
import {baselineStatistics} from './interaction-baseline-actions.mjs';
import {decodeRegionPage} from './region-stream-actions.mjs';

const PLAYER_SAMPLES = 20;
const MINIMUM_CALL_INTERVAL = 35;
const MAXIMUM_MILLIS = 90000;

async function ownedMcp({repo, env}) {
  const require = createRequire(resolve(repo, 'components/companion/package.json'));
  const {Client} = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
  const {StdioClientTransport} = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
  return {
    client: new Client({name: 'mcp-world-baseline', version: '1.0.0'}, {versionNegotiation: {mode: 'legacy'}}),
    transport: new StdioClientTransport({command: process.execPath, args: [resolve(repo, 'components/companion/dist/index.js')], env, stderr: 'pipe'})
  };
}

function userWorld(value) {
  assert.equal(value.available, true, 'The restored client world must be available');
  assert.equal(value.domain, 'WORLD'); assert.equal(value.dataCategory, 'USER'); assert.equal(value.authority, 'CLIENT_KNOWN');
  assert.equal(value.source, 'bounded_client_world_model'); assert.equal(value.dataSource, 'LIVE');
  assert.equal(value.storageAccessed, false); assert.equal(value.chunkLoadRequested, false); assert.equal(value.completeGameData, false);
}

function playerState(value) {
  userWorld(value); assert.equal(value.type, 'player.state');
  for (const field of ['x', 'y', 'z', 'yaw', 'pitch', 'velocityX', 'velocityY', 'velocityZ'])
    assert(Number.isFinite(value[field]), 'Invalid player ' + field);
  assert.equal(typeof value.onGround, 'boolean'); assert.equal(typeof value.dimension, 'string');
}

function inputState(value) {
  assert.equal(value.pressedKeyCount, 0); assert.equal(value.pressedButtonCount, 0);
  assert.equal(value.inputCleanupPending, false); assert.equal(value.inputCleanupFailed, false);
  assert(Number.isSafeInteger(value.inputDispatchSequence) && value.inputDispatchSequence >= 0);
  assert.equal(value.inputContextAvailable, true); assert(Number.isSafeInteger(value.inputContextGeneration));
  assert.equal(typeof value.inputContextId, 'string'); assert(value.inputContextId.length > 0);
  return value;
}

function readOnly(status) {
  assert.equal(status.mode, 'READ'); assert.equal(status.status, 'available'); assert(!status.leaseId, 'The baseline must not hold an input Lease');
  return status;
}

function readOnlyMcpMode(value) {
  assert.equal(value.type, 'control.mode'); assert.equal(value.mode, 'READ'); assert.equal(value.takeoverActive, false);
  assert.equal(value.authorizationIndependent, true); assert.equal(value.authorization?.sessionOpen, true);
  assert.equal(value.authorization.inputLease?.held, false); assert.equal(value.authorization.inputLease.remainingTtlMs, 0);
  return value;
}

// Optional injected services exercise the consumer logic only; they never produce real-MCP evidence.
export async function mcpWorldBaseline({api, repo, instance, target, check, actionSamples}, testServices) {
  const now = testServices?.now ?? (() => performance.now());
  const sleep = testServices?.sleep ?? (millis => new Promise(done => setTimeout(done, millis)));
  const createMcp = testServices?.createMcp ?? ownedMcp;
  const started = now(), deadline = started + MAXIMUM_MILLIS;
  const remaining = (maximum = 12000) => {
    const left = Math.floor(deadline - now()); assert(left > 0, 'MCP world baseline exceeded its 90-second budget'); return Math.min(maximum, left);
  };
  let evidence;
  await check('actual MCP USER player RTT and one-cell region preserve READ, no Lease and zero input', async () => {
    const before = inputState(await api('GET', '/v0/input/state'));
    const nativeBefore = readOnly(await api('GET', '/v0/control/status'));
    const pkg = JSON.parse(await readFile(resolve(repo, 'components/companion/package.json'), 'utf8'));
    const env = Object.fromEntries(Object.entries(testServices?.environment ?? process.env).filter(([, value]) => typeof value === 'string'));
    const authorities = new Set(['MCP_MINECRAFT_TOKEN', 'MCP_MINECRAFT_BASE_URL', 'MCP_MINECRAFT_TOKEN_FILE', 'MCP_MINECRAFT_INSTANCE_DIR', 'MCP_MINECRAFT_TARGET']);
    for (const name of Object.keys(env)) if (authorities.has(name.toUpperCase())) delete env[name];
    env.MCP_MINECRAFT_INSTANCE_DIR = instance; env.MCP_MINECRAFT_TARGET = target;
    let owned, primaryFailure, cleanupFailure;
    const samples = [];
    try {
      const startupStarted = now(); owned = await createMcp({repo, env});
      await owned.client.connect(owned.transport, {timeout: remaining()});
      const startupMillis = now() - startupStarted;
      const initializeServerInfo = owned.client.getServerVersion();
      assert.equal(initializeServerInfo?.name, 'minecraft-protocol-companion'); assert.equal(initializeServerInfo.version, pkg.version);
      const call = async (name, args) => {
        const value = await owned.client.callTool({name, arguments: args}, {timeout: remaining()});
        assert(!value.isError, JSON.stringify(value)); assert(value.structuredContent?.data, JSON.stringify(value));
        return value.structuredContent.data;
      };
      const session = await call('minecraft_get_session', {});
      assert.equal(session.target, target); assert.equal(session.inWorld, true);
      const mcpBefore = readOnlyMcpMode(await call('minecraft_control', {action: 'status'}));
      assert.deepEqual(mcpBefore.modeVersion, nativeBefore.modeVersion);
      assert.equal(mcpBefore.reconsentRequired, nativeBefore.reconsentRequired);
      const samplingStarted = now(); let previousStarted = -Infinity;
      for (let index = 0; index < PLAYER_SAMPLES; index++) {
        let pause = MINIMUM_CALL_INTERVAL - (now() - previousStarted);
        while (pause > 0) {assert(pause < remaining()); await sleep(Math.ceil(pause)); pause = MINIMUM_CALL_INTERVAL - (now() - previousStarted);}
        remaining(); const callStarted = now(); previousStarted = callStarted;
        const player = await call('minecraft_get_state', {kind: 'player', category: 'USER'});
        const callRttMillis = now() - callStarted; playerState(player);
        samples.push({index, callRttMillis, startedOffsetMillis: callStarted - samplingStarted, player});
      }
      const samplingElapsedMillis = now() - samplingStarted, actor = samples.at(-1).player;
      const position = {x: Math.floor(actor.x), y: Math.floor(actor.y) - 1, z: Math.floor(actor.z)};
      const query = {min: position, max: {...position}, maxBlocks: 4096, maxBytes: 131072};
      const regionStarted = now();
      const region = await call('minecraft_query_world', {kind: 'region', perspective: 'client', category: 'USER', action: 'read', query});
      const regionCallRttMillis = now() - regionStarted;
      const cells = decodeRegionPage(region, query, 0, before.inputContextId);
      assert.equal(region.dimension, actor.dimension); assert.equal(region.complete, true); assert.equal(region.allLoaded, true);
      assert.equal(region.pageCellCount, 1); assert.equal(region.unloadedCellCount, 0); assert.equal(cells.length, 1); assert.equal(cells[0].loaded, true);
      const pointStarted = now();
      const point = await api('GET', `/v0/world/block?category=USER&x=${position.x}&y=${position.y}&z=${position.z}`);
      const nativePointRttMillis = now() - pointStarted;
      userWorld(point); assert.equal(point.block, cells[0].block);
      const state = /^Block\{([a-z0-9_.-]+:[a-z0-9/._-]+)\}(?:\[([^\]]*)\])?$/.exec(point.state); assert(state); assert.equal(state[1], cells[0].block);
      const properties = Object.fromEntries((state[2] ?? '').split(',').filter(Boolean).map(value => {
        const pair = /^([a-z0-9_]+)=([a-z0-9_.:/-]+)$/.exec(value); assert(pair); return pair.slice(1);
      }));
      assert.deepEqual(cells[0].properties, properties, 'MCP palette state differs from the Native USER point');
      const finalSession = await call('minecraft_get_session', {});
      assert.equal(finalSession.target, target); assert.equal(finalSession.inWorld, true);
      const mcpAfter = readOnlyMcpMode(await call('minecraft_control', {action: 'status'}));
      const nativeAfter = readOnly(await api('GET', '/v0/control/status'));
      assert.deepEqual(mcpAfter.modeVersion, nativeBefore.modeVersion); assert.deepEqual(nativeAfter.modeVersion, nativeBefore.modeVersion);
      assert.equal(mcpAfter.reconsentRequired, nativeBefore.reconsentRequired); assert.equal(nativeAfter.reconsentRequired, nativeBefore.reconsentRequired);
      const after = inputState(await api('GET', '/v0/input/state'));
      for (const field of ['inputDispatchSequence', 'inputContextId', 'inputContextGeneration']) assert.equal(after[field], before[field], 'Read-only baseline changed ' + field);
      const slowest = samples.reduce((left, right) => right.callRttMillis > left.callRttMillis ? right : left);
      evidence = {
        case: 'mcp_world_baseline', target, checks: 1, transport: testServices ? 'injected_unit_test' : 'real_mcp_stdio', liveEvidence: !testServices,
        initializeServerInfo, expectedPackageVersion: pkg.version, startupMillis, startupExcludedFromPlayerStatistics: true,
        session, finalSession, samples, statistics: {...baselineStatistics(samples.map(value => value.callRttMillis)), slowest: {index: slowest.index, callRttMillis: slowest.callRttMillis}},
        samplingElapsedMillis, timeSource: 'monotonic_elapsed', measurementScope: 'mcp_stdio_call_round_trip_not_http_or_server_effect', crossMachineLatencySla: false,
        minimumCallStartIntervalMillis: MINIMUM_CALL_INTERVAL, activeAdmissionExcludedFromCallRtt: true, automaticRetries: 0,
        query, region, decodedCell: cells[0], point, regionCallRttMillis, nativePointRttMillis,
        nativeBefore, mcpBefore, nativeAfter, mcpAfter, before, after,
        environment: {node: process.version, platform: process.platform, arch: process.arch, connectionAuthority: 'instance_discovery_only', credentialsExcluded: true},
        companionEntry: 'components/companion/dist/index.js', companionClosedBy: 'owning_baseline_only', clientShutdownOwnedByOuterRunner: true,
        permissionsGrantedByHelper: false, controlLeaseAcquired: false, serverAuthoritativeReadsRequested: 0, worldDiskReadsRequested: 0, chunkLoadRequested: false,
        inputDispatched: false, screenshotsUsed: false, stateStreamTested: false, physicalEscTested: false, persistentWriteCalls: 0
      };
    } catch (error) {primaryFailure = error; throw error;}
    finally {
      if (owned) {
        try {await owned.client.close();} catch (error) {cleanupFailure = error;}
        try {await owned.transport.close();} catch (error) {cleanupFailure ??= error;}
      }
      if (cleanupFailure) {
        actionSamples.push({case: 'mcp_world_baseline_cleanup_failure', error: String(cleanupFailure)});
        if (!primaryFailure) throw cleanupFailure;
        console.error(cleanupFailure);
      }
    }
    evidence.ownedCompanionClosed = true; evidence.elapsedMillis = now() - started; evidence.maximumMillis = MAXIMUM_MILLIS;
    actionSamples.push(evidence);
  });
  return evidence;
}
