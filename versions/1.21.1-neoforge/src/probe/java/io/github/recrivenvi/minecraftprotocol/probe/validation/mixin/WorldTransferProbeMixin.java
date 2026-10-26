package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen;
import io.github.recrivenvi.minecraftprotocol.probe.validation.WorldTransferFixture;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutomationProbeScreen.class, remap = false)
abstract class WorldTransferProbeMixin extends Screen {
    protected WorldTransferProbeMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void minecraftProtocol$transferFixture(CallbackInfo ci) {
        if (WorldTransferFixture.enabled())
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Prepare LIVE Destination (Fixture)"),
                                    button -> WorldTransferFixture.change(true, button))
                            .bounds(width / 2 - 100, height / 2 + 80, 200, 20)
                            .build());
        if (WorldTransferFixture.enabled())
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Clear LIVE Destination Pins (Fixture)"),
                                    button -> WorldTransferFixture.change(false, button))
                            .bounds(width / 2 - 100, height / 2 + 104, 200, 20)
                            .build());
    }
}
