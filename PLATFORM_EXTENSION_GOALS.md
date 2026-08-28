# Mine-Craft-Protocol Optional Extension Portfolio

> Document type: optional product-extension portfolio
> Authority: Optional Extension Goals
> Status: ASPIRATIONAL / NON-BLOCKING
> Date: 2026-08-29
> Core authority: `PLATFORM_VISION.md`
> Current implementation claim: NONE

## 1. Purpose

This document records optional product extensions beyond the committed autonomous-testing Core.

> **Every capability in this document is an independent Optional Extension.**

They are not required for Core Product completion, the first Developer Preview, Core 1.0, or completion of the existing Runtime Phase 9/10 roadmap. They do not extend a Runtime exit gate unless a future explicit Product Governance Decision promotes them.

Each extension may be developed partially, deferred indefinitely or cancelled. Failure to complete any extension does not make the Core Autonomous Testing Platform incomplete.

## 2. Governance Status

The extension-only status model is:

```text
IDEA
RESEARCH
PLANNED
EXPERIMENTAL
ACTIVE
PARTIAL
STABLE
DEFERRED
CANCELLED
```

It does not replace Runtime Phase or capability status.

| ID | Extension | Status | Mandatory for Core | Mandatory for first Developer Preview |
|---|---|---|---|---|
| E1 | Development Intelligence & Exploratory Debug | PLANNED | No | No |
| E2 | Autonomous Gameplay | IDEA / RESEARCH | No | No |
| E3 | Deterministic Graphics Acceptance & Render Forensics | IDEA / RESEARCH | No | No |

No implementation is started by adopting this document.

## 3. Relationship to Core

```text
Mine-Craft-Protocol Platform
│
├── COMMITTED CORE
│   └── Agent-native Minecraft Autonomous Testing Platform
│
└── OPTIONAL EXTENSION PORTFOLIO
    ├── E1 Development Intelligence & Exploratory Debug
    ├── E2 Autonomous Gameplay
    └── E3 Deterministic Graphics Acceptance & Render Forensics
```

E1, E2 and E3 are independently planned, gated, releasable, deferrable and cancellable. They do not form an `E1 -> E2 -> E3` mandatory sequence.

Allowed enhancement relationships do not become dependencies:

- E1 may enhance E2 or E3.
- E2 may provide long-play Core scenarios.
- E3 may strengthen optional visual assertions.
- Core does not require E1, E2 or E3.
- E2 and E3 do not require E1.

## 4. Release Non-Blocking Rule

The first Developer Preview requires only the existing Core Developer Preview Gate. Extension requirements are:

```text
NONE
```

The following cannot become implicit blockers:

```text
Source Intelligence or decompiler missing
Managed Source Store / Symbol / AST / Call Graph missing
Groovy/JVM REPL or Web Inspector missing
navigation / combat / survival missing
geometry / Render Graph / Object ID capture missing
Vulkan, ray-tracing or Radiance forensics missing
```

Core 1.0 is also independent from E1/E2/E3 unless the user explicitly redefines its DoD.

## 5. E1 — Development Intelligence & Exploratory Debug

### 5.1 Objective and Lineage

E1 explores a Coding-Agent development loop that can understand Minecraft, Loader, project and third-party Mod source, correlate source with Runtime evidence, and perform separately armed exploratory JVM investigation.

Its capability space inherits ideas from DebugBridge-style Runtime inspection/scripting/human UI and mcdev-mcp-style managed source, symbol, hierarchy, reference and call-graph services. This is a design lineage, not an implementation or parity claim.

### 5.2 Source Intelligence

Candidate capabilities include:

```text
Artifact Resolver
Managed Source Store
Minecraft and Loader artifacts
Mappings
Decompiler adapters and source retrieval
symbol / class / method / field search
package exploration
references / callers / callees
class hierarchy
AST index
call graph
structured version diff
```

Generated or decompiled source would be a reproducible development cache, not project source or a redistribution entitlement.

### 5.3 Mapping Intelligence

Candidate mapping domains include Official/Mojmap, runtime names, Fabric Intermediary, Yarn where relevant, descriptors and cross-version symbol identity.

Possible semantic operations include `mapping.resolve`, `mapping.convert` and `mapping.describe`. These names do not freeze a protocol.

### 5.4 Mod and Injection Intelligence

Candidate capabilities:

```text
Mod JAR metadata, entrypoints and dependencies
embedded libraries and Loader compatibility
third-party Mod decompilation and source search
Mixin config / target / injection-point / descriptor validation
runtime Mixin application correlation
Accessor / Invoker validation
Access Widener validation
Access Transformer validation
```

### 5.5 Runtime-to-Source Correlation

```text
runtime event -> symbol -> mapping -> source
source symbol -> runtime presence -> loaded state -> instrumentation -> evidence
```

Runtime facts and source/decompiler facts remain distinct and provenance-labelled.

### 5.6 Safe Probe and Exploratory JVM

Safe Probe may research restricted, bounded, read-only projection over explicitly exposed roots. It must not become hidden Reflection, filesystem, network or process access and must remain separate from Core typed Observe/Debug.

Groovy or another full-power JVM scripting mechanism may be researched only as:

```text
NOT SANDBOXED
disabled by default
loopback-first
explicit local enable
separate Exploratory Arm
invalid_for_acceptance
```

An ordinary Runtime token, Control Lease or Debug Arm cannot authorize it.

### 5.7 Human Inspector

Optional UI concepts include Runtime/Provider/Debug inspectors, Source Browser, Symbol Search, Call Graph, Exploratory REPL and Evidence viewer. A UI would consume the same typed services and authority rules; it would not define Runtime semantics.

### 5.8 Reclassified DI / PI Roadmap

Historical `DI-0 ... DI-10` design remains an E1 internal roadmap candidate. PI stages that exist only for source, exploratory or Human Inspector integration are also E1 work, not mandatory Platform Ultimate DoD.

Conceptual, unfrozen namespace:

```text
E1.0 architecture / supply chain / threat study
E1.1 artifacts, mappings and Source Store
E1.2 source/decompiler adapters
E1.3 symbol, AST and search
E1.4 references, hierarchy and call graph
E1.5 Mod intelligence
E1.6 Mixin/AW/AT intelligence
E1.7 Runtime/source correlation
E1.8 Safe Probe and separately armed exploratory JVM
E1.9 Human Inspector
E1.10 independent hardening
```

`E1 = CANCELLED` remains compatible with `Core = PASS / RELEASED`.

## 6. E2 — Autonomous Gameplay

### 6.1 Objective

E2 explores how an Agent can use Core real-player control and semantic observation to pursue long-lived Minecraft goals.

> **Autonomous Testing is not Autonomous Gameplay.**

Core follows a supplied test goal. E2 chooses long-term gameplay goals, navigates, gathers resources, survives, fights and progresses.

### 6.2 Semantic-First Vanilla Goal

For Vanilla Minecraft, the aspirational perception order is:

```text
Semantic World State
  -> Semantic UI / Interaction Tree
  -> Render Facts
  -> Multimodal Vision fallback
```

Vanilla gameplay should normally avoid vision when semantic collision/world/UI facts are sufficient. This does not change Core vision fallback for unknown Mod GUI or visual test cases.

### 6.3 Knowledge Perspective

```text
PLAYER_KNOWN
CLIENT_KNOWN
SERVER_AUTHORITATIVE
DEBUG_OMNISCIENT
```

Normal autonomous gameplay uses PLAYER_KNOWN or CLIENT_KNOWN. Existing server/internal/persisted access must not silently make a survival Agent omniscient. Debug knowledge changes evidence and benchmark eligibility.

### 6.4 Mechanical Navigation

First candidates:

```text
Navigation Planner
Movement Action Graph
Local Movement Controller
Navigation Supervisor
```

`navigate_to(...)` may be a high-level skill, but actuation remains real GAME_ROUTED W/A/S/D, jump, sprint, sneak and mouse look. Teleport/direct position mutation cannot prove navigation.

Navigation should prefer BlockState, VoxelShape/collision shape, FluidState, Player AABB and loaded Chunk state rather than vision-based road guessing.

### 6.5 Gameplay Skill Runtime

Candidate Agent skills:

```text
navigate_to / look_at / approach / follow / avoid / flee
mine_block / mine_vein / place_block / pickup_item
eat / craft / smelt
open_container / transfer_items / equip
fight / build / sleep
```

They belong above Core and must not become gameplay-bypassing direct business mutation endpoints.

### 6.6 Fast Local Controller and Memory

Walking, jump timing, sprint-jump, combat aim/timing, shield timing and hazard avoidance may use deterministic local controllers that emit GAME_ROUTED input instead of requiring an LLM decision every tick.

E2 memory may store home, mine entrances, known resources, villages, danger zones, task state and resource goals. It is E2 gameplay state, not Core Runtime truth.

### 6.7 Vanilla Survival Benchmark

Optional benchmark rules:

```text
fresh world
no commands
no fixture
no debug
no multimodal vision
semantic observation only
GAME_ROUTED only
```

Candidate progression: survive one night, obtain food, craft stone tools, build shelter, obtain iron, craft iron pickaxe/shield/bed, enter the Nether and reach the End.

This benchmark measures E2, never Core Release readiness.

### 6.8 Modded Gameplay and Boundary

E2 may later learn Mod UI, machines, recipes and technology chains. E1 may enhance it, but Vanilla E2 must remain independent.

```text
Gameplay Agent
  -> Goal Planner
  -> Gameplay Skill Runtime
  -> Mine-Craft-Protocol Core
  -> GAME_ROUTED
  -> Minecraft
```

Core provides eyes, hands, feet and world state. E2 provides skills, navigation, strategy and memory.

Conceptual roadmap `E2.0 ... E2.7` may cover navigation spikes, skills, survival, combat, memory, Vanilla benchmark and optional Modded gameplay. It is unfrozen and may remain PARTIAL or DEFERRED indefinitely.

## 7. E3 — Deterministic Graphics Acceptance & Render Forensics

### 7.1 Objective

E3 explores structured and numerical validation of Minecraft Mod rendering without relying primarily on a multimodal model saying an image “looks correct.”

```text
Semantic Render State
+ Geometry
+ Render Graph
+ Resources
+ Visibility
+ Numerical Output
-> deterministic or statistical assertions
```

Screenshot and video remain secondary human artifacts. E3 cannot claim human aesthetic approval.

### 7.2 Render Observation Layers

Geometry alone cannot prove resource binding, compositing, visibility, temporal reconstruction or lighting. The research model is:

```text
R0  Minecraft Semantic Runtime
R1  Render Semantic Context
R2  Geometry Submission
R3  Render Graph / Pipeline
R4  Render Resources / Targets
R5  Raster / Visibility Evidence
R6  Ray-Tracing Scene
R7  Lighting / Material / Path State
R8  Temporal / Denoising / Reconstruction
R9  Final Numerical Image Evidence
```

This is an optional extension architecture, not a current implementation.

### 7.3 R1–R5 Raster and Pipeline Forensics

Candidate R1 context: frame, Entity UUID, Block position, renderer, feature layer, model part, semantic role, Mod owner, channel, surface and effect.

Candidate R2 geometry: vertices, indices, primitives, position, normal, UV, color, light, overlay, transform, semantic provenance and temporal trace.

Candidate R3 graph: pass, pipeline, draw/dispatch, draw order, blend, depth, cull, shader/material and producer/consumer.

Candidate R4 resources: RenderTarget, textures, depth attachments, buffers, identity/generation and input/output binding. Vulkan detail may include VkImage/VkBuffer layout, stage, access and descriptor binding while keeping public tools semantic rather than raw-handle RPC.

Candidate R5 visibility: Depth, Object ID, Layer ID, Coverage, Material ID and selected numerical framebuffer readback for clipping, occlusion, transparency and layer attachment.

### 7.4 R6–R9 Ray, Temporal and Numerical Forensics

Candidate R6 scene facts: BLAS/TLAS, instances, transforms, geometry/material IDs, SBT relationships and ray masks.

Candidate bounded R7 diagnostics: selected rays, intersection candidates, any-hit/closest-hit decisions, material decode, normal, roughness, emission, direct/indirect light and reservoir diagnostics. Recording every ray is not an acceptable default.

Candidate R8 facts: motion vectors, jitter, history/validity, temporal reuse, denoising, DLSS/DLSS RR, FSR, XeSS, upscaling and resource transitions.

Candidate R9 assertions: NaN/Inf, luminance, variance, histogram, coverage, edge continuity, temporal error and pixel/region statistics.

### 7.5 Deterministic and Statistical Oracles

`Deterministic Render Test Mode` may fix RNG seed, world time, weather, camera, exposure, jitter, animation tick and known scene without changing the tested algorithm's meaning.

Stochastic path tracing, ReSTIR and volumetrics may instead use statistical mean, variance, energy, convergence or distribution oracles. Exact golden pixels are not universally valid.

### 7.6 Performance and GPU Failure Forensics

Candidate metrics include CPU/GPU frame time, VRAM/RAM, draw/dispatch timing, BLAS/TLAS build, path trace, denoise, DLSS, shader compilation and pipeline recreation.

Candidate failure evidence includes Vulkan validation, device fault, checkpoints, resource/barrier history and the last successful pass. Native/GPU diagnostics require a dedicated extension threat model before activation.

## 8. E3 Driving Benchmarks

### 8.1 Mo' Bends — Dynamic Entity Geometry Benchmark

Reference: <https://github.com/RecRivenVI/MoBends>

Drives semantic body parts, animated/temporal geometry, armor and held items, cape/elytra/trails, child scaling and feature layers. Candidate oracles include joint relationships, limb phase/angle, mesh continuity, attachment transforms, mirror symmetry, trails, duplicates/degenerates, duration and return-to-idle.

### 8.2 GLASS — Render Graph / Multi-Camera / Compositing Benchmark

Reference: <https://github.com/RecRivenVI/GeneralLaymansAestheticSpyingScreen>

Drives remote camera matrices, offscreen RenderTargets, projection surfaces, clipping/transparency, channel binding, particles/weather/sky, hot switching, render-state restoration and multi-camera rendering.

GLASS proves geometry is insufficient: correct projection geometry with the wrong channel texture or pipeline is still wrong.

### 8.3 Radiance — Full-Stack Render Forensics Benchmark

Reference: <https://github.com/RecRivenVI/Radiance>

Drives Java/native scene bridging, C++/Vulkan rendering, geometry/materials, BLAS/TLAS, ray tracing and hit behavior, PBR/lighting/volumetrics, ReSTIR, temporal history, DLSS/RR, synchronization, numerical framebuffers and device faults.

Radiance is an **ultimate benchmark**, meaning an optional research ceiling rather than a product-delivery promise.

If a future E3 can independently validate Mo' Bends, GLASS and Radiance, it would cover a large part of the Minecraft Mod graphics problem space. Core completion does not require that result.

## 9. Generic + Provider Model

E3 should combine generic external Minecraft render observation with renderer-specific typed Provider diagnostics. A future Radiance Provider could expose AS, ReSTIR, DLSS and resource-state facts, but a tested renderer cannot establish correctness by declaring “everything is correct.” Generic and renderer-native evidence should cross-check where feasible.

## 10. Security and Evidence Boundaries

### E1

- Source analysis is diagnostic evidence, not gameplay evidence.
- Decompiled/source inference is not Runtime truth.
- Exploratory JVM output is always `invalid_for_acceptance`.
- Unsafe JVM work requires a separate threat model and authority plane.

### E2

- GAME_ROUTED plus PLAYER_KNOWN/CLIENT_KNOWN may produce PLAYTEST gameplay evidence.
- Commands, Fixture, Debug or omniscience must be reported and may invalidate benchmark eligibility.

### E3

- Render observation is white-box render evidence.
- Deterministic/numerical assertions may prove defined rendering properties.
- Render evidence cannot prove human aesthetic approval.
- Native/GPU hooks require separate compatibility and crash-safety review.

Documenting an extension does not expand the Core attack surface.

## 11. Independent Roadmap Namespace

Extensions use conceptual `E1.*`, `E2.*` and `E3.*` namespaces. Do not append them to Runtime phases as `Phase 9H Navigation`, `Phase 9I Geometry` or `Phase 10J Source Intelligence`.

Extension roadmaps remain unfrozen until their own research/spike tasks establish evidence.

## 12. Non-Commitment Clause

All portfolio capabilities are:

```text
Optional
Aspirational
Subject to feasibility
May be partial
May be deferred
May be cancelled
```

High ambition does not create a release obligation. `Ultimate benchmark` describes a capability ceiling, not a promise to ship.

> **The Core makes Mine-Craft-Protocol useful and releasable. Extensions make it broader and more ambitious. Extensions are opportunities, not release obligations.**

## 13. Promotion, Demotion and Cancellation

An extension becomes Core only after an explicit user Product Governance Decision, for example:

```text
Navigation is now mandatory for Core 1.0.
```

Partial implementation, shared code or integration cannot promote it automatically. Only a separately scoped governance task may then revise Core DoD and Runtime gates.

An extension may move to DEFERRED or CANCELLED without deleting historical research. Record decision, reason and date.

## 14. Current Portfolio Decision

```text
E1 Development Intelligence & Exploratory Debug:
  OPTIONAL / PLANNED / NOT STARTED

E2 Autonomous Gameplay:
  OPTIONAL / IDEA-RESEARCH / NOT STARTED

E3 Deterministic Graphics Acceptance & Render Forensics:
  OPTIONAL / IDEA-RESEARCH / NOT STARTED

First Developer Preview extension requirements:
  NONE

Core completion dependency on E1/E2/E3:
  NONE
```
