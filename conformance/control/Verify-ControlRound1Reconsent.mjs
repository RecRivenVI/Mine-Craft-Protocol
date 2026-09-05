// Human Esc must already have occurred. Run the reacquire branch only after
// explicit conversation consent; this switch is a harness guard, never a wire field.
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { pathToFileURL, fileURLToPath } from 'node:url';
import { readFile, writeFile } from 'node:fs/promises';
const require = createRequire(new URL('../../companion/package.json', import.meta.url));
const { Client } = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
const { StdioClientTransport } = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
assert.equal(process.argv[5], '--conversation-reacquire-approved', 'Ask the user first; never infer conversation consent');
const transport = new StdioClientTransport({ command: process.execPath,
  args: [fileURLToPath(new URL('../../companion/dist/index.js', import.meta.url))], stderr: 'pipe',
  env: { ...process.env, MCP_MINECRAFT_BASE_URL: process.argv[2], MCP_MINECRAFT_TOKEN: (await readFile(process.argv[3], 'utf8')).trim() } });
const client = new Client({ name: 'control-r1-reconsent-conformance', version: '1.0' });
const checks = [];
let leased = false;
async function call(name, args = {}) {
  const reply = await client.callTool({ name, arguments: args });
  return { failed: reply.isError === true, ...reply.structuredContent };
}
async function ok(name, args) {
  const result = await call(name, args); assert.equal(result.failed, false, JSON.stringify(result.error)); return result.data;
}
async function denied() {
  const result = await call('minecraft_interact_ui', { coordinates: { x: 2, y: 2 } });
  assert.equal(result.failed, true);
  assert.equal(result.error.code, 'USER_MANUALLY_ENDED_CONTROL');
  assert.equal(result.error.reconsentRequired, true);
  assert.equal(result.error.control.reconsentRequired, true);
  checks.push({ check: 'persistent_structured_manual_rejection', code: result.error.code,
    reconsentRequired: result.error.reconsentRequired, mode: result.error.control.mode });
}
try {
  await client.connect(transport);
  const initial = await ok('minecraft_control', { action: 'status' });
  assert.equal(initial.mode, 'READ'); assert.equal(initial.reconsentRequired, true);
  await ok('minecraft_get_ui', {});
  for (let i = 0; i < 3; i++) await denied();
  const operate = await ok('minecraft_control', { action: 'set_mode', mode: 'OPERATE' });
  assert.equal(operate.mode, 'OPERATE'); assert.equal(operate.reconsentRequired, true);
  const fixture = await ok('minecraft_fixture', { operation: 'open_standard_gui' });
  assert.equal(fixture.evidenceContaminated, true);
  const afterFixture = await ok('minecraft_control', { action: 'status' });
  assert.equal(afterFixture.takeoverActive, false); assert.equal(afterFixture.reconsentRequired, true);
  await denied();
  const acquired = await ok('minecraft_control', { action: 'acquire', ttlMs: 30000 });
  leased = true;
  assert.equal(acquired.mode, 'TAKEOVER'); assert.equal(acquired.reconsentRequired, false);
  const operation = await ok('minecraft_run_input_pipeline', { timeoutMs: 10000,
    steps: [{ type: 'ui.action', selector: { label: 'Close Probe' }, holdMs: 100 },
      { type: 'wait.until', condition: { type: 'screen', open: false }, timeoutMs: 5000 }] });
  assert.equal(operation.state, 'completed');
  const released = await ok('minecraft_control', { action: 'release' });
  leased = false;
  assert.equal(released.mode, 'READ'); assert.equal(released.reconsentRequired, false);
  const evidence = { result: 'PASS', protocol: 'official_MCP_Companion_to_Runtime',
    readAfterManual: 'PASS', operateWithoutLease: 'PASS', latchAcrossOperate: 'PASS',
    explicitReacquire: 'PASS', finalMode: released.mode, reconsentRequired: false,
    fakeConsentWireField: false, checks, persistentWriteInvocations: 0 };
  await writeFile(process.argv[4], JSON.stringify(evidence, null, 2));
  console.log(JSON.stringify(evidence));
} finally {
  if (leased) await call('minecraft_control', { action: 'release' }).catch(() => undefined);
  await client.close();
}
