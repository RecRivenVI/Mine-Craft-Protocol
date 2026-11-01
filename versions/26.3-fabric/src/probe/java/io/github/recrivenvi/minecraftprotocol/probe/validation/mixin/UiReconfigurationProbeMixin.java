package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.gui.AutomationDiagnosticScreen;
import io.github.recrivenvi.minecraftprotocol.probe.validation.UiReconfigurationFixture;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutomationDiagnosticScreen.class, remap = false)
public abstract class UiReconfigurationProbeMixin extends Screen {
    protected UiReconfigurationProbeMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void mineCraftProtocol$reconfigurationButton(CallbackInfo callback) {
        if (!UiReconfigurationFixture.enabled()) return;
        Button reconfigure =
                Button.builder(
                                Component.literal("Native Reconfigure (Fixture)"),
                                button -> UiReconfigurationFixture.arrange())
                        .bounds(144, 155, 128, 20)
                        .build();
        reconfigure.active = UiReconfigurationFixture.prepare();
        addRenderableWidget(reconfigure);
    }
}
