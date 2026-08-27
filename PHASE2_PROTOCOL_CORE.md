# Phase 2 Protocol Core

> Status: complete for all five Runtime Targets (1.21.1/26.1.2 promoted during Phase 6)  
> Date: 2026-08-28  
> Contract status: unstable V0; Wire Protocol v1 is not frozen

## Scope

Phase 2 implements the execution-plan slice for protocol core, owner-thread scheduling and loopback security. It does not port the control Runtime to NeoForge 1.21.1 or NeoForge 26.1.2; those Targets remain buildable honest placeholders until their Target-specific runtime paths are investigated.

Implemented Runtime Targets:

```text
1.20.1-forge
26.2-neoforge
26.2-fabric
```

## Delivered Behavior

- Minimal request correlation and V0 negotiation.
- Optional relative Deadline.
- Per-operation scope enforcement.
- Single-writer Control Lease with bounded TTL.
- Explicit renew, release and emergency release.
- Input cleanup on release, expiry, control WebSocket disconnect and transport close.
- Runtime-owned key/button state diagnostics.
- Operation-local input idempotency.
- Screen/Menu resource preconditions without a global world revision.
- Cancellable asynchronous screen waits.
- Operation capability descriptors.
- Bounded metadata-only audit ring.
- Exact loopback Host/Origin validation.
- Random 256-bit token generation and non-logged token-file handoff.
- Detached Client, Render and Integrated Server thread-affinity evidence.

## Request Layering

Ordinary reads need no Lease, idempotency key or Screen/Menu revision. Control context appears only on operations that declare support for it.

```text
common:
  Authorization
  X-MCP-Request-Id?
  X-MCP-Protocol-Version?
  X-MCP-Deadline-Ms?

input mutation:
  X-MCP-Control-Lease
  X-MCP-Idempotency-Key?
  X-MCP-Expected-Screen-Revision?
  X-MCP-Expected-Menu-Revision?

long operation:
  operationId
  status
  cancellation endpoint
```

## Automated Evidence

The same `Invoke-Phase2ProtocolConformance.ps1` contract is implemented across all five Runtime Targets; the two promoted Targets were exercised through the later Phase 3–6 black-box gates with active worlds.

| Gate | Forge 1.20.1 | NeoForge 26.2 | Fabric 26.2 |
|---|---:|---:|---:|
| Bearer rejection | PASS | PASS | PASS |
| Origin rejection | PASS | PASS | PASS |
| V0 negotiation/request correlation | PASS | PASS | PASS |
| Operation declarations | PASS | PASS | PASS |
| Deadline | PASS | PASS | PASS |
| Screen resource precondition | PASS | PASS | PASS |
| Single-writer Lease | PASS | PASS | PASS |
| Lease expiry input cleanup | PASS | PASS | PASS |
| Control WebSocket disconnect cleanup | PASS | PASS | PASS |
| Idempotency | PASS | PASS | PASS |
| Long-operation cancellation | PASS | PASS | PASS |
| Client owner thread | PASS | PASS | PASS |
| Render owner thread | PASS | PASS | PASS |
| Integrated Server owner thread | PASS | PASS | PASS |
| Audit correlation/sequence | PASS | PASS | PASS |

`Invoke-Phase2ScopeConformance.ps1` additionally passed against Fabric 26.2 launched with `MCP_RUNTIME_SCOPES=read`, proving that UI, Capture, Control and Diagnostics operations return typed `SCOPE_DENIED` while ordinary read access remains available.

## Commands

```powershell
.\gradlew.bat :protocol-schema:openApiValidate :protocol-schema:generateProtocol --no-daemon

.\conformance\phase2\Invoke-Phase2ProtocolConformance.ps1 `
  -BaseUri http://127.0.0.1:<target-port> `
  -TokenFile <game-directory>\minecraft-protocol\token `
  -ExpectedTarget <target> `
  -RequireIntegratedServer

$env:MCP_RUNTIME_SCOPES = 'read'
.\conformance\phase2\Invoke-Phase2ScopeConformance.ps1 `
  -BaseUri http://127.0.0.1:25583 `
  -TokenFile runs\26.2-fabric\client\minecraft-protocol\token
```

## Thread Boundary Result

HTTP/WebSocket workers parse bounded detached values only. Target runtimes schedule Client/Render work before touching active Minecraft state. The Server diagnostic resolves the integrated server on the Client thread, schedules a detached evidence snapshot on the Server thread and returns only JSON. No `Screen`, `Level`, `Entity`, `Menu`, `MinecraftServer` or render object is returned to the transport worker.

## Honest Capability Boundaries

- Forge 1.20.1 Render Facts remain unavailable and return `coverage=unsupported` / `status=capability_unavailable`.
- NeoForge 1.21.1 and NeoForge 26.1.2 do not claim the Phase 2 Runtime.
- No LAN listener, TLS/pairing, rate limiter, persistent principal identity or tamper-evident audit is implemented.
- Idempotency, operations and audit are bounded in-memory probe state.
- Debug Arm, Fixture, DEBUG_PRIVILEGED, Server Peer and persistent storage remain later phases.

## Exit Decision

Phase 2 exit conditions are satisfied for all five Runtime Targets:

- owner-thread scheduling is executable and produces detached results;
- authentication, permission rejection, Deadline, cancellation, control-channel disconnect and input cleanup have automated evidence;
- the OpenAPI V0 draft and generated Java/TypeScript models describe the implemented protocol-core surface;
- no Wire Protocol v1 freeze has occurred.

The next execution-plan phase is Phase 3: V1 UI, Render Facts and input macro/pipeline work. It should build on these operation declarations and Lease semantics rather than bypassing them.
