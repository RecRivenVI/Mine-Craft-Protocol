package io.github.recrivenvi.minecraftprotocol.client;

/** Keeps client factory descriptors out of the common mod constructor's class linkage. */
public final class ClientConfigRegistration {
    private ClientConfigRegistration() {}

    public static void register(net.neoforged.fml.ModContainer container) {
        container.registerExtensionPoint(
                net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (ignored, parent) ->
                        new io.github.recrivenvi.minecraftprotocol.client.ProtocolConfigScreen(
                                parent));
    }
}
