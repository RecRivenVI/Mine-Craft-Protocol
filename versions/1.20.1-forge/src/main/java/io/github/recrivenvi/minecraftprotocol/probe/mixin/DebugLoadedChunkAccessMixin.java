package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.core.LoadedChunkMutationScope;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerChunkCache.class)
public abstract class DebugLoadedChunkAccessMixin {
    @Shadow @Final public ServerLevel level;

    @Inject(method = "getChunk", at = @At("HEAD"), cancellable = true)
    private void minecraftProtocol$loadedDebugDependency(
            int x,
            int z,
            ChunkStatus status,
            boolean create,
            CallbackInfoReturnable<ChunkAccess> ci) {
        LoadedChunkMutationScope scope = LoadedChunkMutationScope.current();
        if (scope == null || !create) return;
        LevelChunk loaded = ((ServerChunkCache) (Object) this).getChunkNow(x, z);
        if (loaded == null) scope.reject(this.level.dimension().location().toString(), x, z);
        ci.setReturnValue(loaded);
    }
}
