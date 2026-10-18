package io.github.recrivenvi.minecraftprotocol.probe.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

/** No-pixel sink for the actual native SystemToast render lifecycle, not a visual assertion. */
public final class UiToastLifecycleProbeGraphics extends GuiGraphics {
    public UiToastLifecycleProbeGraphics(Minecraft client) {
        super(client, client.renderBuffers().bufferSource());
    }

    @Override
    public void blitSprite(ResourceLocation sprite, int x, int y, int width, int height) {}

    @Override
    public void blitSprite(
            ResourceLocation sprite,
            int textureWidth,
            int textureHeight,
            int u,
            int v,
            int x,
            int y,
            int width,
            int height) {}

    @Override
    public int drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        return 0;
    }

    @Override
    public int drawString(
            Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        return 0;
    }
}
