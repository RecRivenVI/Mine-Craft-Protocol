# Phase 9 Implementation Plan

> Status: Phase 9A PASS WITH IDENTIFIED IMPLEMENTATION GAPS  
> Date: 2026-08-28  
> Attested V1 product commit: `2dda8448d00852d42fb3e07525ee05daaaddd66f`  
> Current phase boundary: Phase 9A only; Phase 9B/9C and Phase 10 are not started  
> Contract status: experimental diagnostics; Wire Protocol v1 is not frozen

## 1. Purpose

This document is the Phase 9 capability inventory, Spike evidence index and execution decomposition. It does not claim that the full Phase 9 product surface exists. Phase 9A deliberately used only:

```text
1.20.1-forge
26.2-neoforge
26.2-fabric
```

NeoForge 1.21.1 and NeoForge 26.1.2 retain the attested V1 Runtime and are intentionally not Phase 9A implementation Targets.

Allowed inventory states are:

```text
IMPLEMENTED
PARTIAL
MISSING
TARGET_SPECIFIC
REQUIRES_SPIKE
DEFERRED_TO_PHASE10
```

Target matrix outcomes use only:

```text
PASS
PARTIAL
UNAVAILABLE
REQUIRES_NEW_HOOK
```

## 2. Entry Reconciliation

Phase 8 is complete. Final Release Evidence binds V1 to `2dda8448d00852d42fb3e07525ee05daaaddd66f`. The post-attestation remote delta contained only:

```text
Artifacts/phase8/remote-parity-2dda8448....json
Artifacts/phase8/final-attestation-2dda8448....json
```

No Runtime, Mixin, OpenAPI, Companion production source, artifact-affecting build logic or protocol implementation changed after the attested product commit. Phase 9A therefore starts from the accepted V1 behavior rather than reopening Phase 8.

## 3. Existing Capability Inventory

| Capability | Existing status | Current mechanism | Target coverage before 9A | Phase 9 action |
|---|---|---|---|---|
| Basic Player LIVE read | IMPLEMENTED | Client/Server owner-thread projection | five Targets | expand fields and resource revisions |
| Basic Entity LIVE read | IMPLEMENTED | bounded radius query | five Targets | add stable typed Living/equipment/relationship projections |
| Basic Block LIVE read | IMPLEMENTED | loaded Client/Server BlockState | five Targets | add properties and Block Entity domain |
| State Frame | PARTIAL | coordinated best-effort Provider reads | five Targets | become one input to bounded Keyframes |
| Provider Read SPI | PARTIAL | detached JSON, LIVE-only | five Targets | add schemas, snapshot semantics and typed Delta capability in 9B |
| Recording | PARTIAL | frame + selected State Frame + events | five Targets | integrate world Keyframe/Delta later; current codec remains unfrozen |
| Debug Arm | IMPLEMENTED | Control Lease + world fingerprint + TTL | five Targets | retain as the authority boundary |
| Debug Player health | IMPLEMENTED | server-thread direct mutation | five Targets | expand by typed domains in 9C |
| Debug loaded block | IMPLEMENTED | loaded state + value precondition | five Targets | retain no-load policy |
| Deep Player | PARTIAL | Phase 9A bounded server snapshot | three representative Targets | formal schema and client/server comparison in 9B |
| Deep Entity | PARTIAL | Phase 9A bounded server snapshot | three representative Targets | native tracked/component/attachment hooks in 9B |
| Block Entity | PARTIAL | loaded map + serialization projection | three representative Targets | provider-safe schema and effect policy in 9B |
| Chunk internals | PARTIAL | `getChunkNow`, sections and Block Entity summary | three representative Targets | holder/ticket/entity manager hooks in 9B |
| Ticket details | MISSING | source investigation only | three representative Targets | Target-specific hook required |
| Scheduled Tick details | PARTIAL | public total count only | three representative Targets | per-chunk detail hook required |
| Persistent read | PARTIAL | typed world/player/chunk Phase 9A read | three representative Targets | lifecycle/fingerprint policy and more domains in 9D |
| Persistent write | MISSING | deliberately absent | none | investigate and implement only in 9D after safety review |
| Experimental Keyframe | IMPLEMENTED | bounded server-thread immutable snapshot | three representative Targets | formal track selection and budgets in 9E |
| Experimental Delta | PARTIAL | detached snapshot diff | three representative Targets | native/event Hook coverage in 9E |
| Reconstruction | PARTIAL | detached JSON operation application | three representative Targets | durable store/index/diff in 9E/9F |
| Rolling Recorder / Golden Visual Diff / Replay / Tick Step | DEFERRED_TO_PHASE10 | none | none | do not start in Phase 9 |

## 4. Phase 9A Runtime Surface

The three representative Targets expose an explicitly experimental diagnostics surface:

```text
GET  /v0/diagnostics/phase9a/inventory
POST /v0/diagnostics/phase9a/observe
POST /v0/diagnostics/phase9a/storage/read
POST /v0/diagnostics/phase9a/keyframe
POST /v0/diagnostics/phase9a/delta
POST /v0/diagnostics/phase9a/reconstruct

POST /v0/debug/player/attribute
POST /v0/debug/entity/state
POST /v0/debug/phase9a/scenario
```

These routes are not an OpenAPI or Wire v1 freeze. They exist to obtain calling-chain evidence. Phase 9B must decide which semantics become formal public protocol and which remain Target diagnostics.

All Minecraft reads run on the Server owner thread and detach JSON before returning. Typed persisted reads capture only immutable path/identity metadata on the Server thread, then perform IO on a dedicated storage worker. HTTP/WS workers never hold live Minecraft objects.

## 5. Deep Observation Findings

### 5.1 Player

The bounded server snapshot currently proves:

```text
identity/name/UUID                         SERVER_AUTHORITATIVE
current/previous position and velocity    SERVER_AUTHORITATIVE
yaw/pitch/pose/environment flags           SERVER_AUTHORITATIVE
health/absorption/food/air/XP              SERVER_AUTHORITATIVE
gamemode/abilities                         SERVER_AUTHORITATIVE
selected slot/inventory/carried/equipment  SERVER_AUTHORITATIVE
syncable attributes/effects                SERVER_AUTHORITATIVE, projected
vehicle/passengers                         SERVER_AUTHORITATIVE
Menu/container identity                    SERVER_AUTHORITATIVE
dimension/respawn                          SERVER_AUTHORITATIVE
```

The existing `/v0/player` remains the separate `CLIENT_KNOWN` baseline. Phase 9A conformance checks Client/Server UUID agreement; it does not merge the perspectives.

Remaining gaps:

```text
statistics       PARTIAL: manager exists; bounded projection not designed
advancements     PARTIAL: manager exists; bounded projection not designed
recipe state     PARTIAL: recipe book exists; bounded projection not designed
cooldown list    REQUIRES_NEW_HOOK for enumeration
client prediction comparison  REQUIRES_SPIKE
camera/crosshair full semantic projection  REQUIRES_SPIKE
permission details  TARGET_SPECIFIC between 1.20.1 and 26.2
```

### 5.2 Entity

Stable common semantic core:

```text
UUID/runtime id/type
position/velocity/rotation/pose
health for Living entities
equipment summary
vehicle/passenger relationships
non-default tracked-data count
no-gravity representative state
```

Not promoted to common protocol:

```text
raw SynchedEntityData values
arbitrary Data Components
Forge capabilities
NeoForge attachments
Fabric third-party custom state
```

Capability/Attachment reads are intentionally skipped because asking a third-party provider for a capability can initialize/cache provider state. Such access must declare `readEffects=lazy_initialization` unless a provider supplies a guaranteed read-only snapshot contract.

### 5.3 Block and Block Entity

BlockState ID and named properties are read from an already-loaded chunk. `getChunkNow` is the no-load boundary.

Block Entity evidence uses the loaded chunk's Block Entity map. Serializing a Block Entity may execute vanilla or Mod serialization hooks, so Phase 9A marks:

```text
readEffects=serialization_hooks_invoked
```

The result exposes type, position and a hash/size of structured serialized state rather than making raw Target internals the public schema. Forge capabilities, NeoForge attachments and equivalent custom Mod state remain provider-owned.

### 5.4 Chunk, Tickets and Scheduled Ticks

The safe common chunk core is:

```text
chunk coordinate
loaded / NOT_LOADED
section count / non-empty section count
Block Entity count
FullChunkStatus-style summary when available
loadRequested=false
```

Entity association is not treated as a LevelChunk-owned portable list because modern versions separate entity management from the chunk object.

Scheduled Tick public APIs expose total Block/Fluid tick counts. Per-chunk tick identity, due time, priority, position and type need Target-specific hooks.

Ticket models are materially different:

```text
1.20.1:
  DistanceManager
  -> Long2Object map of sorted Ticket sets
  -> level/type/key semantics

26.2:
  DistanceManager
  -> TicketStorage
  -> TicketType plus loading/simulation trackers
```

Phase 9B should expose a normalized semantic loading summary plus optional Target diagnostic detail. A raw unified Ticket DTO would hide real semantic differences.

## 6. Three-Target Matrix

| Capability | Forge 1.20.1 | NeoForge 26.2 | Fabric 26.2 |
|---|---|---|---|
| Deep Player | PARTIAL | PARTIAL | PARTIAL |
| Deep Entity | PARTIAL | PARTIAL | PARTIAL |
| Block Entity | PARTIAL | PARTIAL | PARTIAL |
| Chunk internals | PARTIAL | PARTIAL | PARTIAL |
| Tickets | REQUIRES_NEW_HOOK | REQUIRES_NEW_HOOK | REQUIRES_NEW_HOOK |
| Scheduled Ticks | PARTIAL | PARTIAL | PARTIAL |
| Debug representative | PASS | PASS | PASS |
| Persisted chunk read | PASS | PASS | PASS |
| Persisted player read | PASS | PASS | PASS |
| Experimental Keyframe | PASS | PASS | PASS |
| Delta capture | PASS | PASS | PASS |
| Reconstruction | PASS | PASS | PASS |

## 7. Deep Debug Spike

Every representative Target proved:

| Operation | Mechanism | Before/after | Provenance | Synchronization |
|---|---|---|---|---|
| `debug.player.attribute.set` (`minecraft:max_health`) | Server AttributeMap direct mutation | captured | `authority=runtime_internal`, `evidence=diagnostic` | Server thread applied |
| `debug.entity.state.set` (`no_gravity`) | loaded Entity state | captured | `gameplayEvidence=false` | Server thread applied |
| `debug.world.block` | loaded BlockState mutation with expected value | captured | `DEBUG_PRIVILEGED`, contaminated | normal loaded-world update |

The test constructs a state that ordinary PLAYTEST cannot create, then executes a real `GAME_ROUTED` key action and Composite capture:

```text
Arrange = DEBUG_PRIVILEGED
Act = PLAYTEST
Assert = internal + visible
```

All values and the test block are restored. The experimental scenario helper used by Reconstruction is a closed enum for stone inventory add/remove and pig spawn/remove. It requires Control Lease, Debug Arm and `debug` scope; it is not a general command, reflection or object mutation API.

## 8. Persistent Storage Read

Phase 9A implements READ SPIKE ONLY.

### 8.1 Storage layout facts

```text
1.20.1:
  playerdata/<uuid>.dat
  region/r.<x>.<z>.mca for Overworld
  DIM-1 / DIM1 legacy dimension folders
  NbtIo(File) + RegionFile(Path, directory, sync)

26.2:
  players/data/<uuid>.dat
  dimensions/<namespace>/<dimension>/region/r.<x>.<z>.mca
  Overworld also uses dimensions/minecraft/overworld
  NbtIo(Path, NbtAccounter) + RegionStorageInfo + RegionFile
```

The 26.2 layout difference was discovered by a failed first Spike and is now an explicit Target distinction.

### 8.2 Output contract

Typed storage results contain:

```text
source=persistent_storage
dataSource=PERSISTED
worldFingerprint
dimension
typed chunk/player identity
saveMarker
liveWorldExists
targetLoaded
consistency=last_saved_state
stalePossibility
storageAccessOccurred=true
sideEffects=none_read_only
writeImplemented=false
```

No caller supplies a path. `world.*` remains LIVE-only and never falls back to storage. World/player NBT reads are effect-free file reads. The available Minecraft `RegionFile` API opens a write-capable handle even when the Spike requests no write, so chunk results explicitly report `sideEffects=region_file_api_uses_write_capable_handle; no_write_requested`. Phase 9D must choose between a Minecraft-owned storage barrier and a carefully verified read-only region path rather than hiding this risk.

Fabric produced direct evidence of a normal divergence:

```text
LIVE player exists
PERSISTED player record absent before the first save
```

After explicit Save and re-entry, the persisted player record became available. The Runtime did not synthesize it from LIVE state and did not auto-reconcile.

### 8.3 Future write safety matrix

| State | Proposed Phase 9D policy |
|---|---|
| World stopped | candidate with fingerprint, backup and typed domain |
| Unloaded target, server stable | candidate with fingerprint/preconditions and reload verification |
| Loaded authoritative target | reject persisted write; use typed LIVE mutation instead |
| Integrated Server running | reject unless target is proven unloaded and a storage barrier exists |
| Dedicated Server running | reject without Peer-owned storage lifecycle barrier |
| Save/serialization in progress | reject or wait on an explicit Minecraft-owned barrier |
| World closed but lock unavailable | reject |
| Wrong world fingerprint | reject |
| Missing/expired Debug Arm or `debug.storage` scope | reject |
| Partial/corrupt input | controlled error; no write |

No Persistent Write is implemented in Phase 9A.

## 9. Experimental Keyframe and Delta

The bounded experimental Keyframe schema contains:

```text
schemaVersion=phase9a-keyframe-v0
snapshotId / sequence
dimension
serverTick / capturedAtMillis
consistency=server_thread_bounded
perspective=server_authoritative
selector: radius 0-2 chunks, entity radius 0-64, <=64 selected blocks
player
<=128 entities
chunk identities and section summaries
<=128 selected Block Entity summaries
selected BlockStates
world/scheduled-count summary
Provider test-track descriptor
```

No whole-world dump or chunk force-load occurs.

Delta operations are typed by domain:

```text
player.state_change
entity.spawn / entity.state_change / entity.remove
block.add / block.change / block.remove
block_entity.add / block_entity.change / block_entity.remove
chunk.load / chunk.state_change / chunk.unload
world.metadata_change
snapshot.metadata
```

Every Delta declares:

```text
acquisition=snapshot_diff
perspective=server_authoritative
completeness=bounded_projected
serverTick
sequence
```

It is not represented as a native captured event. The missing native inventory/entity/block-entity/chunk/scheduled-tick hooks are an explicit Phase 9B/9E implementation gap.

## 10. Reconstruction Evidence

The deterministic bounded scenario was:

```text
T0 Keyframe
T1 GAME_ROUTED player movement
T2 typed DEBUG inventory change
T3 typed DEBUG block change
T4 typed DEBUG pig spawn
T5 typed DEBUG entity no-gravity state change
T6 typed DEBUG entity removal
```

All DEBUG Arrange state was cleaned up. The operation sequence was applied to the detached T0 JSON and compared with the final bounded authoritative snapshot.

| Target | Keyframe bytes | Delta count | Delta bytes | Result |
|---|---:|---:|---:|---|
| Forge 1.20.1 | 8,215 | 6 | 18,030 | EXACT |
| NeoForge 26.2 | 16,240 | 6 | 28,877 | EXACT |
| Fabric 26.2 | 16,414 | 6 | 23,284 | EXACT |

This proves the bounded snapshot-diff model is internally reconstructable. It does not prove event-complete long-term World Recording.

## 11. Data Volume Probe

The following figures are short mutation-burst measurements, not a final 20 TPS storage benchmark:

| Target | Measured Delta bytes/s | 1 minute | 20 minutes | 1 hour |
|---|---:|---:|---:|---:|
| Forge 1.20.1 | 11,297 | 677,820 | 13,556,400 | 40,669,200 |
| NeoForge 26.2 | 18,593 | 1,115,580 | 22,311,600 | 66,934,800 |
| Fabric 26.2 | 14,874 | 892,440 | 17,848,800 | 53,546,400 |

Phase 9E must benchmark at least custom framed binary, a CBOR-like candidate and a protobuf-like candidate using representative entity-heavy and block-change-heavy tracks. Required measures are size, encode/decode throughput, allocation, random seek and schema evolution. Phase 9A does not select or freeze a codec.

## 12. Cross-Target Findings

### COMMON SEMANTIC CORE

- Player identity/position/health/inventory/equipment and server authority.
- Entity identity/transform/Living summary/relationships.
- Loaded BlockState and named properties.
- No-load Chunk identity/sections/Block Entity summary.
- Typed Debug Arm, diagnostic evidence and before/after mutation.
- Explicit LIVE/PERSISTED separation.
- Bounded Keyframe and typed snapshot-diff operations.

### TARGET-SPECIFIC

- Legacy NBT/Forge capabilities versus 26.2 Data Components/attachments.
- Public 1.20.1 inventory compartment fields versus 26.2 private list/container projection.
- Legacy `playerdata`/root region paths versus 26.2 `players/data` and dimension-scoped Overworld region.
- DistanceManager Ticket sets versus 26.2 TicketStorage and split trackers.
- Block Entity serialization signatures and registry context.
- Permission and respawn models.

### DANGEROUS TO ABSTRACT

- Raw Ticket DTOs.
- Raw SynchedEntityData values.
- Capabilities/Attachments as if reads were effect-free.
- Persistent paths and lifecycle.
- Chunk ownership of entities.
- Scheduled Tick internal containers.
- Raw NBT/Data Component equivalence.

## 13. Shared-Code Promotion

The 26.2 NeoForge/Fabric experimental engines are currently behaviorally close, and detached Delta/reconstruction logic is a future promotion candidate. No shared Runtime module is created in Phase 9A. Promotion waits until NeoForge 1.21.1 and 26.1.2 have real Phase 9B implementations and the extraction does not hide Target lifecycle or storage differences.

## 14. Phase 9 Execution Decomposition

### Phase 9B — Deep Observation and Provider V2

Deliver formal typed Player/Entity/Block Entity/Chunk snapshots, client/server comparison, Provider schema/snapshot/delta declarations and required Target hooks.

Exit gate: three representative Targets plus NeoForge 1.21.1/26.1.2 expose honest capability coverage; no observation force-loads or hides read effects.

### Phase 9C — Deep Debug and Batch Boundary State

Promote proven debug operations into domain namespaces, add bounded batch execution/cancellation/per-item results and expand Menu/Network/Chunk representatives.

Exit gate: every mutation has Arm/scope/precondition/before-after/provenance/resync/cleanup evidence and never counts as gameplay.

### Phase 9D — Persistent Storage

Formalize typed read domains, storage lifecycle barriers, corruption handling and only then implement explicitly authorized write candidates.

Exit gate: LIVE/PERSISTED cannot be confused; loaded-write races, wrong fingerprints, missing scopes/Arms and save-in-progress states fail closed.

### Phase 9E — Recording V2 and Canonical Store

Add native/event Delta instrumentation, periodic Keyframes, selectable tracks, codec benchmark/selection, framing, compression and indexes.

Exit gate: long recordings remain bounded/streamable; codec choice is benchmarked and versioned; gaps are explicit.

### Phase 9F — Structured State Diff and Reconstruction

Implement Player/Inventory/Entity/Block/Block Entity/Chunk/World/Provider Diff with projections, tolerances and ordering semantics.

Exit gate: T0 + typed Delta sequence reconstructs final authoritative bounded state, with only declared partial fields.

### Phase 9G — Five-Target Alignment, Stress and Release Gate

Promote 1.21.1/26.1.2, run five-Target capability matrix, 20-minute mixed stress and complete Phase 8 regression.

Exit gate: Phase 9 DoD is evidence-backed and Phase 10 becomes ready for a separate independent review.

## 15. Phase 9A Conformance and Exit

Phase 9A conformance lives in `conformance/phase9/` and is separate from Phase 8:

```text
Invoke-Phase9AStaticGate.ps1
Invoke-Phase9ADeepObservationConformance.ps1
Invoke-Phase9ADebugSpikeConformance.ps1
Invoke-Phase9AStorageReadConformance.ps1
Invoke-Phase9AReconstructionConformance.ps1
Invoke-Phase9AGate.ps1
```

Verified results:

```text
three representative Target live Spike gates: PASS
bounded Reconstruction: EXACT on all three
Persistent Write: NOT IMPLEMENTED
Phase 8 Local Gate: PASS
five-Target build: PASS
representative Forge 1.20.1 V1 live smoke: PASS
Wire Protocol v1: NOT FROZEN
```

Final Phase 9A decision:

```text
Phase 9A: PASS WITH IDENTIFIED IMPLEMENTATION GAPS
Phase 9B Entry Gate: READY FOR INDEPENDENT REVIEW
Phase 10: NOT STARTED
```

Phase 9A stops here. It does not automatically enter Phase 9B.
