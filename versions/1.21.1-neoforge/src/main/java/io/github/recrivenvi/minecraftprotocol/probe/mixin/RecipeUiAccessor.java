package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import java.util.List;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StateSwitchingButton;
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
    StateSwitchingButton minecraftProtocolProbe$filter();

    @Accessor("recipeBookPage")
    RecipeBookPage minecraftProtocolProbe$page();
}
