import assert from 'node:assert/strict';

const glyphs = view => (view.presentation?.children ?? []).filter(node => node.controlKey?.startsWith('hud.vitals.'));
const patterns = { frozen: /(?:^|\/)frozen_(?:hardcore_)?(?:full|half)/,
  blinking: /_blinking$/, hardcore: /hardcore/ };

export function validateVitalVariant(view, variant) {
  assert(patterns[variant], 'Unknown finite vital variant');
  assert.equal(view.dataCategory, 'USER'); assert.equal(view.screenClass, ''); assert.equal(view.overlayIdentity, 0);
  assert.equal(view.presentation?.hudIconSampleStatus, 'CURRENT');
  const nodes = glyphs(view);
  assert(nodes.some(node => node.controlKey.startsWith('hud.vitals.health.') && patterns[variant].test(node.label)),
    `No actually drawn ${variant} heart glyph`);
  for (const node of nodes) {
    assert.equal(node.visible, true); assert.equal(node.active, false); assert.deepEqual(node.actions, []);
    assert.equal(node.role, 'status_icon'); assert.equal(node.iconSource, 'last_completed_native_hud_draw');
    assert.equal(node.valueSemantics, 'ordered_native_glyph_composition_not_raw_statistics');
    assert.equal(typeof node.resourceId, 'string'); assert.equal(node.width, 9); assert.equal(node.height, 9);
    assert(node.x >= 0 && node.y >= 0 && node.x + 9 <= view.width && node.y + 9 <= view.height);
    for (const hidden of ['health', 'maxHealth', 'absorption', 'ticksFrozen', 'foodLevel', 'saturation', 'airSupply', 'uuid', 'nbt', 'resourceVersion'])
      assert.equal(node[hidden], undefined, `USER must not expose ${hidden}`);
  }
  return nodes;
}

// Only existing production glyph observations are asserted. Hardcore needs a separately created
// real hardcore world; validateVitalVariant supports its resulting glyph but this helper does not
// invent a hardcore flag, alter an immutable baseline, or count an ordinary hard world as coverage.
export async function hudVitalVariantActions({ target, api, command, wait, check, samples, serverTicksAfter, readOnly }) {
  assert.equal(typeof readOnly, 'function');
  const remember = async (variant, view) => {
    const mode = await api('GET', '/v0/control/mode'); assert.equal(mode.mode, 'READ');
    const input = await api('GET', '/v0/input/state');
    assert.equal(input.pressedKeyCount, 0); assert.equal(input.pressedButtonCount, 0);
    assert.equal(input.inputCleanupPending, false); assert.equal(input.inputCleanupFailed, false);
    samples.push({ case: 'hud_vital_variant', target, variant, mode, input,
      category: 'USER', nodes: validateVitalVariant(view, variant),
      arrangeAuthority: 'diagnostic_admin_command', assertAuthority: 'USER_NATIVE_UI', rawPlayerStatisticsExposed: false });
  };
  const heal = async () => {
    await command('effect clear @s'); await command('effect give @s minecraft:instant_health 1 20 true');
    await command('effect give @s minecraft:saturation 1 20 true'); await command('effect clear @s');
  };
  try {
    await check('HUD: blinking hearts are actual damage layers and expire on the native clock', async () => {
      await command('gamemode survival @s'); await command('difficulty normal'); await command('clear @s');
      await command('fill -3 79 -3 3 79 3 minecraft:stone');
      await command('fill -2 80 -2 2 83 2 minecraft:air'); await command('tp @s 0.5 80 0.5 0 0');
      await heal(); await serverTicksAfter(65);
      await command('damage @s 4 minecraft:generic');
      await readOnly(async () => {
        const view = await wait(value => value.presentation?.hudIconSampleStatus === 'CURRENT'
          && glyphs(value).some(node => node.controlKey.startsWith('hud.vitals.health.') && patterns.blinking.test(node.label)), 'actual native blinking heart layer');
        assert(Number.isInteger(view.clientTick)); await remember('blinking', view);
        const expired = await wait(value => value.clientTick >= view.clientTick + 25
          && value.presentation?.hudIconSampleStatus === 'CURRENT'
          && glyphs(value).some(node => node.controlKey.startsWith('hud.vitals.health.'))
          && !glyphs(value).some(node => patterns.blinking.test(node.label)), 'native blinking lifecycle completed');
        samples.push({ case: 'hud_vital_blink_expired', target, firstClientTick: view.clientTick,
          afterClientTick: expired.clientTick, nativeClockObserved: true, rawHealthRead: false });
      }); await heal();
    });
    await check('HUD: actual powder-snow freezing selects the native frozen heart glyph', async () => {
      await command('clear @s'); await heal();
      await command('fill -1 80 -1 1 82 1 minecraft:powder_snow'); await command('tp @s 0.5 80 0.5 0 0');
      await readOnly(async () => {
        const view = await wait(value => value.presentation?.hudIconSampleStatus === 'CURRENT'
          && glyphs(value).some(node => patterns.frozen.test(node.label)), 'native powder-snow frozen heart glyph');
        await remember('frozen', view);
      });
      await command('fill -1 80 -1 1 82 1 minecraft:air');
      await readOnly(async () => {
        await wait(value => value.presentation?.hudIconSampleStatus === 'CURRENT'
          && glyphs(value).some(node => node.controlKey.startsWith('hud.vitals.health.'))
          && !glyphs(value).some(node => patterns.frozen.test(node.label)), 'native thaw restores normal heart family');
      });
      samples.push({ case: 'hud_vital_thaw', target, rawFrozenTicksRead: false, nativeGlyphObserved: true });
    });
  } finally {
    // Reset this disposable arena, not an arbitrary user world or the immutable fixture baseline.
    await command('gamemode creative @s'); await command('fill -2 80 -2 2 83 2 minecraft:air');
    await command('effect clear @s'); await command('clear @s'); await command('tp @s 0.5 80 0.5 0 0');
  }
}
