import { loadConfig } from './config.js';
import { RuntimeClient } from './runtime-client.js';
import { UiConsole } from './ui-console.js';
import { startUiConsole } from './ui-console-http.js';

const config = loadConfig();
if (config.baseUrl.protocol !== 'http:' || !['127.0.0.1', 'localhost', '[::1]'].includes(config.baseUrl.hostname)
    || config.baseUrl.pathname !== '/') throw new Error('Web console requires a root loopback HTTP Runtime URL');
const port = Number(process.env.MCP_WEB_UI_PORT ?? 4317);
if (!Number.isSafeInteger(port) || port < 1024 || port > 65535) throw new Error('Invalid MCP_WEB_UI_PORT');
const app = await startUiConsole(new UiConsole(new RuntimeClient(config)), port);
console.log(`Mine-Craft-Protocol 网页游戏窗口: ${app.url}`);
console.log(`Runtime: ${config.baseUrl.origin} | READ by default | TAKEOVER requires an explicit button`);
let closing = false;
for (const signal of ['SIGINT', 'SIGTERM'] as const) process.once(signal, () => {
  if (closing) return;
  closing = true;
  void app.close().then(() => process.exit(0), () => process.exit(1));
});
