package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen;
import io.github.recrivenvi.minecraftprotocol.probe.validation.ShowcaseToastFixture;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutomationProbeScreen.class, remap = false)
public abstract class ShowcaseToastProbeMixin extends Screen {
    protected ShowcaseToastProbeMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void minecraftProtocolProbe$showcaseButton(CallbackInfo ci) {
        if (Boolean.getBoolean("minecraft.protocol.toastDebugFixture"))
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Toast DEBUG Fixtures"),
                                    button -> ShowcaseToastFixture.showDebug())
                            .bounds(width / 2 - 100, height / 2 + 80, 200, 20)
                            .build());
        if (!Boolean.getBoolean("minecraft.protocol.showcaseFixture")) return;
        addRenderableWidget(
                Button.builder(
                                Component.literal("Showcase Toasts (Fixture)"),
                                button -> ShowcaseToastFixture.show())
                        .bounds(width / 2 - 100, height / 2 + 80, 200, 20)
                        .build());
    }
}
