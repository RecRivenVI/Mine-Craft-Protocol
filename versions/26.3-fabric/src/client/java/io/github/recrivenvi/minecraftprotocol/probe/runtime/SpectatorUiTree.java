package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import com.google.gson.*;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.*;
import java.util.function.ToLongFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.spectator.SpectatorMenu;
import net.minecraft.world.level.GameType;

/** Native shortcuts, not mouse hit boxes or internal spectator action callbacks. */
final class SpectatorUiTree {
    static JsonArray capture(Minecraft client, JsonObject root, ToLongFunction<Object> identity) {
        JsonArray nodes = new JsonArray();
        if (client.player == null
                || client.gameMode == null
                || client.gameMode.getPlayerMode() != GameType.SPECTATOR) return nodes;
        var gui = client.gui.hud.getSpectatorGui();
        var access = (SpectatorGuiUiAccessor) gui;
        var menu = access.minecraftProtocolProbe$menu();
        boolean shown =
                !client.gui.hud.isHidden()
                        && client.gui.screen() == null
                        && client.gui.overlay() == null;
        root.addProperty("uiInputContext", menu == null ? "spectator" : "spectator_menu");
        if (menu == null) {
            root.addProperty(
                    "spectatorContextKey",
                    "spectator:closed:" + identity.applyAsLong(client.player));
            JsonObject open =
                    node(
                            "spectator.open",
                            root.get("spectatorContextKey").getAsString(),
                            identity.applyAsLong(gui));
            open.addProperty("role", "shortcut");
            open.addProperty("visible", shown);
            open.addProperty("inputRoute", "spectator_open");
            open.add("actions", actions("open"));
            nodes.add(open);
            return nodes;
        }
        int page = ((SpectatorMenuUiAccessor) menu).minecraftProtocolProbe$page();
        int selected = menu.getSelectedSlot();
        String context =
                "spectator:"
                        + identity.applyAsLong(menu)
                        + ":"
                        + identity.applyAsLong(menu.getSelectedCategory())
                        + ":"
                        + page
                        + ":"
                        + selected;
        root.addProperty("spectatorContextKey", context);
        float alpha = access.minecraftProtocolProbe$alpha();
        boolean visible = shown && alpha > 0;
        var selectedItem = selected < 0 ? SpectatorMenu.EMPTY_SLOT : menu.getItem(selected);
        JsonObject prompt = node("spectator.prompt", context, identity.applyAsLong(gui));
        prompt.addProperty("role", "hud_text");
        prompt.addProperty("active", false);
        prompt.addProperty("visible", visible);
        prompt.addProperty(
                "label",
                (selectedItem == SpectatorMenu.EMPTY_SLOT
                                ? menu.getSelectedCategory().getPrompt()
                                : selectedItem.getName())
                        .getString());
        prompt.add("actions", actions());
        nodes.add(prompt);
        int x = client.getWindow().getGuiScaledWidth() / 2 - 88;
        int y = (int) Math.floor(client.getWindow().getGuiScaledHeight() - 22 * alpha) + 3;
        for (int slot = 0; slot < 9; slot++) {
            var item = menu.getItem(slot);
            if (item == SpectatorMenu.EMPTY_SLOT) continue;
            var mapping = client.options.keyHotbarSlots[slot];
            var key = ((KeyBindingUiAccessor) mapping).minecraftProtocolProbe$key();
            int wire = -1;
            if (key.getType() == InputConstants.Type.KEYBOARD) {
                wire = NativeInputMapping.wireKey(key.getValue());
            }
            // A modified Loader binding must not be silently dispatched without its physical
            // modifier.
            boolean supported = wire >= 0;
            String keyContext =
                    context + ":" + key.getName() + ":" + supported + ":" + item.isEnabled();
            JsonObject node =
                    node("spectator.slot." + slot, keyContext, identity.applyAsLong(item));
            node.addProperty(
                    "nodeId", "ui:" + keyContext + ":" + slot + ":" + identity.applyAsLong(item));
            node.addProperty("role", "spectator_item");
            node.addProperty("visible", visible);
            node.addProperty("active", supported);
            node.addProperty("selected", slot == selected);
            node.addProperty("activationEnabled", item.isEnabled());
            node.addProperty("label", slot == selected ? item.getName().getString() : "");
            node.addProperty("debugLabel", item.getName().getString());
            node.addProperty("entryIndex", slot);
            node.addProperty("inputRoute", "spectator_hotbar");
            node.addProperty("inputKey", wire);
            node.addProperty("boundsAvailable", true);
            node.addProperty("boundsSemantics", "display_only");
            node.addProperty("x", x + slot * 20);
            node.addProperty("y", y);
            node.addProperty("width", 16);
            node.addProperty("height", 16);
            node.add(
                    "actions",
                    supported
                            ? slot == selected && item.isEnabled()
                                    ? actions("select", "click", "activate")
                                    : actions("select", "click")
                            : actions());
            nodes.add(node);
        }
        return nodes;
    }

    private static JsonObject node(String key, String context, long identity) {
        JsonObject n = new JsonObject();
        n.addProperty("nodeId", "ui:" + context + ":" + identity);
        n.addProperty("elementIdentity", identity);
        n.addProperty("controlKey", key);
        n.addProperty("contextKey", context);
        n.addProperty("capabilitySource", "native_spectator_shortcut");
        n.addProperty("coverage", "semantic_native");
        n.addProperty("acquisition", "typed_vanilla_adapter");
        n.addProperty("label", "");
        n.addProperty("active", true);
        n.addProperty("boundsAvailable", false);
        return n;
    }

    private static JsonArray actions(String... names) {
        JsonArray a = new JsonArray();
        for (String name : names) a.add(name);
        return a;
    }
}
