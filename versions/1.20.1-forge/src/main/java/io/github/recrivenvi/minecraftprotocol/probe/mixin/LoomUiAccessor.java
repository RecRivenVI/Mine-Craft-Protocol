package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.screens.inventory.LoomScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LoomScreen.class)
public interface LoomUiAccessor {
    @Accessor("scrollOffs")
    float minecraftProtocolProbe$scrollOffs();

    @Accessor("scrollOffs")
    void minecraftProtocolProbe$scrollOffs(float value);

    @Accessor("startRow")
    int minecraftProtocolProbe$startRow();

    @Accessor("startRow")
    void minecraftProtocolProbe$startRow(int value);

    @Invoker("totalRowCount")
    int minecraftProtocolProbe$totalRows();

    @Accessor("displayPatterns")
    boolean minecraftProtocolProbe$displayPatterns();
}
