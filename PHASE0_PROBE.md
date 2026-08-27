# Phase 0 Probe

## Work Context

```text
Repository: Mine-Craft-Protocol
Targets: 1.20.1-forge, 26.2-neoforge, 26.2-fabric
Baseline: Phase 0 external behavior loop; no implementation baseline yet
Direct Port Reference: none
Change category: runtime/test infrastructure + target-specific adaptation
```

This file records the executable Phase 0 probe contract. It is not a stable wire protocol and must not be treated as Protocol V1.

## Minimum External Loop

The three probes investigate the same observable loop:

```text
runtime startup
loopback authentication
capability self-test
session and capability query
title-screen interaction tree
selector resolution
game-routed click
basic keyboard and mouse movement
player movement and view control
inventory/slot interaction through the normal screen/menu path
one world interaction
player state
single block query
simple entity query
composite capture
WebSocket event
wait.until
minimal trace and provenance
```

## Minimum Provenance

Each performed action must report what was actually observed:

```text
entryLayer
screenObserved
menuObserved
normalPacketObserved
serverValidationObserved
directBusinessCallUsed
directMutationUsed
clientTick
serverTick
renderFrame
screenRevision
menuRevision
```

Missing evidence is reported as unknown or unavailable, never inferred as successful.

## Probe Evidence Template

Each target records:

```text
runtime startup:
client thread identity:
render thread identity:
integrated server thread identity:
current screen access path:
screen child access path:
container slot access path:
mouse entry path:
keyboard entry path:
key mapping path:
container packet path:
composite capture path:
render facts path:
OpenGL status:
Vulkan status:
required accessor/invoker/instrumentation:
known limitations:
```

## Exit Rule

Phase 0 evidence is accepted only when produced by source inspection plus a compiling or running target probe. A Loader API name, Mixin target, or assumed method name is not evidence by itself.

## Verified Environment

```text
JDK 17: Eclipse Adoptium 17.0.20.8
JDK 25: BellSoft Liberica 25.0.4
Forge probe tooling: ModDevGradle LegacyForge 2.0.144
NeoForge probe tooling: ModDevGradle 2.0.144
Fabric probe tooling: Loom 1.17.20
Forge: 1.20.1-47.4.21
NeoForge: 26.2.0.67
Fabric Loader: 0.19.3
```

All three probe source sets compile. Forge 1.20.1 and NeoForge 26.2 use Gradle 9.2.1 during the probe; Fabric 26.2 uses Gradle 9.5.1.

## Phase 0B — Forge 1.20.1 Evidence

```text
runtime startup: PASS
loopback auth: PASS, 401 without Bearer token
client thread identity: Render thread
current screen access path: Minecraft.screen
screen child access path: Screen.children(), public
container slot access path: AbstractContainerScreen.getGuiLeft/getGuiTop + getMenu
mouse entry path: Invoker → MouseHandler.onMove/onPress/onScroll
keyboard entry path: KeyboardHandler.keyPress
key mapping path: KeyboardHandler → KeyMapping → LocalPlayer input
container path: MouseHandler → AbstractContainerScreen.slotClicked → MultiPlayerGameMode.handleInventoryMouseClick
container packet: ServerboundContainerClickPacket observed
server validation: ServerGamePacketListenerImpl.handleContainerClick observed
composite capture: Screenshot.takeScreenshot(RenderTarget) → NativeImage PNG
OpenGL status: PASS
```

Runtime evidence:

- Title screen Interaction Tree exposed stable widget labels and bounds.
- GAME_ROUTED_RAW click moved from the Forge loading/onboarding screen to TitleScreen.
- Keyboard events selected and entered an existing world.
- Holding W for approximately 24 client ticks moved the player from `z=-38.5` to `z=-33.5789837313969` without direct mutation.
- Inventory Screen produced 46 slot nodes.
- One slot click incremented Screen Slot, Menu Dispatch and client packet counters; the integrated server observed `ServerboundContainerClickPacket` on the server thread.
- Client-known block query returned `minecraft:grass_block` below the player.
- Composite Capture returned a PNG with the standard `89 50 4E 47 0D 0A 1A 0A` signature.

Transport fact: Minecraft 1.20.1 does not ship `netty-codec-http`; the probe must place the matching Netty 4.1.82 module on ModDevGradle's `additionalRuntimeClasspath`. This is not yet a production packaging decision.

## Phase 0C — NeoForge 26.2 Evidence

```text
runtime startup: PASS on OpenGL
loopback auth: PASS
client thread identity: Render thread
current screen access path: Minecraft.gui.screen()
screen child access path: Screen.children(), public
container slot access path: NeoForge getLeftPos/getTopPos extension + getMenu
mouse entry path: Invoker → MouseHandler.onMove/onButton/onScroll
keyboard entry path: Invoker → KeyboardHandler.keyPress(KeyEvent)
container input type: ContainerInput
render facts: GuiRenderState addElement/addItem/addText/addPicturesInPictureState
OpenGL Composite Capture: PASS
Vulkan default early window: BLOCKED before Minecraft tick
Vulkan with earlyWindowControl=false: PASS
Vulkan Composite Capture: PASS
```

26.2 `Screenshot.takeScreenshot` uses the graphics-device abstraction and `copyTextureToBuffer`; the same callback path returned valid PNG data on OpenGL and Vulkan.

NeoForge's default early loading window is currently a separate Vulkan compatibility boundary. Disabling `earlyWindowControl` allows Minecraft Vulkan startup and does not require a different Runtime capture contract.

## Phase 0D — Fabric 26.2 Evidence

```text
runtime startup: PASS on OpenGL and Vulkan
loopback auth: PASS, 401 without Bearer token
WebSocket hello: PASS
client thread identity: Render thread
current screen access path: Minecraft.gui.screen()
screen child access path: Screen.children(), public
container slot access path: Accessor for vanilla leftPos/topPos + getMenu
mouse/keyboard paths: same vanilla 26.2 signatures as NeoForge
render facts: PASS on OpenGL and Vulkan
OpenGL Composite Capture: PASS
Vulkan Composite Capture: PASS
```

Fabric exposed the first confirmed Loader difference: vanilla 26.2 does not expose NeoForge's `getLeftPos/getTopPos` convenience methods, so a read-only Accessor is required for container slot bounds.

## Cross-Target Findings

1. `Screen.children()` is a viable Interaction Tree root on all three probe Targets.
2. Container slots are not ordinary Screen children and require a separate Menu/Slot projection.
3. GAME_ROUTED_RAW requires private input-handler access on all Targets; 26.2 also requires structured input records.
4. Screen changes can occur inside an input call before the next client tick. Screen revision refresh must occur on tick and on relevant queries/mutations.
5. A global world revision is unnecessary for the tested loop; screen/menu resource revisions and value observations are sufficient.
6. 26.2 Render Facts can be observed passively at `GuiRenderState` submission methods.
7. Render Facts are primitive facts only; they do not reconstruct semantics inside a custom texture.
8. 26.2 Composite Capture can use one GPU abstraction for OpenGL and Vulkan.
9. NeoForge's early window is a Loader-specific Vulkan startup constraint, not a Capture API constraint.
10. Transport dependency packaging differs materially between 1.20.1 and 26.2 and must remain a later engineering decision.
11. `ServerGamePacketListenerImpl.handleContainerClick` is entered first on a Netty IO thread and then again after `PacketUtils.ensureRunningOnSameThread` on the Server thread. Server-validation evidence must be hooked after this scheduling boundary, not at method HEAD.

## Current Phase Status

```text
Phase 0A: complete
Phase 0B: minimum loop verified
Phase 0C: UI/Input/Render/OpenGL/Vulkan capture paths verified
Phase 0D: UI/Input/Render/OpenGL/Vulkan capture paths verified
Phase 0E: findings recorded above
Phase 0F: Protocol V0 Draft is maintained separately
Phase 0G: complete; three real targets promoted to versions/ and exposed through one explicit root Gradle build
Phase 0H: complete; Conformance V0 passed against all three promoted targets with active integrated worlds
```

## Promoted Repository Build

The root repository explicitly includes only implemented targets:

```text
:versions:1.20.1-forge
:versions:26.2-neoforge
:versions:26.2-fabric
```

The Gradle 9.5.1 root wrapper completed all three target `build` tasks in one invocation. No 1.21.1 or 26.1.2 placeholder target was created.

## Conformance V0 Results

| Target | Auth | UI Tree | GAME_ROUTED | World Query | Container/Server Trace | Capture | WebSocket | Result |
|---|---|---|---|---|---|---|---|---|
| 1.20.1 Forge | PASS | PASS | PASS | PASS | PASS | OpenGL PASS | PASS | PASS |
| 26.2 NeoForge | PASS | PASS | PASS | PASS | PASS | OpenGL PASS; Vulkan PASS with early window disabled | PASS | PASS |
| 26.2 Fabric | PASS | PASS | PASS | PASS | PASS | OpenGL PASS; Vulkan PASS | PASS | PASS |

Each promoted target completed an active-world Conformance run that validated authenticated session/capabilities, Interaction Tree, PNG Capture, WebSocket hello, player state, loaded block query and entity query. Container clicks were separately verified through Screen → Menu → client packet → Server thread validation.
