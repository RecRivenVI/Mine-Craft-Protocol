package io.github.recrivenvi.minecraftprotocol.core.extension;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.probe.extension.WorldAdapter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class WorldExtensionEngineTest {
    private static final WorldAdapter.Frame FRAME =
            new WorldAdapter.Frame("runtime", 1, "overworld", "block", "0,80,2", 2, "world:parent");

    private static WorldAdapter.Part part(String revision, double offset, double tolerance) {
        return new WorldAdapter.Part(
                "body",
                revision,
                "Body",
                new WorldAdapter.Bounds(offset, 80, 2, offset + 1, 81, 3),
                new WorldAdapter.Point(offset + .5, 80.5, 2.5),
                tolerance,
                Map.of("material", "stone"));
    }

    private static WorldAdapter adapter(
            String id,
            java.util.function.Function<WorldAdapter.Context, WorldAdapter.Projection> capture) {
        return new WorldAdapter() {
            public Descriptor descriptor() {
                return new Descriptor(id, 64, 1000);
            }

            public boolean matches(Frame frame) {
                return true;
            }

            public Projection capture(Context context) {
                return capture.apply(context);
            }
        };
    }

    private static WorldAdapter ready(String id, WorldAdapter.Part... parts) {
        return adapter(
                id,
                context ->
                        new WorldAdapter.Projection(
                                context.frame(), System.nanoTime(), List.of(parts)));
    }

    private static WorldExtensionEngine.Discovery read(WorldExtensionEngine engine) {
        return engine.discover(FRAME, new Object(), new JsonObject(), object -> 3, () -> true);
    }

    @Test
    void scopedOwnerContextCannotEscapeAndSnapshotsStayDetached() {
        var retained = new AtomicReference<WorldAdapter.Context>();
        var engine =
                new WorldExtensionEngine(
                        "runtime",
                        () ->
                                List.of(
                                        adapter(
                                                "sample:parts",
                                                context -> {
                                                    retained.set(context);
                                                    assertNotNull(context.nativeParent());
                                                    context.nativeSnapshot()
                                                            .addProperty("injected", true);
                                                    assertFalse(
                                                            context.nativeSnapshot()
                                                                    .has("injected"));
                                                    return new WorldAdapter.Projection(
                                                            context.frame(),
                                                            System.nanoTime(),
                                                            List.of(part("1", 0, 0)));
                                                })));
        var value = read(engine);
        assertEquals(1, value.parts().size());
        assertThrows(IllegalStateException.class, () -> retained.get().nativeParent());
        assertThrows(IllegalStateException.class, () -> retained.get().identify(new Object()));
        assertThrows(
                IllegalStateException.class,
                () -> engine.discover(FRAME, new Object(), new JsonObject(), o -> 3, () -> false));
        var dto = WorldExtensionEngine.describe(value.parts().get(0));
        assertEquals("USER", dto.get("category").getAsString());
        assertEquals("CLIENT_KNOWN", dto.get("perspective").getAsString());
        assertFalse(
                dto.has("targetRef"), "Only the native parent registry can issue an action target");
    }

    @Test
    void adapterFailureIsTransactionalAndDoesNotSuppressOtherPackages() {
        var engine =
                new WorldExtensionEngine(
                        "runtime",
                        () ->
                                List.of(
                                        ready("bad:duplicate", part("1", 0, 0), part("1", 1, 0)),
                                        adapter(
                                                "bad:frame",
                                                c ->
                                                        new WorldAdapter.Projection(
                                                                new WorldAdapter.Frame(
                                                                        "other",
                                                                        1,
                                                                        "overworld",
                                                                        "block",
                                                                        "x",
                                                                        2,
                                                                        "ref"),
                                                                System.nanoTime(),
                                                                List.of(part("1", 0, 0)))),
                                        ready("good:parts", part("1", 0, 0))));
        var result = read(engine);
        assertEquals(1, result.parts().size());
        assertEquals("good:parts", result.parts().get(0).capabilityId());
        assertEquals(2, result.limitations().size());
    }

    @Test
    void partRevalidationDoesNotCreepOrFallbackToChangedGeometry() {
        var current = new AtomicReference<>(part("1", 0, .02));
        var engine =
                new WorldExtensionEngine(
                        "runtime",
                        () ->
                                List.of(
                                        adapter(
                                                "sample:parts",
                                                c ->
                                                        new WorldAdapter.Projection(
                                                                c.frame(),
                                                                System.nanoTime(),
                                                                List.of(current.get())))));
        var binding = read(engine).parts().get(0);
        current.set(part("1", .01, .02));
        assertEquals(
                .51,
                engine.resolve(binding, new Object(), new JsonObject(), o -> 3, () -> true)
                        .aimPoint()
                        .x(),
                .000001);
        current.set(part("1", .03, .02));
        assertEquals(
                "WORLD_PART_STALE",
                assertThrows(
                                ExtensionFailure.class,
                                () ->
                                        engine.resolve(
                                                binding,
                                                new Object(),
                                                new JsonObject(),
                                                o -> 3,
                                                () -> true))
                        .code());
        current.set(part("2", 0, .02));
        assertThrows(
                ExtensionFailure.class,
                () -> engine.resolve(binding, new Object(), new JsonObject(), o -> 3, () -> true));
        current.set(part("1", 0, 0));
        assertThrows(
                ExtensionFailure.class,
                () -> engine.resolve(binding, new Object(), new JsonObject(), o -> 3, () -> true));
    }

    @Test
    void perReadBudgetSpansParentsAndNestedScopesRestoreTheirOwnBudget() {
        var many =
                java.util.stream.IntStream.range(0, 64)
                        .mapToObj(
                                i ->
                                        new WorldAdapter.Part(
                                                "part" + i,
                                                "1",
                                                "part",
                                                new WorldAdapter.Bounds(0, 0, 0, 1, 1, 1),
                                                new WorldAdapter.Point(.5, .5, .5),
                                                0,
                                                Map.of()))
                        .toList();
        var engine =
                new WorldExtensionEngine(
                        "runtime",
                        () ->
                                List.of(
                                        adapter(
                                                "sample:parts",
                                                c ->
                                                        new WorldAdapter.Projection(
                                                                c.frame(),
                                                                System.nanoTime(),
                                                                many))));
        try (var outer = engine.readScope()) {
            for (int i = 0; i < 4; i++) assertEquals(64, read(engine).parts().size());
            assertTrue(read(engine).parts().isEmpty());
            try (var inner = engine.readScope()) {
                assertEquals(64, read(engine).parts().size());
            }
            assertTrue(read(engine).parts().isEmpty());
        }
        assertEquals(64, read(engine).parts().size());
    }

    @Test
    void invalidGeometryNeverBecomesAnInputTarget() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorldAdapter.Bounds(0, 0, 0, Double.NaN, 1, 1));
        assertThrows(
                IllegalArgumentException.class, () -> new WorldAdapter.Bounds(0, 0, 0, 65, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WorldAdapter.Part(
                                "body",
                                "1",
                                "body",
                                new WorldAdapter.Bounds(0, 0, 0, 1, 1, 1),
                                new WorldAdapter.Point(2, .5, .5),
                                0,
                                Map.of()));
    }
}
