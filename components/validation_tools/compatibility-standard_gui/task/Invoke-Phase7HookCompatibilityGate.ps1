[CmdletBinding()]
param([string]$RepositoryRoot = (Join-Path $PSScriptRoot '../../../..'))

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path -LiteralPath $RepositoryRoot).Path

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw "Phase 7 Hook gate assertion failed: $Message" }
}

function Get-PassiveDrawContracts {
    param([string]$Target)
    $modern = $Target.StartsWith('26.')
    $sdl = $Target.StartsWith('26.3-')
    $graphics = if ($modern) { 'Lnet/minecraft/client/gui/GuiGraphicsExtractor;' } else { 'Lnet/minecraft/client/gui/GuiGraphics;' }
    $resource = if ($modern) { 'Lnet/minecraft/resources/Identifier;' } else { 'Lnet/minecraft/resources/ResourceLocation;' }
    $pipeline = if ($sdl) { 'Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;' } else { 'Lcom/mojang/blaze3d/pipeline/RenderPipeline;' }
    $font = 'Lnet/minecraft/client/gui/Font;'
    $component = 'Lnet/minecraft/network/chat/Component;'
    $sequence = 'Lnet/minecraft/util/FormattedCharSequence;'
    $sprite = if ($modern) { $graphics + 'blitSprite(' + $pipeline + $resource + 'IIII)V' } elseif ($Target -eq '1.20.1-forge') { $graphics + 'blit(' + $resource + 'IIIIII)V' } else { $graphics + 'blitSprite(' + $resource + 'IIII)V' }
    $fill = $graphics + 'fill(' + $(if ($modern) { $pipeline } else { 'Lnet/minecraft/client/renderer/RenderType;' }) + 'IIIII)V'
    $contracts = @{
        'HudVitalIconsMixin.java' = @{ Descriptors = @($sprite) * $(if ($Target -eq '1.20.1-forge') { 1 } else { 5 }); Test = 'HudDrawDelegationTest.java' }
        'ItemDecorationPresentationMixin.java' = @{ Descriptors = @($fill) * 3; Test = 'HudPresentationTest.java' }
    }
    $bossDescriptor = if ($Target -eq '1.20.1-forge') { $sprite } elseif ($modern) { $graphics + 'blitSprite(' + $pipeline + $resource + 'IIIIIIII)V' } else { $graphics + 'blitSprite(' + $resource + 'IIIIIIII)V' }
    $bossSelector = if ($Target -eq '1.20.1-forge') { 'drawBar(' + $graphics + 'IILnet/minecraft/world/BossEvent;II)V' } else { $(if ($modern) { 'extractBar(' } else { 'drawBar(' }) + $graphics + 'IILnet/minecraft/world/BossEvent;I[' + $resource + '[' + $resource + ')V' }
    $contracts['BossBarPresentationMixin.java'] = @{ Descriptors = @($bossDescriptor); Test = 'LateGuiPresentationTest.java'; MethodSelector = $bossSelector; BossDraw = $true; BossNativeSites = ($Target -ne '26.3-neoforge'); LegacyLate = (-not $modern) }
    if (-not $modern) {
        $contracts['LegacyNauseaPresentationMixin.java'] = @{ Descriptors = @($graphics + 'blit(' + $resource + 'IIIFFIIII)V'); Test = 'LegacyNauseaPresentationTest.java'; MethodSelector = 'renderConfusionOverlay'; LegacyNausea = $true }
    }
    if ($Target -eq '1.20.1-forge') {
        $contracts['ForgeVitalIconsMixin.java'] = @{ Descriptors = @($sprite) * 4; Test = 'HudDrawDelegationTest.java' }
        $contracts['HudPresentationMixin.java'] = @{ Descriptors = @(($graphics + 'drawString(' + $font + $component + 'III)I'), ($graphics + 'drawString(' + $font + 'Ljava/lang/String;IIIZ)I')); Test = 'HudPresentationTest.java' }
        $contracts['ContextualBarPresentationMixin.java'] = @{ Descriptors = @($sprite); Test = 'HudPresentationTest.java' }
    } elseif (-not $modern) {
        $contracts['HudPresentationMixin.java'] = @{ Descriptors = @(($graphics + 'drawStringWithBackdrop(' + $font + $component + 'IIII)I'), ($graphics + 'drawString(' + $font + 'Ljava/lang/String;IIIZ)I')); Test = 'HudPresentationTest.java' }
        $contracts['ContextualBarPresentationMixin.java'] = @{ Descriptors = @($sprite, ($graphics + 'blitSprite(' + $resource + 'IIIIIIII)V')); Test = 'HudPresentationTest.java' }
    } else {
        $bar = if ($sdl) { 'ContextualBar' } else { 'ContextualBarRenderer' }
        $contracts['HudPresentationMixin.java'] = @{ Descriptors = @(($graphics + 'textWithBackdrop(' + $font + $component + 'IIII)V'), ('Lnet/minecraft/client/gui/contextualbar/' + $bar + ';extractExperienceLevel(' + $graphics + $font + 'I)V')); Test = 'HudPresentationTest.java' }
        $contracts['ContextualBarPresentationMixin.java'] = @{ Descriptors = @($sprite, ($graphics + 'blitSprite(' + $pipeline + $resource + 'IIIIIIII)V')); Test = 'HudPresentationTest.java' }
        $contracts['LocatorBarPresentationMixin.java'] = @{ Descriptors = @($sprite, ($graphics + 'blitSprite(' + $pipeline + $resource + 'IIIII)V')); Test = 'HudPresentationTest.java' }
    }
    if ($modern) {
        $contracts['HudCameraPresentationMixin.java'] = @{ Descriptors = @($sprite, ($graphics + 'blitSprite(' + $pipeline + $resource + 'IIIIIIII)V'), ($graphics + 'blit(' + $pipeline + $resource + 'IIFFIIIII)V'), ($graphics + 'blit(' + $pipeline + $resource + 'IIFFIIII)V'), $fill, ($graphics + 'blitSprite(' + $pipeline + 'Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;IIIII)V'), ($graphics + 'fill(IIIII)V')); Test = 'HudCameraPresentationTest.java' }
    } elseif ($Target -eq '1.21.1-neoforge') {
        $overlay = $graphics + 'blit(' + $resource + 'IIIFFIIII)V'
        $contracts['HudCameraPresentationMixin.java'] = @{ Descriptors = @($sprite, ($graphics + 'blitSprite(' + $resource + 'IIIIIIII)V'), $overlay, $overlay, ($graphics + 'fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V'), ($graphics + 'blit(IIIIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V'), $fill); Test = 'HudCameraPresentationTest.java' }
    } elseif ($Target -eq '1.20.1-forge') {
        $overlay = $graphics + 'blit(' + $resource + 'IIIFFIIII)V'
        $contracts['HudCameraPresentationMixin.java'] = @{ Descriptors = @($sprite, $overlay, $overlay, ($graphics + 'fill(Lnet/minecraft/client/renderer/RenderType;IIIIII)V'), ($graphics + 'blit(IIIIILnet/minecraft/client/renderer/texture/TextureAtlasSprite;)V')); Test = 'HudCameraPresentationTest.java' }
        $contracts['ForgeHudSleepPresentationMixin.java'] = @{ Descriptors = @($fill); Test = 'HudCameraPresentationTest.java'; ForgeSleep = $true }
    }
    if ($sdl) {
        $text = $graphics + 'text('
        $contracts['FriendToastPresentationMixin.java'] = @{ Descriptors = @($sprite, ($text + $font + $sequence + 'IIIZ)V')); Test = 'UiToastDrawTest.java' }
    } elseif (-not $modern) {
        $text = $graphics + 'drawString('
        $score = if ($Target -eq '1.20.1-forge') { $text + $font + 'Ljava/lang/String;III)I' } else { $text + $font + $component + 'III)I' }
        $contracts['PlayerTabPresentationMixin.java'] = @{ Descriptors = @(($text + $font + $sequence + 'III)I'), ($text + $font + $sequence + 'III)I'), ($text + $font + $component + 'III)I'), $score, $sprite); Test = 'PlayerTabPresentationTest.java'; LegacyPlayerTab = $true }
    }
    if ($modern) {
        $text = $graphics + 'text('
        $backdrop = $graphics + 'textWithBackdrop(' + $font + $component + 'IIII)V'
        $activeText = 'Lnet/minecraft/client/gui/ActiveTextCollector;accept(Lnet/minecraft/client/gui/TextAlignment;IILnet/minecraft/client/gui/ActiveTextCollector$Parameters;' + $sequence + ')V'
        $contracts['ChatBackgroundPresentationMixin.java'] = @{ Descriptors = @($activeText); Test = 'LateGuiPresentationTest.java' }
        $contracts['ChatFocusedPresentationMixin.java'] = @{ Descriptors = @($activeText); Test = 'LateGuiPresentationTest.java' }
        $contracts['DebugTextPresentationMixin.java'] = @{ Descriptors = @($text + $font + 'Ljava/lang/String;IIIZ)V'); Test = 'LateGuiPresentationTest.java' }
        $contracts['EffectsInventoryPresentationMixin.java'] = @{ Descriptors = @(($text + $font + $sequence + 'III)V'), ($text + $font + $component + 'III)V')); Test = 'LateGuiPresentationTest.java' }
        $contracts['HudMessagePresentationMixin.java'] = @{ Descriptors = @($backdrop) * 3; Test = 'LateGuiPresentationTest.java' }
        $contracts['SubtitlePresentationMixin.java'] = @{ Descriptors = @(($text + $font + 'Ljava/lang/String;III)V'), ($text + $font + $component + 'III)V')); Test = 'LateGuiPresentationTest.java' }
        $contracts['TooltipPresentationMixin.java'] = @{ Descriptors = @($text + $font + $sequence + 'IIIZ)V'); Test = 'LateGuiPresentationTest.java' }
        $contracts['PlayerTabPresentationMixin.java'] = @{ Descriptors = @(($text + $font + $sequence + 'III)V'), ($text + $font + $sequence + 'III)V'), ($text + $font + $component + 'III)V'), ($text + $font + $component + 'III)V'), $sprite); Test = 'PlayerTabPresentationTest.java'; PlayerTabCounter = $true }
        $contracts['NowPlayingToastPresentationMixin.java'] = @{ Descriptors = @($sprite, ($text + $font + $component + 'III)V')); Test = 'UiToastDrawTest.java' }
        $contracts['ToastResidentPresentationMixin.java'] = @{ Descriptors = @('Lnet/minecraft/client/gui/components/toasts/Toast;extractRenderState(' + $graphics + $font + 'J)V'); Test = 'UiToastDrawTest.java'; ResidentScope = $true }
    } else {
        $text = $graphics + 'drawString('
        $message = if ($Target -eq '1.20.1-forge') { $text + $font + $component + 'III)I' } else { $graphics + 'drawStringWithBackdrop(' + $font + $component + 'IIII)I' }
        $contracts['ChatPresentationMixin.java'] = @{ Descriptors = @(($text + $font + $sequence + 'III)I'), ($text + $font + $component + 'III)I')); Test = 'LateGuiPresentationTest.java'; LegacyLate = $true }
        $contracts['DebugTextPresentationMixin.java'] = @{ Descriptors = @($text + $font + 'Ljava/lang/String;IIIZ)I'); Test = 'LateGuiPresentationTest.java'; LegacyLate = $true }
        $contracts['EffectsInventoryPresentationMixin.java'] = @{ Descriptors = @($text + $font + $component + 'III)I'); Test = 'LateGuiPresentationTest.java'; LegacyLate = $true }
        $contracts['HudMessagePresentationMixin.java'] = @{ Descriptors = @($message) * 3; Test = 'LateGuiPresentationTest.java'; LegacyLate = $true }
        $contracts['SubtitlePresentationMixin.java'] = @{ Descriptors = @(($text + $font + 'Ljava/lang/String;III)I'), ($text + $font + $component + 'III)I')); Test = 'LateGuiPresentationTest.java'; LegacyLate = $true }
        $contracts['TooltipPresentationMixin.java'] = @{ Descriptors = @('Lnet/minecraft/client/gui/Font;drawInBatch(' + $sequence + 'FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I'); Test = 'LateGuiPresentationTest.java'; LegacyLate = $true }
        $contracts['GuiGraphicsTooltipPresentationMixin.java'] = @{ Descriptors = @('Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent;renderText(' + $font + 'IILorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V'); Test = 'LateGuiPresentationTest.java'; LegacyTooltipScope = $true }
        if ($Target -eq '1.20.1-forge') {
            $contracts['DebugTextPresentationMixin.java'].MethodSelector = 'renderHUDText'
            $contracts['DebugTextPresentationMixin.java'].ForgeDraw = $true
            $contracts['HudMessagePresentationMixin.java'].Descriptors = @($message)
            $contracts['ForgeHudMessagePresentationMixin.java'] = @{ Descriptors = @($text + $font + $sequence + 'III)I'); Test = 'LateGuiPresentationTest.java'; LegacyLate = $true; MethodSelector = 'renderRecordOverlay'; ForgeDraw = $true }
        }
    }
    return $contracts
}

function Assert-PassiveDrawSource {
    param([string]$Label, [string]$Text, [hashtable]$Contract)
    Assert-True ($Text -notmatch 'cancellable\s*=\s*true|\.cancel\s*\(|@Overwrite|@Modify') "$Label passive native draw delegates cannot cancel or modify arguments"
    $handlers = [regex]::Matches($Text, '(?s)@Redirect\s*\((?<annotation>.*?)\)\s*private\s+(?:static\s+)?(?<result>void|int)\s+(?<name>[\w$]+)\s*\((?<parameters>.*?)\)\s*\{')
    Assert-True ($handlers.Count -eq @($Contract.Descriptors).Count -and $handlers.Count -eq ([regex]::Matches($Text, '@Redirect\b')).Count) "$Label must contain only its reviewed native draw handlers"
    $actual = @()
    foreach ($handler in $handlers) {
        $annotation = $handler.Groups['annotation'].Value
        $targets = [regex]::Matches($annotation, 'target\s*=\s*"([^"]+)"')
        Assert-True ($targets.Count -eq 1 -and $annotation -match 'value\s*=\s*"INVOKE"' -and $annotation -notmatch 'require\s*=\s*0|expect\s*=|slice\s*=') "$Label draw delegate must target one required native invocation"
        if ($Contract.ForgeSleep) {
            $compactAnnotation = [regex]::Replace($annotation, '\s+', '')
            Assert-True ($compactAnnotation.StartsWith('method="renderSleepFade",remap=false,at=@At(') -and $compactAnnotation.EndsWith('remap=true)')) "$Label must retain the exact Forge sleep method and native invocation remapping"
        }
        if ($Contract.ForgeDraw) {
            $compactAnnotation = [regex]::Replace($annotation, '\s+', '')
            Assert-True ($compactAnnotation.StartsWith('method="' + $Contract.MethodSelector + '",remap=false,at=@At(') -and $compactAnnotation.EndsWith('remap=true)')) "$Label must retain the exact Forge text method and native invocation remapping"
        }
        if ($Contract.MethodSelector) {
            $selectors = [regex]::Matches($annotation, 'method\s*=\s*"([^"]+)"')
            Assert-True ($selectors.Count -eq 1 -and $selectors[0].Groups[1].Value -ceq $Contract.MethodSelector) "$Label must retain its exact reviewed native method selector"
        }
        $descriptor = $targets[0].Groups[1].Value
        $actual += $descriptor
        $native = [regex]::Match($descriptor, '^L(?<owner>[^;]+);(?<method>\w+)\((?<arguments>[^)]*)\)(?<result>[VI])$')
        Assert-True ($native.Success) "$Label native draw descriptor must retain its reviewed return type"
        $start = $handler.Index + $handler.Length
        $depth = 1
        $end = $start
        while ($end -lt $Text.Length -and $depth -gt 0) {
            if ($Text[$end] -eq '{') { $depth++ } elseif ($Text[$end] -eq '}') { $depth-- }
            $end++
        }
        Assert-True ($depth -eq 0) "$Label draw handler body must be complete"
        $body = $Text.Substring($start, $end - $start - 1)
        $parameters = @($handler.Groups['parameters'].Value.Split(',') | ForEach-Object {
            $parameter = [regex]::Match($_.Trim(), '([\w$]+)$')
            Assert-True $parameter.Success "$Label draw handler parameters must be plain original arguments"
            $parameter.Groups[1].Value
        })
        $method = $native.Groups['method'].Value
        if ($Contract.LegacyTooltipScope) {
            $normalized = [regex]::Replace($body, '\s+', '')
            $compactAnnotation = [regex]::Replace($annotation, '\s+', '')
            $selector = 'renderTooltipInternal(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;)V'
            Assert-True ($compactAnnotation.StartsWith('method="' + $selector + '",at=@At(') -and $parameters.Count -eq 6 -and ($parameters -join ',') -ceq 'component,font,x,y,pose,buffers' -and $normalized -ceq 'UiNativeText.tooltipDraw((GuiGraphics)(Object)this,component,()->component.renderText(font,x,y,pose,buffers));' -and $Text -match '(?m)^import net\.minecraft\.client\.gui\.screens\.inventory\.tooltip\.ClientTooltipComponent;' -and $Text -match '(?m)^import net\.minecraft\.client\.gui\.Font;') "$Label must wrap exactly one unchanged typed native Tooltip call with its actual graphics and component owner"
        } elseif ($Contract.ResidentScope) {
            $normalized = [regex]::Replace($body, '\s+', '')
            $call = $parameters[0] + '.' + $method + '(' + ($parameters[1..($parameters.Count - 1)] -join ',') + ')'
            Assert-True ($normalized.StartsWith('if(toastinstanceofUiToastDraw.Sourcesource){') -and $normalized.Contains('.capture(') -and ([regex]::Matches($normalized, [regex]::Escape($call))).Count -eq 2 -and $normalized.Contains('()->' + $call) -and $normalized.EndsWith('}else' + $call + ';')) "$Label resident wrapper must call unchanged native draw in exactly one of the capture/fallback branches"
        } else {
            $staticDraw = $native.Groups['owner'].Value -match '/ContextualBar(?:Renderer)?$'
            $receiver = if ($staticDraw) { ($native.Groups['owner'].Value -split '/')[-1] } else { $parameters[0] }
            $argumentCount = ([regex]::Matches($native.Groups['arguments'].Value, '\[*(?:L[^;]+;|[BCDFIJSZ])')).Count
            $argumentStart = if ($staticDraw) { 0 } else { 1 }
            Assert-True ($parameters.Count -ge $argumentCount + $argumentStart) "$Label must accept every original native draw argument"
            $arguments = $parameters[$argumentStart..($argumentStart + $argumentCount - 1)]
            $call = $receiver + '.' + $method + '(' + ($arguments -join ',') + ');'
            $normalized = [regex]::Replace($body, '\s+', '')
            $prefix = if ($native.Groups['result'].Value -eq 'I') { 'intresult=' } else { '' }
            Assert-True ($normalized.StartsWith($prefix + $call) -and ([regex]::Matches($body, [regex]::Escape($receiver) + '\s*\.\s*' + $method + '\s*\(')).Count -eq 1) "$Label must forward unchanged arguments to exactly one original native draw before observation"
            if ($prefix) { Assert-True ($normalized.EndsWith('returnresult;')) "$Label must preserve the native draw return value" }
        }
        if ($Contract.BossDraw) {
            $observer = if ($descriptor.Contains(';blit(')) { 'bossAtlas' } else { 'bossSprite' }
            Assert-True ($body -match 'UiPresentationSnapshot\.activeGuiPresentation\(\)' -and $body -match ('frame\.' + $observer + '\(') -and $body -notmatch 'getProgress\(|getColor\(|getOverlay\(|getName\(|getId\(|\bBossEvent\b') "$Label must observe only its actual late-frame native Boss draw arguments"
        }
        if ($Contract.LegacyNausea) {
            Assert-True (([regex]::Replace($body, '\s+', '')).EndsWith('UiNativeText.nausea(graphics,texture.toString(),x,y,depth,width,height);')) "$Label must observe the unchanged native nausea texture only after drawing"
        }
    }
    Assert-True ((($actual | Sort-Object) -join "`n") -ceq ((@($Contract.Descriptors) | Sort-Object) -join "`n")) "$Label native draw targets must match the exact reviewed descriptor multiset"
    return $handlers.Count
}

function Assert-NativeUiTarget {
    param([string]$Label, [string]$Text, [string[]]$Expected)
    $mixin = [regex]::Match($Text, '@Mixin\s*\(\s*(?<targets>[^\r\n]+)\s*\)')
    $actual = @([regex]::Matches($mixin.Groups['targets'].Value, '([\w.]+)\.class') | ForEach-Object { $_.Groups[1].Value })
    Assert-True ($actual.Count -eq $Expected.Count) "$Label public UI target must match its exact native allowlist"
    foreach ($native in $Expected) {
        $matched = $false
        foreach ($candidate in $actual) {
            $first = ($candidate -split '\.')[0]
            $suffix = $candidate.Substring($first.Length)
            if (-not $native.EndsWith('.' + $candidate)) { continue }
            $import = $native.Substring(0, $native.Length - $suffix.Length)
            $package = $import.Substring(0, $import.LastIndexOf('.'))
            if ($Text -match ('(?m)^import\s+' + [regex]::Escape($import) + '\s*;') -or $Text -match ('(?m)^import\s+' + [regex]::Escape($package) + '\.\*\s*;')) { $matched = $true; break }
        }
        Assert-True $matched "$Label public UI target/import must be exactly $native, not an imported third-party lookalike"
    }
}

function Assert-LegacyScissorObservation {
    param([string]$Label, [string]$MixinText, [string]$DecoderText, [string]$TestText)
    Assert-True ($MixinText -notmatch 'cancellable\s*=\s*true|\.cancel\s*\(|@Redirect|@Overwrite|@Modify|@Accessor|@Invoker') "$Label scissor observation must not cancel, redirect or replace the native update"
    $compact = [regex]::Replace($MixinText, '\s+', '')
    Assert-True ($compact.Contains('privatevolatileUiNativeText.ScissorSampleminecraftProtocolProbe$scissor;') -and $compact.Contains('publicUiNativeText.ScissorSampleminecraftProtocolProbe$scissor(){returnminecraftProtocolProbe$scissor;}')) "$Label scissor sample must start unknown and its getter must return only its own observed state"
    $hooks = [regex]::Matches($MixinText, '(?s)@Inject\s*\((?<annotation>.*?)\)\s*private\s+void\s+(?<name>[\w$]+)\s*\((?<parameters>.*?)\)\s*\{(?<body>[^{}]*)\}')
    Assert-True ($hooks.Count -eq 2 -and ([regex]::Matches($MixinText, '@Inject\b')).Count -eq 2) "$Label must observe exactly its native constructor and applyScissor returns"
    foreach ($hook in $hooks) {
        $initial = $hook.Groups['name'].Value -ceq 'minecraftProtocolProbe$initialScissor'
        $applied = $hook.Groups['name'].Value -ceq 'minecraftProtocolProbe$appliedScissor'
        Assert-True ($initial -or $applied) "$Label may not observe an unreviewed scissor method"
        $method = if ($initial) { '<init>(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V' } else { 'applyScissor(Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V' }
        $annotation = [regex]::Replace($hook.Groups['annotation'].Value, '\s+', '')
        Assert-True ($annotation -ceq ('method="' + $method + '",at=@At("RETURN")')) "$Label may observe only the exact completed native constructor/applyScissor descriptor"
        $parameters = if ($initial) { 'CallbackInfoci' } else { 'ScreenRectanglerectangle,CallbackInfoci' }
        Assert-True (([regex]::Replace($hook.Groups['parameters'].Value, '\s+', '')) -ceq $parameters) "$Label scissor observation parameters must remain exact native callback inputs"
        $body = if ($initial) { 'minecraftProtocolProbe$scissor=newUiNativeText.ScissorSample(this,null,UiNativeText.ScissorOrigin.NATIVE_INITIAL_EMPTY_STACK);' } else { 'minecraftProtocolProbe$scissor=newUiNativeText.ScissorSample(this,rectangle,UiNativeText.ScissorOrigin.NATIVE_APPLY_SCISSOR_RETURN);' }
        Assert-True (([regex]::Replace($hook.Groups['body'].Value, '\s+', '')) -ceq $body) "$Label must bind the same graphics owner and unchanged completed native scissor, not a guessed rectangle"
    }
    $decoder = [regex]::Replace($DecoderText, '\s+', '')
    Assert-True ($decoder.Contains('returngraphicsinstanceofScissorSourcesource?validScissor(graphics,source.minecraftProtocolProbe$scissor()):null;') -and $decoder.Contains('returnowner!=null&&sample!=null&&sample.owner()==owner&&sample.origin()!=null?sample:null;')) "$Label decoder must reject unknown scissor samples and samples owned by other graphics"
    $test = [regex]::Replace($TestText, '\s+', '')
    Assert-True ($test.Contains('compiled("net/minecraft/client/gui/GuiGraphics")') -or $test.Contains('compiled(graphicsOwner)')) "$Label must inspect the actual native graphics bytecode"
    Assert-True ($test.Contains('"java/lang/Object.<init>()V","java/util/ArrayDeque.<init>()V"') -and $test.Contains('field.name.equals("scissorStack")') -and $test.Contains('call.name.equals(name.equals("enableScissor")?"push":"pop")') -and $test.Contains('assertEquals("applyScissor",nativeCalls.get(nativeCalls.size()-1).name);') -and $test.Contains('assertEquals("RETURN",annotationValue(assertInstanceOf(AnnotationNode.class,point),"value"));') -and $test.Contains('assertNull(UiNativeText.validScissor(newObject(),first));')) "$Label must retain native empty-stack, push/pop-to-apply, RETURN-boundary and owner-isolation ASM/sample tests"
}

function Assert-LegacyTooltipScope {
    param([string]$Label, [string]$DecoderText, [string]$TestText)
    $decoder = [regex]::Replace($DecoderText, '\s+', '')
    $scope = 'publicstaticvoidtooltipDraw(GuiGraphicsgraphics,ClientTooltipComponentcomponent,RunnablenativeDraw){TooltipContextprevious=TOOLTIP.get();TOOLTIP.set(newTooltipContext(graphics,component));try{nativeDraw.run();}finally{if(previous==null)TOOLTIP.remove();elseTOOLTIP.set(previous);}}'
    Assert-True ($decoder.Contains('privaterecordTooltipContext(GuiGraphicsgraphics,ClientTooltipComponentcomponent){}') -and $decoder.Contains('privatestaticfinalThreadLocal<TooltipContext>TOOLTIP=newThreadLocal<>();') -and $decoder.Contains($scope) -and ([regex]::Matches($decoder, 'nativeDraw\.run\(\)')).Count -eq 1) "$Label Tooltip scope must execute native draw exactly once and restore its actual nested owner in finally"
    Assert-True ($DecoderText -match '(?m)^import net\.minecraft\.client\.gui\.screens\.inventory\.tooltip\.ClientTooltipComponent;' -and $DecoderText -match '(?m)^import net\.minecraft\.client\.gui\.GuiGraphics;' -and $DecoderText -match '(?m)^import net\.minecraft\.client\.gui\.Font;' -and $decoder.Contains('TooltipContextcontext=TOOLTIP.get();if(context==null||context.component()!=component)return;observe("tooltip",context.graphics(),font,plain(text),x,y,font.width(text),color,pose);') -and $decoder.Contains('ScissorSampleclip=scissor(graphics);if(clip==null)return;')) "$Label Tooltip observation must reject the wrong component and unknown graphics/scissor before publishing native text"
    $test = [regex]::Replace($TestText, '\s+', '')
    Assert-True ($test.Contains('wrapper.methods.stream().filter(method->method.name.startsWith("lambda$")).findFirst().orElseThrow()') -and $test.Contains('assertEquals("net/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipComponent",draw.owner);') -and $test.Contains('assertEquals(List.of(args).subList(1,args.length),List.of(Type.getArgumentTypes(draw.desc)));') -and $test.Contains('assertEquals(1,wrapper.methods.stream().flatMap(method->Arrays.stream(method.instructions.toArray()))') -and $test.Contains('assertFalse(scope.tryCatchBlocks.isEmpty());') -and $test.Contains('call.owner.equals("java/lang/Runnable")&&call.name.equals("run")') -and $test.Contains('call.owner.equals("java/lang/ThreadLocal")&&call.name.equals("remove")') -and $test.Contains('assertSame(previous,scope.get());') -and $test.Contains('assertEquals(3,calls.get());assertNull(scope.get());') -and $test.Contains('UiNativeText.tooltipText(newObject(),null,null,0,0,0,null);')) "$Label must retain actual Tooltip lambda/once/arguments, exceptional and nested-owner cleanup ASM and execution tests"
}

function Assert-LateGuiBoundary {
    param([string]$Target, [string]$MixinText, [string]$SnapshotText, [string]$TestText)
    $method = if ($Target.StartsWith('1.')) { 'render' } elseif ($Target -eq '26.1.2-neoforge') { 'extractGui' } else { 'extractRenderState' }
    $owner = if ($Target.StartsWith('1.') -or $Target -eq '26.1.2-neoforge') { 'gameRenderer' } else { 'gui' }
    $compact = [regex]::Replace($MixinText, '\s+', '')
    Assert-True ($MixinText -notmatch 'cancellable\s*=\s*true|\.cancel\s*\(|@Redirect|@Overwrite|@Modify|@Accessor|@Invoker' -and ([regex]::Matches($MixinText, '@Inject\b')).Count -eq 2 -and $compact.Contains('privatefinalUiPresentationSnapshot.RenderedHudminecraftProtocolProbe$latePresentation=newUiPresentationSnapshot.RenderedHud(128);')) "$Target complete GUI frame must have only two passive boundary observations and its independent 128-node budget"
    Assert-True ($compact.Contains('@Inject(method="' + $method + '",at=@At("HEAD"))privatevoidminecraftProtocolProbe$begin(CallbackInfoci){minecraftProtocolProbe$latePresentation.beginGui(Minecraft.getInstance());}') -and $compact.Contains('@Inject(method="' + $method + '",at=@At("RETURN"))privatevoidminecraftProtocolProbe$finish(CallbackInfoci){minecraftProtocolProbe$latePresentation.finish(System.nanoTime());}') -and $compact.Contains('publicUiPresentationSnapshot.RenderedHudminecraftProtocolProbe$guiPresentation(){returnminecraftProtocolProbe$latePresentation;}')) "$Target must use the exact complete native GUI boundary, not clear or publish a partial inner draw"
    $snapshot = [regex]::Replace($SnapshotText, '\s+', '')
    Assert-True ($snapshot -match ('returnMinecraft\.getInstance\(\)\.' + $owner + 'instanceofGuiPresentationSource(?<source>\w+)\?\k<source>\.minecraftProtocolProbe\$guiPresentation\(\):null;')) "$Target complete GUI getter must read its actual bound native owner"
    $test = [regex]::Replace($TestText, '\s+', '')
    Assert-True ($test.Contains('gui.interfaces.contains("io/github/recrivenvi/minecraftprotocol/probe/runtime/UiPresentationSnapshot$GuiPresentationSource")') -or $test.Contains('frame.interfaces.contains("io/github/recrivenvi/minecraftprotocol/probe/runtime/UiPresentationSnapshot$GuiPresentationSource")')) "$Target complete GUI boundary must retain compiled native owner coverage"
    if ($Target.StartsWith('1.')) {
        Assert-True ($test.Contains('Type.getObjectType("net/minecraft/client/renderer/GameRenderer")') -and $test.Contains('assertTrue(hud>=0&&screen>hud,') -and $test.Contains('call.name.equals("drawScreen")')) "$Target must retain actual old native HUD-before-Screen ordering coverage"
    } elseif ($Target -eq '26.1.2-neoforge') {
        Assert-True ($test.Contains('nativeClass("net/minecraft/client/renderer/GameRenderer")') -and $test.Contains('method.name.equals("extractGui")') -and $test.Contains('"extractRenderState","drawScreen","extractDeferredSubtitles"')) "$Target must retain actual complete extractGui and deferred-subtitle native boundary coverage"
    }
}

if ($MyInvocation.InvocationName -ne '.') {

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
    if ($target.Name -eq '26.3-fabric') {
        $files += Get-ChildItem -LiteralPath (Join-Path $repositoryRoot 'versions/26.3-fabric/src/main/java/io/github/recrivenvi/minecraftprotocol/probe/mixin') -Filter '*.java' -File
    }
    Assert-True ($files.Count -gt 0) "$($target.Name) must own concrete Hook sources"
    $texts = @($files | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw })
    $combined = $texts -join "`n"

    Assert-True ($combined -notmatch '@Overwrite') "$($target.Name) must not use Overwrite"
    $operatorFiles = @('KeyboardHandlerMixin.java', 'MouseHandlerMixin.java', 'MinecraftMixin.java', 'WindowMixin.java', 'InputConstantsMixin.java', 'SDLEventHandlerMixin.java', 'WorldGestureMixin.java', 'PlayerCommandMixin.java', 'ConnectionMixin.java', 'CommandCompletionConnectionMixin.java', 'GameModeScreenRenderMixin.java')
    $debugMutationFiles = @('DebugLoadedChunkAccessMixin.java')
    $worldDelegationFiles = @(if ($target.Name.StartsWith('1.')) { 'LegacyEnvironmentRevisionMixin.java' })
    $passiveWorldCount = $worldDelegationFiles.Count
    if ($passiveWorldCount) {
        $worldTime = Get-Content -LiteralPath (Join-Path $mixinDirectory $worldDelegationFiles[0]) -Raw
        $worldTimeCompact = [regex]::Replace($worldTime, '\s+', '')
        Assert-True (([regex]::Matches($worldTime, '@Redirect')).Count-eq1 -and $worldTimeCompact.Contains('target="Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"') -and $worldTimeCompact.Contains('try{level.setDayTime(ticks);}finally{minecraftProtocol$naturalDayTimeUpdate=false;}') -and $worldTime-notmatch'cancellable\s*=\s*true|@Overwrite|@Modify') "$($target.Name) world clock revision must delegate the unchanged native setter once and unwind its natural-tick marker"
    }
    $loadedGuard = Get-Content -LiteralPath ($files | Where-Object Name -eq $debugMutationFiles[0]).FullName -Raw
    $compactGuard = [regex]::Replace($loadedGuard, '\s+', '')
    Assert-True ($compactGuard.Contains('@Mixin(ServerChunkCache.class)') -and $compactGuard.Contains('if(scope==null||!create)return;') -and $compactGuard.Contains('.getChunkNow(x,z)') -and $compactGuard.Contains('scope.reject(') -and ([regex]::Matches($loadedGuard,'cancellable\s*=\s*true')).Count-eq1 -and $loadedGuard-notmatch'@Redirect|@Overwrite|@Modify') "$($target.Name) DEBUG dependency guard must preserve ordinary native access and reject unknown chunks only inside its explicit scope"
    $drawContracts = Get-PassiveDrawContracts $target.Name
    $drawFiles = @($files | Where-Object { $drawContracts.ContainsKey($_.Name) } | Select-Object -ExpandProperty Name)
    foreach ($required in @('HudVitalIconsMixin.java', 'HudPresentationMixin.java', 'ItemDecorationPresentationMixin.java', 'ContextualBarPresentationMixin.java', 'BossBarPresentationMixin.java')) {
        Assert-True ($required -in $drawFiles) "$($target.Name) must retain its native $required observation"
    }
    if ($target.Name -eq '1.20.1-forge') { Assert-True ('ForgeVitalIconsMixin.java' -in $drawFiles) "$($target.Name) must retain Forge native vital delegates" }
    if ($target.Name.StartsWith('26.')) { Assert-True ('LocatorBarPresentationMixin.java' -in $drawFiles) "$($target.Name) must retain applicable native locator delegates" }
    if ($target.Name.StartsWith('1.')) {
        Assert-True ('PlayerTabPresentationMixin.java' -in $drawFiles) "$($target.Name) must retain its native player list draw delegates"
        Assert-True ('LegacyNauseaPresentationMixin.java' -in $drawFiles) "$($target.Name) must retain its native nausea draw delegate"
    }
    $passiveDrawCount = 0
    foreach ($drawFile in $files | Where-Object Name -in $drawFiles) {
        $contract = $drawContracts[$drawFile.Name]
        $passiveDrawCount += Assert-PassiveDrawSource "$($target.Name)/$($drawFile.Name)" (Get-Content -LiteralPath $drawFile.FullName -Raw) $contract
        $drawTest = Join-Path $repositoryRoot "versions/$($target.Name)/src/test/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/$($contract.Test)"
        Assert-True (Test-Path -LiteralPath $drawTest) "$($target.Name)/$($drawFile.Name) must check compiled native handlers in $($contract.Test)"
        $testCode = Get-Content -LiteralPath $drawTest -Raw
        $compactTest = [regex]::Replace($testCode, '\s+', '')
        if ($contract.LegacyTooltipScope) {
            $firstNativeTest = $compactTest.Contains('assertInstanceOf(MethodInsnNode.class,instructions.get(args.length))') -and $compactTest.Contains('args[index].getOpcode(Opcodes.ILOAD),load.getOpcode()') -and $compactTest.Contains('assertEquals(local,load.var)') -and $compactTest.Contains('local+=args[index].getSize();')
            $oneNativeTest = $compactTest.Contains('assertEquals(1,wrapper.methods.stream().flatMap(method->Arrays.stream(method.instructions.toArray()))') -and $compactTest.Contains('call.owner.equals(draw.owner)&&call.name.equals(draw.name)).count());')
        } elseif ($contract.LegacyPlayerTab) {
            $firstNativeTest = $compactTest.Contains('assertInstanceOf(MethodInsnNode.class,instructions.get(arguments.length))') -and $compactTest.Contains('arguments[index].getOpcode(Opcodes.ILOAD),load.getOpcode()') -and $compactTest.Contains('assertEquals(local,load.var)') -and $compactTest.Contains('local+=arguments[index].getSize();')
            $oneNativeTest = $compactTest.Contains('assertEquals("net/minecraft/client/gui/GuiGraphics",draw.owner);') -and $compactTest.Contains('assertEquals(List.of(arguments).subList(1,arguments.length),List.of(Type.getArgumentTypes(draw.desc)));') -and $compactTest.Contains('assertEquals(1,java.util.Arrays.stream(method.instructions.toArray()).filter(value->valueinstanceofMethodInsnNodecall&&call.owner.equals(draw.owner)&&call.name.equals(draw.name)).count());') -and $compactTest.Contains('assertEquals(stored.var,loaded.var);') -and $compactTest.Contains('assertEquals(Opcodes.IRETURN,instructions.get(instructions.size()-1).getOpcode());') -and $compactTest.Contains('assertEquals(5,observers);')
        } elseif ($contract.PlayerTabCounter) {
            $firstNativeTest = $compactTest.Contains('assertInstanceOf(MethodInsnNode.class,instructions.get(arguments.length));') -and $compactTest.Contains('arguments[index].getOpcode(Opcodes.ILOAD),load.getOpcode()') -and $compactTest.Contains('assertEquals(local,load.var)') -and $compactTest.Contains('local+=arguments[index].getSize();')
            $oneNativeTest = $compactTest.Contains('invoke.owner.equals("net/minecraft/client/gui/GuiGraphicsExtractor")&&(invoke.name.equals("text")||invoke.name.equals("blitSprite"))') -and $compactTest.Contains('nativeDraws++;forwarded=true;') -and $compactTest.Contains('assertTrue(forwarded,method.name);records++;') -and $compactTest.Contains('assertEquals(1,nativeDraws,method.name);') -and $compactTest.Contains('assertEquals(1,records,method.name);') -and $compactTest.Contains('assertEquals(5,observers);')
        } else {
            $firstNativeTest = $compactTest.Contains('assertSame(draw,instructions.get(') -or $compactTest.Contains('MethodInsnNode.class,instructions.get(args.length)')
            $oneNativeTest = $compactTest -match '(?s)assertEquals\(1,instructions.stream\(\).*?\.count\(\)'
            if ($contract.LegacyLate) {
                $expectedLateDelegates = if ($target.Name -eq '1.20.1-forge') { 10 } else { 11 }
                $oneNativeTest = $oneNativeTest -and $compactTest.Contains('if(Type.getReturnType(draw.desc).equals(Type.INT_TYPE)){') -and $compactTest.Contains('assertEquals(stored.var,loaded.var);') -and $compactTest.Contains('assertEquals(Opcodes.IRETURN,instructions.get(instructions.size()-1).getOpcode());') -and $compactTest.Contains('assertEquals(' + $expectedLateDelegates + ',delegates);')
            }
        }
        Assert-True ($testCode.Contains([IO.Path]::GetFileNameWithoutExtension($drawFile.Name)) -and $testCode -match 'ClassReader' -and $testCode -match 'VarInsnNode' -and $firstNativeTest -and $oneNativeTest) "$($target.Name)/$($drawFile.Name) must have real ASM coverage of argument order, first native invocation and one draw"
        if ($contract.BossDraw) {
            $expectedDelegates = if ($target.Name -eq '1.20.1-forge') { 10 } elseif ($target.Name.StartsWith('1.')) { 11 } else { 12 }
            Assert-True ($compactTest.Contains('assertEquals(' + $expectedDelegates + ',delegates);')) "$($target.Name) Boss delegate must remain in the exact complete late draw ASM group"
            if ($contract.BossNativeSites) {
                $nativeSelector = $contract.MethodSelector.Substring($contract.MethodSelector.IndexOf('('))
                Assert-True ($compactTest.Contains('nativeClass("net/minecraft/client/gui/components/BossHealthOverlay")') -and $compactTest.Contains('"' + $nativeSelector + '"') -and $compactTest.Contains('assertEquals(2,Arrays.stream(overload.instructions.toArray())') -and $compactTest.Contains('call.desc.equals(descriptor)).count());') -and $compactTest.Contains('assertEquals(1,calls.stream().filter(') -and $compactTest.Contains('List.of("getProgress","getColor","getOverlay","getName","getId").contains(call.name)') -and $compactTest.Contains('field.owner.equals("net/minecraft/world/BossEvent")')) "$($target.Name) Boss ASM must inspect the exact native overload, its two draw sites and absence of raw Boss reads"
            }
        }
        if ($contract.LegacyNausea) {
            Assert-True ($compactTest.Contains('StringRENDERER="net/minecraft/client/renderer/GameRenderer";') -and $compactTest.Contains('StringGRAPHICS="net/minecraft/client/gui/GuiGraphics";') -and $compactTest.Contains('StringBLIT="(Lnet/minecraft/resources/ResourceLocation;IIIFFIIII)V";') -and $compactTest.Contains('assertEquals(List.of("renderConfusionOverlay"),value(redirect,"method"));') -and $compactTest.Contains('assertTrue(nausea>=0&&hud>nausea);') -and $compactTest.Contains('assertEquals(1,Arrays.stream(nativeNausea.instructions.toArray())') -and $compactTest.Contains('call.desc.equals(BLIT)).count());') -and $compactTest.Contains('assertFalse(calls(observer,ROOT+"runtime/UiPresentationSnapshot","activeHudPresentation"));') -and $compactTest.Contains('assertTrue(calls(observer,ROOT+"runtime/UiPresentationSnapshot","activeGuiPresentation"));')) "$($target.Name) nausea ASM must prove the actual native draw, method selector and complete late-frame owner"
        }
        if ($contract.ResidentScope) {
            $scopeSource = Get-Content -LiteralPath (Join-Path $repositoryRoot 'components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core/UiToastDraw.java') -Raw
            Assert-True ($scopeSource -match '(?m)^package io\.github\.recrivenvi\.minecraftprotocol\.core;' -and $compactTest.Contains('compiled.interfaces.contains("io/github/recrivenvi/minecraftprotocol/core/UiToastDraw$Source")') -and $compactTest.Contains('call.owner.equals("io/github/recrivenvi/minecraftprotocol/core/UiToastDraw")')) "$($target.Name) must bind the shared core resident scope and compiled owner without a split Target package"
            Assert-True (([regex]::Matches($scopeSource, 'nativeDraw\.run\(\)')).Count -eq 1 -and $scopeSource -match '(?s)try\s*\{\s*nativeDraw.run\(\);\s*success\s*=\s*true;\s*\}\s*finally' -and $testCode -match 'failedDrawInvalidatesPriorTextAndUnwindsNativeScopeEvenWithReentry') "$($target.Name) resident Toast scope must invoke native draw exactly once and unwind when it throws"
        }
    }
    if ($target.Name.StartsWith('1.')) {
        $scissorPath = Join-Path $mixinDirectory 'GuiGraphicsScissorMixin.java'
        Assert-True (Test-Path -LiteralPath $scissorPath) "$($target.Name) must retain native graphics scissor observations"
        $decoderPath = Join-Path $mixinDirectory '../runtime/UiNativeText.java'
        $testPath = Join-Path $repositoryRoot "versions/$($target.Name)/src/test/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/PlayerTabPresentationTest.java"
        Assert-LegacyScissorObservation $target.Name (Get-Content -LiteralPath $scissorPath -Raw) (Get-Content -LiteralPath $decoderPath -Raw) (Get-Content -LiteralPath $testPath -Raw)
        $lateTest = Join-Path $repositoryRoot "versions/$($target.Name)/src/test/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/LateGuiPresentationTest.java"
        Assert-LegacyTooltipScope $target.Name (Get-Content -LiteralPath $decoderPath -Raw) (Get-Content -LiteralPath $lateTest -Raw)
    }
    $observationCode = ($files | Where-Object { $_.Name -notin $operatorFiles -and $_.Name -notin $drawFiles -and $_.Name -notin $debugMutationFiles -and $_.Name -notin $worldDelegationFiles } | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -join "`n"
    Assert-True ($observationCode -notmatch 'cancellable\s*=\s*true|\.cancel\s*\(|@Redirect') "$($target.Name) observation Hooks must not change control flow"
    $lateSource = Join-Path $mixinDirectory 'GuiLatePresentationMixin.java'
    $snapshotSource = Join-Path $mixinDirectory '../runtime/UiPresentationSnapshot.java'
    $lateTest = Join-Path $repositoryRoot "versions/$($target.Name)/src/test/java/io/github/recrivenvi/minecraftprotocol/probe/runtime/LateGuiPresentationTest.java"
    Assert-LateGuiBoundary $target.Name (Get-Content -LiteralPath $lateSource -Raw) (Get-Content -LiteralPath $snapshotSource -Raw) (Get-Content -LiteralPath $lateTest -Raw)
    $windowHook = Get-Content -LiteralPath (Join-Path $mixinDirectory 'WindowMixin.java') -Raw
    $cursorArgumentPattern = '@ModifyVariable\(\s*method\s*=\s*"selectCursor\(Lcom/mojang/blaze3d/platform/cursor/CursorType;\)V",\s*at\s*=\s*@At\("HEAD"\),\s*argsOnly\s*=\s*true,\s*ordinal\s*=\s*0\)'
    $cursorArgumentCount = ([regex]::Matches($windowHook, $cursorArgumentPattern)).Count
    $expectedCursorArguments = if ($target.Name.StartsWith('26.')) { 1 } else { 0 }
    Assert-True ($cursorArgumentCount -eq $expectedCursorArguments) "$($target.Name) must own only its applicable exact host cursor argument guard"
    $otherWindowCode = [regex]::Replace($windowHook, $cursorArgumentPattern, '')
    $otherHookCode = ($files | Where-Object Name -ne 'WindowMixin.java' | ForEach-Object { Get-Content -LiteralPath $_.FullName -Raw }) -join "`n"
    Assert-True (($otherWindowCode + $otherHookCode) -notmatch '@ModifyArg|@ModifyArgs|@ModifyVariable|@ModifyConstant') "$($target.Name) must not use unreviewed replacement mechanisms"
    $cancellableCount = ([regex]::Matches($combined, 'cancellable\s*=\s*true')).Count
    $redirectCount = ([regex]::Matches($combined, '@Redirect')).Count
    $replacementCount = $redirectCount + $cursorArgumentCount
    $expectedCancellable=if($target.Name.StartsWith('26.3-')){27}elseif($target.Name.StartsWith('26.')){24}else{22}
    $operatorRedirectCount = $redirectCount - $passiveDrawCount - $passiveWorldCount
    $operatorReplacementCount = $operatorRedirectCount + $cursorArgumentCount
    $expectedOperatorRedirect=if($target.Name.StartsWith('1.')){7}elseif($target.Name.StartsWith('26.3-')){5}else{6}
    Assert-True ($cancellableCount -eq $expectedCancellable -and $operatorRedirectCount -eq $expectedOperatorRedirect) "$($target.Name) must match the scoped DEBUG dependency guard, exclusive input/polling/warp guards and native-ingress/icon/keymapping/mouse-frame redirects"
    $mouseConsumer=if($target.Name-eq'1.20.1-forge'){'turnPlayer'}else{'handleAccumulatedMovement'}
    $minecraftHook=Get-Content -LiteralPath (Join-Path $mixinDirectory 'MinecraftMixin.java') -Raw
    $minecraftCompact=[regex]::Replace($minecraftHook,'\s+','')
    Assert-True (([regex]::Matches($minecraftHook,'@Redirect')).Count-eq2-and$minecraftCompact.Contains('target="Lnet/minecraft/client/MouseHandler;'+$mouseConsumer+'()V"')-and$minecraftCompact.Contains('AgentInputContext.routed(mouse::'+$mouseConsumer+');')-and$minecraftCompact.Contains('elsemouse.'+$mouseConsumer+'();')) "$($target.Name) native mouse-frame delegate must preserve one unchanged native call in either origin branch"
    $runtime=(Get-ChildItem -LiteralPath (Join-Path $mixinDirectory '../runtime') -Filter '*ProbeRuntime.java'|ForEach-Object {Get-Content -LiteralPath $_.FullName -Raw}) -join "`n"
    if ($target.Name.StartsWith('1.')) {
        $deferred = Get-Content -LiteralPath (Join-Path $mixinDirectory 'GameModeScreenRenderMixin.java') -Raw
        $deferredCompact = [regex]::Replace($deferred, '\s+', '')
        Assert-True ($deferred -match '(?m)^import net\.minecraft\.client\.gui\.screens\.Screen;' -and $deferredCompact.Contains('@Mixin(Screen.class)') -and $deferredCompact.Contains('@Redirect(method="renderWithTooltip",at=@At(value="INVOKE",target="Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))') -and ([regex]::Matches($deferred, '@Redirect\b')).Count -eq 1 -and $deferred -notmatch '@Invoker|@Accessor|@Overwrite|cancellable\s*=\s*true') "$($target.Name) deferred game mode must wrap only the actual public Screen render invocation"
        $runtimeName = if ($target.Name -eq '1.20.1-forge') { 'ForgeProbeRuntime' } else { 'NeoForgeProbeRuntime' }
        Assert-True ($deferredCompact.Contains($runtimeName + '.renderGameModeScreen(screen,graphics,x,y,partialTick);') -and ([regex]::Matches($deferredCompact, 'renderGameModeScreen\(')).Count -eq 1 -and $deferredCompact -notmatch 'screen\.render\(') "$($target.Name) deferred game mode must forward unchanged native render arguments exactly once"
    }
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
    $chatPacketGuard = if ($target.Name.StartsWith('26.')) {
        $compactRuntime = [regex]::Replace($runtime, '\s+', '')
        $compactCommands = [regex]::Replace($commandAdapter, '\s+', '')
        $compactRuntime.Contains('NativePlayerCommands.submittedCommand(packet)') -and $compactRuntime.Contains('returncommand==null||allowPlayerCommand(command,true,source);') -and $compactCommands.Contains('staticStringsubmittedCommand(Packet<?>packet){') -and $compactCommands.Contains('if(packetinstanceofnet.minecraft.network.protocol.game.ServerboundChatCommandPacketcommand)returncommand.command();')
    } else { $runtime -match 'ServerboundChatCommandPacket' }
    Assert-True ($chatPacketGuard-and$runtime-match'NativePlayerCommands.configuration\(packet\)'-and$runtime-match'gate.allowConfiguration\('-and$commandAdapter-match'ServerboundSetCommandBlockPacket'-and$commandAdapter-match'ServerboundSetCommandMinecartPacket') "$($target.Name) final typed chat/block/minecart admission must remain in production adapters"
    Assert-True ($runtime -match 'worldTargeting.holdActive\(\)' -and $runtime -match 'worldTargeting.permit\(kind\)') "$($target.Name) semantic veto must only apply while the Runtime owns a bounded gesture"
    Assert-True ($runtime -match ('"cancellableInjectionCount",\s*'+$cancellableCount+'\)') -and $runtime -match ('"replacementInjectionCount",\s*'+$replacementCount+'\)')) "$($target.Name) production Hook counts must match actual sources"
    Assert-True ($runtime -match ('"passiveWorldDelegationCount",\s*'+$passiveWorldCount+'\)')) "$($target.Name) world delegation count must remain separate from native draw and input replacement counts"
    if ($cursorArgumentCount -gt 0) {
        Assert-True ($runtime -match 'host_cursor_shape_guard' -and $runtime -match 'MIXIN_MODIFY_VARIABLE' -and $runtime -match 'default_host_shape_during_takeover') "$($target.Name) must disclose its host cursor argument replacement, not mislabel it as cancellation or redirect"
    }
    Assert-True ($runtime -match ('"operatorReplacementCount",\s*'+$operatorReplacementCount+'\)') -and $runtime -match ('"passiveDrawDelegationCount",\s*'+$passiveDrawCount+'\)') -and $runtime -match 'operator_control_debug_mutation_and_exact_hud_draw_delegation' -and $runtime -match '"debugMutationGuardCount",\s*1\)' -and $runtime -match '"DEBUG_MUTATION"' -and $runtime -match 'scoped_native_chunk_dependency_guard' -and $runtime -match 'passive_draw_observation') "$($target.Name) production manifest must disclose passive draw delegation separately from operator control and DEBUG mutation"
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
    $privateDrawTargets = @{
        'ChatBackgroundPresentationMixin.java' = 'net.minecraft.client.gui.components.ChatComponent$DrawingBackgroundGraphicsAccess'
        'ChatFocusedPresentationMixin.java' = 'net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess'
        'ToastResidentPresentationMixin.java' = 'net.minecraft.client.gui.components.toasts.ToastManager$ToastInstance'
    }
    $hud = if ($target.Name.StartsWith('26.3-')) { 'net.minecraft.client.gui.Hud' } else { 'net.minecraft.client.gui.Gui' }
    $publicUiTargets = @{
        'AbstractContainerScreenAccessor.java' = @('net.minecraft.client.gui.screens.inventory.AbstractContainerScreen')
        'AdvancementsUiAccessor.java' = @('net.minecraft.client.gui.screens.advancements.AdvancementsScreen')
        'AdvancementTabUiAccessor.java' = @('net.minecraft.client.gui.screens.advancements.AdvancementTab')
        'AdvancementToastUiAccessor.java' = @('net.minecraft.client.gui.components.toasts.AdvancementToast')
        'AdvancementWidgetUiAccessor.java' = @('net.minecraft.client.gui.screens.advancements.AdvancementWidget')
        'BookUiAccessor.java' = @('net.minecraft.client.gui.screens.inventory.BookEditScreen')
        'BookViewUiAccessor.java' = @('net.minecraft.client.gui.screens.inventory.BookViewScreen')
        'BossOverlayUiAccessor.java' = @('net.minecraft.client.gui.components.BossHealthOverlay')
        'ChatComponentUiAccessor.java' = @('net.minecraft.client.gui.components.ChatComponent')
        'ChatUiAccessor.java' = @('net.minecraft.client.gui.screens.ChatScreen')
        'ChatPresentationMixin.java' = @('net.minecraft.client.gui.components.ChatComponent')
        'CommandSuggestionsUiAccessor.java' = @('net.minecraft.client.gui.components.CommandSuggestions')
        'CreativeUiAccessor.java' = @('net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen')
        'GameModeSlotUiAccessor.java' = @('net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen.GameModeSlot')
        'GameModeSwitcherUiAccessor.java' = @('net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen')
        'HudUiAccessor.java' = @($hud)
        'KeyBindingUiAccessor.java' = @('net.minecraft.client.KeyMapping')
        'KeyEntryUiAccessor.java' = @($(if($target.Name-eq'1.20.1-forge'){'net.minecraft.client.gui.screens.controls.KeyBindsList.KeyEntry'}else{'net.minecraft.client.gui.screens.options.controls.KeyBindsList.KeyEntry'}))
        'LoomUiAccessor.java' = @('net.minecraft.client.gui.screens.inventory.LoomScreen')
        'MerchantUiAccessor.java' = @('net.minecraft.client.gui.screens.inventory.MerchantScreen')
        'PackEntryUiAccessor.java' = @('net.minecraft.client.gui.screens.packs.TransferableSelectionList.PackEntry')
        'PlayerListUiAccessor.java' = @('net.minecraft.client.gui.components.PlayerTabOverlay')
        'RecipeOverlayUiAccessor.java' = @('net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent')
        'RecipeToastUiAccessor.java' = @('net.minecraft.client.gui.components.toasts.RecipeToast')
        'RecipeUiAccessor.java' = @('net.minecraft.client.gui.screens.recipebook.RecipeBookComponent')
        'ScrollAreaUiAccessor.java' = @('net.minecraft.client.gui.components.AbstractScrollArea')
        'ScrollWidgetUiAccessor.java' = @('net.minecraft.client.gui.components.AbstractScrollWidget')
        'SignUiAccessor.java' = @('net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen')
        'SpectatorGuiUiAccessor.java' = @('net.minecraft.client.gui.components.spectator.SpectatorGui')
        'SpectatorMenuUiAccessor.java' = @('net.minecraft.client.gui.spectator.SpectatorMenu')
        'StonecutterUiAccessor.java' = @('net.minecraft.client.gui.screens.inventory.StonecutterScreen')
        'StringWidgetUiAccessor.java' = @('net.minecraft.client.gui.components.AbstractStringWidget')
        'SuggestionListUiAccessor.java' = @('net.minecraft.client.gui.components.CommandSuggestions.SuggestionsList')
        'SystemToastUiAccessor.java' = @('net.minecraft.client.gui.components.toasts.SystemToast')
        'TextFieldUiAccessor.java' = @('net.minecraft.client.gui.font.TextFieldHelper')
        'ToastManagerUiAccessor.java' = @($(if($target.Name.StartsWith('1.')){'net.minecraft.client.gui.components.toasts.ToastComponent'}else{'net.minecraft.client.gui.components.toasts.ToastManager'}))
        'TooltipUiAccessor.java' = @('net.minecraft.client.gui.components.Tooltip')
        'TutorialToastUiAccessor.java' = @('net.minecraft.client.gui.components.toasts.TutorialToast')
        'WidgetTooltipUiAccessor.java' = @('net.minecraft.client.gui.components.AbstractWidget')
        'WorldEntryAccessor.java' = @('net.minecraft.client.gui.screens.worldselection.WorldSelectionList.WorldListEntry')
        'HudVitalIconsMixin.java' = @($hud)
        'ForgeVitalIconsMixin.java' = @('net.minecraftforge.client.gui.overlay.ForgeGui')
        'ForgeHudSleepPresentationMixin.java' = @('net.minecraftforge.client.gui.overlay.ForgeGui')
        'HudPresentationMixin.java' = @($hud)
        'HudPresentationFrameMixin.java' = @('net.minecraft.client.renderer.GameRenderer')
        'ItemDecorationPresentationMixin.java' = @($(if($target.Name.StartsWith('26.')){'net.minecraft.client.gui.GuiGraphicsExtractor'}else{'net.minecraft.client.gui.GuiGraphics'}))
        'ContextualBarPresentationMixin.java' = @($(if($target.Name.StartsWith('26.3-')){'net.minecraft.client.gui.contextualbar.ExperienceBar';'net.minecraft.client.gui.contextualbar.JumpableVehicleBar'}elseif($target.Name.StartsWith('26.')){'net.minecraft.client.gui.contextualbar.ExperienceBarRenderer';'net.minecraft.client.gui.contextualbar.JumpableVehicleBarRenderer'}else{$hud}))
        'LocatorBarPresentationMixin.java' = @($(if($target.Name.StartsWith('26.3-')){'net.minecraft.client.gui.contextualbar.LocatorBar'}else{'net.minecraft.client.gui.contextualbar.LocatorBarRenderer'}))
        'HudCameraPresentationMixin.java' = @($hud)
        'LegacyNauseaPresentationMixin.java' = @('net.minecraft.client.renderer.GameRenderer')
        'HudMessagePresentationMixin.java' = @($hud)
        'ForgeHudMessagePresentationMixin.java' = @('net.minecraftforge.client.gui.overlay.ForgeGui')
        'PlayerTabPresentationMixin.java' = @('net.minecraft.client.gui.components.PlayerTabOverlay')
        'GuiGraphicsScissorMixin.java' = @('net.minecraft.client.gui.GuiGraphics')
        'GuiLatePresentationMixin.java' = @($(if ($target.Name.StartsWith('1.') -or $target.Name -eq '26.1.2-neoforge') { 'net.minecraft.client.renderer.GameRenderer' } else { 'net.minecraft.client.gui.Gui' }))
        'GuiGraphicsTooltipPresentationMixin.java' = @('net.minecraft.client.gui.GuiGraphics')
        'BossBarPresentationMixin.java' = @('net.minecraft.client.gui.components.BossHealthOverlay')
        'DebugTextPresentationMixin.java' = @($(if ($target.Name -eq '1.20.1-forge') { 'net.minecraftforge.client.gui.overlay.ForgeGui' } else { 'net.minecraft.client.gui.components.DebugScreenOverlay' }))
        'EffectsInventoryPresentationMixin.java' = @($(if ($target.Name.StartsWith('1.')) { 'net.minecraft.client.gui.screens.inventory.EffectRenderingInventoryScreen' } else { 'net.minecraft.client.gui.screens.inventory.EffectsInInventory' }))
        'SubtitlePresentationMixin.java' = @('net.minecraft.client.gui.components.SubtitleOverlay')
        'TooltipPresentationMixin.java' = @('net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip')
        'FriendToastPresentationMixin.java' = @('net.minecraft.client.gui.components.toasts.FriendToast')
        'NowPlayingToastPresentationMixin.java' = @('net.minecraft.client.gui.components.toasts.NowPlayingToast')
        'EffectIconRenderMixin.java' = @($hud)
        'HudTitleLifecycleMixin.java' = @($hud)
        'ToastRenderTextMixin.java' = @('net.minecraft.client.gui.components.toasts.AdvancementToast', 'net.minecraft.client.gui.components.toasts.SystemToast', 'net.minecraft.client.gui.components.toasts.TutorialToast', 'net.minecraft.client.gui.components.toasts.RecipeToast')
    }
    foreach ($file in $files) {
        $text = Get-Content -LiteralPath $file.FullName -Raw
        if ($text -match '@Mixin\s*\(\s*targets\s*=') {
            $match = [regex]::Match($text, '@Mixin\s*\(\s*targets\s*=\s*"([^"]+)"\s*\)')
            if ($file.Name-eq'ClockInstanceAccessor.java') {
                $clockCompact=[regex]::Replace($text,'\s+','')
                Assert-True ($target.Name-eq'26.1.2-neoforge'-and$match.Success-and$match.Groups[1].Value-ceq'net.minecraft.world.clock.ServerClockManager$ClockInstance'-and$clockCompact.Contains('@Accessor("paused")booleanminecraftProtocol$paused();')-and$clockCompact.Contains('@Accessor("rate")floatminecraftProtocol$rate();')-and([regex]::Matches($text,'@Accessor')).Count-eq2-and$text-notmatch'@Inject|@Redirect|@Overwrite|@Invoker|@Mutable') "$($target.Name) private native clock playback accessor must remain the exact two read-only fields"
                continue
            }
            if ($privateDrawTargets.ContainsKey($file.Name)) {
                Assert-True ($match.Success -and $match.Groups[1].Value -ceq $privateDrawTargets[$file.Name] -and $file.Name -in $drawFiles -and $text -notmatch '@Accessor|@Invoker|@Inject') "$($target.Name)/$($file.Name) private draw target must match its separately reviewed native delegation contract"
                $nativeMethod = if ($file.Name -eq 'ToastResidentPresentationMixin.java') { 'extractRenderState' } else { 'handleMessage' }
                $methodSelectors = @([regex]::Matches($text, 'method\s*=\s*"([^"]+)"') | ForEach-Object { $_.Groups[1].Value })
                Assert-True ($methodSelectors.Count -eq 1 -and $methodSelectors[0] -ceq $nativeMethod) "$($target.Name)/$($file.Name) private draw delegate must observe only its reviewed native method"
                continue
            }
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
            if ($publicUiTargets.ContainsKey($file.Name)) {
                Assert-NativeUiTarget "$($target.Name)/$($file.Name)" $text $publicUiTargets[$file.Name]
                if ($file.Name -eq 'RecipeToastUiAccessor.java') {
                    $compact = [regex]::Replace($text, '\s+', '')
                    Assert-True ($target.Name.StartsWith('1.') -and $compact.EndsWith('publicinterfaceRecipeToastUiAccessor{@Accessor("recipes")List<?>minecraftProtocolProbe$recipes();}') -and $text -notmatch '@Invoker|@Inject|@Redirect|@Overwrite|@Mutable') "$($target.Name) Recipe Toast adapter must only read its native recipe list to reject the empty-render branch"
                }
            } else {
                Assert-True ($file.Name -notmatch 'UiAccessor\.java$|UiMarker\.java$|PresentationMixin\.java$') "$($target.Name)/$($file.Name) new public UI adapter needs an exact native target review"
                $publicTargets = [regex]::Match($text, '@Mixin\s*\(\s*([^\r\n]+)\)')
                $types = @([regex]::Matches($publicTargets.Groups[1].Value, '([\w.]+)\.class') | ForEach-Object { $_.Groups[1].Value })
                Assert-True ($types.Count -gt 0) "$($target.Name)/$($file.Name) must target a native class literal"
                foreach ($type in $types) {
                    $first = ($type -split '\.')[0]
                    $import = [regex]::Match($text, '(?m)^import\s+([\w.]+\.' + [regex]::Escape($first) + ');')
                    $nativeImport = if ($import.Success) { $import.Groups[1].Value -match '^(net\.minecraft\.|com\.mojang\.blaze3d\.)' } else { $text -match '(?m)^import\s+(net\.minecraft\.|com\.mojang\.blaze3d\.)[\w.]+\.\*;' }
                    Assert-True $nativeImport "$($target.Name)/$($file.Name) must target Minecraft, not a third-party Mod"
                }
            }
        }
    }

    $configPath = Join-Path $repositoryRoot "versions\$($target.Name)\$($target.Config)"
    $config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
    Assert-True ([bool]$config.required) "$($target.Name) Mixin config must fail visibly when a required Hook cannot apply"
    Assert-True ($config.injectors.defaultRequire -eq 1) "$($target.Name) Hook injections must not silently skip"
    $registered = @(@($config.client) + @($config.mixins) | Where-Object { $_ })
    if ($target.Name -eq '26.3-fabric') {
        $serverConfig = Get-Content -LiteralPath (Join-Path $repositoryRoot 'versions/26.3-fabric/src/main/resources/minecraft_protocol_probe.server.mixins.json') -Raw | ConvertFrom-Json
        $commonHooks=@('DebugLoadedChunkAccessMixin','ClockControlRevisionMixin','WeatherControlRevisionMixin','BaseContainerBlockEntityAccessor','DistanceManagerAccessor','LevelTicksAccessor','AttributeMapAccessor','MobEffectInstanceAccessor')
        Assert-True ($serverConfig.required -and $serverConfig.injectors.defaultRequire -eq 1 -and ((@($serverConfig.mixins)|Sort-Object) -join ',') -ceq (($commonHooks|Sort-Object) -join ',')) 'Fabric native server resource hooks must be required on the common source set'
        $registered += @($serverConfig.mixins)
    }
    Assert-True ($registered.Count -eq $files.Count) "$($target.Name) Mixin config and owned Hook source count must agree"
    Assert-True ((($registered | Sort-Object) -join ',') -ceq ((@($files | ForEach-Object BaseName) | Sort-Object) -join ',')) "$($target.Name) Mixin config must register each audited source exactly once"

    [pscustomobject]@{
        Target = $target.Name
        HookSources = $files.Count
        Injects = ([regex]::Matches($combined, '@Inject\s*\(')).Count
        Invokers = ([regex]::Matches($combined, '@Invoker\s*\(')).Count
        Accessors = ([regex]::Matches($combined, '@Accessor\s*\(')).Count
        Overwrites = 0
        Cancellable = $cancellableCount
        OperatorRedirect = $operatorRedirectCount
        CursorArgumentReplacement = $cursorArgumentCount
        OperatorReplacement = $operatorReplacementCount
        PassiveDrawDelegation = $passiveDrawCount
        PassiveWorldDelegation = $passiveWorldCount
        Replacement = $replacementCount
        CompiledDelegationTests = 'REQUIRED_NOT_EXECUTED_BY_STATIC_GATE'
        ThirdPartyTargets = 0
        Result = 'PASS'
    }
}

[pscustomobject]@{
    Result = 'PASS'
    Policy = 'capability_fidelity_first'
    Targets = $results
}
}
