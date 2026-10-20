package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen;
import io.github.recrivenvi.minecraftprotocol.probe.validation.UiWorldRecoveryFixtureScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutomationProbeScreen.class, remap = false)
public abstract class UiWorldRecoveryEntryProbeMixin extends Screen {
    protected UiWorldRecoveryEntryProbeMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void minecraftProtocolProbe$worldRecoveryButton(CallbackInfo callback) {
        if (UiWorldRecoveryFixtureScreen.enabled()
                && minecraft.level == null
                && minecraft.player == null
                && minecraft.getConnection() == null
                && minecraft.getSingleplayerServer() == null) {
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("World Recovery (Fixture)"),
                                    button ->
                                            minecraft.gui.setScreen(
                                                    new UiWorldRecoveryFixtureScreen(this)))
                            .bounds(144, 179, 128, 20)
                            .build());
        }
    }
}
