[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..\..')).Path

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Phase 7 Hook gate assertion failed: $Message" }
}

$targets = @(
    [pscustomobject]@{ Name = '1.20.1-forge'; Source = 'src\main\java'; Config = 'src\main\resources\minecraft_protocol_probe.mixins.json' },
    [pscustomobject]@{ Name = '1.21.1-neoforge'; Source = 'src\main\java'; Config = 'src\main\resources\minecraft_protocol_probe.mixins.json' },
    [pscustomobject]@{ Name = '26.1.2-neoforge'; Source = 'src\main\java'; Config = 'src\main\resources\minecraft_protocol_probe.mixins.json' },
    [pscustomobject]@{ Name = '26.2-neoforge'; Source = 'src\main\java'; Config = 'src\main\resources\minecraft_protocol_probe.mixins.json' },
    [pscustomobject]@{ Name = '26.2-fabric'; Source = 'src\client\java'; Config = 'src\client\resources\minecraft_protocol_probe.client.mixins.json' }
)

$results = foreach ($target in $targets) {
    $mixinDirectory = Join-Path $repositoryRoot "versions\$($target.Name)\$($target.Source)\io\github\recrivenvi\minecraftprotocol\probe\mixin"
    $files = @(Get-ChildItem -LiteralPath $mixinDirectory -Filter '*.java' -File)
    Assert-True ($files.Count -gt 0) "$($target.Name) must own concrete Hook sources"
    $texts = @($files | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw })
    $combined = $texts -join "`n"

    Assert-True ($combined -notmatch '@Overwrite') "$($target.Name) must not use Overwrite"
    $operatorFiles = @('KeyboardHandlerMixin.java', 'MouseHandlerMixin.java', 'MinecraftMixin.java', 'WindowMixin.java', 'InputConstantsMixin.java')
    $observationCode = ($files | Where-Object Name -notin $operatorFiles | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -join "`n"
    Assert-True ($observationCode -notmatch 'cancellable\s*=\s*true|\.cancel\s*\(|@Redirect') "$($target.Name) observation Hooks must not change control flow"
    Assert-True ($combined -notmatch '@ModifyArg|@ModifyArgs|@ModifyVariable|@ModifyConstant') "$($target.Name) must not use unreviewed replacement mechanisms"
    $cancellableCount = ([regex]::Matches($combined, 'cancellable\s*=\s*true')).Count
    $redirectCount = ([regex]::Matches($combined, '@Redirect')).Count
    $expectedCancellable=if($target.Name.StartsWith('26.')){15}else{13}
    Assert-True ($cancellableCount -eq $expectedCancellable -and $redirectCount -eq 4) "$($target.Name) must match exclusive input/polling/warp guards and native-ingress/icon/keymapping redirects"
    $windowHook = Get-Content -LiteralPath (Join-Path $mixinDirectory 'WindowMixin.java') -Raw
    Assert-True ($windowHook -match 'glfwSetWindowIcon' -and $windowHook -match 'onVanillaWindowIcon') "$($target.Name) icon redirect must preserve actual Vanilla pixels"
    Assert-True ($combined -notmatch '@Mixin\s*\(\s*targets\s*=') "$($target.Name) must use typed Minecraft targets"
    foreach ($file in $files) {
        $text = Get-Content -LiteralPath $file.FullName -Raw
        Assert-True ($text -match 'import (net\.minecraft\.|com\.mojang\.blaze3d\.)') "$($target.Name)/$($file.Name) must target Minecraft, not a third-party Mod"
    }

    $configPath = Join-Path $repositoryRoot "versions\$($target.Name)\$($target.Config)"
    $config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
    Assert-True ([bool]$config.required) "$($target.Name) Mixin config must fail visibly when a required Hook cannot apply"
    Assert-True ($config.injectors.defaultRequire -eq 1) "$($target.Name) Hook injections must not silently skip"
    Assert-True (@($config.client).Count -eq $files.Count) "$($target.Name) Mixin config and owned Hook source count must agree"

    [pscustomobject]@{
        Target = $target.Name
        HookSources = $files.Count
        Injects = ([regex]::Matches($combined, '@Inject\s*\(')).Count
        Invokers = ([regex]::Matches($combined, '@Invoker\s*\(')).Count
        Accessors = ([regex]::Matches($combined, '@Accessor\s*\(')).Count
        Overwrites = 0
        Cancellable = $cancellableCount
        Replacement = $redirectCount
        ThirdPartyTargets = 0
        Result = 'PASS'
    }
}

[pscustomobject]@{
    Result = 'PASS'
    Policy = 'capability_fidelity_first'
    Targets = $results
}
