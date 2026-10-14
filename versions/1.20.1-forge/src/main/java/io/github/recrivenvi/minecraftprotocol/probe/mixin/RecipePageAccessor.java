package io.github.recrivenvi.minecraftprotocol.probe.mixin;

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
    OverlayRecipeComponent minecraftProtocolProbe$overlay();

    @Accessor("forwardButton")
    StateSwitchingButton minecraftProtocolProbe$forward();

    @Accessor("backButton")
    StateSwitchingButton minecraftProtocolProbe$back();

    @Invoker("listButtons")
    void minecraftProtocolProbe$buttons(Consumer<AbstractWidget> consumer);
}
