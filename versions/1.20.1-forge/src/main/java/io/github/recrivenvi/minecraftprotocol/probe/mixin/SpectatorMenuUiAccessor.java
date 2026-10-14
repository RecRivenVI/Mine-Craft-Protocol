package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.spectator.SpectatorMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SpectatorMenu.class)
public interface SpectatorMenuUiAccessor {
    @Accessor("page")
    int minecraftProtocolProbe$page();
}
