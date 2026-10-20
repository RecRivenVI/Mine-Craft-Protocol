import assert from 'node:assert/strict';
import test from 'node:test';
import {readFile} from 'node:fs/promises';
import {playerListActions, validateNativePlayerList} from './player-list-actions.mjs';

test('Forge probe uses the actual legacy packet objective-before-owner constructor order', async () => {
  const code = await readFile(new URL('../../../../versions/1.20.1-forge/src/probe/java/io/github/recrivenvi/minecraftprotocol/probe/validation/UiPlayerListFixtureScreen.java', import.meta.url), 'utf8');
  const ordered = value => assert.match(value, /new ClientboundSetScorePacket\(\s*ServerScoreboard\.Method\.CHANGE,\s*objectiveName,\s*entry\.profileName,/);
  ordered(code);
  const swapped = code.replace(/objectiveName,\s*entry\.profileName,/, 'entry.profileName, objectiveName,');
  assert.notEqual(swapped, code);
  assert.throws(() => ordered(swapped));
});

function fixture(native = {}) {
  const proof = {probeOnly: true, arrangeAuthority: 'DIAGNOSTIC_local_client_packet_fixture', fixtureId: 'same-owned-local-fixture',
    target: '26.3-neoforge', processId: 7, sequence: 1, mode: 'roster', installed: true, cleaned: false,
    actualMultiplayerNetworkValidated: false, serverPlayersCreated: 0, syntheticListed: 82, syntheticUnlisted: 1,
    ...native,
    expectedVisibleRows: Array.from({length: 80}, (_, index) => ({displayName: 'Visible ' + index,
      scoreVisible: index % 9 !== 0, pingSprite: 'minecraft:icon/ping_' + ['unknown', '5', '4', '3', '2', '1'][index % 6]}))};
  if (native.nativePingFormat === 'gui_icons_atlas') for (const [index, row] of proof.expectedVisibleRows.entries()) {
    delete row.pingSprite;
    row.pingAtlas = {textureId: 'minecraft:textures/gui/icons.png', textureU: 0,
      textureV: 176 + [5, 0, 1, 2, 3, 4][index % 6] * 8, width: 10, height: 8};
  }
  if (native.nativeListOrderSupported === false) Object.assign(proof,
    {originalPlayerListed: true, localPlayerTemporarilyUnlisted: true, localPlayerListRestored: false});
  return proof;
}

const nativePorts = [
  {target: '1.20.1-forge', nativeNumberFormatsSupported: false, nativeListOrderSupported: false, nativePingFormat: 'gui_icons_atlas'},
  {target: '1.21.1-neoforge', nativeNumberFormatsSupported: true, nativeListOrderSupported: false, nativePingFormat: 'resource_sprite'},
  {target: '26.1.2-neoforge', nativeNumberFormatsSupported: true, nativeListOrderSupported: true, nativePingFormat: 'resource_sprite'},
  {target: '26.3-fabric', nativeNumberFormatsSupported: true, nativeListOrderSupported: true, nativePingFormat: 'resource_sprite'},
];

function drawnView(proof, kind = proof.mode) {
  const node = (key, label, x, y, width, height, role = 'hud_text') => ({nodeId: key, controlKey: key, label,
    x, y, width, height, role, boundsAvailable: true, visible: true, active: false, actions: [], opacity: 1,
    labelSource: 'last_completed_native_player_list_draw', iconSource: 'last_completed_native_player_list_draw', textPhase: 'drawn'});
  const children = proof.expectedVisibleRows.map((expected, index) => {
    const key = 'hud.playerlist.row.' + index, x = 16 + Math.floor(index / 20) * 150, y = 20 + index % 20 * 10;
    const row = {...node(key, expected.displayName, x, y, 60, 9), entryIndex: index, children: []};
    if (expected.scoreVisible && ['fixed', 'numeric', 'fallback'].includes(kind))
      row.children.push(node(key + '.part.0', kind === 'fixed' ? 'VISIBLE SCORE' : kind === 'numeric' ? '8675309' : '1000.0hp', x + 61, y, 40, 9));
    if (expected.scoreVisible && kind === 'hearts') for (const [glyphIndex, glyph] of ['container', 'full', 'full_blinking'].entries()) {
      const icon = node(key + '.part.' + row.children.length, '', x + 61, y, 9, 9, 'status_icon');
      row.children.push(expected.pingAtlas ? {...icon, textureId: expected.pingAtlas.textureId,
        textureU: [16, 52, 70][glyphIndex], textureV: 0, atlasWidth: 256, atlasHeight: 256, spriteSemantics: 'native_gui_icons_atlas_region'}
        : {...icon, resourceId: 'minecraft:hud/heart/' + glyph});
    }
    const ping = node(key + '.part.' + row.children.length, '', x + 110, y, 10, 8, 'status_icon');
    row.children.push(expected.pingAtlas ? {...ping, ...expected.pingAtlas, atlasWidth: 256, atlasHeight: 256, spriteSemantics: 'native_gui_icons_atlas_region'}
      : {...ping, resourceId: expected.pingSprite});
    return row;
  });
  for (const [part, text, y] of [['header', 'Local roster header', 1], ['footer', 'Local roster footer', 230]]) {
    const key = 'hud.playerlist.' + part;
    children.push({...node(key, text, 10, y, 100, 9), children: [node(key + '.part.0', text, 10, y, 100, 9)]});
  }
  return {dataCategory: 'USER', screenClass: '', overlayIdentity: 0, width: 640, height: 480,
    treeTruncated: false, children: [], presentation: {children}};
}

test('validates the actual row/part representation for all finite native score kinds', () => {
  for (const kind of ['roster', 'fixed', 'blank', 'hearts', 'fallback']) {
    const proof = fixture(), result = validateNativePlayerList(drawnView(proof, kind), proof, kind);
    assert.equal(result.rows, 80); assert.equal(result.pingSprites.length, 6);
    assert.equal(result.actualMultiplayerNetworkValidated, false);
  }
});

test('missing native rows, wrong sorting, raw private fields, stale text and computed icons cannot pass', () => {
  for (const corrupt of [
    view => view.presentation.children.splice(0, 1),
    view => view.presentation.children.reverse(),
    view => { view.presentation.children[0].label = 'ui_hidden_000'; },
    view => { view.presentation.children[0].latency = 0; },
    view => { view.presentation.children[0].children[0].score = 8675309; },
    view => { view.presentation.children[0].children[0].resourceId = 'minecraft:icon/ping_3'; },
    view => { view.presentation.children[0].iconSource = 'computed_from_raw_latency'; },
    view => { view.presentation.children[0].children[0].x = 900; },
    view => { view.presentation.children[80].labelSource = 'uncaptured_header_field'; },
  ]) {
    const proof = fixture(), view = drawnView(proof); corrupt(view);
    assert.throws(() => validateNativePlayerList(view, proof));
  }
});

test('blank, spectator, heart and fallback branches each require their actual drawn representation', () => {
  const proof = fixture();
  assert.throws(() => validateNativePlayerList(drawnView(proof, 'fixed'), proof, 'blank'));
  assert.throws(() => validateNativePlayerList(drawnView(proof, 'blank'), proof, 'fixed'));
  assert.throws(() => validateNativePlayerList(drawnView(proof, 'blank'), proof, 'hearts'));
  assert.throws(() => validateNativePlayerList(drawnView(proof, 'hearts'), proof, 'fallback'));
  const spectatorLeak = drawnView(proof, 'fixed');
  spectatorLeak.presentation.children[0].children.push({...spectatorLeak.presentation.children[1].children[0]});
  assert.throws(() => validateNativePlayerList(spectatorLeak, proof, 'fixed'));
});

test('a partially viewport-clipped native score is withheld, never recovered from its format or raw value', () => {
  const proof = fixture(), view = drawnView(proof, 'fixed');
  const part = view.presentation.children[1].children[0];
  part.textPhase = 'withheld_partial_or_occluded'; part.label = '';
  assert.equal(validateNativePlayerList(view, proof, 'fixed').withheldFixedScores, 1);
  part.label = 'VISIBLE SCORE'; assert.throws(() => validateNativePlayerList(view, proof, 'fixed'));
  part.label = ''; part.textPhase = 'unknown'; assert.throws(() => validateNativePlayerList(view, proof, 'fixed'));
  for (const row of view.presentation.children) for (const node of row.children ?? []) if (node.role === 'hud_text') {
    node.textPhase = 'withheld_partial_or_occluded'; node.label = '';
  }
  assert.throws(() => validateNativePlayerList(view, proof, 'fixed'));
  const fallback = drawnView(proof, 'fallback');
  const fragment = fallback.presentation.children[1].children[0];
  fragment.textPhase = 'withheld_partial_or_occluded'; fragment.label = '';
  assert.equal(validateNativePlayerList(fallback, proof, 'fallback').withheldFallbackScores, 1);
  fragment.label = '1000.0hp'; assert.throws(() => validateNativePlayerList(fallback, proof, 'fallback'));
  fragment.label = ''; fragment.textPhase = 'unobserved'; assert.throws(() => validateNativePlayerList(fallback, proof, 'fallback'));
  for (const row of fallback.presentation.children) for (const node of row.children ?? []) if (node.role === 'hud_text') {
    node.textPhase = 'withheld_partial_or_occluded'; node.label = '';
  }
  assert.throws(() => validateNativePlayerList(fallback, proof, 'fallback'));
});

function runtime({missingDraw = false, foreignOwner = false, native = {}, restoreListedFailed = false} = {}) {
  const calls = [], checks = [], samples = []; let proof = {...fixture(native), sequence: 0, mode: 'uninstalled', installed: false};
  let screen = '', held = 0, hidden = false, clock = 0;
  const scoreButtons = native.nativeNumberFormatsSupported === false ? [['Numeric Score (Fixture)', 'numeric']]
    : [['Fixed Score (Fixture)', 'fixed'], ['Blank Score (Fixture)', 'blank']];
  scoreButtons.push(['Heart Score (Fixture)', 'hearts'], ['Heart Fallback (Fixture)', 'fallback']);
  const labels = ['Install Local Roster (Fixture)', ...scoreButtons.map(([label]) => label),
    'View Player List (Fixture)', 'Restore Local Roster (Fixture)'];
  const value = () => {
    const current = drawnView(proof); current.screenClass = screen;
    if (screen || !held || hidden || !proof.installed || proof.cleaned || missingDraw) current.presentation.children = [];
    current.children = screen.endsWith('UiPlayerListFixtureScreen') ? labels.map(label => ({nodeId: label, label, role: 'button', active: true, visible: true, actions: ['click']})) : [];
    return current;
  };
  const ctx = {repo: '.', target: proof.target, samples, tree: async () => value(), nodes: current => current.children,
    now: () => clock, sleep: async delay => { clock += delay; },
    wait: async (predicate, label) => { const current = value(); assert(predicate(current), label); return current; },
    openFixture: async () => { screen = 'io.github.recrivenvi.minecraftprotocol.probe.validation.UiPlayerListFixtureScreen'; },
    readFixtureProof: async () => structuredClone(foreignOwner && proof.installed ? {...proof, fixtureId: 'foreign-owned-local-fixture'} : proof),
    check: async (name, run) => { await run(); checks.push(name); },
    key: async code => {
      calls.push({key: code}); held = 0;
      if (code === 290) hidden = !hidden;
      else if (code === 69) screen = 'net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen';
      else if (code === 256) screen = '';
      else assert.fail('Unexpected key ' + code);
    },
    ui: async selector => {
      assert.deepEqual(Object.keys(selector), ['nodeId']); calls.push({selector});
      const label = selector.nodeId;
      if (label === labels[0]) { proof.installed = true; proof.mode = 'roster'; proof.sequence++; }
      else if (label === 'View Player List (Fixture)') screen = '';
      else if (label === 'Restore Local Roster (Fixture)') {
        proof.cleaned = true; proof.mode = 'restored'; proof.sequence++;
        if (proof.nativeListOrderSupported === false) {
          proof.localPlayerTemporarilyUnlisted = false; proof.localPlayerListRestored = !restoreListedFailed;
        }
      } else { const selected = scoreButtons.find(([candidate]) => candidate === label); assert(selected); proof.mode = selected[1]; proof.sequence++; }
    },
    api: async (method, path, body) => {
      calls.push({method, path, body});
      if (path === '/v0/input/state') return {pressedKeyCount: held, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false};
      if (path === '/v0/input/key') { assert.equal(method, 'POST'); assert.equal(body.key, 258); held = body.action; return {accepted: true}; }
      if (path === '/v0/input/release-all') { held = 0; return {released: true}; }
      assert.fail('Unexpected API ' + path);
    }};
  return {ctx, calls, checks, samples, state: () => ({proof, screen, held, hidden})};
}

test('the actual helper drives all native branches and independently confirms normal visibility and cleanup', async () => {
  const run = runtime(); await playerListActions(run.ctx);
  assert.equal(run.checks.length, 3); assert.equal(run.samples.length, 7);
  assert.deepEqual(run.samples.map(sample => sample.case), ['player_list_roster', 'player_list_visibility', 'player_list_fixed',
    'player_list_blank', 'player_list_hearts', 'player_list_fallback', 'player_list_cleanup']);
  assert.equal(run.state().proof.cleaned, true); assert.equal(run.state().screen, ''); assert.equal(run.state().held, 0);
  assert(run.calls.some(call => call.path === '/v0/input/release-all'));
  assert(!run.calls.some(call => /acquire|reconsent|debug|command/.test(call.path ?? '')));
});

test('missing actual draws fail and still remove only the owned local fixture', async () => {
  const run = runtime({missingDraw: true});
  await assert.rejects(playerListActions(run.ctx), /actual native player-list roster draw/);
  assert.equal(run.checks.length, 0); assert.equal(run.state().proof.cleaned, true);
  assert(!run.samples.some(sample => sample.case === 'player_list_roster'));
});

test('a changed fixture owner is not accepted or overwritten during recovery', async () => {
  const run = runtime({foreignOwner: true}); await assert.rejects(playerListActions(run.ctx), /another roster fixture/);
  assert.equal(run.checks.length, 0); assert(!run.calls.some(call => call.selector?.nodeId === 'Restore Local Roster (Fixture)'));
  assert.equal(run.state().held, 0);
});

test('all four native ports validate their actual formats, icon representation and supplied ordering', () => {
  for (const native of nativePorts) {
    const proof = fixture(native);
    proof.expectedVisibleRows.reverse();
    for (const kind of native.nativeNumberFormatsSupported ? ['roster', 'fixed', 'blank', 'hearts', 'fallback']
      : ['roster', 'numeric', 'hearts', 'fallback']) {
      const summary = validateNativePlayerList(drawnView(proof, kind), proof, kind);
      assert.equal(summary.rows, 80);
      assert.deepEqual(summary.displayedNames, proof.expectedVisibleRows.map(row => row.displayName));
      assert.equal(summary.nativeFeatures.listOrder, native.nativeListOrderSupported);
      assert.equal(summary.nativeFeatures.numberFormats, native.nativeNumberFormatsSupported);
      assert.equal(summary.pingSprites.length, native.nativePingFormat === 'resource_sprite' ? 6 : 0);
      assert.equal(summary.pingAtlasRegions.length, native.nativePingFormat === 'gui_icons_atlas' ? 6 : 0);
      if (kind === 'numeric') assert(summary.fullyDrawnNumericScores > 0);
    }
  }
});

test('non-reference fixtures cannot infer missing native facts from their target name', () => {
  for (const native of nativePorts) for (const field of ['nativeNumberFormatsSupported', 'nativeListOrderSupported', 'nativePingFormat']) {
    const proof = fixture(native), view = drawnView(proof);
    delete proof[field]; assert.throws(() => validateNativePlayerList(view, proof), /native fixture/);
  }
  const reference = fixture();
  assert.equal(validateNativePlayerList(drawnView(reference), reference).nativeFeatures.numberFormats, true);
  reference.nativeNumberFormatsSupported = 'true';
  assert.throws(() => validateNativePlayerList(drawnView(reference), reference), /number-format support/);
});

test('a real viewport-clipped legacy heart keeps only its visible geometry and cannot invent a full glyph', () => {
  const proof = fixture(nativePorts[0]);
  const clipped = () => {
    const view = drawnView(proof, 'hearts');
    const part = view.presentation.children.flatMap(row => row.children ?? []).find(node => node.textureV === 0);
    Object.assign(part, {x: view.width - 6, width: 6, textPhase: 'withheld_partial_or_occluded', label: ''});
    return {view, part};
  };
  assert.equal(validateNativePlayerList(clipped().view, proof, 'hearts').kind, 'hearts');
  for (const mutate of [
    part => part.x--, part => part.textPhase = 'drawn', part => part.label = 'hidden glyph',
    part => part.width = 10, part => part.height = 10, part => part.textureU = 99,
  ]) {
    const {view, part} = clipped(); mutate(part);
    assert.throws(() => validateNativePlayerList(view, proof, 'hearts'));
  }
});

test('actual atlas ping and heart geometry cannot be replaced by invented sprites or regions', () => {
  const proof = fixture(nativePorts[0]);
  for (const corrupt of [
    view => { view.presentation.children[0].children[0].textureV = 200; },
    view => { view.presentation.children[0].children[0].textureId = 'minecraft:missing'; },
    view => { view.presentation.children[0].children[0].resourceId = 'minecraft:icon/ping_unknown'; },
    view => { view.presentation.children[0].children[0].atlasWidth = 255; },
    view => { view.presentation.children[0].children[0].spriteSemantics = 'computed'; },
    view => { view.presentation.children[0].children[0].iconSource = 'reconstructed_from_latency'; },
  ]) { const view = drawnView(proof); corrupt(view); assert.throws(() => validateNativePlayerList(view, proof)); }
  const hearts = drawnView(proof, 'hearts');
  for (const row of hearts.presentation.children) for (const part of row.children ?? []) if (part.textureV === 0) part.textureU = 99;
  assert.throws(() => validateNativePlayerList(hearts, proof, 'hearts'), /heart glyphs/);
  assert.throws(() => validateNativePlayerList(drawnView(proof, 'fixed'), proof, 'fixed'), /unavailable native number formats/);
  assert.throws(() => validateNativePlayerList(drawnView(proof, 'blank'), proof, 'blank'), /unavailable native number formats/);
});

test('native numeric text is permitted only in its actually drawn score parts, never private metadata', () => {
  const proof = fixture(nativePorts[0]);
  for (const corrupt of [
    view => { view.presentation.children[1].children[0].label = '8675308'; },
    view => { view.presentation.children[1].children[0].score = 8675309; },
    view => { view.presentation.children[1].children[0].uuid = '2db84700-2587-4877-83df-21d3d9550553'; },
    view => { view.presentation.children[1].children[0].profileName = 'ui_hidden_001'; },
    view => { view.presentation.children[1].children[0].extra = '8675309'; },
    view => { view.presentation.children[0].children.push({...view.presentation.children[1].children[0]}); },
  ]) { const view = drawnView(proof, 'numeric'); corrupt(view); assert.throws(() => validateNativePlayerList(view, proof, 'numeric')); }
  const view = drawnView(proof, 'numeric'), part = view.presentation.children[1].children[0];
  part.label = ''; part.textPhase = 'withheld_partial_or_occluded';
  assert.equal(validateNativePlayerList(view, proof, 'numeric').withheldNumericScores, 1);
  part.label = '8675309'; assert.throws(() => validateNativePlayerList(view, proof, 'numeric'));
  part.label = '';
  for (const row of view.presentation.children) for (const child of row.children ?? []) if (child.role === 'hud_text') {
    child.label = ''; child.textPhase = 'withheld_partial_or_occluded';
  }
  assert.throws(() => validateNativePlayerList(view, proof, 'numeric'), /all actually visible native numeric text/);
  const blankProof = fixture(nativePorts[1]), blank = drawnView(blankProof, 'blank');
  blank.presentation.children[1].children.unshift(drawnView(blankProof, 'numeric').presentation.children[1].children[0]);
  assert.throws(() => validateNativePlayerList(blank, blankProof, 'blank'), /Blank formatting/);
});

test('the actual helper runs every port using fixture facts and restores the old native listed state', async () => {
  for (const native of nativePorts) {
    const run = runtime({native}); await playerListActions(run.ctx);
    assert.equal(run.checks.length, 3); assert.equal(run.state().proof.cleaned, true);
    assert.equal(run.state().held, 0); assert.equal(run.state().screen, '');
    const kinds = run.samples.filter(sample => sample.nativeFeatures).map(sample => sample.kind);
    assert.deepEqual(kinds, native.nativeNumberFormatsSupported ? ['roster', 'fixed', 'blank', 'hearts', 'fallback']
      : ['roster', 'numeric', 'hearts', 'fallback']);
    assert.equal(run.samples.length, native.nativeNumberFormatsSupported ? 7 : 6);
    if (!native.nativeNumberFormatsSupported) assert(!run.calls.some(call => /Fixed|Blank/.test(call.selector?.nodeId ?? '')));
    if (!native.nativeListOrderSupported) {
      assert.equal(run.state().proof.localPlayerListRestored, true);
      assert.equal(run.samples.at(-1).fixture.localPlayerListRestored, true);
    }
    assert(!run.calls.some(call => /acquire|reconsent|debug|command/.test(call.path ?? '')));
  }
});

test('failure to restore the original listed state is an actual helper cleanup failure', async () => {
  const run = runtime({native: nativePorts[0], restoreListedFailed: true});
  await assert.rejects(playerListActions(run.ctx), error => error instanceof AggregateError
    && error.errors.some(cause => /original local player listed state/.test(cause.message)));
  assert.equal(run.state().held, 0);
  assert(!run.samples.some(sample => sample.case === 'player_list_cleanup'));
});

test('changing native fixture facts mid-run does not downgrade its validation branch', async () => {
  const run = runtime(), read = run.ctx.readFixtureProof;
  run.ctx.readFixtureProof = async () => { const value = await read(); if (value.installed) value.nativeNumberFormatsSupported = false; return value; };
  await assert.rejects(playerListActions(run.ctx), /capabilities must remain stable/);
  assert.equal(run.checks.length, 0); assert.equal(run.state().held, 0);
  assert(!run.calls.some(call => call.selector?.nodeId === 'Numeric Score (Fixture)'));
});
