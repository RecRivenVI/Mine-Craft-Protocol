package io.github.recrivenvi.minecraftprotocol.mixin;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RecipeBookPage.class)
public interface RecipePageAccessor {
    @Accessor("overlay")
    OverlayRecipeComponent mineCraftProtocol$overlay();

    @Accessor("forwardButton")
    StateSwitchingButton mineCraftProtocol$forward();

    @Accessor("backButton")
    StateSwitchingButton mineCraftProtocol$back();

    @Invoker("listButtons")
    void mineCraftProtocol$buttons(Consumer<AbstractWidget> consumer);
}
