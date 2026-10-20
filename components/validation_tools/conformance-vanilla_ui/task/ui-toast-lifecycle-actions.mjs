import assert from 'node:assert/strict';

export const legacyToastKinds = Object.freeze(['System', 'Tutorial', 'Advancement', 'Recipe']);
const toastRows = (view, kind) => (view.presentation?.children ?? []).filter(node => node.role === 'toast'
  && (!kind || node.class?.endsWith(`.${kind}Toast`)));
const fullPhases = {System: ['static'], Tutorial: ['static'], Advancement: ['header', 'title', 'header_and_title'], Recipe: ['static']};

export function validateLegacyToastDraw(view, kind, phase, expectedOwner) {
  assert(legacyToastKinds.includes(kind)); assert(['entering', 'fully_shown', 'exiting'].includes(phase));
  assert.equal(view.dataCategory, 'USER'); assert.equal(view.overlayIdentity, 0);
  const rows = toastRows(view, kind).filter(node => node.renderPhase === phase);
  assert.equal(rows.length, 1, `One actual ${kind} ${phase} owner is required`);
  const node = rows[0];
  assert.equal(node.visible, true); assert.equal(node.active, false); assert.deepEqual(node.actions, []);
  assert.equal(node.labelSource, 'last_native_toast_render'); assert.equal(node.boundsAvailable, true);
  assert.equal(node.boundsSemantics, 'native_toast_draw_viewport_intersection');
  assert.match(node.contextKey, /^toast:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
  if (expectedOwner !== undefined) assert.equal(node.contextKey, expectedOwner, 'A different native Toast cannot complete this lifecycle');
  for (const key of ['x', 'y', 'width', 'height']) assert(Number.isFinite(node[key]), key);
  assert(node.x >= 0 && node.y >= 0 && node.width > 0 && node.height > 0);
  assert(node.x + node.width <= view.width && node.y + node.height <= view.height);
  if (phase === 'fully_shown') {
    assert(fullPhases[kind].includes(node.textPhase), 'Keep the actual native static/header/title text phase');
    assert(node.label?.trim(), 'A full native draw must contain real rendered text');
  } else {
    assert.equal(node.textPhase, 'withheld_partial_or_occluded'); assert.equal(node.label, '');
  }
  for (const key of ['debugLabel', 'resourceVersion', 'debugToastValue', 'queuedText', 'criteria', 'recipeId'])
    assert.equal(node[key], undefined, `USER must not expose ${key}`);
  return node;
}

// Diagnostic Arrange creates local Native display objects only. Every observation below is a
// standard USER tree read; no player advancement/recipe, audio, Options or world file is changed.
export async function uiToastLifecycleActions({target, api, tree, wait, nodes, ui, check, samples, openFixture}) {
  assert(['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric'].includes(target));
  const clean = async () => {
    const input = await api('GET', '/v0/input/state');
    assert.equal(input.pressedKeyCount, 0); assert.equal(input.pressedButtonCount, 0);
    assert.equal(input.inputCleanupPending, false); assert.equal(input.inputCleanupFailed, false); return input;
  };
  const button = async label => {
    const value = await tree(); assert.equal(value.dataCategory, 'USER');
    const found = nodes(value).filter(node => node.visible !== false && node.active !== false
      && node.label === label && node.actions?.includes('click'));
    assert.equal(found.length, 1, 'One current semantic fixture button: ' + label);
    return ui({nodeId: found[0].nodeId});
  };
  const boundary = async () => {
    const value = await wait(value => value.screenClass.endsWith('ShowcaseToastFixture$LifecycleScreen'), 'owned native Toast lifecycle fixture');
    assert.equal(value.dataCategory, 'USER'); assert.equal(value.overlayIdentity, 0); assert.equal(value.treeTruncated, false); return value;
  };
  const empty = async () => {
    const value = await wait(value => toastRows(value).length === 0, 'no actual resident Toast draw remains');
    return {clientTick: value.clientTick, remaining: toastRows(value).length};
  };
  const lifecycle = async (kind, begin) => {
    await begin();
    // Native SHOW/HIDE selects the animation direction before the first sliding frame. Direction
    // alone does not mean geometry/text is partial; observe a real clipped frame for this proof.
    const enteringView = await wait(value => toastRows(value, kind).some(node => node.renderPhase === 'entering'
      && node.textPhase === 'withheld_partial_or_occluded'), kind + ' actual entering draw');
    const entering = validateLegacyToastDraw(enteringView, kind, 'entering');
    const owner = entering.contextKey;
    const full = validateLegacyToastDraw(await wait(value => toastRows(value, kind).some(node => node.contextKey === owner
      && node.renderPhase === 'fully_shown' && node.label?.trim()), kind + ' actual fully shown draw'), kind, 'fully_shown', owner);
    if (kind === 'Tutorial') await button('Finish Tutorial');
    const exiting = validateLegacyToastDraw(await wait(value => toastRows(value, kind).some(node => node.contextKey === owner
      && node.renderPhase === 'exiting' && node.textPhase === 'withheld_partial_or_occluded'), kind + ' actual exiting draw'), kind, 'exiting', owner);
    const absentView = await wait(value => !toastRows(value).some(node => node.contextKey === owner), kind + ' native exit completed');
    return {kind, contextKey: owner, entering, full, exiting, absent: {clientTick: absentView.clientTick, remaining: 0},
      tutorialHideRequestedByFixture: kind === 'Tutorial', naturalGameplayCompletionClaimed: false};
  };
  let opened = false, fixtureReset = false, primary;
  const ensureFixture = async () => {
    if (opened) return;
    await openFixture(); await button('Toast Lifecycle Fixture'); opened = true; await boundary();
    // The standalone scope can still contain the native movement tutorial. Reset only after
    // entering this owned disposable fixture; clearing it is not gameplay tutorial completion.
    await button('Clear Lifecycle Toasts'); fixtureReset = true; await empty();
  };
  try {
    for (const kind of legacyToastKinds) await check(`HUD: native legacy ${kind} Toast enters, fully draws, exits and disappears`, async () => {
      await ensureFixture();
      const before = await clean(), observed = await lifecycle(kind, () => button(kind + ' Lifecycle'));
      await empty();
      samples.push({case: 'native_toast_lifecycle', target, category: 'USER', source: 'probe_only_native_toast_construction',
        arrangeAuthority: 'DIAGNOSTIC_native_display_fixture', assertAuthority: 'USER_NATIVE_DRAW',
        ...observed, before, input: await clean(), fixtureReset, gameplayTutorialCompletionClaimed: false,
        gameplayUnlockChanged: false, volumeWritten: false, worldFilesWritten: false});
    });
    await check('HUD: native legacy Toast queue hides unrendered Recipe until actual slots are released', async () => {
      await ensureFixture();
      const before = await clean(); await empty(); await button('Queue Blockers');
      const blocking = await wait(value => toastRows(value, 'Tutorial').filter(node => node.renderPhase === 'fully_shown'
        && /^Queue blocker [1-5]/.test(node.label)).length === 5, 'five actual fully shown native queue blockers');
      const blockers = toastRows(blocking, 'Tutorial').filter(node => /^Queue blocker [1-5]/.test(node.label));
      assert.equal(toastRows(blocking).length, 5, 'Only the five owned Native blockers may occupy the slots');
      assert.equal(new Set(blockers.map(node => node.contextKey)).size, 5);
      for (const node of blockers) assert.match(node.contextKey, /^toast:[0-9a-f-]{36}$/);
      await button('Queue Recipe');
      const observations = []; let lastTick;
      await wait(value => {
        assert.equal(value.dataCategory, 'USER'); assert.equal(value.overlayIdentity, 0);
        assert.equal(toastRows(value, 'Recipe').length, 0, 'Queued Recipe type/geometry must not appear in USER');
        const forbiddenTextPresent = (value.presentation?.children ?? []).some(node => /New Recipe(?:s|\(s\))? Unlocked|Check your recipe book/.test(node.label ?? ''));
        assert.equal(forbiddenTextPresent, false, 'Queued Recipe text must not appear before a native draw');
        const visibleBlockers = toastRows(value, 'Tutorial').filter(node => blockers.some(blocker => blocker.contextKey === node.contextKey)
          && node.renderPhase === 'fully_shown').length;
        assert.equal(visibleBlockers, 5, 'The actual Native queue must still be blocked');
        assert(Number.isSafeInteger(value.clientTick));
        if (value.clientTick !== lastTick) {
          assert(lastTick === undefined || value.clientTick > lastTick); lastTick = value.clientTick;
          observations.push({clientTick: value.clientTick, recipeNodes: 0, forbiddenTextPresent, visibleBlockers});
        }
        return observations.length >= 3;
      }, 'queued Native Recipe remains absent across three client ticks');
      const queuedRecipe = await lifecycle('Recipe', () => button('Release Blockers'));
      await empty();
      samples.push({case: 'native_toast_queue_boundary', target, category: 'USER', source: 'probe_only_native_toast_construction',
        arrangeAuthority: 'DIAGNOSTIC_native_display_fixture', assertAuthority: 'USER_NATIVE_DRAW',
        blockers: blockers.map(({contextKey, label, renderPhase}) => ({contextKey, label, renderPhase})), observations,
        released: true, queuedRecipe, before, input: await clean(),
        queuedTextExposed: false, fixtureReset, gameplayTutorialCompletionClaimed: false,
        gameplayUnlockChanged: false, volumeWritten: false, worldFilesWritten: false});
    });
  } catch (error) {primary = error; throw error;}
  finally {
    if (opened) {
      try {
        await boundary(); await button('Clear Lifecycle Toasts'); await empty(); await button('Done');
        await wait(value => value.screenClass.endsWith('AutomationProbeScreen'), 'return from the owned Toast fixture');
        await clean();
      } catch (error) {if (primary) primary.cleanupFailure = error.message; else throw error;}
    }
  }
}
