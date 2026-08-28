# Phase 8 / V1 Release Hardening Evidence

## Evidence Binding

```text
sourceCommit: WORKING_TREE_UNCOMMITTED
sourceBaseCommit: 500fb05edc9df029288791045890a70b9cbfa0d4
branch: master
originCommit: 500fb05edc9df029288791045890a70b9cbfa0d4
workingTreeClean: false
gateVersion: phase8.1-remote-parity-v1
timestamp: 2026-08-28T11:17:43.3627744+00:00
artifactSourceCommit: WORKING_TREE_UNCOMMITTED
evidenceSourceCommit: WORKING_TREE_UNCOMMITTED
```

- Current verdict: `working-tree candidate PASS`; `origin/master V1 Release Candidate FAIL pending reconciliation commit/push/reverification`.
- Architecture impact: no architecture redesign; Wire Protocol v1 remains unfrozen.
- Git policy: Phase 8.1 creates no commit or push.

This tracked document is a human-readable evidence policy and summary, not a self-referential commit attestation. Formal commit-bound evidence is emitted by `Invoke-Phase8RemoteParityGate.ps1` from a clean detached worktree and must contain:

```text
sourceCommit
branch
originCommit
workingTreeClean
gateVersion
timestamp
criticalSourceHashes
artifactHashes
```

If the source worktree is dirty, the strongest permitted claim is `working-tree candidate PASS`. `origin/master Release Candidate PASS` requires the Remote Parity Gate to rebuild and validate the exact fetched commit.

## Phase 8.1 Working-Tree Candidate

The local repair based on `500fb05edc9df029288791045890a70b9cbfa0d4` passed:

- OpenAPI validation and generation;
- Hardening Static Gate across five Targets and six byte-equivalent hardening files;
- 8 Java tests with 0 failures;
- 3 Companion tests with 0 failures;
- five Target builds;
- unified ConditionEngine semantics for standalone and Pipeline Wait/Assert;
- Pipeline player/block/event waits and entity/menu asserts;
- 12 cancellation scenarios with 0 post-cancel input dispatches;
- EventHub, Security and Recording hardening suites;
- 7 sequential live runs, 5 integrated-world runs and 2 Vulkan runs.

Working-tree Artifact hashes:

| Target | Bytes | SHA-256 |
| --- | ---: | --- |
| `1.20.1-forge` | 195756 | `64A3CC1957A7507447B5142FA2D623A561D6F711CE0889E4CDA6D155654B0262` |
| `1.21.1-neoforge` | 194628 | `EB77EDF995AE08318AFD5D311530B31DB246976DF7568A8CF0A84FDFEB085DC8` |
| `26.1.2-neoforge` | 199600 | `707A2C8392BD000F08BA0BAC519B025B1ED363F4537C112089560DC689C56396` |
| `26.2-neoforge` | 199735 | `16A0AA732CABFBA2DBE33D8860291EB59EFC816501E5A312BF8C0513C29249EF` |
| `26.2-fabric` | 204173 | `73131F1850D6560A85100B9D6CABB2FAA6C1DC8F84D4DD58DA41EF765D8DFA42` |

These hashes are not remote release attestations because the source worktree is dirty. After commit and push, `Invoke-Phase8RemoteParityGate.ps1` must rebuild the fetched commit and emit new commit-bound hashes.

## Phase 8.1 Remote Verification Findings

Fetch and clean-worktree verification of `origin/master@500fb05edc9df029288791045890a70b9cbfa0d4` established:

- `REMOTE_VERIFY` was detached at the fetched commit and clean before and after validation;
- OpenAPI, the historical Hardening Static Gate, 8 Java tests, 3 Companion tests and five Target builds passed from that clean worktree;
- Companion production streaming was present: zero `arrayBuffer()` calls and live `content-length`, `getReader()` and `reader.cancel()` paths;
- EventHub, SecurityGate, cancellation, Recording budgets, Runtime Artifact streaming, MCP tools and typed schemas were on production paths;
- tracked Evidence incorrectly named `fd95d8aef6501029f9bdf0fd5adcfde907ad4ec7` as its baseline;
- standalone/Pipeline conditions still split Screen/UI evaluation from the remaining `ConditionEngine`, and Pipeline player/block/entity/menu parity was not covered.

The Phase 8.1 Remote Parity Gate therefore correctly returns `FAIL` for current `origin/master`. This document must not be changed to remote PASS until the repair is committed, pushed and revalidated from a newly fetched clean worktree.

## Historical Phase 8 Deterministic Gates

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

## Historical Phase 8 Live Hardening Conformance

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

## Historical Five-Target Live Smoke

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
