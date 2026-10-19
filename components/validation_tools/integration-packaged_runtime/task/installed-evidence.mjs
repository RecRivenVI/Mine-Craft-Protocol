import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {sha256} from '../../regression-v1_release/task/alpha-candidate.mjs';
import {contextExitEvidence} from '../../conformance-world_read/task/context-exit-evidence.mjs';

function installedWorldProof(result){
  assert.equal(result.enterWorldRequested,true);assert.equal(result.enteredWorld,true);
  const input=result.worldInput;assert.equal(input?.policy,'immutable_baseline_read_only_copy');
  assert.equal(input.target,result.target);assert.equal(input.minecraft,result.target.slice(0,result.target.lastIndexOf('-')));
  assert.equal(input.worldName,'World-Read-Alpha');assert.equal(input.source,`validations/conformance-world_read/instance/${result.target}/baseline`);
  assert.equal(input.compatibilityBasis,'same_target_and_minecraft_unchanged');
  for(const field of ['manifestSha256','baselineSha256','baselineTargetFactsSha256','currentTargetFactsSha256','worldFilesSha256'])assert.match(input[field],/^[0-9a-f]{64}$/);
  assert.equal(input.currentTargetFactsSha256,result.sourceHashes?.[`versions/${result.target}/target.properties`]);
  for(const field of ['sourceFileCount','worldFileCount'])assert(Number.isSafeInteger(input[field])&&input[field]>0&&input[field]<=100000);
  assert(input.worldFileCount<=input.sourceFileCount);assert.equal(input.filesVerified,true);assert.equal(input.copiedFilesVerified,true);assert.equal(input.credentialsCopied,false);
  const player=result.worldReadSample;assert.equal(player?.available,true);assert.equal(player.dataCategory,'USER');assert.equal(player.authority,'CLIENT_KNOWN');
  assert.equal(player.chunkLoadRequested,false);assert.equal(player.storageAccessed,false);
  for(const axis of ['x','y','z'])assert(Number.isFinite(player[axis]));assert.equal(typeof player.dimension,'string');assert(player.dimension.length>0);
  assert.equal(result.worldSession?.target,result.target);assert.equal(result.worldSession.inWorld,true);
  assert.equal(result.worldTree?.screenClass,'');assert.equal(result.worldTree.overlayIdentity,0);
  const clean=value=>{assert.equal(value?.pressedKeyCount,0);assert.equal(value.pressedButtonCount,0);assert.equal(value.inputCleanupPending,false);assert.equal(value.inputCleanupFailed,false);};
  clean(result.worldInputState);assert.equal(result.worldInputState.inputContextAvailable,true);
  const exit=result.worldExit;assert.equal(exit?.outcome,'world_exit_independently_observed');assert.equal(exit.requestReplayed,false);
  assert(exit.screenClass?.endsWith('TitleScreen'));assert.equal(exit.overlayIdentity,0);clean(exit.after);
  assert.equal(exit.before.inputContextAvailable,true);assert.equal(exit.after.inputContextAvailable,false);
  for(const key of ['inputGeneration','inputContextGeneration']){assert(Number.isSafeInteger(exit.before[key])&&exit.before[key]>=0);assert(Number.isSafeInteger(exit.after[key])&&exit.after[key]>exit.before[key]);}
  assert.equal(typeof exit.before.inputContextId,'string');assert(exit.before.inputContextId.length>0);assert.equal(typeof exit.after.inputContextId,'string');assert(exit.after.inputContextId.length>0);assert.notEqual(exit.after.inputContextId,exit.before.inputContextId);
  assert.equal(exit.released?.passed,true);assert.deepEqual(exit.released.evidence,{pressedKeyCount:0,pressedButtonCount:0});
  if(exit.requestOutcome==='not_confirmed')contextExitEvidence(exit.before,exit.after,{screenClass:exit.screenClass,overlayIdentity:exit.overlayIdentity},exit.error);
  else{assert.equal(exit.requestOutcome,'response_received');assert(exit.response&&typeof exit.response==='object');}
  assert.equal(result.pause?.state,'completed');assert.equal(result.pause.result?.cleanupOnComplete,true);
  assert.equal(result.pause.result.steps.length,1);assert.equal(result.pause.result.steps[0].stepType,'key.tap');
  const lease=result.worldLease;assert.equal(lease?.preserved,true);assert.equal(typeof lease.leaseId,'string');assert(lease.leaseId.length>0);
  assert(Array.isArray(lease.renewals)&&lease.renewals.length>0);for(const renewal of lease.renewals)assert.equal(renewal.leaseId,lease.leaseId);
  assert.equal(lease.finalStatus?.mode,'READ');assert.equal(lease.finalStatus.status,'available');assert(!lease.finalStatus.leaseId);
  return {enteredWorld:true,worldInput:input,worldReadSample:player,worldSession:result.worldSession,worldExit:exit,worldLease:lease};
}

export function installedProof(result,artifact,sourceFingerprint,options={}){
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
  if(options.requireEnteredWorld===true)assert.equal(result.enterWorldRequested,true,'This delivery requires the explicit installed enter-world smoke');
  let world={enteredWorld:false};
  if(result.enterWorldRequested===true||result.enteredWorld===true)world=installedWorldProof(result);
  else{assert(result.enterWorldRequested===undefined||result.enterWorldRequested===false);assert(result.enteredWorld===undefined||result.enteredWorld===false);}
  return {target:artifact.target,status:'PASS',artifactSha256:artifact.sha256,sourceFingerprint,runtime:result.runtime,cleanShutdown:true,endpointVerified:true,readPassed:true,titleReady:true,providerRegistryResolved:true,providerSpiOrigins:result.providerSpiOrigins,providerCatalog:result.providerCatalog,instanceProfile:result.instanceProfile,windowWidth:result.windowWidth,windowHeight:result.windowHeight,...world};
}
export async function collectInstalledProof(repo,artifacts,sourceFingerprint,options={}){
  const proofs=[];
  for(const artifact of artifacts){
    const path=`validations/integration-packaged_runtime/result/${artifact.target}.json`;
    const bytes=await readFile(resolve(repo,path));const result=JSON.parse(bytes.toString('utf8'));
    assert(Object.keys(result.toolHashes??{}).length>=4,'Installed proof must bind its tools');
    for(const [file,hash]of Object.entries(result.toolHashes))assert.equal(sha256(await readFile(resolve(repo,file))),hash,'Stale installed Runtime tool: '+file);
    proofs.push({...installedProof(result,artifact,sourceFingerprint,options),result:path,resultSha256:sha256(bytes)});
  }return proofs;
}
