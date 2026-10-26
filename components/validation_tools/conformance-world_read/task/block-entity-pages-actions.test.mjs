import assert from 'node:assert/strict';
import test from 'node:test';
import {blockEntityPagesActions} from './block-entity-pages-actions.mjs';

function fixture(dirty = false) {
  let block = dirty ? 'minecraft:stone' : 'minecraft:air', mode = 'READ';
  const commands = [], samples = [], checks = [];
  const ctx = {target:'26.3-neoforge',fixtureInput:{policy:'immutable_baseline_restored',target:'26.3-neoforge',credentialsCopied:false},
    mode:async value => {mode = value;}, acquire:async () => {}, release:async () => {},
    click:async () => {}, clickNode:async () => {}, nodes:t => t.children ?? [],
    wait:async predicate => {const world = {screenClass:'SelectWorldScreen',children:[{worldName:'World-Read-Alpha'}]}; return predicate(world) ? world : {screenClass:''};},
    check:async (name, run) => {await run(); checks.push(name);}, actionSamples:samples,
    api:async (method,path,body) => {
      if (path === '/v0/input/state') return {pressedKeyCount:0,pressedButtonCount:0,inputCleanupPending:false,inputCleanupFailed:false,inputDispatchSequence:4};
      if (path === '/v0/player?category=USER') return {x:.5,y:4,z:.5};
      if (path === '/v0/command/player') {
        assert.equal(mode,'OPERATE'); commands.push(body.command); block = body.command.split(' ').at(-1);
        return {inputDispatched:false,dataCategory:'USER'};
      }
      if (path === '/v0/observe/deep') {
        if (body.domains[0] === 'blocks') return {server:{chunkLoadRequested:false,storageAccessed:false,
          blocks:body.selector.blocks.map(p => ({key:`${p.x},${p.y},${p.z}`,available:true,loadRequested:false,blockId:block}))}};
        return {dataCategory:'DEBUG',server:{chunkLoadRequested:false,storageAccessed:false,
          blockEntities:Array.from({length:128},(_,i) => ({key:`${i},24,8`,loaded:true,readEffects:'none'}))}};
      }
      assert.fail('Unexpected pages test request: ' + path);
    }};
  return {ctx,commands,samples,checks,state:() => ({block,mode})};
}

test('CF-022 test detects the real no-continuation failure and still restores its native fixture', async () => {
  const f = fixture();
  await assert.rejects(blockEntityPagesActions(f.ctx), /no pagination metadata/);
  assert.equal(f.checks.length,0); assert.equal(f.commands.length,2);
  assert.match(f.commands[0],/minecraft:barrel$/); assert.match(f.commands[1],/minecraft:air$/);
  assert.deepEqual(f.state(),{block:'minecraft:air',mode:'READ'});
  const cleanup = f.samples.find(value => value.case === 'block_entity_pages_cleanup');
  assert.equal(cleanup.restored.length,160); assert(!f.samples.some(value => value.case === 'block_entity_pages_complete'));
});

test('CF-022 fixture refuses nonempty native cells before any mutation', async () => {
  const f = fixture(true);
  await assert.rejects(blockEntityPagesActions(f.ctx), /Only verified baseline air/);
  assert.equal(f.commands.length,0); assert.deepEqual(f.state(),{block:'minecraft:stone',mode:'READ'});
});
