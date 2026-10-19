import assert from 'node:assert/strict';
import {readFile,writeFile,mkdir,readdir,copyFile,stat,access} from 'node:fs/promises';
import {createHash,randomUUID} from 'node:crypto';
import {resolve,join,dirname,delimiter,basename} from 'node:path';
import {homedir} from 'node:os';
import {spawn,execFileSync} from 'node:child_process';
import {createWriteStream} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {connectRuntime,automaticTakeoverRequest} from '../../runtime-connection.mjs';
import {candidateSource,sha256} from '../../regression-v1_release/task/alpha-candidate.mjs';

export async function findJava(version,jdks=join(homedir(),'.gradle/jdks')){
  assert.match(String(version),/^[1-9]\d*$/,'An explicit Java major version is required');
  for(const entry of await readdir(jdks,{withFileTypes:true})){
    if(!entry.isDirectory())continue;const candidate=join(jdks,entry.name,'bin/java.exe');
    if(!await access(candidate).then(()=>true,()=>false))continue;
    const release=await readFile(join(dirname(dirname(candidate)),'release'),'utf8').catch(()=>'');
    if(new RegExp('JAVA_VERSION="'+version+'[.\\"]').test(release))return candidate;
  }
  assert.fail('No installed Java '+version);
}

async function main(){
const repo=resolve(import.meta.dirname,'../../../..');
const target=process.argv.find(v=>v.startsWith('--target='))?.slice(9);
assert(['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric'].includes(target));
const root=resolve(repo,'validations/integration-packaged_runtime/instance/installed',target);
const home=join(root,'installation'),game=join(root,'client');
const cache=resolve(repo,'validations/integration-packaged_runtime/instance/downloads');
const gradle=join(homedir(),'.gradle/caches');
const digest=(bytes,algorithm='sha256')=>createHash(algorithm).update(bytes).digest('hex');
const exists=path=>access(path).then(()=>true,()=>false);
const properties=async path=>Object.fromEntries((await readFile(path,'utf8')).split(/\r?\n/).filter(l=>l.includes('=')&&!l.startsWith('#')).map(l=>{const i=l.indexOf('=');return[l.slice(0,i),l.slice(i+1)];}));
const facts=await properties(join(repo,'versions',target,'target.properties')),product=await properties(join(repo,'gradle.properties'));
const targets=[...(await readFile(join(repo,'settings.gradle.kts'),'utf8')).matchAll(/register\("([^"\r\n]+)"\)/g)].map(match=>match[1]);
const sourceHashes=await candidateSource(repo,targets),sourceFingerprint=sha256(JSON.stringify(sourceHashes));
const toolHashes={};
for(const path of ['components/validation_tools/integration-packaged_runtime/task/installed-launcher.mjs','components/validation_tools/integration-packaged_runtime/task/Invoke-InstalledRuntimeSmoke.ps1','components/validation_tools/integration-packaged_runtime/task/InstalledInstanceCleanup.ps1','components/validation_tools/integration-packaged_runtime/task/installed-evidence.mjs','components/validation_tools/regression-v1_release/task/alpha-candidate.mjs','validations/integration-packaged_runtime/validation.toml'])toolHashes[path]=sha256(await readFile(join(repo,path)));
const instanceFacts=await readFile(join(repo,'validations/integration-packaged_runtime/validation.toml'),'utf8');
const width=Number(instanceFacts.match(/^width\s*=\s*(\d+)$/m)?.[1]),height=Number(instanceFacts.match(/^height\s*=\s*(\d+)$/m)?.[1]);
assert(Number.isSafeInteger(width)&&width>0&&Number.isSafeInteger(height)&&height>0,'Explicit installed test dimensions required');
const minecraft=target.slice(0,target.lastIndexOf('-')),loader=target.slice(target.lastIndexOf('-')+1);
await mkdir(cache,{recursive:true});
async function download(url,path,sha1,alternatives=[]){
  for(const source of [path,...alternatives])if(await exists(source)){
    const bytes=await readFile(source);
    if(sha1&&digest(bytes,'sha1')!==sha1)continue;
    if(source!==path){await mkdir(dirname(path),{recursive:true});await copyFile(source,path);}return bytes;
  }
  let failure;
  for(let attempt=0;attempt<3;attempt++)try{
    const response=await fetch(url,{signal:AbortSignal.timeout(120000)});assert(response.ok,`${response.status} ${url}`);
    const bytes=Buffer.from(await response.arrayBuffer());if(sha1)assert.equal(digest(bytes,'sha1'),sha1,`Official checksum: ${url}`);
    await mkdir(dirname(path),{recursive:true});const temporary=path+'.part-'+randomUUID();await writeFile(temporary,bytes);
    const {rename}=await import('node:fs/promises');await rename(temporary,path);return bytes;
  }catch(error){failure=error;}
  throw failure;
}
async function json(url,path,sha1,alternatives=[]){return JSON.parse((await download(url,path,sha1,alternatives)).toString('utf8'));}
async function parallel(items,action,concurrency=12){let next=0;await Promise.all(Array.from({length:Math.min(concurrency,items.length)},async()=>{for(;;){const index=next++;if(index>=items.length)return;await action(items[index]);}}));}
function permitted(rules){
  if(!rules)return true;let allowed=false;
  for(const rule of rules){
    if(rule.os?.name&&rule.os.name!=='windows')continue;
    if(rule.os?.arch&&!['x86_64','amd64','x64'].includes(rule.os.arch))continue;
    if(rule.os?.version&&!new RegExp(rule.os.version).test('10.0'))continue;
    if(rule.features&&Object.entries(rule.features).some(([,expected])=>expected!==false))continue;
    allowed=rule.action==='allow';
  }return allowed;
}
async function run(java,args,cwd,log,timeout=600000){
  const output=createWriteStream(log);const child=spawn(java,args,{cwd,windowsHide:true,stdio:['ignore','pipe','pipe']});
  child.stdout.pipe(output,{end:false});child.stderr.pipe(output,{end:false});
  const code=await new Promise((yes,no)=>{const timer=setTimeout(()=>{child.kill();no(Error('Owned installer deadline: '+log));},timeout);child.once('error',e=>{clearTimeout(timer);no(e);});child.once('exit',c=>{clearTimeout(timer);yes(c);});});
  output.end();assert.equal(code,0,'Official installer failed; see '+log);
}
const java=await findJava(facts.java_version);
let installerJava,installerJavaVersion;
if(loader!=='fabric'){
  const daemonFactsPath='gradle/gradle-daemon-jvm.properties';
  installerJavaVersion=(await properties(join(repo,daemonFactsPath))).toolchainVersion;
  installerJava=await findJava(installerJavaVersion);
  toolHashes[daemonFactsPath]=sha256(await readFile(join(repo,daemonFactsPath)));
}
await mkdir(home,{recursive:true});await mkdir(game,{recursive:true});
assert.equal((await readdir(game)).length,0,'Game directory must be completely new');
const launcherManifest=await json('https://piston-meta.mojang.com/mc/game/version_manifest_v2.json',join(cache,'version_manifest_v2.json'));
const version=launcherManifest.versions.find(v=>v.id===minecraft);assert(version);
const vanilla=await json(version.url,join(home,'versions',minecraft,minecraft+'.json'),version.sha1,[join(gradle,'neoformruntime/artifacts',`minecraft_${minecraft}_version_manifest.json`)]);
await download(vanilla.downloads.client.url,join(home,'versions',minecraft,minecraft+'.jar'),vanilla.downloads.client.sha1,[join(gradle,'neoformruntime/artifacts',`minecraft_${minecraft}_client.jar`)]);
console.log('Official Minecraft '+minecraft+' verified; installing '+loader+' '+facts.loader_version);
let profile;
if(loader==='fabric'){
  profile=await json(`https://meta.fabricmc.net/v2/versions/loader/${minecraft}/${facts.loader_version}/profile/json`,join(home,'fabric-profile.json'));
}else{
  const coordinate=loader==='forge'?`${minecraft}-${facts.loader_version}`:facts.loader_version;
  const url=loader==='forge'?`https://maven.minecraftforge.net/net/minecraftforge/forge/${coordinate}/forge-${coordinate}-installer.jar`:`https://maven.neoforged.net/releases/net/neoforged/neoforge/${coordinate}/neoforge-${coordinate}-installer.jar`;
  const installer=join(cache,loader+'-'+coordinate+'-installer.jar');
  const checksumResponse=await fetch(url+'.sha1',{signal:AbortSignal.timeout(60000)});assert(checksumResponse.ok);
  const checksum=(await checksumResponse.text()).trim().split(/\s/)[0];assert.match(checksum,/^[a-f0-9]{40}$/i);
  await download(url,installer,checksum);
  await writeFile(join(home,'launcher_profiles.json'),JSON.stringify({profiles:{},settings:{}}));
  console.log('Official installer Java '+installerJavaVersion+'; game Java '+facts.java_version);
  await run(installerJava,['-Djava.awt.headless=true','-jar',installer,'--installClient',home],home,join(root,'installer.log'));
  const profiles=(await readdir(join(home,'versions'))).filter(name=>name!==minecraft);
  assert.equal(profiles.length,1,'One official Loader profile');profile=JSON.parse(await readFile(join(home,'versions',profiles[0],profiles[0]+'.json'),'utf8'));
}
const libraries=new Map();
for(const library of [...vanilla.libraries,...profile.libraries])if(permitted(library.rules)){
  const parts=library.name.split(':');libraries.set([parts[0],parts[1],parts[3]??''].join(':'),library);
}
const classpath=[],nativeJars=[];
await parallel([...libraries.values()],async library=>{
  const parts=library.name.split(':');const maven=parts[0].replaceAll('.','/')+'/'+parts[1]+'/'+parts[2]+'/'+parts[1]+'-'+parts[2]+(parts[3]?'-'+parts[3]:'')+'.jar';
  const artifact=library.downloads?.artifact??{path:maven,url:(library.url??'https://libraries.minecraft.net/')+maven};
  const local=join(home,'libraries',artifact.path);await download(artifact.url,local,artifact.sha1);
  classpath.push(local);
  if(parts[3]?.startsWith('natives-windows'))nativeJars.push(local);
  if(library.natives?.windows){
    const native=library.downloads.classifiers[library.natives.windows.replace('${arch}','64')];
    const path=join(home,'libraries',native.path);await download(native.url,path,native.sha1);nativeJars.push(path);
  }
});
const natives=join(home,'natives');await mkdir(natives,{recursive:true});
for(const path of nativeJars)execFileSync(join(dirname(java),'jar.exe'),['xf',path],{cwd:natives});
const assets=join(cache,'assets'),index=vanilla.assetIndex;
const assetIndex=await json(index.url,join(assets,'indexes',index.id+'.json'),index.sha1,[join(gradle,'fabric-loom/assets/indexes',index.id+'.json')]);
await parallel([...new Map(Object.values(assetIndex.objects).map(entry=>[entry.hash,entry])).values()],async entry=>{
  const suffix=entry.hash.slice(0,2)+'/'+entry.hash;
  await download('https://resources.download.minecraft.net/'+suffix,join(assets,'objects',suffix),entry.hash,[join(gradle,'fabric-loom/assets/objects',suffix)]);
},24);
// The official Loader profile's ignoreList names version_name.jar, not the parent Minecraft id.
const installedGameJar=join(home,'versions',profile.id,profile.id+'.jar');
await mkdir(dirname(installedGameJar),{recursive:true});await copyFile(join(home,'versions',minecraft,minecraft+'.jar'),installedGameJar);
classpath.sort();classpath.push(installedGameJar);
for(const path of classpath)assert(path.startsWith(home+'\\')&&!path.includes('\\build\\')&&!path.includes('\\components\\')&&!path.includes('.gradle'),'Only official installed runtime classpath');
const name=`${product.mod_id}-${target}-${product.mod_version}.jar`,artifact=join(repo,'build/release',product.mod_version,name);
const artifactBytes=await readFile(artifact),artifactSha256=digest(artifactBytes);
await mkdir(join(game,'mods'));await writeFile(join(game,'mods',name),artifactBytes);
await writeFile(join(game,'options.txt'),'lang:en_us\nsoundCategory_master:0.0\nonboardAccessibility:false\nautoJump:false\n');
const substitutions={auth_player_name:'PackagedTest',version_name:profile.id,game_directory:game,assets_root:assets,assets_index_name:index.id,auth_uuid:'b726e6e91d5c43cf87909f8a57d435ee',auth_access_token:'0',clientid:'',auth_xuid:'0',user_type:'legacy',version_type:'release',natives_directory:natives,launcher_name:'Mine-Craft-Protocol-installed-smoke',launcher_version:'1',classpath:classpath.join(delimiter),library_directory:join(home,'libraries'),classpath_separator:delimiter};
const expand=value=>value.replace(/\$\{([^}]+)\}/g,(_,key)=>{assert(Object.hasOwn(substitutions,key),'Unknown official argument '+key);return substitutions[key];});
function argumentsFor(values){return values.flatMap(value=>typeof value==='string'?[expand(value)]:permitted(value.rules)?(Array.isArray(value.value)?value.value:[value.value]).map(expand):[]);}
const jvm=argumentsFor([...(vanilla.arguments?.jvm??[]),...(profile.arguments?.jvm??[])]);
const gameArgs=argumentsFor([...(vanilla.arguments?.game??[]),...(profile.arguments?.game??[])]);
const args=['-Xmx4G','-Xms512M','-Xlog:class+load=info:file=class-load.log','-Dminecraft.protocol.probe.port=0',...jvm,profile.mainClass,...gameArgs,'--width',String(width),'--height',String(height)];
const output=createWriteStream(join(root,'client-console.log'));
const child=spawn(java,args,{cwd:game,env:{...process.env,MCP_RUNTIME_SCOPES:'read,ui,input,control'},windowsHide:true,stdio:['ignore','pipe','pipe']});
child.stdout.pipe(output,{end:false});child.stderr.pipe(output,{end:false});
let exited=false,exitCode;const exit=new Promise((yes,no)=>{child.once('error',no);child.once('exit',code=>{exited=true;exitCode=code;output.end();yes(code);});});
const started=Date.now();let connection,lease;
async function acquire(){
  const status=await connection.fetch('/v0/control/mode');assert(status.ok,'Fresh control state before automatic acquisition');
  const request=automaticTakeoverRequest(await status.json(),{ttlMs:15000});
  const response=await connection.fetch('/v0/control/acquire',{method:'POST',headers:{'content-type':'application/json'},body:JSON.stringify(request)});
  assert(response.ok,JSON.stringify(await response.clone().json()));return response.json();
}
const report={target,status:'FAIL',testedAt:new Date().toISOString(),runtime:'installed_loader_release_jar',loaderVersion:facts.loader_version,minecraft,javaVersion:facts.java_version,...(loader==='fabric'?{}:{installerJavaVersion}),artifactSha256,sourceFingerprint,sourceHashes,toolHashes,freshGameDirectory:true,externalModCount:1,developmentClasspath:false,maximumHeapBytes:4294967296,persistentWriteCalls:0};
try{
  let title;
  while(Date.now()-started<240000){
    assert(!exited,'Installed client exited before readiness: '+exitCode);
    try{
      connection??=await connectRuntime(game,target);
      const response=await connection.fetch('/v0/ui/tree');assert(response.ok);const tree=await response.json();
      if(tree.screenClass.endsWith('AccessibilityOnboardingScreen')){
        const walk=node=>[node,...(node.children??[]).flatMap(walk)];const button=walk(tree).find(n=>n.translationKey==='gui.continue');
        if(button){lease=(await acquire()).leaseId;await connection.fetch('/v0/ui/action',{method:'POST',body:JSON.stringify({selector:{nodeId:button.nodeId},action:'click'}),headers:{'content-type':'application/json','X-MCP-Control-Lease':lease}});await connection.fetch('/v0/control/release',{method:'POST',headers:{'X-MCP-Control-Lease':lease}});lease=undefined;}
      }
      if(tree.screenClass.endsWith('TitleScreen')&&!tree.overlayIdentity){title=tree;break;}
    }catch(error){report.lastReadinessError=error.message;}
    await new Promise(yes=>setTimeout(yes,300));
  }
  assert(title,'Installed title readiness timeout');
  assert.equal(title.windowWidth,width,'Installed client width');assert.equal(title.windowHeight,height,'Installed client height');
  report.instanceProfile='automatic';report.windowWidth=title.windowWidth;report.windowHeight=title.windowHeight;
  const response=await connection.fetch('/v0/player?category=USER');assert(response.ok,'USER READ request');const player=await response.json();assert.equal(player.dataCategory,'USER');
  report.endpointVerified=true;report.handshake=true;report.readPassed=true;report.titleReady=true;report.readSample=player;
  const providerResponse=await connection.fetch('/v0/observe/deep/capabilities');assert(providerResponse.ok,'Installed Provider SPI catalog');
  const providerCapabilities=await providerResponse.json();
  report.providerCatalog=providerCapabilities.providers.filter(p=>['safe','client-thread','render-thread','server-thread'].some(id=>p.providerId===`${product.mod_id}:${id}`));
  assert.equal(report.providerCatalog.length,4,'Registered Provider V2 descriptors must resolve from the final product');
  for(const p of report.providerCatalog){assert.equal(typeof p.snapshotSchema,'string');assert.equal(typeof p.querySchema,'string');assert(['server_thread','client_thread','render_thread'].includes(p.threadAffinity));}
  const classes=await readFile(join(game,'class-load.log'),'utf8');
  report.nettyHttpOrigin=classes.split(/\r?\n/).find(l=>l.includes('io.netty.handler.codec.http.HttpMessage source:'));
  report.transportOrigin=classes.split(/\r?\n/).find(l=>l.includes('io.github.recrivenvi.minecraftprotocol.core.ProbeTransport source:'));
  assert(report.nettyHttpOrigin&&report.transportOrigin,'Real product and HTTP classes must be loaded');
  report.providerSpiOrigins=['AgentDataProviderV2','MinecraftProtocolProvidersV2','ProviderSchemaRegistry'].map(name=>classes.split(/\r?\n/).find(line=>line.includes(`${product.mod_group}.probe.api.${name} source:`)));
  assert(report.providerSpiOrigins.every(Boolean),'Actual public SPI classes must be loaded in the independent Loader');
  report.providerRegistryResolved=true;
  const walk=node=>[node,...(node.children??[]).flatMap(walk)];const quit=walk(title).find(n=>n.translationKey==='menu.quit');assert(quit);
  lease=(await acquire()).leaseId;
  try{
    const click=await connection.fetch('/v0/ui/action',{method:'POST',headers:{'content-type':'application/json','X-MCP-Control-Lease':lease},body:JSON.stringify({selector:{nodeId:quit.nodeId},action:'click'})});
    report.quitHttpStatus=click.status;
    if(!click.ok){
      report.quitError=await click.json();
      assert.equal(click.status,503,'Only an explicit Runtime shutdown, never an internal 500, is an explained cancellation');
      assert.equal(report.quitError.error,'RUNTIME_SHUTTING_DOWN');
      assert.equal(report.quitError.executionOutcome,'not_confirmed');assert.equal(report.quitError.retrySafe,false);
    }
  }catch(error){
    if(error.name==='AssertionError')throw error;
    report.quitTransportError=error.message;
  }
  let exitTimer;const code=await Promise.race([exit,new Promise((_,no)=>{exitTimer=setTimeout(()=>no(Error('Installed clean exit timeout')),60000);})]).finally(()=>clearTimeout(exitTimer));assert.equal(code,0);
  report.cleanShutdown=true;report.exitCode=code;assert(!await exists(join(game,'minecraft-protocol/endpoint.json')),'Endpoint removed');
  const log=await readFile(join(game,'logs/latest.log'),'utf8');assert(/Stopping!/.test(log),'Vanilla normal stopping log');
  assert.equal((await readdir(join(game,'mods'))).length,1,'Exactly one installed product JAR');
  assert(!await exists(join(game,'crash-reports'))||!(await readdir(join(game,'crash-reports'))).length,'No crash reports');
  report.noCrashReports=true;
  assert.equal(digest(await readFile(artifact)),artifactSha256,'Artifact unchanged during run');
  for(const [path,hash]of Object.entries(toolHashes))assert.equal(sha256(await readFile(join(repo,path))),hash,'Installed validation tool changed during run: '+path);
  report.sourceChangedDuringRun=sha256(JSON.stringify(await candidateSource(repo,targets)))!==sourceFingerprint;
  assert.equal(report.sourceChangedDuringRun,false,'Candidate source unchanged');report.status='PASS';
}catch(error){report.error=error.stack;throw error;}
finally{
  if(!exited){child.kill();report.cleanShutdown=false;await exit.catch(()=>{});}
  await mkdir(resolve(repo,'validations/integration-packaged_runtime/result'),{recursive:true});
  await writeFile(resolve(repo,'validations/integration-packaged_runtime/result',target+'.json'),JSON.stringify(report,null,2)+'\n');
}
console.log('PASS installed Loader / one final JAR / endpoint / USER READ / clean exit: '+target);
}

const invoked=process.argv[1]&&resolve(process.argv[1]),modulePath=fileURLToPath(import.meta.url);
if(invoked&&(process.platform==='win32'?invoked.toLowerCase()===modulePath.toLowerCase():invoked===modulePath))await main();
