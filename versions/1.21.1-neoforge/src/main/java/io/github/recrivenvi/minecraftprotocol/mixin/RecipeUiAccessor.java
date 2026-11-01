package io.github.recrivenvi.minecraftprotocol.mixin;

import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecipeBookComponent.class)
public interface RecipeUiAccessor {
    @Accessor("width")
    int mineCraftProtocol$width();

    @Accessor("height")
    int mineCraftProtocol$height();

    @Accessor("xOffset")
    int mineCraftProtocol$xOffset();

    @Accessor("tabButtons")
    List<RecipeBookTabButton> mineCraftProtocol$tabs();

    @Accessor("selectedTab")
    RecipeBookTabButton mineCraftProtocol$selectedTab();

    @Accessor("searchBox")
    EditBox mineCraftProtocol$search();

    @Accessor("filterButton")
    StateSwitchingButton mineCraftProtocol$filter();

    @Accessor("recipeBookPage")
    RecipeBookPage mineCraftProtocol$page();
}
