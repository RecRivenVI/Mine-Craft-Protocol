package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerClockManager.class)
public interface ClockManagerAccessor {
    @Accessor("clocks")
    Map<Holder<WorldClock>, ?> minecraftProtocol$clocks();
}
