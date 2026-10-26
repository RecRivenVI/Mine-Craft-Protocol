import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

// Only the restored runner-owned world may be changed. A counter is not a write grant.
export async function resourceVersionActions({api,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples,setArm,repo,instance,target,enterWorld=false}) {
  if(enterWorld) {
    await acquire();await click('menu.singleplayer','Singleplayer');
    const tree=await wait(t=>t.screenClass.endsWith('SelectWorldScreen'),'select resource-version fixture');
    const world=nodes(tree).find(n=>n.worldName==='World-Read-Alpha');assert(world);
    await clickNode(world);await click('selectWorld.select','Play Selected World');
    await wait(t=>!t.screenClass,'resource-version fixture entered',150000);await release();
  }
  const player=await api('GET','/v0/player?category=DEBUG');
  const at={x:Math.floor(player.x)+4,y:Math.floor(player.y)+2,z:Math.floor(player.z)+4};
  const fingerprint=(await api('GET','/v0/world/fingerprint?category=DEBUG')).worldFingerprint;
  const beforeInput=await api('GET','/v0/input/state');
  const query={category:'DEBUG',perspective:'server_authoritative',domains:['blocks'],selector:{blocks:[at]}};
  async function inspect() {
    const snapshot=await api('POST','/v0/observe/deep',query),state=snapshot.server.blocks[0];
    assert(state);assert.equal(state.available,true);assert.equal(state.loadRequested,false);
    const ref=snapshot.resourceRevisionRefs.find(r=>r.resourceType==='block'&&r.resourceKey===`${snapshot.server.dimension}@${state.key}`);
    assert(ref,JSON.stringify(snapshot.resourceRevisionRefs));assert.equal(ref.revisionScope,'resource');assert.equal(ref.mutationPreconditionEligible,true);
    return {state,ref};
  }
  const original=await inspect();assert.equal(original.state.blockId,'minecraft:air','Only restored fixture air is used');
  const body=(source,blockId)=>({operation:'world.block.set',worldFingerprint:fingerprint,...at,expectedResourceVersion:source.ref,expectedBlockId:source.state.blockId,expectedProperties:source.state.properties,blockId});
  async function arm(namespaces=['world']) {setArm((await api('POST','/v0/debug/arm',{worldFingerprint:fingerprint,namespaces,ttlMs:60000})).debugArmId);}
  let lastMutation=0,ownedBlockVersion,ownedBlockState;
  async function mutate(request,error) {
    const pause=650-(Date.now()-lastMutation);if(pause>0)await new Promise(r=>setTimeout(r,pause));lastMutation=Date.now();
    return api('POST','/v0/debug/mutations',request,error);
  }
  function noCounter(error) {
    for(const field of ['actualRevision','expectedRevision','currentResourceVersion','resourceVersion'])assert.equal(field in error,false,JSON.stringify(error));
    assert.equal(error.errorDetails?.actualRevision,undefined);
  }
  function staleMetadata(error,expected,actual) {
    assert.equal(error.actualRevision,actual);assert.equal(error.expectedRevision,expected);
    assert.equal(error.reinspectRequired,true);assert.equal(error.retrySafe,false);
    for(const field of ['currentResourceVersion','resourceVersion','before','after','rawNbt','serializedState'])assert.equal(field in error,false);
  }
  try {
    await check('formal Debug capability discovers current inspection selectors without granting mutation',async()=>{
      const capability=await api('GET','/v0/debug/capabilities'),contract=capability.resourceInspection;
      assert(contract,JSON.stringify(capability));assert.equal(contract.path,'/v0/observe/deep');assert.equal(contract.resourceVersionsField,'resourceRevisionRefs');
      assert.equal(contract.mutationPreconditionField,'expectedResourceVersion');assert.equal(contract.inspectionRequiresArm,false);
      assert.equal(contract.grantsMutationPermission,false);assert.equal(contract.readWriteAtomic,false);assert.equal(contract.automaticRetry,false);
      assert.equal(contract.operations.length,24);
      assert.deepEqual(contract.operations.map(o=>o.operation).sort(),['player.vehicle.set','entity.vehicle.set','entity.leash.set','entity.owner.set','world.time.set','world.weather.set','world.rule.set','entity.spawn','entity.remove','player.health.set','player.state.set','entity.state.set','player.attribute.set','entity.attribute.set','entity.equipment.set','player.effect.set','entity.effect.set','entity.no_gravity.set','world.block.set','block_entity.custom_name.set','menu.slot.set','player.inventory.slot.set','player.equipment.set','block_entity.slot.set'].sort());
      const entry=contract.operations.find(o=>o.operation==='world.block.set');assert.equal(entry.resourceType,original.ref.resourceType);assert.deepEqual(entry.targetFields,['x','y','z']);
      actionSamples.push({case:'resource_inspection_contract',contract,inspected:original});
    });
    await check('READ inspection is useful but does not grant write mode or Arm',async()=>{
      await mode('READ');const inspected=await inspect();assert.deepEqual(inspected.ref,original.ref);
      await mutate(body(inspected,'minecraft:stone'),'DEBUG_NOT_ARMED');
      await arm();await mutate(body(inspected,'minecraft:stone'),'OPERATE_REQUIRED');
      assert.deepEqual((await inspect()).state,original.state);
    });
    await mode('OPERATE');await arm();
    let stale;
    await check('typed mutation directly accepts the formal inspected token and advances revision',async()=>{
      stale=await inspect();const result=await mutate(body(stale,'minecraft:stone'));
      ownedBlockVersion=result.afterResourceVersion;ownedBlockState=result.after;
      assert.equal(result.beforeResourceVersion.revision,stale.ref.revision);assert(result.afterResourceVersion.revision>stale.ref.revision);
      assert.equal(result.gameplayEvidence,false);assert.equal(result.evidenceContaminated,true);assert.equal(result.storageAccessed,false);
      assert.equal((await inspect()).state.blockId,'minecraft:stone');
      actionSamples.push({case:'inspect_to_typed_mutation',result,evidenceAuthority:'DEBUG_PRIVILEGED'});
    });
    await check('stale typed token yields safe counters and requires reinspection with zero mutation',async()=>{
      const before=await inspect(),denied=await mutate(body(stale,'minecraft:dirt'),'STALE_RESOURCE_REVISION');
      staleMetadata(denied,stale.ref.revision,before.ref.revision);assert.deepEqual(await inspect(),before);
      const revised=await inspect();assert.equal(revised.ref.revision,denied.actualRevision);
      const applied=await mutate(body(revised,'minecraft:dirt'));assert.equal(applied.beforeResourceVersion.revision,revised.ref.revision);
      ownedBlockVersion=applied.afterResourceVersion;ownedBlockState=applied.after;
      actionSamples.push({case:'safe_stale_reinspect',denied,reinspected:revised.ref,applied});
    });
    await check('scope Arm world epoch and resource identity failures never expose actual revision',async()=>{
      const before=await inspect(),request=body(stale,'minecraft:stone');
      await api('POST','/v0/debug/disarm');setArm(undefined);
      noCounter(await mutate(request,'DEBUG_NOT_ARMED'));await arm(['player']);
      noCounter(await mutate(request,'DEBUG_SCOPE_DENIED'));await arm();
      noCounter(await mutate({...request,worldFingerprint:'0'.repeat(64)},'WORLD_FINGERPRINT_MISMATCH'));await arm();
      for(const [field,value,code] of [
        ['sessionEpoch','previous-runtime','STALE_SESSION_EPOCH'],
        ['resourceType','entity','RESOURCE_MISMATCH'],
        ['resourceKey',`${before.ref.resourceKey}-other`,'RESOURCE_MISMATCH'],
        ['lifecycleId','other-instance','RESOURCE_MISMATCH'],
        ['mutationPreconditionEligible',false,'RESOURCE_VERSION_NOT_PRECONDITION_ELIGIBLE']
      ])noCounter(await mutate({...request,expectedResourceVersion:{...request.expectedResourceVersion,[field]:value}},code));
      assert.deepEqual(await inspect(),before);
    });
    await check('fresh revision never bypasses an explicit typed value condition',async()=>{
      const current=await inspect();const denied=await mutate({...body(current,'minecraft:stone'),expectedBlockId:'minecraft:air'},'VALUE_PRECONDITION_FAILED');
      noCounter(denied);assert.deepEqual(await inspect(),current);
      const malformed=await mutate({...body(current,'minecraft:stone'),expectedResourceVersion:{...current.ref,revision:String(current.ref.revision)}},'INVALID_RESOURCE_VERSION');
      noCounter(malformed);assert.deepEqual(await inspect(),current);
    });
    await check('actual MCP keeps inspect tokens and safe stale fields through structured errors',async()=>{
      const require=createRequire(resolve(repo,'components/companion/package.json'));
      const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
      const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
      const env=Object.fromEntries(Object.entries(process.env).filter(([,v])=>typeof v==='string'));
      for(const name of ['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE'])delete env[name];
      env.MCP_MINECRAFT_INSTANCE_DIR=instance;env.MCP_MINECRAFT_TARGET=target;
      const client=new Client({name:'resource-version-consumer',version:'1.0.0'}),transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
      try {
        await client.connect(transport);
        const armed=await client.callTool({name:'minecraft_debug_arm',arguments:{action:'arm',worldFingerprint:fingerprint,namespaces:['world'],ttlMs:60000}});assert(!armed.isError,JSON.stringify(armed));setArm(armed.structuredContent.data.debugArmId);
        const observed=await client.callTool({name:'minecraft_deep_observe',arguments:query});assert(!observed.isError,JSON.stringify(observed));
        const snapshot=observed.structuredContent.data,ref=snapshot.resourceRevisionRefs.find(r=>r.resourceType==='block');assert(ref?.mutationPreconditionEligible);
        const result=await client.callTool({name:'minecraft_debug',arguments:{action:'mutate',mutation:body(stale,'minecraft:stone')}});
        assert(result.isError,JSON.stringify(result));const error=result.structuredContent.error;
        assert.equal(error.code,'STALE_RESOURCE_REVISION');staleMetadata(error,stale.ref.revision,ref.revision);
        actionSamples.push({case:'resource_version_actual_mcp',error,inspectedRef:ref});
      }finally {await client.close();await transport.close();}
    });
  }finally {
    await mode('OPERATE');await arm();
    try {
      const current=await inspect();if(JSON.stringify(current.state)!==JSON.stringify(original.state)){assert(ownedBlockVersion&&ownedBlockState,'No confirmed owned block change; preserve instead of cleanup');assert.deepEqual(current.ref,ownedBlockVersion,'Block changed externally; preserve instead of cleanup');assert.deepEqual(current.state,ownedBlockState,'Unknown block state; preserve instead of cleanup');await mutate({...body(current,original.state.blockId),properties:original.state.properties});}
      assert.deepEqual((await inspect()).state,original.state);
    }finally {await api('POST','/v0/debug/disarm');setArm(undefined);await mode('READ');}
  }
  await check('resource inspection cleanup preserves held-zero no-Lease and zero input dispatch',async()=>{
    const after=await api('GET','/v0/input/state');assert.equal(after.pressedKeyCount,0);assert.equal(after.pressedButtonCount,0);
    assert.equal(after.inputDispatchSequence,beforeInput.inputDispatchSequence);assert.equal((await api('GET','/v0/control/status')).status,'available');
  });
  await extendedResourceVersionActions({api,mode,acquire,release,wait,check,actionSamples,setArm,repo,instance,target});
}

async function extendedResourceVersionActions({api,mode,acquire,release,wait,check,actionSamples,setArm,repo,instance,target}) {
  const actor=await api('GET','/v0/player?category=DEBUG');
  const at={x:Math.floor(actor.x)+6,y:Math.floor(actor.y)+2,z:Math.floor(actor.z)+4};
  const entityAt={x:Math.floor(actor.x)+3.5,y:Math.floor(actor.y)+2,z:Math.floor(actor.z)+0.5};
  const tag='mcp_rv_'+randomUUID().replaceAll('-','');
  const fingerprint=(await api('GET','/v0/world/fingerprint?category=DEBUG')).worldFingerprint;
  const arrangeInput=await api('GET','/v0/input/state');
  let armId,lastMutation=0,entityUuid,entityArrangeAttempted=false,pauseOpened=false,primary;
  const oldIds=new Set();
  const cleanupErrors=[];
  const pause=ms=>new Promise(r=>setTimeout(r,ms));
  async function poll(read,predicate,label) {
    const end=Date.now()+5000;let value;
    do {value=await read();if(predicate(value))return value;await pause(50);}while(Date.now()<end);
    throw new Error(`${label}: ${JSON.stringify(value)}`);
  }
  async function inspect(domains,selector={},extra={}) {
    return api('POST','/v0/observe/deep',{category:'DEBUG',perspective:'server_authoritative',domains,selector,...extra});
  }
  function select(snapshot,type,key,state) {
    const ref=snapshot.resourceRevisionRefs.find(r=>r.resourceType===type&&r.resourceKey===key);
    assert(state,`Missing ${type} state ${key}`);assert(ref,`Missing ${type} token ${key}`);
    assert.equal(ref.mutationPreconditionEligible,true);assert.equal(ref.revisionScope,'resource');return {state,ref};
  }
  async function player() {const s=await inspect(['player']);return select(s,'player',`${s.server.player.uuid}@server_authoritative`,s.server.player);}
  async function menu() {const s=await inspect(['menu']);return select(s,'menu',`${s.server.menu.ownerUuid}@menu:${s.server.menu.menuId}`,s.server.menu);}
  async function entity() {const s=await inspect(['entities'],{entityRadius:16});return select(s,'entity',entityUuid,s.server.entities.find(e=>e.uuid===entityUuid));}
  async function block() {const s=await inspect(['blocks'],{blocks:[at]});return select(s,'block',`${s.server.dimension}@${at.x},${at.y},${at.z}`,s.server.blocks[0]);}
  async function blockEntity() {const s=await inspect(['block_entities'],{chunkRadius:1},{includeSerializedBlockEntities:true});return select(s,'block_entity_serialized',`${s.server.dimension}@${at.x},${at.y},${at.z}`,s.server.blockEntities.find(e=>e.key===`${at.x},${at.y},${at.z}`));}
  async function arm() {
    armId=(await api('POST','/v0/debug/arm',{worldFingerprint:fingerprint,namespaces:['world','player','entity','block_entity','menu'],ttlMs:60000})).debugArmId;
    setArm(armId);
  }
  async function paced() {const delay=650-(Date.now()-lastMutation);if(delay>0)await pause(delay);lastMutation=Date.now();}
  async function mutate(body,error) {await paced();return api('POST','/v0/debug/mutations',body,error);}
  function request(operation,source,fields) {return {operation,worldFingerprint:fingerprint,expectedResourceVersion:source.ref,...fields};}
  async function zero() {const s=await api('GET','/v0/input/state');assert.equal(s.pressedKeyCount,0);assert.equal(s.pressedButtonCount,0);assert.equal((await api('GET','/v0/control/status')).status,'available');return s;}
  async function native(command) {const receipt=await api('POST','/v0/command/player',{command,route:'NATIVE'});assert.equal(receipt.route,'NATIVE');assert.equal(receipt.inputDispatched,false);return receipt;}
  function metadata(error,expected,actual) {assert.equal(error.actualRevision,actual);assert.equal(error.expectedRevision,expected);assert.equal(error.reinspectRequired,true);assert.equal(error.retrySafe,false);assert.equal(error.currentResourceVersion,undefined);}
  async function chain({operation,read,value,changed,build,wrong,consumer,onApplied}) {
    await arm();const inputBefore=await zero(),original=await read();let failure,changedVersion;
    function ownedChange(current) {
      assert.deepEqual(value(current.state),changed,`${operation}: unknown external value; preserve instead of cleanup`);
      assert(changedVersion,`${operation}: no confirmed mutation receipt; preserve instead of cleanup`);
      assert.deepEqual(current.ref,changedVersion,`${operation}: token changed outside this mutation; preserve instead of cleanup`);
    }
    try {
      const applied=await mutate(build(original,changed));
      changedVersion=applied.afterResourceVersion;onApplied?.(applied);
      assert.equal(applied.beforeResourceVersion.revision,original.ref.revision);assert(applied.afterResourceVersion.revision>original.ref.revision);
      assert.equal(applied.gameplayEvidence,false);assert.equal(applied.evidenceContaminated,true);assert.equal(applied.storageAccessed,false);
      const after=await read();assert.deepEqual(value(after.state),changed);
      const stale=await mutate(build(original,value(original.state)),'STALE_RESOURCE_REVISION');metadata(stale,original.ref.revision,after.ref.revision);
      assert.deepEqual(await read(),after,'Stale rejection changed the target resource');
      const fresh=await read();const bad=await mutate({...build(fresh,value(original.state)),...wrong(fresh)},'VALUE_PRECONDITION_FAILED');assert.equal(bad.actualRevision,undefined);assert.deepEqual(await read(),fresh);
      const beforeRestore=await read();ownedChange(beforeRestore);
      const restored=await mutate(build(beforeRestore,value(original.state)));const final=await read();assert.deepEqual(value(final.state),value(original.state));
      const inputAfter=await zero();assert.equal(inputAfter.inputDispatchSequence,inputBefore.inputDispatchSequence);
      actionSamples.push({case:'resource_version_typed_chain',operation,authority:'DEBUG_PRIVILEGED',mechanism:applied.mechanism,inspectionSource:'formal_deep_observation',original:original.ref,applied,stale,reinspected:fresh.ref,restored,inputBefore,inputAfter,actInputDelta:0,consumer:consumer??'native_http'});
    }catch(error) {failure=error;throw error;}
    finally {
      try {
        const current=await read();if(JSON.stringify(value(current.state))!==JSON.stringify(value(original.state))){ownedChange(current);await mutate(build(current,value(original.state)));}
        assert.deepEqual(value((await read()).state),value(original.state));
      }catch(error) {if(failure){cleanupErrors.push(`${operation}: ${error}`);}else throw error;}
    }
  }
  try {
    await mode('OPERATE');
    const originalEntities=await inspect(['entities'],{entityRadius:16});
    assert.equal(originalEntities.server.entities.some(e=>e.type==='minecraft:armor_stand'&&Math.abs(e.x-entityAt.x)<0.1&&Math.abs(e.z-entityAt.z)<0.1),false,'Unexpected preexisting entity at owned fixture position');
    originalEntities.server.entities.forEach(e=>oldIds.add(e.uuid));entityArrangeAttempted=true;
    const summon=await native(`summon minecraft:armor_stand ${entityAt.x} ${entityAt.y} ${entityAt.z} {NoGravity:1b,Invulnerable:1b,Invisible:1b,Tags:["${tag}"]}`);
    const spawned=await poll(()=>inspect(['entities'],{entityRadius:16}),s=>s.server.entities.some(e=>!oldIds.has(e.uuid)&&e.type==='minecraft:armor_stand'&&Math.abs(e.x-entityAt.x)<0.1&&Math.abs(e.z-entityAt.z)<0.1),'owned armor stand spawned');
    const matches=spawned.server.entities.filter(e=>!oldIds.has(e.uuid)&&e.type==='minecraft:armor_stand'&&Math.abs(e.x-entityAt.x)<0.1&&Math.abs(e.z-entityAt.z)<0.1);assert.equal(matches.length,1);entityUuid=matches[0].uuid;
    assert.equal(matches[0].noGravity,true);
    await acquire();
    const gesture=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:256,holdMs:40}],timeoutMs:12000});
    assert.equal((await api('POST',`/v0/operations/${gesture.operationId}/wait`,{timeoutMs:12000})).state,'completed');
    await wait(t=>t.screenClass.endsWith('PauseScreen'),'native Pause Screen stabilizes diagnostic fixture');pauseOpened=true;await release();
    await mode('OPERATE');await arm();
    const settled=await player();await pause(180);assert.deepEqual(await player(),settled,'Native pause did not stabilize the inspected player; do not retry stale writes');
    const arrangeAfter=await zero();actionSamples.push({case:'resource_version_fixture_arrange',authority:'diagnostic_user_command_and_game_routed_pause',summon,entityUuid,entityAt,uniqueTag:tag,arrangeInputBefore:arrangeInput,arrangeInputAfter:arrangeAfter,arrangeInputDelta:arrangeAfter.inputDispatchSequence-arrangeInput.inputDispatchSequence,nativePauseObserved:true,ownerThreadReadsWhilePaused:true});
    await check('player health formal inspect mutation stale reinspection and recovery without input Lease',async()=>{
      const p=await player();assert(p.state.health>2);const changed=p.state.health-1;
      await chain({operation:'player.health.set',read:player,value:s=>s.health,changed,build:(s,value)=>request('player.health.set',s,{health:value,expectedHealth:s.state.health}),wrong:s=>({expectedHealth:s.state.health+1})});
    });
    await check('player native max-health attribute read alias maps to typed inspect revision and recovery',async()=>{
      const value=s=>{const match=s.attributes.filter(a=>['minecraft:max_health','minecraft:generic.max_health'].includes(a.id));assert.equal(match.length,1);return match[0].base;};
      const p=await player();const nativeAttribute=p.state.attributes.find(a=>['minecraft:max_health','minecraft:generic.max_health'].includes(a.id));
      await chain({operation:'player.attribute.set',read:player,value,changed:value(p.state)+0.25,build:(s,v)=>request('player.attribute.set',s,{attributeId:'minecraft:max_health',value:v,expectedValue:value(s.state)}),wrong:s=>({expectedValue:value(s.state)+1})});
      actionSamples.push({case:'resource_version_attribute_mapping',readAttributeId:nativeAttribute.id,writeAttributeId:'minecraft:max_health',mappingSource:'native_registry_attribute_snapshot'});
    });
    await check('loaded entity no-gravity lifecycle token changes and recovers with safe stale metadata',async()=>{
      await chain({operation:'entity.no_gravity.set',read:entity,value:s=>s.noGravity,changed:false,build:(s,value)=>request('entity.no_gravity.set',s,{entityUuid,value,expectedNoGravity:s.state.noGravity,expectedEntityType:s.state.type}),wrong:s=>({expectedNoGravity:!s.state.noGravity})});
    });
    await check('serialized container Block Entity token supports typed custom name mutation and recovery',async()=>{
      await arm();const original=await block();assert.equal(original.state.blockId,'minecraft:air','Only restored owned empty air may become the container fixture');
      let failure,placementVersion,originalSerialized,changedSerialized;
      try {
        const placement=await mutate(request('world.block.set',original,{...at,blockId:'minecraft:chest',expectedBlockId:'minecraft:air',expectedProperties:original.state.properties}));placementVersion=placement.afterResourceVersion;
        const be=await blockEntity();assert.equal(be.state.type,'minecraft:chest');
        originalSerialized=be.state.serializedState;
        const changed='ResourceVersionFixture';
        // The empty newly placed native container is unnamed; the native typed value guard
        // independently proves before/after names without interpreting version-specific component NBT.
        const name=s=>{if(JSON.stringify(s.serializedState)===JSON.stringify(originalSerialized))return null;assert(changedSerialized&&JSON.stringify(s.serializedState)===JSON.stringify(changedSerialized),'Unknown external container contents; preserve instead of cleanup');return changed;};
        await chain({operation:'block_entity.custom_name.set',read:blockEntity,value:name,changed,build:(s,v)=>request('block_entity.custom_name.set',s,{...at,customName:v,expectedCustomName:name(s.state),expectedBlockEntityType:s.state.type}),wrong:()=>({expectedCustomName:'not-the-native-name'}),onApplied:result=>{changedSerialized=result.after.serializedState;assert(changedSerialized);}});
        assert.deepEqual((await blockEntity()).state.serializedState,originalSerialized);
      }catch(error){failure=error;throw error;}
      finally {
        try {const current=await block();if(current.state.blockId!==original.state.blockId){assert.equal(current.state.blockId,'minecraft:chest','Unknown replacement block; preserve instead of cleanup');assert(placementVersion&&originalSerialized,'No confirmed owned empty container; preserve instead of cleanup');assert.deepEqual(current.ref,placementVersion,'Container block changed externally; preserve instead of cleanup');assert.deepEqual((await blockEntity()).state.serializedState,originalSerialized,'Container data changed externally; preserve instead of cleanup');await mutate(request('world.block.set',current,{...at,blockId:original.state.blockId,properties:original.state.properties,expectedBlockId:current.state.blockId,expectedProperties:current.state.properties}));}assert.deepEqual((await block()).state,original.state);}
        catch(error){if(failure)cleanupErrors.push(`container block: ${error}`);else throw error;}
      }
    });
    let safeSlot;
    await check('active server Menu slot token owns the full Menu and restores a safe inventory slot',async()=>{
      const initial=await menu();assert.equal(initial.state.menuId,0);safeSlot=initial.state.slots.find(s=>s.slot>=9&&s.slot<=35&&s.empty);assert(safeSlot,'Owned player inventory must have an empty ordinary slot');
      const slot=s=>s.slots.find(n=>n.slot===safeSlot.slot);const value=s=>({id:slot(s).id,count:slot(s).count});
      await chain({operation:'menu.slot.set',read:menu,value,changed:{id:'minecraft:stone',count:7},build:(s,v)=>request('menu.slot.set',s,{slot:safeSlot.slot,itemId:v.id,count:v.count,expectedMenuId:s.state.menuId,expectedItemId:slot(s.state).id,expectedCount:slot(s.state).count}),wrong:s=>({expectedCount:slot(s.state).count+1})});
    });
    await check('actual MCP Menu inspect mutation stale reinspection and restoration retains structured counters',async()=>{
      await arm();const inputBefore=await zero();
      const require=createRequire(resolve(repo,'components/companion/package.json'));
      const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
      const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
      const env=Object.fromEntries(Object.entries(process.env).filter(([,v])=>typeof v==='string'));
      for(const name of ['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE'])delete env[name];env.MCP_MINECRAFT_INSTANCE_DIR=instance;env.MCP_MINECRAFT_TARGET=target;
      const client=new Client({name:'resource-version-menu-consumer',version:'1.0.0'}),transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
      const call=async(name,args,error)=>{const r=await client.callTool({name,arguments:args});if(error){assert(r.isError,JSON.stringify(r));assert.equal(r.structuredContent.error.code,error);return r.structuredContent.error;}assert(!r.isError,JSON.stringify(r));return r.structuredContent.data;};
      const read=async()=>{const s=await call('minecraft_deep_observe',{category:'DEBUG',perspective:'server_authoritative',domains:['menu']});return select(s,'menu',`${s.server.menu.ownerUuid}@menu:${s.server.menu.menuId}`,s.server.menu);};
      const make=(s,count)=>request('menu.slot.set',s,{slot:safeSlot.slot,itemId:count?'minecraft:stone':'minecraft:air',count,expectedMenuId:s.state.menuId,expectedItemId:s.state.slots.find(n=>n.slot===safeSlot.slot).id,expectedCount:s.state.slots.find(n=>n.slot===safeSlot.slot).count});
      let original,failure,changedVersion,changedSlot;
      function ownedSlot(current) {
        assert(changedVersion&&changedSlot,'No confirmed MCP item mutation; preserve instead of cleanup');
        assert.equal(changedSlot.id,'minecraft:stone');assert.equal(changedSlot.count,5);
        assert.deepEqual(current.state.slots.find(n=>n.slot===safeSlot.slot),changedSlot,'Unknown MCP slot contents; preserve instead of cleanup');
        assert.deepEqual(current.ref,changedVersion,'Menu changed outside this MCP mutation; preserve instead of cleanup');
      }
      try {
        await client.connect(transport);const armed=await call('minecraft_debug_arm',{action:'arm',worldFingerprint:fingerprint,namespaces:['menu'],ttlMs:60000});armId=armed.debugArmId;setArm(armId);
        original=await read();assert.equal(original.state.slots.find(n=>n.slot===safeSlot.slot).empty,true);
        await paced();const applied=await call('minecraft_debug',{action:'mutate',mutation:make(original,5)});changedVersion=applied.afterResourceVersion;changedSlot=applied.after.slots.find(n=>n.slot===safeSlot.slot);assert(applied.afterResourceVersion.revision>original.ref.revision);
        const after=await read();assert.equal(after.state.slots.find(n=>n.slot===safeSlot.slot).count,5);
        await paced();const stale=await call('minecraft_debug',{action:'mutate',mutation:make(original,0)},'STALE_RESOURCE_REVISION');metadata(stale,original.ref.revision,after.ref.revision);
        const fresh=await read();ownedSlot(fresh);await paced();const restored=await call('minecraft_debug',{action:'mutate',mutation:make(fresh,0)});assert.equal((await read()).state.slots.find(n=>n.slot===safeSlot.slot).empty,true);
        const inputAfter=await zero();assert.equal(inputAfter.inputDispatchSequence,inputBefore.inputDispatchSequence);
        actionSamples.push({case:'resource_version_menu_actual_mcp',operation:'menu.slot.set',consumer:'mcp_stdio',authority:'DEBUG_PRIVILEGED',original:original.ref,applied,stale,reinspected:fresh.ref,restored,inputBefore,inputAfter,actInputDelta:0});
      }catch(error){failure=error;throw error;}
      finally {
        try {if(original){const current=await read();if(!current.state.slots.find(n=>n.slot===safeSlot.slot).empty){ownedSlot(current);await paced();await call('minecraft_debug',{action:'mutate',mutation:make(current,0)});}}}
        catch(error){if(failure)cleanupErrors.push(`MCP menu: ${error}`);else throw error;}
        finally {await client.close();await transport.close();}
      }
    });
  }catch(error){primary=error;throw error;}
  finally {
    try {await api('POST','/v0/debug/disarm');setArm(undefined);}catch(error){cleanupErrors.push(`disarm: ${error}`);}
    if(pauseOpened){
      try {
        await acquire();const operation=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:256,holdMs:40}],timeoutMs:12000});
        assert.equal((await api('POST',`/v0/operations/${operation.operationId}/wait`,{timeoutMs:12000})).state,'completed');await wait(t=>!t.screenClass,'native diagnostic Pause Screen closed');await release();
      }catch(error){cleanupErrors.push(`unpause: ${error}`);}
    }
    try {
      await mode('OPERATE');if(entityArrangeAttempted){const ownedTarget=entityUuid??`@e[type=minecraft:armor_stand,tag=${tag},x=${entityAt.x-1},y=${entityAt.y-1},z=${entityAt.z-1},dx=2,dy=2,dz=2,limit=1]`;await native(`kill ${ownedTarget}`);await poll(()=>inspect(['entities'],{entityRadius:16}),s=>!s.server.entities.some(e=>e.uuid===entityUuid||(!oldIds.has(e.uuid)&&e.type==='minecraft:armor_stand'&&Math.abs(e.x-entityAt.x)<0.1&&Math.abs(e.z-entityAt.z)<0.1)),'owned armor stand removed');}
      await mode('READ');await zero();
    }catch(error){cleanupErrors.push(`entity cleanup: ${error}`);}
    actionSamples.push({case:'resource_version_extended_cleanup',entityUuid,entityArrangeAttempted,entityRemoved:entityArrangeAttempted&&cleanupErrors.length===0,restoredReadMode:cleanupErrors.length===0,cleanupErrors:[...cleanupErrors],persistentWriteCalls:0});
    if(cleanupErrors.length){if(primary)primary.message+=`; cleanup: ${cleanupErrors.join('; ')}`;else throw new Error(`Resource version cleanup failed: ${cleanupErrors.join('; ')}`);}
  }
}
