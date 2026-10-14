package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(StonecutterScreen.class)
public interface StonecutterUiAccessor {
    @Accessor("scrollOffs")
    float minecraftProtocolProbe$scrollOffs();

    @Accessor("startIndex")
    int minecraftProtocolProbe$startIndex();

    @Accessor("displayRecipes")
    boolean minecraftProtocolProbe$displayRecipes();
}
