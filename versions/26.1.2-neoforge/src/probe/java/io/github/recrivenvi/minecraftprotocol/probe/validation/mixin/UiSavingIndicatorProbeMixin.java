package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen;
import io.github.recrivenvi.minecraftprotocol.probe.validation.UiSavingIndicatorFixture;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutomationProbeScreen.class, remap = false)
public abstract class UiSavingIndicatorProbeMixin extends Screen {
    protected UiSavingIndicatorProbeMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void minecraftProtocolProbe$savingFixture(CallbackInfo callback) {
        if (UiSavingIndicatorFixture.prepare())
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Pause Save Fixture"),
                                    button -> {
                                        UiSavingIndicatorFixture.arm();
                                        button.active = false;
                                    })
                            .bounds(8, 203, 128, 20)
                            .build());
    }
}
