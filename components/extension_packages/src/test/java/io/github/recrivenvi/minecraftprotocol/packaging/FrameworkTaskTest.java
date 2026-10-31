package io.github.recrivenvi.minecraftprotocol.packaging;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.extensions.modernui.nativebridge.FrameworkTasks;
import java.net.URL;
import java.net.URLClassLoader;
import org.junit.jupiter.api.Test;

class FrameworkTaskTest {
    @Test
    void nativeCompilerLoaderIsScopedAndRestoredOnSuccessAndFailure() throws Exception {
        Thread owner = Thread.currentThread();
        ClassLoader original = owner.getContextClassLoader();
        try (var framework = new URLClassLoader(new URL[0], original)) {
            assertEquals(
                    "compiled",
                    FrameworkTasks.contextual(
                                    () -> {
                                        assertSame(
                                                framework,
                                                Thread.currentThread().getContextClassLoader());
                                        return "compiled";
                                    },
                                    framework)
                            .get());
            assertSame(original, owner.getContextClassLoader());
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            FrameworkTasks.contextual(
                                            () -> {
                                                assertSame(
                                                        framework,
                                                        Thread.currentThread()
                                                                .getContextClassLoader());
                                                throw new IllegalStateException("compiler failure");
                                            },
                                            framework)
                                    .get());
            assertSame(original, owner.getContextClassLoader());
        }
    }
}
