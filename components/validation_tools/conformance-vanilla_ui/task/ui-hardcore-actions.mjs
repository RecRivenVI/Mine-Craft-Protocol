import assert from 'node:assert/strict';
import {validateVitalVariant} from './hud-vital-variant-actions.mjs';

export const hardcoreWorldName = 'UI-Hardcore-Validation';
const actionable = (node, action = 'click') => node.visible !== false && node.active !== false && node.actions?.includes(action);

// The caller supplies the already-owned validation client and its existing TAKEOVER Lease.
// The new world is retained inside that disposable instance; no baseline or world file is edited.
export async function uiHardcoreActions({target, api, tree, wait, nodes, ui, key, title, saveQuit, check, samples}) {
  assert.equal(typeof saveQuit, 'function', 'Reuse the runner native Save and Quit boundary handler');
  const clean = async () => {
    const value = await api('GET', '/v0/input/state');
    assert.equal(value.pressedKeyCount, 0); assert.equal(value.pressedButtonCount, 0);
    assert.equal(value.inputCleanupPending, false); assert.equal(value.inputCleanupFailed, false); return value;
  };
  const page = async name => {
    const value = await wait(value => value.screenClass.endsWith(name), 'hardcore native ' + name);
    assert.equal(value.dataCategory, 'USER'); assert.equal(value.overlayIdentity, 0); assert.equal(value.treeTruncated, false); return value;
  };
  const select = async (key, label, action = 'click') => {
    const value = await tree(); assert.equal(value.dataCategory, 'USER'); assert.equal(value.treeTruncated, false);
    const found = nodes(value).filter(node => actionable(node, action) && (node.translationKey === key || node.label === label));
    assert.equal(found.length, 1, 'One visible semantic native control: ' + key); return found[0];
  };
  const button = async (key, label) => {const node = await select(key, label); return {node, response: await ui({nodeId: node.nodeId})};};
  let started = false;
  try {
    await check('HUD: hardcore: actual native world creation yields drawn hardcore hearts and saves normally', async () => {
      await title(); await page('TitleScreen');
      assert.equal((await api('GET', '/v0/player?category=USER')).available, false);
      const control = await api('GET', '/v0/control/status');
      assert.equal(control.mode, 'TAKEOVER'); assert.equal(control.takeoverActive, true);
      assert.equal(typeof control.leaseId, 'string'); assert(control.leaseId.length > 0);
      await clean(); started = true;
      const actions = [await button('menu.singleplayer', 'Singleplayer')];
      const listing = await page('SelectWorldScreen');
      const priorWorlds = nodes(listing).filter(node => node.worldId);
      assert(!priorWorlds.some(node => node.worldName === hardcoreWorldName), 'Do not overwrite or reopen a pre-existing hardcore fixture world');
      actions.push(await button('selectWorld.create', 'Create New World')); await page('CreateWorldScreen');
      const name = await select('selectWorld.enterName', 'World Name', 'type_text');
      actions.push({node: name, action: 'type_text', response: await ui({nodeId: name.nodeId}, 'type_text', {text: hardcoreWorldName})});
      const modes = [];
      for (let index = 0; index < 4; index++) {
        const view = await page('CreateWorldScreen');
        const candidates = nodes(view).filter(node => actionable(node) && (node.translationKey === 'selectWorld.gameMode' || /^Game Mode: (?:Survival|Hardcore|Creative)$/.test(node.label ?? '')));
        assert.equal(candidates.length, 1, 'Actual native Game Mode cycle button');
        const mode = candidates[0]; modes.push(mode);
        if (/^Game Mode: Hardcore$/.test(mode.label)) break;
        assert(index < 3, 'Native Game Mode did not reach Hardcore in its bounded cycle');
        actions.push({node: mode, response: await ui({nodeId: mode.nodeId})});
      }
      assert.equal(modes.at(-1).label, 'Game Mode: Hardcore');
      const create = await select('selectWorld.create', 'Create New World'), before = await clean();
      let response, error;
      try {response = await ui({nodeId: create.nodeId});} catch (failure) {
        const body = failure.nativeError ?? failure.runtimeError, status = failure.httpStatus ?? failure.status;
        if (status !== 409 || !['REQUEST_CANCELLED', 'CONTROL_INPUT_GENERATION_CHANGED'].includes(body?.error)
          || body.executionOutcome !== 'not_confirmed' || body.retrySafe !== false) throw failure;
        error = {status, body};
      }
      const view = await wait(value => !value.screenClass && value.overlayIdentity === 0
        && value.presentation?.hudIconSampleStatus === 'CURRENT'
        && (value.presentation?.children ?? []).some(node => node.controlKey?.startsWith('hud.vitals.health.') && /hardcore/.test(node.label ?? '')), 'actual newly created hardcore HUD');
      const hearts = validateVitalVariant(view, 'hardcore');
      const player = await api('GET', '/v0/player?category=USER'), session = await api('GET', '/v0/session'), after = await clean();
      const currentControl = await api('GET', '/v0/control/status');
      assert.equal(player.dataCategory, 'USER'); assert.equal(player.available, true); assert.equal(player.gameMode, 'survival');
      assert.equal(session.target, target); assert.equal(session.inWorld, true);
      assert.equal(before.inputContextAvailable, false); assert.equal(after.inputContextAvailable, true);
      assert(after.inputContextGeneration > before.inputContextGeneration); assert(after.inputGeneration > before.inputGeneration);
      assert.notEqual(after.inputContextId, before.inputContextId);
      assert.equal(currentControl.mode, 'TAKEOVER'); assert.equal(currentControl.takeoverActive, true);
      assert.equal(currentControl.leaseId, control.leaseId); assert.deepEqual(currentControl.modeVersion, control.modeVersion);
      const entry = {requestOutcome: error ? 'not_confirmed' : 'response_received', requestReplayed: false,
        independentlyObserved: true, before, after, ...(error ? {error} : {response})};
      await key(256); await page('PauseScreen');
      const quit = await select('menu.returnToMenu', 'Save and Quit to Title');
      const exited = await saveQuit(quit); await page('TitleScreen'); await clean();
      // Observe the world created by this run, never infer its identity from a folder name.
      await button('menu.singleplayer', 'Singleplayer');
      const saved = await page('SelectWorldScreen'), worlds = nodes(saved).filter(node => node.worldId);
      const created = worlds.filter(node => node.worldName === hardcoreWorldName && !priorWorlds.some(prior => prior.worldId === node.worldId));
      assert.equal(created.length, 1, 'Exactly one newly saved semantic hardcore world');
      assert.deepEqual(worlds.filter(node => !created.includes(node)).map(node => node.worldId).sort(), priorWorlds.map(node => node.worldId).sort(), 'Existing worlds are unchanged');
      samples.push({case: 'hud_hardcore_native_world', target, category: 'USER', worldName: hardcoreWorldName, world: created[0],
        selectedMode: modes.at(-1), modeCycle: modes, semanticActions: actions, createTarget: create, entry, player,
        heartNodes: hearts, exit: exited, evidenceAuthority: 'PLAYTEST', nativeWorldCreation: true,
        hardcoreFlagMutationUsed: false, handwrittenCoordinates: false, deathTested: false, worldDeletionUsed: false});
    });
  } finally {
    if (started) {await title(); await page('TitleScreen'); await clean();}
  }
}
