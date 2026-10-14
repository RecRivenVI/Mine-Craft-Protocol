package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.recipebook.OverlayRecipeComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(RecipeBookPage.class)
public interface RecipePageAccessor {
    @Accessor("overlay")
    OverlayRecipeComponent minecraftProtocolProbe$overlay();

    @Accessor("forwardButton")
    ImageButton minecraftProtocolProbe$forward();

    @Accessor("backButton")
    ImageButton minecraftProtocolProbe$back();

    @Invoker("listButtons")
    void minecraftProtocolProbe$buttons(Consumer<AbstractWidget> consumer);
}
