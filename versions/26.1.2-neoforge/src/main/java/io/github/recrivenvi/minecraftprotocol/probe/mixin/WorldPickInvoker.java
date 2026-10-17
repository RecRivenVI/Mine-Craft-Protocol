package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Minecraft.class)
public interface WorldPickInvoker {
    @Invoker("pick")
    void minecraftProtocolProbe$pick(float partialTick);
}
