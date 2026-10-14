import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

// An idle test controller explicitly hands off its owned fixture. No user website is reused.
export async function editorConsumers(repo, base, token, tokenFile, kind, expected) {
 const require = createRequire(resolve(repo, 'components/companion/package.json'));
 const load = path => import(pathToFileURL(resolve(repo, path)).href);
 const { Client } = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
 const { StdioClientTransport } = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
 const { RuntimeClient } = await load('components/companion/dist/runtime-client.js');
 const { UiConsole } = await load('components/companion/dist/ui-console.js');
 const { startUiConsole } = await load('components/companion/dist/ui-console-http.js');
 const env = Object.fromEntries(Object.entries(process.env).filter(([, value]) => typeof value === 'string'));
 delete env.MCP_MINECRAFT_TOKEN;
 env.MCP_MINECRAFT_BASE_URL = base; env.MCP_MINECRAFT_TOKEN_FILE = tokenFile;
 const client = new Client({ name: 'editor-consumer-conformance', version: '1.0.0' });
 const transport = new StdioClientTransport({ command: process.execPath, args: [resolve(repo, 'components/companion/dist/index.js')], env, stderr: 'pipe' });
 const nodes = t => (t.children ?? []).flatMap(n => [n, ...nodes(n)]);
 const controlKey = kind === 'book' ? 'book.page' : 'sign.line.2';
 const results = [];
 let web, browserToken, mcpOwned = false, webOwned = false;
 const tool = async (name, args = {}) => {
  const response = await client.callTool({ name, arguments: args });
  assert(!response.isError, JSON.stringify(response.structuredContent));
  return response.structuredContent.data;
 };
 const request = async (path, body) => {
  const response = await fetch(web.url + '/api/' + path, { method: body === undefined ? 'GET' : 'POST', headers: { 'x-web-ui-token': browserToken, ...(body === undefined ? {} : { 'content-type': 'application/json' }) }, ...(body === undefined ? {} : { body: JSON.stringify(body) }), signal: AbortSignal.timeout(30000) });
  const value = await response.json();
  if (!response.ok) throw Object.assign(new Error(value.message), { code: value.error });
  return value;
 };
 try {
  await client.connect(transport);
  const state = await tool('minecraft_control', { action: 'status' });
  assert.equal(state.mode, 'READ'); assert.equal(state.reconsentRequired, false);
  await tool('minecraft_control', { action: 'begin_task', ttlMs: 60000, budgetMs: 120000 }); mcpOwned = true;
  await tool('minecraft_control', { action: 'acquire', ttlMs: 60000 });
  const original = nodes(await tool('minecraft_get_ui')).find(n => n.controlKey === controlKey);
  assert.equal(original.value, expected);
  for (const text of ['MCP 验证', expected]) {
   const response = await tool('minecraft_interact_ui', { selector: { controlKey }, action: 'type_text', text, replace: true });
   assert.notEqual(response.directMutationUsed, true);
   assert.equal(nodes(await tool('minecraft_get_ui')).find(n => n.controlKey === controlKey).value, text);
  }
  await tool('minecraft_control', { action: 'release' });
  await tool('minecraft_control', { action: 'end_task' }); mcpOwned = false;
  results.push({ transport: 'mcp_stdio', editor: kind, status: 'PASS' });
  web = await startUiConsole(new UiConsole(new RuntimeClient({ baseUrl: new URL(base), token, timeoutMs: 30000, maxJsonBytes: 2097152, maxArtifactBytes: 2097152 })), 0);
  ({ browserToken } = await (await fetch(web.url + '/api/bootstrap')).json());
  let s = await request('acquire', {}); webOwned = true;
  const action = async (key, action, extra = {}) => {
   s = await request('snapshot');
   const n = nodes(s.tree).find(n => n.controlKey === key); assert(n);
   const response = await request('action', { nodeId: n.nodeId, frameKey: s.frameKey, action, ...extra });
   assert.notEqual(response.result?.directMutationUsed, true);
   s = await request('snapshot');
  };
  await action(controlKey, 'type_text', { text: 'Web', replace: true });
  await request('input', { type: 'text', text: ' 输入', sequence: s.inputSequence + 1, inputFrameKey: s.inputFrameKey });
  s = await request('snapshot');
  assert.equal(nodes(s.tree).find(n => n.controlKey === controlKey).value, 'Web 输入');
  await action(controlKey, 'type_text', { text: expected, replace: true });
  assert.equal(nodes(s.tree).find(n => n.controlKey === controlKey).value, expected);
  if (kind === 'book') {
   const old = { nodeId: nodes(s.tree).find(n => n.controlKey === controlKey).nodeId, frameKey: s.frameKey, action: 'type_text', text: 'MUST NOT ARRIVE' };
   await action('book.next_page', 'click');
   await assert.rejects(request('action', old), { code: 'WEB_UI_STALE_FRAME' });
   await action('book.previous_page', 'click');
   assert.equal(nodes(s.tree).find(n => n.controlKey === controlKey).value, expected);
  }
  s = await request('release', {}); webOwned = false;
  assert.equal(s.control.mode, 'READ');
  assert.equal(s.input.pressedKeyCount, 0); assert.equal(s.input.pressedButtonCount, 0);
  results.push({ transport: 'web_http', editor: kind, status: 'PASS' });
  return results;
 } finally {
  if (webOwned) await request('release', {}).catch(() => {});
  if (mcpOwned) {
   await tool('minecraft_control', { action: 'release' }).catch(() => {});
   await tool('minecraft_control', { action: 'end_task' }).catch(() => {});
  }
  try { await web?.close(); } finally { await client.close(); }
 }
}
