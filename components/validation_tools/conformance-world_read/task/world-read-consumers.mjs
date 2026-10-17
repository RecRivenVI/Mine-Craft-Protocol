import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
import { resolve, dirname } from 'node:path';
import { pathToFileURL } from 'node:url';
import { automaticTakeoverRequest } from '../../runtime-connection.mjs';

// Only the runner's owned Runtime is used. Player input and diagnostic mutations remain
// separately authorized; no-op health and a reversible UI flag exercise real batch results.
export async function commandOperationConsumer(repo, instanceDirectory, target, operationId, expected) {
  const require=createRequire(resolve(repo,'components/companion/package.json'));
  const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
  const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
  const env=Object.fromEntries(Object.entries(process.env).filter(([,value])=>typeof value==='string'));
  for(const key of ['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE'])delete env[key];
  env.MCP_MINECRAFT_INSTANCE_DIR=instanceDirectory;env.MCP_MINECRAFT_TARGET=target;
  const client=new Client({name:'command-provenance-consumer',version:'0.0.1'});
  const transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
  const call=async(name,args)=>{const result=await client.callTool({name,arguments:args});assert(!result.isError,JSON.stringify(result));return result.structuredContent.data;};
  try {
    await client.connect(transport);
    for(const tool of ['minecraft_get_operation','minecraft_wait_operation']){
      const value=await call(tool,{operationId,category:'USER',...(tool==='minecraft_wait_operation'?{timeoutMs:1000}:{})});
      assert.equal(value.state,expected.state);assert.equal(value.commandSubmissionsComplete,true);
      assert.equal(value.result.partial===true,expected.partial??true);assert.equal(value.result.commandSubmissions.length,1);
      assert.equal(value.result.evidenceContaminated,expected.contaminated);assert.equal(value.result.commandEffectsConfirmed,false);
      assert.equal(value.result.commandSubmissions[0].effectClassification,expected.effect);
      assert.equal(value.result.commandSubmissions[0].effectConfirmed,false);
      if(expected.configurationKind){
        const receipt=value.result.commandSubmissions[0];
        assert.equal(receipt.configurationKind,expected.configurationKind);
        assert.equal(receipt.configuredCommandEffect,expected.configuredCommandEffect);
        if(expected.configurationPayloadType)assert.equal(receipt.configurationPayloadType,expected.configurationPayloadType);
        assert.equal(receipt.classificationSource,'native_player_command_configuration');
        assert.equal(receipt.contaminationReason,'player_command_configuration');
        assert.equal(receipt.gameplayAcceptanceEligible,false);
      }
    }
    const control=await call('minecraft_control',{action:'status'});assert.equal(control.mode,'READ');assert.equal(control.takeoverActive,false);
    return {name:'MCP '+expected.scenario+' retains terminal input provenance under READ without a Lease',status:'PASS'};
  } finally {try{await client.close();}finally{await transport.close();}}
}

export async function playerCommandConsumer(repo, instanceDirectory, target) {
  const require=createRequire(resolve(repo,'components/companion/package.json'));
  const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
  const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
  const env=Object.fromEntries(Object.entries(process.env).filter(([,value])=>typeof value==='string'));
  for(const key of ['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE'])delete env[key];
  env.MCP_MINECRAFT_INSTANCE_DIR=instanceDirectory;env.MCP_MINECRAFT_TARGET=target;
  const client=new Client({name:'native-chat-consumer',version:'0.0.1'});
  const transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
  const call=async(name,args)=>{const result=await client.callTool({name,arguments:args});assert(!result.isError,JSON.stringify(result));return result.structuredContent.data;};
  let lease;
  try{
    await client.connect(transport);
    const mode=await call('minecraft_control',{action:'status'});assert.equal(mode.mode,'READ');assert.equal(mode.reconsentRequired,false);
    lease=(await call('minecraft_control',automaticTakeoverRequest(mode,{action:'acquire',ttlMs:30000}))).leaseId;
    const receipt=await call('minecraft_execute_player_command',{leaseId:lease,command:'help'});
    assert.equal(receipt.mechanism,'GAME_ROUTED_RAW');assert.equal(receipt.effectClassification,'COMMUNICATION');
    assert.equal(receipt.evidenceContaminated,false);assert.equal(receipt.effectConfirmed,false);assert.equal(receipt.gameplayAcceptanceEligible,false);
    return [{name:'MCP native Chat helper preserves classified unconfirmed receipt without silent acquire',status:'PASS'}];
  }finally{
    try{if(lease)await call('minecraft_control',{action:'release',leaseId:lease});}
    finally{try{await client.close();}finally{await transport.close();}}
  }
}

export async function inputContractConsumers(repo, instanceDirectory, target, leaseId) {
  const require=createRequire(resolve(repo,'components/companion/package.json'));
  const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
  const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
  const env=Object.fromEntries(Object.entries(process.env).filter(([,value])=>typeof value==='string'));
  for(const key of ['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE'])delete env[key];
  env.MCP_MINECRAFT_INSTANCE_DIR=instanceDirectory;env.MCP_MINECRAFT_TARGET=target;
  const client=new Client({name:'input-contract-consumer',version:'0.0.1'});
  const transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
  try {
    await client.connect(transport);
    if(leaseId){
      const denied=await client.callTool({name:'minecraft_run_input_pipeline',arguments:{leaseId,steps:[{type:'key',key:87,action:1},{type:'assert.that',condition:{type:'screen',category:'DEBUG'}}]}});
      assert.equal(denied.isError,true,JSON.stringify(denied));assert.equal(denied.structuredContent.error.code,'UI_CATEGORY_MODE_MISMATCH');
      return [{name:'MCP typed DEBUG condition denial preserves Native error without authority upgrade',status:'PASS'}];
    }
    for(const category of ['USER','DEBUG']){
      const result=await client.callTool({name:'minecraft_assert',arguments:{condition:{type:'screen',category,classContains:'TitleScreen'}}});
      assert(!result.isError,JSON.stringify(result));assert.equal(result.structuredContent.data.domain,'UI');assert.equal(result.structuredContent.data.dataCategory,category);
      assert.equal(Boolean(result.structuredContent.data.evidence.debugFacts),category==='DEBUG');
    }
    return [{name:'MCP UI USER and DEBUG conditions preserve explicit category and native projection',status:'PASS'}];
  } finally {await client.close();}
}

export async function worldReadConsumers(repo, base, tokenFile, target) {
  const require = createRequire(resolve(repo, 'components/companion/package.json'));
  const { Client } = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
  const { StdioClientTransport } = await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
  const env = Object.fromEntries(Object.entries(process.env).filter(([, value]) => typeof value === 'string'));
  delete env.MCP_MINECRAFT_TOKEN;
  delete env.MCP_MINECRAFT_BASE_URL;
  delete env.MCP_MINECRAFT_TOKEN_FILE;
  env.MCP_MINECRAFT_INSTANCE_DIR = dirname(dirname(tokenFile));
  env.MCP_MINECRAFT_TARGET = target;
  const client = new Client({name:'world-read-conformance',version:'0.0.1'});
  const transport = new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
  const checks=[];
  const call=async(name,args,error)=>{
    if(name==='minecraft_control'&&args.action==='acquire')args=automaticTakeoverRequest(await call('minecraft_control',{action:'status'}),args);
    const result=await client.callTool({name,arguments:args});
    if(error){assert.equal(result.structuredContent.error.code,error,JSON.stringify(result));return;}
    assert(!result.isError,JSON.stringify(result.structuredContent));return result.structuredContent.data;
  };
  try {
    await client.connect(transport);
    const player=await call('minecraft_get_state',{kind:'player'});
    assert.equal(player.target,target);assert.equal(player.dataCategory,'USER');assert.equal(typeof player.x,'number');
    const scene=await call('minecraft_get_state',{kind:'scene'});
    assert.equal(scene.dataCategory,'USER');assert.equal(scene.coverage,'bounded_client_cache');
    const sceneQuery=await call('minecraft_query_world',{kind:'scene',radius:1,blockLimit:16,entityLimit:8});
    assert.equal(sceneQuery.coverage,'bounded_client_cache');assert(sceneQuery.blocks.length<=16);assert(sceneQuery.entities.length<=8);
    const nearby=await call('minecraft_query_world',{kind:'entities',radius:16,limit:8});
    assert.equal(nearby.authority,'CLIENT_KNOWN');assert(nearby.entities.length<=8);
    await call('minecraft_query_world',{kind:'entities',radius:33},'INVALID_WORLD_USER_QUERY');
    checks.push({name:'MCP USER player, bounded scene/entity queries and budgets',status:'PASS'});
    const internal=await call('minecraft_get_state',{kind:'state_frame',category:'DEBUG',reads:[{providerId:'minecraft:client/player'}]});
    assert.equal(internal.dataCategory,'DEBUG');assert.equal(typeof internal.reads[0].data.x,'number');
    await call('minecraft_get_state',{kind:'state_frame',reads:[{providerId:'minecraft:server/player'}]},'WORLD_USER_PERCEPTION_UNAVAILABLE');
    checks.push({name:'MCP State Frame category preservation and USER refusal',status:'PASS'});
    assert.equal((await call('minecraft_assert',{condition:{type:'player',healthMin:1}})).passed,true);
    const assertion=await call('minecraft_assert',{condition:{type:'player',category:'DEBUG',expected:{available:true}}});
    assert.equal(assertion.passed,true);assert.equal(assertion.dataCategory,'DEBUG');
    const deep=await call('minecraft_deep_observe',{category:'DEBUG',perspective:'client_known',domains:['player']});
    assert.equal(deep.dataCategory,'DEBUG');
    checks.push({name:'MCP assertion and formal Deep classification',status:'PASS'});
    const recording=await call('minecraft_start_recording',{config:{category:'DEBUG',captureFrames:false,maxSamples:1,intervalMs:50,stateReads:[{providerId:'minecraft:client/player'}],contactSheet:{enabled:false}}});
    await call('minecraft_recording',{action:'get',recordingId:recording.recordingId},'RECORDING_NOT_FOUND');
    const deadline=Date.now()+15000;
    let ready=false;
    while(Date.now()<deadline){
      const status=await call('minecraft_recording',{action:'get',category:'DEBUG',recordingId:recording.recordingId});
      if(status.artifactReady){ready=true;break;}
      assert.notEqual(status.status,'failed',JSON.stringify(status));
      await new Promise(done=>setTimeout(done,50));
    }
    assert(ready,'MCP diagnostic recording did not finalize');
    const artifact=await client.readResource({uri:`minecraft://recordings/debug/${recording.recordingId}/artifact`});
    assert('blob'in artifact.contents[0]);
    assert.equal(Buffer.from(artifact.contents[0].blob,'base64').subarray(0,2).toString(),'PK');
    checks.push({name:'MCP DEBUG recording and category-bound binary resource',status:'PASS'});
    const mode=await call('minecraft_control',{action:'status'});
    assert.equal(mode.mode,'READ');
    checks.push({name:'Read consumers neither acquire Lease nor change mode',status:'PASS'});
    await call('minecraft_run_input_pipeline',{steps:[{type:'world.action',targetRef:'world:unknown',action:'aim'}]},'TAKEOVER_REQUIRED');
    assert.equal(mode.reconsentRequired,false,'A human revoke must not be bypassed by conformance');
    await call('minecraft_control',{action:'acquire',ttlMs:30000});
    try {
      const receipt=await call('minecraft_execute_player_command',{command:'help'});
      assert.equal(receipt.mechanism,'GAME_ROUTED_RAW');
      assert.equal(receipt.effectClassification,'COMMUNICATION');
      assert.equal(receipt.submissionStatus,'submitted_unconfirmed');
      assert.equal(receipt.effectConfirmed,false);
      assert.equal(receipt.serverValidationObserved,false);
      assert.equal(receipt.gameplayAcceptanceEligible,false);
      assert.equal(receipt.evidenceContaminated,false);
      checks.push({name:'MCP player command preserves native Chat classified and unconfirmed submission receipt',status:'PASS'});
      const position=await call('minecraft_get_state',{kind:'player'});
      const block=await call('minecraft_query_world',{kind:'block',x:Math.floor(position.x),y:Math.floor(position.y)-1,z:Math.floor(position.z)});
      assert(block.targetRef?.startsWith('world:'));
      const action=await call('minecraft_run_input_pipeline',{steps:[{type:'world.action',targetRef:block.targetRef,action:'aim'}],timeoutMs:10000,waitForCompletion:true});
      assert.equal(action.state,'completed',JSON.stringify(action));
      assert.equal(action.result.steps[0].result.domain,'WORLD');
      assert.equal(action.result.steps[0].result.directMutationUsed,false);
      checks.push({name:'MCP explicit TAKEOVER semantic target and native aim',status:'PASS'});
    } finally {await call('minecraft_control',{action:'release'});}
    await call('minecraft_control',{action:'set_mode',mode:'OPERATE'});
    try {
      const armed=await call('minecraft_debug_arm',{action:'arm',namespaces:['player'],ttlMs:60000});
      assert.equal(typeof armed.worldFingerprint,'string');
      const snapshot=await call('minecraft_deep_observe',{category:'DEBUG',perspective:'server_authoritative',domains:['player']});
      const ref=snapshot.resourceRevisionRefs.find(r=>r.resourceType==='player');assert(ref);
      const health=snapshot.server.player.health;assert.equal(typeof health,'number');
      const result=await call('minecraft_debug',{action:'batch',items:[{operation:'player.health.set',worldFingerprint:armed.worldFingerprint,expectedResourceVersion:ref,health,expectedHealth:health}],waitForCompletion:true});
      assert.equal(result.state,'completed',JSON.stringify(result));assert.equal(result.result.status,'partial',JSON.stringify(result));
      assert.equal(result.result.succeededItems,0);assert.equal(result.result.failedItems,1);
      assert.equal(result.result.items[0].error,'DEBUG_NO_STATE_CHANGE','An equal-value setter is rejected, not a successful mutation');
      const unchanged=await call('minecraft_deep_observe',{category:'DEBUG',perspective:'server_authoritative',domains:['player']});assert.equal(unchanged.server.player.health,health);
      const control=await call('minecraft_control',{action:'status'});assert.equal(control.mode,'OPERATE');assert.equal(control.takeoverActive,false,'World batch must not acquire player control');
      await call('minecraft_control',{action:'set_mode',mode:'READ'});
      await call('minecraft_get_operation',{operationId:result.operationId,category:'USER'},'OPERATION_NOT_FOUND');
      assert.equal((await call('minecraft_wait_operation',{operationId:result.operationId,category:'DEBUG',timeoutMs:1000})).state,'completed');
      checks.push({name:'MCP world Debug batch automatic DEBUG wait preserves explicit no-state-change result and USER isolation, without player Lease',status:'PASS'});
    } finally {
      await call('minecraft_debug_arm',{action:'disarm'});
      await call('minecraft_control',{action:'set_mode',mode:'READ'});
    }
    // The pause Screen is opened only through explicit TAKEOVER, never by OPERATE.
    await call('minecraft_control',{action:'acquire',ttlMs:30000});
    try {
      const result=await call('minecraft_run_input_pipeline',{steps:[{type:'key.tap',key:256,holdMs:40}],timeoutMs:10000});
      assert.equal(result.state,'completed');
    } finally {await call('minecraft_control',{action:'release'});}
    await call('minecraft_control',{action:'set_mode',mode:'OPERATE'});
    const flatten=value=>(value.children??[]).flatMap(n=>[n,...flatten(n)]);
    let original;
    try {
      const tree=await call('minecraft_get_ui',{category:'DEBUG'});assert(tree.screenClass.endsWith('PauseScreen'));
      original=flatten(tree).find(n=>n.translationKey==='menu.options');assert(original?.resourceVersion&&original.active);
      await new Promise(done=>setTimeout(done,600));
      await call('minecraft_debug_arm',{action:'arm',uiContextId:tree.uiContextId,namespaces:['ui'],ttlMs:60000});
      const result=await call('minecraft_debug',{action:'batch',items:[{operation:'ui.widget.active.set',nodeId:original.nodeId,expectedResourceVersion:original.resourceVersion,expectedValue:true,value:false}]});
      assert.equal(result.state,'completed',JSON.stringify(result));assert.equal(result.result.succeededItems,1,JSON.stringify(result));
      assert.equal(result.result.items[0].result.evidenceAuthority,'DEBUG_PRIVILEGED');
      assert.equal(result.result.items[0].result.inputDispatchCount,0);
      const changed=flatten(await call('minecraft_get_ui',{category:'DEBUG'})).find(n=>n.nodeId===original.nodeId);assert.equal(changed?.active,false);
      const control=await call('minecraft_control',{action:'status'});assert.equal(control.mode,'OPERATE');assert.equal(control.takeoverActive,false,'UI batch must not acquire player control');
      checks.push({name:'MCP pure UI Debug batch automatic wait and actual reversible widget mutation, without player Lease',status:'PASS'});
    } finally {
      try {
        if(original){
          const node=flatten(await call('minecraft_get_ui',{category:'DEBUG'})).find(n=>n.nodeId===original.nodeId);assert(node,'Owned pause widget must remain available for cleanup');
          if(node.active!==original.active){
            await new Promise(done=>setTimeout(done,600));
            const restored=await call('minecraft_debug',{action:'batch',items:[{operation:'ui.widget.active.set',nodeId:node.nodeId,expectedResourceVersion:node.resourceVersion,expectedValue:false,value:original.active}]});
            assert.equal(restored.result.succeededItems,1,JSON.stringify(restored));
          }
          assert.equal(flatten(await call('minecraft_get_ui',{category:'DEBUG'})).find(n=>n.nodeId===original.nodeId)?.active,original.active);
        }
      } finally {
        await call('minecraft_debug_arm',{action:'disarm'});
        await call('minecraft_control',{action:'set_mode',mode:'READ'});
      }
    }
    const held=await call('minecraft_get_state',{kind:'input'});assert.equal(held.pressedKeyCount,0);assert.equal(held.pressedButtonCount,0);
    return checks;
  } finally {await client.close().catch(()=>{});await transport.close().catch(()=>{});}
}
