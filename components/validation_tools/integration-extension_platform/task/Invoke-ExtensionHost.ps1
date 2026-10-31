[CmdletBinding()]
param([Parameter(Mandatory)][ValidateSet('list','prepare','verify','snapshot','restore','retire-endpoint')][string]$Action,
    [string]$Target='26.3-neoforge',[string]$Adapter='platform_probe',
    [ValidateSet('native-client','installed-client','installed-server')][string]$Profile='native-client')
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
. (Join-Path $PSScriptRoot '../../RuntimeConnection.ps1')
. (Join-Path $PSScriptRoot 'ExtensionHost.Helpers.ps1')
$plan=Get-ExtensionHostPlan $repo $Target $Adapter $Profile
[void](Assert-ExtensionHostPath $plan.Base $plan.Game)
if($Action-eq'list'){$plan|Select-Object Target,Adapter,Profile,Role;return}
if(-not$IsWindows){throw 'Native extension host workflows currently require Windows'}
if($Action-eq'verify'){
    # Registered workflows own launch identity, normal stop, endpoint/port checks and evidence.
    if($plan.Role-eq'server'){
        [IO.Directory]::CreateDirectory($plan.Game)|Out-Null
        $serverGuard=Enter-RuntimeRunner $plan.Game
        try{& node (Join-Path $PSScriptRoot '../../integration-server_peer/task/server-console-smoke.mjs') "--target=$Target" '--extension-platform' "--extension-adapter=$Adapter"}finally{$serverGuard.Dispose()}
    }elseif($plan.Installed){
        & (Join-Path $PSScriptRoot '../../integration-packaged_runtime/task/Invoke-InstalledRuntimeSmoke.ps1') -Target $Target -ExtensionPlatform -ExtensionAdapter $Adapter
    }else{& (Join-Path $PSScriptRoot 'Invoke-ExtensionPlatformSmoke.ps1') -Target $Target -Adapter $Adapter}
    if($LASTEXITCODE-ne0){throw 'Registered host workflow failed'}
    return
}
if(-not(Test-Path -LiteralPath $plan.Game)){[IO.Directory]::CreateDirectory($plan.Game)|Out-Null}
$guard=Enter-RuntimeRunner $plan.Game
try{
    if($Action-eq'retire-endpoint'){
        $owned=Get-Content -LiteralPath (Join-Path $plan.Game 'extension-platform-owner.json') -Raw|ConvertFrom-Json
        if($owned.owner-ne'extension-platform'-or$owned.target-ne$Target){throw 'Unknown instance owner; retained'}
        Retire-ExtensionHostEndpoint $plan
        return
    }
    Assert-ExtensionHostStopped $plan
    $marker=Join-Path $plan.Game 'extension-platform-owner.json'
    if(-not(Test-Path -LiteralPath $marker)){
        if(@(Get-ChildItem -LiteralPath $plan.Game -Force).Count){throw 'Refuse unowned existing instance'}
        if($Action-ne'prepare'){throw 'Prepare an owned instance first'}
        [IO.File]::WriteAllText($marker,(@{owner='extension-platform';target=$Target}|ConvertTo-Json)+"`n",[Text.UTF8Encoding]::new($false))
    }
    $owner=Get-Content -LiteralPath $marker -Raw|ConvertFrom-Json
    if($owner.owner-ne'extension-platform'-or$owner.target-ne$Target){throw 'Unknown host instance identity'}
    if($Action-eq'prepare'){
        if($plan.Role-ne'client'){throw 'Server creation is owned by the registered verify workflow'}
        & (Join-Path $repo 'gradlew.bat') ':runtime_core:extensionSdkJar' ':extension_packages:assemble' ':companion:assemble' '--offline' '--console=plain'
        if($LASTEXITCODE-ne0){throw 'SDK/package build failed'}
        & node (Join-Path $PSScriptRoot 'install-platform-probe.mjs') "--target=$Target" "--installed=$($plan.Installed.ToString().ToLowerInvariant())"
        if($LASTEXITCODE-ne0){throw 'Platform package installation failed'}
        $arguments=@('-B',(Join-Path $PSScriptRoot 'install-adapter.py'),"--target=$Target","--adapter=$Adapter")
        if($plan.Installed){$arguments+='--installed'}
        & python @arguments
        if($LASTEXITCODE-ne0){throw 'Adapter installation failed'}
        Write-Output 'Prepared declared packages and approvals; verify stages native hooks and runs the owned scenario.'
        return
    }
    [void](Assert-ExtensionHostPath $plan.Base $plan.Fixture)
    if($Action-eq'snapshot'){
        if(Test-Path -LiteralPath $plan.Fixture){throw 'Registered baseline already exists; it is not overwritten'}
        $files=Get-ExtensionFixtureFiles $plan.Base $plan.World
        $copy=Join-Path $plan.Fixture 'world'
        [IO.Directory]::CreateDirectory($plan.Fixture)|Out-Null
        Copy-Item -LiteralPath $plan.World -Destination $copy -Recurse
        $copied=Get-ExtensionFixtureFiles $plan.Base $copy
        if(($files|ConvertTo-Json -Compress)-cne($copied|ConvertTo-Json -Compress)){throw 'Fixture changed during stopped copy; retained for diagnosis'}
        [IO.File]::WriteAllText((Join-Path $plan.Fixture 'manifest.json'),(@{schemaVersion=1;target=$Target;adapter=$Adapter;profile=$Profile;facts=(Get-ExtensionHostFacts $repo $plan);files=$files}|ConvertTo-Json -Depth 8)+"`n",[Text.UTF8Encoding]::new($false))
        Write-Output "Frozen stopped fixture: $Target/$Profile/$Adapter ($($files.Count) files)"
    }else{
        $manifest=Get-Content (Join-Path $plan.Fixture 'manifest.json') -Raw|ConvertFrom-Json
        if($manifest.schemaVersion-ne1-or$manifest.target-ne$Target-or$manifest.adapter-ne$Adapter-or$manifest.profile-ne$Profile){throw 'Fixture identity mismatch'}
        if(($manifest.facts|ConvertTo-Json -Depth 8 -Compress)-cne((Get-ExtensionHostFacts $repo $plan)|ConvertTo-Json -Depth 8 -Compress)){throw 'Fixture Target/mod versions differ; baseline cannot silently upgrade'}
        $copy=Join-Path $plan.Fixture 'world'
        $files=Get-ExtensionFixtureFiles $plan.Base $copy
        if(($files|ConvertTo-Json -Compress)-cne($manifest.files|ConvertTo-Json -Compress)){throw 'Fixture hash/content mismatch'}
        Assert-ExtensionHostStopped $plan
        if(Test-Path -LiteralPath $plan.World){Send-ExtensionHostRecycle $plan.Base $plan.World}
        [IO.Directory]::CreateDirectory((Split-Path $plan.World))|Out-Null
        Copy-Item -LiteralPath $copy -Destination $plan.World -Recurse
        if(((Get-ExtensionFixtureFiles $plan.Base $plan.World)|ConvertTo-Json -Compress)-cne($files|ConvertTo-Json -Compress)){throw 'Restored fixture hash mismatch'}
        Write-Output "Restored identical fixture: $Target/$Profile/$Adapter"
    }
}finally{$guard.Dispose()}
