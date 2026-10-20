import assert from 'node:assert/strict';

const toast = (view, kind, phase) => (view.presentation?.children ?? []).find(node =>
  node.class?.endsWith(`${kind}Toast`) && (!phase || node.renderPhase === phase));

export async function prepareNativeMusicToastOption({target, key, tree, wait, ui}) {
  const descendants = value => (value.children ?? []).flatMap(node => [node, ...descendants(node)]);
  const labels = ['Music Toast: Never', 'Music Toast: Pause Menu', 'Music Toast: Pause Menu and Toast'];
  const desired = labels[2];
  const isMusicToast = node => node.translationKey === 'options.musicToast' || /^Music Toast:/.test(node.label);
  let originalLabel;
  const page = async name => {
    const value = await wait(value => value.screenClass.split('.').at(-1) === name, 'native ' + name);
    assert.equal(value.dataCategory, 'USER'); assert.equal(value.overlayIdentity, 0); return value;
  };
  async function semantic(predicate, label, direction = -1) {
    for (let count = 0; count < 12; count++) {
      const current = await tree(), matches = descendants(current).filter(node => node.visible !== false && predicate(node));
      assert(matches.length <= 1, label);
      if (matches.length) { assert.notEqual(matches[0].active, false); return matches[0]; }
      assert(current.screenClass.endsWith('SoundOptionsScreen'), label);
      const lists = descendants(current).filter(node => node.visible !== false && node.role === 'list' && node.actions?.includes('scroll'));
      assert.equal(lists.length, 1, 'One visible native Sound Options list');
      const list = lists[0]; assert(Number.isFinite(list.scrollAmount));
      await ui({nodeId: list.nodeId}, 'scroll', {yOffset: direction * 4});
      await wait(value => value.screenClass === current.screenClass && (descendants(value).some(node => node.visible !== false && predicate(node))
        || descendants(value).some(node => node.nodeId === list.nodeId && node.scrollAmount !== list.scrollAmount)), label + ' reached by native list scroll');
    }
    assert.fail('Native options scroll budget exhausted: ' + label);
  }
  async function click(predicate, label) {
    const node = await semantic(predicate, label); assert(node.actions.includes('click'));
    return ui({nodeId: node.nodeId});
  }
  async function closeSettings() {
    for (let i = 0; i < 4; i++) {
      const value = await tree(); if (!value.screenClass) return value;
      assert(/(?:^|\.)(?:SoundOptionsScreen|OptionsScreen|PauseScreen)$/.test(value.screenClass), 'Do not dismiss an unrelated Screen');
      await key(256);
    }
    assert.fail('Native music toast settings did not close');
  }
  async function soundSettings(wanted, remembering = false) {
    let value = await tree();
    if (!value.screenClass) {await key(256); value = await page('PauseScreen');}
    assert.equal(value.screenClass.split('.').at(-1), 'PauseScreen', 'Music Toast settings begin at the owned Pause Screen');
    const clicks = [];
    try {
      await click(node => node.translationKey === 'menu.options' || node.label === 'Options...', 'Options button'); await page('OptionsScreen');
      await click(node => node.translationKey === 'options.sounds' || node.label === 'Music & Sounds...', 'Music & Sounds button'); await page('SoundOptionsScreen');
      const masterOption = () => semantic(node => node.role === 'slider' && /^Master Volume:/.test(node.label), 'Master Volume slider', 1);
      const master = await masterOption();
      assert.equal(master.valueSemantics, 'normalized_slider_fraction');
      assert.equal(master.value, 0, 'MASTER must already be zero; the helper never writes volume');
      let option = await semantic(isMusicToast, 'native Music Toast option');
      assert(labels.includes(option.label), 'The English disposable fixture must expose a known native Music Toast value');
      const before = option.label;
      if (remembering) originalLabel = before;
      else assert([originalLabel, desired].includes(before), 'Do not overwrite a Music Toast preference changed outside this helper');
      for (let i = 0; i < 3 && option.label !== wanted; i++) {
        assert(option.actions.includes('click'));
        const previous = option.label;
        const selector = {nodeId: option.nodeId};
        const result = await ui(selector);
        value = await wait(value => value.screenClass.split('.').at(-1) === 'SoundOptionsScreen'
          && descendants(value).filter(isMusicToast).length === 1
          && descendants(value).find(isMusicToast).label !== previous, 'actual native Music Toast setting changed');
        assert.equal(value.dataCategory, 'USER'); assert.equal(value.overlayIdentity, 0);
        option = await semantic(isMusicToast, 'native Music Toast option after click');
        assert(labels.includes(option.label), 'Native Music Toast must remain a known cycle value');
        clicks.push({before: previous, after: option.label, selector, result});
      }
      assert.equal(option.label, wanted, 'Native Music Toast must reach the requested value within three semantic clicks');
      const afterMaster = await masterOption();
      assert.equal(afterMaster.valueSemantics, 'normalized_slider_fraction');
      assert.equal(afterMaster.value, 0, 'MASTER must remain zero; the helper never writes volume');
      return {before, after: option.label, masterVolume: master.value, masterVolumeAfter: afterMaster.value, clicks,
        masterWritten: false, source: 'native_semantic_sound_options'};
    } finally {await closeSettings();}
  }
  const prepared = await soundSettings(desired, true);
  let restored;
  const restore = async () => {
    if (restored) return restored;
    const settings = await soundSettings(originalLabel);
    restored = {case: 'hud_music_toast_option_restored', target, originalLabel, settings,
      preferenceRestored: settings.after === originalLabel, scope: 'owned_disposable_validation_options',
      masterWritten: false, debugSetterUsed: false, modeChanged: false, leaseAcquired: false};
    return restored;
  };
  restore.evidence = {case: 'hud_music_toast_option_prepared', target, originalLabel, desiredLabel: desired, settings: prepared,
    scope: 'owned_disposable_validation_options', masterWritten: false, debugSetterUsed: false,
    modeChanged: false, leaseAcquired: false};
  return restore;
}

export async function hideResidentFriendToast({api}) {
  // The caller arms its existing UI authority before showing the Toast. Do not switch modes,
  // reacquire a Lease or disarm between the actual hide and observing the native exit frames.
  const snapshot=await api('GET','/v0/ui/tree?category=DEBUG');
  const node=snapshot.presentation.children.find(node=>node.debugToastKind==='friend'&&!node.debugHideRequested);
  assert(node?.resourceVersion,'A fresh actual resident Friend Toast owner is required');
  const result=await api('POST','/v0/debug/mutations',{operation:'ui.toast.hide',nodeId:node.nodeId,
    expectedResourceVersion:node.resourceVersion,expectedValue:false,value:true});
  assert.equal(result.inputDispatchCount,0);assert.equal(result.evidenceAuthority,'DEBUG_PRIVILEGED');
  assert.equal(result.dataCategory,'DEBUG');return result;
}

export function validateResidentToastDraw(view, kind, phase) {
  assert.equal(view.dataCategory, 'USER'); assert.equal(view.overlayIdentity, 0);
  const node = toast(view, kind, phase); assert(node, `No actual resident ${kind} ${phase} draw`);
  assert.equal(node.role, 'toast'); assert.equal(node.visible, true); assert.equal(node.active, false);
  assert.deepEqual(node.actions, []); assert.equal(node.boundsAvailable, true);
  assert.equal(node.boundsSemantics, 'native_toast_draw_viewport_intersection');
  assert.equal(node.labelSource, 'last_native_toast_render');
  assert(Number.isFinite(node.x) && Number.isFinite(node.y) && node.x >= 0 && node.y >= 0);
  assert(node.width > 0 && node.height > 0 && node.x + node.width <= view.width && node.y + node.height <= view.height);
  if (phase === 'fully_shown') {
    assert.equal(node.textPhase, 'drawn'); assert(node.label?.trim(), 'Real native fully shown text is required');
  } else {
    assert.equal(node.label, '', 'Partly offscreen text must not be guessed or disclosed');
    assert.equal(node.textPhase, 'withheld_partial_or_occluded');
  }
  if (kind === 'NowPlaying') { assert.equal(node.x, 0); assert.equal(node.y, 0); }
  for (const hidden of ['debugLabel', 'resourceVersion', 'debugToastValue', 'songId', 'skinProfile', 'uuid', 'queuedText'])
    assert.equal(node[hidden], undefined, `USER must not expose ${hidden}`);
  return node;
}

// Callbacks use existing probe-only native buttons / typed UI DEBUG dismissal. The native music
// fixture remains master-muted and disposable; neither account access nor old-song restoration is asserted.
export async function hudToastUserCompletionActions({ target, api, wait, check, samples,
  showNowPlayingFixture, clearNowPlayingFixture, showFriendFixture, hideFriendFixture }) {
  assert(['26.1.2-neoforge', '26.3-neoforge', '26.3-fabric'].includes(target), 'Only Targets with verified native resident Toast types are supported');
  const kinds = target === '26.1.2-neoforge' ? ['NowPlaying'] : ['Friend', 'NowPlaying'];
  const clean = async () => {
    const state = await api('GET', '/v0/input/state');
    assert.equal(state.pressedKeyCount, 0); assert.equal(state.pressedButtonCount, 0);
    assert.equal(state.inputCleanupPending, false); assert.equal(state.inputCleanupFailed, false);
    return state;
  };
  for (const kind of kinds) await check(`HUD: native resident ${kind} enter/full/exit USER presentation`, async () => {
    try {
      if (kind === 'NowPlaying') await showNowPlayingFixture(); else await showFriendFixture();
      const entering = validateResidentToastDraw(await wait(value => toast(value, kind, 'entering'), `${kind} actual entering draw`), kind, 'entering');
      const full = validateResidentToastDraw(await wait(value => toast(value, kind, 'fully_shown')?.label?.trim(), `${kind} actual fully shown text`), kind, 'fully_shown');
      if (kind === 'Friend') await hideFriendFixture();
      // NowPlaying's normal five-second expiry leaves real current music alive during its exit;
      // stopPlaying before this observation would correctly make the native draw disappear.
      const exiting = validateResidentToastDraw(await wait(value => toast(value, kind, 'exiting')?.textPhase === 'withheld_partial_or_occluded', `${kind} actual partially clipped exiting draw`), kind, 'exiting');
      await wait(value => !toast(value, kind), `${kind} native exit completed`);
      samples.push({ case: 'hud_resident_toast_user', target, kind, category: 'USER', entering, full, exiting,
        input: await clean(), partialTextWithheld: true, queuedTextExposed: false,
        ...(kind === 'NowPlaying' ? { source: 'native_public_music_manager', masterRemainedZero: true,
          audiblePlaybackAsserted: false, previousRandomSongRestored: false, cleanupPolicy: 'stop_disposable_native_playlist_not_transactional_song_restore' }
          : { friendsServiceContacted: false, source: 'native_local_friend_display_fixture' }) });
    } finally {
      if (kind === 'NowPlaying') await clearNowPlayingFixture();
    }
  });
}
