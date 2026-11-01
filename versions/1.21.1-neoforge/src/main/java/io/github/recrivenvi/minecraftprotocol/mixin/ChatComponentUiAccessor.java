package io.github.recrivenvi.minecraftprotocol.mixin;

import java.util.List;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;

@Mixin(ChatComponent.class)
public interface ChatComponentUiAccessor {
    @Accessor("trimmedMessages")
    List<GuiMessage.Line> mineCraftProtocol$lines();

    @Accessor("chatScrollbarPos")
    int mineCraftProtocol$scroll();

    @Invoker("getLineHeight")
    int mineCraftProtocol$lineHeight();
}
