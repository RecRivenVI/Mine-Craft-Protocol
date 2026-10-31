import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';

// The host prepares a frozen machine fixture before this process. Formal Act is
// read-only: it neither expands the Runtime console catalogue nor loads chunks.
export async function serverAdapterActions({adapter,api,observations,check,until}) {
    observations.push({serverAdapterTool:{path:'components/validation_tools/integration-extension_platform/task/server-adapter-actions.mjs',sha256:createHash('sha256').update(await readFile(new URL(import.meta.url))).digest('hex')}});
    const catalog=await api('GET','/v0/extensions');
    const item=catalog.packages.find(value=>value.packageId===adapter);
    if(!['ae2','create','littletiles'].includes(adapter)){
        await check('client-only adapter has no entry or client capability on the dedicated server',async()=>{
            assert.equal(item?.status,'not_applicable',JSON.stringify(catalog));
            assert.match(item.reason,/No entrypoint/);
            assert.deepEqual(item.registeredCapabilities,[]);
            observations.push({case:'dedicated_adapter_side',adapter,clientEntryExecuted:false,registered:[]});
        });
        return;
    }
    assert.equal(item?.status,'active',JSON.stringify(catalog));
    const read=async query=>{
        const result=await api('POST','/v0/observe/deep',{category:'DEBUG',perspective:'server_authoritative',domains:['providers'],includeProviderData:true,providerIds:[adapter+':machine'],providerQuery:query,budgets:{maxProviders:1,providerTimeoutMs:1000,maxProviderBytes:32768}});
        const provider=result.providers.find(value=>value.providerId===adapter+':machine');
        assert.equal(provider?.status,'completed',JSON.stringify(result));
        return provider.data;
    };
    await check('dedicated server adapter reads existing native machine state without a player or client classes',async()=>{
        assert(item.registeredCapabilities.includes(adapter+':machine'));
        assert.equal((await api('GET','/v0/control/mode')).mode,'READ');
            const data=await read({x:0,y:80,z:0});
            assert.equal(data.available,true,JSON.stringify(data));
            assert.equal(data.chunkLoadRequested,false);
            assert.equal(data.supported,true,JSON.stringify(data));
            assert.equal(typeof data.nativeType,'string');
            if(adapter==='create'){assert.deepEqual(data.actors,[]);assert.equal(data.running,false);}
            const unavailable=await read({x:10000000,y:80,z:10000000});
            assert.equal(unavailable.available,false);assert.equal(unavailable.chunkLoadRequested,false);
            observations.push({case:'dedicated_machine',adapter,data,unavailable,zeroPlayers:true,clientInput:false});
    });
}
