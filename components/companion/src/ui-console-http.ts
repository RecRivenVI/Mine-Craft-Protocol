import { randomBytes, timingSafeEqual } from 'node:crypto';
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

export async function startUiConsole(controller: UiConsole, port = 4317) {
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
        ...(error.control ? { control: error.control } : {}), ...(error.reconsentRequired ? { reconsentRequired: true, controlState: error.controlState } : {}) });
      else send(500, { error: 'WEB_UI_REQUEST_FAILED', message: '本机网站请求失败；不会重放输入，请刷新查看实例状态' });
    }
  });
  server.requestTimeout = 30000;
  server.headersTimeout = 10000;
  server.maxConnections = 32;
  await new Promise<void>((resolve, reject) => {
    server.once('error', reject);
    server.listen(port, '127.0.0.1', () => resolve());
  });
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
