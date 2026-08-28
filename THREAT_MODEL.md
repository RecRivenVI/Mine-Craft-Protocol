# Mine-Craft-Protocol Threat Model Baseline

> Status: Phase 8 V1 release-hardening baseline  
> Scope: current V0 loopback Runtime and future privileged control plane

## Protected Assets

- Player input authority.
- Loaded client and server world state.
- Future Fixture and DEBUG_PRIVILEGED authority.
- Authentication tokens and control leases.
- Screenshots, chat, books, signs, logs and recorded state.
- Local game stability and save integrity.

## Trust Boundaries

```text
Agent / Companion
  │ authenticated request
  ▼
loopback HTTP/WS worker
  │ scheduled immutable command
  ▼
Minecraft client thread
  │ normal packet or Server Peer bridge
  ▼
Minecraft server thread
```

Minecraft-provided content is untrusted even when it originates from a locally loaded world. A remote server, resource pack or Mod may control visible text and rendered content.

## Current Implemented Controls

- Bind to `127.0.0.1` only.
- Generate a fresh 256-bit Bearer token unless an explicit token is supplied; hand it off through the game-directory token file without logging its value.
- Require the Bearer token on HTTP and WebSocket upgrade and compare it in constant time.
- Return `401` without valid authorization.
- Require exact loopback Host and Origin values; prefix lookalikes are rejected.
- Enforce per-operation scopes.
- Require a bounded single-writer Control Lease for every input mutation.
- Release Runtime-owned input on explicit release, TTL expiry, associated control-channel disconnect, transport close and emergency release.
- Enforce optional relative deadlines, operation-local idempotency and Screen/Menu resource preconditions.
- Expose cancellable long operations and a bounded metadata-only audit ring.
- Bound input Pipelines to 256 steps/five minutes, validate the Lease before every step and run input cleanup on completion, failure or cancellation.
- Treat selector labels, GUI text, Render Facts and vision-model coordinates as untrusted data-plane input.
- Label registered third-party Provider output as untrusted and keep the Phase 4 Provider SPI LIVE-only.
- Refuse unloaded Server block queries without chunk loading or persistent-storage fallback.
- Keep Fixture/Debug scopes disabled by default and require Control Lease plus a world-bound TTL Debug Arm for Debug mutations.
- Bound Recording acquisition and writer queues; drop and record gaps instead of blocking game threads.
- Limit aggregated HTTP request bodies to 1 MiB.
- Bound world entity radius and result count.
- Bound wait timeout.
- Schedule Minecraft operations onto the client thread.
- Do not expose arbitrary filesystem, shell, process, ClassLoader or reflection operations.
- Report actual input provenance and direct-mutation flags.

No default credential is tracked. The token file is still readable by processes with the same user authority; loopback does not defend against a fully compromised local account. V1 is formally loopback-only under ADR-0001. LAN exposure remains unavailable and in Ultimate Scope.

## Prompt Injection Isolation

Minecraft text may enter only the data plane:

```text
chat/book/sign/MOTD/GUI text
  → observation result
  → trust/source metadata
```

It must never modify:

- MCP Tool descriptions.
- System prompts.
- Runtime policy.
- Scope definitions.
- Debug Arm state.
- Destructive-action authorization.

The Companion preserves trust/provenance metadata and adds a data-plane-only boundary to every Agent-visible Runtime result.

## Principal Threats

### Unauthorized Local Control

Another local process may discover the port or token and inject input.

Implemented controls: random per-run token, loopback-only listener, scopes, Control Lease, TTL, constant-time credential comparison, stable token-lifetime principal identity, per-principal/per-connection request budgets, category-specific expensive-operation budgets, bounded active operations and audit correlation across principal/connection/Lease/Debug Arm/Operation. Stronger platform-specific token-file ACL hardening is defense-in-depth beyond the portable V1 baseline.

### LAN Exposure

Binding beyond loopback exposes full player control to the network.

Current status: LAN binding is deliberately unavailable in the V1 Release Profile under ADR-0001. Host/Origin validation, audit, scopes and rate budgets are active on loopback. TLS, pairing, revocable persistent principals, IP allowlists and a separate LAN conformance gate are required before future LAN enablement.

### Input State Sticking

Disconnect or cancellation may leave keys/buttons held.

Implemented controls: Lease TTL, control WebSocket disconnect cleanup, transport-close cleanup, emergency release, observable Runtime-owned input dispatch sequence/state, a Pipeline cancellation token, tracked current child and scheduled handles, owner-thread cancellation barriers and deferred-callback checks. Hardening conformance covers delay, single/multi-key hold, mouse hold, mid-drag, multi-step, wait, UI hold, immediate/near completion, disconnect and Lease expiry, and verifies no later input sequence after cleanup.

### Malicious Automation Pipeline

A caller may submit very large, slow or deliberately stuck macro programs.

Controls: bounded body size, maximum 256 steps, per-step delay limits, maximum five-minute Pipeline lifetime, propagated operation cancellation, Lease validation before every step and a maximum of 16 concurrently retained operations before terminal eviction. Unknown step and condition types fail closed.

### Vision Coordinate Confusion

A screenshot model may return stale, off-screen or adversarially influenced coordinates.

Controls: explicit `gui_scaled` coordinate space, Screen/Menu preconditions where supplied, targeting provenance (`interaction_tree`, `explicit_coordinate` or `vision`), normal Minecraft input routing and no interpretation of game text as control-plane policy.

### Malicious or Oversized Game Data

NBT, Components, entity queries or rendered text may exhaust memory or Agent context.

Planned controls: projections, limits, pagination, byte budgets, depth limits and untrusted-content tagging.

### Thread-Safety Violation

Transport threads may read live Minecraft objects or mutate state.

Control: all current operations schedule onto the owner thread and return detached results. This remains a release gate.

### Debug Privilege Confusion

Fixture or direct mutation could be reported as gameplay success.

Implemented baseline controls: separate default-disabled scopes, Control Lease, world fingerprint, TTL Debug Arm, strongly typed operations, mutation provenance, recording contamination propagation and audit. Broader Debug domains remain later work.

### Recording Resource Exhaustion

Continuous capture may exhaust GPU readback slots, memory, disk or writer capacity.

Controls: bounded duration/sample count, maximum two in-flight samples, fixed 64-entry writer queue, drop-and-gap backpressure, asynchronous PNG encoding/writes/composition, bounded State Frame reads and explicit Artifact status. Ultimate size budgets and retention policies remain future work.

### Artifact Data Exposure

Artifacts may contain screenshots, player/world state and untrusted Mod text.

Controls: authenticated loopback download, per-session opaque IDs, source/trust metadata, checksums, no arbitrary filesystem browsing endpoint and no path parameter supplied by callers. Artifact directories are Runtime-generated beneath the game directory.

### Persistent Storage Corruption

Future storage mutation may conflict with live state or partial saves.

Planned controls: separate `storage.world.*` namespace, world fingerprint, explicit consistency, backup/checkpoint and no implicit fallback from live query.

Current Phase 4 enforcement: no persistent-storage endpoint exists; all built-in and registered Provider responses declare `dataSource=LIVE` and `storageAccessed=false`; unloaded chunks remain unavailable.

### Malicious Read Provider

A third-party Mod may register a provider that returns oversized, misleading or prompt-injection content.

Controls: explicit namespaced registration, reserved `minecraft:` namespace, detached JSON-only contract, standard request-body limits, trust/source propagation and no promotion of provider data into Tool descriptions, scopes or Runtime policy. Provider code remains part of the registering Mod's trust boundary and must own its thread scheduling.

### Observation Authority Confusion

Client-known state may be stale or incomplete compared with Integrated Server state.

Controls: separate endpoints and provider IDs, explicit `perspective`, `source`, `authority`, `stalePossible`, Server tick evidence and typed unavailability when no authoritative server is present. State Frames declare `coordinated_best_effort`, not transactional consistency.

### Dedicated Server Peer Abuse

A modified client or another client-side Mod may craft Peer payloads directly, bypassing the loopback HTTP surface.

Controls: the server accepts only a closed set of Minecraft-domain operations, bounds every JSON payload below 32 KiB, validates all arguments, runs on the logical Server owner thread, refuses unloaded chunk mutation, and independently gates Fixture/Debug by explicit server flags plus operator/Integrated-owner authority. Read results are limited to the connected player's current server/dimension context. No generic RPC, reflection or persistent-storage operation exists.

The client-side Debug Arm remains required for the supported HTTP workflow, but it is not treated as a server authentication credential. The server's independent flag/operator decision is the security boundary against a crafted payload.

### Peer Authority or Lifecycle Confusion

A remote server without the Mod could be mislabeled authoritative, or stale requests could survive a disconnect and complete against a replacement connection.

Controls: explicit hello/ack negotiation, connection-identity reset, generated request IDs, five-second timeout, pending-future failure/clear on disconnect, `peerAuthenticated` and `serverTick` evidence, and typed `SERVER_PEER_UNAVAILABLE` degradation. Provider/State Frame wrappers propagate the actual returned source instead of a static Integrated Server label.

### Hook Collision and Silent Degradation

An invasive Hook may replace another Mod's behavior, cancel a normal call path, target third-party code, or silently stop applying after a Minecraft update.

Controls: Capability/Fidelity First selection, typed Minecraft targets, no Overwrite/cancellation/replacement-style Hooks in the V1 baseline, source/config count agreement, required injections, runtime self-test, typed Hook manifest and capability degradation when evidence is missing. The gate reduces predictable collision risk but cannot prove compatibility with every future third-party transformation.

### Disabled Semantic Node Confusion

An Agent may trust a Tree node's `active=false` or empty `actions` declaration while the Runtime still emits a coordinate click.

Control: selector-based UI action now revalidates visibility, active state and supported action before input generation and fails `UI_NODE_NOT_ACTIONABLE`. Explicit/Vision coordinate actions remain deliberately available as a separately-provenanced fallback.

### Companion Control-Plane Injection

Minecraft text could be copied into Tool descriptions, Prompt instructions or permission definitions and become control-plane input.

Controls: all Tool/Resource/Prompt registrations are static source declarations; Prompt construction performs no Runtime read; every Runtime result is wrapped `dataPlaneOnly=true` and `dynamicPolicyApplied=false`; conformance injects instruction-like GUI text and verifies it appears only in result data.

### MCP stdio Corruption or Credential Disclosure

Logging on stdout can corrupt JSON-RPC, while errors or configuration output may disclose the Runtime token.

Controls: `serveStdio(factory)`, no production `console.log`, static stderr-only readiness, structured safe errors, credential redaction tests, exact token environment/file lookup and no token value in Tool/Resource output.

### Companion Network Expansion

A Companion pointed at a non-loopback Runtime could silently expand the attack surface.

Controls: loopback is the default and non-loopback URLs fail unless `MCP_COMPANION_ALLOW_NON_LOOPBACK=true` is explicit. The opt-in does not claim TLS/pairing and the Companion exposes no general proxy, shell, filesystem browser or process control.

### Release Evidence Source Drift

Tests run against a dirty worktree can describe capabilities that are absent from the published commit, while a tracked evidence document can retain an obsolete source SHA after later commits.

Controls: formal release evidence must be generated from a clean detached worktree at the fetched `origin/master` commit. The Remote Parity Gate records `sourceCommit`, `originCommit`, branch, cleanliness, gate version/time, critical Git blob hashes and built Artifact hashes. Dirty-tree results are labeled working-tree candidates and cannot establish a remote Release Candidate PASS.

## Explicit Non-Goals

The Runtime will not provide:

- Arbitrary shell execution.
- Arbitrary filesystem browsing.
- Arbitrary JVM reflection RPC.
- ClassLoader manipulation.
- General process control.

Internal invasive implementation remains restricted behind typed Minecraft-domain operations.

## Phase 8 Security Evidence

- Readiness and capabilities cannot overclaim failed hooks.
- Black-box conformance checks authentication, Origin rejection, protocol negotiation, scopes, single-writer Lease behavior, stale resource preconditions, deadlines, cancellation, TTL cleanup, control-channel disconnect cleanup and audit.
- No tracked production credential exists and generated token values are not logged.
- No listener binds beyond `127.0.0.1`.
- Request bodies, credentials and game text are excluded from the Phase 2 audit ring.
- Thread-affinity diagnostics return detached data rather than live Minecraft objects.
- Automation conformance verifies Pipeline cancellation while W is held and observes that all Runtime-owned input is released.
- The standard Mod GUI is opened only through a typed Fixture operation that marks evidence contamination.
- A far unloaded Server block returns `chunk_not_loaded`, `chunkLoadRequested=false` and `storageAccessed=false` on all five Runtime Targets.
- Provider discovery/read and State Frame output preserve LIVE/source/trust metadata.
- OpenGL/Vulkan capture concurrency tests finish with no held Runtime input.
- Default-scope conformance rejects Fixture and Debug endpoints with `SCOPE_DENIED`.
- Wrong fingerprints, missing Arms, disarmed Arms and expired Arms fail closed.
- Recording manifests and timelines preserve Fixture/Debug contamination.
- Debug block mutation uses an expected-value precondition and refuses unloaded targets.
- Every Target starts as a physical Dedicated Server without loading client-only Runtime hooks.
- Every Target completes a separate Client-to-Dedicated-Server Peer round trip without forced routing.
- Non-operator remote players receive `fixture=false` and `debug=false` even when server feature flags are enabled.
- Peer disconnect cleanup reports `connected=false` and zero pending requests.
- Peer-backed State Frames preserve `source=dedicated_server_peer` at both wrapper and data levels.
- Static inspection finds zero Overwrite, cancellable injection, replacement-style Hook and third-party Mixin targets across all five Targets.
- Runtime Hook manifests report core self-test readiness and per-capability failure behavior.
- Disabled semantic controls reject selector actions before input is generated.
- Official MCP Client conformance verifies static Tool/Prompt definitions under malicious-looking game text.
- PNG and Artifact data use bounded MCP Resources with UUID-only Recording identifiers.
- npm audit reports zero known vulnerabilities and dependencies are lockfile-pinned.
- Companion production sources contain no shell/process execution API and no stdout logging.
- Real Phase 8 Minecraft MCP conformance returns to title and releases the Control Lease cleanly.

## Current Residual Risks

- A process running as the same OS user may read the token handoff file or inspect the game process.
- The audit ring is volatile and bounded; it is not a tamper-evident security log.
- LAN pairing/TLS and persistent, independently revocable account principals are Ultimate/deferred; V1 remains loopback-only and uses a token-lifetime authenticated principal.
- Idempotency storage is in-memory and process-local.
- Scope configuration is per Runtime process; fine-grained multi-account principals are deferred.
