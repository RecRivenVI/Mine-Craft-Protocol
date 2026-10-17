package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ChatComponent.class)
public interface CommandFeedbackChatAccessor {
    @Accessor("allMessages")
    List<GuiMessage> minecraftProtocolProbe$messages();

    @Accessor("visibleMessageFilter")
    Predicate<GuiMessage> minecraftProtocolProbe$visibleFilter();
}
