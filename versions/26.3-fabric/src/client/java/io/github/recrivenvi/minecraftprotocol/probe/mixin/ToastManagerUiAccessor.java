package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ToastManager.class)
public interface ToastManagerUiAccessor {
    @Accessor("visibleToasts")
    java.util.List<?> minecraftProtocolProbe$visible();

    @Accessor("queued")
    java.util.Deque<net.minecraft.client.gui.components.toasts.Toast>
            minecraftProtocolProbe$queued();
}
