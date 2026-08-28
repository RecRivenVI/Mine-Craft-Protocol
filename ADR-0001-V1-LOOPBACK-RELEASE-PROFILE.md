# ADR-0001: V1 Loopback-Only Release Profile

- Status: Accepted
- Date: 2026-08-28
- Scope: V1 release security profile only
- Ultimate Scope impact: none

## Context

The execution plan originally grouped the loopback and explicitly enabled LAN security baselines inside V1. The implemented Runtime, however, binds only to `127.0.0.1` and rejects non-loopback `Host` and `Origin` values. The Companion option that permits a non-loopback Runtime URL only allows the Companion to connect to an externally hosted Runtime; it is not a TLS terminator, pairing service, IP allowlist, or safe LAN exposure mechanism.

Treating that state as a completed LAN baseline would make the V1 contract and the release evidence disagree. Adding a production LAN listener during release hardening would also require a separately threat-modeled identity, pairing, certificate lifecycle, revocation, discovery, and network abuse surface.

## Decision

The V1 release profile is formally narrowed to **loopback-only exposure**:

- Runtime bind address is `127.0.0.1`;
- Bearer authentication, runtime-scoped principal identity, scopes, Host/Origin validation, bounded request bodies, per-principal/per-connection rate budgets, expensive-operation budgets, Control Lease, Debug Arm, audit correlation, Prompt Injection isolation, disconnect cleanup and operation limits are V1 requirements;
- LAN listener/proxy exposure is unavailable in V1 and must be reported as such;
- setting `MCP_COMPANION_ALLOW_NON_LOOPBACK=true` does not change the Runtime security profile and carries no TLS/pairing claim.

LAN remains part of Ultimate Scope. It may be promoted only after a dedicated ADR and conformance suite cover TLS, pairing, persistent/revocable principals, IP policy, discovery, abuse budgets, audit persistence and upgrade behavior.

## Consequences

- This is an explicit product-scope correction, not a silent deletion of the original LAN goal.
- V1 can be evaluated against controls that are actually implemented and testable.
- A release must not advertise LAN readiness.
- Wire Protocol v1 remains unfrozen.
