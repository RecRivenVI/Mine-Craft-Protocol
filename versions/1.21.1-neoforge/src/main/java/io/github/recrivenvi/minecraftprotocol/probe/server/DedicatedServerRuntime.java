package io.github.recrivenvi.minecraftprotocol.probe.server;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.core.ProbeTransport;
import io.github.recrivenvi.minecraftprotocol.core.ServerConsole;
import io.github.recrivenvi.minecraftprotocol.core.ServerConsoleService;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.dedicated.DedicatedServer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

public final class DedicatedServerRuntime {
    private static ServerConsoleService service;
    private static ProbeTransport transport;

    private DedicatedServerRuntime() {}

    public static void initialize() {
        NeoForge.EVENT_BUS.addListener(DedicatedServerRuntime::started);
        NeoForge.EVENT_BUS.addListener(DedicatedServerRuntime::stopping);
    }

    private static void started(ServerStartedEvent event) {
        if (!(event.getServer() instanceof DedicatedServer server)) return;
        var directory = server.getServerDirectory();
        service =
                new ServerConsoleService(
                        directory,
                        "1.21.1-neoforge",
                        new ServerConsole.Host() {
                            @Override
                            public void schedule(Runnable action) {
                                server.execute(action);
                            }

                            @Override
                            public void dispatch(
                                    String command,
                                    java.util.function.Consumer<String> feedback,
                                    java.util.function.Consumer<JsonObject> result) {
                                var base = server.createCommandSourceStack();
                                var source =
                                        base.withSource(
                                                        new CommandSource() {
                                                            @Override
                                                            public void sendSystemMessage(
                                                                    Component message) {
                                                                feedback.accept(
                                                                        message.getString());
                                                                base.source.sendSystemMessage(
                                                                        message);
                                                            }

                                                            @Override
                                                            public boolean acceptsSuccess() {
                                                                return base.source.acceptsSuccess();
                                                            }

                                                            @Override
                                                            public boolean acceptsFailure() {
                                                                return base.source.acceptsFailure();
                                                            }

                                                            @Override
                                                            public boolean shouldInformAdmins() {
                                                                return base.source
                                                                        .shouldInformAdmins();
                                                            }
                                                        })
                                                .withCallback(
                                                        (success, value) -> {
                                                            JsonObject nativeResult =
                                                                    new JsonObject();
                                                            nativeResult.addProperty(
                                                                    "success", success);
                                                            nativeResult.addProperty(
                                                                    "value", value);
                                                            result.accept(nativeResult);
                                                        });
                                server.getCommands().performPrefixedCommand(source, command);
                            }
                        },
                        new DedicatedWorldDebug(server));
        try {
            transport = ProbeTransport.forInstance(service, directory, "1.21.1-neoforge");
            transport.startAsync();
        } catch (RuntimeException failure) {
            service.close();
            service = null;
            throw failure;
        }
    }

    private static void stopping(ServerStoppingEvent event) {
        if (!(event.getServer() instanceof DedicatedServer)) return;
        if (transport != null) {
            transport.close();
            transport = null;
        }
        if (service != null) {
            service.close();
            service = null;
        }
    }
}
