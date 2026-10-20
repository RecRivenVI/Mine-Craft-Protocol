import assert from 'node:assert/strict';

export function nativeNavigationFeatures(target) {
  assert(['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric'].includes(target));
  return {font:target!=='1.20.1-forge',worldSettings:!target.startsWith('1.'),dialogs:!target.startsWith('1.')};
}

// Only the outer runner's disposable world and actual native screen graph are used.
export async function uiNavigationCompletionActions({api,target,command,key,tree,wait,nodes,ui,click,title,enterWorld,openFixture,check,samples}) {
  const features=nativeNavigationFeatures(target);
  const sleep=ms=>new Promise(done=>setTimeout(done,ms));
  let lastPipeline=-Infinity;
  async function clean() {
    const state=await api('GET','/v0/input/state');
    assert.equal(state.pressedKeyCount,0);assert.equal(state.pressedButtonCount,0);
    assert.equal(state.inputCleanupPending,false);assert.equal(state.inputCleanupFailed,false);return state;
  }
  async function page(name) {
    const value=await wait(value=>value.screenClass.endsWith(name),'native '+name);
    assert.equal(value.dataCategory,'USER');assert.equal(value.treeTruncated,false);
    return value;
  }
  async function button(keys,label) {
    const value=await tree(),candidates=nodes(value).filter(node=>node.visible!==false&&node.active!==false
      &&(keys.includes(node.translationKey)||node.label===label));
    assert.equal(candidates.length,1,'One visible native target: '+(label??keys.join('|')));
    return ui({nodeId:candidates[0].nodeId});
  }
  async function pipeline(steps,cleanupOnComplete=true) {
    const delay=550-(performance.now()-lastPipeline);if(delay>0)await sleep(delay);
    lastPipeline=performance.now();
    const operation=await api('POST','/v0/pipelines',{steps,cleanupOnComplete,timeoutMs:10000});
    const result=await api('POST','/v0/operations/'+operation.operationId+'/wait',{timeoutMs:10000});
    assert.equal(result.state,'completed',JSON.stringify(result));return result;
  }
  function record(caseName,visited,extra={}) {
    samples.push({case:caseName,nativeScreens:visited.map(value=>value.screenClass),screens:visited.map(value=>({screenClass:value.screenClass,
      screenIdentity:value.screenIdentity,screenRevision:value.screenRevision,interactionNodeCount:value.interactionNodeCount})),
      semanticTargets:true,handwrittenCoordinates:false,screenConstructionIsNotFullOperationCoverage:true,...extra});
  }
  function unavailable(group,nativeClasses,extra={}) {
    samples.push({case:'navigation_native_unavailable',target,group,nativeClasses,disposition:'NATIVE_CLASS_ABSENT',
      operationCoverage:false,semanticActionsPerformed:false,...extra});
  }
  await check('UI navigation: available font, online and telemetry pages expose actual native controls without changing settings',async()=>{
    const visited=[];
    await title();await click('menu.options','Options...');
    await click('options.language','Language...');const language=await page('LanguageSelectScreen');
    if(features.font) {
      await click('options.font','Font Settings...');visited.push(await page('FontOptionsScreen'));
      assert(nodes(visited.at(-1)).some(node=>node.actions?.includes('click')));
      await button(['gui.done'],'Done');await page('LanguageSelectScreen');
    }else {
      visited.push(language);unavailable('font_settings',['net.minecraft.client.gui.screens.options.FontOptionsScreen']);
    }
    await button(['gui.done'],'Done');await page('OptionsScreen');
    await click('options.online','Online...');visited.push(await page('OnlineOptionsScreen'));
    assert(nodes(visited.at(-1)).some(node=>node.actions?.includes('click')));
    await button(['gui.done'],'Done');await page('OptionsScreen');
    const telemetryEntry=nodes(await tree()).find(node=>node.translationKey==='options.telemetry');assert(telemetryEntry);
    let telemetryNativeEntryDisabled=false;
    if(telemetryEntry.active===false){
      const before=await clean(),beforeTree=await tree();
      await assert.rejects(ui({nodeId:telemetryEntry.nodeId}),error=>error.code==='UI_NODE_NOT_ACTIONABLE');
      const after=await clean(),afterTree=await tree();
      // A rejected active pipeline still performs release_all; that cleanup owns one sequence
      // number even when it releases zero keys/buttons. It is not a native click or move.
      assert.equal(after.inputDispatchSequence,before.inputDispatchSequence+1);
      for(const field of ['x','y'])assert.equal(after.agentPointer[field],before.agentPointer[field]);
      for(const field of ['screenIdentity','screenRevision'])assert.equal(afterTree[field],beforeTree[field]);
      telemetryNativeEntryDisabled=true;
    }else{
      await ui({nodeId:telemetryEntry.nodeId});visited.push(await page('TelemetryInfoScreen'));
      const telemetryControls=nodes(visited.at(-1)).filter(node=>node.visible!==false&&(target==='1.20.1-forge'
        ? node.role==='button'&&node.class==='net.minecraft.client.gui.components.CycleButton' : node.role==='checkbox'));
      assert.equal(telemetryControls.length,1,'One actual native telemetry opt-in control');
      await button(['gui.done'],'Done');
    }
    // No account policy, opt-in, external link or data-directory action is bypassed.
    await title();await clean();
    record('navigation_settings_completion',visited,{settingsChanged:false,telemetryOptInChanged:false,externalNavigationUsed:false,telemetryNativeEntryDisabled,
      fontDisposition:features.font?'actual_native_screen_visited':'NATIVE_CLASS_ABSENT',fontOperationCoverage:features.font,
      telemetryCoverage:telemetryNativeEntryDisabled?'native_entry_refusal_only_local_screen_uses_explicit_probe_fixture':'actual_native_entry'});
  });
  await check('UI navigation: native credits, win screen and cancel-only external link confirmation',async()=>{
    await title();await click('menu.options','Options...');await click('options.credits_and_attribution','Credits & Attribution...');
    const credits=await page('CreditsAndAttributionScreen');
    await click('credits_and_attribution.button.credits','Credits...');const win=await page('WinScreen');
    await key(256);await page('CreditsAndAttributionScreen');await button(['gui.done'],'Done');await title();await clean();
    record('navigation_credits_completion',[credits,win],{nativeWinScreenOpened:true,externalNavigationUsed:false});
  });
  await check('UI navigation: single-biome editor and experiments cancel without creating or replacing a world',async()=>{
    await title();await click('menu.singleplayer','Singleplayer');
    const before=await page('SelectWorldScreen'),worlds=nodes(before).filter(node=>node.worldId).map(node=>node.worldId).sort();
    await click('selectWorld.create','Create New World');await page('CreateWorldScreen');
    await ui({role:'tab',label:'World'});
    for(let index=0;index<12;index++) {
      const type=nodes(await tree()).find(node=>node.label?.startsWith('World Type:'));
      assert(type,'Actual native world type control');if(/Single Biome/.test(type.label))break;
      assert(index<11,'Single Biome preset was not found in the bounded native cycle');await ui({nodeId:type.nodeId});
    }
    await click('selectWorld.customizeType','Customize...');const buffet=await page('CreateBuffetWorldScreen');
    const choices=nodes(buffet).filter(node=>node.role==='list_item'&&node.visible&&node.actions?.includes('click'));
    assert(choices.length>0);await ui({nodeId:choices[0].nodeId});await button(['gui.done'],'Done');await page('CreateWorldScreen');
    // Stable Minecraft can omit the native experiments entry. Absence is not a screen-operation PASS.
    await ui({role:'tab',label:'Game'});
    const entry=nodes(await tree()).find(node=>node.translationKey==='selectWorld.experiments'&&node.visible&&node.active);
    let experiments;
    if(entry) {await ui({nodeId:entry.nodeId});experiments=await page('ExperimentsScreen');await button(['gui.cancel'],'Cancel');await page('CreateWorldScreen');}
    await button(['gui.cancel'],'Cancel');
    const after=await page('SelectWorldScreen');assert.deepEqual(nodes(after).filter(node=>node.worldId).map(node=>node.worldId).sort(),worlds);
    await title();await clean();record('navigation_world_creation_completion',[buffet,...(experiments?[experiments]:[])],{worldCreated:false,worldListUnchanged:true,experimentsChanged:false,
      experimentsDisposition:experiments?'actual_native_screen_visited':'native_entry_not_exposed_in_stable_build_not_validated'});
  });
  if(features.worldSettings) await check('UI navigation: world options, in-world game rules and native debug screen return through real input',async()=>{
    await enterWorld();await key(256);await page('PauseScreen');
    if(target==='26.1.2-neoforge'){await click('menu.options','Options...');await page('OptionsScreen');}
    await click('options.worldOptions.button','World Options...');const worldOptions=await page('WorldOptionsScreen');
    await click('editGamerule.inGame.button','Game Rules...');const rules=await page('InWorldGameRulesScreen');
    assert(nodes(rules).some(node=>node.actions?.includes('click')));
    await button(['gui.cancel'],'Cancel');await page('WorldOptionsScreen');
    if(target==='26.1.2-neoforge'){await button(['gui.done'],'Done');await page('OptionsScreen');await button(['gui.done'],'Done');}
    else await button(['gui.cancel'],'Cancel');
    await page('PauseScreen');await key(256);
    await wait(value=>!value.screenClass,'ready native gameplay before debug menu');
    // The restored baseline uses Vanilla's F3+F6 bindings; no OS input is produced.
    await pipeline([{type:'key',key:292,action:1},{type:'key.tap',key:295,holdMs:40}],false);
    const debug=await page('DebugOptionsScreen');await pipeline([{type:'key',key:292,action:0}]);
    const search=nodes(debug).find(node=>node.role==='text_field'&&node.visible&&node.actions?.includes('type_text'));
    assert(search);await ui({nodeId:search.nodeId},'type_text',{text:'fps'});
    await wait(value=>nodes(value).some(node=>node.nodeId===search.nodeId&&node.value==='fps'),'native debug search draft');
    await key(256);await wait(value=>!value.screenClass,'debug menu closed');await clean();
    record('navigation_world_settings_completion',[worldOptions,rules,debug],{gamerulesChanged:false,debugSettingsChanged:false,debugSearchDraftOnly:true});
  });
  else unavailable('world_options_rules_debug',[
    'net.minecraft.client.gui.screens.options.WorldOptionsScreen','net.minecraft.client.gui.screens.options.InWorldGameRulesScreen',
    'net.minecraft.client.gui.screens.debug.DebugOptionsScreen']);
  await check('UI navigation: actual private game-mode choices hover and commit only on native modifier release',async()=>{
    if(!features.worldSettings)await enterWorld();
    await wait(value=>!value.screenClass,'gameplay before native game-mode menu');
    await clean();
    const initial=await api('GET','/v0/player?category=USER');assert(['creative','survival','adventure','spectator'].includes(initial.gameMode));
    const arrange=[];
    arrange.push(await command('fill -2 79 -2 2 79 2 minecraft:stone'));
    arrange.push(await command('tp @s 0.5 80 0.5 0 0'));
    const footingEnd=performance.now()+5000;let footing,readyTick;
    do {
      const [block,player]=await Promise.all([api('GET','/v0/world/block?x=0&y=79&z=0&category=USER'),api('GET','/v0/player?category=USER')]);
      if(block.available===true&&block.block==='minecraft:stone'&&player.available===true
          &&Math.abs(player.x-0.5)<0.05&&Math.abs(player.y-80)<0.05&&Math.abs(player.z-0.5)<0.05
          &&Number.isSafeInteger(player.clientTick)) {
        readyTick??=player.clientTick;
        if(player.clientTick>=readyTick+2) {footing={block,player};break;}
      } else readyTick=undefined;
      await sleep(75);
    }while(performance.now()<footingEnd);
    assert(footing,'The owned platform and client player did not settle before a native survival transition');
    const alternate=initial.gameMode==='creative'?'survival':'creative',visited=[],operations=[];
    let cancelledSelection;
    async function select(mode,cancel=false) {
      await pipeline([{type:'key',key:292,action:1},{type:'key.tap',key:293,holdMs:40}],false);
      const opened=await page('GameModeSwitcherScreen');visited.push(opened);
      const choices=nodes(opened).filter(node=>node.controlKey?.startsWith('game_mode.choice.'));
      assert.equal(choices.length,4);
      for(const choice of choices) {assert.deepEqual(choice.actions,['hover']);assert.equal(choice.effect,'hover_then_release_debug_modifier');}
      const choice=choices.find(node=>node.translationKey==='gameMode.'+mode||node.label.toLowerCase()===mode);assert(choice,'Native '+mode+' choice');
      const before=await api('GET','/v0/player?category=USER'),held=await api('GET','/v0/input/state');assert.equal(held.pressedKeyCount,1);
      await ui({nodeId:choice.nodeId},'hover');
      const hovered=await wait(value=>nodes(value).some(node=>node.nodeId===choice.nodeId&&node.selected),'native hover selects '+mode);
      const uncommitted=await api('GET','/v0/player?category=USER');assert.equal(uncommitted.gameMode,before.gameMode,'Hover itself must not change mode');
      if(cancel) {
        const stopped=await api('POST','/v0/input/release-all',{});
        await wait(value=>!value.screenClass,'cancelled Agent game-mode menu closes without submitting');
        const deadline=performance.now()+5000;let later;
        do {later=await api('GET','/v0/player?category=USER');assert.equal(later.gameMode,before.gameMode,'Cancelled native menu submitted a late selection');
          if(later.clientTick>=uncommitted.clientTick+4)break;await sleep(75);
        }while(performance.now()<deadline);
        assert(later.clientTick>=uncommitted.clientTick+4,'Native cleanup must be observed across subsequent client ticks');
        cancelledSelection={choice,before,uncommitted,stopped,later,after:await clean(),cancelledBy:'explicit_input_release_all',privateSubmitCalled:false};return;
      }
      const released=await pipeline([{type:'key',key:292,action:0}]);await wait(value=>!value.screenClass,'native modifier release closes switcher');
      const end=performance.now()+5000;let player;
      do {player=await api('GET','/v0/player?category=USER');if(player.gameMode===mode)break;await sleep(75);}while(performance.now()<end);
      assert.equal(player.gameMode,mode,'Native server-validated game mode was not observed');
      const after=await clean();operations.push({mode,choice,hoveredScreenRevision:hovered.screenRevision,before,uncommitted,released,player,after});
    }
    try {await select(alternate);await select(initial.gameMode);await select(alternate,true);}
    finally {
      const input=await api('GET','/v0/input/state');if(input.pressedKeyCount||input.pressedButtonCount)await api('POST','/v0/input/release-all',{});
      if((await api('GET','/v0/player?category=USER')).gameMode!==initial.gameMode)await command('gamemode '+initial.gameMode);
    }
    await clean();record('navigation_native_game_mode_completion',visited,{operations,initialMode:initial.gameMode,restored:true,activation:'native_debug_modifier_release',directModeSetterUsed:false,
      arrange,arrangeAuthority:'diagnostic_player_commands_owned_copy_only',safeFooting:footing,cancelledSelection});
  });
  if(features.dialogs) {
  await check('UI navigation: native Dialog text styles drive actual semantic links without calling callbacks directly',async()=>{
    await openFixture();await button([],'UI Navigation Fixtures');await page('UiNavigationFixtureScreen');
    await button([],'Notice Dialog (Fixture)');const notice=await page('SimpleDialogScreen');
    const links=nodes(notice).filter(node=>node.controlKey?.startsWith('text.link.')&&node.visible);
    assert(links.length>=3);const show=links.find(node=>node.label==='semantic dialog link');assert(show);assert.equal(show.clickEventType,'show_dialog');
    assert(show.actions.includes('click'));await ui({nodeId:show.nodeId},'hover');
    const before=await api('GET','/v0/input/state');await ui({nodeId:show.nodeId});
    const destination=await wait(value=>value.screenClass.endsWith('SimpleDialogScreen')&&value.title==='Semantic Link Destination (Fixture)','actual native ShowDialog link destination');
    assert.notEqual(destination.screenIdentity,notice.screenIdentity);await button(['gui.done'],'Done');await page('UiNavigationFixtureScreen');
    const after=await clean();assert(after.inputDispatchSequence>before.inputDispatchSequence);
    record('navigation_native_dialog_style_hit',[notice,destination],{nativeStyleTarget:show,actualScreenChange:true,arrange:'explicit_probe_constructed_native_dialog_real_connection_access',externalNavigationUsed:false});
  });
  await check('UI navigation: Dialog command and custom callbacks retain the production send-before-effect guards',async()=>{
    const rejected=[];
    for(const [label,codes] of [['blocked command link',['PLAYER_COMMAND_OUT_OF_SCOPE','PLAYER_COMMAND_NOT_AVAILABLE']],['blocked custom link',['PLAYER_COMMAND_UNCLASSIFIED']]]) {
      await button([],'Notice Dialog (Fixture)');const value=await page('SimpleDialogScreen');
      const link=nodes(value).find(node=>node.controlKey?.startsWith('text.link.')&&node.label===label);assert(link);
      let failure;await assert.rejects(ui({nodeId:link.nodeId}),error=>{failure={code:error.code,httpStatus:error.httpStatus,nativeError:error.nativeError};return codes.includes(error.code);});
      rejected.push({label,target:link,failure});
      const current=await tree();if(current.screenClass.endsWith('SimpleDialogScreen'))await button(['gui.done'],'Done');
      await page('UiNavigationFixtureScreen');
    }
    await clean();samples.push({case:'navigation_dialog_callback_guards',rejected,directCallbackUsed:false,callbackOutcome:'production_guard_rejected',serverSideEffectIndependentlyMeasured:false});
  });
  await check('UI navigation: six native Dialog variants and cancel-only link pages remain semantically navigable',async()=>{
    const visited=[];
    for(const [label,screen,exitKeys] of [['Multi Dialog (Fixture)','MultiButtonDialogScreen',['gui.done']],['Dialog List (Fixture)','DialogListDialogScreen',['gui.done']],
      ['Server Links (Fixture)','ServerLinksDialogScreen',['gui.done']],['Dialog Warning (Fixture)','DialogScreen$WarningScreen',['gui.back']],
      ['Dialog Waiting (Fixture)','WaitingForResponseScreen',[]],['Confirm Link (Fixture)','ConfirmLinkScreen',['gui.cancel']],['Notice Link (Fixture)','NoticeWithLinkScreen',['gui.back']]]) {
      await button([],label);let value=await page(screen);visited.push(value);
      if(screen==='WaitingForResponseScreen') {
        value=await wait(value=>value.screenClass.endsWith(screen)&&nodes(value).some(node=>node.visible&&node.active&&node.actions?.includes('click')),'native waiting page exposes its own delayed close button');
        const close=nodes(value).filter(node=>node.visible&&node.active&&node.actions?.includes('click'));assert.equal(close.length,1);await ui({nodeId:close[0].nodeId});
      } else if(screen==='NoticeWithLinkScreen')await key(256);
      else await button(exitKeys,screen==='ConfirmLinkScreen'?'Cancel':screen==='DialogScreen$WarningScreen'?'Back':'Done');
      await page('UiNavigationFixtureScreen');
    }
    await button(['gui.done'],'Done');await page('AutomationProbeScreen');await button([],'Close Probe');await wait(value=>!value.screenClass,'probe navigation closed');await clean();
    record('navigation_dialog_variants',visited,{arrange:'explicit_probe_native_screens',externalNavigationUsed:false,realExternalServiceAvailabilityTested:false,worldMutationUsed:false});
  });
  }else unavailable('native_dialogs',[
    'net.minecraft.client.gui.screens.dialog.SimpleDialogScreen','net.minecraft.client.gui.screens.dialog.MultiButtonDialogScreen',
    'net.minecraft.client.gui.screens.dialog.DialogListDialogScreen','net.minecraft.client.gui.screens.dialog.ServerLinksDialogScreen',
    'net.minecraft.client.gui.screens.dialog.DialogScreen$WarningScreen','net.minecraft.client.gui.screens.dialog.WaitingForResponseScreen'],
    {unavailableOperations:['dialog_style_link','dialog_callback_guards','dialog_variants'],
      existingLinkPagesValidatedHere:false,cancelOnlyLinksCoverageOwner:'separate_native_confirmation_fixture'});
}
