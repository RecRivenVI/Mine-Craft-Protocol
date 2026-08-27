# Phase 4 Live Observation and Capture

> Status: complete for all five Runtime Targets (1.21.1/26.1.2 promoted during Phase 6)  
> Date: 2026-08-28  
> Contract status: unstable V0; Wire Protocol v1 is not frozen

## Delivered Capabilities

- Explicit client-known LIVE Player, Block and Entity observations.
- Explicit Integrated Server-authoritative LIVE Player, Block and Entity observations.
- Typed authority unavailability outside an Integrated Server world.
- No-load Server block query for loaded chunks only.
- LIVE-only Provider Read SPI with third-party trust propagation.
- Coordinated best-effort State Frame with per-read snapshot IDs.
- Actual graphics-device backend reporting.
- Composite PNG capture with IO-pool encoding.
- Capture/input parallel execution.

## Data Source Contract

Every observation carries:

```text
perspective
source
authority
dataSource=LIVE
storageAccessed=false
stalePossible
```

Client and Server sources are never silently substituted for each other. A query for Server authority at the title screen returns `SERVER_AUTHORITATIVE_UNAVAILABLE`.

For a block outside loaded Server chunks:

```text
available=false
reason=chunk_not_loaded
chunkLoadRequested=false
storageAccessed=false
```

No region, playerdata, level data or other persisted state is read by Phase 4 endpoints.

## State Frame

State Frames accept up to 32 provider reads and return:

```text
stateFrameId
stateFrameSequence
consistency=coordinated_best_effort
startedAtMillis
completedAtMillis
querySnapshotId per read
providerRevision per read
```

This correlates Client and Server snapshots without claiming an impossible global world transaction.

## Provider SPI

The Java SPI consists of:

```text
ReadProvider
MinecraftProtocolProviders.register(...)
```

Providers are explicit, namespaced and detached-JSON-only. `minecraft:` is reserved for built-ins. The bundled `minecraft_protocol_probe:echo` provider validates registration, discovery, query execution and untrusted third-party metadata on all three Targets.

## Runtime Evidence

`conformance/phase4/Invoke-Phase4ObservationConformance.ps1` passed in the following matrix:

| Target | Backend | Client LIVE | Integrated authority | Provider/Frame | Capture + Input |
|---|---|---:|---:|---:|---:|
| Forge 1.20.1 | OpenGL | PASS | PASS | PASS | PASS |
| NeoForge 1.21.1 | OpenGL | PASS | PASS | PASS | PASS |
| NeoForge 26.1.2 | OpenGL | PASS | PASS | PASS | PASS |
| NeoForge 26.2 | OpenGL | PASS | PASS | PASS | PASS |
| NeoForge 26.2 | Vulkan | PASS | PASS | PASS | PASS |
| Fabric 26.2 | OpenGL | PASS | PASS | PASS | PASS |
| Fabric 26.2 | Vulkan | PASS | PASS | PASS | PASS |

The authority test verifies:

- Client and Server Player UUID equality;
- explicit Server thread evidence;
- loaded Block ID agreement;
- unloaded distant block refusal;
- distinct Client/Server Entity sources;
- a six-read Client+Server State Frame.

The concurrency test holds W in a Pipeline, then starts eight Composite captures concurrently. The Pipeline continues through mouse-move steps, completes normally, all captures have valid PNG signatures and Runtime-owned input finishes clear. On 2026-08-28 this complete gate, including Integrated Server authority, was rerun under Vulkan for both 26.2 Targets.

## Remaining Boundaries

- Ordinary remote servers without Peer cannot expose Server authority.
- Dedicated Server Peer was subsequently delivered in Phase 6.
- Persistent Storage remains unavailable and belongs to later Ultimate work under a separate namespace.
- State Frame consistency is best-effort, not transactional.
- Provider code is part of the registering Mod's trust/threading boundary; its output remains untrusted data.

## Exit Decision

Phase 4 exit conditions are satisfied for all five Runtime Targets:

- Client and Server observation sources are explicit;
- no basic query implicitly reads Persistent Storage or loads an unloaded chunk;
- Composite Capture and real input execute concurrently;
- 26.2 OpenGL/Vulkan backend reporting is runtime-derived and verified;
- State Frame and Provider Read SPI are executable.

The next execution-plan phase is Phase 5: Recording, Artifact and baseline Fixture/Debug capabilities.
