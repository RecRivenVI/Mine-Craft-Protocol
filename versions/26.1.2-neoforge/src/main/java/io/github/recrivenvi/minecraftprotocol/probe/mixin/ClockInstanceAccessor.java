package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.world.clock.ServerClockManager$ClockInstance")
public interface ClockInstanceAccessor {
    @Accessor("paused")
    boolean minecraftProtocol$paused();

    @Accessor("rate")
    float minecraftProtocol$rate();
}
