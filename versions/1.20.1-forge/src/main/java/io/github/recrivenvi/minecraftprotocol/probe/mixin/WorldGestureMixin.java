package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.ForgeProbeRuntime;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
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
        if (!ForgeProbeRuntime.allowSemanticWorldInput("break")) cir.setReturnValue(false);
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void minecraftProtocolProbe$beforeDestroy(
            BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ForgeProbeRuntime.beforeSemanticDestroy(pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void minecraftProtocolProbe$afterDestroy(
            BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ForgeProbeRuntime.afterSemanticDestroy(pos, cir.getReturnValue());
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$guardUseOn(CallbackInfoReturnable<InteractionResult> cir) {
        if (!ForgeProbeRuntime.allowSemanticWorldInput("use_on"))
            cir.setReturnValue(InteractionResult.PASS);
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void minecraftProtocolProbe$useOnResult(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        ForgeProbeRuntime.semanticUseOnResult(player, hand, hit, cir.getReturnValue());
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$guardUse(CallbackInfoReturnable<InteractionResult> cir) {
        if (!ForgeProbeRuntime.allowSemanticWorldInput("use"))
            cir.setReturnValue(InteractionResult.PASS);
    }

    @Inject(
            method = {"interact", "interactAt"},
            at = @At("HEAD"),
            cancellable = true)
    private void minecraftProtocolProbe$guardInteract(
            CallbackInfoReturnable<InteractionResult> cir) {
        if (!ForgeProbeRuntime.allowSemanticWorldInput("interact"))
            cir.setReturnValue(InteractionResult.PASS);
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocolProbe$guardAttack(CallbackInfo ci) {
        if (!ForgeProbeRuntime.allowSemanticWorldInput("attack")) ci.cancel();
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void minecraftProtocolProbe$attackConsumed(
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.entity.Entity target,
            CallbackInfo ci) {
        ForgeProbeRuntime.nativeEntityAttackCompleted(player, target);
    }

    @Inject(method = "releaseUsingItem", at = @At("RETURN"))
    private void minecraftProtocolProbe$released(CallbackInfo ci) {
        ForgeProbeRuntime.semanticUseReleased();
    }
}
