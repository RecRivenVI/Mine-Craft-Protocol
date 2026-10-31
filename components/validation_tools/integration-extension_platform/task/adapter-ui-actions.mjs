import assert from 'node:assert/strict';

// Commands are explicit test arrangement; only normal input is acceptance Act.
export async function adapterUiActions({adapter,api,tree,screen:readScreen,nodes,mode,acquire,key,check,observations,until}) {
    const screen=(predicate,timeout=20000)=>readScreen(predicate,timeout);
    const operation=async started=>{
        const value=started.operationId?await api('POST',`/v0/operations/${started.operationId}/wait`,{timeoutMs:18000}):started;
        if(started.operationId)assert.equal(value.state,'completed',JSON.stringify(value));return value;
    };
    const ui=async(node,action,extra={})=>operation(await api('POST','/v0/ui/action',{selector:{nodeId:node.nodeId},action,...extra}));
    const command=async text=>{await new Promise(resolve=>setTimeout(resolve,550));const result=await api('POST','/v0/command/player',{command:text,route:'NATIVE'});observations.push({fixtureCommand:text,result});return result;};
    const catalog=await api('GET','/v0/extensions');const installed=catalog.packages.find(item=>item.packageId===adapter);
    assert.equal(installed?.status,'active',JSON.stringify(catalog));observations.push({adapter,catalog});
    if(adapter==='computercraft') {
        await check('CC visible terminal uses existing semantic Widget and real keyboard roundtrip',async()=>{
            await mode('OPERATE');
            for(const text of ['gamemode creative @s','clear @s','fill -2 80 0 2 84 4 minecraft:air','fill -2 79 0 2 79 4 minecraft:stone','tp @s .5 80 .5 0 0','setblock 0 80 2 computercraft:computer_advanced[facing=north]'])await command(text);
            await acquire();
            const block=await until(async()=>{const value=await api('GET','/v0/world/block?x=0&y=80&z=2&category=USER');return value.block==='computercraft:computer_advanced'&&value.targetRef?value:false;},'CC fixture block did not synchronize to the client',10000);
            const used=await operation(await api('POST','/v0/pipelines',{steps:[{type:'world.action',targetRef:block.targetRef,action:'use'}],timeoutMs:12000}));
            const opened=await screen(value=>value.screenClass.startsWith('dan200.computercraft.client.gui.')&&nodes(value).some(node=>node.capabilitySource==='computercraft:terminal'));
            let terminal=nodes(opened).find(node=>node.capabilitySource==='computercraft:terminal');
            assert(!terminal.nodeId.startsWith('extension:'),'Native Widget identity must be retained');
            const hovered=await ui(terminal,'hover');
            await ui(terminal,'type_text',{text:'lua',replace:false});await key(257);
            const interpreter=await screen(value=>nodes(value).some(node=>node.capabilitySource==='computercraft:terminal'&&/Interactive Lua|Lua [0-9]/.test(node.value)),20000);
            terminal=nodes(interpreter).find(node=>node.capabilitySource==='computercraft:terminal');
            await ui(terminal,'type_text',{text:'print(6*7)',replace:false});await key(257);
            const after=await screen(value=>nodes(value).some(node=>node.capabilitySource==='computercraft:terminal'&&node.value.split('\n').some(line=>line.trim()==='42')),20000);
            terminal=nodes(after).find(node=>node.capabilitySource==='computercraft:terminal');
            observations.push({case:'computercraft_terminal_roundtrip',used,hovered,terminal,act:'GAME_ROUTED',oracle:'client_visible_terminal',directBusinessCall:false,fixtureCommands:true});
            await key(256);await screen(value=>!value.screenClass);await api('POST','/v0/control/release');
        });
    } else if(adapter==='modern_ui') {
        await check('Modern UI framework tree and logical input navigate real configuration',async()=>{
            await acquire();await key(256);
            const pause=await screen(value=>nodes(value).some(node=>['menu.disconnect','menu.returnToMenu'].includes(node.translationKey)));
            const quit=nodes(pause).find(node=>['menu.disconnect','menu.returnToMenu'].includes(node.translationKey));
            const {finishBaselineWorldExit}=await import('../../conformance-world_read/task/interaction-baseline-actions.mjs');
            await finishBaselineWorldExit({api,clickNode:node=>ui(node,'click'),wait:screen,quit});
            await screen(value=>value.screenClass.endsWith('TitleScreen'));await acquire();
            let current=await tree();await ui(nodes(current).find(node=>node.translationKey==='fml.menu.mods'),'click');
            current=await screen(value=>value.screenClass.endsWith('ModListScreen'));
            const row=nodes(current).find(node=>node.role==='list_item'&&/Modern UI/.test(node.label));assert(row,JSON.stringify(current));
            await ui(row,'click');current=await tree();
            const config=nodes(current).find(node=>node.translationKey==='fml.menu.mods.config'&&node.active!==false);assert(config);
            await ui(config,'click');
            current=await screen(value=>nodes(value).some(node=>node.capabilitySource==='modern_ui:visible_views'),20000);
            const initial=nodes(current).filter(node=>node.capabilitySource==='modern_ui:visible_views');
            observations.push({case:'modern_ui_visible_initial',nodes:initial});
            const candidates=initial.filter(node=>node.role==='checkbox'&&typeof node.value==='boolean'&&node.label);
            assert(candidates.length>=2,JSON.stringify(initial));
            const selected=candidates.find(node=>node.value===true),other=candidates.find(node=>node.value===false);assert(selected&&other);
            observations.push({beforeHover:await tree()});await ui(other,'hover');observations.push({afterHover:await tree()});const result=await ui(other,'click');
            current=await screen(value=>nodes(value).some(node=>node.nodeId===other.nodeId&&node.value===true),10000);
            observations.push({case:'modern_ui_real_navigation',from:selected,to:other,result,nodes:nodes(current).filter(node=>node.capabilitySource==='modern_ui:visible_views')});
            await ui(nodes(current).find(node=>node.nodeId===selected.nodeId),'click');
            current=await screen(value=>nodes(value).some(node=>node.nodeId===selected.nodeId&&node.value===true),10000);
            const preference=nodes(current).find(node=>node.capabilitySource==='modern_ui:visible_views'&&node.label==='Preferences');
            if(preference?.value!==true){await ui(preference,'click');current=await screen(value=>nodes(value).some(node=>node.role==='slider'),10000);}
            // The framework animates the panel layout after navigation. Observe settled geometry;
            // the Runtime must keep rejecting a target which moves during pointer travel.
            let previousLayout='',stableSamples=0;
            current=await screen(value=>{
                const layout=JSON.stringify(nodes(value).filter(node=>node.capabilitySource==='modern_ui:visible_views'&&['slider','text_field'].includes(node.role)).map(node=>[node.nodeId,node.x,node.y,node.width,node.height]));
                stableSamples=layout===previousLayout?stableSamples+1:0;previousLayout=layout;
                return stableSamples>=5&&nodes(value).some(node=>node.role==='slider');
            },10000);
            const slider=nodes(current).find(node=>node.capabilitySource==='modern_ui:visible_views'&&node.role==='slider');assert(slider);
            const editor=nodes(current).find(node=>node.capabilitySource==='modern_ui:visible_views'&&node.role==='text_field');assert(editor);
            const originalText=editor.value;
            const adjusted=await ui(slider,'set_value',{value:.5});
            current=await screen(value=>nodes(value).some(node=>node.nodeId===slider.nodeId&&node.value===50),10000);
            const edited=await ui(nodes(current).find(node=>node.nodeId===editor.nodeId),'type_text',{text:originalText,replace:true});
            await key(258);
            current=await screen(value=>nodes(value).some(node=>node.nodeId===slider.nodeId&&node.value===slider.value),10000);
            observations.push({case:'modern_ui_slider_text_restore',original:{slider,editor},adjusted,edited,nodes:nodes(current).filter(node=>[slider.nodeId,editor.nodeId].includes(node.nodeId)),directBusinessCall:false});
            const scrollable=nodes(current).filter(node=>node.capabilitySource==='modern_ui:visible_views'&&node.canPanY&&typeof node.scrollY==='number').sort((a,b)=>b.width*b.height-a.width*a.height)[0];assert(scrollable);
            const scrolled=await ui(scrollable,'scroll',{yOffset:-3});
            current=await screen(value=>nodes(value).some(node=>node.nodeId===scrollable.nodeId&&node.scrollY!==scrollable.scrollY),10000);
            const scrollState=nodes(current).find(node=>node.nodeId===scrollable.nodeId);
            await ui(scrollState,'scroll',{yOffset:3});
            await screen(value=>nodes(value).some(node=>node.nodeId===scrollable.nodeId&&node.scrollY===scrollable.scrollY),10000);
            observations.push({case:'modern_ui_native_scroll_restore',before:scrollable,after:scrollState,scrolled,directBusinessCall:false});
            const dragStep={type:'ui.drag',fromSelector:{nodeId:slider.nodeId},toSelector:{nodeId:slider.nodeId},fromOffset:{x:.25,y:.5},toOffset:{x:.75,y:.5},durationMs:500,segments:12};
            const dragged=await operation(await api('POST','/v0/pipelines',{steps:[dragStep],timeoutMs:12000}));
            current=await screen(value=>nodes(value).some(node=>node.nodeId===slider.nodeId&&node.value!==slider.value),10000);
            observations.push({case:'modern_ui_native_drag',operation:dragged,after:nodes(current).find(node=>node.nodeId===slider.nodeId),directBusinessCall:false});
            await ui(nodes(current).find(node=>node.nodeId===editor.nodeId),'type_text',{text:originalText,replace:true});await key(258);
            await screen(value=>nodes(value).some(node=>node.nodeId===slider.nodeId&&node.value===slider.value),10000);
            const active=await api('POST','/v0/pipelines',{steps:[{...dragStep,durationMs:6000,segments:100}],timeoutMs:14000});
            await until(async()=>{const input=await api('GET','/v0/input/state');return input.pressedButtonCount>0?input:false;},'Framework drag did not press',10000);
            await mode('READ');
            const terminal=await api('POST',`/v0/operations/${active.operationId}/wait`,{timeoutMs:8000});assert.equal(terminal.state,'cancelled',JSON.stringify(terminal));
            const clean=await until(async()=>{const input=await api('GET','/v0/input/state');return input.pressedKeyCount===0&&input.pressedButtonCount===0&&!input.inputCleanupPending?input:false;},'Framework revoke did not release input',8000);
            const sequence=clean.inputDispatchSequence;await new Promise(resolve=>setTimeout(resolve,350));assert.equal((await api('GET','/v0/input/state')).inputDispatchSequence,sequence);
            observations.push({case:'modern_ui_active_drag_mode_exit',terminal,clean,postCancelDispatch:0,physicalEsc:false});
            await acquire();current=await tree();
            await ui(nodes(current).find(node=>node.nodeId===editor.nodeId),'type_text',{text:originalText,replace:true});await key(258);
            await screen(value=>nodes(value).some(node=>node.nodeId===slider.nodeId&&node.value===slider.value),10000);
            for(let depth=0;depth<8;depth++){
                if(!(await tree()).screenClass.startsWith('icyllis.modernui.'))break;
                await key(256);
            }
            assert(!(await tree()).screenClass.startsWith('icyllis.modernui.'),'Modern UI back stack did not close');
        });
    } else if(adapter==='emi') {
        await check('EMI real search, recipe navigation and favourite use normal semantic input',async()=>{
            await acquire();await key(69);
            let current=await screen(value=>nodes(value).some(node=>node.capabilitySource==='emi:visible_ui'&&node.role==='text_field'),15000);
            let search=nodes(current).find(node=>node.capabilitySource==='emi:visible_ui'&&node.role==='text_field');
            const searched=await ui(search,'type_text',{text:'crafting table',replace:true});
            await key(257); // EMI deliberately consumes recipe/favourite hotkeys while search is focused.
            current=await screen(value=>nodes(value).some(node=>node.capabilitySource==='emi:visible_ui'&&node.item==='minecraft:crafting_table'),10000);
            const item=nodes(current).find(node=>node.capabilitySource==='emi:visible_ui'&&node.item==='minecraft:crafting_table');
            const initialCount=nodes(current).filter(node=>node.capabilitySource==='emi:visible_ui'&&node.item==='minecraft:crafting_table').length;
            await ui(item,'hover');await key(65);
            current=await screen(value=>nodes(value).filter(node=>node.capabilitySource==='emi:visible_ui'&&node.item==='minecraft:crafting_table').length===(initialCount>1?1:2),10000);
            observations.push({case:'emi_favourite',searched,item,nodes:nodes(current).filter(node=>node.capabilitySource==='emi:visible_ui')});
            await ui(nodes(current).find(node=>node.capabilitySource==='emi:visible_ui'&&node.item==='minecraft:crafting_table'),'hover');await key(65);
            current=await screen(value=>nodes(value).filter(node=>node.capabilitySource==='emi:visible_ui'&&node.item==='minecraft:crafting_table').length===initialCount,10000);
            const recipeTarget=nodes(current).find(node=>node.capabilitySource==='emi:visible_ui'&&node.item==='minecraft:crafting_table');assert(recipeTarget);
            await ui(recipeTarget,'hover');await key(82);
            current=await screen(value=>value.screenClass==='dev.emi.emi.screen.RecipeScreen'&&nodes(value).some(node=>node.capabilitySource==='emi:visible_ui'&&node.role==='ingredient'),15000);
            observations.push({case:'emi_native_recipe',nodes:nodes(current).filter(node=>node.capabilitySource==='emi:visible_ui'),directBusinessCall:false});
            await key(256);current=await screen(value=>!value.screenClass.endsWith('RecipeScreen'));
            search=nodes(current).find(node=>node.capabilitySource==='emi:visible_ui'&&node.role==='text_field');assert(search);
            await ui(search,'type_text',{text:'',replace:true});await key(257);await key(256);await screen(value=>!value.screenClass);
            await api('POST','/v0/control/release');
        });
    } else {
        throw new Error(`Adapter ${adapter} has no completed business smoke yet`);
    }
}
