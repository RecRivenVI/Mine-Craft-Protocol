import test from 'node:test';
import assert from 'node:assert/strict';
import { hudBossScoreActions, validateBossDraw } from './hud-boss-score-actions.mjs';

function fixture(style = 'notched_10') {
  const bar = { controlKey: 'hud.bossbar.0', label: 'HUD Red Segmented', role: 'progress_bar', visible: true,
    active: false, actions: [], x: 69, y: 12, width: 182, height: 5, value: .5, valueSemantics: 'displayed_lerping_progress' };
  const resources = ['red_background', ...(style === 'progress' ? [] : [`${style}_background`]),
    'red_progress', ...(style === 'progress' ? [] : [`${style}_progress`])];
  return { dataCategory: 'USER', screenClass: '', overlayIdentity: 0, width: 320, height: 240,
    presentation: { children: [bar, ...resources.map(resource => ({ controlKey: `hud.boss.draw.${resource}`,
      label: `boss_bar/${resource}`, role: 'status_icon', resourceId: `minecraft:boss_bar/${resource}`,
      visible: true, active: false, actions: [], x: 69, y: 12, width: resource.endsWith('_background') ? 182 : 91,
      height: 5, renderPhase: 'boss_bar', iconSource: 'last_completed_native_hud_draw', boundsAvailable: true }))] } };
}

function atlasFixture(style = 'notched_10', color = 'red') {
  const view = fixture(style);
  const rows = [color === 'red' ? 20 : 10, ...(style === 'progress' ? [] : [90]),
    color === 'red' ? 25 : 15, ...(style === 'progress' ? [] : [95])];
  for (const [index, node] of view.presentation.children.slice(1).entries()) {
    delete node.resourceId;
    node.label = 'native boss bar atlas region';
    node.textureId = 'minecraft:textures/gui/bars.png';
    node.textureU = 0; node.textureV = rows[index]; node.atlasWidth = 256; node.atlasHeight = 256;
    node.spriteSemantics = 'native_gui_bars_atlas_region';
  }
  return view;
}

test('requires actual color and segment native sprites in addition to the displayed progress', () => {
  assert.equal(validateBossDraw(fixture(), { label: 'HUD Red Segmented', color: 'red', style: 'notched_10' }).sprites.length, 4);
  assert.equal(validateBossDraw(fixture('progress'), { label: 'HUD Red Segmented', color: 'red', style: 'progress' }).sprites.length, 2);
});

test('does not mistake current name/progress projection or requested command properties for draw evidence', () => {
  const value = fixture(); value.presentation.children.splice(1);
  value.presentation.children[0].color = 'red'; value.presentation.children[0].style = 'notched_10';
  assert.throws(() => validateBossDraw(value, { label: 'HUD Red Segmented', color: 'red', style: 'notched_10' }));
  for (const mutate of [view => view.dataCategory = 'DEBUG', view => view.screenClass = 'InventoryScreen',
    view => view.presentation.children[1].iconSource = 'requested_color',
    view => view.presentation.children[2].width = 90,
    view => view.presentation.children[3].visible = false,
    view => view.presentation.children[3].uuid = 'private-boss-id',
    view => view.presentation.children[3].y++,
  ]) { const view = fixture(); mutate(view); assert.throws(() => validateBossDraw(view, { label: 'HUD Red Segmented', color: 'red', style: 'notched_10' })); }
});

test('Forge requires real native atlas UV layers without fabricated modern sprite ids', () => {
  const options = { target: '1.20.1-forge', label: 'HUD Red Segmented', color: 'red', style: 'notched_10' };
  assert.equal(validateBossDraw(atlasFixture(), options).sprites.length, 4);
  assert.equal(validateBossDraw(atlasFixture('progress', 'blue'), { ...options, color: 'blue', style: 'progress' }).sprites.length, 2);
  assert.throws(() => validateBossDraw(fixture(), options));
  assert.throws(() => validateBossDraw(atlasFixture(), { ...options, target: '1.21.1-neoforge' }));
  for (const mutate of [
    view => view.presentation.children[1].textureId = 'minecraft:textures/gui/icons.png',
    view => view.presentation.children[1].textureU = 1,
    view => view.presentation.children[2].textureV = 20,
    view => view.presentation.children[3].textureV = 80,
    view => view.presentation.children[1].atlasWidth = 128,
    view => view.presentation.children[1].atlasHeight = 128,
    view => view.presentation.children[1].spriteSemantics = 'requested_style',
    view => view.presentation.children[1].resourceId = 'minecraft:boss_bar/red_background',
    view => view.presentation.children.reverse(),
    view => view.presentation.children.push(structuredClone(view.presentation.children[1])),
    view => view.presentation.children.splice(2, 1),
    view => view.presentation.children[2].width = 90,
    view => view.presentation.children[3].visible = false,
    view => view.presentation.children[3].uuid = 'private-boss-id',
    view => view.presentation.children[3].y++,
  ]) { const value = atlasFixture(); mutate(value); assert.throws(() => validateBossDraw(value, options)); }
});

test('modern native layers must retain exact actual sprite identity order and multiplicity', () => {
  const options = { target: '26.3-fabric', label: 'HUD Red Segmented', color: 'red', style: 'notched_10' };
  for (const mutate of [
    view => { [view.presentation.children[1], view.presentation.children[2]] = [view.presentation.children[2], view.presentation.children[1]]; },
    view => view.presentation.children.push(structuredClone(view.presentation.children[1])),
    view => view.presentation.children[1].resourceId = 'requested:red_background',
    view => delete view.presentation.children[1].resourceId,
  ]) { const value = fixture(); mutate(value); assert.throws(() => validateBossDraw(value, options)); }
});

test('the old interleaved color-progress then notch-background expectation is rejected on all native Targets', () => {
  for (const target of ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric']) {
    const value = target === '1.20.1-forge' ? atlasFixture() : fixture();
    const options = {target, label: 'HUD Red Segmented', color: 'red', style: 'notched_10'};
    assert.equal(validateBossDraw(value, options).sprites.length, 4);
    assert.deepEqual(value.presentation.children.slice(1).map(node => node.width), [182, 182, 91, 91]);
    if (target === '1.20.1-forge') assert.deepEqual(value.presentation.children.slice(1).map(node => node.textureV), [20, 90, 25, 95]);
    [value.presentation.children[2], value.presentation.children[3]] = [value.presentation.children[3], value.presentation.children[2]];
    assert.throws(() => validateBossDraw(value, options), target);
  }
});

test('the actual Forge action consumer uses atlas draws inside readonly and removes all owned bars', async () => {
  const bars = new Map(), commands = [], samples = [], checks = [];
  let insideReadOnly = false, reads = 0;
  const tree = async () => {
    assert.equal(insideReadOnly || bars.size === 0, true);
    const children = [];
    for (const bar of [...bars.values()].filter(value => value.visible).slice(0, 2)) {
      const view = atlasFixture(bar.style, bar.color);
      for (const node of view.presentation.children) {
        node.y = 12 + children.filter(value => value.role === 'progress_bar').length * 19;
      }
      view.presentation.children[0].label = bar.label;
      view.presentation.children[0].controlKey = `hud.bossbar.${children.filter(value => value.role === 'progress_bar').length}`;
      children.push(...view.presentation.children);
    }
    return { dataCategory: 'USER', screenClass: '', overlayIdentity: 0, width: 320, height: 120,
      presentation: { children } };
  };
  await hudBossScoreActions({ target: '1.20.1-forge', samples, tree,
    check: async (label, run) => { checks.push(label); await run(); },
    readOnly: async run => { assert.equal(insideReadOnly, false); insideReadOnly = true; reads++;
      try { return await run(); } finally { insideReadOnly = false; } },
    wait: async predicate => { const view = await tree(); assert.equal(predicate(view), true); return view; },
    command: async value => {
      assert.equal(insideReadOnly, false); commands.push(value);
      const match = /^bossbar (add|set|remove) (\S+)(?: (.*))?$/.exec(value); assert(match);
      const [, action, id, args] = match;
      if (action === 'add') bars.set(id, { label: JSON.parse(args).text, color: 'red', style: 'progress', visible: true });
      else if (action === 'remove') assert.equal(bars.delete(id), true);
      else {
        assert(bars.has(id)); const [key, option] = args.split(' ');
        if (key === 'color' || key === 'style') bars.get(id)[key] = option;
        else if (key === 'visible') bars.get(id).visible = option === 'true';
        else assert(['max', 'value', 'players'].includes(key));
      }
    },
  });
  assert.equal(checks.length, 1, 'Forge has no native Fixed number format');
  assert.equal(bars.size, 0); assert.equal(insideReadOnly, false); assert.equal(reads, 3);
  assert.deepEqual(samples.map(value => value.case), ['hud_boss_native_variants', 'hud_boss_hidden_reflow']);
  assert.equal(samples[0].viewportCutoffObserved, true); assert.equal(samples[0].commandReceiptIsNotDrawProof, true);
  assert.equal(commands.filter(value => value.startsWith('bossbar remove ')).length, 7);
});
