package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import java.util.List;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OverlayRecipeComponent.class)
public interface RecipeOverlayUiAccessor {
    @Accessor("recipeButtons")
    List<? extends AbstractWidget> minecraftProtocolProbe$buttons();
}
