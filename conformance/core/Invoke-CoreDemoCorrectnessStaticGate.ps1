[CmdletBinding()]
param()
$ErrorActionPreference='Stop'
$root=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
function Require([bool]$Condition,[string]$Reason){if(-not $Condition){throw "Core demo correctness: $Reason"}}
$targets=@('1.20.1-forge','1.21.1-neoforge','26.1.2-neoforge','26.2-neoforge','26.2-fabric')
foreach($target in $targets){
    $source=if($target -eq '26.2-fabric'){'client'}else{'main'}
    $runtimeName=if($target -eq '1.20.1-forge'){'ForgeProbeRuntime'}elseif($target -eq '26.2-fabric'){'FabricProbeRuntime'}else{'NeoForgeProbeRuntime'}
    $base=Join-Path $root "versions/$target/src/$source/java/io/github/recrivenvi/minecraftprotocol/probe"
    $runtime=Get-Content (Join-Path $base "runtime/$runtimeName.java") -Raw
    $storage=Get-Content (Join-Path $base 'runtime/PersistentStorageAdapter.java') -Raw
    $mouse=Get-Content (Join-Path $base 'mixin/MouseHandlerMixin.java') -Raw
    $window=Get-Content (Join-Path $base 'mixin/WindowMixin.java') -Raw
    $frame=Get-Content (Join-Path $base 'mixin/MinecraftMixin.java') -Raw
    Require ($runtime -match 'getChunk\(x >> 4, z >> 4,[\s\S]*?FULL, false\) == null') "$target must check the actual client chunk cache without loading"
    Require ($runtime -match 'readSavedStorage' -and $runtime -match 'rememberStorageContext') "$target must retain detached post-quit storage context"
    Require ($storage -match 'readIdentity' -and $storage -match 'classifyIo' -and $storage -match 'PERSISTED_STORAGE_BUSY') "$target must distinguish IO availability from corruption"
    Require ($storage -match 'shared_read_only_session_lock' -and $storage -match 'offline_file_snapshot') "$target offline reads must carry truthful lifecycle and read guard"
    Require ($runtime -match 'beforeOperatorChrome' -and $runtime -match 'captureContentAtBoundary' -and $runtime -match 'contentFrameReady') "$target captures must be ordered before chrome on a freshly rendered frame"
    Require ($runtime -notmatch 'captureInProgress|50L, TimeUnit.MILLISECONDS\);[\s\S]{0,100}return result;') "$target must not use timed chrome suppression"
    Require ($frame -match 'beforePresent' -and $frame -match 'virtualKeymappingConsumption') "$target final rendering and routed keymapping consumption hooks are missing"
    Require ($mouse -match 'method = "grabMouse"' -and $mouse -match 'allowHostMouseGrab') "$target must prevent native grab at its entry point"
    Require ($window -match 'onHostFocus' -and $window -match 'onVanillaWindowIcon') "$target must observe focus and preserve real icons"
    Require ($runtime -notmatch 'glfwSetWindowIcon\(window, null\)') "$target must not restore the platform default icon"
    Require ($runtime -match 'onControlledClient' -and $runtime -match 'transitionSequence\(\) != accepted.transitionSequence') "$target queued input needs a control-generation barrier"
}
$client=Get-Content (Join-Path $root 'companion/src/runtime-client.ts') -Raw
$result=Get-Content (Join-Path $root 'companion/src/result.ts') -Raw
Require ($client -match 'manualRevocationReason' -and $result -match 'reconsentRequired: runtime.reconsentRequired') 'MCP must preserve structured manual revocation'
$hookGate=& (Join-Path $root 'conformance/phase7/Invoke-Phase7HookCompatibilityGate.ps1')
Require ($hookGate.Result -eq 'PASS') 'Hook source/manifest policy must remain aligned'
[pscustomobject]@{Result='PASS';Targets=5;CaptureOrder='CONTENT_READBACK_THEN_OPERATOR_CHROME';PersistentWrite='NOT_IMPLEMENTED';NativeInputLive='SEPARATE_REQUIRED_GATE'}
