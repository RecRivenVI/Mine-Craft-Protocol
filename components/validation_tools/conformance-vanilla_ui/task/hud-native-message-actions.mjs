import assert from 'node:assert/strict';
import {validateLateTextDraw} from './hud-late-text-actions.mjs';

const targets = ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric'];
const nodes = value => (value.children ?? []).flatMap(node => [node, ...nodes(node)]);
const drawn = (value, family) => (value.presentation?.children ?? []).filter(node => node.renderPhase === family);
const selected = value => (value.presentation?.children ?? []).find(node => node.selected && node.controlKey?.startsWith('hud.hotbar.'));

export function subtitleSetting(node) {
  assert(node?.active !== false, 'The real subtitles option must be enabled');
  const match = /^(?:Closed Captions|Show Subtitles):\s*(ON|OFF)$/.exec(node.label);
  assert(match, 'The English disposable fixture must expose the actual native subtitle ON/OFF label');
  return match[1] === 'ON';
}

export function validateNativeSubtitle(value, expected = 'Cow moos') {
  assert.equal(value.screenClass, '');
  const observed = validateLateTextDraw(value, 'subtitle', {includes: expected});
  const directions = observed.filter(node => node.textPhase === 'drawn' && /^[<>]$/.test(node.label));
  assert(directions.length > 0, 'A real native direction glyph is required, not a sound-coordinate inference');
  return {text: observed.filter(node => node.textPhase === 'drawn' && node.label.includes(expected)), directions};
}

export function validateNativeRecord(value, expected = 'Now Playing: C418 - 13') {
  assert.equal(value.screenClass, '');
  const observed = validateLateTextDraw(value, 'overlay_message');
  assert(observed.some(node => node.textPhase === 'drawn' && node.label === expected), 'The real record description must be drawn');
  return observed;
}

// Commands only arrange this caller-owned disposable world or emit a native sound. The record
// Act is USER world.action use. No actionbar setter, sound-listener call or HUD value is fabricated.
export async function hudNativeMessageActions({target, api, command, key, tree, wait, ui, check, samples}, services = {}) {
  assert(targets.includes(target));
  let originalSubtitles, ownedBlock, ownedDisc = false, primary, inputMayStillRun = false;
  const now = services.now ?? (() => performance.now());
  const sleep = services.sleep ?? (milliseconds => new Promise(done => setTimeout(done, milliseconds)));
  const cleanupErrors = [];
  const page = async name => {
    const value = await wait(value => value.screenClass.split('.').at(-1) === name, 'native ' + name);
    assert.equal(value.dataCategory, 'USER'); assert.equal(value.overlayIdentity, 0); return value;
  };
  const zero = async () => {
    const value = await api('GET', '/v0/input/state');
    assert.equal(value.pressedKeyCount, 0); assert.equal(value.pressedButtonCount, 0);
    assert.equal(value.inputCleanupPending, false); assert.equal(value.inputCleanupFailed, false); return value;
  };
  async function semantic(predicate, label, direction = -1) {
    for (let count = 0; count < 12; count++) {
      const current = await tree(), matches = nodes(current).filter(node => node.visible !== false && predicate(node));
      assert(matches.length <= 1, label);
      if (matches.length) {assert.notEqual(matches[0].active, false); return matches[0];}
      assert(current.screenClass.endsWith('SoundOptionsScreen'), label);
      const lists = nodes(current).filter(node => node.visible !== false && node.role === 'list' && node.actions?.includes('scroll'));
      assert.equal(lists.length, 1, 'One visible native Sound Options list');
      const list = lists[0]; assert.notEqual(list.active, false); assert(Number.isFinite(list.scrollAmount));
      await ui({nodeId: list.nodeId}, 'scroll', {yOffset: direction * 4});
      await wait(value => value.screenClass === current.screenClass
        && (nodes(value).some(node => node.visible !== false && predicate(node))
          || nodes(value).some(node => node.nodeId === list.nodeId && node.scrollAmount !== list.scrollAmount)),
      label + ' reached by native list scroll');
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
    assert.fail('Native sound settings did not close');
  }
  async function soundSettings(desired, remember = false, restoring = false) {
    let value = await tree();
    if (!value.screenClass) {await key(256); value = await page('PauseScreen');}
    assert.equal(value.screenClass.split('.').at(-1), 'PauseScreen', 'Sound settings begin at the owned Pause Screen');
    try {
      await click(node => node.translationKey === 'menu.options' || node.label === 'Options...', 'Options button'); await page('OptionsScreen');
      await click(node => node.translationKey === 'options.sounds' || node.label === 'Music & Sounds...', 'Music & Sounds button'); await page('SoundOptionsScreen');
      const master = await semantic(node => node.role === 'slider' && /^Master Volume:/.test(node.label), 'Master Volume slider', 1);
      assert.equal(master.valueSemantics, 'normalized_slider_fraction');
      if (!restoring) assert.equal(master.value, 0, 'MASTER must already be zero; the helper never writes volume');
      let subtitle = await semantic(node => /^(?:Closed Captions|Show Subtitles):/.test(node.label), 'native subtitle option');
      const before = subtitleSetting(subtitle);
      if (remember && originalSubtitles === undefined) originalSubtitles = before;
      if (desired !== undefined && before !== desired) {
        assert(subtitle.actions.includes('click')); await ui({nodeId: subtitle.nodeId});
        value = await wait(value => nodes(value).some(node => node.visible !== false && /^(?:Closed Captions|Show Subtitles):/.test(node.label)
          && subtitleSetting(node) === desired), 'actual native subtitle setting changed');
        subtitle = await semantic(node => /^(?:Closed Captions|Show Subtitles):/.test(node.label), 'native subtitle option after click');
      }
      return {masterVolume: master.value, subtitlesBefore: before, subtitlesAfter: subtitleSetting(subtitle),
        masterWritten: false, label: subtitle.label};
    } finally {await closeSettings();}
  }
  const point = at => api('GET', '/v0/world/block?category=USER&' + new URLSearchParams(at));
  function userBlock(value) {
    assert.equal(value.available, true); assert.equal(value.dataCategory, 'USER'); assert.equal(value.authority, 'CLIENT_KNOWN');
    return value;
  }
  async function pollBlock(at, predicate, label) {
    let last; const deadline = now() + 5000;
    for (let i = 0; i < 100 && now() <= deadline; i++) {
      last = userBlock(await point(at)); if (predicate(last)) return last;
      await sleep(50);
    }
    assert.fail(label + ': ' + JSON.stringify(last));
  }
  async function useRecord(targetRef) {
    inputMayStillRun = true;
    try {
      const request = {steps: [{type: 'world.action', category: 'USER', targetRef, action: 'use'}], timeoutMs: 12000};
      const accepted = await api('POST', '/v0/pipelines', request);
      assert.equal(typeof accepted.operationId, 'string');
      const result = await api('POST', '/v0/operations/' + accepted.operationId + '/wait', {timeoutMs: 14000});
      assert.equal(result.state, 'completed', JSON.stringify(result));
      const receipts = (result.result?.steps ?? []).filter(step => step.stepType === 'world.action'); assert.equal(receipts.length, 1);
      assert.equal(receipts[0].result.dataCategory, 'USER'); assert.equal(receipts[0].result.evidenceAuthority, 'PLAYTEST');
      assert.equal(receipts[0].result.directMutationUsed, false); assert.equal(receipts[0].result.effectConfirmed, false);
      await zero(); inputMayStillRun = false; return {request, accepted, result};
    } catch (error) {
      // An uncertain input RPC is never replayed. Strong stop drains old input before fixture cleanup.
      try {await api('POST', '/v0/input/release-all', {}); await zero(); inputMayStillRun = false;}
      catch (cleanup) {error.inputCleanupFailure = String(cleanup); throw error;}
      throw error;
    }
  }
  try {
    await check('HUD: native messages: real subtitle text direction and natural expiry while muted', async () => {
      const settings = await soundSettings(true, true);
      const closed = await wait(value => !value.screenClass, 'native sound settings close');
      const listenerFrame = await wait(value => !value.screenClass && value.clientTick >= closed.clientTick + 2, 'native subtitle listener gets completed gameplay frames');
      await wait(value => drawn(value, 'subtitle').length === 0, 'no previous native subtitle remains');
      const emitted = await command('playsound minecraft:entity.cow.ambient neutral @s ^4 ^ ^ 1 1');
      const view = await wait(value => {
        try {validateNativeSubtitle(value); return true;} catch {return false;}
      }, 'actual cow subtitle text and direction draw');
      samples.push({case: 'hud_native_subtitle', target, category: 'USER', settings, emitted,
        listenerClientTick: listenerFrame.clientTick, draw: validateNativeSubtitle(view), input: await zero(),
        authority: 'completed_native_gui_draw', soundReceiptIsNotDrawProof: true, audiblePlaybackAsserted: false});
      const expired = await wait(value => !value.screenClass && drawn(value, 'subtitle').length === 0, 'native subtitle naturally expires');
      samples.push({case: 'hud_native_subtitle_expired', target, remainingNativeSubtitleDraws: drawn(expired, 'subtitle').length,
        clientTick: expired.clientTick, input: await zero(), sourceCacheClearedByTool: false});
    });
    await check('HUD: native messages: real record use produces native Now Playing overlay and expires', async () => {
      const settings = await soundSettings();
      const actor = await api('GET', '/v0/player?category=USER');
      assert.equal(actor.available, true); assert.equal(actor.dataCategory, 'USER');
      for (const field of ['x', 'y', 'z']) assert(Number.isFinite(actor[field]));
      const beforeTree = await tree(); assert.equal(beforeTree.screenClass, '');
      const held = selected(beforeTree); assert(held && held.count === 0, 'Do not replace an unknown nonempty selected item');
      const at = {x: Math.floor(actor.x), y: Math.floor(actor.y), z: Math.floor(actor.z) + 2};
      const before = userBlock(await point(at)); assert.equal(before.block, 'minecraft:air', 'Only an empty owned fixture position may be used');
      await wait(value => drawn(value, 'overlay_message').length === 0, 'no previous native overlay message remains');
      ownedBlock = at; const arranged = await command(`setblock ${at.x} ${at.y} ${at.z} minecraft:jukebox`);
      await pollBlock(at, value => value.block === 'minecraft:jukebox', 'native jukebox received');
      ownedDisc = true; const given = await command('item replace entity @s weapon.mainhand with minecraft:music_disc_13');
      await wait(value => selected(value)?.item === 'minecraft:music_disc_13' && selected(value)?.count === 1, 'real record in selected player slot');
      const targetBlock = userBlock(await point(at)); assert.equal(targetBlock.block, 'minecraft:jukebox');
      assert.equal(typeof targetBlock.targetRef, 'string'); assert(targetBlock.targetRef);
      const used = await useRecord(targetBlock.targetRef);
      const after = await pollBlock(at, value => value.block === 'minecraft:jukebox' && value.state?.includes('has_record=true'), 'record actually inserted');
      const view = await wait(value => {
        try {validateNativeRecord(value); return true;} catch {return false;}
      }, 'actual native record-description overlay');
      samples.push({case: 'hud_native_record', target, category: 'USER', settings, fixture: {at, before, arranged, given},
        worldTarget: targetBlock, used, after, draw: validateNativeRecord(view), input: await zero(),
        actAuthority: 'PLAYTEST', arrangeAuthority: 'diagnostic_admin_command', authority: 'completed_native_gui_draw',
        effectConfirmedByOperation: false, audiblePlaybackAsserted: false, actionbarCommandUsed: false});
      const expired = await wait(value => !value.screenClass && drawn(value, 'overlay_message').length === 0, 'native record overlay naturally expires');
      samples.push({case: 'hud_native_record_expired', target, clientTick: expired.clientTick,
        remainingNativeOverlayDraws: drawn(expired, 'overlay_message').length, input: await zero(), hudValueClearedByTool: false});
    });
    samples.push({case: 'hud_native_messages_scope', target, subtitleAndRecordPaths: true,
      savingIndicatorStatus: 'separate_native_save_scenario', demoStatus: 'separate_demo_launch_scenario', audiblePlaybackAsserted: false});
  } catch (error) {primary = error;}
  const cleanup = async run => {try {await run();} catch (error) {cleanupErrors.push(error);}};
  if (inputMayStillRun) cleanupErrors.push(new Error('Mandatory input cleanup is unconfirmed; fixture and settings retained to avoid late input'));
  if (!inputMayStillRun) await cleanup(async () => {await closeSettings(); await zero();});
  if (ownedBlock && !inputMayStillRun) await cleanup(async () => {
    const value = userBlock(await point(ownedBlock));
    assert(['minecraft:jukebox', 'minecraft:air'].includes(value.block), 'Do not overwrite a changed fixture block');
    if (value.block === 'minecraft:jukebox') {
      await command(`item replace block ${ownedBlock.x} ${ownedBlock.y} ${ownedBlock.z} container.0 with minecraft:air`);
      await command(`setblock ${ownedBlock.x} ${ownedBlock.y} ${ownedBlock.z} minecraft:air`);
    }
    await pollBlock(ownedBlock, value => value.block === 'minecraft:air', 'owned jukebox removed');
  });
  if (ownedDisc && !inputMayStillRun) await cleanup(async () => {
    const held = selected(await tree()); assert(held && (held.count === 0 || held.item === 'minecraft:music_disc_13'), 'Do not remove an unrelated held item');
    if (held.count) await command('item replace entity @s weapon.mainhand with minecraft:air');
    await wait(value => selected(value)?.count === 0, 'owned disc removed');
  });
  if (originalSubtitles !== undefined && !inputMayStillRun) await cleanup(async () => {
    const restored = await soundSettings(originalSubtitles, false, true);
    assert.equal(restored.subtitlesAfter, originalSubtitles); assert.equal(restored.masterVolume, 0);
    samples.push({case: 'hud_native_messages_settings_restored', target, settings: restored, input: await zero()});
  });
  await cleanup(async () => {assert.equal((await tree()).screenClass, ''); await zero();});
  if (primary) {if (cleanupErrors.length) primary.cleanupFailures = cleanupErrors.map(String); throw primary;}
  if (cleanupErrors.length) throw new AggregateError(cleanupErrors, 'Native message fixture cleanup failed');
}
