package io.github.recrivenvi.minecraftprotocol.probe.validation;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.properties.PropertyMap;
import io.github.recrivenvi.minecraftprotocol.probe.mixin.PlayerListUiAccessor;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.numbers.BlankFormat;
import net.minecraft.network.chat.numbers.FixedFormat;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;

/** Local client packet Arrange only: no remote players, accounts, skins or server-side agents. */
public final class UiPlayerListFixtureScreen extends Screen {
    private static UiPlayerListFixtureScreen active;
    private Screen previous;
    private final ClientPacketListener connection;
    private final UUID originalPlayerId;
    private final PlayerInfo originalPlayer;
    private final boolean originalPlayerListed;
    private final String fixtureId = UUID.randomUUID().toString();
    private final String objectiveName = "ui_tab_" + fixtureId.substring(0, 8);
    private final List<Entry> entries = new ArrayList<>();
    private final List<PlayerTeam> teams = new ArrayList<>();
    private final Objective originalObjective;
    private final Component originalHeader, originalFooter;
    private Component ownedHeader, ownedFooter;
    private Objective ownedObjective;
    private int sequence;
    private String mode = "uninstalled";
    private boolean installed, cleaned;

    private record Entry(
            UUID id,
            String profileName,
            String displayName,
            boolean listed,
            int latency,
            GameType gameMode,
            String team) {}

    public static boolean enabled() {
        return Boolean.getBoolean("minecraft.protocol.uiPlayerListFixture")
                && "conformance-vanilla_ui".equals(System.getProperty("validation.name"))
                && "client".equals(System.getProperty("validation.role"));
    }

    public static Screen open(Screen previous) {
        Minecraft client = Minecraft.getInstance();
        if (active != null && !active.cleaned && active.connection == client.getConnection()) {
            active.previous = previous;
            return active;
        }
        active = new UiPlayerListFixtureScreen(previous);
        return active;
    }

    private UiPlayerListFixtureScreen(Screen previous) {
        super(Component.literal("Player List (Local Packet Fixture)"));
        if (!enabled()) throw new IllegalStateException("Player-list fixture is not enabled");
        Minecraft client = Minecraft.getInstance();
        connection = client.getConnection();
        if (connection == null || client.level == null || client.player == null)
            throw new IllegalStateException("Player-list fixture needs its restored client world");
        if (connection.getOnlinePlayers().size() != 1)
            throw new IllegalStateException(
                    "Do not mix a local roster fixture with a real multiplayer roster");
        originalPlayerId = client.player.getUUID();
        originalPlayer = connection.getPlayerInfo(originalPlayerId);
        if (originalPlayer == null)
            throw new IllegalStateException("The local player roster entry is unavailable");
        originalPlayerListed = connection.getListedOnlinePlayers().contains(originalPlayer);
        this.previous = previous;
        originalObjective = client.level.getScoreboard().getDisplayObjective(DisplaySlot.LIST);
        var tab = (PlayerListUiAccessor) client.gui.getTabList();
        originalHeader = tab.minecraftProtocolProbe$header();
        originalFooter = tab.minecraftProtocolProbe$footer();
        report();
    }

    @Override
    protected void init() {
        if (minecraft.getConnection() != connection || minecraft.level == null)
            throw new IllegalStateException("The owned client connection changed");
        List<String> labels =
                List.of(
                        "Install Local Roster (Fixture)",
                        "Fixed Score (Fixture)",
                        "Blank Score (Fixture)",
                        "Heart Score (Fixture)",
                        "Heart Fallback (Fixture)",
                        "View Player List (Fixture)",
                        "Restore Local Roster (Fixture)");
        for (int index = 0; index < labels.size(); index++) {
            final int action = index;
            addRenderableWidget(
                    Button.builder(Component.literal(labels.get(index)), button -> action(action))
                            .bounds(width / 2 - 120, height / 2 - 84 + index * 24, 240, 20)
                            .build());
        }
    }

    private void action(int action) {
        if (action == 0) install();
        else if (action >= 1 && action <= 4)
            score(
                    switch (action) {
                        case 1 -> "fixed";
                        case 2 -> "blank";
                        case 3 -> "hearts";
                        default -> "fallback";
                    });
        else if (action == 5) {
            if (!installed) throw new IllegalStateException("Install the owned roster first");
            minecraft.setScreen(null);
        } else if (action == 6) restore();
    }

    private void install() {
        if (installed) throw new IllegalStateException("Do not replay roster installation");
        if (connection.getPlayerInfo(originalPlayerId) != originalPlayer
                || connection.getListedOnlinePlayers().contains(originalPlayer)
                        != originalPlayerListed)
            throw new IllegalStateException(
                    "Do not overwrite an externally changed local roster entry");
        var detachedBoard = new Scoreboard();
        for (int group = 0; group < 2; group++)
            teams.add(
                    detachedBoard.addPlayerTeam(
                            "ui_tab_" + group + "_" + fixtureId.substring(0, 8)));
        int[] latency = {-1, 0, 150, 300, 600, 1000};
        for (int index = 0; index < 83; index++) {
            String profile = String.format(java.util.Locale.ROOT, "ui_hidden_%03d", index);
            String display = String.format(java.util.Locale.ROOT, "Visible %03d", index);
            String team = teams.get(index % 2).getName();
            entries.add(
                    new Entry(
                            UUID.randomUUID(),
                            profile,
                            display,
                            index != 82,
                            latency[index % 6],
                            index % 9 == 0 ? GameType.SPECTATOR : GameType.SURVIVAL,
                            team));
            detachedBoard.addPlayerToTeam(profile, teams.get(index % 2));
        }
        for (PlayerTeam team : teams)
            connection.handleSetPlayerTeamPacket(
                    ClientboundSetPlayerTeamPacket.createAddOrModifyPacket(team, true));
        EnumSet<ClientboundPlayerInfoUpdatePacket.Action> actions =
                EnumSet.of(
                        ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_GAME_MODE,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LATENCY,
                        ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME);
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), connection.registryAccess());
        try {
            buffer.writeEnumSet(actions, ClientboundPlayerInfoUpdatePacket.Action.class);
            buffer.writeVarInt(entries.size());
            for (Entry entry : entries) {
                buffer.writeUUID(entry.id);
                buffer.writeUtf(entry.profileName, 16);
                ByteBufCodecs.GAME_PROFILE_PROPERTIES.encode(buffer, new PropertyMap());
                buffer.writeVarInt(entry.gameMode.getId());
                buffer.writeBoolean(entry.listed);
                buffer.writeVarInt(entry.latency);
                buffer.writeBoolean(true);
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(
                        buffer, Component.literal(entry.displayName));
            }
            var packet = ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buffer);
            if (buffer.isReadable())
                throw new IllegalStateException("Unexpected unread roster bytes");
            connection.handlePlayerInfoUpdate(packet);
        } finally {
            buffer.release();
        }
        setOriginalPlayerListed(false);
        ownedHeader = Component.literal("Local roster header");
        ownedFooter = Component.literal("Local roster footer");
        connection.handleTabListCustomisation(
                new ClientboundTabListPacket(ownedHeader, ownedFooter));
        installed = true;
        mode = "roster";
        sequence++;
        report();
    }

    private void score(String kind) {
        if (!installed || cleaned)
            throw new IllegalStateException("The owned roster is not active");
        var board = minecraft.level.getScoreboard();
        if (ownedObjective != null && board.getObjective(objectiveName) != ownedObjective)
            throw new IllegalStateException("The fixture objective was externally replaced");
        var renderType =
                kind.equals("hearts") || kind.equals("fallback")
                        ? ObjectiveCriteria.RenderType.HEARTS
                        : ObjectiveCriteria.RenderType.INTEGER;
        var format =
                kind.equals("blank")
                        ? BlankFormat.INSTANCE
                        : new FixedFormat(Component.literal("VISIBLE SCORE"));
        var detached =
                new Objective(
                        new Scoreboard(),
                        objectiveName,
                        ObjectiveCriteria.DUMMY,
                        Component.literal("Local fixture score"),
                        renderType,
                        false,
                        format);
        connection.handleAddObjective(
                new ClientboundSetObjectivePacket(detached, ownedObjective == null ? 0 : 2));
        ownedObjective = board.getObjective(objectiveName);
        connection.handleSetDisplayObjective(
                new ClientboundSetDisplayObjectivePacket(DisplaySlot.LIST, ownedObjective));
        for (Entry entry : entries)
            connection.handleSetScore(
                    new ClientboundSetScorePacket(
                            entry.profileName,
                            objectiveName,
                            kind.equals("fallback") ? 2000 : kind.equals("hearts") ? 20 : 8675309,
                            Optional.empty(),
                            Optional.empty()));
        mode = kind;
        sequence++;
        report();
    }

    private void restore() {
        if (!installed || cleaned)
            throw new IllegalStateException("No owned roster remains to restore");
        if (connection.getPlayerInfo(originalPlayerId) != originalPlayer
                || connection.getListedOnlinePlayers().contains(originalPlayer))
            throw new IllegalStateException(
                    "Do not overwrite an externally changed local roster entry");
        var board = minecraft.level.getScoreboard();
        if (ownedObjective != null) {
            if (board.getObjective(objectiveName) != ownedObjective
                    || board.getDisplayObjective(DisplaySlot.LIST) != ownedObjective)
                throw new IllegalStateException(
                        "Do not overwrite an externally changed list objective");
            connection.handleSetDisplayObjective(
                    new ClientboundSetDisplayObjectivePacket(DisplaySlot.LIST, originalObjective));
            connection.handleAddObjective(new ClientboundSetObjectivePacket(ownedObjective, 1));
        }
        var tab = (PlayerListUiAccessor) minecraft.gui.getTabList();
        if (tab.minecraftProtocolProbe$header() != ownedHeader
                || tab.minecraftProtocolProbe$footer() != ownedFooter)
            throw new IllegalStateException("Do not overwrite externally changed header/footer");
        connection.handleTabListCustomisation(
                new ClientboundTabListPacket(
                        originalHeader == null ? Component.empty() : originalHeader,
                        originalFooter == null ? Component.empty() : originalFooter));
        connection.handlePlayerInfoRemove(
                new ClientboundPlayerInfoRemovePacket(entries.stream().map(Entry::id).toList()));
        for (PlayerTeam team : teams)
            connection.handleSetPlayerTeamPacket(
                    ClientboundSetPlayerTeamPacket.createRemovePacket(team));
        for (Entry entry : entries)
            if (connection.getPlayerInfo(entry.id) != null)
                throw new IllegalStateException("Owned roster entry remained after cleanup");
        setOriginalPlayerListed(originalPlayerListed);
        if (connection.getListedOnlinePlayers().contains(originalPlayer) != originalPlayerListed)
            throw new IllegalStateException(
                    "The original local player listed state was not restored");
        cleaned = true;
        mode = "restored";
        sequence++;
        report();
    }

    private void setOriginalPlayerListed(boolean listed) {
        RegistryFriendlyByteBuf buffer =
                new RegistryFriendlyByteBuf(Unpooled.buffer(), connection.registryAccess());
        try {
            buffer.writeEnumSet(
                    EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED),
                    ClientboundPlayerInfoUpdatePacket.Action.class);
            buffer.writeVarInt(1);
            buffer.writeUUID(originalPlayerId);
            buffer.writeBoolean(listed);
            var packet = ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buffer);
            if (buffer.isReadable())
                throw new IllegalStateException("Unexpected unread listed-state bytes");
            connection.handlePlayerInfoUpdate(packet);
        } finally {
            buffer.release();
        }
    }

    private void report() {
        var value = new JsonObject();
        value.addProperty("probeOnly", true);
        value.addProperty("arrangeAuthority", "DIAGNOSTIC_local_client_packet_fixture");
        value.addProperty("fixtureId", fixtureId);
        value.addProperty("target", System.getProperty("validation.target"));
        value.addProperty("processId", ProcessHandle.current().pid());
        value.addProperty("sequence", sequence);
        value.addProperty("mode", mode);
        value.addProperty("installed", installed);
        value.addProperty("cleaned", cleaned);
        value.addProperty("actualMultiplayerNetworkValidated", false);
        value.addProperty("serverPlayersCreated", 0);
        value.addProperty("nativeNumberFormatsSupported", true);
        value.addProperty("nativeListOrderSupported", false);
        value.addProperty("originalPlayerListed", originalPlayerListed);
        value.addProperty(
                "localPlayerTemporarilyUnlisted", installed && !cleaned && originalPlayerListed);
        value.addProperty(
                "localPlayerListRestored",
                cleaned
                        && connection.getPlayerInfo(originalPlayerId) == originalPlayer
                        && connection.getListedOnlinePlayers().contains(originalPlayer)
                                == originalPlayerListed);
        value.addProperty("nativePingFormat", "resource_sprite");
        value.addProperty("syntheticListed", entries.stream().filter(Entry::listed).count());
        value.addProperty(
                "syntheticUnlisted", entries.stream().filter(entry -> !entry.listed).count());
        value.addProperty("originalHeaderWasNull", originalHeader == null);
        value.addProperty("originalFooterWasNull", originalFooter == null);
        value.addProperty("nullHeaderRestoredAsEmpty", cleaned && originalHeader == null);
        value.addProperty("nullFooterRestoredAsEmpty", cleaned && originalFooter == null);
        JsonArray expected = new JsonArray();
        entries.stream()
                .filter(Entry::listed)
                .sorted(
                        Comparator.comparingInt(
                                        (Entry entry) ->
                                                entry.gameMode == GameType.SPECTATOR ? 1 : 0)
                                .thenComparing(Entry::team)
                                .thenComparing(Entry::profileName, String.CASE_INSENSITIVE_ORDER))
                .limit(80)
                .forEach(
                        entry -> {
                            JsonObject row = new JsonObject();
                            row.addProperty("displayName", entry.displayName);
                            row.addProperty("scoreVisible", entry.gameMode != GameType.SPECTATOR);
                            row.addProperty(
                                    "pingSprite",
                                    "minecraft:icon/ping_"
                                            + (entry.latency < 0
                                                    ? "unknown"
                                                    : entry.latency < 150
                                                            ? "5"
                                                            : entry.latency < 300
                                                                    ? "4"
                                                                    : entry.latency < 600
                                                                            ? "3"
                                                                            : entry.latency < 1000
                                                                                    ? "2"
                                                                                    : "1"));
                            expected.add(row);
                        });
        value.add("expectedVisibleRows", expected);
        try {
            Path result = Path.of(System.getProperty("validation.result"));
            Files.createDirectories(result);
            Files.writeString(
                    result.resolve(
                            System.getProperty("validation.target") + "-player-list-probe.json"),
                    value + "\n",
                    StandardCharsets.UTF_8);
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Player-list fixture facts were not recorded", failure);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(previous);
    }
}
