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
    $operatorFiles = @('KeyboardHandlerMixin.java', 'MouseHandlerMixin.java', 'MinecraftMixin.java', 'WindowMixin.java', 'InputConstantsMixin.java', 'SDLEventHandlerMixin.java', 'WorldGestureMixin.java', 'PlayerCommandMixin.java', 'ConnectionMixin.java', 'CommandCompletionConnectionMixin.java')
    $drawFiles = @('HudVitalIconsMixin.java')
    if ($target.Name -eq '1.20.1-forge') { $drawFiles += 'ForgeVitalIconsMixin.java' }
    $drawCode = ($files | Where-Object Name -in $drawFiles | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -join "`n"
    Assert-True (([regex]::Matches($drawCode, '@Redirect')).Count -eq 5 -and $drawCode -notmatch 'cancellable\s*=\s*true|\.cancel\s*\(|@Overwrite|@Modify') "$($target.Name) exact native HUD draw delegates cannot cancel or modify arguments"
    $drawTest = Join-Path $repositoryRoot "versions/$($target.Name)/src/test/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/HudDrawDelegationTest.java"
    Assert-True (Test-Path -LiteralPath $drawTest) "$($target.Name) must check compiled HUD handlers for one unchanged original draw before observation"
    $observationCode = ($files | Where-Object { $_.Name -notin $operatorFiles -and $_.Name -notin $drawFiles } | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -join "`n"
    Assert-True ($observationCode -notmatch 'cancellable\s*=\s*true|\.cancel\s*\(|@Redirect') "$($target.Name) observation Hooks must not change control flow"
    Assert-True ($combined -notmatch '@ModifyArg|@ModifyArgs|@ModifyVariable|@ModifyConstant') "$($target.Name) must not use unreviewed replacement mechanisms"
    $cancellableCount = ([regex]::Matches($combined, 'cancellable\s*=\s*true')).Count
    $redirectCount = ([regex]::Matches($combined, '@Redirect')).Count
    $expectedCancellable=if($target.Name.StartsWith('26.3-')){26}elseif($target.Name.StartsWith('26.')){23}else{21}
    $expectedRedirect=if($target.Name.StartsWith('26.3-')){9}else{10}
    Assert-True ($cancellableCount -eq $expectedCancellable -and $redirectCount -eq $expectedRedirect) "$($target.Name) must match exclusive input/polling/warp guards and native-ingress/icon/keymapping redirects"
    $runtime=(Get-ChildItem -LiteralPath (Join-Path $mixinDirectory '../runtime') -Filter '*ProbeRuntime.java'|ForEach-Object {Get-Content -LiteralPath $_.FullName -Raw}) -join "`n"
    $worldHook=Get-Content -LiteralPath (Join-Path $mixinDirectory 'WorldGestureMixin.java') -Raw
    Assert-True ($worldHook -match '@Mixin\(MultiPlayerGameMode.class\)' -and $worldHook -match 'allowSemanticWorldInput' -and $worldHook -notmatch '@Redirect|@Overwrite') "$($target.Name) world guards must remain native call admission, not replacement actions"
    $commandEntry=Get-Content -LiteralPath (Join-Path $mixinDirectory 'PlayerCommandMixin.java') -Raw
    $commandPacket=Get-Content -LiteralPath (Join-Path $mixinDirectory 'ConnectionMixin.java') -Raw
    Assert-True (([regex]::Matches($commandEntry,'cancellable\s*=\s*true')).Count-eq2-and$commandEntry-match'allowPlayerCommand'-and$commandEntry-notmatch'@Redirect|@Overwrite') "$($target.Name) command entry/link hooks must only admit native submission"
    Assert-True (([regex]::Matches($commandPacket,'cancellable\s*=\s*true')).Count-eq1-and$commandPacket-match'allowPlayerCommandPacket'-and$commandPacket-notmatch'@Redirect|@Overwrite') "$($target.Name) final packet guard must only admit native command packets"
    $commandAdapter=Get-Content -LiteralPath (Join-Path $mixinDirectory '../runtime/NativePlayerCommands.java') -Raw
    $completionHook=Get-Content -LiteralPath (Join-Path $mixinDirectory 'CommandCompletionConnectionMixin.java') -Raw
    $completionBridge=Get-Content -LiteralPath (Join-Path $mixinDirectory '../runtime/NativeCommandSuggestions.java') -Raw
    Assert-True (([regex]::Matches($completionHook,'cancellable\s*=\s*true')).Count-eq1-and$completionHook-match'requests.owns\(packet.(id|getId)\(\)\)'-and$completionHook-match'completeCustomSuggestions'-and$completionHook-notmatch'@Redirect|@Overwrite') "$($target.Name) completion hook may consume only issued Agent responses after native thread routing"
    Assert-True ($completionBridge-match'requireHumanIdle'-and$completionBridge-match'pendingSuggestions'-and$completionBridge-match'ServerboundCommandSuggestionPacket'-and$completionBridge-notmatch'\.cancel\(|completeCustomSuggestions\(') "$($target.Name) Agent completion cannot own or cancel the human future"
    $toastReset=Get-Content -LiteralPath (Join-Path $mixinDirectory 'SystemToastResetMixin.java') -Raw
    Assert-True ($toastReset-match'@Mixin\(SystemToast.class\)'-and$toastReset-match'@Inject'-and$toastReset-match'reset\('-and$toastReset-match'HEAD'-and$toastReset-notmatch'cancellable\s*=\s*true|\.cancel\s*\(|@Redirect|@Overwrite') "$($target.Name) native Toast reset revision must remain passive and cannot suppress or replace game updates"
    Assert-True ($runtime-match'system_toast_reset_revision'-and$runtime-match'passive_native_reset_revision') "$($target.Name) passive Toast reset Hook must be disclosed independently of cancellable input Hooks"
    Assert-True ($runtime-match'ServerboundChatCommandPacket'-and$runtime-match'NativePlayerCommands.configuration\(packet\)'-and$runtime-match'gate.allowConfiguration\('-and$commandAdapter-match'ServerboundSetCommandBlockPacket'-and$commandAdapter-match'ServerboundSetCommandMinecartPacket') "$($target.Name) final typed chat/block/minecart admission must remain in production adapters"
    Assert-True ($runtime -match 'worldTargeting.holdActive\(\)' -and $runtime -match 'worldTargeting.permit\(kind\)') "$($target.Name) semantic veto must only apply while the Runtime owns a bounded gesture"
    Assert-True ($runtime -match ('"cancellableInjectionCount",\s*'+$cancellableCount+'\)') -and $runtime -match ('"replacementInjectionCount",\s*'+$redirectCount+'\)')) "$($target.Name) production Hook counts must match actual sources"
    Assert-True ($runtime -match '"passiveDrawDelegationCount",\s*5\)' -and $runtime -match 'operator_control_and_exact_hud_draw_delegation' -and $runtime -match 'passive_draw_observation') "$($target.Name) production manifest must disclose passive draw delegation separately from operator control"
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
