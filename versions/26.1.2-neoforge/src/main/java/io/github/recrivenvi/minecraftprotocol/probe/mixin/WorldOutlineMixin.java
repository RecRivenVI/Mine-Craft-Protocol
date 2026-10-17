package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import io.github.recrivenvi.minecraftprotocol.probe.runtime.WorldPresentationSnapshot;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
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
            double x,
            double y,
            double z,
            BlockOutlineRenderState outline,
            int color,
            float lineWidth,
            CallbackInfo ci) {
        if (!outline.shape().isEmpty() && (color >>> 24) != 0)
            WorldPresentationSnapshot.outlineSubmitted(outline.pos());
    }
}
