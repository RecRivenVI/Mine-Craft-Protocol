package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen;
import io.github.recrivenvi.minecraftprotocol.probe.validation.UiNavigationFixtureScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutomationProbeScreen.class, remap = false)
public abstract class UiNavigationProbeMixin extends Screen {
    protected UiNavigationProbeMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void minecraftProtocolProbe$navigationButton(CallbackInfo ci) {
        if (UiNavigationFixtureScreen.enabled()) {
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("UI Navigation Fixtures"),
                                    button ->
                                            minecraft.setScreen(
                                                    new UiNavigationFixtureScreen(this)))
                            .bounds(8, 107, 128, 20)
                            .build());
        }
    }
}
