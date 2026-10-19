import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdir,writeFile,readFile} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {installedProof} from './installed-evidence.mjs';
import {findJava} from './installed-launcher.mjs';

const artifact={target:'1.21.1-neoforge',sha256:'a'.repeat(64)},fingerprint='b'.repeat(64);
function valid(){return{target:artifact.target,status:'PASS',runtime:'installed_loader_release_jar',artifactSha256:artifact.sha256,sourceFingerprint:fingerprint,sourceChangedDuringRun:false,freshGameDirectory:true,endpointVerified:true,handshake:true,readPassed:true,titleReady:true,cleanShutdown:true,noCrashReports:true,developmentClasspath:false,externalModCount:1,exitCode:0,maximumHeapBytes:4294967296,instanceProfile:'automatic',windowWidth:1920,windowHeight:1080,persistentWriteCalls:0,quitHttpStatus:200,nettyHttpOrigin:'io.netty.handler.codec.http.HttpMessage source: file:/installation/netty-codec-http.jar',transportOrigin:'io.github.recrivenvi.minecraftprotocol.core.ProbeTransport source: file:/client/mods/product.jar',readSample:{dataCategory:'USER',available:false},providerRegistryResolved:true,providerCatalog:['safe','client-thread','render-thread','server-thread'].map(id=>({providerId:'product:'+id,threadAffinity:id==='client-thread'?'client_thread':id==='render-thread'?'render_thread':'server_thread',snapshotSchema:'fixture://snapshot',querySchema:'fixture://query'})),providerSpiOrigins:['AgentDataProviderV2','MinecraftProtocolProvidersV2','ProviderSchemaRegistry'].map(name=>'io.github.fixture.probe.api.'+name+' source: file:/client/mods/product.jar')};}
test('installed launcher resolves game and installer JDK majors independently from release facts',async()=>{
  const jdks=resolve(import.meta.dirname,'../../build/test/installed-java');
  for(const [name,version]of [['jdk170','170.0.1'],['jdk17','17.0.19'],['jdk25','25.0.4']]){
    const root=join(jdks,name);await mkdir(join(root,'bin'),{recursive:true});
    await writeFile(join(root,'bin/java.exe'),'fixture, never executed\n');
    await writeFile(join(root,'release'),`JAVA_VERSION="${version}"\n`);
  }
  assert.equal(await findJava('17',jdks),join(jdks,'jdk17/bin/java.exe'));
  assert.equal(await findJava('25',jdks),join(jdks,'jdk25/bin/java.exe'));
  await assert.rejects(findJava('21',jdks),/No installed Java 21/);
  for(const invalid of [undefined,'','25.0.4','25|17'])await assert.rejects(findJava(invalid,jdks),/explicit Java major/);
});
test('official installer uses daemon JVM facts while the game retains Target Java',async()=>{
  const source=await readFile(new URL('./installed-launcher.mjs',import.meta.url),'utf8');
  assert.match(source,/const java=await findJava\(facts\.java_version\)/);
  assert.match(source,/daemonFactsPath='gradle\/gradle-daemon-jvm\.properties'/);
  assert.match(source,/installerJavaVersion=\(await properties\(join\(repo,daemonFactsPath\)\)\)\.toolchainVersion/);
  assert.match(source,/installerJava=await findJava\(installerJavaVersion\)/);
  assert.match(source,/toolHashes\[daemonFactsPath\]=sha256/);
  assert.match(source,/await run\(installerJava,\['-Djava\.awt\.headless=true','-jar',installer,'--installClient',home\]/);
  assert.match(source,/const child=spawn\(java,args,/);
  assert.match(source,/loader==='fabric'\?\{\}:\{installerJavaVersion\}/);
});
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
