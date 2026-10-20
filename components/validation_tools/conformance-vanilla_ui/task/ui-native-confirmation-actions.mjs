import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {resolve} from 'node:path';

export function verifyNativeConfirmationProof(value,{target,fixtureId,sequence,caseId,screenClass,returned,callbackAction}) {
  assert(screenClass.startsWith('net.minecraft.client.gui.'),'The fixture must contain an actual native screen class');
  assert.equal(value.probeOnly,true);assert.equal(value.arrangeAuthority,'DIAGNOSTIC_native_screen_fixture');
  assert.equal(value.target,target);assert.equal(value.fixtureId,fixtureId);assert.equal(value.sequence,sequence);assert.equal(value.case,caseId);
  assert.equal(value.nativeClass,screenClass);assert.equal(value.returnedToFixture,returned);
  if(callbackAction!==undefined)assert.equal(value.callbackAction,callbackAction);
  for(const field of ['actualFaultInduced','externalServiceUsed','worldFilesTouched','globalPackRepositoryTouched','serverListEntrySupplied'])assert.equal(value[field],false,field);
  assert.equal(value.experimentOutputCalls,0,'A cancelled experimental draft must not apply its output');
  if(['experiments','experimental_confirm'].includes(caseId))assert.deepEqual(value.selectedPacksAfter,value.selectedPacksBefore,'The private native pack selection changed');
  return value;
}

// These are native UI checks, not actual OOM, backup, account acceptance or external-service tests.
export async function nativeConfirmationActions({api,repo,target,tree,wait,nodes,ui,title,openFixture,check,samples,readFixtureProof}) {
  assert(['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric'].includes(target));
  const probePath=resolve(repo,'validations/conformance-vanilla_ui/result',target+'-native-confirmation-probe.json');
  async function clean() {
    const input=await api('GET','/v0/input/state');assert.equal(input.pressedKeyCount,0);assert.equal(input.pressedButtonCount,0);
    assert.equal(input.inputCleanupPending,false);assert.equal(input.inputCleanupFailed,false);return input;
  }
  async function page(name) {
    const value=await wait(value=>value.screenClass.endsWith(name),'native confirmation '+name);
    assert.equal(value.dataCategory,'USER');assert.equal(value.treeTruncated,false);return value;
  }
  async function button(keys=[],label) {
    const value=await tree(),found=nodes(value).filter(node=>node.visible!==false&&node.active!==false&&node.actions?.includes('click')
      &&(keys.includes(node.translationKey)||node.label===label));
    assert.equal(found.length,1,'One safe native confirmation target: '+(label??keys.join('|')));
    const result=await ui({nodeId:found[0].nodeId});return {node:found[0],result};
  }
  async function proof() {
    const value=readFixtureProof?await readFixtureProof():JSON.parse(await readFile(probePath,'utf8'));
    assert.equal(value.target,target);assert.equal(value.probeOnly,true);
    assert.equal(typeof value.fixtureId,'string');assert(value.fixtureId.length>=16);
    assert(Number.isSafeInteger(value.sequence)&&value.sequence>=0);assert(Number.isSafeInteger(value.processId)&&value.processId>0);return value;
  }
  async function menu() {
    if((await tree()).screenClass.endsWith('UiNativeConfirmationFixtureScreen'))return page('UiNativeConfirmationFixtureScreen');
    await title();assert.equal((await api('GET','/v0/player?category=USER')).available,false,'All confirmation fixtures run after normal world shutdown');
    await openFixture();
    if(target==='26.3-neoforge'){await button([],'UI Navigation Fixtures');await page('UiNavigationFixtureScreen');}
    await button([],'More Native Screens (Fixture)');return page('UiNativeConfirmationFixtureScreen');
  }
  async function open(caseId,label,screen) {
    await menu();const baseline=await proof(),before=await clean();await button([],label);
    const value=await page(screen),expected={target,fixtureId:baseline.fixtureId,sequence:baseline.sequence+1,caseId,screenClass:value.screenClass};
    const arranged=verifyNativeConfirmationProof(await proof(),{...expected,returned:false,callbackAction:'none'});
    return {caseId,before,arranged,expected,value,actions:[]};
  }
  async function finish(context,keys,callbackAction) {
    const nativeTitleReturn=target==='1.20.1-forge'&&context.caseId==='onboarding';
    if(target==='1.20.1-forge'&&['onboarding','backup'].includes(context.caseId))callbackAction='none';
    context.actions.push(await button(keys));const afterPage=await page(nativeTitleReturn?'TitleScreen':'UiNativeConfirmationFixtureScreen');
    const after=await clean(),returned=verifyNativeConfirmationProof(await proof(),{...context.expected,returned:!nativeTitleReturn,callbackAction});
    assert(after.inputDispatchSequence>context.before.inputDispatchSequence);
    samples.push({case:'native_confirmation_'+context.caseId,arrangeAuthority:'DIAGNOSTIC_native_screen_fixture',actAuthority:'PLAYTEST',
      screenClass:context.value.screenClass,nativeScreens:[context.value.screenClass],screenIdentity:context.value.screenIdentity,
      semanticActions:context.actions,before:context.before,after,arranged:context.arranged,returned,
      ...(context.draft?{draft:context.draft}:{}),semanticTargets:true,handwrittenCoordinates:false,
      businessOperationCoverage:'native_ui_only_safe_branch',actualFaultInduced:false,externalServiceAcceptanceTested:false,returnedScreen:afterPage.screenClass});
  }
  await check('UI native confirmation: ten safe acknowledgement, cancel, privacy and permission pages use actual semantic actions',async()=>{
    await menu();const capabilities=await proof();
    if(target!=='26.3-neoforge'){
      assert(Array.isArray(capabilities.supportedCases));assert(Array.isArray(capabilities.nativeUnavailableCases));
      for(const unavailable of capabilities.nativeUnavailableCases){assert.equal(unavailable.disposition,'NATIVE_CLASS_ABSENT');
        assert(!capabilities.supportedCases.includes(unavailable.case));
        samples.push({...unavailable,case:'native_confirmation_unavailable',nativeCase:unavailable.case,target,operationCoverage:false});}
    }
    for(const [caseId,label,screen,keys,callback] of [
      ['popup','Popup (Fixture)','PopupScreen',['gui.done'],'popup_closed'],
      ['onboarding','Onboarding (Fixture)','AccessibilityOnboardingScreen',['gui.continue'],'onboarding_closed'],
      ['alert','Alert (Fixture)','AlertScreen',['gui.back'],'alert_closed'],
      ['backup','Backup Confirm (Fixture)','BackupConfirmScreen',['gui.cancel'],'backup_cancelled'],
      ['death_confirm','Death Confirm (Fixture)','DeathScreen$TitleConfirmScreen',['gui.no'],'death_cancelled'],
      ['waiting','Waiting (Fixture)','GenericWaitingScreen',['gui.done'],'waiting_closed'],
      ['privacy','Privacy Link (Fixture)','PrivacyConfirmLinkScreen',['gui.cancel'],'privacy_cancelled'],
      ['conduct','Code of Conduct (Fixture)','CodeOfConductScreen',['menu.disconnect'],'conduct_declined'],
      ['restrictions','Restrictions (Fixture)','RestrictionsScreen',['gui.done'],'none'],
      ['telemetry','Telemetry Page (Fixture)','TelemetryInfoScreen',['gui.done'],'none'],
    ]) {
      if(target!=='26.3-neoforge'&&!capabilities.supportedCases.includes(caseId)){
        assert(capabilities.nativeUnavailableCases.some(value=>value.case===caseId),'Unavailable native case must be explicit');continue;
      }
      const context=await open(caseId,label,screen);
      if(caseId==='waiting')await wait(value=>value.screenClass.endsWith('GenericWaitingScreen')&&nodes(value).some(node=>node.translationKey==='gui.done'
        &&node.visible&&node.active&&node.actions?.includes('click')),'native waiting page enables its own delayed close button');
      if(caseId==='backup') {
        const checkbox=nodes(context.value).find(node=>node.role==='checkbox'&&node.visible&&node.active);assert(checkbox);
        context.actions.push({node:checkbox,result:await ui({nodeId:checkbox.nodeId})});
        const changed=await wait(value=>nodes(value).some(node=>node.nodeId===checkbox.nodeId&&node.selected!==checkbox.selected),'native erase-cache draft checkbox');
        context.draft={before:checkbox,after:nodes(changed).find(node=>node.nodeId===checkbox.nodeId),applied:false};
      }
      await finish(context,keys,callback);
    }
  });
  if(target.startsWith('1.'))await check('UI native confirmation: legacy native link pages cancel without opening external navigation',async()=>{
    const link=await open('confirm_link','Confirm Link (Fixture)','ConfirmLinkScreen');await finish(link,['gui.cancel'],'confirm_link_cancelled');
    if(target==='1.21.1-neoforge'){
      const notice=await open('notice_link','Notice Link (Fixture)','NoticeWithLinkScreen');
      const value=await tree(),back=nodes(value).filter(node=>node.visible&&node.active&&node.actions?.includes('click')&&node.translationKey==='gui.back');
      assert.equal(back.length,1,'Native Notice Back button');await finish(notice,['gui.back'],'notice_link_closed');
    }
  });
  await check('UI native confirmation: native Error and OOM pages return safely without inducing errors or exiting the client',async()=>{
    for(const [caseId,label,screen,key] of [['error','Error (Fixture)','ErrorScreen','gui.cancel'],['oom','OOM Page (Fixture)','OutOfMemoryScreen','gui.toTitle']]) {
      const context=await open(caseId,label,screen),action=await button([key]),afterPage=await page('TitleScreen'),after=await clean();
      assert.equal((await api('GET','/v0/player?category=USER')).available,false);
      assert(after.inputDispatchSequence>context.before.inputDispatchSequence);
      samples.push({case:'native_confirmation_'+caseId,arrangeAuthority:'DIAGNOSTIC_native_screen_fixture',actAuthority:'PLAYTEST',
        screenClass:context.value.screenClass,nativeScreens:[context.value.screenClass],screenIdentity:context.value.screenIdentity,
        semanticActions:[action],before:context.before,after,arranged:context.arranged,returnedScreen:afterPage.screenClass,
        semanticTargets:true,handwrittenCoordinates:false,businessOperationCoverage:'native_ui_only_safe_branch',actualFaultInduced:false,quitClientSelected:false});
    }
  });
  await check('UI native confirmation: experimental draft toggle, native Details and cancellation leave private pack selection unchanged',async()=>{
    const context=await open('experiments','Experiments Draft (Fixture)','ExperimentsScreen');
    const toggle=nodes(context.value).find(node=>node.visible&&node.active&&node.class.endsWith('CycleButton')&&node.actions?.includes('click'));
    assert(toggle,'An actual builtin FEATURE pack switch must be exposed');context.actions.push({node:toggle,result:await ui({nodeId:toggle.nodeId})});
    const changed=await wait(value=>nodes(value).some(node=>node.nodeId===toggle.nodeId&&node.label!==toggle.label),'native experiment draft toggle label');
    context.draft={before:toggle,after:nodes(changed).find(node=>node.nodeId===toggle.nodeId),applied:false};
    await finish(context,['gui.cancel'],'none');
    const confirm=await open('experimental_confirm','Experimental Confirm (Fixture)','ConfirmExperimentalFeaturesScreen');
    const details=await button(['selectWorld.experimental.details']);confirm.actions.push(details);
    const detailsPage=await page('ConfirmExperimentalFeaturesScreen$DetailsScreen');
    assert(nodes(detailsPage).length>0);confirm.actions.push(await button(['gui.back']));await page('ConfirmExperimentalFeaturesScreen');
    await finish(confirm,['gui.back'],'experimental_cancelled');
    samples.push({case:'native_confirmation_experimental_details',screenClass:detailsPage.screenClass,nativeScreens:[detailsPage.screenClass],
      screenIdentity:detailsPage.screenIdentity,semanticActions:[details,...confirm.actions.slice(1)],
      arrangeAuthority:'DIAGNOSTIC_native_screen_fixture',actAuthority:'PLAYTEST',semanticTargets:true,handwrittenCoordinates:false,
      actualFaultInduced:false,worldGenerationWithExperimentsTested:false});
    await button(['gui.done']);
    if(target==='26.3-neoforge'){await page('UiNavigationFixtureScreen');await button(['gui.done']);}
    await page('AutomationProbeScreen');
    await button([],'Close Probe');await page('TitleScreen');await clean();
  });
}
