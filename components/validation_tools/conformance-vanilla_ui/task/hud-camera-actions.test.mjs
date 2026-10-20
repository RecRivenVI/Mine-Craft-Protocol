import test from 'node:test';
import assert from 'node:assert/strict';
import { validateHudCameraDraw } from './hud-camera-actions.mjs';

const fixture = (phase = 'camera_overlay') => ({ dataCategory: 'USER', screenClass: '', overlayIdentity: 0,
  width: 320, height: 240, presentation: { children: [{ controlKey: 'hud.overlay.spyglass.texture.0.0',
    resourceId: 'minecraft:textures/misc/spyglass_scope.png', renderPhase: phase, role: 'status_icon',
    visible: true, active: false, actions: [], iconSource: 'last_completed_native_hud_draw',
    boundsAvailable: true, boundsSemantics: 'native_transformed_quad_visible_aabb',
    x: 0, y: .5, width: 320, height: 239.5, opacity: .75 }] } });

test('accepts actual clipped native draw geometry, opacity and pixel progress only', () => {
  assert.equal(validateHudCameraDraw(fixture(), { phase: 'camera_overlay', resourceIncludes: 'spyglass' }).length, 1);
  const view = fixture('attack_indicator'); Object.assign(view.presentation.children[0], {
    role: 'progress_bar', value: 8, valueMinimum: 0, valueMaximum: 16,
    valueSemantics: 'drawn_fill_pixels_not_raw_attack_strength' });
  assert.equal(validateHudCameraDraw(view, { phase: 'attack_indicator' })[0].value, 8);
});

test('rejects hidden/private/raw data, impossible bounds, unsupported source and absent branch', () => {
  for (const mutate of [
    view => view.dataCategory = 'DEBUG', view => view.overlayIdentity = 10,
    view => view.presentation.children[0].visible = false,
    view => view.presentation.children[0].attackStrength = .5,
    view => view.presentation.children[0].portalTime = 3,
    view => view.presentation.children[0].x = -.25,
    view => view.presentation.children[0].height = 241,
    view => view.presentation.children[0].opacity = 0,
    view => view.presentation.children[0].iconSource = 'private_player_state',
    view => view.presentation.children[0].boundsSemantics = 'inferred_geometry',
  ]) { const view = fixture(); mutate(view); assert.throws(() => validateHudCameraDraw(view)); }
  assert.throws(() => validateHudCameraDraw(fixture(), { resourceIncludes: 'pumpkin' }));
  const view = fixture(); view.presentation.children = [];
  assert.throws(() => validateHudCameraDraw(view, { phase: 'crosshair' }));
});

function legacyFixture(region = 'hud/crosshair') {
  const view = fixture(region === 'hud/crosshair' ? 'crosshair' : 'attack_indicator');
  view.target = '1.20.1-forge';
  const node = view.presentation.children[0]; delete node.resourceId;
  const geometry = {
    'hud/crosshair': [0, 0, 15, 15],
    'hud/crosshair_attack_indicator_background': [36, 94, 16, 4],
    'hud/crosshair_attack_indicator_full': [68, 94, 16, 16],
    'hud/crosshair_attack_indicator_progress': [52, 94, 9, 4],
    'hud/hotbar_attack_indicator_background': [0, 94, 18, 18],
    'hud/hotbar_attack_indicator_progress': [18, 104, 18, 8],
  }[region];
  Object.assign(node, { controlKey: `hud.${region}`, x: 10, y: 20,
    textureId: 'minecraft:textures/gui/icons.png', atlasWidth: 256, atlasHeight: 256,
    textureU: geometry[0], textureV: geometry[1], width: geometry[2], height: geometry[3],
    spriteSemantics: 'native_gui_icons_atlas_region', spriteRegion: region });
  if (region.endsWith('_progress')) Object.assign(node, {
    role: 'progress_bar', value: region.includes('hotbar') ? node.height : node.width,
    valueMinimum: 0, valueMaximum: region.includes('hotbar') ? 18 : 16,
    valueSemantics: 'drawn_fill_pixels_not_raw_attack_strength' });
  return view;
}

test('accepts all six real Forge camera atlas regions without inventing sprite resources', () => {
  for (const region of ['hud/crosshair', 'hud/crosshair_attack_indicator_background',
    'hud/crosshair_attack_indicator_progress', 'hud/crosshair_attack_indicator_full',
    'hud/hotbar_attack_indicator_background', 'hud/hotbar_attack_indicator_progress']) {
    const view = legacyFixture(region);
    const [node] = validateHudCameraDraw(view);
    assert.equal(node.spriteRegion, region); assert.equal(node.resourceId, undefined);
  }
});

test('rejects legacy fake sprite resources, wrong atlas, UV, dimensions, phase and inferred values', () => {
  for (const mutate of [
    node => node.resourceId = 'minecraft:hud/crosshair',
    node => node.resourceId = null,
    node => node.textureId = 'private:icons',
    node => node.textureId = 'minecraft:textures/gui/widgets.png',
    node => delete node.textureId,
    node => node.textureU = 1, node => node.textureV = 1,
    node => node.width = 14, node => node.height = 16, node => node.width = 15.5,
    node => node.atlasWidth = 128, node => node.atlasHeight = 128,
    node => node.spriteSemantics = 'inferred_sprite',
    node => node.spriteRegion = 'hud/not_a_native_region',
    node => node.renderPhase = 'attack_indicator',
    node => node.renderPhase = 'camera_overlay',
    node => node.role = 'progress_bar',
    node => node.value = 1,
  ]) {
    const view = legacyFixture(); mutate(view.presentation.children[0]);
    assert.throws(() => validateHudCameraDraw(view));
  }
  for (const [region, mutate] of [
    ['hud/crosshair_attack_indicator_progress', node => node.width = 17],
    ['hud/crosshair_attack_indicator_progress', node => node.textureU = 51],
    ['hud/crosshair_attack_indicator_progress', node => node.height = 5],
    ['hud/crosshair_attack_indicator_progress', node => node.value = 8],
    ['hud/crosshair_attack_indicator_progress', node => node.valueMaximum = 17],
    ['hud/hotbar_attack_indicator_progress', node => node.textureV = 94],
    ['hud/hotbar_attack_indicator_progress', node => node.width = 17],
    ['hud/hotbar_attack_indicator_progress', node => node.height = 19],
    ['hud/hotbar_attack_indicator_progress', node => node.value = 7],
    ['hud/hotbar_attack_indicator_progress', node => node.valueMaximum = 16],
    ['hud/hotbar_attack_indicator_background', node => node.renderPhase = 'crosshair'],
  ]) {
    const view = legacyFixture(region); mutate(view.presentation.children[0]);
    assert.throws(() => validateHudCameraDraw(view));
  }
});

test('Forge cannot masquerade as a modern sprite and modern native sprite validation is retained', () => {
  const modern = fixture('crosshair'); modern.target = '26.3-neoforge';
  Object.assign(modern.presentation.children[0], { resourceId: 'minecraft:hud/crosshair', width: 15, height: 15 });
  assert.equal(validateHudCameraDraw(modern, { phase: 'crosshair', resourceIncludes: 'crosshair' }).length, 1);
  const fakeForge = structuredClone(modern); fakeForge.target = '1.20.1-forge';
  assert.throws(() => validateHudCameraDraw(fakeForge));
  const legacy = legacyFixture('hud/crosshair_attack_indicator_progress');
  delete legacy.presentation.children[0].valueSemantics;
  assert.throws(() => validateHudCameraDraw(legacy));
});
