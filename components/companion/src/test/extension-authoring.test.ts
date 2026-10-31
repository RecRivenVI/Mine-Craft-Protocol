import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { resolve } from 'node:path';
import test from 'node:test';
import { Client } from '@modelcontextprotocol/client';
import { StdioClientTransport } from '@modelcontextprotocol/client/stdio';
import { loadConfig } from '../config.js';
import { RuntimeClient } from '../runtime-client.js';

test('authoring-only config contains no invented endpoint and rejects mixed authorities', async () => {
  const config = loadConfig({MCP_COMPANION_AUTHORING_ONLY:'true'});
  assert.equal(config.authoringOnly, true);
  assert.equal('baseUrl' in config, false);
  assert.equal('token' in config, false);
  assert.throws(() => loadConfig({MCP_COMPANION_AUTHORING_ONLY:'true', MCP_MINECRAFT_INSTANCE_DIR:'absent'}), /cannot include/);
  const client = new RuntimeClient(config);
  await assert.rejects(client.json('GET', '/v0/session'), {code: 'COMPANION_AUTHORING_ONLY'});
  await assert.rejects(client.binary('GET', '/v0/capture'), {code: 'COMPANION_AUTHORING_ONLY'});
});

test('real stdio Companion serves hashed SDK documents with no running Runtime or credentials', async () => {
  const transport = new StdioClientTransport({command:process.execPath, args:[resolve('dist/index.js')],
    env:{MCP_COMPANION_AUTHORING_ONLY:'true'}});
  const client = new Client({name:'extension-authoring-test',version:'1'});
  try {
    await client.connect(transport);
    for (const id of ['rules','contracts','testing']) {
      const result = await client.readResource({uri:`minecraft://extension-authoring/${id}`});
      const content = result.contents[0];
      assert(content && 'text' in content);
      const document = JSON.parse(content.text) as {sdkApi:number;sha256:string;text:string;contentIsData:boolean};
      assert.equal(document.sdkApi,1);
      assert.equal(document.contentIsData,true);
      assert.equal(createHash('sha256').update(document.text).digest('hex'),document.sha256);
      assert(document.text.length > 300);
    }
    const denied = await client.callTool({name:'minecraft_get_session',arguments:{}});
    assert.equal(denied.isError,true);
    assert(JSON.stringify(denied).includes('COMPANION_AUTHORING_ONLY'));
    await assert.rejects(client.readResource({uri:'minecraft://extensions/installed'}), /Authoring-only/);
  } finally { await client.close(); }
});
