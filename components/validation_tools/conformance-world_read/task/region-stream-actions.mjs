import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';

const TYPE = 'world.player.state';
const RELEASED = {type: 'input.released'};
const keyOf = value => `${value.x}:${value.y}:${value.z}`;
const integer = (value, label, minimum = 0) => assert(Number.isSafeInteger(value) && value >= minimum, 'Invalid ' + label);

export function decodeRegionPage(page, request, offset, expectedContext) {
  assert.equal(page.type, 'world.region'); assert.equal(page.available, true);
  assert.equal(page.domain, 'WORLD'); assert.equal(page.dataCategory, 'USER'); assert.equal(page.authority, 'CLIENT_KNOWN');
  assert.equal(page.source, 'bounded_client_world_model'); assert.equal(page.dataSource, 'LIVE');
  assert.equal(page.storageAccessed, false); assert.equal(page.chunkLoadRequested, false); assert.equal(page.crossPageAtomic, false);
  assert.equal(page.completeGameData, false); assert.equal(page.stateSemantics, 'per_page_live_client_cache_non_atomic');
  assert.equal(page.order, 'section_y_z_x_then_local_y_z_x'); assert.equal(page.unloadedPaletteIndex, -1);
  assert.equal(typeof page.contextId, 'string'); assert(page.contextId.length > 0); if (expectedContext) assert.equal(page.contextId, expectedContext);
  integer(page.sourceTick, 'sourceTick'); integer(page.sourceTickEnd, 'sourceTickEnd'); assert(page.sourceTickEnd >= page.sourceTick);
  integer(page.ownerBudgetNanos, 'ownerBudgetNanos', 1); assert(page.ownerBudgetNanos <= 8000000);
  integer(page.captureDurationNanos, 'captureDurationNanos');
  assert.deepEqual(page.region.min, request.min); assert.deepEqual(page.region.max, request.max);
  const volume = ['x', 'y', 'z'].reduce((total, axis) => total * (request.max[axis] - request.min[axis] + 1), 1);
  assert.equal(page.requestedCellCount, volume); assert.equal(page.pageStartOffset, offset);
  integer(page.pageCellCount, 'pageCellCount', 1); assert(page.pageCellCount <= request.maxBlocks);
  assert.equal(page.indices.length, page.pageCellCount); assert.equal(page.coveredCellCount, offset + page.pageCellCount);
  assert.equal(page.remainingCellCount, volume - page.coveredCellCount);
  assert.equal(page.complete, page.remainingCellCount === 0); assert.equal(page.truncated, !page.complete);
  assert.equal(page.maxBlocks, request.maxBlocks); assert.equal(page.maxBytes, request.maxBytes);
  const payload = {...page}; delete payload.requestId; delete payload.protocolVersion;
  assert(Buffer.byteLength(JSON.stringify(payload)) <= request.maxBytes, 'Region payload exceeded its byte budget');
  if (page.complete) assert(!('nextCursor' in page));
  else {assert.equal(typeof page.nextCursor, 'string'); assert(page.nextCursor.length > 0);}
  const {min, size} = page.section; integer(page.section.offset, 'section offset');
  for (const axis of ['x', 'y', 'z']) {
    integer(min[axis], 'section min ' + axis, Number.MIN_SAFE_INTEGER); integer(size[axis], 'section size ' + axis, 1); assert(size[axis] <= 16);
    assert(min[axis] >= request.min[axis] && min[axis] + size[axis] - 1 <= request.max[axis]);
    assert.equal(Math.floor(min[axis] / 16), page.section[axis]); assert.equal(Math.floor((min[axis] + size[axis] - 1) / 16), page.section[axis]);
  }
  assert(page.section.offset + page.pageCellCount <= size.x * size.y * size.z);
  for (const state of page.palette) {
    assert.deepEqual(Object.keys(state).sort(), ['block', 'properties']); assert.match(state.block, /^[a-z0-9_.-]+:[a-z0-9/._-]+$/);
    assert(state.properties && typeof state.properties === 'object' && !Array.isArray(state.properties));
    for (const [name, value] of Object.entries(state.properties)) {assert.match(name, /^[a-z0-9_]{1,64}$/); assert.equal(typeof value, 'string');}
  }
  const cells = page.indices.map((paletteIndex, index) => {
    integer(paletteIndex, 'palette index', -1); assert(paletteIndex < page.palette.length);
    const local = page.section.offset + index;
    return {x: min.x + local % size.x, y: min.y + Math.floor(local / (size.x * size.z)), z: min.z + Math.floor(local / size.x) % size.z,
      loaded: paletteIndex !== -1, ...(paletteIndex === -1 ? {} : page.palette[paletteIndex])};
  });
  assert.equal(page.pageUnloadedCellCount, cells.filter(value => !value.loaded).length); return cells;
}

export function validatePlayerStream(receipt, target, context, previous) {
  assert.equal(receipt.normalClose, true); assert.equal(receipt.exitCode, 0);
  assert.equal(receipt.controlLeaseHeaderSent, false); assert.equal(receipt.credentialsExcluded, true);
  assert.equal(receipt.hello?.type, 'event.hello'); assert.equal(receipt.hello.target, target);
  assert.equal(receipt.subscription?.type, 'event.subscription.accepted');
  assert.deepEqual(receipt.subscription.filter.types, [TYPE]); integer(receipt.subscription.sequence, 'subscription sequence');
  assert(Number.isFinite(receipt.collectMillis) && receipt.collectMillis >= 1000 && receipt.collectMillis <= 3000);
  const frames = receipt.messages.filter(value => value.event.type === TYPE);
  assert(frames.length > 0); let lastSequence = previous?.sequence ?? -1, lastTick = previous?.sourceTick ?? -1;
  for (const message of receipt.messages) assert(!['event.gap', 'event.error'].includes(message.event.type), 'Stream needs explicit resync after a gap/error');
  for (const {event} of frames) {
    assert.equal(event.dataCategory, 'USER'); assert.equal(event.authority, 'CLIENT_KNOWN'); assert.equal(event.domain, 'WORLD');
    assert.equal(event.source, 'client_tick_state'); assert.equal(event.stateStream, 'client_player_pose'); assert.equal(event.storageAccessed, false);
    assert.equal(event.serverAuthoritative, false); assert.deepEqual(event.streamContext, context);
    integer(event.sequence, 'event sequence'); integer(event.sourceTick, 'event source tick'); integer(event.sampledAtMillis, 'sample time');
    assert(event.sequence > lastSequence); assert(event.sourceTick > lastTick, 'Each delivered state must have one new native tick');
    lastSequence = event.sequence; lastTick = event.sourceTick;
    for (const field of ['x', 'y', 'z', 'yaw', 'pitch', 'velocityX', 'velocityY', 'velocityZ']) assert(Number.isFinite(event[field]), 'Invalid state ' + field);
    assert.equal(typeof event.onGround, 'boolean');
  }
  const liveFrames = frames.filter(value => value.afterSubscription === true && value.event.sequence > receipt.subscription.sequence);
  for (const frame of liveFrames) assert(Number.isFinite(frame.elapsedMillis) && frame.elapsedMillis >= 0 && frame.elapsedMillis <= receipt.collectMillis + 100);
  const firstSecondFrames = liveFrames.filter(value => value.elapsedMillis <= 1000);
  assert(firstSecondFrames.length >= 15, 'At least 15 newly published state frames are required in the first 1000ms, not replay or collection-tail frames');
  assert(liveFrames.at(-1).elapsedMillis - liveFrames[0].elapsedMillis >= 600, 'A replay burst cannot stand in for a one-second native sample');
  return {frames: frames.length, liveFrames: liveFrames.length, first: frames[0].event, last: frames.at(-1).event,
    firstSecondFrames: firstSecondFrames.length, firstSecondWindowMillis: 1000,
    uniqueSourceTicks: new Set(frames.map(value => value.event.sourceTick)).size, collectMillis: receipt.collectMillis,
    observedLiveSpanMillis: liveFrames.at(-1).elapsedMillis - liveFrames[0].elapsedMillis, minimumNewFrames: 15,
    nominalNativeTickRate: 20, crossMachineRateSla: false};
}

const STREAM_SCRIPT = `
$ErrorActionPreference = 'Stop'
$socket = [Net.WebSockets.ClientWebSocket]::new()
$deadline = [Threading.CancellationTokenSource]::new(15000)
try {
  $config = [Console]::ReadLine() | ConvertFrom-Json
  foreach ($header in $config.headers.PSObject.Properties) { $socket.Options.SetRequestHeader($header.Name, [string]$header.Value) }
  [void]$socket.ConnectAsync([Uri]$config.url, $deadline.Token).GetAwaiter().GetResult()
  $buffer = [byte[]]::new(65536)
  $frame = [IO.MemoryStream]::new()
  $pending = $null
  $watch = [Diagnostics.Stopwatch]::new()
  $subscribed = $false
  $received = 0
  while (-not $subscribed -or $watch.ElapsedMilliseconds -lt $config.collectMillis) {
    if ($null -eq $pending) { $pending = $socket.ReceiveAsync([ArraySegment[byte]]::new($buffer), $deadline.Token) }
    if (-not $pending.Wait(50)) { continue }
    $part = $pending.GetAwaiter().GetResult()
    $pending = $null
    if ($part.MessageType -ne [Net.WebSockets.WebSocketMessageType]::Text) { throw 'Unexpected WebSocket frame' }
    $frame.Write($buffer, 0, $part.Count)
    if ($frame.Length -gt 65536) { throw 'State message exceeded budget' }
    if (-not $part.EndOfMessage) { continue }
    $value = [Text.Encoding]::UTF8.GetString($frame.ToArray()) | ConvertFrom-Json
    $frame.SetLength(0)
    $received++
    if ($received -gt 256) { throw 'State collection exceeded budget' }
    if ($value.type -eq 'event.hello') {
      [Console]::WriteLine((@{kind='hello';value=$value} | ConvertTo-Json -Depth 15 -Compress))
      $command = [Text.Encoding]::UTF8.GetBytes('{"type":"event.subscribe","filter":{"types":["world.player.state"]}}')
      [void]$socket.SendAsync([ArraySegment[byte]]::new($command), [Net.WebSockets.WebSocketMessageType]::Text, $true, $deadline.Token).GetAwaiter().GetResult()
    } elseif ($value.type -eq 'event.subscription.accepted') {
      if ($subscribed) { throw 'Unexpected duplicate subscription receipt' }
      $subscribed = $true
      $watch.Start()
      [Console]::WriteLine((@{kind='subscription';value=$value} | ConvertTo-Json -Depth 15 -Compress))
    } else {
      [Console]::WriteLine((@{kind='event';event=$value;afterSubscription=$subscribed;elapsedMillis=$watch.ElapsedMilliseconds} | ConvertTo-Json -Depth 15 -Compress))
    }
  }
  $deadline.CancelAfter(5000)
  [void]$socket.CloseOutputAsync([Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'owned state collection complete', $deadline.Token).GetAwaiter().GetResult()
  do {
    if ($null -eq $pending) { $pending = $socket.ReceiveAsync([ArraySegment[byte]]::new($buffer), $deadline.Token) }
    $part = $pending.GetAwaiter().GetResult()
    $pending = $null
  } while ($part.MessageType -ne [Net.WebSockets.WebSocketMessageType]::Close)
  [Console]::WriteLine('{"kind":"closed","normalClose":true}')
} catch {
  [Console]::Error.WriteLine('Owned state WebSocket failed: ' + $_.Exception.GetType().Name)
  exit 1
} finally { $socket.Dispose(); $deadline.Dispose() }
`;

export async function collectPlayerStateEvents(connection, {collectMillis = 1250, resumeFromSequence} = {}) {
  assert(Number.isSafeInteger(collectMillis) && collectMillis >= 1000 && collectMillis <= 3000);
  const url = new URL('/v0/events?category=USER&type=' + TYPE, connection.base);
  assert(['http:', 'https:'].includes(url.protocol)); url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  if (resumeFromSequence !== undefined) {integer(resumeFromSequence, 'resume sequence'); url.searchParams.set('resumeFromSequence', String(resumeFromSequence));}
  assert.equal(typeof connection.token, 'string'); assert(connection.token.length > 0);
  const headers = Object.fromEntries(Object.entries(connection.headers ?? {}).map(([name, value]) => [name.toLowerCase(), value])
    .filter(([name]) => name !== 'x-mcp-control-lease'));
  headers.authorization = 'Bearer ' + connection.token;
  const child = spawn('pwsh', ['-NoLogo', '-NoProfile', '-NonInteractive', '-EncodedCommand', Buffer.from(STREAM_SCRIPT, 'utf16le').toString('base64')],
    {stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true});
  child.stdout.setEncoding('utf8'); child.stderr.resume();
  let pending = '', bytes = 0, hello, subscription, normalClose = false, failure; const messages = [];
  const completed = new Promise((resolve, reject) => {
    child.once('error', reject); child.stdin.on('error', error => {failure = error; child.kill();});
    child.stdout.on('data', data => {
      pending += data; bytes += Buffer.byteLength(data);
      if (bytes > 4194304) {failure = new Error('State collector output exceeded budget'); child.kill(); return;}
      let newline;
      while ((newline = pending.indexOf('\n')) >= 0) {
        const line = pending.slice(0, newline).trim(); pending = pending.slice(newline + 1); if (!line) continue;
        try {
          const value = JSON.parse(line);
          if (value.kind === 'hello') {assert(!hello); hello = value.value;}
          else if (value.kind === 'subscription') {assert(hello); assert(!subscription); subscription = value.value;}
          else if (value.kind === 'event') {assert(hello); messages.push({event: value.event, elapsedMillis: value.elapsedMillis, afterSubscription: value.afterSubscription});}
          else if (value.kind === 'closed') normalClose = value.normalClose === true;
          else throw new Error('Unknown collector output');
        } catch {failure = new Error('Invalid owned state collector response'); child.kill();}
      }
    });
    child.once('close', code => {
      if (failure) reject(failure);
      else if (code !== 0 || !normalClose || !hello || !subscription) reject(new Error('Owned state WebSocket did not complete normal collection/close'));
      else resolve({hello, subscription, messages, collectMillis, resumeFromSequence, normalClose, exitCode: code,
        transport: '.NET_ClientWebSocket', controlLeaseHeaderSent: false, credentialsExcluded: true});
    });
  });
  const timeout = setTimeout(() => {failure = new Error('State collection exceeded its bounded process deadline'); child.kill();}, 22000);
  child.stdin.end(JSON.stringify({url: url.href, headers, collectMillis}) + '\n');
  try {return await completed;} finally {clearTimeout(timeout); if (child.exitCode === null && child.signalCode === null) child.kill();}
}

export async function regionStreamActions({api, target, fixtureInput, connection, mode, acquire, release, click, clickNode, wait, nodes,
  check, actionSamples, forgetExpiredLease}, testServices) {
  const now = testServices?.now ?? (() => performance.now()), sleep = testServices?.sleep ?? (millis => new Promise(done => setTimeout(done, millis)));
  const collect = testServices?.collect ?? collectPlayerStateEvents, started = now(), deadline = started + 180000;
  let ownsLease = false, ownedLease, primaryFailure, cleanupFailure, stopEvidence, firstSummary, firstReceipt, streamContext, lastWorldRequest = -Infinity;
  const cursors = new Set(), regionRequestAttempts = [];
  const remaining = (max = 5000) => {const left = Math.floor(deadline - now()); assert(left > 0, 'Region/stream scenario exceeded 180 seconds'); return Math.min(max, left);};
  async function poll(read, predicate, label, max = 5000) {
    const end = now() + remaining(max); let value;
    do {value = await read(); if (predicate(value)) return value; await sleep(75);} while (now() < end);
    assert.fail(label + ': ' + JSON.stringify(value));
  }
  async function zero() {const value = await api('GET', '/v0/input/state'); assert.equal(value.pressedKeyCount, 0); assert.equal(value.pressedButtonCount, 0); return value;}
  async function readMode() {const value = await api('GET', '/v0/control/status'); assert.equal(value.mode, 'READ'); assert.equal(value.status, 'available'); assert(!value.leaseId); return value;}
  async function released() {
    const value = await api('POST', '/v0/wait/until', {condition: RELEASED, timeoutMs: remaining()});
    assert.equal(value.passed, true); assert.deepEqual(value.evidence, {pressedKeyCount: 0, pressedButtonCount: 0}); return value;
  }
  async function handBack() {
    if (!ownsLease) return;
    const value = await api('GET', '/v0/control/status');
    if (value.leaseId === ownedLease) {
      let failure;
      try {stopEvidence = await api('POST', '/v0/input/release-all', {}); await released(); await zero();} catch (error) {failure = error;}
      try {await release();} catch (error) {failure ??= error;}
      if (failure) throw failure;
    } else {assert.equal(value.mode, 'READ'); assert.equal(value.status, 'available'); assert(!value.leaseId); await released(); await zero(); forgetExpiredLease();}
    ownsLease = false; ownedLease = undefined;
  }
  async function worldSlot() {
    let pause = 35 - (now() - lastWorldRequest);
    while (pause > 0) {assert(pause < remaining()); await sleep(Math.ceil(pause)); pause = 35 - (now() - lastWorldRequest);}
    lastWorldRequest = now();
  }
  async function page(query, action = 'read', attempts = [], pageIndex) {
    const body = {category: 'USER', action, query: structuredClone(query)}; let lastFailure;
    for (let attemptInPage = 1; attemptInPage <= 3; attemptInPage++) {
      try {remaining(); await worldSlot(); remaining();} catch (error) {throw lastFailure ?? error;}
      const began = now(), record = {body: structuredClone(body), attemptInPage, requestIndex: regionRequestAttempts.length, startedOffsetMillis: began - started};
      let value;
      try {
        value = await api('POST', '/v0/world/region', structuredClone(body));
      } catch (error) {
        lastFailure = error;
        const runtimeError = error?.runtimeError, hint = runtimeError?.retryAfterMillis;
        const retryable = action === 'read' && error?.status === 429 && runtimeError?.error === 'WORLD_REGION_BUDGET_NO_PROGRESS'
          && Number.isSafeInteger(hint) && hint >= 1 && hint <= 1000;
        Object.assign(record, {outcome: retryable ? 'typed_no_progress' : 'failed',
          status: Number.isSafeInteger(error?.status) ? error.status : null, elapsedMillis: now() - began, retryScheduled: false,
          ...(runtimeError && typeof runtimeError === 'object' ? {error: structuredClone(runtimeError)} : {}),
          ...(retryable ? {retryAfterMillis: hint} : {})});
        attempts.push(record); regionRequestAttempts.push(record);
        if (!retryable || attemptInPage === 3) throw error;
        let budget; try {budget = remaining();} catch {throw error;}
        if (hint >= budget) throw error;
        const waitBegan = now(), wake = waitBegan + hint; record.retryScheduled = true;
        while (now() < wake) {
          const delay = Math.ceil(wake - now());
          try {budget = remaining();} catch {record.waitMillis = now() - waitBegan; throw error;}
          if (delay >= budget) {record.waitMillis = now() - waitBegan; throw error;}
          await sleep(delay);
        }
        record.waitMillis = now() - waitBegan;
        continue;
      }
      Object.assign(record, {outcome: 'success', status: 200, elapsedMillis: now() - began, retryScheduled: false,
        responseSha256: createHash('sha256').update(JSON.stringify(value)).digest('hex'),
        ...(pageIndex === undefined ? {} : {pageIndex})});
      attempts.push(record); regionRequestAttempts.push(record);
      if (query.cursor) cursors.delete(query.cursor); if (value.nextCursor) cursors.add(value.nextCursor); return value;
    }
    throw lastFailure;
  }
  async function rejectedCursor(cursor) {
    await worldSlot(); return api('POST', '/v0/world/region', {category: 'USER', query: {cursor}}, 'WORLD_REGION_CURSOR_INVALID');
  }
  async function readRegion(request, actor, maxPages) {
    const began = now(), firstRequestIndex = regionRequestAttempts.length, pages = [], cells = new Map(), requestAttempts = [];
    let query = request, covered = 0, unloaded = 0, firstCursor, consumedCursor;
    do {
      const value = await page(query, 'read', requestAttempts, pages.length), decoded = decodeRegionPage(value, request, covered, streamContext.opaqueId);
      assert.equal(value.dimension, actor.dimension);
      if (pages.length) assert(value.sourceTick >= pages.at(-1).sourceTickEnd, 'Native page source ticks moved backwards');
      unloaded += decoded.filter(cell => !cell.loaded).length; assert.equal(value.unloadedCellCount, unloaded);
      assert.equal(value.allLoaded, value.complete && unloaded === 0);
      if (query.cursor) consumedCursor ??= query.cursor; firstCursor ??= value.nextCursor;
      for (const cell of decoded) {const key = keyOf(cell); assert(!cells.has(key), 'Duplicate region cell: ' + key); cells.set(key, cell);}
      covered += decoded.length; pages.push(value);
      assert(pages.length <= maxPages, request.maxBlocks === 4096 ? '4096-cell pages must reduce the actual 64-cell baseline calls by at least half' : '64-cell baseline exceeded its bounded page count');
      if (value.complete) break; query = {cursor: value.nextCursor};
    } while (true);
    const expected = ['x', 'y', 'z'].reduce((total, axis) => total * (request.max[axis] - request.min[axis] + 1), 1);
    assert.equal(covered, expected); assert.equal(cells.size, expected);
    for (let y = request.min.y; y <= request.max.y; y++) for (let z = request.min.z; z <= request.max.z; z++) for (let x = request.min.x; x <= request.max.x; x++)
      assert(cells.has(`${x}:${y}:${z}`), 'Missing requested coordinate');
    return {pages, cells, covered, unloaded, firstCursor, consumedCursor, requestAttempts, actualRequests: requestAttempts.length,
      firstRequestIndex, lastRequestIndex: regionRequestAttempts.length - 1,
      elapsedMillis: now() - began, elapsedIncludesActiveAdmission: true, activeAdmissionIntervalMillis: 35};
  }
  async function matchingPoint(cell) {
    await worldSlot(); const point = await api('GET', `/v0/world/block?category=USER&x=${cell.x}&y=${cell.y}&z=${cell.z}`);
    assert.equal(point.dataCategory, 'USER'); assert.equal(point.authority, 'CLIENT_KNOWN'); assert.equal(point.chunkLoadRequested, false);
    assert.equal(point.storageAccessed, false); assert.equal(point.available, cell.loaded);
    if (cell.loaded) {
      assert.equal(point.block, cell.block);
      const match = /^Block\{([a-z0-9_.-]+:[a-z0-9/._-]+)\}(?:\[([^\]]*)\])?$/.exec(point.state); assert(match); assert.equal(match[1], cell.block);
      const properties = Object.fromEntries((match[2] ?? '').split(',').filter(Boolean).map(value => {
        const pair = /^([a-z0-9_]+)=([a-z0-9_.:/-]+)$/.exec(value); assert(pair); return pair.slice(1);
      }));
      assert.deepEqual(properties, cell.properties, 'USER point state properties differ from the reconstructed region');
    } else {assert.equal(point.reason, 'chunk_not_loaded'); assert(!('block' in point));}
    return {cell, point};
  }
  try {
    await check('region/stream uses only the restored copy and a stable diagnostic floor, then releases input to READ', async () => {
      assert.equal(fixtureInput?.policy, 'immutable_baseline_restored'); assert.equal(fixtureInput.target, target); assert.equal(fixtureInput.role, 'client');
      assert.equal(fixtureInput.credentialsCopied, false); assert.match(fixtureInput.baselineSha256, /^[0-9a-f]{64}$/); assert.equal(fixtureInput.restoredSha256, fixtureInput.baselineSha256);
      await mode('READ'); await zero(); const title = await api('GET', '/v0/ui/tree'); assert(title.screenClass.endsWith('TitleScreen')); assert(!title.overlayIdentity);
      await acquire(); ownsLease = true; const status = await api('GET', '/v0/control/status'); assert.equal(status.mode, 'TAKEOVER'); ownedLease = status.leaseId; assert.equal(typeof ownedLease, 'string');
      await click('menu.singleplayer', 'Singleplayer'); const list = await wait(value => value.screenClass.endsWith('SelectWorldScreen'), 'region fixed world list', remaining(20000));
      const row = nodes(list).find(value => value.worldName === 'World-Read-Alpha'); assert(row); await clickNode(row); await click('selectWorld.select', 'Play Selected World');
      await wait(value => !value.screenClass, 'region fixed world loaded', remaining(60000)); await handBack(); await mode('OPERATE');
      const original = await api('GET', '/v0/player?category=USER'); assert.equal(original.available, true);
      const x = Math.floor(original.x), y = Math.floor(original.y), z = Math.floor(original.z), receipts = [];
      for (const command of [`fill ${x - 2} ${y - 1} ${z - 2} ${x + 2} ${y - 1} ${z + 2} minecraft:stone`,
        `fill ${x - 2} ${y} ${z - 2} ${x + 2} ${y + 3} ${z + 2} minecraft:air`, 'gamemode survival @s', `tp @s ${x + .5} ${y} ${z + .5} 0 0`]) {
        const receipt = await api('POST', '/v0/command/player', {command, route: 'NATIVE'});
        assert.equal(receipt.route, 'NATIVE'); assert.equal(receipt.inputDispatched, false); assert.equal(receipt.effectConfirmed, false); receipts.push(receipt);
      }
      const settled = await poll(() => api('GET', '/v0/player?category=USER'), value => value.available && value.onGround === true
        && Math.abs(value.x - x - .5) < .02 && Math.abs(value.y - y) < .02 && Math.abs(value.z - z - .5) < .02
        && Math.hypot(value.velocityX, value.velocityZ) < .001, 'Diagnostic client player did not settle');
      await mode('READ'); await readMode(); await zero();
      const context = await api('GET', '/v0/input/state'); assert.equal(context.inputContextAvailable, true);
      streamContext = {generation: context.inputContextGeneration, opaqueId: context.inputContextId};
      actionSamples.push({case: 'region_stream_fixture', fixtureInput, worldName: row.worldName, original, receipts, settled, context,
        arrangeAuthority: 'diagnostic_current_player_NATIVE_commands', arrangeIsGameplayAcceptance: false, stopEvidence});
    });
    await check('region pages reconstruct every requested cell once, reduce calls, agree with USER points and reject consumed/cancelled cursors', async () => {
      await readMode(); const inputBefore = await zero(), actor = await api('GET', '/v0/player?category=USER');
      const min = {x: Math.floor(actor.x) - 8, y: Math.floor(actor.y) - 1, z: Math.floor(actor.z) - 8};
      const request = {min, max: {x: min.x + 15, y: min.y + 15, z: min.z + 15}, maxBlocks: 4096, maxBytes: 131072};
      let regionMaximumDistance = 0;
      for (const x of [min.x, request.max.x]) for (const y of [min.y, request.max.y]) for (const z of [min.z, request.max.z])
        regionMaximumDistance = Math.max(regionMaximumDistance, Math.hypot(x + .5 - actor.x, y + .5 - actor.y, z + .5 - actor.z));
      assert(regionMaximumDistance <= 32);
      const baselineRequest = {...request, maxBlocks: 64}, baseline = await readRegion(baselineRequest, actor, 256), optimized = await readRegion(request, actor, 32);
      const {pages, cells, covered, unloaded, firstCursor, consumedCursor} = optimized;
      assert(baseline.actualRequests / optimized.actualRequests >= 2, 'Current 4096-cell budget did not halve the actual current 64-cell request count');
      for (const [key, cell] of cells) assert.deepEqual(cell, baseline.cells.get(key), 'Same current region changed between page budgets: ' + key);
      const samples = [0, 1023, 2048, 3071, 4095].map(index => [...cells.values()][index]), points = [];
      for (const cell of samples) points.push(await matchingPoint(cell));
      const pathNodes = [], pathPoints = [], pathStarted = now();
      for (let row = 0; row < 5; row++) for (let column = 0; column < 11; column++) pathNodes.push({
        x: min.x + (row % 2 === 0 ? column : 10 - column), y: Math.floor(actor.y), z: min.z + row});
      assert.equal(new Set(pathNodes.map(keyOf)).size, 55);
      for (let index = 1; index < pathNodes.length; index++) assert.equal(Math.abs(pathNodes[index].x - pathNodes[index - 1].x) + Math.abs(pathNodes[index].z - pathNodes[index - 1].z), 1);
      let maximumDistance = 0;
      for (const node of pathNodes) for (const [layer, dy] of [['support', -1], ['feet', 0], ['head', 1]]) {
        const at = {...node, y: node.y + dy}, key = keyOf(at), cell = cells.get(key); assert(cell); assert.deepEqual(cell, baseline.cells.get(key));
        maximumDistance = Math.max(maximumDistance, Math.hypot(at.x + .5 - actor.x, at.y + .5 - actor.y, at.z + .5 - actor.z));
        pathPoints.push({layer, ...await matchingPoint(cell)});
      }
      assert.equal(pathPoints.length, 165); assert.equal(new Set(pathPoints.map(value => keyOf(value.cell))).size, 165); assert(maximumDistance <= 32);
      const path = {kind: 'bounded_semantic_query_plan_not_player_traversal', nodes: pathNodes, nodeCount: 55, horizontalStepCount: 54,
        layers: ['support', 'feet', 'head'], points: pathPoints, uniqueCoordinates: 165, actualPointHttpRequests: pathPoints.length,
        baselineRegionHttpRequests: baseline.actualRequests, optimizedRegionHttpRequests: optimized.actualRequests, additionalRegionRequestsForPath: 0,
        pointToOptimizedRequestRatio: pathPoints.length / optimized.actualRequests,
        pathRequestedCells: 165, regionRequestedCells: covered, regionOverfetchCellRatio: covered / 165, regionIncludesCellsNotOnPath: true,
        queryBounds: {min: {x: min.x, y: Math.floor(actor.y) - 1, z: min.z}, max: {x: min.x + 10, y: Math.floor(actor.y) + 1, z: min.z + 4}},
        horizontalSpan: {x: 11, z: 5}, maximumCellCenterDistance: maximumDistance, userRadius: 32,
        elapsedMillis: now() - pathStarted, actualTraversalTested: false, pathfindingClaimed: false, historicalPointPathMeasured: false};
      let consumedRejected;
      if (consumedCursor) consumedRejected = await rejectedCursor(consumedCursor);
      const pending = await page({...request, maxBlocks: 1}); assert.equal(pending.complete, false); assert(pending.nextCursor);
      const advanced = await page({cursor: pending.nextCursor}); assert(advanced.nextCursor);
      const rotatedRejected = await rejectedCursor(pending.nextCursor);
      const cancelled = await page({cursor: advanced.nextCursor}, 'cancel'); assert.equal(cancelled.type, 'world.region.cancel'); assert.equal(cancelled.cancelled, true);
      const twice = await page({cursor: advanced.nextCursor}, 'cancel'); assert.equal(twice.cancelled, false);
      const cancelledRejected = await rejectedCursor(advanced.nextCursor);
      const inputAfter = await zero(); assert.equal(inputAfter.inputDispatchSequence, inputBefore.inputDispatchSequence); await readMode();
      actionSamples.push({case: 'region_stream_region', request, pages, points, covered, unloaded, uniqueCoordinates: cells.size, firstCursor, consumedRejected,
        pending, advanced, rotatedRejected, cancelled, twice, cancelledRejected, inputBefore, inputAfter,
        baseline: {...baseline, cells: undefined, request: baselineRequest}, optimized: {...optimized, cells: undefined, request}, path,
        comparisonBasis: 'current_candidate_same_region_region_api_64_vs_4096_page_budget', historicalApiMeasured: false,
        regionMaximumCellCenterDistance: regionMaximumDistance, userRadius: 32,
        baselineRegionCalls: baseline.actualRequests, actualRegionCalls: optimized.actualRequests, activeAdmissionIntervalMillis: 35,
        callReductionRatio: baseline.actualRequests / optimized.actualRequests, crossPageAtomic: false, readLoadRequested: false, debugReadUsed: false});
    });
    await check('USER state stream publishes at least 15 unique new native ticks around one second and quantifies stopped GET error', async () => {
      await readMode(); const before = await api('GET', '/v0/player?category=USER');
      firstReceipt = await collect(connection, {collectMillis: 1250}); firstSummary = validatePlayerStream(firstReceipt, target, streamContext);
      const after = await api('GET', '/v0/player?category=USER'); assert.equal(before.onGround, true); assert.equal(after.onGround, true);
      for (const value of [before, after]) {assert.equal(value.dataCategory, 'USER'); assert.equal(value.authority, 'CLIENT_KNOWN');}
      const errors = Object.fromEntries(['x', 'y', 'z', 'yaw', 'pitch', 'velocityX', 'velocityY', 'velocityZ'].map(name => [name, Math.abs(after[name] - firstSummary.last[name])]));
      for (const name of ['x', 'y', 'z']) {assert(Math.abs(after[name] - before[name]) <= .02, 'Stopped player moved during collection'); assert(errors[name] <= .02);}
      assert(Object.values(errors).every(Number.isFinite)); await readMode(); await zero();
      actionSamples.push({case: 'region_stream_state', receipt: firstReceipt, summary: firstSummary, before, after, stoppedGetAbsoluteErrors: errors,
        errorUnits: {position: 'blocks', angles: 'native_degrees', velocity: 'native_client_velocity'}, noControlLeaseUsed: true, serverEffectConfirmed: false});
    });
    await check('read-only stream reconnect resumes ordered newer ticks in the same real context without acquiring input', async () => {
      await readMode(); const before = await zero();
      const firstFrames = firstReceipt.messages.filter(value => value.event.type === TYPE); assert(firstFrames.length >= 2);
      const resumeBase = firstFrames.at(-2).event, knownLast = firstFrames.at(-1).event;
      const receipt = await collect(connection, {collectMillis: 1250, resumeFromSequence: resumeBase.sequence});
      const summary = validatePlayerStream(receipt, target, streamContext, resumeBase), after = await zero();
      const replayed = receipt.messages.filter(value => value.event.type === TYPE && value.event.sequence === knownLast.sequence);
      assert.equal(replayed.length, 1, 'Reconnect must actually replay the known final cached state exactly once');
      assert.deepEqual(replayed[0].event, knownLast, 'Known cached state was not replayed verbatim'); assert(knownLast.sequence <= receipt.subscription.sequence);
      assert.equal(receipt.resumeFromSequence, resumeBase.sequence); assert.equal(after.inputContextId, streamContext.opaqueId);
      assert.equal(after.inputContextGeneration, streamContext.generation); assert.equal(after.inputDispatchSequence, before.inputDispatchSequence); await readMode();
      actionSamples.push({case: 'region_stream_resume', previousLast: firstSummary.last, resumeBase, replayedKnownEvent: replayed[0].event,
        replayedKnownSequence: knownLast.sequence, knownReplayMatched: true, receipt, summary, before, after,
        noAutoAcquire: true, sameContext: true, replayGapResyncExercised: false});
    });
  } catch (error) {primaryFailure = error; throw error;}
  finally {
    for (const cursor of cursors) try {await api('POST', '/v0/world/region', {category: 'USER', action: 'cancel', query: {cursor}});} catch (error) {cleanupFailure ??= error;}
    try {await handBack();} catch (error) {cleanupFailure ??= error;}
    actionSamples.push({case: 'region_stream_requests', requestAttempts: regionRequestAttempts,
      scope: 'page_helper_calls_excludes_expected_cursor_errors_and_final_cleanup',
      boundedRetryCode: 'WORLD_REGION_BUDGET_NO_PROGRESS', maximumAttemptsPerPage: 3, maximumRetryHintMillis: 1000});
    if (cleanupFailure) {actionSamples.push({case: 'region_stream_cleanup_failure', error: String(cleanupFailure)}); if (!primaryFailure) throw cleanupFailure;}
  }
  const finalInput = await zero(), finalMode = await readMode(); assert(now() - started <= 180000);
  const result = {case: 'region_stream_complete', target, checks: 4, elapsedMillis: now() - started, maximumMillis: 180000,
    evidenceKind: testServices ? 'synthetic_test_not_runtime' : 'runtime_monotonic', liveEvidence: !testServices,
    finalInput, finalMode, physicalEscTested: false, continuousWorldRegionSubscriptionClaimed: false,
    clientShutdownOwnedByOuterRunner: true, persistentWriteCalls: 0}; actionSamples.push(result); return result;
}
