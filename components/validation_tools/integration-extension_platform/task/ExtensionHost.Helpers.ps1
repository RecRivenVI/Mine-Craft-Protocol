function Get-ExtensionHostPlan {
    param([string]$Repository,[string]$Target,[string]$Adapter,[string]$Profile)
    $registered=[regex]::Matches([IO.File]::ReadAllText((Join-Path $Repository 'settings.gradle.kts')),'register\("([0-9.]+-(?:forge|neoforge|fabric))"\)')|ForEach-Object {$_.Groups[1].Value}
    if($Target-notin$registered){throw 'Registered Target required'}
    $adapters=@('platform_probe')+@(Get-Content (Join-Path $Repository 'components/extensionpacks/extensionpacks/index.json') -Raw|ConvertFrom-Json)
    if($Adapter-notin$adapters){throw 'Registered adapter/fixture ID required'}
    if($Profile-notin@('native-client','installed-client','installed-server')){throw 'Registered host profile required'}
    if($Adapter-ne'platform_probe'){
        $spec=Get-Content (Join-Path $Repository "components/extensionpacks/extensionpacks/$Adapter/package.json") -Raw|ConvertFrom-Json
        if($Target-notin$spec.targets){throw 'No pinned adapter variant for this Target'}
    }
    $role=if($Profile-eq'installed-server'){'server'}else{'client'}
    $installed=$Profile.StartsWith('installed-')
    $base=[IO.Path]::GetFullPath((Join-Path $Repository 'validations/integration-extension_platform/instance'))
    $game=Join-Path $base "$(if($installed){'installed/'})$Target/$role"
    [pscustomobject]@{Target=$Target;Adapter=$Adapter;Profile=$Profile;Role=$role;Installed=$installed;Game=$game;Base=$base;Fixture=Join-Path $base "fixtures/$Target/$Profile/$Adapter";World=Join-Path $game $(if($role-eq'server'){if($Adapter-eq'platform_probe'){'Console-Smoke'}else{"Extension-Server-$Adapter"}}else{"saves/Extension-Platform-$Adapter"})}
}

function Assert-ExtensionHostPath {
    param([string]$Base,[string]$Path)
    $baseFull=[IO.Path]::GetFullPath($Base).TrimEnd('\','/')+[IO.Path]::DirectorySeparatorChar
    $full=[IO.Path]::GetFullPath($Path)
    if((Test-Path -LiteralPath $Base)-and((Get-Item -LiteralPath $Base -Force).Attributes-band[IO.FileAttributes]::ReparsePoint)){throw 'Registered host root cannot be a link'}
    if(-not$full.StartsWith($baseFull,[StringComparison]::OrdinalIgnoreCase)){throw 'Path escapes registered host root'}
    for($at=$full;$at-and$at.StartsWith($baseFull,[StringComparison]::OrdinalIgnoreCase);$at=Split-Path $at){
        if((Test-Path -LiteralPath $at)-and((Get-Item -LiteralPath $at -Force).Attributes-band[IO.FileAttributes]::ReparsePoint)){throw 'Host paths cannot contain links'}
    }
    return $full
}

function Assert-ExtensionHostStopped {
    param($Plan)
    if(-not$IsWindows){throw 'Native host process and lock verification requires Windows'}
    [void](Assert-ExtensionHostPath $Plan.Base $Plan.Game)
    if(Test-Path -LiteralPath (Join-Path $Plan.Game 'mine_craft_protocol/endpoint.json')){throw 'Endpoint still present; use normal Runtime shutdown/owned cleanup before host changes'}
    $processes=Get-CimInstance Win32_Process -Filter "name='java.exe'"
    if($processes|Where-Object {$_.CommandLine-and$_.CommandLine.Contains($Plan.Game,[StringComparison]::OrdinalIgnoreCase)}){throw 'Game process still uses registered instance'}
    Assert-RuntimeInstanceStopped $Plan.Game
    $worldLock=Join-Path $Plan.World 'session.lock'
    if(Test-Path -LiteralPath $worldLock){$handle=[IO.File]::Open($worldLock,[IO.FileMode]::Open,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None);$handle.Dispose()}
}

function Get-ExtensionFixtureFiles {
    param([string]$Base,[string]$Directory)
    [void](Assert-ExtensionHostPath $Base $Directory)
    $files=[Collections.Generic.List[object]]::new();$bytes=0L
    foreach($item in Get-ChildItem -LiteralPath $Directory -Force -Recurse){
        [void](Assert-ExtensionHostPath $Base $item.FullName)
        if($item.PSIsContainer){continue}
        $bytes+=$item.Length
        if($files.Count-ge100000-or$bytes-gt16GB){throw 'Fixture exceeds fixed file/byte budget'}
        $files.Add([pscustomobject]@{path=[IO.Path]::GetRelativePath($Directory,$item.FullName).Replace('\','/');sha256=(Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash.ToLowerInvariant()})
    }
    return @($files|Sort-Object path)
}

function Send-ExtensionHostRecycle {
    param([string]$Base,[string]$Path)
    if(-not$IsWindows){throw 'Recycle Bin host workflow requires Windows'}
    $checked=Assert-ExtensionHostPath $Base $Path
    Add-Type -AssemblyName Microsoft.VisualBasic
    [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteDirectory($checked,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)
}

function Get-ExtensionHostFacts {
    param([string]$Repository,$Plan)
    $mods=@(Get-ChildItem -LiteralPath (Join-Path $Plan.Game 'mods') -Filter '*.jar' -ErrorAction SilentlyContinue|Sort-Object Name|ForEach-Object {
        [void](Assert-ExtensionHostPath $Plan.Base $_.FullName)
        [ordered]@{name=$_.Name;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
    })
    [ordered]@{targetFactsSha256=(Get-FileHash -LiteralPath (Join-Path $Repository "versions/$($Plan.Target)/target.properties") -Algorithm SHA256).Hash.ToLowerInvariant();mods=$mods}
}

function Retire-ExtensionHostEndpoint {
    param($Plan)
    if(-not$IsWindows){throw 'Endpoint process/port retirement requires Windows'}
    $path=Join-Path $Plan.Game 'mine_craft_protocol/endpoint.json'
    [void](Assert-ExtensionHostPath $Plan.Base $path)
    if(-not(Test-Path -LiteralPath $path)){return}
    if((Get-Item -LiteralPath $path).Length-gt16384){throw 'Oversized endpoint retained'}
    $record=Get-Content -LiteralPath $path -Raw|ConvertFrom-Json
    if($record.schemaVersion-ne1-or$record.target-ne$Plan.Target-or[IO.Path]::GetFullPath($record.gameDirectory)-ine[IO.Path]::GetFullPath($Plan.Game)-or$record.pid-lt1-or$record.runtimeInstanceId-notmatch'^[0-9a-f-]{36}$'){throw 'Unknown endpoint identity retained'}
    if(Get-Process -Id $record.pid -ErrorAction SilentlyContinue){throw 'Endpoint owner still alive; retained'}
    if(Get-NetTCPConnection -State Listen -LocalPort $record.port -ErrorAction SilentlyContinue){throw 'Endpoint port still listening; retained'}
    Assert-RuntimeInstanceStopped $Plan.Game
    Add-Type -AssemblyName Microsoft.VisualBasic
    [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($path,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)
    Write-Output 'Retired owned stale endpoint after dead PID, released port and lock checks.'
}
