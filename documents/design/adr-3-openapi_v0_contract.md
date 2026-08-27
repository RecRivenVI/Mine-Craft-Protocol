# ADR 0003: OpenAPI V0 Contract Source

## 状态

已接受

适用于 Phase 1，并保持到 Phase 5。

## 背景

Runtime 与 Companion 需要共享可生成的接口字段定义。

## 决策

Use an OpenAPI 3.0 document as the versioned V0 HTTP contract source and OpenAPI Generator to produce Java and TypeScript models into build output.

## Constraints

- V0 remains unstable and is not Wire Protocol V1.
- Generated code is not yet a shared Runtime dependency.
- The schema contains only implemented, capability-gated surfaces with black-box evidence. Phase 2 adds request layering/security; Phase 3 automation; Phase 4 observation; Phase 5 Recording/Artifact/Debug. These extensions still do not freeze Wire Protocol v1 or a final Recording codec.
- WebSocket event evolution is documented separately until its event vocabulary is sufficiently proven.

## 后果

- Java and TypeScript consumers share field definitions.
- Target implementations remain concrete and independent.
- Schema validation and generation become local Gradle gates.
