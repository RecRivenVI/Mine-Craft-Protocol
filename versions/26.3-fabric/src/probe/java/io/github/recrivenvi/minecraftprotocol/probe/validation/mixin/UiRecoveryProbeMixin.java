package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.gui.AutomationDiagnosticScreen;
import io.github.recrivenvi.minecraftprotocol.probe.validation.UiRecoveryFixtureScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutomationDiagnosticScreen.class, remap = false)
public abstract class UiRecoveryProbeMixin extends Screen {
    protected UiRecoveryProbeMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mineCraftProtocol$recoveryButton(CallbackInfo callback) {
        if (UiRecoveryFixtureScreen.enabled()
                && minecraft.level == null
                && minecraft.player == null
                && minecraft.getConnection() == null) {
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Recovery UI (Fixture)"),
                                    button ->
                                            minecraft.gui.setScreen(
                                                    new UiRecoveryFixtureScreen(this)))
                            .bounds(8, 179, 128, 20)
                            .build());
        }
    }
}
