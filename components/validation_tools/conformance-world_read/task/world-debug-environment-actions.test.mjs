import assert from 'node:assert/strict';
import test from 'node:test';
import {environmentRef,weatherValue} from './world-debug-environment-actions.mjs';
test('environment consumer requires an actual eligible resource and preserves distinct weather timers',()=>{
  const value={clearWeatherTime:0,rainTime:6000,thunderTime:3000,raining:true,thundering:false,key:'native'};
  assert.deepEqual(weatherValue(value),{clearWeatherTime:0,rainTime:6000,thunderTime:3000,raining:true,thundering:false});
  assert.throws(()=>weatherValue({raining:true}));assert.throws(()=>environmentRef({resourceRevisionRefs:[]},'world_time','clock'));
  const snapshot={resourceRevisionRefs:[{resourceType:'world_time',resourceKey:'clock',mutationPreconditionEligible:true}]};assert.equal(environmentRef(snapshot,'world_time','clock'),snapshot.resourceRevisionRefs[0]);
});
