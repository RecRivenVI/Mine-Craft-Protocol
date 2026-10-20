import { randomBytes, randomInt, timingSafeEqual } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { createServer, type IncomingMessage, type ServerResponse } from 'node:http';
import { fileURLToPath } from 'node:url';
import { RuntimeError } from './runtime-client.js';
import type { JsonObject } from './types.js';
import { UiConsole } from './ui-console.js';

const assets: Record<string, [string, string]> = {
  '/': ['index.html', 'text/html'], '/app.mjs': ['app.mjs', 'text/javascript'],
  '/model.mjs': ['model.mjs', 'text/javascript'], '/style.css': ['style.css', 'text/css']
};

// https://fetch.spec.whatwg.org/#port-blocking applies before an HTTP connection is attempted.
const fetchBlockedPorts = new Set([0, 1, 7, 9, 11, 13, 15, 17, 19, 20, 21, 22, 23, 25, 37, 42, 43, 53,
  69, 77, 79, 87, 95, 101, 102, 103, 104, 109, 110, 111, 113, 115, 117, 119, 123, 135, 137, 139, 143,
  161, 179, 389, 427, 465, 512, 513, 514, 515, 526, 530, 531, 532, 540, 548, 554, 556, 563, 587, 601,
  636, 989, 990, 993, 995, 1719, 1720, 1723, 2049, 3659, 4045, 4190, 5060, 5061, 6000, 6566,
  6665, 6666, 6667, 6668, 6669, 6679, 6697, 10080]);
const automaticBindAttempts = 8;

export async function startUiConsole(controller: UiConsole, port = 4317) {
  if (!Number.isSafeInteger(port) || port < 0 || port > 65535)
    throw new RuntimeError('WEB_UI_INVALID_PORT', 400, '网页端口必须是0（自动分配）或1–65535的整数');
  if (port !== 0 && fetchBlockedPorts.has(port))
    throw new RuntimeError('WEB_UI_PORT_BLOCKED', 400, `网页端口${port}被HTTP Fetch标准限制，请使用其他端口或0自动分配`);
  const browsers = new Map<string, number>();
  let origin = '';
  const server = createServer(async (request, response) => {
    response.setHeader('cache-control', 'no-store');
    response.setHeader('x-content-type-options', 'nosniff');
    response.setHeader('referrer-policy', 'no-referrer');
    response.setHeader('content-security-policy', "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' blob:; connect-src 'self'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'");
    const send = (status: number, value: unknown): void => {
      response.writeHead(status, { 'content-type': 'application/json; charset=utf-8' });
      response.end(JSON.stringify(value));
    };
    try {
      if (request.headers.host !== new URL(origin).host || (request.headers.origin && request.headers.origin !== origin)
          || request.headers['sec-fetch-site'] === 'cross-site') {
        send(403, { error: 'WEB_UI_ORIGIN_REJECTED', message: '仅允许本机同源访问' }); return;
      }
      const path = new URL(request.url ?? '/', origin).pathname;
      if (request.method === 'GET' && assets[path]) {
        const [name, contentType] = assets[path];
        response.writeHead(200, { 'content-type': `${contentType}; charset=utf-8` });
        response.end(await readFile(fileURLToPath(new URL(`../web/${name}`, import.meta.url)))); return;
      }
      if (request.method === 'GET' && path === '/api/bootstrap') {
        for (const [id, seen] of browsers) if (Date.now() - seen > 60000) browsers.delete(id);
        if (browsers.size >= 16) { send(429, { error: 'WEB_UI_BROWSER_LIMIT' }); return; }
        const browser = randomBytes(32).toString('hex');
        browsers.set(browser, Date.now());
        send(200, { browserToken: browser, runtimeUrl: controller.client.config.baseUrl.origin }); return;
      }
      const browser = request.headers['x-web-ui-token'];
      const known = typeof browser === 'string' && /^[0-9a-f]{64}$/.test(browser) && [...browsers.keys()].find(id =>
        id.length === browser.length && timingSafeEqual(Buffer.from(id), Buffer.from(browser)));
      if (!known) { send(403, { error: 'WEB_UI_SESSION_REQUIRED', message: '请刷新网站重新建立本机页面会话' }); return; }
      browsers.set(known, Date.now());
      if (request.method === 'GET' && path === '/api/snapshot') { send(200, await controller.snapshot(known)); return; }
      if (request.method === 'POST' && path === '/api/capture') {
        const capture = await controller.capture();
        response.writeHead(200, { 'content-type': 'image/png', 'x-web-ui-frame': encodeURIComponent(capture.frame),
          'x-web-ui-captured-at': String(Date.now()) });
        response.end(capture.bytes); return;
      }
      if (request.method !== 'POST') { send(404, { error: 'WEB_UI_ROUTE_NOT_FOUND' }); return; }
      if (!(request.headers['content-type'] ?? '').startsWith('application/json')) { send(415, { error: 'WEB_UI_JSON_REQUIRED' }); return; }
      const body = await readBody(request);
      if (path === '/api/acquire') send(200, await controller.acquire(known));
      else if (path === '/api/release') send(200, await controller.release(known));
      else if (path === '/api/action') send(200, await controller.action(known, body));
      else if (path === '/api/input') send(200, await controller.input(known, body));
      else if (path === '/api/input/reset') send(200, await controller.resetInput(known));
      else if (path === '/api/key') send(200, await controller.key(known, body.key));
      else send(404, { error: 'WEB_UI_ROUTE_NOT_FOUND' });
    } catch (error) {
      if (response.headersSent) { response.destroy(); return; }
      if (error instanceof RuntimeError) send(error.status, { error: error.code, message: error.message,
        ...(error.control ? { control: error.control } : {}), ...(error.reconsentRequired ? { reconsentRequired: true, controlState: error.controlState } : {}),
        ...(error.rateLimit ? { retryAfterMillis: error.retryAfterMillis, rateLimit: error.rateLimit } : {}),
        ...(error.actualHit ? {actualHit:error.actualHit} : {}) });
      else send(500, { error: 'WEB_UI_REQUEST_FAILED', message: '本机网站请求失败；不会重放输入，请刷新查看实例状态' });
    }
  });
  server.requestTimeout = 30000;
  server.headersTimeout = 10000;
  server.maxConnections = 32;
  for (let attempt = 0; attempt < automaticBindAttempts; attempt++) {
    // OS port=0 may select Fetch-blocked ports; the high dynamic range has no such ports.
    const candidate = port === 0 ? randomInt(49152, 65536) : port;
    try {
      await new Promise<void>((resolve, reject) => {
        const listening = (): void => { server.off('error', failed); resolve(); };
        const failed = (error: Error): void => { server.off('listening', listening); reject(error); };
        server.once('error', failed); server.once('listening', listening);
        try { server.listen(candidate, '127.0.0.1'); }
        catch (error) { server.off('error', failed); server.off('listening', listening); reject(error); }
      });
      break;
    } catch (error) {
      if (port === 0 && (error as NodeJS.ErrnoException).code === 'EADDRINUSE' && attempt + 1 < automaticBindAttempts) continue;
      throw error;
    }
  }
  const address = server.address();
  if (!address || typeof address === 'string') throw new Error('No local listener');
  origin = `http://127.0.0.1:${address.port}`;
  const timer = setInterval(() => { void controller.tick(); }, 1000);
  timer.unref();
  return { server, url: origin, close: async () => {
    clearInterval(timer);
    await controller.close();
    server.closeAllConnections();
    await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()));
  } };
}

async function readBody(request: IncomingMessage): Promise<JsonObject> {
  const chunks: Buffer[] = [];
  let length = 0;
  for await (const chunk of request) {
    const bytes = Buffer.from(chunk as Uint8Array);
    length += bytes.length;
    if (length > 16384) throw new RuntimeError('WEB_UI_BODY_LIMIT', 413, '请求超出 16 KiB 预算');
    chunks.push(bytes);
  }
  try {
    const parsed: unknown = JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}');
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error();
    return parsed as JsonObject;
  } catch { throw new RuntimeError('WEB_UI_INVALID_JSON', 400, '请求必须是 JSON 对象'); }
}
