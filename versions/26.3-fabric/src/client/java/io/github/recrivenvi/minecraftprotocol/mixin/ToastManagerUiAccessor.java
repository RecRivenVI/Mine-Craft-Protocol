package io.github.recrivenvi.minecraftprotocol.mixin;

import net.minecraft.client.gui.components.toasts.NowPlayingToast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ToastManager.class)
public interface ToastManagerUiAccessor {
    @Accessor("visibleToasts")
    java.util.List<?> mineCraftProtocol$visible();

    @Accessor("queued")
    java.util.Deque<net.minecraft.client.gui.components.toasts.Toast> mineCraftProtocol$queued();

    @Accessor("nowPlayingToast")
    ToastManager.ToastInstance<NowPlayingToast> mineCraftProtocol$nowPlaying();
}
