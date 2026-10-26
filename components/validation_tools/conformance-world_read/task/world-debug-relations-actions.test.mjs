import assert from 'node:assert/strict';
import test from 'node:test';
import {relationRef,relationState} from './world-debug-relations-actions.mjs';
test('relationship consumer cannot substitute a full entity token or another lifecycle',()=>{
  const relation=id=>({lifecycleId:id,leash:{supported:true,resolution:'none'},owner:{supported:false}});
  const value={server:{player:{uuid:'p',relationships:relation('p1')},entities:[{uuid:'e',relationships:relation('e1')}]},resourceRevisionRefs:[{resourceType:'entity_relationship',resourceKey:'e',lifecycleId:'e1',mutationPreconditionEligible:true}]};assert.equal(relationState(value,'p').lifecycleId,'p1');assert.equal(relationState(value,'e').vehicle,null);assert.equal(relationState(value,'e').leash.holderUuid,null);assert.equal(relationRef(value,'e'),value.resourceRevisionRefs[0]);
  const wrong=structuredClone(value);wrong.resourceRevisionRefs[0].resourceType='entity';assert.throws(()=>relationRef(wrong,'e'));const stale=structuredClone(value);stale.resourceRevisionRefs[0].lifecycleId='old';assert.throws(()=>relationRef(stale,'e'));
});
