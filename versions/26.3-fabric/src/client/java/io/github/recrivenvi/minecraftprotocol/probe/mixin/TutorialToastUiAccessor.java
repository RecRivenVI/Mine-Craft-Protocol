package io.github.recrivenvi.minecraftprotocol.probe.mixin;

import net.minecraft.client.gui.components.toasts.TutorialToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(TutorialToast.class)
public interface TutorialToastUiAccessor {
    @Accessor("progressable")
    boolean minecraftProtocolProbe$progressable();

    @Accessor("progress")
    float minecraftProtocolProbe$progress();

    @Accessor("visibility")
    net.minecraft.client.gui.components.toasts.Toast.Visibility minecraftProtocolProbe$visibility();

    @Accessor("lines")
    java.util.List<net.minecraft.util.FormattedCharSequence> minecraftProtocolProbe$lines();
}
