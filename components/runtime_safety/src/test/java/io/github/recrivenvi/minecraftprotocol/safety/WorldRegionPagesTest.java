package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class WorldRegionPagesTest {
    private interface Cells {
        WorldRegionPages.Cell read(int x, int y, int z);
    }

    private static final class View implements WorldRegionPages.View {
        final AtomicInteger reads = new AtomicInteger(), contexts = new AtomicInteger();
        WorldRegionPages.Context current =
                WorldRegionPagesTest.context("runtime/connection/world/player:1", 0.5);
        Cells cells = (x, y, z) -> WorldRegionPages.Cell.known("minecraft:air", Map.of());

        public WorldRegionPages.Context context() {
            contexts.incrementAndGet();
            return current;
        }

        public WorldRegionPages.Cell cell(int x, int y, int z) {
            reads.incrementAndGet();
            return cells.read(x, y, z);
        }
    }

    @Test
    void realPagesReconstructNegativeAndCrossSectionCoordinatesWithoutGapsOrDuplication() {
        View view = new View();
        view.cells =
                (x, y, z) ->
                        x == -1
                                ? WorldRegionPages.Cell.unloaded()
                                : WorldRegionPages.Cell.known(
                                        x % 2 == 0 ? "minecraft:stone" : "minecraft:air", Map.of());
        JsonObject request = box(-2, -1, -2, 2, 1, 2);
        request.addProperty("maxBlocks", 7);
        Set<String> seen = new HashSet<>();
        int pages = 0, unloaded = 0, previousCovered = 0;
        try (var reader = reader(new AtomicLong())) {
            JsonObject page;
            do {
                page = reader.read(request, "owner", view, () -> {});
                assertEquals("USER", page.get("dataCategory").getAsString());
                assertEquals("CLIENT_KNOWN", page.get("authority").getAsString());
                assertFalse(page.get("crossPageAtomic").getAsBoolean());
                assertFalse(page.get("storageAccessed").getAsBoolean());
                assertFalse(page.get("chunkLoadRequested").getAsBoolean());
                assertEquals(previousCovered, page.get("pageStartOffset").getAsInt());
                JsonObject section = page.getAsJsonObject("section");
                JsonObject min = section.getAsJsonObject("min"),
                        size = section.getAsJsonObject("size");
                int sx = size.get("x").getAsInt(), sz = size.get("z").getAsInt();
                JsonArray indices = page.getAsJsonArray("indices"),
                        palette = page.getAsJsonArray("palette");
                assertTrue(indices.size() > 0 && indices.size() <= 7);
                for (int i = 0; i < indices.size(); i++) {
                    int local = section.get("offset").getAsInt() + i;
                    int x = min.get("x").getAsInt() + local % sx;
                    int z = min.get("z").getAsInt() + local / sx % sz;
                    int y = min.get("y").getAsInt() + local / (sx * sz);
                    assertEquals(Math.floorDiv(x, 16), section.get("x").getAsInt());
                    assertEquals(Math.floorDiv(y, 16), section.get("y").getAsInt());
                    assertEquals(Math.floorDiv(z, 16), section.get("z").getAsInt());
                    assertTrue(seen.add(x + ":" + y + ":" + z));
                    int index = indices.get(i).getAsInt();
                    if (x == -1) {
                        assertEquals(-1, index);
                        unloaded++;
                    } else {
                        JsonObject state = palette.get(index).getAsJsonObject();
                        assertEquals(
                                x % 2 == 0 ? "minecraft:stone" : "minecraft:air",
                                state.get("block").getAsString());
                        assertEquals(Set.of("block", "properties"), state.keySet());
                    }
                }
                previousCovered += indices.size();
                assertEquals(previousCovered, page.get("coveredCellCount").getAsInt());
                assertEquals(unloaded, page.get("unloadedCellCount").getAsInt());
                assertEquals(75 - previousCovered, page.get("remainingCellCount").getAsInt());
                request = continuation(page);
                assertTrue(++pages <= 75, "Every successful page must progress");
            } while (!page.get("complete").getAsBoolean());
            assertEquals(75, seen.size());
            assertEquals(75, view.reads.get());
            assertFalse(page.has("nextCursor"));
            assertFalse(page.get("allLoaded").getAsBoolean());
        }
    }

    @Test
    void unknownAndKnownAirRemainDifferentEvenForACompleteRead() {
        View view = new View();
        view.cells =
                (x, y, z) ->
                        x == 0
                                ? WorldRegionPages.Cell.known("minecraft:air", Map.of())
                                : WorldRegionPages.Cell.unloaded();
        try (var reader = reader(new AtomicLong())) {
            JsonObject result = reader.read(box(0, 0, 0, 1, 0, 0), "owner", view, () -> {});
            assertEquals("[0,-1]", result.getAsJsonArray("indices").toString());
            assertEquals(1, result.getAsJsonArray("palette").size());
            assertEquals(1, result.get("pageUnloadedCellCount").getAsInt());
            assertTrue(result.get("complete").getAsBoolean());
            assertFalse(result.get("allLoaded").getAsBoolean());
            assertEquals(
                    "per_page_live_client_cache_non_atomic",
                    result.get("stateSemantics").getAsString());
        }
    }

    @Test
    void exactExistingUserRadiusIsUsedAtBlockCentersRatherThanABoxOrExpandedSection() {
        View view = new View();
        try (var reader = reader(new AtomicLong())) {
            int radius = WorldClientModel.MAX_RADIUS;
            assertTrue(
                    reader.read(box(radius, 0, 0, radius, 0, 0), "owner", view, () -> {})
                            .get("complete")
                            .getAsBoolean());
            int before = view.reads.get();
            failure(
                    "WORLD_REGION_OUTSIDE_USER_RADIUS",
                    () ->
                            reader.read(
                                    box(radius + 1, 0, 0, radius + 1, 0, 0),
                                    "owner",
                                    view,
                                    () -> {}));
            failure(
                    "WORLD_REGION_OUTSIDE_USER_RADIUS",
                    () -> reader.read(box(24, 24, 0, 24, 24, 0), "owner", view, () -> {}));
            assertEquals(before, view.reads.get());
        }
    }

    @Test
    void queryShapeAndBudgetsFailBeforeAnyClientCellIsRead() {
        View view = new View();
        try (var reader = reader(new AtomicLong())) {
            for (String query :
                    new String[] {
                        "{}",
                        "{\"min\":null}",
                        "{\"min\":{\"x\":0.5,\"y\":0,\"z\":0},\"max\":{\"x\":1,\"y\":0,\"z\":0}}"
                    })
                failure(
                        "INVALID_WORLD_REGION_QUERY",
                        () -> reader.read(json(query), "owner", view, () -> {}));
            for (String key : new String[] {"maxBlocks", "maxBytes"}) {
                for (String bad :
                        new String[] {"0", "-1", "1.5", "\"1\"", "null", "true", "2147483648"}) {
                    JsonObject query = box(0, 0, 0, 1, 0, 0);
                    query.add(key, JsonParser.parseString(bad));
                    failure(
                            "INVALID_WORLD_REGION_QUERY",
                            () -> reader.read(query, "owner", view, () -> {}));
                }
            }
            for (JsonObject query :
                    new JsonObject[] {
                        box(1, 0, 0, 0, 0, 0),
                        box(0, 0, 0, 66, 0, 0),
                        box(-32, -32, -32, 32, 32, 32)
                    })
                failure(
                        "INVALID_WORLD_REGION_QUERY",
                        () -> reader.read(query, "owner", view, () -> {}));
            JsonObject extra = box(0, 0, 0, 1, 0, 0);
            extra.addProperty("category", "DEBUG");
            failure(
                    "INVALID_WORLD_REGION_QUERY",
                    () -> reader.read(extra, "owner", view, () -> {}));
            assertEquals(0, view.reads.get());
        }
    }

    @Test
    void wrongOwnerAndChangedQueryCannotAdvanceOrConsumeTheRightfulCursor() {
        View view = new View();
        try (var reader = reader(new AtomicLong())) {
            JsonObject query = box(0, 0, 0, 2, 0, 0);
            query.addProperty("maxBlocks", 1);
            JsonObject page = reader.read(query, "owner", view, () -> {}),
                    next = continuation(page);
            int before = view.reads.get();
            failure(
                    "WORLD_REGION_CURSOR_INVALID",
                    () -> reader.read(next, "other", view, () -> {}));
            assertFalse(reader.cancel(page.get("nextCursor").getAsString(), "other"));
            for (String field :
                    new String[] {"min", "max", "maxBlocks", "maxBytes", "contextId", "owner"}) {
                JsonObject changed = next.deepCopy();
                changed.addProperty(field, 1);
                failure(
                        "INVALID_WORLD_REGION_QUERY",
                        () -> reader.read(changed, "owner", view, () -> {}));
            }
            assertEquals(before, view.reads.get());
            JsonObject advanced = reader.read(next, "owner", view, () -> {});
            assertEquals(2, advanced.get("coveredCellCount").getAsInt());
            failure(
                    "WORLD_REGION_CURSOR_INVALID",
                    () -> reader.read(next, "owner", view, () -> {}));
        }
    }

    @Test
    void nativeContextChangeOrLeavingTheRadiusRefusesBeforeAdditionalCells() {
        View view = new View();
        try (var reader = reader(new AtomicLong())) {
            JsonObject query = box(0, 0, 0, 2, 0, 0);
            query.addProperty("maxBlocks", 1);
            JsonObject next = continuation(reader.read(query, "owner", view, () -> {}));
            int before = view.reads.get();
            view.current = context("replacement world/player/connection", 0.5);
            failure(
                    "WORLD_REGION_CONTEXT_CHANGED",
                    () -> reader.read(next, "owner", view, () -> {}));
            view.current = null;
            failure(
                    "WORLD_REGION_CONTEXT_CHANGED",
                    () -> reader.read(next, "owner", view, () -> {}));
            view.current = context("runtime/connection/world/player:1", 100.5);
            failure(
                    "WORLD_REGION_OUTSIDE_USER_RADIUS",
                    () -> reader.read(next, "owner", view, () -> {}));
            assertEquals(before, view.reads.get());
        }
    }

    @Test
    void idleAndHardExpiryAreIndependentAndNeverRenewedByFailedReads() {
        View view = new View();
        AtomicLong clock = new AtomicLong();
        JsonObject query = box(0, 0, 0, 3, 3, 3);
        query.addProperty("maxBlocks", 1);
        try (var reader = reader(clock)) {
            JsonObject next = continuation(reader.read(query, "owner", view, () -> {}));
            clock.set(30_000_000_000L);
            failure(
                    "WORLD_REGION_CURSOR_EXPIRED",
                    () -> reader.read(next, "owner", view, () -> {}));
            assertEquals(1, view.reads.get());
        }
        clock.set(0);
        try (var reader = reader(clock)) {
            JsonObject next = continuation(reader.read(query, "owner", view, () -> {}));
            for (int i = 1; i <= 4; i++) {
                clock.set(i * 29_000_000_000L);
                next = continuation(reader.read(next, "owner", view, () -> {}));
            }
            JsonObject expired = next;
            clock.set(120_000_000_000L);
            int before = view.reads.get();
            failure(
                    "WORLD_REGION_CURSOR_EXPIRED",
                    () -> reader.read(expired, "owner", view, () -> {}));
            assertEquals(before, view.reads.get());
        }
    }

    @Test
    void bytesBudgetTruncatesWithProgressAndAllReturnedPagesStayWithinTheActualLimit() {
        View view = new View();
        view.cells =
                (x, y, z) -> {
                    Map<String, String> properties = new HashMap<>();
                    for (int i = 0; i < 32; i++) properties.put("p" + i, "v".repeat(128));
                    return WorldRegionPages.Cell.known(
                            "example:block_" + x + "_" + y + "_" + z, properties);
                };
        JsonObject query = box(0, 0, 0, 7, 3, 3);
        query.addProperty("maxBytes", WorldRegionPages.MIN_PAGE_BYTES);
        try (var reader = reader(new AtomicLong())) {
            JsonObject page = reader.read(query, "owner", view, () -> {});
            assertEquals("byte_budget", page.get("truncationReason").getAsString());
            int covered = 0, pages = 0;
            do {
                assertTrue(page.get("pageCellCount").getAsInt() > 0);
                assertTrue(
                        page.toString().getBytes(StandardCharsets.UTF_8).length
                                <= WorldRegionPages.MIN_PAGE_BYTES);
                covered += page.get("pageCellCount").getAsInt();
                assertEquals(covered, page.get("coveredCellCount").getAsInt());
                assertTrue(++pages <= 128);
                if (page.get("complete").getAsBoolean()) break;
                page = reader.read(continuation(page), "owner", view, () -> {});
            } while (true);
            assertEquals(128, covered);
        }
    }

    @Test
    void ownerTimeBudgetReturnsOnlyProgressingPagesAndZeroProgressFailsExplicitly() {
        AtomicLong clock = new AtomicLong();
        View view = new View();
        view.cells =
                (x, y, z) -> {
                    clock.addAndGet(3_000_000L);
                    return WorldRegionPages.Cell.known("minecraft:air", Map.of());
                };
        try (var reader = reader(clock)) {
            JsonObject page = reader.read(box(0, 0, 0, 3, 0, 0), "owner", view, () -> {});
            assertEquals(3, page.get("pageCellCount").getAsInt());
            assertEquals("owner_time_budget", page.get("truncationReason").getAsString());
            assertTrue(
                    reader.read(continuation(page), "owner", view, () -> {})
                            .get("complete")
                            .getAsBoolean());
        }
        AtomicLong elapsed = new AtomicLong();
        View untouched = new View();
        try (var reader =
                new WorldRegionPages(
                        () -> elapsed.getAndAdd(WorldRegionPages.MAX_OWNER_NANOS), () -> 1L)) {
            failure(
                    "WORLD_REGION_BUDGET_NO_PROGRESS",
                    () -> reader.read(box(0, 0, 0, 1, 0, 0), "owner", untouched, () -> {}));
            assertEquals(0, untouched.reads.get());
        }
    }

    @Test
    void authorizationAndCancellationStopBeforeAdditionalCellsAndDoNotConsumeTheInputCursor() {
        View view = new View();
        try (var reader = reader(new AtomicLong())) {
            JsonObject query = box(0, 0, 0, 3, 0, 0);
            query.addProperty("maxBlocks", 2);
            JsonObject next = continuation(reader.read(query, "owner", view, () -> {}));
            int before = view.reads.get();
            assertThrows(
                    SecurityException.class,
                    () ->
                            reader.read(
                                    next,
                                    "owner",
                                    view,
                                    () -> {
                                        throw new SecurityException("revoked");
                                    }));
            assertThrows(
                    CancellationException.class,
                    () ->
                            reader.read(
                                    next,
                                    "owner",
                                    view,
                                    () -> {
                                        throw new CancellationException("cancelled");
                                    }));
            assertEquals(before, view.reads.get());
            assertThrows(
                    CancellationException.class,
                    () ->
                            reader.read(
                                    next,
                                    "owner",
                                    view,
                                    () -> {
                                        if (view.reads.get() > before)
                                            throw new CancellationException(
                                                    "cancelled during native sample");
                                    }));
            assertEquals(before + 1, view.reads.get(), "No second cell after cancellation");
            JsonObject recovered = reader.read(next, "owner", view, () -> {});
            assertEquals(2, recovered.get("pageStartOffset").getAsInt());
            assertTrue(recovered.get("complete").getAsBoolean());
        }
    }

    @Test
    void contextReplacementDuringAReadCannotPublishOrReadFurtherCells() {
        View view = new View();
        view.cells =
                (x, y, z) -> {
                    view.current = context("new native identity", 0.5);
                    return WorldRegionPages.Cell.known("minecraft:stone", Map.of());
                };
        try (var reader = reader(new AtomicLong())) {
            failure(
                    "WORLD_REGION_CONTEXT_CHANGED",
                    () -> reader.read(box(0, 0, 0, 3, 0, 0), "owner", view, () -> {}));
            assertEquals(1, view.reads.get());
        }
    }

    @Test
    void cursorRetentionIsBoundedAndExplicitCancelOrCloseReleasesIt() {
        View view = new View();
        JsonObject query = box(0, 0, 0, 2, 0, 0);
        query.addProperty("maxBlocks", 1);
        var reader = reader(new AtomicLong());
        String first = null;
        for (int i = 0; i < WorldRegionPages.MAX_CURSORS; i++) {
            String token =
                    reader.read(query, "owner", view, () -> {}).get("nextCursor").getAsString();
            if (first == null) first = token;
        }
        int before = view.reads.get();
        failure("WORLD_REGION_CURSOR_LIMIT", () -> reader.read(query, "owner", view, () -> {}));
        assertEquals(before, view.reads.get());
        assertTrue(reader.cancel(first, "owner"));
        assertFalse(reader.cancel(first, "owner"));
        assertTrue(reader.read(query, "owner", view, () -> {}).has("nextCursor"));
        reader.close();
        failure("WORLD_REGION_CLOSED", () -> reader.read(query, "owner", view, () -> {}));
        assertFalse(reader.cancel(first, "owner"));
    }

    @Test
    void paletteCopiesTypedPropertiesAndDoesNotDependOnMapOrder() {
        View view = new View();
        Map<String, String> original = new LinkedHashMap<>();
        original.put("waterlogged", "false");
        original.put("axis", "y");
        var cell = WorldRegionPages.Cell.known("minecraft:oak_log", original);
        original.put("axis", "x");
        view.cells =
                (x, y, z) ->
                        x == 0
                                ? cell
                                : WorldRegionPages.Cell.known(
                                        "minecraft:oak_log",
                                        Map.of("axis", "y", "waterlogged", "false"));
        try (var reader = reader(new AtomicLong())) {
            JsonObject result = reader.read(box(0, 0, 0, 1, 0, 0), "owner", view, () -> {});
            assertEquals(1, result.getAsJsonArray("palette").size());
            assertEquals("[0,0]", result.getAsJsonArray("indices").toString());
            assertFalse(result.has("resourceVersion"));
            assertFalse(result.has("targetRef"));
            assertFalse(result.has("nbt"));
        }
        failure(
                "WORLD_REGION_SOURCE_INVALID",
                () -> WorldRegionPages.Cell.known("../unsafe", Map.of()));
        failure(
                "WORLD_REGION_SOURCE_INVALID",
                () ->
                        WorldRegionPages.Cell.known(
                                "minecraft:stone", Map.of("raw", "{arbitrary:nbt}")));
    }

    @Test
    void absenceIsExplicitAndProducesNoContinuationOrCellRead() {
        View view = new View();
        view.current = null;
        try (var reader = reader(new AtomicLong())) {
            JsonObject result = reader.read(box(0, 0, 0, 1, 0, 0), "owner", view, () -> {});
            assertFalse(result.get("available").getAsBoolean());
            assertEquals("client_world_unavailable", result.get("reason").getAsString());
            assertFalse(result.has("nextCursor"));
            assertEquals(0, view.reads.get());
        }
    }

    private static WorldRegionPages reader(AtomicLong clock) {
        return new WorldRegionPages(clock::get, () -> 1_000L);
    }

    private static WorldRegionPages.Context context(String identity, double x) {
        return new WorldRegionPages.Context(identity, "minecraft:overworld", x, 0.5, 0.5, 42);
    }

    private static JsonObject continuation(JsonObject page) {
        JsonObject next = new JsonObject();
        if (page.has("nextCursor")) next.add("cursor", page.get("nextCursor").deepCopy());
        return next;
    }

    private static JsonObject box(int x, int y, int z, int maxX, int maxY, int maxZ) {
        return json(
                "{\"min\":{\"x\":"
                        + x
                        + ",\"y\":"
                        + y
                        + ",\"z\":"
                        + z
                        + "},\"max\":{\"x\":"
                        + maxX
                        + ",\"y\":"
                        + maxY
                        + ",\"z\":"
                        + maxZ
                        + "}}");
    }

    private static JsonObject json(String source) {
        return JsonParser.parseString(source).getAsJsonObject();
    }

    private static void failure(String code, org.junit.jupiter.api.function.Executable operation) {
        var error = assertThrows(WorldRegionPages.Failure.class, operation);
        assertEquals(code, error.code());
        assertTrue(error.httpStatus() >= 400);
    }
}
