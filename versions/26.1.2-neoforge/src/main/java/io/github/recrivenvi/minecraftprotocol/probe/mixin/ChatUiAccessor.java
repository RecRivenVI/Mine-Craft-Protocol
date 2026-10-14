package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatScreen.class)
public interface ChatUiAccessor {
    @Accessor("commandSuggestions")
    CommandSuggestions minecraftProtocolProbe$suggestions();

    @Accessor("displayMode")
    ChatComponent.DisplayMode minecraftProtocolProbe$displayMode();
}
