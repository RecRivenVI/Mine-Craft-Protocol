package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("guiRenderer") GuiRenderer minecraftProtocolProbe$guiRenderer();
    @Accessor("fogRenderer") FogRenderer minecraftProtocolProbe$fogRenderer();
}
