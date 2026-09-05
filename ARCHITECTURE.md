# Mine-Craft-Protocol Architecture Baseline

> Status: Platform charter adopted; Phase 8/V1 attested; Phase 9A/9B/9C, Phase 9D-0/9D-1/9D-2/9D-2.1 and packaged-artifact runtime attestation complete; Persistent Write Entry Review READY for independent review
> Authority: `PLATFORM_VISION.md` defines the committed Core, `PLATFORM_EXTENSION_GOALS.md` defines optional extensions, and this file records the current implemented Runtime and Companion architecture.

## Product Boundary

Mine-Craft-Protocol's committed product is the **Agent-native Minecraft Autonomous Testing Platform**. **Agent-native Minecraft Development, Debugging and Testing Platform** remains an umbrella positioning. The current implemented core is the **Minecraft Agent Control Runtime**, the Core Runtime Control, Testing and Observation subsystem.

The native HTTP/WebSocket Runtime contract remains independent from MCP. The implemented TypeScript MCP Companion is a separate adapter process and is not part of the in-game Runtime core. E1/E2/E3 are optional external portfolios; none is implemented or required by this architecture baseline.

## Optional Extension Boundary — INACTIVE / NON-BLOCKING

```text
Coding Agent / Human Inspector
             |
             v
Unified Agent Surface (MCP / Native APIs)
             |
      Platform Companion
       |             |
       |             +-- Development Intelligence Service
       |                   +-- Artifact / Mapping
       |                   +-- Managed Source Store
       |                   +-- Symbol / AST / Reference / Call Graph
       |                   +-- Mod / Mixin / AW / AT Intelligence
       |
       +-- Runtime Adapter -- HTTP/WS --> Minecraft Agent Control Runtime
```

Status and ownership are explicit:

| Plane | Status | Process boundary |
|---|---|---|
| Runtime Control and Testing | CURRENT | Minecraft Runtime Mod + optional Server Peer |
| Deep Runtime Observation | CURRENT | Minecraft Runtime Mod + Server authority |
| Typed Deep Debug | CURRENT | Minecraft Runtime Mod / Integrated Server; Phase 9C core complete, Dedicated Peer parity limited |
| Evidence / Artifact / Timeline | CURRENT | Runtime plus Companion consumers |
| E1 Source / Mapping / Mod Intelligence | OPTIONAL / NOT STARTED | possible external Development Intelligence Service |
| E1 Runtime <-> Source / Safe Probe / Exploratory / Inspector | OPTIONAL / NOT STARTED | separate authority and threat boundary |
| E2 Autonomous Gameplay | OPTIONAL / NOT STARTED | Agent skill/navigation layer above Core |
| E3 Render Forensics | OPTIONAL / NOT STARTED | optional typed render/GPU services and Providers |

If E1 is activated, its Development Intelligence Service may initially share a process with the Platform Companion, but the Runtime Mod never owns heavyweight decompilation, source corpora, SQLite/full-text indexes, AST parsing, call-graph construction or third-party Mod decompilation.

Normal Runtime and typed Debug remain free of arbitrary shell, process, filesystem and Reflection RPC. A future unsafe Exploratory JVM surface is a separate, default-off, loopback-only, explicitly armed and explicitly **not sandboxed** plane; its output is always invalid for gameplay acceptance.

If E1 is activated, Runtime/Source/Mapping correlation should use Target-aware symbol identity without merging Runtime truth with source/decompiler inference.

No optional extension creates a Core module, dependency, Release Gate or attack surface merely by being documented. First Developer Preview and Core 1.0 remain Core-only decisions.

## Explicit Targets

Current real targets are:

```text
versions/
├─ 1.20.1-forge/
├─ 1.21.1-neoforge/
├─ 26.1.2-neoforge/
├─ 26.2-neoforge/
└─ 26.2-fabric/
```

Targets are siblings. They do not depend on one another. `26.2-neoforge` remains the modern semantic reference and `26.2-fabric` its same-version Loader comparison, but all five Targets now own complete Runtime implementations. Cross-Target repetition remains intentional until a later evidence-backed extraction decision.

Current implementation status:

| Target | Build | Client runtime | Dedicated server | V0 control runtime |
|---|---|---|---|---|
| 1.20.1 Forge | verified | verified | Integrated + physical Peer verified | Phase 9B Formal Observation verified |
| 1.21.1 NeoForge | verified | verified | Integrated + physical Peer verified | Phase 9B Formal Observation verified |
| 26.1.2 NeoForge | verified | OpenGL verified | Integrated + physical Peer verified | Phase 9B Formal Observation verified |
| 26.2 NeoForge | verified | OpenGL/Vulkan verified | Integrated + physical Peer verified | Phase 9B Formal Observation verified |
| 26.2 Fabric | verified | OpenGL/Vulkan verified | Integrated + physical Peer verified | Phase 9B Formal Observation verified |

## Current Runtime Layers

Each Target currently owns concrete implementations of:

```text
target bootstrap
target Mixin/access hooks
client-thread observation
GAME_ROUTED input adapter
Interaction Tree projection
live player/world query
composite capture
loopback HTTP/WebSocket probe transport
minimal trace and readiness
layered request metadata and typed errors
scope enforcement and single-writer Control Lease
resource-level Screen/Menu preconditions
deadlines, input idempotency and cancellable operations
bounded audit and thread-affinity diagnostics
semantic selector and bounds-center coordinate generation
tree-targeted and vision-coordinate UI actions
cancellable multi-step input Pipeline with finally cleanup
single-source ConditionEngine for standalone and Pipeline wait/assert conditions
bounded EventHub filter/ring/resume/gap/resync
per-principal/per-connection SecurityGate request budgets
bounded structured Render Facts on 26.2
explicit client-known and Integrated Server-authoritative LIVE queries
coordinated best-effort State Frames
typed LIVE-only Provider Read SPI
runtime graphics-backend and Capture concurrency evidence
bounded multi-track Recording Session
versioned Artifact Bundle and experimental binary canonical store
streaming Runtime/Companion Artifact transport and aggregate Recording budgets
scope-gated Fixture and world-bound Debug Arm
typed player-health and loaded-block Debug mutations
formal typed Player/Entity/Block/Block Entity/Menu/Provider Debug mutations
session/world/lifecycle/revision/value mutation preconditions
bounded non-transactional Debug batch with per-item results and cancellation permit
Debug contamination windows and EventHub operation/resource correlation
evidence contamination propagation
optional peer-v0 custom-payload negotiation
Dedicated Server-authoritative LIVE query routing
server-side operator/feature-flag gates for Peer Fixture/Debug
Peer timeout, disconnect and pending-request cleanup
```

The proven, Minecraft-independent `runtime-safety` helpers are shared and embedded in each Target. Minecraft/Loader adapters remain Target-local; this is not an inheritance chain or a shared Loader framework.

## Shared Contract Layer

The shared layer is restricted to proven facts:

```text
protocol-schema/
  OpenAPI V0 source
  generated Java models
  generated TypeScript models

conformance/
  target-independent black-box scenarios

runtime-safety/
  proven identity/lifecycle safety and control/capture coordination helpers
```

Generated protocol models remain build output. The existing `runtime-safety` promotion is backed by five-Target runtime/packaging evidence; further sharing still requires evidence.

## Phase 8 MCP Companion

`companion/` connects to one authenticated Runtime over loopback HTTP and serves MCP over stdio through the official v2 SDK. It exposes a curated static Tool surface and uses Resources for large PNG/Artifact payloads. Runtime text is wrapped as data-plane-only content and never participates in Tool/Prompt construction, scope decisions or Debug authorization.

The Companion preserves the native protocol as the authority: Lease, Debug Arm, scopes, Peer operator gates, resource revisions, typed errors and provenance are forwarded rather than recreated. Its build checks the exact OpenAPI generation and its official Client conformance covers mock and live Minecraft workflows.

## Phase 2 Request and Operation Model

The HTTP mapping keeps the common request layer deliberately small:

```text
Authorization: Bearer <runtime token>
X-MCP-Request-Id: optional caller correlation ID
X-MCP-Protocol-Version: optional, defaults to v0
X-MCP-Deadline-Ms: optional relative deadline
```

Mutation-only context is declared per operation rather than imposed on reads:

```text
X-MCP-Control-Lease
X-MCP-Idempotency-Key
X-MCP-Expected-Screen-Revision
X-MCP-Expected-Menu-Revision
```

`GET /v0/operations` is the runtime declaration surface for scope, Lease, idempotency, cancellation, supported preconditions and thread affinity. Long waits can be created as operation handles, inspected and cancelled. V0 remains unstable and does not freeze Wire Protocol V1.

MCP exposes native Operation get/wait/cancel without creating a second lifecycle state machine. Cancelling a waiting MCP Pipeline request issues native `DELETE /v0/operations/{operationId}`.

There is still no global `expectedWorldRevision`. Current mutations accept only the Screen/Menu resources they actually depend on. Later world operations must add resource or value preconditions appropriate to the target block, chunk, entity, container or provider.

## Thread Ownership

```text
HTTP/WS worker
  → schedules immutable request data
  → Minecraft client thread
  → snapshots DTO/JSON
  → returns immutable result

render capture request
  → client/render thread
  → GPU screenshot callback
  → IO worker encodes PNG

container input
  → client input handler
  → Screen/Menu
  → normal packet
  → Netty server receive
  → PacketUtils server-thread scheduling
  → Server thread validation
```

Active Minecraft objects must not escape their owning thread.

`GET /v0/diagnostics/thread` produces detached evidence on the requested Client, Render or Integrated Server owner thread. Server affinity is unavailable when no integrated server exists; it is never simulated on the transport worker. Composite capture remains the concrete Render scheduling path, while normal container packets remain the concrete Server validation path.

## Control Lease and Input Cleanup

The five runtimes implement one input writer and arbitrarily many readers. A Lease has a bounded TTL of 1–60 seconds and supports acquire, renew, release and emergency release.

Every input mutation requires the active Lease ID. Runtime-owned pressed keys and mouse buttons are released through the same Minecraft input handlers when:

- the Lease is explicitly released;
- the Lease TTL expires;
- a WebSocket control channel associated with the Lease disconnects;
- the transport closes;
- emergency release is requested.

`GET /v0/input/state` exposes only the Runtime-owned virtual input state for diagnostics and automated cleanup verification. It is not an OS-wide key logger.

### Current Operator Presence and Human Override

The authenticated Runtime control session has explicit `READ`, `OPERATE` and `TAKEOVER` intentions, not permission levels. Its independent manual latch derives the compatibility presentation states `IDLE`, `AGENT_CONTROLLED` and `MANUALLY_REVOKED`. Native Esc consumes its first event, revokes the Lease, cancels input work and releases held input. Agent-routed Esc is an ordinary GUI key. Esc returns TAKEOVER to READ. The manual latch and structured `reconsentRequired=true` persist across denied player-control requests and explicit OPERATE until successful acquire/reacquire. Conversation reconsent is an Agent/Companion policy, not a Runtime-verifiable boolean. Read-only operations and independent Debug authorization are not revoked by input handback.

The current policy prevents automatic/background mouse grab; a focused native click in the gameplay viewport may grant capture until focus loss. It is not exclusive takeover: other human game input is not generally suppressed. Original window icon pixels and title are restored on handback; presentation has a short Fade. Fresh ordinary content is captured through a bounded queue before the final Operator pass, so the blue edge and current bottom-right prompt are not evidence pixels. No timed chrome suppression or separate OS overlay is used.

Round 1 implements GET/POST `/v0/control/mode`, session/capability state and MCP typed mode actions. READ permits observation, standalone conditions, capture/recording, events and persisted reads. OPERATE permits separately authorized Fixture/Debug mutation without an input Lease. Player input, GUI, pipelines and player commands require TAKEOVER plus the existing Lease. Mode selection never supplies scopes or Debug Arm. Resource/value preconditions and evidence authority remain independent.

Transitions use `{controlSessionId,generation}`; stale requests fail closed. Leaving TAKEOVER invalidates input generation before cancellation and waits for held-input cleanup. Owner-thread admission rejects stale queued input. Bounded OPERATE work guards prevent overlap with TAKEOVER; completed mutations are not rolled back. Native/MCP classification is indexed in `conformance/control/control-mode-surface.json`.

Mode admission is Runtime-local, not a distributed transaction. Already-sent Dedicated Peer packets retain peer-v0 timeout semantics; a timeout is not proof of no remote mutation. Server permission checks remain independent; authoritative state must be reobserved before retrying an uncertain outcome.

Minecraft.close HEAD drains the Runtime before Loader teardown and window destruction; a guarded JVM shutdown hook remains fallback. This avoids first-use Recording classes being loaded after the Mod class loader has closed. Contact Sheet/finalization failure is typed by stage and cause, with source-file retention distinguished from a verified completed Bundle.

Always-free host cursor, exclusive native-input suppression, virtual pointer and pixel chrome remain **not implemented** (Rounds 2–4). See `AGENT_CONTROL_MODEL_RESEARCH.md`. Historical UX closeout evidence remains `Artifacts/core/core-ux-closeout-20260905.json`; the isolated Forge save-click timeout is retained as a historical observation, not erased by later passes.

## Authentication, Scopes and Audit

The Runtime binds IPv4 loopback only. It uses an explicitly supplied token from `minecraft.protocol.token` / `MCP_RUNTIME_TOKEN`, or generates a 256-bit random token and writes it to `<gameDirectory>/minecraft-protocol/token` without logging the value.

Host and Origin are constrained to exact loopback names. Bearer comparison is constant-time. Effective scopes come from `minecraft.protocol.scopes` / `MCP_RUNTIME_SCOPES`; the V1 default scopes are `read`, `ui`, `input`, `capture`, `event`, `diagnostics`, `control` and `command`.

Authenticated requests pass through `SecurityGate`, which assigns token-lifetime principal and connection identities and applies principal, connection and expensive-category token buckets. Active Operations are bounded to 16.

The current audit store is a bounded in-memory ring intended for probe verification, not the Ultimate persistent audit artifact. It correlates request, principal, connection, Lease, Debug Arm and Operation without recording credentials or request bodies.

## Phase 3 UI Resolution and Action

The Interaction Tree now reports semantic roles, node/resource revisions, coverage, generated interaction points and supported actions. Selectors may match node ID, role, label, class, slot and substring variants. Ambiguous matches fail unless `nth` is explicit.

```text
selector
  → current Interaction Tree re-resolution
  → exact match / explicit nth
  → bounds-center GUI coordinate
  → Screen/Menu resource precondition
  → GAME_ROUTED_RAW mouse path
  → normal Screen / Menu / packet processing
```

Tree targeting and explicit coordinates share the same input path. Vision fallback is therefore:

```text
GET screenshot + GET ui.vision.context
  → multimodal model chooses gui_scaled coordinates
  → POST ui.action source=vision
  → GAME_ROUTED_RAW input
```

The Runtime does not claim that Render Facts reconstruct business semantics. Tree coverage and fallback availability are reported independently.

## Phase 7 Extended Widget Alignment

The five Target fixture covers EditBox, disabled state, duplicate selectors and runtime-added standard Widgets. Selector-based action now validates the resolved node's `active`, `visible` and declared `actions` fields before generating routed input. A semantic node that declares itself non-actionable fails closed; explicit/Vision coordinates remain the fallback for GUI content without reliable semantics.

`GET /v0/diagnostics/hooks` exposes Target-owned mechanism, target, injection point, behavior, runtime status and degraded capability for every V1-critical Hook. The current gate forbids Overwrite and third-party Mixin targets; it explicitly audits seven Operator-control cancellations and two icon/keymapping forwarding redirects per Target. Observation Hooks remain non-cancelling. Runtime self-test remains authoritative over static configuration.

## Input Pipeline

`POST /v0/pipelines` starts a cancellable operation supporting:

- mouse move, press/release, click, scrolling and segmented dragging;
- raw keys, key taps and multi-key chords;
- selector-based UI action and selector-to-selector drag;
- delay, `wait.until` and `assert.that` steps.

Pipelines are bounded to 256 steps and five minutes. Every step revalidates the Control Lease. Failure, cancellation, Lease expiry and normal completion with default settings release all Runtime-owned held input through Minecraft's input handlers. `cleanupOnComplete=false` is explicit and still remains bounded by Lease expiry or release.

Standalone `POST /v0/wait/until`, standalone `POST /v0/assert`, Pipeline `wait.until` and Pipeline `assert.that` all use the same `ConditionEngine`. Screen/UI and Player/Block/Entity/Menu/Inventory/Recording/Event/Operation/Provider conditions therefore have one semantics source and one cancellation-aware polling implementation.

## Standard Mod GUI Fixture

Each probed Target contains a small standard Minecraft Widget-based Screen used only for compatibility conformance. Opening it is a typed `DIRECT` Fixture Arrange action and returns `evidenceContaminated=true`. All subsequent button discovery and activation use Interaction Tree plus GAME_ROUTED input; Fixture setup is never presented as gameplay acceptance.

## Render Facts Baseline

NeoForge 26.1.2/26.2 and Fabric 26.2 retain the latest 256 `GuiRenderState` submission facts for the current Screen revision. Every fact contains sequence, client tick, Screen revision, category, state class and structured bounds. `semanticInference=false` is explicit.

Forge 1.20.1 and NeoForge 1.21.1 report `coverage=unsupported` rather than fabricating a Render Tree.

## Phase 4 Live Observation Boundary

Live observation is split into explicit sources:

```text
/v0/player, /v0/world/*
  source=client_live
  authority=client_observed
  stalePossible=true

/v0/server/player, /v0/server/world/*
  source=integrated_server_live | dedicated_server_peer
  authority=server_authoritative
  stalePossible=false
```

Both return `dataSource=LIVE` and `storageAccessed=false`. Server block queries use only already-loaded state, return `chunk_not_loaded` for an unloaded target and record `chunkLoadRequested=false`. No ordinary query falls back to region files, playerdata or other persisted state.

### Phase 9D-0 Persistent Read Boundary

The five Targets expose the unstable V0 `phase9a/storage/read` route through a target-local `PersistentStorageAdapter`. The external result is uniform (`dataSource=PERSISTED`, `consistency=last_saved_state`, stale risk, storage identity and file revision); path resolution remains Target-local through Minecraft's `LevelResource` and dimension storage APIs. Reads use bounded NBT accounting, a bounded storage executor and read-only region channels. File snapshots and the session-lock identity are checked across the read; lifecycle changes, save-at-capture and shutdown fail closed. `storage.read` is separate from the broad `debug` scope, and no Persistent Write operation exists.

Windows lock/access contention is reported as busy/unavailable, not inferred corruption. After a real Save & Quit and loss of LIVE ownership, a detached, identity-checked last-world context permits read-only `offline_file_snapshot` access guarded by the existing session lock. No Server object, force-load or LIVE fallback is required. The bounded Anvil reader accepts a valid unpadded final sector, still rejects truncated payloads, and explicitly reports unsupported external `.mcc` chunks. Runtime close retires the read worker/context.

### Persistent Write Entry Boundary

Phase 9D-2 supplies the hardened safety foundation without enabling writes. `StorageIdentity` uses stable world-directory lineage and excludes mutable `level.dat`/`session.lock` content and file lineage; mutable content remains in `FileSnapshot`/File Revision. The single-file foundation rechecks after backup and immediately before `ATOMIC_MOVE`, rejects external lock competition and returns explicit commit/recovery states. Windows file/backup force and namespace atomicity are covered; directory durability is explicitly unproven, so this is not a power-loss transaction. Phase 9D-2.1 packages the shared `runtime-safety` module into every Target's development runtime and final artifact (Jar-in-Jar for Forge/NeoForge, Loom `include` for Fabric), isolates its package from target runtime classes, and verifies the five-target running → saving → unload → title lifecycle. The foundation exposes no write route and has not modified a Minecraft save. A new Persistent Write Entry Review is now required before any writer work.

`AtomicWriteRequest` is synthetic-fixture-only: bounded same-directory temp output, durable file force, precondition recheck, backup, required `ATOMIC_MOVE`, explicit commit point, post-verification and recovery statuses. The backup-window and final-precommit mutation tests now reject stale targets, while an external process holding `session.lock` is rejected. `requireDirectoryForce=true` reports `DIRECTORY_DURABILITY_UNVERIFIED` on the current Windows/Java environment. Non-atomic replacement is rejected, and Region/Anvil/`.mcc` is not implemented. A future first implementation may consider only an offline/stopped, typed single-file `level.dat` metadata candidate after the shared runtime artifact is proven in all Targets. Online or loaded targets, save/unload/shutdown races, playerdata, Region/Anvil/`.mcc` and Peer-owned storage writes remain rejected. This boundary does not block the Core Developer Preview.

Authoritative state comes from an active Integrated Server or a negotiated Dedicated Server Peer. Title screen and remote-without-Peer contexts return a typed unavailable error rather than client data relabeled as authoritative.

## State Frame

`POST /v0/state/frames` accepts 1–32 provider reads and returns:

```text
stateFrameId
stateFrameSequence
consistency=coordinated_best_effort
dataSource=LIVE
storageAccessed=false
startedAtMillis / completedAtMillis
per-read querySnapshotId / providerRevision / perspective
```

Client and Server reads are scheduled concurrently onto their owner threads. The result does not claim a global transaction or global world revision.

## Provider Read SPI

Third-party Mods may explicitly register a namespaced `ReadProvider` through `MinecraftProtocolProviders`. The SPI accepts detached JSON queries and returns detached JSON futures; it does not expose object-graph traversal, reflection or live Minecraft objects.

The Phase 4 registry is deliberately LIVE-only. External provider output is labeled `trust=untrusted_mod_provider`, `thirdParty=true`, `source=registered_provider` and `storageAccessed=false`. The bundled echo provider exists solely to exercise registration, discovery and read conformance.

## Capture Backend and Concurrency

`GET /v0/capture/info` reports the actual active backend from the graphics device, rather than inferring it from Target configuration. Verified combinations are:

```text
Forge 1.20.1: OpenGL
NeoForge 1.21.1: OpenGL
NeoForge 26.1.2: OpenGL
NeoForge 26.2: OpenGL + Vulkan
Fabric 26.2: OpenGL + Vulkan
```

Screenshot readback occurs on the render/client path and PNG encoding occurs on the IO pool. Conformance holds W, executes mouse movement and performs eight simultaneous Composite captures, proving capture does not serialize the input Pipeline.

On 2026-08-28 both 26.2 Vulkan Targets reran the complete Phase 4 gate with Integrated Server authority, then the Phase 5 Recording gate. Each recorded 20 Composite frames and 20 State Frames with zero gaps and produced a valid Contact Sheet, canonical binary store and Artifact ZIP.

## Phase 5 Recording Session

`POST /v0/recordings` starts one bounded multi-track session without acquiring the input Lease. The baseline tracks:

```text
Composite PNG frames
selected Provider State Frames
Screen/Menu revisions
Runtime-owned input state
input and Pipeline events
Fixture/Debug contamination events
recording gaps and writer errors
```

Sampling is scheduled away from Minecraft threads. At most two samples may be in GPU/state acquisition concurrently. A single writer uses a fixed 64-entry queue. Queue saturation or excess in-flight work increments `gaps` and drops the sample; it never blocks Client/Render/Server threads to claim zero loss.

## Artifact Bundle

Finalization produces:

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

The readable manifest and indexes are versioned. NDJSON is explicitly a human/debug export. `canonical/store-v0.bin` uses an experimental length-prefixed record store behind a `CanonicalStore` interface; the manifest marks `frozen=false`. It is not the final Recording codec.

Contact Sheet composition, checksums and ZIP generation execute on writer/finalizer workers after capture/state acquisition drains.

Contact Sheet dimensions, pixels, decoded sources, raw allocation, output bytes, per-frame/state bytes, Session bytes and Bundle source bytes use checked aggregate budgets. Oversized sheets split into multiple derivatives. Artifact download returns a `Path` and streams it through Netty chunked file transfer.

## Release Evidence Binding

Formal V1/Phase release evidence is generated from a clean detached worktree at the fetched remote commit. The Remote Parity Gate records commit/ref identity, worktree cleanliness, gate version/time, critical Git blob hashes and built Artifact hashes. A dirty source tree may only be called a working-tree candidate; it cannot establish `origin/master Release Candidate PASS`.

## Fixture, Debug Arm and Evidence Contamination

`fixture` and `debug` are absent from default scopes. Phase 5 conformance enables them explicitly.

Fixture operations require the Control Lease and report:

```text
mode=FIXTURE
mechanism=SERVER_API | DIRECT
evidenceContaminated=true
```

Debug mutations additionally require a short-lived `X-MCP-Debug-Arm` bound to a session-specific world fingerprint. Mismatch, expiry, disarm or world change fails closed. Representative typed operations are:

```text
fixture.player.teleport
debug.player.health
debug.world.block
```

Block Debug accepts `expectedBlockId` and only mutates an already-loaded block. No generic field setter, method invocation or reflection surface exists.

Any Fixture/Debug action during a Recording marks the session, timeline and Artifact manifest as contaminated. Such evidence cannot be presented as pure PLAYTEST acceptance.

## Phase 6 Dedicated Server Peer

All five Targets register an optional Minecraft play payload identified as `minecraft_protocol:peer_v0`. The Peer is not an HTTP server and does not expose the JVM; it is a typed bridge carried by the normal authenticated player connection:

```text
Client Runtime
  → hello / hello_ack negotiation
  → requestId + typed Minecraft operation
  → normal custom payload packet
  → logical Server main thread
  → detached JSON result
  → response + serverTick
```

V0 Peer operations are deliberately bounded to:

```text
peer.status
player.get
world.block.get
world.entities.query
world.fingerprint
fixture.player.teleport
debug.player.health
debug.world.block
```

The external HTTP contract remains semantic and strongly typed. There is no reflection, arbitrary method invocation, object traversal or generic field mutation. Server block reads and writes operate only on loaded chunks; Debug block mutation retains `expectedBlockId` as a value precondition.

Peer reads are available to the connected player. Peer Fixture and Debug additionally require an explicit dedicated-server feature flag and operator/singleplayer-owner authority. HTTP scope, Control Lease and Debug Arm checks still run on the Client Runtime before its typed mutation is sent. The server applies its own independent flag/operator gate.

`GET /v0/server/peer` reports negotiation state without pretending the capability exists. `POST /v0/server/peer/probe` performs an actual serialized round trip. Remote-without-Peer requests fail `SERVER_PEER_UNAVAILABLE`; disconnect resets negotiation and completes every pending request exceptionally before clearing it.

The production routing rule uses Peer only for a real remote connection. `MCP_PEER_FORCE=true` is a conformance-only harness that routes Integrated Server calls through the payload codec, allowing every Target to exercise serialization, timeout and reply behavior without changing the normal topology decision.

Phase 6 verified both topologies on every Target:

- Integrated Server with forced payload routing, including read, Fixture, Debug and disconnect cleanup.
- Independent Dedicated Server process plus quick-play Client, without forced routing, including authoritative player/entity reads and disconnect cleanup.

Physical remote conformance intentionally used non-operator development players. It therefore verified `fixture=false` and `debug=false` as the correct server-side denial boundary; the operator-enabled mutation path was separately exercised on the Integrated Server serialization harness.

## Input Fidelity

The implemented Phase 0 entry layer is `GAME_ROUTED_RAW`:

- 1.20.1 invokes private MouseHandler callbacks and public KeyboardHandler input.
- 26.2 invokes private structured MouseHandler and KeyboardHandler callbacks.
- Container evidence separately observes Screen Slot, Menu dispatch, client packet and Server thread validation.

Direct mutation is not used by these paths.

## UI and Render Model

Interaction Tree starts at `Screen.children()`. Container slots are projected separately from the active Menu.

26.2 Render Facts observe `GuiRenderState` submission methods. They describe primitive render facts only. Fabric requires a read-only Accessor for vanilla container position fields; NeoForge provides convenience getters.

## Capture

- 1.20.1 uses RenderTarget screenshot capture.
- 26.2 uses graphics-device texture-to-buffer readback.
- 26.2 OpenGL and Vulkan share the same Runtime capture contract.
- NeoForge Vulkan run configuration disables its incompatible early loading window before Minecraft startup.

## Revision Model

V0 implements `screenRevision` and `menuRevision`. Screen revision refresh occurs during ticks and relevant queries/mutations because a Screen can change inside one input call.

There is no global world revision. Future concurrency protection uses resource revisions and value preconditions.

## Readiness

`/v0/readiness` reports runtime-observed hook state. A configured Mixin is not considered verified by configuration alone. Hooks that require a container click, capture or render submission remain `unverified_until_*` until exercised.

## Build Interface

The root Gradle wrapper is authoritative:

```powershell
.\gradlew.bat :versions:<target>:build
.\gradlew.bat :versions:<target>:runClient
.\gradlew.bat :versions:<target>:runServer

cd companion
npm ci
npm test
npm run conformance:live
```

Target versions and plugins remain target-owned. Product identity remains root-owned.
