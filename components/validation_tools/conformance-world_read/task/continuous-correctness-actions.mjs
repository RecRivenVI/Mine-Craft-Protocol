import assert from 'node:assert/strict';

// Functional duration only: no throughput, latency distribution or attack-ratio measurements.
export async function continuousCorrectnessActions({api, target, fixtureInput, mode, acquire, release,
  click, clickNode, wait, nodes, check, actionSamples}) {
  const sleep = millis => new Promise(done => setTimeout(done, millis));
  let ownsLease = false, leaseId;
  async function until(read, predicate, label) {
    const deadline = performance.now() + 10000;
    do {const value = await read(); if (predicate(value)) return value; await sleep(100);} while (performance.now() < deadline);
    assert.fail(label);
  }
  async function send(steps) {
    const started = await api('POST', '/v0/pipelines', {steps, timeoutMs: 10000, cleanupOnComplete: false});
    const deadline = performance.now() + 12000;
    let outcome;
    do {
      outcome = await api('POST', `/v0/operations/${started.operationId}/wait`, {timeoutMs: 3000});
      if (['completed', 'failed', 'cancelled'].includes(outcome.state)) break;
    } while (performance.now() < deadline);
    assert.equal(outcome.state, 'completed', JSON.stringify(outcome));
    for (const step of outcome.result.steps) {
      assert.equal(step.result.inputOrigin, 'AGENT_ROUTED');
      assert.equal(step.result.entryLayer, 'GAME_ROUTED_RAW');
      assert.equal(step.result.hostCursorUsed, false);
      assert.equal(step.result.directMutationUsed, false);
    }
    return started.operationId;
  }
  const key = (code, action) => ({type: 'key', key: code, action, scanCode: 0, modifiers: 0});
  try {
    assert.equal(fixtureInput.policy, 'immutable_baseline_restored');
    assert.equal(fixtureInput.target, target);
    assert.equal(fixtureInput.credentialsCopied, false);
    await acquire(); ownsLease = true;
    await click('menu.singleplayer', 'Singleplayer');
    const list = await wait(tree => tree.screenClass.endsWith('SelectWorldScreen')
      && nodes(tree).some(node => node.worldName === 'World-Read-Alpha'), 'continuous correctness populated world list');
    await clickNode(nodes(list).find(node => node.worldName === 'World-Read-Alpha'));
    await click('selectWorld.select', 'Play Selected World');
    await wait(tree => !tree.screenClass, 'continuous correctness fixed world entered');
    await release(); ownsLease = false; await mode('OPERATE');
    const original = await api('GET', '/v0/player?category=USER');
    const x = Math.floor(original.x), z = Math.floor(original.z), y = Math.min(200, Math.max(64, Math.floor(original.y) + 8));
    const commands = [
      `fill ${x - 8} ${y} ${z - 8} ${x + 8} ${y + 4} ${z + 8} minecraft:stone`,
      `fill ${x - 7} ${y + 1} ${z - 7} ${x + 7} ${y + 4} ${z + 7} minecraft:air`,
      'gamemode survival @s', `tp @s ${x + .5} ${y + 1} ${z + .5} 0 0`
    ];
    for (const command of commands) {
      const receipt = await api('POST', '/v0/command/player', {command, route: 'NATIVE'});
      assert.equal(receipt.inputDispatched, false); assert.equal(receipt.effectConfirmed, false);
    }
    const settled = await until(() => api('GET', '/v0/player?category=USER'), player => player.available
      && player.onGround && !player.flying && player.gameMode === 'survival'
      && Math.abs(player.x - x - .5) < .05 && Math.abs(player.y - y - 1) < .05
      && Math.abs(player.z - z - .5) < .05, 'Diagnostic containment was not received');
    for (const [dx, dz] of [[0, 0], [-8, 0], [8, 0], [0, -8], [0, 8]])
      await until(() => api('GET', `/v0/world/block?category=USER&x=${x + dx}&y=${y + (dx || dz ? 1 : 0)}&z=${z + dz}`),
        block => block.available && block.block === 'minecraft:stone', 'Diagnostic floor/wall was not received');
    actionSamples.push({case: 'continuous_correctness_arrange', original, settled, commands,
      arrangeIsGameplayAcceptance: false, authority: 'diagnostic_native_commands_in_restored_copy'});
    await acquire(); ownsLease = true;
    leaseId = (await api('GET', '/v0/control/status')).leaseId;
    await check('45 seconds of real routed movement and look with independent USER observations', async () => {
      const observations = [], began = performance.now(), finish = began + 45000;
      let previousKey, index = 0;
      while (performance.now() < finish) {
        const direction = index % 2 ? 83 : 87;
        const steps = previousKey === undefined ? [key(direction, 1)] : [key(previousKey, 0), key(direction, 1)];
        steps.push({type: 'mouse.delta', dx: index % 2 ? -3 : 3, dy: 0});
        const operationId = await send(steps); previousKey = direction;
        const input = await api('GET', '/v0/input/state');
        assert.equal(input.pressedKeyCount, 1); assert.equal(input.pressedButtonCount, 0);
        assert(input.pressedKeys.includes(direction));
        const player = await api('GET', '/v0/player?category=USER');
        const scene = await api('GET', '/v0/world/scene?category=USER&radius=2&blockLimit=1&entityLimit=8');
        assert.equal(player.dataCategory, 'USER'); assert.equal(scene.authority, 'CLIENT_KNOWN');
        assert.equal(scene.chunkLoadRequested, false);
        assert.equal(player.dimension, settled.dimension); assert(player.onGround); assert.equal(player.flying, false);
        assert(Math.abs(player.x - x - .5) < 7 && Math.abs(player.z - z - .5) < 7 && Math.abs(player.y - y - 1) < .15,
          'Player left diagnostic containment');
        assert.equal((await api('GET', '/v0/control/status')).leaseId, leaseId);
        observations.push({operationId, direction, elapsedMillis: performance.now() - began, input, player});
        index++;
        await sleep(Math.max(0, Math.min(finish, began + index * 1000) - performance.now()));
      }
      assert(observations.length >= 20);
      assert(observations.at(-1).player.clientTick > observations[0].player.clientTick);
      const distance = observations.slice(1).reduce((sum, row, i) => sum
        + Math.hypot(row.player.x - observations[i].player.x, row.player.z - observations[i].player.z), 0);
      assert(distance > 1, 'Actual player movement was not observed');
      actionSamples.push({case: 'continuous_correctness_window', requestedMillis: 45000,
        observedMillis: performance.now() - began, observations, clientObservedDistance: distance,
        performanceMeasured: false, serverEffectConfirmed: false});
    });
    await check('strong stop and release leave no held or late dispatched input', async () => {
      const stopped = await api('POST', '/v0/input/release-all', {});
      const released = await api('POST', '/v0/wait/until', {condition: {type: 'input.released'}, timeoutMs: 5000});
      assert.equal(released.passed, true);
      await release(); ownsLease = false;
      const before = await api('GET', '/v0/input/state');
      await sleep(1000);
      const after = await api('GET', '/v0/input/state'), control = await api('GET', '/v0/control/status');
      assert.equal(before.pressedKeyCount, 0); assert.equal(after.pressedKeyCount, 0);
      assert.equal(before.pressedButtonCount, 0); assert.equal(after.pressedButtonCount, 0);
      assert.equal(after.inputDispatchSequence, before.inputDispatchSequence);
      assert.equal(after.inputCleanupPending, false); assert.equal(control.mode, 'READ');
      actionSamples.push({case: 'continuous_correctness_stop', stopped, released, before, after, control, postReleaseInput: 0});
    });
  } finally {
    if (ownsLease) {await api('POST', '/v0/input/release-all', {}); await release();}
  }
}
