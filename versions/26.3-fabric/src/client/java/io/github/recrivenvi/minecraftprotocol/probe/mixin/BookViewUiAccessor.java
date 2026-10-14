package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.screens.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BookViewScreen.class)
public interface BookViewUiAccessor {
    @Invoker("visitText")
    void minecraftProtocolProbe$visitText(ActiveTextCollector collector, boolean clickableOnly);

    @Accessor("currentPage")
    int minecraftProtocolProbe$pageIndex();

    @Accessor("bookAccess")
    BookViewScreen.BookAccess minecraftProtocolProbe$book();

    @Accessor("forwardButton")
    PageButton minecraftProtocolProbe$forward();

    @Accessor("backButton")
    PageButton minecraftProtocolProbe$back();
}
