import assert from 'node:assert/strict';
import test from 'node:test';
import {fluidFixturePositions} from './world-debug-loading-actions.mjs';

test('scheduler pressure fixture has 65 unique positions inside exactly the players loaded chunk',()=>{
  for(const p of [{x:0.25,y:64.5,z:0.25},{x:-0.25,y:64,z:-16.25}]){const all=fluidFixturePositions(p);assert.equal(all.length,65);assert.equal(new Set(all.map(v=>`${v.x},${v.y},${v.z}`)).size,65);for(const at of all){assert.equal(Math.floor(at.x/16),Math.floor(p.x/16));assert.equal(Math.floor(at.z/16),Math.floor(p.z/16));assert(at.y>p.y+7);}}
});
