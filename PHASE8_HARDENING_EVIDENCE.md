# Phase 8 / V1 Release Hardening Evidence

- Date: 2026-08-28
- Baseline HEAD: `fd95d8aef6501029f9bdf0fd5adcfde907ad4ec7`
- Final verdict: `Phase 8 Hardening PASS`; `V1 Release Candidate PASS`
- Architecture impact: no architecture redesign; Wire Protocol v1 remains unfrozen
- Git policy: no commit or push created

## Deterministic gates

`conformance/phase8/Invoke-Phase8LocalGate.ps1 -Offline` passed with:

- OpenAPI `0.0.1-phase8` valid;
- 78 generated Java models and 78 generated TypeScript files;
- five current Phase 8 artifacts built;
- 8 Java hardening unit tests, 0 failures;
- 3 Companion Node test cases, 0 failures;
- 23 static MCP Tools, four Resources, two Resource templates and one Prompt;
- dependency audit: 0 vulnerabilities;
- hardening static audit: PASS across five Targets and six byte-equivalent common hardening files.

Java unit coverage includes committed Operation terminal state, cancellation propagation, deadline cancellation, active-operation bounds, audit identity correlation, EventHub typed filtering, expired ring resume/full resync and stalled-consumer queue gaps.

Companion coverage includes official MCP client negotiation, Operation get/wait/cancel, MCP request cancellation propagating to native `DELETE /operations/{id}`, current-player command mapping, Prompt Injection isolation, and bounded streaming for small, near-limit, declared-over-limit and unknown-length chunked-over-limit responses.

## Live hardening conformance

The final 26.2 NeoForge OpenGL representative passed the current code:

| Gate | Result | Evidence |
| --- | --- | --- |
| Typed Wait/Assert | PASS | Player, Block, Entity, Menu, Inventory, Event, Operation |
| Cancellation | PASS | 12 scenarios; 0 post-cancel input dispatches |
| Disconnect/Lease expiry | PASS | active lease-bound Pipeline cancelled and input cleared |
| EventHub | PASS | filter, fast consumer, stalled consumer, bounded gap, ring resume, expired resume, full resync |
| Security | PASS | auth, Host/Origin, principal, 1 MiB body, expensive-rate budget, 16-operation bound, audit correlation |
| Recording | PASS | 64 frames, maximum accepted Contact Sheet dimensions, 3 split sheets, 45,641,802 budgeted stored bytes including canonical duplication |
| Artifact | PASS | Netty file streaming and Companion streaming budget |
| Shutdown finalization | PASS | active Recording produced completed manifest and Bundle; no `RejectedExecutionException` |

Cancellation scenarios were: delay, W hold, multi-key hold, mouse hold, mid-drag, multi-step Pipeline, `wait.until`, UI hold delay, immediate cancel, near-normal-completion race, controlling WebSocket disconnect and Lease expiry.

## Five-Target final live smoke

Every row used the current hardened artifact and completed launch, readiness, authentication, capability/session, Interaction Tree, GAME_ROUTED input, integrated test-world entry, player movement/use input, client LIVE Player/Block/Entity observation, integrated-server authoritative Player/Block observation, current-player command through `NORMAL_NETWORK`, Composite Capture, WS event, world-to-title exit, Lease cleanup and clean client shutdown.

| Target | Build | Launch | Readiness | UI | Input | World | Capture | WS | Shutdown | Overall |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Forge 1.20.1 | PASS | PASS | PASS | PASS | PASS | PASS | OpenGL PASS | PASS | PASS | PASS |
| NeoForge 1.21.1 | PASS | PASS | PASS | PASS | PASS | PASS | OpenGL PASS | PASS | PASS | PASS |
| NeoForge 26.1.2 | PASS | PASS | PASS | PASS | PASS | PASS | OpenGL PASS | PASS | PASS | PASS |
| NeoForge 26.2 | PASS | PASS | PASS | PASS | PASS | PASS | OpenGL PASS / Vulkan PASS | PASS | PASS | PASS |
| Fabric 26.2 | PASS | PASS | PASS | PASS | PASS | PASS | OpenGL PASS / Vulkan PASS | PASS | PASS | PASS |

Backend evidence was read from `capture.info.backend`; no unavailable backend was reported as successful.

## Scope and security decision

ADR-0001 formally defines V1 as loopback-only. LAN exposure remains in Ultimate Scope and is not advertised as V1-ready. The implemented V1 baseline includes Bearer authentication, token-lifetime principal identity, scopes, Host/Origin validation, per-principal/per-connection and expensive-category budgets, bounded operations/subscriptions, Control Lease, Debug Arm, correlated audit data, Prompt Injection isolation and disconnect cleanup.

## Remaining non-blockers

- Wire Protocol v1 remains intentionally unfrozen.
- A separate 2026-07-28-era MCP client fixture remains desirable; the official v2 client negotiated `2025-11-25` in current tests.
- Persistent/revocable multi-account principals and TLS/pairing/IP policy are part of the deferred LAN/Ultimate profile.
- Golden Diff, Rolling Recorder, Replay, Tick Step, full World Delta reconstruction, persistent storage operations and advanced crash diagnostics remain Ultimate/deferred.
