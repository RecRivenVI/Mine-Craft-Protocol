import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';

const rowKey = /^hud\.playerlist\.row\.\d+$/;
const pingKey = /^minecraft:icon\/ping_(?:unknown|[1-5])$/;
const atlasTexture = 'minecraft:textures/gui/icons.png';
const heartAtlasU = new Set([16, 25, 52, 61, 70, 79, 160, 169]);
const pause = ms => new Promise(done => setTimeout(done, ms));

function nativeFeatures(proof) {
  const reference = proof.target === '26.3-neoforge';
  const numberFormats = proof.nativeNumberFormatsSupported ?? (reference ? true : undefined);
  assert.equal(typeof numberFormats, 'boolean', 'The native fixture must explicitly report number-format support');
  if (!reference || proof.nativeListOrderSupported !== undefined)
    assert.equal(typeof proof.nativeListOrderSupported, 'boolean', 'The native fixture must explicitly report list-order support');
  const pingFormat = proof.nativePingFormat ?? (reference && proof.expectedVisibleRows?.every(row => pingKey.test(row.pingSprite ?? '')) ? 'resource_sprite' : undefined);
  assert(['resource_sprite', 'gui_icons_atlas'].includes(pingFormat), 'The native fixture must report its actual ping representation');
  if (proof.nativeListOrderSupported === false) {
    assert.equal(typeof proof.originalPlayerListed, 'boolean');
    assert.equal(typeof proof.localPlayerTemporarilyUnlisted, 'boolean');
    assert.equal(typeof proof.localPlayerListRestored, 'boolean');
  }
  return {numberFormats, pingFormat, listOrder: proof.nativeListOrderSupported ?? null,
    localPlayerListRestorationRequired: proof.nativeListOrderSupported === false};
}

function nativePingAtlas(node) {
  return node.role === 'status_icon' && node.textureId === atlasTexture && node.textureU === 0
    && Number.isInteger(node.textureV) && node.textureV >= 176 && node.textureV <= 216
    && (node.textureV - 176) % 8 === 0 && node.width === 10 && node.height === 8;
}

function nativeHeartAtlas(node, view) {
  return node.role === 'status_icon' && node.textureId === atlasTexture && heartAtlasU.has(node.textureU)
    && node.textureV === 0 && (node.width === 9 && node.height === 9
      || view && node.textPhase === 'withheld_partial_or_occluded' && node.label === ''
        && node.width > 0 && node.width <= 9 && node.height > 0 && node.height <= 9
        && (node.x === 0 || node.y === 0 || node.x + node.width === view.width || node.y + node.height === view.height));
}

export function validateNativePlayerList(view, proof, kind = 'roster') {
  const features = nativeFeatures(proof);
  assert(['roster', 'fixed', 'blank', 'numeric', 'hearts', 'fallback'].includes(kind));
  if (kind === 'fixed' || kind === 'blank') assert.equal(features.numberFormats, true, 'Do not invent unavailable native number formats');
  if (kind === 'numeric') assert.equal(features.numberFormats, false, 'Use the fixture-declared native numeric branch');
  assert.equal(view.dataCategory, 'USER'); assert.equal(view.screenClass, ''); assert.equal(view.overlayIdentity, 0);
  assert.equal(proof.probeOnly, true); assert.equal(proof.arrangeAuthority, 'DIAGNOSTIC_local_client_packet_fixture');
  assert.equal(proof.actualMultiplayerNetworkValidated, false); assert.equal(proof.serverPlayersCreated, 0);
  assert.equal(proof.installed, true); assert.equal(proof.cleaned, false);
  assert.equal(proof.syntheticListed, 82); assert.equal(proof.syntheticUnlisted, 1);
  assert.equal(proof.expectedVisibleRows.length, 80);
  const list = view.presentation.children.filter(node => rowKey.test(node.controlKey ?? ''));
  assert.equal(list.length, 80, 'Native listed filtering and the 80-row cap must really render');
  assert.deepEqual(list.map(node => node.label), proof.expectedVisibleRows.map(row => row.displayName), 'Actual native ordering, not a computed USER profile list');
  const resources = new Set(), atlasRegions = new Map();
  let fullyDrawnFixedScores = 0, withheldFixedScores = 0, fullyDrawnNumericScores = 0, withheldNumericScores = 0,
    fullyDrawnFallbackScores = 0, withheldFallbackScores = 0;
  for (const [index, row] of list.entries()) {
    assert.equal(row.entryIndex, index); assert.equal(row.boundsAvailable, true);
    assert(row.x >= 0 && row.y >= 0 && row.width > 0 && row.height > 0);
    assert.equal(row.labelSource, 'last_completed_native_player_list_draw');
    assert.equal(row.active, false); assert.deepEqual(row.actions, []);
    assert(Array.isArray(row.children) && row.children.length <= 64);
    if (features.pingFormat === 'resource_sprite') {
      const ping = row.children.filter(node => pingKey.test(node.resourceId ?? ''));
      assert.equal(ping.length, 1); assert.equal(ping[0].resourceId, proof.expectedVisibleRows[index].pingSprite);
      resources.add(ping[0].resourceId);
    } else {
      const ping = row.children.filter(nativePingAtlas), expected = proof.expectedVisibleRows[index].pingAtlas;
      assert.equal(ping.length, 1); assert(expected && nativePingAtlas({...expected, role: 'status_icon'}));
      for (const field of ['textureId', 'textureU', 'textureV', 'width', 'height']) assert.equal(ping[0][field], expected[field]);
      assert.equal(ping[0].resourceId, undefined, 'The native atlas draw is not a computed sprite');
      assert.equal(ping[0].atlasWidth, 256); assert.equal(ping[0].atlasHeight, 256);
      assert.equal(ping[0].spriteSemantics, 'native_gui_icons_atlas_region');
      const resource = JSON.stringify(expected); resources.add(resource); atlasRegions.set(resource, expected);
    }
    const textParts = row.children.filter(node => node.role === 'hud_text');
    const text = textParts.filter(node => node.label?.trim());
    const hearts = row.children.filter(features.pingFormat === 'gui_icons_atlas' ? node => nativeHeartAtlas(node, view)
      : node => node.resourceId?.startsWith('minecraft:hud/heart/'));
    const scoreVisible = proof.expectedVisibleRows[index].scoreVisible;
    if (kind === 'fixed' && scoreVisible) {
      assert(textParts.length > 0, 'The native fixed-format score must have an actual draw');
      for (const part of textParts) {
        if (part.textPhase === 'drawn') { assert.equal(part.label, 'VISIBLE SCORE'); fullyDrawnFixedScores++; }
        else { assert.equal(part.textPhase, 'withheld_partial_or_occluded'); assert.equal(part.label, ''); withheldFixedScores++; }
      }
    }
    if (kind === 'numeric' && scoreVisible) {
      assert(textParts.length > 0, 'The native numeric score must have an actual draw');
      for (const part of textParts) {
        if (part.textPhase === 'drawn') { assert.equal(part.label, '8675309'); fullyDrawnNumericScores++; }
        else { assert.equal(part.textPhase, 'withheld_partial_or_occluded'); assert.equal(part.label, ''); withheldNumericScores++; }
      }
    }
    if (kind === 'blank' || !scoreVisible) {
      assert.equal(text.length, 0, 'Blank formatting/spectator suppression must not reveal raw values');
      assert.equal(hearts.length, 0, 'Blank formatting/spectator suppression must not reveal score glyphs');
    }
    if (kind === 'hearts' && scoreVisible) { assert(hearts.length > 0, 'Actual native heart glyphs must be drawn'); assert.equal(text.length, 0); }
    if (kind === 'fallback' && scoreVisible) {
      assert.equal(hearts.length, 0); assert(textParts.length > 0, 'The native narrow-spacing fallback must actually submit text');
      for (const part of textParts) {
        if (part.textPhase === 'drawn') { assert(/1000(?:\.0)?/.test(part.label)); fullyDrawnFallbackScores++; }
        else { assert.equal(part.textPhase, 'withheld_partial_or_occluded'); assert.equal(part.label, ''); withheldFallbackScores++; }
      }
    }
    for (const node of [row, ...row.children]) {
      assert.equal(node.visible, true); assert.equal(node.active, false); assert.deepEqual(node.actions, []);
      assert.equal(node.iconSource, 'last_completed_native_player_list_draw');
      assert(node.x >= 0 && node.y >= 0 && node.x + node.width <= view.width && node.y + node.height <= view.height);
      for (const hidden of ['uuid', 'profile', 'profileName', 'latency', 'score', 'health', 'gameMode', 'value', 'resourceVersion'])
        assert.equal(node[hidden], undefined, 'USER must not publish ' + hidden);
      if (features.pingFormat === 'gui_icons_atlas' && node.role === 'status_icon') {
        assert(nativePingAtlas(node) || nativeHeartAtlas(node, view), 'Only actually drawn native ping/heart atlas regions are allowed');
        assert.equal(node.resourceId, undefined, 'Do not reconstruct modern sprite IDs from a native atlas draw');
        assert.equal(node.atlasWidth, 256); assert.equal(node.atlasHeight, 256);
        assert.equal(node.spriteSemantics, 'native_gui_icons_atlas_region');
      }
    }
  }
  assert.equal(resources.size, 6, 'All six actually drawn native ping icons must be represented');
  if (kind === 'fixed') assert(fullyDrawnFixedScores > 0, 'Clipped fragments cannot replace all actually visible formatted score text');
  if (kind === 'numeric') assert(fullyDrawnNumericScores > 0, 'Clipped fragments cannot replace all actually visible native numeric text');
  if (kind === 'fallback') assert(fullyDrawnFallbackScores > 0, 'Clipped fragments cannot replace all actually visible native fallback text');
  assert(!JSON.stringify(list).includes('ui_hidden_'), 'Private fixture profile names are not display names');
  const guarded = structuredClone(list);
  if (kind === 'numeric') for (const [index, row] of guarded.entries()) if (proof.expectedVisibleRows[index].scoreVisible)
    for (const part of row.children) if (part.role === 'hud_text' && part.textPhase === 'drawn' && part.label === '8675309') part.label = '';
  assert(!JSON.stringify(guarded).includes('8675309'), 'Raw values are allowed only in the actual native numeric score draw');
  for (const [key, label] of [['header', 'Local roster header'], ['footer', 'Local roster footer']]) {
    const node = view.presentation.children.find(node => node.controlKey === 'hud.playerlist.' + key);
    assert.equal(node?.label, label); assert.equal(node.labelSource, 'last_completed_native_player_list_draw');
    assert(node.children?.length > 0); assert(node.children.every(part => part.boundsAvailable && part.visible));
  }
  return {rows: list.length, pingSprites: [...resources].filter(resource => pingKey.test(resource)).sort(),
    pingAtlasRegions: [...atlasRegions.values()], nativeFeatures: features, parts: list.reduce((sum, row) => sum + row.children.length, 0),
    displayedNames: list.map(row => row.label), kind, fullyDrawnFixedScores, withheldFixedScores, fullyDrawnNumericScores,
    withheldNumericScores, fullyDrawnFallbackScores, withheldFallbackScores, localClientPacketFixture: true, actualMultiplayerNetworkValidated: false};
}

export async function playerListActions({api, repo, target, key, tree, wait, nodes, ui, openFixture, check, samples,
  readFixtureProof, sleep = pause, now = () => performance.now()}) {
  const proofPath = resolve(repo, 'validations/conformance-vanilla_ui/result', target + '-player-list-probe.json');
  let identity, featureIdentity, sequence = -1, installed = false, hidden = false, primary, lastInput = -Infinity;
  const calls = [];
  const list = value => value.presentation.children.filter(node => node.controlKey?.startsWith('hud.playerlist.'));
  async function pace() { const delay = 550 - (now() - lastInput); if (delay > 0) await sleep(delay); lastInput = now(); }
  async function proof() {
    const value = readFixtureProof ? await readFixtureProof() : JSON.parse(await readFile(proofPath, 'utf8'));
    assert.equal(value.target, target); assert.equal(value.probeOnly, true);
    assert.equal(value.arrangeAuthority, 'DIAGNOSTIC_local_client_packet_fixture');
    assert.equal(value.actualMultiplayerNetworkValidated, false); assert.equal(value.serverPlayersCreated, 0);
    assert(typeof value.fixtureId === 'string' && value.fixtureId.length >= 16);
    assert(Number.isSafeInteger(value.sequence) && value.sequence >= 0); assert(Number.isSafeInteger(value.processId) && value.processId > 0);
    identity ??= value.fixtureId; assert.equal(value.fixtureId, identity, 'Do not accept another roster fixture');
    const features = nativeFeatures(value); featureIdentity ??= features;
    assert.deepEqual(features, featureIdentity, 'The owned native fixture capabilities must remain stable'); return value;
  }
  async function clean() {
    const input = await api('GET', '/v0/input/state');
    assert.equal(input.pressedKeyCount, 0); assert.equal(input.pressedButtonCount, 0);
    assert.equal(input.inputCleanupPending, false); assert.equal(input.inputCleanupFailed, false); return input;
  }
  async function heldTab(action) { await pace(); await api('POST', '/v0/input/key', {key: 258, action}); calls.push({key: 258, action}); }
  async function tap(code) { await pace(); await key(code); calls.push({key: code, action: 'tap'}); }
  async function menu() {
    if (!(await tree()).screenClass.endsWith('UiPlayerListFixtureScreen')) {
      await heldTab(0); await openFixture();
      await wait(value => value.screenClass.endsWith('UiPlayerListFixtureScreen'), 'owned local roster fixture menu');
    }
    const current = await proof(); sequence = current.sequence; return current;
  }
  async function button(label) {
    const current = await tree(), matches = nodes(current).filter(node => node.label === label && node.active !== false && node.visible !== false && node.actions?.includes('click'));
    assert.equal(matches.length, 1, 'One semantic local roster fixture button: ' + label);
    await pace(); const result = await ui({nodeId: matches[0].nodeId}); calls.push({nodeId: matches[0].nodeId, label}); return result;
  }
  async function arrange(label, kind) {
    await menu(); await button(label); const current = await proof();
    assert.equal(current.sequence, sequence + 1); assert.equal(current.mode, kind); sequence = current.sequence;
    installed = current.installed && !current.cleaned; return current;
  }
  async function shown(kind) {
    const current = await proof(); assert.equal(current.mode, kind);
    await button('View Player List (Fixture)'); await wait(value => !value.screenClass, 'owned fixture closes for native Tab');
    await heldTab(1);
    const value = await wait(value => { try { validateNativePlayerList(value, current, kind); return true; } catch { return false; } }, 'actual native player-list ' + kind + ' draw');
    return {value, proof: current, summary: validateNativePlayerList(value, current, kind)};
  }
  function record(caseName, result, extra = {}) {
    samples.push({case: caseName, target, fixture: result.proof, ...result.summary, ...extra,
      source: 'last_completed_native_player_list_draw', semanticTargets: true, handwrittenCoordinates: false,
      authority: 'CLIENT_UI', arrangeAuthority: 'DIAGNOSTIC_local_client_packet_fixture', serverPlayersCreated: 0,
      actions: calls.slice()});
  }
  try {
    await clean();
    await check('HUD player list: actual listed filtering, ordering, 80-row cap, six native ping icons and native visibility', async () => {
      await arrange('Install Local Roster (Fixture)', 'roster'); const result = await shown('roster');
      record('player_list_roster', result);
      await heldTab(0); await wait(value => list(value).length === 0, 'native Tab release hides the list');
      await tap(290); hidden = true; await heldTab(1);
      await wait(value => list(value).length === 0, 'F1 does not expose a cached player-list frame');
      await heldTab(0); await tap(290); hidden = false; await heldTab(1);
      await wait(value => { try { validateNativePlayerList(value, result.proof); return true; } catch { return false; } }, 'native list returns after F1');
      await tap(69); const screen = await wait(value => value.screenClass.endsWith('InventoryScreen'), 'native Inventory occludes Tab');
      assert.equal(list(screen).length, 0); await tap(256); await wait(value => !value.screenClass, 'Inventory closes');
      await heldTab(0); await clean();
      samples.push({case: 'player_list_visibility', tabReleaseHidden: true, f1Hidden: true, screenHidden: true, input: await clean()});
    });
    const formats = nativeFeatures(await proof()).numberFormats;
    await check(formats ? 'HUD player list: native fixed and blank score formatting does not publish raw objective values'
      : 'HUD player list: actual native numeric score draw remains read-only without unavailable fixed or blank formats', async () => {
      for (const [label, kind] of formats ? [['Fixed Score (Fixture)', 'fixed'], ['Blank Score (Fixture)', 'blank']]
        : [['Numeric Score (Fixture)', 'numeric']]) {
        await arrange(label, kind); const result = await shown(kind); record('player_list_' + kind, result);
        await heldTab(0); await clean();
      }
    });
    await check('HUD player list: actual native heart glyphs and narrow-spacing text fallback remain read-only', async () => {
      for (const [label, kind] of [['Heart Score (Fixture)', 'hearts'], ['Heart Fallback (Fixture)', 'fallback']]) {
        await arrange(label, kind); const result = await shown(kind); record('player_list_' + kind, result);
        await heldTab(0); await clean();
      }
    });
  } catch (error) { primary = error; throw error; }
  finally {
    const errors = [];
    for (const cleanup of [
      async () => { await api('POST', '/v0/input/release-all', {}); },
      async () => { if (hidden) { await tap(290); hidden = false; } },
      async () => {
        if (!identity) return;
        const actual = await proof();
        if (!installed && !(actual.installed && !actual.cleaned)) return;
        await menu(); await button('Restore Local Roster (Fixture)'); const restored = await proof();
        assert.equal(restored.sequence, sequence + 1); assert.equal(restored.mode, 'restored'); assert.equal(restored.cleaned, true);
        if (featureIdentity.localPlayerListRestorationRequired) {
          assert.equal(restored.localPlayerListRestored, true, 'The original local player listed state must really be restored');
          assert.equal(restored.localPlayerTemporarilyUnlisted, false);
        }
        await button('View Player List (Fixture)'); await wait(value => !value.screenClass && list(value).length === 0, 'restored roster returns safely to the actual world');
        installed = false; samples.push({case: 'player_list_cleanup', fixture: restored, ownedRosterRemoved: true, input: await clean()});
      }, clean,
    ]) { try { await cleanup(); } catch (error) { errors.push(error); } }
    if (errors.length) {
      if (primary) primary.cleanupErrors = errors.map(error => error.message);
      else throw new AggregateError(errors, 'The local player-list fixture was not fully restored');
    }
  }
}
