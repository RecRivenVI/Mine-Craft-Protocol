package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.core.EnvironmentControlRevisions;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.ServerClockManager;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerClockManager.class)
abstract class ClockControlRevisionMixin {
    @Inject(method = "modifyClock", at = @At("RETURN"))
    private void minecraftProtocol$recordClockEdit(
            Holder<WorldClock> clock, Consumer<?> mutation, CallbackInfo callback) {
        EnvironmentControlRevisions.timeEdited(
                ((ClockManagerAccessor) this).minecraftProtocol$clocks().get(clock));
    }
}
