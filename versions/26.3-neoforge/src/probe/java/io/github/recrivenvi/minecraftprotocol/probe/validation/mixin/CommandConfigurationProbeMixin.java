package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.CommandConfigurationProbe;
import net.minecraft.network.protocol.game.ServerboundJigsawGeneratePacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetCommandMinecartPacket;
import net.minecraft.network.protocol.game.ServerboundSetJigsawBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetStructureBlockPacket;
import net.minecraft.network.protocol.game.ServerboundSetTestBlockPacket;
import net.minecraft.network.protocol.game.ServerboundTestInstanceBlockActionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CommandConfigurationProbeMixin {
    // HEAD observes admitted packets even when a native handler subsequently fails.
    // The probe counts only the server-thread re-entry, not Netty's initial dispatch.
    @Inject(method = "handleSetTestBlock", at = @At("HEAD"))
    private void mineCraftProtocol$testBlock(
            ServerboundSetTestBlockPacket packet, CallbackInfo callbackInfo) {
        CommandConfigurationProbe.testBlock(
                ((ServerGamePacketListenerImpl) (Object) this).player, packet);
    }

    @Inject(method = "handleTestInstanceBlockAction", at = @At("HEAD"))
    private void mineCraftProtocol$testAction(
            ServerboundTestInstanceBlockActionPacket packet, CallbackInfo callbackInfo) {
        CommandConfigurationProbe.testAction(
                ((ServerGamePacketListenerImpl) (Object) this).player, packet);
    }

    @Inject(method = "handleSetStructureBlock", at = @At("RETURN"))
    private void mineCraftProtocol$structure(
            ServerboundSetStructureBlockPacket packet, CallbackInfo callbackInfo) {
        CommandConfigurationProbe.structure(
                ((ServerGamePacketListenerImpl) (Object) this).player, packet);
    }

    @Inject(method = "handleSetJigsawBlock", at = @At("RETURN"))
    private void mineCraftProtocol$jigsaw(
            ServerboundSetJigsawBlockPacket packet, CallbackInfo callbackInfo) {
        CommandConfigurationProbe.jigsaw(
                ((ServerGamePacketListenerImpl) (Object) this).player, packet);
    }

    @Inject(method = "handleJigsawGenerate", at = @At("RETURN"))
    private void mineCraftProtocol$generate(
            ServerboundJigsawGeneratePacket packet, CallbackInfo callbackInfo) {
        CommandConfigurationProbe.generate(
                ((ServerGamePacketListenerImpl) (Object) this).player, packet);
    }

    @Inject(method = "handleSetCommandBlock", at = @At("RETURN"))
    private void mineCraftProtocol$block(
            ServerboundSetCommandBlockPacket packet, CallbackInfo callbackInfo) {
        CommandConfigurationProbe.block(
                ((ServerGamePacketListenerImpl) (Object) this).player, packet);
    }

    @Inject(method = "handleSetCommandMinecart", at = @At("RETURN"))
    private void mineCraftProtocol$minecart(
            ServerboundSetCommandMinecartPacket packet, CallbackInfo callbackInfo) {
        CommandConfigurationProbe.minecart(
                ((ServerGamePacketListenerImpl) (Object) this).player, packet);
    }
}
