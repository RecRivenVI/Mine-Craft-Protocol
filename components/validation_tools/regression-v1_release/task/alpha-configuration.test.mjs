import test from 'node:test';
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {commandConfigurationProof} from './alpha-evidence.mjs';

function fixture(){
  const receipt=kind=>({configurationKind:kind,configurationPayloadType:kind.startsWith('STRUCTURE_')?'STRUCTURE_PARAMETERS':kind==='JIGSAW_BLOCK'?'BLOCK_STATE':'COMMAND',configuredCommandEffect:kind.startsWith('STRUCTURE_')||kind==='JIGSAW_BLOCK'?'STATE_MUTATION':'INFORMATION',effectClassification:'STATE_MUTATION',classificationSource:'native_player_command_configuration',mode:'DEBUG_PRIVILEGED',evidenceContaminated:true,effectConfirmed:false,gameplayAcceptanceEligible:false});
  const probe=(n,cfg,text)=>({attemptId:n.toString(16).padStart(32,'0'),probeOnly:true,chunkLoadRequested:false,worldMutationUsedByProbe:false,jigsawConfigurationPackets:cfg,jigsawGenerationPackets:0,storedJigsawFinalStateHash:createHash('sha256').update(text).digest('hex')});
  const sample=(kind,i,partial=false)=>({case:partial?'configuration_partial_failure':'configuration_submitted',kind,initial:probe(i*2,0,'minecraft:air'),after:probe(i*2+1,1,partial?'minecraft:air':'minecraft:stone'),result:{operationId:'operation-'+i,state:partial?'failed':'completed',...(partial?{errorCode:'PLAYER_COMMAND_UNCLASSIFIED'}:{}),result:{...(partial?{partial:true}:{}),evidenceContaminated:true,commandSubmissions:[receipt(kind)]}}});
  const operations=[sample('COMMAND_BLOCK',1),sample('COMMAND_MINECART',2),sample('JIGSAW_BLOCK',3),sample('JIGSAW_BLOCK',4,true)];
  const structureProbe=n=>({...probe(n,0,'minecraft:air'),structureUpdatePackets:0,structureScanPackets:0,structureSavePackets:0,structureLoadPackets:0,storedStructureNameHash:createHash('sha256').update('minecraft:validation_structure').digest('hex'),storedStructureMode:'SAVE',storedStructureOffset:'0,0,2',storedStructureSize:'3,3,3'});
  for(const [kind,counter] of [['STRUCTURE_UPDATE','structureUpdatePackets'],['STRUCTURE_SCAN','structureScanPackets']]){
    const value=sample(kind,operations.length+1);value.initial=structureProbe(20);value.after={...structureProbe(21),[counter]:1};operations.push(value);
  }
  const rejected=[['STRUCTURE_SAVE','PLAYER_COMMAND_OUT_OF_SCOPE'],['STRUCTURE_LOAD','PLAYER_COMMAND_UNCLASSIFIED']].map(([kind,code])=>({case:'structure_rejected',kind,code,result:{state:'failed',errorCode:code},initial:structureProbe(30),after:structureProbe(31)}));
  return {target:'26.3-neoforge',scenario:'command-configuration',status:'PASS',evidence:'development_runtime_native',fullSmoke:false,fullWorldMilestone:false,sourceChangedDuringRun:false,cleanShutdown:true,maximumHeapBytes:4294967296,persistentWriteCalls:0,checks:Array.from({length:16},()=>({status:'PASS'})),actionSamples:[...operations,...['PLAYER_COMMAND_OUT_OF_SCOPE','PLAYER_COMMAND_UNCLASSIFIED'].map(code=>({case:'jigsaw_rejected',code,result:{state:'failed',errorCode:code},initial:probe(10,0,'minecraft:air'),after:probe(11,0,'minecraft:air')})),...rejected,{case:'configuration_artifact',checked:{status:'PASS',category:'USER',configurationAttemptEvents:8,configurationSubmissionEvents:8,structuredStates:1}}],consumerScope:'native_command_configuration',consumerChecks:operations.map(s=>({status:'PASS',inputUnchanged:true,operationId:s.result.operationId}))};
}

test('alpha admits command configuration only as scoped development proof, including partial native submission',()=>{
  const proof=commandConfigurationProof(fixture(),'26.3-neoforge');assert.deepEqual(proof,{checks:16,mcpChecks:6});
});

test('alpha refuses wrong target, falsely clean/complete results, executed generation, stale reads and missing consumers',()=>{
  const mutations=[r=>r.target='26.3-fabric',r=>r.fullWorldMilestone=true,r=>r.fullSmoke=true,r=>r.cleanShutdown=false,r=>r.sourceChangedDuringRun=true,r=>r.maximumHeapBytes=8589934592,r=>r.persistentWriteCalls=1,r=>r.actionSamples[3].result.result.partial=false,r=>r.actionSamples[3].result.result.commandSubmissions=[],r=>r.actionSamples[3].after.jigsawGenerationPackets++,r=>r.actionSamples[2].after.jigsawConfigurationPackets=0,r=>r.actionSamples[2].after.storedJigsawFinalStateHash='0'.repeat(64),r=>r.actionSamples[2].after.attemptId=r.actionSamples[2].initial.attemptId,r=>r.actionSamples.find(s=>s.case==='jigsaw_rejected').after.jigsawConfigurationPackets++,r=>r.actionSamples.at(-1).checked.configurationSubmissionEvents=5,r=>r.consumerChecks.pop(),r=>r.consumerChecks[0].inputUnchanged=false,r=>r.consumerChecks[0].operationId='another operation',r=>r.actionSamples[4].result.result.commandSubmissions[0].configurationPayloadType='COMMAND',r=>r.actionSamples[5].after.storedStructureSize='0,0,0',r=>r.actionSamples[5].after.structureScanPackets=0,r=>r.actionSamples.find(s=>s.case==='structure_rejected').after.structureSavePackets++,r=>r.actionSamples.find(s=>s.kind==='STRUCTURE_LOAD').after.storedStructureNameHash='wrong',r=>r.actionSamples[4].after.structureLoadPackets++];
  for(const mutate of mutations){const value=fixture();mutate(value);assert.throws(()=>commandConfigurationProof(value,'26.3-neoforge'));}
});
