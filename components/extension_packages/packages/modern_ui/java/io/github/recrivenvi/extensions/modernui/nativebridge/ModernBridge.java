package io.github.recrivenvi.extensions.modernui.nativebridge;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.extension.LogicalInput;
import io.github.recrivenvi.minecraftprotocol.probe.extension.UiAdapter;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

/** Native helper shared by the Mixin and private entry class spaces. Never dispatches a click. */
public final class ModernBridge {
    private record Pending(Object root, Object event, LogicalInput.Delivery delivery) {}

    private static final Map<Object, Pending> EVENTS = new ConcurrentHashMap<>();
    private static volatile java.util.function.Supplier<
                    io.github.recrivenvi.minecraftprotocol.probe.extension.PackageContext
                            .ControlState>
            presence;

    public static void presence(
            java.util.function.Supplier<
                            io.github.recrivenvi.minecraftprotocol.probe.extension.PackageContext
                                    .ControlState>
                    value) {
        presence = value;
    }

    private static boolean takeover() {
        var current = presence;
        return current != null && current.get().takeoverActive();
    }

    public static boolean rejectUnownedInput() {
        return takeover() && LogicalInput.current() == null;
    }

    private static final AtomicReference<UiAdapter.Projection> SNAPSHOT = new AtomicReference<>();
    private static final AtomicBoolean CAPTURE_PENDING = new AtomicBoolean();
    private static final Map<Object, Long> IDS = new WeakHashMap<>();

    private record Capture(UiAdapter.Frame frame, int screenHash) {}

    private static volatile Capture requested;
    private static volatile RuntimeException captureFailure;
    private static long nextId;

    private ModernBridge() {}

    public static int mouseButton(long window, int button) {
        var state = LogicalInput.current();
        if (state != null && state.window() == window) return state.buttonDown(button) ? 1 : 0;
        if (takeover()) return 0;
        return ((Number)
                        staticCall(
                                "org.lwjgl.glfw.GLFW",
                                "glfwGetMouseButton",
                                new Class<?>[] {long.class, int.class},
                                window,
                                button))
                .intValue();
    }

    public static void queue(Object root, Object event) {
        if (LogicalInput.current() == null) {
            if (takeover()) {
                call(event, "recycle", new Class<?>[0]);
                return;
            }
            call(
                    root,
                    "enqueueInputEvent",
                    new Class<?>[] {type("icyllis.modernui.view.InputEvent")},
                    event);
            return;
        }
        var state = LogicalInput.current();
        if (state.cleanupOnly()) {
            // Runtime awaits reset's owner-thread barrier. A second queued release would be
            // cancelled by overlapping reset calls and incorrectly fail the same cleanup.
            call(event, "recycle", new Class<?>[0]);
            return;
        }
        Object currentScreen = field(manager(), "mScreen");
        if (currentScreen == null
                || System.identityHashCode(currentScreen) != state.screenObjectHash()) {
            call(event, "recycle", new Class<?>[0]);
            return;
        }
        if (EVENTS.size() >= 256) throw new IllegalStateException("Framework queue budget");
        var delivery = LogicalInput.defer();
        var pending = new Pending(root, event, delivery);
        EVENTS.put(event, pending);
        try {
            postUi(
                    () -> {
                        boolean[] nativeOwned = {false};
                        try {
                            delivery.deliver(
                                    ModernBridge::isUiThread,
                                    () -> {
                                        if (!targetCurrent(delivery.snapshot().target()))
                                            throw new LogicalInput.TargetInvalidated(
                                                    "Framework target changed before consumption");
                                        var nativeQueue =
                                                (ConcurrentLinkedQueue<?>)
                                                        field(root, "mInputEvents");
                                        if (!nativeQueue.isEmpty() || field(root, "mView") == null)
                                            throw new LogicalInput.TargetInvalidated(
                                                    "Framework native queue/context changed; re-observe before retrying");
                                        call(
                                                root,
                                                "enqueueInputEvent",
                                                new Class<?>[] {
                                                    type("icyllis.modernui.view.InputEvent")
                                                },
                                                event);
                                        nativeOwned[0] = true;
                                        try {
                                            declared(root, "doProcessInputEvents");
                                        } finally {
                                            refreshSnapshot();
                                        }
                                    });
                        } catch (RuntimeException rejected) {
                            // Delivery already completed exceptionally; do not crash the UI worker.
                        } finally {
                            if (!nativeOwned[0]) call(event, "recycle", new Class<?>[0]);
                            EVENTS.remove(event, pending);
                        }
                    });
        } catch (RuntimeException failure) {
            EVENTS.remove(event, pending);
            delivery.targetInvalidated("Framework owner rejected input");
            call(event, "recycle", new Class<?>[0]);
            throw failure;
        }
    }

    private static Object declared(Object object, String name) {
        for (Class<?> owner = object.getClass(); owner != null; owner = owner.getSuperclass())
            try {
                var method = owner.getDeclaredMethod(name);
                if (!method.trySetAccessible())
                    throw new IllegalStateException("Framework input method inaccessible");
                return method.invoke(object);
            } catch (NoSuchMethodException ignored) {
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Framework input consumption failed", failure);
            }
        throw new IllegalStateException("Framework input method unavailable: " + name);
    }

    private static boolean targetCurrent(LogicalInput.Target target) {
        if (target == null || !target.capability().equals("modern_ui:visible_views")) return true;
        for (var entry : IDS.entrySet())
            if (target.key().equals("view:" + entry.getValue())) {
                Object view = entry.getKey();
                if (!targetRevision(view).equals(target.revision())) return false;
                if (target.textFocusRequired()
                        && !Boolean.TRUE.equals(call(view, "isFocused", new Class<?>[0])))
                    return false;
                if (!Boolean.TRUE.equals(call(view, "isShown", new Class<?>[0]))
                        || !Boolean.TRUE.equals(call(view, "isEnabled", new Class<?>[0])))
                    return false;
                try {
                    Class<?> rectType = type("icyllis.modernui.graphics.Rect");
                    Object rect = rectType.getConstructor().newInstance();
                    if (!Boolean.TRUE.equals(
                            call(view, "getGlobalVisibleRect", new Class<?>[] {rectType}, rect)))
                        return false;
                    double sx = (double) target.guiWidth() / target.framebufferWidth(),
                            sy = (double) target.guiHeight() / target.framebufferHeight();
                    double x = Math.max(0, rectType.getField("left").getInt(rect) * sx),
                            y = Math.max(0, rectType.getField("top").getInt(rect) * sy);
                    double
                            right =
                                    Math.min(
                                            target.guiWidth(),
                                            rectType.getField("right").getInt(rect) * sx),
                            bottom =
                                    Math.min(
                                            target.guiHeight(),
                                            rectType.getField("bottom").getInt(rect) * sy);
                    return Math.abs(x - target.x()) < 1e-6
                            && Math.abs(y - target.y()) < 1e-6
                            && Math.abs(Math.max(0, right - x) - target.width()) < 1e-6
                            && Math.abs(Math.max(0, bottom - y) - target.height()) < 1e-6;
                } catch (ReflectiveOperationException failure) {
                    return false;
                }
            }
        return false;
    }

    public static void postMain(Runnable action) {
        if (LogicalInput.current() != null
                && Boolean.TRUE.equals(
                        staticCall(
                                "icyllis.modernui.core.Core", "isOnMainThread", new Class<?>[0])))
            action.run();
        else
            staticCall(
                    "icyllis.modernui.core.Core",
                    "postOnMainThread",
                    new Class<?>[] {Runnable.class},
                    action);
    }

    public static Runnable textDelivery(Runnable action) {
        if (LogicalInput.current() == null) return action;
        var delivery = LogicalInput.defer();
        return () -> {
            try {
                delivery.deliver(
                        ModernBridge::isUiThread,
                        () -> {
                            try {
                                action.run();
                            } finally {
                                refreshSnapshot();
                            }
                        });
            } catch (RuntimeException rejected) {
                // The originating Runtime operation observes the failed acknowledgement.
            }
        };
    }

    public static UiAdapter.Projection snapshot(UiAdapter.Frame frame, int screenHash) {
        requested = new Capture(frame, screenHash);
        renderedFrame();
        var failure = captureFailure;
        if (failure != null) throw failure;
        var value = SNAPSHOT.get();
        return value != null && value.frame().equals(frame) ? value : null;
    }

    public static void renderedFrame() {
        if (requested == null) return;
        if (CAPTURE_PENDING.compareAndSet(false, true)) {
            try {
                postUi(
                        () -> {
                            try {
                                refreshSnapshot();
                            } finally {
                                CAPTURE_PENDING.set(false);
                            }
                        });
            } catch (RuntimeException failure) {
                CAPTURE_PENDING.set(false);
                throw failure;
            }
        }
    }

    private static void refreshSnapshot() {
        var request = requested;
        if (request == null) return;
        try {
            Object manager = manager(), screen = field(manager, "mScreen");
            if (screen == null || System.identityHashCode(screen) != request.screenHash()) {
                SNAPSHOT.set(null);
                return;
            }
            Object decor = call(manager, "getDecorView", new Class<?>[0]);
            List<UiAdapter.Node> nodes = new ArrayList<>();
            if (decor != null) visit(decor, nodes, 0);
            if (request.equals(requested))
                SNAPSHOT.set(new UiAdapter.Projection(request.frame(), System.nanoTime(), nodes));
            captureFailure = null;
        } catch (RuntimeException failure) {
            SNAPSHOT.set(null);
            captureFailure = failure;
        }
    }

    private static void visit(Object view, List<UiAdapter.Node> nodes, int depth) {
        if (depth > 24
                || nodes.size() >= 512
                || !Boolean.TRUE.equals(call(view, "isShown", new Class<?>[0]))) return;
        try {
            Class<?> rectType = type("icyllis.modernui.graphics.Rect");
            Object rect = rectType.getConstructor().newInstance();
            if (!Boolean.TRUE.equals(
                    call(view, "getGlobalVisibleRect", new Class<?>[] {rectType}, rect))) return;
            int x = rectType.getField("left").getInt(rect),
                    y = rectType.getField("top").getInt(rect);
            int w = rectType.getField("right").getInt(rect) - x,
                    h = rectType.getField("bottom").getInt(rect) - y;
            if (w <= 0 || h <= 0) return;
            JsonObject data = new JsonObject();
            data.addProperty("x", x);
            data.addProperty("y", y);
            data.addProperty("width", w);
            data.addProperty("height", h);
            data.addProperty("visible", true);
            data.addProperty(
                    "active", Boolean.TRUE.equals(call(view, "isEnabled", new Class<?>[0])));
            data.addProperty(
                    "focused", Boolean.TRUE.equals(call(view, "isFocused", new Class<?>[0])));
            boolean edit = type("icyllis.modernui.widget.EditText").isInstance(view);
            boolean slider = isType(view, "icyllis.modernui.widget.AbsSeekBar");
            boolean checked = isType(view, "icyllis.modernui.widget.CompoundButton");
            boolean clickable = Boolean.TRUE.equals(call(view, "isClickable", new Class<?>[0]));
            String text = "";
            if (type("icyllis.modernui.widget.TextView").isInstance(view)) {
                Object layout = call(view, "getLayout", new Class<?>[0]);
                if (layout != null)
                    text = Objects.toString(call(layout, "getText", new Class<?>[0]), "");
                if (text.length() > 2048) text = text.substring(0, 2048);
            }
            data.addProperty("label", edit ? "Text input" : text);
            data.addProperty("labelSource", edit ? "framework_role" : "framework_visible_layout");
            data.addProperty(
                    "role",
                    edit
                            ? "text_field"
                            : slider
                                    ? "slider"
                                    : checked ? "checkbox" : clickable ? "button" : "region");
            if (edit) {
                data.addProperty("value", text);
                data.addProperty("editable", true);
                data.addProperty("multiline", true);
                data.addProperty("textReplaceStrategy", "select_all");
            }
            if (checked) {
                data.addProperty(
                        "value", Boolean.TRUE.equals(call(view, "isChecked", new Class<?>[0])));
                data.addProperty("valueSemantics", "visible_checked_state");
            }
            String revision = targetRevision(view);
            if (slider) {
                int min = ((Number) call(view, "getMin", new Class<?>[0])).intValue(),
                        max = ((Number) call(view, "getMax", new Class<?>[0])).intValue();
                data.addProperty("valueMinimum", min);
                data.addProperty("valueMaximum", max);
                data.addProperty(
                        "value", ((Number) call(view, "getProgress", new Class<?>[0])).intValue());
            }
            JsonArray actions = new JsonArray();
            actions.add("hover");
            if (clickable || edit || slider || checked) actions.add("click");
            if (edit) actions.add("type_text");
            if (slider) actions.add("set_value");
            boolean scroll = false;
            boolean vertical = false, horizontal = false;
            for (String axis : List.of("canScrollVertically", "canScrollHorizontally"))
                for (int direction : new int[] {-1, 1}) {
                    boolean allowed =
                            Boolean.TRUE.equals(
                                    call(view, axis, new Class<?>[] {int.class}, direction));
                    if (axis.equals("canScrollVertically")) vertical |= allowed;
                    else horizontal |= allowed;
                }
            scroll = vertical || horizontal;
            if (scroll) {
                actions.add("scroll");
                data.addProperty("canPanY", vertical);
                data.addProperty("canPanX", horizontal);
                data.addProperty(
                        "scrollX", ((Number) call(view, "getScrollX", new Class<?>[0])).intValue());
                data.addProperty(
                        "scrollY", ((Number) call(view, "getScrollY", new Class<?>[0])).intValue());
            }
            if (clickable || scroll || edit || slider) actions.add("drag");
            data.add("actions", actions);
            long id = IDS.computeIfAbsent(view, unused -> ++nextId);
            nodes.add(
                    new UiAdapter.Node(
                            "view:" + id, 0, revision, UiAdapter.Space.FRAMEBUFFER, data));
            if (type("icyllis.modernui.view.ViewGroup").isInstance(view)) {
                int size = ((Number) call(view, "getChildCount", new Class<?>[0])).intValue();
                for (int index = 0; index < size && nodes.size() < 512; index++)
                    visit(
                            call(view, "getChildAt", new Class<?>[] {int.class}, index),
                            nodes,
                            depth + 1);
            }
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Modern UI visible projection unavailable", failure);
        }
    }

    private static boolean isType(Object value, String name) {
        for (Class<?> type = value.getClass(); type != null; type = type.getSuperclass())
            if (type.getName().equals(name)) return true;
        return false;
    }

    private static String targetRevision(Object view) {
        String revision = view.getClass().getName();
        if (isType(view, "icyllis.modernui.widget.AbsSeekBar"))
            revision +=
                    ":"
                            + call(view, "getMin", new Class<?>[0])
                            + ":"
                            + call(view, "getMax", new Class<?>[0]);
        return revision;
    }

    public static CompletableFuture<Void> reset(String reason) {
        SNAPSHOT.set(null);
        EVENTS.values().forEach(item -> item.delivery().cancel());
        CompletableFuture<Void> done = new CompletableFuture<>();
        try {
            Object manager = manager();
            if (manager == null) return CompletableFuture.completedFuture(null);
            ((StringBuilder) field(manager, "mCharInputBuffer")).setLength(0);
            set(manager, "mButtonState", 0);
            postUi(
                    () -> {
                        try {
                            releaseOnUi(manager);
                            done.complete(null);
                        } catch (Throwable failure) {
                            done.completeExceptionally(failure);
                        }
                    });
        } catch (Throwable failure) {
            done.completeExceptionally(failure);
        }
        return done;
    }

    public static io.github.recrivenvi.minecraftprotocol.probe.extension.ExtensionResources
                    .ThreadOwner
            cleanupOwner() {
        return new io.github.recrivenvi.minecraftprotocol.probe.extension.ExtensionResources
                .ThreadOwner("modern_ui_thread", ModernBridge::postUi, ModernBridge::isUiThread);
    }

    public static void releaseOnUi() {
        Object current = manager();
        if (current != null) releaseOnUi(current);
        presence = null;
        requested = null;
        SNAPSHOT.set(null);
        captureFailure = null;
        IDS.clear();
    }

    private static void releaseOnUi(Object manager) {
        if (!isUiThread()) throw new IllegalStateException("Wrong framework cleanup owner");
        SNAPSHOT.set(null);
        EVENTS.values().forEach(item -> item.delivery().cancel());
        Object root = field(manager, "mRoot");
        if (root != null) {
            var nativeQueue = (ConcurrentLinkedQueue<?>) field(root, "mInputEvents");
            for (Object event; (event = nativeQueue.poll()) != null; )
                call(event, "recycle", new Class<?>[0]);
        }
        Object decor = call(manager, "getDecorView", new Class<?>[0]);
        if (decor != null) {
            Object event =
                    staticCall(
                            "icyllis.modernui.view.MotionEvent",
                            "obtain",
                            new Class<?>[] {
                                long.class, int.class, float.class, float.class, int.class
                            },
                            System.nanoTime(),
                            3,
                            0f,
                            0f,
                            0);
            try {
                call(
                        decor,
                        "dispatchPointerEvent",
                        new Class<?>[] {type("icyllis.modernui.view.MotionEvent")},
                        event);
            } finally {
                call(event, "recycle", new Class<?>[0]);
            }
            call(decor, "cancelPendingInputEvents", new Class<?>[0]);
        }
        // Pipeline cleanup releases input, not observation. Publish the post-cancel UI state
        // before acknowledging the cleanup barrier, so the next semantic action can resolve it.
        refreshSnapshot();
    }

    private static boolean isUiThread() {
        return Boolean.TRUE.equals(
                staticCall("icyllis.modernui.core.Core", "isOnUiThread", new Class<?>[0]));
    }

    private static void postUi(Runnable task) {
        staticCall(
                "icyllis.modernui.core.Core",
                "postOnUiThread",
                new Class<?>[] {Runnable.class},
                task);
    }

    private static Object manager() {
        return staticCall("icyllis.modernui.mc.UIManager", "getInstance", new Class<?>[0]);
    }

    private static Class<?> type(String name) {
        try {
            return Class.forName(name, false, ModernBridge.class.getClassLoader());
        } catch (ClassNotFoundException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Object staticCall(
            String owner, String method, Class<?>[] args, Object... values) {
        try {
            return type(owner).getMethod(method, args).invoke(null, values);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(owner + "." + method, failure);
        }
    }

    private static Object call(Object object, String method, Class<?>[] args, Object... values) {
        try {
            return object.getClass().getMethod(method, args).invoke(object, values);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException(object.getClass().getName() + "." + method, failure);
        }
    }

    private static Field find(Object object, String name) {
        for (Class<?> owner = object.getClass(); owner != null; owner = owner.getSuperclass())
            try {
                Field field = owner.getDeclaredField(name);
                if (!field.trySetAccessible())
                    throw new IllegalStateException("Framework field inaccessible");
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        throw new IllegalStateException("Framework field missing: " + name);
    }

    private static Object field(Object object, String name) {
        try {
            return find(object, name).get(object);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static void set(Object object, String name, Object value) {
        try {
            find(object, name).set(object, value);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
