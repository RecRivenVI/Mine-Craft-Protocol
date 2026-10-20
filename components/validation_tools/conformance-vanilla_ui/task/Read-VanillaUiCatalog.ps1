[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')][string]$Target)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$version,$loader=$Target.Split('-')
$facts=@{}
foreach($line in Get-Content (Join-Path $repo "versions/$Target/target.properties")) {
    if($line -match '^([^#=]+)=(.*)$'){$facts[$Matches[1].Trim()]=$Matches[2].Trim()}
}
$artifacts=Join-Path $repo "versions/$Target/build/moddev/artifacts"
$jars=switch($loader) {
    forge { @(Join-Path $artifacts "forge-$version-$($facts.loader_version).jar") }
    neoforge { @(Join-Path $artifacts $(if($version.StartsWith('1.')){"neoforge-$($facts.loader_version).jar"}else{"minecraft-patched-$($facts.loader_version).jar"})) }
    fabric {
        $launch=Join-Path $repo ".gradle/loom-cache/projects/version/$Target/launch.cfg"
        if(-not(Test-Path -LiteralPath $launch)){throw 'Current Loom launch configuration missing; build first'}
        $paths=@{}
        foreach($line in Get-Content -LiteralPath $launch){
            if($line-match '^\s*fabric\.(gameJarPath(?:\.client)?)=(.+)$'){$paths[$Matches[1]]=$Matches[2].Trim()}
        }
        if(-not$paths.ContainsKey('gameJarPath')-or-not$paths.ContainsKey('gameJarPath.client')){throw 'Current Loom configuration must name common and client game artifacts'}
        foreach($key in @('gameJarPath','gameJarPath.client')){
            $path=[IO.Path]::GetFullPath($paths[$key])
            $cache=[IO.Path]::GetFullPath((Join-Path $repo '.gradle/loom-cache/minecraftMaven/net/minecraft'))+[IO.Path]::DirectorySeparatorChar
            if(-not$path.StartsWith($cache,[StringComparison]::OrdinalIgnoreCase)-or(Split-Path (Split-Path $path -Parent) -Leaf)-ne$version){throw 'Loom game artifact does not belong to this Target and repository'}
            $path
        }
    }
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
function U2($Reader){return (([int]$Reader.ReadByte() -shl 8) -bor [int]$Reader.ReadByte())}
function Read-Class($Entry) {
    if($Entry.Length-gt4MB){throw 'UI class exceeds the bounded catalog input size'}
    $stream=$Entry.Open();$reader=[IO.BinaryReader]::new($stream)
    try{
        if([Convert]::ToHexString($reader.ReadBytes(4))-ne'CAFEBABE'){throw 'Invalid class header'}
        $null=U2 $reader;$null=U2 $reader;$size=U2 $reader
        $pool=[object[]]::new($size)
        for($i=1;$i-lt$size;$i++){
            $tag=$reader.ReadByte()
            switch($tag){
                1 { $length=U2 $reader;$pool[$i]=[Text.Encoding]::UTF8.GetString($reader.ReadBytes($length)) }
                7 { $pool[$i]=U2 $reader }
                {$_-in3,4,9,10,11,12,17,18} { $null=$reader.ReadBytes(4) }
                {$_-in5,6} { $null=$reader.ReadBytes(8);$i++ }
                {$_-in8,16,19,20} { $null=$reader.ReadBytes(2) }
                15 { $null=$reader.ReadBytes(3) }
                default { throw "Unknown class constant tag: $tag" }
            }
        }
        $access=U2 $reader;$self=U2 $reader;$parent=U2 $reader
        return @{name=[string]$pool[[int]$pool[$self]];parent=$(if($parent){[string]$pool[[int]$pool[$parent]]}else{''});abstract=(($access-band0x0400)-ne0)}
    }finally{$reader.Dispose();$stream.Dispose()}
}
$classes=@{};$inputs=@()
foreach($jar in $jars){
    if(-not(Test-Path -LiteralPath $jar)){throw "Minecraft artifact missing; build $Target first"}
    $zip=[IO.Compression.ZipFile]::OpenRead($jar)
    try{
        foreach($entry in $zip.Entries){
            if($entry.FullName.EndsWith('.class')-and($entry.FullName.StartsWith('net/minecraft/client/gui/')-or$entry.FullName.StartsWith('com/mojang/realmsclient/')-or$entry.FullName.StartsWith('net/minecraft/realms/'))){
                $class=Read-Class $entry
                if($classes.ContainsKey($class.name)){throw 'Duplicate Minecraft UI class in catalog inputs'}
                $classes[$class.name]=$class
            }
        }
    }finally{$zip.Dispose()}
    $inputs+=@{path=[IO.Path]::GetRelativePath($repo,$jar).Replace('\','/');sha256=(Get-FileHash -LiteralPath $jar -Algorithm SHA256).Hash.ToLowerInvariant()}
}
if(-not$classes.ContainsKey('net/minecraft/client/gui/screens/Screen')){throw 'Named Screen base missing; never substitute obfuscated or unrelated JARs'}
function Is-Screen([string]$Name){
    for($depth=0;$depth-lt64;$depth++){
        if($Name-eq'net/minecraft/client/gui/screens/Screen'){return $true}
        if(-not$classes.ContainsKey($Name)){return $false}
        $Name=$classes[$Name].parent
    }
    throw 'UI class inheritance exceeds catalog budget'
}
$catalog=Get-Content -LiteralPath (Join-Path $repo 'validations/conformance-vanilla_ui/task/screens.json') -Raw|ConvertFrom-Json
function Get-UiCatalogAssignment($Catalog,[string]$ClassName){
    $simple=$ClassName.Substring($ClassName.LastIndexOf('.')+1)
    $matches=@($Catalog.families|Where-Object {$simple-in$_.screens})
    if($matches.Count-gt1){throw "Screen has competing family assignments: $ClassName"}
    $family=if($matches.Count){$matches[0].name}else{$null}
    $responsibility='interactive_ui';$reason='Native actionable UI; catalog membership is not operation acceptance'
    foreach($rule in $Catalog.namespaceFamilies){
        if($ClassName.StartsWith($rule.prefix,[StringComparison]::Ordinal)){
            if($family-and$family-ne$rule.family){throw "Screen namespace and family disagree: $ClassName"}
            $family=$rule.family;$responsibility=$rule.responsibility;$reason=$rule.reason
        }
    }
    foreach($rule in $Catalog.responsibilityRules){if($simple-in$rule.screens){$responsibility=$rule.responsibility;$reason=$rule.reason}}
    if(-not$family){$family='UNASSIGNED';$reason='Actual native class needs an explicit family assignment'}
    @{family=$family;responsibility=$responsibility;reason=$reason}
}
function Get-UiCatalogDisposition([string]$Responsibility,[bool]$HasRecordedChecks){
    if($Responsibility-eq'non_interactive_transition'){return 'NON_INTERACTIVE_TRANSITION'}
    if($Responsibility-eq'external_service'){return 'EXTERNAL_SERVICE_LIMITED'}
    if($HasRecordedChecks){return 'RECORDED_LIVE_CHECKS'}
    'PENDING'
}
function Get-UiCatalogEvidence($Reports,$Family,[string]$ClassName){
    foreach($entry in $Reports){
        if($ClassName-notin$entry.report.screens){continue}
        $checks=@($entry.report.checks|Where-Object {$check=$_;$check.status-eq'PASS'-and@($Family.evidenceChecks|Where-Object {$check.name-match$_}).Count}|ForEach-Object name)
        if($checks.Count){@{path=$entry.path;sha256=$entry.sha256;testedAt=$entry.report.testedAt;testedCommit=$entry.report.testedCommit;checks=$checks;sourceBinding='historical_recorded_run_not_current_candidate_acceptance';scope='actual_class_encounter_and_family_checks_not_complete_operation_coverage'}}
    }
}
$reports=@()
$directory=Join-Path $repo 'validations/conformance-vanilla_ui/result'
foreach($file in Get-ChildItem -LiteralPath $directory -Filter "$Target*.json" -File){
    $report=Get-Content -LiteralPath $file.FullName -Raw|ConvertFrom-Json
    if($report.target-ne$Target-or$report.status-ne'PASS'-or$report.sourceChangedDuringRun-ne$false-or$report.cleanShutdown-ne$true-or-not$report.sourceHashes-or-not$report.screens-or-not$report.checks){continue}
    $reports+=@{path=[IO.Path]::GetRelativePath($repo,$file.FullName).Replace('\','/');sha256=(Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant();report=$report}
}
$screens=@($classes.Values | Where-Object { -not$_.abstract-and(Is-Screen $_.name) } | Sort-Object name | ForEach-Object {
    $className=$_.name.Replace('/','.')
    $assignment=Get-UiCatalogAssignment $catalog $className
    $family=@($catalog.families|Where-Object name -eq $assignment.family)|Select-Object -First 1
    $evidence=@(Get-UiCatalogEvidence $reports $family $className)
    @{class=$className;superClass=$_.parent.Replace('/','.');family=$assignment.family;responsibility=$assignment.responsibility;dispositionReason=$assignment.reason;
        coverageDisposition=(Get-UiCatalogDisposition $assignment.responsibility ($evidence.Count-gt0));liveEvidence=$evidence;
        userRead='UNVALIDATED';userOperation='UNVALIDATED';debugRead='UNVALIDATED';debugOperation='UNVALIDATED'}
})
$catalogEntries=@(foreach($family in $catalog.families){foreach($name in $family.screens){
    $present=@($classes.Values|Where-Object {$_.name.Substring($_.name.LastIndexOf('/')+1)-eq$name})
    $concrete=@($screens|Where-Object {$_.class.Substring($_.class.LastIndexOf('.')+1)-eq$name})
    @{family=$family.name;screen=$name;nativeDisposition=$(if(-not$present.Count){'NATIVE_ABSENT'}elseif(-not$concrete.Count){'NOT_CONCRETE_SCREEN'}else{'PRESENT'});classes=@($concrete|ForEach-Object class);
        note='Class presence/absence is Target-specific; an absent dedicated class does not imply the user action is absent'}
}})
$unassigned=@($screens|Where-Object family -eq UNASSIGNED|ForEach-Object class)
$result=@{target=$Target;loaderVersion=$facts.loader_version;generatedAt=[DateTime]::UtcNow.ToString('o');inputs=$inputs;concreteScreenCount=$screens.Count;screens=$screens;
    catalogEntries=$catalogEntries;unassignedClasses=$unassigned;unassignedCount=$unassigned.Count;
    dispositionCounts=@($screens|Group-Object coverageDisposition|Sort-Object Name|ForEach-Object {@{disposition=$_.Name;count=$_.Count}});
    method='named_class_inheritance_inventory_and_bound_recorded_evidence';acceptance='NOT_VALIDATED';currentCandidateLiveAcceptance=$false;
    limitations=@('Family assignment is an acceptance catalog, never a Runtime input allowlist','Recorded class/check association is not complete operation coverage or new candidate Live PASS','HUD, Toast, tooltip and other non-Screen interaction regions require a separate catalog','Service/account state is not supplied by a static class inventory')}
[IO.Directory]::CreateDirectory($directory)|Out-Null
[IO.File]::WriteAllText((Join-Path $directory "inventory-$Target.json"),($result|ConvertTo-Json -Depth 12)+"`n",[Text.UTF8Encoding]::new($false))
Write-Output "UI catalog: $Target; $($screens.Count) concrete native Screens; $($unassigned.Count) unassigned; NOT_VALIDATED"
