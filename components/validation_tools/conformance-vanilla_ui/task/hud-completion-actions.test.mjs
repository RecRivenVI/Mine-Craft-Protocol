import test from 'node:test';
import assert from 'node:assert/strict';
import { validateHudItemDraw, validateHudContextDraw, effectIconNodes } from './hud-completion-actions.mjs';

test('effect consumers count and clean only native effect nodes, not crosshair or camera status icons', () => {
  const unrelated = [
    { role: 'status_icon', controlKey: 'hud.crosshair.minecraft:hud/crosshair' },
    { role: 'status_icon', controlKey: 'hud.overlay.texture.minecraft:textures/misc/vignette.png.0.0' },
  ];
  const effects = [
    { role: 'status_icon', controlKey: 'hud.effect.0', effect: 'minecraft:speed' },
    { role: 'status_icon', controlKey: 'hud.effect.1', effect: 'minecraft:slowness' },
  ];
  const view = children => ({ presentation: { children } });
  assert.deepEqual(effectIconNodes(view([...unrelated, ...effects])), effects);
  assert.deepEqual(effectIconNodes(view(unrelated)), []);
  const hidden = { role: 'status_icon', controlKey: 'hud.effect.2', effect: 'minecraft:invisibility' };
  assert.equal(effectIconNodes(view([...unrelated, ...effects, hidden])).length, 3);
  assert.equal(effectIconNodes(view([{ role: 'hud_text', controlKey: 'hud.effect.0' }, ...unrelated])).length, 0);
});

function fixture(kind = 'durability') {
  const bar = { controlKey: `hud.item.${kind}.10.20`, visible: true, active: false, actions: [],
    iconSource: 'last_completed_native_hud_draw', role: 'progress_bar', boundsAvailable: true,
    x: 12, y: 33, width: kind === 'durability' ? 13 : 16, height: kind === 'durability' ? 2 : 8,
    valueMinimum: 0, valueMaximum: kind === 'durability' ? 13 : 16, value: kind === 'durability' ? 7 : 8,
    valueSemantics: kind === 'durability' ? 'drawn_fill_pixels_not_raw_item_state' : 'drawn_fill_pixels_not_remaining_ticks', opacity: .5 };
  return { dataCategory: 'USER', screenClass: '', overlayIdentity: 0, presentation: { children: [
    { controlKey: 'hud.hotbar.0', selected: true, x: 10, y: 20, item: 'minecraft:iron_sword' },
    bar, { controlKey: 'hud.selected_item_name', label: 'actual native item name', opacity: .75,
      labelSource: 'last_completed_native_hud_draw', actions: [] },
  ] } };
}

test('accepts only native item pixel extents and actually drawn selected text', () => {
  assert.equal(validateHudItemDraw(fixture(), 'durability').bar.value, 7);
  assert.equal(validateHudItemDraw(fixture('cooldown'), 'cooldown').bar.height, 8);
});

function contextFixture(family = 'experience') {
  const common = { visible: true, active: false, actions: [], iconSource: 'last_completed_native_hud_draw', opacity: 1,
    boundsAvailable: true, x: 69, y: 211, width: 182, height: 5 };
  const background = { ...common, controlKey: `hud.context.${family}.background`, role: 'status_icon', resourceId: `minecraft:hud/${family}_bar_background` };
  const progress = { ...common, controlKey: `hud.context.${family}.progress`, role: 'progress_bar', width: 57,
    value: 57, valueMinimum: 0, valueMaximum: 182, valueSemantics: 'drawn_sprite_extent_not_raw_player_statistics' };
  return { dataCategory: 'USER', screenClass: '', overlayIdentity: 0, width: 320, height: 240,
    presentation: { children: [background, progress, { ...common, controlKey: 'hud.context.experience.level',
      label: 'Level 7', role: 'hud_text', x: 140, y: 205, width: 40, height: 9 }] } };
}

test('accepts displayed contextual sprites and labels without raw player or waypoint state', () => {
  assert.equal(validateHudContextDraw(contextFixture(), 'experience', { requireProgress: true, requireLevel: true }).length, 3);
  assert.equal(validateHudContextDraw(contextFixture('jump'), 'jump', { requireProgress: true }).length, 2);
  const locator = contextFixture('locator');
  locator.presentation.children[1] = { ...locator.presentation.children[0], controlKey: 'hud.context.locator.marker',
    resourceId: 'minecraft:hud/locator_bar_dot', x: 100, y: 212, width: 3, height: 3 };
  assert.equal(validateHudContextDraw(locator, 'locator', { requireMarker: true }).length, 2);
});

test('legacy Forge uses its actual GUI atlas region, never a fabricated modern sprite id',()=>{
  const view=contextFixture();
  for(const [index,node] of view.presentation.children.slice(0,2).entries()){
    delete node.resourceId;
    Object.assign(node,{textureId:'minecraft:textures/gui/icons.png',textureU:0,textureV:index?69:64,
      atlasWidth:256,atlasHeight:256,spriteSemantics:'native_gui_icons_atlas_region',
      spriteRegion:'hud/experience_bar_'+(index?'progress':'background')});
  }
  assert.equal(validateHudContextDraw(view,'experience',{requireProgress:true,requireLevel:true}).length,3);
  for(const mutate of [node=>node.textureV=65,node=>node.textureU=1,node=>node.textureId='private:atlas',
    node=>node.atlasWidth=128,node=>node.resourceId='minecraft:hud/experience_bar_background']){
    const invalid=structuredClone(view);mutate(invalid.presentation.children[0]);
    assert.throws(()=>validateHudContextDraw(invalid,'experience',{requireProgress:true}));
  }
});

test('contextual proof rejects hidden/private state, impossible extents and background-only locator', () => {
  for (const mutate of [
    view => view.dataCategory = 'DEBUG',
    view => view.presentation.children[0].experiencePoints = 100,
    view => view.presentation.children[1].jumpCharge = .3,
    view => view.presentation.children[1].value--,
    view => view.presentation.children[1].width = 400,
    view => view.presentation.children[1].opacity = 0,
  ]) {
    const view = contextFixture(); mutate(view);
    assert.throws(() => validateHudContextDraw(view, 'experience', { requireProgress: true, requireLevel: true }));
  }
  const locator = contextFixture('locator'); locator.presentation.children.splice(1);
  assert.throws(() => validateHudContextDraw(locator, 'locator', { requireMarker: true }));
  locator.presentation.children.push({ ...locator.presentation.children[0], controlKey: 'hud.context.locator.marker',
    resourceId: 'minecraft:hud/locator_bar_dot', uuid: 'private-waypoint-identity' });
  assert.throws(() => validateHudContextDraw(locator, 'locator', { requireMarker: true }));
});

test('rejects wrong item geometry, private statistics, hidden text and unobserved sources', () => {
  for (const mutate of [
    view => view.screenClass = 'InventoryScreen',
    view => view.overlayIdentity = 42,
    view => view.presentation.children[0].x++,
    view => view.presentation.children[1].value = 99,
    view => view.presentation.children[1].damage = 100,
    view => view.presentation.children[1].iconSource = 'raw_item_state',
    view => view.presentation.children[2].opacity = 0,
    view => view.presentation.children[2].label = '',
  ]) {
    const view = fixture(); mutate(view); assert.throws(() => validateHudItemDraw(view, 'durability'));
  }
  const view = fixture('cooldown'); view.presentation.children[1].value--;
  assert.throws(() => validateHudItemDraw(view, 'cooldown'));
});
