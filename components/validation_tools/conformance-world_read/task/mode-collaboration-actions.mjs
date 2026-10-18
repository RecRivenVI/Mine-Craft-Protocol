import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

// All changes are confined to the runner's restored world; no physical Esc is simulated.
export async function modeCollaborationActions({api,connection,repo,instance,target,mode,acquire,release,key,click,clickNode,wait,nodes,check,actionSamples,setArm}) {
  const samples=[];
  async function clean(){const s=await api('GET','/v0/input/state');assert.equal(s.pressedKeyCount,0);assert.equal(s.pressedButtonCount,0);return s;}
  const chunkQuery={category:'DEBUG',perspective:'server_authoritative',domains:['chunks'],selector:{chunkRadius:0}};
  await check('READ admits both independently authorized categories but neither input nor native command',async()=>{
    const caps=await api('GET','/v0/capabilities');assert.equal(caps.worldContract.readModeIndependent,true);assert.equal(caps.uiContract.readModeIndependent,true);assert.equal(caps.capabilities['command.player.native'],'native_current_player_without_input_lease');
    for(const category of ['USER','DEBUG'])assert.equal((await api('GET','/v0/player?category='+category)).dataCategory,category);
    await api('POST','/v0/input/key',{key:87,scanCode:0,action:1,modifiers:0},'TAKEOVER_REQUIRED');
    await api('POST','/v0/command/player',{command:'help',route:'NATIVE'},'OPERATE_REQUIRED');
    await clean();
  });
  await acquire();await click('menu.singleplayer','Singleplayer');
  let tree=await wait(t=>t.screenClass.endsWith('SelectWorldScreen'),'collaboration world list');
  const row=nodes(tree).find(n=>n.worldName==='World-Read-Alpha');assert(row);
  await clickNode(row);await click('selectWorld.select','Play Selected World');await wait(t=>!t.screenClass,'collaboration world entered',150000);await release();
  const readChunks=await api('POST','/v0/observe/deep',chunkQuery);
  await check('OPERATE retains USER and DEBUG reads without input Lease',async()=>{
    await mode('OPERATE');
    for(const category of ['USER','DEBUG']) {
      assert.equal((await api('GET','/v0/player?category='+category)).dataCategory,category);
      assert.equal((await api('GET','/v0/ui/tree?category='+category)).dataCategory,category);
    }
    assert.equal((await api('GET','/v0/control/mode')).takeoverActive,false);
    await api('POST','/v0/input/key',{key:87,scanCode:0,action:1,modifiers:0},'TAKEOVER_REQUIRED');
  });
  await check('OPERATE AUTO native command does not inject input or acquire Lease',async()=>{
    const before=await clean(),screen=await api('GET','/v0/ui/tree');
    const receipt=await api('POST','/v0/command/player',{command:'help'});
    assert.equal(receipt.route,'NATIVE');assert.equal(receipt.mechanism,'NORMAL_NETWORK');assert.equal(receipt.dataCategory,'USER');
    assert.equal(receipt.requiresControlLease,false);assert.equal(receipt.inputDispatched,false);assert.equal(receipt.effectConfirmed,false);assert.equal(receipt.gameplayAcceptanceEligible,false);assert.equal(receipt.feedbackAvailable,false);
    const after=await clean();assert.equal(typeof before.inputDispatchSequence,'number');assert.equal(after.inputDispatchSequence,before.inputDispatchSequence);
    assert.equal((await api('GET','/v0/ui/tree')).screenIdentity,screen.screenIdentity);
    assert.equal((await api('GET','/v0/control/mode')).mode,'OPERATE');
    await api('POST','/v0/command/player',{command:'help',route:'CHAT'},'TAKEOVER_REQUIRED');
    samples.push({case:'native_operate',receipt,inputBefore:before,inputAfter:after});
  });
  await check('TAKEOVER DEBUG chunk payload is not downgraded or stripped by mode',async()=>{
    await acquire();const takeover=await api('POST','/v0/observe/deep',chunkQuery);
    assert.equal(takeover.dataCategory,'DEBUG');assert.equal(takeover.server.chunkLoadRequested,false);
    assert(readChunks.server.chunks.length>0);assert(takeover.server.chunks.length>0);
    const before=readChunks.server.chunks.find(c=>c.loaded),after=takeover.server.chunks.find(c=>c.loaded&&c.chunkX===before?.chunkX&&c.chunkZ===before?.chunkZ);
    assert(before);assert(after,JSON.stringify(takeover.server.chunks));
    assert.deepEqual(Object.keys(after).sort(),Object.keys(before).sort());
    assert.equal(after.sectionCount,before.sectionCount);
    const remote=await api('GET','/v0/world/block?category=DEBUG&x=1000000&y=64&z=1000000');assert.equal(remote.available,false);assert.equal(remote.chunkLoadRequested,false);
    samples.push({case:'takeover_chunk_fields',read:before,takeover:after,remote});
  });
  let fingerprint,original;
  const facts=await api('GET','/v0/player?category=DEBUG'),at={x:Math.floor(facts.x)+4,y:Math.floor(facts.y)+2,z:Math.floor(facts.z)+4};
  async function block(){const s=await api('POST','/v0/observe/deep',{category:'DEBUG',perspective:'server_authoritative',domains:['blocks'],selector:{blocks:[at]}});return {state:s.server.blocks[0],ref:s.resourceRevisionRefs.find(r=>r.resourceType==='block'&&r.resourceKey.includes(`${at.x},${at.y},${at.z}`))??s.resourceRevisionRefs.find(r=>r.resourceType==='block')};}
  let lastMutation=0;
  async function mutate(value,expected){const pause=550-(Date.now()-lastMutation);if(pause>0)await new Promise(r=>setTimeout(r,pause));lastMutation=Date.now();const s=await block();return api('POST','/v0/debug/mutations',{operation:'world.block.set',worldFingerprint:fingerprint,expectedResourceVersion:s.ref,x:at.x,y:at.y,z:at.z,blockId:value,expectedBlockId:s.state.blockId,expectedProperties:s.state.properties},expected);}
  try {
    await check('TAKEOVER independent typed Debug still requires Arm and resource preconditions',async()=>{
      fingerprint=(await api('GET','/v0/world/fingerprint?category=DEBUG')).worldFingerprint;original=await block();
      assert(original.ref,JSON.stringify(original));
      assert.equal(original.state.blockId,'minecraft:air','Only restored empty fixture air may be changed');
      await mutate('minecraft:stone','DEBUG_NOT_ARMED');
      setArm((await api('POST','/v0/debug/arm',{worldFingerprint:fingerprint,namespaces:['world'],ttlMs:60000})).debugArmId);
      const changed=await mutate('minecraft:stone');assert.equal(changed.gameplayEvidence,false);assert.equal(changed.evidenceContaminated,true);
      assert.equal((await block()).state.blockId,'minecraft:stone');assert.equal((await api('GET','/v0/control/mode')).mode,'TAKEOVER');
      samples.push({case:'takeover_debug',result:changed});
    });
    await check('leaving TAKEOVER cancels queued player input but retains read and independent authority',async()=>{
      const started=await api('POST','/v0/pipelines',{steps:[{type:'key',key:87,action:1},{type:'delay',durationMs:2500},{type:'key.tap',key:65,holdMs:100}],timeoutMs:10000});
      await release();const outcome=await api('POST',`/v0/operations/${started.operationId}/wait`,{timeoutMs:5000});assert.equal(outcome.state,'cancelled',JSON.stringify(outcome));
      const stopped=await clean();await new Promise(r=>setTimeout(r,400));const later=await clean();assert.equal(typeof stopped.inputDispatchSequence,'number');assert.equal(later.inputDispatchSequence,stopped.inputDispatchSequence);
      assert.equal((await api('GET','/v0/player?category=DEBUG')).dataCategory,'DEBUG');
      await mode('OPERATE');await mutate(original.state.blockId);assert.equal((await block()).state.blockId,original.state.blockId);
      samples.push({case:'input_handback',operation:outcome,stopped,later,physicalEscTested:false});
    });
  } finally {
    if(original){const current=await api('GET','/v0/control/mode');if(current.mode==='TAKEOVER')await release();await mode('OPERATE');if((await block()).state.blockId!==original.state.blockId)await mutate(original.state.blockId);}
    await api('POST','/v0/debug/disarm');setArm(undefined);
  }
  await check('native command leaves an existing Chat Screen and draft unchanged',async()=>{
    await acquire();await key(84);await wait(t=>t.screenClass.endsWith('ChatScreen'),'existing native Chat');
    const t=await api('GET','/v0/ui/tree'),editor=nodes(t).find(n=>n.role==='text_field');assert(editor);
    await api('POST','/v0/ui/action',{selector:{nodeId:editor.nodeId},action:'type_text',text:'collaboration unsent draft'});
    await release();await mode('OPERATE');
    const before=await api('GET','/v0/ui/tree');const receipt=await api('POST','/v0/command/player',{command:'help',route:'NATIVE'});const after=await api('GET','/v0/ui/tree');
    assert.equal(after.screenIdentity,before.screenIdentity);assert.deepEqual(nodes(after).filter(n=>n.role==='text_field').map(n=>n.value),nodes(before).filter(n=>n.role==='text_field').map(n=>n.value));
    assert.equal(receipt.inputDispatched,false);await clean();
    await acquire();await key(256);assert.equal((await api('GET','/v0/control/mode')).mode,'TAKEOVER');await release();await mode('OPERATE');
  });
  await check('actual MCP exposes native command route and TAKEOVER diagnostic chunk reads',async()=>{
    const require=createRequire(resolve(repo,'components/companion/package.json'));
    const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
    const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
    const env=Object.fromEntries(Object.entries(process.env).filter(([,v])=>typeof v==='string'));
    for(const name of ['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE'])delete env[name];env.MCP_MINECRAFT_INSTANCE_DIR=instance;
    const client=new Client({name:'mode-collaboration-regression',version:'1.0.0'}),transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
    try {
      await client.connect(transport);
      let result=await client.callTool({name:'minecraft_execute_player_command',arguments:{command:'help',route:'NATIVE'}});assert(!result.isError,JSON.stringify(result));assert.equal(result.structuredContent.data.route,'NATIVE');
      await acquire();result=await client.callTool({name:'minecraft_deep_observe',arguments:chunkQuery});assert(!result.isError,JSON.stringify(result));assert(result.structuredContent.data.server.chunks.length>0);assert.equal(result.structuredContent.data.dataCategory,'DEBUG');await release();
    }finally{await client.close();}
  });
  await mode('READ');await clean();actionSamples.push(...samples);
}
