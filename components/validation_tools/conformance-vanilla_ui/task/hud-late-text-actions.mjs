import assert from 'node:assert/strict';

const families = new Set(['inventory_effect', 'tooltip', 'chat_background', 'chat_focused',
  'debug_text', 'subtitle', 'overlay_message', 'saving_indicator', 'demo']);
const rows = (view, family) => (view.presentation?.children ?? []).filter(node => node.renderPhase === family);
const nodes = view => (view.children ?? []).flatMap(node => [node, ...nodes(node)]);

export function validateLateTextDraw(view, family, { includes, requireText = true } = {}) {
  assert(families.has(family), 'Unknown native presentation family');
  assert.equal(view.dataCategory, 'USER'); assert.equal(view.overlayIdentity, 0);
  const observed = rows(view, family);
  assert(observed.length > 0, `Actual native ${family} drawing is required`);
  for (const node of observed) {
    assert.equal(node.visible, true); assert.equal(node.active, false); assert.deepEqual(node.actions, []);
    assert.equal(node.role, 'hud_text'); assert.equal(node.boundsAvailable, true);
    assert.equal(node.labelSource, 'last_completed_native_gui_text_draw');
    assert.equal(node.iconSource, 'last_completed_native_gui_draw');
    assert.equal(node.boundsSemantics, 'native_text_draw_viewport_scissor_intersection');
    assert(node.opacity > 0 && node.opacity <= 1);
    for (const field of ['x', 'y', 'width', 'height']) assert(Number.isFinite(node[field]), field);
    assert(node.x >= 0 && node.y >= 0 && node.width > 0 && node.height > 0);
    assert(node.x + node.width <= view.width && node.y + node.height <= view.height);
    assert(['drawn', 'withheld_partial_or_occluded'].includes(node.textPhase));
    if (node.textPhase === 'withheld_partial_or_occluded') assert.equal(node.label, '');
    for (const hidden of ['duration', 'amplifier', 'latency', 'allMessages', 'trimmedMessages',
      'soundPosition', 'serverChunk', 'uuid', 'resourceVersion']) assert.equal(node[hidden], undefined, hidden);
  }
  if (requireText) assert(observed.some(node => node.textPhase === 'drawn' && node.label?.trim()), 'Actual fully drawn text is required');
  if (includes) assert(observed.some(node => node.textPhase === 'drawn' && node.label.includes(includes)), includes);
  return observed;
}

// The caller owns this disposable world. These finite cases prove native text draw output,
// not all subtitle sounds, Demo mode, every tooltip component or every debug-overlay option.
export async function hudLateTextCompletionActions({ target, api, command, wait, tree, key, ui, check, samples }) {
  assert(['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric'].includes(target));
  let inventory = false, chat = false, debug = false;
  const clean = async () => {
    const value = await api('GET', '/v0/input/state');
    assert.equal(value.pressedKeyCount, 0); assert.equal(value.pressedButtonCount, 0);
    assert.equal(value.inputCleanupPending, false); assert.equal(value.inputCleanupFailed, false);
    return value;
  };
  const sample = async (view, family, extra = {}) => samples.push({ case: 'hud_late_native_text', target,
    category: 'USER', family, nodes: validateLateTextDraw(view, family, extra), input: await clean(),
    authority: 'completed_native_gui_draw', hiddenSourceTextAsserted: false, ...extra });
  const closeScreen = async () => {
    for (let i = 0; i < 3; i++) {
      const view = await tree();
      if (!view.screenClass) return;
      assert(/(?:InventoryScreen|ChatScreen)$/.test(view.screenClass), 'Do not dismiss an unrelated Screen');
      await key(256);
    }
    assert.equal((await tree()).screenClass, '', 'Owned UI must close through native Esc');
  };
  try {
    await check('HUD: Inventory effects expose only the actual clipped name and displayed duration', async () => {
      await command('gamemode survival @s'); await command('effect give @s minecraft:speed 600 1 false');
      await key(69); inventory = true;
      const view = await wait(value => /\.InventoryScreen$/.test(value.screenClass)
        && rows(value, 'inventory_effect').some(node => node.textPhase === 'drawn' && node.label?.trim()), 'actual native Inventory effect text');
      await sample(view, 'inventory_effect');
      assert(rows(view, 'inventory_effect').some(node => /\d+:\d{2}/.test(node.label)), 'Native displayed duration, not raw effect ticks');
      await key(256); inventory = false;
      assert.equal(rows(await wait(value => !value.screenClass && rows(value, 'inventory_effect').length === 0, 'Inventory effect draw disappears'), 'inventory_effect').length, 0);
      await command('effect clear @s minecraft:speed');
    });
    await check('HUD: semantic item hover yields actual tooltip draw and leaving the item removes it', async () => {
      await command('clear @s');
      const name = JSON.stringify({text: 'HUD Tooltip Item'});
      const itemArgument = target.startsWith('1.20.1-') ? `minecraft:iron_sword{display:{Name:'${name}'}}`
        : target.startsWith('1.') ? `minecraft:iron_sword[minecraft:custom_name='${name}']`
          : 'minecraft:iron_sword[minecraft:custom_name={text:"HUD Tooltip Item"}]';
      await command('item replace entity @s hotbar.0 with ' + itemArgument);
      await key(69); inventory = true;
      let view = await wait(value => /\.InventoryScreen$/.test(value.screenClass)
        && nodes(value).some(node => node.role === 'slot' && node.item === 'minecraft:iron_sword'), 'Inventory semantic item slot');
      const empty = nodes(view).find(node => node.role === 'slot' && node.slotOwner === 'player_inventory'
        && node.visible !== false && node.active !== false && node.count === 0);
      assert(empty, 'A real empty player inventory slot is required');
      await ui({ nodeId: empty.nodeId }, 'hover');
      await wait(value => rows(value, 'tooltip').length === 0, 'no cached tooltip on an empty slot');
      view = await tree();
      const item = nodes(view).find(node => node.role === 'slot' && node.item === 'minecraft:iron_sword' && node.visible !== false);
      assert(item); await ui({ nodeId: item.nodeId }, 'hover');
      view = await wait(value => rows(value, 'tooltip').some(node => node.label?.includes('HUD Tooltip Item')), 'actual native hovered item tooltip');
      await sample(view, 'tooltip', { includes: 'HUD Tooltip Item' });
      view = await tree();
      const destination = nodes(view).find(node => node.role === 'slot' && node.slotOwner === 'player_inventory'
        && node.visible !== false && node.active !== false && node.count === 0);
      assert(destination); await ui({ nodeId: destination.nodeId }, 'hover');
      await wait(value => rows(value, 'tooltip').length === 0, 'tooltip no longer drawn after semantic hover leaves');
      await key(256); inventory = false; await wait(value => !value.screenClass, 'Inventory closes');
    });
    await check('HUD: chat background and focused chat are actual native text draws, not hidden chat history', async () => {
      const label = 'HUD Native Chat Sample';
      await command(`tellraw @s ${JSON.stringify({ text: label })}`);
      await sample(await wait(value => !value.screenClass && rows(value, 'chat_background').some(node => node.label?.includes(label)), 'actual background chat draw'), 'chat_background', { includes: label });
      await key(84); chat = true;
      const view = await wait(value => /ChatScreen$/.test(value.screenClass)
        && rows(value, 'chat_focused').some(node => node.label?.includes(label)), 'actual focused chat draw');
      await sample(view, 'chat_focused', { includes: label });
      assert.equal(rows(view, 'chat_background').length, 0, 'Do not publish the obscured background-chat frame as focused content');
      await closeScreen(); chat = false;
      await wait(value => !value.screenClass && rows(value, 'chat_focused').length === 0, 'focused chat draw invalidated on screen close');
    });
    await check('HUD: F3 and transient overlay message expose actual native drawn text and disappear when hidden', async () => {
      await key(292); debug = true;
      await sample(await wait(value => rows(value, 'debug_text').some(node => node.textPhase === 'drawn' && node.label?.trim()), 'actual native F3 text'), 'debug_text');
      await key(292); debug = false;
      await wait(value => rows(value, 'debug_text').length === 0, 'native F3 no longer drawn');
      const label = 'HUD Native Overlay Sample';
      await command(`title @s actionbar ${JSON.stringify({ text: label })}`);
      await sample(await wait(value => rows(value, 'overlay_message').some(node => node.label === label), 'actual transient actionbar draw'), 'overlay_message', { includes: label });
      await wait(value => rows(value, 'overlay_message').length === 0, 'native overlay message naturally expires');
      samples.push({ case: 'hud_late_text_finite_scope', target, nativeChatAndF3AndActionbar: true,
        jukeboxWorkflowAsserted: false, subtitleSoundsAsserted: false, savingIndicatorAsserted: false, demoModeAsserted: false });
    });
  } finally {
    if (inventory || chat) await closeScreen();
    if (debug) await key(292);
    await command('effect clear @s minecraft:speed'); await command('clear @s'); await command('gamemode creative @s');
    assert.equal((await tree()).screenClass, ''); await clean();
  }
}
