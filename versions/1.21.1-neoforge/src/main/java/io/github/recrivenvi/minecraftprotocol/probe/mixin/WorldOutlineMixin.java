package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.WorldPresentationSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class WorldOutlineMixin {
    @Inject(method = "renderHitOutline", at = @At("RETURN"))
    private void minecraftProtocolProbe$outlineSubmitted(
            PoseStack pose,
            VertexConsumer consumer,
            Entity viewer,
            double x,
            double y,
            double z,
            BlockPos position,
            BlockState state,
            CallbackInfo ci) {
        var client = Minecraft.getInstance();
        if (client.level != null
                && !state.getShape(client.level, position, CollisionContext.of(viewer)).isEmpty())
            WorldPresentationSnapshot.outlineSubmitted(position);
    }
}
