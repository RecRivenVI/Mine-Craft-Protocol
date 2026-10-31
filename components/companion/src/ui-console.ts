import { RuntimeClient, RuntimeError } from './runtime-client.js';
import type { JsonObject, JsonValue } from './types.js';

const frameFields = ['target', 'screenClass', 'screenIdentity', 'screenRevision', 'overlayIdentity',
  'menuIdentity', 'menuRevision', 'width', 'height', 'windowWidth', 'windowHeight', 'guiScale', 'gameCursorRevision', 'editorContextKey', 'uiInputContext', 'spectatorContextKey'];

export function frameKey(tree: JsonObject): string {
  return JSON.stringify(frameFields.map(field => tree[field] ?? null));
}

export function inputFrameKey(tree: JsonObject): string {
  return JSON.stringify(frameFields.filter(field => !['menuRevision', 'gameCursorRevision'].includes(field)).map(field => tree[field] ?? null));
}

export function flattenNodes(tree: JsonObject): JsonObject[] {
  const result: JsonObject[] = [];
  const walk = (value: JsonValue | undefined, depth: number): void => {
    if (!Array.isArray(value) || depth > 32) return;
    for (const node of value) {
      if (!node || typeof node !== 'object' || Array.isArray(node)) continue;
      if (result.length >= 4096) throw new RuntimeError('WEB_UI_TREE_LIMIT', 413, '界面节点超出网页预算');
      result.push(node);
      walk(node.children, depth + 1);
    }
  };
  walk(tree.children, 0);
  return result;
}

function failure(code: string, message: string): never {
  throw new RuntimeError(code, 409, message);
}

export class UiConsole {
  private owner: string | undefined;
  private leaseId: string | undefined;
  private taskId: string | undefined;
  private lastSeen = 0;
  private lastRenew = 0;
  private busy = false;
  private ticking = false;
  private closed = false;
  private generation = 0;
  private abort = new AbortController();
  private inputSequence = 0;
  private heldKeys = new Set<number>();
  private heldButtons = new Set<number>();
  private gestureFrame: string | undefined;
  private lastControl: JsonObject = {};

  constructor(readonly client: RuntimeClient) {}

  async snapshot(browser: string, attempt=0): Promise<JsonObject> {
    const version = this.generation;
    if (this.owner === browser) this.lastSeen = Date.now();
    const session = await this.client.json('GET', '/v0/session');
    if (session.deploymentRole === 'DEDICATED_SERVER') {
      const [control, console] = await Promise.all([this.client.json('GET', '/v0/control/mode'),
        this.client.json('GET','/v0/server/console?after=0&limit=128').catch((error:unknown) => {
          if(error instanceof RuntimeError)return {available:false,error:error.code,message:error.message};
          throw error;
        })]);
      return {session,control,console,headless:true,ownsLease:false,otherBrowserOwnsLease:false,
        runtimeUrl:this.client.connectionConfig.baseUrl.origin,observedAt:Date.now()};
    }
    const [control, tree, input] = await Promise.all([
      this.client.json('GET', '/v0/control/mode'),
      this.client.json('GET', '/v0/ui/tree'), this.client.json('GET', '/v0/input/state')
    ]);
    if(version===this.generation)this.lastControl=control;
    if (tree.dataCategory !== 'USER') failure('WEB_UI_CATEGORY_MISMATCH', '网页用户操作窗口只接受明确标记为 USER 的数据，请使用支持 UI 类别合同的 Runtime');
    if (version === this.generation && this.leaseId && (control.mode !== 'TAKEOVER' || control.reconsentRequired === true)) await this.releaseOwned();
    if(version!==this.generation){if(attempt<2)return this.snapshot(browser,attempt+1);failure('WEB_UI_STATE_CHANGED','控制状态持续变化，请稍后刷新；不发布混合身份的快照');}
    return { session, control, tree, input, frameKey: frameKey(tree), observedAt: Date.now(),
      runtimeUrl: this.client.connectionConfig.baseUrl.origin, ownsLease: this.owner === browser && !!this.leaseId,
      controllerBusy: this.busy, inputFrameKey: inputFrameKey(tree), inputSequence: this.inputSequence,
      otherBrowserOwnsLease: !!this.owner && this.owner !== browser };
  }

  async acquire(browser: string): Promise<JsonObject> {
    if (this.closed) failure('WEB_UI_CLOSED', '网站正在关闭');
    if (this.owner === browser && this.leaseId) return this.snapshot(browser);
    if (this.owner || this.busy) failure('WEB_UI_OWNER_BUSY', '另一个页面或操作已占用控制；不会抢占');
    this.busy = true;
    const version = this.generation;
    try {
      const control = await this.client.json('GET', '/v0/control/mode');
      if (control.mode === 'TAKEOVER') failure('WEB_UI_OWNER_BUSY', '实例已被其他控制者接管');
      const activeTask = control.agentTask;
      if (activeTask && typeof activeTask === 'object' && !Array.isArray(activeTask) && activeTask.active)
        failure('WEB_UI_OWNER_BUSY', '实例存在其他活动任务');
      const task = await this.client.json('POST', '/v0/control/task', { action: 'begin', ttlMs: 15000, budgetMs: 300000 });
      if (typeof task.taskId !== 'string') failure('WEB_UI_INVALID_TASK', 'Runtime 未返回任务身份');
      this.taskId = task.taskId;
      if (version !== this.generation || this.closed) failure('WEB_UI_CANCELLED', '接管申请已取消');
      const lease = await this.client.json('POST', '/v0/control/acquire', {
        ttlMs: 15000, taskId: this.taskId, expectedModeVersion: control.modeVersion ?? null
      });
      if (typeof lease.leaseId !== 'string') failure('WEB_UI_INVALID_LEASE', 'Runtime 未返回 Lease');
      this.leaseId = lease.leaseId;
      if (version !== this.generation || this.closed) failure('WEB_UI_CANCELLED', '接管申请已取消');
      this.owner = browser;
      this.generation++;
      this.lastSeen = this.lastRenew = Date.now();
      this.abort = new AbortController();
      this.inputSequence = 0;
      return await this.snapshot(browser);
    } catch (error) {
      await this.releaseOwned();
      throw error;
    } finally { this.busy = false; }
  }

  async consoleAction(body:JsonObject):Promise<JsonObject> {
    const session=await this.client.json('GET','/v0/session');
    if(session.deploymentRole!=='DEDICATED_SERVER')failure('WEB_CONSOLE_NOT_APPLICABLE','当前实例不是独立服务器');
    if(body.action==='set_mode') {
      if(body.mode!=='READ'&&body.mode!=='OPERATE')failure('WEB_CONSOLE_MODE','服务端仅适用 READ/OPERATE');
      const current=await this.client.json('GET','/v0/control/mode');
      return this.client.json('POST','/v0/control/mode',{mode:body.mode,expectedModeVersion:current.modeVersion??null});
    }
    if(body.action==='complete')return this.client.json('POST','/v0/server/console/suggestions',{input:body.input??'',...(body.cursor===undefined?{}:{cursor:body.cursor})});
    if(body.action==='execute')return this.client.json('POST','/v0/server/console/commands',{command:body.command??''});
    failure('WEB_CONSOLE_ACTION','不支持的控制台操作');
  }

  async release(browser: string): Promise<JsonObject> {
    if (this.owner && this.owner !== browser) failure('WEB_UI_NOT_OWNER', '只能释放本页面取得的 Lease');
    await this.releaseOwned();
    return this.snapshot(browser);
  }

  private requireOwner(browser: string): string {
    if(this.lastControl.reconsentRequired===true)
      throw new RuntimeError('USER_MANUALLY_ENDED_CONTROL',409,'用户手动结束控制；重新接管需要重新明确同意',undefined,
        {controlState:'MANUALLY_REVOKED',reconsentRequired:true,control:this.lastControl});
    if (this.owner !== browser || !this.leaseId) failure('TAKEOVER_REQUIRED', '请先明确申请 TAKEOVER；浏览不会自动接管');
    if (this.closed) failure('WEB_UI_CLOSED', '网站正在关闭');
    this.lastSeen = Date.now();
    return this.leaseId;
  }

  /** Browser events are a bounded GUI-only stream over the existing Native input endpoints. */
  async input(browser: string, request: JsonObject): Promise<JsonObject> {
    const lease = this.requireOwner(browser);
    if (this.busy) failure('WEB_UI_GESTURE_BUSY', '上一个输入正在执行，不会积压旧事件');
    const seq = request.sequence;
    if (typeof seq !== 'number' || !Number.isSafeInteger(seq) || seq !== this.inputSequence + 1)
      failure('WEB_UI_INPUT_SEQUENCE', '输入次序失效；不会重放鼠标或按键');
    this.busy = true;
    this.inputSequence = seq;
    const version = this.generation;
    try {
      const tree = await this.client.json('GET', '/v0/ui/tree');
      const frame = inputFrameKey(tree);
      // A press may replace the Screen. Its release is cleanup, not input into the new target.
      if (request.action === 0 && ((request.type === 'button' && typeof request.button === 'number' && this.heldButtons.has(request.button))
          || (request.type === 'key' && typeof request.key === 'number' && this.heldKeys.has(request.key)))) {
        const button = request.type === 'button';
        const result = await this.client.json('POST', button ? '/v0/input/mouse/button' : '/v0/input/key',
          button ? {button: request.button ?? null, action: 0, modifiers: 0} : {key:request.key ?? null,scanCode:0,action:0,modifiers:0},
          {headers:{'x-mcp-control-lease':lease},signal:this.abort.signal});
        if(button)this.heldButtons.delete(Number(request.button));else this.heldKeys.delete(Number(request.key));
        this.gestureFrame=this.heldButtons.size||this.heldKeys.size?this.gestureFrame:undefined;
        return request.type==='button'||[256,257,69].includes(Number(request.key))?{result,sequence:seq,snapshot:await this.snapshot(browser)}:{result,sequence:seq};
      }
      if (!tree.screenClass || (tree.overlayIdentity && tree.overlayIdentity !== 0))
        failure('WEB_UI_GUI_REQUIRED', '网页只操作 GUI，不提供第一人称控制');
      if (request.inputFrameKey !== frame || (this.gestureFrame && this.gestureFrame !== frame))
        failure('WEB_UI_STALE_FRAME', '界面已变化，已取消未完成输入');
      const options = { headers: { 'x-mcp-control-lease': lease,
        'x-mcp-expected-screen-revision': String(tree.screenRevision) }, signal: this.abort.signal, timeoutMs: 25000 };
      const modifiers = typeof request.modifiers === 'number' && Number.isInteger(request.modifiers)
        && request.modifiers >= 0 && request.modifiers <= 15 ? request.modifiers : 0;
      const type = request.type;
      let result: JsonObject;
      if (type === 'move' || type === 'button' || type === 'scroll') {
        const x = request.x, y = request.y;
        if (typeof x !== 'number' || typeof y !== 'number' || !Number.isFinite(x) || !Number.isFinite(y)
          || x < 0 || y < 0 || x >= Number(tree.width) || y >= Number(tree.height))
          failure('WEB_UI_INVALID_COORDINATES', '鼠标必须位于当前游戏 GUI 内');
        if (type === 'button' && (typeof request.button !== 'number' || !Number.isInteger(request.button)||request.button<0||request.button>7
          || (request.action !== 0 && request.action !== 1))) failure('WEB_UI_INVALID_INPUT','无效鼠标按钮事件');
        if (type === 'scroll' && ([request.xOffset??0,request.yOffset??0].some(n=>typeof n!=='number'||!Number.isFinite(n)||Math.abs(n)>20)))
          failure('WEB_UI_INVALID_INPUT','无效滚轮事件');
        result = await this.client.json('POST', '/v0/input/mouse/move', { x, y, motion: 'direct' }, options);
        if (version !== this.generation) failure('WEB_UI_CANCELLED', '接管已结束');
        if (type === 'button') {
          const button = request.button, action = request.action;
          if (typeof button !== 'number' || !Number.isInteger(button)||button<0||button>7 || (action !== 0 && action !== 1))
            failure('WEB_UI_INVALID_INPUT', '无效鼠标按钮事件');
          if (action === 1) this.heldButtons.add(button);
          result = await this.client.json('POST', '/v0/input/mouse/button', { button, action, modifiers }, options);
          if (action === 0) this.heldButtons.delete(button);
        } else if (type === 'scroll') {
          const xOffset = request.xOffset ?? 0, yOffset = request.yOffset ?? 0;
          if (typeof xOffset !== 'number' || typeof yOffset !== 'number' || !Number.isFinite(xOffset)
            || !Number.isFinite(yOffset) || Math.abs(xOffset)>20 || Math.abs(yOffset)>20)
            failure('WEB_UI_INVALID_INPUT', '无效滚轮事件');
          result = await this.client.json('POST', '/v0/input/mouse/scroll', { xOffset, yOffset }, options);
        }
      } else if (type === 'key') {
        const key = request.key, action = request.action;
        if (typeof key !== 'number' || !Number.isInteger(key) || key < 32 || key > 348 || typeof action !== 'number' || ![0,1,2].includes(action))
          failure('WEB_UI_INVALID_INPUT', '无效键盘事件');
        if (action !== 0) this.heldKeys.add(key);
        result = await this.client.json('POST', '/v0/input/key', { key, scanCode: 0, action, modifiers }, options);
        if (action === 0) this.heldKeys.delete(key);
      } else if (type === 'text') {
        if (typeof request.text !== 'string' || request.text.length > 2048)
          failure('WEB_UI_INVALID_INPUT', '无效文本输入');
        const fields = flattenNodes(tree).filter(node => node.role === 'text_field' && node.focused === true
          && node.active !== false && node.visible !== false && Array.isArray(node.actions) && node.actions.includes('type_text'));
        if (fields.length !== 1) failure('WEB_UI_TEXT_FOCUS_REQUIRED', '没有唯一的游戏文本焦点；请先点击输入位置');
        result = await this.client.json('POST', '/v0/ui/action', { selector: { nodeId: fields[0]!.nodeId ?? null },
          action: 'type_text', text: request.text, replace: false, focusOnly: true }, options);
      } else failure('WEB_UI_INVALID_INPUT', '不支持的输入事件');
      this.gestureFrame = this.heldButtons.size || this.heldKeys.size ? frame : undefined;
      const changesContext=type==='button'||type==='scroll'||(type==='key'&&[256,257,69].includes(Number(request.key)));
      return changesContext?{result,sequence:seq,snapshot:await this.snapshot(browser)}:{result,sequence:seq};
    } catch (error) {
      await this.resetInput(browser).catch(() => this.releaseOwned());
      throw error;
    } finally { this.busy = false; }
  }

  async resetInput(browser: string): Promise<JsonObject> {
    if(this.busy) { this.requireOwner(browser);await this.releaseOwned();return {sequence:this.inputSequence,released:true}; }
    const lease = this.requireOwner(browser);
    const options = { headers: { 'x-mcp-control-lease': lease } };
    for (const button of this.heldButtons) await this.client.json('POST', '/v0/input/mouse/button', {button,action:0,modifiers:0},options);
    for (const key of this.heldKeys) await this.client.json('POST', '/v0/input/key', {key,scanCode:0,action:0,modifiers:0},options);
    this.heldKeys.clear();this.heldButtons.clear();this.gestureFrame=undefined;
    return { sequence: this.inputSequence };
  }

  async action(browser: string, request: JsonObject): Promise<JsonObject> {
    const lease = this.requireOwner(browser);
    if (this.busy) failure('WEB_UI_GESTURE_BUSY', '上一个操作尚未结束；不会排队点击旧界面');
    this.busy = true;
    const version = this.generation;
    let shiftHeld = false;
    try {
      const tree = await this.client.json('GET', '/v0/ui/tree');
      if (request.frameKey !== frameKey(tree)) failure('WEB_UI_STALE_FRAME', '界面已经变化，请刷新后再点击');
      const action = typeof request.action === 'string' ? request.action : 'click';
      if(request.button!==undefined&&(typeof request.button!=='number'||!Number.isInteger(request.button)||request.button<0||request.button>7))failure('WEB_UI_INVALID_ACTION','无效鼠标按钮；不会改成左键');
      if (!['hover', 'click', 'double_click', 'scroll', 'scroll_into_view', 'type_text', 'set_value', 'select', 'activate', 'open'].includes(action))
        failure('WEB_UI_INVALID_ACTION', '不支持的语义操作');
      if (action === 'type_text' && typeof request.text !== 'string')
        failure('WEB_UI_INVALID_ACTION', '文本操作必须明确提供文本；缺失文本不会清空字段');
      let target: JsonObject;
      if (request.coordinates !== undefined) {
        const point = request.coordinates;
        if (action !== 'click' || request.nodeId !== undefined || !point || typeof point !== 'object' || Array.isArray(point)
            || typeof point.x !== 'number' || typeof point.y !== 'number' || !Number.isFinite(point.x) || !Number.isFinite(point.y)
            || point.x < 0 || point.y < 0 || point.x >= Number(tree.width) || point.y >= Number(tree.height)
            || !tree.screenClass) failure('WEB_UI_INVALID_COORDINATES', '只允许当前 GUI 范围内的显式坐标点击');
        target = { coordinates: { x: point.x, y: point.y }, source: 'explicit_coordinate' };
      } else {
        const node = flattenNodes(tree).find(value => value.nodeId === request.nodeId);
        if (!node || node.active === false || node.occluded === true || (node.visible === false && action !== 'scroll_into_view')) failure('WEB_UI_TARGET_UNAVAILABLE', '目标已隐藏、遮挡、禁用或不存在');
        if (!Array.isArray(node.actions) || !node.actions.includes(action))
          failure('WEB_UI_TARGET_UNAVAILABLE', '该节点不提供所选操作；不会退回坐标点击');
        target = { selector: { nodeId: node.nodeId ?? null } };
      }
      if (version !== this.generation) failure('WEB_UI_CANCELLED', '接管已结束');
      if (request.shift === true && ['click', 'double_click'].includes(action)) {
        shiftHeld = true;
        await this.client.json('POST', '/v0/input/key', { key: 340, scanCode: 0, action: 1, modifiers: 1 },
          { headers: { 'x-mcp-control-lease': lease }, signal: this.abort.signal });
      }
      const result = await this.client.json('POST', '/v0/ui/action', {
        ...target, action,
        button: request.button ?? 0, modifiers: request.shift === true ? 1 : 0, holdMs: 80,
        ...(action === 'type_text' ? { text: request.text ?? '', replace: request.replace !== false } : {}),
        ...(action === 'set_value' ? { value: request.value ?? null } : {}),
        ...(action === 'scroll' ? { yOffset: request.yOffset ?? 0 } : {})
      }, { headers: { 'x-mcp-control-lease': lease }, signal: this.abort.signal, timeoutMs: 25000 });
      if (shiftHeld) {
        await this.client.json('POST', '/v0/input/key', { key: 340, scanCode: 0, action: 0, modifiers: 0 },
          { headers: { 'x-mcp-control-lease': lease } });
        shiftHeld = false;
      }
      return { result, snapshot: await this.snapshot(browser) };
    } finally {
      if (shiftHeld) await this.client.json('POST', '/v0/input/key', { key: 340, scanCode: 0, action: 0, modifiers: 0 },
        { headers: { 'x-mcp-control-lease': lease } }).catch(() => this.releaseOwned());
      this.busy = false;
    }
  }

  async key(browser: string, key: JsonValue | undefined): Promise<JsonObject> {
    const lease = this.requireOwner(browser);
    if (key !== 'Escape' && key !== 'Inventory') failure('WEB_UI_KEY_NOT_ALLOWED', '仅提供返回和背包快捷键');
    if (this.busy) failure('WEB_UI_GESTURE_BUSY', '上一个操作尚未结束');
    this.busy = true;
    let operationId: string | undefined;
    try {
      const started = await this.client.json('POST', '/v0/pipelines', {
        steps: [{ type: 'key.tap', key: key === 'Escape' ? 256 : 69, holdMs: 60 }], timeoutMs: 10000
      }, { headers: { 'x-mcp-control-lease': lease }, signal: this.abort.signal });
      if (typeof started.operationId !== 'string') failure('WEB_UI_INVALID_OPERATION', 'Runtime 未返回操作身份');
      operationId = started.operationId;
      const result = await this.client.json('POST', `/v0/operations/${encodeURIComponent(operationId)}/wait`,
        { timeoutMs: 10000 }, { signal: this.abort.signal, timeoutMs: 12000 });
      if (result.state !== 'completed' && result.status !== 'completed') failure('WEB_UI_OPERATION_FAILED', '快捷键未完成；请检查当前状态');
      operationId = undefined;
      return { result, snapshot: await this.snapshot(browser) };
    } finally {
      if (operationId) await this.client.json('DELETE', `/v0/operations/${encodeURIComponent(operationId)}`).catch(() => undefined);
      this.busy = false;
    }
  }

  async capture(): Promise<{ bytes: Uint8Array; frame: string }> {
    const before = await this.client.json('GET', '/v0/ui/tree');
    const image = await this.client.binary('GET', '/v0/capture', { maxResponseBytes: 16 * 1024 * 1024, timeoutMs: 25000 });
    if (!image.contentType.startsWith('image/png')) failure('WEB_UI_INVALID_CAPTURE', 'Runtime 未返回 PNG');
    const after = await this.client.json('GET', '/v0/ui/tree');
    if (frameKey(before) !== frameKey(after)) failure('WEB_UI_STALE_CAPTURE', '截图期间界面变化，请重新获取');
    return { bytes: image.bytes, frame: frameKey(after) };
  }

  async tick(): Promise<void> {
    if (this.ticking || !this.leaseId || this.closed) return;
    this.ticking = true;
    try {
      if (Date.now() - this.lastSeen > 6000) { await this.releaseOwned(); return; }
      if (Date.now() - this.lastRenew < 5000) return;
      const lease = this.leaseId;
      const task = this.taskId;
      const control = await this.client.json('GET', '/v0/control/mode');
      if (control.mode !== 'TAKEOVER' || control.reconsentRequired) { await this.releaseOwned(); return; }
      if (task) await this.client.json('POST', '/v0/control/task', { action: 'heartbeat', taskId: task, ttlMs: 15000 });
      await this.client.json('POST', '/v0/control/renew', { ttlMs: 15000 }, { headers: { 'x-mcp-control-lease': lease } });
      this.lastRenew = Date.now();
    } catch { await this.releaseOwned(); }
    finally { this.ticking = false; }
  }

  private forget(): void {
    this.generation++;
    this.abort.abort();
    this.owner = this.leaseId = this.taskId = undefined;
    this.heldKeys.clear();this.heldButtons.clear();this.gestureFrame=undefined;
  }

  private async releaseOwned(): Promise<void> {
    const lease = this.leaseId;
    const task = this.taskId;
    this.forget();
    if (lease) await this.client.json('POST', '/v0/control/release', undefined,
      { headers: { 'x-mcp-control-lease': lease } }).catch(() => undefined);
    if (task) await this.client.json('POST', '/v0/control/task', { action: 'end', taskId: task }).catch(() => undefined);
  }

  async close(): Promise<void> { this.closed = true; await this.releaseOwned(); }
}
