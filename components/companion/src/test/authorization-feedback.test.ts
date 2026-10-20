import assert from 'node:assert/strict';
import test from 'node:test';
import { RuntimeError } from '../runtime-client.js';
import { asToolResult } from '../result.js';

test('Companion preserves bounded page readiness reasons without hidden target data', async () => {
  for (const code of ['ASSERTION_FAILED', 'WAIT_TIMEOUT']) {
    const result = await asToolResult(async () => {
      throw new RuntimeError(code, code === 'WAIT_TIMEOUT' ? 408 : 412, 'not ready', 'ready-request', {
        readiness: {kind:'hud', ready:false, checks:{screen:true,overlay:false,viewport:true,secret:'not forwarded'},
          failures:['OVERLAY_PRESENT'], hiddenMatchCount:8, label:'private game data'}
      });
    });
    const error = (result.structuredContent as {error:Record<string,unknown>}).error;
    assert.deepEqual(error.readiness, {kind:'hud',ready:false,checks:{screen:true,overlay:false,viewport:true},failures:['OVERLAY_PRESENT']});
    assert.equal(JSON.stringify(error).includes('private game data'),false);
  }
  assert.equal(new RuntimeError('SCOPE_DENIED',403,'denied',undefined,{
    readiness:{kind:'hud',ready:false,checks:{},failures:[]}
  }).readiness,undefined);
});

test('Companion preserves safe stale revision metadata without forwarding tokens or world values', async () => {
  const result = await asToolResult(async () => {
    throw new RuntimeError('STALE_RESOURCE_REVISION', 409, 'Inspect again', 'revision-request', {
      expectedRevision: 7, actualRevision: 8, reinspectRequired: true, retrySafe: false,
      resourceVersion: { secret: 'must-not-forward' }, worldValue: 'must-not-forward'
    });
  });
  assert.equal(result.isError, true);
  const error = (result.structuredContent as { error: Record<string, unknown> }).error;
  assert.equal(error.actualRevision, 8);
  assert.equal(error.expectedRevision, 7);
  assert.equal(error.reinspectRequired, true);
  assert.equal(error.retrySafe, false);
  assert.equal(error.resourceVersion, undefined);
  assert.equal(error.worldValue, undefined);
  for (const code of ['RESOURCE_MISMATCH', 'DEBUG_NOT_ARMED', 'SCOPE_DENIED']) {
    const value = new RuntimeError(code, 409, 'denied', undefined, { actualRevision: 8, expectedRevision: 7, reinspectRequired: true, retrySafe: false });
    assert.equal(value.resourceRevision, undefined);
  }
});

test('Companion does not imply a deadline rolls back native commands or authorizes retry', async () => {
  const result = await asToolResult(async () => {
    throw new RuntimeError('REQUEST_DEADLINE_EXCEEDED', 408, 'deadline', 'batch-request', { executionOutcome: 'not_confirmed', retrySafe: false });
  });
  const error = (result.structuredContent as { error: Record<string, unknown> }).error;
  assert.equal(error.executionOutcome, 'not_confirmed');
  assert.equal(error.retrySafe, false);
});
