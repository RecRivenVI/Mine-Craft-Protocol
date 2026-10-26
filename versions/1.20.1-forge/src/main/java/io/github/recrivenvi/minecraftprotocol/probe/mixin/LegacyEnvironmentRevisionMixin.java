package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.core.EnvironmentControlRevisions;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerLevel.class)
abstract class LegacyEnvironmentRevisionMixin {
    @Unique private boolean minecraftProtocol$naturalDayTimeUpdate;

    // Vanilla calls this same setter for ordinary day ticks; only the exact natural call is
    // excluded.
    @Redirect(
            method = "tickTime",
            at =
                    @At(
                            value = "INVOKE",
                            target = "Lnet/minecraft/server/level/ServerLevel;setDayTime(J)V"))
    private void minecraftProtocol$advanceNaturalTime(ServerLevel level, long ticks) {
        minecraftProtocol$naturalDayTimeUpdate = true;
        try {
            level.setDayTime(ticks);
        } finally {
            minecraftProtocol$naturalDayTimeUpdate = false;
        }
    }

    @Inject(method = "setDayTime", at = @At("RETURN"))
    private void minecraftProtocol$recordClockEdit(long ticks, CallbackInfo callback) {
        if (!minecraftProtocol$naturalDayTimeUpdate) EnvironmentControlRevisions.timeEdited(this);
    }

    @Inject(method = "setWeatherParameters", at = @At("RETURN"))
    private void minecraftProtocol$recordWeatherEdit(
            int clearTime,
            int weatherTime,
            boolean raining,
            boolean thundering,
            CallbackInfo callback) {
        EnvironmentControlRevisions.weatherEdited(this);
    }
}
