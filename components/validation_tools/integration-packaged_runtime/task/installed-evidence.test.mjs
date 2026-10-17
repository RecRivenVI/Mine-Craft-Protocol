import test from 'node:test';
import assert from 'node:assert/strict';
import {installedProof} from './installed-evidence.mjs';

const artifact={target:'1.21.1-neoforge',sha256:'a'.repeat(64)},fingerprint='b'.repeat(64);
function valid(){return{target:artifact.target,status:'PASS',runtime:'installed_loader_release_jar',artifactSha256:artifact.sha256,sourceFingerprint:fingerprint,sourceChangedDuringRun:false,freshGameDirectory:true,endpointVerified:true,handshake:true,readPassed:true,titleReady:true,cleanShutdown:true,noCrashReports:true,developmentClasspath:false,externalModCount:1,exitCode:0,maximumHeapBytes:4294967296,instanceProfile:'automatic',windowWidth:1920,windowHeight:1080,persistentWriteCalls:0,quitHttpStatus:200,nettyHttpOrigin:'io.netty.handler.codec.http.HttpMessage source: file:/installation/netty-codec-http.jar',transportOrigin:'io.github.recrivenvi.minecraftprotocol.core.ProbeTransport source: file:/client/mods/product.jar',readSample:{dataCategory:'USER',available:false},providerRegistryResolved:true,providerCatalog:['safe','client-thread','render-thread','server-thread'].map(id=>({providerId:'product:'+id,threadAffinity:id==='client-thread'?'client_thread':id==='render-thread'?'render_thread':'server_thread',snapshotSchema:'fixture://snapshot',querySchema:'fixture://query'})),providerSpiOrigins:['AgentDataProviderV2','MinecraftProtocolProvidersV2','ProviderSchemaRegistry'].map(name=>'io.github.fixture.probe.api.'+name+' source: file:/client/mods/product.jar')};}
test('alpha accepts only actual clean-install final-JAR proof, not full gameplay acceptance',()=>{
  assert.equal(installedProof(valid(),artifact,fingerprint).status,'PASS');
  assert.throws(()=>installedProof({...valid(),quitHttpStatus:500},artifact,fingerprint),'Internal server failure cannot masquerade as an explained shutdown');
});
test('explicit shutdown cancellation and connection interruption remain distinct from a completed RPC',()=>{
  const quitError={error:'RUNTIME_SHUTTING_DOWN',executionOutcome:'not_confirmed',retrySafe:false};
  assert.equal(installedProof({...valid(),quitHttpStatus:503,quitError},artifact,fingerprint).status,'PASS');
  assert.throws(()=>installedProof({...valid(),quitHttpStatus:503},artifact,fingerprint));
  assert.throws(()=>installedProof({...valid(),quitHttpStatus:503,quitError:{...quitError,retrySafe:true}},artifact,fingerprint));
  const interrupted={...valid()};delete interrupted.quitHttpStatus;
  assert.throws(()=>installedProof(interrupted,artifact,fingerprint));
  assert.equal(installedProof({...interrupted,quitTransportError:'connection closed'},artifact,fingerprint).status,'PASS');
});
test('installation must resolve the actual public Provider SPI, not a catalog count or development directory',()=>{
  for(const patch of [{providerRegistryResolved:false},{providerCatalog:[]},{providerCatalog:Array(4).fill(valid().providerCatalog[0])},{providerSpiOrigins:[]},{providerSpiOrigins:Array(3).fill('unrelated source: file:/mods/product.jar')},{providerSpiOrigins:valid().providerSpiOrigins.map(s=>s.replace('file:/client/mods/product.jar','D:/repo/components/runtime_core/build/classes/java/main/'))}])assert.throws(()=>installedProof({...valid(),...patch},artifact,fingerprint));
});
test('alpha refuses stale, missing, development or failed installed Runtime results',()=>{
  for(const patch of [{instanceProfile:'acceptance'},{windowWidth:1280},{windowHeight:720},{artifactSha256:'c'.repeat(64)},{sourceFingerprint:'d'.repeat(64)},{status:'FAIL'},{runtime:'loader_development_source_sets'},{sourceChangedDuringRun:true},{freshGameDirectory:false},{endpointVerified:false},{handshake:false},{readPassed:false},{titleReady:false},{cleanShutdown:false},{noCrashReports:false},{developmentClasspath:true},{externalModCount:2},{exitCode:1},{persistentWriteCalls:1},{nettyHttpOrigin:''},{transportOrigin:'io.github.recrivenvi.minecraftprotocol.core.ProbeTransport source: D:\\repo\\versions\\1.21.1-neoforge\\build\\classes\\'}])assert.throws(()=>installedProof({...valid(),...patch},artifact,fingerprint),JSON.stringify(patch));
});
