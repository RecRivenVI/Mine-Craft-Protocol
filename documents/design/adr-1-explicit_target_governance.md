# ADR 0001: Explicit Target Governance

## 状态

已接受

## 背景

跨 Minecraft/Loader 的实现需要保持可独立理解与构建。

## 决策

Each Minecraft-version/Loader pair is a flat sibling Gradle target under `versions/`. Targets do not inherit implementation from each other. Target properties remain local; product identity remains at repository root.

## 后果

- Porting relationships are references, not build dependencies.
- Duplication is accepted until sharing is proven.
- Future targets are created only when implementation starts.
- Root settings explicitly list real targets.
