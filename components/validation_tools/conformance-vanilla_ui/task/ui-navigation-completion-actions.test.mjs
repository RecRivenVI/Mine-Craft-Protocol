import assert from 'node:assert/strict';
import test from 'node:test';
import {nativeNavigationFeatures,uiNavigationCompletionActions} from './ui-navigation-completion-actions.mjs';

test('native disabled telemetry refuses input and accounts only for mandatory failure cleanup',async()=>{
  for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']) {
  const screens=[],samples=[];let current='TitleScreen',disabledRequests=0;
  const checks=[],clicks=[];
  const button=(key,active=true)=>({nodeId:key,translationKey:key,role:'button',visible:true,active,actions:['click']});
  const tree=async()=>({screenClass:'net.minecraft.client.gui.screens.'+current,dataCategory:'USER',treeTruncated:false,
    children:current==='OptionsScreen'?[button('options.telemetry',false),button('gui.done')]:[button('gui.done')]});
  const choose=key=>{clicks.push(key);if(key==='gui.done'){current=screens.pop();return;}
    if(key==='options.font')assert.notEqual(target,'1.20.1-forge','Forge has no native Font screen');
    const destination={'menu.options':'OptionsScreen','options.language':'LanguageSelectScreen','options.font':'FontOptionsScreen','options.online':'OnlineOptionsScreen'}[key];
    assert(destination,key);screens.push(current);current=destination;};
  await uiNavigationCompletionActions({target,samples,tree,nodes:value=>value.children,
    title:async()=>{current='TitleScreen';screens.length=0;},click:async key=>choose(key),
    ui:async selector=>{if(selector.nodeId==='options.telemetry'){disabledRequests++;throw Object.assign(new Error('native disabled'),{code:'UI_NODE_NOT_ACTIONABLE'});}choose(selector.nodeId);},
    api:async(method,path)=>{assert.equal(method,'GET');assert.equal(path,'/v0/input/state');return {pressedKeyCount:0,pressedButtonCount:0,inputCleanupPending:false,inputCleanupFailed:false,inputDispatchSequence:9+disabledRequests,agentPointer:{x:120,y:72}};},
    wait:async(predicate,label)=>{const value=await tree();assert(predicate(value),label);return value;},
    check:async(name,run)=>{checks.push(name);if(name.includes('font, online and telemetry'))await run();}});
  assert.equal(disabledRequests,1);const sample=samples.find(value=>value.case==='navigation_settings_completion');assert(sample);
  assert.equal(sample.telemetryNativeEntryDisabled,true);assert(!sample.nativeScreens.some(name=>name.endsWith('TelemetryInfoScreen')));
  assert.equal(sample.telemetryOptInChanged,false);assert.equal(sample.settingsChanged,false);assert.equal(current,'TitleScreen');
  assert.equal(sample.fontOperationCoverage,target!=='1.20.1-forge');assert.equal(clicks.includes('options.font'),target!=='1.20.1-forge');
  assert.equal(checks.length,target.startsWith('1.')?4:8,'Native-absent families never run as passing checks');
  const absent=samples.filter(value=>value.case==='navigation_native_unavailable');
  assert(absent.every(value=>value.operationCoverage===false&&value.semanticActionsPerformed===false&&value.disposition==='NATIVE_CLASS_ABSENT'));
  if(target.startsWith('1.')) {
    assert(absent.some(value=>value.group==='world_options_rules_debug'));
    const dialogs=absent.find(value=>value.group==='native_dialogs');assert(dialogs);
    assert.equal(dialogs.existingLinkPagesValidatedHere,false);
    assert(!dialogs.nativeClasses.some(value=>/NoticeWithLinkScreen|ConfirmLinkScreen/.test(value)),'Present link pages are not incorrectly marked absent');
  }else assert.equal(absent.length,0);
  }
});

test('enabled telemetry entry reads its real legacy CycleButton or newer Checkbox without opting in',async()=>{
  async function run(target,controls) {
    let current='TitleScreen';const stack=[],samples=[],calls=[];
    const button=key=>({nodeId:key,translationKey:key,role:'button',visible:true,active:true,actions:['click']});
    const tree=async()=>({screenClass:'native.'+current,dataCategory:'USER',treeTruncated:false,
      children:current==='OptionsScreen'?[button('options.telemetry'),button('gui.done')]
        :[button('gui.done'),...(current==='TelemetryInfoScreen'?controls:[])]});
    const choose=key=>{calls.push(key);if(key==='gui.done'){current=stack.pop();return;}
      const next={'menu.options':'OptionsScreen','options.language':'LanguageSelectScreen','options.font':'FontOptionsScreen',
        'options.online':'OnlineOptionsScreen','options.telemetry':'TelemetryInfoScreen'}[key];
      assert(next,'No opt-in or external action is allowed: '+key);stack.push(current);current=next;};
    await uiNavigationCompletionActions({target,samples,tree,nodes:value=>value.children,
      title:async()=>{current='TitleScreen';stack.length=0;},click:async key=>choose(key),ui:async selector=>choose(selector.nodeId),
      api:async(method,path)=>{assert.equal(method,'GET');assert.equal(path,'/v0/input/state');return{pressedKeyCount:0,pressedButtonCount:0,inputCleanupPending:false,inputCleanupFailed:false};},
      wait:async(predicate,label)=>{const value=await tree();assert(predicate(value),label);return value;},
      check:async(name,action)=>{if(name.includes('font, online and telemetry'))await action();}});
    assert.equal(current,'TitleScreen');assert.equal(samples.find(value=>value.case==='navigation_settings_completion').telemetryOptInChanged,false);
    assert(!calls.includes('opt-in'));
  }
  for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']) {
    const control={nodeId:'opt-in',role:target==='1.20.1-forge'?'button':'checkbox',
      class:'net.minecraft.client.gui.components.'+(target==='1.20.1-forge'?'CycleButton':'Checkbox'),visible:true,active:false,actions:[]};
    await run(target,[control]);
    await assert.rejects(run(target,[]),/One actual native telemetry opt-in control/);
    await assert.rejects(run(target,[control,control]),/One actual native telemetry opt-in control/);
    if(target==='1.20.1-forge')await assert.rejects(run(target,[{...control,class:'unrelated.CycleButton'}]),/One actual native telemetry opt-in control/);
  }
});

function controlledRuntime({target='26.3-neoforge',legacyField=false,confirmFooting=true,commitDuringCleanup=false}={}) {
  let gameMode='creative',selected,screen='',held=0,sequence=0,tick=0,footingReads=0;
  const samples=[],calls=[],operations=new Map();
  const nodes=()=>['creative','survival','adventure','spectator'].map((mode,index)=>({nodeId:'native-mode-'+index,
    controlKey:'game_mode.choice.'+index,role:'game_mode',label:mode,translationKey:'gameMode.'+mode,
    visible:true,active:true,actions:['hover'],effect:'hover_then_release_debug_modifier',selected:selected===mode}));
  const tree=async()=>({screenClass:screen,dataCategory:'USER',treeTruncated:false,screenIdentity:screen?1:0,screenRevision:1,children:screen?nodes():[]});
  const input=()=>({pressedKeyCount:held,pressedButtonCount:0,inputCleanupPending:false,inputCleanupFailed:false,inputDispatchSequence:sequence});
  const api=async(method,path,body)=>{
    calls.push({method,path,body});
    if(path==='/v0/player?category=USER')return {available:true,x:0.5,y:80,z:0.5,clientTick:++tick,[legacyField?'gamemode':'gameMode']:gameMode};
    if(path==='/v0/world/block?x=0&y=79&z=0&category=USER') {footingReads++;return {available:true,block:confirmFooting?'minecraft:stone':'minecraft:air'};}
    if(path==='/v0/input/state')return input();
    if(method==='POST'&&path==='/v0/input/release-all') {
      assert.equal(screen,'GameModeSwitcherScreen');assert.equal(held,1);assert(selected);
      if(commitDuringCleanup)gameMode=selected;
      held=0;screen='';sequence++;return{type:'input.release_all'};
    }
    if(method==='POST'&&path==='/v0/pipelines') {
      assert(footingReads>=2,'No mode input may be dispatched before two actual footing observations');
      for(const step of body.steps) {
        if(step.type==='key'&&step.key===292) {
          held=step.action;
          if(step.action===0) {assert.equal(screen,'GameModeSwitcherScreen');assert(selected);gameMode=selected;screen='';}
        } else if(step.type==='key.tap'&&step.key===293) {assert.equal(held,1);screen='GameModeSwitcherScreen';}
        else assert.fail('Unexpected native input '+JSON.stringify(step));
        sequence++;
      }
      const operationId=String(operations.size+1);operations.set(operationId,{state:'completed'});return {operationId};
    }
    const waiting=path.match(/^\/v0\/operations\/(\d+)\/wait$/);
    if(waiting)return operations.get(waiting[1]);
    assert.fail('Unexpected Runtime call: '+method+' '+path);
  };
  const ctx={api,target,samples,tree,nodes:value=>value.children,
    enterWorld:async()=>{calls.push({enterWorld:true});assert.equal(screen,'');},
    command:async command=>{calls.push({command});
      if(command==='gamemode creative'){assert(commitDuringCleanup);gameMode='creative';}
      else assert(['fill -2 79 -2 2 79 2 minecraft:stone','tp @s 0.5 80 0.5 0 0'].includes(command));
      return {route:'NATIVE',effectConfirmed:false};},
    wait:async(predicate,label)=>{const value=await tree();assert(predicate(value),label);return value;},
    ui:async(selector,action)=>{assert.equal(action,'hover');const target=nodes().find(node=>node.nodeId===selector.nodeId);assert(target);selected=target.label;sequence++;return {inputRoute:'GAME_ROUTED'};},
    check:async(name,run)=>{if(name.includes('actual private game-mode choices'))await run();}};
  return {ctx,calls,samples,state:()=>({gameMode,held,screen,footingReads})};
}

test('the real helper consumes formal USER gameMode, observes footing, releases the modifier and restores native mode',async()=>{
  const runtime=controlledRuntime();await uiNavigationCompletionActions(runtime.ctx);
  assert.deepEqual(runtime.state(),{gameMode:'creative',held:0,screen:'',footingReads:3});
  assert.equal(runtime.samples.length,1);const proof=runtime.samples[0];
  assert.equal(proof.case,'navigation_native_game_mode_completion');assert.equal(proof.initialMode,'creative');assert.equal(proof.restored,true);
  assert.deepEqual(proof.operations.map(value=>value.player.gameMode),['survival','creative']);
  assert.deepEqual(proof.operations.map(value=>value.uncommitted.gameMode),['creative','survival']);
  assert.equal(proof.safeFooting.player.y,80);assert.equal(proof.safeFooting.block.block,'minecraft:stone');
  assert.equal(runtime.calls.filter(call=>call.path==='/v0/pipelines').length,5);
  assert.equal(proof.cancelledSelection.later.gameMode,'creative');assert(proof.cancelledSelection.later.clientTick>=proof.cancelledSelection.uncommitted.clientTick+4);
  assert.deepEqual(runtime.calls.filter(call=>call.command).map(call=>call.command),['fill -2 79 -2 2 79 2 minecraft:stone','tp @s 0.5 80 0.5 0 0']);
});

test('a legacy gamemode-only player response fails closed before Arrange or player input',async()=>{
  const runtime=controlledRuntime({legacyField:true});await assert.rejects(uiNavigationCompletionActions(runtime.ctx),assert.AssertionError);
  assert.equal(runtime.calls.some(call=>call.command||call.path==='/v0/pipelines'),false);assert.equal(runtime.samples.length,0);
});

test('five native feature facts are explicit and an unknown target is rejected before navigation',()=>{
  assert.deepEqual(nativeNavigationFeatures('1.20.1-forge'),{font:false,worldSettings:false,dialogs:false});
  assert.deepEqual(nativeNavigationFeatures('1.21.1-neoforge'),{font:true,worldSettings:false,dialogs:false});
  for(const target of ['26.1.2-neoforge','26.3-neoforge','26.3-fabric'])
    assert.deepEqual(nativeNavigationFeatures(target),{font:true,worldSettings:true,dialogs:true});
  assert.throws(()=>nativeNavigationFeatures('unregistered'),assert.AssertionError);
});

test('older targets enter the owned world, expose four native choices, commit twice and cancel without nonexistent menu input',async()=>{
  for(const target of ['1.20.1-forge','1.21.1-neoforge']) {
    const runtime=controlledRuntime({target});await uiNavigationCompletionActions(runtime.ctx);
    assert.deepEqual(runtime.state(),{gameMode:'creative',held:0,screen:'',footingReads:3});
    assert.equal(runtime.calls.filter(value=>value.enterWorld).length,1);
    const sample=runtime.samples.find(value=>value.case==='navigation_native_game_mode_completion');assert(sample);
    assert.deepEqual(sample.operations.map(value=>value.player.gameMode),['survival','creative']);
    assert.equal(sample.operations.every(value=>value.choice.effect==='hover_then_release_debug_modifier'),true);
    assert.equal(runtime.calls.filter(value=>value.path==='/v0/pipelines').length,5);
    assert(runtime.samples.filter(value=>value.case==='navigation_native_unavailable').every(value=>value.operationCoverage===false));
  }
});

test('strong cleanup cannot submit the native pending choice on any of the five targets',async()=>{
  for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']) {
    const runtime=controlledRuntime({target,commitDuringCleanup:true});
    await assert.rejects(uiNavigationCompletionActions(runtime.ctx),/Cancelled native menu submitted a late selection/);
    assert.equal(runtime.samples.some(sample=>sample.case==='navigation_native_game_mode_completion'),false);
  }
});

function creationContext(target,experimentsAvailable) {
  let screen='TitleScreen',tab='Game',preset=0;const worlds=['immutable-baseline'],calls=[],samples=[];
  const button=(nodeId,key,label,extra={})=>({nodeId,translationKey:key,label,role:'button',visible:true,active:true,actions:['click'],...extra});
  const tree=async()=>({dataCategory:'USER',treeTruncated:false,screenClass:'native.'+screen,
    children:screen==='SelectWorldScreen'?[...worlds.map(worldId=>button(worldId,'',worldId,{worldId})),button('create','selectWorld.create','Create New World')]
      :screen==='CreateWorldScreen'?[button('cancel','gui.cancel','Cancel'),
        ...(tab==='World'?[button('preset','','World Type: '+(preset?'Single Biome':'Default')),button('customize','selectWorld.customizeType','Customize...')]
          :experimentsAvailable?[button('experiments','selectWorld.experiments','Experiments')]:[])]
      :screen==='CreateBuffetWorldScreen'?[button('biome','','Native biome',{role:'list_item'}),button('done','gui.done','Done')]
      :screen==='ExperimentsScreen'?[button('cancel-experiments','gui.cancel','Cancel')]:[]});
  const ui=async(selector,action='click')=>{calls.push({selector,action});assert.equal(action,'click');
    if(selector.role==='tab'){assert.equal(screen,'CreateWorldScreen');assert(['World','Game'].includes(selector.label));tab=selector.label;return;}
    const found=(await tree()).children.filter(node=>node.nodeId===selector.nodeId);assert.equal(found.length,1);
    if(selector.nodeId==='preset')preset++;
    else if(selector.nodeId==='biome')assert.equal(screen,'CreateBuffetWorldScreen');
    else if(selector.nodeId==='done')screen='CreateWorldScreen';
    else if(selector.nodeId==='cancel-experiments')screen='CreateWorldScreen';
    else if(selector.nodeId==='cancel')screen='SelectWorldScreen';
    else if(selector.nodeId==='create')screen='CreateWorldScreen';
    else if(selector.nodeId==='customize')screen='CreateBuffetWorldScreen';
    else if(selector.nodeId==='experiments')screen='ExperimentsScreen';
    else assert.fail('Unexpected native creation target '+selector.nodeId);
  };
  const ctx={target,samples,tree,nodes:value=>value.children,ui,
    title:async()=>{screen='TitleScreen';},
    click:async key=>{if(key==='menu.singleplayer'){screen='SelectWorldScreen';return;}const view=await tree();
      const found=view.children.filter(node=>node.translationKey===key);assert.equal(found.length,1,key);return ui({nodeId:found[0].nodeId});},
    api:async(method,path)=>{assert.equal(method,'GET');assert.equal(path,'/v0/input/state');return {pressedKeyCount:0,pressedButtonCount:0,inputCleanupPending:false,inputCleanupFailed:false};},
    wait:async(predicate,label)=>{const value=await tree();assert(predicate(value),label);return value;},
    check:async(name,run)=>{if(name.includes('single-biome editor'))await run();}};
  return {ctx,calls,samples,state:()=>({screen,worlds})};
}

test('single-biome draft and optional experiments cancel through actual semantic controls on all five target routes',async()=>{
  for(const target of ['1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric']) {
    for(const available of [false,true]) {
      const run=creationContext(target,available);await uiNavigationCompletionActions(run.ctx);
      assert.deepEqual(run.state(),{screen:'TitleScreen',worlds:['immutable-baseline']});
      const sample=run.samples.find(value=>value.case==='navigation_world_creation_completion');assert(sample);
      assert.equal(sample.worldCreated,false);assert.equal(sample.worldListUnchanged,true);assert.equal(sample.experimentsChanged,false);
      assert.equal(sample.experimentsDisposition,available?'actual_native_screen_visited':'native_entry_not_exposed_in_stable_build_not_validated');
      assert.equal(run.calls.filter(value=>value.selector.nodeId==='create').length,1,'Never press the actual world creation confirmation');
      assert(!run.calls.some(value=>Object.keys(value.selector).some(field=>['x','y'].includes(field))));
    }
  }
});

function worldSettingsContext(target) {
  const viaOptions=target==='26.1.2-neoforge',samples=[],calls=[],visited=[],executed=[],operations=new Map();
  let screen='',revision=0,held=0,sequence=0,search='';
  const move=next=>{screen=next;revision++;visited.push(next||'GAMEPLAY');};
  const button=(key,label,extra={})=>({nodeId:`${screen}:${revision}:${key}`,translationKey:key,label,
    role:'button',visible:true,active:true,actions:['click'],...extra});
  const tree=async()=>({dataCategory:'USER',treeTruncated:false,screenClass:screen?'native.'+screen:'',
    screenIdentity:screen?revision:0,screenRevision:revision,interactionNodeCount:screen?2:0,
    children:screen==='PauseScreen'?[button('menu.options','Options...'),
      ...(!viaOptions?[button('options.worldOptions.button','World Options...')]:[])]
      :screen==='OptionsScreen'?[button('options.worldOptions.button','World Options...'),button('gui.done','Done')]
      :screen==='WorldOptionsScreen'?[button('editGamerule.inGame.button','Game Rules...'),
        button(viaOptions?'gui.done':'gui.cancel',viaOptions?'Done':'Cancel')]
      :screen==='InWorldGameRulesScreen'?[button('gui.cancel','Cancel'),button('rule-mutation','Change game rule')]
      :screen==='DebugOptionsScreen'?[button('debug-search','Search',{role:'text_field',actions:['type_text'],value:search})]:[]});
  const ui=async(selector,action='click',extra)=>{
    assert.deepEqual(Object.keys(selector),['nodeId'],'Every UI action must resolve an actual semantic node');
    const value=await tree(),found=value.children.filter(node=>node.nodeId===selector.nodeId);
    assert.equal(found.length,1,'No missing-page, stale-node or direct callback fallback');
    const node=found[0];calls.push({screen,selector,action,extra,key:node.translationKey});
    assert(node.actions.includes(action));
    if(action==='type_text') {assert.equal(screen,'DebugOptionsScreen');assert.equal(node.translationKey,'debug-search');assert.equal(extra.text,'fps');search=extra.text;sequence++;return;}
    assert.equal(action,'click');
    if(node.translationKey==='menu.options') {assert(viaOptions);assert.equal(screen,'PauseScreen');move('OptionsScreen');}
    else if(node.translationKey==='options.worldOptions.button') {assert.equal(screen,viaOptions?'OptionsScreen':'PauseScreen');move('WorldOptionsScreen');}
    else if(node.translationKey==='editGamerule.inGame.button') {assert.equal(screen,'WorldOptionsScreen');move('InWorldGameRulesScreen');}
    else if(node.translationKey==='gui.cancel') {
      if(screen==='InWorldGameRulesScreen')move('WorldOptionsScreen');
      else {assert(!viaOptions);assert.equal(screen,'WorldOptionsScreen');move('PauseScreen');}
    } else if(node.translationKey==='gui.done') {
      assert(viaOptions);
      if(screen==='WorldOptionsScreen')move('OptionsScreen');
      else {assert.equal(screen,'OptionsScreen');move('PauseScreen');}
    } else assert.fail('A game rule or unrelated setting must not be mutated');
    sequence++;
  };
  const ctx={target,samples,tree,nodes:value=>value.children,ui,
    enterWorld:async()=>{assert.equal(screen,'');move('');},
    title:async()=>assert.fail('This scoped check must not open the title page'),
    command:async()=>assert.fail('World settings navigation must not use commands or direct game callbacks'),
    click:async key=>{const value=await tree(),found=value.children.filter(node=>node.translationKey===key);assert.equal(found.length,1,key+' must exist on the actual current page');return ui({nodeId:found[0].nodeId});},
    key:async key=>{calls.push({screen,key});assert.equal(key,256);assert.equal(held,0);
      if(!screen)move('PauseScreen');else {assert(['PauseScreen','DebugOptionsScreen'].includes(screen));move('');}sequence++;},
    wait:async(predicate,label)=>{const value=await tree();assert(predicate(value),label);return value;},
    api:async(method,path,body)=>{
      if(method==='GET'&&path==='/v0/input/state')return{pressedKeyCount:held,pressedButtonCount:0,inputCleanupPending:false,inputCleanupFailed:false,inputDispatchSequence:sequence};
      if(method==='POST'&&path==='/v0/pipelines') {
        calls.push({screen,method,path,body});
        if(body.cleanupOnComplete===false) {
          assert.equal(screen,'');assert.equal(held,0);
          assert.deepEqual(body.steps,[{type:'key',key:292,action:1},{type:'key.tap',key:295,holdMs:40}]);
          held=1;move('DebugOptionsScreen');
        } else {assert.equal(screen,'DebugOptionsScreen');assert.equal(held,1);assert.deepEqual(body.steps,[{type:'key',key:292,action:0}]);held=0;}
        sequence++;const operationId=String(operations.size+1);operations.set(operationId,{state:'completed'});return{operationId};
      }
      const waiting=path.match(/^\/v0\/operations\/(\d+)\/wait$/);
      if(method==='POST'&&waiting) {assert(operations.has(waiting[1]));return operations.get(waiting[1]);}
      assert.fail('Only actual input pipelines and input-state reads are allowed: '+method+' '+path);
    },
    check:async(name,run)=>{if(name.includes('world options, in-world game rules')) {executed.push(name);await run();}}
  };
  return{ctx,calls,samples,visited,executed,state:()=>({screen,held,search})};
}

test('26.1.2 world options use the actual Pause to Options route and Done-only native return pages',async()=>{
  const run=worldSettingsContext('26.1.2-neoforge');await uiNavigationCompletionActions(run.ctx);
  assert.equal(run.executed.length,1);assert.deepEqual(run.state(),{screen:'',held:0,search:'fps'});
  assert.deepEqual(run.visited,['GAMEPLAY','PauseScreen','OptionsScreen','WorldOptionsScreen','InWorldGameRulesScreen',
    'WorldOptionsScreen','OptionsScreen','PauseScreen','GAMEPLAY','DebugOptionsScreen','GAMEPLAY']);
  assert.deepEqual(run.calls.filter(value=>value.selector).map(value=>[value.screen,value.key,value.action]),[
    ['PauseScreen','menu.options','click'],['OptionsScreen','options.worldOptions.button','click'],
    ['WorldOptionsScreen','editGamerule.inGame.button','click'],['InWorldGameRulesScreen','gui.cancel','click'],
    ['WorldOptionsScreen','gui.done','click'],['OptionsScreen','gui.done','click'],['DebugOptionsScreen','debug-search','type_text']]);
  const proof=run.samples.find(value=>value.case==='navigation_world_settings_completion');assert(proof);
  assert.deepEqual(proof.nativeScreens,['native.WorldOptionsScreen','native.InWorldGameRulesScreen','native.DebugOptionsScreen']);
  assert.equal(proof.gamerulesChanged,false);assert.equal(proof.debugSettingsChanged,false);assert.equal(proof.debugSearchDraftOnly,true);
  assert.equal(proof.semanticTargets,true);assert.equal(proof.handwrittenCoordinates,false);
  assert(!run.calls.some(value=>value.key==='rule-mutation'));
});

test('26.3 world options remain directly reachable from Pause and return by native Cancel',async()=>{
  for(const target of ['26.3-neoforge','26.3-fabric']) {
    const run=worldSettingsContext(target);await uiNavigationCompletionActions(run.ctx);
    assert.equal(run.executed.length,1);assert.deepEqual(run.state(),{screen:'',held:0,search:'fps'});
    assert.deepEqual(run.visited,['GAMEPLAY','PauseScreen','WorldOptionsScreen','InWorldGameRulesScreen',
      'WorldOptionsScreen','PauseScreen','GAMEPLAY','DebugOptionsScreen','GAMEPLAY']);
    assert.deepEqual(run.calls.filter(value=>value.selector&&value.screen==='WorldOptionsScreen').map(value=>value.key),
      ['editGamerule.inGame.button','gui.cancel']);
    assert(!run.calls.some(value=>value.key==='menu.options'||value.key==='gui.done'||value.key==='rule-mutation'));
    const proof=run.samples.find(value=>value.case==='navigation_world_settings_completion');assert(proof);
    assert.deepEqual(proof.nativeScreens,['native.WorldOptionsScreen','native.InWorldGameRulesScreen','native.DebugOptionsScreen']);
    assert.equal(proof.gamerulesChanged,false);assert.equal(proof.debugSettingsChanged,false);
  }
});
