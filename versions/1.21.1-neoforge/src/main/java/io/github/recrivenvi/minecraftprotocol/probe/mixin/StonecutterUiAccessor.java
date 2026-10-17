package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(StonecutterScreen.class)
public interface StonecutterUiAccessor {
    @Accessor("scrollOffs")
    float minecraftProtocolProbe$scrollOffs();

    @Accessor("scrollOffs")
    void minecraftProtocolProbe$scrollOffs(float value);

    @Accessor("startIndex")
    int minecraftProtocolProbe$startIndex();

    @Accessor("startIndex")
    void minecraftProtocolProbe$startIndex(int value);

    @Invoker("getOffscreenRows")
    int minecraftProtocolProbe$offscreenRows();

    @Accessor("displayRecipes")
    boolean minecraftProtocolProbe$displayRecipes();
}
