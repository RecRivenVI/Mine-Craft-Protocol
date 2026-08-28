# Mine-Craft-Protocol Platform Vision

> Document type: engineering product charter
> Status: adopted long-term boundary; implementation remains governed by the existing Runtime gates
> Date: 2026-08-29
> Current implementation baseline: Runtime V1 attested; Runtime Phase 9B complete; Phase 9C not started
> Wire Protocol v1: NOT FROZEN

## 1. Product Definition

The long-term product is the:

> **Agent-native Minecraft Development, Debugging and Testing Platform**

The existing **Minecraft Agent Control Runtime** remains the Platform's in-game Runtime Control, Testing and Observation subsystem. It is not deprecated, renamed away or replaced. The Platform extends the product boundary around that proven Runtime so a Coding Agent can eventually perform one evidence-backed development loop:

```text
understand source and Mod
  -> modify project
  -> build and launch
  -> control real GUI/player behavior
  -> observe and debug Runtime state
  -> investigate unknown internals
  -> correlate failure to source symbols
  -> rebuild and regress
  -> produce trustworthy evidence
```

The repository name, Java package, Mod ID and artifact IDs are unchanged. Product naming is a separate future decision.

## 2. Governing Philosophy

The Platform adopts these rules:

> **Typed path is the trusted testing path. Exploratory path is the investigation path.**

> **Runtime facts and source facts are correlated, not conflated.**

> **Generated/decompiled sources are managed development artifacts, not project source.**

> **The Platform may be powerful; evidence must remain honest.**

Existing Runtime rules remain binding:

- Capability/Fidelity-first; invasive internals are allowed when required and verified.
- PLAYTEST, FIXTURE and DEBUG_PRIVILEGED retain distinct authority and evidence.
- GAME_ROUTED real input is the gameplay acceptance path.
- Interaction Tree -> Render Facts -> Screenshot/Vision remains the GUI fallback chain.
- LIVE and PERSISTED data never mix implicitly.
- Public Runtime and Debug APIs remain strongly typed Minecraft-domain operations.
- Resource revisions, Control Lease, Debug Arm, scopes, provenance and evidence stay authoritative.
- Target capability honesty is more important than superficial parity.
- Wire Protocol v1 remains unfrozen.

Exploratory investigation is an additional fourth authority class, not a relaxation of the first three:

```text
PLAYTEST < FIXTURE < DEBUG_PRIVILEGED < EXPLORATORY_JVM
```

This ordering describes increasing authority and risk, not increasing evidence quality. EXPLORATORY output is always invalid for gameplay acceptance.

## 3. Status Vocabulary

Every Platform capability uses one of these labels:

| Status | Meaning |
|---|---|
| `CURRENT` | Implemented in the present repository and backed by its stated gate/evidence. |
| `PARTIAL` | A bounded implementation exists, but the complete public capability is not delivered. |
| `PLANNED` | Accepted product scope with no implementation claim. |
| `ULTIMATE` | Long-term completion target whose design and gate may still evolve. |

Current status:

| Capability | Status | Track | Expected stage |
|---|---|---|---|
| Runtime Control and Testing | CURRENT | Runtime | V1 / continuing regression |
| MCP Runtime Companion | CURRENT | Runtime | V1 / continuing regression |
| Deep Runtime Observation | CURRENT | Runtime | Phase 9B complete; later consumers remain |
| Typed Deep Debug | PARTIAL | Runtime | Phase 9C planned |
| Persistent Storage Plane | PARTIAL | Runtime | Phase 9D planned |
| Recording V2 / reconstructable long-term state | PARTIAL | Runtime | Phase 9E-9F planned |
| Runtime advanced diagnostics and recovery | PLANNED | Runtime | Phase 10 |
| Source Intelligence | PLANNED | Development Intelligence | DI-1 through DI-4 |
| Mapping Intelligence | PLANNED | Development Intelligence | DI-1 and PI-0 |
| Mod Intelligence | PLANNED | Development Intelligence | DI-5 through DI-6 |
| Runtime <-> Source Correlation | PLANNED | Platform Integration | DI-7 / PI-2 |
| Safe Probe | PLANNED | Development Intelligence | DI-8 |
| Unsafe Exploratory JVM | PLANNED | Development Intelligence | DI-8, after a dedicated security spike |
| Human Developer Inspector | PLANNED | Platform Integration | DI-9 / PI-4 |
| Unified Platform Agent Surface | PLANNED | Platform Integration | PI-1 through PI-5 |

No planned entry above is a current Runtime capability.

## 4. Platform Planes

### 4.1 Runtime Control and Testing Plane — CURRENT

Owns GUI, GAME_ROUTED input, player/world interaction, player commands, wait/assert, capture, Recording, Server Peer, Fixture, conformance and gameplay evidence. This remains the trusted black-box action path.

### 4.2 Deep Runtime Observation Plane — CURRENT

Owns typed Player, Entity, Block, Block Entity, Chunk, loading/ticket, Scheduled Tick, World, Menu and Provider observations across explicit client-known and server-authoritative perspectives. It acquires Runtime truth without becoming a general JVM inspector.

### 4.3 Typed Deep Debug Plane — PARTIAL

Owns strongly typed, domain-specific mutations such as future `debug.player.*`, `debug.entity.*`, `debug.world.*`, `debug.block_entity.*`, `debug.chunk.*`, `debug.menu.*`, `debug.client.*`, `debug.network.*` and `debug.storage.*`.

Internal Accessors, Invokers, Loader internals, Mixins and direct mutation are allowed. Public `set_field`, arbitrary invocation and object-graph traversal are not.

### 4.4 Source Intelligence Plane — PLANNED

Owns resolved Minecraft/Loader/Mod artifacts, decompiled or upstream source, symbols, ASTs, hierarchy, references, call graph and structured version differences. Source results always identify Target, universe, mapping namespace, origin and generated/decompiled status.

### 4.5 Mapping Intelligence Plane — PLANNED

Owns version-aware resolution among Official/Mojmap, runtime names, Fabric Intermediary and Yarn where applicable. A symbol identity includes at least Target identity, mapping namespace, owner, member name and descriptor.

### 4.6 Mod Intelligence Plane — PLANNED

Owns project and third-party Mod analysis: metadata, Mod ID/version/Loader, entrypoints, dependencies, packages, embedded libraries, Mixin configuration, Access Widener and Access Transformer declarations. Source universes are explicit:

```text
minecraft
loader
project
mod:<mod-id>
```

### 4.7 Runtime <-> Source Correlation Plane — PLANNED

Correlates runtime Screen/Menu/Entity/packet/hook/exception/Provider/debug identities with mapped source symbols, and supports the reverse question: whether a source symbol exists, is loaded, is instrumented or appears in recent runtime evidence.

### 4.8 Exploratory Debug Plane — PLANNED

Provides two deliberately distinct levels:

- Safe Probe: read-only, bounded projection/filter/map/comparison over explicitly exposed objects; no filesystem, network, process, ClassLoader, arbitrary reflection, arbitrary invocation or mutation.
- Unsafe Developer REPL (`EXPLORATORY_JVM`): full-power local JVM exploration selected only after a dedicated implementation/security spike.

The unsafe level is **not a sandbox**. If enabled, it may be capable of filesystem, network, reflection, ClassLoader, process and JVM-internal access. A bearer token does not make it sandboxed.

### 4.9 Evidence, Artifact and Timeline Plane — CURRENT, EXTENSIBLE

Correlates Runtime input, ticks, frames, state, events, commands, assertions and provenance. Future bundles may add content-addressed source-symbol, stack-mapping, Mixin-analysis and source-snippet references; they must not duplicate large source corpora into every bundle.

### 4.10 Human Developer Inspector — PLANNED

Provides optional human visibility for sessions, capabilities, UI tree, Player/Entity/Chunk state, Providers, Debug operations, recordings, timeline, source navigation, call graph, Mixin inspection and explicitly armed exploratory work. It complements the Agent surface and does not define Runtime semantics.

Candidate views include Dashboard, Runtime Session, Player, Entities, Blocks/Chunks, GUI Tree, Deep Observation, Providers, Debug Operations, Recording/Artifacts, Timeline, Source Browser, Symbol Search, Call Graph, Mixin Inspector, Exploratory REPL and Logs/Diagnostics. This is a product inventory, not a current UI implementation.

## 5. Deployment Boundary

```text
Coding Agent / Human Inspector
             |
             v
Unified Agent Surface (MCP + Native APIs)
             |
     Platform Companion
       |           |
       |           +-- Development Intelligence Service
       |                 +-- Artifact Resolver
       |                 +-- Mapping Service
       |                 +-- Decompiler adapter
       |                 +-- Managed Source Store
       |                 +-- Symbol / AST indexes
       |                 +-- Reference / Call Graph
       |                 +-- Mod Intelligence
       |
       +-- Runtime Adapter -- HTTP/WS --> Minecraft Agent Control Runtime
                                               |
                                               +-- Integrated Server
                                               +-- Dedicated Server Peer
```

The Platform Companion and Development Intelligence Service may initially share a process, but remain separate architectural responsibilities. The Runtime Mod must not host heavyweight decompilers, source caches, SQLite/full-text indexes, AST parsers, call-graph engines or third-party Mod decompilation.

Build/project modification/process launch belongs to the Agent host or Development service under its own authorization. It does not justify adding arbitrary shell, process control or filesystem browsing to the normal Minecraft Runtime.

## 6. Managed Source Architecture

### 6.1 Pipeline

```text
Target/session identity
  -> Artifact Resolver
  -> checksum and origin verification
  -> mapping/remap selection
  -> upstream source or decompiler adapter
  -> Managed Source Store
  -> symbol/full-text/AST indexes
  -> reference/hierarchy/call graph
```

The store is an OS-managed, versioned and reproducible cache, never required project source. Project repositories must not carry bulk `minecraft-sources/`, `decompiled/` or per-version reference trees.

### 6.2 Artifact Resolver

Planned inputs include Minecraft client/server artifacts, official mappings, Intermediary, Yarn where applicable, Forge/NeoForge artifacts, available Loader sources and dependency Mod JARs. Every input records version, repository origin, checksum and resolution metadata.

### 6.3 Source Retrieval and Decompilation

Prefer authoritative upstream source when available. Otherwise reuse a mature JVM decompiler behind an implementation-neutral interface. Public APIs return source/symbol/AST/reference concepts and never bind the protocol to one decompiler.

### 6.4 Index and Query Model

Planned source operations include search, class/method/field retrieval, package exploration, symbol description, references, callers, callees, hierarchy and version diff. Queries must support snippets/ranges/projections, pagination, reference limits and bounded graph traversal by depth, direction, node count, package and universe.

An implementation-neutral Native API may eventually expose equivalents of:

```text
source.search
source.get_class / get_method / get_field
source.package / symbol
source.references / callers / callees / hierarchy
source.diff_versions
mapping.resolve / convert / describe
mod.analyze
mixin.inspect / validate
trace.explain
```

These names are design sketches and do not freeze the Native or Wire protocol.

### 6.5 Version Intelligence

`source.diff_versions` is structured, not only textual. It may report symbol existence/movement, signature/descriptor changes, inheritance changes, AST-level change and call-graph differences. Same spelling across versions never proves semantic identity.

### 6.6 Supply Chain, License and Copyright

The Development Intelligence threat model must cover artifact origin, checksums, repository trust, mapping provenance, decompiler input, third-party JAR trust and cache poisoning. Cached entries record source origin, license metadata, generated/decompiled status and redistribution restrictions.

Local caching does not grant a right to redistribute decompiled Minecraft source. Release and sharing policies require a separate legal/license review.

## 7. Mod and Injection Intelligence

`mod.analyze`-style capabilities will identify metadata, entrypoints, dependencies, Mixins, Access Widener/Transformer files, packages/classes and embedded libraries without requiring the user to copy third-party sources into the development workspace.

Mixin intelligence will eventually inspect target class/member/descriptor, injection kind, injection point, ordinal, slice, require/expect/allow and priority. Runtime correlation should distinguish configured, class loaded, target resolved, injection matched and actual match count.

AW/AT/Accessor/Invoker validation will resolve mappings, confirm the class/member/descriptor exists for the selected Target and report version compatibility. Loader metadata validation will cover `fabric.mod.json`, Forge/NeoForge metadata, Mixin configs, AW/AT declarations, entrypoints, dependencies and environment declarations.

These are `PLANNED` Development Intelligence capabilities, not current Runtime claims.

## 8. Unified Identity and Agent Surface

Runtime, Source and Mapping services share one version-aware identity model:

```text
Target identity
Source universe
Mapping namespace
Class owner
Member name
Descriptor
Artifact hash
```

The Platform should automatically resolve the source universe from the active Runtime session/Target fingerprint where possible. Explicit override remains available for offline or cross-version work.

Native APIs may be fine-grained. MCP stays ergonomic and domain-aggregated; Platform expansion must not produce hundreds of narrowly overlapping Tools. Large source, graph and state results use Resources, Artifact handles, streaming and indexed retrieval instead of huge Tool JSON.

The long-term goal is one Agent entrypoint even when multiple backend services implement it. Unified routing does not imply shared process, shared credentials or shared authority.

Future aggregated families may include runtime observe/interact/debug/record, source search/get/references/diff, Mod analyze/search/validate and an explicitly unsafe exploratory execute family. Names and schemas are not frozen by this charter.

## 9. Exploratory Debug Security and Evidence

### 9.1 Safe Probe

Safe Probe is read-only, typed/bounded and auditable. It may inspect only explicitly exposed roots and operations. It must have query/depth/time/result budgets and cannot be used as a hidden general Reflection RPC.

### 9.2 Unsafe Developer REPL

Unsafe JVM execution is:

- disabled by default;
- loopback-only in its first design;
- enabled only by explicit local configuration and a human-visible developer decision;
- authorized by a separate short-lived Exploratory Arm, never an ordinary Debug Arm;
- restricted to a trusted local developer threat model;
- explicitly labelled **NOT SANDBOXED**.

Remote/LAN unsafe execution is outside the ordinary LAN capability and requires a separate security study.

### 9.3 Arm and Audit

The future lifecycle is equivalent to `exploratory.arm`, `renew` and `disarm`, with short TTL and session binding. Audit records principal, session, request, timestamp, script hash, duration, result and exception. Full script text is not recorded by default because it may contain secrets; an explicit local policy may enable it.

Every result carries:

```text
authority=exploratory
mechanism=safe_probe | jvm_script
evidence=invalid_for_acceptance
```

Exploratory writes/execution can arrange or investigate a test, but can never prove gameplay. Repeated useful exploration must graduate through typed API design, conformance and evidence rules before joining Observe or Debug.

## 10. Competitive Architecture Study

This study adopts public design ideas, not source code. It is not a claim of implementation parity.

### 10.1 DebugBridge

Public project materials describe a loopback WebSocket Runtime inspector with native Player/Entity/Block/Screen/capture endpoints, Groovy execution and a bundled Vue human inspector. They also describe client-only operation and developer-gated capabilities.

Adopt:

- runtime inspector and fast structured native endpoints;
- ad-hoc JVM investigation as a real developer need;
- human Web UI for visibility and source/runtime navigation;
- mapping-aware runtime symbol handling.

Extend:

- five explicit Forge/NeoForge/Fabric Targets;
- Integrated Server and Dedicated Server Peer authority;
- typed automation, real GAME_ROUTED input, wait/assert and recording;
- scopes, Lease, separate Debug/Exploratory Arms, provenance and evidence;
- Runtime <-> Source correlation and Mod/injection intelligence.

Deliberately different:

- unrestricted scripting is never the normal test path;
- the architecture is not client-only;
- arbitrary REPL output never becomes gameplay evidence;
- full JVM execution is called unsandboxed rather than treated as safe because it is loopback.

Reference: <https://github.com/use-ai-for-mc/debugbridge>

### 10.2 mcdev-mcp

Public project materials describe automatic Minecraft artifact acquisition, Vineflower decompilation, an OS cache, symbol/source/package queries, hierarchy, references/call graph and DebugBridge runtime integration.

Adopt:

- managed artifact/source retrieval and regeneratable OS cache;
- decompilation as an implementation detail;
- class/method/symbol retrieval, hierarchy, references and call graph;
- token-efficient source retrieval instead of bulk workspace source trees.

Extend:

- shared five-Target Runtime/Source/Mapping identity;
- multi-loader and third-party Mod universes;
- structured version differences and Mixin/AW/AT/metadata intelligence;
- runtime failure, timeline and evidence correlation;
- automatic build/test/regression feedback through the existing Runtime.

Deliberately different:

- source intelligence is selected from Runtime Target identity rather than remaining an unrelated active-version setting;
- heavy source work stays outside the Minecraft Mod;
- preparation and cache mutation use an explicit Development-service authority rather than silently inheriting Runtime scopes;
- Source facts do not override Runtime facts.

Reference: <https://github.com/use-ai-for-mc/mcdev-mcp>

### 10.3 minecraft-dev-mcp supporting study

Its public design provides an additional reference for version/mapping-aware JAR remapping, third-party Mod decompilation and a shared OS cache containing JARs, mappings, sources and SQLite indexes. These are candidate design inputs only; codec/index/cache choices require DI spikes and supply-chain/license review.

Reference: <https://github.com/MCDxAI/minecraft-dev-mcp>

## 11. Parallel Roadmap

### 11.1 Runtime Track — unchanged

The existing Phase 9C-9G and Phase 10 scope, order, gates and independent-review boundaries remain unchanged. This charter cannot waive a Runtime gate.

### 11.2 Development Intelligence Track

| Stage | Objective | Draft exit gate |
|---|---|---|
| DI-0 | Requirements, DebugBridge/mcdev-mcp/minecraft-dev-mcp study, mapping/decompiler/index/cache/license spikes | evidence-based architecture, threat boundary, candidate benchmarks and no production DI claims |
| DI-1 | Managed Artifact Resolver, Target identity, mappings and Source Store | reproducible checksummed corpus for representative Targets; cache lifecycle and provenance verified |
| DI-2 | Source acquisition/decompiler adapters | upstream/decompiled source retrieval works behind an implementation-neutral contract; origin/licence metadata present |
| DI-3 | Symbol, full-text and AST indexes | bounded class/method/field/package/string/annotation search with deterministic rebuild and token-efficient retrieval |
| DI-4 | References, hierarchy and call graph | callers/callees/implementations/field sites are budgeted, indexed and validated on representative corpora |
| DI-5 | Mod JAR intelligence | typed metadata/dependency/entrypoint/package/embedded-library analysis across representative loaders |
| DI-6 | Mixin/AW/AT/Accessor/Invoker/Loader metadata intelligence | mapping-aware validation and structured failure explanations across representative Targets |
| DI-7 | Runtime <-> Source correlation | stable symbol identity links runtime evidence and source indexes in both directions |
| DI-8 | Safe Probe and separately armed unsafe JVM exploration | Safe Probe security conformance plus explicit unsandboxed local-only REPL threat/arm/audit acceptance |
| DI-9 | Human Developer Inspector | read/inspect workflows consume the same typed services; dangerous controls remain explicitly armed |
| DI-10 | Unified Development Intelligence hardening | multi-Target performance, cache integrity, security, licence, recovery and release evidence pass |

DI stages may be re-sliced after actual spikes. DI implementation does not start in this charter task. Runtime Phase 9C must establish the typed Debug boundary before exploratory/runtime-correlation implementation proceeds. DI-0 architecture work may be authorized separately.

### 11.3 Platform Integration Track

| Stage | Objective | Draft exit gate |
|---|---|---|
| PI-0 | Unified Target and symbol identity | Runtime, source, mapping and artifact identities round-trip across all supported Target descriptions |
| PI-1 | Unified Agent surface | ergonomic MCP aggregation and native routing without duplicate authority/state machines |
| PI-2 | Runtime <-> Source correlation | events/failures/symbols navigate bidirectionally with explicit confidence and provenance |
| PI-3 | Evidence integration | Artifact references source/mapping/Mod analysis by content address without bundling bulk corpora |
| PI-4 | Human Inspector integration | UI consumes the same APIs, reports current capabilities honestly and preserves Arm boundaries |
| PI-5 | Platform release hardening | Runtime, DI and Exploratory threat models, regression, performance and supply-chain gates pass together |

The Runtime and Development Intelligence tracks are orthogonal. After a separately approved Phase 9C establishes typed Debug boundaries, later Runtime work and DI-0 through DI-4 may proceed in parallel. Phase 10 remains independently gated.

## 12. Product Scope and Release Strategy

Scope layers are distinct:

```text
Runtime V1
Runtime Ultimate
Platform Developer Preview
Platform Beta
Platform Ultimate
```

Adding Platform Ultimate scope does not retroactively invalidate Phase 8 or Runtime V1 evidence.

A first Platform Developer Preview may ship with current Runtime control/testing, Deep Observation, a suitably gated typed Deep Debug surface and MCP. It does not need Source Intelligence, unsafe Exploratory JVM or Human Inspector to claim that narrower preview scope.

Platform Ultimate requires:

- complete Runtime and five-Target testing tracks;
- Deep Observation, typed Deep Debug, Persistent Storage and recording/replay diagnostics;
- Source, Mapping, Mod and Mixin/AW/AT intelligence;
- Runtime <-> Source correlation;
- Safe Probe and explicitly unsafe Exploratory JVM;
- unified MCP/Native surface and Human Inspector;
- complete Platform security, evidence, supply-chain, license and release engineering.

## 13. Non-goals and Explicit Deferrals

This charter does not:

- start Phase 9C, Phase 10, DI implementation or Platform Integration implementation;
- install a decompiler, download Minecraft sources, create a Source Store/index or add source MCP Tools;
- implement Safe Probe, Groovy/JVM scripting or a Web UI;
- rename the repository, Mod ID, packages or artifacts;
- turn the normal Runtime into an arbitrary shell, process controller, filesystem RPC or Reflection RPC;
- merge EXPLORATORY into DEBUG_PRIVILEGED;
- treat an unsafe JVM REPL as sandboxed;
- permit exploratory evidence to satisfy gameplay acceptance;
- change existing Phase 9/10 scope or bypass their gates;
- freeze Wire Protocol v1.

## 14. Current Governance Decision

```text
Current Runtime task continues under existing Gate.
Platform goal upgrade recorded.
Development Intelligence implementation has NOT started.
Phase 9C has NOT started.
Phase 10 has NOT started.
Wire Protocol v1 remains NOT FROZEN.
```
