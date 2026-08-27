package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.NeoForgeProbeRuntime;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiItemRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiRenderState.class)
abstract class GuiRenderStateMixin {
    @Inject(method = "addGuiElement", at = @At("HEAD"))
    private void minecraftProtocolProbe$observeElement(GuiElementRenderState state, CallbackInfo callbackInfo) {
        minecraftProtocolProbe$observe("element", state.getClass().getName(), state.bounds());
    }

    @Inject(method = "addItem", at = @At("HEAD"))
    private void minecraftProtocolProbe$observeItem(GuiItemRenderState state, CallbackInfo callbackInfo) {
        minecraftProtocolProbe$observe("item", state.getClass().getName(), state.bounds());
    }

    @Inject(method = "addText", at = @At("HEAD"))
    private void minecraftProtocolProbe$observeText(GuiTextRenderState state, CallbackInfo callbackInfo) {
        minecraftProtocolProbe$observe("text", state.getClass().getName(), state.bounds());
    }

    @Inject(method = "addPicturesInPictureState", at = @At("HEAD"))
    private void minecraftProtocolProbe$observePicture(PictureInPictureRenderState state, CallbackInfo callbackInfo) {
        minecraftProtocolProbe$observe("picture", state.getClass().getName(), state.bounds());
    }

    private static void minecraftProtocolProbe$observe(
            String category, String stateClass, ScreenRectangle bounds) {
        if (bounds == null) {
            NeoForgeProbeRuntime.observeRenderFact(category, stateClass, 0, 0, 0, 0);
            return;
        }
        NeoForgeProbeRuntime.observeRenderFact(
                category, stateClass, bounds.left(), bounds.top(), bounds.width(), bounds.height());
    }
}
