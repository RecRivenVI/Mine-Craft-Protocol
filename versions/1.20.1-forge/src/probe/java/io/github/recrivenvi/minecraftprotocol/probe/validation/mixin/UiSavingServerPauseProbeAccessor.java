package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(IntegratedServer.class)
public interface UiSavingServerPauseProbeAccessor {
    @Accessor("paused")
    boolean minecraftProtocolProbe$paused();
}
