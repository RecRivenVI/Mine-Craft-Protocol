package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen;
import io.github.recrivenvi.minecraftprotocol.probe.validation.UiNativeConfirmationFixtureScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutomationProbeScreen.class, remap = false)
public abstract class UiNativeConfirmationProbeMixin extends Screen {
    protected UiNativeConfirmationProbeMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void minecraftProtocolProbe$nativeConfirmationButton(CallbackInfo ci) {
        if (UiNativeConfirmationFixtureScreen.enabled())
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("More Native Screens (Fixture)"),
                                    button ->
                                            minecraft.setScreen(
                                                    new UiNativeConfirmationFixtureScreen(this)))
                            .bounds(8, 155, 128, 20)
                            .build());
    }
}
