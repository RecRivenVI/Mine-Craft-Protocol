import assert from 'node:assert/strict';
import test from 'node:test';
import {cameraVariantRows, hudCameraVariantActions} from './hud-camera-variant-actions.mjs';

const screenClass = name => name ? 'net.minecraft.client.gui.screens.' + name : '';
const control = (nodeId, extra = {}) => ({nodeId, role: 'button', visible: true, active: true,
  actions: ['hover', 'click'], ...extra});
const draw = kind => ({nodeId: 'draw-' + kind, controlKey: 'hud.overlay.' + kind + '.texture.0',
  role: 'status_icon', visible: true, active: false, actions: [],
  renderPhase: kind === 'sleep' ? 'sleep_overlay' : 'camera_overlay',
  resourceId: kind === 'portal' ? 'minecraft:block/nether_portal'
    : kind === 'sleep' ? undefined : 'minecraft:textures/misc/' + kind + '.png',
  iconSource: 'last_completed_native_hud_draw', boundsAvailable: true,
  boundsSemantics: 'native_transformed_quad_visible_aabb', x: 0, y: 0, width: 640, height: 360, opacity: .5});

function fixture({target = '26.3-neoforge', only, missingDraw, masterVolume = 0, missingRule = false, noBedScreen = false,
  operationState = 'completed', drawMutation, delayedTeleport = false, ignoreVignetteClick = false, initialVignette = true,
  portalCompletes = true, portalLeaves = true, accessibilityCount = 1} = {}) {
  const calls = [], samples = [], checks = [], operations = new Map(), blocks = new Map();
  const legacy = target.startsWith('1.'), messages = [];
  let clock = 0, tick = 0, screen = '', mode = 'creative', held = 0, item = 'minecraft:air', count = 0;
  let x = .5, y = 80, z = .5, yaw = 0, pitch = 0, nausea = false, portal = false;
  let sleepingPercentage = 100, scale = 1, vignette = initialVignette, listOffset = 0, pendingTeleport, ruleSearch = '', hidden = false;
  const player = () => ({available: true, dimension: 'minecraft:overworld', gameMode: mode, x, y, z, yaw, pitch});
  function treeValue() {
    tick++;
    let children = [];
    const button = (key, extra) => control(key, {translationKey: key, ...extra});
    if (screen === 'PauseScreen') children = ['menu.options', ...(!legacy && target !== '26.1.2-neoforge' ? ['options.worldOptions.button'] : []), 'menu.returnToGame'].map(key => button(key));
    else if (screen === 'OptionsScreen') children = ['options.video', 'options.sounds', 'gui.done',
      ...(target === '26.1.2-neoforge' ? ['options.worldOptions.button'] : []),
      ...Array(accessibilityCount).fill(target === '1.20.1-forge' ? 'options.accessibility.title' : 'options.accessibility')].map(key => button(key));
    else if (screen === 'VideoSettingsScreen') children = [...(!legacy ? [button('vignette', {label: 'Show Vignette: ' + (vignette ? 'ON' : 'OFF')})] : []), button('gui.done')];
    else if (screen === 'SoundOptionsScreen') children = [button('master', {role: 'slider', label: 'Master Volume: OFF', value: masterVolume,
      actions: ['hover', 'set_value']}), button('gui.done')];
    else if (screen === 'AccessibilityOptionsScreen') children = [button('screen-effects', {role: 'slider', label: 'Screen Effects: ' + scale,
      value: scale, actions: ['hover', 'set_value']}), button('gui.done')];
    else if (screen === 'WorldOptionsScreen') children = [button('editGamerule.inGame.button'), button(target === '26.1.2-neoforge' ? 'gui.done' : 'gui.cancel')];
    else if (screen === 'InWorldGameRulesScreen') children = [button('gui.cancel'),
      control('rule-search', {role: 'text_field', class: 'net.minecraft.client.gui.components.EditBox', label: '',
        value: ruleSearch, actions: ['hover', 'type_text']}),
      ...(missingRule || ruleSearch !== 'sleep' ? [] : [control('sleep-rule', {role: 'text_field',
        label: 'Sleep percentage\nplayers_sleeping_percentage\n', value: String(sleepingPercentage), actions: ['hover', 'type_text']})]),
      control('list', {role: 'list', actions: ['scroll'], scrollAmount: listOffset})];
    else if (screen === 'InBedChatScreen') children = [button('multiplayer.stopSleeping')];
    const presentation = [{nodeId: 'hotbar-0', controlKey: 'hud.hotbar.0', selected: true, item, count}];
    if (!screen && !hidden) presentation.push(...messages.slice(-8).map((label, index) => ({nodeId: 'chat-' + index,
      renderPhase: 'chat_background', role: 'hud_text', visible: true, active: false, actions: [], boundsAvailable: true,
      label, labelSource: 'last_completed_native_gui_text_draw', iconSource: 'last_completed_native_gui_draw',
      textPhase: 'drawn', boundsSemantics: 'native_text_draw_viewport_scissor_intersection',
      x: 10, y: 200 + index * 10, width: 320, height: 9, opacity: 1})));
    for (const kind of ['vignette', 'nausea', 'portal', 'sleep']) {
      const shown = kind === 'sleep' ? screen === 'InBedChatScreen' : screen === '' && !hidden
        && (kind === 'vignette' ? vignette : kind === 'nausea' ? nausea && scale < 1 : portal);
      if (shown && kind !== missingDraw) {
        const node = draw(kind); if (drawMutation) drawMutation(node, kind); presentation.push(node);
      }
    }
    return {dataCategory: 'USER', target, screenClass: screenClass(screen),
      screenIdentity: screen ? 1 : 0, overlayIdentity: 0, treeTruncated: false, clientTick: tick,
      width: 640, height: 360, children, presentation: {children: presentation}};
  }
  function applyTeleport(value) {
    [x, y, z, yaw, pitch] = value; portal = false;
  }
  const ctx = {target, samples, nodes: view => view.children,
    tree: async () => treeValue(),
    wait: async (predicate, label) => {const value = treeValue(); assert(predicate(value), label); return value;},
    check: async (name, run) => {
      assert(name.startsWith('HUD camera variants:'));
      if (only && !name.includes(only)) return;
      await run(); checks.push(name);
    },
    key: async code => {calls.push({key: code}); assert.equal(screen, '');
      if (code === 290) hidden = !hidden; else {assert.equal(code, 256); screen = 'PauseScreen';}},
    ui: async (selector, action, extra) => {
      assert.deepEqual(Object.keys(selector), ['nodeId']);
      const node = treeValue().children.find(value => value.nodeId === selector.nodeId);
      assert(node && node.actions.includes(action), 'The mock consumes a currently exposed real control');
      calls.push({selector, action, extra, screen});
      const id = selector.nodeId;
      if (id === 'menu.options') screen = 'OptionsScreen';
      else if (id === 'menu.returnToGame') screen = '';
      else if (id === 'options.worldOptions.button') screen = 'WorldOptionsScreen';
      else if (id === 'editGamerule.inGame.button') {screen = 'InWorldGameRulesScreen'; ruleSearch = '';}
      else if (id === 'options.video') screen = 'VideoSettingsScreen';
      else if (id === 'options.sounds') screen = 'SoundOptionsScreen';
      else if (id === (target === '1.20.1-forge' ? 'options.accessibility.title' : 'options.accessibility')) screen = 'AccessibilityOptionsScreen';
      else if (id === 'gui.done') screen = screen === 'OptionsScreen' ? 'PauseScreen' : 'OptionsScreen';
      else if (id === 'gui.cancel') screen = screen === 'InWorldGameRulesScreen' ? 'WorldOptionsScreen' : 'PauseScreen';
      else if (id === 'multiplayer.stopSleeping') screen = '';
      else if (id === 'screen-effects') {assert.equal(action, 'set_value'); scale = extra.value;}
      else if (id === 'vignette') {assert.equal(action, 'click'); if (!ignoreVignetteClick) vignette = !vignette;}
      else if (id === 'rule-search') {assert.equal(action, 'type_text'); assert.equal(extra.text, 'sleep'); ruleSearch = extra.text;}
      else if (id === 'list') {assert.deepEqual(extra, {yOffset: -4}); listOffset += 4;}
      else assert.fail('Unexpected actual UI action: ' + id);
      return {targetingSource: 'interaction_tree', directMutationUsed: false};
    },
    command: async text => {
      calls.push({command: text}); assert.equal(screen, '', 'Diagnostic Arrange cannot silently dismiss a Screen');
      if (text.startsWith('gamemode ')) mode = text.split(' ')[1];
      else if (text.includes('tp @s ')) {
        const match = text.match(/tp @s ([\d.-]+) ([\d.-]+) ([\d.-]+) ([\d.-]+) ([\d.-]+)$/); assert(match);
        const position = match.slice(1).map(Number);
        if (delayedTeleport) pendingTeleport = {position, reads: 2}; else applyTeleport(position);
      } else if (text.startsWith('fill ')) {
        const match = text.match(/^fill (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (-?\d+) (minecraft:\w+)(?:\[.*])?$/); assert(match);
        const [minX, minY, minZ, maxX, maxY, maxZ] = match.slice(1, 7).map(Number);
        for (let bx = minX; bx <= maxX; bx++) for (let by = minY; by <= maxY; by++) for (let bz = minZ; bz <= maxZ; bz++)
          blocks.set([bx, by, bz].join(','), match[7]);
      } else if (/^gamerule (?:minecraft:players_sleeping_percentage|playersSleepingPercentage) \d+$/.test(text)) sleepingPercentage = Number(text.split(' ').at(-1));
      else if (text.startsWith('tellraw @s ')) messages.push(JSON.parse(text.slice('tellraw @s '.length)).text);
      else if (text === 'gamerule playersSleepingPercentage') messages.push('Gamerule playersSleepingPercentage is currently set to: ' + sleepingPercentage);
      else if (text.startsWith('item replace entity @s weapon.mainhand with ')) {
        const parts = text.split(' '); item = parts[6]; count = item === 'minecraft:air' ? 0 : Number(parts[7]);
      } else if (text === 'effect give @s minecraft:nausea 12 0 true') nausea = true;
      else if (text === 'effect clear @s minecraft:nausea') nausea = false;
      else assert(['time set night', 'time set day', 'weather clear'].includes(text), text);
      return {accepted: true, effectConfirmed: false};
    },
    api: async (method, path, body) => {
      calls.push({method, path, body});
      if (path === '/v0/input/state') return {pressedKeyCount: held, pressedButtonCount: 0,
        inputCleanupPending: false, inputCleanupFailed: false};
      if (path === '/v0/player?category=USER') {
        if (pendingTeleport && --pendingTeleport.reads === 0) {applyTeleport(pendingTeleport.position); pendingTeleport = undefined;}
        return player();
      }
      if (path.startsWith('/v0/world/block?')) {
        const query = new URLSearchParams(path.split('?')[1]); assert.equal(query.get('category'), 'USER');
        const position = ['x', 'y', 'z'].map(key => query.get(key)).join(',');
        return {available: true, block: blocks.get(position) ?? 'minecraft:air', dataCategory: 'USER',
          chunkLoadRequested: false, targetRef: 'block:' + position};
      }
      if (path === '/v0/input/release-all') {
        held = 0;
        for (const operation of operations.values()) if (operation.state === 'running') operation.state = 'cancelled';
        return {cleanupCompleted: true};
      }
      if (path === '/v0/pipelines') {
        assert.equal(method, 'POST'); assert.equal(screen, '');
        const operationId = 'op-' + (operations.size + 1), first = body.steps[0];
        let result = {state: operationState};
        if (first.type === 'key') {
          assert.deepEqual(body.steps, [{type: 'key', key: 87, action: 1}, {type: 'delay', durationMs: 1500}, {type: 'key', key: 87, action: 0}]);
          assert.equal(mode, 'survival'); assert.equal(blocks.get('0,80,2'), 'minecraft:nether_portal');
          held = 1; portal = true; z = 2.2; result = {state: 'running', portalMovement: true};
        } else {
          assert.equal(first.type, 'world.action'); assert.equal(first.category, 'USER');
          assert.equal(body.steps.length, 1); assert(first.targetRef.startsWith('block:'));
          assert.equal(first.x, undefined); assert.equal(first.y, undefined);
          if (first.action === 'place') {
            assert.equal(item, 'minecraft:red_bed'); assert.equal(count, 1);
            assert.equal(first.targetRef, 'block:0,79,2'); assert.equal(first.face, 'up');
            assert.deepEqual(first.hit, {u: .5, v: .5}); assert.equal(first.expectedDestinationBlockId, 'minecraft:air');
            blocks.set('0,80,2', 'minecraft:red_bed'); blocks.set('0,80,3', 'minecraft:red_bed');
          } else {
            assert.equal(first.action, 'use'); assert.equal(first.targetRef, 'block:0,80,2');
            assert.equal(sleepingPercentage, 101); assert.equal(item, 'minecraft:air');
            if (!noBedScreen) screen = 'InBedChatScreen';
          }
          result.result = {steps: [{stepType: 'world.action', result: {dataCategory: 'USER', evidenceAuthority: 'PLAYTEST',
            directMutationUsed: false, effectConfirmed: false}}]};
        }
        operations.set(operationId, result); return {operationId};
      }
      if (/^\/v0\/operations\/op-\d+\/wait$/.test(path)) {
        const operation = operations.get(path.split('/')[3]);
        if (operation.portalMovement && operation.state === 'running' && portalCompletes) {
          operation.state = 'completed'; held = 0;
          if (portalLeaves) {portal = false; z = 5;}
        }
        return operation;
      }
      assert.fail('Unexpected Runtime call: ' + method + ' ' + path);
    },
  };
  return {ctx, calls, checks, samples, services: {now: () => clock, sleep: async milliseconds => {clock += milliseconds;}},
    state: () => ({screen, mode, x, y, z, yaw, pitch, held, item, count, sleepingPercentage, scale, vignette, nausea, portal}), hudHidden: () => hidden};
}

test('the actual helper proves four finite native branches through semantic controls and USER readback', async () => {
  const run = fixture({delayedTeleport: true}); await hudCameraVariantActions(run.ctx, run.services);
  assert.equal(run.checks.length, 4);
  assert.deepEqual(run.samples.map(sample => sample.case), ['portal', 'sleep', 'nausea', 'vignette'].map(kind => 'hud_camera_variant_' + kind));
  assert.equal(run.samples[0].movementState, 'completed'); assert.equal(run.samples[0].dimensionTransferValidated, false);
  assert.equal(run.samples[0].observationMayCancelActiveMovement, false);
  assert.equal(run.samples[0].leftPortalThroughNativeMovement, true);
  const submitted = run.calls.findIndex(call => call.path === '/v0/pipelines');
  const completed = run.calls.findIndex((call, index) => index > submitted && /\/v0\/operations\/.+\/wait$/.test(call.path ?? ''));
  const stopped = run.calls.findIndex((call, index) => index > submitted && call.path === '/v0/input/release-all');
  assert(completed > submitted && stopped > completed, 'Do not stop the player inside the portal while waiting for HTTP or restoring the fixture');
  assert.equal(run.samples[1].stopSleepingThroughNativeButton, true);
  assert.equal(run.samples[1].sleepRuleRestored, 100); assert.equal(run.samples[1].originalWorldClockRestored, false);
  assert.equal(run.samples[2].screenEffectScaleRestored, 1); assert.equal(run.samples[3].vignetteOptionRestored, true);
  for (const sample of run.samples) {
    assert.equal(sample.finalPixelAssertion, false); assert.equal(sample.directRendererSetterUsed, false);
    assert.equal(sample.input.pressedKeyCount, 0); assert.equal(sample.input.pressedButtonCount, 0);
  }
  assert.deepEqual(run.state(), {screen: '', mode: 'creative', x: .5, y: 80, z: .5, yaw: 0, pitch: 0,
    held: 0, item: 'minecraft:air', count: 0, sleepingPercentage: 100, scale: 1, vignette: true, nausea: false, portal: false});
  assert(!run.calls.some(call => call.selector?.nodeId === 'master'));
  assert(!run.calls.some(call => call.path?.includes('category=DEBUG') || call.path?.includes('/debug/')));
  assert(run.calls.filter(call => call.selector).every(call => Object.keys(call.selector).join() === 'nodeId'));
  assert.equal(run.calls.filter(call => call.selector?.nodeId === 'rule-search').length, 3);
  assert(!run.calls.some(call => call.selector?.nodeId === 'sleep-rule' || call.selector?.nodeId === 'list'));
});

test('four non-reference target adaptations retain actual draw assertions, rule restoration and muted Master', async () => {
  for (const target of ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-fabric']) {
    const run = fixture({target}); await hudCameraVariantActions(run.ctx, run.services);
    assert.equal(run.checks.length, 4); assert.equal(run.samples.length, 4);
    assert.equal(run.state().sleepingPercentage, 100); assert.equal(run.state().scale, 1); assert.equal(run.state().screen, '');
    assert.equal(run.state().held, 0); assert.equal(run.hudHidden(), false);
    const sleep = run.samples.find(sample => sample.case === 'hud_camera_variant_sleep');
    assert.deepEqual(sleep.sleepRuleReads.map(value => value.value), [100, 101, 100]);
    assert(!run.calls.some(call => call.path?.includes('DEBUG') || call.path?.includes('/debug/')));
    const vignette = run.samples.find(sample => sample.case === 'hud_camera_variant_vignette');
    assert.equal(vignette.masterBefore, 0); assert.equal(vignette.masterAfter, 0); assert.equal(vignette.masterVolumeWritten, false);
    if (target.startsWith('1.')) {
      assert(sleep.sleepRuleReads.every(value => value.source === 'actual_native_chat_draw' && value.commandSubmissionIsNotReadback));
      assert.equal(run.calls.filter(call => call.command === 'gamerule playersSleepingPercentage').length, 3);
      assert.equal(run.calls.filter(call => call.command?.startsWith('tellraw @s ')).length, 3);
      assert(!run.calls.some(call => ['options.worldOptions.button', 'editGamerule.inGame.button', 'rule-search', 'vignette'].includes(call.selector?.nodeId)));
      assert.equal(vignette.nativeVignetteOptionAvailable, false); assert.equal(vignette.nativeVideoOptionOffSuppressesDraw, false);
      assert.equal(vignette.vignetteOptionRestored, 'not_applicable'); assert.equal(vignette.f1ProjectedNodeCount, 0);
      assert.equal(vignette.userProjectionOcclusionAsserted, true); assert.equal(vignette.framebufferDarknessAsserted, false);
      assert(vignette.screenOcclusion.endsWith('PauseScreen'));
      assert.equal(run.calls.filter(call => call.key === 290).length, 2);
    } else {
      assert.equal(vignette.nativeVignetteOptionAvailable, true); assert.equal(vignette.nativeVideoOptionOffSuppressesDraw, true);
      assert.equal(run.calls.filter(call => call.selector?.nodeId === 'rule-search').length, 3);
      assert.equal(run.calls.some(call => call.command?.startsWith('tellraw @s ')), false);
    }
  }
});

test('a legacy target without an actual vignette draw does not invent a missing toggle or claim coverage', async () => {
  const run = fixture({target: '1.20.1-forge', only: 'native video Vignette', initialVignette: false});
  await assert.rejects(hudCameraVariantActions(run.ctx, run.services), /Native vignette overlay/);
  assert.equal(run.samples.length, 0); assert.equal(run.state().vignette, false); assert.equal(run.hudHidden(), false);
  assert(!run.calls.some(call => call.selector?.nodeId === 'vignette' || call.key === 290));
  assert.equal(run.state().screen, '');
});

test('no actual portal draw fails, stops owned movement and restores the native player without a PASS sample', async () => {
  const run = fixture({only: 'real survival movement', missingDraw: 'portal'});
  await assert.rejects(hudCameraVariantActions(run.ctx, run.services), /Native portal overlay/);
  assert.equal(run.checks.length, 0); assert.equal(run.samples.length, 0);
  assert.equal(run.state().held, 0); assert.equal(run.state().mode, 'creative'); assert.equal(run.state().z, .5);
  assert(run.calls.some(call => call.path === '/v0/input/release-all'));
});

test('portal observation cannot stop active movement or treat completion inside the portal as a safe native exit', async () => {
  for (const options of [{portalCompletes: false}, {portalLeaves: false}]) {
    const run = fixture({only: 'real survival movement', ...options});
    await assert.rejects(hudCameraVariantActions(run.ctx, run.services));
    assert.equal(run.samples.length, 0);
    assert.equal(run.state().held, 0);
    assert.equal(run.state().portal, false);
    assert.equal(run.state().z, .5);
  }
});

test('sleep cannot pass from a command or raw timer without real bed use and InBed Chat draw', async () => {
  const run = fixture({only: 'real bed use', noBedScreen: true});
  await assert.rejects(hudCameraVariantActions(run.ctx, run.services), /Native sleep overlay/);
  assert.equal(run.samples.length, 0); assert.equal(run.state().sleepingPercentage, 100);
  assert.equal(run.state().screen, ''); assert.equal(run.state().item, 'minecraft:air');
  assert(run.calls.some(call => call.body?.steps?.[0]?.type === 'world.action' && call.body.steps[0].action === 'use'));
});

test('a missing native rule is a failed precondition and its owned settings are still closed', async () => {
  const run = fixture({only: 'real bed use', missingRule: true});
  await assert.rejects(hudCameraVariantActions(run.ctx, run.services), error => /Native sleeping rule USER lookup failed/.test(error.message)
    && error.userRuleLookup.fields.some(node => node.nodeId === 'rule-search' && node.value === 'sleep'));
  assert.equal(run.samples.length, 0); assert.equal(run.state().screen, '');
  assert(!run.calls.some(call => call.command));
  assert.equal(run.calls.filter(call => call.selector?.nodeId === 'rule-search').length, 1);
  assert(!run.calls.some(call => call.selector?.nodeId === 'list'));
});

test('MASTER is a read-only muted precondition and a failure does not leave the Sounds Screen open', async () => {
  const run = fixture({only: 'native nausea', masterVolume: .5});
  await assert.rejects(hudCameraVariantActions(run.ctx, run.services), /MASTER must already be muted/);
  assert.equal(run.samples.length, 0); assert.equal(run.state().screen, '');
  assert(!run.calls.some(call => call.selector?.nodeId === 'master' || call.command));
});

test('a missing actual nausea texture does not pass and the real option and effect are restored', async () => {
  const run = fixture({only: 'native nausea', missingDraw: 'nausea'});
  await assert.rejects(hudCameraVariantActions(run.ctx, run.services), /Native nausea overlay/);
  assert.equal(run.samples.length, 0); assert.equal(run.state().scale, 1); assert.equal(run.state().nausea, false);
  assert.equal(run.state().screen, ''); assert.equal(run.state().held, 0);
});

test('legacy accessibility uses its real translation key and missing or ambiguous controls never click', async () => {
  const run = fixture({target: '1.20.1-forge', only: 'native nausea'});
  await hudCameraVariantActions(run.ctx, run.services);
  assert(run.calls.some(call => call.selector?.nodeId === 'options.accessibility.title'));
  assert(!run.calls.some(call => call.selector?.nodeId === 'options.accessibility'));
  for (const accessibilityCount of [0, 2]) {
    const rejected = fixture({target: '1.20.1-forge', only: 'native nausea', accessibilityCount});
    await assert.rejects(hudCameraVariantActions(rejected.ctx, rejected.services), /options.accessibility.title/);
    assert(!rejected.calls.some(call => call.selector?.nodeId === 'options.accessibility.title'));
    assert.equal(rejected.samples.length, 0);
    assert.equal(rejected.state().screen, '');
    assert.equal(rejected.state().held, 0);
  }
});

test('the option must actually change and a completed UI call is not evidence that Vignette is off', async () => {
  const run = fixture({only: 'native video Vignette', ignoreVignetteClick: true});
  await assert.rejects(hudCameraVariantActions(run.ctx, run.services), /Vignette: ON|Native camera fixture cleanup failed/);
  assert.equal(run.samples.length, 0); assert.equal(run.state().vignette, true); assert.equal(run.state().screen, '');
});

test('an originally disabled Vignette is restored disabled after proving the real on and off branches', async () => {
  const run = fixture({only: 'native video Vignette', initialVignette: false});
  await hudCameraVariantActions(run.ctx, run.services);
  assert.equal(run.checks.length, 1); assert.equal(run.samples[0].vignetteOptionRestored, false);
  assert.equal(run.state().vignette, false); assert.equal(run.state().screen, '');
  assert.equal(run.samples[0].masterVolumeWritten, false);
});

test('a nonterminal world operation cannot turn a bed placement into completed input evidence', async () => {
  const run = fixture({only: 'real bed use', operationState: 'running'});
  await assert.rejects(hudCameraVariantActions(run.ctx, run.services), /actual terminal completion/);
  assert.equal(run.samples.length, 0); assert.equal(run.state().sleepingPercentage, 100); assert.equal(run.state().held, 0);
});

test('actual overlay validation rejects raw/private data, fake geometry, wrong source and wrong native context', () => {
  const view = kind => ({dataCategory: 'USER', screenClass: kind === 'sleep' ? screenClass('InBedChatScreen') : '',
    overlayIdentity: 0, width: 640, height: 360, presentation: {children: [draw(kind)]}});
  for (const kind of ['portal', 'sleep', 'nausea', 'vignette']) assert.equal(cameraVariantRows(view(kind), kind).length, 1);
  for (const mutate of [
    value => value.dataCategory = 'DEBUG', value => value.overlayIdentity = 1,
    value => value.screenClass = screenClass('PauseScreen'),
    value => value.presentation.children[0].opacity = 0,
    value => value.presentation.children[0].x = 1,
    value => value.presentation.children[0].nauseaIntensity = 1,
    value => value.presentation.children[0].rawColor = 0xffffff,
    value => value.presentation.children[0].iconSource = 'private_renderer_value',
  ]) {const value = view('nausea'); mutate(value); assert.throws(() => cameraVariantRows(value, 'nausea'));}
  const sleep = view('sleep'); sleep.screenClass = ''; assert.throws(() => cameraVariantRows(sleep, 'sleep'));
  const wrong = view('vignette'); assert.throws(() => cameraVariantRows(wrong, 'portal'));
});
