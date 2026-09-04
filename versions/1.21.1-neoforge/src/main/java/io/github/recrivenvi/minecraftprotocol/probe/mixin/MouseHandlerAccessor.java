package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("mouseGrabbed")
    void minecraftProtocolProbe$setMouseGrabbed(boolean grabbed);
}
