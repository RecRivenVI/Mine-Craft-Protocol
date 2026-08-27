[CmdletBinding()]param([switch]$Offline,[switch]$SkipBuild)
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
function Assert-True([bool]$Condition,[string]$Message){if(-not$Condition){throw "Packaging gate: $Message"}}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$targets=@('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')
$settings=Get-Content (Join-Path $root 'settings.gradle.kts') -Raw
Assert-True ($settings-match 'product\("runtime_safety"\)') 'shared safety component is not a product'
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
  Assert-True (@($zip.Entries|Where-Object {$_.FullName-match '(?i)runtime[-_]safety.*\.jar$'}).Count-eq0) "$target must not duplicate safety in a nested JAR"
  foreach($license in Get-ChildItem (Join-Path $root 'licenses') -File){
   Assert-True ($null-ne$zip.GetEntry('META-INF/licenses/'+$license.Name)) "$target third-party license missing"
  }
 }finally{$zip.Dispose()}
 $hashes[$target]=(Get-FileHash $artifact -Algorithm SHA256).Hash.ToLowerInvariant()
}
[pscustomobject]@{Result='PASS';Targets=5;FinalArtifactEmbedding='FLATTENED_PRODUCT_COMPONENT';DuplicateSharedClass='NONE';ArtifactSha256=$hashes;Runtime='NOT_RUN';PersistentWrites=0}
