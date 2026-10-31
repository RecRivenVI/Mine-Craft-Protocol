package io.github.recrivenvi.extensions.emi;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.extension.*;
import java.lang.reflect.Field;
import java.util.*;

/** Visible recipe/sidebar projections only; no recipe, storage or cheat service is invoked. */
public final class Entry implements PackageExtension, UiAdapter {
    private Class<?> manager;
    private static boolean legacy;
    // Exact 1.20.1 namedToIntermediate ABI; no fallback after a getter has actually run.
    private static final Map<String, String> LEGACY_METHODS =
            Map.of(
                    "getX",
                    "m_252754_",
                    "getY",
                    "m_252907_",
                    "getWidth",
                    "m_5711_",
                    "getHeight",
                    "m_93694_",
                    "getValue",
                    "m_94155_",
                    "isFocused",
                    "m_93696_");
    private static final Map<String, String> LEGACY_FIELDS =
            Map.of("active", "f_93623_", "visible", "f_93624_");

    @Override
    public void initialize(PackageContext context) throws Exception {
        legacy = context.target().equals("1.20.1-forge");
        if (context.side() == PackageContext.Side.CLIENT) {
            manager =
                    Class.forName(
                            "dev.emi.emi.screen.EmiScreenManager",
                            false,
                            getClass().getClassLoader());
            context.uiAdapter(this);
        }
    }

    @Override
    public Descriptor descriptor() {
        return new Descriptor("emi:visible_ui", Set.of(View.USER, View.DEBUG), 512, 250);
    }

    @Override
    public boolean matches(Frame frame) {
        return !frame.screenClass().isEmpty();
    }

    @Override
    public Projection capture(Context context) {
        if (Boolean.TRUE.equals(invoke(null, manager, "isDisabled", new Class<?>[0])))
            return new Projection(context.frame(), System.nanoTime(), List.of());
        Object screen = context.nativeScreen();
        String screenName = screen.getClass().getName();
        boolean recipes = screenName.equals("dev.emi.emi.screen.RecipeScreen");
        boolean container = false;
        for (Class<?> type = screen.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().endsWith(".AbstractContainerScreen")) container = true;
        if (!recipes && !container)
            return new Projection(context.frame(), System.nanoTime(), List.of());
        List<Node> nodes = new ArrayList<>();
        Object search = field(null, manager, "search");
        if (search != null) {
            JsonObject text = data("Search EMI", "text_field");
            text.getAsJsonArray("actions").add("type_text");
            long identity = context.identify(search);
            boolean nativeWidget =
                    contains(context.nativeTree().getAsJsonArray("children"), identity);
            if (nativeWidget) {
                text.remove("visible");
                text.remove("active");
            } else {
                bounds(
                        text,
                        number(search, "getX"),
                        number(search, "getY"),
                        number(search, "getWidth"),
                        number(search, "getHeight"));
                text.addProperty(
                        "visible",
                        Boolean.TRUE.equals(field(search, search.getClass(), "visible")));
                text.addProperty(
                        "active", Boolean.TRUE.equals(field(search, search.getClass(), "active")));
                text.addProperty(
                        "value", Objects.toString(call(search, "getValue", new Class<?>[0])));
                text.addProperty(
                        "focused", Boolean.TRUE.equals(call(search, "isFocused", new Class<?>[0])));
                text.addProperty("editable", true);
                text.addProperty("textReplaceStrategy", "select_all");
            }
            nodes.add(new Node("search", nativeWidget ? identity : 0, "search", Space.GUI, text));
        }
        List<?> panels = (List<?>) field(null, manager, "panels");
        if (panels != null)
            for (int pi = 0; pi < panels.size(); pi++) {
                Object panel = panels.get(pi);
                if (!Boolean.TRUE.equals(call(panel, "isVisible", new Class<?>[0]))) continue;
                int page = ((Number) field(panel, panel.getClass(), "page")).intValue();
                String panelType = Objects.toString(call(panel, "getType", new Class<?>[0]));
                for (String button : List.of("pageLeft", "pageRight", "cycle")) {
                    Object widget = field(panel, panel.getClass(), button);
                    if (widget != null)
                        addWidget(
                                nodes,
                                context,
                                "panel:" + pi + ":" + button,
                                widget,
                                button + " " + panelType);
                }
                List<?> spaces = (List<?>) call(panel, "getSpaces", new Class<?>[0]);
                for (int si = 0; si < spaces.size(); si++) {
                    Object space = spaces.get(si);
                    List<?> stacks =
                            (List<?>) call(space, "getPage", new Class<?>[] {int.class}, page);
                    for (int index = 0; index < stacks.size() && nodes.size() < 500; index++) {
                        Object ingredient = stacks.get(index);
                        int x = number(space, "getRawX", index),
                                y = number(space, "getRawY", index);
                        if (!unobscured(space, x + 9, y + 9)) continue;
                        String item = itemId(ingredient);
                        JsonObject node = data(item, "ingredient");
                        bounds(node, x, y, 18, 18);
                        if (!item.equals("Multiple visible ingredient variants"))
                            node.addProperty("item", item);
                        node.addProperty(
                                "count",
                                ((Number) call(ingredient, "getAmount", new Class<?>[0]))
                                        .longValue());
                        String key = "panel:" + pi + ":" + si + ":" + page + ":" + index;
                        nodes.add(
                                new Node(
                                        key,
                                        0,
                                        panelType
                                                + ":"
                                                + page
                                                + ":"
                                                + item
                                                + ":"
                                                + System.identityHashCode(ingredient),
                                        Space.GUI,
                                        node));
                    }
                }
            }
        if (recipes) {
            List<?> groups = (List<?>) field(screen, screen.getClass(), "currentPage");
            if (groups != null)
                for (int gi = 0; gi < groups.size(); gi++) {
                    Object group = groups.get(gi);
                    int gx = ((Number) field(group, group.getClass(), "x")).intValue(),
                            gy = ((Number) field(group, group.getClass(), "y")).intValue();
                    List<?> widgets = (List<?>) field(group, group.getClass(), "widgets");
                    for (int wi = 0; wi < widgets.size() && nodes.size() < 512; wi++) {
                        Object widget = widgets.get(wi);
                        Object rect = call(widget, "getBounds", new Class<?>[0]);
                        if (number(rect, "width") <= 0 || number(rect, "height") <= 0) continue;
                        String label = widget.getClass().getSimpleName();
                        String role = "region";
                        if (isType(widget, "dev.emi.emi.api.widget.SlotWidget")) {
                            label = itemId(call(widget, "getStack", new Class<?>[0]));
                            role = "ingredient";
                        }
                        JsonObject node = data(label, role);
                        bounds(
                                node,
                                gx + number(rect, "x"),
                                gy + number(rect, "y"),
                                number(rect, "width"),
                                number(rect, "height"));
                        nodes.add(
                                new Node(
                                        "recipe:" + gi + ":" + wi,
                                        0,
                                        widget.getClass().getName() + ":" + label,
                                        Space.GUI,
                                        node));
                    }
                }
        }
        return new Projection(context.frame(), System.nanoTime(), nodes);
    }

    private boolean unobscured(Object space, int x, int y) {
        if (!Boolean.TRUE.equals(
                call(space, "contains", new Class<?>[] {int.class, int.class}, x, y))) return false;
        var exclusions = (List<?>) field(null, manager, "lastExclusion");
        if (exclusions != null)
            for (var bounds : exclusions)
                if (Boolean.TRUE.equals(
                        call(bounds, "contains", new Class<?>[] {int.class, int.class}, x, y)))
                    return false;
        return true;
    }

    private static boolean contains(JsonArray nodes, long identity) {
        for (var element : nodes) {
            if (!element.isJsonObject()) continue;
            var node = element.getAsJsonObject();
            if (node.has("elementIdentity") && node.get("elementIdentity").getAsLong() == identity)
                return true;
            if (node.has("children") && contains(node.getAsJsonArray("children"), identity))
                return true;
        }
        return false;
    }

    private static JsonObject data(String label, String role) {
        JsonObject data = new JsonObject();
        data.addProperty("label", label);
        data.addProperty("labelSource", "visible_emi_content");
        data.addProperty("role", role);
        data.addProperty("visible", true);
        data.addProperty("active", true);
        JsonArray actions = new JsonArray();
        List.of("hover", "click", "drag", "scroll").forEach(actions::add);
        data.add("actions", actions);
        return data;
    }

    private static void bounds(JsonObject data, int x, int y, int w, int h) {
        data.addProperty("x", x);
        data.addProperty("y", y);
        data.addProperty("width", w);
        data.addProperty("height", h);
    }

    private static void addWidget(
            List<Node> nodes, Context context, String key, Object widget, String label) {
        if (!Boolean.TRUE.equals(field(widget, widget.getClass(), "visible"))) return;
        JsonObject node = data(label, "button");
        bounds(
                node,
                number(widget, "getX"),
                number(widget, "getY"),
                number(widget, "getWidth"),
                number(widget, "getHeight"));
        node.addProperty("active", Boolean.TRUE.equals(field(widget, widget.getClass(), "active")));
        nodes.add(new Node(key, 0, key, Space.GUI, node));
    }

    private static String itemId(Object ingredient) {
        var stacks = (List<?>) call(ingredient, "getEmiStacks", new Class<?>[0]);
        return stacks.isEmpty()
                ? "empty"
                : stacks.size() != 1
                        ? "Multiple visible ingredient variants"
                        : Objects.toString(call(stacks.get(0), "getId", new Class<?>[0]));
    }

    private static boolean isType(Object value, String name) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals(name)) return true;
        return false;
    }

    private static int number(Object value, String method) {
        return ((Number) call(value, method, new Class<?>[0])).intValue();
    }

    private static int number(Object value, String method, int argument) {
        return ((Number) call(value, method, new Class<?>[] {int.class}, argument)).intValue();
    }

    private static Object call(Object value, String name, Class<?>[] args, Object... values) {
        return invoke(value, value.getClass(), name, args, values);
    }

    private static Object invoke(
            Object value, Class<?> type, String name, Class<?>[] args, Object... values) {
        try {
            java.lang.reflect.Method method;
            try {
                method = type.getMethod(name, args);
            } catch (NoSuchMethodException missing) {
                if (!legacy || !LEGACY_METHODS.containsKey(name)) throw missing;
                method = type.getMethod(LEGACY_METHODS.get(name), args);
            }
            return method.invoke(value, values);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("EMI API unavailable: " + name, failure);
        }
    }

    private static Object field(Object value, Class<?> owner, String name) {
        for (Class<?> type = owner; type != null; type = type.getSuperclass())
            try {
                Field field;
                try {
                    field = type.getDeclaredField(name);
                } catch (NoSuchFieldException missing) {
                    if (!legacy
                            || !type.getName().startsWith("net.minecraft.")
                            || !LEGACY_FIELDS.containsKey(name)) throw missing;
                    field = type.getDeclaredField(LEGACY_FIELDS.get(name));
                }
                if (!field.trySetAccessible())
                    throw new IllegalStateException("EMI field inaccessible");
                return field.get(value);
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException(failure);
            }
        throw new IllegalStateException("EMI field unavailable: " + name);
    }
}
