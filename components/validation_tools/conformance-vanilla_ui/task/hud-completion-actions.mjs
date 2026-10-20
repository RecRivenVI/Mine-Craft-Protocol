import assert from 'node:assert/strict';

export const effectIconNodes = view => view.presentation.children.filter(node =>
  node.role === 'status_icon' && node.controlKey?.startsWith('hud.effect.'));

const prefix = 'hud.item.';
const itemNodes = view => (view.presentation?.children ?? []).filter(node =>
  node.controlKey === 'hud.selected_item_name' || node.controlKey?.startsWith(prefix));

export function validateHudItemDraw(view, kind) {
  assert.equal(view.dataCategory, 'USER');
  assert.equal(view.screenClass, ''); assert.equal(view.overlayIdentity, 0);
  const children = view.presentation.children;
  const selected = children.find(node => node.selected && node.controlKey?.startsWith('hud.hotbar.'));
  assert(selected, 'The real selected hotbar item must be presented');
  const bar = children.find(node => node.controlKey === `${prefix}${kind}.${selected.x}.${selected.y}`);
  assert(bar, `No actual native ${kind} draw for the selected item`);
  assert.equal(bar.visible, true); assert.equal(bar.active, false); assert.deepEqual(bar.actions, []);
  assert.equal(bar.iconSource, 'last_completed_native_hud_draw'); assert.equal(bar.role, 'progress_bar');
  assert.equal(bar.boundsAvailable, true); assert(bar.width > 0 && bar.height > 0);
  assert.equal(bar.valueMinimum, 0); assert(bar.valueMaximum > 0);
  assert(Number.isInteger(bar.value) && bar.value >= 0 && bar.value <= bar.valueMaximum);
  assert.equal(bar.valueSemantics, kind === 'durability'
    ? 'drawn_fill_pixels_not_raw_item_state' : 'drawn_fill_pixels_not_remaining_ticks');
  if (kind === 'durability') {
    assert(bar.value > 0 && bar.value < bar.valueMaximum, 'The fixture must show an actually damaged item');
    const name = children.find(node => node.controlKey === 'hud.selected_item_name');
    assert(name?.label?.trim(), 'Native selected-item highlight text must be actually drawn');
    assert.equal(name.labelSource, 'last_completed_native_hud_draw');
    assert(name.opacity > 0 && name.opacity <= 1); assert.deepEqual(name.actions, []);
  } else {
    assert(bar.value > 0); assert(bar.opacity > 0 && bar.opacity <= 1);
    assert.equal(bar.value, bar.height); assert.equal(bar.valueMaximum, bar.width);
  }
  for (const node of itemNodes(view))
    for (const forbidden of ['damage', 'maxDamage', 'remainingTicks', 'cooldownTicks', 'nbt', 'components'])
      assert.equal(node[forbidden], undefined, `USER must not expose ${forbidden}`);
  return { selected, bar, ...(kind === 'durability' ? { name: children.find(node => node.controlKey === 'hud.selected_item_name') } : {}) };
}

export function validateHudContextDraw(view, family, { requireProgress = false, requireLevel = false, requireMarker = false } = {}) {
  assert.equal(view.dataCategory, 'USER'); assert.equal(view.screenClass, ''); assert.equal(view.overlayIdentity, 0);
  const rows = (view.presentation?.children ?? []).filter(node => node.controlKey?.startsWith(`hud.context.${family}.`));
  const backgroundRegion=`hud/${family}_bar_background`;
  const legacyAtlas=node=>node.textureId==='minecraft:textures/gui/icons.png'
    &&node.textureU===0&&node.atlasWidth===256&&node.atlasHeight===256
    &&node.spriteSemantics==='native_gui_icons_atlas_region'&&!node.resourceId
    &&({ 'hud/experience_bar_background':64,'hud/experience_bar_progress':69,
      'hud/jump_bar_background':84,'hud/jump_bar_progress':89,'hud/jump_bar_cooldown':74 })[node.spriteRegion]===node.textureV;
  assert(rows.some(node => node.resourceId?.endsWith(`/${family}_bar_background`)
    ||(legacyAtlas(node)&&node.spriteRegion===backgroundRegion)), `Actual ${family} background must be drawn`);
  if (requireProgress) assert(rows.some(node => node.role === 'progress_bar' && node.value > 0), `Actual ${family} progress sprite must be drawn`);
  if (requireLevel) assert(rows.some(node => node.controlKey === 'hud.context.experience.level' && node.label?.trim()), 'Native experience-level label must be drawn');
  if (requireMarker) assert(rows.some(node => node.resourceId && !node.resourceId.endsWith('/locator_bar_background')), 'Actual native locator marker must be drawn, not merely a background');
  for (const node of rows) {
    assert.equal(node.visible, true); assert.equal(node.active, false); assert.deepEqual(node.actions, []);
    assert.equal(node.iconSource, 'last_completed_native_hud_draw'); assert(node.opacity > 0 && node.opacity <= 1);
    if(node.textureId)assert(legacyAtlas(node),'Only a verified native legacy GUI atlas region is accepted');
    assert.equal(node.boundsAvailable, true); assert(node.x >= 0 && node.y >= 0 && node.width > 0 && node.height > 0);
    assert(node.x + node.width <= view.width && node.y + node.height <= view.height);
    if (node.role === 'progress_bar') {
      assert.equal(node.value, node.width); assert.equal(node.valueMinimum, 0);
      assert(Number.isInteger(node.valueMaximum) && node.valueMaximum >= node.value);
      assert.equal(node.valueSemantics, 'drawn_sprite_extent_not_raw_player_statistics');
    }
    for (const field of ['experiencePoints', 'experienceProgress', 'jumpCharge', 'jumpCooldown', 'waypointId', 'uuid', 'worldPosition', 'resourceVersion'])
      assert.equal(node[field], undefined, `USER must not expose ${field}`);
  }
  return rows;
}

// Only the caller-owned disposable UI fixture is changed. Commands arrange items; the Act is
// native hotbar selection/use/F1/Inventory input, never a private HUD setter or pixel assertion.
export async function hudItemCompletionActions({ target, api, command, wait, tree, key, use, check, samples }) {
  const clean = async () => {
    const value = await api('GET', '/v0/input/state');
    assert.equal(value.pressedKeyCount, 0); assert.equal(value.pressedButtonCount, 0);
    assert.equal(value.inputCleanupPending, false); assert.equal(value.inputCleanupFailed, false);
    return value;
  };
  await check('HUD: completed native selected-name and durability pixel draw', async () => {
    const item = target.startsWith('1.20.1-')
      ? 'minecraft:iron_sword{Damage:100}' : 'minecraft:iron_sword[minecraft:damage=100]';
    await command('clear @s');
    await command(`item replace entity @s hotbar.0 with ${item}`);
    await command('item replace entity @s hotbar.1 with minecraft:ender_pearl 16');
    await key(50); await key(49);
    const view = await wait(value => {
      try { validateHudItemDraw(value, 'durability'); return true; } catch { return false; }
    }, 'actual selected-name/durability draw');
    const evidence = validateHudItemDraw(view, 'durability');
    assert.equal(evidence.selected.item, 'minecraft:iron_sword');
    samples.push({ case: 'hud_item_draw', target, kind: 'durability', category: 'USER',
      ...evidence, input: await clean(), authority: 'native_completed_hud_draw', rawItemStateExposed: false });
    await key(290);
    const hidden = await wait(value => itemNodes(value).length === 0, 'F1 hides new item HUD presentation');
    assert.equal(itemNodes(hidden).length, 0);
    await key(290);
    await wait(value => itemNodes(value).some(node => node.controlKey.startsWith(prefix)), 'F1 restores rendered durability');
    await key(69);
    const inventory = await wait(value => /InventoryScreen$/.test(value.screenClass), 'Inventory occludes native HUD item presentation');
    assert.equal(itemNodes(inventory).length, 0);
    await key(256);
    await wait(value => !value.screenClass && itemNodes(value).some(node => node.controlKey.startsWith(prefix)), 'HUD resumes after Inventory');
    samples.push({ case: 'hud_item_occlusion', target, f1UserNodes: 0, inventoryUserNodes: 0, input: await clean() });
  });
  await check('HUD: native item-use cooldown draw and natural expiry', async () => {
    await key(50); await use();
    const view = await wait(value => {
      try { validateHudItemDraw(value, 'cooldown'); return true; } catch { return false; }
    }, 'real native item-use cooldown overlay');
    const evidence = validateHudItemDraw(view, 'cooldown');
    assert.equal(evidence.selected.item, 'minecraft:ender_pearl');
    samples.push({ case: 'hud_item_draw', target, kind: 'cooldown', category: 'USER',
      ...evidence, input: await clean(), authority: 'native_completed_hud_draw', rawItemStateExposed: false });
    await wait(value => !itemNodes(value).some(node => node.controlKey.startsWith(prefix + 'cooldown.')), 'natural native cooldown expiry');
    samples.push({ case: 'hud_item_cooldown_expired', target, remainingTicksExposed: false, input: await clean() });
    await command('clear @s');
    assert.equal(itemNodes(await wait(value => itemNodes(value).length === 0, 'item HUD fixture cleanup')).length, 0);
    assert.equal((await tree()).screenClass, '');
    await clean();
  });
}

export async function hudContextCompletionActions({ target, api, command, wait, tree, key, check, samples }) {
  const tag = 'ui_hud_context_fixture';
  const familyRows = (view, family) => (view.presentation?.children ?? []).filter(node => node.controlKey?.startsWith(`hud.context.${family}.`));
  const clean = async () => {
    const input = await api('GET', '/v0/input/state');
    assert.equal(input.pressedKeyCount, 0); assert.equal(input.pressedButtonCount, 0);
    assert.equal(input.inputCleanupPending, false); assert.equal(input.inputCleanupFailed, false);
    return input;
  };
  let heldJump = false;
  try {
    await check('HUD: native experience pixel bar and displayed level', async () => {
      await command('gamemode survival @s');
      await command('experience set @s 7 levels'); await command('experience set @s 9 points');
      const view = await wait(value => familyRows(value, 'experience').some(node => node.role === 'progress_bar' && node.value > 0)
        && familyRows(value, 'experience').some(node => node.controlKey === 'hud.context.experience.level'), 'actual native experience bar and level');
      const rows = validateHudContextDraw(view, 'experience', { requireProgress: true, requireLevel: true });
      assert(rows.find(node => node.controlKey === 'hud.context.experience.level').label.includes('7'));
      samples.push({ case: 'hud_context_draw', target, family: 'experience', category: 'USER', nodes: rows, input: await clean(), rawPlayerStatisticsExposed: false });
      await key(290);
      const hidden = await wait(value => !familyRows(value, 'experience').length, 'F1 hides completed contextual-bar samples');
      assert.equal(familyRows(hidden, 'experience').length, 0); await key(290);
      await wait(value => familyRows(value, 'experience').length > 0, 'native experience HUD returns');
      await key(69); const inventory = await wait(value => /InventoryScreen$/.test(value.screenClass), 'Inventory obscures contextual HUD');
      assert.equal(familyRows(inventory, 'experience').length, 0); await key(256);
      await wait(value => !value.screenClass && familyRows(value, 'experience').length > 0, 'experience HUD after Inventory');
      samples.push({ case: 'hud_context_occlusion', target, family: 'experience', f1UserNodes: 0, inventoryUserNodes: 0, input: await clean() });
    });
    await check('HUD: native mounted jump bar uses actual held-key progress', async () => {
      await command(`kill @e[tag=${tag}]`);
      await command('fill -3 79 -3 3 79 5 minecraft:stone');
      await command('fill -2 80 -2 2 83 4 minecraft:air'); await command('tp @s 0.5 80 0.5 0 0');
      await command(`summon minecraft:horse 0.5 80 2.5 {Tags:["${tag}"],Tame:1b,NoAI:1b,Silent:1b}`);
      const saddle = target.startsWith('1.') ? 'horse.saddle' : 'saddle';
      await command(`item replace entity @e[tag=${tag},type=minecraft:horse,limit=1] ${saddle} with minecraft:saddle`);
      await command(`ride @s mount @e[tag=${tag},type=minecraft:horse,limit=1]`);
      await wait(value => familyRows(value, 'jump').length > 0, 'actual native mounted jump background');
      await api('POST', '/v0/input/key', { key: 32, scanCode: 0, action: 1, modifiers: 0 }); heldJump = true;
      let view;
      try {
        view = await wait(value => familyRows(value, 'jump').some(node => node.role === 'progress_bar' && node.value > 0), 'actual native jump charge sprite');
      } finally {
        await api('POST', '/v0/input/key', { key: 32, scanCode: 0, action: 0, modifiers: 0 }); heldJump = false;
      }
      const released = await api('POST', '/v0/wait/until', { condition: { type: 'input.released' }, timeoutMs: 5000 });
      assert.equal(released.passed, true);
      samples.push({ case: 'hud_context_draw', target, family: 'jump', category: 'USER',
        nodes: validateHudContextDraw(view, 'jump', { requireProgress: true }), released, input: await clean(), rawPlayerStatisticsExposed: false });
      await command('ride @s dismount'); await command(`kill @e[tag=${tag}]`);
      await wait(value => familyRows(value, 'jump').length === 0, 'jump bar ends after native dismount');
    });
    if (target.startsWith('26.')) await check('HUD: native locator bar and marker omit waypoint identity', async () => {
      await command('tp @s 0.5 80 0.5 0 0');
      await command(`summon minecraft:cow 0.5 80 8.5 {Tags:["${tag}"],NoAI:1b,Silent:1b}`);
      await command(`attribute @e[tag=${tag},type=minecraft:cow,limit=1] minecraft:waypoint_transmit_range base set 32`);
      const view = await wait(value => familyRows(value, 'locator').some(node => node.resourceId && !node.resourceId.endsWith('/locator_bar_background')), 'actual native locator marker after experience priority naturally expires');
      samples.push({ case: 'hud_context_draw', target, family: 'locator', category: 'USER',
        nodes: validateHudContextDraw(view, 'locator', { requireMarker: true }), input: await clean(), waypointIdentityExposed: false, rawPlayerStatisticsExposed: false });
      await command(`kill @e[tag=${tag}]`);
      await wait(value => familyRows(value, 'locator').length === 0, 'locator disappears after native waypoint removal');
    });
  } finally {
    if (heldJump) await api('POST', '/v0/input/key', { key: 32, scanCode: 0, action: 0, modifiers: 0 });
    // Reset the disposable creative UI fixture; this is not a claim to roll back a user world.
    await command('ride @s dismount'); await command(`kill @e[tag=${tag}]`);
    await command('experience set @s 0 levels'); await command('experience set @s 0 points');
    await command('gamemode creative @s'); await command('tp @s 0.5 80 0.5 0 0');
    assert.equal((await tree()).screenClass, ''); await clean();
  }
}
