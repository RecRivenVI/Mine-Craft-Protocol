import test from 'node:test';
import assert from 'node:assert/strict';
import { validateVitalVariant, hudVitalVariantActions } from './hud-vital-variant-actions.mjs';

const fixture = label => ({ dataCategory: 'USER', screenClass: '', overlayIdentity: 0, width: 320, height: 240,
  presentation: { hudIconSampleStatus: 'CURRENT', children: [{ controlKey: 'hud.vitals.health.0',
    label, resourceId: `minecraft:${label}`, role: 'status_icon', visible: true, active: false, actions: [],
    iconSource: 'last_completed_native_hud_draw', valueSemantics: 'ordered_native_glyph_composition_not_raw_statistics',
    x: 10, y: 200, width: 9, height: 9 }] } });

test('accepts actual finite frozen blinking and hardcore glyph labels including legacy atlas semantics', () => {
  for (const [kind, label] of [['frozen', 'hud/heart/frozen_full'], ['blinking', 'hud/heart/container_blinking'],
    ['hardcore', 'hud/heart/hardcore_full'], ['hardcore', 'hud/heart/container_hardcore']])
    assert.equal(validateVitalVariant(fixture(label), kind).length, 1);
});

test('rejects raw health/freeze values, invisible or stale draws and wrong glyph themes', () => {
  for (const mutate of [value => value.dataCategory = 'DEBUG', value => value.screenClass = 'PauseScreen',
    value => value.presentation.hudIconSampleStatus = 'STALE', value => value.presentation.children[0].visible = false,
    value => value.presentation.children[0].health = 20, value => value.presentation.children[0].ticksFrozen = 140,
    value => value.presentation.children[0].iconSource = 'predicted_frozen',
  ]) { const value = fixture('hud/heart/frozen_full'); mutate(value); assert.throws(() => validateVitalVariant(value, 'frozen')); }
  assert.throws(() => validateVitalVariant(fixture('hud/heart/full'), 'hardcore'));
  assert.throws(() => validateVitalVariant(fixture('hud/heart/frozen_full'), 'unknown'));
});

test('native fixture failure still resets the disposable arena and never writes a hardcore flag', async () => {
  const commands = [];
  await assert.rejects(hudVitalVariantActions({ target: '26.3-neoforge', api: async () => {},
    command: async command => { commands.push(command); if (command.startsWith('damage ')) throw new Error('native fixture failed'); },
    wait: async () => { throw new Error('must not wait after failed Arrange'); },
    check: async (_name, run) => run(), samples: [], serverTicksAfter: async () => {}, readOnly: run => run(),
  }), /native fixture failed/);
  assert(commands.includes('gamemode creative @s'));
  assert(commands.includes('fill -2 80 -2 2 83 2 minecraft:air'));
  assert.equal(commands.some(command => /hardcore/i.test(command)), false);
});
