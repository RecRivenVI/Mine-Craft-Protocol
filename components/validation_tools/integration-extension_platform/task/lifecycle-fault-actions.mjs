import assert from 'node:assert/strict';

export async function restoreLifecycleFaults({api,mode}) {
    try{await api('POST','/v0/control/emergency-release');}catch(error){if(error.code!=='CONTROL_INPUT_CLEANUP_FAILED')throw error;}
    if((await api('GET','/v0/control/mode')).mode!=='OPERATE')await mode('OPERATE');
    const worldFingerprint=(await api('GET','/v0/world/fingerprint?category=DEBUG')).worldFingerprint;
    await api('POST','/v0/debug/arm',{worldFingerprint,namespaces:['provider'],ttlMs:15000});
    for(const id of ['lifetime_left','lifetime_right'])for(const action of ['recover','stop']){
        const value=await api('POST','/v0/observe/deep',{category:'DEBUG',perspective:'server_authoritative',domains:['providers'],includeProviderData:true,providerIds:[id+':state'],providerQuery:{}});
        const expectedResourceVersion=value.resourceRevisionRefs.find(ref=>ref.resourceKey===id+':state'&&ref.mutationPreconditionEligible);assert(expectedResourceVersion);
        await api('POST','/v0/debug/mutations',{operation:'provider.mutate',providerId:id+':state',worldFingerprint,expectedResourceVersion,mutation:{action},timeoutMs:1000});
    }
    await api('POST','/v0/control/emergency-release');
    await api('POST','/v0/debug/disarm');
}

// Two separately installed/approved SDK packages. No native game mutation in either fixture.
export async function lifecycleFaultActions({api,mode,acquire,key,screen,until,check,observations}) {
    const ids=['lifetime_left','lifetime_right'],pause=ms=>new Promise(done=>setTimeout(done,ms));
    const read=async id=>{
        const value=await api('POST','/v0/observe/deep',{category:'DEBUG',perspective:'server_authoritative',domains:['providers'],includeProviderData:true,providerIds:[id+':state'],providerQuery:{},budgets:{maxProviders:1,providerTimeoutMs:1000,maxProviderBytes:32768}});
        const provider=value.providers.find(item=>item.providerId===id+':state');assert.equal(provider?.status,'completed',JSON.stringify(value));
        return {data:provider.data,version:value.resourceRevisionRefs.find(ref=>ref.resourceKey===id+':state'&&ref.mutationPreconditionEligible)};
    };
    const fingerprint=(await api('GET','/v0/world/fingerprint?category=DEBUG')).worldFingerprint;
    const arm=()=>api('POST','/v0/debug/arm',{worldFingerprint:fingerprint,namespaces:['provider'],ttlMs:60000});
    const mutate=async(id,action)=>{
        const before=await read(id);assert(before.version);
        return api('POST','/v0/debug/mutations',{operation:'provider.mutate',providerId:id+':state',worldFingerprint:fingerprint,expectedResourceVersion:before.version,mutation:{action},timeoutMs:1000});
    };
    const inputReleased=async()=>{const value=await api('GET','/v0/input/state');assert.equal(value.pressedKeyCount,0);assert.equal(value.pressedButtonCount,0);};
    const ensureOperate=async()=>{if((await api('GET','/v0/control/mode')).mode!=='OPERATE')await mode('OPERATE');};
    await check('two independent installed packages isolate input cleanup failures and recover under explicit Debug authority',async()=>{
        await mode('OPERATE');await arm();
        for(const id of ids){await mutate(id,'start');}
        await until(async()=>{const data=await Promise.all(ids.map(read));return data.every(value=>value.data.samples>0)?data:false;},'Independent samplers did not start',6000);
        for(const id of ids){
            const other=ids.find(value=>value!==id),previous=(await read(other)).data.samples;
            await mutate(id,'fail_input');
            await mode('READ');
            let cleanupRejected;try{await api('POST','/v0/control/emergency-release');}catch(error){cleanupRejected=error;assert.equal(error.code,'CONTROL_INPUT_CLEANUP_FAILED',String(error));}
            assert(cleanupRejected,'Injected framework cleanup must not be reported as released');
            let rejected;try{await acquire();}catch(error){rejected=error;assert.equal(error.code,'CONTROL_INPUT_CLEANUP_FAILED',String(error));}
            assert(rejected,'Failed cleanup must fence TAKEOVER before player-input admission');
            assert.notEqual((await api('GET','/v0/control/mode')).mode,'TAKEOVER');await inputReleased();
            await until(async()=>(await read(other)).data.samples>previous,'Other package stopped because of independent input failure',5000);
            const diagnostic=await api('GET','/v0/extensions/resources');assert(diagnostic.inputCleanup.some(report=>report.adapterId===id+':input'&&report.status==='failed'),JSON.stringify(diagnostic));
            await ensureOperate();await mutate(id,'recover');await api('POST','/v0/control/emergency-release');await acquire();await api('POST','/v0/control/release');await ensureOperate();
            observations.push({case:'independent_input_failure',packageId:id,reason:rejected.runtimeError,diagnostic,otherSamplerContinued:true,reacquired:true});
        }
        await api('POST','/v0/debug/disarm');
    });
    await check('two package grant cleanup failures are named, stop sampling, and recover without reviving retired work',async()=>{
        for(const failed of ids){
            await ensureOperate();const grant=await arm();
            for(const id of ids){await mutate(id,'recover');await mutate(id,'start');}
            await mutate(failed,'fail_cleanup');await api('POST','/v0/debug/disarm');
            const reports=await until(async()=>{const value=await api('GET','/v0/extensions/resources'),rows=value.cleanup.filter(row=>ids.includes(row.packageId)&&row.contextId===grant.debugArmId);return rows.length===2&&rows.every(row=>row.complete)?rows:false;},'Independent grant cleanup did not finish',6000);
            assert.equal(reports.find(row=>row.packageId===failed).released,false,JSON.stringify(reports));
            assert.equal(reports.find(row=>row.packageId!==failed).released,true,JSON.stringify(reports));
            const errors=reports.find(row=>row.packageId===failed).resources.filter(row=>row.status!=='released');
            assert.equal(errors.length,1,JSON.stringify(reports));assert.match(errors[0].resourceId,/^sampler-/);assert.equal(errors[0].ownerThread,'server_thread');assert.equal(errors[0].status,'failed');assert.equal(errors[0].failure,'IllegalStateException');
            const stopped=await Promise.all(ids.map(read));assert(stopped.every(value=>!value.data.running));
            await pause(350);assert.deepEqual((await Promise.all(ids.map(read))).map(value=>value.data.samples),stopped.map(value=>value.data.samples));
            observations.push({case:'independent_grant_failure',failed,reports,noLateSampling:true});
        }
        await ensureOperate();await arm();for(const id of ids){await mutate(id,'recover');await mutate(id,'start');}
        await until(async()=>(await read(ids[0])).data.running&&(await read(ids[1])).data.running,'Both packages did not recover',5000);
    });
    await check('native Screen and input Lease lifetimes retire independently of WORLD and CONNECTION',async()=>{
        for(const id of ids)await mutate(id,'watch_contexts');
        await acquire();await key(69);await screen(value=>/InventoryScreen$/.test(value.screenClass));
        const open=await until(async()=>{const value=(await read(ids[0])).data;return value.contexts.INPUT_LEASE&&value.contexts.SCREEN&&value.watchedContexts[value.contexts.INPUT_LEASE]&&value.watchedContexts[value.contexts.SCREEN]?value:false;},'Native context scopes were not observed',5000);
        const input=open.contexts.INPUT_LEASE,screenId=open.contexts.SCREEN,world=open.contexts.WORLD,connection=open.contexts.CONNECTION;
        await api('POST','/v0/control/release');
        const released=await until(async()=>{const value=(await read(ids[0])).data;return value.watchedContexts[input]===false?value:false;},'Retired Lease scope still publishes',5000);
        assert.equal(released.watchedContexts[world],true);assert.equal(released.watchedContexts[connection],true);assert.equal(released.watchedContexts[screenId],true);
        await acquire();await key(256);await screen(value=>!value.screenClass);
        const closed=await until(async()=>{const value=(await read(ids[0])).data;return value.watchedContexts[screenId]===false?value:false;},'Retired Screen scope still publishes',5000);
        assert.equal(closed.watchedContexts[world],true);assert.equal(closed.watchedContexts[connection],true);
        await api('POST','/v0/control/release');await ensureOperate();
        for(const id of ids)await mutate(id,'stop');await api('POST','/v0/debug/disarm');await inputReleased();
        observations.push({case:'native_context_lifetimes',open,released,closed,worldContextId:world,connectionContextId:connection,oldPublicationsRejected:true});
    });
}
