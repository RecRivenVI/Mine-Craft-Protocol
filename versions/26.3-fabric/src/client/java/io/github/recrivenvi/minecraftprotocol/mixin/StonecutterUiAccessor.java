package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(StonecutterScreen.class)
public interface StonecutterUiAccessor {
    @Accessor("scrollOffs")
    float mineCraftProtocol$scrollOffs();

    @Accessor("scrollOffs")
    void mineCraftProtocol$scrollOffs(float value);

    @Accessor("startIndex")
    int mineCraftProtocol$startIndex();

    @Accessor("startIndex")
    void mineCraftProtocol$startIndex(int value);

    @Invoker("getOffscreenRows")
    int mineCraftProtocol$offscreenRows();

    @Accessor("displayRecipes")
    boolean mineCraftProtocol$displayRecipes();
}
