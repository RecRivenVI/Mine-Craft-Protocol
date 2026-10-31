import {readFile,mkdir,writeFile,copyFile} from 'node:fs/promises';
import {resolve} from 'node:path';
import {createHash} from 'node:crypto';
const repo=resolve('../..');
const targets=[...(await readFile(resolve(repo,'settings.gradle.kts'),'utf8')).matchAll(/register\("([0-9.]+-(?:forge|neoforge|fabric))"\)/g)].map(match=>match[1]);
for(const side of ['left','right']) {
    const id='lifetime_'+side,namespace='io.github.recrivenvi.extensions.lifetime'+side,root=resolve('build/packages',id);
    await mkdir(resolve(root,'code'),{recursive:true});await mkdir(resolve(root,'docs'),{recursive:true});
    await copyFile(resolve('build/lifecycle-code',id+'.jar'),resolve(root,'code/adapter.jar'));
    await writeFile(resolve(root,'docs/overview.md'),'# Lifecycle fault fixture\n\nIndependent negative-gate package. Explicit armed counter sampling only; injected input/grant cleanup failure must never be reported as released. No game mutation or direct player input.\n');
    const variants={};for(const target of targets){
        const facts=await readFile(resolve(repo,`versions/${target}/target.properties`),'utf8');
        variants[target]={javaVersion:Number(facts.match(/^java_version=(\d+)$/m)[1]),code:['code/adapter.jar'],entrypoints:{common:[namespace+'.Entry']},mixins:[]};
    }
    const files={};for(const file of ['code/adapter.jar','docs/overview.md'])files[file]=createHash('sha256').update(await readFile(resolve(root,file))).digest('hex');
    const manifest={formatVersion:1,sdkApi:1,id,version:'1.0.0',javaNamespace:namespace,files,variants,dependencies:{mods:{},packages:{}},capabilities:[id+':state',id+':input'],requestedScopes:['read','debug.world.read','debug.provider','input'],exports:[],documents:{overview:'docs/overview.md'},tests:{}};
    await writeFile(resolve(root,'extension.json'),JSON.stringify(manifest,null,2)+'\n');
}
