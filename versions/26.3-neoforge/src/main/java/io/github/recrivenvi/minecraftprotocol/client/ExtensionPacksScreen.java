package io.github.recrivenvi.minecraftprotocol.client;

import io.github.recrivenvi.minecraftprotocol.core.extension.*;
import io.github.recrivenvi.minecraftprotocol.loader.LoaderExtensionBridge;
import java.util.*;
import java.util.stream.Stream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.packs.PackSelectionScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.repository.*;
import net.minecraft.world.flag.FeatureFlagSet;

/**
 * The native resource-pack surface backed only by extension selection, never game resource packs.
 */
public final class ExtensionPacksScreen extends PackSelectionScreen {
    private final ExtensionPackSelection selection;

    public ExtensionPacksScreen(Screen parent) {
        this(
                parent,
                new ExtensionPackSelection(
                        Minecraft.getInstance().gameDirectory.toPath(),
                        LoaderExtensionBridge.snapshotEnvironment()));
    }

    private ExtensionPacksScreen(Screen parent, ExtensionPackSelection selection) {
        super(
                repository(selection),
                ignored -> Minecraft.getInstance().gui.setScreen(parent),
                selection.directory(),
                Component.translatable(
                        io.github.recrivenvi.minecraftprotocol.ProtocolIdentity.MOD_ID
                                + ".extensionpacks.title"));
        this.selection = selection;
    }

    private static PackRepository repository(ExtensionPackSelection selection) {
        PackRepository repository =
                new PackRepository(
                        output -> {
                            for (var entry : selection.entries()) {
                                var location =
                                        new PackLocationInfo(
                                                entry.id(),
                                                Component.literal(entry.title()),
                                                PackSource.DEFAULT,
                                                Optional.empty());
                                var metadata =
                                        new Pack.Metadata(
                                                description(entry)
                                                        .copy()
                                                        .append("\n")
                                                        .append(detail(entry)),
                                                PackCompatibility.COMPATIBLE,
                                                FeatureFlagSet.of(),
                                                List.of());
                                var supplier =
                                        new Pack.ResourcesSupplier() {
                                            public PackMetadataResources openMetadata(
                                                    PackLocationInfo info) {
                                                return new AbstractPackMetadataResources(info) {
                                                    public net.minecraft.server.packs.resources
                                                                            .IoSupplier<
                                                                    java.io.InputStream>
                                                            getRootResource(String... paths) {
                                                        if (paths.length == 1
                                                                && paths[0].equals("pack.png")
                                                                && entry.source()
                                                                        .manifest()
                                                                        .files()
                                                                        .containsKey("pack.png"))
                                                            return () ->
                                                                    new java.io
                                                                            .ByteArrayInputStream(
                                                                            entry.source()
                                                                                    .read(
                                                                                            "pack.png",
                                                                                            1024
                                                                                                    * 1024));
                                                        return null;
                                                    }

                                                    public void close() {}
                                                };
                                            }

                                            public Stream<PackResources> openResources(
                                                    PackLocationInfo info, Pack.Metadata data) {
                                                return Stream.empty();
                                            }
                                        };
                                output.accept(
                                        new Pack(
                                                location,
                                                supplier,
                                                metadata,
                                                new PackSelectionConfig(
                                                        false, Pack.Position.TOP, false)));
                            }
                        });
        repository.reload();
        repository.setSelected(
                selection.entries().stream()
                        .filter(ExtensionPackSelection.Entry::enabled)
                        .map(ExtensionPackSelection.Entry::id)
                        .toList());
        return repository;
    }

    public ExtensionPackSelection.Entry entry(String id) {
        return selection.current(id);
    }

    public static Component description(ExtensionPackSelection.Entry entry) {
        return Component.translatableWithFallback(
                io.github.recrivenvi.minecraftprotocol.ProtocolIdentity.MOD_ID
                        + ".extensionpack."
                        + entry.id()
                        + ".description",
                entry.description());
    }

    public static Component detail(ExtensionPackSelection.Entry entry) {
        String state =
                !entry.available()
                        ? "incompatible"
                        : !entry.approved()
                                ? "approval_required"
                                : entry.pending() ? "pending_restart" : "approved";
        return Component.translatable(
                io.github.recrivenvi.minecraftprotocol.ProtocolIdentity.MOD_ID
                        + ".extensionpacks."
                        + state);
    }

    public boolean toggle(String id, boolean enabled) {
        try {
            selection.select(
                    id,
                    enabled,
                    io.github.recrivenvi.minecraftprotocol.safety.AgentInputContext.isNativeTask());
            return true;
        } catch (ExtensionFailure denied) {
            return false;
        }
    }

    @Override
    public void onFilesDrop(List<java.nio.file.Path> paths) {
        // Executable packages are installed explicitly; native drag-and-drop is not an approval
        // path.
    }
}
