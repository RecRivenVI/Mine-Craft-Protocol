import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {execFileSync} from 'node:child_process';
import {connectRuntime,automaticTakeoverRequest} from '../../runtime-connection.mjs';
import {candidateSource,sha256} from '../../regression-v1_release/task/alpha-candidate.mjs';
import {continuousFunctionalActions} from './continuous-functional-actions.mjs';

const repo=resolve(import.meta.dirname,'../../../..');
const target=process.argv.find(value=>value.startsWith('--target='))?.slice(9);
assert.equal(target,'26.3-fabric');
const instance=resolve(repo,'validations/conformance-world_read/instance',target,'client');
const fixtureInput=JSON.parse(await readFile(resolve(instance,'fixture-input.json')));
const connection=await connectRuntime(instance,target),checks=[],actionSamples=[];
let lease,renew,failure;
async function api(method,path,body,expectedError,extraHeaders={}){
 if(method==='POST'&&path==='/v0/control/acquire')body=automaticTakeoverRequest(await api('GET','/v0/control/mode'),body);
 const response=await connection.fetch(path,{method,headers:{...(lease?{'x-mcp-control-lease':lease}:{}),...(body?{'content-type':'application/json'}:{}),...extraHeaders},...(body?{body:JSON.stringify(body)}:{}),signal:AbortSignal.timeout(25000)});
 const value=await response.json();
 if(expectedError){assert(!response.ok);assert((Array.isArray(expectedError)?expectedError:[expectedError]).includes(value.error),JSON.stringify(value));return value;}
 if(!response.ok){const error=new Error(JSON.stringify(value));error.status=response.status;error.runtimeError=value;throw error;}return value;
}
const nodes=tree=>(tree.children??[]).flatMap(node=>[node,...nodes(node)]);
async function wait(predicate,description,budget=90000){
 const end=Date.now()+budget;
 do{const tree=await api('GET','/v0/ui/tree');if(!tree.overlayIdentity&&predicate(tree))return tree;await new Promise(done=>setTimeout(done,180));}while(Date.now()<end);
 throw new Error('UI timeout: '+description);
}
const clickNode=(node,modifiers=0)=>api('POST','/v0/ui/action',{selector:{nodeId:node.nodeId},action:'click',modifiers});
async function click(key,label){const tree=await api('GET','/v0/ui/tree'),node=nodes(tree).find(row=>(row.translationKey===key||row.label===label)&&row.active!==false&&row.visible!==false);assert(node,'Missing semantic button '+key);return clickNode(node);}
async function key(key){const started=await api('POST','/v0/pipelines',{steps:[{type:'key.tap',key,holdMs:40}],timeoutMs:12000});const outcome=await api('POST',`/v0/operations/${started.operationId}/wait`,{timeoutMs:12000});assert.equal(outcome.state,'completed');}
async function acquire(){assert.equal((await api('GET','/v0/control/mode')).reconsentRequired,false);lease=(await api('POST','/v0/control/acquire',{ttlMs:60000})).leaseId;renew=setInterval(()=>void api('POST','/v0/control/renew',{ttlMs:60000}).catch(error=>{failure??=error;}),20000);}
async function release(){clearInterval(renew);await api('POST','/v0/control/release');lease=undefined;}
async function mode(mode){const current=await api('GET','/v0/control/mode');await api('POST','/v0/control/mode',{mode,expectedModeVersion:current.modeVersion});}
async function sources(){
 const targets=['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric'];
 const values=await candidateSource(repo,targets);
 for(const path of ['components/validation_tools/conformance-world_read/task/continuous-functional-actions.mjs','components/validation_tools/conformance-world_read/task/continuous-functional-smoke.mjs','components/validation_tools/conformance-world_read/task/continuous-performance-actions.mjs','components/validation_tools/conformance-world_read/task/world-read-smoke.mjs','components/validation_tools/conformance-world_read/task/context-exit-evidence.mjs','components/validation_tools/runtime-connection.mjs','components/validation_tools/conformance-world_read/task/WorldReadFixture.ps1','components/companion/package.json','components/companion/package-lock.json'])values[path]=sha256(await readFile(resolve(repo,path)));
 return values;
}
const before=await sources();
try{
 await continuousFunctionalActions({api,target,fixtureInput,mode,acquire,release,click,clickNode,wait,nodes,check:async(name,run)=>{await run();checks.push({name,status:'PASS'});console.log('PASS: '+name);},actionSamples,forgetExpiredLease:()=>{clearInterval(renew);lease=undefined;}});
}catch(error){failure=error;console.error(error);}
finally{
 clearInterval(renew);
 try{execFileSync(process.execPath,[resolve(repo,'components/validation_tools/conformance-world_read/task/world-read-smoke.mjs'),`--target=${target}`,'--shutdown-only=true'],{cwd:repo,stdio:'inherit'});}catch(error){failure??=error;}
 const after=await sources(),sourceChangedDuringRun=JSON.stringify(before)!==JSON.stringify(after);if(sourceChangedDuringRun)failure??=new Error('Sources changed during functional validation');
 const result={target,scenario:'continuous-functional',status:failure?'FAIL':'PASS',acceptanceScope:'functional_only',performance:{status:'DEFERRED_BY_USER_2026_10_20',newMeasurements:false},testedAt:new Date().toISOString(),evidence:'development_runtime_native',fixturePolicy:fixtureInput.policy,fixtureInput,runtimeConnection:{runtimeInstanceId:connection.instanceId,port:connection.port,pid:connection.pid,discovery:'instance_directory'},checks,actionSamples,sourceHashes:after,sourceChangedDuringRun,persistentWriteCalls:0,fullWorldMilestone:false,fullSmoke:false,...(failure?{error:String(failure)}:{})};
 await writeFile(resolve(repo,`validations/conformance-world_read/result/${target}-continuous-functional.json`),JSON.stringify(result,null,2)+'\n');
}
if(failure)process.exitCode=1;
