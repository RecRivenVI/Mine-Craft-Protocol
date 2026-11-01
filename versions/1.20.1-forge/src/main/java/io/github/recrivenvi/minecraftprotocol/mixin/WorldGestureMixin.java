package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.ForgeRuntime;
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
    private void mineCraftProtocol$guardBreak(CallbackInfoReturnable<Boolean> cir) {
        if (!ForgeRuntime.allowSemanticWorldInput("break")) cir.setReturnValue(false);
    }

    @Inject(method = "destroyBlock", at = @At("HEAD"))
    private void mineCraftProtocol$beforeDestroy(
            BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ForgeRuntime.beforeSemanticDestroy(pos);
    }

    @Inject(method = "destroyBlock", at = @At("RETURN"))
    private void mineCraftProtocol$afterDestroy(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        ForgeRuntime.afterSemanticDestroy(pos, cir.getReturnValue());
    }

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void mineCraftProtocol$guardUseOn(CallbackInfoReturnable<InteractionResult> cir) {
        if (!ForgeRuntime.allowSemanticWorldInput("use_on"))
            cir.setReturnValue(InteractionResult.PASS);
    }

    @Inject(method = "useItemOn", at = @At("RETURN"))
    private void mineCraftProtocol$useOnResult(
            LocalPlayer player,
            InteractionHand hand,
            BlockHitResult hit,
            CallbackInfoReturnable<InteractionResult> cir) {
        ForgeRuntime.semanticUseOnResult(player, hand, hit, cir.getReturnValue());
    }

    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void mineCraftProtocol$guardUse(CallbackInfoReturnable<InteractionResult> cir) {
        if (!ForgeRuntime.allowSemanticWorldInput("use"))
            cir.setReturnValue(InteractionResult.PASS);
    }

    @Inject(
            method = {"interact", "interactAt"},
            at = @At("HEAD"),
            cancellable = true)
    private void mineCraftProtocol$guardInteract(CallbackInfoReturnable<InteractionResult> cir) {
        if (!ForgeRuntime.allowSemanticWorldInput("interact"))
            cir.setReturnValue(InteractionResult.PASS);
    }

    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void mineCraftProtocol$guardAttack(CallbackInfo ci) {
        if (!ForgeRuntime.allowSemanticWorldInput("attack")) ci.cancel();
    }

    @Inject(method = "attack", at = @At("RETURN"))
    private void mineCraftProtocol$attackConsumed(
            net.minecraft.world.entity.player.Player player,
            net.minecraft.world.entity.Entity target,
            CallbackInfo ci) {
        ForgeRuntime.nativeEntityAttackCompleted(player, target);
    }

    @Inject(method = "releaseUsingItem", at = @At("RETURN"))
    private void mineCraftProtocol$released(CallbackInfo ci) {
        ForgeRuntime.semanticUseReleased();
    }
}
