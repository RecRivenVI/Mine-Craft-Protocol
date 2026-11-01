package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.screens.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookViewScreen.class)
public interface BookViewUiAccessor {
    @Accessor("currentPage")
    int mineCraftProtocol$pageIndex();

    @Accessor("bookAccess")
    BookViewScreen.BookAccess mineCraftProtocol$book();

    @Accessor("forwardButton")
    PageButton mineCraftProtocol$forward();

    @Accessor("backButton")
    PageButton mineCraftProtocol$back();
}
