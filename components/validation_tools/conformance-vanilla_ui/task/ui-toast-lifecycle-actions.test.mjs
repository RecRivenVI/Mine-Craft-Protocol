import assert from 'node:assert/strict';
import test from 'node:test';
import {readFile, readdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {legacyToastKinds, uiToastLifecycleActions, validateLegacyToastDraw} from './ui-toast-lifecycle-actions.mjs';

const owner = index => `toast:00000000-0000-4000-8000-${String(index).padStart(12, '0')}`;

test('actual Runner completes the real movement tutorial before later gameplay or Toast-reset fixtures', async () => {
  const source = await readFile(new URL('./run-vanilla-ui.mjs', import.meta.url), 'utf8');
  const world = source.indexOf("await check('world fixture: GUI entry");
  const tutorial = source.indexOf("await check('Toast: native movement tutorial");
  assert(world >= 0 && tutorial > world);
  assert.equal(source.indexOf("await check('Toast: native movement tutorial", tutorial + 1), -1);
  for (const call of ['await uiCorrectnessActions(', 'await hudContextCompletionActions(', 'await hudCameraVariantActions(', 'await uiNavigationCompletionActions(', 'await uiToastLifecycleActions('])
    assert(source.indexOf(call) > tutorial, 'Native tutorial must precede ' + call);
});

function validateProbeEntrances(sources) {
  const rectangles = [];
  for (const {name, source} of sources) {
    if (!source.includes('AutomationProbeScreen.class')) continue;
    for (const match of source.matchAll(/\.bounds\(\s*8\s*,\s*(\d+)\s*,\s*128\s*,\s*20\s*\)/g))
      rectangles.push({name, x: 8, y: Number(match[1]), width: 128, height: 20});
  }
  assert(rectangles.length > 0, 'Actual literal Probe entrance bounds must be examined');
  for (const rectangle of rectangles) {
    assert(rectangle.x >= 0 && rectangle.y >= 0 && rectangle.x + rectangle.width <= 480
      && rectangle.y + rectangle.height <= 270, 'Probe entrance outside the actual 480x270 UI fixture: ' + rectangle.name);
  }
  for (const [index, a] of rectangles.entries()) for (const b of rectangles.slice(index + 1))
    assert(!(a.x < b.x + b.width && b.x < a.x + a.width && a.y < b.y + b.height && b.y < a.y + a.height),
      'Overlapping literal Probe entrances: ' + a.name + '@' + a.y + ' and ' + b.name + '@' + b.y);
  return rectangles;
}

test('all five real Probe sources place every literal left entrance inside 480x270 without overlap', async () => {
  const repo = resolve(import.meta.dirname, '../../../..'); let reference;
  for (const target of ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric']) {
    const root = resolve(repo, 'versions', target, 'src/probe');
    const sources = [];
    for (const entry of await readdir(root, {recursive: true, withFileTypes: true})) {
      if (!entry.isFile() || !entry.name.endsWith('.java')) continue;
      sources.push({name: entry.name, source: await readFile(resolve(entry.parentPath, entry.name), 'utf8')});
    }
    const actual = validateProbeEntrances(sources);
    const toast = actual.filter(rectangle => rectangle.name === 'ShowcaseToastProbeMixin.java' && rectangle.y === 227);
    assert.equal(toast.length, 1, target + ' must expose the real lifecycle entry exactly once at the free row');
    if (target === '26.3-neoforge') reference = sources;
  }
  const collision = reference.map(value => value.name === 'ShowcaseToastProbeMixin.java'
    ? {...value, source: value.source.replace(/\.bounds\(8, 227, 128, 20\)/, '.bounds(8, 107, 128, 20)')} : value);
  assert.throws(() => validateProbeEntrances(collision), /Overlapping literal Probe entrances/,
    'The original Toast-versus-Navigation row 107 collision must be detected from actual production Probe sources');
  const outside = reference.map(value => value.name === 'ShowcaseToastProbeMixin.java'
    ? {...value, source: value.source.replace(/\.bounds\(8, 227, 128, 20\)/, '.bounds(8, 251, 128, 20)')} : value);
  assert.throws(() => validateProbeEntrances(outside), /outside the actual 480x270/);
});
function draw(kind, phase, index = 1, label) {
  return {nodeId: 'toast.0', contextKey: owner(index), class: `net.minecraft.client.gui.components.toasts.${kind}Toast`,
    role: 'toast', visible: true, active: false, actions: [], boundsAvailable: true,
    boundsSemantics: 'native_toast_draw_viewport_intersection', labelSource: 'last_native_toast_render',
    x: 280, y: 0, width: 120, height: 32, renderPhase: phase,
    label: phase === 'fully_shown' ? label ?? 'Real native display text' : '',
    textPhase: phase === 'fully_shown' ? kind === 'Advancement' ? 'header' : 'static' : 'withheld_partial_or_occluded'};
}
const view = node => ({dataCategory: 'USER', overlayIdentity: 0, width: 400, height: 240, presentation: {children: [node]}});

test('the actual Native full text phase is retained and opaque owner, clipping and privacy are mandatory', () => {
  for (const kind of legacyToastKinds) for (const phase of ['entering', 'fully_shown', 'exiting'])
    assert.equal(validateLegacyToastDraw(view(draw(kind, phase)), kind, phase, owner(1)).contextKey, owner(1));
  for (const phase of ['title', 'header_and_title']) {
    const title = draw('Advancement', 'fully_shown'); title.textPhase = phase;
    assert.equal(validateLegacyToastDraw(view(title), 'Advancement', 'fully_shown').textPhase, phase);
  }
  for (const mutate of [value => value.contextKey = undefined, value => value.contextKey = 'toast.0',
    value => value.contextKey = owner(2), value => value.label = '', value => value.textPhase = 'drawn',
    value => value.resourceVersion = {}, value => value.debugLabel = 'hidden cached text',
    value => value.width = 121, value => value.boundsSemantics = 'predicted_geometry']) {
    const node = draw('System', 'fully_shown'); mutate(node);
    assert.throws(() => validateLegacyToastDraw(view(node), 'System', 'fully_shown', owner(1)));
  }
  const partial = draw('Recipe', 'entering'); partial.label = 'not actually fully visible';
  assert.throws(() => validateLegacyToastDraw(view(partial), 'Recipe', 'entering'));
});

function nativeFixture(options = {}) {
  const calls = [], samples = [], checks = [], phaseFrames = []; let screen = 'AutomationProbeScreen', tick = 1, nextOwner = 1;
  let current = options.initialUnrelatedToast ? {kind: 'Tutorial', phase: 'fully_shown', owner: 99} : undefined;
  let blockers = [], queued = false, queueSeen = false;
  const labels = ['System Lifecycle', 'Tutorial Lifecycle', 'Advancement Lifecycle', 'Recipe Lifecycle',
    'Finish Tutorial', 'Queue Blockers', 'Queue Recipe', 'Release Blockers', 'Clear Lifecycle Toasts', 'Done'];
  const makeView = () => {
    const entries = blockers.map((id, i) => draw('Tutorial', 'fully_shown', id, 'Queue blocker ' + (i + 1)));
    if (current) {
      const node = draw(current.kind, current.phase, current.owner);
      if ((current.phase === 'entering' && current.enterFullRemaining > 0)
        || (current.phase === 'exiting' && current.exitFullRemaining > 0)) {
        node.label = 'Real native display text'; node.textPhase = current.kind === 'Advancement' ? 'header' : 'static';
      }
      if (options.changedOwner && current.phase === 'fully_shown') node.contextKey = owner(current.owner + 100);
      if (options.partialLeak && current.phase !== 'fully_shown') node.label = 'hidden future text';
      phaseFrames.push({contextKey: node.contextKey, renderPhase: node.renderPhase, textPhase: node.textPhase});
      entries.push(node);
    }
    if (queued && options.queueLeak) entries.push(draw('Recipe', 'entering', 99));
    return {dataCategory: 'USER', overlayIdentity: 0, treeTruncated: false, width: 400, height: 240,
      screenClass: screen === 'Lifecycle' ? 'probe.ShowcaseToastFixture$LifecycleScreen' : 'probe.' + screen,
      clientTick: tick++, children: (screen === 'Lifecycle' ? labels : ['Toast Lifecycle Fixture']).map(label => ({
        nodeId: label, label, role: 'button', visible: true, active: true, actions: ['click']})),
      presentation: {children: entries}};
  };
  const advance = () => {
    if (!current) return;
    if (current.phase === 'entering') {
      if (current.enterFullRemaining > 0) current.enterFullRemaining--;
      else current.phase = 'fully_shown';
    } else if (current.phase === 'fully_shown' && current.kind !== 'Tutorial') {
      current.phase = 'exiting'; current.exitFullRemaining = options.firstExitStillFull ? 1 : 0;
    } else if (current.phase === 'exiting') {
      if (current.exitFullRemaining > 0) current.exitFullRemaining--;
      else current = undefined;
    }
  };
  const ctx = {target: options.target ?? '26.3-neoforge', samples,
    api: async (method, path) => {
      calls.push({method, path}); assert.equal(method, 'GET'); assert.equal(path, '/v0/input/state');
      return {pressedKeyCount: 0, pressedButtonCount: 0, inputCleanupPending: false, inputCleanupFailed: false};
    },
    tree: async () => makeView(), nodes: value => value.children,
    openFixture: async () => {calls.push({openFixture: true}); assert.equal(screen, 'AutomationProbeScreen');},
    ui: async selector => {
      assert.deepEqual(Object.keys(selector), ['nodeId']); const label = selector.nodeId; calls.push({button: label});
      if (label === 'Toast Lifecycle Fixture') {assert.equal(screen, 'AutomationProbeScreen'); screen = 'Lifecycle'; return;}
      assert.equal(screen, 'Lifecycle'); assert(labels.includes(label));
      if (label.endsWith(' Lifecycle') && !label.startsWith('Clear')) {
        const kind = label.split(' ')[0]; assert(legacyToastKinds.includes(kind));
        current = {kind, phase: options.noEntering ? 'fully_shown' : 'entering', owner: nextOwner++, enterFullRemaining: options.firstEnterStillFull ? 1 : 0};
      } else if (label === 'Finish Tutorial') {assert.equal(current?.kind, 'Tutorial'); current.phase = 'exiting'; current.exitFullRemaining = options.firstExitStillFull ? 1 : 0;}
      else if (label === 'Queue Blockers') {assert.equal(blockers.length, 0); blockers = [nextOwner++, nextOwner++, nextOwner++, nextOwner++, nextOwner++];}
      else if (label === 'Queue Recipe') {assert.equal(blockers.length, 5); queued = true;}
      else if (label === 'Release Blockers') {assert.equal(queueSeen, true, 'Must observe the actual queue before releasing it'); blockers = []; queued = false; current = {kind: 'Recipe', phase: 'entering', owner: nextOwner++, enterFullRemaining: options.firstEnterStillFull ? 1 : 0};}
      else if (label === 'Clear Lifecycle Toasts') {blockers = []; current = undefined; queued = false;}
      else if (label === 'Done') {assert.equal(current, undefined); assert.equal(blockers.length, 0); screen = 'AutomationProbeScreen';}
    },
    wait: async (predicate, description) => {
      for (let i = 0; i < 12; i++) {
        const value = makeView();
        if (predicate(value)) {
          if (description.includes('three client ticks')) queueSeen = true;
          if (/actual (?:entering|fully shown|exiting) draw/.test(description)) advance();
          return value;
        }
        advance();
      }
      throw new Error('Timeout: ' + description);
    },
    check: async (name, operation) => {await operation(); checks.push(name);},
  };
  return {ctx, calls, checks, samples, phaseFrames, screen: () => screen};
}

test('the real helper drives all four Native lifecycles and the blocked queue only with semantic fixture buttons and USER reads', async () => {
  for (const target of ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric']) {
    const fixture = nativeFixture({target}); await uiToastLifecycleActions(fixture.ctx);
    assert.equal(fixture.checks.length, 5); assert(fixture.checks.every(name => name.startsWith('HUD: native legacy')));
    assert.deepEqual(fixture.samples.filter(row => row.case === 'native_toast_lifecycle').map(row => row.kind), legacyToastKinds);
    const queue = fixture.samples.find(row => row.case === 'native_toast_queue_boundary');
    assert.equal(queue.blockers.length, 5); assert.equal(queue.observations.length, 3);
    assert(queue.observations.every(row => row.recipeNodes === 0 && row.visibleBlockers === 5 && !row.forbiddenTextPresent));
    assert.equal(queue.queuedRecipe.entering.contextKey, queue.queuedRecipe.full.contextKey);
    assert.equal(queue.queuedRecipe.full.contextKey, queue.queuedRecipe.exiting.contextKey);
    assert.equal(fixture.screen(), 'AutomationProbeScreen');
    assert.deepEqual(fixture.calls.filter(call => call.method).map(call => call.path),
      fixture.calls.filter(call => call.method).map(() => '/v0/input/state'));
  }
});

test('missing entry, replacement owner and private partial text fail and still reset only the owned fixture', async () => {
  for (const option of [{noEntering: true}, {changedOwner: true}, {partialLeak: true}]) {
    const fixture = nativeFixture(option); await assert.rejects(uiToastLifecycleActions(fixture.ctx));
    assert.equal(fixture.screen(), 'AutomationProbeScreen');
    assert.equal(fixture.calls.filter(call => call.button === 'Clear Lifecycle Toasts').length, 2);
  }
});

test('queued Recipe visibility cannot pass and is not retried or inferred from queue submission', async () => {
  const fixture = nativeFixture({queueLeak: true}); await assert.rejects(uiToastLifecycleActions(fixture.ctx), /Queued Recipe type/);
  assert.equal(fixture.calls.filter(call => call.button === 'Queue Recipe').length, 1);
  assert.equal(fixture.calls.filter(call => call.button === 'Release Blockers').length, 0);
  assert.equal(fixture.samples.filter(row => row.case === 'native_toast_queue_boundary').length, 0);
  assert.equal(fixture.screen(), 'AutomationProbeScreen');
});

test('filtered-out checks do not open, clear or observe a fixture as an unrecorded side effect', async () => {
  const fixture = nativeFixture(); fixture.ctx.check = async () => {};
  await uiToastLifecycleActions(fixture.ctx);
  assert.deepEqual(fixture.calls, []); assert.deepEqual(fixture.samples, []);
  assert.equal(fixture.screen(), 'AutomationProbeScreen');
});

test('a pre-existing movement tutorial is reset only inside the owned fixture and is not called gameplay completion', async () => {
  const fixture = nativeFixture({initialUnrelatedToast: true});
  await uiToastLifecycleActions(fixture.ctx);
  const entered = fixture.calls.findIndex(call => call.button === 'Toast Lifecycle Fixture');
  const cleared = fixture.calls.findIndex(call => call.button === 'Clear Lifecycle Toasts');
  const arranged = fixture.calls.findIndex(call => call.button === 'System Lifecycle');
  assert(entered >= 0 && cleared > entered && arranged > cleared);
  assert.equal(fixture.calls.filter(call => call.button === 'Clear Lifecycle Toasts').length, 2);
  assert.equal(fixture.samples.length, 5);
  for (const sample of fixture.samples) {
    assert.equal(sample.fixtureReset, true); assert.equal(sample.gameplayTutorialCompletionClaimed, false);
    assert.equal(sample.gameplayUnlockChanged, false);
  }
  assert.equal(fixture.screen(), 'AutomationProbeScreen');
});

test('Native direction changes can still fully display text before actual clipped entry and exit frames', async () => {
  const fixture = nativeFixture({firstEnterStillFull: true, firstExitStillFull: true});
  await uiToastLifecycleActions(fixture.ctx);
  const lifecycles = [...fixture.samples.filter(row => row.case === 'native_toast_lifecycle'),
    fixture.samples.find(row => row.case === 'native_toast_queue_boundary').queuedRecipe];
  assert.equal(lifecycles.length, 5);
  for (const sample of lifecycles) for (const phase of ['entering', 'exiting']) {
    const frames = fixture.phaseFrames.filter(frame => frame.contextKey === sample.contextKey && frame.renderPhase === phase);
    assert(frames.length >= 2, 'The helper must wait past the still-fully-visible direction boundary');
    assert(['static', 'header'].includes(frames[0].textPhase));
    assert(frames.some(frame => frame.textPhase === 'withheld_partial_or_occluded'));
    assert.equal(sample[phase].textPhase, 'withheld_partial_or_occluded'); assert.equal(sample[phase].label, '');
  }
});
