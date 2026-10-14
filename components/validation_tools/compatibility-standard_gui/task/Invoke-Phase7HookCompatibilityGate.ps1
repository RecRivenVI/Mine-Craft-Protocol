[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Phase 7 Hook gate assertion failed: $Message" }
}

$targets = @(
    [pscustomobject]@{ Name = '1.20.1-forge'; Source = 'src\main\java'; Config = 'src\main\resources\minecraft_protocol_probe.mixins.json' },
    [pscustomobject]@{ Name = '1.21.1-neoforge'; Source = 'src\main\java'; Config = 'src\main\resources\minecraft_protocol_probe.mixins.json' },
    [pscustomobject]@{ Name = '26.1.2-neoforge'; Source = 'src\main\java'; Config = 'src\main\resources\minecraft_protocol_probe.mixins.json' },
    [pscustomobject]@{ Name = '26.3-neoforge'; Source = 'src\main\java'; Config = 'src\main\resources\minecraft_protocol_probe.mixins.json' },
    [pscustomobject]@{ Name = '26.3-fabric'; Source = 'src\client\java'; Config = 'src\client\resources\minecraft_protocol_probe.client.mixins.json' }
)

$results = foreach ($target in $targets) {
    $mixinDirectory = Join-Path $repositoryRoot "versions\$($target.Name)\$($target.Source)\io\github\recrivenvi\minecraftprotocol\probe\mixin"
    $files = @(Get-ChildItem -LiteralPath $mixinDirectory -Filter '*.java' -File)
    Assert-True ($files.Count -gt 0) "$($target.Name) must own concrete Hook sources"
    $texts = @($files | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw })
    $combined = $texts -join "`n"

    Assert-True ($combined -notmatch '@Overwrite') "$($target.Name) must not use Overwrite"
    $operatorFiles = @('KeyboardHandlerMixin.java', 'MouseHandlerMixin.java', 'MinecraftMixin.java', 'WindowMixin.java', 'InputConstantsMixin.java', 'SDLEventHandlerMixin.java')
    $observationCode = ($files | Where-Object Name -notin $operatorFiles | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -join "`n"
    Assert-True ($observationCode -notmatch 'cancellable\s*=\s*true|\.cancel\s*\(|@Redirect') "$($target.Name) observation Hooks must not change control flow"
    Assert-True ($combined -notmatch '@ModifyArg|@ModifyArgs|@ModifyVariable|@ModifyConstant') "$($target.Name) must not use unreviewed replacement mechanisms"
    $cancellableCount = ([regex]::Matches($combined, 'cancellable\s*=\s*true')).Count
    $redirectCount = ([regex]::Matches($combined, '@Redirect')).Count
    $expectedCancellable=if($target.Name.StartsWith('26.3-')){17}elseif($target.Name.StartsWith('26.')){14}else{12}
    $expectedRedirect=if($target.Name.StartsWith('26.3-')){4}else{5}
    Assert-True ($cancellableCount -eq $expectedCancellable -and $redirectCount -eq $expectedRedirect) "$($target.Name) must match exclusive input/polling/warp guards and native-ingress/icon/keymapping redirects"
    $runtime=(Get-ChildItem -LiteralPath (Join-Path $mixinDirectory '../runtime') -Filter '*ProbeRuntime.java'|ForEach-Object {Get-Content -LiteralPath $_.FullName -Raw}) -join "`n"
    Assert-True ($runtime -match ('"cancellableInjectionCount",\s*'+$cancellableCount+'\)') -and $runtime -match ('"replacementInjectionCount",\s*'+$redirectCount+'\)')) "$($target.Name) production Hook counts must match actual sources"
    $windowHook = Get-Content -LiteralPath (Join-Path $mixinDirectory 'WindowMixin.java') -Raw
    $iconApi=if($target.Name.StartsWith('26.3-')){'SDL_SetWindowIcon'}else{'glfwSetWindowIcon'}
    Assert-True ($windowHook -match $iconApi -and $windowHook -match 'onVanillaWindowIcon') "$($target.Name) icon redirect must preserve actual Vanilla pixels"
    $titleApi=if($target.Name.StartsWith('26.3-')){'SDL_SetWindowTitle'}else{'glfwSetWindowTitle'}
    Assert-True ($windowHook -match $titleApi -and $windowHook -match 'onVanillaWindowTitle' -and $windowHook -notmatch 'cancellable') "$($target.Name) title and icon must decorate final native output, not cancel game updates"
    # These Vanilla private nested classes cannot be named as Java class literals.
    # Keep an exact file/target allowlist; no arbitrary string or third-party target is accepted.
    $privateUiTargets = @{
        'AdvancementTypeUiAccessor.java' = 'net.minecraft.client.gui.screens.advancements.AdvancementTabType'
        'MerchantTradeUiAccessor.java' = 'net.minecraft.client.gui.screens.inventory.MerchantScreen$TradeOfferButton'
        'BeaconPowerUiAccessor.java' = 'net.minecraft.client.gui.screens.inventory.BeaconScreen$BeaconPowerButton'
        'BeaconButtonUiAccessor.java' = 'net.minecraft.client.gui.screens.inventory.BeaconScreen$BeaconScreenButton'
        'BeaconConfirmUiMarker.java' = 'net.minecraft.client.gui.screens.inventory.BeaconScreen$BeaconConfirmButton'
        'BeaconCancelUiMarker.java' = 'net.minecraft.client.gui.screens.inventory.BeaconScreen$BeaconCancelButton'
        'ToastInstanceUiAccessor.java' = $(if($target.Name.StartsWith('1.')){'net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance'}else{'net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance'})
    }
    foreach ($file in $files) {
        $text = Get-Content -LiteralPath $file.FullName -Raw
        if ($text -match '@Mixin\s*\(\s*targets\s*=') {
            $match = [regex]::Match($text, '@Mixin\s*\(\s*targets\s*=\s*"([^"]+)"\s*\)')
            Assert-True ($privateUiTargets.ContainsKey($file.Name) -and $match.Success -and $match.Groups[1].Value -ceq $privateUiTargets[$file.Name]) "$($target.Name)/$($file.Name) private Vanilla target must match the exact UI allowlist"
            Assert-True ($text -notmatch '@Inject|@Redirect|@Overwrite') "$($target.Name)/$($file.Name) private UI adapter must not alter control flow"
            $invokers=@([regex]::Matches($text,'@Invoker\("([^"]+)"\)')|ForEach-Object {$_.Groups[1].Value})
            if($file.Name-eq'AdvancementTypeUiAccessor.java'){
                Assert-True (($invokers|Where-Object {$_-notin@('getX','getY','getWidth','getHeight')}).Count-eq0) "$($target.Name) private type invokers must be exact read-only geometry getters"
            }elseif($file.Name-eq'ToastInstanceUiAccessor.java'){
                $expected=if($target.Name.StartsWith('1.')){@('getVisibility')}else{@()}
                Assert-True (($invokers -join ',') -ceq ($expected -join ',')) "$($target.Name) Toast adapter only invokes the native read-only visibility interpolation"
            }else{Assert-True ($invokers.Count-eq0) "$($target.Name)/$($file.Name) private UI adapter is Accessor/marker only"}
        } else {
            Assert-True ($text -match 'import (net\.minecraft\.|com\.mojang\.blaze3d\.)') "$($target.Name)/$($file.Name) must target Minecraft, not a third-party Mod"
        }
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
