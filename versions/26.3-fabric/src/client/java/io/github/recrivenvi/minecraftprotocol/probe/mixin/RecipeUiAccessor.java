package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import java.util.List;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.recipebook.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(RecipeBookComponent.class)
public interface RecipeUiAccessor {
    @Accessor("tabButtons")
    List<RecipeBookTabButton> minecraftProtocolProbe$tabs();

    @Accessor("searchBox")
    EditBox minecraftProtocolProbe$search();

    @Accessor("filterButton")
    CycleButton<Boolean> minecraftProtocolProbe$filter();

    @Accessor("recipeBookPage")
    RecipeBookPage minecraftProtocolProbe$page();
}
