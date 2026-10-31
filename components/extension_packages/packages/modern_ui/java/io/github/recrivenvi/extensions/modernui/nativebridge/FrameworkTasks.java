package io.github.recrivenvi.extensions.modernui.nativebridge;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/** Preserve the native framework loader only for its own asynchronous compiler callback. */
public final class FrameworkTasks {
    private FrameworkTasks() {}

    public static <T> CompletableFuture<T> compile(Supplier<T> work) {
        return CompletableFuture.supplyAsync(contextual(work, work.getClass().getClassLoader()));
    }

    public static <T> Supplier<T> contextual(Supplier<T> work, ClassLoader loader) {
        return () -> {
            Thread thread = Thread.currentThread();
            ClassLoader previous = thread.getContextClassLoader();
            thread.setContextClassLoader(loader);
            try {
                return work.get();
            } finally {
                thread.setContextClassLoader(previous);
            }
        };
    }
}
