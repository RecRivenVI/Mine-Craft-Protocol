import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

export const weatherValue=value=>Object.fromEntries(['clearWeatherTime','rainTime','thunderTime','raining','thundering'].map(key=>{assert(Object.hasOwn(value,key));return [key,value[key]];}));
export function environmentRef(snapshot,type,key){const result=snapshot.resourceRevisionRefs.find(ref=>ref.resourceType===type&&ref.resourceKey===key);assert(result?.mutationPreconditionEligible);return result;}

export async function worldDebugEnvironmentActions({api,target,repo,instance,fixtureInput,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples,setArm}) {
  assert.equal(fixtureInput?.policy,'immutable_baseline_restored');assert.equal(fixtureInput.target,target);
  let held=false,paused=false,fingerprint,lastWrite=0,failure;const undo=[],cleanupErrors=[],pause=ms=>new Promise(resolve=>setTimeout(resolve,ms));
  const deep=()=>api('POST','/v0/observe/deep',{category:'DEBUG',perspective:'server_authoritative',domains:['world']});
  const env=s=>s.server.world.environment;
  const clock=s=>{const c=env(s).clocks.find(c=>c.clockId==='minecraft:overworld');assert(c);return c;};
  const ref=(s,type,value)=>environmentRef(s,type,value.key);
  const base=(s,type,value)=>({worldFingerprint:fingerprint,expectedResourceVersion:ref(s,type,value)});
  const arm=async()=>setArm((await api('POST','/v0/debug/arm',{worldFingerprint:fingerprint,namespaces:['world'],ttlMs:60000})).debugArmId);
  async function mutate(body,error){const delay=650-(Date.now()-lastWrite);if(delay>0)await pause(delay);lastWrite=Date.now();return api('POST','/v0/debug/mutations',body,error);}
  async function tapEscape(){const p=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key:256,holdMs:40}],timeoutMs:12000});assert.equal((await api('POST',`/v0/operations/${p.operationId}/wait`,{timeoutMs:12000})).state,'completed');}
  async function input(){const s=await api('GET','/v0/input/state');assert.equal(s.pressedKeyCount,0);assert.equal(s.pressedButtonCount,0);assert.equal(s.inputCleanupPending,false);assert.equal(s.inputCleanupFailed,false);assert.equal((await api('GET','/v0/control/status')).status,'available');return s;}
  async function chain(type,select,operation,changed,targetFields={}) {
    await arm();const before=await deep(),original=select(before),inputBefore=await input(),originalValue=type==='world_time'?original.totalTicks:type==='world_weather'?weatherValue(original):original.value;
    const make=(snapshot,value,expectedValue)=>({operation,...base(snapshot,type,select(snapshot)),...targetFields,value,expectedValue});let applied;
    const restore=async()=>{const s=await deep(),current=select(s),value=type==='world_time'?current.totalTicks:type==='world_weather'?weatherValue(current):current.value;
      if(JSON.stringify(value)===JSON.stringify(originalValue))return;assert(applied);assert.deepEqual(ref(s,type,current),applied.afterResourceVersion,'Unknown external edit is preserved');assert.deepEqual(value,changed);
      await mutate(make(s,originalValue,changed));assert.deepEqual(type==='world_time'?select(await deep()).totalTicks:type==='world_weather'?weatherValue(select(await deep())):select(await deep()).value,originalValue);
    };undo.push(restore);
    applied=await mutate(make(before,changed,originalValue));assert.equal(applied.gameplayEvidence,false);assert.equal(applied.evidenceContaminated,true);assert.equal(applied.storageAccessed,false);
    const after=await deep();assert.deepEqual(ref(after,type,select(after)),applied.afterResourceVersion);assert.deepEqual(type==='world_time'?select(after).totalTicks:type==='world_weather'?weatherValue(select(after)):select(after).value,changed);
    const stale=await mutate(make(before,originalValue,changed),'STALE_RESOURCE_REVISION');assert.equal(stale.retrySafe,false);assert.equal(stale.actualRevision,applied.afterResourceVersion.revision);
    await restore();await mutate(make(before,changed,originalValue),'STALE_RESOURCE_REVISION');undo.pop();
    const inputAfter=await input();assert.equal(inputAfter.inputDispatchSequence,inputBefore.inputDispatchSequence);actionSamples.push({case:'world_environment_chain',operation,before:original,applied,after:select(after),stale,restored:select(await deep()),inputBefore,inputAfter,actInputDelta:0});
  }
  try {
    await acquire();held=true;await click('menu.singleplayer','Singleplayer');const list=await wait(t=>t.screenClass.endsWith('SelectWorldScreen'),'environment fixed world list');const worlds=nodes(list).filter(n=>n.worldName==='World-Read-Alpha');assert.equal(worlds.length,1);await clickNode(worlds[0]);await click('selectWorld.select','Play Selected World');await wait(t=>!t.screenClass,'environment world running');
    await tapEscape();await wait(t=>t.screenClass.endsWith('PauseScreen'),'paused native environment fixture');paused=true;await release();held=false;await mode('OPERATE');fingerprint=(await api('GET','/v0/world/fingerprint?category=DEBUG')).worldFingerprint;
    await check('native environment catalog keeps read effects independent authority and typed edit guards',async()=>{
      await mode('READ');const before=await deep();assert.equal(before.server.chunkLoadRequested,false);assert.equal(before.server.storageAccessed,false);assert.equal(env(before).storageAccessed,false);assert(env(before).rules.length>0);assert(env(before).clocks.length>0);
      const initial=clock(before),body={operation:'world.time.set',...base(before,'world_time',initial),clockId:initial.clockId,value:initial.totalTicks+1234};
      await mode('OPERATE');await mutate(body,'DEBUG_NOT_ARMED');await arm();await mode('READ');await mutate(body,'OPERATE_REQUIRED');await mode('OPERATE');
      for(const value of ['1234',1.5,{}])await mutate({...body,value},'INVALID_DEBUG_ENVIRONMENT');
      await mutate({...body,expectedValue:initial.totalTicks+1},'VALUE_PRECONDITION_FAILED');await mutate({...body,clockId:'minecraft:unknown_clock'},'INVALID_DEBUG_ENVIRONMENT');
      const weather=env(before).weather;await mutate({operation:'world.weather.set',...base(before,'world_weather',weather),value:{...weatherValue(weather),raining:'true'}},'INVALID_DEBUG_ENVIRONMENT');
      const bool=env(before).rules.find(r=>typeof r.value==='boolean'&&r.typedWritable);assert(bool);await mutate({operation:'world.rule.set',...base(before,'world_rule',bool),ruleId:bool.ruleId,value:1},'INVALID_DEBUG_ENVIRONMENT');
      const integer=env(before).rules.find(r=>typeof r.value==='number'&&r.typedWritable&&r.minimum>-2147483648)??env(before).rules.find(r=>typeof r.value==='number'&&r.typedWritable);assert(integer);
      const invalidInteger=integer.minimum>-2147483648?integer.minimum-1:2147483648;
      await mutate({operation:'world.rule.set',...base(before,'world_rule',integer),ruleId:integer.ruleId,value:invalidInteger},'INVALID_DEBUG_ENVIRONMENT');
      const after=await deep();assert.deepEqual(env(after),env(before));actionSamples.push({case:'world_environment_guards',before:env(before),after:env(after)});
    });
    await check('native clock total ticks edit and restore preserves explicit edit generations across ABA',async()=>{
      const initial=clock(await deep());await chain('world_time',clock,'world.time.set',initial.totalTicks+1234,{clockId:initial.clockId});
    });
    await check('native weather flags and independent countdown values restore without player input',async()=>{
      const original=env(await deep()).weather;await chain('world_weather',s=>env(s).weather,'world.weather.set',{clearWeatherTime:0,rainTime:6000,thunderTime:3000,raining:!original.raining,thundering:false});
    });
    await check('native available boolean and integer game rules use their actual argument domains and callbacks',async()=>{
      const rules=env(await deep()).rules;const bool=rules.find(r=>typeof r.value==='boolean'&&r.typedWritable&&/keep.?inventory/i.test(r.ruleId));assert(bool);
      await chain('world_rule',s=>env(s).rules.find(r=>r.ruleId===bool.ruleId),'world.rule.set',!bool.value,{ruleId:bool.ruleId});
      const integer=rules.find(r=>typeof r.value==='number'&&r.typedWritable&&/random.?tick.?speed/i.test(r.ruleId));assert(integer);await chain('world_rule',s=>env(s).rules.find(r=>r.ruleId===integer.ruleId),'world.rule.set',integer.value===1?2:1,{ruleId:integer.ruleId});
    });
    await check('actual MCP world time mutation retains native resource type and explicit restoration',async()=>{
      const require=createRequire(resolve(repo,'components/companion/package.json')),{Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href),{StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
      const client=new Client({name:'world-environment-consumer',version:'1.0.0'},{versionNegotiation:{mode:'legacy'}}),processEnv=Object.fromEntries(Object.entries(process.env).filter(([k,v])=>typeof v==='string'&&!/^MCP_MINECRAFT_/i.test(k)));processEnv.MCP_MINECRAFT_INSTANCE_DIR=instance;processEnv.MCP_MINECRAFT_TARGET=target;
      const transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env:processEnv,stderr:'pipe'});let applied;
      const call=async(name,args)=>{const r=await client.callTool({name,arguments:args},{timeout:10000});assert(!r.isError,JSON.stringify(r));return r.structuredContent.data;};
      const before=await deep(),original=clock(before),inputBefore=await input();const make=(s,value,expectedValue)=>({operation:'world.time.set',...base(s,'world_time',clock(s)),clockId:original.clockId,value,expectedValue});
      try {await client.connect(transport);await call('minecraft_debug_arm',{action:'arm',worldFingerprint:fingerprint,namespaces:['world'],ttlMs:60000});await pause(650);
        applied=await call('minecraft_debug',{action:'mutate',mutation:make(before,original.totalTicks+321,original.totalTicks)});const after=await deep();assert.equal(clock(after).totalTicks,original.totalTicks+321);assert.deepEqual(ref(after,'world_time',clock(after)),applied.afterResourceVersion);await pause(650);
        const restored=await call('minecraft_debug',{action:'mutate',mutation:make(after,original.totalTicks,original.totalTicks+321)});applied=undefined;assert.equal(clock(await deep()).totalTicks,original.totalTicks);actionSamples.push({case:'world_environment_actual_mcp',restored,consumer:'mcp_stdio'});
      } finally {await client.close();await transport.close();if(applied){await arm();const s=await deep();assert.deepEqual(ref(s,'world_time',clock(s)),applied.afterResourceVersion);await mutate(make(s,original.totalTicks,original.totalTicks+321));}}
      assert.equal((await input()).inputDispatchSequence,inputBefore.inputDispatchSequence);
    });
    await check('natural clock and weather timer progression does not impersonate an explicit native edit',async()=>{
      await arm();const initial=await deep(),advance=env(initial).rules.find(r=>/^(minecraft:advance_time|doDaylightCycle)$/.test(r.ruleId));assert(advance);let changed;
      const changeRule=(s,value,expectedValue)=>({operation:'world.rule.set',...base(s,'world_rule',env(s).rules.find(r=>r.ruleId===advance.ruleId)),ruleId:advance.ruleId,value,expectedValue});
      if(!advance.value)changed=await mutate(changeRule(initial,true,false));
      try {await acquire();held=true;await tapEscape();await wait(t=>!t.screenClass,'environment natural tick running');paused=false;await release();held=false;await mode('OPERATE');
        const before=await deep(),inputBefore=await input(),deadline=Date.now()+5000;let after;
        do{after=await deep();assert(Date.now()<deadline,'Native game time must actually advance');}while(after.server.world.gameTime<before.server.world.gameTime+5);
        assert(clock(after).totalTicks>clock(before).totalTicks);assert.deepEqual(ref(after,'world_time',clock(after)),ref(before,'world_time',clock(before)));
        assert.notDeepEqual(weatherValue(env(after).weather),weatherValue(env(before).weather),'Native weather countdown must actually progress, not merely preserve a version on frozen state');
        if(env(after).weather.raining===env(before).weather.raining&&env(after).weather.thundering===env(before).weather.thundering)assert.deepEqual(ref(after,'world_weather',env(after).weather),ref(before,'world_weather',env(before).weather));
        assert.equal((await input()).inputDispatchSequence,inputBefore.inputDispatchSequence);actionSamples.push({case:'world_environment_natural_progression',before:env(before),after:env(after),beforeGameTime:before.server.world.gameTime,afterGameTime:after.server.world.gameTime,performanceMeasured:false});
      }finally{if(!paused){await acquire();held=true;await tapEscape();await wait(t=>t.screenClass.endsWith('PauseScreen'),'environment natural tick paused');paused=true;await release();held=false;await mode('OPERATE');}
        if(changed){await arm();const s=await deep();assert.deepEqual(ref(s,'world_rule',env(s).rules.find(r=>r.ruleId===advance.ruleId)),changed.afterResourceVersion);await mutate(changeRule(s,false,true));}}
    });
  }catch(error){failure=error;}finally{
    try{if(held){await release();held=false;}await mode('OPERATE');if(fingerprint){await arm();for(const restore of undo.reverse())await restore();}}catch(error){cleanupErrors.push(error.message);}
    try{await api('POST','/v0/debug/disarm');setArm(undefined);await mode('READ');await input();}catch(error){cleanupErrors.push(error.message);}actionSamples.push({case:'world_debug_environment_cleanup',cleanupErrors,persistentWriteCalls:0});
  }
  if(failure)throw failure;assert.deepEqual(cleanupErrors,[]);
}
