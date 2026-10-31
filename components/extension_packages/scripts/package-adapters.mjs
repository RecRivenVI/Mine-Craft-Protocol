import {readFile, writeFile, mkdir, copyFile, readdir} from 'node:fs/promises';
import {resolve} from 'node:path';
import {createHash} from 'node:crypto';
const repo=resolve('../..');
const targets=[...(await readFile(resolve(repo,'settings.gradle.kts'),'utf8')).matchAll(/register\("([0-9.]+-(?:forge|neoforge|fabric))"\)/g)].map(match=>match[1]);
for(const id of ['computercraft','modern_ui','emi']) {
    const spec=JSON.parse(await readFile(resolve(`packages/${id}/package.json`),'utf8'));
    const output=resolve(`build/packages/${id}`);await mkdir(resolve(output,'code'),{recursive:true});await mkdir(resolve(output,'docs'),{recursive:true});
    const candidates=(await readdir('build/adapter-code')).filter(name=>name===id+'.jar');
    if(candidates.length!==1)throw new Error('One standalone compiled adapter JAR required: '+id);
    await copyFile(resolve('build/adapter-code',candidates[0]),resolve(output,'code/adapter.jar'));
    await copyFile(resolve(`packages/${id}/overview.md`),resolve(output,'docs/overview.md'));
    const files={},variants={};
    for(const name of ['code/adapter.jar','docs/overview.md',...Object.keys(spec.resources??{})]) {
        if(spec.resources?.[name]!==undefined){await mkdir(resolve(output,name,'..'),{recursive:true});await writeFile(resolve(output,name),JSON.stringify(spec.resources[name])+'\n');}
        files[name]=createHash('sha256').update(await readFile(resolve(output,name))).digest('hex');
    }
    for(const target of spec.targets) {
        if(!targets.includes(target))throw new Error('Unregistered Target '+target);
        const facts=await readFile(resolve(repo,`versions/${target}/target.properties`),'utf8');
        const javaVersion=Number(facts.match(/^java_version=(\d+)$/m)?.[1]);
        variants[target]={javaVersion,code:['code/adapter.jar'],entrypoints:{client:[spec.javaNamespace+'.Entry']},mixins:spec.targetMixins?.[target]??spec.mixins??[],...(spec.targetMods?.[target]?{mods:spec.targetMods[target]}:{})};
    }
    const manifest={formatVersion:1,sdkApi:1,id,version:spec.version,javaNamespace:spec.javaNamespace,files,variants,
        dependencies:{mods:spec.mods,packages:{}},capabilities:spec.capabilities,requestedScopes:spec.requestedScopes??['ui'],exports:spec.exports??[],documents:{overview:'docs/overview.md'},tests:{}};
    await writeFile(resolve(output,'extension.json'),JSON.stringify(manifest,null,2)+'\n');
    console.log(`Packaged ${id} ${spec.version}: ${Object.keys(variants).join(', ')}`);
}
