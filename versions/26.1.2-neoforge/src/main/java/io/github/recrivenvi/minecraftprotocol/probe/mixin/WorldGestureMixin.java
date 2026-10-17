package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.NeoForgeProbeRuntime;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Only an owned semantic gesture may veto a changed target; ordinary Vanilla input passes through.
 */
@Mixin(MultiPlayerGameMode.class)
public abstract class WorldGestureMixin {
    @Inject(
            method = {"startDestroyBlock", "continueDestroyBlock", "destroyBlock"},
            at = @At("HEAD"),
            cancellable = true)
    private void minecraftProtocolProbe$guardBreak(CallbackInfoReturnable<Boolean> cir) {
        if (!NeoForgeProbeRuntime.allowSemanticWorldInput("break")) cir.setReturnValue(false);
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void minecraftProtocolProbe$beforeDestroy(
            BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        NeoForgeProbeRuntime.beforeSemanticDestroy(pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void minecraftProtocolProbe$afterDestroy(
            BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        NeoForgeProbeRuntime.afterSemanticDestroy(pos, cir.getReturnValue());
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$guardUseOn(CallbackInfoReturnable<InteractionResult> cir) {
        if (!NeoForgeProbeRuntime.allowSemanticWorldInput("use_on"))
            cir.setReturnValue(InteractionResult.PASS);
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$guardUse(CallbackInfoReturnable<InteractionResult> cir) {
        if (!NeoForgeProbeRuntime.allowSemanticWorldInput("use"))
            cir.setReturnValue(InteractionResult.PASS);
    }

    @Inject(method = "interact", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$guardInteract(
            CallbackInfoReturnable<InteractionResult> cir) {
        if (!NeoForgeProbeRuntime.allowSemanticWorldInput("interact"))
            cir.setReturnValue(InteractionResult.PASS);
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$guardAttack(CallbackInfo ci) {
        if (!NeoForgeProbeRuntime.allowSemanticWorldInput("attack")) ci.cancel();
    }

    @Inject(method = "releaseUsingItem", at = @At("RETURN"))
    private void minecraftProtocolProbe$released(CallbackInfo ci) {
        NeoForgeProbeRuntime.semanticUseReleased();
    }
}
