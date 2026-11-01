package io.github.recrivenvi.minecraftprotocol.client;

/** Keeps client factory descriptors out of the common mod constructor's class linkage. */
public final class ClientConfigRegistration {
    private ClientConfigRegistration() {}

    public static void register() {
        net.minecraftforge.fml.ModLoadingContext.get()
                .registerExtensionPoint(
                        net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
                        () ->
                                new net.minecraftforge.client.ConfigScreenHandler
                                        .ConfigScreenFactory(
                                        (minecraft, parent) ->
                                                new io.github.recrivenvi.minecraftprotocol.client
                                                        .ProtocolConfigScreen(parent)));
    }
}
