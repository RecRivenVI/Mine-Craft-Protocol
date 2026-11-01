package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.components.spectator.SpectatorGui;
import net.minecraft.client.gui.spectator.SpectatorMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(SpectatorGui.class)
public interface SpectatorGuiUiAccessor {
    @Accessor("menu")
    SpectatorMenu mineCraftProtocol$menu();

    @Invoker("getHotbarAlpha")
    float mineCraftProtocol$alpha();
}
