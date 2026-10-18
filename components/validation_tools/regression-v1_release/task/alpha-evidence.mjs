import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';

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
