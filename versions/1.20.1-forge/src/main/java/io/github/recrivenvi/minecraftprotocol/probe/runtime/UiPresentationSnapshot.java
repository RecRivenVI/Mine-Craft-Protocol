package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.core.UiToastDraw;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.*;
import io.github.recrivenvi.minecraftprotocol.safety.UiHudIcons;
import io.github.recrivenvi.minecraftprotocol.safety.UiRenderedIcons;
import io.github.recrivenvi.minecraftprotocol.safety.UiRenderedText;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.*;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.*;
import org.joml.Matrix4fc;

/** Bounded read-only HUD/Toast facts; no artificial click targets for noninteractive content. */
public final class UiPresentationSnapshot {
    public interface HudPresentationSource {
        RenderedHud minecraftProtocolProbe$hudPresentation();
    }

    public interface GuiPresentationSource {
        RenderedHud minecraftProtocolProbe$guiPresentation();
    }

    public interface PlayerListPresentationSource {
        RenderedPlayerList minecraftProtocolProbe$playerListPresentation();
    }

    /** Native row draws have their own bounded frame; they do not consume the late-text budget. */
    public static final class RenderedPlayerList {
        private static final int MAX_ROWS = 80;
        private static final int MAX_PARTS = 64;
        private final RenderedHud geometry = new RenderedHud(1);
        private final List<JsonObject> rows = new ArrayList<>();
        private final List<JsonObject> header = new ArrayList<>();
        private final List<JsonObject> footer = new ArrayList<>();
        private WeakReference<Object> connection = new WeakReference<>(null);
        private WeakReference<Object> owner = new WeakReference<>(null);
        private JsonObject currentRow;
        private int rowParts;
        private int renderDepth;
        private boolean overflow;
        private volatile Completed completed;

        private record Completed(
                RenderedHud.Completed context,
                WeakReference<Object> connection,
                WeakReference<Object> owner,
                List<JsonObject> nodes) {}

        public static String plain(FormattedCharSequence text) {
            return UiNativeText.plain(text);
        }

        public void begin(Minecraft client, Object nativeOwner) {
            begin(
                    client.level,
                    client.getCameraEntity(),
                    client.getConnection(),
                    nativeOwner,
                    client.screen,
                    client.getOverlay(),
                    client.getWindow().getGuiScaledWidth(),
                    client.getWindow().getGuiScaledHeight());
        }

        void begin(
                Object world,
                Object camera,
                Object currentConnection,
                Object nativeOwner,
                Object screen,
                Object overlay,
                int width,
                int height) {
            completed = null;
            if (renderDepth++ > 0) {
                overflow = true;
                return;
            }
            rows.clear();
            header.clear();
            footer.clear();
            currentRow = null;
            rowParts = 0;
            overflow =
                    world == null
                            || camera == null
                            || currentConnection == null
                            || nativeOwner == null;
            connection = new WeakReference<>(currentConnection);
            owner = new WeakReference<>(nativeOwner);
            geometry.beginGui(world, camera, screen, overlay, width, height);
        }

        private JsonObject text(
                String text,
                int x,
                int y,
                int width,
                int height,
                float opacity,
                Matrix4fc pose,
                ScreenRectangle scissor) {
            if (renderDepth != 1 || overflow) return null;
            geometry.pending.clear();
            geometry.nativeText("playerlist", text, x, y, width, height, opacity, pose, scissor);
            if (geometry.pending.isEmpty()) return null;
            JsonObject result = geometry.pending.values().iterator().next().deepCopy();
            geometry.pending.clear();
            result.addProperty("renderPhase", "player_list");
            result.addProperty("labelSource", "last_completed_native_player_list_draw");
            result.addProperty("iconSource", "last_completed_native_player_list_draw");
            return result;
        }

        public void name(
                String text,
                int x,
                int y,
                int width,
                int height,
                float opacity,
                Matrix4fc pose,
                ScreenRectangle scissor) {
            if (renderDepth != 1 || overflow) return;
            if (rows.size() == MAX_ROWS) {
                overflow = true;
                return;
            }
            String key = "hud.playerlist.row." + rows.size();
            JsonObject drawn = text(text, x, y, width, height, opacity, pose, scissor);
            currentRow = drawn == null ? new JsonObject() : drawn;
            currentRow.addProperty("nodeId", key);
            currentRow.addProperty("controlKey", key);
            currentRow.addProperty("entryIndex", rows.size());
            currentRow.addProperty("role", "hud_text");
            if (drawn == null) {
                currentRow.addProperty("label", "");
                currentRow.addProperty("boundsAvailable", false);
                currentRow.addProperty("visible", false);
                currentRow.addProperty("active", false);
                currentRow.addProperty("textPhase", "unobserved");
                currentRow.add("actions", new JsonArray());
            }
            currentRow.addProperty("labelSource", "last_completed_native_player_list_draw");
            currentRow.addProperty("iconSource", "last_completed_native_player_list_draw");
            currentRow.addProperty("renderPhase", "player_list");
            currentRow.add("children", new JsonArray());
            rows.add(currentRow);
            rowParts = 0;
        }

        public void header(
                String text,
                int x,
                int y,
                int width,
                int height,
                float opacity,
                Matrix4fc pose,
                ScreenRectangle scissor) {
            paragraph(
                    header,
                    "hud.playerlist.header",
                    text,
                    x,
                    y,
                    width,
                    height,
                    opacity,
                    pose,
                    scissor);
        }

        public void footer(
                String text,
                int x,
                int y,
                int width,
                int height,
                float opacity,
                Matrix4fc pose,
                ScreenRectangle scissor) {
            paragraph(
                    footer,
                    "hud.playerlist.footer",
                    text,
                    x,
                    y,
                    width,
                    height,
                    opacity,
                    pose,
                    scissor);
        }

        private void paragraph(
                List<JsonObject> parts,
                String key,
                String value,
                int x,
                int y,
                int width,
                int height,
                float opacity,
                Matrix4fc pose,
                ScreenRectangle scissor) {
            if (renderDepth != 1 || overflow) return;
            if (parts.size() == MAX_PARTS) {
                overflow = true;
                return;
            }
            JsonObject part = text(value, x, y, width, height, opacity, pose, scissor);
            if (part == null) return;
            identifyPart(part, key + ".part." + parts.size(), "hud_text");
            parts.add(part);
        }

        public void score(
                String value,
                int x,
                int y,
                int width,
                int height,
                float opacity,
                Matrix4fc pose,
                ScreenRectangle scissor) {
            if (!nextPart()) return;
            JsonObject part = text(value, x, y, width, height, opacity, pose, scissor);
            if (part == null) return;
            identifyPart(part, partKey(), "hud_text");
            part.addProperty("labelSource", "last_completed_native_player_list_draw");
            currentRow.getAsJsonArray("children").add(part);
        }

        public void atlas(
                String texture,
                int x,
                int y,
                int u,
                int v,
                int width,
                int height,
                float opacity,
                Matrix4fc pose,
                ScreenRectangle scissor) {
            if (!texture.equals("minecraft:textures/gui/icons.png")
                    || width <= 0
                    || height <= 0
                    || u < 0
                    || v < 0
                    || u > 256 - width
                    || v > 256 - height
                    || !nextPart()) return;
            JsonObject part = text(texture, x, y, width, height, opacity, pose, scissor);
            if (part == null) return;
            identifyPart(part, partKey(), "status_icon");
            part.remove("labelSource");
            part.addProperty("label", "");
            part.addProperty("textureId", texture);
            part.addProperty("textureU", u);
            part.addProperty("textureV", v);
            part.addProperty("atlasWidth", 256);
            part.addProperty("atlasHeight", 256);
            part.addProperty("spriteSemantics", "native_gui_icons_atlas_region");
            part.addProperty("iconSource", "last_completed_native_player_list_draw");
            currentRow.getAsJsonArray("children").add(part);
        }

        private boolean nextPart() {
            if (renderDepth != 1 || overflow || currentRow == null) return false;
            if (rowParts++ == MAX_PARTS) {
                overflow = true;
                return false;
            }
            return true;
        }

        private String partKey() {
            return currentRow.get("controlKey").getAsString() + ".part." + (rowParts - 1);
        }

        private static void identifyPart(JsonObject node, String key, String role) {
            node.addProperty("nodeId", key);
            node.addProperty("controlKey", key);
            node.addProperty("role", role);
        }

        private static JsonObject paragraphNode(String key, List<JsonObject> parts) {
            if (parts.isEmpty()) return null;
            JsonObject node = parts.get(0).deepCopy();
            identifyPart(node, key, "hud_text");
            StringBuilder label = new StringBuilder();
            JsonArray children = new JsonArray();
            double left = Double.POSITIVE_INFINITY, top = Double.POSITIVE_INFINITY;
            double right = Double.NEGATIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
            for (JsonObject part : parts) {
                if (!label.isEmpty()) label.append('\n');
                label.append(part.get("label").getAsString());
                children.add(part.deepCopy());
                left = Math.min(left, part.get("x").getAsDouble());
                top = Math.min(top, part.get("y").getAsDouble());
                right =
                        Math.max(
                                right,
                                part.get("x").getAsDouble() + part.get("width").getAsDouble());
                bottom =
                        Math.max(
                                bottom,
                                part.get("y").getAsDouble() + part.get("height").getAsDouble());
            }
            node.addProperty("label", UiRenderedText.bounded(label.toString()));
            node.addProperty("x", left);
            node.addProperty("y", top);
            node.addProperty("width", right - left);
            node.addProperty("height", bottom - top);
            node.addProperty("labelSource", "last_completed_native_player_list_draw");
            node.add("children", children);
            return node;
        }

        public void finish(long now) {
            if (renderDepth == 0 || --renderDepth != 0) return;
            geometry.pending.clear();
            geometry.finish(now);
            if (overflow || geometry.completed == null) return;
            List<JsonObject> result = new ArrayList<>();
            JsonObject first = paragraphNode("hud.playerlist.header", header);
            if (first != null) result.add(first);
            for (JsonObject row : rows) {
                if (!row.get("visible").getAsBoolean() && !row.getAsJsonArray("children").isEmpty())
                    row.addProperty("visible", true);
                result.add(row.deepCopy());
            }
            JsonObject last = paragraphNode("hud.playerlist.footer", footer);
            if (last != null) result.add(last);
            completed = new Completed(geometry.completed, connection, owner, List.copyOf(result));
            currentRow = null;
        }

        JsonArray sample(
                Object world,
                Object camera,
                Object currentConnection,
                Object nativeOwner,
                Object screen,
                Object overlay,
                int width,
                int height,
                long now,
                boolean visible) {
            JsonArray result = new JsonArray();
            Completed frame = completed;
            if (!visible
                    || frame == null
                    || currentConnection == null
                    || nativeOwner == null
                    || frame.connection.get() != currentConnection
                    || frame.owner.get() != nativeOwner) return result;
            var context = frame.context;
            if (context.world.get() != world
                    || context.camera.get() != camera
                    || context.screen.get() != screen
                    || context.overlay.get() != overlay
                    || context.worldPresent != (world != null)
                    || context.cameraPresent != (camera != null)
                    || context.screenPresent != (screen != null)
                    || context.overlayPresent != (overlay != null)
                    || width != context.width
                    || height != context.height
                    || now < context.capturedAt
                    || now - context.capturedAt > 250_000_000L) return result;
            for (JsonObject row : frame.nodes) result.add(row.deepCopy());
            return result;
        }
    }

    /** A bounded frame of native draw callbacks, not private player or item statistics. */
    public static final class RenderedHud {
        private final Map<String, JsonObject> pending = new LinkedHashMap<>();
        private final int maximumNodes;
        private WeakReference<Object> world = new WeakReference<>(null);
        private WeakReference<Object> camera = new WeakReference<>(null);
        private WeakReference<Object> screen = new WeakReference<>(null);
        private WeakReference<Object> overlay = new WeakReference<>(null);
        private boolean worldPresent;
        private boolean cameraPresent;
        private boolean screenPresent;
        private boolean overlayPresent;
        private int textIndex;
        private int width;
        private int height;
        private boolean collecting;
        private boolean overflow;
        private volatile Completed completed;

        private record Completed(
                WeakReference<Object> world,
                WeakReference<Object> camera,
                WeakReference<Object> screen,
                WeakReference<Object> overlay,
                boolean worldPresent,
                boolean cameraPresent,
                boolean screenPresent,
                boolean overlayPresent,
                int width,
                int height,
                long capturedAt,
                List<JsonObject> nodes) {}

        public RenderedHud() {
            this(32);
        }

        public RenderedHud(int maximumNodes) {
            if (maximumNodes < 1 || maximumNodes > 256)
                throw new IllegalArgumentException("Invalid native frame budget");
            this.maximumNodes = maximumNodes;
        }

        public void begin(Minecraft client) {
            beginNative(
                    client.level,
                    client.getCameraEntity(),
                    client.screen,
                    client.getOverlay(),
                    client.getWindow().getGuiScaledWidth(),
                    client.getWindow().getGuiScaledHeight(),
                    false);
        }

        void begin(Object level, Object owner, Object currentScreen, int w, int h) {
            beginNative(level, owner, currentScreen, null, w, h, false);
        }

        public void beginGui(Minecraft client) {
            beginGui(
                    client.level,
                    client.getCameraEntity(),
                    client.screen,
                    client.getOverlay(),
                    client.getWindow().getGuiScaledWidth(),
                    client.getWindow().getGuiScaledHeight());
        }

        void beginGui(
                Object level,
                Object owner,
                Object currentScreen,
                Object currentOverlay,
                int w,
                int h) {
            beginNative(level, owner, currentScreen, currentOverlay, w, h, true);
        }

        private void beginNative(
                Object level,
                Object owner,
                Object currentScreen,
                Object currentOverlay,
                int w,
                int h,
                boolean guiFrame) {
            completed = null;
            pending.clear();
            overflow = false;
            textIndex = 0;
            collecting = (guiFrame || level != null && owner != null) && w > 0 && h > 0;
            world = new WeakReference<>(level);
            camera = new WeakReference<>(owner);
            screen = new WeakReference<>(currentScreen);
            screenPresent = currentScreen != null;
            overlay = new WeakReference<>(currentOverlay);
            overlayPresent = currentOverlay != null;
            worldPresent = level != null;
            cameraPresent = owner != null;
            width = w;
            height = h;
        }

        public void selectedName(String label, int x, int y, int w, int color) {
            renderedText("hud.selected_item_name", label, x, y, w, color);
        }

        public void experienceLevel(String label, int x, int y, int w) {
            renderedText("hud.context.experience.level", label, x, y, w, 0xFFFFFFFF);
        }

        private void renderedText(String key, String label, int x, int y, int w, int color) {
            if ((color >>> 24) == 0 || label.isBlank()) return;
            JsonObject node = node(key, "hud_text", label, x, y, w, 9);
            if (node != null) {
                node.addProperty("opacity", (color >>> 24) / 255.0);
                node.addProperty("labelSource", "last_completed_native_hud_draw");
            }
        }

        public void contextualSprite(
                String family,
                String resource,
                int x,
                int y,
                int w,
                int h,
                int textureWidth,
                int color) {
            if ((color >>> 24) == 0 || textureWidth > 0 && w > textureWidth) return;
            JsonObject node =
                    node(
                            "hud.context." + family + "." + resource + "." + x + "." + y,
                            textureWidth > 0 ? "progress_bar" : "status_icon",
                            family + " " + resource,
                            x,
                            y,
                            w,
                            h);
            if (node != null) {
                node.addProperty("resourceId", resource);
                node.addProperty("opacity", (color >>> 24) / 255.0);
                if (textureWidth > 0) {
                    node.addProperty("value", w);
                    node.addProperty("valueMinimum", 0);
                    node.addProperty("valueMaximum", textureWidth);
                    node.addProperty(
                            "valueSemantics", "drawn_sprite_extent_not_raw_player_statistics");
                }
            }
        }

        public void legacyContextualSprite(
                String family,
                String semantic,
                String texture,
                int x,
                int y,
                int u,
                int v,
                int w,
                int h,
                int barWidth) {
            contextualSprite(family, semantic, x, y, w, h, barWidth, 0xFFFFFFFF);
            JsonObject node =
                    pending.get("hud.context." + family + "." + semantic + "." + x + "." + y);
            if (node != null) {
                node.remove("resourceId");
                node.addProperty("textureId", texture);
                node.addProperty("textureU", u);
                node.addProperty("textureV", v);
                node.addProperty("atlasWidth", 256);
                node.addProperty("atlasHeight", 256);
                node.addProperty("spriteSemantics", "native_gui_icons_atlas_region");
                node.addProperty("spriteRegion", semantic);
            }
        }

        public void legacyCrosshairSprite(
                String texture, int x, int y, int u, int v, int w, int h) {
            if (!texture.equals("minecraft:textures/gui/icons.png")) return;
            String region;
            int maximum = 0;
            boolean vertical = false;
            if (u == 0 && v == 0 && w == 15 && h == 15) region = "hud/crosshair";
            else if (u == 68 && v == 94 && w == 16 && h == 16)
                region = "hud/crosshair_attack_indicator_full";
            else if (u == 36 && v == 94 && w == 16 && h == 4)
                region = "hud/crosshair_attack_indicator_background";
            else if (u == 52 && v == 94 && w > 0 && w <= 16 && h == 4) {
                region = "hud/crosshair_attack_indicator_progress";
                maximum = 16;
            } else if (u == 0 && v == 94 && w == 18 && h == 18)
                region = "hud/hotbar_attack_indicator_background";
            else if (u == 18 && h > 0 && h <= 18 && v == 112 - h && w == 18) {
                region = "hud/hotbar_attack_indicator_progress";
                maximum = 18;
                vertical = true;
            } else return;
            boolean attack = !region.equals("hud/crosshair");
            JsonObject node =
                    node(
                            "hud." + (attack ? "attack_indicator." : "crosshair.") + region,
                            maximum > 0 ? "progress_bar" : "status_icon",
                            attack ? "attack indicator " + region : "crosshair",
                            x,
                            y,
                            w,
                            h);
            if (node == null) return;
            node.addProperty("textureId", texture);
            node.addProperty("textureU", u);
            node.addProperty("textureV", v);
            node.addProperty("atlasWidth", 256);
            node.addProperty("atlasHeight", 256);
            node.addProperty("spriteSemantics", "native_gui_icons_atlas_region");
            node.addProperty("spriteRegion", region);
            node.addProperty("renderPhase", attack ? "attack_indicator" : "crosshair");
            node.addProperty("opacity", 1);
            if (maximum > 0) {
                node.addProperty("value", vertical ? h : w);
                node.addProperty("valueMinimum", 0);
                node.addProperty("valueMaximum", maximum);
                node.addProperty("valueSemantics", "drawn_fill_pixels_not_raw_attack_strength");
            }
        }

        public void bossAtlas(String texture, int x, int y, int u, int v, int w, int h) {
            if (!texture.equals("minecraft:textures/gui/bars.png")
                    || w <= 0
                    || h <= 0
                    || u < 0
                    || v < 0
                    || u > 256 - w
                    || v > 256 - h) return;
            JsonObject node =
                    node(
                            "hud.bossbar.draw." + texture + "." + u + "." + v + "." + x + "." + y,
                            "status_icon",
                            "native boss bar atlas region",
                            x,
                            y,
                            w,
                            h);
            if (node != null) {
                node.addProperty("textureId", texture);
                node.addProperty("textureU", u);
                node.addProperty("textureV", v);
                node.addProperty("atlasWidth", 256);
                node.addProperty("atlasHeight", 256);
                node.addProperty("spriteSemantics", "native_gui_bars_atlas_region");
                node.addProperty("renderPhase", "boss_bar");
            }
        }

        public void nativeText(
                String family,
                String text,
                int x,
                int y,
                int w,
                int h,
                float opacity,
                Matrix4fc pose,
                ScreenRectangle scissor) {
            nativeText(family, text, (double) x, (double) y, w, h, opacity, pose, scissor);
        }

        public void nativeText(
                String family,
                String text,
                double x,
                double y,
                int w,
                int h,
                float opacity,
                Matrix4fc pose,
                ScreenRectangle scissor) {
            if (!collecting
                    || !Float.isFinite(opacity)
                    || opacity <= 0
                    || opacity > 1
                    || text.isBlank()
                    || w <= 0
                    || h <= 0) return;
            if (pose.m03() != 0 || pose.m13() != 0 || pose.m23() != 0 || pose.m33() != 1) return;
            double[] xs = {x, (double) x + w, x, (double) x + w};
            double[] ys = {y, y, (double) y + h, (double) y + h};
            double rawLeft = Double.POSITIVE_INFINITY, rawTop = Double.POSITIVE_INFINITY;
            double rawRight = Double.NEGATIVE_INFINITY, rawBottom = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 4; i++) {
                double px = pose.m00() * xs[i] + pose.m10() * ys[i] + pose.m30();
                double py = pose.m01() * xs[i] + pose.m11() * ys[i] + pose.m31();
                if (!Double.isFinite(px) || !Double.isFinite(py)) return;
                rawLeft = Math.min(rawLeft, px);
                rawTop = Math.min(rawTop, py);
                rawRight = Math.max(rawRight, px);
                rawBottom = Math.max(rawBottom, py);
            }
            double left = Math.max(0, rawLeft), top = Math.max(0, rawTop);
            double right = Math.min(width, rawRight), bottom = Math.min(height, rawBottom);
            if (scissor != null) {
                left = Math.max(left, scissor.left());
                top = Math.max(top, scissor.top());
                right = Math.min(right, scissor.right());
                bottom = Math.min(bottom, scissor.bottom());
            }
            boolean complete =
                    left == rawLeft && top == rawTop && right == rawRight && bottom == rawBottom;
            JsonObject node =
                    boundedNode(
                            "presentation." + family + "." + textIndex++,
                            "hud_text",
                            complete ? text : "",
                            left,
                            top,
                            right - left,
                            bottom - top);
            if (node != null) {
                node.addProperty("opacity", opacity);
                node.addProperty("renderPhase", family);
                node.addProperty("textPhase", complete ? "drawn" : "withheld_partial_or_occluded");
                node.addProperty("labelSource", "last_completed_native_gui_text_draw");
                node.addProperty("iconSource", "last_completed_native_gui_draw");
                node.addProperty(
                        "boundsSemantics", "native_text_draw_viewport_scissor_intersection");
            }
        }

        public void cameraOverlay(
                String family,
                String resource,
                int x,
                int y,
                int w,
                int h,
                int color,
                float m00,
                float m01,
                float m10,
                float m11,
                float m20,
                float m21) {
            cameraOverlay(family, resource, x, y, w, h, color, m00, m01, m10, m11, m20, m21, null);
        }

        public void cameraOverlay(
                String family,
                String resource,
                int x,
                int y,
                int w,
                int h,
                int color,
                float m00,
                float m01,
                float m10,
                float m11,
                float m20,
                float m21,
                ScreenRectangle scissor) {
            if ((color >>> 24) == 0 || w <= 0 || h <= 0) return;
            double[] xs = {x, (double) x + w, x, (double) x + w};
            double[] ys = {y, y, (double) y + h, (double) y + h};
            double left = Double.POSITIVE_INFINITY, top = Double.POSITIVE_INFINITY;
            double right = Double.NEGATIVE_INFINITY, bottom = Double.NEGATIVE_INFINITY;
            for (int i = 0; i < 4; i++) {
                double px = m00 * xs[i] + m10 * ys[i] + m20;
                double py = m01 * xs[i] + m11 * ys[i] + m21;
                if (!Double.isFinite(px) || !Double.isFinite(py)) return;
                left = Math.min(left, px);
                top = Math.min(top, py);
                right = Math.max(right, px);
                bottom = Math.max(bottom, py);
            }
            left = Math.max(0, left);
            top = Math.max(0, top);
            right = Math.min(width, right);
            bottom = Math.min(height, bottom);
            if (scissor != null) {
                left = Math.max(left, scissor.left());
                top = Math.max(top, scissor.top());
                right = Math.min(right, scissor.right());
                bottom = Math.min(bottom, scissor.bottom());
            }
            JsonObject node =
                    boundedNode(
                            "hud.overlay." + family + "." + resource + "." + x + "." + y,
                            "status_icon",
                            family + " overlay",
                            left,
                            top,
                            right - left,
                            bottom - top);
            if (node == null) return;
            if (!resource.isEmpty()) node.addProperty("resourceId", resource);
            node.addProperty("opacity", (color >>> 24) / 255.0);
            node.addProperty(
                    "renderPhase", family.equals("sleep") ? "sleep_overlay" : "camera_overlay");
            node.addProperty("boundsSemantics", "native_transformed_quad_visible_aabb");
        }

        public void durabilityBackground(int itemX, int itemY, int x, int y, int w, int h) {
            JsonObject node =
                    node(
                            decorationKey("durability", itemX, itemY),
                            "progress_bar",
                            "item durability bar",
                            x,
                            y,
                            w,
                            h);
            if (node != null) {
                node.addProperty("value", 0);
                node.addProperty("valueMinimum", 0);
                node.addProperty("valueMaximum", w);
                node.addProperty("valueSemantics", "drawn_fill_pixels_not_raw_item_state");
            }
        }

        public void durabilityForeground(int itemX, int itemY, int filledWidth) {
            if (!collecting) return;
            JsonObject node = pending.get(decorationKey("durability", itemX, itemY));
            if (node != null
                    && filledWidth >= 0
                    && filledWidth <= node.get("valueMaximum").getAsInt())
                node.addProperty("value", filledWidth);
        }

        public void cooldown(int itemX, int itemY, int x, int y, int w, int h, int color) {
            if ((color >>> 24) == 0) return;
            JsonObject node =
                    node(
                            decorationKey("cooldown", itemX, itemY),
                            "progress_bar",
                            "item cooldown overlay",
                            x,
                            y,
                            w,
                            h);
            if (node != null) {
                node.addProperty("opacity", (color >>> 24) / 255.0);
                node.addProperty("value", h);
                node.addProperty("valueMinimum", 0);
                node.addProperty("valueMaximum", w);
                node.addProperty("valueSemantics", "drawn_fill_pixels_not_remaining_ticks");
            }
        }

        private static String decorationKey(String kind, int x, int y) {
            return "hud.item." + kind + "." + x + "." + y;
        }

        private JsonObject node(String key, String role, String label, int x, int y, int w, int h) {
            return boundedNode(key, role, label, x, y, w, h);
        }

        private JsonObject boundedNode(
                String key, String role, String label, double x, double y, double w, double h) {
            if (!collecting
                    || !Double.isFinite(x)
                    || !Double.isFinite(y)
                    || !Double.isFinite(w)
                    || !Double.isFinite(h)
                    || x < 0
                    || y < 0
                    || w <= 0
                    || h <= 0
                    || x + w > width
                    || y + h > height) return null;
            if (!pending.containsKey(key) && pending.size() == maximumNodes) {
                overflow = true;
                return null;
            }
            JsonObject node = new JsonObject();
            node.addProperty("nodeId", key);
            node.addProperty("controlKey", key);
            node.addProperty("role", role);
            node.addProperty("label", UiRenderedText.bounded(label));
            node.addProperty("visible", true);
            node.addProperty("active", false);
            node.addProperty("iconSource", "last_completed_native_hud_draw");
            node.add("actions", new JsonArray());
            node.addProperty("boundsAvailable", true);
            node.addProperty("x", x);
            node.addProperty("y", y);
            node.addProperty("width", w);
            node.addProperty("height", h);
            pending.put(key, node);
            return node;
        }

        public void finish(long now) {
            if (!collecting) return;
            collecting = false;
            completed =
                    new Completed(
                            world,
                            camera,
                            screen,
                            overlay,
                            worldPresent,
                            cameraPresent,
                            screenPresent,
                            overlayPresent,
                            width,
                            height,
                            now,
                            overflow ? List.of() : List.copyOf(pending.values()));
        }

        JsonArray sample(Object level, Object owner, Object currentScreen, int w, int h, long now) {
            JsonArray result = new JsonArray();
            Completed frame = completed;
            if (frame == null
                    || frame.world.get() == null
                    || frame.world.get() != level
                    || frame.camera.get() == null
                    || frame.camera.get() != owner
                    || frame.screen.get() != currentScreen
                    || frame.screenPresent != (currentScreen != null)
                    || frame.overlay.get() != null
                    || frame.overlayPresent
                    || frame.width != w
                    || frame.height != h
                    || now < frame.capturedAt
                    || now - frame.capturedAt > 250_000_000L) return result;
            for (JsonObject node : frame.nodes) result.add(node.deepCopy());
            return result;
        }

        JsonArray sampleGui(
                Object level,
                Object owner,
                Object currentScreen,
                Object currentOverlay,
                int w,
                int h,
                long now) {
            JsonArray result = new JsonArray();
            Completed frame = completed;
            if (frame == null
                    || frame.world.get() != level
                    || frame.worldPresent != (level != null)
                    || frame.camera.get() != owner
                    || frame.cameraPresent != (owner != null)
                    || frame.screen.get() != currentScreen
                    || frame.screenPresent != (currentScreen != null)
                    || frame.overlay.get() != currentOverlay
                    || frame.overlayPresent != (currentOverlay != null)
                    || frame.width != w
                    || frame.height != h
                    || now < frame.capturedAt
                    || now - frame.capturedAt > 250_000_000L) return result;
            for (JsonObject node : frame.nodes) result.add(node.deepCopy());
            return result;
        }
    }

    public static RenderedHud activeHudPresentation() {
        Minecraft client = Minecraft.getInstance();
        return client.gui instanceof HudPresentationSource source
                ? source.minecraftProtocolProbe$hudPresentation()
                : null;
    }

    public static RenderedHud activeGuiPresentation() {
        return Minecraft.getInstance().gameRenderer instanceof GuiPresentationSource source
                ? source.minecraftProtocolProbe$guiPresentation()
                : null;
    }

    static JsonObject capture(Minecraft client) {
        JsonObject result = new JsonObject();
        JsonArray children = new JsonArray();
        boolean hidden = client.options.hideGui;
        boolean hudVisible =
                !hidden
                        && client.level != null
                        && client.screen == null
                        && client.getOverlay() == null;
        HudUiAccessor hud = (HudUiAccessor) client.gui;
        hotbar(client, children, hudVisible);
        if (client.level != null && client.getOverlay() == null) {
            var source = activeHudPresentation();
            if (source != null)
                for (JsonElement value :
                        source.sample(
                                client.level,
                                client.getCameraEntity(),
                                client.screen,
                                client.getWindow().getGuiScaledWidth(),
                                client.getWindow().getGuiScaledHeight(),
                                System.nanoTime())) {
                    JsonObject node = value.getAsJsonObject();
                    String phase =
                            node.has("renderPhase") ? node.get("renderPhase").getAsString() : "";
                    if (renderedHudVisible(
                            phase,
                            hidden,
                            client.screen != null,
                            client.screen instanceof InBedChatScreen,
                            false)) children.add(node);
                }
        }
        bossBars(client, children, hudVisible);
        sidebar(client, children, hudVisible);
        effects(client, children, hudVisible);
        playerList(client, children, hudVisible);
        var late = activeGuiPresentation();
        if (late != null && client.getOverlay() == null)
            for (JsonElement value :
                    late.sampleGui(
                            client.level,
                            client.getCameraEntity(),
                            client.screen,
                            client.getOverlay(),
                            client.getWindow().getGuiScaledWidth(),
                            client.getWindow().getGuiScaledHeight(),
                            System.nanoTime())) {
                JsonObject node = value.getAsJsonObject();
                String phase = node.get("renderPhase").getAsString();
                boolean visible =
                        renderedLateVisible(
                                phase,
                                hudVisible,
                                client.screen != null,
                                client.screen instanceof ChatScreen,
                                client.screen instanceof InBedChatScreen,
                                client.getOverlay() != null);
                if (visible) children.add(node);
            }
        vitalIcons(client, children, result, hudVisible);
        if (hud.minecraftProtocolProbe$titleTime() > 0) {
            add(children, "hud.title", text(hud.minecraftProtocolProbe$title()), hudVisible);
            add(children, "hud.subtitle", text(hud.minecraftProtocolProbe$subtitle()), hudVisible);
        }
        if (hud.minecraftProtocolProbe$actionbarTime() > 0)
            add(
                    children,
                    "hud.actionbar",
                    text(hud.minecraftProtocolProbe$actionbar()),
                    hudVisible);
        ToastManagerUiAccessor manager = (ToastManagerUiAccessor) client.getToasts();
        int i = 0;
        for (Object instance : manager.minecraftProtocolProbe$visible()) {
            if (i >= 8) break;
            toast(client, children, instance, "toast." + i++, hidden);
        }
        result.add("children", children);
        result.addProperty("queuedToastCount", manager.minecraftProtocolProbe$queued().size());
        result.addProperty("coverage", "partial_typed_presentation");
        result.addProperty(
                "hudOcclusionPolicy",
                "USER HUD text only with no Screen/Overlay; Toast text only fully shown");
        JsonArray limitations = new JsonArray();
        limitations.add(
                "Typed Vanilla HUD: title/subtitle/actionbar, item hotbar, boss bars, sidebar, effect icons and player-list text; other HUD and third-party render overrides remain unsupported");
        limitations.add(
                "Player list exposes native display names/header/footer only, not profile identity, exact latency, skins or objective scores/hearts; USER requires the native list to be visible");
        limitations.add(
                "Effect icon opacity comes from the last completed native draw at the same player/Screen/GUI size, no older than 250 ms; no amplifier/duration or third-party draw guarantee");
        limitations.add(
                "Toast text: System/Tutorial/Advancement/Recipe from native render callbacks; other types expose identity only; no queued text in USER");
        limitations.add(
                "USER Toast nodes use completed native draw geometry bound to toast/world/camera/Screen/Overlay/GUI size for 250 ms; partial entry/exit exposes clipped bounds and phase without text, while fully shown text retains its native phase. This is not framebuffer or third-party occlusion proof");
        limitations.add(
                "Item name, durability/cooldown and contextual bars use bounded completed native HUD draws; no raw item, XP, jump or waypoint state.");
        result.add("limitations", limitations);
        limitations.add(
                "Vital icons use actual ordered icons.png tiles (normalized labels), not raw health, food, air, saturation or mount statistics; completed samples are world/camera/Screen/size bound, expire after 250 ms and fail closed beyond 256 glyphs. Third-party draw replacement/final pixel occlusion are not guaranteed.");
        return result;
    }

    static boolean renderedLateVisible(
            String phase,
            boolean hudVisible,
            boolean screenPresent,
            boolean chatScreen,
            boolean sleepScreen,
            boolean overlayPresent) {
        if (overlayPresent) return false;
        return switch (phase) {
            // The old native GameRenderer submits nausea before its hideGui branch.
            case "camera_overlay" -> !screenPresent;
            case "boss_bar", "overlay_message", "demo", "chat_background" -> hudVisible;
            case "chat_focused" -> chatScreen;
            case "subtitle" -> !screenPresent || chatScreen || sleepScreen;
            default -> true;
        };
    }

    private static void toast(
            Minecraft client, JsonArray children, Object instance, String key, boolean hidden) {
        ToastInstanceUiAccessor access = (ToastInstanceUiAccessor) instance;
        Toast toast = access.minecraftProtocolProbe$toast();
        long now = System.nanoTime();
        int width = client.getWindow().getGuiScaledWidth(),
                height = client.getWindow().getGuiScaledHeight();
        float portion = access.minecraftProtocolProbe$portion(net.minecraft.Util.getMillis());
        boolean framedSource = toast instanceof UiToastText.FrameSource;
        UiToastText.Frame frame =
                framedSource
                        ? ((UiToastText.FrameSource) toast).minecraftProtocolProbe$toastFrame()
                        : null;
        if (frame != null)
            frame =
                    frame.current(
                            toast,
                            client.level,
                            client.getCameraEntity(),
                            client.screen,
                            client.getOverlay(),
                            width,
                            height,
                            now);
        boolean visible =
                !hidden
                        && client.getOverlay() == null
                        && (framedSource ? portion > 0 : portion >= 1);
        JsonObject observed =
                frame == null
                        ? null
                        : renderedToastNode(
                                key,
                                toast.getClass().getName(),
                                frame,
                                visible,
                                portion,
                                access.minecraftProtocolProbe$visibility() == Toast.Visibility.HIDE,
                                width,
                                height);
        if (observed != null) {
            appendObservedToast(children, observed, toastText(toast));
            return;
        }
        UiRenderedText rendered =
                toast instanceof UiRenderedText.Source source
                        ? source.minecraftProtocolProbe$renderedText()
                        : null;
        boolean current = !framedSource && rendered != null && rendered.current(now, width, height);
        JsonObject node = add(children, key, current ? rendered.label() : "", visible && current);
        node.addProperty("debugLabel", UiRenderedText.bounded(toastText(toast)));
        node.addProperty("labelSource", "last_native_toast_render");
        node.addProperty("textPhase", current ? rendered.phase() : "unobserved");
        node.addProperty("class", toast.getClass().getName());
        node.addProperty("role", "toast");
        node.addProperty("boundsAvailable", !framedSource);
        if (!framedSource) {
            node.addProperty("x", width - toast.width());
            node.addProperty("y", access.minecraftProtocolProbe$index() * 32);
            node.addProperty("width", toast.width());
            node.addProperty("height", toast.height());
        }
        node.addProperty(
                "textSupported",
                toast instanceof SystemToast
                        || toast instanceof TutorialToast
                        || toast instanceof AdvancementToast
                        || toast instanceof RecipeToast);
    }

    static void appendObservedToast(
            JsonArray children, JsonObject observed, String nativeDebugText) {
        observed.addProperty("debugLabel", UiRenderedText.bounded(nativeDebugText));
        children.add(observed);
    }

    static JsonObject toastDrawNode(
            String key,
            String nativeClass,
            UiToastDraw.Sample draw,
            boolean visible,
            float currentPortion,
            int width,
            int height) {
        double left = Math.max(0, draw.x()), top = Math.max(0, draw.y());
        double right = Math.min(width, draw.x() + draw.width());
        double bottom = Math.min(height, draw.y() + draw.height());
        if (right <= left || bottom <= top) return null;
        boolean fullText =
                visible
                        && currentPortion >= 1
                        && draw.slidePortion() >= 1
                        && left == draw.x()
                        && top == draw.y()
                        && right == draw.x() + draw.width()
                        && bottom == draw.y() + draw.height();
        JsonObject node = new JsonObject();
        node.addProperty("nodeId", key);
        node.addProperty("controlKey", key);
        node.addProperty("class", nativeClass);
        node.addProperty("role", "toast");
        node.addProperty("label", fullText ? draw.label() : "");
        node.addProperty("labelSource", "last_native_toast_render");
        node.addProperty("textSupported", true);
        node.addProperty("textPhase", fullText ? "drawn" : "withheld_partial_or_occluded");
        node.addProperty("renderPhase", draw.renderPhase());
        node.addProperty("visible", visible);
        node.addProperty("active", false);
        node.addProperty("boundsAvailable", true);
        node.addProperty("boundsSemantics", "native_toast_draw_viewport_intersection");
        node.addProperty("x", left);
        node.addProperty("y", top);
        node.addProperty("width", right - left);
        node.addProperty("height", bottom - top);
        node.add("actions", new JsonArray());
        return node;
    }

    static JsonObject renderedToastNode(
            String key,
            String nativeClass,
            UiToastText.Frame frame,
            boolean visible,
            float portion,
            boolean hiding,
            int width,
            int height) {
        if (!Float.isFinite(portion) || portion < 0 || portion > 1) return null;
        JsonObject node =
                toastDrawNode(
                        key,
                        nativeClass,
                        frame.sample(portion, hiding),
                        visible,
                        portion,
                        width,
                        height);
        if (node != null) {
            if (node.get("textPhase").getAsString().equals("drawn"))
                node.addProperty("textPhase", frame.textPhase());
            if (visible) node.addProperty("contextKey", frame.contextKey());
        }
        return node;
    }

    private static void vitalIcons(
            Minecraft client, JsonArray nodes, JsonObject result, boolean visible) {
        var sample =
                ((UiHudIcons.Source) client.gui)
                        .minecraftProtocolProbe$hudIcons()
                        .sample(
                                client.level,
                                client.getCameraEntity(),
                                client.screen,
                                client.getWindow().getGuiScaledWidth(),
                                client.getWindow().getGuiScaledHeight(),
                                System.nanoTime());
        result.addProperty("hudIconSampleStatus", visible ? sample.status() : "OCCLUDED");
        if (!visible || !sample.status().equals("CURRENT")) return;
        int index = 0;
        for (var icon : sample.icons()) {
            String family = icon.kind().name().toLowerCase(java.util.Locale.ROOT);
            JsonObject node = add(nodes, "hud.vitals." + family + "." + index, icon.label(), true);
            node.addProperty("entryIndex", index++);
            node.addProperty("role", "status_icon");
            node.addProperty("resourceId", icon.resourceId());
            node.addProperty("iconSource", "last_completed_native_hud_draw");
            node.addProperty(
                    "valueSemantics", "ordered_native_glyph_composition_not_raw_statistics");
            bounds(node, icon.x(), icon.y(), icon.width(), icon.height());
        }
    }

    static boolean renderedHudVisible(
            String phase,
            boolean hidden,
            boolean screenPresent,
            boolean sleepScreen,
            boolean overlayPresent) {
        if (overlayPresent) return false;
        if (phase.equals("sleep_overlay")) return !screenPresent || sleepScreen;
        return !hidden && !screenPresent;
    }

    private static void hotbar(Minecraft client, JsonArray nodes, boolean visible) {
        if (!(client.getCameraEntity() instanceof Player player) || client.gameMode == null) return;
        boolean itemsVisible = visible && client.gameMode.getPlayerMode() != GameType.SPECTATOR;
        int center = client.getWindow().getGuiScaledWidth() / 2;
        int y = client.getWindow().getGuiScaledHeight() - 19;
        for (int slot = 0; slot < 9; slot++) {
            JsonObject node =
                    hotbarItem(
                            nodes,
                            "hud.hotbar." + slot,
                            player.getInventory().getItem(slot),
                            itemsVisible,
                            center - 88 + slot * 20,
                            y);
            node.addProperty("selected", slot == player.getInventory().selected);
        }
        ItemStack offhand = player.getOffhandItem();
        if (!offhand.isEmpty())
            hotbarItem(
                    nodes,
                    "hud.offhand",
                    offhand,
                    itemsVisible,
                    center + (player.getMainArm() == HumanoidArm.RIGHT ? -117 : 101),
                    y);
    }

    private static JsonObject hotbarItem(
            JsonArray nodes, String key, ItemStack stack, boolean visible, int x, int y) {
        // Icons and counts are visible; custom names/NBT/tooltips are not revealed by the hotbar.
        JsonObject node = add(nodes, key, "", visible);
        node.addProperty("visible", visible);
        node.addProperty("role", "hud_item");
        node.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        node.addProperty("count", stack.isEmpty() ? 0 : stack.getCount());
        bounds(node, x, y, 16, 16);
        return node;
    }

    private static void bossBars(Minecraft client, JsonArray nodes, boolean visible) {
        int y = 12;
        int index = 0;
        for (var event :
                ((BossOverlayUiAccessor) client.gui.getBossOverlay())
                        .minecraftProtocolProbe$events()
                        .values()) {
            if (index >= 32) break;
            JsonObject node = add(nodes, "hud.bossbar." + index++, text(event.getName()), visible);
            node.addProperty("role", "progress_bar");
            node.addProperty("visible", visible);
            node.addProperty("value", event.getProgress());
            node.addProperty("valueMinimum", 0);
            node.addProperty("valueMaximum", 1);
            node.addProperty("valueSemantics", "displayed_lerping_progress");
            bounds(node, client.getWindow().getGuiScaledWidth() / 2 - 91, y, 182, 5);
            y += 19;
            if (y >= client.getWindow().getGuiScaledHeight() / 3) break;
        }
    }

    private static void sidebar(Minecraft client, JsonArray nodes, boolean visible) {
        if (client.level == null || client.player == null) return;
        Scoreboard scoreboard = client.level.getScoreboard();
        Objective objective = null;
        PlayerTeam team = scoreboard.getPlayersTeam(client.player.getScoreboardName());
        if (team != null && team.getColor().getId() >= 0)
            objective = scoreboard.getDisplayObjective(3 + team.getColor().getId());
        if (objective == null) objective = scoreboard.getDisplayObjective(1);
        if (objective == null) return;
        var all = scoreboard.getPlayerScores(objective);
        var shown =
                all.stream()
                        .filter(
                                score ->
                                        score.getOwner() != null
                                                && !score.getOwner().startsWith("#"))
                        .toList();
        // Vanilla 1.20.1 skips using the unfiltered collection size. Reproduce its visible
        // rows, including the smaller window when hidden scores are present, not a guessed 15.
        if (shown.size() > 15) shown = shown.stream().skip(Math.max(0, all.size() - 15)).toList();
        // This renderer emits the header from inside the row loop; an empty sidebar has no title.
        add(
                nodes,
                "hud.sidebar.title",
                text(objective.getDisplayName()),
                visible && !shown.isEmpty());
        int index = 0;
        for (int row = shown.size() - 1; row >= 0; row--) {
            var entry = shown.get(row);
            String name =
                    PlayerTeam.formatNameForTeam(
                                    scoreboard.getPlayersTeam(entry.getOwner()),
                                    Component.literal(entry.getOwner()))
                            .getString();
            add(nodes, "hud.sidebar.row." + index++, name + ": " + entry.getScore(), visible);
        }
    }

    private static void effects(Minecraft client, JsonArray nodes, boolean visible) {
        if (client.player == null) return;
        int beneficial = 0;
        int harmful = 0;
        int count = 0;
        // Match Vanilla's reverse natural ordering and separate beneficial/harmful rows.
        for (var effect :
                client.player.getActiveEffects().stream()
                        .sorted(Comparator.reverseOrder())
                        .limit(64)
                        .toList()) {
            var type = effect.getEffect();
            if (!net.minecraftforge.client.extensions.common.IClientMobEffectExtensions.of(effect)
                    .isVisibleInGui(effect)) continue;
            boolean icon = effect.showIcon();
            int column = icon ? (type.isBeneficial() ? ++beneficial : ++harmful) : 0;
            int x = client.getWindow().getGuiScaledWidth() - 25 * column;
            int y = 1 + (client.isDemo() ? 15 : 0) + (type.isBeneficial() ? 0 : 26);
            var drawn =
                    ((UiRenderedIcons.Source) client.gui)
                            .minecraftProtocolProbe$effectIcons()
                            .sample(
                                    client.getMobEffectTextures()
                                            .get(effect.getEffect())
                                            .contents()
                                            .name()
                                            .toString(),
                                    x + 3,
                                    y + 3,
                                    client.player,
                                    client.screen,
                                    client.getWindow().getGuiScaledWidth(),
                                    client.getWindow().getGuiScaledHeight(),
                                    System.nanoTime());
            boolean shown =
                    visible
                            && icon
                            && x >= 0
                            && y + 24 <= client.getWindow().getGuiScaledHeight()
                            && drawn != null
                            && drawn.opacity() > 0;
            JsonObject node = add(nodes, "hud.effect." + count++, "", shown);
            node.addProperty("visible", shown);
            node.addProperty("role", "status_icon");
            node.addProperty("effect", BuiltInRegistries.MOB_EFFECT.getKey(type).toString());
            node.addProperty("iconSource", "last_native_effect_icon_draw");
            node.addProperty(
                    "renderPhase",
                    drawn == null ? "unobserved" : drawn.opacity() > 0 ? "drawn" : "transparent");
            if (drawn != null) node.addProperty("opacity", drawn.opacity());
            bounds(node, x + 3, y + 3, 18, 18);
        }
    }

    private static void playerList(Minecraft client, JsonArray nodes, boolean visible) {
        if (client.level == null || client.player == null || client.getConnection() == null) return;
        var overlay = client.gui.getTabList();
        var access = (PlayerListUiAccessor) overlay;
        boolean shown = visible && access.minecraftProtocolProbe$visible();
        if (shown) {
            if (overlay instanceof PlayerListPresentationSource source)
                for (JsonElement value :
                        source.minecraftProtocolProbe$playerListPresentation()
                                .sample(
                                        client.level,
                                        client.getCameraEntity(),
                                        client.getConnection(),
                                        overlay,
                                        client.screen,
                                        client.getOverlay(),
                                        client.getWindow().getGuiScaledWidth(),
                                        client.getWindow().getGuiScaledHeight(),
                                        System.nanoTime(),
                                        true)) nodes.add(value);
            return;
        }
        add(nodes, "hud.playerlist.header", text(access.minecraftProtocolProbe$header()), shown);
        int index = 0;
        // The native getter applies listed-player filtering, ordering and the 80-entry cap.
        // Display names may deliberately hide the profile name: never publish the latter as USER.
        for (var player : access.minecraftProtocolProbe$displayedPlayers()) {
            if (index >= 80) break;
            add(
                    nodes,
                    "hud.playerlist.row." + index++,
                    text(overlay.getNameForDisplay(player)),
                    shown);
        }
        add(nodes, "hud.playerlist.footer", text(access.minecraftProtocolProbe$footer()), shown);
    }

    private static void bounds(JsonObject node, int x, int y, int width, int height) {
        node.addProperty("boundsAvailable", true);
        node.addProperty("x", x);
        node.addProperty("y", y);
        node.addProperty("width", width);
        node.addProperty("height", height);
    }

    static String toastText(Toast toast) {
        if (toast instanceof SystemToast) {
            SystemToastUiAccessor value = (SystemToastUiAccessor) toast;
            return text(value.minecraftProtocolProbe$title())
                    + "\n"
                    + lines(value.minecraftProtocolProbe$message());
        }
        if (toast instanceof TutorialToast)
            return text(((TutorialToastUiAccessor) toast).minecraftProtocolProbe$title())
                    + "\n"
                    + text(((TutorialToastUiAccessor) toast).minecraftProtocolProbe$message());
        if (toast instanceof AdvancementToast) return advancementTitle(toast).getString();
        if (toast instanceof RecipeToast)
            return Component.translatable("recipe.toast.title").getString()
                    + "\n"
                    + Component.translatable("recipe.toast.description").getString();
        return "";
    }

    private static Component advancementTitle(Toast toast) {
        var advancement = ((AdvancementToastUiAccessor) toast).minecraftProtocolProbe$advancement();
        return advancement.getDisplay() == null
                ? Component.empty()
                : advancement.getDisplay().getTitle();
    }

    static String lines(List<FormattedCharSequence> lines) {
        StringBuilder out = new StringBuilder();
        for (FormattedCharSequence line : lines) {
            if (out.length() >= 2048) break;
            if (!out.isEmpty()) out.append('\n');
            line.accept(
                    (index, style, codepoint) -> {
                        if (out.length() >= 2048) return false;
                        out.appendCodePoint(codepoint);
                        return true;
                    });
        }
        return out.toString();
    }

    private static String text(Component value) {
        return value == null ? "" : value.getString();
    }

    private static JsonObject add(JsonArray nodes, String key, String label, boolean visible) {
        JsonObject node = new JsonObject();
        node.addProperty("nodeId", key);
        node.addProperty("controlKey", key);
        node.addProperty("role", "hud_text");
        node.addProperty("label", label.substring(0, Math.min(2048, label.length())));
        node.addProperty("visible", visible && !label.isBlank());
        node.addProperty("active", false);
        node.addProperty("boundsAvailable", false);
        node.add("actions", new JsonArray());
        nodes.add(node);
        return node;
    }
}
