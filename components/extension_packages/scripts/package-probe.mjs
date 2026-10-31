import {readFile, mkdir, writeFile, copyFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {resolve} from 'node:path';
const repo=resolve('../..'), output=resolve('build/packages/platform_probe');
const code=process.argv[2];
if(!code)throw new Error('Gradle supplies the compiled standalone SDK package JAR');
await mkdir(resolve(output,'code'),{recursive:true});
await mkdir(resolve(output,'docs'),{recursive:true});
await mkdir(resolve(output,'mixins'),{recursive:true});
await writeFile(resolve(output,'mixins/probe.json'), JSON.stringify({required:true,minVersion:'0.8',package:'io.github.recrivenvi.extensions.platformprobe.hooks.mixin',compatibilityLevel:'JAVA_17',mixins:['ServerTickHookMixin']})+'\n');
await copyFile(code,resolve(output,'code/adapter.jar'));
await writeFile(resolve(output,'docs/overview.md'),'# Platform probe\n\nOwner-thread sampling of a managed DEBUG grant. Passive physical-JVM MinecraftServer tick hook counter and explicit managed sampling are separate. Shared native helper identity is required. No world mutation or user input. Runtime read and typed Debug authorize separately.\n');
const variants={},extraFiles=[];
for(const [,target] of (await readFile(resolve(repo,'settings.gradle.kts'),'utf8')).matchAll(/register\("([0-9.]+-(?:forge|neoforge|fabric))"\)/g)){
  const facts=await readFile(resolve(repo,`versions/${target}/target.properties`),'utf8');
  const javaVersion=Number(facts.match(/^java_version=(\d+)$/m)?.[1]);
  if(!Number.isInteger(javaVersion))throw new Error(`Missing authoritative Java fact for ${target}`);
  let config='mixins/probe.json';
  if(target.endsWith('-forge')){
    const mapping=JSON.parse(await readFile(resolve(repo,'validations/integration-extension_platform/task/legacy-mapping.json'),'utf8'));
    if(mapping.target!==target||mapping.namedMethod!=='tickServer'||!/^m_[0-9]+_$/.test(mapping.runtimeMethod))throw new Error('Explicit legacy fixture mapping required');
    const nativeMapping=await readFile(resolve(repo,`versions/${target}/build/moddev/artifacts/namedToIntermediate.tsrg`),'utf8').catch(error=>{if(error.code==='ENOENT')return null;throw error;});
    if(nativeMapping!==null){
      const lines=nativeMapping.split(/\r?\n/),owner=lines.findIndex(line=>line.startsWith(mapping.class+' '));
      if(owner<0)throw new Error('Native mapping owner missing');
      let matched=false;
      for(const line of lines.slice(owner+1)){if(!line.startsWith('\t'))break;if(line.trim()===mapping.namedMethod+' '+mapping.descriptor+' '+mapping.runtimeMethod)matched=true;}
      if(!matched)throw new Error('Native Forge mapping differs from the explicit fixture input');
    }
    const values={'io/github/recrivenvi/extensions/platformprobe/hooks/mixin/ServerTickHookMixin':{[mapping.namedMethod]:'L'+mapping.class+';'+mapping.runtimeMethod+mapping.descriptor}};
    const refmap='mixins/platform_probe-forge.refmap.json';config='mixins/probe-forge.json';
    await writeFile(resolve(output,refmap),JSON.stringify({mappings:values,data:{searge:values}})+'\n');
    await writeFile(resolve(output,config),JSON.stringify({required:true,minVersion:'0.8',package:'io.github.recrivenvi.extensions.platformprobe.hooks.mixin',compatibilityLevel:'JAVA_17',mixins:['ServerTickHookMixin'],refmap})+'\n');
    extraFiles.push(refmap,config);
  }
  variants[target]={javaVersion,code:['code/adapter.jar'],entrypoints:{common:['io.github.recrivenvi.extensions.platformprobe.Entry']},mixins:[{config,side:'common'}]};
}
const files={};
for(const name of ['code/adapter.jar','docs/overview.md','mixins/probe.json',...extraFiles])files[name]=createHash('sha256').update(await readFile(resolve(output,name))).digest('hex');
const manifest={formatVersion:1,sdkApi:1,id:'platform_probe',version:'1.0.3',javaNamespace:'io.github.recrivenvi.extensions.platformprobe',
 files,variants,dependencies:{mods:{},packages:{}},capabilities:['platform_probe:sampling','platform_probe:native_ui'],
 requestedScopes:['debug.world.read','debug.provider','ui'],exports:['io.github.recrivenvi.extensions.platformprobe.hooks.state'],documents:{overview:'docs/overview.md'},tests:{}};
await writeFile(resolve(output,'extension.json'),JSON.stringify(manifest,null,2)+'\n');
console.log(JSON.stringify({packageId:manifest.id,contentIdentity:createHash('sha256').update(await readFile(resolve(output,'extension.json'))).digest('hex'),output}));
