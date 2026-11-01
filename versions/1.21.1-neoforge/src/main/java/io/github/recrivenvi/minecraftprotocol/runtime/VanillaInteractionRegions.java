package io.github.recrivenvi.minecraftprotocol.runtime;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.mixin.*;
import java.util.*;
import java.util.function.ToLongFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.client.gui.screens.advancements.*;
import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Style;

/** Bounded, read-only semantic regions omitted from the native widget graph. */
final class VanillaInteractionRegions {
    private final long revision;
    private final ToLongFunction<Object> identity;
    private final JsonArray nodes = new JsonArray();
    private final Set<Object> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private Screen screen;
    private boolean truncated;

    boolean truncated() {
        return truncated;
    }

    VanillaInteractionRegions(long revision, ToLongFunction<Object> identity) {
        this.revision = revision;
        this.identity = identity;
    }

    JsonArray capture(Screen screen) {
        this.screen = screen;
        walk(screen.children(), 0);
        Minecraft mc = Minecraft.getInstance();
        if (screen instanceof BookViewScreen book) {
            BookViewUiAccessor access = (BookViewUiAccessor) book;
            int[] index = {0};
            RichTextUiRegions rich =
                    new RichTextUiRegions(
                            mc.font,
                            r -> {
                                Style hit =
                                        book.getClickedComponentStyleAt(
                                                (r.left() + r.right()) / 2,
                                                (r.top() + r.bottom()) / 2);
                                if (hit != null && hit.equals(r.style()))
                                    text(
                                            book,
                                            "book.link." + index[0]++,
                                            "page:" + access.mineCraftProtocol$pageIndex(),
                                            r);
                            });
            var lines =
                    mc.font.split(
                            access.mineCraftProtocol$book()
                                    .getPage(access.mineCraftProtocol$pageIndex()),
                            114);
            for (int i = 0; i < Math.min(14, lines.size()); i++)
                rich.line((screen.width - 192) / 2 + 36, 32 + i * 9, 1, 9, lines.get(i));
        }
        if (screen instanceof ChatScreen chat) {
            ChatUiAccessor access = (ChatUiAccessor) chat;
            suggestions(access.mineCraftProtocol$suggestions());
            int[] index = {0};
            var component = mc.gui.getChat();
            var data = (ChatComponentUiAccessor) component;
            double scale = component.getScale();
            int height = data.mineCraftProtocol$lineHeight();
            RichTextUiRegions rich =
                    new RichTextUiRegions(
                            mc.font,
                            r -> {
                                Style hit =
                                        component.getClickedComponentStyleAt(
                                                (r.left() + r.right()) / 2,
                                                (r.top() + r.bottom()) / 2);
                                if (hit != null && hit.equals(r.style()))
                                    text(chat, "chat.link." + index[0]++, "chat", r);
                            });
            int count = Math.min(128, component.getLinesPerPage());
            for (int i = 0;
                    i < count
                            && i + data.mineCraftProtocol$scroll()
                                    < data.mineCraftProtocol$lines().size();
                    i++)
                rich.line(
                        4 * scale,
                        screen.height - 40 - (i + 1) * height * scale,
                        scale,
                        height * scale,
                        data.mineCraftProtocol$lines()
                                .get(i + data.mineCraftProtocol$scroll())
                                .content());
        }
        if (screen instanceof AdvancementsScreen advancements) advancements(advancements);
        if (screen instanceof GameModeSwitcherScreen gameModes) gameModes(gameModes);
        return nodes;
    }

    private void gameModes(GameModeSwitcherScreen screen) {
        int index = 0;
        for (var slot : ((GameModeSwitcherUiAccessor) screen).mineCraftProtocol$slots()) {
            JsonObject node =
                    region(
                            slot,
                            "game_mode.choice." + index++,
                            "game_mode",
                            slot.getMessage().getString(),
                            slot.getX(),
                            slot.getY(),
                            slot.getWidth(),
                            slot.getHeight(),
                            20,
                            "hover");
            node.addProperty(
                    "selected", ((GameModeSlotUiAccessor) slot).mineCraftProtocol$selected());
            node.addProperty("effect", "hover_then_release_debug_modifier");
            if (slot.getMessage().getContents()
                    instanceof net.minecraft.network.chat.contents.TranslatableContents translated)
                node.addProperty("translationKey", translated.getKey());
        }
    }

    private void advancements(AdvancementsScreen screen) {
        AdvancementsUiAccessor a = (AdvancementsUiAccessor) screen;
        int left = (screen.width - 252) / 2, top = (screen.height - 140) / 2;
        if (a.mineCraftProtocol$tabs().size() > 1)
            for (var tab : a.mineCraftProtocol$tabs().values()) {
                if (tab.getPage() != AdvancementsUiAccessor.mineCraftProtocol$page()) continue;
                var type = (AdvancementTypeUiAccessor) (Object) tab.getType();
                JsonObject node =
                        region(
                                tab,
                                "advancement.tab",
                                "tab",
                                tab.getTitle().getString(),
                                left + type.mineCraftProtocol$x(tab.getIndex()),
                                top + type.mineCraftProtocol$y(tab.getIndex()),
                                type.mineCraftProtocol$width(),
                                type.mineCraftProtocol$height(),
                                20,
                                "hover",
                                "click",
                                "mouse_down",
                                "mouse_up");
                node.addProperty("resourceId", tab.getRootNode().holder().id().toString());
                node.addProperty("selected", tab == a.mineCraftProtocol$selected());
            }
        var tab = a.mineCraftProtocol$selected();
        if (tab == null) return;
        AdvancementTabUiAccessor t = (AdvancementTabUiAccessor) tab;
        JsonObject canvas =
                region(
                        tab,
                        "advancement.canvas",
                        "canvas",
                        tab.getTitle().getString(),
                        left + 9,
                        top + 18,
                        234,
                        113,
                        10,
                        "hover",
                        "scroll",
                        "click",
                        "mouse_down",
                        "mouse_up",
                        "drag");
        canvas.addProperty("scrollX", t.mineCraftProtocol$scrollX());
        canvas.addProperty("scrollY", t.mineCraftProtocol$scrollY());
        canvas.addProperty(
                "canPanX", t.mineCraftProtocol$maxX() - t.mineCraftProtocol$minX() > 234);
        canvas.addProperty(
                "canPanY", t.mineCraftProtocol$maxY() - t.mineCraftProtocol$minY() > 113);
        for (var widget : t.mineCraftProtocol$widgets().values()) {
            double x = left + 9 + Math.floor(t.mineCraftProtocol$scrollX()) + widget.getX();
            double y = top + 18 + Math.floor(t.mineCraftProtocol$scrollY()) + widget.getY();
            double l = Math.max(x, left + 9),
                    r = Math.min(x + 26, left + 243),
                    u = Math.max(y, top + 18),
                    b = Math.min(y + 26, top + 131);
            if (r <= l || b <= u) continue;
            var data = (AdvancementWidgetUiAccessor) widget;
            JsonObject node =
                    region(
                            widget,
                            "advancement.node",
                            "advancement",
                            data.mineCraftProtocol$display().getTitle().getString(),
                            l,
                            u,
                            r - l,
                            b - u,
                            20,
                            "hover");
            node.addProperty(
                    "resourceId", data.mineCraftProtocol$advancement().holder().id().toString());
            node.addProperty(
                    "description",
                    VanillaUiTree.bounded(
                            data.mineCraftProtocol$display().getDescription().getString()));
        }
    }

    private void walk(List<? extends GuiEventListener> children, int depth) {
        if (depth > 16 || nodes.size() >= 512 || seen.size() >= 2048) {
            truncated = true;
            return;
        }
        for (GuiEventListener child : children) {
            if (nodes.size() >= 512 || seen.size() >= 2048) {
                truncated = true;
                break;
            }
            if (!seen.add(child)) continue;
            if (child instanceof AbstractSelectionList<?> area && area.getMaxScroll() > 0) {
                var a = (SelectionListAccessor) area;
                int x = a.mineCraftProtocol$scrollbarX(),
                        y = area.getY(),
                        w = 6,
                        h = area.getHeight();
                int thumbH =
                        Math.max(
                                32,
                                Math.min(
                                        h - 8,
                                        (int)
                                                ((float) (h * h)
                                                        / a.mineCraftProtocol$contentHeight())));
                int thumbY =
                        Math.max(
                                y,
                                (int) area.getScrollAmount() * (h - thumbH) / area.getMaxScroll()
                                        + y);
                String state = x + ":" + y + ":" + w + ":" + h + ":" + area.getMaxScroll();
                JsonObject track =
                        region(
                                area,
                                "scrollbar.track",
                                "scrollbar",
                                "Scroll track",
                                x,
                                y,
                                w,
                                h,
                                10,
                                "hover",
                                "click",
                                "scroll",
                                "mouse_down",
                                "mouse_up",
                                "drag");
                track.addProperty("semanticState", state);
                track.addProperty("scrollAmount", area.getScrollAmount());
                track.addProperty("maxScrollAmount", area.getMaxScroll());
                track.addProperty("dragAxis", "vertical");
                JsonObject thumb =
                        region(
                                area,
                                "scrollbar.thumb",
                                "scrollbar_thumb",
                                "Scroll thumb",
                                x,
                                thumbY,
                                w,
                                thumbH,
                                11,
                                "hover",
                                "click",
                                "mouse_down",
                                "mouse_up",
                                "drag");
                thumb.addProperty("semanticState", state);
                thumb.addProperty("dragAxis", "vertical");
            }
            if (child instanceof AbstractScrollWidget area && area.visible && area.active) {
                ScrollWidgetUiAccessor a = (ScrollWidgetUiAccessor) area;
                int max = a.mineCraftProtocol$maxScroll();
                if (a.mineCraftProtocol$visible() && max > 0) {
                    int x = area.getX() + area.getWidth(),
                            y = area.getY(),
                            height = area.getHeight();
                    int thumbHeight = a.mineCraftProtocol$thumbHeight();
                    int thumbY =
                            Math.max(
                                    y,
                                    (int) a.mineCraftProtocol$scroll()
                                                    * (height - thumbHeight)
                                                    / max
                                            + y);
                    String state = x + ":" + y + ":" + height + ":" + max;
                    JsonObject track =
                            region(
                                    area,
                                    "scrollbar.track",
                                    "scrollbar",
                                    "Scroll track",
                                    x,
                                    y,
                                    8,
                                    height,
                                    10,
                                    "hover",
                                    "click",
                                    "scroll",
                                    "mouse_down",
                                    "mouse_up",
                                    "drag");
                    track.addProperty("semanticState", state);
                    track.addProperty("scrollAmount", a.mineCraftProtocol$scroll());
                    track.addProperty("maxScrollAmount", max);
                    track.addProperty("dragAxis", "vertical");
                    JsonObject thumb =
                            region(
                                    area,
                                    "scrollbar.thumb",
                                    "scrollbar_thumb",
                                    "Scroll thumb",
                                    x,
                                    thumbY,
                                    8,
                                    thumbHeight,
                                    11,
                                    "hover",
                                    "click",
                                    "mouse_down",
                                    "mouse_up",
                                    "drag");
                    thumb.addProperty("semanticState", state);
                    thumb.addProperty("dragAxis", "vertical");
                }
            }
            if (child instanceof ContainerEventHandler container)
                walk(container.children(), depth + 1);
        }
    }

    private void suggestions(CommandSuggestions suggestions) {
        if (suggestions == null) return;
        var list = ((CommandSuggestionsUiAccessor) suggestions).mineCraftProtocol$list();
        if (list == null) return;
        SuggestionListUiAccessor a = (SuggestionListUiAccessor) (Object) list;
        var rect = a.mineCraftProtocol$rect();
        JsonObject root =
                region(
                        list,
                        "chat.suggestions",
                        "list",
                        "Command suggestions",
                        rect.getX(),
                        rect.getY(),
                        rect.getWidth(),
                        rect.getHeight(),
                        50,
                        "hover",
                        "scroll");
        root.addProperty("entryCount", a.mineCraftProtocol$entries().size());
        root.addProperty("scrollAmount", a.mineCraftProtocol$offset());
        int visible = rect.getHeight() / 12;
        for (int i = 0;
                i < visible
                        && i + a.mineCraftProtocol$offset() < a.mineCraftProtocol$entries().size();
                i++) {
            int n = i + a.mineCraftProtocol$offset();
            var s = a.mineCraftProtocol$entries().get(n);
            JsonObject row =
                    region(
                            list,
                            "chat.suggestion." + n,
                            "suggestion",
                            s.getText(),
                            rect.getX(),
                            rect.getY() + 12 * i,
                            rect.getWidth(),
                            12,
                            51,
                            "hover",
                            "click",
                            "mouse_down",
                            "mouse_up");
            row.addProperty("entryIndex", n);
            row.addProperty("selected", n == a.mineCraftProtocol$current());
            row.addProperty(
                    "semanticState",
                    a.mineCraftProtocol$original() + ":" + s.getRange() + ":" + s.getText());
            row.addProperty("effect", "insert_suggestion_not_submit");
        }
    }

    private void text(Object owner, String key, String context, RichTextUiRegions.Region r) {
        Style s = r.style();
        JsonObject node =
                region(
                        owner,
                        key,
                        s.getClickEvent() == null ? "text" : "link",
                        r.text(),
                        r.left(),
                        r.top(),
                        r.right() - r.left(),
                        r.bottom() - r.top(),
                        30,
                        s.getClickEvent() == null
                                ? new String[] {"hover"}
                                : new String[] {"hover", "click", "mouse_down", "mouse_up"});
        node.addProperty("contextKey", context);
        String state =
                context
                        + "|"
                        + r.text()
                        + "|"
                        + s
                        + "|"
                        + r.left()
                        + ":"
                        + r.top()
                        + ":"
                        + r.right()
                        + ":"
                        + r.bottom();
        String fingerprint;
        try {
            fingerprint =
                    java.util.HexFormat.of()
                            .formatHex(
                                    java.security.MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    state.getBytes(
                                                            java.nio.charset.StandardCharsets
                                                                    .UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        node.addProperty("nodeId", node.get("nodeId").getAsString() + ":" + fingerprint);
        node.addProperty("semanticState", fingerprint);
        node.addProperty("untrustedContent", true);
        if (s.getClickEvent() != null) {
            node.addProperty("clickEventType", s.getClickEvent().getAction().getSerializedName());
            node.addProperty("clickEvent", VanillaUiTree.bounded(s.getClickEvent().toString()));
        }
        if (s.getInsertion() != null)
            node.addProperty("insertion", VanillaUiTree.bounded(s.getInsertion()));
    }

    private JsonObject region(
            Object owner,
            String key,
            String role,
            String label,
            double x,
            double y,
            double width,
            double height,
            int layer,
            String... actions) {
        JsonObject node = new JsonObject();
        node.addProperty(
                "nodeId", "ui:" + revision + ":region:" + identity.applyAsLong(owner) + ":" + key);
        node.addProperty("elementIdentity", identity.applyAsLong(owner));
        node.addProperty("controlKey", key);
        node.addProperty("role", role);
        node.addProperty("label", VanillaUiTree.bounded(label));
        node.addProperty("class", owner.getClass().getName());
        node.addProperty("coverage", "semantic_native");
        node.addProperty("acquisition", "native_interaction_region");
        node.addProperty("interactionLayer", layer);
        node.addProperty("capabilitySource", "confirmed_vanilla_input_handler");
        double l = Math.max(0, x),
                t = Math.max(0, y),
                r = Math.min(screen.width, x + width),
                b = Math.min(screen.height, y + height);
        boolean visible = r > l && b > t;
        node.addProperty("visible", visible);
        node.addProperty("active", true);
        node.addProperty("boundsAvailable", visible);
        node.addProperty("x", l);
        node.addProperty("y", t);
        node.addProperty("width", Math.max(0, r - l));
        node.addProperty("height", Math.max(0, b - t));
        node.addProperty("interactionX", (l + r) / 2);
        node.addProperty("interactionY", (t + b) / 2);
        JsonArray a = new JsonArray();
        if (visible) for (String action : actions) a.add(action);
        node.add("actions", a);
        if (nodes.size() < 512) nodes.add(node);
        else truncated = true;
        return node;
    }
}
