package io.github.recrivenvi.extensions.ae2;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.extension.*;
import java.lang.reflect.*;
import java.util.*;

/** Client-rendered storage entries and framework widgets, never server storage/crafting calls. */
public final class Entry implements PackageExtension, UiAdapter {
    public void initialize(PackageContext context) {
        if (context.side() == PackageContext.Side.CLIENT) context.uiAdapter(this);
        io.github.recrivenvi.extensions.ae2.support.ReadProvider.register(
                context,
                "machine",
                "server_thread",
                io.github.recrivenvi.extensions.ae2.support.NativeAccess::blockQuery,
                query -> machine(context, query));
    }

    public Descriptor descriptor() {
        return new Descriptor("ae2:visible_ui", Set.of(View.USER, View.DEBUG), 512, 250);
    }

    public boolean matches(Frame frame) {
        return frame.screenClass().startsWith("appeng.client.gui.");
    }

    public Projection capture(Context context) {
        Object screen = context.nativeScreen();
        List<Node> result = new ArrayList<>();
        JsonArray nativeNodes = context.nativeTree().getAsJsonArray("children");
        Object container = field(screen, "widgets");
        var widgets = (Map<?, ?>) field(container, "widgets");
        for (var entry : widgets.entrySet()) {
            Object widget = entry.getValue();
            long identity = context.identify(widget);
            JsonObject nativeNode = find(nativeNodes, identity);
            if (nativeNode == null) continue;
            JsonObject data = new JsonObject();
            if (!nativeNode.has("label") || nativeNode.get("label").getAsString().isBlank()) {
                data.addProperty("label", "AE2 " + entry.getKey());
                data.addProperty("labelSource", "registered_visible_widget");
            }
            result.add(
                    new Node(
                            "widget:" + context.identify(widget),
                            identity,
                            "widget:" + context.identify(widget),
                            Space.GUI,
                            data));
        }
        for (Object slot : (List<?>) declared(screen, "getInventorySlots")) {
            if (!slot.getClass().getName().equals("appeng.client.gui.me.common.RepoSlot")) continue;
            long identity = context.identify(slot);
            JsonObject nativeNode = find(nativeNodes, identity);
            if (nativeNode == null
                    || !nativeNode.has("visible")
                    || !nativeNode.get("visible").getAsBoolean()) continue;
            Object entry = call(slot, "getEntry");
            if (entry == null) continue;
            Object what = call(entry, "getWhat");
            String id = Objects.toString(call(what, "getId"));
            JsonObject data = new JsonObject(), value = new JsonObject();
            data.addProperty("role", "virtual_storage_entry");
            data.addProperty("label", id);
            data.addProperty("labelSource", "client_visible_grid_entry");
            value.addProperty(
                    "storedAmount", ((Number) call(entry, "getStoredAmount")).longValue());
            value.addProperty(
                    "requestableAmount",
                    ((Number) call(entry, "getRequestableAmount")).longValue());
            value.addProperty("craftable", Boolean.TRUE.equals(call(entry, "isCraftable")));
            data.add("value", value);
            data.addProperty("valueSemantics", "client_visible_network_entry");
            JsonArray actions = new JsonArray();
            List.of("hover", "click", "right_click", "shift_click", "drag", "scroll")
                    .forEach(actions::add);
            data.add("actions", actions);
            result.add(
                    new Node(
                            "entry:" + identity,
                            identity,
                            "entry:" + call(entry, "getSerial") + ":" + id,
                            Space.GUI,
                            data));
        }
        int left = ((Number) call(screen, "getGuiLeft")).intValue(),
                top = ((Number) call(screen, "getGuiTop")).intValue();
        for (var entry : ((Map<?, ?>) field(container, "compositeWidgets")).entrySet()) {
            Object widget = entry.getValue();
            if (!widget.getClass().getName().equals("appeng.client.gui.widgets.Scrollbar")
                    || !Boolean.TRUE.equals(call(widget, "isVisible"))) continue;
            Object bounds = call(widget, "getBounds");
            int width = ((Number) call(bounds, "getWidth")).intValue(),
                    height = ((Number) call(bounds, "getHeight")).intValue();
            if (width <= 0 || height <= 0) continue;
            JsonObject data = new JsonObject();
            data.addProperty("role", "region");
            data.addProperty("label", "AE2 " + entry.getKey());
            data.addProperty("labelSource", "registered_visible_widget");
            data.addProperty("x", left + ((Number) call(bounds, "getX")).intValue());
            data.addProperty("y", top + ((Number) call(bounds, "getY")).intValue());
            data.addProperty("width", width);
            data.addProperty("height", height);
            data.addProperty("visible", true);
            data.addProperty("active", true);
            data.addProperty(
                    "scrollAmount", ((Number) call(widget, "getCurrentScroll")).intValue());
            data.addProperty("canPanY", true);
            JsonArray actions = new JsonArray();
            List.of("hover", "click", "drag", "scroll").forEach(actions::add);
            data.add("actions", actions);
            result.add(
                    new Node(
                            "composite:" + context.identify(widget),
                            0,
                            "scrollbar:" + System.identityHashCode(widget),
                            Space.GUI,
                            data));
        }
        if (screen.getClass().getName().endsWith("CraftConfirmScreen"))
            craftTable(context, screen, result, left, top);
        return new Projection(context.frame(), System.nanoTime(), result);
    }

    private static Object nativeCall(Object object, String name, Object... args) {
        return io.github.recrivenvi.extensions.ae2.support.NativeAccess.call(object, name, args);
    }

    private static int integerField(Object object, String name) {
        return ((Number)
                        io.github.recrivenvi.extensions.ae2.support.NativeAccess.field(
                                object, name))
                .intValue();
    }

    private static void craftTable(
            Context context, Object screen, List<Node> nodes, int left, int top) {
        Object menu = field(screen, "menu"), plan = nativeCall(menu, "getPlan");
        if (plan == null) return;
        Object table = field(screen, "table");
        int offset =
                ((Number) nativeCall(field(screen, "scrollbar"), "getCurrentScroll")).intValue();
        int rows = integerField(table, "rows"),
                columns = integerField(table, "COLS"),
                width = integerField(table, "CELL_WIDTH"),
                height = integerField(table, "CELL_HEIGHT");
        if (rows < 0
                || rows > 32
                || columns < 1
                || columns > 8
                || width < 1
                || width > 512
                || height < 1
                || height > 512)
            throw new IllegalStateException("Unsupported native crafting table layout");
        var entries = (List<?>) nativeCall(plan, "getEntries");
        for (int row = 0; row < rows; row++)
            for (int column = 0; column < columns; column++) {
                int index = (offset + row) * columns + column;
                if (index < 0 || index >= entries.size()) continue;
                Object entry = entries.get(index),
                        stack = nativeCall(table, "getEntryStack", entry);
                var lines = new ArrayList<String>();
                for (Object text : (List<?>) nativeCall(table, "getEntryDescription", entry)) {
                    if (lines.size() == 8) break;
                    lines.add(Objects.toString(nativeCall(text, "getString")));
                }
                String id = Objects.toString(nativeCall(stack, "getId"));
                var data = new JsonObject();
                data.addProperty("role", "region");
                data.addProperty("label", id + "\n" + String.join("\n", lines));
                data.addProperty("labelSource", "native_visible_crafting_table");
                data.addProperty("x", left + integerField(table, "x") + column * width);
                data.addProperty("y", top + integerField(table, "y") + row * height);
                data.addProperty("width", width);
                data.addProperty("height", height);
                data.addProperty("visible", true);
                data.addProperty("active", true);
                var actions = new JsonArray();
                actions.add("hover");
                actions.add("scroll");
                data.add("actions", actions);
                var value = new JsonObject();
                value.addProperty(
                        "usedBytes", ((Number) nativeCall(plan, "getUsedBytes")).longValue());
                value.addProperty(
                        "simulation", Boolean.TRUE.equals(nativeCall(plan, "isSimulation")));
                value.addProperty("noCPU", Boolean.TRUE.equals(nativeCall(menu, "hasNoCPU")));
                data.add("value", value);
                nodes.add(
                        new Node(
                                "craft-plan:" + index,
                                0,
                                revision(id + ":" + lines + ":" + value),
                                Space.GUI,
                                data));
            }
    }

    private static String revision(String value) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            value.getBytes(
                                                    java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static JsonObject machine(PackageContext context, JsonObject query) {
        Object entity =
                io.github.recrivenvi.extensions.ae2.support.NativeAccess.loadedBlockEntity(
                        context, query);
        var result = new JsonObject();
        result.addProperty("available", entity != null);
        result.addProperty("chunkLoadRequested", false);
        if (entity == null) return result;
        result.addProperty("nativeType", entity.getClass().getName());
        if (!entity.getClass().getName().startsWith("appeng.blockentity.")) {
            result.addProperty("supported", false);
            return result;
        }
        result.addProperty("supported", true);
        Object managed =
                io.github.recrivenvi.extensions.ae2.support.NativeAccess.call(
                        entity, "getMainNode");
        result.addProperty(
                "online",
                Boolean.TRUE.equals(
                        io.github.recrivenvi.extensions.ae2.support.NativeAccess.call(
                                managed, "isOnline")));
        Object node =
                io.github.recrivenvi.extensions.ae2.support.NativeAccess.call(managed, "getNode");
        if (node != null) {
            Object grid =
                    io.github.recrivenvi.extensions.ae2.support.NativeAccess.call(node, "getGrid");
            int count = 0;
            for (Object ignored :
                    (Iterable<?>)
                            io.github.recrivenvi.extensions.ae2.support.NativeAccess.call(
                                    grid, "getNodes")) {
                if (count++ == 128) {
                    result.addProperty("nodesTruncated", true);
                    break;
                }
            }
            result.addProperty("nodeCount", Math.min(count, 128));
            var cpus = new JsonArray();
            for (Object cpu :
                    (Iterable<?>) nativeCall(nativeCall(grid, "getCraftingService"), "getCpus")) {
                if (cpus.size() == 16) break;
                var state = new JsonObject();
                state.addProperty("busy", Boolean.TRUE.equals(nativeCall(cpu, "isBusy")));
                state.addProperty(
                        "storage", ((Number) nativeCall(cpu, "getAvailableStorage")).longValue());
                state.addProperty(
                        "coprocessors", ((Number) nativeCall(cpu, "getCoProcessors")).intValue());
                cpus.add(state);
            }
            result.add("craftingCpus", cpus);
        }
        if (entity.getClass().getName().endsWith("DriveBlockEntity")) {
            result.addProperty(
                    "powered",
                    Boolean.TRUE.equals(
                            io.github.recrivenvi.extensions.ae2.support.NativeAccess.call(
                                    entity, "isPowered")));
            int count =
                    ((Number)
                                    io.github.recrivenvi.extensions.ae2.support.NativeAccess.call(
                                            entity, "getCellCount"))
                            .intValue();
            result.addProperty("cellCount", count);
            var cells = new JsonArray();
            for (int i = 0; i < Math.min(count, 16); i++) {
                var cell = new JsonObject();
                cell.addProperty("slot", i);
                cell.addProperty(
                        "state",
                        Objects.toString(
                                io.github.recrivenvi.extensions.ae2.support.NativeAccess.call(
                                        entity, "getCellStatus", i)));
                cells.add(cell);
            }
            result.add("cells", cells);
        }
        return result;
    }

    private static JsonObject find(JsonArray nodes, long identity) {
        for (var element : nodes)
            if (element.isJsonObject()) {
                JsonObject node = element.getAsJsonObject();
                if (node.has("elementIdentity")
                        && node.get("elementIdentity").getAsLong() == identity) return node;
                if (node.has("children")) {
                    var nested = find(node.getAsJsonArray("children"), identity);
                    if (nested != null) return nested;
                }
            }
        return null;
    }

    private static Object call(Object value, String method) {
        return nativeCall(value, method);
    }

    private static Object declared(Object value, String method) {
        for (Class<?> owner = value.getClass(); owner != null; owner = owner.getSuperclass())
            try {
                Method member = owner.getDeclaredMethod(method);
                if (!member.trySetAccessible())
                    throw new IllegalStateException("AE2 visible API inaccessible");
                return member.invoke(value);
            } catch (NoSuchMethodException missing) {
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(failure);
            }
        throw new IllegalStateException("AE2 visible API missing: " + method);
    }

    private static Object field(Object value, String name) {
        for (Class<?> owner = value.getClass(); owner != null; owner = owner.getSuperclass())
            try {
                Field field = owner.getDeclaredField(name);
                if (!field.trySetAccessible())
                    throw new IllegalStateException("AE2 visible field inaccessible");
                return field.get(value);
            } catch (NoSuchFieldException missing) {
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException(failure);
            }
        throw new IllegalStateException("AE2 visible field missing: " + name);
    }
}
