package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerReconfigScreen.class)
public interface UiReconfigurationScreenProbeAccessor {
    @Accessor("connection")
    Connection mineCraftProtocol$connection();

    @Accessor("delayTicker")
    int mineCraftProtocol$delayTicker();

    @Accessor("disconnectButton")
    Button mineCraftProtocol$disconnectButton();
}
