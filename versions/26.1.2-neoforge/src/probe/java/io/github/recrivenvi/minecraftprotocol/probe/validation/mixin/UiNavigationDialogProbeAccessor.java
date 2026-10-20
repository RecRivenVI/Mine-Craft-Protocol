package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import net.minecraft.client.gui.screens.dialog.DialogConnectionAccess;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ClientPacketListener.class)
public interface UiNavigationDialogProbeAccessor {
    @Invoker("createDialogAccess")
    DialogConnectionAccess minecraftProtocolProbe$dialogAccess();
}
