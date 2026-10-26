package io.github.recrivenvi.minecraftprotocol.core;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

class DebugItemRequestTest {
    @Test
    void unresolvedNativeLootTableIsReadOnlyBeforeAnySlotAccess() {
        var unresolved =
                JsonParser.parseString(
                                "{\"LootTable\":\"minecraft:chests/simple_dungeon\",\"LootTableSeed\":123}")
                        .getAsJsonObject();
        assertEquals(
                "DEBUG_CONTAINER_LOOT_UNRESOLVED",
                assertThrows(
                                ProtocolState.ProtocolException.class,
                                () -> DebugItemRequest.requireResolvedContainer(unresolved))
                        .code());
        assertTrue(unresolved.has("LootTable"));
        assertDoesNotThrow(
                () ->
                        DebugItemRequest.requireResolvedContainer(
                                JsonParser.parseString("{\"Items\":[]}").getAsJsonObject()));
    }

    @Test
    void onlyClosedLiteralItemDataAndExactCountsAreAccepted() {
        var item =
                DebugItemRequest.parse(
                        JsonParser.parseString(
                                        "{\"count\":1,\"itemId\":\"minecraft:iron_pickaxe\",\"itemData\":{\"customName\":\"Test\",\"damage\":2}}")
                                .getAsJsonObject());
        assertEquals(1, item.count());
        assertEquals("Test", item.customName());
        assertEquals(2, item.damage());
        assertEquals(
                0,
                DebugItemRequest.parse(JsonParser.parseString("{\"count\":0}").getAsJsonObject())
                        .count());
        for (String body :
                new String[] {
                    "{}",
                    "{\"count\":0.5}",
                    "{\"count\":\"0\"}",
                    "{\"count\":-1}",
                    "{\"count\":65}",
                    "{\"count\":1}",
                    "{\"count\":0,\"itemData\":{}}",
                    "{\"count\":1,\"itemId\":\"minecraft:stone\",\"itemData\":{\"rawNbt\":\"{}\"}}",
                    "{\"count\":1,\"itemId\":\"minecraft:stone\",\"itemData\":{\"damage\":0.5}}"
                })
            assertThrows(
                    ProtocolState.ProtocolException.class,
                    () -> DebugItemRequest.parse(JsonParser.parseString(body).getAsJsonObject()),
                    body);
    }
}
