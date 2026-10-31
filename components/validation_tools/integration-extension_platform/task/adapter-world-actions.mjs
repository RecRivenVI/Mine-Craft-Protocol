import assert from 'node:assert/strict';

// Arrangement commands create only this adapter's owned test world. Act uses standard input.
export async function adapterWorldActions({adapter,api,tree,screen,nodes,mode,acquire,key,check,observations,until}) {
    const pause=ms=>new Promise(done=>setTimeout(done,ms));
    const operation=async started=>{
        if(!started.operationId)return started;
        const value=await api('POST',`/v0/operations/${started.operationId}/wait`,{timeoutMs:18000});
        assert.equal(value.state,'completed',JSON.stringify(value));return value;
    };
    const pipe=steps=>api('POST','/v0/pipelines',{steps,timeoutMs:18000}).then(operation);
    const ui=(node,action,extra={})=>{assert(node?.nodeId,'Semantic node required');return api('POST','/v0/ui/action',{selector:{nodeId:node.nodeId},action,...extra}).then(operation);};
    const arrange=async commands=>{
        await mode('OPERATE');
        for(const command of commands){observations.push({fixtureCommand:command,result:await api('POST','/v0/command/player',{command,route:'NATIVE'})});await pause(550);}
        await acquire();
    };
    const block=async(x,y,z,id)=>until(async()=>{
        const value=await api('GET',`/v0/world/block?x=${x}&y=${y}&z=${z}&category=USER`);
        return value.targetRef&&(!id||value.block===id)?value:false;
    },'Fixture target did not synchronize',10000);
    const use=async(x,y,z,options={})=>pipe([{type:'world.action',targetRef:(await block(x,y,z)).targetRef,action:'use',...options}]);
    const read=async(providerId,providerQuery={},perspective='server_authoritative')=>{
        const value=await api('POST','/v0/observe/deep',{category:'DEBUG',perspective,domains:['providers'],includeProviderData:true,providerIds:[providerId],providerQuery,budgets:{maxProviders:1,providerTimeoutMs:1000,maxProviderBytes:32768}});
        const provider=value.providers.find(item=>item.providerId===providerId);assert.equal(provider?.status,'completed',JSON.stringify(value));return provider.data;
    };
    const act=async action=>{
        const started=await api('POST','/v0/debug/evidence/act/start');
        const value=await action();
        const finished=await api('POST','/v0/debug/evidence/act/finish',{actId:started.actId});
        assert.equal(finished.contaminated,false,JSON.stringify(finished));
        observations.push({case:'native_act_receipt',started,finished});return value;
    };
    const catalog=await api('GET','/v0/extensions');assert.equal(catalog.packages.find(item=>item.packageId===adapter)?.status,'active',JSON.stringify(catalog));

    if(adapter==='ae2') {
        await check('AE2 real powered network, native cell insertion and semantic terminal storage roundtrip',async()=>{
            await arrange(['gamemode creative @s','fill -3 79 -2 5 79 7 minecraft:stone','fill -3 80 -2 5 85 7 minecraft:air',
                'setblock 0 80 3 ae2:drive','setblock 1 80 3 ae2:creative_energy_cell','setblock 0 81 3 ae2:1k_crafting_storage',
                'setblock 0 80 4 ae2:pattern_provider','setblock 1 80 4 ae2:molecular_assembler','clear @s',
                'item replace entity @s hotbar.0 with ae2:fluix_glass_cable','item replace entity @s hotbar.1 with ae2:crafting_terminal',
                'item replace entity @s hotbar.2 with minecraft:oak_log 2','item replace entity @s hotbar.3 with ae2:item_storage_cell_1k',
                'tp @s -0.5 80 0.5 0 25','gamemode survival @s']);
            await act(async()=>{
                await key(49);const cable=await use(-1,79,3,{face:'up'});
                await block(-1,80,3,'ae2:cable_bus');await key(50);const terminal=await use(-1,80,3,{face:'north'});
                await key(53);await use(0,80,3);
                let current=await screen(value=>/DriveScreen$/.test(value.screenClass),15000);
                const cell=nodes(current).find(node=>node.role==='slot'&&node.item==='ae2:item_storage_cell_1k');assert(cell,JSON.stringify(current));
                const inserted=await ui(cell,'shift_click');
                current=await screen(value=>nodes(value).some(node=>node.role==='slot'&&node.item==='ae2:item_storage_cell_1k'&&node.nodeId!==cell.nodeId),10000);
                observations.push({case:'ae2_cell_insert',cable,terminal,inserted,slots:nodes(current).filter(node=>node.role==='slot')});
                await key(256);await screen(value=>!value.screenClass);await use(-1,80,3,{face:'north'});
                current=await screen(value=>/TermScreen$/.test(value.screenClass),15000);
                observations.push({case:'ae2_terminal_initial',tree:current});
                const logs=nodes(current).find(node=>node.role==='slot'&&node.item==='minecraft:oak_log');assert(logs,JSON.stringify(current));
                const stored=await ui(logs,'shift_click');
                current=await screen(value=>nodes(value).some(node=>node.capabilitySource==='ae2:visible_ui'&&node.label==='minecraft:oak_log'),10000);
                const entry=nodes(current).find(node=>node.capabilitySource==='ae2:visible_ui'&&node.label==='minecraft:oak_log');
                observations.push({case:'ae2_stored_virtual_entry',stored,entry});
                const take=await ui(entry,'click');
                current=await screen(value=>value.carriedStack?.id==='minecraft:oak_log',10000);
                const destination=nodes(current).find(node=>node.role==='slot'&&node.slotOwner==='player_inventory'&&node.item==='minecraft:air');
                if(destination)await ui(destination,'click');
                else await ui(nodes(current).find(node=>node.nodeId===logs.nodeId),'click');
                await key(256);await screen(value=>!value.screenClass);await key(69);
                current=await screen(value=>/InventoryScreen$/.test(value.screenClass));
                const returned=nodes(current).filter(node=>node.role==='slot'&&node.item==='minecraft:oak_log');assert(returned.some(node=>node.count===2),JSON.stringify(current));
                observations.push({case:'ae2_storage_roundtrip',take,returned,normalGuiInput:true,directStorageCall:false});
                await key(256);await screen(value=>!value.screenClass);
            });
            const machine=await read('ae2:machine',{x:0,y:80,z:3});assert.equal(machine.online,true,JSON.stringify(machine));assert(machine.nodeCount>=5,JSON.stringify(machine));
            observations.push({case:'ae2_independent_server_network',machine,authority:'DEBUG'});
        });
        await check('AE2 prepared pattern is installed by normal slots and crafting is requested through its real amount/confirm UI',async()=>{
            const legacy=(await api('GET','/v0/session')).target==='1.20.1-forge';
            const pattern=legacy?'ae2:crafting_pattern{in:[{id:"minecraft:oak_log",Count:1b},{},{},{},{},{},{},{},{}],out:{id:"minecraft:oak_planks",Count:4b},recipe:"minecraft:oak_planks",substitute:0b,substituteFluids:0b}':'ae2:crafting_pattern[ae2:encoded_crafting_pattern={inputs:[{id:oak_log},{},{},{},{},{},{},{},{}],result:{id:oak_planks,count:4},recipeId:oak_planks,canSubstitute:false,canSubstituteFluids:false}]';
            await arrange(['give @s '+pattern]);
            await act(async()=>{
                await key(53);await use(0,80,4);
                let current=await screen(value=>/PatternProviderScreen$/.test(value.screenClass),10000);
                const encoded=nodes(current).find(node=>node.role==='slot'&&node.item==='ae2:crafting_pattern'&&node.slotOwner==='player_inventory');
                assert(encoded,JSON.stringify(current));const installed=await ui(encoded,'shift_click');
                await key(256);await screen(value=>!value.screenClass);await use(-1,80,3,{face:'north'});
                current=await screen(value=>/TermScreen$/.test(value.screenClass),10000);
                const logs=nodes(current).find(node=>node.role==='slot'&&node.item==='minecraft:oak_log'&&node.slotOwner==='player_inventory');assert(logs,JSON.stringify(current));await ui(logs,'shift_click');
                current=await screen(value=>nodes(value).some(node=>node.capabilitySource==='ae2:visible_ui'&&node.label==='minecraft:oak_planks'&&node.value?.craftable),15000);
                const craftable=nodes(current).find(node=>node.capabilitySource==='ae2:visible_ui'&&node.label==='minecraft:oak_planks');assert.equal(craftable.value.storedAmount,0);
                const request=await ui(craftable,'click');
                current=await screen(value=>/CraftAmountScreen$/.test(value.screenClass),10000);
                observations.push({case:'ae2_craft_amount',tree:current,installed,request});
                const next=nodes(current).find(node=>node.active!==false&&node.visible!==false&&node.role==='button'&&/next/i.test(node.label??''));assert(next,JSON.stringify(current));await ui(next,'click');
                current=await screen(value=>/CraftConfirmScreen$/.test(value.screenClass)&&nodes(value).some(node=>node.active!==false&&node.role==='button'&&/start/i.test(node.label??'')),15000);
                observations.push({case:'ae2_craft_confirmation',tree:current});
                const start=nodes(current).find(node=>node.active!==false&&node.visible!==false&&node.role==='button'&&/start/i.test(node.label??''));assert(start,JSON.stringify(current));const submitted=await ui(start,'click');
                current=await screen(value=>/TermScreen$/.test(value.screenClass)&&nodes(value).some(node=>node.label==='minecraft:oak_planks'&&node.value?.storedAmount>=4),20000);
                const output=nodes(current).find(node=>node.label==='minecraft:oak_planks'&&node.value?.storedAmount>=4);await ui(output,'click');
                current=await screen(value=>value.carriedStack?.id==='minecraft:oak_planks'&&value.carriedStack.count>=4,10000);
                const destination=nodes(current).find(node=>node.role==='slot'&&node.slotOwner==='player_inventory'&&node.item==='minecraft:air');assert(destination);await ui(destination,'click');
                await key(256);await screen(value=>!value.screenClass);await key(69);current=await screen(value=>/InventoryScreen$/.test(value.screenClass));
                const actual=nodes(current).find(node=>node.role==='slot'&&node.item==='minecraft:oak_planks'&&node.count>=4);assert(actual,JSON.stringify(current));
                observations.push({case:'ae2_real_autocrafting',status:'PASS',submitted,actual,preparedPatternIsArrange:true,directCraftSubmit:false});
                await key(256);await screen(value=>!value.screenClass);
            });
        });
    } else if(adapter==='exposure') {
        await check('Exposure normal attachment slots and two real shutters produce different frame identifiers',async()=>{
            await arrange(['gamemode creative @s','fill -6 79 -2 7 79 9 minecraft:stone','fill -6 80 -2 7 85 9 minecraft:air',
                'fill -4 80 6 -2 83 6 minecraft:red_concrete','fill 3 80 6 5 83 6 minecraft:blue_concrete','clear @s',
                'item replace entity @s hotbar.0 with exposure:camera','item replace entity @s hotbar.1 with exposure:color_film',
                'tp @s 0.5 80 0.5 0 0','time set 6000','gamemode survival @s']);
            await key(49);const empty=await read('exposure:camera',{},'client_known');assert.equal(empty.camera,true);assert.equal(empty.filmPresent,false);
            await act(async()=>{
                await pipe([{type:'key',key:340,action:1},{type:'delay',durationMs:100},{type:'mouse.button',button:1,action:1},{type:'delay',durationMs:100},{type:'mouse.button',button:1,action:0},{type:'key',key:340,action:0}]);
                let current=await screen(value=>!!value.screenClass,15000);
                const film=nodes(current).find(node=>node.role==='slot'&&node.item==='exposure:color_film');assert(film,JSON.stringify(current));
                await ui(film,'click');current=await tree();
                const slot=nodes(current).find(node=>node.role==='slot'&&node.slot===0&&node.nodeId!==film.nodeId);assert(slot,JSON.stringify(current));
                await ui(slot,'click');await key(256);await screen(value=>!value.screenClass);
                await until(async()=>{const data=await read('exposure:camera',{},'client_known');return data.filmPresent&&data.frameCount===0?data:false;},'Film did not load',10000);
                const button=()=>pipe([{type:'mouse.button',button:1,action:1},{type:'delay',durationMs:80},{type:'mouse.button',button:1,action:0}]);
                const shots=[];
                for(let index=0;index<2;index++){
                    const target=await block(index===0?-3:4,81,6);await pipe([{type:'world.action',targetRef:target.targetRef,action:'aim'}]);
                    await button();await until(async()=>{const data=await read('exposure:camera',{},'client_known');return data.active?data:false;},'Camera did not activate',10000);
                    const shutter=await button();const after=await until(async()=>{const data=await read('exposure:camera',{},'client_known');return data.frameCount===index+1?data:false;},'Real shutter did not produce a frame',20000);
                    shots.push({shutter,after});await key(256);await until(async()=>!(await read('exposure:camera',{},'client_known')).active,'Viewfinder did not close',10000);
                }
                const final=await read('exposure:camera',{},'client_known');assert.equal(new Set(final.frames.map(frame=>frame.identifier)).size,2,JSON.stringify(final));
                observations.push({case:'exposure_distinct_real_photos',empty,shots,final,directShutterCall:false,oracle:'actual_film_components'});
            });
        });
    } else if(adapter==='create') {
        await check('Create real bearing arrival chain and independent assembly state',async()=>{
            await arrange(['gamemode creative @s','fill -3 79 -2 6 79 7 minecraft:stone','fill -3 80 -2 6 86 7 minecraft:air',
                'setblock 0 81 2 create:creative_motor[facing=east]','setblock 1 81 2 create:mechanical_bearing[facing=east]',
                'setblock 2 81 2 minecraft:stone','setblock 2 82 2 minecraft:lever[face=floor,facing=north,powered=false]',
                'clear @s','tp @s 3.5 80 -0.5 -20 0']);
            const before=await read('create:machine',{x:1,y:81,z:2});
            assert.equal(before.supported,true,JSON.stringify(before));assert.equal(before.running,false,JSON.stringify(before));
            assert(before.actors.some(actor=>actor.handEmpty&&!actor.sneaking&&actor.mayBuild),JSON.stringify(before));
            assert.notEqual(before.speedRpm,0,JSON.stringify(before));
            const discovered=await block(1,81,2,'create:mechanical_bearing');
            assert(discovered.extensionParts?.some(part=>part.capabilitySource==='create:parts'),JSON.stringify(discovered));
            const input=await act(()=>pipe([{type:'world.action',targetRef:discovered.extensionParts.find(part=>part.capabilitySource==='create:parts').targetRef,action:'use'}]));
            let after;try{after=await until(async()=>{const value=await read('create:machine',{x:1,y:81,z:2});return value.running?value:false;},'Bearing did not assemble',5000);}
            catch(error){
                const first=await read('create:machine',{x:1,y:81,z:2});
                const longer=await act(()=>pipe([{type:'mouse.button',button:1,action:1},{type:'delay',durationMs:200},{type:'mouse.button',button:1,action:0}]));
                await pause(500);after=await read('create:machine',{x:1,y:81,z:2});
                observations.push({case:'create_bounded_same_native_input_diagnostic',first,longer,after});
            }
            const arrived=after.nativeInteraction.clientCalls>before.nativeInteraction.clientCalls&&after.nativeInteraction.serverCalls>before.nativeInteraction.serverCalls;
            observations.push({case:'create_real_assembly',before,input,after,nativeArrived:arrived,assembly:after.running?'PASS':'NOT_CLOSED',directAssemblyCall:false,
                movingPartStatus:'PREVIEW_NOT_NATIVE_PARENT_PICKABLE'});
            assert(arrived,'Normal interaction did not arrive at both Create native callbacks: '+JSON.stringify(after));
            if(after.running){assert.equal(after.hasMovedContraption,true);assert(after.contraptionParts.length>0);}
        });
    } else if(adapter==='littletiles') {
        await check('LittleTiles typed upstream fixture, client micro geometry and native door callback diagnosis',async()=>{
            await arrange(['gamemode creative @s','fill -5 79 -3 8 79 10 minecraft:stone','fill -5 80 -3 8 88 10 minecraft:air','clear @s','tp @s -1.5 80 3.5 -90 25','kill @e[type=!minecraft:player,distance=..32]']);
            await mode('OPERATE');
            const query={category:'DEBUG',perspective:'server_authoritative',domains:['providers'],includeProviderData:true,providerIds:['littletiles:example_fixture'],providerQuery:{},budgets:{maxProviders:1,providerTimeoutMs:1000,maxProviderBytes:16384}};
            const observed=await api('POST','/v0/observe/deep',query);assert.equal(observed.providers[0].status,'completed',JSON.stringify(observed));
            const version=observed.resourceRevisionRefs.find(ref=>ref.resourceType==='provider'&&ref.resourceKey==='littletiles:example_fixture');assert(version?.mutationPreconditionEligible);
            const fingerprint=(await api('GET','/v0/world/fingerprint?category=DEBUG')).worldFingerprint;
            const arm=(await api('POST','/v0/debug/arm',{worldFingerprint:fingerprint,namespaces:['provider'],ttlMs:15000})).debugArmId;
            const beforeInput=await api('GET','/v0/input/state');
            let fixture;
            try{fixture=await api('POST','/v0/debug/mutations',{operation:'provider.mutate',providerId:'littletiles:example_fixture',worldFingerprint:fingerprint,expectedResourceVersion:version,mutation:{operation:'prepare_example',variant:'standalone_axis'},timeoutMs:1000,resultByteBudget:16384});}
            finally{await api('POST','/v0/debug/disarm');}
            const afterInput=await api('GET','/v0/input/state');assert.equal(beforeInput.inputDispatchSequence,afterInput.inputDispatchSequence);assert.equal(afterInput.pressedKeyCount,0);assert.equal(afterInput.pressedButtonCount,0);
            observations.push({case:'littletiles_explicit_debug_arrange',fixture,armIdentity:arm,leaseRequired:false,gameplayEvidence:false});
            await acquire();await key(49);
            await act(async()=>{
                const placement=await use(0,79,3,{face:'up'});
                let scene=await until(async()=>{const value=await api('GET','/v0/world/scene?category=USER&radius=5&blockLimit=512&entityLimit=32');return value.blocks?.some(block=>block.extensionParts?.some(part=>part.capabilitySource==='littletiles:parts'))?value:false;},'Placed micro geometry did not reach client cache',15000);
                const parents=scene.blocks.filter(block=>block.extensionParts?.some(part=>part.capabilitySource==='littletiles:parts'));
                const parent=parents.sort((a,b)=>Math.abs(a.y-81)-Math.abs(b.y-81))[0];
                await key(50); // Empty hand, and wait for placement's authoritative client update.
                await pause(800);
                const before=await read('littletiles:machine',{x:parent.x,y:parent.y,z:parent.z});
                assert.equal(before.supported,true,JSON.stringify(before));assert(before.boxCount>0);
                assert.equal(before.animations.length,0,'Old animation entities must not become the oracle for this input');
                const fresh=await block(parent.x,parent.y,parent.z);
                const part=fresh.extensionParts.find(part=>part.capabilitySource==='littletiles:parts');
                for(const candidate of parent.extensionParts){assert(candidate.bounds.minX<candidate.bounds.maxX);assert(candidate.aimPoint.x>=candidate.bounds.minX&&candidate.aimPoint.x<=candidate.bounds.maxX);}
                observations.push({case:'littletiles_static_native_geometry',placement,parent,before,parts:parent.extensionParts});
                const click=await pipe([{type:'world.action',targetRef:part.targetRef,action:'use'}]);
                await pause(900);const after=await read('littletiles:machine',{x:parent.x,y:parent.y,z:parent.z});
                const states=[...(after.structures??[]),...(after.animations??[]).flatMap(item=>item.structures??[])];
                // Animation migration creates a new native structure identity; the isolated fixture had no animation entity before Act.
                const opened=after.animations?.length===1&&states.some(item=>item.currentState===1&&before.structures.some(original=>original.nativeType===item.nativeType&&original.currentState===0));
                observations.push({case:'littletiles_normal_door_input',click,before,after,diagnosticOnly:after.interactionTrace,directDoorSetter:false,
                    doorBusinessStatus:opened?'OPEN_CONFIRMED':'NOT_CLOSED'});
                assert(after.interactionTrace?.sequence>before.interactionTrace?.sequence,'No LittleDoor native callback arrival: '+JSON.stringify(after));
                if(opened) {
                    const current=await api('GET',`/v0/world/block?x=${parent.x}&y=${parent.y}&z=${parent.z}&category=USER`);
                    const next=current.extensionParts?.find(item=>item.capabilitySource==='littletiles:parts');
                    if(next) {
                        const closedInput=await pipe([{type:'world.action',targetRef:next.targetRef,action:'use'}]);
                        const closed=await until(async()=>{const data=await read('littletiles:machine',{x:parent.x,y:parent.y,z:parent.z});return data.structures?.every(item=>item.currentState===0&&!item.changing)?data:false;},'Axis door did not close',10000);
                        observations.push({case:'littletiles_axis_return',closedInput,closed});
                    } else observations.push({case:'littletiles_axis_return',status:'NOT_CLOSED',reason:'moving_entity_has_no_native_parent_part_pick'});
                }
            });
        });
    } else throw new Error('Unknown world adapter: '+adapter);
    await api('POST','/v0/control/release');
}
