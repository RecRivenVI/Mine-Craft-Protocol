package io.github.recrivenvi.minecraftprotocol.probe.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.recrivenvi.minecraftprotocol.core.ProtocolState;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

class NativeBlockEntitySelectorTest {
    private static final String ENGINE =
            "io/github/recrivenvi/minecraftprotocol/probe/runtime/Phase9ASpikeEngine";

    @Test
    void formalContinuationReachesTheProductionPagerAndRejectsClientOnlyReads() throws Exception {
        ClassNode engine = compiled(ENGINE);
        assertTrue(
                calls(method(engine, "blockEntities")).stream()
                        .anyMatch(
                                call ->
                                        call.owner.equals(
                                                        "io/github/recrivenvi/minecraftprotocol/core/DeepBlockEntityPages")
                                                && call.name.equals("select")));
        assertTrue(
                calls(method(engine, "close")).stream()
                        .anyMatch(
                                call ->
                                        call.owner.equals(
                                                        "io/github/recrivenvi/minecraftprotocol/core/DeepBlockEntityPages")
                                                && call.name.equals("clear")));
        for (String body :
                List.of(
                        "{\"perspective\":\"client_known\",\"domains\":[\"block_entities\"],\"selector\":{\"blockEntityCursor\":\"opaque\"}}",
                        "{\"perspective\":\"server_authoritative\",\"domains\":[\"blocks\"],\"selector\":{\"blockEntityCursor\":\"opaque\"}}")) {
            ProtocolState.ProtocolException rejected =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () ->
                                    Phase9ASpikeEngine.formalChunkRadius(
                                            JsonParser.parseString(body).getAsJsonObject()));
            assertEquals("UNSUPPORTED_DEEP_SELECTOR", rejected.code());
        }
    }

    @Test
    void formalCoordinatesReachTheProductionCaptureThatReadsBlockEntities() throws Exception {
        ClassNode engine = compiled(ENGINE);
        MethodNode formal = method(engine, "captureFormal");
        assertTrue(
                Arrays.stream(formal.instructions.toArray())
                        .filter(LdcInsnNode.class::isInstance)
                        .map(LdcInsnNode.class::cast)
                        .anyMatch(value -> "selectedBlocks".equals(value.cst)));
        assertTrue(calls(formal).stream().anyMatch(call -> call.name.equals("normalizedSelector")));
        assertTrue(calls(formal).stream().anyMatch(call -> call.name.equals("captureSnapshot")));
        assertTrue(
                calls(method(engine, "captureSnapshot")).stream()
                        .anyMatch(
                                call ->
                                        call.owner.equals(ENGINE)
                                                && call.name.equals("blockEntities")
                                                && call.desc.contains(
                                                        "Lcom/google/gson/JsonObject;")));
    }

    @Test
    void productionBlockEntityCoordinatesMustSelectTheNativeCacheInsteadOfScanningUnrelatedEntries()
            throws Exception {
        ClassNode engine = compiled(ENGINE);
        List<MethodNode> production = reachable(engine, method(engine, "blockEntities"));
        assertTrue(
                production.stream().anyMatch(NativeBlockEntitySelectorTest::readsSelectedBlocks),
                "CF-021: captureFormal preserves selectedBlocks, but the actual blockEntities path ignores it");
        List<MethodInsnNode> calls =
                production.stream().flatMap(value -> calls(value).stream()).toList();
        assertTrue(
                calls.stream().anyMatch(call -> call.name.equals("getChunkNow")),
                "Explicit coordinates must consult only an already loaded chunk");
        assertTrue(
                calls.stream().anyMatch(call -> call.name.equals("getBlockEntities")),
                "Explicit coordinates must use the existing native block-entity map");
        assertTrue(
                calls.stream()
                        .anyMatch(
                                call ->
                                        call.owner.equals("java/util/Map")
                                                && call.name.equals("get")),
                "Selected coordinates must perform exact cache lookup, not return every entry");
        for (String forbidden :
                List.of("getChunk", "getChunkAt", "getBlockEntity", "computeIfAbsent")) {
            assertFalse(
                    calls.stream().anyMatch(call -> call.name.equals(forbidden)),
                    "Selected deep reads must not load chunks or instantiate block entities: "
                            + forbidden);
        }
    }

    @Test
    void actualNativeBlockEntityMapAccessorDoesNotLoadOrInstantiateAnything() throws Exception {
        MethodNode getter =
                method(compiled("net/minecraft/world/level/chunk/LevelChunk"), "getBlockEntities");
        assertTrue(
                Arrays.stream(getter.instructions.toArray())
                        .filter(FieldInsnNode.class::isInstance)
                        .map(FieldInsnNode.class::cast)
                        .anyMatch(
                                field ->
                                        field.getOpcode() == Opcodes.GETFIELD
                                                && field.desc.equals("Ljava/util/Map;")));
        assertFalse(
                Arrays.stream(getter.instructions.toArray())
                        .anyMatch(
                                instruction ->
                                        instruction.getOpcode() == Opcodes.PUTFIELD
                                                || instruction.getOpcode() == Opcodes.NEW));
        for (MethodInsnNode call : calls(getter)) {
            assertFalse(
                    List.of("getChunk", "getChunkNow", "getBlockEntity", "computeIfAbsent", "put")
                            .contains(call.name));
        }
    }

    @Test
    void exactCoordinateFixtureSeparatesAnotherEntityMissingPositionAndUnloadedChunk() {
        CacheFixture fixture = cacheFixture();
        assertEquals(chunk(fixture.selected()), chunk(fixture.unrelated()));
        assertEquals(chunk(fixture.selected()), chunk(fixture.missing()));
        assertNotEquals(chunk(fixture.selected()), chunk(fixture.unloaded()));
        Map<BlockPos, String> nativeMap = fixture.loaded().get(chunk(fixture.selected()));
        assertEquals(2, nativeMap.size());
        assertEquals("selected-barrel", nativeMap.get(fixture.selected()));
        assertEquals("unrelated-chest", nativeMap.get(fixture.unrelated()));
        assertNull(nativeMap.get(fixture.missing()));
        assertFalse(fixture.loaded().containsKey(chunk(fixture.unloaded())));
        assertNotEquals(fixture.selected(), fixture.unrelated());
        List<BlockPos> actual =
                Phase9ASpikeEngine.selectedBlockPositions(
                        JsonParser.parseString(
                                        "[{\"x\":17,\"y\":64,\"z\":-1},{\"x\":17.0,\"y\":64,\"z\":-1}]")
                                .getAsJsonArray());
        assertEquals(List.of(fixture.selected()), actual);
        assertEquals(List.of("selected-barrel"), actual.stream().map(nativeMap::get).toList());
        assertEquals(List.of(), Phase9ASpikeEngine.selectedBlockPositions(new JsonArray()));
    }

    @Test
    void formalBlockEntitySelectorRejectsInvalidCoordinatesBeforeAnyWorldCapture() {
        for (String blocks :
                List.of(
                        "null",
                        "{}",
                        "[null]",
                        "[{}]",
                        "[{\"x\":1.5,\"y\":64,\"z\":0}]",
                        "[{\"x\":\"1\",\"y\":64,\"z\":0}]",
                        "[{\"x\":2147483648,\"y\":64,\"z\":0}]")) {
            JsonObject request =
                    JsonParser.parseString(
                                    "{\"domains\":[\"block_entities\"],\"selector\":{\"blocks\":"
                                            + blocks
                                            + "}}")
                            .getAsJsonObject();
            var error =
                    assertThrows(
                            ProtocolState.ProtocolException.class,
                            () -> Phase9ASpikeEngine.formalChunkRadius(request));
            assertEquals("INVALID_DEEP_SELECTOR", error.code());
            assertEquals(400, error.httpStatus());
            try (var engine = new Phase9ASpikeEngine("1.20.1-forge")) {
                assertEquals(
                        "INVALID_DEEP_SELECTOR",
                        assertThrows(
                                        ProtocolState.ProtocolException.class,
                                        () -> engine.captureFormal(null, null, request))
                                .code());
            }
        }
    }

    @Test
    void unsupportedClientCoordinatesDoNotExposeAnotherInternalClientDomainOrRejectOtherDomains() {
        JsonObject request =
                JsonParser.parseString(
                                "{\"perspective\":\"client_known\",\"domains\":[\"block_entities\"],\"selector\":{\"blocks\":[{\"x\":1,\"y\":64,\"z\":0}]}}")
                        .getAsJsonObject();
        var error =
                assertThrows(
                        ProtocolState.ProtocolException.class,
                        () -> Phase9ASpikeEngine.formalChunkRadius(request));
        assertEquals("UNSUPPORTED_DEEP_SELECTOR", error.code());
        assertEquals(400, error.httpStatus());
        request.addProperty("perspective", "server_authoritative");
        assertEquals(0, Phase9ASpikeEngine.formalChunkRadius(request));
        request.addProperty("perspective", "both");
        assertEquals(0, Phase9ASpikeEngine.formalChunkRadius(request));
        request.addProperty("perspective", "client_known");
        request.add("domains", JsonParser.parseString("[\"blocks\"]"));
        assertEquals(0, Phase9ASpikeEngine.formalChunkRadius(request));
        request.add("domains", JsonParser.parseString("[\"player\"]"));
        request.getAsJsonObject("selector").addProperty("blocks", "unrelated");
        assertEquals(0, Phase9ASpikeEngine.formalChunkRadius(request));
    }

    @Test
    void exactCoordinatesKeepTheExistingSixtyFourPositionLimit() {
        JsonArray positions = new JsonArray();
        for (int index = 0; index < 65; index++)
            positions.add(JsonParser.parseString("{\"x\":" + index + ",\"y\":64,\"z\":0}"));
        var error =
                assertThrows(
                        ProtocolState.ProtocolException.class,
                        () -> Phase9ASpikeEngine.selectedBlockPositions(positions));
        assertEquals("PHASE9A_SELECTOR_TOO_LARGE", error.code());
        assertEquals(413, error.httpStatus());
    }

    // Finite loaded-cache input; production parsing and native cache access are tested separately.
    private static CacheFixture cacheFixture() {
        BlockPos unrelated = new BlockPos(16, 64, -1);
        BlockPos selected = new BlockPos(17, 64, -1);
        BlockPos missing = new BlockPos(18, 64, -1);
        BlockPos unloaded = new BlockPos(160000, 64, -160001);
        Map<BlockPos, String> entries = new LinkedHashMap<>();
        entries.put(unrelated, "unrelated-chest");
        entries.put(selected, "selected-barrel");
        return new CacheFixture(
                Map.of(chunk(selected), entries), selected, unrelated, missing, unloaded);
    }

    private static ChunkPos chunk(BlockPos position) {
        return new ChunkPos(position.getX() >> 4, position.getZ() >> 4);
    }

    private record CacheFixture(
            Map<ChunkPos, Map<BlockPos, String>> loaded,
            BlockPos selected,
            BlockPos unrelated,
            BlockPos missing,
            BlockPos unloaded) {}

    private static boolean readsSelectedBlocks(MethodNode method) {
        boolean selectedArgument = false;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof LdcInsnNode literal) {
                selectedArgument = "selectedBlocks".equals(literal.cst);
            } else if (instruction instanceof MethodInsnNode call && selectedArgument) {
                if (call.owner.equals("com/google/gson/JsonObject")
                        && call.name.equals("getAsJsonArray")
                        && call.desc.equals("(Ljava/lang/String;)Lcom/google/gson/JsonArray;"))
                    return true;
                selectedArgument = false;
            }
        }
        return false;
    }

    private static List<MethodNode> reachable(ClassNode owner, MethodNode start) {
        List<MethodNode> result = new ArrayList<>();
        List<MethodNode> pending = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            MethodNode next = pending.remove(pending.size() - 1);
            if (!visited.add(next.name + next.desc)) continue;
            result.add(next);
            for (MethodInsnNode call : calls(next)) {
                if (!call.owner.equals(owner.name)) continue;
                owner.methods.stream()
                        .filter(
                                method ->
                                        method.name.equals(call.name)
                                                && method.desc.equals(call.desc))
                        .forEach(pending::add);
            }
        }
        return result;
    }

    private static List<MethodInsnNode> calls(MethodNode method) {
        return Arrays.stream(method.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance)
                .map(MethodInsnNode.class::cast)
                .toList();
    }

    private static MethodNode method(ClassNode owner, String name) {
        return owner.methods.stream()
                .filter(method -> method.name.equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static ClassNode compiled(String name) throws Exception {
        try (InputStream input =
                NativeBlockEntitySelectorTest.class.getResourceAsStream("/" + name + ".class")) {
            assertNotNull(input, name);
            ClassNode result = new ClassNode();
            new ClassReader(input).accept(result, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            return result;
        }
    }
}
