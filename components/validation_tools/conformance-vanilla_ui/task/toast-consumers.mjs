import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { resolve } from 'node:path';
import { pathToFileURL } from 'node:url';

// Read-only consumers attached to this suite's owned Runtime; never attach to the user's website.
export async function toastConsumers(repo, base, token, tokenFile, target) {
 const require = createRequire(resolve(repo, 'components/companion/package.json'));
 const load = path => import(pathToFileURL(resolve(repo, path)).href);
 const { Client } = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
 const { StdioClientTransport } = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
 const { RuntimeClient } = await load('components/companion/dist/runtime-client.js');
 const { UiConsole } = await load('components/companion/dist/ui-console.js');
 const { startUiConsole } = await load('components/companion/dist/ui-console-http.js');
 const environment = Object.fromEntries(Object.entries(process.env).filter(([, value]) => typeof value === 'string'));
 delete environment.MCP_MINECRAFT_TOKEN;
 environment.MCP_MINECRAFT_BASE_URL = base;
 environment.MCP_MINECRAFT_TOKEN_FILE = tokenFile;
 const client = new Client({ name: 'toast-read-conformance', version: '1.0.0' });
 const transport = new StdioClientTransport({ command: process.execPath, args: [resolve(repo, 'components/companion/dist/index.js')], env: environment, stderr: 'pipe' });
 let web;
 try {
  await client.connect(transport);
  web = await startUiConsole(new UiConsole(new RuntimeClient({ baseUrl: new URL(base), token, timeoutMs: 10000, maxJsonBytes: 2097152, maxArtifactBytes: 2097152 })), 0);
  const { browserToken } = await (await fetch(web.url + '/api/bootstrap')).json();
  const read = async () => {
   const response = await client.callTool({ name: 'minecraft_get_ui', arguments: { category: 'USER' } });
   assert(!response.isError, JSON.stringify(response.structuredContent));
   const http = await fetch(web.url + '/api/snapshot', { headers: { 'x-web-ui-token': browserToken } });
   assert.equal(http.status, 200);
   const snapshot = await http.json();
   assert.equal(snapshot.ownsLease, false);
   return [['mcp_stdio', response.structuredContent.data], ['web_http', snapshot.tree]];
  };
  return {
   async verify() {
    const evidence = [];
    for (const [transport, tree] of await read()) {
     assert.equal(tree.target, target); assert.equal(tree.dataCategory, 'USER');
     const toast = tree.presentation.children.find(n => n.class?.endsWith('RecipeToast'));
     assert(toast, transport + ' observed live RecipeToast');
     assert.equal(toast.textPhase, 'static'); assert.equal(toast.textSupported, true);
     assert.match(toast.label, /New Recipe(?:s|\(s\)) Unlocked/); assert.match(toast.label, /Check your recipe book/);
     assert.equal(toast.labelSource, 'last_native_toast_render'); assert.equal(toast.debugLabel, undefined);
     assert.deepEqual(toast.actions, []);
     evidence.push({ transport, label: toast.label, phase: toast.textPhase, status: 'PASS' });
    }
    return evidence;
   },
   async verifyEffect(effect) {
    const evidence = [];
    for (const [transport, tree] of await read()) {
     assert.equal(tree.target, target); assert.equal(tree.dataCategory, 'USER');
     const icon = tree.presentation.children.find(n => n.effect === effect);
     assert(icon, transport + ' observed live native effect icon');
     assert.equal(icon.iconSource, 'last_native_effect_icon_draw'); assert.equal(icon.renderPhase, 'drawn');
     assert(icon.opacity > 0 && icon.opacity <= 1); assert.equal(icon.duration, undefined); assert.equal(icon.amplifier, undefined);
     assert.deepEqual(icon.actions, []);
     evidence.push({transport, effect, opacity: icon.opacity, source: icon.iconSource, status:'PASS'});
    }
    return evidence;
   },
   async close() { try { await web.close(); } finally { await client.close(); } }
  };
 } catch (error) {
  try { await web?.close(); } finally { await client.close(); }
  throw error;
 }
}
