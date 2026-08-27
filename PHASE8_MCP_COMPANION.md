# Phase 8 MCP Companion and V1 Release Hardening

> Status: implemented and verified  
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

The Companion exposes 19 statically declared Tools:

```text
minecraft_get_session
minecraft_get_capabilities
minecraft_get_ui
minecraft_get_state
minecraft_query_world
minecraft_control
minecraft_interact_ui
minecraft_run_input_pipeline
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

No `minecraft_execute_command` Tool is advertised because the current native V1 Runtime does not yet expose a typed command endpoint. The Companion does not fabricate an unavailable capability.

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
- 19 Tools, four Resources, two templates and one Prompt;
- Lease state reuse across Tool calls;
- Pipeline operation polling;
- PNG and Artifact binary Resources;
- Runtime error mapping and credential redaction;
- Prompt Injection isolation;
- loopback-first configuration;
- MCP negotiation and clean process shutdown.

40 sequential Tool calls produced a 16.21 ms p95 against the mock Runtime, below the 250 ms release budget.

### Live Minecraft Runtime

The final Phase 8 Fabric 26.2 Runtime passed:

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

The Companion is Target-independent; five-Target native contract/build/runtime evidence remains supplied by Phases 6–7.

## Security and Release Gates

- `npm ci` is reproducible from the lockfile.
- `npm audit --audit-level=high` reports zero vulnerabilities at all severities.
- Production Companion code contains no shell/process execution API.
- Runtime paths are restricted to the typed `/v0/` namespace and reject traversal.
- Non-loopback transport is explicit.
- Response sizes are bounded separately for JSON and Artifacts.
- stdio production code has no `console.log`.
- Tool names are static literals and the reviewed surface count is gated.
- Five Phase 8 Minecraft artifacts and 72 Java/72 TypeScript protocol models build successfully.

## V1 Definition of Done

The 20 V1 criteria in §27.2 are satisfied by cumulative evidence:

1. five-Target load/conformance: Phases 6–7;
2. shared external contract and honest capability matrix: Phase 7;
3–10. title/world, GUI, Vision, input, Container, observation and wait/assert: Phases 0–4;
11. Capture/Recording/Contact Sheet/Artifact and input concurrency: Phases 4–5 plus Vulkan revalidation;
12. provenance/evidence: all Runtime and Companion results;
13–15. Integrated/Peer authority and operation planes: Phases 5–6;
16. HTTP/WS plus MCP autonomous workflow: Phase 8 live conformance;
17. Auth/Scope/Lease/Arm/prompt isolation/audit/disconnect: Phases 2, 5, 6 and 8;
18. Hook/Capability self-test: Phase 7;
19. bounded pressure and latency: Recording backpressure plus Phase 8 performance budgets;
20. Ultimate-only features remain explicitly unavailable or deferred.

## Remaining Boundaries

- Wire Protocol v1 is still intentionally unfrozen.
- A dedicated 2026-07-28-era MCP Client gate remains to be added when a suitable host/client fixture is available.
- TLS, pairing and LAN proxying are not provided by merely enabling a non-loopback URL.
- Command execution is not advertised until the native Runtime has a typed command capability.
- Ultimate Storage, full World Delta recording, replay, rolling recovery and advanced diagnostics remain Phases 9–10.

Official SDK references: [v2 server package](https://github.com/modelcontextprotocol/typescript-sdk/tree/main/packages/server), [stdio serving](https://github.com/modelcontextprotocol/typescript-sdk/blob/main/docs/serving/stdio.md), [v2 migration and protocol serving](https://github.com/modelcontextprotocol/typescript-sdk/blob/main/docs/migration/support-2026-07-28.md).
