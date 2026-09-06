# AGENTS.md

## Minecraft Operation and Human Acceptance Boundary

For this repository, operate and validate Minecraft through Mine-Craft-Protocol's
own Runtime HTTP/WebSocket API or MCP Companion. Do not use Codex Computer Use,
CUA, desktop automation, or host input injection to operate Minecraft or its
keyboard, mouse, or window. Do not substitute a Computer Use skill for this rule.

When acceptance requires physical Escape, Alt+Tab, real mouse clicks, or host
focus changes, ask the user to perform the concrete action and wait. Judge the
result using Runtime/Event/Audit evidence and the user's visual confirmation.
Never report injected desktop events as human acceptance. Read-only process
metadata and normal build/launch commands are not desktop input automation.

## Documentation Authority and Navigation

The user-authorized documentation home is [documents/README.md](documents/README.md).
Keep only this file and the concise project README as root Markdown entries.

- [Core vision](documents/product/vision.md): the committed Autonomous Testing product.
- [Optional extensions](documents/product/extensions.md): E1/E2/E3, never implicit Core gates.
- [Execution plan](documents/product/execution-plan.md): current status and next authorized gates.
- [Architecture](documents/architecture/overview.md), [control model](documents/architecture/agent-control.md)
  and [threat model](documents/architecture/threat-model.md): adopted design and current limits.
- Historical Phase records and immutable `validations/results/` are evidence, not current work orders.
  Do not rewrite their historical paths, hashes or failures to match newer results.
- Testing plans live in `documents/testing/`; executable drivers stay in `validations/tests/`.
  Module-local operational references such as `components/companion/README.md` may stay beside code.
  Root, Targets and Gradle components use native `build.gradle.kts` files; shared
  Instance Cascade infrastructure lives in `gradle/instances.gradle`.
- Documentation examples use repository-root command paths unless explicitly stated.
  After moving documents, update live references and run
  `pwsh -File validations/tests/core/repository/Invoke-DocumentationGate.ps1`.
- Plans, Showcase READY, implementation COMPLETE and human/remote acceptance PASS
  are different states. Never promote one by editing documentation alone.

## Placement Decision Tree

Use the first applicable responsibility, not a historical development phase:

1. Minecraft-version/Loader implementation -> `versions/<target>/`.
2. Product code independent of a single Target -> a real `components/<component>/`.
   Gradle components: runtime-safety and protocol-schema. Companion is an independent
   Node/TypeScript component and is **not** registered as a Gradle project.
3. Normal local Minecraft runtime -> `instances/<target>/<variant>/`.
4. A validation-specific isolated runtime (Vulkan/Showcase) -> ignored
   `validations/.local/<scenario>/<target>/`; never a fourth canonical variant.
5. Project documentation -> `documents/`.
6. How to prove behavior -> `validations/tests/{core,compatibility,releases}/`.
7. Produced evidence -> `validations/results/{core,compatibility,releases}/`.
   Create a category only when it has real content; do not add empty placeholders.
8. Native/shared Gradle infrastructure -> `gradle/`.
9. An official ecosystem directory may remain where its tool requires it, but
   tracking is a separate decision. Local generated IDE state is not product truth.

Core authority remains **Agent-native Minecraft Autonomous Testing Platform**.
E1/E2/E3 are optional and non-blocking. READ/OPERATE/TAKEOVER express intent, not
permission. PLAYTEST / FIXTURE / DEBUG_PRIVILEGED evidence stays separate; no
directory migration, passing build or documentary claim grants gameplay acceptance.

## Repository Philosophy

This repository follows an **Explicit Target Governance** model for multi-version, multi-loader Minecraft Java development.

The primary goals are:

1. **Local understandability over abstraction.**
2. **Explicit structure over hidden automation.**
3. **Stable, independently operable targets over inheritance chains.**
4. **Proven sharing over speculative sharing.**
5. **Simple standard APIs over custom frameworks.**
6. **Behavioral correctness over preserving historical implementation.**

A developer should be able to enter the repository, inspect the directory tree, run a small number of `gradlew` commands, and quickly understand what targets exist and how each target is built and tested.

Do not optimize the repository for minimum line count, minimum duplication, or maximum abstraction at the expense of this property.

---

# 1. Target Model

Every supported combination of:

```text
Minecraft version × Mod loader
```

is an explicit **Target**.

Target IDs use:

```text
<minecraft-version>-<loader>
```

Examples:

```text
1.20.1-forge
1.21.1-neoforge
26.1.2-neoforge
26.2-neoforge
26.2-fabric
```

Minecraft version comes first because Minecraft-version differences are generally the dominant compatibility boundary.

Do not use loader-first target names such as:

```text
forge-1.20.1
fabric-26.2
```

---

# 2. Target Directory Layout

All real product targets are flat children of:

```text
versions/
```

Example:

```text
versions/
├─ 1.20.1-forge/
├─ 1.21.1-neoforge/
├─ 26.1.2-neoforge/
├─ 26.2-neoforge/
└─ 26.2-fabric/
```

Each target is a real Gradle subproject.

A target normally owns:

```text
versions/<target>/
├─ build.gradle.kts
└─ src/
```

A target directory must represent an actual implementation.

Do **not** create:

* empty future targets;
* placeholder target directories;
* speculative loader nodes;
* version skeletons that are not currently being implemented.

A future target is created only when work on that target actually begins.

---

# 3. Targets Are Siblings

Targets do not inherit implementation from one another.

Do not create dependency chains such as:

```text
1.20.1-forge
    ↓
1.21.1-neoforge
    ↓
26.1.2-neoforge
    ↓
26.2-neoforge
```

in Gradle or source ownership.

All targets are siblings:

```text
versions/
├─ A
├─ B
├─ C
└─ D
```

A target may be the **porting reference** for another target, but that is a development relationship, not a build dependency.

---

# 4. Baseline Is Semantic, Not Structural

A repository may designate one target as its current **Baseline**.

The Baseline answers:

> What implementation defines the intended behavior of this product?

It does **not** mean:

> Other targets inherit code from this target.

The Baseline may differ between repositories.

It is not required to be:

* the oldest target;
* the newest target;
* Forge;
* Fabric;
* NeoForge.

The best current reference implementation should serve as the behavioral authority.

The Baseline may change over the lifetime of the project without changing the sibling relationship between targets.

---

# 5. Porting References

When porting to a new target, prefer a stable existing target with the smallest meaningful difference.

Prefer transitions such as:

```text
26.2-neoforge
→
26.2-fabric
```

when investigating loader differences.

Prefer:

```text
26.1.2-neoforge
→
26.2-neoforge
```

when investigating Minecraft/API-version differences.

Whenever practical:

> A port edge should change only one major dimension at a time.

The repository does not need every project to have the same historical target graph.

Different repositories may have different origins and porting paths.

---

# 6. Do Not Pre-Abstract

New implementation belongs to the concrete target first.

Do not begin a new repository by creating speculative layers such as:

```text
common/
shared/
core/
platform/
compat/
bridge/
api/
impl/
```

unless there is already concrete evidence that the layer is necessary.

Code duplication is allowed.

A duplicated implementation that is easy to understand is preferable to an abstraction that requires several files and indirections to understand one operation.

Use this rule:

> **Abstraction follows evidence.**

Do not abstract because code *might* be shared later.

---

# 7. Shared Code Promotion

Shared code may be introduced only after multiple real targets demonstrate stable equivalent behavior.

A candidate for sharing should satisfy all or nearly all of the following:

1. Equivalent implementations already exist in multiple targets.
2. Their behavior is known to be the same.
3. The shared form is stable across those targets.
4. Sharing measurably reduces total complexity.
5. A target remains easy to understand after the extraction.
6. The abstraction does not hide important Minecraft-version or loader-specific behavior.

If sharing reduces duplicated lines but increases navigation cost or conceptual complexity, do not share.

Do not optimize for DRY at the expense of readability.

---

# 8. Project / Target / Instance / Local Facts

- `gradle.properties`: product identity (`mod_id`, `mod_name`, `maven_group`,
  `mod_version`, `mod_environment=both`, licence/authors/description) and real
  `org.gradle.*` options. One authoritative key per fact; no group/archive aliases.
- `versions/<target>/target.properties`: Minecraft/Loader/Java and Target dependency facts.
  Loader plugin bootstrap versions remain in that Target's native plugins block.
- `instance.properties`: tracked runtime defaults, never Minecraft/Loader dependency versions.
- `local.properties`: ignored environment-like tool paths and final `instance.*`
  overrides only. Unknown user keys are preserved; they are not merged into Project/Target facts.
- Component-specific versioning may remain component-owned; moving a module must
  not silently change its Maven identity or embedded-library version.

---

# 9. Java Identity

For repositories owned through GitHub, prefer:

```text
Maven group:
io.github.<owner>

Java package root:
io.github.<owner>.<modid>
```

Example:

```text
mod_id=examplemod
maven_group=io.github.example

package:
io.github.example.examplemod
```

Minecraft version and loader name do not belong in the Java package root.

Avoid:

```text
io.github.example.examplemod.fabric
io.github.example.examplemod.v121
```

unless the code itself genuinely represents a loader-specific or version-specific concept inside a shared source tree.

Target directories already express the target identity.

---

# 10. Explicit Hierarchical Gradle Topology

```text
versions/<target>       <-> :versions:<target>
components/<component> <-> :components:<component>
```

Use explicit `includeTarget` / `includeComponent` calls in settings. Missing real
directories/build scripts must fail; no scanning, alias modules or fake projects.
Intermediate `:versions` / `:components` projects only express hierarchy and own
no product logic. Companion remains outside the Gradle graph.

Keep required pluginManagement, repositories and toolchain bootstrap. Root
build.gradle.kts stays coordination-only; the single Instance Cascade implementation
belongs in `gradle/`, not five copies, buildSrc, or a speculative build-logic framework.
Do not introduce Stonecutter without an explicit user decision.

---

# 11. Standard Target Task Contract

A real target should expose a predictable Gradle interface wherever applicable.

Expected tasks include:

```text
build
test
runClient
runClientMultiplayer
runServer
```

Not every target must have meaningful JVM unit tests, but `build` and the relevant run configurations should remain obvious.

The primary user-facing command shape is:

```text
gradlew :versions:<target>:<task>
```

Examples:

```powershell
.\gradlew.bat :versions:1.20.1-forge:build
.\gradlew.bat :versions:26.2-neoforge:runClient
.\gradlew.bat :versions:26.2-fabric:runServer
```

Do not replace this with opaque parameter-driven dispatch such as:

```text
gradlew runClient -Pversion=... -Ploader=...
```

unless explicitly requested.

Explicit project paths are preferred.

---

# 12. Canonical Instance Variants

Mine-Craft-Protocol is a both-side project; all three variants are real:

```text
instances/<target>/
  client/
  client-multiplayer/
  server/
```

Do not create these directories until local tooling/runtime actually needs them.
Vulkan is a client launch scenario, not `client-vulkan` as a fourth instance type.
The retained `runClientVulkan` task uses a validation-isolated work directory and
the standard client configuration. Do not weaken actual backend verification.

All instance files are ignored by default. Only reviewed exact-path exceptions
may be whitelisted; never whitelist a world/token/cache/log/mod or whole variant.
Local runtime-data moves are separate from Git moves: inspect, require absent
destination, preserve conflicts, never overwrite or erase worlds to tidy the tree.

# 13. Instance Cascade and Local Overrides

Supported fields: memory, width, height, playerName, extraJvmArgs, extraGameArgs.

Within each file, in descending specificity:

```properties
instance.[<target>].<variant>.<property>
instance.[<target>].<property>
instance.<variant>.<property>
instance.<property>
```

Resolve tracked instance.properties independently, then local.properties
independently. ANY applicable local value wins, even local Global over repository
Target+Variant. Never merge two Properties objects before searching specificity.

Extra arguments are JSON arrays of strings; a present empty value means an empty
list and clears repository extra args. Missing memory/size/name preserves Loader
defaults. An explicitly invalid/empty typed value fails before the relevant run,
not an unrelated build. Do not duplicate memory/name/size/game-directory options
inside extra args. No fake defaults merely to fill a table.

The shared resolver is consumed by all five native Loader run models. Preserve
existing local files/unknown keys and migrate only proven legacy instance keys.
Do not print local secrets or unrelated machine settings into evidence.

# 14. Client Player Profiles

The new client-multiplayer variant requires an explicit playerName before launch;
the repository does not invent a new primary player identity. If both names are
configured they must differ. Invalid/missing second-client identity must not block
build/test/server or the ordinary client. Configure a distinct value in ignored
local.properties before a multiplayer validation; never reuse two identical players.

One client at a time for ordinary acceptance. A deliberately scoped multiplayer
test may use server + client + client-multiplayer with separate directories and
identities. Do not confuse that explicit test with unexplained duplicate clients.

---

# 15. Project Mod vs External Test Mods

During development, the project itself should normally be loaded through the loader/Gradle development source-set mechanism.

Do not copy the project's own built JAR into:

```text
instances/<target>/<profile>/mods/
```

for ordinary development runs.

Doing so can create duplicate-mod or stale-JAR ambiguity.

External development/test mods may be manually placed into the standard instance:

```text
mods/
```

unless an explicit automated dependency-management system is later introduced.

Do not create speculative third-party-mod management infrastructure unless requested.

---

# 16. `src/main` and `src/client`

Where the loader/tooling provides a real compile-time client boundary, use it.

For Fabric/Loom, prefer:

```text
src/
├─ main/
└─ client/
```

when the project contains client-only code.

Typical ownership:

```text
src/main/
→ blocks
→ items
→ registries
→ world logic
→ server-safe data

src/client/
→ renderers
→ models
→ client initialization
→ client-only resources
```

This split is not cosmetic.

It provides a real environment boundary and helps prevent client-only APIs from leaking into dedicated-server code.

Do not add extra source-set categories merely for organizational aesthetics.

---

# 17. Prefer Standard Minecraft and Loader APIs

Use implementation layers in this order of preference:

1. Vanilla Minecraft standard mechanism.
2. Loader-standard mechanism.
3. Existing mature library when genuinely justified.
4. Small project-specific implementation.
5. Custom framework.

The fifth option should be rare.

Do not implement:

```text
RegistryManager
PlatformManager
FactoryFramework
CompatibilityFacade
ServiceLayer
generic wrapper stacks
```

around APIs that are already simple and readable.

If:

```java
Registry.register(...)
```

is clearer than a custom registry framework, use it directly.

---

# 18. Preserve Intent, Not Historical Implementation

Old code is a knowledge source, not an architectural requirement.

When rewriting:

> **Preserve intended product behavior, not implementation structure.**

Historical code may be:

* deleted;
* merged;
* split;
* renamed;
* replaced;
* simplified.

Do not keep a complex abstraction merely because the old implementation used it.

If historical compatibility is explicitly out of scope, do not introduce compatibility layers for old:

```text
registry IDs
resource paths
block states
class names
world saves
```

unless the user explicitly requests compatibility.

---

# 19. Rendering and Model Principle

When working with custom rendering:

> Asset geometry is authoritative.

Model:

```text
geometry
origin
pivot
local rotations
```

should not be silently corrected by arbitrary project-specific transforms.

Runtime transforms should have an explicit semantic purpose such as:

```text
coordinate-space conversion
whole-model direction rotation
Minecraft ItemDisplayContext presentation
framework-coordinate adaptation
```

Avoid empirical per-model corrections such as:

```text
model A translate +0.23
model B scale 0.97
model C rotate 17°
```

unless the product itself genuinely requires a unique transformation.

For block direction changes, conceptually treat the standard Minecraft block as:

```text
0..16 × 0..16 × 0..16
```

with macro center:

```text
(8,8,8)
```

or:

```text
(0.5,0.5,0.5)
```

in world block units.

Local asset pivots are not the same thing as whole-block orientation pivots.

---

# 20. Validation Has Separate Layers

Do not collapse all validation into a single “works” state.

At minimum distinguish:

```text
Build
Runtime
Functional / Visual Acceptance
```

Example:

```text
build                PASS
runClient            PASS
runServer            PASS
visual orientation   PENDING
```

A successful Gradle build does not prove visual correctness.

A client reaching the title screen does not prove:

```text
models
textures
placement directions
animations
GUI
world rendering
```

are correct.

Report what was actually verified.

---

# 21. Dedicated Server Safety

Client success does not prove server safety.

Where the product is expected to load on a dedicated server, run:

```text
runServer
```

and verify that it reaches a normal ready state.

Client-only code must remain outside server execution paths.

Do not infer server compatibility from successful client compilation.

---

# 22. Multiplayer Reference Testing

Where the repository provides the full run topology, prefer validating:

```text
server
+
client
+
client-multiplayer
```

simultaneously.

This proves that:

* run directories are independent;
* player identities are independent;
* the target can support real client/server testing;
* local runtime state does not collide.

Do not use this requirement to force every ordinary project to maintain unnecessary profiles.

---

# 23. Repository Should Be Self-Describing

Do not depend on large amounts of governance documentation to explain ordinary structure.

The repository should communicate its structure directly through:

```text
versions/
instances/
settings.gradle.kts
gradle.properties
target build.gradle.kts
source-set names
clear package names
clear class names
```

Do not add:

```text
README
documents/
architecture documents
workflow documentation
```

unless explicitly requested.

`AGENTS.md` itself is the agent-governance exception.

Do not create additional documentation merely because a refactor occurred.

---

# 24. Do Not Add CI or Workflows Without Request

Do not create:

```text
.github/workflows/
CI pipelines
release workflows
automatic publication
dependency bots
```

unless explicitly requested.

The repository's local Gradle interface remains authoritative.

---

# 25. Do Not Create Git History Without Permission

Unless the user explicitly requests it:

* do not create commits;
* do not push;
* do not create branches;
* do not rewrite history;
* do not create tags.

Large tasks may modify the working tree and perform validation without creating Git history.

At the end, report:

```text
branch
HEAD
git status
staged state
```

and clearly state whether any commit/push/branch operation occurred.

---

# 26. Agent Work Protocol

Before significant implementation work, establish:

```text
Repository:
Target:
Baseline:
Direct Port Reference:
Change category:
```

Change category should normally be one of:

```text
product behavior
Minecraft-version adaptation
loader adaptation
target-specific adaptation
build infrastructure
runtime/test infrastructure
```

Before modifying a target, understand:

1. What behavior is intended?
2. Which existing target is the closest reference?
3. What dimension is actually changing?
4. Which files are owned by this target?
5. Which other targets could be affected?

Do not silently expand the task into repository-wide architectural work.

---

# 27. Agent Completion Report

After meaningful work, report factual results.

Include as applicable:

```text
Modified target
Modified shared/root files
Build result
Runtime result
Server result
Tests
Artifact result
Functional deviations
Remaining manual acceptance
Git status
```

Do not claim:

```text
visual PASS
functional PASS
server compatible
```

without actually verifying those properties.

---

# 28. Architecture Changes Require Evidence

Do not introduce repository-wide architecture merely because it is theoretically cleaner.

Examples requiring strong justification and normally explicit user approval:

```text
common/
shared/
core/
platform/
compat/
buildSrc/
Convention Plugins
Stonecutter
automatic target discovery
source generation frameworks
dependency-management frameworks
```

A new abstraction must solve a demonstrated problem.

Do not manufacture future problems to justify present abstractions.

---

# 29. Reference Repository Principle

A reference repository should demonstrate **working capabilities**, not speculative architecture.

It is appropriate for a reference repository to fully exercise:

```text
build
client
multiplayer client
dedicated server
local overrides
main/client separation
artifact production
```

It is not appropriate to create:

```text
fake targets
empty shared layers
unused compatibility infrastructure
future loader scaffolding
```

merely to demonstrate that they could exist.

A capability becomes part of the reference model only after it is actually needed and verified.

---

# 30. Final Decision Rule

When choices conflict, use the following priorities:

```text
Understandability > abstraction purity
Explicitness      > hidden automation
Correct behavior  > historical implementation
Stable targets    > inheritance chains
Proven sharing    > speculative sharing
Standard API      > custom framework
Simple concrete code > clever generic code
```

The repository is successful when a developer can enter it after months away and quickly answer:

```text
What targets exist?
Where is each target implemented?
How do I build one?
How do I run its client?
How do I run its server?
Where is its local runtime state?
Which configuration is global?
Which configuration belongs only to this target?
```

without first reverse-engineering a custom build framework.

That property is more important than minimizing duplicate source files.
