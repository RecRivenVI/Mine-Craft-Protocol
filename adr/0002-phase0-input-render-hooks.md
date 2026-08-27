# ADR 0002: Phase 0 Input and Render Hooks

## Status

Accepted for V0; subject to later refinement.

## Context

Phase 0 verified three extreme targets.

## Decision

- Use the Minecraft input-handler layer for `GAME_ROUTED_RAW`.
- Observe Screen, Menu, packet and Server thread stages independently.
- Build Interaction Tree from Screen children plus separate Menu Slot projection.
- Observe 26.2 Render Facts at GuiRenderState submission methods.
- Use the 26.2 GPU screenshot abstraction for both OpenGL and Vulkan.

## Consequences

- Private input entry points require Invokers.
- Fabric container positioning requires an Accessor.
- Server validation hooks must occur after PacketUtils thread scheduling.
- Render Facts do not imply business semantics.

