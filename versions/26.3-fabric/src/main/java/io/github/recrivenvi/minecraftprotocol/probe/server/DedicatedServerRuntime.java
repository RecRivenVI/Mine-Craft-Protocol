package io.github.recrivenvi.minecraftprotocol.probe.server;

import com.google.gson.JsonObject;
import io.github.recrivenvi.minecraftprotocol.core.ProbeTransport;
import io.github.recrivenvi.minecraftprotocol.core.ServerConsole;
import io.github.recrivenvi.minecraftprotocol.core.ServerConsoleService;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.DedicatedWorldDebug;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.commands.CommandSource;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;

public final class DedicatedServerRuntime {
    private static ServerConsoleService service;
    private static ProbeTransport transport;

    private DedicatedServerRuntime() {}

    public static void initialize() {
        ServerLifecycleEvents.SERVER_STARTED.register(DedicatedServerRuntime::started);
        ServerLifecycleEvents.SERVER_STOPPING.register(DedicatedServerRuntime::stopping);
    }

    private static void started(MinecraftServer minecraftServer) {
        if (!(minecraftServer instanceof DedicatedServer server)) return;
        var directory = server.getServerDirectory();
        service =
                new ServerConsoleService(
                        directory,
                        "26.3-fabric",
                        new ServerConsole.Host() {
                            @Override
                            public void schedule(Runnable action) {
                                server.execute(action);
                            }

                            @Override
                            public java.util.concurrent.CompletableFuture<JsonObject> complete(
                                    String input, int cursor) {
                                var dispatcher = server.getCommands().getDispatcher();
                                return dispatcher
                                        .getCompletionSuggestions(
                                                dispatcher.parse(
                                                        input, server.createCommandSourceStack()),
                                                cursor)
                                        .thenApply(
                                                suggestions -> {
                                                    JsonObject value = new JsonObject();
                                                    com.google.gson.JsonArray entries =
                                                            new com.google.gson.JsonArray();
                                                    for (var suggestion : suggestions.getList()) {
                                                        if (entries.size() >= 64) break;
                                                        JsonObject entry = new JsonObject();
                                                        String text = suggestion.getText();
                                                        entry.addProperty(
                                                                "text",
                                                                text.substring(
                                                                        0,
                                                                        Math.min(
                                                                                text.length(),
                                                                                2048)));
                                                        entry.addProperty(
                                                                "start",
                                                                suggestion.getRange().getStart());
                                                        entry.addProperty(
                                                                "end",
                                                                suggestion.getRange().getEnd());
                                                        entries.add(entry);
                                                    }
                                                    value.add("suggestions", entries);
                                                    value.addProperty(
                                                            "truncated",
                                                            suggestions.getList().size()
                                                                    > entries.size());
                                                    return value;
                                                });
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
                                                                server.sendSystemMessage(message);
                                                            }

                                                            @Override
                                                            public boolean acceptsSuccess() {
                                                                return server.acceptsSuccess();
                                                            }

                                                            @Override
                                                            public boolean acceptsFailure() {
                                                                return server.acceptsFailure();
                                                            }

                                                            @Override
                                                            public boolean shouldInformAdmins() {
                                                                return server.shouldInformAdmins();
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
                        new DedicatedWorldDebug(server, "26.3-fabric"));
        try {
            transport = ProbeTransport.forInstance(service, directory, "26.3-fabric");
            io.github.recrivenvi.minecraftprotocol.core.ServerPeerReceiver.attach(
                    server, transport);
            transport.startAsync();
        } catch (RuntimeException failure) {
            if (transport != null) {
                io.github.recrivenvi.minecraftprotocol.core.ServerPeerReceiver.detach(
                        server, transport);
                transport.close();
                transport = null;
            }
            service.close();
            service = null;
            throw failure;
        }
    }

    private static void stopping(MinecraftServer minecraftServer) {
        if (!(minecraftServer instanceof DedicatedServer)) return;
        if (transport != null) {
            io.github.recrivenvi.minecraftprotocol.core.ServerPeerReceiver.detach(
                    minecraftServer, transport);
            transport.close();
            transport = null;
        }
        if (service != null) {
            service.close();
            service = null;
        }
    }
}
