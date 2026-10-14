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
        $cache=Join-Path $repo '.gradle/loom-cache/minecraftMaven/net/minecraft'
        $found=@(Get-ChildItem $cache -Recurse -Filter "*$version.jar" | Where-Object { $_.Directory.Name -eq $version })
        if($found.Count-ne2){throw 'Expected one current common and clientOnly Minecraft artifact; build first, do not guess among stale artifacts'}
        @($found.FullName)
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
            if($entry.FullName.EndsWith('.class')-and($entry.FullName.StartsWith('net/minecraft/client/gui/')-or$entry.FullName.StartsWith('com/mojang/realmsclient/'))){
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
$screens=@($classes.Values | Where-Object { -not$_.abstract-and(Is-Screen $_.name) } | Sort-Object name | ForEach-Object {
    @{class=$_.name.Replace('/','.');superClass=$_.parent.Replace('/','.');userRead='UNVALIDATED';userOperation='UNVALIDATED';debugRead='UNVALIDATED';debugOperation='UNVALIDATED'}
})
$result=@{target=$Target;loaderVersion=$facts.loader_version;generatedAt=[DateTime]::UtcNow.ToString('o');inputs=$inputs;concreteScreenCount=$screens.Count;screens=$screens;
    method='named_class_inheritance_inventory';acceptance='NOT_VALIDATED';limitations=@('Inventory is not operation coverage','HUD, Toast, tooltip and other non-Screen interaction regions require a separate catalog','Service/account state is not supplied by a static class inventory')}
$directory=Join-Path $repo 'validations/conformance-vanilla_ui/result'
[IO.Directory]::CreateDirectory($directory)|Out-Null
[IO.File]::WriteAllText((Join-Path $directory "inventory-$Target.json"),($result|ConvertTo-Json -Depth 8)+"`n",[Text.UTF8Encoding]::new($false))
Write-Output "UI catalog: $Target; $($screens.Count) concrete native Screens; NOT_VALIDATED"
