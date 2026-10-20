package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import java.util.function.Consumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractStringWidget;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractStringWidget.class)
public interface StringWidgetUiAccessor {
    @Accessor("componentClickHandler")
    Consumer<Style> minecraftProtocolProbe$clickHandler();

    @Accessor("font")
    Font minecraftProtocolProbe$font();
}
