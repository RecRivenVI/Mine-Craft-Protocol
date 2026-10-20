import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

// Permission changes are explicit probe Arrange, not a USER action or a claim of human cooperation.
export async function commandCollaborationActions({api,repo,instance,target,fixtureInput,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples}) {
  assert.equal(fixtureInput?.policy,'immutable_baseline_restored');
  assert.equal(fixtureInput.target,target);
  const started=Date.now(),path='/v0/command/player',samples=[];
  let leased=false,probeOpen=false,permissionMayBeRevoked=false;
  const pause=()=>new Promise(done=>setTimeout(done,50));
  async function clean(){const value=await api('GET','/v0/input/state');assert.equal(value.pressedKeyCount,0);assert.equal(value.pressedButtonCount,0);assert.equal(value.inputCleanupFailed,false);return value;}
  async function take(){assert.equal((await api('GET','/v0/control/mode')).reconsentRequired,false,'No automatic reacquire after human override');await acquire();leased=true;}
  async function handBack(){if(leased){await release();leased=false;}}
  async function button(label){const tree=await api('GET','/v0/ui/tree'),found=nodes(tree).filter(value=>value.label===label&&value.visible!==false&&value.active!==false);assert.equal(found.length,1,label);await clickNode(found[0]);}
  async function marker(){
    const attemptId=randomUUID().replaceAll('-','');
    const receipt=await api('POST',path,{command:'help Mine-Craft-Protocol_command_collaboration_'+attemptId,route:'NATIVE'});
    assert.equal(receipt.route,'NATIVE');assert.equal(receipt.inputDispatched,false);assert.equal(receipt.effectConfirmed,false);
    const end=Date.now()+5000;let value;
    do{
      try{value=JSON.parse(await readFile(resolve(repo,'validations/conformance-world_read/result',target+'-command-collaboration-probe.json'),'utf8'));}
      catch(error){if(error.code!=='ENOENT'&&!(error instanceof SyntaxError))throw error;}
      if(value?.attemptId===attemptId){
        assert.equal(value.probeOnly,true);assert.equal(value.permissionArrange,'probe_server_profile_permissions_override');
        assert.equal(value.operatorListChanged,false);assert.equal(value.worldMetadataChanged,false);assert.equal(value.originalGamemaster,true);
        assert(Number.isSafeInteger(value.nativePlayerCommands));return value;
      }
      await pause();
    }while(Date.now()<end);
    throw new Error('Native server FIFO command marker not observed: '+JSON.stringify(value));
  }
  async function commandTree(expected){
    const end=Date.now()+5000;let value;
    do{
      value=await api('POST',path,{action:'discover',input:'time query gametime',maxResults:128});
      if(value.roots.includes('time')===expected&&value.parsedExecutable===expected)return value;
      await pause();
    }while(Date.now()<end);
    throw new Error('Native permission-filtered command tree did not update: '+JSON.stringify(value));
  }
  async function change(revoked){
    await take();
    if(revoked)permissionMayBeRevoked=true;
    await button(revoked?'Revoke Commands (Fixture)':'Restore Commands (Fixture)');
    await wait(tree=>nodes(tree).some(value=>value.label===(revoked?'Commands Revoked (Fixture)':'Commands Restored (Fixture)')),'server permission fixture callback completed',5000);
    if(!revoked)permissionMayBeRevoked=false;
    await handBack();await mode('OPERATE');
  }
  try {
    await check('07d automatic setup enters only the restored fixed world and explicit permission fixture',async()=>{
      await take();await click('menu.singleplayer','Singleplayer');
      const tree=await wait(value=>value.screenClass.endsWith('SelectWorldScreen')&&nodes(value).some(node=>node.worldName==='World-Read-Alpha'),'command collaboration fixed world list ready');
      const worlds=nodes(tree).filter(value=>value.worldName==='World-Read-Alpha');assert.equal(worlds.length,1);
      await clickNode(worlds[0]);await click('selectWorld.select','Play Selected World');await wait(value=>!value.screenClass,'command collaboration fixed world entered',150000);
      await handBack();await mode('OPERATE');
      const fixture=await api('POST','/v0/diagnostics/ui/test-screen',{});assert.equal(fixture.evidenceContaminated,true);
      await wait(value=>value.screenClass.endsWith('AutomationProbeScreen'),'command collaboration probe screen');probeOpen=true;
      const controls=nodes(await api('GET','/v0/ui/tree'));
      for(const label of ['Revoke Commands (Fixture)','Restore Commands (Fixture)'])assert.equal(controls.filter(value=>value.label===label).length,1,'Launch requires -Dminecraft.protocol.commandCollaborationFixture=true');
      await clean();
    });
    await check('07d initial actual server authority and native tree admit a bounded read-only command',async()=>{
      const tree=await commandTree(true),inputBefore=await clean(),before=await marker();
      assert.equal(before.permissionRevoked,false);assert.equal(before.gamemasterAllowed,true);
      const receipt=await api('POST',path,{command:'time query gametime',route:'NATIVE'}),after=await marker();
      assert.equal(receipt.dataCategory,'USER');assert.equal(receipt.requiresControlLease,false);assert.equal(receipt.serverValidationObserved,false);
      assert.equal(after.nativePlayerCommands,before.nativePlayerCommands+2);
      const inputAfter=await clean();assert.equal(inputAfter.inputDispatchSequence,inputBefore.inputDispatchSequence);
      samples.push({case:'command_collaboration_initial_authority',tree,before,receipt,after,inputBefore,inputAfter,arrangeAuthority:'probe_only_server_profile_permission_fixture'});
    });
    await check('07d actual server permission loss resends the native tree and rejects before any command packet',async()=>{
      await change(true);const tree=await commandTree(false),inputBefore=await clean(),before=await marker();
      assert.equal(before.permissionRevoked,true);assert.equal(before.gamemasterAllowed,false);assert.equal(before.nativePermissionTreeSends,1);
      const denied=await api('POST',path,{command:'time query gametime',route:'NATIVE'},'PLAYER_COMMAND_NOT_AVAILABLE');
      const after=await marker(),inputAfter=await clean();assert.equal(after.nativePlayerCommands,before.nativePlayerCommands+1,'Only the subsequent permitted help marker reached the server');
      assert.equal(inputAfter.inputDispatchSequence,inputBefore.inputDispatchSequence);
      assert.equal((await api('GET','/v0/control/status')).status,'available');
      samples.push({case:'command_collaboration_permission_revoked',tree,before,denied,after,inputBefore,inputAfter,serverPermissionChangedBy:'probe_only_native_server_source_override',clientCommandTreeForged:false});
    });
    await check('07d partial native batch does not reuse the permission that preceded revocation',async()=>{
      const before=await marker(),inputBefore=await clean();
      const batch=await api('POST',path,{action:'batch',commands:['help','time query gametime','help']});
      assert.equal(batch.submittedCount,1);assert.equal(batch.stoppedAtCommandIndex,1);
      assert.equal(batch.commands[0].status,'submitted_unconfirmed');assert.equal(batch.commands[1].error.code,'PLAYER_COMMAND_NOT_AVAILABLE');assert.equal(batch.commands[2].status,'not_attempted');
      assert.equal(batch.rollbackAttempted,false);assert.equal(batch.retrySafe,false);
      const after=await marker(),inputAfter=await clean();assert.equal(after.nativePlayerCommands,before.nativePlayerCommands+2,'Only first help plus subsequent marker reached the server');
      assert.equal(inputAfter.inputDispatchSequence,inputBefore.inputDispatchSequence);
      samples.push({case:'command_collaboration_revoked_batch',before,batch,after,inputBefore,inputAfter});
    });
    await check('07d restoring the original server permission restores real discovery and submission',async()=>{
      await change(false);const tree=await commandTree(true),before=await marker(),inputBefore=await clean();
      assert.equal(before.permissionRevoked,false);assert.equal(before.gamemasterAllowed,true);assert.equal(before.nativePermissionTreeSends,2);
      const receipt=await api('POST',path,{command:'time query gametime',route:'NATIVE'}),after=await marker(),inputAfter=await clean();
      assert.equal(after.nativePlayerCommands,before.nativePlayerCommands+2);assert.equal(inputAfter.inputDispatchSequence,inputBefore.inputDispatchSequence);
      samples.push({case:'command_collaboration_permission_restored',tree,before,receipt,after,inputBefore,inputAfter});
    });
    await check('07d actual MCP uses the restored current player without Lease or UI input',async()=>{
      const require=createRequire(resolve(repo,'components/companion/package.json'));
      const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
      const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
      const env=Object.fromEntries(Object.entries(process.env).filter(([key,value])=>typeof value==='string'&&!['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE','MCP_MINECRAFT_INSTANCE_DIR'].includes(key.toUpperCase())));env.MCP_MINECRAFT_INSTANCE_DIR=instance;
      const client=new Client({name:'command-collaboration-conformance',version:'1.0.0'}),transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
      const expectedPackageVersion=JSON.parse(await readFile(resolve(repo,'components/companion/package.json'),'utf8')).version;
      const before=await marker(),inputBefore=await clean(),screenBefore=await api('GET','/v0/ui/tree');
      try {
        await client.connect(transport);const initializeServerInfo=client.getServerVersion();assert.equal(initializeServerInfo.version,expectedPackageVersion);
        const session=await client.callTool({name:'minecraft_get_session',arguments:{}});assert(!session.isError,JSON.stringify(session));assert.equal(session.structuredContent.data.target,target);
        const called=await client.callTool({name:'minecraft_execute_player_command',arguments:{command:'time query gametime',route:'NATIVE'}});assert(!called.isError,JSON.stringify(called));
        const receipt=called.structuredContent.data;assert.equal(receipt.route,'NATIVE');assert.equal(receipt.inputDispatched,false);assert.equal(receipt.requiresControlLease,false);
        const after=await marker(),inputAfter=await clean(),screenAfter=await api('GET','/v0/ui/tree');assert.equal(after.nativePlayerCommands,before.nativePlayerCommands+2);
        assert.equal(screenAfter.screenIdentity,screenBefore.screenIdentity);assert.equal(inputAfter.inputDispatchSequence,inputBefore.inputDispatchSequence);
        assert.equal((await api('GET','/v0/control/status')).status,'available');assert.equal((await api('GET','/v0/control/mode')).mode,'OPERATE');
        samples.push({case:'command_collaboration_mcp',transport:'real_mcp_stdio',initializeServerInfo,expectedPackageVersion,before,receipt,after,inputBefore,inputAfter,uiContextPreserved:true});
      }finally{await client.close();}
    });
  }finally{
    if(permissionMayBeRevoked){await change(false);await commandTree(true);}
    if(probeOpen){if(!leased)await take();await button('Close Probe');await wait(value=>!value.screenClass,'command collaboration probe closes');await handBack();}
    await mode('READ');const finalInput=await clean(),finalMode=await api('GET','/v0/control/mode');
    samples.push({case:'command_collaboration_complete',target,elapsedMillis:Date.now()-started,finalInput,finalMode,serverPermissionFixtureTested:true,operatorListChanged:false,worldMetadataChanged:false,humanCooperationTested:false,humanPermissionChangeTested:false,physicalEscTested:false,runtimeScopeHotRevocationTested:false,clientShutdownOwnedByOuterRunner:true,persistentWriteCalls:0});
    actionSamples.push(...samples);
  }
}
