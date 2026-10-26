package io.github.recrivenvi.minecraftprotocol.probe.validation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;

/** Explicit loaded-destination Arrange inside the immutable world validation copy only. */
public final class WorldTransferFixture {
    private static final String TARGET = "1.21.1-neoforge";
    private static final TicketType<Long> PIN =
            TicketType.create("minecraft_protocol_transfer_fixture", Long::compare, 6001);
    private static final List<Pin> pins = new ArrayList<>();

    private record Pin(String dimension, int x, int z) {}

    private WorldTransferFixture() {}

    public static boolean enabled() {
        return Boolean.getBoolean("minecraft.protocol.worldTransferFixture")
                && "conformance-world_read".equals(System.getProperty("validation.name"))
                && "client".equals(System.getProperty("validation.role"))
                && TARGET.equals(System.getProperty("validation.target"));
    }

    public static void change(boolean prepare, Button button) {
        if (!enabled()) return;
        Minecraft client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        if (server == null || client.player == null)
            throw new IllegalStateException("Integrated player required");
        Path root = client.gameDirectory.toPath().toAbsolutePath().normalize();
        try {
            if (!root.endsWith(
                            Path.of(
                                    "validations",
                                    "conformance-world_read",
                                    "instance",
                                    TARGET,
                                    "client"))
                    || !root.toRealPath().equals(root)
                    || !server.getWorldPath(LevelResource.ROOT)
                            .toRealPath()
                            .startsWith(root.resolve("saves")))
                throw new IllegalStateException("Transfer fixture is outside its owned copy");
            var owner =
                    JsonParser.parseString(
                                    Files.readString(
                                            root.resolve("world-read-owner.json"),
                                            StandardCharsets.UTF_8))
                            .getAsJsonObject();
            if (!TARGET.equals(owner.get("target").getAsString())
                    || !"world-read-classification".equals(owner.get("owner").getAsString()))
                throw new IllegalStateException("Transfer fixture ownership mismatch");
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
        UUID playerId = client.player.getUUID();
        button.active = false;
        server.execute(
                () -> {
                    JsonObject report = new JsonObject();
                    report.addProperty("target", TARGET);
                    report.addProperty("authority", "FIXTURE");
                    try {
                        if (server.getPlayerList().getPlayerCount() != 1
                                || server.getPlayerList().getPlayer(playerId) == null
                                || !server.getWorldData().getLevelName().equals("World-Read-Alpha"))
                            throw new IllegalStateException(
                                    "Transfer fixture requires the fixed solo world");
                        if (prepare) {
                            if (!pins.isEmpty())
                                throw new IllegalStateException(
                                        "Fixture pins must be transient and uniquely owned");
                            var player = server.getPlayerList().getPlayer(playerId);
                            pin(
                                    player.serverLevel(),
                                    player.chunkPosition().x,
                                    player.chunkPosition().z);
                            pin(server.getLevel(Level.NETHER), 0, 0);
                        } else {
                            for (Pin pin : pins) {
                                var level =
                                        server.getLevel(
                                                net.minecraft.resources.ResourceKey.create(
                                                        net.minecraft.core.registries.Registries
                                                                .DIMENSION,
                                                        net.minecraft.resources.ResourceLocation
                                                                .parse(pin.dimension())));
                                level.getChunkSource()
                                        .removeRegionTicket(
                                                PIN, new ChunkPos(pin.x(), pin.z()), 0, 1L);
                            }
                            pins.clear();
                        }
                        report.addProperty("dimension", "minecraft:the_nether");
                        report.addProperty("x", .5);
                        report.addProperty("y", 160.125);
                        report.addProperty("z", .5);
                        report.addProperty("loadedChunks", prepare ? 18 : 0);
                        report.addProperty("transientPins", pins.size());
                        report.addProperty("persistentTickets", 0);
                        report.addProperty("vanillaForcedTicketsCreated", 0);
                        report.addProperty("status", prepare ? "prepared" : "cleared");
                    } catch (Throwable failure) {
                        report.addProperty("status", "failed");
                        report.addProperty("error", failure.toString());
                    }
                    try {
                        Files.writeString(
                                root.resolve("world-transfer-fixture.json"),
                                report.toString() + "\n",
                                StandardCharsets.UTF_8);
                    } catch (java.io.IOException error) {
                        throw new IllegalStateException(error);
                    }
                    client.execute(
                            () -> {
                                button.active = true;
                                button.setMessage(
                                        Component.literal(
                                                report.has("error")
                                                        ? "Transfer Fixture Failed"
                                                        : prepare
                                                                ? "Transfer Destination Ready (Fixture)"
                                                                : "Transfer Pins Cleared (Fixture)"));
                            });
                });
    }

    private static void pin(ServerLevel level, int cx, int cz) {
        for (int x = cx - 1; x <= cx + 1; x++)
            for (int z = cz - 1; z <= cz + 1; z++) {
                level.getChunk(x, z);
                level.getChunkSource().addRegionTicket(PIN, new ChunkPos(x, z), 0, 1L);
                pins.add(new Pin(level.dimension().location().toString(), x, z));
            }
    }
}
