package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen;
import io.github.recrivenvi.minecraftprotocol.probe.validation.UiPlayerListFixtureScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutomationProbeScreen.class, remap = false)
public abstract class UiPlayerListProbeMixin extends Screen {
    protected UiPlayerListProbeMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void minecraftProtocolProbe$playerListButton(CallbackInfo ci) {
        if (UiPlayerListFixtureScreen.enabled() && minecraft.level != null) {
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Player List Fixture"),
                                    button ->
                                            minecraft.setScreen(
                                                    UiPlayerListFixtureScreen.open(this)))
                            .bounds(8, 131, 128, 20)
                            .build());
        }
    }
}
