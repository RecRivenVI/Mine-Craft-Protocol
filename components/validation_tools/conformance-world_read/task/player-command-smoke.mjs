import assert from 'node:assert/strict';
import {readFile,readdir,writeFile,mkdir} from 'node:fs/promises';
import {resolve,relative} from 'node:path';
import {createHash,randomUUID} from 'node:crypto';
import {execFileSync} from 'node:child_process';
import {connectRuntime} from '../../runtime-connection.mjs';
import {playerCommandConsumer} from './world-read-consumers.mjs';

const repo=resolve(import.meta.dirname,'../../../..');
const args=Object.fromEntries(process.argv.slice(2).map(arg=>arg.replace(/^--/,'').split('=')));
const target=args.target,validation='conformance-world_read';
assert(['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric'].includes(target));
const instance=resolve(repo,'validations',validation,'instance',target,'client');
const fixtureInput=JSON.parse(await readFile(resolve(instance,'fixture-input.json'),'utf8'));
assert.equal(fixtureInput.policy,'immutable_baseline_restored');assert.equal(fixtureInput.target,target);
assert.equal(fixtureInput.baselineSha256,args['fixture-hash']);assert.equal(fixtureInput.restoredSha256,args['fixture-hash']);
const connection=await connectRuntime(instance,target);
const checks=[],actionSamples=[],consumerChecks=[];
let lease,failure,shutdownResponse='not_requested';
async function sources(){
  const files=[];
  for(const root of [`versions/${target}/src`,'components/runtime_core/src/main','components/runtime_safety/src/main','components/companion/src'])
    for(const entry of await readdir(resolve(repo,root),{recursive:true,withFileTypes:true}))if(entry.isFile()&&/\.(java|json|ts)$/.test(entry.name))files.push(relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/'));
  for(const entry of await readdir(import.meta.dirname,{withFileTypes:true}))if(entry.isFile()&&/\.(mjs|ps1)$/.test(entry.name))files.push(relative(repo,resolve(import.meta.dirname,entry.name)).replaceAll('\\','/'));
  files.push('components/protocol_schema/src/main/openapi/minecraft-control-v0.json','components/validation_tools/InstanceProfiles.ps1','components/validation_tools/Prepare-InstanceProfile.ps1','components/validation_tools/instance-profiles.gradle','build.gradle.kts','components/validation_tools/RuntimeConnection.ps1','components/validation_tools/runtime-connection.mjs',`validations/${validation}/validation.toml`,'gradle.properties','settings.gradle.kts','gradle/libs.versions.toml',`versions/${target}/build.gradle.kts`,`versions/${target}/target.properties`);
  const hashes={};for(const file of [...new Set(files)].sort())hashes[file]=createHash('sha256').update(await readFile(resolve(repo,file))).digest('hex');return hashes;
}
const before=await sources();
async function api(method,path,body,expected){
  const response=await connection.fetch(path,{method,headers:{...(lease?{'x-mcp-control-lease':lease}:{}),...(body?{'content-type':'application/json'}:{})},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(30000)});
  const value=await response.json();
  if(expected){assert(!response.ok,JSON.stringify(value));assert.equal(value.error,expected,JSON.stringify(value));}
  else if(!response.ok)throw Object.assign(new Error(JSON.stringify(value)),{code:value.error});
  return value;
}
const nodes=t=>(t.children??[]).flatMap(n=>[n,...nodes(n)]);
const delay=ms=>new Promise(done=>setTimeout(done,ms));
async function wait(predicate,label,ms=10000){const end=Date.now()+ms;do{const tree=await api('GET','/v0/ui/tree');if(!tree.overlayIdentity&&predicate(tree))return tree;await delay(25);}while(Date.now()<end);throw new Error(label);}
async function click(key){const tree=await api('GET','/v0/ui/tree');const n=nodes(tree).filter(n=>n.translationKey===key&&n.visible!==false&&n.active!==false);assert.equal(n.length,1,key);return api('POST','/v0/ui/action',{selector:{nodeId:n[0].nodeId},action:'click'});}
async function key(code,state='completed'){const op=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:code,holdMs:40}],timeoutMs:5000});const result=await api('POST',`/v0/operations/${op.operationId}/wait`,{timeoutMs:6000});assert.equal(result.state,state,JSON.stringify(result));return result;}
async function clean(){const s=await api('GET','/v0/input/state');assert.equal(s.pressedKeyCount,0);assert.equal(s.pressedButtonCount,0);return s;}
async function acquire(){assert.equal((await api('GET','/v0/control/mode')).reconsentRequired,false,'Never reacquire after manual revoke');lease=(await api('POST','/v0/control/acquire',{ttlMs:60000})).leaseId;}
async function check(name,work){if(lease)await api('POST','/v0/control/renew',{ttlMs:60000});await work();checks.push({name,status:'PASS'});console.log('PASS: '+name);}
async function barrier(){
  const attempt=randomUUID().replaceAll('-','');await api('POST','/v0/control/renew',{ttlMs:60000});await api('POST','/v0/command/player',{command:'say Mine-Craft-Protocol_command_configuration_'+attempt});
  const end=Date.now()+10000;do{try{const value=JSON.parse(await readFile(resolve(repo,'validations',validation,'result',target+'-command-configuration-probe.json'),'utf8'));if(value.attemptId===attempt){assert.equal(value.worldMutationUsedByProbe,false);assert.equal(value.chunkLoadRequested,false);assert(Number.isInteger(value.nativePlayerCommands));return value;}}catch(error){if(error.code!=='ENOENT'&&!(error instanceof SyntaxError))throw error;}await delay(25);}while(Date.now()<end);throw new Error('native command FIFO barrier timeout');
}
try{
  await check('READ cannot submit a player command',async()=>{await api('POST','/v0/command/player',{command:'help'},'TAKEOVER_REQUIRED');await clean();});
  await acquire();
  await check('semantic GUI enters the restored fixed world',async()=>{
    await wait(t=>t.screenClass.endsWith('TitleScreen'),'title');await click('menu.singleplayer');
    const tree=await wait(t=>t.screenClass.endsWith('SelectWorldScreen'),'world selection');
    const world=nodes(tree).filter(n=>n.worldName==='World-Read-Alpha');assert.equal(world.length,1);
    await api('POST','/v0/ui/action',{selector:{nodeId:world[0].nodeId},action:'click'});await click('selectWorld.select');
    await wait(t=>!t.screenClass,'enter fixed world',150000);
  });
  const itemPayload=target==='1.20.1-forge'?'give @s minecraft:command_block{BlockEntityTag:{Command:"reload"}}':'give @s minecraft:command_block[minecraft:block_entity_data={id:"minecraft:command_block",Command:"reload"}]';
  const rejectedPayloads=[
    ['summon minecraft:command_block_minecart ~ ~ ~ {Command:"reload"}','PLAYER_COMMAND_OUT_OF_SCOPE'],
    ['setblock 2 80 0 minecraft:command_block{Command:"reload"}','PLAYER_COMMAND_OUT_OF_SCOPE'],
    ['data modify block 2 80 0 Command set value "reload"','PLAYER_COMMAND_OUT_OF_SCOPE'],
    ['setblock 2 80 0 minecraft:jigsaw{final_state:\'minecraft:command_block{Command:"reload"}\'}','PLAYER_COMMAND_OUT_OF_SCOPE'],
    ['data modify block 2 80 0 final_state set value \'minecraft:command_block{Command:"reload"}\'','PLAYER_COMMAND_OUT_OF_SCOPE'],
    [itemPayload,'PLAYER_COMMAND_OUT_OF_SCOPE'],
    ['summon minecraft:command_block_minecart ~ ~ ~ {Command:"unknown_inner_program"}','PLAYER_COMMAND_UNCLASSIFIED'],
    ['data modify block 2 80 0 Command set from block 2 80 0 Command','PLAYER_COMMAND_UNCLASSIFIED'],
    ['function minecraft:missing','PLAYER_COMMAND_UNCLASSIFIED'],
    ['schedule function minecraft:missing 1t','PLAYER_COMMAND_UNCLASSIFIED'],
    ['clone 0 80 0 0 80 0 1 80 0','PLAYER_COMMAND_UNCLASSIFIED'],
    ['place template minecraft:igloo/top','PLAYER_COMMAND_UNCLASSIFIED'],
    ['loot give @s loot minecraft:chests/simple_dungeon','PLAYER_COMMAND_UNCLASSIFIED'],
    ['item replace entity @s hotbar.0 from entity @s hotbar.1','PLAYER_COMMAND_UNCLASSIFIED'],
    ['item modify entity @s hotbar.0 minecraft:unknown_modifier','PLAYER_COMMAND_UNCLASSIFIED']
  ];
  await check('native structured payload preflight rejects nested controls before any player input',async()=>{
    const initial=await barrier(),start=await clean();
    for(const [command,code]of rejectedPayloads)await api('POST','/v0/command/player',{command},code);
    const end=await clean();assert.equal(end.inputDispatchSequence,start.inputDispatchSequence);assert.equal((await api('GET','/v0/ui/tree')).screenClass,'');
    const after=await barrier();assert.equal(after.nativePlayerCommands,initial.nativePlayerCommands+1,'Only the second FIFO barrier reaches the real server');
    actionSamples.push({case:'native_payload_preflight',initial,after,start,end,rejected:rejectedPayloads.map(([,code])=>code)});
  });
  await check('raw native Chat cannot bypass embedded-program checks on native submission',async()=>{
    const initial=await barrier();
    for(const [command,code]of [rejectedPayloads[1],rejectedPayloads[2],rejectedPayloads[7],rejectedPayloads[8],rejectedPayloads[9],rejectedPayloads[10],rejectedPayloads[13]]){
      await key(84);await wait(t=>t.screenClass.endsWith('ChatScreen'),'native chat for payload');
      await api('POST','/v0/ui/action',{selector:{role:'text_field',nth:0},action:'type_text',text:'/'+command});
      const result=await key(257,'failed');assert.equal(result.errorCode,code,JSON.stringify(result));
      for(let i=0;i<4;i++){if(!(await api('GET','/v0/ui/tree')).screenClass)break;await key(256);}
      await wait(t=>!t.screenClass,'rejected native Chat cleanup');await clean();actionSamples.push({case:'raw_payload_denial',code,result});
    }
    const after=await barrier();assert.equal(after.nativePlayerCommands,initial.nativePlayerCommands+1,'Rejected raw submissions must not reach the server');
    actionSamples.push({case:'raw_payload_zero_server_dispatch',initial,after});
  });
  if(!target.startsWith('1.')) await check('opaque native rich-text custom action fails before any server callback',async()=>{
    const label='Opaque callback validation';
    await api('POST','/v0/command/player',{command:'tellraw @s '+JSON.stringify({
      text:label,click_event:{action:'custom',id:'minecraft:validation',payload:{unresolved:true}}
    })});
    const initial=await barrier();
    await key(84);
    const t=await wait(t=>nodes(t).some(n=>n.role==='link'&&n.label?.includes(label)),'native custom-action link');
    const links=nodes(t).filter(n=>n.role==='link'&&n.label?.includes(label));
    // Native glyph active areas may split a styled link by glyph height/spacing.
    // Choose one real semantic node, not a guessed whole-label bounding rectangle.
    assert(links.length>0);assert.equal(new Set(links.map(n=>n.nodeId)).size,links.length);
    assert(links.every(n=>n.clickEventType==='custom'&&n.actions?.includes('click')));
    const link=links[0];assert.equal(link.clickEventType,'custom');
    const operation=await api('POST','/v0/pipelines',{steps:[{type:'ui.action',selector:{nodeId:link.nodeId},action:'click'}],timeoutMs:10000});
    const result=await api('POST',`/v0/operations/${operation.operationId}/wait`,{timeoutMs:12000});
    assert.equal(result.state,'failed',JSON.stringify(result));assert.equal(result.errorCode,'PLAYER_COMMAND_UNCLASSIFIED',JSON.stringify(result));
    assert.equal(result.result?.commandSubmissions?.length??0,0);
    for(let i=0;i<4;i++){if(!(await api('GET','/v0/ui/tree')).screenClass)break;await key(256);}
    await wait(t=>!t.screenClass,'custom-action draft cleanup');
    const after=await barrier();assert.equal(after.customActionPackets,initial.customActionPackets);
    actionSamples.push({case:'custom_action_rejected',semanticRegionCount:links.length,
      selectedControlKey:link.controlKey,initial,after,result});
    await clean();
  });
  await check('safe literal configuration and scalar clearing use native setters with independent server readback',async()=>{
    for(const [command,text]of [
      ['setblock 2 80 0 minecraft:command_block{Command:"time query gametime"}','time query gametime'],
      ['data modify block 2 80 0 Command set value "help"','help'],
      ['data modify block 2 80 0 Command set value ""','']
    ]){
      const receipt=await api('POST','/v0/command/player',{command});assert.equal(receipt.effectClassification,'STATE_MUTATION');assert.equal(receipt.evidenceContaminated,true);assert.equal(receipt.effectConfirmed,false);
      const readback=await barrier();assert.equal(readback.storedBlockCommandHash,createHash('sha256').update(text).digest('hex'));actionSamples.push({case:'safe_payload_native_readback',receipt,readback,assertAuthority:'DEBUG_probe_native_server_readback'});
    }
    const query=await api('POST','/v0/command/player',{command:'data get block 2 80 0 Command'});assert.equal(query.effectClassification,'INFORMATION');assert.equal(query.evidenceContaminated,false);
    await api('POST','/v0/command/player',{command:'setblock 2 80 0 minecraft:air'});await clean();
  });
  await check('helper uses native Chat characters and Enter with truthful classified receipt',async()=>{
    const start=await clean();
    const receipt=await api('POST','/v0/command/player',{command:'help'});
    assert.equal(receipt.mechanism,'GAME_ROUTED_RAW');assert.equal(receipt.effectClassification,'COMMUNICATION');
    assert.equal(receipt.evidenceContaminated,false);assert.equal(receipt.effectConfirmed,false);assert.equal(receipt.gameplayAcceptanceEligible,false);
    const end=await clean();assert(end.inputDispatchSequence>start.inputDispatchSequence);
    await wait(t=>!t.screenClass,'native Enter closed chat');actionSamples.push({case:'native_chat_helper',receipt,start,end});
  });
  await check('native preflight rejects host commands and chat-limit overflow before input',async()=>{
    const start=await clean();
    await api('POST','/v0/command/player',{command:'reload'},'PLAYER_COMMAND_OUT_OF_SCOPE');
    await api('POST','/v0/command/player',{command:'say '+'x'.repeat(260)},'PLAYER_COMMAND_CHAT_LIMIT');
    const end=await clean();assert.equal(end.inputDispatchSequence,start.inputDispatchSequence);assert.equal((await api('GET','/v0/ui/tree')).screenClass,'');
    actionSamples.push({case:'zero_input_preflight',start,end});
  });
  await check('existing chat draft is not overwritten or submitted by helper',async()=>{
    await key(84);await wait(t=>t.screenClass.endsWith('ChatScreen'),'native chat');
    await api('POST','/v0/ui/action',{selector:{role:'text_field',nth:0},action:'type_text',text:'/help'});
    const start=await clean();await api('POST','/v0/command/player',{command:'help'},'PLAYER_COMMAND_SCREEN_ACTIVE');
    const tree=await api('GET','/v0/ui/tree');assert(nodes(tree).some(n=>n.role==='text_field'&&n.value==='/help'));
    const end=await clean();assert.equal(end.inputDispatchSequence,start.inputDispatchSequence);await key(256);
  });
  await check('query stays unconfirmed and mutation retains diagnostic evidence',async()=>{
    const query=await api('POST','/v0/command/player',{command:'time query gametime'});
    const mutation=await api('POST','/v0/command/player',{command:'time add 0'});
    assert.equal(query.effectClassification,'INFORMATION');assert.equal(query.evidenceContaminated,false);
    assert.equal(mutation.effectClassification,'STATE_MUTATION');assert.equal(mutation.evidenceContaminated,true);assert.equal(mutation.mode,'DEBUG_PRIVILEGED');
    assert.equal(mutation.effectConfirmed,false);actionSamples.push({case:'effects',query,mutation});await clean();
  });
  await check('lease loss during native typing prevents Enter and late input',async()=>{
    const pending=api('POST','/v0/command/player',{command:'say '+'x'.repeat(230)}).then(value=>({value}),error=>({error}));
    await wait(t=>t.screenClass.endsWith('ChatScreen')&&nodes(t).some(n=>n.role==='text_field'&&n.value?.length>0&&n.value.length<235),'observe actual partial draft');
    await api('POST','/v0/control/release');lease=undefined;
    const result=await pending;assert(result.error,'Command must not report successful submission after lease loss');
    assert.equal((await api('GET','/v0/control/mode')).mode,'READ');const start=await clean();await delay(180);const end=await clean();
    assert.equal(end.inputDispatchSequence,start.inputDispatchSequence);
    const tree=await api('GET','/v0/ui/tree');assert(tree.screenClass.endsWith('ChatScreen'),'Cancel preserves draft, never secretly submits or closes it');
    actionSamples.push({case:'lease_loss_native_typing',error:String(result.error),start,end});
    await acquire();await key(256);await wait(t=>!t.screenClass,'explicit caller closes leftover draft');
  });
  await check('actual MCP stdio consumer uses the same native Chat helper',async()=>{
    await api('POST','/v0/control/release');lease=undefined;
    consumerChecks.push(...await playerCommandConsumer(repo,instance,target));
    assert.equal((await api('GET','/v0/control/mode')).mode,'READ');await clean();
  });
}catch(error){failure=error;console.error(error);}
finally{
  try{
    if(!lease)await acquire();
    for(let i=0;i<8;i++){
      const tree=await api('GET','/v0/ui/tree');
      if(tree.screenClass.endsWith('TitleScreen')){shutdownResponse='requested';try{await click('menu.quit');shutdownResponse='acknowledged';}catch(error){if(error.code!=='CancellationException')throw error;shutdownResponse='cancelled_at_runtime_close_requires_runner_exit_attestation';}break;}
      if(nodes(tree).some(n=>['menu.returnToMenu','menu.disconnect'].includes(n.translationKey))){const n=nodes(tree).find(n=>['menu.returnToMenu','menu.disconnect'].includes(n.translationKey));await api('POST','/v0/ui/action',{selector:{nodeId:n.nodeId},action:'click'});await wait(t=>t.screenClass.endsWith('TitleScreen'),'saved and quit',60000);}
      else await key(256);
    }
    assert.notEqual(shutdownResponse,'not_requested');
  }catch(error){failure??=error;console.error(error);}
  const after=await sources(),sourceChangedDuringRun=JSON.stringify(before)!==JSON.stringify(after);
  if(sourceChangedDuringRun)failure??=new Error('Sources changed during Runtime test');
  const directory=resolve(repo,'validations',validation,'result');await mkdir(directory,{recursive:true});
  await writeFile(resolve(directory,target+'-player-command.json'),JSON.stringify({status:failure?'FAIL':'PASS',target,scenario:'player-command',fullSmoke:false,fullWorldMilestone:false,testedAt:new Date().toISOString(),testedCommit:execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).trim(),dirty:true,evidence:'development_runtime_native',runtimeConnection:{runtimeInstanceId:connection.instanceId,port:connection.port,pid:connection.pid},fixturePolicy:fixtureInput.policy,fixtureInput,checks,actionSamples,consumerChecks,consumerScope:'player_command_chat',shutdownResponse,sourceHashes:after,sourceChangedDuringRun,persistentWriteCalls:0,notExecuted:['full_world_smoke','packaged_artifact_runtime','human_acceptance'],...(failure?{error:String(failure)}:{})},null,2)+'\n');
}
if(failure)process.exitCode=1;
