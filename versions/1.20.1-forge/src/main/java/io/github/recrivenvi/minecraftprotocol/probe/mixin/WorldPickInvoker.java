package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface WorldPickInvoker {
    @Invoker("pick")
    void minecraftProtocolProbe$pick(float partialTick);
}
