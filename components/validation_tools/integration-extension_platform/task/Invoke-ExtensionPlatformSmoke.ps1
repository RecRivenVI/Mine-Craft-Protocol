[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')][string]$Target,
    [ValidateSet('platform_probe','computercraft','modernui','emi','ae2','create','exposure','littletiles')][string]$Adapter='platform_probe',[switch]$LifetimeFaults,[switch]$Settings,[ValidateSet('initial','disabled-restart','enabled-restart')][string]$SettingsPhase='initial',
    [ValidateSet('en_us','zh_cn')][string]$Language='en_us')
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
. (Join-Path $PSScriptRoot '../../RuntimeConnection.ps1')
. (Join-Path $PSScriptRoot '../../InstanceProfiles.ps1')
$root=Join-Path $repo "validations/integration-extension_platform/instance/$Target"
$client=Join-Path $root 'client'
[IO.Directory]::CreateDirectory($client)|Out-Null
$lock=Enter-RuntimeRunner $client
try {
    $owner=Join-Path $client 'extension-platform-owner.json'
    if(Test-Path $owner){$owned=Get-Content $owner -Raw|ConvertFrom-Json;if($owned.target-ne$Target-or$owned.owner-ne'extension-platform'){throw 'Unknown extension instance owner'}}
    elseif(@(Get-ChildItem $client -Force).Count){throw 'Refuse unowned instance'}
    else{[IO.File]::WriteAllText($owner,(@{owner='extension-platform';target=$Target}|ConvertTo-Json)+"`n")}
    & (Join-Path $repo 'gradlew.bat') ':extensionpacks:packagePlatformProbe' ':extensionpacks:packageAdapters' ':extensionpacks:packageLifecycleFixtures' '--offline' '--console=plain'
    if($LASTEXITCODE-ne0){throw 'Probe package build failed'}
    if(-not$Settings-or$SettingsPhase-eq'initial'){
    & node (Join-Path $PSScriptRoot 'install-platform-probe.mjs') "--target=$Target" "--lifetimes=$($LifetimeFaults.ToString().ToLowerInvariant())" "--settings=$($Settings.ToString().ToLowerInvariant())"
    if($LASTEXITCODE-ne0){throw 'Package installation failed'}
    & {
        & python -B (Join-Path $PSScriptRoot 'install-adapter.py') "--target=$Target" "--adapter=$Adapter" @($(if($Settings){'--settings'}))
        if($LASTEXITCODE-ne0){throw 'Declared adapter/input installation failed'}
    }
    }
    # Obsolete derived libraries are this validation's own files; the Loader now regenerates its cache.
    foreach($name in @('minecraft-protocol-extension-libraries.jar','mine_craft_protocol-extension-libraries.jar','mine_craft_protocol-extension-discovery.jar')) {
        $old=[IO.Path]::GetFullPath((Join-Path $client ('mods/'+$name)))
        if(-not$old.StartsWith([IO.Path]::GetFullPath($client)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Unsafe owned native-library path'}
        if(Test-Path -LiteralPath $old){Add-Type -AssemblyName Microsoft.VisualBasic;[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($old,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)}
    }
    & (Join-Path $repo 'gradlew.bat') ':extensionpacks:stageValidationLibrary' "-PextensionTarget=$Target" '--offline' '--console=plain'
    if($LASTEXITCODE-ne0){throw 'Development source-set native staging failed'}
    $stage=Join-Path $client 'mine_craft_protocol/extensions/native/mine_craft_protocol-extension-libraries.jar'
    if(Test-Path $stage){Add-Type -AssemblyName Microsoft.VisualBasic;[Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile([IO.Path]::GetFullPath($stage),[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)}
    Move-Item -LiteralPath ($stage+'.pending') -Destination $stage
    Copy-Item -LiteralPath $stage -Destination (Join-Path $client 'mods/mine_craft_protocol-extension-libraries.jar')
    $options=Join-Path $client 'options.txt'
    $original=if(Test-Path $options){[IO.File]::ReadAllText($options)}else{''}
    $profile=Get-InstanceProfileOptions $original
    $profile=[regex]::Replace($profile,'(?m)^lang:.*$',('lang:'+$Language))
    [IO.File]::WriteAllText($options,$profile,[Text.UTF8Encoding]::new($false))
    # Loader fatal dialogs have no Runtime yet; CI + no splash keeps startup failures in logs.
    $fml=Join-Path $client 'config/fml.toml'
    [IO.Directory]::CreateDirectory((Split-Path $fml))|Out-Null
    $fmlText=if(Test-Path $fml){[IO.File]::ReadAllText($fml)}else{''}
    $fmlText=if($fmlText-match'(?m)^earlyWindowControl\s*='){[regex]::Replace($fmlText,'(?m)^earlyWindowControl\s*=.*$','earlyWindowControl = false')}else{$fmlText+"`nearlyWindowControl = false`n"}
    [IO.File]::WriteAllText($fml,$fmlText,[Text.UTF8Encoding]::new($false))
    $command="Set-Location -LiteralPath '$($repo.Replace("'","''"))'; `$env:CI='true'; `$env:MCP_RUNTIME_SCOPES='read,ui,input,capture,event,diagnostics,control,command,debug,debug.world.read,debug.write,debug.world,debug.ui,debug.ui.read,debug.provider'; & './gradlew.bat' ':version:$($Target):runValidationIntegrationExtensionPlatformClient' '--offline' '--console=plain'; exit `$LASTEXITCODE"
    Write-Output "Extension platform: $Target, one owned 1920x1080 muted client (4 GiB). SDK install/read/Debug/cleanup."
    $process=Start-Process pwsh -ArgumentList @('-NoProfile','-Command',$command) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'launch-out.log') -RedirectStandardError (Join-Path $root 'launch-error.log')
    $connection=$null;$failure=$null
    try {
        $connection=Wait-RuntimeConnection $client $Target -NotBefore $process.StartTime.ToUniversalTime() -Launcher $process
        if($Settings){& node (Join-Path $PSScriptRoot 'extension-settings-smoke.mjs') "--target=$Target" "--phase=$SettingsPhase" "--language=$Language"}else{& node (Join-Path $PSScriptRoot 'extension-platform-smoke.mjs') "--target=$Target" "--adapter=$Adapter" "--lifetime-faults=$($LifetimeFaults.ToString().ToLowerInvariant())"}
        if($LASTEXITCODE-ne0){throw 'Extension platform assertions failed'}
    } catch {$failure=$_}
    finally {
        if(-not$process.WaitForExit(5000)-and$connection){& node (Join-Path $PSScriptRoot 'extension-platform-smoke.mjs') "--target=$Target" '--shutdown-only=true'}
        $stopped=$process.WaitForExit(60000)
        $clean=$stopped-and$process.ExitCode-eq0-and-not(Test-Path (Join-Path $client 'mine_craft_protocol/endpoint.json'))
        if($clean-and$connection-and(Get-NetTCPConnection -State Listen -LocalPort $connection.Port -ErrorAction SilentlyContinue)){$clean=$false}
        $result=Join-Path $repo "validations/integration-extension_platform/result/$Target-client$(if($Adapter-ne'platform_probe'){'-'+$Adapter})$(if($LifetimeFaults){'-lifetimes'})$(if($Settings){'-settings'+$(if($SettingsPhase-ne'initial'){'-'+$SettingsPhase})+$(if($Language-ne'en_us'){'-'+$Language})}).json"
        if(Test-Path $result){$report=Get-Content $result -Raw|ConvertFrom-Json;if($connection-and$report.runtimeInstanceId-eq$connection.RuntimeInstanceId){$report|Add-Member -NotePropertyName cleanShutdown -NotePropertyValue $clean -Force;if(-not$clean){$report.status='FAIL'};[IO.File]::WriteAllText($result,($report|ConvertTo-Json -Depth 40)+"`n",[Text.UTF8Encoding]::new($false))}}
        if(-not$connection){
            [IO.Directory]::CreateDirectory((Split-Path $result))|Out-Null
            $report=@{target=$Target;status='FAIL';stage='runtime_startup';completedAt=[DateTimeOffset]::UtcNow.ToString('o');runtimeInstanceId=$null;cleanShutdown=$clean;error=[string]$failure;persistentWriteCalls=0}
            [IO.File]::WriteAllText($result,($report|ConvertTo-Json -Depth 10)+"`n",[Text.UTF8Encoding]::new($false))
        }
        if(-not$clean){$failure=[string]$failure+'; owned extension Runtime did not close cleanly; retained for diagnosis'}
    }
    if($failure){throw $failure}
    Write-Output "PASS: $Target extension SDK/Provider smoke and clean shutdown"
} finally {$lock.Dispose()}
