import assert from 'node:assert/strict';
import test from 'node:test';
import { get, Server } from 'node:http';
import { createServer as createNetServer } from 'node:net';
import { RuntimeClient, RuntimeError } from '../runtime-client.js';
import { UiConsole, frameKey, inputFrameKey, flattenNodes } from '../ui-console.js';
import { startUiConsole } from '../ui-console-http.js';
import type { JsonObject, JsonValue, RuntimeBinary, RuntimeRequestOptions } from '../types.js';

class MockRuntime extends RuntimeClient {
  mode = 'READ';
  revoked = false;
  generation = 0;
  tree: JsonObject = { target: '26.3-fabric', dataCategory: 'USER', screenIdentity: 1, screenRevision: 1, width: 427, height: 240,
    children: [{ nodeId: 'opaque:unique-slot', role: 'slot', slot: 9, nativeSlotIndex: 0,
      item: 'minecraft:stone', count: 64, x: 5, y: 10, width: 18, height: 18, actions: ['click'] }] };
  calls: Array<{ method: string; path: string; body: JsonValue | undefined; options: RuntimeRequestOptions }> = [];
  taskActive = false;
  captureChanges = false;
  denyAcquire = false;
  pauseAcquire: (() => Promise<void>) | undefined;
  pauseAction: (() => Promise<void>) | undefined;
  delayModeOnce: (() => Promise<void>) | undefined;
  constructor() { super({ baseUrl: new URL('http://127.0.0.1:25602'), token: 'private-test-token', timeoutMs: 1000, maxJsonBytes: 100000, maxArtifactBytes: 100000 }); }
  control(): JsonObject { return { mode: this.mode, reconsentRequired: this.revoked,
    modeVersion: { controlSessionId: 'session-a', generation: this.generation }, agentTask: { active: this.taskActive } }; }
  override async json<T extends JsonValue = JsonObject>(method: 'GET' | 'POST' | 'DELETE', path: string,
    body?: JsonValue, options: RuntimeRequestOptions = {}): Promise<T> {
    this.calls.push({ method, path, body, options });
    let result: JsonObject = {};
    if (path === '/v0/control/mode') {
      result = this.control();
      const delay = this.delayModeOnce; this.delayModeOnce = undefined; await delay?.();
    }
    else if (path === '/v0/session') result = { target: '26.3-fabric', inWorld: true };
    else if (path === '/v0/input/state') result = { pressedKeyCount: 0, pressedButtonCount: 0 };
    else if (path === '/v0/ui/tree') result = structuredClone(this.tree);
    else if (path === '/v0/control/task') {
      const action = (body as JsonObject).action;
      this.taskActive = action !== 'end'; result = { taskId: 'test-task' };
    } else if (path === '/v0/control/acquire') {
      if (this.denyAcquire) throw new RuntimeError('SCOPE_REQUIRED', 403, 'control scope missing');
      await this.pauseAcquire?.();
      this.mode = 'TAKEOVER'; this.revoked = false; this.generation++; result = { leaseId: 'private-test-lease' };
    } else if (path === '/v0/control/release') { this.mode = 'READ'; this.generation++; }
    else if (path === '/v0/ui/action' || path === '/v0/pipelines') {
      assert.equal(options.headers?.['x-mcp-control-lease'], 'private-test-lease');
      assert.equal(this.mode, 'TAKEOVER');
      await this.pauseAction?.();
      result = path === '/v0/pipelines' ? { operationId: 'op-1' } : { entryLayer: 'GAME_ROUTED_RAW' };
    } else if (path === '/v0/operations/op-1/wait') result = { state: 'completed' };
    return result as T;
  }
  override async binary(): Promise<RuntimeBinary> {
    this.calls.push({ method: 'GET', path: '/v0/capture', body: undefined, options: {} });
    if (this.captureChanges) this.tree.screenRevision = 2;
    return { bytes: new Uint8Array([137, 80, 78, 71]), contentType: 'image/png' };
  }
}

test('web spectator uses semantic native shortcuts without enabling gameplay coordinate input', async () => {
  const runtime=new MockRuntime(), console=new UiConsole(runtime);
  runtime.tree.screenClass='';runtime.tree.uiInputContext='spectator';runtime.tree.spectatorContextKey='closed';
  runtime.tree.children=[{nodeId:'open',visible:true,active:true,inputRoute:'spectator_open',boundsAvailable:false,actions:['open']}];
  let s=await console.acquire('browser');
  await console.action('browser',{nodeId:'open',frameKey:s.frameKey??null,action:'open'});
  const oldFrame=s.frameKey;
  runtime.tree.uiInputContext='spectator_menu';runtime.tree.spectatorContextKey='menu:1:0';
  runtime.tree.children=[{nodeId:'item',visible:true,active:true,inputRoute:'spectator_hotbar',inputKey:49,
    selected:false,activationEnabled:false,boundsSemantics:'display_only',x:5,y:10,width:16,height:16,actions:['select','click']}];
  s=await console.snapshot('browser');
  assert.notEqual(s.frameKey,oldFrame);
  await console.action('browser',{nodeId:'item',frameKey:s.frameKey??null,action:'select'});
  await assert.rejects(console.action('browser',{nodeId:'item',frameKey:s.frameKey??null,action:'activate'}),{code:'WEB_UI_TARGET_UNAVAILABLE'});
  await assert.rejects(console.action('browser',{coordinates:{x:6,y:11},frameKey:s.frameKey??null}),{code:'WEB_UI_INVALID_COORDINATES'});
  const requests=runtime.calls.filter(c=>c.path==='/v0/ui/action');
  assert.deepEqual(requests.map(c=>(c.body as JsonObject).action),['open','select']);
  assert(requests.every(c=>!(c.body as JsonObject).coordinates));
  assert(!runtime.calls.some(c=>c.path.startsWith('/v0/input/')&&c.method==='POST'));
  runtime.tree.spectatorContextKey='menu:1:1';
  await assert.rejects(console.action('browser',{nodeId:'item',frameKey:s.frameKey??null,action:'select'}),{code:'WEB_UI_STALE_FRAME'});
  await console.close();
});

test('web READ observes without acquiring a task, lease, or input authority', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime);
  const snapshot = await console.snapshot('browser');
  assert.equal(snapshot.ownsLease, false);
  await assert.rejects(console.action('browser', { nodeId: 'opaque:unique-slot', frameKey: snapshot.frameKey ?? null }), { code: 'TAKEOVER_REQUIRED' });
  assert(runtime.calls.every(call => call.method === 'GET'));
});

test('web snapshot preserves read-only HUD player-list text without inventing clickable controls', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime);
  runtime.tree.presentation = { coverage: 'partial_typed_presentation', children: [
    { nodeId: 'hud.playerlist.row.0', controlKey: 'hud.playerlist.row.0', role: 'hud_text',
      label: '[UI] Display name', visible: true, active: false, boundsAvailable: false, actions: [] }
  ] };
  const snapshot = await console.snapshot('browser');
  assert.deepEqual((snapshot.tree as JsonObject).presentation, runtime.tree.presentation);
  assert(!flattenNodes(snapshot.tree as JsonObject).some(node => node.nodeId === 'hud.playerlist.row.0'));
  assert.equal(snapshot.ownsLease, false);
  assert(runtime.calls.every(call => call.method === 'GET'));
});

test('web retains native effect draw opacity as read-only presentation without acquiring input', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime);
  runtime.tree.presentation = {coverage:'partial_typed_presentation',children:[
    {nodeId:'hud.effect.0',role:'status_icon',effect:'minecraft:strength',iconSource:'last_native_effect_icon_draw',
      renderPhase:'drawn',opacity:0.125,visible:true,active:false,label:'',actions:[]}
  ]};
  const snapshot = await console.snapshot('browser');
  assert.deepEqual((snapshot.tree as JsonObject).presentation, runtime.tree.presentation);
  assert.equal(snapshot.ownsLease, false);
  assert(!flattenNodes(snapshot.tree as JsonObject).some(n=>n.nodeId==='hud.effect.0'));
  assert(runtime.calls.every(call=>call.method==='GET'));
  await console.close();
});

test('web user viewport rejects DEBUG, unknown and unclassified snapshots instead of guessing USER', async () => {
  for (const category of ['DEBUG', 'unknown', null]) {
    const runtime = new MockRuntime(), console = new UiConsole(runtime);
    if (category === null) delete runtime.tree.dataCategory;
    else runtime.tree.dataCategory = category;
    await assert.rejects(console.snapshot('browser'), {code:'WEB_UI_CATEGORY_MISMATCH'});
    assert(runtime.calls.every(call => call.method === 'GET'));
    await console.close();
  }
});

test('web preserves native Toast phase updates as read-only presentation, without taking a lease', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime);
  for (const [textPhase, label] of [['header', 'Advancement Made!'], ['title', 'Visible\nTitle']] as const) {
    runtime.tree.presentation = { coverage: 'partial_typed_presentation', children: [
      { nodeId: 'toast.0', role: 'toast', label, labelSource: 'last_native_toast_render', textPhase,
        textSupported: true, visible: true, active: false, actions: [] }
    ] };
    const snapshot = await console.snapshot('browser');
    assert.deepEqual((snapshot.tree as JsonObject).presentation, runtime.tree.presentation);
    assert(!flattenNodes(snapshot.tree as JsonObject).some(node => node.nodeId === 'toast.0'));
    assert.equal(snapshot.ownsLease, false);
  }
  assert(runtime.calls.every(call => call.method === 'GET'));
  await console.close();
});

test('web forwards nested semantic text, slider and hidden-row scrolling without coordinate fallback', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime);
  runtime.tree.children = [{nodeId:'list',role:'list',actions:['scroll'],children:[
    {nodeId:'input',role:'text_field',actions:['type_text'],active:true,visible:true},
    {nodeId:'slider',role:'slider',actions:['set_value'],active:true,visible:true},
    {nodeId:'hidden',role:'list_item',actions:['scroll_into_view'],active:true,visible:false}
  ]}];
  const snapshot = await console.acquire('browser');
  for(const request of [
    {nodeId:'input',action:'type_text',text:'中文',replace:true},
    {nodeId:'slider',action:'set_value',value:0.5},
    {nodeId:'hidden',action:'scroll_into_view'}
  ]) await console.action('browser', {...request,frameKey:snapshot.frameKey??null});
  const calls=runtime.calls.filter(call=>call.path==='/v0/ui/action');
  assert.equal(calls.length,3);
  assert.equal((calls[0]?.body as JsonObject).text,'中文');
  assert.equal((calls[1]?.body as JsonObject).value,0.5);
  assert.deepEqual((calls[2]?.body as JsonObject).selector,{nodeId:'hidden'});
  assert(calls.every(call=>!(call.body as JsonObject).coordinates));
  await assert.rejects(console.action('browser',{nodeId:'list',frameKey:snapshot.frameKey??null}),{code:'WEB_UI_TARGET_UNAVAILABLE'});
  await console.close();
});

test('web forwards hovered server sub-actions and rejects a pre-reorder node identity', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime);
  const control = (id: string, visible: boolean): JsonObject => ({ nodeId:id, role:'hit_region', controlKey:'server.move_down', visible, active:true, actions:visible?['click']:[] });
  runtime.tree.children = [{nodeId:'server-a',role:'list_item',controlKey:'server.entry',children:[control('server-a:down:0',false)]}];
  let snapshot = await console.acquire('browser');
  await assert.rejects(console.action('browser',{nodeId:'server-a:down:0',frameKey:snapshot.frameKey??null}),{code:'WEB_UI_TARGET_UNAVAILABLE'});
  runtime.tree.children = [{nodeId:'server-a',role:'list_item',controlKey:'server.entry',children:[control('server-a:down:0',true)]}];
  snapshot = await console.snapshot('browser');
  await console.action('browser',{nodeId:'server-a:down:0',frameKey:snapshot.frameKey??null});
  runtime.tree.children = [{nodeId:'server-b',role:'list_item',controlKey:'server.entry',children:[control('server-b:down:0',true)]}];
  snapshot = await console.snapshot('browser');
  await assert.rejects(console.action('browser',{nodeId:'server-a:down:0',frameKey:snapshot.frameKey??null}),{code:'WEB_UI_TARGET_UNAVAILABLE'});
  const calls=runtime.calls.filter(call=>call.path==='/v0/ui/action');
  assert.equal(calls.length,1);
  assert.deepEqual((calls[0]?.body as JsonObject).selector,{nodeId:'server-a:down:0'});
  assert(!(calls[0]?.body as JsonObject).coordinates);
  await console.close();
});

test('web preserves rendered menu scrollbar roles and forwards native hover/scroll without Debug', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime);
  runtime.tree.children = [
    {nodeId:'opaque-track',controlKey:'creative.scrollbar.track',role:'scrollbar',active:true,visible:true,scrollAmount:0,dragAxis:'vertical',actions:['hover','scroll','click']},
    {nodeId:'opaque-thumb',controlKey:'creative.scrollbar.thumb',role:'scrollbar_thumb',active:true,visible:true,interactionLayer:21,actions:['hover','click','drag']},
    {nodeId:'disabled-track',controlKey:'loom.scrollbar.track',role:'scrollbar',active:false,visible:true,actions:[]}
  ];
  const snapshot = await console.acquire('browser');
  assert.deepEqual((snapshot.tree as JsonObject).children,runtime.tree.children);
  await console.action('browser',{nodeId:'opaque-thumb',frameKey:snapshot.frameKey??null,action:'hover'});
  await console.action('browser',{nodeId:'opaque-track',frameKey:snapshot.frameKey??null,action:'scroll',yOffset:-1});
  await assert.rejects(console.action('browser',{nodeId:'disabled-track',frameKey:snapshot.frameKey??null}),{code:'WEB_UI_TARGET_UNAVAILABLE'});
  const calls=runtime.calls.filter(call=>call.path==='/v0/ui/action');
  assert.equal(calls.length,2);
  assert.deepEqual(calls.map(call=>(call.body as JsonObject).selector),[{nodeId:'opaque-thumb'},{nodeId:'opaque-track'}]);
  assert.equal((calls[1]?.body as JsonObject).yOffset,-1);
  assert(!runtime.calls.some(call=>call.path.startsWith('/v0/debug/')));
  await console.close();
});

test('web semantic slot click uses opaque node identity and existing Lease, never coordinates or Debug', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime);
  const snapshot = await console.acquire('browser');
  await console.action('browser', { nodeId: 'opaque:unique-slot', frameKey: snapshot.frameKey ?? null, shift: true, button: 1 });
  const request = runtime.calls.find(call => call.path === '/v0/ui/action');
  assert.deepEqual(request?.body, { selector: { nodeId: 'opaque:unique-slot' }, action: 'click', button: 1, modifiers: 1, holdMs: 80 });
  assert.equal(request?.options.headers?.['x-mcp-control-lease'], 'private-test-lease');
  assert(!runtime.calls.some(call => call.path.includes('debug') || call.path.includes('storage')));
  await console.close(); assert.equal(runtime.mode, 'READ');
});

test('web Shift click holds the actual routed key and releases it on success or native rejection', async () => {
  for (const reject of [false, true]) {
    const runtime = new MockRuntime(), console = new UiConsole(runtime);
    const snapshot = await console.acquire('browser');
    if (reject) runtime.pauseAction = async () => { throw new RuntimeError('UI_TARGET_INVALIDATED', 409, 'stale menu'); };
    const action = console.action('browser', { nodeId:'opaque:unique-slot',frameKey:snapshot.frameKey ?? null,shift:true });
    if (reject) await assert.rejects(action, {code:'UI_TARGET_INVALIDATED'}); else await action;
    const keys = runtime.calls.filter(call => call.path === '/v0/input/key');
    assert.deepEqual(keys.map(call => (call.body as JsonObject).action), [1,0]);
    assert(keys.every(call => (call.body as JsonObject).key === 340 && call.options.headers?.['x-mcp-control-lease'] === 'private-test-lease'));
    await console.close();
  }
});

test('web stale screen, scale, menu and item revision reject before input dispatch', async () => {
  for (const field of ['screenIdentity', 'screenRevision', 'menuRevision', 'menuIdentity', 'guiScale', 'width', 'gameCursorRevision', 'editorContextKey']) {
    const runtime = new MockRuntime(), console = new UiConsole(runtime);
    const snapshot = await console.acquire('browser'); runtime.tree[field] = 99;
    await assert.rejects(console.action('browser', { nodeId: 'opaque:unique-slot', frameKey: snapshot.frameKey ?? null }), { code: 'WEB_UI_STALE_FRAME' });
    assert(!runtime.calls.some(call => call.path === '/v0/ui/action')); await console.close();
  }
});

test('web rejects other browsers, hidden nodes and actions without semantic support', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime);
  let snapshot = await console.acquire('first');
  await assert.rejects(console.acquire('second'), { code: 'WEB_UI_OWNER_BUSY' });
  await assert.rejects(console.release('second'), { code: 'WEB_UI_NOT_OWNER' });
  await assert.rejects(console.action('second', { nodeId: 'opaque:unique-slot', frameKey: snapshot.frameKey ?? null }), { code: 'TAKEOVER_REQUIRED' });
  const node = flattenNodes(runtime.tree)[0]!; node.visible = false;
  await assert.rejects(console.action('first', { nodeId: node.nodeId ?? null, frameKey: snapshot.frameKey ?? null }), { code: 'WEB_UI_TARGET_UNAVAILABLE' });
  node.visible = true; node.actions = []; snapshot = await console.snapshot('first');
  await assert.rejects(console.action('first', { nodeId: node.nodeId ?? null, frameKey: snapshot.frameKey ?? null }), { code: 'WEB_UI_TARGET_UNAVAILABLE' });
  await console.close();
});

test('web manual revoke preserves READ and latch; polling never reacquires', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime);
  await console.acquire('browser'); runtime.revoked = true; runtime.mode = 'READ'; runtime.taskActive = false;
  for (let i = 0; i < 3; i++) {
    const snapshot = await console.snapshot('browser');
    assert.equal((snapshot.control as JsonObject).reconsentRequired, true); assert.equal(snapshot.ownsLease, false);
    await assert.rejects(console.key('browser', 'Escape'), { code: 'USER_MANUALLY_ENDED_CONTROL', reconsentRequired: true });
  }
  assert.equal(runtime.calls.filter(call => call.path === '/v0/control/acquire').length, 1);
  const snapshot = await console.acquire('browser');
  assert.equal((snapshot.control as JsonObject).reconsentRequired, false);
  await console.close();
});

test('web native authorization denial is preserved and does not keep task ownership', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime); runtime.denyAcquire = true;
  await assert.rejects(console.acquire('browser'), { code: 'SCOPE_REQUIRED', status: 403 });
  assert.equal(runtime.taskActive, false); assert.equal(runtime.mode, 'READ'); await console.close();
});

test('web concurrent gestures reject instead of accumulating late input', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime);
  const snapshot = await console.acquire('browser');
  let resume!: () => void, started!: () => void;
  const entered = new Promise<void>(resolve => { started = resolve; });
  runtime.pauseAction = () => new Promise(resolve => { resume = resolve; started(); });
  const first = console.action('browser', { nodeId: 'opaque:unique-slot', frameKey: snapshot.frameKey ?? null });
  await entered;
  await assert.rejects(console.action('browser', { nodeId: 'opaque:unique-slot', frameKey: snapshot.frameKey ?? null }), { code: 'WEB_UI_GESTURE_BUSY' });
  resume(); await first; await console.close();
});

test('web close during acquire never publishes or leaves a late Lease', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime);
  let resume!: () => void, started!: () => void;
  const entered = new Promise<void>(resolve => { started = resolve; });
  runtime.pauseAcquire = () => new Promise(resolve => { resume = resolve; started(); });
  const acquiring = console.acquire('browser'); await entered; await console.close(); resume();
  await assert.rejects(acquiring, { code: 'WEB_UI_CANCELLED' });
  assert.equal(runtime.mode, 'READ'); assert.equal(runtime.taskActive, false);
});

test('web an older READ poll cannot forget a concurrently acquired Lease', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime);
  let resume!: () => void, entered!: () => void;
  const modeRead = new Promise<void>(resolve => { entered = resolve; });
  runtime.delayModeOnce = () => new Promise(resolve => { resume = resolve; entered(); });
  const oldPoll = console.snapshot('browser');await modeRead;
  await console.acquire('browser'); resume(); const reconciled=await oldPoll;
  assert.equal((reconciled.control as JsonObject).mode,'TAKEOVER');
  const current = await console.snapshot('browser');
  assert.equal(current.ownsLease, true); assert.equal(runtime.mode, 'TAKEOVER');
  await console.close(); assert.equal(runtime.mode, 'READ');
});

test('web inactive browser heartbeat releases only its own Lease and task', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime);
  const now = Date.now;
  let clock = now();
  Date.now = () => clock;
  try {
    await console.acquire('browser'); clock += 7000; await console.tick();
    assert.equal(runtime.mode, 'READ'); assert.equal(runtime.taskActive, false);
    assert.equal((await console.snapshot('browser')).ownsLease, false);
  } finally { Date.now = now; await console.close(); }
});

test('web explicit blank-region coordinates require fresh GUI and remain visibly distinct from semantic actions', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime); runtime.tree.screenClass = 'SelectWorldScreen';
  const snapshot = await console.acquire('browser');
  await console.action('browser', { frameKey: snapshot.frameKey ?? null, coordinates: { x: 426.75, y: 239.75 } });
  assert.deepEqual(runtime.calls.find(call => call.path === '/v0/ui/action')?.body, {
    coordinates: { x: 426.75, y: 239.75 }, source: 'explicit_coordinate', action: 'click', button: 0, modifiers: 0, holdMs: 80
  });
  await assert.rejects(console.action('browser', { frameKey: snapshot.frameKey ?? null, coordinates: { x: 427, y: 50 } }), { code: 'WEB_UI_INVALID_COORDINATES' });
  await console.close();
});

test('web Agent Esc uses an existing GAME_ROUTED pipeline and does not revoke', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime);
  await console.acquire('browser'); await console.key('browser', 'Escape');
  const call = runtime.calls.find(value => value.path === '/v0/pipelines');
  assert.deepEqual(call?.body, { steps: [{ type: 'key.tap', key: 256, holdMs: 60 }], timeoutMs: 10000 });
  assert.equal(runtime.revoked, false); assert.equal(runtime.mode, 'TAKEOVER');
  await assert.rejects(console.key('browser', 'F3'), { code: 'WEB_UI_KEY_NOT_ALLOWED' }); await console.close();
});

test('web screenshot remains READ-compatible and rejects layout changes during capture', async () => {
  const runtime = new MockRuntime(), console = new UiConsole(runtime);
  const image = await console.capture(); assert.equal(image.bytes[0], 137); assert.equal(image.frame, frameKey(runtime.tree));
  assert(runtime.calls.every(call => call.method === 'GET'));
  runtime.captureChanges = true; await assert.rejects(console.capture(), { code: 'WEB_UI_STALE_CAPTURE' });
});

test('web HTTP preserves structured Runtime quota hints without acquiring or replaying', async () => {
  const runtime = new MockRuntime();
  const rateLimit = { scope: 'principal', category: 'world', operationCategory: 'world', capacity: 60,
    remaining: 0, refillPerSecond: 30, cost: 1 };
  runtime.json = async () => { throw new RuntimeError('EXPENSIVE_OPERATION_RATE_LIMITED', 429, 'budget', 'quota-request',
    { retryAfterMillis: 34, rateLimit }); };
  const app = await startUiConsole(new UiConsole(runtime), 0);
  try {
    const bootstrap = await (await fetch(app.url + '/api/bootstrap')).json() as { browserToken: string };
    const response = await fetch(app.url + '/api/snapshot', { headers: { 'x-web-ui-token': bootstrap.browserToken } });
    assert.equal(response.status, 429);
    const body = await response.json() as { error: string; retryAfterMillis: number; rateLimit: unknown };
    assert.equal(body.error, 'EXPENSIVE_OPERATION_RATE_LIMITED');
    assert.equal(body.retryAfterMillis, 34); assert.deepEqual(body.rateLimit, rateLimit);
    assert.equal(runtime.mode, 'READ'); assert.equal(runtime.calls.length, 0);
  } finally { await app.close(); }
});

test('web HTTP refuses foreign origins, absent sessions and arbitrary native paths; never exposes Runtime credentials', async () => {
  const runtime = new MockRuntime(), app = await startUiConsole(new UiConsole(runtime), 0);
  try {
    assert.equal((await fetch(app.url).catch(error=>{throw new Error('Local UI connection failed: '+app.url,{cause:error});})).status, 200);
    assert.equal((await fetch(app.url + '/api/bootstrap', { headers: { origin: 'https://example.test' } })).status, 403);
    assert.equal((await fetch(app.url + '/api/snapshot')).status, 403);
    const wrongHostStatus = await new Promise<number | undefined>((resolve, reject) => {
      const request = get(app.url + '/api/bootstrap', { headers: { host: 'example.test' } }, response => {
        response.resume(); response.once('end', () => resolve(response.statusCode));
      });
      request.once('error', reject);
    });
    assert.equal(wrongHostStatus, 403);
    const bootstrap = await (await fetch(app.url + '/api/bootstrap')).json() as { browserToken: string };
    const headers = { 'x-web-ui-token': bootstrap.browserToken, 'content-type': 'application/json' };
    const snapshot = await (await fetch(app.url + '/api/snapshot', { headers })).text();
    assert(!snapshot.includes(runtime.connectionConfig.token));
    assert.equal((await fetch(app.url + '/v0/debug/mutations', { method: 'POST', headers, body: '{}' })).status, 404);
    assert.equal((await fetch(app.url + '/api/action', { method: 'POST', headers, body: '{}' })).status, 409);
    assert.equal((await fetch(app.url + '/api/action', { method: 'POST', headers, body: '[]' })).status, 400);
    assert.equal((await fetch(app.url + '/api/action', { method: 'POST', headers, body: JSON.stringify({ junk: 'x'.repeat(20000) }) })).status, 413);
    const acquire = await fetch(app.url + '/api/acquire', { method: 'POST', headers, body: '{}' });
    assert.equal(acquire.status, 200); assert(!(await acquire.text()).includes('private-test-lease'));
    const image = await fetch(app.url + '/api/capture', { method: 'POST', headers, body: '{}' });
    assert.equal(image.headers.get('content-type'), 'image/png'); assert.equal(image.status, 200);
    await fetch(app.url + '/api/release', { method: 'POST', headers, body: '{}' }); assert.equal(runtime.mode, 'READ');
  } finally { await app.close(); }
});

test('web automatic listener uses a browser-safe dynamic port and real fetch bootstrap, then unbinds', async () => {
  const runtime = new MockRuntime(), app = await startUiConsole(new UiConsole(runtime), 0);
  const port = Number(new URL(app.url).port);
  try {
    assert(port >= 49152 && port <= 65535);
    const response = await fetch(app.url + '/api/bootstrap'); assert.equal(response.status, 200);
    const body = await response.json() as {browserToken: string; runtimeUrl: string};
    assert.match(body.browserToken, /^[0-9a-f]{64}$/);
    assert.equal(body.runtimeUrl, runtime.connectionConfig.baseUrl.origin);
    assert.equal((await fetch(app.url + '/api/snapshot', {headers: {'x-web-ui-token': body.browserToken}})).status, 200);
  } finally { await app.close(); }
  assert.equal(app.server.listening, false); assert.equal(app.server.address(), null);
  const replacement = createNetServer();
  try { await new Promise<void>((resolve, reject) => {
    replacement.once('error', reject); replacement.listen(port, '127.0.0.1', resolve);
  }); } finally { if (replacement.listening) await new Promise<void>((resolve, reject) => replacement.close(error => error ? reject(error) : resolve())); }
});

test('web explicit Fetch-blocked and malformed ports fail before creating or controlling a listener', async () => {
  for (const port of [1, 6000, 6566, 6667, 6679, 6697, 10080]) {
    const runtime = new MockRuntime();
    await assert.rejects(startUiConsole(new UiConsole(runtime), port), {code: 'WEB_UI_PORT_BLOCKED'});
    assert.equal(runtime.calls.length, 0);
  }
  for (const port of [-1, 65536, .5, Number.NaN, Number.POSITIVE_INFINITY])
    await assert.rejects(startUiConsole(new UiConsole(new MockRuntime()), port), {code: 'WEB_UI_INVALID_PORT'});
});

test('web explicit safe port is honored and an occupied explicit port never falls back', async () => {
  const first = await startUiConsole(new UiConsole(new MockRuntime()), 0);
  const port = Number(new URL(first.url).port);
  try {
    await assert.rejects(startUiConsole(new UiConsole(new MockRuntime()), port), {code: 'EADDRINUSE'});
    assert.equal((await fetch(first.url + '/api/bootstrap')).status, 200);
  } finally { await first.close(); }
  const explicit = await startUiConsole(new UiConsole(new MockRuntime()), port);
  try { assert.equal(Number(new URL(explicit.url).port), port); assert.equal((await fetch(explicit.url + '/api/bootstrap')).status, 200); }
  finally { await explicit.close(); }
});

test('web automatic bind retries only address-in-use, stays bounded and removes per-attempt listeners', async t => {
  const original = Server.prototype.listen;
  let attempts = 0;
  const replacement = t.mock.method(Server.prototype, 'listen', function (this: Server, ...args: Parameters<typeof original>) {
    attempts++; assert.equal(typeof args[0], 'number'); assert((args[0] as unknown as number) >= 49152);
    if (attempts === 1) {
      queueMicrotask(() => this.emit('error', Object.assign(new Error('owned simulated collision'), {code: 'EADDRINUSE'})));
      return this;
    }
    return Reflect.apply(original, this, args) as Server;
  });
  const app = await startUiConsole(new UiConsole(new MockRuntime()), 0);
  try {
    assert.equal(attempts, 2); assert.equal((await fetch(app.url + '/api/bootstrap')).status, 200);
    assert.equal(app.server.listenerCount('error'), 0);
    assert(!app.server.listeners('listening').some(listener => listener.name === 'listening'), 'No per-attempt listener remains; Node owns its connection-tracking listener');
  } finally { await app.close(); replacement.mock.restore(); }
  let failedServer: Server | undefined;
  t.mock.method(Server.prototype, 'listen', function (this: Server) {
    failedServer = this; attempts++;
    queueMicrotask(() => this.emit('error', Object.assign(new Error('owned persistent collision'), {code: 'EADDRINUSE'})));
    return this;
  });
  attempts = 0;
  await assert.rejects(startUiConsole(new UiConsole(new MockRuntime()), 0), {code: 'EADDRINUSE'});
  assert.equal(attempts, 8); assert.equal(failedServer?.listening, false);
  assert.equal(failedServer?.listenerCount('error'), 0);
  assert(!failedServer?.listeners('listening').some(listener => listener.name === 'listening'));
});

test('web non-collision bind errors are not retried', async t => {
  let attempts = 0;
  const replacement = t.mock.method(Server.prototype, 'listen', function (this: Server) {
    attempts++; queueMicrotask(() => this.emit('error', Object.assign(new Error('owned denied bind'), {code: 'EACCES'}))); return this;
  });
  await assert.rejects(startUiConsole(new UiConsole(new MockRuntime()), 0), {code: 'EACCES'});
  assert.equal(attempts, 1); replacement.mock.restore();
});

test('direct GUI stream keeps fractional positions, button ownership, scroll and native keys',async()=>{
  const runtime=new MockRuntime(),console=new UiConsole(runtime);runtime.tree.screenClass='InventoryScreen';
  await console.acquire('browser');const frame=inputFrameKey(runtime.tree);let sequence=0;
  for(const event of [{type:'key',key:340,action:1,modifiers:1},
    {type:'button',x:10.25,y:20.75,button:0,action:1,modifiers:1},{type:'move',x:30.5,y:40.25},
    {type:'button',x:30.5,y:40.25,button:0,action:0},{type:'key',key:340,action:0},
    {type:'scroll',x:30.5,y:40.25,yOffset:-1}])await console.input('browser',{...event,sequence:++sequence,inputFrameKey:frame});
  const native=runtime.calls.filter(c=>c.path.startsWith('/v0/input/')&&c.method==='POST');
  assert(native.every(c=>c.options.headers?.['x-mcp-control-lease']==='private-test-lease'));
  assert(native.some(c=>c.path==='/v0/input/mouse/move'&&(c.body as JsonObject).x===10.25&&(c.body as JsonObject).motion==='direct'));
  assert.deepEqual(native.filter(c=>c.path==='/v0/input/mouse/button').map(c=>(c.body as JsonObject).action),[1,0]);
  assert(!native.some(c=>c.path.includes('delta')));await console.close();
});

test('GUI stream refuses READ, gameplay, stale frames and malformed input without a press',async()=>{
  const runtime=new MockRuntime(),console=new UiConsole(runtime);
  await assert.rejects(console.input('browser',{sequence:1}),{code:'TAKEOVER_REQUIRED'});
  await console.acquire('browser');runtime.tree.screenClass='InventoryScreen';
  await assert.rejects(console.input('browser',{type:'button',sequence:1,inputFrameKey:inputFrameKey(runtime.tree),x:5,y:5,button:99,action:1}),{code:'WEB_UI_INVALID_INPUT'});
  assert(!runtime.calls.some(c=>c.method==='POST'&&c.path.startsWith('/v0/input/')));
  await console.acquire('browser');
  await assert.rejects(console.input('browser',{type:'move',sequence:1,inputFrameKey:'old',x:5,y:5}),{code:'WEB_UI_STALE_FRAME'});
  await console.acquire('browser');runtime.tree.screenClass='';
  await assert.rejects(console.input('browser',{type:'move',sequence:1,inputFrameKey:inputFrameKey(runtime.tree),x:5,y:5}),{code:'WEB_UI_GUI_REQUIRED'});
  assert(!runtime.calls.some(c=>c.path==='/v0/input/mouse/move'));await console.close();
});

test('release after a screen-changing press only cleans its held button, never clicks or moves on replacement',async()=>{
  const runtime=new MockRuntime(),console=new UiConsole(runtime);runtime.tree.screenClass='TitleScreen';
  await console.acquire('browser');const frame=inputFrameKey(runtime.tree);
  await console.input('browser',{type:'button',sequence:1,inputFrameKey:frame,x:10,y:20,button:0,action:1});
  runtime.tree.screenClass='SelectWorldScreen';runtime.tree.screenIdentity=2;runtime.tree.screenRevision=2;
  const before=runtime.calls.length;
  await console.input('browser',{type:'button',sequence:2,inputFrameKey:frame,x:10,y:20,button:0,action:0});
  assert(!runtime.calls.slice(before).some(c=>c.path==='/v0/input/mouse/move'));
  assert.equal((await console.snapshot('browser')).ownsLease,true);await console.close();
});

test('text stream preserves native caret and rejects unfocused text instead of silently focusing',async()=>{
  const runtime=new MockRuntime(),console=new UiConsole(runtime);runtime.tree.screenClass='ChatScreen';
  runtime.tree.children=[{nodeId:'editor',role:'text_field',focused:true,actions:['type_text']}];
  await console.acquire('browser');
  await console.input('browser',{type:'text',text:'中文',sequence:1,inputFrameKey:inputFrameKey(runtime.tree)});
  assert.deepEqual(runtime.calls.find(c=>c.path==='/v0/ui/action')?.body,{selector:{nodeId:'editor'},action:'type_text',text:'中文',replace:false,focusOnly:true});
  assert(!runtime.calls.some(c=>c.path==='/v0/input/mouse/button'));
  await assert.rejects(console.input('browser',{type:'text',text:'x',sequence:1,inputFrameKey:inputFrameKey(runtime.tree)}),{code:'WEB_UI_INPUT_SEQUENCE'});
  await console.close();
});

test('gesture frame ignores only own menu changes, not screen replacement, size or scale',()=>{
 const tree:JsonObject={screenClass:'InventoryScreen',screenIdentity:1,menuRevision:1,guiScale:4,width:480,height:270};
 const key=inputFrameKey(tree);assert.equal(inputFrameKey({...tree,menuRevision:2}),key);
 for(const change of [{guiScale:3},{width:640},{screenIdentity:2},{screenClass:'AnotherScreen'}])assert.notEqual(inputFrameKey({...tree,...change}),key);
});

test('dedicated web snapshot and console actions never request graphical or player input surfaces',async()=>{
  class DedicatedRuntime extends RuntimeClient {
    calls:Array<{method:string;path:string;body:JsonValue|undefined}>=[];
    mode='READ';
    constructor(){super({baseUrl:new URL('http://127.0.0.1:25603'),token:'server-token',timeoutMs:1000,maxJsonBytes:100000,maxArtifactBytes:100000});}
    override async json<T extends JsonValue=JsonObject>(method:'GET'|'POST'|'DELETE',path:string,body?:JsonValue):Promise<T>{
      this.calls.push({method,path,body});let value:JsonObject;
      if(path==='/v0/session')value={target:'26.3-neoforge',deploymentRole:'DEDICATED_SERVER'};
      else if(path==='/v0/control/mode'){if(method==='POST')this.mode=String((body as JsonObject).mode);value={mode:this.mode,modeVersion:{generation:1}};}
      else if(path.startsWith('/v0/server/console?'))value={records:[{level:'INFO',text:'<script>untrusted server text</script>'}]};
      else if(path==='/v0/server/console/suggestions')value={commandExecuted:false,suggestions:[{text:'list',start:0,end:2}]};
      else if(path==='/v0/server/console/commands'){
        if(this.mode!=='OPERATE')throw new RuntimeError('OPERATE_REQUIRED',409,'Explicit intent required');
        value={operationId:'server-operation',state:'accepted'};
      }else throw new Error('Unexpected graphical/player call: '+path);
      return value as T;
    }
  }
  const runtime=new DedicatedRuntime(),controller=new UiConsole(runtime);
  const snapshot=await controller.snapshot('browser');assert.equal(snapshot.headless,true);assert.equal(snapshot.ownsLease,false);
  await assert.rejects(controller.consoleAction({action:'execute',command:'list'}),{code:'OPERATE_REQUIRED'});
  const completion=await controller.consoleAction({action:'complete',input:'li',cursor:2});assert.equal(completion.commandExecuted,false);
  await controller.consoleAction({action:'set_mode',mode:'OPERATE'});
  assert.equal((await controller.consoleAction({action:'execute',command:'list'})).operationId,'server-operation');
  assert(!runtime.calls.some(call=>/ui\/tree|capture|input|control\/acquire/.test(call.path)));
  await controller.close();
});
