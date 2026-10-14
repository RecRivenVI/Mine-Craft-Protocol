import assert from 'node:assert/strict';
import { readFile, writeFile, readdir, mkdir } from 'node:fs/promises';
import { resolve, relative } from 'node:path';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';

const repo = resolve(import.meta.dirname, '../../../..');
const args = Object.fromEntries(process.argv.slice(2).map(arg => arg.replace(/^--/, '').split('=')));
const target = args.target;
assert(['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric'].includes(target));
const validation = args.validation ?? 'conformance-world_read';
assert(['conformance-world_read','migration-runtime_core'].includes(validation));
const instance = resolve(repo, 'validations',validation,'instance', target, 'client');
const token = (await readFile(resolve(instance, 'minecraft-protocol/token'), 'utf8')).trim();
const base = 'http://127.0.0.1:'+(validation==='migration-runtime_core'?28598:28597);
let lease, renew, failure;
const checks = [];
async function api(method, path, body, expectedError) {
  const response = await fetch(base + path, { method, headers: { authorization:'Bearer '+token, ...(lease ? {'x-mcp-control-lease':lease}:{}), ...(body ? {'content-type':'application/json'}:{}) }, ...(body ? {body:JSON.stringify(body)}:{}), signal:AbortSignal.timeout(25000) });
  const value = await response.json();
  if (expectedError) { assert.equal(value.error, expectedError, JSON.stringify(value)); assert(!response.ok); return value; }
  assert(response.ok, JSON.stringify(value)); return value;
}
const delay = () => new Promise(resolveWait => setTimeout(resolveWait, 180));
const nodes = tree => (tree.children ?? []).flatMap(node => [node, ...nodes(node)]);
async function wait(predicate, description, budget=90000) {
  const end=Date.now()+budget;
  do { const tree=await api('GET','/v0/ui/tree'); if (!tree.overlayIdentity && predicate(tree)) return tree; await delay(); } while(Date.now()<end);
  throw new Error('UI timeout: '+description);
}
async function clickNode(node, modifiers=0) { return api('POST','/v0/ui/action',{selector:{nodeId:node.nodeId},action:'click',modifiers}); }
async function click(key, label) {
  const tree=await api('GET','/v0/ui/tree');
  const candidates=nodes(tree).filter(node=>node.translationKey===key&&node.active!==false&&node.visible!==false);
  const node=candidates.length===1?candidates[0]:nodes(tree).find(node=>node.label===label&&node.active!==false&&node.visible!==false);
  assert(node,'Missing semantic button: '+key); return clickNode(node);
}
async function key(code) {
  const operation=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:code,holdMs:40}],timeoutMs:12000});
  assert.equal((await api('POST','/v0/operations/'+operation.operationId+'/wait',{timeoutMs:12000})).state,'completed');
}
async function acquire() {
  lease=(await api('POST','/v0/control/acquire',{ttlMs:60000})).leaseId;
  renew=setInterval(()=>{void api('POST','/v0/control/renew',{ttlMs:60000}).catch(()=>{});},20000);
}
async function release() { clearInterval(renew); await api('POST','/v0/control/release'); lease=undefined; }
async function mode(value) { const current=await api('GET','/v0/control/mode'); await api('POST','/v0/control/mode',{mode:value,expectedModeVersion:current.modeVersion}); }
async function shutdown() {
  if (!lease) await acquire();
  for(let i=0;i<10;i++) {
    const tree=await wait(()=>true,'shutdown navigation');
    if(tree.screenClass.endsWith('TitleScreen')) {
      await click('menu.quit','Quit Game').catch(()=>{}); clearInterval(renew); lease=undefined; return;
    }
    const quit=nodes(tree).find(node=>['menu.disconnect','menu.returnToMenu'].includes(node.translationKey));
    if(quit) await clickNode(quit); else await key(256);
  }
  throw new Error('Unable to request normal shutdown');
}
if(args['shutdown-only']==='true'){await shutdown();process.exit(0);}
async function sources() {
  const hashes={};
  for(const dir of [resolve(repo,'versions',target,'src'),resolve(repo,'components/runtime_safety/src/main'),resolve(repo,'components/runtime_core/src/main')]) {
    for(const entry of await readdir(dir,{recursive:true,withFileTypes:true})) if(entry.isFile()&&/\.(java|json)$/.test(entry.name)) {
      const path=resolve(entry.parentPath,entry.name);hashes[relative(repo,path).replaceAll('\\','/')]=createHash('sha256').update(await readFile(path)).digest('hex');
    }
  }
  for(const path of ['components/protocol_schema/src/main/openapi/minecraft-control-v0.json',relative(repo,import.meta.filename),'components/validation_tools/conformance-world_read/task/Invoke-WorldReadSmoke.ps1','validations/conformance-world_read/validation.toml','gradle.properties','settings.gradle.kts','gradle/libs.versions.toml',`versions/${target}/build.gradle.kts`,`versions/${target}/target.properties`]) hashes[path]=createHash('sha256').update(await readFile(resolve(repo,path))).digest('hex');
  return hashes;
}
const before=await sources();
async function check(name, run) {await run();checks.push({name,status:'PASS'});console.log('PASS: '+name);}
try {
  assert.equal((await api('GET','/v0/session')).target,target);
  await check('capability truth and title USER precision',async()=>{
    const capability=await api('GET','/v0/capabilities');assert.equal(capability.worldContract.implementationStage,'basic_read_classification');assert.equal(capability.worldContract.consumerIsolationComplete,false);assert.equal(capability.worldContract.milestoneComplete,false);
    const player=await api('GET','/v0/player');assert.equal(player.dataCategory,'USER');assert.equal(player.domain,'WORLD');for(const field of ['x','y','z','yaw','pitch','health','uuid','serverTick'])assert(!(field in player));assert.equal(player.presentation.length,0);
  });
  await check('scene/server USER and invalid category fail before internal capture',async()=>{
    for(const path of ['/v0/server/player','/v0/world/block?x=0&y=80&z=0','/v0/server/world/block?x=0&y=80&z=0','/v0/world/entities','/v0/server/world/entities'])await api('GET',path,undefined,'WORLD_USER_PERCEPTION_UNAVAILABLE');
    await api('GET','/v0/player?category=CLIENT_KNOWN',undefined,'INVALID_WORLD_CATEGORY');
  });
  await check('READ debug independent of write Arm and Lease',async()=>{
    const player=await api('GET','/v0/player?category=DEBUG');assert.equal(player.dataCategory,'DEBUG');assert.equal(player.perceptionFiltered,false);assert.equal(player.available,false);assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
  });
  await check('OPERATE rejects USER but accepts diagnostic read without Lease',async()=>{
    await mode('OPERATE');await api('GET','/v0/player',undefined,'WORLD_CATEGORY_MODE_MISMATCH');assert.equal((await api('GET','/v0/player?category=DEBUG')).dataCategory,'DEBUG');await mode('READ');
  });
  await acquire();
  await check('TAKEOVER rejects diagnostic read',()=>api('GET','/v0/player?category=DEBUG',undefined,'WORLD_CATEGORY_MODE_MISMATCH'));
  await check('existing semantic GUI enters dedicated creative flat world',async()=>{
    await click('menu.singleplayer','Singleplayer');
    let tree=await wait(tree=>/SelectWorldScreen$|CreateWorldScreen$/.test(tree.screenClass),'select world');
    if(tree.screenClass.endsWith('SelectWorldScreen')) {
      const world=nodes(tree).find(node=>node.worldName==='World-Read-Alpha');
      if(world){await clickNode(world);await click('selectWorld.select','Play Selected World');await wait(tree=>!tree.screenClass,'enter world',150000);return;}
      await click('selectWorld.create','Create New World');
    }
    await wait(tree=>tree.screenClass.endsWith('CreateWorldScreen'),'create world');
    await api('POST','/v0/ui/action',{selector:{role:'text_field',nth:0},action:'type_text',text:'World-Read-Alpha'});
    for(let i=0;i<4;i++){tree=await api('GET','/v0/ui/tree');const button=nodes(tree).find(node=>node.label?.startsWith('Game Mode:'));assert(button);if(button.label.includes('Creative'))break;await clickNode(button);}
    tree=await api('GET','/v0/ui/tree');const worldTab=nodes(tree).find(node=>node.role==='tab'&&node.label==='World');if(worldTab)await clickNode(worldTab);
    for(let i=0;i<12;i++){tree=await api('GET','/v0/ui/tree');const type=nodes(tree).find(node=>node.label?.startsWith('World Type:'));if(!type||/Superflat|Flat/.test(type.label))break;await clickNode(type);}
    tree=await api('GET','/v0/ui/tree');const game=nodes(tree).find(node=>node.role==='tab'&&node.label==='Game');if(game)await clickNode(game);
    await click('selectWorld.create','Create New World');await wait(tree=>!tree.screenClass,'generate world',150000);
  });
  await check('USER HUD through native creative inventory, no direct mutation',async()=>{
    await key(69);await wait(tree=>tree.screenClass.includes('Creative'),'creative inventory');
    assert.equal((await api('GET','/v0/player')).presentation.length,0);
    let inventory=await api('GET','/v0/ui/tree');
    const building=nodes(inventory).find(node=>node.role==='tab'&&(node.translationKey==='itemGroup.buildingBlocks'||node.label==='Building Blocks'));
    if(building&&!building.selected)await clickNode(building);
    inventory=await wait(tree=>nodes(tree).some(node=>node.role==='slot'&&node.item&&node.item!=='minecraft:air'&&node.visible!==false),'native creative visible item slot',12000);
    const slot=nodes(inventory).find(node=>node.role==='slot'&&node.item&&node.item!=='minecraft:air'&&node.visible!==false);assert(slot);
    const item=slot.item;
    await api('POST','/v0/input/key',{key:340,scanCode:0,action:1,modifiers:1});
    try{await clickNode(slot,1);}finally{await api('POST','/v0/input/key',{key:340,scanCode:0,action:0,modifiers:0});}
    // Creative catalog Shift-click takes a full carried stack; place it via a real empty Slot.
    inventory=await api('GET','/v0/ui/tree');
    const destination=nodes(inventory).find(node=>node.role==='slot'&&node.item==='minecraft:air'&&node.count===0&&node.visible!==false);
    assert(destination,'visible empty hotbar slot in the full creative catalog page');await clickNode(destination);
    await key(69);await wait(tree=>!tree.screenClass&&tree.presentation?.children?.some(node=>node.item===item&&node.visible),'visible hotbar '+item);
    const player=await api('GET','/v0/player');assert(player.presentation.some(node=>node.item===item));assert.equal(player.authority,'CLIENT_UI');assert.equal(player.storageAccessed,false);assert(!('health' in player));assert(!('x' in player));
    await release();
  });
  await check('DEBUG full precision and loaded/unloaded semantics remain separate',async()=>{
    const player=await api('GET','/v0/player?category=DEBUG');assert.equal(player.available,true);assert.equal(typeof player.x,'number');
    const block=await api('GET',`/v0/world/block?category=DEBUG&x=${Math.floor(player.x)}&y=${Math.floor(player.y)-1}&z=${Math.floor(player.z)}`);assert.equal(block.dataCategory,'DEBUG');
    const remote=await api('GET','/v0/world/block?category=DEBUG&x=1000000&y=64&z=1000000');assert.equal(remote.available,false);assert.equal(remote.chunkLoadRequested,false);
    for(const path of ['/v0/server/player?category=DEBUG','/v0/world/entities?category=DEBUG','/v0/server/world/entities?category=DEBUG'])assert.equal((await api('GET',path)).dataCategory,'DEBUG');
  });
  await check('READ input denied and held input zero',async()=>{
    await api('POST','/v0/input/key',{key:87,scanCode:0,action:1,modifiers:0},'TAKEOVER_REQUIRED');const state=await api('GET','/v0/input/state');assert.equal(state.pressedKeyCount,0);assert.equal(state.pressedButtonCount,0);
  });
  if(validation==='migration-runtime_core') await check('shared Provider Capture Recording and Event bridge',async()=>{
    const providers=await api('GET','/v0/providers');
    const echo=providers.providers.find(value=>value.id?.endsWith(':echo'));
    assert(echo,'registered product echo Provider');
    const reply=await api('POST','/v0/providers/read',{providerId:echo.id,query:{marker:'runtime_core'}});
    assert.equal(reply.data.echo.marker,'runtime_core');
    const capture=await fetch(base+'/v0/capture',{headers:{authorization:'Bearer '+token},signal:AbortSignal.timeout(25000)});
    assert(capture.ok);const bytes=new Uint8Array(await capture.arrayBuffer());
    assert(bytes.length<16*1024*1024);assert.deepEqual([...bytes.slice(0,8)],[137,80,78,71,13,10,26,10]);
    await api('GET','/v0/events/resync');
    const recording=await api('POST','/v0/recordings',{intervalMs:250,durationMs:1000,maxSamples:4,stateReads:[],contactSheet:{enabled:true}});
    let status;const end=Date.now()+30000;
    do{status=await api('GET','/v0/recordings/'+recording.recordingId);if(status.artifactReady)break;assert.notEqual(status.status,'failed',JSON.stringify(status));await delay();}while(Date.now()<end);
    assert(status.artifactReady,JSON.stringify(status));assert(status.writtenFrames>0,JSON.stringify(status));
    const artifact=await fetch(base+'/v0/recordings/'+recording.recordingId+'/artifact',{headers:{authorization:'Bearer '+token},signal:AbortSignal.timeout(25000)});
    assert(artifact.ok);const bundle=new Uint8Array(await artifact.arrayBuffer());assert(bundle.length<16*1024*1024);assert.equal(bundle[0],80);assert.equal(bundle[1],75);
  });
} catch(error) { failure=error;console.error(error);try{const tree=await api('GET','/v0/ui/tree');checks.push({name:'failure context',screen:tree.screenClass,presentation:tree.presentation,width:tree.width,height:tree.height,windowWidth:tree.windowWidth,windowHeight:tree.windowHeight,visibleNodes:nodes(tree).filter(node=>node.visible!==false).map(node=>({role:node.role,label:node.label,item:node.item,controlKey:node.controlKey,translationKey:node.translationKey}))});}catch{} }
finally {
  clearInterval(renew);
  try { await shutdown(); } catch(error) {failure??=error;console.error(error);}
  const after=await sources();const sourceChangedDuringRun=JSON.stringify(before)!==JSON.stringify(after);if(sourceChangedDuringRun)failure??=new Error('Sources changed during Runtime test');
  const directory=resolve(repo,'validations',validation,'result');await mkdir(directory,{recursive:true});
  await writeFile(resolve(directory,target+'.json'),JSON.stringify({status:failure?'FAIL':'PASS',target,testedAt:new Date().toISOString(),testedCommit:execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).trim(),dirty:true,evidence:'development_runtime_native',fixturePolicy:'reused_disposable_smoke_world_not_fixed_copy',arrange:'native_creative_gui_user_path',checks,sourceHashes:after,sourceChangedDuringRun,persistentWriteCalls:0,fullWorldMilestone:false,...(failure?{error:String(failure)}:{})},null,2)+'\n');
}
if(failure)process.exitCode=1;
