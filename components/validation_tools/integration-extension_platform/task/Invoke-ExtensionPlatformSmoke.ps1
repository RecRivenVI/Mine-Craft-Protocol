[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')][string]$Target)
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
    & node (Join-Path $PSScriptRoot 'install-platform-probe.mjs') "--target=$Target"
    if($LASTEXITCODE-ne0){throw 'Package installation failed'}
    $options=Join-Path $client 'options.txt'
    $original=if(Test-Path $options){[IO.File]::ReadAllText($options)}else{''}
    [IO.File]::WriteAllText($options,(Get-InstanceProfileOptions $original),[Text.UTF8Encoding]::new($false))
    $command="Set-Location -LiteralPath '$($repo.Replace("'","''"))'; `$env:MCP_RUNTIME_SCOPES='read,ui,input,event,diagnostics,control,command,debug,debug.world.read,debug.write,debug.provider'; & './gradlew.bat' ':version:$($Target):runValidationIntegrationExtensionPlatformClient' '--offline' '--console=plain'; exit `$LASTEXITCODE"
    Write-Output "Extension platform: $Target, one owned 1920x1080 muted client (4 GiB). SDK install/read/Debug/cleanup."
    $process=Start-Process pwsh -ArgumentList @('-NoProfile','-Command',$command) -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $root 'launch-out.log') -RedirectStandardError (Join-Path $root 'launch-error.log')
    $connection=$null;$failure=$null
    try {
        $connection=Wait-RuntimeConnection $client $Target -NotBefore $process.StartTime.ToUniversalTime() -Launcher $process
        & node (Join-Path $PSScriptRoot 'extension-platform-smoke.mjs') "--target=$Target"
        if($LASTEXITCODE-ne0){throw 'Extension platform assertions failed'}
    } catch {$failure=$_}
    finally {
        if(-not$process.WaitForExit(5000)-and$connection){& node (Join-Path $PSScriptRoot 'extension-platform-smoke.mjs') "--target=$Target" '--shutdown-only=true'}
        $stopped=$process.WaitForExit(60000)
        $clean=$stopped-and$process.ExitCode-eq0-and-not(Test-Path (Join-Path $client 'minecraft-protocol/endpoint.json'))
        if($clean-and$connection-and(Get-NetTCPConnection -State Listen -LocalPort $connection.Port -ErrorAction SilentlyContinue)){$clean=$false}
        $result=Join-Path $repo "validations/integration-extension_platform/result/$Target-client.json"
        if(Test-Path $result){$report=Get-Content $result -Raw|ConvertFrom-Json;if($connection-and$report.runtimeInstanceId-eq$connection.RuntimeInstanceId){$report|Add-Member -NotePropertyName cleanShutdown -NotePropertyValue $clean -Force;if(-not$clean){$report.status='FAIL'};[IO.File]::WriteAllText($result,($report|ConvertTo-Json -Depth 40)+"`n",[Text.UTF8Encoding]::new($false))}}
        if(-not$clean){$failure='Owned extension Runtime did not close cleanly; retained for diagnosis'}
    }
    if($failure){throw $failure}
    Write-Output "PASS: $Target extension SDK/Provider smoke and clean shutdown"
} finally {$lock.Dispose()}
