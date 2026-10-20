import assert from 'node:assert/strict';
import test from 'node:test';
import {hardcoreWorldName, uiHardcoreActions} from './ui-hardcore-actions.mjs';

function nativeContext({existingFixture = false, duplicateMode = false, noHardcoreMode = false,
  normalHearts = false, hiddenHealth = false, entryError, enterWorld = true, changedLease = false,
  saveFails = false, controlMode = 'TAKEOVER'} = {}) {
  let screen = 'TitleScreen', name = 'New World', mode = 0, context = 0, inWorld = false, created = false;
  const worlds = [{worldId: 'baseline', worldName: 'UI-Conformance'},
    ...(existingFixture ? [{worldId: 'existing-hardcore', worldName: hardcoreWorldName}] : [])];
  const calls = [], semantic = [], checks = [], samples = [];
  const node = (nodeId, translationKey, label, extra = {}) => ({nodeId, translationKey, label,
    role: 'button', visible: true, active: true, actions: ['click'], ...extra});
  const input = () => ({pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false,
    inputGeneration: context, inputContextGeneration: context, inputContextId: 'native-' + context, inputContextAvailable: inWorld});
  const tree = async () => {
    let children = [];
    if (screen === 'TitleScreen') children = [node('single', 'menu.singleplayer', 'Singleplayer')];
    if (screen === 'SelectWorldScreen') children = [...worlds.map(world => node(world.worldId, '', world.worldName, world)), node('new', 'selectWorld.create', 'Create New World')];
    if (screen === 'CreateWorldScreen') {
      const label = 'Game Mode: ' + (noHardcoreMode ? ['Survival', 'Creative'][mode % 2] : ['Survival', 'Hardcore', 'Creative'][mode % 3]);
      children = [node('name', 'selectWorld.enterName', 'World Name', {role: 'text_field', value: name, actions: ['type_text']}),
        node('mode', 'selectWorld.gameMode', label), ...(duplicateMode ? [node('mode-duplicate', 'selectWorld.gameMode', label)] : []),
        node('create', 'selectWorld.create', 'Create New World')];
    }
    if (screen === 'PauseScreen') children = [node('save', 'menu.returnToMenu', 'Save and Quit to Title')];
    const label = normalHearts ? 'hud/heart/full' : 'hud/heart/hardcore_full';
    return {dataCategory: 'USER', screenClass: screen ? 'net.minecraft.client.gui.screens.' + screen : '', overlayIdentity: 0,
      treeTruncated: false, width: 320, height: 240, children,
      presentation: {hudIconSampleStatus: 'CURRENT', children: !screen && inWorld ? [{controlKey: 'hud.vitals.health.0', label,
        resourceId: 'minecraft:' + label, role: 'status_icon', visible: true, active: false, actions: [],
        iconSource: 'last_completed_native_hud_draw', valueSemantics: 'ordered_native_glyph_composition_not_raw_statistics',
        x: 10, y: 200, width: 9, height: 9, ...(hiddenHealth ? {health: 20} : {})}] : []}};
  };
  const api = async (method, path, body) => {
    calls.push({method, path, body}); assert.equal(method, 'GET');
    if (path === '/v0/input/state') return input();
    if (path === '/v0/control/status') return {mode: controlMode, takeoverActive: controlMode === 'TAKEOVER',
      leaseId: changedLease && inWorld ? 'different-lease' : 'same-lease', modeVersion: {controlSessionId: 'control', generation: 1}};
    if (path === '/v0/player?category=USER') return {available: inWorld, dataCategory: 'USER', gameMode: 'survival'};
    if (path === '/v0/session') return {target: '26.3-neoforge', inWorld};
    assert.fail('Unexpected API ' + path);
  };
  const ui = async ({nodeId}, action = 'click', extra = {}) => {
    const found = (await tree()).children.find(value => value.nodeId === nodeId); assert(found, nodeId);
    assert(found.actions.includes(action)); semantic.push({nodeId, action, extra});
    if (nodeId === 'single') screen = 'SelectWorldScreen';
    else if (nodeId === 'new') screen = 'CreateWorldScreen';
    else if (nodeId === 'name') {assert.equal(action, 'type_text'); name = extra.text;}
    else if (nodeId === 'mode') mode++;
    else if (nodeId === 'create') {
      assert.equal(name, hardcoreWorldName); assert.equal(mode % 3, 1, 'Native mode selection, not a server command');
      created = true;
      if (enterWorld) {screen = ''; inWorld = true; context++; worlds.push({worldId: 'created-hardcore', worldName: name});}
      if (entryError) throw entryError;
    } else assert.fail('No world reopen, flag mutation or unrelated semantic target: ' + nodeId);
    return {targetingSource: 'interaction_tree', directMutationUsed: false};
  };
  const finish = () => {if (inWorld) context++; inWorld = false; screen = 'TitleScreen';};
  const ctx = {target: '26.3-neoforge', api, tree, nodes: value => value.children ?? [], ui,
    wait: async (predicate, label) => {const value = await tree(); assert(predicate(value), label); return value;},
    key: async code => {assert.equal(code, 256); assert(inWorld); assert.equal(screen, ''); screen = 'PauseScreen';},
    title: async () => {finish(); return tree();},
    saveQuit: async quit => {assert.equal(quit.nodeId, 'save'); assert.equal(screen, 'PauseScreen');
      if (saveFails) throw new Error('native save failed'); finish(); return {outcome: 'world_exit_independently_observed', requestReplayed: false};},
    check: async (label, run) => {checks.push(label); await run();}, samples};
  return {ctx, calls, semantic, checks, samples, state: () => ({screen, created, inWorld, worlds, mode})};
}

const cancelledEntry = () => Object.assign(new Error('Native entry epoch changed'), {httpStatus: 409,
  nativeError: {error: 'CONTROL_INPUT_GENERATION_CHANGED', executionOutcome: 'not_confirmed', retrySafe: false}});

test('native semantic creation selects Hardcore, observes actual drawn hearts, saves and preserves existing worlds', async () => {
  const value = nativeContext(); await uiHardcoreActions(value.ctx);
  assert.equal(value.checks.length, 1); assert.equal(value.samples.length, 1);
  const sample = value.samples[0]; assert.equal(sample.case, 'hud_hardcore_native_world');
  assert.equal(sample.selectedMode.label, 'Game Mode: Hardcore'); assert.equal(sample.world.worldId, 'created-hardcore');
  assert.equal(sample.entry.requestOutcome, 'response_received'); assert.equal(sample.entry.independentlyObserved, true);
  assert.equal(sample.heartNodes[0].label, 'hud/heart/hardcore_full'); assert.equal(sample.hardcoreFlagMutationUsed, false);
  assert.equal(sample.deathTested, false); assert.equal(sample.worldDeletionUsed, false);
  assert.equal(value.state().screen, 'TitleScreen'); assert.equal(value.state().inWorld, false);
  assert.equal(value.calls.some(call => call.method !== 'GET'), false);
  assert.deepEqual(value.semantic.map(call => call.nodeId), ['single', 'new', 'name', 'mode', 'create', 'single']);
});

test('context-cancelled creation is never replayed or called successful, but independent world and hearts can prove entry', async () => {
  const value = nativeContext({entryError: cancelledEntry()}); await uiHardcoreActions(value.ctx);
  assert.equal(value.samples[0].entry.requestOutcome, 'not_confirmed'); assert.equal(value.samples[0].entry.requestReplayed, false);
  assert.equal(value.semantic.filter(call => call.nodeId === 'create').length, 1);
  assert.equal(value.samples[0].entry.response, undefined);
});

test('a pre-existing named world, ambiguous mode or bounded cycle without Hardcore never reaches creation', async () => {
  for (const options of [{existingFixture: true}, {duplicateMode: true}, {noHardcoreMode: true}]) {
    const value = nativeContext(options); await assert.rejects(uiHardcoreActions(value.ctx), assert.AssertionError);
    assert.equal(value.state().created, false); assert.equal(value.state().screen, 'TitleScreen'); assert.equal(value.samples.length, 0);
    assert(value.semantic.filter(call => call.nodeId === 'mode').length <= 3);
  }
});

test('ordinary hearts, hidden raw statistics, no independently entered world or changed Lease cannot become coverage', async () => {
  for (const options of [{normalHearts: true}, {hiddenHealth: true}, {enterWorld: false, entryError: cancelledEntry()}, {changedLease: true}]) {
    const value = nativeContext(options); await assert.rejects(uiHardcoreActions(value.ctx), assert.AssertionError);
    assert.equal(value.samples.length, 0); assert.equal(value.state().screen, 'TitleScreen');
    assert.equal(value.semantic.filter(call => call.nodeId === 'create').length, 1);
  }
});

test('uncertain transport failure is not replayed, and a failed native save does not publish a successful sample', async () => {
  const failure = new Error('connection lost after submission');
  const uncertain = nativeContext({entryError: failure});
  await assert.rejects(uiHardcoreActions(uncertain.ctx), error => error === failure);
  assert.equal(uncertain.samples.length, 0); assert.equal(uncertain.semantic.filter(call => call.nodeId === 'create').length, 1);
  const failedSave = nativeContext({saveFails: true}); await assert.rejects(uiHardcoreActions(failedSave.ctx), /native save failed/);
  assert.equal(failedSave.samples.length, 0); assert.equal(failedSave.state().screen, 'TitleScreen');
});

test('READ is not silently escalated to create a world', async () => {
  const value = nativeContext({controlMode: 'READ'}); await assert.rejects(uiHardcoreActions(value.ctx), assert.AssertionError);
  assert.equal(value.semantic.length, 0); assert.equal(value.samples.length, 0);
});
