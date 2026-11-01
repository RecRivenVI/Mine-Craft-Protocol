package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.client.ExtensionPacksScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.packs.TransferableSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TransferableSelectionList.PackEntry.class)
public abstract class ExtensionPackRowMixin {
    @Inject(method = "extractContent", at = @At("HEAD"))
    private void mineCraftProtocol$tint(
            GuiGraphicsExtractor graphics,
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
                        row.getX(),
                        row.getY(),
                        row.getX() + row.getWidth(),
                        row.getY() + row.getHeight(),
                        !state.available() ? 0x66FF4444 : 0x66FFFF44);
        }
    }

    @Inject(method = "extractContent", at = @At("TAIL"))
    private void mineCraftProtocol$details(
            GuiGraphicsExtractor graphics,
            int mx,
            int my,
            boolean hovered,
            float delta,
            CallbackInfo ci) {
        if (hovered && Minecraft.getInstance().screen instanceof ExtensionPacksScreen screen) {
            var row = (TransferableSelectionList.PackEntry) (Object) this;
            var state = screen.entry(row.getPackId());
            if (state != null)
                graphics.setTooltipForNextFrame(
                        Minecraft.getInstance().font,
                        Minecraft.getInstance()
                                .font
                                .split(
                                        ExtensionPacksScreen.detail(state)
                                                .copy()
                                                .append("\n")
                                                .append(
                                                        net.minecraft.network.chat.Component
                                                                .translatable(
                                                                        io.github.recrivenvi
                                                                                        .minecraftprotocol
                                                                                        .ProtocolIdentity
                                                                                        .MOD_ID
                                                                                + ".extensionpacks.permissions",
                                                                        String.join(
                                                                                ", ",
                                                                                state.source()
                                                                                        .manifest()
                                                                                        .requestedScopes()))),
                                        Math.min(260, Math.max(1, screen.width - 24))),
                        mx,
                        my);
        }
    }
}
