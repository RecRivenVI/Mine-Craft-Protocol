import assert from 'node:assert/strict';
import {createServer} from 'node:net';

const MAXIMUM_CONNECTIONS = 2;
// Both sequential native GUI cases share this listener, not one connection's budget.
const MAXIMUM_MILLIS = 15000 * MAXIMUM_CONNECTIONS;

// This is only an owned TCP sink: it never implements or replies to a Minecraft protocol.
export async function ownedConnectionUiActions({api, target, key, tree, wait, nodes, ui, click, title, check, samples}) {
  assert(['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric'].includes(target));
  let server, deadlineTimer, started, deadline, fatal, primary, maximumOpenSockets = 0, serverClosed = false, serverClosedAtMillis;
  const connections = [];
  const now = () => performance.now(), pause = milliseconds => new Promise(done => setTimeout(done, milliseconds));
  const offset = () => Math.max(0, now() - started);
  function healthy() {if (fatal) throw fatal; if (deadline !== undefined) assert(now() < deadline, 'Owned TCP listener exceeded ' + MAXIMUM_MILLIS + ' milliseconds');}
  async function poll(predicate, description, maximum = 5000) {
    const end = Math.min(deadline, now() + maximum);
    do {healthy(); if (predicate()) return; await pause(20);} while (now() < end);
    healthy(); assert.fail(description);
  }
  async function clean() {
    const input = await api('GET', '/v0/input/state'); healthy();
    assert.equal(input.pressedKeyCount, 0); assert.equal(input.pressedButtonCount, 0);
    assert.equal(input.inputCleanupPending, false); assert.equal(input.inputCleanupFailed, false); return input;
  }
  async function control() {
    const value = await api('GET', '/v0/control/status'); healthy();
    assert.equal(value.mode, 'TAKEOVER'); assert.equal(value.status, 'active'); assert.equal(value.reconsentRequired, false);
    assert.equal(typeof value.leaseId, 'string'); assert(value.leaseId.length > 0); return value;
  }
  async function page(name) {
    const value = await wait(value => value.screenClass.endsWith(name), 'Actual native owned connection ' + name); healthy();
    assert.equal(value.dataCategory, 'USER'); assert.equal(value.treeTruncated, false); return value;
  }
  async function button(keys) {
    const value = await tree(); healthy();
    const candidates = nodes(value).filter(node => keys.includes(node.translationKey) && node.visible && node.active && node.actions?.includes('click'));
    assert.equal(candidates.length, 1, 'One actual semantic connection button: ' + keys.join('|'));
    const result = await ui({nodeId: candidates[0].nodeId}); healthy(); assert.notEqual(result.directMutationUsed, true);
    return {node: candidates[0], result};
  }
  async function multiplayer() {
    await title(); await button(['menu.multiplayer']);
    let value = await wait(value => /SafetyScreen$|JoinMultiplayerScreen$/.test(value.screenClass), 'Native multiplayer entry'); healthy();
    if (value.screenClass.endsWith('SafetyScreen')) {await button(['gui.proceed']); value = await page('JoinMultiplayerScreen');}
    assert.equal(nodes(value).filter(node => node.controlKey === 'server.entry').length, 0, 'Only the disposable baseline without saved servers may be used');
    return value;
  }
  async function directJoin() {
    await button(['selectServer.direct']); const value = await page('DirectJoinServerScreen');
    const fields = nodes(value).filter(node => node.role === 'text_field' && node.visible && node.active && node.actions?.includes('type_text'));
    assert.equal(fields.length, 1, 'Actual Direct Join address field'); return {value, field: fields[0]};
  }
  async function listen() {
    started = now(); deadline = started + MAXIMUM_MILLIS;
    server = createServer(socket => {
      const observed = {index: connections.length, acceptedAtMillis: offset(), remoteAddress: socket.remoteAddress,
        receivedBytes: 0, peerEndObserved: false, closed: false, serverInitiatedClose: false, closeError: false};
      const record = {socket, observed}; connections.push(record);
      maximumOpenSockets = Math.max(maximumOpenSockets, connections.filter(connection => !connection.observed.closed).length);
      socket.on('data', data => {
        observed.receivedBytes += data.length;
        if (observed.receivedBytes > 65536) {fatal ??= new Error('Owned TCP input exceeded the 64KiB connection budget'); observed.serverInitiatedClose = 'budget'; socket.destroy();}
      });
      socket.on('end', () => {observed.peerEndObserved = true;});
      socket.on('error', error => {observed.socketErrorCode = error.code; if (error.code !== 'ECONNRESET') fatal ??= error;});
      socket.on('close', hadError => {observed.closed = true; observed.closedAtMillis = offset(); observed.closeError = hadError;});
      if (connections.length > MAXIMUM_CONNECTIONS || socket.remoteAddress !== '127.0.0.1') {
        fatal ??= new Error('Owned loopback connection count or address did not match its finite fixture');
        observed.serverInitiatedClose = 'invalid_peer_or_count'; socket.destroy();
      }
    });
    server.maxConnections = MAXIMUM_CONNECTIONS;
    server.on('drop', () => {fatal ??= new Error('Owned TCP listener rejected excess concurrent connections');});
    server.on('error', error => {fatal ??= error;});
    server.on('close', () => {serverClosed = true; serverClosedAtMillis = offset();});
    deadlineTimer = setTimeout(() => {
      fatal ??= new Error('Owned TCP listener reached its ' + MAXIMUM_MILLIS + '-millisecond hard lifetime');
      for (const connection of connections) if (!connection.observed.closed) {
        connection.observed.serverInitiatedClose = 'deadline_cleanup'; connection.socket.destroy();
      }
      if (server.listening) server.close();
    }, MAXIMUM_MILLIS);
    deadlineTimer.unref();
    await new Promise((done, reject) => {server.once('listening', done); server.once('error', reject); server.listen(0, '127.0.0.1');});
    healthy(); const address = server.address(); assert.equal(address.address, '127.0.0.1');
    assert(Number.isSafeInteger(address.port) && address.port > 0); return '127.0.0.1:' + address.port;
  }
  async function connect(address, field, index) {
    const typed = await ui({nodeId: field.nodeId}, 'type_text', {text: address}); healthy(); assert.notEqual(typed.directMutationUsed, true);
    await wait(value => value.screenClass.endsWith('DirectJoinServerScreen') && nodes(value).some(node => node.nodeId === field.nodeId && node.value === address), 'Exact owned loopback address'); healthy();
    const joined = await button(['selectServer.select']), value = await page('ConnectScreen');
    await poll(() => connections.length >= index + 1, 'Native Direct Join did not reach the owned TCP listener');
    assert.equal(connections.length, index + 1, 'No reconnect, status query or implicit replay is part of this fixture');
    assert.equal(connections[index].observed.closed, false, 'The owned transport must still be live while reading ConnectScreen');
    return {value, typed, joined, connection: connections[index]};
  }
  function sameLease(before, after) {assert.equal(after.leaseId, before.leaseId); assert.deepEqual(after.modeVersion, before.modeVersion);}
  function sample(caseId, connected, before, extra) {
    const value = {case: caseId, target, screenClass: connected.value.screenClass, nativeScreens: [connected.value.screenClass],
      screenIdentity: connected.value.screenIdentity, screenRevision: connected.value.screenRevision, before,
      nativeAddress: '127.0.0.1', port: server.address().port, connection: connected.connection.observed,
      semanticActions: [connected.typed, connected.joined], state: 'awaiting_native_result', semanticTargets: true,
      handwrittenCoordinates: false, directCallbackUsed: false, arrangeAuthority: 'DIAGNOSTIC_owned_loopback_tcp_sink', actAuthority: 'PLAYTEST',
      businessOperationCoverage: 'native_connection_ui_cancel_or_disconnect_only', minecraftProtocolRepliesSent: 0,
      loginSucceeded: false, externalServerTargetUsed: false, ...extra}; samples.push(value); return value;
  }
  try {
    await check('UI owned connection: actual Direct Join reaches ConnectScreen and native Cancel closes its transport', async () => {
      const authority = await control(), before = await clean(); await multiplayer();
      const {field} = await directJoin(), address = await listen(), connected = await connect(address, field, 0);
      const result = sample('owned_connection_native_cancel', connected, before);
      result.semanticActions.push(await button(['gui.cancel']));
      const returned = await page('JoinMultiplayerScreen');
      await poll(() => connected.connection.observed.closed, 'Native Cancel returned to the menu but did not close its owned TCP socket');
      assert.equal(connected.connection.observed.serverInitiatedClose, false, 'Server cleanup cannot prove native cancellation');
      sameLease(authority, await control()); result.after = await clean(); result.returnedScreen = returned.screenClass;
      result.peerCloseObservedAfterCancel = true; result.state = 'native_cancel_and_transport_close_observed';
    });
    await check('UI owned connection: closing only the owned socket produces actual DisconnectedScreen and a semantic return', async () => {
      healthy(); assert.equal(connections.length, 1); assert.equal(connections[0].observed.closed, true);
      const authority = await control(), before = await clean(), {field} = await directJoin();
      const address = '127.0.0.1:' + server.address().port, connected = await connect(address, field, 1);
      const result = sample('owned_connection_native_disconnect', connected, before);
      connected.connection.observed.serverInitiatedClose = 'intentional_owned_disconnect'; connected.connection.observed.serverCloseRequestedAtMillis = offset();
      connected.connection.socket.destroy(); await poll(() => connected.connection.observed.closed, 'The second owned socket did not close');
      const disconnected = await page('DisconnectedScreen'); assert.equal(disconnected.screenClass, 'net.minecraft.client.gui.screens.DisconnectedScreen');
      result.nativeScreens.push(disconnected.screenClass); result.disconnectedScreenIdentity = disconnected.screenIdentity;
      result.disconnectedScreenRevision = disconnected.screenRevision; result.semanticActions.push(await button(['gui.toMenu', 'gui.toTitle']));
      const returned = await wait(value => /JoinMultiplayerScreen$|TitleScreen$/.test(value.screenClass), 'Native disconnection return'); healthy();
      sameLease(authority, await control()); result.after = await clean(); result.returnedScreen = returned.screenClass;
      result.state = 'native_disconnected_page_and_return_observed';
    });
    if (!server) return;
    assert.equal(connections.length, MAXIMUM_CONNECTIONS); assert(connections.every(connection => connection.observed.closed));
    await title(); healthy(); await clean();
  } catch (error) {primary = error; throw error;}
  finally {
    clearTimeout(deadlineTimer);
    const cleanupStarted = now(); let cleanupFailure;
    for (const connection of connections) if (!connection.observed.closed) {
      connection.observed.serverInitiatedClose = 'final_cleanup'; connection.socket.destroy();
    }
    if (server) {
      try {
        if (server.listening) server.close();
        while ((!serverClosed || connections.some(connection => !connection.observed.closed)) && now() - cleanupStarted < 2000) await pause(10);
        assert.equal(server.listening, false); assert.equal(serverClosed, true); assert(connections.every(connection => connection.observed.closed), 'Owned sockets survived cleanup');
      } catch (error) {cleanupFailure = error;}
      samples.push({case: 'owned_connection_cleanup', target, listenerHost: '127.0.0.1', maximumMillis: MAXIMUM_MILLIS,
        observedLifetimeMillis: serverClosedAtMillis ?? offset(), helperElapsedAfterListenMillis: offset(), acceptedConnections: connections.length, maximumConnections: MAXIMUM_CONNECTIONS,
        maximumOpenSockets, listening: server.listening, serverClosed, openSockets: connections.filter(connection => !connection.observed.closed).length,
        connections: connections.map(connection => connection.observed), cleanupCompleted: !cleanupFailure, failed: Boolean(primary || fatal || cleanupFailure),
        ...(primary ? {failure: String(primary.message)} : {}), externalServerTargetUsed: false, minecraftProtocolRepliesSent: 0});
    }
    if (cleanupFailure) {if (primary) throw new AggregateError([primary, cleanupFailure], 'Owned connection UI and cleanup failed'); throw cleanupFailure;}
    if (!primary && fatal) throw fatal;
  }
}
