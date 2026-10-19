package io.github.recrivenvi.minecraftprotocol.safety;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.function.LongSupplier;

/** Owner-thread pages over an already loaded client cache; never a server/storage reader. */
public final class WorldRegionPages implements AutoCloseable {
    public static final int MAX_CURSORS = 32;
    public static final int MAX_REGION_CELLS = 65_536;
    public static final int MAX_PAGE_CELLS = WorldClientModel.MAX_BLOCK_SCAN;
    public static final int MIN_PAGE_BYTES = 16_384;
    public static final int MAX_PAGE_BYTES = 131_072;
    public static final long MAX_OWNER_NANOS = 8_000_000L;
    private static final long IDLE_NANOS = 30_000_000_000L;
    private static final long LIFETIME_NANOS = 120_000_000_000L;
    private static final int METADATA_RESERVE_BYTES = 4_096;
    private final LongSupplier nanos;
    private final LongSupplier millis;
    private final Map<String, Stream> cursors = new HashMap<>();
    private boolean closed;

    /**
     * identity is minted from real native runtime/connection/world/player epochs, not query data.
     */
    public record Context(
            String identity, String dimension, double x, double y, double z, long sourceTick) {
        public Context {
            if (identity == null
                    || identity.isBlank()
                    || identity.length() > 256
                    || !resourceId(dimension)
                    || !Double.isFinite(x)
                    || !Double.isFinite(y)
                    || !Double.isFinite(z)
                    || sourceTick < 0)
                throw failure("WORLD_REGION_SOURCE_INVALID", 409, "Invalid native client context");
        }
    }

    /** Only native registry identity and block-state properties enter a palette; never NBT. */
    public record Cell(boolean loaded, String block, Map<String, String> properties) {
        public Cell {
            if (!loaded) {
                if (block != null || properties == null || !properties.isEmpty())
                    throw failure("WORLD_REGION_SOURCE_INVALID", 409, "Invalid unloaded cell");
            } else {
                if (!resourceId(block) || properties == null || properties.size() > 32)
                    throw failure("WORLD_REGION_SOURCE_INVALID", 409, "Invalid client block state");
                for (var entry : properties.entrySet())
                    if (entry.getKey() == null
                            || !entry.getKey().matches("[a-z0-9_]{1,64}")
                            || entry.getValue() == null
                            || !entry.getValue().matches("[a-z0-9_.:/-]{1,128}"))
                        throw failure("WORLD_REGION_SOURCE_INVALID", 409, "Invalid block property");
            }
            properties = Map.copyOf(properties);
        }

        public static Cell known(String block, Map<String, String> properties) {
            return new Cell(true, block, properties);
        }

        public static Cell unloaded() {
            return new Cell(false, null, Map.of());
        }
    }

    public interface View {
        /** Null means no current client world. This call must not load or consult a server. */
        Context context();

        /** Must use cached native chunks/sections with loading disabled, on their owner thread. */
        Cell cell(int x, int y, int z);
    }

    public static final class Failure extends IllegalArgumentException {
        private final String code;
        private final int httpStatus;

        private Failure(String code, int httpStatus, String message) {
            super(message);
            this.code = code;
            this.httpStatus = httpStatus;
        }

        public String code() {
            return code;
        }

        public int httpStatus() {
            return httpStatus;
        }
    }

    public WorldRegionPages() {
        this(System::nanoTime, System::currentTimeMillis);
    }

    WorldRegionPages(LongSupplier nanos, LongSupplier millis) {
        this.nanos = Objects.requireNonNull(nanos);
        this.millis = Objects.requireNonNull(millis);
    }

    public JsonObject read(JsonObject query, String trustedOwner, View view, Runnable guard) {
        return read(query, trustedOwner, view, guard, MAX_OWNER_NANOS);
    }

    /** The caller runs this synchronously on the native client owner; guards must never block. */
    public synchronized JsonObject read(
            JsonObject query,
            String trustedOwner,
            View view,
            Runnable guard,
            long ownerBudgetNanos) {
        guard.run();
        cancelled();
        Objects.requireNonNull(trustedOwner);
        if (trustedOwner.isBlank()) throw new IllegalArgumentException("Trusted owner is required");
        if (closed) throw failure("WORLD_REGION_CLOSED", 503, "Region reader is closed");
        if (ownerBudgetNanos <= 0 || ownerBudgetNanos > MAX_OWNER_NANOS)
            throw new IllegalArgumentException("Invalid native owner time budget");
        long started = nanos.getAsLong();
        String cursor = cursor(query);
        Stream stream;
        if (cursor != null) {
            stream = cursors.get(cursor);
            if (stream == null || !stream.owner.equals(trustedOwner))
                throw failure(
                        "WORLD_REGION_CURSOR_INVALID", 409, "Unknown or unavailable region cursor");
            if (expired(stream, started)) {
                cursors.remove(cursor);
                throw failure(
                        "WORLD_REGION_CURSOR_EXPIRED",
                        409,
                        "Region cursor expired; restart the read");
            }
        } else {
            Request request = request(query);
            Context context = view.context();
            if (context == null) {
                JsonObject unavailable = metadata();
                unavailable.addProperty("available", false);
                unavailable.addProperty("reason", "client_world_unavailable");
                guard.run();
                cancelled();
                return unavailable;
            }
            inRange(request.bounds, context);
            cursors.entrySet().removeIf(entry -> expired(entry.getValue(), started));
            if (cursors.size() >= MAX_CURSORS)
                throw failure(
                        "WORLD_REGION_CURSOR_LIMIT",
                        429,
                        "Cancel a region cursor or wait for expiry");
            stream = new Stream(trustedOwner, context, request, started, started, 0, 0);
        }
        Context context = active(view, guard, stream);
        Request request = stream.request;
        Slice slice =
                request.slices.stream()
                        .filter(value -> stream.offset < value.start + value.cells())
                        .findFirst()
                        .orElseThrow();
        int startInSlice = stream.offset - slice.start;
        JsonArray palette = new JsonArray(), indices = new JsonArray();
        Map<Cell, Integer> paletteIndices = new LinkedHashMap<>();
        int payloadBytes = 0, unloaded = 0;
        String reason = "section_boundary";
        while (startInSlice + indices.size() < slice.cells()) {
            active(view, guard, stream);
            if (indices.size() >= request.maxBlocks) {
                reason = "cell_budget";
                break;
            }
            if (nanos.getAsLong() - started >= ownerBudgetNanos) {
                reason = "owner_time_budget";
                break;
            }
            int offset = startInSlice + indices.size();
            int x = slice.minX + offset % slice.sizeX;
            int z = slice.minZ + offset / slice.sizeX % slice.sizeZ;
            int y = slice.minY + offset / (slice.sizeX * slice.sizeZ);
            Cell cell = Objects.requireNonNull(view.cell(x, y, z), "Native cell is required");
            Integer index = cell.loaded ? paletteIndices.get(cell) : Integer.valueOf(-1);
            JsonObject addition = null;
            int extra = 0;
            if (index == null) {
                index = palette.size();
                addition = new JsonObject();
                addition.addProperty("block", cell.block);
                JsonObject properties = new JsonObject();
                new TreeMap<>(cell.properties).forEach(properties::addProperty);
                addition.add("properties", properties);
                extra = bytes(addition) + 1;
            }
            extra += Integer.toString(index).length() + 1;
            if (METADATA_RESERVE_BYTES + payloadBytes + extra > request.maxBytes) {
                reason = "byte_budget";
                break;
            }
            if (addition != null) {
                palette.add(addition);
                paletteIndices.put(cell, index);
            }
            indices.add(index);
            payloadBytes += extra;
            if (!cell.loaded) unloaded++;
        }
        if (indices.isEmpty())
            throw failure(
                    reason.equals("byte_budget")
                            ? "WORLD_REGION_PAGE_TOO_SMALL"
                            : "WORLD_REGION_BUDGET_NO_PROGRESS",
                    reason.equals("byte_budget") ? 413 : 429,
                    "No cell fits this page budget; no continuation was committed");
        Context completedContext = active(view, guard, stream);
        int covered = stream.offset + indices.size();
        boolean complete = covered == request.bounds.cells();
        String nextCursor = complete ? null : UUID.randomUUID().toString();
        JsonObject result = metadata();
        result.addProperty("available", true);
        result.addProperty("contextId", context.identity);
        result.addProperty("dimension", context.dimension);
        result.addProperty("sourceTick", context.sourceTick);
        result.addProperty("sourceTickEnd", completedContext.sourceTick);
        result.addProperty("sampledAtMillis", millis.getAsLong());
        result.addProperty("captureDurationNanos", Math.max(0, nanos.getAsLong() - started));
        result.add("region", request.bounds.json());
        JsonObject section = new JsonObject();
        section.addProperty("x", slice.sectionX);
        section.addProperty("y", slice.sectionY);
        section.addProperty("z", slice.sectionZ);
        section.add("min", position(slice.minX, slice.minY, slice.minZ));
        section.add("size", position(slice.sizeX, slice.sizeY, slice.sizeZ));
        section.addProperty("offset", startInSlice);
        result.add("section", section);
        result.add("palette", palette);
        result.add("indices", indices);
        result.addProperty("pageStartOffset", stream.offset);
        result.addProperty("pageCellCount", indices.size());
        result.addProperty("pageUnloadedCellCount", unloaded);
        result.addProperty("requestedCellCount", request.bounds.cells());
        result.addProperty("coveredCellCount", covered);
        result.addProperty("remainingCellCount", request.bounds.cells() - covered);
        result.addProperty("unloadedCellCount", stream.unloaded + unloaded);
        result.addProperty("complete", complete);
        result.addProperty("allLoaded", complete && stream.unloaded + unloaded == 0);
        result.addProperty("truncated", !complete);
        result.addProperty("truncationReason", complete ? "none" : reason);
        result.addProperty("maxBlocks", request.maxBlocks);
        result.addProperty("maxBytes", request.maxBytes);
        result.addProperty("ownerBudgetNanos", ownerBudgetNanos);
        if (nextCursor != null) result.addProperty("nextCursor", nextCursor);
        if (bytes(result) > request.maxBytes)
            throw failure(
                    "WORLD_REGION_PAGE_TOO_SMALL", 413, "Region page exceeds its byte budget");
        active(view, guard, stream);
        long committed = nanos.getAsLong();
        if (expired(stream, committed))
            throw failure(
                    "WORLD_REGION_CURSOR_EXPIRED", 409, "Region read expired before publication");
        // Only a successfully guarded page consumes its input cursor. Failed reads may retry it.
        if (cursor != null) cursors.remove(cursor);
        if (nextCursor != null)
            cursors.put(
                    nextCursor,
                    new Stream(
                            stream.owner,
                            stream.context,
                            request,
                            stream.created,
                            committed,
                            covered,
                            stream.unloaded + unloaded));
        return result;
    }

    /** Idempotent release, including after a caller no longer needs an unfinished read. */
    public synchronized boolean cancel(String cursor, String trustedOwner) {
        Stream stream = cursors.get(cursor);
        if (stream == null || !stream.owner.equals(trustedOwner)) return false;
        cursors.remove(cursor);
        return true;
    }

    @Override
    public synchronized void close() {
        closed = true;
        cursors.clear();
    }

    private Context active(View view, Runnable guard, Stream stream) {
        guard.run();
        cancelled();
        Context current = view.context();
        if (current == null
                || !stream.context.identity.equals(current.identity)
                || !stream.context.dimension.equals(current.dimension))
            throw failure(
                    "WORLD_REGION_CONTEXT_CHANGED",
                    409,
                    "Client context changed; restart the read");
        inRange(stream.request.bounds, current);
        return current;
    }

    private static JsonObject metadata() {
        JsonObject result = new JsonObject();
        result.addProperty("type", "world.region");
        result.addProperty("domain", "WORLD");
        result.addProperty("dataCategory", "USER");
        result.addProperty("authority", "CLIENT_KNOWN");
        result.addProperty("source", "bounded_client_world_model");
        result.addProperty("dataSource", "LIVE");
        result.addProperty("stateSemantics", "per_page_live_client_cache_non_atomic");
        result.addProperty("order", "section_y_z_x_then_local_y_z_x");
        result.addProperty("coverage", "requested_cells_in_existing_client_cache");
        result.addProperty("unloadedPaletteIndex", -1);
        result.addProperty("completeGameData", false);
        result.addProperty("crossPageAtomic", false);
        result.addProperty("storageAccessed", false);
        result.addProperty("chunkLoadRequested", false);
        return result;
    }

    private static String cursor(JsonObject query) {
        if (query == null) throw invalid("Region query is required");
        if (!query.has("cursor")) return null;
        if (!query.keySet().equals(Set.of("cursor")))
            throw invalid("A continuation accepts only its fixed-query cursor");
        JsonElement value = query.get("cursor");
        if (!value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()
                || value.getAsString().length() > 128) throw invalid("Invalid region cursor");
        return value.getAsString();
    }

    private static Request request(JsonObject query) {
        if (!Set.of("min", "max", "maxBlocks", "maxBytes").containsAll(query.keySet()))
            throw invalid("Unsupported region query field");
        int[] min = point(query, "min"), max = point(query, "max");
        int span = WorldClientModel.MAX_RADIUS * 2 + 1;
        long volume = 1;
        for (int axis = 0; axis < 3; axis++) {
            long length = (long) max[axis] - min[axis] + 1;
            if (length <= 0 || length > span) throw invalid("Invalid bounded region extent");
            volume *= length;
        }
        if (volume > MAX_REGION_CELLS) throw invalid("Region cell budget exceeded");
        Bounds bounds = new Bounds(min[0], min[1], min[2], max[0], max[1], max[2]);
        List<Slice> slices = new ArrayList<>();
        int start = 0;
        for (int y = Math.floorDiv(min[1], 16); y <= Math.floorDiv(max[1], 16); y++)
            for (int z = Math.floorDiv(min[2], 16); z <= Math.floorDiv(max[2], 16); z++)
                for (int x = Math.floorDiv(min[0], 16); x <= Math.floorDiv(max[0], 16); x++) {
                    int lowX = Math.max(min[0], x * 16),
                            lowY = Math.max(min[1], y * 16),
                            lowZ = Math.max(min[2], z * 16);
                    Slice slice =
                            new Slice(
                                    x,
                                    y,
                                    z,
                                    lowX,
                                    lowY,
                                    lowZ,
                                    Math.min(max[0], x * 16 + 15) - lowX + 1,
                                    Math.min(max[1], y * 16 + 15) - lowY + 1,
                                    Math.min(max[2], z * 16 + 15) - lowZ + 1,
                                    start);
                    slices.add(slice);
                    start += slice.cells();
                }
        return new Request(
                bounds,
                List.copyOf(slices),
                integer(query, "maxBlocks", MAX_PAGE_CELLS, 1, MAX_PAGE_CELLS),
                integer(query, "maxBytes", 65_536, MIN_PAGE_BYTES, MAX_PAGE_BYTES));
    }

    private static int[] point(JsonObject query, String key) {
        if (!query.has(key) || !query.get(key).isJsonObject())
            throw invalid("Missing region " + key);
        JsonObject point = query.getAsJsonObject(key);
        if (!point.keySet().equals(Set.of("x", "y", "z"))) throw invalid("Invalid region position");
        return new int[] {
            integer(point, "x", 0, Integer.MIN_VALUE, Integer.MAX_VALUE),
            integer(point, "y", 0, Integer.MIN_VALUE, Integer.MAX_VALUE),
            integer(point, "z", 0, Integer.MIN_VALUE, Integer.MAX_VALUE)
        };
    }

    private static int integer(JsonObject query, String key, int fallback, int min, int max) {
        if (!query.has(key)) return fallback;
        try {
            JsonElement field = query.get(key);
            if (!field.isJsonPrimitive() || !field.getAsJsonPrimitive().isNumber())
                throw invalid("Invalid " + key);
            int value = field.getAsBigDecimal().intValueExact();
            if (value < min || value > max) throw invalid("Out of range " + key);
            return value;
        } catch (RuntimeException malformed) {
            throw invalid("Invalid region integer " + key);
        }
    }

    private static void inRange(Bounds bounds, Context context) {
        double limit = (double) WorldClientModel.MAX_RADIUS * WorldClientModel.MAX_RADIUS;
        for (int x : new int[] {bounds.minX, bounds.maxX})
            for (int y : new int[] {bounds.minY, bounds.maxY})
                for (int z : new int[] {bounds.minZ, bounds.maxZ}) {
                    double dx = x + 0.5 - context.x,
                            dy = y + 0.5 - context.y,
                            dz = z + 0.5 - context.z;
                    if (dx * dx + dy * dy + dz * dz > limit)
                        throw failure(
                                "WORLD_REGION_OUTSIDE_USER_RADIUS",
                                409,
                                "Region left the bounded USER radius");
                }
    }

    private static boolean resourceId(String value) {
        return value != null
                && value.length() <= 256
                && value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+");
    }

    private static JsonObject position(int x, int y, int z) {
        JsonObject point = new JsonObject();
        point.addProperty("x", x);
        point.addProperty("y", y);
        point.addProperty("z", z);
        return point;
    }

    private static int bytes(JsonElement json) {
        return json.toString().getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean expired(Stream stream, long now) {
        return now - stream.touched >= IDLE_NANOS || now - stream.created >= LIFETIME_NANOS;
    }

    private static void cancelled() {
        if (Thread.currentThread().isInterrupted())
            throw new CancellationException("Region read cancelled");
    }

    private static Failure invalid(String message) {
        return failure("INVALID_WORLD_REGION_QUERY", 400, message);
    }

    private static Failure failure(String code, int status, String message) {
        return new Failure(code, status, message);
    }

    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int cells() {
            return (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }

        JsonObject json() {
            JsonObject bounds = new JsonObject();
            bounds.add("min", position(minX, minY, minZ));
            bounds.add("max", position(maxX, maxY, maxZ));
            return bounds;
        }
    }

    private record Slice(
            int sectionX,
            int sectionY,
            int sectionZ,
            int minX,
            int minY,
            int minZ,
            int sizeX,
            int sizeY,
            int sizeZ,
            int start) {
        int cells() {
            return sizeX * sizeY * sizeZ;
        }
    }

    private record Request(Bounds bounds, List<Slice> slices, int maxBlocks, int maxBytes) {}

    private record Stream(
            String owner,
            Context context,
            Request request,
            long created,
            long touched,
            int offset,
            int unloaded) {}
}
