[CmdletBinding()]param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../../../..')).Path
$runtimeCore=Join-Path (Resolve-Path (Join-Path $PSScriptRoot '../../../..')) 'components/runtime_core/src/main/java/io/github/recrivenvi/minecraftprotocol/core'
function Require([bool]$ok,[string]$reason){if(-not$ok){throw "Control implementation: $reason"}}
$schema=Get-Content (Join-Path $root 'components/protocol_schema/src/main/openapi/minecraft-control-v0.json') -Raw|ConvertFrom-Json
$surface=& (Join-Path $PSScriptRoot '../../conformance-native_protocol/task/Invoke-NativeSurfaceConformance.ps1')
Require ($surface.Result-eq'PASS') 'current Native/MCP/documented surface mismatch'
Require ($schema.info.version-eq'0.0.1-ui-m1') 'wrong Native contract version'
Require ($null-ne$schema.paths.'/v0/input/mouse/delta') 'relative input route missing'
$cursorContract=& (Join-Path $PSScriptRoot 'Test-ControlCursorContract.ps1')
Require ($cursorContract.Result-eq'PASS') 'cursor contract response classifier failed'
$mode=& (Join-Path $PSScriptRoot 'Invoke-ControlRound1StaticGate.ps1')
Require ($mode.Result-eq'PASS') 'Round 1 intention/authorization contract regression'
$hooks=& (Join-Path $PSScriptRoot '../../compatibility-standard_gui/task/Invoke-Phase7HookCompatibilityGate.ps1')
Require ($hooks.Result-eq'PASS') 'Hook source/manifest mismatch'
foreach($target in @('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.3-neoforge','26.3-fabric')){
 $source=if($target-eq'26.3-fabric'){'client'}else{'main'}
 $name=if($target-eq'1.20.1-forge'){'ForgeProbeRuntime'}elseif($target-eq'26.3-fabric'){'FabricProbeRuntime'}else{'NeoForgeProbeRuntime'}
 $directory=Join-Path $root "versions/$target/src/$source/java/io/github/recrivenvi/minecraftprotocol/probe"
 $runtime=Get-Content (Join-Path $directory "runtime/$name.java") -Raw
 $keyboard=Get-Content (Join-Path $directory 'mixin/KeyboardHandlerMixin.java') -Raw
 $mouse=Get-Content (Join-Path $directory 'mixin/MouseHandlerMixin.java') -Raw
 $constants=Get-Content (Join-Path $directory 'mixin/InputConstantsMixin.java') -Raw
 $engine=Get-Content (Join-Path $runtimeCore 'AutomationEngine.java') -Raw
 $state=Get-Content (Join-Path $runtimeCore 'ProtocolState.java') -Raw
 $transport=Get-Content (Join-Path $runtimeCore 'ProbeTransport.java') -Raw
 Require ($state-match'AgentTaskLifecycle'-and$state-match'this::expireTaskIfNeeded'-and$state-match'leaseTaskId'-and$state-match'inputCleanupBarrier.thenApply') "$target bounded task/attached input cleanup missing"
 Require ($transport-match'path.equals\("/v0/control/task"\)'-and$transport-match'protocolState.agentActivity\(\)'-and-not$transport.Contains('presenceChannels')) "$target presence is still bound to TCP channel lifetime"
 Require ($runtime-match'logicalPointer.viewport\('-and$runtime-match'gui\s*\? this.logicalPointer.x\(\)') "$target page/viewport pointer continuity missing"
 Require ($runtime-match'logicalPointer.gameCapture\('-and$runtime-match'logicalPointer.gameRequest\('-and$runtime-match'logicalPointer.gameCursorVisible\('-and$constants-match'onGameCursorRequest') "$target effective game positioning/visibility path missing"
 Require ($runtime-match'Game cursor changed during pointer dispatch'-and$engine-match'"gameCursorRevision"') "$target stale game cursor gesture guard missing"
 if($target.StartsWith('26.3-')){Require ($mouse-match'method = "resyncMousePosition"'-and$runtime-match'handleMouseResync'-and$runtime-match'restoreAgentPointer\(client\)') "$target host resync must not overwrite Agent coordinates"}
 Require ($runtime-match'operatorChrome.textWidth\(this.minecraft.font::width\)'-and$runtime-match'operatorChrome.text\('-and$runtime-match'graphics.enableScissor\('-and$runtime-match'graphics.disableScissor\(') "$target continuous shared text weights/native dynamic clip missing"
 Require ($keyboard-match'AgentInputContext.consume'-and$keyboard-match'charTyped') "$target keyboard/character origin missing"
 if($target.StartsWith('26.3-')){
  $sdl=Get-Content (Join-Path $directory 'mixin/SDLEventHandlerMixin.java') -Raw
  foreach($entry in @('handleKeyEvent','handleTextInputEvent','handleTextEditingEvent','handleMouseMotionEvent','handleMouseButtonEvent','handleMouseWheelEvent','handleDropCompleteEvent')){Require ($sdl.Contains($entry)) "$target SDL ingress missing: $entry"}
  Require ($sdl-match'AgentInputContext.nativeTask'-and$keyboard-match'textInput'-and$keyboard-match'textEditing'-and$keyboard-match'resubmitLastPreeditEvent') "$target SDL origin/IME boundary missing"
  Require ($runtime-match'NativeInputMapping.event'-and$runtime-match'NativeInputMapping.button') "$target wire/native semantic mapping missing"
 }else{
  Require ($keyboard-match'nativeTask'-and$mouse-match'nativeTask') "$target callback native origin missing"
  if($target.StartsWith('26.')){Require ($keyboard-match'preeditCallback'-and$keyboard-match'resubmitLastPreeditEvent') "$target IME boundary missing"}
 }
 Require ($mouse-match'method = "onMove"'-and$mouse-match'method = "onScroll"'-and$mouse-match'method = "onDrop"') "$target native mouse boundary missing"
 Require ($runtime-match'capabilities\.addProperty\(\s*"input.host_cursor_capture",\s*"blocked_during_takeover"\s*\)'-and-not$runtime.Contains('agent_gated_native_click')) "$target obsolete cursor capability"
 Require ($runtime-notmatch'humanCursorCaptureGranted = true|glfwSetCursorPos|SDL_WarpMouse') "$target still grants/warps host cursor"
 $captureGuard=if($target.StartsWith('26.3-')){$constants-match'grabMouse'-and$constants-match'releaseMouse'}else{$constants-match'grabOrReleaseMouse'}
 Require ($captureGuard-and$constants-match'isKeyDown') "$target standard capture/polling guard missing"
 Require ($runtime-match'validatePointerGuard'-and$runtime-match'interactionIdentity'-and$runtime-match'mouseDelta') "$target atomic GUI/relative pointer path missing"
 $buttonBody=[regex]::Match($runtime,'(?s)private JsonObject mouseButtonGuardedNow\(.*?(?=\n    @Override)').Value
 Require ($buttonBody-match'advanceDragGuard\(guard, this.snapshotUiTree\(\)\)' -and $buttonBody.IndexOf('this.refreshMenu(client)')-lt$buttonBody.IndexOf('AutomationEngine.advanceDragGuard')) "$target drag must advance its own pickup state immediately after the native button callback, not in an unrelated key callback"
 $keyBody=[regex]::Match($runtime,'(?s)private JsonObject keyGuardedNow\(.*?(?=\n    @Override)').Value
 Require (-not$keyBody.Contains('AutomationEngine.advanceDragGuard')) "$target key input must not adopt unrelated drag item changes"
 Require ($engine-match'InputSequenceQueue'-and$engine-match'armDeadline'-and$engine-match'POINTER_HELD') "$target bounded ownership missing"
 Require ($engine-match'AgentPointer.interpolate'-and$engine-match'UI_TARGET_INVALIDATED'-and$engine-match'mouseButtonGuarded') "$target deterministic target-checked input missing"
 Require ($engine-match'AgentPointer.approach'-and$engine-match'InteractionPacing.movementSteps'-and$engine-match'pacedStep\(step, this\)'-and$engine-match'if \(release\) ready') "$target cancellable spectator pacing/release bypass missing"
 Require ($runtime-match'addSlotBounds\(node, left \+ slot.x, top \+ slot.y\)'-and$runtime-match'"x", itemX - 1'-and$runtime-match'"y", itemY - 1') "$target native slot hit box origin mismatch"
 Require ($runtime-match'viewIndex < capturedSlots'-and$runtime-match'capturedSlots = Math.min\(menu.slots.size\(\), slotBudget\)'-and$runtime-match'addSlotIdentity\(\s*node,\s*slot,\s*viewIndex'-and$runtime-match'"slot", viewIndex'-and$runtime-match'"nativeSlotIndex", slot.index'-and$runtime-notmatch'"slot", slot.index|"nodeId", "slot:" \+ this.screenRevision \+ ":" \+ slot.index') "$target creative wrappers still alias semantic slot identities"
 Require ($runtime-match'addSlotVisibility\(node, screen.width, screen.height\)'-and$runtime-match'menuHash\(AbstractContainerMenu menu\)'-and$engine-match'"menuIdentity"') "$target current screen menu/hidden slot guard missing"
 Require ($runtime-match'AutomationEngine.interactionPoint\(node\)'-and$engine-match'AgentPointer.center'-and$engine-match'fromPoint = offsetPoint\(from, step, "fromOffset"\)'-and$engine-match'toPoint = offsetPoint\(to, step, "toOffset"\)'-and$engine-match'if \(!step.has\(key\)\) return interactionPoint\(node\)'-and$engine-match'INVALID_UI_OFFSET') "$target target geometry/default center/normalized drag contract mismatch"
 Require ($engine-match'request.has\("selector"\) == request.has\("coordinates"\)') "$target ambiguous UI targeting is not rejected"
 Require ($runtime.IndexOf('this.evidenceCaptures.beforeOperatorChrome')-lt$runtime.IndexOf('renderChrome(graphics, alpha)')) "$target Operator pass precedes evidence copy"
 Require ($runtime-match'instanceof net.minecraft.client.gui.screens.LoadingOverlay\) return') "$target Operator text can compile before initial resource reload is ready"
 Require ($runtime-match'ControlChrome.pointer'-and$runtime-match'ControlChrome.panel'-and$runtime-match'top = 8') "$target pixel Operator UI missing"
 Require ($runtime-match'WindowDecoration.title'-and$runtime-match'WindowDecoration.icon'-and$runtime-match'original_with_agent_pointer'-and$runtime-match'publishWindowIcons\(window, false\)'-and$runtime-notmatch'applyAgentIcon|agent_placeholder|ImageIO.read') "$target final-output decoration/original icon restoration missing"
 Require ($runtime-match'ControlChrome\s*\.PointerTransform'-and$runtime-match'graphics.pose\(\).translate\('-and$runtime-match'this.logicalPointer.x\(\),\s*this.logicalPointer.y\(\),\s*width,\s*height,'-and$runtime-notmatch'Math.round\(this.logicalPointer.[xy]\(\)\)') "$target fractional orb hotspot is quantized or native pose adapter missing"
 if($target.StartsWith('26.')){Require ($runtime-match'windowState.width / \(float\) windowState.guiScale'-and$runtime-match'windowState.height / \(float\) windowState.guiScale') "$target orb origin ignores native float projection"}
 Require ($runtime-match'logicalPointer.button\('-and$runtime-match'logicalPointer.pressScale\(System.nanoTime\(\)\)'-and$runtime-match'logicalPointer.releaseButtons\(\)') "$target delivered-button visual feedback/cleanup missing"
}
$chrome=Get-Content (Join-Path $root 'components/runtime_safety/src/main/java/io/github/recrivenvi/minecraftprotocol/safety/ControlChrome.java') -Raw
Require ($chrome-match'!textVisible\(fade\)'-and$chrome-match'if \(textVisible\(opacity\)\)'-and$chrome-match'(?s)finally\s*\{\s*renderer.disableScissor\(\)') 'shared zero-alpha Vanilla text flash guard/native clip restoration missing'
foreach($message in @('智能体正在读取您的实例','智能体正在操控您的实例','智能体已接管您的实例 · Esc 以退出')){Require ($chrome.Contains($message)) 'mode copy mismatch'}
[pscustomobject]@{Result='PASS';Targets=5;HttpOperations=$mode.ClassifiedHttpOperations;McpTools=$mode.ClassifiedMcpTools;ImplementationScope='EXCLUSIVE_INPUT_LOGICAL_POINTER_PIXEL_CHROME';UnifiedAcceptance='PENDING';HumanDesktopEvents='NOT_SIMULATED';WireProtocolV1='NOT_FROZEN'}
