package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.components.spectator.SpectatorGui;
import net.minecraft.client.gui.spectator.SpectatorMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SpectatorGui.class)
public interface SpectatorGuiUiAccessor {
    @Accessor("menu")
    SpectatorMenu minecraftProtocolProbe$menu();

    @Invoker("getHotbarAlpha")
    float minecraftProtocolProbe$alpha();
}
