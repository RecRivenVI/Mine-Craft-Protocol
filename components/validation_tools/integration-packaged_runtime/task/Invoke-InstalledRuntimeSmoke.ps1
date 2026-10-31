[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')][string]$Target,
    [switch]$EnterWorld,[switch]$ExtensionPlatform,
    [ValidateSet('platform_probe','computercraft','modern_ui','emi')][string]$ExtensionAdapter='platform_probe')
$ErrorActionPreference='Stop'
if($ExtensionAdapter-ne'platform_probe'-and-not$ExtensionPlatform){throw 'Adapter requires explicit ExtensionPlatform scenario'}
$resultName=$Target+$(if($ExtensionAdapter-ne'platform_probe'){'-'+$ExtensionAdapter})+'.json'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
. (Join-Path $PSScriptRoot '../../RuntimeConnection.ps1')
. (Join-Path $PSScriptRoot 'InstalledInstanceCleanup.ps1')
$parent=Join-Path $repo $(if($ExtensionPlatform){'validations/integration-extension_platform/instance/installed'}else{'validations/integration-packaged_runtime/instance/installed'})
[IO.Directory]::CreateDirectory($parent)|Out-Null
$root=Join-Path $parent $Target
$lock=[IO.File]::Open((Join-Path $parent ($Target+'.runner.lock')),[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::ReadWrite,[IO.FileShare]::None)
try{
    $stage='owned_instance_cleanup'
    $resultRoot=Join-Path $repo $(if($ExtensionPlatform){'validations/integration-extension_platform/result'}else{'validations/integration-packaged_runtime/result'})
    [IO.Directory]::CreateDirectory($resultRoot)|Out-Null
    [IO.File]::WriteAllText((Join-Path $resultRoot $resultName),(@{target=$Target;status='IN_PROGRESS';testedAt=[DateTimeOffset]::UtcNow.ToString('o')}|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
    if(Test-Path -LiteralPath $root){
        Retire-InstalledRuntimeInstance -Root $root -ExpectedParent $parent -Target $Target -ShortParent (Split-Path -Parent $repo)|Out-Null
    }
    $stage='official_installation'
    [IO.Directory]::CreateDirectory($root)|Out-Null
    [IO.File]::WriteAllText((Join-Path $root 'owner.json'),(@{owner='packaged-runtime-clean-install';target=$Target}|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
    $launcherArgs=@("--target=$Target")
    if($EnterWorld){$launcherArgs+='--enter-world'}
    if($ExtensionPlatform){$launcherArgs+='--extension-platform';$launcherArgs+='--extension-adapter='+$ExtensionAdapter}
    & node (Join-Path $PSScriptRoot 'installed-launcher.mjs') @launcherArgs
    if($LASTEXITCODE-ne0){
        $result=Join-Path $resultRoot $resultName
        if((Get-Content $result -Raw|ConvertFrom-Json).status-eq'IN_PROGRESS'){
            [IO.File]::WriteAllText($result,(@{target=$Target;status='FAIL';stage='official_installation';testedAt=[DateTimeOffset]::UtcNow.ToString('o')}|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
        }
        throw "Installed Runtime smoke failed: $Target"
    }
}catch{
    $result=Join-Path $resultRoot $resultName
    if((Get-Content -LiteralPath $result -Raw|ConvertFrom-Json).status-eq'IN_PROGRESS'){
        [IO.File]::WriteAllText($result,(@{target=$Target;status='FAIL';stage=$stage;error=$_.Exception.Message;testedAt=[DateTimeOffset]::UtcNow.ToString('o')}|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
    }
    throw
}finally{$lock.Dispose()}
