[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')][string]$Target,
    [ValidateSet('platform_probe','computercraft','modern_ui','emi')][string]$Adapter='platform_probe')
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
    & (Join-Path $repo 'gradlew.bat') ':extension_packages:packagePlatformProbe' ':extension_packages:packageAdapters' '--offline' '--console=plain'
    if($LASTEXITCODE-ne0){throw 'Probe package build failed'}
    & node (Join-Path $PSScriptRoot 'install-platform-probe.mjs') "--target=$Target"
    if($LASTEXITCODE-ne0){throw 'Package installation failed'}
    & {
        & python -B (Join-Path $PSScriptRoot 'install-adapter.py') "--target=$Target" "--adapter=$Adapter"
        if($LASTEXITCODE-ne0){throw 'Declared adapter/input installation failed'}
    }
    & (Join-Path $repo 'gradlew.bat') ':extension_packages:stageValidationLibrary' "-PextensionTarget=$Target" '--offline' '--console=plain'
    if($LASTEXITCODE-ne0){throw 'Offline native-library staging failed'}
    $stage=Join-Path $client 'mods/minecraft-protocol-extension-libraries.jar'
    $candidate=$stage+'.pending'
    if(Test-Path -LiteralPath $stage){
        $resolved=[IO.Path]::GetFullPath($stage)
        if(-not$resolved.StartsWith([IO.Path]::GetFullPath($client)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Stage path escapes owned instance'}
        Add-Type -AssemblyName Microsoft.VisualBasic
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile($resolved,[Microsoft.VisualBasic.FileIO.UIOption]::OnlyErrorDialogs,[Microsoft.VisualBasic.FileIO.RecycleOption]::SendToRecycleBin)
    }
    Move-Item -LiteralPath $candidate -Destination $stage
    $options=Join-Path $client 'options.txt'
    $original=if(Test-Path $options){[IO.File]::ReadAllText($options)}else{''}
    [IO.File]::WriteAllText($options,(Get-InstanceProfileOptions $original),[Text.UTF8Encoding]::new($false))
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
        & node (Join-Path $PSScriptRoot 'extension-platform-smoke.mjs') "--target=$Target" "--adapter=$Adapter"
        if($LASTEXITCODE-ne0){throw 'Extension platform assertions failed'}
    } catch {$failure=$_}
    finally {
        if(-not$process.WaitForExit(5000)-and$connection){& node (Join-Path $PSScriptRoot 'extension-platform-smoke.mjs') "--target=$Target" '--shutdown-only=true'}
        $stopped=$process.WaitForExit(60000)
        $clean=$stopped-and$process.ExitCode-eq0-and-not(Test-Path (Join-Path $client 'minecraft-protocol/endpoint.json'))
        if($clean-and$connection-and(Get-NetTCPConnection -State Listen -LocalPort $connection.Port -ErrorAction SilentlyContinue)){$clean=$false}
        $result=Join-Path $repo "validations/integration-extension_platform/result/$Target-client$(if($Adapter-ne'platform_probe'){'-'+$Adapter}).json"
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
