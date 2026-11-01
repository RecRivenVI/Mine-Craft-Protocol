package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.screens.inventory.LoomScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LoomScreen.class)
public interface LoomUiAccessor {
    @Accessor("scrollOffs")
    float mineCraftProtocol$scrollOffs();

    @Accessor("scrollOffs")
    void mineCraftProtocol$scrollOffs(float value);

    @Accessor("startRow")
    int mineCraftProtocol$startRow();

    @Accessor("startRow")
    void mineCraftProtocol$startRow(int value);

    @Invoker("totalRowCount")
    int mineCraftProtocol$totalRows();

    @Accessor("displayPatterns")
    boolean mineCraftProtocol$displayPatterns();
}
