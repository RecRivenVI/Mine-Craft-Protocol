import assert from 'node:assert/strict';
import test from 'node:test';
import {legacySleepingRuleDraw, nativeSaddleSlots, readLegacySleepingRule, readVisibleSleepingRule, sleepingRule, specialWorldMenuActions} from './ui-special-world-menu-actions.mjs';

function controlledNative({target = '26.3-neoforge', socialAvailable = false, pipelineRunning = false, occupiedHand = false,
  absentSocial = target.startsWith('1.') || target === '26.1.2-neoforge', missingSocialRefusal = false,
  disabledCleanupDelta = 1, disabledMovePointer = false, disabledScreenChange = false, disabledHeld = false,
  changingSlotIds = false, slotChangedAfterPickup, driftAfterEquipment = false,
  bedCompanionPickup = false, bedPickupNotRemoved = false, unrelatedCleanupHand = false, bedDropNotRemoved = false,
  initialBedCount = 0, bedCompanionCount = 1, priorBedDrops = 0, priorDropsNotRemoved = false} = {}) {
  let screen = '', rule = 37, item = occupiedHand ? 'minecraft:diamond' : 'minecraft:air', count = occupiedHand ? 1 : 0;
  let time = 0, sequence = 0, identity = 1, revision = 1, bed = false, nautilus = false, tame = true;
  let carried, saddle = false, draft = '', ruleReads = 0, held = 0, actionbar = '', ruleSearch = '', bedDrop = false;
  let otherBeds = initialBedCount;
  let latentBeds = priorBedDrops;
  const pointer = {x: 12.25, y: 8.5};
  let position = {x: .5, y: 80, z: .5};
  const operations = new Map(), commands = [], inputs = [], samples = [], checks = [], messages = [], slotInputs = []; let clientTick = 0;
  const node = (nodeId, translationKey, extra = {}) => ({nodeId, translationKey, role: 'button', label: translationKey,
    visible: true, active: true, actions: ['click'], ...extra});
  const change = next => {screen = next; identity++; revision++; draft = '';};
  const tree = async () => {
    let children = [];
    if (screen === 'PauseScreen') children = [target === '26.1.2-neoforge' ? node('options', 'menu.options') : node('world-options', 'options.worldOptions.button'),
      absentSocial ? node('lan', 'menu.shareToLan') : node('social', 'menu.playerReporting', {active: socialAvailable})];
    if (screen === 'OptionsScreen') children = [node('world-options', 'options.worldOptions.button'), node('options-done', 'gui.done')];
    if (screen === 'WorldOptionsScreen') children = [node('rules', 'editGamerule.inGame.button'), node('world-cancel', target === '26.1.2-neoforge' ? 'gui.done' : 'gui.cancel')];
    if (screen === 'InWorldGameRulesScreen') {
      ruleReads++; children = [node('rule-search', '', {role: 'text_field', class: 'net.minecraft.client.gui.components.EditBox',
        label: '', value: ruleSearch, actions: ['type_text']}),
        ...(ruleSearch === 'sleep' ? [node('sleep-rule', '', {role: 'text_field',
          label: 'Sleep percentage\nplayers_sleeping_percentage\n', value: String(rule), actions: ['type_text']})] : []), node('rules-cancel', 'gui.cancel')];
    }
    if (screen === 'InBedChatScreen') children = [node('sleep-chat', 'chat.editBox', {role: 'text_field', value: draft, actions: ['type_text']}), node('wake-up', 'multiplayer.stopSleeping')];
    if (screen === 'NautilusInventoryScreen') children = [node('mount-saddle', '', {role: 'slot', slot: 0, slotOwner: 'menu', item: saddle ? 'minecraft:saddle' : 'minecraft:air', count: saddle ? 1 : 0}),
      node('mount-body', '', {role: 'slot', slot: 1, slotOwner: 'menu', item: 'minecraft:air', count: 0}),
      node('player-source', '', {role: 'slot', slot: 29, slotOwner: 'player_inventory', item, count})];
    if (screen === 'NautilusInventoryScreen' && slotInputs.length > 0) {
      if (slotChangedAfterPickup === 'missing') children = children.filter(node => node.slot !== 0);
      if (slotChangedAfterPickup === 'owner') children[0].slotOwner = 'player_inventory';
      if (slotChangedAfterPickup === 'slot') children[0].slot = 99;
      if (slotChangedAfterPickup === 'duplicate') children.push({...children[0], nodeId: 'duplicate-mount'});
    }
    if (changingSlotIds && screen === 'NautilusInventoryScreen') children = children.map(node => ({...node, nodeId: node.nodeId + '@' + revision}));
    if (screen === 'SocialInteractionsScreen') children = ['hidden', 'blocked', 'all'].map(tab => node('tab-' + tab, 'gui.socialInteractions.tab_' + tab));
    if (screen === 'SocialInteractionsScreen') children.push(node('social-search', '', {role: 'text_field', value: draft, actions: ['type_text']}), node('social-done', 'gui.done'));
    return {screenClass: screen ? 'net.minecraft.client.gui.screens.' + screen : '', dataCategory: 'USER', treeTruncated: false,
      overlayIdentity: 0, clientTick: ++clientTick, width: 640, height: 360,
      screenIdentity: identity + (screen === 'NautilusInventoryScreen' && slotInputs.length > 0 && slotChangedAfterPickup === 'screen' ? 1 : 0),
      screenRevision: revision, menuRevision: revision, carriedStack: carried ? {id: carried, count: 1, empty: false} : {empty: true}, children,
      presentation: {children: [...Array.from({length: 9}, (_, index) => ({nodeId: 'hud.hotbar.' + index, controlKey: 'hud.hotbar.' + index,
        role: 'hotbar_item', selected: index === 0, item: index === 0 ? item : index <= otherBeds ? 'minecraft:red_bed' : 'minecraft:air', count: index === 0 ? count : index <= otherBeds ? 1 : 0})),
        ...(actionbar ? [{controlKey: 'hud.actionbar', label: actionbar, visible: true}] : []),
        ...(!screen ? messages.slice(-8).map((label, index) => ({renderPhase: 'chat_background', role: 'hud_text',
          label, visible: true, active: false, actions: [], boundsAvailable: true, x: 10, y: 200 + index * 10,
          width: 320, height: 9, opacity: 1, labelSource: 'last_completed_native_gui_text_draw',
          iconSource: 'last_completed_native_gui_draw', textPhase: 'drawn',
          boundsSemantics: 'native_text_draw_viewport_scissor_intersection'})) : [])]}};
  };
  const input = () => ({pressedKeyCount: held, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false,
    inputDispatchSequence: sequence, agentPointer: {...pointer}});
  const api = async (method, path, body) => {
    inputs.push({method, path, body});
    if (path === '/v0/input/state') return input();
    if (path === '/v0/player?category=USER') return {available: true, dataCategory: 'USER', gameMode: 'creative',
      ...(driftAfterEquipment && slotInputs.length >= 2 ? {x: .5, y: 72.197, z: -4.559} : position)};
    if (path === '/v0/player?category=DEBUG') return {available: true, dataCategory: 'DEBUG', uuid: '00000000-0000-0000-0000-000000000001'};
    if (path.startsWith('/v0/world/block?')) {
      const query = new URLSearchParams(path.split('?')[1]), y = Number(query.get('y'));
      return {available: true, dataCategory: 'USER', block: y === 79 ? 'minecraft:stone' : bed ? 'minecraft:red_bed' : 'minecraft:air', targetRef: y === 79 ? 'support' : 'bed'};
    }
    if (path.startsWith('/v0/world/entities?')) return {dataCategory: 'USER', authority: 'CLIENT_KNOWN', chunkLoadRequested: false, entitiesTruncated: false,
      entities: [...(nautilus ? [{type: 'minecraft:nautilus', x: .5, y: 80, z: 2.5, tamed: tame, targetRef: 'native-nautilus'}] : []),
        ...Array.from({length: latentBeds + (bedDrop ? 1 : 0)}, () => ({type: 'minecraft:item', itemId: 'minecraft:red_bed', itemCount: 1, x: .5, y: 80, z: 3.5}))]};
    if (method === 'POST' && path === '/v0/pipelines') {
      const receipts = [];
      for (const step of body.steps) {
        if (step.type === 'key') held = step.action;
        else if (step.type === 'delay') assert(step.durationMs >= 150);
        else if (step.type === 'world.action') {
          assert.equal(step.category, 'USER');
          if (step.action === 'place') {assert.equal(step.targetRef, 'support'); assert.equal(item, 'minecraft:red_bed'); bed = true;}
          else if (step.targetRef === 'bed') {assert(bed); assert.equal(rule, 101); assert.equal(item, 'minecraft:air'); change('InBedChatScreen');}
          else {assert.equal(step.targetRef, 'native-nautilus'); assert(nautilus && tame); assert.equal(held, 1, 'Real Shift must remain held through native entity use'); change('NautilusInventoryScreen');}
          receipts.push({stepType: 'world.action', result: {dataCategory: 'USER', evidenceAuthority: 'PLAYTEST', directMutationUsed: false, effectConfirmed: false}});
        } else assert.fail(JSON.stringify(step));
        sequence++;
      }
      held = 0;
      const operationId = String(operations.size + 1); operations.set(operationId, {state: pipelineRunning ? 'executing' : 'completed', result: {steps: receipts}});
      return {operationId};
    }
    const waiting = /^\/v0\/operations\/(\d+)\/wait$/.exec(path); if (waiting) return operations.get(waiting[1]);
    assert.fail('Unexpected API ' + method + ' ' + path);
  };
  const ui = async ({nodeId}, action = 'click', extra = {}) => {
    const value = await tree(), control = value.children.find(node => node.nodeId === nodeId); assert(control, nodeId);
    if (control.role === 'slot') slotInputs.push({nodeId, slot: control.slot, slotOwner: control.slotOwner,
      screenIdentity: value.screenIdentity, screenRevision: revision});
    nodeId = nodeId.split('@')[0];
    if (!control.active) {
      sequence += disabledCleanupDelta;
      if (disabledMovePointer) pointer.x += .5;
      if (disabledScreenChange) revision++;
      if (disabledHeld) held = 1;
      throw Object.assign(new Error('Native entry disabled'), {code: 'UI_NODE_NOT_ACTIONABLE'});
    }
    assert(control.actions.includes(action)); sequence++; revision++;
    if (action === 'type_text') {if (nodeId === 'rule-search') {assert.equal(extra.text, 'sleep'); ruleSearch = extra.text;}
      else {assert.notEqual(nodeId, 'sleep-rule', 'Rule reading must never edit the actual rule'); draft = extra.text;} return {};}
    if (nodeId === 'options') change('OptionsScreen');
    else if (nodeId === 'options-done') change('PauseScreen');
    else if (nodeId === 'world-options') change('WorldOptionsScreen');
    else if (nodeId === 'rules') {change('InWorldGameRulesScreen'); ruleSearch = '';}
    else if (nodeId === 'rules-cancel') change('WorldOptionsScreen');
    else if (nodeId === 'world-cancel') change(target === '26.1.2-neoforge' ? 'OptionsScreen' : 'PauseScreen');
    else if (nodeId === 'wake-up') {assert.equal(draft, ''); otherBeds += latentBeds; latentBeds = 0; change('');}
    else if (nodeId === 'player-source') {
      if (carried) {assert.equal(count, 0); item = carried; count = 1; carried = undefined;}
      else {assert.equal(item, 'minecraft:saddle'); carried = item; item = 'minecraft:air'; count = 0;}
    } else if (nodeId === 'mount-saddle') {if (carried) {assert(!saddle); saddle = true; carried = undefined;} else {assert(saddle); saddle = false; carried = 'minecraft:saddle';}}
    else if (nodeId === 'social') change('SocialInteractionsScreen');
    else if (nodeId.startsWith('tab-')) assert.equal(screen, 'SocialInteractionsScreen');
    else if (nodeId === 'social-done') change('PauseScreen');
    else assert.fail('Unexpected semantic native node ' + nodeId);
    return {};
  };
  const ctx = {api, target, command: async text => {
    commands.push(text);
    if (text === 'gamerule playersSleepingPercentage') messages.push('Gamerule playersSleepingPercentage is currently set to: ' + rule);
    else if (text.startsWith('tellraw @s ')) messages.push(JSON.parse(text.slice('tellraw @s '.length)).text);
    else if (text.startsWith('gamerule ')) {rule = Number(text.split(' ').at(-1)); assert(Number.isSafeInteger(rule));}
    else if (text.startsWith('tp @s ')) {const values = text.split(' '); position = {x: Number(values[2]), y: Number(values[3]), z: Number(values[4])};}
    else if (text.startsWith('item replace ')) {item = text.split(' ')[6]; count = item === 'minecraft:air' ? 0 : Number(text.split(' ')[7]);}
    else if (text.startsWith('summon ')) {assert.match(text, /Owner:\[I;0,0,0,1\]/); nautilus = true;}
    else if (text.startsWith('kill @e[type=minecraft:item,')) {
      assert.equal(text, 'kill @e[type=minecraft:item,x=-1,y=79,z=1,dx=3,dy=4,dz=4,nbt={Item:{id:"minecraft:red_bed"}}]');
      if (!bedDropNotRemoved) bedDrop = false;
      if (!priorDropsNotRemoved) latentBeds = 0;
    } else if (text.startsWith('kill ')) {assert.match(text, /tag=ui_menu_[a-f0-9]+/); nautilus = false;}
    else if (text === 'fill 0 80 2 0 80 3 minecraft:air') {
      if (bed && bedCompanionPickup) {
        item = 'minecraft:red_bed'; count = 1; otherBeds += bedCompanionCount - 1;
      }
      if (bed && unrelatedCleanupHand) {item = 'minecraft:diamond'; count = 1;}
      if (bed && bedDropNotRemoved) bedDrop = true;
      bed = false;
    } else if (text === 'clear @s minecraft:red_bed 1') {
      assert(item === 'minecraft:red_bed' && count === 1 || otherBeds > 0);
      if (!bedPickupNotRemoved) {
        if (item === 'minecraft:red_bed') {item = 'minecraft:air'; count = 0;}
        else otherBeds--;
      }
    }
    return {accepted: true, effectConfirmed: false};
  }, key: async code => {
    sequence++; if (code === 256) change(screen ? '' : 'PauseScreen');
    else if (code === 80) {assert.equal(screen, ''); if (socialAvailable) change('SocialInteractionsScreen'); else actionbar = missingSocialRefusal ? '' : 'Social Interactions are only available in Multiplayer';}
    else assert.fail('Unexpected key ' + code);
  }, tree, nodes: value => value.children ?? [], ui,
  click: async translationKey => {const value = await tree(), target = value.children.find(node => node.translationKey === translationKey); assert(target); await ui({nodeId: target.nodeId});},
  wait: async (predicate, label) => {const value = await tree(); assert(predicate(value), label + ': ' + JSON.stringify(value)); return value;},
  enterWorld: async () => {assert.equal(screen, '');}, check: async (name, run) => {await run(); checks.push(name);}, samples};
  return {ctx, services: {now: () => time, sleep: async milliseconds => {time += milliseconds;}}, commands, inputs, samples, checks, slotInputs,
    state: () => ({screen, rule, item, count, held, saddle, carried, nautilus, bed, ruleReads}), setTame: value => {tame = value;}};
}

test('the actual helper uses native bed and Nautilus USER gestures, restores the rule, and records the real singleplayer Social limitation', async () => {
  const runtime = controlledNative(); await specialWorldMenuActions(runtime.ctx, runtime.services);
  assert.equal(runtime.checks.length, 3); assert.deepEqual(runtime.samples.map(sample => sample.case), ['special_world_in_bed_chat', 'special_world_nautilus_inventory', 'special_world_social_environment_limit']);
  const state = runtime.state(); assert.equal(state.rule, 37); assert.equal(state.item, 'minecraft:air'); assert.equal(state.count, 0);
  for (const flag of ['held', 'saddle']) assert.equal(state[flag], flag === 'held' ? 0 : false);
  assert.equal(state.nautilus, false); assert.equal(state.bed, false); assert.equal(state.screen, ''); assert(state.ruleReads >= 3);
  const bed = runtime.samples[0]; assert.equal(bed.chatSubmitted, false); assert.equal(bed.originalRule.value, 37); assert.equal(bed.restoredRule.value, 37);
  assert.equal(runtime.samples[1].saddleEquippedAndReturned, true); assert.equal(runtime.samples[1].source.slot, 29);
  assert.equal(runtime.samples[2].screenOperationCoverage, false); assert.equal(runtime.samples[2].screenConstructedByProbe, false);
  const denial = runtime.samples[2].disabledEntryProof;
  assert.equal(denial.after.inputDispatchSequence, denial.before.inputDispatchSequence + 1);
  assert.deepEqual(denial.after.agentPointer, denial.before.agentPointer);
  assert.equal(denial.afterScreenIdentity, denial.beforeScreenIdentity); assert.equal(denial.afterScreenRevision, denial.beforeScreenRevision);
  assert(runtime.inputs.filter(call => call.path === '/v0/pipelines').every(call => call.body.steps.some(step => step.type === 'world.action')));
  assert(!runtime.inputs.some(call => /debug\/mutations|control\/acquire/.test(call.path)));
  assert.equal(runtime.inputs.filter(call => call.path === '/v0/world/entities?category=USER&radius=8&limit=128').length, 4,
    'Bed preparation records before/after drops, cleanup reads them again, followed by normal Nautilus discovery');
});

test('26.1.2 sleeping-rule reads use Options and both native Done returns without editing the rule field', async () => {
  const value = controlledNative({target: '26.1.2-neoforge'}), calls = [], click = value.ctx.click, ui = value.ctx.ui;
  value.ctx.click = async (...args) => {calls.push(args[0]); return click(...args);};
  value.ctx.ui = async (...args) => {calls.push(args[0].nodeId); return ui(...args);};
  value.ctx.check = async (name, run) => {if (name.includes('actual bed use')) await run();};
  await specialWorldMenuActions(value.ctx, value.services);
  for (const key of ['menu.options', 'options.worldOptions.button', 'world-cancel', 'options-done'])
    assert.equal(calls.filter(value => value === key).length, 3, key);
  assert(!calls.includes('sleep-rule')); assert.equal(value.state().screen, ''); assert.equal(value.state().rule, 37);
  assert.equal(value.samples[0].restoredRule.value, 37); assert.equal(value.samples[0].chatSubmitted, false);
});

test('a native companion bed pickup is removed once and USER entity plus later hotbar frames prove consecutive fixtures are clean', async () => {
  const value = controlledNative({bedCompanionPickup: true});
  await specialWorldMenuActions(value.ctx, value.services);
  assert.equal(value.checks.length, 3); assert.equal(value.samples[1].case, 'special_world_nautilus_inventory');
  const cleanup = value.samples[0].bedCleanup;
  assert.equal(cleanup.beforeHotbar[0].item, 'minecraft:red_bed'); assert.equal(cleanup.beforeHotbar[0].count, 1);
  assert(cleanup.afterHotbar.every(slot => slot.item === 'minecraft:air' && slot.count === 0));
  assert(cleanup.observedTick < cleanup.afterTick && cleanup.afterTick < cleanup.stableTick);
  assert.deepEqual(cleanup.dropReadback.remainingBedDrops, []); assert.equal(cleanup.dropReadback.dataCategory, 'USER');
  assert.equal(cleanup.dropReadback.entitiesTruncated, false); assert.equal(cleanup.unrelatedItemsCleared, false);
  assert.equal(value.commands.filter(text => text === 'clear @s minecraft:red_bed 1').length, 1);
  assert.equal(value.commands.filter(text => text.startsWith('kill @e[type=minecraft:item,')).length, 2);
  assert(!value.commands.some(text => text === 'clear @s' || text.startsWith('execute as @e[')));
});

test('bed cleanup fails closed for an unremoved pickup, a remaining real item entity or an unrelated hand without replay', async () => {
  for (const [options, expected] of [
    [{bedCompanionPickup: true, bedPickupNotRemoved: true}, /Owned bed pickup removed/],
    [{bedDropNotRemoved: true}, /Owned bed item entity disappears/],
    [{unrelatedCleanupHand: true}, /unrelated player hand/],
  ]) {
    const value = controlledNative(options);
    value.ctx.check = async (name, run) => {if (name.includes('actual bed use')) await run();};
    await assert.rejects(specialWorldMenuActions(value.ctx, value.services), expected);
    assert.equal(value.commands.filter(text => text.startsWith('kill @e[type=minecraft:item,')).length, 2);
    assert.equal(value.commands.filter(text => text === 'clear @s minecraft:red_bed 1').length, options.bedCompanionPickup ? 1 : 0);
    assert.equal(value.samples.some(sample => sample.case === 'special_world_nautilus_inventory'), false);
    assert(!value.commands.includes('clear @s'));
    if (options.unrelatedCleanupHand) assert.equal(value.state().item, 'minecraft:diamond');
  }
});

test('bed cleanup preserves preexisting hotbar beds and removes only the one new companion pickup', async () => {
  const value = controlledNative({initialBedCount: 1, bedCompanionPickup: true});
  value.ctx.check = async (name, run) => {if (name.includes('actual bed use')) await run();};
  await specialWorldMenuActions(value.ctx, value.services);
  const proof = value.samples.find(row => row.case === 'special_world_in_bed_chat').bedCleanup;
  assert.equal(proof.initialBedCount, 1); assert.equal(proof.addedBeds, 1);
  assert.equal(proof.initialHotbar[1].count, 1); assert.equal(proof.beforeHotbar[1].count, 1);
  assert.equal(proof.beforeHotbar[0].count, 1);
  assert.equal(proof.afterHotbar[1].count, 1); assert.equal(proof.afterHotbar[0].count, 0);
  assert.equal(value.commands.filter(text => text === 'clear @s minecraft:red_bed 1').length, 1);
});

test('more than one newly appearing bed is still refused without clearing unknown inventory', async () => {
  const value = controlledNative({initialBedCount: 1, bedCompanionPickup: true, bedCompanionCount: 2});
  value.ctx.check = async (name, run) => {if (name.includes('actual bed use')) await run();};
  await assert.rejects(specialWorldMenuActions(value.ctx, value.services), /unknown extra bed stacks/);
  assert.equal(value.commands.filter(text => text.startsWith('clear @s')).length, 0);
});

test('earlier owned bed drops are removed before wake-up can turn them into unknown inventory pickups', async () => {
  const value = controlledNative({priorBedDrops: 2});
  value.ctx.check = async (name, run) => {if (name.includes('actual bed use')) await run();};
  await specialWorldMenuActions(value.ctx, value.services);
  const proof = value.samples.find(row => row.case === 'special_world_in_bed_chat');
  assert.equal(proof.priorDropReadback.remainingBedDrops.length, 2);
  assert.deepEqual(proof.preparationDropReadback.remainingBedDrops, []);
  assert.equal(proof.bedCleanup.initialBedCount, 0); assert.equal(proof.bedCleanup.addedBeds, 0);
  assert(value.commands.findIndex(text => text.startsWith('kill @e[type=minecraft:item,'))
    < value.commands.findIndex(text => text.startsWith('item replace ')));
  assert.equal(value.commands.filter(text => text.startsWith('clear @s')).length, 0);
});

test('a prior bed entity that remains visible prevents arranging another bed or clearing inventory', async () => {
  const value = controlledNative({priorBedDrops: 2, priorDropsNotRemoved: true});
  value.ctx.check = async (name, run) => {if (name.includes('actual bed use')) await run();};
  await assert.rejects(specialWorldMenuActions(value.ctx, value.services), /Prior diagnostic bed drops/);
  assert(!value.commands.some(text => text.startsWith('item replace ') || text.startsWith('clear @s')));
});

test('disabled native entry requires exactly its mandatory cleanup, no held input, no pointer movement and no Screen change', async () => {
  for (const options of [{disabledCleanupDelta: 0}, {disabledCleanupDelta: 2}, {disabledMovePointer: true}, {disabledScreenChange: true}, {disabledHeld: true}]) {
    const runtime = controlledNative(options);
    runtime.ctx.check = async (name, run) => {if (name.includes('native social entry')) await run();};
    await assert.rejects(specialWorldMenuActions(runtime.ctx, runtime.services), assert.AssertionError);
    assert.equal(runtime.samples.length, 0, JSON.stringify(options));
  }
});

test('an actually available native Social page uses semantic tabs and draft search without reporting or account changes', async () => {
  const runtime = controlledNative({socialAvailable: true}); await specialWorldMenuActions(runtime.ctx, runtime.services);
  const sample = runtime.samples.find(sample => sample.case === 'special_world_social_navigation'); assert(sample);
  assert.equal(sample.visits.length, 3); assert.equal(sample.localNavigationOnly, true); assert.equal(sample.reportSubmitted, false); assert.equal(sample.externalServiceAcceptance, false);
  assert.equal(runtime.state().screen, '');
});

test('old unpublished singleplayer exposes its actual LAN alternative and the real Social-key refusal, not an invented disabled button', async () => {
  for (const target of ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge']) {
    const value = controlledNative({target});
    value.ctx.check = async (name, run) => {if (name.includes('native social entry')) await run();};
    await specialWorldMenuActions(value.ctx, value.services);
    const sample = value.samples[0]; assert.equal(value.samples.length, 1);
    assert.equal(sample.disposition, 'NATIVE_SINGLEPLAYER_ENTRY_ABSENT'); assert.equal(sample.entry, null);
    assert.equal(sample.disabledEntryProof, undefined); assert.equal(sample.absentEntryProof.socialEntries, 0);
    assert.equal(sample.absentEntryProof.alternateEntry.translationKey, 'menu.shareToLan');
    assert.equal(sample.screenOperationCoverage, false); assert.equal(value.state().screen, '');
    assert.equal(value.commands.length, 0); assert.equal(sample.fakeAccountUsed, false);
  }
});

test('missing or duplicate LAN, a modern missing entry, and absent visible Social refusal cannot become environment coverage', async () => {
  for (const target of ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge']) for (const bad of ['missing', 'duplicate', 'no_refusal']) {
    const value = controlledNative({target, missingSocialRefusal: bad === 'no_refusal'}), original = value.ctx.tree;
    value.ctx.tree = async () => {const tree = await original();
      if (tree.screenClass.endsWith('PauseScreen') && bad !== 'no_refusal')
        tree.children = bad === 'missing' ? tree.children.filter(node => node.translationKey !== 'menu.shareToLan')
          : [...tree.children, {...tree.children.find(node => node.translationKey === 'menu.shareToLan'), nodeId: 'duplicate-lan'}];
      return tree;};
    value.ctx.wait = async (predicate, label) => {const tree = await value.ctx.tree(); assert(predicate(tree), label); return tree;};
    value.ctx.check = async (name, run) => {if (name.includes('native social entry')) await run();};
    await assert.rejects(specialWorldMenuActions(value.ctx, value.services)); assert.equal(value.samples.length, 0);
  }
  const modern = controlledNative({absentSocial: true});
  modern.ctx.check = async (name, run) => {if (name.includes('native social entry')) await run();};
  await assert.rejects(specialWorldMenuActions(modern.ctx, modern.services), /Actual native social/);
  assert.equal(modern.samples.length, 0);
});

test('an executing operation is not accepted as menu coverage and required sleeping-rule cleanup still runs', async () => {
  const runtime = controlledNative({pipelineRunning: true}); await assert.rejects(specialWorldMenuActions(runtime.ctx, runtime.services), /executing/);
  assert.equal(runtime.state().rule, 37); assert.equal(runtime.state().item, 'minecraft:air'); assert.equal(runtime.samples.length, 0);
});

test('an unknown occupied player hand is never replaced by diagnostic Arrange', async () => {
  const runtime = controlledNative({occupiedHand: true}); await assert.rejects(specialWorldMenuActions(runtime.ctx, runtime.services), /unknown nonempty/);
  assert.equal(runtime.commands.length, 0); assert.equal(runtime.inputs.some(call => call.path === '/v0/pipelines'), false);
});

test('Nautilus rebinds every slot click and readback from fresh semantic anchors when carried-stack revisions replace nodeIds', async () => {
  const value = controlledNative({changingSlotIds: true});
  value.ctx.check = async (name, run) => {if (name.includes('actual tamed Nautilus')) await run();};
  await specialWorldMenuActions(value.ctx, value.services);
  assert.deepEqual(value.slotInputs.map(node => node.slot), [29, 0, 0, 29]);
  assert.equal(new Set(value.slotInputs.map(node => node.nodeId)).size, 4);
  assert.equal(new Set(value.slotInputs.map(node => node.screenIdentity)).size, 1);
  assert.equal(value.samples.length, 1); assert.equal(value.samples[0].saddleEquippedAndReturned, true);
  assert.equal(value.state().saddle, false); assert.equal(value.state().carried, undefined);
  assert.deepEqual(value.samples[0].sourceAnchor, {slot: 29, slotOwner: 'player_inventory'});
  assert.deepEqual(value.samples[0].destinationAnchor, {slot: 0, slotOwner: 'menu'});
  assert.deepEqual(value.samples[0].slotActions.map(action => action.nodeId), value.slotInputs.map(action => action.nodeId));
  assert.match(value.commands.find(command => command.startsWith('summon ')), /Owner:\[I;0,0,0,1\]/);
  assert(value.commands.includes('fill -2 80 0 2 81 4 minecraft:stone'));
  assert(value.commands.includes('fill -1 80 1 1 81 3 minecraft:water'));
  assert(value.commands.includes('tp @s 0.5 82 0.5 0 0'));
  assert(value.commands.includes('fill -2 80 0 2 81 4 minecraft:air'));
  assert.deepEqual(value.samples[0].playerPositions.map(position => position.stage), ['after_arrange', 'menu_opened', 'saddle_equipped', 'saddle_returned']);
  for (const position of value.samples[0].playerPositions) assert.deepEqual(position,
    {stage: position.stage, dataCategory: 'USER', available: true, x: .5, y: 82, z: .5});
});

test('Nautilus stable-position readback rejects actual player drift after equipment without teleporting or retrying an Act', async () => {
  const value = controlledNative({changingSlotIds: true, driftAfterEquipment: true});
  value.ctx.check = async (name, run) => {if (name.includes('actual tamed Nautilus')) await run();};
  await assert.rejects(specialWorldMenuActions(value.ctx, value.services), /player left the isolated dry Nautilus standing position/);
  assert.equal(value.slotInputs.length, 2); assert.equal(value.samples.length, 1);
  const sample = value.samples[0]; assert.equal(sample.case, 'special_world_nautilus_failure');
  assert.equal(sample.playerPositions.at(-1).stage, 'saddle_equipped'); assert.equal(sample.playerPositions.at(-1).y, 72.197);
  assert.equal(sample.diagnostics.player.z, -4.559); assert.equal(sample.completedSlotActions.length, 2);
  assert.equal(value.commands.filter(command => command.startsWith('tp ')).length, 2,
    'Only the preexisting fixture reset and initial dry-pool Arrange may teleport');
  assert.equal(value.state().held, 0); assert.equal(value.state().screen, ''); assert.equal(value.state().nautilus, false);
});

test('Nautilus does not replay or change semantic anchors when a fresh USER slot, owner, uniqueness or Screen changes', async () => {
  for (const slotChangedAfterPickup of ['missing', 'owner', 'slot', 'duplicate', 'screen']) {
    const value = controlledNative({changingSlotIds: true, slotChangedAfterPickup});
    value.ctx.check = async (name, run) => {if (name.includes('actual tamed Nautilus')) await run();};
    await assert.rejects(specialWorldMenuActions(value.ctx, value.services), assert.AssertionError);
    assert.equal(value.slotInputs.length, 1, slotChangedAfterPickup); assert.equal(value.samples.length, 1);
    assert.equal(value.samples[0].case, 'special_world_nautilus_failure'); assert.equal(value.samples[0].verified, false);
    assert.equal(value.state().held, 0); assert.equal(value.state().screen, ''); assert.equal(value.state().nautilus, false);
  }
});

test('Nautilus failure records its latest public slot frame and one-shot USER positions before any cleanup input', async () => {
  const value = controlledNative({changingSlotIds: true, slotChangedAfterPickup: 'screen'}), events = [];
  value.ctx.check = async (name, run) => {if (name.includes('actual tamed Nautilus')) await run();};
  const originalApi = value.ctx.api, originalKey = value.ctx.key, originalCommand = value.ctx.command;
  value.ctx.api = async (method, path, body) => {
    const result = await originalApi(method, path, body);
    if (value.slotInputs.length && value.samples.length === 0) {
      assert.equal(method, 'GET'); assert.equal(value.state().nautilus, true, 'Position diagnostics must precede entity cleanup');
      events.push(path);
      if (path === '/v0/player?category=USER') return {...result, x: 4.5, y: 79, z: -1.5};
    }
    return result;
  };
  value.ctx.key = async code => {if (value.slotInputs.length) {events.push('cleanup_key'); assert.equal(value.samples[0].case, 'special_world_nautilus_failure');}
    return originalKey(code);};
  value.ctx.command = async text => {if (value.slotInputs.length) {events.push('cleanup_command'); assert.equal(value.samples[0].case, 'special_world_nautilus_failure');}
    return originalCommand(text);};
  await assert.rejects(specialWorldMenuActions(value.ctx, value.services), /same native Nautilus Screen/);
  assert.deepEqual(events.slice(0, 3), ['/v0/player?category=USER', '/v0/world/entities?category=USER&radius=8&limit=128', '/v0/input/state']);
  assert.equal(events[3], 'cleanup_key');
  const sample = value.samples[0]; assert.equal(sample.collectedBeforeCleanup, true); assert.equal(sample.verified, false);
  assert.equal(sample.lastSlotFrame.dataCategory, 'USER'); assert.equal(sample.lastSlotFrame.carriedStack.id, 'minecraft:saddle');
  assert.equal(sample.lastSlotFrame.screenIdentity, value.slotInputs[0].screenIdentity + 1);
  assert.deepEqual(sample.completedSlotActions.map(action => action.action), ['pickup']);
  assert.equal(sample.diagnostics.player.dataCategory, 'USER'); assert.equal(sample.diagnostics.player.x, 4.5);
  assert.equal(sample.diagnostics.entities.dataCategory, 'USER'); assert.equal(sample.diagnostics.entities.entities.length, 1);
  assert.equal(sample.diagnostics.entities.entities[0].type, 'minecraft:nautilus'); assert.equal(sample.diagnostics.entities.entities[0].z, 2.5);
  assert.equal(sample.diagnostics.input.pressedKeyCount, 0); assert.equal(sample.diagnosticFailure, undefined);
  assert.equal(value.slotInputs.length, 1, 'Diagnostics add no slot input or replay');
  assert.equal(value.state().nautilus, false); assert.equal(value.state().screen, '');
});

test('failed or non-USER diagnostics never replace the original Nautilus error or expose another category', async () => {
  const value = controlledNative({changingSlotIds: true}), primary = new Error('original slot response lost');
  value.ctx.check = async (name, run) => {if (name.includes('actual tamed Nautilus')) await run();};
  const originalUi = value.ctx.ui, originalApi = value.ctx.api, diagnostics = [];
  value.ctx.ui = async (...args) => {const result = await originalUi(...args); if (value.slotInputs.length === 1) throw primary; return result;};
  value.ctx.api = async (method, path, body) => {
    if (value.slotInputs.length && value.samples.length === 0) {
      diagnostics.push(path); assert.equal(method, 'GET');
      if (path === '/v0/player?category=USER') throw new Error('USER player unavailable');
      if (path.startsWith('/v0/world/entities?')) return {dataCategory: 'DEBUG', entities: [{uuid: 'private-diagnostic-data'}]};
    }
    return originalApi(method, path, body);
  };
  await assert.rejects(specialWorldMenuActions(value.ctx, value.services), error => error === primary);
  assert.deepEqual(diagnostics, ['/v0/player?category=USER', '/v0/world/entities?category=USER&radius=8&limit=128', '/v0/input/state']);
  const sample = value.samples[0]; assert.equal(sample.primaryError.message, primary.message);
  assert.deepEqual(sample.diagnosticFailure.map(failure => failure.part), ['player', 'entities']);
  assert.equal(sample.diagnostics.player, undefined); assert.equal(sample.diagnostics.entities, undefined);
  assert.equal(sample.diagnostics.input.pressedKeyCount, 0); assert.equal(sample.completedSlotActions.length, 0);
  assert(!JSON.stringify(sample).includes('private-diagnostic-data')); assert.equal(value.slotInputs.length, 1);
});

test('semantic equipment selection uses unique USER view slots and real backing owners without DEBUG slot indices', () => {
  const nodes = value => value.children;
  const value = {screenClass: 'net.minecraft.client.gui.screens.inventory.NautilusInventoryScreen', dataCategory: 'USER', treeTruncated: false,
    children: [{role: 'slot', slot: 0, slotOwner: 'menu', item: 'minecraft:air', count: 0, visible: true, active: true, actions: ['click']},
      {role: 'slot', slot: 29, slotOwner: 'player_inventory', item: 'minecraft:saddle', count: 1, visible: true, active: true, actions: ['click']}]};
  assert.equal(nativeSaddleSlots(value, nodes).source.slot, 29);
  for (const change of [copy => {copy.children[0].slotOwner = 'player_inventory';}, copy => {copy.children[1].slot = 0;},
    copy => {copy.children[1].visible = false;}, copy => {copy.children[1].count = 2;}, copy => {copy.screenClass = 'HorseInventoryScreen';}]) {
    const copy = structuredClone(value); change(copy); assert.throws(() => nativeSaddleSlots(copy, nodes));
  }
  assert.deepEqual(sleepingRule('1.20.1-forge'), {command: 'playersSleepingPercentage', key: 'gamerule.playersSleepingPercentage'});
  assert.deepEqual(sleepingRule('26.3-neoforge'), {command: 'minecraft:players_sleeping_percentage', key: 'gamerule.minecraft.players_sleeping_percentage'});
  assert.throws(() => sleepingRule('unregistered'));
});

function ruleLookup({target = '26.3-neoforge', noSearch = false, duplicateSearch = false, duplicateRule = false,
  missingRule = false, ruleId = 'players_sleeping_percentage', ruleValue = '37', hiddenDecoy = false,
  legacyTranslation = false, searchError, searchLabel = ''} = {}) {
  let query = ''; const calls = [];
  const search = () => ({nodeId: 'search', role: 'text_field', class: 'net.minecraft.client.gui.components.EditBox',
    label: searchLabel, visible: true, active: true, value: query, actions: ['type_text']});
  const rule = (nodeId = 'actual') => ({nodeId, role: 'text_field', label: 'Sleep percentage\n' + ruleId + '\n',
    ...(legacyTranslation ? {translationKey: 'gamerule.playersSleepingPercentage'} : {}),
    value: ruleValue, visible: true, active: true, actions: ['type_text']});
  const ctx = {target, nodes: value => value.children ?? [], tree: async () => ({dataCategory: 'USER',
    screenClass: 'net.minecraft.client.gui.screens.options.InWorldGameRulesScreen', screenIdentity: 9,
    treeTruncated: false, overlayIdentity: 0, children: [
      ...(!noSearch ? [search(), ...(duplicateSearch ? [{...search(), nodeId: 'second-search'}] : [])] : []),
      ...(hiddenDecoy ? [{...rule('hidden'), visible: false, value: '999', actions: ['scroll_into_view']}] : []),
      ...(!missingRule && (noSearch || query === 'sleep') ? [rule(), ...(duplicateRule ? [rule('duplicate')] : [])] : [])]}),
    ui: async (selector, action, extra) => {calls.push({selector, action, extra});
      assert.deepEqual(selector, {nodeId: 'search'}); assert.equal(action, 'type_text'); assert.deepEqual(extra, {text: 'sleep'});
      if (searchError) throw searchError; query = extra.text;},
    poll: async (read, predicate, label) => {for (let index = 0; index < 3; index++) {const value = await read(); if (predicate(value)) return value;} throw new Error(label);}};
  return {ctx, calls};
}

test('native empty-message Search filters by sleep and reads only the exact visible multiline rule id', async () => {
  const value = ruleLookup({hiddenDecoy: true}); const result = await readVisibleSleepingRule(value.ctx);
  assert.equal(result.value, 37); assert.equal(result.nodeId, 'actual'); assert.equal(result.translationKey, undefined);
  assert.equal(result.ruleId, 'minecraft:players_sleeping_percentage'); assert.equal(result.search.query, 'sleep');
  assert.equal(result.search.value, 'sleep'); assert.equal(value.calls.length, 1);
  const namespace = ruleLookup({ruleId: 'minecraft:players_sleeping_percentage', searchLabel: 'Search...'});
  assert.equal((await readVisibleSleepingRule(namespace.ctx)).value, 37);
});

test('an older visible exact native translation remains readable without inventing Search or hidden scrolling', async () => {
  const value = ruleLookup({target: '1.20.1-forge', noSearch: true, legacyTranslation: true, ruleId: 'playersSleepingPercentage'});
  const result = await readVisibleSleepingRule(value.ctx); assert.equal(result.value, 37);
  assert.equal(result.search, undefined); assert.equal(value.calls.length, 0);
});

test('ambiguous Search or rule, wrong id, missing value and invalid numbers fail with a bounded current USER summary', async () => {
  for (const options of [{duplicateSearch: true}, {duplicateRule: true}, {ruleId: 'different_sleep_rule'},
    {missingRule: true}, {ruleValue: '1.5'}, {ruleValue: '9007199254740992'}]) {
    const value = ruleLookup(options);
    await assert.rejects(readVisibleSleepingRule(value.ctx), error => /Native sleeping rule USER lookup failed/.test(error.message)
      && error.userRuleLookup.dataCategory === 'USER' && error.userRuleLookup.fields.length <= 12);
    assert(value.calls.length <= 1, JSON.stringify(options));
    assert(value.calls.every(call => call.selector.nodeId === 'search' && call.action === 'type_text'));
  }
});

test('a cancelled Search input is neither retried nor replaced with scroll, coordinates or DEBUG', async () => {
  const failure = Object.assign(new Error('search input cancelled'), {code: 'REQUEST_CANCELLED'});
  const value = ruleLookup({searchError: failure});
  await assert.rejects(readVisibleSleepingRule(value.ctx), error => error.cause === failure && error.userRuleLookup.fields[0].nodeId === 'search');
  assert.equal(value.calls.length, 1);
});

const queryDraw = (label, y, extra = {}) => ({renderPhase: 'chat_background', role: 'hud_text', label,
  visible: true, active: false, actions: [], boundsAvailable: true, x: 10, y, width: 320, height: 9, opacity: 1,
  labelSource: 'last_completed_native_gui_text_draw', iconSource: 'last_completed_native_gui_draw',
  textPhase: 'drawn', boundsSemantics: 'native_text_draw_viewport_scissor_intersection', ...extra});
const queryView = children => ({dataCategory: 'USER', screenClass: '', overlayIdentity: 0, width: 640, height: 360,
  clientTick: 1, presentation: {children}});

test('legacy query parsing requires its unique actually drawn marker before the exact fresh response, including bounded native wrapping', () => {
  const marker = 'unique current marker', text = 'Gamerule playersSleepingPercentage is currently set to: 37';
  const value = queryView([queryDraw(text.replace('37', '999'), 80), queryDraw(marker, 100), queryDraw(text, 110)]);
  assert.equal(legacySleepingRuleDraw(value, marker).value, 37);
  const wrapped = queryView([queryDraw(marker, 100), queryDraw('Gamerule playersSleepingPercentage', 110),
    queryDraw('is currently set to: 37', 120)]);
  assert.equal(legacySleepingRuleDraw(wrapped, marker).value, 37);
  for (const rows of [
    [queryDraw(text, 80), queryDraw(marker, 100)],
    [queryDraw(marker, 100), queryDraw(text, 110), queryDraw(text, 120)],
    [queryDraw(marker, 90), queryDraw(marker, 100), queryDraw(text, 110)],
    [queryDraw(marker, 100), queryDraw(text, 110, {labelSource: 'cached_chat_history'})],
    [queryDraw(marker, 100), queryDraw('', 110, {textPhase: 'withheld_partial_or_occluded'})],
    [queryDraw(marker, 100), queryDraw(text.replace('37', '9007199254740992'), 110)],
  ]) assert.throws(() => legacySleepingRuleDraw(queryView(rows), marker));
});

test('the legacy reader parses only native draw, ignores command result values and never replays a missing response', async () => {
  for (const missingResponse of [false, true]) {
    const calls = [], rows = []; let tick = 0;
    const ctx = {target: '1.20.1-forge', tree: async () => ({...queryView(rows.map((label, index) => queryDraw(label, 100 + index * 10))), clientTick: ++tick}),
      command: async text => {
        calls.push(text);
        if (text.startsWith('tellraw @s ')) rows.push(JSON.parse(text.slice('tellraw @s '.length)).text);
        else {assert.equal(text, 'gamerule playersSleepingPercentage');
          if (!missingResponse) rows.push('Gamerule playersSleepingPercentage is currently set to: 37');}
        return {value: 999, actualRuleValue: 999};
      },
      poll: async (read, predicate, label) => {for (let index = 0; index < 3; index++) {const value = await read(); if (predicate(value)) return value;} throw new Error(label);}};
    if (missingResponse) await assert.rejects(readLegacySleepingRule(ctx), error => /Native sleeping rule chat read failed/.test(error.message)
      && error.userRuleLookup.rows.length === 1);
    else {const result = await readLegacySleepingRule(ctx); assert.equal(result.value, 37);
      assert.equal(result.querySubmission.value, 999); assert.equal(result.commandSubmissionIsNotReadback, true);}
    assert.equal(calls.length, 2); assert.equal(calls.filter(value => value === 'gamerule playersSleepingPercentage').length, 1);
  }
});

test('legacy special-bed scenario reads original/change/restored values from USER chat and uses no nonexistent rule GUI', async () => {
  for (const target of ['1.20.1-forge', '1.21.1-neoforge']) {
    const run = controlledNative({target}); run.ctx.check = async (name, act) => {if (name.includes('actual bed use')) await act();};
    await specialWorldMenuActions(run.ctx, run.services); assert.equal(run.samples.length, 1);
    const sample = run.samples[0]; assert.equal(sample.originalRule.source, 'actual_native_chat_draw');
    assert.equal(sample.originalRule.value, 37); assert.equal(sample.restoredRule.value, 37);
    assert.equal(sample.restoredRule.commandSubmissionIsNotReadback, true); assert.equal(run.state().rule, 37);
    assert.equal(run.state().screen, ''); assert.equal(run.state().held, 0);
    assert.equal(run.commands.filter(text => text === 'gamerule playersSleepingPercentage').length, 3);
  }
});
