# Phase 5 Recording, Artifact and Debug

> Status: complete for all five Runtime Targets (1.21.1/26.1.2 promoted during Phase 6)  
> Date: 2026-08-28  
> Contract status: unstable V0; Recording codec and Wire Protocol v1 are not frozen

## Delivered Capabilities

- Bounded continuous Composite frame capture.
- Selected Provider State Frame track.
- Screen/Menu revision and Runtime input-state indexing.
- Input/Pipeline/runtime event timeline.
- Fixed-capacity asynchronous writer and explicit gaps.
- Automatic Contact Sheet.
- Versioned Artifact Bundle with checksums and ZIP download.
- Experimental binary canonical store plus NDJSON debug export.
- Default-disabled Fixture and Debug scopes.
- World-fingerprint/TTL Debug Arm.
- Strongly typed Fixture teleport, Debug health and Debug block operations.
- Evidence contamination propagation into Recording and Artifact.

## Recording Backpressure

```text
Scheduler
  → at most 2 capture/state samples in flight
  → fixed 64-entry writer queue
  → writer/finalizer workers
```

If acquisition or persistence cannot keep up, the Runtime increments a gap and drops work. It does not block Client, Render or Server threads. Recording conformance executes mouse movement and a multi-key Pipeline during capture; both complete normally.

## Artifact Layout

```text
manifest.json
frame-index.json
frames/*.png
state/*.json
timeline/timeline.ndjson
canonical/store-v0.bin
derivatives/contact-sheet.png
checksums.json
bundle.zip
```

`manifest.json` records `mcp-artifact-v0`, Phase 5 schema, configuration, backpressure policy, gap/error counts and evidence contamination.

The canonical store is behind an interface and uses an experimental `MCPR` length-prefixed binary layout. `frozen=false` is mandatory. NDJSON is a readable export, not the long-term canonical truth.

## Debug Safety

Fixture and Debug are absent from default scopes. Phase 5 conformance enables them explicitly.

Debug Arm requires the active Control Lease, current world fingerprint and a maximum 60-second TTL. It fails after expiry, explicit disarm or fingerprint mismatch.

The representative APIs are Minecraft-domain typed:

```text
fixture.player.teleport
debug.player.health
debug.world.block
```

The block API accepts `expectedBlockId`, operates only on loaded live state and never exposes reflection. Conformance writes the existing block state back to the same position, exercising the mutation path without materially modifying the world.

## Evidence Contamination

Fixture/Debug results include:

```text
mode
perspective
mechanism
directMutationUsed
storageAccessed=false
evidenceContaminated=true
```

An active Recording receives `evidence.contamination` timeline records and sets the manifest flag. These recordings cannot be reported as pure gameplay acceptance.

## Runtime Evidence

`Invoke-Phase5RecordingDebugConformance.ps1` passed on:

| Target | Backend | Frames | State Frames | Contact Sheet | ZIP | Debug Arm | Contamination |
|---|---|---:|---:|---:|---:|---:|---:|
| Forge 1.20.1 | OpenGL | 20 | 20 | PASS | PASS | PASS | PASS |
| NeoForge 1.21.1 | OpenGL | 20 | 20 | PASS | PASS | PASS | PASS |
| NeoForge 26.1.2 | OpenGL | 20 | 20 | PASS | PASS | PASS | PASS |
| NeoForge 26.2 | OpenGL | 20 | 20 | PASS | PASS | PASS | PASS |
| NeoForge 26.2 | Vulkan | 20 | 20 | PASS | PASS | PASS | PASS |
| Fabric 26.2 | OpenGL | 20 | 20 | PASS | PASS | PASS | PASS |
| Fabric 26.2 | Vulkan | 20 | 20 | PASS | PASS | PASS | PASS |

Each Artifact contains valid frame/state tracks, `MCPR` magic, checksums, Contact Sheet and contamination entries. Artifact download works entirely through the authenticated loopback protocol.

The 2026-08-28 Vulkan revalidation produced zero Recording gaps. NeoForge emitted a 17,240,503-byte Artifact and Fabric an 18,154,460-byte Artifact while preserving the same 20-frame/20-State-Frame contract.

`Invoke-Phase5DefaultScopeConformance.ps1` separately verifies that default Runtime configuration returns `SCOPE_DENIED` for Fixture and Debug.

## Remaining Boundaries

- This is basic Recording, not full per-tick Keyframe+Delta world reconstruction.
- WORLD/GUI-separated capture, JPEG/WebP/RAW and advanced stitching modes remain later work.
- Artifact size/retention policy and crash recovery remain incomplete.
- Debug coverage is representative, not all-domain Deep Debug.
- Persistent Storage remains unavailable.
- Dedicated Server Peer was subsequently delivered in Phase 6.

## Exit Decision

Phase 5 exit conditions are satisfied for all five Runtime Targets:

- Recording backpressure is bounded and non-blocking;
- Contact Sheet and versioned Artifact Bundle are consumable;
- canonical representation is not locked to NDJSON;
- Fixture/Debug cannot masquerade as PLAYTEST evidence;
- Debug Arm and typed mutations are executable and fail closed.

The next execution-plan phase is Phase 6: Dedicated Server Peer.
