import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {validateLateTextDraw} from './hud-late-text-actions.mjs';

const TARGETS = ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric'];

export function sleepingRule(target) {
  assert(TARGETS.includes(target));
  return target.startsWith('1.') ? {command: 'playersSleepingPercentage', key: 'gamerule.playersSleepingPercentage'}
    : {command: 'minecraft:players_sleeping_percentage', key: 'gamerule.minecraft.players_sleeping_percentage'};
}

export async function readVisibleSleepingRule({target, tree, nodes, ui, poll}) {
  const rule = sleepingRule(target), ids = new Set([rule.command, rule.command.replace(/^minecraft:/, '')]);
  const visible = node => node.visible !== false && node.active !== false && node.occluded !== true;
  const field = node => visible(node) && node.role === 'text_field' && node.actions?.includes('type_text');
  const matchesRule = node => field(node) && (node.translationKey === rule.key
    || (node.label ?? '').split(/\r?\n/).some(line => ids.has(line.trim())));
  // Native search has an empty message plus a Search hint; the USER tree may not export that hint.
  // Numeric rule fields instead have the native title + newline + exact rule id + newline message.
  const searchField = node => field(node) && (node.translationKey === 'gui.game_rule.search'
    || /^(?:Search|Search\.\.\.)$/.test(node.label ?? '')
    || node.class === 'net.minecraft.client.gui.components.EditBox' && node.label === '');
  let last, search;
  const summary = () => ({screenClass: last?.screenClass, screenIdentity: last?.screenIdentity,
    dataCategory: last?.dataCategory, fields: (last?.dataCategory === 'USER' ? nodes(last) : []).filter(node => ['text_field', 'list'].includes(node.role)).slice(0, 12)
      .map(node => ({nodeId: node.nodeId, role: node.role, class: node.class, label: String(node.label ?? '').slice(0, 180),
        translationKey: node.translationKey, value: String(node.value ?? '').slice(0, 64), visible: node.visible,
        active: node.active, actions: node.actions?.slice(0, 6)}))});
  const read = async () => {
    last = await tree(); assert.equal(last.dataCategory, 'USER'); assert.equal(last.treeTruncated, false);
    assert.equal(last.overlayIdentity ?? 0, 0); assert(last.screenClass.endsWith('InWorldGameRulesScreen')); return last;
  };
  try {
    let value = await poll(read, value => nodes(value).some(node => searchField(node) || matchesRule(node)), 'Native game-rule Search or visible sleeping field');
    const searches = nodes(value).filter(searchField); assert(searches.length <= 1, 'One visible native game-rule Search input');
    if (searches.length) {
      const before = searches[0]; await ui({nodeId: before.nodeId}, 'type_text', {text: 'sleep'});
      value = await poll(read, value => nodes(value).some(node => searchField(node) && node.nodeId === before.nodeId && node.value === 'sleep')
        && nodes(value).some(matchesRule), 'Native sleep filter exposes its exact sleeping rule');
      search = {nodeId: before.nodeId, label: before.label, query: 'sleep', value: nodes(value).find(node => node.nodeId === before.nodeId)?.value};
    }
    const matches = nodes(value).filter(matchesRule); assert.equal(matches.length, 1, 'One visible native sleeping percentage value');
    const node = matches[0]; assert.match(node.value, /^\d+$/); const number = Number(node.value);
    assert(Number.isSafeInteger(number));
    return {value: number, nodeId: node.nodeId, label: node.label, translationKey: node.translationKey,
      ruleId: rule.command, screenClass: value.screenClass, screenIdentity: value.screenIdentity, ...(search ? {search} : {})};
  } catch (error) {
    throw Object.assign(new Error('Native sleeping rule USER lookup failed: ' + error.message + '; nodes=' + JSON.stringify(summary()), {cause: error}),
      {userRuleLookup: summary()});
  }
}

export function legacySleepingRuleDraw(view, marker) {
  assert.equal(view.screenClass, ''); assert.equal(view.overlayIdentity, 0);
  const rows = validateLateTextDraw(view, 'chat_background').filter(node => node.textPhase === 'drawn');
  const markers = rows.filter(node => node.label === marker); assert.equal(markers.length, 1, 'One actual current rule-query marker');
  const anchor = markers[0], after = rows.filter(node => node.y >= anchor.y + anchor.height)
    .sort((left, right) => left.y - right.y);
  const matches = [];
  // A native wrapped message occupies adjacent lines; only at most three fully drawn lines
  // below this unique marker can form this exact finite vanilla query response.
  for (let start = 0; start < after.length; start++) for (let length = 1; length <= 3 && start + length <= after.length; length++) {
    const lines = after.slice(start, start + length);
    if (lines.some((node, index) => node.x !== lines[0].x || index > 0
      && (node.y < lines[index - 1].y + lines[index - 1].height || node.y - lines[index - 1].y > lines[index - 1].height + 4))) continue;
    const text = lines.map(node => node.label).join(' ').replace(/\s+/g, ' ').trim();
    const match = /^Gamerule playersSleepingPercentage is currently set to: (\d+)$/.exec(text);
    if (match) {const value = Number(match[1]); assert(Number.isSafeInteger(value)); matches.push({value, nodes: lines, label: text});}
  }
  assert.equal(matches.length, 1, 'One newly drawn native sleeping-rule response after the marker');
  return {...matches[0], markerNode: anchor};
}

export async function readLegacySleepingRule({target, tree, command, poll}) {
  assert(TARGETS.includes(target) && target.startsWith('1.'));
  const query = 'gamerule ' + sleepingRule(target).command, marker = 'UI rule ' + randomUUID().replaceAll('-', '').slice(0, 12);
  const before = await tree(); assert.equal(before.dataCategory, 'USER'); assert.equal(before.screenClass, '');
  assert.equal(before.overlayIdentity, 0); assert(Number.isSafeInteger(before.clientTick));
  const markerSubmission = await command('tellraw @s ' + JSON.stringify({text: marker}));
  await poll(tree, view => {
    try {assert.equal(view.screenClass, ''); return view.clientTick > before.clientTick
      && validateLateTextDraw(view, 'chat_background').some(node => node.textPhase === 'drawn' && node.label === marker);} catch {return false;}
  }, 'Actual native rule-query marker is drawn');
  const querySubmission = await command(query); let last;
  const view = await poll(tree, view => {
    last = view; try {return view.clientTick > before.clientTick && legacySleepingRuleDraw(view, marker).nodes.length > 0;} catch {return false;}
  }, 'Actual native sleeping-rule query follows its unique marker').catch(error => {
    const summary = {screenClass: last?.screenClass, clientTick: last?.clientTick, dataCategory: last?.dataCategory,
      rows: (last?.dataCategory === 'USER' ? last.presentation?.children ?? [] : []).filter(node => node.renderPhase === 'chat_background')
        .slice(0, 12).map(node => ({label: String(node.label ?? '').slice(0, 180), x: node.x, y: node.y,
          textPhase: node.textPhase, labelSource: node.labelSource}))};
    throw Object.assign(new Error('Native sleeping rule chat read failed: ' + error.message + '; nodes=' + JSON.stringify(summary), {cause: error}), {userRuleLookup: summary});
  });
  const actual = legacySleepingRuleDraw(view, marker);
  return {value: actual.value, ruleId: sleepingRule(target).command, source: 'actual_native_chat_draw', marker,
    markerNode: actual.markerNode, queryNodes: actual.nodes, label: actual.label, sourceTick: view.clientTick,
    screenClass: view.screenClass, queryCommand: query, markerSubmission, querySubmission,
    commandSubmissionIsNotReadback: true};
}

export function nativeSaddleSlots(value, nodes) {
  assert(value.screenClass.endsWith('NautilusInventoryScreen'));
  assert.equal(value.dataCategory, 'USER'); assert.equal(value.treeTruncated, false);
  const slots = nodes(value).filter(node => node.role === 'slot');
  assert.equal(new Set(slots.map(node => node.slot)).size, slots.length, 'Semantic menu positions must be unique');
  const source = slots.filter(node => node.slotOwner === 'player_inventory' && node.item === 'minecraft:saddle' && node.count === 1);
  const destination = slots.filter(node => node.slotOwner === 'menu' && node.slot === 0 && node.count === 0);
  assert.equal(source.length, 1, 'Exactly one owned fixture saddle must be visible in the player inventory');
  assert.equal(destination.length, 1, 'The actual native Nautilus menu adds its saddle equipment slot first');
  for (const node of [...source, ...destination]) assert(node.visible && node.active && node.actions.includes('click'));
  return {source: source[0], destination: destination[0], slotCount: slots.length};
}

// Arrange is confined to the outer runner's restored disposable world. Native USER
// world gestures and semantic UI actions, not direct callbacks, perform every Act.
export async function specialWorldMenuActions({api, target, command, key, tree, wait, nodes, ui, click, enterWorld, check, samples}, services) {
  assert(TARGETS.includes(target));
  const now = services?.now ?? (() => performance.now());
  const sleep = services?.sleep ?? (milliseconds => new Promise(done => setTimeout(done, milliseconds)));
  const started = now(), deadline = started + 180000;
  const tag = 'ui_menu_' + randomUUID().replaceAll('-', '').slice(0, 24);
  let lastPipeline = -Infinity;
  async function poll(read, predicate, label, milliseconds = 5000) {
    const end = Math.min(deadline, now() + milliseconds); let value;
    do {value = await read(); if (predicate(value)) return value; await sleep(75);} while (now() < end);
    assert.fail(label + ': ' + JSON.stringify(value));
  }
  async function clean() {
    const value = await api('GET', '/v0/input/state');
    assert.equal(value.pressedKeyCount, 0); assert.equal(value.pressedButtonCount, 0);
    assert.equal(value.inputCleanupPending, false); assert.equal(value.inputCleanupFailed, false); return value;
  }
  async function page(name) {
    const value = await wait(value => value.screenClass.endsWith(name), 'actual native ' + name);
    assert.equal(value.dataCategory, 'USER'); assert.equal(value.treeTruncated, false); return value;
  }
  async function button(translationKey) {
    const value = await tree(), matches = nodes(value).filter(node => node.translationKey === translationKey && node.visible && node.active);
    assert.equal(matches.length, 1, translationKey); return ui({nodeId: matches[0].nodeId});
  }
  async function gameplay() {await wait(value => !value.screenClass, 'native gameplay'); await clean();}
  async function pipeline(steps) {
    const interval = 550 - (now() - lastPipeline); if (interval > 0) await sleep(interval);
    assert(now() < deadline, 'Special world menus exceeded 180 seconds'); lastPipeline = now();
    const accepted = await api('POST', '/v0/pipelines', {steps, timeoutMs: 12000});
    const result = await api('POST', '/v0/operations/' + accepted.operationId + '/wait', {timeoutMs: 14000});
    assert.equal(result.state, 'completed', JSON.stringify(result));
    const worldSteps = steps.filter(step => step.type === 'world.action');
    const receipts = (result.result?.steps ?? []).filter(step => step.stepType === 'world.action'); assert.equal(receipts.length, worldSteps.length);
    for (const receipt of receipts) {
      assert.equal(receipt.result.dataCategory, 'USER'); assert.equal(receipt.result.evidenceAuthority, 'PLAYTEST');
      assert.equal(receipt.result.directMutationUsed, false); assert.equal(receipt.result.effectConfirmed, false);
    }
    return result;
  }
  const point = (x, y, z) => api('GET', '/v0/world/block?' + new URLSearchParams({x, y, z, category: 'USER'}));
  async function known(x, y, z, block) {
    return poll(() => point(x, y, z), value => value.available === true && value.block === block && typeof value.targetRef === 'string', 'Client fixture block not received');
  }
  async function prepare(receipts) {
    await enterWorld(); await gameplay();
    const original = await api('GET', '/v0/player?category=USER'); assert.equal(original.available, true);
    assert.equal(original.gameMode, 'creative', 'This disposable UI fixture must already be Creative');
    const selected = nodes((await tree()).presentation ?? {}).filter(node => node.selected && node.controlKey?.startsWith('hud.hotbar.'));
    assert.equal(selected.length, 1, 'Actual USER selected-hotbar slot');
    assert.equal(selected[0].count, 0, 'Do not replace unknown nonempty inventory contents');
    for (const text of ['fill -3 79 -3 3 79 5 minecraft:stone', 'fill -2 80 -1 2 83 4 minecraft:air', 'tp @s 0.5 80 0.5 0 0']) receipts.push(await command(text));
    await poll(() => api('GET', '/v0/player?category=USER'), value => value.available && Math.abs(value.x - .5) < .05
      && Math.abs(value.y - 80) < .05 && Math.abs(value.z - .5) < .05, 'Native fixture player position');
    return original;
  }
  async function selectedItem(item, count) {
    return wait(value => !value.screenClass && nodes(value.presentation ?? {}).some(node => node.selected && node.item === item && node.count === count), 'Actual held ' + item);
  }
  async function readSleepingRule() {
    if (target.startsWith('1.')) return readLegacySleepingRule({target, tree, command, poll});
    await key(256); await page('PauseScreen');
    if (target === '26.1.2-neoforge') {await click('menu.options', 'Options...'); await page('OptionsScreen');}
    await click('options.worldOptions.button', 'World Options...'); await page('WorldOptionsScreen');
    await click('editGamerule.inGame.button', 'Game Rules...'); await page('InWorldGameRulesScreen');
    try {return await readVisibleSleepingRule({target, tree, nodes, ui, poll});}
    finally {
      await button('gui.cancel'); await page('WorldOptionsScreen');
      if (target === '26.1.2-neoforge') {await button('gui.done'); await page('OptionsScreen'); await button('gui.done');}
      else await button('gui.cancel');
      await page('PauseScreen'); await key(256); await gameplay();
    }
  }
  async function closeOwnedScreen() {
    const value = await tree();
    if (value.screenClass.endsWith('InBedChatScreen')) {
      const field = nodes(value).find(node => node.role === 'text_field' && node.visible && node.active);
      if (field?.value) await ui({nodeId: field.nodeId}, 'type_text', {text: ''});
      await button('multiplayer.stopSleeping');
    } else if (value.screenClass.endsWith('NautilusInventoryScreen')) await key(256);
    await gameplay();
  }
  function record(caseName, value, extra) {
    const sample = {case: caseName, nativeScreens: [value.screenClass], screenClass: value.screenClass, screenIdentity: value.screenIdentity,
      screenRevision: value.screenRevision, semanticTargets: true, handwrittenCoordinates: false, directCallbackUsed: false,
      arrangeAuthority: 'DIAGNOSTIC_current_player_commands_owned_copy_only', actAuthority: 'PLAYTEST',
      businessOperationCoverage: 'native_ui_minimal_safe_branch', ...extra};
    samples.push(sample); return sample;
  }
  function bedHotbar(current) {
    assert.equal(current.dataCategory, 'USER'); assert.equal(current.screenClass, ''); assert.equal(current.overlayIdentity, 0);
    const slots = nodes(current.presentation ?? {}).filter(node => /^hud\.hotbar\.[0-8]$/.test(node.controlKey ?? ''));
    assert.equal(slots.length, 9); assert.equal(new Set(slots.map(node => node.controlKey)).size, 9);
    assert.equal(slots.filter(node => node.selected).length, 1); return slots;
  }
  const bedCount = slots => slots.filter(node => node.item === 'minecraft:red_bed').reduce((sum, node) => sum + node.count, 0);
  const removeBedDrops = () => command('kill @e[type=minecraft:item,x=-1,y=79,z=1,dx=3,dy=4,dz=4,nbt={Item:{id:"minecraft:red_bed"}}]');
  const bedDrops = async () => {
    const value = await api('GET', '/v0/world/entities?category=USER&radius=8&limit=128');
    assert.equal(value.dataCategory, 'USER'); assert.equal(value.authority, 'CLIENT_KNOWN');
    assert.equal(value.chunkLoadRequested, false); assert.equal(value.entitiesTruncated, false); assert(Array.isArray(value.entities));
    const items = value.entities.filter(entity => entity.type === 'minecraft:item'
      && entity.x >= -1 && entity.x <= 3 && entity.y >= 79 && entity.y <= 84 && entity.z >= 1 && entity.z <= 6);
    for (const item of items) {assert.equal(typeof item.itemId, 'string'); assert(Number.isSafeInteger(item.itemCount) && item.itemCount > 0);}
    return {dataCategory: value.dataCategory, authority: value.authority, chunkLoadRequested: value.chunkLoadRequested,
      entitiesTruncated: value.entitiesTruncated, remainingBedDrops: items.filter(item => item.itemId === 'minecraft:red_bed')
        .map(({itemId, itemCount, x, y, z}) => ({itemId, itemCount, x, y, z}))};
  };
  const noBedDrops = label => poll(bedDrops, value => value.remainingBedDrops.length === 0, label);
  await check('UI special world menus: actual bed use opens InBed Chat, edits only a draft and wakes through the native button', async () => {
    const arrange = []; await prepare(arrange); const originalRule = await readSleepingRule();
    // Prior diagnostics restore their item history only with the outer immutable copy.
    // Empty this owned bed area before measuring inventory or adding the current fixture.
    const priorDropReadback = await bedDrops(), removedPriorDrops = await removeBedDrops();
    const preparationDropReadback = await noBedDrops('Prior diagnostic bed drops leave the owned area');
    const initialHotbar = bedHotbar(await tree()), initialBedCount = bedCount(initialHotbar);
    let attemptedRule = false, arrangedItem = false, primary, proof;
    try {
      attemptedRule = true; arrange.push(await command('gamerule ' + sleepingRule(target).command + ' 101'));
      assert.equal((await readSleepingRule()).value, 101, 'Native rule readback must prevent automatic overnight wake-up');
      arrange.push(await command('item replace entity @s weapon.mainhand with minecraft:red_bed 1')); arrangedItem = true;
      await selectedItem('minecraft:red_bed', 1); const support = await known(0, 79, 2, 'minecraft:stone');
      const placed = await pipeline([{type: 'world.action', category: 'USER', targetRef: support.targetRef, action: 'place', face: 'up', hit: {u: .5, v: .5}, expectedDestinationBlockId: 'minecraft:air'}]);
      const foot = await known(0, 80, 2, 'minecraft:red_bed'); await known(0, 80, 3, 'minecraft:red_bed');
      arrange.push(await command('item replace entity @s weapon.mainhand with minecraft:air')); arrangedItem = false;
      await selectedItem('minecraft:air', 0); arrange.push(await command('time set night')); arrange.push(await command('weather clear'));
      const before = await clean(); const entered = await pipeline([{type: 'world.action', category: 'USER', targetRef: (await point(0, 80, 2)).targetRef, action: 'use'}]);
      const value = await page('InBedChatScreen'), fields = nodes(value).filter(node => node.role === 'text_field' && node.visible && node.active && node.actions.includes('type_text'));
      assert.equal(fields.length, 1); const field = fields[0], draft = 'UI sleeping draft - not sent';
      await ui({nodeId: field.nodeId}, 'type_text', {text: draft});
      const drafted = await wait(value => value.screenClass.endsWith('InBedChatScreen') && nodes(value).some(node => node.nodeId === field.nodeId && node.value === draft), 'Native sleeping chat draft');
      await ui({nodeId: field.nodeId}, 'type_text', {text: ''}); await button('multiplayer.stopSleeping'); await gameplay();
      const after = await clean(); assert(after.inputDispatchSequence > before.inputDispatchSequence);
      proof = {arrange, originalRule, priorDropReadback, removedPriorDrops, preparationDropReadback, placed, foot, entered, before, after, draftedScreenRevision: drafted.screenRevision, chatSubmitted: false,
        semanticActions: ['world.place', 'world.use', 'chat.type_text', 'chat.clear', 'multiplayer.stopSleeping'], automaticWakePreventedByRule: 101};
      proof = record('special_world_in_bed_chat', value, proof);
    } catch (error) {primary = error; throw error;}
    finally {
      try {
        await closeOwnedScreen();
        if (arrangedItem) await command('item replace entity @s weapon.mainhand with minecraft:air');
        if (attemptedRule) {
          await command('gamerule ' + sleepingRule(target).command + ' ' + originalRule.value);
          const restored = await readSleepingRule(); assert.equal(restored.value, originalRule.value); if (proof) proof.restoredRule = restored;
        }
        await command('time set day');
        const beforeRemoval = await tree(); assert.equal(beforeRemoval.dataCategory, 'USER');
        assert(Number.isSafeInteger(beforeRemoval.clientTick));
        await command('fill 0 80 2 0 80 3 minecraft:air');
        // Removing one native bed half can drop its companion even for a Creative command source.
        const removedDrop = await removeBedDrops();
        const hotbar = bedHotbar;
        const observed = await wait(current => !current.screenClass && current.clientTick > beforeRemoval.clientTick,
          'Native bed-removal frame reaches USER hotbar');
        const dropReadback = await noBedDrops('Owned bed item entity disappears from bounded USER world model');
        const observedSlots = hotbar(observed), addedBeds = bedCount(observedSlots) - initialBedCount;
        assert(addedBeds >= 0 && addedBeds <= 1, 'Do not clear unknown extra bed stacks: ' + JSON.stringify({initialBedCount,
          initialHotbar: initialHotbar.map(({controlKey, item, count, selected}) => ({controlKey, item, count, selected})),
          observedHotbar: observedSlots.map(({controlKey, item, count, selected}) => ({controlKey, item, count, selected})),
          beforeRemovalTick: beforeRemoval.clientTick, observedTick: observed.clientTick, addedBeds}));
        const selected = observedSlots.find(node => node.selected);
        assert(selected.count === 0 || selected.item === 'minecraft:red_bed' && selected.count === 1,
          'Do not replace an unrelated player hand during bed cleanup');
        let removedPickup;
        if (addedBeds) removedPickup = await command('clear @s minecraft:red_bed 1');
        const cleared = current => !current.screenClass && bedCount(hotbar(current)) === initialBedCount
          && hotbar(current).some(node => node.selected && node.item === 'minecraft:air' && node.count === 0);
        const afterRemoval = await wait(current => current.clientTick > observed.clientTick && cleared(current), 'Owned bed pickup removed from actual USER hotbar');
        const stableRemoval = await wait(current => current.clientTick > afterRemoval.clientTick && cleared(current), 'Owned bed cleanup remains empty on a later native frame');
        const compact = slots => slots.map(node => ({controlKey: node.controlKey, item: node.item, count: node.count, selected: node.selected}));
        assert.deepEqual(compact(hotbar(stableRemoval)), compact(initialHotbar), 'Bed cleanup must restore every initial hotbar slot');
        if (proof) proof.bedCleanup = {item: 'minecraft:red_bed', pickupLimit: 1, initialBedCount, addedBeds,
          initialHotbar: compact(initialHotbar), removedDrop, dropReadback,
          ...(removedPickup ? {removedPickup} : {}), beforeHotbar: compact(observedSlots), afterHotbar: compact(hotbar(stableRemoval)),
          observedTick: observed.clientTick, afterTick: afterRemoval.clientTick, stableTick: stableRemoval.clientTick,
          source: 'USER_native_hotbar_readback', unrelatedItemsCleared: false};
        await clean();
      } catch (cleanup) {if (primary) throw new AggregateError([primary, cleanup], 'Bed scenario and cleanup failed'); throw cleanup;}
    }
  });
  await check('UI special world menus: actual tamed Nautilus inventory equips and returns one saddle through USER slots', async () => {
    if (target.startsWith('1.')) {samples.push({case: 'special_world_nautilus_unavailable', target, disposition: 'NATIVE_CLASS_ABSENT', operationCoverage: false}); return;}
    const arrange = [], slotActions = [], playerPositions = []; await prepare(arrange); let spawned = false, equipped = false, primary, lastSlotFrame;
    const standing = {x: .5, y: 82, z: .5};
    const atStanding = player => player.available === true && ['x', 'y', 'z'].every(axis => Number.isFinite(player[axis])
      && Math.abs(player[axis] - standing[axis]) < .125);
    const stablePosition = async (stage, waitForTeleport = false) => {
      const read = async () => {const value = await api('GET', '/v0/player?category=USER'); assert.equal(value.dataCategory, 'USER'); return value;};
      const player = waitForTeleport ? await poll(read, atStanding, 'Native dry pool-edge standing position') : await read();
      playerPositions.push({stage, dataCategory: player.dataCategory, available: player.available, x: player.x, y: player.y, z: player.z});
      assert(atStanding(player), 'The player left the isolated dry Nautilus standing position: ' + JSON.stringify(playerPositions.at(-1)));
    };
    const rememberSlotFrame = current => {
      assert.equal(current.dataCategory, 'USER');
      lastSlotFrame = {dataCategory: current.dataCategory, screenClass: current.screenClass, screenIdentity: current.screenIdentity,
        screenRevision: current.screenRevision, menuRevision: current.menuRevision, clientTick: current.clientTick,
        treeTruncated: current.treeTruncated, overlayIdentity: current.overlayIdentity,
        carriedStack: current.carriedStack ? {id: current.carriedStack.id, count: current.carriedStack.count, empty: current.carriedStack.empty} : undefined,
        slots: nodes(current).filter(node => node.role === 'slot').slice(0, 64).map(node => ({nodeId: node.nodeId,
          slot: node.slot, slotOwner: node.slotOwner, item: node.item, count: node.count, visible: node.visible, active: node.active}))};
    };
    try {
      const actor = await api('GET', '/v0/player?category=DEBUG'); assert.equal(actor.available, true);
      assert.match(actor.uuid, /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i);
      const uuidHex = actor.uuid.replaceAll('-', '');
      const owner = [0, 8, 16, 24].map(offset => Number.parseInt(uuidHex.slice(offset, offset + 8), 16) | 0);
      arrange.push(await command('fill -2 80 0 2 81 4 minecraft:stone'));
      arrange.push(await command('fill -1 80 1 1 81 3 minecraft:water'));
      // The dry north rim is above both water layers; its top is below the native entity-interaction ray.
      arrange.push(await command('tp @s 0.5 82 0.5 0 0'));
      await stablePosition('after_arrange', true);
      arrange.push(await command(`summon minecraft:nautilus 0.5 80 2.5 {Owner:[I;${owner.join(',')}],NoAI:1b,NoGravity:1b,Silent:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:["${tag}"]}`)); spawned = true;
      const entity = await poll(async () => {
        const result = await api('GET', '/v0/world/entities?category=USER&radius=8&limit=128'); assert.equal(result.dataCategory, 'USER');
        assert.equal(result.authority, 'CLIENT_KNOWN'); assert.equal(result.chunkLoadRequested, false); assert.equal(result.entitiesTruncated, false);
        return result.entities.filter(entity => entity.type === 'minecraft:nautilus' && Math.hypot(entity.x - .5, entity.y - 80, entity.z - 2.5) < .5);
      }, value => value.length === 1 && value[0].tamed === true && typeof value[0].targetRef === 'string', 'Actual client-synchronized tamed Nautilus');
      assert(!('ownerUUID' in entity[0]) && !('uuid' in entity[0]), 'DEBUG Arrange must not expand the USER entity result');
      arrange.push(await command('item replace entity @s weapon.mainhand with minecraft:saddle 1')); equipped = true; await selectedItem('minecraft:saddle', 1);
      const before = await clean(); const entered = await pipeline([{type: 'key', key: 340, action: 1}, {type: 'delay', durationMs: 160},
        {type: 'world.action', category: 'USER', targetRef: entity[0].targetRef, action: 'use'}]);
      const value = await page('NautilusInventoryScreen'); await clean();
      rememberSlotFrame(value);
      await stablePosition('menu_opened');
      const {source, destination, slotCount} = nativeSaddleSlots(value, nodes);
      assert(Number.isSafeInteger(value.screenIdentity) && value.screenIdentity > 0);
      const anchor = node => {
        assert(Number.isSafeInteger(node.slot) && node.slot >= 0);
        return {slot: node.slot, slotOwner: node.slotOwner};
      };
      const sourceAnchor = anchor(source), destinationAnchor = anchor(destination);
      const currentSlots = current => {
        assert.equal(current.dataCategory, 'USER');
        rememberSlotFrame(current);
        const context = JSON.stringify({screenClass: current.screenClass, screenIdentity: current.screenIdentity,
          treeTruncated: current.treeTruncated, overlayIdentity: current.overlayIdentity, carriedStack: current.carriedStack,
          completedSlotActions: slotActions.map(entry => entry.action)});
        assert.equal(current.screenClass, value.screenClass, 'Native Nautilus Screen changed: ' + context);
        assert.equal(current.screenIdentity, value.screenIdentity, 'The same native Nautilus Screen must own every slot action: ' + context);
        assert.equal(current.treeTruncated, false, 'Native Nautilus tree shape changed: ' + context); assert.equal(current.overlayIdentity, 0);
        const find = expected => {
          const matches = nodes(current).filter(node => node.role === 'slot'
            && node.slot === expected.slot && node.slotOwner === expected.slotOwner);
          assert.equal(matches.length, 1, 'One current semantic Nautilus slot must match its complete USER anchor');
          const node = matches[0]; assert(node.visible && node.active && node.actions?.includes('click'));
          assert.equal(typeof node.nodeId, 'string'); return node;
        };
        return {source: find(sourceAnchor), destination: find(destinationAnchor)};
      };
      const carriedSaddle = current => current.carriedStack?.id === 'minecraft:saddle' && current.carriedStack.count === 1;
      async function slotClick(name, selection, expectedCount, expectCarried) {
        const current = await tree(), node = currentSlots(current)[selection];
        assert.equal(node.count, expectedCount); if (expectedCount) assert.equal(node.item, 'minecraft:saddle');
        if (expectCarried) assert(carriedSaddle(current)); else assert.equal(current.carriedStack?.empty, true);
        const response = await ui({nodeId: node.nodeId});
        slotActions.push({action: name, anchor: selection === 'source' ? sourceAnchor : destinationAnchor,
          nodeId: node.nodeId, screenIdentity: current.screenIdentity, screenRevision: current.screenRevision, response});
      }
      await slotClick('pickup', 'source', 1, false);
      await wait(current => {const slots = currentSlots(current); return carriedSaddle(current)
        && slots.source.count === 0 && slots.destination.count === 0;}, 'Native saddle carried');
      await slotClick('equip', 'destination', 0, true);
      const installed = await wait(current => {const slots = currentSlots(current); return current.carriedStack?.empty === true
        && slots.source.count === 0 && slots.destination.item === 'minecraft:saddle' && slots.destination.count === 1;}, 'Native saddle equipment readback');
      await stablePosition('saddle_equipped');
      await slotClick('pickup_equipment', 'destination', 1, false);
      await wait(current => {const slots = currentSlots(current); return carriedSaddle(current)
        && slots.source.count === 0 && slots.destination.count === 0;}, 'Native saddle picked back up');
      await slotClick('return', 'source', 0, true);
      const returned = await wait(current => {const slots = currentSlots(current); return current.carriedStack?.empty === true
        && slots.source.item === 'minecraft:saddle' && slots.source.count === 1 && slots.destination.count === 0;}, 'Native saddle returned to exact player slot');
      await stablePosition('saddle_returned');
      await key(256); await gameplay(); const after = await clean(); assert(after.inputDispatchSequence > before.inputDispatchSequence);
      record('special_world_nautilus_inventory', value, {arrange, entity: entity[0], entered, source, destination, slotCount, before, after,
        installedMenuRevision: installed.menuRevision, returnedMenuRevision: returned.menuRevision, ownerUsedOnlyForDiagnosticArrange: true,
        sourceAnchor, destinationAnchor, slotActions, playerPositions,
        saddleEquippedAndReturned: true, semanticActions: ['world.sneak_use', 'slot.pickup', 'slot.equip', 'slot.pickup', 'slot.return'], nativeTamingRandomnessTested: false});
    } catch (error) {
      primary = error;
      const sample = {case: 'special_world_nautilus_failure', target, category: 'USER', verified: false,
        operationCoverage: false, collectedBeforeCleanup: true, primaryError: {name: error.name, message: error.message, code: error.code},
        lastSlotFrame, completedSlotActions: slotActions.map(entry => ({...entry})), playerPositions, diagnostics: {}};
      const diagnosticFailures = [];
      for (const [name, path] of [['player', '/v0/player?category=USER'],
        ['entities', '/v0/world/entities?category=USER&radius=8&limit=128'], ['input', '/v0/input/state']]) {
        try {
          const value = await api('GET', path);
          if (name === 'input') sample.diagnostics.input = {pressedKeyCount: value.pressedKeyCount, pressedButtonCount: value.pressedButtonCount,
            inputCleanupPending: value.inputCleanupPending, inputCleanupFailed: value.inputCleanupFailed, inputDispatchSequence: value.inputDispatchSequence};
          else {
            assert.equal(value.dataCategory, 'USER');
            if (name === 'player') sample.diagnostics.player = {dataCategory: value.dataCategory, available: value.available,
              dimension: value.dimension, gameMode: value.gameMode, x: value.x, y: value.y, z: value.z};
            else {
              assert(Array.isArray(value.entities) && value.entities.length <= 128);
              sample.diagnostics.entities = {dataCategory: value.dataCategory, authority: value.authority,
                chunkLoadRequested: value.chunkLoadRequested, entitiesTruncated: value.entitiesTruncated,
                entities: value.entities.map(entity => ({type: entity.type, targetRef: entity.targetRef,
                  x: entity.x, y: entity.y, z: entity.z, tamed: entity.tamed}))};
            }
          }
        } catch (diagnosticFailure) {
          diagnosticFailures.push({part: name, name: diagnosticFailure.name, message: diagnosticFailure.message});
        }
      }
      if (diagnosticFailures.length) sample.diagnosticFailure = diagnosticFailures;
      samples.push(sample);
      throw error;
    }
    finally {
      try {
        await closeOwnedScreen();
        if (equipped) await command('item replace entity @s weapon.mainhand with minecraft:air');
        if (spawned) await command(`kill @e[type=minecraft:nautilus,tag=${tag}]`);
        await command('fill -2 80 0 2 81 4 minecraft:air'); await clean();
      } catch (cleanup) {if (primary) throw new AggregateError([primary, cleanup], 'Nautilus scenario and cleanup failed'); throw cleanup;}
    }
  });
  await check('UI special world menus: native social entry exposes local navigation or its exact singleplayer limitation', async () => {
    await enterWorld(); await gameplay(); const before = await clean(); await key(256); const pause = await page('PauseScreen');
    const keys = ['menu.playerReporting', 'menu.socialInteractions', 'gui.socialInteractions.title'];
    const entries = nodes(pause).filter(node => keys.includes(node.translationKey) && node.visible);
    const absent = (target.startsWith('1.') || target === '26.1.2-neoforge') && entries.length === 0;
    let absentEntryProof;
    if (absent) {
      const alternatives = nodes(pause).filter(node => node.translationKey === 'menu.shareToLan' && node.visible && node.active && node.actions?.includes('click'));
      assert.equal(alternatives.length, 1, 'The native unpublished singleplayer branch exposes Open to LAN, not reporting');
      absentEntryProof = {dataCategory: pause.dataCategory, screenClass: pause.screenClass, screenIdentity: pause.screenIdentity,
        screenRevision: pause.screenRevision, socialEntries: entries.length, alternateEntry: alternatives[0]};
    } else assert.equal(entries.length, 1, 'Actual native social/reporting entry');
    const entry = entries[0] ?? null; let disabledEntryProof;
    if (absent || entry.active === false) {
      if (entry) {
        const beforeDenial = await clean(), beforeTree = await tree();
        await assert.rejects(ui({nodeId: entry.nodeId}), error => error.code === 'UI_NODE_NOT_ACTIONABLE');
        const afterDenial = await clean(), afterTree = await tree();
        // Failed active pipelines must release owned input. Even an empty native cleanup
        // advances this sequence once; it does not mean a mouse move or click was sent.
        assert.equal(afterDenial.inputDispatchSequence, beforeDenial.inputDispatchSequence + 1);
        for (const field of ['x', 'y']) {
          assert(Number.isFinite(beforeDenial.agentPointer?.[field]));
          assert.equal(afterDenial.agentPointer?.[field], beforeDenial.agentPointer[field]);
        }
        for (const field of ['screenIdentity', 'screenRevision']) assert.equal(afterTree[field], beforeTree[field]);
        disabledEntryProof = {before: beforeDenial, after: afterDenial, beforeScreenIdentity: beforeTree.screenIdentity,
          afterScreenIdentity: afterTree.screenIdentity, beforeScreenRevision: beforeTree.screenRevision, afterScreenRevision: afterTree.screenRevision,
          sequenceIncrementMeaning: 'mandatory_pipeline_failed_release_all_not_native_mouse_input'};
      }
      await key(256); await gameplay();
      // Vanilla's P binding separately refuses the singleplayer context with a visible actionbar.
      await key(80); const refusal = await poll(tree, value => value.screenClass.endsWith('SocialInteractionsScreen')
        || nodes(value.presentation ?? {}).some(node => node.controlKey === 'hud.actionbar' && /Social Interactions.*(only|multiplayer)|only.*multiplayer/i.test(node.label ?? node.text ?? '')),
      'Native Social key must open the page or expose its actual singleplayer rejection', 3000);
      if (!refusal.screenClass.endsWith('SocialInteractionsScreen')) {
        await clean(); samples.push({case: 'special_world_social_environment_limit', target, entry, disabledEntryProof, absentEntryProof, pauseScreenClass: pause.screenClass,
          refusalPresentation: refusal.presentation, before, after: await clean(), disposition: absent ? 'NATIVE_SINGLEPLAYER_ENTRY_ABSENT' : 'NATIVE_SINGLEPLAYER_ENTRY_DISABLED',
          screenOperationCoverage: false, externalServiceAcceptance: false, fakeAccountUsed: false, screenConstructedByProbe: false}); return;
      }
    } else await ui({nodeId: entry.nodeId});
    const value = await page('SocialInteractionsScreen'), visits = [];
    for (const translationKey of ['gui.socialInteractions.tab_hidden', 'gui.socialInteractions.tab_blocked', 'gui.socialInteractions.tab_all']) {
      const current = await tree(), candidates = nodes(current).filter(node => node.translationKey === translationKey && node.active && node.visible);
      assert.equal(candidates.length, 1); await ui({nodeId: candidates[0].nodeId}); visits.push({translationKey, screenRevision: (await page('SocialInteractionsScreen')).screenRevision});
    }
    const search = nodes(await tree()).filter(node => node.role === 'text_field' && node.visible && node.active && node.actions.includes('type_text')); assert.equal(search.length, 1);
    await ui({nodeId: search[0].nodeId}, 'type_text', {text: 'ui fixture no player'});
    await wait(value => nodes(value).some(node => node.nodeId === search[0].nodeId && node.value === 'ui fixture no player'), 'Native Social search draft');
    await ui({nodeId: search[0].nodeId}, 'type_text', {text: ''}); await button('gui.done');
    const back = await tree(); if (back.screenClass.endsWith('PauseScreen')) await key(256); await gameplay();
    const after = await clean(); assert(after.inputDispatchSequence > before.inputDispatchSequence);
    record('special_world_social_navigation', value, {entry, disabledEntryProof, absentEntryProof, visits, before, after, semanticActions: [absent ? 'native.social_key' : 'native.entry', 'tab.hidden', 'tab.blocked', 'tab.all', 'search.type_text', 'search.clear', 'gui.done'],
      localNavigationOnly: true, playerHiddenOrBlocked: false, reportSubmitted: false, externalServiceAcceptance: false, fakeAccountUsed: false});
  });
}
