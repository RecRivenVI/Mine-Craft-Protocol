import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';

const RELEASED = {type: 'input.released'};
const TERMINAL = new Set(['completed', 'failed', 'cancelled']);
const KEY = (key, action) => ({type: 'key', key, action, scanCode: 0, modifiers: 0});

// Reuse the validation suite's .NET WebSocket library; credentials travel only over stdin.
const SOCKET_SCRIPT = `
$ErrorActionPreference = 'Stop'
$socket = [Net.WebSockets.ClientWebSocket]::new()
$deadline = [Threading.CancellationTokenSource]::new(10000)
try {
  $config = [Console]::ReadLine() | ConvertFrom-Json
  foreach ($header in $config.headers.PSObject.Properties) { $socket.Options.SetRequestHeader($header.Name, [string]$header.Value) }
  [void]$socket.ConnectAsync([Uri]$config.url, $deadline.Token).GetAwaiter().GetResult()
  $buffer = [byte[]]::new(16384)
  $hello = [IO.MemoryStream]::new()
  do {
    $part = $socket.ReceiveAsync([ArraySegment[byte]]::new($buffer), $deadline.Token).GetAwaiter().GetResult()
    if ($part.MessageType -ne [Net.WebSockets.WebSocketMessageType]::Text) { throw 'Expected event hello text' }
    $hello.Write($buffer, 0, $part.Count)
    if ($hello.Length -gt 65536) { throw 'Event hello exceeded budget' }
  } while (-not $part.EndOfMessage)
  $value = [Text.Encoding]::UTF8.GetString($hello.ToArray()) | ConvertFrom-Json
  if ($value.type -ne 'event.hello') { throw 'Expected event.hello' }
  [Console]::WriteLine((@{state='ready';hello=$value} | ConvertTo-Json -Depth 12 -Compress))
  $request = [Console]::ReadLine() | ConvertFrom-Json
  if ($request.action -ne 'close') { throw 'Expected explicit close' }
  $deadline.CancelAfter(5000)
  [void]$socket.CloseOutputAsync([Net.WebSockets.WebSocketCloseStatus]::NormalClosure, 'owned context validation complete', $deadline.Token).GetAwaiter().GetResult()
  do { $part = $socket.ReceiveAsync([ArraySegment[byte]]::new($buffer), $deadline.Token).GetAwaiter().GetResult() }
  while ($part.MessageType -ne [Net.WebSockets.WebSocketMessageType]::Close)
  [Console]::WriteLine('{"state":"closed","normalClose":true}')
} catch {
  [Console]::Error.WriteLine('Owned control WebSocket failed: ' + $_.Exception.GetType().Name)
  exit 1
} finally { $socket.Dispose(); $deadline.Dispose() }
`;

export async function openLeaseBoundEventSocket(connection, leaseId, budget = 12000) {
  assert.equal(typeof leaseId, 'string'); assert(leaseId.length > 0);
  assert.equal(typeof connection.token, 'string'); assert(connection.token.length > 0);
  const url = new URL('/v0/events?category=USER', connection.base);
  assert(['http:', 'https:'].includes(url.protocol)); url.protocol = url.protocol === 'https:' ? 'wss:' : 'ws:';
  const headers = Object.fromEntries(Object.entries(connection.headers ?? {}).map(([name, value]) => [name.toLowerCase(), value]));
  headers.authorization = 'Bearer ' + connection.token; headers['x-mcp-control-lease'] = leaseId;
  const child = spawn('pwsh', ['-NoLogo', '-NoProfile', '-NonInteractive', '-EncodedCommand', Buffer.from(SOCKET_SCRIPT, 'utf16le').toString('base64')],
    {stdio: ['pipe', 'pipe', 'pipe'], windowsHide: true});
  child.stdout.setEncoding('utf8');
  let output = '', ready, closed = false, closeRequested = false, childFailure, complete;
  const exit = new Promise((resolve, reject) => {
    child.once('error', error => {childFailure = error; reject(error);});
    child.once('close', (code, signal) => {
      if (code === 0 && closed && closeRequested) resolve({exitCode: code, normalClose: true});
      else reject(new Error('Owned control WebSocket exited without normal close: ' + String(code ?? signal)));
    });
  });
  // The rejection is observed again by close/abort; attach now to avoid an orphan rejection.
  exit.catch(() => {});
  const hello = new Promise((resolve, reject) => {
    ready = {resolve, reject};
    child.stdout.on('data', bytes => {
      output += bytes.toString();
      if (output.length > 131072) {reject(new Error('Owned WebSocket output exceeded budget')); child.kill(); return;}
      let newline;
      while ((newline = output.indexOf('\n')) >= 0) {
        const line = output.slice(0, newline).trim(); output = output.slice(newline + 1);
        if (!line) continue;
        try {
          const message = JSON.parse(line);
          if (message.state === 'ready') {assert.equal(message.hello.type, 'event.hello'); resolve(message.hello);}
          else if (message.state === 'closed') closed = message.normalClose === true;
        } catch {reject(new Error('Invalid owned WebSocket bridge response')); child.kill();}
      }
    });
    // Do not surface arbitrary child output, which may contain local connection information.
    child.stderr.resume(); child.once('error', reject);
    child.once('close', () => reject(childFailure ?? new Error('Owned WebSocket ended before hello')));
  });
  const timer = setTimeout(() => {ready.reject(new Error('Owned control WebSocket hello timed out')); child.kill();}, budget);
  child.stdin.on('error', error => ready.reject(error));
  child.stdin.write(JSON.stringify({url: url.href, headers}) + '\n');
  try { complete = await hello; }
  catch (error) {child.kill(); await exit.catch(() => {}); throw error;}
  finally {clearTimeout(timer);}
  return {
    hello: complete,
    async close() {
      assert(!closeRequested, 'Control WebSocket close must not be replayed'); closeRequested = true;
      child.stdin.end('{"action":"close"}\n');
      const timeout = setTimeout(() => child.kill(), 7000);
      try {return await exit;} finally {clearTimeout(timeout);}
    },
    async abort() {if (child.exitCode === null && child.signalCode === null) child.kill(); await exit.catch(() => {});}
  };
}

export function inputContextSnapshot(value, available) {
  for (const name of ['inputContextGeneration', 'inputGeneration', 'inputDispatchSequence', 'pressedKeyCount', 'pressedButtonCount'])
    assert(Number.isSafeInteger(value?.[name]) && value[name] >= 0, 'Invalid actual input snapshot: ' + name);
  assert.equal(typeof value.inputContextId, 'string'); assert(value.inputContextId.length > 0);
  assert.equal(typeof value.inputContextAvailable, 'boolean');
  if (available !== undefined) assert.equal(value.inputContextAvailable, available);
  return value;
}

export function assertInputContextAdvanced(before, after) {
  inputContextSnapshot(before, true); inputContextSnapshot(after, true);
  assert(after.inputContextGeneration > before.inputContextGeneration, 'Native context did not advance');
  assert.notEqual(after.inputContextId, before.inputContextId, 'Native context opaque identity was reused');
  assert(after.inputGeneration > before.inputGeneration, 'Native replacement did not fence old input');
  assert.equal(after.pressedKeyCount, 0); assert.equal(after.pressedButtonCount, 0);
  assert.equal(after.inputCleanupPending, false); assert.equal(after.inputCleanupFailed, false);
}

export async function inputContextActions({api, target, fixtureInput, connection, mode, acquire, release, click, clickNode,
  wait, nodes, check, actionSamples, forgetExpiredLease}, testServices) {
  const now = testServices?.now ?? (() => performance.now());
  const sleep = testServices?.sleep ?? (millis => new Promise(done => setTimeout(done, millis)));
  const openSocket = testServices?.openSocket ?? openLeaseBoundEventSocket;
  const started = now(), deadline = started + 240000;
  let ownsLease = false, ownedLease, lastPost = -Infinity, socket, primaryFailure, cleanupFailure;
  const remaining = (maximum = 5000) => {const left = Math.floor(deadline - now()); assert(left > 0, 'Input context exceeded 240 seconds'); return Math.min(maximum, left);};
  const input = async (zero = false) => {
    remaining(); const value = inputContextSnapshot(await api('GET', '/v0/input/state'));
    if (zero) {assert.equal(value.pressedKeyCount, 0); assert.equal(value.pressedButtonCount, 0);} return value;
  };
  async function poll(read, predicate, label, maximum = 5000) {
    const end = now() + remaining(maximum); let value;
    do {value = await read(); if (predicate(value)) return value; await sleep(75);} while (now() < end);
    assert.fail(label + ': ' + JSON.stringify(value));
  }
  async function released() {
    const value = await api('POST', '/v0/wait/until', {condition: RELEASED, timeoutMs: remaining()});
    assert.equal(value.passed, true); assert.deepEqual(value.condition, RELEASED);
    assert.deepEqual(value.evidence, {pressedKeyCount: 0, pressedButtonCount: 0}); return value;
  }
  async function statusSame(expected) {
    const value = await api('GET', '/v0/control/status');
    assert.equal(value.leaseId, ownedLease); assert.equal(value.mode, 'TAKEOVER'); assert.equal(value.status, 'active');
    assert.equal(value.reconsentRequired, false); if (expected) assert.deepEqual(value.modeVersion, expected.modeVersion); return value;
  }
  async function start(steps, options = {}) {
    let pause = 550 - (now() - lastPost);
    while (pause > 0) {assert(pause < remaining()); await sleep(Math.ceil(pause)); pause = 550 - (now() - lastPost);}
    lastPost = now(); const value = await api('POST', '/v0/pipelines', {steps, timeoutMs: 20000, ...options});
    assert.equal(typeof value.operationId, 'string'); return value;
  }
  async function terminal(operation, expected = 'completed') {
    return poll(() => api('POST', `/v0/operations/${operation.operationId}/wait`, {timeoutMs: remaining(1000)}), value => {
      if (!TERMINAL.has(value.state)) {assert.equal(value.status, 'running'); return false;}
      assert.equal(value.state, expected, JSON.stringify(value)); return true;
    }, 'Operation did not reach ' + expected, 8000);
  }
  async function tap(code) {
    const outcome = await terminal(await start([{type: 'key.tap', key: code, holdMs: 40}]));
    await released(); await input(true); return outcome;
  }
  async function observeStable(before) {
    const after = await input(true);
    assert.equal(after.inputContextGeneration, before.inputContextGeneration); assert.equal(after.inputContextId, before.inputContextId);
    assert.equal(after.inputContextAvailable, true); return after;
  }
  async function negativeWindow(after) {
    assert(remaining() >= 500); await sleep(500); const later = await input(true);
    assert.equal(later.inputDispatchSequence, after.inputDispatchSequence, 'Old input arrived after cleanup'); return later;
  }
  async function gameplayReady(expected) {
    const observations = []; let previous;
    const ready = await poll(async () => {
      const before = await input(true), tree = await api('GET', '/v0/ui/tree'), after = await input(true);
      for (const state of [before, after]) {
        assert.equal(state.inputContextId, expected.inputContextId, 'Native context changed while waiting for gameplay readiness');
        assert.equal(state.inputContextGeneration, expected.inputContextGeneration);
        assert.equal(state.inputGeneration, expected.inputGeneration, 'Input generation changed while waiting for gameplay readiness');
        assert.equal(state.inputContextAvailable, true);
        assert(Number.isSafeInteger(state.clientTick) && state.clientTick >= 0, 'Missing actual native input tick');
      }
      assert(Number.isSafeInteger(tree.clientTick) && tree.clientTick >= before.clientTick && tree.clientTick <= after.clientTick,
        'UI readiness needs an ordered actual native frame');
      assert(Number.isSafeInteger(tree.screenRevision) && tree.screenRevision >= 0, 'Missing actual native screen revision');
      const frame = {before, after, ui: {clientTick: tree.clientTick, screenClass: tree.screenClass,
        screenIdentity: tree.screenIdentity, screenRevision: tree.screenRevision, overlayIdentity: tree.overlayIdentity}};
      observations.push(frame);
      const unobstructed = tree.screenClass === '' && tree.screenIdentity === 0 && tree.overlayIdentity === 0
        && [before, after].every(value => value.inputCleanupPending === false && value.inputCleanupFailed === false);
      if (!unobstructed) {previous = undefined; return null;}
      if (previous && tree.clientTick > previous.ui.clientTick && tree.screenRevision === previous.ui.screenRevision)
        return {observations, stableNativeFrames: [previous, frame], targetInputContextId: expected.inputContextId,
          targetInputContextGeneration: expected.inputContextGeneration, condition: 'two_distinct_native_frames_no_screen_no_overlay_cleanup_complete'};
      previous = frame; return null;
    }, value => value !== null, 'Destination gameplay UI did not become ready on two native frames', 10000);
    return ready;
  }
  async function newShift(expected) {
    const readiness = await gameplayReady(expected);
    const before = await input(true);
    assert.equal(before.inputContextId, expected.inputContextId); assert.equal(before.inputContextGeneration, expected.inputContextGeneration);
    assert.equal(before.inputGeneration, expected.inputGeneration); assert.equal(before.inputContextAvailable, true);
    assert.equal(before.inputCleanupPending, false); assert.equal(before.inputCleanupFailed, false);
    const outcome = await terminal(await start([{type: 'key.tap', key: 340, holdMs: 40}]));
    await released(); const after = await input(true); assert(after.inputDispatchSequence >= before.inputDispatchSequence + 3);
    return {readiness, before, outcome, after};
  }
  async function nativeCommand(command) {
    const receipt = await api('POST', '/v0/command/player', {command, route: 'NATIVE'});
    assert.equal(receipt.route, 'NATIVE'); assert.equal(receipt.inputDispatched, false); assert.equal(receipt.effectConfirmed, false); return receipt;
  }
  async function boundary(destination) {
    const leaseBefore = await statusSame(), before = await input(true);
    const active = await start([KEY(340, 1), {type: 'delay', durationMs: 15000}, {type: 'key.tap', key: 65, holdMs: 40}], {cleanupOnComplete: false});
    const held = await poll(() => input(), value => value.pressedKeyCount === 1 && value.pressedKeys?.includes(340), 'Shift never became held');
    const queued = await start([{type: 'key.tap', key: 68, holdMs: 40}]);
    const queuedState = await input(); assert.equal(queuedState.inputDispatchSequence, held.inputDispatchSequence);
    const activeBefore = await api('GET', `/v0/operations/${active.operationId}`);
    assert.equal(activeBefore.status, 'running'); assert(!TERMINAL.has(activeBefore.state));
    const receipt = await nativeCommand(`execute in ${destination.dimension} run tp @s ${destination.x} ${destination.y} ${destination.z}`);
    const player = await poll(() => api('GET', '/v0/player?category=USER'), value => value.available === true
      && value.dimension === destination.dimension && [value.x, value.y, value.z].every(Number.isFinite)
      && Math.abs(value.x - destination.x) < 2 && Math.abs(value.y - destination.y) < 2 && Math.abs(value.z - destination.z) < 2,
    'Actual USER destination was not received', 20000);
    assert.equal(player.dataCategory, 'USER');
    const outcomes = [await terminal(active, 'cancelled'), await terminal(queued, 'cancelled')];
    const waited = await released();
    const after = await poll(() => input(true), value => value.inputContextAvailable && !value.inputCleanupPending && !value.inputCleanupFailed,
      'Native boundary cleanup did not finish');
    assertInputContextAdvanced(before, after); const later = await negativeWindow(after);
    const leaseAfter = await statusSame(leaseBefore), freshInput = await newShift(after); await statusSame(leaseBefore);
    return {before, active, held, queued, queuedState, activeBefore, receipt, player, outcomes, waited, after, later, leaseBefore, leaseAfter, freshInput,
      negativeObservationMillis: 500, sameLeasePreserved: true, oldInputWasCancelledNotReplayed: true};
  }
  try {
    await check('input context uses a verified fixed copy and ordinary Pause/Inventory do not replace the native epoch', async () => {
      assert.equal(fixtureInput?.policy, 'immutable_baseline_restored'); assert.equal(fixtureInput.target, target);
      assert.equal(fixtureInput.role, 'client'); assert.equal(fixtureInput.credentialsCopied, false);
      assert.match(fixtureInput.baselineSha256, /^[0-9a-f]{64}$/); assert.equal(fixtureInput.restoredSha256, fixtureInput.baselineSha256);
      await mode('READ'); const titleInput = await input(true), title = await api('GET', '/v0/ui/tree');
      assert(title.screenClass.endsWith('TitleScreen')); assert(!title.overlayIdentity);
      await acquire(); ownsLease = true; const acquired = await api('GET', '/v0/control/status');
      assert.equal(acquired.status, 'active'); assert.equal(acquired.mode, 'TAKEOVER'); assert.equal(typeof acquired.leaseId, 'string'); ownedLease = acquired.leaseId;
      await click('menu.singleplayer', 'Singleplayer'); const list = await wait(value => value.screenClass.endsWith('SelectWorldScreen'), 'context fixed world list', remaining(20000));
      const row = nodes(list).find(value => value.worldName === 'World-Read-Alpha'); assert(row);
      await clickNode(row); await click('selectWorld.select', 'Play Selected World');
      await wait(value => !value.screenClass, 'context fixed world entered', remaining(60000));
      const inWorld = await poll(() => input(true), value => value.inputContextAvailable && !value.inputCleanupPending, 'Native world epoch unavailable', 15000);
      assert(inWorld.inputContextGeneration > titleInput.inputContextGeneration); assert.notEqual(inWorld.inputContextId, titleInput.inputContextId);
      const screens = [];
      for (const [code, suffix] of [[256, 'PauseScreen'], [256, ''], [69, 'InventoryScreen'], [256, '']]) {
        const outcome = await tap(code); const tree = await wait(value => suffix ? value.screenClass.endsWith(suffix) : !value.screenClass, 'context ordinary ' + (suffix || 'gameplay'), remaining(10000));
        screens.push({code, screenClass: tree.screenClass, outcome, input: await observeStable(inWorld)}); await statusSame(acquired);
      }
      actionSamples.push({case: 'input_context_screens', fixtureInput, titleInput, inWorld, screens, worldName: row.worldName, coordinateFallbacks: 0,
        ordinaryScreensDoNotAdvanceNativeContext: true, agentEscDoesNotRevoke: true});
    });
    await check('input context dimension replacement cancels held active/queued input and preserves the same Lease', async () => {
      const original = await api('GET', '/v0/player?category=USER'); assert.equal(original.available, true); assert.equal(original.dimension, 'minecraft:overworld');
      const arrange = await nativeCommand('gamemode creative @s');
      const creative = await poll(() => api('GET', '/v0/player?category=USER'), value => value.available && value.gameMode === 'creative', 'Diagnostic safety gamemode was not observed');
      const outward = await boundary({dimension: 'minecraft:the_nether', x: 0, y: 200, z: 0});
      const returned = await boundary({dimension: original.dimension, x: original.x, y: original.y, z: original.z});
      actionSamples.push({case: 'input_context_dimensions', original, arrange, creative, outward, returned,
        arrangeIsGameplayAcceptance: false, destinationConfirmedBy: 'independent_USER_client_player', nativeCommandEffectReceiptNotProof: true});
    });
    await check('input context closing the actual Lease-bound event WebSocket releases TAKEOVER and held input', async () => {
      const before = await statusSame(), readyInput = await input(true);
      socket = await openSocket(connection, ownedLease, remaining(12000)); const hello = socket.hello;
      assert.equal(hello.type, 'event.hello'); assert.equal(hello.target, target);
      await statusSame(before);
      const down = await start([KEY(340, 1)], {cleanupOnComplete: false}); await terminal(down);
      const held = await poll(() => input(), value => value.pressedKeyCount === 1 && value.pressedKeys?.includes(340), 'WebSocket fixture Shift was not held');
      const closed = await socket.close(); assert.equal(closed.normalClose, true); assert.equal(closed.exitCode, 0); socket = undefined;
      const ended = await poll(() => api('GET', '/v0/control/status'), value => value.mode === 'READ' && value.status === 'available' && !value.leaseId,
        'Lease-bound event WebSocket close did not release control');
      assert.equal(ended.reconsentRequired, false); assert.equal(ended.modeTransitionReason, 'control_channel_disconnected');
      const waited = await released(), after = await poll(() => input(true), value => !value.inputCleanupPending && !value.inputCleanupFailed, 'Disconnect cleanup unfinished');
      const later = await negativeWindow(after);
      assert.equal(after.inputContextId, readyInput.inputContextId); assert.equal(after.inputContextGeneration, readyInput.inputContextGeneration);
      forgetExpiredLease(); ownsLease = false; ownedLease = undefined;
      actionSamples.push({case: 'input_context_control_ws', before, hello, readyInput, down, held, closed, ended, waited, after, later,
        transport: '.NET_ClientWebSocket', leaseHeaderBound: true, credentialsExcluded: true, negativeObservationMillis: 500,
        ordinaryHttpCloseNotTestedAsDisconnect: true, localLeaseForgottenOnlyAfterConfirmedEnd: true});
    });
  } catch (error) {primaryFailure = error; throw error;}
  finally {
    try {if (socket) await socket.abort();} catch (error) {cleanupFailure = error;}
    if (ownsLease) {
      try {
        const current = await api('GET', '/v0/control/status');
        if (current.leaseId === ownedLease) {
          let stopFailure;
          try {await api('POST', '/v0/input/release-all', {}); await released(); await input(true);} catch (error) {stopFailure = error;}
          try {await release();} catch (error) {stopFailure ??= error;}
          if (stopFailure) throw stopFailure;
        } else {
          assert.equal(current.status, 'available', 'Do not clean up a different owner'); assert.equal(current.mode, 'READ'); assert(!current.leaseId);
          await released(); await input(true); forgetExpiredLease();
        }
      } catch (error) {cleanupFailure ??= error;}
    }
    if (cleanupFailure) {actionSamples.push({case: 'input_context_cleanup_failure', error: String(cleanupFailure)}); if (!primaryFailure) throw cleanupFailure;}
  }
  const finalInput = await input(true), finalMode = await api('GET', '/v0/control/status');
  assert.equal(finalMode.mode, 'READ'); assert.equal(finalMode.status, 'available'); assert(!finalMode.leaseId);
  assert(now() - started <= 240000);
  const result = {case: 'input_context_complete', target, checks: 3, evidenceKind: testServices ? 'synthetic_test_not_runtime' : 'runtime_monotonic',
    liveEvidence: !testServices, elapsedMillis: now() - started, maximumMillis: 240000, finalInput, finalMode,
    sameUuidRespawnTested: false, respawnReason: 'native_gamerule_version_scenario_not_exercised', physicalEscTested: false,
    ordinaryHttpCloseImmediateReleaseClaimed: false, clientShutdownOwnedByOuterRunner: true, persistentWriteCalls: 0};
  actionSamples.push(result); return result;
}
