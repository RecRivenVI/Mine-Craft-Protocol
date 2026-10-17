import { readFile, readdir, writeFile, stat } from 'node:fs/promises';
import { resolve, relative, dirname } from 'node:path';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import {coreShowcaseProof,commandConfigurationProof,consumerPriorityProof,consumerFeedbackProof,uiRegressionProof} from './alpha-evidence.mjs';
import {candidateSource} from './alpha-candidate.mjs';
import {collectInstalledProof} from '../../integration-packaged_runtime/task/installed-evidence.mjs';

const repo=resolve(import.meta.dirname,'../../../..');
const scenario=process.argv.find(value=>value.startsWith('--scenario='))?.slice('--scenario='.length)??'full';
const validation=process.argv.find(value=>value.startsWith('--validation='))?.slice('--validation='.length)??(scenario==='core-showcase'?'feature-control_ui_showcase':'conformance-world_read');
if(!['conformance-world_read','migration-runtime_core','feature-control_ui_showcase'].includes(validation))throw new Error('Unsupported Native smoke validation: '+validation);
if(!['full','input-preconditions','player-command','command-configuration','core-showcase','packaged-runtime'].includes(scenario)||(scenario==='core-showcase'?validation!=='feature-control_ui_showcase':validation==='feature-control_ui_showcase'||!['full','packaged-runtime'].includes(scenario)&&validation!=='conformance-world_read'))throw new Error('Unsupported alpha Native smoke scope');
const facts=Object.fromEntries((await readFile(resolve(repo,'gradle.properties'),'utf8')).split(/\r?\n/).filter(line=>line.startsWith('mod_')).map(line=>{const index=line.indexOf('=');return [line.slice(0,index),line.slice(index+1)];}));
const targets=[...(await readFile(resolve(repo,'settings.gradle.kts'),'utf8')).matchAll(/register\("([^"\r\n]+)"\)/g)].map(match=>match[1]);
const digest=bytes=>createHash('sha256').update(bytes).digest('hex');
const artifacts=[];
for(const target of targets){
  const name=`${facts.mod_id}-${target}-${facts.mod_version}.jar`;
  const collected=resolve(repo,'build/release',facts.mod_version,name);
  const native=resolve(repo,'versions',target,'build/libs',name);
  const bytes=await readFile(collected);
  if(digest(bytes)!==digest(await readFile(native)))throw new Error('Collected/native artifact mismatch: '+target);
  artifacts.push({target,path:relative(repo,collected).replaceAll('\\','/'),fileName:name,sha256:digest(bytes),bytes:bytes.length,builtAt:(await stat(native)).mtime.toISOString()});
}
const sourceHashes=await candidateSource(repo,targets);
const packagedRuntime=await collectInstalledProof(repo,artifacts,digest(JSON.stringify(sourceHashes)));
for(const proof of packagedRuntime){
  const bytes=await readFile(resolve(repo,proof.result));
  if(digest(bytes)!==proof.resultSha256)throw new Error('Installed proof changed during alpha preparation: '+proof.target);
  const result=JSON.parse(bytes.toString('utf8'));
  for(const key of ['testedAt','loaderVersion','javaVersion','freshGameDirectory','externalModCount','developmentClasspath','exitCode','maximumHeapBytes','noCrashReports','nettyHttpOrigin','transportOrigin','quitHttpStatus','quitError','quitTransportError','toolHashes'])if(result[key]!==undefined)proof[key]=result[key];
}
const head=execFileSync('git',['rev-parse','HEAD'],{cwd:repo,encoding:'utf8'}).trim();
const dirty=execFileSync('git',['status','--porcelain=v1'],{cwd:repo,encoding:'utf8'}).trim().length>0;
const nativeSmoke=[];
const uiRegression=[];
for(const target of targets) {
  if(scenario==='packaged-runtime'){
    const proof=packagedRuntime.find(value=>value.target===target);
    nativeSmoke.push({target,result:proof.result,scenario,fullSmoke:false,checks:4,mcpChecks:0,mcpScope:'not_performed_by_installed_smoke',evidence:'installed_loader_release_jar',sourceMatchesCurrent:true,cleanShutdown:true,fixturePolicy:'fresh_install_no_world'});
    continue;
  }
  const path=`validations/${validation}/result/${target}${['full','core-showcase'].includes(scenario)?'':'-'+scenario}.json`;
  const result=JSON.parse(await readFile(resolve(repo,path),'utf8'));
  const showcase=scenario==='core-showcase'?coreShowcaseProof(result,target):null;
  if(scenario==='command-configuration')commandConfigurationProof(result,target);
  let matches=true;
  for(const [source,hash] of Object.entries(result.sourceHashes??{}))if(digest(await readFile(resolve(repo,source)))!==hash)matches=false;
  if((showcase?result.result!=='PASS':result.status!=='PASS'||!result.cleanShutdown)||result.sourceChangedDuringRun||!matches)throw new Error('Missing/current candidate Native smoke: '+target);
  if(showcase){nativeSmoke.push({target,result:path,scenario,fullSmoke:false,...showcase,sourceMatchesCurrent:true});continue;}
  if(scenario==='full'&&(result.fullSmoke===false||(result.scenario!==undefined&&result.scenario!=='full')))throw new Error('Targeted result is not full Native smoke: '+target);
  const feedbackProof=scenario==='full'?consumerFeedbackProof(result,target):null;
  if(scenario==='full') {
    consumerPriorityProof(result,target);
    const uiPath=`validations/conformance-vanilla_ui/result/${target}.json`,bytes=await readFile(resolve(repo,uiPath)),ui=JSON.parse(bytes.toString('utf8'));
    const proof=uiRegressionProof(ui,target);
    if(Object.keys(ui.sourceHashes??{}).length<100)throw new Error('UI regression must bind production sources and tools: '+target);
    for(const [source,hash]of Object.entries(ui.sourceHashes))if(digest(await readFile(resolve(repo,source)))!==hash)throw new Error('Stale UI regression: '+target+' '+source);
    uiRegression.push({target,...proof,result:uiPath,resultSha256:digest(bytes)});
    const item=result.actionSamples?.find(sample=>sample.case==='same_item_count_metadata_revision');
    if(!item||!/^([0-9a-f]{64})$/.test(item.beforeRevision)||!/^([0-9a-f]{64})$/.test(item.afterRevision)||item.beforeRevision===item.afterRevision||item.sameMenuAndSlot!==true||item.menuRevisionChanged!==true||item.userPrivateFingerprintLeaked!==false)
      throw new Error('Missing native same-item/count metadata revision proof: '+target);
  }
  const scope={ 'player-command':{checks:11,mcp:1,consumer:'player_command_chat'},'input-preconditions':{checks:7,mcp:2,consumer:'ui_condition_contract'},'command-configuration':{checks:16,mcp:6,consumer:'native_command_configuration'} }[scenario];
  if(scenario!=='full'&&(!scope||result.scenario!==scenario||result.fullSmoke!==false||result.fixturePolicy!=='immutable_baseline_restored'||result.checks.length!==scope.checks||result.checks.some(value=>value.status!=='PASS')||result.consumerScope!==scope.consumer||result.consumerChecks?.length!==scope.mcp||result.consumerChecks.some(value=>value.status!=='PASS')))throw new Error('Invalid targeted Native/MCP smoke scope: '+target);
  if(result.fixturePolicy==='immutable_baseline_restored'&&(!/^[0-9a-f]{64}$/.test(result.fixtureInput?.baselineSha256??'')||result.fixtureInput.restoredSha256!==result.fixtureInput.baselineSha256||result.fixtureInput.target!==target||result.fixtureInput.role!=='client'||result.fixtureInput.targetFactsSha256!==result.sourceHashes[`versions/${target}/target.properties`]||result.fixtureInput.credentialsCopied!==false))throw new Error('Invalid immutable fixture evidence: '+target);
  nativeSmoke.push({target,result:path,scenario,fullSmoke:scenario==='full',checks:result.checks.length,mcpChecks:(result.consumerChecks?.length??0)+(scenario==='full'?1+feedbackProof.realMcpChecks:0),mcpScope:result.consumerScope??(scenario==='full'?'world_read_provider_and_consumer_feedback':'none'),evidence:result.evidence,sourceMatchesCurrent:true,cleanShutdown:result.cleanShutdown,fixturePolicy:result.fixturePolicy,...(scenario==='full'?{consumerPriority:consumerPriorityProof(result,target),consumerFeedback:feedbackProof}:{}),...(result.fixtureInput?{fixtureInput:result.fixtureInput}:{})});
}
const contract=JSON.parse(await readFile(resolve(repo,'components/protocol_schema/src/main/openapi/minecraft-control-v0.json'),'utf8')).components.schemas.WorldReadContract.properties;
const report={type:'internal_alpha_build',preparedAt:new Date().toISOString(),commit:head,dirty,sourceFingerprint:digest(JSON.stringify(sourceHashes)),sourceHashes,modVersion:facts.mod_version,worldStage:contract.implementationStage.enum[0],fullWorldMilestone:contract.milestoneComplete.enum[0],readConsumerIsolationComplete:contract.readConsumerIsolationComplete.enum[0],consumerIsolationComplete:contract.consumerIsolationComplete.enum[0],artifactCheck:'collected_matches_native',runtimeAcceptance:'selected development scenario plus separately bound installed title/READ smoke; not full packaged gameplay or human acceptance',nativeSmokeScope:scenario,fullNativeSmoke:scenario==='full',nativeSmoke,artifacts};
report.packagedRuntimeVerified=true;report.packagedRuntime=packagedRuntime;
if(scenario==='full')report.uiRegression=uiRegression;
if(scenario==='packaged-runtime')report.runtimeAcceptance='installed Loader / single release JAR / title + USER READ / clean shutdown; not full UI/world or human acceptance';
const output=resolve(repo,'build/alpha-manifest.json');
const alphaRoot=resolve(process.env.MCP_ALPHA_ROOT??'D:/Workspaces/Artifacts/MineCraftProtocol/Alpha');
const dateParts=Object.fromEntries(new Intl.DateTimeFormat('en-GB',{timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hourCycle:'h23'}).formatToParts(new Date(report.preparedAt)).map(part=>[part.type,part.value]));
const snapshotName=`${dateParts.year}${dateParts.month}${dateParts.day}-${dateParts.hour}${dateParts.minute}-${report.sourceFingerprint.slice(0,8)}`;
const snapshotDirectory=resolve(alphaRoot,snapshotName);
report.distribution={layout:'flat_immutable_snapshot',directory:snapshotDirectory.replaceAll('\\','/'),timeZone:'Asia/Shanghai',artifactPaths:'fileName relative to snapshot; path records repository provenance',keepNewest:3,inUse:'explicit nonexpiring consumer pins outside immutable snapshots'};
const guideSource='documents/development/workflow-alpha_agent.md';
const guideOutput=resolve(repo,'build/AGENT_GUIDE.md');
const guideBytes=await readFile(resolve(repo,guideSource));
let guide=guideBytes.toString('utf8').replace(/\r\n/g,'\n').replace(/\]\(([^)]+)\)/g,(original,link)=>{
  if(/^(?:[a-z][a-z0-9+.-]*:|#|\/)/i.test(link))return original;
  const [path,anchor]=link.split('#');
  return ']('+resolve(repo,dirname(guideSource),path).replaceAll('\\','/')+(anchor?'#'+anchor:'')+')';
});
const rows=[];
for(const artifact of artifacts){
  const target=artifact.target;
  const properties=Object.fromEntries((await readFile(resolve(repo,'versions',target,'target.properties'),'utf8')).split(/\r?\n/).filter(line=>line.includes('=')&&!line.startsWith('#')).map(line=>{const i=line.indexOf('=');return[line.slice(0,i).trim(),line.slice(i+1).trim()];}));
  const fabric=target.endsWith('-fabric');
  const runtime=target.endsWith('-forge')?'ForgeProbeRuntime':fabric?'FabricProbeRuntime':'NeoForgeProbeRuntime';
  const source=await readFile(resolve(repo,'versions',target,'src',fabric?'client':'main','java/io/github/recrivenvi/minecraftprotocol/probe/runtime',runtime+'.java'),'utf8');
  if(!source.replace(/\s/g,'').includes('ProbeTransport.forInstance(this,minecraft.gameDirectory.toPath(),"'+target+'")'))throw new Error('Target does not use instance discovery: '+target);
  const endpointSource=await readFile(resolve(repo,'components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core/RuntimeEndpoint.java'),'utf8');
  const port=endpointSource.match(/DEFAULT_PORT\s*=\s*(\d+)/)?.[1];
  if(port!=='0')throw new Error('Runtime automatic port default must be authoritative: '+target);
  rows.push('| '+target+' | '+properties.loader_version+' | '+properties.java_version+' | '+port+' | ['+artifact.fileName+']('+resolve(snapshotDirectory,artifact.fileName).replaceAll('\\','/')+') |');
}
const snapshot='## 本次 alpha 构建快照\n\n生成时间：'+report.preparedAt+'。产品版本：'+facts.mod_version+'。\n\n'+
  '- 固定分发快照：`'+snapshotDirectory.replaceAll('\\','/')+'`。其他线程应登记使用并复制此快照，不要引用 build/。\n- HEAD 基线：`'+head+'`。\n- 工作树包含未提交改动：`'+dirty+'`。\n- 产品源码指纹：`'+report.sourceFingerprint+'`。\n- 世界读取消费者隔离：`'+report.readConsumerIsolationComplete+'`；完整世界里程碑：`'+report.fullWorldMilestone+'`。\n- [源码、制品 SHA-256 与证据清单](alpha-manifest.json)。\n\n'+
  '| Target | 编译 Loader | Java | Runtime 默认端口 | 最终 JAR |\n| --- | --- | --- | --- | --- |\n'+rows.join('\n')+'\n\n'+
  '默认值 0 表示由操作系统分配空闲端口，不是连接地址。按实例目录的 endpoint.json 连接并锁定 runtimeInstanceId；不扫描端口。五端均先通过独立正常Loader、单个最终JAR安装检查；以下范围为`'+scenario+'`，全套世界smoke为`'+report.fullNativeSmoke+'`，不替代完整领域或人工验收。\n\n'+
  '| Target | Native 用例数 | MCP 专项数 | 当前源码匹配 | 正常退出 |\n| --- | --- | --- | --- | --- |\n'+nativeSmoke.map(r=>'| '+r.target+' | '+r.checks+' | '+r.mcpChecks+' | '+r.sourceMatchesCurrent+' | '+r.cleanShutdown+' |').join('\n')+'\n';
const introductionEnd=guide.indexOf('\n\n',guide.indexOf('\n\n')+2);
if(introductionEnd<0)throw new Error('Agent guide must start with a title and introduction');
guide=guide.slice(0,introductionEnd)+'\n\n'+snapshot.replace(/\n+(?=- )/g,'\n\n').trimEnd()+guide.slice(introductionEnd);
report.agentGuide={path:relative(repo,guideOutput).replaceAll('\\','/'),fileName:'AGENT_GUIDE.md',sha256:digest(Buffer.from(guide)),source:guideSource,sourceSha256:digest(guideBytes)};
await writeFile(guideOutput,guide);
await writeFile(output,JSON.stringify(report,null,2)+'\n');
console.log('Prepared internal alpha manifest: '+output+' ('+artifacts.length+' JARs)');
console.log('Prepared Agent guide: '+guideOutput);
execFileSync('pwsh',['-NoProfile','-File',resolve(import.meta.dirname,'Publish-AlphaSnapshot.ps1'),'-OutputRoot',alphaRoot,'-RepositoryRoot',repo],{cwd:repo,stdio:'inherit'});
