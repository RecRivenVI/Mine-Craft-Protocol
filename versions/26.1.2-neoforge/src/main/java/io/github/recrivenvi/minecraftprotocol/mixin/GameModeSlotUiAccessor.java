package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.screens.debug.GameModeSwitcherScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameModeSwitcherScreen.GameModeSlot.class)
public interface GameModeSlotUiAccessor {
    @Accessor("isSelected")
    boolean mineCraftProtocol$selected();
}
