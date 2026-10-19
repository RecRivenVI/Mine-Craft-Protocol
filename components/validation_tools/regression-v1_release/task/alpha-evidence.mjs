import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {resolve,relative} from 'node:path';
import {fileURLToPath} from 'node:url';
import {baselineStatistics} from '../../conformance-world_read/task/interaction-baseline-actions.mjs';
import {decodeRegionPage,validatePlayerStream} from '../../conformance-world_read/task/region-stream-actions.mjs';
import {placementReceiptCases,stateFromUserPoint,validatePlacementReceipt,validatePlacementCompanion} from '../../conformance-world_read/task/world-placement-receipts-actions.mjs';
import {performanceAttackComparison} from '../../conformance-world_read/task/continuous-performance-actions.mjs';
import {validateFunctionalAttack} from '../../conformance-world_read/task/continuous-functional-actions.mjs';
import {DEPENDENCY_PROFILE,verifyDependencyPublication} from './dependency-proof.mjs';

export const CONTINUOUS_PROFILE='continuous_gameplay_trial_v1';
export const DEFERRED_PERFORMANCE='DEFERRED_BY_USER_2026_10_20';
export const CONTINUOUS_SCENARIOS=['input-context','region-stream','world-placement-receipts','continuous-performance','interaction-baseline'];
const hash=bytes=>createHash('sha256').update(bytes).digest('hex');
const finite=value=>assert(Number.isFinite(value)&&value>=0,'Invalid nonnegative measurement');
const sample=(result,name)=>{const rows=(result.actionSamples??[]).filter(row=>row.case===name);assert.equal(rows.length,1,'Missing/duplicate actual sample '+name);return rows[0];};
function inputClean(value){for(const key of ['pressedKeyCount','pressedButtonCount'])assert.equal(value[key],0);assert.equal(value.inputCleanupPending,false);assert.equal(value.inputCleanupFailed,false);assert(Number.isSafeInteger(value.inputDispatchSequence)&&value.inputDispatchSequence>=0);}
function released(value){assert.equal(value.passed,true);assert.deepEqual(value.evidence,{pressedKeyCount:0,pressedButtonCount:0});}
function inputUnchanged(before,after){inputClean(before);inputClean(after);for(const key of ['inputDispatchSequence','inputContextGeneration','inputContextId'])assert.equal(after[key],before[key],key);}
function readMode(value){assert.equal(value.mode,'READ');assert.notEqual(value.takeoverActive,true);assert(!value.leaseId);}
function user(value){assert.equal(value.available,true);assert.equal(value.domain,'WORLD');assert.equal(value.dataCategory,'USER');assert.equal(value.authority,'CLIENT_KNOWN');assert.equal(value.source,'bounded_client_world_model');assert.equal(value.dataSource,'LIVE');assert.equal(value.storageAccessed,false);assert.equal(value.chunkLoadRequested,false);assert.equal(value.completeGameData,false);}
function resultHeader(result,target,scenario,checks){
 assert.equal(result.target,target);assert.equal(result.scenario,scenario);assert.equal(result.status,'PASS');assert.equal(result.evidence,'development_runtime_native');assert.equal(result.fullSmoke,false);assert.equal(result.fullWorldMilestone,false);
 assert.equal(result.cleanShutdown,true);assert.equal(result.sourceChangedDuringRun,false);assert.equal(result.persistentWriteCalls,0);assert.equal(result.fixturePolicy,'immutable_baseline_restored');
 assert.equal(result.checks?.length,checks);assert(result.checks.every(row=>row.status==='PASS'));
 const fixture=result.fixtureInput;assert.equal(fixture?.policy,'immutable_baseline_restored');assert.equal(fixture.target,target);assert.equal(fixture.role,'client');assert.equal(fixture.credentialsCopied,false);assert.match(fixture.baselineSha256,/^[0-9a-f]{64}$/);assert.equal(fixture.restoredSha256,fixture.baselineSha256);
 assert(!(result.actionSamples??[]).some(row=>row.case?.endsWith('_cleanup_failure')),'Cleanup failed');
}
function completed(result,name,target,maximum,checks){const value=sample(result,name);assert.equal(value.target,target);assert.equal(value.liveEvidence,true);assert.equal(value.evidenceKind,'runtime_monotonic');assert.equal(value.checks,checks);assert.equal(value.maximumMillis,maximum);finite(value.elapsedMillis);assert(value.elapsedMillis<=maximum);inputClean(value.finalInput);readMode(value.finalMode);assert.equal(value.physicalEscTested,false);assert.equal(value.clientShutdownOwnedByOuterRunner,true);assert.equal(value.persistentWriteCalls,0);return value;}

export function inputContextProof(result,target){
 resultHeader(result,target,'input-context',3);
 const screens=sample(result,'input_context_screens');assert.equal(screens.coordinateFallbacks,0);assert.equal(screens.ordinaryScreensDoNotAdvanceNativeContext,true);assert.equal(screens.agentEscDoesNotRevoke,true);assert.equal(screens.screens.length,4);
 for(const row of screens.screens){assert.equal(row.outcome.state,'completed');for(const key of ['inputContextGeneration','inputContextId'])assert.equal(row.input[key],screens.inWorld[key]);inputClean(row.input);}
 const dimensions=sample(result,'input_context_dimensions');assert.equal(dimensions.arrangeIsGameplayAcceptance,false);assert.equal(dimensions.destinationConfirmedBy,'independent_USER_client_player');assert.equal(dimensions.nativeCommandEffectReceiptNotProof,true);
 for(const edge of [dimensions.outward,dimensions.returned]){
  assert.equal(edge.activeBefore.status,'running');assert.equal(edge.held.pressedKeyCount,1);assert.equal(edge.queuedState.inputDispatchSequence,edge.held.inputDispatchSequence);assert.equal(edge.outcomes.length,2);assert.deepEqual(edge.outcomes.map(row=>row.operationId),[edge.active.operationId,edge.queued.operationId]);assert(edge.outcomes.every(row=>row.state==='cancelled'));
  for(const key of ['inputContextGeneration','inputGeneration'])assert(edge.after[key]>edge.before[key]);assert.notEqual(edge.after.inputContextId,edge.before.inputContextId);inputUnchanged(edge.after,edge.later);released(edge.waited);assert.equal(edge.negativeObservationMillis,500);assert.equal(edge.sameLeasePreserved,true);assert.equal(edge.oldInputWasCancelledNotReplayed,true);assert.equal(edge.leaseBefore.leaseId,edge.leaseAfter.leaseId);assert.deepEqual(edge.leaseBefore.modeVersion,edge.leaseAfter.modeVersion);
  assert.equal(edge.freshInput.outcome.state,'completed');inputClean(edge.freshInput.after);assert.equal(edge.freshInput.after.inputContextId,edge.after.inputContextId);assert(edge.freshInput.after.inputDispatchSequence>edge.after.inputDispatchSequence);assert.equal(edge.freshInput.readiness.condition,'two_distinct_native_frames_no_screen_no_overlay_cleanup_complete');assert.equal(edge.freshInput.readiness.stableNativeFrames.length,2);
 }
 const ws=sample(result,'input_context_control_ws');assert.equal(ws.transport,'.NET_ClientWebSocket');assert.equal(ws.leaseHeaderBound,true);assert.equal(ws.credentialsExcluded,true);assert.equal(ws.closed.normalClose,true);assert.equal(ws.closed.exitCode,0);assert.equal(ws.hello.target,target);assert.equal(ws.held.pressedKeyCount,1);readMode(ws.ended);assert.equal(ws.ended.status,'available');assert.equal(ws.ended.reconsentRequired,false);assert.equal(ws.ended.modeTransitionReason,'control_channel_disconnected');released(ws.waited);inputUnchanged(ws.after,ws.later);assert.equal(ws.after.inputContextId,ws.readyInput.inputContextId);assert.equal(ws.negativeObservationMillis,500);assert.equal(ws.ordinaryHttpCloseNotTestedAsDisconnect,true);assert.equal(ws.localLeaseForgottenOnlyAfterConfirmedEnd,true);
 const end=completed(result,'input_context_complete',target,240000,3);assert.equal(end.sameUuidRespawnTested,false);assert.equal(end.ordinaryHttpCloseImmediateReleaseClaimed,false);
 return{scenario:'input-context',checks:3,epochBoundary:true,activeAndQueuedCancelled:true,leaseBoundWebSocketClose:true,sameUuidRespawnTested:false,physicalEscTested:false};
}

function decodedRegion(pages,query,context){let covered=0,unloaded=0;const cells=new Map();assert(pages.length>0);for(const page of pages){const decoded=decodeRegionPage(page,query,covered,context);for(const cell of decoded){const key=[cell.x,cell.y,cell.z].join(':');assert(!cells.has(key),'Duplicate region cell');cells.set(key,cell);if(!cell.loaded)unloaded++;}covered+=decoded.length;assert.equal(page.unloadedCellCount,unloaded);}assert.equal(pages.at(-1).complete,true);assert.equal(covered,['x','y','z'].reduce((n,key)=>n*(query.max[key]-query.min[key]+1),1));return{covered,unloaded,cells};}
function regionRequestProof(read,trace){
 const attempts=read.requestAttempts,pages=read.pages;assert(Array.isArray(attempts),'Actual region HTTP attempts are required');assert(attempts.length>=pages.length&&attempts.length<=pages.length*3);assert.equal(read.actualRequests,attempts.length);
 assert(Number.isSafeInteger(read.firstRequestIndex)&&read.firstRequestIndex>=0);assert.equal(read.lastRequestIndex,read.firstRequestIndex+attempts.length-1);assert.deepEqual(attempts,trace.slice(read.firstRequestIndex,read.lastRequestIndex+1),'Every failed and successful request must remain in the original trace');
 let query=read.request,pageIndex=0,attemptInPage=1,previous;const consumed=new Set();
 for(const[index,row]of attempts.entries()){
  assert.equal(row.requestIndex,read.firstRequestIndex+index);
  assert(pageIndex<pages.length,'No request may follow the complete region');assert.deepEqual(row.body,{category:'USER',action:'read',query});assert.equal(row.attemptInPage,attemptInPage);finite(row.startedOffsetMillis);finite(row.elapsedMillis);
  if(previous)assert(row.startedOffsetMillis-previous.startedOffsetMillis>=previous.elapsedMillis+(previous.waitMillis??0)-.000001,'Recorded retries must include the actual wait');
  if(row.outcome==='success'){
   assert.equal(row.status,200);assert.equal(row.pageIndex,pageIndex);assert.equal(row.responseSha256,hash(JSON.stringify(pages[pageIndex])));assert.equal(row.retryScheduled,false);assert.equal(row.waitMillis??0,0);assert(!Object.hasOwn(row,'error'));assert(!Object.hasOwn(row,'retryAfterMillis'));
   if(query.cursor){assert(!consumed.has(query.cursor),'A successful cursor must be single-use');consumed.add(query.cursor);}
   const page=pages[pageIndex++];if(!page.complete){assert.equal(typeof page.nextCursor,'string');assert(!consumed.has(page.nextCursor),'A successor cannot reuse a consumed cursor');query={cursor:page.nextCursor};}attemptInPage=1;
  }else{
   assert.equal(row.outcome,'typed_no_progress','Transport and unknown errors must not be retried');assert.equal(row.status,429);assert.equal(row.error?.error,'WORLD_REGION_BUDGET_NO_PROGRESS');assert(Number.isSafeInteger(row.error.retryAfterMillis)&&row.error.retryAfterMillis>=1&&row.error.retryAfterMillis<=1000);assert.equal(row.retryAfterMillis,row.error.retryAfterMillis);assert.equal(row.retryScheduled,true);assert(!Object.hasOwn(row,'pageIndex'));assert(!Object.hasOwn(row,'responseSha256'));finite(row.waitMillis);assert(row.waitMillis>=row.retryAfterMillis);assert(attemptInPage<3,'Each page has at most three total attempts');attemptInPage++;
  }
  previous=row;
 }
 assert.equal(pageIndex,pages.length,'Each emitted page must correspond to a successful HTTP request');assert.equal(attemptInPage,1,'A failed request must not be reported as a completed read');return attempts.length;
}
function mcpWorldProof(row,target){
 assert.equal(row.target,target);assert.equal(row.transport,'real_mcp_stdio');assert.equal(row.liveEvidence,true);assert.equal(row.initializeServerInfo?.name,'minecraft-protocol-companion');assert.equal(row.initializeServerInfo.version,row.expectedPackageVersion);assert.equal(typeof row.expectedPackageVersion,'string');assert(row.expectedPackageVersion.length>0);assert.equal(row.samples.length,20);
 for(const [index,s]of row.samples.entries()){assert.equal(s.index,index);finite(s.callRttMillis);finite(s.startedOffsetMillis);user(s.player);assert.equal(s.player.type,'player.state');for(const field of ['x','y','z','yaw','pitch','velocityX','velocityY','velocityZ'])assert(Number.isFinite(s.player[field]));if(index)assert(s.startedOffsetMillis-row.samples[index-1].startedOffsetMillis>=35);}
 const statistics=baselineStatistics(row.samples.map(value=>value.callRttMillis));const slowest=row.samples.reduce((a,b)=>b.callRttMillis>a.callRttMillis?b:a);assert.deepEqual(row.statistics,{...statistics,slowest:{index:slowest.index,callRttMillis:slowest.callRttMillis}});
 for(const status of [row.nativeBefore,row.nativeAfter]){readMode(status);assert.equal(status.status,'available');}
 for(const status of [row.mcpBefore,row.mcpAfter]){readMode(status);assert.equal(status.type,'control.mode');assert.equal(status.takeoverActive,false);assert.equal(status.authorizationIndependent,true);assert.equal(status.authorization.sessionOpen,true);assert.equal(status.authorization.inputLease.held,false);assert.equal(status.authorization.inputLease.remainingTtlMs,0);}
 for(const status of [row.nativeBefore,row.mcpBefore,row.nativeAfter,row.mcpAfter]){assert.deepEqual(status.modeVersion,row.nativeBefore.modeVersion);assert.equal(status.reconsentRequired,row.nativeBefore.reconsentRequired);}inputUnchanged(row.before,row.after);
 for(const session of [row.session,row.finalSession]){assert.equal(session.target,target);assert.equal(session.inWorld,true);}
 const cells=decodeRegionPage(row.region,row.query,0,row.before.inputContextId);assert.equal(cells.length,1);assert.equal(row.region.complete,true);assert.equal(row.region.allLoaded,true);assert.equal(cells[0].loaded,true);assert.deepEqual(row.decodedCell,cells[0]);user(row.point);assert.deepEqual(stateFromUserPoint(row.point),{block:cells[0].block,properties:cells[0].properties});
 for(const key of ['startupMillis','samplingElapsedMillis','regionCallRttMillis','nativePointRttMillis','elapsedMillis'])finite(row[key]);assert(row.samplingElapsedMillis>=row.samples.at(-1).startedOffsetMillis+row.samples.at(-1).callRttMillis);assert.equal(row.maximumMillis,90000);assert(row.elapsedMillis<=row.maximumMillis);assert.equal(row.startupExcludedFromPlayerStatistics,true);assert.equal(row.timeSource,'monotonic_elapsed');assert.equal(row.measurementScope,'mcp_stdio_call_round_trip_not_http_or_server_effect');assert.equal(row.minimumCallStartIntervalMillis,35);assert.equal(row.activeAdmissionExcludedFromCallRtt,true);assert.equal(row.automaticRetries,0);assert.equal(row.crossMachineLatencySla,false);
 for(const key of ['controlLeaseAcquired','permissionsGrantedByHelper','chunkLoadRequested','inputDispatched','screenshotsUsed','stateStreamTested','physicalEscTested'])assert.equal(row[key],false);for(const key of ['serverAuthoritativeReadsRequested','worldDiskReadsRequested','persistentWriteCalls'])assert.equal(row[key],0);assert.equal(row.ownedCompanionClosed,true);assert.equal(row.environment.credentialsExcluded,true);assert.equal(row.environment.connectionAuthority,'instance_discovery_only');
 return{samples:20,statistics:row.statistics,actualStdio:true};
}

export function regionStreamProof(result,target){
 resultHeader(result,target,'region-stream',5);const fixture=sample(result,'region_stream_fixture'),region=sample(result,'region_stream_region');const context={opaqueId:fixture.context.inputContextId,generation:fixture.context.inputContextGeneration};
 const decoded=decodedRegion(region.pages,region.request,context.opaqueId);assert.equal(decoded.covered,4096);assert.equal(region.covered,decoded.covered);assert.equal(region.unloaded,decoded.unloaded);assert.equal(region.uniqueCoordinates,decoded.cells.size);inputUnchanged(region.inputBefore,region.inputAfter);
 assert.equal(region.crossPageAtomic,false);assert.equal(region.readLoadRequested,false);assert.equal(region.debugReadUsed,false);assert.equal(region.historicalApiMeasured,false);assert.equal(region.comparisonBasis,'current_candidate_same_region_region_api_64_vs_4096_page_budget');assert.equal(region.userRadius,32);assert(Number.isFinite(region.regionMaximumCellCenterDistance)&&region.regionMaximumCellCenterDistance<=32);
 const baseline=decodedRegion(region.baseline.pages,region.baseline.request,context.opaqueId);assert.deepEqual(region.baseline.request.min,region.request.min);assert.deepEqual(region.baseline.request.max,region.request.max);assert.equal(region.baseline.request.maxBlocks,64);assert.equal(region.request.maxBlocks,4096);assert.equal(baseline.covered,decoded.covered);assert.deepEqual(region.optimized.request,region.request);assert.deepEqual(region.optimized.pages,region.pages);
 const trace=sample(result,'region_stream_requests');assert.equal(trace.scope,'page_helper_calls_excludes_expected_cursor_errors_and_final_cleanup');assert(Array.isArray(trace.requestAttempts));for(const[index,row]of trace.requestAttempts.entries())assert.equal(row.requestIndex,index);assert.equal(region.baseline.firstRequestIndex,0);assert.equal(region.optimized.firstRequestIndex,region.baseline.lastRequestIndex+1);
 const baselineCalls=regionRequestProof(region.baseline,trace.requestAttempts),optimizedCalls=regionRequestProof(region.optimized,trace.requestAttempts);assert.equal(region.baselineRegionCalls,baselineCalls);assert.equal(region.actualRegionCalls,optimizedCalls);assert.equal(region.callReductionRatio,baselineCalls/optimizedCalls);assert(region.callReductionRatio>=2,'All failed HTTP attempts count against the actual request reduction');
 assert.equal(region.path.baselineRegionHttpRequests,baselineCalls);assert.equal(region.path.optimizedRegionHttpRequests,optimizedCalls);assert.equal(region.path.actualPointHttpRequests,165);assert.equal(region.path.additionalRegionRequestsForPath,0);assert.equal(region.path.pointToOptimizedRequestRatio,region.path.actualPointHttpRequests/optimizedCalls);
 for(const comparison of region.points){const known=decoded.cells.get([comparison.cell.x,comparison.cell.y,comparison.cell.z].join(':'));assert.deepEqual(comparison.cell,known);assert.deepEqual(stateFromUserPoint(comparison.point),{block:known.block,properties:known.properties});}
 assert(region.points.length>0);for(const rejection of [region.consumedRejected,region.rotatedRejected,region.cancelledRejected])assert.equal(rejection.error,'WORLD_REGION_CURSOR_INVALID');assert.equal(region.cancelled.cancelled,true);assert.equal(region.twice.cancelled,false);
 const state=sample(result,'region_stream_state');assert.deepEqual(state.summary,validatePlayerStream(state.receipt,target,context));assert.equal(state.noControlLeaseUsed,true);assert.equal(state.serverEffectConfirmed,false);
 for(const field of ['x','y','z','yaw','pitch','velocityX','velocityY','velocityZ']){const error=Math.abs(state.after[field]-state.summary.last[field]);assert(Number.isFinite(error));assert.equal(state.stoppedGetAbsoluteErrors[field],error);if(['x','y','z'].includes(field)){assert(error<=.02);assert(Math.abs(state.after[field]-state.before[field])<=.02);}}
 const resume=sample(result,'region_stream_resume');assert.deepEqual(resume.summary,validatePlayerStream(resume.receipt,target,context,resume.resumeBase));assert.equal(resume.knownReplayMatched,true);const replay=resume.receipt.messages.filter(row=>row.event.sequence===resume.replayedKnownSequence);assert.equal(replay.length,1);assert.deepEqual(replay[0].event,resume.replayedKnownEvent);assert.deepEqual(resume.replayedKnownEvent,state.summary.last);inputUnchanged(resume.before,resume.after);assert.equal(resume.noAutoAcquire,true);assert.equal(resume.sameContext,true);assert.equal(resume.replayGapResyncExercised,false);
 completed(result,'region_stream_complete',target,180000,4);const mcp=mcpWorldProof(sample(result,'mcp_world_baseline'),target);
 return{scenario:'region-stream',checks:5,coveredCells:decoded.covered,regionCalls:optimizedCalls,baselineCalls,callReductionRatio:baselineCalls/optimizedCalls,failedRegionAttempts:{baseline:baselineCalls-region.baseline.pages.length,optimized:optimizedCalls-region.pages.length},firstSecondFrames:state.summary.firstSecondFrames,resumeFirstSecondFrames:resume.summary.firstSecondFrames,mcp,continuousWorldRegionSubscriptionClaimed:false};
}

export function placementReceiptsProof(result,target){
 resultHeader(result,target,'world-placement-receipts',8);const rows=(result.actionSamples??[]).filter(row=>row.case==='world_placement_receipt');assert.deepEqual(rows.map(row=>row.id).sort(),placementReceiptCases.map(row=>row.id).sort());
 for(const row of rows){const spec=placementReceiptCases.find(value=>value.id===row.id);assert.deepEqual(row.specification,spec);const actual=validatePlacementReceipt(row.operation,spec,stateFromUserPoint(row.fixture.destinationBefore),stateFromUserPoint(row.afterDestination));assert.deepEqual(row.result,actual);assert.equal(actual.targetRef,row.discovered.targetRef);assert.equal(actual.onTarget,true);assert.equal(actual.actualHit.kind,'block');assert.deepEqual(actual.actualHit.block,row.fixture.target);assert.equal(actual.actualHit.face,row.fixture.face);inputClean(row.beforeInput);inputClean(row.afterInput);assert(row.afterInput.inputDispatchSequence>row.beforeInput.inputDispatchSequence);released(row.waited);assert.equal(row.arrangeAuthority,'diagnostic_current_player_NATIVE_commands');assert.equal(row.actAuthority,'PLAYTEST');assert.equal(row.assertAuthority,'CLIENT_KNOWN_USER');assert.equal(row.actUsesSemanticTarget,true);assert.equal(row.pixelInputUsed,false);assert.equal(row.directMutationUsedByAct,false);assert.equal(row.operationCompletedMeans,'input_lifecycle_only');assert.equal(row.serverValidationObserved,false);
  if(!spec.blocked&&['door','bed','double_plant'].includes(spec.kind))validatePlacementCompanion(spec,stateFromUserPoint(row.afterDestination),stateFromUserPoint(row.companionAfter));if(spec.kind==='slab')assert.equal(stateFromUserPoint(row.afterDestination).properties.type,'double');if(spec.kind==='note_block')assert.equal(stateFromUserPoint(row.interactionAfter).properties.note,'1');
 }
 const end=completed(result,'world_placement_complete',target,240000,8);assert.deepEqual(end.tested,placementReceiptCases.map(row=>row.id));assert.equal(end.serverValidationObserved,false);return{scenario:'world-placement-receipts',checks:8,nativeConsumptionObserved:true,independentUserReadback:true,serverValidationObserved:false};
}

export function continuousPerformanceProof(result,target){
 resultHeader(result,target,'continuous-performance',5);const singles=(result.actionSamples??[]).filter(row=>row.case==='continuous_performance_attack_single');assert.deepEqual(singles.map(row=>row.index),[0,1,2]);const measured=sample(result,'continuous_performance_attack_comparison');const comparison=performanceAttackComparison(singles,measured.batch);assert.deepEqual(measured.comparison,comparison);
 assert.equal(comparison.targetRatio,1.5);assert.equal(comparison.targetResult,comparison.targetMet?'MET':'NOT_MET');assert.equal(comparison.actualAttackCountConfirmed,true,'Native entity attack consumption is not confirmed');assert.equal(comparison.serverEffectConfirmed,false);assert.equal(measured.targetNotMetBlocksPerformanceCompletion,true);assert.equal(measured.measurementValidityDoesNotMeanTargetMet,true);
 const fixture=sample(result,'continuous_performance_attack_fixture');assert.equal(fixture.entity.type,'minecraft:cow');assert.equal(typeof fixture.entity.targetRef,'string');assert(fixture.entity.targetRef.length>0);assert.equal(fixture.aim.outcome.state,'completed');const hit=fixture.aim.outcome.result.steps[0].result;assert.equal(hit.onTarget,true);assert.equal(hit.actualHit.kind,'entity');assert.equal(hit.actualHit.entityType,'minecraft:cow');assert(Number.isFinite(hit.actualHit.distance)&&hit.actualHit.distance>0&&hit.actualHit.distance<=3);finite(fixture.elapsedMillis);assert.equal(fixture.scope,'diagnostic_fixture_and_real_USER_semantic_aim_excluded_from_attack_timing');assert.equal(fixture.actAuthority,'PLAYTEST');assert.equal(fixture.nativeCooldownChanged,false);assert.equal(fixture.serverEffectConfirmed,false);assert.equal(fixture.damageConfirmed,false);
 const movement=sample(result,'continuous_performance_movement');assert(movement.observedMillis>=30000);assert(movement.observedMillis<=60000);assert(movement.clientObservedTravelDistance>1);assert(movement.lastClientTick>movement.firstClientTick);assert(movement.observations.length>=30);assert.equal(movement.originalSensitivityUnchanged,true);assert.equal(movement.serverEffectConfirmed,false);
 const end=completed(result,'continuous_performance_complete',target,180000,5);assert.equal(end.comparisonTargetResult,comparison.targetResult);assert.equal(end.attackConsumptionConfirmed,true);assert.equal(end.performanceComplete,comparison.targetMet&&comparison.actualAttackCountConfirmed);assert.equal(end.nativeCooldownChanged,false);assert.equal(end.serverEffectConfirmed,false);assert.equal(end.networkDisconnectTested,false);return{scenario:'continuous-performance',checks:5,targetRatio:1.5,batchToSingleP50Ratio:comparison.batchToSingleP50Ratio,targetMet:comparison.targetMet,targetResult:comparison.targetResult,performanceComplete:end.performanceComplete,actualAttackCountConfirmed:true,serverEffectConfirmed:false,delivery:comparison.targetMet?'trial':'trial_with_known_performance_limit',performanceAcceptance:comparison.targetMet?'MEASURED_TARGET_MET':'NOT_MET',goalPerformancePass:false};
}

export function interactionBaselineProof(result,target){
 resultHeader(result,target,'interaction-baseline',8);const rows=(result.actionSamples??[]).filter(row=>row.case==='interaction_baseline_sample'&&row.group==='gui_semantic_hover');assert.equal(rows.length,20);assert.deepEqual(rows.map(row=>row.index),Array.from({length:20},(_,index)=>index));assert.equal(new Set(rows.map(row=>row.operationId)).size,20);for(const row of rows){assert.equal(row.state,'completed');assert.equal(row.outcome.state,'completed');assert.equal(row.timing.timeSource,'monotonic_elapsed');assert.equal(row.timing.scope,'automation_pipeline_not_http_or_server_effect');assert.deepEqual(row.timing,row.outcome.result.timing);assert.equal(row.semanticTarget.freshTreePerSample,true);assert.equal(row.outcome.result.steps[0].result.resolvedNode.nodeId,row.semanticTarget.nodeId);assert.equal(row.outcome.result.steps[0].result.action,'hover');inputClean(row.inputBefore);inputClean(row.inputAfter);finite(row.postRttMillis);finite(row.waitRttMillis);finite(row.observedTerminalMillis);}
 const gui=sample(result,'interaction_baseline_gui_pacing');assert.equal(gui.sampleCount,20);assert.equal(gui.guiSamples,20);assert.equal(gui.guiP95Available,true);assert.equal(gui.intentionalPacingUnchanged,true);assert.deepEqual(gui.hoverSamples,rows);assert.equal(gui.alternatingSlots.length,2);assert.notEqual(gui.alternatingSlots[0].slot,gui.alternatingSlots[1].slot);for(const field of ['postRttMillis','waitRttMillis','observedTerminalMillis'])assert.deepEqual(gui.statistics[field],baselineStatistics(rows.map(row=>row[field])));const end=sample(result,'interaction_baseline_complete');assert.equal(end.guiSamples,20);assert.equal(end.guiP95Available,true);assert.equal(end.inputCallbackCounterIsDispatchProof,false);assert.equal(end.persistentWriteCalls,0);finite(end.elapsedMillis);assert(end.elapsedMillis<=210000);
 return{scenario:'interaction-baseline',checks:8,guiSamples:20,guiP95Available:true,guiPacingUnchanged:true,guiObservedTerminalMillis:gui.statistics.observedTerminalMillis};
}

const continuousProofs={'input-context':inputContextProof,'region-stream':regionStreamProof,'world-placement-receipts':placementReceiptsProof,'continuous-performance':continuousPerformanceProof,'interaction-baseline':interactionBaselineProof};
export function continuousFunctionalProof(result,target){
 assert.equal(target,'26.3-fabric');resultHeader(result,target,'continuous-functional',9);
 assert.equal(result.acceptanceScope,'functional_only');assert.deepEqual(result.performance,{status:DEFERRED_PERFORMANCE,newMeasurements:false});
 const end=sample(result,'continuous_functional_complete');assert.equal(end.target,target);assert.equal(end.scope,'functional_only');assert.equal(end.liveEvidence,true);assert.equal(end.evidenceKind,'runtime_functional');assert.equal(end.checks,9);assert.deepEqual(end.performance,{status:DEFERRED_PERFORMANCE,newMeasurementsPerformed:false});
 assert.deepEqual(end.attackPairs,[1,3]);assert.equal(end.actualNativeAttackConsumptionConfirmed,true);assert.equal(end.guiHoverCount,2);inputClean(end.finalInput);readMode(end.finalMode);assert.equal(end.persistentWriteCalls,0);assert.equal(end.physicalEscTested,false);assert.equal(end.serverEffectConfirmed,false);assert.equal(end.damageConfirmed,false);assert.equal(end.clientShutdownOwnedByOuterRunner,true);
 const denied=sample(result,'continuous_functional_read_denied');assert.equal(denied.refused.error,'TAKEOVER_REQUIRED');inputClean(denied.before);inputClean(denied.after);assert.equal(denied.before.inputDispatchSequence,denied.after.inputDispatchSequence);
 const fixture=sample(result,'continuous_functional_fixture');assert.equal(fixture.copyOnlySideEffects,true);assert.equal(fixture.actAuthority,'PLAYTEST');assert.equal(fixture.nativeCooldownChanged,false);assert.equal(fixture.serverEffectConfirmed,false);assert.equal(fixture.aim.outcome.state,'completed');assert.equal(fixture.aim.outcome.result.steps[0].result.onTarget,true);
 const attacks=(result.actionSamples??[]).filter(row=>row.case==='continuous_functional_attack');assert.deepEqual(attacks.map(row=>row.pairs),[1,3]);for(const row of attacks)assert.deepEqual(row.sample.validation,validateFunctionalAttack(row.sample,row.pairs));
 const singles=(result.actionSamples??[]).filter(row=>row.case==='continuous_functional_single_input');assert.deepEqual(singles.map(row=>row.name),['mouse_delta','delay_100','shift_40']);for(const row of singles){assert.equal(row.sample.outcome.state,'completed');inputClean(row.sample.inputBefore);inputClean(row.sample.inputAfter);}
 const hover=sample(result,'continuous_functional_gui_hover');assert.equal(hover.intentionalGuiPacingUnchanged,true);assert.equal(hover.anchors.length,2);assert.notEqual(hover.anchors[0],hover.anchors[1]);assert.equal(hover.samples.length,2);for(const[index,row]of hover.samples.entries()){assert.equal(row.slot,hover.anchors[index]);assert.equal(row.sample.outcome.state,'completed');const actual=row.sample.outcome.result.steps[0].result;assert.equal(actual.action,'hover');assert.equal(actual.targetingSource,'interaction_tree');assert.equal(actual.resolvedNode.nodeId,row.nodeId);assert.equal(actual.resolvedNode.slot,row.slot);}
 const cancelled=sample(result,'continuous_functional_cancel');assert.equal(cancelled.held.pressedKeyCount,1);assert.equal(cancelled.outcome.state,'cancelled');released(cancelled.waited);inputClean(cancelled.stopped);inputClean(cancelled.later);assert.equal(cancelled.later.inputDispatchSequence,cancelled.stopped.inputDispatchSequence);assert.equal(cancelled.negativeObservationMillis,300);assert.equal(cancelled.noLateInput,true);assert.equal(cancelled.physicalEscTested,false);
 const cleanup=sample(result,'continuous_functional_fixture_cleanup');assert.equal(cleanup.tag,fixture.tag);assert.deepEqual(cleanup.entityPosition,fixture.entity?{x:fixture.entity.x,y:fixture.entity.y,z:fixture.entity.z}:null);assert(Array.isArray(cleanup.after));
 const stop=sample(result,'continuous_functional_stop');assert.deepEqual(end.finalStop,{before:stop.before,response:stop.response,waited:stop.waited,after:stop.after});released(stop.waited);inputClean(stop.after);
 return{acceptanceScope:'functional_only',checks:9,actualAttackCountConfirmed:true,attackPairs:[1,3],guiSamples:2,performanceAcceptance:DEFERRED_PERFORMANCE,newPerformanceMeasurements:false,serverEffectConfirmed:false,goalPerformancePass:false};
}

export async function collectContinuousGameplayProof(repo,targets,sourceHashes,performancePolicy){
 const packageVersion=JSON.parse(await readFile(resolve(repo,'components/companion/package.json'),'utf8')).version;assert.equal(typeof packageVersion,'string');assert(packageVersion.length>0);
 const proofs=[];for(const target of targets)for(const scenario of CONTINUOUS_SCENARIOS){
  if(performancePolicy===DEFERRED_PERFORMANCE&&target==='26.3-fabric'&&['continuous-performance','interaction-baseline'].includes(scenario)){
   const path=`validations/conformance-world_read/result/${target}-continuous-functional.json`,bytes=await readFile(resolve(repo,path)),result=JSON.parse(bytes),bindings=await verifyEvidenceSources(repo,result);
   for(const[source,digest]of Object.entries(sourceHashes))assert.equal(bindings[source],digest,'Different functional candidate '+source);
   for(const tool of ['continuous-functional-actions.mjs','continuous-functional-smoke.mjs','continuous-performance-actions.mjs','world-read-smoke.mjs','context-exit-evidence.mjs'])assert(Object.hasOwn(bindings,'components/validation_tools/conformance-world_read/task/'+tool),'Missing actual functional tool binding');
   const proof=continuousFunctionalProof(result,target);proofs.push({target,scenario,result:path,resultSha256:hash(bytes),sourceMatchesCurrent:true,proof});continue;
  }
  const path=`validations/conformance-world_read/result/${target}-${scenario}.json`,bytes=await readFile(resolve(repo,path)),result=JSON.parse(bytes),bindings=await verifyEvidenceSources(repo,result);
  const required=Object.keys(sourceHashes).filter(source=>source.startsWith('components/runtime_core/src/main/')||source.startsWith('components/runtime_safety/src/main/')||source.startsWith(`versions/${target}/src/main/`)||source.startsWith(`versions/${target}/src/client/`)||[`versions/${target}/target.properties`,`versions/${target}/build.gradle.kts`,'gradle.properties','settings.gradle.kts','gradle/libs.versions.toml','components/protocol_schema/src/main/openapi/minecraft-control-v0.json'].includes(source));
  required.push('components/companion/src/server.ts','components/companion/package.json','components/companion/package-lock.json','components/validation_tools/conformance-world_read/task/world-read-smoke.mjs','components/validation_tools/conformance-world_read/task/Invoke-WorldReadSmoke.ps1','components/validation_tools/conformance-world_read/task/WorldReadFixture.ps1',`components/validation_tools/conformance-world_read/task/${scenario==='interaction-baseline'?'interaction-baseline-actions':scenario==='world-placement-receipts'?'world-placement-receipts-actions':scenario==='continuous-performance'?'continuous-performance-actions':scenario==='region-stream'?'region-stream-actions':'input-context-actions'}.mjs`);
  if(scenario==='region-stream')required.push('components/validation_tools/conformance-world_read/task/mcp-world-baseline.mjs');assert(required.length>15,'Missing candidate production bindings');for(const source of required)assert(Object.hasOwn(bindings,source),'Missing continuous source binding '+source);for(const source of required)if(Object.hasOwn(sourceHashes,source))assert.equal(bindings[source],sourceHashes[source],'Different candidate '+source);
  assert.equal(result.fixtureInput.targetFactsSha256,bindings[`versions/${target}/target.properties`]);if(scenario==='region-stream')assert.equal(sample(result,'mcp_world_baseline').expectedPackageVersion,packageVersion);const proof=continuousProofs[scenario](result,target);proofs.push({target,scenario,result:path,resultSha256:hash(bytes),sourceMatchesCurrent:true,proof});
 }
 const performance=proofs.filter(row=>row.scenario==='continuous-performance').map(({target,proof})=>proof.acceptanceScope==='functional_only'?{target,status:DEFERRED_PERFORMANCE,actualAttackCountConfirmed:proof.actualAttackCountConfirmed,newMeasurements:false}:{target,batchToSingleP50Ratio:proof.batchToSingleP50Ratio,targetMet:proof.targetMet,targetResult:proof.targetResult,actualAttackCountConfirmed:proof.actualAttackCountConfirmed,performanceComplete:proof.performanceComplete}),allTargetsMet=performance.every(row=>row.targetMet===true);
 return{profile:CONTINUOUS_PROFILE,scope:'A-D_continuous_gameplay_slice',ready:true,readyFor:'trial_not_goal_acceptance',delivery:allTargetsMet?'trial':'trial_with_known_performance_limit',fullMilestonesComplete:false,performanceAcceptance:{scope:'measured_attack_ratio_not_full_goal_acceptance',targetRatio:1.5,allTargetsMet,status:allTargetsMet?'MEASURED_TARGET_MET':'NOT_MET',goalPerformancePass:false,...(performancePolicy===DEFERRED_PERFORMANCE?{currentPolicy:DEFERRED_PERFORMANCE,newMeasurements:false}:{}),targets:performance},proofs};
}

export async function verifyContinuousPublication(report,repo){
 assert.equal(report.publicationProfile,CONTINUOUS_PROFILE,'New publications require the continuous-gameplay trial profile');assert.equal(report.nativeSmokeScope,'full');assert.equal(report.fullNativeSmoke,true);const targets=report.artifacts.map(row=>row.target);assert.equal(targets.length,5);assert.equal(new Set(targets).size,5);const actual=await collectContinuousGameplayProof(repo,targets,report.sourceHashes,report.performancePolicy);assert.deepEqual(report.continuousGameplay,actual,'Continuous gameplay evidence/proofs changed');assert.equal(report.delivery,actual.delivery);assert.equal(report.fullMilestonesComplete,false);assert.deepEqual(report.performanceAcceptance,actual.performanceAcceptance);await priorPublicationProofs(report,repo,targets);return actual;
}

async function priorPublicationProofs(report,repo,targets){
 const groups=[['nativeSmoke',null],['uiRegression',null],['continuousInput',null],['controlCollaboration',['mode-collaboration','command-discovery-batch','resource-version','server-completion']]];
 for(const[key,scenarios]of groups){const rows=report[key];assert.equal(rows?.length,targets.length*(scenarios?.length??1),'Missing prior publication group '+key);const identities=new Set();for(const row of rows){assert(targets.includes(row.target));const scenario=scenarios?row.scenario:null;assert(!scenarios||scenarios.includes(scenario));const identity=row.target+':'+(scenario??key);assert(!identities.has(identity),'Duplicate prior proof '+identity);identities.add(identity);
   const expected=key==='uiRegression'?`validations/conformance-vanilla_ui/result/${row.target}.json`:`validations/conformance-world_read/result/${row.target}${key==='continuousInput'?'-continuous-input':scenario?'-'+scenario:''}.json`;assert.equal(row.result,expected);const bytes=await readFile(resolve(repo,expected));assert.equal(hash(bytes),row.resultSha256,'Changed prior publication evidence');const result=JSON.parse(bytes),bindings=await verifyEvidenceSources(repo,result);
   for(const[source,digest]of Object.entries(report.sourceHashes))if(candidateBindingRequired(source,row.target,key))assert.equal(bindings[source],digest,'Different prior candidate '+source);
   assert.equal(result.sourceChangedDuringRun,false);assert.equal(result.cleanShutdown,true);assert.equal(result.persistentWriteCalls,0);if(key==='nativeSmoke'){assert.equal(result.scenario,'full');assert.equal(result.fullSmoke,true);assert.equal(row.fullSmoke,true);assert.equal(row.sourceMatchesCurrent,true);assert.deepEqual(row.consumerPriority,consumerPriorityProof(result,row.target));assert.deepEqual(row.consumerFeedback,consumerFeedbackProof(result,row.target));}else{const proof=key==='uiRegression'?uiRegressionProof(result,row.target):key==='continuousInput'?continuousInputProof(result,row.target):collaborationIncrementProof(result,row.target,scenario);for(const[field,value]of Object.entries(proof))assert.deepEqual(row[field],value,'Changed prior proof summary '+field);}
 }}
}

export function candidateBindingRequired(source,target,group){
 const production=source.startsWith('components/runtime_core/src/main/')||source.startsWith('components/runtime_safety/src/main/')||source.startsWith(`versions/${target}/src/main/`)||source.startsWith(`versions/${target}/src/client/`)||source===`versions/${target}/target.properties`;
 // The UI producer binds Java/mixins and Target facts. The mandatory full-world
 // group above and installed-JAR proof bind all resources; UI is not a second
 // metadata/texture or pixel-appearance attestation.
 return production&&(group!=='uiRegression'||source.endsWith('.java')||source.endsWith('mixins.json')||source===`versions/${target}/target.properties`);
}

export function canonicalSourceHashes(hashes){
 assert(hashes&&typeof hashes==='object'&&!Array.isArray(hashes),'Source bindings must be an object');
 const result=Object.create(null);
 for(const [source,hash]of Object.entries(hashes)){
  const path=source.replaceAll('\\','/');
  assert(!path.includes(':')&&!path.split('/').some(part=>!part||part==='.'||part==='..'),'Unsafe source binding path: '+source);
  assert(typeof hash==='string'&&/^[0-9a-f]{64}$/.test(hash),'Invalid source binding hash: '+source);
  assert(!Object.hasOwn(result,path),'Duplicate canonical source binding: '+path);
  result[path]=hash;
 }
 return result;
}

export function scenarioSourceHashes(hashes,scenario){
 const bindings=canonicalSourceHashes(hashes);
 // The world runner records this import for every scenario, but invokes it only
 // in the placement branch. Its readback assertion fix cannot invalidate reads,
 // input, commands or their existing measurements.
 if(scenario&&scenario!=='world-placement-receipts')delete bindings['components/validation_tools/conformance-world_read/task/world-placement-receipts-actions.mjs'];
 return bindings;
}

export async function verifyEvidenceSources(repo,result){
 const bindings=scenarioSourceHashes(result.sourceHashes??{},result.scenario);
 for(const[source,digest]of Object.entries(bindings))assert.equal(hash(await readFile(resolve(repo,source))),digest,'Stale evidence source '+source);
 return bindings;
}

export function continuousInputProof(result,target){
 assert.equal(result.target,target);assert.equal(result.scenario,'continuous-input');assert.equal(result.status,'PASS');
 assert.equal(result.evidence,'development_runtime_native');assert.equal(result.fullSmoke,false);assert.equal(result.fullWorldMilestone,false);
 assert.equal(result.cleanShutdown,true);assert.equal(result.sourceChangedDuringRun,false);assert.equal(result.persistentWriteCalls,0);
 assert.equal(result.fixturePolicy,'immutable_baseline_restored');assert.equal(result.checks?.length,10);
 assert(result.checks.every(row=>row.status==='PASS'&&row.name.startsWith('continuous ')));
 const samples=result.actionSamples??[],sample=name=>{const rows=samples.filter(row=>row.case===name);assert.equal(rows.length,1,name);return rows[0];};
 assert(!samples.some(row=>row.case==='continuous_cleanup_failure'||row.case==='continuous_mcp_cleanup_failure'));
 const input=(value,key)=>{
  for(const field of ['pressedKeyCount','pressedButtonCount','inputDispatchSequence','inputGeneration'])assert(Number.isSafeInteger(value[field])&&value[field]>=0,field);
  assert.equal(value.pressedButtonCount,0);assert.equal(value.pressedKeyCount,key===undefined?0:1);
  if(key!==undefined)assert(value.pressedKeys.includes(key));
 };
 const clean=value=>{input(value);assert.equal(value.inputCleanupPending,false);assert.equal(value.inputCleanupFailed,false);};
 const released=value=>{assert.equal(value.passed,true);assert.deepEqual(value.condition,{type:'input.released'});assert.deepEqual(value.evidence,{pressedKeyCount:0,pressedButtonCount:0});};
 const completed=value=>assert.equal(value.state,'completed');
 const routed=value=>{
  completed(value);
  for(const step of value.result.steps){if(!['key','mouse.delta','mouse.button'].includes(step.stepType))continue;
   assert.equal(step.result.inputOrigin,'AGENT_ROUTED');assert.equal(step.result.entryLayer,'GAME_ROUTED_RAW');assert.equal(step.result.hostCursorUsed,false);assert.equal(step.result.directMutationUsed,false);
  }
 };
 const stop=value=>{assert.equal(value.type,'input.release_all');assert(Number.isSafeInteger(value.inputGeneration)&&value.inputGeneration>=0);for(const field of ['releasedKeys','releasedButtons'])assert(Number.isSafeInteger(value[field])&&value[field]>=0);};
 const denied=sample('continuous_read_denied');input(denied.before);input(denied.after);assert.equal(denied.denied.error,'TAKEOVER_REQUIRED');
 assert.equal(denied.before.inputDispatchSequence,denied.after.inputDispatchSequence);assert.equal(denied.before.inputGeneration,denied.after.inputGeneration);released(denied.waited);released(denied.asserted);
 const entry=sample('continuous_semantic_world_entry');assert.equal(entry.worldName,'World-Read-Alpha');assert.equal(typeof entry.nodeId,'string');assert(entry.nodeId.length>0);assert.equal(entry.coordinateFallbacks,0);
 const movement=sample('continuous_preserved_w');routed(movement.down);assert.equal(movement.down.result.cleanupOnComplete,false);input(movement.held,87);assert.equal(movement.looks.length,3);
 for(const look of movement.looks){routed(look.outcome);assert.equal(look.outcome.result.cleanupOnComplete,false);assert.equal(look.outcome.result.steps.filter(step=>step.stepType==='mouse.delta').length,1);input(look.held,87);}
 for(const value of [movement.before,movement.after]){assert.equal(value.dataCategory,'USER');assert.equal(value.authority,'CLIENT_KNOWN');for(const field of ['x','z'])assert(Number.isFinite(value[field]));}
 const distance=Math.hypot(movement.after.x-movement.before.x,movement.after.z-movement.before.z);assert(distance>.01);assert.equal(movement.observedHorizontalDistance,distance);assert.equal(movement.serverAcceptanceConfirmed,false);assert.equal(movement.actAuthority,'PLAYTEST');
 const up=sample('continuous_explicit_up');routed(up.up);assert.equal(up.up.result.cleanupOnComplete,false);released(up.waited);input(up.after);
 const defaultCleanup=sample('continuous_default_cleanup');completed(defaultCleanup.down);assert.equal(defaultCleanup.down.result.cleanupOnComplete,false);input(defaultCleanup.held,87);
 completed(defaultCleanup.completed);assert.equal(defaultCleanup.completed.result.cleanupOnComplete,true);assert.equal(defaultCleanup.completed.result.cleanup.releasedKeys,1);assert.equal(defaultCleanup.completed.result.timing.stages.cleanup.calls,1);released(defaultCleanup.waited);input(defaultCleanup.after);
 const stopped=sample('continuous_release_all');input(stopped.held,340);input(stopped.queuedState,340);assert.equal(stopped.queuedState.inputDispatchSequence,stopped.held.inputDispatchSequence);
 assert.equal(stopped.activeBefore.status,'running');assert(!['completed','failed','cancelled'].includes(stopped.activeBefore.state));assert.equal(stopped.queued.length,2);assert.equal(stopped.outcomes.length,3);
 assert.deepEqual(stopped.outcomes.map(row=>row.operationId),[stopped.active,...stopped.queued].map(row=>row.operationId));assert(stopped.outcomes.every(row=>row.state==='cancelled'));
 stop(stopped.stopped);released(stopped.waited);clean(stopped.after);clean(stopped.later);assert.equal(stopped.after.inputGeneration,stopped.stopped.inputGeneration);assert(stopped.after.inputGeneration>stopped.queuedState.inputGeneration);
 assert.equal(stopped.after.inputDispatchSequence,stopped.later.inputDispatchSequence);assert.equal(stopped.cleanupBarrierConfirmedBy,'release_all_response');assert.equal(stopped.negativeObservationMillis,500);
 assert.equal(stopped.leasePreserved,true);assert.equal(stopped.modeVersionPreserved,true);assert.equal(stopped.physicalEscTested,false);assert(Number.isFinite(stopped.stopRttMillis)&&stopped.stopRttMillis>=0);
 const fresh=sample('continuous_after_stop');assert.equal(typeof fresh.leaseId,'string');assert(fresh.leaseId.length>0);completed(fresh.completed);input(fresh.after);assert(fresh.after.inputDispatchSequence>stopped.later.inputDispatchSequence);
 const attack=sample('continuous_attack_pipeline');routed(attack.completed);input(attack.after);assert.equal(attack.completed.result.steps.filter(step=>step.stepType==='mouse.button').length,6);
 assert.deepEqual(attack.timing,attack.completed.result.timing);assert.equal(attack.timing.scope,'automation_pipeline_not_http_or_server_effect');assert.equal(attack.timing.timeSource,'monotonic_elapsed');assert.equal(attack.timing.stages.cleanup.calls,1);
 assert.equal(attack.ordinaryButtonPairs,3);assert.equal(attack.cleanupCalls,1);for(const field of ['attackHitConfirmed','serverAcceptanceConfirmed','latencyRatioClaimed'])assert.equal(attack[field],false);assert.equal(attack.actAuthority,'PLAYTEST');assert(Number.isFinite(attack.observedTerminalMillis)&&attack.observedTerminalMillis>=0);
 const expiry=sample('continuous_lease_expiry');assert.equal(typeof expiry.renewed.leaseId,'string');assert.equal(expiry.renewed.leaseId,fresh.leaseId);input(expiry.held,340);
 assert.equal(expiry.expired.mode,'READ');assert.equal(expiry.expired.takeoverActive,false);assert.equal(expiry.expired.reconsentRequired,false);assert.equal(expiry.expired.modeTransitionReason,'lease_expired');released(expiry.waited);clean(expiry.after);assert.equal(expiry.status.status,'available');assert.equal(expiry.localLeaseForgottenOnlyAfterConfirmedExpiry,true);
 const mcp=sample('continuous_mcp_release_inputs');assert.equal(mcp.transport,'real_mcp_stdio');assert.equal(mcp.initializeServerInfo.name,'minecraft-protocol-companion');assert.equal(typeof mcp.expectedPackageVersion,'string');assert(mcp.expectedPackageVersion.length>0);assert.equal(mcp.initializeServerInfo.version,mcp.expectedPackageVersion);
 assert.equal(mcp.cached.leaseId,mcp.acquired.leaseId);assert.equal(typeof mcp.cached.leaseId,'string');completed(mcp.down);assert.equal(mcp.down.result.cleanupOnComplete,false);input(mcp.held,340);stop(mcp.stopped);released(mcp.waited);released(mcp.asserted);
 assert.equal(mcp.before.mode,'TAKEOVER');assert.equal(mcp.after.mode,'TAKEOVER');assert.equal(mcp.after.reconsentRequired,false);assert.deepEqual(mcp.before.modeVersion,mcp.after.modeVersion);assert.equal(mcp.cachedLeasePreserved,true);assert.equal(mcp.leaseReturnedBy,'owning_native_runner');completed(mcp.fresh);assert.equal(mcp.returned.mode,'READ');
 const finished=sample('continuous_input_complete');assert.equal(finished.target,target);assert.equal(finished.maximumMillis,180000);assert(Number.isFinite(finished.elapsedMillis)&&finished.elapsedMillis>=0&&finished.elapsedMillis<=finished.maximumMillis);assert.equal(finished.checks,10);assert.equal(finished.minimumPipelinePostIntervalMillis,550);clean(finished.finalInput);
 for(const field of ['physicalEscTested','networkDisconnectTested','stateStreamTested','nativeWorldEpochCrossServerGuarantee'])assert.equal(finished[field],false);
 assert.equal(finished.cleanupBarrierNotInferredFromHeldCounts,true);assert.equal(finished.clientShutdownOwnedByOuterRunner,true);assert.equal(finished.persistentWriteCalls,0);
 return{status:'PASS',checks:10,evidence:'development_runtime_native_and_mcp',crossRequestHeldInput:true,defaultCleanupOfPriorHeldInput:true,strongStopActiveAndQueued:true,sameLeaseResumesAfterStop:true,leaseExpiryCleanup:true,realMcpReleaseInputs:true,cleanShutdown:true,physicalEscTested:false,networkDisconnectTested:false,stateStreamTested:false,nativeWorldEpochCrossServerGuarantee:false,serverAcceptanceConfirmed:false,attackHitConfirmed:false,latencyRatioClaimed:false};
}

export function collaborationIncrementProof(result,target,scenario){
 assert.equal(result.target,target);assert.equal(result.scenario,scenario);assert.equal(result.status,'PASS');
 assert.equal(result.cleanShutdown,true);assert.equal(result.sourceChangedDuringRun,false);assert.equal(result.persistentWriteCalls,0);
 const checks=scenario==='resource-version'?14:8;
 assert.equal(result.fixturePolicy,'immutable_baseline_restored');assert.equal(result.checks.length,checks);assert(result.checks.every(row=>row.status==='PASS'));
 const samples=result.actionSamples??[],find=name=>{const value=samples.find(row=>row.case===name);assert(value,'Missing actual collaboration sample '+name);return value;};
 if(scenario==='mode-collaboration'){
  assert.equal(find('native_operate').receipt.route,'NATIVE');
  const chunks=find('takeover_chunk_fields');assert.deepEqual(Object.keys(chunks.takeover).sort(),Object.keys(chunks.read).sort());
  assert.equal(chunks.takeover.loaded,true);assert.equal(chunks.takeover.loadRequested,false);assert(Array.isArray(chunks.takeover.scheduledBlockTicks));assert.equal(chunks.remote.available,false);
  assert.equal(find('input_handback').physicalEscTested,false);
 }else if(scenario==='command-discovery-batch'){
  const batch=find('native_command_batch').batch;assert.equal(batch.submittedCount,3);assert.equal(batch.requiresControlLease,false);assert.equal(batch.inputDispatched,false);
  assert.equal(batch.effectConfirmed,false);assert.equal(batch.serverFeedbackCorrelated,false);
  const boundary=find('native_batch_boundary').boundary;assert.equal(boundary.commandCount,32);assert.equal(boundary.submittedCount,32);
  assert.equal(find('native_batch_stops_on_first_error').result.commands[2].status,'not_attempted');find('mcp_command_discovery_batch');
 }else if(scenario==='server-completion'){
  const sample=name=>{const rows=samples.filter(row=>row.case===name);assert.equal(rows.length,1,name);return rows[0];};
  const server=value=>{
   assert.equal(value.source,'current_player_server_command_suggestions');assert.equal(value.completionSource,'SERVER');assert.equal(value.completionScope,'server_current_player');assert.equal(value.dataCategory,'USER');
   assert.equal(value.serverCompletionRequested,true);assert.equal(value.serverCompletionSupported,true);assert.equal(value.serverAcceptanceConfirmed,false);assert.equal(value.commandExecuted,false);assert.equal(value.inputDispatched,false);assert.equal(value.requiresControlLease,false);
   assert.equal(value.humanFutureModified,false);assert.equal(value.humanRequestsHavePriority,true);assert.equal(value.serverBudgetShared,true);assert.equal(value.maxAgentCompletionsInFlight,1);
   assert(Array.isArray(value.suggestions));
  };
  const help=value=>{const found=value.suggestions.find(row=>row.text==='help');assert(found);assert.equal(found.start,0);assert.equal(found.end,2);};
  const local=sample('server_completion_local_default').local;assert.equal(local.source,'current_player_native_command_tree');assert.equal(local.completionScope,'client_local_only');assert.equal(local.serverCompletionRequested,false);assert.equal(local.commandExecuted,false);assert.equal(local.inputDispatched,false);assert.equal(local.requiresControlLease,false);help(local);
  const actual=sample('server_completion_actual_reply').result;server(actual);help(actual);
  const bounded=sample('server_completion_bounded').result;server(bounded);assert(bounded.suggestionCount>1);assert.equal(bounded.suggestions.length,1);assert.equal(bounded.suggestionsTruncated,true);assert.equal(bounded.completionComplete,false);assert.equal(bounded.nativeServerMayLimitSuggestions,true);
  const concurrent=sample('server_completion_concurrency').values;assert.equal(concurrent.length,2);assert.equal(concurrent.filter(row=>row.status===200).length,1);server(concurrent.find(row=>row.status===200).data);
  const denied=concurrent.find(row=>row.status!==200);assert.equal(denied.status,denied.data.error==='PLAYER_COMMAND_COMPLETION_BUSY'?409:429);assert(['PLAYER_COMMAND_COMPLETION_BUSY','PLAYER_COMMAND_COMPLETION_RATE_LIMITED'].includes(denied.data.error));
  const chat=sample('server_completion_preserves_chat');server(chat.result);assert(Array.isArray(chat.before)&&chat.before.length>0);assert.deepEqual(chat.after,chat.before);assert(chat.before.some(row=>typeof row.value==='string'&&row.value.length>0));assert(chat.before.every(row=>typeof row.focused==='boolean'));
  const mcp=sample('mcp_server_completion');assert.equal(mcp.target,target);server(mcp.result);help(mcp.result);
 }else if(scenario==='resource-version'){
  const contract=find('resource_inspection_contract').contract;assert.equal(contract.operations.length,6);assert.equal(contract.grantsMutationPermission,false);
  const error=find('resource_version_actual_mcp').error;assert.equal(error.code,'STALE_RESOURCE_REVISION');assert.equal(error.reinspectRequired,true);assert.equal(error.retrySafe,false);
  assert(Number.isSafeInteger(error.actualRevision));assert.notEqual(error.actualRevision,error.expectedRevision);find('safe_stale_reinspect');
  const arrange=find('resource_version_fixture_arrange');assert.equal(arrange.nativePauseObserved,true);assert.equal(arrange.ownerThreadReadsWhilePaused,true);
  const chains=samples.filter(row=>row.case==='resource_version_typed_chain');
  assert.deepEqual(chains.map(row=>row.operation).sort(),['player.health.set','player.attribute.set','entity.no_gravity.set','block_entity.custom_name.set','menu.slot.set'].sort());
  const chainProof=(chain,consumer)=>{
   assert.equal(chain.consumer,consumer);assert.equal(chain.authority,'DEBUG_PRIVILEGED');assert.equal(chain.actInputDelta,0);
   for(const input of [chain.inputBefore,chain.inputAfter]){assert(Number.isSafeInteger(input.inputDispatchSequence)&&input.inputDispatchSequence>=0);assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);}
   assert.equal(chain.inputBefore.inputDispatchSequence,chain.inputAfter.inputDispatchSequence);
   for(const ref of [chain.original,chain.applied.beforeResourceVersion,chain.applied.afterResourceVersion,chain.reinspected,chain.restored.beforeResourceVersion,chain.restored.afterResourceVersion])assert(Number.isSafeInteger(ref.revision)&&ref.revision>=0);
   assert.equal(chain.applied.beforeResourceVersion.revision,chain.original.revision);assert(chain.applied.afterResourceVersion.revision>chain.original.revision);
   assert.equal(chain.applied.gameplayEvidence,false);assert.equal(chain.applied.evidenceContaminated,true);assert.equal(chain.applied.storageAccessed,false);
   assert.equal(chain.stale[consumer==='native_http'?'error':'code'],'STALE_RESOURCE_REVISION');assert.equal(chain.stale.expectedRevision,chain.original.revision);assert.equal(chain.stale.actualRevision,chain.reinspected.revision);assert.equal(chain.stale.reinspectRequired,true);assert.equal(chain.stale.retrySafe,false);
   assert.equal(chain.restored.beforeResourceVersion.revision,chain.reinspected.revision);assert(chain.restored.afterResourceVersion.revision>chain.reinspected.revision);
  };
  for(const chain of chains){assert.equal(chain.inspectionSource,'formal_deep_observation');chainProof(chain,'native_http');}
  const mcp=find('resource_version_menu_actual_mcp');assert.equal(mcp.operation,'menu.slot.set');chainProof(mcp,'mcp_stdio');
  const clean=find('resource_version_extended_cleanup');assert.equal(clean.entityRemoved,true);assert.equal(clean.restoredReadMode,true);assert.equal(clean.persistentWriteCalls,0);assert.deepEqual(clean.cleanupErrors,[]);
 }else throw new Error('Unsupported collaboration scenario');
 return{scenario,checks,evidence:'development_runtime_native_and_mcp',cleanShutdown:true,physicalEscTested:false,...(scenario==='server-completion'?{serverCompletion:true,serverBudgetShared:true,humanCompletionConcurrentLiveTested:false}:{})};
}

export function uiRegressionProof(result,target){
  assert.equal(result.target,target);assert.equal(result.status,'PASS');assert.equal(result.scenario,'all');assert.equal(result.phase,'test');
  assert.equal(result.runtime,'development_native');assert.equal(result.cleanShutdown,true);assert.equal(result.processExitCode,0);assert.equal(result.endpointRemoved,true);assert.equal(result.sourceChangedDuringRun,false);assert.equal(result.persistentWriteCalls,0);
  assert.equal(result.noMultimodal,true);assert.equal(result.screenshotAssertions,0);assert.match(result.fixtureSha256,/^[0-9a-f]{64}$/);assert(result.checks.length>=50);assert(result.checks.every(c=>c.status==='PASS'));
  const selection=result.worldSelectionSamples;assert.equal(selection?.length,1);assert.equal(selection[0].selectedBefore,false);assert.equal(selection[0].status,'PASS');assert.equal(selection[0].worldEntered,true);assert.equal(selection[0].noHeldInput,true);
  for(const transport of ['mcp_stdio','web_http'])assert(result.vitalSamples.some(s=>s.kind==='vital_consumer'&&s.transport===transport&&s.status==='PASS'&&s.readWithoutLease===true));
  const native=result.uiDebugSamples??[],scrolls=['creative.scrollbar','loom.scrollbar','stonecutter.scrollbar','merchant.scrollbar'];
  const scrollKinds=[];
  for(const key of scrolls){
    const rows=native.filter(s=>s.case==='native_screen_scroll'&&s.controlKey===key);assert.equal(rows.length,1,key);
    const sample=rows[0];assert.equal(typeof sample.enabled,'boolean');assert.equal(sample.restored,true);assert.equal(sample.inputDelta,0);
    if(sample.enabled){assert.equal(sample.selectionUnchanged,true);scrollKinds.push('debug_screen_scroll_'+key.split('.')[0]);}
    else{assert.equal(target,'1.20.1-forge');assert.equal(key,'stonecutter.scrollbar');assert.equal(sample.original,0);}
  }
  const hud=['hud.title','hud.subtitle','hud.actionbar'];
  for(const field of hud){
    const rows=native.filter(s=>s.case==='native_hud_text'&&s.field===field);assert.equal(rows.length,1,field);
    const sample=rows[0];assert.equal(sample.restoredText,true);assert.equal(sample.inputDelta,0);assert.equal(sample.worldMutation,false);
    assert.equal(sample.lifetimeRestored,false);assert.equal(sample.richFormattingRestored,false);
    const rejects=native.filter(s=>s.case==='hud_message_rejected'&&s.field===field);
    for(const [code,count]of [['INVALID_UI_DEBUG_VALUE',4],['VALUE_PRECONDITION_FAILED',1],['STALE_RESOURCE_REVISION',1]]){
      const matches=rejects.filter(s=>s.code===code);assert.equal(matches.length,count,field+' '+code);assert(matches.every(s=>s.inputDelta===0));
    }
  }
  const guards=native.filter(s=>s.case==='native_hud_content_guard_probe');assert.equal(guards.length,1);
  const guard=guards[0];assert.equal(guard.status,'PASS');assert.equal(guard.target,target);assert.equal(guard.probeOnly,true);assert.equal(guard.checks,10);
  assert.equal(guard.playerInputUsed,false);assert.equal(guard.worldMutationUsed,false);assert(Number.isSafeInteger(guard.processId)&&guard.processId>0);
  assert.equal(guard.processId,result.launchAttestation?.processId);
  assert(Date.parse(guard.testedAt)>=Date.parse(result.launchAttestation?.titleReadyAt)&&Date.parse(guard.testedAt)<=Date.parse(result.testedAt),'HUD guard must belong to this actual Loader run');
  const toastGuards=native.filter(s=>s.case==='native_toast_content_guard_probe');assert.equal(toastGuards.length,1);
  const toastGuard=toastGuards[0];assert.equal(toastGuard.status,'PASS');assert.equal(toastGuard.target,target);assert.equal(toastGuard.probeOnly,true);assert.equal(toastGuard.checks,9);
  assert.equal(toastGuard.nativeTickVersionStable,true);assert.equal(toastGuard.sameValueResetInvalidates,true);assert.equal(toastGuard.resetGenerationChecks,2);
  assert.equal(toastGuard.resetConsumption,['1.20.1-forge','1.21.1-neoforge'].includes(target)?'native_render_on_probe_owned_toast_no_pixel_canvas':'native_update_on_probe_owned_toast');
  assert.equal(toastGuard.playerInputUsed,false);assert.equal(toastGuard.worldMutationUsed,false);assert.equal(toastGuard.processId,result.launchAttestation?.processId);
  assert(Date.parse(toastGuard.processStartedAt)<=Date.parse(toastGuard.testedAt)&&Date.parse(toastGuard.testedAt)<=Date.parse(result.testedAt),'Toast guard must belong to this Loader process lifetime');
  for(const name of ['native_tutorial_progress','native_system_toast_reset']){
    const rows=native.filter(s=>s.case===name);assert.equal(rows.length,1,name);assert.equal(rows[0].inputDelta,0);assert.equal(rows[0].worldMutation,false);
    if(name==='native_tutorial_progress')assert.equal(rows[0].restored,true);
    else{assert.equal(rows[0].restoredText,true);assert.equal(rows[0].lifetimeRestored,false);assert.equal(rows[0].richFormattingRestored,false);}
  }
  for(const kind of ['progress','static',...(target==='1.20.1-forge'?[]:['system'])]){
    const rows=native.filter(s=>s.case==='native_toast_hide'&&s.kind===kind);assert.equal(rows.length,1,kind);assert.equal(rows[0].oneWay,true);assert.equal(rows[0].inputDelta,0);assert.equal(rows[0].worldMutation,false);
  }
  if(target==='1.20.1-forge'){
    assert(!native.some(s=>s.case==='native_toast_hide'&&s.kind==='system'),'Forge has no native SystemToast.forceHide');
    const absent=native.filter(s=>s.case==='native_system_toast_hide_absent');assert.equal(absent.length,1);assert.equal(absent[0].nativeMethodAbsent,true);assert.equal(absent[0].inputDelta,0);
    const cleanup=native.filter(s=>s.case==='legacy_system_toast_fixture_cleanup');assert.equal(cleanup.length,1);assert.equal(cleanup[0].probeOnly,true);assert.equal(cleanup[0].notAProductHideCapability,true);
  }
  for(const operation of ['ui.slider.set','ui.toast.reset'])for(const code of ['INVALID_UI_DEBUG_VALUE','VALUE_PRECONDITION_FAILED','STALE_RESOURCE_REVISION'])
    assert(native.some(s=>s.case==='native_toast_rejected'&&s.operation===operation&&s.code===code&&s.inputDelta===0),operation+' '+code);
  const pans=result.advancementPanSamples??[];
  assert.equal(pans.length,3,'All native advancement canvas branches are required');
  const pan=name=>{const rows=pans.filter(s=>s.case===name);assert.equal(rows.length,1,name);assert.equal(rows[0].inputDelta,0);return rows[0];};
  const point=value=>{assert.deepEqual(Object.keys(value).sort(),['x','y']);for(const axis of ['x','y'])assert.equal(Number.isFinite(value[axis]),true);};
  const fixed=pan('native_advancement_pan_fixed_axes');point(fixed.original);assert.equal(fixed.centeredByNativeRenderer,true);assert.equal(fixed.bothFixed,true);assert.equal(fixed.unrequestedZeroing,false);
  const ranged=pan('native_advancement_pan_ranged');point(ranged.original);point(ranged.value);assert.notDeepEqual(ranged.value,ranged.original);assert.equal(ranged.centeredByNativeRenderer,true);assert.equal(ranged.restored,true);assert.equal(ranged.staleRejected,true);assert.equal(ranged.playerLeaseRequired,false);
  assert.equal(pan('native_advancement_pan_screen_replaced').rejected,true);
  const kinds=['widget_active','widget_visible','native_list','native_slider','native_editbox','book','book_title','sign','hanging_sign',...scrollKinds,...hud.map(key=>'debug_'+key.replace('.','_')),'debug_tutorial_progress','debug_system_toast','debug_advancement_canvas'];
  const samples=result.editorSamples??[];
  for(const kind of kinds){
    const mcp=samples.filter(s=>s.editor===kind&&s.transport==='mcp_stdio_debug');assert.equal(mcp.length,1,kind);
    assert.equal(mcp[0].status,'PASS');assert.equal(mcp[0].inputDelta,0);assert.equal(mcp[0].playerLeaseAcquired,false);assert.equal(mcp[0].typedRecovery,true);assert.equal(mcp[0].userOperationGetWaitAssertRejected,true);assert.equal(mcp[0].diagnosticCancelRedacted,true);
    const web=samples.filter(s=>s.editor===kind&&s.transport==='web_user_isolation');assert.equal(web.length,1,kind);
    assert.equal(web[0].status,'PASS');assert.equal(web[0].operateUserSnapshotAvailable,true);assert.equal(web[0].debugMetadataLeaked,false);assert.equal(web[0].automaticLeaseAcquired,false);
  }
  for(const kind of ['book','book_title','sign','hanging_sign'])for(const transport of ['mcp_stdio','web_http']){
    const user=samples.filter(s=>s.editor===kind&&s.transport===transport);assert.equal(user.length,1);assert.equal(user[0].status,'PASS');
    if(kind==='book_title'&&transport==='mcp_stdio'){
      assert.equal(user[0].emptyRestoreVerified,true);
      assert.equal(user[0].textReplaceStrategy,['1.20.1-forge','1.21.1-neoforge'].includes(target)?'backspace_from_end':'select_all');
    }
  }
  assert(result.uiDebugSamples?.length>=134,'Native widget and full editor guard matrix is required');
  return{status:'PASS',checks:result.checks.length,scope:'existing_full_ui_suite_not_all_vanilla',semanticUnselectedWorldDoubleClick:true,uiDiagnosticConsumers:kinds.length,webUserIsolation:kinds.length,nativeScreenScrolls:scrolls.length,nativeHudMessages:hud.length,nativeHudGuardChecks:guard.checks,nativeToastGuardChecks:toastGuard.checks,nativeToastReset:true,nativeTutorialProgress:true,nativeToastHideCount:target==='1.20.1-forge'?2:3,nativeAdvancementCanvas:true,cleanShutdown:true};
}

export function consumerPriorityProof(result,target){
  assert.equal(result.target,target);assert.equal(result.status,'PASS');assert(['full','consumer'].includes(result.scenario));
  assert.equal(result.cleanShutdown,true);assert.equal(result.sourceChangedDuringRun,false);assert.equal(result.persistentWriteCalls,0);
  const cases=result.checks?.filter(c=>c.name.startsWith('consumer '));assert.equal(cases?.length,9);assert(cases.every(c=>c.status==='PASS'));
  const samples=result.actionSamples??[],find=name=>{const matches=samples.filter(s=>s.case===name);assert.equal(matches.length,1,name);return matches[0];};
  const motion=find('consumer_motion');assert.equal(motion.after.dataCategory,'USER');assert.equal(motion.after.authority,'CLIENT_KNOWN');assert.equal(motion.heldInput,0);assert.equal(motion.result.state,'completed');
  assert(Math.hypot(motion.after.x-motion.before.x,motion.after.z-motion.before.z)>.02);assert(Math.abs(motion.after.yaw-motion.before.yaw)>.01);
  const frames=samples.filter(s=>s.case==='consumer_frame');assert.deepEqual(frames.map(s=>s.stage),['initial_world','after_resource_reload','after_dimension_roundtrip','saved_title','saved_world_reentry']);
  for(const frame of frames){assert.match(frame.sha256,/^[0-9a-f]{64}$/);assert(frame.bytes>100);assert.deepEqual(frame.dimensions,[1920,1080]);assert.equal(frame.overlayIdentity,0);assert.equal(frame.thirdPartyConvergenceClaimed,false);}
  const recording=find('consumer_recording');assert(recording.status.artifactReady);assert(recording.status.writtenFrames>0);assert(recording.status.writtenStates>0);assert(recording.artifactBytes>100);assert.equal(recording.readWithoutLease,true);
  const dimensions=samples.filter(s=>s.case==='consumer_dimension');assert.deepEqual(dimensions.map(s=>s.dimension),['minecraft:the_nether','minecraft:overworld']);for(const sample of dimensions){assert.equal(sample.player.dimension,sample.dimension);assert.equal(sample.player.dataCategory,'USER');}
  const reads=find('consumer_provider_owner_reads');assert.equal(reads.readWithoutArm,true);assert.equal(reads.readWithoutLease,true);assert.equal(reads.providers.length,4);assert(reads.providers.every(p=>p.status==='completed'&&typeof p.data.threadName==='string'));
  const mutation=find('consumer_provider_mutation');assert.equal(mutation.authority,'DEBUG_PRIVILEGED');assert.equal(mutation.inputLease,false);assert.equal(mutation.changed.gameplayEvidence,false);assert.equal(mutation.changed.mechanism,'registered_provider_typed_mutation');assert(mutation.changed.afterResourceVersion.revision>mutation.changed.beforeResourceVersion.revision);
  const deadline=find('consumer_provider_deadline');assert.equal(deadline.error,'REQUEST_DEADLINE_EXCEEDED');assert.equal(deadline.noLateEffect,true);assert(deadline.windowMs>=650);
  const mcp=find('consumer_mcp_provider');assert.equal(mcp.transport,'real_mcp_stdio');assert.equal(mcp.readWithoutArm,true);assert.equal(mcp.readWithoutLease,true);
  return{status:'PASS',checks:cases.length,frames:frames.length,providerRead:true,providerTypedMutation:true,providerDeadlineNoLateEffect:true,realMcp:true,thirdPartyConvergenceClaimed:false};
}

export function consumerFeedbackProof(result,target){
  assert.equal(result.target,target);assert.equal(result.status,'PASS');assert.equal(result.scenario,'full');assert.equal(result.cleanShutdown,true);assert.equal(result.sourceChangedDuringRun,false);assert.equal(result.persistentWriteCalls,0);
  const checks=result.checks.filter(c=>c.name.startsWith('feedback '));assert.equal(checks.length,14);assert(checks.every(c=>c.status==='PASS'));
  const samples=result.actionSamples??[],find=name=>{const matches=samples.filter(s=>s.case===name);assert.equal(matches.length,1,name);return matches[0];};
  const quota=(value,category)=>{assert(value.retryAfterMillis>0&&value.retryAfterMillis<=60000);const b=value.rateLimit;assert.equal(b.operationCategory,category);assert(['principal','connection'].includes(b.scope));assert(b.capacity>0);assert.equal(b.remaining,0);assert(b.refillPerSecond>0);assert.equal(b.cost,1);};
  const read=find('feedback_read_quota');quota(read.native,'world');quota(read.mcp,'world');assert.equal(read.realMcp,true);assert.equal(read.inputLease,false);assert.equal(read.inputDispatchDelta,0);
  const pipeline=find('feedback_pipeline_quota');quota(pipeline,'pipeline');assert.equal(pipeline.inputRetried,false);assert.equal(pipeline.inputDispatchDelta,0);assert.equal(pipeline.heldKeys,0);assert.equal(pipeline.heldButtons,0);
  const receipt=value=>{assert.equal(value.accepted,true);assert.equal(value.effectConfirmed,false);assert.equal(value.serverValidationObserved,false);assert.equal(value.gameplayAcceptanceEligible,false);const f=value.commandFeedback;assert.equal(f.association,'temporal_not_guaranteed');assert.equal(f.windowMillis,500);assert.equal(f.complete,true);assert.equal(f.readable,true);assert(f.messages.length>0&&f.messages.length<=16);for(const m of f.messages){assert.equal(m.source,'client_chat');assert([...m.text].length<=512);assert(m.offsetMillis>=0&&m.offsetMillis<=500);}};
  const command=find('feedback_native_command');receipt(command.success);receipt(command.error);assert.equal(command.correlationGuaranteed,false);assert.equal(command.normalPlayerPath,true);
  const preflight=find('feedback_command_preflight');assert.equal(preflight.error,'PLAYER_COMMAND_NOT_AVAILABLE');assert.equal(preflight.inputDispatchDelta,0);
  const mcpCommand=find('feedback_mcp_command');receipt(mcpCommand.result);assert.equal(mcpCommand.realMcp,true);
  const title=find('feedback_title_ready');assert.equal(title.result.passed,true);assert.equal(title.result.evidence.pageReady,true);assert.equal(title.result.evidence.overlayIdentity,0);assert.equal(title.inputDispatchDelta,0);assert.equal(title.unknownWorld.available,false);assert(!('block'in title.unknownWorld));
  const blocks=find('feedback_block_readiness');assert.equal(blocks.air.evidence.available,true);assert.equal(blocks.air.evidence.block,'minecraft:air');assert.equal(blocks.loadingRequested,false);for(const b of [blocks.unloaded,blocks.stillUnloaded]){assert.equal(b.available,false);assert.equal(b.chunkLoadRequested,false);}
  const destination=find('feedback_destination_ready');assert.equal(destination.ready.passed,true);assert.equal(destination.ready.evidence.authority,'CLIENT_KNOWN');assert.equal(destination.readDoesNotLoad,true);
  const unknown=find('feedback_player_unknown');assert.equal(unknown.player.available,false);for(const key of ['gameMode','onGround','flying'])assert.equal(unknown.player[key],'unknown');
  const player=value=>{assert.equal(value.available,true);assert.equal(value.authority,'CLIENT_KNOWN');assert.equal(value.dataCategory,'USER');assert(['survival','creative','adventure','spectator'].includes(value.gameMode));assert.equal(typeof value.onGround,'boolean');assert.equal(typeof value.flying,'boolean');};
  const nativePlayer=find('feedback_user_player');for(const value of [nativePlayer.player,nativePlayer.provider.data,nativePlayer.frame.reads[0].data])player(value);assert.equal(nativePlayer.inputDispatchDelta,0);assert.equal(nativePlayer.condition.passed,true);
  const mcpPlayer=find('feedback_mcp_player');player(mcpPlayer.player);player(mcpPlayer.frame.reads[0].data);assert.equal(mcpPlayer.realMcp,true);assert.equal(mcpPlayer.inputLease,false);
  const mode=find('feedback_player_mode');player(mode.before);player(mode.changed);assert.notEqual(mode.before.gameMode,mode.changed.gameMode);
  for(const name of ['feedback_native_hit','feedback_mcp_hit']){const hit=find(name);assert.equal(hit.operation.errorCode,'WORLD_TARGET_NOT_HIT');assert.equal(hit.operation.state,'failed');assert.deepEqual(hit.hit,hit.operation.errorDetails.actualHit);assert.equal(hit.hit.kind,'block');assert.equal(hit.hit.dataCategory,'USER');assert.equal(hit.hit.authority,'CLIENT_KNOWN');assert.equal(hit.hit.face,'north');assert.deepEqual(hit.hit.block,{x:0,y:81,z:2});assert(hit.hit.distance>0&&hit.hit.distance<=32);assert.equal(hit.cleanupDispatches,1);assert.equal(hit.playerInputDispatches,0);assert.equal(hit.afterInput.inputDispatchSequence-hit.beforeInput.inputDispatchSequence,1);assert.equal(hit.afterInput.pressedKeyCount,0);assert.equal(hit.afterInput.pressedButtonCount,0);if(name==='feedback_mcp_hit')assert.equal(hit.realMcp,true);}
  return{status:'PASS',checks:14,realMcpChecks:4,readQuota:true,pipelineQuotaNoPlayerInput:true,temporalCommandFeedback:true,pageReady:true,clientBlockReady:true,clientPlayerFlags:true,boundedActualHit:true,serverExecutionClaimed:false};
}

export function commandConfigurationProof(result,target) {
  assert.equal(result.target,target);assert.equal(result.scenario,'command-configuration');assert.equal(result.status,'PASS');
  assert.equal(result.evidence,'development_runtime_native');assert.equal(result.fullSmoke,false);assert.equal(result.fullWorldMilestone,false);
  assert.equal(result.sourceChangedDuringRun,false);assert.equal(result.cleanShutdown,true);assert.equal(result.maximumHeapBytes,4294967296);assert.equal(result.persistentWriteCalls,0);
  assert.equal(result.checks?.length,16);assert(result.checks.every(c=>c.status==='PASS'));
  const samples=result.actionSamples;
  const submitted=samples.filter(s=>s.case==='configuration_submitted');
  assert.deepEqual(submitted.map(s=>s.kind),['COMMAND_BLOCK','COMMAND_MINECART','JIGSAW_BLOCK','STRUCTURE_UPDATE','STRUCTURE_SCAN']);
  const partial=samples.filter(s=>s.case==='configuration_partial_failure');assert.equal(partial.length,1);
  const operations=[...submitted,...partial];
  for(const sample of operations){
    const state=sample.result;assert.equal(state.state,sample===partial[0]?'failed':'completed');assert.equal(state.result.partial===true,sample===partial[0]);
    assert.equal(state.result.evidenceContaminated,true);assert.equal(state.result.commandSubmissions.length,1);
    const receipt=state.result.commandSubmissions[0],jigsaw=sample.kind==='JIGSAW_BLOCK',structure=sample.kind.startsWith('STRUCTURE_');
    assert.equal(receipt.configurationKind,sample.kind);assert.equal(receipt.configurationPayloadType,structure?'STRUCTURE_PARAMETERS':jigsaw?'BLOCK_STATE':'COMMAND');
    assert.equal(receipt.configuredCommandEffect,jigsaw||structure?'STATE_MUTATION':'INFORMATION');assert.equal(receipt.effectClassification,'STATE_MUTATION');
    assert.equal(receipt.classificationSource,'native_player_command_configuration');assert.equal(receipt.mode,'DEBUG_PRIVILEGED');assert.equal(receipt.evidenceContaminated,true);assert.equal(receipt.effectConfirmed,false);assert.equal(receipt.gameplayAcceptanceEligible,false);
  }
  assert.equal(partial[0].result.errorCode,'PLAYER_COMMAND_UNCLASSIFIED');
  const hash=text=>createHash('sha256').update(text).digest('hex');
  for(const [sample,text]of [[submitted[2],'minecraft:stone'],[partial[0],'minecraft:air']]){
    assert.equal(sample.after.jigsawConfigurationPackets,sample.initial.jigsawConfigurationPackets+1);
    assert.equal(sample.after.jigsawGenerationPackets,sample.initial.jigsawGenerationPackets);
    assert.equal(sample.after.storedJigsawFinalStateHash,hash(text));
    for(const probe of [sample.initial,sample.after]){assert.equal(probe.probeOnly,true);assert.equal(probe.chunkLoadRequested,false);assert.equal(probe.worldMutationUsedByProbe,false);assert.match(probe.attemptId,/^[0-9a-f]{32}$/);}
    assert.notEqual(sample.initial.attemptId,sample.after.attemptId);
  }
  const rejected=samples.filter(s=>s.case==='jigsaw_rejected');assert.equal(rejected.length,2);
  assert.deepEqual(rejected.map(s=>s.code),['PLAYER_COMMAND_OUT_OF_SCOPE','PLAYER_COMMAND_UNCLASSIFIED']);
  for(const sample of rejected){assert.equal(sample.result.state,'failed');assert.equal(sample.result.errorCode,sample.code);assert.equal(sample.after.jigsawConfigurationPackets,sample.initial.jigsawConfigurationPackets);assert.equal(sample.after.jigsawGenerationPackets,sample.initial.jigsawGenerationPackets);}
  for(const [sample,counter] of [[submitted[3],'structureUpdatePackets'],[submitted[4],'structureScanPackets']]){
    for(const key of ['structureUpdatePackets','structureScanPackets','structureSavePackets','structureLoadPackets'])assert.equal(sample.after[key],sample.initial[key]+(key===counter?1:0));
    assert.equal(sample.after.storedStructureNameHash,hash('minecraft:validation_structure'));assert.equal(sample.after.storedStructureMode,'SAVE');
    assert.notEqual(sample.initial.attemptId,sample.after.attemptId);
    for(const probe of [sample.initial,sample.after]){assert.equal(probe.probeOnly,true);assert.equal(probe.chunkLoadRequested,false);assert.equal(probe.worldMutationUsedByProbe,false);assert.match(probe.attemptId,/^[0-9a-f]{32}$/);}
  }
  assert.equal(submitted[4].after.storedStructureOffset,'0,0,2');assert.equal(submitted[4].after.storedStructureSize,'3,3,3');
  const structureRejected=samples.filter(s=>s.case==='structure_rejected');assert.equal(structureRejected.length,2);
  assert.deepEqual(structureRejected.map(s=>[s.kind,s.code]),[['STRUCTURE_SAVE','PLAYER_COMMAND_OUT_OF_SCOPE'],['STRUCTURE_LOAD','PLAYER_COMMAND_UNCLASSIFIED']]);
  for(const sample of structureRejected){
    assert.equal(sample.result.state,'failed');assert.equal(sample.result.errorCode,sample.code);assert.equal(sample.result.result?.commandSubmissions?.length??0,0);
    for(const key of ['structureUpdatePackets','structureScanPackets','structureSavePackets','structureLoadPackets','storedStructureNameHash','storedStructureMode','storedStructureOffset','storedStructureSize'])assert.equal(sample.after[key],sample.initial[key]);
  }
  const artifact=samples.find(s=>s.case==='configuration_artifact')?.checked;
  assert.equal(artifact?.status,'PASS');assert.equal(artifact.category,'USER');assert.equal(artifact.configurationAttemptEvents,8);assert.equal(artifact.configurationSubmissionEvents,8);assert(artifact.structuredStates>0);
  assert.equal(result.consumerScope,'native_command_configuration');assert.equal(result.consumerChecks?.length,6);
  assert(result.consumerChecks.every(c=>c.status==='PASS'&&c.inputUnchanged===true));
  assert.deepEqual(result.consumerChecks.map(c=>c.operationId).sort(),operations.map(s=>s.result.operationId).sort());
  return {checks:16,mcpChecks:6};
}

export function coreShowcaseProof(result,target) {
  assert.equal(result.target,target);
  assert.equal(result.purpose,'CORE_SHOWCASE_SMOKE');
  assert.equal(result.runtime,'loader_development_source_sets');
  assert.equal(result.result,'PASS');
  assert.equal(result.sourceChangedDuringRun,false);
  assert.equal(result.cleanup,'CLEAN_SHUTDOWN_PORT_RELEASED');
  assert.equal(result.maximumHeapBytes,4294967296);
  assert.equal(result.persistentWriteInvocations,0);
  assert.equal(result.visualAcceptance,'NOT_PERFORMED');
  assert.equal(result.unifiedAcceptance,'NOT_PERFORMED');
  assert(!result.fixtureRecoveryRequired);
  const names=['world-observe','world-interact','wait-evidence','persistent-read','cancel-cleanup'];
  assert.deepEqual(result.cases?.map(item=>item.case),names);
  for(const item of result.cases){assert.equal(item.result,'PASS');assert.equal(item.heldKeys,0);assert.equal(item.heldButtons,0);}
  const saved=result.cases[3].evidence;
  assert.deepEqual(saved?.map(item=>item.domain),['world','player','chunk']);
  for(const item of saved){assert.equal(item.dataSource,'PERSISTED');assert.equal(item.consistency,'last_saved_state');assert.equal(item.writeImplemented,false);}
  const capture=result.cases[2].evidence?.find(item=>item.contents);
  assert(capture?.contents.checksumsVerified>0);
  assert.equal(capture.contents.dataCategory,'USER');
  assert.equal(capture.operatorUiPixelAssertion,'NOT_PERFORMED');
  const cancel=result.cases[4].evidence?.find(item=>item.case==='cancel-cleanup');
  assert.equal(cancel?.terminal,'cancelled');assert.equal(cancel.mode,'READ');assert.equal(cancel.postCleanupInputDispatch,0);
  const hashes=result.sourceHashes;
  assert(hashes&&typeof hashes==='object');
  assert(hashes['components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core/ProbeTransport.java']);
  assert(hashes[`versions/${target}/target.properties`]);
  for(const [path,hash] of Object.entries(hashes)){
    assert(!path.startsWith('/')&&!path.includes('\\')&&!path.split('/').includes('..')&&!path.includes(':'));
    assert.match(hash,/^[0-9a-f]{64}$/);
  }
  return {checks:names.length,mcpChecks:0,mcpScope:'not_performed_by_showcase',evidence:'development_runtime_native_showcase',fixturePolicy:'owned_mutable_showcase_world',cleanShutdown:true};
}

if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)){
 try{
  assert.equal(process.argv[2],'--verify-continuous-publication');assert.equal(process.argv[4],'--repository-root');assert.equal(process.argv.length,6);
  const repo=resolve(process.argv[5]),manifest=resolve(process.argv[3]);assert.equal(relative(repo,manifest).replaceAll('\\','/'),'build/alpha-manifest.json');
  const report=JSON.parse(await readFile(manifest,'utf8'));
  const proof=report.publicationProfile===DEPENDENCY_PROFILE?await verifyDependencyPublication(report,repo):await verifyContinuousPublication(report,repo);
  console.log('Trial publication gate: PASS; profile '+report.publicationProfile+'; performance '+proof.performanceAcceptance.status+'; full Goal acceptance not claimed');
 }catch(error){console.error('Continuous-gameplay alpha refused: '+error.message);process.exitCode=1;}
}
