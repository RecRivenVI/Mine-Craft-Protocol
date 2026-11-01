package io.github.recrivenvi.minecraftprotocol.extensions.create.mixin;

import io.github.recrivenvi.minecraftprotocol.extensions.create.bridge.NativeHooks;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(
        targets = "com.simibubi.create.content.contraptions.bearing.MechanicalBearingBlock",
        remap = false)
public abstract class BearingUseMixin1_21_1NeoForge {
    @Inject(method = "useItemOn", at = @At("HEAD"))
    private void minecraftProtocol$entered(
            @Coerce Object stack,
            @Coerce Object state,
            @Coerce Object level,
            @Coerce Object pos,
            @Coerce Object player,
            @Coerce Object hand,
            @Coerce Object hit,
            CallbackInfoReturnable<Object> result) {
        NativeHooks.entered(level);
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void minecraftProtocol$returned(
            @Coerce Object stack,
            @Coerce Object state,
            @Coerce Object level,
            @Coerce Object pos,
            @Coerce Object player,
            @Coerce Object hand,
            @Coerce Object hit,
            CallbackInfoReturnable<Object> result) {
        NativeHooks.returned(level, result.getReturnValue());
    }
}
