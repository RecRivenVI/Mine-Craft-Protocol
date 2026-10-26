package io.github.recrivenvi.minecraftprotocol.probe.validation.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.gui.AutomationProbeScreen;
import io.github.recrivenvi.minecraftprotocol.probe.validation.WorldUserCatalog;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = AutomationProbeScreen.class, remap = false)
abstract class WorldUserCatalogProbeMixin extends Screen {
    protected WorldUserCatalogProbeMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void minecraftProtocol$worldUserCatalog(CallbackInfo ci) {
        if (WorldUserCatalog.enabled())
            addRenderableWidget(
                    Button.builder(
                                    Component.literal("Capture Native World Catalog (Fixture)"),
                                    WorldUserCatalog::capture)
                            .bounds(width / 2 - 100, height / 2 + 80, 200, 20)
                            .build());
    }
}
