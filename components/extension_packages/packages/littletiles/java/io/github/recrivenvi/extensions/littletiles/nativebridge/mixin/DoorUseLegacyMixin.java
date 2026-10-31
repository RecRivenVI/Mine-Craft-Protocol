package io.github.recrivenvi.extensions.littletiles.nativebridge.mixin;

import io.github.recrivenvi.extensions.littletiles.nativebridge.NativeHooks;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The pinned Forge release includes InteractionHand in the native use callback. */
@Pseudo
@Mixin(
        targets = "team.creative.littletiles.common.structure.type.animation.LittleDoor",
        remap = false)
public abstract class DoorUseLegacyMixin {
    @Inject(method = "use", at = @At("HEAD"))
    private void minecraftProtocol$enter(
            @Coerce Object level,
            @Coerce Object focus,
            @Coerce Object pos,
            @Coerce Object player,
            @Coerce Object hit,
            @Coerce Object hand,
            CallbackInfoReturnable<Object> result) {
        NativeHooks.record("enter", level, this, focus, null);
    }

    @Inject(method = "use", at = @At("RETURN"))
    private void minecraftProtocol$return(
            @Coerce Object level,
            @Coerce Object focus,
            @Coerce Object pos,
            @Coerce Object player,
            @Coerce Object hit,
            @Coerce Object hand,
            CallbackInfoReturnable<Object> result) {
        NativeHooks.record("return", level, this, focus, result.getReturnValue());
    }
}
