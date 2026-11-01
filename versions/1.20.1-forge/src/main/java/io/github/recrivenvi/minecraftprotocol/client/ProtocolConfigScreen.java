package io.github.recrivenvi.minecraftprotocol.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public final class ProtocolConfigScreen extends Screen {
    private final Screen parent;

    public ProtocolConfigScreen(Screen parent) {
        super(
                Component.literal(
                        io.github.recrivenvi.minecraftprotocol.ProtocolIdentity.DISPLAY_NAME));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(
                Button.builder(
                                Component.translatable(
                                        io.github.recrivenvi.minecraftprotocol.ProtocolIdentity
                                                        .MOD_ID
                                                + ".options.extensionpacks"),
                                button -> minecraft.setScreen(new ExtensionPacksScreen(this)))
                        .bounds(width / 2 - 100, height / 2 - 24, 200, 20)
                        .build());
        addRenderableWidget(
                Button.builder(Component.translatable("gui.done"), button -> onClose())
                        .bounds(width / 2 - 100, height / 2 + 4, 200, 20)
                        .build());
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
