package io.github.recrivenvi.minecraftprotocol.safety;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class WorldClientModelTest {
    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }

    private static class View implements WorldClientModel.View {
        boolean available = true;
        AtomicInteger blocks = new AtomicInteger();
        AtomicInteger entities = new AtomicInteger();

        public boolean available() {
            return available;
        }

        public JsonObject player() {
            return json(
                    "{\"x\":0.25,\"y\":64.5,\"z\":0.25,\"health\":19.75,\"uuid\":\"hidden\",\"nbt\":\"hidden\"}");
        }

        public JsonObject block(int x, int y, int z) {
            blocks.incrementAndGet();
            return x < 0
                    ? json("{\"available\":false,\"reason\":\"chunk_not_loaded\"}")
                    : json(
                            "{\"available\":true,\"block\":\"minecraft:stone\",\"state\":\"stone\",\"nbt\":\"secret\",\"inventory\":\"secret\"}");
        }

        public Iterator<JsonObject> entities() {
            return IntStream.range(0, 10000)
                    .mapToObj(
                            i -> {
                                entities.incrementAndGet();
                                return json(
                                        "{\"type\":\"minecraft:pig\",\"x\":1,\"y\":64,\"z\":1,\"invisible\":true,\"uuid\":\"secret\",\"health\":20,\"goal\":\"secret\"}");
                            })
                    .iterator();
        }
    }

    @Test
    void ownPlayerClientFlagsStayTypedAndMissingInformationIsExplicitlyUnknown() {
        View view =
                new View() {
                    public JsonObject player() {
                        return json(
                                "{\"x\":0,\"y\":64,\"z\":0,\"gameMode\":\"creative\",\"onGround\":false,\"flying\":true,\"serverPermission\":4,\"uuid\":\"secret\"}");
                    }
                };
        var result = WorldClientModel.capture("player", new JsonObject(), view);
        assertEquals("creative", result.get("gameMode").getAsString());
        assertFalse(result.get("onGround").getAsBoolean());
        assertTrue(result.get("flying").getAsBoolean());
        assertFalse(result.has("serverPermission"));
        assertFalse(result.has("uuid"));
        for (boolean available : new boolean[] {true, false}) {
            View missing = new View();
            missing.available = available;
            var unknown = WorldClientModel.capture("player", new JsonObject(), missing);
            for (String field : java.util.List.of("gameMode", "onGround", "flying"))
                assertEquals("unknown", unknown.get(field).getAsString());
        }
    }

    @Test
    void clientKnowledgeDoesNotRequireHudVisibilityAndDoesNotPublishInternalFields() {
        View view = new View();
        JsonObject player = WorldClientModel.capture("player", new JsonObject(), view);
        assertEquals(19.75, player.get("health").getAsDouble());
        assertEquals(0.25, player.get("x").getAsDouble());
        assertFalse(player.has("uuid"));
        assertFalse(player.has("nbt"));
        assertEquals("CLIENT_KNOWN", player.get("authority").getAsString());
        assertEquals("not_evaluated", player.get("visibility").getAsString());
        assertFalse(player.get("storageAccessed").getAsBoolean());
        assertFalse(player.get("chunkLoadRequested").getAsBoolean());
        JsonObject block =
                WorldClientModel.capture("block", json("{\"x\":1,\"y\":64,\"z\":0}"), view);
        assertEquals("minecraft:stone", block.get("block").getAsString());
        assertFalse(block.has("nbt"));
        assertFalse(block.has("inventory"));
        JsonObject entities = WorldClientModel.capture("entities", json("{\"limit\":1}"), view);
        assertEquals(1, entities.getAsJsonArray("entities").size());
        assertEquals(1, view.entities.get());
        assertFalse(entities.toString().contains("secret"));
        assertTrue(
                entities.getAsJsonArray("entities")
                        .get(0)
                        .getAsJsonObject()
                        .get("invisible")
                        .getAsBoolean());
    }

    @Test
    void nativeUseAndHurtFeedbackDoNotExposeEntityHealth() {
        View view =
                new View() {
                    public JsonObject player() {
                        return json(
                                "{\"x\":0,\"y\":64,\"z\":0,\"usingItem\":true,\"usingHand\":\"off_hand\",\"riding\":true,\"useTicks\":12,\"inventory\":\"secret\"}");
                    }

                    public Iterator<JsonObject> entities() {
                        return java.util.List.of(
                                        json(
                                                "{\"type\":\"minecraft:pig\",\"x\":1,\"y\":64,\"z\":0,\"hurtAnimation\":true,\"health\":12,\"uuid\":\"secret\"}"))
                                .iterator();
                    }
                };
        JsonObject player = WorldClientModel.capture("player", new JsonObject(), view);
        assertTrue(player.get("usingItem").getAsBoolean());
        assertEquals(12, player.get("useTicks").getAsInt());
        assertEquals("off_hand", player.get("usingHand").getAsString());
        assertTrue(player.get("riding").getAsBoolean());
        assertFalse(player.has("inventory"));
        JsonObject entity =
                WorldClientModel.capture("entities", json("{\"limit\":1}"), view)
                        .getAsJsonArray("entities")
                        .get(0)
                        .getAsJsonObject();
        assertTrue(entity.get("hurtAnimation").getAsBoolean());
        assertFalse(entity.has("health"));
        assertFalse(entity.has("uuid"));
    }

    @Test
    void entityInteractionFeedbackIsClientOnlyAndDoesNotPublishBreedingOrOwnerInternals() {
        View view =
                new View() {
                    public Iterator<JsonObject> entities() {
                        return java.util.List.of(
                                        json(
                                                "{\"type\":\"minecraft:wolf\",\"x\":1,\"y\":64,\"z\":0,\"baby\":false,\"tamed\":true,\"sitting\":true,\"leashed\":true,\"leashHolderKnown\":true,\"leashHeldByPlayer\":true,\"ownerUUID\":\"secret\",\"inLove\":true,\"breedCooldown\":6000,\"health\":20,\"nbt\":\"secret\"}"))
                                .iterator();
                    }
                };
        for (String kind : java.util.List.of("entities", "scene")) {
            JsonObject entity =
                    WorldClientModel.capture(kind, new JsonObject(), view)
                            .getAsJsonArray("entities")
                            .get(0)
                            .getAsJsonObject();
            for (String field :
                    java.util.List.of(
                            "tamed", "sitting", "leashed", "leashHolderKnown", "leashHeldByPlayer"))
                assertTrue(entity.get(field).getAsBoolean());
            assertFalse(entity.get("baby").getAsBoolean());
            for (String field :
                    java.util.List.of("ownerUUID", "inLove", "breedCooldown", "health", "nbt"))
                assertFalse(entity.has(field));
        }
        View unresolved =
                new View() {
                    public Iterator<JsonObject> entities() {
                        return java.util.List.of(
                                        json(
                                                "{\"type\":\"minecraft:cow\",\"x\":1,\"y\":64,\"z\":0,\"leashed\":true,\"leashHolderKnown\":false}"))
                                .iterator();
                    }
                };
        JsonObject entity =
                WorldClientModel.capture("entities", new JsonObject(), unresolved)
                        .getAsJsonArray("entities")
                        .get(0)
                        .getAsJsonObject();
        assertFalse(entity.has("leashHeldByPlayer"));
        assertFalse(entity.has("tamed"));
    }

    @Test
    void executableReferencesSurviveOnlyTheUserProjectionAllowlist() {
        View view =
                new View() {
                    public JsonObject block(int x, int y, int z) {
                        return json(
                                "{\"available\":true,\"block\":\"minecraft:stone\",\"state\":\"stone\",\"targetRef\":\"world:block\",\"targetTtlMs\":15000,\"nbt\":\"secret\"}");
                    }

                    public Iterator<JsonObject> entities() {
                        return java.util.List.of(
                                        json(
                                                "{\"type\":\"minecraft:pig\",\"x\":1,\"y\":64,\"z\":1,\"targetRef\":\"world:entity\",\"targetTtlMs\":15000,\"uuid\":\"secret\"}"))
                                .iterator();
                    }
                };
        JsonObject block =
                WorldClientModel.capture("block", json("{\"x\":1,\"y\":64,\"z\":0}"), view);
        assertEquals("world:block", block.get("targetRef").getAsString());
        assertFalse(block.has("nbt"));
        JsonObject scene = WorldClientModel.capture("scene", json("{\"radius\":0}"), view);
        assertEquals(
                "world:block",
                scene.getAsJsonArray("blocks")
                        .get(0)
                        .getAsJsonObject()
                        .get("targetRef")
                        .getAsString());
        JsonObject entities = WorldClientModel.capture("entities", new JsonObject(), view);
        assertEquals(
                "world:entity",
                entities.getAsJsonArray("entities")
                        .get(0)
                        .getAsJsonObject()
                        .get("targetRef")
                        .getAsString());
        assertFalse(entities.toString().contains("secret"));
    }

    @Test
    void absentWorldUnloadedBlockAndOutOfRangeNeverRequestFallback() {
        View view = new View();
        view.available = false;
        assertFalse(
                WorldClientModel.capture("scene", new JsonObject(), view)
                        .get("available")
                        .getAsBoolean());
        assertEquals(0, view.blocks.get());
        view.available = true;
        assertEquals(
                "outside_user_radius",
                WorldClientModel.capture("block", json("{\"x\":1000000,\"y\":64,\"z\":0}"), view)
                        .get("reason")
                        .getAsString());
        assertEquals(0, view.blocks.get());
        assertEquals(
                "chunk_not_loaded",
                WorldClientModel.capture("block", json("{\"x\":-1,\"y\":64,\"z\":0}"), view)
                        .get("reason")
                        .getAsString());
        assertEquals(1, view.blocks.get());
    }

    @Test
    void everyQueryPathEnforcesBudgetsAndExactCoordinatesBeforeTouchingClient() {
        View view = new View();
        for (String value :
                new String[] {
                    "{\"radius\":33}",
                    "{\"radius\":-1}",
                    "{\"radius\":\"16\"}",
                    "{\"limit\":129}",
                    "{\"limit\":0}",
                    "{\"uuid\":\"secret\"}"
                })
            assertThrows(
                    WorldClientModel.InvalidQuery.class,
                    () -> WorldClientModel.capture("entities", json(value), view));
        for (String value :
                new String[] {
                    "{\"radius\":9}",
                    "{\"radius\":1.5}",
                    "{\"blockLimit\":513}",
                    "{\"entityLimit\":129}"
                })
            assertThrows(
                    WorldClientModel.InvalidQuery.class,
                    () -> WorldClientModel.capture("scene", json(value), view));
        for (String value :
                new String[] {
                    "{}", "{\"x\":0.1,\"y\":64,\"z\":0}", "{\"x\":2147483648,\"y\":64,\"z\":0}"
                })
            assertThrows(
                    WorldClientModel.InvalidQuery.class,
                    () -> WorldClientModel.capture("block", json(value), view));
        assertEquals(0, view.blocks.get());
        assertEquals(0, view.entities.get());
    }

    @Test
    void sceneAndEntityScanAreBoundedEvenWhenCandidatesAreOutsideRadius() {
        View view = new View();
        JsonObject scene =
                WorldClientModel.capture(
                        "scene", json("{\"radius\":8,\"blockLimit\":3,\"entityLimit\":2}"), view);
        assertTrue(view.blocks.get() <= WorldClientModel.MAX_BLOCK_SCAN);
        assertTrue(scene.getAsJsonArray("blocks").size() <= 3);
        assertTrue(scene.get("blocksTruncated").getAsBoolean());
        assertTrue(scene.getAsJsonArray("entities").size() <= 2);
        assertTrue(scene.get("entitiesTruncated").getAsBoolean());
        View outside =
                new View() {
                    public Iterator<JsonObject> entities() {
                        return IntStream.range(0, 10000)
                                .mapToObj(
                                        i -> {
                                            entities.incrementAndGet();
                                            return json("{\"x\":10000,\"y\":64,\"z\":0}");
                                        })
                                .iterator();
                    }
                };
        JsonObject entities = WorldClientModel.capture("entities", new JsonObject(), outside);
        assertTrue(outside.entities.get() <= WorldClientModel.MAX_ENTITY_SCAN);
        assertTrue(entities.get("entitiesTruncated").getAsBoolean());
        assertTrue(entities.getAsJsonArray("entities").isEmpty());
    }

    @Test
    void cancellationStopsScanningAndLongClientTextIsExplicitlyTruncated() {
        View view =
                new View() {
                    public JsonObject block(int x, int y, int z) {
                        JsonObject value = super.block(x, y, z);
                        value.addProperty("state", "x".repeat(4000));
                        return value;
                    }
                };
        JsonObject result =
                WorldClientModel.capture("block", json("{\"x\":0,\"y\":64,\"z\":0}"), view);
        assertEquals(512, result.get("state").getAsString().length());
        assertTrue(result.get("textTruncated").getAsBoolean());
        Thread.currentThread().interrupt();
        try {
            assertThrows(
                    java.util.concurrent.CancellationException.class,
                    () -> WorldClientModel.capture("scene", new JsonObject(), view));
            assertEquals(1, view.blocks.get());
        } finally {
            Thread.interrupted();
        }
    }
}
