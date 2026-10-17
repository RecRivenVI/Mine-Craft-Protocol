package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import io.github.recrivenvi.minecraftprotocol.probe.mixin.WorldChunkRevisionMixin;
import io.github.recrivenvi.minecraftprotocol.safety.WorldBlockChanges;
import java.lang.reflect.Method;
import java.util.Arrays;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

final class WorldChunkLifecycleTest {
    private static Method revision() throws Exception {
        Method method = WorldTargeting.class.getDeclaredMethod("revision", Object.class);
        method.setAccessible(true);
        return method;
    }

    private static WorldBlockChanges history() throws Exception {
        var field = WorldTargeting.class.getDeclaredField("BLOCK_CHANGES");
        field.setAccessible(true);
        return (WorldBlockChanges) field.get(null);
    }

    @Test
    void productionBulkAdapterInvalidatesExistingPositionsAndBreakAccounting() throws Exception {
        // Synthetic identities exercise the production adapter, not a game or packet replay.
        Object chunk = new Object(), other = new Object();
        Method revision = revision();
        long beforeRevision = (long) revision.invoke(null, chunk);
        revision.invoke(null, other);
        WorldBlockChanges history = history();
        long before = history.snapshot();
        WorldTargeting.chunkReplaced(chunk);
        assertFalse(history.unchanged(chunk, 0, before));
        assertFalse(history.unchanged(chunk, -1, before));
        assertTrue(history.unchanged(other, 0, before));
        assertEquals(beforeRevision + 1, revision.invoke(null, chunk));
        assertTrue(history.unchanged(chunk, 0, history.snapshot()));
    }

    @Test
    void unobservedChunksDoNotConsumeTheClientHistory() throws Exception {
        WorldBlockChanges history = history();
        long before = history.snapshot();
        WorldTargeting.chunkReplaced(new Object());
        assertEquals(before, history.snapshot());
    }

    @Test
    void actualProductionHookInvalidatesBeforeTheNativeVoidSnapshotMethod() throws Exception {
        var nativeMethods =
                Arrays.stream(LevelChunk.class.getDeclaredMethods())
                        .filter(method -> method.getName().equals("replaceWithPacketData"))
                        .toList();
        assertEquals(1, nativeMethods.size(), "Snapshot overloads require explicit native review");
        assertEquals(void.class, nativeMethods.getFirst().getReturnType());
        Method hook =
                WorldChunkRevisionMixin.class.getDeclaredMethod(
                        "minecraftProtocolProbe$replaceTargets", CallbackInfo.class);
        Inject injection = hook.getAnnotation(Inject.class);
        assertNotNull(injection);
        assertArrayEquals(new String[] {"replaceWithPacketData"}, injection.method());
        assertEquals("HEAD", injection.at()[0].value());
        assertFalse(injection.cancellable());
        // Invoke the real production callback with a synthetic identity; no Minecraft replay is
        // claimed.
        Object chunk = new WorldChunkRevisionMixin() {};
        revision().invoke(null, chunk);
        WorldBlockChanges history = history();
        long before = history.snapshot();
        hook.setAccessible(true);
        hook.invoke(chunk, new CallbackInfo("replaceWithPacketData", false));
        assertFalse(history.unchanged(chunk, 0, before));
        assertFalse(history.unchanged(chunk, Long.MIN_VALUE, before));
    }
}
