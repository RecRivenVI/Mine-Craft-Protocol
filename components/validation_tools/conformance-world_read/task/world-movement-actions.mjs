import assert from 'node:assert/strict';

export const groundDirections=[{name:'forward',key:87,axis:'z',sign:1},{name:'left',key:65,axis:'x',sign:1},{name:'back',key:83,axis:'z',sign:-1},{name:'right',key:68,axis:'x',sign:-1}];

// Setup commands are diagnostic and confined to the owned immutable world copy. Acts use
// the public native key pipeline; assertions sample the bounded client USER model, not images.
export async function worldMovementActions({api,mode,acquire,release,click,clickNode,wait,nodes,check,actionSamples,fixtureInput}) {
  assert.equal(fixtureInput?.policy,'immutable_baseline_restored');
  const pause=()=>new Promise(r=>setTimeout(r,35)),player=()=>api('GET','/v0/player'),input=()=>api('GET','/v0/input/state');
  async function zero(){const s=await input();assert.equal(s.pressedKeyCount,0);assert.equal(s.pressedButtonCount,0);return s;}
  async function poll(read,accept,label,budget=7000){let value;const end=Date.now()+budget;do{value=await read();if(accept(value))return value;await pause();}while(Date.now()<end);throw new Error(label+': '+JSON.stringify(value));}
  async function command(text){const r=await api('POST','/v0/command/player',{command:text});assert.equal(r.mechanism,'GAME_ROUTED_RAW');assert.equal(r.effectConfirmed,false);}
  async function reset(x=0.5,y=80,z=0.5){await zero();await command(`tp @s ${x} ${y} ${z} 0 0`);await poll(player,p=>Math.abs(p.x-x)<0.02&&Math.abs(p.y-y)<0.02&&Math.abs(p.z-z)<0.02&&Math.abs(p.yaw)<0.1,'fixture reaches client');}
  const steps=(keys,holdMs)=>[{type:'key.chord',keys:keys.map(key=>({key})),holdMs}];
  async function act(keys,holdMs,pipelineSteps=steps(keys,holdMs)){
    const before=await zero(),initial=await player(),started=await api('POST','/v0/pipelines',{steps:pipelineSteps,timeoutMs:12000}),samples=[];let operation;const end=Date.now()+14000;
    do{const p=await player();assert.equal(p.dataCategory,'USER');assert.equal(p.authority,'CLIENT_KNOWN');samples.push({x:p.x,y:p.y,z:p.z});operation=await api('GET','/v0/operations/'+started.operationId);if(['completed','failed','cancelled'].includes(operation.state))break;await pause();}while(Date.now()<end);
    assert.equal(operation.state,'completed',JSON.stringify(operation));const inputAfter=await zero();assert(inputAfter.inputDispatchSequence>before.inputDispatchSequence);
    return {initial,final:await player(),keys,holdMs,pipelineSteps,samples,operation,inputAfter,arrangeAuthority:'diagnostic_admin_command',actAuthority:'PLAYTEST',actPath:'GAME_ROUTED_NATIVE_KEY_PIPELINE',assertAuthority:'CLIENT_KNOWN_USER',screenshotUsed:false,directMutationUsedByAct:false};
  }
  const distance=s=>Math.hypot(s.final.x-s.initial.x,s.final.z-s.initial.z);
  await check('movement permissions: READ and OPERATE reject player input without effects',async()=>{
    for(const intent of ['READ','OPERATE']){await mode(intent);const before=await zero();await api('POST','/v0/pipelines',{steps:steps([87],100),timeoutMs:2000},'TAKEOVER_REQUIRED');assert.equal((await zero()).inputDispatchSequence,before.inputDispatchSequence);}await mode('READ');await acquire();
  });
  await check('movement fixture: semantic entry and bounded diagnostic floor/air',async()=>{
    await click('menu.singleplayer','Singleplayer');const t=await wait(t=>t.screenClass.endsWith('SelectWorldScreen'),'owned world list'),worlds=nodes(t).filter(n=>n.worldName==='World-Read-Alpha');assert.equal(worlds.length,1);await clickNode(worlds[0]);await click('selectWorld.select','Play Selected World');await wait(t=>!t.screenClass,'owned world',150000);
    await command('gamemode creative @s');await command('clear @s');await command('fill -8 79 -8 8 79 8 minecraft:stone');await command('fill -8 80 -8 8 86 8 minecraft:air');await reset();assert.equal((await api('GET','/v0/world/block?x=0&y=79&z=0')).block,'minecraft:stone');
  });
  let walking;
  await check('WU-01 native W A S D follow camera axes and release input',async()=>{
    for(const d of groundDirections){await reset();const s=await act([d.key],800);assert((s.final[d.axis]-s.initial[d.axis])*d.sign>1.5,JSON.stringify(s));const other=d.axis==='x'?'z':'x';assert(Math.abs(s.final[other]-s.initial[other])<0.2);assert(Math.abs(s.final.y-80)<0.05);actionSamples.push({case:'movement_axis',direction:d.name,...s});if(d.name==='forward')walking=s;}
  });
  await check('WU-01 native diagonal movement is normalized',async()=>{
    await reset();const s=await act([87,68],800);assert(s.final.x<s.initial.x-0.8);assert(s.final.z>s.initial.z+0.8);assert(distance(s)<distance(walking)*1.3);actionSamples.push({case:'movement_diagonal',...s});
  });
  await check('WU-02 native jump rises and lands on the same floor',async()=>{
    await reset();const s=await act([32],180);assert(Math.max(...s.samples.map(p=>p.y))>80.3);const landed=await poll(player,p=>Math.abs(p.y-80)<0.02,'native landing');await zero();actionSamples.push({case:'movement_jump',...s,landed:{x:landed.x,y:landed.y,z:landed.z}});
  });
  await check('WU-01 crouch and sprint change native movement speed',async()=>{
    await reset();const walk=await act([87],1000);await reset();const crouch=await act([340,87],1000);await reset();const sprint=await act([341,87],1000);const distances={walk:distance(walk),crouch:distance(crouch),sprint:distance(sprint)};assert(distances.crouch>0.2&&distances.crouch<distances.walk*0.65,JSON.stringify(distances));assert(distances.sprint>distances.walk*1.1,JSON.stringify(distances));actionSamples.push({case:'movement_stances',walk,crouch,sprint,distances});
  });
  await check('WU-01/02 native wall collision and sneak edge protection',async()=>{
    await command('fill -1 80 3 1 83 3 minecraft:stone');await reset();const blocked=await act([87],1300);actionSamples.push({case:'movement_wall_collision',...blocked});assert(blocked.final.z>2&&blocked.final.z<2.72,JSON.stringify(blocked.final));assert(Math.abs(blocked.final.y-80)<0.05);await command('fill -1 80 3 1 83 3 minecraft:air');await command('fill -1 79 3 1 79 7 minecraft:air');await reset();
    // Stop forward momentum while still crouched; releasing both keys at the ledge
    // legitimately lets Vanilla inertia carry the now-standing player off the edge.
    const edgeSteps=[{type:'key',key:340,action:1},{type:'key',key:87,action:1},{type:'delay',durationMs:2800},{type:'key',key:87,action:0},{type:'delay',durationMs:500},{type:'key',key:340,action:0}];
    const edge=await act([340,87],2800,edgeSteps);actionSamples.push({case:'movement_sneak_edge',...edge});assert(edge.final.z>2.9&&edge.final.z<3.35,JSON.stringify(edge.final));assert(edge.samples.every(p=>Math.abs(p.y-80)<0.05),JSON.stringify(edge.samples));assert(Math.abs(edge.final.y-80)<0.05,JSON.stringify(edge.final));await command('fill -1 79 3 1 79 7 minecraft:stone');
  });
  await check('WU-02 native ladder climbing through forward input',async()=>{
    await command('fill 0 80 3 0 85 3 minecraft:stone');await command('fill 0 80 2 0 85 2 minecraft:ladder[facing=north]');await reset(0.5,80,1.7);const s=await act([87],1600);assert(s.final.y>81.2,JSON.stringify(s));await command('fill 0 80 2 0 85 3 minecraft:air');await reset();actionSamples.push({case:'movement_ladder',...s});
  });
  await check('WU-03 water ascent/descent through native jump/crouch input',async()=>{
    await command('fill -2 80 -2 2 84 2 minecraft:water');await reset();assert.equal((await api('GET','/v0/world/block?x=0&y=81&z=0')).block,'minecraft:water');const up=await act([32],1500);assert(up.final.y>81,JSON.stringify(up));const down=await act([340],1200);assert(down.final.y<up.final.y-0.2,JSON.stringify(down));await command('fill -2 80 -2 2 84 2 minecraft:air');await reset();actionSamples.push({case:'movement_water',up,down});
  });
  async function noLateInput(before){for(let count=0;count<8;count++){await pause();assert.equal((await zero()).inputDispatchSequence,before.inputDispatchSequence);}}
  await check('movement cancellation releases keys and cancels queued input',async()=>{
    await reset();const started=await api('POST','/v0/pipelines',{steps:[{type:'key',key:87,action:1},{type:'delay',durationMs:6000},{type:'key',key:87,action:0},{type:'key.tap',key:68,holdMs:200}],timeoutMs:12000});await poll(input,s=>s.pressedKeyCount===1,'actual held key');await api('DELETE','/v0/operations/'+started.operationId);await poll(input,s=>s.pressedKeyCount===0&&s.pressedButtonCount===0,'cancel release');const clean=await zero();await noLateInput(clean);const operation=await api('GET','/v0/operations/'+started.operationId);assert.equal(operation.state,'cancelled');actionSamples.push({case:'movement_cancel',operation,inputAfter:clean,lateInputDispatched:false});
  });
  await check('movement lease loss returns READ and prevents late input',async()=>{
    await reset();const started=await api('POST','/v0/pipelines',{steps:[{type:'key.chord',keys:[{key:87},{key:68}],holdMs:6000},{type:'key.tap',key:65,holdMs:200}],timeoutMs:12000});await poll(input,s=>s.pressedKeyCount===2,'actual held chord');await release();const control=await api('GET','/v0/control/mode');assert.equal(control.mode,'READ');assert.equal(control.reconsentRequired,false);await poll(input,s=>s.pressedKeyCount===0&&s.pressedButtonCount===0,'lease release');const clean=await zero();await noLateInput(clean);assert.equal((await player()).dataCategory,'USER');const operation=await api('GET','/v0/operations/'+started.operationId);assert(['cancelled','failed'].includes(operation.state),JSON.stringify(operation));actionSamples.push({case:'movement_lease_loss',operation,control,inputAfter:clean,lateInputDispatched:false});
  });
}
