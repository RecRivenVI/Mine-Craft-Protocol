package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.spectator.SpectatorMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpectatorMenu.class)
public interface SpectatorMenuUiAccessor {
    @Accessor("page")
    int mineCraftProtocol$page();
}
