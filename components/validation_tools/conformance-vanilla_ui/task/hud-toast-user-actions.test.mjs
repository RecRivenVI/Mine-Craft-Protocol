import test from 'node:test';
import assert from 'node:assert/strict';
import { validateResidentToastDraw,hideResidentFriendToast,prepareNativeMusicToastOption,hudToastUserCompletionActions } from './hud-toast-user-actions.mjs';

function fixture(kind = 'NowPlaying', phase = 'fully_shown') {
  return { dataCategory: 'USER', overlayIdentity: 0, width: 320, height: 240, presentation: { children: [{
    class: `native.${kind}Toast`, role: 'toast', visible: true, active: false, actions: [],
    boundsAvailable: true, boundsSemantics: 'native_toast_draw_viewport_intersection',
    labelSource: 'last_native_toast_render', x: 0, y: 0, width: 120, height: 30,
    label: phase === 'fully_shown' ? 'actually rendered native text' : '', renderPhase: phase,
    textPhase: phase === 'fully_shown' ? 'drawn' : 'withheld_partial_or_occluded',
  }] } };
}

test('requires actual full text but deliberately withholds partial entry and exit text', () => {
  for (const kind of ['Friend', 'NowPlaying']) for (const phase of ['entering', 'fully_shown', 'exiting'])
    assert.equal(validateResidentToastDraw(fixture(kind, phase), kind, phase).renderPhase, phase);
});

test('rejects private/cache data, invented partial text, wrong phase and incorrect NowPlaying placement', () => {
  for (const mutate of [view => view.dataCategory = 'DEBUG', view => view.overlayIdentity = 1,
    view => view.presentation.children[0].debugLabel = 'hidden',
    view => view.presentation.children[0].songId = 'private',
    view => view.presentation.children[0].skinProfile = {},
    view => view.presentation.children[0].x = 200,
    view => view.presentation.children[0].height = 300,
    view => view.presentation.children[0].label = '',
    view => view.presentation.children[0].labelSource = 'music_cache',
  ]) { const view = fixture(); mutate(view); assert.throws(() => validateResidentToastDraw(view, 'NowPlaying', 'fully_shown')); }
  const partial = fixture('Friend', 'entering'); partial.presentation.children[0].label = 'guessed missing characters';
  assert.throws(() => validateResidentToastDraw(partial, 'Friend', 'entering'));
  assert.throws(() => validateResidentToastDraw(fixture(), 'NowPlaying', 'exiting'));
});

test('the actual dismissal uses a fresh guarded DEBUG owner without Lease or mode roundtrips',async()=>{
  const calls=[],resourceVersion={generation:7};
  const result=await hideResidentFriendToast({api:async(method,path,body)=>{
    calls.push({method,path,body});
    if(path==='/v0/ui/tree?category=DEBUG')return {presentation:{children:[
      {debugToastKind:'friend',debugHideRequested:true,nodeId:'old'},
      {debugToastKind:'friend',debugHideRequested:false,nodeId:'resident',resourceVersion}]}};
    assert.equal(method,'POST');assert.equal(path,'/v0/debug/mutations');
    assert.deepEqual(body,{operation:'ui.toast.hide',nodeId:'resident',expectedResourceVersion:resourceVersion,expectedValue:false,value:true});
    return {inputDispatchCount:0,evidenceAuthority:'DEBUG_PRIVILEGED',dataCategory:'DEBUG'};
  }});
  assert.equal(result.inputDispatchCount,0);
  assert.deepEqual(calls.map(call=>call.path),['/v0/ui/tree?category=DEBUG','/v0/debug/mutations']);
});

test('no fresh owner or a rejected mutation never triggers an automatic retry or authority change',async()=>{
  let mutations=0;
  await assert.rejects(hideResidentFriendToast({api:async()=>({presentation:{children:[]}})}),assert.AssertionError);
  const failure=Object.assign(new Error('stale owner'),{code:'STALE_RESOURCE_REVISION'});
  await assert.rejects(hideResidentFriendToast({api:async(method,path)=>{
    if(method==='GET')return {presentation:{children:[{debugToastKind:'friend',nodeId:'resident',resourceVersion:{generation:1}}]}};
    mutations++;throw failure;
  }}),error=>error===failure);
  assert.equal(mutations,1);
});

function musicOptionFixture(options = {}) {
  const labels = ['Music Toast: Never', 'Music Toast: Pause Menu', 'Music Toast: Pause Menu and Toast'];
  const state = {screen: options.screen ?? '', index: options.index ?? 0, master: options.master ?? 0,
    visible: options.visible ?? true, revision: 0};
  const calls = [];
  const view = () => ({dataCategory: 'USER', overlayIdentity: 0,
    screenClass: state.screen ? `native.${state.screen}` : '', children: [
      ...(state.screen === 'PauseScreen' ? [{nodeId: 'options', role: 'button', translationKey: 'menu.options', label: 'Options...', active: true, visible: true, actions: ['click']}] : []),
      ...(state.screen === 'OptionsScreen' ? [{nodeId: 'sounds', role: 'button', translationKey: 'options.sounds', label: 'Music & Sounds...', active: true, visible: true, actions: ['click']}] : []),
      ...(state.screen === 'SoundOptionsScreen' ? [{nodeId: 'sound-list',role: 'list',visible:true,active:true,actions:['scroll'],scrollAmount:state.visible?64:0, children: [
        ...(options.visible === false && state.visible ? [] : [{nodeId: 'master', role: 'slider', label: 'Master Volume: OFF', valueSemantics: options.valueSemantics ?? 'normalized_slider_fraction', value: state.master,
          active: true, visible: true, actions: ['click', 'set_value']}]),
        ...(!options.missing && state.visible ? [{nodeId: `music-${state.revision}`, role: 'button', translationKey: 'options.musicToast',
          label: options.label ?? labels[state.index], active: !options.disabled, visible: state.visible, actions: ['click', 'scroll_into_view']}] : []),
        ...(options.duplicate ? [{nodeId: 'duplicate', role: 'button', label: labels[state.index], active: true, visible: true, actions: ['click']}] : []),
      ]}] : []),
    ]});
  const all = value => (value.children ?? []).flatMap(node => [node, ...all(node)]);
  const ctx = {target: options.target ?? '26.3-neoforge',
    api: async (...args) => {calls.push({api: args}); assert.fail('Preparation cannot acquire authority, use DEBUG setters or write options directly');},
    tree: async () => view(),
    wait: async (predicate, label) => {const value = view(); assert(predicate(value), 'Unmet actual Screen/option condition: ' + label); return value;},
    key: async code => {
      assert.equal(code, 256); calls.push({key: code, screen: state.screen});
      state.screen = {'': 'PauseScreen', PauseScreen: '', OptionsScreen: 'PauseScreen', SoundOptionsScreen: 'OptionsScreen'}[state.screen];
      assert.notEqual(state.screen, undefined, 'Do not close unrelated Screens');
    },
    ui: async (selector, action = 'click',extra={}) => {
      calls.push({selector, action, screen: state.screen});
      assert.deepEqual(Object.keys(selector), ['nodeId'], 'Only a current semantic nodeId is allowed');
      const node = all(view()).find(node => node.nodeId === selector.nodeId); assert(node, 'Stale semantic node');
      assert.notEqual(selector.nodeId, 'master', 'The MASTER slider must never receive an action');
      if (action === 'scroll') {
        assert.equal(selector.nodeId,'sound-list'); assert.equal(Math.abs(extra.yOffset),4);
        state.visible=extra.yOffset<0;state.revision++;return {state:'completed'};
      }
      assert.equal(action, 'click'); assert.equal(node.visible, true); assert.equal(node.active, true);
      if (selector.nodeId === 'options') state.screen = 'OptionsScreen';
      else if (selector.nodeId === 'sounds') state.screen = 'SoundOptionsScreen';
      else {
        assert(selector.nodeId.startsWith('music-'));
        if (options.failure) throw options.failure;
        state.index = options.cycle ? options.cycle(state.index) : (state.index + 1) % labels.length;
        state.revision++;
      }
      return {state: 'completed', semanticNodeId: selector.nodeId};
    },
  };
  return {ctx, state, calls, musicClicks: () => calls.filter(call => call.action === 'click' && call.selector.nodeId.startsWith('music-'))};
}

test('prepares and restores the real semantic Music Toast cycle without writing volume or changing authority', async () => {
  const f = musicOptionFixture();
  const restore = await prepareNativeMusicToastOption(f.ctx);
  assert.equal(typeof restore, 'function'); assert.equal(f.state.index, 2); assert.equal(f.state.screen, '');
  assert.equal(restore.evidence.originalLabel, 'Music Toast: Never');
  assert.equal(restore.evidence.settings.clicks.length, 2); assert.equal(restore.evidence.settings.masterVolumeAfter, 0);
  assert.equal(restore.evidence.scope, 'owned_disposable_validation_options');
  for (const flag of ['masterWritten', 'debugSetterUsed', 'modeChanged', 'leaseAcquired']) assert.equal(restore.evidence[flag], false);
  const restored = await restore();
  assert.equal(restored.preferenceRestored, true); assert.equal(restored.settings.after, 'Music Toast: Never');
  assert.equal(restored.settings.clicks.length, 1); assert.equal(f.state.index, 0); assert.equal(f.state.master, 0); assert.equal(f.state.screen, '');
  const calls = f.calls.length; assert.equal(await restore(), restored); assert.equal(f.calls.length, calls, 'Completed restore is idempotent');
  assert.equal(f.calls.filter(call => call.api).length, 0); assert.equal(f.musicClicks().length, 3);
});

test('finds absent USER options by native list scrolling and returns upward to verify MASTER without exposing hidden nodes', async () => {
  const hidden = musicOptionFixture({visible: false, screen: 'PauseScreen', index: 1});
  const restore = await prepareNativeMusicToastOption(hidden.ctx);
  assert.equal(hidden.calls.filter(call => call.action === 'scroll').length, 2);
  assert.equal(restore.evidence.settings.clicks.length, 1); await restore(); assert.equal(hidden.state.index, 1);
  const ready = musicOptionFixture({index: 2});
  const unchanged = await prepareNativeMusicToastOption(ready.ctx); assert.equal(unchanged.evidence.settings.clicks.length, 0);
  assert.equal((await unchanged()).settings.clicks.length, 0); assert.equal(ready.musicClicks().length, 0);
});

test('rejects nonzero or untyped MASTER without any music or volume mutation and closes only its own settings', async () => {
  for (const options of [{master: 0.5}, {valueSemantics: 'raw_game_value'}]) {
    const f = musicOptionFixture(options);
    await assert.rejects(prepareNativeMusicToastOption(f.ctx), assert.AssertionError);
    assert.equal(f.musicClicks().length, 0); assert.equal(f.state.master, options.master ?? 0); assert.equal(f.state.index, 0);
    assert.equal(f.state.screen, ''); assert.equal(f.calls.filter(call => call.api).length, 0);
  }
});

test('missing, ambiguous, unknown or disabled native cycles are rejected rather than guessed or coordinate-clicked', async () => {
  for (const options of [{missing: true}, {duplicate: true}, {label: 'Music Toast: Future Value'}, {disabled: true}]) {
    const f = musicOptionFixture(options);
    await assert.rejects(prepareNativeMusicToastOption(f.ctx), assert.AssertionError);
    assert.equal(f.musicClicks().length, 0); assert.equal(f.state.screen, '');
  }
  const unrelated = musicOptionFixture({screen: 'UnknownScreen'});
  await assert.rejects(prepareNativeMusicToastOption(unrelated.ctx), assert.AssertionError);
  assert.deepEqual(unrelated.calls, [], 'Do not dismiss an unrelated Screen to reach settings');
});

test('bounds cycle inputs and never replays a rejected semantic click', async () => {
  const unreachable = musicOptionFixture({cycle: index => (index + 1) % 2});
  await assert.rejects(prepareNativeMusicToastOption(unreachable.ctx), /within three semantic clicks/);
  assert.equal(unreachable.musicClicks().length, 3); assert.equal(unreachable.state.screen, '');
  const failure = Object.assign(new Error('UI click cancelled'), {code: 'REQUEST_CANCELLED'});
  const rejected = musicOptionFixture({failure});
  await assert.rejects(prepareNativeMusicToastOption(rejected.ctx), error => error === failure);
  assert.equal(rejected.musicClicks().length, 1); assert.equal(rejected.state.screen, ''); assert.equal(rejected.state.index, 0);
});

test('restoration does not overwrite an external preference or unmute a changed MASTER setting', async () => {
  const external = musicOptionFixture(); const restore = await prepareNativeMusicToastOption(external.ctx);
  external.state.index = 1;
  await assert.rejects(restore(), /changed outside this helper/);
  assert.equal(external.musicClicks().length, 2); assert.equal(external.state.index, 1); assert.equal(external.state.screen, '');
  const volume = musicOptionFixture(); const restoreVolume = await prepareNativeMusicToastOption(volume.ctx);
  volume.state.master = 0.5;
  await assert.rejects(restoreVolume(), /MASTER must already be zero/);
  assert.equal(volume.musicClicks().length, 2); assert.equal(volume.state.master, 0.5); assert.equal(volume.state.index, 2);
});

function residentFixture(target, {missingFull = false} = {}) {
  const calls = [], samples = [], checks = [];
  let current, phase = 0;
  const show = kind => {assert.equal(current, undefined); current = kind; phase = 0; calls.push('show:' + kind);};
  const ctx = {target, samples,
    api: async (method, path) => {
      assert.equal(method, 'GET'); assert.equal(path, '/v0/input/state');
      return {pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false};
    },
    wait: async (predicate, label) => {
      assert(current);
      const stages = ['entering', 'fully_shown', 'exiting'];
      const stage = stages[phase++];
      let value = stage ? fixture(current, stage) : {presentation: {children: []}};
      if (missingFull && stage === 'fully_shown') value = {presentation: {children: []}};
      assert(predicate(value), label);
      if (!stage) current = undefined;
      return value;
    },
    check: async (name, run) => {await run(); checks.push(name);},
    showNowPlayingFixture: async () => show('NowPlaying'),
    clearNowPlayingFixture: async () => {calls.push('clear:NowPlaying'); current = undefined;},
    ...(target.startsWith('26.3') ? {
      showFriendFixture: async () => show('Friend'),
      hideFriendFixture: async () => {assert.equal(current, 'Friend'); assert.equal(phase, 2); calls.push('hide:Friend');},
    } : {}),
  };
  return {ctx, calls, samples, checks};
}

test('26.1.2 executes only its actual native NowPlaying type and does not require Friend callbacks', async () => {
  const run = residentFixture('26.1.2-neoforge');
  assert.equal(run.ctx.showFriendFixture, undefined); assert.equal(run.ctx.hideFriendFixture, undefined);
  await hudToastUserCompletionActions(run.ctx);
  assert.equal(run.checks.length, 1); assert(run.checks[0].includes('NowPlaying'));
  assert.deepEqual(run.calls, ['show:NowPlaying', 'clear:NowPlaying']);
  assert.equal(run.samples.length, 1); assert.equal(run.samples[0].kind, 'NowPlaying');
  assert.equal(run.samples[0].source, 'native_public_music_manager');
  assert.equal(run.samples[0].audiblePlaybackAsserted, false);
});

test('both current 26.3 Targets retain real Friend and NowPlaying phase assertions', async () => {
  for (const target of ['26.3-neoforge', '26.3-fabric']) {
    const run = residentFixture(target); await hudToastUserCompletionActions(run.ctx);
    assert.equal(run.checks.length, 2);
    assert.deepEqual(run.samples.map(sample => sample.kind), ['Friend', 'NowPlaying']);
    assert.deepEqual(run.calls, ['show:Friend', 'hide:Friend', 'show:NowPlaying', 'clear:NowPlaying']);
    for (const sample of run.samples) {
      assert.equal(sample.category, 'USER'); assert.equal(sample.partialTextWithheld, true);
      assert.equal(sample.entering.label, ''); assert(sample.full.label); assert.equal(sample.exiting.label, '');
    }
  }
});

test('26.1.2 native missing full text fails and still clears its own real music fixture', async () => {
  const run = residentFixture('26.1.2-neoforge', {missingFull: true});
  await assert.rejects(hudToastUserCompletionActions(run.ctx), /actual fully shown text/);
  assert.deepEqual(run.calls, ['show:NowPlaying', 'clear:NowPlaying']);
  assert.equal(run.checks.length, 0); assert.equal(run.samples.length, 0);
});

test('26.1.2 retains actual semantic Music Toast option preparation, scrolling, muted MASTER and restore', async () => {
  const run = musicOptionFixture({target: '26.1.2-neoforge', visible: false, index: 1});
  const restore = await prepareNativeMusicToastOption(run.ctx);
  assert.equal(restore.evidence.target, '26.1.2-neoforge'); assert.equal(run.state.index, 2);
  assert.equal(restore.evidence.settings.masterVolume, 0); assert.equal(restore.evidence.settings.masterVolumeAfter, 0);
  assert(run.calls.some(call => call.action === 'scroll'));
  const result = await restore(); assert.equal(result.preferenceRestored, true);
  assert.equal(run.state.index, 1); assert.equal(run.state.screen, ''); assert.equal(run.state.master, 0);
  assert(!run.calls.some(call => call.selector?.nodeId === 'master' || call.api));
});

test('unsupported native resident types are rejected before any fixture activation', async () => {
  const run = residentFixture('1.21.1-neoforge');
  await assert.rejects(hudToastUserCompletionActions(run.ctx), /verified native resident Toast types/);
  assert.deepEqual(run.calls, []); assert.equal(run.samples.length, 0);
});
