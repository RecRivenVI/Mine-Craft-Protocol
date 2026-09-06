#!/usr/bin/env node
import { serveStdio } from '@modelcontextprotocol/server/stdio';

import { loadConfig } from './config.js';
import { buildServer } from './server.js';

const config = loadConfig();
const handle = serveStdio(() => buildServer(config));

console.error('Mine-Craft-Protocol MCP Companion ready on stdio');

for (const signal of ['SIGINT', 'SIGTERM'] as const) {
  process.once(signal, () => {
    void handle.close().finally(() => process.exit(0));
  });
}
