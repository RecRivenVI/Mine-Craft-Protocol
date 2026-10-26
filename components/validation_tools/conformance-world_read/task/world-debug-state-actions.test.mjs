import assert from 'node:assert/strict';
import test from 'node:test';
import {stateView} from './world-debug-state-actions.mjs';

test('state conditions use the correct native player and entity motion paths',()=>{
  const position={velocityX:0.125,velocityY:-0.25,velocityZ:0};
  assert.deepEqual(stateView({food:18,position},['food','velocity'],true),{food:18,velocity:{x:0.125,y:-0.25,z:0}});
  assert.deepEqual(stateView({...position,silent:false},['silent','velocity'],false),{silent:false,velocity:{x:0.125,y:-0.25,z:0}});
  assert.throws(()=>stateView({position},['unknown'],true));
});
