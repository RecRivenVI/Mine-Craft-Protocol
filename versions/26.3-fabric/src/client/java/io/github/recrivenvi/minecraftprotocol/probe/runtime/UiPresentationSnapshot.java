package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.*;
import io.github.recrivenvi.minecraftprotocol.safety.UiHudIcons;
import io.github.recrivenvi.minecraftprotocol.safety.UiRenderedIcons;
import io.github.recrivenvi.minecraftprotocol.safety.UiRenderedText;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.*;

/** Bounded read-only HUD/Toast facts; no artificial click targets for noninteractive content. */
final class UiPresentationSnapshot {
    static JsonObject capture(Minecraft client) {
        JsonObject result = new JsonObject();
        JsonArray children = new JsonArray();
        boolean hidden = client.gui.hud.isHidden();
        boolean hudVisible =
                !hidden
                        && client.level != null
                        && client.gui.screen() == null
                        && client.gui.overlay() == null;
        HudUiAccessor hud = (HudUiAccessor) client.gui.hud;
        hotbar(client, children, hudVisible);
        bossBars(client, children, hudVisible);
        sidebar(client, children, hudVisible);
        effects(client, children, hudVisible);
        playerList(client, children, hudVisible);
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
        ToastManagerUiAccessor manager = (ToastManagerUiAccessor) client.gui.toastManager();
        int i = 0;
        for (Object instance : manager.minecraftProtocolProbe$visible()) {
            if (i >= 8) break;
            ToastInstanceUiAccessor access = (ToastInstanceUiAccessor) instance;
            Toast toast = access.minecraftProtocolProbe$toast();
            boolean visible =
                    !hidden
                            && client.gui.overlay() == null
                            && access.minecraftProtocolProbe$portion() >= 1.0F;
            UiRenderedText rendered =
                    toast instanceof UiRenderedText.Source source
                            ? source.minecraftProtocolProbe$renderedText()
                            : null;
            boolean current =
                    rendered != null
                            && rendered.current(
                                    System.nanoTime(),
                                    client.getWindow().getGuiScaledWidth(),
                                    client.getWindow().getGuiScaledHeight());
            JsonObject node =
                    add(
                            children,
                            "toast." + i++,
                            current ? rendered.label() : "",
                            visible && current);
            node.addProperty("debugLabel", UiRenderedText.bounded(toastText(toast)));
            node.addProperty("labelSource", "last_native_toast_render");
            node.addProperty("textPhase", current ? rendered.phase() : "unobserved");
            node.addProperty("class", toast.getClass().getName());
            node.addProperty("role", "toast");
            node.addProperty("boundsAvailable", true);
            node.addProperty("x", client.getWindow().getGuiScaledWidth() - toast.width());
            node.addProperty("y", access.minecraftProtocolProbe$index() * 32);
            node.addProperty("width", toast.width());
            node.addProperty("height", toast.height());
            node.addProperty(
                    "textSupported",
                    toast instanceof SystemToast
                            || toast instanceof TutorialToast
                            || toast instanceof AdvancementToast
                            || toast instanceof RecipeToast);
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
                "USER Toast text requires a fully shown toast and a native render sample no older than one second at the same GUI size; this is not a framebuffer or third-party rendering guarantee");
        result.add("limitations", limitations);
        limitations.add(
                "Vital icons are the ordered native glyph composition, including container/blinking/foreground layers; no raw health, food, air, saturation or mount statistics. The completed sample is world/camera/Screen/size bound, expires after 250 ms and fails closed beyond 256 glyphs; third-party draw replacement and final pixel occlusion are not guaranteed.");
        return result;
    }

    private static void vitalIcons(
            Minecraft client, JsonArray nodes, JsonObject result, boolean visible) {
        var sample =
                ((UiHudIcons.Source) client.gui.hud)
                        .minecraftProtocolProbe$hudIcons()
                        .sample(
                                client.level,
                                client.getCameraEntity(),
                                client.gui.screen(),
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
            node.addProperty("selected", slot == player.getInventory().getSelectedSlot());
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
                ((BossOverlayUiAccessor) client.gui.hud.getBossOverlay())
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
        if (team != null && team.getColor().isPresent())
            objective = scoreboard.getDisplayObjective(team.getColor().get().displaySlot());
        if (objective == null) objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (objective == null) return;
        add(nodes, "hud.sidebar.title", text(objective.getDisplayName()), visible);
        var format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        int index = 0;
        // Match Vanilla's display order, hidden-owner filter and 15-row limit, not all scores.
        for (var entry :
                scoreboard.listPlayerScores(objective).stream()
                        .filter(score -> !score.isHidden())
                        .sorted(
                                Comparator.comparingInt(PlayerScoreEntry::value)
                                        .reversed()
                                        .thenComparing(
                                                PlayerScoreEntry::owner,
                                                String.CASE_INSENSITIVE_ORDER))
                        .limit(15)
                        .toList()) {
            String name =
                    PlayerTeam.formatNameForTeam(
                                    scoreboard.getPlayersTeam(entry.owner()), entry.ownerName())
                            .getString();
            String score = entry.formatValue(format).getString();
            // Blank/custom number formats must not leak the underlying numeric score to USER.
            add(
                    nodes,
                    "hud.sidebar.row." + index++,
                    name + (score.isEmpty() ? "" : ": " + score),
                    visible);
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
            var type = effect.getEffect().value();
            boolean icon = effect.showIcon();
            int column = icon ? (type.isBeneficial() ? ++beneficial : ++harmful) : 0;
            int x = client.getWindow().getGuiScaledWidth() - 25 * column;
            int y = 1 + (client.isDemo() ? 15 : 0) + (type.isBeneficial() ? 0 : 26);
            var drawn =
                    ((UiRenderedIcons.Source) client.gui.hud)
                            .minecraftProtocolProbe$effectIcons()
                            .sample(
                                    net.minecraft.client.gui.Hud.getMobEffectSprite(
                                                    effect.getEffect())
                                            .toString(),
                                    x + 3,
                                    y + 3,
                                    client.player,
                                    client.gui.screen(),
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
        var overlay = client.gui.hud.getTabList();
        var access = (PlayerListUiAccessor) overlay;
        boolean shown = visible && access.minecraftProtocolProbe$visible();
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
            return lines(value.minecraftProtocolProbe$title())
                    + "\n"
                    + lines(value.minecraftProtocolProbe$message());
        }
        if (toast instanceof TutorialToast)
            return lines(((TutorialToastUiAccessor) toast).minecraftProtocolProbe$lines());
        if (toast instanceof AdvancementToast) return advancementTitle(toast).getString();
        if (toast instanceof RecipeToast)
            return Component.translatable("recipe.toast.title").getString()
                    + "\n"
                    + Component.translatable("recipe.toast.description").getString();
        return "";
    }

    private static Component advancementTitle(Toast toast) {
        var advancement =
                ((AdvancementToastUiAccessor) toast).minecraftProtocolProbe$advancement().value();
        return advancement.display().map(display -> display.title()).orElse(Component.empty());
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
