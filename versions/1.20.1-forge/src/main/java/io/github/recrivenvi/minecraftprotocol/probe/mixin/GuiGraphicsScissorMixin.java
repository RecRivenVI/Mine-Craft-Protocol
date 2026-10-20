package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiNativeText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsScissorMixin implements UiNativeText.ScissorSource {
    @Unique private volatile UiNativeText.ScissorSample minecraftProtocolProbe$scissor;

    @Inject(
            method =
                    "<init>(Lnet/minecraft/client/Minecraft;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
            at = @At("RETURN"))
    private void minecraftProtocolProbe$initialScissor(CallbackInfo ci) {
        minecraftProtocolProbe$scissor =
                new UiNativeText.ScissorSample(
                        this, null, UiNativeText.ScissorOrigin.NATIVE_INITIAL_EMPTY_STACK);
    }

    @Inject(
            method = "applyScissor(Lnet/minecraft/client/gui/navigation/ScreenRectangle;)V",
            at = @At("RETURN"))
    private void minecraftProtocolProbe$appliedScissor(ScreenRectangle rectangle, CallbackInfo ci) {
        minecraftProtocolProbe$scissor =
                new UiNativeText.ScissorSample(
                        this, rectangle, UiNativeText.ScissorOrigin.NATIVE_APPLY_SCISSOR_RETURN);
    }

    @Override
    public UiNativeText.ScissorSample minecraftProtocolProbe$scissor() {
        return minecraftProtocolProbe$scissor;
    }
}
