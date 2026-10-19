import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {createServer} from 'node:http';
import {once} from 'node:events';
import test from 'node:test';
import {collectPlayerStateEvents, decodeRegionPage, regionStreamActions, validatePlayerStream} from './region-stream-actions.mjs';

const TARGET = '26.3-neoforge', TYPE = 'world.player.state', CONTEXT = {generation: 1, opaqueId: 'native-world-player-1'};
const pose = (sequence, x = .5, y = 65, z = .5) => ({type: TYPE, domain: 'WORLD', stateStream: 'client_player_pose', streamContext: {...CONTEXT},
  dataCategory: 'USER', authority: 'CLIENT_KNOWN', source: 'client_tick_state', storageAccessed: false, serverAuthoritative: false,
  sourceTick: sequence + 100, sampledAtMillis: 100000 + sequence * 50, sequence, x, y, z, yaw: 0, pitch: 0, onGround: true,
  velocityX: 0, velocityY: -.0784, velocityZ: 0});
const streamReceipt = (after = 0, count = 20) => ({hello: {type: 'event.hello', target: TARGET, sequence: after},
  subscription: {type: 'event.subscription.accepted', sequence: after, filter: {types: [TYPE]}},
  messages: Array.from({length: count}, (_, index) => ({event: pose(after + index + 1), elapsedMillis: (index + 1) * 50, afterSubscription: true})),
  collectMillis: 1250, resumeFromSequence: after || undefined, normalClose: true, exitCode: 0, controlLeaseHeaderSent: false, credentialsExcluded: true});

function slices(request) {
  const output = []; let start = 0;
  for (let y = Math.floor(request.min.y / 16); y <= Math.floor(request.max.y / 16); y++)
    for (let z = Math.floor(request.min.z / 16); z <= Math.floor(request.max.z / 16); z++)
      for (let x = Math.floor(request.min.x / 16); x <= Math.floor(request.max.x / 16); x++) {
        const min = {x: Math.max(request.min.x, x * 16), y: Math.max(request.min.y, y * 16), z: Math.max(request.min.z, z * 16)};
        const size = {x: Math.min(request.max.x, x * 16 + 15) - min.x + 1, y: Math.min(request.max.y, y * 16 + 15) - min.y + 1,
          z: Math.min(request.max.z, z * 16 + 15) - min.z + 1};
        output.push({x, y, z, min, size, start, cells: size.x * size.y * size.z}); start += size.x * size.y * size.z;
      }
  return output;
}

function nativePage(request, offset = 0, cellSource = () => ({loaded: true, block: 'minecraft:air', properties: {}}), cap = Infinity) {
  const slice = slices(request).find(value => offset < value.start + value.cells); assert(slice);
  const local = offset - slice.start, count = Math.min(request.maxBlocks, cap, slice.cells - local), indices = [], palette = [];
  for (let index = 0; index < count; index++) {
    const at = local + index, x = slice.min.x + at % slice.size.x, z = slice.min.z + Math.floor(at / slice.size.x) % slice.size.z,
      y = slice.min.y + Math.floor(at / (slice.size.x * slice.size.z)), cell = cellSource(x, y, z);
    if (!cell.loaded) indices.push(-1);
    else {
      const entry = {block: cell.block, properties: cell.properties}; let entryIndex = palette.findIndex(value => JSON.stringify(value) === JSON.stringify(entry));
      if (entryIndex < 0) {entryIndex = palette.length; palette.push(entry);} indices.push(entryIndex);
    }
  }
  const volume = ['x', 'y', 'z'].reduce((total, axis) => total * (request.max[axis] - request.min[axis] + 1), 1), complete = offset + count === volume;
  let unloaded = 0;
  for (const part of slices(request)) for (let index = 0; index < part.cells && part.start + index < offset + count; index++) {
    const x = part.min.x + index % part.size.x, z = part.min.z + Math.floor(index / part.size.x) % part.size.z,
      y = part.min.y + Math.floor(index / (part.size.x * part.size.z)); if (!cellSource(x, y, z).loaded) unloaded++;
  }
  return {type: 'world.region', domain: 'WORLD', available: true, dataCategory: 'USER', authority: 'CLIENT_KNOWN', storageAccessed: false,
    source: 'bounded_client_world_model', dataSource: 'LIVE', dimension: 'minecraft:overworld',
    chunkLoadRequested: false, crossPageAtomic: false, completeGameData: false, stateSemantics: 'per_page_live_client_cache_non_atomic',
    order: 'section_y_z_x_then_local_y_z_x', unloadedPaletteIndex: -1, contextId: CONTEXT.opaqueId, sourceTick: 100 + offset, sourceTickEnd: 101 + offset,
    captureDurationNanos: 1000000, ownerBudgetNanos: 8000000, region: {min: request.min, max: request.max},
    section: {x: slice.x, y: slice.y, z: slice.z, min: slice.min, size: slice.size, offset: local}, palette, indices,
    pageStartOffset: offset, pageCellCount: count, pageUnloadedCellCount: indices.filter(value => value === -1).length, unloadedCellCount: unloaded,
    allLoaded: complete && unloaded === 0,
    requestedCellCount: volume, coveredCellCount: offset + count, remainingCellCount: volume - offset - count,
    complete, truncated: !complete, maxBlocks: request.maxBlocks, maxBytes: request.maxBytes, ...(!complete ? {nextCursor: 'cursor-' + (offset + count)} : {})};
}

test('region decoder reconstructs negative/cross-section positions and preserves unloaded vs known air', () => {
  const request = {min: {x: -2, y: -1, z: -2}, max: {x: 2, y: 1, z: 2}, maxBlocks: 7, maxBytes: 131072}, cells = new Map();
  let covered = 0;
  do {
    const page = nativePage(request, covered, x => x === -1 ? {loaded: false} : {loaded: true, block: 'minecraft:air', properties: {}});
    const decoded = decodeRegionPage(page, request, covered, CONTEXT.opaqueId);
    for (const cell of decoded) {const key = `${cell.x}:${cell.y}:${cell.z}`; assert(!cells.has(key)); cells.set(key, cell);}
    covered += decoded.length;
  } while (covered < 75);
  assert.equal(cells.size, 75); assert.equal(cells.get('-1:0:0').loaded, false); assert(!('block' in cells.get('-1:0:0')));
  assert.equal(cells.get('0:0:0').block, 'minecraft:air');
});

test('region metadata, coverage, palette and native boundaries fail closed', () => {
  const request = {min: {x: 0, y: 64, z: 0}, max: {x: 15, y: 79, z: 15}, maxBlocks: 4096, maxBytes: 131072};
  for (const edit of [value => {value.dataCategory = 'DEBUG';}, value => {value.storageAccessed = true;}, value => {value.chunkLoadRequested = true;},
    value => {value.crossPageAtomic = true;}, value => {value.contextId = 'wrong-context';}, value => {value.pageStartOffset++;},
    value => {value.coveredCellCount--;}, value => {value.indices[0] = 20;}, value => {value.indices[0] = -2;},
    value => {value.palette[0].nbt = {};}, value => {value.section.size.x = 17;}, value => {value.sourceTickEnd = -1;},
    value => {value.complete = false;}, value => {value.pageUnloadedCellCount++;}, value => {value.ownerBudgetNanos = 9000000;}]) {
    const page = nativePage(request); edit(page); assert.throws(() => decodeRegionPage(page, request, 0, CONTEXT.opaqueId));
  }
});

test('region byte budget excludes only HTTP envelope fields and still rejects oversized real payload', () => {
  const request = {min: {x: 0, y: 64, z: 0}, max: {x: 15, y: 79, z: 15}, maxBlocks: 4096, maxBytes: 16384};
  const page = nativePage(request), wrapped = {...page, requestId: 'request-'.repeat(3000), protocolVersion: 'v0'.repeat(3000)};
  assert(Buffer.byteLength(JSON.stringify(page)) < request.maxBytes);
  assert(Buffer.byteLength(JSON.stringify(wrapped)) > request.maxBytes);
  assert.doesNotThrow(() => decodeRegionPage(wrapped, request, 0, CONTEXT.opaqueId));
  assert.equal(wrapped.requestId, 'request-'.repeat(3000)); assert.equal(wrapped.protocolVersion, 'v0'.repeat(3000));
  const oversized = nativePage(request);
  for (let index = 0; index < 8; index++) oversized.palette.push({block: 'test:large_' + index,
    properties: Object.fromEntries(Array.from({length: 20}, (_, field) => ['flag_' + field, 'x'.repeat(128)]))});
  assert(Buffer.byteLength(JSON.stringify(oversized)) > request.maxBytes);
  assert.throws(() => decodeRegionPage(oversized, request, 0, CONTEXT.opaqueId), /Region payload exceeded/);
});

test('state validation demands at least 15 new unique native ticks, not replay or same-tick/burst claims', () => {
  const valid = validatePlayerStream(streamReceipt(), TARGET, CONTEXT);
  assert.equal(valid.liveFrames, 20); assert.equal(valid.firstSecondFrames, 20); assert.equal(valid.firstSecondWindowMillis, 1000);
  for (const edit of [value => {value.messages.pop(); value.messages = value.messages.slice(0, 14);},
    value => {value.subscription.sequence = 100;}, value => {value.messages[1].event.sourceTick = value.messages[0].event.sourceTick;},
    value => {value.messages[1].event.sequence = value.messages[0].event.sequence;}, value => {value.messages.forEach(frame => frame.elapsedMillis = 0);},
    value => {value.messages[2].event.streamContext.opaqueId = 'other';}, value => {value.messages[2].event.serverAuthoritative = true;},
    value => {value.messages[2].event.dataCategory = 'DEBUG';}, value => {value.messages[2].event.x = NaN;},
    value => {value.messages.push({event: {type: 'event.gap'}});}, value => {value.normalClose = false;},
    value => {value.subscription.filter.types = [];}, value => {value.hello.target = 'another-target';}, value => {value.controlLeaseHeaderSent = true;}]) {
    const receipt = streamReceipt(); edit(receipt); assert.throws(() => validatePlayerStream(receipt, TARGET, CONTEXT));
  }
  const first = validatePlayerStream(streamReceipt(), TARGET, CONTEXT);
  assert.doesNotThrow(() => validatePlayerStream(streamReceipt(20), TARGET, CONTEXT, first.last));
  assert.throws(() => validatePlayerStream(streamReceipt(19), TARGET, CONTEXT, first.last));
});

test('1250ms collection tail cannot make up fewer than 15 newly delivered frames in the first second', () => {
  const receipt = streamReceipt();
  receipt.messages.forEach((frame, index) => {frame.elapsedMillis = index < 14 ? (index + 1) * 50 : 1050 + (index - 14) * 30;});
  assert.equal(receipt.messages.length, 20); assert.equal(receipt.messages.filter(value => value.elapsedMillis <= 1000).length, 14);
  assert(receipt.messages.at(-1).elapsedMillis <= 1250);
  assert.throws(() => validatePlayerStream(receipt, TARGET, CONTEXT), /first 1000ms/);
});

function simulation(options = {}) {
  let milliseconds = 0, mode = 'READ', lease, callerLease, sequence = 0, generation = 0, screen = 'TitleScreen', cursorNumber = 0, releases = 0, stops = 0;
  const calls = [], samples = [], checks = [], cursors = new Map(), collectors = [], fills = [], sleeps = [];
  const player = {available: true, dataCategory: 'USER', authority: 'CLIENT_KNOWN', x: .5, y: 65, z: .5, yaw: 0, pitch: 0,
    onGround: true, velocityX: 0, velocityY: -.0784, velocityZ: 0, dimension: 'minecraft:overworld'};
  const input = () => ({pressedKeyCount: 0, pressedButtonCount: 0, inputDispatchSequence: sequence, inputGeneration: generation,
    inputCleanupPending: false, inputCleanupFailed: false, inputContextAvailable: !screen, inputContextId: CONTEXT.opaqueId, inputContextGeneration: CONTEXT.generation});
  const status = () => ({mode, status: lease ? 'active' : 'available', ...(lease ? {leaseId: lease} : {}), reconsentRequired: false});
  const cell = (x, y, z) => {
    if (options.unloaded && x === -8) return {loaded: false};
    const fill = fills.findLast(value => x >= value.min.x && x <= value.max.x && y >= value.min.y && y <= value.max.y && z >= value.min.z && z <= value.max.z);
    return {loaded: true, block: fill?.block ?? 'minecraft:air', properties: {}};
  };
  const api = async (method, path, body, expectedError) => {
    calls.push({method, path, body: body && structuredClone(body), expectedError, at: milliseconds}); milliseconds += 5;
    if (method === 'GET' && path === '/v0/input/state') return input();
    if (method === 'GET' && ['/v0/control/status', '/v0/control/mode'].includes(path)) return status();
    if (method === 'GET' && path === '/v0/ui/tree') return {screenClass: screen, overlayIdentity: 0};
    if (method === 'GET' && path === '/v0/player?category=USER') return {...player};
    if (method === 'POST' && path === '/v0/input/release-all') {assert.equal(lease, callerLease); stops++; sequence++; generation++; return {type: 'input.release_all', inputGeneration: generation};}
    if (method === 'POST' && path === '/v0/wait/until') return {passed: true, condition: body.condition, evidence: {pressedKeyCount: 0, pressedButtonCount: 0}};
    if (method === 'POST' && path === '/v0/command/player') {
      assert.equal(mode, 'OPERATE'); assert(!lease); assert.equal(body.route, 'NATIVE'); const parts = body.command.split(' ');
      if (parts[0] === 'fill') {const [x, y, z, a, b, c] = parts.slice(1, 7).map(Number); fills.push({min: {x, y, z}, max: {x: a, y: b, z: c}, block: parts[7]});}
      if (parts[0] === 'tp') {const [x, y, z, yaw, pitch] = parts.slice(2).map(Number); Object.assign(player, {x, y, z, yaw, pitch});}
      return {route: 'NATIVE', inputDispatched: false, effectConfirmed: false};
    }
    if (method === 'POST' && path === '/v0/world/region') {
      assert.equal(mode, 'READ'); assert(!lease); assert.equal(body.category, 'USER');
      const advance = duration => {milliseconds += duration;};
      if (body.action === 'cancel') {
        const failure = options.regionFailure?.({action: 'cancel', body, advance}); if (failure) throw failure;
        const removed = cursors.delete(body.query.cursor); return {type: 'world.region.cancel', dataCategory: 'USER', cancelled: removed};
      }
      let request = body.query, offset = 0;
      if (request.cursor) {
        const current = cursors.get(request.cursor);
        if (!current) {assert.equal(expectedError, 'WORLD_REGION_CURSOR_INVALID'); return {error: expectedError};}
        request = current.request; offset = current.offset;
      }
      assert(!expectedError);
      const failure = options.regionFailure?.({action: 'read', body, request, offset, advance}); if (failure) throw failure;
      if (body.query.cursor) cursors.delete(body.query.cursor);
      const source = options.baselineMismatch && request.maxBlocks === 64 ? (x, y, z) => {
        const value = cell(x, y, z); return value.loaded ? {...value, properties: {unexpected: 'changed'}} : value;
      } : cell;
      const page = nativePage(request, offset, source, options.pageCap ?? Infinity);
      if (options.duplicatePage && offset > 0) page.section.offset = 0;
      if (!page.complete) {page.nextCursor = 'owned-cursor-' + ++cursorNumber; cursors.set(page.nextCursor, {request, offset: page.coveredCellCount});}
      return page;
    }
    if (method === 'GET' && path.startsWith('/v0/world/block?')) {
      const query = new URL('http://owned' + path).searchParams, x = Number(query.get('x')), y = Number(query.get('y')), z = Number(query.get('z')), value = cell(x, y, z);
      const pathMismatch = options.pathPointMismatch && x === -8 && y === 65 && z === -8;
      return {available: value.loaded, dataCategory: 'USER', authority: 'CLIENT_KNOWN', storageAccessed: false, chunkLoadRequested: false,
        ...(value.loaded ? {block: options.pointMismatch || pathMismatch ? 'minecraft:dirt' : value.block, state: 'Block{' + value.block + '}'} : {reason: 'chunk_not_loaded'})};
    }
    assert.fail('Unexpected fake route ' + method + ' ' + path);
  };
  const fixtureInput = {policy: 'immutable_baseline_restored', target: TARGET, role: 'client', credentialsCopied: false, baselineSha256: 'a'.repeat(64), restoredSha256: 'a'.repeat(64)};
  const env = {api, target: TARGET, fixtureInput, connection: {base: 'http://127.0.0.1:9999', token: 'never-publish-token'},
    mode: async value => {assert(!lease); mode = value;}, acquire: async () => {lease = callerLease = 'own-lease'; mode = 'TAKEOVER';},
    release: async () => {assert.equal(lease, callerLease); releases++; lease = callerLease = undefined; mode = 'READ';},
    click: async key => {if (key === 'menu.singleplayer') screen = 'SelectWorldScreen'; else if (key === 'selectWorld.select') screen = ''; else assert.fail('Unknown button');},
    clickNode: async node => assert.equal(node.worldName, 'World-Read-Alpha'), wait: async predicate => {const tree = {screenClass: screen}; assert(predicate(tree)); return tree;},
    nodes: () => [{worldName: 'World-Read-Alpha', nodeId: 'owned-world'}], check: async (name, callback) => {await callback(); checks.push(name);}, actionSamples: samples,
    forgetExpiredLease: () => {assert(!lease); assert.equal(mode, 'READ');}};
  const services = {now: () => milliseconds, sleep: async duration => {
    const actual = options.earlySleep ? Math.max(.5, duration / 2) : duration;
    sleeps.push({requested: duration, actual}); milliseconds += actual;
  }, collect: async (connection, config) => {
    assert.equal(mode, 'READ'); assert(!lease); collectors.push(config); milliseconds += config.collectMillis;
    const isResume = config.resumeFromSequence !== undefined;
    const receipt = streamReceipt(isResume ? 20 : 0, options.lowStreamRate ? 14 : 20);
    if (isResume && !options.resumeNoReplay) {
      const known = pose(20); if (options.resumeAlteredReplay) known.x += 1;
      receipt.messages.unshift({event: known, elapsedMillis: 0, afterSubscription: false});
    }
    receipt.resumeFromSequence = config.resumeFromSequence;
    if (options.collectFailure) throw new Error('owned collector failed');
    if (options.resumeOtherContext && collectors.length === 2) receipt.messages[0].event.streamContext.opaqueId = 'different-context';
    return receipt;
  }};
  return {env, services, calls, samples, checks, cursors, collectors, sleeps, counters: () => ({lease, mode, releases, stops})};
}

function regionFailure(code = 'WORLD_REGION_BUDGET_NO_PROGRESS', hint = 50, status = 429) {
  return Object.assign(new Error('Structured region failure'), {status, runtimeError: {error: code, retryAfterMillis: hint}});
}

function pageTrace(fixture) {
  const trace = fixture.samples.find(value => value.case === 'region_stream_requests'); assert(trace);
  assert.equal(trace.scope, 'page_helper_calls_excludes_expected_cursor_errors_and_final_cleanup'); return trace.requestAttempts;
}

test('synthetic full flow proves 4096 distinct cells, point agreement, cursor retirement and two READ-only streams', async () => {
  const fixture = simulation({unloaded: true}), result = await regionStreamActions(fixture.env, fixture.services);
  assert.equal(result.liveEvidence, false); assert.equal(result.evidenceKind, 'synthetic_test_not_runtime'); assert.equal(result.checks, 4);
  assert.equal(fixture.checks.length, 4); assert.equal(result.persistentWriteCalls, 0); assert.equal(fixture.cursors.size, 0);
  const region = fixture.samples.find(value => value.case === 'region_stream_region');
  assert.equal(region.covered, 4096); assert.equal(region.uniqueCoordinates, 4096); assert.equal(region.actualRegionCalls, 4); assert.equal(region.callReductionRatio, 16);
  assert.equal(region.points.length, 5); assert.equal(region.rotatedRejected.error, 'WORLD_REGION_CURSOR_INVALID');
  assert.equal(region.baselineRegionCalls, 64); assert.equal(region.baseline.request.maxBlocks, 64); assert.equal(region.optimized.request.maxBlocks, 4096);
  for (const read of [region.baseline, region.optimized]) {
    assert.equal(read.actualRequests, read.requestAttempts.length);
    assert.deepEqual(read.requestAttempts, pageTrace(fixture).slice(read.firstRequestIndex, read.lastRequestIndex + 1));
    for (const attempt of read.requestAttempts) {
      assert.equal(attempt.outcome, 'success'); assert.equal(attempt.attemptInPage, 1); assert.equal(attempt.retryScheduled, false);
      assert.equal(attempt.responseSha256, createHash('sha256').update(JSON.stringify(read.pages[attempt.pageIndex])).digest('hex'));
    }
  }
  assert.equal(region.historicalApiMeasured, false); assert.equal(region.path.nodeCount, 55); assert.equal(region.path.uniqueCoordinates, 165);
  assert.equal(region.path.actualPointHttpRequests, 165); assert.equal(region.path.horizontalStepCount, 54); assert.equal(region.path.additionalRegionRequestsForPath, 0);
  assert.equal(region.path.pathRequestedCells, 165); assert.equal(region.path.regionRequestedCells, 4096); assert(region.path.regionOverfetchCellRatio > 1);
  assert.equal(region.path.regionIncludesCellsNotOnPath, true); assert(region.regionMaximumCellCenterDistance <= 32);
  assert(region.path.maximumCellCenterDistance <= 32); assert.equal(region.path.actualTraversalTested, false); assert.equal(region.path.historicalPointPathMeasured, false);
  assert.equal(region.cancelled.cancelled, true); assert.equal(region.twice.cancelled, false);
  const resume = fixture.samples.find(value => value.case === 'region_stream_resume'); assert.equal(resume.summary.first.sequence, resume.previousLast.sequence);
  assert.equal(resume.resumeBase.sequence, 19); assert.equal(resume.knownReplayMatched, true); assert.deepEqual(resume.replayedKnownEvent, pose(20));
  assert.deepEqual(fixture.collectors, [{collectMillis: 1250}, {collectMillis: 1250, resumeFromSequence: 19}]);
  assert.equal(fixture.counters().releases, 1); assert.equal(fixture.counters().stops, 1); assert.equal(fixture.counters().mode, 'READ');
  assert(!JSON.stringify(fixture.samples).includes('never-publish-token'));
  const worldCalls = fixture.calls.filter(value => value.path === '/v0/world/region' || value.path.startsWith('/v0/world/block?'));
  for (let index = 1; index < worldCalls.length; index++) assert(worldCalls[index].at - worldCalls[index - 1].at >= 35);
});

test('explicit no-progress responses retry initial and continuation queries unchanged and count all real requests', async () => {
  const failed = new Set();
  const fixture = simulation({earlySleep: true, regionFailure: ({action, body, request}) => {
    if (action !== 'read') return;
    const key = request.maxBlocks + (body.query.cursor ? ':continuation' : ':initial');
    if (failed.has(key)) return; failed.add(key); return regionFailure();
  }});
  await regionStreamActions(fixture.env, fixture.services);
  const region = fixture.samples.find(value => value.case === 'region_stream_region');
  assert.equal(region.baselineRegionCalls, 66); assert.equal(region.actualRegionCalls, 6); assert.equal(region.callReductionRatio, 11);
  assert.equal(region.path.baselineRegionHttpRequests, 66); assert.equal(region.path.optimizedRegionHttpRequests, 6);
  assert.equal(region.path.pointToOptimizedRequestRatio, 165 / 6); assert.equal(region.path.actualPointHttpRequests, 165);
  for (const read of [region.baseline, region.optimized]) {
    assert.equal(read.actualRequests, read.pages.length + 2);
    assert.deepEqual(read.requestAttempts, pageTrace(fixture).slice(read.firstRequestIndex, read.lastRequestIndex + 1));
    const failures = read.requestAttempts.filter(value => value.outcome === 'typed_no_progress'); assert.equal(failures.length, 2);
    for (const failure of failures) {
      assert.equal(failure.status, 429); assert.deepEqual(failure.error, {error: 'WORLD_REGION_BUDGET_NO_PROGRESS', retryAfterMillis: 50});
      assert.equal(failure.retryAfterMillis, 50); assert.equal(failure.retryScheduled, true); assert(failure.waitMillis >= 50);
      assert(!('pageIndex' in failure)); assert(!('responseSha256' in failure));
      const next = read.requestAttempts[read.requestAttempts.indexOf(failure) + 1]; assert.deepEqual(next.body, failure.body);
      assert.equal(next.attemptInPage, 2); assert(next.startedOffsetMillis >= failure.startedOffsetMillis + failure.elapsedMillis + failure.waitMillis);
      assert.equal(next.outcome, 'success'); assert.equal(next.responseSha256, createHash('sha256').update(JSON.stringify(read.pages[next.pageIndex])).digest('hex'));
    }
  }
  const measured = [...region.baseline.requestAttempts, ...region.optimized.requestAttempts];
  assert.equal(measured.length, 72); assert.equal(measured.filter(value => value.outcome === 'typed_no_progress').length, 4);
  assert(fixture.sleeps.some(value => value.actual < value.requested)); assert.equal(fixture.cursors.size, 0);
});

test('three total failed attempts preserve the last structured error instead of producing a page', async () => {
  const failures = [], fixture = simulation({regionFailure: ({action}) => {
    if (action !== 'read') return;
    const error = regionFailure(); failures.push(error); return error;
  }});
  await assert.rejects(regionStreamActions(fixture.env, fixture.services), error => error === failures.at(-1));
  assert.equal(failures.length, 3); const attempts = pageTrace(fixture); assert.equal(attempts.length, 3);
  assert.deepEqual(attempts.map(value => value.attemptInPage), [1, 2, 3]);
  assert.deepEqual(attempts.map(value => value.retryScheduled), [true, true, false]);
  assert(attempts.every(value => value.outcome === 'typed_no_progress')); assert.deepEqual(attempts[0].body, attempts[2].body);
  assert(!fixture.samples.some(value => value.case === 'region_stream_region')); assert.equal(fixture.collectors.length, 0);
});

test('a continuation that exhausts no-progress retries remains owned until explicit cleanup', async () => {
  const failures = [], fixture = simulation({regionFailure: ({action, body}) => {
    if (action !== 'read' || !body.query.cursor) return;
    const error = regionFailure(); failures.push(error); return error;
  }});
  await assert.rejects(regionStreamActions(fixture.env, fixture.services), error => error === failures.at(-1));
  const attempts = pageTrace(fixture); assert.equal(attempts.length, 4); assert.equal(attempts[0].outcome, 'success');
  const cursor = attempts[0].body.query.cursor ?? fixture.calls.find(value => value.body?.query?.cursor).body.query.cursor;
  for (const failure of attempts.slice(1)) assert.equal(failure.body.query.cursor, cursor);
  const cleanup = fixture.calls.filter(value => value.body?.action === 'cancel'); assert.equal(cleanup.length, 1);
  assert.equal(cleanup[0].body.query.cursor, cursor); assert.equal(fixture.cursors.size, 0);
});

test('invalid hints, other quotas, transport uncertainty, stale context and cancellation never retry', async () => {
  const errors = [
    ...[0, 1001, -1, 1.5, '50', NaN, undefined].map(hint => {
      const error = regionFailure(); error.runtimeError.retryAfterMillis = hint; return error;
    }),
    regionFailure('WORLD_REGION_BUSY'), regionFailure('EXPENSIVE_OPERATION_RATE_LIMITED'),
    regionFailure('WORLD_REGION_CURSOR_INVALID', 50, 409), regionFailure('WORLD_REGION_CONTEXT_CHANGED', 50, 409),
    regionFailure('REQUEST_CANCELLED', 50, 409), regionFailure('WORLD_REGION_BUDGET_NO_PROGRESS', 50, 503),
    Object.assign(new Error('Aborted'), {name: 'AbortError'}), new TypeError('fetch failed'),
    new Error('{"error":"WORLD_REGION_BUDGET_NO_PROGRESS","retryAfterMillis":50}')
  ];
  for (const error of errors) {
    const fixture = simulation({regionFailure: ({action}) => action === 'read' ? error : undefined});
    await assert.rejects(regionStreamActions(fixture.env, fixture.services), actual => actual === error);
    const attempts = pageTrace(fixture); assert.equal(attempts.length, 1); assert.equal(attempts[0].retryScheduled, false);
    assert.equal(attempts[0].outcome, 'failed'); assert(!('waitMillis' in attempts[0]));
    assert.equal(fixture.collectors.length, 0);
  }
});

test('cancel actions do not retry even the otherwise eligible no-progress response', async () => {
  const error = regionFailure(); let failed = false;
  const fixture = simulation({regionFailure: ({action}) => {
    if (action === 'cancel' && !failed) {failed = true; return error;}
  }});
  await assert.rejects(regionStreamActions(fixture.env, fixture.services), actual => actual === error);
  const cancellations = pageTrace(fixture).filter(value => value.body.action === 'cancel'); assert.equal(cancellations.length, 1);
  assert.equal(cancellations[0].attemptInPage, 1); assert.equal(cancellations[0].outcome, 'failed'); assert.equal(cancellations[0].retryScheduled, false);
  assert.equal(fixture.cursors.size, 0, 'Finally cleanup must still own the cursor after a failed cancel');
});

test('insufficient scenario deadline preserves the structured failure without retrying', async () => {
  const error = regionFailure(), fixture = simulation({regionFailure: ({action, advance}) => {
    if (action === 'read') {advance(179999); return error;}
  }});
  await assert.rejects(regionStreamActions(fixture.env, fixture.services), actual => actual === error);
  const attempts = pageTrace(fixture); assert.equal(attempts.length, 1); assert.equal(attempts[0].retryScheduled, false);
});

test('invalid restored receipt admits no instance operations', async () => {
  const fixture = simulation(); fixture.env.fixtureInput.credentialsCopied = true;
  await assert.rejects(regionStreamActions(fixture.env, fixture.services)); assert.equal(fixture.calls.length, 0);
});

test('64-cell pages do not meet the promised reduction and any known remaining cursor is cancelled', async () => {
  const fixture = simulation({pageCap: 64}); await assert.rejects(regionStreamActions(fixture.env, fixture.services), /reduce the actual 64-cell baseline/);
  assert.equal(fixture.cursors.size, 0); assert.equal(fixture.collectors.length, 0);
});

test('a live USER point mismatch fails rather than relabeling non-atomic data as consistent', async () => {
  const fixture = simulation({pointMismatch: true}); await assert.rejects(regionStreamActions(fixture.env, fixture.services));
  assert.equal(fixture.cursors.size, 0); assert.equal(fixture.collectors.length, 0); assert(!fixture.samples.some(value => value.case === 'region_stream_region'));
});

test('equal block IDs with different properties between current page budgets fail full-region equivalence', async () => {
  const fixture = simulation({baselineMismatch: true}); await assert.rejects(regionStreamActions(fixture.env, fixture.services), /Same current region changed/);
  assert.equal(fixture.cursors.size, 0); assert.equal(fixture.collectors.length, 0);
});

test('all 55 path nodes require independent floor/feet/head point agreement, not just the five region samples', async () => {
  const fixture = simulation({pathPointMismatch: true}); await assert.rejects(regionStreamActions(fixture.env, fixture.services));
  assert.equal(fixture.collectors.length, 0); assert(!fixture.samples.some(value => value.case === 'region_stream_region'));
});

test('repeated coordinates from an incorrect section continuation cannot count as coverage', async () => {
  const fixture = simulation({pageCap: 128, duplicatePage: true}); await assert.rejects(regionStreamActions(fixture.env, fixture.services), /Duplicate region cell/);
  assert.equal(fixture.cursors.size, 0); assert.equal(fixture.collectors.length, 0);
});

test('fewer than 15 state frames fails instead of relaxing the nominal native sample requirement', async () => {
  const fixture = simulation({lowStreamRate: true}); await assert.rejects(regionStreamActions(fixture.env, fixture.services), /At least 15/);
  assert.equal(fixture.collectors.length, 1); assert(!fixture.samples.some(value => value.case === 'region_stream_state'));
});

test('a resumed different context is rejected without any new acquire or input', async () => {
  const fixture = simulation({resumeOtherContext: true}); await assert.rejects(regionStreamActions(fixture.env, fixture.services));
  assert.equal(fixture.counters().releases, 1); assert(!fixture.samples.some(value => value.case === 'region_stream_resume'));
});

test('reconnecting with only fresh frames does not pretend the known cached last frame was replayed', async () => {
  const fixture = simulation({resumeNoReplay: true}); await assert.rejects(regionStreamActions(fixture.env, fixture.services), /actually replay the known/);
  assert(!fixture.samples.some(value => value.case === 'region_stream_resume'));
});

test('a known sequence with altered payload is not accepted as the genuine cached event', async () => {
  const fixture = simulation({resumeAlteredReplay: true}); await assert.rejects(regionStreamActions(fixture.env, fixture.services), /not replayed verbatim/);
  assert(!fixture.samples.some(value => value.case === 'region_stream_resume'));
});

test('collector failure propagates without retry and READ stays input-free', async () => {
  const fixture = simulation({collectFailure: true}); await assert.rejects(regionStreamActions(fixture.env, fixture.services), /owned collector failed/);
  assert.equal(fixture.collectors.length, 1); assert.equal(fixture.counters().lease, undefined); assert.equal(fixture.counters().releases, 1);
});

test('real .NET state bridge explicitly subscribes and collects/ closes without sending a Control Lease', {skip: process.platform !== 'win32', timeout: 20000}, async () => {
  const server = createServer(), sockets = new Set(), receivedHeaders = []; const intervals = new Set(); let sequence = 0;
  const send = (socket, value) => {
    const bytes = Buffer.from(JSON.stringify(value)), header = bytes.length < 126 ? Buffer.from([0x81, bytes.length]) : Buffer.from([0x81, 126, 0, 0]);
    if (bytes.length >= 126) header.writeUInt16BE(bytes.length, 2); socket.write(Buffer.concat([header, bytes]));
  };
  server.on('upgrade', (request, socket) => {
    sockets.add(socket); socket.on('error', () => {}); socket.on('close', () => sockets.delete(socket)); receivedHeaders.push(request.headers);
    const hash = createHash('sha1').update(request.headers['sec-websocket-key'] + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64');
    socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ' + hash + '\r\n\r\n');
    send(socket, {type: 'event.hello', target: TARGET, sequence}); let buffered = Buffer.alloc(0), interval;
    socket.on('data', data => {
      buffered = Buffer.concat([buffered, data]);
      while (buffered.length >= 6) {
        const opcode = buffered[0] & 15; let length = buffered[1] & 127, at = 2;
        if (length === 126) {if (buffered.length < 8) return; length = buffered.readUInt16BE(2); at += 2;}
        assert(length < 65536); if (buffered.length < at + 4 + length) return;
        const mask = buffered.subarray(at, at + 4), body = Buffer.from(buffered.subarray(at + 4, at + 4 + length));
        for (let index = 0; index < body.length; index++) body[index] ^= mask[index % 4]; buffered = buffered.subarray(at + 4 + length);
        if (opcode === 8) {if (interval) clearInterval(interval); socket.end(Buffer.from([0x88, 2, 3, 232])); return;}
        if (opcode !== 1) continue;
        const command = JSON.parse(body); assert.deepEqual(command, {type: 'event.subscribe', filter: {types: [TYPE]}});
        send(socket, {type: 'event.subscription.accepted', sequence, filter: {types: [TYPE]}});
        interval = setInterval(() => send(socket, pose(++sequence)), 50); intervals.add(interval);
      }
    });
  });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  try {
    const receipt = await collectPlayerStateEvents({base: 'http://127.0.0.1:' + server.address().port, token: 'owned-mock-token',
      headers: {authorization: 'Bearer caller-token', Authorization: 'Bearer different-case-caller-token',
        'x-mcp-runtime-instance': 'own-mock-instance', 'X-MCP-Control-Lease': 'must-not-be-sent', 'x-mcp-control-lease': 'also-must-not-be-sent'}}, {collectMillis: 1250});
    assert.equal(receivedHeaders[0].authorization, 'Bearer owned-mock-token'); assert.equal(receivedHeaders[0]['x-mcp-runtime-instance'], 'own-mock-instance');
    assert(!('x-mcp-control-lease' in receivedHeaders[0])); assert.equal(receipt.controlLeaseHeaderSent, false);
    assert(validatePlayerStream(receipt, TARGET, CONTEXT).liveFrames >= 15); assert.equal(receipt.exitCode, 0);
  } finally {
    for (const interval of intervals) clearInterval(interval); for (const socket of sockets) socket.destroy(); await new Promise(resolve => server.close(resolve));
  }
});
