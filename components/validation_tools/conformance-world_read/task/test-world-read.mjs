import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { resolve } from 'node:path';
import { execFileSync } from 'node:child_process';

const repo=resolve(import.meta.dirname,'../../../..');
const schema=JSON.parse(await readFile(resolve(repo,'components/protocol_schema/src/main/openapi/minecraft-control-v0.json'),'utf8'));
for(const path of ['/v0/player','/v0/server/player','/v0/world/block','/v0/server/world/block','/v0/world/entities','/v0/server/world/entities']) assert(schema.paths[path].get.parameters.some(value=>value.$ref==='#/components/parameters/WorldReadCategory'),path+' missing actual category contract');
for(const model of ['WorldDataCategory','WorldUserPlayerResponse','WorldReadContract']) assert(schema.components.schemas[model]);
for(const model of ['PlayerStateResponse','BlockStateResponse','EntityQueryResponse']) {
  const fields=schema.components.schemas[model].allOf[1];assert(fields.required.includes('dataCategory'));assert.equal(fields.properties.dataCategory.enum[0],'DEBUG');
}
assert.equal(schema.components.schemas.WorldReadContract.properties.consumerIsolationComplete.enum[0],false);
for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
  const main=target.endsWith('fabric')?'client':'main';
  const base=resolve(repo,'versions',target,'src',main,'java/io/github/recrivenvi/minecraftprotocol/probe/runtime');
  const transport=await readFile(resolve(repo,'components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core/ProbeTransport.java'),'utf8');
  assert(transport.includes('new WorldReadEngine(service, this.protocolState)'),target+' missing production boundary');
  assert.equal((transport.match(/\n\s*worldRead\(/g)??[]).length,6,target+' basic route bypass');
  const adapter=await readFile(resolve(base,target.endsWith('fabric')?'FabricProbeRuntime.java':target.startsWith('1.20.1')?'ForgeProbeRuntime.java':'NeoForgeProbeRuntime.java'),'utf8');
  assert(adapter.includes('new ProbeTransport(this, token, port, "'+target+'")'),target+' shared transport target identity not bound');
  for(const test of ['WorldReadEngineTest','WorldReadTransportTest']) assert((await readFile(resolve(repo,'components/runtime_core/src/test/java/io/github/recrivenvi/minecraftprotocol/core',test+'.java'),'utf8')).includes('class '+test));
}
for(const file of ['world-read-smoke.mjs','../../regression-v1_release/task/write-alpha-manifest.mjs'])execFileSync(process.execPath,['--check',resolve(import.meta.dirname,file)]);
execFileSync('pwsh',['-NoProfile','-Command',"$errors=$null;[void][System.Management.Automation.Language.Parser]::ParseFile('components/validation_tools/conformance-world_read/task/Invoke-WorldReadSmoke.ps1',[ref]$null,[ref]$errors);if($errors.Count){throw ($errors|Out-String)}"],{cwd:repo,stdio:'pipe'});
console.log('PASS: six basic route bindings, schema selectors, executable smoke syntax; production HTTP/permission tests run in each Target check');
