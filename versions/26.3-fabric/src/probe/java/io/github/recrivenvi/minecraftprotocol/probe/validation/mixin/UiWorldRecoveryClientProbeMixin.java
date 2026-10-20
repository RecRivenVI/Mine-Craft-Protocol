package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.UiWorldRecoveryFixtureScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class UiWorldRecoveryClientProbeMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void minecraftProtocolProbe$observeOwnedSourceWorld(CallbackInfo callback) {
        UiWorldRecoveryFixtureScreen.observeClientTick();
    }
}
