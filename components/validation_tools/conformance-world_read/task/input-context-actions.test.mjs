import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {createServer} from 'node:http';
import {once} from 'node:events';
import test from 'node:test';
import {assertInputContextAdvanced, inputContextActions, inputContextSnapshot, openLeaseBoundEventSocket} from './input-context-actions.mjs';

const snapshot = (generation = 1) => ({inputContextGeneration: generation, inputContextId: 'native-' + generation, inputContextAvailable: true,
  inputGeneration: generation, inputDispatchSequence: 0, pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false});

test('native epoch rejects partial/invalid state and same ID, same generation or unfinished cleanup', () => {
  assert.deepEqual(inputContextSnapshot(snapshot()), snapshot());
  for (const edit of [value => {value.inputContextGeneration = NaN;}, value => {value.inputContextId = '';}, value => {value.inputContextAvailable = 1;},
    value => {value.pressedKeyCount = -1;}, value => {delete value.inputGeneration;}]) {
    const value = snapshot(); edit(value); assert.throws(() => inputContextSnapshot(value));
  }
  assert.doesNotThrow(() => assertInputContextAdvanced(snapshot(1), snapshot(2)));
  for (const edit of [value => {value.inputContextGeneration = 1;}, value => {value.inputContextId = 'native-1';}, value => {value.inputGeneration = 1;},
    value => {value.pressedKeyCount = 1;}, value => {value.pressedButtonCount = 1;}, value => {value.inputCleanupPending = true;},
    value => {value.inputCleanupFailed = true;}, value => {value.inputContextAvailable = false;}]) {
    const value = snapshot(2); edit(value); assert.throws(() => assertInputContextAdvanced(snapshot(1), value));
  }
});

function simulation(options = {}) {
  let milliseconds = 0, mode = 'READ', lease, callerLease, modeGeneration = 0, context = 0, generation = 0, sequence = 0, screen = 'TitleScreen';
  let operationId = 0, releases = 0, stops = 0, socketOpened = 0, socketClosed = 0, socketAborted = 0, forgotten = 0, receivingQueries = 0;
  const keys = new Set(), operations = new Map(), calls = [], samples = [], checks = [], socketOptions = [], uiReads = [];
  let destinationFrames = [];
  const player = {available: true, dataCategory: 'USER', dimension: 'minecraft:overworld', x: .5, y: 65, z: .5, gameMode: 'creative'};
  const state = () => ({...snapshot(context), inputContextAvailable: context > 0, inputGeneration: generation,
    inputDispatchSequence: sequence, pressedKeyCount: keys.size, pressedButtonCount: 0, pressedKeys: [...keys], pressedButtons: [], clientTick: Math.floor(milliseconds / 50)});
  const status = () => ({mode, status: lease ? 'active' : 'available', ...(lease ? {leaseId: lease} : {}), reconsentRequired: false,
    modeVersion: {controlSessionId: 'test', generation: modeGeneration}, ...(mode === 'READ' && socketClosed ? {modeTransitionReason: 'control_channel_disconnected'} : {})});
  const cancel = () => {
    keys.clear(); sequence++; generation++;
    for (const operation of operations.values()) if (!operation.outcome) operation.outcome = {operationId: operation.id, state: 'cancelled', status: 'cancelled'};
  };
  function execute(operation) {
    if (options.failFreshShift && operation.steps[0]?.type === 'key.tap' && operation.steps[0].key === 340)
      return operation.outcome = {operationId: operation.id, state: 'failed', status: 'failed', error: 'UI_TARGET_INVALIDATED'};
    for (const step of operation.steps) {
      if (step.type === 'key.tap') {
        sequence += 2;
        if (step.key === 256) screen = screen ? '' : 'PauseScreen';
        if (step.key === 69) screen = 'CreativeModeInventoryScreen';
      } else assert.fail('Unexpected completed fake step');
    }
    if (operation.cleanupOnComplete !== false) {keys.clear(); sequence++;}
    operation.outcome = {operationId: operation.id, state: 'completed', status: 'completed', result: {steps: []}};
    return operation.outcome;
  }
  const api = async (method, path, body) => {
    calls.push({method, path, body: body && structuredClone(body), at: milliseconds}); milliseconds += 5;
    if (method === 'GET' && path === '/v0/input/state') return state();
    if (method === 'GET' && ['/v0/control/status', '/v0/control/mode'].includes(path)) return status();
    if (method === 'GET' && path === '/v0/ui/tree') {
      const receiving = receivingQueries > 0 || (options.loadingNeverReady && context > 1);
      if (receivingQueries > 0) receivingQueries--;
      const visible = receiving ? 'ReceivingLevelScreen' : screen;
      const tree = {screenClass: visible, screenIdentity: visible ? 1 : 0, screenRevision: visible ? 1 : 0,
        overlayIdentity: 0, clientTick: Math.floor(milliseconds / 50)};
      uiReads.push({...tree, at: milliseconds}); if (context > 1) destinationFrames.push(tree); return tree;
    }
    if (method === 'GET' && path === '/v0/player?category=USER') return {...player};
    if (method === 'POST' && path === '/v0/pipelines') {
      assert.equal(mode, 'TAKEOVER'); assert(lease); const id = 'operation-' + ++operationId;
      if (body.steps[0]?.type === 'key.tap' && body.steps[0].key === 340) {
        const frames = destinationFrames.slice(-2);
        assert.equal(frames.length, 2, 'Fresh input needs native UI readiness observations');
        assert(frames.every(value => !value.screenClass && !value.overlayIdentity));
        assert(frames[1].clientTick > frames[0].clientTick, 'Same-frame polling is not readiness');
        assert.equal(receivingQueries, 0, 'No fresh input during ReceivingLevelScreen');
      }
      const operation = {id, ...structuredClone(body), waits: 0};
      if (body.steps[0].type === 'key') {assert.equal(body.steps[0].key, 340); keys.add(340); sequence++; operation.active = true;}
      else if (body.steps[0].key === 68 && keys.has(340)) operation.queued = true;
      operations.set(id, operation); return {operationId: id, status: 'running', state: operation.queued ? 'queued' : 'executing'};
    }
    if (path.startsWith('/v0/operations/')) {
      const operation = operations.get(path.split('/')[3]); assert(operation);
      if (method === 'GET') return operation.outcome ?? {operationId: operation.id, state: 'executing', status: 'running'};
      assert.equal(method, 'POST'); operation.waits++;
      if (operation.outcome) return operation.outcome;
      if (options.nonterminalFirst && operation.waits === 1) return {operationId: operation.id, state: 'executing', status: 'running'};
      if (operation.active && operation.steps.length === 1)
        return operation.outcome = {operationId: operation.id, state: 'completed', status: 'completed'};
      if (operation.active || operation.queued) return {operationId: operation.id, state: 'executing', status: 'running'};
      return execute(operation);
    }
    if (method === 'POST' && path === '/v0/command/player') {
      assert.equal(body.route, 'NATIVE'); assert.equal(mode, 'TAKEOVER'); assert.equal(lease, callerLease);
      const parts = body.command.split(' ');
      if (parts[0] === 'gamemode') player.gameMode = parts[1];
      else {
        assert.deepEqual(parts.slice(0, 2), ['execute', 'in']); assert.deepEqual(parts.slice(3, 6), ['run', 'tp', '@s']);
        if (options.commandFailure) throw new Error('native command failed before submission');
        const [x, y, z] = parts.slice(6).map(Number); Object.assign(player, {dimension: parts[2], x, y, z});
        if (options.netherGravity && player.dimension === 'minecraft:the_nether') player.y = 128;
        cancel(); if (!options.skipEpoch) context++;
        receivingQueries = options.receivingQueries ?? 0; destinationFrames = [];
        if (options.foreignOwner) lease = 'someone-else';
      }
      return {route: 'NATIVE', inputDispatched: false, effectConfirmed: false};
    }
    if (method === 'POST' && path === '/v0/wait/until') {
      assert.deepEqual(body.condition, {type: 'input.released'}); assert.equal(keys.size, 0);
      return {passed: true, condition: body.condition, evidence: {pressedKeyCount: 0, pressedButtonCount: 0}};
    }
    if (method === 'POST' && path === '/v0/input/release-all') {
      assert.equal(lease, callerLease); stops++; cancel(); return {type: 'input.release_all', inputGeneration: generation};
    }
    assert.fail('Unexpected fake request: ' + method + ' ' + path);
  };
  const fixtureInput = {policy: 'immutable_baseline_restored', target: '26.3-neoforge', role: 'client', credentialsCopied: false,
    baselineSha256: 'a'.repeat(64), restoredSha256: 'a'.repeat(64)};
  const services = {now: () => milliseconds, sleep: async duration => {
    milliseconds += duration;
    if (options.lateInput && duration === 500) sequence++;
  }, openSocket: async (connection, leaseId) => {
    assert.equal(leaseId, lease); assert.equal(leaseId, callerLease); socketOpened++; socketOptions.push({base: connection.base, leaseId});
    if (options.failSocketOpen) throw new Error('owned socket handshake failed');
    return {hello: {type: 'event.hello', target: options.wrongHelloTarget ? 'another-target' : '26.3-neoforge', sequence: 101},
      close: async () => {
        socketClosed++; cancel(); lease = undefined; mode = 'READ'; modeGeneration++;
        if (options.failSocketClose) throw new Error('owned socket normal close failed');
        return {normalClose: true, exitCode: 0};
      }, abort: async () => {socketAborted++; if (lease === callerLease) {cancel(); lease = undefined; mode = 'READ'; modeGeneration++;}}};
  }};
  const env = {api, target: '26.3-neoforge', fixtureInput, connection: {base: 'http://127.0.0.1:12345', token: 'never-include-secret', headers: {}},
    mode: async value => {assert(!lease); mode = value;},
    acquire: async () => {assert(!lease); lease = callerLease = 'own-lease'; mode = 'TAKEOVER'; modeGeneration++;},
    release: async () => {assert.equal(lease, callerLease); releases++; cancel(); lease = undefined; mode = 'READ'; modeGeneration++; callerLease = undefined;},
    forgetExpiredLease: () => {assert(!lease); assert.equal(mode, 'READ'); assert.equal(keys.size, 0); forgotten++; callerLease = undefined;},
    click: async key => {
      if (key === 'menu.singleplayer') screen = 'SelectWorldScreen';
      else if (key === 'selectWorld.select') {screen = ''; context++; cancel();}
      else assert.fail('Unexpected fake semantic click');
    }, clickNode: async node => assert.equal(node.worldName, 'World-Read-Alpha'),
    nodes: () => [{worldName: 'World-Read-Alpha', nodeId: 'world-fixed'}],
    wait: async predicate => {const tree = {screenClass: screen}; assert(predicate(tree)); return tree;},
    check: async (name, callback) => {await callback(); checks.push(name);}, actionSamples: samples};
  return {env, services, calls, samples, checks, socketOptions, state, operations, uiReads,
    counters: () => ({mode, lease, callerLease, releases, stops, socketOpened, socketClosed, socketAborted, forgotten, milliseconds})};
}

test('three bounded synthetic scenes verify actual-shaped epoch, cancellation, independent readback and a real-control-channel close contract', async () => {
  const fixture = simulation({nonterminalFirst: true}); const result = await inputContextActions(fixture.env, fixture.services);
  assert.equal(result.liveEvidence, false); assert.equal(result.evidenceKind, 'synthetic_test_not_runtime'); assert.equal(result.checks, 3);
  assert.equal(result.sameUuidRespawnTested, false); assert.equal(result.physicalEscTested, false); assert.equal(result.persistentWriteCalls, 0);
  assert.equal(fixture.checks.length, 3); const screens = fixture.samples.find(value => value.case === 'input_context_screens');
  assert.equal(screens.screens.length, 4); assert(screens.screens.every(value => value.input.inputContextId === screens.inWorld.inputContextId));
  const dimensions = fixture.samples.find(value => value.case === 'input_context_dimensions');
  for (const boundary of [dimensions.outward, dimensions.returned]) {
    assert.equal(boundary.outcomes.length, 2); assert(boundary.outcomes.every(value => value.state === 'cancelled'));
    assert(boundary.after.inputContextGeneration > boundary.before.inputContextGeneration);
    assert.equal(boundary.later.inputDispatchSequence, boundary.after.inputDispatchSequence); assert.equal(boundary.sameLeasePreserved, true);
    assert.equal(boundary.receipt.effectConfirmed, false); assert.equal(boundary.player.dataCategory, 'USER');
  }
  const ws = fixture.samples.find(value => value.case === 'input_context_control_ws');
  assert.equal(ws.hello.sequence, 101, 'Keep the received hello rather than manufacturing evidence');
  assert.equal(ws.ended.modeTransitionReason, 'control_channel_disconnected'); assert.equal(ws.closed.normalClose, true);
  assert.equal(fixture.counters().forgotten, 1); assert.equal(fixture.counters().socketClosed, 1); assert.equal(fixture.counters().socketAborted, 0);
  assert.equal(fixture.counters().stops, 0); assert.equal(fixture.counters().releases, 0); assert.equal(fixture.counters().lease, undefined);
  const posts = fixture.calls.filter(value => value.method === 'POST' && value.path === '/v0/pipelines');
  assert(posts.length >= 10); for (let index = 1; index < posts.length; index++) assert(posts[index].at - posts[index - 1].at >= 550);
  assert(!JSON.stringify(fixture.samples).includes('never-include-secret')); assert(fixture.counters().milliseconds < 240000);
});

test('dimension cancellation uses the fixed Nether roof instead of requiring an airborne arrival to remain at altitude', async () => {
  const fixture = simulation({netherGravity: true}); await inputContextActions(fixture.env, fixture.services);
  const outward = fixture.samples.find(value => value.case === 'input_context_dimensions').outward;
  assert.equal(outward.player.y, 128);
  assert(fixture.calls.some(value => value.path === '/v0/command/player'
    && value.body.command === 'execute in minecraft:the_nether run tp @s 0 128 0'));
  assert(outward.outcomes.every(value => value.state === 'cancelled'));
  assert.equal(outward.later.inputDispatchSequence, outward.after.inputDispatchSequence);
});

test('input context waits for its asynchronous exact world row rather than just the Screen', async () => {
  const fixture = simulation(), originalNodes = fixture.env.nodes, originalWait = fixture.env.wait;
  const row = {worldName: 'World-Read-Alpha', nodeId: 'world-fixed'}; let clicked = 0;
  fixture.env.nodes = tree => tree.children ?? originalNodes(tree);
  fixture.env.wait = async (predicate, label, timeout) => {
    if (!label.includes('context fixed world list')) return originalWait(predicate, label, timeout);
    assert.equal(predicate({screenClass: 'SelectWorldScreen', children: []}), false);
    assert.equal(predicate({screenClass: 'SelectWorldScreen', children: [{worldName: 'Another World'}]}), false);
    const ready = {screenClass: 'SelectWorldScreen', children: [row]}; assert.equal(predicate(ready), true); return ready;
  };
  fixture.env.clickNode = async value => {assert.equal(value, row); clicked++;};
  await inputContextActions(fixture.env, fixture.services); assert.equal(clicked, 1); assert.equal(fixture.checks.length, 3);
});

test('input context refuses duplicate exact world rows before player dispatch and releases its own Lease', async () => {
  const fixture = simulation(), originalNodes = fixture.env.nodes, originalWait = fixture.env.wait;
  fixture.env.nodes = tree => tree.children ?? originalNodes(tree);
  fixture.env.wait = async (predicate, label, timeout) => {
    if (!label.includes('context fixed world list')) return originalWait(predicate, label, timeout);
    const ready = {screenClass: 'SelectWorldScreen', children: [originalNodes()[0], {...originalNodes()[0]}]};
    assert.equal(predicate(ready), true); return ready;
  };
  fixture.env.clickNode = async () => assert.fail('Ambiguous world must not be clicked');
  await assert.rejects(inputContextActions(fixture.env, fixture.services), /One exact fixed-world row/);
  assert.equal(fixture.calls.filter(call => call.path === '/v0/pipelines').length, 0);
  assert.equal(fixture.counters().releases, 1); assert.equal(fixture.state().pressedKeyCount, 0);
});

test('unverified fixture does not acquire, enter a world or send any mutation', async () => {
  for (const change of [env => {env.fixtureInput = null;}, env => {env.fixtureInput.target = '1.20.1-forge';},
    env => {env.fixtureInput.credentialsCopied = true;}, env => {env.fixtureInput.restoredSha256 = 'b'.repeat(64);}]) {
    const fixture = simulation(); change(fixture.env); await assert.rejects(inputContextActions(fixture.env, fixture.services));
    assert.equal(fixture.calls.length, 0); assert.equal(fixture.counters().lease, undefined);
  }
});

test('ReceivingLevelScreen may outlive dimension/epoch arrival; fresh input waits for two unobstructed native frames', async () => {
  const fixture = simulation({receivingQueries: 4}); await inputContextActions(fixture.env, fixture.services);
  const dimensions = fixture.samples.find(value => value.case === 'input_context_dimensions');
  for (const boundary of [dimensions.outward, dimensions.returned]) {
    const readiness = boundary.freshInput.readiness;
    assert.equal(readiness.observations.filter(value => value.ui.screenClass === 'ReceivingLevelScreen').length, 4);
    assert.equal(readiness.stableNativeFrames.length, 2);
    assert(readiness.stableNativeFrames[1].ui.clientTick > readiness.stableNativeFrames[0].ui.clientTick);
    assert(readiness.stableNativeFrames.every(value => value.ui.screenClass === '' && value.ui.screenIdentity === 0 && value.ui.overlayIdentity === 0));
    assert.equal(readiness.targetInputContextId, boundary.after.inputContextId);
    assert.equal(readiness.targetInputContextGeneration, boundary.after.inputContextGeneration);
  }
  assert.equal(fixture.calls.filter(value => value.path === '/v0/pipelines' && value.body.steps[0]?.type === 'key.tap' && value.body.steps[0]?.key === 340).length, 2);
});

test('an unresolved loading screen times out without issuing a fresh key operation', async () => {
  const fixture = simulation({loadingNeverReady: true}); await assert.rejects(inputContextActions(fixture.env, fixture.services), /Destination gameplay UI did not become ready/);
  assert.equal(fixture.calls.filter(value => value.path === '/v0/pipelines' && value.body.steps[0]?.type === 'key.tap' && value.body.steps[0]?.key === 340).length, 0);
  assert.equal(fixture.counters().stops, 1); assert.equal(fixture.counters().releases, 1);
});

test('fresh operation invalidation is propagated and never retried after readiness', async () => {
  const fixture = simulation({failFreshShift: true}); await assert.rejects(inputContextActions(fixture.env, fixture.services), /UI_TARGET_INVALIDATED/);
  assert.equal(fixture.calls.filter(value => value.path === '/v0/pipelines' && value.body.steps[0]?.type === 'key.tap' && value.body.steps[0]?.key === 340).length, 1);
  assert.equal(fixture.counters().stops, 1); assert.equal(fixture.counters().releases, 1);
});

test('same native epoch after actual dimension readback is a failure, not accepted using only the dimension', async () => {
  const fixture = simulation({skipEpoch: true}); await assert.rejects(inputContextActions(fixture.env, fixture.services), /Native context did not advance/);
  assert.equal(fixture.counters().stops, 1); assert.equal(fixture.counters().releases, 1); assert.equal(fixture.state().pressedKeyCount, 0);
  assert.equal(fixture.counters().socketOpened, 0);
});

test('a late callback in the negative observation window fails and cleans only the owned Lease', async () => {
  const fixture = simulation({lateInput: true}); await assert.rejects(inputContextActions(fixture.env, fixture.services), /Old input arrived/);
  assert.equal(fixture.counters().stops, 1); assert.equal(fixture.counters().releases, 1); assert.equal(fixture.counters().lease, undefined);
});

test('failed native mutation is never retried and all pending old input is safely stopped', async () => {
  const fixture = simulation({commandFailure: true}); await assert.rejects(inputContextActions(fixture.env, fixture.services), /native command failed before submission/);
  assert.equal(fixture.calls.filter(value => value.path === '/v0/command/player' && value.body.command.startsWith('execute ')).length, 1);
  assert.equal(fixture.counters().stops, 1); assert.equal(fixture.counters().releases, 1); assert.equal(fixture.state().pressedKeyCount, 0);
});

test('another owner is never released even when the scenario fails after a native replacement', async () => {
  const fixture = simulation({foreignOwner: true}); await assert.rejects(inputContextActions(fixture.env, fixture.services));
  assert.equal(fixture.counters().lease, 'someone-else'); assert.equal(fixture.counters().stops, 0); assert.equal(fixture.counters().releases, 0);
  assert(fixture.samples.some(value => value.case === 'input_context_cleanup_failure'));
});

test('socket handshake failure stops/releases own input without pretending a disconnect happened', async () => {
  const fixture = simulation({failSocketOpen: true}); await assert.rejects(inputContextActions(fixture.env, fixture.services), /owned socket handshake failed/);
  assert.equal(fixture.counters().socketClosed, 0); assert.equal(fixture.counters().stops, 1); assert.equal(fixture.counters().releases, 1);
  assert(!fixture.samples.some(value => value.case === 'input_context_control_ws'));
});

test('abnormal socket close remains a failure while confirmed ended ownership is safely forgotten', async () => {
  const fixture = simulation({failSocketClose: true}); await assert.rejects(inputContextActions(fixture.env, fixture.services), /owned socket normal close failed/);
  assert.equal(fixture.counters().socketAborted, 1); assert.equal(fixture.counters().forgotten, 1); assert.equal(fixture.counters().releases, 0);
  assert(!fixture.samples.some(value => value.case === 'input_context_control_ws'));
});

test('hello from the wrong Target is not accepted and only the owned socket is aborted', async () => {
  const fixture = simulation({wrongHelloTarget: true}); await assert.rejects(inputContextActions(fixture.env, fixture.services));
  assert.equal(fixture.counters().socketClosed, 0); assert.equal(fixture.counters().socketAborted, 1);
  assert.equal(fixture.counters().forgotten, 1); assert.equal(fixture.counters().releases, 0);
  assert(!fixture.samples.some(value => value.case === 'input_context_control_ws'));
});

test('a real .NET ClientWebSocket sends the instance/Lease headers, receives hello and closes normally', {skip: process.platform !== 'win32', timeout: 20000}, async () => {
  const target = '26.3-neoforge', headers = [], sockets = new Set(); let bridge;
  const server = createServer();
  server.on('upgrade', (request, socket) => {
    sockets.add(socket); socket.on('close', () => sockets.delete(socket)); socket.on('error', () => {});
    headers.push({...request.headers, url: request.url});
    const accepted = createHash('sha1').update(request.headers['sec-websocket-key'] + '258EAFA5-E914-47DA-95CA-C5AB0DC85B11').digest('base64');
    socket.write('HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: ' + accepted + '\r\n\r\n');
    const hello = Buffer.from(JSON.stringify({type: 'event.hello', target, sequence: 42})); assert(hello.length < 126);
    socket.write(Buffer.concat([Buffer.from([0x81, hello.length]), hello]));
    socket.on('data', bytes => {if ((bytes[0] & 15) === 8) {socket.end(Buffer.from([0x88, 2, 3, 232]));}});
  });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  try {
    bridge = await openLeaseBoundEventSocket({base: 'http://127.0.0.1:' + server.address().port, token: 'owned-fixture-token',
      headers: {authorization: 'Bearer caller-token', Authorization: 'Bearer different-case-caller-token',
        'x-mcp-runtime-instance': 'owned-instance', 'x-mcp-control-lease': 'caller-lease', 'X-MCP-Control-Lease': 'different-case-caller-lease'}}, 'own-fixture-lease');
    assert.equal(bridge.hello.sequence, 42);
    assert.equal(headers[0].authorization, 'Bearer owned-fixture-token'); assert.equal(headers[0]['x-mcp-control-lease'], 'own-fixture-lease');
    assert.equal(headers[0]['x-mcp-runtime-instance'], 'owned-instance'); assert.equal(headers[0].url, '/v0/events?category=USER');
    assert.deepEqual(await bridge.close(), {exitCode: 0, normalClose: true}); bridge = undefined;
  } finally {
    if (bridge) await bridge.abort(); for (const socket of sockets) socket.destroy();
    await new Promise(resolve => server.close(resolve));
  }
});
