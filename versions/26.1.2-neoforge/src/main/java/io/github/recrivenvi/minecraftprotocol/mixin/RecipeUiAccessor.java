package io.github.recrivenvi.minecraftprotocol.mixin;

import java.util.List;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RecipeBookComponent.class)
public interface RecipeUiAccessor {
    @Invoker("getXOrigin")
    int mineCraftProtocol$xOrigin();

    @Invoker("getYOrigin")
    int mineCraftProtocol$yOrigin();

    @Accessor("tabButtons")
    List<RecipeBookTabButton> mineCraftProtocol$tabs();

    @Accessor("selectedTab")
    RecipeBookTabButton mineCraftProtocol$selectedTab();

    @Accessor("searchBox")
    EditBox mineCraftProtocol$search();

    @Accessor("filterButton")
    CycleButton<Boolean> mineCraftProtocol$filter();

    @Accessor("recipeBookPage")
    RecipeBookPage mineCraftProtocol$page();
}
