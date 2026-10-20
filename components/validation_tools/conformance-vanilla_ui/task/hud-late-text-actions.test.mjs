import test from 'node:test';
import assert from 'node:assert/strict';
import { validateLateTextDraw, hudLateTextCompletionActions } from './hud-late-text-actions.mjs';

function fixture(family = 'tooltip') {
  return { dataCategory: 'USER', overlayIdentity: 0, screenClass: 'InventoryScreen', width: 320, height: 240,
    presentation: { children: [{ renderPhase: family, role: 'hud_text', visible: true, active: false, actions: [],
      boundsAvailable: true, x: 10.25, y: 20.5, width: 80, height: 9, opacity: .75,
      label: 'Actual native text', labelSource: 'last_completed_native_gui_text_draw',
      iconSource: 'last_completed_native_gui_draw', textPhase: 'drawn',
      boundsSemantics: 'native_text_draw_viewport_scissor_intersection' }] } };
}

test('accepts only finite actual native text families and permits withheld partial geometry without claiming visible text', () => {
  for (const family of ['inventory_effect', 'tooltip', 'chat_background', 'chat_focused', 'debug_text',
    'subtitle', 'overlay_message', 'saving_indicator', 'demo'])
    assert.equal(validateLateTextDraw(fixture(family), family, { includes: 'native' }).length, 1);
  const value = fixture(); value.presentation.children[0].textPhase = 'withheld_partial_or_occluded';
  value.presentation.children[0].label = '';
  assert.equal(validateLateTextDraw(value, 'tooltip', { requireText: false }).length, 1);
  assert.throws(() => validateLateTextDraw(value, 'tooltip'));
});

test('rejects cache data, hidden characters, impossible coordinates and non-native proof', () => {
  for (const mutate of [value => value.dataCategory = 'DEBUG', value => value.overlayIdentity = 1,
    value => value.presentation.children[0].allMessages = [], value => value.presentation.children[0].amplifier = 1,
    value => value.presentation.children[0].soundPosition = [1, 2, 3], value => value.presentation.children[0].resourceVersion = 'secret',
    value => value.presentation.children[0].visible = false, value => value.presentation.children[0].active = true,
    value => value.presentation.children[0].opacity = 0, value => value.presentation.children[0].x = NaN,
    value => value.presentation.children[0].width = 400, value => value.presentation.children[0].textPhase = 'withheld_partial_or_occluded',
    value => value.presentation.children[0].labelSource = 'cached_tooltip_message',
  ]) { const value = fixture(); mutate(value); assert.throws(() => validateLateTextDraw(value, 'tooltip')); }
  assert.throws(() => validateLateTextDraw(fixture(), 'unknown'));
  assert.throws(() => validateLateTextDraw(fixture(), 'tooltip', { includes: 'not drawn' }));
});

test('a native fixture rejection still resets only the owned disposable fixture without pretending Live succeeded', async () => {
  const commands = [], samples = [];
  await assert.rejects(hudLateTextCompletionActions({ target: '26.3-neoforge', samples,
    command: async text => { commands.push(text); if (text.startsWith('effect give')) throw new Error('native effect fixture denied'); },
    api: async () => ({ pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false }),
    tree: async () => ({ screenClass: '' }), key: async () => {}, ui: async () => {},
    wait: async () => { throw new Error('must not fabricate a view'); }, check: async (_name, run) => run(),
  }), /native effect fixture denied/);
  assert(commands.includes('effect clear @s minecraft:speed')); assert(commands.includes('clear @s'));
  assert.equal(commands.at(-1), 'gamemode creative @s'); assert.deepEqual(samples, []);
});

test('the actual tooltip helper sends each native item format and requires real semantic hover/readback before recording proof', async () => {
  for (const target of ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric']) {
    const commands = [], samples = [], hovers = [];
    const name = JSON.stringify({text: 'HUD Tooltip Item'});
    const expected = target === '1.20.1-forge' ? `minecraft:iron_sword{display:{Name:'${name}'}}`
      : target === '1.21.1-neoforge' ? `minecraft:iron_sword[minecraft:custom_name='${name}']`
        : 'minecraft:iron_sword[minecraft:custom_name={text:"HUD Tooltip Item"}]';
    let screen = '', itemReceived = false, hovered = '';
    const tree = async () => {
      const value = fixture();
      value.screenClass = screen;
      value.children = screen ? [
        {nodeId: 'empty', role: 'slot', slotOwner: 'player_inventory', item: 'minecraft:air', count: 0, visible: true, active: true},
        ...(itemReceived ? [{nodeId: 'item', role: 'slot', slotOwner: 'player_inventory', item: 'minecraft:iron_sword', count: 1, visible: true, active: true}] : []),
      ] : [];
      if (hovered === 'item') value.presentation.children[0].label = 'HUD Tooltip Item';
      else value.presentation.children = [];
      return value;
    };
    await hudLateTextCompletionActions({target, samples,
      command: async text => {
        commands.push(text);
        if (text.startsWith('item replace ')) {
          assert.equal(text, 'item replace entity @s hotbar.0 with ' + expected, 'Actual native command grammar must match this Target');
          itemReceived = true;
        } else if (text === 'clear @s') itemReceived = false;
      },
      api: async () => ({pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false}),
      tree, wait: async (predicate, label) => {const value = await tree(); assert(predicate(value), label); return value;},
      key: async code => {assert([69, 256].includes(code)); screen = code === 69 ? 'net.minecraft.client.gui.screens.inventory.InventoryScreen' : ''; hovered = '';},
      ui: async (selector, action) => {assert.deepEqual(Object.keys(selector), ['nodeId']); assert.equal(action, 'hover'); hovered = selector.nodeId; hovers.push(hovered);},
      check: async (label, run) => {if (label.startsWith('HUD: semantic item hover')) await run();},
    });
    assert.equal(commands.filter(command => command.startsWith('item replace ')).length, 1);
    assert.deepEqual(hovers, ['empty', 'item', 'empty']);
    assert.equal(samples.length, 1); assert.equal(samples[0].family, 'tooltip');
    assert(samples[0].nodes.some(node => node.label === 'HUD Tooltip Item'));
    assert.equal(screen, ''); assert.equal(itemReceived, false);
  }
});

test('a rejected old native tooltip item command is not replayed or converted into a hover proof', async () => {
  const commands = [], samples = [];
  await assert.rejects(hudLateTextCompletionActions({target: '1.21.1-neoforge', samples,
    command: async text => {commands.push(text); if (text.startsWith('item replace ')) throw new Error('Native component parser rejected the fixture');},
    api: async () => ({pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false}),
    tree: async () => ({screenClass: ''}), wait: async () => assert.fail('No tooltip may be invented'),
    key: async () => assert.fail('No inventory input after rejected Arrange'), ui: async () => assert.fail('No hover after rejected Arrange'),
    check: async (label, run) => {if (label.startsWith('HUD: semantic item hover')) await run();},
  }), /Native component parser rejected/);
  assert.equal(commands.filter(command => command.startsWith('item replace ')).length, 1);
  assert.deepEqual(samples, []);
});
