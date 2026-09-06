# Native V0 Developer Reference

> Status: CURRENT developer reference, not a frozen wire specification.
> Native protocol: `v0`; OpenAPI version: `0.0.1-control-r24`.
> Authority: [OpenAPI source](../../components/protocol-schema/src/main/openapi/minecraft-control-v0.json)
> and Runtime capabilities/operation declarations; this prose does not add endpoints.
> [Agent control](../architecture/agent-control.md) · [Architecture](../architecture/overview.md)
> · [Companion configuration](../../components/companion/README.md).

## Transport

The V0 probe uses authenticated loopback HTTP and WebSocket endpoints. Runtime libraries are packaged into the final Target artifacts. TLS/pairing and LAN exposure are not implemented in the current loopback-only profile.

The complete HTTP operation list is maintained in OpenAPI rather than duplicated
here. Current entry points for discovery and intent are:

```text
Authorization: Bearer <token>
X-MCP-Request-Id: <optional correlation ID>
X-MCP-Protocol-Version: v0
GET  /v0/session
GET  /v0/capabilities
GET  /v0/readiness
GET  /v0/operations
GET  /v0/control/mode
POST /v0/control/mode
POST /v0/control/acquire
WS   /v0/events
```

All 75 formal HTTP operations carry `x-agent-mode`; the
[classification index](../../validations/tests/core/control/control-mode-surface.json)
also covers the 24 MCP Tools and their typed discriminators. READ-compatible,
OPERATE-required, TAKEOVER-required and mode-independent are intent policies,
not permission levels. A mixed Tool is not classified only by its name.

## MCP Companion Mapping

The Phase 8 TypeScript Companion is an adapter over this native contract, not another source of Minecraft authority. It serves stdio through the official MCP v2 `serveStdio` entry point and publishes 24 curated Tools, four static Resources, two Recording Resource Templates and one static acceptance Prompt.

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

Long operations return an `operationId` handle and declare cancellation support. Typed Debug already requires operation-specific Debug Arm and resource/value context. Lease, idempotency, cancellation and preconditions are operation capabilities, not mandatory fields on reads.

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
read ui input capture event diagnostics control command
```

Input is a single-writer capability. `control.acquire` returns a Lease ID with a bounded TTL; input operations require `X-MCP-Control-Lease`. Release, TTL expiry, associated control WebSocket disconnect, emergency release and transport shutdown all schedule release events through Minecraft's input handlers.

Only explicit acquire/reacquire enters TAKEOVER. READ and OPERATE cannot emit player/GUI input; OPERATE mutation uses its own scope/Arm checks without an input Lease. Native physical Esc returns TAKEOVER to READ and preserves the independent reconsent latch. Agent-routed Esc is ordinary GUI input. The Runtime cannot authenticate conversation consent.

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
ResourceRevisionRef:
  sessionEpoch
  resourceType / resourceKey
  lifecycleId
  revision / revisionSource / revisionScope
  mutationPreconditionEligible
snapshotId / querySnapshotId / stateFrameId
```

There is no global `expectedWorldRevision`. A block mutation can use dimension, position and expected block value; a menu operation can use screen/menu/slot revisions or expected item value.

Formal Debug uses an eligible resource-scoped version plus typed value preconditions. Query-view Provider revisions are not mutation tokens. Runtime restart and resource lifecycle reuse invalidate old versions. Generic arrays remain ordered; Provider resource revisionState is independent of query projection.

The implemented input mutations accept `X-MCP-Expected-Screen-Revision` and `X-MCP-Expected-Menu-Revision`. Unrelated world changes cannot invalidate these operations.

## Thread Affinity

Transport workers pass only detached request values into Minecraft schedulers. Results are detached JSON or byte buffers. `/v0/diagnostics/thread?affinity=client|render|server` provides conformance evidence from the requested owner thread; `server` returns `SERVER_UNAVAILABLE` unless an integrated server is active.

## Hook Compatibility Manifest

`GET /v0/diagnostics/hooks` is a read-only Target-owned declaration and self-test surface. It reports the Capability/Fidelity First policy, aggregate Overwrite/cancellation/third-party target counts, and per-Hook mechanism, Minecraft target, injection point, behavior, runtime status and failure capability.

Current governance permits necessary typed Mixins, Invokers and Accessors, forbids Overwrite and third-party Mixin targets, and explicitly audits the Operator-control cancellations/redirects. The current gate records 13 legacy / 15 modern cancellations and four redirects per Target. Observation hooks remain non-cancelling. The old Phase 7 zero-cancellation rule is historical, not the current control implementation. A Hook that has not been exercised reports `unverified_until_*`; an unavailable legacy path reports `capability_unavailable`. Static configuration must not be relabeled runtime verified.

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

Resolution returns the current node and a `bounds_center` point. `ui.action` supports hover as well as click/scroll/press/release. GUI input moves the logical pointer along the existing deterministic trajectory and invokes normal mouseMoved/hover handlers. Screen/element identity, revision, scale/dimensions and bounds are rechecked before each side effect and atomic press. One bounded gesture queue serializes pointer work; stale geometry is rejected, not clicked at an old coordinate.

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
mouse.move / mouse.delta / mouse.button / mouse.click / mouse.scroll / mouse.drag
key / key.tap / key.chord
ui.action / ui.drag
wait.until / assert.that
```

Every step revalidates the Control Lease. Cleanup runs after success by default and always runs after failure or cancellation. Setting `cleanupOnComplete=false` may intentionally preserve input, but Lease expiry/release remains authoritative.

Standalone and Pipeline conditions share ConditionEngine: Screen/UI, Player, Block, Entity, Menu, Inventory, Recording, Event, Operation and Provider conditions, as declared by the schema. Runtime-side polling replaces fixed Agent sleeps. Relative gameplay mouse.delta uses Vanilla camera sensitivity/inversion and no host cursor capture; this is not direct yaw/pitch mutation.

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

Remote servers without the payload return `SERVER_PEER_UNAVAILABLE`. Read operations use the permissions already represented by the authenticated Minecraft player connection. Fixture/Debug additionally require explicit server feature flags and operator authority; the HTTP-facing Runtime still requires its own scopes, explicit OPERATE and applicable Debug Arm before sending Fixture/Debug. Peer flag/operator checks remain independently authoritative; input Lease applies to player-style TAKEOVER paths.

## Formal Deep Observation

`GET /v0/observe/deep/capabilities` and `POST /v0/observe/deep` expose bounded,
projection-aware typed snapshots with explicit perspective, completeness, read
effects, session epoch, snapshot IDs and resource versions. Third-party data is
schema-validated and remains untrusted. Unsupported fields are partial/unavailable,
not a reason to traverse arbitrary JVM objects or force-load chunks.

Persistent reads remain separate at `POST /v0/diagnostics/phase9a/storage/read`:
explicit `storage.read`, bounded IO, `PERSISTED / last_saved_state`, stale risk and
identity/lifecycle checks. Busy/locked, changed/stale, missing and corrupt outcomes
are distinct. A safely retained last-world context supports post-Save-&-Quit reads.
There is **no storage.write route**.

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

The compatibility Phase 4 SPI is LIVE-only and has no Persistent Storage fallback. Formal Provider V2 adds enforced schemas, scope/effects/affinity declarations, bounded completion/cancellation, query-independent resource revisionState and native revision integrity. Typed Provider mutation is a separate OPERATE + Debug authorization path, never smuggled into a read.

## State Frame

A State Frame performs 1–32 provider reads and returns a versioned correlation object. Its consistency is `coordinated_best_effort`; Client and Server results may come from adjacent ticks. It is not a transaction and does not introduce a global world revision.

## Capture

`GET /v0/capture` returns `image/png`. Operator Chrome and Agent Virtual Pointer are excluded by the final rendering/readback order; ordinary game hover/tooltips remain captured. 1.20.1 uses the RenderTarget screenshot path. 26.2 uses asynchronous GPU texture-to-buffer readback and works on OpenGL and Vulkan in the verified probes.

`GET /v0/capture/info` reports the actual device backend, Composite/PNG mode, runtime verification, IO-pool encoding and input-concurrency support. Capture/input conformance holds a key and advances a mouse Pipeline while eight screenshots complete in parallel.

## Recording and Artifact

The Phase 5 Recording request controls interval, duration, maximum samples, frame capture, selected Provider reads and Contact Sheet dimensions. Limits are bounded in Runtime.

Recording observation is READ-compatible and does not acquire the input Lease. Capture/state acquisition and a bounded writer run independently from input Pipelines. Backpressure policy is `drop_sample_and_record_gap`.

The versioned Artifact Bundle contains readable manifest/index/checksum files, raw frames, State Frame JSON, an NDJSON debug export, Contact Sheet and an experimental binary canonical store. The binary store begins with `MCPR` and is explicitly marked `frozen=false`; consumers must not treat its V0 layout as Wire Protocol v1.

Artifact download uses an opaque Recording ID. Callers cannot supply filesystem paths.

## Fixture and Debug Arm

`fixture` and `debug` are independent scopes and absent from default grants. Non-player Fixture/Debug mutation requires explicit OPERATE, not the player input Lease. Formal Debug additionally requires:

```text
authenticated principal
debug + debug.write + operation domain scope
X-MCP-Debug-Arm, bound to session/world/namespace
current worldFingerprint
unexpired TTL / deadline
eligible expectedResourceVersion and operation value preconditions
```

Representative typed operations are player teleport in FIXTURE mode and Player health / loaded Block mutations in DEBUG_PRIVILEGED mode. Block mutation supports `expectedBlockId`. None exposes arbitrary reflection or object traversal.

Mutation results declare mode, perspective, mechanism, direct-mutation use, storage access and `evidenceContaminated=true`. Active Recording Sessions copy contamination into timeline and manifest.

Formal mutations/batches are declared at `/v0/debug/mutations` and `/v0/debug/batches`; the legacy representative paths remain compatibility surfaces. Capability and evidence fields must not be generalized to unsupported Chunk/Client/Network or Peer domains.

## Errors

Errors remain structured Native/MCP data, not strings from which a client guesses intent. Key control errors include `TAKEOVER_REQUIRED`, `OPERATE_REQUIRED`, `USER_MANUALLY_ENDED_CONTROL`, `STALE_MODE_REVISION` and `MODE_OPERATION_IN_PROGRESS`; manual-revocation results preserve `reconsentRequired`. Resource-version errors are separate from value-precondition failures.

The following is a non-exhaustive compatibility error index; exact per-operation responses are governed by OpenAPI and Runtime:

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

## Historical baseline capability coverage

The following rows retain Phase 2–8 capability evidence. They are not a fresh attestation of current HEAD or the new exclusive-input/pointer UX; current limitations and pending acceptance live in the execution plan.

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

## Historical conformance pointers

These phase-era descriptions are retained as evidence context, not rerun claims.
Old drivers may predate explicit OPERATE/TAKEOVER; use current control gates for
current mode/cancellation checks. No gate is implicitly passed by this document.

### Phase 2 Conformance Boundary

The black-box Phase 2 suite verifies authentication, Host/Origin rejection, protocol correlation, operation declarations, Deadline, resource preconditions, Lease conflict/renew/release/expiry, input cleanup, control WebSocket disconnect cleanup, idempotency, cancellation, audit and Client/Render thread ownership. Integrated Server thread ownership is a separate active-world gate. Scope denial is tested by launching a Runtime with `MCP_RUNTIME_SCOPES=read`.

### Phase 3 Conformance Boundary

The same Phase 3 scenario passes on all five Targets. It validates semantic roles, selector resolution, generated coordinates, a standard Widget-based Mod Screen, screenshot/vision coordinate fallback, Runtime wait/assert, scrolling, segmented dragging, multi-key chords, Pipeline cancellation cleanup and a single Pipeline that enters an integrated world, opens Inventory, resolves Slot 0, clicks it through Screen/Menu/normal packet/Server validation and closes Inventory. NeoForge 26.1.2/26.2 and Fabric 26.2 additionally verify structured Render Facts; Forge 1.20.1 and NeoForge 1.21.1 report them honestly unavailable.

### Phase 4 Conformance Boundary

Phase 4 verifies title-screen authoritative unavailability, explicit client/server LIVE metadata, matching player UUIDs, loaded block agreement, unloaded block refusal without storage or chunk loading, entity source separation, registered third-party provider trust propagation, multi-source State Frames and Capture/input concurrency. OpenGL and Integrated Server authority are exercised on all five Targets; 26.2 NeoForge/Fabric additionally retain Vulkan evidence.

### Phase 5 Conformance Boundary

All five Runtime Targets record 20 Composite frames and 20 multi-provider State Frames while a real input Pipeline runs. Each produces a readable Contact Sheet, checksums, NDJSON export, `MCPR` binary store and downloadable ZIP. Fixture and Debug operations are executed as same-state/no-op probes, contaminating evidence without materially changing the test world. Missing, mismatched, disarmed and expired Debug Arms fail closed. A separate default-scope run confirms Fixture/Debug denial.

### Phase 6 Conformance Boundary

All five Targets pass the same Integrated Peer serialization scenario and the same independent Dedicated Server scenario. The gate verifies typed unavailability before connection, `peer-v0` negotiation, authoritative LIVE provenance, no persistent-storage fallback, player/block/entity requests, server feature/operator denial, and disconnect cleanup with zero pending requests. The Integrated harness additionally exercises Peer Fixture and Debug with explicit scopes, server flags, Debug Arm and a same-state block precondition.

### Phase 7 Conformance Boundary

All five Targets pass the same extended Widget scenario: EditBox semantics, disabled action rejection, duplicate-selector ambiguity, `nth` resolution, dynamic child discovery, routed semantic clicks, screenshot capture and Hook manifest self-test. Modern Targets additionally expose Render Facts; legacy Targets report them unavailable. The static Hook gate verifies source/config agreement and rejects high-conflict transformation mechanisms.
