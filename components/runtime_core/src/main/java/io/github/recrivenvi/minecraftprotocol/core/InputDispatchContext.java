package io.github.recrivenvi.minecraftprotocol.core;

import com.google.gson.JsonObject;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** An internal, expiring effect permit captured before crossing Minecraft's client executor. */
public final class InputDispatchContext {
    public interface Permit {
        String gestureId();

        void check();

        /** Called on the native owner thread immediately before an input-producing callback. */
        void beforeInput(BiConsumer<Long, Long> validate);

        default String expectedPlayerCommand() {
            return null;
        }

        default boolean playerCommandSubmissionAllowed() {
            return true;
        }
    }

    record Preconditions(
            Long screenRevision, Long menuRevision, Supplier<CompletableFuture<JsonObject>> verify)
            implements Supplier<CompletableFuture<JsonObject>> {
        @Override
        public CompletableFuture<JsonObject> get() {
            return verify.get();
        }
    }

    static final class FirstInput {
        private final Preconditions expected;
        private boolean validated;
        private boolean rejected;

        FirstInput(Preconditions expected) {
            this.expected = expected;
        }

        synchronized void validate(BiConsumer<Long, Long> validate) {
            if (validated || expected == null) return;
            try {
                validate.accept(expected.screenRevision(), expected.menuRevision());
            } catch (RuntimeException failure) {
                rejected = true;
                throw failure;
            }
            // A pipeline may legitimately change its own Screen/menu after its first event.
            validated = true;
        }

        synchronized boolean rejected() {
            return rejected;
        }
    }

    private static final ThreadLocal<Permit> CURRENT = new ThreadLocal<>();

    private InputDispatchContext() {}

    public static Permit capture() {
        return CURRENT.get();
    }

    static CompletableFuture<JsonObject> run(
            String gestureId, Runnable check, Supplier<CompletableFuture<JsonObject>> factory) {
        return run(gestureId, check, null, factory);
    }

    static CompletableFuture<JsonObject> run(
            String gestureId,
            Runnable check,
            FirstInput firstInput,
            Supplier<CompletableFuture<JsonObject>> factory) {
        return run(gestureId, check, firstInput, null, true, factory);
    }

    static CompletableFuture<JsonObject> run(
            String gestureId,
            Runnable check,
            FirstInput firstInput,
            String expectedCommand,
            boolean commandSubmissionAllowed,
            Supplier<CompletableFuture<JsonObject>> factory) {
        AtomicBoolean live = new AtomicBoolean(true);
        Permit permit =
                new Permit() {
                    @Override
                    public String gestureId() {
                        return gestureId;
                    }

                    @Override
                    public String expectedPlayerCommand() {
                        return expectedCommand;
                    }

                    @Override
                    public boolean playerCommandSubmissionAllowed() {
                        return commandSubmissionAllowed;
                    }

                    @Override
                    public void check() {
                        if (!live.get())
                            throw new ProtocolState.ProtocolException(
                                    "INPUT_DISPATCH_EXPIRED",
                                    409,
                                    "The originating input effect is no longer active");
                        check.run();
                    }

                    @Override
                    public void beforeInput(BiConsumer<Long, Long> validate) {
                        check();
                        if (firstInput != null) firstInput.validate(validate);
                        check();
                    }
                };
        Permit previous = CURRENT.get();
        CURRENT.set(permit);
        try {
            permit.check();
            CompletableFuture<JsonObject> result = factory.get();
            result.whenComplete((value, failure) -> live.set(false));
            return result;
        } catch (Throwable failure) {
            live.set(false);
            throw failure;
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }
}
