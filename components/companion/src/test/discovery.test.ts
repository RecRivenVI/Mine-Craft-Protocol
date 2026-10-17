import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
import { once } from 'node:events';
import { mkdirSync, mkdtempSync, realpathSync, writeFileSync } from 'node:fs';
import { createServer } from 'node:http';
import { resolve } from 'node:path';
import test from 'node:test';
import { loadConfig } from '../config.js';
import { RuntimeClient } from '../runtime-client.js';

async function fixture() {
  const id = randomUUID();
  const calls: string[] = [];
  let servedId: string = id;
  const server = createServer((request, response) => {
    calls.push(request.method + ' ' + request.url);
    assert.equal(request.headers.authorization, 'Bearer discovery-test-token');
    assert.equal(request.headers['x-mcp-runtime-instance'], id);
    response.setHeader('x-mcp-runtime-instance', servedId);
    response.setHeader('content-type', 'application/json');
    response.end(JSON.stringify({ target: 'test-target', runtimeConnection: { runtimeInstanceId: servedId } }));
  });
  server.listen(0, '127.0.0.1'); await once(server, 'listening');
  const address = server.address(); assert(address && typeof address !== 'string');
  mkdirSync('build/discovery-tests', { recursive: true });
  const directory = realpathSync(mkdtempSync(resolve('build/discovery-tests/instance-')));
  mkdirSync(resolve(directory, 'minecraft-protocol'));
  const record = {
    schemaVersion: 1, runtimeInstanceId: id, target: 'test-target', gameDirectory: directory,
    port: address.port, baseUrl: `http://127.0.0.1:${address.port}`, pid: process.pid,
    startedAt: new Date().toISOString(), processStartedAt: new Date(Date.now() - process.uptime() * 1000).toISOString(),
    tokenFile: 'token', state: 'RUNNING'
  };
  const write = () => writeFileSync(resolve(directory, 'minecraft-protocol/endpoint.json'), JSON.stringify(record));
  write(); writeFileSync(resolve(directory, 'minecraft-protocol/token'), 'discovery-test-token');
  const env = { MCP_MINECRAFT_INSTANCE_DIR: directory, MCP_MINECRAFT_TARGET: 'test-target' };
  return { id, record, write, env, calls, served: (value: string) => { servedId = value; }, close: async () => { server.closeAllConnections(); await new Promise<void>(done => server.close(() => done())); } };
}

test('instance directory discovery pins both the safe initial handshake and every request', async () => {
  const runtime = await fixture();
  try {
    const config = loadConfig(runtime.env);
    assert.equal(config.discovery?.runtimeInstanceId, runtime.id);
    const client = new RuntimeClient(config);
    await client.json('POST', '/v0/control/acquire', {}, { headers: { 'x-mcp-runtime-instance': 'cannot-override-pin' } });
    assert.deepEqual(runtime.calls, ['GET /v0/session', 'POST /v0/control/acquire']);
    runtime.record.runtimeInstanceId = randomUUID(); runtime.write();
    await assert.rejects(client.json('POST', '/v0/control/acquire'), { code: 'COMPANION_RUNTIME_INSTANCE_CHANGED' });
    assert.equal(runtime.calls.length, 2, 'changed discovery dispatches zero requests');
  } finally { await runtime.close(); }
});

test('wrong identity and legacy responses fail the handshake before any mutation', async () => {
  const runtime = await fixture();
  try {
    runtime.served(randomUUID());
    const client = new RuntimeClient(loadConfig(runtime.env));
    await assert.rejects(client.json('POST', '/v0/control/acquire'), { code: 'COMPANION_RUNTIME_INSTANCE_MISMATCH' });
    assert.deepEqual(runtime.calls, ['GET /v0/session']);
  } finally { await runtime.close(); }
});

test('identity must remain correct for binary resources, not only JSON responses', async () => {
  const runtime = await fixture();
  try {
    const client = new RuntimeClient(loadConfig(runtime.env));
    await client.json('GET', '/v0/session');
    runtime.served(randomUUID());
    await assert.rejects(client.binary('GET', '/v0/capture'), { code: 'COMPANION_RUNTIME_INSTANCE_MISMATCH' });
  } finally { await runtime.close(); }
});

test('discovery rejects mixed authorities, wrong Target, stale PID, foreign directory and unsafe URLs', async () => {
  const runtime = await fixture();
  try {
    assert.throws(() => loadConfig({ ...runtime.env, MCP_MINECRAFT_TOKEN: 'other' }), /do not mix/);
    assert.throws(() => loadConfig({ ...runtime.env, MCP_MINECRAFT_TARGET: 'wrong-target' }), /Invalid Runtime/);
    for (const [key, value] of Object.entries({ baseUrl: 'http://192.0.2.1:1234', gameDirectory: realpathSync('.'), tokenFile: '../token', pid: 2147483647, state: 'STOPPED', schemaVersion: 2 })) {
      const previous = runtime.record[key as keyof typeof runtime.record];
      Object.assign(runtime.record, { [key]: value }); runtime.write();
      assert.throws(() => loadConfig(runtime.env));
      Object.assign(runtime.record, { [key]: previous }); runtime.write();
    }
    writeFileSync(resolve(runtime.env.MCP_MINECRAFT_INSTANCE_DIR, 'minecraft-protocol/endpoint.json'), 'x'.repeat(16385));
    assert.throws(() => loadConfig(runtime.env), /16 KiB/);
    assert.equal(runtime.calls.length, 0);
  } finally { await runtime.close(); }
});

test('manual endpoint remains explicit; no shared port is silently guessed', () => {
  assert.throws(() => loadConfig({ MCP_MINECRAFT_TOKEN: 'token' }), /INSTANCE_DIR/);
  const config = loadConfig({ MCP_MINECRAFT_BASE_URL: 'http://127.0.0.1:25581', MCP_MINECRAFT_TOKEN: 'token' });
  assert.equal(config.baseUrl.port, '25581'); assert.equal(config.discovery, undefined);
});
