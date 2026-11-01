package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.client.ExtensionPacksScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TransferableSelectionList.PackEntry.class)
public abstract class ExtensionPackRowMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void mineCraftProtocol$tint(
            GuiGraphics graphics,
            int index,
            int y,
            int x,
            int width,
            int height,
            int mx,
            int my,
            boolean hovered,
            float delta,
            CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof ExtensionPacksScreen screen) {
            var row = (TransferableSelectionList.PackEntry) (Object) this;
            var state = screen.entry(row.getPackId());
            if (state != null && (!state.available() || state.pending()))
                graphics.fill(
                        x, y, x + width, y + height, !state.available() ? 0x66FF4444 : 0x66FFFF44);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void mineCraftProtocol$details(
            GuiGraphics graphics,
            int index,
            int y,
            int x,
            int width,
            int height,
            int mx,
            int my,
            boolean hovered,
            float delta,
            CallbackInfo ci) {
        if (hovered && Minecraft.getInstance().screen instanceof ExtensionPacksScreen screen) {
            var row = (TransferableSelectionList.PackEntry) (Object) this;
            var state = screen.entry(row.getPackId());
            if (state != null)
                graphics.renderTooltip(
                        Minecraft.getInstance().font,
                        Minecraft.getInstance()
                                .font
                                .split(
                                        net.minecraft.network.chat.Component.literal(
                                                state.detail()
                                                        + "\n权限："
                                                        + String.join(
                                                                ", ",
                                                                state.source()
                                                                        .manifest()
                                                                        .requestedScopes())),
                                        Math.min(260, Math.max(1, screen.width - 24))),
                        mx,
                        my);
        }
    }
}
