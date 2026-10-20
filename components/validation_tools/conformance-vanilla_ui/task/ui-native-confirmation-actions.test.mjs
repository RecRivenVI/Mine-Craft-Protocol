import assert from 'node:assert/strict';
import test from 'node:test';
import {nativeConfirmationActions,verifyNativeConfirmationProof} from './ui-native-confirmation-actions.mjs';

const expected={target:'26.3-neoforge',fixtureId:'current-native-fixture',sequence:4,caseId:'experiments',
  screenClass:'net.minecraft.client.gui.screens.worldselection.ExperimentsScreen',returned:true,callbackAction:'none'};
const fixture=()=>({probeOnly:true,arrangeAuthority:'DIAGNOSTIC_native_screen_fixture',target:expected.target,fixtureId:expected.fixtureId,
  sequence:4,case:'experiments',nativeClass:expected.screenClass,returnedToFixture:true,callbackAction:'none',
  actualFaultInduced:false,externalServiceUsed:false,worldFilesTouched:false,globalPackRepositoryTouched:false,serverListEntrySupplied:false,
  experimentOutputCalls:0,selectedPacksBefore:['vanilla'],selectedPacksAfter:['vanilla']});

test('accepts current native callback facts with unchanged cancelled pack selection',()=>{
  const actual=fixture();assert.equal(verifyNativeConfirmationProof(actual,expected),actual);
});

test('rejects stale, substituted, non-native, applied or unsafe fixture evidence',()=>{
  for(const change of [
    {probeOnly:false},{arrangeAuthority:'USER'},{target:'1.20.1-forge'},{fixtureId:'old-fixture'},{sequence:3},{case:'popup'},
    {nativeClass:'io.github.recrivenvi.minecraftprotocol.probe.validation.FakeExperimentsScreen'},
    {returnedToFixture:false},{callbackAction:'unexpected_experiments_apply'},{actualFaultInduced:true},
    {externalServiceUsed:true},{worldFilesTouched:true},{globalPackRepositoryTouched:true},{serverListEntrySupplied:true},
    {experimentOutputCalls:1},{selectedPacksAfter:['vanilla','trade_rebalance']},
  ])assert.throws(()=>verifyNativeConfirmationProof({...fixture(),...change},expected),JSON.stringify(change));
});

function controlledAdapter({target='26.3-neoforge',waitingDelay=6,waitingNeverActive=false,
  waitingDisablesAfterWait=false,forgedCallback,invalidUnavailable=false,overlapUnavailable=false,omitUnavailable=false}={}) {
  const forge=target==='1.20.1-forge',old=target.startsWith('1.'),reference=target==='26.3-neoforge';
  const variants=[
    ['popup','Popup (Fixture)',forge?'net.minecraft.client.gui.screens.PopupScreen':'net.minecraft.client.gui.components.PopupScreen','gui.done','popup_closed'],
    ['onboarding','Onboarding (Fixture)','net.minecraft.client.gui.screens.AccessibilityOnboardingScreen','gui.continue',forge?'none':'onboarding_closed'],
    ['alert','Alert (Fixture)','net.minecraft.client.gui.screens.AlertScreen','gui.back','alert_closed'],
    ['backup','Backup Confirm (Fixture)','net.minecraft.client.gui.screens.BackupConfirmScreen','gui.cancel',forge?'none':'backup_cancelled'],
    ['death_confirm','Death Confirm (Fixture)','net.minecraft.client.gui.screens.DeathScreen$TitleConfirmScreen','gui.no','death_cancelled'],
    ['waiting','Waiting (Fixture)','net.minecraft.client.gui.screens.GenericWaitingScreen','gui.done','waiting_closed'],
    ['privacy','Privacy Link (Fixture)','net.minecraft.client.gui.screens.PrivacyConfirmLinkScreen','gui.cancel','privacy_cancelled'],
    ['conduct','Code of Conduct (Fixture)','net.minecraft.client.gui.screens.multiplayer.CodeOfConductScreen','menu.disconnect','conduct_declined'],
    ['restrictions','Restrictions (Fixture)','net.minecraft.client.gui.screens.multiplayer.RestrictionsScreen','gui.done','none'],
    ['telemetry','Telemetry Page (Fixture)','net.minecraft.client.gui.screens.telemetry.TelemetryInfoScreen','gui.done','none'],
    ['error','Error (Fixture)','net.minecraft.client.gui.screens.ErrorScreen','gui.cancel','none'],
    ['oom','OOM Page (Fixture)','net.minecraft.client.gui.screens.OutOfMemoryScreen','gui.toTitle','none'],
    ['experiments','Experiments Draft (Fixture)','net.minecraft.client.gui.screens.worldselection.ExperimentsScreen','gui.cancel','none'],
    ['experimental_confirm','Experimental Confirm (Fixture)','net.minecraft.client.gui.screens.worldselection.ConfirmExperimentalFeaturesScreen','gui.back','experimental_cancelled'],
    ['confirm_link','Confirm Link (Fixture)','net.minecraft.client.gui.screens.ConfirmLinkScreen','gui.cancel','confirm_link_cancelled'],
    ['notice_link','Notice Link (Fixture)','net.minecraft.client.gui.screens.NoticeWithLinkScreen','gui.back','notice_link_closed'],
  ];
  const absent=old?['privacy','conduct','restrictions',...(forge?['notice_link']:[])]:target==='26.1.2-neoforge'?['privacy']:[];
  const supported=variants.filter(value=>!absent.includes(value[0])&&(old||!['confirm_link','notice_link'].includes(value[0])));
  let screen='net.minecraft.client.gui.screens.TitleScreen',sequence=0,inputSequence=0,current,selected=false;
  let fixtureIdentity=0,waitingReads=0,forceWaitingDisabled=false,closed=false,experimentSelected=false;
  const samples=[],actions=[],checks=[],waitingFrames=[],waitLabels=[],callbackInvocations=[];
  const fixtureScreen='io.github.recrivenvi.minecraftprotocol.probe.validation.UiNativeConfirmationFixtureScreen';
  const navigationScreen='io.github.recrivenvi.minecraftprotocol.probe.validation.UiNavigationFixtureScreen';
  const probeScreen='io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen';
  let probe;
  function freshProbe() {
    probe={...fixture(),target,fixtureId:'current-native-fixture-'+(++fixtureIdentity),case:'',sequence:0,nativeClass:'',
      returnedToFixture:false,processId:7,callbackAction:'none'};
    sequence=0;current=undefined;closed=false;
  }
  freshProbe();
  const capabilities=()=>{
    const unavailable=absent.filter(name=>!(omitUnavailable&&name==='privacy')).map(name=>({case:name,
      nativeClass:variants.find(value=>value[0]===name)[2],disposition:invalidUnavailable?'CLASS_PRESENT':'NATIVE_CLASS_ABSENT'}));
    return {supportedCases:supported.map(value=>value[0]),nativeUnavailableCases:[...unavailable,
      ...(overlapUnavailable?[{case:'popup',nativeClass:variants[0][2],disposition:'NATIVE_CLASS_ABSENT'}]:[])]};
  };
  const button=(id,label,translationKey,extra={})=>({nodeId:id,label,translationKey,class:'net.minecraft.client.gui.components.Button',role:'button',visible:true,active:true,actions:['click'],...extra});
  const waitingActive=()=>!waitingNeverActive&&!forceWaitingDisabled&&waitingReads>=waitingDelay;
  const tree=async()=>{
    let children=[];
    if(screen===probeScreen)children=[button(reference?'navigation':'more',reference?'UI Navigation Fixtures':'More Native Screens (Fixture)'),button('close-probe','Close Probe')];
    else if(screen===navigationScreen)children=[button('more','More Native Screens (Fixture)'),button('navigation-done','Done','gui.done')];
    else if(screen===fixtureScreen)children=[...supported.map(value=>button(value[0],value[1])),button('fixture-done','Done','gui.done')];
    else if(current&&!screen.endsWith('TitleScreen')){
      if(screen.endsWith('ConfirmExperimentalFeaturesScreen$DetailsScreen'))children=[button('details-back','Back','gui.back')];
      else {
        let active=true;
        if(current[0]==='waiting'){waitingReads++;active=waitingActive();waitingFrames.push({read:waitingReads,active});}
        children=[button('safe-close','Safe native return',current[3],{active,actions:active?['click']:[]})];
        if(current[0]==='backup')children.push({...button('draft-erase','Erase cache','selectWorld.backupEraseCache'),role:'checkbox',selected});
        if(current[0]==='experiments')children.push(button('experiment-toggle','Native feature: '+(experimentSelected?'ON':'OFF'),'',{class:'net.minecraft.client.gui.components.CycleButton'}));
        if(current[0]==='experimental_confirm')children.push(button('details','Details','selectWorld.experimental.details'));
        if(['confirm_link','notice_link','privacy','oom'].includes(current[0]))children.push(button('unsafe-action','Open external link or quit','chat.link.open'));
      }
    }
    return {screenClass:screen,screenIdentity:inputSequence+1,screenRevision:inputSequence+1,dataCategory:'USER',treeTruncated:false,children};
  };
  const ctx={repo:'.',target,samples,tree,nodes:value=>value.children,
    title:async()=>{screen='net.minecraft.client.gui.screens.TitleScreen';},
    openFixture:async()=>{screen=probeScreen;},
    readFixtureProof:async()=>structuredClone({...probe,...capabilities(),
      ...(forgedCallback&&closed&&current?.[0]===forgedCallback?{callbackAction:'synthetic_callback'}:{})}),
    api:async(method,path)=>{
      assert.equal(method,'GET','Only actual semantic UI actions may mutate the adapter');
      if(path==='/v0/player?category=USER')return {available:false};
      assert.equal(path,'/v0/input/state');return {pressedKeyCount:0,pressedButtonCount:0,inputCleanupPending:false,inputCleanupFailed:false,inputDispatchSequence:inputSequence};
    },
    wait:async(predicate,label)=>{
      waitLabels.push(label);
      for(let index=0;index<20;index++){
        const value=await tree();
        if(predicate(value)){
          if(waitingDisablesAfterWait&&label==='native waiting page enables its own delayed close button')forceWaitingDisabled=true;
          return value;
        }
      }
      throw new Error('Native wait timed out: '+label);
    },
    ui:async(selector)=>{
      assert.deepEqual(Object.keys(selector),['nodeId']);
      const value=await tree(),found=value.children.filter(node=>node.nodeId===selector.nodeId);assert.equal(found.length,1,'Current native adapter target');
      const node=found[0];assert.equal(node.active,true,'Never click a native disabled button');assert(node.actions.includes('click'));
      actions.push({nodeId:node.nodeId,caseId:current?.[0],active:node.active,waitingReads});inputSequence++;
      if(node.nodeId==='navigation')screen=navigationScreen;
      else if(node.nodeId==='more'){freshProbe();screen=fixtureScreen;}
      else if(node.nodeId==='close-probe')screen='net.minecraft.client.gui.screens.TitleScreen';
      else if(node.nodeId==='fixture-done')screen=reference?navigationScreen:probeScreen;
      else if(node.nodeId==='navigation-done')screen=probeScreen;
      else if(node.nodeId==='draft-erase')selected=!selected;
      else if(node.nodeId==='experiment-toggle')experimentSelected=!experimentSelected;
      else if(node.nodeId==='details')screen=current[2]+'$DetailsScreen';
      else if(node.nodeId==='details-back')screen=current[2];
      else if(node.nodeId==='safe-close') {
        closed=true;probe.callbackAction=current[4];
        if(current[4]!=='none')callbackInvocations.push({caseId:current[0],callbackAction:current[4]});
        const titleReturn=['error','oom'].includes(current[0])||forge&&current[0]==='onboarding';
        probe.returnedToFixture=!titleReturn;screen=titleReturn?'net.minecraft.client.gui.screens.TitleScreen':fixtureScreen;
      }else{
        assert.notEqual(node.nodeId,'unsafe-action','The real helper must not navigate externally, accept a link or quit');
        current=supported.find(value=>value[0]===node.nodeId);assert(current,'An absent native class must never be constructed');
        selected=false;waitingReads=0;forceWaitingDisabled=false;closed=false;experimentSelected=false;sequence++;
        screen=current[2];probe={...probe,sequence,case:current[0],nativeClass:screen,returnedToFixture:false,callbackAction:'none'};
      }
      return {targetingSource:'interaction_tree',directMutationUsed:false};
    },
    check:async(name,run)=>{checks.push(name);await run();},
  };
  return {ctx,supported,absent,actions,checks,samples,waitingFrames,waitLabels,callbackInvocations,
    state:()=>({screen,inputSequence,waitingReads})};
}

test('the real helper consumes five native case directories, true callbacks and explicit NativeClassAbsent without phantom pages',async()=>{
  for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']){
    const run=controlledAdapter({target});await nativeConfirmationActions(run.ctx);
    const absent=run.samples.filter(value=>value.case==='native_confirmation_unavailable');
    assert.equal(absent.length,run.absent.length,target);
    assert.deepEqual(absent.map(value=>value.nativeCase).sort(),[...run.absent].sort());
    assert(absent.every(value=>value.disposition==='NATIVE_CLASS_ABSENT'&&value.operationCoverage===false));
    const operated=run.samples.filter(value=>value.case!=='native_confirmation_unavailable');
    assert.equal(operated.length,run.supported.length+1,'Actual cases plus the actual Details page, not absent cases');
    for(const [caseId,,nativeClass]of run.supported){
      const sample=operated.find(value=>value.case==='native_confirmation_'+caseId);assert(sample,caseId+' '+target);
      assert.equal(sample.screenClass,nativeClass);assert.equal(sample.semanticTargets,true);
      assert.equal(sample.handwrittenCoordinates,false);assert.equal(sample.businessOperationCoverage,'native_ui_only_safe_branch');
    }
    assert(!run.actions.some(value=>value.nodeId==='unsafe-action'));
    assert(!run.actions.some(value=>run.absent.includes(value.nodeId)));
    assert.equal(run.actions.filter(value=>value.nodeId==='draft-erase').length,1);
    assert.equal(run.checks.length,target.startsWith('1.')?4:3);
    assert(run.state().screen.endsWith('TitleScreen'));
    assert.equal(run.actions.some(value=>value.nodeId==='navigation'),target==='26.3-neoforge','Only reference has the Nav intermediary');
    if(target==='1.20.1-forge'){
      const onboarding=operated.find(value=>value.case==='native_confirmation_onboarding');
      assert(onboarding.returnedScreen.endsWith('TitleScreen'));assert.equal(onboarding.returned.returnedToFixture,false);
      assert.equal(onboarding.returned.callbackAction,'none');
      const backup=operated.find(value=>value.case==='native_confirmation_backup');assert.equal(backup.returned.callbackAction,'none');
      assert.equal(backup.returned.returnedToFixture,true);
      assert(!run.callbackInvocations.some(value=>['onboarding','backup'].includes(value.caseId)),'No invented callback for native parent/title returns');
    }
    if(target.startsWith('1.')){
      assert(operated.some(value=>value.case==='native_confirmation_confirm_link'));
      assert.equal(operated.some(value=>value.case==='native_confirmation_notice_link'),target==='1.21.1-neoforge');
    }
  }
});

test('reference waiting completion really polls a native delayed active state and never clicks the disabled frame',async()=>{
  const run=controlledAdapter({waitingDelay:8});await nativeConfirmationActions(run.ctx);
  const close=run.actions.filter(value=>value.nodeId==='safe-close'&&value.caseId==='waiting');
  assert.equal(close.length,1);assert.equal(close[0].active,true);assert(close[0].waitingReads>=8);
  assert(run.waitingFrames.some(value=>value.active===false));assert(run.waitingFrames.some(value=>value.active===true));
  assert(run.waitLabels.includes('native waiting page enables its own delayed close button'));
  const sample=run.samples.find(value=>value.case==='native_confirmation_waiting');
  assert.equal(sample.arranged.returnedToFixture,false);assert.equal(sample.returned.callbackAction,'waiting_closed');
});

test('a permanently disabled or freshly re-disabled waiting button is not clicked and cannot produce a callback sample',async()=>{
  for(const options of [{waitingNeverActive:true},{waitingDisablesAfterWait:true}]){
    const run=controlledAdapter(options);await assert.rejects(nativeConfirmationActions(run.ctx),
      options.waitingNeverActive?/Native wait timed out: native waiting page enables/:/One safe native confirmation target/);
    assert(!run.actions.some(value=>value.nodeId==='safe-close'&&value.caseId==='waiting'));
    assert(!run.callbackInvocations.some(value=>value.caseId==='waiting'));
    assert(!run.samples.some(value=>value.case==='native_confirmation_waiting'));
    assert(run.waitingFrames.some(value=>value.active===false));
  }
});

test('Forge title and parent return paths reject forged callbacks instead of accepting synthetic lifecycle evidence',async()=>{
  for(const forgedCallback of ['onboarding','backup']){
    const run=controlledAdapter({target:'1.20.1-forge',forgedCallback});
    await assert.rejects(nativeConfirmationActions(run.ctx),assert.AssertionError);
    assert(!run.samples.some(value=>value.case==='native_confirmation_'+forgedCallback));
  }
});

test('invalid or overlapping absence evidence fails before opening a native case and missing absence cannot silently skip a page',async()=>{
  for(const option of ['invalidUnavailable','overlapUnavailable','omitUnavailable']){
    const run=controlledAdapter({target:'1.21.1-neoforge',[option]:true});
    await assert.rejects(nativeConfirmationActions(run.ctx),assert.AssertionError);
    assert(!run.actions.some(value=>value.nodeId==='privacy'));
    if(option!=='omitUnavailable')assert(!run.actions.some(value=>value.nodeId==='popup'));
  }
});
