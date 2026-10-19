import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {resolve} from 'node:path';
import {pathToFileURL} from 'node:url';

export async function collectReadQuota({nativeRead,mcpRead,validate}) {
 let nativeQuota,mcpQuota,requests=0;
 // Either transport can encounter the first rejection at a refill boundary.
 // Keep the same bounded read-only probe until both actual error paths are observed.
 for(let round=0;round<16&&(!nativeQuota||!mcpQuota);round++){
  const replies=await Promise.all(Array.from({length:12},()=>nativeRead()));requests+=12;
  for(const reply of replies){if(reply.status===429)nativeQuota=validate(reply.body,'world');else assert.equal(reply.status,200,JSON.stringify(reply));}
  const result=await mcpRead();
  if(result.isError){const error=result.structuredContent?.error;assert.equal(error?.status,429,JSON.stringify(result));mcpQuota=validate(error,'world');}
 }
 assert(nativeQuota,'bounded native read burst must reach quota');assert(mcpQuota,'bounded real MCP read must observe structured quota');
 return {nativeQuota,mcpQuota,requests};
}

// Bounded quota probes. Accepted pipelines contain only delay, never player input.
export async function consumerRateActions({api,connection,repo,instance,acquire,release,leaseHeaders,check,actionSamples}) {
 const input=()=>api('GET','/v0/input/state');
 function validate(value,operation){
  assert(['RATE_LIMITED','EXPENSIVE_OPERATION_RATE_LIMITED'].includes(value.error??value.code),JSON.stringify(value));
  assert(Number.isSafeInteger(value.retryAfterMillis)&&value.retryAfterMillis>0&&value.retryAfterMillis<=60000);
  const b=value.rateLimit;assert(b);assert.equal(b.operationCategory,operation);
  assert(['principal','connection'].includes(b.scope));assert([operation,'requests'].includes(b.category));
  assert(Number.isSafeInteger(b.capacity)&&b.capacity>0);assert.equal(b.remaining,0);assert(b.refillPerSecond>0);assert.equal(b.cost,1);
  return {retryAfterMillis:value.retryAfterMillis,rateLimit:b};
 }
 function clean(value){assert.equal(value.pressedKeyCount,0);assert.equal(value.pressedButtonCount,0);assert(Number.isSafeInteger(value.inputDispatchSequence));return value;}
 async function raw(path,options={}){const response=await connection.fetch(path,{...options,signal:AbortSignal.timeout(15000)});return{status:response.status,body:await response.json()};}
 await check('feedback rate quota: native and actual MCP readonly bursts expose the limiting budget',async()=>{
  assert.equal((await api('GET','/v0/control/mode')).mode,'READ');const before=clean(await input());
  const require=createRequire(resolve(repo,'components/companion/package.json'));
  const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);
  const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
  const env=Object.fromEntries(Object.entries(process.env).filter(([,value])=>typeof value==='string'));
  for(const key of ['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE'])delete env[key];env.MCP_MINECRAFT_INSTANCE_DIR=instance;
  const client=new Client({name:'rate-feedback-regression',version:'1.0.0'}),transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
  let nativeQuota,mcpQuota,requests=0;
  try{
   await client.connect(transport);
   ({nativeQuota,mcpQuota,requests}=await collectReadQuota({
    nativeRead:()=>raw('/v0/world/block?x=2&y=80&z=2'),
    mcpRead:()=>client.callTool({name:'minecraft_query_world',arguments:{kind:'block',x:2,y:80,z:2}}),validate
   }));
  }finally{await client.close();}
  // Wait only for the reported refill estimate, not for a gameplay state or an automatic input replay.
  await new Promise(done=>setTimeout(done,Math.max(nativeQuota.retryAfterMillis,mcpQuota.retryAfterMillis)+25));
  const after=clean(await input());assert.equal(after.inputDispatchSequence,before.inputDispatchSequence);
  assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
  actionSamples.push({case:'feedback_read_quota',native:nativeQuota,mcp:mcpQuota,requests,realMcp:true,inputLease:false,inputDispatchDelta:0});
 });
 await check('feedback rate quota: delay-only pipelines reject before player input and remain independently leased',async()=>{
  await acquire();const before=clean(await input()),operations=[];let quota;
  try{
   for(let attempt=0;attempt<32;attempt++){
    const response=await raw('/v0/pipelines',{method:'POST',headers:{'content-type':'application/json',...leaseHeaders()},body:JSON.stringify({steps:[{type:'delay',durationMs:1}],cleanupOnComplete:false})});
    if(response.status===429){quota=validate(response.body,'pipeline');break;}
    assert.equal(response.status,200,JSON.stringify(response));assert(response.body.operationId);operations.push(response.body.operationId);
    // Read each accepted result before the bounded operation table can evict it.
    const state=await api('POST',`/v0/operations/${response.body.operationId}/wait`,{timeoutMs:5000});assert.equal(state.state,'completed',JSON.stringify(state));
   }
   assert(quota,'bounded pipeline burst must reach quota');
   const after=clean(await input());assert.equal(after.inputDispatchSequence,before.inputDispatchSequence);
   actionSamples.push({case:'feedback_pipeline_quota',...quota,acceptedDelayPipelines:operations.length,inputDispatchDelta:0,heldKeys:0,heldButtons:0,inputRetried:false});
  }finally{await release();}
  // Restore test independence by waiting for the reported bucket to refill;
  // never retry the rejected input or change the Runtime quota.
  await new Promise(done=>setTimeout(done,Math.max(quota.retryAfterMillis,Math.ceil((quota.rateLimit.capacity-quota.rateLimit.remaining)/quota.rateLimit.refillPerSecond*1000))+25));
 });
}
