package io.github.recrivenvi.minecraftprotocol.mixin;

import io.github.recrivenvi.minecraftprotocol.runtime.NeoForgeRuntime;
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
    private void mineCraftProtocol$observeElement(
            GuiElementRenderState state, CallbackInfo callbackInfo) {
        mineCraftProtocol$observe("element", state.getClass().getName(), state.bounds());
    }

    @Inject(method = "addItem", at = @At("HEAD"))
    private void mineCraftProtocol$observeItem(
            GuiItemRenderState state, CallbackInfo callbackInfo) {
        mineCraftProtocol$observe("item", state.getClass().getName(), state.bounds());
    }

    @Inject(method = "addText", at = @At("HEAD"))
    private void mineCraftProtocol$observeText(
            GuiTextRenderState state, CallbackInfo callbackInfo) {
        mineCraftProtocol$observe("text", state.getClass().getName(), state.bounds());
    }

    @Inject(method = "addPicturesInPictureState", at = @At("HEAD"))
    private void mineCraftProtocol$observePicture(
            PictureInPictureRenderState state, CallbackInfo callbackInfo) {
        mineCraftProtocol$observe("picture", state.getClass().getName(), state.bounds());
    }

    private static void mineCraftProtocol$observe(
            String category, String stateClass, ScreenRectangle bounds) {
        if (bounds == null) {
            NeoForgeRuntime.observeRenderFact(category, stateClass, 0, 0, 0, 0);
            return;
        }
        NeoForgeRuntime.observeRenderFact(
                category, stateClass, bounds.left(), bounds.top(), bounds.width(), bounds.height());
    }
}
