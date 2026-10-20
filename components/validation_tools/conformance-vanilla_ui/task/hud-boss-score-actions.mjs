import assert from 'node:assert/strict';

const bossRows = view => (view.presentation?.children ?? []).filter(node => /^hud\.bossbar\.\d+$/.test(node.controlKey));
const spriteName = node => node.resourceId?.startsWith('minecraft:boss_bar/')
  ? node.resourceId.slice('minecraft:'.length) : node.label;

// Displayed progress alone cannot prove the native color and segmentation draw. The Forge
// fixture uses the reviewed bars.png UV rows; later targets must expose actual sprite ids.
export function validateBossDraw(view, { target, label, color, style }) {
  assert.equal(view.dataCategory, 'USER'); assert.equal(view.screenClass, ''); assert.equal(view.overlayIdentity, 0);
  const bar = bossRows(view).find(node => node.label === label); assert(bar, 'Actual visible boss row is required');
  assert.equal(bar.visible, true); assert.equal(bar.role, 'progress_bar'); assert.equal(bar.active, false);
  assert.deepEqual(bar.actions, []); assert.equal(bar.width, 182); assert.equal(bar.height, 5);
  assert.equal(bar.valueSemantics, 'displayed_lerping_progress'); assert(Math.abs(bar.value - .5) < .001);
  const sprites = (view.presentation?.children ?? []).filter(node => node.renderPhase === 'boss_bar'
    && node.x === bar.x && node.y === bar.y);
  const expected = [`boss_bar/${color}_background`, ...(style === 'progress' ? [] : [`boss_bar/${style}_background`]),
    `boss_bar/${color}_progress`, ...(style === 'progress' ? [] : [`boss_bar/${style}_progress`])];
  const atlas = target === '1.20.1-forge';
  const colorRows = { red: 20, blue: 10 };
  if (atlas) {
    assert(Object.hasOwn(colorRows, color), 'The finite Forge fixture requires a reviewed native color UV');
    assert(['progress', 'notched_10'].includes(style), 'The finite Forge fixture requires a reviewed native style UV');
  }
  const expectedRows = atlas ? [colorRows[color], ...(style === 'progress' ? [] : [90]),
    colorRows[color] + 5, ...(style === 'progress' ? [] : [95])] : [];
  assert.equal(sprites.length, expected.length, 'Every actual layer must be present once and in native draw order');
  for (const [index, name] of expected.entries()) {
    const node = sprites[index];
    if (atlas) {
      assert.equal(node.textureId, 'minecraft:textures/gui/bars.png');
      assert.equal(node.textureU, 0); assert.equal(node.textureV, expectedRows[index]);
      assert.equal(node.atlasWidth, 256); assert.equal(node.atlasHeight, 256);
      assert.equal(node.spriteSemantics, 'native_gui_bars_atlas_region');
      assert.equal(node.resourceId, undefined, 'Legacy atlas observations must not invent modern sprite ids');
    } else {
      assert.equal(node.resourceId, `minecraft:${name}`, `Actual native ${name} draw is required`);
    }
    assert.equal(node.visible, true); assert.equal(node.active, false); assert.deepEqual(node.actions, []);
    assert.equal(node.iconSource, 'last_completed_native_hud_draw'); assert.equal(node.boundsAvailable, true);
    assert(node.height === 5 && node.width > 0 && node.width <= 182);
    assert(node.x >= 0 && node.y >= 0 && node.x + node.width <= view.width && node.y + 5 <= view.height);
    assert.equal(node.width, name.endsWith('_background') ? 182 : 91);
    for (const hidden of ['uuid', 'bossId', 'rawProgress', 'nbt', 'resourceVersion']) assert.equal(node[hidden], undefined);
  }
  if (!atlas) assert.equal(sprites.some(node => spriteName(node)?.startsWith('boss_bar/notched_')), style !== 'progress');
  return { bar, sprites };
}

export async function hudBossScoreActions({ target, command, wait, tree, check, samples, readOnly }) {
  const created = [];
  const labels = ['HUD Red Segmented', 'HUD Blue Solid', ...Array.from({ length: 5 }, (_, i) => `HUD Overflow ${i}`)];
  const identifiers = labels.map((_, i) => `ui_hud_variant:bar_${i}`);
  await check('HUD: actual boss color segmentation order viewport cutoff and removal', async () => {
    assert.equal(bossRows(await tree()).length, 0, 'The disposable fixture must have no unrelated boss bars');
    try {
      for (let i = 0; i < identifiers.length; i++) {
        const id = identifiers[i]; await command(`bossbar add ${id} ${JSON.stringify({ text: labels[i] })}`); created.push(id);
        await command(`bossbar set ${id} max 100`); await command(`bossbar set ${id} value 50`);
        await command(`bossbar set ${id} players @s`);
      }
      await command(`bossbar set ${identifiers[0]} color red`); await command(`bossbar set ${identifiers[0]} style notched_10`);
      await command(`bossbar set ${identifiers[1]} color blue`); await command(`bossbar set ${identifiers[1]} style progress`);
      await readOnly(async () => {
        const view = await wait(value => {
          try { validateBossDraw(value, { target, label: labels[0], color: 'red', style: 'notched_10' }); return true; } catch { return false; }
        }, 'actual native boss color/segment draws (not command acknowledgement)');
        const red = validateBossDraw(view, { target, label: labels[0], color: 'red', style: 'notched_10' });
        const blue = validateBossDraw(view, { target, label: labels[1], color: 'blue', style: 'progress' });
        const expectedCount = Math.min(labels.length, Math.max(1, Math.ceil((view.height / 3 - 12) / 19)));
        const rows = bossRows(view); assert.equal(rows.length, expectedCount);
        assert.deepEqual(rows.map(node => node.label), labels.slice(0, expectedCount));
        assert(rows.every(node => node.y < view.height / 3));
        samples.push({ case: 'hud_boss_native_variants', target, category: 'USER', red, blue,
          visibleRows: rows, fixtureRows: labels.length, viewportCutoffObserved: expectedCount < labels.length,
          arrangeAuthority: 'diagnostic_admin_command', assertAuthority: 'USER_NATIVE_DRAW', commandReceiptIsNotDrawProof: true });
      });
      await command(`bossbar set ${identifiers[0]} visible false`);
      await readOnly(async () => {
        const view = await wait(value => !bossRows(value).some(node => node.label === labels[0])
          && bossRows(value).some(node => node.label === labels[2]), 'hidden boss removed and later native row advances');
        const expectedCount = Math.min(labels.length - 1, Math.max(1, Math.ceil((view.height / 3 - 12) / 19)));
        assert.deepEqual(bossRows(view).map(node => node.label), labels.slice(1, expectedCount + 1));
        samples.push({ case: 'hud_boss_hidden_reflow', target, rows: bossRows(view), hiddenNameAbsent: true });
      });
    } finally {
      for (const id of created.reverse()) await command(`bossbar remove ${id}`);
    }
    await readOnly(async () => assert.equal(bossRows(await wait(value => bossRows(value).length === 0, 'all owned boss bars removed')).length, 0));
  });
  if (!target.startsWith('1.20.1-')) await check('HUD: native fixed sidebar number format reveals text but not the underlying score', async () => {
    const id = 'ui_hud_fixed'; let createdObjective = false;
    try {
      await command(`scoreboard objectives add ${id} dummy ${JSON.stringify({ text: 'HUD fixed score fixture' })}`); createdObjective = true;
      await command(`scoreboard players set VisibleName ${id} 876543`);
      await command(`scoreboard objectives modify ${id} numberformat fixed ${JSON.stringify({ text: 'DISPLAY', color: 'gold' })}`);
      await command(`scoreboard objectives setdisplay sidebar ${id}`);
      await readOnly(async () => {
        const view = await wait(value => value.presentation?.children?.some(node => node.controlKey?.startsWith('hud.sidebar.row.')
          && node.label === 'VisibleName: DISPLAY'), 'native formatted sidebar text');
        const rows = view.presentation.children.filter(node => node.controlKey?.startsWith('hud.sidebar.'));
        assert(!JSON.stringify(rows).includes('876543'));
        assert(rows.every(node => node.value === undefined && !node.active && node.actions.length === 0));
        samples.push({ case: 'hud_sidebar_fixed_format', target, rows, rawScoreExposed: false,
          allTextStylesAsserted: false, category: 'USER' });
      });
    } finally { if (createdObjective) await command(`scoreboard objectives remove ${id}`); }
    await readOnly(async () => await wait(value => !value.presentation?.children?.some(node => node.controlKey?.startsWith('hud.sidebar.')), 'fixed sidebar removed'));
  });
}
