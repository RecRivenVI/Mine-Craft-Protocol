import assert from 'node:assert/strict';
import {createRequire} from 'node:module';
import {pathToFileURL} from 'node:url';
import {resolve} from 'node:path';

function flags(player) {
 assert.equal(player.available,true);assert.equal(player.dataCategory,'USER');assert.equal(player.authority,'CLIENT_KNOWN');assert.equal(player.storageAccessed,false);assert.equal(player.chunkLoadRequested,false);
 assert(['survival','creative','adventure','spectator'].includes(player.gameMode),JSON.stringify(player));assert.equal(typeof player.onGround,'boolean');assert.equal(typeof player.flying,'boolean');for(const name of ['serverPermission','nbt','uuid'])assert(!(name in player));
 return {gameMode:player.gameMode,onGround:player.onGround,flying:player.flying};
}

export async function consumerPlayerTitle({api,check,actionSamples}) {
 await check('feedback player: absent client world exposes unknown flags rather than invented defaults',async()=>{
  const player=await api('GET','/v0/player');assert.equal(player.available,false);for(const key of ['gameMode','onGround','flying'])assert.equal(player[key],'unknown');actionSamples.push({case:'feedback_player_unknown',player});
 });
}

export async function consumerPlayerFlags({api,repo,instance,target,acquire,release,check,actionSamples}) {
 await check('feedback player: Native provider frame and conditions retain client known flags under READ without input',async()=>{
  assert.equal((await api('GET','/v0/control/mode')).mode,'READ');const before=await api('GET','/v0/input/state');const player=await api('GET','/v0/player'),expected=flags(player);
  const provider=await api('POST','/v0/providers/read',{providerId:'minecraft:client/player'});assert.deepEqual(flags(provider.data),expected);
  const frame=await api('POST','/v0/state/frames',{reads:[{providerId:'minecraft:client/player'}]});assert.deepEqual(flags(frame.reads[0].data),expected);
  const condition=await api('POST','/v0/assert',{condition:{type:'player',expected}});assert.equal(condition.passed,true);
  assert.equal((await api('GET','/v0/input/state')).inputDispatchSequence,before.inputDispatchSequence);actionSamples.push({case:'feedback_user_player',player,provider,frame,condition,inputDispatchDelta:0});
 });
 await check('feedback player: actual MCP reads player and frame fields without acquiring control',async()=>{
  const require=createRequire(resolve(repo,'components/companion/package.json'));const {Client}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client')).href);const {StdioClientTransport}=await import(pathToFileURL(require.resolve('@modelcontextprotocol/client/stdio')).href);
  const env=Object.fromEntries(Object.entries(process.env).filter(([,v])=>typeof v==='string'));for(const key of ['MCP_MINECRAFT_TOKEN','MCP_MINECRAFT_BASE_URL','MCP_MINECRAFT_TOKEN_FILE'])delete env[key];env.MCP_MINECRAFT_INSTANCE_DIR=instance;env.MCP_MINECRAFT_TARGET=target;
  const client=new Client({name:'client-player-feedback-consumer',version:'0.0.1'}),transport=new StdioClientTransport({command:process.execPath,args:[resolve(repo,'components/companion/dist/index.js')],env,stderr:'pipe'});
  const call=async(name,args)=>{const value=await client.callTool({name,arguments:args});assert(!value.isError,JSON.stringify(value));return value.structuredContent.data;};
  try {await client.connect(transport);const player=await call('minecraft_get_state',{kind:'player'});flags(player);const frame=await call('minecraft_get_state',{kind:'state_frame',reads:[{providerId:'minecraft:client/player'}]});flags(frame.reads[0].data);assert.equal((await call('minecraft_control',{action:'status'})).mode,'READ');actionSamples.push({case:'feedback_mcp_player',player,frame,realMcp:true,inputLease:false});}
  finally {try{await client.close();}finally{await transport.close();}}
 });
 await check('feedback player: normal current player game mode changes are observed not granted by READ',async()=>{
  const original=await api('GET','/v0/player');flags(original);assert.equal(original.flying,false,'Fixed fixture must not begin with an active flight toggle');const changed=original.gameMode==='survival'?'creative':'survival';
  await acquire();
  try {await api('POST','/v0/command/player',{command:`gamemode ${changed} @s`});await release();const state=await api('POST','/v0/wait/until',{condition:{type:'player',expected:{gameMode:changed,flying:false}},timeoutMs:5000});flags(state.evidence);actionSamples.push({case:'feedback_player_mode',before:original,changed:state.evidence,arrangeAuthority:'diagnostic_native_player_command'});}
  finally {const state=await api('GET','/v0/control/mode');if(state.mode!=='TAKEOVER')await acquire();try{await api('POST','/v0/command/player',{command:`gamemode ${original.gameMode} @s`});}finally{await release();}}
 });
}
