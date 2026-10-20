package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.StringDecomposer;
import org.joml.Matrix4f;

/** Native draw argument decoding; no player/profile/score cache is read. */
public final class UiNativeText {
    private UiNativeText() {}

    private record TooltipContext(GuiGraphics graphics, ClientTooltipComponent component) {}

    private static final ThreadLocal<TooltipContext> TOOLTIP = new ThreadLocal<>();

    public static String chatFamily() {
        return Minecraft.getInstance().screen instanceof ChatScreen
                ? "chat_focused"
                : "chat_background";
    }

    public static void component(
            String family,
            GuiGraphics graphics,
            Font font,
            Component text,
            int x,
            int y,
            int color) {
        sequence(family, graphics, font, text.getVisualOrderText(), x, y, color);
    }

    public static void literal(
            String family, GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        observe(
                family,
                graphics,
                font,
                plain(text),
                x,
                y,
                font.width(text),
                color,
                graphics.pose().last().pose());
    }

    public static void sequence(
            String family,
            GuiGraphics graphics,
            Font font,
            FormattedCharSequence text,
            int x,
            int y,
            int color) {
        observe(
                family,
                graphics,
                font,
                plain(text),
                x,
                y,
                font.width(text),
                color,
                graphics.pose().last().pose());
    }

    private static void observe(
            String family,
            GuiGraphics graphics,
            Font font,
            String text,
            double x,
            double y,
            int width,
            int color,
            Matrix4f pose) {
        ScissorSample clip = scissor(graphics);
        if (clip == null) return;
        var frame = UiPresentationSnapshot.activeGuiPresentation();
        if (frame != null)
            frame.nativeText(
                    family,
                    text,
                    x,
                    y,
                    width,
                    font.lineHeight,
                    drawOpacity(color),
                    pose,
                    clip.rectangle());
    }

    public static void nausea(
            GuiGraphics graphics, String resource, int x, int y, int depth, int width, int height) {
        ScissorSample clip = scissor(graphics);
        if (clip == null) return;
        var frame = UiPresentationSnapshot.activeGuiPresentation();
        if (frame == null) return;
        float alpha = RenderSystem.getShaderColor()[3];
        if (!Float.isFinite(alpha) || alpha <= 0 || alpha > 1) return;
        var pose = graphics.pose().last().pose();
        if (pose.m03() != 0 || pose.m13() != 0 || pose.m23() != 0 || pose.m33() != 1) return;
        frame.cameraOverlay(
                "nausea",
                resource,
                x,
                y,
                width,
                height,
                (Math.round(alpha * 255) << 24) | 0xFFFFFF,
                pose.m00(),
                pose.m01(),
                pose.m10(),
                pose.m11(),
                pose.m20() * depth + pose.m30(),
                pose.m21() * depth + pose.m31(),
                clip.rectangle());
    }

    private static float drawOpacity(int color) {
        float shaderAlpha = RenderSystem.getShaderColor()[3];
        return Float.isFinite(shaderAlpha) && shaderAlpha >= 0 && shaderAlpha <= 1
                ? opacity(color) * shaderAlpha
                : 0;
    }

    public static void tooltipDraw(
            GuiGraphics graphics, ClientTooltipComponent component, Runnable nativeDraw) {
        TooltipContext previous = TOOLTIP.get();
        TOOLTIP.set(new TooltipContext(graphics, component));
        try {
            nativeDraw.run();
        } finally {
            if (previous == null) TOOLTIP.remove();
            else TOOLTIP.set(previous);
        }
    }

    public static void tooltipText(
            Object component,
            Font font,
            FormattedCharSequence text,
            float x,
            float y,
            int color,
            Matrix4f pose) {
        TooltipContext context = TOOLTIP.get();
        if (context == null || context.component() != component) return;
        observe(
                "tooltip",
                context.graphics(),
                font,
                plain(text),
                x,
                y,
                font.width(text),
                color,
                pose);
    }

    public enum ScissorOrigin {
        NATIVE_INITIAL_EMPTY_STACK,
        NATIVE_APPLY_SCISSOR_RETURN
    }

    public record ScissorSample(Object owner, ScreenRectangle rectangle, ScissorOrigin origin) {}

    public interface ScissorSource {
        ScissorSample minecraftProtocolProbe$scissor();
    }

    public static ScissorSample scissor(GuiGraphics graphics) {
        return graphics instanceof ScissorSource source
                ? validScissor(graphics, source.minecraftProtocolProbe$scissor())
                : null;
    }

    static ScissorSample validScissor(Object owner, ScissorSample sample) {
        return owner != null && sample != null && sample.owner() == owner && sample.origin() != null
                ? sample
                : null;
    }

    public static float opacity(int color) {
        // Font.adjustColor promotes legacy RGB/low-alpha values before its actual draw.
        return ((color & 0xFC000000) == 0 ? 255 : color >>> 24) / 255.0F;
    }

    public static String plain(String text) {
        return plain(sink -> StringDecomposer.iterateFormatted(text, Style.EMPTY, sink));
    }

    static String plain(FormattedCharSequence text) {
        StringBuilder label = new StringBuilder();
        boolean[] obfuscated = {false};
        text.accept(
                (index, style, codepoint) -> {
                    if (style.isObfuscated()) {
                        obfuscated[0] = true;
                        return false;
                    }
                    if (label.length() >= 2048) return false;
                    label.appendCodePoint(codepoint);
                    return true;
                });
        return obfuscated[0] ? "" : label.toString();
    }
}
