import assert from 'node:assert/strict';
import test from 'node:test';
import { clearWorldUiFixture } from './world-ui-actions.mjs';
import {breedingFixtureCommands,villagerFixtureCommands} from '../../vanilla-command-fixtures.mjs';
import {coreShowcaseProof} from '../../regression-v1_release/task/alpha-evidence.mjs';

function showcaseResult(){
  const target='26.3-neoforge',hash='0'.repeat(64);
  const cases=['world-observe','world-interact','wait-evidence','persistent-read','cancel-cleanup'].map(name=>({case:name,result:'PASS',heldKeys:0,heldButtons:0}));
  cases[2].evidence=[{contents:{checksumsVerified:4,dataCategory:'USER'},operatorUiPixelAssertion:'NOT_PERFORMED'}];
  cases[3].evidence=['world','player','chunk'].map(domain=>({domain,dataSource:'PERSISTED',consistency:'last_saved_state',writeImplemented:false}));
  cases[4].evidence=[{case:'cancel-cleanup',terminal:'cancelled',mode:'READ',postCleanupInputDispatch:0}];
  return {target,purpose:'CORE_SHOWCASE_SMOKE',runtime:'loader_development_source_sets',result:'PASS',sourceChangedDuringRun:false,cleanup:'CLEAN_SHUTDOWN_PORT_RELEASED',maximumHeapBytes:4294967296,persistentWriteInvocations:0,visualAcceptance:'NOT_PERFORMED',unifiedAcceptance:'NOT_PERFORMED',cases,sourceHashes:{'components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core/ProbeTransport.java':hash,[`versions/${target}/target.properties`]:hash}};
}
test('alpha accepts bounded Core Showcase only as scoped development evidence, never full Native or MCP',()=>{
  const proof=coreShowcaseProof(showcaseResult(),'26.3-neoforge');
  assert.equal(proof.checks,5);assert.equal(proof.mcpChecks,0);assert.equal(proof.fixturePolicy,'owned_mutable_showcase_world');assert(!('fullSmoke' in proof));
});
test('alpha refuses partial, stale, unsafe or falsely promoted Core Showcase proof',()=>{
  for(const edit of [r=>r.cases.pop(),r=>r.result='FAIL',r=>r.sourceChangedDuringRun=true,r=>r.cleanup='FAILED',r=>r.cases[0].heldKeys=1,r=>r.cases[3].evidence[0].dataSource='LIVE',r=>r.cases[4].evidence[0].postCleanupInputDispatch=1,r=>r.sourceHashes={},r=>r.sourceHashes['../secret']='0'.repeat(64),r=>r.persistentWriteInvocations=1,r=>r.visualAcceptance='PASS',r=>r.fixtureRecoveryRequired={}]){
    const result=showcaseResult();edit(result);assert.throws(()=>coreShowcaseProof(result,'26.3-neoforge'));
  }
});

for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
  test(target+' breeding fixture keeps native AI and distinct bounded movement selectors',()=>{
    const commands=breedingFixtureCommands({target,tag:'world_gesture_fixture',position:[-29999999.5,-64,29999998.5]});
    assert.equal(commands.length,4);
    assert(commands.every(command=>command.length+1<=256&&!/[\r\n]/.test(command)));
    const attribute=target.startsWith('1.')?'minecraft:generic.movement_speed':'minecraft:movement_speed';
    for(const [index,z] of [29999997.5,29999999.5].entries()){
      const adultTag=`world_gesture_fixture_breed${index}`;
      assert.equal(commands[index*2],`summon minecraft:cow -29999999.5 -64 ${z} {Silent:1b,PersistenceRequired:1b,NoAI:0b,Age:0,Tags:["world_gesture_fixture","${adultTag}"]}`);
      assert.equal(commands[index*2+1],`attribute @e[type=minecraft:cow,tag=${adultTag},limit=1] ${attribute} base set 0`);
    }
    assert.notEqual(commands[1],commands[3]);
    assert(!commands.some(command=>/\b(?:data|execute|tp|teleport)\b/.test(command)));
    assert(!commands.some(command=>/InLove|NoAI:1b|Age:-|baby/.test(command)));
  });
  test(target+' villager fixture keeps full offers in native Chat budget',()=>{
    const sells=['stone','dirt','sand','cobblestone','oak_log','glass','gravel','clay','brick','iron_ingot','gold_ingot','diamond'];
    const commands=villagerFixtureCommands({target,tag:'world_gesture_fixture',position:[-29999999.5,-64,29999999.5],sells,zeroPriceMultiplier:true});
    assert.equal(commands.length,39);
    assert(commands.every(command=>command.length+1<=256));
    assert.equal(commands.filter(command=>command.includes('Offers.Recipes append value')).length,12);
    for(const item of sells)assert(commands.some(command=>command.includes(`sell:{id:"minecraft:${item}",`)));
    assert(commands.filter(command=>command.includes('Offers.Recipes append value')).every(command=>command.includes(target.startsWith('1.20.')?'Count:1b':'count:1')));
    assert.equal(commands.filter(command=>command.endsWith('.maxUses set value 999')).length,12);
    assert.equal(commands.filter(command=>command.endsWith('.priceMultiplier set value 0f')).length,12);
  });
}
test('villager fixture rejects unsafe tags and positions instead of generating commands',()=>{
  for(const tag of ['unsafe,limit=2','escape]','two\ncommands'])assert.throws(()=>villagerFixtureCommands({target:'26.3-neoforge',tag,position:[0,80,0],sells:['stone']}));
  assert.throws(()=>villagerFixtureCommands({target:'26.3-neoforge',tag:'owned',position:[Infinity,80,0],sells:['stone']}));
});
test('breeding fixture rejects unknown Target and unsafe tag or positions before command generation',()=>{
  const valid={target:'26.3-neoforge',tag:'world_gesture_fixture',position:[0.5,80,2.5]};
  for(const target of ['1.21.1-forge','26.3-unknown','26.4-neoforge','__proto__',null])assert.throws(()=>breedingFixtureCommands({...valid,target}));
  for(const tag of ['unsafe,limit=2','escape]','two\ncommands','',undefined])assert.throws(()=>breedingFixtureCommands({...valid,tag}));
  for(const position of [null,[0,80],[0,80,0,1],[Infinity,80,0],[0,NaN,0],[0,80,'0'],[30000001,80,0],[0,80,29999999.5],[0,80,-29999999.5]])assert.throws(()=>breedingFixtureCommands({...valid,position}));
});

const snapshot=(tick,item='minecraft:air',drops=false,truncated=false)=>({
  tree:{clientTick:tick,screenClass:'',presentation:{children:[{role:'hud_item',selected:true,item,count:item==='minecraft:air'?0:1}]}},
  nearby:{entitiesTruncated:truncated,entities:drops?[{type:'minecraft:item'}]:[]}
});
async function exercise(states) {
  const calls=[];let current=states[0],reads=0;
  await clearWorldUiFixture({
    command:async text=>{calls.push(text);},
    api:async(method,path)=>{assert.equal(method,'GET');return path==='/v0/ui/tree'?current.tree:current.nearby;},
    poll:async(read,predicate,label,budget)=>{
      assert.equal(budget,8000);
      for(const state of states){current=state;reads++;if(predicate(await read()))return;}
      throw new Error('fixture condition was not satisfied');
    }
  });
  return {calls,reads};
}
test('fixture waits for actual empty hand and no dropped items, not command acceptance',async()=>{
  const r=await exercise([snapshot(1,'minecraft:stone'),snapshot(2,'minecraft:air',true),snapshot(3),snapshot(4),snapshot(5)]);
  assert.deepEqual(r.calls,['kill @e[type=minecraft:item,distance=..8]','clear @s']);assert.equal(r.reads,5);
});
test('duplicate ticks and truncated empty entity lists cannot prove a settled fixture',async()=>{
  const r=await exercise([snapshot(1,'minecraft:air',false,true),snapshot(2),snapshot(2),snapshot(3),snapshot(4)]);
  assert.equal(r.reads,5);
  await assert.rejects(exercise([snapshot(1,'minecraft:air',false,true),snapshot(2,'minecraft:air',false,true)]));
});
test('late pickup resets stability and is not silently retried as an acceptance Act',async()=>{
  const r=await exercise([snapshot(1),snapshot(2),snapshot(3,'minecraft:stone'),snapshot(4),snapshot(5),snapshot(6)]);
  assert.equal(r.reads,6);assert.equal(r.calls.length,2);
  await assert.rejects(exercise([snapshot(1),snapshot(2,'minecraft:stone'),snapshot(3)]));
});
