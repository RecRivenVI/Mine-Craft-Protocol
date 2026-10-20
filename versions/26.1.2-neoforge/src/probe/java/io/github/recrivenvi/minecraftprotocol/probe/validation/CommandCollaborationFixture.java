package io.github.recrivenvi.minecraftprotocol.probe.validation;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.server.players.NameAndId;

/** Opt-in authority Arrange on the real server source; never writes the world or operator list. */
public final class CommandCollaborationFixture {
    private static final String PREFIX = "help Mine-Craft-Protocol_command_collaboration_";
    private static Context current;

    private CommandCollaborationFixture() {}

    public static boolean enabled() {
        return Boolean.getBoolean("minecraft.protocol.commandCollaborationFixture")
                && "conformance-world_read".equals(System.getProperty("validation.name"))
                && "client".equals(System.getProperty("validation.role"));
    }

    public static LevelBasedPermissionSet permissions(
            MinecraftServer candidate, NameAndId profile) {
        if (!(candidate instanceof IntegratedServer server)) return null;
        Context context = current;
        return enabled()
                        && context != null
                        && context.server.get() == server
                        && server.isSameThread()
                        && context.playerId.equals(profile.id())
                        && context.revoked
                ? LevelBasedPermissionSet.ALL
                : null;
    }

    public static void change(boolean revoked, Button button) {
        if (!enabled()) return;
        Minecraft client = Minecraft.getInstance();
        IntegratedServer server = client.getSingleplayerServer();
        if (server == null || client.player == null)
            throw new IllegalStateException("The command fixture requires its integrated player");
        UUID playerId = client.player.getUUID();
        button.active = false;
        server.execute(
                () -> {
                    ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                    Context context = context(server, player);
                    context.revoked = revoked;
                    server.getPlayerList().sendPlayerPermissionLevel(player);
                    context.treeSends++;
                    boolean actual =
                            player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
                    if (actual != (!revoked && context.originalGamemaster))
                        throw new IllegalStateException(
                                "Server permission fixture was not applied");
                    client.execute(
                            () -> {
                                button.setMessage(
                                        Component.literal(
                                                revoked
                                                        ? "Commands Revoked (Fixture)"
                                                        : "Commands Restored (Fixture)"));
                                button.active = true;
                            });
                });
    }

    public static void command(CommandSourceStack source, String command) {
        if (!enabled()
                || !(source.getServer() instanceof IntegratedServer server)
                || !server.isSameThread()
                || source.getPlayer() == null) return;
        Context context = current;
        boolean marker = command.startsWith(PREFIX);
        if (context == null || context.server.get() != server) {
            if (!marker) return;
            context = context(server, source.getPlayer());
        }
        if (!context.playerId.equals(source.getPlayer().getUUID())) return;
        context.nativeCommands++;
        if (!marker) return;
        String attempt = command.substring(PREFIX.length());
        if (!attempt.matches("[0-9a-f]{32}")) return;
        JsonObject result = new JsonObject();
        result.addProperty("attemptId", attempt);
        result.addProperty("probeOnly", true);
        result.addProperty("permissionArrange", "probe_server_profile_permissions_override");
        result.addProperty("permissionRevoked", context.revoked);
        result.addProperty("originalGamemaster", context.originalGamemaster);
        result.addProperty(
                "gamemasterAllowed",
                source.getPlayer().permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
        result.addProperty("nativePlayerCommands", context.nativeCommands);
        result.addProperty("nativePermissionTreeSends", context.treeSends);
        result.addProperty("operatorListChanged", false);
        result.addProperty("worldMetadataChanged", false);
        try {
            Path output = Path.of(System.getProperty("validation.result"));
            Files.createDirectories(output);
            Files.writeString(
                    output.resolve(
                            System.getProperty("validation.target")
                                    + "-command-collaboration-probe.json"),
                    result + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static Context context(IntegratedServer server, ServerPlayer player) {
        if (player == null
                || !server.isSameThread()
                || !server.isSingleplayer()
                || server.getPlayerList().getPlayerCount() != 1
                || !server.getWorldData().getLevelName().equals("World-Read-Alpha"))
            throw new IllegalStateException(
                    "Command authority Arrange requires the fixed solo copy");
        Context existing = current;
        if (existing != null
                && existing.server.get() == server
                && existing.playerId.equals(player.getUUID())) return existing;
        boolean original = player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
        if (!original)
            throw new IllegalStateException("The fixture must initially have command authority");
        current = new Context(server, player.getUUID(), original);
        return current;
    }

    private static final class Context {
        final WeakReference<IntegratedServer> server;
        final UUID playerId;
        final boolean originalGamemaster;
        boolean revoked;
        int nativeCommands;
        int treeSends;

        Context(IntegratedServer server, UUID playerId, boolean originalGamemaster) {
            this.server = new WeakReference<>(server);
            this.playerId = playerId;
            this.originalGamemaster = originalGamemaster;
        }
    }
}
