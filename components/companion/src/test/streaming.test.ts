import assert from 'node:assert/strict';
import { once } from 'node:events';
import { createServer } from 'node:http';
import test from 'node:test';

import { loadConfig } from '../config.js';
import { RuntimeClient, RuntimeError } from '../runtime-client.js';
import { asToolResult } from '../result.js';

const token = 'stream-budget-token';

test('Native hit errors retain only bounded client pick facts through RuntimeClient and MCP results',async()=>{
  const hit={kind:'block',source:'native_interaction_pick',dataCategory:'USER',authority:'CLIENT_KNOWN',distance:2.5,position:{x:.5,y:81.5,z:2,private:'secret'},block:{x:0,y:81,z:2},face:'north',guard:'secret'};
  let requests=0;const server=createServer((_request,response)=>{requests++;response.writeHead(409,{'content-type':'application/json'});response.end(JSON.stringify({error:'WORLD_TARGET_NOT_HIT',message:'Native pick was blocked',requestId:'native-hit',actualHit:hit}));});
  server.listen(0,'127.0.0.1');await once(server,'listening');const address=server.address();if(!address||typeof address==='string')throw Error('Hit fixture did not bind');
  const client=new RuntimeClient(loadConfig({MCP_MINECRAFT_BASE_URL:`http://127.0.0.1:${address.port}`,MCP_MINECRAFT_TOKEN:token}));
  try {const value=await asToolResult(()=>client.json('POST','/v0/test-hit',{}));assert.equal(value.isError,true);const error=(value.structuredContent as {error:{code:string;actualHit:Record<string,unknown>}}).error;assert.equal(error.code,'WORLD_TARGET_NOT_HIT');assert.equal(error.actualHit.kind,'block');assert.equal(error.actualHit.face,'north');assert(!JSON.stringify(value).includes('secret'));assert.equal(requests,1);}
  finally {server.close();await once(server,'close');}
  assert.equal(new RuntimeError('OTHER',409,'unrelated',undefined,{actualHit:hit}).actualHit,undefined);
  assert.equal(new RuntimeError('WORLD_TARGET_NOT_HIT',409,'invalid',undefined,{actualHit:{...hit,distance:100}}).actualHit,undefined);
});

for (const transport of ['json', 'binary']) {
  test(`Native rate quota remains structured in MCP for ${transport} responses without retry`, async () => {
    const budget = { scope: 'principal', category: 'pipeline', operationCategory: 'pipeline', capacity: 8,
      remaining: 0, refillPerSecond: 2, cost: 1 };
    let attempts = 0;
    const server = createServer((_request, response) => {
      attempts++;
      response.writeHead(429, { 'content-type': 'application/json' }).end(JSON.stringify({
        error: 'EXPENSIVE_OPERATION_RATE_LIMITED', message: 'budget', requestId: 'limited-native',
        retryAfterMillis: 251, rateLimit: { ...budget, unexpected: 'discard' }
      }));
    });
    server.listen(0, '127.0.0.1'); await once(server, 'listening');
    const address = server.address(); if (!address || typeof address === 'string') throw new Error('test did not bind');
    try {
      const client = new RuntimeClient(loadConfig({ MCP_MINECRAFT_BASE_URL: `http://127.0.0.1:${address.port}`, MCP_MINECRAFT_TOKEN: token }));
      const result = await asToolResult(async () => {
        if (transport === 'binary') { await client.binary('GET', '/v0/capture'); return {}; }
        return client.json('POST', '/v0/pipelines', { steps: [] });
      });
      const error = (result.structuredContent as { error: Record<string, unknown> }).error;
      assert.equal(result.isError, true); assert.equal(error.status, 429);
      assert.equal(error.code, 'EXPENSIVE_OPERATION_RATE_LIMITED'); assert.equal(error.retryAfterMillis, 251);
      assert.deepEqual(error.rateLimit, budget); assert.equal(error.requestId, 'limited-native');
      assert.equal(attempts, 1); assert.equal(error.retrySafe, undefined);
    } finally { server.close(); await once(server, 'close'); }
  });
}

test('Malformed quota hints are not promoted into machine retry guidance', () => {
  const rateLimit = { scope: 'principal', category: 'world', operationCategory: 'world', capacity: 60,
    remaining: 0, refillPerSecond: 30, cost: 1 };
  for (const details of [
    { retryAfterMillis: -1, rateLimit }, { retryAfterMillis: 10, rateLimit: { ...rateLimit, remaining: 61 } },
    { retryAfterMillis: 10, rateLimit: { ...rateLimit, refillPerSecond: 0 } }
  ]) {
    const error = new RuntimeError('RATE_LIMITED', 429, 'budget', undefined, details);
    assert.equal(error.retryAfterMillis, undefined); assert.equal(error.rateLimit, undefined);
  }
});

test('Native cancellation retains unconfirmed outcome in MCP without claiming safe replay', async () => {
  const server = createServer((_request, response) => {
    response.writeHead(503, { 'content-type': 'application/json' }).end(JSON.stringify({
      error: 'RUNTIME_SHUTTING_DOWN', message: 'Pipeline cancelled', requestId: 'native-quit',
      executionOutcome: 'not_confirmed', retrySafe: false
    }));
  });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  const address = server.address();
  if (!address || typeof address === 'string') throw new Error('test server did not bind');
  try {
    const client = new RuntimeClient(loadConfig({ MCP_MINECRAFT_BASE_URL: `http://127.0.0.1:${address.port}`, MCP_MINECRAFT_TOKEN: token }));
    const result = await asToolResult(() => client.json('POST', '/v0/ui/action', { action: 'click' }));
    assert.equal(result.isError, true);
    const error = (result.structuredContent as { error: Record<string, unknown> }).error;
    assert.equal(error.code, 'RUNTIME_SHUTTING_DOWN');
    assert.equal(error.status, 503);
    assert.equal(error.executionOutcome, 'not_confirmed');
    assert.equal(error.retrySafe, false);
    assert.equal(error.requestId, 'native-quit');
  } finally {
    server.close();
    await once(server, 'close');
  }
});

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
