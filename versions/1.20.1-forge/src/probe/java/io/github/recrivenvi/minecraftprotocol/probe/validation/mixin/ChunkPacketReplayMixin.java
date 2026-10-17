package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.validation.ChunkPacketReplay;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class ChunkPacketReplayMixin {
    @Inject(method = "replaceWithPacketData", at = @At("HEAD"))
    private void minecraftProtocolProbe$begin(CallbackInfo callbackInfo) {
        ChunkPacketReplay.before((LevelChunk) (Object) this);
    }

    @Inject(method = "replaceWithPacketData", at = @At("RETURN"))
    private void minecraftProtocolProbe$end(CallbackInfo callbackInfo) {
        ChunkPacketReplay.after((LevelChunk) (Object) this);
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void minecraftProtocolProbe$position(CallbackInfoReturnable<BlockState> callbackInfo) {
        ChunkPacketReplay.positionUpdate((LevelChunk) (Object) this);
    }
}
