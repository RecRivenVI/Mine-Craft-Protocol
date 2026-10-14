package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.screens.inventory.LoomScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(LoomScreen.class)
public interface LoomUiAccessor {
    @Accessor("scrollOffs")
    float minecraftProtocolProbe$scrollOffs();

    @Accessor("startRow")
    int minecraftProtocolProbe$startRow();

    @Accessor("displayPatterns")
    boolean minecraftProtocolProbe$displayPatterns();
}
