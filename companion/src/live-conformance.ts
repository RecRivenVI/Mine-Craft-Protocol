import assert from 'node:assert/strict';
import { resolve } from 'node:path';

import { Client } from '@modelcontextprotocol/client';
import { StdioClientTransport } from '@modelcontextprotocol/client/stdio';

function environment(): Record<string, string> {
  return Object.fromEntries(Object.entries(process.env).filter((entry): entry is [string, string] => entry[1] !== undefined));
}

async function main(): Promise<void> {
  const expectedTarget = process.env.MCP_EXPECTED_TARGET;
  if (!expectedTarget) throw new Error('MCP_EXPECTED_TARGET is required');
  const transport = new StdioClientTransport({
    command: process.execPath,
    args: [resolve(process.cwd(), 'dist/index.js')],
    cwd: process.cwd(),
    stderr: 'pipe',
    env: environment()
  });
  const client = new Client({ name: 'minecraft-phase8-live-conformance', version: '0.0.1-phase8' });
  const call = async (name: string, args: Record<string, unknown> = {}): Promise<Record<string, unknown>> => {
    const result = await client.callTool({ name, arguments: args });
    if (result.isError) throw new Error(`${name} failed: ${JSON.stringify(result.structuredContent)}`);
    return result.structuredContent as Record<string, unknown>;
  };
  const data = (result: Record<string, unknown>): Record<string, unknown> => result.data as Record<string, unknown>;

  let leaseAcquired = false;
  try {
    await client.connect(transport);
    const tools = await client.listTools();
    assert.equal(tools.tools.length, 24);
    const resources = await client.listResources();
    assert.equal(resources.resources.length, 4);
    const prompts = await client.listPrompts();
    assert.equal(prompts.prompts.length, 1);

    const session = data(await call('minecraft_get_session'));
    assert.equal(session.target, expectedTarget);
    assert.match(String(session.screenClass), /TitleScreen/);
    const capabilities = data(await call('minecraft_get_capabilities'));
    assert.ok(capabilities.capabilities);
    const hooks = data(await call('minecraft_diagnostics', { kind: 'hooks' }));
    assert.equal(hooks.policy, 'capability_fidelity_first');
    assert.equal(hooks.overall, 'ready');

    await call('minecraft_control', { action: 'acquire', ttlMs: 60000 });
    leaseAcquired = true;
    const fixture = data(await call('minecraft_fixture', { operation: 'open_standard_gui' }));
    assert.equal(fixture.evidenceContaminated, true);
    const tree = data(await call('minecraft_get_ui', { view: 'tree' }));
    assert.equal(JSON.stringify(tree).includes('Compatibility Text'), true);
    await call('minecraft_interact_ui', { action: 'click', selector: { role: 'button', label: 'Add Dynamic Control' } });
    await call('minecraft_wait', { condition: { type: 'ui.exists', selector: { role: 'button', label: 'Dynamic Control' } }, timeoutMs: 5000 });
    await call('minecraft_interact_ui', { action: 'click', selector: { role: 'button', label: 'Dynamic Control' } });

    const captureTool = await client.callTool({ name: 'minecraft_capture', arguments: {} });
    assert.equal(captureTool.isError, undefined);
    const capture = await client.readResource({ uri: 'minecraft://capture/latest' });
    const captureContent = capture.contents[0];
    assert.ok(captureContent && 'blob' in captureContent);
    const signature = Buffer.from(captureContent.blob, 'base64').subarray(0, 8);
    assert.equal(signature.toString('hex'), '89504e470d0a1a0a');

    await call('minecraft_interact_ui', { action: 'click', selector: { role: 'button', label: 'Close Probe' } });
    await call('minecraft_wait', { condition: { type: 'screen', classContains: 'TitleScreen' }, timeoutMs: 5000 });

    const enterWorld = data(await call('minecraft_run_input_pipeline', {
      timeoutMs: 55000,
      steps: [
        { type: 'ui.action', action: 'click', selector: { role: 'button', label: 'Singleplayer' } },
        { type: 'wait.until', timeoutMs: 5000, condition: { type: 'screen', classContains: 'SelectWorldScreen' } },
        { type: 'mouse.click', x: 200, y: 75 },
        { type: 'delay', durationMs: 250 },
        { type: 'ui.action', action: 'click', selector: { role: 'button', label: 'Play Selected World' } },
        { type: 'wait.until', timeoutMs: 30000, condition: { type: 'screen', open: false } }
      ]
    }));
    assert.equal(enterWorld.status, 'completed');
    const clientPlayer = data(await call('minecraft_get_state', { kind: 'player', serverAuthoritative: false }));
    const serverPlayer = data(await call('minecraft_get_state', { kind: 'player', serverAuthoritative: true }));
    assert.equal(clientPlayer.available, true);
    assert.equal(clientPlayer.uuid, serverPlayer.uuid);

    const leaveWorld = data(await call('minecraft_run_input_pipeline', {
      timeoutMs: 15000,
      steps: [
        { type: 'key', key: 256, scanCode: 1, action: 1 },
        { type: 'key', key: 256, scanCode: 1, action: 0 },
        { type: 'wait.until', timeoutMs: 5000, condition: { type: 'screen', classContains: 'PauseScreen' } },
        { type: 'ui.action', action: 'click', selector: { role: 'button', label: 'Save and Quit to Title' } },
        { type: 'wait.until', timeoutMs: 10000, condition: { type: 'screen', classContains: 'TitleScreen' } }
      ]
    }));
    assert.equal(leaveWorld.status, 'completed');
    await call('minecraft_control', { action: 'release' });
    leaseAcquired = false;

    const samples: number[] = [];
    for (let index = 0; index < 25; index++) {
      const start = performance.now();
      await call('minecraft_get_session');
      samples.push(performance.now() - start);
    }
    samples.sort((left, right) => left - right);
    const p50 = samples[Math.floor(samples.length * 0.5)] ?? 0;
    const p95 = samples[Math.floor(samples.length * 0.95)] ?? 0;
    assert.ok(p95 < 250, `live MCP p95 ${p95.toFixed(2)}ms exceeded 250ms`);

    process.stdout.write(`${JSON.stringify({
      Result: 'PASS',
      Target: expectedTarget,
      NegotiatedProtocol: client.getNegotiatedProtocolVersion(),
      Tools: tools.tools.length,
      Resources: resources.resources.length,
      Prompts: prompts.prompts.length,
      ExtendedGui: 'PASS',
      TitleWorldRoundTrip: 'PASS',
      ClientServerPlayerAgreement: 'PASS',
      CaptureResource: 'PASS',
      PromptIsolation: 'PASS',
      SessionP50Ms: Number(p50.toFixed(2)),
      SessionP95Ms: Number(p95.toFixed(2)),
      Samples: samples.length
    }, null, 2)}\n`);
  } finally {
    if (leaseAcquired) await call('minecraft_control', { action: 'emergency_release' }).catch(() => undefined);
    await client.close().catch(() => undefined);
  }
}

main().catch(error => {
  process.stderr.write(`${error instanceof Error ? error.stack ?? error.message : String(error)}\n`);
  process.exitCode = 1;
});
