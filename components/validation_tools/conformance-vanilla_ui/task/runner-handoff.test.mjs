import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import vm from 'node:vm';
import test from 'node:test';

const source = await readFile(new URL('./run-vanilla-ui.mjs', import.meta.url), 'utf8');
const start = source.indexOf('async function editorHandoff(');
const end = source.indexOf('\n}\ntry{', start);
assert(start >= 0 && end > start, 'Production handoff function boundary');
const handoff = new vm.Script('(' + source.slice(start, end + 2) + ')');

function controller({ consumerError, recoveryError, humanOverride = false } = {}) {
  const calls = [], logs = [];
  const context = {
    assert, resolve, args: { scenario: 'all' }, target: '26.3-fabric',
    repo: 'owned-repo', base: 'owned-runtime', token: 'test-token', instance: 'owned-instance',
    lease: 'old-lease', taskId: 'old-task', taskStarted: 0, failure: undefined, editorSamples: [],
    console: { error: (...values) => logs.push(values) },
    editorConsumers: async () => {
      calls.push('consumer');
      if (consumerError) throw consumerError;
      return [{ status: 'PASS', editor: 'debug_hud_title' }];
    },
    api: async (method, path, body) => {
      calls.push([method, path, body]);
      if (method === 'GET') return { mode: 'READ', reconsentRequired: humanOverride };
      if (path === '/v0/control/task' && body.action === 'begin') {
        if (recoveryError) throw recoveryError;
        return { taskId: 'new-task' };
      }
      if (path === '/v0/control/acquire') {
        assert.equal(body.taskId, 'new-task');
        return { leaseId: 'new-lease' };
      }
      return {};
    }
  };
  const run = handoff.runInContext(vm.createContext(context));
  return { context, calls, logs, run: () => run('debug_hud_title') };
}

test('production editor handoff replaces the ended task before controller work resumes', async () => {
  const fixture = controller();
  await fixture.run();
  assert.equal(fixture.context.lease, 'new-lease');
  assert.equal(fixture.context.taskId, 'new-task');
  assert.equal(fixture.context.editorSamples.length, 1);
  assert.equal(fixture.context.failure, undefined);
});

test('a consumer failure survives recovery and never becomes a passing sample', async () => {
  const primary = new Error('original consumer rejection');
  const fixture = controller({ consumerError: primary });
  await assert.rejects(fixture.run(), error => error === primary);
  assert.equal(fixture.context.failure, primary);
  assert.equal(fixture.context.taskId, 'new-task');
  assert.equal(fixture.context.lease, 'new-lease');
  assert.equal(fixture.context.editorSamples.length, 0);
});

test('a recovery failure is reported without replacing the original consumer failure', async () => {
  const primary = new Error('original consumer rejection');
  const secondary = new Error('controller recovery rejected');
  const fixture = controller({ consumerError: primary, recoveryError: secondary });
  await assert.rejects(fixture.run(), error => error === primary);
  assert.equal(fixture.context.failure, primary);
  assert.equal(fixture.context.taskId, undefined);
  assert.equal(fixture.context.lease, undefined);
  assert(fixture.logs.some(values => values.includes(secondary)));
});

test('handoff recovery never begins a task or acquires input after human override', async () => {
  for (const consumerError of [undefined, new Error('interrupted consumer')]) {
    const fixture = controller({ consumerError, humanOverride: true });
    await assert.rejects(fixture.run(), error => consumerError ? error === consumerError : /human override/.test(error.message));
    assert(!fixture.calls.some(call => Array.isArray(call) && (call[1] === '/v0/control/acquire' || call[2]?.action === 'begin')));
    assert.equal(fixture.context.taskId, undefined);
    assert.equal(fixture.context.lease, undefined);
  }
});
