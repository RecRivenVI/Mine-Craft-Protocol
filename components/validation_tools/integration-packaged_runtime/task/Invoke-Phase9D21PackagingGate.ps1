[CmdletBinding()]param([switch]$Offline,[switch]$SkipBuild)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
function Assert-True([bool]$Condition,[string]$Message){if(-not$Condition){throw "Packaging gate: $Message"}}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$targets=@('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')
$settings=Get-Content (Join-Path $root 'settings.gradle.kts') -Raw
Assert-True ($settings-match 'product\("runtime_safety"\)') 'shared safety component is not a product'
Assert-True ($settings-match 'product\("runtime_core"\)') 'shared Runtime core component is not a product'
if(-not$SkipBuild){
 $arguments=@('verifyRelease','--no-daemon');if($Offline){$arguments+='--offline'}
 & (Join-Path $root 'gradlew.bat') @arguments
 Assert-True ($LASTEXITCODE-eq0) 'five-target release verification failed'
}
$properties=Get-Content (Join-Path $root 'gradle.properties') -Raw
$modId=[regex]::Match($properties,'(?m)^mod_id=(.+)$').Groups[1].Value.Trim()
$version=[regex]::Match($properties,'(?m)^mod_version=(.+)$').Groups[1].Value.Trim()
$hashes=[ordered]@{}
foreach($target in $targets){
 $artifact=Join-Path $root "versions/$target/build/libs/$modId-$target-$version.jar"
 Assert-True (Test-Path $artifact) "$target final artifact missing"
 $zip=[IO.Compression.ZipFile]::OpenRead($artifact)
 try{
  $shared='io/github/recrivenvi/minecraftprotocol/safety/PersistentWriteSafetyFoundation.class'
  Assert-True (@($zip.Entries|Where-Object FullName -EQ $shared).Count-eq1) "$target must contain exactly one flattened safety class"
  foreach($coreClass in @('core/ProbeTransport','core/ProtocolState','core/providers/ProbeV2Providers','probe/api/AgentDataProviderV2')){
   Assert-True (@($zip.Entries|Where-Object FullName -EQ ('io/github/recrivenvi/minecraftprotocol/'+$coreClass+'.class')).Count-eq1) "$target core product class missing/duplicated: $coreClass"
  }
  Assert-True (@($zip.Entries|Where-Object {$_.FullName-match '^io/github/recrivenvi/minecraftprotocol/probe/runtime/(ProbeTransport|ProtocolState)\.class$|(?i)runtime[-_]core.*\.jar$'}).Count-eq0) "$target must not retain old core classes or embed a second core JAR"
  Assert-True (@($zip.Entries|Where-Object {$_.FullName-match '(?i)runtime[-_]safety.*\.jar$'}).Count-eq0) "$target must not duplicate safety in a nested JAR"
  foreach($license in Get-ChildItem (Join-Path $root 'licenses') -File){
   Assert-True ($null-ne$zip.GetEntry('META-INF/licenses/'+$license.Name)) "$target third-party license missing"
  }
  $targetFacts=Get-Content (Join-Path $root "versions/$target/target.properties") -Raw
  $httpVersion=[regex]::Match($targetFacts,'(?m)^netty_http_version=(.+)$').Groups[1].Value.Trim()
  $httpJars=@($zip.Entries|Where-Object FullName -Match '(?:^|/)netty-codec-http-[^/]+\.jar$')
  if($httpVersion){
   Assert-True ($httpJars.Count-eq1-and$httpJars[0].FullName-eq"META-INF/jarjar/netty-codec-http-$httpVersion.jar") "$target must embed one matching HTTP module"
   Assert-True ($null-ne$zip.GetEntry('META-INF/jarjar/metadata.json')) "$target Jar-in-Jar metadata missing"
   $buffer=[IO.MemoryStream]::new();$stream=$httpJars[0].Open();$stream.CopyTo($buffer);$stream.Dispose();$buffer.Position=0
   $inner=[IO.Compression.ZipArchive]::new($buffer,[IO.Compression.ZipArchiveMode]::Read)
   try{foreach($class in @('HttpMessage','FullHttpResponse')){Assert-True ($null-ne$inner.GetEntry("io/netty/handler/codec/http/$class.class")) "$target embedded HTTP class missing: $class"}}
   finally{$inner.Dispose();$buffer.Dispose()}
  }else{Assert-True ($httpJars.Count-eq0) "$target must use the game's HTTP library without embedding a conflicting copy"}
  if($target.EndsWith('-fabric')){
   $apiJars=@($zip.Entries|Where-Object FullName -Match '^META-INF/jars/fabric-api-[^/]+\.jar$')
   Assert-True ($apiJars.Count-eq1) "$target must include its required Fabric API for single-JAR installation"
   $reader=[IO.StreamReader]::new($zip.GetEntry('fabric.mod.json').Open())
   try{$metadata=$reader.ReadToEnd()|ConvertFrom-Json}finally{$reader.Dispose()}
   Assert-True ($apiJars[0].FullName-in@($metadata.jars.file)) "$target nested Fabric API is not registered with Loader"
  }
  if($target-eq'1.20.1-forge'){
   $reader=[IO.StreamReader]::new($zip.GetEntry("$modId.refmap.json").Open())
   try{$refmap=$reader.ReadToEnd()|ConvertFrom-Json}finally{$reader.Dispose()}
   $effect=$refmap.mappings.'io/github/recrivenvi/minecraftprotocol/probe/mixin/EffectIconRenderMixin'
   Assert-True ($effect.'lambda$renderEffects$0'-match'^Lnet/minecraft/client/gui/Gui;m_\d+_\(') 'Forge effect lambda must have production SRG mapping'
  }
 }finally{$zip.Dispose()}
 $hashes[$target]=(Get-FileHash $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
}
[pscustomobject]@{Result='PASS';Targets=5;FinalArtifactEmbedding='FLATTENED_PRODUCT_COMPONENT';DuplicateSharedClass='NONE';ArtifactSha256=$hashes;Runtime='NOT_RUN';PersistentWrites=0}
