import assert from 'node:assert/strict';

const rows = view => (view.presentation?.children ?? []).filter(node =>
  ['crosshair', 'attack_indicator', 'camera_overlay', 'sleep_overlay'].includes(node.renderPhase));

const atlasFields = ['textureId', 'textureU', 'textureV', 'atlasWidth', 'atlasHeight', 'spriteSemantics', 'spriteRegion'];

function validateLegacyCameraAtlas(node) {
  assert.equal(node.resourceId, undefined, 'Legacy atlas regions must not invent a sprite resource');
  assert.equal(node.textureId, 'minecraft:textures/gui/icons.png');
  assert.equal(node.atlasWidth, 256); assert.equal(node.atlasHeight, 256);
  assert.equal(node.spriteSemantics, 'native_gui_icons_atlas_region');
  for (const field of ['textureU', 'textureV', 'width', 'height']) assert(Number.isInteger(node[field]), field);
  const regions = {
    'hud/crosshair': [0, 0, 15, 15],
    'hud/crosshair_attack_indicator_background': [36, 94, 16, 4],
    'hud/crosshair_attack_indicator_full': [68, 94, 16, 16],
    'hud/hotbar_attack_indicator_background': [0, 94, 18, 18],
  };
  const fixed = regions[node.spriteRegion];
  const horizontal = node.spriteRegion === 'hud/crosshair_attack_indicator_progress';
  const vertical = node.spriteRegion === 'hud/hotbar_attack_indicator_progress';
  assert(fixed || horizontal || vertical, 'Only reviewed native camera atlas regions are accepted');
  assert.equal(node.renderPhase, node.spriteRegion === 'hud/crosshair' ? 'crosshair' : 'attack_indicator');
  assert.equal(node.role, horizontal || vertical ? 'progress_bar' : 'status_icon');
  if (fixed) assert.deepEqual([node.textureU, node.textureV, node.width, node.height], fixed);
  if (horizontal) {
    assert.equal(node.textureU, 52); assert.equal(node.textureV, 94); assert.equal(node.height, 4);
    assert(node.width > 0 && node.width <= 16);
  }
  if (vertical) {
    assert.equal(node.textureU, 18); assert.equal(node.textureV, 112 - node.height); assert.equal(node.width, 18);
    assert(node.height > 0 && node.height <= 18);
  }
  if (horizontal || vertical) {
    assert.equal(node.value, vertical ? node.height : node.width);
    assert.equal(node.valueMaximum, vertical ? 18 : 16);
  } else {
    for (const field of ['value', 'valueMinimum', 'valueMaximum', 'valueSemantics'])
      assert.equal(node[field], undefined, 'Fixed atlas icons do not expose an inferred attack value');
  }
}

export function validateHudCameraDraw(view, { phase, resourceIncludes } = {}) {
  assert.equal(view.dataCategory, 'USER'); assert.equal(view.overlayIdentity, 0);
  const nodes = rows(view).filter(node => (!phase || node.renderPhase === phase)
    && (!resourceIncludes || node.resourceId?.includes(resourceIncludes)));
  assert(nodes.length > 0, `No actual native ${phase ?? 'camera'} draw`);
  for (const node of nodes) {
    assert.equal(node.visible, true); assert.equal(node.active, false); assert.deepEqual(node.actions, []);
    assert.equal(node.iconSource, 'last_completed_native_hud_draw'); assert.equal(node.boundsAvailable, true);
    for (const field of ['x', 'y', 'width', 'height']) assert(Number.isFinite(node[field]), field);
    assert(node.x >= 0 && node.y >= 0 && node.width > 0 && node.height > 0);
    assert(node.x + node.width <= view.width && node.y + node.height <= view.height);
    assert(node.opacity > 0 && node.opacity <= 1);
    if (atlasFields.some(field => node[field] !== undefined)
      || view.target === '1.20.1-forge' && ['crosshair', 'attack_indicator'].includes(node.renderPhase))
      validateLegacyCameraAtlas(node);
    if (node.renderPhase === 'camera_overlay' || node.renderPhase === 'sleep_overlay')
      assert.equal(node.boundsSemantics, 'native_transformed_quad_visible_aabb');
    if (node.role === 'progress_bar') {
      assert.equal(node.valueSemantics, 'drawn_fill_pixels_not_raw_attack_strength');
      assert(Number.isInteger(node.value) && node.value >= 0 && node.value <= node.valueMaximum);
      assert.equal(node.valueMinimum, 0);
    }
    for (const hidden of ['attackStrength', 'cooldownTicks', 'ticksFrozen', 'portalTime', 'targetEntity', 'worldPosition', 'uuid', 'nbt'])
      assert.equal(node[hidden], undefined, `USER must not expose ${hidden}`);
  }
  return nodes;
}

// These arrange only the caller-owned disposable UI world. No screenshots, direct renderer
// setters or fake player statistics are used; portal/sleep are not claimed by this finite slice.
export async function hudCameraCompletionActions({ target, api, command, wait, tree, key, check, samples }) {
  let heldUse = false;
  const clean = async () => {
    const state = await api('GET', '/v0/input/state');
    assert.equal(state.pressedKeyCount, 0); assert.equal(state.pressedButtonCount, 0);
    assert.equal(state.inputCleanupPending, false); assert.equal(state.inputCleanupFailed, false);
    return state;
  };
  const sample = async (view, options) => samples.push({ case: 'hud_camera_draw', target,
    category: 'USER', nodes: validateHudCameraDraw(view, options), input: await clean(),
    authority: 'native_completed_hud_draw', rawPlayerStatisticsExposed: false, finalPixelAssertion: false });
  const button = action => api('POST', '/v0/input/mouse/button', { button: 1, action, modifiers: 0 });
  try {
    await check('HUD: actual crosshair follows first-person, F1 and Inventory visibility', async () => {
      await command('gamemode creative @s'); await command('clear @s');
      await command('item replace entity @s armor.head with minecraft:air');
      await sample(await wait(value => rows(value).some(node => node.renderPhase === 'crosshair'), 'actual first-person crosshair'), { phase: 'crosshair' });
      await key(290);
      assert.equal(rows(await wait(value => rows(value).length === 0, 'native F1 camera/crosshair suppression')).length, 0);
      await key(290); await wait(value => rows(value).some(node => node.renderPhase === 'crosshair'), 'first-person crosshair restored');
      await key(294);
      const thirdPerson = await wait(value => !rows(value).some(node => node.renderPhase === 'crosshair'), 'third-person hides native crosshair');
      await key(294); await key(294);
      await wait(value => rows(value).some(node => node.renderPhase === 'crosshair'), 'native first-person camera restored');
      await key(69);
      const inventory = await wait(value => /InventoryScreen$/.test(value.screenClass), 'Inventory occlusion');
      assert.equal(rows(inventory).length, 0); await key(256);
      await wait(value => !value.screenClass && rows(value).some(node => node.renderPhase === 'crosshair'), 'HUD returns after Inventory');
      samples.push({ case: 'hud_camera_occlusion', target, f1Rows: 0,
        thirdPersonCrosshairs: rows(thirdPerson).filter(node => node.renderPhase === 'crosshair').length,
        inventoryRows: 0, input: await clean() });
    });
    await check('HUD: actual selected-item change produces native attack-indicator pixels', async () => {
      await command('item replace entity @s hotbar.0 with minecraft:iron_axe');
      await key(50); await key(49);
      const view = await wait(value => rows(value).some(node => node.renderPhase === 'attack_indicator'
        && node.role === 'progress_bar' && node.value > 0), 'native attack-indicator cooldown after item selection');
      await sample(view, { phase: 'attack_indicator' });
      samples.push({ case: 'hud_attack_indicator_scope', target, trigger: 'native_selected_item_change',
        attackDamageAsserted: false, allIndicatorOptionsAsserted: false, rawAttackStrengthExposed: false });
    });
    await check('HUD: native equipped camera overlay uses actual texture and F1 visibility', async () => {
      await command('item replace entity @s armor.head with minecraft:carved_pumpkin');
      await sample(await wait(value => rows(value).some(node => node.resourceId?.includes('pumpkin')), 'actual pumpkin camera texture'),
        { phase: 'camera_overlay', resourceIncludes: 'pumpkin' });
      await key(290);
      assert.equal(rows(await wait(value => rows(value).length === 0, 'F1 suppresses native camera layer')).length, 0);
      await key(290);
      await wait(value => rows(value).some(node => node.resourceId?.includes('pumpkin')), 'native camera layer restored');
      await command('item replace entity @s armor.head with minecraft:air');
      await wait(value => !rows(value).some(node => node.resourceId?.includes('pumpkin')), 'native equipped overlay removed');
    });
    await check('HUD: real held spyglass use submits a viewport-clipped scope and mask', async () => {
      await command('item replace entity @s hotbar.1 with minecraft:spyglass'); await key(50);
      await button(1); heldUse = true;
      const view = await wait(value => rows(value).some(node => node.resourceId?.includes('spyglass')), 'native held spyglass scope');
      const nodes = validateHudCameraDraw(view, { phase: 'camera_overlay' });
      assert(nodes.some(node => node.controlKey.startsWith('hud.overlay.spyglass.')));
      assert(nodes.some(node => node.controlKey.startsWith('hud.overlay.spyglass_mask.')));
      await button(0); heldUse = false;
      const released = await api('POST', '/v0/wait/until', { condition: { type: 'input.released' }, timeoutMs: 5000 });
      assert.equal(released.passed, true);
      samples.push({ case: 'hud_camera_draw', target, category: 'USER', nodes, released,
        input: await clean(), authority: 'native_completed_hud_draw', finalPixelAssertion: false,
        rawPlayerStatisticsExposed: false });
      await wait(value => !rows(value).some(node => node.controlKey.startsWith('hud.overlay.spyglass')), 'scope disappears after native release');
    });
  } finally {
    if (heldUse) await button(0);
    await command('item replace entity @s armor.head with minecraft:air'); await command('clear @s');
    assert.equal((await tree()).screenClass, ''); await clean();
  }
}
