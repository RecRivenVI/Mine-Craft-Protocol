# ADR 0002: Phase 0 Input and Render Hooks

## 状态

已接受

适用于 V0，允许后续基于验证修订。

## 背景

Phase 0 verified three extreme targets.

## 决策

- Use the Minecraft input-handler layer for `GAME_ROUTED_RAW`.
- Observe Screen, Menu, packet and Server thread stages independently.
- Build Interaction Tree from Screen children plus separate Menu Slot projection.
- Observe 26.2 Render Facts at GuiRenderState submission methods.
- Use the 26.2 GPU screenshot abstraction for both OpenGL and Vulkan.

## 后果

- Private input entry points require Invokers.
- Fabric container positioning requires an Accessor.
- Server validation hooks must occur after PacketUtils thread scheduling.
- Render Facts do not imply business semantics.
