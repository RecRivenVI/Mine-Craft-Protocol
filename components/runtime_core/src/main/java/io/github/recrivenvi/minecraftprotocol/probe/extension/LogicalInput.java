package io.github.recrivenvi.minecraftprotocol.probe.extension;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Read-only native polling adaptation. This API cannot acquire a Lease or dispatch player input.
 */
public final class LogicalInput {
    public enum Kind {
        KEY,
        CHARACTER,
        PREEDIT,
        MOVE,
        BUTTON,
        SCROLL,
        DROP
    }

    public record Target(
            String capability,
            String key,
            String revision,
            double x,
            double y,
            double width,
            double height,
            int guiWidth,
            int guiHeight,
            int framebufferWidth,
            int framebufferHeight,
            boolean textFocusRequired) {
        public Target {
            java.util.Objects.requireNonNull(capability);
            java.util.Objects.requireNonNull(key);
            java.util.Objects.requireNonNull(revision);
            if (!Double.isFinite(x)
                    || !Double.isFinite(y)
                    || !Double.isFinite(width)
                    || !Double.isFinite(height)
                    || width <= 0
                    || height <= 0
                    || guiWidth <= 0
                    || guiHeight <= 0
                    || framebufferWidth <= 0
                    || framebufferHeight <= 0)
                throw new IllegalArgumentException("Finite positive target geometry required");
        }
    }

    public static final class TargetInvalidated extends RuntimeException {
        public TargetInvalidated(String reason) {
            super(reason);
        }
    }

    public record Snapshot(
            String runtimeContext,
            long generation,
            long screenIdentity,
            long window,
            Kind kind,
            Set<Integer> keys,
            Set<Integer> buttons,
            double windowX,
            double windowY,
            boolean cleanupOnly,
            int screenObjectHash,
            Target target) {
        public Snapshot(
                String runtimeContext,
                long generation,
                long screenIdentity,
                long window,
                Kind kind,
                Set<Integer> keys,
                Set<Integer> buttons,
                double windowX,
                double windowY,
                boolean cleanupOnly) {
            this(
                    runtimeContext,
                    generation,
                    screenIdentity,
                    window,
                    kind,
                    keys,
                    buttons,
                    windowX,
                    windowY,
                    cleanupOnly,
                    0,
                    null);
        }

        public Snapshot {
            java.util.Objects.requireNonNull(runtimeContext);
            java.util.Objects.requireNonNull(kind);
            keys = Set.copyOf(keys);
            buttons = Set.copyOf(buttons);
            if (!Double.isFinite(windowX)
                    || !Double.isFinite(windowY)
                    || cleanupOnly && (!keys.isEmpty() || !buttons.isEmpty()))
                throw new IllegalArgumentException(
                        "Finite input state, with empty release-only cleanup required");
        }

        public boolean keyDown(int key) {
            return !cleanupOnly && keys.contains(key);
        }

        public boolean buttonDown(int button) {
            return !cleanupOnly && buttons.contains(button);
        }
    }

    private static final ThreadLocal<Binding> CURRENT = new ThreadLocal<>();

    private LogicalInput() {}

    public static Snapshot current() {
        Binding binding = CURRENT.get();
        return binding != null && binding.owner == Thread.currentThread() && binding.live()
                ? binding.snapshot
                : null;
    }

    /** Capture before leaving the admitted callback; consumption acknowledges native completion. */
    public static Delivery defer() {
        Binding binding = CURRENT.get();
        if (current() == null) throw new IllegalStateException("No active logical input callback");
        Delivery delivery = new Delivery(binding);
        binding.pending.accept(delivery.completion);
        return delivery;
    }

    public static final class Delivery {
        private final Binding origin;
        private final AtomicBoolean consumed = new AtomicBoolean();
        private final CompletableFuture<Void> completion = new CompletableFuture<>();

        private Delivery(Binding origin) {
            this.origin = origin;
            CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS)
                    .execute(
                            () ->
                                    completion.completeExceptionally(
                                            new java.util.concurrent.TimeoutException(
                                                    "Framework input delivery was not acknowledged")));
        }

        public CompletableFuture<Void> completion() {
            return completion.copy();
        }

        public void cancel() {
            completion.cancel(false);
        }

        public Snapshot snapshot() {
            return origin.snapshot;
        }

        public void targetInvalidated(String reason) {
            completion.completeExceptionally(new TargetInvalidated(reason));
        }

        public boolean deliver(BooleanSupplier frameworkOwner, Runnable action) {
            try (var consumption = begin(frameworkOwner)) {
                if (consumption == null) return false;
                try {
                    action.run();
                } catch (Throwable failure) {
                    consumption.failed(failure);
                    throw failure;
                }
                return true;
            }
        }

        public Consumption begin(BooleanSupplier frameworkOwner) {
            if (!consumed.compareAndSet(false, true))
                throw new IllegalStateException("Logical delivery is one-use");
            try {
                if (completion.isDone() || !origin.valid.getAsBoolean()) {
                    completion.cancel(false);
                    return null;
                }
                if (!frameworkOwner.getAsBoolean())
                    throw new IllegalStateException("Wrong framework input owner");
                return new Consumption(
                        new Binding(
                                origin.snapshot,
                                origin.valid,
                                origin.deferredSource,
                                origin.deferredSource,
                                future -> {
                                    throw new IllegalStateException(
                                            "A framework delivery cannot defer itself again");
                                }));
            } catch (Throwable failure) {
                completion.completeExceptionally(failure);
                throw failure;
            }
        }

        public final class Consumption implements AutoCloseable {
            private final Binding binding;
            private boolean closed;
            private Throwable failure;

            private Consumption(Binding binding) {
                this.binding = binding;
            }

            public void failed(Throwable value) {
                failure = value;
            }

            @Override
            public void close() {
                if (closed) return;
                binding.close();
                closed = true;
                if (failure == null) completion.complete(null);
                else completion.completeExceptionally(failure);
            }
        }
    }

    /**
     * Runtime bridge only; a binding supplies observations, never authority or native admission.
     */
    public static final class Binding implements AutoCloseable {
        private final Snapshot snapshot;
        private final BooleanSupplier valid, source, deferredSource;
        private final Consumer<CompletableFuture<Void>> pending;
        private final Thread owner = Thread.currentThread();
        private final Binding previous;
        private boolean closed;

        public Binding(
                Snapshot snapshot,
                BooleanSupplier valid,
                BooleanSupplier source,
                BooleanSupplier deferredSource,
                Consumer<CompletableFuture<Void>> pending) {
            this.snapshot = java.util.Objects.requireNonNull(snapshot);
            this.valid = java.util.Objects.requireNonNull(valid);
            this.source = java.util.Objects.requireNonNull(source);
            this.deferredSource = java.util.Objects.requireNonNull(deferredSource);
            this.pending = java.util.Objects.requireNonNull(pending);
            previous = CURRENT.get();
            CURRENT.set(this);
        }

        private boolean live() {
            return !closed && source.getAsBoolean() && valid.getAsBoolean();
        }

        @Override
        public void close() {
            if (owner != Thread.currentThread() || CURRENT.get() != this)
                throw new IllegalStateException("Logical input scopes must close in owner order");
            closed = true;
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
