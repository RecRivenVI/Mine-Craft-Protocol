package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.multiplayer.ServerReconfigScreen;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ServerReconfigScreen.class)
public interface UiReconfigurationScreenProbeAccessor {
    @Accessor("connection")
    Connection minecraftProtocolProbe$connection();

    @Accessor("delayTicker")
    int minecraftProtocolProbe$delayTicker();

    @Accessor("disconnectButton")
    Button minecraftProtocolProbe$disconnectButton();
}
