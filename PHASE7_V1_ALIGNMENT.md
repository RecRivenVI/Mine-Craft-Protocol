# Phase 7 Five-Target V1 Alignment

> Status: implemented and runtime-verified on all five Targets  
> Date: 2026-08-28  
> Contract status: unstable V0 alignment baseline; Wire Protocol v1 remains unfrozen

## Delivered Scope

Phase 7 closes the first five-Target V1 alignment pass:

- all Targets expose the same external V0 core operations;
- the standard Mod GUI fixture now exercises a broader Widget surface;
- selector actions fail closed when the Interaction Tree declares a node disabled, hidden or unsupported;
- every Target exposes a typed Hook manifest with runtime self-test state;
- a static Hook compatibility gate rejects high-conflict mechanisms and third-party targets;
- capability differences are explicit rather than hidden by a shared Loader abstraction.

## Extended Standard Mod GUI Fixture

The fixture remains a typed `DIRECT` Arrange operation and contaminates evidence. All subsequent interactions use Interaction Tree resolution and `GAME_ROUTED_RAW` input.

It now contains:

```text
EditBox: Compatibility Text
mutable Probe Action button
Add Dynamic Control button
two identical Duplicate Action buttons
disabled Disabled Action button
Close Probe button
runtime-added Dynamic Control button
```

The five-Target black-box scenario verifies:

- `EditBox` projects as `role=text_field` and receives routed Screen clicks;
- disabled state projects as `active=false` with no actions;
- selector action on a disabled node fails `UI_NODE_NOT_ACTIONABLE`;
- duplicate selectors fail `UI_SELECTOR_AMBIGUOUS` without `nth`;
- `nth=1` selects the second duplicate with its own bounds;
- a Widget added after Screen initialization appears without replacing the Screen;
- the dynamic Widget can be activated by semantic selector;
- Composite capture remains valid while the compatibility Screen is open;
- modern Render Facts describe render primitives without semantic inference;
- legacy Targets continue to report Render Facts honestly unavailable.

The fail-closed action check applies only to selector-based semantic actions. Explicit/Vision coordinates remain available as the required fallback for render-only or otherwise non-semantic GUI content.

## Hook Manifest

`GET /v0/diagnostics/hooks` returns a Target-owned manifest:

```text
policy=capability_fidelity_first
overwriteCount
cancellableInjectionCount
thirdPartyTargetCount
runtimeSelfTest
overall
hooks[]:
  id
  mechanism
  target
  injectionPoint
  behavior
  cancellable
  overwrite
  thirdPartyTarget
  runtimeStatus
  failureMode
  failureCapability
```

The manifest does not claim that Hook configuration alone proves runtime health. Tick, Invoker, capture, render and container hooks report `runtime_verified`, `unverified_until_*`, `capability_unavailable` or degraded state from actual Runtime evidence.

## Static Hook Compatibility Gate

The static gate passed with:

| Target | Hook sources | Injects | Invokers | Accessors | Overwrite | Cancellable | Third-party targets |
|---|---:|---:|---:|---:|---:|---:|---:|
| Forge 1.20.1 | 6 | 5 | 3 | 0 | 0 | 0 | 0 |
| NeoForge 1.21.1 | 6 | 5 | 3 | 0 | 0 | 0 | 0 |
| NeoForge 26.1.2 | 8 | 9 | 4 | 0 | 0 | 0 | 0 |
| NeoForge 26.2 | 8 | 9 | 4 | 0 | 0 | 0 | 0 |
| Fabric 26.2 | 9 | 9 | 4 | 2 | 0 | 0 | 0 |

The gate rejects:

- `@Overwrite`;
- cancellable injections or `CallbackInfo.cancel()`;
- `@Redirect`, ModifyArg(s), ModifyVariable and ModifyConstant;
- string-based `@Mixin(targets=...)` declarations;
- Hook sources without a typed `net.minecraft` target;
- Mixin configs that silently skip required injections;
- disagreement between configured and owned Hook source counts.

This is a V1 compatibility discipline, not proof of compatibility with every possible third-party transformation. Real Mod-pack collision cases still require targeted conformance when identified.

## Public Capability Matrix

| Capability | 1.20.1 Forge | 1.21.1 NeoForge | 26.1.2 NeoForge | 26.2 NeoForge | 26.2 Fabric |
|---|---|---|---|---|---|
| V0 HTTP/WS core | verified | verified | verified | verified | verified |
| Interaction Tree | verified | verified | verified | verified | verified |
| Extended standard Widget GUI | verified fixture | verified fixture | verified fixture | verified fixture | verified fixture |
| Disabled semantic action guard | verified | verified | verified | verified | verified |
| Selector ambiguity + nth | verified | verified | verified | verified | verified |
| Dynamic Screen children | verified | verified | verified | verified | verified |
| GAME_ROUTED_RAW | verified | verified | verified | verified | verified |
| Keyboard entry | public method | public method | Invoker | Invoker | Invoker |
| Container bounds | Loader/vanilla getter | Loader/vanilla getter | Loader/vanilla getter | Loader/vanilla getter | Accessor |
| Render Facts | unavailable | unavailable | verified | verified | verified |
| OpenGL Capture | verified | verified | verified | verified | verified |
| Vulkan Capture | N/A | N/A | configured, unverified | verified | verified |
| Recording/Artifact | verified | verified | verified | verified | verified |
| Integrated authority | verified | verified | verified | verified | verified |
| Dedicated Server Peer | verified | verified | verified | verified | verified |
| Hook manifest/self-test | verified | verified | verified | verified | verified |

No capability row is synthesized by inheritance. Each value reflects that Target's implementation and gates.

## Conformance Results

`Invoke-Phase7AlignmentConformance.ps1` passed on all five Targets. `Invoke-Phase7HookCompatibilityGate.ps1` and `Invoke-Phase7LocalGate.ps1` also pass.

The local gate produces:

```text
OpenAPI: 0.0.1-phase7
Java models: 72
TypeScript files: 72
five Phase 7 artifacts
```

## Remaining Boundaries

- This phase verifies Minecraft-standard Widget patterns, not every third-party custom renderer.
- Render-only/custom-framebuffer Screens still rely on Render Facts and Screenshot/Vision fallback.
- The static Hook gate reduces known conflict risk but does not substitute for testing a reported Mod conflict.
- NeoForge 26.1.2 Vulkan remains configured but not yet gated.
- Wire Protocol v1 remains unfrozen pending Phase 8 hardening and Companion integration.

## Exit Decision

Phase 7 exit conditions are satisfied: all five Targets expose the V1 core declaration surface, standard Widget compatibility and Hook policy are executable, and every known Target difference is represented as capability/provenance rather than hidden implementation inheritance.

The next execution-plan phase is Phase 8: MCP Companion and V1 release hardening.
