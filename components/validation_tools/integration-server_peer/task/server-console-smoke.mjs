import assert from 'node:assert/strict';
import {checkServerWorldDebug} from './server-world-debug-checks.mjs';
import {prepareServerWorld} from './server-world-fixture.mjs';
import {spawn} from 'node:child_process';
import {createHash} from 'node:crypto';
import {createWriteStream} from 'node:fs';
import {readFile,writeFile,mkdir,readdir,cp,copyFile,access} from 'node:fs/promises';
import {createServer} from 'node:net';
import {join,resolve} from 'node:path';
import {homedir} from 'node:os';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import {findJava} from '../../integration-packaged_runtime/task/installed-launcher.mjs';

const repo=resolve(import.meta.dirname,'../../../..');
const target=process.argv.find(arg=>arg.startsWith('--target='))?.slice(9);
const worldDebug=process.argv.includes('--world-debug');
assert(['26.3-neoforge','26.1.2-neoforge','1.21.1-neoforge','1.20.1-forge','26.3-fabric'].includes(target),'Explicit registered Target required');
const properties=async path=>Object.fromEntries((await readFile(path,'utf8')).split(/\r?\n/).filter(line=>line.includes('=')&&!line.startsWith('#')).map(line=>{const i=line.indexOf('=');return[line.slice(0,i),line.slice(i+1)];}));
const facts=await properties(join(repo,'versions',target,'target.properties')), product=await properties(join(repo,'gradle.properties'));
const base=join(repo,'validations/integration-server_peer'), game=join(base,'instance',target,'console-server');
const exists=path=>access(path).then(()=>true,()=>false), sleep=ms=>new Promise(done=>setTimeout(done,ms).unref());
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
await mkdir(game,{recursive:true});await mkdir(join(base,'result'),{recursive:true});
const ownerFile=join(game,'console-owner.json');
function alive(pid){if(!pid)return false;assert(Number.isSafeInteger(pid)&&pid>0,'Invalid owned process identity');try{process.kill(pid,0);return true;}catch(error){if(error.code==='ESRCH')return false;throw error;}}
if(await exists(ownerFile)){const owner=JSON.parse(await readFile(ownerFile,'utf8'));assert.equal(owner.owner,'server-console-smoke');assert.equal(owner.target,target);assert(!alive(owner.pid)&&!alive(owner.serverPid),'Owned server smoke or its surviving server is still running; directory left untouched');}
const endpointFile=join(game,'minecraft-protocol/endpoint.json');
if(await exists(endpointFile)){const old=JSON.parse(await readFile(endpointFile,'utf8'));assert.equal(old.target,target);assert(!alive(old.pid),'A Runtime still owns this directory; no configuration or JAR will be overwritten');}
await writeFile(ownerFile,JSON.stringify({owner:'server-console-smoke',target,pid:process.pid}));
assert(/^eula\s*=\s*true\s*$/m.test(await readFile(join(repo,'local.toml'),'utf8')),'Explicit preexisting EULA consent required');
const clientCache=join(repo,'validations/integration-packaged_runtime/instance/installed',target,'installation');
if(!await exists(join(game,'libraries')))await cp(join(clientCache,'libraries'),join(game,'libraries'),{recursive:true});
const loader=target.slice(target.lastIndexOf('-')+1), minecraft=target.slice(0,target.lastIndexOf('-'));
const coordinate=loader==='forge'?minecraft+'-'+facts.loader_version:facts.loader_version;
const argsFile=join('libraries',loader==='forge'?'net/minecraftforge/forge':'net/neoforged/neoforge',coordinate,'win_args.txt');
const java=await findJava(facts.java_version);
async function runInstaller(){
  const installer=join(repo,'validations/integration-packaged_runtime/instance/downloads',loader+'-'+coordinate+'-installer.jar');
  assert(await exists(installer),'Run the installed-runtime preparation first: official Loader installer must be cached');
  const output=createWriteStream(join(game,'installer.log'));
  const child=spawn(await findJava('25'),['-Djava.awt.headless=true','-jar',installer,'--installServer',game],{cwd:game,windowsHide:true,stdio:['ignore','pipe','pipe']});
  child.stdout.pipe(output,{end:false});child.stderr.pipe(output,{end:false});
  const code=await new Promise((yes,no)=>{const deadline=setTimeout(()=>{child.kill();no(Error('Owned installer exceeded 180 seconds'));},180000);child.once('error',no);child.once('exit',code=>{clearTimeout(deadline);yes(code);});});output.end();assert.equal(code,0,'Official server install failed; inspect installer.log');
}
let launchArgs;
if(loader==='fabric'){
  const profile=JSON.parse(await readFile(join(clientCache,'fabric-profile.json'),'utf8'));
  const vanilla=JSON.parse(await readFile(join(clientCache,'versions',minecraft,minecraft+'.json'),'utf8'));
  const bundled=join(game,'server.jar');
  if(!await exists(bundled))await copyFile(join(homedir(),'.gradle/caches/neoformruntime/artifacts',`minecraft_${minecraft}_server.jar`),bundled);
  assert.equal(createHash('sha1').update(await readFile(bundled)).digest('hex'),vanilla.downloads.server.sha1,'Official bundled server hash');
  const cp=profile.libraries.map(lib=>{const [group,name,version]=lib.name.split(':');return join(game,'libraries',group.replaceAll('.','/'),name,version,`${name}-${version}.jar`);});
  for(const file of cp)assert(await exists(file),'Official Fabric profile library missing: '+file);
  launchArgs=['-Dfabric.gameJarPath='+bundled,'-cp',cp.join(';'),'net.fabricmc.loader.impl.launch.server.FabricServerLauncher','nogui'];
}else{
  if(!await exists(join(game,argsFile)))await runInstaller();
  launchArgs=['@'+argsFile,'nogui'];
}
const jarName=`${product.mod_id}-${target}-${product.mod_version}.jar`,jar=join(repo,'versions',target,'build/libs',jarName);
await mkdir(join(game,'mods'),{recursive:true});assert((await readdir(join(game,'mods'))).every(name=>name===jarName),'Only this product JAR is permitted');
await copyFile(jar,join(game,'mods',jarName));
await writeFile(join(game,'eula.txt'),'eula=true\n');
const reservation=createServer();await new Promise(done=>reservation.listen(0,'127.0.0.1',done));const gamePort=reservation.address().port;await new Promise(done=>reservation.close(done));
await writeFile(join(game,'server.properties'),`server-ip=127.0.0.1\nserver-port=${gamePort}\nlevel-name=Console-Smoke\nonline-mode=false\nwhite-list=false\nmax-players=1\nview-distance=2\nsimulation-distance=2\npause-when-empty-seconds=-1\n`);
const report={target,jarSha256:digest(await readFile(jar)),startedAt:new Date().toISOString(),scope:worldDebug?'single_final_jar_dedicated_world_debug_and_console_zero_players_no_client':'single_final_jar_dedicated_console_zero_players_no_client',checks:{},persistentWriteCalls:0,persistentWriteBasis:'This runner invokes no Persistent Storage write API; ordinary native server saving is separate.'};
report.fixture=await prepareServerWorld({repo,game,world:'Console-Smoke',target,facts:join(repo,'versions',target,'target.properties'),
  start:async()=>{
    const output=createWriteStream(join(game,'fixture-preparation.log'));
    const native=spawn(java,['-Xms512M','-Xmx2G','-Dfile.encoding=UTF-8','-Dterminal.jline=false',...launchArgs],{cwd:game,windowsHide:true,stdio:['pipe','pipe','pipe']});
    await writeFile(ownerFile,JSON.stringify({owner:'server-console-smoke',target,pid:process.pid,serverPid:native.pid}));
    native.stdout.pipe(output,{end:false});native.stderr.pipe(output,{end:false});
    const ready=new Promise((done,fail)=>{
      let text='';const inspect=bytes=>{text=(text+bytes).slice(-65536);if(/Done \(/.test(text))done();};
      native.stdout.on('data',inspect);native.stderr.on('data',inspect);native.once('error',fail);native.once('exit',code=>fail(Error('Fixture preparation exited '+code)));
    });
    const exit=new Promise(done=>native.once('exit',done));
    let timer;
    try{await Promise.race([ready,new Promise((_,fail)=>{timer=setTimeout(()=>fail(Error('Fixture preparation deadline')),180000);})]);}
    catch(error){native.kill();await exit;output.end();throw error;}finally{clearTimeout(timer);}
    return {native,exit,output};
  },stop:async({native,exit,output})=>{
    native.stdin.write('stop\n');let timer;
    try{assert.equal(await Promise.race([exit,new Promise((_,fail)=>{timer=setTimeout(()=>fail(Error('Fixture stop deadline')),45000);})]),0);}
    catch(error){native.kill();await exit;throw error;}finally{clearTimeout(timer);output.end();}
  }});
const log=createWriteStream(join(game,'console-smoke.log'));
const child=spawn(java,['-Xms512M','-Xmx2G','-Dfile.encoding=UTF-8','-Dterminal.jline=false','-Dminecraft.protocol.scopes=read,event,control,command,diagnostics,server_console'+(worldDebug?',storage.read,server_peer,debug,debug.world.read,debug.write,debug.world,debug.entity,debug.block_entity,debug.player,debug.menu,debug.provider':''),...launchArgs],{cwd:game,windowsHide:true,stdio:['pipe','pipe','pipe']});
report.pid=child.pid;report.gamePort=gamePort;child.stdout.pipe(log,{end:false});child.stderr.pipe(log,{end:false});
await writeFile(ownerFile,JSON.stringify({owner:'server-console-smoke',target,pid:process.pid,serverPid:child.pid}));
let exited=false,exitCode,mcp,mcpTransport;const exit=new Promise(done=>child.once('exit',code=>{exited=true;exitCode=code;done(code);}));
async function until(read,valid,label,timeout=15000){const end=Date.now()+timeout;let value;do{assert(!exited,'Server exited before '+label);value=await read();if(valid(value))return value;await sleep(100);}while(Date.now()<end);throw Error(label+': '+JSON.stringify(value));}
let endpoint,token;
async function call(method,path,body,expected=200,headers={}){
  const response=await fetch(endpoint.baseUrl+path,{method,headers:{Authorization:'Bearer '+token,'X-MCP-Runtime-Instance':endpoint.runtimeInstanceId,...(body?{'Content-Type':'application/json'}:{}),...headers},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(10000)});
  const value=await response.json();assert.equal(response.status,expected,JSON.stringify(value));return value;
}
async function nativeCommand(command){
  const reply=await mcp.callTool({name:'minecraft_server_console',arguments:{action:'execute',command}});assert(!reply.isError,JSON.stringify(reply));
  const operation=reply.structuredContent.data;
  const terminal=await until(()=>call('GET','/v0/operations/'+operation.operationId),value=>['completed','failed','cancelled'].includes(value.state),'console Operation terminal');assert.equal(terminal.state,'completed',JSON.stringify(terminal));return terminal.result;
}
try{
  endpoint=await until(async()=>await exists(join(game,'minecraft-protocol/endpoint.json'))?JSON.parse(await readFile(join(game,'minecraft-protocol/endpoint.json'),'utf8')):null,value=>value?.pid===child.pid&&value?.target===target,'dedicated endpoint for this owned process',120000);
  assert.equal(endpoint.deploymentRole,'DEDICATED_SERVER');token=(await readFile(join(game,'minecraft-protocol/token'),'utf8')).trim();report.runtimeInstanceId=endpoint.runtimeInstanceId;report.runtimePort=endpoint.port;
  report.session=await call('GET','/v0/session');assert.equal(report.session.deploymentRole,'DEDICATED_SERVER');assert.equal(report.session.inputSurfaceAvailable,false);
  await call('GET','/v0/session',undefined,401,{Authorization:'Bearer wrong'});await call('GET','/v0/session',undefined,409,{'X-MCP-Runtime-Instance':'00000000-0000-0000-0000-000000000000'});report.checks.authAndInstancePin=true;
  const mode=await call('GET','/v0/control/mode');assert(mode.availableTransitions.every(item=>item.mode!=='TAKEOVER'));
  assert.equal((await call('POST','/v0/control/acquire',{},409)).error,'TAKEOVER_NOT_APPLICABLE');
  const denied=await call('POST','/v0/server/console/commands',{command:'say READ_DENIED'},409);assert.equal(denied.error,'OPERATE_REQUIRED');
  await call('GET','/v0/capture',undefined,409);report.checks.readCannotWriteOrTakeover=true;
  await call('POST','/v0/control/mode',{mode:'OPERATE',expectedModeVersion:mode.modeVersion});
  const require=createRequire(join(repo,'components/companion/package.json'));
  const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
  const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
  mcpTransport=new StdioClientTransport({command:process.execPath,args:[join(repo,'components/companion/dist/index.js')],env:{...process.env,MCP_MINECRAFT_INSTANCE_DIR:game,MCP_MINECRAFT_ROLE:'DEDICATED_SERVER',MCP_MINECRAFT_TARGET:target}});
  mcp=new Client({name:'server-console-installed-smoke',version:'1.0.0'});await mcp.connect(mcpTransport);
  report.list=await nativeCommand('list');assert(report.list.feedback.some(text=>text.includes('0')));assert.equal(report.list.actor,'server_operator');assert.equal(report.list.worldEffectConfirmed,false);
  report.command=await nativeCommand('say SERVER_CONSOLE_中文');assert.equal(report.command.nativeResult.success,true);
  report.setTime=await nativeCommand('time set 1234');assert.equal(report.setTime.nativeResult.success,true);
  report.timeReadback=await nativeCommand(target.startsWith('26.')?'time query time':'time query daytime');
  assert.equal(report.timeReadback.nativeResult.success,true);assert(report.timeReadback.nativeResult.value>=1234&&report.timeReadback.nativeResult.value<1600,'Independent native clock readback');
  report.checks.independentNativeTimeReadback=true;
  report.syntax=await nativeCommand('time console_smoke_invalid');assert(report.syntax.feedback.length>0);assert.equal(report.syntax.resultCallbackReceived,false);
  report.checks.realCompanionConsoleNoLease=true;
  const stopDenied=await call('POST','/v0/server/console/commands',{command:'stop'},409);assert.equal(stopDenied.error,'CONSOLE_COMMAND_UNSUPPORTED');report.checks.controlPlaneNotImplicitlyOpened=true;
  child.stdin.write('say NATIVE_CONSOLE_COEXISTS\n');
  const page=await until(()=>call('GET','/v0/server/console?after=0&limit=128'),value=>value.records.some(item=>item.text.includes('NATIVE_CONSOLE_COEXISTS')),'native console coexistence');
  assert.equal(page.storageAccessed,false);assert(!page.records.some(item=>item.text.includes('READ_DENIED')));report.console=page;report.checks.liveOutputAndNativeConsoleCoexist=true;
  const readReply=await mcp.callTool({name:'minecraft_server_console',arguments:{action:'read',after:0,limit:64}});assert(!readReply.isError);assert.equal(readReply.structuredContent.data.authority,'SERVER_CONSOLE');
  report.providerDescriptors=await call('GET','/v0/providers');
  const consoleDescriptor=report.providerDescriptors.providers.find(item=>item.id==='minecraft:server/console');
  assert.deepEqual(consoleDescriptor.readCategories,['USER','DEBUG']);assert.equal(consoleDescriptor.defaultReadCategory,'USER');assert.equal(consoleDescriptor.explicitCategoryRequired,false);
  const worldDescriptor=report.providerDescriptors.providers.find(item=>item.id==='minecraft:server/world/deep');
  assert.deepEqual(worldDescriptor.readCategories,['DEBUG']);assert.equal(Object.hasOwn(worldDescriptor,'defaultReadCategory'),false);assert.equal(worldDescriptor.explicitCategoryRequired,true);
  const consoleCondition=await mcp.callTool({name:'minecraft_assert',arguments:{condition:{type:'provider',category:'USER',providerId:'minecraft:server/console',expected:{data:{authority:'SERVER_CONSOLE',actor:'server_operator'}}}}});
  assert(!consoleCondition.isError,JSON.stringify(consoleCondition));assert.equal(consoleCondition.structuredContent.data.passed,true);report.checks.consoleProviderCategoriesAndMcpCondition=true;
  const suggestions=await mcp.callTool({name:'minecraft_server_console',arguments:{action:'complete',input:'li',cursor:2}});
  assert(!suggestions.isError,JSON.stringify(suggestions));assert.equal(suggestions.structuredContent.data.commandExecuted,false);
  assert(suggestions.structuredContent.data.suggestions.some(item=>item.text==='list'));report.checks.nativeCompletionWithoutExecution=true;
  const recording=await call('POST','/v0/recordings',{durationMs:5000,intervalMs:100,maxSamples:8});
  const recorded=await until(()=>call('GET','/v0/recordings/'+recording.recordingId),value=>value.writtenStates>=2,'headless structured recording');
  assert.equal(recorded.writtenFrames,0);assert.equal(recorded.deploymentRole,'DEDICATED_SERVER');
  await call('DELETE','/v0/recordings/'+recording.recordingId);report.recording=recorded;
  if(worldDebug)report.worldDebug=await checkServerWorldDebug({call,mcp,child,until});
  await mcp.close();mcp=undefined;await mcpTransport.close();
  child.stdin.write('stop\n');assert.equal(await Promise.race([exit,sleep(30000).then(()=>{throw Error('normal stop deadline');})]),0);
  assert(!await exists(join(game,'minecraft-protocol/endpoint.json')));report.checks.normalShutdownRemovesEndpoint=true;report.status='PASS';
  console.log(target+(worldDebug?' dedicated world DEBUG + console PASS':' dedicated console PASS'));
}catch(error){report.status='FAIL';report.error=String(error.stack??error);console.error(error);process.exitCode=1;}
finally{
  if(mcp)await mcp.close().catch(()=>{});if(mcpTransport)await mcpTransport.close().catch(()=>{});
  if(!exited){child.stdin.write('stop\n');await Promise.race([exit,sleep(30000)]);if(!exited)child.kill();}
  report.exitCode=exitCode;report.finishedAt=new Date().toISOString();await writeFile(join(base,'result',target+(worldDebug?'-server-debug':'-console')+'.json'),JSON.stringify(report,null,2)+'\n');await writeFile(ownerFile,JSON.stringify({owner:'server-console-smoke',target,pid:0,serverPid:exited?0:child.pid}));log.end();
}
