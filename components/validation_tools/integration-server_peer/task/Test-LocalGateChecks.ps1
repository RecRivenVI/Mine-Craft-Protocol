[CmdletBinding()]param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$fixture=Join-Path $root ('build/validation-fixtures/local-gates-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($fixture)
$targets=@('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')
$gatePaths=@('components/validation_tools/integration-server_peer/task/Invoke-ServerPeerLocalGate.ps1','components/validation_tools/compatibility-standard_gui/task/Invoke-StandardGuiLocalGate.ps1')
function Copy-FixtureFile([string]$Relative){
 $dest=Join-Path $fixture $Relative;[void][IO.Directory]::CreateDirectory((Split-Path $dest))
 Copy-Item -LiteralPath (Join-Path $root $Relative) -Destination $dest
}
foreach($p in @('gradle.properties','components/protocol_schema/src/main/openapi/minecraft-control-v0.json')+$gatePaths+@('components/validation_tools/integration-server_peer/task/Assert-ProductArtifact.ps1','components/validation_tools/compatibility-standard_gui/task/Invoke-Phase7HookCompatibilityGate.ps1','components/validation_tools/integration-server_peer/task/Invoke-Phase6PeerConformance.ps1','components/validation_tools/integration-server_peer/task/Invoke-Phase6DedicatedPeerConformance.ps1')){Copy-FixtureFile $p}
foreach($f in Get-ChildItem (Join-Path $root 'components/runtime_core/src/main') -Recurse -File){Copy-FixtureFile ([IO.Path]::GetRelativePath($root,$f.FullName))}
$facts=Get-Content (Join-Path $root 'gradle.properties') -Raw|ConvertFrom-StringData
function Get-JarName([string]$Target){return $facts.mod_id+'-'+$Target+'-'+$facts.mod_version+'.jar'}
foreach($target in $targets){
 $jarName=Get-JarName $target
 Copy-FixtureFile "versions/$target/build.gradle.kts"
 foreach($f in Get-ChildItem (Join-Path $root "versions/$target/src") -Recurse -File){Copy-FixtureFile ([IO.Path]::GetRelativePath($root,$f.FullName))}
 Copy-FixtureFile "versions/$target/build/libs/$jarName"
}
foreach($area in @('java','typescript')){foreach($f in Get-ChildItem (Join-Path $root "components/protocol_schema/build/generated/$area") -Recurse -File){Copy-FixtureFile ([IO.Path]::GetRelativePath($root,$f.FullName))}}
# SkipBuild must not invoke Gradle. This fixture has no runnable build or game entry.
[IO.File]::WriteAllText((Join-Path $fixture 'gradlew.bat'),"@echo off`r`nexit /b 91`r`n")
$results=[Collections.Generic.List[object]]::new()
function Run-Gate([string]$Relative,[bool]$Pass,[string]$Case,[string]$ExpectedError=''){
 $failure=$null;$value=$null
 try{$value=& (Join-Path $fixture $Relative) -SkipBuild}catch{$failure=$_.Exception.Message}
 if($Pass){
  if($failure){throw "$Case unexpected failure: $failure"}
  if($value.Result-ne'PASS'-or$value.Protocol-ne'0.0.1-ui-m1'-or@($value.Targets).Count-ne5-or(($value.Targets.Target|Sort-Object)-join',')-ne(($targets|Sort-Object)-join',')-or$value.JavaModels-le0-or$value.TypeScriptFiles-le0){throw "$Case vacuous/incomplete PASS"}
  if(@($value.Targets|Where-Object {$_.Artifact-ne(Get-JarName $_.Target)-or$_.Bytes-le0}).Count){throw "$Case wrong artifact"}
 }else{if(-not$failure-or($ExpectedError-and$failure-notlike('*'+$ExpectedError+'*'))){throw "$Case did not fail for expected reason: $failure"}}
 $results.Add([pscustomobject]@{Gate=(Split-Path $Relative -Leaf);Case=$Case;Expected=if($Pass){'PASS'}else{'FAIL'};Outcome=if($failure){'FAIL'}else{'PASS'};Check='PASS';Error=$failure})
}
function With-Missing([string]$Relative,[scriptblock]$Check){
 $path=Join-Path $fixture $Relative;$held=Join-Path $fixture ('held-'+[guid]::NewGuid().ToString('N'))
 Move-Item -LiteralPath $path -Destination $held
 try{& $Check}finally{Move-Item -LiteralPath $held -Destination $path}
}
function With-Text([string]$Relative,[scriptblock]$Transform,[scriptblock]$Check){
 $path=Join-Path $fixture $Relative;$original=[IO.File]::ReadAllBytes($path);$text=[IO.File]::ReadAllText($path)
 try{$changed=& $Transform $text;[IO.File]::WriteAllText($path,$changed);& $Check}finally{[IO.File]::WriteAllBytes($path,$original)}
}
foreach($gate in $gatePaths){
 Run-Gate $gate $true 'current-products-five-targets'
 foreach($target in $targets){
  $source=if($target-eq'26.3-fabric'){'client'}else{'main'}
  $file=if($gate.Contains('ServerPeer')){"versions/$target/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/peer/DedicatedPeerServer.java"}else{"versions/$target/src/$source/java/io/github/recrivenvi/minecraftprotocol/probe/gui/AutomationProbeScreen.java"}
  With-Missing $file {Run-Gate $gate $false "missing-source-$target" $target}
 }
 $schema='components/protocol_schema/src/main/openapi/minecraft-control-v0.json'
 With-Text $schema {param($s)$s.Replace('0.0.1-ui-m1','0.0.1-invalid')} {Run-Gate $gate $false 'wrong-contract-version' 'Schema must identify'}
 $route=if($gate.Contains('ServerPeer')){'/v0/server/peer'}else{'/v0/diagnostics/hooks'}
 With-Text $schema {param($s)$j=$s|ConvertFrom-Json -AsHashtable;$j.paths.Remove($route);$j|ConvertTo-Json -Depth 100} {Run-Gate $gate $false 'missing-contract-route' 'path must exist'}
 if($gate.Contains('ServerPeer')){
  With-Text 'gradle.properties' {param($s)$s.Replace('mod_client_side=optional','mod_client_side=true')} {Run-Gate $gate $false 'mandatory-client-regression' 'optional contract'}
  With-Text 'gradle.properties' {param($s)$s.Replace('mod_server_side=optional','mod_server_side=true')} {Run-Gate $gate $false 'mandatory-server-regression' 'optional contract'}
  With-Text 'versions/26.3-neoforge/build.gradle.kts' {param($s)$s.Replace('net.neoforged.moddev','removedLoader')} {Run-Gate $gate $false 'wrong-source-contract' 'native Loader launch model'}
 }else{
  With-Text 'versions/26.3-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/gui/AutomationProbeScreen.java' {param($s)$s.Replace('Compatibility Text','removed text')} {Run-Gate $gate $false 'wrong-source-contract' 'Compatibility Text'}
  With-Text 'versions/26.3-neoforge/src/main/resources/minecraft_protocol_probe.mixins.json' {param($s)$j=$s|ConvertFrom-Json -AsHashtable;$j.required=$false;$j|ConvertTo-Json -Depth 20} {Run-Gate $gate $false 'wrong-hook-contract' 'required Hook'}
  With-Text 'versions/26.3-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/mixin/WindowMixin.java' {param($s)$s.Replace('ordinal = 0','ordinal = 1')} {Run-Gate $gate $false 'wrong-cursor-argument-guard' 'exact host cursor argument guard'}
  With-Text 'versions/26.3-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/mixin/WindowMixin.java' {param($s)$s+"`n@ModifyVariable(method = `"other`", at = @At(`"HEAD`"))`n"} {Run-Gate $gate $false 'unreviewed-variable-replacement' 'unreviewed replacement mechanisms'}
  With-Text 'versions/26.3-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/NeoForgeProbeRuntime.java' {param($s)$s.Replace('MIXIN_MODIFY_VARIABLE','MIXIN_REDIRECT')} {Run-Gate $gate $false 'mislabelled-cursor-argument-hook' 'must disclose its host cursor argument replacement'}
 }
 foreach($target in $targets){$jarName=Get-JarName $target;With-Missing "versions/$target/build/libs/$jarName" {Run-Gate $gate $false "missing-artifact-$target" 'artifact must exist'}}
 $jarName=Get-JarName '26.3-neoforge'
 $library=Join-Path $fixture 'versions/26.3-neoforge/build/libs';$correct=Join-Path $library $jarName
 $wrong=Join-Path $library ($facts.mod_id+'-0.0.1-phase7.jar')
 if(-not(Test-Path $wrong)){Copy-Item -LiteralPath $correct -Destination $wrong}
 (Get-Item $wrong).LastWriteTimeUtc=[DateTime]::UtcNow.AddHours(1)
 Run-Gate $gate $true 'newer-wrong-version-ignored'
 With-Missing "versions/26.3-neoforge/build/libs/$jarName" {Run-Gate $gate $false 'wrong-version-only' 'artifact must exist'}
 With-Text 'gradle.properties' {param($s)$s.Replace('mod_version='+$facts.mod_version,'mod_version=0.0.1-not-built')} {Run-Gate $gate $false 'project-version-changed-without-artifact' 'artifact must exist'}
 foreach($fault in @('version','identity')){
  $original=[IO.File]::ReadAllBytes($correct)
  $id=if($fault-eq'identity'){'wrong_mod'}else{$facts.mod_id}
  $version=if($fault-eq'version'){'0.0.1-phase7'}else{$facts.mod_version}
  $memory=[IO.MemoryStream]::new()
  try{
   $zip=[IO.Compression.ZipArchive]::new($memory,[IO.Compression.ZipArchiveMode]::Create,$true)
   $writer=[IO.StreamWriter]::new($zip.CreateEntry('META-INF/neoforge.mods.toml').Open())
   $writer.Write("[[mods]]`nmodId=`"$id`"`nversion=`"$version`"`n");$writer.Dispose();$zip.Dispose()
   [IO.File]::WriteAllBytes($correct,$memory.ToArray())
   Run-Gate $gate $false "renamed-stale-jar-$fault" 'metadata identity/version mismatch'
  }finally{$memory.Dispose();[IO.File]::WriteAllBytes($correct,$original)}
 }
}
$report=[pscustomobject]@{Result='PASS';Checks=$results.Count;Cases=$results;Fixture=$fixture;SampleKind='COPIES_OF_CURRENT_SOURCE_AND_JARS';ActualRepositoryFilesMutated=$false;GameStarted=$false}
$output=Join-Path $root 'validations/integration-server_peer/result'
[void][IO.Directory]::CreateDirectory($output)
[IO.File]::WriteAllText((Join-Path $output 'local-gates.json'),($report|ConvertTo-Json -Depth 8).Replace("`r`n","`n")+"`n",[Text.UTF8Encoding]::new($false))
$report
