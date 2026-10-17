package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.CommandConfigurationProbe;
import net.minecraft.network.protocol.common.ServerboundCustomClickActionPacket;
import net.minecraft.server.network.ServerCommonPacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerCommonPacketListenerImpl.class)
public abstract class CustomActionProbeMixin {
    @Inject(method = "handleCustomClickAction", at = @At("HEAD"))
    private void minecraftProtocolProbe$received(
            ServerboundCustomClickActionPacket packet, CallbackInfo ci) {
        CommandConfigurationProbe.customAction();
    }
}
