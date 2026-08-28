# Phase 9 Implementation Plan

> Status: Phase 9C PASS — TYPED DEEP DEBUG + BOUNDED BATCH HARDENED
> Date: 2026-08-28  
> Attested V1 product commit: `2dda8448d00852d42fb3e07525ee05daaaddd66f`  
> Current phase boundary: Phase 9C is complete; Phase 9D awaits independent review; Phase 10 is not started
> Contract status: formal Deep Observation V0 plus retained experimental diagnostics; Wire Protocol v1 is not frozen

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

### 1.1 Core and Optional Extension Boundary

The committed product is the **Agent-native Minecraft Autonomous Testing Platform** described by `PLATFORM_VISION.md`. The Minecraft Agent Control Runtime remains its Runtime Control, Testing and Observation subsystem.

This governance separation does not change Phase 9 scope, implementation order, evidence or exit gates. E1 Development Intelligence & Exploratory Debug, E2 Autonomous Gameplay and E3 Deterministic Graphics Acceptance & Render Forensics are independently governed Optional Extensions in `PLATFORM_EXTENSION_GOALS.md`. They are outside Phase 9 and have not started implementation.

Phase 9C has passed its independent implementation gate with strongly typed Minecraft-domain mutations. An optional exploratory path cannot compensate for a missing or incorrect typed Debug contract. Runtime Phase 9D-9G and Phase 10 remain unchanged. No Extension is a Phase 9 or first Developer Preview blocker.

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
| Provider V2 | IMPLEMENTED | typed descriptor, detached schema-backed snapshot, effects/budget/failure isolation | five Targets | consumed by later Recording/Debug phases |
| Recording | PARTIAL | frame + selected State Frame + events | five Targets | integrate world Keyframe/Delta later; current codec remains unfrozen |
| Debug Arm | IMPLEMENTED | Control Lease + world fingerprint + TTL | five Targets | retain as the authority boundary |
| Typed Deep Debug core | IMPLEMENTED | Arm/scope/world/resource/value guarded owner-thread mutation | five Targets | retain as the trusted Debug path |
| Debug loaded block | IMPLEMENTED | loaded state + ResourceVersion/value preconditions | five Targets | retain no-load policy |
| Deep Player | IMPLEMENTED | formal typed server snapshot plus explicit client-known counterpart | five Targets | optional domains remain limitations |
| Deep Entity | IMPLEMENTED | formal typed common core; raw tracked data excluded | five Targets | Provider V2 owns custom extensions |
| Block Entity | IMPLEMENTED | safe default summary; explicit structured serialization opt-in | five Targets | byte-budgeted and effect-labelled |
| Chunk internals | IMPLEMENTED | `getChunkNow`, sections, loading summary and ticks | five Targets | raw diagnostics stay Target-specific |
| Ticket/loading | IMPLEMENTED | normalized summary + read-only Target diagnostic Accessor | five Targets | do not promote raw parity |
| Scheduled Tick details | IMPLEMENTED | read-only LevelTicks Accessor | five Targets | Recording consumption deferred to 9E |
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

### Phase 9B — Deep Observation and Provider V2 — COMPLETE, CONTRACT HARDENED

Deliver formal typed Player/Entity/Block Entity/Chunk snapshots, client/server comparison, Provider schema/snapshot/delta declarations and required Target hooks.

Exit gate result: PASS — CONTRACT HARDENED. All five Targets expose the formal V0 schema, projection-independent resource-local revision, client/server comparison, no-load observation, normalized loading summary, Target diagnostic tickets, Scheduled Tick detail, enforced Provider V2 contracts and executable budgets.

### Phase 9C — Deep Debug and Batch Boundary State — COMPLETE

Promoted proven debug operations into domain namespaces, added bounded batch execution/cancellation/per-item results and established honest Menu/Provider/Chunk/Client/Network capability boundaries.

Exit gate result: PASS. Every formal mutation has authenticated principal scopes, world/session-bound Arm, ResourceVersion/value preconditions, before/after snapshots, revision advance, provenance, resync/cleanup evidence and never counts as gameplay. Chunk/Client/Network remain explicit `PARTIAL` without raw backdoors. Dedicated Peer retains its separately gated legacy typed health/block subset; full Phase 9C resource-version parity is a later Target limitation, not fabricated.

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

The Optional Extension Portfolio does not append E1/E2/E3 work to Phase 9 or renumber it as Phase 11+. Separately authorized extension research may proceed independently, but it cannot waive, expand or block a Runtime gate.

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
Phase 9B Entry Gate: CLOSED (completed)
Phase 10: NOT STARTED
```

Phase 9A stopped at its independent review boundary. Phase 9B and Phase 9C were subsequently authorized and completed. Work now stops at the Phase 9D independent review boundary.

## 16. Phase 9B Formal Deep Observation Evidence

Formal native routes:

```text
GET  /v0/observe/deep/capabilities
POST /v0/observe/deep
```

OpenAPI V0 version is `0.0.1-phase9b2`; Wire Protocol v1 remains unfrozen. The MCP Companion exposes one typed aggregation Tool, `minecraft_deep_observe`, without duplicating every domain endpoint.

Every formal response carries `ObservationMetadata`, session epoch, snapshot ID, client/server ticks, alignment quality, limitations and resource-local `ResourceRevisionRef` values. Runtime-derived revisions use canonical semantic state captured before response projection. Provider results retain their declared provider revision source. Block Entity lifecycle/type state and opt-in serialized state have separate revisions. No global world revision exists.

Five-Target formal coverage:

| Domain | 1.20.1 Forge | 1.21.1 NF | 26.1.2 NF | 26.2 NF | 26.2 Fabric |
|---|---|---|---|---|---|
| Player | PASS | PASS | PASS | PASS | PASS |
| Client/server compare | PASS | PASS | PASS | PASS | PASS |
| Entity common core | PASS | PASS | PASS | PASS | PASS |
| Block | PASS | PASS | PASS | PASS | PASS |
| Block Entity safe | PASS | PASS | PASS | PASS | PASS |
| Block Entity serialized opt-in | PASS | PASS | PASS | PASS | PASS |
| Chunk | PASS | PASS | PASS | PASS | PASS |
| Loading summary | PASS | PASS | PASS | PASS | PASS |
| Ticket detail | TARGET_DIAGNOSTIC_ONLY | TARGET_DIAGNOSTIC_ONLY | TARGET_DIAGNOSTIC_ONLY | TARGET_DIAGNOSTIC_ONLY | TARGET_DIAGNOSTIC_ONLY |
| Scheduled block/fluid ticks | PASS | PASS | PASS | PASS | PASS |
| World | PARTIAL | PARTIAL | PARTIAL | PARTIAL | PARTIAL |
| Menu | PASS | PASS | PASS | PASS | PASS |
| Provider V2 | PASS | PASS | PASS | PASS | PASS |

Ticket and LevelTicks hooks are read-only Mixin Accessors with no cancellation or control-flow change. Legacy Targets observe DistanceManager Ticket sets; 26.x Targets observe TicketStorage. Generic Agents consume normalized reasons/loading/simulation/holder state. Raw detail is explicitly diagnostic-only.

Block Entity policy:

```text
default:
  type + position + loaded + revision + provider descriptors
  readEffects=none

includeSerializedBlockEntities=true:
  Minecraft serialization path
  NBT converted to structured JSON tree
  readEffects=serialization_hooks_invoked
  16 KiB per Block Entity / 64 KiB aggregate defaults
  explicit truncation when exceeded
```

Provider V2 enforces authenticated required scopes, perspective and owner-thread affinity before invocation. `allowReadEffects` can authorize only declared lazy initialization; load, storage and mutation remain denied in normal observation. Entry blocking is detected/quarantined; timeout/deadline/disconnect/runtime-close retire pending work and ignore late completion. Snapshot and query payloads are validated through an executable schema registry; invalid registration and duplicate IDs fail deterministically.

Representative NeoForge 26.2 projection baseline:

| Profile | Owner-thread capture | Response bytes | Entities | Chunks | Providers |
|---|---:|---:|---:|---:|---:|
| minimal | 655 us | 2,708 | 0 | 0 | 0 |
| typical | 6,280 us | 18,412 | 3 | 9 | 1 |
| maximum | 3,863 us | 122,140 | 41 | 25 | 2 |

Soft owner-thread budget is 4 ms and hard budget is 12 ms. A capture beyond the hard budget is marked partial; response/provider/serialized-state budgets reject or truncate explicitly.

Phase 9B regression evidence:

```text
OpenAPI validation/generation: PASS
five-Target build: PASS
five-Target formal live Gate: PASS
five-Target V1 smoke: PASS
Provider V2 effect/failure/budget isolation: PASS
Ticket/Scheduled Tick runtime hook verification: PASS
unloaded observation -> NOT_LOADED/no load: PASS
Phase 8 Local Gate: PASS
Companion tests: 3 PASS, 24 Tools
dependency audit: 0 high / 0 critical
```

Remaining planned work is unchanged:

```text
9C Deep Debug expansion
9D Persistent Storage lifecycle/write safety
9E native/event Delta, Recording V2 and Canonical Store
9F Structured Diff/reconstruction
9G five-Target long stress/release gate
Phase 10 advanced diagnostics/recovery
```

```text
Phase 9B.1: PASS
Phase 9B.2: PASS
Phase 9B: PASS — CONTRACT + REVISION IDENTITY HARDENED
Phase 9C: PASS
Phase 9D Entry Gate: READY FOR INDEPENDENT REVIEW
Phase 10: NOT STARTED
```

## 17. Phase 9B.1 Contract Hardening Evidence

### 17.1 Resource revision model

| Resource | Canonical semantic state | Revision source | Request-shape independence |
|---|---|---|---|
| Player | full owner-thread Player snapshot without request/tick metadata | snapshot change sequence | projection independent |
| Menu | menu ID, slots and carried stack | snapshot change sequence | projection independent |
| Entity | identity plus full captured common semantic state | snapshot change sequence | projection/order independent |
| Block | loaded state, ID and properties | snapshot change sequence | selector metadata excluded |
| Chunk | loaded/status/sections/Block Entity count/loading/ticks | snapshot change sequence | radius/projection excluded |
| Block Entity | key/type/loaded lifecycle state | snapshot change sequence | serialization opt-in excluded |
| Block Entity serialized | structured opt-in serialized state | snapshot change sequence | separate from base revision |
| Provider | native provider revision or deterministic schema payload | provider revision / snapshot change sequence | query/projection independent |

The tracker is a 4,096-entry access-ordered LRU fingerprint cache. Every new or changed runtime-derived state receives a session-monotonic generation; eviction never resets a resource to revision 1 or aliases an older generation. sessionEpoch distinguishes Runtime lifetimes.

### 17.2 Provider policy matrix

| Descriptor | Normal observe | allowReadEffects=true | Additional authority |
|---|---|---|---|
| snapshot safe / no effects | allow | allow | authenticated required scopes |
| may initialize | skip | allow with lazy_initialization evidence | declared scopes |
| may load data | skip | still skip | future dedicated policy only |
| may access storage | skip | still skip | Phase 9D policy only |
| may mutate | deny | deny | Phase 9C typed Debug only |
| unsupported perspective | skip without invocation | skip | none |
| missing required scope | deny without invocation | deny | authenticated principal must hold scope |
| unsupported affinity | explicit unavailable | explicit unavailable | Target capability |

Provider synchronous entry budget is 10 ms. Violations are recorded and quarantined; this is cooperative in-process containment, not a hostile-code sandbox. Async timeout attempts underlying cancellation, retires the invocation generation, releases accounting and prevents late result/revision publication. Request deadline and disconnect propagate to the same cancellation tree.

### 17.3 Five-Target live matrix

| Target | Revision | Scope | Perspective | Affinity | Policy | Timeout | Schema | Formal 9B | V1 | Shutdown |
|---|---|---|---|---|---|---|---|---|---|---|
| Forge 1.20.1 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| NeoForge 1.21.1 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| NeoForge 26.1.2 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| NeoForge 26.2 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| Fabric 26.2 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |

Representative NeoForge 26.2 performance after moving revision work off the owner thread:

| Profile | Owner capture | Revision | Provider entry | Provider validation | Response bytes |
|---|---:|---:|---:|---:|---:|
| minimal | 134 us | 274 us detached | N/A | N/A | 2,761 |
| typical | 2,449 us | 590 us detached | 7 us | 67 us | 20,588 |
| maximum | 4,726 us | 2,771 us detached | bounded per provider | 44 us max | 121,995 |

The final five-Target matrix observed a 5,772 us worst owner-thread capture, below the 12 ms hard budget. Revision canonicalization, hashing and response projection run on `minecraft-protocol-observation-revisions`; their cost is reported separately and does not block the Client/Server owner thread. Provider entry, validation and total duration are also emitted separately.

MCP cancellation is protocol-era aware. Runtime HTTP deadline, disconnect and typed `DELETE /v0/requests/{requestId}` cancellation are implemented and live-tested. Modern MCP contexts that deliver an AbortSignal invoke the typed cancel route. The currently negotiated `2025-11-25` stdio transport does not deliver cancellation notifications to the Companion handler; it therefore uses the explicit operation cancel Tool for operations and always sends a Runtime deadline for Deep Observation rather than claiming unsupported early cancellation.

## 18. Phase 9B.2 Revision Identity / Canonicalization Evidence

### 18.1 Canonical collection semantics

| Domain | Collection semantics | Normalization | Revision behavior |
|---|---|---|---|
| Inventory | ordered by slot index | capture order preserved | slot/content swap changes revision |
| Menu slots | ordered by slot index | capture order preserved | slot/content swap changes revision |
| Attributes | unordered set | sorted by registry ID | source iteration reorder is stable |
| Effects | unordered set | sorted by effect ID | source iteration reorder is stable |
| Passengers | ordered by Minecraft relationship order | preserved | reorder changes revision |
| Entity query results | unordered resource collection | sorted by UUID | source iteration reorder is stable |
| NBT List | ordered | preserved | reorder changes serialized BE revision |
| Provider JSON array | ordered by default | Provider contract may normalize explicitly | reorder changes fallback revision state |

Generic canonicalization sorts JSON object keys and preserves every JSON array in original order. It no longer guesses set semantics.

### 18.2 Provider revision contract

Provider descriptors now declare revisionSource, revisionScope, revisionSchema and revisionQueryInvariant. Resource-scoped fallback providers supply a bounded revisionState independent from query-shaped data. Query-view fallback revisions include a query fingerprint in identity and are not mutation-precondition eligible. Native provider revisions are resource scoped, non-decreasing and checked against canonical revisionState; regression or same-revision/different-state is rejected and quarantines the provider.

### 18.3 Resource version token

Every ResourceRevisionRef now carries sessionEpoch, resourceType, resourceKey, lifecycleId, revision, revisionSource, revisionScope and mutationPreconditionEligible. ResourceVersionVerifier provides stable STALE_SESSION_EPOCH, RESOURCE_MISMATCH, STALE_RESOURCE_REVISION and RESOURCE_VERSION_NOT_PRECONDITION_ELIGIBLE outcomes without implementing any Debug mutation.

Session-local lifecycle generations are assigned to Player, Menu, Entity, Chunk and Block Entity objects. Menu ID reuse, entity recreation, Block Entity replacement and chunk unload/reload therefore invalidate old tokens even when the semantic key is reused. The lifecycle map and revision tracker are both bounded at 4,096 entries; reset/eviction allocates new monotonic generations rather than aliasing history.

### 18.4 Executor bounds

| Executor | Threads | Queue | Overload |
|---|---:|---:|---|
| Detached revision | 1 | 8 | request fails with REVISION_BACKPRESSURE / HTTP 429 |
| Detached Provider entry | 2 | 16 | provider result fails with provider_worker_backpressure |

Active Deep Observation requests are bounded at 16. Provider pending work is therefore bounded by 16 requests x 8 providers, while entry and revision queues remain independently bounded. Forced unit overload and 16-request live concurrency both pass; the representative run completed 15 requests and rejected one with controlled 429, with queues returning to zero and session latency at 7 ms.

### 18.5 Five-Target final matrix

| Target | Canonical | Provider revision | Epoch | Lifecycle | Executors | Formal 9B | V1 |
|---|---|---|---|---|---|---|---|
| Forge 1.20.1 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| NeoForge 1.21.1 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| NeoForge 26.1.2 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| NeoForge 26.2 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| Fabric 26.2 | PASS | PASS | PASS | PASS | PASS | PASS | PASS |

Across the final Phase 9B.1/9B.2 matrices, the worst observed owner-thread capture was 5,839 us, detached revision was 4,656 us and Provider validation was 141 us. Owner-thread work remains below the 12 ms hard budget.

## 19. Phase 9C Typed Deep Debug Evidence

Formal V0 routes are:

```text
GET  /v0/debug/capabilities
POST /v0/debug/mutations
POST /v0/debug/batches
GET  /v0/debug/evidence
POST /v0/debug/evidence/act/start
POST /v0/debug/evidence/act/finish
```

The typed mutation union covers representative Player health/attribute, Entity no-gravity, loaded Block, container Block Entity custom name, Menu slot and registered Provider mutations. Every item requires `debug`, `debug.write`, a domain scope, a session/world/namespace-bound Debug Arm and an eligible resource-scoped version token. Value preconditions remain independent and composable. Validation and mutation occur under the authoritative owner-thread permit.

Provider Debug is a separate `mutate(DebugContext)` contract with mutation/result schemas, required scope, Arm, affinity, native resource revision integrity, cancellation and audit. It is never hidden in an observation query. Delayed Provider mutation cancellation was live-tested with zero post-cancel state change.

Batch V0 is an ordered, non-transactional, bounded sequence:

```text
max items:          64
max request bytes:  256 KiB
max writes/tick:    4
max duration:       30 seconds
failure policy:     STOP_ON_FAILURE | CONTINUE_ON_FAILURE
```

It reuses native Operation get/wait/cancel, returns per-item results and partial cancellation snapshots, rechecks Arm/world authority for every item and uses a cross-thread stamped cancellation permit. Conformance covers 10-item success, mixed/stale failure, cancellation with zero later mutations, delayed Provider cancellation, Arm expiry, world exit, Runtime shutdown cleanup and both failure policies.

Representative NeoForge 26.2 batch measurements:

| Scenario | Result | Measured evidence |
|---|---|---|
| single typed item | PASS | included in per-item timings; no unbounded owner-thread loop |
| 10 items | PASS | 180 ms total in final five-Target matrix |
| maximum 64 items | PASS | 2,631 ms total; 2,620 ms Runtime duration; 29,007 us average / 35,224 us maximum item; 127,886 response bytes; -69,632 process working-set delta; cleanup PASS |
| cancelled batch | PASS | three items completed before accepted cancellation; zero post-cancel mutation |

The development Integrated Server reported a normal idle `world.gameTime` advance near 20/s but accelerated while the mutation workload was scheduled. That counter is therefore not treated as an authoritative TPS benchmark for this scenario. The reliable budget evidence is bounded per-item execution, explicit yielding, responsive Runtime requests, no rejected/blocked owner-thread loop, no persistent working-set growth and complete cleanup. Formal long-duration FPS/TPS stress remains Phase 9G.

Five-Target live results:

| Target | Player | Entity | Block | Block Entity | Menu | Provider | Batch | Evidence | Phase 9B | V1 |
|---|---|---|---|---|---|---|---|---|---|---|
| Forge 1.20.1 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| NeoForge 1.21.1 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| NeoForge 26.1.2 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| NeoForge 26.2 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |
| Fabric 26.2 | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS | PASS |

Chunk, Client and Network are deliberately `PARTIAL`: their capability and safety boundary is formal, but Phase 9C does not expose raw Ticket mutation, arbitrary client-field writes, arbitrary packet classes or ByteBuf injection. Persistent Storage write remains unimplemented for Phase 9D.

```text
Phase 9C: PASS
Phase 9D Entry Gate: READY FOR INDEPENDENT REVIEW
Phase 10: NOT STARTED
Development Intelligence: NOT STARTED
Wire Protocol v1: NOT FROZEN
```
