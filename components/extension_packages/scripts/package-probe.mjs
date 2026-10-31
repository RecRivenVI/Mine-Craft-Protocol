import {readFile, mkdir, writeFile, copyFile} from 'node:fs/promises';
import {createHash} from 'node:crypto';
import {resolve} from 'node:path';
const repo=resolve('../..'), output=resolve('build/packages/platform_probe');
const code=process.argv[2];
if(!code)throw new Error('Gradle supplies the compiled standalone SDK package JAR');
await mkdir(resolve(output,'code'),{recursive:true});
await mkdir(resolve(output,'docs'),{recursive:true});
await copyFile(code,resolve(output,'code/adapter.jar'));
await writeFile(resolve(output,'docs/overview.md'),'# Platform probe\n\nOwner-thread sampling of a managed DEBUG grant. No world mutation or user input. Runtime read and typed Debug authorize separately.\n');
const variants={};
for(const [,target] of (await readFile(resolve(repo,'settings.gradle.kts'),'utf8')).matchAll(/register\("([0-9.]+-(?:forge|neoforge|fabric))"\)/g)){
  const facts=await readFile(resolve(repo,`versions/${target}/target.properties`),'utf8');
  const javaVersion=Number(facts.match(/^java_version=(\d+)$/m)?.[1]);
  if(!Number.isInteger(javaVersion))throw new Error(`Missing authoritative Java fact for ${target}`);
  variants[target]={javaVersion,code:['code/adapter.jar'],entrypoints:{common:['io.github.recrivenvi.extensions.platformprobe.Entry']},mixins:[]};
}
const files={};
for(const name of ['code/adapter.jar','docs/overview.md'])files[name]=createHash('sha256').update(await readFile(resolve(output,name))).digest('hex');
const manifest={formatVersion:1,sdkApi:1,id:'platform_probe',version:'1.0.0',javaNamespace:'io.github.recrivenvi.extensions.platformprobe',
 files,variants,dependencies:{mods:{},packages:{}},capabilities:['platform_probe:sampling'],
 requestedScopes:['debug.world.read','debug.provider'],exports:[],documents:{overview:'docs/overview.md'},tests:{}};
await writeFile(resolve(output,'extension.json'),JSON.stringify(manifest,null,2)+'\n');
console.log(JSON.stringify({packageId:manifest.id,contentIdentity:createHash('sha256').update(await readFile(resolve(output,'extension.json'))).digest('hex'),output}));
