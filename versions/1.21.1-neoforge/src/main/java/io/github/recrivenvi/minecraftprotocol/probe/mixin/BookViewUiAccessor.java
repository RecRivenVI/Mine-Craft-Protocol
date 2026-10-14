package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.screens.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookViewScreen.class)
public interface BookViewUiAccessor {
    @Accessor("currentPage")
    int minecraftProtocolProbe$pageIndex();

    @Accessor("bookAccess")
    BookViewScreen.BookAccess minecraftProtocolProbe$book();

    @Accessor("forwardButton")
    PageButton minecraftProtocolProbe$forward();

    @Accessor("backButton")
    PageButton minecraftProtocolProbe$back();
}
