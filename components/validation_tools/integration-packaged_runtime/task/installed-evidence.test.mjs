import test from 'node:test';
import assert from 'node:assert/strict';
import {mkdir,writeFile,readFile} from 'node:fs/promises';
import {resolve,join} from 'node:path';
import {createHash,randomUUID} from 'node:crypto';
import {installedProof,collectInstalledProof} from './installed-evidence.mjs';
import {installedRuntimeScopes,findJava,copyInstalledWorldFixture,readInstalledWorldBaseline,installedWorldSmoke} from './installed-launcher.mjs';

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
  assert.match(source,/MCP_RUNTIME_SCOPES:installedRuntimeScopes\(\{enterWorld,extensionPlatform\}\)/);
  assert.equal(installedRuntimeScopes(),'read,ui,input,control');
  assert.equal(installedRuntimeScopes({enterWorld:true}),'read,ui,input,control,diagnostics');
  assert.deepEqual(installedRuntimeScopes({extensionPlatform:true}).split(','),['read','ui','input','event','diagnostics','control','command','debug','debug.world.read','debug.write','debug.provider']);
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

const hash=value=>createHash('sha256').update(value).digest('hex');
async function baselineFixture(){
  const repo=resolve(import.meta.dirname,'../../build/test/installed-world-'+randomUUID()),target=artifact.target;
  const root=join(repo,'validations/conformance-world_read/instance',target,'baseline'),game=join(repo,'installed/client');
  const data={'options.txt':'soundCategory_master:0.8\n','world-read-owner.json':JSON.stringify({target,owner:'world-read-classification'}),
    'saves/World-Read-Alpha/level.dat':'immutable test bytes, never launched\n','saves/World-Read-Alpha/region/r.0.0.mca':'immutable region bytes\n',
    'resourcepacks/example.txt':'not copied\n'};
  const files=[];
  for(const path of Object.keys(data).sort()){await mkdir(join(root,path,'..'),{recursive:true});await writeFile(join(root,path),data[path]);files.push({path,sha256:hash(data[path])});}
  const facts='loader_version=new\njava_version=21\n';await mkdir(join(repo,'versions',target),{recursive:true});await writeFile(join(repo,'versions',target,'target.properties'),facts);
  const manifest={schemaVersion:1,target,role:'client',owner:'world-read-classification',targetFactsSha256:hash('loader_version=old\n'),files,
    hash:hash(files.map(file=>file.path+':'+file.sha256).join('\n'))};
  await writeFile(join(root,'fixture-manifest.json'),JSON.stringify(manifest,null,2)+'\n');await mkdir(game,{recursive:true});
  return {repo,target,root,game,manifest,facts};
}

test('installed world copy preserves original baseline and old facts while copying only the exact world files',async()=>{
  const fixture=await baselineFixture(),before=await readFile(join(fixture.root,'fixture-manifest.json'));
  const result=await copyInstalledWorldFixture(fixture.repo,fixture.target,fixture.game);
  assert.equal(result.compatibilityBasis,'same_target_and_minecraft_unchanged');assert.equal(result.filesVerified,true);assert.equal(result.copiedFilesVerified,true);
  assert.equal(result.baselineTargetFactsSha256,fixture.manifest.targetFactsSha256);assert.equal(result.currentTargetFactsSha256,hash(fixture.facts));
  assert.notEqual(result.baselineTargetFactsSha256,result.currentTargetFactsSha256,'Loader changes must not require editing the old baseline manifest');
  assert.equal(result.worldFileCount,2);assert.equal(result.sourceFileCount,5);assert.equal(result.credentialsCopied,false);
  assert.deepEqual(await readFile(join(fixture.root,'fixture-manifest.json')),before);
  assert.deepEqual(await readFile(join(fixture.game,'saves/World-Read-Alpha/level.dat')),await readFile(join(fixture.root,'saves/World-Read-Alpha/level.dat')));
  for(const path of ['options.txt','resourcepacks/example.txt','world-read-owner.json','minecraft-protocol/token'])await assert.rejects(readFile(join(fixture.game,path)),{code:'ENOENT'});
  await assert.rejects(copyInstalledWorldFixture(fixture.repo,fixture.target,fixture.game),/completely new/);
});

test('world baseline import rejects changed bytes, unlisted files, wrong identity and traversal without altering the original manifest',async()=>{
  for(const edit of [
    async value=>writeFile(join(value.root,'saves/World-Read-Alpha/level.dat'),'tampered'),
    async value=>writeFile(join(value.root,'unexpected.txt'),'unlisted'),
    async value=>{value.manifest.target='26.3-fabric';await writeFile(join(value.root,'fixture-manifest.json'),JSON.stringify(value.manifest));},
    async value=>{value.manifest.files[0].path='saves/../../secret';await writeFile(join(value.root,'fixture-manifest.json'),JSON.stringify(value.manifest));},
    async value=>{value.manifest.hash='0'.repeat(64);await writeFile(join(value.root,'fixture-manifest.json'),JSON.stringify(value.manifest));}
  ]){const fixture=await baselineFixture();await edit(fixture);const original=await readFile(join(fixture.root,'fixture-manifest.json'));await assert.rejects(copyInstalledWorldFixture(fixture.repo,fixture.target,fixture.game));assert.deepEqual(await readFile(join(fixture.root,'fixture-manifest.json')),original);}
});

function worldSimulation(options={}){
  let milliseconds=0,screen='TitleScreen',inWorld=false,lease,acquires=0,releases=0,renews=0,generation=1,worldReads=0,listReads=0;
  const calls=[];
  const snapshot=()=>({pressedKeyCount:0,pressedButtonCount:0,inputCleanupPending:false,inputCleanupFailed:false,
    inputContextAvailable:inWorld,inputGeneration:generation,inputContextGeneration:generation,inputContextId:'context-'+generation});
  const tree=()=>({screenClass:screen,overlayIdentity:0,children:screen==='TitleScreen'?[{nodeId:'single',translationKey:'menu.singleplayer'}]
    :screen==='SelectWorldScreen'?[{nodeId:'world',worldName:'World-Read-Alpha'},{nodeId:'play',translationKey:'selectWorld.select'}]
      :screen==='PauseScreen'?[{nodeId:'exit-world',translationKey:'menu.returnToMenu'}]:[]});
  const api=async(method,path,body)=>{
    calls.push({method,path,body:structuredClone(body)});milliseconds+=2;
    if(method==='GET'&&path==='/v0/control/status')return {mode:lease?'TAKEOVER':'READ',status:lease?'active':'available',...(lease?{leaseId:lease}:{})};
    if(method==='POST'&&path==='/v0/control/renew'){assert.equal(body.ttlMs,60000);renews++;return {leaseId:options.changedLease?'foreign':lease};}
    if(method==='GET'&&path.startsWith('/v0/ui/tree')){const value=tree();if(screen==='SelectWorldScreen'&&options.asyncWorldList&&++listReads<4)value.children=[];return value;}
    if(method==='GET'&&path==='/v0/input/state')return snapshot();
    if(method==='GET'&&path==='/v0/player?category=USER'){
      worldReads++;if(options.slowPlayer&&worldReads<72){milliseconds+=150;return {available:false};}
      return {available:inWorld,dataCategory:options.debugRead?'DEBUG':'USER',authority:'CLIENT_KNOWN',chunkLoadRequested:false,storageAccessed:false,x:1,y:64,z:1,dimension:'minecraft:overworld'};
    }
    if(method==='GET'&&path==='/v0/session')return {target:artifact.target,inWorld};
    if(method==='POST'&&path==='/v0/ui/action'){
      const id=body.selector.nodeId;
      if(id==='single')screen='SelectWorldScreen';
      else if(id==='world')assert.equal(screen,'SelectWorldScreen');
      else if(id==='play'){inWorld=true;screen='';generation++;}
      else if(id==='exit-world'){
        screen='TitleScreen';inWorld=false;generation++;
        if(options.cancelExit)throw Object.assign(new Error('Native exit response not confirmed'),{status:409,runtimeError:{error:'REQUEST_CANCELLED',executionOutcome:'not_confirmed',retrySafe:false}});
        if(options.badExit)throw Object.assign(new Error('unexpected failure'),{status:500,runtimeError:{error:'INTERNAL_ERROR'}});
      }else assert.fail('Unknown native UI target');
      return {type:'ui.action_result'};
    }
    if(method==='POST'&&path==='/v0/pipelines'){
      assert.deepEqual(body.steps,[{type:'key.tap',key:256,holdMs:40}]);assert.equal(body.cleanupOnComplete,true);screen='PauseScreen';return {operationId:'pause'};
    }
    if(method==='POST'&&path==='/v0/operations/pause/wait')return {state:'completed',result:{cleanupOnComplete:true,steps:[{stepType:'key.tap',result:{inputOrigin:'AGENT_ROUTED',entryLayer:'GAME_ROUTED_RAW',directMutationUsed:false}}]}};
    if(method==='POST'&&path==='/v0/wait/until'){assert.deepEqual(body.condition,{type:'input.released'});return {passed:true,evidence:{pressedKeyCount:0,pressedButtonCount:0}};}
    assert.fail('Unexpected installed world API '+method+' '+path);
  };
  return {context:{api,target:artifact.target,async acquire(){assert(!lease);acquires++;lease='own-lease';return {leaseId:lease};},async release(){assert.equal(lease,'own-lease');releases++;lease=undefined;}},
    services:{now:()=>milliseconds,sleep:async value=>{milliseconds+=value;}},calls,state:()=>({lease,acquires,releases,renews,inWorld})};
}

test('installed enter-world smoke renews one Lease, independently reads USER player and returns to title without commands or probe',async()=>{
  const fixture=worldSimulation(),result=await installedWorldSmoke(fixture.context,fixture.services);
  assert.equal(result.enteredWorld,true);assert.equal(result.worldReadSample.available,true);assert.equal(result.worldSession.inWorld,true);
  assert.equal(result.worldInputState.inputContextAvailable,true);assert.equal(result.worldExit.after.inputContextAvailable,false);
  assert.equal(result.worldExit.requestOutcome,'response_received');assert.equal(result.worldLease.preserved,true);
  assert.deepEqual(fixture.state(),{lease:undefined,acquires:1,releases:1,renews:1,inWorld:false});
  assert(!fixture.calls.some(value=>/command|debug|fixture/.test(value.path)));
});

test('installed world entry waits for the actual asynchronous world row, not only the screen class',async()=>{
  const fixture=worldSimulation({asyncWorldList:true}),result=await installedWorldSmoke(fixture.context,fixture.services);
  assert.equal(result.enteredWorld,true);assert.equal(fixture.calls.filter(call=>call.path==='/v0/ui/action'&&call.body.selector.nodeId==='world').length,1);
  assert.equal(fixture.state().acquires,1);assert.equal(fixture.state().releases,1);
});

test('SaveQuit cancellation is retained as not confirmed and never replayed; other errors fail with owned Lease released',async()=>{
  const fixture=worldSimulation({cancelExit:true}),result=await installedWorldSmoke(fixture.context,fixture.services);
  assert.equal(result.worldExit.requestOutcome,'not_confirmed');assert.equal(result.worldExit.error.status,409);assert.equal(result.worldExit.requestReplayed,false);
  assert.equal(fixture.calls.filter(value=>value.body?.selector?.nodeId==='exit-world').length,1);
  for(const options of [{badExit:true},{changedLease:true},{debugRead:true}]){
    const failed=worldSimulation(options);await assert.rejects(installedWorldSmoke(failed.context,failed.services));assert.equal(failed.state().lease,undefined);
  }
});

function validWorld(){
  const result=valid(),current='c'.repeat(64),snapshot=(generation,available)=>({pressedKeyCount:0,pressedButtonCount:0,inputCleanupPending:false,inputCleanupFailed:false,inputContextAvailable:available,inputGeneration:generation,inputContextGeneration:generation,inputContextId:'context-'+generation});
  return {...result,enterWorldRequested:true,enteredWorld:true,sourceHashes:{[`versions/${artifact.target}/target.properties`]:current},
    worldInput:{policy:'immutable_baseline_read_only_copy',source:`validations/conformance-world_read/instance/${artifact.target}/baseline`,target:artifact.target,minecraft:'1.21.1',worldName:'World-Read-Alpha',
      manifestSha256:'1'.repeat(64),baselineSha256:'2'.repeat(64),baselineTargetFactsSha256:'3'.repeat(64),currentTargetFactsSha256:current,worldFilesSha256:'4'.repeat(64),compatibilityBasis:'same_target_and_minecraft_unchanged',sourceFileCount:5,worldFileCount:2,filesVerified:true,copiedFilesVerified:true,credentialsCopied:false},
    worldReadSample:{dataCategory:'USER',authority:'CLIENT_KNOWN',available:true,storageAccessed:false,chunkLoadRequested:false,x:1,y:64,z:1,dimension:'minecraft:overworld'},
    worldSession:{target:artifact.target,inWorld:true},worldTree:{screenClass:'',overlayIdentity:0},worldInputState:snapshot(1,true),
    pause:{state:'completed',result:{cleanupOnComplete:true,steps:[{stepType:'key.tap'}]}},
    worldExit:{outcome:'world_exit_independently_observed',requestOutcome:'response_received',requestReplayed:false,response:{type:'ui.action_result'},screenClass:'TitleScreen',overlayIdentity:0,before:snapshot(1,true),after:snapshot(2,false),released:{passed:true,evidence:{pressedKeyCount:0,pressedButtonCount:0}}},
    worldLease:{leaseId:'own',renewals:[{leaseId:'own'}],preserved:true,finalStatus:{mode:'READ',status:'available'}}};
}

test('installed proof keeps the default title scope and optionally requires the complete independent enter-world evidence',()=>{
  assert.equal(installedProof(valid(),artifact,fingerprint).enteredWorld,false);
  assert.throws(()=>installedProof(valid(),artifact,fingerprint,{requireEnteredWorld:true}));
  const world=validWorld();assert.equal(installedProof(world,artifact,fingerprint,{requireEnteredWorld:true}).enteredWorld,true);
  for(const edit of [value=>{value.enteredWorld=false;},value=>{value.worldReadSample.available=false;},value=>{value.worldReadSample.dataCategory='DEBUG';},
    value=>{value.worldSession.inWorld=false;},value=>{value.worldInput.currentTargetFactsSha256='d'.repeat(64);},value=>{value.worldInput.credentialsCopied=true;},
    value=>{value.worldLease.renewals[0].leaseId='foreign';},value=>{value.worldExit.requestReplayed=true;},value=>{value.worldExit.after.inputContextGeneration=1;},
    value=>{value.worldExit.after.pressedKeyCount=1;},value=>{value.readSample.available=true;},value=>{value.worldTree.overlayIdentity=1;}]){
    const invalid=structuredClone(world);edit(invalid);assert.throws(()=>installedProof(invalid,artifact,fingerprint,{requireEnteredWorld:true}));
  }
});

test('collectInstalledProof forwards the required enter-world policy rather than trusting a summary flag',async()=>{
  const repo=resolve(import.meta.dirname,'../../build/test/installed-proof-'+randomUUID());
  const resultRoot=join(repo,'validations/integration-packaged_runtime/result');await mkdir(resultRoot,{recursive:true});
  const tool='components/tool.js';await mkdir(join(repo,'components'),{recursive:true});await writeFile(join(repo,tool),'tool\n');
  const toolHashes=Object.fromEntries([tool,'tool1','tool2','tool3'].map(name=>[name,hash('tool\n')]));
  for(const name of ['tool1','tool2','tool3'])await writeFile(join(repo,name),'tool\n');
  await writeFile(join(resultRoot,artifact.target+'.json'),JSON.stringify({...valid(),toolHashes}));
  assert.equal((await collectInstalledProof(repo,[artifact],fingerprint))[0].enteredWorld,false);
  await assert.rejects(collectInstalledProof(repo,[artifact],fingerprint,{requireEnteredWorld:true}));
  await writeFile(join(resultRoot,artifact.target+'.json'),JSON.stringify({...validWorld(),toolHashes}));
  assert.equal((await collectInstalledProof(repo,[artifact],fingerprint,{requireEnteredWorld:true}))[0].enteredWorld,true);
});
