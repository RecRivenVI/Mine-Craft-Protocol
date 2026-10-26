package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen;
import io.github.recrivenvi.minecraftprotocol.probe.validation.WorldDebugBoundaryFixture;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutomationProbeScreen.class, remap = false)
public abstract class WorldDebugBoundaryProbeMixin extends Screen {
    protected WorldDebugBoundaryProbeMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void minecraftProtocolProbe$boundaryButtons(CallbackInfo ci) {
        if (!WorldDebugBoundaryFixture.enabled()) return;
        addRenderableWidget(
                Button.builder(
                                Component.literal("Prepare Boundary Island (Fixture)"),
                                button -> WorldDebugBoundaryFixture.change(true, button))
                        .bounds(width / 2 - 100, height / 2 + 80, 200, 20)
                        .build());
        addRenderableWidget(
                Button.builder(
                                Component.literal("Restore Boundary Island (Fixture)"),
                                button -> WorldDebugBoundaryFixture.change(false, button))
                        .bounds(width / 2 - 100, height / 2 + 104, 200, 20)
                        .build());
    }
}
