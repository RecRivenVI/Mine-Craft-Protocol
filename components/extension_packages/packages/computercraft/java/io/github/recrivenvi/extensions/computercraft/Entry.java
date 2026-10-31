package io.github.recrivenvi.extensions.computercraft;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.extension.PackageContext;
import io.github.recrivenvi.minecraftprotocol.probe.extension.PackageExtension;
import io.github.recrivenvi.minecraftprotocol.probe.extension.UiAdapter;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;

/** Observes the client's rendered terminal; editing remains CC's keyboard and mouse callbacks. */
public final class Entry implements PackageExtension, UiAdapter {
    @Override
    public void initialize(PackageContext context) {
        if (context.side() == PackageContext.Side.CLIENT) context.uiAdapter(this);
    }

    @Override
    public Descriptor descriptor() {
        return new Descriptor("computercraft:terminal", Set.of(View.USER, View.DEBUG), 1, 250);
    }

    @Override
    public boolean matches(Frame frame) {
        return frame.screenClass().startsWith("dan200.computercraft.client.gui.");
    }

    @Override
    public Projection capture(Context context) {
        Object screen = context.nativeScreen();
        Object widget = field(screen, "terminal");
        if (widget == null) return null;
        Object terminal = field(screen, "terminalData");
        if (terminal == null) return null;
        int width = number(terminal, "getWidth"), height = number(terminal, "getHeight");
        if (width < 1 || height < 1 || width > 256 || height > 256)
            throw new IllegalStateException("Terminal dimensions exceed observation budget");
        StringBuilder text = new StringBuilder();
        synchronized (terminal) {
            for (int row = 0; row < height; row++) {
                if (row > 0) text.append('\n');
                text.append(call(terminal, "getLine", new Class<?>[] {int.class}, row));
            }
        }
        JsonObject data = new JsonObject();
        data.addProperty("role", "text_field");
        data.addProperty("label", "Computer terminal");
        data.addProperty("labelSource", "client_terminal_text");
        data.addProperty("value", text.toString());
        data.addProperty("valueComplete", true);
        data.addProperty("editable", true);
        data.addProperty("multiline", true);
        data.addProperty("textReplaceStrategy", "append");
        JsonArray actions = new JsonArray();
        List.of("hover", "click", "drag", "scroll", "type_text").forEach(actions::add);
        data.add("actions", actions);
        return new Projection(
                context.frame(),
                System.nanoTime(),
                List.of(
                        new Node(
                                "terminal",
                                context.identify(widget),
                                width + "x" + height,
                                Space.GUI,
                                data)));
    }

    private static int number(Object target, String name) {
        return ((Number) call(target, name, new Class<?>[0])).intValue();
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) {
        try {
            return target.getClass().getMethod(name, types).invoke(target, args);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("CC API unavailable: " + name, failure);
        }
    }

    private static Object field(Object target, String name) {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                if (!field.trySetAccessible())
                    throw new IllegalStateException("CC field inaccessible: " + name);
                return field.get(target);
            } catch (NoSuchFieldException ignored) {
            } catch (IllegalAccessException failure) {
                throw new IllegalStateException(failure);
            }
        }
        throw new IllegalStateException("CC field unavailable: " + name);
    }
}
