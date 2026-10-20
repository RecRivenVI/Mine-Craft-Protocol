package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.UiPresentationSnapshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ForgeGui.class)
public abstract class ForgeHudSleepPresentationMixin {
    @Redirect(
            method = "renderSleepFade",
            remap = false,
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V",
                            remap = true))
    private void minecraftProtocolProbe$sleep(
            GuiGraphics graphics,
            RenderType renderType,
            int left,
            int top,
            int right,
            int bottom,
            int color) {
        graphics.fill(renderType, left, top, right, bottom, color);
        var frame = UiPresentationSnapshot.activeHudPresentation();
        if (frame == null) return;
        var pose = graphics.pose().last().pose();
        if (pose.m03() != 0 || pose.m13() != 0 || pose.m23() != 0 || pose.m33() != 1) return;
        frame.cameraOverlay(
                "sleep",
                "",
                left,
                top,
                right - left,
                bottom - top,
                color,
                pose.m00(),
                pose.m01(),
                pose.m10(),
                pose.m11(),
                pose.m30(),
                pose.m31());
    }
}
