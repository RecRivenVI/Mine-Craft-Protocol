import assert from 'node:assert/strict';
import {createConnection} from 'node:net';
import test from 'node:test';
import {ownedConnectionUiActions} from './ui-owned-connection-actions.mjs';

// The GUI is a controlled Native API adapter; the owned listener and client sockets
// are real TCP. This tests the helper, not Minecraft runtime or human acceptance.
function controlledNative({cancelLeavesSocketOpen = false, extraConnections = false, returnToTitle = false} = {}) {
  let screen = 'TitleScreen', sequence = 0, identity = 1, address = '', safety = true, current;
  const clients = [], samples = [], checks = [], actions = [], addresses = [];
  const change = value => {screen = value; identity++;};
  const button = (nodeId, translationKey) => ({nodeId, translationKey, role: 'button', visible: true, active: true, actions: ['click']});
  const tree = async () => {
    let children = [];
    if (screen === 'TitleScreen') children = [button('multiplayer', 'menu.multiplayer')];
    else if (screen === 'SafetyScreen') children = [button('safety-proceed', 'gui.proceed')];
    else if (screen === 'JoinMultiplayerScreen') children = [button('direct', 'selectServer.direct')];
    else if (screen === 'DirectJoinServerScreen') children = [button('join', 'selectServer.select'),
      {...button('address', 'addServer.enterIp'), role: 'text_field', value: address, actions: ['type_text']}];
    else if (screen === 'ConnectScreen') children = [button('native-cancel', 'gui.cancel')];
    else if (screen === 'DisconnectedScreen') children = [button('native-return', returnToTitle ? 'gui.toTitle' : 'gui.toMenu')];
    return {screenClass: 'net.minecraft.client.gui.screens.' + screen, screenIdentity: identity, screenRevision: sequence + 1,
      dataCategory: 'USER', treeTruncated: false, children};
  };
  function connect() {
    const [host, port] = address.split(':'); assert.equal(host, '127.0.0.1'); assert(Number(port) > 0);
    const entry = {socket: createConnection({host, port: Number(port)}), cancelled: false}; clients.push(entry); current = entry;
    entry.socket.on('connect', () => {entry.socket.write(Buffer.from('controlled-test-client'));});
    entry.socket.on('error', () => {});
    entry.socket.on('close', () => {if (current === entry && !entry.cancelled && screen === 'ConnectScreen') change('DisconnectedScreen');});
    return entry;
  }
  const ctx = {target: '26.3-neoforge', samples, tree, nodes: value => value.children,
    title: async () => {change('TitleScreen');},
    api: async (method, path) => {
      assert.equal(method, 'GET');
      if (path === '/v0/control/status') return {mode: 'TAKEOVER', status: 'active', leaseId: 'owned-test-lease', reconsentRequired: false,
        modeVersion: {controlSessionId: 'test-session', generation: 4}};
      assert.equal(path, '/v0/input/state'); return {pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false,
        inputCleanupFailed: false, inputDispatchSequence: sequence};
    },
    wait: async (predicate, label) => {
      const end = performance.now() + 1000; let value;
      do {value = await tree(); if (predicate(value)) return value; await new Promise(done => setTimeout(done, 5));} while (performance.now() < end);
      assert.fail(label + ': ' + JSON.stringify(value));
    },
    ui: async (selector, action = 'click', extra = {}) => {
      assert.deepEqual(Object.keys(selector), ['nodeId']); const value = await tree(), node = value.children.find(node => node.nodeId === selector.nodeId);
      assert(node); assert(node.actions.includes(action)); sequence++; actions.push({nodeId: selector.nodeId, action, extra});
      if (action === 'type_text') {assert.match(extra.text, /^127\.0\.0\.1:\d+$/); address = extra.text; addresses.push(address);}
      else if (selector.nodeId === 'multiplayer') change(safety ? 'SafetyScreen' : 'JoinMultiplayerScreen');
      else if (selector.nodeId === 'safety-proceed') {safety = false; change('JoinMultiplayerScreen');}
      else if (selector.nodeId === 'direct') {address = ''; change('DirectJoinServerScreen');}
      else if (selector.nodeId === 'join') {
        change('ConnectScreen'); connect(); if (extraConnections) {connect(); connect();}
      } else if (selector.nodeId === 'native-cancel') {
        current.cancelled = true; if (!cancelLeavesSocketOpen) current.socket.destroy(); change('JoinMultiplayerScreen');
      } else if (selector.nodeId === 'native-return') change(returnToTitle ? 'TitleScreen' : 'JoinMultiplayerScreen');
      else assert.fail(selector.nodeId);
      return {targetingSource: 'interaction_tree', directMutationUsed: false};
    },
    check: async (name, run) => {await run(); checks.push(name);},
  };
  return {ctx, clients, samples, checks, actions, addresses, state: () => screen,
    close: () => {for (const client of clients) client.socket.destroy();}};
}

test('unselected connection checks neither require two connections nor navigate or create a listener', async () => {
  const runtime = controlledNative();
  runtime.ctx.check = async () => {};
  runtime.ctx.api = async () => {assert.fail('A filtered group must not read or dispatch');};
  runtime.ctx.title = async () => {assert.fail('A filtered group must not navigate');};
  await ownedConnectionUiActions(runtime.ctx);
  assert.deepEqual(runtime.samples, []); assert.deepEqual(runtime.actions, []); assert.deepEqual(runtime.clients, []);
});

test('actual helper owns two real TCP connections, observes native Cancel transport close and then Disconnected semantic return', async () => {
  const runtime = controlledNative();
  try {
    await ownedConnectionUiActions(runtime.ctx);
    assert.equal(runtime.checks.length, 2); assert.equal(runtime.clients.length, 2); assert.equal(runtime.state(), 'TitleScreen');
    assert(runtime.clients.every(client => client.socket.destroyed)); assert.equal(runtime.addresses.length, 2); assert.equal(runtime.addresses[0], runtime.addresses[1]);
    const [cancelled, disconnected, cleanup] = runtime.samples;
    assert.equal(cancelled.case, 'owned_connection_native_cancel'); assert.equal(cancelled.peerCloseObservedAfterCancel, true);
    assert.equal(cancelled.connection.serverInitiatedClose, false); assert.equal(cancelled.connection.closed, true);
    assert.equal(disconnected.case, 'owned_connection_native_disconnect'); assert.equal(disconnected.connection.serverInitiatedClose, 'intentional_owned_disconnect');
    assert(disconnected.nativeScreens.includes('net.minecraft.client.gui.screens.DisconnectedScreen'));
    assert.equal(disconnected.state, 'native_disconnected_page_and_return_observed');
    assert.equal(cleanup.cleanupCompleted, true); assert.equal(cleanup.serverClosed, true); assert.equal(cleanup.listening, false);
    assert.equal(cleanup.openSockets, 0); assert.equal(cleanup.acceptedConnections, 2); assert(cleanup.maximumOpenSockets <= 2);
    assert.equal(cleanup.failed, false); assert.equal(cleanup.maximumMillis, 30000); assert(cleanup.observedLifetimeMillis < cleanup.maximumMillis);
    assert(runtime.samples.every(sample => sample.minecraftProtocolRepliesSent === 0 && sample.externalServerTargetUsed === false));
    assert.equal(runtime.actions.filter(action => action.nodeId === 'join').length, 2);
  } finally {runtime.close();}
});

test('native account policy may return Disconnected directly to Title without a fabricated server-list callback', async () => {
  const runtime = controlledNative({returnToTitle: true});
  try {await ownedConnectionUiActions(runtime.ctx); assert(runtime.samples[1].returnedScreen.endsWith('TitleScreen')); assert.equal(runtime.samples.at(-1).cleanupCompleted, true);}
  finally {runtime.close();}
});

test('returning from Connect without closing the client socket fails and final owned cleanup cannot substitute for native cancellation', async () => {
  const runtime = controlledNative({cancelLeavesSocketOpen: true});
  try {
    await assert.rejects(ownedConnectionUiActions(runtime.ctx), /did not close its owned TCP socket/);
    assert.equal(runtime.checks.length, 0); assert.equal(runtime.actions.filter(action => action.nodeId === 'join').length, 1);
    const partial = runtime.samples[0], cleanup = runtime.samples.at(-1);
    assert.equal(partial.state, 'awaiting_native_result'); assert.equal(partial.peerCloseObservedAfterCancel, undefined);
    assert.equal(partial.connection.serverInitiatedClose, 'final_cleanup'); assert.equal(partial.connection.closed, true);
    assert.equal(cleanup.failed, true); assert.equal(cleanup.cleanupCompleted, true); assert.equal(cleanup.openSockets, 0); assert.equal(cleanup.listening, false);
    assert.match(cleanup.failure, /did not close/);
  } finally {runtime.close();}
});

test('extra native connection attempts fail closed and all owned sockets are still closed', async () => {
  const runtime = controlledNative({extraConnections: true});
  try {
    await assert.rejects(ownedConnectionUiActions(runtime.ctx), /excess concurrent|implicit replay|finite fixture/);
    assert.equal(runtime.checks.length, 0); const cleanup = runtime.samples.at(-1);
    assert.equal(cleanup.failed, true); assert.equal(cleanup.cleanupCompleted, true); assert.equal(cleanup.openSockets, 0); assert.equal(cleanup.listening, false);
    assert(cleanup.acceptedConnections <= 2); assert(cleanup.maximumOpenSockets <= 2);
  } finally {runtime.close();}
});
