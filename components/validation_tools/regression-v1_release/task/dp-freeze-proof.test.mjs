import assert from 'node:assert/strict';
import test from 'node:test';
import {readFile} from 'node:fs/promises';
import {continuousCorrectnessProof} from './dp-freeze-proof.mjs';
import {eWorldUnexecutedReadinessUpdate} from './e-world-proof.mjs';
import {sha256} from './alpha-candidate.mjs';
import {previousProbePublicationSource, probePublicationUpdate} from './probe-publication-proof.mjs';

function result() {
  return {actionSamples: [
    {case: 'continuous_correctness_window', requestedMillis: 45000, observedMillis: 45010,
      observations: Array.from({length: 30}, (_, clientTick) => ({player: {clientTick}})),
      clientObservedDistance: 20, performanceMeasured: false},
    {case: 'continuous_correctness_stop', postReleaseInput: 0,
      before: {pressedKeyCount: 0, pressedButtonCount: 0, inputDispatchSequence: 100},
      after: {pressedKeyCount: 0, pressedButtonCount: 0, inputDispatchSequence: 100, inputCleanupPending: false},
      control: {mode: 'READ'}}
  ]};
}

test('functional freeze requires duration, real tick/movement progress and an independently released input barrier', () => {
  assert.deepEqual(continuousCorrectnessProof(result()), {requestedMillis: 45000, performanceMeasured: false, postReleaseInput: 0});
  for (const change of [row => {row.observedMillis = 29999;}, row => {row.observations = [];},
    row => {row.observations.at(-1).player.clientTick = 0;}, row => {row.clientObservedDistance = 0;},
    row => {row.performanceMeasured = true;}]) {
    const candidate = result(); change(candidate.actionSamples[0]);
    assert.throws(() => continuousCorrectnessProof(candidate));
  }
});

test('zero-held samples cannot hide late dispatch, pending cleanup or an unreleased mode', () => {
  for (const change of [row => {row.after.inputDispatchSequence++;}, row => {row.after.pressedKeyCount = 1;},
    row => {row.after.pressedButtonCount = 1;}, row => {row.after.inputCleanupPending = true;},
    row => {row.control.mode = 'TAKEOVER';}]) {
    const candidate = result(); change(candidate.actionSamples[1]);
    assert.throws(() => continuousCorrectnessProof(candidate));
  }
});

test('known updates to an unexecuted helper require exact prior bytes and cannot cover its executed scenario', async () => {
  const path = 'components/validation_tools/conformance-world_read/task/resource-version-actions.mjs';
  const bytes = await readFile(new URL('../../conformance-world_read/task/resource-version-actions.mjs', import.meta.url));
  const old = bytes.toString().replace('assert.equal(contract.operations.length,27);', 'assert.equal(contract.operations.length,24);')
    .replace("'provider.mutate','player.transform.set','entity.transform.set',", '');
  const recorded = sha256(Buffer.from(old));
  assert(eWorldUnexecutedReadinessUpdate(path, bytes, recorded, 'full', {scenario: 'full'}));
  const withoutPacing = old.replace("try {await paced();await api('POST','/v0/debug/disarm');setArm(undefined);}catch(error){cleanupErrors.push(`disarm: ${error}`);}",
    "try {await api('POST','/v0/debug/disarm');setArm(undefined);}catch(error){cleanupErrors.push(`disarm: ${error}`);}");
  assert(eWorldUnexecutedReadinessUpdate(path, bytes, sha256(Buffer.from(withoutPacing)), 'full', {scenario: 'full'}));
  assert.equal(eWorldUnexecutedReadinessUpdate(path, bytes, recorded, 'resource-version', {scenario: 'resource-version'}), false);
  assert.equal(eWorldUnexecutedReadinessUpdate(path, bytes, recorded, 'full', {scenario: 'resource-version'}), false);
  assert.equal(eWorldUnexecutedReadinessUpdate(path, Buffer.concat([bytes, Buffer.from('\n// Changed Act\n')]), recorded, 'full', {scenario: 'full'}), false);
});

test('probe reuse recognizes only the complete publication patch, never callback, counter or product changes', async () => {
  for (const target of ['1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric']) {
    const path = `versions/${target}/src/probe/java/io/github/recrivenvi/minecraftprotocol/probe/validation/CommandConfigurationProbe.java`;
    const bytes = await readFile(new URL('../../../../' + path, import.meta.url));
    const previous = previousProbePublicationSource(path, bytes);
    assert(previous); const recorded = sha256(previous);
    assert(probePublicationUpdate(path, bytes, recorded));
    assert.equal(probePublicationUpdate(path.replace('/src/probe/', '/src/main/'), bytes, recorded), false);
    assert.equal(probePublicationUpdate(path, Buffer.from(bytes.toString().replace('nativePlayerCommands++;', 'nativePlayerCommands += 2;')), recorded), false);
    assert.equal(probePublicationUpdate(path, Buffer.from(bytes.toString().replace('publicationAttempt == 24', 'publicationAttempt == 2400')), recorded), false);
  }
});
