package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.safety.UiRenderedIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class EffectIconRenderMixin implements UiRenderedIcons.Source {
    @Unique private final UiRenderedIcons mineCraftProtocol$icons = new UiRenderedIcons();

    @Inject(method = "renderEffects", at = @At("HEAD"))
    private void mineCraftProtocol$begin(GuiGraphics graphics, CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        mineCraftProtocol$icons.begin(
                client.player,
                client.screen,
                client.getWindow().getGuiScaledWidth(),
                client.getWindow().getGuiScaledHeight());
    }

    @Inject(method = "lambda$renderEffects$0", at = @At("RETURN"))
    private static void mineCraftProtocol$observed(
            GuiGraphics graphics,
            float alpha,
            int x,
            int y,
            TextureAtlasSprite sprite,
            CallbackInfo ci) {
        ((UiRenderedIcons.Source) Minecraft.getInstance().gui)
                .mineCraftProtocol$effectIcons()
                .observe(sprite.contents().name().toString(), x + 3, y + 3, 18, 18, alpha);
    }

    @Inject(method = "renderEffects", at = @At("RETURN"))
    private void mineCraftProtocol$finish(GuiGraphics graphics, CallbackInfo ci) {
        mineCraftProtocol$icons.finish(System.nanoTime());
    }

    @Override
    public UiRenderedIcons mineCraftProtocol$effectIcons() {
        return mineCraftProtocol$icons;
    }
}
