import assert from 'node:assert/strict';
import test from 'node:test';
import {hudNativeMessageActions, subtitleSetting} from './hud-native-message-actions.mjs';

function nativeContext({originalSubtitles = false, masterVolume = 0, caption = 'Closed Captions',
  subtitleSource = 'last_completed_native_gui_text_draw', arrows = true, pipelineState = 'completed',
  stopFails = false, recordInserted = true, recordLabel = 'Now Playing: C418 - 13', occupied = false,
  masterChangesBeforeRecord = false, pipelineRpcCancelled = false, scrollOptions = false,
  soundStartsAtBottom = false, scrollDoesNotMove = false, scrollTargetMissing = false,
  duplicateOptionsList = false, hiddenSubtitleDecoy = false, duplicateOptionsButton = false,
  scrollError} = {}) {
  let screen = '', subtitles = originalSubtitles, master = masterVolume, block = 'minecraft:air';
  let hand = occupied ? 'minecraft:diamond' : 'minecraft:air', count = occupied ? 1 : 0;
  let tick = 0, time = 0, subtitleFrames = 0, overlayFrames = 0, inserted = false, heldButtons = 0;
  let soundScroll = 0;
  const commands = [], inputs = [], clicks = [], checks = [], samples = [], operations = new Map();
  const button = (nodeId, translationKey, label, extra = {}) => ({nodeId, translationKey, label,
    role: 'button', visible: true, active: true, actions: ['click'], ...extra});
  const text = (family, label, x = 10) => ({controlKey: 'presentation.' + family + '.' + x,
    renderPhase: family, label, role: 'hud_text', visible: true, active: false, actions: [], boundsAvailable: true,
    x, y: 20, width: 80, height: 9, opacity: 1, labelSource: 'last_completed_native_gui_text_draw',
    iconSource: 'last_completed_native_gui_draw', textPhase: 'drawn', boundsSemantics: 'native_text_draw_viewport_scissor_intersection'});
  const tree = async () => {
    tick++; let children = [];
    if (screen === 'PauseScreen') children = [button('options', 'menu.options', 'Options...'),
      ...(duplicateOptionsButton ? [button('options-copy', 'menu.options', 'Options...')] : [])];
    if (screen === 'OptionsScreen') children = [button('sounds', 'options.sounds', 'Music & Sounds...')];
    if (screen === 'SoundOptionsScreen') {
      const options = [
        ...(!scrollOptions || soundScroll === 0 ? [button('master', 'options.generic_value', 'Master Volume: OFF',
          {role: 'slider', value: master, valueSemantics: 'normalized_slider_fraction', actions: ['set_value']})] : []),
        ...((!scrollOptions || soundScroll === 64) && !scrollTargetMissing ? [button('subtitles', 'options.generic_value', caption + ': ' + (subtitles ? 'ON' : 'OFF'))] : []),
        ...(hiddenSubtitleDecoy ? [button('hidden-subtitles', 'options.generic_value', caption + ': OFF', {visible: false, actions: ['scroll_into_view']})] : []),
      ];
      children = scrollOptions ? [button('sound-list', '', '', {role: 'list', actions: ['scroll'], scrollAmount: soundScroll, children: options}),
        ...(duplicateOptionsList ? [button('other-list', '', '', {role: 'list', actions: ['scroll'], scrollAmount: soundScroll})] : [])] : options;
    }
    const presentation = [{controlKey: 'hud.hotbar.0', selected: true, item: hand, count}];
    if (!screen && subtitles && subtitleFrames > 0) {
      const row = text('subtitle', 'Cow moos'); row.labelSource = subtitleSource; presentation.push(row);
      if (arrows) presentation.push(text('subtitle', '>', 100));
    }
    if (!screen && overlayFrames > 0) presentation.push(text('overlay_message', recordLabel));
    subtitleFrames = Math.max(0, subtitleFrames - 1); overlayFrames = Math.max(0, overlayFrames - 1);
    return {dataCategory: 'USER', screenClass: screen ? 'native.' + screen : '', overlayIdentity: 0,
      clientTick: tick, width: 320, height: 240, children, presentation: {children: presentation}};
  };
  const key = async code => {
    assert.equal(code, 256); inputs.push({key: code});
    screen = screen === '' ? 'PauseScreen' : screen === 'PauseScreen' ? '' : screen === 'OptionsScreen' ? 'PauseScreen' : 'OptionsScreen';
  };
  const ui = async ({nodeId}, action = 'click', extra = {}) => {
    clicks.push({nodeId, action, extra});
    if (action === 'scroll') {
      assert.equal(screen, 'SoundOptionsScreen'); assert.equal(nodeId, 'sound-list'); assert.equal(scrollOptions, true);
      assert.equal(Math.abs(extra.yOffset), 4); assert.deepEqual(Object.keys(extra), ['yOffset']);
      if (scrollError) throw scrollError;
      if (!scrollDoesNotMove) soundScroll = extra.yOffset > 0 ? 0 : 64;
      return;
    }
    assert.equal(action, 'click');
    if (nodeId === 'options') {assert.equal(screen, 'PauseScreen'); screen = 'OptionsScreen';}
    else if (nodeId === 'sounds') {assert.equal(screen, 'OptionsScreen'); screen = 'SoundOptionsScreen'; soundScroll = soundStartsAtBottom ? 64 : 0;}
    else if (nodeId === 'subtitles') {assert.equal(screen, 'SoundOptionsScreen'); subtitles = !subtitles;}
    else assert.fail('Must never write Master or click an unrelated control: ' + nodeId);
  };
  const api = async (method, path, body) => {
    inputs.push({method, path, body});
    if (path === '/v0/input/state') return {pressedKeyCount: 0, pressedButtonCount: heldButtons,
      inputCleanupPending: false, inputCleanupFailed: false};
    if (path === '/v0/player?category=USER') return {available: true, dataCategory: 'USER', x: .5, y: 80, z: .5};
    if (path.startsWith('/v0/world/block?')) {
      const query = new URLSearchParams(path.split('?')[1]); assert.equal(query.get('category'), 'USER');
      return {available: true, dataCategory: 'USER', authority: 'CLIENT_KNOWN', block,
        state: block === 'minecraft:jukebox' ? 'Block{minecraft:jukebox}[has_record=' + inserted + ']' : 'Block{minecraft:air}',
        targetRef: block === 'minecraft:jukebox' ? 'native-jukebox-target' : 'native-empty-target'};
    }
    if (path === '/v0/pipelines') {
      assert.equal(method, 'POST'); assert.deepEqual(body.steps, [{type: 'world.action', category: 'USER', targetRef: 'native-jukebox-target', action: 'use'}]);
      assert.equal(block, 'minecraft:jukebox'); assert.equal(hand, 'minecraft:music_disc_13');
      if (pipelineRpcCancelled) {heldButtons = 1; throw new Error('request cancelled; submission outcome unknown');}
      heldButtons = pipelineState === 'completed' ? 0 : 1;
      if (pipelineState === 'completed') {inserted = recordInserted; overlayFrames = recordInserted ? 12 : 0;}
      const operationId = 'native-record-use'; operations.set(operationId, {state: pipelineState, result: {steps: [{stepType: 'world.action', result: {
        dataCategory: 'USER', evidenceAuthority: 'PLAYTEST', directMutationUsed: false, effectConfirmed: false}}]}});
      return {operationId};
    }
    if (path === '/v0/operations/native-record-use/wait') return operations.get('native-record-use');
    if (path === '/v0/input/release-all') {
      if (stopFails) throw new Error('mandatory cleanup failed'); heldButtons = 0; return {type: 'input.release_all'};
    }
    assert.fail('Unexpected API ' + method + ' ' + path);
  };
  const command = async value => {
    commands.push(value);
    if (value === 'playsound minecraft:entity.cow.ambient neutral @s ^4 ^ ^ 1 1') {
      assert.equal(master, 0); assert(subtitles); subtitleFrames = 12;
    } else if (value === 'setblock 0 80 2 minecraft:jukebox') {assert.equal(block, 'minecraft:air'); block = 'minecraft:jukebox';}
    else if (value === 'setblock 0 80 2 minecraft:air') {block = 'minecraft:air'; inserted = false;}
    else if (value === 'item replace entity @s weapon.mainhand with minecraft:music_disc_13') {hand = 'minecraft:music_disc_13'; count = 1;}
    else if (value === 'item replace entity @s weapon.mainhand with minecraft:air') {hand = 'minecraft:air'; count = 0;}
    else if (value === 'item replace block 0 80 2 container.0 with minecraft:air') inserted = false;
    else assert.fail('No substituted actionbar, private HUD or listener command: ' + value);
    return {type: 'command.submission_result', submitted: true, command: value};
  };
  const wait = async (predicate, label) => {
    for (let i = 0; i < 30; i++) {const value = await tree(); if (predicate(value)) return value; time += 50;}
    throw new Error('Timeout: ' + label);
  };
  const ctx = {target: '26.3-neoforge', api, command, tree, key, ui, wait, samples,
    check: async (name, run) => {
      assert(name.startsWith('HUD: native messages:')); checks.push(name);
      if (masterChangesBeforeRecord && name.includes('real record')) master = .5;
      return run();
    }};
  return {ctx, services: {now: () => time, sleep: async milliseconds => {time += milliseconds;}}, commands, inputs, clicks, checks, samples,
    state: () => ({screen, subtitles, master, block, hand, count, heldButtons})};
}

test('actual helper consumes semantic options, native sound/draw/expiry and USER record-use then restores the disposable fixture', async () => {
  const mock = nativeContext(); await hudNativeMessageActions(mock.ctx, mock.services);
  assert.equal(mock.checks.length, 2);
  assert.deepEqual(mock.state(), {screen: '', subtitles: false, master: 0, block: 'minecraft:air', hand: 'minecraft:air', count: 0, heldButtons: 0});
  assert.equal(mock.clicks.filter(value => value.nodeId === 'subtitles').length, 2);
  assert(mock.clicks.every(value => value.nodeId !== 'master'));
  for (const name of ['hud_native_subtitle', 'hud_native_subtitle_expired', 'hud_native_record', 'hud_native_record_expired', 'hud_native_messages_settings_restored'])
    assert(mock.samples.some(value => value.case === name), name);
  const record = mock.samples.find(value => value.case === 'hud_native_record');
  assert.equal(record.target, '26.3-neoforge'); assert.equal(record.worldTarget.targetRef, 'native-jukebox-target');
  assert.equal(record.after.state, 'Block{minecraft:jukebox}[has_record=true]'); assert.equal(record.effectConfirmedByOperation, false);
  assert.equal(record.actionbarCommandUsed, false); assert.equal(record.audiblePlaybackAsserted, false);
  assert.deepEqual(record.used.request.steps, [{type: 'world.action', category: 'USER', targetRef: 'native-jukebox-target', action: 'use'}]);
  assert.equal(mock.inputs.filter(value => value.path === '/v0/pipelines').length, 1);
  assert(mock.commands.every(value => !/^(?:title|tellraw)|setOverlayMessage|onPlaySound/.test(value)));
  const scope = mock.samples.find(value => value.case === 'hud_native_messages_scope');
  assert.equal(scope.savingIndicatorStatus, 'separate_native_save_scenario'); assert.equal(scope.demoStatus, 'separate_demo_launch_scenario');
});

test('preserves an initially enabled legacy-labelled subtitle option without toggling or modifying Master', async () => {
  const mock = nativeContext({originalSubtitles: true, caption: 'Show Subtitles'});
  mock.ctx.target = '1.20.1-forge'; await hudNativeMessageActions(mock.ctx, mock.services);
  assert.equal(mock.state().subtitles, true); assert.equal(mock.state().master, 0);
  assert.equal(mock.clicks.filter(value => value.nodeId === 'subtitles' || value.nodeId === 'master').length, 0);
});

test('never emits sounds or changes volume when the actual Master slider is not zero', async () => {
  const mock = nativeContext({masterVolume: .5});
  await assert.rejects(hudNativeMessageActions(mock.ctx, mock.services), /MASTER must already be zero/);
  assert.equal(mock.commands.length, 0); assert.equal(mock.state().screen, ''); assert.equal(mock.state().master, .5);
  assert.equal(mock.clicks.some(value => value.nodeId === 'master'), false);
});

test('private/cached subtitle text or missing real direction glyph cannot pass and the setting is still restored', async () => {
  for (const settings of [{subtitleSource: 'cached_sound_event'}, {arrows: false}]) {
    const mock = nativeContext(settings);
    await assert.rejects(hudNativeMessageActions(mock.ctx, mock.services), /actual cow subtitle text and direction/);
    assert.equal(mock.state().subtitles, false); assert.equal(mock.state().screen, '');
    assert.equal(mock.commands.some(value => value.includes('jukebox')), false);
    assert.equal(mock.samples.some(value => value.case === 'hud_native_subtitle' || value.case === 'hud_native_messages_scope'), false);
  }
});

test('a nonterminal input operation is not success or replayed; strong stop precedes fixture cleanup', async () => {
  const mock = nativeContext({pipelineState: 'executing'});
  await assert.rejects(hudNativeMessageActions(mock.ctx, mock.services), /executing/);
  assert.equal(mock.inputs.filter(value => value.path === '/v0/pipelines').length, 1);
  assert.equal(mock.inputs.filter(value => value.path === '/v0/input/release-all').length, 1);
  assert.equal(mock.state().heldButtons, 0); assert.equal(mock.state().block, 'minecraft:air'); assert.equal(mock.state().subtitles, false);
  assert.equal(mock.samples.some(value => value.case === 'hud_native_record'), false);
});

test('unconfirmed mandatory cleanup retains the owned fixture and does not send new input or world cleanup', async () => {
  const mock = nativeContext({pipelineState: 'executing', stopFails: true});
  await assert.rejects(hudNativeMessageActions(mock.ctx, mock.services), error => /executing/.test(error.message)
    && /mandatory cleanup failed/.test(error.inputCleanupFailure) && error.cleanupFailures.some(value => value.includes('fixture and settings retained')));
  assert.equal(mock.state().block, 'minecraft:jukebox'); assert.equal(mock.state().subtitles, true);
  assert.equal(mock.commands.includes('setblock 0 80 2 minecraft:air'), false);
  const stopIndex = mock.inputs.findIndex(value => value.path === '/v0/input/release-all');
  assert.equal(mock.inputs.slice(stopIndex + 1).some(value => value.key || value.path === '/v0/pipelines'), false);
});

test('cancelled input RPC is not replayed or treated as evidence and still invokes mandatory strong stop', async () => {
  const mock = nativeContext({pipelineRpcCancelled: true});
  await assert.rejects(hudNativeMessageActions(mock.ctx, mock.services), /submission outcome unknown/);
  assert.equal(mock.inputs.filter(value => value.path === '/v0/pipelines').length, 1);
  assert.equal(mock.inputs.filter(value => value.path === '/v0/input/release-all').length, 1);
  assert.equal(mock.state().heldButtons, 0); assert.equal(mock.state().block, 'minecraft:air');
  assert.equal(mock.samples.some(value => value.case === 'hud_native_record'), false);
});

test('missing record effect or a generic actionbar label cannot replace actual record-path proof', async () => {
  for (const settings of [{recordInserted: false}, {recordLabel: 'A different actionbar message'}]) {
    const mock = nativeContext(settings);
    await assert.rejects(hudNativeMessageActions(mock.ctx, mock.services), /record actually inserted|actual native record-description overlay/);
    assert.equal(mock.state().block, 'minecraft:air'); assert.equal(mock.state().count, 0); assert.equal(mock.state().subtitles, false);
    assert.equal(mock.samples.some(value => value.case === 'hud_native_record'), false);
  }
});

test('rechecks Master before the record Act and restores the subtitle toggle even after an external volume change', async () => {
  const mock = nativeContext({masterChangesBeforeRecord: true});
  await assert.rejects(hudNativeMessageActions(mock.ctx, mock.services), /MASTER must already be zero/);
  assert.equal(mock.state().subtitles, false); assert.equal(mock.state().master, .5);
  assert.equal(mock.commands.some(value => value.includes('jukebox')), false);
  assert.equal(mock.clicks.some(value => value.nodeId === 'master'), false);
});

test('an occupied selected item is preserved and no record fixture is placed', async () => {
  const mock = nativeContext({occupied: true});
  await assert.rejects(hudNativeMessageActions(mock.ctx, mock.services), /unknown nonempty selected item/);
  assert.equal(mock.state().hand, 'minecraft:diamond'); assert.equal(mock.state().block, 'minecraft:air');
  assert.equal(mock.commands.some(value => value.includes('jukebox')), false); assert.equal(mock.state().subtitles, false);
});

test('subtitle setting parser refuses guessed or inactive values', () => {
  assert.equal(subtitleSetting({label: 'Closed Captions: ON', active: true}), true);
  assert.equal(subtitleSetting({label: 'Show Subtitles: OFF', active: true}), false);
  assert.throws(() => subtitleSetting({label: 'Closed Captions: Enabled'}));
  assert.throws(() => subtitleSetting({label: 'Closed Captions: ON', active: false}));
});

test('finds USER-omitted Subtitles through the visible native list instead of a hidden option selector', async () => {
  const mock = nativeContext({scrollOptions: true, hiddenSubtitleDecoy: true});
  await hudNativeMessageActions(mock.ctx, mock.services);
  const scrolls = mock.clicks.filter(call => call.action === 'scroll');
  assert.equal(scrolls.length, 3); assert(scrolls.every(call => call.nodeId === 'sound-list' && call.extra.yOffset === -4));
  assert.equal(mock.clicks.filter(call => call.nodeId === 'subtitles').length, 2);
  assert(!mock.clicks.some(call => call.nodeId === 'hidden-subtitles' || call.nodeId === 'master' || call.action === 'scroll_into_view'));
  assert.equal(mock.state().subtitles, false); assert.equal(mock.state().screen, ''); assert.equal(mock.state().master, 0);
  assert(mock.samples.some(sample => sample.case === 'hud_native_messages_settings_restored'));
});

test('scrolls upward to actual MASTER then downward to actual Subtitles on each owned settings visit', async () => {
  const mock = nativeContext({scrollOptions: true, soundStartsAtBottom: true});
  await hudNativeMessageActions(mock.ctx, mock.services);
  const scrolls = mock.clicks.filter(call => call.action === 'scroll');
  assert.deepEqual(scrolls.map(call => call.extra.yOffset), [4, -4, 4, -4, 4, -4]);
  assert(scrolls.every(call => call.nodeId === 'sound-list'));
  assert(!mock.clicks.some(call => call.nodeId === 'master'));
  assert.equal(mock.state().subtitles, false); assert.equal(mock.state().master, 0); assert.equal(mock.state().screen, '');
});

test('missing or ambiguous list and no actual scroll progress fail without option or sound mutations', async () => {
  for (const options of [{scrollDoesNotMove: true}, {scrollTargetMissing: true}, {duplicateOptionsList: true}]) {
    const mock = nativeContext({scrollOptions: true, ...options});
    await assert.rejects(hudNativeMessageActions(mock.ctx, mock.services), /native list scroll|One visible native Sound Options list/);
    assert.equal(mock.commands.length, 0); assert.equal(mock.state().subtitles, false); assert.equal(mock.state().master, 0);
    assert.equal(mock.state().screen, ''); assert(!mock.clicks.some(call => call.nodeId === 'subtitles' || call.nodeId === 'master'));
    assert.equal(mock.samples.some(sample => sample.case === 'hud_native_subtitle'), false);
    assert(mock.clicks.filter(call => call.action === 'scroll').length <= 2);
  }
});

test('a rejected native list scroll is not retried or replaced with coordinates or DEBUG', async () => {
  const failure = Object.assign(new Error('native scroll cancelled'), {code: 'REQUEST_CANCELLED'});
  const mock = nativeContext({scrollOptions: true, scrollError: failure});
  await assert.rejects(hudNativeMessageActions(mock.ctx, mock.services), error => error === failure);
  assert.equal(mock.clicks.filter(call => call.action === 'scroll').length, 1);
  assert.equal(mock.commands.length, 0); assert.equal(mock.state().screen, ''); assert.equal(mock.state().subtitles, false);
  assert(!mock.inputs.some(call => call.path?.includes('DEBUG') || call.path?.includes('/debug/')));
});

test('ordinary pages remain strict unique semantic selection and never search them by scrolling', async () => {
  const mock = nativeContext({scrollOptions: true, duplicateOptionsButton: true});
  await assert.rejects(hudNativeMessageActions(mock.ctx, mock.services), /Options button/);
  assert.equal(mock.clicks.length, 0); assert.equal(mock.commands.length, 0); assert.equal(mock.state().screen, '');
});
