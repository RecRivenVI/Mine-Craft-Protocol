package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.recrivenvi.minecraftprotocol.safety.UiRenderedIcons;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.core.Holder;
import net.minecraft.util.ARGB;
import net.minecraft.world.effect.MobEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public abstract class EffectIconRenderMixin implements UiRenderedIcons.Source {
    @Unique private final UiRenderedIcons minecraftProtocolProbe$icons = new UiRenderedIcons();

    @Inject(method = "extractEffects", at = @At("HEAD"))
    private void minecraftProtocolProbe$begin(
            GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        minecraftProtocolProbe$icons.begin(
                client.player,
                client.gui.screen(),
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight());
    }

    // Observe at the actual draw; locals and the native ARGB conversion are never modified.
    @Inject(
            method = "extractEffects",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V"))
    private void minecraftProtocolProbe$observed(
            GuiGraphicsExtractor graphics,
            DeltaTracker delta,
            CallbackInfo ci,
            @Local(index = 8) Holder<MobEffect> effect,
            @Local(index = 9) int x,
            @Local(index = 10) int y,
            @Local(index = 11) float alpha) {
        minecraftProtocolProbe$icons.observe(
                Hud.getMobEffectSprite(effect).toString(),
                x + 3,
                y + 3,
                18,
                18,
                (ARGB.white(alpha) >>> 24) / 255.0F);
    }

    @Inject(method = "extractEffects", at = @At("RETURN"))
    private void minecraftProtocolProbe$finish(
            GuiGraphicsExtractor graphics, DeltaTracker delta, CallbackInfo ci) {
        minecraftProtocolProbe$icons.finish(System.nanoTime());
    }

    @Override
    public UiRenderedIcons minecraftProtocolProbe$effectIcons() {
        return minecraftProtocolProbe$icons;
    }
}
