import assert from 'node:assert/strict';
import {validateHudCameraDraw} from './hud-camera-actions.mjs';
import {readLegacySleepingRule, readVisibleSleepingRule, sleepingRule as sleepingRuleFacts} from './ui-special-world-menu-actions.mjs';

const delay = milliseconds => new Promise(resolve => setTimeout(resolve, milliseconds));
const visible = node => node.visible !== false && node.active !== false && node.occluded !== true;
const kinds = new Set(['portal', 'sleep', 'nausea', 'vignette']);

export function cameraVariantRows(view, kind) {
  assert(kinds.has(kind));
  const phase = kind === 'sleep' ? 'sleep_overlay' : 'camera_overlay';
  const result = (view.presentation?.children ?? []).filter(node => node.renderPhase === phase
    && (kind === 'sleep' ? node.controlKey?.startsWith('hud.overlay.sleep.')
      : kind === 'portal' ? node.controlKey?.startsWith('hud.overlay.portal.') && node.resourceId?.includes('nether_portal')
        : node.resourceId === 'minecraft:textures/misc/' + kind + '.png'));
  if (kind === 'sleep') assert(view.screenClass.endsWith('InBedChatScreen'), 'Sleep must really use the native sleeping UI');
  else assert.equal(view.screenClass, '', 'A background effect is not permission to expose an obscured HUD');
  assert(result.length > 0, 'No actually drawn ' + kind + ' overlay');
  const actual = validateHudCameraDraw(view, {phase});
  for (const node of result) {
    assert(actual.some(draw => draw.nodeId === node.nodeId));
    assert.equal(node.role, 'status_icon');
    for (const forbidden of ['nauseaIntensity', 'vignetteBrightness', 'sleepTimer', 'portalTime', 'effectDuration', 'rawColor'])
      assert.equal(node[forbidden], undefined, 'USER must not publish ' + forbidden);
  }
  return result;
}

// Arrange changes only the outer runner's disposable copy. Native options are restored through
// their real controls; world time/recipe/item history is restored by discarding that copy.
export async function hudCameraVariantActions({api, target, command, key, tree, wait, nodes, ui, check, samples},
  services = {}) {
  const rule = sleepingRuleFacts(target);
  const legacy = target.startsWith('1.');
  const now = services.now ?? (() => performance.now()), sleep = services.sleep ?? delay;
  const deadline = now() + 180000; let lastInput = -Infinity;
  const calls = [], sleepRuleReads = [];
  async function pace() {
    const remaining = 550 - (now() - lastInput); if (remaining > 0) await sleep(remaining);
    assert(now() < deadline, 'Camera variants exceeded 180 seconds'); lastInput = now();
  }
  async function poll(read, predicate, label, timeout = 5000) {
    const end = Math.min(deadline, now() + timeout); let value;
    do {value = await read(); if (predicate(value)) return value; await sleep(50);} while (now() < end);
    assert.fail(label + ': ' + JSON.stringify(value));
  }
  async function clean() {
    const input = await api('GET', '/v0/input/state');
    assert.equal(input.pressedKeyCount, 0); assert.equal(input.pressedButtonCount, 0);
    assert.equal(input.inputCleanupPending, false); assert.equal(input.inputCleanupFailed, false); return input;
  }
  async function tap(code) {await pace(); await key(code); calls.push({key: code});}
  async function act(node, action = 'click', extra = {}) {
    assert(node?.nodeId && visible(node) && node.actions?.includes(action), 'A current semantic control is required');
    await pace(); const result = await ui({nodeId: node.nodeId}, action, extra);
    calls.push({nodeId: node.nodeId, action, ...extra}); return result;
  }
  async function arrange(text) {const result = await command(text); lastInput = now(); return result;}
  function one(value, predicate, label) {
    const matches = nodes(value).filter(node => visible(node) && predicate(node));
    assert.equal(matches.length, 1, label); return matches[0];
  }
  async function button(key) {return act(one(await tree(), node => node.translationKey === key && node.actions?.includes('click'), key));}
  async function page(name) {
    const value = await wait(value => value.screenClass.endsWith(name), name);
    assert.equal(value.dataCategory, 'USER'); assert.equal(value.overlayIdentity, 0); assert.equal(value.treeTruncated, false); return value;
  }
  async function closeSettings() {
    for (let count = 0; count < 5; count++) {
      const current = await tree(); if (!current.screenClass) {await clean(); return;}
      assert(/(?:PauseScreen|OptionsScreen|VideoSettingsScreen|AccessibilityOptionsScreen|SoundOptionsScreen|WorldOptionsScreen|InWorldGameRulesScreen)$/.test(current.screenClass), 'Do not dismiss an unrelated Screen');
      if (current.screenClass.endsWith('PauseScreen')) await button('menu.returnToGame');
      else {
        const buttons = nodes(current).filter(node => visible(node) && node.actions?.includes('click'));
        const cancel = buttons.find(node => node.translationKey === 'gui.cancel'), done = buttons.find(node => node.translationKey === 'gui.done');
        assert(cancel || done, 'Native Done/Cancel must be present'); await act(cancel ?? done);
      }
    }
    assert.fail('Owned settings did not return to gameplay');
  }
  async function openOptions(destination) {
    assert.equal((await tree()).screenClass, ''); await tap(256); await page('PauseScreen'); await button('menu.options'); await page('OptionsScreen');
    const nativeKey = target === '1.20.1-forge' && destination === 'options.accessibility'
      ? 'options.accessibility.title' : destination;
    await button(nativeKey); return page(destination === 'options.video' ? 'VideoSettingsScreen' : destination === 'options.sounds' ? 'SoundOptionsScreen' : 'AccessibilityOptionsScreen');
  }
  async function findOption(predicate, label) {
    for (let count = 0; count < 16; count++) {
      const current = await tree(), found = nodes(current).filter(node => visible(node) && predicate(node));
      if (found.length) {assert.equal(found.length, 1, label); return found[0];}
      const scroll = one(current, node => node.role === 'list' && node.actions?.includes('scroll'), 'One native options list');
      const before = scroll.scrollAmount;
      await act(scroll, 'scroll', {yOffset: -4});
      await poll(tree, value => {
        const fresh = nodes(value).find(node => node.nodeId === scroll.nodeId);
        return nodes(value).some(node => visible(node) && predicate(node)) || fresh?.scrollAmount !== before;
      }, 'Native list advances toward ' + label);
    }
    assert.fail('Option was not exposed in the bounded native list: ' + label);
  }
  async function master() {
    let primary;
    try {
      await openOptions('options.sounds');
      const node = await findOption(node => node.role === 'slider' && /^Master Volume\b/.test(node.label ?? ''), 'Actual Master Volume slider');
      assert.equal(node.value, 0, 'MASTER must already be muted; this helper never writes sound volume');
      return {nodeId: node.nodeId, value: node.value};
    } catch (error) {primary = error; throw error;}
    finally {await cleanup(primary, [closeSettings]);}
  }
  async function initialPlayer() {
    assert.equal((await tree()).screenClass, ''); await clean();
    const player = await api('GET', '/v0/player?category=USER');
    assert.equal(player.available, true); assert.equal(player.dimension, 'minecraft:overworld');
    for (const field of ['x', 'y', 'z', 'yaw', 'pitch']) assert(Number.isFinite(player[field]), field);
    assert(['creative', 'survival', 'adventure', 'spectator'].includes(player.gameMode)); return player;
  }
  async function restorePlayer(player) {
    await arrange(`execute in minecraft:overworld run tp @s ${player.x} ${player.y} ${player.z} ${player.yaw} ${player.pitch}`);
    await arrange('gamemode ' + player.gameMode + ' @s');
    await poll(() => api('GET', '/v0/player?category=USER'), value => value.available && value.dimension === player.dimension
      && Math.abs(value.x - player.x) < .05 && Math.abs(value.y - player.y) < .05 && Math.abs(value.z - player.z) < .05
      && value.gameMode === player.gameMode, 'Original fixture player position/mode restored');
  }
  async function cleanup(primary, jobs) {
    const errors = [];
    for (const job of jobs) try {await job();} catch (error) {errors.push(error);}
    if (errors.length) {
      if (primary) primary.cleanupErrors = errors.map(error => error.message);
      else throw new AggregateError(errors, 'Native camera fixture cleanup failed');
    }
  }
  const point = (x, y, z) => api('GET', '/v0/world/block?' + new URLSearchParams({x, y, z, category: 'USER'}));
  const known = (x, y, z, id) => poll(() => point(x, y, z), value => value.available && value.block === id
    && value.dataCategory === 'USER' && value.chunkLoadRequested === false && typeof value.targetRef === 'string', 'Fixture block received: ' + id);
  async function worldAction(step) {
    await pace(); const accepted = await api('POST', '/v0/pipelines', {steps: [{type: 'world.action', category: 'USER', ...step}], timeoutMs: 12000});
    const result = await api('POST', '/v0/operations/' + accepted.operationId + '/wait', {timeoutMs: 14000});
    assert.equal(result.state, 'completed', 'World input must reach an actual terminal completion');
    const receipts = (result.result?.steps ?? []).filter(step => step.stepType === 'world.action'); assert.equal(receipts.length, 1);
    const receipt = receipts[0].result; assert.equal(receipt.dataCategory, 'USER'); assert.equal(receipt.evidenceAuthority, 'PLAYTEST');
    assert.equal(receipt.directMutationUsed, false); assert.equal(receipt.effectConfirmed, false); return result;
  }
  async function actual(kind, timeout = 6000) {
    return poll(tree, value => {try {cameraVariantRows(value, kind); return true;} catch {return false;}}, 'Native ' + kind + ' overlay', timeout);
  }
  async function absent(kind) {
    const before = (await tree()).clientTick;
    assert(Number.isSafeInteger(before));
    return poll(tree, value => Number.isSafeInteger(value.clientTick) && value.clientTick >= before + 3
      && !(value.presentation?.children ?? []).some(node => kind === 'sleep' ? node.renderPhase === 'sleep_overlay'
        : kind === 'portal' ? node.controlKey?.startsWith('hud.overlay.portal.')
          : node.resourceId === 'minecraft:textures/misc/' + kind + '.png'), 'Native ' + kind + ' draw disappears', 6000);
  }
  function sample(kind, value, extra = {}) {
    return {case: 'hud_camera_variant_' + kind, target, dataCategory: 'USER', nodes: cameraVariantRows(value, kind),
      sourceTick: value.clientTick, nativeScreen: value.screenClass, finalPixelAssertion: false, rawRendererValuesRead: false,
      directRendererSetterUsed: false, semanticTargets: true, handwrittenCoordinates: false,
      arrangeRole: 'diagnostic_disposable_world_only', arrangeCommandDataCategory: 'USER',
      assertSource: 'USER_native_completed_hud_draw', ...extra};
  }
  await check('HUD camera variants: real survival movement enters a portal briefly without changing dimension', async () => {
    const player = await initialPlayer(); let primary, movement, evidence; const beginCalls = calls.length;
    try {
      await arrange('gamemode survival @s'); await arrange('fill -2 79 -1 2 79 10 minecraft:stone');
      await arrange('fill -2 80 -1 2 84 10 minecraft:air');
      await arrange('fill -1 79 2 2 83 2 minecraft:obsidian');
      await arrange('fill 0 80 2 1 82 2 minecraft:nether_portal[axis=x]');
      await known(0, 80, 2, 'minecraft:nether_portal'); await arrange('tp @s 0.5 80 1.2 0 0');
      await poll(() => api('GET', '/v0/player?category=USER'), value => value.available && value.dimension === player.dimension
        && Math.abs(value.z - 1.2) < .05, 'Native player stands just outside the portal');
      await pace(); movement = await api('POST', '/v0/pipelines', {steps: [{type: 'key', key: 87, action: 1},
        {type: 'delay', durationMs: 1500}, {type: 'key', key: 87, action: 0}], timeoutMs: 4000});
      const value = await actual('portal', 2000);
      const terminal = await api('POST', '/v0/operations/' + movement.operationId + '/wait', {timeoutMs: 4000});
      assert.equal(terminal.state, 'completed');
      await poll(() => api('GET', '/v0/player?category=USER'), value => value.available
        && value.dimension === player.dimension && value.z > 3, 'Native forward movement must carry the player out of the portal before stopping');
      const stop = await api('POST', '/v0/input/release-all', {});
      await arrange('tp @s 0.5 80 0.5 0 0');
      const after = await poll(() => api('GET', '/v0/player?category=USER'), value => value.available
        && value.dimension === player.dimension && value.z < 1, 'The player must actually leave the portal');
      await absent('portal'); await clean();
      evidence = sample('portal', value, {movementOperationId: movement.operationId, movementState: terminal.state,
        observationMayCancelActiveMovement: false, leftPortalThroughNativeMovement: true,
        stop, dimensionBefore: player.dimension, dimensionAfter: after.dimension,
        actualPortalDraw: true, dimensionTransferValidated: false});
    } catch (error) {primary = error; throw error;}
    finally {
      await cleanup(primary, [() => api('POST', '/v0/input/release-all', {}), () => restorePlayer(player),
        () => arrange('fill -1 80 2 2 83 2 minecraft:air'),
        () => arrange('fill -1 79 2 2 79 2 minecraft:stone'), clean]);
    }
    samples.push({...evidence, playerRestored: true, input: await clean(), actions: calls.slice(beginCalls)});
  });
  async function sleepingRule() {
    if (legacy) {const result = await readLegacySleepingRule({target, tree, command: arrange, poll}); sleepRuleReads.push(result); return result.value;}
    let primary;
    try {
      await tap(256); await page('PauseScreen');
      if (target === '26.1.2-neoforge') {await button('menu.options'); await page('OptionsScreen');}
      await button('options.worldOptions.button'); await page('WorldOptionsScreen');
      await button('editGamerule.inGame.button'); await page('InWorldGameRulesScreen');
      const result = await readVisibleSleepingRule({target, tree, nodes, ui: async (selector, action, extra) => {
        const current = await tree(), node = one(current, node => node.nodeId === selector.nodeId, 'Current native game-rule Search');
        return act(node, action, extra);
      }, poll}); sleepRuleReads.push(result); return result.value;
    } catch (error) {primary = error; throw error;}
    finally {await cleanup(primary, [closeSettings]);}
  }
  await check('HUD camera variants: real bed use draws the sleep overlay with its InBed Chat and wakes through the native button', async () => {
    const player = await initialPlayer(), originalRule = await sleepingRule();
    let changedRule = false, primary, evidence; const beginCalls = calls.length;
    try {
      changedRule = true; await arrange('gamerule ' + rule.command + ' 101'); assert.equal(await sleepingRule(), 101);
      await arrange('gamemode creative @s'); await arrange('fill -2 79 -1 2 79 5 minecraft:stone');
      await arrange('fill -2 80 -1 2 83 4 minecraft:air'); await arrange('tp @s 0.5 80 0.5 0 0');
      await arrange('item replace entity @s weapon.mainhand with minecraft:red_bed 1');
      await wait(value => value.presentation?.children?.some(node => node.selected && node.item === 'minecraft:red_bed' && node.count === 1), 'Actual client receives one owned bed');
      const support = await known(0, 79, 2, 'minecraft:stone');
      const placed = await worldAction({targetRef: support.targetRef, action: 'place', face: 'up', hit: {u: .5, v: .5}, expectedDestinationBlockId: 'minecraft:air'});
      await known(0, 80, 2, 'minecraft:red_bed'); await known(0, 80, 3, 'minecraft:red_bed');
      await arrange('item replace entity @s weapon.mainhand with minecraft:air');
      await wait(value => value.presentation?.children?.some(node => node.selected && node.item === 'minecraft:air' && node.count === 0), 'Owned bed removed from the actual hand');
      await arrange('time set night'); await arrange('weather clear');
      const entered = await worldAction({targetRef: (await known(0, 80, 2, 'minecraft:red_bed')).targetRef, action: 'use'});
      const value = await actual('sleep'); await button('multiplayer.stopSleeping');
      await wait(value => !value.screenClass, 'Native wake button closes sleeping UI'); await absent('sleep');
      evidence = sample('sleep', value, {placed, entered, sleepRuleDuring: 101, stopSleepingThroughNativeButton: true});
    } catch (error) {primary = error; throw error;}
    finally {
      await cleanup(primary, [
        async () => {if ((await tree()).screenClass.endsWith('InBedChatScreen')) await button('multiplayer.stopSleeping');},
        () => api('POST', '/v0/input/release-all', {}), closeSettings,
        async () => {if (changedRule) {await arrange('gamerule ' + rule.command + ' ' + originalRule); assert.equal(await sleepingRule(), originalRule);}},
        () => arrange('item replace entity @s weapon.mainhand with minecraft:air'),
        () => arrange('fill 0 80 2 0 80 3 minecraft:air'), () => arrange('time set day'), () => restorePlayer(player), clean,
      ]);
    }
    samples.push({...evidence, sleepRuleRestored: originalRule, sleepRuleReads, fixtureClockResetToDay: true, originalWorldClockRestored: false,
      input: await clean(), actions: calls.slice(beginCalls)});
  });
  await check('HUD camera variants: native nausea accessibility overlay follows a real effect and restores Screen Effects', async () => {
    let original, changed = false, primary, evidence; const beforeMaster = await master(), beginCalls = calls.length;
    try {
      await openOptions('options.accessibility');
      let node = await findOption(node => node.role === 'slider' && /^Distortion Effects\b|^Screen Effects\b/.test(node.label ?? ''), 'Native Screen Effects slider');
      original = node.value; assert(Number.isFinite(original) && original >= 0 && original <= 1);
      changed = true; await act(node, 'set_value', {value: 0});
      node = await findOption(node => node.role === 'slider' && /^Distortion Effects\b|^Screen Effects\b/.test(node.label ?? ''), 'Native Screen Effects slider');
      assert.equal(node.value, 0); await closeSettings();
      await arrange('effect give @s minecraft:nausea 12 0 true'); const value = await actual('nausea', 10000);
      await arrange('effect clear @s minecraft:nausea'); await absent('nausea');
      evidence = sample('nausea', value, {screenEffectScaleDuring: 0, effectArrangedByNativeCommand: true,
        coverage: 'native_accessibility_nausea_texture_not_3d_world_distortion'});
    } catch (error) {primary = error; throw error;}
    finally {
      await cleanup(primary, [() => api('POST', '/v0/input/release-all', {}), () => arrange('effect clear @s minecraft:nausea'), closeSettings,
        async () => {
          if (!changed) return; await openOptions('options.accessibility');
          const node = await findOption(node => node.role === 'slider' && /^Distortion Effects\b|^Screen Effects\b/.test(node.label ?? ''), 'Native Screen Effects restore');
          await act(node, 'set_value', {value: original});
          assert.equal((await findOption(node => node.role === 'slider' && /^Distortion Effects\b|^Screen Effects\b/.test(node.label ?? ''), 'Screen Effects restored')).value, original);
          await closeSettings();
        }, clean]);
    }
    const afterMaster = await master(); samples.push({...evidence, screenEffectScaleRestored: original,
      masterBefore: beforeMaster.value, masterAfter: afterMaster.value, masterVolumeWritten: false, input: await clean(), actions: calls.slice(beginCalls)});
  });
  await check('HUD camera variants: native video Vignette branch records available controls and USER visibility', async () => {
    let original, changed = false, primary, evidence; const beforeMaster = await master(), beginCalls = calls.length;
    if (legacy) {
      let hidden = false;
      try {
        const value = await actual('vignette');
        await tap(290); hidden = true; const f1 = await absent('vignette'); assert.equal(f1.screenClass, '');
        await tap(290); hidden = false; const restored = await actual('vignette');
        await tap(256); const paused = await page('PauseScreen'); await absent('vignette');
        await button('menu.returnToGame'); const resumed = await actual('vignette'); await clean();
        evidence = sample('vignette', value, {nativeVignetteOptionAvailable: false,
          optionDisposition: 'NATIVE_INDEPENDENT_VIGNETTE_OPTION_ABSENT', nativeVideoOptionOffSuppressesDraw: false,
          f1ProjectedNodeCount: (f1.presentation?.children ?? []).filter(node => node.resourceId === 'minecraft:textures/misc/vignette.png').length,
          screenOcclusion: paused.screenClass, restoredAfterF1Tick: restored.clientTick, restoredAfterScreenTick: resumed.clientTick,
          userProjectionOcclusionAsserted: true, framebufferDarknessAsserted: false});
      } catch (error) {primary = error; throw error;}
      finally {await cleanup(primary, [() => api('POST', '/v0/input/release-all', {}), closeSettings,
        async () => {if (hidden) await tap(290);}, clean]);}
      const afterMaster = await master(); samples.push({...evidence, vignetteOptionRestored: 'not_applicable',
        masterBefore: beforeMaster.value, masterAfter: afterMaster.value, masterVolumeWritten: false, input: await clean(), actions: calls.slice(beginCalls)});
      return;
    }
    const isVignette = node => /^Show Vignette: (?:ON|OFF)$/.test(node.label ?? '');
    async function setting(enabled) {
      await openOptions('options.video'); let node = await findOption(isVignette, 'Native Vignette option');
      const before = node.label;
      if ((node.label.endsWith(': ON')) !== enabled) {await act(node); node = await findOption(isVignette, 'Native Vignette changes');}
      assert.equal(node.label, 'Show Vignette: ' + (enabled ? 'ON' : 'OFF')); await closeSettings(); return before;
    }
    try {
      await openOptions('options.video'); const node = await findOption(isVignette, 'Native Vignette option'); original = node.label.endsWith(': ON');
      await closeSettings(); changed = true; await setting(true); const value = await actual('vignette');
      await setting(false); await absent('vignette');
      evidence = sample('vignette', value, {nativeVignetteOptionAvailable: true, nativeVideoOptionOffSuppressesDraw: true, framebufferDarknessAsserted: false});
    } catch (error) {primary = error; throw error;}
    finally {await cleanup(primary, [() => api('POST', '/v0/input/release-all', {}), closeSettings,
      async () => {if (changed) await setting(original);}, clean]);}
    const afterMaster = await master(); samples.push({...evidence, vignetteOptionRestored: original,
      masterBefore: beforeMaster.value, masterAfter: afterMaster.value, masterVolumeWritten: false, input: await clean(), actions: calls.slice(beginCalls)});
  });
}
