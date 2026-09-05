[CmdletBinding()]param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
function Require([bool]$ok,[string]$reason){if(-not$ok){throw "Control implementation: $reason"}}
$schema=Get-Content (Join-Path $root 'protocol-schema/src/main/openapi/minecraft-control-v0.json') -Raw|ConvertFrom-Json
Require ($schema.info.version-eq'0.0.1-control-r24') 'wrong Native contract version'
Require ($null-ne$schema.paths.'/v0/input/mouse/delta') 'relative input route missing'
$mode=& (Join-Path $PSScriptRoot 'Invoke-ControlRound1StaticGate.ps1')
Require ($mode.Result-eq'PASS') 'Round 1 intention/authorization contract regression'
$hooks=& (Join-Path $PSScriptRoot '../phase7/Invoke-Phase7HookCompatibilityGate.ps1')
Require ($hooks.Result-eq'PASS') 'Hook source/manifest mismatch'
foreach($target in @('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.2-neoforge','26.2-fabric')){
 $source=if($target-eq'26.2-fabric'){'client'}else{'main'}
 $name=if($target-eq'1.20.1-forge'){'ForgeProbeRuntime'}elseif($target-eq'26.2-fabric'){'FabricProbeRuntime'}else{'NeoForgeProbeRuntime'}
 $directory=Join-Path $root "versions/$target/src/$source/java/io/github/recrivenvi/minecraftprotocol/probe"
 $runtime=Get-Content (Join-Path $directory "runtime/$name.java") -Raw
 $keyboard=Get-Content (Join-Path $directory 'mixin/KeyboardHandlerMixin.java') -Raw
 $mouse=Get-Content (Join-Path $directory 'mixin/MouseHandlerMixin.java') -Raw
 $constants=Get-Content (Join-Path $directory 'mixin/InputConstantsMixin.java') -Raw
 $engine=Get-Content (Join-Path $directory 'runtime/AutomationEngine.java') -Raw
 Require ($keyboard-match'nativeTask'-and$keyboard-match'AgentInputContext.consume'-and$keyboard-match'charTyped') "$target keyboard/character origin missing"
 if($target.StartsWith('26.')){Require ($keyboard-match'preeditCallback'-and$keyboard-match'resubmitLastPreeditEvent') "$target IME boundary missing"}
 Require ($mouse-match'nativeTask'-and$mouse-match'method = "onMove"'-and$mouse-match'method = "onScroll"'-and$mouse-match'method = "onDrop"') "$target native mouse boundary missing"
 Require ($runtime-notmatch'humanCursorCaptureGranted = true|glfwSetCursorPos') "$target still grants/warps host cursor"
 Require ($constants-match'grabOrReleaseMouse'-and$constants-match'isKeyDown') "$target standard capture/polling guard missing"
 Require ($runtime-match'validatePointerGuard'-and$runtime-match'interactionIdentity'-and$runtime-match'mouseDelta') "$target atomic GUI/relative pointer path missing"
 Require ($engine-match'InputSequenceQueue'-and$engine-match'armDeadline'-and$engine-match'POINTER_HELD') "$target bounded ownership missing"
 Require ($engine-match'AgentPointer.interpolate'-and$engine-match'UI_TARGET_INVALIDATED'-and$engine-match'mouseButtonGuarded') "$target deterministic target-checked input missing"
 Require ($runtime.IndexOf('this.evidenceCaptures.beforeOperatorChrome')-lt$runtime.IndexOf('renderChrome(graphics, alpha)')) "$target Operator pass precedes evidence copy"
 Require ($runtime-match'ControlChrome.pointer'-and$runtime-match'ControlChrome.panel'-and$runtime-match'top = 8') "$target pixel Operator UI missing"
}
$chrome=Get-Content (Join-Path $root 'runtime-safety/src/main/java/io/github/recrivenvi/minecraftprotocol/safety/ControlChrome.java') -Raw
foreach($message in @('智能体正在读取您的实例','智能体正在操控您的实例','智能体已接管您的实例 · Esc 以退出')){Require ($chrome.Contains($message)) 'mode copy mismatch'}
[pscustomobject]@{Result='PASS';Targets=5;HttpOperations=$mode.ClassifiedHttpOperations;McpTools=$mode.ClassifiedMcpTools;ImplementationScope='EXCLUSIVE_INPUT_LOGICAL_POINTER_PIXEL_CHROME';UnifiedAcceptance='PENDING';HumanDesktopEvents='NOT_SIMULATED';WireProtocolV1='NOT_FROZEN'}
