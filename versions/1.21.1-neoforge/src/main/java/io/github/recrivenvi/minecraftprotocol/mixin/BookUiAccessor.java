package io.github.recrivenvi.minecraftprotocol.mixin;

import java.util.List;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.screens.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookEditScreen.class)
public interface BookUiAccessor {
    @Accessor("pageEdit")
    TextFieldHelper mineCraftProtocol$pageField();

    @Accessor("titleEdit")
    TextFieldHelper mineCraftProtocol$titleField();

    @Accessor("currentPage")
    int mineCraftProtocol$pageIndex();

    @Accessor("pages")
    List<String> mineCraftProtocol$pages();

    @Accessor("forwardButton")
    PageButton mineCraftProtocol$forward();

    @Accessor("backButton")
    PageButton mineCraftProtocol$back();

    @Accessor("isSigning")
    boolean mineCraftProtocol$signing();

    @Accessor("title")
    String mineCraftProtocol$title();
}
