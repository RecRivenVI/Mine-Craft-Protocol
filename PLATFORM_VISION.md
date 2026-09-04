# Mine-Craft-Protocol Core Product Vision

> Document type: committed product contract
> Authority: Core Product Vision
> Status: COMMITTED
> Date: 2026-08-29
> Current implementation baseline: Runtime V1 attested; Runtime Phase 9C, Phase 9D-0, Phase 9D-1 and Phase 9D-2 safety foundation complete; Persistent Write Entry Review (third review) CLOSED
> Wire Protocol v1: NOT FROZEN
> Optional extensions: governed separately by `PLATFORM_EXTENSION_GOALS.md`

## 1. Committed Product Definition

Mine-Craft-Protocol is first and foremost an:

> **Agent-native Minecraft Autonomous Testing Platform**

This is the only committed product goal. The project must make a Coding Agent capable of independently driving a real Minecraft Java Runtime to prepare a test, execute it, observe the result, wait and assert, collect evidence, diagnose failure and run regression.

The broader umbrella positioning may remain:

> **Agent-native Minecraft Development, Debugging and Testing Platform**

That umbrella does not make every possible development, gameplay or graphics extension part of the delivery contract.

```text
Mine-Craft-Protocol Platform
│
├── COMMITTED CORE
│   └── Agent-native Minecraft Autonomous Testing Platform
│
└── OPTIONAL EXTENSION PORTFOLIO
    └── governed only by PLATFORM_EXTENSION_GOALS.md
```

The repository name, Java package, Mod ID and artifact IDs remain unchanged.

## 2. Meaning of Autonomous Testing

In the Core Product, `Autonomous` means:

```text
Agent receives a test goal
  -> prepares the environment
  -> executes the test
  -> observes the result
  -> waits and asserts
  -> collects evidence
  -> diagnoses failure
  -> reports and regresses
```

It does not mean:

```text
Agent survives or plays Minecraft indefinitely
Agent invents its own long-term survival strategy
Agent replaces a full source-intelligence platform
Agent provides complete deterministic GPU/render forensics
```

Therefore:

> **Autonomous Testing is not Autonomous Gameplay.**

Autonomous Gameplay is a possible optional extension, not a prerequisite for the Core Product.

## 3. Core Product Capabilities

The committed Core includes the existing Runtime and Testing roadmap:

- real Minecraft Runtime control;
- GAME_ROUTED keyboard, mouse, GUI and player actuation;
- Interaction Tree and semantic UI targeting;
- Render Facts where supported;
- Screenshot and multimodal fallback for unknown GUI/visual cases;
- Player, Entity, Block, Block Entity, Chunk, World and Menu observation;
- explicit Client-known and Server-authoritative perspectives;
- Integrated Server and Dedicated Server Peer boundaries;
- runtime wait and assert;
- cancellable Pipelines and native Operation lifecycle;
- Control Lease and input cleanup;
- Fixture and strongly typed DEBUG_PRIVILEGED operations;
- Deep Observation and Provider contracts;
- capture, Recording, Artifact and Timeline;
- provenance, evidence contamination and regression reporting;
- loopback security, scopes, audit and prompt-injection isolation;
- native HTTP/WebSocket protocol and independent MCP Companion;
- explicit five-Target support;
- automated test orchestration and regression testing.

The existing Runtime Phase 9 and Phase 10 scopes remain Core commitments. Their current order, capability boundaries, conformance and independent review gates are unchanged by the optional-extension portfolio.

## 4. Core Testing Method

The Core continues to follow:

> **White-box observation, black-box operation, visual fallback and isolated fixtures.**

The three Runtime authority planes remain:

```text
PLAYTEST
FIXTURE
DEBUG_PRIVILEGED
```

They do not collapse into one another.

- PLAYTEST proves what a real player path can do.
- FIXTURE establishes controlled test preconditions.
- DEBUG_PRIVILEGED constructs and inspects diagnostic or boundary state.

Debug and Fixture evidence never masquerade as gameplay acceptance.

## 5. Input and GUI Fidelity

The gameplay-valid path remains:

```text
semantic target or visual coordinate
  -> virtual Minecraft input
  -> Screen / Menu / KeyMapping
  -> normal packet where applicable
  -> server validation
```

The GUI fallback order remains:

```text
Interaction Tree
  -> Render Facts
  -> Screenshot / Multimodal Vision
```

Optional deterministic graphics work must not remove or weaken the Core Vision fallback. Unknown Mod interfaces and visually defined test cases may continue to use multimodal evidence when semantic evidence is unavailable.

## 6. Observation and Debug Contract

Core observation remains typed, perspective-aware and side-effect honest:

```text
perspective
acquisition
completeness
readEffects
resource revision / snapshot identity
```

Core Debug remains Minecraft-domain typed. Internal Accessors, Invokers, Loader internals, Mixins and direct mutation are allowed when required and verified; public arbitrary field/method/object-graph Reflection is not.

LIVE and PERSISTED remain separate planes. Ordinary `world.*` queries never silently read storage, and Persistent Storage work remains governed by the existing Runtime roadmap.

## 7. Evidence Contract

Every Core result must preserve the distinction among:

```text
gameplay evidence
fixture evidence
diagnostic evidence
invalid_for_acceptance
```

The Core can combine visible and internal evidence, but must state their origin. Minecraft text remains untrusted data-plane content and cannot change Tool descriptions, permissions, Runtime policy or Debug authorization.

Human aesthetic approval is not implied by a passing machine assertion. Screenshot/video artifacts remain useful supporting evidence even if an optional deterministic graphics extension is never implemented.

## 8. Explicit Targets

The committed Core continues to support:

```text
1.20.1-forge
1.21.1-neoforge
26.1.2-neoforge
26.2-neoforge
26.2-fabric
```

Targets remain explicit siblings. Public behavior, protocol semantics, evidence and Conformance are shared; internal Minecraft/Loader access paths may differ.

## 9. Core Deployment Boundary

```text
Coding Agent / Test Runner
        │
        ├── MCP Companion
        └── Native HTTP / WebSocket
                     │
                     ▼
          Minecraft Agent Control Runtime
                     │
          ┌──────────┴──────────┐
          ▼                     ▼
  Integrated Server     Dedicated Server Peer
```

The Core Runtime does not become a general shell, filesystem browser, process controller, ClassLoader service or Reflection RPC.

Optional external services may exist in the future, but their authority, security model and release lifecycle remain outside Core Runtime gates unless the user explicitly promotes them.

## 10. Core Status Vocabulary

Runtime phases and capabilities keep their existing status vocabulary and gates. The optional-extension portfolio has its own lifecycle vocabulary; it does not replace or reinterpret Runtime Phase status.

Current Core status:

| Capability | Status | Core stage |
|---|---|---|
| Runtime Control and Testing | CURRENT | V1 / continuing regression |
| MCP Runtime Companion | CURRENT | V1 / continuing regression |
| Deep Runtime Observation | CURRENT | Phase 9B complete |
| Typed Deep Debug | CURRENT | Phase 9C complete |
| Persistent Storage Plane | SAFETY FOUNDATION | Phase 9D-0 read + Phase 9D-1/9D-2 safety foundation complete; third Persistent Write Entry Review CLOSED on runtime artifact evidence; no write route |
| Recording V2 / reconstructable state | PARTIAL | Phase 9E-9F planned |
| Runtime advanced diagnostics and recovery | PLANNED | Phase 10 |

No optional extension status changes any row in this table.

## 11. First Developer Preview Contract

> **The first Developer Preview requires only the Core Autonomous Testing goal.**

The first Developer Preview may ship when the existing Core Developer Preview Gate is satisfied. It is not blocked by any of the following:

```text
Source Intelligence
Decompiler or Managed Source Store
Symbol/AST/Call Graph indexes
Groovy or JVM REPL
Human Web Inspector
Autonomous navigation, survival or combat
Geometry or Render Graph capture
Vulkan/render forensics
Radiance deterministic acceptance
```

Extension requirements for the first Developer Preview are:

```text
NONE
```

## 12. Core 1.0 and Ultimate Runtime

Core 1.0 is governed by Core capability and quality decisions only. E1, E2 or E3 does not automatically become a Core 1.0 requirement.

The existing Runtime Ultimate scope remains committed because it completes the autonomous-testing platform itself: full Runtime observation/debug/storage/recording/diff/recovery capabilities across the supported Targets.

Optional Source Intelligence, Autonomous Gameplay and deterministic Render Forensics are not part of Runtime Ultimate Definition of Done. They remain outside Core unless the user makes an explicit Product Governance Decision.

## 13. Optional Extensions

The Platform may host ambitious optional extensions without weakening or delaying the Core. Their single authority is:

> `PLATFORM_EXTENSION_GOALS.md`

The current optional portfolio is summarized only as:

```text
E1 Development Intelligence & Exploratory Debug
E2 Autonomous Gameplay
E3 Deterministic Graphics Acceptance & Render Forensics
```

This file deliberately does not duplicate their roadmaps, benchmarks or implementation sketches.

Extensions may be researched, implemented partially, deferred indefinitely or cancelled. Core can pass and release in every such state.

## 14. Promotion Rule

An optional extension can become Core only through an explicit user Product Governance Decision, for example:

```text
Navigation is now mandatory for Core 1.0.
```

Implementation progress, architectural convenience or partial integration does not automatically promote an extension into Core DoD, Runtime phases or Developer Preview gates.

## 15. Release Governance

These states are valid:

```text
Core Runtime 1.0
E1 EXPERIMENTAL
E2 NOT STARTED
E3 DEFERRED
```

The Core makes Mine-Craft-Protocol useful and releasable. Extensions make it broader and more ambitious.

> **Extensions are opportunities, not release obligations.**

## 16. Current Governance Decision

```text
Committed Core:
  Agent-native Minecraft Autonomous Testing Platform

First Developer Preview:
  Core gate only; no Extension blockers

Runtime Phase 9:
  unchanged

Runtime Phase 10:
  unchanged

Phase 9C:
  complete under its existing Gate

Phase 9D-0:
  complete; followed by Phase 9D-1 safety foundation

Phase 9D-1:
  complete; followed by Phase 9D-2 safety hardening

Phase 9D-2:
  complete; third Persistent Write Entry Review CLOSED on five-target runtime artifact evidence; no real save mutation or write route

E1 / E2 / E3:
  optional; not started by this governance task

Wire Protocol v1:
  NOT FROZEN
```
