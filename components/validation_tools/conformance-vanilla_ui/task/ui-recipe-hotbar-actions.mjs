import assert from 'node:assert/strict';

const filterKeys = new Set(['gui.recipebook.toggleRecipes.all', 'gui.recipebook.toggleRecipes.craftable']);
const recipeItems = ['oak_planks', 'spruce_planks', 'stick'];
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const usable = node => node.visible !== false && node.active !== false && node.occluded !== true;
function filterState(node) {
  if (!/\.(?:StateSwitchingButton|CycleButton)$/.test(node.class ?? '')) return undefined;
  if (filterKeys.has(node.translationKey)) return node.translationKey;
  // Native CycleButton HIDE still builds optionNameValue(EMPTY, value): the visible
  // semantic message has options.generic_value at its root, not the nested recipe key.
  if (node.class.endsWith('.CycleButton') && node.translationKey === 'options.generic_value') {
    if (node.label === ': Showing All') return 'gui.recipebook.toggleRecipes.all';
    if (node.label === ': Showing Craftable') return 'gui.recipebook.toggleRecipes.craftable';
  }
  return undefined;
}

// The outer runner restores its immutable, disposable copy. Recipe unlocks are diagnostic
// Arrange, not a transaction on a user's recipe book. No Runtime/private setter is used here.
export async function uiRecipeHotbarActions({api, target, command, key, tree, wait, nodes, ui, check, samples,
  pause = sleep, now = () => performance.now()}) {
  assert(['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric'].includes(target), 'A maintained native Target is required');
  const allCategory = target.startsWith('1.') ? 'CRAFTING_SEARCH' : 'CRAFTING';
  let lastInput = -Infinity;
  const calls = [];
  async function pace() {
    const delay = 550 - (now() - lastInput);
    if (delay > 0) await pause(delay);
    lastInput = now();
  }
  async function act(node, action = 'click', extra = {}) {
    // Runtime hover accepts a visible native hit box even when a Slot has no hover
    // entry in its click actions. Never extend that exception to a press/activation.
    const hoverBounds = action === 'hover' && ['x', 'y', 'width', 'height'].every(key => Number.isFinite(node?.[key]))
      && node.width > 0 && node.height > 0;
    assert(node?.nodeId && usable(node) && (node.actions?.includes(action) || hoverBounds), 'A fresh, actionable semantic target is required');
    await pace();
    const result = await ui({nodeId: node.nodeId}, action, extra);
    calls.push({nodeId: node.nodeId, role: node.role, controlKey: node.controlKey, action, ...extra});
    return result;
  }
  async function tap(code) { await pace(); await key(code); }
  async function arrange(text) { const result = await command(text); lastInput = now(); return result; }
  async function view() {
    const value = await tree();
    assert.equal(value.dataCategory, 'USER'); assert.equal(typeof value.screenClass, 'string');
    if (value.screenClass !== '' || Object.hasOwn(value, 'treeTruncated')) assert.equal(value.treeTruncated, false);
    assert.equal(value.overlayIdentity, 0); return value;
  }
  function one(value, predicate, label) {
    const matches = nodes(value).filter(node => usable(node) && predicate(node));
    const candidates = matches.length === 1 ? [] : nodes(value).filter(node => usable(node)
      && /\.(?:StateSwitchingButton|CycleButton|EditBox|RecipeBookTabButton)$/.test(node.class ?? '')).slice(0, 16)
      .map(node => ({nodeId: node.nodeId, class: node.class, role: node.role, label: String(node.label ?? '').slice(0, 160),
        translationKey: node.translationKey, labelSource: node.labelSource, actions: node.actions?.slice(0, 6)}));
    assert.equal(matches.length, 1, label + (candidates.length ? '; public candidates=' + JSON.stringify(candidates) : '')); return matches[0];
  }
  async function clean() {
    const input = await api('GET', '/v0/input/state');
    assert.equal(input.pressedKeyCount, 0); assert.equal(input.pressedButtonCount, 0);
    assert.equal(input.inputCleanupPending, false); assert.equal(input.inputCleanupFailed, false); return input;
  }
  async function ready() {
    const value = await view(); assert.equal(value.screenClass, '');
    const player = await api('GET', '/v0/player?category=USER'); assert.equal(player.available, true);
    assert(['survival', 'creative', 'adventure', 'spectator'].includes(player.gameMode));
    const mode = await api('GET', '/v0/control/mode');
    assert.equal(mode.mode, 'TAKEOVER'); assert.equal(mode.reconsentRequired, false);
    await clean(); return player;
  }
  async function closeOwnedInventory() {
    const current = await view();
    if (current.screenClass === '') return;
    assert(/\.(?:InventoryScreen|CreativeModeInventoryScreen)$/.test(current.screenClass), 'Do not close an unrelated Screen');
    await tap(256); await wait(value => !value.screenClass, 'owned inventory closes'); await clean();
  }
  async function cleanup(primary, jobs) {
    const failures = [];
    for (const job of jobs) { try { await job(); } catch (error) { failures.push(error); } }
    if (failures.length) {
      if (primary) primary.cleanupErrors = failures.map(error => error.message);
      else throw new AggregateError(failures, 'Recipe/hotbar cleanup was not confirmed');
    }
  }
  const book = value => nodes(value).find(node => /\.RecipeBookComponent$|\.InventoryRecipeBookComponent$|\.CraftingRecipeBookComponent$/.test(node.class ?? '') && usable(node));
  const recipes = value => nodes(value).filter(node => node.role === 'recipe' && usable(node));
  const recipeSignature = value => [...new Set(recipes(value).map(node => node.item))].sort();
  const search = value => one(value, node => node.role === 'text_field' && node.actions?.includes('type_text'), 'One native recipe search field');
  const filter = value => one(value, node => filterState(node) !== undefined, 'Native recipe-filter semantic message identifies its real state');
  const tabs = value => nodes(value).filter(node => node.role === 'tab' && /\.RecipeBookTabButton$/.test(node.class ?? '') && usable(node));
  const allTab = value => one(value, node => node.role === 'tab' && /\.RecipeBookTabButton$/.test(node.class ?? '') && node.label === allCategory, 'Native all-recipes category');
  const tabIdentity = node => node.resourceId === undefined ? node.label : node.resourceId;
  const selectedAs = (value, expected) => {
    const current = tabs(value), selected = current.filter(node => node.selected === true);
    return current.every(node => typeof node.selected === 'boolean') && selected.length === 1
      && tabIdentity(selected[0]) === expected;
  };
  const tabState = node => ({nodeId: node.nodeId, label: node.label,
    ...(node.resourceId === undefined ? {} : {resourceId: node.resourceId}), selected: node.selected});
  const rectangle = node => {
    for (const field of ['x', 'y', 'width', 'height']) assert(Number.isFinite(node[field]), 'Native overlay rectangle requires finite ' + field);
    assert(node.width >= 1 && node.height >= 1, 'Native overlay rectangle must contain a pixel center');
    return {nodeId: node.nodeId, controlKey: node.controlKey, x: node.x, y: node.y, width: node.width, height: node.height};
  };
  async function inventory() {
    await tap(69);
    return wait(value => /\.InventoryScreen$/.test(value.screenClass), 'native survival inventory');
  }
  async function toggleBook() {
    const current = await view();
    await act(one(current, node => /\.ImageButton$/.test(node.class ?? '') && node.actions?.includes('click'), 'One native Inventory recipe-book toggle'));
  }
  async function recipeFixture(body) {
    const player = await ready(); let arranged = false, initialBook, initialSearch, initialFilter, primary, proof;
    const callStart = calls.length;
    try {
      await arrange('gamemode survival @s');
      let current = await inventory();
      assert.equal(current.carriedStack?.empty, true);
      assert(nodes(current).filter(node => node.role === 'slot').every(node => node.count === 0), 'The disposable fixture inventory must start empty');
      initialBook = !!book(current);
      if (!initialBook) { await toggleBook(); current = await wait(value => !!book(value), 'recipe book opens'); }
      initialSearch = search(current).value; assert.equal(typeof initialSearch, 'string');
      initialFilter = filterState(filter(current));
      await closeOwnedInventory();
      arranged = true;
      await arrange('recipe take @s *');
      for (const item of recipeItems) await arrange('recipe give @s minecraft:' + item);
      for (const [index, item] of ['oak_log', 'spruce_log', 'oak_planks'].entries())
        await arrange(`item replace entity @s hotbar.${index} with minecraft:${item} 8`);
      current = await inventory();
      if (!book(current)) { await toggleBook(); current = await wait(value => !!book(value), 'recipe book reopens'); }
      await act(search(current), 'type_text', {text: '', replace: true});
      current = await view(); await act(allTab(current));
      await wait(value => selectedAs(value, allCategory), 'native all-recipes category is owner-selected');
      current = await view();
      if (filterState(filter(current)) !== 'gui.recipebook.toggleRecipes.all') await act(filter(current));
      await wait(value => recipes(value).some(node => /_planks$/.test(node.item ?? '')), 'unlocked native planks recipe received');
      proof = await body();
    } catch (error) { primary = error; throw error; }
    finally {
      await cleanup(primary, [
        async () => {
          const current = await view();
          if (!/\.InventoryScreen$/.test(current.screenClass) || initialSearch === undefined) return;
          if (!book(current)) await toggleBook();
          let restored = await view();
          // A failed overlay is not dismissed by clicking through its hidden underlying controls.
          assert(!nodes(restored).some(node => node.controlKey === 'recipe.overlay'), 'Leave unexpected recipe overlay for the outer safe shutdown');
          await act(search(restored), 'type_text', {text: initialSearch, replace: true});
          restored = await view();
          if (filterState(filter(restored)) !== initialFilter) await act(filter(restored));
          restored = await view(); assert.equal(search(restored).value, initialSearch); assert.equal(filterState(filter(restored)), initialFilter);
          if (!initialBook) { await toggleBook(); await wait(value => !book(value), 'original closed recipe-book visibility restored'); }
        },
        closeOwnedInventory,
        async () => { if (arranged) await arrange('clear @s'); },
        async () => { await arrange('gamemode ' + player.gameMode + ' @s'); },
        clean,
      ]);
    }
    samples.push({...proof, target, semanticTargets: true, handwrittenCoordinates: false, actAuthority: 'PLAYTEST',
      arrangeAuthority: 'DIAGNOSTIC_player_commands_on_disposable_copy', recipeUnlockCommandsSent: arranged,
      originalRecipeUnlockSetRestored: false, recipeStateResetBy: 'outer_immutable_fixture_restore',
      searchAndFilterRestored: true, recipeVisibilityRestored: true, originalGameModeRestored: true,
      actions: calls.slice(callStart), input: await clean()});
  }
  await check('UI recipes and hotbars: native recipe categories, craftable filter and text search use real controls', async () => {
    await recipeFixture(async () => {
      let current = await view(); const before = recipeSignature(current), choices = tabs(current);
      assert(choices.length >= 2, 'At least one non-search native recipe category is required');
      const category = choices.find(node => node.label !== allCategory); assert(category);
      if (!target.startsWith('1.')) {
        assert.match(category.resourceId, /^[a-z0-9_.-]+:[a-z0-9_./-]+$/);
        assert.equal(category.label, category.resourceId);
      }
      await act(category);
      const selected = await wait(value => selectedAs(value, tabIdentity(category)), 'native book owner selects the chosen category');
      const selectedNode = one(selected, node => node.role === 'tab' && /\.RecipeBookTabButton$/.test(node.class ?? '')
        && node.selected === true, 'One owner-selected native recipe category');
      const selectedItems = recipeSignature(selected);
      await act(allTab(await view()));
      const restored = await wait(value => selectedAs(value, allCategory), 'all-recipes category restored by native owner selection');
      const restoredNode = allTab(restored); assert.equal(restoredNode.selected, true);
      const original = filter(await view()); await act(original);
      const toggled = await wait(value => filterState(filter(value)) !== filterState(original), 'native craftable filter changes');
      const toggledKey = filterState(filter(toggled));
      await act(filter(await view()));
      await wait(value => filterState(filter(value)) === filterState(original), 'native filter restored');
      await act(search(await view()), 'type_text', {text: 'planks', replace: true});
      const filtered = await wait(value => search(value).value === 'planks' && recipes(value).length > 0
        && recipes(value).every(node => /_planks$/.test(node.item ?? '')), 'native recipe search narrows visible recipes');
      return {case: 'recipe_controls', originalItems: before, category: {...tabState(selectedNode), items: selectedItems},
        restoredCategory: tabState(restoredNode), selectionSource: 'native_book_owner_selectedTab_identity',
        filterBefore: filterState(original), filterAfter: toggledKey, searchValue: search(filtered).value, searchItems: recipeSignature(filtered)};
    });
  });
  await check('UI recipes and hotbars: recipe multi-choice overlay fills real crafting slots through USER input', async () => {
    await recipeFixture(async () => {
      await act(search(await view()), 'type_text', {text: 'planks', replace: true});
      let controlMayBeHeld = false, primary;
      async function control(action) {
        await pace();
        const operation = await api('POST', '/v0/pipelines', {steps: [{type: 'key', key: 341, action}],
          cleanupOnComplete: false, timeoutMs: 10000});
        assert.equal(typeof operation.operationId, 'string');
        const result = await api('POST', '/v0/operations/' + operation.operationId + '/wait', {timeoutMs: 10000});
        assert.equal(result.state, 'completed', 'Native recipe Control operation must complete, not remain queued/running/failed');
        calls.push({action: 'native_recipe_control', key: 341, keyAction: action,
          cleanupOnComplete: false, operationId: operation.operationId, state: result.state});
        return {operationId: operation.operationId, state: result.state};
      }
      try {
        controlMayBeHeld = true;
        const down = await control(1), held = await api('GET', '/v0/input/state');
        assert.equal(held.pressedKeyCount, 1); assert.equal(held.pressedButtonCount, 0);
        assert(Array.isArray(held.pressedKeys) && held.pressedKeys.includes(341), 'Actual native Control must remain held before reading the recipe target');
        assert.equal(held.inputCleanupPending, false); assert.equal(held.inputCleanupFailed, false);
        const current = await view(), targetRecipe = recipes(current).find(node => /_planks$/.test(node.item ?? ''));
        assert(targetRecipe, 'A fresh planks recipe group must be visible while native Control freezes its display');
        await act(targetRecipe, 'click', {button: 1});
        await wait(value => nodes(value).some(node => node.controlKey === 'recipe.overlay'), 'actual native multi-recipe overlay');
        const up = await control(0), released = await clean(); controlMayBeHeld = false;
        const expanded = await view(); assert(nodes(expanded).some(node => node.controlKey === 'recipe.overlay'));
        const originalPopup = one(expanded, node => node.controlKey === 'recipe.overlay', 'One native recipe popup owner');
        assert.equal(expanded.carriedStack?.empty, true); assert(Number.isSafeInteger(expanded.screenIdentity) && expanded.screenIdentity > 0);
        const options = nodes(expanded).filter(node => node.role === 'recipe_option' && usable(node));
        assert(options.length >= 2, 'The fixture must really exercise more than one recipe option');
        const choice = options[0]; await act(choice);
        const dismissalView = await view();
        assert.equal(dismissalView.screenClass, expanded.screenClass, 'Recipe dismissal must stay on the same native Screen');
        assert.equal(dismissalView.screenIdentity, expanded.screenIdentity, 'Recipe dismissal must retain its native Screen owner');
        assert.equal(dismissalView.carriedStack?.empty, true);
        const popup = one(dismissalView, node => node.controlKey === 'recipe.overlay', 'One still-open native recipe popup');
        assert.equal(popup.nodeId, originalPopup.nodeId, 'Do not dismiss a replacement native recipe popup owner');
        const popupBounds = rectangle(popup), optionBounds = nodes(dismissalView).filter(node => node.role === 'recipe_option').map(rectangle);
        assert.equal(optionBounds.length, options.length);
        const inside = (point, bounds) => point.x >= bounds.x && point.x < bounds.x + bounds.width
          && point.y >= bounds.y && point.y < bounds.y + bounds.height;
        const point = [{x: popupBounds.x + .5, y: popupBounds.y + .5},
          {x: popupBounds.x + popupBounds.width - .5, y: popupBounds.y + .5},
          {x: popupBounds.x + .5, y: popupBounds.y + popupBounds.height - .5},
          {x: popupBounds.x + popupBounds.width - .5, y: popupBounds.y + popupBounds.height - .5}]
          .find(point => inside(point, popupBounds) && optionBounds.every(bounds => !inside(point, bounds)));
        assert(point, 'The native recipe popup must expose a safe background corner outside every option');
        await clean(); await pace();
        const dismissalOperation = await api('POST', '/v0/pipelines', {steps: [{type: 'mouse.click', ...point, button: 0, holdMs: 40}], timeoutMs: 10000});
        assert.equal(typeof dismissalOperation.operationId, 'string');
        const dismissalResult = await api('POST', '/v0/operations/' + dismissalOperation.operationId + '/wait', {timeoutMs: 10000});
        assert.equal(dismissalResult.state, 'completed', 'Native recipe background click must complete, not remain queued/running/failed');
        const dismissalInput = await clean();
        const dismissed = await wait(value => !nodes(value).some(node => node.controlKey === 'recipe.overlay'), 'native recipe background click dismisses the overlay');
        assert.equal(dismissed.screenClass, expanded.screenClass); assert.equal(dismissed.screenIdentity, expanded.screenIdentity);
        const overlayDismissal = {targeting: 'computed_native_overlay_background_coordinates', semanticNodeClick: false,
          source: {dataCategory: dismissalView.dataCategory, screenClass: dismissalView.screenClass, screenIdentity: dismissalView.screenIdentity,
            overlayNodeId: popup.nodeId, popup: popupBounds, options: optionBounds}, point,
          operationId: dismissalOperation.operationId, stepType: 'mouse.click', state: dismissalResult.state, overlayAbsent: true,
          after: {screenClass: dismissed.screenClass, screenIdentity: dismissed.screenIdentity,
            pressedKeyCount: dismissalInput.pressedKeyCount, pressedButtonCount: dismissalInput.pressedButtonCount,
            inputCleanupPending: dismissalInput.inputCleanupPending, inputCleanupFailed: dismissalInput.inputCleanupFailed}};
        calls.push({action: 'native_recipe_background_dismissal', ...point, operationId: dismissalOperation.operationId, state: dismissalResult.state});
        const filled = await wait(value => !nodes(value).some(node => node.controlKey === 'recipe.overlay')
          && nodes(value).some(node => node.role === 'slot' && node.slot === 0 && /_planks$/.test(node.item ?? '') && node.count === 4)
          && nodes(value).some(node => node.role === 'slot' && node.slot >= 1 && node.slot <= 4 && /^(?:minecraft:oak_log|minecraft:spruce_log)$/.test(node.item ?? '') && node.count === 1),
        'native server-synchronized recipe placement has real ingredient and result slots');
        assert.equal(filled.carriedStack?.empty, true);
        const result = one(filled, node => node.role === 'slot' && node.slot === 0, 'Actual native crafting output');
        const ingredients = nodes(filled).filter(node => node.role === 'slot' && node.slot >= 1 && node.slot <= 4 && node.count > 0);
        assert.equal(ingredients.length, 1);
        assert.equal(result.item, ingredients[0].item.replace('_log', '_planks'));
        return {case: 'recipe_overlay_fill', recipe: {nodeId: targetRecipe.nodeId, item: targetRecipe.item},
          overlayOptionCount: options.length, chosenOption: {nodeId: choice.nodeId, controlKey: choice.controlKey}, overlayDismissal,
          result: {slot: result.slot, item: result.item, count: result.count},
          ingredients: ingredients.map(node => ({slot: node.slot, item: node.item, count: node.count})),
          nativeControlFreezeUsed: true, nativeControlFreeze: {key: 341, down, up,
            heldBeforeRightClick: {pressedKeyCount: held.pressedKeyCount, pressedButtonCount: held.pressedButtonCount, pressedKeys: held.pressedKeys},
            heldAfterRelease: {pressedKeyCount: released.pressedKeyCount, pressedButtonCount: released.pressedButtonCount},
            purpose: 'native_recipe_display_rotation_pause', ordinaryCyclingLabelGuardModified: false},
          realMenuContentsConfirmed: true, ghostRecipeIsNotSufficient: true};
      } catch (error) { primary = error; throw error; }
      finally {
        if (controlMayBeHeld) {
          try {
            const stopped = await api('POST', '/v0/input/release-all', {});
            assert.equal(stopped.type, 'input.release_all'); await clean();
          } catch (error) { if (primary) primary.controlFreezeCleanupError = error.message; else throw error; }
        }
      }
    });
  });
  await check('UI recipes and hotbars: Creative Saved Hotbars save, load and restore an originally empty native row', async () => {
    const player = await ready(); let arranged = false, rowMayBeChanged = false, primary, originalTab, beforeRow, savedRow, loaded, restoredRow;
    const callStart = calls.length;
    const empty = row => row.length === 9 && row.every((node, index) => index === 0 ? node.item === 'minecraft:paper' && node.count === 1 : node.item === 'minecraft:air' && node.count === 0);
    const row = value => Array.from({length: 9}, (_, index) => one(value, node => node.role === 'slot' && node.slot === index, 'Saved hotbar view slot ' + index));
    const state = value => value.map(node => ({slot: node.slot, item: node.item, count: node.count}));
    async function creative() { await tap(69); return wait(value => /\.CreativeModeInventoryScreen$/.test(value.screenClass), 'native creative inventory'); }
    async function selectTab(id) { await act(one(await view(), node => node.role === 'tab' && node.resourceId === id, 'Native creative tab ' + id)); }
    async function hotbar() {
      return wait(value => !value.screenClass && value.presentation?.children?.filter(node => /^hud\.hotbar\.[0-8]$/.test(node.controlKey ?? '')).length === 9, 'nine actual USER hotbar slots');
    }
    function bar(value) { return value.presentation.children.filter(node => /^hud\.hotbar\.[0-8]$/.test(node.controlKey ?? '')).sort((a, b) => a.controlKey.localeCompare(b.controlKey)); }
    async function chord(activator) {
      await pace();
      const operation = await api('POST', '/v0/pipelines', {steps: [{type: 'key', key: activator, action: 1},
        {type: 'key.tap', key: 49, holdMs: 150}, {type: 'key', key: activator, action: 0}], timeoutMs: 10000});
      assert.equal(typeof operation.operationId, 'string');
      const result = await api('POST', '/v0/operations/' + operation.operationId + '/wait', {timeoutMs: 10000});
      assert.equal(result.state, 'completed', 'Do not accept a queued/running/failed native hotbar operation'); await clean();
      calls.push({action: 'native_hotbar_chord', activator, key: 49, holdMs: 150, operationId: operation.operationId, state: result.state});
    }
    async function firstRow() { await creative(); await selectTab('minecraft:hotbar'); return row(await view()); }
    async function emptyHint() {
      const first = one(await view(), node => node.role === 'slot' && node.slot === 0, 'Native empty-hotbar hint');
      await act(first, 'hover');
      const hint = await wait(value => (value.presentation?.children ?? []).filter(node => node.controlKey?.startsWith('presentation.tooltip.'))
        .map(node => node.label).join(' ').match(/\bC\s*\+\s*1\b/i), 'native placeholder tooltip confirms the fixed C+1 save binding');
      return hint.presentation.children.filter(node => node.controlKey?.startsWith('presentation.tooltip.')).map(node => node.label).join(' ');
    }
    try {
      await arrange('gamemode survival @s'); const initialInventory = await inventory();
      assert.equal(initialInventory.carriedStack?.empty, true);
      assert(nodes(initialInventory).filter(node => node.role === 'slot').every(node => node.count === 0), 'Do not replace pre-existing fixture items');
      await closeOwnedInventory(); await arrange('gamemode creative @s');
      const initial = await creative(); originalTab = one(initial, node => node.role === 'tab' && node.selected === true, 'Original native creative tab').resourceId;
      await selectTab('minecraft:hotbar'); beforeRow = row(await view());
      assert(empty(beforeRow), 'Do not overwrite a non-empty or unknown Saved Hotbar row'); await emptyHint();
      await closeOwnedInventory(); arranged = true;
      for (const [index, item] of ['stone', 'oak_planks', 'glass'].entries()) await arrange(`item replace entity @s hotbar.${index} with minecraft:${item} ${index + 2}`);
      const expected = bar(await hotbar()).map(node => ({item: node.item, count: node.count}));
      assert.deepEqual(expected.slice(0, 3), [{item: 'minecraft:stone', count: 2}, {item: 'minecraft:oak_planks', count: 3}, {item: 'minecraft:glass', count: 4}]);
      rowMayBeChanged = true; await chord(67);
      await wait(value => !value.screenClass && value.presentation?.children?.some(node => node.controlKey === 'hud.actionbar' && /\bX\s*\+\s*1\b/i.test(node.label ?? '')), 'native saved-hotbar feedback confirms the fixed X+1 load binding');
      savedRow = await firstRow(); assert.deepEqual(savedRow.map(node => ({item: node.item, count: node.count})), expected);
      await closeOwnedInventory(); await arrange('clear @s');
      await wait(value => !value.screenClass && bar(value).length === 9 && bar(value).every(node => node.count === 0), 'actual hotbar cleared before load');
      await chord(88); loaded = bar(await wait(value => !value.screenClass && JSON.stringify(bar(value).map(node => ({item: node.item, count: node.count}))) === JSON.stringify(expected), 'native saved hotbar load restores all nine real slots'));
    } catch (error) { primary = error; throw error; }
    finally {
      await cleanup(primary, [closeOwnedInventory,
        async () => {
          if (!arranged) return;
          await arrange('clear @s'); await wait(value => !value.screenClass && bar(value).length === 9 && bar(value).every(node => node.count === 0), 'owned hotbar items cleared');
          if (rowMayBeChanged) {
            await chord(67); restoredRow = await firstRow(); assert(empty(restoredRow), 'Empty saved hotbar must really be restored'); await emptyHint();
          }
        },
        async () => { if (originalTab && (await view()).screenClass.endsWith('CreativeModeInventoryScreen')) await selectTab(originalTab); },
        closeOwnedInventory,
        async () => { await arrange('gamemode ' + player.gameMode + ' @s'); }, clean]);
    }
    samples.push({case: 'creative_saved_hotbars', target, before: state(beforeRow), saved: state(savedRow),
      loaded: loaded.map(node => ({controlKey: node.controlKey, item: node.item, count: node.count})), restored: state(restoredRow),
      firstRowOriginallyEmpty: true, firstRowRestoredEmpty: true, otherSavedRowsTouched: false,
      exactHotbarsFileBytesRestored: false, nativeLoadServerContentsConfirmed: true, fixedBindingsConfirmedByNativeText: true,
      originalCreativeTabRestored: true, originalGameModeRestored: true, semanticTargets: true, handwrittenCoordinates: false,
      actAuthority: 'PLAYTEST', arrangeAuthority: 'DIAGNOSTIC_player_commands_on_disposable_copy', actions: calls.slice(callStart), input: await clean()});
  });
}
