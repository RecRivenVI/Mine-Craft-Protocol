import assert from 'node:assert/strict';
import { loadConfig } from '../companion/dist/config.js';
import { readRuntimeDiscovery } from '../companion/dist/discovery.js';
import { RuntimeClient } from '../companion/dist/runtime-client.js';

// Automatic validation never interprets a conversation or clears a human override.
export function automaticTakeoverRequest(state, request = {}) {
  if (state?.reconsentRequired === true) throw Object.assign(new Error('用户手动结束控制；自动验证不会重新接管，需在当前对话重新取得明确同意。'), {
    code: 'USER_MANUALLY_ENDED_CONTROL', reconsentRequired: true, controlState: 'MANUALLY_REVOKED'
  });
  const version = state?.modeVersion;
  if (state?.reconsentRequired !== false || !version || typeof version.controlSessionId !== 'string'
      || !version.controlSessionId || !Number.isSafeInteger(version.generation) || version.generation < 0) {
    throw Object.assign(new Error('Fresh control state is required before automatic acquisition'), { code: 'CONTROL_STATE_UNAVAILABLE' });
  }
  // Pin the checked generation so an override between status and acquire cannot be cleared.
  // Preserve a caller's explicit stale pin: only Runtime may judge it, never freshen it here.
  return { ...request, expectedModeVersion: structuredClone(request.expectedModeVersion ?? version) };
}

// Opt in only the validation's own client, not the product's explicit reacquire path.
export function guardAutomaticTakeover(client) {
  const json = client.json.bind(client);
  client.json = async (method, path, body, ...options) => {
    if (method === 'POST' && path === '/v0/control/acquire')
      body = automaticTakeoverRequest(await json('GET', '/v0/control/mode'), body);
    return json(method, path, body, ...options);
  };
  return client;
}

export async function connectRuntime(instanceDirectory, target) {
  const config = loadConfig({ MCP_MINECRAFT_INSTANCE_DIR: instanceDirectory, MCP_MINECRAFT_TARGET: target });
  const client = new RuntimeClient(config);
  const session = await client.json('GET', '/v0/session');
  assert.equal(session.target, target);
  const headers = { authorization: 'Bearer ' + config.token, 'x-mcp-runtime-instance': config.discovery.runtimeInstanceId };
  return {
    base: config.baseUrl.origin, token: config.token, headers, instanceId: config.discovery.runtimeInstanceId,
    port: config.discovery.port, pid: config.discovery.pid,
    async fetch(path, init = {}) {
      const current = readRuntimeDiscovery(instanceDirectory, target);
      assert.equal(current.runtimeInstanceId, config.discovery.runtimeInstanceId, 'Runtime restarted; explicit reconnect required');
      assert.equal(current.baseUrl, config.discovery.baseUrl);
      assert.equal(current.pid, config.discovery.pid);
      const response = await fetch(config.baseUrl.origin + path, { ...init, redirect: 'error', headers: { ...init.headers, ...headers } });
      assert.equal(response.headers.get('x-mcp-runtime-instance'), config.discovery.runtimeInstanceId, 'Runtime response identity mismatch');
      return response;
    }
  };
}
