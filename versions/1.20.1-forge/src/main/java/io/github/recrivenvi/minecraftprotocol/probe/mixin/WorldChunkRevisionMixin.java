package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class WorldChunkRevisionMixin {
    @Inject(method = "replaceWithPacketData", at = @At("HEAD"))
    private void minecraftProtocolProbe$replaceTargets(CallbackInfo callbackInfo) {
        // Invalidate before decoding: partial or failed replacements cannot retain old authority.
        io.github.recrivenvi.minecraftprotocol.probe.runtime.WorldTargeting.chunkReplaced(this);
    }

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void minecraftProtocolProbe$invalidateTargets(
            BlockPos pos, BlockState state, boolean moved, CallbackInfoReturnable<BlockState> cir) {
        // Vanilla echoes unchanged block state after item use; that is not a lifecycle change.
        // Real A->B->A transitions still increment twice, even when the final state is equal.
        if (cir.getReturnValue() != null)
            io.github.recrivenvi.minecraftprotocol.probe.runtime.WorldTargeting.chunkChanged(
                    this, pos, cir.getReturnValue(), state);
    }
}
