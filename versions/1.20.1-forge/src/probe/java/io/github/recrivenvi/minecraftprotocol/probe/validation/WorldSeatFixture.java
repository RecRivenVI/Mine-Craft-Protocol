package io.github.recrivenvi.minecraftprotocol.probe.validation;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.GameType;

/** Native two-player Arrange only; no product player, input or server capability is added. */
public final class WorldSeatFixture {
    private static final String TARGET = "1.20.1-forge";

    private WorldSeatFixture() {}

    public static boolean enabled() {
        return Boolean.getBoolean("minecraft.protocol.worldSeatFixture")
                && "conformance-world_read".equals(System.getProperty("validation.name"))
                && "client".equals(System.getProperty("validation.role"))
                && TARGET.equals(System.getProperty("validation.target"));
    }

    public static void open(Button button) {
        if (!enabled()) return;
        Minecraft client = Minecraft.getInstance();
        var server = client.getSingleplayerServer();
        if (!client.isSameThread() || server == null || client.player == null)
            throw new IllegalStateException("Seat fixture requires its native integrated world");
        Path root = client.gameDirectory.toPath().toAbsolutePath().normalize();
        try {
            if (!root.endsWith(
                            Path.of(
                                    "validations",
                                    "conformance-world_read",
                                    "instance",
                                    TARGET,
                                    "client"))
                    || !root.toRealPath().equals(root))
                throw new IllegalStateException("Seat fixture outside its owned validation copy");
            JsonObject owner =
                    JsonParser.parseString(
                                    Files.readString(
                                            root.resolve("world-read-owner.json"),
                                            StandardCharsets.UTF_8))
                            .getAsJsonObject();
            if (!TARGET.equals(owner.get("target").getAsString())
                    || !"world-read-classification".equals(owner.get("owner").getAsString()))
                throw new IllegalStateException("Seat fixture ownership mismatch");
            if (server.isPublished())
                throw new IllegalStateException("Seat comparison must own the LAN listener");
            server.submit(() -> server.setUsesAuthentication(false)).join();
            int port = HttpUtil.getAvailablePort();
            if (!server.publishServer(GameType.SURVIVAL, false, port))
                throw new IllegalStateException("Native LAN listener could not open");
            JsonObject report = new JsonObject();
            report.addProperty("target", TARGET);
            report.addProperty("port", server.getPort());
            report.addProperty("source", "native_integrated_lan_diagnostic_fixture");
            report.addProperty("authentication", server.usesAuthentication());
            report.addProperty("productDeploymentClaimed", false);
            Files.writeString(
                    root.resolve("world-seat-fixture.json"), report + "\n", StandardCharsets.UTF_8);
            button.setMessage(Component.literal("Native Seat Comparison Ready (Fixture)"));
            button.active = false;
        } catch (Exception failure) {
            throw new IllegalStateException("Native seat fixture failed", failure);
        }
    }
}
