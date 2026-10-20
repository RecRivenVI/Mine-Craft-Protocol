import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import test from 'node:test';
import {uiCommandManagementActions} from './ui-command-management-actions.mjs';

const hash = text => createHash('sha256').update(text).digest('hex');
const PREFIX = 'say Mine-Craft-Protocol_command_configuration_';
const CLASS = 'net.minecraft.client.gui.screens.inventory.';

// Controlled Native consumers run the real helper; they are not game acceptance evidence.
function controlled({target = '26.3-neoforge', packetLeak = false, storedMutation = false, foreignPid = false,
  unknownOwner = false, pending = false, releaseError = false, initDenied = true, filterAll = false,
  initialScreen = ''} = {}) {
  const calls = [], checks = [], samples = [], operations = new Map();
  let block = 'minecraft:air', cart = false, screen = initialScreen, draft = '', identity = 0, revision = 0;
  let commandCount = 0, minecartPackets = 0, storedCart = '', snapshot, clock = 0;
  function show(value) {screen = value; identity++; revision++; draft = '';}
  function view() {
    const children = [];
    if (screen.endsWith('MinecartCommandBlockEditScreen')) children.push({nodeId: 'cart-command', role: 'text_field',
      translationKey: 'advMode.command', value: draft, visible: true, active: true, actions: ['type_text']});
    if (screen.endsWith('TestBlockEditScreen')) children.push({nodeId: 'test-message', role: 'text_field',
      translationKey: 'test_block.message', value: draft, visible: true, active: true, actions: ['type_text']});
    if (screen.endsWith('TestInstanceBlockEditScreen')) children.push({nodeId: 'test-id', role: 'text_field',
      translationKey: 'test_instance_block.test_id', value: draft, visible: true, active: true, actions: ['type_text']});
    if (screen) children.push({nodeId: screen.endsWith('TestInstanceBlockEditScreen') ? 'reset' : 'done', role: 'button',
      translationKey: screen.endsWith('TestInstanceBlockEditScreen') ? 'test_instance.action.reset' : 'gui.done',
      visible: true, active: true, actions: ['click']});
    return {dataCategory: 'USER', screenClass: screen, screenIdentity: screen ? identity : 0,
      screenRevision: revision, treeTruncated: false, overlayIdentity: 0, children};
  }
  function receipt(attemptId) {
    return {attemptId, probeOnly: true, validationName: 'conformance-vanilla_ui', target,
      processId: foreignPid ? 99 : 7, fixtureAuthority: 'DIAGNOSTIC_native_server_receipt_observation',
      worldName: unknownOwner ? 'unknown_world' : 'UI-Conformance', singlePrivatePlayerBound: !unknownOwner,
      nativePlayerCommands: commandCount, commandBlockPackets: 0, commandMinecartPackets: minecartPackets,
      jigsawConfigurationPackets: 0, jigsawGenerationPackets: 0, structureUpdatePackets: 0,
      structureScanPackets: 0, structureSavePackets: 0, structureLoadPackets: 0, testBlockPackets: 0,
      testInstanceActionPackets: 0, uiFixtureMinecartCount: cart ? 1 : 0,
      ...(cart ? {uiFixtureMinecartCommandHash: hash(storedCart)} : {}),
      ...(block === 'minecraft:test_block' ? {storedTestMode: 'LOG', storedTestMessageHash: hash('')} : {}),
      ...(block === 'minecraft:test_instance_block' ? {storedTestInstanceHash: hash('native-test-parameters')} : {}),
      chunkLoadRequested: false, worldMutationUsedByProbe: false};
  }
  const ctx = {api: async (method, path, body) => {
    calls.push({method, path, body});
    if (method === 'GET' && path === '/v0/input/state') return {
      pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false};
    if (method === 'POST' && path === '/v0/input/release-all') {
      assert.deepEqual(body, {}); if (releaseError) throw new Error('release failed'); return {type: 'input.release_all'};
    }
    if (method === 'GET' && path === '/v0/player?category=USER') return {available: true, gameMode: 'creative'};
    if (method === 'GET' && path.startsWith('/v0/world/block?')) return {available: true, dataCategory: 'USER', block, targetRef: 'owned-block'};
    if (method === 'GET' && path.startsWith('/v0/world/entities?')) return {dataCategory: 'USER', entitiesTruncated: false,
      chunkLoadRequested: false, entities: cart ? [{type: 'minecraft:command_block_minecart', x: 2.5, y: 80, z: .5, targetRef: 'owned-cart'}] : []};
    if (method === 'POST' && path === '/v0/pipelines') {
      assert.equal(body.steps.length, 1); const [step] = body.steps;
      let denied = false;
      if (step.type === 'world.action') {
        assert.equal(step.category, 'USER'); assert.equal(step.action, 'use');
        if (step.targetRef === 'owned-cart') {assert(cart); show(CLASS + 'MinecartCommandBlockEditScreen');}
        else if (step.targetRef === 'owned-block' && block === 'minecraft:test_block') show(CLASS + 'TestBlockEditScreen');
        else if (step.targetRef === 'owned-block' && block === 'minecraft:test_instance_block') {show(CLASS + 'TestInstanceBlockEditScreen'); denied = initDenied;}
        else assert.fail('Unexpected world target');
      } else {
        assert.equal(step.type, 'ui.action'); assert.deepEqual(Object.keys(step.selector), ['nodeId']);
        if (step.selector.nodeId === 'test-id') {assert.equal(step.action, 'type_text'); draft = step.text; revision++;}
        else {
          assert.equal(step.action, 'click');
          if (screen.endsWith('MinecartCommandBlockEditScreen')) {
            if (packetLeak) minecartPackets++;
            if (storedMutation) storedCart = 'reload';
          }
          show('');
        }
        denied = true;
      }
      const operationId = String(operations.size + 1);
      operations.set(operationId, pending ? {state: 'executing'} : {state: denied ? 'failed' : 'completed',
        ...(denied ? {errorCode: 'PLAYER_COMMAND_OUT_OF_SCOPE'} : {}), result: {commandSubmissions: []}});
      return {operationId};
    }
    if (method === 'POST' && /^\/v0\/operations\/\d+\/wait$/.test(path)) return operations.get(path.split('/')[3]);
    assert.fail('Unexpected API: ' + method + ' ' + path);
  }, repo: '.', target, processId: 7, samples, tree: async () => {calls.push({tree: true}); return view();},
  command: async text => {
    calls.push({command: text}); commandCount++;
    if (text.startsWith(PREFIX)) snapshot = receipt(text.slice(PREFIX.length));
    else if (text.startsWith('setblock ')) block = text.split(' ')[4].replace(/\[.*/, '');
    else if (text.startsWith('summon ')) {cart = true; storedCart = '';}
    else if (text.startsWith('kill ')) {assert.match(text, /tag=ui_management_[0-9a-f]{32}/); cart = false;}
    else assert.match(text, /^(fill |tp )/);
    return {dataCategory: 'USER', effectConfirmed: false};
  }, key: async code => {calls.push({key: code}); assert.equal(code, 256); assert(screen); show('');},
  wait: async (predicate, description) => {const current = view(); assert(predicate(current), description); return current;},
  nodes: value => value.children,
  ui: async (selector, action, options) => {
    calls.push({ui: selector, action, options}); assert.deepEqual(Object.keys(selector), ['nodeId']);
    assert(['cart-command', 'test-message'].includes(selector.nodeId)); assert.equal(action, 'type_text'); draft = options.text; revision++;
    return {targetingSource: 'interaction_tree', directMutationUsed: false};
  }, enterWorld: async () => {calls.push({enterWorld: true}); show('');},
  check: async (name, run) => {assert.match(name, /^UI command management:/); if (filterAll) return; await run(); checks.push(name);},
  readProbe: async () => structuredClone(snapshot)};
  return {ctx, calls, checks, samples, state: () => ({screen, cart, block}),
    services: {now: () => clock, sleep: async milliseconds => {clock += milliseconds;}}};
}

test('actual helper visits all three modern native classes and rejects management actions with unchanged server proof', async () => {
  const run = controlled(); await uiCommandManagementActions(run.ctx, run.services);
  assert.equal(run.checks.length, 3);
  assert.deepEqual(run.samples.map(value => value.case), ['ui_command_minecart_configuration_denied',
    'ui_test_block_configuration_denied', 'ui_test_instance_management_denied']);
  assert.deepEqual(run.samples.map(value => value.screenClass), ['MinecartCommandBlockEditScreen', 'TestBlockEditScreen',
    'TestInstanceBlockEditScreen'].map(value => CLASS + value));
  for (const sample of run.samples) {
    assert.equal(sample.managementExecutionValidated, false); assert.equal(sample.semanticTargets, true);
    assert.equal(sample.handwrittenCoordinates, false); assert.equal(sample.directCallbackUsed, false);
    assert.equal(sample.readbackAuthority, 'DIAGNOSTIC_native_server_receipt_observation');
    assert.equal(sample.after.commandMinecartPackets, sample.before.commandMinecartPackets);
    assert.equal(sample.after.testBlockPackets, sample.before.testBlockPackets);
    assert.equal(sample.after.testInstanceActionPackets, sample.before.testInstanceActionPackets);
  }
  assert.equal(run.samples[2].openOutcome.state, 'failed', 'A refused native INIT does not erase its actual Screen');
  assert.equal(run.samples[2].localDraftRollbackClaimed, false);
  assert.deepEqual(run.state(), {screen: '', cart: false, block: 'minecraft:air'});
  assert.equal(run.calls.filter(value => value.command?.startsWith('summon ')).length, 1);
});

test('old Targets actually operate the command minecart and report only absent native Test classes as unavailable', async () => {
  for (const target of ['1.20.1-forge', '1.21.1-neoforge']) {
    const run = controlled({target}); await uiCommandManagementActions(run.ctx, run.services);
    assert.equal(run.checks.length, 1); assert.equal(run.samples[0].screenClass, CLASS + 'MinecartCommandBlockEditScreen');
    assert.equal(run.samples[1].case, 'ui_test_management_native_absent'); assert.equal(run.samples[1].operationCoverage, false);
    assert(!run.calls.some(value => value.command?.includes('minecraft:test_')));
  }
});

test('server packet leakage and native stored-value changes fail despite a syntactically correct rejected response', async () => {
  for (const options of [{packetLeak: true}, {storedMutation: true}]) {
    const run = controlled(options);
    await assert.rejects(uiCommandManagementActions(run.ctx, run.services), /changed native server evidence/);
    assert.equal(run.samples.length, 0); assert.equal(run.calls.filter(value => value.command?.startsWith('summon ')).length, 1);
    assert.deepEqual(run.state(), {screen: '', cart: false, block: 'minecraft:air'});
  }
});

test('the current process and exact private fixture owner are established before any Arrange mutation', async () => {
  for (const options of [{foreignPid: true}, {unknownOwner: true}]) {
    const run = controlled(options); await assert.rejects(uiCommandManagementActions(run.ctx, run.services));
    assert.equal(run.samples.length, 0); assert.equal(run.checks.length, 0);
    assert(!run.calls.some(value => /^(setblock|fill|summon|kill|tp) /.test(value.command ?? '')));
  }
});

test('executing is not a successful terminal operation and no rejected input is automatically replayed', async () => {
  const run = controlled({pending: true});
  await assert.rejects(uiCommandManagementActions(run.ctx, run.services), /must reach a terminal state/);
  assert.equal(run.samples.length, 0); assert.equal(run.calls.filter(value => value.path === '/v0/pipelines').length, 1);
  assert.deepEqual(run.state(), {screen: '', cart: false, block: 'minecraft:air'});
});

test('primary native evidence failure and cleanup failure are both retained', async () => {
  const run = controlled({packetLeak: true, releaseError: true});
  await assert.rejects(uiCommandManagementActions(run.ctx, run.services), error => error instanceof AggregateError
    && error.errors.some(error => /changed native server evidence/.test(error.message))
    && error.errors.some(error => /release failed/.test(error.message)));
  assert.equal(run.samples.length, 0); assert.equal(run.state().cart, false);
});

test('a successful native open still requires the same rejected Test management contract', async () => {
  const run = controlled({initDenied: false}); await uiCommandManagementActions(run.ctx, run.services);
  assert.equal(run.samples[2].openOutcome.state, 'completed'); assert.equal(run.samples[2].query.errorCode, 'PLAYER_COMMAND_OUT_OF_SCOPE');
  assert.equal(run.samples[2].reset.errorCode, 'PLAYER_COMMAND_OUT_OF_SCOPE');
});

test('filtering every check performs no API, command, world navigation or cleanup, including old Target NA handling', async () => {
  for (const target of ['26.3-neoforge', '1.20.1-forge']) {
    const run = controlled({target, filterAll: true, initialScreen: 'net.minecraft.client.gui.screens.TitleScreen'});
    await uiCommandManagementActions(run.ctx, run.services);
    assert.deepEqual(run.calls, []); assert.deepEqual(run.checks, []); assert.deepEqual(run.samples, []);
    assert.equal(run.state().screen, 'net.minecraft.client.gui.screens.TitleScreen');
  }
});

test('selected checks reuse an already open gameplay world and arrange once, while a Title Screen enters only once', async () => {
  const current = controlled(); await uiCommandManagementActions(current.ctx, current.services);
  assert.equal(current.calls.filter(value => value.enterWorld).length, 0);
  assert.equal(current.calls.filter(value => value.command === 'fill -1 79 -1 4 79 2 minecraft:stone').length, 1);
  const title = controlled({initialScreen: 'net.minecraft.client.gui.screens.TitleScreen'});
  await uiCommandManagementActions(title.ctx, title.services);
  assert.equal(title.calls.filter(value => value.enterWorld).length, 1);
  assert.equal(title.calls.filter(value => value.command === 'fill -1 79 -1 4 79 2 minecraft:stone').length, 1);
});
