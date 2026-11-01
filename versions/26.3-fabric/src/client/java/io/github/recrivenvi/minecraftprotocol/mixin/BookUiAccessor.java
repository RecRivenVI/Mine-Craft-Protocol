package io.github.recrivenvi.minecraftprotocol.mixin;

import java.util.List;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookEditScreen.class)
public interface BookUiAccessor {
    @Accessor("currentPage")
    int mineCraftProtocol$pageIndex();

    @Accessor("pages")
    List<String> mineCraftProtocol$pages();

    @Accessor("forwardButton")
    PageButton mineCraftProtocol$forward();

    @Accessor("backButton")
    PageButton mineCraftProtocol$back();

    @Accessor("page")
    MultiLineEditBox mineCraftProtocol$page();
}
