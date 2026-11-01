import assert from 'node:assert/strict';
import { once } from 'node:events';
import type { Server } from 'node:http';
import { fetchBlockedPorts } from '../ui-console-http.js';

// A host can allocate an ephemeral port that Fetch refuses before connecting.
export async function listenFetch(server: Server): Promise<void> {
  for (let attempt = 0; attempt < 8; attempt++) {
    server.listen(0, '127.0.0.1');
    await once(server, 'listening');
    const address = server.address();
    assert(address && typeof address !== 'string');
    if (!fetchBlockedPorts.has(address.port)) return;
    await new Promise<void>((done, fail) => server.close(error => error ? fail(error) : done()));
  }
  throw new Error('No Fetch-compatible port allocated for the local test fixture');
}
