import assert from 'node:assert/strict';
import {readFile,readdir,writeFile,stat,mkdir} from 'node:fs/promises';
import {resolve,relative,dirname} from 'node:path';
import {execFileSync} from 'node:child_process';
import {candidateSource,extensionPackageSource,sha256} from './alpha-candidate.mjs';
import {collectInstalledProof} from '../../integration-packaged_runtime/task/installed-evidence.mjs';
import {EXTENSION_PROFILE,collectExtensionProof,verifyExtensionPublication,currentHashes} from './extension-proof.mjs';

const repo=resolve(import.meta.dirname,'../../../..');
const settings=await readFile(resolve(repo,'settings.gradle.kts'),'utf8');
const targets=[...settings.matchAll(/register\("([0-9.]+-(?:forge|neoforge|fabric))"\)/g)].map(match=>match[1]);
assert.equal(targets.length,5);
const facts=Object.fromEntries((await readFile(resolve(repo,'gradle.properties'),'utf8')).split(/\r?\n/).filter(line=>line.startsWith('mod_')).map(line=>{const at=line.indexOf('=');return[line.slice(0,at),line.slice(at+1)];}));
const sourceHashes=await candidateSource(repo,targets),sourceFingerprint=sha256(JSON.stringify(sourceHashes));
const artifacts=[];
for(const target of targets){
  const fileName=`${facts.mod_id}-${target}-${facts.mod_version}.jar`,path=`build/release/${facts.mod_version}/${fileName}`;
  const bytes=await readFile(resolve(repo,path));
  assert.equal(sha256(bytes),sha256(await readFile(resolve(repo,'versions',target,'build/libs',fileName))));
  artifacts.push({target,fileName,path,sha256:sha256(bytes),bytes:bytes.length,builtAt:(await stat(resolve(repo,path))).mtime.toISOString()});
}
// All proofs are collected before writing a manifest or changing latest.
const extensionPlatform=await collectExtensionProof(repo,artifacts);
const packagedRuntime=await collectInstalledProof(repo,artifacts,sourceFingerprint,{requireEnteredWorld:true});
const additionalArtifacts=[];
async function add(path,fileName){const bytes=await readFile(resolve(repo,path));additionalArtifacts.push({path,fileName,sha256:sha256(bytes),bytes:bytes.length});}
async function directory(path,prefix,include=()=>true){
  for(const entry of (await readdir(resolve(repo,path),{recursive:true,withFileTypes:true})).sort((a,b)=>resolve(a.parentPath,a.name).localeCompare(resolve(b.parentPath,b.name)))){
    assert(!entry.isSymbolicLink(),'Distribution cannot contain links');
    const suffix=relative(resolve(repo,path),resolve(entry.parentPath,entry.name)).replaceAll('\\','/');
    if(entry.isFile()&&!suffix.startsWith('test/')&&include(suffix))await add(path+'/'+suffix,prefix+'/'+suffix);
  }
}
const sdk=(await readdir(resolve(repo,'components/runtime_core/build/libs'))).filter(name=>name.endsWith('-extension-sdk.jar'));assert.equal(sdk.length,1);
await add('components/runtime_core/build/libs/'+sdk[0],'extensions/sdk/'+sdk[0]);
const ids=JSON.parse(await readFile(resolve(repo,'components/extensionpacks/extensionpacks/index.json'),'utf8'));
for(const id of ids)await directory('components/extensionpacks/build/extensionpacks/'+id,'extensions/extensionpacks/'+id);
for(const name of ['procedure-author_extension','build-extensionpacks','testing-extension_platform'])await add('documents/development/'+name+'.md','extensions/documents/development/'+name+'.md');
await add('documents/reference/api-extension_platform.md','extensions/documents/reference/api-extension_platform.md');
await directory('components/extensionpacks/extensionpacks/computercraft/java','extensions/components/extensionpacks/extensionpacks/computercraft/java');
for(const file of additionalArtifacts.filter(item=>item.fileName.startsWith('extensions/documents/'))){
  for(const match of (await readFile(resolve(repo,file.path),'utf8')).matchAll(/\]\(([^)]+)\)/g)){
    if(/^(?:[a-z][a-z0-9+.-]*:|#|\/)/i.test(match[1]))continue;
    const linked=relative(repo,resolve(repo,dirname(file.fileName),match[1].split('#')[0])).replaceAll('\\','/');
    assert(additionalArtifacts.some(item=>item.fileName===linked),'Missing distributed author reference: '+linked);
  }
}
const companionManifest=JSON.parse(await readFile(resolve(repo,'components/companion/package.json'),'utf8'));
companionManifest.scripts={start:'node ./dist/index.js'};
const distribution='build/companion-distribution';await mkdir(resolve(repo,distribution),{recursive:true});
await writeFile(resolve(repo,distribution,'package.json'),JSON.stringify(companionManifest,null,2)+'\n');
await add(distribution+'/package.json','companion/package.json');
await add('components/companion/package-lock.json','companion/package-lock.json');
await directory('components/companion/dist','companion/dist',name=>!name.startsWith('ui-console')&&!name.startsWith('live-conformance'));
const consumerSourceHashes={};
for(const root of ['components/companion/src','components/companion/scripts','components/companion/web'])for(const entry of await readdir(resolve(repo,root),{recursive:true,withFileTypes:true}))if(entry.isFile()){
  const path=relative(repo,resolve(entry.parentPath,entry.name)).replaceAll('\\','/');consumerSourceHashes[path]=sha256(await readFile(resolve(repo,path)));
}
const extensionSourceHashes=await extensionPackageSource(repo,['platform_probe',...ids]);
const head=execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).trim();
const preparedAt=new Date().toISOString();
const parts=Object.fromEntries(new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date(preparedAt)).map(part=>[part.type,part.value]));
const name=`${parts.year}${parts.month}${parts.day}-${parts.hour}${parts.minute}-${sourceFingerprint.slice(0,8)}`;
const alphaRoot=resolve(process.env.MCP_ALPHA_ROOT??'D:/Workspaces/Artifacts/MineCraftProtocol/Alpha');
const snapshotDirectory=resolve(alphaRoot,name);
const guideSource='documents/development/workflow-alpha_agent.md',guideBytes=await readFile(resolve(repo,guideSource));
let guide=guideBytes.toString('utf8').replace(/\r\n/g,'\n').replace(/\]\(([^)]+)\)/g,(original,link)=>{
  if(/^(?:[a-z][a-z0-9+.-]*:|#|\/)/i.test(link))return original;
  const [path,anchor]=link.split('#');return ']('+resolve(repo,dirname(guideSource),path).replaceAll('\\','/')+(anchor?'#'+anchor:'')+')';
});
const insert=guide.indexOf('\n\n');assert(insert>0);
guide=guide.slice(0,insert)+`\n\n本批扩展平台固定快照：${name}；源码提交：${head}。其他线程应复制此不可变目录，不引用build。\n\n五端本体、SDK、七包和Companion同批交付；companion目录先执行npm ci --omit=dev，再运行dist/index.js。extensions/extensionpacks中的内容不是Loader模组，不放进mods：由真人在原生模组配置页初次启用，批准绑定内容哈希，重启后生效。作者规则可通过离线MCP资源查阅。\n\n当前证明为五端平台/发行安装/独服、五端世界full与输入上下文/45秒连续正确性、参考端原版UI all及其余四端pointer/special-menus/ui-navigation，加上有上游包的具名有限业务。不是任意第三方功能全覆盖；CC NeoForge26.3上游启动失败、LittleTiles运动实体再次关闭等限制见alpha-manifest.json的extensionPlatform.limitations。Forge AE2首次只读采样曾超预算，同批重测通过，根因未确认；原失败保留在状态页。真人协作/IME/视觉验收仍未执行，不声明两个领域整体接受或对外发布。Persistent Write=0，未测性能。\n\n[本批完整清单](alpha-manifest.json)。`+guide.slice(insert);
const report={type:'internal_alpha_build',preparedAt,commit:head,dirty:execFileSync('git',['status','--porcelain=v1'],{cwd:repo,encoding:'utf8'}).trim().length>0,modVersion:facts.mod_version,sourceHashes,sourceFingerprint,
  artifacts,additionalArtifacts,extensionSourceHashes,consumerSourceHashes,publicationProfile:EXTENSION_PROFILE,extensionPlatform,packagedRuntimeVerified:true,packagedRuntime,
  nativeSmoke:extensionPlatform.proofs.filter(item=>item.result.endsWith('-client.json')&&!item.result.includes('-installed-')).map(item=>({...item,sourceMatchesCurrent:true,cleanShutdown:true})),
  nativeSmokeScope:'extension_platform_and_affected_core',fullNativeSmoke:false,fullMilestonesComplete:false,humanAcceptance:'NOT_PERFORMED',performanceAcceptance:{status:'NOT_PERFORMED'},persistentWriteCalls:0,
  distribution:{layout:'flat_immutable_snapshot',directory:snapshotDirectory.replaceAll('\\','/'),timeZone:'Asia/Shanghai',keepNewest:3,inUse:'explicit consumer pins; previous snapshots retained'},
  agentGuide:{path:'build/AGENT_GUIDE.md',fileName:'AGENT_GUIDE.md',sha256:sha256(guide),source:guideSource,sourceSha256:sha256(guideBytes)}};
await currentHashes(repo,extensionSourceHashes);await currentHashes(repo,consumerSourceHashes);await verifyExtensionPublication(report,repo);
await writeFile(resolve(repo,'build/AGENT_GUIDE.md'),guide);await writeFile(resolve(repo,'build/alpha-manifest.json'),JSON.stringify(report,null,2)+'\n');
execFileSync('pwsh',['-NoProfile','-File',resolve(import.meta.dirname,'Publish-AlphaSnapshot.ps1'),'-OutputRoot',alphaRoot,'-RepositoryRoot',repo,'-KeepExistingSnapshots'],{cwd:repo,stdio:'inherit'});
