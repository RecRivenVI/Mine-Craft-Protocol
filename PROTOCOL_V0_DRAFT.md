# Mine-Craft-Protocol V0 Draft

> Status: evidence-based Phase 8 release-hardening draft  
> Stability: unstable; not Wire Protocol V1  
> Source of truth: verified Phase 0 behavior plus Phase 2–8 Runtime and MCP conformance

## Transport

The V0 probe uses authenticated loopback HTTP and WebSocket endpoints. Production transport packaging, TLS and LAN exposure are outside this draft.

```text
Authorization: Bearer <token>
X-MCP-Request-Id: <optional correlation ID>
X-MCP-Protocol-Version: v0
GET  /v0/session
GET  /v0/capabilities
GET  /v0/readiness
GET  /v0/trace
GET  /v0/audit
GET  /v0/security/context
GET  /v0/diagnostics/thread
GET  /v0/diagnostics/hooks
POST /v0/diagnostics/ui/test-screen  # DIRECT Fixture Arrange; contaminated
GET  /v0/operations
POST /v0/operations/wait/screen
GET  /v0/operations/{operationId}
DELETE /v0/operations/{operationId}
GET  /v0/control/status
POST /v0/control/acquire
POST /v0/control/renew
POST /v0/control/release
POST /v0/control/emergency-release
GET  /v0/ui/tree
POST /v0/ui/resolve
POST /v0/ui/action
GET  /v0/ui/vision/context
GET  /v0/render/facts           # capability-gated
GET  /v0/input/state            # Runtime-owned virtual state only
POST /v0/input/mouse/move
POST /v0/input/mouse/button
POST /v0/input/mouse/scroll
POST /v0/input/key
GET  /v0/player
GET  /v0/server/peer
POST /v0/server/peer/probe
GET  /v0/server/player
GET  /v0/world/block
GET  /v0/world/entities
GET  /v0/server/world/block
GET  /v0/server/world/entities
GET  /v0/providers
POST /v0/providers/read
POST /v0/state/frames
GET  /v0/capture/info
GET  /v0/capture
GET  /v0/world/fingerprint
GET  /v0/debug/status
POST /v0/debug/arm
POST /v0/debug/renew
POST /v0/debug/disarm
POST /v0/fixture/player/teleport
POST /v0/debug/player/health
POST /v0/debug/world/block
GET  /v0/recordings
POST /v0/recordings
GET  /v0/recordings/{recordingId}
DELETE /v0/recordings/{recordingId}
GET  /v0/recordings/{recordingId}/artifact
GET  /v0/wait/screen
POST /v0/wait/until
POST /v0/assert
POST /v0/pipelines
WS   /v0/events
```

## MCP Companion Mapping

The Phase 8 TypeScript Companion is an adapter over this native contract, not another source of Minecraft authority. It serves stdio through the official MCP v2 `serveStdio` entry point and publishes 19 curated Tools, four static Resources, two Recording Resource Templates and one static acceptance Prompt.

Large Composite PNG and Artifact ZIP data use `minecraft://` Resources. Tool/Resource JSON preserves native provenance and adds a data-plane-only marker. MCP Tool descriptions and Prompt instructions are static and never derived from Runtime text.

The Companion requires exact native `v0` and checks the OpenAPI document version during build. The official v2 Client negotiated MCP `2025-11-25` in repository conformance; the SDK's 2026-07-28 serving path is enabled by `serveStdio` but not yet separately client-gated here.

## Layered Request Envelope

The current probe maps fields directly to HTTP. The public request layer is minimal:

```text
requestId
negotiatedProtocol
deadline?
traceOptions?
metadata?
```

In the HTTP mapping, `requestId`, negotiated protocol and deadline use headers. Missing request IDs are generated; missing protocol version defaults to `v0`. Unsupported protocol versions fail with `426 PROTOCOL_VERSION_UNSUPPORTED`.

Mutation context is operation-specific:

```text
leaseId?
idempotencyKey?
preconditions?
```

Long operations return an `operationId` handle and declare cancellation support. Debug operations will later add Debug Arm context. Lease, idempotency, cancellation and preconditions are operation capabilities, not mandatory fields on reads.

`GET /v0/operations` reports, per operation:

```text
scope
requiresControlLease
requiresDebugArm
supportsIdempotency
supportsCancellation
supportedPreconditions
threadAffinity
```

## Authentication, Scopes and Control Lease

The Runtime binds `127.0.0.1` only, requires Bearer authentication and accepts only exact loopback Host/Origin values. A random 256-bit token is generated unless explicitly configured and is handed off through `<gameDirectory>/minecraft-protocol/token`.

Scopes are enforced per operation. The default probe scopes are:

```text
read ui input capture event diagnostics control
```

Input is a single-writer capability. `control.acquire` returns a Lease ID with a bounded TTL; input operations require `X-MCP-Control-Lease`. Release, TTL expiry, associated control WebSocket disconnect, emergency release and transport shutdown all schedule release events through Minecraft's input handlers.

The Lease is not required for ordinary reads. `GET /v0/input/state` reports only keys/buttons held by this Runtime so cleanup can be tested without observing unrelated human or OS input.

## Deadlines, Idempotency and Cancellation

`X-MCP-Deadline-Ms` is a relative request deadline. Elapsed requests fail with `408 REQUEST_DEADLINE_EXCEEDED` and wait loops observe cancellation/completion.

Input operations support `X-MCP-Idempotency-Key`. Keys are operation-local and cached only in bounded process memory. Long screen waits can be started asynchronously, inspected and cancelled through `/v0/operations/{operationId}`.

## Resource Revisions

```text
sessionEpoch / sessionRevision
screenRevision
menuRevision
nodeRevision
playerRevision?
entityRevision?
chunkRevision?
providerRevision?
snapshotId / querySnapshotId / stateFrameId
```

There is no global `expectedWorldRevision`. A block mutation can use dimension, position and expected block value; a menu operation can use screen/menu/slot revisions or expected item value.

The implemented input mutations accept `X-MCP-Expected-Screen-Revision` and `X-MCP-Expected-Menu-Revision`. Unrelated world changes cannot invalidate these operations.

## Thread Affinity

Transport workers pass only detached request values into Minecraft schedulers. Results are detached JSON or byte buffers. `/v0/diagnostics/thread?affinity=client|render|server` provides conformance evidence from the requested owner thread; `server` returns `SERVER_UNAVAILABLE` unless an integrated server is active.

## Hook Compatibility Manifest

`GET /v0/diagnostics/hooks` is a read-only Target-owned declaration and self-test surface. It reports the Capability/Fidelity First policy, aggregate Overwrite/cancellation/third-party target counts, and per-Hook mechanism, Minecraft target, injection point, behavior, runtime status and failure capability.

The V1 alignment baseline permits necessary Mixins, Invokers and Accessors but currently uses no Overwrite, cancellable injection, Redirect/Modify replacement or third-party Mixin target. A Hook that has not been exercised reports `unverified_until_*`; an unavailable legacy path reports `capability_unavailable`. Static configuration must not be relabeled runtime verified.

## Input Provenance

```text
entryLayer:
  GAME_ROUTED_RAW
  GAME_ROUTED_SCREEN
  GAME_ROUTED_KEYMAPPING
  NORMAL_NETWORK
  DIRECT

screenObserved
menuObserved
normalMenuProcessingObserved
normalPacketObserved
serverValidationObserved
directBusinessCallUsed
directMutationUsed
```

Evidence fields report observations. Missing evidence is false/unknown, not inferred.

## Interaction Tree Node

```text
nodeId
role
class
nodeRevision
coverage
label?
x?
y?
width?
height?
active?
visible?
interactionX?
interactionY?
actions[]
screenRevision
menuRevision?
slot?
item?
count?
```

Widgets come from Screen children. Slots are projected separately from the active Menu.

## Selector and UI Action

Selectors support exact or substring matching over node ID, role, label and class, plus Slot ID, case mode, visibility/activity filters and explicit `nth`. Zero matches return `UI_NODE_NOT_FOUND`; ambiguous matches return `UI_SELECTOR_AMBIGUOUS`.

Resolution returns the current node plus a `bounds_center` interaction point. `ui.action` re-resolves selectors and validates Screen/Menu resources before entering the same `GAME_ROUTED_RAW` path as raw mouse input.

For selector actions, V0 also validates the resolved node's `active`, `visible` and `actions` declaration. Disabled, hidden or action-incompatible nodes fail `UI_NODE_NOT_ACTIONABLE`. Coordinate/Vision actions intentionally bypass semantic actionability because they are the fallback for content that cannot provide a trustworthy tree.

Coordinate actions use `gui_scaled` coordinates and preserve targeting provenance:

```text
interaction_tree
explicit_coordinate
vision
vision_coordinate
```

## Input Pipeline and Conditions

`POST /v0/pipelines` starts a cancellable operation with up to 256 steps and a maximum five-minute lifetime. Supported V0 step types are:

```text
delay
mouse.move / mouse.button / mouse.click / mouse.scroll / mouse.drag
key / key.tap / key.chord
ui.action / ui.drag
wait.until / assert.that
```

Every step revalidates the Control Lease. Cleanup runs after success by default and always runs after failure or cancellation. Setting `cleanupOnComplete=false` may intentionally preserve input, but Lease expiry/release remains authoritative.

Current conditions cover Screen class/title/open state and UI selector existence. Runtime-side polling replaces fixed Agent sleep.

## Vision Fallback

`ui.vision.context` declares the capture endpoint, coordinate action endpoint, coordinate space, Screen revision and current tree coverage. The Runtime does not run a multimodal model; a Companion or Agent obtains `/v0/capture`, chooses coordinates, then calls `ui.action` with vision provenance.

## Render Facts

26.2 exposes counts plus the latest 256 structured facts for the current Screen revision from:

```text
GuiElementRenderState
GuiItemRenderState
GuiTextRenderState
PictureInPictureRenderState
```

Each fact has sequence, tick, Screen revision, category, implementation class and integer bounds. `semanticInference=false`: Render Facts do not promise recovered business semantics. Forge 1.20.1 and NeoForge 1.21.1 report the capability unavailable.

## Live Observation

V0 world endpoints read only loaded client-known state. They never fall back to persistent storage.

```text
player: client_known
block: client_known_live
entities: client_known_live
```

Server endpoints return authoritative LIVE state from an Integrated Server or a negotiated Dedicated Server Peer. They are capability-gated and fail when neither authority source is available.

Every result declares:

```text
perspective
source
authority
dataSource=LIVE
storageAccessed=false
stalePossible
```

Server block reads additionally declare `chunkLoadRequested=false`. Unloaded targets return `chunk_not_loaded`; no persistent read or chunk load is attempted.

## Dedicated Server Peer

`GET /v0/server/peer` returns the local negotiation state. `POST /v0/server/peer/probe` sends `peer.status` through the Minecraft custom-payload channel and therefore proves a serialized server round trip rather than merely inspecting client configuration.

The unstable Peer protocol is `peer-v0` and has four message kinds:

```text
hello
hello_ack
request
response
```

Requests carry a generated `requestId`, a closed set of typed operation names and detached JSON parameters. Responses correlate the request, carry `serverTick`, and return detached Minecraft-domain data or a typed error. V0 applies a five-second client timeout and clears all pending requests on connection replacement/disconnect.

Peer JSON is bounded below 32 KiB in both directions. Entity queries clamp radius to 128 blocks and results to 128 entries.

Implemented operation names are `peer.status`, `player.get`, `world.block.get`, `world.entities.query`, `world.fingerprint`, `fixture.player.teleport`, `debug.player.health` and `debug.world.block`. This is not a generic RPC surface.

Successful Peer data declares:

```text
source=dedicated_server_peer
authority=server_authoritative
perspective=server_authoritative_live
dataSource=LIVE
storageAccessed=false
peerAuthenticated=true
serverTick
```

Remote servers without the payload return `SERVER_PEER_UNAVAILABLE`. Read operations use the permissions already represented by the authenticated Minecraft player connection. Fixture/Debug additionally require explicit server feature flags and operator authority; the HTTP-facing Runtime still requires its own scope, Lease and Debug Arm before sending a typed mutation.

## Provider Read SPI

Built-in provider IDs include:

```text
minecraft:client/player
minecraft:client/world/block
minecraft:client/world/entities
minecraft:server/player
minecraft:server/world/block
minecraft:server/world/entities
minecraft:capture/info
```

Mods may explicitly register another namespaced provider through the Java SPI. `minecraft:` is reserved. Provider output carries `providerRevision`, `querySnapshotId`, perspective, thread affinity, source and trust. Third-party output is always Agent-visible untrusted data and cannot alter scopes, Tools or policy.

The Phase 4 SPI is LIVE-only and has no Persistent Storage fallback.

## State Frame

A State Frame performs 1–32 provider reads and returns a versioned correlation object. Its consistency is `coordinated_best_effort`; Client and Server results may come from adjacent ticks. It is not a transaction and does not introduce a global world revision.

## Capture

`GET /v0/capture` returns `image/png`. 1.20.1 uses the RenderTarget screenshot path. 26.2 uses asynchronous GPU texture-to-buffer readback and works on OpenGL and Vulkan in the verified probes.

`GET /v0/capture/info` reports the actual device backend, Composite/PNG mode, runtime verification, IO-pool encoding and input-concurrency support. Capture/input conformance holds a key and advances a mouse Pipeline while eight screenshots complete in parallel.

## Recording and Artifact

The Phase 5 Recording request controls interval, duration, maximum samples, frame capture, selected Provider reads and Contact Sheet dimensions. Limits are bounded in Runtime.

Recording does not acquire the input Lease. Capture/state acquisition and a bounded writer run independently from input Pipelines. Backpressure policy is `drop_sample_and_record_gap`.

The versioned Artifact Bundle contains readable manifest/index/checksum files, raw frames, State Frame JSON, an NDJSON debug export, Contact Sheet and an experimental binary canonical store. The binary store begins with `MCPR` and is explicitly marked `frozen=false`; consumers must not treat its V0 layout as Wire Protocol v1.

Artifact download uses an opaque Recording ID. Callers cannot supply filesystem paths.

## Fixture and Debug Arm

`fixture` and `debug` are independent scopes and are absent from default grants. Fixture operations require a Control Lease. Debug mutations require:

```text
debug scope
Control Lease
X-MCP-Debug-Arm
current worldFingerprint
unexpired TTL
```

Representative typed operations are player teleport in FIXTURE mode and Player health / loaded Block mutations in DEBUG_PRIVILEGED mode. Block mutation supports `expectedBlockId`. None exposes arbitrary reflection or object traversal.

Mutation results declare mode, perspective, mechanism, direct-mutation use, storage access and `evidenceContaminated=true`. Active Recording Sessions copy contamination into timeline and manifest.

## Errors

V0 currently exposes typed errors for the implemented subset and reserves the remaining capability-specific codes:

```text
UNAUTHORIZED
HOST_REJECTED
ORIGIN_REJECTED
SCOPE_DENIED
PROTOCOL_VERSION_UNSUPPORTED
CONTROL_LEASE_REQUIRED
CONTROL_LEASE_CONFLICT
REQUEST_DEADLINE_EXCEEDED
RUNTIME_NOT_READY
CAPABILITY_UNAVAILABLE
STALE_SCREEN_REVISION
STALE_MENU_REVISION
PRECONDITION_FAILED
CHUNK_NOT_LOADED
WAIT_TIMEOUT
CAPTURE_FAILED
HOOK_FAILED
OPERATION_NOT_FOUND
TOO_MANY_OPERATIONS
SERVER_UNAVAILABLE
UI_NODE_NOT_FOUND
UI_SELECTOR_AMBIGUOUS
UI_NODE_NOT_ACTIONABLE
UNSUPPORTED_UI_ACTION
INVALID_PIPELINE
UNSUPPORTED_PIPELINE_STEP
PIPELINE_TIMEOUT
ASSERTION_FAILED
UNSUPPORTED_CONDITION
SERVER_AUTHORITATIVE_UNAVAILABLE
SERVER_PLAYER_UNAVAILABLE
SERVER_PEER_UNAVAILABLE
SERVER_PEER_TIMEOUT
SERVER_PEER_DISCONNECTED
SERVER_PEER_ERROR
PEER_OPERATION_UNSUPPORTED
PEER_FIXTURE_DENIED
PEER_DEBUG_DENIED
PROVIDER_NOT_FOUND
INVALID_PROVIDER_REQUEST
INVALID_STATE_FRAME
DEBUG_ARM_REQUIRED
WORLD_FINGERPRINT_MISMATCH
UNKNOWN_BLOCK
RECORDING_NOT_FOUND
ARTIFACT_NOT_READY
ARTIFACT_CREATE_FAILED
INVALID_RECORDING
```

## Known Target Capabilities

| Capability | 1.20.1 Forge | 1.21.1 NeoForge | 26.1.2 NeoForge | 26.2 NeoForge | 26.2 Fabric |
|---|---|---|---|---|---|
| Interaction Tree | verified | verified | verified | verified | verified |
| GAME_ROUTED_RAW | verified | verified | verified | verified | verified |
| Render Facts | unavailable | unavailable | verified | verified | verified |
| OpenGL Capture | verified | verified | verified | verified | verified |
| Vulkan Capture | N/A | N/A | configured, not yet gated | verified | verified |
| HTTP/WS Runtime | verified | verified | verified | verified | verified |
| Selector/Vision/Input Pipeline | verified | verified | verified | verified | verified |
| Standard Widget-based Mod GUI | verified fixture | verified fixture | verified fixture | verified fixture | verified fixture |
| World → Inventory → Slot pipeline | verified | verified | verified | verified | verified |
| Client/Integrated LIVE observation | verified | verified | verified | verified | verified |
| Provider Read + State Frame | verified | verified | verified | verified | verified |
| Recording/Contact Sheet/Artifact | verified | verified | verified | verified | verified |
| Debug Arm + typed Debug | verified | verified | verified | verified | verified |
| Optional Dedicated Server Peer | remote verified | remote verified | remote verified | remote verified | remote verified |
| Peer authoritative reads | verified | verified | verified | verified | verified |
| Peer Fixture/Debug gate | flag/operator denial + integrated harness | flag/operator denial + integrated harness | flag/operator denial + integrated harness | flag/operator denial + integrated harness | flag/operator denial + integrated harness |
| Peer disconnect cleanup | verified | verified | verified | verified | verified |

## Phase 2 Conformance Boundary

The black-box Phase 2 suite verifies authentication, Host/Origin rejection, protocol correlation, operation declarations, Deadline, resource preconditions, Lease conflict/renew/release/expiry, input cleanup, control WebSocket disconnect cleanup, idempotency, cancellation, audit and Client/Render thread ownership. Integrated Server thread ownership is a separate active-world gate. Scope denial is tested by launching a Runtime with `MCP_RUNTIME_SCOPES=read`.

## Phase 3 Conformance Boundary

The same Phase 3 scenario passes on all five Targets. It validates semantic roles, selector resolution, generated coordinates, a standard Widget-based Mod Screen, screenshot/vision coordinate fallback, Runtime wait/assert, scrolling, segmented dragging, multi-key chords, Pipeline cancellation cleanup and a single Pipeline that enters an integrated world, opens Inventory, resolves Slot 0, clicks it through Screen/Menu/normal packet/Server validation and closes Inventory. NeoForge 26.1.2/26.2 and Fabric 26.2 additionally verify structured Render Facts; Forge 1.20.1 and NeoForge 1.21.1 report them honestly unavailable.

## Phase 4 Conformance Boundary

Phase 4 verifies title-screen authoritative unavailability, explicit client/server LIVE metadata, matching player UUIDs, loaded block agreement, unloaded block refusal without storage or chunk loading, entity source separation, registered third-party provider trust propagation, multi-source State Frames and Capture/input concurrency. OpenGL and Integrated Server authority are exercised on all five Targets; 26.2 NeoForge/Fabric additionally retain Vulkan evidence.

## Phase 5 Conformance Boundary

All five Runtime Targets record 20 Composite frames and 20 multi-provider State Frames while a real input Pipeline runs. Each produces a readable Contact Sheet, checksums, NDJSON export, `MCPR` binary store and downloadable ZIP. Fixture and Debug operations are executed as same-state/no-op probes, contaminating evidence without materially changing the test world. Missing, mismatched, disarmed and expired Debug Arms fail closed. A separate default-scope run confirms Fixture/Debug denial.

## Phase 6 Conformance Boundary

All five Targets pass the same Integrated Peer serialization scenario and the same independent Dedicated Server scenario. The gate verifies typed unavailability before connection, `peer-v0` negotiation, authoritative LIVE provenance, no persistent-storage fallback, player/block/entity requests, server feature/operator denial, and disconnect cleanup with zero pending requests. The Integrated harness additionally exercises Peer Fixture and Debug with explicit scopes, server flags, Debug Arm and a same-state block precondition.

## Phase 7 Conformance Boundary

All five Targets pass the same extended Widget scenario: EditBox semantics, disabled action rejection, duplicate-selector ambiguity, `nth` resolution, dynamic child discovery, routed semantic clicks, screenshot capture and Hook manifest self-test. Modern Targets additionally expose Render Facts; legacy Targets report them unavailable. The static Hook gate verifies source/config agreement and rejects high-conflict transformation mechanisms.
