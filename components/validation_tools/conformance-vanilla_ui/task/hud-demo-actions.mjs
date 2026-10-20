import assert from 'node:assert/strict';
import {validateLateTextDraw} from './hud-late-text-actions.mjs';

const actionable = node => node.visible !== false && node.active !== false && node.actions?.includes('click');
const introScreens = new Map([
  ['1.20.1-forge', 'net.minecraft.client.gui.screens.DemoIntroScreen'],
  ['1.21.1-neoforge', 'net.minecraft.client.gui.screens.DemoIntroScreen'],
  ['26.1.2-neoforge', 'net.minecraft.client.gui.components.PopupScreen'],
  ['26.3-neoforge', 'net.minecraft.client.gui.components.PopupScreen'],
  ['26.3-fabric', 'net.minecraft.client.gui.components.PopupScreen']
]);

export function validateDemoLaunch(launch, target) {
  assert(introScreens.has(target), 'Only the five maintained native Demo Targets are accepted');
  assert.equal(launch?.target, target); assert.equal(launch.validationOwned, true, 'Demo requires the runner-owned validation instance');
  assert(Number.isSafeInteger(launch.processId) && launch.processId > 0);
  assert(Array.isArray(launch.gameArguments) && launch.gameArguments.every(value => typeof value === 'string'));
  assert.equal(launch.gameArguments.filter(value => value === '--demo').length, 1, 'Actual Java launch game arguments must contain --demo exactly once');
  assert.equal(typeof launch.instanceDirectory, 'string');
  assert(launch.instanceDirectory.replaceAll('\\', '/').endsWith('/validations/conformance-vanilla_ui/instance/' + target + '/client'), 'Do not enter a daily or external Demo save');
  return launch;
}

// Launch attestation must be collected by the runner from its actual owned Java invocation.
// This helper never toggles Minecraft.isDemo, constructs a screen or calls a renderer.
export async function hudDemoActions({target, api, tree, wait, nodes, ui, key, title, saveQuit, check, samples, launchAttestation}) {
  const launch = validateDemoLaunch(launchAttestation, target);
  assert.equal(typeof saveQuit, 'function', 'Reuse the native Save and Quit context-boundary handler');
  const clean = async () => {
    const value = await api('GET', '/v0/input/state');
    assert.equal(value.pressedKeyCount, 0); assert.equal(value.pressedButtonCount, 0);
    assert.equal(value.inputCleanupPending, false); assert.equal(value.inputCleanupFailed, false); return value;
  };
  const page = async name => {
    const value = await wait(value => value.screenClass.endsWith(name), 'native Demo ' + name);
    assert.equal(value.dataCategory, 'USER'); assert.equal(value.overlayIdentity, 0); assert.equal(value.treeTruncated, false); return value;
  };
  const demoTitle = async () => {
    const value = await page('TitleScreen'), controls = nodes(value);
    const play = controls.filter(node => actionable(node) && node.translationKey === 'menu.playdemo');
    assert.equal(play.length, 1, 'Actual Demo Title entry is required');
    assert(controls.some(node => node.visible !== false && node.translationKey === 'menu.resetdemo'), 'Actual Demo Title reset entry is required');
    assert(!controls.some(node => node.visible !== false && node.translationKey === 'menu.singleplayer'), 'Normal Title is not a Demo launch');
    return {value, play: play[0], reset: controls.find(node => node.translationKey === 'menu.resetdemo')};
  };
  const submitOnce = async node => {
    try {
      const response = await ui({nodeId: node.nodeId});
      assert.notEqual(response?.directMutationUsed, true);
      return {requestOutcome: 'response_received', requestReplayed: false, response};
    } catch (failure) {
      const body = failure.nativeError ?? failure.runtimeError, status = failure.httpStatus ?? failure.status;
      if (status !== 409 || !['REQUEST_CANCELLED', 'CONTROL_INPUT_GENERATION_CHANGED'].includes(body?.error)
        || body.executionOutcome !== 'not_confirmed' || body.retrySafe !== false) throw failure;
      return {requestOutcome: 'not_confirmed', requestReplayed: false, error: {status, body}};
    }
  };
  let started = false;
  try {
    await check('HUD: Demo: real --demo Title entry enters the native world and draws its remaining-time HUD', async () => {
      await title(); const initial = await demoTitle();
      assert.equal((await api('GET', '/v0/player?category=USER')).available, false);
      const control = await api('GET', '/v0/control/status');
      assert.equal(control.mode, 'TAKEOVER'); assert.equal(control.takeoverActive, true);
      assert.equal(typeof control.leaseId, 'string'); assert(control.leaseId.length > 0);
      const before = await clean(); assert.equal(before.inputContextAvailable, false); started = true;
      const submitted = await submitOnce(initial.play);
      // Native DemoMode sends its intro after entering the world, so an earlier HUD frame is not completion.
      const introduction = await wait(value => value.screenClass === introScreens.get(target)
        && nodes(value).some(node => node.visible === true && node.translationKey === 'demo.help.buy'), 'actual native Demo intro');
      assert.equal(introduction.dataCategory, 'USER'); assert.equal(introduction.overlayIdentity, 0);
      assert.equal(introduction.treeTruncated, false);
      const later = nodes(introduction).filter(node => node.visible === true && node.active === true
        && node.actions?.includes('click') && node.translationKey === 'demo.help.later');
      assert.equal(later.length, 1, 'One actual safe native Demo Later button is required');
      const introRequest = await submitOnce(later[0]);
      const view = await wait(value => !value.screenClass && value.overlayIdentity === 0
        && (value.presentation?.children ?? []).some(node => node.renderPhase === 'demo' && node.textPhase === 'drawn' && /^Remaining time: \d+:\d{2}(?::\d{2})?$/.test(node.label ?? '')), 'actual Demo remaining-time draw');
      const drawn = validateLateTextDraw(view, 'demo');
      assert(drawn.some(node => /^Remaining time: \d+:\d{2}(?::\d{2})?$/.test(node.label)), 'A fresh Demo countdown must be actually drawn');
      const player = await api('GET', '/v0/player?category=USER'), session = await api('GET', '/v0/session'), after = await clean();
      const currentControl = await api('GET', '/v0/control/status');
      assert.equal(player.dataCategory, 'USER'); assert.equal(player.available, true); assert.equal(player.gameMode, 'survival');
      assert.equal(session.target, target); assert.equal(session.inWorld, true); assert.equal(after.inputContextAvailable, true);
      assert(after.inputContextGeneration > before.inputContextGeneration); assert(after.inputGeneration > before.inputGeneration);
      assert.notEqual(after.inputContextId, before.inputContextId);
      assert.equal(currentControl.mode, 'TAKEOVER'); assert.equal(currentControl.takeoverActive, true);
      assert.equal(currentControl.leaseId, control.leaseId); assert.deepEqual(currentControl.modeVersion, control.modeVersion);
      const entry = {...submitted, independentlyObserved: true, before, after};
      const intro = {observed: true, screenClass: introduction.screenClass, title: introduction.title,
        safeButton: later[0], ...introRequest, dismissedIndependently: true,
        afterScreenClass: view.screenClass, afterOverlayIdentity: view.overlayIdentity};
      await key(256); const paused = await page('PauseScreen');
      const quit = nodes(paused).filter(node => actionable(node) && node.translationKey === 'menu.returnToMenu');
      assert.equal(quit.length, 1, 'Actual Save and Quit, not Demo reset or purchase');
      const exited = await saveQuit(quit[0]); const returned = await demoTitle(); await clean();
      assert.equal(returned.reset.active, true, 'Native Demo world now exists after its normal save');
      samples.push({case: 'hud_demo_native_launch', target, category: 'USER', launch,
        titleEntry: initial.play, entry, intro, player, nativeDemoNodes: drawn, exit: exited,
        returnedTitle: returned.value.screenClass, evidenceAuthority: 'PLAYTEST', resetClicked: false,
        externalLinkClicked: false, handwrittenCoordinates: false, demoModeMutationUsed: false,
        expiredDemoTested: false, nativeRemainingTimeDrawObserved: true,
        demoSaveDirectory: 'Demo_World', crossRunPersistenceAsserted: false});
    });
  } finally {
    if (started) {await title(); await page('TitleScreen'); await clean();}
  }
}
