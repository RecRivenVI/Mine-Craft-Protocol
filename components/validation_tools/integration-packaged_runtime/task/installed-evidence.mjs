import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {sha256} from '../../regression-v1_release/task/alpha-candidate.mjs';

export function installedProof(result,artifact,sourceFingerprint){
  assert.equal(result.target,artifact.target);assert.equal(result.status,'PASS');
  assert.equal(result.runtime,'installed_loader_release_jar');assert.equal(result.artifactSha256,artifact.sha256);
  assert.equal(result.sourceFingerprint,sourceFingerprint);assert.equal(result.sourceChangedDuringRun,false);
  for(const key of ['freshGameDirectory','endpointVerified','handshake','readPassed','titleReady','cleanShutdown','noCrashReports'])assert.equal(result[key],true,key);
  assert.equal(result.developmentClasspath,false);assert.equal(result.externalModCount,1);assert.equal(result.exitCode,0);
  assert.equal(result.maximumHeapBytes,4294967296);assert.equal(result.persistentWriteCalls,0);
  assert.equal(result.instanceProfile,'automatic');assert.equal(result.windowWidth,1920);assert.equal(result.windowHeight,1080);
  assert(result.nettyHttpOrigin?.includes('io.netty.handler.codec.http.HttpMessage source:'));
  assert(result.transportOrigin?.includes('io.github.recrivenvi.minecraftprotocol.core.ProbeTransport source:'));
  assert(!/[/\\]components[/\\]|[/\\]versions[/\\][^/\\]+[/\\]build[/\\]|\.gradle/i.test(result.transportOrigin));
  assert.equal(result.readSample?.dataCategory,'USER');assert.equal(result.readSample?.available,false);
  assert.equal(result.providerRegistryResolved,true);assert.equal(result.providerCatalog?.length,4);assert.equal(result.providerSpiOrigins?.length,3);
  assert.deepEqual(result.providerCatalog.map(p=>p.providerId.split(':').at(-1)).sort(),['client-thread','render-thread','safe','server-thread']);
  for(const p of result.providerCatalog){const name=p.providerId.split(':').at(-1);assert.equal(p.threadAffinity,name==='client-thread'?'client_thread':name==='render-thread'?'render_thread':'server_thread');assert.equal(typeof p.snapshotSchema,'string');assert.equal(typeof p.querySchema,'string');}
  for(const origin of result.providerSpiOrigins){assert.equal(typeof origin,'string');assert(origin.includes(' source:'));assert(!/[/\\]components[/\\]|[/\\]versions[/\\][^/\\]+[/\\]build[/\\]|\.gradle/i.test(origin));}
  for(const name of ['AgentDataProviderV2','MinecraftProtocolProvidersV2','ProviderSchemaRegistry'])assert(result.providerSpiOrigins.some(origin=>origin.includes('.probe.api.'+name+' source:')));
  if(result.quitHttpStatus!==undefined){
    assert(result.quitHttpStatus>=200&&result.quitHttpStatus<300||result.quitHttpStatus===503,'Quit cannot report internal HTTP 500');
    if(result.quitHttpStatus===503){assert.equal(result.quitError?.error,'RUNTIME_SHUTTING_DOWN');assert.equal(result.quitError.executionOutcome,'not_confirmed');assert.equal(result.quitError.retrySafe,false);}
  }else assert.equal(typeof result.quitTransportError,'string','An interrupted connection must remain explicit');
  return {target:artifact.target,status:'PASS',artifactSha256:artifact.sha256,sourceFingerprint,runtime:result.runtime,cleanShutdown:true,endpointVerified:true,readPassed:true,titleReady:true,providerRegistryResolved:true,providerSpiOrigins:result.providerSpiOrigins,providerCatalog:result.providerCatalog,instanceProfile:result.instanceProfile,windowWidth:result.windowWidth,windowHeight:result.windowHeight};
}
export async function collectInstalledProof(repo,artifacts,sourceFingerprint){
  const proofs=[];
  for(const artifact of artifacts){
    const path=`validations/integration-packaged_runtime/result/${artifact.target}.json`;
    const bytes=await readFile(resolve(repo,path));const result=JSON.parse(bytes.toString('utf8'));
    assert(Object.keys(result.toolHashes??{}).length>=4,'Installed proof must bind its tools');
    for(const [file,hash]of Object.entries(result.toolHashes))assert.equal(sha256(await readFile(resolve(repo,file))),hash,'Stale installed Runtime tool: '+file);
    proofs.push({...installedProof(result,artifact,sourceFingerprint),result:path,resultSha256:sha256(bytes)});
  }return proofs;
}
