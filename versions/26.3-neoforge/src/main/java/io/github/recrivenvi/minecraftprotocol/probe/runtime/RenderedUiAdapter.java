package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.*;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.*;
import java.util.*;
import java.util.function.ToLongFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.gui.screens.packs.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

final class RenderedUiAdapter {
    static final int COLLECTION_LIMIT = 256;
    private final long revision;
    private final ToLongFunction<Object> identity;

    RenderedUiAdapter(long revision, ToLongFunction<Object> identity) {
        this.revision = revision;
        this.identity = identity;
    }

    private JsonObject node(Object owner, String key, String label, String role) {
        JsonObject result = new JsonObject();
        long id = identity.applyAsLong(owner);
        result.addProperty("nodeId", "ui:" + revision + ":" + id + ":" + key);
        result.addProperty("elementIdentity", id);
        result.addProperty("nodeRevision", revision);
        result.addProperty("class", owner.getClass().getName());
        result.addProperty("controlKey", key);
        result.addProperty("label", VanillaUiTree.bounded(label));
        result.addProperty("role", role);
        result.addProperty("coverage", "semantic_native");
        result.addProperty("acquisition", "typed_vanilla_adapter");
        return result;
    }

    static void actions(JsonObject node, String... values) {
        JsonArray actions = new JsonArray();
        for (String value : values) actions.add(value);
        node.add("actions", actions);
    }

    private JsonObject hit(
            Object owner,
            String key,
            String label,
            ScreenRectangle bounds,
            ScreenRectangle clip,
            boolean enabled,
            boolean shown) {
        JsonObject node = node(owner, key, label, "button");
        VanillaUiTree.bounds(node, bounds, clip, enabled, shown);
        actions(
                node,
                enabled && node.get("visible").getAsBoolean()
                        ? new String[] {"hover", "click", "mouse_down", "mouse_up"}
                        : new String[0]);
        return node;
    }

    JsonArray packControls(
            TransferableSelectionList.PackEntry entry,
            JsonObject row,
            ScreenRectangle rowBounds,
            ScreenRectangle clip) {
        PackSelectionModel.Entry pack = ((PackEntryUiAccessor) entry).minecraftProtocolProbe$pack();
        row.addProperty("packId", pack.getId());
        row.addProperty("label", VanillaUiTree.bounded(pack.getTitle().getString()));
        row.addProperty("selected", pack.isSelected());
        row.addProperty("role", "container");
        actions(row, "hover");
        int x = entry.getContentX(), y = entry.getContentY();
        boolean shown = row.get("visible").getAsBoolean();
        JsonArray controls = new JsonArray();
        String[] verbs = {"select", "unselect", "move_up", "move_down"};
        boolean[] allowed = {
            pack.canSelect(), pack.canUnselect(), pack.canMoveUp(), pack.canMoveDown()
        };
        for (int i = 0; i < verbs.length; i++) {
            // Match the native 32-pixel icon, left half and right quarters.
            int dx = i >= 2 ? 16 : 0;
            int dy = i == 3 ? 16 : 0;
            int w = i == 0 ? 32 : 16, h = i >= 2 ? 16 : 32;
            JsonObject control =
                    hit(
                            entry,
                            "pack." + verbs[i],
                            verbs[i] + ": " + pack.getTitle().getString(),
                            new ScreenRectangle(x + dx, y + dy, w, h),
                            clip,
                            allowed[i] && row.get("active").getAsBoolean(),
                            shown);
            control.addProperty("packId", pack.getId());
            control.addProperty(
                    "semanticState", pack.getId() + ":" + pack.isSelected() + ":" + verbs[i]);
            if (!shown && allowed[i]) actions(control, "scroll_into_view");
            controls.add(control);
        }
        return controls;
    }

    JsonArray serverControls(
            Object entry,
            String name,
            JsonObject row,
            ScreenRectangle icon,
            ScreenRectangle clip,
            int index,
            int savedCount,
            boolean hovered) {
        row.addProperty("label", VanillaUiTree.bounded(name));
        row.addProperty("controlKey", "server.entry");
        // Bind ordering separately from the ping/narration, which changes asynchronously.
        row.addProperty("semanticState", index + ":" + name);
        JsonArray controls = new JsonArray();
        String[] verbs = {"join", "move_up", "move_down"};
        boolean[] allowed = {true, index > 0, index + 1 < savedCount};
        for (int i = 0; i < verbs.length; i++) {
            JsonObject control =
                    hit(
                            entry,
                            "server." + verbs[i],
                            verbs[i] + ": " + name,
                            new ScreenRectangle(
                                    icon.left() + (i == 0 ? 16 : 0),
                                    icon.top() + (i == 2 ? 16 : 0),
                                    16,
                                    i == 0 ? 32 : 16),
                            clip,
                            allowed[i] && row.get("active").getAsBoolean(),
                            hovered && row.get("visible").getAsBoolean() && allowed[i]);
            control.addProperty("semanticState", index + ":" + name + ":" + verbs[i]);
            control.addProperty("nodeId", control.get("nodeId").getAsString() + ":" + index);
            control.addProperty("entryIndex", index);
            controls.add(control);
        }
        return controls;
    }

    JsonArray capture(Screen screen, JsonObject root) {
        JsonArray controls = new JsonArray();
        if (screen instanceof AbstractContainerScreen<?> container) {
            int left = container.getLeftPos(), top = container.getTopPos();
            ScreenRectangle clip = new ScreenRectangle(0, 0, screen.width, screen.height);
            if (screen instanceof CreativeModeInventoryScreen creative) {
                CreativeUiAccessor access = (CreativeUiAccessor) creative;
                var tab = CreativeUiAccessor.minecraftProtocolProbe$selectedTab();
                if (tab.canScroll()) {
                    float offset = access.minecraftProtocolProbe$scrollOffs();
                    controls.addAll(
                            scrollbar(
                                    screen,
                                    "creative.scrollbar",
                                    new ScreenRectangle(left + 175, top + 18, 14, 112),
                                    new ScreenRectangle(
                                            left + 175, top + 18 + (int) (95 * offset), 12, 15),
                                    clip,
                                    access.minecraftProtocolProbe$canScroll(),
                                    offset,
                                    identity.applyAsLong(tab)
                                            + ":"
                                            + access.minecraftProtocolProbe$searchBox()
                                                    .getValue()));
                }
            }
            if (screen instanceof StonecutterScreen stone)
                stonecutter(stone, controls, left, top, clip);
            if (screen instanceof LoomScreen loom) loom(loom, controls, left, top, clip);
            if (screen instanceof EnchantmentScreen enchantment)
                enchantments(enchantment, controls, left, top, clip);
            if (screen instanceof MerchantScreen merchant) {
                int count = merchant.getMenu().getOffers().size();
                int offset = ((MerchantUiAccessor) merchant).minecraftProtocolProbe$scrollOff();
                int steps = count - 6;
                int dy = 0;
                if (steps > 1) {
                    int remainder = 139 - (27 + (steps - 1) * 139 / steps);
                    dy = Math.min(113, offset * (1 + remainder / steps + 139 / steps));
                    if (offset == steps - 1) dy = 113;
                }
                controls.addAll(
                        scrollbar(
                                screen,
                                "merchant.scrollbar",
                                new ScreenRectangle(left + 94, top + 18, 6, 140),
                                new ScreenRectangle(left + 94, top + 18 + dy, 6, 27),
                                clip,
                                count > 7,
                                count > 7 ? (double) offset / (count - 7) : 0,
                                identity.applyAsLong(merchant.getMenu().getOffers())
                                        + ":"
                                        + count));
                ScreenRectangle area = null;
                for (GuiEventListener child : screen.children())
                    if (child instanceof MerchantTradeUiAccessor
                            && child instanceof AbstractWidget widget
                            && widget.visible) {
                        ScreenRectangle next = widget.getRectangle();
                        if (area == null) area = next;
                        else {
                            int x = Math.min(area.left(), next.left()),
                                    y = Math.min(area.top(), next.top());
                            area =
                                    new ScreenRectangle(
                                            x,
                                            y,
                                            Math.max(area.right(), next.right()) - x,
                                            Math.max(area.bottom(), next.bottom()) - y);
                        }
                    }
                if (area != null)
                    controls.add(
                            grid(
                                    screen,
                                    "merchant.offers",
                                    area.left(),
                                    area.top(),
                                    area.width(),
                                    area.height(),
                                    merchant.getMenu().getOffers().size()));
            }
        }
        if (screen instanceof AbstractSignEditScreen sign) {
            SignUiAccessor access = (SignUiAccessor) sign;
            String[] messages = access.minecraftProtocolProbe$messages();
            for (int line = 0; line < Math.min(4, messages.length); line++) {
                JsonObject node =
                        keyboardField(
                                sign,
                                "sign.line." + line,
                                "Sign line " + (line + 1),
                                messages[line],
                                access.minecraftProtocolProbe$line() == line,
                                false);
                node.addProperty("focusOffset", line - access.minecraftProtocolProbe$line());
                node.addProperty(
                        "contextKey",
                        access.minecraftProtocolProbe$sign().getBlockPos().toString());
                node.addProperty("lineIndex", line);
                node.addProperty("active", access.minecraftProtocolProbe$valid());
                if (!node.get("active").getAsBoolean()) actions(node);
                controls.add(node);
            }
        }
        if (screen instanceof BookEditScreen book) {
            BookUiAccessor access = (BookUiAccessor) book;
            JsonObject data = new JsonObject();
            data.addProperty("family", "book_edit");
            data.addProperty("pageIndex", access.minecraftProtocolProbe$pageIndex());
            data.addProperty("pageCount", access.minecraftProtocolProbe$pages().size());
            root.add("screenData", data);
            root.addProperty(
                    "editorContextKey", "book.page." + access.minecraftProtocolProbe$pageIndex());
        }
        if (screen instanceof BookViewScreen book) {
            BookViewUiAccessor access = (BookViewUiAccessor) book;
            JsonObject data = new JsonObject();
            data.addProperty("family", "book_read");
            data.addProperty("pageIndex", access.minecraftProtocolProbe$pageIndex());
            data.addProperty("pageCount", access.minecraftProtocolProbe$book().getPageCount());
            data.addProperty(
                    "pageText",
                    VanillaUiTree.bounded(
                            access.minecraftProtocolProbe$book()
                                    .getPage(access.minecraftProtocolProbe$pageIndex())
                                    .getString()));
            root.add("screenData", data);
        }
        if (screen instanceof BookViewScreen) {
            JsonObject data = root.getAsJsonObject("screenData");
            root.addProperty(
                    "editorContextKey", "book.read.page." + data.get("pageIndex").getAsInt());
            JsonObject content =
                    node(
                            screen,
                            "book.content." + data.get("pageIndex").getAsInt(),
                            "Book page content",
                            "text");
            content.add("value", data.get("pageText"));
            content.addProperty("visible", true);
            content.addProperty("active", false);
            content.addProperty("boundsAvailable", false);
            actions(content);
            controls.add(content);
        }
        return controls;
    }

    JsonObject keyboardField(
            Object owner,
            String key,
            String label,
            String value,
            boolean focused,
            boolean multiline) {
        JsonObject node = node(owner, key, label, "text_field");
        node.addProperty("value", VanillaUiTree.bounded(value));
        node.addProperty("active", true);
        node.addProperty("visible", true);
        node.addProperty("focused", focused);
        node.addProperty("multiline", multiline);
        node.addProperty("inputRoute", "screen_keyboard");
        node.addProperty("boundsAvailable", false);
        actions(node, "type_text");
        return node;
    }

    void annotate(Screen screen, GuiEventListener child, JsonObject node) {
        if (screen instanceof BookSignScreen && child instanceof EditBox) {
            node.addProperty("controlKey", "book.title");
            node.addProperty("label", "Book title");
        }
        if (screen instanceof BeaconScreen && child instanceof BeaconButtonUiAccessor button) {
            node.addProperty("selected", button.minecraftProtocolProbe$selected());
            if (child instanceof BeaconPowerUiAccessor power) {
                var effect = power.minecraftProtocolProbe$effect();
                String id =
                        effect.unwrapKey()
                                .map(key -> key.identifier().toString())
                                .orElse("unregistered");
                String key =
                        "beacon."
                                + (power.minecraftProtocolProbe$primary()
                                        ? "primary."
                                        : "secondary.")
                                + id
                                + "."
                                + power.minecraftProtocolProbe$tier();
                node.addProperty("controlKey", key);
                node.addProperty("nodeId", node.get("nodeId").getAsString() + ":" + key);
                node.addProperty("resourceId", id);
                node.addProperty("label", effect.value().getDisplayName().getString());
            } else if (child instanceof BeaconConfirmUiMarker)
                node.addProperty("controlKey", "beacon.confirm");
            else if (child instanceof BeaconCancelUiMarker)
                node.addProperty("controlKey", "beacon.cancel");
        }
        if (child instanceof MultiLineEditBox) node.addProperty("multiline", true);
        if (screen instanceof BookEditScreen book) {
            BookUiAccessor access = (BookUiAccessor) book;
            if (child == access.minecraftProtocolProbe$page()) {
                node.addProperty("controlKey", "book.page");
                node.addProperty(
                        "contextKey", "book.page." + access.minecraftProtocolProbe$pageIndex());
                node.addProperty(
                        "nodeId",
                        node.get("nodeId").getAsString()
                                + ":page:"
                                + access.minecraftProtocolProbe$pageIndex());
                node.addProperty("label", "Book page");
            }
            if (child == access.minecraftProtocolProbe$forward())
                node.addProperty("controlKey", "book.next_page");
            if (child == access.minecraftProtocolProbe$back())
                node.addProperty("controlKey", "book.previous_page");
        }
        if (screen instanceof BookViewScreen book) {
            BookViewUiAccessor access = (BookViewUiAccessor) book;
            if (child == access.minecraftProtocolProbe$forward())
                node.addProperty("controlKey", "book.next_page");
            if (child == access.minecraftProtocolProbe$back())
                node.addProperty("controlKey", "book.previous_page");
        }
        if (screen instanceof MerchantScreen merchant
                && child instanceof MerchantTradeUiAccessor button) {
            int index =
                    button.minecraftProtocolProbe$index()
                            + ((MerchantUiAccessor) merchant).minecraftProtocolProbe$scrollOff();
            var offers = merchant.getMenu().getOffers();
            if (index < 0 || index >= offers.size()) return;
            var offer = offers.get(index);
            ItemStack result = offer.getResult();
            node.addProperty("controlKey", "merchant.trade." + index);
            node.addProperty("label", result.getHoverName().getString());
            node.addProperty("item", BuiltInRegistries.ITEM.getKey(result.getItem()).toString());
            node.addProperty("count", result.getCount());
            node.addProperty("entryIndex", index);
            node.addProperty(
                    "selected",
                    index
                            == ((MerchantUiAccessor) merchant)
                                    .minecraftProtocolProbe$selectedTrade());
            node.addProperty("outOfStock", offer.isOutOfStock());
            node.addProperty(
                    "semanticState",
                    stackKey(offer.getCostA())
                            + ":"
                            + stackKey(offer.getCostB())
                            + ":"
                            + stackKey(result)
                            + ":"
                            + offer.getUses()
                            + ":"
                            + offer.getMaxUses());
            node.addProperty(
                    "nodeId",
                    node.get("nodeId").getAsString()
                            + ":trade:"
                            + index
                            + ":"
                            + node.get("semanticState").getAsString());
        }
    }

    private static String stackKey(ItemStack stack) {
        return BuiltInRegistries.ITEM.getKey(stack.getItem()) + ":" + stack.getCount();
    }

    private JsonObject grid(
            Object owner, String key, int x, int y, int width, int height, int count) {
        JsonObject node = node(owner, key, key, "list");
        node.addProperty("x", x);
        node.addProperty("y", y);
        node.addProperty("width", width);
        node.addProperty("height", height);
        node.addProperty("visible", true);
        node.addProperty("active", true);
        node.addProperty("entryCount", count);
        node.addProperty("collectionTruncated", count > COLLECTION_LIMIT);
        actions(node, "hover", "scroll");
        return node;
    }

    JsonArray scrollbar(
            Object owner,
            String key,
            ScreenRectangle track,
            ScreenRectangle thumb,
            ScreenRectangle clip,
            boolean enabled,
            double offset,
            String context) {
        JsonArray result = new JsonArray();
        // The painted thumb and the native click region differ in some Vanilla menus.
        // Only advertise their intersection, not pixels that cannot start a native drag.
        ScreenRectangle hitClip = VanillaUiTree.intersect(clip, track);
        String state =
                context
                        + ":"
                        + track.left()
                        + ":"
                        + track.top()
                        + ":"
                        + track.width()
                        + ":"
                        + track.height();
        for (int i = 0; i < 2; i++) {
            JsonObject control =
                    hit(
                            owner,
                            key + (i == 0 ? ".track" : ".thumb"),
                            key,
                            i == 0 ? track : thumb,
                            hitClip,
                            enabled,
                            true);
            control.addProperty("role", i == 0 ? "scrollbar" : "scrollbar_thumb");
            control.addProperty("semanticState", state);
            control.addProperty("scrollAmount", offset);
            control.addProperty("maxScrollAmount", enabled ? 1 : 0);
            control.addProperty("dragAxis", "vertical");
            control.addProperty("interactionLayer", 20 + i);
            control.addProperty("capabilitySource", "confirmed_vanilla_input_handler");
            if (enabled && control.get("visible").getAsBoolean())
                actions(control, "hover", "click", "scroll", "mouse_down", "mouse_up", "drag");
            result.add(control);
        }
        return result;
    }

    private void stonecutter(
            StonecutterScreen screen, JsonArray controls, int left, int top, ScreenRectangle clip) {
        StonecutterUiAccessor access = (StonecutterUiAccessor) screen;
        StonecutterMenu menu = screen.getMenu();
        var recipes = menu.getVisibleRecipes().entries();
        float offset = access.minecraftProtocolProbe$scrollOffs();
        controls.addAll(
                scrollbar(
                        screen,
                        "stonecutter.scrollbar",
                        new ScreenRectangle(left + 119, top + 9, 12, 54),
                        new ScreenRectangle(left + 119, top + 15 + (int) (41 * offset), 12, 15),
                        clip,
                        access.minecraftProtocolProbe$displayRecipes() && recipes.size() > 12,
                        offset,
                        identity.applyAsLong(menu.getSlot(0).getItem()) + ":" + recipes.size()));
        if (!access.minecraftProtocolProbe$displayRecipes()) return;
        int start = access.minecraftProtocolProbe$startIndex();
        JsonObject grid =
                grid(screen, "stonecutter.recipes", left + 52, top + 14, 64, 54, recipes.size());
        JsonArray choices = new JsonArray();
        for (int i = 0; i < Math.min(COLLECTION_LIMIT, recipes.size()); i++) {
            var recipe = recipes.get(i).recipe();
            ItemStack stack =
                    recipe.optionDisplay()
                            .resolveForFirstStack(
                                    SlotDisplayContext.fromLevel(Minecraft.getInstance().level));
            int relative = i - start;
            JsonObject node =
                    hit(
                            screen,
                            "stonecutter.recipe." + i,
                            stack.getHoverName().getString(),
                            new ScreenRectangle(
                                    left + 52 + Math.floorMod(relative, 4) * 16,
                                    top + 14 + Math.floorDiv(relative, 4) * 18,
                                    16,
                                    18),
                            VanillaUiTree.intersect(
                                    clip, new ScreenRectangle(left + 52, top + 14, 64, 54)),
                            true,
                            true);
            node.addProperty("item", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            node.addProperty("entryIndex", i);
            node.addProperty("selected", menu.getSelectedRecipeIndex() == i);
            node.addProperty("semanticState", Long.toString(identity.applyAsLong(recipe)));
            node.addProperty(
                    "nodeId",
                    node.get("nodeId").getAsString() + ":" + identity.applyAsLong(recipe));
            scrollChild(node, grid, 18);
            choices.add(node);
        }
        grid.add("children", choices);
        controls.add(grid);
    }

    private void loom(
            LoomScreen screen, JsonArray controls, int left, int top, ScreenRectangle clip) {
        LoomUiAccessor access = (LoomUiAccessor) screen;
        LoomMenu menu = screen.getMenu();
        var patterns = menu.getSelectablePatterns();
        float offset = access.minecraftProtocolProbe$scrollOffs();
        controls.addAll(
                scrollbar(
                        screen,
                        "loom.scrollbar",
                        new ScreenRectangle(left + 119, top + 9, 12, 56),
                        new ScreenRectangle(left + 119, top + 13 + (int) (41 * offset), 12, 15),
                        clip,
                        access.minecraftProtocolProbe$displayPatterns() && patterns.size() > 16,
                        offset,
                        identity.applyAsLong(menu.getSlot(0).getItem())
                                + ":"
                                + identity.applyAsLong(menu.getSlot(1).getItem())
                                + ":"
                                + identity.applyAsLong(menu.getSlot(2).getItem())
                                + ":"
                                + patterns.size()));
        if (!access.minecraftProtocolProbe$displayPatterns()) return;
        int start = access.minecraftProtocolProbe$startRow() * 4;
        JsonObject grid =
                grid(screen, "loom.patterns", left + 60, top + 13, 56, 56, patterns.size());
        JsonArray choices = new JsonArray();
        for (int i = 0; i < Math.min(COLLECTION_LIMIT, patterns.size()); i++) {
            var pattern = patterns.get(i);
            String id =
                    pattern.unwrapKey()
                            .map(key -> key.identifier().toString())
                            .orElse("unregistered");
            int relative = i - start;
            JsonObject node =
                    hit(
                            screen,
                            "loom.pattern." + i,
                            id,
                            new ScreenRectangle(
                                    left + 60 + Math.floorMod(relative, 4) * 14,
                                    top + 13 + Math.floorDiv(relative, 4) * 14,
                                    14,
                                    14),
                            VanillaUiTree.intersect(
                                    clip, new ScreenRectangle(left + 60, top + 13, 56, 56)),
                            true,
                            true);
            node.addProperty("resourceId", id);
            node.addProperty("entryIndex", i);
            node.addProperty("selected", menu.getSelectedBannerPatternIndex() == i);
            node.addProperty("semanticState", Long.toString(identity.applyAsLong(pattern)));
            node.addProperty(
                    "nodeId",
                    node.get("nodeId").getAsString() + ":" + identity.applyAsLong(pattern));
            scrollChild(node, grid, 14);
            choices.add(node);
        }
        grid.add("children", choices);
        controls.add(grid);
    }

    private static void scrollChild(JsonObject node, JsonObject grid, int height) {
        node.addProperty("scrollParentId", grid.get("nodeId").getAsString());
        node.addProperty("contentY", node.get("y").getAsInt());
        node.addProperty("contentHeight", height);
        if (!node.get("visible").getAsBoolean()) actions(node, "scroll_into_view");
    }

    private void enchantments(
            EnchantmentScreen screen, JsonArray controls, int left, int top, ScreenRectangle clip) {
        EnchantmentMenu menu = screen.getMenu();
        var player = Minecraft.getInstance().player;
        for (int i = 0; i < 3; i++) {
            boolean enabled =
                    menu.costs[i] > 0
                            && player != null
                            && (player.getAbilities().instabuild
                                    || (menu.getGoldCount() >= i + 1
                                            && player.experienceLevel >= i + 1
                                            && player.experienceLevel >= menu.costs[i]));
            JsonObject node =
                    hit(
                            screen,
                            "enchantment.option." + i,
                            "Enchantment " + (i + 1) + " — level " + menu.costs[i],
                            new ScreenRectangle(left + 60, top + 14 + i * 19, 108, 19),
                            clip,
                            enabled,
                            true);
            node.addProperty("requiredLevel", menu.costs[i]);
            node.addProperty("enchantmentHintId", menu.enchantClue[i]);
            node.addProperty("hintLevel", menu.levelClue[i]);
            node.addProperty(
                    "semanticState",
                    menu.getEnchantmentSeed()
                            + ":"
                            + menu.costs[i]
                            + ":"
                            + menu.enchantClue[i]
                            + ":"
                            + menu.levelClue[i]);
            node.addProperty(
                    "nodeId",
                    node.get("nodeId").getAsString()
                            + ":"
                            + node.get("semanticState").getAsString());
            controls.add(node);
        }
    }
}
