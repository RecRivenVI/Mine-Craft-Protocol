package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.core.EnvironmentControlRevisions;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftServer.class)
abstract class WeatherControlRevisionMixin {
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
