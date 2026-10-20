package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.UiReconfigurationFixture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class UiReconfigurationClientProbeMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void minecraftProtocolProbe$observeOwnedReconfiguration(CallbackInfo callback) {
        UiReconfigurationFixture.observeClientTick();
    }
}
