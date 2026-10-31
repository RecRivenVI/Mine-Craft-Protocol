import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {sha256} from './alpha-candidate.mjs';
import {eWorldRunProof, eWorldUnexecutedReadinessUpdate} from './e-world-proof.mjs';
import {coreShowcaseProof} from './alpha-evidence.mjs';
import {probePublicationUpdate} from './probe-publication-proof.mjs';

export function continuousCorrectnessProof(result) {
  const window = result.actionSamples.find(row => row.case === 'continuous_correctness_window');
  assert.equal(window.requestedMillis, 45000); assert(window.observedMillis >= 45000);
  assert(window.observations.length >= 20); assert.equal(window.performanceMeasured, false);
  assert(window.clientObservedDistance > 1);
  assert(window.observations.at(-1).player.clientTick > window.observations[0].player.clientTick);
  const stop = result.actionSamples.find(row => row.case === 'continuous_correctness_stop');
  assert.equal(stop.postReleaseInput, 0); assert.equal(stop.before.pressedKeyCount, 0);
  assert.equal(stop.after.pressedKeyCount, 0); assert.equal(stop.before.pressedButtonCount, 0);
  assert.equal(stop.after.pressedButtonCount, 0); assert.equal(stop.control.mode, 'READ');
  assert.equal(stop.after.inputDispatchSequence, stop.before.inputDispatchSequence);
  assert.equal(stop.after.inputCleanupPending, false);
  return {requestedMillis: 45000, performanceMeasured: false, postReleaseInput: 0};
}

export async function collectDpFreezeProof(repo, artifacts, sourceHashes) {
  const proofs = [];
  for (const artifact of artifacts) {
    const target = artifact.target;
    for (const suffix of ['continuous-correctness', 'integrated-peer', 'mode-collaboration', 'resource-version',
      'command-discovery-batch', 'server-completion', 'region-stream', 'block-entity-selector', 'command-configuration']) {
      const resultPath = `validations/conformance-world_read/result/${target}-${suffix}.json`;
      const bytes = await readFile(resolve(repo, resultPath)), result = JSON.parse(bytes);
      eWorldRunProof(result, target, suffix);
      const recordedToolUpdates = [];
      for (const [path, hash] of Object.entries(result.sourceHashes)) {
        if (path.includes('/src/test/')) continue;
        const bytes = await readFile(resolve(repo, path));
        const publicationOnly = suffix !== 'command-configuration' && probePublicationUpdate(path, bytes, hash);
        assert(sha256(bytes) === hash || publicationOnly || eWorldUnexecutedReadinessUpdate(path, bytes, hash, suffix, result),
          'Stale DP executed source: ' + path);
        if (sha256(bytes) !== hash) recordedToolUpdates.push({source: path,
          reason: publicationOnly ? 'probe_json_publication_only' : 'exact_reverse_hash_of_unexecuted_catalogue_or_disarm_pacing_only',
          ...(publicationOnly ? {previousSha256: hash, currentSha256: sha256(bytes),
            currentProducerProof: `validations/conformance-world_read/result/${target}-command-configuration.json`} : {})});
      }
      for (const [path, hash] of Object.entries(sourceHashes)) {
        if (path.startsWith(`versions/${target}/src/`) || path.startsWith('components/runtime_core/src/main/')
          || path.startsWith('components/runtime_safety/src/main/'))
          assert.equal(result.sourceHashes[path], hash, 'Missing DP production source: ' + path);
      }
      if (suffix === 'continuous-correctness') {
        continuousCorrectnessProof(result);
      }
      proofs.push({target, scenario: suffix, result: resultPath, resultSha256: sha256(bytes), sourceMatchesCurrent: true,
        ...(recordedToolUpdates.length ? {recordedToolUpdates} : {})});
    }
    const showcasePath = `validations/feature-control_ui_showcase/result/${target}.json`;
    const showcaseBytes = await readFile(resolve(repo, showcasePath)), showcase = JSON.parse(showcaseBytes);
    const showcaseProof = coreShowcaseProof(showcase, target);
    const probePublicationUpdates = [];
    for (const [path, hash] of Object.entries(showcase.sourceHashes)) {
      const bytes = await readFile(resolve(repo, path));
      assert(sha256(bytes) === hash || probePublicationUpdate(path, bytes, hash), 'Stale Core Showcase source: ' + path);
      if (sha256(bytes) !== hash) probePublicationUpdates.push({source: path, reason: 'probe_json_publication_only',
        previousSha256: hash, currentSha256: sha256(bytes),
        currentProducerProof: `validations/conformance-world_read/result/${target}-command-configuration.json`});
    }
    proofs.push({target, scenario: 'core-showcase', result: showcasePath, resultSha256: sha256(showcaseBytes),
      sourceMatchesCurrent: true, ...showcaseProof, ...(probePublicationUpdates.length ? {probePublicationUpdates} : {})});
    for (const suffix of ['server-debug', 'optional-install']) {
      const resultPath = `validations/integration-server_peer/result/${target}-${suffix}.json`;
      const bytes = await readFile(resolve(repo, resultPath)), result = JSON.parse(bytes);
      assert.equal(result.target, target); assert.equal(result.status, 'PASS');
      assert.equal(result.jarSha256, artifact.sha256); assert.equal(result.persistentWriteCalls, 0);
      if (suffix === 'optional-install') {
        assert.equal(result.developmentClasspath, false); assert.equal(result.allOwnedProcessesExited, true);
        assert.equal(result.cases.length, 3); assert(result.cases.every(row => row.status === 'PASS'));
      } else assert.equal(result.exitCode, 0);
      proofs.push({target, scenario: suffix, result: resultPath, resultSha256: sha256(bytes), jarSha256: artifact.sha256});
    }
  }
  return {scope: 'developer_preview_automatic_freeze', proofs, humanAcceptance: 'NOT_PERFORMED',
    fullMilestonesComplete: false, performanceMeasured: false, persistentWriteCalls: 0};
}
