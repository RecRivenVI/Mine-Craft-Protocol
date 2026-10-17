import assert from 'node:assert/strict';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

const repo = resolve(import.meta.dirname, '../../../..');
const target = '26.3-fabric';
const instance = resolve(repo, 'validations/conformance-vanilla_ui/instance', target, 'client');
const owner = JSON.parse(await readFile(resolve(instance, 'fixture-owner.json'), 'utf8'));
assert.equal(owner.owner, 'Mine-Craft-Protocol vanilla UI conformance');
assert.equal(owner.target, target);
const require = createRequire(resolve(repo, 'components/companion/package.json'));
const { Client } = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
const { StdioClientTransport } = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
const environment = Object.fromEntries(Object.entries(process.env).filter(([, value]) => typeof value === 'string'));
delete environment.MCP_MINECRAFT_TOKEN;
delete environment.MCP_MINECRAFT_BASE_URL;
delete environment.MCP_MINECRAFT_TOKEN_FILE;
environment.MCP_MINECRAFT_INSTANCE_DIR = instance;
environment.MCP_MINECRAFT_TARGET = target;
const client = new Client({ name: 'ui-category-conformance', version: '1.0.0' });
const transport = new StdioClientTransport({ command: process.execPath,
  args: [resolve(repo, 'components/companion/dist/index.js')], env: environment, stderr: 'pipe' });
const checks = [];
let failure;
let lastMutation = 0;
async function mutationBudget() {
  await new Promise(resolve => setTimeout(resolve, Math.max(0, 550 - (Date.now() - lastMutation))));
  lastMutation = Date.now();
}
async function tool(name, args = {}) {
  if (name === 'minecraft_debug' || name === 'minecraft_debug_arm') await mutationBudget();
  const response = await client.callTool({ name, arguments: args });
  assert(!response.isError, JSON.stringify(response.structuredContent));
  return response.structuredContent.data;
}
try {
  await client.connect(transport);
  const before = await tool('minecraft_control', { action: 'status' });
  assert.equal(before.mode, 'READ');
  const user = await tool('minecraft_get_ui', { category: 'USER' });
  assert.equal(user.target, target);
  assert.equal(user.domain, 'UI');
  assert.equal(user.dataCategory, 'USER');
  assert(!user.debugFacts);
  checks.push('MCP USER preserves explicit Native category');
  const debug = await tool('minecraft_get_ui', { category: 'DEBUG' });
  assert.equal(debug.dataCategory, 'DEBUG');
  assert.equal(debug.debugFacts.completeGameData, false);
  assert(debug.debugFacts.capturedNodeCount >= user.treeNodeCount);
  checks.push('MCP DEBUG returns bounded data with independent read authorization');
  const invalid = await client.callTool({ name: 'minecraft_interact_ui', arguments: {
    category: 'DEBUG', action: 'click', selector: { translationKey: 'menu.singleplayer' }
  } });
  assert(invalid.isError, 'DEBUG must not fall through to a USER click');
  checks.push('MCP input schema rejects DEBUG without a player side effect');
  const after = await tool('minecraft_control', { action: 'status' });
  assert.equal(after.mode, 'READ');
  assert.deepEqual(after.modeVersion, before.modeVersion);
  const remaining = await tool('minecraft_get_ui');
  assert.equal(remaining.screenRevision, user.screenRevision);
  assert.equal(remaining.screenClass, user.screenClass);
  checks.push('Read operations do not acquire TAKEOVER or change the Screen');
  await tool('minecraft_control', {action:'set_mode',mode:'OPERATE'});
  const ui = await tool('minecraft_get_ui', {category:'DEBUG'});
  const nodes = value => (value.children??[]).flatMap(n=>[n,...nodes(n)]);
  let node = nodes(ui).find(n=>n.translationKey==='menu.singleplayer');assert(node?.resourceVersion);
  await tool('minecraft_debug_arm',{action:'arm',uiContextId:ui.uiContextId,namespaces:['ui']});
  try {
    const request = {operation:'ui.widget.visible.set',nodeId:node.nodeId,expectedResourceVersion:node.resourceVersion,value:false};
    const changed = await tool('minecraft_debug',{action:'mutate',mutation:request});
    assert.equal(changed.after.visible,false);assert.equal(changed.inputDispatchCount,0);assert.equal(changed.evidenceAuthority,'DEBUG_PRIVILEGED');
    await mutationBudget();
    const stale = await client.callTool({name:'minecraft_debug',arguments:{action:'mutate',mutation:request}});
    assert.equal(stale.structuredContent.error.code,'STALE_RESOURCE_REVISION');
    checks.push('MCP typed UI Debug preserves session Arm, UI token, stale rejection and diagnostic evidence');
  } finally {
    node=nodes(await tool('minecraft_get_ui',{category:'DEBUG'})).find(n=>n.nodeId===node.nodeId);assert(node);
    if(!node.visible)await tool('minecraft_debug',{action:'mutate',mutation:{operation:'ui.widget.visible.set',nodeId:node.nodeId,expectedResourceVersion:node.resourceVersion,value:true}});
    await tool('minecraft_debug_arm',{action:'disarm'});
    await tool('minecraft_control',{action:'set_mode',mode:'READ'});
  }
  assert.equal((await tool('minecraft_control',{action:'status'})).mode,'READ');
  checks.push('UI Debug cleanup restores control without acquiring player Lease');
} catch (error) { failure = error; }
finally {
  await client.close();
  const sourceHashes = {};
  for (const path of ['components/companion/src/server.ts',
    'components/protocol_schema/src/main/openapi/minecraft-control-v0.json',
    'components/runtime_safety/src/main/java/io/github/recrivenvi/minecraftprotocol/safety/UiDataView.java',
    'components/validation_tools/conformance-vanilla_ui/task/run-ui-category-consumers.mjs']) {
    sourceHashes[path] = createHash('sha256').update(await readFile(resolve(repo, path))).digest('hex');
  }
  const output = resolve(repo, 'validations/conformance-vanilla_ui/result');
  await mkdir(output, { recursive: true });
  const result = { status: failure ? 'FAIL' : 'PASS', target, transport: 'mcp_stdio',
    testedAt: new Date().toISOString(), checks, sourceHashes, noMultimodal: true,
    persistentWriteCalls: 0, ...(failure ? { error: String(failure) } : {}) };
  await writeFile(resolve(output, 'ui-category-consumers-26.3-fabric.json'), JSON.stringify(result, null, 2) + '\n');
  console.log(JSON.stringify(result));
}
if (failure) process.exitCode = 1;
