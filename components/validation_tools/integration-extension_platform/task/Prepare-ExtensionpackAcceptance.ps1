[CmdletBinding()]param([switch]$PrepareOnly)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
. (Join-Path $PSScriptRoot '../../RuntimeConnection.ps1')
. (Join-Path $PSScriptRoot 'ExtensionHost.Helpers.ps1')
$target='1.21.1-neoforge'
$base=Join-Path $repo 'validations/feature-control_ui_showcase/instance'
$game=Join-Path $base "$target/client"
[void](Assert-ExtensionHostPath $base $game)
Assert-RuntimeInstanceStopped $game
$clients=@(Get-CimInstance Win32_Process -Filter "Name = 'java.exe' OR Name = 'javaw.exe'"|Where-Object {$_.CommandLine-match[regex]::Escape($repo)-and$_.CommandLine-match'clientRunVmArgs|KnotClient|--launchTarget(?:=|\s+)[^\s]*client|net\.minecraft\.client\.main\.Main'})
if($clients.Count){throw 'Another project client is active; no second acceptance client will be started'}
$owner=Join-Path $game 'showcase-instance.json'
if(Test-Path $owner){
    $record=Get-Content $owner -Raw|ConvertFrom-Json
    if($record.target-ne$target-or$record.purpose-ne'CONTROL_UI_SHOWCASE'){throw 'Unknown acceptance instance owner'}
}elseif((Test-Path $game)-and@(Get-ChildItem $game -Force).Count){throw 'Refuse an unowned acceptance instance'}
else{
    [IO.Directory]::CreateDirectory($game)|Out-Null
    [IO.File]::WriteAllText($owner,(@{target=$target;purpose='CONTROL_UI_SHOWCASE';created=[DateTime]::UtcNow.ToString('o')}|ConvertTo-Json)+"`n")
}
$lock=Enter-RuntimeRunner $game
try{
    & (Join-Path $repo 'gradlew.bat') ':extensionpacks:packageAdapters' '--offline' '--console=plain'
    if($LASTEXITCODE-ne0){throw 'Extensionpack build failed'}
    $ids=Get-Content (Join-Path $repo 'components/extensionpacks/extensionpacks/index.json') -Raw|ConvertFrom-Json
    foreach($id in $ids){
        $source=Join-Path $repo "components/extensionpacks/build/extensionpacks/$id"
        $destination=Join-Path $game "extensionpacks/$id"
        [void](Assert-ExtensionHostPath $game $destination)
        if(-not(Test-Path (Join-Path $source 'extensionpack.json'))){throw "Missing built extensionpack $id"}
        if(Test-Path $destination){
            $before=Get-ExtensionFixtureFiles $game $destination
            $after=Get-ExtensionFixtureFiles (Split-Path $source) $source
            if(($before|ConvertTo-Json -Depth 10 -Compress)-cne($after|ConvertTo-Json -Depth 10 -Compress)){
                throw "Acceptance package $id differs; preserve it and resolve the owned test replacement explicitly"
            }
        }else{
            [IO.Directory]::CreateDirectory((Split-Path $destination))|Out-Null
            Copy-Item -LiteralPath $source -Destination $destination -Recurse
        }
    }
    # This host tool installs files only; first approval remains a physical in-game action.
    & (Join-Path $PSScriptRoot '../../Prepare-InstanceProfile.ps1') -Directory $game -Kind acceptance
    $options=Join-Path $game 'options.txt'
    [IO.File]::WriteAllText($options,([regex]::Replace([IO.File]::ReadAllText($options),'(?m)^lang:.*$','lang:zh_cn')),[Text.UTF8Encoding]::new($false))
    $stage=Join-Path $game 'mine_craft_protocol/extensions/native/mine_craft_protocol-extension-libraries.jar'
    function Recycle-StagedFile([string]$File){
        [void](Assert-ExtensionHostPath $game $File)
        if(Test-Path -LiteralPath $File){
            Add-Type -AssemblyName Microsoft.VisualBasic
            [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile([IO.Path]::GetFullPath($File),[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)
        }
    }
    foreach($file in @(($stage+'.pending'),(Join-Path $game 'mods/mine_craft_protocol-extension-libraries.jar'))){
        Recycle-StagedFile $file
    }
    & (Join-Path $repo 'gradlew.bat') ':extensionpacks:stageValidationLibrary' "-PextensionTarget=$target" '-PextensionRole=client' '-PextensionLayout=showcase' '--offline' '--console=plain'
    if($LASTEXITCODE-ne0){throw 'Source-set acceptance early-hook staging failed'}
    Recycle-StagedFile $stage
    Move-Item -LiteralPath ($stage+'.pending') -Destination $stage
    [IO.Directory]::CreateDirectory((Join-Path $game 'mods'))|Out-Null
    Copy-Item -LiteralPath $stage -Destination (Join-Path $game 'mods/mine_craft_protocol-extension-libraries.jar')
    if($PrepareOnly){Write-Output 'Seven extensionpacks prepared; approval records were not changed; client not started';return}
    $child=Join-Path $PSScriptRoot '../../feature-control_ui_showcase/task/Start-ControlUiShowcaseClient.ps1'
    $arguments=@('-NoProfile','-File',('"'+$child+'"'),'-Target',$target,'-InstanceDirectory',('"'+$game+'"'),'-Extensions','-Offline')
    $process=Start-Process (Join-Path $PSHOME 'pwsh.exe') -ArgumentList $arguments -WorkingDirectory $repo -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $game 'launcher.log') -RedirectStandardError (Join-Path $game 'launcher-error.log')
    $connection=Wait-RuntimeConnection $game $target -NotBefore $process.StartTime.ToUniversalTime() -Launcher $process
    Write-Output "Acceptance client ready: $target; PID=$($connection.ProcessId); Runtime=$($connection.RuntimeInstanceId). No automatic approval or Showcase case."
}finally{$lock.Dispose()}
