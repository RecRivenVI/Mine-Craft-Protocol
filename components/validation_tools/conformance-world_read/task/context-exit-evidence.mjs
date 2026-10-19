import assert from 'node:assert/strict';

// This confirms a cleanup destination, not successful execution of the cancelled click.
export function contextExitEvidence(before, after, tree, error) {
  assert.equal(error.status, 409);
  assert(['REQUEST_CANCELLED', 'CONTROL_INPUT_GENERATION_CHANGED'].includes(error.body?.error));
  assert.equal(error.body.executionOutcome, 'not_confirmed');
  assert.equal(error.body.retrySafe, false);
  assert(tree.screenClass.endsWith('TitleScreen'));
  assert.equal(tree.overlayIdentity, 0);
  for (const field of ['inputGeneration', 'inputContextGeneration']) {
    assert(Number.isSafeInteger(before[field]) && before[field] >= 0);
    assert(Number.isSafeInteger(after[field]) && after[field] > before[field]);
  }
  assert.equal(before.inputContextAvailable, true);
  assert.equal(after.inputContextAvailable, false);
  assert.equal(typeof before.inputContextId, 'string'); assert(before.inputContextId.length > 0);
  assert.equal(typeof after.inputContextId, 'string'); assert(after.inputContextId.length > 0);
  assert.notEqual(after.inputContextId, before.inputContextId);
  assert.equal(after.pressedKeyCount, 0); assert.equal(after.pressedButtonCount, 0);
  assert.equal(after.inputCleanupPending, false); assert.equal(after.inputCleanupFailed, false);
  return {outcome: 'world_exit_independently_observed', requestOutcome: 'not_confirmed', requestReplayed: false,
    before, after, screenClass: tree.screenClass, overlayIdentity: tree.overlayIdentity, error};
}
