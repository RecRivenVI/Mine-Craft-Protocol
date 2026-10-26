package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen;
import io.github.recrivenvi.minecraftprotocol.probe.validation.WorldSeatFixture;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutomationProbeScreen.class, remap = false)
abstract class WorldSeatFixtureProbeMixin extends Screen {
    protected WorldSeatFixtureProbeMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void minecraftProtocol$worldSeats(CallbackInfo ci) {
        if (WorldSeatFixture.enabled())
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Open Native Seat Comparison (Fixture)"),
                                    WorldSeatFixture::open)
                            .bounds(width / 2 - 100, height / 2 + 80, 200, 20)
                            .build());
    }
}
