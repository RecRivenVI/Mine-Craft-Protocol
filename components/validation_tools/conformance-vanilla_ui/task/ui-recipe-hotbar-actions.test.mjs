import assert from 'node:assert/strict';
import test from 'node:test';
import {uiRecipeHotbarActions} from './ui-recipe-hotbar-actions.mjs';

function runtime({only, nonempty = false, savedNonempty = false, missingOverlay = false, ghostOnly = false,
  operationState = 'completed', wrongLoad = false, wrongSaveHint = false, wrongLoadHint = false, overlayOptions = 2,
  target = '26.3-neoforge', missingRecipeMetadata = false, wrongAllCategory = false,
  screenTruncated = false, missingScreenTruncation = false, hudTruncated, overlayIdentity = 0, filterDefect,
  missingSelected = false, duplicateSelected = false, ignoredTabClick = false, animatedRecipes = false,
  controlOperationState = 'completed', controlNotHeld = false, overlayClickError, controlCleanupError,
  noBackground = false, replacementPopup = false, replacementScreen = false,
  backgroundState = 'completed', backgroundIgnored = false, occupiedCarriedAfterChoice = false} = {}) {
  const allCategory = target.startsWith('1.') ? 'CRAFTING_SEARCH' : 'CRAFTING';
  const calls = [], samples = [], checks = [], operations = new Map();
  const held = new Set();
  let mode = 'creative', screen = '', bookOpen = false, query = '', filterAll = true, category = allCategory;
  let creativeTab = 'minecraft:building_blocks', overlay = false, tooltip = '', actionbar = '', clock = 0, sequence = 0;
  let inventory = Array.from({length: 9}, () => ({item: 'minecraft:air', count: 0}));
  if (nonempty) inventory[5] = {item: 'minecraft:diamond', count: 7};
  let saved = savedNonempty ? [{item: 'minecraft:diamond', count: 1}, ...inventory.slice(1)] : undefined;
  let unlocked = true, crafting = false, recipeFrame = 0, chosen = false;
  const button = (id, role, extra = {}) => ({nodeId: id, role, visible: true, active: true,
    actions: ['hover', 'click'], ...extra});
  function slot(index, item) { return button('slot-' + index, 'slot', {slot: index, ...item,
    x: index * 18, y: 0, width: 18, height: 18, actions: ['click', 'mouse_down', 'mouse_up', 'right_click', 'shift_click']}); }
  const tooltipNode = () => ({controlKey: 'presentation.tooltip.0', label: tooltip});
  function treeValue() {
    let children = [], presentation = [];
    if (screen === 'InventoryScreen') {
      children.push(button('book-toggle', 'button', {class: 'net.minecraft.client.gui.components.ImageButton'}));
      for (let index = 0; index < 46; index++) {
        let content = {item: 'minecraft:air', count: 0};
        if (index >= 36 && index < 45) content = inventory[index - 36];
        if (crafting && index === 0) content = {item: 'minecraft:oak_planks', count: 4};
        if (crafting && index === 1 && !ghostOnly) content = {item: 'minecraft:oak_log', count: 1};
        children.push(slot(index, content));
      }
      if (bookOpen) {
        children.push(button('book', 'container', {class: 'net.minecraft.client.gui.screens.recipebook.' + (target.startsWith('1.') ? 'RecipeBookComponent' : 'CraftingRecipeBookComponent')}));
        children.push(button('search', 'text_field', {class: 'net.minecraft.client.gui.components.EditBox',
          value: query, actions: ['hover', 'click', 'type_text']}));
        const filter = button('filter', 'button', target.startsWith('1.')
          ? {class: 'net.minecraft.client.gui.components.StateSwitchingButton', labelSource: 'native_tooltip',
            label: 'Showing ' + (filterAll ? 'All' : 'Craftable'), translationKey: 'gui.recipebook.toggleRecipes.' + (filterAll ? 'all' : 'craftable')}
          : {class: 'net.minecraft.client.gui.components.CycleButton', label: ': Showing ' + (filterAll ? 'All' : 'Craftable'),
            translationKey: 'options.generic_value'});
        if (filterDefect === 'unknown') filter.label = ': Some other setting';
        if (filterDefect === 'wrong_class') filter.class = 'net.minecraft.client.gui.components.Button';
        children.push(filter);
        if (filterDefect === 'duplicate') children.push({...filter, nodeId: 'other-filter'});
        for (const name of [wrongAllCategory ? 'UNKNOWN_SEARCH' : allCategory, 'BUILDING']) children.push(button('tab-' + name, 'tab', {
          class: 'net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton',
          label: name === 'BUILDING' && !target.startsWith('1.') ? 'minecraft:crafting_building_blocks' : name,
          ...(name === 'BUILDING' && !target.startsWith('1.') ? {resourceId: 'minecraft:crafting_building_blocks'} : {}),
          ...(!missingSelected ? {selected: duplicateSelected || category === name} : {})}));
        if (unlocked) {
          const planks = animatedRecipes && recipeFrame++ % 2 ? 'minecraft:spruce_planks' : 'minecraft:oak_planks';
          const items = category === 'BUILDING' ? [planks] : [planks, 'minecraft:stick'];
          for (const item of items.filter(item => !query || item.includes(query))) children.push(button('recipe-' + item, missingRecipeMetadata ? 'widget' : 'recipe', missingRecipeMetadata ? {} : {item, count: 4}));
        }
        if (overlay) {
          const options = Array.from({length: overlayOptions}, (_, index) => button('option-' + index, 'recipe_option',
            {controlKey: 'recipe.overlay.option.' + index, x: noBackground ? 0 : 120 + index * 27, y: noBackground ? 0 : 100,
              width: noBackground ? 480 : 25, height: noBackground ? 270 : 25}));
          children = [button(chosen && replacementPopup ? 'replacement-overlay' : 'overlay', 'popup',
            {controlKey: 'recipe.overlay', x: 0, y: 0, width: 480, height: 270, actions: [], children: options})];
        }
      }
    } else if (screen === 'CreativeModeInventoryScreen') {
      for (const id of ['minecraft:building_blocks', 'minecraft:hotbar']) children.push(button('creative-' + id, 'tab', {resourceId: id, selected: creativeTab === id}));
      if (creativeTab === 'minecraft:hotbar') {
        const row = saved ?? [{item: 'minecraft:paper', count: 1}, ...Array.from({length: 8}, () => ({item: 'minecraft:air', count: 0}))];
        children.push(...row.map((item, index) => slot(index, item)));
      }
      if (tooltip) presentation.push(tooltipNode());
    } else {
      presentation = inventory.map((item, index) => ({controlKey: 'hud.hotbar.' + index, ...item}));
      if (actionbar) presentation.push({controlKey: 'hud.actionbar', label: actionbar});
    }
    return {screenClass: screen ? 'net.minecraft.client.gui.screens.inventory.' + screen : '',
      dataCategory: 'USER', overlayIdentity, screenIdentity: screen ? chosen && replacementScreen ? 2 : 1 : 0,
      ...(screen ? {...(!missingScreenTruncation ? {treeTruncated: screenTruncated} : {}), carriedStack: {empty: !(chosen && occupiedCarriedAfterChoice)}}
        : hudTruncated === undefined ? {} : {treeTruncated: hudTruncated}),
      children, presentation: {children: presentation}};
  }
  const flatten = value => (value.children ?? []).flatMap(node => [node, ...flatten(node)]);
  const ctx = {target, samples, nodes: flatten,
    now: () => clock, pause: async delay => { clock += delay; }, tree: async () => {
      calls.push({tree: true, controlHeld: held.has(341)}); return treeValue();
    },
    wait: async (predicate, label) => { const value = treeValue(); assert(predicate(value), label); return value; },
    check: async (name, run) => {
      assert(name.startsWith('UI recipes and hotbars:'));
      if (only && !name.includes(only)) return;
      await run(); checks.push(name);
    },
    command: async text => {
      calls.push({command: text});
      assert.equal(screen, '', 'Diagnostic Arrange is not allowed to type into the test UI');
      if (text.startsWith('gamemode ')) mode = text.split(' ')[1];
      else if (text === 'recipe take @s *') unlocked = false;
      else if (text.startsWith('recipe give @s minecraft:')) unlocked = true;
      else if (text.startsWith('item replace entity @s hotbar.')) {
        const match = text.match(/^item replace entity @s hotbar\.(\d) with (minecraft:\w+) (\d+)$/); assert(match);
        inventory[Number(match[1])] = {item: match[2], count: Number(match[3])};
      } else if (text === 'clear @s') inventory = inventory.map(() => ({item: 'minecraft:air', count: 0}));
      else assert.fail('Unexpected command: ' + text);
      return {accepted: true, effectConfirmed: false};
    },
    key: async code => {
      calls.push({key: code}); sequence++;
      if (code === 69) { assert.equal(screen, ''); screen = mode === 'creative' ? 'CreativeModeInventoryScreen' : 'InventoryScreen'; }
      else if (code === 256) { assert(screen.endsWith('InventoryScreen')); screen = ''; overlay = false; crafting = false; tooltip = ''; }
      else assert.fail('Unexpected key: ' + code);
    },
    ui: async (selector, action = 'click', extra = {}) => {
      assert.deepEqual(Object.keys(selector), ['nodeId']);
      calls.push({selector, action, extra}); sequence++;
      const id = selector.nodeId;
      if (id === 'book-toggle') bookOpen = !bookOpen;
      else if (id === 'search') { assert.equal(action, 'type_text'); query = extra.text; }
      else if (id === 'filter') filterAll = !filterAll;
      else if (id.startsWith('tab-')) {if (!ignoredTabClick) category = id.slice(4);}
      else if (id.startsWith('recipe-')) { assert.equal(extra.button, 1); assert(held.has(341), 'Native display must be frozen by Control during right-click');
        if (overlayClickError) throw overlayClickError; if (!missingOverlay) overlay = true; }
      else if (id.startsWith('option-')) { assert.equal(held.size, 0, 'Release the real modifier before choosing an option'); assert(overlay); chosen = true; crafting = true; }
      else if (id.startsWith('creative-')) { creativeTab = id.slice(9); tooltip = ''; }
      else if (id === 'slot-0' && action === 'hover') tooltip = wrongSaveHint ? 'Save with V+1' : 'Save Hotbar with C+1';
      else assert.fail('Unexpected semantic action: ' + id);
      return {targetingSource: 'interaction_tree', directMutationUsed: false};
    },
    api: async (method, path, body) => {
      calls.push({method, path, body});
      if (path === '/v0/player?category=USER') return {available: true, gameMode: mode};
      if (path === '/v0/control/mode') return {mode: 'TAKEOVER', reconsentRequired: false};
      if (path === '/v0/input/state') return {pressedKeyCount: controlNotHeld ? 0 : held.size, pressedKeys: controlNotHeld ? [] : [...held],
        pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false, inputDispatchSequence: sequence};
      if (path === '/v0/input/release-all') {
        assert.equal(method, 'POST'); if (controlCleanupError) throw controlCleanupError;
        held.clear(); return {type: 'input.release_all'};
      }
      if (path === '/v0/pipelines') {
        if (body.steps.length === 1 && body.steps[0].type === 'mouse.click') {
          assert.equal(method, 'POST'); assert.equal(screen, 'InventoryScreen'); assert(overlay && chosen); assert.equal(held.size, 0);
          const step = body.steps[0]; assert.equal(step.button, 0); assert.equal(step.holdMs, 40);
          assert(step.x >= 0 && step.x < 480 && step.y >= 0 && step.y < 270);
          for (let index = 0; index < overlayOptions; index++) assert(!(step.x >= 120 + index * 27 && step.x < 145 + index * 27 && step.y >= 100 && step.y < 125));
          if (!backgroundIgnored) overlay = false;
          sequence++; const operationId = 'op-' + (operations.size + 1); operations.set(operationId, {state: backgroundState}); return {operationId};
        }
        if (body.steps.length === 1 && body.steps[0].key === 341) {
          assert.equal(method, 'POST'); assert.equal(screen, 'InventoryScreen'); assert.equal(body.cleanupOnComplete, false);
          const step = body.steps[0]; assert.equal(step.type, 'key');
          if (step.action === 1) held.add(341); else {assert.equal(step.action, 0); held.delete(341);}
          sequence++; const operationId = 'op-' + (operations.size + 1);
          operations.set(operationId, {state: step.action === 1 ? controlOperationState : 'completed'}); return {operationId};
        }
        assert.equal(method, 'POST'); assert.equal(screen, '');
        assert.equal(body.steps.length, 3); const [down, tap, up] = body.steps;
        assert.deepEqual(tap, {type: 'key.tap', key: 49, holdMs: 150}); assert.equal(down.type, 'key');
        assert.equal(down.action, 1); assert.equal(up.action, 0); assert.equal(up.key, down.key);
        if (down.key === 67) {
          saved = inventory.every(item => item.count === 0) ? undefined : structuredClone(inventory);
          actionbar = wrongLoadHint ? 'Saved; load with Z+1' : 'Hotbar saved. Press X+1 to load';
        } else if (down.key === 88) {
          assert(saved); inventory = wrongLoad ? inventory : structuredClone(saved);
        } else assert.fail('Unexpected native hotbar activator');
        sequence += 3; const operationId = 'op-' + (operations.size + 1);
        operations.set(operationId, {state: operationState}); return {operationId};
      }
      if (/^\/v0\/operations\/op-\d+\/wait$/.test(path)) return operations.get(path.split('/')[3]);
      assert.fail('Unexpected interface: ' + method + ' ' + path);
    }};
  return {ctx, calls, samples, checks, held: () => [...held], state: () => ({mode, screen, bookOpen, query, filterAll, saved, inventory, creativeTab})};
}

test('the actual helper drives native recipe controls, overlay choice and saved-hotbar chords, with independent readback', async () => {
  const run = runtime(); const hud = await run.ctx.tree();
  assert.equal(hud.screenClass, ''); assert.equal(hud.overlayIdentity, 0);
  assert.equal(Object.hasOwn(hud, 'treeTruncated'), false); assert.equal(Object.hasOwn(hud, 'carriedStack'), false);
  await uiRecipeHotbarActions(run.ctx);
  assert.equal(run.checks.length, 3); assert.deepEqual(run.samples.map(value => value.case), ['recipe_controls', 'recipe_overlay_fill', 'creative_saved_hotbars']);
  assert(run.samples[0].category.items.length < run.samples[0].originalItems.length);
  assert.equal(run.samples[0].category.selected, true); assert.equal(run.samples[0].restoredCategory.selected, true);
  assert.equal(run.samples[0].category.resourceId, 'minecraft:crafting_building_blocks');
  assert.equal(run.samples[0].restoredCategory.label, 'CRAFTING');
  assert.equal(run.samples[0].selectionSource, 'native_book_owner_selectedTab_identity');
  const recipeCommands = run.calls.map(call => call.command).filter(command => command?.startsWith('recipe '));
  assert.deepEqual(recipeCommands.slice(0, 4), ['recipe take @s *', 'recipe give @s minecraft:oak_planks',
    'recipe give @s minecraft:spruce_planks', 'recipe give @s minecraft:stick']);
  assert.equal(run.samples[0].recipeUnlockCommandsSent, true); assert.equal(run.samples[0].originalRecipeUnlockSetRestored, false);
  assert.equal(run.samples[1].overlayOptionCount, 2); assert.equal(run.samples[1].result.count, 4);
  assert.equal(run.samples[1].ingredients[0].item, 'minecraft:oak_log');
  assert.equal(run.samples[2].nativeLoadServerContentsConfirmed, true); assert.equal(run.samples[2].firstRowRestoredEmpty, true);
  assert.equal(run.samples[2].exactHotbarsFileBytesRestored, false);
  assert(run.samples[2].actions.filter(value => value.action === 'native_hotbar_chord').every(value => value.holdMs === 150));
  assert.deepEqual(run.state(), {mode: 'creative', screen: '', bookOpen: false, query: '', filterAll: true,
    saved: undefined, inventory: Array.from({length: 9}, () => ({item: 'minecraft:air', count: 0})), creativeTab: 'minecraft:building_blocks'});
  for (const call of run.calls) {
    assert(!call.path?.includes('category=DEBUG')); assert(!call.path?.includes('/debug/'));
    if (call.selector) assert.deepEqual(Object.keys(call.selector), ['nodeId']);
    if (call.body?.steps) assert(call.body.steps.every(step => ['key', 'key.tap', 'mouse.click'].includes(step.type)));
  }
  assert.equal(run.calls.filter(call => call.path === '/v0/pipelines').length, 6, 'Control down/up, one Native background dismissal and save/load/empty-row restore');
});

test('animated native recipe display never replaces the real owner-selected category or restoration proof', async () => {
  const run = runtime({only: 'native recipe categories', animatedRecipes: true}); await uiRecipeHotbarActions(run.ctx);
  assert.equal(run.checks.length, 1); assert.equal(run.samples[0].category.selected, true);
  assert.equal(run.samples[0].restoredCategory.label, 'CRAFTING'); assert.equal(run.samples[0].restoredCategory.selected, true);
  assert.notEqual(run.samples[0].category.nodeId, run.samples[0].restoredCategory.nodeId);
});

test('missing, ambiguous or unchanged native selected state cannot be replaced with a changed recipe display', async () => {
  for (const changes of [{missingSelected: true}, {duplicateSelected: true}, {ignoredTabClick: true, animatedRecipes: true}]) {
    const run = runtime({only: 'native recipe categories', ...changes});
    await assert.rejects(uiRecipeHotbarActions(run.ctx), /owner-selected|owner selects/);
    assert.equal(run.samples.length, 0);
  }
});

test('a native Screen still requires explicit untruncated shape before any semantic recipe action', async () => {
  for (const change of [{screenTruncated: true}, {missingScreenTruncation: true}]) {
    const run = runtime({only: 'native recipe categories', ...change});
    await assert.rejects(uiRecipeHotbarActions(run.ctx), assert.AssertionError);
    assert.equal(run.samples.length, 0); assert(!run.calls.some(call => call.selector));
    assert(!run.calls.some(call => call.command?.startsWith('recipe give') || call.command?.startsWith('item replace')));
  }
});

test('real compound CycleButton messages change filter state without relying on their unchanged generic translation key', async () => {
  const run = runtime({only: 'native recipe categories'}); await uiRecipeHotbarActions(run.ctx);
  assert.equal(run.checks.length, 1);
  assert.equal(run.samples[0].filterBefore, 'gui.recipebook.toggleRecipes.all');
  assert.equal(run.samples[0].filterAfter, 'gui.recipebook.toggleRecipes.craftable');
  assert.equal(run.state().filterAll, true);
  assert.equal(run.calls.filter(call => call.selector?.nodeId === 'filter').length, 2);
});

test('unknown, wrong-class or ambiguous filter messages fail before recipe Arrange or filter input with bounded public diagnostics', async () => {
  for (const filterDefect of ['unknown', 'wrong_class', 'duplicate']) {
    const run = runtime({only: 'native recipe categories', filterDefect});
    await assert.rejects(uiRecipeHotbarActions(run.ctx), error => /Native recipe-filter semantic message/.test(error.message)
      && /public candidates=/.test(error.message));
    assert.equal(run.samples.length, 0);
    assert(!run.calls.some(call => call.selector?.nodeId === 'filter' || call.command?.startsWith('recipe give')));
  }
});

test('HUD does not permit an explicit truncated graph, blocking overlay or missing native overlay identity', async () => {
  for (const change of [{hudTruncated: true}, {overlayIdentity: 7}, {overlayIdentity: null}]) {
    const run = runtime({only: 'native recipe categories', ...change});
    await assert.rejects(uiRecipeHotbarActions(run.ctx), assert.AssertionError);
    assert.equal(run.samples.length, 0); assert.equal(run.calls.filter(call => !call.tree).length, 0);
  }
  const run = runtime({only: 'native recipe categories'}), originalTree = run.ctx.tree;
  run.ctx.tree = async () => {const value = await originalTree(); delete value.overlayIdentity; return value;};
  await assert.rejects(uiRecipeHotbarActions(run.ctx), assert.AssertionError);
  assert.equal(run.samples.length, 0); assert.equal(run.calls.filter(call => !call.tree).length, 0);
});

test('a nonempty fixture inventory is rejected without clearing or replacing its contents', async () => {
  const run = runtime({only: 'native recipe categories', nonempty: true});
  await assert.rejects(uiRecipeHotbarActions(run.ctx), /must start empty/);
  assert.equal(run.samples.length, 0); assert.equal(run.state().inventory[5].item, 'minecraft:diamond');
  assert(!run.calls.some(call => call.command === 'clear @s' || call.command?.startsWith('recipe give') || call.command?.startsWith('item replace')));
});

test('native Control is terminal and actually held before a fresh recipe target, then released before fresh overlay selection', async () => {
  const run = runtime({only: 'recipe multi-choice'}); await uiRecipeHotbarActions(run.ctx);
  assert.equal(run.checks.length, 1); assert.deepEqual(run.held(), []);
  const down = run.calls.findIndex(call => call.path === '/v0/pipelines' && call.body.steps[0].key === 341 && call.body.steps[0].action === 1);
  const heldRead = run.calls.findIndex((call, index) => index > down && call.path === '/v0/input/state');
  const fresh = run.calls.findIndex((call, index) => index > heldRead && call.tree && call.controlHeld);
  const rightClick = run.calls.findIndex(call => call.selector?.nodeId.startsWith('recipe-'));
  const up = run.calls.findIndex(call => call.path === '/v0/pipelines' && call.body.steps[0].key === 341 && call.body.steps[0].action === 0);
  const releasedRead = run.calls.findIndex((call, index) => index > up && call.path === '/v0/input/state');
  const freshOverlay = run.calls.findIndex((call, index) => index > releasedRead && call.tree && !call.controlHeld);
  const option = run.calls.findIndex(call => call.selector?.nodeId.startsWith('option-'));
  assert(down >= 0 && heldRead > down && fresh > heldRead && rightClick > fresh && up > rightClick
    && releasedRead > up && freshOverlay > releasedRead && option > freshOverlay);
  for (const index of [down, up]) {
    const next = run.calls[index + 1]; assert.equal(next.method, 'POST'); assert.match(next.path, /^\/v0\/operations\/op-\d+\/wait$/);
    assert.equal(run.calls[index].body.cleanupOnComplete, false);
  }
  assert.equal(run.samples[0].nativeControlFreezeUsed, true);
  assert.equal(run.samples[0].nativeControlFreeze.key, 341);
  assert.deepEqual(run.samples[0].nativeControlFreeze.heldBeforeRightClick.pressedKeys, [341]);
  assert.equal(run.samples[0].nativeControlFreeze.heldAfterRelease.pressedKeyCount, 0);
  assert.equal(run.samples[0].nativeControlFreeze.ordinaryCyclingLabelGuardModified, false);
});

test('one computed native popup-background click dismisses the retained overlay before reading real crafting slots', async () => {
  const run = runtime({only: 'recipe multi-choice'}); await uiRecipeHotbarActions(run.ctx);
  const proof = run.samples[0].overlayDismissal;
  assert.equal(proof.targeting, 'computed_native_overlay_background_coordinates'); assert.equal(proof.semanticNodeClick, false);
  assert.equal(proof.source.dataCategory, 'USER'); assert.equal(proof.source.popup.controlKey, 'recipe.overlay');
  assert.equal(proof.source.overlayNodeId, proof.source.popup.nodeId); assert.equal(proof.source.options.length, 2);
  assert.deepEqual(proof.point, {x: proof.source.popup.x + .5, y: proof.source.popup.y + .5});
  for (const bounds of proof.source.options) assert(!(proof.point.x >= bounds.x && proof.point.x < bounds.x + bounds.width
    && proof.point.y >= bounds.y && proof.point.y < bounds.y + bounds.height));
  assert.equal(proof.state, 'completed'); assert.equal(proof.overlayAbsent, true);
  assert.equal(proof.after.screenClass, proof.source.screenClass); assert.equal(proof.after.screenIdentity, proof.source.screenIdentity);
  assert.equal(proof.after.pressedKeyCount, 0); assert.equal(proof.after.pressedButtonCount, 0);
  assert.equal(proof.after.inputCleanupPending, false); assert.equal(proof.after.inputCleanupFailed, false);
  const background = run.calls.filter(call => call.body?.steps?.[0]?.type === 'mouse.click');
  assert.equal(background.length, 1); assert.deepEqual(background[0].body.steps, [{type: 'mouse.click', ...proof.point, button: 0, holdMs: 40}]);
  const optionIndex = run.calls.findIndex(call => call.selector?.nodeId.startsWith('option-'));
  const backgroundIndex = run.calls.indexOf(background[0]); assert(backgroundIndex > optionIndex);
  assert(run.calls.slice(optionIndex + 1, backgroundIndex).some(call => call.tree));
  assert.equal(run.calls[backgroundIndex + 1].path, '/v0/operations/' + proof.operationId + '/wait');
  assert.equal(run.samples[0].realMenuContentsConfirmed, true); assert.equal(run.state().screen, '');
});

test('unsafe background, replacement owner or carried contents are rejected without a coordinate click', async () => {
  for (const changes of [{noBackground: true}, {replacementPopup: true}, {replacementScreen: true}, {occupiedCarriedAfterChoice: true}]) {
    const run = runtime({only: 'recipe multi-choice', ...changes});
    await assert.rejects(uiRecipeHotbarActions(run.ctx), assert.AssertionError);
    assert.equal(run.samples.length, 0); assert.deepEqual(run.held(), []);
    assert(!run.calls.some(call => call.body?.steps?.[0]?.type === 'mouse.click'));
    assert.equal(run.calls.filter(call => call.selector?.nodeId.startsWith('option-')).length, 1);
  }
});

test('a nonterminal or ineffective native background click is never replayed or accepted as placement proof', async () => {
  for (const changes of [{backgroundState: 'running'}, {backgroundState: 'queued'}, {backgroundState: 'failed'}, {backgroundIgnored: true}]) {
    const run = runtime({only: 'recipe multi-choice', ...changes});
    await assert.rejects(uiRecipeHotbarActions(run.ctx), /background click/);
    assert.equal(run.samples.length, 0); assert.deepEqual(run.held(), []);
    assert.equal(run.calls.filter(call => call.body?.steps?.[0]?.type === 'mouse.click').length, 1);
    assert.equal(run.calls.filter(call => call.selector?.nodeId.startsWith('option-')).length, 1);
  }
});

test('nonterminal Control or missing real held state never clicks a recipe and is strongly released in finally', async () => {
  for (const changes of [{controlOperationState: 'running'}, {controlOperationState: 'queued'}, {controlOperationState: 'failed'}, {controlNotHeld: true}]) {
    const run = runtime({only: 'recipe multi-choice', ...changes});
    await assert.rejects(uiRecipeHotbarActions(run.ctx), assert.AssertionError);
    assert.equal(run.samples.length, 0); assert.deepEqual(run.held(), []);
    assert(!run.calls.some(call => call.selector?.nodeId.startsWith('recipe-')));
    const stop = run.calls.findIndex(call => call.path === '/v0/input/release-all');
    const close = run.calls.findIndex((call, index) => index > stop && call.key === 256);
    assert(stop >= 0 && close > stop, 'Safety release completes before inventory cleanup');
    assert.equal(run.calls.filter(call => call.path === '/v0/input/release-all').length, 1);
    assert.equal(run.calls.filter(call => call.path === '/v0/pipelines').length, 1, 'Never replay the modifier mutation');
  }
});

test('a failed native right-click is not replayed and its original error survives Control cleanup', async () => {
  const failure = Object.assign(new Error('native rotating recipe guard refused'), {code: 'UI_TARGET_INVALIDATED'});
  const run = runtime({only: 'recipe multi-choice', overlayClickError: failure});
  await assert.rejects(uiRecipeHotbarActions(run.ctx), error => error === failure);
  assert.equal(run.samples.length, 0); assert.deepEqual(run.held(), []);
  assert.equal(run.calls.filter(call => call.selector?.nodeId.startsWith('recipe-')).length, 1);
  const stop = run.calls.findIndex(call => call.path === '/v0/input/release-all');
  assert(stop >= 0); assert(run.calls.slice(stop + 1).some(call => call.path === '/v0/input/state'));
  assert(run.calls.slice(stop + 1).some(call => call.key === 256));
  const cleanupFailure = new Error('release barrier unavailable');
  const broken = runtime({only: 'recipe multi-choice', overlayClickError: failure, controlCleanupError: cleanupFailure});
  await assert.rejects(uiRecipeHotbarActions(broken.ctx), error => error === failure && error.controlFreezeCleanupError === cleanupFailure.message);
  assert.equal(broken.samples.length, 0); assert.deepEqual(broken.held(), [341], 'A failed cleanup is reported, never marked held-zero');
});

test('a nonempty native saved row is never overwritten or loaded', async () => {
  const run = runtime({only: 'Creative Saved Hotbars', savedNonempty: true});
  await assert.rejects(uiRecipeHotbarActions(run.ctx), /Do not overwrite/);
  assert.equal(run.samples.length, 0); assert.equal(run.state().saved[0].item, 'minecraft:diamond');
  assert(!run.calls.some(call => call.path === '/v0/pipelines' || call.command === 'clear @s'));
});

test('native Slot hover needs real visible hit bounds when its advertised actions are click-only', async () => {
  const run = runtime({only: 'Creative Saved Hotbars'}), original = run.ctx.tree;
  run.ctx.tree = async () => {
    const value = await original();
    if (value.screenClass.endsWith('CreativeModeInventoryScreen')) {
      const hint = value.children.find(node => node.role === 'slot' && node.slot === 0);
      if (hint) delete hint.width;
    }
    return value;
  };
  await assert.rejects(uiRecipeHotbarActions(run.ctx), /fresh, actionable semantic target/);
  assert.equal(run.samples.length, 0);
  assert(!run.calls.some(call => call.selector?.nodeId === 'slot-0' && call.action === 'hover'));
});

for (const [name, changes, message] of [
  ['missing multi-choice overlay', {missingOverlay: true}, 'actual native multi-recipe overlay'],
  ['only one native option', {overlayOptions: 1}, 'more than one recipe option'],
  ['ghost output without real ingredient slots', {ghostOnly: true}, 'real ingredient and result slots'],
]) test(name + ' cannot produce a recipe-placement PASS', async () => {
  const run = runtime({only: 'recipe multi-choice', ...changes});
  await assert.rejects(uiRecipeHotbarActions(run.ctx), error => error.message.includes(message));
  assert.equal(run.samples.length, 0);
});

test('a nonterminal native hotbar operation is not accepted as saved or completed', async () => {
  const run = runtime({only: 'Creative Saved Hotbars', operationState: 'running'});
  await assert.rejects(uiRecipeHotbarActions(run.ctx), /queued\/running\/failed/);
  assert.equal(run.samples.length, 0); assert(!run.calls.some(call => call.body?.steps?.[0]?.key === 88));
});

test('an unchanged actual hotbar after load is a failure, not a successful saved-list preview', async () => {
  const run = runtime({only: 'Creative Saved Hotbars', wrongLoad: true});
  await assert.rejects(uiRecipeHotbarActions(run.ctx), /native saved hotbar load restores/);
  assert.equal(run.samples.length, 0); assert.equal(run.state().saved, undefined);
});

for (const [name, changes] of [['save', {wrongSaveHint: true}], ['load', {wrongLoadHint: true}]])
  test('the actual native ' + name + ' binding hint must match the fixed fixture before proceeding', async () => {
    const run = runtime({only: 'Creative Saved Hotbars', ...changes});
    await assert.rejects(uiRecipeHotbarActions(run.ctx), /native .*binding/);
    assert.equal(run.samples.length, 0);
    if (name === 'save') assert(!run.calls.some(call => call.path === '/v0/pipelines'));
    else assert(!run.calls.some(call => call.body?.steps?.[0]?.key === 88));
  });

test('an unverified Target is refused before any operation or Arrange', async () => {
  const run = runtime(); run.ctx.target = 'unknown-target';
  await assert.rejects(uiRecipeHotbarActions(run.ctx), /maintained native Target/);
  assert.equal(run.calls.length, 0);
});

test('all maintained Targets execute actual recipe search/overlay and native saved-hotbar actions using their native category labels', async () => {
  for (const target of ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-fabric']) {
    const run = runtime({target}); await uiRecipeHotbarActions(run.ctx);
    assert.equal(run.checks.length, 3); assert.equal(run.samples.length, 3);
    assert.equal(run.samples[1].realMenuContentsConfirmed, true); assert.equal(run.samples[1].ghostRecipeIsNotSufficient, true);
    assert.equal(run.samples[2].nativeLoadServerContentsConfirmed, true); assert.equal(run.samples[2].firstRowRestoredEmpty, true);
    const selectedTabs = run.calls.filter(call => call.selector?.nodeId.startsWith('tab-')).map(call => call.selector.nodeId);
    assert(selectedTabs.includes('tab-' + (target.startsWith('1.') ? 'CRAFTING_SEARCH' : 'CRAFTING')));
    assert.equal(run.calls.filter(call => call.path === '/v0/pipelines').length, 6);
    assert.equal(run.state().screen, ''); assert.equal(run.state().saved, undefined);
  }
});

test('an unknown native search category or unclassified recipe button cannot manufacture a cross-version recipe proof', async () => {
  for (const change of [{wrongAllCategory: true}, {missingRecipeMetadata: true}]) {
    const run = runtime({target: '1.20.1-forge', only: 'native recipe categories', ...change});
    await assert.rejects(uiRecipeHotbarActions(run.ctx));
    assert.equal(run.samples.length, 0);
    assert.equal(run.calls.some(call => call.selector?.nodeId.startsWith('recipe-')), false);
  }
});
