import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createServer } from 'node:http';
import test from 'node:test';

import { loadConfig } from '../config.js';
import { RuntimeClient, RuntimeError } from '../runtime-client.js';

const token = 'stream-budget-token';

test('RuntimeClient enforces declared and chunked response budgets while streaming', async () => {
  const server = createServer((request, response) => {
    if (request.headers.authorization !== `Bearer ${token}`) {
      response.writeHead(401).end();
      return;
    }
    if (request.url === '/v0/small') {
      const bytes = Buffer.alloc(32, 1);
      response.writeHead(200, { 'content-type': 'application/octet-stream', 'content-length': bytes.length });
      response.end(bytes);
      return;
    }
    if (request.url === '/v0/near-limit') {
      const bytes = Buffer.alloc(1024, 2);
      response.writeHead(200, { 'content-type': 'application/octet-stream', 'content-length': bytes.length });
      response.end(bytes);
      return;
    }
    if (request.url === '/v0/declared-over') {
      response.writeHead(200, { 'content-type': 'application/octet-stream', 'content-length': 2048 });
      response.end(Buffer.alloc(2048, 3));
      return;
    }
    response.writeHead(200, { 'content-type': 'application/octet-stream', 'transfer-encoding': 'chunked' });
    for (let index = 0; index < 8; index++) response.write(Buffer.alloc(256, index));
    response.end();
  });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  const address = server.address();
  if (!address || typeof address === 'string') throw new Error('test server did not bind');
  const client = new RuntimeClient(loadConfig({
    MCP_MINECRAFT_BASE_URL: `http://127.0.0.1:${address.port}`,
    MCP_MINECRAFT_TOKEN: token
  }));
  try {
    assert.equal((await client.binary('GET', '/v0/small', { maxResponseBytes: 1024 })).bytes.length, 32);
    assert.equal((await client.binary('GET', '/v0/near-limit', { maxResponseBytes: 1024 })).bytes.length, 1024);
    await assert.rejects(
      client.binary('GET', '/v0/declared-over', { maxResponseBytes: 1024 }),
      (error: unknown) => error instanceof RuntimeError && error.code === 'COMPANION_RESPONSE_TOO_LARGE');
    await assert.rejects(
      client.binary('GET', '/v0/chunked-over', { maxResponseBytes: 1024 }),
      (error: unknown) => error instanceof RuntimeError && error.code === 'COMPANION_RESPONSE_TOO_LARGE');
  } finally {
    server.close();
    await once(server, 'close');
  }
});
