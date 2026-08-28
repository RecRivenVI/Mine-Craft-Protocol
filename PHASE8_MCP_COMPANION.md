# Phase 8 MCP Companion and V1 Release Hardening

> Status: Phase 8.1 working-tree candidate PASS; origin/master Release Candidate FAIL pending commit-bound revalidation
> Date: 2026-08-28  
> Product baseline: V1 capability set complete; native Wire Protocol v1 remains unfrozen

## Delivered Companion

`companion/` is an independent Node.js/TypeScript process. Minecraft does not embed an MCP SDK; the Companion connects only to the authenticated native Runtime HTTP contract.

Dependencies are pinned to:

```text
@modelcontextprotocol/server 2.0.0
@modelcontextprotocol/client 2.0.0 (conformance only)
zod 4.4.3
TypeScript 7.0.2
Node.js >=22
```

The server uses `serveStdio(factory)`, keeps stdout exclusively for MCP JSON-RPC and sends its static readiness message to stderr.

## MCP Surface

The Companion exposes 23 statically declared Tools:

```text
minecraft_get_session
minecraft_get_capabilities
minecraft_get_ui
minecraft_get_state
minecraft_query_world
minecraft_control
minecraft_interact_ui
minecraft_run_input_pipeline
minecraft_get_operation
minecraft_wait_operation
minecraft_cancel_operation
minecraft_wait
minecraft_assert
minecraft_capture
minecraft_start_recording
minecraft_recording
minecraft_get_artifact
minecraft_diagnostics
minecraft_peer
minecraft_fixture
minecraft_debug_arm
minecraft_debug
minecraft_execute_player_command
```

It exposes four static Resources:

```text
minecraft://session
minecraft://capabilities
minecraft://ui
minecraft://capture/latest
```

and two templates:

```text
minecraft://recordings/{recordingId}
minecraft://recordings/{recordingId}/artifact
```

PNG and Artifact ZIP bytes are Resources rather than large Tool text. Recording IDs must be UUIDs and callers never supply filesystem paths.

One static `minecraft_mod_acceptance` Prompt describes the safe acceptance workflow. It never reads Minecraft content while constructing its instructions.

`minecraft_execute_player_command` maps only to `command.player.execute`: current player identity, current permissions, normal command packet/server validation, PLAYTEST provenance and no privilege escalation. Fixture/admin and Debug commands remain separate domains and are not fabricated.

## Session and Authorization

The Companion keeps only the current Runtime Control Lease ID and Debug Arm ID for its MCP connection. It does not grant either authority:

- Runtime Bearer authentication remains mandatory;
- Runtime scope checks remain authoritative;
- Control Lease conflict/TTL remains authoritative;
- Debug Arm fingerprint/TTL remains authoritative;
- Dedicated Server flags/operator checks remain authoritative.

The default Runtime URL is loopback. A non-loopback URL requires `MCP_COMPANION_ALLOW_NON_LOOPBACK=true`; this is only an explicit opt-in and does not claim to provide TLS or pairing.

## Prompt Injection Isolation

Tool names, descriptions, annotations and Prompt instructions are static source text. Runtime responses are wrapped as:

```text
plane=data
dataPlaneOnly=true
dynamicPolicyApplied=false
transport=runtime_http
```

Chat, books, signs, MOTD, GUI labels and Mod/provider output can appear only inside Tool/Resource data. Tests inject an instruction-like GUI label and verify it never appears in Tool definitions or the static Prompt.

## Error and Protocol Mapping

Runtime HTTP errors become MCP Tool results with `isError=true` and structured:

```text
code
HTTP status
safe message
requestId?
```

Credentials are never included. Missing Runtime, timeout, invalid JSON and response-size failures have Companion-specific typed codes.

Every build reads the native OpenAPI source and verifies exact version `0.0.1-phase8`, required paths/schemas and absence of global `expectedWorldRevision`.

## MCP Compatibility

The official v2 Client negotiated `2025-11-25` in both mock and live conformance. `serveStdio` is the official v2 compatibility entry point and supports legacy plus the 2026-07-28 serving era. The latter is SDK-supported but has not yet been exercised with a separate 2026-era Client in this repository.

This compatibility statement does not freeze the native Minecraft Wire Protocol v1.

## Conformance

### Deterministic mock Runtime

The official v2 MCP Client verifies:

- stdio initialization without stdout corruption;
- 23 Tools, four Resources, two templates and one Prompt;
- Lease state reuse across Tool calls;
- native Pipeline operation get/wait/cancel and MCP-to-native cancellation propagation;
- discriminated Pipeline/selector/condition/Recording schemas;
- current-player command provenance;
- Content-Length preflight and bounded streaming for declared, chunked and unknown-size Runtime responses;
- PNG and Artifact binary Resources;
- Runtime error mapping and credential redaction;
- Prompt Injection isolation;
- loopback-first configuration;
- MCP negotiation and clean process shutdown.

40 sequential Tool calls produced a 16.21 ms p95 against the mock Runtime, below the 250 ms release budget.

### Historical Live Minecraft Runtime Evidence

The earlier Phase 8 Fabric 26.2 Runtime passed the following workflow, but this evidence predates the hardening patch set and is not accepted as the final Release Artifact gate:

```text
MCP initialize/list
get session/capabilities/hooks
acquire Control Lease
open extended standard Mod GUI
discover and activate a dynamic Widget
read Composite PNG as MCP Resource
close GUI
title → test world through a real input Pipeline
client/server Player agreement
world → title through a real input Pipeline
release Control Lease
```

25 sequential live Session Tool calls measured:

```text
p50 = 16.76 ms
p95 = 33.61 ms
budget = 250 ms
```

Final acceptance was re-run on current hardened artifacts across all five Targets, plus OpenGL/Vulkan variants for NeoForge/Fabric 26.2. Historical Phase 6–8 evidence was not used as a substitute.

### Historical Working-Tree Hardening Evidence

The pre-Phase-8.1 working-tree run passed the following checks. These results remain useful regression evidence but are not commit-bound remote certification:

- five Target build, launch, readiness, authenticated session/capability/UI, GAME_ROUTED input, integrated-world Player/Block/Entity observation, server-authoritative Player/Block observation, current-player command, Composite Capture, WS event, Lease release and clean shutdown;
- NeoForge 26.2 OpenGL and Vulkan capture;
- Fabric 26.2 OpenGL and Vulkan capture;
- 12 cancellation scenarios with zero post-cancel input dispatches, including disconnect and Lease expiry;
- EventHub typed filter, fast/stalled consumer, bounded queue gap, resume in ring, expired resume and full resync;
- Player/Block/Entity/Menu/Inventory/Event/Operation typed Wait/Assert conditions;
- Host/Origin, principal identity, body budget, expensive-operation budget, active-operation limit and audit correlation;
- 64-frame Recording using maximum accepted Contact Sheet dimensions, split into three sheets, streamed Artifact download and `CLOSED` lifecycle;
- active Recording during actual client shutdown produced a completed manifest and Bundle without `RejectedExecutionException`.

## Security and Release Gates

- `npm ci` is reproducible from the lockfile.
- `npm audit --audit-level=high` reports zero vulnerabilities at all severities.
- Production Companion code contains no shell/process execution API.
- Runtime paths are restricted to the typed `/v0/` namespace and reject traversal.
- Non-loopback transport is explicit.
- Response sizes are bounded before allocation when Content-Length is known and incrementally while streaming otherwise.
- stdio production code has no `console.log`.
- Tool names are static literals and the reviewed surface count is gated.
- Native Artifact HTTP uses chunked file streaming; Recording has per-frame/state, Session, Contact Sheet and Bundle-source aggregate budgets.
- Runtime requests have token-lifetime principal identity, per-principal/per-connection and expensive-category budgets, bounded active operations, and correlated audit entries.
- EventHub provides typed filters, a bounded ring, bounded client queues, resume, explicit gaps and minimum full resync.
- Five hardened Phase 8 artifacts must build successfully in the current gate run.

## V1 Definition of Done Status

The implementation satisfied the working-tree Phase 8 Hardening suite under ADR-0001's loopback-only V1 Release Profile. Phase 8.1 found that the tracked Evidence was bound to an obsolete SHA and that Pipeline typed-condition parity required additional production/conformance work. Until those repairs are committed, pushed, fetched into a clean detached worktree and revalidated, the permitted status is:

```text
working-tree candidate: PASS
origin/master Release Candidate: FAIL
Phase 9 entry: CLOSED
```

The numbered criteria below remain the V1 acceptance contract; they are not a current remote PASS assertion.

1. five-Target load/conformance: current hardened-artifact world smoke PASS;
2. shared external contract and honest capability matrix: Phase 7;
3–10. title/world, GUI, Vision, input, Container, observation and wait/assert: Phases 0–4;
11. Capture/Recording/Contact Sheet/Artifact and input concurrency: Phases 4–5 plus Vulkan revalidation;
12. provenance/evidence: all Runtime and Companion results;
13–15. Integrated/Peer authority and operation planes: Phases 5–6;
16. HTTP/WS plus MCP autonomous workflow: official MCP mock/client lifecycle PASS and current five-Target native live smoke PASS;
17. Auth/Scope/Lease/Arm/prompt isolation/audit/disconnect: Phases 2, 5, 6 and 8;
18. Hook/Capability self-test: Phase 7;
19. bounded pressure and latency: static/Companion/live EventHub and Recording stress PASS;
20. Ultimate-only features remain explicitly unavailable or deferred.

## Remaining Boundaries

- Wire Protocol v1 is still intentionally unfrozen.
- A dedicated 2026-07-28-era MCP Client gate remains to be added when a suitable host/client fixture is available.
- V1 is loopback-only under ADR-0001. TLS, pairing and LAN proxying remain Ultimate/deferred.
- Current-player command execution is implemented; Fixture/admin and Debug command domains remain separately scoped.
- Ultimate Storage, full World Delta recording, replay, rolling recovery and advanced diagnostics remain Phases 9–10.

Official SDK references: [v2 server package](https://github.com/modelcontextprotocol/typescript-sdk/tree/main/packages/server), [stdio serving](https://github.com/modelcontextprotocol/typescript-sdk/blob/main/docs/serving/stdio.md), [v2 migration and protocol serving](https://github.com/modelcontextprotocol/typescript-sdk/blob/main/docs/migration/support-2026-07-28.md).
