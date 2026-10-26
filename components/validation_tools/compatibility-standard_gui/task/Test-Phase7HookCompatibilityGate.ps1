[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '../../../..')).Path
$gate = Join-Path $PSScriptRoot 'Invoke-Phase7HookCompatibilityGate.ps1'
. $gate
$fixture = Join-Path $root ('build/validation-fixtures/hook-gate-' + [guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($fixture)
$targets = @('1.20.1-forge', '1.21.1-neoforge', '26.1.2-neoforge', '26.3-neoforge', '26.3-fabric')
$cases = [Collections.Generic.List[object]]::new()

function Copy-HookFixtureFile {
    param([string]$Relative)
    $destination = Join-Path $fixture $Relative
    [void][IO.Directory]::CreateDirectory((Split-Path -Parent $destination))
    Copy-Item -LiteralPath (Join-Path $root $Relative) -Destination $destination
}

Copy-HookFixtureFile 'components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core/UiToastDraw.java'

foreach ($target in $targets) {
    $source = if ($target.EndsWith('fabric')) { 'client' } else { 'main' }
    $base = "versions/$target/src/$source/java/io/github/recrivenvi/minecraftprotocol/probe"
    $mixins = @(Get-ChildItem -LiteralPath (Join-Path $root "$base/mixin") -Filter '*.java' -File)
    if ($target -eq '26.3-fabric') {
        $mixins += Get-Item -LiteralPath (Join-Path $root 'versions/26.3-fabric/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/mixin/DebugLoadedChunkAccessMixin.java')
        Copy-HookFixtureFile 'versions/26.3-fabric/src/main/resources/minecraft_protocol_probe.server.mixins.json'
    }
    foreach ($file in $mixins) { Copy-HookFixtureFile ([IO.Path]::GetRelativePath($root, $file.FullName)) }
    foreach ($file in Get-ChildItem -LiteralPath (Join-Path $root "$base/runtime") -Filter '*.java' -File | Where-Object { $_.Name -like '*ProbeRuntime.java' -or $_.Name -in @('NativePlayerCommands.java', 'NativeCommandSuggestions.java', 'UiNativeText.java', 'UiPresentationSnapshot.java') }) {
        Copy-HookFixtureFile ([IO.Path]::GetRelativePath($root, $file.FullName))
    }
    $config = if ($source -eq 'client') { 'minecraft_protocol_probe.client.mixins.json' } else { 'minecraft_protocol_probe.mixins.json' }
    Copy-HookFixtureFile "versions/$target/src/$source/resources/$config"
    $configPath = Join-Path $fixture "versions/$target/src/$source/resources/$config"
    $configInput = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
    $configInput.client = @($mixins | Where-Object { $_.BaseName -notin @($configInput.mixins) -and -not ($target -eq '26.3-fabric' -and $_.BaseName -eq 'DebugLoadedChunkAccessMixin') } | ForEach-Object BaseName)
    [IO.File]::WriteAllText($configPath, ($configInput | ConvertTo-Json -Depth 10), [Text.UTF8Encoding]::new($false))
    foreach ($test in @('HudDrawDelegationTest.java', 'HudPresentationTest.java', 'HudCameraPresentationTest.java', 'LateGuiPresentationTest.java', 'LegacyNauseaPresentationTest.java', 'UiToastDrawTest.java', 'PlayerTabPresentationTest.java')) {
        $relative = "versions/$target/src/test/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/$test"
        if (Test-Path -LiteralPath (Join-Path $root $relative)) { Copy-HookFixtureFile $relative }
    }
    # Registration and count declarations are fixture inputs, not a Runtime/configuration acceptance.
    $texts = @($mixins | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw })
    $combined = $texts -join "`n"
    $contracts = Get-PassiveDrawContracts $target
    $passive = 0
    foreach ($file in $mixins | Where-Object { $contracts.ContainsKey($_.Name) }) {
        $passive += Assert-PassiveDrawSource "$target/$($file.Name)" (Get-Content -LiteralPath $file.FullName -Raw) $contracts[$file.Name]
    }
    $total = ([regex]::Matches($combined, '@Redirect\b|@ModifyVariable\b')).Count
    $passiveWorld = if($target.StartsWith('1.')) { 1 } else { 0 }
    $runtime = @(Get-ChildItem -LiteralPath (Join-Path $fixture "$base/runtime") -Filter '*ProbeRuntime.java' -File)
    Assert-True ($runtime.Count -eq 1) "$target fixture needs exactly one real Runtime source"
    $code = Get-Content -LiteralPath $runtime[0].FullName -Raw
    $code = $code -replace '("replacementInjectionCount",\s*)\d+', ('${1}' + $total)
    $code = $code -replace '("passiveDrawDelegationCount",\s*)\d+', ('${1}' + $passive)
    if ($code -match '"operatorReplacementCount",') {
        $code = $code -replace '("operatorReplacementCount",\s*)\d+', ('${1}' + ($total - $passive - $passiveWorld))
    } else {
        $code = $code -replace '(json.addProperty\("replacementInjectionCount",\s*\d+\);)', ('$1' + "`n" + 'json.addProperty("operatorReplacementCount", ' + ($total - $passive - $passiveWorld) + ');')
    }
    [IO.File]::WriteAllText($runtime[0].FullName, $code, [Text.UTF8Encoding]::new($false))
}

function Invoke-HookFixtureCase {
    param([string]$Case, [string]$ExpectedError = '')
    $failure = $null
    $result = $null
    try { $result = & $gate -RepositoryRoot $fixture } catch { $failure = $_.Exception.Message }
    if ($ExpectedError) {
        if (-not $failure -or $failure -notlike ('*' + $ExpectedError + '*')) { throw "$Case did not reject the expected contract violation: $failure" }
    } else {
        if ($failure) { throw "$Case unexpectedly failed: $failure" }
        if ($result.Result -ne 'PASS' -or @($result.Targets).Count -ne 5) { throw "$Case returned an incomplete result" }
        foreach ($target in $result.Targets) {
            if ($target.Replacement -ne $target.OperatorReplacement + $target.PassiveDrawDelegation + $target.PassiveWorldDelegation -or $target.OperatorReplacement -ne $target.OperatorRedirect + $target.CursorArgumentReplacement -or $target.CompiledDelegationTests -ne 'REQUIRED_NOT_EXECUTED_BY_STATIC_GATE') { throw "$Case returned misleading Hook classification" }
        }
    }
    $cases.Add([pscustomobject]@{ Case = $Case; Check = 'PASS'; Expected = $(if ($ExpectedError) { 'FAIL' } else { 'PASS' }); Error = $failure })
}

function With-HookFixtureText {
    param([string]$Relative, [scriptblock]$Transform, [scriptblock]$Check)
    $path = Join-Path $fixture $Relative
    $original = [IO.File]::ReadAllBytes($path)
    try {
        $text = [IO.File]::ReadAllText($path)
        $changed = & $Transform $text
        if ($changed -ceq $text) { throw "Mutation did not change $Relative" }
        [IO.File]::WriteAllText($path, $changed, [Text.UTF8Encoding]::new($false))
        & $Check
    } finally { [IO.File]::WriteAllBytes($path, $original) }
}

Invoke-HookFixtureCase 'five-current-source-copies-with-explicit-manifest-counts'
$reference = 'versions/26.3-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe'
With-HookFixtureText "$reference/mixin/MinecraftMixin.java" { param($s) $s.Replace('AgentInputContext.routed(mouse::handleAccumulatedMovement);', 'mouse.handleAccumulatedMovement();') } { Invoke-HookFixtureCase 'mouse-frame-loses-logical-origin' 'native mouse-frame delegate' }
$camera = "$reference/mixin/HudCameraPresentationMixin.java"
With-HookFixtureText $camera { param($s) $s.Replace('graphics.blitSprite(pipeline, sprite, x, y, width, height);', 'graphics.blitSprite(pipeline, sprite, x + 1, y, width, height);') } { Invoke-HookFixtureCase 'changed-native-draw-argument' 'forward unchanged arguments' }
With-HookFixtureText $camera { param($s) $s.Replace('graphics.blitSprite(pipeline, sprite, x, y, width, height);', '') } { Invoke-HookFixtureCase 'missing-native-draw' 'forward unchanged arguments' }
With-HookFixtureText $camera { param($s) $s.Replace('graphics.blitSprite(pipeline, sprite, x, y, width, height);', 'graphics.blitSprite(pipeline, sprite, x, y, width, height); graphics.blitSprite(pipeline, sprite, x, y, width, height);') } { Invoke-HookFixtureCase 'duplicate-native-draw' 'forward unchanged arguments' }
With-HookFixtureText $camera { param($s) $s.Replace('graphics.blitSprite(pipeline, sprite, x, y, width, height);', 'UiPresentationSnapshot.activeHudPresentation(); graphics.blitSprite(pipeline, sprite, x, y, width, height);') } { Invoke-HookFixtureCase 'observation-before-original-draw' 'forward unchanged arguments' }
With-HookFixtureText $camera { param($s) $s.Replace('Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(', 'Lthird/party/GuiGraphicsExtractor;blitSprite(') } { Invoke-HookFixtureCase 'third-party-draw-target' 'exact reviewed descriptor multiset' }
With-HookFixtureText $camera { param($s) $s.Replace('value = "INVOKE",', 'value = "INVOKE", require = 0,') } { Invoke-HookFixtureCase 'optional-native-draw-injection' 'one required native invocation' }
With-HookFixtureText $camera { param($s) $s + "`n@Redirect(method = `"other`", at = @At(`"HEAD`"))`n" } { Invoke-HookFixtureCase 'extra-unreviewed-draw-handler' 'only its reviewed native draw handlers' }
$playerTab = "$reference/mixin/PlayerTabPresentationMixin.java"
With-HookFixtureText $playerTab { param($s) $s.Replace('Lnet/minecraft/util/FormattedCharSequence;III)V', 'Lnet/minecraft/network/chat/Component;III)V') } { Invoke-HookFixtureCase 'player-tab-keeps-two-distinct-sequence-draws' 'exact reviewed descriptor multiset' }
With-HookFixtureText $playerTab { param($s) $s.Replace('graphics.text(font, text, x, y, color);', 'graphics.text(font, text, x + 1, y, color);') } { Invoke-HookFixtureCase 'player-tab-keeps-original-text-arguments' 'forward unchanged arguments' }
With-HookFixtureText $playerTab { param($s) $s.Replace('import net.minecraft.client.gui.components.PlayerTabOverlay;', 'import third.party.PlayerTabOverlay;') } { Invoke-HookFixtureCase 'player-tab-requires-the-native-target' 'public UI target/import must be exactly' }
$playerTabTest = 'versions/26.3-neoforge/src/test/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/PlayerTabPresentationTest.java'
With-HookFixtureText $playerTabTest { param($s) $s.Replace('assertEquals(1, nativeDraws, method.name)', 'assertEquals(2, nativeDraws, method.name)') } { Invoke-HookFixtureCase 'player-tab-asm-cannot-accept-double-native-draws' 'real ASM coverage' }
With-HookFixtureText $playerTabTest { param($s) $s.Replace('instructions.get(arguments.length)', 'instructions.get(arguments.length + 1)') } { Invoke-HookFixtureCase 'player-tab-asm-native-call-must-follow-original-arguments' 'real ASM coverage' }
$forgeSleep = 'versions/1.20.1-forge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/mixin/ForgeHudSleepPresentationMixin.java'
With-HookFixtureText $forgeSleep { param($s) $s.Replace('graphics.fill(renderType, left, top, right, bottom, color);', 'graphics.fill(renderType, left, top, right, bottom, color + 1);') } { Invoke-HookFixtureCase 'forge-sleep-keeps-original-native-fill' 'forward unchanged arguments' }
With-HookFixtureText $forgeSleep { param($s) $s.Replace('import net.minecraftforge.client.gui.overlay.ForgeGui;', 'import third.party.ForgeGui;') } { Invoke-HookFixtureCase 'forge-sleep-requires-the-native-loader-overlay' 'public UI target/import must be exactly' }
With-HookFixtureText $forgeSleep { param($s) $s.Replace('remap = false', 'remap = true') } { Invoke-HookFixtureCase 'forge-sleep-retains-native-remapping-boundary' 'exact Forge sleep method' }
$forgeDebug = 'versions/1.20.1-forge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/mixin/DebugTextPresentationMixin.java'
With-HookFixtureText $forgeDebug { param($s) $s.Replace('import net.minecraftforge.client.gui.overlay.ForgeGui;', 'import third.party.ForgeGui;') } { Invoke-HookFixtureCase 'forge-debug-requires-actual-loader-draw-target' 'public UI target/import must be exactly' }
With-HookFixtureText $forgeDebug { param($s) $s.Replace('method = "renderHUDText"', 'method = "renderLines"') } { Invoke-HookFixtureCase 'forge-debug-rejects-unreachable-vanilla-selector' 'exact Forge text method' }
$forgeMessage = 'versions/1.20.1-forge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/mixin/ForgeHudMessagePresentationMixin.java'
With-HookFixtureText $forgeMessage { param($s) $s.Replace('remap = false', 'remap = true') } { Invoke-HookFixtureCase 'forge-overlay-keeps-native-remapping-boundary' 'exact Forge text method' }
With-HookFixtureText $forgeMessage { param($s) $s.Replace('graphics.drawString(font, text, x, y, color);', 'graphics.drawString(font, text, x + 1, y, color);') } { Invoke-HookFixtureCase 'forge-overlay-keeps-original-actual-draw' 'forward unchanged arguments' }
With-HookFixtureText 'versions/1.20.1-forge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/mixin/HudCameraPresentationMixin.java' { param($s) $s.Replace('Lnet/minecraft/resources/ResourceLocation;IIIIII)V', 'Lnet/minecraft/resources/ResourceLocation;IIII)V') } { Invoke-HookFixtureCase 'forge-camera-retains-exact-atlas-overload' 'forward unchanged arguments' }
With-HookFixtureText "$reference/mixin/GuiLatePresentationMixin.java" { param($s) $s + "`n@Redirect(method = `"other`", at = @At(`"HEAD`"))`n" } { Invoke-HookFixtureCase 'redirect-in-passive-inject-adapter' 'observation Hooks must not change control flow' }
With-HookFixtureText "$reference/mixin/GuiLatePresentationMixin.java" { param($s) $s.Replace('import net.minecraft.client.gui.Gui;', 'import third.party.Gui;') } { Invoke-HookFixtureCase 'public-native-class-lookalike' 'public UI target/import must be exactly' }
With-HookFixtureText "$reference/mixin/ChatBackgroundPresentationMixin.java" { param($s) $s.Replace('net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess', 'third.party.ChatComponent$DrawingBackgroundGraphicsAccess') } { Invoke-HookFixtureCase 'private-draw-native-target' 'private draw target must match' }
With-HookFixtureText "$reference/mixin/ChatBackgroundPresentationMixin.java" { param($s) $s + "`n@Accessor(`"hidden`")`n" } { Invoke-HookFixtureCase 'private-draw-is-not-an-accessor-exemption' 'private draw target must match' }
With-HookFixtureText "$reference/mixin/ChatBackgroundPresentationMixin.java" { param($s) $s.Replace('method = "handleMessage"', 'method = "unreviewed"') } { Invoke-HookFixtureCase 'private-draw-native-method-not-reviewed' 'only its reviewed native method' }
With-HookFixtureText "$reference/mixin/ToastResidentPresentationMixin.java" { param($s) $s.Replace('} else toast.extractRenderState(graphics, font, elapsed);', '} else toast.extractRenderState(graphics, font, elapsed + 1);') } { Invoke-HookFixtureCase 'resident-toast-fallback-arguments' 'capture/fallback branches' }
With-HookFixtureText 'components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core/UiToastDraw.java' { param($s) $s.Replace('nativeDraw.run();', 'nativeDraw.run(); nativeDraw.run();') } { Invoke-HookFixtureCase 'resident-toast-double-callback' 'invoke native draw exactly once' }
With-HookFixtureText "$reference/runtime/NativePlayerCommands.java" { param($s) $s.Replace('ServerboundChatCommandPacket command', 'UnreviewedCommandPacket command') } { Invoke-HookFixtureCase 'delegated-native-command-packet-guard-removed' 'final typed chat/block/minecart admission' }
With-HookFixtureText 'versions/1.20.1-forge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/mixin/HudPresentationMixin.java' { param($s) $s.Replace('return result;', 'return result + 1;') } { Invoke-HookFixtureCase 'native-integer-return-value-changed' 'preserve the native draw return value' }
With-HookFixtureText 'versions/26.3-neoforge/src/test/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/HudCameraPresentationTest.java' { param($s) $s.Replace('assertSame(draw, instructions.get(', 'assertNotNull(draw); unused(') } { Invoke-HookFixtureCase 'compiled-argument-and-order-coverage-removed' 'real ASM coverage' }
With-HookFixtureText "$reference/runtime/NeoForgeProbeRuntime.java" { param($s) $s -replace '("passiveDrawDelegationCount",\s*)\d+', '${1}5' } { Invoke-HookFixtureCase 'passive-draw-count-hidden-in-manifest' 'disclose passive draw delegation separately' }
With-HookFixtureText "$reference/mixin/WindowMixin.java" { param($s) $s.Replace('ordinal = 0', 'ordinal = 1') } { Invoke-HookFixtureCase 'cursor-argument-guard-still-exact' 'exact host cursor argument guard' }
With-HookFixtureText "$reference/mixin/WindowMixin.java" { param($s) $s + "`n@ModifyVariable(method = `"other`", at = @At(`"HEAD`"))`n" } { Invoke-HookFixtureCase 'other-modify-variable-remains-forbidden' 'unreviewed replacement mechanisms' }
With-HookFixtureText 'versions/26.3-neoforge/src/main/resources/minecraft_protocol_probe.mixins.json' { param($s) $j = $s | ConvertFrom-Json; $j.client[0] = $j.client[1]; $j | ConvertTo-Json -Depth 10 } { Invoke-HookFixtureCase 'same-count-config-duplicate' 'register each audited source exactly once' }
$forgeBase = 'versions/1.20.1-forge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe'
$legacyNeoBase = 'versions/1.21.1-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe'
foreach ($legacy in @($forgeBase, $legacyNeoBase)) {
    With-HookFixtureText "$legacy/mixin/GameModeScreenRenderMixin.java" { param($s) $s.Replace('method = "renderWithTooltip"', 'method = "render"') } { Invoke-HookFixtureCase 'deferred-game-mode-native-public-selector' 'actual public Screen render invocation' }
    With-HookFixtureText "$legacy/mixin/GameModeScreenRenderMixin.java" { param($s) $s.Replace('screen, graphics, x, y, partialTick', 'screen, graphics, x + 1, y, partialTick') } { Invoke-HookFixtureCase 'deferred-game-mode-original-coordinates' 'unchanged native render arguments' }
}
$recipeToast = "$forgeBase/mixin/RecipeToastUiAccessor.java"
With-HookFixtureText $recipeToast { param($s) $s.Replace('import net.minecraft.client.gui.components.toasts.RecipeToast;', 'import third.party.RecipeToast;') } { Invoke-HookFixtureCase 'recipe-toast-accessor-retains-native-owner' 'public UI target/import must be exactly' }
With-HookFixtureText $recipeToast { param($s) $s.Replace('@Accessor("recipes")', '@Accessor("items")') } { Invoke-HookFixtureCase 'recipe-toast-accessor-retains-exact-read-field' 'only read its native recipe list' }
With-HookFixtureText "$legacyNeoBase/mixin/RecipeToastUiAccessor.java" { param($s) $s.Replace('List<?> minecraftProtocolProbe$recipes();', 'void minecraftProtocolProbe$recipes(List<?> recipes);') } { Invoke-HookFixtureCase 'recipe-toast-accessor-cannot-become-a-setter' 'only read its native recipe list' }
$forgeTab = "$forgeBase/mixin/PlayerTabPresentationMixin.java"
$legacyNeoTab = "$legacyNeoBase/mixin/PlayerTabPresentationMixin.java"
With-HookFixtureText $forgeTab { param($s) $s.Replace('Lnet/minecraft/util/FormattedCharSequence;III)I', 'Lnet/minecraft/network/chat/Component;III)I') } { Invoke-HookFixtureCase 'legacy-forge-player-tab-keeps-two-sequence-sites' 'exact reviewed descriptor multiset' }
With-HookFixtureText $legacyNeoTab { param($s) $s.Replace('Lnet/minecraft/network/chat/Component;III)I', 'Ljava/lang/String;III)I') } { Invoke-HookFixtureCase 'legacy-neoforge-player-tab-keeps-component-score-overload' 'exact reviewed descriptor multiset' }
With-HookFixtureText $forgeTab { param($s) $s.Replace('Lnet/minecraft/resources/ResourceLocation;IIIIII)V', 'Lnet/minecraft/resources/ResourceLocation;IIII)V') } { Invoke-HookFixtureCase 'legacy-forge-player-tab-keeps-native-atlas-overload' 'forward unchanged arguments' }
With-HookFixtureText $legacyNeoTab { param($s) $s.Replace('return result;', 'return result + 1;') } { Invoke-HookFixtureCase 'legacy-player-tab-cannot-change-native-integer-return' 'preserve the native draw return value' }
$legacyTest = 'versions/1.21.1-neoforge/src/test/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/PlayerTabPresentationTest.java'
With-HookFixtureText $legacyTest { param($s) $s.Replace('assertEquals(stored.var, loaded.var);', 'assertNotNull(stored);') } { Invoke-HookFixtureCase 'legacy-player-tab-asm-must-prove-original-return-local' 'real ASM coverage' }
With-HookFixtureText $legacyTest { param($s) $s -replace 'assertEquals\(\s*1,\s*java\.util\.Arrays\.stream\(method\.instructions\.toArray\(\)\)\s*\.filter\(\s*value\s*->\s*value\s+instanceof\s+MethodInsnNode\s+call', 'assertEquals(2, java.util.Arrays.stream(method.instructions.toArray()).filter(value -> value instanceof MethodInsnNode call' } { Invoke-HookFixtureCase 'legacy-player-tab-asm-must-reject-double-native-draw' 'real ASM coverage' }
$scissor = "$forgeBase/mixin/GuiGraphicsScissorMixin.java"
With-HookFixtureText $scissor { param($s) $s.Replace('at = @At("RETURN")', 'at = @At("HEAD")') } { Invoke-HookFixtureCase 'legacy-scissor-observes-completed-native-state-only' 'exact completed native constructor/applyScissor descriptor' }
With-HookFixtureText $scissor { param($s) $s.Replace('applyScissor(Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V', 'applyScissor()V') } { Invoke-HookFixtureCase 'legacy-scissor-keeps-exact-native-rectangle-descriptor' 'exact completed native constructor/applyScissor descriptor' }
With-HookFixtureText $scissor { param($s) $s.Replace('this, rectangle, UiNativeText.ScissorOrigin.NATIVE_APPLY_SCISSOR_RETURN', 'new Object(), rectangle, UiNativeText.ScissorOrigin.NATIVE_APPLY_SCISSOR_RETURN') } { Invoke-HookFixtureCase 'legacy-scissor-sample-must-belong-to-this-graphics' 'same graphics owner and unchanged completed native scissor' }
With-HookFixtureText $scissor { param($s) $s.Replace('this, rectangle, UiNativeText.ScissorOrigin.NATIVE_APPLY_SCISSOR_RETURN', 'this, null, UiNativeText.ScissorOrigin.NATIVE_APPLY_SCISSOR_RETURN') } { Invoke-HookFixtureCase 'legacy-scissor-cannot-invent-an-empty-applied-rectangle' 'same graphics owner and unchanged completed native scissor' }
With-HookFixtureText $scissor { param($s) $s.Replace('private volatile UiNativeText.ScissorSample minecraftProtocolProbe$scissor;', 'private volatile UiNativeText.ScissorSample minecraftProtocolProbe$scissor = new UiNativeText.ScissorSample(this, null, UiNativeText.ScissorOrigin.NATIVE_INITIAL_EMPTY_STACK);') } { Invoke-HookFixtureCase 'legacy-scissor-cannot-assume-state-before-construction-return' 'sample must start unknown' }
With-HookFixtureText "$legacyNeoBase/runtime/UiNativeText.java" { param($s) $s.Replace('sample.owner() == owner', 'sample.owner() != null') } { Invoke-HookFixtureCase 'legacy-scissor-decoder-cannot-accept-other-graphics-owner' 'reject unknown scissor samples and samples owned by other graphics' }
With-HookFixtureText $legacyTest { param($s) $s.Replace('"java/util/ArrayDeque.<init>()V"', '"unverified/Stack.<init>()V"') } { Invoke-HookFixtureCase 'legacy-scissor-native-empty-stack-fact-must-remain-tested' 'native empty-stack, push/pop-to-apply' }
With-HookFixtureText $scissor { param($s) $s.Replace('import net.minecraft.client.gui.GuiGraphics;', 'import third.party.GuiGraphics;') } { Invoke-HookFixtureCase 'legacy-scissor-must-target-real-native-graphics' 'public UI target/import must be exactly' }
With-HookFixtureText 'versions/26.1.2-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/mixin/PlayerTabPresentationMixin.java' { param($s) $s.Replace('Lcom/mojang/blaze3d/pipeline/RenderPipeline;', 'Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;') } { Invoke-HookFixtureCase 'modern-player-tab-retains-pre-sdl-native-pipeline' 'exact reviewed descriptor multiset' }
With-HookFixtureText 'components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core/UiToastDraw.java' { param($s) $s.Replace('package io.github.recrivenvi.minecraftprotocol.core;', 'package io.github.recrivenvi.minecraftprotocol.probe.runtime;') } { Invoke-HookFixtureCase 'resident-scope-cannot-reintroduce-a-split-target-package' 'shared core resident scope and compiled owner' }
With-HookFixtureText 'versions/26.1.2-neoforge/src/test/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/UiToastDrawTest.java' { param($s) $s.Replace('io/github/recrivenvi/minecraftprotocol/core/UiToastDraw', 'io/github/recrivenvi/minecraftprotocol/probe/runtime/UiToastDraw') } { Invoke-HookFixtureCase 'resident-asm-must-prove-the-actual-core-component-owner' 'shared core resident scope and compiled owner' }
$tooltipScope = "$forgeBase/mixin/GuiGraphicsTooltipPresentationMixin.java"
$tooltipDraw = "$forgeBase/mixin/TooltipPresentationMixin.java"
$nativeText = "$forgeBase/runtime/UiNativeText.java"
$lateTest = 'versions/1.20.1-forge/src/test/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/LateGuiPresentationTest.java'
With-HookFixtureText $tooltipScope { param($s) $s.Replace('Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;renderText(', 'Lthird/party/ClientTooltipComponent;renderText(') } { Invoke-HookFixtureCase 'tooltip-scope-requires-the-real-native-interface-owner' 'exact reviewed descriptor multiset' }
With-HookFixtureText $tooltipScope { param($s) $s.Replace('import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;', 'import third.party.ClientTooltipComponent;') } { Invoke-HookFixtureCase 'tooltip-scope-rejects-a-lookalike-component-import' 'unchanged typed native Tooltip call' }
With-HookFixtureText $tooltipDraw { param($s) $s.Replace('Lnet/minecraft/client/gui/Font;drawInBatch(', 'Lthird/party/Font;drawInBatch(') } { Invoke-HookFixtureCase 'tooltip-glyph-draw-requires-the-real-native-font-owner' 'exact reviewed descriptor multiset' }
With-HookFixtureText $tooltipScope { param($s) $s.Replace('component.renderText(font, x, y, pose, buffers)', 'component.renderText(font, x + 1, y, pose, buffers)') } { Invoke-HookFixtureCase 'tooltip-scope-forwards-the-original-native-coordinates' 'unchanged typed native Tooltip call' }
With-HookFixtureText $tooltipScope { param($s) $s.Replace('UiNativeText.tooltipDraw(', 'component.renderText(font, x, y, pose, buffers); UiNativeText.tooltipDraw(') } { Invoke-HookFixtureCase 'tooltip-scope-cannot-submit-an-extra-native-call' 'unchanged typed native Tooltip call' }
With-HookFixtureText $tooltipDraw { param($s) $s -replace '(int\s+result\s*=\s*font\.drawInBatch\()', 'UiNativeText.chatFamily(); $1' } { Invoke-HookFixtureCase 'tooltip-glyph-observation-cannot-precede-the-native-draw' 'forward unchanged arguments' }
With-HookFixtureText $tooltipDraw { param($s) $s -replace '(int\s+result\s*=\s*font\.drawInBatch\(\s*text,\s*x,\s*y,\s*color,\s*shadow,\s*pose,\s*buffers,\s*mode,\s*background,\s*light\s*\);)', '$1 font.drawInBatch(text, x, y, color, shadow, pose, buffers, mode, background, light);' } { Invoke-HookFixtureCase 'tooltip-glyph-draw-cannot-be-duplicated' 'forward unchanged arguments' }
With-HookFixtureText $tooltipDraw { param($s) $s.Replace('return result;', 'return result + 1;') } { Invoke-HookFixtureCase 'tooltip-glyph-draw-keeps-the-native-return-value' 'preserve the native draw return value' }
With-HookFixtureText $nativeText { param($s) $s.Replace('} finally {', '} catch (RuntimeException ignored) {') } { Invoke-HookFixtureCase 'tooltip-scope-must-clean-up-in-finally-not-only-on-success' 'restore its actual nested owner in finally' }
With-HookFixtureText $nativeText { param($s) $s -replace 'if\s*\(previous\s*==\s*null\)\s*TOOLTIP\.remove\(\);\s*else\s*TOOLTIP\.set\(previous\);', 'TOOLTIP.remove();' } { Invoke-HookFixtureCase 'tooltip-scope-must-restore-the-actual-outer-owner-after-reentry' 'restore its actual nested owner in finally' }
With-HookFixtureText $nativeText { param($s) $s.Replace('nativeDraw.run();', 'nativeDraw.run(); nativeDraw.run();') } { Invoke-HookFixtureCase 'tooltip-scope-must-invoke-the-native-callback-once' 'execute native draw exactly once' }
With-HookFixtureText $nativeText { param($s) $s.Replace('context.component() != component', 'context.component() == component') } { Invoke-HookFixtureCase 'tooltip-observer-must-reject-a-different-native-component' 'reject the wrong component and unknown graphics' }
With-HookFixtureText $nativeText { param($s) $s.Replace('import net.minecraft.client.gui.Font;', 'import third.party.Font;') } { Invoke-HookFixtureCase 'tooltip-decoder-cannot-replace-the-native-font-owner' 'reject the wrong component and unknown graphics' }
With-HookFixtureText $lateTest { param($s) $s -replace 'assertEquals\(\s*1,\s*wrapper\.methods\.stream\(\)\s*\.flatMap\(\s*method\s*->\s*Arrays\.stream\(method\.instructions\.toArray\(\)\)\)', 'assertEquals(2, wrapper.methods.stream().flatMap(method -> Arrays.stream(method.instructions.toArray()))' } { Invoke-HookFixtureCase 'tooltip-wrapper-asm-must-prove-the-single-actual-interface-draw' 'real ASM coverage' }
With-HookFixtureText $lateTest { param($s) $s.Replace('assertFalse(scope.tryCatchBlocks.isEmpty());', 'assertTrue(scope.tryCatchBlocks.isEmpty());') } { Invoke-HookFixtureCase 'tooltip-wrapper-asm-must-prove-the-exceptional-cleanup-handler' 'exceptional and nested-owner cleanup' }
With-HookFixtureText "$forgeBase/mixin/GuiLatePresentationMixin.java" { param($s) $s.Replace('method = "render"', 'method = "extractRenderState"') } { Invoke-HookFixtureCase 'legacy-late-frame-must-enclose-the-actual-native-renderer' 'exact complete native GUI boundary' }
With-HookFixtureText 'versions/26.1.2-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/mixin/GuiLatePresentationMixin.java' { param($s) $s.Replace('method = "extractGui"', 'method = "extractRenderState"') } { Invoke-HookFixtureCase 'pre-sdl-late-frame-must-include-screen-and-deferred-subtitle-extraction' 'exact complete native GUI boundary' }
With-HookFixtureText "$forgeBase/runtime/UiPresentationSnapshot.java" { param($s) $s -replace 'return\s+Minecraft\.getInstance\(\)\.gameRenderer\s+instanceof\s+GuiPresentationSource', 'return Minecraft.getInstance().gui instanceof GuiPresentationSource' } { Invoke-HookFixtureCase 'legacy-late-reader-must-use-the-same-outer-renderer-owner' 'read its actual bound native owner' }
With-HookFixtureText "$forgeBase/mixin/GuiLatePresentationMixin.java" { param($s) $s.Replace('new UiPresentationSnapshot.RenderedHud(128)', 'new UiPresentationSnapshot.RenderedHud(129)') } { Invoke-HookFixtureCase 'late-frame-budget-is-an-explicit-independent-contract' 'independent 128-node budget' }
$forgeBoss = "$forgeBase/mixin/BossBarPresentationMixin.java"
With-HookFixtureText $forgeBoss { param($s) $s.Replace('Lnet/minecraft/resources/ResourceLocation;IIIIII)V', 'Lnet/minecraft/resources/ResourceLocation;IIII)V') } { Invoke-HookFixtureCase 'legacy-boss-must-retain-the-real-atlas-overload' 'forward unchanged arguments' }
With-HookFixtureText $forgeBoss { param($s) $s.Replace('world/BossEvent;II)V', 'world/BossEvent;I)V') } { Invoke-HookFixtureCase 'legacy-boss-must-retain-the-exact-inner-draw-selector' 'exact reviewed native method selector' }
With-HookFixtureText $forgeBoss { param($s) $s.Replace('graphics.blit(texture, x, y, u, v, width, height);', 'graphics.blit(texture, x, y, u + 1, v, width, height);') } { Invoke-HookFixtureCase 'legacy-boss-must-forward-the-real-atlas-uv' 'forward unchanged arguments' }
With-HookFixtureText $forgeBoss { param($s) $s.Replace('graphics.blit(texture, x, y, u, v, width, height);', 'graphics.blit(texture, x, y, u, v, width, height); graphics.blit(texture, x, y, u, v, width, height);') } { Invoke-HookFixtureCase 'legacy-boss-cannot-double-the-native-draw' 'forward unchanged arguments' }
With-HookFixtureText $forgeBoss { param($s) $s.Replace('var frame =', 'boss.getProgress(); var frame =') } { Invoke-HookFixtureCase 'boss-draw-observation-cannot-read-raw-boss-state' 'only its actual late-frame native Boss draw arguments' }
With-HookFixtureText "$legacyNeoBase/mixin/BossBarPresentationMixin.java" { param($s) $s.Replace('Lnet/minecraft/resources/ResourceLocation;IIIIIIII)V', 'Lnet/minecraft/resources/Identifier;IIIIIIII)V') } { Invoke-HookFixtureCase 'legacy-neoforge-boss-must-retain-native-resource-location' 'exact reviewed descriptor multiset' }
With-HookFixtureText 'versions/26.1.2-neoforge/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/mixin/BossBarPresentationMixin.java' { param($s) $s.Replace('Lcom/mojang/blaze3d/pipeline/RenderPipeline;', 'Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;') } { Invoke-HookFixtureCase 'pre-sdl-boss-must-retain-native-pipeline' 'exact reviewed descriptor multiset' }
With-HookFixtureText 'versions/26.3-fabric/src/client/java/io/github/recrivenvi/minecraftprotocol/probe/mixin/BossBarPresentationMixin.java' { param($s) $s.Replace('import net.minecraft.client.gui.components.BossHealthOverlay;', 'import third.party.BossHealthOverlay;') } { Invoke-HookFixtureCase 'fabric-boss-must-retain-native-overlay-owner' 'public UI target/import must be exactly' }
With-HookFixtureText $lateTest { param($s) $s -replace 'assertEquals\(\s*2,\s*Arrays\.stream\(overload\.instructions\.toArray\(\)\)', 'assertEquals(1, Arrays.stream(overload.instructions.toArray())' } { Invoke-HookFixtureCase 'boss-asm-must-prove-two-real-native-draw-sites' 'two draw sites and absence of raw Boss reads' }
With-HookFixtureText $lateTest { param($s) $s -replace 'assertEquals\(\s*10,\s*delegates\)', 'assertEquals(9, delegates)' } { Invoke-HookFixtureCase 'boss-asm-cannot-omit-a-reviewed-native-delegate' 'real ASM coverage' }
$nausea = "$forgeBase/mixin/LegacyNauseaPresentationMixin.java"
With-HookFixtureText $nausea { param($s) $s.Replace('method = "renderConfusionOverlay"', 'method = "render"') } { Invoke-HookFixtureCase 'legacy-nausea-must-retain-exact-confusion-method' 'exact reviewed native method selector' }
With-HookFixtureText $nausea { param($s) $s.Replace('graphics.blit(texture, x, y, depth, u, v, width, height, textureWidth, textureHeight);', 'graphics.blit(texture, x, y, depth + 1, u, v, width, height, textureWidth, textureHeight);') } { Invoke-HookFixtureCase 'legacy-nausea-must-retain-native-depth-and-uv' 'forward unchanged arguments' }
With-HookFixtureText $nausea { param($s) $s.Replace('import net.minecraft.client.renderer.GameRenderer;', 'import third.party.GameRenderer;') } { Invoke-HookFixtureCase 'legacy-nausea-must-retain-native-renderer-owner' 'public UI target/import must be exactly' }
With-HookFixtureText 'versions/1.20.1-forge/src/test/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/LegacyNauseaPresentationTest.java' { param($s) $s.Replace('"activeGuiPresentation"', '"activeHudPresentation"') } { Invoke-HookFixtureCase 'legacy-nausea-asm-must-prove-the-complete-late-owner' 'nausea ASM must prove' }
Invoke-HookFixtureCase 'source-copy-fixture-restored-after-negative-cases'

[pscustomobject]@{
    Result = 'PASS'
    Checks = $cases.Count
    Cases = $cases
    Fixture = $fixture
    SampleKind = 'CURRENT_NATIVE_SOURCE_COPIES_WITH_EXPLICIT_CONFIG_AND_MANIFEST_FIXTURE_INPUTS'
    ActualRepositoryFilesMutated = $false
    CompiledTestsExecuted = $false
    RuntimeStarted = $false
}
