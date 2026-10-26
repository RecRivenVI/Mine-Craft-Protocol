import assert from 'node:assert/strict';
import test from 'node:test';
import {registryRef} from './world-debug-entities-actions.mjs';
test('entity factory consumer refuses incomplete duplicate or unavailable membership tokens',()=>{
  const value={server:{entityRegistry:{available:true,chunkLoadRequested:false,count:2,members:['a','b'],key:'chunk'}},resourceRevisionRefs:[{resourceType:'entity_registry',resourceKey:'chunk',mutationPreconditionEligible:true}]};
  assert.equal(registryRef(value),value.resourceRevisionRefs[0]);
  for(const change of [{available:false},{count:1},{members:['b','a']},{members:['a','a']}]){const malformed=structuredClone(value);Object.assign(malformed.server.entityRegistry,change);assert.throws(()=>registryRef(malformed));}
  const absent=structuredClone(value);absent.resourceRevisionRefs=[];assert.throws(()=>registryRef(absent));
});
