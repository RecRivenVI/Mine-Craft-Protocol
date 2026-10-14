package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import java.util.List;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.screens.inventory.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BookEditScreen.class)
public interface BookUiAccessor {
    @Accessor("currentPage")
    int minecraftProtocolProbe$pageIndex();

    @Accessor("pages")
    List<String> minecraftProtocolProbe$pages();

    @Accessor("forwardButton")
    PageButton minecraftProtocolProbe$forward();

    @Accessor("backButton")
    PageButton minecraftProtocolProbe$back();

    @Accessor("page")
    MultiLineEditBox minecraftProtocolProbe$page();
}
