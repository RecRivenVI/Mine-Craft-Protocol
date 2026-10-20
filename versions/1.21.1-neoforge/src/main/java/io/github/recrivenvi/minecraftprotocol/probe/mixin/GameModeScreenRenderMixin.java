package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import io.github.recrivenvi.minecraftprotocol.probe.runtime.NeoForgeProbeRuntime;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Screen.class)
public abstract class GameModeScreenRenderMixin {
    @Redirect(
            method = "renderWithTooltip",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/screens/Screen;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void minecraftProtocolProbe$gameModeRender(
            Screen screen, GuiGraphics graphics, int x, int y, float partialTick) {
        NeoForgeProbeRuntime.renderGameModeScreen(screen, graphics, x, y, partialTick);
    }
}
