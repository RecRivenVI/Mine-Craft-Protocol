package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;

@Mixin(ChatComponent.class)
public interface ChatComponentUiAccessor {
    @Accessor("trimmedMessages")
    List<GuiMessage.Line> minecraftProtocolProbe$lines();

    @Accessor("chatScrollbarPos")
    int minecraftProtocolProbe$scroll();

    @Invoker("getLineHeight")
    int minecraftProtocolProbe$lineHeight();
}
