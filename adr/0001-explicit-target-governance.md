# ADR 0001: Explicit Target Governance

## Status

Accepted.

## Decision

Each Minecraft-version/Loader pair is a flat sibling Gradle target under `versions/`. Targets do not inherit implementation from each other. Target properties remain local; product identity remains at repository root.

## Consequences

- Porting relationships are references, not build dependencies.
- Duplication is accepted until sharing is proven.
- Future targets are created only when implementation starts.
- Root settings explicitly list real targets.

