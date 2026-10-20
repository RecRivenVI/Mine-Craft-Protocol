package io.github.recrivenvi.minecraftprotocol.probe.mixin;

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
    int minecraftProtocolProbe$xOrigin();

    @Invoker("getYOrigin")
    int minecraftProtocolProbe$yOrigin();

    @Accessor("tabButtons")
    List<RecipeBookTabButton> minecraftProtocolProbe$tabs();

    @Accessor("selectedTab")
    RecipeBookTabButton minecraftProtocolProbe$selectedTab();

    @Accessor("searchBox")
    EditBox minecraftProtocolProbe$search();

    @Accessor("filterButton")
    CycleButton<Boolean> minecraftProtocolProbe$filter();

    @Accessor("recipeBookPage")
    RecipeBookPage minecraftProtocolProbe$page();
}
