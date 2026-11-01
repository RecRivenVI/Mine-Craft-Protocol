package io.github.recrivenvi.minecraftprotocol.client;

public final class ProtocolModMenu implements com.terraformersmc.modmenu.api.ModMenuApi {
    @Override
    public com.terraformersmc.modmenu.api.ConfigScreenFactory<?> getModConfigScreenFactory() {
        return ProtocolConfigScreen::new;
    }
}
