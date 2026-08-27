# AGENTS.md

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

# 8. Shared Facts Are Different From Shared Implementation

Stable repository-level product identity should normally have one source of truth.

Examples:

```text
Mod ID
Mod display name
Maven group
artifact base name
release version
author
```

These belong at repository/root level when they are genuinely common.

Target-specific facts belong to the target.

Examples:

```text
Minecraft version
loader version
Fabric API version
Forge version
NeoForge version
mappings
Java toolchain
target-specific dependencies
target-specific plugins
```

Do not turn the root `gradle.properties` into a database containing every target's dependency matrix.

Use this ownership rule:

> **“What product is this?” belongs to the root.**

> **“How does this target build and run?” belongs to the target.**

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

# 10. Gradle Must Stay Explicit

`settings.gradle.kts` should explicitly declare real targets.

Example:

```kotlin
include(":versions:1.20.1-forge")
include(":versions:1.21.1-neoforge")
include(":versions:26.2-neoforge")
include(":versions:26.2-fabric")
```

Prefer explicit declarations over:

```text
automatic target discovery
directory scanning
generated matrices
dynamic loader detection
implicit target activation
```

The root `build.gradle.kts` should remain thin.

Root tasks may aggregate real target tasks, but the root must not become a hidden framework that decides what each target means.

Avoid introducing, without demonstrated need:

```text
buildSrc/
Convention Plugins
custom target frameworks
dynamic source-set matrices
automatic loader selection
generated target graphs
```

Do not introduce Stonecutter or an equivalent conditional-source framework unless the user explicitly chooses that governance model.

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

# 12. Run Instance Layout

Development instances live under repository-root:

```text
runs/<target>/
```

A target may expose:

```text
runs/<target>/
├─ client/
├─ client-multiplayer/
└─ server/
```

`client` is the normal development client.

`client-multiplayer` is a second independent client profile used for multiplayer/LAN/server testing.

`server` is the dedicated server profile.

The latter two may be omitted for projects that genuinely do not need them, but a reference repository may deliberately implement all three.

`runs/` is local runtime state and must not be committed.

Typical ignore rule:

```gitignore
/runs/
```

---

# 13. Local Developer Configuration

Machine-specific developer configuration belongs in:

```text
/local.properties
```

`local.properties` is optional and Git ignored.

Example:

```gitignore
/local.properties
```

Do not create a tracked `run.properties` merely to provide editable defaults.

Defaults that are part of the target's development behavior should live directly in the relevant build logic.

`local.properties` should only override local developer values.

Example:

```properties
player.client.name=LocalPlayer
```

Partial overrides must be allowed.

The repository must work when `local.properties` does not exist.

---

# 14. Client Player Profiles

Where multiple development clients exist, use separate player identities.

Conceptually:

```text
runClient
→ client player

runClientMultiplayer
→ second player
```

Names should be validated only when the corresponding client task runs.

Invalid client configuration must not block unrelated operations such as:

```text
build
test
runServer
```

The two multiplayer-testing client identities should not resolve to the same player name.

---

# 15. Project Mod vs External Test Mods

During development, the project itself should normally be loaded through the loader/Gradle development source-set mechanism.

Do not copy the project's own built JAR into:

```text
runs/<target>/<profile>/mods/
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
runs/
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
docs/
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
